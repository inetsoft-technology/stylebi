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

import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.*;
import inetsoft.web.adhoc.DecodeParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class RepositoryViewsheetController {
   @Autowired
   public RepositoryViewsheetController(SheetService sheetService,
                                        ContentRepositoryTreeService treeService,
                                        ResourcePermissionService permissionService)
   {
      this.sheetService = sheetService;
      this.treeService = treeService;
      this.permissionService = permissionService;
   }

   @GetMapping("/api/em/content/repository/viewsheet")
   public RepositorySheetSettingsModel getViewsheetSettings(
      @DecodeParam("path") String path,
      @RequestParam("timeZone") String timeZone,
      @RequestParam(value = "owner", required = false) String owner,
      Principal principal
   ) throws Exception
   {
      String currOrgID = OrganizationManager.getInstance().getCurrentOrgID();

      if(SecurityEngine.getSecurity().getSecurityProvider().getOrganization(currOrgID) == null) {
         throw new InvalidOrgException(Catalog.getCatalog().getString("em.security.invalidOrganizationPassed"));
      }

      int scope = treeService.getAssetScope(path);
      path = treeService.getUnscopedPath(path);
      IdentityID ownerID = IdentityID.getIdentityIDFromKey(owner);
      final AssetEntry entry = new AssetEntry(scope, AssetEntry.Type.VIEWSHEET, path, ownerID);
      return sheetService.getSheetSettings(entry, ResourceType.REPORT, timeZone, owner, principal);
   }

   @PostMapping("/api/em/content/repository/viewsheet")
   public RepositorySheetSettingsModel setViewsheetSettings(
      @DecodeParam("path") String path,
      @RequestParam("timeZone") String timeZone,
      @RequestParam(value = "owner", required = false) String owner,
      @RequestBody() RepositorySheetSettingsModel model, Principal principal) throws Exception
   {
      int scope = treeService.getAssetScope(path);
      path = treeService.getUnscopedPath(path);
      IdentityID ownerID = IdentityID.getIdentityIDFromKey(owner);
      final AssetEntry oldEntry = new AssetEntry(scope, AssetEntry.Type.VIEWSHEET, path, ownerID);
      final AssetEntry newEntry =
         sheetService.setSheetSettings(oldEntry.toIdentifier(), principal, model);

      if(model.permissionTableModel() != null && model.permissionTableModel().changed()) {
         permissionService.setResourcePermissions(path, ResourceType.REPORT,
                                                  model.permissionTableModel(), principal);
      }

      return sheetService.getSheetSettings(newEntry, ResourceType.REPORT, timeZone, owner, principal);
   }

   private final SheetService sheetService;
   private final ContentRepositoryTreeService treeService;
   private final ResourcePermissionService permissionService;
}
