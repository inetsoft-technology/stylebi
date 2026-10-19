/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.web.wiz.controller;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.web.RecycleUtils;
import inetsoft.web.admin.sheet.Sheet;
import inetsoft.web.admin.sheet.ws.WorksheetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only worksheet discovery for the composer-chat MCP plugin's {@code list_worksheets} tool.
 *
 * <p>Unlike the admin-ai {@code /v1/admin/worksheets} endpoint this has no site-admin gate: it
 * lists what the calling user can read in their own organization (the shared tree plus their own
 * private tree). Every entry returned is one that {@code runQuery("ws:...")} and the attach path
 * accept for the same caller, so a listed worksheet is never refused afterwards.</p>
 */
@RestController
@RequestMapping("/api/wiz")
public class WorksheetListController {
   @Autowired
   public WorksheetListController(
      @Qualifier("adminSheetWorksheetService") WorksheetService worksheetService,
      AssetRepository assetRepository)
   {
      this.worksheetService = worksheetService;
      this.assetRepository = assetRepository;
   }

   @GetMapping(value = "/v1/worksheets", produces = MediaType.APPLICATION_JSON_VALUE)
   public List<WorksheetListEntry> listWorksheets(Principal principal) throws Exception {
      List<WorksheetListEntry> result = new ArrayList<>();
      List<Sheet> sheets = worksheetService.getWorksheets(principal).getSheets();

      if(sheets == null) {
         return result;
      }

      boolean noPermission = SreeEnv.getProperty("security.provider").equals("") &&
         !VpmProcessor.useVpmSecurity();

      for(Sheet sheet : sheets) {
         String path = sheet.getPath();

         if(path == null || path.startsWith(RecycleUtils.RECYCLE_BIN_FOLDER + "/")) {
            continue;
         }

         try {
            AssetEntry entry = AssetEntry.createAssetEntry(sheet.getAsset());

            if(!noPermission && !isRunnable(entry, principal)) {
               continue;
            }
         }
         catch(Exception e) {
            LOG.debug("Skipping worksheet {} due to error: {}", path, e.getMessage());
            continue;
         }

         if(sheet.isGlobal()) {
            result.add(new WorksheetListEntry(path, sheet.getLabel(), "global", null));
         }
         else {
            String owner = sheet.getUser() == null ? null : sheet.getUser().getName();
            result.add(new WorksheetListEntry(path, sheet.getLabel(), "user", owner));
         }
      }

      return result;
   }

   /**
    * Applies the checks {@code XUtil.runQuery} makes for a {@code ws:} reference, which the flat
    * listing does not: the private-tree READ/My Dashboards check, and the READ walk over the
    * worksheet and each of its parent folders.
    */
   private boolean isRunnable(AssetEntry entry, Principal principal) throws Exception {
      if(entry.getScope() == AssetRepository.USER_SCOPE) {
         try {
            assetRepository.checkAssetPermission(principal, entry, ResourceAction.READ, true);
            return true;
         }
         catch(Exception e) {
            return false;
         }
      }

      AssetEntry parent = entry;

      while(principal != null && parent != null && !parent.isRoot()) {
         if(!SecurityEngine.getSecurity().checkPermission(
            principal, ResourceType.ASSET, parent.getPath(), ResourceAction.READ))
         {
            return false;
         }

         parent = parent.getParent();
      }

      return true;
   }

   public record WorksheetListEntry(String path, String label, String scope, String owner) {
   }

   private final WorksheetService worksheetService;
   private final AssetRepository assetRepository;
   private static final Logger LOG = LoggerFactory.getLogger(WorksheetListController.class);
}
