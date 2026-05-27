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

import inetsoft.uql.XConstants;
import org.junit.jupiter.api.Test;

import java.text.FieldPosition;
import java.text.ParsePosition;

import static org.junit.jupiter.api.Assertions.*;

class DurationFormatTest {

   // ---- Constructor and toPattern tests ----

   @Test
   void defaultConstructorUsesDefaultPattern() {
      DurationFormat fmt = new DurationFormat();
      assertEquals(DurationFormat.DEFAULT, fmt.toPattern());
   }

   @Test
   void constructorWithCustomPattern() {
      DurationFormat fmt = new DurationFormat("HH:mm:ss");
      assertEquals("HH:mm:ss", fmt.toPattern());
   }

   @Test
   void constructorWithNullPatternFallsBackToDefault() {
      DurationFormat fmt = new DurationFormat(null);
      assertEquals(DurationFormat.DEFAULT, fmt.toPattern());
   }

   @Test
   void constructorWithEmptyPatternFallsBackToDefault() {
      DurationFormat fmt = new DurationFormat("");
      assertEquals(DurationFormat.DEFAULT, fmt.toPattern());
   }

   @Test
   void constructorWithPatternAndPadWithZeros() {
      DurationFormat fmt = new DurationFormat("HH:mm:ss", true);
      assertEquals("HH:mm:ss", fmt.toPattern());
   }

   // ---- getFormatType tests ----

   @Test
   void getFormatTypeWithPadZeros() {
      DurationFormat fmt = new DurationFormat("HH:mm:ss", true);
      assertEquals(XConstants.DURATION_FORMAT, fmt.getFormatType());
   }

   @Test
   void getFormatTypeWithoutPadZeros() {
      DurationFormat fmt = new DurationFormat("HH:mm:ss", false);
      assertEquals(XConstants.DURATION_FORMAT_PAD_NON, fmt.getFormatType());
   }

   // ---- format(long) tests ----

   @Test
   void formatZeroMilliseconds() {
      DurationFormat fmt = new DurationFormat("HH:mm:ss");
      String result = fmt.format(0L);
      assertEquals("00:00:00", result);
   }

   @Test
   void formatOneSecond() {
      DurationFormat fmt = new DurationFormat("HH:mm:ss");
      String result = fmt.format(1000L);
      assertEquals("00:00:01", result);
   }

   @Test
   void formatOneMinute() {
      DurationFormat fmt = new DurationFormat("HH:mm:ss");
      long oneMinuteMs = 60 * 1000L;
      String result = fmt.format(oneMinuteMs);
      assertEquals("00:01:00", result);
   }

   @Test
   void formatOneHour() {
      DurationFormat fmt = new DurationFormat("HH:mm:ss");
      long oneHourMs = 3600 * 1000L;
      String result = fmt.format(oneHourMs);
      assertEquals("01:00:00", result);
   }

   @Test
   void formatOneDay() {
      DurationFormat fmt = new DurationFormat("dd HH:mm:ss");
      long oneDayMs = 24 * 3600 * 1000L;
      String result = fmt.format(oneDayMs);
      assertEquals("01 00:00:00", result);
   }

   @Test
   void format90Seconds() {
      // 90 seconds = 1 minute 30 seconds
      DurationFormat fmt = new DurationFormat("mm:ss");
      long ms = 90 * 1000L;
      String result = fmt.format(ms);
      assertEquals("01:30", result);
   }

   @Test
   void format3661Seconds() {
      // 1 hour, 1 minute, 1 second
      DurationFormat fmt = new DurationFormat("HH:mm:ss");
      long ms = (3600 + 60 + 1) * 1000L;
      String result = fmt.format(ms);
      assertEquals("01:01:01", result);
   }

   @Test
   void formatWithoutPadding() {
      DurationFormat fmt = new DurationFormat("H:m:s", false);
      long ms = (3600 + 60 + 1) * 1000L;
      String result = fmt.format(ms);
      assertEquals("1:1:1", result);
   }

   // ---- format(double) tests ----

   @Test
   void formatDoubleIsConvertedToLong() {
      DurationFormat fmt = new DurationFormat("HH:mm:ss");
      // format(double) truncates to long
      String result = fmt.format(1000.0);
      assertEquals("00:00:01", result);
   }

   @Test
   void formatDoubleLargeValue() {
      DurationFormat fmt = new DurationFormat("HH:mm:ss");
      long oneHourMs = 3600 * 1000L;
      String result = fmt.format((double) oneHourMs);
      assertEquals("01:00:00", result);
   }

   // ---- format(Object) tests ----

   @Test
   void formatObjectNull() {
      DurationFormat fmt = new DurationFormat("HH:mm:ss");
      StringBuffer sb = fmt.format(null, new StringBuffer(), new FieldPosition(0));
      assertEquals("", sb.toString());
   }

   @Test
   void formatObjectEmptyString() {
      DurationFormat fmt = new DurationFormat("HH:mm:ss");
      StringBuffer sb = fmt.format("", new StringBuffer(), new FieldPosition(0));
      assertEquals("", sb.toString());
   }

   @Test
   void formatObjectLong() {
      DurationFormat fmt = new DurationFormat("HH:mm:ss");
      StringBuffer sb = fmt.format(3600_000L, new StringBuffer(), new FieldPosition(0));
      assertEquals("01:00:00", sb.toString());
   }

   @Test
   void formatObjectInteger() {
      DurationFormat fmt = new DurationFormat("HH:mm:ss");
      StringBuffer sb = fmt.format(60_000, new StringBuffer(), new FieldPosition(0));
      assertEquals("00:01:00", sb.toString());
   }

   @Test
   void formatObjectStringNumber() {
      DurationFormat fmt = new DurationFormat("HH:mm:ss");
      StringBuffer sb = fmt.format("3600000", new StringBuffer(), new FieldPosition(0));
      assertEquals("01:00:00", sb.toString());
   }

   @Test
   void formatObjectStringNonNumericThrows() {
      DurationFormat fmt = new DurationFormat("HH:mm:ss");
      assertThrows(NumberFormatException.class,
         () -> fmt.format("not-a-number", new StringBuffer(), new FieldPosition(0)));
   }

   @Test
   void formatObjectNonNumberThrows() {
      DurationFormat fmt = new DurationFormat("HH:mm:ss");
      assertThrows(NumberFormatException.class,
         () -> fmt.format(Boolean.TRUE, new StringBuffer(), new FieldPosition(0)));
   }

   // ---- parse() throws UnsupportedOperationException ----

   @Test
   void parseThrowsUnsupportedOperation() {
      DurationFormat fmt = new DurationFormat("HH:mm:ss");
      assertThrows(UnsupportedOperationException.class,
         () -> fmt.parse("01:00:00", new ParsePosition(0)));
   }

   // ---- Default pattern constant ----

   @Test
   void defaultPatternValue() {
      assertEquals("dd HH:mm:ss", DurationFormat.DEFAULT);
   }

   // ---- Boundary / unit transition tests ----

   @Test
   void sixtySecondsEqualsOneMinute() {
      DurationFormat fmt = new DurationFormat("mm:ss");
      String result = fmt.format(60_000L);
      assertEquals("01:00", result);
   }

   @Test
   void sixtyMinutesEqualsOneHour() {
      DurationFormat fmt = new DurationFormat("HH:mm");
      long ms = 60 * 60 * 1000L;
      String result = fmt.format(ms);
      assertEquals("01:00", result);
   }

   @Test
   void twentyFourHoursEqualsOneDay() {
      DurationFormat fmt = new DurationFormat("dd HH");
      long ms = 24 * 60 * 60 * 1000L;
      String result = fmt.format(ms);
      assertEquals("01 00", result);
   }
}
