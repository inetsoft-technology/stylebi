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
package inetsoft.uql.gdata;

import com.google.api.services.sheets.v4.model.NumberFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@code GDataRuntime}'s conversion of a Google Sheets DATE-formatted serial number:
 * the calendar day it represents must not shift regardless of the JVM's default time zone (bug
 * #80408 - the server-side default zone, not the spreadsheet's own zone, used to leak into a
 * date-only value and shift it a day in zones at or east of UTC).
 */
class GDataRuntimeTest {
   @BeforeEach
   void setUp() {
      originalDefault = TimeZone.getDefault();
   }

   @AfterEach
   void tearDown() {
      TimeZone.setDefault(originalDefault);
   }

   @ParameterizedTest
   @ValueSource(strings = {"UTC", "Asia/Tokyo", "America/New_York", "Pacific/Kiritimati", "Etc/GMT+12"})
   void convertsDateSerialToTheSameCalendarDayInEveryTimeZone(String zoneId) throws Exception {
      TimeZone.setDefault(TimeZone.getTimeZone(zoneId));

      // 45292 is the Google Sheets serial number for 2024-01-01.
      Object converted = formatNumberValue(45292d, new NumberFormat().setType("DATE"));

      assertEquals(Date.valueOf("2024-01-01").toString(), converted.toString());
   }

   @Test
   void doesNotRegressDateTimeConversion() throws Exception {
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

      // 45292.5 is the Google Sheets serial number for 2024-01-01 12:00:00.
      Object converted = formatNumberValue(45292.5d, new NumberFormat().setType("DATE_TIME"));

      assertEquals(Timestamp.class, converted.getClass());
      assertEquals("2024-01-01 12:00:00.0", converted.toString());
   }

   private static Object formatNumberValue(Double value, NumberFormat format) throws Exception {
      Method method = GDataRuntime.class
         .getDeclaredMethod("formatNumberValue", Double.class, NumberFormat.class);
      method.setAccessible(true);
      return method.invoke(null, value, format);
   }

   private TimeZone originalDefault;
}
