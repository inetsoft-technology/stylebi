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
package inetsoft.web.binding.handler;

import inetsoft.report.composition.graph.calc.*;
import inetsoft.report.internal.binding.GroupField;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.util.Catalog;
import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.graph.CalculateInfo;
import inetsoft.web.binding.model.graph.calc.RunningTotalCalcInfo;
import inetsoft.web.binding.model.graph.calc.ValueOfCalcInfo;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CalculatorHandler {
   public List<String[]> getResetOptions(DataRef ref) {
      if(ref == null) {
         return null;
      }

      int dimensionDateLevel = getDimensionDateLevel(ref);
      String dtype = ref.getDataType();
      String[][] opts = dtype == XSchema.TIME ?
         resetAtTimes : dtype == XSchema.DATE ? resetAtDates : resetAtDateTimes;
      return getResetOptions(opts, ref, dimensionDateLevel);
   }

   public List<String[]> getResetOptions(String[][] opts, DataRef ref) {
      int dimensionDateLevel = getDimensionDateLevel(ref);
      return getResetOptions(opts, ref, dimensionDateLevel);
   }

   public List<String[]> getResetOptions(String[][] opts, DataRef ref,
                                             int dimensionDateLevel)
   {
      List<String[]> arr = new ArrayList<>();
      int priority = RunningTotalCalc.getDatePriority(dimensionDateLevel);
      String stype = ref == null ? null : ref.getDataType();
      boolean date = XSchema.DATE.equals(stype);
      boolean time = XSchema.TIME.equals(stype);
      int boundary =
         RunningTotalCalc.getDatePriority(RunningTotalColumn.HOUR);

      for(int i = 0; i < opts.length; i++) {
         if(i > 0) {
            int prior = RunningTotalCalc.getDatePriority(Integer.parseInt(opts[i][1]));

            if(prior <= priority || (date && prior <= boundary) ||
               (time && prior > boundary))
            {
               continue;
            }
         }

         arr.add(opts[i]);
      }

      return arr;
   }

   public int getDimensionDateLevel(DataRef ref) {
      if(ref == null || !XSchema.isDateType(ref.getDataType()) || hasNamedGroup(ref)) {
         return XConstants.NONE_DATE_GROUP;
      }

      int level = -1;

      if(ref instanceof XDimensionRef) {
         level = ((XDimensionRef) ref).getDateLevel();
      }
      else if(ref instanceof GroupField) {
         OrderInfo order = ((GroupField) ref).getOrderInfo();
         level = order.getOption();
      }

      level = DateRangeRef.isDateTime(level) ? level : XConstants.NONE_DATE_GROUP;

      return transferLevel(level);
   }

   public int transferLevel(int dateRangeLevel) {
      switch(dateRangeLevel) {
         case DateRangeRef.YEAR_INTERVAL :
            return RunningTotalColumn.YEAR;
         case DateRangeRef.QUARTER_INTERVAL:
            return RunningTotalColumn.QUARTER;
         case DateRangeRef.MONTH_INTERVAL :
            return RunningTotalColumn.MONTH;
         case DateRangeRef.WEEK_INTERVAL :
            return RunningTotalColumn.WEEK;
         case DateRangeRef.DAY_INTERVAL :
            return RunningTotalColumn.DAY;
         case DateRangeRef.HOUR_INTERVAL :
            return RunningTotalColumn.HOUR;
         case DateRangeRef.MINUTE_INTERVAL :
            return RunningTotalColumn.MINUTE;
         case DateRangeRef.SECOND_INTERVAL :
            return RunningTotalColumn.SECOND;
         default :
            return RunningTotalColumn.NONE;
      }
   }

   public boolean supportReset(DataRef ref) {
      if(ref == null) {
         return false;
      }

      int level = -1;
      boolean datetime = false;

      if(ref instanceof XDimensionRef && !hasNamedGroup(ref)) {
         level = ((XDimensionRef) ref).getDateLevel();
         datetime = ((XDimensionRef) ref).isDateTime();
      }
      else if(ref instanceof GroupField && !hasNamedGroup(ref)) {
         OrderInfo order = ((GroupField) ref).getOrderInfo();
         level = order.getOption();
         datetime = isDateTime(ref.getDataType());
      }

      return datetime && (level & XConstants.PART_DATE_GROUP) == 0;
   }

   private boolean hasNamedGroup(DataRef ref) {
      if(ref instanceof XDimensionRef) {
         return ((XDimensionRef) ref).isNamedGroupAvailable();
      }
      else if(ref instanceof GroupField) {
         return ((GroupField) ref).isNamedGroupAvailable();
      }

      return false;
   }

   private boolean isDateTime(String type) {
      return XSchema.DATE.equals(type) || XSchema.TIME_INSTANT.equals(type) ||
         XSchema.TIME.equals(type);
   }

   /**
    * Updates the aggregate column values for aggregates affected by a dLevel change
    */
   public static void updateAggregateColNames(List refs, String oldName,
                                              String newName, int dlevel)
   {
      for(Object ref : refs) {
         if(!(ref instanceof BAggregateRefModel)) {
            continue;
         }

         CalculateInfo calc = ((BAggregateRefModel) ref).getCalculateInfo();

         if(calc instanceof ValueOfCalcInfo) {
            ValueOfCalcInfo cInfo = (ValueOfCalcInfo) calc;

            if(Objects.equals(cInfo.getColumnName(), oldName)) {
               cInfo.setColumnName(newName);
            }

            final int from = cInfo.getFrom();

            if(isInvalidFrom(dlevel, from)) {
               cInfo.setFrom(ValueOfCalc.PREVIOUS);
            }
         }

         if(calc instanceof RunningTotalCalcInfo){
            RunningTotalCalcInfo cInfo = (RunningTotalCalcInfo) calc;

            if(Objects.equals(cInfo.getBreakBy(), oldName)) {
               cInfo.setBreakBy(newName);
            }
         }
      }
   }

   /**
    * @param dlevel the new dlevel of the updated dimension.
    * @param from   the change from option.
    */
   private static boolean isInvalidFrom(int dlevel, int from) {
      if(!(from == ValueOfCalc.PREVIOUS_YEAR || from == ValueOfCalc.PREVIOUS_QUARTER ||
         from == ValueOfCalc.PREVIOUS_WEEK))
      {
         return false;
      }

      if(dlevel == -1 || dlevel == DateRangeRef.NONE ||
         dlevel == DateRangeRef.YEAR_DATE_GROUP ||
         dlevel == DateRangeRef.WEEK_DATE_GROUP)
      {
         return true;
      }

      return dlevel == DateRangeRef.QUARTER_DATE_GROUP && from != ValueOfCalc.PREVIOUS_YEAR ||
         dlevel == DateRangeRef.MONTH_DATE_GROUP &&
            (from != ValueOfCalc.PREVIOUS_YEAR || from != ValueOfCalc.PREVIOUS_QUARTER) ||
         dlevel >= DateRangeRef.DAY_DATE_GROUP &&
            (from != ValueOfCalc.PREVIOUS_YEAR || from != ValueOfCalc.PREVIOUS_WEEK);
   }

   public static String[][] resetAtTimes = new String[][] {
      {Catalog.getCatalog().getString("None"), RunningTotalColumn.NONE + ""},
      {Catalog.getCatalog().getString("Hour"), RunningTotalColumn.HOUR + ""},
      {Catalog.getCatalog().getString("Minute"), RunningTotalColumn.MINUTE + ""}
   };

   public static String[][] resetAtDates = new String[][] {
      {Catalog.getCatalog().getString("None"), RunningTotalColumn.NONE + ""},
      {Catalog.getCatalog().getString("Year"), RunningTotalColumn.YEAR + ""},
      {Catalog.getCatalog().getString("Quarter"), RunningTotalColumn.QUARTER + ""},
      {Catalog.getCatalog().getString("Month"), RunningTotalColumn.MONTH + ""},
      {Catalog.getCatalog().getString("Week"), RunningTotalColumn.WEEK + ""},
      {Catalog.getCatalog().getString("Day"), RunningTotalColumn.DAY + ""}
   };

   public static String[][] resetAtDateTimes = new String[][] {
      {Catalog.getCatalog().getString("None"), RunningTotalColumn.NONE + ""},
      {Catalog.getCatalog().getString("Year"), RunningTotalColumn.YEAR + ""},
      {Catalog.getCatalog().getString("Quarter"), RunningTotalColumn.QUARTER + ""},
      {Catalog.getCatalog().getString("Month"), RunningTotalColumn.MONTH + ""},
      {Catalog.getCatalog().getString("Week"), RunningTotalColumn.WEEK + ""},
      {Catalog.getCatalog().getString("Day"), RunningTotalColumn.DAY + ""},
      {Catalog.getCatalog().getString("Hour"), RunningTotalColumn.HOUR + ""},
      {Catalog.getCatalog().getString("Minute"), RunningTotalColumn.MINUTE + ""}
   };
   // for chart
   public static final String INNER_DIMENSION = "";
   // for crosstab
   public static final String VALUE_OF_TAG = "valueof";
   public static final String BREAK_BY_TAG = "breakby";
   public static final String MOVING_TAG = "moving";
   public static final String PERCENT_LEVEL_TAG = "percent_level";
   public static final String PERCENT_DIMS_TAG = "percent_dims";
}