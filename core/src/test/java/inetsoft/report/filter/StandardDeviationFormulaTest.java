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
 * Tests for StandardDeviationFormula (sample standard deviation, sqrt of sample variance).
 *
 * <p>Known values:
 * <ul>
 *   <li>{2, 4, 4, 4, 5, 5, 7, 9}: sample variance=32/7, stddev=sqrt(32/7)≈2.1381</li>
 *   <li>{1, 2, 3, 4, 5}: sample variance=2.5, stddev=sqrt(2.5)≈1.5811</li>
 *   <li>{1, 3}: sample variance=2, stddev=sqrt(2)≈1.4142</li>
 * </ul>
 */
public class StandardDeviationFormulaTest {

   private StandardDeviationFormula formula;

   @BeforeEach
   void setUp() {
      formula = new StandardDeviationFormula();
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
      // stddev of one value: variance=0, sqrt(0)=0
      formula.addValue(5.0d);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // Known mathematical values — sample standard deviation
   // -----------------------------------------------------------------------

   @Test
   void getResult_twoIdenticalValues_returnsZero() {
      formula.addValue(4.0d);
      formula.addValue(4.0d);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_twoValues_correctSampleStdDev() {
      // {1, 3}: variance=2, stddev=sqrt(2)
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      assertEquals(Math.sqrt(2.0), (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_classicExample_correctSampleStdDev() {
      // {2, 4, 4, 4, 5, 5, 7, 9}: sample var=32/7, stddev=sqrt(32/7)
      double[] values = {2, 4, 4, 4, 5, 5, 7, 9};
      for(double v : values) {
         formula.addValue(v);
      }
      double expected = Math.sqrt(32.0 / 7.0);
      assertEquals(expected, (Double) formula.getResult(), 1e-6);
   }

   @Test
   void getResult_oneToFive_correctSampleStdDev() {
      // {1, 2, 3, 4, 5}: variance=2.5, stddev=sqrt(2.5)
      formula.addValue(1.0d);
      formula.addValue(2.0d);
      formula.addValue(3.0d);
      formula.addValue(4.0d);
      formula.addValue(5.0d);
      assertEquals(Math.sqrt(2.5), (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // stddev = sqrt(variance)
   // -----------------------------------------------------------------------

   @Test
   void getResult_equalsSquareRootOfSampleVariance() {
      double[] values = {3, 7, 1, 9, 5};
      VarianceFormula varianceFormula = new VarianceFormula();
      for(double v : values) {
         formula.addValue(v);
         varianceFormula.addValue(v);
      }
      double variance = (Double) varianceFormula.getResult();
      double stddev = (Double) formula.getResult();
      assertEquals(Math.sqrt(variance), stddev, 1e-10);
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
      assertEquals(Math.sqrt(2.0), (Double) formula.getResult(), 1e-10);
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
      formula.addValue(new double[]{1.0, 99.0});
      formula.addValue(new double[]{3.0, 99.0});
      assertEquals(Math.sqrt(2.0), (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_normalValues_computesStdDev() {
      formula.addValue(1);
      formula.addValue(3);
      assertEquals(Math.sqrt(2.0), (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueInt_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_INTEGER);
      assertTrue(formula.isNull());
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
      assertEquals(Math.sqrt(2.0), formula.getDoubleResult(), 1e-10);
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
      formula.addValue(100.0d);
      formula.addValue(200.0d);
      formula.reset();
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      assertEquals(Math.sqrt(2.0), (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // clone()
   // -----------------------------------------------------------------------

   @Test
   void clone_returnsStandardDeviationFormulaInstance() {
      Object cloned = formula.clone();
      assertInstanceOf(StandardDeviationFormula.class, cloned);
   }

   @Test
   void clone_copiesAccumulatedValues() {
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      StandardDeviationFormula cloned = (StandardDeviationFormula) formula.clone();
      assertEquals(Math.sqrt(2.0), (Double) cloned.getResult(), 1e-10);
   }

   @Test
   void clone_isIndependentOfOriginal() {
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      StandardDeviationFormula cloned = (StandardDeviationFormula) formula.clone();
      formula.reset();
      assertEquals(Math.sqrt(2.0), (Double) cloned.getResult(), 1e-10);
   }

   @Test
   void clone_preservesDefaultResult() {
      formula.setDefaultResult(true);
      StandardDeviationFormula cloned = (StandardDeviationFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
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
