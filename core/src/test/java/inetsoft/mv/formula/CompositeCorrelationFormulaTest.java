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

class CompositeCorrelationFormulaTest {

   private CompositeCorrelationFormula formula;

   @BeforeEach
   void setUp() {
      formula = new CompositeCorrelationFormula();
   }

   @Test
   void testReset() {
      formula.reset();
      assertEquals(0, formula.getDoubleResult());
      assertNull(formula.getResult());
      assertTrue(formula.isNull());
   }

   @Test
   void testAddValueInvalid() {
      //invalid object array
      Object[] values = {"invalid", 2.0, 3.0, 4.0, 5.0, 6.0, 7.0};
      formula.addValue(values);
      assertTrue(formula.isNull());

      //double array, 5 value
      formula.reset();
      formula.addValue(new double[]{1.0, 2.0, 3.0, 4.0, 5.0});
      assertTrue(formula.isNull());

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
   void testAddValueGetResult() {
      //double array, 7 value
      formula.addValue(new double[]{1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0});
      assertFalse(formula.isNull());
      assertEquals(-0.13055824196677338, formula.getResult());
      assertEquals(-0.13055824196677338, formula.getDoubleResult());

      //double array, 4 value
      formula.reset();
      formula.addValue(new double[]{1.0, 2.0, 3.0, 4.0});
      assertFalse(formula.isNull());
      assertEquals(-0.125, formula.getResult());
      assertEquals(-0.125, formula.getDoubleResult());

      //zero standard deviation
      formula.reset();
      formula.addValue(new double[]{1.0, 1.0, 1.0, 1, 1.0, 1, 1.0}); // Values that lead to zero standard deviation
      assertNull(formula.getResult());
      assertEquals(0, formula.getDoubleResult());
   }

   @Test
   void testAddValueWithObjectArray() {
      //number value
      Object[] values = {1, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0};
      formula.addValue(values);
      assertFalse(formula.isNull());
      assertEquals(-0.13055824196677338, formula.getResult());
      assertEquals(-0.13055824196677338, formula.getDoubleResult());
      formula.reset();
      assertTrue(formula.isNull());

      //string value
      values = new Object[]{"1.0", "2.0", "3.0", "4.0", "5.0", "6.0", "7.0"};
      formula.addValue(values);
      assertFalse(formula.isNull());
      assertEquals(-0.13055824196677338, formula.getResult());
      assertEquals(-0.13055824196677338, formula.getDoubleResult());
   }

   @Test
   void testSetDefaultResult() {
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
   }

   @Test
   void testClone() {
      CompositeCorrelationFormula clonedFormula = (CompositeCorrelationFormula) formula.clone();
      assertNotNull(clonedFormula);
   }

   @Test
   void testGetDisplayName() {
      assertEquals("CorrelationFormula", formula.getDisplayName());
   }

   @Test
   void testGetName() {
      assertEquals(XConstants.CORRELATION_FORMULA, formula.getName());
   }

   @Test
   void testGetResultType() {
      assertEquals(Double.class, formula.getResultType());
   }
}