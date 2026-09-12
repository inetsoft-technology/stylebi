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

class IntegerFormatTest {

   private IntegerFormat format;

   @BeforeEach
   void setUp() {
      format = new IntegerFormat();
   }

   // ---- format(Object) tests ----

   @Test
   void formatZero() {
      assertEquals("0", format.format(0));
   }

   @Test
   void formatPositive() {
      assertEquals("42", format.format(42));
   }

   @Test
   void formatNegative() {
      assertEquals("-100", format.format(-100));
   }

   @Test
   void formatMinValue() {
      assertEquals(String.valueOf(Integer.MIN_VALUE), format.format(Integer.MIN_VALUE));
   }

   @Test
   void formatMaxValue() {
      assertEquals(String.valueOf(Integer.MAX_VALUE), format.format(Integer.MAX_VALUE));
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
      assertEquals(Integer.valueOf(0), result);
      assertEquals(1, pos.getIndex());
   }

   @Test
   void parsePositiveValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("12345", pos);
      assertEquals(Integer.valueOf(12345), result);
      assertEquals(5, pos.getIndex());
   }

   @Test
   void parseNegativeValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("-678", pos);
      assertEquals(Integer.valueOf(-678), result);
      assertEquals(4, pos.getIndex());
   }

   @Test
   void parseMinValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject(String.valueOf(Integer.MIN_VALUE), pos);
      assertEquals(Integer.MIN_VALUE, result);
   }

   @Test
   void parseMaxValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject(String.valueOf(Integer.MAX_VALUE), pos);
      assertEquals(Integer.MAX_VALUE, result);
   }

   @Test
   void parsePositionIndexIsSetToStringLength() {
      ParsePosition pos = new ParsePosition(0);
      String source = "9999";
      format.parseObject(source, pos);
      assertEquals(source.length(), pos.getIndex());
   }

   // ---- round-trip tests ----

   @ParameterizedTest
   @ValueSource(ints = {Integer.MIN_VALUE, -1000000, -1, 0, 1, 1000000, Integer.MAX_VALUE})
   void roundTrip(int value) {
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
         () -> format.parseObject("abc", pos));
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
   void parseLongOverflow() {
      ParsePosition pos = new ParsePosition(0);
      // Long.MAX_VALUE overflows Integer
      assertThrows(NumberFormatException.class,
         () -> format.parseObject(String.valueOf(Long.MAX_VALUE), pos));
   }
}
