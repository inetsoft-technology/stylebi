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
package inetsoft.util;

import inetsoft.test.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class ExtendedDateFormatTest {
   @Test
   public void localTime() {
      final ExtendedDateFormat format = new ExtendedDateFormat(Tool.DEFAULT_TIME_PATTERN);
      final Date expected = Date.from(LocalDateTime.of(1970, 1, 1, 1, 2, 3)
                                         .atZone(ZoneId.systemDefault()).toInstant());
      final Date actual = format.parse("01:02:03", null);

      assertEquals(expected, actual);
   }

   @Test
   public void localDate() {
      final ExtendedDateFormat format = new ExtendedDateFormat(Tool.DEFAULT_DATE_PATTERN);
      final Date expected = Date.from(LocalDateTime.of(2020, 1, 2, 0, 0)
                                         .atZone(ZoneId.systemDefault()).toInstant());
      final Date actual = format.parse("2020-01-02", null);

      assertEquals(expected, actual);
   }

   @Test
   public void localDateTime() {
      final ExtendedDateFormat format = new ExtendedDateFormat(Tool.DEFAULT_DATETIME_PATTERN);
      final Date expected = Date.from(LocalDateTime.of(2020, 1, 2, 1, 2, 3)
                                         .atZone(ZoneId.systemDefault()).toInstant());
      final Date actual = format.parse("2020-01-02 01:02:03", null);

      assertEquals(expected, actual);
   }

   @Test
   public void testQQ() {
      final ExtendedDateFormat format = new ExtendedDateFormat("'Q'QQ'('MMM')'");
      final Date date = Date.from(LocalDateTime.of(2020, 2, 2, 1, 2, 3)
                                     .atZone(ZoneId.systemDefault()).toInstant());
      final String actual = format.format(date);
      Locale currentLocale = Locale.getDefault();

      if(currentLocale.equals(Locale.US)) {
         assertEquals("Q1(Feb)", actual);
      }
   }

   @Test
   public void monthYear() {
      final ExtendedDateFormat format = new ExtendedDateFormat("yyyy-M");
      final Date expected = Date.from(LocalDateTime.of(2020, 2, 1, 0, 0, 0)
                                         .atZone(ZoneId.systemDefault()).toInstant());
      final Date actual = format.parse("2020-02", null);

      assertEquals(expected, actual);
   }

   @Test
   public void parsePositionUsesLocale() {
      final ExtendedDateFormat format = new ExtendedDateFormat("hh:mm a", Locale.JAPAN);
      final Date expected = Date.from(LocalDateTime.of(1970, 1, 1, 19, 0)
                                         .atZone(ZoneId.systemDefault()).toInstant());
      final String text = format.format(expected);
      final ParsePosition pos = new ParsePosition(0);

      assertEquals(expected, format.parseObject(text, pos));
      assertEquals(text.length(), pos.getIndex());
   }

   @Test
   public void parsePositionMonthName() {
      final ExtendedDateFormat format = new ExtendedDateFormat("MMMM d, yyyy", Locale.GERMANY);
      final Date expected = Date.from(LocalDateTime.of(2020, 12, 31, 0, 0)
                                         .atZone(ZoneId.systemDefault()).toInstant());
      final String text = format.format(expected);
      final ParsePosition pos = new ParsePosition(0);

      assertEquals(expected, format.parse(text, pos));
      assertEquals(text.length(), pos.getIndex());
   }

   @Test
   public void parsePositionStartsAtIndex() {
      final ExtendedDateFormat format = new ExtendedDateFormat(Tool.DEFAULT_DATE_PATTERN);
      final Date expected = Date.from(LocalDateTime.of(2011, 1, 2, 0, 0)
                                         .atZone(ZoneId.systemDefault()).toInstant());
      final ParsePosition pos = new ParsePosition(5);

      assertEquals(expected, format.parse("XXXX 2011-01-02 tail", pos));
      assertEquals(15, pos.getIndex());
   }

   @Test
   public void parsePositionFailureReturnsNull() {
      final ExtendedDateFormat format = new ExtendedDateFormat("QQQ yyyy", Locale.US);
      final ParsePosition pos = new ParsePosition(0);

      assertNull(format.parseObject("4th 2020", pos));
      assertEquals(0, pos.getIndex());
      assertEquals(0, pos.getErrorIndex());
   }

   // Bug #77416: before 1901 the parsed fields must be converted with the same hybrid
   // Julian/Gregorian calendar and java.util.TimeZone offsets that format() uses, otherwise
   // pre-1582 dates (Julian) and dates in a zone's local mean time era (LMT) move on every
   // save and reload. formats are created after setDefault so they carry the tested zone
   @ParameterizedTest(name = "{0} {2}")
   @CsvSource({
      "Asia/Shanghai, yyyy-MM-dd HH:mm:ss, 1900-12-31 23:30:00",
      "Asia/Shanghai, yyyy-MM-dd, 1882-01-01",
      "Asia/Shanghai, yyyy-MM-dd, 1012-02-29",
      "Asia/Shanghai, yyyy-MM-dd HH:mm:ss, 1012-02-29 10:00:00",
      "Asia/Kolkata, yyyy-MM-dd HH:mm:ss, 1882-06-01 23:59:59",
      "Asia/Kolkata, yyyy-MM-dd, 1600-02-29",
      "America/New_York, yyyy-MM-dd, 1882-06-01",
      "America/New_York, yyyy-MM-dd, 1600-02-29",
      "America/New_York, yyyy-MM-dd, 1582-10-04",
      "America/New_York, yyyy-MM-dd, 1582-10-15",
      "America/New_York, yyyy-MM-dd, 1500-02-29",
      "America/New_York, yyyy-MM-dd, 1012-02-29",
      "America/New_York, yyyy-MM-dd, 0001-01-01",
      "America/New_York, yyyy-MM-dd HH:mm:ss.SSS, 1012-02-29 10:00:00.123",
      "America/New_York, MM/dd/yyyy, 02/29/1012",
      "UTC, yyyy-MM-dd, 1012-02-29"
   })
   public void pre1901DateSurvivesFormatAndParse(String zone, String pattern, String text)
      throws Exception
   {
      final TimeZone oldZone = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone(zone));
         final ExtendedDateFormat format = new ExtendedDateFormat(pattern, Locale.US);
         final Date parsed = (Date) format.parseObject(text);
         final String saved = format.format(parsed);

         assertEquals(text, saved);
         assertEquals(parsed, format.parseObject(saved));
         assertEquals(new SimpleDateFormat(pattern, Locale.US).parse(text), parsed);
      }
      finally {
         TimeZone.setDefault(oldZone);
      }
   }

   // Bug #77416: from 1901 on the java.time conversion is kept, including its choice of the
   // earlier offset in a daylight saving overlap
   @ParameterizedTest(name = "{0} {1}")
   @CsvSource({
      "America/New_York, 2024-11-03 01:30:00",
      "America/New_York, 1901-01-01 00:00:00",
      "Asia/Shanghai, 1901-01-01 00:00:00",
      "Asia/Shanghai, 2024-02-29 10:00:00"
   })
   public void post1900DateUsesJavaTime(String zone, String text) {
      final TimeZone oldZone = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone(zone));
         final ExtendedDateFormat format = new ExtendedDateFormat(Tool.DEFAULT_DATETIME_PATTERN);
         final Date expected = Date.from(
            LocalDateTime.parse(text.replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant());

         assertEquals(expected, format.parse(text, null));
      }
      finally {
         TimeZone.setDefault(oldZone);
      }
   }

   // Bug #77444: an invalid day of month is clamped to the last day of the month on both
   // sides of 1901 instead of rolling over into the next month before 1901. the last day is
   // the one of the calendar format() uses, so the Julian 1500-02-29 is kept. single "y"
   // patterns keep the two digit year window of SimpleDateFormat
   @ParameterizedTest(name = "{0} {1} {2}")
   @CsvSource({
      "America/New_York, yyyy-MM-dd, 1850-02-30, 1850-02-28",
      "America/New_York, yyyy-MM-dd, 1900-02-29, 1900-02-28",
      "America/New_York, yyyy-MM-dd, 1850-04-31, 1850-04-30",
      "America/New_York, yyyy-MM-dd, 1500-02-30, 1500-02-29",
      "America/New_York, yyyy-MM-dd, 1901-02-29, 1901-02-28",
      "America/New_York, yyyy-MM-dd, 2023-02-30, 2023-02-28",
      "America/New_York, yyyy-MM-dd HH:mm:ss, 1850-02-30 10:11:12, 1850-02-28 10:11:12",
      "America/New_York, yyyy-MM-dd HH:mm:ss, 2023-02-30 10:11:12, 2023-02-28 10:11:12",
      "America/New_York, dd MMM yyyy, 30 Feb 1850, 28 Feb 1850",
      "America/New_York, M/d/y, 2/30/50, 2/28/1950",
      "America/New_York, M/d/y, 4/31/99, 4/30/1999",
      "Asia/Shanghai, yyyy-MM-dd, 1850-02-30, 1850-02-28",
      "Asia/Shanghai, yyyy-MM-dd, 1900-02-29, 1900-02-28",
      "Asia/Shanghai, yyyy-MM-dd, 1500-02-30, 1500-02-29",
      "Asia/Shanghai, yyyy-MM-dd, 2023-02-30, 2023-02-28",
      "Asia/Shanghai, yyyy-MM-dd HH:mm:ss, 1850-02-30 10:11:12, 1850-02-28 10:11:12",
      "Asia/Shanghai, M/d/y, 2/30/50, 2/28/1950"
   })
   public void invalidDayClampsToLastDayOfMonth(String zone, String pattern, String text,
                                                String expected) throws Exception
   {
      final TimeZone oldZone = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone(zone));
         final ExtendedDateFormat format = new ExtendedDateFormat(pattern, Locale.US);
         final SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
         final Date parsed = format.parse(text);

         assertEquals(sdf.parse(expected), parsed);
         assertEquals(parsed, format.parseObject(text));
      }
      finally {
         TimeZone.setDefault(oldZone);
      }
   }

   // Bug #77444: the strict SimpleDateFormat attempt before 1901 must not change the result
   // for valid dates SimpleDateFormat only accepts leniently, such as years <= 0 (stored for
   // BC dates), the 1582 cutover gap and 24:00
   @ParameterizedTest(name = "{0} {1} {2}")
   @CsvSource({
      "America/New_York, yyyy-MM-dd, 0000-01-01, -62167374000000",
      "America/New_York, yyyy-MM-dd, -0043-03-15, -63517978800000",
      "America/New_York, yyyy-MM-dd HH:mm:ss, -0043-03-15 10:00:00, -63517942800000",
      "America/New_York, yyyy-MM-dd, 1582-10-10, -12218842800000",
      "America/New_York, yyyy-MM-dd HH:mm:ss, 1850-02-28 24:00:00, -3781710000000",
      "America/New_York, M/d/y, 2/28/50, -626122800000",
      "Asia/Shanghai, yyyy-MM-dd, 0000-01-01, -62167420800000",
      "Asia/Shanghai, yyyy-MM-dd, -0043-03-15, -63518025600000"
   })
   public void pre1901LenientInputIsUnchanged(String zone, String pattern, String text,
                                              long expected) throws Exception
   {
      final TimeZone oldZone = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone(zone));
         final ExtendedDateFormat format = new ExtendedDateFormat(pattern, Locale.US);

         assertEquals(new Date(expected), format.parse(text));
         assertEquals(new Date(expected), format.parseObject(text));
      }
      finally {
         TimeZone.setDefault(oldZone);
      }
   }

   // Bug #77444: the step back to the last day of the month must only undo a day overflow.
   // without a month or a day field, or when the lenient result is not exactly one month
   // after the java.time month (week patterns, where java.time reads "u" as the year, an
   // offset moving 24:00 across a month, a Julian day of year), the lenient result is kept
   @ParameterizedTest(name = "{0} {1} {2}")
   @CsvSource(quoteCharacter = '"', value = {
      "America/New_York, YYYY-'W'ww-u, 2020-W53-1",
      "America/New_York, YYYY-'W'ww-u, 1850-W53-1",
      "Asia/Shanghai, YYYY-'W'ww-u, 2020-W53-1",
      "America/New_York, yyyy-MM-dd HH:mm XXX, 1850-01-31 24:00 +00:00",
      "America/New_York, yyyy-DDD HH:mm, 1500-059 24:00",
      "America/New_York, yyyy dd HH:mm, 1850 31 24:00",
      "America/New_York, yyyy-MM HH:mm, 1850-02 24:00"
   })
   public void pre1901NoDayOverflowKeepsLenientResult(String zone, String pattern, String text)
      throws Exception
   {
      final TimeZone oldZone = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone(zone));
         final ExtendedDateFormat format = new ExtendedDateFormat(pattern, Locale.US);
         final Date expected = new SimpleDateFormat(pattern, Locale.US).parse(text);

         assertEquals(expected, format.parse(text));
         assertEquals(expected, format.parseObject(text));
      }
      finally {
         TimeZone.setDefault(oldZone);
      }
   }

   // Bug #77444: the last valid day follows the hybrid calendar on both sides of the 1582
   // cutover (the Julian 1300 is a leap year, the Gregorian 1700 is not), also when the zone
   // set on the format differs from the JVM default
   @ParameterizedTest(name = "{0} {1} {2}")
   @CsvSource({
      "America/New_York, America/New_York, 1300-02-30, 1300-02-29",
      "America/New_York, America/New_York, 1300-02-31, 1300-02-29",
      "America/New_York, America/New_York, 1700-02-29, 1700-02-28",
      "America/New_York, America/New_York, 1582-11-31, 1582-11-30",
      "America/New_York, Asia/Tokyo, 1850-02-30, 1850-02-28",
      "Asia/Shanghai, Asia/Tokyo, 1300-02-30, 1300-02-29"
   })
   public void invalidDayClampsInHybridCalendarAndFormatZone(String defaultZone, String zone,
                                                             String text, String expected)
      throws Exception
   {
      final TimeZone oldZone = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone(defaultZone));
         final ExtendedDateFormat format = new ExtendedDateFormat("yyyy-MM-dd", Locale.US);
         final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
         format.setTimeZone(TimeZone.getTimeZone(zone));
         sdf.setTimeZone(TimeZone.getTimeZone(zone));

         assertEquals(sdf.parse(expected), format.parse(text));
         assertEquals(sdf.parse(expected), format.parseObject(text));
      }
      finally {
         TimeZone.setDefault(oldZone);
      }
   }

   // Bug #77444: the strict attempt before 1901 runs on a clone, so the format itself stays
   // lenient and a later parse with a ParsePosition still rolls an invalid day over
   @Test
   public void strictPre1901AttemptLeavesFormatLenient() throws Exception {
      final TimeZone zone = TimeZone.getTimeZone("America/New_York");
      final ExtendedDateFormat format = new ExtendedDateFormat("yyyy-MM-dd", Locale.US);
      final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
      format.setTimeZone(zone);
      sdf.setTimeZone(zone);

      assertEquals(sdf.parse("1850-02-28"), format.parse("1850-02-30"));
      assertTrue(format.isLenient());
      assertEquals(sdf.parse("1850-03-02"), format.parse("1850-02-30", new ParsePosition(0)));
   }

   // Bug #77443: parse(String) must convert in the zone of the format, like format() and
   // parse(String, ParsePosition), not in the JVM default zone. the format is created after
   // the default is changed so that only setTimeZone() makes the two zones differ
   @ParameterizedTest(name = "{0} {1} {2} {3}")
   @CsvSource({
      "America/Los_Angeles, UTC, yyyy-MM-dd HH:mm:ss, 2025-01-01 12:00:00, 2025-01-01T12:00:00Z",
      "America/Los_Angeles, Asia/Shanghai, yyyy-MM-dd HH:mm:ss, 2025-01-01 20:00:00, 2025-01-01T12:00:00Z",
      "America/Los_Angeles, UTC, HH:mm:ss, 12:00:00, 1970-01-01T12:00:00Z",
      "UTC, America/New_York, yyyy-MM-dd, 2025-01-01, 2025-01-01T05:00:00Z",
      "America/Los_Angeles, Asia/Shanghai, yyyy-MM-dd HH:mm:ss, 1901-01-01 01:00:00, 1900-12-31T17:00:00Z",
      "America/Los_Angeles, Asia/Shanghai, yyyy-MM-dd HH:mm:ss, 1900-12-31 23:00:00, 1900-12-31T14:54:17Z"
   })
   public void parseUsesZoneOfFormat(String defaultZone, String zone, String pattern, String text,
                                     String expected) throws Exception
   {
      final TimeZone oldZone = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone(defaultZone));
         final ExtendedDateFormat format = new ExtendedDateFormat(pattern, Locale.US);
         format.setTimeZone(TimeZone.getTimeZone(zone));
         final Date parsed = format.parse(text);

         assertEquals(Date.from(Instant.parse(expected)), parsed);
         assertEquals(format.parse(text, new ParsePosition(0)), parsed);
         assertEquals(parsed, format.parseObject(text));
         assertEquals(text, format.format(parsed));
      }
      finally {
         TimeZone.setDefault(oldZone);
      }
   }

   // Bug #77443: a daylight saving gap is resolved with the transitions of the format's zone
   @Test
   public void parseUsesZoneOfFormatInDaylightSavingGap() throws Exception {
      final TimeZone oldZone = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
         final ExtendedDateFormat format = new ExtendedDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
         format.setTimeZone(TimeZone.getTimeZone("America/New_York"));
         final String text = "2024-03-10 02:30:00";

         assertEquals(Date.from(Instant.parse("2024-03-10T07:30:00Z")), format.parse(text));
         assertEquals(format.parse(text, new ParsePosition(0)), format.parse(text));
      }
      finally {
         TimeZone.setDefault(oldZone);
      }
   }

   // Bug #77443: a daylight saving overlap takes the earlier offset of the format's zone, the
   // same choice made when the format's zone is the default (see post1900DateUsesJavaTime)
   @Test
   public void parseUsesZoneOfFormatInDaylightSavingOverlap() throws Exception {
      final TimeZone oldZone = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
         final ExtendedDateFormat format = new ExtendedDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
         format.setTimeZone(TimeZone.getTimeZone("America/New_York"));
         final Date daylight = Date.from(Instant.parse("2024-11-03T05:30:00Z"));
         final String text = format.format(daylight);

         assertEquals("2024-11-03 01:30:00", text);
         assertEquals(daylight, format.parse(text));
         assertEquals(daylight, format.parseObject(text));
      }
      finally {
         TimeZone.setDefault(oldZone);
      }
   }

   // Bug #77441: an input java.time rejects must not switch the instance to SimpleDateFormat
   // for later inputs. results are compared with a fresh instance instead of fixed values
   // because the java.time answers for invalid days and zones are owned by other fixes
   @ParameterizedTest(name = "{0} {1}")
   @CsvSource({
      "yyyy-MM-dd, 2011-02-30",
      "yyyy-MM-dd, 2011-02-29",
      "yyyy-MM-dd, 1300000000000",
      "yyyy-MM-dd, 2011-01-02",
      "HH:mm:ss, 24:00:00",
      "yyyy-MM-dd HH:mm:ss, 2024-11-03 01:30:00",
      "M/d/yy, 1/2/50"
   })
   public void failedParseDoesNotChangeLaterResults(String pattern, String text) {
      final ExtendedDateFormat fresh = new ExtendedDateFormat(pattern, Locale.US);
      final ExtendedDateFormat poisoned = new ExtendedDateFormat(pattern, Locale.US);
      poison(poisoned);

      assertEquals(parseResult(fresh, text), parseResult(poisoned, text));
      assertEquals(parseResult(fresh, text), parseResult(poisoned, text));
   }

   // Bug #77441: parse(String) had the same sticky fallback as parseObject(String)
   @Test
   public void failedParseDoesNotChangeLaterParse() throws Exception {
      final ExtendedDateFormat fresh = new ExtendedDateFormat("yyyy-MM-dd", Locale.US);
      final ExtendedDateFormat poisoned = new ExtendedDateFormat("yyyy-MM-dd", Locale.US);
      poison(poisoned);

      assertEquals(fresh.parse("2011-02-30"), poisoned.parse("2011-02-30"));
   }

   // Bug #77441: a zone other than the JVM default must give the same result before and
   // after a failed parse
   @Test
   public void failedParseDoesNotChangeZonedResult() {
      final ExtendedDateFormat fresh = new ExtendedDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
      final ExtendedDateFormat poisoned = new ExtendedDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
      final TimeZone zone = TimeZone.getTimeZone(
         "UTC".equals(TimeZone.getDefault().getID()) ? "Asia/Shanghai" : "UTC");
      fresh.setTimeZone(zone);
      poisoned.setTimeZone(zone);
      poison(poisoned);

      assertEquals(parseResult(fresh, "2020-01-01 00:00:00"),
                   parseResult(poisoned, "2020-01-01 00:00:00"));
   }

   // Bug #77441: FormatCache clones the prototype and hands the clones out round robin, so a
   // clone that failed once must not answer differently from the others
   @Test
   public void formatCacheSlotsAgreeAfterFailedParse() {
      final FormatCache cache = new FormatCache(new ExtendedDateFormat("yyyy-MM-dd", Locale.US));
      parseResult(cache, "2011-01-02 10:01:02");
      final Set<Object> results = new HashSet<>();

      for(int i = 0; i < 512; i++) {
         results.add(parseResult(cache, "2011-02-30"));
      }

      assertEquals(1, results.size(), results::toString);
   }

   // Bug #77441: CoreTool.parseDate() uses a process wide FormatCache, so one failed parse
   // anywhere in the server must not change what later callers get, including epoch millis
   @Test
   public void coreToolParseDateAgreesAfterFailedParse() {
      parseDateResult("2011-01-02 10:01:02");
      final Set<Object> results = new HashSet<>();
      final Set<Object> epochResults = new HashSet<>();

      for(int i = 0; i < 512; i++) {
         results.add(parseDateResult("2011-02-30"));
      }

      for(int i = 0; i < 512; i++) {
         epochResults.add(parseDateResult("1300000000000"));
      }

      assertEquals(1, results.size(), results::toString);
      assertEquals(Set.of(new Date(1300000000000L)), epochResults);
   }

   // Bug #77441: a clone of a format that already failed a parse behaves like a fresh format
   @Test
   public void cloneOfFailedFormatMatchesFresh() {
      final ExtendedDateFormat prototype = new ExtendedDateFormat("HH:mm:ss", Locale.US);
      poison(prototype);
      final ExtendedDateFormat clone = (ExtendedDateFormat) prototype.clone();
      final ExtendedDateFormat fresh = new ExtendedDateFormat("HH:mm:ss", Locale.US);

      assertEquals(parseResult(fresh, "24:00:00"), parseResult(clone, "24:00:00"));
   }

   // Bug #77441: java.time cannot compile the extended quarter patterns. that is remembered
   // per pattern, and the pattern parses the same way on every call
   @Test
   public void quarterPatternIsRememberedAsUnsupported() {
      final ExtendedDateFormat format = new ExtendedDateFormat("QQQ yyyy", Locale.US);
      final String pattern = format.toPattern();
      final String text = format.format(
         Date.from(LocalDateTime.of(2011, 2, 1, 0, 0).atZone(ZoneId.systemDefault()).toInstant()));
      final Object first = parseResult(format, text);

      assertTrue(ExtendedDateFormat.isUnsupportedPattern(pattern));
      assertEquals(first, parseResult(format, text));
      assertEquals(first, parseResult(new ExtendedDateFormat("QQQ yyyy", Locale.US), text));
      assertEquals(parseResult(format, "not a quarter"), parseResult(format, "not a quarter"));
      assertThrows(IllegalArgumentException.class, () -> format.parse(text, null));
   }

   // Bug #77441: a zone without a java.time ID must fall back to SimpleDateFormat for the
   // call instead of throwing ZoneRulesException out of parse(String)/parseObject(String)
   @Test
   public void zoneWithoutJavaTimeIdFallsBack() throws Exception {
      final TimeZone oldZone = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
         final ExtendedDateFormat format = new ExtendedDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
         format.setTimeZone(new SimpleTimeZone(3600000, "Custom"));
         final String text = "2025-01-01 13:00:00";
         final Date expected = Date.from(Instant.parse("2025-01-01T12:00:00Z"));

         assertEquals(expected, format.parse(text));
         assertEquals(expected, format.parseObject(text));
         assertEquals(expected, format.parse(text, new ParsePosition(0)));
      }
      finally {
         TimeZone.setDefault(oldZone);
      }
   }

   private static void poison(ExtendedDateFormat format) {
      // java.time rejects it, which used to switch the instance to SimpleDateFormat for good
      assertEquals(ParseException.class, parseResult(format, "not a date"));
   }

   private static Object parseResult(ExtendedDateFormat format, String text) {
      try {
         return format.parseObject(text);
      }
      catch(ParseException ex) {
         return ParseException.class;
      }
   }

   private static Object parseDateResult(String text) {
      try {
         return CoreTool.parseDate(text, null);
      }
      catch(ParseException ex) {
         return ParseException.class;
      }
   }

   private static Object parseResult(FormatCache cache, String text) {
      try {
         return cache.parse(text);
      }
      catch(ParseException ex) {
         return ParseException.class;
      }
   }

   // Bug #77465: the java.time fast path kept only the local fields, so the offset or zone
   // name parsed by z, Z or X was replaced by another zone. both String overloads must give
   // what SimpleDateFormat and parse(String, ParsePosition) give. the JVM zone, the format
   // zone and the parsed offset all differ, so a wrong zone cannot match by coincidence
   @ParameterizedTest(name = "{0} {1}")
   @CsvSource(delimiter = '|', value = {
      "yyyy-MM-dd HH:mm:ss Z | 2025-01-01 12:00:00 +0000",
      "yyyy-MM-dd HH:mm:ss Z | 2025-01-01 12:00:00 +0530",
      "yyyy-MM-dd HH:mm:ss Z | 2025-07-01 12:00:00 -0400",
      "yyyy-MM-dd HH:mm:ss X | 2025-01-01 12:00:00 Z",
      "yyyy-MM-dd HH:mm:ss X | 2025-01-01 12:00:00 +05",
      "yyyy-MM-dd HH:mm:ss XX | 2025-01-01 12:00:00 +0530",
      "yyyy-MM-dd HH:mm:ss XXX | 2025-01-01 12:00:00 +05:30",
      "yyyy-MM-dd'T'HH:mm:ssXXX | 2025-01-01T12:00:00Z",
      "yyyy-MM-dd HH:mm:ss z | 2025-01-01 12:00:00 EST",
      "yyyy-MM-dd HH:mm:ss z | 2025-01-01 12:00:00 UTC",
      "yyyy-MM-dd HH:mm:ss z | 2025-01-01 12:00:00 GMT+05:30",
      "yyyy-MM-dd HH:mm:ss z | 2025-07-01 12:00:00 PST",
      "yyyy-MM-dd HH:mm:ss zzzz | 2025-01-01 12:00:00 Eastern Standard Time",
      "EEE MMM dd HH:mm:ss zzz yyyy | Wed Jan 01 12:00:00 EST 2025",
      "HH:mm Z | 12:00 +0000",
      "HH:mm:ss z | 12:00:00 EST",
      "yyyy-MM-dd Z | 2025-01-01 +0900",
      "HH 'o''clock' Z | 12 o'clock +0000",
      "yyyy-MM-dd HH:mm:ss Z | 1850-01-01 12:00:00 +0000",
      "yyyy-MM-dd HH:mm:ss Z | 1900-12-31 12:00:00 +0530"
   })
   public void zonePatternParsesLikeSimpleDateFormat(String pattern, String text)
      throws Exception
   {
      final TimeZone oldZone = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
         final TimeZone zone = TimeZone.getTimeZone("Asia/Tokyo");
         final ExtendedDateFormat format = new ExtendedDateFormat(pattern, Locale.US);
         final SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
         format.setTimeZone(zone);
         sdf.setTimeZone(zone);
         final Date expected = sdf.parse(text);

         assertEquals(expected, format.parse(text));
         assertEquals(expected, format.parseObject(text));
         assertEquals(expected, format.parse(text, new ParsePosition(0)));
         assertThrows(IllegalArgumentException.class, () -> format.parse(text, null));
      }
      finally {
         TimeZone.setDefault(oldZone);
      }
   }

   // Bug #77465: a quoted Z is a literal, so the pattern has no zone field and keeps the
   // java.time fast path
   @ParameterizedTest(name = "{0} {1}")
   @CsvSource(delimiter = '|', value = {
      "yyyy-MM-dd'T'HH:mm:ss'Z' | 2025-01-01T12:00:00Z",
      "yyyy-MM-dd 'it''s Z' | 2025-01-01 it's Z"
   })
   public void quotedZoneLetterKeepsJavaTime(String pattern, String text) throws Exception {
      final TimeZone oldZone = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
         final ExtendedDateFormat format = new ExtendedDateFormat(pattern, Locale.US);
         final Date expected = new SimpleDateFormat(pattern, Locale.US).parse(text);

         assertEquals(expected, format.parse(text, null));
         assertEquals(expected, format.parse(text));
         assertEquals(expected, format.parseObject(text));
      }
      finally {
         TimeZone.setDefault(oldZone);
      }
   }

   // Bug #77465: SimpleDateFormat only accepts the zone names of the locale for z, so a
   // region id fails to parse instead of giving a date with the zone ignored
   @Test
   public void zoneNamePatternRejectsRegionId() {
      final TimeZone oldZone = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
         final String pattern = "yyyy-MM-dd HH:mm:ss z";
         final String text = "2025-01-01 12:00:00 America/New_York";
         final ExtendedDateFormat format = new ExtendedDateFormat(pattern, Locale.US);

         assertThrows(ParseException.class,
                      () -> new SimpleDateFormat(pattern, Locale.US).parse(text));
         assertThrows(ParseException.class, () -> format.parse(text));
         assertThrows(ParseException.class, () -> format.parseObject(text));
         assertNull(format.parse(text, new ParsePosition(0)));
      }
      finally {
         TimeZone.setDefault(oldZone);
      }
   }

   // Bug #77465: the zone field check follows applyPattern() on the same instance, and a
   // lowercase z inside a quoted literal is not a zone field
   @Test
   public void zoneFieldFollowsApplyPattern() throws Exception {
      final TimeZone oldZone = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
         final String plain = "yyyy-MM-dd 'zone z' HH:mm";
         final String zoned = "yyyy-MM-dd HH:mm Z";
         final ExtendedDateFormat format = new ExtendedDateFormat(plain, Locale.US);
         final Date plainDate =
            new SimpleDateFormat(plain, Locale.US).parse("2025-01-01 zone z 12:00");

         assertEquals(plainDate, format.parse("2025-01-01 zone z 12:00", null));

         format.applyPattern(zoned);
         assertEquals(Date.from(Instant.parse("2025-01-01T12:00:00Z")),
                      format.parse("2025-01-01 12:00 +0000"));
         assertThrows(IllegalArgumentException.class,
                      () -> format.parse("2025-01-01 12:00 +0000", null));

         format.applyPattern(plain);
         assertEquals(plainDate, format.parse("2025-01-01 zone z 12:00", null));
      }
      finally {
         TimeZone.setDefault(oldZone);
      }
   }

   // Bug #77465: SimpleDateFormat sets the zone of the format from a parsed zone name. a
   // shared or cached format must keep its zone, otherwise every later format() prints in
   // the zone of whatever was parsed last
   @ParameterizedTest(name = "{0} {1}")
   @CsvSource(delimiter = '|', value = {
      "America/New_York | 2011-03-10 14:05 PST",
      "America/New_York | 2011-03-10 14:05 PST tail",
      "America/New_York | 1850-01-01 12:00 PST",
      "America/New_York | 2011-03-10 14:05 GMT+05:30",
      "Asia/Tokyo | 2011-03-10 14:05 PST"
   })
   public void parseKeepsZoneOfFormat(String zoneId, String text) throws Exception {
      final TimeZone oldZone = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
         final String pattern = "yyyy-MM-dd HH:mm z";
         final TimeZone zone = TimeZone.getTimeZone(zoneId);
         final Date other = Date.from(Instant.parse("2011-07-01T12:00:00Z"));
         final ExtendedDateFormat fresh = new ExtendedDateFormat(pattern, Locale.US);
         fresh.setTimeZone(zone);
         final String expected = fresh.format(other);
         final List<ThrowingFunction> parsers = List.of(
            f -> f.parse(text), f -> f.parseObject(text), f -> f.parse(text, new ParsePosition(0)));

         for(ThrowingFunction parser : parsers) {
            final ExtendedDateFormat format = new ExtendedDateFormat(pattern, Locale.US);
            format.setTimeZone(zone);

            assertNotNull(parser.apply(format));
            assertEquals(zoneId, format.getTimeZone().getID());
            assertEquals(expected, format.format(other));
         }
      }
      finally {
         TimeZone.setDefault(oldZone);
      }
   }

   private interface ThrowingFunction {
      Object apply(ExtendedDateFormat format) throws Exception;
   }

   // Bug #77458: the java.time formatter was built in the JVM locale and shared by every
   // format locale, so parse(String) accepted JVM-language text, used the JVM week rules and
   // ignored a non-Gregorian calendar. both String overloads must give what SimpleDateFormat
   // in the format locale gives, for the text of the format locale and for en_US text. the
   // text is made by SimpleDateFormat so it does not depend on the locale data provider.
   // dates are always Gregorian (Bug #77605), so SimpleDateFormat uses the Gregorian calendar
   @ParameterizedTest(name = "{0} {1}")
   @CsvSource(delimiter = '|', value = {
      "de | MMM d, yyyy",
      "de | MMMM d, yyyy",
      "de | EEE yyyy-MM-dd",
      "de | yyyy-MM-dd G",
      "fr | MMMM d, yyyy",
      "fr | EEEE yyyy-MM-dd",
      "zh-CN | yyyy-MM-dd hh:mm a",
      "en-GB | YYYY-ww-EEE",
      "en-US-u-rg-gbzzzz | YYYY-ww-EEE",
      "th-TH | yyyy-MM-dd",
      "en-US-u-ca-buddhist | yyyy-MM-dd",
      "ja-JP-u-ca-japanese-x-lvariant-JP | yyyy-MM-dd",
      "en-US | MMM d, yyyy",
      "en-US | EEE yyyy-MM-dd hh:mm a"
   })
   public void localeParsesLikeSimpleDateFormat(String tag, String pattern) {
      final Locale locale = Locale.forLanguageTag(tag);
      final Date date = Date.from(LocalDateTime.of(2011, 3, 10, 14, 0)
                                     .atZone(ZoneId.systemDefault()).toInstant());
      final String localText = CoreTool.createGregorianDateFormat(pattern, locale).format(date);
      final String usText = new SimpleDateFormat(pattern, Locale.US).format(date);

      for(String text : new String[] { localText, usText }) {
         final ExtendedDateFormat format = new ExtendedDateFormat(pattern, locale);
         final Object expected = parseOrError(
            () -> CoreTool.createGregorianDateFormat(pattern, locale).parse(text));

         assertEquals(expected, parseOrError(() -> format.parse(text)), text);
         assertEquals(expected, parseOrError(() -> (Date) format.parseObject(text)), text);
         assertEquals(expected == ParseException.class ? null : expected,
                      format.parse(text, new ParsePosition(0)), text);
      }
   }

   // Bug #77458: the java.time path ignored the buddhist and japanese imperial calendars of
   // the locale, so a formatted date could parse back hundreds of years off. Bug #77605: the
   // calendar is Gregorian for these locales too, so the year is the Gregorian year and
   // java.time parses it
   @ParameterizedTest(name = "{0}")
   @CsvSource({ "th-TH", "ja-JP-u-ca-japanese-x-lvariant-JP" })
   public void nonGregorianLocaleRoundTrip(String tag) throws Exception {
      final Locale locale = Locale.forLanguageTag(tag);
      final ExtendedDateFormat format = new ExtendedDateFormat("yyyy-MM-dd", locale);
      final Date date = Date.from(LocalDateTime.of(2025, 3, 3, 0, 0)
                                     .atZone(ZoneId.systemDefault()).toInstant());
      final String text = format.format(date);

      assertEquals("2025-03-03", text);
      assertEquals(date, format.parse(text));
      assertEquals(date, format.parseObject(text));
      assertEquals(date, format.parse(text, null));
   }

   // Bug #77458: the shared formatter was keyed by pattern and zone only, so the locale of
   // the first format to parse a pattern was used by every later format with that pattern
   @Test
   public void formatterCacheKeysOnLocale() throws Exception {
      final String pattern = "MMM d, yyyy '77458'";
      final Date date = Date.from(LocalDateTime.of(2011, 3, 3, 0, 0)
                                     .atZone(ZoneId.systemDefault()).toInstant());
      final String usText = new SimpleDateFormat(pattern, Locale.US).format(date);
      final String deText = new SimpleDateFormat(pattern, Locale.GERMANY).format(date);
      final ExtendedDateFormat us = new ExtendedDateFormat(pattern, Locale.US);
      final ExtendedDateFormat de = new ExtendedDateFormat(pattern, Locale.GERMANY);

      assertEquals(date, us.parse(usText, null));
      assertEquals(date, de.parse(deText));
      assertEquals(parseOrError(() -> new SimpleDateFormat(pattern, Locale.GERMANY).parse(usText)),
                   parseOrError(() -> de.parse(usText)));
      assertEquals(date, us.parse(usText));
   }

   // Bug #77458: the calendar type is checked for each call, since setCalendar() can switch
   // between a Gregorian and a non-Gregorian calendar
   @Test
   public void setCalendarSwitchesParser() throws Exception {
      final String pattern = "yyyy-MM-dd";
      final Locale thai = Locale.forLanguageTag("th-TH");
      final TimeZone zone = TimeZone.getDefault();
      final ExtendedDateFormat us = new ExtendedDateFormat(pattern, Locale.US);
      final SimpleDateFormat usSdf = new SimpleDateFormat(pattern, Locale.US);
      us.setCalendar(Calendar.getInstance(zone, thai));
      usSdf.setCalendar(Calendar.getInstance(zone, thai));

      assertEquals(usSdf.parse("2554-03-03"), us.parse("2554-03-03"));
      assertThrows(IllegalArgumentException.class, () -> us.parse("2554-03-03", null));

      final ExtendedDateFormat th = new ExtendedDateFormat(pattern, thai);
      final SimpleDateFormat thSdf = new SimpleDateFormat(pattern, thai);
      th.setCalendar(new GregorianCalendar(zone, thai));
      thSdf.setCalendar(new GregorianCalendar(zone, thai));

      assertEquals(thSdf.parse("2011-03-03"), th.parse("2011-03-03"));
      assertEquals(thSdf.parse("2011-03-03"), th.parse("2011-03-03", null));
   }

   // Bug #77458: without a locale, SimpleDateFormat uses the default format locale at
   // construction, so the java.time path must use that one too and not the current default
   @Test
   public void defaultLocaleIsTakenAtConstruction() {
      final Locale oldLocale = Locale.getDefault(Locale.Category.FORMAT);
      final String pattern = "MMM d, yyyy";
      final String text = "Mar 3, 2011";

      try {
         Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY);
         final ExtendedDateFormat format = new ExtendedDateFormat(pattern);
         final SimpleDateFormat sdf = new SimpleDateFormat(pattern);
         Locale.setDefault(Locale.Category.FORMAT, Locale.US);

         assertEquals(parseOrError(() -> sdf.parse(text)), parseOrError(() -> format.parse(text)));
         assertEquals(parseOrError(() -> sdf.parse(text)),
                      parseOrError(() -> (Date) format.parseObject(text)));
      }
      finally {
         Locale.setDefault(Locale.Category.FORMAT, oldLocale);
      }
   }

   // Bug #77458: java.time resolves the week fields to a date only for a week date (Y, w
   // and E), and parse(str, null) then read Jan 1 or the first of the month. other week
   // patterns must give what SimpleDateFormat gives, for text in any locale
   @ParameterizedTest(name = "{0} {1}")
   @CsvSource(delimiter = '|', value = {
      "en-US | yyyy-ww",
      "en-GB | yyyy-ww",
      "en-GB | yyyy-ww-EEE",
      "de | yyyy-'W'ww-EEE",
      "fr | yyyy-'W'ww-EEE",
      "zh-CN | yyyy-'W'ww-EEE",
      "ja | yyyy-'W'ww-EEE",
      "ko | yyyy-'W'ww-EEE",
      "ru | yyyy-'W'ww-EEE",
      "ar | yyyy-'W'ww-EEE",
      "de | YYYY-ww-EEE yyyy",
      "en-US | YYYY-ww",
      "fr | yyyy-MM-W",
      "en-US | yyyy-MM-W",
      "en-GB | yyyy-MM-dd EEE ww"
   })
   public void weekPatternParsesLikeSimpleDateFormat(String tag, String pattern) {
      final Locale locale = Locale.forLanguageTag(tag);

      for(LocalDate day : new LocalDate[] {
         LocalDate.of(2011, 3, 10), LocalDate.of(2012, 12, 31), LocalDate.of(2016, 1, 3) })
      {
         final Date date = Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());

         for(Locale textLocale : new Locale[] { locale, Locale.US }) {
            final String text = new SimpleDateFormat(pattern, textLocale).format(date);
            final ExtendedDateFormat format = new ExtendedDateFormat(pattern, locale);
            final Object expected =
               parseOrError(() -> new SimpleDateFormat(pattern, locale).parse(text));

            assertEquals(expected, parseOrError(() -> format.parse(text)), text);
            assertEquals(expected, parseOrError(() -> (Date) format.parseObject(text)), text);
         }
      }
   }

   // Bug #77458: a week date is resolved by java.time like SimpleDateFormat, so it keeps
   // the java.time path
   @ParameterizedTest(name = "{0}")
   @CsvSource({ "en-US", "en-GB", "de" })
   public void weekDateKeepsJavaTime(String tag) throws Exception {
      final Locale locale = Locale.forLanguageTag(tag);
      final String pattern = "YYYY-ww-EEE";

      for(LocalDate day : new LocalDate[] {
         LocalDate.of(2011, 3, 10), LocalDate.of(2012, 12, 31), LocalDate.of(2016, 1, 3) })
      {
         final Date date = Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
         final String text = new SimpleDateFormat(pattern, locale).format(date);
         final ExtendedDateFormat format = new ExtendedDateFormat(pattern, locale);

         assertEquals(new SimpleDateFormat(pattern, locale).parse(text), format.parse(text, null));
         assertEquals(date, format.parse(text));
      }
   }

   // Bug #77458: fields java.time resolves differently from SimpleDateFormat were hidden for
   // text in other languages, since English java.time rejected it. a two-letter year is read
   // as 2000-2099 by java.time instead of with the 2-digit year start (#77507). a day of week
   // without a day, a 12-hour hour without am/pm, a week year without a week, a fraction other
   // than SSS and the calendar week rules of a -u-ca-iso8601 locale are resolved differently too
   @ParameterizedTest(name = "{0} {1}")
   @CsvSource(delimiter = '|', value = {
      "fr | MMM d, yy",
      "de | dd. MMM yy",
      "ja | d-MMM-yy",
      "en-US | MM/dd/yy",
      "en-US | MMMM yy",
      "fr | YY-ww-EEE",
      "de | EEE yyyy",
      "de | d. MMMM yyyy h:mm",
      "en-GB | yyyy-MM-dd a",
      "de | d. MMMM Y",
      "fr | dd/MM/yyyy HH:mm:ss.S",
      "de | D MMMM yyyy",
      "en-US-u-ca-iso8601 | YYYY-ww-EEE",
      "en-US | MM/dd/YY",
      "en-US | yyMMdd",
      "de | MMMMM yyyy",
      "fr | EEEEE, MMMMM dd, yyyy",
      "de | F MMM yyyy"
   })
   public void fieldParsesLikeSimpleDateFormat(String tag, String pattern) {
      final Locale locale = Locale.forLanguageTag(tag);

      for(LocalDateTime time : new LocalDateTime[] {
         LocalDateTime.of(1990, 1, 1, 0, 0), LocalDateTime.of(1950, 6, 15, 3, 4, 5, 6_000_000),
         LocalDateTime.of(2011, 3, 10, 14, 5, 6, 789_000_000) })
      {
         final Date date = Date.from(time.atZone(ZoneId.systemDefault()).toInstant());

         for(Locale textLocale : new Locale[] { locale, Locale.US }) {
            final String text = new SimpleDateFormat(pattern, textLocale).format(date);
            final ExtendedDateFormat format = new ExtendedDateFormat(pattern, locale);
            final Object expected =
               parseOrError(() -> new SimpleDateFormat(pattern, locale).parse(text));

            assertEquals(expected, parseOrError(() -> format.parse(text)), text);
            assertEquals(expected, parseOrError(() -> (Date) format.parseObject(text)), text);
         }
      }
   }

   // Bug #77458: setCalendar() can give other week rules than the locale's
   @Test
   public void calendarWeekRulesParseLikeSimpleDateFormat() throws Exception {
      final String pattern = "YYYY-ww-EEE";
      final ExtendedDateFormat format = new ExtendedDateFormat(pattern, Locale.US);
      final SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
      format.getCalendar().setFirstDayOfWeek(Calendar.MONDAY);
      format.getCalendar().setMinimalDaysInFirstWeek(4);
      sdf.getCalendar().setFirstDayOfWeek(Calendar.MONDAY);
      sdf.getCalendar().setMinimalDaysInFirstWeek(4);

      assertEquals(sdf.parse("2011-10-Thu"), format.parse("2011-10-Thu"));
      assertThrows(IllegalArgumentException.class, () -> format.parse("2011-10-Thu", null));
   }

   // Bug #77458: the common patterns keep the java.time path in every locale
   @ParameterizedTest(name = "{0} {1}")
   @CsvSource(delimiter = '|', value = {
      "en-US | yyyy-MM-dd", "en-US | yyyy-MM-dd HH:mm:ss", "en-US | MM/dd/yyyy", "en-US | HH:mm:ss",
      "en-US | MMM d, yyyy", "en-US | hh:mm a", "en-US | yyyy-MM-dd'T'HH:mm:ss.SSS",
      "en-US | EEE MMM dd HH:mm:ss yyyy", "en-US | MMMM yyyy", "de | dd.MM.yyyy", "de | d. MMMM yyyy",
      "fr | EEEE d MMMM yyyy", "ja | yyyy/MM/dd H:mm", "en-GB | dd/MM/yyyy hh:mm a", "de | YYYY-ww-EEE"
   })
   public void commonPatternKeepsJavaTime(String tag, String pattern) throws Exception {
      final Locale locale = Locale.forLanguageTag(tag);
      final Date date = Date.from(LocalDateTime.of(2011, 3, 10, 14, 5, 6, 789_000_000)
                                     .atZone(ZoneId.systemDefault()).toInstant());
      final String text = new SimpleDateFormat(pattern, locale).format(date);

      assertEquals(new SimpleDateFormat(pattern, locale).parse(text),
                   new ExtendedDateFormat(pattern, locale).parse(text, null), text);
   }

   // Bug #77508: java.time does not resolve a calendar year with a week of year, so
   // parse(String) gave Jan 1
   @Test
   public void calendarYearWithWeekParsesToDayOfWeek() throws Exception {
      final ExtendedDateFormat format = new ExtendedDateFormat("yyyy-'W'ww-EEE", Locale.UK);
      final Date date = Date.from(LocalDate.of(2011, 3, 10)
                                     .atStartOfDay(ZoneId.systemDefault()).toInstant());

      assertEquals(date, format.parse("2011-W10-Thu"));
      assertEquals(date, format.parseObject("2011-W10-Thu"));
   }

   // Bug #77507: java.time read a two-letter year as 2000-2099, ignoring the 2-digit year
   // start and the lenient flag of SimpleDateFormat
   @Test
   public void twoDigitYearUsesTwoDigitYearStart() throws Exception {
      final ExtendedDateFormat format = new ExtendedDateFormat("MM/dd/yy", Locale.US);
      final ExtendedDateFormat compact = new ExtendedDateFormat("yyMMdd", Locale.US);
      final ExtendedDateFormat strict = new ExtendedDateFormat("MM/dd/yy", Locale.US);
      final Calendar calendar = Calendar.getInstance();
      calendar.clear();
      calendar.set(1900, Calendar.JANUARY, 1);
      format.set2DigitYearStart(calendar.getTime());
      strict.setLenient(false);

      assertEquals(date(1910, 12, 31), format.parse("12/31/10"));
      assertEquals(date(1910, 12, 31), format.parseObject("12/31/10"));
      assertEquals(date(1985, 12, 31), compact.parse("851231"));
      assertEquals(date(1985, 12, 31), compact.parseObject("851231"));
      assertThrows(ParseException.class, () -> strict.parse("02/30/85"));
      assertThrows(ParseException.class, () -> strict.parseObject("02/30/85"));
   }

   // Bug #77458: GGGGG, MMMMM, LLLLL and EEEEE are the narrow forms in java.time, e.g. 3 for
   // March in zh and the Cyrillic M for both March and May in ru, so java.time accepted text
   // that SimpleDateFormat in the format locale rejects. the full forms SimpleDateFormat makes
   // must still parse. the ru text, which java.time read as May, must throw
   @ParameterizedTest(name = "{0} {1} {2}")
   @CsvSource(delimiter = '|', value = {
      "zh | MMMMM yyyy | 3 2011",
      "ja | MMMMM yyyy | 3 2011",
      "ru | d MMMMM yyyy | 10 \u041c 2011",
      "ru | d LLLLL yyyy | 10 \u041c 2011",
      "fr | EEEEE dd/MM/yyyy | J 10/03/2011",
      "en-US | dd MMMM yyyy GGGGG | 10 March 2011 A"
   })
   public void narrowFormParsesLikeSimpleDateFormat(String tag, String pattern, String narrow)
      throws Exception
   {
      final Locale locale = Locale.forLanguageTag(tag);
      final ExtendedDateFormat format = new ExtendedDateFormat(pattern, locale);
      final Object expected =
         parseOrError(() -> new SimpleDateFormat(pattern, locale).parse(narrow));

      if(tag.equals("ru")) {
         assertEquals(ParseException.class, expected, narrow);
      }

      assertEquals(expected, parseOrError(() -> format.parse(narrow)), narrow);
      assertEquals(expected, parseOrError(() -> (Date) format.parseObject(narrow)), narrow);

      final Date date = date(2011, 3, 10);
      final String full = new SimpleDateFormat(pattern, locale).format(date);

      assertEquals(new SimpleDateFormat(pattern, locale).parse(full), format.parse(full), full);
      assertEquals(new SimpleDateFormat(pattern, locale).parse(full), format.parseObject(full),
                   full);
   }

   // Bug #77507: a number is still read as milliseconds from epoch by parseObject() when the
   // pattern is parsed by SimpleDateFormat
   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = '|', value = {
      "1300000000000", "+1300000000000", "' 1300000000000 '", "1.3E12", "1.3e+12", "1300000000000d",
      "0x1.2ea05f2p40"
   })
   public void numberParsesAsEpochMillis(String text) throws Exception {
      final ExtendedDateFormat format = new ExtendedDateFormat("MM/dd/yy", Locale.US);
      final long millis = (long) Double.parseDouble(text);

      assertEquals(new Date(millis), format.parseObject(text));
   }

   // Bug #77458: parseObject() tried java.time before the epoch milliseconds, so a compact
   // numeric date sent to SimpleDateFormat must not be read as milliseconds. text java.time
   // rejects (too long for the pattern or day of year 0) is still read as milliseconds
   @ParameterizedTest(name = "{0} {1}")
   @CsvSource(delimiter = '|', value = {
      "yyMMddHHmmss | 851231120000 | date",
      "yyMMddHHmmss | 460101000000 | date",
      "yyyyDDDHHmmss | 2011069120000 | date",
      "yyMMddHHmmss | 1300000000000 | millis",
      "yyyyDDDHHmmss | 1300000000000 | millis"
   })
   public void numericDateParsesBeforeEpochMillis(String pattern, String text, String kind)
      throws Exception
   {
      final ExtendedDateFormat format = new ExtendedDateFormat(pattern, Locale.US);
      final Date expected = kind.equals("date") ?
         new SimpleDateFormat(pattern, Locale.US).parse(text) : new Date(Long.parseLong(text));

      assertEquals(expected, format.parseObject(text));

      if(kind.equals("date")) {
         assertEquals(expected, format.parse(text));
      }
   }

   private static Date date(int year, int month, int day) {
      return Date.from(LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault())
                          .toInstant());
   }

   private interface DateParser {
      Date parse() throws ParseException;
   }

   private static Object parseOrError(DateParser parser) {
      try {
         return parser.parse();
      }
      catch(ParseException ex) {
         return ParseException.class;
      }
   }
}
