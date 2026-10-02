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
package inetsoft.sree.web;

import inetsoft.report.internal.license.*;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.util.XSessionService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #77221: for normal logins {@link SRPrincipal#toIdentifier()} is per user, so the hosted
 * license service keeps one session entry for all of a user's logins on a node. Ending one login
 * must not stop that entry while another login of the same user is still active.
 */
@Tag("core")
class HostedSessionServiceTest {
   @BeforeEach
   void setUp() {
      AtomicInteger sessionCounter = new AtomicInteger();
      XSessionService sessionService = mock(XSessionService.class);
      when(sessionService.createSessionID(any(), any()))
         .thenAnswer(inv -> "xs" + sessionCounter.incrementAndGet());
      xSessionStatic = mockStatic(XSessionService.class);
      xSessionStatic.when(XSessionService::getService).thenReturn(sessionService);

      license = mock(License.class);
      when(license.type()).thenReturn(LicenseType.HOSTED);
      when(license.key()).thenReturn("hosted-key");

      LicenseManager licenseManager = mock(LicenseManager.class);
      when(licenseManager.getClaimedLicenses()).thenReturn(Set.of(license));

      hostedLicenseService = mock(HostedLicenseService.class);
      when(hostedLicenseService.startSession(any(), any())).thenReturn(true);

      licenseManagerStatic = mockStatic(LicenseManager.class);
      licenseManagerStatic.when(LicenseManager::getInstance).thenReturn(licenseManager);
      hostedStatic = mockStatic(HostedLicenseService.class);
      hostedStatic.when(HostedLicenseService::getInstance).thenReturn(hostedLicenseService);

      // every principal created by the test is an active login
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getActivePrincipalList()).thenReturn(activePrincipals);
      securityStatic = mockStatic(SecurityEngine.class);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);

      service = new SessionLicenseService.HostedSessionService();
   }

   @AfterEach
   void tearDown() {
      securityStatic.close();
      hostedStatic.close();
      licenseManagerStatic.close();
      xSessionStatic.close();
   }

   @Test
   void releasingOneLoginKeepsHostedSessionOfOtherLoginOfSameUser() {
      SRPrincipal browser1 = createPrincipal("S1", 111L);
      SRPrincipal browser2 = createPrincipal("S2", 222L);
      assertEquals(browser1.toIdentifier(), browser2.toIdentifier());

      service.newSession(browser1);
      service.newSession(browser2);
      service.releaseSession(browser1);

      verify(hostedLicenseService, never()).stopSession(any(), any());

      service.releaseSession(browser2);

      verify(hostedLicenseService, times(1)).stopSession("hosted-key", browser2);
   }

   @Test
   void releasingSoleLoginStopsHostedSession() {
      SRPrincipal browser1 = createPrincipal("S1", 111L);

      service.newSession(browser1);
      service.releaseSession(browser1);

      verify(hostedLicenseService, times(1)).stopSession("hosted-key", browser1);
   }

   @Test
   void differentUsersAreStoppedIndependently() {
      SRPrincipal user1 = createPrincipal("u1", "S1", 111L);
      SRPrincipal user2 = createPrincipal("u2", "S2", 222L);

      service.newSession(user1);
      service.newSession(user2);
      service.releaseSession(user1);

      verify(hostedLicenseService, times(1)).stopSession("hosted-key", user1);
      verify(hostedLicenseService, never()).stopSession("hosted-key", user2);
   }

   private SRPrincipal createPrincipal(String sessionId, long secureId) {
      return createPrincipal("u", sessionId, secureId);
   }

   private SRPrincipal createPrincipal(String user, String sessionId, long secureId) {
      IdentityID id = new IdentityID(user, "host-org");
      ClientInfo client = new ClientInfo(id, "10.0.0.5", sessionId);
      SRPrincipal principal = new SRPrincipal(client, new IdentityID[0], secureId);
      // normal password login, see SecurityEngine.authenticate()
      principal.setProperty("__internal__", "true");
      activePrincipals.add(principal);
      return principal;
   }

   private final List<SRPrincipal> activePrincipals = new ArrayList<>();
   private License license;
   private HostedLicenseService hostedLicenseService;
   private MockedStatic<LicenseManager> licenseManagerStatic;
   private MockedStatic<HostedLicenseService> hostedStatic;
   private MockedStatic<SecurityEngine> securityStatic;
   private MockedStatic<XSessionService> xSessionStatic;
   private SessionLicenseService.HostedSessionService service;
}
