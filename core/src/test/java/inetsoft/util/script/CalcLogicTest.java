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

import static org.junit.jupiter.api.Assertions.*;

class CalcLogicTest {

   @Test
   void testAnd() {
      // Test case: All true
      assertTrue(CalcLogic.and(true, true, true, true, true, true, true, true, true, true));

      // Test case: One false
      assertFalse(CalcLogic.and(true, true, false, true, true, true, true, true, true, true));

      // Test case: Mixed boolean expressions
      assertFalse(CalcLogic.and((0 < 3), (-1 > 2), (5 < 6), "test", 1, 2.5, null, null, null, null));

      // Test case: Null values
      assertTrue(CalcLogic.and(true, null, null, null, null, null, null, null, null, null));
      assertFalse(CalcLogic.and(false, null, null, null, null, null, null, null, null, null));
   }

   @Test
   void testOr() {
      // Test case: All false
      assertFalse(CalcLogic.or(false, false, false, false, false, false, false, false, false, false));

      // Test case: One true
      assertTrue(CalcLogic.or(false, false, true, false, false, false, false, false, false, false));

      // Test case: Mixed boolean expressions
      assertTrue(CalcLogic.or((0 < 3), (-1 > 2), (5 < 6), "test", 1, 2.5, null, null, null, null));

      // Test case: Null values
      assertFalse(CalcLogic.or(false, null, null, null, null, null, null, null, null, null));
      assertTrue(CalcLogic.or(true, null, null, null, null, null, null, null, null, null));
   }

   @Test
   void testNot() {
      // Test case: True to false
      assertFalse(CalcLogic.not(true));
      assertFalse(CalcLogic.not((0 < 3)));

      // Test case: False to true
      assertTrue(CalcLogic.not(false));
      assertTrue(CalcLogic.not((3 > 9)));
   }

   @Test
   void testIif() {
      // Test case: Condition is true
      assertEquals("TrueValue", CalcLogic.iif(true, "TrueValue", "FalseValue"));

      // Test case: Condition is false
      assertEquals("FalseValue", CalcLogic.iif(false, "TrueValue", "FalseValue"));

      // Test case: Null values or ""
      assertEquals(0, CalcLogic.iif((0 < 3), null, "FalseValue"));
      assertEquals(0, CalcLogic.iif(true, "", "FalseValue"));
      assertEquals(Boolean.FALSE, CalcLogic.iif((3 > 9), "TrueValue", null));
   }
}