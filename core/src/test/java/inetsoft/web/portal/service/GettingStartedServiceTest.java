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
package inetsoft.web.portal.service;

/*
 * GettingStartedService coverage map — [unit] tier, Mockito stubs SecurityEngine and repositories
 *
 * Cases deferred — require indexed storage + asset repository stream integration:
 *
 * [GettingStartedService] hasCreatedAssets() full asset-matching pipeline (minimal empty-keys case only)
 */

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.portal.PortalTab;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.IndexedStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class GettingStartedServiceTest {

   private static final IdentityID USER_ID =
      new IdentityID("user1", Organization.getSelfOrganizationID());

   @Mock private AssetRepository assetRepository;
   @Mock private AnalyticRepository analyticRepository;
   @Mock private SecurityEngine securityEngine;
   @Mock private IndexedStorage indexedStorage;
   @Mock private PortalThemesManager portalThemesManager;
   @Mock private XPrincipal principal;

   private GettingStartedService gettingStartedService;

   @BeforeEach
   void setUp() {
      gettingStartedService = new GettingStartedService(
         assetRepository, analyticRepository, securityEngine, indexedStorage, portalThemesManager);
   }

   // -------------------------------------------------------------------------
   // getDefaultFolder — self-organization user scope folders
   // -------------------------------------------------------------------------

   @Nested
   class GetDefaultFolder {

      @Test
      void selfOrgWorksheetType_returnsUserFolderIdentifier() {
         when(principal.getOrgId()).thenReturn(Organization.getSelfOrganizationID());
         when(principal.getName()).thenReturn(USER_ID.convertToKey());

         String expected = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.FOLDER, "/",
            USER_ID, Organization.getSelfOrganizationID()).toIdentifier();

         assertEquals(expected,
            gettingStartedService.getDefaultFolder(principal, AssetEntry.Type.WORKSHEET));
      }

      @Test
      void selfOrgDashboardType_returnsRepositoryFolderIdentifier() {
         when(principal.getOrgId()).thenReturn(Organization.getSelfOrganizationID());
         when(principal.getName()).thenReturn(USER_ID.convertToKey());

         String expected = new AssetEntry(AssetRepository.USER_SCOPE,
            AssetEntry.Type.REPOSITORY_FOLDER, "/", USER_ID,
            Organization.getSelfOrganizationID()).toIdentifier();

         assertEquals(expected,
            gettingStartedService.getDefaultFolder(principal, AssetEntry.Type.DASHBOARD));
      }

      @Test
      void nonSelfOrg_returnsNull() {
         when(principal.getOrgId()).thenReturn(Organization.getDefaultOrganizationID());

         assertNull(gettingStartedService.getDefaultFolder(principal, AssetEntry.Type.WORKSHEET));
      }
   }

   // -------------------------------------------------------------------------
   // Permission checks — SecurityEngine delegation
   // -------------------------------------------------------------------------

   @Nested
   class PermissionChecks {

      @Test
      void hasCreateWSPermission_granted_returnsTrue() throws Exception {
         when(securityEngine.checkPermission(principal, ResourceType.WORKSHEET,
            "*", ResourceAction.ACCESS)).thenReturn(true);

         assertTrue(gettingStartedService.hasCreateWSPermission(principal));
      }

      @Test
      void hasCreateWSPermission_securityException_returnsFalse() throws Exception {
         when(securityEngine.checkPermission(principal, ResourceType.WORKSHEET,
            "*", ResourceAction.ACCESS)).thenThrow(new inetsoft.sree.security.SecurityException("denied"));

         assertFalse(gettingStartedService.hasCreateWSPermission(principal));
      }

      @Test
      void hasDashboardPermission_granted_returnsTrue() throws Exception {
         when(securityEngine.checkPermission(principal, ResourceType.VIEWSHEET,
            "*", ResourceAction.ACCESS)).thenReturn(true);

         assertTrue(gettingStartedService.hasDashboardPermission(principal));
      }

      @Test
      void checkCreateDatasourcePermission_noDataTab_returnsFalse() {
         PortalTab otherTab = mock(PortalTab.class);
         when(otherTab.getName()).thenReturn("Home");
         when(portalThemesManager.getPortalTabs()).thenReturn(List.of(otherTab));

         assertFalse(gettingStartedService.checkCreateDatasourcePermission(principal));
      }

      @Test
      void checkCreateDatasourcePermission_dataTabAndCreatePermission_returnsTrue() throws Exception {
         PortalTab dataTab = mock(PortalTab.class);
         when(dataTab.getName()).thenReturn("Data");
         when(portalThemesManager.getPortalTabs()).thenReturn(List.of(dataTab));
         when(securityEngine.checkPermission(principal, ResourceType.CREATE_DATA_SOURCE,
            "*", ResourceAction.ACCESS)).thenReturn(true);

         assertTrue(gettingStartedService.checkCreateDatasourcePermission(principal));
      }

      @Test
      void hasPermission_allChecksPass_returnsTrue() throws Exception {
         PortalTab dataTab = mock(PortalTab.class);
         when(dataTab.getName()).thenReturn("Data");
         when(portalThemesManager.getPortalTabs()).thenReturn(List.of(dataTab));
         when(securityEngine.checkPermission(principal, ResourceType.CREATE_DATA_SOURCE,
            "*", ResourceAction.ACCESS)).thenReturn(true);
         when(securityEngine.checkPermission(principal, ResourceType.VIEWSHEET,
            "*", ResourceAction.ACCESS)).thenReturn(true);
         when(securityEngine.checkPermission(principal, ResourceType.WORKSHEET,
            "*", ResourceAction.ACCESS)).thenReturn(true);

         assertTrue(gettingStartedService.hasPermission(principal));
      }
   }

   // -------------------------------------------------------------------------
   // hasCreatedAssets — minimal empty-storage contract
   // -------------------------------------------------------------------------

   @Nested
   class HasCreatedAssets {

      @Test
      void noIndexedKeys_returnsFalse() {
         when(principal.getName()).thenReturn(USER_ID.convertToKey());
         when(indexedStorage.getKeys(any())).thenReturn(Collections.emptySet());

         assertFalse(gettingStartedService.hasCreatedAssets(principal));
      }
   }
}
