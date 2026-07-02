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

package inetsoft.web.wiz;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;

public class WizUtil {
   public static String decodeId(String id) {
      String decodedId;

      if(id == null || id.isEmpty()) {
         decodedId = null;
      }
      else {
         try {
            decodedId = new String(Base64.getDecoder().decode(id), StandardCharsets.UTF_8);
         }
         catch(IllegalArgumentException e) {
            decodedId = null;
         }
      }

      return decodedId;
   }

   /**
    * Resolve the runtime viewsheet for a wiz modify operation, transparently restoring it when the
    * runtime has been reaped (TTL expiry / server restart).
    *
    * The runtime viewsheet is transient, but the viewsheet asset that {@code viewsheetIdentifier}
    * points to is durable (every wiz create/modify rewrites it via persistViewsheet), so a reaped
    * runtime can be reopened from the identifier into a fresh runtime carrying the same state. Callers
    * MUST read {@link RuntimeViewsheet#getID()} off the returned value to pick up the (possibly new)
    * runtimeId and echo it back to the client, so subsequent edits target the live runtime instead of
    * the reaped one.
    *
    * @param viewsheetService    the runtime registry (resolve + reopen).
    * @param runtimeId           the runtime id the client believes is active (may be reaped).
    * @param viewsheetIdentifier the durable asset identifier to restore from; may be null/empty.
    * @param user                the requesting principal.
    * @return the live RuntimeViewsheet (the existing one, or a freshly reopened one).
    * @throws ExpiredSheetException if the runtime is gone AND no identifier is available to restore from.
    */
   public static RuntimeViewsheet getViewsheetOrRestore(ViewsheetService viewsheetService,
                                                        String runtimeId, String viewsheetIdentifier,
                                                        Principal user)
      throws Exception
   {
      try {
         return viewsheetService.getViewsheet(runtimeId, user);
      }
      catch(ExpiredSheetException ex) {
         if(Tool.isEmptyString(viewsheetIdentifier)) {
            // Nothing durable to restore from (e.g. the asset was explicitly removed) — surface expiry.
            throw ex;
         }

         AssetEntry entry = AssetEntry.createAssetEntry(viewsheetIdentifier);

         if(entry == null) {
            throw ex;
         }

         String restoredId = viewsheetService.openViewsheet(entry, user, false);
         LOG.debug("Restored reaped runtime [{}] from identifier [{}] as [{}]",
                   runtimeId, viewsheetIdentifier, restoredId);
         return viewsheetService.getViewsheet(restoredId, user);
      }
   }

   /**
    * Applies max mode state to the primary assembly of a viewsheet without refreshing.
    * The caller is responsible for triggering a viewsheet refresh afterward.
    *
    * @param vs      the viewsheet.
    * @param maxSize the max mode dimensions.
    */
   public static void prepareMaxMode(Viewsheet vs, Dimension maxSize) {
      if(vs == null || vs.getWizInfo() == null || !vs.getWizInfo().isWizVisualization() ||
         maxSize == null || maxSize.width <= 0 || maxSize.height <= 0)
      {
         return;
      }

      for(Assembly assembly : vs.getAssemblies()) {
         if(!(assembly instanceof VSAssembly vsAssembly)) {
            continue;
         }

         VSAssemblyInfo info = vsAssembly.getVSAssemblyInfo();

         if(info instanceof ChartVSAssemblyInfo chartInfo) {
            chartInfo.setMaxSize(maxSize);
            vs.setMaxMode(true);
            setMaxModeZIndex(vs, info, maxSize);
            return;
         }
         else if(info instanceof TableDataVSAssemblyInfo tableInfo) {
            tableInfo.setMaxSize(maxSize);
            vs.setMaxMode(true);
            setMaxModeZIndex(vs, info, maxSize);
            return;
         }
      }
   }

   private static void setMaxModeZIndex(Viewsheet vs, VSAssemblyInfo info, Dimension maxSize) {
      if(maxSize == null) {
         return;
      }

      Assembly[] assemblies = vs.getAssemblies(true, true);

      if(assemblies == null || assemblies.length == 0) {
         return;
      }

      VSAssembly top = (VSAssembly) assemblies[assemblies.length - 1];
      int zIndex = top.getVSAssemblyInfo().getZIndex() + 1;

      if(info instanceof ChartVSAssemblyInfo chartInfo) {
         chartInfo.setMaxModeZIndex(zIndex);
      }
      else if(info instanceof TableDataVSAssemblyInfo tableInfo) {
         tableInfo.setMaxModeZIndex(zIndex);
      }
   }

   public static final String ANNOTATION_RAW_DATA_MAX_ROW = "annotation.rawdata.maxrow";

   private static final Logger LOG = LoggerFactory.getLogger(WizUtil.class);
}
