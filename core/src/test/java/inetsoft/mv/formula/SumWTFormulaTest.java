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

class SumWTFormulaTest {

   private SumWTFormula formula;

   @BeforeEach
   void setUp() {
      formula = new SumWTFormula();
   }

   @Test
   void testReset() {
      formula.addValue(new double[]{2.0, 3.0});
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
   void testAddValueWithDoubleArray() {
      formula.addValue(new double[]{2.0, 3.0});
      assertFalse(formula.isNull());
      assertEquals(6.0, formula.getDoubleResult());

      formula.addValue(new double[]{Tool.NULL_DOUBLE, 3.0});
      assertEquals(6.0, formula.getDoubleResult());

      formula.addValue(new double[]{2.0, Tool.NULL_DOUBLE});
      assertEquals(6.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueWithObject() {
      formula.addValue(new Object[]{2.0, 3.0});
      assertFalse(formula.isNull());
      assertEquals(6.0, formula.getDoubleResult());

      formula.addValue(new Object[]{null, 3.0});
      assertEquals(6.0, formula.getDoubleResult());

      formula.addValue(new Object[]{2.0, null});
      assertEquals(6.0, formula.getDoubleResult());

      formula.addValue(new Object[]{"4", "5"});
      assertEquals(26.0, formula.getDoubleResult());

      formula.addValue(new Object[]{"invalid", "5"});
      assertEquals(26.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueInvalid() {
      // Test addValue(double)
      Exception exceptionDouble = assertThrows(RuntimeException.class, () -> formula.addValue(1.0));
      assertEquals("Unsupported method called!", exceptionDouble.getMessage());

      // Test addValue(float)
      Exception exceptionFloat = assertThrows(RuntimeException.class, () -> formula.addValue(1.0f));
      assertEquals("Unsupported method called!", exceptionFloat.getMessage());

      // Test addValue(long)
      Exception exceptionLong = assertThrows(RuntimeException.class, () -> formula.addValue(1L));
      assertEquals("Unsupported method called!", exceptionLong.getMessage());

      // Test addValue(int)
      Exception exceptionInt = assertThrows(RuntimeException.class, () -> formula.addValue(1));
      assertEquals("Unsupported method called!", exceptionInt.getMessage());

      // Test addValue(short)
      Exception exceptionShort = assertThrows(RuntimeException.class, () -> formula.addValue((short) 1));
      assertEquals("Unsupported method called!", exceptionShort.getMessage());
   }

   @Test
   void testGetDisplayName() {
      assertEquals("SumWTFormula", formula.getDisplayName());
   }

   @Test
   void testGetName() {
      assertEquals(XConstants.SUMWT_FORMULA, formula.getName());
   }

   @Test
   void testClone() {
      SumWTFormula clonedFormula = (SumWTFormula) formula.clone();
      assertNotNull(clonedFormula);
      assertEquals(formula.getDisplayName(), clonedFormula.getDisplayName());
   }
}