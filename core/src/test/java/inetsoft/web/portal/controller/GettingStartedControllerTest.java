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
package inetsoft.web.portal.controller;

/*
 * GettingStartedController coverage map — [unit] tier, Mockito stubs GettingStartedService
 *
 * Cases deferred — folderId format / getDefaultFolder logic:
 *
 * [GettingStartedService] getDefaultFolder() -> covered in GettingStartedServiceTest
 *
 * Cases deferred — hasCreatedAssets stream pipeline:
 *
 * [GettingStartedService] hasCreatedAssets() -> indexed storage + asset repository integration
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.portal.model.GettingStartedAssetDefaultFolder;
import inetsoft.web.portal.service.GettingStartedService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class GettingStartedControllerTest {

   private static final String DEFAULT_FOLDER_ID = "folder-id-from-service";

   @Mock private GettingStartedService gettingStartedService;
   @Mock private SecurityEngine securityEngine;
   @Mock private XPrincipal xPrincipal;

   private GettingStartedController gettingStartedController;
   private MockedStatic<SreeEnv> sreeEnvMock;

   @BeforeEach
   void setUp() {
      gettingStartedController = new GettingStartedController(gettingStartedService, securityEngine);
      sreeEnvMock = Mockito.mockStatic(SreeEnv.class);
      sreeEnvMock.when(() -> SreeEnv.getProperty("getting.started")).thenReturn(null);
   }

   @AfterEach
   void tearDown() {
      if(sreeEnvMock != null) {
         sreeEnvMock.close();
      }
   }

   // -------------------------------------------------------------------------
   // showGettingStarted
   // -------------------------------------------------------------------------

   @Nested
   class ShowGettingStarted {

      @Test
      void sreeEnvOverride_returnsConfiguredValue() {
         sreeEnvMock.when(() -> SreeEnv.getProperty("getting.started")).thenReturn("false");

         assertEquals("false", gettingStartedController.showGettingStarted(xPrincipal));
         verifyNoInteractions(gettingStartedService);
      }

      @Test
      void principalShowFlagNotTrue_returnsFalse() {
         when(xPrincipal.getProperty("showGettingStated")).thenReturn("false");

         assertEquals("false", gettingStartedController.showGettingStarted(xPrincipal));
         verifyNoInteractions(gettingStartedService);
      }

      @Test
      void principalShowFlagTrue_evaluatesServiceAndClearsProperty() {
         when(xPrincipal.getProperty("showGettingStated")).thenReturn("true");
         when(gettingStartedService.hasPermission(xPrincipal)).thenReturn(true);
         when(gettingStartedService.hasCreatedAssets(xPrincipal)).thenReturn(false);

         assertEquals("true", gettingStartedController.showGettingStarted(xPrincipal));

         verify(xPrincipal).setProperty("showGettingStated", null);
         verify(gettingStartedService).hasPermission(xPrincipal);
         verify(gettingStartedService).hasCreatedAssets(xPrincipal);
      }

      @Test
      void nonXPrincipal_skipsShowFlagGate() {
         Principal plainPrincipal = mock(Principal.class);
         when(gettingStartedService.hasPermission(plainPrincipal)).thenReturn(true);
         when(gettingStartedService.hasCreatedAssets(plainPrincipal)).thenReturn(false);

         assertEquals("true", gettingStartedController.showGettingStarted(plainPrincipal));

         verify(gettingStartedService).hasPermission(plainPrincipal);
         verify(gettingStartedService).hasCreatedAssets(plainPrincipal);
      }
   }

   // -------------------------------------------------------------------------
   // checkCreateQueryPermission
   // -------------------------------------------------------------------------

   @Nested
   class CheckCreateQueryPermission {

      @Test
      void noWorksheetPermission_returnsError() throws Exception {
         when(gettingStartedService.hasCreateWSPermission(xPrincipal)).thenReturn(false);

         GettingStartedAssetDefaultFolder result =
            gettingStartedController.checkCreateQueryPermission(xPrincipal);

         assertNotNull(result.errorMessage());
         assertNull(result.folderId());
         verify(securityEngine, never()).checkPermission(
            any(Principal.class), any(ResourceType.class), anyString(), any(ResourceAction.class));
      }

      @Test
      void noPhysicalTableAccess_returnsError() throws Exception {
         when(gettingStartedService.hasCreateWSPermission(xPrincipal)).thenReturn(true);
         when(securityEngine.checkPermission(xPrincipal, ResourceType.PHYSICAL_TABLE,
            "*", ResourceAction.ACCESS)).thenReturn(false);

         GettingStartedAssetDefaultFolder result =
            gettingStartedController.checkCreateQueryPermission(xPrincipal);

         assertNotNull(result.errorMessage());
         assertNull(result.folderId());
         verify(gettingStartedService, never()).getDefaultFolder(any(), any());
      }

      @Test
      void authorized_returnsDefaultWorksheetFolder() throws Exception {
         when(gettingStartedService.hasCreateWSPermission(xPrincipal)).thenReturn(true);
         when(securityEngine.checkPermission(xPrincipal, ResourceType.PHYSICAL_TABLE,
            "*", ResourceAction.ACCESS)).thenReturn(true);
         when(gettingStartedService.getDefaultFolder(xPrincipal, AssetEntry.Type.WORKSHEET))
            .thenReturn(DEFAULT_FOLDER_ID);

         GettingStartedAssetDefaultFolder result =
            gettingStartedController.checkCreateQueryPermission(xPrincipal);

         assertEquals(DEFAULT_FOLDER_ID, result.folderId());
         assertNull(result.errorMessage());
      }
   }

   // -------------------------------------------------------------------------
   // checkCreateWSPermission
   // -------------------------------------------------------------------------

   @Nested
   class CheckCreateWSPermission {

      @Test
      void noWorksheetPermission_returnsError() {
         when(gettingStartedService.hasCreateWSPermission(xPrincipal)).thenReturn(false);

         GettingStartedAssetDefaultFolder result =
            gettingStartedController.checkCreateWSPermission(xPrincipal);

         assertNotNull(result.errorMessage());
         assertNull(result.folderId());
      }

      @Test
      void authorized_returnsDefaultWorksheetFolder() {
         when(gettingStartedService.hasCreateWSPermission(xPrincipal)).thenReturn(true);
         when(gettingStartedService.getDefaultFolder(xPrincipal, AssetEntry.Type.WORKSHEET))
            .thenReturn(DEFAULT_FOLDER_ID);

         GettingStartedAssetDefaultFolder result =
            gettingStartedController.checkCreateWSPermission(xPrincipal);

         assertEquals(DEFAULT_FOLDER_ID, result.folderId());
         assertNull(result.errorMessage());
      }
   }

   // -------------------------------------------------------------------------
   // getVSDefaultSaveFolder
   // -------------------------------------------------------------------------

   @Nested
   class GetVSDefaultSaveFolder {

      @Test
      void noDashboardPermission_returnsError() {
         when(gettingStartedService.hasDashboardPermission(xPrincipal)).thenReturn(false);

         GettingStartedAssetDefaultFolder result =
            gettingStartedController.getVSDefaultSaveFolder(xPrincipal);

         assertNotNull(result.errorMessage());
         assertNull(result.folderId());
      }

      @Test
      void authorized_returnsDefaultDashboardFolder() {
         when(gettingStartedService.hasDashboardPermission(xPrincipal)).thenReturn(true);
         when(gettingStartedService.getDefaultFolder(xPrincipal, AssetEntry.Type.DASHBOARD))
            .thenReturn(DEFAULT_FOLDER_ID);

         GettingStartedAssetDefaultFolder result =
            gettingStartedController.getVSDefaultSaveFolder(xPrincipal);

         assertEquals(DEFAULT_FOLDER_ID, result.folderId());
         assertNull(result.errorMessage());
      }
   }
}
