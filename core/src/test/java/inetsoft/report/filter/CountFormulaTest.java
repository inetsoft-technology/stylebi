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

import inetsoft.uql.XConstants;
import inetsoft.util.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.sql.Timestamp;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CountFormula.
 *
 * <p>CountFormula counts non-null values. isNull() always returns false.
 * getResult() returns the integer count.
 */
@Tag("core")
public class CountFormulaTest {

   private CountFormula formula;

   @BeforeEach
   void setUp() {
      formula = new CountFormula();
   }

   // -----------------------------------------------------------------------
   // isNull() — CountFormula always returns false
   // -----------------------------------------------------------------------

   @Test
   void isNull_noValuesAdded_alwaysFalse() {
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_afterAddingValues_alwaysFalse() {
      formula.addValue("x");
      assertFalse(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — empty case
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_returnsZero() {
      assertEquals(0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(Object)
   // -----------------------------------------------------------------------

   @Test
   void addValue_singleNonNullObject_countIsOne() {
      formula.addValue("hello");
      assertEquals(1, formula.getResult());
   }

   @Test
   void addValue_multipleObjects_countIsCorrect() {
      formula.addValue("a");
      formula.addValue("b");
      formula.addValue("c");
      assertEquals(3, formula.getResult());
   }

   @Test
   void addValue_nullObject_notCounted() {
      formula.addValue((Object) null);
      assertEquals(0, formula.getResult());
   }

   @Test
   void addValue_mixedNullAndNonNull_onlyNonNullCounted() {
      formula.addValue("a");
      formula.addValue((Object) null);
      formula.addValue("b");
      formula.addValue((Object) null);
      assertEquals(2, formula.getResult());
   }

   @Test
   void addValue_numericObject_counted() {
      formula.addValue(Integer.valueOf(42));
      assertEquals(1, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double)
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_normalValue_counted() {
      formula.addValue(3.14);
      assertEquals(1, formula.getResult());
   }

   @Test
   void addValueDouble_nullSentinel_notCounted() {
      formula.addValue(Tool.NULL_DOUBLE);
      assertEquals(0, formula.getResult());
   }

   @Test
   void addValueDouble_multipleValues_countIsCorrect() {
      formula.addValue(1.0);
      formula.addValue(2.0);
      formula.addValue(3.0);
      assertEquals(3, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double[])
   // -----------------------------------------------------------------------

   @Test
   void addValueDoubleArray_normalValue_counted() {
      formula.addValue(new double[]{5.0});
      assertEquals(1, formula.getResult());
   }

   @Test
   void addValueDoubleArray_nullSentinel_notCounted() {
      formula.addValue(new double[]{Tool.NULL_DOUBLE});
      assertEquals(0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_normalValue_counted() {
      formula.addValue(2.5f);
      assertEquals(1, formula.getResult());
   }

   @Test
   void addValueFloat_nullSentinel_notCounted() {
      formula.addValue(Tool.NULL_FLOAT);
      assertEquals(0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_normalValue_counted() {
      formula.addValue(100L);
      assertEquals(1, formula.getResult());
   }

   @Test
   void addValueLong_nullSentinel_notCounted() {
      formula.addValue(Tool.NULL_LONG);
      assertEquals(0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_normalValue_counted() {
      formula.addValue(7);
      assertEquals(1, formula.getResult());
   }

   @Test
   void addValueInt_nullSentinel_notCounted() {
      formula.addValue(Tool.NULL_INTEGER);
      assertEquals(0, formula.getResult());
   }

   @Test
   void addValueInt_multipleValues_countIsCorrect() {
      formula.addValue(1);
      formula.addValue(2);
      formula.addValue(3);
      assertEquals(3, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_normalValue_counted() {
      formula.addValue((short) 5);
      assertEquals(1, formula.getResult());
   }

   @Test
   void addValueShort_nullSentinel_notCounted() {
      formula.addValue(Tool.NULL_SHORT);
      assertEquals(0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // getDoubleResult()
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_noValues_returnsZero() {
      assertEquals(0.0, formula.getDoubleResult());
   }

   @Test
   void getDoubleResult_afterAddingValues_returnsCount() {
      formula.addValue("a");
      formula.addValue("b");
      assertEquals(2.0, formula.getDoubleResult());
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsCount() {
      formula.addValue("x");
      formula.addValue("y");
      formula.reset();
      assertEquals(0, formula.getResult());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      formula.addValue("first");
      formula.reset();
      formula.addValue("second");
      formula.addValue("third");
      assertEquals(2, formula.getResult());
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
      CountFormula cloned = (CountFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   @Test
   void clone_clonedInstanceIsIndependent() {
      formula.addValue("a");
      CountFormula cloned = (CountFormula) formula.clone();
      cloned.reset();
      assertEquals(1, formula.getResult());
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
   void getResultType_returnsIntegerClass() {
      assertEquals(Integer.class, formula.getResultType());
   }

   // -----------------------------------------------------------------------
   // dates, blank strings, the default result, clone and reset
   // -----------------------------------------------------------------------

   // gap:report.summary-formulas.count#count-non-null-values
   @Test
   void addValue_datesAndTimestamps_nullNotCounted() {
      long base = 1_700_000_000_000L;
      Date d1 = new Date(base);
      Date d2 = new Date(base + 86_400_000L);
      addAll(formula, d1, d1, d2, null);
      assertEquals(3, formula.getResult());

      formula.reset();
      addAll(formula, new Timestamp(base), null, null);
      assertEquals(1, formula.getResult());
   }

   // gap:report.summary-formulas.count#count-empty-string-counts
   @Test
   void addValue_emptyAndBlankStrings_counted() {
      addAll(formula, "", " ", null);
      assertEquals(2, formula.getResult());

      formula.reset();
      addAll(formula, "", "", "");
      assertEquals(3, formula.getResult());

      formula.reset();
      addAll(formula, null, null);
      assertEquals(0, formula.getResult());
   }

   // gap:report.summary-formulas.count#count-no-values-is-zero
   @Test
   void getResult_defaultResultWithoutPercentage_staysZero() {
      // the default result makes no difference without a percentage
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
      assertEquals(0, formula.getResult());
   }

   // gap:report.summary-formulas.common#formula-reused-after-reset
   @Test
   void cloneAndReset_copyAndOriginalStayIndependent() {
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

   // -----------------------------------------------------------------------
   // percentage of total
   // -----------------------------------------------------------------------

   // gap:report.summary-formulas.count#count-percentage-of-total
   @Test
   void getResult_percentageOfTotal_returnsRatio() {
      CountFormula count = percentage(new CountFormula(), 100);
      addAll(count, "this", "is", "a", "test!");
      assertEquals(0.04, count.getResult());
      assertEquals(0.04, count.getDoubleResult());

      // the original result is the plain count
      assertEquals(4, count.getOriginalResult());
      assertEquals(XConstants.PERCENTAGE_OF_GRANDTOTAL, count.getPercentageType());

      // a negative total gives a negative ratio
      CountFormula negative = percentage(new CountFormula(), -100);
      addAll(negative, 1, 3, 3, 3, 4, 4, "");
      assertEquals(-0.07, negative.getResult());
      assertEquals(7, negative.getOriginalResult());

      // the group percentage is a percentage too
      CountFormula group = new CountFormula();
      group.setPercentageType(XConstants.PERCENTAGE_OF_GROUP);
      group.setTotal(8);
      addAll(group, 1, 2);
      assertEquals(0.25, group.getResult());
   }

   // gap:report.summary-formulas.count#count-percentage-needs-integer-total
   @Test
   void getResult_percentageWithNonIntegerTotal_returnsPlainCount() {
      // Count still needs an Integer total (unlike Distinct Count since Bug #80952): any
      // total that is not an Integer leaves the plain count
      Object[] totals = { 100L, 100.0, 100.0f, (short) 100, "100" };

      for(Object total : totals) {
         String type = total.getClass().getSimpleName();
         CountFormula count = percentage(new CountFormula(), total);
         addAll(count, "a", "b", "b", "c");
         assertEquals(4, count.getResult(), "count, total " + type);
         assertEquals(4.0, count.getDoubleResult(), "count, total " + type);
      }

      // no total at all
      CountFormula noTotal = percentage(new CountFormula(), null);
      addAll(noTotal, "a", "b");
      assertEquals(2, noTotal.getResult());

      // an Integer total of 0: null, or 0 with the default result
      CountFormula zero = percentage(new CountFormula(), 0);
      addAll(zero, "a", "b");
      assertNull(zero.getResult());
      assertEquals(0.0, zero.getDoubleResult());
      zero.setDefaultResult(true);
      assertEquals(0.0, zero.getResult());

      // an Integer total gives the ratio
      CountFormula integer = percentage(new CountFormula(), 8);
      addAll(integer, "a", "b");
      assertEquals(0.25, integer.getResult());
   }

   private static void addAll(Formula formula, Object... values) {
      for(Object value : values) {
         formula.addValue(value);
      }
   }

   private static <T extends PercentageFormula> T percentage(T formula, Object total) {
      formula.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      formula.setTotal(total);
      return formula;
   }
}
