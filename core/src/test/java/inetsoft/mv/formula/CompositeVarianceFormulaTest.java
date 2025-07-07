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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompositeVarianceFormulaTest {

   private CompositeVarianceFormula formula;

   @BeforeEach
   void setUp() {
      formula = new CompositeVarianceFormula();
   }

   @Test
   void testReset() {
      formula.addValue(new double[]{1, 2, 3});
      assertFalse(formula.isNull());
      formula.reset();
      assertTrue(formula.isNull());
      assertEquals(0, formula.getDoubleResult());
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
   void testAddValueWithDoubleArray() {
      // Valid double array
      formula.addValue(new double[]{10, 3, 5});
      assertFalse(formula.isNull());
      assertEquals(0.05555555555555555, formula.getResult());
      assertEquals(0.05555555555555555, formula.getDoubleResult());

      // Invalid double array (length < 3)
      formula.reset();
      formula.addValue(new double[]{1, 2});
      assertTrue(formula.isNull());
   }

   @Test
   void testAddValueWithObjectArray() {
      // Valid object array, number value
      Object[] values = {10, 3.0, 5.0};
      formula.addValue(values);
      assertFalse(formula.isNull());
      assertEquals(0.05555555555555555, formula.getResult());
      assertEquals(0.05555555555555555, formula.getDoubleResult());

      // Valid object array, string value
      formula.reset();
      Object[] values2 = {"10", "3", "5"};
      formula.addValue(values2);
      assertFalse(formula.isNull());
      assertEquals(0.05555555555555555, formula.getResult());
      assertEquals(0.05555555555555555, formula.getDoubleResult());

      // Invalid object array (non-numeric values)
      formula.reset();
      Object[] invalidValues = {"invalid", 4.0, 2.0};
      formula.addValue(invalidValues);
      assertTrue(formula.isNull());
   }

   @Test
   void testClone() {
      CompositeVarianceFormula clonedFormula = (CompositeVarianceFormula) formula.clone();
      assertNotNull(clonedFormula);
      assertEquals(formula.getDisplayName(), clonedFormula.getDisplayName());
   }

   @Test
   void testGetDisplayName() {
      assertEquals("VarianceFormula", formula.getDisplayName());
   }

   @Test
   void testGetName() {
      assertEquals(XConstants.VARIANCE_FORMULA, formula.getName());
   }

   @Test
   void testGetResultType() {
      assertEquals(Double.class, formula.getResultType());
   }
}
