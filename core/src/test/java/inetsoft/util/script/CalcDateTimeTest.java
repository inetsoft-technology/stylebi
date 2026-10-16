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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.*;
import java.time.temporal.IsoFields;
import java.util.Date;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CalcDateTimeTest {

   @Test
   public void testDatevalue() {
      // Test Case 1: Valid date after 1900
      Date validDate = toDate("2023-10-01T00:00");
      assertEquals(45200, CalcDateTime.datevalue(validDate), 1);

      // Test Case 2: Null date
      assertEquals(-1, CalcDateTime.datevalue(null));

      // Test Case 3: java.sql.Date instance
      java.sql.Date sqlDate = new java.sql.Date(validDate.getTime());
      assertEquals(45200, CalcDateTime.datevalue(sqlDate), 1);

      // Test Case 4: Invalid object type
      try {
         CalcDateTime.datevalue("InvalidType");
         fail("Expected RuntimeException for invalid type");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("Date is required"));
      }
   }

   // Bug #77450, java.time reads dates before 1901 in local mean time
   @Test
   void datevalueBefore1901InKolkata() {
      CalcUtilTest.runInZone("Asia/Kolkata", () -> {
         Date d1882 = CalcUtilTest.hybridDate(1882, 6, 1);
         assertEquals(-6422, CalcDateTime.datevalue(d1882));
         assertEquals(-6422, CalcDateTime.datevalue(new java.sql.Date(d1882.getTime())));
         assertEquals(1, CalcDateTime.datevalue(CalcUtilTest.hybridDate(1900, 1, 1)));
         assertEquals(45199, CalcDateTime.datevalue(CalcUtilTest.hybridDate(2023, 10, 1)));
      });
   }

   @Test
   public void testDay() {
      // Test Case 1: Valid date
      Date validDate = toDate("2023-10-15T00:00");
      assertEquals(15, CalcDateTime.day(validDate));

      // Test Case 2: Null date
      assertEquals(-1, CalcDateTime.day(null));

      // Test Case 3: java.sql.Date instance
      java.sql.Date sqlDate = new java.sql.Date(validDate.getTime());
      assertEquals(15, CalcDateTime.day(sqlDate));

      // Test Case 4: Invalid object type
      try {
         CalcDateTime.day("InvalidType");
         fail("Expected RuntimeException for invalid type");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("Date is required"));
      }
   }

   @Test
   public void testDayOfYear() {
      // Test Case 1: Valid date
      Date validDate = toDate("2023-03-15T00:00");
      assertEquals(74, CalcDateTime.dayofyear(validDate));

      // Test Case 2: Null date
      assertEquals(-1, CalcDateTime.dayofyear(null));

      // Test Case 3: java.sql.Date instance
      java.sql.Date sqlDate = new java.sql.Date(validDate.getTime());
      assertEquals(74, CalcDateTime.dayofyear(sqlDate));

      // Test Case 4: Invalid object type
      try {
         CalcDateTime.dayofyear("InvalidType");
         fail("Expected RuntimeException for invalid type");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("Date is required"));
      }
   }

   @Test
   public void testDays360() {
      // Test Case 1: Valid dates using U.S. (NASD) method
      Date startDate = toDate("2023-01-01T00:00");
      Date endDate = toDate("2023-12-31T00:00");
      assertEquals(360, CalcDateTime.days360(startDate, endDate, false));//bug #71413

      // Test Case 2: Valid dates using European method
      assertEquals(359, CalcDateTime.days360(startDate, endDate, true));//bug #71413

      // Test Case 3: Start date is after end date
      assertEquals(-1, CalcDateTime.days360(endDate, startDate, false));

      // Test Case 4: Null start date
      assertEquals(-1, CalcDateTime.days360(null, endDate, false));

      // Test Case 5: Null end date
      assertEquals(-1, CalcDateTime.days360(startDate, null, false));

      // Test Case 6: Both dates are null
      assertEquals(-1, CalcDateTime.days360(null, null, false));

      // Test Case 7: Invalid object type for start date
      try {
         CalcDateTime.days360("InvalidType", endDate, false);
         fail("Expected RuntimeException for invalid start date type");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("Date is required"));
      }

      // Test Case 8: Invalid object type for end date
      try {
         CalcDateTime.days360(startDate, "InvalidType", false);
         fail("Expected RuntimeException for invalid end date type");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("Date is required"));
      }
   }

   @Test
   public void testEdate() {
      // Test Case 1: Valid date, positive months
      Date validDate = toDate("2023-01-31T00:00");
      Object result = CalcDateTime.edate(validDate, 1);
      assertEquals(toDate("2023-02-28T00:00"), result);

      // Test Case 2: Valid date, negative months
      result = CalcDateTime.edate(validDate, -1);
      assertEquals(toDate("2022-12-31T00:00"), result);

      // Test Case 3: Null date
      result = CalcDateTime.edate(null, 1);
      assertNull(result);

      // Test Case 4: End of month adjustment
      validDate = toDate("2023-01-31T00:00");
      result = CalcDateTime.edate(validDate, 2);
      assertEquals(toDate("2023-03-31T00:00"), result);

      // Test Case 5: Invalid object type
      try {
         CalcDateTime.edate("InvalidType", 1);
         fail("Expected RuntimeException for invalid type");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("Date is required"));
      }
   }

   @Test
   public void testEomonth() {
      // Test Case 1: Valid date, positive months
      Date validDate = toDate("2023-01-31T00:00");
      Object result = CalcDateTime.eomonth(validDate, 1);
      assertEquals(toDate("2023-02-28T00:00"), result);

      // Test Case 2: Valid date, negative months
      result = CalcDateTime.eomonth(validDate, -1);
      assertEquals(toDate("2022-12-31T00:00"), result);

      // Test Case 3: Null date
      result = CalcDateTime.eomonth(null, 1);
      assertNull(result);

      // Test Case 4: End of month adjustment
      validDate = toDate("2023-01-31T00:00");
      result = CalcDateTime.eomonth(validDate, 2);
      assertEquals(toDate("2023-03-31T00:00"), result);

      // Test Case 5: Invalid object type
      try {
         CalcDateTime.eomonth("InvalidType", 1);
         fail("Expected RuntimeException for invalid type");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("Date is required"));
      }
   }

   @Test
   public void testHour() {
      // Test Case 1: Valid date
      Date validDate = toDate("2023-10-15T15:30");
      assertEquals(15, CalcDateTime.hour(validDate));

      // Test Case 2: Null date
      assertEquals(-1, CalcDateTime.hour(null));

      // Test Case 3: java.sql.Date instance
      java.sql.Date sqlDate = new java.sql.Date(validDate.getTime());
      assertEquals(15, CalcDateTime.hour(sqlDate));

      // Test Case 4: Invalid object type
      try {
         CalcDateTime.hour("InvalidType");
         fail("Expected RuntimeException for invalid type");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("Date is required"));
      }
   }

   @Test
   public void testMinute() {
      // Test Case 1: Valid date
      Date validDate = toDate("2023-10-15T15:45");
      assertEquals(45, CalcDateTime.minute(validDate));

      // Test Case 2: Null date
      assertEquals(-1, CalcDateTime.minute(null));

      // Test Case 3: java.sql.Date instance
      java.sql.Date sqlDate = new java.sql.Date(validDate.getTime());
      assertEquals(45, CalcDateTime.minute(sqlDate));

      // Test Case 4: Invalid object type
      try {
         CalcDateTime.minute("InvalidType");
         fail("Expected RuntimeException for invalid type");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("Date is required"));
      }
   }

   @Test
   public void testMonth() {
      // Test Case 1: Valid date
      Date validDate = toDate("2023-10-15T00:00");
      assertEquals(10, CalcDateTime.month(validDate));

      // Test Case 2: Null date
      assertEquals(-1, CalcDateTime.month(null));

      // Test Case 3: java.sql.Date instance
      java.sql.Date sqlDate = new java.sql.Date(validDate.getTime());
      assertEquals(10, CalcDateTime.month(sqlDate));

      // Test Case 4: Invalid object type
      try {
         CalcDateTime.month("InvalidType");
         fail("Expected RuntimeException for invalid type");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("Date is required"));
      }
   }

   @Test
   public void testQuarter() {
      // Test Case 1: Valid date
      Date validDate = toDate("2023-10-15T00:00");
      assertEquals(4, CalcDateTime.quarter(validDate));

      // Test Case 2: Null date
      assertEquals(1, CalcDateTime.quarter(null));

      // Test Case 3: java.sql.Date instance
      java.sql.Date sqlDate = new java.sql.Date(validDate.getTime());
      assertEquals(4, CalcDateTime.quarter(sqlDate));

      // Test Case 4: Invalid object type
      try {
         CalcDateTime.quarter("InvalidType");
         fail("Expected RuntimeException for invalid type");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("Date is required"));
      }
   }

   @Test
   public void testNetworkDays() {
      //Test Case 1: No holidays
      Date startDate = toDate("2023-10-01T00:00");
      Date endDate = toDate("2023-10-10T00:00");
      assertEquals(7, CalcDateTime.networkdays(startDate, endDate, null));

      //Test Case 2: With holidays
      Object[] holidays = new Object[]{
         toDate("2023-10-02T00:00")
      };
      assertEquals(6, CalcDateTime.networkdays(startDate, endDate, holidays));

      //Test Case 3: Start date is the same as end date
      startDate = toDate("2023-11-01T00:00");
      assertEquals(1, CalcDateTime.networkdays(startDate, startDate, null));

      //Test Case 4: Start date is after end date
      startDate = toDate("2023-10-10T00:00");
      endDate = toDate("2023-10-01T00:00");
      assertEquals(-7, CalcDateTime.networkdays(startDate, endDate, null)); // Negative working days

      //Test Case 5: Null dates
      assertEquals(-1, CalcDateTime.networkdays(null, null, null)); // Invalid input
   }

   @Test
   public void testNowToday() {
      Object result = CalcDateTime.now();
      assertNotNull(result);
      assertTrue(result instanceof Date);

      result = CalcDateTime.today();
      assertNotNull(result);
      assertTrue(result instanceof Date);
   }

   @Test
   public void testSecond() {
      // Test Case 1: Valid date
      Date validDate = toDate("2023-10-15T15:45:30");
      assertEquals(30, CalcDateTime.second(validDate));

      // Test Case 2: Null date
      assertEquals(-1, CalcDateTime.second(null));

      // Test Case 3: java.sql.Date instance
      java.sql.Date sqlDate = new java.sql.Date(validDate.getTime());
      assertEquals(30, CalcDateTime.second(sqlDate));

      // Test Case 4: Invalid object type
      try {
         CalcDateTime.second("InvalidType");
         fail("Expected RuntimeException for invalid type");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("Date is required"));
      }
   }

   @Test
   public void testTime() {
      // Test Case 1: Valid time within range
      double result = CalcDateTime.time(15, 30, 45);
      assertEquals(0.6463541, result, 0.0000001);

      // Test Case 2: Time values exceeding range
      result = CalcDateTime.time(25, 70, 80);
      assertEquals(0.0912037, result, 0.0000001); //bug #71436

      // Test Case 3: Time values at boundary
      result = CalcDateTime.time(0, 0, 0);
      assertEquals(0.0, result, 0.0000001);

      result = CalcDateTime.time(23, 59, 59);
      assertEquals(0.9999884, result, 0.0000001);
   }

   @Test
   public void testTimevalue() {
      // Test Case 1: Valid date
      Date validDate = toDate("2023-10-15T15:45:30");
      double result = CalcDateTime.timevalue(validDate);
      assertEquals(0.6565972, result, 0.0000001);

      // Test Case 2: Null date
      result = CalcDateTime.timevalue(null);
      assertEquals(-1, result);

      // Test Case 3: java.sql.Date instance
      java.sql.Date sqlDate = new java.sql.Date(validDate.getTime());
      result = CalcDateTime.timevalue(sqlDate);
      assertEquals(0.6565972, result, 0.0000001);

      // Test Case 4: Invalid object type
      try {
         CalcDateTime.timevalue("InvalidType");
         fail("Expected RuntimeException for invalid type");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("Date is required"));
      }
   }

   @Test
   public void testWeekday() {
      // Test Case 1: Default return type (1 for Sunday through 7 for Saturday)
      Date validDate = toDate("2023-10-15T00:00"); // Sunday
      assertEquals(1, CalcDateTime.weekday(validDate, null));

      // Test Case 2: Return type 2 (1 for Monday through 7 for Sunday)
      assertEquals(7, CalcDateTime.weekday(validDate, 2));

      // Test Case 3: Return type 3 (0 for Monday through 6 for Sunday)
      assertEquals(6, CalcDateTime.weekday(validDate, 3));

      // Test Case 4: Null date
      assertEquals(-1, CalcDateTime.weekday(null, null));

      // Test Case 5: Invalid object type for date
      try {
         CalcDateTime.weekday("InvalidType", null);
         fail("Expected RuntimeException for invalid date type");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("Date is required"));
      }
   }

   @Test
   public void testWeeknum() {
      // Test Case 1: Default return type (week begins on Sunday)
      Date validDate = toDate("2023-10-15T00:00"); // Sunday
      assertEquals(42, CalcDateTime.weeknum(validDate, null));

      // Test Case 2: Return type 2 (week begins on Monday)
      assertEquals(42, CalcDateTime.weeknum(validDate, 2));

      // Test Case 3: Null date
      assertEquals(-1, CalcDateTime.weeknum(null, null));

      // Test Case 4: Invalid object type for date
      try {
         CalcDateTime.weeknum("InvalidType", null);
         fail("Expected RuntimeException for invalid date type");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("Date is required"));
      }
   }

   @Test
   public void testWorkday() {
      // Test Case 1: Positive days, no holidays
      Date startDate = toDate("2023-10-01T00:00");
      Object result = CalcDateTime.workday(startDate, 5, null);
      assertEquals(toDate("2023-10-06T00:00"), result);

      // Test Case 2: Negative days, no holidays
      result = CalcDateTime.workday(startDate, -5, null);
      assertEquals(toDate("2023-09-25T00:00"), result);

      // Test Case 3: Positive days with holidays
      Object[] holidays = new Object[]{
         toDate("2023-10-03T00:00")
      };
      result = CalcDateTime.workday(startDate, 5, holidays);
      assertEquals(toDate("2023-10-09T00:00"), result);

      // Test Case 4: Negative days with holidays
      holidays = new Object[]{
         toDate("2023-09-27T00:00")
      };
      result = CalcDateTime.workday(startDate, -5, holidays);
      assertEquals(toDate("2023-09-22T00:00"), result);

      // Test Case 5: Null start date
      result = CalcDateTime.workday(null, 5, null);
      assertNull(result);

      // Test Case 6: Start date is a weekend
      startDate = toDate("2023-10-07T00:00"); // Saturday
      result = CalcDateTime.workday(startDate, 5, null);
      assertEquals(toDate("2023-10-13T00:00"), result);

      // Test Case 7: Invalid holiday object type
      try {
         Object invalidHolidays = "InvalidType";
         CalcDateTime.workday(startDate, 5, invalidHolidays);
         fail("Expected RuntimeException for invalid holiday type");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("Date is required"));
      }
   }

   @Test
   public void testYear() {
      // Test Case 1: Valid date without interval
      Date validDate = toDate("2023-10-15T00:00");
      assertEquals(2023, CalcDateTime.year(validDate));

      // Test Case 2: Null date
      assertEquals(-1, CalcDateTime.year(null));

      // Test Case 3: Valid date with interval
      Object interval = 10;
      assertEquals(2020, CalcDateTime.year(validDate, interval));

      // Test Case 4: Valid date with interval resulting in rounding
      interval = 5;
      assertEquals(2020, CalcDateTime.year(validDate, interval));
   }

   @Test
   public void testYearfrac() {
      // Test Case 1: Valid dates with US (NASD) 30/360 basis
      Date startDate = toDate("2023-01-01T00:00");
      Date endDate = toDate("2023-12-31T00:00");
      double result = CalcDateTime.yearfrac(startDate, endDate, 0);
      assertEquals(1.0, result, 0.0000001);

      // Test Case 2: Valid dates with Actual/Actual basis
      result = CalcDateTime.yearfrac(startDate, endDate, 1);
      assertEquals(0.9972602, result, 0.0000001);

      // Test Case 3: Valid dates with Actual/360 basis
      result = CalcDateTime.yearfrac(startDate, endDate, 2);
      assertEquals(1.0111111, result, 0.0000001);

      // Test Case 4: Valid dates with Actual/365 basis
      result = CalcDateTime.yearfrac(startDate, endDate, 3);
      assertEquals(0.9972602, result, 0.0000001);

      // Test Case 5: Valid dates with European 30/360 basis
      result = CalcDateTime.yearfrac(startDate, endDate, 4);
      assertEquals(0.9972222, result, 0.0000001);

      // Test Case 6: Invalid basis value
      try {
         CalcDateTime.yearfrac(startDate, endDate, 5);
         fail("Expected RuntimeException for invalid basis value");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("Basis value specified (5) is invalid"));
      }
   }

   @Test
   public void testDateMethod() {
      Date inputDate = toDate("2023-04-15T12:23:24.005");

      // Test Case 1: Valid date with YEAR_DATE_GROUP
      assertEquals(toDate("2023-01-01T00:00"), CalcDateTime.date(inputDate, CalcDateTime.YEAR_DATE_GROUP, 1));

      // Test Case 2: Valid date with QUARTER_DATE_GROUP
      assertEquals(toDate("2023-04-01T00:00"), CalcDateTime.date(inputDate, CalcDateTime.QUARTER_DATE_GROUP, 1));

      // Test Case 3: Valid date with MONTH_DATE_GROUP
      assertEquals(toDate("2023-04-01T00:00"), CalcDateTime.date(inputDate, CalcDateTime.MONTH_DATE_GROUP, 1));

      // Test Case 4: Valid date with DAY_DATE_GROUP
      assertEquals(toDate("2023-04-15T00:00"), CalcDateTime.date(inputDate, CalcDateTime.DAY_DATE_GROUP, 1));

      // Test Case 5: Valid date with HOUR_DATE_GROUP
      assertEquals(toDate("2023-04-15T12:00:00"), CalcDateTime.date(inputDate, CalcDateTime.HOUR_DATE_GROUP, 1));

      // Test Case 6: Valid date with MINUTE_DATE_GROUP
      assertEquals(toDate("2023-04-15T12:23:00"), CalcDateTime.date(inputDate, CalcDateTime.MINUTE_DATE_GROUP, 1));

      // Test Case 7: Valid date with SECOND_DATE_GROUP
      assertEquals(toDate("2023-04-15T12:23:24"), CalcDateTime.date(inputDate, CalcDateTime.SECOND_DATE_GROUP, 1));

      // Test Case 8: Null date
      assertNull(CalcDateTime.date(null, CalcDateTime.YEAR_DATE_GROUP, 1));
   }

   @Test
   public void testMaxMinDate() {
      // Test Case 1: Valid dates
      Object[] dates = new Object[]{
         toDate("2023-10-01T00:00"),
         toDate("2023-10-15T00:00"),
         toDate("2023-10-10T00:00")
      };
      Date result = CalcDateTime.maxDate(dates);
      assertEquals(toDate("2023-10-15T00:00"), CalcDateTime.maxDate(dates));
      assertEquals(toDate("2023-10-01T00:00"), CalcDateTime.minDate(dates));

      // Test Case 2: Empty array
      dates = new Object[]{};
      assertNull(CalcDateTime.maxDate(dates));
      assertNull(CalcDateTime.minDate(dates));

      // Test Case 3: Null input
      assertNull(CalcDateTime.maxDate(null));
      assertNull(CalcDateTime.minDate(null));
   }

   @Test
   public void testFiscalYear() {
      // Test Case 1: Date before fiscal year start
      Date testDate = toDate("2023-03-31T00:00");
      int fiscalYear = CalcDateTime.fiscalyear(testDate, 4, 1, null);
      assertEquals(2022, fiscalYear);

      // Test Case 2: Date on fiscal year start
      testDate = toDate("2023-04-01T00:00");
      fiscalYear = CalcDateTime.fiscalyear(testDate, 4, 1, null);
      assertEquals(2023, fiscalYear);

      // Test Case 3: Date after fiscal year start
      testDate = toDate("2023-04-15T00:00");
      fiscalYear = CalcDateTime.fiscalyear(testDate, 4, 1, null);
      assertEquals(2023, fiscalYear);

      // Test Case 4: Null date
      try {
         CalcDateTime.fiscalyear(null, 4, 1, null);
         fail("Expected RuntimeException for null date");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("date must not be null"));
      }
   }

   @Test
   public void testFiscalQuarter() {
      // Test Case 1: Date in the first fiscal quarter
      Date testDate = toDate("2023-04-15T00:00");
      int fiscalQuarter = CalcDateTime.fiscalquarter(testDate, 4, 1, null);
      assertEquals(1, fiscalQuarter);

      // Test Case 2: Date in the second fiscal quarter
      testDate = toDate("2023-07-15T00:00");
      fiscalQuarter = CalcDateTime.fiscalquarter(testDate, 4, 1, null);
      assertEquals(2, fiscalQuarter);

      // Test Case 3: Date in the third fiscal quarter
      testDate = toDate("2023-10-15T00:00");
      fiscalQuarter = CalcDateTime.fiscalquarter(testDate, 4, 1, null);
      assertEquals(3, fiscalQuarter);

      // Test Case 4: Date in the fourth fiscal quarter
      testDate = toDate("2024-01-15T00:00");
      fiscalQuarter = CalcDateTime.fiscalquarter(testDate, 4, 1, null);
      assertEquals(4, fiscalQuarter);

      // Test Case 5: Null date
      try {
         CalcDateTime.fiscalquarter(null, 4, 1, null);
         fail("Expected RuntimeException for null date");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("date must not be null"));
      }
   }

   @Test
   public void testFiscalMonth() {
      // Test Case 1: Date in the current fiscal year
      Date testDate = toDate("2023-05-15T00:00");
      int fiscalMonth = CalcDateTime.fiscalmonth(testDate, 4, 1, null);
      assertEquals(2, fiscalMonth);

      // Test Case 2: Date in the previous fiscal year
      testDate = toDate("2023-03-15T00:00");
      fiscalMonth = CalcDateTime.fiscalmonth(testDate, 4, 1, null);
      assertEquals(12, fiscalMonth);

      // Test Case 3: Date on the fiscal year start
      testDate = toDate("2023-04-01T00:00");
      fiscalMonth = CalcDateTime.fiscalmonth(testDate, 4, 1, null);
      assertEquals(1, fiscalMonth);

      // Test Case 4: Null date
      try {
         CalcDateTime.fiscalmonth(null, 4, 1, null);
         fail("Expected RuntimeException for null date");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("date must not be null"));
      }
   }

   @Test
   public void testFiscalWeek() {
      // Test Case 1: Date within the fiscal year
      Date testDate = toDate("2023-04-15T00:00");
      int fiscalWeek = CalcDateTime.fiscalweek(testDate, 4, 1, null);
      assertEquals(3, fiscalWeek);

      // Test Case 2: Date before fiscal year start
      testDate = toDate("2023-03-31T00:00");
      fiscalWeek = CalcDateTime.fiscalweek(testDate, 4, 1, null);
      assertEquals(53, fiscalWeek);

      // Test Case 3: Date on fiscal year start
      testDate = toDate("2023-04-01T00:00");
      fiscalWeek = CalcDateTime.fiscalweek(testDate, 4, 1, null);
      assertEquals(1, fiscalWeek);

      // Test Case 4: Date after fiscal year start
      testDate = toDate("2023-04-08T00:00");
      fiscalWeek = CalcDateTime.fiscalweek(testDate, 4, 1, null);
      assertEquals(2, fiscalWeek);

      // Test Case 5: Null date
      try {
         CalcDateTime.fiscalweek(null, 4, 1, null);
         fail("Expected RuntimeException for null date");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("date must not be null"));
      }
   }

   @Test
   public void testFiscalYear445() {
      // Test Case 1: Valid date within a fiscal year
      Date testDate = toDate("2023-04-15T00:00");
      int fiscalYear = CalcDateTime.fiscalyear445(testDate, 2023, 4, 1, null, null);
      assertEquals(2023, fiscalYear);

      // Test Case 2: Date on fiscal year start
      testDate = toDate("2023-04-01T00:00");
      fiscalYear = CalcDateTime.fiscalyear445(testDate, 2023, 4, 1, null, null);
      assertEquals(2023, fiscalYear);

      // Test Case 3: Date in a year with 53 weeks
      testDate = toDate("2023-12-31T00:00");
      Object yearsWith53Weeks = new Object[]{ 2023 };
      fiscalYear = CalcDateTime.fiscalyear445(testDate, 2023, 4, 1, yearsWith53Weeks, null);
      assertEquals(2023, fiscalYear);

      // Test Case 4: Date before fiscal year start
      try {
         CalcDateTime.fiscalyear445(toDate("2023-03-31T00:00"), 2023, 4, 1, null, null);
         fail("Expected IllegalArgumentException for Date before fiscal year start");
      }
      catch(IllegalArgumentException e) {
         assertTrue(e.getMessage().matches(".*Specified date .* is before fiscal calendar start.*"));
      }

      // Test Case 5: Null date
      try {
         CalcDateTime.fiscalyear445(null, 2023, 4, 1, null, null);
         fail("Expected RuntimeException for null date");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("date must not be null"));
      }
   }

   @Test
   public void testFiscalQuarter445() {
      // Test Case 1: Valid date within a fiscal quarter
      Date testDate = toDate("2023-04-15T00:00");
      int fiscalQuarter = CalcDateTime.fiscalquarter445(testDate, 2023, 4, 1, null, null);
      assertEquals(1, fiscalQuarter);

      // Test Case 2: Date in the second fiscal quarter
      testDate = toDate("2023-07-15T00:00");
      fiscalQuarter = CalcDateTime.fiscalquarter445(testDate, 2023, 4, 1, null, null);
      assertEquals(2, fiscalQuarter);

      // Test Case 3: Date in the third fiscal quarter
      testDate = toDate("2023-10-15T00:00");
      fiscalQuarter = CalcDateTime.fiscalquarter445(testDate, 2023, 4, 1, null, null);
      assertEquals(3, fiscalQuarter);

      // Test Case 4: Date in the fourth fiscal quarter
      testDate = toDate("2024-01-15T00:00");
      fiscalQuarter = CalcDateTime.fiscalquarter445(testDate, 2023, 4, 1, null, null);
      assertEquals(4, fiscalQuarter);

      // Test Case 5: Null date
      try {
         CalcDateTime.fiscalquarter445(null, 2023, 4, 1, null, null);
         fail("Expected an exception for null date");
      }
      catch(Exception e) {
         assertTrue(e instanceof NullPointerException);
      }
   }

   @Test
   public void testFiscalMonth445() {
      // Test Case 1: Valid date within fiscal year
      Date testDate = toDate("2023-04-15T00:00");
      int fiscalMonth = CalcDateTime.fiscalmonth445(testDate, 2023, 4, 1, null, null);
      assertEquals(1, fiscalMonth);

      // Test Case 2: Valid date in the second fiscal month
      testDate = toDate("2023-05-15T00:00");
      fiscalMonth = CalcDateTime.fiscalmonth445(testDate, 2023, 4, 1, null, null);
      assertEquals(2, fiscalMonth);

      // Test Case 3: Valid date in a year with 53 weeks
      testDate = toDate("2023-12-15T00:00");
      Object yearsWith53Weeks = new Object[]{ 2023 };
      fiscalMonth = CalcDateTime.fiscalmonth445(testDate, 2023, 4, 1, yearsWith53Weeks, null);
      assertEquals(9, fiscalMonth);

      // Test Case 4: Date before fiscal year start
      try {
         CalcDateTime.fiscalmonth445(toDate("2023-03-31T00:00"), 2023, 4, 1, null, null);
         fail("Expected IllegalArgumentException for Date before fiscal year start");
      }
      catch(IllegalArgumentException e) {
         assertTrue(e.getMessage().matches(".*Specified date .* is before fiscal calendar start.*"));
      }

      // Test Case 5: Null date
      try {
         CalcDateTime.fiscalmonth445(null, 2023, 4, 1, null, null);
         fail("Expected an exception for null date");
      }
      catch(Exception e) {
         assertTrue(e instanceof NullPointerException);
      }
   }

   @Test
   public void testFiscalWeek445() {
      // Test Case 1: Valid date within fiscal year
      Date testDate = toDate("2023-04-15T00:00");
      int fiscalWeek = CalcDateTime.fiscalweek445(testDate, 2023, 4, 1, null, null);
      assertEquals(3, fiscalWeek);

      // Test Case 2: Valid date on fiscal year start
      testDate = toDate("2023-04-01T00:00");
      fiscalWeek = CalcDateTime.fiscalweek445(testDate, 2023, 4, 1, null, null);
      assertEquals(1, fiscalWeek);

      // Test Case 3: Valid date after fiscal year start
      testDate = toDate("2023-04-08T00:00");
      fiscalWeek = CalcDateTime.fiscalweek445(testDate, 2023, 4, 1, null, null);
      assertEquals(2, fiscalWeek);

      // Test Case 4: Valid date before fiscal year start
      try {
         CalcDateTime.fiscalweek445(toDate("2023-03-31T00:00"), 2023, 4, 1, null, null);
         fail("Expected IllegalArgumentException for Date before fiscal year start");
      }
      catch(IllegalArgumentException e) {
         assertTrue(e.getMessage().matches(".*Specified date .* is before fiscal calendar start.*"));
      }

      // Test Case 5: Null date
      try {
         CalcDateTime.fiscalweek445(null, 2023, 4, 1, null, null);
         fail("Expected RuntimeException for null date");
      } catch (RuntimeException e) {
         assertTrue(e.getMessage().contains("date must not be null"));
      }
   }

   @Test
   public void testFiscalYear454() {
      // Test Case 1: Valid date within fiscal year
      Date testDate = toDate("2023-04-15T00:00");
      int fiscalYear = CalcDateTime.fiscalyear454(testDate, 2023, 4, 1, null, null);
      assertEquals(2023, fiscalYear);

      // Test Case 2: Date on fiscal year start
      testDate = toDate("2023-04-01T00:00");
      fiscalYear = CalcDateTime.fiscalyear454(testDate, 2023, 4, 1, null, null);
      assertEquals(2023, fiscalYear);

      // Test Case 3: Date in a year with 53 weeks
      testDate = toDate("2023-12-31T00:00");
      Object yearsWith53Weeks = new Object[]{ 2023 };
      fiscalYear = CalcDateTime.fiscalyear454(testDate, 2023, 4, 1, yearsWith53Weeks, null);
      assertEquals(2023, fiscalYear);

      // Test Case 4: Date before fiscal year start
      try {
         CalcDateTime.fiscalyear454(toDate("2023-03-31T00:00"), 2023, 4, 1, null, null);
         fail("Expected IllegalArgumentException for Date before fiscal year start");
      }
      catch(IllegalArgumentException e) {
         assertTrue(e.getMessage().matches(".*Specified date .* is before fiscal calendar start.*"));
      }

      // Test Case 5: Null date
      try {
         CalcDateTime.fiscalyear454(null, 2023, 4, 1, null, null);
         fail("Expected an exception for null date");
      }
      catch(Exception e) {
         assertTrue(e.getMessage().contains("date must not be null"));
      }
   }

   @Test
   public void testFiscalQuarter454() {
      // Test Case 1: Valid date within the fiscal quarter
      Date testDate = toDate("2023-04-15T00:00");
      int fiscalQuarter = CalcDateTime.fiscalquarter454(testDate, 2023, 4, 1, null, null);
      assertEquals(1, fiscalQuarter);

      // Test Case 2: Date in the second fiscal quarter
      testDate = toDate("2023-07-15T00:00");
      fiscalQuarter = CalcDateTime.fiscalquarter454(testDate, 2023, 4, 1, null, null);
      assertEquals(2, fiscalQuarter);

      // Test Case 3: Date in the third fiscal quarter
      testDate = toDate("2023-10-15T00:00");
      fiscalQuarter = CalcDateTime.fiscalquarter454(testDate, 2023, 4, 1, null, null);
      assertEquals(3, fiscalQuarter);

      // Test Case 4: Date in the fourth fiscal quarter
      testDate = toDate("2024-01-15T00:00");
      fiscalQuarter = CalcDateTime.fiscalquarter454(testDate, 2023, 4, 1, null, null);
      assertEquals(4, fiscalQuarter);

      // Test Case 5: Null date
      try {
         CalcDateTime.fiscalquarter454(null, 2023, 4, 1, null, null);
         fail("Expected an exception for null date");
      }
      catch(Exception e) {
         assertTrue(e.getMessage().contains("date must not be null"));
      }
   }

   @Test
   public void testFiscalMonth454() {
      // Test Case 1: Valid date within fiscal year
      Date testDate = toDate("2023-04-15T00:00");
      int fiscalMonth = CalcDateTime.fiscalmonth454(testDate, 2023, 4, 1, null, null);
      assertEquals(1, fiscalMonth);

      // Test Case 2: Valid date in the second fiscal month
      testDate = toDate("2023-05-15T00:00");
      fiscalMonth = CalcDateTime.fiscalmonth454(testDate, 2023, 4, 1, null, null);
      assertEquals(2, fiscalMonth);

      // Test Case 3: Valid date in a year with 53 weeks
      testDate = toDate("2023-12-15T00:00");
      Object yearsWith53Weeks = new Object[]{ 2023 };
      fiscalMonth = CalcDateTime.fiscalmonth454(testDate, 2023, 4, 1, yearsWith53Weeks, null);
      assertEquals(9, fiscalMonth);

      // Test Case 4: Date before fiscal year start
      try {
         testDate = toDate("2023-03-15T00:00");
         CalcDateTime.fiscalmonth454(testDate, 2023, 4, 1, null, null);
         fail("Expected IllegalArgumentException for date before fiscal year start");
      }
      catch(IllegalArgumentException e) {
         assertTrue(e.getMessage().matches(".*Specified date .* is before fiscal calendar start.*"));
      }

      // Test Case 5: Null date
      try {
         CalcDateTime.fiscalmonth454(null, 2023, 4, 1, null, null);
         fail("Expected RuntimeException for null date");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("date must not be null"));
      }
   }

   @Test
   public void testFiscalWeek454() {
      // Test Case 1: Valid date within fiscal year
      Date testDate = toDate("2023-04-15T00:00");
      int fiscalWeek = CalcDateTime.fiscalweek454(testDate, 2023, 4, 1, null, null);
      assertEquals(3, fiscalWeek);

      // Test Case 2: Valid date on fiscal year start
      testDate = toDate("2023-04-01T00:00");
      fiscalWeek = CalcDateTime.fiscalweek454(testDate, 2023, 4, 1, null, null);
      assertEquals(1, fiscalWeek);

      // Test Case 3: Valid date after fiscal year start
      testDate = toDate("2023-04-08T00:00");
      fiscalWeek = CalcDateTime.fiscalweek454(testDate, 2023, 4, 1, null, null);
      assertEquals(2, fiscalWeek);

      // Test Case 4: Valid date before fiscal year start
      try {
         CalcDateTime.fiscalweek454(toDate("2023-03-31T00:00"), 2023, 4, 1, null, null);
         fail("Expected IllegalArgumentException for Date before fiscal year start");
      }
      catch(IllegalArgumentException e) {
         assertTrue(e.getMessage().matches(".*Specified date .* is before fiscal calendar start.*"));
      }

      // Test Case 5: Null date
      try {
         CalcDateTime.fiscalweek454(null, 2023, 4, 1, null, null);
         fail("Expected RuntimeException for null date");
      } catch (RuntimeException e) {
         assertTrue(e.getMessage().contains("date must not be null"));
      }
   }

   @Test
   public void testFiscalYear544() {
      // Test Case 1: Valid date within fiscal year
      Date testDate = toDate("2023-04-15T00:00");
      int fiscalYear = CalcDateTime.fiscalyear544(testDate, 2023, 4, 1, null, null);
      assertEquals(2023, fiscalYear);

      // Test Case 2: Date on fiscal year start
      testDate = toDate("2023-04-01T00:00");
      fiscalYear = CalcDateTime.fiscalyear544(testDate, 2023, 4, 1, null, null);
      assertEquals(2023, fiscalYear);

      // Test Case 3: Date in a year with 53 weeks
      testDate = toDate("2023-12-31T00:00");
      Object yearsWith53Weeks = new Object[]{ 2023 };
      fiscalYear = CalcDateTime.fiscalyear544(testDate, 2023, 4, 1, yearsWith53Weeks, null);
      assertEquals(2023, fiscalYear);

      // Test Case 4: Date before fiscal year start
      try {
         testDate = toDate("2023-03-31T00:00");
         CalcDateTime.fiscalyear544(testDate, 2023, 4, 1, null, null);
         fail("Expected IllegalArgumentException for date before fiscal year start");
      }
      catch(IllegalArgumentException e) {
         assertTrue(e.getMessage().matches(".*Specified date .* is before fiscal calendar start.*"));
      }

      // Test Case 5: Null date
      try {
         CalcDateTime.fiscalyear544(null, 2023, 4, 1, null, null);
         fail("Expected RuntimeException for null date");
      }
      catch(RuntimeException e) {
         assertTrue(e.getMessage().contains("date must not be null"));
      }
   }

   @Test
   public void testFiscalQuarter544() {
      // Test Case 1: Valid date within the first fiscal quarter
      Date testDate = toDate("2023-04-15T00:00");
      int fiscalQuarter = CalcDateTime.fiscalquarter544(testDate, 2023, 4, 1, null, null);
      assertEquals(1, fiscalQuarter);

      // Test Case 2: Valid date within the second fiscal quarter
      testDate = toDate("2023-07-15T00:00");
      fiscalQuarter = CalcDateTime.fiscalquarter544(testDate, 2023, 4, 1, null, null);
      assertEquals(2, fiscalQuarter);

      // Test Case 3: Valid date within the third fiscal quarter
      testDate = toDate("2023-10-15T00:00");
      fiscalQuarter = CalcDateTime.fiscalquarter544(testDate, 2023, 4, 1, null, null);
      assertEquals(3, fiscalQuarter);

      // Test Case 4: Valid date within the fourth fiscal quarter
      testDate = toDate("2024-01-15T00:00");
      fiscalQuarter = CalcDateTime.fiscalquarter544(testDate, 2023, 4, 1, null, null);
      assertEquals(4, fiscalQuarter);

      // Test Case 5: Null date
      try {
         CalcDateTime.fiscalquarter544(null, 2023, 4, 1, null, null);
         fail("Expected IllegalArgumentException for null date");
      }
      catch(Exception e) {
         assertTrue(e instanceof NullPointerException);
      }
   }

   @Test
   public void testFiscalMonth544() {
      // Test Case 1: Valid date within fiscal year
      Date testDate = toDate("2023-04-15T00:00");
      int fiscalMonth = CalcDateTime.fiscalmonth544(testDate, 2023, 4, 1, null, null);
      assertEquals(1, fiscalMonth);

      // Test Case 2: Valid date in the second fiscal month
      testDate = toDate("2023-05-15T00:00");
      fiscalMonth = CalcDateTime.fiscalmonth544(testDate, 2023, 4, 1, null, null);
      assertEquals(2, fiscalMonth);

      // Test Case 3: Valid date in a year with 53 weeks
      testDate = toDate("2023-12-15T00:00");
      Object yearsWith53Weeks = new Object[]{ 2023 };
      fiscalMonth = CalcDateTime.fiscalmonth544(testDate, 2023, 4, 1, yearsWith53Weeks, null);
      assertEquals(9, fiscalMonth);

      // Test Case 4: Date before fiscal year start
      try {
         testDate = toDate("2023-03-15T00:00");
         CalcDateTime.fiscalmonth544(testDate, 2023, 4, 1, null, null);
         fail("Expected IllegalArgumentException for date before fiscal year start");
      }
      catch(IllegalArgumentException e) {
         assertTrue(e.getMessage().matches(".*Specified date .* is before fiscal calendar start.*"));
      }

      // Test Case 5: Null date
      try {
         CalcDateTime.fiscalmonth544(null, 2023, 4, 1, null, null);
         fail("Expected IllegalArgumentException for null date");
      }
      catch(Exception e) {
         assertTrue(e instanceof NullPointerException);
      }
   }

   @Test
   public void testFiscalWeek544() {
      // Test Case 1: Valid date within fiscal year
      Date testDate = toDate("2023-04-15T00:00");
      int fiscalWeek = CalcDateTime.fiscalweek544(testDate, 2023, 4, 1, null, null);
      assertEquals(3, fiscalWeek);

      // Test Case 2: Valid date on fiscal year start
      testDate = toDate("2023-04-01T00:00");
      fiscalWeek = CalcDateTime.fiscalweek544(testDate, 2023, 4, 1, null, null);
      assertEquals(1, fiscalWeek);

      // Test Case 3: Valid date after fiscal year start
      testDate = toDate("2023-04-08T00:00");
      fiscalWeek = CalcDateTime.fiscalweek544(testDate, 2023, 4, 1, null, null);
      assertEquals(2, fiscalWeek);

      // Test Case 4: Valid date before fiscal year start
      try {
         CalcDateTime.fiscalweek544(toDate("2023-03-31T00:00"), 2023, 4, 1, null, null);
         fail("Expected IllegalArgumentException for Date before fiscal year start");
      }
      catch(IllegalArgumentException e) {
         assertTrue(e.getMessage().matches(".*Specified date .* is before fiscal calendar start.*"));
      }

      // Test Case 5: Null date
      try {
         CalcDateTime.fiscalweek544(null, 2023, 4, 1, null, null);
         fail("Expected RuntimeException for null date");
      } catch (RuntimeException e) {
         assertTrue(e.getMessage().contains("date must not be null"));
      }
   }

   // Bug #77471, holidays, week numbers and fiscal periods use the Gregorian calendar under a
   // Buddhist or Japanese imperial default locale, and are unchanged under Gregorian locales
   @Test
   void gregorianResultsUnderNonGregorianDefaultLocale() {
      for(String tag : new String[] { "en-US", "de-DE", "th-TH", "ja-JP-u-ca-japanese" }) {
         Locale oldLocale = Locale.getDefault();

         try {
            Locale.setDefault(Locale.forLanguageTag(tag));
            Object[] holidays = { CalcUtilTest.hybridDate(2024, 1, 3) };
            Object[] yearsWith53Weeks = { 2020 };
            Date june2024 = CalcUtilTest.hybridDate(2024, 6, 15);

            assertEquals(4, CalcDateTime.networkdays(CalcUtilTest.hybridDate(2024, 1, 1),
                                                     CalcUtilTest.hybridDate(2024, 1, 5), holidays), tag);
            assertEquals(CalcUtilTest.hybridDate(2024, 1, 5),
                         CalcDateTime.workday(CalcUtilTest.hybridDate(2024, 1, 1), 3, holidays), tag);
            assertEquals(24, CalcDateTime.weeknum(CalcUtilTest.hybridDate(2019, 6, 15), 1), tag);
            // Bug #77526, January 1 is in week 1 whatever the locale's minimal days
            assertEquals(1, CalcDateTime.weeknum(CalcUtilTest.hybridDate(2021, 1, 1), 1), tag);
            assertEquals(2024, CalcDateTime.fiscalyear(june2024, 4, 1, null), tag);
            assertEquals(3, CalcDateTime.fiscalmonth(june2024, 4, 1, null), tag);
            assertEquals(2023, CalcDateTime.fiscalyear445(june2024, 2015, 1, 4, yearsWith53Weeks, null), tag);
            assertEquals(24, CalcDateTime.fiscalweek445(june2024, 2015, 1, 4, yearsWith53Weeks, null), tag);
         }
         finally {
            Locale.setDefault(oldLocale);
         }
      }
   }

   private static final int[] WEEKNUM_RETURN_TYPES = { 1, 2, 11, 12, 13, 14, 15, 16, 17, 21 };

   // year, month, day, then the Excel WEEKNUM for each of WEEKNUM_RETURN_TYPES
   private static final int[][] EXCEL_WEEKNUM = {
         { 2023, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 52 }, // Sun
         { 2024, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 }, // Mon
         { 2019, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 }, // Tue
         { 2020, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 }, // Wed
         { 2015, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 }, // Thu
         { 2021, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 53 }, // Fri
         { 2022, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 52 }, // Sat
         { 2023, 12, 31, 53, 53, 53, 53, 53, 53, 53, 53, 53, 52 }, // Sun
         { 2024, 12, 31, 53, 53, 53, 54, 53, 53, 53, 53, 53, 1 }, // Tue
         { 2019, 12, 31, 53, 53, 53, 53, 53, 53, 53, 53, 53, 1 }, // Tue
         { 2020, 12, 31, 53, 53, 53, 53, 53, 54, 53, 53, 53, 53 }, // Thu
         { 2015, 12, 31, 53, 53, 53, 53, 53, 53, 53, 53, 53, 53 }, // Thu
         { 2021, 12, 31, 53, 53, 53, 53, 53, 53, 53, 53, 53, 52 }, // Fri
         { 2022, 12, 31, 53, 53, 53, 53, 53, 53, 53, 53, 53, 52 }, // Sat
         { 2000, 12, 31, 54, 53, 53, 53, 53, 53, 53, 53, 54, 52 }, // Sun
         { 2024, 2, 29, 9, 9, 9, 10, 10, 10, 9, 9, 9, 9 }, // Thu
         { 2012, 3, 9, 10, 11, 11, 11, 11, 11, 11, 10, 10, 10 }, // Fri
         { 2019, 6, 15, 24, 24, 24, 24, 25, 25, 25, 25, 24, 24 }, // Sat
         { 2021, 1, 3, 2, 1, 1, 1, 1, 1, 1, 2, 2, 53 }, // Sun
         { 2021, 1, 4, 2, 2, 2, 1, 1, 1, 1, 2, 2, 1 }, // Mon
         { 2024, 12, 29, 53, 52, 52, 53, 53, 53, 53, 53, 53, 52 }, // Sun
         { 2024, 12, 30, 53, 53, 53, 53, 53, 53, 53, 53, 53, 1 }, // Mon
         { 2026, 12, 31, 53, 53, 53, 53, 53, 53, 53, 53, 53, 53 }, // Thu
         { 2027, 1, 3, 2, 1, 1, 1, 1, 1, 1, 2, 2, 53 }, // Sun
         { 2027, 1, 4, 2, 2, 2, 1, 1, 1, 1, 2, 2, 1 }, // Mon
   };

   // Bug #77526, weeknum follows Excel WEEKNUM for every return type, at year boundaries and in
   // leap years, whatever the default locale's first day of week and minimal days
   @ParameterizedTest
   @ValueSource(strings = { "en-US", "de-DE", "en-GB", "th-TH", "ja-JP-u-ca-japanese" })
   void weeknumMatchesExcel(String tag) {
      Locale oldLocale = Locale.getDefault();

      try {
         Locale.setDefault(Locale.forLanguageTag(tag));

         for(int[] row : EXCEL_WEEKNUM) {
            Date date = CalcUtilTest.hybridDate(row[0], row[1], row[2]);

            for(int i = 0; i < WEEKNUM_RETURN_TYPES.length; i++) {
               int rt = WEEKNUM_RETURN_TYPES[i];
               assertEquals(row[i + 3], CalcDateTime.weeknum(date, rt),
                            tag + " " + row[0] + "-" + row[1] + "-" + row[2] + " rt " + rt);
            }

            assertEquals(row[3], CalcDateTime.weeknum(date, null), tag + " null rt");
         }
      }
      finally {
         Locale.setDefault(oldLocale);
      }
   }

   // Bug #77526, checks every day of 1999-2030 against the WEEKNUM definition: week 1 contains
   // January 1 and each later start day begins a new week, and return type 21 is the ISO week
   @ParameterizedTest
   @ValueSource(strings = { "en-US", "de-DE", "en-GB", "th-TH" })
   void weeknumMatchesDefinitionEveryDay(String tag) {
      DayOfWeek[] starts = { DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.MONDAY,
                             DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                             DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY };
      Locale oldLocale = Locale.getDefault();

      try {
         Locale.setDefault(Locale.forLanguageTag(tag));
         int[] weeks = new int[starts.length];

         for(LocalDate day = LocalDate.of(1999, 1, 1); day.getYear() <= 2030;
             day = day.plusDays(1))
         {
            Date date = Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());

            for(int i = 0; i < starts.length; i++) {
               if(day.getDayOfYear() == 1) {
                  weeks[i] = 1;
               }
               else if(day.getDayOfWeek() == starts[i]) {
                  weeks[i]++;
               }

               assertEquals(weeks[i], CalcDateTime.weeknum(date, WEEKNUM_RETURN_TYPES[i]),
                            tag + " " + day + " rt " + WEEKNUM_RETURN_TYPES[i]);
            }

            assertEquals(day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                         CalcDateTime.weeknum(date, 21), tag + " " + day + " rt 21");
         }
      }
      finally {
         Locale.setDefault(oldLocale);
      }
   }

   // Bug #77526, an unsupported return type keeps the Sunday default
   @Test
   void weeknumUnsupportedReturnTypeUsesSunday() {
      Date date = CalcUtilTest.hybridDate(2019, 6, 15);
      assertEquals(24, CalcDateTime.weeknum(date, 3));
      assertEquals(24, CalcDateTime.weeknum(date, 99));
      assertEquals(53, CalcDateTime.weeknum(CalcUtilTest.hybridDate(2024, 12, 30), 3));
   }

   /**
    * @param localDateTime an ISO-8601 datetime string, e.g. 2007-12-03T10:15:30
    *
    * @return the corresponding date in the default time zone.
    */
   private java.util.Date toDate(String localDateTime) {
      return java.util.Date.from(LocalDateTime.parse(localDateTime)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant());
   }

   // Bug #78112, CalcDateTime.date()'s WEEK_DATE_GROUP case (which backs the toList/mapList
   // "round=week" date-rounding option, a distinct code path from CalcDateTime.weeknum() above)
   // must round every date to the same week bucket under any JVM default locale, not just
   // locales whose GregorianCalendar default minimalDaysInFirstWeek happens to be 1 (en-US,
   // zh-CN). de-DE/en-GB/th-TH default to 4, which used to round December/January dates near a
   // year boundary to a different week bucket than en-US, because the method read
   // Calendar.WEEK_OF_YEAR from CoreTool.calendar without correcting minimalDaysInFirstWeek.
   @ParameterizedTest
   @ValueSource(strings = { "de-DE", "en-GB", "th-TH" })
   void dateWeekGroupMatchesEnUsUnderAnyLocale(String tag) throws Exception {
      long[][] results = new long[2][];
      Throwable[] error = { null };

      // Locale.setDefault() is JVM-global, not thread-local, so even though
      // weekGroupDates() below runs on separate worker threads, the locale it sets must be
      // restored once on this thread after both have finished (matching the save/restore
      // pattern used by this file's other locale-switching tests, e.g.
      // gregorianResultsUnderNonGregorianDefaultLocale above).
      Locale oldLocale = Locale.getDefault();

      try {
         // CoreTool.calendar is a shared ThreadLocal, never reconstructed when the locale
         // changes -- each locale must run on its own fresh thread, same trap as
         // DateRangeRefWeekOfYearLocaleTest/SortOrderTest's locale-switching tests.
         Thread enUs = new Thread(() -> {
            try {
               results[0] = weekGroupDates("en-US");
            }
            catch(Throwable ex) {
               error[0] = ex;
            }
         });
         Thread other = new Thread(() -> {
            try {
               results[1] = weekGroupDates(tag);
            }
            catch(Throwable ex) {
               error[0] = ex;
            }
         });

         enUs.start();
         enUs.join();
         other.start();
         other.join();
      }
      finally {
         Locale.setDefault(oldLocale);
      }

      if(error[0] != null) {
         throw new AssertionError(error[0]);
      }

      assertArrayEquals(results[0], results[1], tag + " vs en-US");
   }

   /**
    * Round every day from 2015-01-01 through 2030 to its week bucket via
    * CalcDateTime.date(..., WEEK_DATE_GROUP, 1), under the given JVM default locale. Must be
    * called on a fresh thread per locale (see dateWeekGroupMatchesEnUsUnderAnyLocale).
    */
   private long[] weekGroupDates(String tag) {
      Locale.setDefault(Locale.forLanguageTag(tag));
      java.util.List<Long> millis = new java.util.ArrayList<>();

      for(LocalDate day = LocalDate.of(2015, 1, 1); day.getYear() <= 2030;
          day = day.plusDays(1))
      {
         Date date = Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
         Date rounded = CalcDateTime.date(date, CalcDateTime.WEEK_DATE_GROUP, 1d);
         millis.add(rounded.getTime());
      }

      return millis.stream().mapToLong(Long::longValue).toArray();
   }
}
