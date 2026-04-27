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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.asset.DateRangeRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StandardPeriods}.
 *
 * <p>A fixed reference date of 2024-06-15 is used as the end-day so that the
 * computed start-dates are deterministic.</p>
 *
 * <p>Logic trace for {@link StandardPeriods#getStartDate()}:
 * <ol>
 *   <li>Calendar is set to endDay.</li>
 *   <li>{@code resetLowDateLevel} zeroes H/M/S/ms.</li>
 *   <li>{@code setDateToLevelStart} snaps to the beginning of the current
 *       period at that level.</li>
 *   <li>{@code calendar.add(calendarLevel, -preCount * (isQuarter ? 3 : 1))}
 *       rolls back by preCount intervals.</li>
 * </ol>
 */
class StandardPeriodsTest {

   /** Fixed end-day: 2024-06-15 */
   private static final String END_DAY_STR = "2024-06-15";
   private static final String DATE_FORMAT  = "yyyy-MM-dd";

   private StandardPeriods periods;

   @BeforeEach
   void setUp() {
      periods = new StandardPeriods();
      // Use a fixed end-day so all date computations are deterministic
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue(END_DAY_STR);
   }

   // ---- getStartDate — YEAR_INTERVAL ----

   /**
    * preCount=1, YEAR_INTERVAL, endDay=2024-06-15
    * → snap to 2024-01-01, subtract 1 year → 2023-01-01
    */
   @Test
   void getStartDateYearIntervalPreCount1() throws Exception {
      periods.setDateLevel(DateRangeRef.YEAR_INTERVAL);
      periods.setPreCount(1);

      Date start = periods.getStartDate();
      assertEquals("2023-01-01", formatDate(start));
   }

   /**
    * preCount=2, YEAR_INTERVAL, endDay=2024-06-15
    * → snap to 2024-01-01, subtract 2 years → 2022-01-01
    */
   @Test
   void getStartDateYearIntervalPreCount2() throws Exception {
      periods.setDateLevel(DateRangeRef.YEAR_INTERVAL);
      periods.setPreCount(2);

      Date start = periods.getStartDate();
      assertEquals("2022-01-01", formatDate(start));
   }

   // ---- getStartDate — QUARTER_INTERVAL ----

   /**
    * preCount=1, QUARTER_INTERVAL, endDay=2024-06-15 (Q2, i.e. April=month 3 in 0-indexed)
    * → snap to start of Q2 = 2024-04-01, subtract 3 months → 2024-01-01
    */
   @Test
   void getStartDateQuarterIntervalPreCount1() throws Exception {
      periods.setDateLevel(DateRangeRef.QUARTER_INTERVAL);
      periods.setPreCount(1);

      Date start = periods.getStartDate();
      assertEquals("2024-01-01", formatDate(start));
   }

   /**
    * preCount=2, QUARTER_INTERVAL, endDay=2024-06-15
    * → snap to 2024-04-01, subtract 6 months → 2023-10-01
    */
   @Test
   void getStartDateQuarterIntervalPreCount2() throws Exception {
      periods.setDateLevel(DateRangeRef.QUARTER_INTERVAL);
      periods.setPreCount(2);

      Date start = periods.getStartDate();
      assertEquals("2023-10-01", formatDate(start));
   }

   // ---- getStartDate — MONTH_INTERVAL ----

   /**
    * preCount=1, MONTH_INTERVAL, endDay=2024-06-15
    * → snap to 2024-06-01, subtract 1 month → 2024-05-01
    */
   @Test
   void getStartDateMonthIntervalPreCount1() throws Exception {
      periods.setDateLevel(DateRangeRef.MONTH_INTERVAL);
      periods.setPreCount(1);

      Date start = periods.getStartDate();
      assertEquals("2024-05-01", formatDate(start));
   }

   /**
    * preCount=2, MONTH_INTERVAL, endDay=2024-06-15
    * → snap to 2024-06-01, subtract 2 months → 2024-04-01
    */
   @Test
   void getStartDateMonthIntervalPreCount2() throws Exception {
      periods.setDateLevel(DateRangeRef.MONTH_INTERVAL);
      periods.setPreCount(2);

      Date start = periods.getStartDate();
      assertEquals("2024-04-01", formatDate(start));
   }

   /**
    * preCount=6, MONTH_INTERVAL, endDay=2024-06-15
    * → snap to 2024-06-01, subtract 6 months → 2023-12-01
    */
   @Test
   void getStartDateMonthIntervalPreCount6CrossesYearBoundary() throws Exception {
      periods.setDateLevel(DateRangeRef.MONTH_INTERVAL);
      periods.setPreCount(6);

      Date start = periods.getStartDate();
      assertEquals("2023-12-01", formatDate(start));
   }

   // ---- getStartDate — WEEK_INTERVAL ----

   /**
    * preCount=1, WEEK_INTERVAL, endDay=2024-06-15 (a Saturday).
    * setDateToLevelStart snaps to first-day-of-week, then subtract 1 week.
    * The actual day depends on the locale's first day of week (usually Sunday=1).
    * We verify only that the result is exactly 7 days before the snapped week start.
    */
   @Test
   void getStartDateWeekIntervalPreCount1IsSevenDaysBeforeWeekStart() throws Exception {
      periods.setDateLevel(DateRangeRef.WEEK_INTERVAL);
      periods.setPreCount(1);

      // Compute expected: snap endDay to first-day-of-week, then go back 1 week.
      // Use setTime() so all date fields are computed (not user-stamped), then use
      // arithmetic to snap to the week start. Calling set(DAY_OF_WEEK, ...) after
      // set(year, month, day) causes a field-stamp conflict that moves to the *next*
      // occurrence of that day rather than staying in the current week.
      Calendar cal = Calendar.getInstance();
      cal.setTime(new java.text.SimpleDateFormat(DATE_FORMAT).parse(END_DAY_STR));
      cal.set(Calendar.HOUR_OF_DAY, 0);
      cal.set(Calendar.MINUTE, 0);
      cal.set(Calendar.SECOND, 0);
      cal.set(Calendar.MILLISECOND, 0);
      int daysToWeekStart = (cal.get(Calendar.DAY_OF_WEEK) - cal.getFirstDayOfWeek() + 7) % 7;
      cal.add(Calendar.DAY_OF_MONTH, -daysToWeekStart);
      cal.add(Calendar.WEEK_OF_YEAR, -1);
      Date expected = cal.getTime();

      Date start = periods.getStartDate();
      assertEquals(formatDate(expected), formatDate(start));
   }

   // ---- getStartDate — DAY_INTERVAL ----

   /**
    * preCount=3, DAY_INTERVAL, endDay=2024-06-15
    * DAY: setDateToLevelStart is a no-op at day granularity (only resets H/M/S/ms).
    * subtract 3 days → 2024-06-12
    */
   @Test
   void getStartDateDayIntervalPreCount3() throws Exception {
      periods.setDateLevel(DateRangeRef.DAY_INTERVAL);
      periods.setPreCount(3);

      Date start = periods.getStartDate();
      assertEquals("2024-06-12", formatDate(start));
   }

   /**
    * preCount=1, DAY_INTERVAL, endDay=2024-06-15 → 2024-06-14
    */
   @Test
   void getStartDateDayIntervalPreCount1() throws Exception {
      periods.setDateLevel(DateRangeRef.DAY_INTERVAL);
      periods.setPreCount(1);

      Date start = periods.getStartDate();
      assertEquals("2024-06-14", formatDate(start));
   }

   // ---- getRuntimeEndDay ----

   @Test
   void getRuntimeEndDayReturnsSetEndDayWhenNotTodayAsEndDay() throws Exception {
      // endDay has been set to 2024-06-15 and toDayAsEndDay=false in setUp()
      Date endDay = periods.getRuntimeEndDay();
      assertNotNull(endDay);
      assertEquals(END_DAY_STR, formatDate(endDay));
   }

   @Test
   void getRuntimeEndDayReturnsTodayWhenEndDayIsNull() {
      StandardPeriods p = new StandardPeriods();
      // Default: toDayAsEndDay=true and endDay=null → getRuntimeEndDay returns today
      Date endDay = p.getRuntimeEndDay();
      assertNotNull(endDay);

      // Should be very close to "now"
      long diff = Math.abs(endDay.getTime() - System.currentTimeMillis());
      assertTrue(diff < 60_000L, "Expected getRuntimeEndDay() to be close to now");
   }

   // ---- clone() — DynamicValue fields are independently copied ----

   @Test
   void cloneProducesIndependentPreCount() {
      periods.setDateLevel(DateRangeRef.MONTH_INTERVAL);
      periods.setPreCount(3);

      StandardPeriods cloned = periods.clone();
      assertNotNull(cloned);
      assertEquals(3, cloned.getPreCount());

      // Mutating the clone's preCount should not affect the original
      cloned.setPreCount(99);
      assertEquals(3, periods.getPreCount());
      assertEquals(99, cloned.getPreCount());
   }

   @Test
   void cloneProducesIndependentDateLevel() {
      periods.setDateLevel(DateRangeRef.YEAR_INTERVAL);

      StandardPeriods cloned = periods.clone();
      cloned.setDateLevel(DateRangeRef.DAY_INTERVAL);

      assertEquals(DateRangeRef.YEAR_INTERVAL, periods.getDateLevel());
      assertEquals(DateRangeRef.DAY_INTERVAL, cloned.getDateLevel());
   }

   @Test
   void clonePreservesEndDay() throws Exception {
      StandardPeriods cloned = periods.clone();
      assertEquals(END_DAY_STR, formatDate(cloned.getRuntimeEndDay()));
   }

   // ---- equals(Object) ----

   @Test
   void equalsReturnsTrueForSameFields() {
      // Use setPreCountValue / setDateLevelValue so that DynamicValue.equals()
      // (which compares the design-time string) sees the same values.
      periods.setPreCountValue("3");
      periods.setDateLevelValue(String.valueOf(DateRangeRef.MONTH_INTERVAL));

      StandardPeriods other = new StandardPeriods();
      other.setToDayAsEndDay(false);
      other.setEndDateValue(END_DAY_STR);
      other.setPreCountValue("3");
      other.setDateLevelValue(String.valueOf(DateRangeRef.MONTH_INTERVAL));

      assertEquals(periods, other);
   }

   @Test
   void equalsReturnsFalseForDifferentPreCount() {
      periods.setPreCountValue("1");
      periods.setDateLevelValue(String.valueOf(DateRangeRef.MONTH_INTERVAL));

      StandardPeriods other = new StandardPeriods();
      other.setToDayAsEndDay(false);
      other.setEndDateValue(END_DAY_STR);
      other.setPreCountValue("10");
      other.setDateLevelValue(String.valueOf(DateRangeRef.MONTH_INTERVAL));

      assertNotEquals(periods, other);
   }

   @Test
   void equalsReturnsFalseForDifferentDateLevel() {
      periods.setPreCountValue("2");
      periods.setDateLevelValue(String.valueOf(DateRangeRef.YEAR_INTERVAL));

      StandardPeriods other = new StandardPeriods();
      other.setToDayAsEndDay(false);
      other.setEndDateValue(END_DAY_STR);
      other.setPreCountValue("2");
      other.setDateLevelValue(String.valueOf(DateRangeRef.DAY_INTERVAL));

      assertNotEquals(periods, other);
   }

   @Test
   void equalsReturnsFalseForNonStandardPeriods() {
      assertNotEquals(periods, "not a StandardPeriods");
   }

   // ---- helper ----

   private String formatDate(Date date) throws Exception {
      return new SimpleDateFormat(DATE_FORMAT).format(date);
   }
}
