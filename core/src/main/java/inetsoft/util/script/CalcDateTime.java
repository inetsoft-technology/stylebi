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
package inetsoft.util.script;

import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import org.mozilla.javascript.Undefined;

import java.text.DateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Implementation of all Date and Time functions for JavaScript
 *
 * @version 8.0, 6/27/2005
 * @author InetSoft Technology Corp
 */
public class CalcDateTime {
   /**
    * Year date group.
    */
   public static final int YEAR_DATE_GROUP = 0;
   /**
    * Quarter date group.
    */
   public static final int QUARTER_DATE_GROUP = 1;
   /**
    * Month date group.
    */
   public static final int MONTH_DATE_GROUP = 2;
   /**
    * Week date group.
    */
   public static final int WEEK_DATE_GROUP = 3;
   /**
    * Day date group.
    */
   public static final int DAY_DATE_GROUP = 4;
   /**
    * Hour date group.
    */
   public static final int HOUR_DATE_GROUP = 5;
   /**
    * Minute date group.
    */
   public static final int MINUTE_DATE_GROUP = 6;
   /**
    * Second date group.
    */
   public static final int SECOND_DATE_GROUP = 7;
   /**
    * Millisecond date group.
    */
   public static final int MILLISECOND_DATE_GROUP = 8;

   /**
    * Get the serial number of the date object
    * @param date Date object for which the serial number is desired
    * @return serial number (January 1, 1900 has serial number 1)
    */
   public static int datevalue(Object date) {
      date = unwrapDate(date);

      if(date == null) {
         return -1;
      }

      // Addition of one accounts for January 1, 1900 as it is not considered
      // when the number of days between two dates is calculated
      // @by stephenwebster, For bug1429715993235
      // Removed the plus one, as the getSerialStart() day is
      // now set 1 less day so that getSerialDays threshold properly considers
      // the exact moment, time (00:00:00), when the end date is crossed.
      
      // Using "toInstant().atZone" to convert one date to localDate works well for date after1900.
      // But for date before 1900, it will calculate different offset for different time zones.
      // In nomal case, date wil not start before 1900, there is only one date before it is the 
      // getSerialStart(), so for the getSerialStart, we create local date from calendar to avoid
      // convert it to wrong data. Using origianl to create local date.
      Calendar cal = CalcUtil.getSerialStart();
      LocalDate date1 = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
              cal.get(Calendar.DAY_OF_MONTH));

      // java.sql.Date doesn't support toInstant()
      if(date instanceof java.sql.Date) {
         date = new java.util.Date(((java.sql.Date) date).getTime());
      }

      LocalDate date2 = ((Date) date).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

      return (int) ChronoUnit.DAYS.between(date1, date2);
   }

   /**
    * Get the day of the date, integer between 1 and 31
    * @param date input date object
    * @return day of the month 1-31
    */
   public static int day(Object date) {
      date = unwrapDate(date);

      if(date == null) {
         return -1;
      }

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) date);

      return cal.get(Calendar.DAY_OF_MONTH);
   }

   /**
    * Get the day of year. The first day of the year is 1.
    * @param date input date object
    * @return day of the year, 1-365.
    */
   public static int dayofyear(Object date) {
      date = unwrapDate(date);

      if(date == null) {
         return -1;
      }

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) date);

      return cal.get(Calendar.DAY_OF_YEAR);
   }

   /**
    * Get the number of days in a 30 days per month / 360 days per year cycle
    * @param start_date Start Date
    * @param end_date End Date
    * @param method false/null indicates U.S. (NASD) method, true indiates
    * European method
    * @return -1 if start date is after the end date else
    * number of 30/360 days between the start and end day
    */
   public static int days360(Object start_date, Object end_date, Object method) {
      start_date = unwrapDate(start_date);
      end_date = unwrapDate(end_date);

      if(start_date == null || end_date == null) {
         return -1;
      }

      method = JavaScriptEngine.unwrap(method);

      Calendar start = CoreTool.calendar.get();
      start.setTime((Date) start_date);

      Calendar end = CoreTool.calendar2.get();
      end.setTime((Date) end_date);

      //If the start date is after end date then return -1
      if(end.getTime().before(start.getTime())) {
         return -1;
      }

      //European method.
      if(method != null && (Boolean) method) {
         CalcUtil.convertToUsNasd(start, end);
      }
      //U.S. (NASD) method
      else {
         CalcUtil.convertToEuropean(start, end);
      }

      return CalcUtil.get30_360Days(start, end);
   }

   /**
    * Get the date a specified number of months before or after the date
    * @param date input date
    * @param months number of months (-ve value signifies a date before the input
    * date.
    * @return a date object representing the date specified number of months
    * before or after the input date
    */
   public static Object edate(Object date, int months) {
      date = unwrapDate(date);

      if(date == null) {
         return null;
      }

      Date orig = (Date) ((Date) date).clone();
      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) date);
      int st_month = cal.get(Calendar.MONTH);

      cal.set(Calendar.MONTH, cal.get(Calendar.MONTH) + months);

      // @by amitm, bug1130863074712
      // Jan 31, 2000 + 1 using Calendar gives March 2, 2000
      // Hence if the difference in the months > the number of months
      // specified, we have to find the end of the nth month post current
      // month.
      if(cal.get(Calendar.MONTH) - st_month > months) {
         Calendar calOg = CoreTool.calendar2.get();
         calOg.setTime(orig);
         calOg.set(Calendar.DATE, 1);
         return eomonth(calOg.getTime(), months);
      }

      return cal.getTime();
   }

   /**
    * Get the last day of the month a specified number of months
    * before or after the date
    * @param date input date
    * @param months number of months (-ve value signifies a date before the input
    * date.
    * @return a date object representing the last day of the month
    * specified number of months before or after the input date
    */
   public static Object eomonth(Object date, int months) {
      date = unwrapDate(date);

      if(date == null) {
         return null;
      }

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) date);

      // @by yanie: fix #515, set the day to 1, otherwise, if the end day of
      // original month is larger than the end day of the aim month,
      // the function result is wrong. such as eomonth(2015-7-31, -1).
      cal.set(Calendar.DATE, 1);
      cal.set(Calendar.MONTH, cal.get(Calendar.MONTH) + months);

      int date_val = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
      cal.set(Calendar.DATE, date_val);
      return cal.getTime();
   }

   /**
    * Get the hour from the date value
    * @param date input date
    * @return hour (0-23)
    */
   public static int hour(Object date) {
      date = unwrapDate(date);

      if(date == null) {
         return -1;
      }

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) date);

      return cal.get(Calendar.HOUR_OF_DAY);
   }

   /**
    * Get the minute from the date value
    * @param date input date
    * @return minute (0-59)
    */
   public static int minute(Object date) {
      date = unwrapDate(date);

      if(date == null) {
         return -1;
      }

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) date);

      return cal.get(Calendar.MINUTE);
   }

   /**
    * Get the month from the date value
    * @param date input date
    * @return month 1 (January) - 12 (December)
    */
   public static int month(Object date) {
      date = unwrapDate(date);

      if(date == null) {
         return -1;
      }

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) date);

      //JavaScript follows a 1 - 12 month unlike 0 - 11 in Java
      return cal.get(Calendar.MONTH) + 1;
   }

   /**
    * Get the quarter from the date value
    * @param date input date
    * @return quarter number, 1, 2, 3, 4.
    */
   public static int quarter(Object date) {
      return (month(date) - 1) / 3 + 1;
   }

   /**
    * Get the number of whole working days between dates
    * Exclude WEEKENDS and any optional holidays specified
    * @param start_date start date
    * @param end_date end date
    * @param holidaysObj optional list of holidays to be excluded
    * @return Number of working days between dates
    */
   public static int networkdays(Object start_date, Object end_date,
                                 Object holidaysObj) {
      Object[] holidays = JavaScriptEngine.split(holidaysObj);
      start_date = unwrapDate(start_date);
      end_date = unwrapDate(end_date);

      if(start_date == null || end_date == null) {
         return -1;
      }

      if(holidays != null) {
         holidays = JavaScriptEngine.split(holidays);
      }
      else {
         holidays = new Object[0];
      }

      Calendar start = CoreTool.calendar.get();
      start.setTime((Date) start_date);

      Calendar end = CoreTool.calendar2.get();
      end.setTime((Date) end_date);

      boolean reverse = false;

      //If the start date is after end date then return -ve working days
      if(end.getTime().before(start.getTime())) {
         reverse = true;
         Calendar tmp = end;
         end = start;
         start = tmp;
      }

      int working_days = 0;
      Calendar cal = Calendar.getInstance();

      while(true) {
         int day = start.get(Calendar.DAY_OF_WEEK);
         boolean holiday = false;

         for(Object holidayObj : holidays) {
            Date holidayDate = (Date) unwrapDate(holidayObj);
            cal.setTime(holidayDate);

            //Is the day in consideration a holiday
            if((start.get(Calendar.YEAR) == cal.get(Calendar.YEAR)) &&
               (start.get(Calendar.MONTH) == cal.get(Calendar.MONTH)) &&
               (start.get(Calendar.DATE) == cal.get(Calendar.DATE))) {
               holiday = true;
            }
         }

         //Saturday/Sunday/Holidays are omitted from the count
         if(day != Calendar.SATURDAY && day != Calendar.SUNDAY && !holiday) {
            working_days++;
         }

         if((start.get(Calendar.YEAR) == end.get(Calendar.YEAR)) &&
            (start.get(Calendar.MONTH) == end.get(Calendar.MONTH)) &&
            (start.get(Calendar.DATE) == end.get(Calendar.DATE))) {
            break;
         }

         start.set(Calendar.DATE, start.get(Calendar.DATE) + 1);
      }

      if(reverse) {
         return (-1 * working_days);
      }

      return working_days;
   }

   /**
    * Get the current date time
    * @return current date time
    */
   public static Object now() {
      return new Date();
   }

   /**
    * Get the seconds from the date value
    * @param date input date
    * @return seconds 0-59
    */
   public static int second(Object date) {
      date = unwrapDate(date);

      if(date == null) {
         return -1;
      }

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) date);

      return cal.get(Calendar.SECOND);
   }

   /**
    * Get decimal number for a particular time
    * @param hour Hour
    * @param minute Minute
    * @param second Second
    * @return fraction of the time TIME is a value ranging from
    * 0 (zero) to 0.99999999, representing the times from
    * 0:00:00 (12:00:00 AM) to 23:59:59 (11:59:59 P.M.).
    */
   public static double time(int hour, int minute, int second) {
      hour = hour % 24;
      minute = minute % 60;
      second = second % 60;

      Calendar start = CoreTool.calendar.get();
      start.clear();
      start.set(Calendar.HOUR, 0);
      start.set(Calendar.MINUTE, 0);
      start.set(Calendar.SECOND, 0);

      Calendar cal = CoreTool.calendar2.get();
      cal.clear();
      cal.set(Calendar.HOUR, hour);
      cal.set(Calendar.MINUTE, minute);
      cal.set(Calendar.SECOND, second);

      float time = cal.getTime().getTime() - start.getTime().getTime();
      float day = 24*60*60*1000;

      return time / day;
   }

   /**
    * Get decimal number for a particular time
    * @param date represents the time for which to obtain a fraction
    * @return fraction of the time TIME is a value ranging from
    * 0 (zero) to 0.99999999, representing the times from
    * 0:00:00 (12:00:00 AM) to 23:59:59 (11:59:59 P.M.).
    */
   public static double timevalue(Object date) {
      date = unwrapDate(date);

      if(date == null) {
         return -1;
      }

      Calendar start = CoreTool.calendar.get();
      start.setTime((Date) date);
      start.set(Calendar.AM_PM, Calendar.AM);
      start.set(Calendar.HOUR, 0);
      start.set(Calendar.MINUTE, 0);
      start.set(Calendar.SECOND, 0);

      float time = ((Date) date).getTime() - start.getTime().getTime();
      float day = 24 * 60 * 60 * 1000;

      return time / day;
   }

   /**
    * Get the current date time
    * @return current date time
    */
   public static Object today() {
      return new Date();
   }

   /**
    * Get the day of the week corresponding to a date
    * @param date date to get the day of week
    * @param return_type
    * 1 or null --> Numbers 1 (Sunday) through 7 (Saturday).
    * 2         --> Numbers 1 (Monday) through 7 (Sunday).
    * 3         --> Numbers 0 (Monday) through 6 (Sunday).
    * @return day of the week
    */
   public static int weekday(Object date, Object return_type) {
      date = unwrapDate(date);

      if(date == null) {
         return -1;
      }

      return_type = JavaScriptEngine.unwrap(return_type);
      int rt = 1;

      if(return_type != null) {
         rt = ((Number) return_type).intValue();
      }

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) date);

      // Default --> 1 (Sunday) through 7 (Saturday)
      int day_of_week = cal.get(Calendar.DAY_OF_WEEK);

      // 1 (Monday) through 7 (Sunday)
      if(rt == 2) {
         day_of_week = (day_of_week + 6) % 7;

         if(day_of_week < 1) {
            day_of_week = 7;
         }
      }
      // 0 (Monday) through 6 (Sunday)
      else if(rt == 3) {
         day_of_week -= 2;

         if(day_of_week < 0) {
            day_of_week = 6;
         }
      }

      return day_of_week;
   }

   /**
    * Get the value where the week falls numerically within a year.
    * @param date date to get week identifier
    * @param return_type
    * 1 or null --> Week begins on Sunday. Weekdays are numbered 1 through 7.
    * 2         --> Week begins on Monday. Weekdays are numbered 1 through 7.
    * @return week of the year
    */
   public static int weeknum(Object date, Object return_type) {
      date = unwrapDate(date);

      if(date == null) {
         return -1;
      }

      return_type = JavaScriptEngine.unwrap(return_type);
      int rt = 1;

      if(return_type != null) {
         rt = ((Number) return_type).intValue();
      }

      Calendar cal = Calendar.getInstance();
      cal.setTime((Date) date);

      //Default --> Week Begins SUNDAY

      // Week begins MONDAY
      if(rt == 2) {
         cal.setFirstDayOfWeek(Calendar.MONDAY);
      }
      else {
         cal.setFirstDayOfWeek(Calendar.SUNDAY);
      }

      return cal.get(Calendar.WEEK_OF_YEAR);
   }

   /**
    * Get the number of working days before or after a date.
    * @param start_date input date
    * @param days
    * 1 or null --> Week begins on Sunday. Weekdays are numbered 1 through 7.
    * 2         --> Week begins on Monday. Weekdays are numbered 1 through 7.
    * @return week of the year
    */
   public static Object workday(Object start_date, int days, Object holidaysObj) {
      Object[] holidays = JavaScriptEngine.split(holidaysObj);
      start_date = unwrapDate(start_date);

      if(start_date == null) {
         return null;
      }

      if(holidays != null) {
         holidays = JavaScriptEngine.split(holidays);
      }
      else {
         holidays = new Object[0];
      }

      Calendar start = CoreTool.calendar.get();
      start.setTime((Date) start_date);

      int difference = 1;

      //if -ve days indicates rolling back to date older than start date
      if(days < 0) {
         difference = -1;
         days *= -1;
      }

      Calendar cal = Calendar.getInstance();

      while(days != 0) {
         start.set(Calendar.DATE, start.get(Calendar.DATE) + difference);

         int day = start.get(Calendar.DAY_OF_WEEK);
         boolean holiday = false;

         for(Object holidayObj : holidays) {
            Date holidayDate = (Date) unwrapDate(holidayObj);
            cal.setTime(holidayDate);

            if((start.get(Calendar.YEAR) == cal.get(Calendar.YEAR)) &&
               (start.get(Calendar.MONTH) == cal.get(Calendar.MONTH)) &&
               (start.get(Calendar.DATE) == cal.get(Calendar.DATE))) {
               holiday = true;
            }
         }

         if(day != Calendar.SATURDAY && day != Calendar.SUNDAY && !holiday) {
            days--;
         }
      }

      return start.getTime();
   }

   /**
    * Get the year from a date.
    * @param date input date
    * @return year value
    */
   public static int year(Object date) {
      return year(date, null);
   }

   /**
    * Get the year from a date.
    * @param date input date
    * @param intervalstr the interval value
    * @return year value
    */
   public static int year(Object date, Object intervalstr) {
      date = unwrapDate(date);
      intervalstr = JavaScriptEngine.unwrap(intervalstr);

      Calendar cal = CoreTool.calendar.get();

      if(date == null) {
         return -1;
      }

      cal.setTime((Date) date);

      int year = cal.get(Calendar.YEAR);
      double interval = 1;

      if(intervalstr != null) {
         try {
            interval = Double.parseDouble(intervalstr.toString());
         }
         catch(Exception ex) {
            // ignore it
            interval = 1;
         }
      }

      return (int) (((int) (year / interval)) * interval);
   }

   /**
    * Get the fraction of the year represented by the number of
    * whole days between two dates
    * @param start_date Start Date
    * @param end_date End Date
    * @param basis
    * 0 or null --> US (NASD) 30/360
    * 1         --> Actual/actual
    * 2         --> Actual/360
    * 3         --> Actual/365
    * 4         --> European 30/360
    * @return fraction of year, -1 indicates error
    * i.e. basis < 0 or basis > 4
    */
   public static double yearfrac(Object start_date, Object end_date, Object basis) {
      start_date = unwrapDate(start_date);
      end_date = unwrapDate(end_date);
      basis = JavaScriptEngine.unwrap(basis);


      Calendar start = CoreTool.calendar.get();
      start.setTime((Date) start_date);

      Calendar end = CoreTool.calendar2.get();
      end.setTime((Date) end_date);

      int basis_val = 0;

      if(basis != null) {
         basis_val = ((Number) basis).intValue();
      }

      //basis < 0 or > 4 is invalid
      if(basis_val < 0 || basis_val > 4) {
         throw new RuntimeException("Basis value specified (" + basis_val +
                                    ") is invalid");
      }

      return CalcUtil.calculateBasisDayCountFraction((Date) start_date,
                                                     (Date) end_date,
                                                     basis_val);
   }

   /**
    * Return the text name of the month of the date.
    */
   public static String monthname(Object dateObj) {
      Date date = (Date) unwrapDate(dateObj);

      return MONTH_FMT.get().format(date);
   }

   /**
    * Return the text name of the day of week of the date.
    */
   public static String weekdayname(Object dateObj) {
      Date date = (Date) unwrapDate(dateObj);

      return WEEKDAY_FMT.get().format(date);
   }

   /**
    * Apply interval option for date.
    */
   public static Date date(Object dobj, Object optionobj, Object intervalobj) {
      if(!(dobj instanceof Date) || !(optionobj instanceof Number) ||
         !(intervalobj instanceof Number))
      {
         return null;
      }

      Date d = (Date) dobj;
      int option = ((Number) optionobj).intValue();
      double interval = ((Number) intervalobj).doubleValue();
      Calendar cal = CoreTool.calendar.get();
      cal.clear();
      cal.setTime(d);
      int year, month, weeks, day, hour, minute, second, millisecond;

      year = cal.get(Calendar.YEAR);
      month = cal.get(Calendar.MONTH);
      weeks = cal.get(Calendar.WEEK_OF_YEAR);
      day = cal.get(Calendar.DAY_OF_MONTH);
      hour = cal.get(Calendar.HOUR_OF_DAY);
      minute = cal.get(Calendar.MINUTE);
      second = cal.get(Calendar.SECOND);
      millisecond = cal.get(Calendar.MILLISECOND);
      int h2;

      switch(option) {
      case YEAR_DATE_GROUP:
         h2 = (int) (year / interval);
         h2 = (int) (h2 * interval);
         setDate(cal, h2, 0, 1, 0, 0, 0, 0);
         break;
      case QUARTER_DATE_GROUP:
         h2 = (int) (month / (interval * 3));
         h2 = (int) (h2 * interval * 3);
         setDate(cal, year, h2, 1, 0, 0, 0, 0);
         break;
      case MONTH_DATE_GROUP:
         h2 = (int) (month / interval);
         h2 = (int) (h2 * interval);
         setDate(cal, year, h2, 1, 0, 0, 0, 0);
         break;
      case WEEK_DATE_GROUP:
         h2 = (int) (weeks / interval);

         // next year's first week
         if(month == Calendar.DECEMBER && weeks == 1) {
            year += 1;
         }

         h2 = (int) (h2 * interval);
         cal.clear();
         cal.setFirstDayOfWeek(Tool.getFirstDayOfWeek());
         cal.set(year, Calendar.JANUARY, 1, 0, 0, 0);
         cal.set(Calendar.WEEK_OF_YEAR, h2);
         cal.set(Calendar.DAY_OF_WEEK, Tool.getFirstDayOfWeek());
         break;
      case DAY_DATE_GROUP:
         // calendar day start from 1, so we must minus 1
         h2 = (int) ((day - 1) / interval);
         h2 = (int) (h2 * interval) + 1;
         setDate(cal, year, month, h2, 0, 0, 0, 0);
         break;
      case HOUR_DATE_GROUP:
         h2 = (int) (hour / interval);
         h2 = (int) (h2 * interval);
         setDate(cal, year, month, day, h2, 0, 0, 0);
         break;
      case MINUTE_DATE_GROUP:
         h2 = (int) (minute / interval);
         h2 = (int) (h2 * interval);
         setDate(cal, year, month, day, hour, h2, 0, 0);
         break;
      case SECOND_DATE_GROUP:
         h2 = (int) (second / interval);
         h2 = (int) (h2 * interval);
         setDate(cal, year, month, day, hour, minute, h2, 0);
         break;
      case MILLISECOND_DATE_GROUP:
         h2 = (int) (millisecond / interval);
         h2 = (int) (h2 * interval);
         setDate(cal, year, month, day, hour, minute, second, h2);
         break;
      }

      Date date = cal.getTime();

      // maintain same date type to avoid subtle problems downstream
      if(dobj instanceof java.sql.Date) {
         date = new java.sql.Date(date.getTime());
      }
      else if(dobj instanceof java.sql.Timestamp) {
         date = new java.sql.Timestamp(date.getTime());
      }
      else if(dobj instanceof java.sql.Time) {
         cal.setTime(date);
         cal.set(Calendar.YEAR, 1970);
         cal.set(Calendar.MONTH, Calendar.JANUARY);
         cal.set(Calendar.DAY_OF_MONTH, 1);
         date = new java.sql.Time(cal.getTime().getTime());
      }

      return date;
   }

   /**
    * Get the max date.
    */
   public static Date maxDate(Object objs) {
      Object[] dates = sortDate(objs);

      return dates.length > 0 ? (Date) dates[dates.length - 1] : null;
   }

   /**
    * Get the min date.
    */
   public static Date minDate(Object objs) {
      Object[] dates = sortDate(objs);

      return dates.length > 0 ? (Date) dates[0] : null;
   }

   /**
    * Gets the year of a simple fiscal year for the specified date. A simple
    * fiscal year is defined by a start date.
    *
    * @param date       the date.
    * @param startMonth the month in which the fiscal year starts.
    * @param startDay   the date on which the fiscal year starts (optional).
    * @param timeZone   the time zone in which the fiscal start month and date
    *                   are specified (optional).
    *
    * @return the four-digit fiscal year for the date.
    *
    * @since 12.0
    */
   public static int fiscalyear(Object date, Object startMonth, Object startDay,
                                Object timeZone)
   {
      Calendar dateCalendar = createCalendar(timeZone);
      dateCalendar.setTime((Date) unwrapDate(date));

      Calendar fyCalendar = createSimpleFiscalCalendar(
         dateCalendar.get(Calendar.YEAR), startMonth, startDay, timeZone);

      int fiscalYear = fyCalendar.get(Calendar.YEAR);

      if(dateCalendar.before(fyCalendar)) {
         // in previous fiscal year
         --fiscalYear;
      }

      return fiscalYear;
   }

   /**
    * Gets the quarter of a simple fiscal year for the specified date. A simple
    * fiscal year is defined by a start date.
    *
    * @param date       the date.
    * @param startMonth the one-based month in which the fiscal year starts.
    * @param startDay   the date on which the fiscal year starts (optional).
    * @param timeZone   the time zone in which the fiscal start month and date
    *                   are specified (optional).
    *
    * @return the fiscal quarter number for the date.
    *
    * @since 12.0
    */
   public static int fiscalquarter(Object date, Object startMonth,
                                   Object startDay, Object timeZone)
   {
      Calendar dateCalendar = createCalendar(timeZone);
      dateCalendar.setTime((Date) unwrapDate(date));

      Calendar fyCalendar = createSimpleFiscalCalendar(
         dateCalendar.get(Calendar.YEAR), startMonth, startDay, timeZone);

      if(dateCalendar.before(fyCalendar)) {
         // in previous fiscal year
         dateCalendar.add(Calendar.YEAR, 1);
      }

      int months = getSimpleFiscalMonths(fyCalendar, dateCalendar);
      return (months - 1) / 3 + 1;
   }

   /**
    * Gets the month of a simple fiscal year for the specified date. A simple
    * fiscal year is defined by a start date.
    *
    * @param date       the date.
    * @param startMonth the one-based month in which the fiscal year starts.
    * @param startDay   the date on which the fiscal year starts (optional).
    * @param timeZone   the time zone in which the fiscal start month and date
    *                   are specified (optional).
    *
    * @return the one-based month of the fiscal year.
    *
    * @since 12.0
    */
   public static int fiscalmonth(Object date, Object startMonth,
                                 Object startDay, Object timeZone)
   {
      Calendar dateCalendar = createCalendar(timeZone);
      dateCalendar.setTime((Date) unwrapDate(date));

      Calendar fyCalendar = createSimpleFiscalCalendar(
         dateCalendar.get(Calendar.YEAR), startMonth, startDay, timeZone);

      if(dateCalendar.before(fyCalendar)) {
         // in previous fiscal year
         dateCalendar.add(Calendar.YEAR, 1);
      }

      return getSimpleFiscalMonths(fyCalendar, dateCalendar);
   }

   /**
    * Gets the week of a simple fiscal year for the specified date. A simple
    * fiscal year is defined by a start date.
    *
    * @param date       the date.
    * @param startMonth the one-based month in which the fiscal year starts.
    * @param startDay   the date on which the fiscal year starts (optional).
    * @param timeZone   the time zone in which the fiscal start month and date
    *                   are specified (optional).
    *
    * @return the one-based week of the fiscal year.
    *
    * @since 12.0
    */
   public static int fiscalweek(Object date, Object startMonth,
                                Object startDay, Object timeZone)
   {
      Calendar dateCalendar = createCalendar(timeZone);
      dateCalendar.setTime((Date) unwrapDate(date));

      Calendar fyCalendar = createSimpleFiscalCalendar(
         dateCalendar.get(Calendar.YEAR), startMonth, startDay, timeZone);

      if(dateCalendar.before(fyCalendar)) {
         // in previous fiscal year
         dateCalendar.add(Calendar.YEAR, 1);
      }

      int week = 0;

      while(!fyCalendar.after(dateCalendar)) {
         ++week;
         fyCalendar.add(Calendar.DATE, 7);
      }

      return week;
   }

   /**
    * Gets the year for a date in a 4-4-5 fiscal calendar.
    *
    * @param date             the date.
    * @param startYear        the year in which the fiscal calendar starts.
    * @param startMonth       the month in which the fiscal calendar starts.
    * @param startDay         the date on which the fiscal calendar starts.
    * @param yearsWith53Weeks the list of years that contain an extra week, for
    *                         a total of 53 weeks.
    * @param timeZone         the time zone in which the fiscal start year,
    *                         month, and date are specified (optional).
    *
    * @return the four-digit year.
    *
    * @since 12.0
    */
   public static int fiscalyear445(Object date, Object startYear,
                                   Object startMonth, Object startDay,
                                   Object yearsWith53Weeks, Object timeZone)
   {
      return get52WeekFiscalYear(
         date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone);
   }

   /**
    * Gets the quarter for a date in a 4-4-5 fiscal calendar.
    *
    * @param date             the date.
    * @param startYear        the year in which the fiscal calendar starts.
    * @param startMonth       the month in which the fiscal calendar starts.
    * @param startDay         the date on which the fiscal calendar starts.
    * @param yearsWith53Weeks the list of years that contain an extra week, for
    *                         a total of 53 weeks.
    * @param timeZone         the time zone in which the fiscal start year,
    *                         month, and date are specified (optional).
    *
    * @return the quarter number.
    *
    * @since 12.0
    */
   public static int fiscalquarter445(Object date, Object startYear,
                                      Object startMonth, Object startDay,
                                      Object yearsWith53Weeks, Object timeZone)
   {
      return get52WeekFiscalQuarter(
         date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone);
   }

   /**
    * Gets the month for a date in a 4-4-5 fiscal calendar.
    *
    * @param date             the date.
    * @param startYear        the year in which the fiscal calendar starts.
    * @param startMonth       the month in which the fiscal calendar starts.
    * @param startDay         the date on which the fiscal calendar starts.
    * @param yearsWith53Weeks the list of years that contain an extra week, for
    *                         a total of 53 weeks.
    * @param timeZone         the time zone in which the fiscal start year,
    *                         month, and date are specified (optional).
    *
    * @return the quarter number.
    *
    * @since 12.0
    */
   public static int fiscalmonth445(Object date, Object startYear,
                                    Object startMonth, Object startDay,
                                    Object yearsWith53Weeks, Object timeZone)
   {
      return get52WeekFiscalMonth(
         date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone,
         4, 4, 5);
   }

   /**
    * Gets the week for a date in a 4-4-5 fiscal calendar.
    *
    * @param date             the date.
    * @param startYear        the year in which the fiscal calendar starts.
    * @param startMonth       the month in which the fiscal calendar starts.
    * @param startDay         the date on which the fiscal calendar starts.
    * @param yearsWith53Weeks the list of years that contain an extra week, for
    *                         a total of 53 weeks.
    * @param timeZone         the time zone in which the fiscal start year,
    *                         month, and date are specified (optional).
    *
    * @return the week number.
    *
    * @since 12.0
    */
   public static int fiscalweek445(Object date, Object startYear,
                                   Object startMonth, Object startDay,
                                   Object yearsWith53Weeks, Object timeZone)
   {
      return get52WeekFiscalWeek(
         date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone);
   }

   /**
    * Gets the year for a date in a 4-5-4 fiscal calendar.
    *
    * @param date             the date.
    * @param startYear        the year in which the fiscal calendar starts.
    * @param startMonth       the month in which the fiscal calendar starts.
    * @param startDay         the date on which the fiscal calendar starts.
    * @param yearsWith53Weeks the list of years that contain an extra week, for
    *                         a total of 53 weeks.
    * @param timeZone         the time zone in which the fiscal start year,
    *                         month, and date are specified (optional).
    *
    * @return the four-digit year.
    *
    * @since 12.0
    */
   public static int fiscalyear454(Object date, Object startYear,
                                   Object startMonth, Object startDay,
                                   Object yearsWith53Weeks, Object timeZone)
   {
      return get52WeekFiscalYear(
         date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone);
   }

   /**
    * Gets the quarter for a date in a 4-5-4 fiscal calendar.
    *
    * @param date             the date.
    * @param startYear        the year in which the fiscal calendar starts.
    * @param startMonth       the month in which the fiscal calendar starts.
    * @param startDay         the date on which the fiscal calendar starts.
    * @param yearsWith53Weeks the list of years that contain an extra week, for
    *                         a total of 53 weeks.
    * @param timeZone         the time zone in which the fiscal start year,
    *                         month, and date are specified (optional).
    *
    * @return the quarter number.
    *
    * @since 12.0
    */
   public static int fiscalquarter454(Object date, Object startYear,
                                      Object startMonth, Object startDay,
                                      Object yearsWith53Weeks, Object timeZone)
   {
      return get52WeekFiscalQuarter(
         date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone);
   }

   /**
    * Gets the month for a date in a 4-5-4 fiscal calendar.
    *
    * @param date             the date.
    * @param startYear        the year in which the fiscal calendar starts.
    * @param startMonth       the month in which the fiscal calendar starts.
    * @param startDay         the date on which the fiscal calendar starts.
    * @param yearsWith53Weeks the list of years that contain an extra week, for
    *                         a total of 53 weeks.
    * @param timeZone         the time zone in which the fiscal start year,
    *                         month, and date are specified (optional).
    *
    * @return the quarter number.
    *
    * @since 12.0
    */
   public static int fiscalmonth454(Object date, Object startYear,
                                    Object startMonth, Object startDay,
                                    Object yearsWith53Weeks, Object timeZone)
   {
      return get52WeekFiscalMonth(
         date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone,
         4, 5, 4);
   }

   /**
    * Gets the week for a date in a 4-5-4 fiscal calendar.
    *
    * @param date             the date.
    * @param startYear        the year in which the fiscal calendar starts.
    * @param startMonth       the month in which the fiscal calendar starts.
    * @param startDay         the date on which the fiscal calendar starts.
    * @param yearsWith53Weeks the list of years that contain an extra week, for
    *                         a total of 53 weeks.
    * @param timeZone         the time zone in which the fiscal start year,
    *                         month, and date are specified (optional).
    *
    * @return the week number.
    *
    * @since 12.0
    */
   public static int fiscalweek454(Object date, Object startYear,
                                   Object startMonth, Object startDay,
                                   Object yearsWith53Weeks, Object timeZone)
   {
      return get52WeekFiscalWeek(
         date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone);
   }

   /**
    * Gets the year for a date in a 5-4-4 fiscal calendar.
    *
    * @param date             the date.
    * @param startYear        the year in which the fiscal calendar starts.
    * @param startMonth       the month in which the fiscal calendar starts.
    * @param startDay         the date on which the fiscal calendar starts.
    * @param yearsWith53Weeks the list of years that contain an extra week, for
    *                         a total of 53 weeks.
    * @param timeZone         the time zone in which the fiscal start year,
    *                         month, and date are specified (optional).
    *
    * @return the four-digit year.
    *
    * @since 12.0
    */
   public static int fiscalyear544(Object date, Object startYear,
                                   Object startMonth, Object startDay,
                                   Object yearsWith53Weeks, Object timeZone)
   {
      return get52WeekFiscalYear(
         date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone);
   }

   /**
    * Gets the quarter for a date in a 5-4-4 fiscal calendar.
    *
    * @param date             the date.
    * @param startYear        the year in which the fiscal calendar starts.
    * @param startMonth       the month in which the fiscal calendar starts.
    * @param startDay         the date on which the fiscal calendar starts.
    * @param yearsWith53Weeks the list of years that contain an extra week, for
    *                         a total of 53 weeks.
    * @param timeZone         the time zone in which the fiscal start year,
    *                         month, and date are specified (optional).
    *
    * @return the quarter number.
    *
    * @since 12.0
    */
   public static int fiscalquarter544(Object date, Object startYear,
                                      Object startMonth, Object startDay,
                                      Object yearsWith53Weeks, Object timeZone)
   {
      return get52WeekFiscalQuarter(
         date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone);
   }

   /**
    * Gets the month for a date in a 5-4-4 fiscal calendar.
    *
    * @param date             the date.
    * @param startYear        the year in which the fiscal calendar starts.
    * @param startMonth       the month in which the fiscal calendar starts.
    * @param startDay         the date on which the fiscal calendar starts.
    * @param yearsWith53Weeks the list of years that contain an extra week, for
    *                         a total of 53 weeks.
    * @param timeZone         the time zone in which the fiscal start year,
    *                         month, and date are specified (optional).
    *
    * @return the quarter number.
    *
    * @since 12.0
    */
   public static int fiscalmonth544(Object date, Object startYear,
                                    Object startMonth, Object startDay,
                                    Object yearsWith53Weeks, Object timeZone)
   {
      return get52WeekFiscalMonth(
         date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone,
         5, 4, 4);
   }

   /**
    * Gets the week for a date in a 5-4-4 fiscal calendar.
    *
    * @param date             the date.
    * @param startYear        the year in which the fiscal calendar starts.
    * @param startMonth       the month in which the fiscal calendar starts.
    * @param startDay         the date on which the fiscal calendar starts.
    * @param yearsWith53Weeks the list of years that contain an extra week, for
    *                         a total of 53 weeks.
    * @param timeZone         the time zone in which the fiscal start year,
    *                         month, and date are specified (optional).
    *
    * @return the week number.
    *
    * @since 12.0
    */
   public static int fiscalweek544(Object date, Object startYear,
                                   Object startMonth, Object startDay,
                                   Object yearsWith53Weeks, Object timeZone)
   {
      return get52WeekFiscalWeek(
         date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone);
   }

   /**
    * Creates a calendar for the specified time zone. If the time zone is not
    * specified, the default time zone is used.
    *
    * @param timeZone the identifier for the time zone.
    *
    * @return a new calendar instance.
    *
    * @since 12.0
    */
   private static Calendar createCalendar(Object timeZone) {
      Calendar calendar = Calendar.getInstance();

      if(timeZone != null && !(timeZone instanceof Undefined)) {
         calendar.setTimeZone(TimeZone.getTimeZone((String) timeZone));
      }

      return calendar;
   }

   /**
    * Creates a calendar set to the start date of a simple fiscal year.
    *
    * @param year       the Julian calendar year for the calendar object.
    * @param startMonth the one-based month in which the fiscal year starts.
    * @param startDay   the date on which the fiscal year starts (optional).
    * @param timeZone   the time zone in which the fiscal start month and date
    *                   are specified (optional).
    *
    * @return the fiscal year start date.
    */
   private static Calendar createSimpleFiscalCalendar(int year,
                                                      Object startMonth,
                                                      Object startDay,
                                                      Object timeZone)
   {
      int startMonthValue = ((Number) startMonth).intValue();
      Integer startDateValue =
         startDay == null || (startDay instanceof Undefined) ?
         null : ((Number) startDay).intValue();

      Calendar calendar = createCalendar(timeZone);
      calendar.clear();
      calendar.set(Calendar.YEAR, year);
      calendar.set(Calendar.MONTH, startMonthValue - 1);

      if(startDateValue == null) {
         calendar.set(Calendar.DATE, 1);
      }
      else {
         calendar.set(Calendar.DATE, startDateValue);
      }

      return calendar;
   }

   /**
    * Gets the year for a date in a 52/53-week fiscal calendar.
    *
    * @param date             the date.
    * @param startYear        the year in which the fiscal calendar starts.
    * @param startMonth       the month in which the fiscal calendar starts.
    * @param startDay         the date on which the fiscal calendar starts.
    * @param yearsWith53Weeks the list of years that contain an extra week, for
    *                         a total of 53 weeks.
    * @param timeZone         the time zone in which the fiscal start year,
    *                         month, and date are specified (optional).
    *
    * @return the four-digit year.
    *
    * @since 12.0
    */
   private static int get52WeekFiscalYear(Object date, Object startYear,
                                          Object startMonth, Object startDay,
                                          Object yearsWith53Weeks,
                                          Object timeZone)
   {
      Calendar dateCalendar = createCalendar(timeZone);
      dateCalendar.setTime((Date) unwrapDate(date));

      Calendar startCalendar = create52WeekStartCalendar(
         dateCalendar, startYear, startMonth, startDay, yearsWith53Weeks,
         timeZone);

      return startCalendar.get(Calendar.YEAR);
   }

   /**
    * Gets the quarter for a date in a 52/53-week fiscal calendar.
    *
    * @param date             the date.
    * @param startYear        the year in which the fiscal calendar starts.
    * @param startMonth       the month in which the fiscal calendar starts.
    * @param startDay         the date on which the fiscal calendar starts.
    * @param yearsWith53Weeks the list of years that contain an extra week, for
    *                         a total of 53 weeks.
    * @param timeZone         the time zone in which the fiscal start year,
    *                         month, and date are specified (optional).
    *
    * @return the quarter number.
    *
    * @since 12.0
    */
   private static int get52WeekFiscalQuarter(Object date, Object startYear,
                                             Object startMonth, Object startDay,
                                             Object yearsWith53Weeks,
                                             Object timeZone)
   {
      int dayOfYear = get52WeekFiscalDayOfYear(
         date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone);
      int quarter = (dayOfYear - 1) / 91 + 1;

      if(quarter > 4) {
         // in 53rd week
         quarter = 4;
      }

      return quarter;
   }

   /**
    * Gets the month for a date in a 52/53-week fiscal calendar.
    *
    * @param date             the date.
    * @param startYear        the year in which the fiscal calendar starts.
    * @param startMonth       the month in which the fiscal calendar starts.
    * @param startDay         the date on which the fiscal calendar starts.
    * @param yearsWith53Weeks the list of years that contain an extra week, for
    *                         a total of 53 weeks.
    * @param timeZone         the time zone in which the fiscal start year,
    *                         month, and date are specified (optional).
    *
    * @return the one-based month number.
    *
    * @since 12.0
    */
   private static int get52WeekFiscalMonth(Object date, Object startYear,
                                           Object startMonth, Object startDay,
                                           Object yearsWith53Weeks,
                                           Object timeZone,
                                           int ... weekLengths)
   {
      int dayOfYear = get52WeekFiscalDayOfYear(
         date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone);
      int quarter = (dayOfYear - 1) / 91 + 1;

      if(quarter > 4) {
         // in 53rd week
         quarter = 4;
      }

      int month = (quarter - 1) * 3;
      int quarterOffset = (quarter - 1) * 91;
      int dayInQuarter = dayOfYear - quarterOffset;
      int weekInQuarter = (dayInQuarter - 1) / 7 + 1;

      int result = 0;
      int totalWeeks = 0;

      for(int i = 0; i < weekLengths.length - 1; i++) {
         if(weekInQuarter <= totalWeeks + weekLengths[i]) {
            result = i + 1;
            break;
         }

         totalWeeks += weekLengths[i];
      }

      if(result == 0) {
         // in 53rd week
         result = weekLengths.length;
      }

      return month + result;
   }

   /**
    * Gets the week for a date in a 52/53-week fiscal calendar.
    *
    * @param date             the date.
    * @param startYear        the year in which the fiscal calendar starts.
    * @param startMonth       the month in which the fiscal calendar starts.
    * @param startDay         the date on which the fiscal calendar starts.
    * @param yearsWith53Weeks the list of years that contain an extra week, for
    *                         a total of 53 weeks.
    * @param timeZone         the time zone in which the fiscal start year,
    *                         month, and date are specified (optional).
    *
    * @return the week number.
    *
    * @since 12.0
    */
   private static int get52WeekFiscalWeek(Object date, Object startYear,
                                          Object startMonth, Object startDay,
                                          Object yearsWith53Weeks,
                                          Object timeZone)
   {
      int dayOfYear = get52WeekFiscalDayOfYear(
         date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone);
      return (dayOfYear - 1) / 7 + 1;
   }

   /**
    * Gets the day of year for a date in a 52/53-week fiscal calendar.
    *
    * @param date             the date.
    * @param startYear        the year in which the fiscal calendar starts.
    * @param startMonth       the month in which the fiscal calendar starts.
    * @param startDay         the date on which the fiscal calendar starts.
    * @param yearsWith53Weeks the list of years that contain an extra week, for
    *                         a total of 53 weeks.
    * @param timeZone         the time zone in which the fiscal start year,
    *                         month, and date are specified (optional).
    *
    * @return the day number.
    *
    * @since 12.0
    */
   private static int get52WeekFiscalDayOfYear(Object date, Object startYear,
                                               Object startMonth, Object startDay,
                                               Object yearsWith53Weeks,
                                               Object timeZone)
   {
      Calendar dateCalendar = createCalendar(timeZone);
      dateCalendar.setTime((Date) unwrapDate(date));

      Calendar startCalendar = create52WeekStartCalendar(
         dateCalendar, startYear, startMonth, startDay, yearsWith53Weeks,
         timeZone);

      int dayOfYear;

      int startDayOfYear = startCalendar.get(Calendar.DAY_OF_YEAR);
      int dateDayOfYear = dateCalendar.get(Calendar.DAY_OF_YEAR);

      if(dateCalendar.get(Calendar.YEAR) > startCalendar.get(Calendar.YEAR)) {
         dayOfYear = startCalendar.getActualMaximum(Calendar.DAY_OF_YEAR) -
            startDayOfYear + dateDayOfYear + 1;
      }
      else {
         dayOfYear = dateDayOfYear - startDayOfYear + 1;
      }

      return dayOfYear;
   }

   /**
    * Creates a calendar that is set to the first day of the specified year in
    * a 52/53-week fiscal calendar.
    *
    * @param date             the date which is contained by the fiscal year.
    * @param startYear        the year in which the fiscal calendar starts.
    * @param startMonth       the month in which the fiscal calendar starts.
    * @param startDay         the date on which the fiscal calendar starts.
    * @param yearsWith53Weeks the list of years that contain an extra week, for
    *                         a total of 53 weeks.
    * @param timeZone         the time zone in which the fiscal start year,
    *                         month, and date are specified (optional).
    *
    * @return the start calendar.
    */
   private static Calendar create52WeekStartCalendar(
      Calendar date, Object startYear, Object startMonth, Object startDay,
      Object yearsWith53Weeks, Object timeZone)
   {
      Calendar calendar = createCalendar(timeZone);
      calendar.clear();
      calendar.set(Calendar.YEAR, ((Number) startYear).intValue());
      calendar.set(Calendar.MONTH, ((Number) startMonth).intValue() - 1);
      calendar.set(Calendar.DATE, ((Number) startDay).intValue());

      if(date.before(calendar)) {
         throw new IllegalArgumentException(
            "Specified date \"" + date.getTime() +
            "\" is before fiscal calendar start \"" +
            calendar.getTime() + "\"");
      }

      Object[] yearsArray = JavaScriptEngine.split(yearsWith53Weeks);
      int[] years = new int[yearsArray.length];

      for(int i = 0; i < years.length; i++) {
         if(yearsArray[i] instanceof Number) {
            years[i] = ((Number) yearsArray[i]).intValue();
         }
         else if(yearsArray[i] instanceof String) {
            years[i] = Integer.parseInt((String) yearsArray[i]);
         }
         else {
            throw new IllegalArgumentException(
               "Invalid 53-week year value: " + yearsArray[i]);
         }
      }

      Arrays.sort(years);

      while(true) {
         int days;

         if(Arrays.binarySearch(years, calendar.get(Calendar.YEAR)) < 0) {
            // 52-week year
            days = 364;
         }
         else {
            // 53-week year
            days = 371;
         }

         calendar.add(Calendar.DATE, days);

         if(!calendar.before(date)) {
            if(calendar.after(date)) {
               calendar.add(Calendar.DATE, -days);
            }

            break;
         }
      }

      return calendar;
   }

   /**
    * Gets the number of fiscal months between two dates in a simple fiscal
    * calendar.
    *
    * @param start the start date of the calendar year.
    * @param date  the date to which to count the months.
    *
    * @return the number of months.
    */
   private static int getSimpleFiscalMonths(Calendar start, Calendar date) {
      int months = 0;
      int day = start.get(Calendar.DATE);

      while(!start.after(date)) {
         ++months;

         // Don't naively use Calendar.add(Calendar.MONTH, 1) because this may
         // cause problems if the start day is greater than 28. For example,
         // what does adding one month to May 31 mean? There is no June 31, so
         // is the result July 1 or June 30. Counting the months in the manner
         // below avoids this ambiguity. We set the date of the calendar to the
         // first so there are no problems with the next month's end date. Then
         // we move the calendar to the next month, rolling over to January of
         // the next year if necessary. Finally, we set the date to the maximum
         // of the start date and the actual last date of the current month.

         start.set(Calendar.DATE, 1);
         start.add(Calendar.MONTH, 1);

         // if the start day is greater than the number of days in the current
         // month, use the actual last day of the month
         int newDate =
            Math.min(day, start.getActualMaximum(Calendar.DAY_OF_MONTH));
         start.set(Calendar.DATE, newDate);
      }

      return months;
   }

   /**
    * Sort the dates
    */
   private static Object[] sortDate(Object objs) {
      Object[] dates = JavaScriptEngine.split(objs);

      Arrays.sort(dates, new Comparator() {
         @Override
         public int compare(Object date1, Object date2) {
            if(!(date1 instanceof Date)) {
               return -1;
            }

            if(!(date2 instanceof Date)) {
               return 1;
            }

            return ((Date) date1).compareTo(((Date) date2));
         }
      });

      return dates;
   }

   /**
    * Set date val to calendar.
    */
   private static void setDate(Calendar cal, int year, int month, int day,
                               int hour, int minute, int second, int msecond) {
      cal.clear();
      cal.set(year, month, day, hour, minute, second);
      cal.add(Calendar.MILLISECOND, msecond);
   }

   /**
    * Unwrap a javascript object as a date.
    */
   private static Object unwrapDate(Object obj) {
      Object date = JavaScriptEngine.unwrap(obj);

      if(date != null) {
         if(date instanceof Object[]) {
            date = ((Object[]) date)[0];
         }

         if(!(date instanceof Date)) {
            throw new RuntimeException("Date is required: " + date + " " + date.getClass());
         }
      }

      return date;
   }

   private static ThreadLocal<DateFormat> MONTH_FMT = ThreadLocal.withInitial(() ->
      Tool.createDateFormat("MMMM")
   );
   private static ThreadLocal<DateFormat> WEEKDAY_FMT = ThreadLocal.withInitial(() ->
      Tool.createDateFormat("EEEE")
   );
}
