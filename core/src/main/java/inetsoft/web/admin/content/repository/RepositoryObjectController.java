/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.content.repository;

import inetsoft.report.internal.Util;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Resource;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.MessageException;
import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class RepositoryObjectController {
   @Autowired
   public RepositoryObjectController(RepositoryObjectService repositoryObjectService,
                                     ResourcePermissionService permissionService)
   {
      this.repositoryObjectService = repositoryObjectService;
      this.permissionService = permissionService;
   }

   @GetMapping("api/em/content/repository/tree/node/permission")
   public ResourcePermissionModel getResourcePermission(@DecodeParam("path") String path,
                                                        @RequestParam("type") String type,
                                                        Principal principal)
   {
      Resource resource = permissionService.getRepositoryResourceType(Integer.parseInt(type), path);
      return permissionService.getTableModel(resource.getPath(), resource.getType(),
                                             ResourcePermissionService.ADMIN_ACTIONS,
                                             principal);
   }

   @PostMapping("api/em/content/repository/tree/node/permission")
   public void setResourcePermission(@DecodeParam("path") String path,
                                     @RequestParam("type") String type,
                                     @RequestBody ResourcePermissionModel model,
                                     Principal principal)
      throws Exception
   {
      Resource resource = permissionService.getRepositoryResourceType(Integer.parseInt(type), path);
      String fullPath = Util.getObjectFullPath(Integer.parseInt(type), path, principal);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      AssetEntry entry = new AssetEntry(
         AssetRepository.COMPONENT_SCOPE, Integer.parseInt(type), path, pId);
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      ((AbstractAssetEngine)repository).fireLibraryEvent(entry);
      this.permissionService.setResourcePermissions(
         resource.getPath(), resource.getType(), fullPath, model, principal);
   }

   @PostMapping("/api/em/settings/content/repository/folder/add/")
   public ContentRepositoryTreeNode addFolder(@RequestBody NewRepositoryFolderRequest parentInfo,
                         @RequestParam("isWorksheetFolder") boolean isWorksheetFolder,
                         Principal principal)
      throws Exception
   {
      return this.repositoryObjectService.addFolder(parentInfo, isWorksheetFolder, principal);
   }

   /**
    * Delete the selected repository entry
    */
   @PostMapping("api/em/content/repository/tree/delete")
   public ConnectionStatus deleteRepositoryEntry(@RequestBody DeleteTreeNodesRequest deleteRequest,
                                                 Principal principal) throws MessageException
   {
      return repositoryObjectService.deleteNodes(
         deleteRequest.nodes(), principal, deleteRequest.force(), deleteRequest.permanent());
   }

   /**
    * Move/copy the selected repository entry
    */
   @PostMapping("api/em/content/repository/tree/move")
   public void moveFile(@RequestBody MoveCopyTreeNodesRequest request,
                                     Principal principal)
      throws Exception
   {
      repositoryObjectService.moveFiles(request, true, principal);
   }

   private final RepositoryObjectService repositoryObjectService;
   private final ResourcePermissionService permissionService;
}
