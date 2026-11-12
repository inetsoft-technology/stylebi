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

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProductFormula.
 */
@Tag("core")
public class ProductFormulaTest {

   private static final double DELTA = 1e-9;

   private ProductFormula formula;

   @BeforeEach
   void setUp() {
      formula = new ProductFormula();
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
      formula.addValue(2.0);
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
      assertEquals(Double.valueOf(0), formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(Object)
   // -----------------------------------------------------------------------

   @Test
   void addValue_singleInteger_returnsProduct() {
      formula.addValue(Integer.valueOf(5));
      assertEquals(Double.valueOf(5.0), formula.getResult());
   }

   @Test
   void addValue_multipleIntegers_returnsCorrectProduct() {
      formula.addValue(Integer.valueOf(2));
      formula.addValue(Integer.valueOf(3));
      formula.addValue(Integer.valueOf(4));
      assertEquals(Double.valueOf(24.0), formula.getResult());
   }

   @Test
   void addValue_nullObject_skipped() {
      formula.addValue(Integer.valueOf(6));
      formula.addValue((Object) null);
      assertEquals(Double.valueOf(6.0), formula.getResult());
   }

   @Test
   void addValue_numericString_parsedAndMultiplied() {
      formula.addValue("5");
      formula.addValue("4");
      assertEquals(Double.valueOf(20.0), formula.getResult());
   }

   @Test
   void addValue_nonNumericString_ignoredButCountIncremented() {
      // cnt increments before the multiplication; a NumberFormatException is
      // swallowed; the product remains 1 but cnt is 1
      formula.addValue("abc");
      // cnt=1 so not null, product=1
      assertEquals(Double.valueOf(1.0), formula.getResult());
   }

   @Test
   void addValue_includingZero_productIsZero() {
      formula.addValue(Integer.valueOf(5));
      formula.addValue(Integer.valueOf(0));
      formula.addValue(Integer.valueOf(3));
      assertEquals(Double.valueOf(0.0), formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double)
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_singleValue_returnsItself() {
      formula.addValue(7.0);
      assertEquals(Double.valueOf(7.0), formula.getResult());
   }

   @Test
   void addValueDouble_multipleValues_returnsProduct() {
      formula.addValue(2.0);
      formula.addValue(5.0);
      assertEquals(Double.valueOf(10.0), formula.getResult());
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
   void addValueDoubleArray_singleElement_multiplied() {
      formula.addValue(new double[]{4.0});
      assertEquals(Double.valueOf(4.0), formula.getResult());
   }

   @Test
   void addValueDoubleArray_nullSentinel_skipped() {
      formula.addValue(new double[]{Tool.NULL_DOUBLE});
      assertTrue(formula.isNull());
   }

   @Test
   void addValueDoubleArray_emptyArray_skipped() {
      formula.addValue(new double[]{});
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_multiplied() {
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
   void addValueLong_multiplied() {
      formula.addValue(2L);
      formula.addValue(10L);
      assertEquals(Double.valueOf(20.0), formula.getResult());
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
   void addValueInt_multiplied() {
      formula.addValue(3);
      formula.addValue(7);
      assertEquals(Double.valueOf(21.0), formula.getResult());
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
   void addValueShort_multiplied() {
      formula.addValue((short) 4);
      formula.addValue((short) 5);
      assertEquals(Double.valueOf(20.0), formula.getResult());
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
   void getDoubleResult_afterAddValues_returnsProduct() {
      formula.addValue(3.0);
      formula.addValue(4.0);
      assertEquals(12.0, formula.getDoubleResult());
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsProduct() {
      formula.addValue(5.0);
      formula.addValue(6.0);
      formula.reset();
      assertTrue(formula.isNull());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      formula.addValue(100.0);
      formula.reset();
      formula.addValue(2.0);
      formula.addValue(3.0);
      assertEquals(Double.valueOf(6.0), formula.getResult());
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
      ProductFormula cloned = (ProductFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   @Test
   void clone_clonedInstanceIsIndependent() {
      formula.addValue(4.0);
      ProductFormula cloned = (ProductFormula) formula.clone();
      cloned.reset();
      assertEquals(Double.valueOf(4.0), formula.getResult());
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
   // blank strings are skipped (Bug #80584)
   // -----------------------------------------------------------------------

   @Test
   void addValue_blankAndWhitespaceStrings_areSkipped() {
      formula.addValue("2");
      formula.addValue("");
      formula.addValue("  ");
      formula.addValue((Object) null);
      formula.addValue("abc");
      formula.addValue("3");
      assertEquals(6.0, formula.getDoubleResult(), 0.0001);
   }

   // -----------------------------------------------------------------------
   // mixed inputs, ported from the 13.8 summary formula tests
   // -----------------------------------------------------------------------

   // gap:report.summary-formulas.arithmetic#product-multiplies-values
   @Test
   void getResult_mixedSignsAndFractions_returnsProduct() {
      addAll(formula, 20, 1, -10, 20, 0.01);
      assertEquals(-40.0, asDouble(formula.getResult()), DELTA);
      assertEquals(-40.0, formula.getDoubleResult(), DELTA);
      assertFalse(formula.isNull());

      // two negative values give a positive product
      formula.reset();
      addAll(formula, -3, -4, 0.5);
      assertEquals(6.0, formula.getResult());
   }

   // gap:report.summary-formulas.arithmetic#product-multiplies-values
   @Test
   void addValue_allPrimitiveOverloadsWithNullSentinels_returnsProduct() {
      // the primitive overloads and the double array, and their null markers
      formula.addValue(2.0);
      formula.addValue(3.0f);
      formula.addValue(4L);
      formula.addValue((short) 5);
      formula.addValue(6);
      formula.addValue(new double[] { 7.0, 100.0 });
      formula.addValue(Tool.NULL_DOUBLE);
      formula.addValue(Tool.NULL_INTEGER);
      assertEquals(5040.0, formula.getResult());

      // the product of a clone does not change the original
      Formula copy = (Formula) formula.clone();
      copy.addValue(2);
      assertEquals(10080.0, copy.getResult());
      assertEquals(5040.0, formula.getResult());
   }

   // gap:report.summary-formulas.arithmetic#percentage-negative-total
   @Test
   void getResult_negativePercentageTotal_returnsNegativeShare() {
      addAll(formula, 3, 4);
      asPercentage(formula, -48);
      assertEquals(-0.25, asDouble(formula.getResult()), DELTA);
      assertEquals(-0.25, formula.getDoubleResult(), DELTA);
      assertEquals(12.0, formula.getOriginalResult());

      // the percentage type is kept after the original result is read
      assertEquals(StyleConstants.PERCENTAGE_OF_GROUP, formula.getPercentageType());
      assertEquals(-0.25, asDouble(formula.getResult()), DELTA);
   }

   // gap:report.summary-formulas.arithmetic#percentage-total-zero-is-null
   @Test
   void getResult_percentageTotalZero_returnsNull() {
      addAll(formula, 0, 1, 10, null);
      asPercentage(formula, 0);
      assertNull(formula.getResult());
      assertEquals(0.0, formula.getDoubleResult());
      assertEquals(0.0, asDouble(formula.getOriginalResult()), DELTA);

      // a total of 0.0 is 0 as well, and the default-result flag gives 0 instead of null
      formula.setTotal(0.0);
      assertNull(formula.getResult());
      formula.setDefaultResult(true);
      assertEquals(0.0, formula.getResult());
      formula.setDefaultResult(false);

      // a different total is a real percentage again
      formula.setTotal(10);
      assertNotNull(formula.getResult());
   }

   // gap:report.summary-formulas.arithmetic#product-skips-text-and-null
   @Test
   void addValue_textAndNullBetweenNumbers_skipped() {
      addAll(formula, "1", "2", "3", "4", "5.5", "a", null);
      assertEquals(132.0, formula.getResult());
      assertEquals(132.0, formula.getDoubleResult());
      assertFalse(formula.isNull());

      // the text and the null do not change the product
      formula.reset();
      addAll(formula, 2, "abc", null, "x", 3);
      assertEquals(6.0, formula.getResult());

      // the text between numbers, with a negative number
      formula.reset();
      addAll(formula, "-2", "text", 5);
      assertEquals(-10.0, formula.getResult());

      // a percentage of the group keeps skipping them
      formula.reset();
      addAll(formula, 30, 1, -10, 20, 7.2, 0.01, null);
      asPercentage(formula, 100);
      assertEquals(-4.32, asDouble(formula.getResult()), DELTA);
      assertEquals(-432.0, asDouble(formula.getOriginalResult()), DELTA);
   }

   // Product of text and dates only is 1.0 (Bug #80541 rejected, kept as is)
   @Test
   void addValue_textOrDatesOnly_returnsOne() {
      // ProductFormula.addValue counts a value before it is parsed, so only text or dates give
      // 1.0 and isNull is false, where Sum gives null. 13.8 Bug #80541 asked for null and was
      // rejected; 13.8 keeps that expectation @Disabled, StyleBI pins the behavior it keeps
      addAll(formula, "abc", "x1");
      assertFalse(formula.isNull());
      assertEquals(1.0, formula.getResult());

      formula.reset();
      addAll(formula, new Date(1_000_000L), new Date(2_000_000L), null);
      assertFalse(formula.isNull());
      assertEquals(1.0, formula.getResult());
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

   // Test Case #80957: a product beyond the double range is Infinity with its sign, and a
   // NaN value makes it NaN. Current behavior
   @Test
   void addValue_beyondDoubleRangeOrNaN_givesInfinityOrNaN() {
      formula.addValue(1e200);
      formula.addValue(1e200);
      assertEquals(Double.POSITIVE_INFINITY, asDouble(formula.getResult()));

      formula.reset();
      formula.addValue(-1e200);
      formula.addValue(1e200);
      assertEquals(Double.NEGATIVE_INFINITY, asDouble(formula.getResult()));

      formula.reset();
      formula.addValue(2.0);
      formula.addValue(Double.NaN);
      assertTrue(Double.isNaN(asDouble(formula.getResult())));
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
