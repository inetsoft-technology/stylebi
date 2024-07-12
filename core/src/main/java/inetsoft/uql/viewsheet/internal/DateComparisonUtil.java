/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.viewsheet.internal;

import inetsoft.util.*;
import inetsoft.util.data.CommonKVModel;
import inetsoft.graph.*;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.data.CalcColumn;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.GraphtDataSelector;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.*;
import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.composition.graph.*;
import inetsoft.report.filter.*;
import inetsoft.report.filter.DCMergeDatePartFilter.MergePartCell;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.composer.model.vs.DateComparisonPaneModel;
import org.apache.commons.lang.ArrayUtils;

import java.text.Format;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class DateComparisonUtil {
   /**
    * Get first date dimension.
    *
    * @param fields fields to be searched.
    */
   public static DataRef findDateDimension(DataRef[] fields) {
      if(fields == null) {
         return null;
      }

      for(DataRef ref : fields) {
         if(isDateDim(ref)) {
            return ref;
         }
      }

      return null;
   }

   public static DCNamedGroupInfo createCustomRangeNameGroup(CustomPeriods customPeriods) {
      DCNamedGroupInfo namedGroupInfo = new DCNamedGroupInfo();
      List<DatePeriod> datePeriods = customPeriods.getDatePeriods(true);
      List<String> groups = DCNamedGroupInfo.getCustomPeriodLabels(datePeriods);

      for(int i = 0; i < datePeriods.size(); i++) {
         DatePeriod period = datePeriods.get(i);

         if(period == null || period.getStart() == null || period.getEnd() == null ||
            period.getStart().after(period.getEnd()))
         {
            continue;
         }

         namedGroupInfo.addGroupName(groups.get(i));
         List list = new ArrayList();
         list.add(period.getStart());
         list.add(period.getEnd());
         namedGroupInfo.setGroupValue(groups.get(i), list);
      }

      return namedGroupInfo;
   }

   /**
    * Check whether current object is a XDimension date ref.
    */
   private static boolean isDateDim(Object ref) {
      if(ref instanceof XDimensionRef) {
         DataRef dref = ((XDimensionRef) ref).getDataRef();

         if(dref != null) {
            return XSchema.isDateType(((XDimensionRef) ref).getDataRef().getDataType()) &&
               !Tool.equals(((XDimensionRef) ref).getDataRef().getDataType(), XSchema.TIME);
         }
      }

      return false;
   }

   /**
    * Check whether the chart is applied the date comparison.
    *
    * @param info chart assembly info.
    *
    * @return <tt>true</tt> if applied or will apply, <tt>false</tt> otherwise.
    */
   public static boolean appliedDateComparison(VSAssemblyInfo info) {
      if(info == null) {
         return false;
      }

      if(info instanceof ChartVSAssemblyInfo) {
         return ((ChartVSAssemblyInfo) info).getVSChartInfo().isAppliedDateComparison() &&
            isDateComparisonDefined(info);
      }
      else if(info instanceof CrosstabVSAssemblyInfo) {
         return ((CrosstabVSAssemblyInfo) info).getVSCrosstabInfo().isAppliedDateComparison() &&
            isDateComparisonDefined(info);
      }

      return false;
   }

   /**
    * Get the assembly design or share dateComparison,
    */
   public static DateComparisonInfo getDateComparison(DateCompareAbleAssemblyInfo assemblyInfo,
                                                      Viewsheet vs)
   {
      if(assemblyInfo instanceof DataVSAssemblyInfo &&
         !((DataVSAssemblyInfo) assemblyInfo).isDateComparisonEnabled())
      {
         return null;
      }

      if(!Tool.isEmptyString(assemblyInfo.getComparisonShareFrom())) {
         VSAssembly fromAssembly = vs.getAssembly(assemblyInfo.getComparisonShareFrom());

         if(fromAssembly == null ||
            !(fromAssembly.getVSAssemblyInfo() instanceof DateCompareAbleAssemblyInfo))
         {
            return null;
         }

         String shareFrom = ((DateCompareAbleAssemblyInfo) fromAssembly.getVSAssemblyInfo())
            .getComparisonShareFrom();

         // maybe nested share
         if(!Tool.isEmptyString(shareFrom)) {
            fromAssembly = vs.getAssembly(shareFrom);
         }

         DateComparisonInfo dateComparisonInfo =
            ((DateCompareAbleAssemblyInfo) fromAssembly.getVSAssemblyInfo()).getDateComparisonInfo();
         DateComparisonInfo dcinfo = assemblyInfo.getDateComparisonInfo();

         if(fromAssembly instanceof CrosstabVSAssembly &&
            assemblyInfo instanceof ChartVSAssemblyInfo &&
            dcinfo != null && dateComparisonInfo != null)
         {
            // @dcColorRemove
            //dateComparisonInfo.setDcColorFrameWrapper(dcinfo.getDcColorFrameWrapper());
            dateComparisonInfo.setUseFacet(dcinfo.isUseFacet());
            dateComparisonInfo.setShowMostRecentDateOnly(dcinfo.isShowMostRecentDateOnly());
         }

         return dateComparisonInfo;
      }
      else {
         return assemblyInfo.getDateComparisonInfo();
      }
   }

   /**
    * Check whether the specified chart supports date comparison.
    */
   public static boolean supportDateComparison(ChartInfo cinfo, boolean rt) {
      if(cinfo == null) {
         return false;
      }

      if(cinfo instanceof VSChartInfo && ((VSChartInfo) cinfo).isAppliedDateComparison()) {
         return true;
      }

      boolean invalidChartType = GraphTypeUtil.checkType(cinfo, type ->
         !GraphTypes.isAuto(type) && !GraphTypes.isBar(type) &&
            !GraphTypes.isLine(type) && !GraphTypes.isArea(type) &&
            !GraphTypes.isInterval(type) && !GraphTypes.isPoint(type));

      if(invalidChartType) {
         return false;
      }

      ChartRef[] xFields = rt ? cinfo.getRTXFields() : cinfo.getXFields();
      ChartRef[] yFields = rt ? cinfo.getRTYFields() : cinfo.getYFields();

      if(xFields == null || yFields == null) {
         return false;
      }

      return containsDateDimension(xFields) && containsAggregate(yFields) ||
         containsDateDimension(yFields) && containsAggregate(xFields);
   }

   /**
    * Check whether the specified chart supports date comparison.
    */
   public static boolean supportDateComparison(VSCrosstabInfo vsCrosstabInfo, boolean rt) {
      if(vsCrosstabInfo == null) {
         return false;
      }

      DataRef[] rowFields = rt ? vsCrosstabInfo.getRuntimeRowHeaders() :
         vsCrosstabInfo.getRowHeaders();
      DataRef[] colFields = rt ? vsCrosstabInfo.getRuntimeColHeaders() :
         vsCrosstabInfo.getColHeaders();
      DataRef[] aggs = rt ? vsCrosstabInfo.getRuntimeAggregates() :
         vsCrosstabInfo.getAggregates();

      return crosstabSupportDateComparison(rowFields, colFields, aggs);
   }

   /**
    * Check whether the crosstab supports date comparison.
    *
    * @param rowFields crosstab row fields.
    * @param colFields crosstab col fields.
    * @param aggs      crosstab aggregate fields.
    *
    * @return true if support, otherwise false.
    */
   public static boolean crosstabSupportDateComparison(DataRef[] rowFields, DataRef[] colFields,
                                                       DataRef[] aggs)
   {
      if((rowFields == null && colFields == null) || aggs == null) {
         return false;
      }

      boolean containsAggs = Arrays.stream(aggs)
         .anyMatch(ref -> !VSUtil.isFake(ref));

      return (containsDateDimension(rowFields) || containsDateDimension(colFields))
         && containsAggs;
   }

   /**
    * Check whether the specified chart defined date comparison.
    *
    * @return true if defined, otherwise false.
    */
   public static boolean isDateComparisonDefined(VSAssemblyInfo info) {
      return isDateComparisonDefined(info, true);
   }

   /**
    * Check whether the specified chart defined date comparison.
    *
    * @param info           VSAssemblyInfo.
    * @param checkShareFrom whether check the share from dc.
    *
    * @return true if defined, otherwise false.
    */
   public static boolean isDateComparisonDefined(VSAssemblyInfo info, boolean checkShareFrom) {
      if(info == null) {
         return false;
      }

      if(info instanceof DateCompareAbleAssemblyInfo) {
         if(!Tool.isEmptyString(((DateCompareAbleAssemblyInfo) info).getComparisonShareFrom())) {
            return checkShareFrom;
         }

         DateComparisonInfo dcInfo = ((DateCompareAbleAssemblyInfo) info).getDateComparisonInfo();

         return dcInfo != null && dcInfo.getPeriods() != null &&
            dcInfo.getInterval() != null && dcInfo.getComparisonOption() != 0;
      }

      return false;
   }

   /**
    * Get a text description of the date comparison definition.
    */
   public static String getDateComparisonDescription(VSAssemblyInfo info) {
      if(info == null) {
         return "";
      }

      String desc = null;

      if(info instanceof DateCompareAbleAssemblyInfo) {
         DateComparisonInfo dcInfo = ((DateCompareAbleAssemblyInfo) info).getDateComparisonInfo();
         String shareFrom = ((DateCompareAbleAssemblyInfo) info).getComparisonShareFrom();

         if(dcInfo != null) {
            desc = dcInfo.getDescription();
         }
         else if(!Tool.isEmptyString(shareFrom)) {
            Viewsheet vs = info.getViewsheet();

            if(vs != null) {
               VSAssembly vsAssembly = vs.getAssembly(shareFrom);

               if(vsAssembly != null) {
                  desc = DateComparisonUtil.getDateComparisonDescription(vsAssembly.getVSAssemblyInfo());
               }
            }
         }
      }

      if(desc == null || "".equals(desc)) {
         return desc;
      }

      boolean appliedDateComparison = true;

      if(info instanceof ChartVSAssemblyInfo) {
         VSChartInfo vinfo = ((ChartVSAssemblyInfo) info).getVSChartInfo();
         appliedDateComparison = vinfo.isAppliedDateComparison();
      }
      else if(info instanceof CrosstabVSAssemblyInfo) {
         VSCrosstabInfo vinfo = ((CrosstabVSAssemblyInfo) info).getVSCrosstabInfo();
         appliedDateComparison = vinfo.isAppliedDateComparison();
      }

      Catalog catalog = Catalog.getCatalog();

      if(!appliedDateComparison) {
         StringBuffer buffer = new StringBuffer(desc);
         buffer.append("\n\n<font color='red'><b>");
         buffer.append(catalog.getString("Warning"));
         buffer.append(":&nbsp;</b>");

         if(info instanceof ChartVSAssemblyInfo) {
            buffer.append(catalog.getString("date.comparison.notApplied.chart"));
         }
         else {
            buffer.append(catalog.getString("date.comparison.notApplied.crosstab"));
         }

         buffer.append("</font>");
         desc = buffer.toString();
      }

      return desc;
   }

   /**
    * Whether contains the date dimension ref.
    *
    * @param fields fields to be checked.
    *
    * @return <tt>true</tt> if contains, <tt>false</tt> otherwise.
    */
   public static boolean containsDateDimension(DataRef[] fields) {
      return Arrays.stream(fields).filter(XDimensionRef.class::isInstance)
         .map(XDimensionRef.class::cast)
         .anyMatch(dim -> XSchema.DATE.equals(dim.getDataType()) ||
            XSchema.TIME_INSTANT.equals(dim.getDataType()));
   }

   /**
    * Whether contains the aggregate.
    *
    * @param fields fields to be checked.
    *
    * @return <tt>true</tt> if contains, <tt>false</tt> otherwise.
    */
   private static boolean containsAggregate(DataRef[] fields) {
      return Arrays.stream(fields).anyMatch(VSAggregateRef.class::isInstance);
   }

   public static void checkGraphValidity(ChartVSAssemblyInfo info, VGraph vgraph) {
      DataSet data = vgraph.getCoordinate().getDataSet();
      List<VisualObject> vos = GTool.getVOs(vgraph);
      boolean noPlotArea = vos == null || vos.size() == 0 || isEmptyPlot(vos);
      boolean noData = data.getRowCount() == 0;

      // If no data, should show no data label
      // If have data, but do not create plot, should show no data label.
      if(noPlotArea || noData) {
         if(GraphUtil.getBrushDataSet(data) == null) {
            info.setNoData(true);
         }
         //If brushed chart, check if have plot, if have plots, should not show no data.
         else {
            info.setNoData(noPlotArea);
         }
      }
      else {
         info.setNoData(false);
      }

      if(!isDateComparisonDefined(info)) {
         return;
      }

      Catalog catalog = Catalog.getCatalog();
      String msg = null;

      if(!info.isNoData() && emptyComparisonValues(data)) {
         msg = catalog.getString("date.comparison.noData");
      }

      VSChartInfo cinfo = info.getVSChartInfo();
      List<ChartAggregateRef> list = cinfo.getAestheticAggregateRefs(true);
      boolean containsTrendCalc = list.stream()
         .filter(ref -> ref.getCalculator() != null)
         .findFirst()
         .isPresent();

      if(!containsTrendCalc) {
         if(msg != null) {
            Tool.addUserMessage(msg);
         }

         return;
      }

      boolean hasLine = vos.stream().anyMatch(v -> v instanceof LineVO);
      boolean singlePointLine = vos.stream()
         .filter(v -> v instanceof LineVO)
         .allMatch(v -> ((LineVO) v).getPoints().length <= 1);

      if(hasLine && singlePointLine) {
         String msg2 = catalog.getString("date.comparison.singlePointLine");
         msg = msg == null ? msg2 : msg + "\n" + msg2;
      }

      if(msg != null) {
         Tool.addUserMessage(msg);
      }
   }

   private static boolean isEmptyPlot(List<VisualObject> vos) {
      if(vos == null || vos.size() == 0) {
         return true;
      }

      for(int i = 0; i < vos.size(); i++) {
         VisualObject vo = vos.get(i);

         // if have one plot, should not paint no data label.
         // if all plot is null, should show no data label.
         if(vo.getBounds() != null) {
            return false;
         }
      }

      return true;
   }

   private static boolean emptyComparisonValues(DataSet dataset) {
      if(dataset == null) {
         return false;
      }

      List<CalcColumn> calcColumns = dataset.getCalcColumns();

      if(calcColumns != null && calcColumns.size() > 0) {
         List<Integer> indexes = new ArrayList<>();

         for(CalcColumn calcColumn : calcColumns) {
            if(calcColumn == null) {
               indexes.add(-1);
               continue;
            }

            indexes.add(dataset.indexOfHeader(calcColumn.getHeader()));
         }

         for(int i = 0; i < dataset.getRowCount(); i++) {
            for(Integer index : indexes) {
               if(index == null || index.equals(-1)) {
                  continue;
               }

               if(dataset.getData(index, i) != null) {
                  return false;
               }
            }
         }

         return true;
      }

      return false;
   }

   private static boolean emptyComparisonValues(TableLens tableLens) {
      if(tableLens == null) {
         return false;
      }

      CrossCalcFilter crossCalcFilter = (CrossCalcFilter) Util.getNestedTable(tableLens,
                                                                              CrossCalcFilter.class);

      if(crossCalcFilter == null) {
         return false;
      }
      else {
         return crossCalcFilter.getCalcColumns() != null &&
            crossCalcFilter.getCalcColumns().size() > 0 && !crossCalcFilter.hasComparisonValues();
      }
   }

   public static void checkGraphValidity(VSAssemblyInfo info, TableLens table) {
      if(!isDateComparisonDefined(info)) {
         return;
      }

      Catalog catalog = Catalog.getCatalog();

      if(table.getRowCount() - table.getHeaderRowCount() <= 0 || emptyComparisonValues(table)) {
         Tool.addUserMessage(catalog.getString("date.comparison.noData"));
      }
   }

   /**
    * Calculate the week of quarter. Use the first full week of a month as the first week of
    * the quarter.
    * @param cal set time Calendar.
    * @return week of quarter.
    */
   public static int getWeekOfQuarter(Calendar cal, int weekStartDay) {
      cal.setMinimalDaysInFirstWeek(7);
      cal.setFirstDayOfWeek(weekStartDay);

      Date fistWeek = getQuarterFirstWeek(cal.getTime(), weekStartDay);
      ZonedDateTime startDate = Instant.ofEpochMilli(fistWeek.getTime())
         .atZone(ZoneId.systemDefault());
      ZonedDateTime endDate = Instant.ofEpochMilli(cal.getTime().getTime())
         .atZone(ZoneId.systemDefault());

      int dayOffset = (int) startDate.until(endDate, ChronoUnit.DAYS) + 1;
      int weekOfQuarter = dayOffset / 7;
      weekOfQuarter += dayOffset % 7 > 0 ? 1 : 0;

      return weekOfQuarter;
   }

   public static void setWeekOfQuarter(int weekOfQuarter, Calendar cal, int weekStartDay) {
      Date quarterFirstWeek = getQuarterFirstWeek(cal.getTime(), weekStartDay);
      Calendar quarterWeek = getCalendar();
      quarterWeek.setTime(quarterFirstWeek);
      quarterWeek.add(Calendar.DATE, (weekOfQuarter - 1) * 7);
      cal.setTime(quarterWeek.getTime());
   }

   // get the quarter 1st week's first day.
   public static Date getQuarterFirstWeek(Date date, int weekStartDay) {
      Calendar cal0 = new GregorianCalendar();
      cal0.setMinimalDaysInFirstWeek(7);
      cal0.setFirstDayOfWeek(weekStartDay);
      cal0.setTime(date);
      cal0.set(Calendar.DAY_OF_WEEK, weekStartDay);
      cal0.add(Calendar.MONTH, -cal0.get(Calendar.MONTH) % 3);
      cal0.set(Calendar.WEEK_OF_MONTH, 1);
      cal0.set(Calendar.DAY_OF_WEEK, weekStartDay);

      return cal0.getTime();
   }

   /**
    * Convert the date comparison interval level to XConstants level.
    */
   public static int dcIntervalLevelToDateGroupLevel(int interval) {
      if((interval & DateComparisonInfo.YEAR) == DateComparisonInfo.YEAR) {
         return XConstants.YEAR_DATE_GROUP;
      }
      else if((interval & DateComparisonInfo.QUARTER) == DateComparisonInfo.QUARTER) {
         return XConstants.QUARTER_DATE_GROUP;
      }
      else if((interval & DateComparisonInfo.MONTH) == DateComparisonInfo.MONTH) {
         return XConstants.MONTH_DATE_GROUP;
      }
      else if((interval & DateComparisonInfo.WEEK) == DateComparisonInfo.WEEK) {
         return XConstants.WEEK_DATE_GROUP;
      }
      else if((interval & DateComparisonInfo.DAY) == DateComparisonInfo.DAY) {
         return XConstants.DAY_DATE_GROUP;
      }

      return -1;
   }

   // set data selectors for chart so extra period added in
   // DateComparisonInfo.getStandardPeriodsCondition() is not plotted.
   public static void applyDateRange(DateComparisonInfo dcInfo, EGraph egraph, VSChartInfo info,
                                     DataSet data)
   {
      DateComparisonPeriods periods = dcInfo.getPeriods();
      VSDataRef comparisonRef = info.getDateComparisonRef();

      if(periods instanceof StandardPeriods &&
         dcInfo.getComparisonOption() != DateComparisonInfo.VALUE && comparisonRef != null)
      {
         String periodCol = getDcPeriodCol(info, data);
         String partCol = getDcPartCol(info);
         Date startDate = dcInfo.getStartDate();
         DateSelector selector = new DateSelector(periodCol, startDate, null);

         for(Scale scale : egraph.getCoordinate().getScales()) {
            String[] fields = scale.getFields();

            if(fields.length > 0 && Tool.equals(partCol, fields[0])) {
               Format fmt = scale.getAxisSpec().getTextSpec().getFormat();
               applyGraphDataSelector(data, scale, new DateSelector(periodCol, startDate, fmt));
            }
            else if(scale instanceof LinearScale ||
               fields.length > 0 && Tool.equals(getBaseName(periodCol), (getBaseName(fields[0]))))
            {
               applyGraphDataSelector(data, scale, selector);
            }
         }

         for(int i = 0; i < egraph.getElementCount(); i++) {
            GraphElement elem = egraph.getElement(i);
            elem.setGraphDataSelector(selector);

            if(elem.getTextFrame() != null) {
               elem.getTextFrame().setGraphDataSelector(new BrushSelector(null, elem.getVars()));
            }
         }

         for(Scale scale : egraph.getCoordinate().getScales()) {
            AxisSpec spec = scale.getAxisSpec();

            if(spec.getTextSpec().getFormat() instanceof DateComparisonFormat) {
               DateComparisonFormat fmt = (DateComparisonFormat) spec.getTextSpec().getFormat();
               fmt.setGraphDataSelector(selector);
            }
         }

         for(VisualFrame frame : egraph.getAllVisualFrames()) {
            if(frame.getScale() != null && frame.getField() != null) {
               applyGraphDataSelector(data, frame.getScale(), selector);
               // for multistyle
               data = VSFrameVisitor.getVisualDataSet(frame.getField(), data);
               frame.getScale().init(data);
            }
         }
      }
   }

   private static void applyGraphDataSelector(DataSet data, Scale scale,
                                              GraphtDataSelector selector)
   {
      String[] fields = scale.getFields();
      Object[] values = scale.getValues();

      if(fields.length > 1 && Arrays.stream(fields).anyMatch(f -> f.startsWith(ElementVO.ALL_PREFIX))) {
         selector = new BrushSelector(selector, fields);
      }

      scale.setGraphDataSelector(selector);

      if(fields.length > 0 && values.length > 0) {
         String[] vars = scale.getVars();
         boolean nonull = false;

         loop1:
         for(int i = 0; i < data.getRowCount(); i++) {
            for(String var : vars) {
               if(data.getData(var, i) != null && values[0].equals(data.getData(fields[0], i))) {
                  nonull = true;
                  break loop1;
               }
            }
         }

         // if selecting by data selector, don't remove the leaving null var. (55397)
         // don't inclue value on scale if ALL measures (e.g. change) are null. (56035)
         if(nonull) {
            scale.setScaleOption(scale.getScaleOption() & ~Scale.NO_LEADING_NULL_VAR);
         }
      }
   }

   private static class DateSelector implements GraphtDataSelector {
      public DateSelector(String periodCol, Date startDate, Format fmt) {
         this.periodCol = periodCol;
         this.startDate = startDate;
         this.fmt = fmt;
      }

      @Override
      public boolean accept(DataSet dataset, int row, String[] fields) {
         Object val = dataset.getData(periodCol, row);

         if(val instanceof MergePartCell) {
            Object date = ((MergePartCell) val).getDateGroupValue();
            return !(date instanceof Date) || ((Date) date).getTime() >= startDate.getTime();
         }

         // empty merged date parts treated as null. (54720)
         if(fmt != null && (val instanceof MergePartCell || val instanceof Number)) {
            return !fmt.format(val).isEmpty();
         }

         return !(val instanceof Date) || ((Date) val).getTime() >= startDate.getTime();
      }

      private String periodCol;
      private Date startDate;
      private Format fmt;
   }

   private static class BrushSelector implements GraphtDataSelector {
      public BrushSelector(GraphtDataSelector selector, String[] fields) {
         this.selector = selector;
         this.allFields = new HashSet<>(Arrays.asList(fields));
      }

      @Override
      public boolean accept(DataSet data, int row, String[] fields) {
         boolean hasAll = false;
         boolean isAllNull = false;

         for(String field : fields) {
            if(field.startsWith(ElementVO.ALL_PREFIX)) {
               hasAll = true;
            }
            else if(allFields.contains(field)) {
               Object allVal = data.getData(ElementVO.ALL_PREFIX + field, row);
               isAllNull = allVal == null;
            }
         }

         // when brushed, both field and __all_field are on the scale, and in the case of
         // comparison, rows with only field, and both field and __all_fields are present,
         // causing the calculated scale to double. this check avoids counting the field
         // values twice. (59511)
         if(!hasAll && !isAllNull) {
            return false;
         }

         return selector == null || selector.accept(data, row, fields);
      }

      private GraphtDataSelector selector;
      private Set<String> allFields;
   }

   /**
    * Get the date period column (e.g. quarter).
    * @param info the target chart info which applied dc.
    * @param data the dataset of the target chart.
    * @return date comparison period column name.
    */
   private static String getDcPeriodCol(VSChartInfo info, DataSet data) {
      VSDataRef comparisonRef = info.getDateComparisonRef();

      if(comparisonRef == null) {
         return null;
      }

      String dateCol = comparisonRef.getFullName();

      if(data.indexOfHeader(dateCol) >= 0) {
         return dateCol;
      }

      if(info.isFacet()) {
         ChartRef[] refs = info.isDcBaseDateOnX() ? info.getRTYFields() : info.getRTXFields();

         if(refs != null && refs.length > 0) {
            ChartRef dim = Arrays.stream(refs)
               .filter(ref -> ref instanceof VSDimensionRef)
               .filter(ref -> XSchema.isDateType(ref.getDataType()))
               .findFirst()
               .orElse(null);

            return dim == null ? dateCol : dim.getFullName();
         }
      }
      else {
         ChartBindable bindable = null;

         if(info.isMultiStyles()) {
            VSDataRef[] xyRefs = info.getRTFields(true, false, false, false);

            VSDataRef aggr = Arrays.stream(xyRefs)
               .filter(ref -> ref instanceof VSChartAggregateRef)
               .findFirst()
               .orElse(null);

            if(aggr != null) {
               bindable = (VSChartAggregateRef) aggr;
            }
         }
         else {
            bindable = info;
         }

         if(bindable != null) {
            AestheticRef aestheticRef = bindable.getColorField();

            if(aestheticRef != null) {
               return aestheticRef.getFullName();
            }

            aestheticRef = bindable.getShapeField();

            if(aestheticRef != null) {
               return aestheticRef.getFullName();
            }
         }
      }

      return dateCol;
   }

   // get the date part column (e.g. week of month).
   public static String getDcPartCol(VSChartInfo info) {
      ChartRef[] refs = info.isDcBaseDateOnX() ? info.getRTXFields() : info.getRTYFields();

      if(refs != null && refs.length > 0) {
         ChartRef dim = Arrays.stream(refs)
            .filter(ref -> ref instanceof VSDimensionRef)
            .filter(ref -> isDCCalcDatePartRef((VSDimensionRef) ref))
            .findFirst()
            .orElse(null);

         return dim == null ? null : dim.getFullName();
      }

      return null;
   }

   public static String getDcPartCol(VSCrosstabInfo info) {
      DataRef[] refs = info.getRuntimeDateComparisonRefs();

      if(refs != null && refs.length > 0) {
         DataRef dim = Arrays.stream(refs)
            .filter(ref -> ref instanceof VSDimensionRef)
            .filter(ref -> isDCCalcDatePartRef((VSDimensionRef) ref, true))
            .findFirst()
            .orElse(null);

         return dim == null ? null : ((VSDimensionRef) dim).getFullName();
      }

      return null;
   }

   private static boolean isDCCalcDatePartRef(VSDimensionRef ref) {
      return isDCCalcDatePartRef(ref, false);
   }

   private static boolean isDCCalcDatePartRef(VSDimensionRef ref, boolean justPart) {
      if(ref.getDataRef() instanceof CalculateRef) {
         return ((CalculateRef) ref.getDataRef()).isDcRuntime();
      }

      if(!ref.isDateTime()) {
         return false;
      }

      return (ref.getDateLevel() & DateRangeRef.PART_DATE_GROUP) == DateRangeRef.PART_DATE_GROUP ||
         // year has no part type, year date group is part (55253).
         !justPart && ref.getDateLevel() == DateRangeRef.YEAR_DATE_GROUP;
   }

   public static boolean isDCCalcDatePartRef(String name) {
      if(name == null) {
         return false;
      }

      return name.startsWith("DayOfYear(") || name.startsWith("MonthOfQuarter(") ||
         name.startsWith("WeekOfQuarter(") || name.startsWith("DayOfQuarter(") ||
         name.startsWith("WeekOfMonth(") || name.startsWith("WeekOfYear(") ||
         name.startsWith(DateRangeRef.getRangeValue(DateRangeRef.MONTH_OF_QUARTER_FULL_WEEK_PART) + "(");
   }

   public static boolean isDCCalcDatePartRef(XDimensionRef dim) {
      if(dim == null) {
         return false;
      }

      return dim.getDataRef() != null && dim.getDataRef() instanceof CalculateRef &&
         ((CalculateRef) dim.getDataRef()).isDcRuntime() && isDCCalcDatePartRef(dim.getFullName());
   }

   public static boolean isShowFullDay(ChartRef ref) {
      if(!(ref instanceof VSDimensionRef)) {
         return false;
      }

      int dlevel = ((VSDimensionRef) ref).getDateLevel();

      if(dlevel == DateRangeRef.WEEK_OF_YEAR_PART || dlevel == DateRangeRef.DAY_OF_WEEK_DATE_GROUP) {
         return true;
      }

      DataRef dref = ((VSDimensionRef) ref).getDataRef();

      if(dref instanceof CalculateRef) {
         String name = dref.getName();

         return name.startsWith("DayOfYear(") || name.startsWith("WeekOfQuarter(") ||
            name.startsWith("WeekOfYear(") || name.startsWith("DayOfQuarter(");
      }

      return false;
   }

   public static String getBaseName(String name) {
      if(name.startsWith("DayOfYear(")) {
         return name.substring(10, name.length() - 1);
      }
      else if(name.startsWith("MonthOfQuarter(")) {
         return name.substring(15, name.length() - 1);
      }
      else if(name.startsWith("WeekOfQuarter(")) {
         return name.substring(14, name.length() - 1);
      }
      else if(name.startsWith("DayOfQuarter(")) {
         return name.substring(13, name.length() - 1);
      }
      else if(name.startsWith("WeekOfMonth(")) {
         return name.substring(12, name.length() - 1);
      }

      return DateRangeRef.getBaseName(name);
   }

   public static void fixDCModelProperties(Viewsheet vs, DateComparisonPaneModel dateComparisonModel,
                                           String objectId, String shareFrom)
   {
      if(vs == null || dateComparisonModel == null) {
         return;
      }

      VSAssembly currentAssembly = vs.getAssembly(objectId);
      VSAssembly shareFromAssembly = vs.getAssembly(shareFrom);

      if(currentAssembly instanceof ChartVSAssembly &&
         shareFromAssembly instanceof CrosstabVSAssembly)
      {
         ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) currentAssembly.getVSAssemblyInfo();
         DateComparisonInfo dinfo = info.getDateComparisonInfo();

         if(dinfo == null) {
            dinfo = new DateComparisonInfo();
            info.setDateComparisonInfo(dinfo);
         }

         dateComparisonModel.setUseFacet(dinfo.isUseFacet());
         dateComparisonModel.setOnlyShowMostRecentDate(dinfo.isShowMostRecentDateOnly());
      }
   }

   public static boolean isDateComparisonChartTypeChanged(ChartVSAssemblyInfo ninfo,
                                                          ChartVSAssemblyInfo oinfo)
   {
      if(!isDateComparisonDefined(ninfo, true) || !isDateComparisonDefined(oinfo, true)) {
         return true;
      }

      List<ChartAggregateRef> nrefs = ninfo.getVSChartInfo().getAestheticAggregateRefs(true);
      List<ChartAggregateRef> orefs = oinfo.getVSChartInfo().getAestheticAggregateRefs(true);

      if(nrefs.size() != orefs.size()) {
         return true;
      }

      for(int i = 0; i < nrefs.size(); i++) {
         ChartAggregateRef nref = nrefs.get(i);
         ChartAggregateRef oref = null;

         for(int j = 0; j < orefs.size() && oref == null; j++) {
            if(Tool.equals(orefs.get(j).getFullName(), nref.getFullName())) {
               oref = orefs.get(j);
            }
         }

         if(oref != null && nref.getRTChartType() != oref.getRTChartType()) {
            return true;
         }
      }

      return false;
   }

   /**
    * Whether format the part level as date and merge them by period group.
    */
   public static boolean showPartAsDates(DateComparisonInfo dcInfo) {
      if(!(dcInfo.getPeriods() instanceof StandardPeriods)) {
         return false;
      }

      StandardPeriods periods = (StandardPeriods) dcInfo.getPeriods();
      int periodLevel = periods.getDateLevel();
      int contextLevel = dcInfo.getInterval().getContextLevel();
      int interval = dcInfo.getInterval().getLevel();
      int granularity = dcInfo.getInterval().getGranularity();

      if(interval == DateComparisonInfo.ALL) {
         return showPartLevelAsDate(periodLevel, granularity);
      }

      if(!dcInfo.isDateSeries()) {
         return false;
      }

      int dcInterval = dcIntervalLevelToDateGroupLevel(interval);
      int dcGranularity = dcIntervalLevelToDateGroupLevel(granularity);
      boolean showPartLevelAsDate = showPartLevelAsDate(periodLevel, granularity);

      if(contextLevel == dcInterval && contextLevel == dcGranularity)  {
         return showPartLevelAsDate;
      }

      return (periodLevel != contextLevel || contextLevel != dcInterval) || showPartLevelAsDate;
   }

   private static boolean showPartLevelAsDate(int parentLevel, int granularity) {
      return parentLevel == XConstants.YEAR_DATE_GROUP &&
         (granularity == DateComparisonInfo.DAY ||
            granularity == DateComparisonInfo.WEEK) ||
         parentLevel == XConstants.QUARTER_DATE_GROUP &&
            (granularity == DateComparisonInfo.DAY ||
               granularity == DateComparisonInfo.WEEK);
   }

   /**
    * Format the merged part cell to string.
    *
    * @param cell merged cell.
    * @param date date group value.
    */
   public static String formatPartMergeCell(MergePartCell cell, Date date, Format fmt) {
      return formatPartMergeCell(cell, date, false, fmt);
   }

   /**
    * Format the merged part cell to string.
    *
    * @param cell merged cell.
    * @param date date group value.
    */
   public static String formatPartMergeCell(MergePartCell cell, Date date, boolean rawDate, Format fmt) {
      int formatLevel = DateRangeRef.DAY_INTERVAL;
      Calendar cal = new GregorianCalendar();
      cal.setFirstDayOfWeek(Tool.getFirstDayOfWeek());
      cal.setMinimalDaysInFirstWeek(7);

      if(rawDate) {
         cal.setTime(date);
         Format dateFmt = fmt != null ? fmt : XUtil.getDefaultDateFormat(formatLevel);
         return dateFmt.format(cal.getTime());
      }

      cal.setTime(date);
      List<XDimensionRef> mergedRefs = cell.getMergedRefs();

      for(int i = 0; i < mergedRefs.size(); i++) {
         XDimensionRef mergedRef = mergedRefs.get(i);
         int value = ((Number) cell.getValue(i)).intValue();
         boolean last = mergedRefs.size() - 1 == i;

         if(DateComparisonUtil.isDCCalcDatePartRef(mergedRef)) {
            if(mergedRef.getFullName().startsWith("DayOfYear(")) {
               cal.set(Calendar.DAY_OF_YEAR, value);
            }
            else if(mergedRef.getFullName().startsWith("MonthOfQuarter(") ||
               mergedRef.getFullName().startsWith(
                  DateRangeRef.getRangeValue(DateRangeRef.MONTH_OF_QUARTER_FULL_WEEK_PART) + "("))
            {
               cal.add(Calendar.MONTH, value - 1);

               if(last) {
                  formatLevel = DateRangeRef.MONTH_INTERVAL;
               }
            }
            else if(mergedRef.getFullName().startsWith("WeekOfQuarter(")) {
               cal.set(Calendar.WEEK_OF_MONTH, value);
               cal.set(Calendar.DAY_OF_WEEK, Tool.getFirstDayOfWeek());
            }
            else if(mergedRef.getFullName().startsWith("DayOfQuarter(")) {
               cal.set(Calendar.DAY_OF_MONTH, value);
            }
            else if(mergedRef.getFullName().startsWith("WeekOfMonth(")) {
               cal.set(Calendar.WEEK_OF_MONTH, value);
               cal.set(Calendar.DAY_OF_WEEK, Tool.getFirstDayOfWeek());
            }
            else if(mergedRef.getFullName().startsWith("WeekOfYear(")) {
               // WeekOfYear is MMW where MM is month and W is week of month.
               // @see JavaScriptEngine.datePart().
               cal.set(Calendar.MONTH, value / 10 - 1);
               cal.set(Calendar.WEEK_OF_MONTH, value % 10);
               cal.set(Calendar.DAY_OF_WEEK, Tool.getFirstDayOfWeek());
            }
         }
         else {
            if(mergedRef.getDateLevel() == DateRangeRef.QUARTER_OF_YEAR_PART ||
               mergedRef.getDateLevel() == DateRangeRef.QUARTER_OF_FULL_WEEK_PART)
            {
               cal.set(Calendar.MONTH, (value - 1) * 3);

               if(last) {
                  formatLevel = DateRangeRef.QUARTER_INTERVAL;
               }
            }
            else if(mergedRef.getDateLevel() == DateRangeRef.MONTH_OF_YEAR_PART ||
               mergedRef.getDateLevel() == DateRangeRef.MONTH_OF_FULL_WEEK_PART)
            {
               cal.set(Calendar.MONTH, value - 1);

               if(last) {
                  formatLevel = DateRangeRef.MONTH_INTERVAL;
               }
            }
            else if(mergedRef.getDateLevel() == DateRangeRef.WEEK_OF_YEAR_PART) {
               cal.set(Calendar.WEEK_OF_YEAR, value);
               cal.set(Calendar.DAY_OF_WEEK, Tool.getFirstDayOfWeek());
            }
            else if(mergedRef.getDateLevel() == DateRangeRef.DAY_OF_MONTH_PART) {
               cal.set(Calendar.DAY_OF_MONTH, value);
            }
            else if(mergedRef.getDateLevel() == DateRangeRef.DAY_OF_WEEK_PART) {
               cal.set(Calendar.DAY_OF_WEEK, value);
            }
         }
      }

      Format dateFmt = fmt != null ? fmt : XUtil.getDefaultDateFormat(formatLevel);

      if(dateFmt instanceof ExtendedDecimalFormat) {
         return dateFmt.format(cell.getValue(0));
      }

      return dateFmt.format(cal.getTime());
   }

   /**
    * Return all fields which are temporarily generated for expand the data as dc required.
    *
    * @param dcInfo              the date comparison info.
    * @param source              the source of target assembly.
    * @param dateComparisonRef   the date comparison field.
    * @param vs                  the current viewsheet.
    */
   public static XDimensionRef[] getAllTempDateGroupRef(DateComparisonInfo dcInfo, String source,
                                                        VSDimensionRef dateComparisonRef,
                                                        Viewsheet vs)
   {
      if(dcInfo != null && !dcInfo.invalid() && dateComparisonRef != null) {
         XDimensionRef[] tempDateGroups = dcInfo.getTempDateGroupRef(source, vs, dateComparisonRef);

         if(tempDateGroups != null && tempDateGroups.length > 0 &&
            dcInfo.needWeekOfYearAuxiliaryRef())
         {
            VSDimensionRef auxiliary = dateComparisonRef.clone();
            auxiliary.setDateLevelValue(DateRangeRef.QUARTER_OF_YEAR_DATE_GROUP + "");
            setForceDcToDateWeekOfMonth(dcInfo.getToDateWeekOfMonth(), auxiliary);
            tempDateGroups = (XDimensionRef[]) ArrayUtils.add(tempDateGroups, auxiliary);
         }

         return tempDateGroups;
      }

      return new XDimensionRef[0];
   }

   public static VSDimensionRef getMergePartDimension(VSAssembly cassembly, Viewsheet vs) {
      if(!(cassembly instanceof DateCompareAbleAssembly)) {
         return null;
      }

      DateCompareAbleAssemblyInfo dcAssemblyInfo =
         (DateCompareAbleAssemblyInfo) cassembly.getVSAssemblyInfo();

      if(DateComparisonUtil.appliedDateComparison(cassembly.getVSAssemblyInfo())) {
         DateComparisonInfo dcInfo = DateComparisonUtil.getDateComparison(dcAssemblyInfo, vs);

         if(!showPartAsDates(dcInfo)) {
            return null;
         }

         DataRef[] dcRtFields = null;

         if(dcAssemblyInfo instanceof ChartVSAssemblyInfo) {
            dcRtFields = ((ChartVSAssemblyInfo) dcAssemblyInfo).getVSChartInfo()
               .getRuntimeDateComparisonRefs();
         }
         else if(dcAssemblyInfo instanceof CrosstabVSAssemblyInfo) {
            dcRtFields = ((CrosstabVSAssemblyInfo) dcAssemblyInfo).getVSCrosstabInfo()
               .getRuntimeDateComparisonRefs();
         }

         if(dcRtFields != null) {
            for(DataRef dcRtField : dcRtFields) {
               if(!(dcRtField instanceof VSDimensionRef)) {
                  continue;
               }

               VSDimensionRef ref = (VSDimensionRef) dcRtField;
               boolean isPart = ref.isDateLevel() &&
                  ((ref.getDateLevel() & DateRangeRef.PART_DATE_GROUP) ==
                     DateRangeRef.PART_DATE_GROUP ||
                     DateComparisonUtil.isDCCalcDatePartRef(ref.getFullName()));

               if(isPart) {
                  return ref;
               }
            }
         }
      }

      return null;
   }

   public static TableLens getMergePartTableLens(VSAssembly cassembly, Viewsheet vs, TableLens base,
                                                 boolean isDetail)
   {
      if(!(cassembly instanceof DateCompareAbleAssembly)) {
         return base;
      }

      DateCompareAbleAssemblyInfo dcAssemblyInfo =
         (DateCompareAbleAssemblyInfo) cassembly.getVSAssemblyInfo();

      if(DateComparisonUtil.appliedDateComparison(cassembly.getVSAssemblyInfo())) {
         DateComparisonInfo dcInfo = DateComparisonUtil.getDateComparison(dcAssemblyInfo, vs);
         DataRef[] dcRtFields = null;
         SourceInfo source = null;
         boolean isCrosstab = false;

         if(dcAssemblyInfo instanceof ChartVSAssemblyInfo) {
            dcRtFields = ((ChartVSAssemblyInfo) dcAssemblyInfo).getVSChartInfo()
               .getRuntimeDateComparisonRefs();
            source = ((ChartVSAssemblyInfo) dcAssemblyInfo).getSourceInfo();
         }
         else if(dcAssemblyInfo instanceof CrosstabVSAssemblyInfo) {
            dcRtFields = ((CrosstabVSAssemblyInfo) dcAssemblyInfo).getVSCrosstabInfo()
               .getRuntimeDateComparisonRefs();
            source = ((CrosstabVSAssemblyInfo) dcAssemblyInfo).getSourceInfo();
            isCrosstab = true;
         }

         if(dcRtFields != null && source != null) {
            VSDimensionRef dateLevelRef = null;
            VSDimensionRef partLevelRef = null;

            for(DataRef dcRtField : dcRtFields) {
               if(!(dcRtField instanceof VSDimensionRef)) {
                  continue;
               }

               VSDimensionRef ref = (VSDimensionRef) dcRtField;
               boolean isPart = ref.isDateLevel() &&
                  ((ref.getDateLevel() & DateRangeRef.PART_DATE_GROUP) ==
                   DateRangeRef.PART_DATE_GROUP ||
                  DateComparisonUtil.isDCCalcDatePartRef(ref.getFullName()));

               if(isPart) {
                  partLevelRef = ref;
               }
               else if(ref.isDateLevel() || ref.isFullWeekDateLevel()) {
                  dateLevelRef = ref;
               }
            }

            if(!showPartAsDates(dcInfo)) {
               partLevelRef = null;
            }

            if(partLevelRef != null) {
               XDimensionRef[] tempRefs = dcInfo.getTempDateGroupRef(source.getSource(), vs,
                  dcAssemblyInfo.getDateComparisonRef());

               if(!isCrosstab && (tempRefs == null || tempRefs.length <= 0)) {
                  return base;
               }

               int toDateWeekOfMonth = dcInfo.getToDateWeekOfMonth();

               for(XDimensionRef tempRef : tempRefs) {
                  setForceDcToDateWeekOfMonth(toDateWeekOfMonth, tempRef);
               }

               VSDataRef dateComparisonRef = dcAssemblyInfo.getDateComparisonRef();
               XDimensionRef weekOfYearAuxiliaryRef = null;

               if(dcInfo.needWeekOfYearAuxiliaryRef()) {
                  VSDimensionRef auxiliary = (VSDimensionRef) dateComparisonRef.clone();
                  auxiliary.setDateLevelValue(DateRangeRef.QUARTER_OF_YEAR_DATE_GROUP + "");
                  weekOfYearAuxiliaryRef = auxiliary;
               }

               if(!isDetail) {
                  base = new DCMergeDatePartFilter(base, Arrays.asList(tempRefs), partLevelRef,
                     dateLevelRef, weekOfYearAuxiliaryRef);
                  base = hideIgnoreDcTemp(base, tempRefs);
               }
            }
         }
      }

      return base;
   }

   private static TableLens hideIgnoreDcTemp(TableLens base, XDimensionRef[] tempRefs) {
      VSDimensionRef ignoreDcTemp = findIgnoreDCTemp(tempRefs);

      if(ignoreDcTemp == null) {
         return base;
      }

      ColumnIndexMap indexMap = new ColumnIndexMap(base);
      int idx = indexMap.getColIndexByFormatedHeader(ignoreDcTemp.getFullName());

      if(idx != -1) {
         int[] arr = new int[base.getColCount() -1];
         int count = 0;

         for(int i = 0; i < base.getColCount(); i++) {
            if(i != idx) {
               arr[count++] = i;
            }
         }

         base = new ColumnMapFilter(base, arr);
      }

      return base;
   }

   private static VSDimensionRef findIgnoreDCTemp(XDimensionRef[] tempRefs) {
      if((tempRefs == null || tempRefs.length <= 0)) {
         return null;
      }

      for(int i = 0; i < tempRefs.length; i++) {
         XDimensionRef ref = tempRefs[i];

         if(isIgnoreDcTemp(ref)) {
            return (VSDimensionRef) ref;
         }
      }

      return null;
   }

   private static boolean isIgnoreDcTemp(XDimensionRef ref) {
      return ref instanceof VSDimensionRef && ((VSDimensionRef) ref).isIgnoreDcTemp();
   }

   public static TableLens hiddenTempGroupAndDatePartRefs(
      VSAssembly cassembly, Viewsheet vs, TableLens base, ColumnSelection detailCols)
   {
      boolean appliedDC = cassembly instanceof DateCompareAbleAssembly &&
         DateComparisonUtil.appliedDateComparison(cassembly.getVSAssemblyInfo());
      XDimensionRef[] dcTempRefs = null;
      SourceInfo sourceInfo = null;
      boolean isCrosstab = cassembly instanceof CrosstabVSAssembly;
      VSDataRef dcRef = null;

      if(cassembly.getVSAssemblyInfo() instanceof DataVSAssemblyInfo) {
         sourceInfo = ((DataVSAssemblyInfo) cassembly.getVSAssemblyInfo()).getSourceInfo();
      }

      if(appliedDC) {
         DateCompareAbleAssemblyInfo dcAssemblyInfo =
            (DateCompareAbleAssemblyInfo) cassembly.getVSAssemblyInfo();
         DateComparisonInfo dcInfo = DateComparisonUtil.getDateComparison(dcAssemblyInfo, vs);

         if(dcAssemblyInfo instanceof ChartVSAssemblyInfo) {
            ChartVSAssemblyInfo cinfo = (ChartVSAssemblyInfo) dcAssemblyInfo;
            dcRef = cinfo.getDateComparisonRef();
         }
         else if(dcAssemblyInfo instanceof CrosstabVSAssemblyInfo) {
            CrosstabVSAssemblyInfo cinfo = (CrosstabVSAssemblyInfo) dcAssemblyInfo;
            dcRef = cinfo.getDateComparisonRef();
         }

         dcTempRefs = DateComparisonUtil.getAllTempDateGroupRef(
            dcInfo, sourceInfo.getSource(),
            (VSDimensionRef) dcAssemblyInfo.getDateComparisonRef(), vs);
      }

      CalculateRef[] calcFields = vs.getCalcFields(sourceInfo.getSource());

      if(calcFields != null) {
         calcFields = Arrays.stream(calcFields)
            .filter(calculateRef -> isDCCalcDatePartRef(calculateRef.getName()))
            .toArray(CalculateRef[]::new);
      }

      int colCount = base.getColCount();
      List<Integer> cols = new ArrayList<>();

      for(int i = colCount - 1; i >= 0; i--) {
         Object colHeader = base.getObject(0, i);

         if(searchHeaderInRefs(colHeader, calcFields, false) < 0 && (!appliedDC ||
            searchHeaderInRefs(colHeader, dcTempRefs, isCrosstab) < 0 &&
            !isDcDataGroupColumn(dcRef, colHeader)))
         {
            cols.add(0, i);
         }
         else if(detailCols != null) {
            detailCols.removeAttribute(i);
         }
      }

      if(cols.size() > 0) {
         int[] colmap = cols.stream().mapToInt(i -> i).toArray();
         return new ColumnMapFilter(base, colmap);
      }

      return base;
   }

   private static boolean isDcDataGroupColumn(VSDataRef dcRef, Object header) {
      if(dcRef == null || !(dcRef instanceof VSDimensionRef) ||
         !Tool.equals(dcRef.getFullName(), header))
      {
         return false;
      }

      return ((VSDimensionRef) dcRef).getNamedGroupInfo() instanceof DCNamedGroupInfo;
   }

   private static int searchHeaderInRefs(Object header, DataRef[] rtFields, boolean isCrosstab) {
      if(rtFields == null || rtFields.length == 0) {
         return -1;
      }

      for(int i = 0; i < rtFields.length; i++) {
         if(rtFields[i] == null) {
            continue;
         }

         String refName = rtFields[i].getName();

         if(isCrosstab && rtFields[i] instanceof VSAggregateRef) {
            refName = CrossTabFilterUtil.getCrosstabRTAggregateName((VSAggregateRef) rtFields[i], false);
         }
         else if(rtFields[i] instanceof VSAggregateRef) {
            refName = ((VSAggregateRef) rtFields[i]).getFullName(false);
         }
         else if(rtFields[i] instanceof VSDataRef) {
            refName = ((VSDataRef) rtFields[i]).getFullName();
         }

         if(Tool.equals(refName, header) || ((String) header).startsWith(refName + "_")) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Find the date type ref to do date comparison, search order is row, col
    * @return CommonKVModel key is axis(ROW or COL), value is date dimension.
    */
   public static CommonKVModel<String, XDimensionRef> getComparisonDateRef(DataRef[] rowHeaders,
                                                                           DataRef[] colHeaders)
   {
      DataRef[] fields = rowHeaders;
      CommonKVModel<String, XDimensionRef> result = new CommonKVModel<>();
      DataRef dateDim = null;
      Boolean dimensionFromRow = null;

      if(fields != null) {
         dateDim = DateComparisonUtil.findDateDimension(fields);

         if(dateDim != null) {
            dimensionFromRow = true;
         }
      }

      fields = colHeaders;

      if(dateDim == null && fields != null) {
         dateDim = DateComparisonUtil.findDateDimension(fields);

         if(dateDim != null) {
            dimensionFromRow = false;
         }
      }

      if(!(dateDim instanceof XDimensionRef)) {
         return null;
      }

      result.setKey(dimensionFromRow ? "ROW" : "COL");
      result.setValue((XDimensionRef) dateDim);

      return result;
   }

   /**
    * Since dc chart don't display the temp date parts data which are just used to expand
    * data properly, but chart selection still need these date parts to create right condition,
    * so here need to add VSFieldValue for these columns if necessary.
    *
    * @param assembly the current chart assembly.
    * @param dataSet  the current dataset.
    * @param selection the selection which need to fix.
    */
   public static void fixDatePartSelection(ChartVSAssembly assembly, VSDataSet dataSet,
                                           VSSelection selection)
   {
      VSChartInfo cinfo = assembly.getVSChartInfo();
      String dcPartCol = DateComparisonUtil.getDcPartCol(cinfo);

      if(dcPartCol == null || selection == null) {
         return;
      }

      ChartVSAssemblyInfo vinfo = (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo();
      XDimensionRef[] tempDcDateParts = vinfo.getTempDateGroupRef();
      List<XDimensionRef> tempDcDatePartList = new ArrayList<>();

      if(tempDcDateParts == null) {
         return;
      }

      for(XDimensionRef tempDcDatePart : tempDcDateParts) {
         if(tempDcDatePart == null || tempDcDatePart instanceof VSDimensionRef &&
            ((VSDimensionRef) tempDcDatePart).isIgnoreDcTemp())
         {
            continue;
         }

         tempDcDatePartList.add(tempDcDatePart);
      }

      if(tempDcDatePartList == null || tempDcDatePartList.size() == 0) {
         return;
      }

      for(int i = 0; i < selection.getPointCount(); i++) {
         VSPoint point = selection.getPoint(i);
         List<VSFieldValue> list = new ArrayList<>();

         for(int j = 0; point != null && j < point.getValueCount(); j++) {
            VSFieldValue fieldValue = point.getValue(j);

            if(fieldValue == null || !Tool.equals(dcPartCol, fieldValue.getFieldName())) {
               continue;
            }

            VSValue vsValue = fieldValue.getFieldValue();
            String val = vsValue == null ? null : vsValue.getValue();
            String[] arr = val == null ? null : val.split("-");

            if(arr == null || arr.length != tempDcDatePartList.size() + 1) {
               continue;
            }

            for(int k = 0; k < arr.length - 1; k++) {
               if(isIgnoreDcTemp(tempDcDatePartList.get(k))) {
                  continue;
               }

               list.add(new VSFieldValue(tempDcDatePartList.get(k).getFullName(), arr[k], true));
            }

            // now set back to original data of DCMergeDatePartFilter.MergePartCell.
            vsValue.setValue(arr[arr.length - 1]);
         }

         for(int j = 0; j < list.size(); j++) {
            point.addValue(list.get(j));
         }
      }
   }

   /**
    * Update nested date comparison share from.
    */
   public static void updateNestedShareFrom(Viewsheet vs, VSAssemblyInfo vinfo) {
      if(vs == null || !(vinfo instanceof DateCompareAbleAssemblyInfo)) {
         return;
      }

      DateCompareAbleAssemblyInfo dinfo = (DateCompareAbleAssemblyInfo) vinfo;
      DateComparisonInfo comparisonInfo = dinfo.getDateComparisonInfo();
      String shareFrom = dinfo.getComparisonShareFrom();

      if(shareFrom == null && comparisonInfo != null) {
         return;
      }

      String name = vinfo.getAbsoluteName();

      for(Assembly assembly : vs.getAssemblies()) {
         if(!(assembly.getInfo() instanceof DateCompareAbleAssemblyInfo)) {
            continue;
         }

         if(Tool.equals(assembly.getAbsoluteName(), name) ||
            Tool.equals(assembly.getAbsoluteName(), shareFrom))
         {
            continue;
         }

         DateCompareAbleAssemblyInfo info = (DateCompareAbleAssemblyInfo) assembly.getInfo();

         if(Tool.equals(name, info.getComparisonShareFrom())) {
            info.setComparisonShareFrom(shareFrom);
         }
      }
   }

   /**
    * Whether vs contains the assembly that applied the date comparison.
    * @param vs viewsheet.
    */
   public static boolean containsDateComparison(Viewsheet vs) {
      if(vs == null) {
         return false;
      }

      Assembly[] assemblies = vs.getAssemblies();

      if(assemblies == null) {
         return false;
      }

      for(Assembly assembly : assemblies) {
         if(!(assembly instanceof VSAssembly)) {
            continue;
         }

         if(appliedDateComparison(((VSAssembly) assembly).getVSAssemblyInfo())) {
            return true;
         }
      }

      return false;
   }

   // if there is group by week, change year/quarter/month to
   // year-of-week/quarter-of-week/month-of-week since the dates are broken at week boundaries
   // across year/month. otherwise the data may be out of sync.
   public static void updateWeekGroupingLevels(DateComparisonInfo dcInfo, VSChartInfo chartInfo,
                                               int weekOfMonth)
   {
      if(dcInfo.alignWeek()) {
         updateWeekGroupingLevels(dcInfo, chartInfo.getRTXFields(), weekOfMonth);
         updateWeekGroupingLevels(dcInfo, chartInfo.getRTYFields(), weekOfMonth);
         updateAestheticRefWeekGroupingLevels(chartInfo, weekOfMonth);
      }
   }

   // if there is group by week, change year/quarter/month to
   // year-of-week/quarter-of-week/month-of-week since the dates are broken at week boundaries
   // across year/month. otherwise the data may be out of sync.
   public static void updateWeekGroupingLevels(DateComparisonInfo dcInfo, DataRef[] groups,
                                               int weekOfMonth)
   {
      if(dcInfo.alignWeek()) {
         for(DataRef group : groups) {
            if(group instanceof XDimensionRef) {
               updateWeekGroupingLevels((XDimensionRef) group, weekOfMonth);
            }
            else if(group instanceof VSChartAggregateRef) {
               updateAestheticRefWeekGroupingLevels((VSChartAggregateRef) group, weekOfMonth);
            }
         }
      }
   }

   private static void updateAestheticRefWeekGroupingLevels(ChartBindable bindable, int weekOfMonth)
   {
      if(bindable == null) {
         return;
      }

      updateWeekGroupingLevels(bindable.getColorField(), weekOfMonth);
      updateWeekGroupingLevels(bindable.getSizeField(), weekOfMonth);
      updateWeekGroupingLevels(bindable.getTextField(), weekOfMonth);
      updateWeekGroupingLevels(bindable.getShapeField(), weekOfMonth);
   }

   private static void updateWeekGroupingLevels(AestheticRef aestheticRef, int weekOfMonth) {
      if(aestheticRef == null) {
         return;
      }

      DataRef dataRef = aestheticRef.getRTDataRef();

      if(dataRef instanceof XDimensionRef) {
         updateWeekGroupingLevels((XDimensionRef) dataRef, weekOfMonth);
      }
   }

   private static void updateWeekGroupingLevels(XDimensionRef dim, int weekOfMonth) {
      if(DateComparisonUtil.isDCCalcDatePartRef(dim)) {
         return;
      }

      if(dim.getDateLevel() == DateRangeRef.YEAR_DATE_GROUP) {
         dim.setDateLevel(DateRangeRef.YEAR_OF_FULL_WEEK);
      }
      else if(dim.getDateLevel() == DateRangeRef.MONTH_DATE_GROUP) {
         dim.setDateLevel(DateRangeRef.MONTH_OF_FULL_WEEK);
      }
      else if(dim.getDateLevel() == DateRangeRef.QUARTER_DATE_GROUP) {
         dim.setDateLevel(DateRangeRef.QUARTER_OF_FULL_WEEK);
      }

      setForceDcToDateWeekOfMonth(weekOfMonth, dim);
   }

   public static int convertNormalToFullWeekLevel(int normalLevel) {
      switch(normalLevel) {
         case XConstants.YEAR_DATE_GROUP:
            return DateRangeRef.YEAR_OF_FULL_WEEK;
         case XConstants.QUARTER_DATE_GROUP :
            return DateRangeRef.QUARTER_OF_FULL_WEEK;
         case XConstants.MONTH_DATE_GROUP:
            return DateRangeRef.MONTH_OF_FULL_WEEK;
         default:
            return normalLevel;
      }
   }

   public static int convertFullWeekLevelToNormal(int fullWeekLevel) {
      switch(fullWeekLevel) {
         case DateRangeRef.YEAR_OF_FULL_WEEK:
            return XConstants.YEAR_DATE_GROUP;
         case DateRangeRef.QUARTER_OF_FULL_WEEK :
            return XConstants.QUARTER_DATE_GROUP;
         case DateRangeRef.MONTH_OF_FULL_WEEK:
            return XConstants.MONTH_DATE_GROUP;
         default:
            return fullWeekLevel;
      }
   }

   public static void setForceDcToDateWeekOfMonth(int weekOfMonth, XDimensionRef dim) {
      if(weekOfMonth > 4 && dim instanceof VSDimensionRef &&
         dim.getDateLevel() == DateRangeRef.YEAR_OF_FULL_WEEK ||
         dim.getDateLevel() == DateRangeRef.MONTH_OF_FULL_WEEK ||
         dim.getDateLevel() == DateRangeRef.QUARTER_OF_FULL_WEEK ||
         dim.getDateLevel() == DateRangeRef.MONTH_OF_FULL_WEEK_PART ||
         dim.getDateLevel() == DateRangeRef.QUARTER_OF_FULL_WEEK_PART ||
         dim.getDateLevel() == DateRangeRef.MONTH_OF_QUARTER_FULL_WEEK_PART)
      {
         ((VSDimensionRef) dim).setForceDcToDateWeekOfMonth(weekOfMonth);
      }
   }

   public static String getNormalLevelFullName(VSDimensionRef dim) {
      if(dim == null) {
         return null;
      }

      if(dim.isFullWeekDateLevel()) {
         VSDimensionRef clone = dim.clone();
         clone.setDateLevel(convertFullWeekLevelToNormal(dim.getDateLevel()));
         return clone.getFullName();
      }

      return dim.getFullName();
   }

   public static boolean adjustCalendarByForceWM(Calendar calendar, int forceWM) {
      Date date = calendar.getTime();
      int weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH);

      if(forceWM > 0 && weekOfMonth != forceWM) {
         calendar.set(Calendar.DATE, 1);
         calendar.add(Calendar.MONTH, -1);

         if(weekOfMonth + calendar.getActualMaximum(Calendar.WEEK_OF_MONTH) == forceWM) {
            return true;
         }
      }

      calendar.setTime(date);

      return false;
   }

   public static void syncWeekGroupingLevels(CrosstabVSAssemblyInfo info) {
      DateComparisonInfo dcInfo = info == null ? null : info.getDateComparisonInfo();

      if(dcInfo == null || !dcInfo.alignWeek()) {
         return;
      }

      Map<String, String> fullLevelNameMap = info.getFullLevelNameMap();
      syncHighlightAttr(info.getHighlightAttr(), fullLevelNameMap);
      syncFormatInfo(info.getFormatInfo(), fullLevelNameMap);
   }


   private static void syncHighlightAttr(TableHighlightAttr attr,
                                          Map<String, String> fullLevelNameMap)
   {
      if(attr == null) {
         return;
      }

      Map<TableDataPath, HighlightGroup> hgMap = attr.getHighlightMap();
      Iterator<Map.Entry<TableDataPath, HighlightGroup>> iterator = hgMap.entrySet().iterator();

      while(iterator.hasNext()) {
         Map.Entry<TableDataPath, HighlightGroup> entry = iterator.next();
         TableDataPath path = entry.getKey();
         HighlightGroup highlightGroup = entry.getValue();

         if(path == null || highlightGroup == null) {
            continue;
         }

         TableDataPath npath = (TableDataPath) path.clone();
         boolean pathChanged = syncPath(npath, fullLevelNameMap);
         boolean condItemChanged = syncConditionItem(highlightGroup, fullLevelNameMap);

         if(pathChanged || condItemChanged) {
            if(pathChanged) {
               iterator.remove();
               path = npath;
            }

            hgMap.put(path, highlightGroup);
         }
      }
   }

   private static void syncFormatInfo(FormatInfo formatInfo, Map<String, String> fullLevelNameMap) {
      Map<TableDataPath, VSCompositeFormat> fmtMap =
         formatInfo == null ? null : formatInfo.getFormatMap();

      if(fmtMap == null) {
         return;
      }

      Iterator<Map.Entry<TableDataPath, VSCompositeFormat>> iterator = fmtMap.entrySet().iterator();

      while(iterator.hasNext()) {
         Map.Entry<TableDataPath, VSCompositeFormat> entry = iterator.next();
         TableDataPath path = entry.getKey();
         VSCompositeFormat format = entry.getValue();

         if(path == null || format == null) {
            continue;
         }

         TableDataPath npath = (TableDataPath) path.clone();

         if(syncPath(npath, fullLevelNameMap)) {
            iterator.remove();
            fmtMap.put(npath, format);
         }
      }
   }

   public static void syncWeekGroupingLevels(VSChartInfo info, DateComparisonInfo dcInfo) {
      if(dcInfo == null || !dcInfo.alignWeek()) {
         return;
      }

      ChartRef[] runtimeDateComparisonRefs = info.getRuntimeDateComparisonRefs();

      if(runtimeDateComparisonRefs != null) {
         return;
      }

      for(ChartRef runtimeDateComparisonRef : runtimeDateComparisonRefs) {
         if(!(runtimeDateComparisonRef instanceof VSChartDimensionRef)) {
            continue;
         }

         VSChartDimensionRef chartDimensionRef = (VSChartDimensionRef) runtimeDateComparisonRef;
         int dateLevel = chartDimensionRef.getDateLevel();

         if(dateLevel == XConstants.YEAR_DATE_GROUP) {
            chartDimensionRef.setDateLevelValue(DateRangeRef.YEAR_OF_FULL_WEEK + "");
         }
         else if(dateLevel == XConstants.QUARTER_DATE_GROUP) {
            chartDimensionRef.setDateLevelValue(DateRangeRef.QUARTER_OF_FULL_WEEK + "");
         }
         else if(dateLevel == XConstants.MONTH_DATE_GROUP) {
            chartDimensionRef.setDateLevelValue(DateRangeRef.MONTH_OF_FULL_WEEK + "");
         }
      }
   }

   private static boolean syncConditionItem(HighlightGroup highlightGroup,
                                           Map<String, String> fullLevelNameMap)
   {
      if(highlightGroup == null) {
         return false;
      }

      boolean changed = false;
      String[] levels = highlightGroup.getLevels();

      for(String level : levels) {
         String[] names = highlightGroup.getNames(level);

         for(String name : names) {
            Highlight texthighlight =
               highlightGroup.getHighlight(level, name);
            ConditionList conditionlist = texthighlight.getConditionGroup();

            for(int d = 0; d < conditionlist.getSize(); d += 2) {
               ConditionItem conditionItem = conditionlist.getConditionItem(d);
               DataRef dataRef = conditionItem.getAttribute();

               if(!fullLevelNameMap.containsKey(dataRef.getAttribute()) ||
                  !(dataRef instanceof ColumnRef) ||
                  !(((ColumnRef) dataRef).getDataRef() instanceof DateRangeRef))
               {
                  continue;
               }

               ColumnRef column = (ColumnRef) dataRef;
               DateRangeRef dateRangeRef = (DateRangeRef) column.getDataRef();
               DataRef baseRef = dateRangeRef.getDataRef();

               if(baseRef == null) {
                  continue;
               }

               dateRangeRef.setDateOption(
                  convertNormalToFullWeekLevel(dateRangeRef.getDateOption()));
               dateRangeRef.setName(
                  dateRangeRef.getName(baseRef.getName(), dateRangeRef.getDateOption()));
               conditionItem.setAttribute(column);
               changed = true;
            }
         }
      }

      if(changed) {
         highlightGroup.updateHighlightGroup0();
      }

      return changed;
   }

   private static boolean syncPath(TableDataPath path, Map<String, String> fullLevelNameMap) {
      String[] arr = path.getPath();
      String[] narr = Arrays.stream(arr)
         .map(gname -> fullLevelNameMap.containsKey(gname) ? fullLevelNameMap.get(gname) : gname)
         .toArray(String[]::new);

      if(!Tool.equals(arr, narr)) {
         path.setPath(narr);
         return true;
      }

      return false;
   }

   private static Calendar getCalendar() {
      Calendar calendar = new GregorianCalendar();
      calendar.setFirstDayOfWeek(Tool.getFirstDayOfWeek());
      calendar.setMinimalDaysInFirstWeek(7);

      return calendar;
   }
}
