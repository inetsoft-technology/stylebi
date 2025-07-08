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

class NthMostFrequentFormulaTest {

   private NthMostFrequentFormula formula;

   @BeforeEach
   void setUp() {
      formula = new NthMostFrequentFormula(2); // Test for the 2nd most frequent value
   }

   @Test
   void testConstructorInvalidN() {
      Exception exception = assertThrows(RuntimeException.class, () -> new NthMostFrequentFormula(0));
      assertEquals("N should be greater than zero", exception.getMessage());
   }

   @Test
   void testReset() {
      formula.addValue(1.0);
      assertFalse(formula.isNull());
      formula.reset();
      assertTrue(formula.isNull());
   }

   @Test
   void testAddValueWithDouble() {
      formula.addValue(1.0);
      formula.addValue(1.0);
      formula.addValue(2.0);
      formula.addValue(3.0);
      formula.addValue(3.0);

      assertFalse(formula.isNull());
      assertEquals(3.0, formula.getResult());
      assertEquals(3.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueWithDoubleArray() {
      formula.addValue(new double[]{1.0});
      formula.addValue(new double[]{2.0});
      formula.addValue(new double[]{2.0});
      formula.addValue(new double[]{3.0});
      formula.addValue(new double[]{3.0});
      formula.addValue(new double[]{3.0});

      assertFalse(formula.isNull());
      assertEquals(2.0, formula.getResult());
      assertEquals(2.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueWithObjectArray() {
      formula.addValue(new Object[]{1.0});
      formula.addValue(new Object[]{2.0});
      formula.addValue(new Object[]{2.0});
      formula.addValue(new Object[]{3.0});
      formula.addValue(new Object[]{3.0});
      formula.addValue(new Object[]{3.0});

      assertFalse(formula.isNull());
//      assertEquals(2.0, formula.getResult()); //Bug #71806
//      assertEquals(2.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueWithFloat() {
      formula.addValue(1.0f);
      formula.addValue(2.0f);
      formula.addValue(2.0f);

      assertFalse(formula.isNull());
      assertEquals(1.0, formula.getResult());
      assertEquals(1.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueWithLong() {
      formula.addValue(1L);
      formula.addValue(2L);
      formula.addValue(2L);

      assertFalse(formula.isNull());
      assertEquals(1.0, formula.getResult());
      assertEquals(1.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueWithInt() {
      formula.addValue(1);
      formula.addValue(2);
      formula.addValue(2);

      assertFalse(formula.isNull());
      assertEquals(1.0, formula.getResult());
      assertEquals(1.0, formula.getDoubleResult());
   }

   @Test
   void testAddValueWithShort() {
      formula.addValue((short) 1);
      formula.addValue((short) 2);
      formula.addValue((short) 2);

      assertFalse(formula.isNull());
      assertEquals(1.0, formula.getResult());
      assertEquals(1.0, formula.getDoubleResult());
   }

   @Test
   void testSetDefaultResult() {
      formula.reset();
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
      assertEquals(0, formula.getResult());
      assertEquals(0.0, formula.getDoubleResult());

      formula.reset();
      formula.setDefaultResult(false);
      assertFalse(formula.isDefaultResult());
      assertNull(formula.getResult());
      assertEquals(0.0, formula.getDoubleResult());
   }

   @Test
   void testClone() {
      formula.addValue(1.0);
      formula.addValue(2.0);

      NthMostFrequentFormula clonedFormula = (NthMostFrequentFormula) formula.clone();
      assertNotNull(clonedFormula);
      assertEquals(formula.getDisplayName(), clonedFormula.getDisplayName());
      assertEquals(formula.getResult(), clonedFormula.getResult());
   }

   @Test
   void testMerge() {
      formula.addValue(1.0);
      formula.addValue(1.0);
      formula.addValue(2.0);

      NthMostFrequentFormula otherFormula = new NthMostFrequentFormula(2);
      otherFormula.addValue(2.0);
      otherFormula.addValue(3.0);

      formula.merge(otherFormula);

      assertEquals(2.0, formula.getResult());
      assertEquals(2.0, formula.getDoubleResult());
   }

   @Test
   void testGetDisplayName() {
      assertEquals("NthMostFrequent", formula.getDisplayName());
   }

   @Test
   void testGetName() {
      assertEquals(XConstants.NTHMOSTFREQUENT_FORMULA, formula.getName());
   }
}