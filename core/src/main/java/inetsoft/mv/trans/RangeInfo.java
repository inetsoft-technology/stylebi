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
package inetsoft.mv.trans;

import inetsoft.mv.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;

/**
 * Range selection info.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public final class RangeInfo {
   /**
    * A numeric range selection.
    */
   public static final int NUMBER = TimeInfo.NUMBER;
   /**
    * A year range selection.
    */
   public static final int YEAR = TimeInfo.YEAR;
   /**
    * A month range selection.
    */
   public static final int MONTH = TimeInfo.MONTH;
   /**
    * A week range selection.
    */
   public static final int WEEK = 5;
   /**
    * A day range selection.
    */
   public static final int DAY = TimeInfo.DAY;

   /**
    * Get the date range option from the specified range type, -1 if not found.
    */
   public static int getDateRangeOption(int rtype) {
      int idx = indexOfDateRange(rtype);
      return idx < 0 ? -1 : DATE_TYPES[1][idx];
   }

   /**
    * Extract range definition from a selection assembly.
    */
   public static RangeInfo getRangeInfo(SelectionVSAssembly sassembly) {
      RangeInfo range = null;

      if(sassembly instanceof TimeSliderVSAssembly) {
         TimeSliderVSAssembly tass = (TimeSliderVSAssembly) sassembly;
         TimeInfo tinfo = tass.getTimeInfo();

         if(tinfo instanceof SingleTimeInfo) {
            SingleTimeInfo sinfo = (SingleTimeInfo) tinfo;
            boolean log = ((TimeSliderVSAssembly) sassembly).isLogScale();
            range = new RangeInfo(sinfo, log);
         }
      }
      else if(sassembly instanceof CalendarVSAssembly) {
         range = new RangeInfo((CalendarVSAssembly) sassembly);
      }

      return range;
   }

   /**
    * Rename column in condition list.
    */
   public static void renameColumn(ConditionListWrapper wrapper, DataRef from,
                                   DataRef to) {
      if(wrapper == null) {
         return;
      }

      ConditionList conds = wrapper.getConditionList();

      for(int i = 0; i < conds.getSize(); i += 2) {
         ConditionItem citem = conds.getConditionItem(i);

         if(!(citem.getXCondition() instanceof Condition)) {
            continue;
         }

         // rename column in condition values
         Condition cond = citem.getCondition();
         int op = cond.getOperation();

         if(op == XCondition.ONE_OF || op == XCondition.EQUAL_TO) {
            continue;
         }

         if(from.equals(citem.getAttribute())) {
            citem.setAttribute(to);
         }

         int count = cond.getValueCount();

         for(int j = 0; j < count; j++) {
            Object val = cond.getValue(j);

            if(from.equals(val)) {
               cond.setValue(j, to);
            }
         }
      }
   }

   /**
    * Get the index of the specified range type if it's for date.
    */
   private static int indexOfDateRange(int rtype) {
      for(int i = 0; i < DATE_TYPES[0].length; i++) {
         if(DATE_TYPES[0][i] == rtype) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Convert number value to logic number matching the range grouping
    * expression.
    */
   public void valueToLogic(ConditionListWrapper conds, DataRef col,
                            int rangeType, MVColumn mvcol) {
      if(conds == null) {
         return;
      }

      if(!(mvcol instanceof XDynamicMVColumn)) {
         LOG.warn(
            "Failed to convert numeric value to logical value: " + conds);
         return;
      }

      for(int i = 0; i < conds.getConditionSize(); i += 2)  {
         ConditionItem item = conds.getConditionItem(i);
         
         if(!(item.getXCondition() instanceof Condition)) {
            continue;
         }

         if(col.getAttribute().equals(item.getAttribute().getAttribute())) {
            Condition cond = (Condition) item.getXCondition();
            int op = cond.getOperation();

            if(op == XCondition.ONE_OF || op == XCondition.EQUAL_TO) {
               continue;
            }

            Object val = cond.getValue(0);

            if(val instanceof Date) {
               cond.removeAllValues();
               cond.addValue(toRangeValue((Date) val, rangeType));
            }
            else if(val instanceof Number) {
               cond.removeAllValues();
               boolean upperInclusive =
                  ((RangeMVColumn) mvcol).isUpperInclusive(val);
               cond.addValue(((RangeMVColumn) mvcol).convert(val));

               if(!upperInclusive && !cond.isEqual() &&
                  (op == XCondition.LESS_THAN || op == XCondition.GREATER_THAN))
               {
                  cond.setEqual(true);
               }
            }
         }
      }
   }

   /**
    * Convert a date to a range value.
    */
   private Object toRangeValue(Date dval, int rtype) {
      int idx = indexOfDateRange(rtype);

      if(idx < 0) {
         throw new RuntimeException("Unsupported date range type: " + rtype);
      }

      return DateRangeRef.getData(DATE_TYPES[1][idx], dval);
   }

   /**
    * Capture the single column range selection from a range slider.
    */
   private RangeInfo(SingleTimeInfo tinfo, boolean log) {
      super();
      this.rtype = tinfo.getRangeType();
      this.log = log;
      this.rsize = tinfo.getRangeSize();
   }

   /**
    * Calendar always creates day ranges.
    */
   private RangeInfo(CalendarVSAssembly calendar) {
      super();

      if(calendar.isYearView()) {
         rtype = MONTH;
      }
      else {
      	 int dtype = calendar.getDateType();

      	 if(dtype == Calendar.MONTH) {
      	    rtype = MONTH;
      	 }
      	 else if(dtype == Calendar.WEEK_OF_MONTH) {
      	    rtype = WEEK;
      	 }
      	 else if(dtype == Calendar.DAY_OF_MONTH) {
      	    rtype = DAY;
      	 }
      	 else {
            if(calendar.isDaySelection()) {
               rtype = DAY;
            }
            else {
               rtype = WEEK;
            }
         }
      }

      rsize = 1;
   }

   /**
    * Return the range selection type.
    */
   public int getRangeType() {
      return rtype;
   }

   /**
    * Get the range size. The range size is the smallest unit of selection.
    */
   public double getRangeSize() {
      return rsize;
   }

   /**
    * Check if is log scale.
    */
   public boolean isLogScale() {
      return log;
   }

   /**
    * Get a string description.
    */
   public String toString() {
      StringBuilder buf = new StringBuilder();
      String tstr = "Unknown";

      switch(rtype) {
      case NUMBER:
         tstr = "Number";
         break;
      case YEAR:
         tstr = "Year";
         break;
      case MONTH:
         tstr = "Month";
         break;
      case WEEK:
         tstr = "Week";
         break;
      case DAY:
         tstr = "Day";
         break;
      }

      buf.append(tstr + ":" + rsize + " ");
      return "RangeInfo[" + buf + "]";
   }

   private static final int[][] DATE_TYPES = new int[][] {
      {YEAR, MONTH, WEEK, DAY},
      {DateRangeRef.YEAR_INTERVAL, DateRangeRef.MONTH_INTERVAL,
       DateRangeRef.WEEK_INTERVAL,
       DateRangeRef.DAY_INTERVAL}
   };

   private static final Logger LOG =
      LoggerFactory.getLogger(RangeInfo.class);
   private int rtype;
   private double rsize;
   private boolean log;
}
