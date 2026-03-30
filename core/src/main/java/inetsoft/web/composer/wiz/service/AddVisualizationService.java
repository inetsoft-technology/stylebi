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
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
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

   /**
    * Property key set on every MirrorTableAssembly that was created by the wiz merge process.
    * Used to identify wiz-managed mirrors without relying on name prefixes.
    */
   static final String PROP_WIZ_MERGED = "wiz.merged";

   public AddVisualizationService(ViewsheetService viewsheetService,
                                  AssetRepository assetRepository)
   {
      this.viewsheetService = viewsheetService;
      this.assetRepository = assetRepository;
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
      Map<String, String> wsRenameMap = mergeWorksheet(vizWS, dashWS, vizSuffix, vsRenameMap);

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
   // Step 1: Worksheet merge
   // ---------------------------------------------------------------------------

   /**
    * Merges all WSAssemblies from {@code vizWS} into {@code dashWS} and returns a map of
    * old assembly names (in vizWS) to their effective names in dashWS after merging.
    */
   private Map<String, String> mergeWorksheet(Worksheet vizWS, Worksheet dashWS,
                                              String vizSuffix,
                                              Map<String, String> vsRenameMap)
   {
      Map<String, String> wsRenameMap = new HashMap<>();
      List<WSAssembly> sorted = topologicalSort(vizWS);

      for(WSAssembly srcAssembly : sorted) {
         // Situation A: BoundTableAssembly with same data source as an existing one
         if(srcAssembly instanceof BoundTableAssembly srcBound) {
            BoundTableAssembly existingTable = findMergeableTable(dashWS, srcBound);

            if(existingTable != null) {
               // Ensure the existing table has a "prev" mirror (first-merge promotion).
               // Any VS binding redirects are collected into vsRenameMap and applied later.
               ensureBaseHasPrevMirror(dashWS, existingTable, vsRenameMap);
               // Expand the base with any new columns from srcBound
               mergeColumns(existingTable, srcBound);
               // Create a new mirror for this visualization's column/condition view
               String curMirrorName = createVizMirror(dashWS, existingTable, srcBound);
               wsRenameMap.put(srcBound.getName(), curMirrorName);
               continue;
            }
         }

         // Situation B: everything else — clone and resolve name conflicts
         // Note: if this assembly (e.g. MirrorTableAssembly, CompositeTableAssembly) references
         // a BoundTableAssembly that was already processed under Situation A, the rename
         // (srcBound → curMirrorName) is already in wsRenameMap. The renameDepended loop below
         // will correctly update the child reference, because topological order guarantees the
         // BoundTableAssembly entry is in wsRenameMap before this downstream assembly is reached.
         WSAssembly cloned = (WSAssembly) srcAssembly.clone();
         String originalName = srcAssembly.getName();
         String targetName = originalName;

         if(dashWS.getAssembly(targetName) != null) {
            targetName = resolveNameConflict(originalName, vizSuffix, dashWS);
            cloned.getWSAssemblyInfo().setName(targetName);
            wsRenameMap.put(originalName, targetName);
         }

         // addAssembly sets cloned.ws = dashWS, enabling correct renameDepended behaviour
         dashWS.addAssembly(cloned);

         // Apply accumulated renames to update child references within this assembly
         for(Map.Entry<String, String> entry : wsRenameMap.entrySet()) {
            cloned.renameDepended(entry.getKey(), entry.getValue());
         }
      }

      return wsRenameMap;
   }

   /**
    * Returns the BoundTableAssembly in {@code dashWS} that shares the same data source
    * (type + datasource prefix + physical table/query name) as {@code srcTable}, or
    * {@code null} if none exists.
    */
   private BoundTableAssembly findMergeableTable(Worksheet dashWS,
                                                 BoundTableAssembly srcTable)
   {
      SourceInfo srcInfo = srcTable.getSourceInfo();

      if(srcInfo == null || srcInfo.isEmpty()) {
         return null;
      }

      for(Assembly a : dashWS.getAssemblies()) {
         if(!(a instanceof BoundTableAssembly candidate)) {
            continue;
         }

         SourceInfo candidateInfo = candidate.getSourceInfo();

         if(candidateInfo == null || candidateInfo.isEmpty()) {
            continue;
         }

         if(srcInfo.getType() == candidateInfo.getType() &&
            Objects.equals(srcInfo.getPrefix(), candidateInfo.getPrefix()) &&
            Objects.equals(srcInfo.getSource(), candidateInfo.getSource()))
         {
            return candidate;
         }
      }

      return null;
   }

   /**
    * If {@code existingTable} does not yet have a wiz mirror pointing to it, promotes it
    * to a "base" table: strips its conditions/aggregation (moving them to a new mirror
    * that takes its original role), and updates dashVS VS bindings to point at the mirror.
    */
   private void ensureBaseHasPrevMirror(Worksheet dashWS,
                                        BoundTableAssembly existingTable,
                                        Map<String, String> vsRenameMap)
   {
      String baseName = existingTable.getName();

      // Check if any wiz mirror already targets this base
      boolean hasMirror = Arrays.stream(dashWS.getAssemblies())
         .anyMatch(a -> a instanceof MirrorTableAssembly m &&
            "true".equals(m.getProperty(PROP_WIZ_MERGED)) &&
            Objects.equals(m.getAssemblyName(), baseName));

      if(hasMirror) {
         return; // already promoted in a previous merge
      }

      // Save the original semantics that belong to the first visualization
      ConditionListWrapper preconds = existingTable.getPreConditionList();
      ConditionListWrapper postconds = existingTable.getPostConditionList();
      AggregateInfo aggr = (AggregateInfo) existingTable.getAggregateInfo().clone();
      ColumnSelection origCols = existingTable.getColumnSelection(true).clone();

      // Strip conditions/aggregation from the base so it becomes a "full data" query
      existingTable.setPreConditionList(new ConditionList());
      existingTable.setPostConditionList(new ConditionList());
      existingTable.setAggregateInfo(new AggregateInfo());

      // Create a mirror that restores the original viz's view
      String prevMirrorName = ensureUniqueName(baseName, dashWS);
      MirrorTableAssembly prevMirror = new MirrorTableAssembly(dashWS, prevMirrorName, existingTable);
      prevMirror.setColumnSelection(origCols, true);
      prevMirror.setPreConditionList(preconds);
      prevMirror.setPostConditionList(postconds);
      prevMirror.setAggregateInfo(aggr);
      prevMirror.setProperty(PROP_WIZ_MERGED, "true");
      dashWS.addAssembly(prevMirror);

      // Collect the redirect (existingTable → prevMirror) so the caller can apply it to dashVS
      // after the full WS merge is complete, rather than touching VS assemblies mid-merge.
      vsRenameMap.put(baseName, prevMirrorName);
   }

   /**
    * Expands {@code base}'s public column selection with any columns from {@code srcTable}
    * that are not already present.
    */
   private void mergeColumns(BoundTableAssembly base, BoundTableAssembly srcTable) {
      ColumnSelection baseColumns = base.getColumnSelection(true);
      ColumnSelection srcColumns = srcTable.getColumnSelection(true);

      for(int i = 0; i < srcColumns.getAttributeCount(); i++) {
         DataRef col = srcColumns.getAttribute(i);

         if(baseColumns.getAttribute(col.getName()) == null) {
            baseColumns.addAttribute(col);
         }
      }

      base.setColumnSelection(baseColumns, true);
   }

   /**
    * Creates a MirrorTableAssembly in {@code dashWS} that points at {@code base} and exposes
    * only the columns (and conditions/aggregation) of {@code srcTable}.
    *
    * @return the name of the created mirror assembly.
    */
   private String createVizMirror(Worksheet dashWS, BoundTableAssembly base,
                                  BoundTableAssembly srcTable)
   {
      String mirrorName = ensureUniqueName(base.getName(), dashWS);

      ColumnSelection vizCols = srcTable.getColumnSelection(true).clone();

      MirrorTableAssembly mirror = new MirrorTableAssembly(dashWS, mirrorName, base);
      mirror.setColumnSelection(vizCols, true);
      mirror.setPreConditionList((ConditionListWrapper) srcTable.getPreConditionList().clone());
      mirror.setPostConditionList((ConditionListWrapper) srcTable.getPostConditionList().clone());
      mirror.setAggregateInfo((AggregateInfo) srcTable.getAggregateInfo().clone());
      mirror.setProperty(PROP_WIZ_MERGED, "true");
      dashWS.addAssembly(mirror);

      return mirrorName;
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
    * Performs a topological sort of the assemblies in {@code ws} so that dependencies are
    * processed before the assemblies that depend on them (sources before sinks).
    * Uses Kahn's algorithm on the dependency graph derived from
    * {@link WSAssembly#getDependeds(Set)}.
    */
   private List<WSAssembly> topologicalSort(Worksheet ws) {
      Assembly[] all = ws.getAssemblies();

      // deps: assembly name → names of WS assemblies it depends on
      // revDeps: assembly name → names of WS assemblies that depend on it
      Map<String, Set<String>> deps = new HashMap<>();
      Map<String, Set<String>> revDeps = new HashMap<>();

      for(Assembly a : all) {
         deps.put(a.getName(), new HashSet<>());
         revDeps.put(a.getName(), new HashSet<>());
      }

      for(Assembly a : all) {
         Set<AssemblyRef> depRefs = new HashSet<>();
         ((WSAssembly) a).getDependeds(depRefs);

         for(AssemblyRef ref : depRefs) {
            String depName = ref.getEntry().getName();

            if(deps.containsKey(depName)) {
               deps.get(a.getName()).add(depName);
               revDeps.get(depName).add(a.getName());
            }
         }
      }

      // Compute in-degree (number of dependencies each assembly has within this WS)
      Map<String, Integer> inDegree = new HashMap<>();

      for(Map.Entry<String, Set<String>> e : deps.entrySet()) {
         inDegree.put(e.getKey(), e.getValue().size());
      }

      // Seed queue with root nodes (no WS-internal dependencies)
      Queue<String> queue = new LinkedList<>();

      for(Map.Entry<String, Integer> e : inDegree.entrySet()) {
         if(e.getValue() == 0) {
            queue.add(e.getKey());
         }
      }

      List<WSAssembly> sorted = new ArrayList<>(all.length);

      while(!queue.isEmpty()) {
         String name = queue.poll();
         WSAssembly node = (WSAssembly) ws.getAssembly(name);

         if(node != null) {
            sorted.add(node);
         }

         for(String downstream : revDeps.getOrDefault(name, Collections.emptySet())) {
            if(inDegree.merge(downstream, -1, Integer::sum) == 0) {
               queue.add(downstream);
            }
         }
      }

      // Append any assemblies not reached (e.g. cycles or disconnected nodes)
      Set<String> visited = new HashSet<>();

      for(WSAssembly a : sorted) {
         visited.add(a.getName());
      }

      for(Assembly a : all) {
         if(!visited.contains(a.getName())) {
            sorted.add((WSAssembly) a);
         }
      }

      return sorted;
   }

   /**
    * Generates a unique name for a new assembly in {@code ws} by appending {@code "_" + vizSuffix},
    * then a counter if still conflicting.
    */
   private String resolveNameConflict(String originalName, String vizSuffix, Worksheet ws) {
      String candidate = originalName + "_" + vizSuffix;

      if(ws.getAssembly(candidate) == null) {
         return candidate;
      }

      int counter = 2;

      while(ws.getAssembly(candidate) != null) {
         candidate = originalName + "_" + vizSuffix + "_" + counter++;
      }

      return candidate;
   }

   /**
    * Returns {@code name} if it does not already exist in {@code ws}, otherwise appends
    * an incrementing counter until a free name is found.
    */
   private String ensureUniqueName(String name, Worksheet ws) {
      if(ws.getAssembly(name) == null) {
         return name;
      }

      int counter = 1;
      String candidate = name + "_" + counter;

      while(ws.getAssembly(candidate) != null) {
         candidate = name + "_" + (++counter);
      }

      return candidate;
   }

   /**
    * Computes a short, unique name suffix for this visualization by sanitizing the entry
    * name (keep alphanumeric + underscore, max 20 chars) and appending a counter if the
    * same suffix has already been used in dashVS.
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

   private static final Logger LOG = LoggerFactory.getLogger(AddVisualizationService.class);
}
