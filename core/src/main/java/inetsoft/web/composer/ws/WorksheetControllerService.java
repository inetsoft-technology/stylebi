/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.*;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.ConditionListWrapper;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ScriptIterator;
import inetsoft.uql.erm.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSInsertColumnsEvent;
import inetsoft.web.composer.ws.event.WSInsertColumnsEventValidator;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class WorksheetControllerService {
   public WorksheetControllerService(ViewsheetService viewsheetService,
                                     DataSourceRegistry dataSourceRegistry)
   {
      this.wsEngine = viewsheetService;
      this.dataSourceRegistry = dataSourceRegistry;
   }

   protected RuntimeWorksheet getRuntimeWorksheet(String runtimeId, Principal principal)
      throws Exception
   {
      WorksheetService engine = getWorksheetEngine();
      return engine.getWorksheet(runtimeId, principal);
   }

   protected WorksheetService getWorksheetEngine() {
      return wsEngine;
   }

   protected DataSourceRegistry getDataSourceRegistry() {
      return dataSourceRegistry;
   }

   /**
    * Check if allows deletion.
    */
   public static boolean allowsDeletion(Worksheet ws, TableAssembly assembly, ColumnRef ref) {
      AssemblyRef[] arr = ws.getDependings(assembly.getAssemblyEntry());

      for(AssemblyRef assemblyRef : arr) {
         String assemblyName = assemblyRef.getEntry().getName();
         Assembly tmp = ws.getAssembly(assemblyName);

         if(tmp instanceof CompositeTableAssembly) {
            CompositeTableAssembly table = (CompositeTableAssembly) tmp;

            if(table.isColumnUsed(assembly, ref)) {
               return false;
            }
         }

         if(tmp instanceof TableAssembly) {
            TableAssembly table = (TableAssembly) tmp;
            DataRef dataRef = AssetUtil.getOuterAttribute(assemblyName, ref);
            ColumnRef ref2 =
               AssetUtil.getColumnRefFromAttribute(table.getColumnSelection(), dataRef);

            if(ref2 != null && !allowsDeletion(ws, table, ref2)) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Finds a column that would be reduced from row-level identity to an aggregate
    * output by {@code newInfo}, while still being relied on elsewhere as a
    * downstream JOIN's key column. Returns the offending column, or {@code null} if
    * none. Unlike {@link #allowsDeletion}, this also catches a column that survives
    * BY NAME as an aggregate field — {@code allowsDeletion} alone only catches a
    * column dropped from the table entirely.
    */
   public static ColumnRef findAggregateIdentityLossConflict(
      Worksheet ws, TableAssembly table, AggregateInfo newInfo)
   {
      if(newInfo == null || newInfo.isEmpty()) {
         return null;
      }

      ColumnSelection cols = table.getColumnSelection();

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         DataRef ref = cols.getAttribute(i);
         String col = ref.getName();

         if(newInfo.getGroup(col) != null) {
            continue;
         }

         if(!(ref instanceof ColumnRef)) {
            continue;
         }

         ColumnRef colRef = (ColumnRef) ref;

         if(colRef.getDataRef() instanceof DateRangeRef) {
            DateRangeRef dateRangeRef = (DateRangeRef) colRef.getDataRef();
            String innerRef = dateRangeRef.getDataRef().getName();

            if(newInfo.getGroup(innerRef) != null) {
               continue;
            }
         }

         if(!allowsDeletion(ws, table, colRef)) {
            return colRef;
         }
      }

      return null;
   }

   /**
    * Wiz-only variant of {@link #findAggregateIdentityLossConflict(Worksheet, TableAssembly,
    * AggregateInfo)} for {@code WorksheetMutationSupport#applyAggregateInfo}, which has already
    * cleared or re-set {@code table}'s aliases by the time it checks (Bug #78144 / WBS-098/100).
    * A column is at risk when its PRE-CALL output identity ({@code originalAliases}, else the
    * live alias, else the attribute) is not still a row-level column of the post-call output:
    * a visible column of an empty {@code newInfo}, a group of a flat one, or a group other
    * than the first of a crosstab ({@link AggregateInfo#isCrosstab()}). The downstream join is
    * looked up under that pre-call identity. The 3-arg overload keeps its name-only rule for
    * the Composer's Group and Aggregate dialog, which never clears aliases before checking.
    *
    * @return the live column of {@code table} whose loss breaks a downstream join, or
    *         {@code null}
    */
   public static ColumnRef findAggregateIdentityLossConflict(
      Worksheet ws, TableAssembly table, AggregateInfo newInfo,
      Map<ColumnRef, String> originalAliases)
   {
      if(newInfo == null) {
         return null;
      }

      for(ColumnRef colRef :
         findAggregateOutputLossCandidates(table, newInfo, originalAliases, false))
      {
         if(!allowsDeletion(ws, table, withPreCallAlias(colRef, originalAliases))) {
            return colRef;
         }
      }

      return null;
   }

   /**
    * One entry per downstream assembly whose OWN {@link AggregateInfo} would lose an
    * aggregate, or a group-by, because its input column is removed/re-grouped away from
    * {@code table} by the new {@code AggregateInfo}.
    */
   public record AggregateInputLossConflict(String dependentAssemblyName, List<String> lostColumns) {}

   /**
    * Finds every downstream assembly (transitively, not just direct dependents) whose
    * own {@link AggregateInfo} aggregates a column of {@code table} that {@code newInfo}
    * would drop from row-level identity. Unlike {@link #findAggregateIdentityLossConflict},
    * which only detects a downstream JOIN key conflict, this detects a column relied on
    * purely as another table's own aggregate INPUT (not a join key at all).
    */
   public static List<AggregateInputLossConflict> findAggregateInputLossConflicts(
      Worksheet ws, TableAssembly table, AggregateInfo newInfo)
   {
      return findAggregateInputLossConflicts(ws, table, newInfo, null);
   }

   /**
    * Same as {@link #findAggregateInputLossConflicts(Worksheet, TableAssembly, AggregateInfo)},
    * except the downstream lookup key for each of {@code table}'s OWN columns is built
    * from {@code originalAliases} (the column's alias before the CURRENT {@code
    * applyAggregateInfo} call touched anything), not the column's live -- possibly
    * already-mutated -- alias. See {@link #getOuterAttributeSnapshotAware}.
    * {@code originalAliases} may be {@code null}, matching the 3-arg overload's
    * unconditional live-alias lookup for any caller built before this parameter existed.
    *
    * <p>Deliberately runs even when {@code newInfo} is completely empty (a full
    * {@code groups:[], aggregates:[]} clear) -- unlike the sibling
    * {@link #findAggregateIdentityLossConflict}, whose own {@code isEmpty()} shortcut IS
    * safe there (a full clear can only ever RESTORE row-level identity, never newly
    * create the join-key-loss risk that guard exists to catch), an empty {@code newInfo}
    * here means every previously-aggregated column reverts to a plain column -- exactly
    * the maximal-loss case this guard exists to check, not one it can skip
    * (Bug #76891 / WBS-084(b)).</p>
    */
   public static List<AggregateInputLossConflict> findAggregateInputLossConflicts(
      Worksheet ws, TableAssembly table, AggregateInfo newInfo, Map<ColumnRef, String> originalAliases)
   {
      LinkedHashMap<String, List<String>> lostByDependent = new LinkedHashMap<>();

      if(newInfo == null) {
         return new ArrayList<>();
      }

      // Bug #78144 (WBS-098/099/100): a column is at risk only when its pre-call output
      // identity is not in the post-call output -- see findAggregateOutputLossCandidates.
      // Bug #76891 / WBS-087 still holds: a column that survives as an aggregate under its
      // unchanged identity stays addressable downstream and is not flagged.
      for(ColumnRef colRef :
         findAggregateOutputLossCandidates(table, newInfo, originalAliases, true))
      {
         findAggregateInputLossConflicts(
            ws, table, colRef, new HashSet<>(), lostByDependent, originalAliases);
      }

      List<AggregateInputLossConflict> conflicts = new ArrayList<>();

      for(Map.Entry<String, List<String>> entry : lostByDependent.entrySet()) {
         conflicts.add(new AggregateInputLossConflict(entry.getKey(), entry.getValue()));
      }

      return conflicts;
   }

   /**
    * Finds every downstream assembly (transitively) whose own {@link AggregateInfo}
    * relies on {@code ref} as an aggregate input or group-by key. Unlike the
    * {@code AggregateInfo}-scoped overloads above, {@code ref} is a single column
    * already known to be at risk -- there is no candidate-filtering step to run, because
    * hiding a column unconditionally removes it from every downstream consumer's view
    * (see {@code AbstractTableAssembly#setColumnSelection}'s invisible-column skip,
    * which the shared post-mutation {@code refreshAssemblies} cascade runs
    * unconditionally). Used by {@code set_column_visibility} (Bug #77001 / WBS-088),
    * which has no proposed {@link AggregateInfo} to derive candidates from in the first
    * place -- it is hiding one already-named column, not replacing a table's grouping.
    */
   public static List<AggregateInputLossConflict> findAggregateInputLossConflicts(
      Worksheet ws, TableAssembly table, ColumnRef ref)
   {
      LinkedHashMap<String, List<String>> lostByDependent = new LinkedHashMap<>();
      findAggregateInputLossConflicts(ws, table, ref, new HashSet<>(), lostByDependent, null);

      List<AggregateInputLossConflict> conflicts = new ArrayList<>();

      for(Map.Entry<String, List<String>> entry : lostByDependent.entrySet()) {
         conflicts.add(new AggregateInputLossConflict(entry.getKey(), entry.getValue()));
      }

      return conflicts;
   }

   /**
    * Bug #78144 (WBS-098/099/100): the columns of {@code table} whose PRE-CALL output identity
    * does not survive {@code newInfo}, i.e. the columns a downstream consumer can lose.
    *
    * <p>The pre-call identity is the column's alias before the current {@code
    * applyAggregateInfo} call ({@code originalAliases}, else its live alias), else its
    * attribute -- the same name {@link AssetUtil#getOuterAttribute} hands a dependent. The
    * post-call output, from {@code AbstractTableAssembly#setColumnSelection} and
    * {@code AssetQuery#getSummaryTableLens}, is:</p>
    * <ul>
    *   <li>empty {@code newInfo}: every visible column, under its final identity;</li>
    *   <li>flat: the groups, plus (when {@code aggregatesKeepIdentity}) the aggregates, under
    *       their final identities;</li>
    *   <li>{@link AggregateInfo#isCrosstab()}: groups[1..] only -- the first group is pivoted
    *       into column headers and every aggregate's own column is gone.</li>
    * </ul>
    * A column survives only if it is ITSELF (same object) such an output under an unchanged
    * identity, so a re-alias ({@code TOTAL -> T2}), a cleared alias ({@code TOTAL -> qty})
    * and a column dropped by the stale-range-column sweep (in {@code originalAliases} but no
    * longer in the selection) are all at risk, while a full clear of an unaliased aggregate
    * or of a never-aggregated table is not. Hidden columns are never output, before or
    * after, so they are never candidates.
    *
    * @param aggregatesKeepIdentity {@code true} for consumers that only need the NAME to keep
    *                               resolving (a downstream aggregate, condition, expression);
    *                               {@code false} for a join key, which also needs row-level
    *                               identity, so an aggregate output does not keep it
    */
   private static List<ColumnRef> findAggregateOutputLossCandidates(
      TableAssembly table, AggregateInfo newInfo, Map<ColumnRef, String> originalAliases,
      boolean aggregatesKeepIdentity)
   {
      ColumnSelection cols = table.getColumnSelection(false);
      Set<ColumnRef> live = Collections.newSetFromMap(new IdentityHashMap<>());
      List<ColumnRef> columns = new ArrayList<>();

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         if(cols.getAttribute(i) instanceof ColumnRef colRef) {
            live.add(colRef);
            columns.add(colRef);
         }
      }

      if(originalAliases != null) {
         List<ColumnRef> removed = new ArrayList<>();

         for(ColumnRef colRef : originalAliases.keySet()) {
            if(!live.contains(colRef)) {
               removed.add(colRef);
            }
         }

         removed.sort(Comparator.comparing(ColumnRef::getName,
            Comparator.nullsFirst(Comparator.naturalOrder())));
         columns.addAll(removed);
      }

      List<ColumnRef> atRisk = new ArrayList<>();

      for(ColumnRef colRef : columns) {
         if(!colRef.isVisible()) {
            continue;
         }

         if(!live.contains(colRef) ||
            !survivesAggregateOutput(newInfo, colRef, originalAliases, aggregatesKeepIdentity))
         {
            atRisk.add(colRef);
         }
      }

      return atRisk;
   }

   private static boolean survivesAggregateOutput(
      AggregateInfo newInfo, ColumnRef col, Map<ColumnRef, String> originalAliases,
      boolean aggregatesKeepIdentity)
   {
      String preCallIdentity = outputIdentity(col, preCallAlias(col, originalAliases));
      String finalIdentity = outputIdentity(col, col.getAlias());

      if(!Objects.equals(preCallIdentity, finalIdentity)) {
         return false;
      }

      if(newInfo.isEmpty()) {
         return true;
      }

      boolean crosstab = newInfo.isCrosstab();

      for(int i = crosstab ? 1 : 0; i < newInfo.getGroupCount(); i++) {
         if(newInfo.getGroup(i).getDataRef() == col) {
            return true;
         }
      }

      if(aggregatesKeepIdentity && !crosstab) {
         for(int i = 0; i < newInfo.getAggregateCount(); i++) {
            if(newInfo.getAggregate(i).getDataRef() == col) {
               return true;
            }
         }
      }

      return false;
   }

   private static String preCallAlias(ColumnRef col, Map<ColumnRef, String> originalAliases) {
      return originalAliases != null && originalAliases.containsKey(col) ?
         originalAliases.get(col) : col.getAlias();
   }

   private static String outputIdentity(ColumnRef col, String alias) {
      return alias != null ? alias : col.getAttribute();
   }

   /**
    * {@code column} itself when its alias is unchanged since {@code originalAliases} was
    * captured, else a shadow clone carrying the pre-call alias, so a downstream lookup asks
    * about the identity the column HAD (see {@link #getOuterAttributeSnapshotAware}).
    */
   private static ColumnRef withPreCallAlias(ColumnRef column,
                                             Map<ColumnRef, String> originalAliases)
   {
      if(originalAliases == null || !originalAliases.containsKey(column)) {
         return column;
      }

      String originalAlias = originalAliases.get(column);

      if(Objects.equals(originalAlias, column.getAlias())) {
         return column;
      }

      ColumnRef shadow = (ColumnRef) column.clone();
      shadow.setAlias(originalAlias);
      return shadow;
   }

   /**
    * Same as {@link AssetUtil#getOuterAttribute}, except when {@code originalAliases}
    * records an alias for {@code column} that differs from its CURRENT one, the lookup
    * key is built from a shadow clone carrying that ORIGINAL alias instead.
    *
    * <p>Bug #76891 / WBS-084(a): {@code column} may be the SAME live {@link ColumnRef}
    * this very {@code applyAggregateInfo} call already mutated -- a brand-new alias just
    * set by its aggregates loop, or a PRIOR alias {@code clearAggregateAliases} just
    * cleared back to {@code null} -- before this guard ever runs. Building the lookup key
    * from that live alias would ask "does anything depend on the identity this call just
    * created/removed," never the question this guard actually needs answered: "does
    * anything depend on the identity this column had BEFORE this call touched it."</p>
    *
    * <p>{@code originalAliases} only ever holds entries for the ORIGINATING table's own
    * columns, captured once, before any mutation, at the top of {@code
    * applyAggregateInfo} -- a {@code column} from a downstream dependent's own,
    * untouched selection (every recursion hop past the first) is simply absent from the
    * map and falls through to the ordinary, unmodified lookup, which is already correct
    * for it (its own alias reflects its own history, never mutated by this call).</p>
    */
   private static DataRef getOuterAttributeSnapshotAware(
      String table, ColumnRef column, Map<ColumnRef, String> originalAliases)
   {
      if(originalAliases == null || !originalAliases.containsKey(column)) {
         return AssetUtil.getOuterAttribute(table, column);
      }

      String originalAlias = originalAliases.get(column);

      if(Objects.equals(originalAlias, column.getAlias())) {
         return AssetUtil.getOuterAttribute(table, column);
      }

      ColumnRef shadow = (ColumnRef) column.clone();
      shadow.setAlias(originalAlias);
      return AssetUtil.getOuterAttribute(table, shadow);
   }

   /**
    * Walks {@code assembly}'s dependents transitively, mapping {@code ref} through each
    * dependent's rename/mirror outer-attribute chain (same mechanism {@link #allowsDeletion}
    * uses for a plain {@code TableAssembly} dependent) and recording a conflict whenever
    * the mapped column is referenced as an aggregate input, OR a group-by, by that
    * dependent's own {@link AggregateInfo} (Bug #76891 / WBS-086: a dependent that GROUPS
    * BY the changed column needs the column to resolve by a stable identity downstream
    * exactly as much as one that aggregates it).
    * <p>
    * {@code visited} tracks only the assemblies on the CURRENT recursion path (its
    * ancestors), not every assembly ever reached across the whole walk -- an entry is
    * added before recursing into a dependent and removed again once that dependent's
    * whole subtree has been processed. This is enough to stop a genuine cycle (a
    * dependent chain that loops back on one of its own ancestors) without also
    * blocking a diamond: the same downstream assembly reached via two different,
    * non-overlapping incoming edges (e.g. two mirrors of one source table that are
    * both joined back together) must be re-checked on each edge, since each edge maps
    * the column through a different rename chain and only one of them may actually
    * match that assembly's own {@link AggregateInfo}.
    */
   private static void findAggregateInputLossConflicts(
      Worksheet ws, TableAssembly assembly, ColumnRef ref, Set<String> visited,
      LinkedHashMap<String, List<String>> lostByDependent, Map<ColumnRef, String> originalAliases)
   {
      AssemblyRef[] arr = ws.getDependings(assembly.getAssemblyEntry());

      for(AssemblyRef assemblyRef : arr) {
         String assemblyName = assemblyRef.getEntry().getName();

         if(!visited.add(assemblyName)) {
            continue;
         }

         try {
            Assembly tmp = ws.getAssembly(assemblyName);

            if(!(tmp instanceof TableAssembly)) {
               continue;
            }

            TableAssembly dependent = (TableAssembly) tmp;
            DataRef outerRef = getOuterAttributeSnapshotAware(assemblyName, ref, originalAliases);
            ColumnRef mappedRef =
               AssetUtil.getColumnRefFromAttribute(dependent.getColumnSelection(), outerRef);

            if(mappedRef == null) {
               continue;
            }

            AggregateInfo dependentInfo = dependent.getAggregateInfo();

            if(dependentInfo != null && !dependentInfo.isEmpty() &&
               (dependentInfo.getAggregates(mappedRef).length > 0 ||
                dependentInfo.getGroup(mappedRef) != null))
            {
               List<String> lostColumns =
                  lostByDependent.computeIfAbsent(assemblyName, k -> new ArrayList<>());
               String name = mappedRef.getName();

               if(!lostColumns.contains(name)) {
                  lostColumns.add(name);
               }
            }

            findAggregateInputLossConflicts(
               ws, dependent, mappedRef, visited, lostByDependent, originalAliases);
         }
         finally {
            visited.remove(assemblyName);
         }
      }
   }

   /**
    * One entry per downstream assembly (transitively) whose own pre-condition, post-condition,
    * ranking condition, or expression-column script text references a column being removed from
    * {@code table} (Bug #77005 / WBS-093/094). Unlike {@link AggregateInputLossConflict}, which
    * is scoped to {@link AggregateInfo}, this covers the consumer kinds that mechanism does not
    * -- {@code remove_column} has no rewrite target to propagate a removal into the way
    * {@code rename_column} does, so precheck-and-refuse (the same shape {@code
    * set_column_visibility}, Bug #77001 / WBS-088, already uses for the aggregate/group-by axis)
    * is the only safe model for it.
    */
   public record ColumnReferenceLossConflict(String dependentAssemblyName, List<String> references) {}

   /**
    * Finds every downstream assembly (transitively, not just direct dependents) whose own
    * condition/ranking/expression references {@code ref}. See {@link ColumnReferenceLossConflict}.
    */
   public static List<ColumnReferenceLossConflict> findColumnReferenceLossConflicts(
      Worksheet ws, TableAssembly table, ColumnRef ref)
   {
      LinkedHashMap<String, List<String>> refsByDependent = new LinkedHashMap<>();
      findColumnReferenceLossConflicts(ws, table, ref, new HashSet<>(), refsByDependent);

      List<ColumnReferenceLossConflict> conflicts = new ArrayList<>();

      for(Map.Entry<String, List<String>> entry : refsByDependent.entrySet()) {
         conflicts.add(new ColumnReferenceLossConflict(entry.getKey(), entry.getValue()));
      }

      return conflicts;
   }

   /**
    * Wiz-only, {@code AggregateInfo}-scoped variant for {@code
    * WorksheetMutationSupport#applyAggregateInfo} (Bug #78144 / WBS-101): runs the reference
    * walker on every column whose pre-call output identity {@code newInfo} removes (see
    * {@link #findAggregateOutputLossCandidates}), looking each one up downstream under its
    * PRE-CALL alias -- by guard time the call has already cleared or re-set the live alias,
    * and a live-alias lookup would find nothing for exactly the aliased cases.
    */
   public static List<ColumnReferenceLossConflict> findColumnReferenceLossConflicts(
      Worksheet ws, TableAssembly table, AggregateInfo newInfo,
      Map<ColumnRef, String> originalAliases)
   {
      LinkedHashMap<String, List<String>> refsByDependent = new LinkedHashMap<>();

      if(newInfo != null) {
         for(ColumnRef colRef :
            findAggregateOutputLossCandidates(table, newInfo, originalAliases, true))
         {
            findColumnReferenceLossConflicts(ws, table, withPreCallAlias(colRef, originalAliases),
               new HashSet<>(), refsByDependent);
         }
      }

      List<ColumnReferenceLossConflict> conflicts = new ArrayList<>();

      for(Map.Entry<String, List<String>> entry : refsByDependent.entrySet()) {
         conflicts.add(new ColumnReferenceLossConflict(entry.getKey(), entry.getValue()));
      }

      return conflicts;
   }

   private static void findColumnReferenceLossConflicts(
      Worksheet ws, TableAssembly assembly, ColumnRef ref, Set<String> visited,
      LinkedHashMap<String, List<String>> refsByDependent)
   {
      AssemblyRef[] arr = ws.getDependings(assembly.getAssemblyEntry());

      for(AssemblyRef assemblyRef : arr) {
         String assemblyName = assemblyRef.getEntry().getName();

         if(!visited.add(assemblyName)) {
            continue;
         }

         try {
            Assembly tmp = ws.getAssembly(assemblyName);

            if(!(tmp instanceof TableAssembly)) {
               continue;
            }

            TableAssembly dependent = (TableAssembly) tmp;
            List<String> found = new ArrayList<>();

            // Bug #77005 review round 2: a Table.Column dot-notation cross-table expression
            // reference (e.g. "L.amount + R.price" on a join table) is independent of whether
            // the column is part of dependent's OWN column selection -- StyleBI's scripting
            // engine lets a script name ANOTHER worksheet table directly, bypassing the
            // mapped-local-column machinery below entirely (that machinery only ever answers
            // "what does DEPENDENT itself call this column", which a bare cross-table script
            // reference never needs). Checked unconditionally, using assembly's own current
            // name/identity at this recursion level (not the possibly-null mappedRef computed
            // below), matching how RenameColumnController's own getExpressionDependeds-gated
            // detection is scoped one recursion level at a time.
            collectDotNotationExpressionReferences(assembly.getName(), ref, dependent, found);

            DataRef outerRef = AssetUtil.getOuterAttribute(assemblyName, ref);
            ColumnRef mappedRef =
               AssetUtil.getColumnRefFromAttribute(dependent.getColumnSelection(), outerRef);

            if(mappedRef != null) {
               collectConditionReferences(
                  dependent.getPreConditionList(), mappedRef, "pre-condition", found);
               collectConditionReferences(
                  dependent.getPostConditionList(), mappedRef, "post-condition", found);
               collectConditionReferences(
                  dependent.getRankingConditionList(), mappedRef, "ranking condition", found);
               collectExpressionReferences(dependent, mappedRef, found);
            }

            if(!found.isEmpty()) {
               List<String> existing =
                  refsByDependent.computeIfAbsent(assemblyName, k -> new ArrayList<>());

               for(String reference : found) {
                  if(!existing.contains(reference)) {
                     existing.add(reference);
                  }
               }
            }

            if(mappedRef != null) {
               findColumnReferenceLossConflicts(ws, dependent, mappedRef, visited, refsByDependent);
            }
         }
         finally {
            visited.remove(assemblyName);
         }
      }
   }

   /**
    * Appends {@code kind} to {@code references} if any item in {@code wrapper} references
    * {@code ref} -- either directly as the condition's own attribute, or (Bug #77005 review round
    * 2) as a {@link RankingCondition}'s own separate "rank by" field. Both are checked with the
    * identical unwrap-and-match logic {@code RenameColumnController.renameMirrorConditionList}/
    * {@code renameConditionValue} use: an {@link AggregateRef}-wrapped attribute (a HAVING/ranking
    * condition on an aggregate table), or a bare {@link GroupRef} (a HAVING/ranking condition on a
    * GROUP BY column -- {@code WorksheetMutationSupport#resolveAggregateOrGroupField} returns the
    * {@code GroupRef} itself for this case), including its nested {@link DateRangeRef} case for a
    * date-grouped column.
    */
   private static void collectConditionReferences(
      ConditionListWrapper wrapper, ColumnRef ref, String kind, List<String> references)
   {
      if(!(wrapper instanceof ConditionList)) {
         return;
      }

      ConditionList list = (ConditionList) wrapper;

      for(int i = 0; i < list.getConditionSize(); i += 2) {
         ConditionItem item = list.getConditionItem(i);

         if(item == null) {
            continue;
         }

         if(referencesColumn(item.getAttribute(), ref)) {
            references.add(kind);
            continue;
         }

         if(item.getXCondition() instanceof RankingCondition rankingCondition &&
            referencesColumn(rankingCondition.getDataRef(), ref))
         {
            references.add(kind);
         }
      }
   }

   /**
    * True if {@code candidate} references {@code ref} -- unwrapping an {@link AggregateRef} or a
    * plain {@link GroupRef} the same way {@code RenameColumnController.renameMirrorConditionList}/
    * {@code renameConditionValue} do (including the nested {@link DateRangeRef} case for a
    * date-grouped column), rather than requiring {@code candidate} to be a bare {@link ColumnRef}.
    */
   private static boolean referencesColumn(DataRef candidate, ColumnRef ref) {
      if(candidate instanceof AggregateRef aggregateRef) {
         candidate = aggregateRef.getDataRef();
      }

      if(candidate instanceof ColumnRef && candidate.equals(ref)) {
         return true;
      }

      if(candidate instanceof GroupRef groupRef) {
         DataRef groupData = groupRef.getDataRef();

         if(groupData instanceof ColumnRef groupCol &&
            groupCol.getDataRef() instanceof DateRangeRef dateRange)
         {
            DataRef dateBase = dateRange.getDataRef();
            return dateBase instanceof AttributeRef &&
               Tool.equals(dateBase.getAttribute(), ref.getAttribute());
         }

         return groupRef.equals(ref);
      }

      return false;
   }

   /**
    * Appends one entry per {@code dependent} expression column whose script text references
    * {@code table} (named at the CURRENT recursion level, not necessarily the original table the
    * removal targets) dot-notation style ({@code Table.Column}) -- the classic cross-table script
    * reference {@link RenameColumnController#renameTableColumn} already detects via {@link
    * AbstractTableAssembly#getExpressionDependeds}/{@link ScriptIterator} (Bug #77005 review round
    * 2). Gated the identical way that detection is: {@code dependent} must itself report {@code
    * table} as an expression dependency at all before its expressions are scanned token-by-token.
    */
   private static void collectDotNotationExpressionReferences(
      String table, ColumnRef ref, TableAssembly dependent, List<String> references)
   {
      if(!(dependent instanceof AbstractTableAssembly)) {
         return;
      }

      Set<AssemblyRef> expressionDeps = new HashSet<>();
      ((AbstractTableAssembly) dependent).getExpressionDependeds(expressionDeps);
      boolean dependsOnTable = expressionDeps.stream()
         .map(AssemblyRef::getEntry)
         .anyMatch(entry -> Tool.equals(entry.getName(), table));

      if(!dependsOnTable) {
         return;
      }

      // getName(), matching RenameColumnController#renameTableColumnExpression's own dot-notation
      // token match (ocolumn.getName()) exactly -- a Table.Column script reference is a bare
      // identifier, addressed by display name (alias if set, else attribute), not the bracket
      // form's raw-attribute convention collectExpressionReferences uses.
      String columnName = ref.getName();

      if(columnName == null) {
         return;
      }

      ColumnSelection columns = dependent.getColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef column = columns.getAttribute(i);

         if(!(column instanceof ColumnRef)) {
            continue;
         }

         DataRef inner = ((ColumnRef) column).getDataRef();

         if(!(inner instanceof ExpressionRef)) {
            continue;
         }

         String expr = ((ExpressionRef) inner).getExpression();

         if(referencesTableColumnDotNotation(expr, table, columnName)) {
            references.add("expression column '" + ((ColumnRef) column).getName() + "'");
         }
      }
   }

   /**
    * True if {@code expr} contains a {@code table.columnName} dot-notation token sequence, using
    * the identical {@link ScriptIterator} token-matching rule {@code RenameColumnController
    * .renameTableColumnExpression}'s listener applies when actually rewriting one -- read-only
    * here, since this only needs to detect presence, not rewrite.
    */
   private static boolean referencesTableColumnDotNotation(
      String expr, String table, String columnName)
   {
      if(expr == null || expr.isEmpty()) {
         return false;
      }

      boolean[] found = { false };
      ScriptIterator.ScriptListener listener = (token, pref, cref) -> {
         if(pref != null && Tool.equals(pref.val, table) && token.isRef() &&
            token.val.equals(columnName) && (cref == null || !"[".equals(cref.val)))
         {
            found[0] = true;
         }
      };

      ScriptIterator iterator = new ScriptIterator(expr);
      iterator.addScriptListener(listener);
      iterator.iterate();
      return found[0];
   }

   /**
    * Appends one entry per {@code dependent} expression column whose script text references
    * {@code ref} under {@code dependent}'s own local column identity -- {@code field['<name>']},
    * with no cross-table qualifier at all, the pattern wiz's own {@code add_expression_column}
    * generates (Bug #77005 / WBS-094). This is a plain substring scan, not the {@link
    * AbstractTableAssembly#getExpressionDependeds}/{@code ScriptIterator} machinery
    * {@code RenameColumnController} uses for a cross-assembly {@code Table.Column} reference (see
    * {@link #collectDotNotationExpressionReferences} for that case, added Bug #77005 review round
    * 2) -- that machinery answers a different question ("does this script reference ANOTHER
    * assembly by name") that a same-table local field access never triggers, so it would never
    * flag this case at all.
    */
   private static void collectExpressionReferences(
      TableAssembly dependent, ColumnRef ref, List<String> references)
   {
      // getAttribute(), not getName() -- field['amount'] addresses a column by its BARE
      // attribute; getName() falls back to an entity-qualified "Table.amount" once no alias
      // is set (AbstractDataRef#getName0), which never appears literally in that bracket form.
      String name = ref.getAttribute();

      if(name == null) {
         return;
      }

      ColumnSelection columns = dependent.getColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef column = columns.getAttribute(i);

         if(!(column instanceof ColumnRef)) {
            continue;
         }

         DataRef inner = ((ColumnRef) column).getDataRef();

         if(!(inner instanceof ExpressionRef)) {
            continue;
         }

         String expr = ((ExpressionRef) inner).getExpression();

         if(expr != null && expr.contains("['" + name + "']")) {
            references.add("expression column '" + ((ColumnRef) column).getName() + "'");
         }
      }
   }

   protected boolean isBeDepend(ColumnSelection columns, DataRef target) {
      boolean depend = false;

      for(int j = 0; j < columns.getAttributeCount(); j++) {
         ColumnRef col = (ColumnRef) columns.getAttribute(j);
         DataRef baseRef = AssetUtil.getBaseAttribute(col);

         if(baseRef instanceof RangeRef) {
            RangeRef dependRef = (RangeRef) baseRef;

            if(dependRef != null && target.equals(dependRef.getDataRef())) {
               depend = true;
               break;
            }
         }
      }

      return depend;
   }

   protected WSInsertColumnsEventValidator validateInsertColumns0(
      RuntimeWorksheet rws,
      WSInsertColumnsEvent event,
      Principal principal,
      ColumnRef replaceRef) throws Exception
   {
      WSInsertColumnsEventValidator.Builder builder = WSInsertColumnsEventValidator.builder();

      Worksheet ws = rws.getWorksheet();
      String name = event.name();
      int index = event.index();
      TableAssembly assembly = (TableAssembly) ws.getAssembly(name);

      if(assembly == null) {
         return null;
      }

      ColumnSelection columns =
         (ColumnSelection) assembly.getColumnSelection().clone(true);
      BoundTableAssembly boundTable;
      SourceInfo source;

      if(assembly instanceof BoundTableAssembly) {
         boundTable = (BoundTableAssembly) assembly;
         BoundTableAssembly otable = (BoundTableAssembly) boundTable.clone();
         source = boundTable.getSourceInfo();
         XLogicalModel lmodel = XUtil.getLogicModel(source, rws.getUser());

         AssetQuerySandbox box = rws.getAssetQuerySandbox();
         int mode = AssetEventUtil.getMode(assembly);
         XTable data = box.getTableLens(name, mode);

         if(index != 0) {
            ColumnRef leftNeighbor = AssetUtil.findColumn(data, index - 1, columns);
            index = columns.indexOfAttribute(leftNeighbor) + 1;
         }
         else if(data.getColCount() > 0) {
            ColumnRef rightNeighbor = AssetUtil.findColumn(data, 0, columns);
            index = columns.indexOfAttribute(rightNeighbor);
         }
         else {
            index = 0;
         }

         if(replaceRef != null) {
            columns.removeAttribute(replaceRef);
         }

         for(AssetEntry entry : event.entries()) {
            String esrc = entry.getProperty("source");
            String entity = entry.getProperty("entity");
            String attr = entry.getProperty("attribute");
            attr = AssetUtil.trimEntity(attr, entity);

            if(source != null && source.getSource() != null &&
               source.getSource().equals(esrc) && attr != null)
            {
               AttributeRef attributeRef = new AttributeRef(entity, attr);

               if(entry.getProperty("caption") != null) {
                  attributeRef.setCaption(entry.getProperty("caption"));
               }

               if(entry.getProperty("refType") != null) {
                  attributeRef.setRefType(
                     Integer.parseInt(entry.getProperty("refType")));
               }

               if(index < 0 || index > columns.getAttributeCount()) {
                  return null;
               }

               ColumnRef ref = new ColumnRef(attributeRef);
               ref.setDataType(entry.getProperty("dtype"));
               int oldCount = columns.getAttributeCount();
               columns.addAttribute(index, ref);

               // Column was successfully added.
               if(oldCount < columns.getAttributeCount()) {
                  index++;
               }

               // if logic model, keep ref type
               if(lmodel != null) {
                  XEntity xentity = lmodel.getEntity(entity);
                  XAttribute xattr = xentity.getAttribute(attr);
                  attributeRef.setRefType(xattr.getRefType());
                  attributeRef.setDefaultFormula(xattr.getDefaultFormula());
                  ref.setDescription(xattr.getDescription());
               }
            }
         }

         if(otable != null) {
            assembly.setColumnSelection(columns);
            WSModelTrapContext context =
               new WSModelTrapContext(boundTable, principal, dataSourceRegistry);

            if(context.isCheckTrap() &&
               context.checkTrap(otable, boundTable).showWarning())
            {
               String msg = context.getTrapCondition();
               builder.trap(msg);
            }

            assembly.setColumnSelection(otable.getColumnSelection());
         }
      }

      return builder.build();
   }

   protected void insertColumns0(
      String name, int index, AssetEntry[] entries,
      ColumnSelection columns, RuntimeWorksheet rws,  TableAssembly assembly) throws Exception
   {
      Worksheet ws = rws.getWorksheet();

      BoundTableAssembly boundTable;
      SourceInfo source;

      boundTable = (BoundTableAssembly) assembly;
      source = boundTable.getSourceInfo();
      XLogicalModel lmodel = XUtil.getLogicModel(source, rws.getUser());

      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      int mode = AssetEventUtil.getMode(assembly);
      XTable data = box.getTableLens(name, mode);

      if(index != 0 && columns.getAttributeCount() > 0) {
         ColumnRef leftNeighbor = AssetUtil.findColumn(data, index - 1, columns);
         index = columns.indexOfAttribute(leftNeighbor) + 1;
      }
      else if(data.getColCount() > 0 && columns.getAttributeCount() > 0) {
         ColumnRef rightNeighbor = AssetUtil.findColumn(data, 0, columns);
         index = columns.indexOfAttribute(rightNeighbor);
      }
      else {
         index = 0;
      }

      for(AssetEntry entry : entries) {
         String esrc = entry.getProperty("source");
         String entity = entry.getProperty("entity");
         String attr = entry.getProperty("attribute");
         attr = AssetUtil.trimEntity(attr, entity);

         if(source != null && source.getSource() != null &&
            source.getSource().equals(esrc) && attr != null)
         {
            AttributeRef attributeRef = new AttributeRef(entity, attr);

            if(entry.getProperty("caption") != null) {
               attributeRef.setCaption(entry.getProperty("caption"));
            }

            if(entry.getProperty("refType") != null) {
               attributeRef.setRefType(
                  Integer.parseInt(entry.getProperty("refType")));
            }

            if(index < 0 || index > columns.getAttributeCount()) {
               return;
            }

            ColumnRef ref = new ColumnRef(attributeRef);
            ref.setDataType(entry.getProperty("dtype"));
            int oldCount = columns.getAttributeCount();
            columns.addAttribute(index, ref);

            // Column was successfully added.
            if(oldCount < columns.getAttributeCount()) {
               index++;
            }

            // if logic model, keep ref type
            if(lmodel != null) {
               XEntity xentity = lmodel.getEntity(entity);
               XAttribute xattr = xentity.getAttribute(attr);
               attributeRef.setRefType(xattr.getRefType());
               attributeRef.setDefaultFormula(xattr.getDefaultFormula());
               ref.setDescription(xattr.getDescription());
            }
         }
      }

      assembly.setColumnSelection(columns);
   }

   protected void setColumnIndex0(String tname, int newIndex,
                                  RuntimeWorksheet rws, int[] oldIndices, Boolean replaceColumn) throws Exception
   {
      Worksheet ws = rws.getWorksheet();

      if(oldIndices.length == 0) {
         return;
      }

      Arrays.sort(oldIndices);
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);

      if(table == null) {
         return;
      }

//      command.put("linkUri", getLinkURI()); TODO
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      int mode = WorksheetEventUtil.getMode(table);
      XTable data = box.getTableLens(tname, mode);
      boolean lastCol = false;
      boolean isCrosstab = table.getAggregateInfo().isCrosstab();

      if(newIndex == data.getColCount()) {
         lastCol = true;
         newIndex--;
      }

      if(isCrosstab && table.isAggregate() && newIndex == table.getAggregateInfo().getGroupCount() - 1) {
         newIndex--;
      }

      if(newIndex < 0 || newIndex >= data.getColCount()) {
         return;
      }

      ColumnSelection columns = table.getColumnSelection();
      ColumnSelection columns2 = table.getColumnSelection(true);
      ColumnRef to = AssetUtil.findColumn(data, newIndex, columns);
      ArrayList<ColumnRef> columnsInsertedLeftOfNewIndex = new ArrayList<>();
      ArrayList<ColumnRef> columnsInsertedRightOfNewIndex = new ArrayList<>();

      if(to == null) {
         return;
      }

      boolean oldIndexIsNewIndex = false;

      for(int oldIndex : oldIndices) {
         ColumnRef from = AssetUtil.findColumn(data, oldIndex, columns);

         if(from == null) {
            return;
         }

         if(oldIndex == newIndex) {
            oldIndexIsNewIndex = true;
         }
         else if(oldIndexIsNewIndex && oldIndex > newIndex) {
            columnsInsertedRightOfNewIndex.add(from);
         }
         else {
            columnsInsertedLeftOfNewIndex.add(from);
         }
      }

      for(ColumnRef from : columnsInsertedLeftOfNewIndex) {
         columns.removeAttribute(from);
         newIndex = columns.indexOfAttribute(to);

         if(lastCol) {
            columns.addAttribute(from);
         }
         else {
            columns.addAttribute(newIndex, from);
         }

         if(columns2.containsAttribute(from) && columns2.containsAttribute(to)) {
            int findex = columns2.indexOfAttribute(from);
            int tindex = columns2.indexOfAttribute(to);

            from = (ColumnRef) columns2.getAttribute(findex);
            columns2.removeAttribute(from);

            if(lastCol) {
               columns2.addAttribute(from);
            }
            else {
               columns2.addAttribute(tindex, from);
            }
         }
      }

      for(ColumnRef from : columnsInsertedRightOfNewIndex) {
         columns.removeAttribute(from);
         newIndex = columns.indexOfAttribute(to) + 1;

         if(lastCol) {
            columns.addAttribute(from);
         }
         else {
            columns.addAttribute(newIndex, from);
         }

         if(columns2.containsAttribute(from) && columns2.containsAttribute(to)) {
            int findex = columns2.indexOfAttribute(from);
            int tindex = columns2.indexOfAttribute(to) + 1;

            from = (ColumnRef) columns2.getAttribute(findex);
            columns2.removeAttribute(from);

            if(lastCol) {
               columns2.addAttribute(from);
            }
            else {
               columns2.addAttribute(tindex, from);
            }
         }
      }

      //if it is replace column, we should remove column 'to'
      if(replaceColumn) {
         columns.removeAttribute(to);
         columns2.removeAttribute(to);
      }

      table.setColumnSelection(columns2, true);
      table.setColumnSelection(columns, false);
   }

   private final WorksheetService wsEngine;
   private final DataSourceRegistry dataSourceRegistry;

   protected static final int ROW_LIMIT = 10000;
   protected static final int COL_LIMIT = 1000;
   protected static final int BLOCK = 100; // Table row counts per loading process
}
