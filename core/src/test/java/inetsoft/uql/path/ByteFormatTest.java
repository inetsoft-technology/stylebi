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

class ByteFormatTest {

   private ByteFormat format;

   @BeforeEach
   void setUp() {
      format = new ByteFormat();
   }

   // ---- format(Object) tests ----

   @Test
   void formatZero() {
      assertEquals("0", format.format((byte) 0));
   }

   @Test
   void formatPositive() {
      assertEquals("42", format.format((byte) 42));
   }

   @Test
   void formatNegative() {
      assertEquals("-1", format.format((byte) -1));
   }

   @Test
   void formatMinValue() {
      assertEquals("-128", format.format(Byte.MIN_VALUE));
   }

   @Test
   void formatMaxValue() {
      assertEquals("127", format.format(Byte.MAX_VALUE));
   }

   // ---- parseObject(String, ParsePosition) tests ----

   @Test
   void parseZero() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("0", pos);
      assertEquals(Byte.valueOf((byte) 0), result);
      assertEquals(1, pos.getIndex());
   }

   @Test
   void parsePositiveValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("100", pos);
      assertEquals(Byte.valueOf((byte) 100), result);
      assertEquals(3, pos.getIndex());
   }

   @Test
   void parseNegativeValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("-50", pos);
      assertEquals(Byte.valueOf((byte) -50), result);
      assertEquals(3, pos.getIndex());
   }

   @Test
   void parseMinValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("-128", pos);
      assertEquals(Byte.MIN_VALUE, result);
      assertEquals(4, pos.getIndex());
   }

   @Test
   void parseMaxValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("127", pos);
      assertEquals(Byte.MAX_VALUE, result);
      assertEquals(3, pos.getIndex());
   }

   @Test
   void parsePositionIndexIsSetToStringLength() {
      ParsePosition pos = new ParsePosition(0);
      format.parseObject("99", pos);
      assertEquals(2, pos.getIndex());
   }

   // ---- round-trip tests ----

   @ParameterizedTest
   @ValueSource(bytes = {Byte.MIN_VALUE, -100, -1, 0, 1, 100, Byte.MAX_VALUE})
   void roundTrip(byte value) {
      String formatted = format.format(Byte.valueOf(value));
      ParsePosition pos = new ParsePosition(0);
      Object parsed = format.parseObject(formatted, pos);
      assertEquals(value, (byte) (Byte) parsed);
   }

   // ---- invalid string tests ----

   @Test
   void parseOverflowValue() {
      ParsePosition pos = new ParsePosition(0);
      assertThrows(NumberFormatException.class,
         () -> format.parseObject("999", pos));
   }

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
         () -> format.parseObject("1.5", pos));
   }
}
