/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.session;

import inetsoft.sree.ClientInfo;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.internal.cluster.MockCluster;
import inetsoft.sree.security.*;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.web.admin.server.NodeProtectionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79542: the master's sweep ends the sessions of an organization that no longer exists once
 * it is found deleted in two runs in a row, through deleteById so the user is logged out.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { inetsoft.test.BaseTestConfiguration.class },
                       initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class IgniteSessionDeletedOrgSweepTest {
   @Mock private SecurityEngine securityEngine;
   @Mock private AuthenticationService authenticationService;
   @Mock private NodeProtectionService nodeProtectionService;

   private MockCluster cluster;
   private IgniteSessionRepository repository;
   private MockedStatic<OrganizationExistence> existence;
   private Set<String> deleted;
   private final List<Collection<String>> asked = new ArrayList<>();

   @BeforeEach
   void setUp() {
      cluster = new MockCluster();
      repository = new IgniteSessionRepository(
         securityEngine, authenticationService, nodeProtectionService, cluster);
      repository.afterPropertiesSet();
      deleted = new HashSet<>();
      existence = mockStatic(OrganizationExistence.class);
      existence.when(() -> OrganizationExistence.findDeleted(anyCollection()))
         .thenAnswer(inv -> {
            Collection<String> ids = inv.getArgument(0);
            asked.add(new HashSet<>(ids));
            Set<String> result = new HashSet<>(ids);
            result.retainAll(deleted);
            return result;
         });
   }

   @AfterEach
   void tearDown() throws Exception {
      existence.close();
      repository.destroy();
   }

   @Test
   void sessionOfDeletedOrgIsEndedAfterTwoPassesAndTheUserIsLoggedOut() {
      SRPrincipal principal = principal("zed", "gone-org", "gone-org");
      String id = save(principal);
      deleted.add("gone-org");

      repository.sweepDeletedOrganizationSessions();
      assertNotNull(repository.findById(id), "one pass must not end the session");
      verifyNoInteractions(authenticationService);

      repository.sweepDeletedOrganizationSessions();
      assertNull(repository.findById(id), "the session must be removed from the repository");
      verify(authenticationService).logout(same(principal), eq("127.0.0.1"), anyString());
   }

   // the only tenant org is deleted: no "would end every org" guard may block it
   @Test
   void onlyNonHostOrgBeingDeletedIsEnded() {
      String host = save(principal("admin", "host-org", "host-org"));
      String tenant = save(principal("zed", "only-org", "only-org"));
      deleted.add("only-org");

      repository.sweepDeletedOrganizationSessions();
      repository.sweepDeletedOrganizationSessions();

      assertNull(repository.findById(tenant));
      assertNotNull(repository.findById(host));
   }

   @Test
   void everySessionOfTheDeletedOrgIsEndedAndOtherOrgsKept() {
      String a = save(principal("a", "gone-org", "gone-org"));
      String b = save(principal("b", "gone-org", "gone-org"));
      String live = save(principal("c", "live-org", "live-org"));
      deleted.add("gone-org");

      repository.sweepDeletedOrganizationSessions();
      repository.sweepDeletedOrganizationSessions();

      assertNull(repository.findById(a));
      assertNull(repository.findById(b));
      assertNotNull(repository.findById(live));
   }

   @Test
   void orgMissingInOnePassOnlyKeepsItsSessions() {
      String id = save(principal("zed", "gone-org", "gone-org"));

      deleted.add("gone-org");
      repository.sweepDeletedOrganizationSessions();
      deleted.clear();
      repository.sweepDeletedOrganizationSessions();
      deleted.add("gone-org");
      repository.sweepDeletedOrganizationSessions();

      assertNotNull(repository.findById(id), "the miss must be consecutive");
      verifyNoInteractions(authenticationService);
   }

   @Test
   void anonymousPrincipalOfAnUnknownOrgIsKept() {
      String id = save(principal("anonymous", "gone-org", "gone-org"));
      deleted.add("gone-org");

      repository.sweepDeletedOrganizationSessions();
      repository.sweepDeletedOrganizationSessions();

      assertNotNull(repository.findById(id));
      assertTrue(asked.isEmpty(), "an anonymous principal's org must not be judged");
   }

   @Test
   void orgIsJudgedOnThePrincipalOrgNotTheIdentityOrg() {
      // identity org differs from the session org (SSO claim): getOrgId() decides
      String sso = save(principal("sso", "claim-org", "host-org"));
      String other = save(principal("other", "host-org", "gone-org"));
      deleted.add("gone-org");
      deleted.add("claim-org");

      repository.sweepDeletedOrganizationSessions();
      repository.sweepDeletedOrganizationSessions();

      assertNotNull(repository.findById(sso), "its getOrgId() is the host org");
      assertNull(repository.findById(other), "its getOrgId() is the deleted org");
      assertTrue(asked.stream().noneMatch(s -> s.contains("claim-org")),
                 "the identity org must not be asked about");
   }

   @Test
   void nonMasterNodeDoesNothingAndForgetsItsFirstMiss() {
      MockCluster follower = spy(new MockCluster());
      IgniteSessionRepository other = new IgniteSessionRepository(
         securityEngine, authenticationService, nodeProtectionService, follower);
      other.afterPropertiesSet();

      try {
         String id = saveIn(other, principal("zed", "gone-org", "gone-org"));
         deleted.add("gone-org");

         doReturn(true).when(follower).isMaster();
         other.sweepDeletedOrganizationSessions();
         doReturn(false).when(follower).isMaster();
         other.sweepDeletedOrganizationSessions();
         other.sweepDeletedOrganizationSessions();
         assertNotNull(other.findById(id), "a follower must not end sessions");

         // the first miss was forgotten while not master: a single pass as master is not enough
         doReturn(true).when(follower).isMaster();
         other.sweepDeletedOrganizationSessions();
         assertNotNull(other.findById(id));
         other.sweepDeletedOrganizationSessions();
         assertNull(other.findById(id));
      }
      finally {
         try {
            other.destroy();
         }
         catch(Exception ignore) {
         }
      }
   }

   @Test
   void predicateFailureDoesNotEndSessionsOrBreakTheSweep() {
      String id = save(principal("zed", "gone-org", "gone-org"));
      existence.when(() -> OrganizationExistence.findDeleted(anyCollection()))
         .thenThrow(new IllegalStateException("provider down"));

      assertDoesNotThrow(() -> {
         repository.sweepDeletedOrganizationSessions();
         repository.sweepDeletedOrganizationSessions();
      });
      assertNotNull(repository.findById(id));
   }

   @Test
   void oneFailingDeleteDoesNotStopTheOthers() {
      String a = save(principal("a", "gone-org", "gone-org"));
      String b = save(principal("b", "gone-org", "gone-org"));
      deleted.add("gone-org");
      doThrow(new IllegalStateException("boom")).when(authenticationService)
         .logout(argThat(p -> p != null && ((SRPrincipal) p).getName().startsWith("a~")),
                 anyString(), anyString());

      repository.sweepDeletedOrganizationSessions();
      assertDoesNotThrow(() -> repository.sweepDeletedOrganizationSessions());

      assertNull(repository.findById(b));
      assertNull(repository.findById(a), "the session is removed before logout is attempted");
   }

   private String save(SRPrincipal principal) {
      return saveIn(repository, principal);
   }

   private String saveIn(IgniteSessionRepository repo, SRPrincipal principal) {
      when(securityEngine.isActiveUser(principal)).thenReturn(true);
      IgniteSessionRepository.IgniteSession session = repo.createSession();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      repo.save(session);
      return session.getId();
   }

   private static SRPrincipal principal(String name, String identityOrg, String orgId) {
      SRPrincipal principal = mock(SRPrincipal.class, withSettings().lenient());
      IdentityID id = new IdentityID(name, identityOrg);
      when(principal.getUser()).thenReturn(new ClientInfo(id, "127.0.0.1"));
      when(principal.getName()).thenReturn(id.convertToKey());
      when(principal.getIdentityID()).thenReturn(id);
      when(principal.getOrgId()).thenReturn(orgId);
      return principal;
   }
}
