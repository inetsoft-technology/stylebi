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
package inetsoft.report.composition.execution;

import inetsoft.graph.internal.FirstDayComparator;
import inetsoft.mv.RuntimeMV;
import inetsoft.report.TableLens;
import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.graph.*;
import inetsoft.report.filter.DefaultTableFilter;
import inetsoft.report.internal.*;
import inetsoft.report.internal.table.ConcatTableLens;
import inetsoft.report.lens.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ChartVSAQuery, the chart viewsheet assembly query.
 *
 * @version 10.
 * @author InetSoft Technology Corp
 */
public class ChartVSAQuery extends CubeVSAQuery implements BindableVSAQuery {
   /**
    * Create a chart viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param chart the specified chart to be processed.
    * @param detail <tt>true</tt> if show detail, <tt>false</tt> otherwise.
    */
   public ChartVSAQuery(ViewsheetSandbox box, String chart, boolean detail) {
      super(box, chart, detail);
   }

   @Override
   protected VSAssembly getAssembly() {
      if(assembly != null) {
         return assembly;
      }

      return assembly = (VSAssembly) super.getAssembly().clone();
   }

   /**
    * Get the data. For show detail, the detail table lens will be returned,
    * otherwise a data set will be returned if any.
    * @return the data of the query.
    */
   @Override
   public Object getData() throws Exception {
      ChartVSAssembly brushsrc = box.getBrushingChart(vname);

      // for show detail, just return table lens
      if(isDetail()) {
         return getTableLens();
      }
      else if(!brush) {
         // for all data without brush, if no brushing, return null
         if(brushsrc == null) {
            return null;
         }

         // if this is the tip view of brush chart, return null
         String tip = brushsrc.getChartInfo().getRuntimeTipView();

         if(vname.equals(tip)) {
            return null;
         }
      }

      TableLens data = getTableLens();
      ChartVSAssembly chart = (ChartVSAssembly) getAssembly();

      if(data == null || chart == null) {
         return null;
      }

      if(data instanceof TableFilter2) {
         TableLens base = ((TableFilter2) data).getTable();

         if(base instanceof XNodeMetaTable) {
            ChartMetaTableGenerator gen = new ChartMetaTableGenerator((XNodeMetaTable) base);
            base = gen.generate(chart);
            ((TableFilter2) data).setTable(base);
         }
      }

      VSChartInfo info = chart.getVSChartInfo();
      VSDataRef[] refs = info.getRTFields();
      refs = (VSDataRef[]) ArrayUtils.addAll(refs, info.getDcTempGroups());
      DateFormat[] fmts = new DateFormat[data.getColCount()];
      boolean hasPeriod = false;
      Set<String> dayOfWeekCols = new HashSet<>();
      int firstDayOfWeek = Tool.getFirstDayOfWeek();
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(data, true);

      for(int i = 0; i < refs.length; i++) {
         int col = Util.findColumn(columnIndexMap, refs[i].getFullName());

         if(col >= 0) {
            XDimensionRef pdim = getPeriodDimRef(info, refs[i].getFullName());

            if(pdim != null && pdim.getDateLevel() >= 0) {
               hasPeriod = true;
               fmts[col] = XUtil.getDefaultDateFormat(pdim.getDateLevel());
            }
         }

         if(firstDayOfWeek != Calendar.SUNDAY &&refs[i] instanceof VSDimensionRef &&
            refs[i].getFullName().contains("DayOfWeek"))
         {
            dayOfWeekCols.add(refs[i].getFullName());
         }
      }

      // convert date period to date type so formatting would work
      if(hasPeriod) {
         data = new PeriodDateTableLens(data, fmts);
      }

      // @by stone, fix bug1320059405682
      ChartRef path = info.getRTPathField();
      int ctype = info.getRTChartType();

      if(path instanceof VSChartAggregateRef) {
         ((VSChartAggregateRef) path).setSupportsLine(GraphTypes.supportsLine(ctype, info));
      }

      // @by davyc, use this logic to handle sort by value will cause multi
      // threading problem, at same time, if group is merged, we will not
      // generate SummaryFilter, this mechanism still have problem, if we
      // want to support it:
      // 1: set sort by value info to GroupRef.order
      // 2: AssetQuery support sort by value
      // 3: create SummaryFilter event group merged
      // sortByVal(data);
      VSDataSet dataset = new VSDataSet(data, refs);
      dataset.setLinkVarTable(box.getVariableTable());
      dataset.setLinkSelections(box.getSelections());
      checkMaxRowLimit(data);

      if(subcols != null) {
         int row = 0;
         TableLens[] subs = {};

         SetTableLens union = (SetTableLens) Util.getNestedTable(data, UnionTableLens.class);

         if(union != null) {
            subs = union.getTables();
         }
         else {
            ConcatTableLens concat = (ConcatTableLens)
               Util.getNestedTable(data, ConcatTableLens.class);

            if(concat != null) {
               subs = concat.getTables();
            }
         }

         int rowCount = data.getRowCount();

         for(int i = 0; i < subs.length; i++) {
            TableLens tbl = subs[i];
            tbl.moreRows(TableLens.EOT);
            int cnt = tbl.getRowCount() - 1;
            int endRow = row + cnt;

            if(endRow > data.getRowCount() - 1) {
               endRow = data.getRowCount() - 1;
            }

            int[] range = {row, endRow};
            dataset.setSubRange(subcols[i], range);
            row += cnt;

            if(row >= rowCount) {
               break;
            }
         }
      }

      // For Feature #16158, Use FirstDayComparator depending on
      // the locale configured.
      if(firstDayOfWeek != Calendar.SUNDAY && dayOfWeekCols != null) {
         for(String mondayCol : dayOfWeekCols) {
            if(!(dataset.getComparator(mondayCol) instanceof ValueOrderComparer)) {
               dataset.setComparator(mondayCol,
                  new FirstDayComparator(dataset.getComparator(mondayCol)));
            }
         }
      }

      return dataset;
   }

   @Override
   public TableAssembly createBindableTable() {
      for_bindable = true;

      try {
         return getTableAssembly(false);
      }
      catch(Exception ex) {
         LOG.warn("create chart table assembly error", ex);
      }

      return null;
   }

   /**
    * Set whether to apply brush selection.
    */
   public void setBrush(boolean brush) {
      this.brush = brush;
   }

   /**
    * Test if brush selection should be applied.
    */
   public boolean isBrush() {
      return brush;
   }

   /**
    * Set whether to ignore runtime conditions (zoom, selection, ...).
    */
   public void setIgnoreRuntimeCondition(boolean no_filter) {
      this.no_filter = no_filter;
   }

   /**
    * Set whether to show empty dimension values in OLAP.
    */
   public void setNoEmpty(boolean no_empty) {
      this.no_empty = no_empty;
   }

   /**
    * Get the predefined dimension/measure information.
    */
   @Override
   public AggregateInfo getAggregateInfo() {
      return ((ChartVSAssembly) getAssembly()).getAggregateInfo();
   }

   /**
    * Set the brush/zoom selection from chart to the specified table.
    * @param table the specified table.
    */
   @Override
   protected void setSharedCondition(ChartVSAssembly chart, TableAssembly table) {
      ChartVSAssembly cassembly = (ChartVSAssembly) getAssembly();
      super.setSharedCondition(chart, table, brush);

      // set zoom selection to chart if any
      if(!isDetail()) {
         ColumnSelection cols = null;

         if(AssetUtil.isCubeTable(table)) {
            TableAssembly cubeTable = getBaseTable(table);
            cols = cubeTable.getColumnSelection();
         }
         else {
            cols = table.getColumnSelection();
         }

         ConditionList conds = cassembly.getZoomConditionList(cols);
         conds = VSAQuery.replaceGroupValues(conds, cassembly);

         if(conds != null && !conds.isEmpty()) {
            zoomTable = table.getName();
            zoomConds = conds;
            /*
            ConditionListWrapper pwrapper = table.getPreRuntimeConditionList();

            if(pwrapper != null && !pwrapper.isEmpty()) {
               List<ConditionList> list = new ArrayList<ConditionList>();
               list.add(pwrapper.getConditionList());
               list.add(conds);
               conds = AssetUtil.mergeConditionList(list, JunctionOperator.AND);
            }

            table.setPreRuntimeConditionList(conds);
            */
         }
      }
   }

   /**
    * Get the VSTableAssembly, do not filter the table columns if not analysis.
    */
   @Override
   protected TableAssembly getVSTableAssembly(String name, boolean analysis) {
      TableAssembly assembly = super.getVSTableAssembly(name, analysis);
      validateCalculateRef(getWorksheet(), name);
      return assembly;
   }

   /**
    * Get the default column selection.
    * @return the default column selection.
    */
   @Override
   public ColumnSelection getDefaultColumnSelection() throws Exception {
      ChartVSAssembly cassembly = (ChartVSAssembly) getAssembly();
      SourceInfo sinfo = cassembly.getSourceInfo();

      if(sinfo == null || sinfo.isEmpty()) {
         return new ColumnSelection();
      }

      ColumnSelection columns = getDefaultAssetColumnSelection();
      return VSUtil.getVSColumnSelection(columns);
   }

   /**
    * Get the table.
    * @return the table of the query.
    */
   @Override
   public TableLens getTableLens() throws Exception {
      TableAssembly table;
      final ViewsheetSandbox box = this.box;
      box.lockRead();

      try {
         table = getTableAssembly(false);

         if(table == null) {
            return null;
         }

         GroupRef[] preservedGroups = table.getAggregateInfo().getGroups();

         if(no_filter) {
            WorksheetWrapper wrapper = new WorksheetWrapper(new Worksheet());
            // To get all depended assemblies from worksheet, avoid
            // assemblies can not be found in wrapper if these assemblies are
            // not tables but still be depended by table
            Worksheet ws = getWorksheet();
            ChartVSAssembly cassembly = (ChartVSAssembly) getAssembly();
            SourceInfo sinfo = cassembly.getSourceInfo();
            String name = sinfo == null || sinfo.isEmpty() ? null : sinfo.getSource();

            if(name != null && name.length() != 0) {
               Assembly assembly = ws.getAssembly(name);
               Assembly[] arr = AssetUtil.getDependedAssemblies(ws, assembly, false);

               for(int i = 0; i < arr.length; i++) {
                  arr[i] = (Assembly) arr[i].clone();
                  wrapper.addAssembly((WSAssembly) arr[i]);
               }
            }

            if(removeRuntimeConditions(table, wrapper)) {
               table = (TableAssembly) wrapper.getAssembly(table.getName());
            }
         }

         TableLens data = getTableLens(table);
         VSChartInfo cinfo = ((ChartVSAssembly) getAssembly()).getVSChartInfo();

         if(isApplyMaxRows(cinfo)) {
            if(!data.moreRows(DOTPLOT_MAXROWS + 1) && data.moreRows(DOTPLOT_MAXROWS)) {
               Tool.addUserMessage(Catalog.getCatalog().getString("common.limited.rows", DOTPLOT_MAXROWS));
            }
         }

         if(!isDetail()) {
            //preserve group data otherwise lost when getting data
            if(cinfo.isAppliedDateComparison()) {
               AggregateInfo preservedAggr = (AggregateInfo) table.getAggregateInfo().clone();
               preservedAggr.setGroups(preservedGroups);
               data = getDcMergeDateTableLens(data, table, preservedAggr);
            }
            else {
               data = getDcMergeDateTableLens(data, table, null);
            }
         }

         if(!isDetail() && data != null && Util.isTimeoutTable(data)) {
            String info = Catalog.getCatalog().getString("common.timeout",
                         getAssembly().getName());
            throw new RuntimeException(info);
         }

         return data;
      }
      finally {
         box.unlockRead();
      }
   }

   /**
    * Remove all runtime conditions from the table assembly.
    */
   private boolean removeRuntimeConditions(TableAssembly table, WorksheetWrapper wrapper) {
      boolean removed = table.getPreRuntimeConditionList() != null ||
         table.getPostRuntimeConditionList() != null;
      table = (TableAssembly) table.clone();

      if(table instanceof ComposedTableAssembly) {
         ComposedTableAssembly ctable = (ComposedTableAssembly) table;
         TableAssembly[] children = ctable.getTableAssemblies(true);

         for(int i = 0; i < children.length; i++) {
            removed = removeRuntimeConditions(children[i], wrapper) || removed;
         }
      }

      table.setPreRuntimeConditionList(null);
      table.setPostRuntimeConditionList(null);
      wrapper.removeAssembly(table.getName());
      wrapper.addAssembly(table);

      return removed;
   }

   /**
    * Execute a table assembly and return a table lens.
    * @param table the specified table assembly.
    * @return the table lens as the result.
    */
   @Override
   protected TableLens getTableLens(TableAssembly table) throws Exception {
      table.setProperty("noEmpty", no_empty + "");
      TableLens lens = super.getTableLens(table);

      if(lens instanceof VSCubeTableLens) {
         return lens;
      }

      ChartVSAssembly cassembly = (ChartVSAssembly) getAssembly();

      if(lens == null || cassembly == null) {
         return null;
      }

      VSChartInfo info = cassembly.getVSChartInfo();
      boolean[] shrink = new boolean[lens.getColCount()];
      boolean needshrink = false;

      for(int i = 0; i < lens.getColCount(); i++) {
         Object hd = Util.getHeader(lens, i);
         String header = hd == null ? null : hd.toString();
         DataRef ref = GraphUtil.getChartRef(info, header, true);

         if(ref instanceof VSDimensionRef) {
            VSDimensionRef dim = (VSDimensionRef) ref;
            String type = dim.getDataRef().getDataType();
            XNamedGroupInfo groups = dim.getNamedGroupInfo();

            if((XSchema.DOUBLE.equals(type) || XSchema.FLOAT.equals(type)) &&
               groups != null && !groups.isEmpty())
            {
               shrink[i] = true;
               needshrink = true;
            }
         }
      }

      if(needshrink) {
         lens = new ShrinkNumberTableLens(lens);

         for(int i = 0; i < shrink.length; i++) {
            ((ShrinkNumberTableLens) lens).setShrinkColumn(i, shrink[i]);
         }
      }

      lens = DateComparisonUtil.getMergePartTableLens(cassembly, getViewsheet(), lens, isDetail());

      return lens;
   }

   /**
    * Append conds to zoom condition.
    */
   private void mergeZoomConds(TableAssembly table, final SubColumns columns) {
      if(for_bindable) {
         return;
      }

      if(zoomConds == null || zoomConds.isEmpty() || zoomTable == null) {
         return;
      }

      while(table != null) {
         if(zoomTable.equals(table.getName())) {
            break;
         }

         table = table instanceof MirrorTableAssembly ?
            ((MirrorTableAssembly) table).getTableAssembly() : null;
      }

      if(table == null) {
         return;
      }

      AssetUtil.Filter filter = columns == null ? null :
         new AssetUtil.Filter() {
            @Override
            public boolean keep(DataRef attr) {
               String name = attr.getName();

               if(name == null || columns.lazyContains(name)) {
                  return true;
               }

               // try match named group ref, because named ref will be
               // replaced in VSAQuery.replaceGroupValues
               Set groups = columns.getGroupRefs();

               if(groups == null) {
                  return false;
               }

               for(Object obj : groups) {
                  if(obj instanceof VSDimensionRef) {
                     VSDimensionRef dim = (VSDimensionRef) obj;

                     if(dim.isNameGroup()) {
                        ColumnRef acref = (ColumnRef) dim.getDataRef();

                        if(acref != null) {
                           if(isSameColumn(acref, name)) {
                              return true;
                           }
                        }
                     }
                  }
               }

               return false;
            }
         };

      ConditionList conds = ConditionUtil.filter(zoomConds, filter);

      if(conds == null || conds.isEmpty()) {
         return;
      }

      ConditionListWrapper pwrapper = table.getPreRuntimeConditionList();

      if(pwrapper != null && !pwrapper.isEmpty()) {
         List<ConditionList> list = new ArrayList<>();
         list.add(pwrapper.getConditionList());
         list.add(conds);
         conds = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
      }

      table.setPreRuntimeConditionList(conds);
   }

   /**
    * Get the table assembly that contains binding info.
    * @param analysis <tt>true</tt> if is for analysis, <tt>false</tt> for
    * runtime.
    */
   private TableAssembly getTableAssembly(boolean analysis) throws Exception {
      ChartVSAssembly cassembly = (ChartVSAssembly) getAssembly();

      if(cassembly == null) {
         return null;
      }

      SourceInfo source = cassembly.getSourceInfo();

      if(source == null || source.isEmpty()) {
         return null;
      }

      TableAssembly table = createTableAssembly(analysis);
      VSChartInfo cinfo = cassembly.getVSChartInfo();
      Map<Set,Set> groups = cinfo.getRTFieldGroups(cinfo.getDcTempGroups());
      ChartVSAssemblyInfo assemblyInfo = cassembly.getChartInfo();
      ChartDescriptor desc = assemblyInfo.getRTChartDescriptor() == null ?
         assemblyInfo.getChartDescriptor() : assemblyInfo.getRTChartDescriptor();

      desc.setSortOthersLast(cinfo);

      if(table == null || isDetail()) {
         mergeZoomConds(table, null);
         return table;
      }

      if(groups.size() <= 1) {
         subcols = null;
         mergeZoomConds(table, null);

         table = applyDiscreteAggregates(table, cinfo);

         if(!for_bindable) {
            table = mergePostCondition(table);
         }

         return table;
      }

      // multi aesthetic may create different grouping and aggregate because of
      // per aggregate aesthetic binding.
      // create different table for each grouping and concatenate the
      // with a union table
      TableAssembly[] subs = new TableAssembly[groups.size()];
      subcols = new SubColumns[subs.length];
      String ozoomTable = zoomTable;
      TableAssemblyOperator[] ops = new TableAssemblyOperator[subs.length - 1];
      Worksheet ws = table.getWorksheet();
      int idx = 0;

      for(Set group : groups.keySet()) {
         // use list instead of set, because date dimension ref for different
         // date level is equals
         List all = new ArrayList(group);
         Set aggrs = groups.get(group);
         all.addAll(aggrs);
         subcols[idx] = new SubColumns(group, aggrs);

         for(Object ref : all) {
            if(ref instanceof ChartAggregateRef) {
               GraphUtil.addSubCol((ChartAggregateRef) ref, subcols[idx]);
            }
         }

         subs[idx] = (TableAssembly) table.clone();
         subs[idx].getInfo().setName(AssetUtil.getNextName(ws, subs[idx].getName() + "_sub"));
         ws.addAssembly(subs[idx]);
         TableAssembly discreteTbl = applyDiscreteAggregates(subs[idx], cinfo);
         TableAssembly subTbl = subs[idx];

         if(discreteTbl != subs[idx]) {
            subs[idx] = discreteTbl;
            // if discrete is applied, the first sub-table is the one containing the
            // original sub table query, and the second is the discrete measures.
            subTbl = ((RelationalJoinTableAssembly) discreteTbl).getTableAssemblies()[0];
            // make sure the column selection is up-to-date
            AssetEventUtil.initColumnSelection(box.getAssetQuerySandbox(), subs[idx]);
            ws.addAssembly(discreteTbl);
         }
         else {
            discreteTbl = null;
         }

         if(table.getName().equals(zoomTable)) {
            zoomTable = subs[idx].getName();
         }

         mergeZoomConds(subs[idx], subcols[idx]);
         zoomTable = ozoomTable;

         ColumnSelection cols = subs[idx].getColumnSelection(true);
         cols = cols.clone();
         // AggregateInfo for multi-style query is on subTbl if discrete added a join
         // on top (subs[idx] is the join table at this point, where subTbl is the base table).
         List<String> removed = filterColumns(subTbl, all);
         AggregateInfo ainfo = subTbl.getAggregateInfo();
         fixRanking(subTbl, ainfo, group);

         if(!for_bindable) {
            subs[idx] = mergePostCondition(subs[idx]);
            ws.addAssembly(subs[idx]);
         }

         TableAssembly osub = subs[idx];

         // if some aggregates are removed from the original aggregate info, create a mirror
         // and fill the removed aggregate column with nulls.
         if(removed.size() > 0) {
            // if in a join table, replace the child with a new table
            if(discreteTbl != null) {
               TableAssembly[] children =
                  ((RelationalJoinTableAssembly) discreteTbl).getTableAssemblies();
               children[0] = createFakeMirror(ws, subTbl, cols, removed);
               ws.addAssembly(children[0]);
               ((RelationalJoinTableAssembly) discreteTbl).setTableAssemblies(children);
            }
            // otherwise replace the top table.
            else {
               subs[idx] = createFakeMirror(ws, subs[idx], cols, removed);
               ws.addAssembly(subs[idx]);
            }
         }

         if(idx < ops.length) {
            ops[idx] = new TableAssemblyOperator(
               null, null, null, null, TableAssemblyOperator.UNION);
            ops[idx].getOperator(0).setDistinct(false);
         }

         // @by larryl, since the union is created on the fly, there will be
         // no corresponding MV table so it will not be transformed
         // (AssetQuery.createAssetQuery -> MVTransformer.transform). This
         // causes selections below the sub-table lost since the sub table
         // will not be transformed if it's not the root
         subs[idx].setProperty("MVRoot", "true");

         subTables.add(osub.getName());
         idx++;
      }

      ws.removeAssembly(table.getName());
      table = new ConcatenatedTableAssembly(ws, table.getName(), subs, ops);
      table.setSQLMergeable(false);
      ((ConcatenatedTableAssembly) table).setHierarchical(false);
      ws.addAssembly(table);
      AssetEventUtil.initColumnSelection(box.getAssetQuerySandbox(), table);

      return table;
   }

   // for discrete aggregate, we group them by the dimensions preceeding it on the same side
   // (x or y). the result is joined on the original table.
   // this is necessary since the discrete measure should only be grouped by the dimension
   // on the same direction. for example, with binding:
   // y: dim1
   // x: dim2 discrete_m1 m2
   // discrete_m1 should be calculated with group by dim2 (no dim1)
   // the result is then merged to the main query (group by dim1, dim2, aggregate m2)
   private TableAssembly applyDiscreteAggregates(TableAssembly table, VSChartInfo cinfo)
         throws Exception
   {
      Map<Set,Set> groupaggrs = cinfo.getRTDiscreteAggregates();

      if(groupaggrs == null || groupaggrs.isEmpty()) {
         return table;
      }

      if("true".equals(table.getProperty("chart.aoa.mirror"))) {
         table = ((MirrorTableAssembly) table).getTableAssembly();
      }

      final ColumnSelection cols = table.getColumnSelection();
      Worksheet ws = table.getWorksheet();
      TableAssembly[] tables = new TableAssembly[groupaggrs.size() + 1];
      TableAssemblyOperator[] ops = new TableAssemblyOperator[groupaggrs.size()];
      int idx = 1;

      tables[0] = table;
      table.setProperty("discrete_sub_table", "true");

      for(Set groups: groupaggrs.keySet()) {
         MirrorTableAssembly tbl2 = new MirrorTableAssembly();
         tables[idx] = tbl2;
         tbl2.setTableAssemblies(new TableAssembly[] { table });
         tbl2.getInfo().setName(table.getName() + "_discrete_" + idx);
         tbl2.setProperty("discrete_sub_table", "true");
         ws.addAssembly(tbl2);
         AssetEventUtil.initColumnSelection(box.getAssetQuerySandbox(), tbl2);

         ColumnSelection cols2 = tbl2.getColumnSelection();
         AggregateInfo ainfo = new AggregateInfo();
         groups.stream()
            .map(g -> ((VSDimensionRef) g).createGroupRef(cols))
            .forEach(g -> ainfo.addGroup((GroupRef) g));

         groupaggrs.get(groups).stream()
            .map(a -> {
               AggregateRef ref = ((VSAggregateRef) a).createAggregateRef(cols);
               // always aggregate for discrete regardless of isAggregate() of original aggr
               // otherwise the sql will be wrong since select clause would not have the
               // column aggregated
               AggregateFormula formula = ((VSAggregateRef) a).getFormula();
               // the 'table' is already aggregated so should handle AoA. (49826, 49837, 50179)
               AggregateFormula pformula = formula.getParentFormula();

               // this is not strict correct since the base aggregate are not child aggregates.
               // but fully handling AoA requires rewriting the base table generation logic, by
               // handling discrete measures as part of createTableAssembly(), or use the child
               // aggregates in createTableAssembly, and use parent formula in discrete, which
               // still leaves the possibility of formulas that don't support AoA.
               if(pformula == null) {
                  pformula = VSUtil.requiresTwoColumns(formula) ? AggregateFormula.MAX : formula;
               }

               ref.setFormula(pformula);
               return ref;
            })
            .forEach(a -> {
               AggregateRef aref = (AggregateRef) a;
               DataRef ref2 = cols2.getAttribute(aref.getDataRef().getName());

               if(ref2 == null) {
                  cols2.addAttribute(aref.getDataRef());
               }
               else {
                  aref.setDataRef(ref2);
               }

               ainfo.addAggregate(aref);
            });

         tbl2.setAggregateInfo(ainfo);

         ops[idx - 1] = new TableAssemblyOperator();

         for(Object group: groups) {
            TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
            op.setLeftTable(table.getName());
            op.setRightTable(tbl2.getName());
            op.setLeftAttribute((DataRef) group);
            op.setRightAttribute((DataRef) group);

            ops[idx - 1].addOperator(op);
         }

         idx++;
      }

      String joinedName = table.getName() + "_discrete_joined_table_";
      RelationalJoinTableAssembly joined = new RelationalJoinTableAssembly(ws, joinedName,
                                                                           tables, ops);
      AssetEventUtil.initColumnSelection(box.getAssetQuerySandbox(), joined);
      ColumnSelection jcols = joined.getColumnSelection(false);
      int joinedIdx = table.getColumnSelection(true).getAttributeCount();
      AggregateInfo ainfo = getAggregateInfo();

      for(Set groups: groupaggrs.keySet()) {
         // hide joined dim columns
         for(int i = 0; i < groups.size(); i++) {
            ColumnRef cref = (ColumnRef) jcols.getAttribute(joinedIdx++);
            cref.setVisible(false);
         }

         Set aggrs = groupaggrs.get(groups);
         Set dup = new HashSet();

         // set alias for joined discrete aggregates
         for(Object aggr : aggrs) {
            VSAggregateRef aref = (VSAggregateRef) aggr;

            // AggregateInfo.add() ignore duplicates, so we should ignore it here too
            if(dup.contains(aref.getFullName())) {
               continue;
            }

            dup.add(aref.getFullName());
            ColumnRef cref = (ColumnRef) jcols.getAttribute(joinedIdx++);

            if(ainfo != null && ainfo.getGroup(cref.getAttribute()) == null) {
               cref.setAlias(aref.getFullName());
            }
         }
      }

      return joined;
   }

   /**
    * Filter useless columns.
    */
   private List<String> filterColumns(TableAssembly table, List all) {
      AggregateInfo ainfo = table.getAggregateInfo();
      ColumnSelection columns = table.getColumnSelection();
      List<String> removed = new ArrayList<>();

      // has group aggregate? just remove columns from it
      if(ainfo != null && !ainfo.isEmpty()) {
         for(int i = ainfo.getGroupCount() - 1; i >= 0; i--) {
            GroupRef gref = ainfo.getGroup(i);
            String name = gref.getName();
            boolean find = false;

            for(Object cref : all) {
               if(isSameColumn((DataRef) cref, name, gref.getEntity())) {
                  find = true;
                  break;
               }
            }

            if(!find) {
               ainfo.removeGroup(i);
               removed.add(name);
            }
         }

         for(int i = ainfo.getAggregateCount() - 1; i >= 0; i--) {
            AggregateRef aref = ainfo.getAggregate(i);
            String name = aref.getName();
            boolean find = false;

            for(Object cref : all) {
               // @ankitmathur For bug1414445012792, If the 'all' DataRef list
               // contains an Viewsheet Aggregate Expression, then traverse
               // through the list of all fields which are used by/in the
               // Expression. If these fields exist in the list of aggregates,
               // do not remove them from the list.
               if(cref instanceof VSAggregateRef && ((VSAggregateRef)
                  cref).getDataRef() instanceof CalculateRef)
               {
                  CalculateRef calRefs = (CalculateRef)
                     ((VSAggregateRef) cref).getDataRef();
                  Enumeration calcElems = calRefs.getExpAttributes();

                  while(calcElems.hasMoreElements()) {
                     AttributeRef calcRef = (AttributeRef)
                        calcElems.nextElement();

                     if(aref.getName().contains(calcRef.getName())) {
                        find = true;
                        break;
                     }
                  }
               }

               if(isSameColumn((DataRef) cref, name)) {
                  find = true;
                  break;
               }
            }

            if(!find) {
               ainfo.removeAggregate(i);
               removed.add(name);
            }
         }
      }
      // no grouping? filter column selection
      else {
         ColumnSelection pubs = table.getColumnSelection(true);

         for(int i = columns.getAttributeCount() - 1; i >= 0; i--) {
            ColumnRef column = (ColumnRef) columns.getAttribute(i);
            boolean find = false;

            for(Object cref : all) {
               if(isSameColumn((DataRef) cref, column.getName())) {
                  find = true;
                  break;
               }
            }

            if(!find) {
               if(pubs.findAttribute(column) != null) {
                  removed.add(column.getName());
               }

               column.setVisible(false);
            }
         }

         table.setColumnSelection(columns);
      }

      table.setAggregateInfo(ainfo);
      table.resetColumnSelection();

      String mname = Assembly.TABLE_VS + vname + "_mirror";

      if(table.getName().startsWith(mname)) {
         if(table instanceof MirrorTableAssembly) {
            MirrorTableAssembly mtable = (MirrorTableAssembly) table;
            Worksheet ws = table.getWorksheet();
            table = mtable.getTableAssembly();

            if(!table.getAggregateInfo().isEmpty()) {
               table = (TableAssembly) table.clone();
               String oname = table.getName();
               table.getInfo().setName(
                  AssetUtil.getNextName(ws, table.getName() + "_fixed"));
               String nname = table.getName();
               ws.addAssembly(table);
               filterColumns(table, all);
               mtable.setTableAssemblies(new TableAssembly[] {table});
               mtable.renameDepended(oname, nname);
            }
         }
      }

      return removed;
   }

   /**
    * Fix ranking info and sort by value info and so on.
    */
   private void fixRanking(TableAssembly table, AggregateInfo ainfo, Set dims) {
      if(table == null || ainfo == null) {
         return;
      }

      ConditionListWrapper wrapper = table.getRankingConditionList();

      if(wrapper == null || wrapper.isEmpty()) {
         return;
      }

      ConditionList rconds = wrapper.getConditionList();

      for(int i = rconds.getSize() - 1; i >= 0; i -= 2) {
         ConditionItem item = rconds.getConditionItem(i);
         RankingCondition tcond = (RankingCondition) item.getXCondition();
         DataRef attr = item.getAttribute();
         DataRef aggregate = tcond.getDataRef() != null ? tcond.getDataRef() : attr;

         if(attr != null && ainfo.getGroup(attr) == null ||
            aggregate != null && ainfo.getAggregate(aggregate) == null)
         {
            // for multi-style chart where same dim is bound to color of two measures, with
            // each color dim having a different ranking condition, the ranking condition
            // in the base table (which is used to create the sub tables from each group/aggregate
            // combination) contains the ranking of the first measure's binding. this logic
            // applies the individual dim ranking. (60085)
            if(!updateDimRankingCondition(dims, tcond, attr, ainfo)) {
               rconds.remove(i);
               rconds.remove(i == 0 ? 0 : i - 1);
            }
            continue;
         }

         boolean invalidConds = dims.stream()
            .filter((Object ref) -> Tool.equals(((ChartDimensionRef) ref).getFullName(), attr.getAttribute()))
            .allMatch((Object ref) -> ((ChartDimensionRef) ref).getRankingOption() == XCondition.NONE);

         if(invalidConds) {
            rconds.remove(i);
            rconds.remove(i == 0 ? 0 : i - 1);
         }
         else {
            updateDimRankingCondition(dims, tcond, attr, ainfo);
         }
      }

      rconds.trim();
   }

   // find the ranking defined in dims and update the RankingCondition with the info.
   private static boolean updateDimRankingCondition(Set dims, RankingCondition tcond, DataRef attr,
                                                    AggregateInfo ainfo) {
      for(Object ref : dims) {
         if(((DataRef) ref).getAttribute().equals(attr.getAttribute())) {
            ChartDimensionRef dim = (ChartDimensionRef) ref;

            if(dim.getRankingOption() != XCondition.NONE) {
               tcond.setOperation(dim.getRankingOption());
               tcond.setN(dim.getRankingN());
               AggregateRef aggr = Arrays.stream(ainfo.getAggregates())
                  .filter(a -> a.getAttribute().equals(dim.getRankingCol()))
                  .findFirst().orElse(null);

               if(aggr != null) {
                  tcond.setDataRef(aggr);
               }
            }

            return true;
         }
      }

      return false;
   }

   private TableAssembly createFakeMirror(Worksheet ws, TableAssembly sub,
                                          ColumnSelection scols, List removed)
   {
      String sname = sub.getName();
      MirrorTableAssembly fakeMirror = new MirrorTableAssembly(ws,
         sname + "_mirror", null, false, sub);
      ColumnSelection mcols = new ColumnSelection();

      for(int i = 0; i < scols.getAttributeCount(); i++) {
         ColumnRef scolumn = (ColumnRef) scols.getAttribute(i);
         String sattr = scolumn.getAttribute();
         DataRef attr = AssetUtil.getOuterAttribute(sname, scolumn);
         String dtype = scolumn.getDataType();
         ColumnRef column = new ColumnRef(attr);
         column.setDataType(dtype);
         mcols.addAttribute(column);

         if(attr instanceof AttributeRef) {
            ((AttributeRef) attr).setRefType(scolumn.getRefType());
            ((AttributeRef) attr).setDefaultFormula(scolumn.getDefaultFormula());
         }

         if(contains(removed, sattr)) {
            boolean cube = (attr.getRefType() & DataRef.CUBE) == DataRef.CUBE;
            ExpressionRef expr = new ExpressionRef("", null, cube ?
               scolumn.getCaption() : attr.getAttribute());
            expr.setExpression("null");
            expr.setRefType(attr.getRefType());
            column.setDataRef(expr);
         }
      }

      fakeMirror.setColumnSelection(mcols);
      ws.addAssembly(fakeMirror);
      return fakeMirror;
   }

   /**
    * Check if a name is on the list.
    */
   private boolean contains(List<String> removed, String name) {
      for(String rname : removed) {
         if(isSameColumn(rname, name)) {
            return true;
         }
      }

      return false;
   }

   private boolean isSameColumn(DataRef ref, String name) {
      return isSameColumn(ref, name, null);
   }

   private boolean isSameColumn(DataRef ref, String name, String entity) {
      name = NamedRangeRef.getBaseName(name, entity);
      boolean same = isSameColumn(NamedRangeRef.getBaseName(GraphUtil.getFullNameNoCalc(ref)), name);

      if(!same) {
         String calc = aggr2calc.get(name);

         if(calc == null) {
            int idx = name.lastIndexOf(".");

            if(idx >= 0) {
               name = name.substring(idx + 1);
               calc = aggr2calc.get(name);
            }

            if(calc != null) {
               return isSameColumn(ref, calc);
            }
         }
      }

      return same;
   }

   /**
    * Check if two names are same column.
    */
   private static boolean isSameColumn(String fname, String name) {
      fname = GraphUtil.getFullNameNoCalc(fname);
      name = GraphUtil.getFullNameNoCalc(name);
      return fname.equals(name);
   }

   /**
    * Create the base plain table assembly. The assembly is used to merge with
    * view selections to produce the final query.
    * @return the created base plain table assembly.
    */
   private TableAssembly createTableAssembly(boolean analysis) throws Exception {
      TableAssembly table = createBaseTableAssembly0(analysis);

      if(table == null) {
         return null;
      }

      boolean isCube = AssetUtil.isCubeTable(table);

      if(isDetail() && !isCube && !box.isMVEnabled(true)) {
         // same as table, if use mv to get detail data, just use it
         if(table.getRuntimeMV() == null) {
            table = findDetailTable(table);
         }
      }

      ChartVSAssembly cassembly = (ChartVSAssembly) getAssembly();
      ColumnSelection cols = table.getColumnSelection();
      VSChartInfo cinfo = cassembly.getVSChartInfo();
      AggregateInfo ainfo = table.getAggregateInfo();
      ChartVSAssemblyInfo assemblyInfo = cassembly.getChartInfo();
      ChartDescriptor desc = assemblyInfo.getRTChartDescriptor() == null ?
         assemblyInfo.getChartDescriptor() : assemblyInfo.getRTChartDescriptor();
      boolean oainfoEmpty = ainfo.isEmpty();
      ConditionList rconds = new ConditionList();

      // for detail
      ColumnSelection cols2 = new ColumnSelection();
      SortInfo dsorts = new SortInfo();
      SortInfo sorts = new SortInfo();

      int counter = 0;
      VSDataRef[] refs = cinfo.getRTFields();
      VSDataRef[] tempRefs = cinfo.getDcTempGroups();
      List<String> dcTempRefNames = new ArrayList<>();

      if(tempRefs != null && tempRefs.length > 0) {
         refs = (VSDataRef[]) ArrayUtils.addAll(refs, tempRefs);
         Arrays.stream(tempRefs).forEach(r -> dcTempRefNames.add(r.getFullName()));
      }

      DataRef prangeref = null;

      if(refs.length == 0) {
         return null;
      }

      Arrays.sort(refs, new VComparator(desc != null && !desc.isRankPerGroup()));

      // fix bug1320832033360. maintain column sequence duplicate.
      Set<ColumnRef> colsSet = new HashSet<>();

      applyFunnelSort(cinfo, refs);

      for(int i = 0; i < refs.length; i++) {
         // dimension ref
         if(refs[i] instanceof VSDimensionRef) {
            VSDimensionRef vdim = (VSDimensionRef) refs[i];
            int size = cols.getAttributeCount();
            GroupRef group = vdim.createGroupRef(cols);

            if(group != null) {
               String[] dates = vdim.getDates();
               boolean period = dates != null && dates.length > 0;

               if(period && cols.getAttributeCount() > size) {
                  prangeref = cols.getAttribute(size);
               }

               ColumnRef column = (ColumnRef) group.getDataRef();
               int index = cols.indexOfAttribute(column);
               DataRef ref = column.getDataRef();

               if(ref instanceof DateRangeRef) {
                  DataRef bref = ((DateRangeRef) ref).getDataRef();
                  bref = cols.findAttribute(bref);

                  if(bref instanceof ColumnRef) {
                     ((DateRangeRef) ref).setOriginalType(bref.getDataType());
                  }
               }

               ColumnRef rcolumn = null; // range column for detail

               // maintain column sequence
               if(index >= 0) {
                  column = (ColumnRef) cols.getAttribute(index);
                  cols.removeAttribute(index);
               }

               // for detail, to show original value seems to be better
               if(isDetail() && (ref instanceof DateRangeRef)) {
                  DateRangeRef range = (DateRangeRef) ref;
                  ref = range.getDataRef();
                  int index2 = cols.indexOfAttribute(ref);

                  if(index2 >= 0) {
                     rcolumn = (ColumnRef) cols.getAttribute(index2);
                     rcolumn.setVisible(true);
                  }
               }

               // do not add period column for show detail
               if(!isDetail() || !period) {
                  if(!(isDetail() && (column.getDataRef() instanceof NamedRangeRef) &&
                       (column.getRefType() & DataRef.CUBE) == DataRef.CUBE))
                  {
                     if(!dcTempRefNames.contains(column.getName())) {
                        column.setVisible(rcolumn == null);
                     }

                     if(isDetail() && column.getDataRef() instanceof NamedRangeRef) {
                        NamedRangeRef namedRangeRef = (NamedRangeRef) column.getDataRef();

                        if(namedRangeRef.getNamedGroupInfo() instanceof DCNamedGroupInfo) {
                           continue;
                        }
                     }

                     if(!colsSet.contains(column)) {
                        cols.addAttribute(counter++, column);
                        colsSet.add(column);
                     }
                     else{
                        cols.addAttribute(index, column);
                     }

                     // for detail
                     cols2.addAttribute(rcolumn != null ? rcolumn : column);
                     SortRef sort = new SortRef(rcolumn != null ? rcolumn : column);
                     dsorts.addSort(sort);
                  }

                  ainfo.addGroup(group);

                  if(!isDetail()) {
                     int order = vdim.getOrder();

                     // time series always sort asc (51330).
                     if(vdim.isDateTime() && vdim.isTimeSeries()) {
                        order = XConstants.SORT_ASC;
                     }

                     if(order == XConstants.SORT_ASC || order == XConstants.SORT_DESC) {
                        SortRef sort2 = new SortRef(rcolumn != null ? rcolumn : column);
                        sort2.setOrder(order);
                        sorts.addSort(sort2);
                     }
                     // this forces dim to be sorted if data is aggregated. this is different
                     // from report and may void the none sorting specified on binding.
                     // it's an edge case and we will keep this logic for now. may consider
                     // removing this to respect the original order regardless of whether
                     // the values are properly sorted. (56590)
                     else if(cinfo.isAggregated() || (order != XConstants.SORT_ORIGINAL &&
                                                      order != XConstants.SORT_NONE))
                     {
                        SortRef sort2 = new SortRef(rcolumn != null ? rcolumn : column);
                        sort2.setOrder(XConstants.SORT_ASC);
                        sorts.addSort(sort2);
                     }
                  }
               }

               if(desc != null) {
                  group.setSortOthersLast(desc.isSortOthersLast());
               }
            }
         }
         // aggregate ref
         else {
            VSAggregateRef vaggr = (VSAggregateRef) refs[i];
            AggregateRef aggr = vaggr.createAggregateRef(cols);

            if(aggr != null) {
               ColumnRef column = (ColumnRef) aggr.getDataRef();

               if(VSUtil.isAggregateCalc(column) && (!isCube || isWorksheetCube())) {
                  CalculateRef cref = (CalculateRef) column;
                  ExpressionRef eref = (ExpressionRef) cref.getDataRef();
                  List<AggregateRef> aggs = VSUtil.findAggregate(
                     getViewsheet(), getSourceTable(), new ArrayList<>(),
                     eref.getExpression());

                  for(int j = 0; j < aggs.size(); j++) {
                     AggregateRef aref = VSUtil.createAliasAgg(aggs.get(j), true);
                     fixUserAggregateRef(aref, cols);
                     ColumnRef colref = (ColumnRef) aref.getDataRef();

                     if(!cols.containsAttribute(colref)) {
                        colref.setVisible(true);
                        cols.addAttribute(counter++, colref);

                        if(colref.getDataRef() instanceof AliasDataRef) {
                           AliasDataRef aliasref = (AliasDataRef) colref.getDataRef();
                           DataRef ref = aliasref.getDataRef();
                           ColumnRef col2 = AssetUtil.getColumnRefFromAttribute(cols, ref);

                           // for detail
                           if(col2 != null) {
                              cols2.addAttribute(col2);
                              SortRef sort = new SortRef(col2);
                              dsorts.addSort(sort);
                           }
                        }
                     }

                     ainfo.addAggregate(aref);
                     aggr2calc.put(aref.getName(), aggr.getName());
                  }
               }

               int index = cols.indexOfAttribute(column);

               // maintain column sequence
               if(index >= 0) {
                  cols.removeAttribute(index);
               }

               column.setVisible(true);
               cols.addAttribute(counter++, column);
               ainfo.addAggregate(aggr);

               if(column.getDataRef() instanceof AliasDataRef) {
                  AliasDataRef aref = (AliasDataRef) column.getDataRef();
                  DataRef ref = aref.getDataRef();
                  column = AssetUtil.getColumnRefFromAttribute(cols, ref);
               }

               // for detail
               cols2.addAttribute(column);
               SortRef sort = new SortRef(column);
               dsorts.addSort(sort);
            }
         }
      }

      // @by billh, flag indicates whether to merge aggregate info to query
      // regardless of ranking condition. If ranking condition exists but it's
      // defined at the inner most dimension, it's also safe enough to merge
      // aggregate info. When summary filter performs post process, it should
      // be capable to handle ranking condition properly then
      boolean mgrcond = true;

      // if no filter, don't apply topn, otherwise the visual data set will be changed
      // fix bug1315839595602
      // prepare ranking
      if(!no_filter) {
         Set<String> rankingFields = new HashSet<>();

         for(int i = 0; i < refs.length; i++) {
            // dimension ref
            if(refs[i] instanceof VSDimensionRef) {
               VSDimensionRef vdim = (VSDimensionRef) refs[i];

               if(rankingFields.contains(vdim.getFullName())) {
                  continue;
               }

               RankingCondition cond = vdim.getRankingCondition();

               if(cond != null) {
                  DataRef aref = cond.getDataRef();

                  if(aref == null) {
                     vdim.updateRanking(cols);
                     cond = vdim.getRankingCondition();
                  }
               }

               if(cond != null) {
                  // clone it, fix bug1343989398469
                  cond = cond.clone();

                  for(int j = i + 1; j < refs.length; j++) {
                     if(refs[j] instanceof VSDimensionRef) {
                        mgrcond = false;
                        break;
                     }
                  }

                  GroupRef group = vdim.createGroupRef(cols);

                  if(group == null) {
                     continue;
                  }

                  DataRef aref = cond.getDataRef();
                  aref = AssetUtil.getColumnRefFromAttribute(cols, aref);
                  DataRef aref2 = (aref != null) ? findRef(ainfo, aref) : null;

                  if(aref2 == null) {
                     LOG.warn("Ranking column not found: " + aref);
                     continue;
                  }

                  cond.setDataRef(aref2);
                  DataRef gref = group.getDataRef();
                  ConditionItem ranking = new ConditionItem(gref, cond, 0);

                  if(rconds.getSize() > 0) {
                     JunctionOperator op = new JunctionOperator();
                     rconds.append(op);
                  }

                  rconds.append(ranking);
                  rankingFields.add(vdim.getFullName());
               }
            }
         }
      }

      // if is sql cube, force to merge, because it is not support post process
      if(isSQLCube(cassembly)) {
         mgrcond = true;
      }

      if(brush) {
         ChartVSAssembly bchart = box.getBrushingChart(vname);
         ConditionList bconds = bchart == null ? null : bchart.getBrushConditionList(cols, true);

         if(bconds != null && !bconds.isEmpty()) {
            table.setProperty("isBrush", "true");
         }
      }

      // for detail?
      if(isDetail()) {
         ConditionList details = cassembly.getDetailConditionList(cols);
         details = removeAggregateCondition(details, ainfo);
         details = replaceGroupValues(details, cassembly, true);
         ConditionListWrapper wrapper = table.getPreRuntimeConditionList();
         ConditionList conds = wrapper == null ? null : wrapper.getConditionList();
         List<ConditionList> list = new ArrayList<>();
         list.add(conds);
         list.add(details);
         conds = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
         table.setPreRuntimeConditionList(conds);
         table.setProperty("showDetail", "true");

         for(int i = 0; i < cols.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) cols.getAttribute(i);

            if(column.getDataRef() instanceof AliasDataRef) {
               continue;
            }

            if(!cols2.containsAttribute(column)) {
               cols2.addAttribute(column);
            }
         }

         // show all columns except date range ref, which may be used in
         // condition list
         table.setColumnSelection(cols2);
         // sort all group/aggregate columns
         table.setSortInfo(dsorts);
         // clear aggregate for it's a detail table
         ainfo.clear();
      }
      else {
         if(isApplyMaxRows(cinfo)) {
            if(box.isRuntime()) {
               table.setMaxRows(DOTPLOT_MAXROWS);
            }
            else {
               table.setMaxDisplayRows(DOTPLOT_MAXROWS);
            }
         }
         else {
            table.setSortInfo(sorts);
         }
      }

      // reset column selection to generate proper public column selection
      // for aggregated table or non-aggegated table
      table.resetColumnSelection();

      // runtime and aggregated? apply ranking
      if(!analysis && !isDetail() && !rconds.isEmpty() &&
         // boxplot doesn't support ranking
         !GraphTypes.isBoxplot(cinfo.getChartType()))
      {
         table.setRankingConditionList(rconds);
      }

      boolean rempty = rconds.isEmpty(); // ranking condition is empty?
      boolean percent = ainfo.containsPercentage(); // percentage defined?
      boolean aoa = ainfo.supportsAOA(); // aggregate on aggregate is supported?
      Map<Set,Set> groupaggrs = cinfo.getRTDiscreteAggregates();
      Map<Set,Set> multigroups = cinfo.getRTFieldGroups();

      boolean hasDiscrete = groupaggrs.size() > 0;
      // both multi-style binding with different group/aggr, and discrete aggregate
      boolean mixedMultiDiscrete = hasDiscrete && multigroups.size() > 0;
      // has any aggregate calc field
      boolean aggrCalc = Arrays.stream(ainfo.getAggregates())
         .anyMatch(a -> VSUtil.isAggregateCalc(a.getDataRef()));
      List<DataRef> ignoreRefs = cinfo.getGeoColumns().stream().collect(Collectors.toList());
      boolean realAggregated = isRealAggregated(ainfo, ignoreRefs.toArray(new DataRef[0]));

      // if binding has lat/lon with none formula, and an aggregate (e.g. sum(total)),
      // generating sql like: select lat, long, sum(total) from ...
      // would cause an exception since lat/long is not aggregated and is not grouped.
      // force them to be aggregated to avoid this error. (47888).
      if(realAggregated) {
         AggregateInfo ninfo = (AggregateInfo) ainfo.clone();
         boolean changed = false;

         for(int i = ninfo.getAggregateCount() - 1; i >= 0; i--) {
            AggregateRef aref = ninfo.getAggregate(i);

            if((aref.getRefType() & DataRef.CUBE_MEASURE) != DataRef.CUBE_MEASURE&&
               aref.getFormula() == AggregateFormula.NONE &&
               !VSUtil.isAggregateCalc(aref.getDataRef()))
            {
               // instead of aggregating lat/lon by MAX, we simply group it.
               // max of lat/lon is just a random point and is meaningless. if lat/long
               // is bound, it seels we should always group on it. (49932)
               if(GraphTypes.isGeo(cinfo.getChartType())) {
                  ninfo.removeAggregate(i);
                  ninfo.addGroup(new GroupRef(aref.getDataRef()));
               }
               else {
                  aref.setFormula(AggregateFormula.MAX);
               }

               changed = true;
            }
         }

         if(changed) {
            // ainfo should be the AggregatInfo of table. (54392)
            table.setAggregateInfo(ainfo = ninfo);
         }
      }

      // non-aggregated table? just discard the aggregate info
      if(!realAggregated) {
         if(!ainfo.isEmpty()) {
            for(int i = 0; i < cols.getAttributeCount(); i++) {
               ColumnRef col = (ColumnRef) cols.getAttribute(i);

               if(col != prangeref && !ainfo.containsGroup(col) && !ainfo.containsAggregate(col)) {
                  col.setVisible(false);
               }
            }
         }

         ainfo.clear();
      }
      // mixed multi-style with discrete has multi-level grouping/join/concat and doesn't
      // work with aoa well.
      // discrete and aggr calc causing aggr calc to be missing in the result. don't use aoa.
      else if((aoa || isSQLCube(cassembly)) && isGroupSupportPushdown(table, getAggregateInfo()) && !(hasDiscrete && aggrCalc) &&
         // 1. aggregated table, percentage existing and supports aoa?
         (percent && rempty && !mixedMultiDiscrete ||
            // ranking doesn't work with discrete measure processing
            !rempty && !hasDiscrete ||
            // 2. aggregated table, percentage not existing, ranking existing but mergeable?
            // shouldn't do aoa if not aoa (50792)
            !rempty && mgrcond && !percent && !mixedMultiDiscrete ||
            // 3. aggregated table, aggregate calc existing and mergeable. (50836)
            ainfo.isCalcMergeable() && rempty && !percent))
      {
         AggregateInfo mainfo = (AggregateInfo) ainfo.clone();

         // create a mirror table to execute percentage
         MirrorTableAssembly mtable = createMirrorTableAssembly(table, vname);
         mtable.setProperty("chart.aoa.mirror", "true");

         if(isSQLCube(cassembly)) {
            mtable.setProperty("SQLCUBE", "true");
         }

         ColumnSelection mcols = mtable.getColumnSelection();

         // replace group ref
         replaceGroupRefWithMirrorColumn(table, mainfo, mcols);

         replaceAggregateRefWithMirrorColumn(table, cassembly, cols, ainfo, aoa, mainfo, mcols);

         replaceRankingWithMirrorColumn(table, rconds);

         // clear ranking, then aggregate might be executed in dbms
         table.setRankingConditionList(new ConditionList());

         // clear percentage, then aggregate might be executed in dbms
         ainfo.clearPercentage();
         // ranking will be executed in mtable
         mtable.setRankingConditionList(rconds);
         // percentage option will be executed in mtable
         mtable.setAggregateInfo(mainfo);
         // reset column selection to generate proper public column selection
         mtable.resetColumnSelection();

         table = mtable;
      }
      // aggregated table, percentage existing and does not support aoa?
      // could do nothing
      else if(!ainfo.isEmpty() && (!rconds.isEmpty() || ainfo.containsPercentage())) {
         RuntimeMV rmv = table.getRuntimeMV();

         if(rmv != null && rmv.isPhysical()) {
            LOG.debug("Cannot use materialized view for chart, underlying table " +
                      "contains aggregation and ranking conditions or a percentage " +
                      "formula: " + vname);
            table.setRuntimeMV(null);
         }
      }
      // if aggregate can't be split, but the base had no aggregate, then we can just perform
      // the aggregation in a mirror and have the base runs against MV. (54293, 54337, 54373)
      else if(!aoa && oainfoEmpty && table.getRuntimeMV() != null) {
         AggregateInfo mainfo = (AggregateInfo) ainfo.clone();
         ainfo.clear();

         removeAggregateCalcFields(table);

         // create a mirror table to execute percentage
         MirrorTableAssembly mtable = createMirrorTableAssembly(table, vname);

         ColumnSelection mcols = mtable.getColumnSelection();
         replaceGroupRefWithMirrorColumn(table, mainfo, mcols);
         replaceAggregateRefWithMirrorColumn2(table, mainfo, mtable);

         mtable.setAggregateInfo(mainfo);
         // reset column selection to generate proper public column selection
         mtable.resetColumnSelection();

         table = mtable;
      }

      // do not execute all columns of a cube
      if(refs.length == 1 && (refs[0].getRefType() & DataRef.CUBE) != 0) {
         boolean variable = refs[0] instanceof VSDimensionRef ?
            ((VSDimensionRef) refs[0]).isVariable() :
            ((VSAggregateRef) refs[0]).isVariable();

         if(variable && table.getColumnSelection(true).equals(table.getColumnSelection())) {
            return null;
         }
      }

      fixAggregateInfo(table, cassembly);
      return table;
   }

   private boolean isApplyMaxRows(VSChartInfo cinfo) {
      return GraphTypeUtil.isDotPlot(cinfo) && box.isBinding();
   }

   private MirrorTableAssembly createMirrorTableAssembly(TableAssembly table, String vname) {
      String mname = Assembly.TABLE_VS + vname + "_mirror";
      Worksheet ws = table.getWorksheet();
      MirrorTableAssembly mtable = new MirrorTableAssembly(ws, mname, null, false, table);

      normalizeTable(mtable);
      ws.addAssembly(mtable);

      return mtable;
   }

   // replace group ref to point to column in the mirror.
   private void replaceGroupRefWithMirrorColumn(TableAssembly table, AggregateInfo mainfo,
                                                ColumnSelection mcols)
   {
      // replace group ref
      for(int i = 0; i < mainfo.getGroupCount(); i++) {
         GroupRef group = mainfo.getGroup(i);
         ColumnRef column = (ColumnRef) group.getDataRef();
         DataRef dref = column.getDataRef();
         DateRangeRef range = null;

         if(dref instanceof DateRangeRef) {
            range = (DateRangeRef) dref;
         }

         DataRef ref = AssetUtil.getOuterAttribute(table.getName(), column);
         column = new ColumnRef(ref);
         group.setDataRef(column);

         if(group.getDateGroup() != GroupRef.NONE_DATE_GROUP && range != null &&
            XSchema.TIME_INSTANT.equals(range.getDataType()))
         {
            column.setOriginalType(range.getOriginalType());
            ColumnRef column2 = (ColumnRef) mcols.findAttribute(column);

            if(column2 != null) {
               column2.setOriginalType(range.getOriginalType());
            }
         }
      }
   }

   // replace the aggregate in mirror to point to base.
   private void replaceAggregateRefWithMirrorColumn(
      TableAssembly table, ChartVSAssembly cassembly, ColumnSelection cols, AggregateInfo ainfo,
      boolean aoa, AggregateInfo mainfo, ColumnSelection mcols)
   {
      List<AggregateRef> aggCalcs = new ArrayList<>();

      // replace aggregate ref
      for(int i = 0; i < mainfo.getAggregateCount(); i++) {
         AggregateRef aggregate = mainfo.getAggregate(i);
         ColumnRef column = (ColumnRef) aggregate.getDataRef();
         AggregateFormula formula = aggregate.getFormula();
         AggregateFormula parent = getParentFormula(formula, aoa);

         if(!isSQLCube(cassembly) || isWorksheetCube()) {
            // agg calc
            if(VSUtil.isAggregateCalc(column)) {
               aggCalcs.add(aggregate);
               continue;
            }
            else {
               aggregate.setFormula(parent);
            }
         }

         DataRef ref = AssetUtil.getOuterAttribute(table.getName(), column);
         column = new ColumnRef(ref);
         aggregate.setDataRef(column);
      }

      // move aggregate calc to mirror
      for(int i = 0; i < aggCalcs.size(); i++) {
         AggregateRef aggregate = aggCalcs.get(i);
         ColumnRef column = (ColumnRef) aggregate.getDataRef();
         DataRef dref = cols.getAttribute(column.getAttribute());
         CalculateRef cref = (CalculateRef) dref.clone();
         DataRef ref = mcols.getAttribute(cref.getName());

         if(ref != null) {
            mcols.removeAttribute(ref);
         }

         mcols.addAttribute(cref);
         // remove the calc calculation from base table
         ainfo.removeAggregate(cref);
      }
   }

   // replace ranking condition column to point to mirror.
   private void replaceRankingWithMirrorColumn(TableAssembly table, ConditionList rconds) {
      // replace ranking refs (top/bottom n aggregate of group)
      for(int i = 0; i < rconds.getSize(); i += 2) {
         ConditionItem citem = rconds.getConditionItem(i);
         RankingCondition ranking = (RankingCondition) citem.getXCondition();
         DataRef attr = citem.getAttribute();

         if(!VSUtil.isAggregateCalc(attr)) {
            attr = AssetUtil.getOuterAttribute(table.getName(), attr);
            attr = new ColumnRef(attr);
         }

         citem.setAttribute(attr);
         attr = ranking.getDataRef();
         String tname = attr instanceof CalculateRef ? null : table.getName();
         attr = AssetUtil.getOuterAttribute(tname, attr);
         attr = new ColumnRef(attr);
         ranking.setDataRef(attr);
      }
   }

   // remove aggregate calc field in column selection
   private void removeAggregateCalcFields(TableAssembly table) {
      ColumnSelection cols3 = table.getColumnSelection(false);

      for(int i = cols3.getAttributeCount() - 1; i >= 0; i--) {
         DataRef col = cols3.getAttribute(i);

         if(col instanceof CalculateRef && !((CalculateRef) col).isBaseOnDetail()) {
            cols3.removeAttribute(i);
         }
      }

      table.resetColumnSelection();
   }

   private void replaceAggregateRefWithMirrorColumn2(TableAssembly table, AggregateInfo mainfo,
                                                     MirrorTableAssembly mtable)
   {
      for(int i = 0; i < mainfo.getAggregateCount(); i++) {
         AggregateRef aggregate = mainfo.getAggregate(i);
         ColumnRef column = (ColumnRef) aggregate.getDataRef();

         if(!(column instanceof CalculateRef)) {
            DataRef baseRef = column.getDataRef();

            // if this is an alias used in aggregate calc, don't change the reference
            // to base table. (54390)
            if(!(baseRef instanceof AliasDataRef) || !((AliasDataRef) baseRef).isAggrCalc()) {
               DataRef ref = AssetUtil.getOuterAttribute(table.getName(), column);
               column = new ColumnRef(ref);
               aggregate.setDataRef(column);
            }
         }
         else {
            mtable.getColumnSelection(false).addAttribute(column);
         }
      }
   }

   private void applyFunnelSort(VSChartInfo cinfo, VSDataRef[] refs) {
      // funnel always sort by value
      if(GraphTypes.isFunnel(cinfo.getChartType()) && refs.length >= 2) {
         VSDataRef[] yRefs = cinfo.getRTYFields();
         VSDataRef aggr = null;
         VSDataRef innerDim = yRefs.length > 0 ? yRefs[yRefs.length - 1] : null;

         for(int j = refs.length - 1; j >= 0; j--) {
            if(refs[j] instanceof VSChartDimensionRef && aggr instanceof VSAggregateRef) {
               // sorting on calc field doesn't work well (we don't show calc field on
               // sort-by-value list on gui). sorting on base value should work fine
               // for % of total, which is the only trend/comparison that makes sense
               // for funnel. (55045)
               String sortBy = ((VSAggregateRef) aggr).getFullName(false);

               if(ArrayUtils.contains(yRefs, refs[j]) && (innerDim == null ||
                  innerDim.getFullName().equals(refs[j].getFullName())))
               {
                  VSChartDimensionRef dim = (VSChartDimensionRef) refs[j];
                  dim.setOrder(XConstants.SORT_VALUE_ASC);
                  dim.setSortByColValue(sortBy);
                  innerDim = dim;
               }
               /* sorting on aesthetics doesn't make much sense. the order for each bar
               will be different and feels random. (55854)
               // shouldn't force outer dim to be sorted by value. (50044)
               else if(!ArrayUtils.contains(yRefs, refs[j]) &&
                  !ArrayUtils.contains(xRefs, refs[j]))
               {
                  VSChartDimensionRef dim = (VSChartDimensionRef) refs[j];
                  dim.setOrder(XConstants.SORT_VALUE_DESC);
                  dim.setSortByColValue(sortBy);
               }
                */
            }
            else {
               aggr = refs[j];
            }
         }
      }
   }

   /**
    * Remove the unreasonable aggregate condition for detail condition list.
    */
   private ConditionList removeAggregateCondition(ConditionList conds,
                                                  AggregateInfo ainfo)
   {
      if(ainfo == null || ainfo.isEmpty() ||
         ainfo.getAggregateCount() == 0 || !ainfo.isAggregated())
      {
         return conds;
      }

      final AggregateRef[] aggs = ainfo.getAggregates();

      AssetUtil.Filter filter = new AssetUtil.Filter() {
         @Override
         public boolean keep(DataRef attr) {
            for(AggregateRef agg : aggs) {
               if(Tool.equals(agg.getName(), attr.getName())) {
                  return false;
               }
            }

            return true;
         }
      };

      return ConditionUtil.filter(conds, filter);
   }

   /**
    * Check if detail condition is valid in the given column selection.
    */
   @Override
   protected boolean isDetailConditionValid(ColumnSelection cols) {
      ChartVSAssembly cassembly = (ChartVSAssembly) getAssembly();
      VSSelection selection = cassembly.getDetailSelection();
      return cassembly.isSelectionValid(selection, cols);
   }

   @Override
   protected boolean isPostSort() {
      return true;
   }

   /**
    * Find the data ref from the specified aggregate info.
    */
   private DataRef findRef(AggregateInfo ainfo, DataRef ref) {
      return AssetUtil.findRef(ainfo, ref);
   }

   /**
    * Get the period dimension ref.
    */
   private XDimensionRef getPeriodDimRef(ChartInfo info, String fullName) {
      ChartRef[] refs = info.getRTXFields();

      for(int i = refs.length - 1; i >= 0; i--) {
         if(refs[i] instanceof XDimensionRef) {
            String name = ((XDimensionRef) refs[i]).getFullName();
            String[] dates = ((XDimensionRef) refs[i]).getDates();

            if(name != null && name.equals(fullName) && dates != null &&
               dates.length >= 2)
            {
               return (XDimensionRef) refs[i];
            }
         }
      }

      refs = info.getRTYFields();

      for(int i = refs.length - 1; i >= 0; i--) {
         if(refs[i] instanceof XDimensionRef) {
            String name = ((XDimensionRef) refs[i]).getFullName();
            String[] dates = ((XDimensionRef) refs[i]).getDates();

            if(name != null && name.equals(fullName) && dates != null &&
               dates.length >= 2)
            {
               return (XDimensionRef) refs[i];
            }
         }
      }

      return null;
   }

   /**
    * VComparator sorts dimensions and aggregates.
    */
   private static class VComparator implements Comparator {
      public VComparator(boolean topNFirst) {
         this.topNFirst = topNFirst;
      }

      /**
       * Compare two data refs, which should be VSDimensionRef or VSAggregateRef
       * only.
       */
      @Override
      public int compare(Object a, Object b) {
         if(a instanceof VSDimensionRef && b instanceof VSDimensionRef) {
            VSDimensionRef dimA = (VSDimensionRef) a;
            VSDimensionRef dimB = (VSDimensionRef) b;

            if(topNFirst || (Tool.equals(dimA.getDataRef(), dimB.getDataRef()) &&
               dimA.getDateLevel() == dimB.getDateLevel()))
            {
               boolean topa1 = ((VSDimensionRef) a).getRankingOption() != XCondition.NONE &&
                  ((VSDimensionRef) a).getRankingN() > 0;
               boolean topb1 = ((VSDimensionRef) b).getRankingOption() != XCondition.NONE &&
                  ((VSDimensionRef) b).getRankingN() > 0;

               if(topa1 != topb1) {
                  return topa1 ? -1 : 1;
               }
            }
         }

         int v1 = a instanceof VSDimensionRef ? 0 : 1;
         int v2 = b instanceof VSDimensionRef ? 0 : 1;
         return v1 - v2;
      }

      private final boolean topNFirst;
   }

   /**
    * Shrink number table lens to avoid db covert number to string problem.
    */
   @SuppressWarnings("serial")
   public static class ShrinkNumberTableLens extends DefaultTableFilter {
      /**
       * Constructor.
       */
      public ShrinkNumberTableLens(TableLens base) {
         super(base);
      }

      /**
       * Set the base table.
       */
      @Override
      public void setTable(TableLens base) {
         super.setTable(base);
         needshrink = new boolean[base.getColCount()];
      }

      /**
       * Set the the specified column need to be shrink or not.
       */
      public void setShrinkColumn(int col, boolean shrink) {
         needshrink[col] = shrink;
      }

      /**
       * Get the object.
       * @param r the specified row index.
       * @param c the specified col index.
       * @return the object.
       */
      @Override
      public Object getObject(int r, int c) {
         Object obj = super.getObject(r, c);

         if(r < getHeaderRowCount() || !needshrink[c] ||
            !(obj instanceof String))
         {
            return obj;
         }

         Object num = Tool.getData(XSchema.DOUBLE, (String) obj);
         // if not a number, return the original string (for named group)
         return num == null ? obj : Tool.toString(num);
      }

      private boolean[] needshrink;
   }

   /**
    * Convert date period label to date object.
    */
   @SuppressWarnings("serial")
   public static class PeriodDateTableLens extends DefaultTableFilter {
      /**
       * Constructor.
       */
      public PeriodDateTableLens(TableLens base, DateFormat[] fmts) {
         super(base);
         this.fmts = fmts;
      }

      /**
       * Get the object.
       * @param r the specified row index.
       * @param c the specified col index.
       * @return the object.
       */
      @Override
      public Object getObject(int r, int c) {
         Object obj = super.getObject(r, c);

         if(r < getHeaderRowCount() || c >= fmts.length || fmts[c] == null ||
            !(obj instanceof String))
         {
            return obj;
         }

         try {
            Date d = null;

            try {
               long v = Long.parseLong((String) obj);
               d = new Date(v);
            }
            catch(Exception ignore) {
            }

            if(d == null) {
               try {
                  d = new Date((String) obj);
               }
               catch(Exception ignore) {
               }
            }

            if(d == null) {
               // @by jasonshobe, bug1411032367385. Try the JDBC timestamp
               // escape format.
               try {
                  d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S")
                     .parse((String) obj);
               }
               catch(Exception ignore) {
                  d = new SimpleDateFormat("yyyy-MM-dd").parse((String) obj);
               }
            }

            return fmts[c].parse(fmts[c].format(d));
         }
         catch(Exception ex) {
            LOG.warn("Failed to parse date: " + obj, ex);
            return obj;
         }
      }

      private DateFormat[] fmts;
   }

   /**
    * Get the parent formula.
    * @param formula the target formula to get parent formula.
    * @param aoa if support aggregate on aggregate.
    */
   private AggregateFormula getParentFormula(AggregateFormula formula, boolean aoa) {
      if(formula == null) {
         return AggregateFormula.NONE;
      }

      // if result is aggregated again (in CrossTabFilter), we should default to just return
      // the result. otherwise Variance of Variance will return 0
      if(!aoa || formula.getParentFormula() == null) {
         return AggregateFormula.SUM;
      }

      return formula.getParentFormula();
   }

   /**
    * Test if the aggregate info is really aggregated.
    * @param geoRefs ignore the refs of columns.
    */
   private boolean isRealAggregated(AggregateInfo ainfo, DataRef... geoRefs) {
      if(ainfo.isAggregated(geoRefs)) {
         ChartVSAssembly chartVSAssembly = (ChartVSAssembly) getAssembly();
         VSChartInfo chartInfo = chartVSAssembly.getVSChartInfo();
         return !GraphTypeUtil.isDotPlot(chartInfo);
      }

      for(int i = 0; i < ainfo.getAggregateCount(); i++) {
         AggregateRef aref = ainfo.getAggregate(i);

         if((aref.getRefType() & DataRef.CUBE_MEASURE) == DataRef.CUBE_MEASURE
            || (aref.getRefType() & DataRef.AGG_CALC) != 0)
         {
            return true;
         }
      }

      return false;
   }

   private static final int DOTPLOT_MAXROWS = 300;
   private static final Logger LOG = LoggerFactory.getLogger(ChartVSAQuery.class);
   private boolean brush;
   private boolean no_filter;
   private boolean no_empty = true;
   private boolean for_bindable;
   // the columns (group and aggregate) in subs tables
   private SubColumns[] subcols;
   private String zoomTable;
   private ConditionList zoomConds;
   private List<String> subTables = new ArrayList();
   private Map<String, String> aggr2calc = new HashMap();
   private VSAssembly assembly;
}
