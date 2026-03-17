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
package inetsoft.uql.path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.ParsePosition;

import static org.junit.jupiter.api.Assertions.*;

class LongFormatTest {

   private LongFormat format;

   @BeforeEach
   void setUp() {
      format = new LongFormat();
   }

   // ---- format(Object) tests ----

   @Test
   void formatZero() {
      assertEquals("0", format.format(0L));
   }

   @Test
   void formatPositive() {
      assertEquals("123456789", format.format(123456789L));
   }

   @Test
   void formatNegative() {
      assertEquals("-987654321", format.format(-987654321L));
   }

   @Test
   void formatMinValue() {
      assertEquals(String.valueOf(Long.MIN_VALUE), format.format(Long.MIN_VALUE));
   }

   @Test
   void formatMaxValue() {
      assertEquals(String.valueOf(Long.MAX_VALUE), format.format(Long.MAX_VALUE));
   }

   @Test
   void formatNull() {
      assertEquals("null", format.format(null));
   }

   // ---- parseObject(String, ParsePosition) tests ----

   @Test
   void parseZero() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("0", pos);
      assertEquals(Long.valueOf(0L), result);
      assertEquals(1, pos.getIndex());
   }

   @Test
   void parsePositiveValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("9876543210", pos);
      assertEquals(Long.valueOf(9876543210L), result);
      assertEquals(10, pos.getIndex());
   }

   @Test
   void parseNegativeValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("-1234567890", pos);
      assertEquals(Long.valueOf(-1234567890L), result);
      assertEquals(11, pos.getIndex());
   }

   @Test
   void parseMinValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject(String.valueOf(Long.MIN_VALUE), pos);
      assertEquals(Long.MIN_VALUE, result);
   }

   @Test
   void parseMaxValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject(String.valueOf(Long.MAX_VALUE), pos);
      assertEquals(Long.MAX_VALUE, result);
   }

   @Test
   void parsePositionIndexIsSetToStringLength() {
      ParsePosition pos = new ParsePosition(0);
      String source = "42";
      format.parseObject(source, pos);
      assertEquals(source.length(), pos.getIndex());
   }

   // ---- round-trip tests ----

   @ParameterizedTest
   @ValueSource(longs = {Long.MIN_VALUE, -1000000000L, -1L, 0L, 1L, 1000000000L, Long.MAX_VALUE})
   void roundTrip(long value) {
      String formatted = format.format(value);
      ParsePosition pos = new ParsePosition(0);
      Object parsed = format.parseObject(formatted, pos);
      assertEquals(value, parsed);
   }

   // ---- invalid string tests ----

   @Test
   void parseNonNumericString() {
      ParsePosition pos = new ParsePosition(0);
      assertThrows(NumberFormatException.class,
         () -> format.parseObject("xyz", pos));
   }

   @Test
   void parseEmptyString() {
      ParsePosition pos = new ParsePosition(0);
      assertThrows(NumberFormatException.class,
         () -> format.parseObject("", pos));
   }

   @Test
   void parseDecimalString() {
      ParsePosition pos = new ParsePosition(0);
      assertThrows(NumberFormatException.class,
         () -> format.parseObject("3.14", pos));
   }

   @Test
   void parseValueExceedingLongMax() {
      ParsePosition pos = new ParsePosition(0);
      // A value larger than Long.MAX_VALUE
      assertThrows(NumberFormatException.class,
         () -> format.parseObject("99999999999999999999", pos));
   }
}
