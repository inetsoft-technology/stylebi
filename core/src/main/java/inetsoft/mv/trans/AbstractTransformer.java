/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.mv.trans;

import inetsoft.mv.MVTool;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;

import java.util.*;

/**
 * AbstractTransformer defines the common APIs to transforms one table assembly
 * in one worksheet.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public abstract class AbstractTransformer {
   /**
    * Check if the table contains ranking aggregate condition.
    */
   public static boolean containsGroupRanking(TableAssembly table) {
      ConditionListWrapper wrapper = table.getRankingRuntimeConditionList();

      if(wrapper == null || wrapper.isEmpty()) {
         return false;
      }

      ConditionList conds = wrapper.getConditionList();

      for(int i = 0; i < conds.getSize(); i += 2) {
         ConditionItem titem = conds.getConditionItem(i);
         RankingCondition tcond = (RankingCondition) titem.getXCondition();
         DataRef aggregate = tcond.getDataRef();
         DataRef attribute = titem.getAttribute();

         if(attribute == null || aggregate == null) {
            continue;
         }

         return true;
      }

      return false;
   }

   /**
    * Check if the table contains sub selection.
    */
   public static boolean containsSubSelection(TransformationDescriptor desc, TableAssembly table) {
      ConditionListWrapper wrapper = table.getPreConditionList();

      if(containsSubSelection0(desc, wrapper)) {
         return true;
      }

      wrapper = table.getPostConditionList();

      if(containsSubSelection0(desc, wrapper)) {
         return true;
      }

      // mv update condition always generaged in db, do not need check
      // mv delete condition may be generated in mv, check
      wrapper = table.getMVDeleteConditionList();

      if(containsSubSelection0(desc, wrapper)) {
         return true;
      }

      if(!(table instanceof ComposedTableAssembly)) {
         return false;
      }

      ComposedTableAssembly ctable = (ComposedTableAssembly) table;
      TableAssembly[] stables = ctable.getTableAssemblies(false);

      for(int i = 0; i < stables.length; i++) {
         if(containsSubSelection(desc, stables[i]) || containsSelection(desc, stables[i])) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if the table contains sub selection.
    */
   private static boolean containsSubSelection0(TransformationDescriptor desc,
                                                ConditionListWrapper wrapper)
   {
      ConditionList conds = wrapper == null ? null : wrapper.getConditionList();

      for(int i = 0; conds != null && i < conds.getSize(); i += 2) {
         Object cond = conds.getXCondition(i);

         if(!(cond instanceof AssetCondition)) {
            continue;
         }

         AssetCondition acnd = (AssetCondition) cond;
         SubQueryValue val = acnd.getSubQueryValue();

         if(val == null) {
            continue;
         }

         String query = val.getQuery();
         TableAssembly stable = query == null ?
            null : (TableAssembly) desc.getWorksheet().getAssembly(query);

         if(stable == null) {
            continue;
         }

         if(containsSubSelection(desc, stable) || containsSelection(desc, stable)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if contains selection.
    */
   private static boolean containsSelection(TransformationDescriptor desc,
                                            TableAssembly table)
   {
      ConditionListWrapper wrapper = table.getPreRuntimeConditionList();
      ConditionList conds = wrapper == null ? null : wrapper.getConditionList();

      if(conds != null && conds.getSize() > 0) {
         return true;
      }

      wrapper = table.getPostRuntimeConditionList();
      conds = wrapper == null ? null : wrapper.getConditionList();

      if(conds != null && conds.getSize() > 0) {
         return true;
      }

      wrapper = table.getRankingRuntimeConditionList();
      conds = wrapper == null ? null : wrapper.getConditionList();

      if(conds != null && conds.getSize() > 0) {
         return true;
      }

      wrapper = table.getMVDeleteConditionList();
      conds = wrapper == null ? null : wrapper.getConditionList();

      if(conds != null && conds.getSize() > 0) {
         return true;
      }

      wrapper = table.getMVUpdateConditionList();
      conds = wrapper == null ? null : wrapper.getConditionList();

      if(conds != null && conds.getSize() > 0 &&
         // if the mv update condition is moved to parent (it may still be
         // kept for the bound table for optimization, we should treat it
         // as non-existant for the purpose of analyzing MV
         !MVTool.isMVConditionMoved(table))
      {
         return true;
      }

      if(!(table instanceof ComposedTableAssembly)) {
         return false;
      }

      ComposedTableAssembly ctable = (ComposedTableAssembly) table;
      TableAssembly[] stables = ctable.getTableAssemblies(false);

      for(int i = 0; i < stables.length; i++) {
         if(containsSelection(desc, stables[i])) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if contains normal ranking which means not variable(runtime) ranking.
    */
   public static boolean containsRanking(TableAssembly table) {
      String tname = table.getName();
      ColumnSelection cols = table.getColumnSelection();
      ConditionListWrapper wrapper = table.getRankingConditionList();
      ConditionList list = wrapper == null ? null : wrapper.getConditionList();
      boolean contained = false;

      for(int i = 0; list != null && i < list.getSize(); i += 2) {
         ConditionItem citem = list.getConditionItem(i);
         DataRef attr = citem.getAttribute();

         if(cols.containsAttribute(attr)) {
            contained = true;
            break;
         }
      }

      return contained;
   }

   /**
    * Check if the specified table assembly is created in viewsheet.
    */
   public static final boolean isVSTable(String tname) {
      return tname.startsWith(Assembly.TABLE_VS);
   }

   /**
    * Normalize a viewsheet column to a table assembly column.
    * @param vcolumn the specified viewsheet column.
    * @param cols the column selection of the table assembly.
    * @return the normalized table assembly column.
    */
   public static final DataRef normalizeColumn(DataRef vcolumn, ColumnSelection cols) {
      String name = vcolumn.getName();
      return cols.getAttribute(name);
   }

   /**
    * Get the base data ref from a data ref.
    * @param ref the specified data ref.
    * @return the base data ref.
    */
   public static final DataRef getBaseDataRef(DataRef ref) {
      while(ref instanceof DataRefWrapper && !(ref instanceof RangeRef)) {
         ref = ((DataRefWrapper) ref).getDataRef();
      }

      return ref;
   }

   /**
    * Get the child column ref.
    * @param pcols the specified parent columns.
    * @param cols the specified child columns.
    * @param cname the specified child table name.
    * @param pref the specified parent data ref.
    * @return the found child column ref, and the parent column ref is also
    * returned for it might be changed as well.
    */
   public static final ColumnRef[] getChildColumn(ColumnSelection pcols,
                                                  ColumnSelection cols,
                                                  String cname, DataRef pref)
   {
      ColumnRef pcol = (ColumnRef) pcols.findAttribute(pref);

      if(pcol == null && pref.getEntity() == null) {
         pref = getParentColumn(pcols, cname, pref);
         pcol = (ColumnRef) pcols.findAttribute(pref);
      }

      if(pcol == null) {
         throw new RuntimeException("Column " + pref + " not found in: " + pcols + "!");
      }

      ColumnRef[] refs = getColumnRef(cols, cname, pref);

      if(refs != null && refs[1] != null) {
         refs[1].setVisible(pcol.isVisible());
         refs[1].setAlias(pcol.getAlias());
      }

      return refs;
   }

   /**
    * Get the column pref.
    * @param cols the specified child columns.
    * @param cname the specified child table name.
    * @param pref the parent data pref.
    * @return the found child column pref, and the parent column pref is also
    * returned for it might be changed as well.
    */
   public static final ColumnRef[] getColumnRef(ColumnSelection cols, String cname, DataRef pref) {
      ColumnRef pcol = null;
      pref = getBaseDataRef(pref);
      String aname = null;
      String dname = null;
      int doption = 0;
      boolean isDateRange = pref instanceof DateRangeRef;

      // handle alias data pref from measure
      if(pref instanceof AliasDataRef) {
         AliasDataRef aref = (AliasDataRef) pref;
         aname = aref.getName();
         pref = aref.getDataRef();

         // alias data pref of alias data pref
         if(pref instanceof AliasDataRef) {
            aref = (AliasDataRef) pref;
            pref = aref.getDataRef();
         }
      }
      // handle date range pref from dimension
      else if(pref instanceof RangeRef) {
         RangeRef range = ((RangeRef) pref);
         dname = range.getName();

         if(range instanceof DateRangeRef) {
            doption = ((DateRangeRef) range).getDateOption();
         }

         pref = range.getDataRef();
      }

      String entity = pref.getEntity();
      String attribute = pref.getAttribute();

      if(!Tool.equals(cname, entity)) {
         return null;
      }

      ColumnRef found = null;

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         ColumnRef scolumn = (ColumnRef) cols.getAttribute(i);
         String alias = scolumn.getAlias();

         if(Tool.equals(alias, attribute)) {
            found = scolumn;
            break;
         }
      }

      for(int i = 0; found == null && i < cols.getAttributeCount(); i++) {
         ColumnRef scolumn = (ColumnRef) cols.getAttribute(i);
         String alias = scolumn.getAlias();

         if(alias != null && alias.length() > 0 && !Tool.equals(alias, scolumn.getAttribute())) {
            continue;
         }

         String sattribute = scolumn.getAttribute();

         if(Tool.equals(sattribute, attribute)) {
            found = scolumn;
         }
      }

      // alias data pref
      if(aname != null && found != null) {
         AliasDataRef aref = new AliasDataRef(aname, found);
         ColumnRef ref2 = new ColumnRef(aref);
         found = ref2;

         if(!cols.containsAttribute(ref2)) {
            cols.addAttribute(ref2);
         }

         pcol = new ColumnRef(new AttributeRef(cname, aname));
      }

      // date range pref
      if(dname != null && found != null) {
         if(isDateRange) {
            DateRangeRef dref = new DateRangeRef(dname, found.getDataRef());
            dref.setOriginalType(found.getDataType());
            dref.setDateOption(doption);
            ColumnRef ref2 = new ColumnRef(dref);
            found = ref2;

            if(!cols.containsAttribute(ref2)) {
               cols.addAttribute(ref2);
            }
         }

         pcol = new ColumnRef(new AttributeRef(cname, dname));
      }

      return new ColumnRef[] {found, pcol};
   }

   /**
    * Get the parent column ref.
    * @param pcolumns the specified parent columns.
    * @param cname the specified child table name.
    * @param cref the specified child data ref.
    * @return the found parent column ref.
    */
   public static final ColumnRef getParentColumn(ColumnSelection pcolumns,
                                                 String cname, DataRef cref) {
      if(cref == null || pcolumns == null) {
         return null;
      }

      String name = cref.getAttribute();

      if(cref instanceof ColumnRef) {
         ColumnRef column = (ColumnRef) cref;
         String alias = column.getAlias();

         if(alias != null && alias.length() > 0) {
            name = alias;
         }
      }

      for(int i = 0; i < pcolumns.getAttributeCount(); i++) {
         ColumnRef pcolumn = (ColumnRef) pcolumns.getAttribute(i);
         DataRef ref = pcolumn.getDataRef();
         String entity = ref.getEntity();
         String attribute = ref.getAttribute();

         if(!Tool.equals(entity, cname)) {
            continue;
         }

         if(Tool.equals(attribute, name)) {
            return pcolumn;
         }
      }

      return new ColumnRef(AssetUtil.getOuterAttribute(cname, cref));
   }

   /**
    * Get the selection data ref.
    * @param useAlias true to use the column alias as column name if it's defined.
    */
   public static ColumnRef getSelectionDataRef(DataRef ref, boolean useAlias) {
      if(ref instanceof ColumnRef && useAlias) {
         ColumnRef column = (ColumnRef) ref;
         String alias = column.getAlias();

         if(alias != null && alias.length() > 0) {
            ref = new AttributeRef(null, alias);
            return new ColumnRef(ref);
         }
      }

      ref = new AttributeRef(null, ref.getAttribute());
      return new ColumnRef(ref);
   }

   /**
    * Check whether the condition list is empty.
    */
   public static final boolean isEmptyFilter(ConditionListWrapper wrapper) {
      return wrapper == null || wrapper.getConditionSize() == 0;
   }

   /**
    * Check whether the condition list is dynamic - parameter.
    */
   public static final boolean isDynamicFilter(TransformationDescriptor desc,
                                               ConditionListWrapper wrapper,
                                               VariableTable table) {
      Worksheet ws = desc.getWorksheet();
      ConditionList list = wrapper == null ? null : wrapper.getConditionList();
      int size = list == null ? 0 : list.getSize();

      if(containsSessionVariable(list)) {
         return true;
      }

      for(int i = 0; i < size; i += 2) {
         ConditionItem citem = list.getConditionItem(i);
         XCondition cond = citem.getXCondition();
         UserVariable[] vars = cond.getAllVariables();

         if(vars != null && vars.length > 0) {
            return true;
         }

         if(ws == null || !(cond instanceof AssetCondition)) {
            continue;
         }

         AssetCondition acnd = (AssetCondition) cond;

         // if the condition references a dynamic table, the table is dynamic (48975).
         boolean dynamic = acnd.getValues().stream()
            .filter(a -> a instanceof DataRef)
            .map(a -> ((DataRef) a).getEntity())
            .anyMatch(a -> desc.isInputDynamicTable(a));

         if(dynamic) {
            return true;
         }

         SubQueryValue val = acnd.getSubQueryValue();

         if(val == null) {
            continue;
         }

         String query = val.getQuery();
         TableAssembly stable = query == null ? null : (TableAssembly) ws.getAssembly(query);

         if(stable == null) {
            continue;
         }

         if(isDynamicTable(desc, stable, table)) {
            return true;
         }
      }

      return false;
   }

   private static boolean containsSessionVariable(ConditionList list) {
      int size = list == null ? 0 : list.getSize();

      for(int i = 0; i < size; i += 2) {
         ConditionItem citem = list.getConditionItem(i);
         XCondition cond = citem.getXCondition();

         if(cond instanceof Condition) {
            Condition c = (Condition) cond;

            for(int j = 0; j < c.getValueCount(); j++) {
               Object val = c.getValue(j);

               if(Condition.isSessionVariable(val)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static boolean isDynamicTable(TransformationDescriptor desc,
                                         TableAssembly stable,
                                         VariableTable vars)
   {
      String tname = stable.getName();

      // bind viewsheet selection?
      if(desc.getSelectionColumns(tname, false).size() > 0) {
         return true;
      }

      ConditionListWrapper swrapper = stable.getPreConditionList();

      if(isDynamicFilter(desc, swrapper, vars)) {
         return true;
      }

      swrapper = stable.getPreRuntimeConditionList();

      if(swrapper != null && !swrapper.isEmpty()) {
         return true;
      }

      swrapper = stable.getPostConditionList();

      if(isDynamicFilter(desc, swrapper, vars)) {
         return true;
      }

      swrapper = stable.getPostRuntimeConditionList();

      if(swrapper != null && !swrapper.isEmpty()) {
         return true;
      }

      swrapper = stable.getRankingConditionList();

      if(isDynamicFilter(desc, swrapper, vars)) {
         return true;
      }

      swrapper = stable.getRankingRuntimeConditionList();

      if(swrapper != null && !swrapper.isEmpty()) {
         return true;
      }

      if(stable instanceof ComposedTableAssembly) {
         TableAssembly[] tables =
            ((ComposedTableAssembly) stable).getTableAssemblies(false);

         for(TableAssembly table : tables) {
            if(isDynamicTable(desc, table, vars)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check if all aggregates are combinable.
    */
   public static final boolean isAggregateCombinable(TableAssembly table,
                                                     AggregateInfo aggr) {
      if(!aggr.isEmpty() && aggr.isCrosstab()) {
         return false;
      }

      for(int i = 0; i < aggr.getGroupCount(); i++) {
         GroupRef group = aggr.getGroup(i);

         if(group.getDateGroup() != GroupRef.NONE_DATE_GROUP) {
            return false;
         }
      }

      XDataSource source =
         DataSourceRegistry.getRegistry().getDataSource(table.getSource());
      int dbtype = -1;

      if(source instanceof JDBCDataSource) {
         dbtype = ((JDBCDataSource) source).getDatabaseType();
      }

      for(int i = 0; i < aggr.getAggregateCount(); i++) {
         AggregateRef ref = aggr.getAggregate(i);

         if(ref.isPercentage()) {
            return false;
         }

         DataRef cref = ref.getDataRef();
         cref = getBaseDataRef(cref);

         if(cref instanceof AliasDataRef) {
            cref = ((AliasDataRef) cref).getDataRef();
         }

         if(cref.isExpression()) {
            return false;
         }

         DataRef ref2 = ref.getSecondaryColumn();

         if(ref2 != null) {
            ref2 = getBaseDataRef(ref2);

            if(ref2 instanceof AliasDataRef) {
               ref2 = ((AliasDataRef) ref2).getDataRef();
            }

            if(ref2.isExpression()) {
               return false;
            }

            String table1 = cref.getEntity();
            String table2 = ref2.getEntity();

            if(!Tool.equals(table1, table2)) {
               return false;
            }
         }

         if(ref.getFormula() != null && !ref.getFormula().isCombinable()) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if any selection is defined on the aggregate column.
    */
   public static final boolean isSelectionOnAggregate(TableAssembly table,
      TransformationDescriptor desc)
   {
      AggregateInfo ainfo = table.getAggregateInfo();
      ColumnSelection tcolumns = table.getColumnSelection();
      List columns = desc.getSelectionColumns(table.getName(), false);

      for(int i = 0; i < columns.size(); i++) {
         WSColumn column = (WSColumn) columns.get(i);

         // for selection on aggregate, when create mv, the selection is cleared,
         // when run mv, apply the selection, aggregate value cannot be changed,
         // but for mv selection/post selection, avaliable columns are groups
         // and aggregates, condition can working correct
         if(desc.isPostColumn(table.getName(), column)) {
            continue;
         }

         DataRef ref = column.getDataRef();
         ref = normalizeColumn(ref, tcolumns);

         if(ainfo.containsAggregate(ref) || ainfo.containsAliasAggregate(ref)) {
            return true;
         }
      }

      ConditionListWrapper wrapper = table.getPreRuntimeConditionList();
      ConditionList conds = wrapper == null ? null : wrapper.getConditionList();

      for(int i = 0; conds != null && i < conds.getSize(); i += 2) {
         ConditionItem citem = conds.getConditionItem(i);
         DataRef ref = citem.getAttribute();

         if(ainfo.containsAggregate(ref) || ainfo.containsAliasAggregate(ref)) {
            return true;
         }
      }

      if(table instanceof AbstractJoinTableAssembly) {
         AbstractJoinTableAssembly jtable = (AbstractJoinTableAssembly) table;
         Enumeration tables = jtable.getOperatorTables();

         while(tables.hasMoreElements()) {
            String[] pair = (String[]) tables.nextElement();
            String ltable = pair[0];
            String rtable = pair[1];
            TableAssemblyOperator top = jtable.getOperator(ltable, rtable);
            TableAssemblyOperator.Operator[] ops = top == null ?
               new TableAssemblyOperator.Operator[0] : top.getOperators();

            for(int i = 0; i < ops.length; i++) {
               DataRef lattr = ops[i].getLeftAttribute();
               DataRef rattr = ops[i].getRightAttribute();
               lattr = AssetUtil.getOuterAttribute(ltable, lattr);
               rattr = AssetUtil.getOuterAttribute(rtable, rattr);

               if(ainfo.containsAggregate(lattr) ||
                  ainfo.containsAliasAggregate(lattr) ||
                  ainfo.containsAggregate(rattr) ||
                  ainfo.containsAliasAggregate(rattr))
               {
                  return true;
               }
            }
         }
      }

      return false;
   }

   /**
    * Fix aggregate info comes from child table.
    * @param ptable the specified parent table.
    * @param table the specified child table.
    */
   public static void fixParentAggregateInfo(TableAssembly ptable, TableAssembly table) {
      AggregateInfo ainfo = ptable.getAggregateInfo();
      ColumnSelection pcolumns = ptable.getColumnSelection();
      ColumnSelection columns = table.getColumnSelection();
      String sname = table.getName();

      for(int i = 0; i < ainfo.getAggregateCount(); i++) {
         AggregateRef aref = ainfo.getAggregate(i);
         DataRef ref = aref.getDataRef();
         ref = AbstractTransformer.getParentColumn(pcolumns, sname, ref);
         aref.setDataRef(ref);
         DataRef sref = aref.getSecondaryColumn();

         if(sref != null) {
            sref = AbstractTransformer.getParentColumn(pcolumns, sname, sref);
            aref.setSecondaryColumn(sref);
         }
      }

      for(int i = 0; i < ainfo.getGroupCount(); i++) {
         GroupRef gref = ainfo.getGroup(i);
         DataRef ref = gref.getDataRef();
         int index = columns.indexOfAttribute(ref);
         ColumnRef pref = AbstractTransformer.getParentColumn(
            pcolumns, sname, ref);

         if(index >= 0) {
            ColumnRef column = (ColumnRef) columns.getAttribute(index);

            if(!column.isVisible()) {
               column.setVisible(true);
               pref.setVisible(false);
               pcolumns.addAttribute(pref);
            }
         }

         gref.setDataRef(pref);
      }
   }

   /**
    * Move the selection columns from tbl to ptbl.
    */
   protected void moveSelectionColumns(TransformationDescriptor desc,
                                       TableAssembly tbl, TableAssembly ptbl)
   {
      List cols = desc.getSelectionColumns(tbl.getName(), false);
      List pcols = desc.getSelectionColumns(ptbl.getName(), false);
      moveSelectionColumns(desc, tbl, cols, ptbl, pcols, true);

      cols = desc.getPreSelectionColumns(tbl.getName());
      pcols = desc.getPreSelectionColumns(ptbl.getName());
      moveSelectionColumns(desc, tbl, cols, ptbl, pcols, false);

      cols = desc.getPostSelectionColumns(tbl.getName());
      pcols = desc.getPostSelectionColumns(ptbl.getName());
      moveSelectionColumns(desc, tbl, cols, ptbl, pcols, false);

      cols = desc.getRankingSelectionColumns(tbl.getName());
      pcols = desc.getRankingSelectionColumns(ptbl.getName());
      moveSelectionColumns(desc, tbl, cols, ptbl, pcols, false);

      cols = desc.getMVSelectionColumns(tbl.getName());
      pcols = desc.getMVSelectionColumns(ptbl.getName());
      moveSelectionColumns(desc, tbl, cols, ptbl, pcols, false);
   }

   /**
    * Move selection column from tbl to ptbl.
    */
   private void moveSelectionColumns(TransformationDescriptor desc,
                                     TableAssembly tbl, List cols,
                                     TableAssembly ptbl, List pcols,
                                     boolean info)
   {
      ColumnSelection columns = tbl.getColumnSelection();
      ColumnSelection pcolumns = ptbl.getColumnSelection();

      for(int i = 0; i < cols.size(); i++) {
         WSColumn column = (WSColumn) cols.get(i);
         DataRef col = column.getDataRef();
         ColumnRef col2 = (ColumnRef) normalizeColumn(col, columns);
         DataRef pcol = getParentColumn(pcolumns, tbl.getName(), col2);
         // a duplicate column in join table may have _1 suffix added to the alias to
         // make it unique. moving the column up should not carry that _1 suffix or the
         // column will not be found. (60645, 62196)
         boolean pcolExists = ptbl.getColumnSelection(true).containsAttribute(pcol);
         pcol = getSelectionDataRef(pcol, pcolExists);
         WSColumn pcolumn = new WSColumn(ptbl.getName(), pcol);
         pcolumn.setRangeInfo(column.getRangeInfo());
         pcols.add(pcolumn);

         /*
         if(mcols != null) {
            mcols.add(pcolumn);
         }
         */

         if(info && "true".equals(SreeEnv.getProperty("mv.info.warning"))) {
            desc.addInfo(TransformationInfo.moveUp(pcolumn));
         }
      }

      if(info && cols.size() > 0) {
         desc.addInfo(TransformationInfo.selectionUp(tbl.getName()));
      }
   }

   /**
    * Check if aggregate contains dictinct count.
    */
   protected boolean containsNonCombinable(AggregateInfo ainfo) {
      return Arrays.stream(ainfo.getAggregates()).anyMatch(aref -> {
            AggregateFormula form = aref.getFormula();

            return AggregateFormula.COUNT_DISTINCT.equals(form) ||
               AggregateFormula.MEDIAN.equals(form) ||
               AggregateFormula.MODE.equals(form);
         });
   }

   /**
    * Check if the assembly contains named range or not.
    */
   protected boolean containsNamedRangeAggregate(TableAssembly table) {
      AggregateInfo ainfo = table.getAggregateInfo();

      for(GroupRef gref : ainfo.getGroups()) {
         if(isNamedRangeRef(gref.getDataRef())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if the assembly contains named group.
    */
   protected boolean containsNamedGroup(TableAssembly table) {
      AggregateInfo ainfo = table.getAggregateInfo();

      for(GroupRef gref : ainfo.getGroups()) {
         if(gref.getNamedGroupInfo() != null && !gref.getNamedGroupInfo().isEmpty()) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if this is a ColumnRef pointing to a NamedRangeRef.
    */
   protected boolean isNamedRangeRef(DataRef dref) {
      return dref instanceof ColumnRef && ((ColumnRef) dref).getDataRef() instanceof NamedRangeRef;
   }

   /**
    * Add transformation fault.
    * @param desc transformation descriptor.
    * @param fault transformation fault.
    * @param block child data block.
    * @param parent parent data block.
    */
   protected void addFault(TransformationDescriptor desc, TransformationFault fault,
                           String block, String parent)
   {
      parent = normalizeBlockName(parent);
      block = normalizeBlockName(block);

      if(!block.equals(parent)) {
         desc.addInfo(getInfo(block));
         desc.addFault(fault);
      }
   }

   /**
    * Get caluclate fields from the table if exist
    */
   protected boolean containsCalculateFields(TableAssembly table) {
      ColumnSelection selection = table.getColumnSelection(false);

      for(int i = 0; i < selection.getAttributeCount(); i++) {
         DataRef ref = selection.getAttribute(i);

         if(ref instanceof CalculateRef) {
            return true;
         }
      }

      return false;
   }

   /**
    * Normalize the name of a data block. If the data block ends with "_O",
    * trim it. If the property "mv_info_warning" is set, don't trim it, which
    * is used to debug.
    * @param block child data block.
    */
   public static String normalizeBlockName(String block) {
      if(!"true".equals(SreeEnv.getProperty("mv.info.warning"))) {
         block = VSUtil.stripOuter(block);
      }

      block = normalizeCubeName(block);
      return block;
   }

   /**
    * Shrink cube table prefix.
    */
   public static String normalizeCubeName(String block) {
      if(block != null && block.startsWith(Assembly.CUBE_VS)) {
         block = block.substring(Assembly.CUBE_VS.length());
      }

      return block;
   }

   /**
    * Create an instance of AbstractTransformer.
    */
   public AbstractTransformer() {
      super();
   }

   /**
    * Transform the table assembly.
    * @return true if successful, false otherwise.
    */
   public abstract boolean transform(TransformationDescriptor desc);

   /**
    * Get the transformation message for this transformer.
    * @param block the data block.
    */
   protected abstract TransformationInfo getInfo(String block);
}
