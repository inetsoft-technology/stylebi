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
package inetsoft.report.internal;

import inetsoft.uql.asset.DateRangeRef;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utilities to fill date gap to suppport timeseries.
 */
public final class TimeSeriesUtil {
   /**
    * Fill the missing time series gap with value.
    *
    * @param timeArr   the time arrays which need to fill time gap.
    * @param group     the group which the filled time should belong.
    * @param dateLevel the date level of the timeseries column.
    *
    * @return the new object array which filled time gap with values.
    */
   public static Object[][] fillTimeGap(Object[] timeArr, Object[] group, int dateLevel,
                                        boolean isRow)
   {
      Object[] datas = getSeriesDate(timeArr, dateLevel, isRow);
      int nrow_count = datas.length;
      int ncol_count = group.length + 1;
      Object[][] result = new Object[nrow_count][ncol_count];

      for(int i = 0; i < datas.length; i++) {
         Object[] row = new Object[ncol_count];
         System.arraycopy(group, 0, row, 0, group.length);
         row[row.length - 1] = datas[i];
         result[i] = row;
      }

      return result;
   }

   public static Object[] getSeriesDate(Object[] dateArr, int dateLevel, boolean isRow) {
      List<Object> dates = new ArrayList<>();
      List<Date> isDate = new ArrayList<>();

      for(Object obj : dateArr) {
         if(obj instanceof Date) {
            isDate.add((Date) obj);
         }
         else {
            dates.add(obj);
         }
      }

      List<?> dateArray = getSeriesDate0(isDate, dateLevel, isRow);
      dates.addAll(dateArray);
      return dates.toArray(new Object[0]);
   }

    /**
    * Get series data
    * @param  dateArr   the time array to fill.
    * @param  dateLevel the date level of the timeseries column.
    * @return           the series time for a group.
    */
   private static List<?> getSeriesDate0(List<?> dateArr, int dateLevel, boolean isRow) {
      if(dateArr.size() < 2) {
         return dateArr;
      }

      dateArr.sort(TimeSeriesUtil::sortDates);
      Object min = dateArr.get(0);
      Object max = dateArr.get(dateArr.size() - 1);

      if(!(min instanceof Date) || !(max instanceof Date)) {
         return dateArr;
      }

      List<Date> dates = new ArrayList<>();
      Date start = new Date(((Date) min).getTime());
      Date end = new Date(((Date) max).getTime());

      if(end.compareTo(start) < 0) {
         return dateArr;
      }

      dates.add(start);

      GregorianCalendar cal = new GregorianCalendar();
      cal.setTime(start);

      // For quarter, calculate date for 3 months.
      boolean isQuarter = dateLevel == DateRangeRef.QUARTER_INTERVAL;

      while(cal.getTime().getTime() < end.getTime()) {
         int type = getCalendarType(dateLevel);
         cal.add(type, isQuarter ? 3 : 1);
         Date date = cal.getTime();
         dates.add(date);

         // limit rows to a 100,000 and columns to 500
         if((isRow && dates.size() >= 100000) || (!isRow && dates.size() >= 500)) {
            String message = Catalog.getCatalog().getString(
               "common.timeSeriesColMaxCount", 500);

            if(isRow) {
               message = Catalog.getCatalog().getString(
                  "common.timeSeriesRowMaxCount", 100000);
            }

            Tool.addUserMessage(message);
            LOG.info(message);

            break;
         }
      }

      if(min instanceof Timestamp && max instanceof Timestamp) {
         dates = dates.stream().map((d) -> {
            if(d == null) {
               return null;
            }
            else {
               return new Timestamp(d.getTime());
            }
         }).collect(Collectors.toList());
      }

      if(min instanceof java.sql.Date && max instanceof java.sql.Date) {
         dates = dates.stream().map((d) -> {
            if(d == null) {
               return null;
            }
            else {
               return new java.sql.Date(d.getTime());
            }
         }).collect(Collectors.toList());
      }

      if(min instanceof java.sql.Time && max instanceof java.sql.Time) {
         dates = dates.stream().map((d) -> {
            if(d == null) {
               return null;
            }
            else {
               return new java.sql.Time(d.getTime());
            }
         }).collect(Collectors.toList());
      }

      return dates;
   }

   private static int sortDates(Object a, Object b) {
      if(a instanceof Date && b instanceof Date) {
         return ((Date) a).compareTo((Date) b);
      }

      return 0;
   }

   /**
    * Get data lavel of Calendar
    * @param dataLevel data Level of DateRangeRef.
    * @return data lavel of Calendar.
    */
   private static int getCalendarType(int dataLevel) {
      int type = Calendar.YEAR;

      switch(dataLevel) {
         case DateRangeRef.YEAR_INTERVAL:
            type = Calendar.YEAR;
            break;
         case DateRangeRef.QUARTER_INTERVAL:
            type = Calendar.MONTH;
            break;
         case DateRangeRef.MONTH_INTERVAL:
            type = Calendar.MONTH;
            break;
         case DateRangeRef.WEEK_INTERVAL:
            type = Calendar.WEEK_OF_YEAR;
            break;
         case DateRangeRef.DAY_INTERVAL:
            type = Calendar.DATE;
            break;
         case DateRangeRef.HOUR_INTERVAL:
            type = Calendar.HOUR_OF_DAY;
            break;
         case DateRangeRef.MINUTE_INTERVAL:
            type = Calendar.MINUTE;
            break;
         case DateRangeRef.SECOND_INTERVAL:
            type = Calendar.SECOND;
            break;
      }

      return type;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(TimeSeriesUtil.class);
}