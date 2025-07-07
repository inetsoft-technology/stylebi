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

import inetsoft.report.StyleConstants;
import inetsoft.uql.XConstants;
import inetsoft.util.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MedianFormulaTest {

   private MedianFormula formula;

   @BeforeEach
   void setUp() {
      formula = new MedianFormula();
   }

   @Test
   void testReset() {
      formula.addValue(10.0);
      assertFalse(formula.isNull());
      formula.reset();
      assertTrue(formula.isNull());
      assertEquals(0.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueDouble() {
      formula.addValue(10.0);
      formula.addValue(20.0);
      formula.addValue(Tool.NULL_DOUBLE);
      assertEquals(15.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueDoubleArray() {
      formula.addValue(new double[]{10.0});
      formula.addValue(new double[]{20.0});
      formula.addValue(new double[]{Tool.NULL_DOUBLE});
      assertEquals(15.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueFloat() {
      formula.addValue(10.0f);
      formula.addValue(20.0f);
      formula.addValue(Tool.NULL_FLOAT);
      assertEquals(15.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueLong() {
      formula.addValue(10L);
      formula.addValue(20L);
      formula.addValue(Tool.NULL_LONG);
      assertEquals(15.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueInt() {
      formula.addValue(10);
      formula.addValue(20);
      formula.addValue(Tool.NULL_INTEGER);
      assertEquals(15.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueShort() {
      formula.addValue((short) 10);
      formula.addValue((short) 20);
      formula.addValue(Tool.NULL_SHORT);
      assertEquals(15.0, formula.getDoubleResult());
   }

   @Test
   void testSetDefaultResult() {
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
      assertEquals(0.0, formula.getResult());

      formula.setDefaultResult(false);
      assertFalse(formula.isDefaultResult());
      assertNull(formula.getResult());
   }

   @Test
   void testMerge() {
      MedianFormula other = new MedianFormula();
      other.addValue(10.0);
      other.addValue(20.0);

      formula.addValue(30.0);
      formula.merge(other);

      assertEquals(20.0, formula.getDoubleResult());
   }

   @Test
   void testClone() {
      formula.addValue(10.0);
      formula.addValue(20.0);

      MedianFormula cloned = (MedianFormula) formula.clone();
      assertNotSame(formula, cloned);
      assertEquals(formula.getDoubleResult(), cloned.getDoubleResult());
   }

   @Test
   void testPercentageCalculation() {
      formula.setPercentageType(StyleConstants.PERCENTAGE_OF_GROUP);
      formula.setTotal(120.0);

      formula.addValue(10.0);
      formula.addValue(20.0);
      assertEquals(0.125, formula.getDoubleResult());

      formula.reset();
      formula.addValue(10.0);
      formula.addValue(20);
      formula.addValue(30.0);
      formula.setTotal("100.0");
      assertEquals(0.2, formula.getDoubleResult());

      //invalid total
      formula.setTotal(0.0);
      assertNull(formula.getResult());
   }

   @Test
   void testGetDisplayName() {
      assertEquals("Median", formula.getDisplayName());
   }

   @Test
   void testGetName() {
      assertEquals(XConstants.MEDIAN_FORMULA, formula.getName());
   }
}