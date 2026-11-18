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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81682: converting a date-time value to a Time keeps only the time of day on 1970-01-01,
 * the value an MV, a JDBC time column and a string conversion already give.
 */
@Tag("core")
class CoreToolTimeDataTest {
   private TimeZone defaultZone;

   @BeforeEach
   void saveZone() {
      defaultZone = TimeZone.getDefault();
   }

   @AfterEach
   void restoreZone() {
      TimeZone.setDefault(defaultZone);
   }

   @ParameterizedTest
   @ValueSource(strings = { "UTC", "Asia/Shanghai", "Europe/London" })
   void dateValuesKeepOnlyTimeOfDay(String zone) {
      TimeZone.setDefault(TimeZone.getTimeZone(zone));
      LocalDateTime ldt = LocalDateTime.of(2018, 12, 20, 0, 2, 7, 123_000_000);
      LocalDateTime summer = LocalDateTime.of(2019, 7, 10, 23, 59, 59, 5_000_000);

      for(LocalDateTime value : new LocalDateTime[] { ldt, summer }) {
         Timestamp ts = Timestamp.valueOf(value);
         assertTimeOfDay(value.toLocalTime(), CoreTool.getTimeData(ts));
         assertTimeOfDay(value.toLocalTime(), CoreTool.getTimeData(new java.util.Date(ts.getTime())));
         assertTimeOfDay(value.toLocalTime(),
                         CoreTool.getTimeData(new java.sql.Date(ts.getTime())));
         assertTimeOfDay(value.toLocalTime(), CoreTool.getTimeData(value));
         assertTimeOfDay(value.toLocalTime(),
                         CoreTool.getTimeData(value.atZone(ZoneId.systemDefault())));
         assertTimeOfDay(value.toLocalTime(),
                         CoreTool.getTimeData(value.atZone(ZoneId.systemDefault()).toInstant()));
         assertTimeOfDay(value.toLocalTime(),
                         CoreTool.getTimeData(
                            value.atZone(ZoneId.systemDefault()).toOffsetDateTime()));
      }
   }

   @ParameterizedTest
   @ValueSource(strings = { "UTC", "Asia/Shanghai", "Europe/London" })
   void sameTimeOnDifferentDatesIsEqual(String zone) {
      TimeZone.setDefault(TimeZone.getTimeZone(zone));
      Time a = CoreTool.getTimeData(Timestamp.valueOf("2018-12-20 00:02:07"));
      Time b = CoreTool.getTimeData(Timestamp.valueOf("2019-10-10 00:02:07"));
      assertEquals(a, b);
      assertEquals(a.getTime(), b.getTime());
   }

   @ParameterizedTest
   @ValueSource(strings = { "UTC", "Asia/Shanghai", "Europe/London" })
   void otherInputsAreUnchanged(String zone) {
      TimeZone.setDefault(TimeZone.getTimeZone(zone));
      Time time = new Time(1545235327000L);
      assertSame(time, CoreTool.getTimeData(time));
      assertEquals(12345L, CoreTool.getTimeData(12345L).getTime());
      assertNull(CoreTool.getTimeData(null));
      assertNull(CoreTool.getTimeData(new Object()));
      assertNotNull(CoreTool.getTimeData("01:02:03"));
   }

   private static void assertTimeOfDay(LocalTime expected, Time actual) {
      assertNotNull(actual);
      assertEquals(expected.withNano(0), actual.toLocalTime());
      assertEquals(LocalDate.of(1970, 1, 1),
                   Instant.ofEpochMilli(actual.getTime()).atZone(ZoneId.systemDefault())
                      .toLocalDate());
      assertEquals(expected.getNano() / 1_000_000L, actual.getTime() % 1000L < 0 ?
                   actual.getTime() % 1000L + 1000L : actual.getTime() % 1000L);
   }
}
