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
 * Tests for AverageFormula.
 */
public class AverageFormulaTest {

   private AverageFormula formula;

   @BeforeEach
   void setUp() {
      formula = new AverageFormula();
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
      formula.addValue(10.0);
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_afterAddNullObject_remainsTrue() {
      formula.addValue((Object) null);
      assertTrue(formula.isNull());
   }

   @Test
   void isNull_afterAddNullDouble_remainsTrue() {
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — empty cases
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_defaultResultFalse_returnsNull() {
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
   }

   @Test
   void getResult_noValues_defaultResultTrue_returnsZero() {
      formula.setDefaultResult(true);
      assertEquals(0.0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(Object) — numeric objects
   // -----------------------------------------------------------------------

   @Test
   void addValue_singleIntegerObject_returnsDoubleAverage() {
      formula.addValue(Integer.valueOf(10));
      assertEquals(10.0, formula.getResult());
   }

   @Test
   void addValue_multipleIntegers_returnsCorrectAverage() {
      formula.addValue(Integer.valueOf(10));
      formula.addValue(Integer.valueOf(20));
      formula.addValue(Integer.valueOf(30));
      assertEquals(20.0, formula.getResult());
   }

   @Test
   void addValue_nullObject_skipped_doesNotAffectAverage() {
      formula.addValue(Integer.valueOf(10));
      formula.addValue((Object) null);
      formula.addValue(Integer.valueOf(20));
      assertEquals(15.0, formula.getResult());
   }

   @Test
   void addValue_numericString_parsedAndIncluded() {
      formula.addValue("15");
      assertEquals(15.0, formula.getResult());
   }

   @Test
   void addValue_nonNumericString_ignored() {
      formula.addValue("abc");
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(double)
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_singleValue_returnsItself() {
      formula.addValue(5.0);
      assertEquals(5.0, formula.getResult());
   }

   @Test
   void addValueDouble_multipleValues_returnsCorrectAverage() {
      formula.addValue(4.0);
      formula.addValue(8.0);
      assertEquals(6.0, formula.getResult());
   }

   @Test
   void addValueDouble_nullSentinel_skipped() {
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueDouble_nullMixedWithReal_onlyRealCounted() {
      formula.addValue(Tool.NULL_DOUBLE);
      formula.addValue(10.0);
      formula.addValue(Tool.NULL_DOUBLE);
      assertEquals(10.0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double[])
   // -----------------------------------------------------------------------

   @Test
   void addValueDoubleArray_singleElement_usesFirstElement() {
      formula.addValue(new double[]{6.0});
      assertEquals(6.0, formula.getResult());
   }

   @Test
   void addValueDoubleArray_nullSentinel_skipped() {
      formula.addValue(new double[]{Tool.NULL_DOUBLE});
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_singleValue_returnsCorrectAverage() {
      formula.addValue(3.0f);
      assertEquals(3.0, (Double) formula.getResult(), 1e-6);
   }

   @Test
   void addValueFloat_nullSentinel_skipped() {
      formula.addValue(Tool.NULL_FLOAT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_singleValue_returnsCorrectAverage() {
      formula.addValue(100L);
      assertEquals(100.0, formula.getResult());
   }

   @Test
   void addValueLong_nullSentinel_skipped() {
      formula.addValue(Tool.NULL_LONG);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_singleValue_returnsCorrectAverage() {
      formula.addValue(7);
      assertEquals(7.0, formula.getResult());
   }

   @Test
   void addValueInt_nullSentinel_skipped() {
      formula.addValue(Tool.NULL_INTEGER);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueInt_multipleValues_returnsCorrectAverage() {
      formula.addValue(2);
      formula.addValue(4);
      formula.addValue(6);
      assertEquals(4.0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_singleValue_returnsCorrectAverage() {
      formula.addValue((short) 20);
      assertEquals(20.0, formula.getResult());
   }

   @Test
   void addValueShort_nullSentinel_skipped() {
      formula.addValue(Tool.NULL_SHORT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getDoubleResult()
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_noValues_returnsZero() {
      assertEquals(0.0, formula.getDoubleResult());
   }

   @Test
   void getDoubleResult_afterAddValue_returnsAverage() {
      formula.addValue(10.0);
      formula.addValue(20.0);
      assertEquals(15.0, formula.getDoubleResult());
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsAccumulatedValues() {
      formula.addValue(10.0);
      formula.addValue(20.0);
      formula.reset();
      assertTrue(formula.isNull());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      formula.addValue(100.0);
      formula.reset();
      formula.addValue(5.0);
      assertEquals(5.0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // clone()
   // -----------------------------------------------------------------------

   @Test
   void clone_returnsNonNullInstance() {
      assertNotNull(formula.clone());
   }

   @Test
   void clone_preservesDefaultResult() {
      formula.setDefaultResult(true);
      AverageFormula cloned = (AverageFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   @Test
   void clone_clonedInstanceIsIndependent() {
      formula.addValue(10.0);
      AverageFormula cloned = (AverageFormula) formula.clone();
      // clone shares the accumulated state (super.clone())
      cloned.reset();
      // original should be unaffected
      assertEquals(10.0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // isDefaultResult / setDefaultResult
   // -----------------------------------------------------------------------

   @Test
   void setDefaultResult_roundTrip() {
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
      formula.setDefaultResult(false);
      assertFalse(formula.isDefaultResult());
   }

   // -----------------------------------------------------------------------
   // getResultType()
   // -----------------------------------------------------------------------

   @Test
   void getResultType_returnsDoubleClass() {
      assertEquals(Double.class, formula.getResultType());
   }
}
