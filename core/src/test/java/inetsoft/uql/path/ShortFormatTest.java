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

class ShortFormatTest {

   private ShortFormat format;

   @BeforeEach
   void setUp() {
      format = new ShortFormat();
   }

   // ---- format(Object) tests ----

   @Test
   void formatZero() {
      assertEquals("0", format.format((short) 0));
   }

   @Test
   void formatPositive() {
      assertEquals("1000", format.format((short) 1000));
   }

   @Test
   void formatNegative() {
      assertEquals("-500", format.format((short) -500));
   }

   @Test
   void formatMinValue() {
      assertEquals(String.valueOf(Short.MIN_VALUE), format.format(Short.MIN_VALUE));
   }

   @Test
   void formatMaxValue() {
      assertEquals(String.valueOf(Short.MAX_VALUE), format.format(Short.MAX_VALUE));
   }

   // ---- parseObject(String, ParsePosition) tests ----

   @Test
   void parseZero() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("0", pos);
      assertEquals(Short.valueOf((short) 0), result);
      assertEquals(1, pos.getIndex());
   }

   @Test
   void parsePositiveValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("200", pos);
      assertEquals(Short.valueOf((short) 200), result);
      assertEquals(3, pos.getIndex());
   }

   @Test
   void parseNegativeValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("-300", pos);
      assertEquals(Short.valueOf((short) -300), result);
      assertEquals(4, pos.getIndex());
   }

   @Test
   void parseMinValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject(String.valueOf(Short.MIN_VALUE), pos);
      assertEquals(Short.MIN_VALUE, result);
   }

   @Test
   void parseMaxValue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject(String.valueOf(Short.MAX_VALUE), pos);
      assertEquals(Short.MAX_VALUE, result);
   }

   @Test
   void parsePositionIndexIsSetToStringLength() {
      ParsePosition pos = new ParsePosition(0);
      String source = "1234";
      format.parseObject(source, pos);
      assertEquals(source.length(), pos.getIndex());
   }

   // ---- round-trip tests ----

   @ParameterizedTest
   @ValueSource(shorts = {Short.MIN_VALUE, -1000, -1, 0, 1, 1000, Short.MAX_VALUE})
   void roundTrip(short value) {
      String formatted = format.format(value);
      ParsePosition pos = new ParsePosition(0);
      Object parsed = format.parseObject(formatted, pos);
      assertEquals(value, (short) (Short) parsed);
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
         () -> format.parseObject("1.5", pos));
   }

   @Test
   void parseOverflowValue() {
      ParsePosition pos = new ParsePosition(0);
      // Integer.MAX_VALUE overflows short
      assertThrows(NumberFormatException.class,
         () -> format.parseObject(String.valueOf(Integer.MAX_VALUE), pos));
   }
}
