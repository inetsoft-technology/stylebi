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
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.web.RecycleUtils;
import inetsoft.web.admin.sheet.Sheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * The read-permission filtering and mapping shared by the non-admin sheet listings behind the
 * composer-chat {@code list_worksheets} and {@code list_viewsheets} tools.
 */
final class SheetListSupport {
   private SheetListSupport() {
   }

   /** Builds one listing row; the row type is the caller's own. */
   @FunctionalInterface
   interface EntryFactory<T> {
      T create(String path, String label, String scope, String owner);
   }

   /**
    * Maps the sheets the admin service returned for {@code principal} to listing rows, dropping
    * recycle-bin entries and every sheet the principal could not actually open.
    */
   static <T> List<T> list(List<Sheet> sheets, Principal principal,
                           AssetRepository assetRepository, EntryFactory<T> factory)
   {
      List<T> result = new ArrayList<>();

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

            if(!noPermission && !isRunnable(entry, principal, assetRepository)) {
               continue;
            }
         }
         catch(Exception e) {
            LOG.debug("Skipping sheet {} due to error: {}", path, e.getMessage());
            continue;
         }

         if(sheet.isGlobal()) {
            result.add(factory.create(path, sheet.getLabel(), "global", null));
         }
         else {
            String owner = sheet.getUser() == null ? null : sheet.getUser().getName();
            result.add(factory.create(path, sheet.getLabel(), "user", owner));
         }
      }

      return result;
   }

   /**
    * Applies the checks {@code XUtil.runQuery} makes for a {@code ws:} reference, which the flat
    * listing does not: the private-tree READ/My Dashboards check, and the READ walk over the
    * sheet and each of its parent folders.
    */
   private static boolean isRunnable(AssetEntry entry, Principal principal,
                                     AssetRepository assetRepository) throws Exception
   {
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

   private static final Logger LOG = LoggerFactory.getLogger(SheetListSupport.class);
}
