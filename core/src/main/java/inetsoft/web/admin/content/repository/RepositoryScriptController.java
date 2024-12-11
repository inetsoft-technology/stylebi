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
package inetsoft.web.admin.content.repository;

import inetsoft.report.LibManager;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.internal.Util;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.repository.model.ScriptSettingsModel;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.sql.Timestamp;

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
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      String objectName = "Script Function/" + model.oname();
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal),
                                                   ActionRecord.ACTION_NAME_EDIT,
                                                   objectName, ActionRecord.OBJECT_TYPE_SCRIPT,
                                                   actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE,
                                                   null);

      LibManager manager = LibManager.getManager();
      boolean change = false;
      String npath = "";

      try {
         if(!Tool.equals(manager.getScriptComment(path), model.description())) {
            manager.setScriptComment(path, model.description());
            change = true;
         }

         if(!Tool.equals(model.name(), model.oname())) {
            actionRecord.setActionError("new name:" + model.name());
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
      catch(Exception e) {
         actionRecord.setActionError(e.getMessage());
         throw new RuntimeException(e);
      }
      finally {
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         Audit.getInstance().auditAction(actionRecord, principal);
      }
   }

   private ResourcePermissionService resourcePermissionService;
}
