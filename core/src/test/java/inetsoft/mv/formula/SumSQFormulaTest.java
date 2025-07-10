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

class SumSQFormulaTest {

   private SumSQFormula formula;

   @BeforeEach
   void setUp() {
      formula = new SumSQFormula();
   }

   @Test
   void testReset() {
      formula.addValue(2.0);
      assertFalse(formula.isNull());
      formula.reset();
      assertTrue(formula.isNull());
      assertEquals(0.0, formula.getDoubleResult());
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
   void testAddValueWithDouble() {
      formula.addValue(3.0);
      assertFalse(formula.isNull());
      assertEquals(9.0, formula.getDoubleResult());

      formula.addValue(Tool.NULL_DOUBLE);
      assertEquals(9.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueWithDoubleArray() {
      formula.addValue(new double[]{4.0});
      assertFalse(formula.isNull());
      assertEquals(16.0, formula.getDoubleResult());

      formula.addValue(new double[]{Tool.NULL_DOUBLE});
      assertEquals(16.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueWithFloat() {
      formula.addValue(2.0f);
      assertFalse(formula.isNull());
      assertEquals(4.0, formula.getDoubleResult());

      formula.addValue(Tool.NULL_FLOAT);
      assertEquals(4.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueWithLong() {
      formula.addValue(5L);
      assertFalse(formula.isNull());
      assertEquals(25.0, formula.getDoubleResult());

      formula.addValue(Tool.NULL_LONG);
      assertEquals(25.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueWithInt() {
      formula.addValue(3);
      assertFalse(formula.isNull());
      assertEquals(9.0, formula.getDoubleResult());

      formula.addValue(Tool.NULL_INTEGER);
      assertEquals(9.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueWithShort() {
      formula.addValue((short) 2);
      assertFalse(formula.isNull());
      assertEquals(4.0, formula.getDoubleResult());

      formula.addValue(Tool.NULL_SHORT);
      assertEquals(4.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueWithObject() {
      Object obj = 3.0;
      formula.addValue(obj);
      assertFalse(formula.isNull());
      assertEquals(9.0, formula.getDoubleResult());

      formula.addValue("4");
      assertEquals(25.0, formula.getDoubleResult());

      formula.addValue("invalid");
      assertEquals(25.0, formula.getDoubleResult());
   }

   @Test
   void testGetResult() {
      formula.addValue(2.0);
      assertEquals(4.0, formula.getResult());

      formula.reset();
      formula.setDefaultResult(true);
      assertEquals(0.0, formula.getResult());

      formula.setDefaultResult(false);
      assertNull(formula.getResult());
   }

   @Test
   void testGetDisplayName() {
      assertEquals("SumSQ", formula.getDisplayName());
   }

   @Test
   void testGetName() {
      assertEquals(XConstants.SUMSQ_FORMULA, formula.getName());
   }

   @Test
   void testClone() {
      SumSQFormula clonedFormula = (SumSQFormula) formula.clone();
      assertNotNull(clonedFormula);
      assertEquals(formula.getDisplayName(), clonedFormula.getDisplayName());
   }
}