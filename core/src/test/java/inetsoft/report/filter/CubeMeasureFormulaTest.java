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
 * Tests for CubeMeasureFormula.
 *
 * <p>CubeMeasureFormula is a summing formula that always returns a Double.
 * isNull() always returns false. clone() copies the accumulated sum.
 */
public class CubeMeasureFormulaTest {

   private CubeMeasureFormula formula;

   @BeforeEach
   void setUp() {
      formula = new CubeMeasureFormula();
   }

   // -----------------------------------------------------------------------
   // isNull()
   // -----------------------------------------------------------------------

   @Test
   void isNull_alwaysReturnsFalse_beforeAnyValues() {
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_alwaysReturnsFalse_afterAddingValues() {
      formula.addValue(5.0);
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_alwaysReturnsFalse_afterReset() {
      formula.addValue(5.0);
      formula.reset();
      assertFalse(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — initial state
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_returnsZero() {
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResultType_returnsDoubleClass() {
      assertEquals(Double.class, formula.getResultType());
   }

   // -----------------------------------------------------------------------
   // addValue(Object)
   // -----------------------------------------------------------------------

   @Test
   void addValue_singleNumber_returnsCorrectSum() {
      formula.addValue(Integer.valueOf(10));
      assertEquals(10.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValue_multipleNumbers_returnsSum() {
      formula.addValue(Integer.valueOf(3));
      formula.addValue(Double.valueOf(4.5));
      formula.addValue(Long.valueOf(2L));
      assertEquals(9.5, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValue_nullObject_isIgnored() {
      formula.addValue((Object) null);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValue_numericString_isParsedAndAdded() {
      formula.addValue("7.5");
      assertEquals(7.5, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValue_nonNumericString_isIgnored() {
      formula.addValue("hello");
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(double)
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_normalValue_addsToSum() {
      formula.addValue(3.14d);
      assertEquals(3.14, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueDouble_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_DOUBLE);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueDouble_multipleValues_sumsCorrectly() {
      formula.addValue(1.0d);
      formula.addValue(2.0d);
      formula.addValue(3.0d);
      assertEquals(6.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueDoubleArray_normalValue_addsFirstElement() {
      formula.addValue(new double[]{5.0, 99.0});
      assertEquals(5.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueDoubleArray_nullSentinelInFirstPosition_isIgnored() {
      formula.addValue(new double[]{Tool.NULL_DOUBLE, 99.0});
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_normalValue_addsToSum() {
      formula.addValue(2.5f);
      assertEquals(2.5, (Double) formula.getResult(), 1e-6);
   }

   @Test
   void addValueFloat_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_FLOAT);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_normalValue_addsToSum() {
      formula.addValue(100L);
      assertEquals(100.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueLong_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_LONG);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_normalValue_addsToSum() {
      formula.addValue(7);
      assertEquals(7.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueInt_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_INTEGER);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueInt_multipleValues_sumsCorrectly() {
      formula.addValue(10);
      formula.addValue(20);
      formula.addValue(30);
      assertEquals(60.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_normalValue_addsToSum() {
      formula.addValue((short) 5);
      assertEquals(5.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueShort_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_SHORT);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // getDoubleResult()
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_noValues_returnsZero() {
      assertEquals(0.0, formula.getDoubleResult(), 1e-10);
   }

   @Test
   void getDoubleResult_afterAddingValues_matchesGetResult() {
      formula.addValue(4.0d);
      formula.addValue(6.0d);
      assertEquals(10.0, formula.getDoubleResult(), 1e-10);
      assertEquals((Double) formula.getResult(), formula.getDoubleResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsAccumulatedSum() {
      formula.addValue(50.0d);
      formula.reset();
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void reset_allowsReuseForNewValues() {
      formula.addValue(50.0d);
      formula.reset();
      formula.addValue(7.0d);
      assertEquals(7.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // clone()
   // -----------------------------------------------------------------------

   @Test
   void clone_copiesCurrentSum() {
      formula.addValue(10.0d);
      formula.addValue(5.0d);
      CubeMeasureFormula cloned = (CubeMeasureFormula) formula.clone();
      assertEquals(15.0, (Double) cloned.getResult(), 1e-10);
   }

   @Test
   void clone_isIndependentOfOriginal() {
      formula.addValue(10.0d);
      CubeMeasureFormula cloned = (CubeMeasureFormula) formula.clone();
      formula.addValue(5.0d);
      // cloned should not be affected by subsequent additions to original
      assertEquals(10.0, (Double) cloned.getResult(), 1e-10);
      assertEquals(15.0, (Double) formula.getResult(), 1e-10);
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
