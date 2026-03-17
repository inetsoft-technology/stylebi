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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NthLargestFormula.
 *
 * <p>NthLargestFormula maintains a sorted list of the N largest distinct values.
 * getResult() returns the Nth largest value (n=1 is the maximum).
 * Duplicate values are not stored.
 */
public class NthLargestFormulaTest {

   // -----------------------------------------------------------------------
   // isNull()
   // -----------------------------------------------------------------------

   @Test
   void isNull_noValuesAdded_returnsTrue() {
      NthLargestFormula formula = new NthLargestFormula(1);
      assertTrue(formula.isNull());
   }

   @Test
   void isNull_afterAddValue_returnsFalse() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Double.valueOf(5.0));
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_afterReset_returnsTrue() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Double.valueOf(5.0));
      formula.reset();
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — no values
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_defaultResultFalse_returnsNull() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
   }

   @Test
   void getResult_noValues_defaultResultTrue_returnsZero() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.setDefaultResult(true);
      assertEquals(0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // n=1: returns the largest value
   // -----------------------------------------------------------------------

   @Test
   void getResult_n1_singleValue_returnsThatValue() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Double.valueOf(42.0));
      assertEquals(42.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_n1_multipleValues_returnsMax() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Double.valueOf(3.0));
      formula.addValue(Double.valueOf(7.0));
      formula.addValue(Double.valueOf(1.0));
      formula.addValue(Double.valueOf(9.0));
      formula.addValue(Double.valueOf(5.0));
      assertEquals(9.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // n=2: returns the second largest
   // -----------------------------------------------------------------------

   @Test
   void getResult_n2_multipleValues_returnsSecondLargest() {
      NthLargestFormula formula = new NthLargestFormula(2);
      formula.addValue(Double.valueOf(3.0));
      formula.addValue(Double.valueOf(7.0));
      formula.addValue(Double.valueOf(1.0));
      formula.addValue(Double.valueOf(9.0));
      formula.addValue(Double.valueOf(5.0));
      assertEquals(7.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_n2_fewerThanNDistinctValues_returnsNullWhenDefaultFalse() {
      NthLargestFormula formula = new NthLargestFormula(2);
      formula.setDefaultResult(false);
      formula.addValue(Double.valueOf(5.0));
      // only one distinct value, can't return 2nd largest
      assertNull(formula.getResult());
   }

   @Test
   void getResult_n2_fewerThanNDistinctValues_returnsZeroWhenDefaultTrue() {
      NthLargestFormula formula = new NthLargestFormula(2);
      formula.setDefaultResult(true);
      formula.addValue(Double.valueOf(5.0));
      assertEquals(0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // n=3: returns the third largest
   // -----------------------------------------------------------------------

   @Test
   void getResult_n3_fiveDistinctValues_returnsThirdLargest() {
      NthLargestFormula formula = new NthLargestFormula(3);
      formula.addValue(Double.valueOf(10.0));
      formula.addValue(Double.valueOf(20.0));
      formula.addValue(Double.valueOf(30.0));
      formula.addValue(Double.valueOf(40.0));
      formula.addValue(Double.valueOf(50.0));
      assertEquals(30.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // Duplicate values are not stored
   // -----------------------------------------------------------------------

   @Test
   void addValue_duplicates_onlyOneEntryKept() {
      NthLargestFormula formula = new NthLargestFormula(2);
      formula.addValue(Double.valueOf(5.0));
      formula.addValue(Double.valueOf(5.0));
      formula.addValue(Double.valueOf(5.0));
      // Only one distinct value, so n=2 cannot be satisfied
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double) — primitive overload
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_normalValues_worksCorrectly() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(4.0d);
      formula.addValue(8.0d);
      formula.addValue(2.0d);
      assertEquals(8.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueDouble_nullSentinel_isIgnored() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueDoubleArray_usesFirstElement() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(new double[]{9.0, 0.0});
      formula.addValue(new double[]{3.0, 0.0});
      assertEquals(9.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_normalValues_worksCorrectly() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(3.0f);
      formula.addValue(7.0f);
      assertEquals(7.0f, ((Number) formula.getResult()).floatValue(), 1e-6f);
   }

   @Test
   void addValueFloat_nullSentinel_isIgnored() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Tool.NULL_FLOAT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_normalValues_returnsLargest() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(100L);
      formula.addValue(200L);
      assertEquals(200L, ((Number) formula.getResult()).longValue());
   }

   @Test
   void addValueLong_nullSentinel_isIgnored() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Tool.NULL_LONG);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_normalValues_returnsLargest() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(5);
      formula.addValue(15);
      formula.addValue(10);
      assertEquals(15, ((Number) formula.getResult()).intValue());
   }

   @Test
   void addValueInt_nullSentinel_isIgnored() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Tool.NULL_INTEGER);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_normalValues_returnsLargest() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue((short) 10);
      formula.addValue((short) 3);
      assertEquals((short) 10, ((Number) formula.getResult()).shortValue());
   }

   @Test
   void addValueShort_nullSentinel_isIgnored() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Tool.NULL_SHORT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getDoubleResult()
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_noValues_returnsZero() {
      NthLargestFormula formula = new NthLargestFormula(1);
      assertEquals(0.0, formula.getDoubleResult(), 1e-10);
   }

   @Test
   void getDoubleResult_withValues_returnsNthLargestAsDouble() {
      NthLargestFormula formula = new NthLargestFormula(2);
      formula.addValue(5.0d);
      formula.addValue(3.0d);
      formula.addValue(7.0d);
      assertEquals(5.0, formula.getDoubleResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsAllValues() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Double.valueOf(10.0));
      formula.reset();
      assertTrue(formula.isNull());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Double.valueOf(100.0));
      formula.reset();
      formula.addValue(Double.valueOf(7.0));
      assertEquals(7.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // clone()
   // -----------------------------------------------------------------------

   @Test
   void clone_copiesAccumulatedValues() {
      NthLargestFormula formula = new NthLargestFormula(2);
      formula.addValue(Double.valueOf(5.0));
      formula.addValue(Double.valueOf(3.0));
      formula.addValue(Double.valueOf(7.0));
      NthLargestFormula cloned = (NthLargestFormula) formula.clone();
      assertEquals(5.0, (Double) cloned.getResult(), 1e-10);
   }

   @Test
   void clone_preservesDefaultResult() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.setDefaultResult(true);
      NthLargestFormula cloned = (NthLargestFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   @Test
   void clone_isIndependentOfOriginal() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Double.valueOf(5.0));
      NthLargestFormula cloned = (NthLargestFormula) formula.clone();
      formula.reset();
      // cloned still has the value
      assertFalse(cloned.isNull());
      assertEquals(5.0, (Double) cloned.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // invalid n clamps to 1
   // -----------------------------------------------------------------------

   @Test
   void constructor_invalidNZero_clampsToOne() {
      NthLargestFormula formula = new NthLargestFormula(0);
      formula.addValue(Double.valueOf(42.0));
      assertEquals(42.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void constructor_invalidNNegative_clampsToOne() {
      NthLargestFormula formula = new NthLargestFormula(-5);
      formula.addValue(Double.valueOf(99.0));
      assertEquals(99.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // defaultResult / setDefaultResult
   // -----------------------------------------------------------------------

   @Test
   void setDefaultResult_trueAndFalse_roundTrip() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
      formula.setDefaultResult(false);
      assertFalse(formula.isDefaultResult());
   }
}
