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
package inetsoft.report.filter;

import inetsoft.util.Tool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NthSmallestFormula.
 *
 * <p>NthSmallestFormula extends NthLargestFormula with an inverted comparator so it tracks the
 * N smallest distinct values instead. n=1 returns the minimum.
 */
public class NthSmallestFormulaTest {

   // -----------------------------------------------------------------------
   // isNull()
   // -----------------------------------------------------------------------

   @Test
   void isNull_noValuesAdded_returnsTrue() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      assertTrue(formula.isNull());
   }

   @Test
   void isNull_afterAddValue_returnsFalse() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Double.valueOf(5.0));
      assertFalse(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — no values
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_defaultResultFalse_returnsNull() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
   }

   @Test
   void getResult_noValues_defaultResultTrue_returnsZero() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.setDefaultResult(true);
      // getResult() returns Integer(0) when def=true and no qualifying values
      assertEquals(Integer.valueOf(0), formula.getResult());
   }

   // -----------------------------------------------------------------------
   // n=1: returns the minimum value
   // -----------------------------------------------------------------------

   @Test
   void getResult_n1_singleValue_returnsThatValue() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Double.valueOf(42.0));
      assertEquals(42.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_n1_multipleValues_returnsMin() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Double.valueOf(3.0));
      formula.addValue(Double.valueOf(7.0));
      formula.addValue(Double.valueOf(1.0));
      formula.addValue(Double.valueOf(9.0));
      formula.addValue(Double.valueOf(5.0));
      assertEquals(1.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // n=2: returns the second smallest
   // -----------------------------------------------------------------------

   @Test
   void getResult_n2_multipleValues_returnsSecondSmallest() {
      NthSmallestFormula formula = new NthSmallestFormula(2);
      formula.addValue(Double.valueOf(3.0));
      formula.addValue(Double.valueOf(7.0));
      formula.addValue(Double.valueOf(1.0));
      formula.addValue(Double.valueOf(9.0));
      formula.addValue(Double.valueOf(5.0));
      assertEquals(3.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_n2_fewerThanNDistinctValues_returnsNullWhenDefaultFalse() {
      NthSmallestFormula formula = new NthSmallestFormula(2);
      formula.setDefaultResult(false);
      formula.addValue(Double.valueOf(5.0));
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // n=3: returns the third smallest
   // -----------------------------------------------------------------------

   @Test
   void getResult_n3_fiveValues_returnsThirdSmallest() {
      NthSmallestFormula formula = new NthSmallestFormula(3);
      formula.addValue(Double.valueOf(10.0));
      formula.addValue(Double.valueOf(20.0));
      formula.addValue(Double.valueOf(30.0));
      formula.addValue(Double.valueOf(40.0));
      formula.addValue(Double.valueOf(50.0));
      assertEquals(30.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // Duplicates are not stored
   // -----------------------------------------------------------------------

   @Test
   void addValue_duplicates_onlyOneEntryKept() {
      NthSmallestFormula formula = new NthSmallestFormula(2);
      formula.addValue(Double.valueOf(5.0));
      formula.addValue(Double.valueOf(5.0));
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // Ordering: NthSmallest vs NthLargest should differ for same inputs
   // -----------------------------------------------------------------------

   @Test
   void getResult_n1_smallestAndLargestReturnDifferentValues() {
      NthSmallestFormula smallest = new NthSmallestFormula(1);
      NthLargestFormula largest = new NthLargestFormula(1);
      Double[] values = {Double.valueOf(1.0), Double.valueOf(5.0), Double.valueOf(9.0)};
      for(Double v : values) {
         smallest.addValue(v);
         largest.addValue(v);
      }
      assertEquals(1.0, (Double) smallest.getResult(), 1e-10);
      assertEquals(9.0, (Double) largest.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(double) — primitive overload
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_normalValues_returnsSmallest() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(8.0d);
      formula.addValue(2.0d);
      formula.addValue(5.0d);
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueDouble_nullSentinel_isIgnored() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_normalValues_returnsSmallest() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(30);
      formula.addValue(10);
      formula.addValue(20);
      assertEquals(10, ((Number) formula.getResult()).intValue());
   }

   @Test
   void addValueInt_nullSentinel_isIgnored() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Tool.NULL_INTEGER);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_normalValues_returnsSmallest() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(300L);
      formula.addValue(100L);
      assertEquals(100L, ((Number) formula.getResult()).longValue());
   }

   @Test
   void addValueLong_nullSentinel_isIgnored() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Tool.NULL_LONG);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_normalValues_returnsSmallest() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(9.0f);
      formula.addValue(3.0f);
      assertEquals(3.0f, ((Number) formula.getResult()).floatValue(), 1e-6f);
   }

   @Test
   void addValueFloat_nullSentinel_isIgnored() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Tool.NULL_FLOAT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_normalValues_returnsSmallest() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue((short) 20);
      formula.addValue((short) 5);
      assertEquals((short) 5, ((Number) formula.getResult()).shortValue());
   }

   @Test
   void addValueShort_nullSentinel_isIgnored() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Tool.NULL_SHORT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getDoubleResult()
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_noValues_returnsZero() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      assertEquals(0.0, formula.getDoubleResult(), 1e-10);
   }

   @Test
   void getDoubleResult_withValues_returnsNthSmallestAsDouble() {
      NthSmallestFormula formula = new NthSmallestFormula(2);
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      formula.addValue(5.0d);
      assertEquals(3.0, formula.getDoubleResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsAllValues() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Double.valueOf(10.0));
      formula.reset();
      assertTrue(formula.isNull());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Double.valueOf(100.0));
      formula.reset();
      formula.addValue(Double.valueOf(7.0));
      assertEquals(7.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // clone()
   // -----------------------------------------------------------------------

   @Test
   void clone_returnsNthSmallestFormulaInstance() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Double.valueOf(5.0));
      Object cloned = formula.clone();
      assertInstanceOf(NthSmallestFormula.class, cloned);
   }

   @Test
   void clone_copiesAccumulatedValues() {
      NthSmallestFormula formula = new NthSmallestFormula(2);
      formula.addValue(Double.valueOf(1.0));
      formula.addValue(Double.valueOf(3.0));
      formula.addValue(Double.valueOf(5.0));
      NthSmallestFormula cloned = (NthSmallestFormula) formula.clone();
      assertEquals(3.0, (Double) cloned.getResult(), 1e-10);
   }

   @Test
   void clone_preservesDefaultResult() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.setDefaultResult(true);
      NthSmallestFormula cloned = (NthSmallestFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   // -----------------------------------------------------------------------
   // invalid n clamps to 1
   // -----------------------------------------------------------------------

   @Test
   void constructor_invalidNZero_clampsToOne() {
      NthSmallestFormula formula = new NthSmallestFormula(0);
      formula.addValue(Double.valueOf(42.0));
      assertEquals(42.0, (Double) formula.getResult(), 1e-10);
   }
}
