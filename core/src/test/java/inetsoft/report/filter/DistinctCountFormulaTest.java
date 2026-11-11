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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DistinctCountFormula.
 *
 * <p>DistinctCountFormula counts the distinct non-null values. The percentage of a numeric
 * total is covered in more detail by DistinctCountFormulaPercentageTest (Bug #80952).
 */
@Tag("core")
class DistinctCountFormulaTest {

   // -----------------------------------------------------------------------
   // distinct values
   // -----------------------------------------------------------------------

   // gap:report.summary-formulas.count#distinct-count-unique-values
   @Test
   void addValue_repeatedValues_countedOnce() {
      DistinctCountFormula distinct = new DistinctCountFormula();
      addAll(distinct, 12, 11, 23, 23, 12, -1, "", null);
      assertEquals(5, distinct.getResult());
      assertEquals(5.0, distinct.getDoubleResult());

      distinct.reset();
      addAll(distinct, "red", "green", "red", "blue", "green");
      assertEquals(3, distinct.getResult());

      long base = 1_700_000_000_000L;
      Date d1 = new Date(base);
      Date d2 = new Date(base + 86_400_000L);
      distinct.reset();
      addAll(distinct, d1, d1, d2);
      assertEquals(2, distinct.getResult());

      // the primitive forms
      distinct.reset();
      distinct.addValue(1.5);
      distinct.addValue(1.5);
      distinct.addValue(Tool.NULL_DOUBLE);
      distinct.addValue(4);
      distinct.addValue(4);
      distinct.addValue(Tool.NULL_INTEGER);
      distinct.addValue(5L);
      distinct.addValue((short) 6);
      distinct.addValue(2.5f);
      distinct.addValue(new double[] { 9.5 });
      assertEquals(6, distinct.getResult());
      assertEquals(Integer.class, distinct.getResultType());
   }

   // gap:report.summary-formulas.count#distinct-count-case-sensitive-and-blank
   @Test
   void addValue_caseAndBlankStrings_countedAsDistinct() {
      DistinctCountFormula distinct = new DistinctCountFormula();
      addAll(distinct, "a", "A");
      assertEquals(2, distinct.getResult());

      // an empty string counts once, null is ignored
      distinct.reset();
      addAll(distinct, "", null);
      assertEquals(1, distinct.getResult());

      distinct.reset();
      addAll(distinct, "", "", null, null);
      assertEquals(1, distinct.getResult());

      distinct.reset();
      addAll(distinct, "a", "a", "c", "C", "", "d", "");
      assertEquals(5, distinct.getResult());

      // blank text is not the empty string
      distinct.reset();
      addAll(distinct, "", " ");
      assertEquals(2, distinct.getResult());
   }

   // -----------------------------------------------------------------------
   // no values and reset
   // -----------------------------------------------------------------------

   // gap:report.summary-formulas.count#count-no-values-is-zero
   // Pending #80953 question 2: an empty Distinct Count is 0 and never null, unlike the other
   // formulas. The developer recommends keeping 0 (SQL COUNT(DISTINCT) over no rows); not
   // decided yet, so this asserts the current behavior
   @Test
   void getResult_noValues_returnsZero() {
      DistinctCountFormula distinct = new DistinctCountFormula();
      assertEquals(0, distinct.getResult());
      assertEquals(0.0, distinct.getDoubleResult());
      assertFalse(distinct.isNull());

      // only nulls
      addAll(distinct, null, null);
      assertEquals(0, distinct.getResult());
      assertFalse(distinct.isNull());

      // after a reset
      addAll(distinct, 1, 2);
      distinct.reset();
      assertEquals(0, distinct.getResult());
      assertFalse(distinct.isNull());

      // the default result makes no difference without a percentage
      distinct.setDefaultResult(true);
      assertEquals(0, distinct.getResult());
   }

   // gap:report.summary-formulas.count#distinct-count-reset
   @Test
   void reset_forgetsTheValuesSeenBefore() {
      DistinctCountFormula distinct = new DistinctCountFormula();
      addAll(distinct, "a", "b", "c");
      assertEquals(3, distinct.getResult());
      distinct.reset();
      assertEquals(0, distinct.getResult());
      addAll(distinct, "z");
      assertEquals(1, distinct.getResult());

      // a value seen before the reset counts again
      distinct.reset();
      addAll(distinct, "a", "b");
      assertEquals(2, distinct.getResult());
   }

   // -----------------------------------------------------------------------
   // percentage of total
   // -----------------------------------------------------------------------

   // gap:report.summary-formulas.count#distinct-count-percentage-of-total
   @Test
   void getResult_percentageOfTotal_returnsRatio() {
      DistinctCountFormula distinct = percentage(new DistinctCountFormula(), 100);
      addAll(distinct, "a", "a", "c", "C", "", "d", "");
      assertEquals(0.05, distinct.getResult());
      assertEquals(0.05, distinct.getDoubleResult());
      assertEquals(5, distinct.getOriginalResult());

      DistinctCountFormula negative = percentage(new DistinctCountFormula(), -100);
      addAll(negative, 12, 11, 12, 11, 32.23, 11.2);
      assertEquals(-0.04, negative.getResult());
      assertEquals(4, negative.getOriginalResult());

      long base = 1_700_000_000_000L;
      Date d1 = new Date(base);
      Date d2 = new Date(base + 86_400_000L);
      DistinctCountFormula dates = percentage(new DistinctCountFormula(), 10);
      addAll(dates, d1, d1, d2);
      assertEquals(0.2, dates.getResult());
   }

   // gap:report.summary-formulas.count#count-percentage-needs-integer-total
   @Test
   void getResult_percentageWithAnyNumericTotal_returnsRatio() {
      Object[] totals = { 100L, 100.0, 100.0f, (short) 100, "100" };

      for(Object total : totals) {
         String type = total.getClass().getSimpleName();
         DistinctCountFormula distinct = percentage(new DistinctCountFormula(), total);
         addAll(distinct, "a", "b", "b", "c");

         // Distinct Count takes any numeric total since Bug #80952 (13.8 still needs an
         // Integer); a non-numeric total leaves the plain count
         if(total instanceof Number) {
            assertEquals(0.03, (Double) distinct.getResult(), 1e-9, "distinct, total " + type);
            assertEquals(0.03, distinct.getDoubleResult(), 1e-9, "distinct, total " + type);
         }
         else {
            assertEquals(3, distinct.getResult(), "distinct, total " + type);
            assertEquals(3.0, distinct.getDoubleResult(), "distinct, total " + type);
         }
      }

      // an Integer total of 0: null, or 0 with the default result
      DistinctCountFormula zeroDistinct = percentage(new DistinctCountFormula(), 0);
      addAll(zeroDistinct, "a", "b");
      assertNull(zeroDistinct.getResult());
      assertEquals(0.0, zeroDistinct.getDoubleResult());
      zeroDistinct.setDefaultResult(true);
      assertEquals(0.0, zeroDistinct.getResult());
   }

   // Test Case #80957, #80953 question 5: a typed column reaches the formula through the
   // primitive overloads (FormulaAgent), an Object column through addValue(Object). Within one
   // type both paths give the same value. Whether 1 and 1.0 in one mixed Object column are the
   // same value is still open in #80953 (the developer advised not to pin it), so mixed types
   // are not asserted here
   @Test
   void addValue_primitiveAndObjectOfOneType_areTheSameValue() {
      DistinctCountFormula distinct = new DistinctCountFormula();
      distinct.addValue(7);
      distinct.addValue((Object) Integer.valueOf(7));
      assertEquals(1, distinct.getResult());

      distinct.reset();
      distinct.addValue(7L);
      distinct.addValue((Object) Long.valueOf(7L));
      assertEquals(1, distinct.getResult());

      distinct.reset();
      distinct.addValue((short) 7);
      distinct.addValue((Object) Short.valueOf((short) 7));
      assertEquals(1, distinct.getResult());

      distinct.reset();
      distinct.addValue(7.5f);
      distinct.addValue((Object) Float.valueOf(7.5f));
      assertEquals(1, distinct.getResult());

      distinct.reset();
      distinct.addValue(7.5);
      distinct.addValue((Object) Double.valueOf(7.5));
      distinct.addValue(new double[] { 7.5 });
      assertEquals(1, distinct.getResult());
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
