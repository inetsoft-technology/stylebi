/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.test.*;
import inetsoft.util.CoreTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CalcUtilTest {

   @Test
   void testCalculateBasisDayCountFraction() {
      Date startDate = toDate("2023-01-01T00:00:00");
      Date endDate = toDate("2023-12-31T00:00:00");

      // Test case 1: US_NASD_30_360 basis
      int basis = 0; // US_NASD_30_360
      assertEquals(1.0, CalcUtil.calculateBasisDayCountFraction(startDate, endDate, basis),
                   0.0001, "US_NASD_30_360 basis failed");

      // Test case 2: ACTUAL_ACTUAL basis
      basis = 1; // ACTUAL_ACTUAL
      assertEquals(0.9973, CalcUtil.calculateBasisDayCountFraction(startDate, endDate, basis),
                   0.0001, "ACTUAL_ACTUAL basis failed");

      // Test case 3: ACTUAL_360 basis
      basis = 2; // ACTUAL_360
      assertEquals(1.0111, CalcUtil.calculateBasisDayCountFraction(startDate, endDate, basis),
                   0.0001, "ACTUAL_360 basis failed");

      // Test case 4: ACTUAL_365 basis
      basis = 3; // ACTUAL_365
      assertEquals(0.9973, CalcUtil.calculateBasisDayCountFraction(startDate, endDate, basis),
                   0.0001, "ACTUAL_365 basis failed");

      // Test case 5: EUROPEAN_30_360 basis
      basis = 4; // EUROPEAN_30_360
      assertEquals(0.9972, CalcUtil.calculateBasisDayCountFraction(startDate, endDate, basis),
                   0.0001, "EUROPEAN_30_360 basis failed");

      // Test case 6: Date range within a leap year
      startDate = toDate("2024-01-01T00:00:00");
      endDate = toDate("2024-12-31T00:00:00");
      basis = 1; // ACTUAL_ACTUAL

      double result = CalcUtil.calculateBasisDayCountFraction(startDate, endDate, basis);
      assertEquals(0.9973, result, 0.0001,
                   "ACTUAL_ACTUAL basis failed for leap year");

      // Test case 7: Date range spanning multiple years
      startDate = toDate("2022-01-01T00:00:00");
      endDate = toDate("2024-12-31T00:00:00");
      result = CalcUtil.calculateBasisDayCountFraction(startDate, endDate, 1);
      assertTrue(result > 2.99 && result < 3.01,
                 "ACTUAL_ACTUAL failed for multi-year range");

      // Test case 8: Start date after end date
      startDate = toDate("2024-12-31T00:00:00");
      endDate = toDate("2024-01-01T00:00:00");
      result = CalcUtil.calculateBasisDayCountFraction(startDate, endDate, 1);
      assertEquals(-0.9973, result, 0.0001,
                   "ACTUAL_ACTUAL failed for reversed date range");
   }

   @Test
   void testGetDayCountBasisDays() {
      // Test case 1: US_NASD_30_360 basis
      Date startDate = toDate("2023-01-01T00:00:00");
      Date endDate = toDate("2023-12-31T00:00:00");
      int basis = 0; // US_NASD_30_360
      assertEquals(360, CalcUtil.getDayCountBasisDays(startDate, endDate, basis),
                   "US_NASD_30_360 basis failed");

      // Test case 2: ACTUAL_ACTUAL basis
      Date startDate1 = toDate("2023-01-01T00:00:00");
      Date endDate1 = toDate("2023-12-31T00:00:00");
      basis = 1; // ACTUAL_ACTUAL
      assertEquals(364, CalcUtil.getDayCountBasisDays(startDate1, endDate1, basis),
                   "ACTUAL_ACTUAL basis failed");

      // Test case 3: ACTUAL_360 basis
      Date startDate2 = toDate("2023-01-01T00:00:00");
      Date endDate2 = toDate("2023-12-31T00:00:00");
      basis = 2; // ACTUAL_360
      assertEquals(364, CalcUtil.getDayCountBasisDays(startDate2, endDate2, basis),
                   "ACTUAL_360 basis failed");

      // Test case 4: ACTUAL_365 basis
      Date startDate3 = toDate("2023-01-01T00:00:00");
      Date endDate3 = toDate("2023-12-31T00:00:00");
      basis = 3; // ACTUAL_365
      assertEquals(364, CalcUtil.getDayCountBasisDays(startDate3, endDate3, basis),
                   "ACTUAL_365 basis failed");

      // Test case 5: EUROPEAN_30_360 basis
      basis = 4; // EUROPEAN_30_360
      assertEquals(360, CalcUtil.getDayCountBasisDays(startDate, endDate, basis),
                   "EUROPEAN_30_360 basis failed");

      // Test case 6: Invalid date range
      Date invalidStartDate = toDate("2024-01-01T00:00:00");
      Date invalidEndDate = toDate("2023-01-01T00:00:00");
      Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcUtil.getDayCountBasisDays(invalidStartDate, invalidEndDate, 0);
      });
      assertEquals("Start Date falls after the End Date", exception.getMessage());
   }

   @Test
   void testGetDayCountBasisYear() {
      // Test case 1: US_NASD_30_360 basis
      int year = 2023;
      int basis = 0; // US_NASD_30_360
      assertEquals(360, CalcUtil.getDayCountBasisYear(year, basis),
                   "US_NASD_30_360 basis failed");

      // Test case 2: ACTUAL_ACTUAL basis (non-leap year)
      basis = 1; // ACTUAL_ACTUAL
      assertEquals(365, CalcUtil.getDayCountBasisYear(year, basis),
                   "ACTUAL_ACTUAL basis failed for non-leap year");

      // Test case 3: ACTUAL_ACTUAL basis (leap year)
      year = 2024; // Leap year
      assertEquals(366, CalcUtil.getDayCountBasisYear(year, basis),
                   "ACTUAL_ACTUAL basis failed for leap year");

      // Test case 4: ACTUAL_360 basis
      year = 2023;
      basis = 2; // ACTUAL_360
      assertEquals(360, CalcUtil.getDayCountBasisYear(year, basis),
                   "ACTUAL_360 basis failed");

      // Test case 5: ACTUAL_365 basis
      basis = 3; // ACTUAL_365
      assertEquals(365, CalcUtil.getDayCountBasisYear(year, basis),
                   "ACTUAL_365 basis failed");

      // Test case 6: EUROPEAN_30_360 basis
      basis = 4; // EUROPEAN_30_360
      assertEquals(360, CalcUtil.getDayCountBasisYear(year, basis),
                   "EUROPEAN_30_360 basis failed");
   }

   @Test
   void testGetSerialDays() {
      // Test case 1: Valid date range
      Date startDate = toDate("2023-01-01T00:00:00");
      Date endDate = toDate("2023-12-31T00:00:00");
      assertEquals(364, CalcUtil.getSerialDays(startDate, endDate), "Valid date range failed");

      // Test case 2: Same start and end date
      startDate = toDate("2023-01-01T00:00:00");
      endDate = toDate("2023-01-01T00:00:00");
      assertEquals(0, CalcUtil.getSerialDays(startDate, endDate), "Same start and end date failed");

      // Test case 3: Start date after end date
      startDate = toDate("2023-12-31T00:00:00");
      endDate = toDate("2023-01-01T00:00:00");
      assertEquals(-364, CalcUtil.getSerialDays(startDate, endDate), "Start date after end date failed");

      // Test case 4: SQL Date input
      startDate = java.sql.Date.valueOf("2023-01-01");
      endDate = java.sql.Date.valueOf("2023-12-31");
      assertEquals(364, CalcUtil.getSerialDays(startDate, endDate), "SQL Date input failed");
   }

   @Test
   void testSplit2D() {
      // Test case 1: Input is a 1D array
      Object input1 = new Object[]{ 1, 2, 3 };
      Object[] result1 = CalcUtil.split2D(input1);
      assertNotNull(result1, "Result should not be null for 1D array input");
      assertEquals(3, result1.length, "Result should have one row for 1D array input");

      // Test case 2: Input is a 2D array
      Object input2 = new Object[]{ new Object[]{ 1, 2 }, new Object[]{ 3, 4 } };
      Object[] result2 = CalcUtil.split2D(input2);
      assertNotNull(result2, "Result should not be null for 2D array input");
      assertEquals(2, result2.length, "Result should have two rows for 2D array input");

      // Test case 3: Input contains a nested array (GraalJS arrays surface as Object[]/List)
      Object[] nativeArray = new Object[]{ 1, 2, 3 };
      Object input3 = new Object[]{ nativeArray };
      Object[] result3 = CalcUtil.split2D(input3);
      assertNotNull(result3, "Result should not be null for input containing nested array");
      assertTrue(result3[0] instanceof Object[], "Nested array should be converted to Object[]");

      // Test case 4: Doubly-nested array
      Object[] nestedNativeArray = new Object[]{ new Object[]{ 4, 5 } };
      Object input4 = new Object[]{ nestedNativeArray };
      Object[] result4 = CalcUtil.split2D(input4);
      assertNotNull(result4, "Result should not be null for nested array input");
      assertTrue(result4[0] instanceof Object[], "Outer array should be converted to Object[]");
      assertTrue(((Object[]) result4[0])[0] instanceof Object[],
                 "Inner array should be converted to Object[]");

      // Test case 5: Null input
      Object input5 = null;
      Object[] result5 = CalcUtil.split2D(input5);
      assertNotNull(result5, "Result should not be null for null input");
      assertEquals(0, result5.length, "Result should be an empty array for null input");

      // Test case 6: Empty array input
      Object input6 = new Object[]{};
      Object[] result6 = CalcUtil.split2D(input6);
      assertNotNull(result6, "Result should not be null for empty array input");
      assertEquals(0, result6.length, "Result should be an empty array for empty array input");
   }

   @Test
   void testConvertToDoubleArray2D() {
      // Test case 1: 1D array input
      Object[] input1 = { 1, 2, 3 };
      double[][] result1 = CalcUtil.convertToDoubleArray2D(input1);
      assertNotNull(result1, "Result should not be null for 1D array input");
      assertEquals(1, result1.length, "Result should have one row for 1D array input");
      assertArrayEquals(new double[]{ 1.0, 2.0, 3.0 }, result1[0], "1D array conversion failed");

      // Test case 2: 2D array input
      Object[] input2 = { new Object[]{ 1, 2 }, new Object[]{ 3, 4 } };
      double[][] result2 = CalcUtil.convertToDoubleArray2D(input2);
      assertNotNull(result2, "Result should not be null for 2D array input");
      assertEquals(2, result2.length, "Result should have two rows for 2D array input");
      assertArrayEquals(new double[]{ 1.0, 2.0 }, result2[0], "First row conversion failed");
      assertArrayEquals(new double[]{ 3.0, 4.0 }, result2[1], "Second row conversion failed");

      // Test case 3: Empty array input
      Object[] input3 = {};
      double[][] result3 = CalcUtil.convertToDoubleArray2D(input3);
      assertNotNull(result3, "Result should not be null for empty array input");
      assertEquals(0, result3.length, "Result should be an empty array for empty array input");

      // Test case 4: Null input
      Object[] input4 = null;
      double[][] result4 = CalcUtil.convertToDoubleArray2D(input4);
      assertNotNull(result4, "Result should not be null for null input");
      assertEquals(0, result4.length, "Result should be an empty array for null input");
   }

   // Bug #77450, the 1899-12-31 serial start is in local mean time (+8:05:43) in java.time
   @Test
   void serialValueInShanghaiIsNotShiftedBySerialStart() {
      runInZone("Asia/Shanghai", () -> {
         assertEquals(45291, CalcTextData.value("1/1/2024"));
         assertEquals(1, CalcTextData.value("1/1/1900"));
         assertEquals(45291, CalcDateTime.datevalue(hybridDate(2024, 1, 1)));
      });
   }

   // Bug #77450, Asia/Kolkata is +5:21:10 in java.time before 1901 vs +5:30 in java.util
   @Test
   void serialDaysBefore1901InKolkata() {
      runInZone("Asia/Kolkata", () -> {
         Date d1882 = hybridDate(1882, 6, 1);
         Date d2024 = hybridDate(2024, 1, 1);
         assertEquals(51713, CalcUtil.getSerialDays(d1882, d2024));
         assertEquals(51713, CalcUtil.getSerialDays(new java.sql.Date(d1882.getTime()),
                                                    new java.sql.Date(d2024.getTime())));
         assertEquals(1, CalcTextData.value("1/1/1900"));
      });
   }

   // Bug #77450, a range across the 1582 Gregorian cutover counts real days
   @Test
   void serialDaysAcrossGregorianCutover() {
      Date d1500 = hybridDate(1500, 3, 1);
      Date d1600 = hybridDate(1600, 3, 1);
      assertEquals(36515, CalcUtil.getSerialDays(d1500, d1600));
      assertEquals(36515, CalcUtil.getSerialDays(new java.sql.Date(d1500.getTime()),
                                                 new java.sql.Date(d1600.getTime())));
      // Julian-only leap day
      java.sql.Date leap = new java.sql.Date(hybridDate(1300, 2, 29).getTime());
      assertEquals(1, CalcUtil.getSerialDays(leap, hybridDate(1300, 3, 1)));
   }

   @Test
   void serialDaysForModernDatesUnchanged() {
      runInZone("America/New_York", () -> {
         // across DST, the time of day is ignored
         assertEquals(1, CalcUtil.getSerialDays(toDate("2021-03-13T23:30:00"),
                                                toDate("2021-03-14T00:30:00")));
         assertEquals(365, CalcUtil.getSerialDays(toDate("2023-01-01T00:00:00"),
                                                  toDate("2024-01-01T23:59:59")));
         assertEquals(-31, CalcUtil.getSerialDays(toDate("2024-02-01T00:00:00"),
                                                  toDate("2024-01-01T00:00:00")));
         assertEquals(45291, CalcTextData.value("1/1/2024"));
      });
   }

   // Bug #77450, every day from 1950 to 2050 counts the same as java.time, and CALC.value is
   // not shifted in other zones where the 1899 serial start is in local mean time
   @Test
   void serialDaysMatchJavaTimeForModernDates() {
      for(String zone : new String[] { "Europe/Warsaw", "Africa/Cairo", "Australia/Lord_Howe",
                                       "Pacific/Apia" })
      {
         runInZone(zone, () -> {
            ZoneId zoneId = ZoneId.systemDefault();
            LocalDateTime start = LocalDateTime.of(1950, 1, 1, 23, 30);
            Date startDate = Date.from(start.atZone(zoneId).toInstant());

            for(LocalDateTime day = start; day.getYear() <= 2050; day = day.plusDays(1)) {
               Date date = Date.from(day.atZone(zoneId).toInstant());
               long expected = ChronoUnit.DAYS.between(
                  start.toLocalDate(), date.toInstant().atZone(zoneId).toLocalDate());
               assertEquals(expected, CalcUtil.getSerialDays(startDate, date), zone + " " + day);
            }

            assertEquals(45291, CalcTextData.value("1/1/2024"), zone);
            assertEquals(1, CalcTextData.value("1/1/1900"), zone);
         });
      }
   }

   static Date hybridDate(int year, int month, int day) {
      GregorianCalendar cal = new GregorianCalendar();
      cal.clear();
      cal.set(year, month - 1, day);
      return cal.getTime();
   }

   /**
    * Run a test with the default time zone, the cached serial start, and the shared thread
    * local calendar in the zone, and restore them after.
    */
   static void runInZone(String zone, Runnable test) {
      TimeZone oldZone = TimeZone.getDefault();
      TimeZone oldCalendarZone = CoreTool.calendar.get().getTimeZone();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone(zone));
         CoreTool.calendar.get().setTimeZone(TimeZone.getDefault());
         ReflectionTestUtils.setField(CalcUtil.class, "date1900", null);
         test.run();
      }
      finally {
         TimeZone.setDefault(oldZone);
         CoreTool.calendar.get().setTimeZone(oldCalendarZone);
         ReflectionTestUtils.setField(CalcUtil.class, "date1900", null);
      }
   }

   /**
    * @param localDateTime an ISO-8601 datetime string, e.g. 2007-12-03T10:15:30
    *
    * @return the corresponding date in the utc time zone.
    */
   private java.util.Date toDate(String localDateTime) {
      return java.util.Date.from(LocalDateTime.parse(localDateTime)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant());
   }
}