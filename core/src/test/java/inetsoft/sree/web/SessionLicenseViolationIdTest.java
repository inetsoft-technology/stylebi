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

import inetsoft.report.internal.LicenseException;
import inetsoft.report.internal.license.*;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.util.XSessionService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #81815: every key violation reported by the session license services carries an id, so
 * that a repeated violation from the same source is logged once per window.
 */
@Tag("core")
class SessionLicenseViolationIdTest {
   @BeforeEach
   void setUp() {
      XSessionService sessionService = mock(XSessionService.class);
      when(sessionService.createSessionID(any(), any())).thenReturn("xs1");
      xSessionStatic = mockStatic(XSessionService.class);
      xSessionStatic.when(XSessionService::getService).thenReturn(sessionService);
      licenseManager = mock(LicenseManager.class);
      licenseManagerStatic = mockStatic(LicenseManager.class);
      licenseManagerStatic.when(LicenseManager::getInstance).thenReturn(licenseManager);
      sutilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutilStatic.when(SUtil::isCluster).thenReturn(false);
      principal = principal("bob", "S1");
   }

   @AfterEach
   void tearDown() {
      xSessionStatic.close();
      sutilStatic.close();
      licenseManagerStatic.close();
   }

   @Test
   void concurrentUsersViolationHasId() {
      SecurityEngine engine = mock(SecurityEngine.class);
      OrganizationManager orgManager = mock(OrganizationManager.class);

      try(MockedStatic<SecurityEngine> security = mockStatic(SecurityEngine.class);
          MockedStatic<OrganizationManager> org = mockStatic(OrganizationManager.class))
      {
         security.when(SecurityEngine::getSecurity).thenReturn(engine);
         org.when(OrganizationManager::getInstance).thenReturn(orgManager);
         when(orgManager.isSiteAdmin(any(SRPrincipal.class))).thenReturn(false);

         SessionLicenseService.ConcurrentSessionService service =
            new SessionLicenseService.ConcurrentSessionService(() -> 0, false);
         assertThrows(LicenseException.class, () -> service.newSession(principal));
      }

      assertViolationId("concurrent-users:");
   }

   @Test
   void namedUserViolationHasId() {
      when(licenseManager.getNamedUsers()).thenReturn(Set.of(new IdentityID("amy", "host-org")));

      SessionLicenseService.NamedSessionService service =
         new SessionLicenseService.NamedSessionService(() -> 10, false);
      assertThrows(LicenseException.class, () -> service.newSession(principal));

      assertViolationId("named-user:");
   }

   @Test
   void namedUserReplaceViolationHasId() {
      when(licenseManager.getNamedUsers()).thenReturn(Set.of(new IdentityID("amy", "host-org")));

      SessionLicenseService.NamedSessionService service =
         new SessionLicenseService.NamedSessionService(() -> 10, false);
      assertThrows(LicenseException.class, () -> service.newSession(principal, "S0"));

      assertViolationId("named-user:");
   }

   @Test
   void hostedHoursViolationHasId() {
      License license = mock(License.class);
      when(license.type()).thenReturn(LicenseType.HOSTED);
      when(licenseManager.getClaimedLicenses()).thenReturn(Set.of(license));
      HostedLicenseService hosted = mock(HostedLicenseService.class);
      when(hosted.startSession(any(), any())).thenReturn(false);
      SecurityEngine engine = mock(SecurityEngine.class);

      try(MockedStatic<HostedLicenseService> hostedStatic = mockStatic(HostedLicenseService.class);
          MockedStatic<SecurityEngine> security = mockStatic(SecurityEngine.class))
      {
         hostedStatic.when(HostedLicenseService::getInstance).thenReturn(hosted);
         security.when(SecurityEngine::getSecurity).thenReturn(engine);

         SessionLicenseService.HostedSessionService service =
            new SessionLicenseService.HostedSessionService();
         assertThrows(LicenseException.class, () -> service.newSession(principal));
      }

      assertViolationId("hosted-hours:");
   }

   @Test
   void idsDifferPerUser() {
      when(licenseManager.getNamedUsers()).thenReturn(Set.of());
      SessionLicenseService.NamedSessionService service =
         new SessionLicenseService.NamedSessionService(() -> 10, false);

      assertThrows(LicenseException.class, () -> service.newSession(principal));
      assertThrows(LicenseException.class, () -> service.newSession(principal("amy", "S2")));

      ArgumentCaptor<String> ids = ArgumentCaptor.forClass(String.class);
      verify(licenseManager, times(2)).addKeyViolation(anyString(), ids.capture());
      assertNotEquals(ids.getAllValues().get(0), ids.getAllValues().get(1));
   }

   private void assertViolationId(String prefix) {
      ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
      verify(licenseManager).addKeyViolation(anyString(), id.capture());
      assertNotNull(id.getValue(), "the violation has no id, so it is never de-duplicated");
      assertTrue(id.getValue().startsWith(prefix), id.getValue());
      assertTrue(id.getValue().contains("bob"), id.getValue());
   }

   private SRPrincipal principal(String user, String sessionId) {
      ClientInfo client = new ClientInfo(new IdentityID(user, "host-org"), "10.0.0.5", sessionId);
      return new SRPrincipal(client, new IdentityID[0], 1L);
   }

   private LicenseManager licenseManager;
   private MockedStatic<LicenseManager> licenseManagerStatic;
   private MockedStatic<SUtil> sutilStatic;
   private MockedStatic<XSessionService> xSessionStatic;
   private SRPrincipal principal;
}
