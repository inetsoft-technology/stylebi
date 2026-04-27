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

class FloatFormatTest {

   private FloatFormat format;

   @BeforeEach
   void setUp() {
      format = new FloatFormat();
   }

   // ---- format(Object) tests ----

   @Test
   void formatZero() {
      assertEquals("0.0", format.format(0.0f));
   }

   @Test
   void formatPositive() {
      assertEquals("3.14", format.format(3.14f));
   }

   @Test
   void formatNegative() {
      assertEquals("-2.5", format.format(-2.5f));
   }

   @Test
   void formatNaN() {
      assertEquals("NaN", format.format(Float.NaN));
   }

   @Test
   void formatPositiveInfinity() {
      assertEquals("Infinity", format.format(Float.POSITIVE_INFINITY));
   }

   @Test
   void formatNegativeInfinity() {
      assertEquals("-Infinity", format.format(Float.NEGATIVE_INFINITY));
   }

   @Test
   void formatMaxValue() {
      String result = format.format(Float.MAX_VALUE);
      assertNotNull(result);
      assertFalse(result.isEmpty());
   }

   @Test
   void formatMinValue() {
      String result = format.format(Float.MIN_VALUE);
      assertNotNull(result);
      assertFalse(result.isEmpty());
   }

   @Test
   void formatInteger() {
      assertEquals("1.0", format.format(1.0f));
   }

   // ---- parseObject(String, ParsePosition) tests ----

   @Test
   void parseZero() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("0.0", pos);
      assertEquals(0.0f, (Float) result, 1e-6f);
      assertEquals(3, pos.getIndex());
   }

   @Test
   void parsePositiveValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("1.5", pos);
      assertEquals(1.5f, (Float) result, 1e-6f);
      assertEquals(3, pos.getIndex());
   }

   @Test
   void parseNegativeValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("-3.25", pos);
      assertEquals(-3.25f, (Float) result, 1e-6f);
      assertEquals(5, pos.getIndex());
   }

   @Test
   void parseNaN() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("NaN", pos);
      assertTrue(Float.isNaN((Float) result));
      assertEquals(3, pos.getIndex());
   }

   @Test
   void parsePositiveInfinity() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("Infinity", pos);
      assertEquals(Float.POSITIVE_INFINITY, (Float) result);
      assertEquals(8, pos.getIndex());
   }

   @Test
   void parseNegativeInfinity() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("-Infinity", pos);
      assertEquals(Float.NEGATIVE_INFINITY, (Float) result);
      assertEquals(9, pos.getIndex());
   }

   @Test
   void parsePositionIndexIsSetToStringLength() {
      ParsePosition pos = new ParsePosition(0);
      String source = "12.34";
      format.parseObject(source, pos);
      assertEquals(source.length(), pos.getIndex());
   }

   // ---- round-trip tests ----

   @ParameterizedTest
   @ValueSource(floats = {-1000.5f, -1.5f, -0.001f, 0.0f, 0.001f, 1.5f, 1000.5f})
   void roundTrip(float value) {
      String formatted = format.format(value);
      ParsePosition pos = new ParsePosition(0);
      Object parsed = format.parseObject(formatted, pos);
      assertEquals(value, (Float) parsed, 1e-4f);
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
   void parseMultipleDecimalPoints() {
      ParsePosition pos = new ParsePosition(0);
      assertThrows(NumberFormatException.class,
         () -> format.parseObject("1.2.3", pos));
   }
}
