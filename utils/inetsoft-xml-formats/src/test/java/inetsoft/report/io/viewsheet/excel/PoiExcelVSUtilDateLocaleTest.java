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
package inetsoft.report.io.viewsheet.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests for {@link PoiExcelVSUtil#setCellValue(Cell, Object, org.apache.poi.ss.usermodel.RichTextString)}'s
 * {@code Date} branch (Bug #78104).
 *
 * <p>{@code org.apache.poi.ss.usermodel.Cell#setCellValue(Date)} converts the date to an Excel
 * serial number by reading a {@code Calendar} built from the JVM default locale
 * ({@code org.apache.poi.util.LocaleUtil.getLocaleCalendar()} -&gt;
 * {@code Calendar.getInstance(tz, Locale.getDefault())}). When the JVM default locale is
 * {@code th_TH}, that resolves to a {@code sun.util.BuddhistCalendar} whose {@code YEAR} field is
 * the Gregorian year + 543, silently corrupting the exported serial (e.g. 2026-01-15 becomes
 * 2569-01-15, serial 244364.0 instead of 46037.0). When it is {@code ja_JP_JP}, it resolves to a
 * {@code java.util.JapaneseImperialCalendar} whose era-relative {@code YEAR} (e.g. 8 for Reiwa 8)
 * fails POI's {@code year &gt;= 1900} windowing check outright, so every Date/Timestamp cell gets
 * the {@code BAD_DATE = -1} sentinel.</p>
 *
 * <p>The fix converts the {@code Date} to a {@code LocalDateTime} via
 * {@code Instant.atZone(ZoneId.systemDefault())} before handing it to POI.
 * {@code Cell.setCellValue(LocalDateTime)} computes the serial directly from the
 * {@code LocalDateTime}'s year/day-of-year/time fields with no {@code Calendar} or locale
 * involved, so it is immune to the JVM default locale. {@code ZoneId.systemDefault()} (not a
 * fixed zone such as UTC) is required for correctness: the pre-fix {@code Date}-based path
 * implicitly resolved the system default time zone too (via
 * {@code LocaleUtil.getUserTimeZone()}), so a fixed-zone conversion would silently shift any
 * timestamp near local midnight onto the wrong day on a non-UTC server — a regression the buggy
 * code does not even have. {@link #convertsNearMidnightTimestampUsingSystemDefaultZoneNotUtc()}
 * guards against reintroducing that mistake.</p>
 */
class PoiExcelVSUtilDateLocaleTest {
   private Locale originalLocale;
   private TimeZone originalTimeZone;

   @BeforeEach
   void saveDefaults() {
      originalLocale = Locale.getDefault();
      originalTimeZone = TimeZone.getDefault();
   }

   /**
    * Restored unconditionally (even on assertion failure) so a locale/time-zone override here
    * never leaks into another test running later in the same JVM/fork.
    */
   @AfterEach
   void restoreDefaults() {
      Locale.setDefault(originalLocale);
      TimeZone.setDefault(originalTimeZone);
   }

   /** Control case: a locale whose default calendar is already Gregorian. */
   @Test
   void writesCorrectSerialUnderGregorianLocale() throws IOException {
      assertSerialForMidnight(Locale.US, EXPECTED_SERIAL_2026_01_15);
   }

   /**
    * Thai Buddhist calendar: the JVM default locale used to make every date come out 543 years
    * off (reported as serial 244364.0 instead of 46037.0 for 2026-01-15).
    */
   @Test
   void writesCorrectSerialUnderThaiBuddhistLocale() throws IOException {
      assertSerialForMidnight(Locale.of("th", "TH"), EXPECTED_SERIAL_2026_01_15);
   }

   /**
    * Japanese Imperial calendar: the JVM default locale used to make every Date/Timestamp cell
    * come out as the BAD_DATE sentinel, serial -1.0.
    */
   @Test
   void writesCorrectSerialUnderJapaneseImperialLocale() throws IOException {
      assertSerialForMidnight(Locale.of("ja", "JP", "JP"), EXPECTED_SERIAL_2026_01_15);
   }

   /**
    * Same Thai Buddhist locale, but with a java.sql.Timestamp (not just java.util.Date) to
    * confirm the fix also covers Timestamp cells (a Timestamp column goes through the exact same
    * {@code instanceof Date} branch), and to pair with the near-midnight case below.
    */
   @Test
   void writesCorrectSerialForTimestampUnderThaiBuddhistLocale() throws IOException {
      Locale.setDefault(Locale.of("th", "TH"));
      TimeZone.setDefault(FIXED_ZONE_GMT8);

      Timestamp timestamp = Timestamp.from(
         ZonedDateTime.of(2026, 1, 15, 0, 0, 0, 0, ZoneId.of(FIXED_ZONE_GMT8.getID())).toInstant());

      assertEquals(EXPECTED_SERIAL_2026_01_15, numericValueAfterWrite(timestamp), DELTA);
   }

   /**
    * The refuter's flagged correctness requirement: converting the Date/Timestamp to
    * LocalDateTime must use ZoneId.systemDefault(), not a fixed zone like UTC. A Timestamp at
    * 2026-01-15 00:30:00 local time, with the JVM default time zone pinned to GMT+8 (not UTC),
    * must land on 2026-01-15 (serial 46037 + 30 minutes), not get shifted back to 2026-01-14 as
    * it would if the conversion used ZoneOffset.UTC instead of the system default zone.
    */
   @Test
   void convertsNearMidnightTimestampUsingSystemDefaultZoneNotUtc() throws IOException {
      Locale.setDefault(Locale.US);
      TimeZone.setDefault(FIXED_ZONE_GMT8);

      ZonedDateTime localMidnightThirty =
         ZonedDateTime.of(2026, 1, 15, 0, 30, 0, 0, ZoneId.of(FIXED_ZONE_GMT8.getID()));
      Timestamp timestamp = Timestamp.from(localMidnightThirty.toInstant());

      // Correct (system-default-zone) serial: 2026-01-15 00:30:00 -> 46037 + 30/1440 minutes.
      double expectedUsingSystemDefaultZone = EXPECTED_SERIAL_2026_01_15 + 30.0 / 1440.0;
      // What a wrong ZoneOffset.UTC-based conversion would produce instead: the same instant is
      // 2026-01-14 16:30:00 UTC, i.e. the previous day.
      double wrongIfUtcWereUsed = EXPECTED_SERIAL_2026_01_15 - 1.0 + 16.5 / 24.0;

      double actual = numericValueAfterWrite(timestamp);

      assertEquals(expectedUsingSystemDefaultZone, actual, DELTA,
         "Date -> LocalDateTime conversion must use ZoneId.systemDefault(), matching the " +
         "system default time zone the pre-fix Date-based path implicitly used");
      assertNotEquals(wrongIfUtcWereUsed, actual, DELTA,
         "a fixed-zone (e.g. UTC) conversion would wrongly shift this near-midnight " +
         "timestamp onto the previous day");
   }

   // the correct, locale-independent Excel serial for 2026-01-15 00:00:00 (matches the diagnosis
   // and refutation's independently-reproduced control value against real POI 5.4.0)
   private static final double EXPECTED_SERIAL_2026_01_15 = 46037.0;
   private static final double DELTA = 1e-6;
   private static final TimeZone FIXED_ZONE_GMT8 = TimeZone.getTimeZone("GMT+08:00");

   private static void assertSerialForMidnight(Locale locale, double expectedSerial)
      throws IOException
   {
      Locale.setDefault(locale);
      TimeZone.setDefault(FIXED_ZONE_GMT8);

      Date date = Date.from(
         ZonedDateTime.of(2026, 1, 15, 0, 0, 0, 0, ZoneId.of(FIXED_ZONE_GMT8.getID())).toInstant());

      assertEquals(expectedSerial, numericValueAfterWrite(date), DELTA);
   }

   /**
    * Writes {@code value} into a real POI cell via the method under test and returns the raw
    * numeric value POI would persist into the .xlsx for that cell.
    */
   private static double numericValueAfterWrite(Object value) throws IOException {
      try(XSSFWorkbook workbook = new XSSFWorkbook()) {
         Sheet sheet = workbook.createSheet();
         Row row = sheet.createRow(0);
         Cell cell = row.createCell(0);

         PoiExcelVSUtil.setCellValue(cell, value, null);

         return cell.getNumericCellValue();
      }
   }
}
