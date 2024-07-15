/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.composition.graph.calc.ChangeCalc;
import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.report.filter.DCMergeDatesCell;
import inetsoft.report.filter.GroupTuple;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.VisualFrameWrapper;
import inetsoft.util.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * DateComparisonInfo, the info of a date comparison.
 *
 * @version 13.5
 * @author InetSoft Technology Corp
 */
public class DateComparisonInfo implements Cloneable, XMLSerializable {
   public static final int VALUE = Calculator.VALUE;
   public static final int CHANGE = Calculator.CHANGE;
   public static final int PERCENT = Calculator.PERCENT;
   public static final int CHANGE_VALUE = 101;
   public static final int PERCENT_VALUE = 102;

   /**
    * Get the calculate column from the date comparison.
    * @param ref a date dimension ref.
    * @param compareLowPartLevel whether comparison value is lower part level for given ref.
    * @return
    */
   public Calculator createComparisonCalc(DataRef ref, boolean compareLowPartLevel,
                                          List<XDimensionRef> comparisonDateDims)
   {
      if(ref == null || invalid() || !(ref instanceof VSDimensionRef)) {
         return null;
      }

      ValueOfCalc calculator = null;
      String columnName = ((VSDataRef) ref).getFullName();
      // for x to date.
      int from;

      //for level is lower part than ref level.
      //for example: compare ref level is month of year,
      // the param ref is year level from level is ValueOfCalc.PREVIOUS_YEAR.
      if(compareLowPartLevel && isStdPeriod()) {
         int level = DateComparisonUtil.convertFullWeekLevelToNormal(
            ((VSDimensionRef) ref).getDateLevel());

         if(level == XConstants.YEAR_DATE_GROUP) {
            from = ValueOfCalc.PREVIOUS_YEAR;
         }
         else if(level == XConstants.QUARTER_DATE_GROUP) {
            from = ValueOfCalc.PREVIOUS_QUARTER;
         }
         else if(level == XConstants.MONTH_DATE_GROUP) {
            from = ValueOfCalc.PREVIOUS_MONTH;
         }
         else if(level == XConstants.WEEK_DATE_GROUP) {
            from = ValueOfCalc.PREVIOUS_WEEK;
         }
         else {
            from = ValueOfCalc.PREVIOUS;
         }
      }
      else {
         from = getCalcValueFrom();
      }

      if(comparisonOption == Calculator.VALUE) {
         return null;
      }

      ChangeCalc calc = new ChangeCalc();
      calculator = calc;

      calc.setFrom(from);
      calc.setColumnName(columnName);
      calc.setAsPercent(comparisonOption == PERCENT || comparisonOption == PERCENT_VALUE);
      calc.setDcPeriods(isStdPeriod() ? null : ((CustomPeriods) dcPeriods).getDatePeriods(true));
      calc.setComparisonDateDims(comparisonDateDims);
      calc.setFirstWeek(isFirstWeek());

      return calculator;
   }

   public ConditionList getDateComparisonConditions(DataRef ref){
      return invalid() ? null : getDateComparisonConditions0(ref);
   }

   /**
    * Get the date group level to be used by the date grouping for data series.
    */
   public int getPeriodDateLevel() {
      int periodLevel = DateRangeRef.YEAR_INTERVAL;

      if(dcPeriods instanceof StandardPeriods) {
         periodLevel = ((StandardPeriods) dcPeriods).getDateLevel();
      }

      return periodLevel;
   }

   public XDimensionRef updateDateDimensionLevel(XDimensionRef ref, String source,
                                                 Viewsheet vs, boolean inner)
   {
      if(!(ref instanceof VSDimensionRef)) {
         return ref;
      }

      VSDimensionRef dimensionRef = (VSDimensionRef) ref;
      dimensionRef.setDateLevelValue("");

      if(dcPeriods instanceof StandardPeriods && isDateSeries()) {
         int granularity = dcInterval.getGranularity();
         int parentLevel = getGranularityParentLevel();

         if((granularity & YEAR) == YEAR) {
            if(parentLevel < XConstants.YEAR_DATE_GROUP) {
               return ref;
            }

            dimensionRef.setDateLevelValue(XConstants.YEAR_DATE_GROUP + "");
         }
         else if((granularity & QUARTER) == QUARTER) {
            if(parentLevel < XConstants.QUARTER_DATE_GROUP) {
               return ref;
            }

            if(parentLevel == XConstants.YEAR_DATE_GROUP) {
               dimensionRef.setDateLevelValue(XConstants.QUARTER_OF_YEAR_DATE_GROUP + "");
            }
            else {
               dimensionRef.setDateLevelValue(XConstants.QUARTER_DATE_GROUP + "");
            }
         }
         else if((granularity & MONTH) == MONTH) {
            if(parentLevel < XConstants.MONTH_DATE_GROUP) {
               return ref;
            }

            if(parentLevel == XConstants.YEAR_DATE_GROUP) {
               dimensionRef.setDateLevelValue(XConstants.MONTH_OF_YEAR_DATE_GROUP + "");
            }
            else if(parentLevel == XConstants.QUARTER_DATE_GROUP) {
               dimensionRef.setDataRef(createCalcRefForNonSupportPartLevel(
                  XConstants.MONTH_DATE_GROUP, XConstants.QUARTER_DATE_GROUP, dimensionRef, source,
                  vs));
            }
            else {
               dimensionRef.setDateLevelValue(XConstants.MONTH_DATE_GROUP + "");
            }

            // for month-of-quarter, show month as: Q1 (Feb)
            if(dimensionRef instanceof VSChartDimensionRef &&
               parentLevel == XConstants.YEAR_DATE_GROUP &&
               dcInterval.getContextLevel() == XConstants.QUARTER_DATE_GROUP)
            {
               AxisDescriptor desc = ((VSChartDimensionRef) dimensionRef).getAxisDescriptor();
               CompositeTextFormat fmt = new CompositeTextFormat();
               fmt.setFormat(new XFormatInfo(TableFormat.DATE_FORMAT, "'Q'QQ '('MMM')'"));
               desc.setColumnLabelTextFormat(dimensionRef.getFullName(), fmt);
            }
         }
         else if((granularity & WEEK) == WEEK) {
            if(parentLevel < XConstants.WEEK_DATE_GROUP) {
               return ref;
            }

            if(parentLevel == XConstants.YEAR_DATE_GROUP) {
               dimensionRef.setDataRef(createCalcRefForNonSupportPartLevel(
                  XConstants.WEEK_DATE_GROUP, XConstants.YEAR_DATE_GROUP, dimensionRef, source,
                  vs));
            }
            else if(parentLevel == XConstants.QUARTER_DATE_GROUP) {
               dimensionRef.setDataRef(createCalcRefForNonSupportPartLevel(
                  XConstants.WEEK_DATE_GROUP, XConstants.QUARTER_DATE_GROUP, dimensionRef, source,
                  vs));
            }
            else if(parentLevel == XConstants.MONTH_DATE_GROUP) {
               dimensionRef.setDataRef(createCalcRefForNonSupportPartLevel(
                  XConstants.WEEK_DATE_GROUP, XConstants.MONTH_DATE_GROUP, dimensionRef, source,
                  vs));
            }
            else {
               dimensionRef.setDateLevelValue(XConstants.WEEK_DATE_GROUP + "");
            }
         }
         else if((granularity & DAY) == DAY) {
            if(parentLevel < XConstants.DAY_DATE_GROUP) {
               return ref;
            }

            if(parentLevel == XConstants.YEAR_DATE_GROUP) {
               dimensionRef.setDataRef(createCalcRefForNonSupportPartLevel(
                  XConstants.DAY_DATE_GROUP, XConstants.YEAR_DATE_GROUP, dimensionRef, source, vs));
            }
            else if(parentLevel == XConstants.QUARTER_DATE_GROUP) {
               dimensionRef.setDataRef(createCalcRefForNonSupportPartLevel(
                  XConstants.DAY_DATE_GROUP, XConstants.QUARTER_DATE_GROUP, dimensionRef, source,
                  vs));
            }
            else if(parentLevel == XConstants.MONTH_DATE_GROUP) {
               dimensionRef.setDateLevelValue(XConstants.DAY_OF_MONTH_DATE_GROUP + "");
            }
            else if(parentLevel == XConstants.WEEK_DATE_GROUP) {
               dimensionRef.setDateLevelValue(XConstants.DAY_OF_WEEK_DATE_GROUP + "");
            }
            else {
               dimensionRef.setDateLevelValue(XConstants.DAY_DATE_GROUP + "");
            }
         }
      }
      else {
         int granularity = dcInterval.getGranularity();

         if((granularity & YEAR) == YEAR) {
            dimensionRef.setDateLevelValue(XConstants.YEAR_DATE_GROUP + "");
         }
         else if((granularity & QUARTER) == QUARTER) {
            dimensionRef.setDateLevelValue(XConstants.QUARTER_DATE_GROUP + "");
         }
         else if((granularity & MONTH) == MONTH) {
            dimensionRef.setDateLevelValue(XConstants.MONTH_DATE_GROUP + "");
         }
         else if((granularity & WEEK) == WEEK) {
            dimensionRef.setDateLevelValue(XConstants.WEEK_DATE_GROUP + "");
         }
         else if((granularity & DAY) == DAY) {
            dimensionRef.setDateLevelValue(XConstants.DAY_DATE_GROUP + "");
         }

         if(dcPeriods instanceof CustomPeriods && ref instanceof VSDimensionRef) {
            VSDimensionRef dim = (VSDimensionRef) ref;
            setCustomPartMap(dim, null, inner);
         }
      }

      DataRef groupRef = dimensionRef.getDataRef();

      if(groupRef instanceof CalculateRef &&
         StringUtils.startsWith(groupRef.getName(), DC_CALC_FIELD_PREFIX))
      {
         dimensionRef.setDataType(XSchema.STRING);
      }

      return dimensionRef;
   }

   /**
    * Get the parent date group level of granularity part.
    */
   public int getGranularityParentLevel() {
      if(!(dcPeriods instanceof StandardPeriods)) {
         return 0;
      }

      StandardPeriods standardPeriods = (StandardPeriods) dcPeriods;

      int period = standardPeriods.getDateLevel();
      int context = dcInterval.getContextLevel();
      int interval = dcInterval.getLevel();
      int granularity = dcInterval.getGranularity();

      if(interval == ALL) {
         return period;
      }

      interval = DateComparisonUtil.dcIntervalLevelToDateGroupLevel(interval);
      granularity = DateComparisonUtil.dcIntervalLevelToDateGroupLevel(granularity);

      if(granularity != interval) {
         return interval;
      }

      if(interval != context) {
         return context;
      }

      if(context != period) {
         return period;
      }

      return period;
   }

   /**
    * Create the calc field for nonsupport part date level.
    *
    * @param partLowLevel partLowLevel part low level.
    * @param partParentLevel part parent level.
    * @param ref dimension ref.
    */
   private DataRef createCalcRefForNonSupportPartLevel(int partLowLevel, int partParentLevel,
                                                       DataRef ref, String source, Viewsheet vs)
   {
      return createCalcRefForNonSupportPartLevel(partLowLevel, partParentLevel, ref, source, vs, false);
   }

   /**
    * Create the calc field for nonsupport part date level.
    *
    * @param partLowLevel partLowLevel part low level.
    * @param partParentLevel part parent level.
    * @param ref dimension ref.
    */
   private DataRef createCalcRefForNonSupportPartLevel(int partLowLevel, int partParentLevel, DataRef ref,
                                                       String source, Viewsheet vs, boolean fullWeek)
   {
      String calcName = "";
      StringBuffer expression = new StringBuffer();
      String colName = ref.getName();
      int toDateWeekOfMonth = getToDateWeekOfMonth();

      if(partParentLevel == XConstants.YEAR_DATE_GROUP) {
         if(partLowLevel == XConstants.DAY_DATE_GROUP) {
            expression.append("datePart('y', field['");
            expression.append(colName);
            expression.append("'])");
            calcName = "DayOfYear(" + colName + ")";
         }
         else if(partLowLevel == XConstants.WEEK_DATE_GROUP) {
            expression.append("datePartForceWeekOfMonth('wy', field['");
            expression.append(colName);
            expression.append("'], true, " + toDateWeekOfMonth + ")");
            calcName = "WeekOfYear(" + colName + ")";
         }
      }
      else if(partParentLevel == XConstants.QUARTER_DATE_GROUP) {
         if(partLowLevel == XConstants.MONTH_DATE_GROUP) {
            if(fullWeek) {
               expression.append("datePartForceWeekOfMonth('wmq', field['");
               expression.append(colName);
               expression.append("'], true, " + toDateWeekOfMonth + ")");
               calcName = "MonthOfQuarterOfWeek(" + colName + ")";
            }
            else {
               expression.append("datePart('mq', field['");
               expression.append(colName);
               expression.append("'], true)");
               calcName = "MonthOfQuarter(" + colName + ")";
            }
         }
         else if(partLowLevel == XConstants.WEEK_DATE_GROUP) {
            expression.append("datePart('wq', field['");
            expression.append(colName);
            expression.append("'], true)");
            calcName = "WeekOfQuarter(" + colName + ")";
         }
         else if(partLowLevel == XConstants.DAY_DATE_GROUP) {
            expression.append("datePart('dq', field['");
            expression.append(colName);
            expression.append("'])");
            calcName = "DayOfQuarter(" + colName + ")";
         }
      }
      else if(partParentLevel == XConstants.MONTH_DATE_GROUP) {
         if(partLowLevel == XConstants.WEEK_DATE_GROUP) {
            expression.append("datePartForceWeekOfMonth('wm', field['");
            expression.append(colName);
            expression.append("'], true, " + (toDateWeekOfMonth > 4 ? toDateWeekOfMonth : -1)
               + ")");
            calcName = "WeekOfMonth(" + colName + ")";
         }
      }

      CalculateRef calc = new CalculateRef();
      calc.setDcRuntime(true);
      calc.setName(calcName);
      calc.setSQL(false);
      ExpressionRef expr = new ExpressionRef();
      expr.setName(calcName);
      expr.setDataType(XSchema.INTEGER);
      calc.setDataRef(expr);
      expr.setExpression(expression.toString());
      vs.addCalcField(source, calc);

      return calc;
   }

   public boolean isDateSeries() {
      int granularity = dcInterval.getGranularity();
      int level = dcInterval.getLevel();

      // group level different from range level, should create series.
      if((level & granularity) != granularity) {
         return true;
      }

      if(dcPeriods instanceof StandardPeriods) {
         StandardPeriods periods = (StandardPeriods) dcPeriods;

         if(getPeriodCount(periods) > 1 && periods.getDateLevel() != dcInterval.getContextLevel()) {
            return true;
         }

         return isCompareAll();
      }

      return !isStdPeriod();
   }

   /**
    * Get the number of periods include in comparison.
    */
   private int getPeriodCount(StandardPeriods periods) {
      return periods.getPreCount() + (periods.isInclusive() ? 1 : 0);
   }

   public boolean isValuePlus() {
      return comparisonOption == CHANGE_VALUE || comparisonOption == PERCENT_VALUE;
   }

   public boolean isValueOnly() {
      return comparisonOption == VALUE;
   }

   public boolean periodLevelSameAsGranularityLevel() {
      int level = dcInterval.getGranularity();
      int periodLevel = XConstants.YEAR_DATE_GROUP;

      if(dcPeriods instanceof StandardPeriods) {
         periodLevel = ((StandardPeriods) dcPeriods).getDateLevel();
      }

      if((level & YEAR) == YEAR) {
         return periodLevel == XConstants.YEAR_DATE_GROUP;
      }
      else if((level & QUARTER) == QUARTER) {
         return periodLevel == XConstants.QUARTER_DATE_GROUP;
      }
      else if((level & MONTH) == MONTH) {
         return periodLevel == XConstants.MONTH_DATE_GROUP;
      }
      else if((level & WEEK) == WEEK) {
         return periodLevel == XConstants.WEEK_DATE_GROUP;
      }
      else if((level & DAY) == DAY) {
         return periodLevel == XConstants.DAY_DATE_GROUP;
      }

      return false;
   }

   public boolean invalid() {
      if(dcPeriods == null || dcInterval == null) {
         return true;
      }

      if(isStdPeriod()) {
         StandardPeriods standardPeriods = (StandardPeriods) dcPeriods;
         int level = dcInterval.getLevel();
         int periodLevel = standardPeriods.getDateLevel();

         if((level & YEAR) == YEAR && periodLevel < XConstants.YEAR_DATE_GROUP ||
            (level & QUARTER) == QUARTER && periodLevel < XConstants.QUARTER_DATE_GROUP ||
            (level & MONTH) == MONTH && periodLevel < XConstants.MONTH_DATE_GROUP ||
            (level & WEEK) == WEEK && periodLevel < XConstants.WEEK_DATE_GROUP ||
            (level & DAY) == DAY && periodLevel < XConstants.DAY_DATE_GROUP)
         {
            return true;
         }
      }
      else {
         List<DatePeriod> periods = ((CustomPeriods) dcPeriods).getDatePeriods();

         if(periods != null) {
            boolean invalidPeriod = periods.stream().anyMatch(period ->
               period.getStart() == null || period.getEnd() == null ||
                  period.getStart().after(period.getEnd()));

            if(invalidPeriod) {
               return true;
            }
         }
      }

      if(!isCompareAll() && !dcInterval.isEndDayAsToDate() && dcInterval.getIntervalEndDate() == null) {
         return true;
      }

      return false;
   }

   public boolean isStdPeriod() {
      return dcPeriods instanceof StandardPeriods;
   }

   /**
    * Check whether the to date is period first week.
    * @return <tt>true</tt> if first, <tt>false</tt> otherwise.
    */
   private boolean isFirstWeek() {
      if(isStdPeriod() && !isCompareAll() &&
         (dcInterval.getLevel() & WEEK) == WEEK)
      {
         int periodLevel = ((StandardPeriods) dcPeriods).getDateLevel();
         Date intervalToDate = getIntervalEndDate();
         Calendar calendar = getCalendar();
         calendar.setTime(intervalToDate);

         if(periodLevel == XConstants.YEAR_DATE_GROUP) {
            return 1 == calendar.get(Calendar.WEEK_OF_YEAR);
         }
         else if(periodLevel == XConstants.QUARTER_DATE_GROUP) {
            return calendar.get(Calendar.MONTH) % 3 == 0 &&
               calendar.get(Calendar.WEEK_OF_MONTH) == 1;
         }
         else if(periodLevel == XConstants.MONTH_DATE_GROUP) {
            return calendar.get(Calendar.WEEK_OF_MONTH) == 1;
         }
      }

      return false;
   }

   private void setNoStdIntervalPartMap(int calendarLevel, int fixIntervalLevel,
                                        VSDimensionRef dim, CustomPeriods customPeriods,
                                        boolean inner)
   {
      customPeriods = customPeriods != null ? customPeriods : (CustomPeriods) dcPeriods;
      List<DatePeriod> periods = customPeriods.getDatePeriods(true);
      List<List<Date[]>> periodsIntervalRanges = getCustomPeriodsIntervalRanges(customPeriods);
      HashMap<Integer, HashMap<Integer, Date>> partGroups = new HashMap<>();
      int amount = fixIntervalLevel == XConstants.QUARTER_DATE_GROUP ? 3 : 1;

      for(int i = 0; i < periods.size(); i++) {
         DatePeriod period = periods.get(i);
         List<Date[]> ranges = periodsIntervalRanges.get(i);
         int index = 0;
         Set<Date> added = new HashSet<>();

         for(Date[] range : ranges) {
            if(!isStdPeriod() && !dateRangeIntersect(period, range)) {
               continue;
            }

            Calendar calendar = getCalendar();
            Date end = setDateToLevelStart(range[1], fixIntervalLevel);
            Date start = setDateToLevelStart(range[0], fixIntervalLevel);
            calendar.setTime(start);

            while(start != null && calendar.getTime().before(end) || calendar.getTime().equals(end))
            {
               if(!added.contains(calendar.getTime())) {
                  if(partGroups.get(index) == null) {
                     partGroups.put(index, new HashMap<>());
                  }

                  partGroups.get(index).put(i, calendar.getTime());
                  index++;
               }

               calendar.add(calendarLevel, amount);
            }
         }
      }

      convertToCustomPartMap(partGroups, periods, dim, inner);
   }

   private void setCustomPartMap(VSDimensionRef dim, DateComparisonPeriods specificPeriods,
                                 boolean inner)
   {
      specificPeriods = specificPeriods == null ? dcPeriods : specificPeriods;

      if(invalid() || !(specificPeriods instanceof CustomPeriods) || dim == null) {
         return;
      }

      CustomPeriods customPeriods = (CustomPeriods) specificPeriods;
      List<DatePeriod> periods = customPeriods.getDatePeriods(true);
      periods.sort(Comparator.comparing(DatePeriod::getStart));

      int level = dcInterval.getGranularity();
      int calendarLevel = -1;
      int fixIntervalLevel = -1;

      // All granularity means dates are grouped into the custom periods without further grouping.
      if(level == ALL) {
         return;
      }
      else if((level & YEAR) == YEAR) {
         calendarLevel = Calendar.YEAR;
         fixIntervalLevel = XConstants.YEAR_DATE_GROUP;
      }
      else if((level & QUARTER) == QUARTER) {
         calendarLevel = Calendar.MONTH;
         fixIntervalLevel = XConstants.QUARTER_DATE_GROUP;
      }
      else if((level & MONTH) == MONTH) {
         calendarLevel = Calendar.MONTH;
         fixIntervalLevel = XConstants.MONTH_DATE_GROUP;
      }
      else if((level & WEEK) == WEEK) {
         calendarLevel = Calendar.WEEK_OF_YEAR;
         fixIntervalLevel = XConstants.WEEK_DATE_GROUP;
      }
      else if((level & DAY) == DAY) {
         calendarLevel = Calendar.DAY_OF_YEAR;
         fixIntervalLevel = XConstants.DAY_DATE_GROUP;
      }

      if(!isCompareAll()) {
         setNoStdIntervalPartMap(calendarLevel, fixIntervalLevel, dim, customPeriods, inner);
      }
      else {
         HashMap<Integer, HashMap<Integer, Date>> partGroups = new HashMap<>();
         int amount = fixIntervalLevel == XConstants.QUARTER_DATE_GROUP ? 3 : 1;

         for(int i = 0; periods != null && i < periods.size(); i++) {
            DatePeriod period = periods.get(i);
            Date start = period.getStart();
            Date end = period.getEnd();

            Calendar calendar = getCalendar();
            setDateToLevelStart(calendar, fixIntervalLevel);
            end = setDateToLevelStart(end, fixIntervalLevel);
            start = setDateToLevelStart(start, fixIntervalLevel);
            calendar.setTime(start);
            int vindex = 0;

            while(start != null && calendar.getTime().before(end) || calendar.getTime().equals(end))
            {
               if(partGroups.get(vindex) == null) {
                  partGroups.put(vindex, new HashMap<>());
               }

               partGroups.get(vindex).put(i, calendar.getTime());
               calendar.add(calendarLevel, amount);
               vindex++;
            }
         }

         convertToCustomPartMap(partGroups, periods, dim, inner);
      }
   }

   /**
    * Return all the dates for each range.
    */
   private Map<Integer, List<Date>> getRangeDates(
      HashMap<Integer, HashMap<Integer, Date>> partGroups)
   {
      Set<Map.Entry<Integer, HashMap<Integer, Date>>> entrySet = partGroups.entrySet();
      Map<Integer, List<Date>> rangeDates = new HashMap<>();

      for(Map.Entry<Integer, HashMap<Integer, Date>> entry : entrySet) {
         HashMap<Integer, Date> partGroup = entry.getValue();
         Set<Map.Entry<Integer, Date>> entrySet0 = partGroup.entrySet();

         for(Map.Entry<Integer, Date> entry0 : entrySet0) {
            int periodIndex = entry0.getKey();

            if(!rangeDates.containsKey(periodIndex)) {
               rangeDates.put(periodIndex, new ArrayList<>());
            }

            rangeDates.get(periodIndex).add(entry0.getValue());
         }
      }

      return rangeDates;
   }

   private void convertToCustomPartMap(HashMap<Integer, HashMap<Integer, Date>> partGroups,
                                       List<DatePeriod> periods, VSDimensionRef dim,
                                       boolean inner)
   {
      Map<Integer, List<Date>> rangeDates =
         dim instanceof VSChartDimensionRef ? null : getRangeDates(partGroups);
      HashMap<Integer, Object> rangeGroupNames = new HashMap<>();
      Map<GroupTuple, DCMergeDatesCell> mergeDatesCellMap = new HashMap<>();
      Set<Map.Entry<Integer, HashMap<Integer, Date>>> entrySet = partGroups.entrySet();
      Map<Long, Integer> order = new HashMap<>();
      List<String> customLabels = isStdPeriod() ?
         null : DCNamedGroupInfo.getCustomPeriodLabels(periods);
      boolean isShowMostRecentDateOnly = dim instanceof VSChartDimensionRef &&
         isShowMostRecentDateOnly();

      for(Map.Entry<Integer, HashMap<Integer, Date>> entry : entrySet) {
         HashMap<Integer, Date> partGroup = entry.getValue();
         DCMergeDatesCell mergeDatesCell = new DCMergeDatesCell(isShowMostRecentDateOnly,
                                                                dim.getDateLevel(), rangeDates,
                                                                inner);
         Set<Map.Entry<Integer, Date>> entrySet0 = partGroup.entrySet();

         for(Map.Entry<Integer, Date> entry0 : entrySet0) {
            int periodIndex = entry0.getKey();
            Object group;

            if(rangeGroupNames.containsKey(periodIndex)) {
               group = rangeGroupNames.get(periodIndex);
            }
            else {
               if(isStdPeriod()) {
                  StandardPeriods standardPeriods = (StandardPeriods) dcPeriods;
                  group = setDateToLevelStart(periods.get(periodIndex).getStart(),
                                              standardPeriods.getDateLevel());
               }
               else {
                  group = customLabels.get(periodIndex);
                  rangeGroupNames.put(periodIndex, group);
               }
            }

            GroupTuple groupTuple = new GroupTuple();
            groupTuple.addValue(group);
            groupTuple.addValue(entry0.getValue());

            if(isValueOnly() || isValuePlus() || periodIndex != 0) {
               mergeDatesCell.addDate(entry0.getValue());
            }

            mergeDatesCellMap.put(groupTuple, mergeDatesCell);
            order.put(entry0.getValue().getTime(), entry.getKey());
         }
      }

      dim.setDcMergeGroup(mergeDatesCellMap);
      dim.setDcMergeGroupOrder(order);
   }

   /**
    * Check whether date in date range.
    *
    * @param range date range.
    * @param another another date range.
    * @return
    */
   private boolean dateRangeIntersect(DatePeriod range, Date[] another) {
      DatePeriod datePeriod = new DatePeriod();

      if(another != null && another.length == 2) {
         datePeriod.setStart(another[0]);
         datePeriod.setEnd(another[1]);
      }

      return dateRangeIntersect(range, datePeriod);
   }

   /**
    * Check whether date in date range.
    *
    * @param range date range.
    * @param another another date range.
    * @return
    */
   private boolean dateRangeIntersect(DatePeriod range, DatePeriod another) {
      if(range == null || another == null) {
         return false;
      }

      if(range.getEnd() == null || range.getStart() == null ||
         range.getStart().after(range.getEnd()))
      {
         return false;
      }

      if(another.getEnd() == null || another.getStart() == null ||
         another.getStart().after(another.getEnd()))
      {
         return false;
      }

      return (range.getStart().before(another.getStart()) ||
         range.getStart().equals(another.getStart())) && (range.getEnd().after(another.getStart()) ||
         range.getEnd().equals(another.getStart())) || (range.getStart().before(another.getEnd()) ||
         range.getStart().equals(another.getEnd())) && (range.getEnd().after(another.getEnd()) ||
         range.getEnd().equals(another.getEnd()));
   }

   /**
    * Get the condition according by date comparison info and ref.
    */
   private ConditionList getDateComparisonConditions0(DataRef ref) {
      if(ref == null) {
         return null;
      }

      if(ref instanceof VSDimensionRef) {
         ref = ((VSDimensionRef) ref).getDataRef();
      }

      ConditionList periodsCon = getPeriodsConditions(ref);
      ConditionList intervalCon = getIntervalConditions(ref);

      if(periodsCon == null) {
         return intervalCon;
      }
      else if(intervalCon == null) {
         return periodsCon;
      }

      List<ConditionList> cons = new ArrayList<>();
      cons.add(periodsCon);
      cons.add(intervalCon);

      return ConditionUtil.mergeConditionList(cons, JunctionOperator.AND);
   }

   public DateComparisonPeriods getPeriods() {
      return dcPeriods;
   }

   public void setDateComparisonPeriods(DateComparisonPeriods dateComparisonPeriods) {
      this.dcPeriods = dateComparisonPeriods;
   }

   public int getComparisonOption() {
      return comparisonOption;
   }

   public void setComparisonOption(int comparisonOption) {
      this.comparisonOption = comparisonOption;
   }

   public boolean isUseFacet() {
      return useFacet;
   }

   public void setUseFacet(boolean useFacet) {
      this.useFacet = useFacet;
   }

   public boolean isShowMostRecentDateOnly() {
      return showMostRecentDateOnly;
   }

   public void setShowMostRecentDateOnly(boolean showMostRecentDateOnly) {
      this.showMostRecentDateOnly = showMostRecentDateOnly;
   }

   public DateComparisonInterval getInterval() {
      return dcInterval;
   }

   public void setDateComparisonInterval(DateComparisonInterval dateComparisonInterval) {
      this.dcInterval = dateComparisonInterval;
   }

   public VisualFrameWrapper getDcColorFrameWrapper() {
      return colorFrame;
   }

   public void setDcColorFrameWrapper(VisualFrameWrapper colorFrame) {
      this.colorFrame = colorFrame;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      if(dcPeriods != null) {
         writer.println("<dateComparisonPeriods class=\"" +
            dcPeriods.getClass().getName() +"\">");
         dcPeriods.writeXML(writer);
         writer.println("</dateComparisonPeriods>");
      }

      if(dcInterval != null) {
         writer.println("<dateComparisonInterval>");
         dcInterval.writeXML(writer);
         writer.println("</dateComparisonInterval>");
      }

      if(colorFrame != null) {
         writer.println("<frameWrapper>");
         colorFrame.writeXML(writer);
         writer.println("</frameWrapper>");
      }

      writer.format("<comparisonOption><![CDATA[%s]]></comparisonOption>", comparisonOption);
      writer.format("<useFacet><![CDATA[%s]]></useFacet>", useFacet);
      writer.format("<onlyShowMostRecentDate><![CDATA[%s]]></onlyShowMostRecentDate>",
                    showMostRecentDateOnly);
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      Element element = Tool.getChildNodeByTagName(tag, "dateComparisonPeriods");

      if(element != null) {
         dcPeriods = DateComparisonPeriods.createDateComparisonPeriods(element);
      }

      element = Tool.getChildNodeByTagName(tag, "dateComparisonInterval");

      if(element != null) {
         dcInterval = new DateComparisonInterval();
         dcInterval.parseXML(element);
      }

      element = Tool.getChildNodeByTagName(tag, "frameWrapper");
      element = element == null ? null : Tool.getChildNodeByTagName(element, "legendFrame");
      colorFrame = new CategoricalColorFrameWrapper();

      if(element != null) {
         colorFrame.parseXML(element);
      }

      String value = Tool.getChildValueByTagName(tag, "comparisonOption");

      if(!Tool.isEmptyString(value)) {
         comparisonOption = Integer.parseInt(value);
      }

      useFacet = "true".equals(Tool.getChildValueByTagName(tag, "useFacet"));
      showMostRecentDateOnly = "true".equals(
         Tool.getChildValueByTagName(tag, "onlyShowMostRecentDate"));
   }

   @Override
   public DateComparisonInfo clone() {
      try {
         DateComparisonInfo info = (DateComparisonInfo) super.clone();

         if(dcPeriods != null) {
            info.dcPeriods = dcPeriods.clone();
         }

         if(dcInterval != null) {
            info.dcInterval = dcInterval.clone();
         }

         if(colorFrame != null) {
            info.colorFrame = (VisualFrameWrapper) colorFrame.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone DateComparisonInfo", ex);
      }

      return null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof DateComparisonInfo)) {
         return false;
      }

      DateComparisonInfo oInfo = (DateComparisonInfo) obj;

      return Tool.equals(dcInterval, oInfo.dcInterval)
         && Tool.equals(dcPeriods, oInfo.dcPeriods) &&
         Tool.equals(colorFrame, oInfo.colorFrame) &&
         comparisonOption == oInfo.comparisonOption && useFacet == oInfo.useFacet &&
         showMostRecentDateOnly == oInfo.showMostRecentDateOnly;
   }

   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();

      if(dcPeriods != null) {
         list.addAll(dcPeriods.getDynamicValues());
      }

      if(dcInterval != null) {
         list.addAll(dcInterval.getDynamicValues());
      }

      return list;
   }

   public boolean isCompareAll() {
      int level = dcInterval.getLevel();
      return level == ALL;
   }

   /**
    * Get the condition by date comparison periods.
    */
   private ConditionList getPeriodsConditions(DataRef ref) {
      if(dcPeriods == null) {
         return null;
      }

      DateComparisonPeriods periods = dcPeriods;
      ConditionList conditionList = null;

      if(periods instanceof CustomPeriods) {
         List<DatePeriod> datePeriods = ((CustomPeriods) periods).getDatePeriods(true);
         conditionList = new ConditionList();

         if(datePeriods == null) {
            return null;
         }

         for(DatePeriod datePeriod : datePeriods) {
            Date start = datePeriod.getStart();
            Date end = datePeriod.getEnd();

            if(start == null || end == null) {
               continue;
            }

            if(end.compareTo(start) <= 0) {
               continue;
            }

            List<Object> values = new ArrayList<>();
            values.add(start);
            values.add(end);
            AssetCondition condition = new AssetCondition();
            condition.setOperation(XCondition.BETWEEN);
            condition.setValues(values);
            ConditionItem item = new ConditionItem();
            item.setAttribute(ref);
            item.setXCondition(condition);
            conditionList.append(item);
            conditionList.append(new JunctionOperator(JunctionOperator.OR, 0));
         }
      }
      else if(periods instanceof StandardPeriods) {
         conditionList = getStandardPeriodsCondition((StandardPeriods) periods, ref);
      }

      conditionList.trim();

      return conditionList;
   }

   /**
    * Whether the standard period disable the to date.
    *
    * @return
    */
   private boolean toDateDisabled() {
      if(!isStdPeriod()) {
         return true;
      }

      StandardPeriods periods = (StandardPeriods) dcPeriods;

      int periodLevel = periods.getDateLevel();
      int contextLevel = dcInterval.getContextLevel();
      int intervalLevel = DateComparisonUtil.dcIntervalLevelToDateGroupLevel(dcInterval.getLevel());

      return periodLevel == intervalLevel && intervalLevel == contextLevel;
   }

   /**
    * Get the condition by standard periods
    * @param periods StandardPeriods
    * @param ref a date type ref.
    */
   private ConditionList getStandardPeriodsCondition(StandardPeriods periods, DataRef ref) {
      if(periods == null) {
         return null;
      }

      ConditionList conditionList = new ConditionList();
      Calendar calendar = getCalendar();
      calendar.setTime(periods.getRuntimeEndDay());
      resetLowDateLevel(calendar);
      int preCount = periods.getPreCount();
      boolean isQuarter = periods.getDateLevel() == XConstants.QUARTER_DATE_GROUP;
      int calendarLevel = getCalendarLevel(periods.getDateLevel());

      // if showing change, add one more interval so the first period will not be empty.
      if(comparisonOption != VALUE) {
         preCount += 1;
      }

      boolean isWeek = alignWeek();

      if(toDateDisabled() || !periods.isToDate()) {
         Date endDate = setTimeToEndOfDay(calendar.getTime());
         Date startDate = getStartDate(preCount);
         Date periodEndDate = new Date(endDate.getTime());

         // for week-to-date, should include the start of week that covers the beginning
         // of the periods. (55662)
         if(dcInterval.getLevel() == WEEK_TO_DATE) {
            startDate = setDateToLevelStart(startDate, XConstants.WEEK_DATE_GROUP);
         }

         if(!periods.isInclusive()) {
            calendar.setTime(endDate);
            calendar.add(calendarLevel, -(isQuarter ? 3 : 1));
            endDate = setDateToEndOfPeriod(calendar.getTime(), periods.getDateLevel());
         }

         if(isWeek && periods.getDateLevel() != XConstants.WEEK_DATE_GROUP) {
            calendar.setTime(startDate);
            WeekInfo.advanceToFirstDayOfWeek(calendar);
            startDate = calendar.getTime();
            calendar.setTime(endDate);
            WeekInfo.advanceToLastOfWeek(calendar);
            endDate = calendar.getTime();

            if(endDate.after(periodEndDate)) {
               endDate = periodEndDate;
            }
         }

         List<Object> values = new ArrayList<>();
         values.add(startDate);
         values.add(endDate);
         AssetCondition condition = new AssetCondition();
         condition.setOperation(XCondition.BETWEEN);
         condition.setValues(values);
         conditionList.append(new ConditionItem(ref, condition, 0));
      }
      else {
         Date toDate = periods.getRuntimeEndDay();
         WeekInfo weekInfo = isWeek && periods.getDateLevel() != XConstants.WEEK_DATE_GROUP
            ? new WeekInfo(toDate) : null;

         if(weekInfo != null) {
            calendar = weekInfo.getWeekStart();
         }
         else {
            calendar.setTime(toDate);
         }

         for(int i = 0; i <= preCount; i++) {
            if(periods.isInclusive() || i != 0) {
               Date[] dateRange = getPeriodDateRange(periods.getDateLevel(), calendar.getTime(),
                  toDate, weekInfo, i == 0);

               if(dateRange == null || dateRange.length != 2 || dateRange[0] == null ||
                  dateRange[1] == null)
               {
                  continue;
               }

               dateRange[1] = setTimeToEndOfDay(dateRange[1]);
               conditionList.append(createDateRangeCondition(ref, dateRange[0], dateRange[1]));
               conditionList.append(new JunctionOperator(JunctionOperator.OR, 0));
            }

            calendar.add(calendarLevel, -(isQuarter ? 3 : 1));
         }
      }

      return conditionList;
   }

   public Date getStartDate() {
      if(getPeriods() instanceof StandardPeriods) {
         return ((StandardPeriods) getPeriods()).getStartDate();
      }

      return getStartDate(0);
   }

   private Date getStartDate(int preCount) {
      if(getPeriods() instanceof StandardPeriods) {
         StandardPeriods periods = (StandardPeriods) getPeriods();
         boolean isQuarter = periods.getDateLevel() == XConstants.QUARTER_DATE_GROUP;
         int calendarLevel = getCalendarLevel(periods.getDateLevel());
         Calendar calendar = getCalendar();
         calendar.setTime(periods.getRuntimeEndDay());

         resetLowDateLevel(calendar);
         setDateToLevelStart(calendar, periods.getDateLevel());
         calendar.add(calendarLevel, -(isQuarter ? 3 : 1) * preCount);
         return calendar.getTime();
      }

      CustomPeriods periods = (CustomPeriods) getPeriods();
      return periods.getDatePeriods().stream().findFirst().map(p -> p.getStart()).orElse(null);
   }

   private Date setTimeToEndOfDay(Date date) {
      Calendar calendar = getCalendar();
      calendar.setTime(date);
      setTimeToEndOfDay(calendar);

      return calendar.getTime();
   }

   private void setTimeToEndOfDay(Calendar calendar) {
      calendar.set(Calendar.HOUR_OF_DAY, 23);
      calendar.set(Calendar.MINUTE, 59);
      calendar.set(Calendar.SECOND, 59);
      calendar.set(Calendar.MILLISECOND, 999);
   }

   private Date setDateToEndOfPeriod(Date date, int level) {
      if(date == null) {
         return null;
      }

      Calendar calendar = getCalendar();
      calendar.setTime(date);
      setDateToEndOfPeriod(calendar, level);

      return calendar.getTime();
   }

   private void setDateToEndOfPeriod(Calendar calendar, int level) {
      calendar.getTime();

      if(level == XConstants.YEAR_DATE_GROUP) {
         calendar.set(Calendar.MONTH, Calendar.DECEMBER);
         calendar.set(Calendar.DATE, calendar.getActualMaximum(Calendar.DATE));
      }
      else if(level == XConstants.QUARTER_DATE_GROUP) {
         int monthOffsetQuarterEnd = 2 - calendar.get(Calendar.MONTH) % 3;
         calendar.add(Calendar.MONTH, monthOffsetQuarterEnd);
         calendar.set(Calendar.DATE, calendar.getActualMaximum(Calendar.DATE));
      }
      else if(level == XConstants.MONTH_DATE_GROUP) {
         calendar.set(Calendar.DATE, calendar.getActualMaximum(Calendar.DATE));
      }
      else if(level == XConstants.WEEK_DATE_GROUP) {
         calendar.set(Calendar.DAY_OF_WEEK, getLastDayOfWeek());
      }

      setTimeToEndOfDay(calendar);
   }

   private int getLastDayOfWeek() {
      return (Tool.getFirstDayOfWeek() + 7 - 1) % 7;
   }

   private static int getFirstDayOfWeek() {
      return Tool.getFirstDayOfWeek();
   }

   private Date setDateToLevelStart(Date date, int level) {
      if(date == null) {
         return null;
      }

      Calendar calendar = getCalendar();
      calendar.setTime(date);
      setDateToLevelStart(calendar, level);

      return calendar.getTime();
   }

   static void setDateToLevelStart(Calendar calendar, int level) {
      calendar.getTime();

      if(level == XConstants.YEAR_DATE_GROUP) {
         calendar.set(Calendar.MONTH, Calendar.JANUARY);
         calendar.set(Calendar.DATE, 1);
      }
      else if(level == XConstants.QUARTER_DATE_GROUP) {
         int monthOfQuarter = calendar.get(Calendar.MONTH) % 3;
         calendar.add(Calendar.MONTH, -monthOfQuarter);
         calendar.set(Calendar.DATE, 1);
      }
      else if(level == XConstants.MONTH_DATE_GROUP) {
         calendar.set(Calendar.DATE, 1);
      }
      else if(level == XConstants.WEEK_DATE_GROUP) {
         calendar.set(Calendar.DAY_OF_WEEK, getFirstDayOfWeek());
      }

      resetLowDateLevel(calendar);
   }

   static Date resetLowDateLevel(Calendar calendar) {
      calendar.set(Calendar.HOUR_OF_DAY, 0);
      calendar.set(Calendar.MINUTE, 0);
      calendar.set(Calendar.SECOND, 0);
      calendar.set(Calendar.MILLISECOND, 0);

      return calendar.getTime();
   }

   private Date resetLowDateLevel(Date date) {
      Calendar calendar = getCalendar();
      calendar.setTime(date);

      return resetLowDateLevel(calendar);
   }

   private ConditionItem createDateRangeCondition(DataRef ref, Date start, Date end) {
      List<Object> values = new ArrayList<>();
      start = resetLowDateLevel(start);
      values.add(start);
      values.add(end);
      AssetCondition condition = new AssetCondition();
      condition.setOperation(XCondition.BETWEEN);
      condition.setValues(values);

      return new ConditionItem(ref, condition, 0);
   }

   /**
    * Fix the start date to the first day of period and use toDate to create the period end day.
    * @param level period date level.
    * @param startDate a period start date.
    * @param toDate toDate for all periods.
    * @param week true to adjust the end of period to the same day-of-week and week-of-month.
    * @param first true if this is the first period.
    */
   private Date[] getPeriodDateRange(int level, Date startDate, Date toDate,
                                     WeekInfo week, boolean first)
   {
      // align the end of range at the same day of week as the toDate.
      boolean alignEndOfWeek = week != null && !first;
      Calendar startCal = getCalendar();
      startCal.setTime(startDate);
      resetLowDateLevel(startCal);
      Calendar toDateCal = getCalendar();

      if(toDate != null) {
         toDateCal.setTime(toDate);
      }
      else {
         toDateCal.setTime(startDate);
      }

      int dayOfWeek = toDateCal.get(Calendar.DAY_OF_WEEK);
      resetLowDateLevel(toDateCal);

      if(level == XConstants.YEAR_DATE_GROUP) {
         startCal.set(Calendar.MONTH, Calendar.JANUARY);

         if(alignEndOfWeek) {
            startCal.set(Calendar.WEEK_OF_MONTH, 1);
         }
         else {
            startCal.set(Calendar.DATE, 1);
         }

         if(toDate != null) {
            if(!first) {
               toDateCal.set(Calendar.YEAR, startCal.get(Calendar.YEAR));

               // if group/compare weekly data, make sure the periods end at the same day of week.
               if(alignEndOfWeek) {
                  week.setYearRange(startCal, toDateCal);
               }
            }
            else if(week != null) {
               WeekInfo.advanceToFirstDayOfWeek(startCal);
            }
         }
         else {
            toDateCal.set(Calendar.DAY_OF_YEAR, toDateCal.getActualMaximum(Calendar.DAY_OF_YEAR));
         }
      }
      else if(level == XConstants.QUARTER_DATE_GROUP) {
         startCal.add(Calendar.MONTH, -(startCal.get(Calendar.MONTH) % 3));

         if(alignEndOfWeek) {
            startCal.set(Calendar.WEEK_OF_MONTH, 1);
         }
         else {
            startCal.set(Calendar.DATE, 1);
         }

         if(toDate != null) {
            if(!first) {
               int toDateMonth = toDateCal.get(Calendar.MONTH);
               int toDateMonthOfQuarter = toDateMonth % 3;
               int toDateDayOfMonth = toDateCal.get(Calendar.DAY_OF_MONTH);
               toDateCal.setTime(startCal.getTime());

               // if group/compare weekly data, make sure the periods end at the same day of week.
               if(alignEndOfWeek) {
                  week.setQuarterRange(startCal, toDateCal);
               }
               else {
                  toDateCal.add(Calendar.MONTH, toDateMonthOfQuarter);
                  toDateCal.set(Calendar.DAY_OF_MONTH,
                                Math.min(toDateDayOfMonth, toDateCal.getActualMaximum(Calendar.DATE)));
               }
            }
            else if(week != null) {
               WeekInfo.advanceToFirstDayOfWeek(startCal);
            }
         }
         else {
            toDateCal.set(Calendar.MONTH, (toDateCal.get(Calendar.MONTH) / 3) * 3 + 2);
            toDateCal.set(Calendar.DAY_OF_MONTH, toDateCal.getActualMaximum(Calendar.DAY_OF_MONTH));
         }
      }
      else if(level == XConstants.MONTH_DATE_GROUP) {
         if(alignEndOfWeek) {
            startCal.set(Calendar.WEEK_OF_MONTH, 1);
         }
         else {
            startCal.set(Calendar.DATE, 1);
         }

         if(toDate != null) {
            if(!first) {
               int dayOfMonth = toDateCal.get(Calendar.DAY_OF_MONTH);
               toDateCal.setTime(startCal.getTime());

               // if group/compare weekly data, make sure the periods end at the same day of week.
               if(alignEndOfWeek) {
                  week.setMonthRange(startCal, toDateCal);
               }
               else {
                  toDateCal.set(Calendar.DAY_OF_MONTH,
                                Math.min(dayOfMonth, toDateCal.getActualMaximum(Calendar.DATE)));
               }
            }
            else if(week != null) {
               WeekInfo.advanceToFirstDayOfWeek(startCal);
            }
         }
         else {
            toDateCal.set(Calendar.DAY_OF_MONTH, toDateCal.getActualMaximum(Calendar.DAY_OF_MONTH));
         }
      }
      else if(level == XConstants.WEEK_DATE_GROUP) {
         startCal.set(Calendar.DAY_OF_WEEK, getFirstDayOfWeek());

         if(toDate != null) {
            toDateCal.setTime(startCal.getTime());
            toDateCal.set(Calendar.DAY_OF_WEEK, dayOfWeek);
         }
         else {
            toDateCal.set(Calendar.DAY_OF_WEEK, toDateCal.getActualMaximum(Calendar.DAY_OF_WEEK));
         }
      }
      else if(level == XConstants.DAY_DATE_GROUP) {
         if(toDate != null) {
            toDateCal.setTime(startCal.getTime());
         }
      }

      return new Date[]{ startCal.getTime(), toDateCal.getTime()};
   }

   /**
    * Get condition by periods and interval.
    * @param ref date type ref.
    */
   private ConditionList getIntervalConditions(DataRef ref) {
      if(isCompareAll()) {
         return null;
      }

      if(dcPeriods instanceof StandardPeriods) {
         return getStandardPeriodsIntervalCondition(ref);
      }
      else {
         // interval (e.g. month-to-date) is not exposed to custom periods now. (64217)
         //return getCustomPeriodsIntervalCondition(ref);
         return null;
      }
   }

   /**
    * Get condition when periods is custom and interval is not standard.
    * @param ref date type ref.
    */
   private ConditionList getCustomPeriodsIntervalCondition(DataRef ref) {
      if(ref == null) {
         return null;
      }

      CustomPeriods customPeriods = (CustomPeriods) dcPeriods;
      List<DatePeriod> datePeriods = customPeriods.getDatePeriods();

      if(datePeriods == null) {
         return null;
      }

      ConditionList conditionList = new ConditionList();
      int contextLevel = dcInterval.getContextLevel();
      Calendar rangeEndCal = getCalendar();
      Calendar rangeStartCal = getCalendar();

      for(DatePeriod range : datePeriods) {
         Date toDate = getRangeToDate();
         rangeStartCal.setTime(range.getStart());
         rangeEndCal.setTime(range.getEnd());
         setDateToLevelStart(rangeStartCal, contextLevel);
         setDateToLevelStart(rangeEndCal, contextLevel);
         appendRangeToDateConditions(rangeStartCal, rangeEndCal, contextLevel, toDate,
                                     conditionList, ref);
      }

      conditionList.trim();

      return conditionList;
   }

   /**
    * Create the applied toDate conditions from rangeStartCal time to rangeEndCal by context level.
    * @param rangeStartCal start date Calendar.
    * @param rangeEndCal end date Calendar.
    * @param contextLevel applied to date context level.
    * @param toDate to date to applied.
    * @param conditionList condition list.
    * @param ref condition ref.
    */
   private void appendRangeToDateConditions(Calendar rangeStartCal, Calendar rangeEndCal,
                                            int contextLevel, Date toDate,
                                            ConditionList conditionList, DataRef ref)
   {
      int contextCalendarLevel = getCalendarLevel(contextLevel);
      boolean contextLevelIsQuarter = contextLevel ==  XConstants.QUARTER_DATE_GROUP;

      while(rangeStartCal.before(rangeEndCal) || rangeStartCal.equals(rangeEndCal)) {
         ConditionItem item = getConditionItemFromDateRangeAndInterval(ref, contextLevel,
            dcInterval.getLevel(), dcInterval.getGranularity(), rangeEndCal.getTime(), toDate);
         setToNextIntervalStart(rangeEndCal, contextCalendarLevel, contextLevelIsQuarter, false, alignWeek());

         if(item == null) {
            continue;
         }

         conditionList.append(item);
         conditionList.append(new JunctionOperator(JunctionOperator.OR, 0));
      }
   }

   /**
    * Get condition ranges when periods is custom and interval is not standard.
    */
   private List<List<Date[]>> getCustomPeriodsIntervalRanges(CustomPeriods customPeriods) {
      List<List<Date[]>> dateRanges = new ArrayList<>();
      customPeriods = customPeriods != null ? customPeriods : (CustomPeriods) dcPeriods;
      List<DatePeriod> datePeriods = customPeriods.getDatePeriods(true);

      if(datePeriods == null) {
         return null;
      }

      int calendarGranularityLevel = -1;
      boolean quarterCalendar = false;

      if(dcInterval.getGranularity() == YEAR) {
         calendarGranularityLevel = Calendar.YEAR;
      }
      else if(dcInterval.getGranularity() == QUARTER) {
         calendarGranularityLevel = Calendar.MONTH;
         quarterCalendar = true;
      }
      else if(dcInterval.getGranularity() == MONTH) {
         calendarGranularityLevel = Calendar.MONTH;
      }
      else if(dcInterval.getGranularity() == WEEK) {
         calendarGranularityLevel = Calendar.WEEK_OF_YEAR;
      }
      else if(dcInterval.getGranularity() == DAY) {
         calendarGranularityLevel = Calendar.DAY_OF_YEAR;
      }

      Calendar calendar = getCalendar();

      for(DatePeriod datePeriod : datePeriods) {
         List<Date[]> ranges = new ArrayList<>();
         Date start = datePeriod.getStart();
         Date end = datePeriod.getEnd();

         if(start == null || end == null) {
            continue;
         }

         if(end.before(start)) {
            ranges.add(new Date[] { start, end });
         }
         else {
            calendar.setTime(start);

            while(calendar.getTime().before(end) || calendar.getTime().equals(end)) {
               Date[] range = getCustomGranularityDateRange(dcInterval.getGranularity(), calendar.getTime());
               ranges.add(range);
            setToNextIntervalStart(calendar, calendarGranularityLevel, quarterCalendar, true, false);
            }
         }

         dateRanges.add(ranges);
      }

      return dateRanges;
   }

   private void setToNextIntervalStart(Calendar calendar, int calendarLevel,
                                       boolean quarterCalendar, boolean add, boolean fullWeek)
   {
      calendar.add(calendarLevel, (add ? 1 : -1) * (quarterCalendar ? 3 : 1));

      if(calendarLevel == Calendar.YEAR) {
         calendar.set(Calendar.MONTH, Calendar.JANUARY);
         calendar.set(Calendar.DAY_OF_YEAR, 1);
      }
      else if(calendarLevel == Calendar.MONTH && quarterCalendar) {
         calendar.add(Calendar.MONTH, -(calendar.get(Calendar.MONTH) % 3));
         calendar.set(Calendar.DAY_OF_MONTH, 1);
      }
      else if(calendarLevel == Calendar.MONTH) {
         calendar.set(Calendar.DAY_OF_MONTH, 1);

         if(fullWeek) {
            WeekInfo.advanceToFirstDayOfWeek(calendar);
         }
      }
      else if(calendarLevel == Calendar.WEEK_OF_YEAR) {
         calendar.set(Calendar.DAY_OF_WEEK, getFirstDayOfWeek());
      }
   }

   /**
    * Get condition when periods is standard and interval is not standard.
    * @param ref date type ref.
    */
   private ConditionList getStandardPeriodsIntervalCondition(DataRef ref) {
      ConditionList conditionList = new ConditionList();
      StandardPeriods standardPeriods = (StandardPeriods) dcPeriods;
      int preCount = standardPeriods.getPreCount();
      int periodLevel = standardPeriods.getDateLevel();
      Date toDate = getRangeToDate();
      int calendarPeriodLevel = getCalendarLevel(periodLevel);
      boolean quarterCalendar = periodLevel == XConstants.QUARTER_DATE_GROUP;

      if(calendarPeriodLevel == -1) {
         return null;
      }

      Calendar calendar = getCalendar();
      calendar.setMinimalDaysInFirstWeek(7);
      calendar.setTime(standardPeriods.getRuntimeEndDay());

      if(alignWeek()) {
         calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
      }

      int contextCalendarLevel = getCalendarLevel(dcInterval.getContextLevel());

      if(contextCalendarLevel == -1 || contextCalendarLevel < calendarPeriodLevel) {
         return null;
      }

      // if showing change, add one more interval so the first period will not be empty.
      if(comparisonOption != VALUE) {
         preCount += 1;
      }

      Calendar endDateCalendar = getCalendar();
      endDateCalendar.setTime(calendar.getTime());
      setDateToLevelStart(endDateCalendar, periodLevel);
      endDateCalendar.add(calendarPeriodLevel, -(quarterCalendar ? 3 : 1) * preCount);

      if(!standardPeriods.isInclusive()) {
         calendar.add(calendarPeriodLevel, -(quarterCalendar ? 3 : 1));
      }

      setDateToEndOfPeriod(calendar, periodLevel);
      setDateToLevelStart(calendar, dcInterval.getContextLevel());
      appendRangeToDateConditions(endDateCalendar, calendar, dcInterval.getContextLevel(),
                                  toDate, conditionList, ref);
      conditionList.trim();

      return conditionList;
   }

   public boolean alignWeek() {
      int intervalLevel = getInterval().getLevel();
      int granularity = getInterval().getGranularity();
      int contextLevel = getInterval().getContextLevel();

      return getPeriods() instanceof StandardPeriods ?
         (granularity == DateComparisonInfo.WEEK ||
            (intervalLevel & DateComparisonInfo.WEEK) != 0 ||
            contextLevel == XConstants.WEEK_DATE_GROUP) &&
            getPeriodDateLevel() != XConstants.WEEK_DATE_GROUP :
         granularity == DateComparisonInfo.WEEK;
   }


   /**
    * Get interval toDate value.
    */
   private Date getRangeToDate() {
      Date toDate = getIntervalEndDate();

      if(!dcInterval.isInclusive() && (dcInterval.getLevel() & SAME_DATE) != SAME_DATE) {
         Calendar calendar = getCalendar();
         calendar.setTime(toDate);
         calendar.add(Calendar.DAY_OF_YEAR, -1);
         toDate = calendar.getTime();
      }

      toDate = setTimeToEndOfDay(toDate);

      return toDate;
   }

   private Date getIntervalEndDate() {
      if(!dcInterval.isEndDayAsToDate()) {
         return dcInterval.getIntervalEndDate();
      }

      if(dcPeriods instanceof StandardPeriods) {
         return ((StandardPeriods) dcPeriods).getRuntimeEndDay();
      }
      else {
         return ((CustomPeriods) dcPeriods).getDatePeriods().get(0).getEnd();
      }
   }

   static int getCalendarLevel(int groupLevel) {
      switch(groupLevel) {
      case XConstants.YEAR_DATE_GROUP:
         return Calendar.YEAR;
      case XConstants.QUARTER_DATE_GROUP:
      case XConstants.MONTH_DATE_GROUP:
         return Calendar.MONTH;
      case XConstants.WEEK_DATE_GROUP:
         return Calendar.WEEK_OF_YEAR;
      case XConstants.DAY_DATE_GROUP:
         return Calendar.DAY_OF_YEAR;
      }

      return -1;
   }

   private ConditionItem getConditionItemFromDateRangeAndInterval(DataRef ref, int periodLevel,
                                                                  int intervalLevel, int granularityLevel,
                                                                  Date periodStartDate,
                                                                  Date intervalToDate)
   {
      Date[] range = getIntervalDateRange(periodLevel, intervalLevel, granularityLevel, periodStartDate,
         intervalToDate);

      if(range == null || range.length != 2 || range[0] == null || range[1] == null ||
         !alignWeek() && range[0].after(range[1]))
      {
         return null;
      }

      return createDateRangeCondition(ref, range[0], range[1]);
   }

   private Date[] getIntervalDateRange(int periodLevel, int intervalLevel, int granularityLevel, Date periodStartDate,
                                       Date intervalToDate)
   {
      return getIntervalDateRange(periodLevel, intervalLevel, granularityLevel, periodStartDate, intervalToDate,
                                  // week and go across month
                                  (intervalLevel & WEEK) == 0);
   }

   private Date[] getIntervalDateRange(int periodLevel, int intervalLevel, int granularityLevel, Date periodStartDate,
                                       Date intervalToDate, boolean forceRangeStartAfterPeriod)
   {
      Calendar periodStartCal = getCalendar();
      periodStartCal.setMinimalDaysInFirstWeek(7);
      periodStartCal.setTime(periodStartDate);
      Calendar intervalToDateCal = getCalendar();
      intervalToDateCal.setTime(intervalToDate);
      boolean isSameInterval = (intervalLevel & SAME_DATE) == SAME_DATE;
      Date startDate = null;
      Date endDate = null;

      if((intervalLevel & YEAR) == YEAR) {
         periodStartCal.set(Calendar.MONTH, Calendar.JANUARY);
         periodStartCal.set(Calendar.DATE, 1);

         if(granularityLevel == WEEK) {
            WeekInfo.advanceToFirstDayOfWeek(periodStartCal);
         }

         startDate = periodStartCal.getTime();

         if(periodLevel == XConstants.YEAR_DATE_GROUP) {
            int monthOfYear = intervalToDateCal.get(Calendar.MONTH);
            int dayOfMonth = intervalToDateCal.get(Calendar.DAY_OF_MONTH);
            periodStartCal.set(Calendar.MONTH, monthOfYear);

            if(isSameInterval) {
               periodStartCal.set(Calendar.DAY_OF_MONTH,
                  periodStartCal.getActualMaximum(Calendar.DAY_OF_MONTH));
            }
            else {
               periodStartCal.set(Calendar.DAY_OF_MONTH,
                  Math.min(dayOfMonth, periodStartCal.getActualMaximum(Calendar.DAY_OF_MONTH)));
            }

            setTimeToEndOfDay(periodStartCal);
            endDate = periodStartCal.getTime();
         }
      }
      else if((intervalLevel & QUARTER) == QUARTER) {
         if(periodLevel == XConstants.YEAR_DATE_GROUP) {
            int quarterOfYear = intervalToDateCal.get(Calendar.MONTH) / 3;
            periodStartCal.set(Calendar.MONTH, quarterOfYear * 3);
         }
         else if(periodLevel == XConstants.QUARTER_DATE_GROUP) {
            periodStartCal.add(Calendar.MONTH, -periodStartCal.get(Calendar.MONTH) % 3);
         }

         periodStartCal.set(Calendar.DAY_OF_MONTH, 1);

         if(granularityLevel == WEEK) {
            WeekInfo.advanceToFirstDayOfWeek(periodStartCal);
         }

         startDate = periodStartCal.getTime();

         int monthOfQuarter = intervalToDateCal.get(Calendar.MONTH) % 3;
         int dayOfMonth = intervalToDateCal.get(Calendar.DAY_OF_MONTH);

         if(isSameInterval) {
            periodStartCal.add(Calendar.MONTH, 2);
            periodStartCal.set(Calendar.DAY_OF_MONTH,
               periodStartCal.getActualMaximum(Calendar.DAY_OF_MONTH));
         }
         else {
            periodStartCal.add(Calendar.MONTH, monthOfQuarter);
            periodStartCal.set(Calendar.DAY_OF_MONTH,
               Math.min(dayOfMonth, periodStartCal.getActualMaximum(Calendar.DAY_OF_MONTH)));
         }

         setTimeToEndOfDay(periodStartCal);
         endDate = periodStartCal.getTime();
      }
      else if((intervalLevel & MONTH) == MONTH) {
         if(periodLevel == XConstants.YEAR_DATE_GROUP) {
            periodStartCal.set(Calendar.MONTH, intervalToDateCal.get(Calendar.MONTH));
         }
         else if(periodLevel == XConstants.QUARTER_DATE_GROUP) {
            int monthOfQuarter = intervalToDateCal.get(Calendar.MONTH) % 3;
            int periodStartMonth = periodStartCal.get(Calendar.MONTH);
            periodStartCal.set(Calendar.MONTH, periodStartMonth / 3 * 3 + monthOfQuarter);
         }

         periodStartCal.set(Calendar.DAY_OF_MONTH, 1);

         if(granularityLevel == WEEK) {
            WeekInfo.advanceToFirstDayOfWeek(periodStartCal);
         }

         startDate = periodStartCal.getTime();
         int dayOfMonth = intervalToDateCal.get(Calendar.DAY_OF_MONTH);

         if(isSameInterval) {
            periodStartCal.set(Calendar.DAY_OF_MONTH,
               periodStartCal.getActualMaximum(Calendar.DAY_OF_MONTH));
         }
         else {
            periodStartCal.set(Calendar.DAY_OF_MONTH,
               Math.min(dayOfMonth, periodStartCal.getActualMaximum(Calendar.DAY_OF_MONTH)));
         }

         setTimeToEndOfDay(periodStartCal);
         endDate = periodStartCal.getTime();
      }
      else if((intervalLevel & WEEK) == WEEK) {
         int toDateWeekOfMonth = intervalToDateCal.get(Calendar.WEEK_OF_MONTH);
         int dayOfWeek = intervalToDateCal.get(Calendar.DAY_OF_WEEK);
         // use same week-of-month and month-of-year instead of week-of-year. this is
         // same as google analytics and is more comprehensible to human. (63990)
         intervalToDateCal.add(Calendar.DATE, -(intervalToDateCal.get(Calendar.DAY_OF_WEEK) - Tool.getFirstDayOfWeek()));

         int weekOfMonth = intervalToDateCal.get(Calendar.WEEK_OF_MONTH);

         if(periodLevel == XConstants.YEAR_DATE_GROUP) {
            periodStartCal.set(Calendar.MONTH, intervalToDateCal.get(Calendar.MONTH));
            periodStartCal.set(Calendar.WEEK_OF_MONTH, weekOfMonth);
         }
         else if(periodLevel == XConstants.QUARTER_DATE_GROUP) {
            // if the week of quarter doesn't existing this quarter, return null.
            if(!setWeekOfQuarter(intervalToDateCal, periodStartCal)) {
               return null;
            }
         }
         else if(periodLevel == XConstants.MONTH_DATE_GROUP) {
            periodStartCal.set(Calendar.WEEK_OF_MONTH, weekOfMonth);
         }

         periodStartCal.set(Calendar.DAY_OF_WEEK, getFirstDayOfWeek());
         startDate = periodStartCal.getTime();

         if(forceRangeStartAfterPeriod && startDate.before(periodStartDate)) {
            startDate = periodStartDate;
         }

         if(isSameInterval) {
            periodStartCal.set(Calendar.DAY_OF_WEEK, getLastDayOfWeek());
         }
         else {
            periodStartCal.set(Calendar.DAY_OF_WEEK, dayOfWeek);
         }

         setTimeToEndOfDay(periodStartCal);
         endDate = periodStartCal.getTime();
      }
      else if((intervalLevel & DAY) == DAY) {
         if(periodLevel == XConstants.YEAR_DATE_GROUP) {
            int monthOfYear = intervalToDateCal.get(Calendar.MONTH);
            int dayOfMonth = intervalToDateCal.get(Calendar.DAY_OF_MONTH);
            periodStartCal.set(Calendar.MONTH, monthOfYear);
            periodStartCal.set(Calendar.DAY_OF_MONTH,
               Math.min(dayOfMonth, periodStartCal.getActualMaximum(Calendar.DAY_OF_MONTH)));
         }
         else if(periodLevel == XConstants.QUARTER_DATE_GROUP) {
            int toDateMonth = intervalToDateCal.get(Calendar.MONTH);
            int dayOfMonth = intervalToDateCal.get(Calendar.DAY_OF_MONTH);
            int monthOfQuarter = toDateMonth % 3;
            periodStartCal.add(Calendar.MONTH, -periodStartCal.get(Calendar.MONTH) % 3);
            periodStartCal.add(Calendar.MONTH, monthOfQuarter);
            periodStartCal.set(Calendar.DAY_OF_MONTH,
               Math.min(dayOfMonth, periodStartCal.getActualMaximum(Calendar.DAY_OF_MONTH)));
         }
         else if(periodLevel == XConstants.MONTH_DATE_GROUP) {
            int dayOfMonth = intervalToDateCal.get(Calendar.DAY_OF_MONTH);
            periodStartCal.set(Calendar.DAY_OF_MONTH,
               Math.min(dayOfMonth, periodStartCal.getActualMaximum(Calendar.DAY_OF_MONTH)));
         }
         else if(periodLevel == XConstants.WEEK_DATE_GROUP) {
            periodStartCal.set(Calendar.DAY_OF_WEEK, intervalToDateCal.get(Calendar.DAY_OF_WEEK));
         }

         startDate = periodStartCal.getTime();
         setTimeToEndOfDay(periodStartCal);
         endDate = periodStartCal.getTime();
      }

      return new Date[] { forceRangeStartAfterPeriod && periodStartDate.after(startDate) ? periodStartDate : startDate, endDate };
   }


   private Date[] getCustomGranularityDateRange(int granularity, Date periodStartDate) {
      Calendar periodStartCal = getCalendar();
      periodStartCal.setMinimalDaysInFirstWeek(7);
      periodStartCal.setTime(periodStartDate);
      Date startDate = null;
      Date endDate = null;

      if(granularity == YEAR) {
         periodStartCal.set(Calendar.MONTH, Calendar.JANUARY);
         periodStartCal.set(Calendar.DATE, 1);
         startDate = periodStartCal.getTime();
         periodStartCal.set(Calendar.MONTH, Calendar.DECEMBER);
         periodStartCal.set(Calendar.DATE, periodStartCal.getActualMaximum(Calendar.DATE));
         setTimeToEndOfDay(periodStartCal);
         endDate = periodStartCal.getTime();
      }
      else if(granularity == QUARTER) {
         periodStartCal.set(Calendar.DATE, 1);
         periodStartCal.add(Calendar.MONTH, -periodStartCal.get(Calendar.MONTH) % 3);
         periodStartCal.set(Calendar.DAY_OF_MONTH, 1);
         startDate = periodStartCal.getTime();
         periodStartCal.add(Calendar.MONTH, 3);
         setTimeToEndOfDay(periodStartCal);
         endDate = periodStartCal.getTime();
      }
      else if(granularity == MONTH) {
         periodStartCal.set(Calendar.DAY_OF_MONTH, 1);
         startDate = periodStartCal.getTime();
         periodStartCal.set(Calendar.DAY_OF_MONTH, periodStartCal.getActualMaximum(Calendar.DAY_OF_MONTH));
         setTimeToEndOfDay(periodStartCal);
         endDate = periodStartCal.getTime();
      }
      else if(granularity == WEEK) {
         periodStartCal.set(Calendar.DAY_OF_WEEK, periodStartCal.getFirstDayOfWeek());
         startDate = periodStartCal.getTime();
         int lastDayOfWeek = periodStartCal.getFirstDayOfWeek() - 1;

         if(lastDayOfWeek == 0) {
            lastDayOfWeek = 7;
         }

         periodStartCal.set(Calendar.DAY_OF_WEEK, lastDayOfWeek);
         setTimeToEndOfDay(periodStartCal);
         endDate = periodStartCal.getTime();
      }
      else if(granularity == DAY) {
         startDate = periodStartCal.getTime();
         setTimeToEndOfDay(periodStartCal);
         endDate = periodStartCal.getTime();
      }

      return new Date[] { startDate, endDate };
   }

   /**
    * Set the periodStartCol to the date of same as the week of quarter of intervalToDateCal.
    * @return true if successful, or alse if the quarter doesn't have the week of quarter.
    */
   private static boolean setWeekOfQuarter(Calendar intervalToDateCal, Calendar periodStartCal) {
      int weekOfQuarter = DateComparisonUtil.getWeekOfQuarter(intervalToDateCal,
                                                              getFirstDayOfWeek());
      int month = periodStartCal.get(Calendar.MONTH);
      // 1st month of next quarter
      Calendar nextQ = new GregorianCalendar();
      nextQ.setFirstDayOfWeek(getFirstDayOfWeek());
      nextQ.setMinimalDaysInFirstWeek(7);
      nextQ.setTime(periodStartCal.getTime());
      nextQ.set(Calendar.MONTH, month + 3);
      nextQ.set(Calendar.DAY_OF_MONTH, 1);

      int dayOfWeek = nextQ.get(Calendar.DAY_OF_WEEK);

      // find the end of the last week of previous month
      if(dayOfWeek == getFirstDayOfWeek()) {
         nextQ.add(Calendar.DATE, -1);
      }
      else {
         nextQ.add(Calendar.DATE, 7 - dayOfWeek);
      }

      periodStartCal.set(Calendar.WEEK_OF_MONTH, weekOfQuarter);
      return !periodStartCal.after(nextQ);
   }

   private int getCalcValueFrom() {
      if(dcPeriods instanceof CustomPeriods) {
         return ValueOfCalc.PREVIOUS_RANGE;
      }
      else if(dcPeriods instanceof StandardPeriods) {
         int level = ((StandardPeriods) dcPeriods).getDateLevel();

         if((dcInterval.getLevel() & YEAR) == YEAR) {
            if(level == XConstants.YEAR_DATE_GROUP) {
               return ValueOfCalc.PREVIOUS_YEAR;
            }
         }
         else if((dcInterval.getLevel() & QUARTER) == QUARTER) {
            if(level == XConstants.YEAR_DATE_GROUP) {
               return ValueOfCalc.PREVIOUS_YEAR;
            }
            else if(level == XConstants.QUARTER_DATE_GROUP) {
               return ValueOfCalc.PREVIOUS_QUARTER;
            }
         }
         else if((dcInterval.getLevel() & MONTH) == MONTH) {
            if(level == XConstants.YEAR_DATE_GROUP) {
               return ValueOfCalc.PREVIOUS_YEAR;
            }
            else if(level == XConstants.QUARTER_DATE_GROUP) {
               return ValueOfCalc.PREVIOUS_QUARTER;
            }
            else if(level == XConstants.MONTH_DATE_GROUP) {
               return ValueOfCalc.PREVIOUS_MONTH;
            }
         }
         else if((dcInterval.getLevel() & WEEK) == WEEK || dcInterval.getLevel() == ALL) {
            if(level == XConstants.YEAR_DATE_GROUP) {
               return ValueOfCalc.PREVIOUS_YEAR;
            }
            else if(level == XConstants.QUARTER_DATE_GROUP) {
               return ValueOfCalc.PREVIOUS_QUARTER;
            }
            else if(level == XConstants.MONTH_DATE_GROUP) {
               return ValueOfCalc.PREVIOUS_MONTH;
            }
            else if(level == XConstants.WEEK_DATE_GROUP) {
               return ValueOfCalc.PREVIOUS_WEEK;
            }
         }
         else if((dcInterval.getLevel() & DAY) == DAY) {
            if(level == XConstants.YEAR_DATE_GROUP) {
               return ValueOfCalc.PREVIOUS_YEAR;
            }
            else if(level == XConstants.QUARTER_DATE_GROUP) {
               return ValueOfCalc.PREVIOUS_QUARTER;
            }
            else if(level == XConstants.MONTH_DATE_GROUP) {
               return ValueOfCalc.PREVIOUS_MONTH;
            }
         }
      }

      return ValueOfCalc.PREVIOUS;
   }

   static Calendar getCalendar() {
      Calendar calendar = new GregorianCalendar();
      calendar.setFirstDayOfWeek(getFirstDayOfWeek());
      calendar.setMinimalDaysInFirstWeek(7);

      return calendar;
   }

   public String getDescription() {
      if(comparisonOption == 0 || dcPeriods == null || dcInterval == null) {
         return "";
      }

      String show = null;
      Catalog catalog = Catalog.getCatalog();

      switch(comparisonOption) {
      case VALUE:
         show = catalog.getString("Value");
         break;
      case CHANGE:
         show = catalog.getString("Change");
         break;
      case CHANGE_VALUE:
         show = catalog.getString("Value and change");
         break;
      case PERCENT:
         show = catalog.getString("Percent change");
         break;
      case PERCENT_VALUE:
         show = catalog.getString("Value and percent change");
         break;
      }

      StringBuffer buffer = new StringBuffer();
      buffer.append("<b>" + catalog.getString("Date Range") + "</b>: ");
      buffer.append(dcPeriods.getDescription());
      buffer.append("\n");

      if(isStdPeriod()) {
         buffer.append("<b>" + catalog.getString("date.comparison.interval") + "</b>: ");
         buffer.append(dcInterval.getDescription());
         buffer.append("\n");
      }

      buffer.append("<b>" + catalog.getString("Display") + "</b>: ");
      buffer.append(show + " " + dcInterval.getGroupByDescription());

      return buffer.toString();
   }

   public boolean needWeekOfYearAuxiliaryRef() {
      if(!(dcPeriods instanceof StandardPeriods)) {
         return false;
      }

      StandardPeriods periods = (StandardPeriods) dcPeriods;
      int periodLevel = periods.getDateLevel();
      int contextLevel = dcInterval.getContextLevel();
      int intervalLevel = dcInterval.getLevel();
      int granularity = dcInterval.getGranularity();

      if(intervalLevel == ALL) {
         return periodLevel == DateRangeRef.YEAR_DATE_GROUP  && (granularity & WEEK) == WEEK;
      }

      return periodLevel == DateRangeRef.YEAR_DATE_GROUP &&
         contextLevel == DateRangeRef.WEEK_DATE_GROUP ||
         (intervalLevel & YEAR) == YEAR && (granularity & WEEK) == WEEK;
   }

   /**
    * Return fields which are temporarily generated for expand the data as dc required,
    * and this part of temp fields also used to date compare(other temp fields are not used
    * in date compare, just used to expand the data).
    */
   public XDimensionRef[] getTempDateGroupRef(DataVSAssemblyInfo info) {
      if(!(info instanceof DateCompareAbleAssemblyInfo)) {
         return null;
      }

      SourceInfo sinfo = info.getSourceInfo();
      String source = sinfo == null ? null : sinfo.getSource();
      DateCompareAbleAssemblyInfo dinfo = (DateCompareAbleAssemblyInfo) info;

      return getTempDateGroupRef(source, info.getViewsheet(), dinfo.getDateComparisonRef());
   }

   /**
    * Return fields which are temporarily generated for expand the data as dc required,
    * and this part of temp fields also used to date compare(other temp fields are not used
    * in date compare, just used to expand the data).
    *
    * @param source the source of the target assembly.
    * @param vs     the current vs.
    * @param ref    the data compare field.
    */
   public XDimensionRef[] getTempDateGroupRef(String source, Viewsheet vs, VSDataRef ref) {
      List<XDimensionRef> refs = new ArrayList<>();

      if(dcPeriods instanceof StandardPeriods && isDateSeries() && dcInterval.getLevel() != ALL &&
         ref instanceof VSDimensionRef)
      {
         StandardPeriods standardPeriods = (StandardPeriods) dcPeriods;
         int periodLevel = standardPeriods.getDateLevel();
         int contextLevel = dcInterval.getContextLevel();
         int intervalLevel = dcInterval.getLevel();
         int granularity = dcInterval.getGranularity();

         if((intervalLevel & granularity) == granularity &&
            contextLevel == DateComparisonUtil.dcIntervalLevelToDateGroupLevel(granularity))
         {
            return refs.toArray(new XDimensionRef[0]);
         }

         if(periodLevel == XConstants.YEAR_DATE_GROUP) {
            if(contextLevel == XConstants.YEAR_DATE_GROUP ) {
               VSDimensionRef intervalRef = getIntervalTempDateGroupRef(contextLevel, intervalLevel,
                  granularity, source, vs, (VSDimensionRef) ref);

               if(intervalRef != null && !refs.contains(intervalRef)) {
                  refs.add(intervalRef);
               }
            }
            else {
               VSDimensionRef contextRef = (VSDimensionRef) ref.clone();
               refs.add(contextRef);

               if(contextLevel == XConstants.QUARTER_DATE_GROUP) {
                  if((intervalLevel & DateComparisonInfo.WEEK) != 0) {
                     contextRef.setDateLevelValue(DateRangeRef.QUARTER_OF_FULL_WEEK_PART + "");
                  }
                  else {
                     contextRef.setDateLevelValue(XConstants.QUARTER_OF_YEAR_DATE_GROUP + "");
                  }
               }
               else if(contextLevel == XConstants.MONTH_DATE_GROUP) {
                  if((granularity & WEEK) == WEEK || (intervalLevel & DateComparisonInfo.WEEK) != 0) {
                     contextRef.setDateLevelValue(DateRangeRef.MONTH_OF_FULL_WEEK_PART + "");
                  }
                  else {
                     contextRef.setDateLevelValue(XConstants.MONTH_OF_YEAR_DATE_GROUP + "");
                  }
               }
               else if(contextLevel == XConstants.WEEK_DATE_GROUP) {
                  contextRef.setDataRef(createCalcRefForNonSupportPartLevel(
                     XConstants.WEEK_DATE_GROUP, XConstants.YEAR_DATE_GROUP, contextRef, source,
                     vs));
               }

               VSDimensionRef intervalRef = getIntervalTempDateGroupRef(contextLevel, intervalLevel,
                  granularity, source, vs, (VSDimensionRef) ref);

               if(intervalRef != null && !refs.contains(intervalRef)) {
                  refs.add(intervalRef);
               }
            }
         }
         else if(periodLevel == XConstants.QUARTER_DATE_GROUP) {
            if(contextLevel == XConstants.QUARTER_DATE_GROUP) {
               VSDimensionRef intervalRef = getIntervalTempDateGroupRef(contextLevel, intervalLevel,
                  granularity, source, vs, (VSDimensionRef) ref);

               if(intervalRef != null && !refs.contains(intervalRef)) {
                  refs.add(intervalRef);
               }
            }
            else {
               VSDimensionRef contextRef = (VSDimensionRef) ref.clone();
               refs.add(contextRef);

               if(contextLevel == XConstants.MONTH_DATE_GROUP) {
                  contextRef.setDataRef(createCalcRefForNonSupportPartLevel(
                     XConstants.MONTH_DATE_GROUP, XConstants.QUARTER_DATE_GROUP, contextRef, source,
                     vs, alignWeek()));
               }
               else if(contextLevel == XConstants.WEEK_DATE_GROUP) {
                  contextRef.setDataRef(createCalcRefForNonSupportPartLevel(
                     XConstants.WEEK_DATE_GROUP, XConstants.QUARTER_DATE_GROUP, contextRef, source,
                     vs));
               }

               VSDimensionRef intervalRef = getIntervalTempDateGroupRef(contextLevel, intervalLevel,
                  granularity, source, vs, (VSDimensionRef) ref);

               if(intervalRef != null && !refs.contains(intervalRef)) {
                  refs.add(intervalRef);
               }
            }
         }
         else if(periodLevel == XConstants.MONTH_DATE_GROUP) {
            if(contextLevel == XConstants.MONTH_DATE_GROUP) {
               VSDimensionRef intervalRef = getIntervalTempDateGroupRef(contextLevel, intervalLevel,
                  granularity, source, vs, (VSDimensionRef) ref);

               if(intervalRef != null && !refs.contains(intervalRef)) {
                  refs.add(intervalRef);
                  String name = intervalRef.getFullName();

                  if(name.startsWith("WeekOfMonth(") && name.endsWith(")")) {
                     // add raw date to fix MergePartCell with boundary week day
                     // cannot convert to original date issue.
                     VSDimensionRef clone = (VSDimensionRef) ref.clone();
                     clone.setDateLevel(DateRangeRef.NONE);
                     clone.setIgnoreDcTemp(true);
                     refs.add(clone);
                  }
               }
            }
            else if(contextLevel == XConstants.WEEK_DATE_GROUP) {
               VSDimensionRef contextRef = (VSDimensionRef) ref.clone();
               contextRef.setDataRef(createCalcRefForNonSupportPartLevel(
                  XConstants.WEEK_DATE_GROUP, XConstants.MONTH_DATE_GROUP, contextRef,
                  source, vs));
               refs.add(contextRef);
            }
         }
      }

      DateComparisonUtil.updateWeekGroupingLevels(this, refs.toArray(new XDimensionRef[0]), getToDateWeekOfMonth());

      return refs.toArray(new XDimensionRef[0]);
   }

   /**
    * Force week of month for dc to make sure calculate correctly when the month of week is out current month.
    */
   public int getToDateWeekOfMonth() {
      boolean isWeekToDate = (getInterval().getLevel() & DateComparisonInfo.WEEK) != 0 &&
         (getInterval().getContextLevel() == XConstants.YEAR_DATE_GROUP ||
         getInterval().getContextLevel() == XConstants.MONTH_DATE_GROUP);

      if(isWeekToDate) {
         Calendar intervalToDateCal = DateComparisonInfo.getCalendar();
         intervalToDateCal.setTime(getRangeToDate());
         intervalToDateCal.add(Calendar.DATE, -(intervalToDateCal.get(Calendar.DAY_OF_WEEK) - 1));

         return intervalToDateCal.get(Calendar.WEEK_OF_MONTH);
      }

      return -1;
   }

   private VSDimensionRef getIntervalTempDateGroupRef(int contextLevel, int intervalLevel,
                                                       int granularity, String source, Viewsheet vs,
                                                       VSDimensionRef ref)
   {
      if((intervalLevel & granularity) == granularity) {
         return null;
      }

      if(contextLevel == XConstants.YEAR_DATE_GROUP) {
         VSDimensionRef intervalRef = (VSDimensionRef) ref.clone();

         if((intervalLevel & QUARTER) == QUARTER) {
            intervalRef.setDateLevelValue(XConstants.QUARTER_OF_YEAR_DATE_GROUP + "");
            return intervalRef;
         }

         if((intervalLevel & MONTH) == MONTH) {
            intervalRef.setDateLevelValue(XConstants.MONTH_OF_YEAR_DATE_GROUP + "");
            return intervalRef;
         }
         else if((intervalLevel & WEEK) == WEEK) {
            intervalRef.setDataRef(createCalcRefForNonSupportPartLevel(
               XConstants.WEEK_DATE_GROUP, XConstants.YEAR_DATE_GROUP, intervalRef, source,
               vs));
            return intervalRef;
         }
      }
      else if(contextLevel == XConstants.QUARTER_DATE_GROUP) {
         VSDimensionRef intervalRef = ref.clone();

         if((intervalLevel & MONTH) == MONTH) {
            boolean week = DateComparisonInfo.WEEK == granularity;

            intervalRef.setDataRef(createCalcRefForNonSupportPartLevel(
               XConstants.MONTH_DATE_GROUP, XConstants.QUARTER_DATE_GROUP, intervalRef, source,
               vs, week));
            return intervalRef;
         }
         else if((intervalLevel & WEEK) == WEEK) {
            intervalRef.setDataRef(createCalcRefForNonSupportPartLevel(
               XConstants.WEEK_DATE_GROUP, XConstants.QUARTER_DATE_GROUP, intervalRef, source,
               vs));
            return intervalRef;
         }
      }
      else if(contextLevel == XConstants.MONTH_DATE_GROUP) {
         VSDimensionRef intervalRef = ref.clone();

         if((intervalLevel & WEEK) == WEEK) {
            intervalRef.setDataRef(createCalcRefForNonSupportPartLevel(
               XConstants.WEEK_DATE_GROUP, XConstants.MONTH_DATE_GROUP, intervalRef, source,
               vs));

            return intervalRef;
         }
      }

      return null;
   }

   @Override
   public String toString() {
      return "DateComparisonInfo@" + System.identityHashCode(this) + "(" +
         dcPeriods.getDescription() + "," +
         dcInterval.getDescription() + "," + comparisonOption + ")";
   }

   private static class WeekInfo {
      public WeekInfo(Date date) {
         weekStart = getCalendar();
         weekStart.setTime(date);

         dayOfWeek = weekStart.get(Calendar.DAY_OF_WEEK);
         weekStart.set(Calendar.DAY_OF_WEEK, getFirstDayOfWeek());
         weekOfMonth = weekStart.get(Calendar.WEEK_OF_MONTH);
         weekOfQuarter = DateComparisonUtil.getWeekOfQuarter(weekStart, getFirstDayOfWeek());
         month = weekStart.get(Calendar.MONTH);
      }

      public Calendar getWeekStart() {
         return weekStart;
      }

      public void setYearRange(Calendar startCal, Calendar toDateCal) {
         toDateCal.set(Calendar.MONTH, month);
         toDateCal.set(Calendar.WEEK_OF_MONTH, weekOfMonth);
         toDateCal.set(Calendar.DAY_OF_WEEK, dayOfWeek);

         advanceToFirstDayOfWeek(startCal);
      }

      public void setMonthRange(Calendar startCal, Calendar toDateCal) {
         toDateCal.set(Calendar.WEEK_OF_MONTH, weekOfMonth);
         toDateCal.set(Calendar.DAY_OF_WEEK, dayOfWeek);

         // set the date to sunday (1st day of week) if weekly.
         advanceToFirstDayOfWeek(startCal);
      }

      public void setQuarterRange(Calendar startCal, Calendar toDateCal) {
         DateComparisonUtil.setWeekOfQuarter(weekOfQuarter, toDateCal, getFirstDayOfWeek());
         toDateCal.set(Calendar.DAY_OF_WEEK, dayOfWeek);

         // set the date to first day ot week if weekly.
         advanceToFirstDayOfWeek(startCal);
      }

      /**
       * Calculate the first day of the week of the month
       * @param startCal
       */
      public static void advanceToFirstDayOfWeek(Calendar startCal) {
         // set the date to first day ot week if weekly.
         int weekIndex = startCal.get(Calendar.WEEK_OF_MONTH);

         if(weekIndex == 0) {
            startCal.add(Calendar.DATE, 7);
         }

         startCal.set(Calendar.DAY_OF_WEEK, getFirstDayOfWeek());
      }

      public static void advanceToLastOfWeek(Calendar endCal) {
         int endDayOfWeek = endCal.getFirstDayOfWeek() - 1;

         if(endDayOfWeek == 0) {
            endDayOfWeek = 7;
         }

         endCal.set(Calendar.DAY_OF_WEEK, endDayOfWeek);
      }

      private int month;
      private int weekOfMonth;
      private int weekOfQuarter;
      private int dayOfWeek;
      private Calendar weekStart;
   }

   private DateComparisonPeriods dcPeriods;
   private DateComparisonInterval dcInterval = new DateComparisonInterval();
   private int comparisonOption;
   private boolean useFacet;
   private boolean showMostRecentDateOnly = true;
   private VisualFrameWrapper colorFrame = new CategoricalColorFrameWrapper();

   public static final int ALL = 0x0;
   public static final int DAY = 0x1;
   public static final int WEEK = 0x2;
   public static final int MONTH = 0x4;
   public static final int QUARTER = 0x8;
   public static final int YEAR = 0x10;

   public static final int TO_DATE = 0x20;
   public static final int WEEK_TO_DATE = TO_DATE | WEEK;
   public static final int MONTH_TO_DATE = TO_DATE | MONTH;
   public static final int QUARTER_TO_DATE = TO_DATE | QUARTER;
   public static final int YEAR_TO_DATE = TO_DATE | YEAR;

   public static final int SAME_DATE = 0x40;
   public static final int SAME_DAY = SAME_DATE | DAY;
   public static final int SAME_WEEK = SAME_DATE | WEEK;
   public static final int SAME_MONTH = SAME_DATE | MONTH;
   public static final int SAME_QUARTER = SAME_DATE | QUARTER;

   private static final String DC_CALC_FIELD_PREFIX = "DC_CALC_PREFIX";
   private static final Logger LOG = LoggerFactory.getLogger(DateComparisonInfo.class);
}
