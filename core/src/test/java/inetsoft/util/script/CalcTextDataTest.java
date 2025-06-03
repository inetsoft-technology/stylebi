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
package inetsoft.util.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class CalcTextDataTest {

   @Test
   void testCharacter() {
      assertEquals('A', CalcTextData.character(65));
      assertEquals('a', CalcTextData.character(97));
   }

   @Test
   void testCode() {
      assertEquals(65, CalcTextData.code("A"));
      assertEquals(97, CalcTextData.code("a"));
   }

   @Test
   void testConcatenate() {
      Object[] data = { "Hello", " ", "World" };
      assertEquals("Hello World", CalcTextData.concatenate(data));
   }

   @ParameterizedTest
   @CsvSource(delimiter = ';', value = {
      "1234.567; 2; $1,234.57",       // Positive number with 2 decimal places
      "-1234.567; 2; ($1,234.57)",    // Negative number with 2 decimal places
      "1234.567; 0; $1,235",          // Positive number with 0 decimal places
      "-1234.567; 0; ($1,235)",       // Negative number with 0 decimal places
      "1234; 0; $1,234",              // Number with no decimals
      "1234.56789; 5; $1,234.56789",  // Number with more decimal places
      "0; 2; $0.00"                   // Edge case: zero
   })
   void testDollar(double value, int decimals, String expected) {
      assertEquals(expected, CalcTextData.dollar(value, decimals));
   }

   @Test
   void testExact() {
      // Test when both strings are the same
      assertTrue(CalcTextData.exact("test", "test"));

      // Test when strings differ in case
      assertFalse(CalcTextData.exact("test", "Test"));

      // Test when both strings are null
      assertFalse(CalcTextData.exact(null, null));

      // Test when both strings are empty
      assertTrue(CalcTextData.exact("", ""));
   }

   @ParameterizedTest
   @CsvSource({
      "'e', 'Hello', 1, 2",  // 'e' is at position 2
      "'o', 'Hello', 1, 5",  // 'o' is at position 5
      "'l', 'Hello', 4, 4",  // 'l' is at position 4 when starting from index 4
      "'', 'Hello', 3, 3",   // Empty string should return start_num
      "'z', 'Hello', 1, 0"   // 'z' is not in "Hello"
   })
   void testFind(String findText, String withinText, int startNum, int expected) {
      if(startNum <= 0 || startNum > withinText.length()) {
         assertThrows(RuntimeException.class, () -> CalcTextData.find(findText, withinText, startNum));
      }
      else {
         assertEquals(expected, CalcTextData.find(findText, withinText, startNum));
      }
   }

   @ParameterizedTest
   @CsvSource(delimiter = ';', value = {
      "1234.567; 2; true; 1234.57",       // No commas, rounded to 2 decimals
      "1234.0; 0; true; 1234",           // No commas, no decimals
      "1234.567; 2; false; 1,234.57",    // With commas, rounded to 2 decimals
      "1234.0; 0; false; 1,234",         // With commas, no decimals
      "-1234.567; 2; true; -1234.57",    // Negative number, no commas
      "-1234.567; 2; false; -1,234.57"   // Negative number, with commas
   })
   void testFixed(double number, int decimals, boolean noCommas, String expected) {
      assertEquals(expected, CalcTextData.fixed(number, decimals, noCommas));
   }

   @Test
   void testLeft() {
      // Test extracting characters within the string length
      assertEquals("Hel", CalcTextData.left("Hello", 3));

      // Test extracting all characters when num_chars equals string length
      assertEquals("Hello", CalcTextData.left("Hello", 5));

      // Test extracting all characters when num_chars exceeds string length
      assertEquals("Hello", CalcTextData.left("Hello", 10));

      // Test extracting a single character when num_chars is 1
      assertEquals("H", CalcTextData.left("Hello", 1));

      // Test default behavior when num_chars is 0
      assertEquals("H", CalcTextData.left("Hello", 0));

      // Test exception when num_chars is negative
      Exception exception = assertThrows(RuntimeException.class, () ->
         CalcTextData.left("Hello", -1)
      );
      assertEquals("Number of characters cannot be less than 0", exception.getMessage());

      // Test empty string input
      assertEquals("", CalcTextData.left("", 3));
   }

   @Test
   void testLen() {
      assertEquals(5, CalcTextData.len("Hello"));
      assertEquals(0, CalcTextData.len(null));
   }

   @Test
   void testLower() {
      assertEquals("he llo", CalcTextData.lower("He lLo"));
      assertEquals("", CalcTextData.lower(null));
   }

   @Test
   void testMid() {
      // Test extracting characters within the string length
      assertEquals("ell", CalcTextData.mid("Hello", 2, 3));

      // Test extracting all characters when start_num + num_chars exceeds string length
      assertEquals("llo", CalcTextData.mid("Hello", 3, 10));

      // Test extracting a single character
      assertEquals("e", CalcTextData.mid("Hello", 2, 1));

      // Test extracting from the start of the string
      assertEquals("Hel", CalcTextData.mid("Hello", 1, 3));

      // Test when start_num is greater than the string length
      assertEquals("", CalcTextData.mid("Hello", 10, 3));

      // Test when num_chars is 0
      assertEquals("", CalcTextData.mid("Hello", 2, 0));

      // Test exception when num_chars is negative
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcTextData.mid("Hello", 2, -1)
      );
      assertEquals("Number of characters cannot be negative", exception1.getMessage());

      // Test exception when start_num is less than 1
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcTextData.mid("Hello", 0, 3)
      );
      assertEquals("start_num cannot be less than 1", exception2.getMessage());
   }

   @Test
   void testProper() {
      // Test with a normal sentence
      assertEquals("Hello World", CalcTextData.proper("hello world"));

      // Test with mixed case input and multiple spaces
      assertEquals("Hello World", CalcTextData.proper("hElLo    WoRLd"));

      // Test with special characters and numbers
      assertEquals("Hello123-World", CalcTextData.proper("hello123-world"));

      // Test with empty string
      assertEquals("", CalcTextData.proper(""));

      // Test with single word
      assertEquals("Hello", CalcTextData.proper("hello"));

      // Test with null input
      assertThrows(NullPointerException.class, () -> CalcTextData.proper(null));
   }

   @Test
   void testReplace() {
      // Test replacing characters within the string length
      assertEquals("HeXXo", CalcTextData.replace("Hello", 3, 2, "XX"));

      // Test replacing characters when start_num is 1
      assertEquals("XXllo", CalcTextData.replace("Hello", 1, 2, "XX"));

      // Test replacing characters when start_num + num_chars exceeds string length
      assertEquals("HeXX", CalcTextData.replace("Hello", 3, 10, "XX"));

      // Test replacing characters with an empty string
      assertEquals("Heo", CalcTextData.replace("Hello", 3, 2, ""));

      // Test replacing characters with a longer string
      assertEquals("HeXXXXXo", CalcTextData.replace("Hello", 3, 2, "XXXXX"));

      // Test exception when start_num is less than 1
      assertThrows(RuntimeException.class, () -> CalcTextData.replace("Hello", 0, 2, "XX"));

      // Test exception when num_chars is negative
      assertThrows(RuntimeException.class, () -> CalcTextData.replace("Hello", 3, -1, "XX"));

      // Test replacing characters when start_num is greater than the string length
      assertEquals("", CalcTextData.replace("Hello", 10, 2, "XX"));
   }

   @Test
   void testRept() {
      // Test repeating a single character
      assertEquals("aaa", CalcTextData.rept("a", 3));

      // Test repeating a string
      assertEquals("abcabcabc", CalcTextData.rept("abc", 3));

      // Test repeating with zero times
      assertEquals("", CalcTextData.rept("abc", 0));

      // Test repeating with an empty string
      assertEquals("", CalcTextData.rept("", 5));

      // Test exception when the result exceeds the maximum allowed length
      assertThrows(RuntimeException.class, () -> CalcTextData.rept("a", 32768));
   }

   @Test
   void testRight() {
      // Test extracting characters within the string length
      assertEquals("lo", CalcTextData.right("Hello", 2));

      // Test extracting all characters when num_chars equals string length
      assertEquals("Hello", CalcTextData.right("Hello", 5));

      // Test extracting all characters when num_chars exceeds string length
      assertEquals("Hello", CalcTextData.right("Hello", 10));

      // Test extracting a single character when num_chars is 1
      assertEquals("o", CalcTextData.right("Hello", 1));

      // Test default behavior when num_chars is 0
      assertEquals("o", CalcTextData.right("Hello", 0));

      // Test exception when num_chars is negative
      Exception exception = assertThrows(RuntimeException.class, () ->
         CalcTextData.right("Hello", -1)
      );
      assertEquals("Number of characters cannot be less than 0", exception.getMessage());

      // Test empty string input
      assertEquals("", CalcTextData.right("", 3));
   }

   @Test
   void testSearch() {
      // Test case-insensitive search
      assertEquals(2, CalcTextData.search("e", "Hello", 1)); // 'e' is at position 2
      assertEquals(5, CalcTextData.search("O", "Hello", 1)); // 'O' (case-insensitive) is at position 5

      // Test with start_num greater than the string length
      assertThrows(RuntimeException.class, () -> CalcTextData.search("e", "Hello", 6));

      // Test with invalid start_num (less than 1)
      assertThrows(RuntimeException.class, () -> CalcTextData.search("e", "Hello", 0));

      // Test with an empty find_text
      assertEquals(-1, CalcTextData.search("", "Hello", 3));

      // Test with find_text not present in within_text
      assertEquals(-1, CalcTextData.search("z", "Hello", 1)); // 'z' is not in "Hello"

      // Test with special characters
//      assertEquals(4, CalcTextData.search("$", "He$lo", 1)); // bug #71357
   }

   @Test
   void testSubstitute() {
      // Test replacing the first occurrence
      assertEquals("Hello World", CalcTextData.substitute("Hello Java", "Java", "World", 1));

      // Test replacing the second occurrence
      assertEquals("Java Hello World", CalcTextData.substitute("Java Hello Java", "Java", "World", 2));

      // Test when the instance number is greater than the occurrences
      assertEquals("Hello Java", CalcTextData.substitute("Hello Java", "Java", "World", 2));

      // Test not replace when instance_num is 0
      assertEquals("Java Java", CalcTextData.substitute("Java Java", "Java", "World", 0));

      // Test when old_text is not found
      assertEquals("Hello Java", CalcTextData.substitute("Hello Java", "Python", "World", 1));

      // Test with an empty string for old_text
//      assertEquals("Hello Java", CalcTextData.substitute("Hello Java", "", "World", 1)); //bug #71361

      // Test with an empty string for new_text
      assertEquals("Hello ", CalcTextData.substitute("Hello Java", "Java", "", 1));
   }

   @Test
   void testT() {
      // Test with a numeric string
      assertEquals("", CalcTextData.t("123"));

      // Test with a boolean string "true"
      assertEquals("", CalcTextData.t("true"));

      // Test with a non-numeric, non-boolean string
      assertEquals("test", CalcTextData.t("test"));

      // Test with an empty string
      assertEquals("", CalcTextData.t(""));
   }

   @Test
   void testText() {
      // Test with a valid number and format
      assertEquals("1,234.57", CalcTextData.text(1234.567, "#,##0.00"));

      // Test with a number without decimals
      assertEquals("1234", CalcTextData.text(1234, "0"));

      // Test with a negative number
      assertEquals("-1,234.57", CalcTextData.text(-1234.567, "#,##0.00"));

      // Test with a number and custom format
      assertEquals("1.23E3", CalcTextData.text(1234, "0.00E0"));

      // Test with zero
      assertEquals("0.00", CalcTextData.text(0, "0.00"));
   }

   @Test
   void testTrim() {
      // Test with leading and trailing spaces
      String input = "   Hello World   ";
      String expected = "Hello World";
      assertEquals(expected, CalcTextData.trim(input));

      // Test with multiple spaces between words
      input = "Hello    World";
      expected = "Hello World";
      assertEquals(expected, CalcTextData.trim(input));

      // Test with no spaces
      input = "HelloWorld";
      expected = "HelloWorld";
      assertEquals(expected, CalcTextData.trim(input));

      // Test with empty string
      input = "";
      expected = "";
      assertEquals(expected, CalcTextData.trim(input));

      // Test with only spaces
      input = "     ";
      expected = "";
      assertEquals(expected, CalcTextData.trim(input));
   }

   @Test
   void testUpper() {
      // Test with a normal string
      assertEquals("HELLO", CalcTextData.upper("Hello"));

      // Test with an empty string
      assertEquals("", CalcTextData.upper(""));

      // Test with a null input
      assertEquals("", CalcTextData.upper(null));

      // Test with a string that is already uppercase
      assertEquals("WORLD", CalcTextData.upper("WORLD"));

      // Test with a string containing special characters
      assertEquals("123!@# HELLO", CalcTextData.upper("123!@# hello"));
   }

   @Test
   void testValue() {
      // Test valid date string
      assertEquals(44925.0, CalcTextData.value("01/01/2023"), 0.01);

      // Test valid time string
      assertEquals(0.5, CalcTextData.value("12:00:00"), 0.01);

      // Test valid numeric string
      assertEquals(1234.0, CalcTextData.value("1234"));

      // Test numeric string with decimal separator
      assertEquals(1234.56, CalcTextData.value("1234.56"), 0.01);

      // Test invalid string
      Exception exception1 = assertThrows(RuntimeException.class, () -> CalcTextData.value("invalid"));
      assertEquals("Invalid Data !", exception1.getMessage());

      // Test empty string
      Exception exception2 = assertThrows(RuntimeException.class, () -> CalcTextData.value(""));
      assertEquals("Invalid Data !", exception2.getMessage());

      // Test null input
      Exception exception3 = assertThrows(RuntimeException.class, () -> CalcTextData.value(null));
      assertEquals("Invalid Data !", exception3.getMessage());
   }
}