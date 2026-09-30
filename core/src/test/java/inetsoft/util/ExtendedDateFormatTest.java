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

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
