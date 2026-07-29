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

import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.Viewsheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Worksheet-level merge utilities shared by AddVisualizationService and GenerateWsService.
 *
 * <p>Core rule: each {@link BoundTableAssembly} appears only once in the target worksheet.
 * Queries that share the same physical table reuse the shared prevMirror where possible
 * (enabling cross-chart filter interaction). A new {@link MirrorTableAssembly} (tagged with
 * {@link #PROP_WIZ_MERGED}) is created on top of prevMirror only when the incoming table
 * carries its own conditions or aggregation that must be isolated.</p>
 *
 * <p><b>Known limitation — condition stacking:</b> When two charts share the same physical
 * table and each has its own design-time pre/post conditions, chart2's condMirror is stacked
 * on prevMirror (which already carries chart1's conditions). Because MirrorTableAssembly
 * evaluates its own conditions on top of the mirrored table's result, chart2 is effectively
 * filtered by chart1's conditions AND chart2's conditions combined. This is an inherent
 * trade-off of sharing the node for cross-chart filter propagation; stacking on the bare
 * {@code _base} would break that propagation. Callers should be aware that merging two charts
 * with conflicting static filters on the same physical table may yield unexpected results.</p>
 */
@Service
public class WsMergeService {

   /**
    * Property key set on every MirrorTableAssembly created by the wiz merge process.
    * Used to identify wiz-managed mirrors without relying on name prefixes.
    */
   public static final String PROP_WIZ_MERGED = "wiz.merged";

   private static final Logger LOG = LoggerFactory.getLogger(WsMergeService.class);

   /**
    * Merges all WSAssemblies from {@code vizWS} into {@code dashWS} and returns a map of
    * old assembly names (in vizWS) to their effective names in dashWS after merging.
    *
    * <p>For each {@link BoundTableAssembly} in vizWS:</p>
    * <ul>
    *   <li>If a matching table already exists in dashWS (same data source): expand the base
    *       with any new columns, ensure it has a "prev" mirror for the first visualization,
    *       then reuse that mirror directly (no conditions/aggregation) or stack a new mirror
    *       on top of it (has conditions/aggregation) for the current visualization.</li>
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
               String prevMirrorName = ensureBaseHasPrevMirror(dashWS, existingTable, vsRenameMap);
               mergeColumns(existingTable, srcBound);
               String baseName = existingTable.getName();

               Assembly prevAssembly = dashWS.getAssembly(prevMirrorName);
               // Use instanceof guard rather than a raw cast: a name collision could place a
               // non-table assembly at prevMirrorName, which would throw ClassCastException.
               TableAssembly prevMirror = prevAssembly instanceof TableAssembly ta ? ta : null;

               if(prevMirror == null) {
                  // Unexpected: ensureBaseHasPrevMirror should always create the mirror.
                  // Do not map srcBound to the missing name — that would insert a dangling
                  // reference into wsRenameMap and silently corrupt downstream joins.
                  // Log and skip; the assembly remains unmapped in this merge pass.
                  LOG.warn("prevMirror '{}' not found in dashWS after ensureBaseHasPrevMirror; " +
                           "skipping rename mapping for '{}'",
                           prevMirrorName, srcBound.getName());
                  continue;
               }

               // After mergeColumns may have added new columns to the base, refresh
               // prevMirror's column selection so stacked mirrors (condMirror / cleanMirror)
               // can see all columns transitively, even when they override their own selection.
               // This runs unconditionally before the branch so any code path below benefits.
               //
               // Snapshot/restore prevMirror's own AggregateInfo around this call:
               // setColumnSelection (the false/private overload) internally calls
               // AggregateInfo#validate(newSelection), which removes any group/aggregate whose
               // ref doesn't resolve against the newly-set selection. prevMirror's own
               // aggregation predates and is unrelated to the column-selection expansion this
               // method does for downstream stacked mirrors, and mergeMirrorColumns runs again
               // for every later chart sharing this physical table -- each call would otherwise
               // re-validate prevMirror's groups/aggregates against the rebuilt selection and
               // can strip them. The column-selection expansion is still needed (downstream
               // joins/mirrors read from prevMirror's column list), so restore the aggregation
               // afterward rather than skip the call.
               AggregateInfo prevMirrorAggrBefore = (AggregateInfo) prevMirror.getAggregateInfo().clone();
               mergeMirrorColumns(prevMirror, baseName, existingTable.getColumnSelection(true));
               // Do NOT simply clone-and-restore prevMirrorAggrBefore here: its refs point at
               // the ColumnRef instances that existed BEFORE mergeMirrorColumns ran, which --
               // for a group/aggregate whose column didn't already exist bare in prevMirror's
               // own selection -- mergeMirrorColumns just replaced with a freshly added,
               // outer-attribute-qualified column (e.g. "sale_order_base.order_count" instead
               // of bare "order_count"). A plain restore leaves refs pointing at a name no
               // longer present in prevMirror's own (now expanded) selection: harmless
               // in-memory (stale ref objects still work by identity), but StyleBI's own
               // query-construction path (AssetQuery#createAssetQuery, run on every viewsheet
               // open) independently re-resolves and re-validates a mirror's aggregate columns
               // by name -- see ensureBaseHasPrevMirror's own qualification for the full
               // mechanism. Rebuild each ref against whatever column actually carries its name
               // in prevMirror's OWN CURRENT (post-merge) selection instead, trying the
               // qualified name too.
               prevMirror.setAggregateInfo(
                  rebindAggregateInfo(prevMirrorAggrBefore, prevMirror.getColumnSelection(false), baseName));
               prevMirror.setAggregate(!prevMirror.getAggregateInfo().isEmpty());

               ConditionListWrapper srcPre = srcBound.getPreConditionList();
               ConditionListWrapper srcPost = srcBound.getPostConditionList();
               AggregateInfo srcAggr = srcBound.getAggregateInfo();
               boolean hasConditions = (srcPre != null && !srcPre.isEmpty()) ||
                                       (srcPost != null && !srcPost.isEmpty());
               boolean hasAggregation = srcAggr != null && !srcAggr.isEmpty();

               if(hasConditions || hasAggregation) {
                  // srcBound carries its own conditions or aggregation. Normally stack a mirror of
                  // prevMirror (not _base) so that runtime filters applied to prevMirror propagate
                  // through this mirror and on to any join that references it, preserving
                  // cross-chart filter interaction while retaining srcBound's conditions and
                  // aggregation.
                  //
                  // EXCEPTION -- prevMirror carries its OWN non-empty AggregateInfo (from whichever
                  // earlier chart first claimed this physical table): stacking srcBound's aggregation
                  // on top of prevMirror would group/aggregate an ALREADY-aggregated, differently
                  // grouped result, which is structurally unsound whenever the two charts group by
                  // different levels (e.g. two independent Quarter(date_order) groupings from two
                  // unrelated charts on the same source table) -- the SQL engine would silently
                  // disambiguate the resulting duplicate output column name with a "_1" suffix
                  // neither chart's own VSChartInfo binding is told about, crashing whichever
                  // chart's graph renders next. Stack on the raw, unaggregated _base table instead
                  // in this case: correctness beats cross-chart-filter-sharing convenience.
                  // existingTable here IS the raw base (ensureBaseHasPrevMirror already renamed it
                  // to "{name}_base" and stripped its conditions/aggregation), so it's always a
                  // safe, clean stacking point regardless of what prevMirror carries.
                  AggregateInfo prevOwnAggr = prevMirror.getAggregateInfo();
                  boolean prevHasIncompatibleAggr = prevOwnAggr != null && !prevOwnAggr.isEmpty();
                  TableAssembly stackOn = prevHasIncompatibleAggr ? existingTable : prevMirror;
                  String condMirrorName = ensureUniqueName(prevMirrorName, dashWS);
                  MirrorTableAssembly condMirror = new MirrorTableAssembly(dashWS, condMirrorName, stackOn);
                  // Set BOTH public AND private selection — same "public without private" hazard
                  // documented on mergeColumns above: AbstractTableAssembly#resetColumnSelection
                  // regenerates a table's public selection FROM its private one whenever the
                  // selection is next validated during query construction, which for an
                  // AGGREGATED mirror happens as a normal part of preparing the aggregate query.
                  // Leaving condMirror's private selection at its default-empty state would let
                  // that reset wipe its public selection back down to (at most) the bare group
                  // dimension, silently dropping every aggregate output. srcBound's own private
                  // selection is exactly right here: it already carries both the genuine
                  // underlying raw column(s) the aggregation reads from AND its own
                  // group/aggregate output names (see mergeableSourceColumns' javadoc for why
                  // that's srcBound's private shape).
                  condMirror.setColumnSelection(srcBound.getColumnSelection(true).clone(), true);
                  condMirror.setColumnSelection(srcBound.getColumnSelection(false).clone(), false);
                  condMirror.setPreConditionList(srcPre != null ? (ConditionListWrapper) srcPre.clone() : new ConditionList());
                  condMirror.setPostConditionList(srcPost != null ? (ConditionListWrapper) srcPost.clone() : new ConditionList());
                  // MirrorTableAssembly.getAggregateInfo() returns its own field, not the
                  // mirrored table's. Setting new AggregateInfo() when srcAggr is null means
                  // "no additional aggregation on this mirror" — it does not suppress
                  // prevMirror's aggregation. Verified: AbstractTableAssembly.getAggregateInfo()
                  // returns ginfo (own field) at all levels; there is no inherited aggregation.
                  boolean condHasAggr = srcAggr != null && !srcAggr.isEmpty();
                  condMirror.setAggregateInfo(condHasAggr ? (AggregateInfo) srcAggr.clone() : new AggregateInfo());
                  // Same isAggregate()-flag hazard as prevMirror above (see ensureBaseHasPrevMirror)
                  // — setAggregateInfo alone leaves this separate flag false, and something in the
                  // worksheet persist/reload path gates on isAggregate(), silently dropping a
                  // genuinely non-empty AggregateInfo by the time the worksheet reloads.
                  condMirror.setAggregate(condHasAggr);

                  // Qualify condMirror's own AGGREGATE refs against the table it mirrors, exactly
                  // as ensureBaseHasPrevMirror does for prevMirror -- condMirror missed this and it
                  // is the same bug: AssetQuery.createAssetQuery (via AssetQuerySandbox#
                  // refreshColumnSelection, every viewsheet open) re-derives this mirror's columns
                  // from the mirrored table's raw outputs (outer-attribute qualified) and
                  // AggregateInfo#validate() drops any aggregate whose ref doesn't resolve against
                  // them. srcBound's aggregate refs point at bare/aliased names (e.g. "order_count"
                  // aliasing "id", "avg_order_value" aliasing "amount_total") that the raw
                  // re-derivation never produces, so they were silently dropped -- collapsing the
                  // chart to just its group ("Aggregate not found: <alias>").
                  //
                  // Unlike prevMirror (whose aggregates were on already-base-named columns),
                  // condMirror's aggregate outputs are ALIASED: qualifying the aliased column
                  // itself would yield "base.order_count" (getOuterAttribute uses the alias as the
                  // name), which still won't match the raw re-derivation. Qualify the aggregate's
                  // UNDERLYING column instead ("base.id") and re-apply the output alias, so the ref
                  // resolves against the re-derived raw column while keeping its output name.
                  if(condHasAggr) {
                     AggregateInfo condAggr = condMirror.getAggregateInfo();
                     ColumnSelection condPub = condMirror.getColumnSelection(true);
                     ColumnSelection condPriv = condMirror.getColumnSelection(false);
                     String condBase = stackOn.getName();

                     for(int ai = 0; ai < condAggr.getAggregateCount(); ai++) {
                        AggregateRef aref = condAggr.getAggregate(ai);
                        DataRef aggColRef = aref.getDataRef();

                        if(aggColRef == null) {
                           continue;
                        }

                        String outputName = aggColRef.getName();
                        String alias = (aggColRef instanceof ColumnRef)
                           ? ((ColumnRef) aggColRef).getAlias() : null;
                        // Qualify the underlying column (raw name), not the aliased wrapper, so the
                        // result is "base.<rawName>" matching what the re-derivation produces.
                        DataRef underlying = (aggColRef instanceof ColumnRef)
                           ? ((ColumnRef) aggColRef).getDataRef() : aggColRef;
                        ColumnRef qualifiedRef =
                           new ColumnRef(AssetUtil.getOuterAttribute(condBase, underlying));

                        if(alias != null) {
                           qualifiedRef.setAlias(alias);
                        }

                        aref.setDataRef(qualifiedRef);
                        replaceColumnByName(condPub, outputName, qualifiedRef);
                        replaceColumnByName(condPriv, outputName, qualifiedRef);
                     }

                     condMirror.setColumnSelection(condPub, true);
                     condMirror.setColumnSelection(condPriv, false);
                  }

                  condMirror.setProperty(PROP_WIZ_MERGED, "true");
                  dashWS.addAssembly(condMirror);
                  wsRenameMap.put(srcBound.getName(), condMirrorName);
               }
               else {
                  // srcBound has no conditions or aggregation of its own. Before reusing
                  // prevMirror directly, check whether prevMirror carries design-time
                  // conditions or aggregation from the first chart. If so, stack a clean
                  // mirror of prevMirror so chart2 is not silently coupled to chart1's
                  // design-time filters, while runtime filter propagation through the
                  // shared prevMirror node is still preserved.
                  ConditionListWrapper prevPre = prevMirror.getPreConditionList();
                  ConditionListWrapper prevPost = prevMirror.getPostConditionList();
                  AggregateInfo prevAggr = prevMirror.getAggregateInfo();
                  boolean prevHasConditions = (prevPre != null && !prevPre.isEmpty()) ||
                                              (prevPost != null && !prevPost.isEmpty());
                  boolean prevHasAggregation = prevAggr != null && !prevAggr.isEmpty();

                  if(prevHasConditions || prevHasAggregation) {
                     // Stack a mirror of prevMirror so chart2 goes through the shared node
                     // and receives runtime cross-chart filter propagation. Note: because
                     // cleanMirror is stacked on prevMirror, chart2 will also see prevMirror's
                     // design-time conditions/aggregation — this is an inherent consequence of
                     // sharing the node, and is acceptable for the cross-filter use case.
                     String cleanMirrorName = ensureUniqueName(prevMirrorName, dashWS);
                     MirrorTableAssembly cleanMirror = new MirrorTableAssembly(dashWS, cleanMirrorName, prevMirror);
                     cleanMirror.setColumnSelection(srcBound.getColumnSelection(true).clone(), true);
                     cleanMirror.setProperty(PROP_WIZ_MERGED, "true");
                     dashWS.addAssembly(cleanMirror);
                     wsRenameMap.put(srcBound.getName(), cleanMirrorName);
                  }
                  else {
                     // prevMirror has no design-time conditions or aggregation — reuse it
                     // directly. srcBound's own column selection is not applied here —
                     // prevMirror's expanded union selection (refreshed above) is used instead,
                     // required for both charts to share a single node and enable cross-chart
                     // filter interaction. Side effect: chart1 (which also points to prevMirror)
                     // gains visibility into chart2's columns. This is acceptable: the wiz
                     // portal always requests explicit column sets, so extra visible columns in
                     // the shared node do not affect chart1's rendered output.
                     wsRenameMap.put(srcBound.getName(), prevMirrorName);
                  }
               }
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
    * Rebuilds {@code original}'s groups/aggregates so each ref's underlying DataRef points at
    * whatever column actually carries its name in {@code currentSelection} — trying the bare
    * name first, then the outer-attribute-qualified form ({@code baseName + "." + name}, the
    * shape {@link #mergeMirrorColumns} uses for a column that didn't already exist bare in the
    * mirror's own selection). A ref that resolves to neither is dropped (logged), rather than
    * left pointing at a column no longer present in the mirror's own selection — see the call
    * site in {@link #mergeWorksheet} for why a plain clone-and-restore is not equivalent.
    */
   private AggregateInfo rebindAggregateInfo(AggregateInfo original, ColumnSelection currentSelection,
                                             String baseName)
   {
      AggregateInfo rebuilt = new AggregateInfo();

      for(int i = 0; i < original.getGroupCount(); i++) {
         GroupRef group = (GroupRef) original.getGroup(i).clone();
         DataRef resolved = resolveByName(currentSelection, group.getName(), baseName);

         if(resolved != null) {
            group.setDataRef(resolved);
            rebuilt.addGroup(group);
         }
         else {
            LOG.warn("rebindAggregateInfo: could not resolve group '{}' against prevMirror's " +
                     "own column selection after merge (base={}); dropping it",
                     group.getName(), baseName);
         }
      }

      for(int i = 0; i < original.getAggregateCount(); i++) {
         AggregateRef aggr = (AggregateRef) original.getAggregate(i).clone();
         DataRef resolved = resolveByName(currentSelection, aggr.getName(), baseName);

         if(resolved != null) {
            aggr.setDataRef(resolved);
            rebuilt.addAggregate(aggr, false);
         }
         else {
            LOG.warn("rebindAggregateInfo: could not resolve aggregate '{}' against prevMirror's " +
                     "own column selection after merge (base={}); dropping it",
                     aggr.getName(), baseName);
         }
      }

      return rebuilt;
   }

   /**
    * Replaces the entry in {@code cols} named {@code name} (if any) with {@code replacement},
    * preserving its position. Used to keep an AggregateRef's DataRef and the mirror's own
    * column selection referencing the SAME qualified ColumnRef instance/name — see
    * {@link #ensureBaseHasPrevMirror} for why this consistency matters.
    */
   private void replaceColumnByName(ColumnSelection cols, String name, ColumnRef replacement) {
      for(int i = 0; i < cols.getAttributeCount(); i++) {
         if(name.equals(cols.getAttribute(i).getName())) {
            cols.setAttribute(i, replacement);
            return;
         }
      }

      // Not found: an AggregateRef's own underlying column should always already be present
      // in the table's own selection it was built from, so this is unexpected -- but add the
      // qualified column rather than silently leaving the (now-qualified) AggregateRef
      // pointing at a name absent from the selection, which would reproduce the exact
      // ref/selection mismatch this qualification exists to prevent.
      LOG.warn("replaceColumnByName: '{}' not found in column selection; adding '{}' instead " +
               "of replacing, to avoid leaving the AggregateRef pointing at a missing column",
               name, replacement.getName());
      cols.addAttribute(replacement);
   }

   private DataRef resolveByName(ColumnSelection selection, String name, String baseName) {
      DataRef found = selection.getAttribute(name);

      if(found != null) {
         return found;
      }

      return selection.getAttribute(baseName + "." + name);
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
    * to a "base" table: renames the base to "{name}_base", then creates a mirror under
    * the original name that carries the first viz's conditions/aggregation.
    *
    * <p>By keeping the original name on the mirror (not the bare base), any VS assemblies
    * already bound to that name continue to reference the correct filtered view without
    * needing to be updated.</p>
    */
   private String ensureBaseHasPrevMirror(Worksheet dashWS,
                                          BoundTableAssembly existingTable,
                                          Map<String, String> vsRenameMap)
   {
      String baseName = existingTable.getName();

      // Check if any wiz mirror already targets this base
      MirrorTableAssembly wizMirror = Arrays.stream(dashWS.getAssemblies())
         .filter(a -> a instanceof MirrorTableAssembly m &&
            "true".equals(m.getProperty(PROP_WIZ_MERGED)) &&
            Objects.equals(m.getAssemblyName(), baseName))
         .map(a -> (MirrorTableAssembly) a)
         .findFirst().orElse(null);

      if(wizMirror != null) {
         return wizMirror.getName(); // already promoted in a previous merge
      }

      // StyleBI's own Viewsheet machinery (Viewsheet#createMirrorTable, fired synchronously
      // from dashVS.addAssembly's reset listener when a VS chart is added to the dashboard
      // viewsheet) automatically wraps any table a chart binds to in an "outer" mirror under
      // the table's own bare name, renaming the original table out of the way first. That can
      // run before this class sees a later chart sharing the same physical source, so the
      // bare name (e.g. "sale_order") may already be occupied by this outer mirror, and
      // `existingTable` found by findMergeableTable (which only matches BoundTableAssembly) is
      // really the renamed-away original underneath it -- not the name any VS chart binding
      // actually resolves through. Creating a fresh prevMirror at existingTable's own name
      // would be orphaned (no VS binding references it) while the real bare name keeps
      // pointing at this pre-existing, empty-AggregateInfo pass-through mirror. Detect this
      // outer mirror and adapt it in place instead of creating a disconnected, unreachable
      // prevMirror elsewhere.
      //
      // MUST also require Viewsheet.VS_MIRROR_TABLE (the tag createMirrorTable itself sets) --
      // matching on base-pointer-name alone is not enough to identify ITS mirror specifically:
      // an entirely unrelated chart can have its OWN real, aggregation-bearing mirror built
      // directly on the same raw physical table for its own reasons (e.g. a "SO_QREV" quarterly
      // rollup mirror of "SO"), which also satisfies a bare name-pointer match. Without this
      // tag check, that unrelated chart's mirror gets misidentified as the empty placeholder and
      // ensureBaseHasPrevMirror overwrites its real AggregateInfo with the (empty) one belonging
      // to whichever OTHER chart's plain table happened to match physically -- silently wiping
      // out that chart's aggregation. Confirmed live: a chart's own quarterly-grouped chain lost
      // its group/aggregate columns entirely once a second, unrelated chart sharing the same
      // physical source got merged in after it.
      MirrorTableAssembly outerMirror = Arrays.stream(dashWS.getAssemblies())
         .filter(a -> a instanceof MirrorTableAssembly m &&
            "true".equals(m.getProperty(Viewsheet.VS_MIRROR_TABLE)))
         .map(a -> (MirrorTableAssembly) a)
         .filter(m -> Objects.equals(m.getAssemblyName(), baseName))
         .findFirst().orElse(null);

      // Save the original semantics that belong to the first visualization.
      // Guard against null: freshly constructed tables may return null condition lists.
      ConditionListWrapper preconds = existingTable.getPreConditionList() != null
         ? existingTable.getPreConditionList() : new ConditionList();
      ConditionListWrapper postconds = existingTable.getPostConditionList() != null
         ? existingTable.getPostConditionList() : new ConditionList();
      AggregateInfo existingAggr = existingTable.getAggregateInfo();
      AggregateInfo aggr = existingAggr != null ? (AggregateInfo) existingAggr.clone() : new AggregateInfo();
      ColumnSelection origCols = existingTable.getColumnSelection(true).clone();
      ColumnSelection origPrivateCols = existingTable.getColumnSelection(false).clone();

      MirrorTableAssembly prevMirror;
      String prevMirrorName;

      if(outerMirror != null) {
         // Adapt the pre-existing outer mirror in place; it already sits at the true bare
         // name every VS binding resolves through, so no rename of existingTable is needed.
         prevMirror = outerMirror;
         prevMirrorName = outerMirror.getName();
      }
      else {
         // No pre-existing outer mirror — original behavior. Rename the base to
         // "{name}_base", freeing the original name for a freshly created mirror.
         // renameAssembly updates the registry and calls renameDepended on all assemblies.
         prevMirrorName = baseName;
         String newBaseName = baseName + "_base";
         dashWS.renameAssembly(baseName, newBaseName, true);
         prevMirror = new MirrorTableAssembly(dashWS, prevMirrorName, existingTable);
      }

      // Strip conditions/aggregation from the (now-renamed, or already bare) base so it
      // becomes "full data".
      existingTable.setPreConditionList(new ConditionList());
      existingTable.setPostConditionList(new ConditionList());
      existingTable.setAggregateInfo(new AggregateInfo());

      // Qualify aggr's own AGGREGATE refs (not groups) with an outer-attribute reference to
      // the base table, and replace the matching bare-named entries in prevMirror's column
      // selection with the SAME qualified ColumnRef instance. Why: AssetQuery.createAssetQuery
      // -- StyleBI's generic query-construction entry point, invoked for every table on every
      // viewsheet open via AssetQuerySandbox#refreshColumnSelection, entirely independent of
      // this class -- independently resolves a mirror's aggregate columns down to their base
      // table using this exact outer-attribute qualification, then calls
      // table.setColumnSelection(..., false) with the result, which triggers
      // AggregateInfo#validate() against the newly-qualified selection. If aggr's own aggregate
      // refs still point at the original bare names (as cloned from the source table before any
      // merge), that validate() finds no match and silently drops them, while a group ref
      // survives untouched because groups are never routed through this same base-resolution
      // step (they read directly off the mirror; no aggregation needed). Qualifying up front
      // closes the gap between compose-time and query-time column resolution.
      String qualifierBase = existingTable.getName();

      for(int i = 0; i < aggr.getAggregateCount(); i++) {
         AggregateRef aref = aggr.getAggregate(i);
         DataRef originalRef = aref.getDataRef();
         String originalName = originalRef.getName();
         ColumnRef qualifiedRef = new ColumnRef(AssetUtil.getOuterAttribute(qualifierBase, originalRef));
         aref.setDataRef(qualifiedRef);
         replaceColumnByName(origCols, originalName, qualifiedRef);
         replaceColumnByName(origPrivateCols, originalName, qualifiedRef);
      }

      // Set BOTH public AND private selection when the FIRST chart onto this physical table
      // carries its own non-empty AggregateInfo (aggr) — same hazard as condMirror below:
      // leaving private at its default-empty state lets a later resetColumnSelection() (a normal
      // part of preparing an aggregate query) regenerate public FROM the empty private selection,
      // silently dropping every aggregate output. Harmless (a no-op beyond the extra assignment)
      // when aggr is empty, since a plain pass-through mirror never triggers that reset path.
      prevMirror.setColumnSelection(origCols, true);
      prevMirror.setColumnSelection(origPrivateCols, false);
      prevMirror.setPreConditionList(preconds);
      prevMirror.setPostConditionList(postconds);
      prevMirror.setAggregateInfo(aggr);
      // isAggregate() short-circuits to false when getAggregateInfo() is empty, but when it's
      // NOT empty, isAggregate() ALSO requires this separate flag (TableAssemblyInfo.isAggregate,
      // default false) to be explicitly set — setAggregateInfo alone does not set it. Keep the
      // flag consistent with the info's actual emptiness.
      prevMirror.setAggregate(!aggr.isEmpty());
      prevMirror.setProperty(PROP_WIZ_MERGED, "true");

      if(outerMirror == null) {
         // Create a mirror under the original name that restores the first viz's view.
         // VS assemblies already bound to baseName now point to this mirror — no VS update needed.
         dashWS.addAssembly(prevMirror);
      }

      return prevMirrorName;
   }

   /**
    * Expands {@code base}'s column selection with any columns from {@code srcTable} that are
    * not already present (judged by column name) — both its public (output) selection AND its
    * private (actually-selected/fetched) selection.
    *
    * <p>Only updating the public selection is not enough: {@link AbstractTableAssembly#resetColumnSelection}
    * and the query-preparation validation path ({@code PreAssetQuery#validateColumnSelection})
    * regenerate a table's public selection FROM its private selection (filtered to visible
    * columns) whenever the table's column selection is next validated/reset during query
    * construction — which silently discards a column added only to the public side. Confirmed
    * live: a JS-expression column two mirror-hops downstream (a dashboard's merged
    * {@code product_name} calc, depending on a hidden {@code product_name_json} base column)
    * evaluated to {@code null} for every row once merged into a dashboard, even though an
    * earlier version of this method correctly added {@code product_name_json} to the public
    * selection at merge time — because nothing had added it to the private selection, so the
    * very next {@code resetColumnSelection()} wiped it back out.</p>
    *
    * <p>{@code base} is a plain bound (non-mirror) table here, so the added private columns need
    * no outer-attribute qualification — contrast {@link #mergeMirrorColumns}, which merges the
    * same base's columns into the "prev" mirror stacked on top of it and DOES need it.</p>
    */
   private void mergeColumns(BoundTableAssembly base, BoundTableAssembly srcTable) {
      ColumnSelection baseColumns = base.getColumnSelection(true);
      ColumnSelection srcColumns = mergeableSourceColumns(srcTable);

      for(int i = 0; i < srcColumns.getAttributeCount(); i++) {
         DataRef col = srcColumns.getAttribute(i);

         if(baseColumns.getAttribute(col.getName()) == null) {
            baseColumns.addAttribute(col);
         }
      }

      ColumnSelection basePrivate = base.getColumnSelection(false);

      for(int i = 0; i < srcColumns.getAttributeCount(); i++) {
         DataRef col = srcColumns.getAttribute(i);

         if(basePrivate.getAttribute(col.getName()) == null) {
            ColumnRef privateCol = (ColumnRef) ((ColumnRef) col).clone();
            privateCol.setVisible(true);
            basePrivate.addAttribute(privateCol);
         }
      }

      base.setColumnSelection(basePrivate, false);
      base.setColumnSelection(baseColumns, true);
   }

   /**
    * Returns the columns from {@code srcTable} that are safe to merge into a SHARED, raw physical
    * base table (see {@link #mergeColumns}).
    *
    * <p>When {@code srcTable} carries no aggregation of its own, its public (output) selection IS
    * its set of genuine raw/pass-through columns — return those directly (unchanged behavior).</p>
    *
    * <p>When {@code srcTable} carries its OWN non-empty {@link AggregateInfo} (the shape
    * {@code create_worksheet_table}'s baked-in aggregateInfo produces), its public selection
    * instead reflects AGGREGATE OUTPUT names (e.g. a date-grouped "Quarter(date_order)", or an
    * aggregate alias like "order_count") — these are NOT genuine physical/raw columns. Merging
    * them into the shared base corrupts it: the base then falsely claims to already HAVE a column
    * with that name, so ANY OTHER chart merged onto the same physical table that independently
    * produces an output with the identical name (its own date-grouping, its own aggregate alias,
    * even a different chart's own chart-level dimension binding) collides with this bogus
    * pre-existing "raw" column. StyleBI's SQL builder then silently disambiguates the alias with
    * a "_1" suffix that no chart's own VSChartInfo binding is told about, crashing that OTHER
    * chart's graph render with ColumnNotFoundException the next time its dashboard tile opens —
    * confirmed live: two independent charts merged onto the same physical table, each producing
    * their own "Quarter(date_order)"-named output, crashed the FIRST chart's render this way even
    * though nothing about the first chart itself changed.
    *
    * <p>In that case, use {@code srcTable}'s PRIVATE selection instead — confirmed live to carry
    * the genuinely-fetched underlying raw column(s) the aggregation reads from (e.g. "date_order"
    * alongside its own "Quarter(date_order)" output) — filtered to exclude any column whose name
    * IS one of the aggregateInfo's own group/aggregate output names, so only the real raw
    * column(s) get merged onto the shared base.</p>
    */
   private ColumnSelection mergeableSourceColumns(BoundTableAssembly srcTable) {
      AggregateInfo srcAggr = srcTable.getAggregateInfo();

      if(srcAggr == null || srcAggr.isEmpty()) {
         return srcTable.getColumnSelection(true);
      }

      Set<String> outputNames = new HashSet<>();

      for(int i = 0; i < srcAggr.getGroupCount(); i++) {
         outputNames.add(srcAggr.getGroup(i).getName());
      }

      for(int i = 0; i < srcAggr.getAggregateCount(); i++) {
         outputNames.add(srcAggr.getAggregate(i).getName());
      }

      ColumnSelection rawCols = new ColumnSelection();
      ColumnSelection srcPrivate = srcTable.getColumnSelection(false);

      for(int i = 0; i < srcPrivate.getAttributeCount(); i++) {
         DataRef col = srcPrivate.getAttribute(i);

         if(!outputNames.contains(col.getName())) {
            rawCols.addAttribute(col);
         }
      }

      return rawCols;
   }

   /**
    * Expands {@code mirror}'s column selection with any columns from {@code baseColumns} (its
    * own base table's public selection, named {@code baseName}) that are not already present —
    * both public and private (see {@link #mergeColumns}'s javadoc for why both are required).
    *
    * <p>Unlike {@link #mergeColumns}, {@code mirror}'s PRIVATE selection references its base
    * table's columns by OUTER ATTRIBUTE — qualified by the base's name (e.g. the existing
    * {@code "PT_base.pt_id"}), not the base's bare column name. A newly-merged column added as a
    * bare (unqualified) clone — the same shape {@code mergeColumns} correctly uses for a plain
    * bound table — sits inconsistent with its siblings and never resolves back to the base
    * table's actual data; {@link AssetUtil#getOuterAttribute} produces the same qualified shape
    * the mirror's other private columns already have.</p>
    */
   private void mergeMirrorColumns(TableAssembly mirror, String baseName, ColumnSelection baseColumns) {
      ColumnSelection mirrorPublic = mirror.getColumnSelection(true);

      for(int i = 0; i < baseColumns.getAttributeCount(); i++) {
         DataRef col = baseColumns.getAttribute(i);

         if(mirrorPublic.getAttribute(col.getName()) == null) {
            mirrorPublic.addAttribute(col);
         }
      }

      ColumnSelection mirrorPrivate = mirror.getColumnSelection(false);

      for(int i = 0; i < baseColumns.getAttributeCount(); i++) {
         DataRef col = baseColumns.getAttribute(i);

         if(mirrorPrivate.getAttribute(col.getName()) == null) {
            ColumnRef privateCol = new ColumnRef(AssetUtil.getOuterAttribute(baseName, col));
            privateCol.setVisible(true);
            mirrorPrivate.addAttribute(privateCol);
         }
      }

      mirror.setColumnSelection(mirrorPrivate, false);
      mirror.setColumnSelection(mirrorPublic, true);
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
