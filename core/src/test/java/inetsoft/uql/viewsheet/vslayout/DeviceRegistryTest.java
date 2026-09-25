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
package inetsoft.uql.viewsheet.vslayout;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.KeyValuePair;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/*
 * Device profiles are stored in a single global DeviceRegistry, not per-organization (see
 * Bug #75603 follow-up). isOrgAllowedToEditDevices() mirrors the (!enterprise || isSiteAdmin ||
 * currentOrg == defaultOrg) gate already applied to the "Edit Mobile Devices" UI control in
 * ViewsheetPropertyDialogService, so that DeviceController can enforce the same restriction
 * server-side instead of only checking the DEVICE:*:ACCESS permission.
 */
@ExtendWith(MockitoExtension.class)
@Tag("core")
class DeviceRegistryTest {

   @Mock
   private Principal principal;

   @Test
   void isOrgAllowedToEditDevices_notEnterprise_allowedRegardlessOfOrg() {
      try(MockedStatic<LicenseManager> license = mockStatic(LicenseManager.class)) {
         license.when(LicenseManager::isEnterprise).thenReturn(false);

         assertTrue(DeviceRegistry.isOrgAllowedToEditDevices(principal));
      }
   }

   @Test
   void isOrgAllowedToEditDevices_enterpriseSiteAdmin_allowed() {
      try(MockedStatic<LicenseManager> license = mockStatic(LicenseManager.class);
          MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class))
      {
         license.when(LicenseManager::isEnterprise).thenReturn(true);
         OrganizationManager orgManager = mock(OrganizationManager.class);
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
         when(orgManager.isSiteAdmin(principal)).thenReturn(true);

         assertTrue(DeviceRegistry.isOrgAllowedToEditDevices(principal));
      }
   }

   @Test
   void isOrgAllowedToEditDevices_enterpriseNonSiteAdminInDefaultOrg_allowed() {
      try(MockedStatic<LicenseManager> license = mockStatic(LicenseManager.class);
          MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class))
      {
         license.when(LicenseManager::isEnterprise).thenReturn(true);
         OrganizationManager orgManager = mock(OrganizationManager.class);
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
         when(orgManager.isSiteAdmin(principal)).thenReturn(false);
         // mixed case on purpose: getCurrentOrgID(principal) is not lower-cased by the provider
         // itself (unlike the no-arg overload), so the comparison must normalize case itself
         when(orgManager.getCurrentOrgID(principal))
            .thenReturn(Organization.getDefaultOrganizationID().toUpperCase());

         assertTrue(DeviceRegistry.isOrgAllowedToEditDevices(principal));
      }
   }

   @Test
   void isOrgAllowedToEditDevices_enterpriseOrgAdminInNonDefaultOrg_denied() {
      try(MockedStatic<LicenseManager> license = mockStatic(LicenseManager.class);
          MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class))
      {
         license.when(LicenseManager::isEnterprise).thenReturn(true);
         OrganizationManager orgManager = mock(OrganizationManager.class);
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
         when(orgManager.isSiteAdmin(principal)).thenReturn(false);
         when(orgManager.getCurrentOrgID(principal)).thenReturn("orgB");

         assertFalse(DeviceRegistry.isOrgAllowedToEditDevices(principal));
      }
   }

   /*
    * Regression test for Bug #77019: KeyValueStorageManager's shared 50-store LRU cache can
    * evict and close the "devices" store (e.g. after creating several organizations in
    * multi-tenant mode). A closed store's stream() silently returns empty forever unless
    * DeviceRegistry re-fetches, which used to permanently empty the Composer "Screens" device
    * list and, via a subsequent setDevices()/replaceAll(), wipe the real default devices from
    * storage. Simulates the eviction by making the originally-cached KeyValueStorage report
    * isClosed() == true, then confirms getDevices() recovers non-empty data on the very next
    * call instead of staying permanently empty.
    */
   @SuppressWarnings("unchecked")
   @Test
   void getDevices_afterUnderlyingStorageEvictedAndClosed_reFetchesAndRecovers() {
      KeyValueStorage<DeviceInfo> evictedStorage = mock(KeyValueStorage.class);
      KeyValueStorage<DeviceInfo> freshStorage = mock(KeyValueStorage.class);
      KeyValueStorageManager kvStorageManager = mock(KeyValueStorageManager.class);

      when(kvStorageManager.<DeviceInfo>getStorage(eq("devices"), any()))
         .thenReturn(evictedStorage, freshStorage);

      DeviceInfo device = new DeviceInfo();
      device.setId("iPhone");
      when(evictedStorage.isClosed()).thenReturn(false, true);
      when(evictedStorage.stream())
         .thenReturn(Stream.of(new KeyValuePair<>("iPhone", device)));
      when(freshStorage.stream())
         .thenReturn(Stream.of(new KeyValuePair<>("iPhone", device)));

      DeviceRegistry registry = new DeviceRegistry(kvStorageManager);

      // first call: storage reports open, real devices come back
      assertEquals(1, registry.getDevices().length,
                  "sanity check: devices load normally before eviction");

      // second call: storage now reports closed (simulating LRU eviction) -- getDevices() must
      // re-fetch a fresh storage instance instead of permanently returning an empty array
      List<DeviceInfo> devices = List.of(registry.getDevices());
      assertEquals(1, devices.size(),
                  "getDevices() must recover non-empty data by re-fetching storage once the " +
                  "cached instance reports closed, instead of staying permanently empty");
   }
}
