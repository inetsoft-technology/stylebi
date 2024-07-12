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

import inetsoft.report.LibManager;
import inetsoft.report.internal.Util;
import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import inetsoft.web.admin.content.repository.model.ScriptSettingsModel;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class RepositoryScriptController {
   @Autowired
   public RepositoryScriptController(ResourcePermissionService resourcePermissionService) {
      this.resourcePermissionService = resourcePermissionService;
   }

   @GetMapping("/api/em/settings/content/repository/script")
   public ScriptSettingsModel getScriptModel(@RequestParam("path") String path,
                                             @RequestParam("type") int type,
                                             Principal principal)
   {
      Resource resource = resourcePermissionService.getRepositoryResourceType(type, path);
      ResourcePermissionModel permissionModel = this.resourcePermissionService.getTableModel(
         resource.getPath(), resource.getType(),
         ResourcePermissionService.ADMIN_ACTIONS, principal);
      LibManager manager = LibManager.getManager();

      return ScriptSettingsModel.builder()
         .name(path)
         .oname(path)
         .description(manager.getScriptComment(path))
         .permissions(permissionModel)
         .build();
   }

   @PostMapping("/api/em/settings/content/repository/script")
   public void setScriptModel(@RequestParam("path") String path,
                              @RequestParam("type") int type,
                              @RequestBody ScriptSettingsModel model,
                              Principal principal)
      throws Exception
   {
      LibManager manager = LibManager.getManager();
      boolean change = false;
      String npath = "";

      if(!Tool.equals(manager.getScriptComment(path), model.description())) {
         manager.setScriptComment(path, model.description());
         change = true;
      }

      if(!Tool.equals(model.name(), model.oname())) {
         manager.renameScript(model.oname(), model.name());
         npath = model.name();
         change = true;
      }

      if(change) {
         manager.save();
      }

      Resource resource = resourcePermissionService.getRepositoryResourceType(type, path);

      if(npath != null && !npath.isEmpty()) {
         SecurityEngine security = SecurityEngine.getSecurity();
         Permission temp = security.getPermission(resource.getType(), path);
         security.removePermission(resource.getType(), path);
         security.setPermission(resource.getType(), npath, temp);
      }

      if(model.permissions() != null && model.permissions().changed()) {
         String fullPath = Util.getObjectFullPath(type, path, principal);
         this.resourcePermissionService.setResourcePermissions(
            resource.getPath(), resource.getType(), fullPath, model.permissions(), principal);
      }
   }

   private ResourcePermissionService resourcePermissionService;
}
