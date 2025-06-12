/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JSTokenizerTest {

   @Test
   void testContainsTokens() {
      // Case 1: Token exists in the string
      assertTrue(JSTokenizer.containsTokens("hello world", "world"));

      // Case 2: Token does not exist in the string
      assertFalse(JSTokenizer.containsTokens("hello world", "java"));

      // Case 3: Multiple tokens, one exists
      assertTrue(JSTokenizer.containsTokens("hello world", "java", "world"));

      // Case 4: Empty string
      assertFalse(JSTokenizer.containsTokens("", "world"));

      // Case 5: Empty tokens
      assertFalse(JSTokenizer.containsTokens("hello world"));
   }

   @Test
   void testTokenize() {
      // Case 1: Simple string
      List<String> tokens = JSTokenizer.tokenize("hello world");
      assertEquals(List.of("hello", "world"), tokens);

      // Case 2: String with punctuation
      tokens = JSTokenizer.tokenize("hello, world!");
      assertEquals(List.of("hello", ",", "world", "!"), tokens);

      // Case 3: String with quotes
      tokens = JSTokenizer.tokenize("\"hello\" 'world'");
      assertEquals(List.of("\"hello\"", "'world'"), tokens);

      // Case 4: Empty string
      tokens = JSTokenizer.tokenize("");
      assertTrue(tokens.isEmpty());

      // Case 5: Complex string
      tokens = JSTokenizer.tokenize("func(a, b) + 1");
      assertEquals(List.of("func", "(", "a", ",", "b", ")", "+", "1"), tokens);
   }

   @Test
   void testIsFunctionCall() {
      // Case 1: Valid function call
      assertTrue(JSTokenizer.isFunctionCall("func(a, b)", "func"));

      // Case 2: Function call with nested parentheses
      assertTrue(JSTokenizer.isFunctionCall("func(a, func2(b))", "func"));

      // Case 3: Function call with arithmetic operations
      assertFalse(JSTokenizer.isFunctionCall("func(a) + func2(b)", "func"));

      // Case 4: Not a function call
      assertFalse(JSTokenizer.isFunctionCall("notAFunction", "func"));

      // Case 5: Function name mismatch
      assertFalse(JSTokenizer.isFunctionCall("func(a, b)", "otherFunc"));

      // Case 6: Empty expression
      assertFalse(JSTokenizer.isFunctionCall("", "func"));

      // Case 7: Function call with unbalanced parentheses
      assertFalse(JSTokenizer.isFunctionCall("func(a, b", "func"));
   }
}