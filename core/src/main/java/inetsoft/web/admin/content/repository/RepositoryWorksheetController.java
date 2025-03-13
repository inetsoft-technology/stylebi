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

import inetsoft.report.internal.Util;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.Tool;
import inetsoft.web.adhoc.DecodeParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class RepositoryWorksheetController {
   @Autowired
   public RepositoryWorksheetController(SheetService sheetService,
                                        ContentRepositoryTreeService treeService,
                                        ResourcePermissionService permissionService)
   {
      this.sheetService = sheetService;
      this.permissionService = permissionService;
      this.treeService = treeService;
   }

   @GetMapping("/api/em/content/repository/worksheet")
   public RepositorySheetSettingsModel getWorksheetSettings(
      @DecodeParam("path") String path,
      @RequestParam("timeZone") String timeZone,
      @RequestParam(value = "owner", required = false) String owner,
      Principal principal
   ) throws Exception
   {
      final int scope = treeService.getAssetScope(path);
      path = treeService.getUnscopedPath(path);

      if(owner != null) {
         int index = path.indexOf(Tool.WORKSHEET);

         if(index >= 0) {
            path = path.substring(Tool.WORKSHEET.length() + 1);
         }
      }

      IdentityID ownerID = IdentityID.getIdentityIDFromKey(owner);
      final AssetEntry entry = new AssetEntry(scope, AssetEntry.Type.WORKSHEET, path, ownerID);
      return sheetService.getSheetSettings(entry, ResourceType.ASSET, timeZone, owner, principal);
   }

   @PostMapping("/api/em/content/repository/worksheet")
   public RepositorySheetSettingsModel setWorksheetSettings(
      @DecodeParam("path") String path,
      @RequestParam("timeZone") String timeZone,
      @RequestParam(value = "owner", required = false) String owner,
      @RequestBody() RepositorySheetSettingsModel model,
      Principal principal
   ) throws Exception
   {
      IdentityID ownerID = IdentityID.getIdentityIDFromKey(owner);
      final int scope = treeService.getAssetScope(path);
      path = treeService.getUnscopedPath(path);
      final AssetEntry entry = new AssetEntry(scope, AssetEntry.Type.WORKSHEET, path, ownerID);

      if(model.permissionTableModel() != null && model.permissionTableModel().changed()) {
         String fullPath = Util.getObjectFullPath(
            RepositoryEntry.WORKSHEET, path, principal, ownerID);
         permissionService.setResourcePermissions(path, ResourceType.ASSET, fullPath,
                                                  model.permissionTableModel(), principal);
         boolean hasWSPermission = SecurityEngine.getSecurity().checkPermission(
                 principal, ResourceType.ASSET, fullPath, ResourceAction.READ);

         if(!hasWSPermission) {
            return null;
         }
      }

      AssetEntry newEntry = sheetService.setSheetSettings(entry.toIdentifier(), principal, model);

      return sheetService.getSheetSettings(newEntry, ResourceType.ASSET, timeZone, owner, principal);
   }

   private final SheetService sheetService;
   private final ResourcePermissionService permissionService;
   private final ContentRepositoryTreeService treeService;
}
