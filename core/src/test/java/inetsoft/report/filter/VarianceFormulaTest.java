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
 * Tests for VarianceFormula (sample variance, divides by N-1).
 *
 * <p>Known values: for {2, 4, 4, 4, 5, 5, 7, 9}, sample variance = 4.571428...
 * For {2, 4, 4, 4, 5, 5, 7, 9} mean=5, sum of sq deviations=32, sample var=32/7≈4.5714
 */
public class VarianceFormulaTest {

   private VarianceFormula formula;

   @BeforeEach
   void setUp() {
      formula = new VarianceFormula();
   }

   // -----------------------------------------------------------------------
   // isNull()
   // -----------------------------------------------------------------------

   @Test
   void isNull_noValuesAdded_returnsTrue() {
      assertTrue(formula.isNull());
   }

   @Test
   void isNull_afterAddValue_returnsFalse() {
      formula.addValue(1.0d);
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_afterReset_returnsTrue() {
      formula.addValue(1.0d);
      formula.reset();
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — empty / single value
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_defaultResultFalse_returnsNull() {
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
   }

   @Test
   void getResult_noValues_defaultResultTrue_returnsZero() {
      formula.setDefaultResult(true);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_singleValue_returnsZero() {
      // variance with one value: count==1, getRawResult returns 0 (count <= 1 guard)
      formula.addValue(5.0d);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // Known mathematical values — sample variance
   // -----------------------------------------------------------------------

   @Test
   void getResult_twoIdenticalValues_returnsZero() {
      formula.addValue(3.0d);
      formula.addValue(3.0d);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_twoValues_correctSampleVariance() {
      // {1, 3}: mean=2, deviations=1,1, sum sq dev=2, sample var = 2/(2-1) = 2
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_classicExample_correctSampleVariance() {
      // {2, 4, 4, 4, 5, 5, 7, 9}: mean=5, sum sq dev=32, sample var = 32/7
      double[] values = {2, 4, 4, 4, 5, 5, 7, 9};
      for(double v : values) {
         formula.addValue(v);
      }
      double expected = 32.0 / 7.0;
      assertEquals(expected, (Double) formula.getResult(), 1e-6);
   }

   @Test
   void getResult_simpleIntegers_correctSampleVariance() {
      // {1, 2, 3, 4, 5}: mean=3, deviations=-2,-1,0,1,2, sum sq dev=10, sample var=10/4=2.5
      formula.addValue(1.0d);
      formula.addValue(2.0d);
      formula.addValue(3.0d);
      formula.addValue(4.0d);
      formula.addValue(5.0d);
      assertEquals(2.5, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // getResultType()
   // -----------------------------------------------------------------------

   @Test
   void getResultType_returnsDoubleClass() {
      assertEquals(Double.class, formula.getResultType());
   }

   // -----------------------------------------------------------------------
   // addValue(Object)
   // -----------------------------------------------------------------------

   @Test
   void addValue_nullObject_isIgnored() {
      formula.addValue((Object) null);
      assertTrue(formula.isNull());
   }

   @Test
   void addValue_numericObject_isAccepted() {
      formula.addValue(Integer.valueOf(2));
      formula.addValue(Integer.valueOf(4));
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValue_numericString_isParsedAndAccepted() {
      formula.addValue("2");
      formula.addValue("4");
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValue_nonNumericString_isIgnored() {
      formula.addValue("hello");
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(double)
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueDoubleArray_usesFirstElement() {
      formula.addValue(new double[]{2.0, 99.0});
      formula.addValue(new double[]{4.0, 99.0});
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueDoubleArray_nullSentinelInFirstPosition_isIgnored() {
      formula.addValue(new double[]{Tool.NULL_DOUBLE, 99.0});
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_normalValues_computesVariance() {
      formula.addValue(2.0f);
      formula.addValue(4.0f);
      assertEquals(2.0, (Double) formula.getResult(), 1e-5);
   }

   @Test
   void addValueFloat_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_FLOAT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_normalValues_computesVariance() {
      formula.addValue(2L);
      formula.addValue(4L);
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueLong_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_LONG);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_normalValues_computesVariance() {
      formula.addValue(2);
      formula.addValue(4);
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueInt_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_INTEGER);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_normalValues_computesVariance() {
      formula.addValue((short) 2);
      formula.addValue((short) 4);
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueShort_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_SHORT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getDoubleResult()
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_matchesGetResult() {
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      assertEquals(2.0, formula.getDoubleResult(), 1e-10);
      assertEquals((Double) formula.getResult(), formula.getDoubleResult(), 1e-10);
   }

   @Test
   void getDoubleResult_noValues_returnsZero() {
      assertEquals(0.0, formula.getDoubleResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsAllValues() {
      formula.addValue(1.0d);
      formula.addValue(2.0d);
      formula.reset();
      assertTrue(formula.isNull());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      formula.addValue(1.0d);
      formula.addValue(9.0d);
      formula.reset();
      formula.addValue(2.0d);
      formula.addValue(4.0d);
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // clone()
   // -----------------------------------------------------------------------

   @Test
   void clone_copiesAccumulatedValues() {
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      VarianceFormula cloned = (VarianceFormula) formula.clone();
      assertEquals(2.0, (Double) cloned.getResult(), 1e-10);
   }

   @Test
   void clone_isIndependentOfOriginal() {
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      VarianceFormula cloned = (VarianceFormula) formula.clone();
      formula.reset();
      assertEquals(2.0, (Double) cloned.getResult(), 1e-10);
   }

   @Test
   void clone_preservesDefaultResult() {
      formula.setDefaultResult(true);
      VarianceFormula cloned = (VarianceFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   // -----------------------------------------------------------------------
   // getOriginalResult() and percentage
   // -----------------------------------------------------------------------

   @Test
   void getOriginalResult_noPercentage_matchesGetResult() {
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      assertEquals(formula.getResult(), formula.getOriginalResult());
   }

   @Test
   void setPercentageType_getPercentageType_roundTrip() {
      formula.setPercentageType(1);
      assertEquals(1, formula.getPercentageType());
      formula.setPercentageType(0);
      assertEquals(0, formula.getPercentageType());
   }

   // -----------------------------------------------------------------------
   // defaultResult / setDefaultResult
   // -----------------------------------------------------------------------

   @Test
   void setDefaultResult_trueAndFalse_roundTrip() {
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
      formula.setDefaultResult(false);
      assertFalse(formula.isDefaultResult());
   }
}
