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
package inetsoft.web.portal.controller;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.Catalog;
import inetsoft.web.portal.model.GettingStartedAssetDefaultFolder;
import inetsoft.web.portal.service.GettingStartedService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
public class GettingStartedController {
   public GettingStartedController(GettingStartedService gettingStartedService) {
      this.gettingStartedService = gettingStartedService;
   }

   @GetMapping("/api/portal/getting-started")
   public String showGettingStarted(Principal principal) {
      String gettingStarted = SreeEnv.getProperty("getting.started");

      // for testing getting started dialog.
      if(gettingStarted != null) {
         return gettingStarted;
      }

      if(principal instanceof XPrincipal) {
         if(!"true".equals(((XPrincipal) principal).getProperty("showGettingStated"))) {
            return "false";
         }
         else {
            ((XPrincipal) principal).setProperty("showGettingStated", null);
         }
      }

      boolean show = gettingStartedService.hasPermission(principal) &&
         !gettingStartedService.hasCreatedAssets(principal);
      return show + "";
   }

   @GetMapping("/api/portal/getting-started/query/defaultFolder")
   public GettingStartedAssetDefaultFolder checkCreateQueryPermission(Principal principal) throws Exception {
      GettingStartedAssetDefaultFolder.Builder folderModel =
         GettingStartedAssetDefaultFolder.builder();
      Catalog catalog = Catalog.getCatalog();

      if(!gettingStartedService.hasCreateWSPermission(principal)) {
         folderModel.errorMessage(catalog.getString("getting.started.new.asset.unauthorized",
            catalog.getString("Data Worksheet")));
      }
      else if(!SecurityEngine.getSecurity().checkPermission(principal, ResourceType.PHYSICAL_TABLE,
         "*", ResourceAction.ACCESS))
      {
         folderModel.errorMessage(catalog.getString("getting.started.access.asset.unauthorized",
            catalog.getString("Physical Table")));
      }
      else {
         folderModel.folderId(gettingStartedService.getDefaultFolder(principal,
            AssetEntry.Type.WORKSHEET));
      }

      return folderModel.build();
   }

   @GetMapping("/api/portal/getting-started/ws/defaultFolder")
   public GettingStartedAssetDefaultFolder checkCreateWSPermission(Principal principal) {
      GettingStartedAssetDefaultFolder.Builder folderModel =
         GettingStartedAssetDefaultFolder.builder();
      Catalog catalog = Catalog.getCatalog();

      if(!gettingStartedService.hasCreateWSPermission(principal)) {
         folderModel.errorMessage(catalog.getString("getting.started.new.asset.unauthorized",
            catalog.getString("Data Worksheet")));
      }
      else {
         folderModel.folderId(gettingStartedService.getDefaultFolder(principal,
            AssetEntry.Type.WORKSHEET));
      }

      return folderModel.build();
   }

   @GetMapping("/api/portal/getting-started/vs/defaultFolder")
   public GettingStartedAssetDefaultFolder getVSDefaultSaveFolder(Principal principal) {
      GettingStartedAssetDefaultFolder.Builder folderModel =
         GettingStartedAssetDefaultFolder.builder();
      Catalog catalog = Catalog.getCatalog();

      if(!gettingStartedService.hasDashboardPermission(principal)) {
         folderModel.errorMessage(catalog.getString("getting.started.new.asset.unauthorized",
            catalog.getString("Dashboard")));
      }
      else {
         folderModel.folderId(gettingStartedService.getDefaultFolder(principal,
            AssetEntry.Type.DASHBOARD));
      }

      return folderModel.build();
   }

   private final GettingStartedService gettingStartedService;
}
