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
package inetsoft.web.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
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
                                  WsMergeService wsMergeService,
                                  SecurityEngine securityEngine)
   {
      this.viewsheetService = viewsheetService;
      this.assetRepository = assetRepository;
      this.wsMergeService = wsMergeService;
      this.securityEngine = securityEngine;
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
      doAddVisualization(runtimeId, vizEntry, xOffset, yOffset, scale, principal);
      return null;
   }

   private void doAddVisualization(String runtimeId,
                                   AssetEntry vizEntry,
                                   int xOffset, int yOffset, float scale,
                                   Principal principal)
      throws Exception
   {
      // Action-level gate ("Visual Composer -> Data Worksheet"): the wiz dashboard runtime
      // referenced by runtimeId may not yet have a backing worksheet (dashVS.getBaseEntry()
      // == null on the first visualization added to a freshly created dashboard), in which
      // case this method creates a brand-new Worksheet() below. That runtime can be reached
      // via WizVsService.createViewsheet without ever passing through a WORKSHEET-gated
      // open/create flow, so the check must be enforced here rather than relying on the
      // caller having already been gated.
      if(!securityEngine.checkPermission(principal, ResourceType.WORKSHEET, "*", ResourceAction.ACCESS)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "composer.authorization.permissionDenied"));
      }

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

      // Load visualization VS.
      // permission=true is required: vizEntry comes straight from the client event
      // (WizComposerController -> AddVisualizationEvent.getEntry()), so the READ ACL must
      // actually run — otherwise a caller could merge any viewsheet they cannot read
      // (another user's private viewsheet) into their own dashboard and exfiltrate its data.
      // Mirrors WizVisualizationService.saveVisualization's permission=true load.
      Viewsheet vizVS = (Viewsheet) assetRepository.getSheet(
         vizEntry, principal, true, AssetContent.ALL);

      if(vizVS == null) {
         throw new Exception("Visualization viewsheet not found: " + vizEntry);
      }

      // Validate datasource is a worksheet
      AssetEntry baseEntry = vizVS.getBaseEntry();

      if(baseEntry == null || !baseEntry.isWorksheet()) {
         throw new IllegalArgumentException(
            "Visualization must be bound to a worksheet datasource: " + vizEntry);
      }

      // Load visualization WS.
      // permission=true for the same reason as the vizVS load above: baseEntry is derived from
      // the client-supplied vizEntry, so its READ ACL must run to prevent reading the source
      // tables of a worksheet the caller is not authorized for.
      Worksheet vizWS = (Worksheet) assetRepository.getSheet(
         baseEntry, principal, true, AssetContent.ALL);

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

      // Save the merged dashboard worksheet back to the repository BEFORE merging in the new VS
      // assemblies (Step 2, below). dashVS.addAssembly() fires a ViewsheetSandbox listener
      // (actionPerformed -> reset0 -> ... -> ChartVSAQuery#createBaseTableAssembly0) SYNCHRONOUSLY,
      // which resolves the newly-added chart's bound table through dashVS.getBaseWorksheet() --
      // dashVS's own cached `ws` field, which is separate from the local `dashWS` object just
      // merged above. Persisting first and then repopulating dashVS's cache (next line) ensures
      // that listener sees the fully-merged worksheet instead of the pre-merge (or previous
      // visualization's) snapshot, which otherwise makes the chart's own bound table (e.g.
      // "RADAR_OUT") look missing and logs CubeVSAQuery's "is not a data block" error the moment
      // the chart is merged in -- even though the persisted worksheet is correct throughout.
      assetRepository.setSheet(dashVS.getBaseEntry(), dashWS, principal, true);
      dashVS.repopulateWorksheet(assetRepository, principal);

      // Step 2: merge viewsheet assemblies
      mergeViewsheet(rvs, vizVS, dashVS, wsRenameMap, xOffset, yOffset, scale);

      // Step 3: record merged visualization
      wizInfo.addMergedVisualization(vizEntry.toIdentifier());
   }

   // ---------------------------------------------------------------------------
   // Step 2: Viewsheet merge
   // ---------------------------------------------------------------------------

   /**
    * Copies all VSAssemblies from {@code vizVS} into {@code dashVS}, applying name-conflict
    * resolution, WS binding renames, and drop-position offsets.
    */
   private void mergeViewsheet(RuntimeViewsheet rvs, Viewsheet vizVS, Viewsheet dashVS,
                               Map<String, String> wsRenameMap,
                               int xOffset, int yOffset, float scale)
   {
      if(scale <= 0f) {
         scale = 1f;
      }

      int offsetX = (int) (xOffset / scale);
      int offsetY = (int) (yOffset / scale);

      // Pass 1: clone every assembly and compute its final name, building vsConflictRenameMap
      //         so that intra-visualization cross-references can be patched in pass 2.
      //         (distinct from the outer vsRenameMap which tracks WS base→mirror renames)
      List<VSAssembly> clones = new ArrayList<>();
      Map<String, String> vsConflictRenameMap = new HashMap<>();

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
            vsConflictRenameMap.put(originalName, targetName);
         }

         clones.add(cloned);
      }

      // Pass 2: patch references, apply offset, and add to dashVS
      for(VSAssembly cloned : clones) {
         // Patch WS table binding references
         applyWsRenameToVSAssembly(cloned, wsRenameMap);

         // Patch VS-level cross-references (container children, linked/selection assemblies)
         for(Map.Entry<String, String> e : vsConflictRenameMap.entrySet()) {
            cloned.renameDepended(e.getKey(), e.getValue());
         }

         // Apply drop position offset
         Point pos = cloned.getPixelOffset();
         cloned.setPixelOffset(new Point(pos.x + offsetX, pos.y + offsetY));

         dashVS.addAssembly(cloned);

         // The dashboard runtime's sandbox may already hold a cached VGraphPair from before this
         // merge (e.g. an earlier visualization merged into the same running dashboard). That
         // cache is keyed by assembly name and never existed for this newly-added name, but the
         // sandbox's worksheet snapshot predates this merge either way -- clearing it here forces
         // a fresh execution against the just-merged worksheet instead of risking a stale/missing
         // graph entry. Mirrors the same clearGraph-after-in-place-mutation pattern used by
         // WizAutoBindingService/WizVsService for other runtime mutations.
         rvs.getViewsheetSandbox().ifPresent(box -> box.clearGraph(cloned.getName()));
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

   /**
    * Bulk-adds multiple visualizations to the wiz dashboard by identifier, stacking them
    * in a 3-column grid.  X and Y placement are determined by measuring the actual bounding
    * box of each newly merged visualization rather than using a fixed cell size, so the layout
    * adapts to the real dimensions of each visualization.
    *
    * <p>Layout rules:
    * <ul>
    *   <li>Visualizations fill left-to-right, wrapping to a new row after every 3.</li>
    *   <li>The X origin of each column is the right edge of the previous column plus {@code VIZ_GAP}.</li>
    *   <li>The Y origin of each row is the bottom edge of the tallest visualization in the
    *       previous row plus {@code VIZ_GAP}.</li>
    *   <li>Invalid or failed identifiers are skipped without shifting the grid position.</li>
    * </ul>
    *
    * @param runtimeId   the runtime ID of the wiz dashboard viewsheet.
    * @param identifiers AssetEntry identifier strings (as produced by {@link AssetEntry#toIdentifier()}).
    * @param principal   the current user.
    */
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void addVisualizationsByIds(@ClusterProxyKey String runtimeId,
                                      List<String> identifiers,
                                      Principal principal)
      throws Exception
   {
      int gridIndex = 0;  // counts only successfully placed visualizations
      int rowStartY = 0;
      int colStartX = 0;
      int rowMaxBottom = 0;

      for(String identifier : identifiers) {
         AssetEntry entry = AssetEntry.createAssetEntry(identifier);

         if(entry == null) {
            LOG.warn("Skipping invalid visualization identifier: {}", identifier);
            continue;
         }

         RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

         if(rvs == null) {
            LOG.warn("Runtime viewsheet expired during bulk add, stopping: {}", runtimeId);
            break;
         }

         Set<String> namesBefore = collectAssemblyNames(rvs.getViewsheet());

         try {
            doAddVisualization(runtimeId, entry, colStartX, rowStartY, 1.0f, principal);

            Rectangle added = computeAddedBounds(rvs.getViewsheet(), namesBefore);

            if(added != null) {
               colStartX = added.x + added.width + VIZ_GAP;
               rowMaxBottom = Math.max(rowMaxBottom, added.y + added.height);
            }

            gridIndex++;

            if(gridIndex % GRID_COLS == 0) {
               // Filled the last column of a row: advance Y for the next row.
               rowStartY = rowMaxBottom + VIZ_GAP;
               rowMaxBottom = rowStartY;
               colStartX = 0;
            }
         }
         catch(Exception e) {
            LOG.warn("Failed to add visualization '{}': {}", identifier, e.getMessage());
         }
      }

      return null;
   }

   /**
    * Returns the names of all assemblies currently in the viewsheet.
    * Used to identify which assemblies were added by the next merge operation.
    */
   private Set<String> collectAssemblyNames(Viewsheet vs) {
      Set<String> names = new HashSet<>();

      for(Assembly a : vs.getAssemblies()) {
         names.add(a.getName());
      }

      return names;
   }

   /**
    * Computes the bounding box of the assemblies added since {@code namesBefore} was captured.
    * Returns {@code null} if no new assemblies were added.
    */
   private Rectangle computeAddedBounds(Viewsheet vs, Set<String> namesBefore) {
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int maxRight = Integer.MIN_VALUE;
      int maxBottom = Integer.MIN_VALUE;
      boolean found = false;

      for(Assembly a : vs.getAssemblies()) {
         if(namesBefore.contains(a.getName()) || !(a instanceof VSAssembly va)) {
            continue;
         }

         Point pos = va.getPixelOffset();
         Dimension size = va.getPixelSize();

         if(pos == null || size == null) {
            continue;
         }

         minX = Math.min(minX, pos.x);
         minY = Math.min(minY, pos.y);
         maxRight = Math.max(maxRight, pos.x + size.width);
         maxBottom = Math.max(maxBottom, pos.y + size.height);
         found = true;
      }

      return found ? new Rectangle(minX, minY, maxRight - minX, maxBottom - minY) : null;
   }

   private final ViewsheetService viewsheetService;
   private final AssetRepository assetRepository;
   private final WsMergeService wsMergeService;
   private final SecurityEngine securityEngine;

   private static final int GRID_COLS = 3;
   private static final int VIZ_GAP = 50;
   private static final Logger LOG = LoggerFactory.getLogger(AddVisualizationService.class);
}
