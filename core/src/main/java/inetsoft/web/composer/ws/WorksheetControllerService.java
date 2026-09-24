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
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XUtil;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSInsertColumnsEvent;
import inetsoft.web.composer.ws.event.WSInsertColumnsEventValidator;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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

         // Bug #76891 / WBS-087: becoming a non-group column is not, on its own, "at
         // risk" -- a column that survives newInfo as an unaliased/unchanged-name
         // aggregate is still addressable by downstream under the exact same identity
         // it always had, so it must not be flagged just because it stopped being a
         // plain row-level column. A column that resolves to neither a group NOR such a
         // preserved-identity aggregate (WBS-086's group-by-only case, or a genuine
         // re-alias/removal) remains "at risk" and is still scanned below.
         if(survivesAsPreservedAggregate(newInfo, colRef)) {
            continue;
         }

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
    * Bug #76891 / WBS-087: true when {@code newInfo} still has an aggregate on {@code col}
    * that keeps it addressable under the identity it already had -- i.e. an aggregate
    * whose own {@link DataRef} matches {@code col} (by attribute/entity, alias-insensitive,
    * via {@link AggregateInfo#getAggregates(DataRef)}) AND carries no NEW alias different
    * from {@code col}'s own attribute name. A column that only survives as such an
    * aggregate is NOT actually put at risk by losing row-level identity -- a downstream
    * consumer that already addresses it by its own (unaliased) name keeps resolving fine.
    * A literal "col's name string is unchanged" check would get this wrong in the other
    * direction too (see WBS-086, {@code ORDER_ID}): a column can be entirely untouched by
    * the call and still be genuinely at risk, because it falls out of {@code newInfo}
    * coverage (neither grouped nor aggregated) entirely -- membership in {@code newInfo},
    * not name stability, is what this must test.
    */
   private static boolean survivesAsPreservedAggregate(AggregateInfo newInfo, ColumnRef col) {
      for(AggregateRef agg : newInfo.getAggregates(col)) {
         DataRef aggRef = agg.getDataRef();
         String aggAlias = aggRef instanceof ColumnRef ? ((ColumnRef) aggRef).getAlias() : null;

         if(aggAlias == null || aggAlias.equals(col.getAttribute())) {
            return true;
         }
      }

      return false;
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
