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
package inetsoft.uql.asset;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.viewsheet.internal.DateComparisonUtil;
import inetsoft.util.Tool;

import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Class for processing date and calendar. This class is not thread safe.
 *
 * @author InetSoft Technology Corp
 * @version 12.2
 */
class DateTimeProcessor {
   public DateTimeProcessor() {
      firstDay = Tool.getFirstDayOfWeek();
   }

   public static DateTimeProcessor at(long millis) {
      DateTimeProcessor processor = new DateTimeProcessor();
      processor.setMillis(millis);
      return processor;
   }

   /**
    * Set the time in milliseconds from epoc.
    */
   public void setMillis(long time) {
      // fix Bug #31873, should update the time for epoch.
      if(this.time != time || time == 0) {
         this.time = time;
         dateTime = Instant.ofEpochMilli(time).atZone(DEFAULT_ZONE_ID);
         year = month = day = weekday = hour = minute = -1;
      }
   }

   /**
    * Get year of the current date.
    */
   public final int getYear() {
      if(year < 0) {
         year = dateTime.getYear();
      }

      return year;
   }

   /**
    * Get month of year of the current date.
    */
   public final int getMonthOfYear() {
      if(month < 0) {
         month = dateTime.getMonthValue();
      }

      return month;
   }

   /**
    * Get day of month of the current date.
    */
   public final int getDayOfMonth() {
      if(day < 0) {
         day = dateTime.getDayOfMonth();
      }

      return day;
   }

   /**
    * Get day of week of the current date.
    */
   public final int getDayOfWeek() {
      if(weekday < 0) {
         weekday = dateTime.getDayOfWeek().getValue();
      }

      return weekday;
   }

   /**
    * Get hour of day of the current date.
    */
   public final int getHourOfDay() {
      if(hour < 0) {
         hour = dateTime.getHour();
      }

      return hour;
   }

   /**
    * Get minute of hour of the current date.
    */
   public final int getMinuteOfHour() {
      if(minute < 0) {
         minute = dateTime.getMinute();
      }

      return minute;
   }

   /**
    * Get second of minute of the current date.
    */
   public final int getSecondOfMinute() {
      return dateTime.getSecond();
   }

   /**
    * Get the week of the year of the current date
    */
   public final int getWeekOfYear() {
      jcalendar.setTimeInMillis(time);
      jcalendar.setFirstDayOfWeek(firstDay);
      return jcalendar.get(Calendar.WEEK_OF_YEAR);
   }

   /**
    * Get the week of the month of the current date
    */
   public final int getWeekOfMonth() {
      jcalendar.setTimeInMillis(time);
      jcalendar.setFirstDayOfWeek(firstDay);
      return jcalendar.get(Calendar.WEEK_OF_MONTH);
   }

   /**
    * Get the month of the week for the date. If the day is on the first partial week of a
    * month, the previous month is returned.
    */
   public final Timestamp getMonthOfFullWeek() {
      return getMonthOfFullWeek(-1);
   }

   /**
    * Get the month of the week for the date. If the day is on the first partial week of a
    * month, the previous month is returned.
    */
   public final Timestamp getMonthOfFullWeek(int forceDcToDateWeekOfMonth) {
      int minDays = jcalendar.getMinimalDaysInFirstWeek();
      jcalendar.setMinimalDaysInFirstWeek(7);

      try {
         int weekOfMonth = getWeekOfMonth();

         if(weekOfMonth < 1) {
            jcalendar.add(Calendar.DATE, -7);
         }

         weekOfMonth = jcalendar.get(Calendar.WEEK_OF_MONTH);
         int year = jcalendar.get(Calendar.YEAR);
         int month = jcalendar.get(Calendar.MONTH);

         if(forceDcToDateWeekOfMonth > 0 && weekOfMonth != forceDcToDateWeekOfMonth) {
            jcalendar.set(Calendar.DATE, 1);
            jcalendar.add(Calendar.MONTH, -1);
            int maxWeekOfMonth = jcalendar.getActualMaximum(Calendar.WEEK_OF_MONTH);

            if(maxWeekOfMonth + weekOfMonth == forceDcToDateWeekOfMonth) {
               month = jcalendar.get(Calendar.MONTH);
               year = jcalendar.get(Calendar.YEAR);
            }
         }

         return getTimestamp(year, month + 1, 1, 0, 0, 0);
      }
      finally {
         jcalendar.setMinimalDaysInFirstWeek(minDays);
      }
   }

   /**
    * Get the quarter of the week for the date. If the day is on the first partial week of a
    * quarter, the previous quarter is returned. Quarter is 1 based.
    */
   public final int getQuarterPartOfFullWeek(int forceDcToDateWeekOfMonth) {
      Timestamp month = getMonthOfFullWeek(forceDcToDateWeekOfMonth);
      jcalendar.setTime(month);
      int monthOfYear = jcalendar.get(Calendar.MONTH);
      return monthOfYear / 3 + 1;
   }

   /**
    * Get the month of the week for the date. If the day is on the first partial week of a
    * month, the previous month is returned. Month is 1 based.
    */
   public final int getMonthPartOfFullWeek(int forceDcToDateWeekOfMonth) {
      int minDays = jcalendar.getMinimalDaysInFirstWeek();
      jcalendar.setMinimalDaysInFirstWeek(7);

      try {
         jcalendar.setTimeInMillis(time);
         jcalendar.setFirstDayOfWeek(firstDay);
         jcalendar.set(Calendar.DAY_OF_WEEK, firstDay);
         DateComparisonUtil.adjustCalendarByForceWM(jcalendar, forceDcToDateWeekOfMonth);

         return jcalendar.get(Calendar.MONTH) + 1;
      }
      finally {
         jcalendar.setMinimalDaysInFirstWeek(minDays);
      }
   }

   /**
    * Get the quarter of the week for the date. If the day is on the first partial week of a
    * quarter, the previous quarter is returned.
    */
   public final Timestamp getQuarterOfFullWeek(int forceDcToDateWeekOfMonth) {
      Timestamp month = getMonthOfFullWeek(forceDcToDateWeekOfMonth);
      jcalendar.setTime(month);
      int monthOfYear = jcalendar.get(Calendar.MONTH);

      if(monthOfYear % 3 != 0) {
         jcalendar.set(Calendar.MONTH, (monthOfYear / 3) * 3);
         return getTimestamp(jcalendar.get(Calendar.YEAR), jcalendar.get(Calendar.MONTH) + 1,
                             1, 0, 0, 0);
      }

      return month;
   }

   /**
    * Get the year of the week for the date. If the day is on the first partial week of a
    * year, the previous year is returned. For example, 2024-01-01 is the last full week
    * of 2023, so the YearOfFullWeek is 2023.
    */
   public final Timestamp getYearOfFullWeek(int forceDcToDateWeekOfMonth) {
      GregorianCalendar calendar = new GregorianCalendar();
      calendar.setMinimalDaysInFirstWeek(7);
      calendar.setTime(new Date(time));
      calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
      int weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH);

      if(weekOfMonth != forceDcToDateWeekOfMonth) {
         calendar.set(Calendar.DATE, 1);
         calendar.add(Calendar.MONTH, -1);

         if(calendar.getActualMaximum(Calendar.WEEK_OF_MONTH) + weekOfMonth == forceDcToDateWeekOfMonth) {
            calendar.set(Calendar.MONTH, 0);

            return getTimestamp(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
               1, 0, 0, 0);
         }
      }

      Timestamp month = getMonthOfFullWeek();
      jcalendar.setTime(month);
      int monthOfYear = jcalendar.get(Calendar.MONTH);

      if(monthOfYear != 0) {
         jcalendar.set(Calendar.MONTH, 0);
         return getTimestamp(jcalendar.get(Calendar.YEAR), jcalendar.get(Calendar.MONTH) + 1,
            1, 0, 0, 0);
      }

      return month;
   }

   /**
    * Get the date object.
    */
   public final Timestamp getTimestamp(int year, int month, int day,
                                       int hour, int minute, int second)
   {
      final ZonedDateTime dateTime = ZonedDateTime.of(year, month, day, hour, minute, second, 0, DEFAULT_ZONE_ID);
      return new Timestamp(dateTime.toInstant().toEpochMilli());
   }

   /**
    * Get the first day of week.
    */
   public final Timestamp getWeek(int year, int month, int day) {
      int weekday = getDayOfWeek();
      ZonedDateTime dateTime = ZonedDateTime.of(year, month, day, 0, 0, 0, 0, DEFAULT_ZONE_ID);
      dateTime = dateTime.plus(
         -((7 - toJodaDay(firstDay) + weekday) % 7), ChronoUnit.DAYS);
      return new Timestamp(dateTime.toInstant().toEpochMilli());
   }

   private int toJodaDay(int javaDay) {
      return (javaDay - 1) == 0 ? 7 : (javaDay - 1);
   }

   private long time;
   private int year, month, day, weekday, hour, minute;
   private ZonedDateTime dateTime = ZonedDateTime.now();
   private Calendar jcalendar = new GregorianCalendar();
   private int firstDay;

   // ZoneId.systemDefault creates a clone, so instead cache the object for reuse.
   private static final ZoneId DEFAULT_ZONE_ID = ZoneId.systemDefault();
}
