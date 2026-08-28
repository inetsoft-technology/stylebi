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
package inetsoft.web.viewsheet.service;

/*
 * Test strategy
 *
 * A calendar combobox whose data type is "date" has no time component. Its value used to travel
 * to the server as epoch millis that the browser had moved into the server time zone. The
 * browser resolves a zone offset through ICU, which models pre-1901 Local Mean Time, while
 * java.util.Date/TimeZone report a whole hour offset for the same instant. For a date inside a
 * zone's LMT era the two disagree by a few minutes, and truncating that instant to midnight
 * turns the disagreement into a whole day: the user picked 1900-01-01 under Asia/Shanghai and
 * the combobox came back showing 1899-12-31.
 *
 * The fix sends a wall clock "yyyy-MM-dd" string, which VSInputService turns straight into the
 * java.sql.Date it names. Going through Tool.getData()'s string parse instead is NOT enough --
 * CoreTool.parseDate() resolves LMT while the truncation that follows does not, so it loses the
 * same day (pinned by [G2] below). That is a wider defect in CoreTool, left alone here.
 *
 * Surefire pins these tests to America/New_York (core/pom.xml argLine), whose LMT era ends in
 * 1883, so 1880-01-01 stands in for the reported 1900-01-01 under Asia/Shanghai. The mechanism
 * and the assertions are the same; only the zone's cutover year differs. (CoreTool.parseDate
 * has an explicit special case for the literal "1900-01-01", which is why that one date happens
 * to survive the string parse -- another sign this class of bug has been hit before.)
 *
 * Behavioral guarantees covered:
 *
 * [G1] convertWallClockDate() keeps the day exactly as picked, for an LMT-era date and a
 *      modern one, and yields a java.sql.Date that Tool.getData() then passes through
 *      unchanged (no re-parse, no truncation).
 * [G2] Pins the two paths this replaces -- an instant, and Tool.getData()'s own string parse --
 *      both losing a day for an LMT-era date, so [G1] is not guarding a non-problem.
 * [G3] isWallClockDate() accepts only a yyyy-MM-dd string on a "date" combobox; a millis
 *      payload, a date/time string and the other date types still take the instant path.
 * [G4] An impossible date that still matches the pattern is returned unchanged rather than
 *      throwing out of the conversion.
 */

import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class VSComboBoxDateValueTest {
   /** A date inside the surefire time zone's Local Mean Time era. */
   private static final String LMT_ERA_DATE = "1880-01-01";
   /** What that date used to become. */
   private static final String SHIFTED_DATE = "1879-12-31";

   // [G1]
   @ParameterizedTest
   @ValueSource(strings = { LMT_ERA_DATE, "1900-01-01", "2025-12-31", "2024-05-06" })
   void wallClockDateKeepsItsDay(String date) throws Exception {
      Object converted = convertWallClockDate(XSchema.DATE, date);

      assertInstanceOf(java.sql.Date.class, converted);
      assertEquals(date, converted.toString());

      // and Tool.getData() leaves an already typed java.sql.Date alone
      assertEquals(date, Tool.getData(XSchema.DATE, converted).toString());
   }

   // [G2]
   @Test
   void bothReplacedPathsLoseADayForAnLmtEraDate() {
      assertTrue(isInLmtEra(LMT_ERA_DATE),
                 "the test date must sit in the zone's LMT era for this to be meaningful");

      // what the browser used to send: the wall clock date resolved through ICU/java.time,
      // which models the pre-1883 -04:56:02 offset that java.util.Date does not
      long millis = startOfDayMillis(LMT_ERA_DATE);

      assertEquals(SHIFTED_DATE, Tool.getData(XSchema.DATE, new Date(millis)).toString(),
                   "pins the instant path the wall clock string replaces");
      assertEquals(SHIFTED_DATE, Tool.getData(XSchema.DATE, LMT_ERA_DATE).toString(),
                   "pins CoreTool.parseDate's own LMT/truncation split -- why the conversion "
                      + "builds the java.sql.Date itself instead of handing over the string");
   }

   // [G3]
   @Test
   void onlyAWallClockDateStringIsConverted() throws Exception {
      assertTrue(isWallClockDate(XSchema.DATE, LMT_ERA_DATE));
      assertTrue(isWallClockDate(XSchema.DATE, "2025-12-31"));

      // a millis payload still takes the instant path
      assertFalse(isWallClockDate(XSchema.DATE, "1767110400000"));
      assertFalse(isWallClockDate(XSchema.DATE, 1767110400000L));
      // so does a date/time string, and anything empty or absent
      assertFalse(isWallClockDate(XSchema.DATE, "2025-12-31 00:00:00"));
      assertFalse(isWallClockDate(XSchema.DATE, ""));
      assertFalse(isWallClockDate(XSchema.DATE, null));
      // the other date types keep their existing handling
      assertFalse(isWallClockDate(XSchema.TIME_INSTANT, "2025-12-31"));
      assertFalse(isWallClockDate(XSchema.TIME, "2025-12-31"));

      // a value that is not converted is handed on untouched
      assertEquals("2025-12-31 00:00:00",
                   convertWallClockDate(XSchema.DATE, "2025-12-31 00:00:00"));
   }

   // [G4]
   @Test
   void animpossibleDateIsReturnedUnchanged() throws Exception {
      assertEquals("2025-02-30", convertWallClockDate(XSchema.DATE, "2025-02-30"));
   }

   private static boolean isInLmtEra(String date) {
      long millis = startOfDayMillis(date);
      ZoneId zone = ZoneId.of(TimeZone.getDefault().getID());

      return TimeZone.getDefault().getOffset(millis) !=
         zone.getRules().getOffset(Instant.ofEpochMilli(millis)).getTotalSeconds() * 1000L;
   }

   private static long startOfDayMillis(String date) {
      return LocalDate.parse(date)
         .atStartOfDay(ZoneId.of(TimeZone.getDefault().getID()))
         .toInstant()
         .toEpochMilli();
   }

   private static boolean isWallClockDate(String dataType, Object value) throws Exception {
      return (Boolean) invoke("isWallClockDate", dataType, value);
   }

   private static Object convertWallClockDate(String dataType, Object value) throws Exception {
      return invoke("convertWallClockDate", dataType, value);
   }

   private static Object invoke(String name, String dataType, Object value) throws Exception {
      Method method = VSInputService.class
         .getDeclaredMethod(name, String.class, Object.class);
      method.setAccessible(true);

      return method.invoke(null, dataType, value);
   }
}
