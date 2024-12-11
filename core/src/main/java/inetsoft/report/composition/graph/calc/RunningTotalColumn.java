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
package inetsoft.report.composition.graph.calc;

import inetsoft.graph.data.*;
import inetsoft.report.composition.graph.Router;
import inetsoft.report.filter.*;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;

import java.util.*;

/**
 * RunningTotalColumn is defined by the aggregate formula for
 * calculating the value.
 */
public class RunningTotalColumn extends AbstractColumn {
   /**
    * None reset level.
    */
   public static final int NONE = -1;
   /**
    * Rest at year interval.
    */
   public static final int YEAR = 0;
   /**
    * Reset at quarter interval.
    */
   public static final int QUARTER = 1;
   /**
    * Reset at month interval.
    */
   public static final int MONTH = 2;
   /**
    * Reset at week interval.
    */
   public static final int WEEK = 3;
   /**
    * Reset at day interval.
    */
   public static final int DAY = 4;
   /**
    * Reset at hour interval.
    */
   public static final int HOUR = 5;
   /**
    * Reset at minute interval.
    */
   public static final int MINUTE = 6;
   /**
    * Reset at second interval.
    */
   public static final int SECOND = 7;

   /**
    * Default constructor.
    */
   public RunningTotalColumn() {
      super();
   }

   /**
    * Constructor.
    * @param field the field name which will be created a calculation on.
    * @param header the column header for this calculation column.
    */
   public RunningTotalColumn(String field, String header) {
      super(field, header);
   }

   /**
    * Set formula.
    */
   public void setFormula(Formula formula) {
      this.formula = formula;
   }

   /**
    * Get formula.
    */
   public Formula getFormula() {
      return formula;
   }

   /**
    * Set reset level.
    */
   public void setResetLevel(int rlevel) {
      this.rlevel = rlevel;
   }

   /**
    * Get reset level.
    */
   public int getResetLevel() {
      return rlevel;
   }

   public String getBreakBy() {
      return breakBy;
   }

   public void setBreakBy(String breakBy) {
      this.breakBy = breakBy;
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
            return null;
         }
      }

      Object val = data.getData(field, row);

      if(formula == null) {
         return val;
      }

      Formula formula = (Formula) this.formula.clone();
      formula.reset();

      if(breakBy != null && !breakBy.isEmpty()) {
         DataSet baseData = data instanceof DataSetFilter ?
            ((DataSetFilter) data).getRootDataSet() : data;
         row = data instanceof DataSetFilter ? ((DataSetFilter) data).getRootRow(row) : row;

         Object firstBreakByVal = baseData.getData(breakBy, row);
         long interval = getInterval(data, row);

         for(int i = row; i >= 0; i--) {
            Object breakByVal = baseData.getData(breakBy, i);
            long intervali = getInterval(data, i);

            if(interval != intervali) {
               continue;
            }

            // save year\quarter...
            if(Tool.equals(breakByVal, firstBreakByVal)) {
               formula.addValue(baseData.getData(field, i));
            }
            else {
               break;
            }
         }
      }
      else {
         long interval = getInterval(data, row);
         // get all previous values' condition data
         data = getPreCondData(data, row);
         formula.addValue(val);

         // add all previous value which is in same interval
         for(int i = data.getRowCount() - 1; i >= 0; i--) {
            long intervali = getInterval(data, i);

            // reset value by the interval(year\quarter...)
            if(interval != intervali) {
               break;
            }

            formula.addValue(data.getData(field, i));
         }
      }

      return formula.getResult();
   }

   /**
    * Calculate the value for the crosstab cell.
    */
   @Override
   public Object calculate(CrossTabFilter.CrosstabDataContext context,
                           CrossTabFilter.PairN tuplePair)
   {
      if(formula == null) {
         return null;
      }

      String ndim = getCurrentDim(context, breakBy);
      int dimIdx = getDimensionIndex(context, ndim);

      if(dimIdx == -1) {
         return null;
      }

      boolean rowDimension = isRowDimension(context, breakBy, ndim) ||
         Tool.equals(breakBy, AbstractCalc.ROW_INNER);

      if((!isCalculateTotal() || rowDimension) &&
         (isTotalRow(context, tuplePair) || isGrandTotalRow(context, tuplePair)))
      {
         if(!isTotalRow(context, tuplePair)) {
            return INVALID;
         }
      }

      CrossFilter.Tuple rtuple =
         CrossTabFilterUtil.createTuple((CrossFilter.Tuple) tuplePair.getValue1());
      CrossFilter.Tuple ctuple =
         CrossTabFilterUtil.createTuple((CrossFilter.Tuple) tuplePair.getValue2());
      Object currentValue = context.getValue(tuplePair);

      if(Tool.equals(breakBy, AbstractCalc.ROW_INNER) ||
         Tool.equals(breakBy, AbstractCalc.COLUMN_INNER))
      {
         if(currentValue == null) {
            return INVALID;
         }

         if(Tool.equals(breakBy, AbstractCalc.COLUMN_INNER)) {
            if(!isCalculateTotal() && (isTotalCol(context, tuplePair) ||
               isGrandTotalCol(context, tuplePair)))
            {
               return INVALID;
            }
         }

         CrossFilter.Tuple tuple = rowDimension ? rtuple : ctuple;

         if(tuple.size() - 1 < dimIdx) {
            return currentValue;
         }

         Object val = tuple.getRow()[dimIdx];
         List preVals = getAllPreviousValues(context, tuplePair, ndim, val, rowDimension);
         Formula formula = (Formula) this.formula.clone();
         formula.reset();
         formula.addValue(context.getValue(tuplePair));
         long interval = getInterval(val);

         for(int i = preVals.size() - 1; i >= 0; i--) {
            CrossFilter.Tuple n_rtuple =
               CrossTabFilterUtil.getNewTuple(context, tuple, preVals.get(i), dimIdx);

            int idx = rowDimension ? CrossTabFilterUtil.getRowIndex(context, n_rtuple, colIndex)
               : CrossTabFilterUtil.getColumnIndex(context, n_rtuple, getColIndex());

            if(idx == -1) {
               break;
            }

            long intervali = getInterval(preVals.get(i));

            if(interval != intervali) {
               break;
            }

            formula.addValue(context.getValue(rowDimension ? n_rtuple : rtuple,
               rowDimension ? ctuple : n_rtuple, tuplePair.getNum()));
         }

         return formula.getResult();
      }
      else if(breakBy != null && !breakBy.isEmpty()) {
         CrossFilter.Tuple tuple = rowDimension ? rtuple : ctuple;

         if(tuple.size() - 1 < dimIdx) {
            return currentValue;
         }

         Object breakbyVal = tuple.getRow()[dimIdx];

         Formula formula = (Formula) this.formula.clone();
         formula.reset();
         int tupleIndex = getTupleIndex(context, tuple, rowDimension);

         for(int index = tupleIndex; index >= 0; index--) {
            CrossFilter.Tuple currentTuple = getTupleByIndex(context, index, rowDimension);

            if(currentTuple.size() != tuple.size()) {
               continue;
            }

            if(!Tool.equals(breakbyVal, currentTuple.getRow()[dimIdx])) {
               break;
            }

            formula.addValue(context.getValue(rowDimension ? currentTuple : rtuple,
               rowDimension ? ctuple : currentTuple, tuplePair.getNum()));
         }

         return formula.getResult();
      }

      return null;
   }

   private int getTupleIndex(CrossTabFilter.CrosstabDataContext context, CrossFilter.Tuple tuple,
                             boolean row)
   {
      return row ? context.getRowTupleIndex(tuple) : context.getColTupleIndex(tuple);
   }

   private CrossFilter.Tuple getTupleByIndex(CrossTabFilter.CrosstabDataContext context, int index,
                                             boolean row)
   {
      return row ? context.getRowTupleByIndex(index) : context.getColTupleByIndex(index);
   }

   /**
    * @param context  the crosstab filter data context.
    * @param dim     the column name of the calculator.
    * @param value   the value of current calculate cell.
    * @param row     true if the dim is crosstab row header, else false.
    * @return        the PREVIOUS or NEXT value of the target value in the dim's group.
    */
   private List getAllPreviousValues(CrossTabFilter.CrosstabDataContext context,
                                     CrossTabFilter.PairN tuplePair, String dim, Object value,
                                     boolean row)
   {
      CrossFilter.Tuple tuple =
         (CrossFilter.Tuple) (row ? tuplePair.getValue1(): tuplePair.getValue2());
      List values = CrossTabFilterUtil.getValues(context, tuple, dim, row);

      List list = new ArrayList();

      if(values == null || values.size() == 0) {
         return list;
      }

      int idx = values.indexOf(value);

      if(idx == -1) {
         Object[] tupleValues = tuple.getRow();

         if(tupleValues != null && tupleValues.length > 0) {
            idx = values.indexOf(tupleValues[tupleValues.length - 1]);
         }
      }

      if(idx == -1) {
         return list;
      }

      for(int i = 0; i < idx; i++) {
         list.add(values.get(i));
      }

      return list;
   }

   /**
    * Get condition data that except dimension's values is in previous of
    * current value.
    */
   private DataSet getPreCondData(DataSet data, int row) {
      // no dimension
      if(innerDim == null) {
         return new PartDataSet(data, row);
      }

      Router router = getRouter(data, innerDim);
      Object val = data.getData(innerDim, row);
      // use east
      Map<String, Object> cond = createCond(data, innerDim, row, val);
      // 2001 1st, 2001 2st, 2001 3st
      Object[] pvals = router.getAllPrevious(val);
      cond.put(innerDim, pvals);
      DataSet sub = getSubDataSet(data, cond);

      return sub;
   }

   private DataSet getSubDataSet(DataSet data, Map<String, Object> cond) {
      if(subs == null || data != subsRoot) {
         subs = new DataSetIndex(subsRoot = data, cond.keySet(), true);
      }

      // passing false to not add this SubDataSet to the topDataSet. (55400)
      return subs.createSubDataSet(cond, false);
   }

   @Override
   public void complete() {
      subs = null;
      subsRoot = null;
   }

   /**
    * Convert a Date value to time millis, if the object is null, treat it as
    * last interval, if the object is not a Date object, treat it as invalid
    * interval which is -1, otherwise will calculate the Date object by reset
    * level.
    */
   private long getInterval(DataSet data, int row) {
      if(rlevel == -1 || innerDim == null) {
         return 0;
      }

      Object val = data.getData(innerDim, row);
      return getInterval(val);
   }

   private long getInterval(Object val, boolean appliedNamedGroup) {
      if(appliedNamedGroup) {
         return -1;
      }

      return getInterval(val);
   }

   private long getInterval(Object val) {
      // not a date? treat as invalid interval
      if(!(val instanceof Date)) {
         return -1;
      }

      Date interval = (Date) val;
      Calendar calendar = CoreTool.calendar.get();
      calendar.setTime(interval);
      int year = -1, month = -1, week = -1, day = -1, hour = -1, minute = -1;

      switch(rlevel) {
         case YEAR:
            year = calendar.get(Calendar.YEAR);
            calendar.clear();
            calendar.set(Calendar.YEAR, year);
            break;
         case QUARTER:
            year = calendar.get(Calendar.YEAR);
            month = calendar.get(Calendar.MONTH) + 1;
            calendar.clear();
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, (month - 1) / 3 * 3);
            break;
         case MONTH:
            year = calendar.get(Calendar.YEAR);
            month = calendar.get(Calendar.MONTH) + 1;
            calendar.clear();
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month - 1);
            break;
         case WEEK:
            year = calendar.get(Calendar.YEAR);
            month = calendar.get(Calendar.MONTH) + 1;
            week = calendar.get(Calendar.WEEK_OF_MONTH);
            calendar.clear();
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month - 1);
            calendar.set(Calendar.WEEK_OF_MONTH, week);
            break;
         case DAY:
            year = calendar.get(Calendar.YEAR);
            month = calendar.get(Calendar.MONTH) + 1;
            week = calendar.get(Calendar.WEEK_OF_MONTH);
            day = calendar.get(Calendar.DAY_OF_MONTH);
            calendar.clear();
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month - 1);
            calendar.set(Calendar.WEEK_OF_MONTH, week);
            calendar.set(Calendar.DAY_OF_MONTH, day);
            break;
         case HOUR:
            year = calendar.get(Calendar.YEAR);
            month = calendar.get(Calendar.MONTH) + 1;
            week = calendar.get(Calendar.WEEK_OF_MONTH);
            day = calendar.get(Calendar.DAY_OF_MONTH);
            hour = calendar.get(Calendar.HOUR_OF_DAY);
            calendar.clear();
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month - 1);
            calendar.set(Calendar.WEEK_OF_MONTH, week);
            calendar.set(Calendar.DAY_OF_MONTH, day);
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            break;
         case MINUTE:
            year = calendar.get(Calendar.YEAR);
            month = calendar.get(Calendar.MONTH) + 1;
            week = calendar.get(Calendar.WEEK_OF_MONTH);
            day = calendar.get(Calendar.DAY_OF_MONTH);
            hour = calendar.get(Calendar.HOUR_OF_DAY);
            minute = calendar.get(Calendar.MINUTE);
            calendar.clear();
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month - 1);
            calendar.set(Calendar.WEEK_OF_MONTH, week);
            calendar.set(Calendar.DAY_OF_MONTH, day);
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            break;
         default:
            return -1;
      }

      return calendar.getTimeInMillis();
   }

   private static class PartDataSet extends AbstractDataSetFilter {
      public PartDataSet(DataSet data, int mrow) {
         super(data);
         this.mrow = mrow;
      }

      @Override
      protected int getRowCount0() {
         return mrow;
      }

      private int mrow = 0;
   }

   private Formula formula;
   private int rlevel = -1;
   private String breakBy = null;
   private DataSetIndex subs;
   private DataSet subsRoot;
}
