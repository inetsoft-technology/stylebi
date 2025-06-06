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
package inetsoft.report.composition.graph;

import inetsoft.graph.EGraph;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.FacetCoord;
import inetsoft.graph.data.*;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.GraphtDataSelector;
import inetsoft.graph.internal.ManualOrderComparer;
import inetsoft.graph.scale.*;
import inetsoft.report.*;
import inetsoft.report.composition.graph.data.ScaleRouter;
import inetsoft.report.filter.*;
import inetsoft.report.internal.*;
import inetsoft.report.internal.binding.ExpertNamedGroupInfo;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.uql.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.log.LogManager;
import inetsoft.util.profile.ProfileUtils;

import java.io.*;
import java.text.Format;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.IntStream;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VSDataSet, the data set for one chart in viewsheet.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class VSDataSet extends AbstractDataSet implements AttributeDataSet {
   /**
    * Create an instance of VSDataSet.
    */
   public VSDataSet(TableLens data, VSDataRef[] refs) {
      super();

      this.data = this.odata = data;
      this.refs = refs == null ? new VSDataRef[0] : refs;
      data.addChangeListener(event -> VSDataSet.this.process());
      process();
   }

   /**
    * Check if the column is an aggregate column. The aggreaget column pattern
    * is aggregateName(columnName).
    */
   private static boolean isAggregateColumn(String columnName) {
      if(columnName == null || columnName.length() == 0 ||
         columnName.indexOf('(') < 0 ||
         columnName.lastIndexOf(')') != columnName.length() - 1)
      {
         return false;
      }

      String formula = columnName.substring(0, columnName.indexOf('('));
      formula = ChartAggregateRef.getBaseName(formula);

      AggregateFormula[] formulas = AggregateFormula.getFormulas();

      for(int i = 0; i < formulas.length; i++) {
         if(formula.equals(formulas[i].getFormulaName())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the data ref for the columns.
    */
   public VSDataRef[] getDataRefs() {
      return refs;
   }

   /**
    * Initialization.
    */
   private void process() {
      try {
         // for Feature #26586, add post processing time record for current report/vs.
         ProfileUtils.addExecutionBreakDownRecord(data.getReportName(),
            ExecutionBreakDownRecord.POST_PROCESSING_CYCLE, args -> {
               process0();
            });
      }
      catch(ColumnNotFoundException colNotFoundException) {
         ColumnNotFoundException thrown = LOG.isDebugEnabled() ? colNotFoundException : null;
         LogManager.getInstance().logException(
            LOG, colNotFoundException.getLogLevel(), colNotFoundException.getMessage(), thrown);
      }
      catch(Exception ex) {
         LOG.error("Failed to process vsdataset", ex);
      }
   }

   private synchronized void process0() {
      data.moreRows(TableLens.EOT);
      this.hcount = data.getHeaderRowCount();
      this.rcount = Math.max(0, data.getRowCount() - hcount);

      if(data instanceof AbstractDataSet) {
         this.rcountu = Math.max(0, ((AbstractDataSet)data).getRowCountUnprojected() - hcount);
      }
      else {
         this.rcountu = this.rcount;
      }

      this.ccount = data.getColCount();
      this.calcHeaders = new HashMap<>();

      removeCalcColumns();

      for(int i = 0; i < refs.length; i++) {
         if(refs[i] instanceof XAggregateRef) {
            XAggregateRef aref = (XAggregateRef) refs[i];

            // if aggregation is disabled (boxplot), ignore calculation
            if(aref.isAggregated() && aref.getCalculator() != null) {
               CalcColumn calc = aref.createCalcColumn();
               addCalcColumn(calc);
               calcHeaders.put(calc.getHeader(), aref.getFullName(false));
            }
         }
      }

      // Todo set the date comparison calc.

      this.hmap = new Object2IntOpenHashMap<>();
      this.hmap2 = new Object2IntOpenHashMap<>();
      this.hmap3 = new Object2IntOpenHashMap<>();
      this.hmap.defaultReturnValue(HEADER_MAP_DEFAULT_VALUE);
      this.hmap2.defaultReturnValue(HEADER_MAP_DEFAULT_VALUE);
      this.hmap3.defaultReturnValue(HEADER_MAP_DEFAULT_VALUE);

      this.cubeOption = new HashMap<>();
      this.dataMap = new HashMap<>();
      this.headers = new String[ccount];
      this.aggregated = new boolean[ccount];
      this.types = new Class[ccount];
      this.aggregated2 = new boolean[ccount];
      this.dimension = new boolean[ccount];
      this.doption = new int[ccount];
      this.comparer = new Comparator[ccount];
      this.linkcache = new FixedSizeSparseMatrix();
      this.pidx = -1; // period comparison col index
      // this.refs = refs;

      // create table for period comparison
      for(int i = 0; i < ccount; i++) {
         String header = XUtil.getHeader(data, i).toString();
         VSDataRef ref = getDataRef(header);

         if(ref instanceof XDimensionRef) {
            XDimensionRef dref = (XDimensionRef) ref;

            if((dref.getRefType() & DataRef.CUBE) != 0) {
               continue;
            }

            String[] dates = dref.getDates();

            if(dates != null && dates.length > 0) {
               pidx = i;
               break;
            }
         }
      }

      // this.data = data;

      // initialize headers first so the columns headers are availabe in the
      // next loop when this VSDataSet is used
      for(int i = 0; i < ccount; i++) {
         headers[i] = AssetUtil.format(XUtil.getHeader(data, i));
      }

      for(int i = 0; i < ccount; i++) {
         String header = headers[i];
         VSDataRef ref = getDataRef(header);

         int index = header.indexOf(".");
         hmap.put(header, i);
         int refType = ref == null ? DataRef.NONE : ref.getRefType();
         cubeOption.put(header, refType);

         if(index >= 0) {
            String header2 = header.substring(index + 1);
            hmap2.put(header2, i);
         }

         if((refType & DataRef.CUBE) == DataRef.CUBE) {
            hmap3.put(ref.getName(), i);
         }

         // trim the source prefix
         String header2 = isAggregateColumn(header) ? header : header.substring(index + 1);

         hmap2.put(header2, i);
         dimension[i] = ref instanceof XDimensionRef;
         doption[i] = -1;

         // aggregate?
         if(!dimension[i]) {
            XAggregateRef aggr = (XAggregateRef) ref;

            if(aggr != null) {
               boolean cube = (aggr.getRefType() & DataRef.CUBE) != 0;
               boolean supportsLine = false;
               aggregated[i] = aggr.isAggregateEnabled();
               aggregated2[i] = aggregated[i] || cube;

               // not aggregated viewsheet calculate ref? we should
               // use its original data type as type, otherwise data
               // type will be wrong, see bug1343630594904
               // cube measure always use as measure
               // fix bug1348455751492
               if((aggr.getRefType() & DataRef.CUBE_MEASURE) != DataRef.CUBE_MEASURE &&
                  (aggr.getFormula() == null || aggr.getFormula() == AggregateFormula.NONE))
               {
                  types[i] = Tool.getDataClass(aggr.getDataType());
               }

               if(aggr instanceof VSChartAggregateRef) {
                  supportsLine = ((VSChartAggregateRef) aggr).isSupportsLine();
               }

               if(!cube && (!aggregated[i] || supportsLine)) {
                  comparer[i] = new DefaultComparer();
               }
            }
            else if(refs != null && refs.length != 0) {
               // @by davyc, this message is useless, especially with aggregate calc
               // see bug1346079744444
               // LOG.warn("Column not found in VSDataSet: " + header);

               // fix Bug #48085, headers contains the base aggregate fields of the calc aggregates,
               // although these fields are not exist in the binding refs but still should be treaded as
               // aggregated to avoid adding them in brush selection.
               aggregated[i] = true;
               aggregated2[i] = true;
            }
         }
         // dimension
         else {
            XDimensionRef dim = (XDimensionRef) ref;
            boolean date = dim.isDateTime() && pidx != i;
               //&& (dim.getRefType() & DataRef.CUBE) == 0;

            try {
               initDataMap(header);
               comparer[i] = dim.createComparator(this);

               if(comparer[i] instanceof DimensionComparer) {
                  ((DimensionComparer) comparer[i]).setComparator(
                     Util.getColumnComparator(data, dim));
               }

               doption[i] = !date ? -1 : dim.getDateLevel();
            }
            catch(Exception ex) {
               LOG.warn("Failed to initialize comparator for column " + i, ex);
            }
         }
      }

      for(int i = 0; i < refs.length; i++) {
         addDiscreteCalc(refs[i]);
      }

      prepareLink();
      fixOrder();

      this.loc = Catalog.getCatalog().getLocale();

      if(this.loc == null) {
         this.loc = Locale.getDefault();
      }
   }

   /**
    * Prepare calc columns.
    */
   @Override
   public void prepareCalc(String dim, int[] rows, boolean calcMeasures) {
      super.prepareCalc(dim, rows, calcMeasures);

      /*
      // calc columns have changed, re-initialize links
      if(ccount + getCalcColumns().size() != link.length) {
         prepareLink();
      }
      */

      // create comparator for each sub-section
      if(subranges != null && subranges.size() > 1) {
         List<Comparator>[] comps = new List[getColCount()];
         List<int[]>[] compranges = new List[getColCount()];

         for(int i = 0; i < comps.length; i++) {
            comps[i] = new ArrayList<>();
            compranges[i] = new ArrayList<>();
         }

         for(SubColumns sub : subranges.keySet()) {
            int[] range = subranges.get(sub);

            for(XDimensionRef subdim : sub.getDimensions()) {
               int col = super.indexOfHeader(subdim.getFullName());

               if(col >= 0) {
                  Comparator comp = subdim.createComparator(this);
                  comps[col].add(comp);
                  compranges[col].add(range);
               }
            }
         }

         for(int i = 0; i < comps.length && i < comparer.length; i++) {
            if(comparer[i] != null && comps[i].size() > 0) {
               comparer[i] = new RangeComparator(comparer[i], comps[i], compranges[i]);
            }
         }
      }
   }

   /**
    * Check if is a calc column.
    */
   public boolean isCalcColumn(String name) {
      return calcHeaders == null ? false : calcHeaders.containsKey(name);
   }

   /**
    * Prepare hyperlink.
    */
   private void prepareLink() {
      link = new HashMap<>();

      for(VSDataRef ref : refs) {
         if(!(ref instanceof HyperlinkRef)) {
            continue;
         }

         Hyperlink l = ((HyperlinkRef) ref).getHyperlink();

         if(l == null) {
            continue;
         }

         String fullname = ref.getFullName();
         link.put(fullname, l);

         // discrete measure? original column share it
         if(ref instanceof XAggregateRef && ((ChartRef) ref).isMeasure()) {
            String oname = GraphUtil.getBaseName((ChartRef) ref);

            if(!fullname.equals(oname)) {
               link.put(oname, l);
            }
         }
      }
   }

   /**
    * Init data map.
    * @param col the specified column.
    */
   private void initDataMap(String col) {
      if(!isCubeTime(col)) {
         return;
      }

      for(int i = 0; i < getRowCount(); i++) {
         dataMap.put(getData(col, i), getOriginalData(col, i));
      }
   }

   /**
    * Check if is cube time dimension.
    */
   private boolean isCubeTime(String col) {
      if(cubeOption == null) {
         return false;
      }

      Integer integer = cubeOption.get(col);
      int refType = integer == null ? DataRef.NONE : integer.intValue();
      return (refType & DataRef.CUBE_TIME_DIMENSION) == DataRef.CUBE_TIME_DIMENSION;
   }

   /**
    * If dimension specifies original order, fix the ordering here.
    */
   private void fixOrder() {
      data = odata;

      // if multi-style chart, the row order is important and should not be sorted
      // here, otherwise the row ranges will cover wrong rows.
      if(!subranges.isEmpty()) {
         return;
      }

      List<VSDataRef> origcols = new ArrayList<>();
      int timeseries = -1;

      for(int i = 0; i < refs.length; i++) {
         if(!(refs[i] instanceof XDimensionRef)) {
            continue;
         }

         XDimensionRef ref = (XDimensionRef) refs[i];

         if(XSchema.isDateType(ref.getDataType()) && ref.isTimeSeries()) {
            timeseries = Util.findColumn(data, ref.getFullName());
         }

         int order = ref.getOrder();

         if((order == XConstants.SORT_ORIGINAL ||
            order == XConstants.SORT_NONE ||
            order == XConstants.SORT_SPECIFIC) &&
            !(ref.getNamedGroupInfo() instanceof ExpertNamedGroupInfo))
         {
            origcols.add(refs[i]);
         }
      }

      SummaryFilter summary = (SummaryFilter) Util.getNestedTable(data, SummaryFilter.class);

      if(origcols.size() == 0) {
         List<XTable> summaryTbls = new ArrayList<>();
         Util.listNestedTable(data, SummaryFilter.class, summaryTbls);

         // if project forward, we assume the data is sorted on the time dimension. if data
         // is not aggregated (and sorted), we explicitly sort it here. (59602)
         if(timeseries >= 0 && summaryTbls.isEmpty()) {
            data = new SortFilter(data, new int[] {timeseries});
         }

         return;
      }

      if(summary == null || !(summary.getTable() instanceof SortFilter)) {
         return;
      }

      // topN already sorted by value
      if(summary.containsTopN(-1)) {
         return;
      }

      SortFilter sorted = (SortFilter) summary.getTable();
      TableLens base = sorted.getTable();

      //@temp yanie: bug1408595053322
      //If contains aggregated CalculateRef, we need to seek base table for
      //one more time since the return base is not the real detail base but
      //still a summary table
      boolean containsAggCalc = false;

      for(int i = 0; i < refs.length; i++) {
         if(!(refs[i] instanceof XAggregateRef)) {
            continue;
         }

         XAggregateRef ref = (XAggregateRef) refs[i];
         DataRef ref2 = ref.getDataRef();

         if(ref2 instanceof CalculateRef) {
            CalculateRef ref3 = (CalculateRef) ref2;

            if(!ref3.isBaseOnDetail()) {
               containsAggCalc = true;
               break;
            }
         }
      }

      if(containsAggCalc) {
         SummaryFilter summary2 = (SummaryFilter) Util.getNestedTable(base, SummaryFilter.class);

         if(summary2 != null) {
            TableLens lens2 = summary2.getTable();

            //@by yanie: bug1415173537753
            //with MV, sometimes the base of summary2 might be ColumnMapFilter
            //use a general method to get the base
            if(lens2 instanceof SortedTable && lens2 instanceof TableFilter) {
               base = ((TableFilter) lens2).getTable();
            }
         }
      }

      ColumnIndexMap baseColumns = new ColumnIndexMap(base, true);
      ColumnIndexMap dataColumns = new ColumnIndexMap(data, true);
      // use SummaryFilter sorting to make sure the columns are ordered property
      // for project forward. (50159, 50160, 50173)
      int[] sumsorts = summary.getSortCols();
      int[] cols = IntStream.range(0, sumsorts.length).filter(col -> col < data.getColCount()).toArray();
      Comparer[] orders = Arrays.stream(sumsorts)
         .mapToObj(col -> sorted.getComparer(col)).toArray(Comparer[]::new);
      SortFilter sorted2 = new SortFilter(data, cols, true);

      for(int i = 0; i < origcols.size(); i++) {
         VSDataRef ref = origcols.get(i);
         int basecol = Util.findColumn(baseColumns, ref);
         int datacol = Util.findColumn(dataColumns, ref);

         if(basecol < 0 || datacol < 0) {
            String full = ref.getFullName();
            basecol = Util.findColumn(baseColumns, full);
            datacol = Util.findColumn(dataColumns, full);
         }

         if(basecol < 0 || datacol < 0) {
            return;
         }

         int idx = ArrayUtils.indexOf(cols, datacol);

         if(idx >= 0) {
            int order = ((XDimensionRef) ref).getOrder();

            if(order == XConstants.SORT_SPECIFIC && ref instanceof VSDimensionRef) {
               List manualSort = ((VSDimensionRef) ref).getManualOrderList();
               orders[idx] = manualSort != null && manualSort.size() > 0 ?
                  new ManualOrderComparer(manualSort.toArray()) :
                  new OriginalOrder(base, basecol);
            }
            else {
               orders[idx] = new OriginalOrder(base, basecol);
            }
         }
      }

      for(int i = 0; i < orders.length && i < cols.length; i++) {
         sorted2.setComparer(cols[i], orders[i]);
      }

      sorted2.moreRows(TableLens.EOT);
      data = sorted2;
   }

   private class CombinedComparer implements Comparer {
      public CombinedComparer(Comparer c1, Comparer c2) {
         super();
         comparer1 = c1;
         comparer2 = c2;
      }

      @Override
      public int compare(Object v1, Object v2) {
         int result = 0;

         if(comparer1 instanceof SortOrder) {
            int idx0 = ((SortOrder) comparer1).getGroupNameIndex(v1);
            int idx1 = ((SortOrder) comparer1).getGroupNameIndex(v2);
            result = idx0 - idx1;
         }
         else {
            result = comparer1.compare(v1, v2);
         }

         if(result == 0) {
            result = comparer2.compare(v1, v2);
         }

         return result;
      }

      @Override
      public int compare(double v1, double v2) {
         return compare(Double.valueOf(v1), Double.valueOf(v2));
      }

      @Override
      public int compare(float v1, float v2) {
         return compare(Float.valueOf(v1), Float.valueOf(v2));
      }

      @Override
      public int compare(long v1, long v2) {
         return compare(Long.valueOf(v1), Long.valueOf(v2));
      }

      @Override
      public int compare(int v1, int v2) {
         return compare(Integer.valueOf(v1), Integer.valueOf(v2));
      }

      @Override
      public int compare(short v1, short v2) {
         return compare(Short.valueOf(v1), Short.valueOf(v2));
      }

      private Comparer comparer1;
      private Comparer comparer2;
   }

   /**
    * Get the colunmn for period comparison.
    */
   public int getPeriodCol() {
      return pidx;
   }

   /**
    * Get the date type defined in TimeScale.
    * @return the date type defined in TimeScale, -1 if not defined.
    */
   public int getDateType(int col) {
      switch(doption[col]) {
      case DateRangeRef.YEAR_INTERVAL:
         return TimeScale.YEAR;
      case DateRangeRef.QUARTER_INTERVAL:
         return TimeScale.QUARTER;
      case DateRangeRef.MONTH_INTERVAL:
         return TimeScale.MONTH;
      case DateRangeRef.WEEK_INTERVAL:
         return TimeScale.WEEK;
      case DateRangeRef.DAY_INTERVAL:
         return TimeScale.DAY;
      case DateRangeRef.HOUR_INTERVAL:
         return TimeScale.HOUR;
      case DateRangeRef.MINUTE_INTERVAL:
         return TimeScale.MINUTE;
      case DateRangeRef.SECOND_INTERVAL:
         return TimeScale.SECOND;
      case DateRangeRef.QUARTER_OF_YEAR_PART:
         return TimeScale.QUARTER;
      case DateRangeRef.MONTH_OF_YEAR_PART:
         return TimeScale.MONTH;
      case DateRangeRef.WEEK_OF_YEAR_PART:
         return TimeScale.WEEK;
      case DateRangeRef.DAY_OF_MONTH_PART:
         return TimeScale.DAY;
      case DateRangeRef.DAY_OF_WEEK_PART:
         return TimeScale.DAY;
      case DateRangeRef.HOUR_OF_DAY_PART:
         return TimeScale.HOUR;
      //@by stephenwebster
      //If no date grouping is selected use the day time scale.
      case DateRangeRef.NONE_INTERVAL:
         return TimeScale.DAY;
      default:
         return -1;
      }
   }

   /**
    * Find the VSDataRef by its full name.
    */
   public VSDataRef getDataRef(String header) {
      for(int i = 0; i < refs.length; i++) {
         if(refs[i].getFullName().equalsIgnoreCase(header)) {
            return refs[i];
         }

         if(refs[i] instanceof XAggregateRef) {
            String pname = ((XAggregateRef) refs[i]).getFullName(false);

            if(pname.equalsIgnoreCase(header)) {
               return refs[i];
            }
         }
      }

      return null;
   }

   @Override
   protected synchronized int indexOfHeader0(String col, boolean all) {
      if(Tool.equals(col0, col)) {
         return idx0;
      }

      col0 = col;
      int val = hmap.getInt(col0);

      if(val == HEADER_MAP_DEFAULT_VALUE) {
         val = hmap2.getInt(col);
      }

      if(val == HEADER_MAP_DEFAULT_VALUE) {
         val = hmap3.getInt(col);
      }

      idx0 = val == HEADER_MAP_DEFAULT_VALUE ? -1 : val;
      idx0 = idx0 >= ccount ? -1 : idx0;
      return idx0;
   }

   /**
    * Return the data at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   @Override
   protected Object getData0(int col, int row) {
      Object value = data.getObject(row + hcount, col);

      if(value instanceof DCMergeDatesCell) {
         value = ((DCMergeDatesCell) value).getMergeLabelCell();
      }

      return value;
   }

   /**
    * Return the original MemberObject at the specified cell. The data is not
    * transformed.
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the MemberObject at the specified cell.
    */
   public Object getMemberObject(int col, int row) {
      if(data instanceof MemberObjectTableLens) {
         return ((MemberObjectTableLens) data).getMemberObject(row + hcount, col);
      }

      return null;
   }

   /**
    * Return the original data at the specified cell. The data is not
    * transformed.
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   public Object getOriginalData(int col, int row) {
      if((col < data.getColCount())) {
         if(data instanceof DataTableLens) {
            return ((DataTableLens) data).getData(row + hcount, col);
         }

         if(row + hcount >= data.getRowCount()) {
            return null;
         }

         return data.getObject(row + hcount, col);
      }

      return getData(col, row);
   }

   /**
    * Return the original data at the specified cell. The data is not
    * transformed.
    * @param col the specified column name.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   public Object getOriginalData(String col, int row) {
      int cidx = indexOfHeader(col);

      if(cidx == -1) {
         LOG.warn("Column not found: " + col);
         throw new MessageException(Catalog.getCatalog().getString(
            "common.invalidTableColumn", col));
      }

      return getOriginalData(cidx, row);
   }

   /**
    * Get the field value used for analysis.
    */
   private VSFieldValue getFieldValue(String col, int row) {
      Object obj = getOriginalData(col, row);

      // here don't to use original data of DCMergeDatePartFilter.MergePartCell,
      // else will lost the temp date parts data. For exmpale, for MonthOfQuarter column,
      // DCMergeDatePartFilter.MergePartCell value is 1-2, which means the second month of
      // the first quarter in a year, we need all the date parts to create right filter.
      if(obj instanceof DCMergeDatesCell) {
         obj = ((DCMergeDatesCell) obj).getOriginalData();
      }

      VSFieldValue pair = new VSFieldValue(col, obj, true);
      pair.setStringData(obj instanceof String);

      return pair;
   }

   /**
    * Get the field values for drill to detail condition.
    * @param hier true if this chart dimensions are hierarchical (e.g. treemap)
    */
   public VSFieldValue[][] getFieldValues(int row, String[] headers, String[] headers2,
                                          boolean expothers, boolean hier)
   {
      if(row >= getRowCount()) {
         return new VSFieldValue[0][];
      }

      List<VSFieldValue[]> list = new ArrayList<>();
      int max = 0;
      SubColumns subcols = getSubColumns(row);

      for(int j = 0; j < headers.length; j++) {
         int cidx = indexOfHeader(headers[j]);

         // discard aggregate brushing
         if(cidx < 0 || /*getPeriodCol() == cidx ||*/
            headers[j].startsWith(SparklinePoint.PREFIX) ||
            isRealAggregate(headers[j]) ||
            // discard calc column value
            isCalcColumn(headers[j]))
         {
            continue;
         }

         if(!Tool.equals(headers[j], headers2[j])) {
            continue;
         }

         // ignore columns if it's not in the sub-dataset
         if(subcols != null && !subcols.contains(headers[j])) {
            continue;
         }

         Object val = getData(cidx, row);

         if(isOthers(headers[j], val + "") && expothers) {
            VSFieldValue[] ofvals = getOthersFieldValues(headers[j], row, hier);
            list.add(ofvals);
            max = Math.max(max, ofvals.length);
         }
         else {
            if(cidx < data.getColCount() && row + hcount >= data.getRowCount()) {
               continue;
            }

            VSFieldValue pair = getFieldValue(headers[j], row);
            list.add(new VSFieldValue[] {pair});
            max = Math.max(max, 1);
         }
      }

      List<List<VSFieldValue>> pts = new ArrayList<>();

      for(int i = 0; i < max; i++) {
         pts.add(new ArrayList<>());
      }

      // create permutation of the values
      for(VSFieldValue[] vals : list) {
         if(vals.length > 0) {
            for(int j = 0; j < pts.size(); j++) {
               List<VSFieldValue> last = pts.get(j);
               VSFieldValue val = vals[j % vals.length];
               last.add(val);
            }
         }
      }

      VSFieldValue[][] arr = new VSFieldValue[pts.size()][];

      for(int i = 0; i < arr.length; i++) {
         arr[i] = pts.get(i).toArray(new VSFieldValue[0]);
      }

      return arr;
   }

   /**
    * Get the field value used for analysis.
    * @param hier true if treemap
    */
   public VSFieldValue[] getFieldValues(String col, String value,
                                        boolean expothers, boolean hier)
   {
      return getFieldValues(col, value, expothers, hier, -1);
   }

   /**
    * Get the field value used for analysis.
    * @param hier true if treemap
    */
   public VSFieldValue[] getFieldValues(String col, String value,
                                        boolean expothers, boolean hier, int row)
   {
      if(isCalcColumn(col)) {
         return new VSFieldValue[0];
      }

      Integer integer = cubeOption.get(col);
      int refType = integer == null ? DataRef.NONE : integer;

      if((refType & DataRef.CUBE) == 0 || refType == DataRef.CUBE_MODEL_DIMENSION) {
         if(isOthers(col, value) && expothers) {
            return getOthersFieldValues(col, row, hier);
         }

         VSDataRef ref = getDataRef(col);
         boolean dcMergeCell = ref instanceof VSDimensionRef &&
            ((VSDimensionRef) ref).getDcMergeGroup() != null;

         if(dcMergeCell) {
            return getDcMergeCellFieldValue(col, value).toArray(new VSFieldValue[0]);
         }
         else {
            VSFieldValue field = getFieldValue(col, value);
            return field == null ? new VSFieldValue[0] : new VSFieldValue[] {field};
         }

      }

      List<VSFieldValue> vsFieldValues = new ArrayList<>();
      int cidx = indexOfHeader(col);
      Object data = value;

      if(cidx >= 0 && DateRangeRef.isDateTime(doption[cidx])) {
         try {
            data = Tool.getData(XSchema.TIME_INSTANT, value);
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      for(int i = 0; i < getRowCount(); i++) {
         Object obj2 = getData(cidx, i);

         if(checkValueEquals(obj2, data)) {
            vsFieldValues.add(getFieldValue(col, i));
         }
      }

      return vsFieldValues.toArray(new VSFieldValue[0]);
   }

   /**
    * Check if the value is the 'Others' value.
    */
   public boolean isOthers(String col, String value) {
      if("Others".equals(value)) {
         for(VSDataRef ref : refs) {
            // there maybe multiple dimensions of same column (e.g. color & group)
            if(ref instanceof VSDimensionRef && ref.getFullName().equalsIgnoreCase(col) &&
               ((VSDimensionRef) ref).isGroupOthers())
            {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Get the field values in the others group.
    */
   private VSFieldValue[] getOthersFieldValues(String col, int row, boolean hier) {
      String others = Catalog.getCatalog().getString("Others");
      Set all = new HashSet(); // topn values (non-others)
      int cidx = indexOfHeader(col);

      all_loop:
      for(int i = 0; i < getRowCount(); i++) {
         // collect non-others value for the same group only
         if(row >= 0) {
            for(int c = 0; c < dimension.length; c++) {
               if(hier && c == cidx) {
                  break;
               }

               if(dimension[c] && c != cidx) {
                  Object v1 = getData(c, row);
                  Object v2 = getData(c, i);

                  if(!Tool.equals(v1, v2)) {
                     continue all_loop;
                  }
               }
            }
         }

         Object val = getData(cidx, i);

         if(!others.equals(val)) {
            all.add(val);
         }
      }

      List<XTable> summaryTbls = new ArrayList<>();
      Util.listNestedTable(data, SummaryFilter.class, summaryTbls);

      Set otherset = new HashSet();

      // find the values in the others group (!topn)
      for(XTable sub : summaryTbls) {
         SummaryFilter summary = (SummaryFilter) sub;
         TableLens base = summary.getTable();
         int bidx = Util.findColumn(base, col);

         if(bidx >= 0) {
            for(int i = 1; base.moreRows(i); i++) {
               Object val = base.getObject(i, bidx);

               if(!all.contains(val)) {
                  String text = Tool.getDataString(val);
                  VSFieldValue pair = new VSFieldValue(col, text);
                  pair.setStringData(val instanceof String);
                  otherset.add(pair);
               }
            }
         }
      }

      return (VSFieldValue[]) otherset.toArray(new VSFieldValue[0]);
   }

   /**
    * Get the field value used for analysis.
    */
   private List<VSFieldValue> getDcMergeCellFieldValue(String col, String value) {
      List<VSFieldValue> list = new ArrayList<>();
      int cidx = indexOfHeader(col);

      if(cidx < 0) {
         return null;
      }

      if(DateRangeRef.isDateTime(doption[cidx])) {
         Object obj = value;

         for(int i = 0; i < getRowCount(); i++) {
            Object obj2 = getData(cidx, i);

            if(obj2 instanceof DCMergeDatesCell) {
               obj2 = ((DCMergeDatesCell) obj2).getMergeLabelCell();
            }

            if(checkValueEquals(obj2, obj)) {
               list.add(getFieldValue(col, i));
            }
         }
      }

      if(list.isEmpty()) {
         list.add(new VSFieldValue(col, value, true));
      }

      return list;
   }

   /**
    * Get the field value used for analysis.
    */
   private VSFieldValue getFieldValue(String col, String value) {
      int cidx = indexOfHeader(col);

      if(cidx < 0) {
         return null;
      }

      // for date/time, make sure the value used for comparison actually matches
      // the value in the dataset. but allow the original string to be used in case
      // the value dooesn't exist (could happen in TimeScale). (43944)
      if(DateRangeRef.isDateTime(doption[cidx])) {
         Object obj = Tool.getData(XSchema.TIME_INSTANT, value);

         for(int i = 0; i < getRowCount(); i++) {
            Object obj2 = getData(cidx, i);

            if(checkValueEquals(obj2, obj)) {
               return getFieldValue(col, i);
            }
         }
      }

      return new VSFieldValue(col, value, true);
   }

   /**
    * Get the comparer to sort data at the specified column.
    * @param col the specified column.
    * @return the comparer to sort data at the specified column.
    */
   @Override
   protected Comparator getComparator0(String col) {
      // for calc field, use original column order
      col = convertField(col);
      int cidx = indexOfHeader(col);

      if(cidx < 0 || cidx >= comparer.length) {
         return null;
      }

      return comparer[cidx];
   }

   /**
    * Set the comparer to sort data at the specified column.
    * @param col the specified column.
    * @param comp the comparer to sort data at the specified column.
    */
   public void setComparator(String col, Comparator comp) {
      int cidx = indexOfHeader(col);

      if(cidx >= 0) {
         comparer[cidx] = comp;
      }
   }

   /**
    * Get mapped data.
    * @param obj key object.
    * @return mached original object if any.
    */
   public Object getMappedData(Object obj) {
      return dataMap.get(obj);
   }

   /**
    * Return the header at the specified column.
    * @param col the specified column index.
    * @return the header at the specified column.
    */
   @Override
   protected String getHeader0(int col) {
      return headers[col];
   }

   /**
    * Get the data type of the column.
    */
   @Override
   protected Class getType0(String col) {
      int cidx = indexOfHeader(col);
      VSDataRef ref = getDataRef(col);
      XNamedGroupInfo ngInfo = ref instanceof XDimensionRef ?
         ((XDimensionRef) ref).getRealNamedGroupInfo() : null;

      if(ngInfo != null && !ngInfo.isEmpty()) {
         return String.class;
      }

      if(cidx >= 0 && DateRangeRef.isDateTime(doption[cidx])) {
         return Date.class;
      }

      if(cidx >= 0 && cidx < types.length && types[cidx] != null) {
         return types[cidx];
      }

      Class cls = data.getColType(cidx);

      if((String.class.equals(cls) || cls == null) && isMeasure(col) &&
         isRealAggregate(col))
      {
         return Double.class;
      }

      return cls;
   }

   /**
    * Check if the column is measure.
    * @param col the specified column name.
    * @return <tt>true</true> if is measure, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isMeasure0(String col) {
      int cidx = indexOfHeader(col);
      return !dimension[cidx];
   }

   /**
    * Check if the column is an aggreagte column.
    * @param col the specified column name.
    * @return <tt>true</true> if is, <tt>false</tt> otherwise.
    */
   public boolean isAggregate(String col) {
      // for calc column, same as original field
      col = convertField(col);
      int cidx = indexOfHeader(col);
      return aggregated[cidx];
   }

   /**
    * Check if the column is an aggreagte column.
    * @param col the specified column name.
    * @return <tt>true</true> if is, <tt>false</tt> otherwise.
    */
   public boolean isRealAggregate(String col) {
      // for calc column, same as original field
      col = convertField(col);
      int cidx = indexOfHeader(col);

      if(cidx < 0) {
         return false;
      }

      return aggregated2[cidx];
   }

   /**
    * Return the number of rows in the chart lens.
    * The number of rows includes the header rows, including projected
    * @return number of rows in the chart lens, including projected
    */
   @Override
   protected int getRowCount0() {
      return rcount;
   }

   /**
    * Return the number of un-projected rows in the chart lens.
    * The number of rows includes the header rows
    * @return number of rows in the chart lens
    */
   @Override
   protected int getRowCountUnprojected0() {
      return rcountu;
   }

   /**
    * Return the number of columns in the chart lens.
    * @return number of columns in the chart lens.
    */
   @Override
   protected int getColCount0() {
      return ccount;
   }

   /**
    * Get the hyperlink at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the hyperlink at the specified cell.
    */
   @Override
   public HRef getHyperlink(int col, int row) {
      if(links != null) {
         Object link = links.get(row, col);

         if(link instanceof HRef) {
            return (HRef) link;
         }
      }

      if(link == null || row < 0 || col < 0 || row >= rcount) {
         return null;
      }

      String header = getHeader(col);
      Hyperlink lref = link.get(header);

      if(lref == null) {
         return null;
      }

      Object ref = linkcache.get(row, col);

      if(ref == SparseMatrix.NULL) {
         ref = getHyperlink0(row, lref);
         linkcache.set(row, col, ref);
      }

      return (Hyperlink.Ref) ref;
   }

   @Override
   public void setHyperlink(int col, int row, HRef link) {
      if(links == null) {
         links = new SparseMatrix();
      }

      links.set(row, col, link);
   }

   /**
    * Set the hyperlink at the specified cell.
    * @param col the specified row index.
    * @param hlink the hyperlink at the specified cell.
    */
   public void setHyperlink(int col, Hyperlink hlink) {
      if(link == null || col < 0) {
         return;
      }

      if(linkcache == null || hlink == null) {
         return;
      }

      String header = getHeader(col);
      link.put(header, hlink);
      //linkcache.set(0, col, new Hyperlink.Ref(hlink));
   }

   /**
    * Get the hyperlink at the specified cell.
    * @param row the specified row index.
    * @param link the specified hyperlink.
    * @return the executed hyperlink.
    */
   private HRef getHyperlink0(int row, Hyperlink link) {
      Map map = new HashMap();

      for(int i = 0; i < getColCount(); i++) {
         Object obj = getData(i, row);

         if(i < headers.length) {
            String header = headers[i];

            if(i < refs.length && refs[i] instanceof GeoRef) {
               header = GeoRef.getBaseName(header);
            }

            map.put(header, obj);
         }
         else {
            map.put(getHeader(i), obj);
         }
      }

      return new Hyperlink.Ref(link, map);
   }

   /**
    * Get the hyperlink at the specified cell.
    * @param col the specified column name.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   @Override
   public HRef getHyperlink(String col, int row) {
      if(row < 0 || row >= rcount) {
         return null;
      }

      // use orginal column instead of calc column
      //col = convertField(col);
      int cidx = indexOfHeader(col);

      if(cidx < 0) {
         return null;
      }

      return getHyperlink(cidx, row);
   }

   /**
    * Get the drill hyperlinks at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the link at the specified cell.
    */
   @Override
   public HRef[] getDrillHyperlinks(int col, int row) {
      if(row < 0 || col < 0 || row >= rcount || col >= ccount) {
         return new Hyperlink.Ref[0];
      }

      if(data == null) {
         return new Hyperlink.Ref[0];
      }

      // convert to data's row
      int brow = data.getHeaderRowCount() + row;
      XDrillInfo dinfo = data.getXDrillInfo(brow, col);

      if(dinfo != null) {
         int size = dinfo.getDrillPathCount();
         Hyperlink.Ref[] refs = new Hyperlink.Ref[size];
         DataRef dcol = dinfo.getColumn();

         for(int k = 0; k < size; k++) {
            DrillPath path = dinfo.getDrillPath(k);
            refs[k] = new Hyperlink.Ref(path, data, brow, col);
            DrillSubQuery query = path.getQuery();
            String queryParam = null;

            if(query != null) {
               refs[k].setParameter(StyleConstants.SUB_QUERY_PARAM, getData(col, row));

               if(dcol != null) {
                  queryParam = Util.findSubqueryVariable(query, dcol.getName());
               }

               if(queryParam == null) {
                  String tableHeader = getTable().getColumnIdentifier(col);
                  tableHeader = tableHeader == null ?
                     (String) Util.getHeader(getTable(), col) : tableHeader;
                  queryParam = Util.findSubqueryVariable(query, tableHeader);
               }

               if(queryParam != null) {
                  refs[k].setParameter(StyleConstants.SUB_QUERY_PARAM_PREFIX + queryParam,
                     getData(col, row));
               }

               Iterator<String> it = query.getParameterNames();
               ColumnIndexMap columnIndexMap = new ColumnIndexMap(getTable(), true);

               while(it.hasNext()) {
                  String qvar = it.next();

                  if(Tool.equals(qvar, queryParam)) {
                     continue;
                  }

                  String header = query.getParameter(qvar);
                  int cidx = Util.findColumn(columnIndexMap, header);

                  if(cidx < 0) {
                     continue;
                  }

                  refs[k].setParameter(StyleConstants.SUB_QUERY_PARAM_PREFIX + qvar,
                     getData(cidx, row));
               }

               GraphUtil.addDashboardParameters(refs[k], linkVarTable, selections);
            }
         }

         return refs;
      }

      return new Hyperlink.Ref[0];
   }

   /**
    * Get the drill hyperlinks at the specified cell.
    * @param col the specified column name.
    * @param row the specified row index.
    * @return the link at the specified cell.
    */
   @Override
   public HRef[] getDrillHyperlinks(String col, int row) {
      if(row < 0 || row >= rcount) {
         return new Hyperlink.Ref[0];
      }

      int cidx = indexOfHeader(col);

      if(cidx < 0) {
         return new Hyperlink.Ref[0];
      }

      return getDrillHyperlinks(cidx, row);
   }

   /**
    * Get the base table lens.
    * @return the base table lens.
    */
   public TableLens getTable() {
      return data;
   }

   /**
    * Get the per cell format.
    * @param row row number.
    * @param col column number.
    * @return format for the specified cell.
    */
   @Override
   public Format getFormat(int col, int row) {
      // if this is calc row (e.g. filled time series), use the default format of the
      // base column. (58169)
      if(row >= rcount) {
         row = rcount - 1;
      }

      if(row < 0 || col < 0 || row >= rcount || col >= ccount) {
         return null;
      }

      if(data instanceof AbstractTableLens) {
         ((AbstractTableLens) data).setLocal(loc);
      }

      return data.getDefaultFormat(row + hcount, col);
   }

   /**
    * Get the per cell format.
    * @param col the specified column name.
    * @param row the specified row index.
    * @return format for the specified cell.
    */
   @Override
   public Format getFormat(String col, int row) {
      //col = convertField(col);
      int cidx = indexOfHeader(col, true);

      if(cidx < 0) {
         return null;
      }

      VSDataRef ref = getDataRef(col);

      if(ref instanceof XDimensionRef) {
         XDimensionRef dim = (XDimensionRef) ref;

         /* quarter of year may be formatted into 1st, 2nd, .. so it's meaningful. (61365)
         // if grouping to part (e.g. day-of-month), don't format as date
         if((dim.getDateLevel() & XConstants.PART_DATE_GROUP) != 0) {
            return null;
         }
          */
         // date part using CalculateRef
         if(XSchema.isNumericType(dim.getDataType())) {
            return null;
         }
      }
      else if(ref instanceof ChartAggregateRef) {
         final ChartAggregateRef aggregateRef = (ChartAggregateRef) ref;
         String type1 = ref.getDataType();
         DataRef ref2 = aggregateRef.getDataRef();
         String type2 = ref2 == null ? null : ref2.getDataType();
         AggregateFormula formula = aggregateRef.getFormula();

         // don't apply date format for COUNT, etc..
         if((XSchema.isDateType(type1) || XSchema.isDateType(type2))
            && !Objects.equals(type1, type2) ||
            formula == AggregateFormula.COUNT_ALL ||
            formula == AggregateFormula.COUNT_DISTINCT)
         {
            return null;
         }

         Calculator calc = aggregateRef.getCalculator();

         // calc field, use base column for format
         if(calc != null && col.equals(aggregateRef.getFullName())) {
            if(calc.isPercent()) {
               return NumberFormat.getPercentInstance();
            }

            // inherit format from the base column
            cidx = indexOfHeader(aggregateRef.getFullName(false));
         }
      }

      return getFormat(cidx, row);
   }

   /**
    * Set the order of values in the column. This can be used in javascript
    * to set the explicit ordering of values.
    */
   public void setOrder(String col, Object[] list) {
      int cidx = indexOfHeader(col);

      if(cidx >= 0) {
         ManualOrderComparer comp = new ManualOrderComparer(list);
         comp.setDefaultComparator(comparer[cidx]);
         comparer[cidx] = comp;
      }
   }

   /**
    * Get the hyperlink definition the specified column.
    * @param col the specified column name.
    * @return the hyperlink at the specified column.
    */
   public Hyperlink getHyperlink(String col) {
      return link == null || col == null ? null : link.get(col);
      /*
      //col = convertField(col);
      int cidx = indexOfHeader(col);

      // @by larryl, in the case of facet chart, the calc won't be
      // initialized because it's handed by the sub-dataset. But we need
      // to get the hyperlink from the VSDataSet, so we look for it
      // explicitly
      if(cidx < 0) {
         for(int i = 0; i < getCalcColumns().size(); i++) {
            CalcColumn calc = getCalcColumns().get(i);

            if(calc.getHeader().equals(col)) {
               cidx = i + getColCount();
            }
         }
      }

      return (cidx >= 0 && cidx < link.length && link != null) ?
         link[cidx] : null;
      */
   }

   /**
    * Convert calc column to original column.
    */
   private String convertField(String cfield) {
      return isCalcColumn(cfield) ? calcHeaders.get(cfield) : cfield;
   }

   /**
    * Read object.
    */
   private void readObject(ObjectInputStream in)
      throws ClassNotFoundException, IOException
   {
      in.defaultReadObject();
      data = DataSerializer.readTable(in, null);
   }

   /**
    * Write object.
    */
   private void writeObject(ObjectOutputStream out) throws IOException {
      out.defaultWriteObject();
      DataSerializer.writeTable(out, data, false);
   }

   /**
    * Check the source target value is same with souce value or not.
    */
   private boolean checkValueEquals(Object tvalue, Object svalue) {
      if(Tool.equals(tvalue, svalue)) {
         return true;
      }

      if(tvalue != null && svalue != null) {
         return Tool.equals(tvalue.toString(), svalue.toString());
      }

      return false;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Object clone() {
      VSDataSet data = (VSDataSet) super.clone();

      if(comparer != null) {
         data.comparer = comparer.clone();
      }

      return data;
   }

   /**
    * Generator dimension scale map for this data set.
    */
   @Override
   public void prepareGraph(EGraph graph, Coordinate coord, DataSet dataset) {
      Map<String, Scale> scalesMap = getVisualScaleMap(graph);
      addCoordScale(scalesMap, coord);
      router = new HashMap<>();
      dataset = dataset == null ? this : dataset;

      /* @by larryl, we create a DataSetRouter later if router is not set.
         If we set the router here, two graphs (with >1 measures) with same
         binding but different 'Separated' status will use DataSetRouter for
         separated drawing, and ScaleRouter for single graph. If there are
         missing points on a date scale, the results will be different.
         It seems the DataSetRouter is more accurate since a missing value
         in the data will be treated as a missing value in DataSetRouter, but
         the ScaleRouter will find the previous value regardless of whether
         a value exists in the dataset, and results a 100% when displaying
         change as percentage.
         changed to use ScaleRouter for time scale since missing items are
         shown on the scale and it shouldn't be treated as missing. (51515)
      */

      for(String f : scalesMap.keySet()) {
         Scale s = scalesMap.get(f);

         if(s instanceof TimeScale) {
            // make sure values are initialized and with full data. (51857, 51856)
            TimeScale scale = (TimeScale) s;
            int opt = s.getScaleOption();
            Number incr = scale.getIncrement();
            GraphtDataSelector selector = scale.getGraphDataSelector();

            scale.setIncrement(1);
            // need to include all data so the previous value can be found even
            // if it's not plotted.
            scale.setGraphDataSelector(null);
            s.setScaleOption(opt & ~Scale.NO_LEADING_NULL_VAR & ~Scale.NO_TRAILING_NULL_VAR &
               ~Scale.NO_NULL);

            s.init(dataset);

            router.put(f, new ScaleRouter(s, getComparator(f)));
            s.setScaleOption(opt);
            scale.setIncrement(incr);
            scale.setGraphDataSelector(selector);
         }
      }
   }

   /**
    * Get all aesthetic frame's scale map.
    */
   private static Map<String, Scale> getVisualScaleMap(EGraph graph) {
      Map<String, Scale> scalesMap = new HashMap();

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);
         VisualFrame[] frames = {elem.getColorFrame(), elem.getSizeFrame(),
                                 elem.getShapeFrame(), elem.getTextureFrame(),
                                 elem.getLineFrame()
         };

         for(int k = 0; k < frames.length; k++) {
            if(frames[k] != null && frames[k].getField() != null &&
               !(frames[k].getScale() instanceof LinearScale))
            {
               scalesMap.put(frames[k].getField(), frames[k].getScale());
            }
         }
      }

      return scalesMap;
   }

   /**
    * Add all coord scales to specified map.
    */
   private static void addCoordScale(Map<String, Scale> map, Coordinate coord) {
      // passing in top coord, get all sub. (51813)
      if(coord instanceof FacetCoord) {
         Arrays.stream(coord.getScales())
            .filter(s -> s.getFields().length > 0)
            .forEach(s -> map.put(s.getFields()[0], s));
         return;
      }

      // inner coord, go up to top coord.
      while(coord != null && !(coord instanceof FacetCoord)) {
         Scale[] scales = coord.getScales();

         for(int i = 0; i < scales.length; i++) {
            Scale scale = scales[i];

            if(scale != null && scale.getFields().length > 0) {
               map.put(scale.getFields()[0], scale);
            }
         }

         coord = coord.getParentCoordinate();
      }
   }

   /**
    * Get router.
    */
   public Router getRouter(String field) {
      return router.get(field);
   }

   /**
    * Set router.
    */
   public void setRouter(String field, Router router) {
      this.router.put(field, router);
   }

   /**
    * Set the range of rows for a sub-table.
    * @param subcols the columns in the sub table.
    * @param range an array of two items, start row and end row (non-inclusive).
    */
   public void setSubRange(SubColumns subcols, int[] range) {
      // if data set serialize out, and read back, subranges will be null
      if(subranges != null) {
         subranges.put(subcols, range);
         // make sure sorting is not applied to multi-style. (62345)
         fixOrder();
      }
   }

   /**
    * Get the range for the sub-table.
    */
   public int[] getSubRange(SubColumns subcols) {
      return subranges == null ? null : subranges.get(subcols);
   }

   /**
    * Get the range of rows for a sub-table.
    */
   public int[] getSubRange(GraphColumns subcols) {
      if(subranges != null) {
         for(SubColumns key : subranges.keySet()) {
            if(key.match(subcols)) {
               return subranges.get(key);
            }
         }
      }

      return null;
   }

   /**
    * Find the row range from VSDataSet or SubDataSet containing a VSDataSet.
    */
   public static int[] getSubRange(DataSet data, GraphColumns subcols) {
      if(data instanceof VSDataSet) {
         return ((VSDataSet) data).getSubRange(subcols);
      }
      else if(data instanceof SubDataSet) {
         SubDataSet sub = (SubDataSet) data;
         DataSet base = sub.getDataSet();

         if(base instanceof VSDataSet) {
           int[] range = ((VSDataSet) base).getSubRange(subcols);

           if(range != null) {
              range = new int[] { sub.getRowFromBase(range[0]), sub.getRowFromBase(range[1]) };

              // if the original range is not in SubDataSet, find where it would be. (61185)
              if(range[0] < 0) {
                 range[0] = -range[0] - 1;
              }

              if(range[1] < 0) {
                 range[1] = -range[1] - 1;
              }
           }

            return range;
         }
      }

      return null;
   }

   /**
    * Get the sub-range columns.
    */
   public Set<SubColumns> getSubColumns() {
      return subranges == null ? new HashSet<>() : subranges.keySet();
   }

   /**
    * Find the columns in the range in case this data set is concatenated
    * from multiple queries (multi-aesthetic).
    */
   public SubColumns getSubColumns(int row) {
      if(subranges != null) {
         for(SubColumns subcols : subranges.keySet()) {
            int[] range = subranges.get(subcols);

            if(row >= range[0] && row < range[1]) {
               return subcols;
            }
         }
      }

      return null;
   }

   /**
    * Add discrete calc column.
    */
   private void addDiscreteCalc(VSDataRef ref) {
      if(!(ref instanceof ChartAggregateRef)) {
         return;
      }

      ChartAggregateRef cRef = (ChartAggregateRef) ref;

      if(cRef.isDiscrete()) {
         String alias = GraphUtil.getName(cRef);

         // if discrete column already in the base data, don't add calc column
         if(Arrays.stream(headers).anyMatch(c -> c.equals(alias))) {
            return;
         }

         ChartAggregateRef cRefCopy = (ChartAggregateRef) cRef.clone();
         cRefCopy.setDiscrete(false);
         String field = GraphUtil.getName(cRefCopy);
         String base = cRefCopy.getFullName(false);
         Class type = null;
         List<CalcColumn> calcs = getCalcColumns();

         for(CalcColumn calc : calcs) {
            if(calc.getHeader().equals(field)) {
               type = calc.getType();
               break;
            }
         }

         if(type == null) {
            type = getType(field);
         }

         addCalcColumn(new DiscreteColumn(alias, field, type));
         calcHeaders.put(alias, base);
      }
   }

   @Override
   public String toString() {
      return super.toString() + "(" + data.getRowCount() + ")";
   }

   @Override
   protected boolean isDynamicColumns() {
      if(data != null) { // use the correct variable
         TableLens temp = data;
         while(temp instanceof TableFilter) {
            if(data instanceof XNodeTableLens ? ((XNodeTableLens)data).isDynamicColumns() : data.isDynamicColumns()) {
               return true;
            }
            temp = ((TableFilter) temp).getTable();
         }
         if(temp != null) {
            return temp.isDynamicColumns();
         }
      }
      return false;
   }

   /**
    * Set varaible table.
    * @param vtable the variable table.
    */
   public void setLinkVarTable(VariableTable vtable) {
      this.linkVarTable = vtable;
   }

   /**
    * Set link selections.
    */
   public void setLinkSelections(Hashtable sel) {
      selections = sel;
   }

   /**
    * Discrete calc column, just used to expand data column.
    */
   private static class DiscreteColumn implements CalcColumn {
      public DiscreteColumn(String alias, String field, Class type) {
         this.alias = alias;
         this.field = field;
         this.allfield = BrushDataSet.ALL_HEADER_PREFIX + field;
         this.type = type == null ? Double.class : type;
      }

      @Override
      public Object calculate(DataSet data, int row, boolean first, boolean last) {
         Object obj = data.getData(field, row);

         if(obj != null) {
            return obj;
         }

         int cidx = data.indexOfHeader(allfield);

         if(cidx >= 0) {
            obj = data.getData(cidx, row);
         }

         return obj;
      }

      @Override
      public String getHeader() {
         return alias;
      }

      @Override
      public Class getType() {
         return type;
      }

      @Override
      public boolean isMeasure() {
         return false;
      }

      @Override
      public String getField() {
         return field;
      }

      private String alias;
      private String field;
      private String allfield;
      private Class type;
   }

   private static class RangeComparator implements DataSetComparator, Serializable {
      public RangeComparator(Comparator comp, List<Comparator> subcomps, List<int[]> ranges) {
         this.comp = comp;
         this.subcomps = subcomps;
         this.ranges = ranges;
      }

      @Override
      public Comparator getComparator(int row) {
         Comparator comp = this.comp;

         for(int i = 0; i < ranges.size(); i++) {
            if(row >= ranges.get(i)[0] && row < ranges.get(i)[1]) {
               comp = subcomps.get(i);
               break;
            }
         }

         return DataSetComparator.getComparator(comp, data);
      }

      @Override
      public int compare(DataSet data, int row1, int row2) {
         // this will never be called
         return 0;
      }

      @Override
      public int compare(Object v1, Object v2) {
         return comp.compare(v1, v2);
      }

      @Override
      public DataSetComparator getComparator(DataSet data) {
         if(this.data != data) {
            this.data = data;
            return new RangeComparator(DataSetComparator.getComparator(comp, data),
                                       subcomps, ranges);
         }

         return this;
      }

      private Comparator comp;
      private DataSet data;
      private List<Comparator> subcomps;
      private List<int[]> ranges;
   }

   private transient TableLens data; // table data
   private transient TableLens odata; // original data
   private VSDataRef[] refs;
   private int hcount; // header row count
   private int rcount; // row count
   private int rcountu; // row count unprojected
   private int ccount; // col count
   private int pidx; // period comparison index
   private Object2IntOpenHashMap<Object> hmap; // header map, header -> idx
   private Object2IntOpenHashMap<Object> hmap2; // trimmed header map, trimmed header -> idx
   private Object2IntOpenHashMap<Object> hmap3; // header map, header(ref.getName()) -> idx
   private String[] headers; // headers
   private boolean[] dimension; // dimension or aggregate flag
   private boolean[] aggregated; // aggregated or not flag
   private boolean[] aggregated2; // assumed to be aggregated for cube columns
   private Class[] types;
   private int[] doption; // date option
   private Comparator[] comparer; // comparer
   private Map<String, Hyperlink> link; // hyperlink
   private transient FixedSizeSparseMatrix linkcache; // hyperlink cache

   private transient String col0; // cached column name
   private transient int idx0; // cached column index
   private transient Map<String, Integer> cubeOption; // cube option
   private transient Map dataMap; // data->original data
   // calc column header name -> original aggregate name
   private transient Map<String, String> calcHeaders;
   private transient Map<String, Router> router = new HashMap<>();
   // sub table columns -> range [startrow, endrow) for multiple tbls
   private transient Map<SubColumns,int[]> subranges = new HashMap<>();
   private Locale loc;
   private SparseMatrix links;
   private transient VariableTable linkVarTable; // the variable for hyperlink
   private transient Hashtable<String, SelectionVSAssembly> selections;
   private static final int HEADER_MAP_DEFAULT_VALUE = -1;
   private static final Logger LOG = LoggerFactory.getLogger(VSDataSet.class);
}
