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
 * Tests for PthPercentileFormula.
 *
 * <p>The formula uses nearest-rank method: pos = round(p/100.0 * (n-1)).
 * isNull() returns true only when no double values have been added.
 * Object, float, long, int, short are stored in separate lists from double.
 */
public class PthPercentileFormulaTest {

   // -----------------------------------------------------------------------
   // isNull()
   // -----------------------------------------------------------------------

   @Test
   void isNull_noValuesAdded_returnsTrue() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      assertTrue(formula.isNull());
   }

   @Test
   void isNull_afterAddDoubleValue_returnsFalse() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(5.0d);
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_afterAddObjectValueOnly_remainsTrue() {
      // isNull() checks only dlist != null — Object-only values populate the vs
      // Vector, not dlist, so isNull() remains true even with values present.
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(Double.valueOf(5.0));
      assertTrue(formula.isNull());
   }

   @Test
   void getResult_afterAddObjectValueOnly_returnsValueViaObjectBranch() {
      // Even though isNull() returns true for Object-only input, getResult()
      // accesses the vs Vector (not dlist) and does return the computed percentile.
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(Double.valueOf(5.0));
      assertEquals(Double.valueOf(5.0), formula.getResult());
   }

   @Test
   void isNull_afterReset_returnsTrue() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(5.0d);
      formula.reset();
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — empty
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_defaultResultFalse_returnsNull() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
   }

   @Test
   void getResult_noValues_defaultResultTrue_returnsZeroDouble() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.setDefaultResult(true);
      assertEquals(0.0, (double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // percentile clamping
   // -----------------------------------------------------------------------

   @Test
   void constructor_percentileAbove100_clampsTo100() {
      PthPercentileFormula formula = new PthPercentileFormula(150);
      formula.addValue(1.0d);
      formula.addValue(2.0d);
      formula.addValue(3.0d);
      // clamped to 100 → should return maximum = 3.0
      assertEquals(3.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void constructor_percentileBelowZero_clampsToZero() {
      PthPercentileFormula formula = new PthPercentileFormula(-10);
      formula.addValue(1.0d);
      formula.addValue(2.0d);
      formula.addValue(3.0d);
      // clamped to 0 → should return minimum = 1.0
      assertEquals(1.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(double) — 0th, 50th, 100th percentile
   // -----------------------------------------------------------------------

   @Test
   void getResult_0thPercentile_returnsMinimum() {
      PthPercentileFormula formula = new PthPercentileFormula(0);
      formula.addValue(5.0d);
      formula.addValue(1.0d);
      formula.addValue(9.0d);
      formula.addValue(3.0d);
      // 0th percentile → pos=0, minimum after sort
      assertEquals(1.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_100thPercentile_returnsMaximum() {
      PthPercentileFormula formula = new PthPercentileFormula(100);
      formula.addValue(5.0d);
      formula.addValue(1.0d);
      formula.addValue(9.0d);
      formula.addValue(3.0d);
      // 100th percentile → pos=n-1, maximum after sort
      assertEquals(9.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_50thPercentile_returnsMedianIndex() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      // {1, 2, 3, 4, 5} sorted → pos = round(0.5 * 4) = 2 → value 3
      formula.addValue(3.0d);
      formula.addValue(1.0d);
      formula.addValue(5.0d);
      formula.addValue(2.0d);
      formula.addValue(4.0d);
      assertEquals(3.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_25thPercentile_returnsCorrectValue() {
      PthPercentileFormula formula = new PthPercentileFormula(25);
      // {1, 2, 3, 4, 5} sorted → pos = round(0.25 * 4) = round(1.0) = 1 → value 2
      formula.addValue(3.0d);
      formula.addValue(1.0d);
      formula.addValue(5.0d);
      formula.addValue(2.0d);
      formula.addValue(4.0d);
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_75thPercentile_returnsCorrectValue() {
      PthPercentileFormula formula = new PthPercentileFormula(75);
      // {1, 2, 3, 4, 5} sorted → pos = round(0.75 * 4) = round(3.0) = 3 → value 4
      formula.addValue(3.0d);
      formula.addValue(1.0d);
      formula.addValue(5.0d);
      formula.addValue(2.0d);
      formula.addValue(4.0d);
      assertEquals(4.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_singleDoubleValue_returnsItForAnyPercentile() {
      PthPercentileFormula f0 = new PthPercentileFormula(0);
      PthPercentileFormula f50 = new PthPercentileFormula(50);
      PthPercentileFormula f100 = new PthPercentileFormula(100);
      f0.addValue(7.0d);
      f50.addValue(7.0d);
      f100.addValue(7.0d);
      assertEquals(7.0, (Double) f0.getResult(), 1e-10);
      assertEquals(7.0, (Double) f50.getResult(), 1e-10);
      assertEquals(7.0, (Double) f100.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(double) — null sentinel
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_nullSentinel_isIgnored() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueDoubleArray_nullSentinelInFirstPosition_isIgnored() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(new double[]{Tool.NULL_DOUBLE, 5.0});
      assertTrue(formula.isNull());
   }

   @Test
   void addValueDoubleArray_normalValue_addsFirstElement() {
      PthPercentileFormula formula = new PthPercentileFormula(0);
      formula.addValue(new double[]{3.0, 99.0});
      formula.addValue(new double[]{1.0, 99.0});
      assertEquals(1.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_0thPercentile_returnsMin() {
      PthPercentileFormula formula = new PthPercentileFormula(0);
      formula.addValue(3.0f);
      formula.addValue(1.0f);
      formula.addValue(5.0f);
      assertEquals(1.0f, ((Number) formula.getResult()).floatValue(), 1e-6f);
   }

   @Test
   void addValueFloat_100thPercentile_returnsMax() {
      PthPercentileFormula formula = new PthPercentileFormula(100);
      formula.addValue(3.0f);
      formula.addValue(1.0f);
      formula.addValue(5.0f);
      assertEquals(5.0f, ((Number) formula.getResult()).floatValue(), 1e-6f);
   }

   @Test
   void addValueFloat_nullSentinel_isIgnored() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(Tool.NULL_FLOAT);
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_0thPercentile_returnsMin() {
      PthPercentileFormula formula = new PthPercentileFormula(0);
      formula.addValue(30L);
      formula.addValue(10L);
      formula.addValue(20L);
      assertEquals(10L, ((Number) formula.getResult()).longValue());
   }

   @Test
   void addValueLong_100thPercentile_returnsMax() {
      PthPercentileFormula formula = new PthPercentileFormula(100);
      formula.addValue(30L);
      formula.addValue(10L);
      formula.addValue(20L);
      assertEquals(30L, ((Number) formula.getResult()).longValue());
   }

   @Test
   void addValueLong_nullSentinel_isIgnored() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(Tool.NULL_LONG);
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_50thPercentile_returnsMiddleValue() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      // {10, 20, 30} sorted → pos = round(0.5 * 2) = 1 → 20
      formula.addValue(30);
      formula.addValue(10);
      formula.addValue(20);
      assertEquals(20, ((Number) formula.getResult()).intValue());
   }

   @Test
   void addValueInt_nullSentinel_isIgnored() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(Tool.NULL_INTEGER);
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_100thPercentile_returnsMax() {
      PthPercentileFormula formula = new PthPercentileFormula(100);
      formula.addValue((short) 5);
      formula.addValue((short) 15);
      formula.addValue((short) 10);
      assertEquals((short) 15, ((Number) formula.getResult()).shortValue());
   }

   @Test
   void addValueShort_nullSentinel_isIgnored() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(Tool.NULL_SHORT);
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(Object)
   // -----------------------------------------------------------------------

   @Test
   void addValueObject_nullIsIgnored() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue((Object) null);
      assertNull(formula.getResult());
   }

   @Test
   void addValueObject_numericValues_0thPercentileReturnsMin() {
      PthPercentileFormula formula = new PthPercentileFormula(0);
      formula.addValue(Double.valueOf(30.0));
      formula.addValue(Double.valueOf(10.0));
      formula.addValue(Double.valueOf(20.0));
      // Object path uses NumericComparer, result should be min
      Object result = formula.getResult();
      assertNotNull(result);
      assertEquals(10.0, ((Number) result).doubleValue(), 1e-10);
   }

   @Test
   void addValueObject_numericValues_100thPercentileReturnsMax() {
      PthPercentileFormula formula = new PthPercentileFormula(100);
      formula.addValue(Double.valueOf(30.0));
      formula.addValue(Double.valueOf(10.0));
      formula.addValue(Double.valueOf(20.0));
      Object result = formula.getResult();
      assertNotNull(result);
      assertEquals(30.0, ((Number) result).doubleValue(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // getDoubleResult()
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_noDoubleValues_returnsZero() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      assertEquals(0.0, formula.getDoubleResult(), 1e-10);
   }

   @Test
   void getDoubleResult_withDoubleValues_returnsPercentileValue() {
      PthPercentileFormula formula = new PthPercentileFormula(0);
      formula.addValue(5.0d);
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      assertEquals(1.0, formula.getDoubleResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsAllValues() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(5.0d);
      formula.reset();
      assertTrue(formula.isNull());
      assertNull(formula.getResult());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      PthPercentileFormula formula = new PthPercentileFormula(0);
      formula.addValue(99.0d);
      formula.reset();
      formula.addValue(1.0d);
      formula.addValue(2.0d);
      assertEquals(1.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // clone()
   // -----------------------------------------------------------------------

   @Test
   void clone_preservesDefaultResult() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.setDefaultResult(true);
      PthPercentileFormula cloned = (PthPercentileFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   @Test
   void clone_copiesObjectValues() {
      PthPercentileFormula formula = new PthPercentileFormula(0);
      formula.addValue(Double.valueOf(30.0));
      formula.addValue(Double.valueOf(10.0));
      formula.addValue(Double.valueOf(20.0));
      PthPercentileFormula cloned = (PthPercentileFormula) formula.clone();
      Object result = cloned.getResult();
      assertNotNull(result);
      assertEquals(10.0, ((Number) result).doubleValue(), 1e-10);
   }

   @Test
   void clone_isIndependentOfOriginal() {
      PthPercentileFormula formula = new PthPercentileFormula(0);
      formula.addValue(Double.valueOf(10.0));
      PthPercentileFormula cloned = (PthPercentileFormula) formula.clone();
      formula.reset();
      // cloned still has its value
      Object result = cloned.getResult();
      assertNotNull(result);
   }

   // -----------------------------------------------------------------------
   // defaultResult / setDefaultResult
   // -----------------------------------------------------------------------

   @Test
   void setDefaultResult_trueAndFalse_roundTrip() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
      formula.setDefaultResult(false);
      assertFalse(formula.isDefaultResult());
   }
}
