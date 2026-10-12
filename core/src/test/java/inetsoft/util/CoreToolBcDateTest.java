/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.util;

import inetsoft.test.*;
import inetsoft.uql.AbstractCondition;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persisted BC dates must keep their era. The year is written as an astronomical year
 * (1 BC is 0000, 44 BC is -0043), which the existing parsers read back as the same BC date,
 * while every AD date keeps its exact string (Bug #77442).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class CoreToolBcDateTest {
   @AfterEach
   void restore() {
      CoreTool.useDatetimeWithMillisFormat.set(false);
      TimeZone.setDefault(defaultZone);
   }

   // BC dates are created through the ERA field, the hybrid Julian/Gregorian calendar the
   // formatters use. java.time is proleptic Gregorian and would be days off.
   private static Date bc(TimeZone tz, int year, int month, int day, int hour, int min, int sec,
                          int millis)
   {
      return date(tz, GregorianCalendar.BC, year, month, day, hour, min, sec, millis);
   }

   private static Date ad(TimeZone tz, int year, int month, int day, int hour, int min, int sec,
                          int millis)
   {
      return date(tz, GregorianCalendar.AD, year, month, day, hour, min, sec, millis);
   }

   private static Date date(TimeZone tz, int era, int year, int month, int day, int hour, int min,
                            int sec, int millis)
   {
      GregorianCalendar cal = new GregorianCalendar(tz);
      cal.clear();
      cal.set(Calendar.ERA, era);
      cal.set(year, month - 1, day, hour, min, sec);
      cal.set(Calendar.MILLISECOND, millis);
      return cal.getTime();
   }

   private static TimeZone cacheZone() {
      return CoreTool.DATE_FORMAT_CACHE.getTimeZone();
   }

   @Test
   void bcDateRoundTripsThroughDataString() {
      TimeZone tz = cacheZone();
      Object[][] cases = {
         { bc(tz, 1, 1, 1, 0, 0, 0, 0), "0000-01-01" },
         { bc(tz, 1, 12, 31, 0, 0, 0, 0), "0000-12-31" },
         { bc(tz, 1, 2, 29, 0, 0, 0, 0), "0000-02-29" }, // Julian leap day
         { bc(tz, 5, 2, 29, 0, 0, 0, 0), "-0004-02-29" }, // Julian leap day
         { bc(tz, 44, 3, 15, 0, 0, 0, 0), "-0043-03-15" },
         { bc(tz, 4713, 1, 1, 0, 0, 0, 0), "-4712-01-01" },
      };

      for(Object[] c : cases) {
         java.sql.Date date = new java.sql.Date(((Date) c[0]).getTime());
         String str = CoreTool.getDataString(date);
         assertEquals(c[1], str);
         Object read = CoreTool.getData(XSchema.DATE, str);
         assertInstanceOf(Date.class, read);
         assertEquals(date.getTime(), ((Date) read).getTime(), str);
      }
   }

   @Test
   void bcTimestampRoundTripsThroughDataString() {
      TimeZone tz = cacheZone();
      Timestamp ts = new Timestamp(bc(tz, 44, 3, 15, 10, 30, 5, 0).getTime());
      String str = CoreTool.getDataString(ts);
      assertEquals("-0043-03-15 10:30:05", str);
      assertEquals(ts.getTime(), ((Date) CoreTool.getData(XSchema.TIME_INSTANT, str)).getTime());

      Timestamp leap = new Timestamp(bc(tz, 5, 2, 29, 23, 59, 59, 0).getTime());
      str = CoreTool.getDataString(leap);
      assertEquals("-0004-02-29 23:59:59", str);
      assertEquals(leap.getTime(), ((Date) CoreTool.getData(XSchema.TIME_INSTANT, str)).getTime());
   }

   @Test
   void bcTimestampWithMillisRoundTrips() {
      CoreTool.useDatetimeWithMillisFormat.set(true);
      TimeZone tz = CoreTool.DATETIME_WITH_MILLIS_FORMAT_CACHE.getTimeZone();
      Timestamp ts = new Timestamp(bc(tz, 44, 3, 15, 10, 30, 5, 123).getTime());
      String str = CoreTool.formatDateTime(ts);
      assertEquals("-0043-03-15 10:30:05.123", str);
      assertEquals(ts.getTime(), ((Date) CoreTool.getData(XSchema.TIME_INSTANT, str)).getTime());
   }

   @ParameterizedTest
   @ValueSource(strings = { "UTC", "America/New_York", "Asia/Shanghai" })
   void bcRoundTripsWithDefaultZone(String zone) {
      TimeZone.setDefault(TimeZone.getTimeZone(zone));
      TimeZone tz = cacheZone();
      Date[] dates = {
         bc(tz, 1, 12, 31, 23, 59, 59, 0),
         bc(tz, 1, 1, 1, 0, 0, 0, 0),
         bc(tz, 44, 3, 15, 10, 30, 0, 0),
      };

      for(Date d : dates) {
         Timestamp ts = new Timestamp(d.getTime());
         String str = CoreTool.getDataString(ts);
         assertTrue(str.startsWith("0000-") || str.startsWith("-0043-"), str);
         assertEquals(ts.getTime(), ((Date) CoreTool.getData(XSchema.TIME_INSTANT, str)).getTime());
      }
   }

   @ParameterizedTest
   @ValueSource(strings = { "UTC", "America/New_York", "Asia/Shanghai" })
   void eraIsCheckedInFormatterZone(String zone) throws Exception {
      TimeZone tz = TimeZone.getTimeZone(zone);
      DateFormat fmt = CoreTool.createDateFormat(CoreTool.DEFAULT_DATETIME_PATTERN);
      fmt.setTimeZone(tz);

      // the last second of 1 BC in the formatter zone is already AD 1 in some other zone
      Date lastBC = bc(tz, 1, 12, 31, 23, 59, 59, 0);
      String str = CoreTool.formatPersistentDate(fmt, lastBC);
      assertEquals("0000-12-31 23:59:59", str);
      assertEquals(lastBC.getTime(), fmt.parse(str).getTime());

      Date firstAD = ad(tz, 1, 1, 1, 0, 0, 0, 0);
      assertEquals("0001-01-01 00:00:00", CoreTool.formatPersistentDate(fmt, firstAD));
   }

   @Test
   void adStringsAreUnchanged() {
      TimeZone tz = cacheZone();
      Date[] dates = {
         ad(tz, 1, 1, 1, 0, 0, 0, 0),
         ad(tz, 1, 1, 2, 12, 0, 0, 0),
         ad(tz, 1000, 2, 29, 0, 0, 0, 0),
         ad(tz, 1582, 10, 4, 0, 0, 0, 0),
         ad(tz, 1582, 10, 15, 0, 0, 0, 0),
         ad(tz, 1900, 1, 1, 0, 0, 0, 0),
         ad(tz, 2024, 2, 29, 13, 45, 30, 250),
      };

      for(Date d : dates) {
         java.sql.Date date = new java.sql.Date(d.getTime());
         Timestamp ts = new Timestamp(d.getTime());
         assertEquals(CoreTool.DATE_FORMAT_CACHE.format(date), CoreTool.formatDate(date));
         assertEquals(CoreTool.DATETIME_FORMAT_CACHE.format(ts), CoreTool.formatDateTime(ts));
         assertEquals(CoreTool.dateFmt.get().format(date),
                      AbstractCondition.getValueString(date));
         assertEquals(CoreTool.timeInstantFmt.get().format(ts),
                      AbstractCondition.getValueString(ts));
      }
   }

   @Test
   void bcConditionValueRoundTrips() {
      TimeZone tz = CoreTool.dateFmt.get().getTimeZone();
      java.sql.Date date = new java.sql.Date(bc(tz, 44, 3, 15, 0, 0, 0, 0).getTime());
      String str = AbstractCondition.getValueString(date);
      assertEquals("{d '-0043-03-15'}", str);
      assertEquals(str, AbstractCondition.getValueString(date, XSchema.DATE));
      assertEquals(date.getTime(),
                   ((Date) AbstractCondition.getData(XSchema.DATE, str)).getTime());

      Timestamp ts = new Timestamp(bc(tz, 1, 1, 1, 10, 30, 0, 0).getTime());
      str = AbstractCondition.getValueString(ts);
      assertEquals("{ts '0000-01-01 10:30:00'}", str);
      assertEquals(str, AbstractCondition.getValueString(ts, XSchema.TIME_INSTANT));
      assertEquals(ts.getTime(),
                   ((Date) AbstractCondition.getData(XSchema.TIME_INSTANT, str)).getTime());
   }

   private final TimeZone defaultZone = TimeZone.getDefault();
}
