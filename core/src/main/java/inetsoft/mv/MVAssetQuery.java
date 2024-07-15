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
package inetsoft.mv;

import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.mv.data.MVQueryBuilder;
import inetsoft.report.TableLens;
import inetsoft.report.composition.QueryTreeModel.QueryNode;
import inetsoft.report.composition.execution.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.FilledTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.report.script.formula.AssetQueryScope;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.swap.XIntList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * MVAssetQuery, the asset query to be executed based on one materialized view.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public class MVAssetQuery extends AssetQuery {
   /**
    * Creates a new instance of AssetQuery.
    * @param table  the table assembly that defines the query.
    * @param user   the user who executes the query.
    * @param vars   the specified variable table.
    * @param stable <tt>true</tt> if the table is a sub-table; <tt>false</tt>
    *               otherwise.
    */
   public static AssetQuery createQuery(TableAssembly table, XPrincipal user,
                                        VariableTable vars, boolean stable,
                                        int mode)
   {
      return new MVAssetQuery(table, user, vars, stable, mode);
   }

   /**
    * Replace variables defined in pre runtime condition list.
    */
   private static void replaceVariables(TableAssembly table, VariableTable vars,
                                        AssetQuerySandbox box)
   {
      replaceVariables(vars, table.getPreRuntimeConditionList(), box);
      replaceVariables(vars, table.getPostRuntimeConditionList(), box);
      replaceVariables(vars, table.getRankingRuntimeConditionList(), box);
   }

   private static void replaceVariables(VariableTable vars,
                                        ConditionListWrapper wrapper,
                                        AssetQuerySandbox box)
   {
      ConditionList conds = wrapper == null ? null : wrapper.getConditionList();

      if(conds == null) {
         return;
      }

      conds.replaceVariables(vars);

      HierarchyListModel model = new HierarchyListModel(conds);

      // the value of a variable might be null. For this case, remove
      // the condition as ignored condition, which is a convention...
      for(int i = model.getSize() - 1; i >= 0; i -= 2) {
         ConditionItem item = (ConditionItem) model.getElementAt(i);
         XCondition cond = item.getXCondition();
         UserVariable[] arr = cond.getAllVariables();

         if(arr != null && arr.length > 0 && cond instanceof AssetCondition) {
            AssetCondition cond2 = (AssetCondition) cond;
            boolean remove = true;

            if(cond2.getValueCount() == 1 && cond2.getValue(0) instanceof SubQueryValue) {
               // subquery, don't remove the condition and let subquery
               // ignore the condition in its own processing
               continue;
            }

            if(cond2.getValueCount() == 1 && cond2.getValue(0) instanceof ExpressionValue) {
               Object val = evaluateExpression((ExpressionValue) cond2.getValue(0), cond2,
                                               vars, box);
               cond2.setValue(0, val);
               remove = val == null;
            }
            else if(cond2.getValueCount() == 2 && (cond2.getValue(0) instanceof ExpressionValue ||
               cond2.getValue(1) instanceof ExpressionValue))
            {
               Object value1;
               Object value2;

               if(cond2.getValue(0) instanceof ExpressionValue) {
                  value1 = evaluateExpression((ExpressionValue) cond2.getValue(0), cond2,
                                              vars, box);
                  cond2.setValue(0, value1);
               }
               else {
                  value1 = 1;
               }

               if(cond2.getValue(1) instanceof ExpressionValue) {
                  value2 = evaluateExpression((ExpressionValue) cond2.getValue(1), cond2,
                                              vars, box);
                  cond2.setValue(1, value2);
               }
               else {
                  value2 = 1;
               }

               remove = value1 == null || value2 == null;
            }

            if(remove) {
               LOG.debug("Condition dropped due to null variable value: {}", cond);
               model.removeConditionItem(i);
            }
         }
      }
   }

   private static Object evaluateExpression(ExpressionValue expressionValue,
                                            AssetCondition condition,
                                            VariableTable vars, AssetQuerySandbox box)
   {
      String exp = expressionValue.getExpression();
      Object value = null;

      if(expressionValue.getType().equals(ExpressionValue.JAVASCRIPT)) {
         value = PreAssetQuery.execScriptExpression(exp, condition, vars, box);
      }

      return value;
   }

   /**
    * Creates a new instance of MVAssetQuery.
    * @param table  the table assembly that defines the query.
    * @param user   the user who executes the query.
    * @param vars   the specified variable table.
    * @param stable <tt>true</tt> if the table is a sub-table; <tt>false</tt>
    *               otherwise.
    */
   private MVAssetQuery(TableAssembly table, XPrincipal user, VariableTable vars, boolean stable,
                        int mode)
   {
      super(mode, new AssetQuerySandbox(table.getWorksheet(), user, vars), stable, false);
      this.table = table;
      RuntimeMV rmv = table.getRuntimeMV();
      replaceVariables(table, vars, box);
      validateConditionList(getPreRuntimeConditionList(), false);
      // might be from remote slave node
      boolean sub = rmv == null ? table.getProperty("sub.mv").equals("true") : rmv.isSub();
      TableAssembly[] tables = transform(table, sub, aggcalcs, exps);
      this.mirror = tables[0];
      this.table = tables[1];
   }

   /**
    * Get the avaliable [groups, aggregates] to run mv.
    */
   public static String[][] getAvailableMVColumns(MVDef def, TableAssembly table) {
      RuntimeMV rmv = table.getRuntimeMV();
      boolean sub = rmv.isSub();
      TableAssembly[] tables = transform(table, sub, new ArrayList<>(), new ArrayList<>());
      table = tables[0] == null ? tables[1] : tables[0];
      prepareColumns(table);
      return MVQueryBuilder.getQueryColumns(table, def);
   }

   private static TableAssembly[] transform(TableAssembly table, boolean sub,
                                            List<CalculateRef> aggcalcs,
                                            List<ExpressionRef> exps)
   {
      TableAssembly[] tables = new TableAssembly[2];
      tables[1] = table;

      if(sub) {
         TableAssembly thisTable = (TableAssembly) table.clone();
         thisTable.resetColumnSelection();
         MirrorTableAssembly mtable =
            new MirrorTableAssembly(thisTable.getWorksheet(), "null", table);
         transform(mtable, thisTable);
         tables[0] = mtable;
         tables[1] = thisTable;
      }
      else {
         AggregateInfo ainfo = table.getAggregateInfo();

         if(ainfo != null) {
            AggregateRef[] arefs = ainfo.getAggregates();

            for(AggregateRef aref : arefs) {
               if(processAggregateCalc(aggcalcs, arefs, aref.getDataRef())) {
                  ainfo.removeAggregate(aref);
               }
            }

            for(GroupRef group : ainfo.getGroups()) {
               if(processAggregateCalc(aggcalcs, arefs, group.getDataRef())) {
                  ainfo.removeGroup(group);
               }
            }
         }

         // not group, aggregate? we should remove aggregate calc from column
         // selection, because it is meaningless
         // fix bug1304419363764
         if(ainfo == null || ainfo.isEmpty()) {
            ColumnSelection cols = table.getColumnSelection();
            boolean calc = false;

            for(int i = cols.getAttributeCount() - 1; i >= 0; i--) {
               ColumnRef ref = (ColumnRef) cols.getAttribute(i);

               if(!(ref instanceof CalculateRef)) {
                  continue;
               }

               CalculateRef cref = (CalculateRef) ref;

               if(!cref.isBaseOnDetail()) {
                  calc = true;
                  cols.removeAttribute(i);
               }
            }

            // aggregate calc, but detailed table? need post process
            // fix bug1325047841359
            if(calc) {
               for(int i = cols.getAttributeCount() - 1; i >= 0; i--) {
                  DataRef sref = ((ColumnRef) cols.getAttribute(i)).getDataRef();

                  if(sref instanceof ExpressionRef &&
                     !(sref instanceof AliasDataRef) &&
                     !(sref instanceof DateRangeRef))
                  {
                     exps.add((ExpressionRef) sref);
                     cols.removeAttribute(i);
                  }
               }
            }

            table.setColumnSelection(cols);
         }
      }

      return tables;
   }

   private static boolean processAggregateCalc(List<CalculateRef> aggcalcs, AggregateRef[] arefs,
                                               DataRef baseRef)
   {
      if(VSUtil.isAggregateCalc(baseRef)) {
         CalculateRef cref = (CalculateRef) baseRef.clone();
         ExpressionRef eref = ((ExpressionRef) cref.getDataRef());
         rewriteCalc(eref, arefs);
         aggcalcs.add(cref);
         return true;
      }

      return false;
   }

   /**
    * Get the runtime table.
    */
   private TableAssembly getRuntimeTable() {
      return mirror != null ? mirror : table;
   }

   /**
    * Transform table assembly.
    */
   private static void transform(TableAssembly parent, TableAssembly child) {
      // move distinct and max rows
      parent.setDistinct(child.isDistinct());
      parent.setMaxRows(child.getMaxRows());
      parent.setRuntimeMV(child.getRuntimeMV());
      child.setDistinct(false);
      child.setMaxRows(0);
      child.setRuntimeMV(null);

      ColumnSelection ocols = child.getColumnSelection(true);
      ColumnSelection icols = child.getColumnSelection(false);
      String cname = child.getName();

      // generate aggregate info
      AggregateInfo painfo = new AggregateInfo();
      AggregateInfo ainfo = child.getAggregateInfo();
      parent.setAggregateInfo(painfo);

      if(!ainfo.isEmpty() && ainfo.isCrosstab()) {
         ainfo.clear();
      }

      for(int i = 0; i < ainfo.getGroupCount(); i++) {
         ColumnRef col = (ColumnRef) ainfo.getGroup(i).getDataRef();
         ColumnRef pcol = getParentCol(cname, col);
         painfo.addGroup(new GroupRef(pcol));
      }

      for(int i = 0; i < ocols.getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) ocols.getAttribute(i);
         ColumnRef pcol = getParentCol(cname, col);

         if(ainfo.containsAggregate(col)) {
            AggregateRef aref = new AggregateRef(pcol, AggregateFormula.MAX);
            painfo.addAggregate(aref);
         }
      }

      // generate filtering
      ConditionListWrapper wrapper = child.getPreRuntimeConditionList();
      parent.setPreRuntimeConditionList(transform(child, wrapper));
      wrapper = child.getPostRuntimeConditionList();
      parent.setPostRuntimeConditionList(transform(child, wrapper));
      wrapper = child.getRankingRuntimeConditionList();
      parent.setRankingRuntimeConditionList(transform(child, wrapper));

      // generate sorting
      SortInfo sinfo = child.getSortInfo();
      SortInfo psinfo = sinfo == null ? new SortInfo() : (SortInfo) sinfo.clone();
      parent.setSortInfo(psinfo);

      for(int i = 0; i < psinfo.getSortCount(); i++) {
         SortRef sref = psinfo.getSort(i);
         DataRef attr = sref.getDataRef();
         ColumnRef col = (ColumnRef) icols.findAttribute(attr);

         if(col == null) {
            LOG.warn("Sorting column not found during transformation: {}", attr);
            continue;
         }

         ColumnRef pcol = getParentCol(cname, col);
         sref.setDataRef(pcol);
      }
   }

   private static boolean isConditionValid(ConditionListWrapper conds, ColumnSelection cols) {
      if(conds != null) {
         for(int i = 0; i < conds.getConditionSize(); i++) {
            if(conds.getConditionItem(i) != null &&
               cols.findAttribute(conds.getConditionItem(i).getAttribute()) == null)
            {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Transform condition.
    */
   private static ConditionList transform(TableAssembly child, ConditionListWrapper wrapper) {
      ConditionList conds = wrapper == null ? null : wrapper.getConditionList();
      ConditionList pconds = conds == null ? new ConditionList() : conds.clone();
      ColumnSelection icols = child.getColumnSelection(false);
      String cname = child.getName();

      for(int i = 0; pconds != null && i < pconds.getSize(); i += 2) {
         ConditionItem citem = pconds.getConditionItem(i);
         DataRef attr = citem.getAttribute();
         ColumnRef col = (ColumnRef) icols.findAttribute(attr);

         if(col == null) {
            LOG.info("Filtering column not found: {}", attr);
            continue;
         }

         ColumnRef pcol = getParentCol(cname, col);
         citem.setAttribute(pcol);
      }

      return pconds;
   }

   /**
    * Get the parent column.
    */
   private static ColumnRef getParentCol(String cname, ColumnRef col) {
      ColumnRef pcol = new ColumnRef(AssetUtil.getOuterAttribute(cname, col));
      pcol.setDataType(col.getDataType());
      return pcol;
   }

   /**
    * Get the table.
    * @param vars the specified variable table.
    * @return the table of the query.
    */
   @Override
   public TableLens getTableLens(VariableTable vars) throws Exception {
      if(AssetQuerySandbox.isDesignMode(mode)) {
         return this.getDesignTableLens(vars);
      }

      // @see MVQueryBuilder
      if(vars != null) {
         vars.remove("__mvassetquery_null_exp_hint__");
      }

      TableAssembly tb = getRuntimeTable();
      RuntimeMV rinfo = tb.getRuntimeMV();
      boolean wsMV = rinfo != null && rinfo.isWSMV();

      // Fixes the SortInfo for sub tables which for some reason is empty.
      if(isSubTable() || wsMV) {
         merge(vars);
      }

      prepared = false;

      ColumnSelection oselection = (ColumnSelection) tb.getColumnSelection(true).clone();
      boolean sort = "true".equals(tb.getProperty("post.sort"));

      String vassembly = rinfo == null ? this.table.getProperty("vs.assembly") :
                                         rinfo.getVSAssembly();
      LOG.debug("Start to execute data from materialized view table {} for assembly {}",
                tb.getName(), vassembly);
      TableLens table = getPostBaseTableLens(vars);
      LOG.debug("End of execute data from materialized view table {} for assembly {}",
                tb.getName(), vassembly);

      // could be cancelled
      if(table == null) {
         return null;
      }

      // Need to sort sub tables before any kind of join is applied on them.
      // Otherwise the resulting table may look different.
      if(sort || isSubTable() || wsMV) {
         // sorts the groupings only
         table = getSortTableLens(table, vars);
         AggregateInfo ainfo = getAggregateInfo();

         if(!ainfo.isEmpty()) {
            int gcount = ainfo.getGroupCount();

            if(gcount > 0) {
               // convert to crosstab and handle ranking conditions on aggregate
               if(wsMV && (ainfo.isCrosstab() || !getRankingConditionList().isEmpty())) {
                  table = getSummaryTableLens(table, vars);
               }

               // in case there is a sort on an aggregate
               table = getSortSummaryTableLens(table, vars);
            }
         }

         if(prepared || wsMV) {
            table = createMapFilter(table, oselection);
         }
      }

      ConditionListWrapper pconds = tb.getPostRuntimeConditionList();

      if(pconds != null && !pconds.isEmpty()) {
         table = getConditionTableLens(table, vars, pconds.getConditionList());
      }

      AggregateInfo group = getAggregateInfo();

      // if table is empty and there is aggregate but no group, the regular query still
      // create a TableSummaryFilter2, which would return a value (e.g. 0 for count).
      // if we don't do the same for MV, the value would be null and different from non-mv
      if(!group.isEmpty() && group.getGroupCount() == 0 && !table.moreRows(1)) {
         table = prepareTableForSummary(table, group);
         table = getTableSummaryTableLens(table, vars);
      }

      table = getRankingTableLens(table, vars);
      table = getMaxRowsTableLens(table, vars);

      // handle "null" expression created in ChartVSAQuery
      String nullHint = vars == null ? null : (String) vars.get("__mvassetquery_null_exp_hint__");
      String[] nullHeaders = nullHint == null ? new String[0]
         : Tool.split(nullHint, "__^_^__", false);

      if(nullHeaders.length > 0) {
         List<String> allheaders = new ArrayList<>();

         for(int i = 0; i < table.getColCount(); i++) {
            allheaders.add(XUtil.getHeader(table, i).toString());
         }

         Collections.addAll(allheaders, nullHeaders);
         String[] headers = allheaders.toArray(new String[0]);
         table = new FilledTableLens(table, headers, null, null);
      }

      if(wsMV) {
         table = getFormatTableLens(table, vars);
      }

      return table;
   }

   // add secondary column so the formula won't fail.
   private static TableLens prepareTableForSummary(TableLens table, AggregateInfo group) {
      DefaultTableLens lens = new DefaultTableLens(table);

      for(int i = 0; i < group.getAggregateCount(); i++) {
         AggregateRef aggr = group.getAggregate(i);
         AggregateFormula formula = aggr.getFormula();

         // if formula requires 2nd column, it would liekly be missing (since it should
         // already be procedded in MV. in this call, MV returned an empty table so
         // we try to create a new summary table to make sure there is a value returned.
         // since the table is empty, we only need to add a column and not worry about
         // populating the value of the column.
         if(formula != null && formula.isTwoColumns()) {
            DataRef ref = aggr.getSecondaryColumn();

            if(ref != null && Util.findColumn(lens, ref) < 0) {
               lens.addColumn();
               lens.setObject(0, lens.getColCount() - 1, ref.getAttribute());
            }
         }
      }

      if(lens.getColCount() != table.getColCount()) {
         return lens;
      }

      return table;
   }

   /**
    * Prepare the correct column selection.
    */
   private static boolean prepareColumns(TableAssembly table) {
      boolean prepared = false;
      boolean sort = "true".equals(table.getProperty("post.sort"));
      boolean empty = table.getAggregateInfo().isEmpty();
      SortInfo info = table.getSortInfo();
      SortRef[] sorts = info.getSorts();

      if(!sort || !empty || sorts.length <= 0) {
         return prepared;
      }

      ColumnSelection publicCols = table.getColumnSelection(true);
      ColumnSelection privateCols = table.getColumnSelection(false);

      for(SortRef sort1 : sorts) {
         DataRef dref = sort1.getDataRef();
         String name = sort1.getName();
         ColumnRef column = (ColumnRef) publicCols.getAttribute(name);
         ColumnRef col = new ColumnRef(dref);
         col.setDataType(dref.getDataType());
         col.setVisible(true);

         if(column == null) {
            publicCols.addAttribute(col);
            prepared = true;
         }

         column = (ColumnRef) privateCols.getAttribute(name);

         if(column == null) {
            privateCols.addAttribute(col);
            prepared = true;
         }
      }

      return prepared;
   }

   /**
    * Get the post process base table.
    * @param vars the specified variable table.
    * @return the post process base table of the query.
    */
   @Override
   protected TableLens getPostBaseTableLens(VariableTable vars) throws Exception {
      TableAssembly table = getRuntimeTable();
      table.resetColumnSelection();
      prepared = prepareColumns(table);
      RuntimeMV rinfo = table.getRuntimeMV();
      runSubQuery(table, vars);
      String mvname = rinfo == null ? this.table.getProperty("mv.name") : rinfo.getMV();
      ColumnSelection ocols = table.getColumnSelection(true);

      AggregateInfo ainfo = table.getAggregateInfo();
      boolean empty = ainfo.isEmpty();
      QueryManager qmgr = getQueryManager();
      boolean wsMV = rinfo != null && rinfo.isWSMV();

      executor = MVTool.newMVExecutor(table, mvname, vars, (XPrincipal) box.getUser());

      if(qmgr != null) {
         qmgr.addPending(executor);
      }

      for(AggregateRef aggr : ainfo.getAggregates()) {
         if(aggr.getFormula() != null && aggr.getFormula().isNumeric()) {
            if(aggr.getDataRef() != null && XSchema.STRING.equals(aggr.getDataRef().getDataType())){
               String msg = "Calculating " + aggr.getFormula().getName() +
                  " of a STRING column (" + DataRefWrapper.getBaseDataRef(aggr).getAttribute() +
                  "). The result may be incorrect.";
               LOG.warn(msg);
               Tool.addUserMessage(msg);
            }
         }
      }

      TableLens data;

      try {
         data = (TableLens) executor.getData();
      }
      catch(Exception ex) {
         throw new MVExecutionException(ex);
      }

      // @by larryl, shouldn't clear it or queryManager.cancel won't work
      // executor = null;

      if(data == null) {
         return null;
      }

      ainfo = table.getAggregateInfo();
      boolean nempty = ainfo.isEmpty();
      int acnt = ainfo.getGroupCount() + ainfo.getAggregateCount();

      // aggregate is added, needs to add column map filter
      if((!wsMV || !ainfo.isCrosstab()) &&
         (empty && !nempty || ocols.getAttributeCount() != acnt || mirror != null))
      {
         data = createMapFilter(data, ocols);
      }

      String[] harr = null;
      String[] sarr = null;

      if(aggcalcs != null && !aggcalcs.isEmpty()) {
         harr = new String[aggcalcs.size()];
         sarr = new String[aggcalcs.size()];

         for(int i = 0; i < aggcalcs.size(); i++) {
            CalculateRef cref = aggcalcs.get(i);
            harr[i] = cref.getName();
            sarr[i] = ((ExpressionRef) cref.getDataRef()).getExpression();
         }
      }
      else if(!exps.isEmpty()) {
         List<String> hlist = new ArrayList<>();
         List<String> slist = new ArrayList<>();

         for(ExpressionRef eref : exps) {
            String name = eref.getName();
            String exp = eref.getExpression();

            if(AssetUtil.findColumn(data, eref) < 0) {
               hlist.add(name);
               slist.add(exp);
            }
         }

         harr = new String[hlist.size()];
         hlist.toArray(harr);
         sarr = new String[slist.size()];
         slist.toArray(sarr);
      }

      if(harr != null && harr.length > 0) {
         AssetQueryScope scope = box.getScope();
         scope.setMode(mode);
         ScriptEnv env = box.getScriptEnv();
         data = new FormulaTableLens(data, harr, sarr, env, box.getScope());
      }

      return new AssetTableLens(data);
   }

   private void runSubQuery(TableAssembly table, VariableTable vars) throws Exception {
      RuntimeMV rinfo = table.getRuntimeMV();

      // remote query? not support
      if(rinfo == null) {
         return;
      }

      ConditionListWrapper wrapper = table.getPreRuntimeConditionList();

      if(wrapper == null || wrapper.isEmpty()) {
         return;
      }

      Worksheet ws = table.getWorksheet();

      if(ws == null) {
         return;
      }

      ConditionList conds = wrapper.getConditionList();

      for(int i = 0; i < conds.getSize(); i += 2) {
         ConditionItem citem = conds.getConditionItem(i);
         XCondition cond = citem.getXCondition();

         if(!(cond instanceof AssetCondition)) {
            continue;
         }

         AssetCondition acond = (AssetCondition) cond;
         SubQueryValue val = acond.getSubQueryValue();

         if(val == null) {
            continue;
         }

         if(val.isCorrelated()) {
            throw new RuntimeException("Correlated subquery condition not supported in MV.");
         }

         String query = val.getQuery();
         TableAssembly stable = query == null ? null : (TableAssembly) ws.getAssembly(query);

         if(stable == null) {
            continue;
         }

         rinfo = stable.getRuntimeMV();

         if(rinfo == null) {
            continue;
         }

         /*
         stable.setRuntimeMV(null);
         vs.initVSTable(query);
         stable = (TableAssembly) ws.getAssembly(query);
         stable = new MirrorTableAssembly(ws, "V_M" + query + "_subQuery", stable);
         stable.setVisible(false);
         ws.addAssembly(stable);
         stable.setRuntimeMV(rinfo);
         */
         stable.setDistinct(true);
         AssetQuery q = AssetQuery.createAssetQuery(
            stable, mode, box, true, touchtime, true, false);
         TableLens lens = q.getTableLens(vars);
         lens = AssetQuery.shuckOffFormat(lens);
         val.initSubTable(lens);
         Vector<Object> values = val.getValues();
         acond.removeAllValues();

         for(Object obj : values) {
            acond.addValue(obj);
         }
      }
   }

   /**
    * Create map filter.
    */
   private TableLens createMapFilter(TableLens table, ColumnSelection cols) {
      int clen = cols.getAttributeCount();
      boolean match = table.getColCount() == clen;
      XIntList list = new XIntList();
      List<String> headers = new ArrayList<>();
      boolean useAlias = false;

      for(int i = 0; i < clen; i++) {
         DataRef ref = cols.getAttribute(i);
         int idx = AssetUtil.findColumn(table, ref);

         if(idx != i) {
            match = false;
         }

         if(idx >= 0) {
            if(ref instanceof ColumnRef && ((ColumnRef) ref).isVisible() &&
               !((ColumnRef) ref).isHiddenParameter())
            {
               // @by: ChrisSpagnoli bug1417662293999 2014-12-12
               // Prevent unneeded duplicate columns, as they can cause problems
               final String newHeader = XUtil.getHeader(table, idx).toString();
               final int newHeaderIndex = headers.indexOf(newHeader);

               if(newHeaderIndex == -1 || list.get(newHeaderIndex) != idx) {
                  list.add(idx);
                  headers.add(newHeader);
               }
            }
            else {
               match = false;
            }
         }
         // if alias and base column both exist, only one column in the table,
         // so here try to restore the correct columns by check from alias ref
         // fix bug1359363246703
         else {
            DataRef[] aliases = findAlias(cols, i);

            for(DataRef alias : aliases) {
               idx = AssetUtil.findColumn(table, alias);

               if(idx >= 0) {
                  list.add(idx);
                  match = false;
                  headers.add(VSUtil.getAttribute(ref));
                  useAlias = true;
                  break;
               }
            }
         }
      }

      if(match) {
         return table;
      }

      int[] arr = list.toArray();
      TableLens cmap = PostProcessor.mapColumn(table, arr);

      if(useAlias) {
         for(int i = 0; i < headers.size(); i++) {
            cmap.setObject(0, i, headers.get(i));
         }
      }

      return cmap;
   }

   /**
    * Find same column.
    */
   private DataRef[] findAlias(ColumnSelection cols, int idx) {
      DataRef ref = cols.getAttribute(idx);
      DataRef bcol = MVTransformer.getBaseRef(ref);
      List<DataRef> alias = new ArrayList<>();

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         if(i == idx) {
            continue;
         }

         DataRef ref2 = cols.getAttribute(i);
         DataRef bcol2 = MVTransformer.getBaseRef(ref2);

         if(bcol.equals(bcol2)) {
            alias.add(0, ref2);
         }
         else if(bcol.getAttribute().equals(bcol2.getAttribute())) {
            alias.add(ref2);
         }
      }

      return alias.toArray(new DataRef[0]);
   }

   /**
    * Get the table assembly to be executed.
    * @return the table assembly.
    */
   @Override
   protected TableAssembly getTable() {
      return table;
   }

   /**
    * Check if the source is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isSourceMergeable() throws Exception {
      return false;
   }

   /**
    * Merge from clause.
    * @return <tt>true</tt> if fully merged, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean mergeFrom(VariableTable vars) {
      // do nothing
      return true;
   }

   /**
    * If the merged columns are in a subquery, return the name
    * of the subquery (e.g. select col1 from (select ...) sub1).
    */
   @Override
   protected String getMergedTableName(ColumnRef column) {
      return null;
   }

   /**
    * Validate the column selection.
    */
   @Override
   public void validateColumnSelection() {
      if(shouldApplyVPM()) {
         super.validateColumnSelection();
      }
   }

   /**
    * Get the default column selection.
    * @return the default column selection.
    */
   @Override
   protected ColumnSelection getDefaultColumnSelection0() {
      ColumnSelection columnSelection = table.getColumnSelection();

      if(shouldApplyVPM() && table instanceof BoundTableAssembly) {
         try {
            BoundTableAssembly btable = (BoundTableAssembly) table;
            SourceInfo sourceInfo = btable.getSourceInfo();
            String[] tableNames;

            if(sourceInfo.getType() == SourceInfo.MODEL) {
               tableNames = XUtil.getEntities(sourceInfo.getPrefix(), sourceInfo.getSource(),
                                              box.getUser(), true);
            }
            else {
               tableNames = new String[]{ sourceInfo.getSource() };
            }

            BiFunction<String, String, Boolean> hcolumns = VpmProcessor.getInstance()
               .getHiddenColumnsSelector(
                  tableNames, new String[0], sourceInfo.getPrefix(), null, null, box.getUser());

            int ccnt = columnSelection.getAttributeCount();

            for(int i = ccnt - 1; i >= 0; i--) {
               ColumnRef col = (ColumnRef) columnSelection.getAttribute(i);
               String entity = sourceInfo.getType() == SourceInfo.MODEL ?
                  col.getEntity() : sourceInfo.getSource();

               if(hcolumns.apply(entity, col.getAttribute())) {
                  columnSelection.removeAttribute(i);
               }
            }
         }
         catch(Exception e) {
            LOG.warn("Failed to get vpm hidden columns: ", e);
         }
      }

      return columnSelection;
   }

   /**
    * Get the string representation of an attribute column.
    * @param column the specified attribute column.
    * @return the string representation of the attribute column.
    */
   @Override
   protected String getAttributeColumn(AttributeRef column) {
      return null;
   }

   /**
    * Get the alias of a column.
    * @param attr the specified column.
    * @return the alias of the column, <tt>null</tt> if not exists.
    */
   @Override
   protected String getAlias(ColumnRef attr) {
      return null;
   }

   /**
    * Get the target jdbc query to merge into.
    * @return the target jdbc query to merge into.
    */
   @Override
   public JDBCQuery getQuery() {
      return null;
   }

   /**
    * Get the target sql to merge into.
    * @return the target sql to merge into.
    */
   @Override
   protected UniformSQL getUniformSQL() {
      return null;
   }

   /**
    * Get a text description of how the query will be executed.
    * @return the text description.
    */
   @Override
   public QueryNode getQueryPlan() throws Exception {
      String sb = catalog.getString("MV Query") + ".";
      return getQueryNode(sb);
   }

   /**
    * Get the catalog.
    * @return the catalog part in sql statement if any.
    */
   @Override
   public String getCatalog() {
      return null;
   }

   /**
    * Get query icon.
    */
   @Override
   protected String getIconPath() {
      return "/inetsoft/report/gui/composition/images/mv.png";
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XMetaInfo getXMetaInfo(ColumnRef column, ColumnRef original) {
      // pick up auto-drill/format from bound query. (53346)
      try {
         if(baseQuery == null) {
            baseQuery = AssetQuery.createAssetQuery(table, AssetQuerySandbox.DESIGN_MODE, box,
                                                    false, -1, true, true);
         }

         // check for stack overflow. won't get useful meta info from MV anyway.
         if(!(baseQuery instanceof MVAssetQuery)) {
            return baseQuery.getXMetaInfo(column, original);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to create base query: " + e, e);
      }

      return null;
   }

   /**
    * Change the expression ref string
    */
   private static void rewriteCalc(ExpressionRef eref, AggregateRef[] arefs) {
      String expression = eref.getExpression();
      List<String> matchNames = new ArrayList<>();
      List<AggregateRef> used = VSUtil.findAggregate(arefs, matchNames, expression);

      for(int i = 0; i < used.size(); i++) {
         expression = expression.replace(matchNames.get(i),
            "field['" + getRefName(used.get(i), arefs) + "']");
      }

      eref.setExpression(expression);
   }

   /**
    * Change the expression ref string
    */
   private static String getRefName(AggregateRef matchRef, AggregateRef[] arefs) {
      for(AggregateRef aref : arefs) {
         if(Tool.equals(aref.getFormula(), matchRef.getFormula())) {
            DataRef mroot = DataRefWrapper.getBaseDataRef(matchRef);
            DataRef aroot = DataRefWrapper.getBaseDataRef(aref);
            DataRef abase = aroot;

            if(mroot instanceof AliasDataRef) {
               mroot = ((AliasDataRef) mroot).getDataRef();
            }

            if(aroot instanceof AliasDataRef) {
               aroot = ((AliasDataRef) aroot).getDataRef();
            }

            if(mroot.getAttribute().equals(aroot.getAttribute())) {
               if(abase instanceof AliasDataRef) {
                  return aref.getName();
               }

               return VSUtil.createAliasAgg(aref, false).getName();
            }
         }
      }

      return VSUtil.createAliasAgg(matchRef, false).getName();
   }

   /**
    * Cancel the query.
    */
   public void cancel() {
      if(executor != null) {
         executor.cancel();
      }
   }

   private boolean shouldApplyVPM() {
      TableAssembly table = getRuntimeTable();
      RuntimeMV rinfo = table.getRuntimeMV();

      if(rinfo != null && rinfo.isWSMV()) {
         MVDef def = MVManager.getManager().get(rinfo.getMV());
         return !def.getMetaData().isBypassVPM();
      }

      return false;
   }

   private TableAssembly table;
   private TableAssembly mirror;
   private List<CalculateRef> aggcalcs = new ArrayList<>();
   private List<ExpressionRef> exps = new ArrayList<>();
   private boolean prepared = false;
   private MVExecutor executor;
   private transient AssetQuery baseQuery;

   private static final Logger LOG = LoggerFactory.getLogger(MVAssetQuery.class);
}
