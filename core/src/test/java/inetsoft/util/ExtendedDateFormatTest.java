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
}
