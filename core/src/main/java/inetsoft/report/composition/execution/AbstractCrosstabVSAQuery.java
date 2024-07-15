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

import inetsoft.graph.data.CalcColumn;
import inetsoft.graph.internal.FirstDayComparator;
import inetsoft.report.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.calc.AbstractColumn;
import inetsoft.report.filter.*;
import inetsoft.report.internal.ComparatorComparer;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.lens.*;
import inetsoft.report.script.formula.AssetQueryScope;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import inetsoft.web.vswizard.model.VSWizardConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CrosstabVSAQuery, the crosstab viewsheet assembly query.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class AbstractCrosstabVSAQuery extends CubeVSAQuery
   implements BindableVSAQuery
{
   /**
    * Create a crosstab viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param crosstab the specified crosstab to be processed.
    * @param detail <tt>true</tt> if show detail, <tt>false</tt> otherwise.
    */
   public AbstractCrosstabVSAQuery(ViewsheetSandbox box, String crosstab, boolean detail) {
      super(box, crosstab, detail);

      CrosstabDataVSAssembly cassembly = (CrosstabDataVSAssembly) getAssembly();
      groupInfo = cassembly.getAggregateInfo();
   }

   /**
    * Get the predefined dimension/measure information.
    */
   @Override
   public AggregateInfo getAggregateInfo() {
      return groupInfo;
   }

   @Override
   public TableAssembly createBindableTable() {
      for_bindable = true;

      try {
         return getTableAssembly(false, false);
      }
      catch(Exception ex) {
         LOG.warn("create crosstab table assembly error", ex);
      }

      return null;
   }

   /**
    * Check if detail condition is valid in the given column selection.
    */
   @Override
   protected boolean isDetailConditionValid(ColumnSelection cols) {
      CrosstabDataVSAssembly cassembly = (CrosstabDataVSAssembly) getAssembly();
      ConditionList details = ((CrosstabVSAssembly) cassembly).getDetailConditionList();

      if(details == null || details.isEmpty()) {
         return true;
      }

      for(int i = 0; i < details.getSize(); i += 2) {
         ConditionItem item = details.getConditionItem(i);
         DataRef ref = item.getAttribute();
         ref = cols.getAttribute(ref.getName());

         if(ref == null) {
            return false;
         }
      }

      return true;
   }

   /**
    * Create the base plain table assembly. The assembly is used to merge with
    * view selections to produce the final query.
    * @param analysis true if for analysis, false for runtime.
    * @param post true if aggregation is done in post processing.
    * @return the created base plain table assembly.
    */
   private TableAssembly createBaseTableAssembly(boolean analysis, boolean post)
      throws Exception
   {
      CrosstabDataVSAssembly cassembly = (CrosstabDataVSAssembly) getAssembly();
      TableAssembly table = createBaseTableAssembly0(analysis);

      if(table == null) {
         return null;
      }
      // if post processing, don't process logic for aggregates
      /*
      else if(post) {
         return table;
      }
      */

      CubeTableAssembly cubetbl = AssetUtil.getBaseCubeTable(table);

      if(isDetail() && cubetbl == null && !box.isMVEnabled(true)) {
         table = findDetailTable(table);
      }

      // set hierarchy
      if(!isDetail() && cubetbl !=  null) {
         CrosstabTree ctree = cassembly.getCrosstabTree();

         if(ctree != null) {
            Map paths = ctree.getCubeExpandedPaths();

            // if convert metadata to live data, we should clear all expanded
            // paths, or the mdx will be wrong, see bug1329814143494
            if(needClearPath(cassembly)) {
               paths.clear();
            }

            cubetbl.setExpandedPaths(paths);
         }
      }

      sinfo = (SourceInfo) ((DataVSAssembly) cassembly).getSourceInfo().clone();
      VSCrosstabInfo cinfo = cassembly.getVSCrosstabInfo();
      ColumnSelection columns = table.getColumnSelection(false);

      if(cinfo == null) {
         return null;
      }

      XCube cube = cassembly.getXCube();
      DataRef[] aggrs2 = cinfo.getRuntimeAggregates();
      oaggrs = (DataRef[]) Tool.clone(aggrs2);
      aggrs = new DataRef[aggrs2.length];

      for(int i = 0; i < aggrs.length; i++) {
         aggrs[i] = (DataRef) aggrs2[i].clone();
      }

      DataRef[] rheaders = cinfo.getRuntimeRowHeaders();
      VSDimensionRef dref = VSUtil.createPeriodDimensionRef(getViewsheet(),
         (DataVSAssembly) getAssembly(), columns, cinfo);

      // append period data ref if not found
      if(dref != null) {
         nrheaders = new DataRef[rheaders.length + 1];
         System.arraycopy(rheaders, 0, nrheaders, 1, rheaders.length);
         nrheaders[0] = dref;
         cinfo.setPeriodRuntimeRowHeaders(nrheaders);

         if(!isDetail()) {
            cinfo.setRuntimeRowHeaders(nrheaders);
            CrossBaseVSAssemblyInfo info = (CrossBaseVSAssemblyInfo) cassembly.getVSAssemblyInfo();
            CrosstabTree ctree = info.getCrosstabTree();

            if(ctree != null) {
               ctree.updateHierarchy(cinfo, info.getXCube(), VSUtil.getCubeType(info.getSourceInfo()));
            }

            appended = true;
         }
      }
      else {
         cinfo.setPeriodRuntimeRowHeaders(null);
      }

      DataRef[] cheaders = cinfo.getRuntimeColHeaders();
      rheaders = cinfo.getRuntimeRowHeaders();
      DataRef[] aggregates = cinfo.getRuntimeAggregates();
      DataRef[] dcTempGroups = cinfo.getDcTempGroups();

      // apply sequence
      ColumnSelection columns2 = (ColumnSelection) columns.clone();
      columns.clear();
      aggalias = new ArrayList<>();

      addColumns(columns, rheaders, columns2);
      addColumns(columns, cheaders, columns2);
      addColumns(columns, aggregates, columns2);

      if(dcTempGroups != null) {
         addColumns(columns, dcTempGroups, columns2);
      }

      if(aggregates != null && aggregates.length > 0) {
         // add user used sub aggregate as alias to avoid name conflict
         IAggregateRef[] naggregates = new IAggregateRef[aggregates.length];

         for(int i = 0; i < aggregates.length; i++) {
            naggregates[i] = (IAggregateRef) aggregates[i];
         }

         List<AggregateRef> calcsubs = getCalcSubAggregates(naggregates);
         addColumns(columns, calcsubs.toArray(new DataRef[0]), columns2);
      }

      AggregateInfo ainfo = getAggregateInfo();
      AggregateRef[] arefs = (ainfo == null) ? new AggregateRef[0] :
         ainfo.getAggregates();
      List<AggregateRef> calcsubs = getCalcSubAggregates(arefs);

      for(int i = 0; i < columns2.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns2.getAttribute(i);

         if(!columns.containsAttribute(column)) {
            // @by larryl, we shouldn't need the other columns since the
            // crosstab would only use the header/aggregate columns. The
            // base table is a copy of the vs_ table, which is a mirror of
            // the original table, so it shouldn't have issue with conditions
            // and other dependencies.
            if(!isDetail() && !isAggregateBaseColumn(calcsubs, column)) {
               column.setVisible(false);
            }

            columns.addAttribute(column);
         }
      }

      table.setColumnSelection(columns, false);

      // do not sort time dimension columns
      ColumnSelection selection = table.getColumnSelection(true);

      for(int i = 0; i < selection.getAttributeCount(); i++) {
         DataRef ref = selection.getAttribute(i);

         if((ref.getRefType() & DataRef.CUBE_TIME_DIMENSION) ==
            DataRef.CUBE_TIME_DIMENSION)
         {
            SortRef sort = new SortRef(ref);
            sort.setOrder(XConstants.SORT_NONE);
            table.getSortInfo().addSort(sort);
         }
      }

      return table;
   }

   /**
    * Check if need clear the expanded path.
    */
   private boolean needClearPath(CrosstabDataVSAssembly cassembly) {
      if(cassembly instanceof CrosstabVSAssembly) {
         boolean metaData = ((CrosstabVSAssembly) cassembly).isMetaData();
         boolean isMeta = getViewsheet().getViewsheetInfo().isMetadata();

         if(!isMeta && metaData ||
            isMeta && box.getMode() == RuntimeViewsheet.VIEWSHEET_RUNTIME_MODE)
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the default column selection.
    * @return the default column selection.
    */
   @Override
   public ColumnSelection getDefaultColumnSelection() throws Exception {
      CrosstabDataVSAssembly cassembly = (CrosstabDataVSAssembly) getAssembly();
      SourceInfo sinfo = ((DataVSAssembly) cassembly).getSourceInfo();

      if(sinfo == null || sinfo.isEmpty()) {
         return new ColumnSelection();
      }

      ColumnSelection columns = getDefaultAssetColumnSelection();
      return VSUtil.getVSColumnSelection(columns);
   }

   /**
    * Get the table.
    */
   @Override
   public TableLens getTableLens() throws Exception {
      final ViewsheetSandbox box = this.box;
      box.lockRead();

      try {
         return getTableLens0();
      }
      finally {
         box.unlockRead();
      }
   }

   /**
    * Get crosstab result.
    */
   private TableLens getTableLens0() throws Exception {
      try {
         aggrs = null;
         sinfo = null;

         CrosstabDataVSAssembly cassembly = (CrosstabDataVSAssembly) getAssembly();
         SourceInfo source = ((DataVSAssembly) cassembly).getSourceInfo();

         if(source == null || source.isEmpty()) {
            return null;
         }

         ConditionList details = !isDetail() ? null :
            ((CrosstabVSAssembly) cassembly).getDetailConditionList();
         VSCrosstabInfo cinfo = cassembly.getVSCrosstabInfo();

         if(cinfo == null) {
            return null;
         }

         VSCrosstabInfo oldInfo = (VSCrosstabInfo) cinfo.clone();
         CrosstabTree ctree = cassembly.getCrosstabTree();
         boolean postDrill = ctree != null && !isCubeDrill() && ctree.isDrilled();

         // don't apply max rows or the result could shift when a level
         // is expanded
         if(ctree != null && ctree.isDrilled() && isCubeDrill()) {
            box.getVariableTable().put(XQuery.HINT_IGNORE_MAX_ROWS, "true");
         }

         TableLens base = null;
         DataRef[] rheaders = null;
         DataRef[] cheaders = null;
         DataRef[] aggregates = null;

         synchronized(cinfo) {
            base = getAssetBaseTableLens(postDrill);
            DataRef[] aggrs = cinfo.getRuntimeAggregates();

            if(aggrs == null || aggrs.length == 0) {
               DataRef[] rows = cinfo.getRuntimeRowHeaders();
               DataRef[] cols = cinfo.getRuntimeColHeaders();
               return (rows == null || rows.length == 0) &&
                  (cols == null || cols.length == 0) ? null : base;
            }

            // show detail? do nothing
            if(details != null && details.getSize() > 0) {
               return base;
            }
            else if(isDetail() || base == null || cinfo == null) {
               return null;
            }

            rheaders = cinfo.getRuntimeRowHeaders();
            cheaders = cinfo.getRuntimeColHeaders();
            aggregates = cinfo.getRuntimeAggregates();
         }

         DataRef[] oaggs = oldInfo.getRuntimeAggregates();
         Map<String, Integer> agg2idx = new HashMap<>();
         ColumnIndexMap columnIndexMap = new ColumnIndexMap(base);
         int boption = cinfo.getPercentageByOption();
         int[] rowh = new int[rheaders.length];
         int[] colh = new int[cheaders.length];
         int[] dateranges = new int[base.getColCount()];
         Object[][] gpairs = {{rowh, rheaders}, {colh, cheaders}};

         for(int i = 0; i < dateranges.length; i++) {
            dateranges[i] = -1;
         }

         for(Object[] pair : gpairs) {
            int[] idxs = (int[]) pair[0]; // rowh or colh
            DataRef[] headers = (DataRef[]) pair[1]; // rheaders or cheaders

            for(int i = 0; i < idxs.length; i++) {
               int col = -1;

               if(groupInfo != null) {
                  GroupRef gref = headers[i] instanceof VSDimensionRef ?
                     ((VSDimensionRef) headers[i]).createGroupRef(null) :
                     getGroupRef(groupInfo, headers[i]);

                  if(gref != null) {
                     DataRef ref2 = gref.getDataRef();

                     if(ref2 instanceof ColumnRef) {
                        ColumnRef column = (ColumnRef) ref2;

                        if(column.getDataRef() instanceof DateRangeRef) {
                           col = AssetUtil.findColumn(base, ref2);

                           if(col >= 0) {
                              Class<?> cls = base.getColType(col);

                              if(cls != null && Number.class.isAssignableFrom(cls)) {
                                 DateRangeRef dref = (DateRangeRef) column.getDataRef();
                                 dateranges[col] = dref.getDateOption();
                              }
                           }
                        }
                        else if(column.getDataRef() instanceof NamedRangeRef) {
                           col = AssetUtil.findColumn(base, ref2);
                        }
                     }
                  }
               }

               col = col >= 0 ? col : AssetUtil.findColumn(base, headers[i]);

               if(col < 0) {
                  LOG.warn("Column not found: " + headers[i]);
                  throw new MessageException(Catalog.getCatalog().getString(
                          "common.invalidTableColumn", headers[i]),
                                             LogLevel.WARN, false);
               }

               idxs[i] = col;
            }
         }

         int[] aggrh = new int[aggregates.length];
         Formula[] formula = new Formula[aggregates.length];
         Formula[] oformula = new Formula[oaggs.length];
         boolean hasFormula = false;

         for(int i = 0; i < aggrh.length; i++) {
            boolean isAggCalc = VSUtil.isAggregateCalc(
               ((VSAggregateRef) aggregates[i]).getDataRef());
            int col = AssetUtil.findColumn(base, aggregates[i]);
            String name = ((XAggregateRef) aggregates[i]).getFullName(false);

            if(aggregates[i] instanceof VSAggregateRef) {
               name = VSUtil.getAggregateField(name, aggregates[i]);
            }

            name = VSUtil.normalizeAggregateName(name);

            if(!agg2idx.containsKey(name)) {
               agg2idx.put(name, i);
            }

            if(col < 0 && !isAggCalc) {
               // aggregate not delegate push down, so use the original header
               // to get col from base table
               ColumnRef cref = (ColumnRef) ((VSAggregateRef) aggregates[i]).getDataRef();

               if(cref != null && cref.getDataRef() instanceof AliasDataRef) {
                  AliasDataRef aliref = (AliasDataRef) cref.getDataRef();
                  col = AssetUtil.findColumn(base, aliref.getDataRef());
               }

               if(col < 0) {
                  LOG.warn("Column not found: " + aggregates[i]);
                  throw new ColumnNotFoundException(Catalog.getCatalog().getString(
                     "common.invalidTableColumn", aggregates[i]));
               }
            }

            int option = ((VSAggregateRef) aggregates[i]).getPercentageOption();

            if(option == StyleConstants.PERCENTAGE_OF_GROUP) {
               if(boption == StyleConstants.PERCENTAGE_BY_ROW &&
                  rheaders.length <= 1)
               {
                  ((VSAggregateRef) aggregates[i]).setPercentageOption(
                     StyleConstants.PERCENTAGE_OF_GRANDTOTAL);
               }
               else if(boption == StyleConstants.PERCENTAGE_BY_COL &&
                  cheaders.length <= 1)
               {
                  ((VSAggregateRef) aggregates[i]).setPercentageOption(
                     StyleConstants.PERCENTAGE_OF_GRANDTOTAL);
               }
            }

            formula[i] = getFormula((VSAggregateRef) aggregates[i], base);
            VSAggregateRef oagg = (VSAggregateRef) oaggs[i];
            AggregateFormula oaform = oagg.getFormula();
            oformula[i] = oaform != null && "Aggregate".equals(oaform.getName())
               ? new SumFormula() : getFormula(oagg, base);
            aggrh[i] = col;

            if(isAggCalc && formula[i] instanceof Formula2) {
               int[] cols = ((Formula2) formula[i]).getSecondaryColumns();

               if(cols != null && cols.length > 0) {
                  aggrh[i] = cols[0];
               }
            }

            if(formula[i] instanceof CalcFieldFormula && aggrh[i] < 0) {
               if(col < 0) {
                  LOG.warn("Column not found: " + aggregates[i]);
                  throw new ColumnNotFoundException(Catalog.getCatalog().getString(
                     "common.calcFieldExpression.error", aggregates[i]));
               }
            }

            hasFormula = hasFormula || formula[i] != null && !(formula[i] instanceof NoneFormula);
         }

         if(rowh.length == 0 && colh.length == 0) {
            if(aggrh.length > 0) {
               if(hasFormula) {
                  String[] measurename = getMeasureHeader(formula, aggregates);
                  SummaryFilter filter = new SummaryFilter(base, rowh, aggrh, formula, formula);
                  filter.setMeasureNames(measurename);
                  return filter;
               }
               else {
                  // if aggregate only and summary is set to None,
                  // the table is a plain table (for scatter chart)
                  return new SubTableLens(base, null, aggrh);
               }
            }
            else if(cinfo.getDesignRowHeaders().length > 0 ||
                    cinfo.getDesignColHeaders().length > 0 ||
                    cinfo.getDesignAggregates().length > 0)
            {
               return new DefaultTableLens(1, 0);
            }

            return new RotatedTableLens(base);
         }

         CrossTabFilter crosstab;
         VSCubeTableLens cubetbl = (base instanceof VSCubeTableLens)
            ? (VSCubeTableLens) base : null;
         Function<String, Integer> findColumn = (name) -> {
            if(agg2idx.containsKey(name)) {
               return agg2idx.get(name);
            }

            return columnIndexMap.getColIndexByFormatedHeader(name, false);
         };

         boolean drilled = ctree.isDrilled() || ctree.hasDrillUpOperation();

         if(cubetbl != null && !postDrill) {
            crosstab = new CrossTabCubeFilter(cubetbl.getTable(),
               cubetbl.getColumnSelection(), rowh, colh, aggrh, formula, cinfo.isMergeSpan());
         }
         else {
            CrosstabVSAssemblyInfo vsAssemblyInfo = (CrosstabVSAssemblyInfo) cassembly.getVSAssemblyInfo();
            DateComparisonInfo dateComparisonInfo =
               DateComparisonUtil.getDateComparison(vsAssemblyInfo, vsAssemblyInfo.getViewsheet());

            if(drilled && dateComparisonInfo == null) {
               // use unique value (e.g. 2001.Qtr1) instead of Qtr1 for
               // drilling unique path
               if(cubetbl != null) {
                  base = new CrosstabTreeTableLens(cubetbl.getTable(), ctree);
               }
               else {
                  base = new CrosstabTreeTableLens(base, ctree);
               }
            }

            TableLens obase = base;
            base = DateComparisonUtil.getMergePartTableLens(
               getAssembly(), getViewsheet(), base, false);
            rowh = mapCols(obase, base, rowh);
            colh = mapCols(obase, base, colh);
            aggrh = mapCols(obase, base, aggrh);

            if(cubetbl != null) {
               crosstab = new CrossTabCubeFilter(base, cubetbl.getColumnSelection(),
                  rowh, colh, aggrh, formula, cinfo.isMergeSpan());
            }
            else {
               VSDimensionRef rowDim = getTimeSeriesRef(rheaders);
               VSDimensionRef colDim = getTimeSeriesRef(cheaders);
               int dleve_r = rowDim != null ? rowDim.getDateLevel() : XConstants.NONE_DATE_GROUP;
               int dleve_c = colDim != null ? colDim.getDateLevel() : XConstants.NONE_DATE_GROUP;
               crosstab = new CrossTabFilter(base, rowh, colh, aggrh, formula, rowDim != null,
                  colDim != null, dleve_r, dleve_c, cinfo.isMergeSpan());
               crosstab.setOldFormula(oformula);
            }
         }

         // if metadata do not need to ignore null totals
         if(!("true".equals(box.getVariableTable().get("calc_metadata")) ||
            getViewsheet().getViewsheetInfo().isMetadata() || isMetadata()))
         {
            crosstab.setIgnoreNullTotals(drilled);
         }

         crosstab.setAggregateTopN(true);

         if(cassembly instanceof CrosstabVSAssembly) {
            CrosstabVSAssembly c = (CrosstabVSAssembly) cassembly;
            crosstab.setAggregateTopN(c.isAggregateTopN());
         }

         crosstab.setPercentageDirection(cinfo.getPercentageByOption());
         crosstab.setKeepColumnHeaders(true);
         crosstab.setShowSummaryHeaders(aggregates.length > 1);

         // @by ankitmathur, Need to make sure the "measureHeaders" are set
         // before make the call to sort the crosstab. When we try to sort the
         // crosstab, we will make a call to initialize the "calcHeaders".
         // This process will not work correctly without the "measureHeaders"
         // being set.
         List<CalcColumn> calcs = new ArrayList<>();

         for(int i = 0; i < aggregates.length; i++) {
            DataRef dataRef = aggrs[i];

            if(!(dataRef instanceof CalculateAggregate)) {
               continue;
            }

            CalculateAggregate aggr = (CalculateAggregate) dataRef;
            Calculator calculator = aggr.getCalculator();

            if(calculator != null) {
               String field = CrossTabFilterUtil.getCrosstabRTAggregateName(aggr, false);
               CalcColumn calc = calculator.createCalcColumn(field);

               if(calc instanceof AbstractColumn) {
                  ((AbstractColumn) calc).setCalculateTotal(cinfo.isCalculateTotal());
               }

               calc.setColIndex(i);
               calcs.add(calc);
            }
         }

         crosstab.setCalcs(calcs, cinfo.isCalculateTotal());
         Map<String, String> calcHeaderMap = new HashMap<>();
         String[] measurename = getMeasureHeader(formula, aggregates, true, calcHeaderMap);
         crosstab.setMeasureNames(measurename, calcHeaderMap);

         List<String> rowHeaders = Arrays.stream(rheaders)
            .map(r -> ((VSDimensionRef) r).getFullName()).collect(Collectors.toList());
         List<String> colHeaders = Arrays.stream(cheaders)
            .map(r -> ((VSDimensionRef) r).getFullName()).collect(Collectors.toList());

         crosstab.setRowHeaders(rowHeaders);
         crosstab.setColHeaders(colHeaders);

         setCrosstabSortOrder(crosstab, cinfo, rheaders, true);
         setCrosstabSortOrder(crosstab, cinfo, cheaders, false);

         ConditionList pcond = createPostConds();

         if(pcond != null && !pcond.isEmpty()) {
            ConditionGroup cgroup = new ConditionGroup(pcond, findColumn);
            crosstab.setCondition(cgroup);
         }

         VSDimensionRef mergePartRef =
            DateComparisonUtil.getMergePartDimension(cassembly, getViewsheet());

         if(cassembly.getVSAssemblyInfo() instanceof CrosstabVSAssemblyInfo) {
            CrosstabVSAssemblyInfo crosstabVSAssemblyInfo =
               (CrosstabVSAssemblyInfo) cassembly.getVSAssemblyInfo();
            VSDimensionRef dcPeriodRef = getDcPeriodRef(
               (CrosstabVSAssemblyInfo) cassembly.getVSAssemblyInfo(), rheaders, cheaders);

            if(dcPeriodRef != null) {
               crosstab.updateDcRefIndexAndStartDate(DateComparisonUtil.getDateComparison(
                  crosstabVSAssemblyInfo, getViewsheet()), dcPeriodRef.getFullName());
            }
         }

         if(mergePartRef != null) {
            crosstab.setDcMergePartRef(mergePartRef.getFullName());
         }

         return crosstab;
      }
      finally {
         dispose();
      }
   }

   private VSDimensionRef getDcPeriodRef(CrosstabVSAssemblyInfo info, DataRef[] rheaders,
                                         DataRef[] cheaders )
   {
      if(!(info.getDateComparisonRef() instanceof VSDimensionRef)) {
         return null;
      }

      VSDimensionRef dateComparisonRef = (VSDimensionRef) info.getDateComparisonRef();
      VSCrosstabInfo cinfo = info.getVSCrosstabInfo();
      VSDimensionRef dcRtCalcDim = null;

      if(cinfo.isDateComparisonOnRow()) {
         if(rheaders == null) {
            return null;
         }

         for(DataRef rheader : rheaders) {
            if(!(rheader instanceof VSDimensionRef)) {
               continue;
            }

            VSDimensionRef dimensionRef = (VSDimensionRef) rheader;

            if(Tool.equals(dimensionRef.getDataRef(), dateComparisonRef.getDataRef())) {
               return dimensionRef;
            }

            if(dimensionRef.getDataRef() instanceof CalculateRef &&
               ((CalculateRef) dimensionRef.getDataRef()).isDcRuntime())
            {
               dcRtCalcDim = dimensionRef;
            }
         }
      }
      else {
         if(cheaders == null) {
            return null;
         }

         for(DataRef cheader : cheaders) {
            if(!(cheader instanceof VSDimensionRef)) {
               continue;
            }

            VSDimensionRef dimensionRef = (VSDimensionRef) cheader;

            if(Tool.equals(dimensionRef.getDataRef(), dateComparisonRef.getDataRef())) {
               return dimensionRef;
            }

            if(dimensionRef.getDataRef() instanceof CalculateRef &&
               ((CalculateRef) dimensionRef.getDataRef()).isDcRuntime())
            {
               dcRtCalcDim = dimensionRef;
            }
         }
      }

      return dcRtCalcDim;
   }

   // map column index from obase to nbase.
   private int[] mapCols(TableLens obase, TableLens nbase, int[] cols) {
      int[] ncols = new int[cols.length];

      for(int i = 0; i < cols.length; i++) {
         ncols[i] = Util.findColumn(nbase, obase.getObject(0, cols[i]));
      }

      return ncols;
   }

   private VSDimensionRef getTimeSeriesRef(DataRef[] refs) {
      if(refs.length < 1) {
         return null;
      }

      VSDimensionRef dref = (VSDimensionRef) refs[refs.length - 1];

      if(dref.isDateTime() && dref.isTimeSeries()) {
         return dref;
      }

      return null;
   }

   /**
    * Get the measure header string for agg calc.
   */
   private String[] getMeasureHeader(Formula[] formula, DataRef[] aggrs) {
      return getMeasureHeader(formula, aggrs, false, null);
   }

   /**
    * Get the measure header string for agg calc.
    */
   private String[] getMeasureHeader(Formula[] formula, DataRef[] aggrs, boolean applyCalc,
                                     Map<String, String> calcHeaderMap)
   {
      String[] measurename = new String[formula.length];

      for(int i = 0; i < formula.length; i++) {
         if(formula[i] instanceof CalcFieldFormula) {
            DataRef ref = VSUtil.getRootRef(aggrs[i], false);
            measurename[i] = ref.getName();
         }
         else if(formula[i] != null) {
            measurename[i] = CrossTabFilterUtil.getCrosstabRTAggregateName((VSAggregateRef) aggrs[i], false);
         }

         if(applyCalc && aggrs[i] instanceof VSAggregateRef &&
            ((VSAggregateRef) aggrs[i]).getCalculator() != null)
         {
            Calculator calc = ((VSAggregateRef) aggrs[i]).getCalculator();
            String originalName = measurename[i];
            measurename[i] = calc.getPrefix() + measurename[i];

            if(calcHeaderMap != null) {
               calcHeaderMap.put(measurename[i], originalName);
            }
         }
      }

      return measurename;
   }

   /**
    * Set the crosstab group sort order.
    */
   private void setCrosstabSortOrder(CrossTabFilter crosstab,
                                     VSCrosstabInfo cinfo, DataRef[] rheaders,
                                     boolean row)
   {
      DataRef[] aggrs = cinfo.getRuntimeAggregates();

      for(int i = 0; i < rheaders.length; i++) {
         VSDimensionRef dref = (VSDimensionRef) rheaders[i];
         int order = dref.isDateTime() && dref.isTimeSeries() ? XConstants.SORT_ASC : dref.getOrder();
         int refType = dref.getRefType();
         int sortBy = findCol(dref.getSortByCol(), aggrs);

         if(sortBy == -1) {
            sortBy = findCol(dref.getSortByColValue(), aggrs);
         }

         TableLens origOrder = findOriginalOrderBase(crosstab);
         SortOrder sorder = (refType & DataRef.CUBE) != DataRef.CUBE ?
            new OrderInfo(order).createSortOrder(origOrder, dref.getDataRef()) :
            new DimensionSortOrder(order, refType, Util.getColumnComparator(crosstab, dref));
         sorder.setInterval(1.0, dref.getRealDateLevel());
         // apply data type, fix bug1260174571737
         sorder.setDataType(AssetUtil.getOriginalType(dref));
         sorder.setDatePostProcess(false);
         Comparer comp = sorder;

         // manual orering
         if(order == XConstants.SORT_SPECIFIC) {
            comp = new ComparatorComparer(dref.createComparator(null));
         }

         if(row) {
            // For Feature #16158, Use FirstDayComparator depending on
            // the locale configured.
            if(Tool.getFirstDayOfWeek() != Calendar.SUNDAY
               && crosstab.getRowHeader(i).contains("DayOfWeek") &&
               comp instanceof SortOrder)
            {
               crosstab.setRowHeaderComparer(i, new FirstDayComparator((SortOrder) comp));
            }
            else {
               crosstab.setRowHeaderComparer(i, comp);
            }

            if(sortBy >= 0) {
               if(order == XConstants.SORT_VALUE_ASC) {
                  crosstab.setRowSortByValInfo(i, sortBy, true);
               }
               else if(order == XConstants.SORT_VALUE_DESC) {
                  crosstab.setRowSortByValInfo(i, sortBy, false);
               }
            }
         }
         else {
            if(Tool.getFirstDayOfWeek() != Calendar.SUNDAY
               && crosstab.getColHeader(i).contains("DayOfWeek") &&
                comp instanceof SortOrder)
            {
               crosstab.setColHeaderComparer(i, new FirstDayComparator((SortOrder) comp));
            }
            else {
               crosstab.setColHeaderComparer(i, comp);
            }

            if(sortBy >= 0) {
               if(order == XConstants.SORT_VALUE_ASC) {
                  crosstab.setColSortByValInfo(i, sortBy, true);
               }
               else if(order == XConstants.SORT_VALUE_DESC) {
                  crosstab.setColSortByValInfo(i, sortBy, false);
               }
            }
         }

         RankingCondition ranking = dref.getRankingCondition();

         if(ranking != null) {
            int n = (Integer) ranking.getN();
            DataRef sref = ranking.getDataRef();
            int dcol = getAggregateIndex(cinfo, sref);

            if(dcol >= 0) {
               if(row) {
                  crosstab.setRowTopN(i, dcol, n, ranking.getOperation() != XCondition.TOP_N, ranking.isGroupOthers());
               }
               else {
                  crosstab.setColTopN(i, dcol, n, ranking.getOperation() != XCondition.TOP_N, ranking.isGroupOthers());
               }
            }
         }
      }
   }

   // find the base table (before sorting) to be used as original order for
   // NONE/ORIGINAL sorting. (58635)
   private TableLens findOriginalOrderBase(TableLens crosstab) {
      TableLens origOrder = Util.getNestedTable(crosstab, SummaryFilter.class);

      if(origOrder instanceof SummaryFilter) {
         origOrder = ((SummaryFilter) origOrder).getTable();

         if(origOrder instanceof SortFilter) {
            origOrder = ((SortFilter) origOrder).getTable();
         }
      }

      return origOrder;
   }

   /**
    * Find the column index.
    */
   private int findCol(String colname, DataRef[] aggrs) {
      if(colname == null) {
         return -1;
      }

      for(int i = 0; i < aggrs.length; i++) {
         if(aggrs[i] instanceof VSAggregateRef) {
            VSAggregateRef agg =  (VSAggregateRef) aggrs[i];

            if(agg.getCalculator() != null && agg.getCalculator().supportSortByValue() &&
               (colname.equals(agg.getFullName()) || colname.equals(
                  VSUtil.getAggregateField(agg.getFullName(), agg))))
            {
               return i;
            }
         }

         if(colname.equals(aggrs[i].getName())) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Get the aggregate index.
    * @param cinfo the specified crosstab info.
    * @param ref the specified ref.
    * @return the aggregate index if found, <tt>-1</tt> otherwise.
    */
   private int getAggregateIndex(VSCrosstabInfo cinfo, DataRef ref) {
      if(cinfo == null || ref == null) {
         return -1;
      }

      DataRef[] refs = cinfo.getRuntimeAggregates();

      for(int i = 0; refs != null && i < refs.length; i++) {
         if(refs[i].equals(ref)) {
            return i;
         }

         if(ref instanceof VSDataRef && refs[i] instanceof VSDataRef &&
            Tool.equals(((VSDataRef) ref).getFullName(), ((VSDataRef) refs[i]).getFullName()))
         {
            return i;
         }
      }

      if(ref instanceof VSAggregateRef) {
         ref = ((VSAggregateRef) ref).getDataRef();
      }

      for(int i = 0; refs != null && i < refs.length; i++) {
          DataRef bref = refs[i] == null ? null :
             ((VSAggregateRef) refs[i]).getDataRef();

          if(ref.equals(bref)) {
             return i;
          }

         if(bref instanceof ColumnRef) {
            bref = ((ColumnRef) bref).getDataRef();
         }

         if(bref instanceof AliasDataRef) {
            AliasDataRef aref = (AliasDataRef) bref;
            bref = aref.getDataRef();
         }

         if(ref.equals(bref)) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Get the table assembly that contains binding info.
    * @param analysis <tt>true</tt> if is for analysis, <tt>false</tt> for
    * @param post true if aggregation is post processed.
    * runtime.
    */
   private TableAssembly getTableAssembly(boolean analysis, boolean post)
         throws Exception
   {
      try {
         CrosstabDataVSAssembly cassembly = (CrosstabDataVSAssembly) getAssembly();
         SourceInfo source = ((DataVSAssembly) cassembly).getSourceInfo();

         if(source == null || source.isEmpty() || source.getType() == SourceInfo.CUBE) {
            return null;
         }

         TableAssembly table = createBaseTableAssembly(analysis, post);
         VSCrosstabInfo cinfo = cassembly.getVSCrosstabInfo();

         if(table != null) {
            ColumnSelection cols = table.getColumnSelection();
            DataRef[][] rheaders = {cinfo.getRuntimeColHeaders(),
                                    cinfo.getRuntimeRowHeaders()};

            for(DataRef[] rrefs : rheaders) {
               for(DataRef ref : rrefs) {
                  VSDimensionRef dref = (VSDimensionRef) ref;
                  RankingCondition ranking = dref.getRankingCondition();

                  if(ranking != null) {
                     DataRef sref = ranking.getDataRef();

                     if(sref == null) {
                        dref.updateRanking(cols);
                     }
                  }
               }
            }
         }

         if(table != null && !isDetail()) {
            // it seems that appended could also push down aggregate
            table = pushDownAggregate(cinfo, table, analysis, post);
         }

         String name = cassembly.getAbsoluteName();

         // for wizard temp crosstab, apply vs wizard analysis max row to the base table.
         if(name != null && name.startsWith(VSWizardConstants.TEMP_CROSSTAB_NAME) &&
            table instanceof MirrorTableAssembly)
         {
            TableAssembly base = ((MirrorTableAssembly) table).getTableAssembly();
            String prop = SreeEnv.getProperty("query.analysis.maxrow");

            if(prop != null) {
               base.setProperty("analysisMaxrow", prop);
            }
         }

         return table;
      }
      finally {
         // for analysis, restore crosstab info
         if(analysis) {
            dispose();
         }
      }
   }

   /**
    * Get the asset base table lens.
    * @param post true if the aggregate is done in post processingj
    * @return the asset base table lens.
    */
   private TableLens getAssetBaseTableLens(boolean post) throws Exception {
      CrosstabDataVSAssembly cassembly = (CrosstabDataVSAssembly) getAssembly();
      VSCrosstabInfo cinfo = cassembly.getVSCrosstabInfo();
      TableAssembly table = getTableAssembly(false, post);

      if(table == null || cinfo == null) {
         return null;
      }

      DataRef[] rheaders = cinfo.getRuntimeRowHeaders();
      DataRef[] cheaders = cinfo.getRuntimeColHeaders();
      ColumnSelection columns = table.getColumnSelection(false);
      ConditionList details = !isDetail() ? null :
         ((CrosstabVSAssembly) cassembly).getDetailConditionList();
      details = replaceGroupValues(details, (CrosstabVSAssembly) cassembly, true);

      if(details != null && !details.isEmpty()) {
         details = VSUtil.normalizeConditionList(columns, details, true, true);

         if(details != null && !details.isEmpty()) {
            ConditionListWrapper wrapper = table.getPreRuntimeConditionList();
            ConditionList conds = wrapper == null ? null :
               wrapper.getConditionList();
            List list = new ArrayList();
            list.add(conds);
            list.add(details);
            conds = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
            table.setPreRuntimeConditionList(conds);
         }

         table.setProperty("showDetail", "true");
         SortInfo sinfo = new SortInfo();
         DataRef[] headers = (DataRef[]) Tool.mergeArray(rheaders, cheaders);

         // add grouping columns to be sorted so the sorting doesn't need
         // to be done post process
         for(int i = 0; i < headers.length; i++) {
            DataRef ref = ((VSDimensionRef) headers[i]).getDataRef();
            ColumnRef column = (ColumnRef) columns.getAttribute(ref.getName());
            int order = ((VSDimensionRef) headers[i]).getOrder();

            if(order != SortOrder.SORT_ORIGINAL &&
              (ref.getRefType() & DataRef.CUBE) != DataRef.CUBE)
            {
               SortRef sort = new SortRef(column);
               sort.setOrder(order);
               sinfo.addSort(sort);
            }
         }

         table.setSortInfo(sinfo);
      }
      else {
         if(isDetail()) {
            return null;
         }

         ConditionList range = cassembly.getRangeConditionList();
         range = VSUtil.normalizeConditionList(columns, range);

         if(range != null && !range.isEmpty()) {
            ConditionListWrapper wrapper = table.getPreRuntimeConditionList();
            ConditionList conds = wrapper == null ? null :
               wrapper.getConditionList();
            List<ConditionList> list = new ArrayList<>();
            list.add(conds);
            list.add(range);
            conds = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
            table.setPreRuntimeConditionList(conds);
         }
      }

      TableLens lens = getTableLens(table);
      lens = getDcMergeDateTableLens(lens, table, null);

      return fillFakeTable(lens, cinfo);
   }

   /**
    * Fill table lens with fake column.
    */
   private TableLens fillFakeTable(TableLens lens, VSCrosstabInfo info) {
      DataRef[] aggs = info.getDesignAggregates();

      if(aggs == null || aggs.length != 1 || lens == null) {
         return lens;
      }

      if(!VSUtil.isFake(aggs[0])) {
         return lens;
      }

      AssetQuerySandbox wbox = box.getAssetQuerySandbox();
      AssetQueryScope scope = wbox.getScope();
      int mdl = isMetadata() || getViewsheet().getViewsheetInfo().isMetadata() ?
         AssetQuerySandbox.DESIGN_MODE :
         box.getMode() == AbstractSheet.SHEET_RUNTIME_MODE ?
         AbstractSheet.SHEET_RUNTIME_MODE : AssetQuerySandbox.LIVE_MODE;
      scope.setMode(mdl);
      String val = mdl == AssetQuerySandbox.DESIGN_MODE ? "999.99" : "0";

      FormulaTableLens lens0 = new FormulaTableLens(
         lens, new String[]{aggs[0].getName()}, new String[]{val},
         wbox.getScriptEnv(), wbox.getScope());
      lens0.setColType(lens.getHeaderColCount() - 1, Double.class);

      return lens0;
   }

   /**
    * Push down aggregate to table.
    */
   private TableAssembly pushDownAggregate(
      VSCrosstabInfo cinfo, TableAssembly table, boolean analysis, boolean post)
   {
      boolean aoa = supportsAOA(cinfo);

      if(!aoa && (post || hasGroupTotal(cinfo) && !isCubeSource() || !isPushDownFormula(cinfo)) ||
         !isGroupSupportPushdown(table, cinfo.getAggregateInfo()))
      {
         addDateRangeColumns(cinfo, table);
         return table;
      }

      groupInfo = new AggregateInfo();
      ColumnSelection cols = table.getColumnSelection();
      AggregateInfo allGroupInfo = new AggregateInfo();
      DataRef[] grows = cinfo.getRuntimeRowHeaders();
      DataRef[] gcols = cinfo.getRuntimeColHeaders();
      DataRef[] tempRefs = cinfo.getDcTempGroups();

      DataRef[][] grps = {grows, gcols, tempRefs};

      // 1. add group ref one by one regardless of optimizable result
      // 2. add date range to column selection regardless of optimizable result
      for(int i = 0; i < grps.length; i++) {
         for(int j = 0; j < grps[i].length; j++) {
            if(!(grps[i][j] instanceof VSDimensionRef)) {
               continue;
            }

            VSDimensionRef ref = (VSDimensionRef) grps[i][j];
            GroupRef gref = ref.createGroupRef(cols);

            if(gref == null) {
               continue;
            }

            DataRef dref = gref.getDataRef();
            cols.removeAttribute(dref);
            cols.addAttribute(dref);
            ColumnRef column = (ColumnRef) gref.getDataRef();

            if(column.getDataRef() instanceof DateRangeRef) {
               DateRangeRef range = (DateRangeRef) column.getDataRef();
               DataRef tref = range.getDataRef();
               DataRef ncol = AssetUtil.getColumnRefFromAttribute(cols, tref);

               if(ncol != null) {
                  range.setDataRef(((ColumnRef) ncol).getDataRef());
               }

               if(!groupInfo.containsGroup(gref)) {
                  groupInfo.addGroup(gref);
               }
            }
            else {
               column = AssetUtil.getColumnRefFromAttribute(cols, column);
               gref.setDataRef(column);
            }

            if(!allGroupInfo.containsGroup(gref)) {
               allGroupInfo.addGroup(gref);
            }
         }
      }

      List aggrsList = new ArrayList();

      if(!isOptimizable(cinfo, aggrsList, aoa, cols)) {
         // @by billh, when mv exists, we should always use mv, no matter
         // detailed or aggregated
         // table.setRuntimeMV(null);
         LOG.debug("Crosstab will be post processed for: {}", vname);
         // fix bug1308728720543, remove the added aggregate ref alias column
         // will cause tablepath changed, and highlight may be lost.
         // removeAggCalcColumn(table);
         return table;
      }

      AggregateInfo ainfo = table.getAggregateInfo();
      cols = table.getColumnSelection();
      int gcnt = allGroupInfo.getGroupCount();

      for(int i = 0; i < gcnt; i++) {
         GroupRef gref = allGroupInfo.getGroup(i);
         gref = (GroupRef) gref.clone();

         if(!ainfo.containsGroup(gref)) {
            ainfo.addGroup(gref);
         }
      }

      table = createAggregates(table, aggrsList, cinfo);
      fixAggregateInfo(table, getAssembly());
      return table;
   }

   /**
    * Add date range column to column selection.
    */
   private void addDateRangeColumns(VSCrosstabInfo cinfo, TableAssembly table) {
      ColumnSelection cols = table.getColumnSelection();
      DataRef[] grows = cinfo.getRuntimeRowHeaders();
      DataRef[] gcols = cinfo.getRuntimeColHeaders();
      DataRef[] tempRefs = cinfo.getDcTempGroups();

      DataRef[][] grps = {grows, gcols, tempRefs};

      for(int i = 0; i < grps.length; i++) {
         for(int j = 0; j < grps[i].length; j++) {
            if(!(grps[i][j] instanceof VSDimensionRef)) {
               continue;
            }

            VSDimensionRef dim = (VSDimensionRef) grps[i][j];
            GroupRef gref = dim.createGroupRef(cols);

            if(gref != null && (dim.isNameGroup() ||
               gref.getDateGroup() != GroupRef.NONE_DATE_GROUP))
            {
               DataRef dref = gref.getDataRef();
               cols.removeAttribute(dref);
               cols.addAttribute(dref);
            }
         }
      }
   }

   /**
    * Check if the crosstab has group total, in which case it's impossible to
    * run a single query to calculate the aggregates.
    */
   private boolean hasGroupTotal(VSCrosstabInfo cinfo) {
      if(for_bindable) {
         return false;
      }

      if(cinfo.isColTotalVisible() || cinfo.isRowTotalVisible()) {
         return true;
      }

      DataRef[] aggrs = cinfo.getRuntimeAggregates();

      for(DataRef ref : aggrs) {
         VSAggregateRef aref = (VSAggregateRef) ref;

         if(aref.getPercentageOption() != XConstants.PERCENTAGE_NONE &&
            aref.getPercentageOption() != XConstants.PERCENTAGE_OF_GRANDTOTAL)
         {
            return true;
         }
      }

      return false;
   }

   private boolean isCubeSource() {
      DataVSAssemblyInfo info = (DataVSAssemblyInfo) getAssembly().getVSAssemblyInfo();
      SourceInfo sourceInfo = info.getSourceInfo();
      String source = sourceInfo == null ? null : sourceInfo.getSource();

      return source != null && source.startsWith(Assembly.CUBE_VS);
   }

   /**
    * Create aggregates in table assembly.
    */
   private TableAssembly createAggregates(TableAssembly table, List aggrsList,
                                          VSCrosstabInfo cinfo)
   {
      AggregateInfo ainfo = table.getAggregateInfo();
      ColumnSelection cols = table.getColumnSelection();

      for(int i = 0; i < aggrsList.size(); i++) {
         VSAggregateRef oaggr = (VSAggregateRef) aggrsList.get(i);
         VSAggregateRef naggr = new VSAggregateRef();
         ColumnRef col = AssetUtil.getColumnRefFromAttribute(cols, oaggr.getDataRef());

         if(VSUtil.isFake(oaggr) || col == null) {
            continue;
         }

         boolean isAggCalc = VSUtil.isAggregateCalc(col);
         CalculateRef cref = null;

         if(isAggCalc) {
            cref = (CalculateRef) oaggr.getDataRef().clone();
            ExpressionRef eref = (ExpressionRef) cref.getDataRef();
            // add the calculate field sub aggregate ref to base table
            String expression = eref.getExpression();
            List<String> matchNames = new ArrayList<>();
            List<AggregateRef> aggs = VSUtil.findAggregate(getViewsheet(),
               getSourceTable(), matchNames, expression);
            // fix Bug #45872, treat the calcfield as normal expression field
            // if it contains no aggregate field.
            isAggCalc = aggs.size() != 0;

            for(int j = 0; j < aggs.size(); j++) {
               AggregateRef aref = VSUtil.createAliasAgg(aggs.get(j), true);
               fixUserAggregateRef(aref, cols);

               if(!ainfo.containsAggregate(aref)) {
                  ainfo.addAggregate(aref);
               }
            }

            String newex = VSUtil.createAOAExpression(aggs, expression,
               matchNames, getViewsheet());
            eref.setExpression(newex);
         }

         AggregateFormula formula = oaggr.getFormula();
         ColumnRef col2 = (ColumnRef) oaggr.getSecondaryColumn();

         if(col2 != null) {
            col2 = AssetUtil.getColumnRefFromAttribute(cols, col2);
         }

         AggregateRef aref = new AggregateRef(col, col2, formula);
         aref.setN(oaggr.getN());

         if((!isAggCalc || for_bindable) && !ainfo.containsAggregate(aref)) {
            ainfo.addAggregate(aref);
         }

         String colname = aref.getAttribute();
         DataRef colref = oaggr.getDataRef();
         naggr.setColumnValue(colname);
         String nform = getParentFormula(formula);
         naggr.setPercentageOption(oaggr.getPercentageOption());
         naggr.setCalculator(oaggr.getCalculator());
         naggr.setFormulaValue(nform);
         naggr.setSecondaryColumn(null);
         naggr.setCaption(oaggr.getCaption());

         if(isAggCalc && cref != null) {
            colref = cref;
         }

         naggr.setDataRef(colref);
         replaceAggregate(cinfo, oaggr, naggr);
      }

      return table;
   }

   /**
    * Get the parent formula.
    */
   private String getParentFormula(AggregateFormula form) {
      if(form == null || form == AggregateFormula.NONE) {
         return AggregateFormula.NONE.getName();
      }
      // if result is aggregated again (in CrossTabFilter), we should default to just return
      // the result. otherwise Variance of Variance will return 0
      else if(form.getParentFormula() == null) {
         return AggregateFormula.SUM.getName();
      }

      return form.getParentFormula().getName();
   }

   /**
    * Replace the aggregate definition in the assembly with the new aggregate.
    */
   private void replaceAggregate(VSCrosstabInfo cinfo, VSAggregateRef oaggr,
                                 VSAggregateRef naggr) {
      DataRef[] aggrs = cinfo.getRuntimeAggregates();

      for(int k = 0; k < aggrs.length; k++) {
         if(aggrs[k].equals(oaggr)) {
            // for aggregate ref, also check its percentage option
            // fix bug1295900880953
            if(aggrs[k] instanceof VSAggregateRef) {
               if(((VSAggregateRef) aggrs[k]).getPercentageOption() !=
                  naggr.getPercentageOption())
               {
                  continue;
               }
            }

            aggrs[k] = naggr;
         }
      }

      cinfo.setRuntimeAggregates(aggrs);
   }

   /**
    * Check if supports aggregate on aggregate.
    */
   private boolean supportsAOA(VSCrosstabInfo cinfo) {
      DataRef[] aggrs = cinfo.getRuntimeAggregates();
      AggregateRef[] allaggs =
         VSUtil.getAggregates(getViewsheet(), getSourceTable(), false);

      // add all aggregates
      for(int i = 0; i < aggrs.length; i++) {
         if(aggrs[i] instanceof VSAggregateRef) {
            if(!VSUtil.supportsAOA((VSAggregateRef) aggrs[i], allaggs)) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * check if the formula is proper to pushdown.
    */
   private boolean isPushDownFormula(VSCrosstabInfo cinfo) {
      DataRef[] aggrs = cinfo.getRuntimeAggregates();
      AggregateRef[] allaggs =
         VSUtil.getAggregates(getViewsheet(), getSourceTable(), false);

      // add all aggregates
      for(int i = 0; i < aggrs.length; i++) {
         IAggregateRef aggr = (IAggregateRef) aggrs[i];

         if(VSUtil.isAggregateCalc(aggr.getDataRef())) {
            CalculateRef cref = (CalculateRef) aggr.getDataRef();
            ExpressionRef eref = (ExpressionRef) cref.getDataRef();
            List<String> names = new ArrayList<>();
            List<AggregateRef> subs = VSUtil.findAggregate(allaggs, names, eref.getExpression());

            for(int j = 0; j < subs.size(); j++) {
               if(!isPushDownFormula(subs.get(j))) {
                  return false;
               }
            }
         }
         else if((!isPushDownFormula(aggr))) {
            return false;
         }
      }

      return true;
   }

   private boolean isPushDownFormula(IAggregateRef aggr) {
      if(aggr == null) {
         return false;
      }

      AggregateFormula formula = aggr.getFormula();
      return formula == null || !formula.isTwoColumns() && !formula.hasN() || pushDown;
   }

   /**
    * Check if a crosstab is optimizable.
    */
   private boolean isOptimizable(VSCrosstabInfo cinfo, List aggrsList,
                                 boolean aoa, ColumnSelection selection) {
      boolean isSQLCube = isSQLCube(getAssembly());

      if(!for_bindable && cinfo.isColTotalVisible() && !aoa && !isSQLCube) {
         LOG.debug("Crosstab column total is visible");
         return false;
      }

      if(!for_bindable && cinfo.isRowTotalVisible() && !aoa && !isSQLCube) {
         LOG.debug("Crosstab row total is visible");
         return false;
      }

      DataRef[] rows = cinfo.getRuntimeRowHeaders();
      DataRef[] cols = cinfo.getRuntimeColHeaders();
      DataRef[] aggrs = cinfo.getRuntimeAggregates();

      /* @by larryl, this is no different from MV for output assembly, where
         there is never grouping. If the result is different, we need to find
         out why.
      // in case that post-process and pre-process return different data
      if(rows.length == 0 && cols.length == 0) {
         return false;
      }
      */

      boolean onlyrow = cols.length == 0;
      boolean onlycol = rows.length == 0;
      boolean topN = false;

      for(int i = 0; i < rows.length; i++) {
         boolean dimTopN = hasTopN((VSDimensionRef) rows[i], cinfo);
         topN = topN || dimTopN;

         if(!for_bindable && dimTopN && !aoa &&
            (i < rows.length - 1 || !onlyrow) && !isSQLCube)
         {
            return false;
         }
      }

      for(int i = 0; i < cols.length; i++) {
         boolean dimTopN = hasTopN((VSDimensionRef) cols[i], cinfo);
         topN = topN || dimTopN;

         if(!for_bindable && dimTopN && !aoa &&
            (i < cols.length - 1 || !onlycol) && !isSQLCube)
         {
            return false;
         }
      }

      // add all aggregates
      for(int i = 0; i < aggrs.length; i++) {
         if(aggrs[i] instanceof VSAggregateRef) {
            if(!for_bindable &&
               !isAggregateOptimizable((VSAggregateRef) aggrs[i], aoa, topN) &&
               !isSQLCube)
            {
               return false;
            }

            aggrsList.add(aggrs[i]);
         }
      }

      // add all groups
      DataRef[][] grps = {rows, cols};

      for(int i = 0; i < grps.length; i++) {
         for(int j = 0; j < grps[i].length; j++) {
            if(!(grps[i][j] instanceof VSDimensionRef)) {
               continue;
            }

            if(!for_bindable &&
               !isDimensionOptimizable((VSDimensionRef) grps[i][j], aoa) &&
               !isSQLCube)
             {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Check if an aggregate is optimizable.
    */
   private boolean isAggregateOptimizable(IAggregateRef aggr, boolean aoa, boolean topN) {
      AggregateFormula formula = aggr.getFormula();

      if(aggr.getDataRef() instanceof CalculateRef) {
         CalculateRef cref = (CalculateRef) aggr.getDataRef();

         if(!cref.isBaseOnDetail()) {
            String expression = ((ExpressionRef) cref.getDataRef()).getExpression();
            Viewsheet vs = getViewsheet();
            List<AggregateRef> aggs = VSUtil.findAggregate(vs, getSourceTable(),
                                                           new ArrayList<>(), expression);

            for(int i = 0; i < aggs.size(); i++) {
               if(!isAggregateOptimizable(aggs.get(i), aoa, topN)) {
                  return false;
               }
            }

            return true;
         }
      }

      if(formula == null || formula.equals(AggregateFormula.NONE) ||
         formula.equals(AggregateFormula.FIRST) ||
         formula.equals(AggregateFormula.LAST))
      {
         return false;
      }

      // Bug #56918, incorrect avg result, handle in post
      if(topN && AggregateFormula.AVG.equals(formula)) {
         return false;
      }

      int percent = aggr.getPercentageOption();

      if(percent != XConstants.PERCENTAGE_NONE && !aoa) {
         return false;
      }

      return true;
   }

   /**
    * Check if a dimension is optimizable.
    */
   private boolean isDimensionOptimizable(VSDimensionRef dim, boolean aoa) {
      if(dim.isSubTotalVisible() && !aoa) {
         return false;
      }

      return true;
   }

   /**
    * Check if a dimension has TopN setting.
    * @param dref the specified vsdimension ref.
    * @param cinfo the specified VSCrosstabInfo.
    * @return true if have the topN, false otherwise.
    */
   private boolean hasTopN(VSDimensionRef dref, VSCrosstabInfo cinfo) {
      RankingCondition ranking = dref.getRankingCondition();

      if(ranking != null) {
         DataRef sref = ranking.getDataRef();
         int dcol = getAggregateIndex(cinfo, sref);

         if(dcol >= 0) {
            return ranking.getOperation() == XCondition.TOP_N ||
                   ranking.getOperation() == XCondition.BOTTOM_N;
         }
      }

      return false;
   }

   /**
    * Get the formula object.
    * @param aggregate the specified aggregate.
    * @return the associated formula object of the aggregate formula.
    */
   private Formula getFormula(IAggregateRef aggregate, TableLens lens) {
      AggregateFormula aform = aggregate.getFormula();

      if(aform == null || VSUtil.isFake(aggregate)) {
         return null;
      }

      DataRef ref = aggregate.getDataRef();
      CalculateRef cref = ref instanceof CalculateRef ? (CalculateRef) ref : null;
      boolean isSQLCube = isSQLCube(getAssembly());

      if(isSQLCube && AggregateFormula.NONE.equals(aform)) {
         aform = AggregateFormula.SUM;
      }

      CrosstabDataVSAssembly cassembly = (CrosstabDataVSAssembly) getAssembly();

      if(!VSUtil.isAggregateCalc(cref) && AggregateFormula.NONE.equals(aform)) {
         // CrossTabFilter sort by value won't work with null formula. use NoneFormula instead.
         return new NoneFormula();
      }

      Formula form = null;

      if(cref == null || isSQLCube && !isWorksheetCube()) {
         String fstr = aform.getFormulaName();
         DataRef ref2 = aggregate.getSecondaryColumn();

         if(ref2 == null && aform.isTwoColumns()) {
            throw new RuntimeException("Formula requires two columns: " +
                                       aform.getFormulaName());
         }

         if(ref2 != null && aform.isTwoColumns()) {
            int col = AssetUtil.findColumn(lens, ref2);

            if(col < 0) {
               LOG.warn("Column not found: " + ref2);
               throw new ColumnNotFoundException(Catalog.getCatalog().getString(
                  "common.invalidTableColumn", ref2));
            }

            fstr += "(" + col + ")";
         }

         if(aform.hasN()) {
            fstr += "(" + aggregate.getN() + ")";
         }

         form = Util.createFormula(lens, fstr);
      }
      else {
         ExpressionRef eref = (ExpressionRef) cref.getDataRef();
         Viewsheet vs = getViewsheet();
         String expression = eref.getExpression();
         List<String> matchNames = new ArrayList<>();
         List<AggregateRef> aggs = VSUtil.findAggregate(vs, getSourceTable(),
            matchNames, expression);
         Formula[] forms = new Formula[aggs.size()];
         String[] names = new String[aggs.size()];
         names = matchNames.toArray(names);
         List<Integer> colidx = new ArrayList<>();

         for(int i = 0; i < aggs.size() ; i++) {
            AggregateRef aref = aggalias == null || vs.isAOARef(aggs.get(i)) ?
               aggs.get(i) : VSUtil.createAliasAgg(aggs.get(i), true);
            forms[i] = getFormula(aref, lens);
            forms[i] = forms[i] == null ? new NoneFormula() : forms[i];
            DataRef bref = aref.getDataRef();
            int col = AssetUtil.findColumn(lens, bref);

            if(col < 0) {
               if(bref instanceof AliasDataRef) {
                  AliasDataRef aliref = (AliasDataRef) bref;
                  col = AssetUtil.findColumn(lens, aliref.getDataRef());
               }

               if(col < 0) {
                  LOG.warn("Column not found: " + bref);
                  throw new ColumnNotFoundException(Catalog.getCatalog().getString(
                     "common.invalidTableColumn", bref));
               }
            }

            colidx.add(col);
            DataRef ref2 = aref.getSecondaryColumn();

            if(forms[i] instanceof Formula2) {
               if(ref2 == null) {
                  throw new RuntimeException("Formula requires two columns: " +
                     forms[i]);
               }

               col = AssetUtil.findColumn(lens, ref2);

               if(col < 0) {
                  LOG.warn("Column not found: " + ref2);
                  throw new ColumnNotFoundException(Catalog.getCatalog().getString(
                     "common.invalidTableColumn", ref2));
               }

               colidx.add(col);
            }
         }

         int[] cols = new int[colidx.size()];

         for(int i = 0; i < colidx.size(); i++) {
            cols[i] = colidx.get(i);
         }

         form = new CalcFieldFormula(expression, names, forms, cols,
            box.getAssetQuerySandbox().getScriptEnv(),
            box.getAssetQuerySandbox().getScope());
      }

      if(form == null) {
         throw new RuntimeException("Unsupported aggregate formula found: " +
                                    aform);
      }

      if(form instanceof PercentageFormula) {
         int percent = aggregate.getPercentageOption();
         ((PercentageFormula) form).setPercentageType(percent);
      }

      return form;
   }

   /**
    * Add column refs to column list from columns2 in the same order as they
    * appear on the data ref array.
    * @param columns the specified target column selection.
    * @param refs the specified data refs.
    * @param columns the specified original column selection.
    */
   private void addColumns(ColumnSelection columns, DataRef[] refs,
                           ColumnSelection columns2) {
      for(int i = 0; i < refs.length; i++) {
         DataRef ref = refs[i];
         DataRef ref2 = null;

         if(VSUtil.isFake(ref)) {
            continue;
         }

         if(ref instanceof DataRefWrapper) {
            DataRef ref0 = ((DataRefWrapper) ref).getDataRef();

            if(ref0 == null) {
               LOG.warn("Column not found: " + ref);
               continue;
            }

            ref = ref0;
         }

         ColumnRef column = ref == null ? null :
            (ColumnRef) columns2.getAttribute(ref.getName());
         boolean aliased = false;

         if(column == null) {
            DataRef bref = ref;

            if(bref instanceof ColumnRef) {
               bref = ((ColumnRef) bref).getDataRef();
            }

            aliased = bref instanceof AliasDataRef;

            if(aliased) {
               AliasDataRef aref = (AliasDataRef) bref;
               DataRef obref = aref.getDataRef();
               column = (ColumnRef) columns2.getAttribute(obref.getName());
               // data type from table is more accurate (vs aggregateRef data type always string)
               // data type may be significant in some case (formula result to correct type).
               // column data type may not be accurate, should keep original if so. (49095)
               if(XSchema.STRING.equals(aref.getDataType())) {
                  aref.setDataType(column.getDataType());
               }
            }

            if(column == null) {
               LOG.warn("Column not found: " + ref);
               continue;
            }

            if(aliased) {
               column = (ColumnRef) ref;
               aggalias.add(column);
            }
         }

         // ignore alias ref like SUM(XXX) when show detail
         if(!aliased || !isDetail()) {
            column.setVisible(true);
            columns.removeAttribute(column);
            columns.addAttribute(column);
         }

         if(refs[i] instanceof IAggregateRef) {
            IAggregateRef aggr = (IAggregateRef) refs[i];
            AggregateFormula form = aggr.getFormula();

            if(form != null && form.isTwoColumns()) {
               ref2 = aggr.getSecondaryColumn();
            }
         }

         if(ref2 != null) {
            column = (ColumnRef) columns2.getAttribute(ref2.getName());

            if(column == null) {
               LOG.warn("Column not found: " + ref2);
               continue;
            }

            if(!columns.containsAttribute(column)) {
               column.setVisible(true);
               columns.addAttribute(column);
            }

            aggalias.add(column);
         }
      }
   }

   /**
    * Dispose the viewsheet query. The temporary worksheet assemblies will be
    * removed, and the stored data will be removed as well in the asset query
    * box.
    */
   private void dispose() {
      CrosstabDataVSAssembly cassembly =
         (CrosstabDataVSAssembly) getAssembly();

      if(cassembly == null) {
         return;
      }

      VSCrosstabInfo cinfo = cassembly.getVSCrosstabInfo();

      if(oaggrs != null) {
         cinfo.setRuntimeAggregates(oaggrs);
      }

      if(sinfo != null) {
         ((DataVSAssembly) cassembly).setSourceInfo(sinfo);
      }

      if(appended) {
         DataRef[] rheaders = cinfo.getRuntimeRowHeaders();

         if(rheaders != null && rheaders.length > 0) {
            DataRef[] nrheaders = new DataRef[rheaders.length - 1];
            System.arraycopy(rheaders, 1, nrheaders, 0, rheaders.length - 1);
            cinfo.setRuntimeRowHeaders(nrheaders);
            appended = false;
            nrheaders = null;
         }
      }
   }

   /**
    * Find the group ref or its base that matchs the column.
    */
   private GroupRef getGroupRef(AggregateInfo ainfo, DataRef ref) {
      GroupRef aref = ainfo.getGroup(ref);

      if(aref != null) {
         return aref;
      }

      for(int i = 0; i < ainfo.getGroupCount(); i++) {
         aref = ainfo.getGroup(i);
         DataRef ref2 = VSUtil.getRootRef(aref, false);

         if(ref2.equals(ref) || ref2.getAttribute().equals(ref.getAttribute())){
            return aref;
         }
      }

      return null;
   }

   /**
    * Check the ref is aggregate base column or not.
    */
   private boolean isAggregateBaseColumn(List<AggregateRef> calcsubs,
                                         ColumnRef ref)
   {
      for(int i = 0; i < calcsubs.size(); i++) {
         DataRef root = VSUtil.getRootRef(calcsubs.get(i), true);

         if(root.getAttribute().equals(ref.getAttribute())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Remove the added aggregate ref alias column if table not execute it.
    */
   private void removeAggCalcColumn(TableAssembly table) {
      if(table == null || aggalias == null) {
         return;
      }

      ColumnSelection cols = table.getColumnSelection();

      for(int i = 0; i < aggalias.size(); i++) {
         int idx = cols.indexOfAttribute(aggalias.get(i));

         if(idx >= 0) {
            DataRef root = VSUtil.getRootRef(aggalias.get(i), true);

            // change the alias base column visible to true
            for(int j = 0; j < cols.getAttributeCount(); j++) {
               ColumnRef ref = (ColumnRef) cols.getAttribute(j);

               if(root.getAttribute().equals(ref.getAttribute())) {
                  ref.setVisible(true);
               }
            }

            if(aggalias.get(i).getDataRef() instanceof AliasDataRef) {
               cols.removeAttribute(idx);
            }
         }
      }

      aggalias = null;
   }

   /**
    *  Set if support pushdown.
    *  for crosstab\chart, this should be false to avoid wrong result after aggregate on aggregate,
    *  but should be true when this is used for time slider, because it won't do aggregate on aggregate.
    */
   public void setPushDown(boolean pushDown) {
      this.pushDown = pushDown;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(AbstractCrosstabVSAQuery.class);
   private boolean appended = false;
   private DataRef[] nrheaders = null;
   private DataRef[] aggrs = null;
   private DataRef[] oaggrs = null;
   private SourceInfo sinfo = null;
   private AggregateInfo groupInfo = null;
   private List<ColumnRef> aggalias = null;
   private boolean pushDown = false;
   private boolean for_bindable;
}
