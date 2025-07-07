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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompositeAverageFormulaTest {

   private CompositeAverageFormula formula;

   @BeforeEach
   void setUp() {
      formula = new CompositeAverageFormula();
   }

   @Test
   void testReset() {
      formula.addValue(new double[]{10, 2});
      formula.reset();
      assertEquals(0, formula.getDoubleResult());
      assertTrue(formula.isNull());
   }

   @Test
   void testSetGetDefaultResult() {
      // Set default result to true
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
      assertEquals(0.0, formula.getResult());

      // Set default result to false
      formula.setDefaultResult(false);
      assertFalse(formula.isDefaultResult());
      assertNull(formula.getResult());
   }

   @Test
   void testAddValue() {
      //invalid object
      formula.addValue("invalid");
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

      //double array
      formula.addValue(new double[]{10, 2});
      assertEquals(5.0, formula.getDoubleResult());

      //object array
      formula.addValue(new Object[]{"10", "3"});//not number
      formula.addValue(new Object[]{15, 2});//number
      assertEquals(5.0, formula.getDoubleResult());
   }

   @Test
   void testPercentageCalculation() {
      formula.setPercentageType(StyleConstants.PERCENTAGE_OF_GROUP);
      formula.setTotal(100);
      formula.addValue(new double[]{50, 2});
      assertEquals(0.25, formula.getDoubleResult());
      assertEquals(0.25, formula.getResult());
      assertEquals(25.0, formula.getOriginalResult());
   }

   @Test
   void testGetResultWithZeroTotal() {
      formula.setPercentageType(StyleConstants.PERCENTAGE_OF_GROUP);
      formula.setTotal(0); // Set total to zero
      formula.addValue(new double[]{0, 2});
      formula.setDefaultResult(false); // Set default result to false
      assertNull(formula.getResult()); // Verify that result is null

      formula.setDefaultResult(true); // Set default result to true
      assertEquals(0.0, formula.getResult()); // Verify that result is 0.0
      assertEquals(0.0, formula.getDoubleResult()); // Verify that double result is 0.0
   }

   @Test
   void testClone() {
      CompositeAverageFormula clonedFormula = (CompositeAverageFormula) formula.clone();
      assertNotNull(clonedFormula);
      assertEquals(formula.getDisplayName(), clonedFormula.getDisplayName());
   }

   @Test
   void testGetSecondaryColumns() {
      CompositeAverageFormula formulaWithColumn = new CompositeAverageFormula(1);
      assertArrayEquals(new int[]{1}, formulaWithColumn.getSecondaryColumns());
   }

   @Test
   void testGetDisplayName() {
      assertEquals("Average", formula.getDisplayName());
   }

   @Test
   void testGetName() {
      assertEquals(XConstants.AVERAGE_FORMULA, formula.getName());
   }

   @Test
   void testGetResultType() {
      assertEquals(Double.class, formula.getResultType());
   }
}