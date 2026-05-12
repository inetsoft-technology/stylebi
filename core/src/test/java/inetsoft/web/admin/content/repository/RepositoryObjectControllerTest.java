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
package inetsoft.web.admin.content.repository;

/*
 * Test strategy
 *
 * RepositoryObjectController has one method with real in-controller logic:
 *
 *   GET api/em/content/repository/tree/node/permission (getResourcePermission)
 *     — parses the "type" string parameter to int via Integer.parseInt(), then makes
 *       two sequential calls: permissionService.getRepositoryResourceType(int, path) to
 *       resolve the Resource, and permissionService.getTableModel(path, type,
 *       ADMIN_ACTIONS, principal) using the resolved resource's path and type.
 *
 * Pure-delegation methods (no branch logic):
 *   addFolder             → repositoryObjectService.addFolder(parentInfo, isWorksheetFolder, principal)
 *   deleteRepositoryEntry → repositoryObjectService.deleteNodes(nodes, principal, force, permanent)
 *   moveFile              → repositoryObjectService.moveFiles(request, true, principal)
 *
 * Explicitly excluded:
 *   setResourcePermission (POST) — calls Util.getObjectFullPath() and SecurityEngine.getSecurity(),
 *   both of which require a full Spring context. These code paths are covered by E2E API tests.
 *
 * No static mocks are required for these tests; all behaviour is expressed through
 * the injected service mocks.
 */

import inetsoft.sree.security.Resource;
import inetsoft.sree.security.ResourceType;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class RepositoryObjectControllerTest {

   @Mock private RepositoryObjectService repositoryObjectService;
   @Mock private ResourcePermissionService permissionService;
   @Mock private Principal principal;
   @Mock private ResourcePermissionModel permissionModel;
   @Mock private ContentRepositoryTreeNode treeNode;
   @Mock private ConnectionStatus connectionStatus;
   @Mock private NewRepositoryFolderRequest folderRequest;
   @Mock private DeleteTreeNodesRequest deleteRequest;
   @Mock private MoveCopyTreeNodesRequest moveRequest;

   private RepositoryObjectController controller;

   @BeforeEach
   void setUp() {
      controller = new RepositoryObjectController(repositoryObjectService, permissionService);
   }

   // -------------------------------------------------------------------------
   // GET api/em/content/repository/tree/node/permission — getResourcePermission
   // -------------------------------------------------------------------------

   // [parses type and sequences two service calls] type="128" → parseInt → getRepositoryResourceType
   // → getTableModel with resolved resource path+type; result returned unchanged
   @Test
   void getResourcePermission_parsesTypeAndDelegatesToPermissionService() {
      String path = "/reports/dash1";
      Resource resource = new Resource(ResourceType.REPORT, path);

      when(permissionService.getRepositoryResourceType(128, path)).thenReturn(resource);
      when(permissionService.getTableModel(
         path, ResourceType.REPORT, ResourcePermissionService.ADMIN_ACTIONS, principal))
         .thenReturn(permissionModel);

      ResourcePermissionModel result = controller.getResourcePermission(path, "128", principal);

      assertSame(permissionModel, result);
      verify(permissionService).getRepositoryResourceType(128, path);
      verify(permissionService).getTableModel(
         path, ResourceType.REPORT, ResourcePermissionService.ADMIN_ACTIONS, principal);
   }

   // -------------------------------------------------------------------------
   // POST /api/em/settings/content/repository/folder/add/ — addFolder
   // -------------------------------------------------------------------------

   // [pure delegation] result from repositoryObjectService.addFolder() returned unchanged
   @Test
   void addFolder_delegatesToService() throws Exception {
      when(repositoryObjectService.addFolder(folderRequest, false, principal))
         .thenReturn(treeNode);

      ContentRepositoryTreeNode result = controller.addFolder(folderRequest, false, principal);

      assertSame(treeNode, result);
      verify(repositoryObjectService).addFolder(folderRequest, false, principal);
   }

   // -------------------------------------------------------------------------
   // POST api/em/content/repository/tree/delete — deleteRepositoryEntry
   // -------------------------------------------------------------------------

   // [pure delegation] deleteNodes() called with unpacked request fields; result returned unchanged
   @Test
   void deleteRepositoryEntry_delegatesToService() {
      TreeNodeInfo[] nodes = new TreeNodeInfo[0];
      when(deleteRequest.nodes()).thenReturn(nodes);
      when(deleteRequest.force()).thenReturn(true);
      when(deleteRequest.permanent()).thenReturn(false);
      when(repositoryObjectService.deleteNodes(nodes, principal, true, false))
         .thenReturn(connectionStatus);

      ConnectionStatus result = controller.deleteRepositoryEntry(deleteRequest, principal);

      assertSame(connectionStatus, result);
      verify(repositoryObjectService).deleteNodes(nodes, principal, true, false);
   }

   // -------------------------------------------------------------------------
   // POST api/em/content/repository/tree/move — moveFile
   // -------------------------------------------------------------------------

   // [pure delegation] repositoryObjectService.moveFiles(request, true, principal) called
   @Test
   void moveFile_delegatesToService() throws Exception {
      controller.moveFile(moveRequest, principal);

      verify(repositoryObjectService).moveFiles(moveRequest, true, principal);
   }
}
