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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.ParsePosition;

import static org.junit.jupiter.api.Assertions.*;

class BooleanFormatTest {

   private BooleanFormat format;

   @BeforeEach
   void setUp() {
      format = new BooleanFormat();
   }

   // ---- format(Object) tests ----

   @Test
   void formatTrue() {
      assertEquals("true", format.format(Boolean.TRUE));
   }

   @Test
   void formatFalse() {
      assertEquals("false", format.format(Boolean.FALSE));
   }

   @Test
   void formatNull() {
      assertEquals("null", format.format(null));
   }

   @Test
   void formatNonBooleanString() {
      assertEquals("hello", format.format("hello"));
   }

   @Test
   void formatInteger() {
      assertEquals("42", format.format(42));
   }

   // ---- parseObject(String, ParsePosition) tests ----

   @Test
   void parseStringOne() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("1", pos);
      assertEquals(Boolean.TRUE, result);
      assertEquals(1, pos.getIndex());
   }

   @Test
   void parseStringTrue() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("true", pos);
      assertEquals(Boolean.TRUE, result);
      assertEquals(4, pos.getIndex());
   }

   @Test
   void parseStringFalse() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("false", pos);
      assertEquals(Boolean.FALSE, result);
      assertEquals(5, pos.getIndex());
   }

   @ParameterizedTest
   @ValueSource(strings = {"TRUE", "True", "tRuE"})
   void parseStringTrueCaseInsensitive(String input) {
      ParsePosition pos = new ParsePosition(0);
      // Boolean.valueOf is case-insensitive for "true"
      Object result = format.parseObject(input, pos);
      assertEquals(Boolean.TRUE, result);
      assertEquals(input.length(), pos.getIndex());
   }

   @ParameterizedTest
   @ValueSource(strings = {"FALSE", "False", "fAlSe"})
   void parseStringFalseCaseInsensitive(String input) {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject(input, pos);
      assertEquals(Boolean.FALSE, result);
      assertEquals(input.length(), pos.getIndex());
   }

   @ParameterizedTest
   @CsvSource({"yes,false", "no,false", "0,false", "2,false", "hello,false"})
   void parseNonTrueFalsyStrings(String input, boolean expected) {
      // Boolean.valueOf returns false for any string that is not "true"
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject(input, pos);
      assertEquals(expected, result);
      assertEquals(input.length(), pos.getIndex());
   }

   @Test
   void parseEmptyString() {
      ParsePosition pos = new ParsePosition(0);
      Object result = format.parseObject("", pos);
      // Boolean.valueOf("") returns false
      assertEquals(Boolean.FALSE, result);
      assertEquals(0, pos.getIndex());
   }

   @Test
   void parsePositionIndexIsSetToStringLength() {
      ParsePosition pos = new ParsePosition(0);
      String source = "trueSomeExtra";
      format.parseObject(source, pos);
      assertEquals(source.length(), pos.getIndex());
   }

   // ---- parseObject(String) inherited method tests ----

   @Test
   void parseObjectStringTrue() throws Exception {
      Object result = format.parseObject("true");
      assertEquals(Boolean.TRUE, result);
   }

   @Test
   void parseObjectStringFalse() throws Exception {
      Object result = format.parseObject("false");
      assertEquals(Boolean.FALSE, result);
   }

   @Test
   void parseObjectStringOne() throws Exception {
      Object result = format.parseObject("1");
      assertEquals(Boolean.TRUE, result);
   }

   @Test
   void parseObjectStringZero() throws Exception {
      Object result = format.parseObject("0");
      assertEquals(Boolean.FALSE, result);
   }
}
