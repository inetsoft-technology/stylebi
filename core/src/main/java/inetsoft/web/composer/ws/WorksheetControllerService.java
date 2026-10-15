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
import inetsoft.uql.Condition;
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
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

   @Autowired
   public void setQueryManagerService(QueryManagerService queryManagerService) {
      this.queryManagerService = queryManagerService;
   }

   /**
    * Checks the table names that a worksheet composer request supplies, before they are
    * looked up in or stored into the worksheet. A cube table name
    * (<tt>___inetsoft_cube_&lt;data source&gt;/&lt;cube&gt;</tt>) is resolved by the worksheet
    * straight from the data source, ahead of its own assemblies and without a permission
    * check. The composer never names its own tables this way, so such a name always requires
    * READ on its data source and cube. Any other name is not checked (Bug #77462).
    *
    * @param principal  the current user.
    * @param tableNames the client-supplied table names, any of which may be null.
    *
    * @throws java.lang.SecurityException if a permission is not granted.
    */
   protected void checkCubeTableReadPermission(Principal principal, String... tableNames) {
      if(tableNames == null) {
         return;
      }

      for(String tableName : tableNames) {
         if(tableName != null && tableName.startsWith(Assembly.CUBE_VS)) {
            if(queryManagerService == null) {
               throw new java.lang.SecurityException(
                  "Unauthorized access to cube table \"" + tableName + "\"");
            }

            queryManagerService.checkCubeTableReadPermission(tableName, principal);
         }
      }
   }

   /**
    * Refuses a cube table name (<tt>___inetsoft_cube_&lt;data source&gt;/&lt;cube&gt;</tt>) as the
    * new name of a worksheet table. The worksheet would resolve that name to the cube instead
    * of the table, and the worksheet could not be saved (Bug #77462).
    *
    * @throws java.lang.SecurityException if the name is a cube table name.
    */
   protected static void checkNotCubeTableName(String name) {
      if(name != null && name.startsWith(Assembly.CUBE_VS)) {
         throw new java.lang.SecurityException(
            "A worksheet table cannot be named as a cube table: \"" + name + "\"");
      }
   }

   /**
    * Refuses to save a worksheet that references a cube table name
    * (<tt>___inetsoft_cube_&lt;data source&gt;/&lt;cube&gt;</tt>), for example as the base of a
    * mirror, a member of a join, a condition subquery or a variable's table, or that names one
    * of its own tables that way. The composer never creates such a reference itself, and a
    * saved one would be resolved later by the viewsheet runtime without a permission check
    * (Bug #77462).
    *
    * @throws java.lang.SecurityException if the worksheet references a cube table name.
    */
   protected static void checkNoCubeTableReference(Worksheet ws) {
      if(ws == null) {
         return;
      }

      for(Assembly assembly : ws.getAssemblies()) {
         if(assembly.getName().startsWith(Assembly.CUBE_VS)) {
            throw new java.lang.SecurityException(
               "Worksheet table \"" + assembly.getName() + "\" is named as a cube table");
         }

         Set<AssemblyRef> depends = new HashSet<>();
         assembly.getDependeds(depends);

         for(AssemblyRef ref : depends) {
            AssemblyEntry entry = ref.getEntry();
            String name = entry == null ? null : entry.getName();

            if(name != null && name.startsWith(Assembly.CUBE_VS)) {
               throw new java.lang.SecurityException(
                  "Worksheet table \"" + assembly.getName() +
                  "\" references cube table \"" + name + "\"");
            }
         }
      }
   }

   /**
    * A join whose key pair stops being type-compatible when {@code table}'s key column is retyped.
    *
    * @param join       the join (composite table) name
    * @param key        the edited column's name on the table being edited
    * @param otherTable the other member the key is joined against
    * @param otherKey   the key name on {@code otherTable}
    * @param otherType  {@code otherKey}'s output type
    */
   public record JoinKeyTypeConflict(String join, String key, String otherTable, String otherKey,
                                     String otherType)
   {
   }

   /**
    * Bug #78258 (WBS-110): finds every join keyed on {@code column} of {@code table} whose key
    * pair is type-compatible with {@code oldType} but not with {@code newType}. Only that
    * transition is reported, so a join that is already mismatched never blocks its own repair.
    *
    * <p>Keys are matched by resolving each operator attribute through the same resolver the
    * join-creation guard uses ({@link WorksheetMutationSupport#resolveJoinKey}), not by
    * {@code CompositeTableAssembly#isColumnUsed}, which compares the underlying attribute name and
    * misses alias-keyed and entity-qualified keys. The walk follows every table built on
    * {@code table} (mirrors, joins of joins, ...) with a visited set.</p>
    */
   public static List<JoinKeyTypeConflict> findJoinKeyTypeConflicts(
      Worksheet ws, TableAssembly table, String column, String oldType, String newType)
   {
      List<JoinKeyTypeConflict> conflicts = new ArrayList<>();

      if(ws == null || table == null || column == null || oldType == null || newType == null ||
         oldType.equals(newType))
      {
         return conflicts;
      }

      collectJoinKeyTypeConflicts(
         ws, table, column, oldType, newType, new HashSet<>(), conflicts);
      return conflicts;
   }

   private static void collectJoinKeyTypeConflicts(
      Worksheet ws, TableAssembly table, String column, String oldType, String newType,
      Set<String> visited, List<JoinKeyTypeConflict> conflicts)
   {
      if(!visited.add(table.getName())) {
         return;
      }

      DataRef edited = WorksheetMutationSupport.resolveJoinKey(table, column);

      if(edited == null) {
         return;
      }

      for(AssemblyRef assemblyRef : ws.getDependings(table.getAssemblyEntry())) {
         Assembly dependent = ws.getAssembly(assemblyRef.getEntry().getName());

         if(!(dependent instanceof TableAssembly dtable)) {
            continue;
         }

         if(dtable instanceof CompositeTableAssembly composite) {
            for(String member : composite.getTableNames()) {
               if(member.equals(table.getName()) ||
                  !(ws.getAssembly(member) instanceof TableAssembly other))
               {
                  continue;
               }

               collectOperatorConflicts(composite, composite.getOperator(table.getName(), member),
                                        true, table, edited, other, oldType, newType, conflicts);
               collectOperatorConflicts(composite, composite.getOperator(member, table.getName()),
                                        false, table, edited, other, oldType, newType, conflicts);
            }
         }

         if(!dtable.isAggregate()) {
            DataRef outer = AssetUtil.getOuterAttribute(table.getName(), edited);
            ColumnRef mapped =
               AssetUtil.getColumnRefFromAttribute(dtable.getColumnSelection(), outer);

            if(mapped != null) {
               collectJoinKeyTypeConflicts(
                  ws, dtable, mapped.getName(), oldType, newType, visited, conflicts);
            }
         }
      }
   }

   private static void collectOperatorConflicts(
      CompositeTableAssembly composite, TableAssemblyOperator operator, boolean tableIsLeft,
      TableAssembly table, DataRef edited, TableAssembly other, String oldType, String newType,
      List<JoinKeyTypeConflict> conflicts)
   {
      if(operator == null) {
         return;
      }

      for(int i = 0; i < operator.getOperatorCount(); i++) {
         TableAssemblyOperator.Operator op = operator.getOperator(i);
         DataRef mine = resolveOperatorKey(
            table, tableIsLeft ? op.getLeftAttribute() : op.getRightAttribute());
         DataRef theirs = resolveOperatorKey(
            other, tableIsLeft ? op.getRightAttribute() : op.getLeftAttribute());

         if(mine == null || theirs == null || !mine.getName().equals(edited.getName())) {
            continue;
         }

         String otherType = theirs.getDataType();

         if(AssetUtil.isMergeable(oldType, otherType) &&
            !AssetUtil.isMergeable(newType, otherType))
         {
            JoinKeyTypeConflict conflict = new JoinKeyTypeConflict(
               composite.getName(), edited.getName(), other.getName(), theirs.getName(),
               otherType);

            if(!conflicts.contains(conflict)) {
               conflicts.add(conflict);
            }
         }
      }
   }

   private static DataRef resolveOperatorKey(TableAssembly t, DataRef attr) {
      if(attr == null) {
         return null;
      }

      DataRef ref = WorksheetMutationSupport.resolveJoinKey(t, attr.getName());

      if(ref == null && attr.getAttribute() != null &&
         !attr.getAttribute().equals(attr.getName()))
      {
         ref = WorksheetMutationSupport.resolveJoinKey(t, attr.getAttribute());
      }

      return ref;
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
      return findAggregateIdentityLossConflict(ws, table, newInfo, originalAliases, null);
   }

   /**
    * As the 4-arg overload, and additionally (Bug #78595) lets a join key that is ALREADY an
    * aggregate output of {@code table} stay one. {@code oldAggregateOutputs} holds
    * {@link #aggregateOutputKey} of every aggregate of the pre-call, non-crosstab
    * {@link AggregateInfo}; a join key that the new info still aggregates under that same
    * (source attribute, output identity) is not "row-level -> aggregated", it is unchanged.
    * {@code null} keeps the original rule that an aggregate never keeps a join key.
    */
   public static ColumnRef findAggregateIdentityLossConflict(
      Worksheet ws, TableAssembly table, AggregateInfo newInfo,
      Map<ColumnRef, String> originalAliases, Set<String> oldAggregateOutputs)
   {
      if(newInfo == null) {
         return null;
      }

      for(ColumnRef colRef :
         findAggregateOutputLossCandidates(table, newInfo, originalAliases, false,
                                           oldAggregateOutputs))
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
         findAggregateOutputLossCandidates(table, newInfo, originalAliases, true, null))
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
    * A column survives only if it (or an equal ref) is such an output under an unchanged
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
      boolean aggregatesKeepIdentity, Set<String> oldAggregateOutputs)
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
            !survivesAggregateOutput(newInfo, colRef, originalAliases, aggregatesKeepIdentity,
                                     oldAggregateOutputs))
         {
            atRisk.add(colRef);
         }
      }

      return atRisk;
   }

   private static boolean survivesAggregateOutput(
      AggregateInfo newInfo, ColumnRef col, Map<ColumnRef, String> originalAliases,
      boolean aggregatesKeepIdentity, Set<String> oldAggregateOutputs)
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
         if(isSameOutputColumn(newInfo.getGroup(i).getDataRef(), col)) {
            return true;
         }
      }

      if(aggregatesKeepIdentity && !crosstab) {
         for(int i = 0; i < newInfo.getAggregateCount(); i++) {
            if(isSameOutputColumn(newInfo.getAggregate(i).getDataRef(), col)) {
               return true;
            }
         }
      }

      // Bug #78595: a join key that is already an aggregate output stays one when this call
      // still aggregates the same source column under the same output identity. Matching is
      // by key, not by ref equality: the old refs' aliases were cleared before this runs.
      if(!aggregatesKeepIdentity && !crosstab && oldAggregateOutputs != null &&
         oldAggregateOutputs.contains(aggregateOutputKey(col, finalIdentity)))
      {
         for(int i = 0; i < newInfo.getAggregateCount(); i++) {
            if(isSameOutputColumn(newInfo.getAggregate(i).getDataRef(), col)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Identity of one aggregate output for {@code oldAggregateOutputs}: its source attribute
    * plus its output identity (alias, else attribute). Bug #78595.
    */
   public static String aggregateOutputKey(ColumnRef col, String alias) {
      return col.getAttribute() + "\0" +
         (alias == null || alias.isEmpty() ? col.getAttribute() : alias);
   }

   /**
    * True if {@code output} is {@code col} itself, or an equal ref ({@code ColumnRef} equality is
    * name based). Bug #78144 review r1 (B-1): a re-sent date-level group builds a NEW
    * {@code ColumnRef(DateRangeRef)}, which the exclusive {@code ColumnSelection#addAttribute}
    * does not add because the live selection already holds an equal one -- so the group and the
    * live column are different objects for the same, unchanged output. A changed output NAME
    * (re-alias, a cleared alias such as WBS-098's {@code TOTAL -> qty}) is still caught by the
    * caller's pre-call == final identity check, which runs first.
    */
   private static boolean isSameOutputColumn(DataRef output, ColumnRef col) {
      return output == col || (output instanceof ColumnRef && output.equals(col));
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
            findAggregateOutputLossCandidates(table, newInfo, originalAliases, true, null))
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
               collectTableStateReferences(dependent, mappedRef, found);
               collectExpressionReferences(dependent, mappedRef, found);
               collectRangeReferences(dependent, mappedRef, found);
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
    * The references {@code table}'s OWN state holds to its own column {@code ref} -- every
    * condition list (pre, post, ranking, the four MV lists; attribute, ranking field, and value
    * slots) and its sort -- for {@code remove_column} on that same table (Bug #78234 / WBS-108).
    * Removing the column makes the post-mutation refresh drop those conditions and the sort
    * silently, and leaves the MV lists stale.
    *
    * <p>Deliberately NOT part of the downstream walker {@link #findColumnReferenceLossConflicts}:
    * that walker is shared with hiding a column ({@code set_column_visibility}) and the aggregate
    * guard, where the column stays in the table's own private selection, so its own conditions
    * and sort survive and must not cause a refusal.</p>
    */
   public static List<String> findOwnColumnReferences(TableAssembly table, ColumnRef ref) {
      List<String> found = new ArrayList<>();

      if(table != null && ref != null) {
         collectTableStateReferences(table, ref, found);
      }

      return found;
   }

   /**
    * The {@code table}'s OWN expression and range columns that read its own column {@code ref}
    * (Bug #78527): a replace-import keeps those derived columns, so dropping an input one of them
    * reads must be refused. {@link #findOwnColumnReferences} covers only conditions and sort.
    */
   public static List<String> findOwnDerivedColumnReferences(TableAssembly table, ColumnRef ref) {
      List<String> found = new ArrayList<>();

      if(table != null && ref != null) {
         collectExpressionReferences(table, ref, found);
         collectRangeReferences(table, ref, found);
      }

      return found;
   }

   /**
    * Condition lists and sort of {@code table} that reference {@code ref}. Shared by the
    * downstream walker and {@link #findOwnColumnReferences}.
    */
   private static void collectTableStateReferences(
      TableAssembly table, ColumnRef ref, List<String> found)
   {
      collectConditionReferences(table.getPreConditionList(), ref, "pre-condition", found);
      collectConditionReferences(table.getPostConditionList(), ref, "post-condition", found);
      collectConditionReferences(
         table.getRankingConditionList(), ref, "ranking condition", found);
      // Bug #78234 / WBS-107: the MV incremental-refresh condition lists
      collectConditionReferences(
         table.getMVUpdatePreConditionList(), ref, "MV update pre-condition", found);
      collectConditionReferences(
         table.getMVUpdatePostConditionList(), ref, "MV update post-condition", found);
      collectConditionReferences(
         table.getMVDeletePreConditionList(), ref, "MV delete pre-condition", found);
      collectConditionReferences(
         table.getMVDeletePostConditionList(), ref, "MV delete post-condition", found);
      collectSortReferences(table.getSortInfo(), ref, found);
   }

   /**
    * Bug #78234: a sort on the column is dropped silently by the post-mutation refresh once the
    * column is gone.
    */
   private static void collectSortReferences(SortInfo sortInfo, ColumnRef ref,
                                             List<String> references)
   {
      if(sortInfo == null) {
         return;
      }

      for(SortRef sort : sortInfo.getSorts()) {
         if(sort != null && referencesColumn(sort.getDataRef(), ref)) {
            references.add("sort");
            return;
         }
      }
   }

   /**
    * Bug #78234 / WBS-105: a range column (numeric or date) built on the column. Checked on the
    * range's own base ref rather than on its generated expression text.
    */
   private static void collectRangeReferences(
      TableAssembly dependent, ColumnRef ref, List<String> references)
   {
      ColumnSelection columns = dependent.getColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef column = columns.getAttribute(i);

         if(!(column instanceof ColumnRef) ||
            !(((ColumnRef) column).getDataRef() instanceof RangeRef range))
         {
            continue;
         }

         DataRef base = range.getDataRef();

         if(base != null && Tool.equals(base.getEntity(), ref.getEntity()) &&
            Tool.equals(base.getAttribute(), ref.getAttribute()))
         {
            references.add("range column '" + ((ColumnRef) column).getName() + "'");
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
            continue;
         }

         // Bug #78234 / WBS-106: a condition whose VALUE is another column (Budget > [field
         // Amount]) holds that column as a DataRef in its value slot.
         if(item.getXCondition() instanceof Condition condition) {
            for(int v = 0; v < condition.getValueCount(); v++) {
               if(condition.getValue(v) instanceof DataRef valueRef &&
                  referencesColumn(valueRef, ref))
               {
                  references.add(kind + " value");
                  break;
               }
            }
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
    * {@code ref} under {@code dependent}'s own local column identity -- {@code field['x']},
    * {@code field["x"]}, {@code field.x}, or the qualified {@code field['T.x']} (Bug #77005 /
    * WBS-094; Bug #78234 / WBS-104/105). Uses {@link FieldReferenceMatcher}, the same token
    * matcher the rename rewrite uses, so remove and rename agree on what counts as a reference.
    * A cross-assembly {@code Table.Column} reference is {@link
    * #collectDotNotationExpressionReferences}'s job. Range columns are skipped here: their
    * expression is generated from their base ref, which {@link #collectRangeReferences} checks.
    */
   private static void collectExpressionReferences(
      TableAssembly dependent, ColumnRef ref, List<String> references)
   {
      Set<String> names = fieldNames(dependent, ref);

      if(names.isEmpty()) {
         return;
      }

      ColumnSelection columns = dependent.getColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef column = columns.getAttribute(i);

         if(!(column instanceof ColumnRef)) {
            continue;
         }

         DataRef inner = ((ColumnRef) column).getDataRef();

         if(!(inner instanceof ExpressionRef) || inner instanceof RangeRef) {
            continue;
         }

         String expr = ((ExpressionRef) inner).getExpression();

         if(FieldReferenceMatcher.references(expr, names)) {
            references.add("expression column '" + ((ColumnRef) column).getName() + "'");
         }
      }
   }

   /**
    * The names a {@code field[...]} script reference can address {@code ref} by inside
    * {@code dependent}: its output name (alias if set, else attribute) and its entity-qualified
    * {@code Entity.attribute} form. The bare attribute is included for an aliased column only when
    * no other column of {@code dependent} outputs that same name -- in a join where both sides
    * have {@code Amount} (the second aliased {@code Amount_1}), {@code field['Amount']} reads the
    * FIRST side's column and must not count as a reference to the second.
    */
   static Set<String> fieldNames(TableAssembly dependent, ColumnRef ref) {
      Set<String> names = new LinkedHashSet<>();
      String attribute = ref.getAttribute();
      String alias = ref.getAlias();
      String entity = ref.getEntity();
      boolean aliased = alias != null && !alias.isEmpty();

      if(aliased) {
         names.add(alias);
      }
      else if(attribute != null) {
         names.add(attribute);
      }

      if(attribute != null && entity != null && !entity.isEmpty()) {
         names.add(entity + "." + attribute);
      }

      if(aliased && attribute != null && !isOutputNameOfAnotherColumn(dependent, ref, attribute)) {
         names.add(attribute);
      }

      return names;
   }

   private static boolean isOutputNameOfAnotherColumn(TableAssembly table, ColumnRef ref,
                                                      String name)
   {
      ColumnSelection columns = table.getColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef column = columns.getAttribute(i);

         if(!(column instanceof ColumnRef other) || other == ref || other.equals(ref)) {
            continue;
         }

         String otherAlias = other.getAlias();
         String output = otherAlias != null && !otherAlias.isEmpty() ?
            otherAlias : other.getAttribute();

         if(name.equals(output)) {
            return true;
         }
      }

      return false;
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
      checkCubeTableReadPermission(principal, name);
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
   private QueryManagerService queryManagerService;

   protected static final int ROW_LIMIT = 10000;
   protected static final int COL_LIMIT = 1000;
   protected static final int BLOCK = 100; // Table row counts per loading process
}
