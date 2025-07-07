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

package inetsoft.mv.formula;

import inetsoft.uql.XConstants;
import inetsoft.util.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistinctCountFormulaTest {

   private DistinctCountFormula formula;

   @BeforeEach
   void setUp() {
      formula = new DistinctCountFormula();
   }

   @Test
   void testReset() {
      formula.addValue(10.0);
      formula.addValue(20.0);
      assertEquals(2, formula.getResult());
      formula.reset();
      assertEquals(0, formula.getResult());
   }

   @Test
   void testAddValueDouble() {
      formula.addValue(10.0);
      formula.addValue(20.0);
      formula.addValue(10.0); // Duplicate value
      formula.addValue(Tool.NULL_DOUBLE);
      assertEquals(2, formula.getResult());
      assertEquals(2, formula.getDoubleResult());
   }

   @Test
   void testAddValueDoubleArray() {
      formula.addValue(new double[]{10.0});
      formula.addValue(new double[]{20.0});
      formula.addValue(new double[]{10.0}); // Duplicate value
      formula.addValue(new double[]{Tool.NULL_DOUBLE});
      assertEquals(2, formula.getResult());
   }

   @Test
   void testAddValueFloat() {
      formula.addValue(10.0f);
      formula.addValue(20.0f);
      formula.addValue(10.0f); // Duplicate value
      formula.addValue(Tool.NULL_FLOAT);
      assertEquals(2, formula.getResult());
   }

   @Test
   void testAddValueLong() {
      formula.addValue(10L);
      formula.addValue(20L);
      formula.addValue(10L); // Duplicate value
      formula.addValue(Tool.NULL_LONG);
      assertEquals(2, formula.getResult());
   }

   @Test
   void testAddValueInt() {
      formula.addValue(10);
      formula.addValue(20);
      formula.addValue(10); // Duplicate value
      formula.addValue(Tool.NULL_INTEGER);
      assertEquals(2, formula.getResult());
   }

   @Test
   void testAddValueShort() {
      formula.addValue((short) 10);
      formula.addValue((short) 20);
      formula.addValue((short) 10); // Duplicate value
      formula.addValue(Tool.NULL_SHORT);
      assertEquals(2, formula.getResult());
   }

   @Test
   void testSetDefaultResult() {
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
      assertEquals(0, formula.getResult());
      assertEquals(0, formula.getDoubleResult());

      formula.setDefaultResult(false);
      assertFalse(formula.isDefaultResult());
   }

   @Test
   void testIsNull() {
      assertFalse(formula.isNull()); // Always returns false
   }

   @Test
   void testClone() {
      formula.addValue(10.0);
      formula.addValue(20.0);

      DistinctCountFormula cloned = (DistinctCountFormula) formula.clone();
      assertNotSame(formula, cloned);
      assertEquals(formula.getResult(), cloned.getResult());
   }

   @Test
   void testMerge() {
      DistinctCountFormula other = new DistinctCountFormula();
      other.addValue(10.0);
      other.addValue(20.0);

      formula.addValue(20.0);
      formula.addValue(30.0);
      formula.merge(other);

      assertEquals(3, formula.getResult());
   }

   @Test
   void testGetDisplayName() {
      assertEquals("Distinct Count", formula.getDisplayName());
   }

   @Test
   void testGetName() {
      assertEquals(XConstants.DISTINCTCOUNT_FORMULA, formula.getName());
   }
}