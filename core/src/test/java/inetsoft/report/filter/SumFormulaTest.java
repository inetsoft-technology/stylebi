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

import inetsoft.report.StyleConstants;
import inetsoft.util.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SumFormula.
 */
@Tag("core")
public class SumFormulaTest {

   private static final double DELTA = 1e-9;

   private SumFormula formula;

   @BeforeEach
   void setUp() {
      formula = new SumFormula();
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
      formula.addValue(1.0);
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_afterAddNullObject_remainsTrue() {
      formula.addValue((Object) null);
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
   void addValue_singleInteger_returnsCorrectSum() {
      formula.addValue(Integer.valueOf(5));
      assertEquals(5.0, formula.getResult());
   }

   @Test
   void addValue_multipleIntegers_returnsCorrectSum() {
      formula.addValue(Integer.valueOf(10));
      formula.addValue(Integer.valueOf(20));
      formula.addValue(Integer.valueOf(30));
      assertEquals(60.0, formula.getResult());
   }

   @Test
   void addValue_nullObject_skipped() {
      formula.addValue(Integer.valueOf(10));
      formula.addValue((Object) null);
      formula.addValue(Integer.valueOf(5));
      assertEquals(15.0, formula.getResult());
   }

   @Test
   void addValue_numericString_parsedAndSummed() {
      formula.addValue("100");
      assertEquals(100.0, formula.getResult());
   }

   @Test
   void addValue_nonNumericString_ignored() {
      formula.addValue("abc");
      assertTrue(formula.isNull());
   }

   @Test
   void addValue_negativeNumbers_summedCorrectly() {
      formula.addValue(Integer.valueOf(-5));
      formula.addValue(Integer.valueOf(10));
      assertEquals(5.0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double)
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_singleValue_returnsItself() {
      formula.addValue(7.5);
      assertEquals(7.5, formula.getResult());
   }

   @Test
   void addValueDouble_multipleValues_returnsSum() {
      formula.addValue(1.5);
      formula.addValue(2.5);
      formula.addValue(3.0);
      assertEquals(7.0, formula.getResult());
   }

   @Test
   void addValueDouble_nullSentinel_skipped() {
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(double[])
   // -----------------------------------------------------------------------

   @Test
   void addValueDoubleArray_singleElement_summedCorrectly() {
      formula.addValue(new double[]{4.0});
      assertEquals(4.0, formula.getResult());
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
   void addValueFloat_singleValue_summedCorrectly() {
      formula.addValue(2.0f);
      assertEquals(2.0, (Double) formula.getResult(), 1e-6);
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
   void addValueLong_singleValue_summedCorrectly() {
      formula.addValue(1000L);
      assertEquals(1000.0, formula.getResult());
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
   void addValueInt_singleValue_summedCorrectly() {
      formula.addValue(42);
      assertEquals(42.0, formula.getResult());
   }

   @Test
   void addValueInt_nullSentinel_skipped() {
      formula.addValue(Tool.NULL_INTEGER);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_singleValue_summedCorrectly() {
      formula.addValue((short) 8);
      assertEquals(8.0, formula.getResult());
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
   void getDoubleResult_afterAddValues_returnsSum() {
      formula.addValue(3.0);
      formula.addValue(7.0);
      assertEquals(10.0, formula.getDoubleResult());
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsSum() {
      formula.addValue(100.0);
      formula.addValue(200.0);
      formula.reset();
      assertTrue(formula.isNull());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      formula.addValue(50.0);
      formula.reset();
      formula.addValue(10.0);
      assertEquals(10.0, formula.getResult());
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
      SumFormula cloned = (SumFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   @Test
   void clone_clonedInstanceIsIndependent() {
      formula.addValue(5.0);
      SumFormula cloned = (SumFormula) formula.clone();
      cloned.reset();
      assertEquals(5.0, formula.getResult());
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

   // -----------------------------------------------------------------------
   // mixed inputs, ported from the 13.8 summary formula tests
   // -----------------------------------------------------------------------

   // gap:report.summary-formulas.arithmetic#sum-adds-numeric-values
   @Test
   void getResult_mixedNumericInputs_returnsSum() {
      addAll(formula, 20, 20.01, -30, 10.1);
      assertEquals(20.11, asDouble(formula.getResult()), DELTA);
      assertEquals(20.11, formula.getDoubleResult(), DELTA);
      assertFalse(formula.isNull());

      // BigDecimal and Long are numbers
      formula.reset();
      addAll(formula, new BigDecimal("1.25"), 9_000_000_000L, new BigDecimal("-0.25"));
      assertEquals(9_000_000_001.0, formula.getResult());

      // numeric strings are added
      formula.reset();
      addAll(formula, "-10", "20", "0.5");
      assertEquals(10.5, formula.getResult());
   }

   // gap:report.summary-formulas.arithmetic#sum-adds-numeric-values
   @Test
   void addValue_allPrimitiveOverloadsWithNullSentinels_returnsSum() {
      // the primitive overloads and the double array, and their null markers
      formula.addValue(1.5);
      formula.addValue(2.5f);
      formula.addValue(3L);
      formula.addValue((short) 4);
      formula.addValue(5);
      formula.addValue(new double[] { 6.0, 100.0 });
      formula.addValue(Tool.NULL_DOUBLE);
      formula.addValue(Tool.NULL_FLOAT);
      formula.addValue(Tool.NULL_LONG);
      formula.addValue(Tool.NULL_SHORT);
      formula.addValue(Tool.NULL_INTEGER);
      formula.addValue(new double[] { Tool.NULL_DOUBLE });
      assertEquals(22.0, formula.getResult());

      // a clone adds on its own
      Formula copy = (Formula) formula.clone();
      copy.addValue(8);
      assertEquals(30.0, copy.getResult());
      assertEquals(22.0, formula.getResult());
   }

   // gap:report.summary-formulas.arithmetic#sum-adds-numeric-values
   @Test
   void getResult_percentageOfGroup_returnsShareOfTotal() {
      addAll(formula, 20, 1, 100000, "");
      asPercentage(formula, 100);
      assertEquals(1000.21, asDouble(formula.getResult()), DELTA);
      assertEquals(1000.21, formula.getDoubleResult(), DELTA);
      assertEquals(100021.0, formula.getOriginalResult());
   }

   // gap:report.summary-formulas.arithmetic#percentage-negative-total
   @Test
   void getResult_negativePercentageTotal_returnsNegativeShare() {
      addAll(formula, 20, 30);
      asPercentage(formula, -100);
      assertEquals(-0.5, asDouble(formula.getResult()), DELTA);
      assertEquals(-0.5, formula.getDoubleResult(), DELTA);
      assertEquals(50.0, formula.getOriginalResult());

      // a negative sum of a negative total is positive
      formula.reset();
      addAll(formula, -20, -30);
      assertEquals(0.5, asDouble(formula.getResult()), DELTA);

      // a total that is not a number is not a percentage
      formula.setTotal("100");
      assertEquals(-50.0, formula.getResult());
   }

   // gap:report.summary-formulas.arithmetic#percentage-total-zero-is-null
   @Test
   void getResult_percentageTotalZero_returnsNull() {
      addAll(formula, 2, 3);
      asPercentage(formula, 0);
      assertNull(formula.getResult());
      assertEquals(0.0, formula.getDoubleResult());
      assertEquals(5.0, asDouble(formula.getOriginalResult()), DELTA);

      // a total of 0.0 is 0 as well, and the default-result flag gives 0 instead of null
      formula.setTotal(0.0);
      assertNull(formula.getResult());
      formula.setDefaultResult(true);
      assertEquals(0.0, formula.getResult());
      formula.setDefaultResult(false);

      // a different total is a real percentage again
      formula.setTotal(10);
      assertEquals(0.5, asDouble(formula.getResult()), DELTA);
   }

   // gap:report.summary-formulas.arithmetic#sum-no-numeric-input-is-null
   @Test
   void addValue_datesAndNullsOnly_remainsNull() {
      // dates and nulls are not numbers
      addAll(formula, new Date(1_000_000L), new Date(2_000_000L), null);
      assertTrue(formula.isNull());
      assertNull(formula.getResult());

      // the default-result flag turns null into 0
      formula.setDefaultResult(true);
      assertEquals(0.0, formula.getResult());
      assertEquals(0.0, formula.getDoubleResult());
   }

   // gap:report.summary-formulas.arithmetic#sum-no-numeric-input-is-null
   @Test
   void reset_afterSum_previousTotalDoesNotLeak() {
      addAll(formula, 5, 6);
      assertEquals(11.0, formula.getResult());
      formula.reset();
      assertTrue(formula.isNull());
      assertNull(formula.getResult());
      assertEquals(0.0, formula.getDoubleResult());
      addAll(formula, 2);
      assertEquals(2.0, formula.getResult());
   }

   // gap:report.summary-formulas.arithmetic#sum-skips-text-and-null
   @Test
   void addValue_textBlankAndNull_skipped() {
      addAll(formula, null, "", "abc", "12", 3);
      assertEquals(15.0, formula.getResult());
      assertEquals(15.0, formula.getDoubleResult());
      assertFalse(formula.isNull());

      // only text is nothing
      formula.reset();
      addAll(formula, "", "abc", " ", "x1");
      assertTrue(formula.isNull());
      assertNull(formula.getResult());

      // the numeric strings are added, the text is not
      formula.reset();
      addAll(formula, "FDSFA", "HELLO", "-10", "a", "20");
      assertEquals(10.0, formula.getResult());
      asPercentage(formula, 100);
      assertEquals(0.1, asDouble(formula.getResult()), DELTA);
      assertEquals(10.0, formula.getOriginalResult());

      // text after the numbers does not remove them
      SumFormula sum = new SumFormula();
      addAll(sum, 4, "oops", "");
      assertEquals(4.0, sum.getResult());
   }

   // gap:report.summary-formulas.common#formula-reused-after-reset
   @Test
   void reset_afterClone_eachCopyKeepsItsOwnValues() {
      Formula fresh = (Formula) formula.clone();
      formula.addValue(4.0);
      formula.addValue(6.0);
      assertFalse(formula.isNull());
      Object first = formula.getResult();
      assertNotNull(first);

      // a clone is its own copy
      Formula copy = (Formula) formula.clone();
      assertEquals(first, copy.getResult());
      copy.addValue(100.0);
      copy.addValue(-50.0);
      assertEquals(first, formula.getResult());
      assertNotEquals(first, copy.getResult());

      // reset starts over, and it can take another group
      formula.reset();
      assertEquals(fresh.isNull(), formula.isNull());
      assertEquals(fresh.getResult(), formula.getResult());
      formula.addValue(4.0);
      formula.addValue(6.0);
      assertEquals(first, formula.getResult());

      // the copy was not reset by it
      assertNotEquals(first, copy.getResult());
   }

   // Test Case #80957: a NaN value is not a null (only Tool.NULL_DOUBLE is skipped), so it
   // makes the sum NaN, through the primitive and the object overload. Current behavior
   @Test
   void addValue_nanValue_sumIsNaN() {
      formula.addValue(1.0);
      formula.addValue(Double.NaN);
      formula.addValue(2.0);
      assertTrue(Double.isNaN(asDouble(formula.getResult())));

      formula.reset();
      addAll(formula, 1.0, Double.NaN, 2.0);
      assertTrue(Double.isNaN(asDouble(formula.getResult())));
   }

   // Test Case #80957: integers are summed as double, so a sum beyond the int or long range
   // does not overflow (it loses precision beyond 2^53 instead)
   @Test
   void addValue_beyondIntAndLongRange_doesNotOverflow() {
      formula.addValue(Integer.MAX_VALUE);
      formula.addValue(Integer.MAX_VALUE);
      assertEquals(4294967294.0, asDouble(formula.getResult()));

      formula.reset();
      addAll(formula, Integer.MAX_VALUE, Integer.MAX_VALUE);
      assertEquals(4294967294.0, asDouble(formula.getResult()));

      // Long.MAX_VALUE as a double is 2^63, so twice it is 2^64
      formula.reset();
      formula.addValue(Long.MAX_VALUE);
      formula.addValue(Long.MAX_VALUE);
      assertEquals(Math.pow(2, 64), asDouble(formula.getResult()));
   }

   private static void addAll(Formula formula, Object... values) {
      for(Object value : values) {
         formula.addValue(value);
      }
   }

   private static void asPercentage(PercentageFormula formula, Object total) {
      formula.setPercentageType(StyleConstants.PERCENTAGE_OF_GROUP);
      formula.setTotal(total);
   }

   private static double asDouble(Object result) {
      assertInstanceOf(Double.class, result);
      return (Double) result;
   }
}
