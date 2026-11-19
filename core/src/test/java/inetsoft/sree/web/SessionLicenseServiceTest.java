/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.sree.web;

import inetsoft.report.internal.LicenseException;
import inetsoft.report.internal.UnlicensedUserNameException;
import inetsoft.report.internal.license.*;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.security.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Seat acquire and release of the concurrent, named and hosted session license services.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  SessionLicenseServiceTest.LogoutConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SessionLicenseServiceTest {
   @Configuration
   static class LogoutConfiguration {
      @Bean
      AuthenticationService authenticationService() {
         return mock(AuthenticationService.class);
      }
   }

   @BeforeEach
   void setUp() {
      savedPrincipal = ThreadContext.getPrincipal();
      reset(authenticationService);
      loggedOut.clear();

      // like the provider does, a logout is delivered to the session services that extend
      // AbstractSessionService
      doAnswer(inv -> {
         Principal principal = inv.getArgument(0);
         loggedOut.add(principal);

         if(routeLogoutTo instanceof AbstractSessionService service) {
            service.loggedOut(new SessionLoggedOutEvent(this, principal, inv.getArgument(1)));
         }

         return null;
      }).when(authenticationService).logout(any(Principal.class), anyBoolean());

      hostedLicense = mock(License.class);
      when(hostedLicense.type()).thenReturn(LicenseType.HOSTED);
      when(hostedLicense.key()).thenReturn("hosted-key");

      licenseManager = mock(LicenseManager.class);
      when(licenseManager.getClaimedLicenses()).thenReturn(Set.of(hostedLicense));
      when(licenseManager.getNamedUsers()).thenReturn(null);

      hostedLicenseService = mock(HostedLicenseService.class);
      when(hostedLicenseService.startSession(any(), any())).thenReturn(true);

      licenseManagerStatic = mockStatic(LicenseManager.class);
      licenseManagerStatic.when(LicenseManager::getInstance).thenReturn(licenseManager);
      hostedStatic = mockStatic(HostedLicenseService.class);
      hostedStatic.when(HostedLicenseService::getInstance).thenReturn(hostedLicenseService);

      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.isSystemAdministratorRole(ADMIN_ROLE)).thenReturn(true);
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getActivePrincipalList()).thenReturn(activePrincipals);
      when(securityEngine.getSecurityProvider()).thenReturn(provider);
      securityStatic = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);
   }

   @AfterEach
   void tearDown() {
      securityStatic.close();
      hostedStatic.close();
      licenseManagerStatic.close();
      routeLogoutTo = null;
      ThreadContext.setPrincipal(savedPrincipal);
   }

   @Test
   void newSessionBelowLimitSeatsPrincipal() {
      SessionLicenseService.ConcurrentSessionService service = concurrent(2);
      SRPrincipal p1 = principal("u1", "10.0.0.1", "s1");

      service.newSession(p1);

      assertEquals(Set.of(p1), service.getActiveSessions());
   }

   @Test
   void newSessionAtLimitRejectsPrincipalWithoutSeat() {
      SessionLicenseService.ConcurrentSessionService service = concurrent(1);
      SRPrincipal p1 = principal("u1", "10.0.0.1", "s1");
      SRPrincipal p2 = principal("u2", "10.0.0.2", "s2");
      service.newSession(p1);

      LicenseException ex = assertThrows(LicenseException.class, () -> service.newSession(p2));

      assertFalse(ex instanceof SessionsExceededException);
      assertEquals(Set.of(p1), service.getActiveSessions());
   }

   @Test
   void newSessionAtLimitForSiteAdminReportsActiveSessions() {
      SessionLicenseService.ConcurrentSessionService service = concurrent(1);
      SRPrincipal p1 = principal("u1", "10.0.0.1", "s1");
      SRPrincipal admin = adminPrincipal("root", "10.0.0.2", "s2");
      service.newSession(p1);

      SessionsExceededException ex =
         assertThrows(SessionsExceededException.class, () -> service.newSession(admin));

      assertEquals(1, ex.getActiveSessions().size());
      assertEquals(p1.getSessionID(), ex.getActiveSessions().get(0).getSessionId());
      assertEquals("u1", ex.getActiveSessions().get(0).getUsername());
      assertEquals(Set.of(p1), service.getActiveSessions());
   }

   @Test
   void newSessionOfSeatedPrincipalAtLimitKeepsOneSeat() {
      SessionLicenseService.ConcurrentSessionService service = concurrent(1);
      SRPrincipal p1 = principal("u1", "10.0.0.1", "s1");

      service.newSession(p1);
      assertDoesNotThrow(() -> service.newSession(p1));

      assertEquals(Set.of(p1), service.getActiveSessions());
   }

   @Test
   void newSessionRejectsWhenLimitDropsBelowSeatedCount() {
      AtomicInteger max = new AtomicInteger(2);
      SessionLicenseService.ConcurrentSessionService service =
         new SessionLicenseService.ConcurrentSessionService(max::get, false);
      SRPrincipal p1 = principal("u1", "10.0.0.1", "s1");
      SRPrincipal p2 = principal("u2", "10.0.0.2", "s2");
      SRPrincipal p3 = principal("u3", "10.0.0.3", "s3");
      service.newSession(p1);
      service.newSession(p2);
      max.set(1);

      assertThrows(LicenseException.class, () -> service.newSession(p3));

      assertFalse(service.getActiveSessions().contains(p3));
   }

   @Test
   void releasedSeatIsAvailableToNextPrincipal() {
      SessionLicenseService.ConcurrentSessionService service = concurrent(1);
      SRPrincipal p1 = principal("u1", "10.0.0.1", "s1");
      SRPrincipal p2 = principal("u2", "10.0.0.2", "s2");
      service.newSession(p1);

      service.releaseSession(p1);
      assertDoesNotThrow(() -> service.newSession(p2));

      assertEquals(Set.of(p2), service.getActiveSessions());
   }

   @ParameterizedTest
   @ValueSource(strings = { "concurrent", "named" })
   void releasingSamePrincipalTwiceKeepsOtherSeats(String type) {
      SessionLicenseManager service = "named".equals(type) ?
         new SessionLicenseService.NamedSessionService(() -> 5, false) : concurrent(5);
      SRPrincipal p1 = principal("u1", "10.0.0.1", "s1");
      SRPrincipal p2 = principal("u2", "10.0.0.2", "s2");
      service.newSession(p1);
      service.newSession(p2);

      service.releaseSession(p1);
      assertDoesNotThrow(() -> service.releaseSession(p1));

      assertEquals(Set.of(p2), service.getActiveSessions());
   }

   @Test
   void newSessionWithReplaceIdTerminatesAndReleasesThatSession() {
      SessionLicenseService.ConcurrentSessionService service = concurrent(1, true);
      routeLogoutTo = service;
      SRPrincipal p1 = principal("u1", "10.0.0.1", "s1");
      SRPrincipal p2 = principal("u2", "10.0.0.2", "s2");
      service.newSession(p1);

      assertDoesNotThrow(() -> service.newSession(p2, p1.getSessionID()));

      assertTrue(loggedOut.contains(p1));
      assertEquals(Set.of(p2), service.getActiveSessions());
   }

   @ParameterizedTest
   @ValueSource(strings = { "concurrent", "hosted" })
   void newSessionFreesSeatOfPrincipalMissingFromSecurityEngine(String type) {
      SessionLicenseManager service = "hosted".equals(type) ?
         new SessionLicenseService.HostedSessionService() : concurrent(1);
      SRPrincipal p1 = principal("u1", "10.0.0.1", "s1");
      SRPrincipal p2 = principal("u2", "10.0.0.2", "s2");
      service.newSession(p1);
      // the security engine no longer knows p1
      activePrincipals.remove(p1);

      assertDoesNotThrow(() -> service.newSession(p2));

      assertEquals(Set.of(p2), service.getActiveSessions());
   }

   @Test
   void namedSessionRejectsUserNotInNamedList() {
      when(licenseManager.getNamedUsers()).thenReturn(Set.of(new IdentityID("alice", ORG)));
      SessionLicenseService.NamedSessionService service =
         new SessionLicenseService.NamedSessionService(() -> 5, false);
      SRPrincipal bob = principal("bob", "10.0.0.1", "s1");

      assertThrows(UnlicensedUserNameException.class, () -> service.newSession(bob));

      assertTrue(service.getActiveSessions().isEmpty());
   }

   @Test
   void namedSessionLogsOutSameUserActiveOnOtherAddress() {
      SessionLicenseService.NamedSessionService service =
         new SessionLicenseService.NamedSessionService();
      SRPrincipal old = principal("alice", "10.0.0.1", "s1");
      SRPrincipal fresh = principal("alice", "10.0.0.2", "s2");
      service.newSession(old);

      assertDoesNotThrow(() -> service.newSession(fresh));

      verify(authenticationService).logout(old, true);
      assertTrue(service.getActiveSessions().contains(fresh));
   }

   @Test
   void namedSessionKeepsSameUserActiveOnSameAddress() {
      SessionLicenseService.NamedSessionService service =
         new SessionLicenseService.NamedSessionService();
      SRPrincipal first = principal("alice", "10.0.0.1", "s1");
      SRPrincipal second = principal("alice", "10.0.0.1", "s2");
      service.newSession(first);

      assertDoesNotThrow(() -> service.newSession(second));

      verify(authenticationService, never()).logout(any(Principal.class), anyBoolean());
   }

   @Disabled("Not filed (the intent is unclear, the one-seat comment sits on the unbounded constructor; left as is): NamedSessionService with a limit of 1: a second principal of the same user on the same address gets LicenseException (Concurrent sessions exceed the installed license limit) at the second newSession, the comment says one seat per user and machine")
   @Test
   void namedViewerSessionOfSameUserOnSameAddressUsesOneSeat() {
      // the viewer variant of the named service, limited to one seat
      SessionLicenseService.NamedSessionService service =
         new SessionLicenseService.NamedSessionService(() -> 1, false);
      SRPrincipal first = principal("alice", "10.0.0.1", "s1");
      SRPrincipal second = principal("alice", "10.0.0.1", "s2");

      assertDoesNotThrow(() -> service.newSession(first));
      assertDoesNotThrow(() -> service.newSession(second));
   }

   @Disabled("Bug #81813: <License> NamedSessionService.newSession(principal, sessionIdToReplace) does not log out the same user's session on another IP address")
   @Test
   void namedSessionWithReplaceIdLogsOutSameUserActiveOnOtherAddressLikeSingleArgument() {
      SessionLicenseService.NamedSessionService service =
         new SessionLicenseService.NamedSessionService();
      SRPrincipal old = principal("alice", "10.0.0.1", "s1");
      SRPrincipal fresh = principal("alice", "10.0.0.2", "s2");
      service.newSession(old);

      assertDoesNotThrow(() -> service.newSession(fresh, "unrelated-session"));

      verify(authenticationService).logout(old, true);
   }

   private SessionLicenseService.ConcurrentSessionService concurrent(int max) {
      return concurrent(max, false);
   }

   private SessionLicenseService.ConcurrentSessionService concurrent(
      int max, boolean logoutAfterFailure)
   {
      Supplier<Integer> supplier = () -> max;
      return new SessionLicenseService.ConcurrentSessionService(supplier, logoutAfterFailure);
   }

   private SRPrincipal principal(String user, String address, String sessionId) {
      return createPrincipal(user, address, sessionId, new IdentityID[0]);
   }

   private SRPrincipal adminPrincipal(String user, String address, String sessionId) {
      return createPrincipal(user, address, sessionId, new IdentityID[] { ADMIN_ROLE });
   }

   private SRPrincipal createPrincipal(String user, String address, String sessionId,
                                       IdentityID[] roles)
   {
      ClientInfo client = new ClientInfo(new IdentityID(user, ORG), address, sessionId);
      SRPrincipal principal = new SRPrincipal(client, roles, secureIds.incrementAndGet());
      activePrincipals.add(principal);
      return principal;
   }

   private static final String ORG = "host-org";
   private static final IdentityID ADMIN_ROLE = new IdentityID("Site Administrator", ORG);

   @Autowired
   private AuthenticationService authenticationService;
   private final List<SRPrincipal> activePrincipals = new ArrayList<>();
   private final List<Principal> loggedOut = new ArrayList<>();
   private final AtomicLong secureIds = new AtomicLong();
   private SessionLicenseManager routeLogoutTo;
   private Principal savedPrincipal;
   private License hostedLicense;
   private LicenseManager licenseManager;
   private HostedLicenseService hostedLicenseService;
   private SecurityEngine securityEngine;
   private MockedStatic<LicenseManager> licenseManagerStatic;
   private MockedStatic<HostedLicenseService> hostedStatic;
   private MockedStatic<SecurityEngine> securityStatic;
}
