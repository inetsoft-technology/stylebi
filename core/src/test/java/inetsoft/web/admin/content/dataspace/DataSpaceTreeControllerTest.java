/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
 * DataSpaceTreeController has real in-controller logic in deleteNodes():
 *   - null guard: nodes == null → return immediately; no service call
 *   - permission loop: calls private checkDeletePermission() for each node:
 *       first check: securityEngine.checkPermission(EM, "*") → false → SecurityException
 *       fall-through (non-shapes path): securityEngine.checkPermission(EM_COMPONENT, "data-space")
 *   - delete loop: deleteDataSpaceNode(path, folder) per node; updateFolder(path) only for files
 *
 * checkDeletePermission() calls ImageShapes.getShapesDirectory() (which invokes SUtil.isMultiTenant()
 * → SecurityEngine.getSecurity() → requires Spring). It is intercepted with MockedStatic when
 * the EM permission passes (tests 3 and 4). ImageShapes.getGlobalShapesDirectory() is safe and
 * called for real.
 *
 * getDataSpaceTree() / getDataSpaceTreeNode() / repairDataSpaceFiles() are pure delegation and
 * are covered by E2E tests.
 *
 * Coverage scope:
 *   [null nodes]              nodes == null → return; service never called
 *   [no EM permission]        EM check fails → SecurityException; deleteDataSpaceNode never called
 *   [file node permitted]     EM + data-space OK → deleteDataSpaceNode + updateFolder called
 *   [folder node permitted]   folder=true → deleteDataSpaceNode called; updateFolder NOT called
 */

import inetsoft.sree.security.*;
import inetsoft.uql.viewsheet.graph.aesthetic.ImageShapes;
import inetsoft.web.admin.content.dataspace.model.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class DataSpaceTreeControllerTest {

   @Mock private DataSpaceContentSettingsService dataSpaceContentSettingsService;
   @Mock private SecurityEngine securityEngine;
   @Mock private DeleteDataSpaceTreeNodesRequest deleteRequest;
   @Mock private HttpServletRequest request;
   @Mock private Principal principal;

   private DataSpaceTreeController controller;

   @BeforeEach
   void setUp() {
      controller = new DataSpaceTreeController(dataSpaceContentSettingsService, securityEngine);
   }

   private static DataSpaceTreeNodeInfo fileNode(String path) {
      return DataSpaceTreeNodeInfo.builder().label("file").path(path).folder(false).build();
   }

   private static DataSpaceTreeNodeInfo folderNode(String path) {
      return DataSpaceTreeNodeInfo.builder().label("folder").path(path).folder(true).build();
   }

   // -------------------------------------------------------------------------
   // deleteNodes()
   // -------------------------------------------------------------------------

   // [null nodes] nodes == null → return immediately; no permission check or service call
   @Test
   void deleteNodes_nullNodes_returnsImmediately() throws Exception {
      when(deleteRequest.nodes()).thenReturn(null);

      controller.deleteNodes(deleteRequest, principal, request);

      verify(dataSpaceContentSettingsService, never()).deleteDataSpaceNode(any(), anyBoolean());
   }

   // [no EM permission] first checkPermission(EM, "*") returns false → SecurityException; no delete
   @Test
   void deleteNodes_noEmPermission_throwsSecurityException() throws Exception {
      when(deleteRequest.nodes()).thenReturn(new DataSpaceTreeNodeInfo[]{ fileNode("reports/file.txt") });
      when(securityEngine.checkPermission(principal, ResourceType.EM, (String) "*", ResourceAction.ACCESS))
         .thenReturn(false);

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> controller.deleteNodes(deleteRequest, principal, request));
      verify(dataSpaceContentSettingsService, never()).deleteDataSpaceNode(any(), anyBoolean());
   }

   // [file node, permitted] EM + data-space permission granted → deleteDataSpaceNode + updateFolder
   @Test
   void deleteNodes_fileNode_dataSpacePermitted_deletesAndUpdatesFolder() throws Exception {
      when(deleteRequest.nodes()).thenReturn(new DataSpaceTreeNodeInfo[]{ fileNode("reports/file.txt") });
      when(securityEngine.checkPermission(principal, ResourceType.EM, (String) "*", ResourceAction.ACCESS))
         .thenReturn(true);
      when(securityEngine.checkPermission(
         principal, ResourceType.EM_COMPONENT, "settings/content/data-space", ResourceAction.ACCESS))
         .thenReturn(true);

      try(MockedStatic<ImageShapes> imageShapesMock = mockStatic(ImageShapes.class, CALLS_REAL_METHODS)) {
         imageShapesMock.when(ImageShapes::getShapesDirectory).thenReturn("portal/host-org/shapes");

         controller.deleteNodes(deleteRequest, principal, request);

         verify(dataSpaceContentSettingsService).deleteDataSpaceNode("reports/file.txt", false);
         verify(dataSpaceContentSettingsService).updateFolder("reports/file.txt");
      }
   }

   // [folder node, permitted] folder=true → deleteDataSpaceNode called; updateFolder NOT called
   @Test
   void deleteNodes_folderNode_dataSpacePermitted_deletesWithoutUpdatingFolder() throws Exception {
      when(deleteRequest.nodes()).thenReturn(new DataSpaceTreeNodeInfo[]{ folderNode("reports") });
      when(securityEngine.checkPermission(principal, ResourceType.EM, (String) "*", ResourceAction.ACCESS))
         .thenReturn(true);
      when(securityEngine.checkPermission(
         principal, ResourceType.EM_COMPONENT, "settings/content/data-space", ResourceAction.ACCESS))
         .thenReturn(true);

      try(MockedStatic<ImageShapes> imageShapesMock = mockStatic(ImageShapes.class, CALLS_REAL_METHODS)) {
         imageShapesMock.when(ImageShapes::getShapesDirectory).thenReturn("portal/host-org/shapes");

         controller.deleteNodes(deleteRequest, principal, request);

         verify(dataSpaceContentSettingsService).deleteDataSpaceNode("reports", true);
         verify(dataSpaceContentSettingsService, never()).updateFolder(any());
      }
   }
}
