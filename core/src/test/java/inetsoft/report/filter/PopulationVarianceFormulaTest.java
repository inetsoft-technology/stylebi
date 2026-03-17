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
 * Tests for PopulationVarianceFormula (divides by N, not N-1).
 *
 * <p>Known values:
 * <ul>
 *   <li>{2, 4, 4, 4, 5, 5, 7, 9}: mean=5, sum sq dev=32, population var = 32/8 = 4.0</li>
 *   <li>{1, 2, 3, 4, 5}: mean=3, sum sq dev=10, population var = 10/5 = 2.0</li>
 * </ul>
 */
public class PopulationVarianceFormulaTest {

   private PopulationVarianceFormula formula;

   @BeforeEach
   void setUp() {
      formula = new PopulationVarianceFormula();
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
      formula.addValue(5.0d);
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_afterReset_returnsTrue() {
      formula.addValue(5.0d);
      formula.reset();
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — empty / single value
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_returnsNull() {
      assertNull(formula.getResult());
   }

   @Test
   void getResult_singleValue_returnsZero() {
      // population variance of a single value is 0 (count==1, getRawResult returns 0)
      formula.addValue(5.0d);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // Known mathematical values — population variance
   // -----------------------------------------------------------------------

   @Test
   void getResult_twoIdenticalValues_returnsZero() {
      formula.addValue(3.0d);
      formula.addValue(3.0d);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_twoValues_correctPopulationVariance() {
      // {1, 3}: mean=2, deviations=1,1, sum sq dev=2, pop var = 2/2 = 1
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      assertEquals(1.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_classicExample_correctPopulationVariance() {
      // {2, 4, 4, 4, 5, 5, 7, 9}: mean=5, sum sq dev=32, pop var = 32/8 = 4.0
      double[] values = {2, 4, 4, 4, 5, 5, 7, 9};
      for(double v : values) {
         formula.addValue(v);
      }
      assertEquals(4.0, (Double) formula.getResult(), 1e-6);
   }

   @Test
   void getResult_oneToFive_correctPopulationVariance() {
      // {1, 2, 3, 4, 5}: mean=3, sum sq dev=10, pop var = 10/5 = 2.0
      formula.addValue(1.0d);
      formula.addValue(2.0d);
      formula.addValue(3.0d);
      formula.addValue(4.0d);
      formula.addValue(5.0d);
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // Population variance is smaller than sample variance
   // -----------------------------------------------------------------------

   @Test
   void getResult_populationVarianceIsSmallerThanSampleVarianceForSameData() {
      double[] values = {2, 4, 4, 4, 5, 5, 7, 9};
      VarianceFormula sampleFormula = new VarianceFormula();
      for(double v : values) {
         formula.addValue(v);
         sampleFormula.addValue(v);
      }
      double popVar = (Double) formula.getResult();
      double sampVar = (Double) sampleFormula.getResult();
      assertTrue(popVar < sampVar, "Population variance should be less than sample variance");
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
      formula.addValue(Integer.valueOf(1));
      formula.addValue(Integer.valueOf(3));
      assertEquals(1.0, (Double) formula.getResult(), 1e-10);
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
   void addValueDoubleArray_nullSentinelInFirstPosition_isIgnored() {
      formula.addValue(new double[]{Tool.NULL_DOUBLE, 5.0});
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_INTEGER);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueInt_normalValues_computesPopulationVariance() {
      formula.addValue(1);
      formula.addValue(3);
      assertEquals(1.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_FLOAT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_LONG);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

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
      assertEquals(1.0, formula.getDoubleResult(), 1e-10);
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
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      assertEquals(1.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // clone()
   // -----------------------------------------------------------------------

   @Test
   void clone_returnsPopulationVarianceFormulaInstance() {
      Object cloned = formula.clone();
      assertInstanceOf(PopulationVarianceFormula.class, cloned);
   }

   @Test
   void clone_copiesAccumulatedValues() {
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      PopulationVarianceFormula cloned = (PopulationVarianceFormula) formula.clone();
      assertEquals(1.0, (Double) cloned.getResult(), 1e-10);
   }

   @Test
   void clone_isIndependentOfOriginal() {
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      PopulationVarianceFormula cloned = (PopulationVarianceFormula) formula.clone();
      formula.reset();
      assertEquals(1.0, (Double) cloned.getResult(), 1e-10);
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
