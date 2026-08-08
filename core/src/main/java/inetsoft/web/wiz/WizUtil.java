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

   /**
    * Infix of the lookup table wiz injects purely to label a foreign key — {@code <source>__fk_<target>}
    * in fkJoinBatchRewrite. Deliberately does NOT match the join itself ({@code <source>__fkjoin}, no
    * trailing underscore), which carries the fact rows and must be capped like any other detail table.
    */
   private static final String FK_LABEL_LOOKUP_INFIX = "__fk_";

   /**
    * Applies the sampled-preview row cap to a worksheet's DETAIL tables, leaving any lookup table
    * injected purely to label a foreign key uncapped. Pass {@code maxRows <= 0} for full data, which
    * clears a cap left by an earlier sampled render on the same runtime.
    *
    * <p>Bug #75989. {@code WorksheetInfo.setDesignMaxRows} is worksheet-WIDE, so it capped every table
    * assembly independently — including the few-row lookup wiz injects to turn an FK id into a name.
    * Truncating the lookup side of an INNER join does not sample the facts, it destroys matches: every
    * fact row whose key was truncated away disappears. Measured on a 984-row table, same binding, only
    * the cap changed: 8 → <b>0 rows</b>, 12 → 12 rows, 20 → 20 rows; the lookup ({@code projects}) has
    * 13 rows, which is exactly where the behaviour turns.
    *
    * <p>What makes that a defect rather than a rough edge: the injection is only sound BECAUSE the join
    * is row-preserving — wiz probes for orphans and duplicate target keys before injecting, precisely so
    * that no unfiltered aggregate changes. A worksheet-wide cap silently voids that precondition. And
    * because the join is injected automatically, a caller who wrote a single-table worksheet and never
    * asked for a join could hit it.
    *
    * <p>Applying the cap per assembly keeps the documented semantics — "aggregate at most maxRows detail
    * rows" — while exempting the dimension tables that were never part of the sampled fact stream.
    */
   public static void applySampledPreviewCap(inetsoft.uql.asset.Worksheet ws, int maxRows) {
      if(ws == null) {
         return;
      }

      final int cap = Math.max(maxRows, 0);

      // Never worksheet-wide (that is the bug). Cleared unconditionally so a cap set by an earlier
      // render of this same runtime cannot linger once the caller asks for full data.
      ws.getWorksheetInfo().setDesignMaxRows(0);

      for(Assembly assembly : ws.getAssemblies()) {
         if(assembly instanceof inetsoft.uql.asset.TableAssembly table) {
            final String name = table.getName();
            table.setMaxRows(name != null && name.contains(FK_LABEL_LOOKUP_INFIX) ? 0 : cap);
         }
      }
   }

   /**
    * The sampled-preview cap currently in effect on {@code ws}, or 0 for full data.
    *
    * Companion to {@link #applySampledPreviewCap} and the reason it exists: the cap can no longer be
    * read back off {@code designMaxRows}, because it is deliberately never set there any more (#75989).
    * Two call sites depend on reading it back — the {@code sampled}/{@code sampleMaxRows} flags that tell
    * the caller its Sum/Count is approximate, and the lazy re-fetch path that must keep a sampled render
    * sampled. Left reading designMaxRows, the first silently claimed a sampled chart was full data and
    * the second silently promoted it to full data.
    *
    * Reads the DETAIL tables only, mirroring how the cap is applied: an injected FK-label lookup is
    * intentionally uncapped, so including it would always report 0.
    */
   public static int sampledPreviewCap(inetsoft.uql.asset.Worksheet ws) {
      if(ws == null) {
         return 0;
      }

      int cap = 0;

      for(Assembly assembly : ws.getAssemblies()) {
         if(assembly instanceof inetsoft.uql.asset.TableAssembly table) {
            final String name = table.getName();

            if(name != null && name.contains(FK_LABEL_LOOKUP_INFIX)) {
               continue;
            }

            cap = Math.max(cap, table.getMaxRows());
         }
      }

      return cap;
   }

   public static final String ANNOTATION_RAW_DATA_MAX_ROW = "annotation.rawdata.maxrow";

   private static final Logger LOG = LoggerFactory.getLogger(WizUtil.class);
}
