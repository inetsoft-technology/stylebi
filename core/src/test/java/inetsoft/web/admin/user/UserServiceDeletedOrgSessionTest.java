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
package inetsoft.web.admin.user;

import inetsoft.sree.RepletRepository;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import inetsoft.web.admin.monitoring.StatusMetricsType;
import inetsoft.web.admin.viewsheet.ViewsheetService;
import inetsoft.web.cluster.ServerClusterClient;
import inetsoft.web.session.IgniteSessionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79542: a site admin sees, and can end, the sessions of an organization that no longer
 * exists. An organization admin never does, and an unknown answer from the providers shows
 * nothing extra.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceDeletedOrgSessionTest {
   @Mock private ViewsheetService viewsheetService;
   @Mock private ServerClusterClient client;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private MonitoringDataService monitoringDataService;
   @Mock private IgniteSessionRepository sessionRepository;
   @Mock private Cluster cluster;
   @Mock private OrganizationManager organizationManager;
   @Mock private Principal caller;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<OrganizationExistence> existence;
   private UserStatusController controller;
   private UserService userService;
   private Set<String> deleted;
   private final List<SRPrincipal> active = new ArrayList<>();
   private final List<Collection<String>> asked = new ArrayList<>();

   private static final String HOST = "host-org";
   private static final IdentityID HOST_USER = new IdentityID("admin", HOST);
   private static final IdentityID ORPHAN_USER = new IdentityID("zed", "gone-org");
   private static final IdentityID OTHER_USER = new IdentityID("bob", "live-org");
   private static final IdentityID GUEST = new IdentityID("anonymous", "gone-org");
   private static final String HOST_SESSION = "USER1700000000000_1_admin~;~host-org";
   private static final String ORPHAN_SESSION = "USER1700000000000_2_zed~;~gone-org";
   private static final String OTHER_SESSION = "USER1700000000000_3_bob~;~live-org";
   private static final String GUEST_SESSION = "USER1700000000000_4_anonymous~;~gone-org";

   @BeforeEach
   void setUp() {
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(organizationManager);
      when(organizationManager.getCurrentOrgID(caller)).thenReturn(HOST);
      when(organizationManager.isSiteAdmin(caller)).thenReturn(true);
      when(caller.getName()).thenReturn("admin~;~host-org");

      deleted = new HashSet<>(Set.of("gone-org"));
      existence = mockStatic(OrganizationExistence.class);
      existence.when(() -> OrganizationExistence.findDeleted(anyCollection()))
         .thenAnswer(inv -> {
            Collection<String> ids = inv.getArgument(0);
            asked.add(new HashSet<>(ids));
            Set<String> result = new HashSet<>(ids);
            result.retainAll(deleted);
            return result;
         });

      userService = spy(new UserService(viewsheetService, client, securityEngine,
         securityProvider, monitoringDataService, sessionRepository, cluster));
      // monitor level is read from SreeEnv, which needs a Spring context
      doReturn(false).when(userService).isLevelQualified(anyString());
      controller = new UserStatusController(userService, monitoringDataService, securityEngine);
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityProvider.checkPermission(eq(caller), eq(ResourceType.SECURITY_USER),
         anyString(), eq(ResourceAction.ADMIN))).thenReturn(true);

      when(client.getMetrics(eq(StatusMetricsType.USER_METRICS), any()))
         .thenReturn(UserMetrics.builder()
            .addSessions(sessionModel(HOST_SESSION, HOST_USER),
                         sessionModel(ORPHAN_SESSION, ORPHAN_USER),
                         sessionModel(OTHER_SESSION, OTHER_USER),
                         sessionModel(GUEST_SESSION, GUEST))
            .build());
      registerSession(ORPHAN_SESSION, "ignite-orphan");
      registerSession(OTHER_SESSION, "ignite-other");
   }

   @AfterEach
   void tearDown() {
      existence.close();
      orgManagerStatic.close();
   }

   @Test
   void siteAdminSeesTheSessionOfTheDeletedOrgOnly() {
      assertEquals(Set.of(HOST_SESSION, ORPHAN_SESSION), visibleSessions(caller));
   }

   @Test
   void siteAdminCanEndTheSessionOfTheDeletedOrg() {
      controller.logout(new String[] { ORPHAN_SESSION }, "/", caller);

      verify(sessionRepository).invalidateSession("ignite-orphan");
   }

   @Test
   void siteAdminCannotEndAnExistingOtherOrgSession() {
      controller.logout(new String[] { OTHER_SESSION }, "/", caller);

      verify(sessionRepository, never()).invalidateSession(anyString());
   }

   @Test
   void orgAdminDoesNotSeeOrEndTheSessionOfTheDeletedOrg() {
      when(organizationManager.isSiteAdmin(caller)).thenReturn(false);

      assertEquals(Set.of(HOST_SESSION), visibleSessions(caller));
      controller.logout(new String[] { ORPHAN_SESSION }, "/", caller);
      verify(sessionRepository, never()).invalidateSession(anyString());
   }

   // the providers could not tell (loading, failed, empty): nothing extra is shown
   @Test
   void unknownAnswerShowsNothingExtra() {
      deleted.clear();

      assertEquals(Set.of(HOST_SESSION), visibleSessions(caller));
   }

   @Test
   void anonymousSessionsAreNotJudged() {
      Set<String> visible = visibleSessions(caller);

      assertFalse(visible.contains(GUEST_SESSION));
      assertEquals(1, asked.size());
      assertEquals(Set.of("gone-org", "live-org", HOST), asked.get(0));
   }

   @Test
   void deletedOrgSessionNeedsTheAdminPermission() {
      when(securityProvider.checkPermission(eq(caller), eq(ResourceType.SECURITY_USER),
         eq(ORPHAN_USER.convertToKey()), eq(ResourceAction.ADMIN))).thenReturn(false);

      assertEquals(Set.of(HOST_SESSION), visibleSessions(caller));
   }

   private Set<String> visibleSessions(Principal principal) {
      Set<String> result = new HashSet<>();
      userService.getServerSessionModel("/", principal)
         .forEach(m -> result.add(m.sessionID()));
      return result;
   }

   private void registerSession(String sessionId, String key) {
      SRPrincipal principal = mock(SRPrincipal.class);
      String name = sessionId.substring(sessionId.lastIndexOf('_') + 1);
      when(principal.getSessionID()).thenReturn(sessionId);
      when(principal.getName()).thenReturn(name);

      IgniteSessionRepository.IgniteSession session =
         mock(IgniteSessionRepository.IgniteSession.class);
      when(session.getAttribute(RepletRepository.PRINCIPAL_COOKIE)).thenReturn(principal);
      active.add(principal);
      when(sessionRepository.getActiveSessions()).thenReturn(active);
      when(sessionRepository.findByIndexNameAndIndexValue(anyString(), eq(name)))
         .thenReturn(Map.of(key, session));
   }

   private static SessionModel sessionModel(String sessionId, IdentityID user) {
      return SessionModel.builder()
         .address("127.0.0.1")
         .id(sessionId)
         .user(user)
         .dateCreated(0L)
         .dateAccessed(0L)
         .activeViewsheets(0)
         .organization(user.getOrgID())
         .build();
   }
}
