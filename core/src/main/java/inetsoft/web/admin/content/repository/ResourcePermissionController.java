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

import inetsoft.report.internal.Util;
import inetsoft.sree.security.Resource;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class ResourcePermissionController {
   @Autowired
   public ResourcePermissionController(ResourcePermissionService service) {
      this.resourcePermissionService = service;
   }

   @GetMapping("/api/em/content/repository/permission")
   public ResourcePermissionModel getRepositoryEntryPermissions(@RequestParam("path") String path,
                                                                @RequestParam("type") int type,
                                                                Principal principal)
   {
      Resource resource = resourcePermissionService.getRepositoryResourceType(type, path);
      return this.resourcePermissionService.getTableModel(
         resource.getPath(), resource.getType(),
         ResourcePermissionService.ADMIN_ACTIONS, principal);
   }

   @PostMapping("/api/em/content/repository/permission")
   public void setRepositoryEntryPermissions(@RequestParam("path") String path,
                                             @RequestParam("type") int type,
                                             @RequestBody ResourcePermissionModel permissionModel,
                                             Principal principal)
      throws Exception
   {
      Resource resource = resourcePermissionService.getRepositoryResourceType(type, path);
      String fullPath = Util.getObjectFullPath(type, path, principal);
      this.resourcePermissionService.setResourcePermissions(
         resource.getPath(), resource.getType(), fullPath, permissionModel, principal);
   }

   private final ResourcePermissionService resourcePermissionService;
}
