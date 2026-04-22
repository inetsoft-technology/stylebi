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
package inetsoft.web.composer.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.ws.WsMergeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;

/**
 * Service that merges a visualization viewsheet into the wiz dashboard viewsheet.
 * Implements the three-step merge described in viewsheet添加visualization.md:
 * 1. Worksheet merge (column-level dedup + mirror isolation)
 * 2. Viewsheet merge (copy assemblies + position offset + rename patch)
 * 3. Record merged visualization in WizInfo
 */
@Service
@ClusterProxy
public class AddVisualizationService {

   public AddVisualizationService(ViewsheetService viewsheetService,
                                  AssetRepository assetRepository,
                                  WsMergeService wsMergeService)
   {
      this.viewsheetService = viewsheetService;
      this.assetRepository = assetRepository;
      this.wsMergeService = wsMergeService;
   }

   /**
    * Merges the visualization identified by {@code vizEntry} into the wiz dashboard viewsheet
    * identified by {@code runtimeId}.
    *
    * @param runtimeId the runtime ID of the wiz dashboard viewsheet.
    * @param vizEntry  the asset entry of the visualization viewsheet to add.
    * @param xOffset   horizontal drop position in scaled canvas coordinates.
    * @param yOffset   vertical drop position in scaled canvas coordinates.
    * @param scale     current canvas zoom scale.
    * @param principal the current user.
    */
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void addVisualization(@ClusterProxyKey String runtimeId,
                                AssetEntry vizEntry,
                                int xOffset, int yOffset, float scale,
                                Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      if(rvs == null) {
         throw new Exception("Runtime viewsheet not found: " + runtimeId);
      }

      Viewsheet dashVS = rvs.getViewsheet();
      Viewsheet.WizInfo wizInfo = dashVS.getWizInfo();

      if(wizInfo == null) {
         throw new IllegalStateException("Viewsheet is not a wiz dashboard (wizInfo is null): " + runtimeId);
      }

      if(!wizInfo.isWizSheet()) {
         throw new IllegalStateException("Viewsheet is not a wiz dashboard (isWizSheet=false): " + runtimeId);
      }

      // Load visualization VS
      Viewsheet vizVS = (Viewsheet) assetRepository.getSheet(
         vizEntry, principal, false, AssetContent.ALL);

      if(vizVS == null) {
         throw new Exception("Visualization viewsheet not found: " + vizEntry);
      }

      // Validate datasource is a worksheet
      AssetEntry baseEntry = vizVS.getBaseEntry();

      if(baseEntry == null || !baseEntry.isWorksheet()) {
         throw new IllegalArgumentException(
            "Visualization must be bound to a worksheet datasource: " + vizEntry);
      }

      // Load visualization WS
      Worksheet vizWS = (Worksheet) assetRepository.getSheet(
         baseEntry, principal, false, AssetContent.ALL);

      if(vizWS == null) {
         throw new Exception("Could not load worksheet for visualization: " + vizEntry);
      }

      // Initialize dashboard WS if this is the first visualization
      Worksheet dashWS = null;

      if(dashVS.getBaseEntry() != null) {
         dashWS = (Worksheet) assetRepository.getSheet(
            dashVS.getBaseEntry(), principal, false, AssetContent.ALL);

         if(dashWS == null) {
            throw new Exception("Could not load worksheet for visualization: " + dashVS.getBaseEntry());
         }

         // If the current base entry is a permanent (already-saved) worksheet, clone its
         // content into a new temporary entry so that in-progress changes are isolated
         // from the saved copy until the user explicitly saves the dashboard.
         // Temp entries (WIZ_DASH_WS_PREFIX) are already working copies — skip them.
         if(!dashVS.getBaseEntry().getName().startsWith(WizUtil.WIZ_DASH_WS_PREFIX)) {
            AssetEntry tempWsEntry = WizUtil.createWizDashWorksheetEntry(dashVS.getBaseEntry().getName());
            assetRepository.setSheet(tempWsEntry, dashWS, principal, true);
            dashVS.setBaseEntry(tempWsEntry);
         }
      }
      else {
         // First visualization: create a temporary worksheet entry (WIZ_DASH_WS_PREFIX) and
         // register it as the dashboard VS baseEntry.  The lifecycle mirrors wiz-copy viewsheets:
         // finalizeWizDashWorksheet() promotes it to a permanent entry on save, and
         // ViewsheetEngine.closeViewsheet() deletes it when the runtime VS is closed.
         dashWS = new Worksheet();
         String vsEntryName = rvs.getEntry() != null ? rvs.getEntry().getName() : runtimeId;
         AssetEntry tempWsEntry = WizUtil.createWizDashWorksheetEntry(vsEntryName);
         dashVS.setBaseEntry(tempWsEntry);
      }

      // Compute a unique name suffix to avoid collisions when copying assemblies
      String vizSuffix = computeUniqueSuffix(vizEntry.getName(), dashVS);

      // VS bindings that need redirecting after WS merge (base-table → prev-mirror renames)
      Map<String, String> vsRenameMap = new HashMap<>();

      // Step 1: merge worksheet (returns vizWS-name → dashWS-name mapping)
      Map<String, String> wsRenameMap = wsMergeService.mergeWorksheet(vizWS, dashWS, vizSuffix, vsRenameMap);

      // Apply deferred VS binding redirects accumulated during WS merge
      for(Map.Entry<String, String> e : vsRenameMap.entrySet()) {
         updateVSBindings(dashVS, e.getKey(), e.getValue());
      }

      // Step 2: merge viewsheet assemblies
      mergeViewsheet(vizVS, dashVS, wsRenameMap, xOffset, yOffset, scale);

      // Step 3: record merged visualization
      wizInfo.addMergedVisualization(vizEntry.toIdentifier());

      // Save the merged dashboard worksheet back to the repository
      assetRepository.setSheet(dashVS.getBaseEntry(), dashWS, principal, true);

      return null;
   }

   // ---------------------------------------------------------------------------
   // Step 2: Viewsheet merge
   // ---------------------------------------------------------------------------

   /**
    * Copies all VSAssemblies from {@code vizVS} into {@code dashVS}, applying name-conflict
    * resolution, WS binding renames, and drop-position offsets.
    */
   private void mergeViewsheet(Viewsheet vizVS, Viewsheet dashVS,
                               Map<String, String> wsRenameMap,
                               int xOffset, int yOffset, float scale)
   {
      if(scale <= 0f) {
         scale = 1f;
      }

      int offsetX = (int) (xOffset / scale);
      int offsetY = (int) (yOffset / scale);

      // Pass 1: clone every assembly and compute its final name, building vsRenameMap
      //         so that intra-visualization cross-references can be patched in pass 2.
      List<VSAssembly> clones = new ArrayList<>();
      Map<String, String> vsRenameMap = new HashMap<>();

      for(Assembly a : vizVS.getAssemblies()) {
         if(!(a instanceof VSAssembly srcAssembly)) {
            continue;
         }

         VSAssembly cloned = (VSAssembly) srcAssembly.clone();
         VSAssemblyInfo info = cloned.getVSAssemblyInfo();

         if(info instanceof ChartVSAssemblyInfo chartInfo) {
            chartInfo.setMaxSize(null);
         }
         else if(info instanceof TableDataVSAssemblyInfo tableInfo) {
            tableInfo.setMaxSize(null);
         }

         String originalName = srcAssembly.getName();
         String targetName = originalName;

         if(dashVS.getAssembly(targetName) != null) {
            int counter = 2;
            targetName = originalName + "_" + counter;

            while(dashVS.getAssembly(targetName) != null) {
               targetName = originalName + "_" + (++counter);
            }
         }

         if(!targetName.equals(originalName)) {
            cloned.getVSAssemblyInfo().setName(targetName);
            vsRenameMap.put(originalName, targetName);
         }

         clones.add(cloned);
      }

      // Pass 2: patch references, apply offset, and add to dashVS
      for(VSAssembly cloned : clones) {
         // Patch WS table binding references
         applyWsRenameToVSAssembly(cloned, wsRenameMap);

         // Patch VS-level cross-references (container children, linked/selection assemblies)
         for(Map.Entry<String, String> e : vsRenameMap.entrySet()) {
            cloned.renameDepended(e.getKey(), e.getValue());
         }

         // Apply drop position offset
         Point pos = cloned.getPixelOffset();
         cloned.setPixelOffset(new Point(pos.x + offsetX, pos.y + offsetY));

         dashVS.addAssembly(cloned);
      }
   }

   /**
    * Updates all WS table name references in a VS assembly using {@code wsRenameMap}.
    * Handles the {@link BindableVSAssembly} interface (covers chart, table, crosstab,
    * selection, calendar, gauge, text, etc.).
    */
   private void applyWsRenameToVSAssembly(VSAssembly assembly,
                                          Map<String, String> wsRenameMap)
   {
      if(wsRenameMap.isEmpty()) {
         return;
      }

      if(assembly instanceof BindableVSAssembly bindable) {
         String current = bindable.getTableName();

         if(current != null) {
            String renamed = wsRenameMap.get(current);

            if(renamed != null) {
               bindable.setTableName(renamed);
            }
         }
      }
   }

   // ---------------------------------------------------------------------------
   // Helpers
   // ---------------------------------------------------------------------------

   /**
    * Updates all VS assemblies in {@code dashVS} whose WS table binding matches
    * {@code oldTableName} to use {@code newTableName} instead.
    */
   private void updateVSBindings(Viewsheet dashVS, String oldTableName, String newTableName) {
      for(Assembly a : dashVS.getAssemblies()) {
         if(!(a instanceof BindableVSAssembly bindable)) {
            continue;
         }

         String current = bindable.getTableName();

         if(oldTableName.equals(current)) {
            bindable.setTableName(newTableName);
         }
      }
   }

   /**
    * Computes a short, unique name suffix for this visualization by sanitizing the entry
    * name (keep alphanumeric + underscore, max 20 chars) and appending a counter based on
    * how many visualizations have already been merged into dashVS.
    */
   private String computeUniqueSuffix(String vizName, Viewsheet dashVS) {
      String base = vizName.replaceAll("[^A-Za-z0-9_]", "_");

      if(base.length() > 20) {
         base = base.substring(0, 20);
      }

      Viewsheet.WizInfo wizInfo = dashVS.getWizInfo();
      int count = wizInfo == null ? 0 : wizInfo.getMergedVisualizations().size();

      return base + "_" + count;
   }

   private final ViewsheetService viewsheetService;
   private final AssetRepository assetRepository;
   private final WsMergeService wsMergeService;

   private static final Logger LOG = LoggerFactory.getLogger(AddVisualizationService.class);
}
