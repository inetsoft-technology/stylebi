/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.content.repository;

import inetsoft.sree.security.*;
import inetsoft.util.data.CommonKVModel;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.internal.SUtil;
import inetsoft.util.Tool;
import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.admin.content.repository.model.LicensedComponents;
import inetsoft.web.security.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@RestController
public class ContentRepositoryTreeController {
   @Autowired
   public ContentRepositoryTreeController(ContentRepositoryTreeService treeService) {
      this.treeService = treeService;
   }

   /**
    * Get the repository tree root
    */
   @PostMapping("/api/em/content/repository/tree")
   public ContentRepositoryTreeModel getRepositoryTree(@RequestBody List<String> usersToLoad,
                                                       Principal principal) throws Exception
   {
      List<ContentRepositoryTreeNode> nodes = treeService.getRootNodes(principal, usersToLoad);
      return new ContentRepositoryTreeModel(nodes);
   }

   @PostMapping("/api/em/content/repository/private/tree")
   public ContentRepositoryTreeModel getRepositoryPrivateTree(@RequestBody CommonKVModel<String, String>[] users,
                                                              Principal principal) throws Exception
   {
      List<ContentRepositoryTreeNode> nodes = new ArrayList<>();

      for(CommonKVModel<String, String> user : users) {
         IdentityID owner = IdentityID.getIdentityIDFromKey(user.getKey());
         String path = user.getValue();
         RepletRegistry registry = RepletRegistry.getRegistry(owner);

         if(Tool.MY_DASHBOARD.equals(path)) {
            nodes.addAll(treeService.getUserReports(owner, registry, principal).children());
         }
         else if(SUtil.MY_DASHBOARD.equals(path)) {
            nodes.addAll(treeService.getUserDashboardNode(owner, registry, principal).children());
         }
      }

      return new ContentRepositoryTreeModel(nodes);
   }

   /**
    * Get the repository tree node
    */
   @GetMapping("/api/em/content/repository/tree")
   public ContentRepositoryTreeModel getRepositoryTree(@DecodeParam("path") String path,
                                                       @DecodeParam("owner") String owner,
                                                       Principal principal) throws Exception
   {
      IdentityID ownerID = IdentityID.getIdentityIDFromKey(owner);
      List<ContentRepositoryTreeNode> nodes = null;
      RepletRegistry registry = RepletRegistry.getRegistry(ownerID);

      if(Tool.MY_DASHBOARD.equals(path)) {
         nodes = treeService.getUserReports(ownerID, registry, principal).children();
      }
      else if(SUtil.MY_DASHBOARD.equals(path)) {
         nodes = treeService.getUserDashboardNode(ownerID, registry, principal).children();
      }

      return new ContentRepositoryTreeModel(nodes);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT,
      resource = "settings/content/repository",
      actions = ResourceAction.ACCESS
   ))
   @GetMapping("/api/em/content/repository/tree/licensed")
   public LicensedComponents getLicensedComponents(
      @SuppressWarnings("unused") @PermissionUser Principal principal)
   {
      return treeService.getLicensedComponents();
   }

   @GetMapping("/api/em/content/is-site-admin")
   public boolean isSiteAdmin(
      @SuppressWarnings("unused") @PermissionUser Principal principal)
   {
      return OrganizationManager.getInstance().isSiteAdmin(principal);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT,
      resource = "settings/content/repository",
      actions = ResourceAction.ACCESS
   ))
   @GetMapping("/api/em/content/repository/tree/search")
   public ContentRepositoryTreeModel searchRepositoryTree(@DecodeParam("filter") String filter,
                                                          @PermissionUser Principal principal)
      throws Exception
   {
      return new ContentRepositoryTreeModel(treeService.searchNodes(principal, filter));
   }

   private final ContentRepositoryTreeService treeService;
}
