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
package inetsoft.report.composition.graph.calc;

import inetsoft.graph.data.*;
import inetsoft.report.composition.graph.Router;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.filter.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DCNamedGroupInfo;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.internal.DateComparisonUtil;
import inetsoft.uql.viewsheet.internal.DatePeriod;
import inetsoft.util.Tool;

import java.util.*;

public class ValueOfColumn extends AbstractColumn {
   public ValueOfColumn() {
      super();
   }

   /**
    * Constructor.
    * @param field the field name which will be created a calculation on.
    * @param header the column header for this calculation column.
    */
   public ValueOfColumn(String field, String header) {
      super(field, header);
   }

   /**
    * Set change type.
    */
   public void setChangeType(int ctype) {
      this.ctype = ctype;
   }

   /**
    * Get change type.
    */
   public int getChangeType() {
      return ctype;
   }

   /**
    * Set change on dimension.
    */
   public void setDim(String dim) {
      this.dim = dim;
   }

   /**
    * Get dimension name that this column is change on.
    */
   public String getDim() {
      return dim;
   }

   /**
    * Calculate the value at the row.
    * @param row the row index of the sorted dataset.
    * @param first true if this is the beginning of a series.
    * @param last true if this is the end of a series.
    */
   @Override
   public Object calculate(DataSet data, int row, boolean first, boolean last) {
      if(isBrushData(data)) {
         data = getBrushData(data);

         if(data == null) {
            return null;
         }

         row = ((SubDataSet2) data).getRow(row);

         if(row < 0) {
            return INVALID;
         }
      }

      Object val = getCalcValue(data, row);

      // invalid point, null
      if(val == INVALID) {
         return INVALID;
      }

      return val;
   }

   /**
    * Calculate the value for the crosstab cell.
    */
   @Override
   public Object calculate(CrossTabFilter.CrosstabDataContext context,
                           CrossTabFilter.PairN tuplePair)
   {
      if(tuplePair == null || context == null) {
         return null;
      }

      Object val = getCalcValue(tuplePair, context);

      // invalid point, null
      if(val == INVALID) {
         return INVALID;
      }

      return val;
   }

   /**
    * Get compare value for current row, if option is FIRST or LAST, value is
    * reuseable, otherwise find it dynamic.
    */
   protected Object getCalcValue(CrossTabFilter.PairN tuplePair,
                                 CrossTabFilter.CrosstabDataContext context) {
      switch(ctype) {
         case ValueOfCalc.FIRST:
         case ValueOfCalc.LAST:
            return getTableStaticCalcValue(context, tuplePair);
         case ValueOfCalc.PREVIOUS:
         case ValueOfCalc.NEXT:
         case ValueOfCalc.PREVIOUS_YEAR:
         case ValueOfCalc.PREVIOUS_QUARTER:
         case ValueOfCalc.PREVIOUS_WEEK:
         case ValueOfCalc.PREVIOUS_MONTH:
         case ValueOfCalc.PREVIOUS_RANGE:
            return getTableDynamicCalcValue(context, tuplePair);
         default:
            return INVALID;
      }
   }

   /**
    * Get the value at previous and next row.
    */
   private Object getTableDynamicCalcValue(CrossTabFilter.CrosstabDataContext context,
                                           CrossTabFilter.PairN tuplePair)
   {
      String ndim = getCurrentDim(context, dim);
      int idx = getDimensionIndex(context, ndim);

      if(idx == -1) {
         return null;
      }

      boolean rowDim = isRowDimension(context, getDim(), ndim);

      if((!isCalculateTotal() || rowDim) && (isTotalRow(context, tuplePair) ||
         isGrandTotalRow(context, tuplePair)))
      {
         CrossFilter.Tuple rowTuple = (CrossFilter.Tuple) tuplePair.getValue1();

         if(!isTotalRow(context, tuplePair) || rowTuple == null || rowTuple.size() <= idx) {
            return INVALID;
         }
      }

      if((!isCalculateTotal() || !rowDim) &&
         (isTotalCol(context, tuplePair) || isGrandTotalCol(context, tuplePair)))
      {
         CrossFilter.Tuple colTuple = (CrossFilter.Tuple) tuplePair.getValue2();

         if(!isTotalCol(context, tuplePair) || colTuple == null || colTuple.size() <= idx) {
            return INVALID;
         }
      }

      int fieldIdx = getColIndex();
      Object rtuple = CrossTabFilterUtil.createTuple((CrossFilter.Tuple) tuplePair.getValue1());
      Object ctuple = CrossTabFilterUtil.createTuple((CrossFilter.Tuple) tuplePair.getValue2());
      Object value = CrossTabFilterUtil.getObject(tuplePair, rowDim, idx);
      Object nvalue = getDynamicValue(context, rowDim ? rtuple : ctuple, ndim, value, rowDim);

      if(nvalue == INVALID || nvalue == Router.INVALID) {
         return INVALID;
      }

      if(rowDim) {
         rtuple = CrossTabFilterUtil.getNewTuple(context, rtuple, nvalue, idx);
      }
      else {
         ctuple = CrossTabFilterUtil.getNewTuple(context, ctuple, nvalue, idx);
      }

      return getValue(context, rtuple, ctuple, fieldIdx, rowDim);
   }

   /**
    * Get the value at fixed row.
    */
   private Object getTableStaticCalcValue(CrossTabFilter.CrosstabDataContext context,
                                          CrossTabFilter.PairN tuplePair) {
      String ndim = getCurrentDim(context, dim);

      return getCalcValue(context, ndim, tuplePair);
   }

   private Object getCalcValue(CrossTabFilter.CrosstabDataContext context, String ndim,
                               CrossTabFilter.PairN tuplePair)
   {
      int idx = getDimensionIndex(context, ndim);
      int size = 1;
      boolean rowDim = isRowDimension(context, getDim(), ndim);

      if(!isCalculateTotal() && (isTotalRow(context, tuplePair) ||
         isGrandTotalRow(context, tuplePair)))
      {
         CrossFilter.Tuple rowTuple = (CrossFilter.Tuple) tuplePair.getValue1();

         if(!isTotalRow(context, tuplePair) || rowTuple == null || rowTuple.size() <= idx) {
            return getValue(context, tuplePair.getValue1(), tuplePair.getValue2(),
               tuplePair.getNum(), rowDim);
         }
      }

      int fieldIdx = getColIndex();
      Object[] result = new Object[size];
      Object rtuple = CrossTabFilterUtil.createTuple((CrossFilter.Tuple) tuplePair.getValue1());
      Object ctuple = CrossTabFilterUtil.createTuple((CrossFilter.Tuple) tuplePair.getValue2());
      Object n_tuple = null;
      Object nvalue;

      nvalue = getStaticValue(context, rowDim ? rtuple : ctuple, ndim, rowDim);

      if(rowDim) {
         boolean isTotalRow = isTotalRow(context, tuplePair);

         if(nvalue == NULL && isCalculateTotal() && isTotalRow) {
            n_tuple = rtuple;
         }
      }
      else {
         if(nvalue == NULL && isCalculateTotal() && isTotalCol(context, tuplePair)) {
            n_tuple = ctuple;
         }
      }

      if(n_tuple == null) {
         if(nvalue == NULL) {
            for(int i = 0; i < result.length; i++) {
               result[i] = NULL;
            }

            return result;
         }
         else {
            if(rowDim) {
               rtuple = CrossTabFilterUtil.getNewTuple(context, rtuple, nvalue, idx);
            }
            else {
               ctuple = CrossTabFilterUtil.getNewTuple(context, ctuple, nvalue, idx);
            }
         }
      }

      Object calcValue = getValue(context, rtuple, ctuple, fieldIdx, rowDim);

      return calcValue == null || calcValue == CalcColumn.INVALID ? NULL : calcValue;
   }

   /**
    * Get compare value for current row, if option is FIRST or LAST, value is
    * reuseable, otherwise find it dynamic.
    */
   protected Object getCalcValue(Object data, int row) {
      switch(ctype) {
         case ValueOfCalc.FIRST:
         case ValueOfCalc.LAST:
            return getStaticCalcValue(data, row);
         case ValueOfCalc.PREVIOUS:
         case ValueOfCalc.NEXT:
         case ValueOfCalc.PREVIOUS_YEAR:
         case ValueOfCalc.PREVIOUS_QUARTER:
         case ValueOfCalc.PREVIOUS_WEEK:
         case ValueOfCalc.PREVIOUS_MONTH:
         case ValueOfCalc.PREVIOUS_RANGE:
            return getDynamicCalcValue(data, row);
         default:
            return INVALID;
      }
   }

   /**
    * Get the value at fixed row.
    */
   private Object getStaticCalcValue(Object data, int row) {
      if(data instanceof DataSet) {
         return getDataSetStaticCalcValue((DataSet) data, row);
      }

      return NULL;
   }

   /**
    * Get the value at fixed row.
    */
   private Object getDataSetStaticCalcValue(DataSet data, int row) {
      Object compareVal = null;
      String ndim = dim == null ? innerDim : dim;

      // no dimension
      if(ndim == null) {
         if(data.getRowCount() > 0) {
            compareVal = data.getData(field,
               ctype == ValueOfCalc.FIRST ? 0 : data.getRowCount() - 1);
         }
      }
      else {
         Router router = getRouter(data, ndim);
         Object val = ctype == ValueOfCalc.FIRST ? router.getFirst() : router.getLast();

         Map<String, Object> cond = createCond(data, ndim, row, val);
         cond.put(ndim, val);

         if(!ndim.equals(innerDim)) {
            data = (data instanceof DataSetFilter) ?
               ((DataSetFilter) data).getRootDataSet() : data;
         }

         data = getSubDataSet(data, cond);

         if(data.getRowCount() > 0) {
            compareVal = data.getData(field,
               ctype == ValueOfCalc.FIRST ? 0 : data.getRowCount() - 1);
         }
      }

      compareVal = compareVal == null ? NULL : compareVal;
      return compareVal;
   }

   /**
    * Get the value at previous and next row.
    */
   private Object getDynamicCalcValue(Object data, int row) {
      if(data instanceof DataSet) {
         return getDataSetDynamicCalcValue((DataSet) data, row);
      }

      return INVALID;
   }

   /**
    * Get the value at previous and next row.
    */
   private Object getDataSetDynamicCalcValue(DataSet data, int row) {
      String ndim = dim == null ? innerDim : dim;

      // no dimension
      if(ndim == null) {
         int r = ctype == ValueOfCalc.PREVIOUS ? row - 1 : row + 1;
         return r >= 0 && r < data.getRowCount() ? data.getData(field, r) : INVALID;
      }
      else {
         Object val = data.getData(ndim, row);
         Object tval = null;

         if(ctype >= ValueOfCalc.PREVIOUS_YEAR) {
            if(ctype != ValueOfCalc.PREVIOUS_RANGE) {
               VSDataSet vsDataSet = getVSDataset(data);

               if(vsDataSet != null && vsDataSet.isOthers(ndim, String.valueOf(val))) {
                  return INVALID;
               }

               tval = getPreviousDate(val);
            }
            else {
               tval = getPreRange(val);
            }
         }
         else {
            Router router = getRouter(data, ndim);
            tval = router.getValue(val, ctype == ValueOfCalc.PREVIOUS ? -1 : 1);
         }

         if(tval == DATE_INVALID || tval == Router.INVALID || tval == Router.NOT_EXIST) {
            return INVALID;
         }

         // the first year in the dataset should not assume the previous year (which is not in
         // the dataset) to be 0 when calculating change.
         if(tval instanceof Date && getMinDate(data).after((Date) tval)) {
            return INVALID;
         }

         Map<String, Object> cond = createCond(data, ndim, row, dcTempGroups);
         cond.put(ndim, tval);

         if(!ndim.equals(innerDim)) {
            data = data instanceof DataSetFilter ? ((DataSetFilter) data).getRootDataSet() : data;
         }

         data = getSubDataSet(data, cond);
         int rcnt = ((AbstractDataSet) data).getRowCountUnprojected();

         if(data.getRowCount() <= 0) {
            return INVALID;
         }

         return data.getData(field, (ctype == ValueOfCalc.PREVIOUS) ? rcnt - 1 : 0);
      }
   }

   // get the minimum date on the date dimention.
   private Date getMinDate(DataSet data) {
      if(minDate == null) {
         minDate = new Date(Long.MAX_VALUE);
         String ndim = dim == null ? innerDim : dim;

         for(int i = 0; i < data.getRowCount(); i++) {
            Object dval = data.getData(ndim, i);

            if(dval instanceof Date && minDate.after((Date) dval)) {
               minDate = (Date) dval;
            }
         }
      }

      return minDate;
   }

   /**
    * Get the missing value.
    * @return default INVALID or a value to use when the value from the (e.g. previous)
    * period is missing.
    */
   protected Object getMissingValue() {
      return INVALID;
   }

   private DataSet getSubDataSet(DataSet data, Map<String, Object> cond) {
      if(subs == null || data != subsRoot) {
         // keep to keep the calc rows in data since it's not going to be re-generated. (55401)
         subs = new DataSetIndex(subsRoot = data, cond.keySet(), true);
      }

      // passing false to not add this SubDataSet to the topDataSet. (55400)
      return subs.createSubDataSet(cond, false);
   }

   private Object getPreRange(Object val) {
      if(ctype != ValueOfCalc.PREVIOUS_RANGE || dcPeriods == null) {
         return Router.INVALID;
      }

      List<String> ranges = DCNamedGroupInfo.getCustomPeriodLabels(dcPeriods);
      int index = ranges.indexOf(val);

      if(index <= 0) {
         return Router.INVALID;
      }

      return ranges.get(index - 1);
   }

   /**
    * Get the interval group of custom date comparison.
    */
   public XDimensionRef getDCRangePeriodDim() {
      if(dateComparisonDims == null) {
         return null;
      }

      return dateComparisonDims.stream()
         .filter(VSDimensionRef.class::isInstance)
         .filter(r -> ((VSDimensionRef) r).isDcRange())
         .findFirst().orElse(null);
   }

   /**
    * Get the vs Data set.
    * @param dataSet special date set.
    * @return
    */
   private VSDataSet getVSDataset(DataSet dataSet) {
      if(dataSet instanceof VSDataSet) {
         return (VSDataSet) dataSet;
      }

      if(dataSet instanceof DataSetFilter) {
         return getVSDataset(((DataSetFilter) dataSet).getDataSet());
      }

      return null;
   }

   /**
    * Get pre week date for quarter period.
    * @param date current start date.
    * @return
    */
   private Date getPreQuarterSameWeek(Date date) {
      long weekIndexOfQuarter = getWeekIndexOfQuarter(date);
      Calendar calendar = jcalendars.get();
      calendar.setTime(date);
      calendar.add(Calendar.MONTH, -3);
      Date firstWeekOfQuarter = DateComparisonUtil.getQuarterFirstWeek(calendar.getTime(),
                                                                       Tool.getFirstDayOfWeek());

      return new Date(firstWeekOfQuarter.getTime() +
                         (weekIndexOfQuarter - 1) * 7 * 24 * 60 * 60 * 1000);
   }

   private long getWeekIndexOfQuarter(Date date) {
      Date firstWeekOfQuarter = DateComparisonUtil.getQuarterFirstWeek(date,
                                                                       Tool.getFirstDayOfWeek());
      long differenceInMillis = date.getTime() - firstWeekOfQuarter.getTime();
      long differenceInWeeks = differenceInMillis / (7 * 24 * 60 * 60 * 1000);

      return 1 + differenceInWeeks;
   }

   private Date getPreMonthSameWeek(Date date) {
      Calendar calendar = jcalendars.get();
      calendar.setTime(date);
      int weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH);
      calendar.set(Calendar.DATE, 1);
      calendar.add(Calendar.MONTH, -1);
      calendar.set(Calendar.WEEK_OF_MONTH, weekOfMonth);
      calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());

      return calendar.getTime();
   }

   /**
    * @param value the target date.
    * @return prevoius year/quarter/week of the target date.
    */
   private Date getPreviousDate(Object value) {
      if(value == null || "".equals(value) || ctype < ValueOfCalc.PREVIOUS_YEAR) {
         return null;
      }

      if(!(value instanceof Date)) {
         throw new RuntimeException(
            "Only date is supported for yearly or quarterly " + "comparison: " + value);
      }

      if(ctype == ValueOfCalc.PREVIOUS_WEEK) {
         return new Date(((Date) value).getTime() - 7 * 24 * 60 * 60000);
      }
      else {
         int months = ctype == ValueOfCalc.PREVIOUS_YEAR ? 12 : 3;

         if(ctype == ValueOfCalc.PREVIOUS_MONTH) {
            months = 1;
         }

         Calendar jcalendar = jcalendars.get();
         int minimalDaysInFirstWeek = jcalendar.getMinimalDaysInFirstWeek();
         jcalendar.setMinimalDaysInFirstWeek(7);

         try {
            if(dateComparisonDims != null && dateComparisonDims.size() > 0) {
               jcalendar.setTime((Date) value);
               XDimensionRef innerDimensionRef = dateComparisonDims.get(dateComparisonDims.size() - 1);
               int innerDateLevel = innerDimensionRef.getDateLevel();

               if(getDCRangePeriodDim() == null && innerDateLevel == XConstants.WEEK_DATE_GROUP) {
                  if(ctype == ValueOfCalc.PREVIOUS_YEAR) {
                     int weekOfMonth = jcalendar.get(Calendar.WEEK_OF_MONTH);
                     //int weekOfYear = firstWeek ? 1 : jcalendar.get(Calendar.WEEK_OF_YEAR);

                     if(firstWeek && jcalendar.get(Calendar.MONTH) == Calendar.DECEMBER) {
                        jcalendar.set(Calendar.WEEK_OF_YEAR, 1);
                        return jcalendar.getTime();
                     }

                     jcalendar.add(Calendar.YEAR, -1);
                     //jcalendar.set(Calendar.WEEK_OF_YEAR, weekOfYear);
                     jcalendar.set(Calendar.WEEK_OF_MONTH, weekOfMonth);

                     return jcalendar.getTime();
                  }
                  else if(ctype == ValueOfCalc.PREVIOUS_QUARTER) {
                     int weekOfYear = jcalendar.get(Calendar.WEEK_OF_YEAR);

                     if(firstWeek) {
                        int month = jcalendar.get(Calendar.MONTH);

                        if(month == Calendar.MARCH) {
                           jcalendar.set(Calendar.MONTH, Calendar.APRIL);
                        }
                        else if(month == Calendar.JUNE) {
                           jcalendar.set(Calendar.MONTH, Calendar.JULY);
                        }
                        else if(month == Calendar.SEPTEMBER) {
                           jcalendar.set(Calendar.MONTH, Calendar.OCTOBER);
                        }
                        else if(month == Calendar.DECEMBER) {
                           jcalendar.add(Calendar.YEAR, 1);
                           jcalendar.set(Calendar.MONTH, Calendar.JANUARY);
                        }
                     }

                     return getPreQuarterSameWeek(jcalendar.getTime());
                  }
                  else if(ctype == ValueOfCalc.PREVIOUS_MONTH) {
                     if(firstWeek && jcalendar.get(Calendar.WEEK_OF_MONTH) ==
                        jcalendar.getActualMaximum(Calendar.WEEK_OF_MONTH))
                     {
                        jcalendar.add(Calendar.MONTH, 1);
                     }

                     return getPreMonthSameWeek(jcalendar.getTime());
                  }
               }
            }
         }
         finally {
            jcalendar.setMinimalDaysInFirstWeek(minimalDaysInFirstWeek);
         }

         jcalendar.setTime((Date) value);
         jcalendar.add(Calendar.MONTH, -months);
         Date preDate = jcalendar.getTime();

         return preDate;
      }
   }

   public void setDcPeriods(List<DatePeriod> dcPeriods) {
      this.dcPeriods = dcPeriods;
   }

   public void setDateComparisonDims(List<XDimensionRef> dateComparisonDims) {
      this.dateComparisonDims = dateComparisonDims;
   }

   public void setFirstWeek(boolean firstWeek) {
      this.firstWeek = firstWeek;
   }

   public List<XDimensionRef> getDcTempGroups() {
      return dcTempGroups;
   }

   public void setDcTempGroups(List<XDimensionRef> dcTempGroups) {
      this.dcTempGroups = dcTempGroups;
   }

   @Override
   public void complete() {
      subs = null;
      subsRoot = null;
   }

   @Override
   public boolean supportSortByValue() {
      return ctype == ValueOfCalc.PREVIOUS_YEAR || ctype == ValueOfCalc.PREVIOUS_QUARTER ||
         ctype == ValueOfCalc.PREVIOUS_MONTH || ctype == ValueOfCalc.PREVIOUS_WEEK ||
         ctype == ValueOfCalc.PREVIOUS_RANGE;
   }

   /**
    * @param context  the crosstab filter.
    * @param tuple   the row tuple or column tuple.
    * @param dim     the column name of the calculator.
    * @param row     true if the dim is crosstab row header, else false.
    * @return    the FIRST or LAST value of the target dim in its group.
    */
   private Object getStaticValue(CrossTabFilter.CrosstabDataContext context, Object tuple,
                                 String dim, boolean row) {
      List values = CrossTabFilterUtil.getValues(context, tuple, dim, row);

      if(values == null || values.size() == 0) {
         return NULL;
      }

      return ctype == ValueOfCalc.FIRST ? values.get(0) : values.get(values.size() - 1);
   }

   private Object getValue(CrossTabFilter.CrosstabDataContext context, Object rtuple,
                           Object ctuple, int fieldIdx, boolean rowDim)
   {
      Object value = CrossTabFilterUtil.getValue(context, rtuple, ctuple, fieldIdx);

      if(value == CalcColumn.INVALID){
         Object clone = Tool.clone(rowDim ? rtuple : ctuple);

         if(clone instanceof CrossFilter.Tuple) {
            Object[] values = ((CrossFilter.Tuple) clone).getRow();

            if(CrossTabFilter.OTHERS.equals(values[values.length - 1]) &&
               context.isOthers())
            {
               clone = new CrossFilter.MergedTuple(values, new ArrayList<>());
            }

            if(rowDim) {
               value = CrossTabFilterUtil.getValue(context, clone, ctuple, fieldIdx);
            }
            else {
               value = CrossTabFilterUtil.getValue(context, rtuple, clone, fieldIdx);
            }
         }
      }

      return value;
   }

   /**
    * @param context  the crosstab filter data context.
    * @param tuple   the row tuple or column tuple.
    * @param dim     the column name of the calculator.
    * @param value   the value of current calculate cell.
    * @param row     true if the dim is crosstab row header, else false.
    *
    * @return        the PREVIOUS or NEXT value of the target value in the dim's group.
    */
   private Object getDynamicValue(CrossTabFilter.CrosstabDataContext context, Object tuple,
                                  String dim, Object value, boolean row)
   {
      List values = CrossTabFilterUtil.getValues(context, tuple, dim, row);

      if(values == null || values.size() == 0) {
         return NULL;
      }

      if(ctype >= ValueOfCalc.PREVIOUS_YEAR) {
         if(context.isOthers() && CrossFilter.OTHERS.equals(value)) {
            return INVALID;
         }

         if(ctype != ValueOfCalc.PREVIOUS_RANGE) {
            return getPreviousDate(value);
         }
         else {
            return getPreRange(value);
         }
      }

      int vindex = values.indexOf(value);
      vindex = ctype == ValueOfCalc.PREVIOUS ? vindex - 1 : vindex + 1;

      if(vindex < 0 || vindex >= values.size()) {
         return INVALID;
      }

      return values.get(vindex);
   }

   private static ThreadLocal<Calendar> jcalendars = new ThreadLocal() {
      @Override
      protected Calendar initialValue() {
         return new GregorianCalendar();
      }
   };

   private int ctype;
   private String dim = null;
   // for custom periods date comparison.
   private List<DatePeriod> dcPeriods;
   // for date comparison, first is period level ref, others is lower level refs.
   private List<XDimensionRef> dateComparisonDims;
   private boolean firstWeek; // for date comparison week level;
   private static final Date DATE_INVALID = new Date();
   private List<XDimensionRef> dcTempGroups;
   private DataSetIndex subs;
   private DataSet subsRoot;
   private Date minDate;
}
