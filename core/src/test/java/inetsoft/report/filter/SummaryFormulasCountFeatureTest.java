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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XConstants;
import inetsoft.util.Tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.sql.Timestamp;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Count, Distinct Count, None and Null summary formulas, driven directly through the
 * formula API.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SummaryFormulasCountFeatureTest {
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

   @Test
   @DisplayName("gap:report.summary-formulas.count#count-non-null-values")
   void countNonNullValues() {
      CountFormula count = new CountFormula();
      addAll(count, 1, 3, 3, 3, 3, 5);
      assertEquals(6, count.getResult());
      assertEquals(6.0, count.getDoubleResult());

      count.reset();
      addAll(count, "this", "is", "a", "test!");
      assertEquals(4, count.getResult());

      // dates, with a null that is not counted
      long base = 1_700_000_000_000L;
      Date d1 = new Date(base);
      Date d2 = new Date(base + 86_400_000L);
      count.reset();
      addAll(count, d1, d1, d2, null);
      assertEquals(3, count.getResult());

      count.reset();
      addAll(count, new Timestamp(base), null, null);
      assertEquals(1, count.getResult());

      // the primitive forms skip their null value
      count.reset();
      count.addValue(1.5);
      count.addValue(Tool.NULL_DOUBLE);
      count.addValue(2.5f);
      count.addValue(Tool.NULL_FLOAT);
      count.addValue(7L);
      count.addValue(Tool.NULL_LONG);
      count.addValue(8);
      count.addValue(Tool.NULL_INTEGER);
      count.addValue((short) 9);
      count.addValue(Tool.NULL_SHORT);
      count.addValue(new double[] { 3.5 });
      count.addValue(new double[] { Tool.NULL_DOUBLE });
      assertEquals(6, count.getResult());
      assertEquals(Integer.class, count.getResult().getClass());
      assertEquals(Integer.class, count.getResultType());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.count#distinct-count-unique-values")
   void distinctCountUniqueValues() {
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

   @Test
   @DisplayName("gap:report.summary-formulas.count#none-formula-last-value")
   void noneFormulaLastValue() {
      NoneFormula none = new NoneFormula();
      assertNull(none.getResult());
      assertTrue(none.isNull());

      // no aggregation: the last value
      none.addValue(1);
      none.addValue(2);
      none.addValue(3);
      assertEquals(3, none.getResult());
      assertFalse(none.isNull());

      none.reset();
      assertNull(none.getResult());
      assertTrue(none.isNull());

      none.addValue(1.5);
      none.addValue(2.5);
      assertEquals(2.5, none.getResult());
      assertEquals(2.5, none.getDoubleResult());

      none.reset();
      none.addValue(10L);
      none.addValue(20L);
      assertEquals(20L, none.getResult());

      none.reset();
      none.addValue(1.5f);
      none.addValue(3.5f);
      assertEquals(3.5f, none.getResult());

      none.reset();
      none.addValue((short) 1);
      none.addValue((short) 4);
      assertEquals((short) 4, none.getResult());

      none.reset();
      addAll(none, "first", "second");
      assertEquals("second", none.getResult());

      // a primitive wins over the object
      none.reset();
      none.addValue(7);
      none.addValue((Object) "text");
      assertEquals(7, none.getResult());

      none.reset();
      none.addValue((Object) "text");
      none.addValue(7);
      assertEquals(7, none.getResult());

      // reset restores null
      none.reset();
      assertNull(none.getResult());
      assertTrue(none.isNull());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.count#null-formula-always-null")
   void nullFormulaAlwaysNull() throws Exception {
      // NullFormula is package private, so make one by reflection
      Constructor<?> constructor =
         Class.forName("inetsoft.report.filter.NullFormula").getDeclaredConstructor();
      constructor.setAccessible(true);
      Formula formula = (Formula) constructor.newInstance();

      assertNull(formula.getResult());
      assertTrue(formula.isNull());

      addAll(formula, "text", 5, new Date(1_700_000_000_000L), null);
      formula.addValue(1.5);
      formula.addValue(2.5f);
      formula.addValue(3L);
      formula.addValue(4);
      formula.addValue((short) 5);
      formula.addValue(new double[] { 6.5 });
      assertNull(formula.getResult());
      assertTrue(formula.isNull());

      RuntimeException error = assertThrows(RuntimeException.class, formula::getDoubleResult);
      assertEquals("Unsupported method called!", error.getMessage());

      formula.reset();
      assertNull(formula.getResult());
      assertSame(formula, formula.clone());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.count#count-percentage-of-total")
   void countPercentageOfTotal() {
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

   @Test
   @DisplayName("gap:report.summary-formulas.count#distinct-count-percentage-of-total")
   void distinctCountPercentageOfTotal() {
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

   @Test
   @DisplayName("gap:report.summary-formulas.count#count-empty-string-counts")
   void countEmptyStringCounts() {
      CountFormula count = new CountFormula();
      addAll(count, "", " ", null);
      assertEquals(2, count.getResult());

      count.reset();
      addAll(count, "", "", "");
      assertEquals(3, count.getResult());

      count.reset();
      addAll(count, null, null);
      assertEquals(0, count.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.count#count-no-values-is-zero")
   void countNoValuesIsZero() {
      CountFormula count = new CountFormula();
      DistinctCountFormula distinct = new DistinctCountFormula();

      assertEquals(0, count.getResult());
      assertEquals(0, distinct.getResult());
      assertEquals(0.0, count.getDoubleResult());
      assertEquals(0.0, distinct.getDoubleResult());
      assertFalse(count.isNull());
      assertFalse(distinct.isNull());

      // only nulls
      addAll(count, null, null);
      addAll(distinct, null, null);
      assertEquals(0, count.getResult());
      assertEquals(0, distinct.getResult());
      assertFalse(count.isNull());
      assertFalse(distinct.isNull());

      // after a reset
      addAll(count, 1, 2);
      addAll(distinct, 1, 2);
      count.reset();
      distinct.reset();
      assertEquals(0, count.getResult());
      assertEquals(0, distinct.getResult());
      assertFalse(count.isNull());
      assertFalse(distinct.isNull());

      // the default result makes no difference without a percentage
      count.setDefaultResult(true);
      distinct.setDefaultResult(true);
      assertTrue(count.isDefaultResult());
      assertEquals(0, count.getResult());
      assertEquals(0, distinct.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.count#count-percentage-needs-integer-total")
   void countPercentageNeedsIntegerTotal() {
      Object[] totals = { 100L, 100.0, 100.0f, (short) 100, "100" };

      for(Object total : totals) {
         String type = total.getClass().getSimpleName();
         CountFormula count = percentage(new CountFormula(), total);
         DistinctCountFormula distinct = percentage(new DistinctCountFormula(), total);
         addAll(count, "a", "b", "b", "c");
         addAll(distinct, "a", "b", "b", "c");

         // Count: any total that is not an Integer leaves the plain count
         assertEquals(4, count.getResult(), "count, total " + type);
         assertEquals(4.0, count.getDoubleResult(), "count, total " + type);

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

      // no total at all
      CountFormula noTotal = percentage(new CountFormula(), null);
      addAll(noTotal, "a", "b");
      assertEquals(2, noTotal.getResult());

      // an Integer total of 0: null, or 0 with the default result
      CountFormula zero = percentage(new CountFormula(), 0);
      DistinctCountFormula zeroDistinct = percentage(new DistinctCountFormula(), 0);
      addAll(zero, "a", "b");
      addAll(zeroDistinct, "a", "b");
      assertNull(zero.getResult());
      assertNull(zeroDistinct.getResult());
      assertEquals(0.0, zero.getDoubleResult());
      assertEquals(0.0, zeroDistinct.getDoubleResult());

      zero.setDefaultResult(true);
      zeroDistinct.setDefaultResult(true);
      assertEquals(0.0, zero.getResult());
      assertEquals(0.0, zeroDistinct.getResult());

      // an Integer total gives the ratio
      CountFormula integer = percentage(new CountFormula(), 8);
      addAll(integer, "a", "b");
      assertEquals(0.25, integer.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.count#distinct-count-case-sensitive-and-blank")
   void distinctCountCaseSensitiveAndBlank() {
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

   @Test
   @DisplayName("gap:report.summary-formulas.count#distinct-count-reset")
   void distinctCountReset() {
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

      CountFormula count = new CountFormula();
      addAll(count, "a", "b", "c");
      assertEquals(3, count.getResult());
      count.reset();
      assertEquals(0, count.getResult());
      addAll(count, "z");
      assertEquals(1, count.getResult());

      SumFormula sum = new SumFormula();
      sum.addValue(1.5);
      sum.addValue(2.5);
      assertEquals(4.0, sum.getResult());
      sum.reset();
      assertNull(sum.getResult());
      sum.addValue(3.0);
      assertEquals(3.0, sum.getResult());
   }
}
