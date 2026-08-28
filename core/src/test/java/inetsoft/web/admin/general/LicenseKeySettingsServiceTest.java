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
package inetsoft.web.admin.general;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.AuthenticationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * 01-spec.md section 0: {@code addServerKey}/{@code removeServerKey} must call
 * {@code LicenseManager.addLicense}/{@code removeLicense} and then replicate {@code setModel}'s own
 * two side effects -- {@code cluster.sendMessage(new ResetLicenseKeyMessage())} and
 * {@code authenticationService.reset()} -- in that exact order.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class LicenseKeySettingsServiceTest {
   @Mock private LicenseManager licenseManager;
   @Mock private Cluster cluster;
   @Mock private AuthenticationService authenticationService;
   private LicenseKeySettingsService service;

   @BeforeEach
   void setUp() {
      service = new LicenseKeySettingsService(licenseManager, cluster, authenticationService);
   }

   @Test
   void addServerKeyCallsAddLicenseThenBroadcastsThenResetsAuth() throws Exception {
      InOrder order = inOrder(licenseManager, cluster, authenticationService);

      service.addServerKey("KEY-1");

      order.verify(licenseManager).addLicense("KEY-1");
      order.verify(cluster).sendMessage(any(ResetLicenseKeyMessage.class));
      order.verify(authenticationService).reset();
      verifyNoMoreInteractions(licenseManager, cluster, authenticationService);
   }

   @Test
   void removeServerKeyCallsRemoveLicenseThenBroadcastsThenResetsAuth() throws Exception {
      InOrder order = inOrder(licenseManager, cluster, authenticationService);

      service.removeServerKey("KEY-1");

      order.verify(licenseManager).removeLicense("KEY-1");
      order.verify(cluster).sendMessage(any(ResetLicenseKeyMessage.class));
      order.verify(authenticationService).reset();
      verifyNoMoreInteractions(licenseManager, cluster, authenticationService);
   }
}
