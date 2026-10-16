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

         // Bug #77463, before 1901 java.time (proleptic Gregorian, tzdb LMT offsets) and
         // java.util (Julian/Gregorian hybrid, raw zone offset before 1900) disagree on the
         // calendar fields of an instant. Values from JDBC, Excel and date parsing carry
         // hybrid fields and every label is formatted by SimpleDateFormat (hybrid), so read
         // the fields from a GregorianCalendar there. From 1901 on both engines agree on the
         // offsets and java.time is kept as is.
         if(time < HYBRID_CUTOFF_MILLIS) {
            setHybridFields(time);
         }
         else {
            dateTime = Instant.ofEpochMilli(time).atZone(DEFAULT_ZONE_ID);
            year = UNSET_YEAR;
            month = day = weekday = hour = minute = second = -1;
         }
      }
   }

   /**
    * Read all fields of a pre-1901 instant from the hybrid calendar. The year is the
    * astronomical year (1 BC = 0, 2 BC = -1, ...), the day of week is ISO (Monday = 1).
    */
   private void setHybridFields(long time) {
      GregorianCalendar cal = getHybridCalendar();
      cal.setTimeInMillis(time);
      year = getAstronomicalYear(cal);
      month = cal.get(Calendar.MONTH) + 1;
      day = cal.get(Calendar.DAY_OF_MONTH);
      int dow = cal.get(Calendar.DAY_OF_WEEK);
      weekday = dow == Calendar.SUNDAY ? 7 : dow - 1;
      hour = cal.get(Calendar.HOUR_OF_DAY);
      minute = cal.get(Calendar.MINUTE);
      second = cal.get(Calendar.SECOND);
   }

   /**
    * Get year of the current date.
    */
   public final int getYear() {
      if(year == UNSET_YEAR) {
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
      if(second < 0) {
         second = dateTime.getSecond();
      }

      return second;
   }

   /**
    * Get the week of the year of the current date
    */
   public final int getWeekOfYear() {
      // Bug #78112: a bare GregorianCalendar inherits the JVM default locale's
      // minimalDaysInFirstWeek (1 for en_US/zh_CN/root, 4 for en_GB/de_DE/fr_FR), so the same
      // date was grouped into a different week purely depending on the server's JVM locale.
      // Force the value CALC.weeknum's default return type already uses -- the week
      // containing January 1 is week 1 -- so this date level agrees with it and with
      // week.start, regardless of locale. Save/restore around the call since jcalendar is
      // shared with getWeekOfMonth()/getMonthOfFullWeek(), which rely on their own
      // minimalDaysInFirstWeek value being left as they set it.
      int minDays = jcalendar.getMinimalDaysInFirstWeek();
      jcalendar.setMinimalDaysInFirstWeek(1);

      try {
         jcalendar.setTimeInMillis(time);
         jcalendar.setFirstDayOfWeek(firstDay);
         return jcalendar.get(Calendar.WEEK_OF_YEAR);
      }
      finally {
         jcalendar.setMinimalDaysInFirstWeek(minDays);
      }
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
            // NOTE: -7 lands in the week *before* this date's own week, so the weekOfMonth
            // read below is one short of what datePart('wy') computes for the same date. It
            // only feeds the forceDcToDateWeekOfMonth comparison, which a forced value of 1-5
            // can never reach from here, so the returned month is unaffected either way.
            jcalendar.add(Calendar.DATE, -7);
         }

         weekOfMonth = jcalendar.get(Calendar.WEEK_OF_MONTH);
         int year = getAstronomicalYear(jcalendar);
         int month = jcalendar.get(Calendar.MONTH);

         if(forceDcToDateWeekOfMonth > 0 && weekOfMonth != forceDcToDateWeekOfMonth) {
            jcalendar.set(Calendar.DATE, 1);
            jcalendar.add(Calendar.MONTH, -1);
            int maxWeekOfMonth = jcalendar.getActualMaximum(Calendar.WEEK_OF_MONTH);

            if(maxWeekOfMonth + weekOfMonth == forceDcToDateWeekOfMonth) {
               month = jcalendar.get(Calendar.MONTH);
               year = getAstronomicalYear(jcalendar);
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
         return getTimestamp(getAstronomicalYear(jcalendar), jcalendar.get(Calendar.MONTH) + 1,
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
      // a bare GregorianCalendar starts the week wherever the JVM default locale says, which
      // is unrelated to the week.start this processor (and every sibling method) uses. The
      // set(DAY_OF_WEEK, getFirstDayOfWeek()) rewind below then landed in the wrong week and
      // the year could come out off by one.
      calendar.setFirstDayOfWeek(firstDay);
      calendar.setMinimalDaysInFirstWeek(7);
      calendar.setTime(new Date(time));
      calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
      int weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH);

      if(weekOfMonth != forceDcToDateWeekOfMonth) {
         calendar.set(Calendar.DATE, 1);
         calendar.add(Calendar.MONTH, -1);

         if(calendar.getActualMaximum(Calendar.WEEK_OF_MONTH) + weekOfMonth == forceDcToDateWeekOfMonth) {
            calendar.set(Calendar.MONTH, 0);

            return getTimestamp(getAstronomicalYear(calendar), calendar.get(Calendar.MONTH) + 1,
               1, 0, 0, 0);
         }
      }

      Timestamp month = getMonthOfFullWeek();
      jcalendar.setTime(month);
      int monthOfYear = jcalendar.get(Calendar.MONTH);

      if(monthOfYear != 0) {
         jcalendar.set(Calendar.MONTH, 0);
         return getTimestamp(getAstronomicalYear(jcalendar), jcalendar.get(Calendar.MONTH) + 1,
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
      // Bug #77463, a pre-1901 group start is built in the hybrid calendar, the same one
      // the fields were read from (see setMillis).
      if(year < HYBRID_CUTOFF_YEAR) {
         return new Timestamp(getHybridMillis(year, month, day, hour, minute, second));
      }

      final ZonedDateTime dateTime = ZonedDateTime.of(year, month, day, hour, minute, second, 0, DEFAULT_ZONE_ID);
      return new Timestamp(dateTime.toInstant().toEpochMilli());
   }

   /**
    * Get the first day of week.
    */
   public final Timestamp getWeek(int year, int month, int day) {
      int weekday = getDayOfWeek();
      int back = (7 - toJodaDay(firstDay) + weekday) % 7;

      // Bug #77463, choose the calendar by the year of the week start, not of the passed
      // date: a week containing 1901-01-01 can start in 1900.
      if(year <= HYBRID_CUTOFF_YEAR &&
         (year < HYBRID_CUTOFF_YEAR || month == 1 && day - back < 1))
      {
         // find the calendar date of the week start by whole-day arithmetic in UTC, which
         // has no offset changes (java.util jumps by up to a day at 1900-01-01T00:00Z in
         // date-line zones) and crosses the 1582 Julian/Gregorian gap correctly (a lenient
         // set of day - back does not), then build its midnight in the default zone
         GregorianCalendar utc = getHybridUtcCalendar();
         utc.clear();
         setAstronomicalFields(utc, year, month, day, 0, 0, 0);
         utc.setTimeInMillis(utc.getTimeInMillis() - back * DAY_MILLIS);
         return new Timestamp(getHybridMillis(getAstronomicalYear(utc),
            utc.get(Calendar.MONTH) + 1, utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0));
      }

      ZonedDateTime dateTime = ZonedDateTime.of(year, month, day, 0, 0, 0, 0, DEFAULT_ZONE_ID);
      dateTime = dateTime.plus(-back, ChronoUnit.DAYS);
      return new Timestamp(dateTime.toInstant().toEpochMilli());
   }

   private int toJodaDay(int javaDay) {
      return (javaDay - 1) == 0 ? 7 : (javaDay - 1);
   }

   /**
    * Get the time of the given (astronomical year, 1 based month) fields in the hybrid
    * Julian/Gregorian calendar of the default zone. The fields are set leniently.
    */
   private long getHybridMillis(int year, int month, int day, int hour, int minute,
                                int second)
   {
      GregorianCalendar cal = getHybridCalendar();
      cal.clear();
      setAstronomicalFields(cal, year, month, day, hour, minute, second);
      return cal.getTimeInMillis();
   }

   /**
    * Set the (astronomical year, 1 based month) fields on a cleared calendar.
    */
   private static void setAstronomicalFields(GregorianCalendar cal, int year, int month, int day,
                                             int hour, int minute, int second)
   {
      if(year <= 0) {
         cal.set(Calendar.ERA, GregorianCalendar.BC);
         cal.set(1 - year, month - 1, day, hour, minute, second);
      }
      else {
         cal.set(year, month - 1, day, hour, minute, second);
      }
   }

   /**
    * Get the hybrid calendar, created on first use so the modern path allocates nothing.
    * Explicitly a GregorianCalendar (not Calendar.getInstance(), which is Buddhist or
    * Japanese in some locales).
    */
   private GregorianCalendar getHybridCalendar() {
      if(hcalendar == null) {
         hcalendar = new GregorianCalendar((TimeZone) DEFAULT_TIME_ZONE.clone());
      }

      return hcalendar;
   }

   /**
    * Get the hybrid calendar in UTC used for whole-day arithmetic, created on first use.
    */
   private GregorianCalendar getHybridUtcCalendar() {
      if(ucalendar == null) {
         ucalendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
      }

      return ucalendar;
   }

   /**
    * Get the year of a calendar as an astronomical year (1 BC = 0), since Calendar.YEAR
    * is relative to the era.
    */
   private static int getAstronomicalYear(Calendar cal) {
      int year = cal.get(Calendar.YEAR);
      return cal.get(Calendar.ERA) == GregorianCalendar.BC ? 1 - year : year;
   }

   /**
    * The first day of week (Tool.getFirstDayOfWeek()) this processor was constructed with.
    * Used by DateRangeRef to detect a stale cached (ThreadLocal) instance after a week.start
    * change.
    */
   int getFirstDay() {
      return firstDay;
   }

   private long time;
   private int year, month, day, weekday, hour, minute, second;
   private ZonedDateTime dateTime = ZonedDateTime.now();
   private Calendar jcalendar = new GregorianCalendar();
   private GregorianCalendar hcalendar; // hybrid calendar for pre-1901 values, lazy
   private GregorianCalendar ucalendar; // hybrid calendar in UTC for day arithmetic, lazy
   private int firstDay;

   // ZoneId.systemDefault creates a clone, so instead cache the object for reuse.
   private static final ZoneId DEFAULT_ZONE_ID = ZoneId.systemDefault();
   private static final TimeZone DEFAULT_TIME_ZONE = TimeZone.getTimeZone(DEFAULT_ZONE_ID);
   // 1901-01-01T00:00Z. From here on java.util and java.time use the same zone offsets
   // (java.util uses the raw offset before 1900-01-01T00:00Z), one year of margin.
   private static final long HYBRID_CUTOFF_MILLIS = -2177452800000L;
   private static final int HYBRID_CUTOFF_YEAR = 1901;
   private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;
   // the astronomical year of a hybrid field read can be negative (2 BC = -1)
   private static final int UNSET_YEAR = Integer.MIN_VALUE;
}
