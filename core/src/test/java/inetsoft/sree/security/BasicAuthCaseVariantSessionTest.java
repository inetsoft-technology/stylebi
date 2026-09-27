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
package inetsoft.sree.security;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.db.DatabaseAuthenticationProvider;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.test.*;
import inetsoft.web.security.BasicAuthenticationFilter;
import inetsoft.web.security.support.FilterTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Bug #77081: end to end through the real {@link BasicAuthenticationFilter}, the real
 * {@link SecurityEngine} principal building and a {@link DatabaseAuthenticationProvider}.
 * Once the principal carries the provider's stored user name, a client that keeps sending a
 * case variant of it must keep its session under a case-insensitive provider, and a case
 * variant must never take over another user's session under a case-sensitive one.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BasicAuthCaseVariantSessionTest {
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private AuthenticationService authService;
   private SecurityProvider securityProvider;
   private SecurityEngine realEngine;
   private MockMvc mvc;
   private final String org = Organization.getDefaultOrganizationID();

   @AfterEach
   void tearDown() {
      if(securityEngineStatic != null) {
         securityEngineStatic.close();
      }
   }

   // caseSensitive=false, stored "bob": "Basic BOB:" on one session logs in once and keeps it
   @Test
   void caseInsensitive_caseVariantName_reusesSession() throws Exception {
      setUp(false, "bob");
      List<Result> results = perform("BOB", "BOB", "BOB");

      assertStatuses(results, 200, 200, 200);
      assertSameSession(results);
      assertEquals("bob" + IdentityID.KEY_DELIMITER + org, results.get(2).principal);
      verifyLogins(1);
      verify(authService, never()).logout(any(Principal.class), any(), any());
   }

   // control: the stored case itself
   @Test
   void caseInsensitive_storedName_reusesSession() throws Exception {
      setUp(false, "bob");
      List<Result> results = perform("bob", "bob", "bob");

      assertStatuses(results, 200, 200, 200);
      assertSameSession(results);
      verifyLogins(1);
      verify(authService, never()).logout(any(Principal.class), any(), any());
   }

   // caseSensitive=false with both "bob" and "BOB" stored: each is its own user, so "BOB"
   // does not reuse "bob"'s session and gets its own principal
   @Test
   void caseInsensitive_bothVariantsStored_caseVariantGetsOwnSession() throws Exception {
      setUp(false, "bob", "BOB");
      List<Result> results = perform("bob", "BOB");

      assertStatuses(results, 200, 200);
      assertEquals("bob" + IdentityID.KEY_DELIMITER + org, results.get(0).principal);
      assertEquals("BOB" + IdentityID.KEY_DELIMITER + org, results.get(1).principal);
      assertNotEquals(results.get(0).sessionId, results.get(1).sessionId);
      verifyLogins(2);
   }

   // caseSensitive=true, stored "bob": a "BOB" request never stays on "bob"'s session
   @Test
   void caseSensitive_caseVariantName_neverReusesSession() throws Exception {
      setUp(true, "bob");
      List<Result> results = perform("bob", "BOB");

      assertStatuses(results, 200, 401);
      verify(authService, atLeastOnce()).logout(any(Principal.class), any(), any());
      assertNull(results.get(1).principal);
   }

   // caseSensitive=true, both stored: "BOB" gets its own session, not "bob"'s
   @Test
   void caseSensitive_bothVariantsStored_caseVariantGetsOwnSession() throws Exception {
      setUp(true, "bob", "BOB");
      List<Result> results = perform("bob", "BOB");

      assertStatuses(results, 200, 200);
      assertEquals("BOB" + IdentityID.KEY_DELIMITER + org, results.get(1).principal);
      assertNotEquals(results.get(0).sessionId, results.get(1).sessionId);
      verifyLogins(2);
   }

   private void setUp(boolean caseSensitive, String... storedNames) throws Exception {
      DatabaseAuthenticationProvider db = createProvider(caseSensitive);
      IdentityID[] stored = Arrays.stream(storedNames)
         .map(n -> new IdentityID(n, org)).toArray(IdentityID[]::new);
      doReturn(stored).when(db).getUsers();
      doReturn(new String[] { org }).when(db).getOrganizationIDs();
      doReturn(new String[0]).when(db).getEmails(any(IdentityID.class));
      doReturn(new String[0]).when(db).getUserGroups(any(IdentityID.class));
      doReturn(new String[0]).when(db).getUserGroups(any(IdentityID.class), anyBoolean());
      doReturn(new IdentityID[0]).when(db).getRoles(any(IdentityID.class));
      // the password check: the users query binds the ticket's name; a case-insensitive
      // provider is assumed to run on a case-insensitive collation, a case-sensitive one not
      doAnswer(inv -> {
         IdentityID name = ((DefaultTicket) inv.getArgument(1)).getName();
         return Arrays.stream(stored)
            .anyMatch(s -> caseSensitive ? s.equals(name) : s.equalsIgnoreCase(name));
      }).when(db).authenticate(any(), any());

      securityProvider =
         CompositeSecurityProvider.create(db, mock(AuthorizationProvider.class));

      // the real principal-building code (SecurityEngine.authenticate(ClientInfo, ...))
      realEngine = new SecurityEngine(mock(LicenseManager.class), mock(Cluster.class));
      Field users = SecurityEngine.class.getDeclaredField("users");
      users.setAccessible(true);
      users.set(realEngine, new HashMap<>());

      // the engine the filter sees
      SecurityEngine filterEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(filterEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(filterEngine.getAuthenticationChain()).thenReturn(Optional.empty());
      when(filterEngine.getActivePrincipalList()).thenReturn(List.of());
      securityEngineStatic = mockStatic(
         SecurityEngine.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(filterEngine);

      authService = mock(AuthenticationService.class, withSettings().lenient());
      doAnswer(inv -> {
         IdentityID userId = inv.getArgument(0);
         String password = inv.getArgument(2);
         ClientInfo info = new ClientInfo(userId, "127.0.0.1", inv.getArgument(10));
         Principal principal =
            realEngine.authenticate(info, new DefaultTicket(userId, password), securityProvider);

         if(principal instanceof SRPrincipal srPrincipal) {
            srPrincipal.setProperty(SUtil.TICKET, userId + ":" + password);
         }

         return principal;
      }).when(authService).authenticate(
         any(), any(), any(), any(), any(), any(), any(), any(),
         anyBoolean(), anyBoolean(), any(), any());

      mvc = FilterTestSupport.builder()
         .withFilter(new BasicAuthenticationFilter(
            mock(SessionLicenseServiceProvider.class), authService))
         .build();
   }

   private static DatabaseAuthenticationProvider createProvider(boolean caseSensitive) {
      String old = SreeEnv.getProperty("security.user.caseSensitive");

      try {
         SreeEnv.setProperty("security.user.caseSensitive", Boolean.toString(caseSensitive));
         return spy(new DatabaseAuthenticationProvider());
      }
      finally {
         if(old == null) {
            SreeEnv.remove("security.user.caseSensitive");
         }
         else {
            SreeEnv.setProperty("security.user.caseSensitive", old);
         }
      }
   }

   // sends one request per name, carrying the session (the client's cookie) across them, and
   // snapshots each response before a later request can invalidate its session
   private List<Result> perform(String... names) throws Exception {
      List<Result> results = new ArrayList<>();
      MockHttpSession session = new MockHttpSession();

      for(String name : names) {
         MvcResult result = mvc.perform(post("/api/internal/data")
               .session(session)
               .header("Authorization", basicAuth(name, "pw")))
            .andReturn();
         MockHttpSession next = (MockHttpSession) result.getRequest().getSession(false);
         Principal principal = next == null ? null :
            (Principal) next.getAttribute(RepletRepository.PRINCIPAL_COOKIE);
         results.add(new Result(result.getResponse().getStatus(),
                                next == null ? null : next.getId(),
                                principal == null ? null : principal.getName()));
         session = next != null ? next : new MockHttpSession();
      }

      return results;
   }

   private void verifyLogins(int count) throws Exception {
      verify(authService, times(count)).authenticate(
         any(), any(), any(), any(), any(), any(), any(), any(),
         anyBoolean(), anyBoolean(), any(), any());
   }

   private static void assertStatuses(List<Result> results, int... statuses) {
      assertArrayEquals(statuses, results.stream().mapToInt(Result::status).toArray());
   }

   private static void assertSameSession(List<Result> results) {
      List<String> ids = results.stream().map(Result::sessionId).toList();
      assertEquals(1, new HashSet<>(ids).size(), "session ids: " + ids);
   }

   private record Result(int status, String sessionId, String principal) {
   }

   private static String basicAuth(String user, String pass) {
      byte[] bytes = (user + ":" + pass).getBytes(StandardCharsets.US_ASCII);
      return "Basic " + Base64.getEncoder().encodeToString(bytes);
   }
}
