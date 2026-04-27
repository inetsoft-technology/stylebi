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

class DoubleFormatTest {

   private DoubleFormat format;

   @BeforeEach
   void setUp() {
      format = new DoubleFormat();
   }

   // ---- format(Object) tests ----

   @Test
   void formatZero() {
      assertEquals("0.0", format.format(0.0));
   }

   @Test
   void formatPositive() {
      assertEquals("3.14", format.format(3.14));
   }

   @Test
   void formatNegative() {
      assertEquals("-2.5", format.format(-2.5));
   }

   @Test
   void formatNaN() {
      assertEquals("NaN", format.format(Double.NaN));
   }

   @Test
   void formatPositiveInfinity() {
      assertEquals("Infinity", format.format(Double.POSITIVE_INFINITY));
   }

   @Test
   void formatNegativeInfinity() {
      assertEquals("-Infinity", format.format(Double.NEGATIVE_INFINITY));
   }

   @Test
   void formatMaxValue() {
      String result = format.format(Double.MAX_VALUE);
      assertNotNull(result);
      assertFalse(result.isEmpty());
   }

   @Test
   void formatMinValue() {
      String result = format.format(Double.MIN_VALUE);
      assertNotNull(result);
      assertFalse(result.isEmpty());
   }

   // ---- parseObject(String, ParsePosition) tests ----

   @Test
   void parseZero() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("0.0", pos);
      assertEquals(0.0, (Double) result, 1e-15);
      assertEquals(3, pos.getIndex());
   }

   @Test
   void parsePositiveValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("3.14", pos);
      assertEquals(3.14, (Double) result, 1e-10);
      assertEquals(4, pos.getIndex());
   }

   @Test
   void parseNegativeValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("-2.5", pos);
      assertEquals(-2.5, (Double) result, 1e-10);
      assertEquals(4, pos.getIndex());
   }

   @Test
   void parseNaN() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("NaN", pos);
      assertTrue(Double.isNaN((Double) result));
      assertEquals(3, pos.getIndex());
   }

   @Test
   void parsePositiveInfinity() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("Infinity", pos);
      assertEquals(Double.POSITIVE_INFINITY, (Double) result);
      assertEquals(8, pos.getIndex());
   }

   @Test
   void parseNegativeInfinity() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("-Infinity", pos);
      assertEquals(Double.NEGATIVE_INFINITY, (Double) result);
      assertEquals(9, pos.getIndex());
   }

   @Test
   void parsePositionIndexIsSetToStringLength() {
      ParsePosition pos = new ParsePosition(0);
      String source = "123.456";
      format.parseObject(source, pos);
      assertEquals(source.length(), pos.getIndex());
   }

   // ---- round-trip tests ----

   @ParameterizedTest
   @ValueSource(doubles = {-1e10, -1.23456789, -0.001, 0.0, 0.001, 1.23456789, 1e10})
   void roundTrip(double value) {
      String formatted = format.format(value);
      ParsePosition pos = new ParsePosition(0);
      Object parsed = format.parseObject(formatted, pos);
      assertEquals(value, (Double) parsed, 1e-10);
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
}
