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
package inetsoft.web.composer.ws;

import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Worksheet-level merge utilities shared by AddVisualizationService and GenerateWsService.
 *
 * <p>Core rule: each {@link BoundTableAssembly} appears only once in the target worksheet.
 * Different queries that share the same physical table each get their own
 * {@link MirrorTableAssembly} (tagged with {@link #PROP_WIZ_MERGED}) that carries
 * independent column selections, conditions, and aggregation.</p>
 */
@Service
public class WsMergeService {

   /**
    * Property key set on every MirrorTableAssembly created by the wiz merge process.
    * Used to identify wiz-managed mirrors without relying on name prefixes.
    */
   public static final String PROP_WIZ_MERGED = "wiz.merged";

   /**
    * Merges all WSAssemblies from {@code vizWS} into {@code dashWS} and returns a map of
    * old assembly names (in vizWS) to their effective names in dashWS after merging.
    *
    * <p>For each {@link BoundTableAssembly} in vizWS:</p>
    * <ul>
    *   <li>If a matching table already exists in dashWS (same data source): expand the base
    *       with any new columns, ensure it has a "prev" mirror for the first visualization,
    *       and create a new mirror for the current visualization.</li>
    *   <li>Otherwise: clone the assembly, resolve name conflicts, and add it directly.</li>
    * </ul>
    *
    * @param vizWS      source worksheet (the new query being added)
    * @param dashWS     target worksheet (accumulates all queries)
    * @param vizSuffix  unique suffix used to resolve name conflicts
    * @param vsRenameMap out-parameter: accumulates base→prevMirror renames that the caller
    *                   should propagate to any VS-level bindings (may be ignored when there
    *                   is no associated viewsheet)
    * @return map of vizWS assembly name → final name in dashWS
    */
   public Map<String, String> mergeWorksheet(Worksheet vizWS, Worksheet dashWS,
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
               ensureBaseHasPrevMirror(dashWS, existingTable, vsRenameMap);
               mergeColumns(existingTable, srcBound);
               String curMirrorName = createVizMirror(dashWS, existingTable, srcBound);
               wsRenameMap.put(srcBound.getName(), curMirrorName);
               continue;
            }
         }

         // Situation B: everything else — clone and resolve name conflicts
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
    * Generates a unique suffix for this merge pass based on the given name and the current
    * number of assemblies in the target worksheet.
    */
   public String computeUniqueSuffix(String name, Worksheet ws) {
      String base = name.replaceAll("[^A-Za-z0-9_]", "_");

      if(base.length() > 20) {
         base = base.substring(0, 20);
      }

      int count = ws.getAssemblies().length;
      return base + "_" + count;
   }

   /**
    * Returns the BoundTableAssembly in {@code dashWS} that shares the same data source
    * (type + datasource prefix + physical table/query name) as {@code srcTable}, or
    * {@code null} if none exists.
    */
   private BoundTableAssembly findMergeableTable(Worksheet dashWS, BoundTableAssembly srcTable) {
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
    * that takes its original role), and records the rename in {@code vsRenameMap} so the
    * caller can redirect any VS-level bindings.
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

      // Collect the redirect so the caller can apply it to VS bindings if needed
      vsRenameMap.put(baseName, prevMirrorName);
   }

   /**
    * Expands {@code base}'s public column selection with any columns from {@code srcTable}
    * that are not already present (judged by column name).
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
    * Creates a MirrorTableAssembly in {@code dashWS} that points at {@code base} and
    * carries only the columns/conditions/aggregation of {@code srcTable}.
    *
    * @return the name of the created mirror assembly
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

   /**
    * Performs a topological sort of the assemblies in {@code ws} so that dependencies are
    * processed before the assemblies that depend on them (sources before sinks).
    * Uses Kahn's algorithm on the dependency graph derived from
    * {@link WSAssembly#getDependeds(Set)}.
    */
   private List<WSAssembly> topologicalSort(Worksheet ws) {
      Assembly[] all = ws.getAssemblies();
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

      Map<String, Integer> inDegree = new HashMap<>();

      for(Map.Entry<String, Set<String>> e : deps.entrySet()) {
         inDegree.put(e.getKey(), e.getValue().size());
      }

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
    * Generates a unique name by appending {@code "_" + vizSuffix}, then an incrementing
    * counter if still conflicting.
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
}
