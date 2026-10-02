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
package inetsoft.web.admin.content.dataspace;

/*
 * Test strategy
 *
 * uploadDataSpaceFiles() calls the private checkUploadPermission() before touching any upload.
 * When the check passes, the next call is uploadService.get(uploadId); it is stubbed to return
 * Optional.empty() so a permitted request ends in IllegalArgumentException("No uploaded files")
 * without writing to the data space, and a denied request ends in SecurityException with
 * uploadService never called.
 *
 * Coverage scope (multi-tenant):
 *   [org admin, global=true]           portal/shapes + global → denied
 *   [org admin, global subfolder]      portal/shapes/sub → denied
 *   [org admin, org sentinel]          portal/shapes + global=false → permitted (org-scoped)
 *   [site admin, global=true]          portal/shapes + global → permitted
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.viewsheet.graph.aesthetic.ImageShapes;
import inetsoft.util.DataSpace;
import inetsoft.web.admin.content.dataspace.model.DataSpaceFolderUploadModel;
import inetsoft.web.admin.upload.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataSpaceFolderSettingsControllerTest {
   @Mock private DataSpaceContentSettingsService dataSpaceContentSettingsService;
   @Mock private DataSpaceFolderSettingsService dataSpaceFolderSettingsService;
   @Mock private UploadService uploadService;
   @Mock private DataSpace dataSpace;
   @Mock private SecurityEngine securityEngine;
   @Mock private HttpServletRequest request;
   @Mock private Principal principal;
   @Mock private OrganizationManager orgManager;

   private DataSpaceFolderSettingsController controller;
   private MockedStatic<ImageShapes> imageShapesMock;
   private MockedStatic<SUtil> sutilMock;
   private MockedStatic<OrganizationManager> orgManagerMock;

   @BeforeEach
   void setUp() throws Exception {
      controller = new DataSpaceFolderSettingsController(
         dataSpaceContentSettingsService, dataSpaceFolderSettingsService, uploadService,
         dataSpace, securityEngine);

      imageShapesMock = mockStatic(ImageShapes.class, CALLS_REAL_METHODS);
      imageShapesMock.when(ImageShapes::getShapesDirectory).thenReturn("portal/orgA/shapes");
      sutilMock = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutilMock.when(SUtil::isMultiTenant).thenReturn(true);
      orgManagerMock = mockStatic(OrganizationManager.class);
      orgManagerMock.when(OrganizationManager::getInstance).thenReturn(orgManager);

      // org admin and site admin both have EM and presentation settings access
      when(securityEngine.checkPermission(principal, ResourceType.EM, (String) "*", ResourceAction.ACCESS))
         .thenReturn(true);
      when(securityEngine.checkPermission(
         principal, ResourceType.EM_COMPONENT, "settings/presentation/settings", ResourceAction.ACCESS))
         .thenReturn(true);
      when(uploadService.get(any())).thenReturn(Optional.empty());
   }

   @AfterEach
   void tearDown() {
      orgManagerMock.close();
      sutilMock.close();
      imageShapesMock.close();
   }

   // [org admin, global=true] must not write to the global shapes folder shared by all orgs
   @Test
   void upload_orgAdminGlobalShapes_throwsSecurityException() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> controller.uploadDataSpaceFiles(uploadModel("portal/shapes", true), principal, request));
      verify(uploadService, never()).get(any());
   }

   // [org admin, global subfolder] a subfolder of the global shapes folder is also global
   @Test
   void upload_orgAdminGlobalShapesSubfolder_throwsSecurityException() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> controller.uploadDataSpaceFiles(uploadModel("portal/shapes/sub", false), principal, request));
      verify(uploadService, never()).get(any());
   }

   // [org admin, org sentinel] portal/shapes with global=false targets the org's own folder
   @Test
   void upload_orgAdminOrgShapesSentinel_permitted() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      assertThrows(IllegalArgumentException.class,
         () -> controller.uploadDataSpaceFiles(uploadModel("portal/shapes", false), principal, request));
      verify(uploadService).get("upload-id");
   }

   // [site admin, global=true] site admin may still manage the global shapes folder
   @Test
   void upload_siteAdminGlobalShapes_permitted() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      assertThrows(IllegalArgumentException.class,
         () -> controller.uploadDataSpaceFiles(uploadModel("portal/shapes", true), principal, request));
      verify(uploadService).get("upload-id");
   }

   private static DataSpaceFolderUploadModel uploadModel(String path, boolean global) {
      return DataSpaceFolderUploadModel.builder()
         .path(path)
         .files("upload-id")
         .global(global)
         .build();
   }
}
