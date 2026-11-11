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
import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XConstants;
import inetsoft.util.Tool;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Min, Max, First, Last and Nth summary formulas, driven directly through the formula API.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SummaryFormulasMinMaxFeatureTest {
   private static final String PARSE_NUMBER = "formula.parse.number";

   private static void addAll(Formula formula, Object... values) {
      for(Object value : values) {
         formula.addValue(value);
      }
   }

   private static void addPairs(Formula formula, Object[]... pairs) {
      for(Object[] pair : pairs) {
         formula.addValue((Object) pair);
      }
   }

   private static Object[] pair(Object measure, Object by) {
      return new Object[] { measure, by };
   }

   private static void withProperty(String name, String value, Runnable action) {
      String old = SreeEnv.getProperty(name);

      try {
         SreeEnv.setProperty(name, value);
         action.run();
      }
      finally {
         if(old == null) {
            SreeEnv.remove(name);
         }
         else {
            SreeEnv.setProperty(name, old);
         }
      }
   }

   @Test
   @DisplayName("gap:report.summary-formulas.min-max#first-by-second-column")
   void firstBySecondColumn() {
      FirstFormula first = new FirstFormula(1);
      assertArrayEquals(new int[] { 1 }, first.getSecondaryColumns());
      assertTrue(first.isNull());
      assertNull(first.getResult());

      // the measure of the row with the smallest By value
      addPairs(first, pair("x", 3), pair("y", 1), pair("z", 2));
      assertFalse(first.isNull());
      assertEquals("y", first.getResult());

      // the first row wins on ties
      first.reset();
      assertTrue(first.isNull());
      addPairs(first, pair("a", 1), pair("b", 1), pair("c", 2));
      assertEquals("a", first.getResult());

      first.reset();
      addPairs(first, pair("a", 5), pair("b", 2), pair("c", 2));
      assertEquals("b", first.getResult());

      // the double array form: {measure, by}
      first.reset();
      first.addValue(new double[] { 10, 3 });
      first.addValue(new double[] { 20, 1 });
      first.addValue(new double[] { 30, 1 });
      assertEquals(20.0, first.getResult());
      assertEquals(20.0, first.getDoubleResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.min-max#last-by-second-column")
   void lastBySecondColumn() {
      LastFormula last = new LastFormula(1);
      assertArrayEquals(new int[] { 1 }, last.getSecondaryColumns());
      assertTrue(last.isNull());

      // the measure of the row with the largest By value
      addPairs(last, pair("x", 3), pair("y", 7), pair("z", 2));
      assertEquals("y", last.getResult());

      // the later row wins on ties
      last.reset();
      addPairs(last, pair("a", 5), pair("b", 5), pair("c", 1));
      assertEquals("b", last.getResult());

      last.reset();
      addPairs(last, pair("a", 1), pair("b", 4), pair("c", 4));
      assertEquals("c", last.getResult());

      // the double array form: {measure, by}
      last.reset();
      last.addValue(new double[] { 10, 9 });
      last.addValue(new double[] { 20, 9 });
      last.addValue(new double[] { 30, 1 });
      assertEquals(20.0, last.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.min-max#max-numeric")
   void maxNumeric() {
      MaxFormula max = new MaxFormula();
      max.addValue(1);
      max.addValue(5);
      max.addValue(3);
      assertEquals(5, max.getResult());

      // all negative
      max.reset();
      max.addValue(-7);
      max.addValue(-2);
      max.addValue(-9);
      assertEquals(-2, max.getResult());

      max.reset();
      max.addValue(1.5);
      max.addValue(-2.5);
      max.addValue(3.25);
      assertEquals(3.25, max.getResult());
      assertEquals(3.25, max.getDoubleResult());

      max.reset();
      max.addValue(-1.5);
      max.addValue(-0.25);
      assertEquals(-0.25, max.getResult());

      max.reset();
      max.addValue(10_000_000_000L);
      max.addValue(-1L);
      assertEquals(10_000_000_000L, max.getResult());

      max.reset();
      max.addValue(2.5f);
      max.addValue(-8.5f);
      assertEquals(2.5f, max.getResult());

      MaxFormula shorts = new MaxFormula();
      shorts.addValue((short) 4);
      shorts.addValue((short) 9);
      assertEquals((short) 9, shorts.getResult());

      // boxed values keep their type
      max.reset();
      addAll(max, 4, 11, -3);
      assertEquals(11, max.getResult());

      max.reset();
      addAll(max, 1.2, 2.1, 3.2, 2.3, 3.22);
      assertEquals(3.22, max.getResult());

      // the null value of a primitive is skipped
      max.reset();
      max.addValue(-1.0);
      max.addValue(Tool.NULL_DOUBLE);
      assertEquals(-1.0, max.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.min-max#min-numeric")
   void minNumeric() {
      MinFormula min = new MinFormula();
      min.addValue(4);
      min.addValue(-1);
      min.addValue(6);
      assertEquals(-1, min.getResult());

      min.reset();
      min.addValue(3);
      min.addValue(1);
      assertEquals(1, min.getResult());

      min.reset();
      min.addValue(1.2);
      min.addValue(-3.5);
      min.addValue(0.25);
      assertEquals(-3.5, min.getResult());
      assertEquals(-3.5, min.getDoubleResult());

      min.reset();
      min.addValue(2.5);
      min.addValue(1.25);
      assertEquals(1.25, min.getResult());

      // a long is a Long
      min.reset();
      min.addValue(10_000_000_000L);
      min.addValue(5_000_000_000L);
      assertEquals(5_000_000_000L, min.getResult());

      min.reset();
      min.addValue(2.5f);
      min.addValue(-8.5f);
      assertEquals(-8.5f, min.getResult());

      MinFormula shorts = new MinFormula();
      shorts.addValue((short) 4);
      shorts.addValue((short) -9);
      assertEquals((short) -9, shorts.getResult());

      // boxed values keep their type
      min.reset();
      addAll(min, -1, -1, 1, 1, 4, 5, 6);
      assertEquals(-1, min.getResult());

      // the null value of a primitive is skipped
      min.reset();
      min.addValue(7.5);
      min.addValue(Tool.NULL_DOUBLE);
      assertEquals(7.5, min.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.min-max#min-max-dates")
   void minMaxDates() {
      long hour = 3_600_000L;
      long base = 1_700_000_000_000L;

      Date d1 = new Date(base);
      Date d2 = new Date(base + 48 * hour);
      Date d3 = new Date(base + 24 * hour);
      MinFormula min = new MinFormula();
      MaxFormula max = new MaxFormula();
      addAll(min, d2, d1, d3, null);
      addAll(max, d2, d1, d3, null);
      assertEquals(Date.class, min.getResult().getClass());
      assertEquals(d1, min.getResult());
      assertEquals(Date.class, max.getResult().getClass());
      assertEquals(d2, max.getResult());

      Timestamp t1 = new Timestamp(base);
      Timestamp t2 = new Timestamp(base + 48 * hour);
      Timestamp t3 = new Timestamp(base + 24 * hour);
      min.reset();
      max.reset();
      addAll(min, t3, t2, t1);
      addAll(max, t3, t2, t1);
      assertEquals(Timestamp.class, min.getResult().getClass());
      assertEquals(t1, min.getResult());
      assertEquals(Timestamp.class, max.getResult().getClass());
      assertEquals(t2, max.getResult());

      Time m1 = new Time(base);
      Time m2 = new Time(base + 3 * hour);
      Time m3 = new Time(base + hour);
      min.reset();
      max.reset();
      addAll(min, m3, m2, m1);
      addAll(max, m3, m2, m1);
      assertEquals(Time.class, min.getResult().getClass());
      assertEquals(m1, min.getResult());
      assertEquals(Time.class, max.getResult().getClass());
      assertEquals(m2, max.getResult());

      // chronological, whatever the order of arrival
      Date early = new Date(base - 400 * 24 * hour);
      min.reset();
      max.reset();
      addAll(min, d1, early);
      addAll(max, early, d1);
      assertEquals(early, min.getResult());
      assertEquals(d1, max.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.min-max#nth-largest-distinct-values")
   void nthLargestDistinctValues() {
      Object[] ints = { 3, -9, 6, 10, 6, 10 };

      // 10 and 6 each count once: 10, 6, 3, -9
      Object[] expected = { 10, 6, 3, -9 };

      for(int n = 1; n <= 4; n++) {
         NthLargestFormula formula = new NthLargestFormula(n);
         addAll(formula, ints);
         assertEquals(expected[n - 1], formula.getResult(), "n=" + n);
      }

      // N equal to the distinct count over doubles
      NthLargestFormula doubles = new NthLargestFormula(3);
      addAll(doubles, 2.5, 7.25, 2.5, -1.0, 7.25);
      assertEquals(-1.0, doubles.getResult());
      assertEquals(-1.0, doubles.getDoubleResult());

      // dates; a null value is not a date to rank
      long day = 86_400_000L;
      long base = 1_700_000_000_000L;
      Date d1 = new Date(base);
      Date d2 = new Date(base + 2 * day);
      NthLargestFormula dates = new NthLargestFormula(1);
      addAll(dates, d1, d2, null);
      assertEquals(d2, dates.getResult());

      NthLargestFormula second = new NthLargestFormula(2);
      addAll(second, d1, d2, d1, d2);
      assertEquals(d1, second.getResult());

      // the primitive forms
      NthLargestFormula prim = new NthLargestFormula(2);
      prim.addValue(5);
      prim.addValue(9);
      prim.addValue(9);
      prim.addValue(Tool.NULL_INTEGER);
      assertEquals(5, prim.getResult());
      assertFalse(prim.isNull());

      prim.reset();
      assertTrue(prim.isNull());
      assertNull(prim.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.min-max#nth-most-frequent-by-count")
   void nthMostFrequentByCount() {
      // 10 occurs 3 times, -9.3 twice, 3.6 and 6 once
      Object[] values = { 3.6, -9.3, -9.3, 10.0, 10.0, 6.0, 10.0 };

      NthMostFrequentFormula first = new NthMostFrequentFormula(1);
      NthMostFrequentFormula second = new NthMostFrequentFormula(2);
      NthMostFrequentFormula third = new NthMostFrequentFormula(3);
      addAll(first, values);
      addAll(second, values);
      addAll(third, values);
      assertEquals(10.0, first.getResult());
      assertEquals(-9.3, second.getResult());
      assertEquals(3.6, third.getResult());
      assertEquals(10.0, first.getDoubleResult());
      assertFalse(first.isNull());

      // the order of arrival does not matter
      NthMostFrequentFormula reordered = new NthMostFrequentFormula(2);
      addAll(reordered, 10.0, 6.0, -9.3, 10.0, 3.6, -9.3, 10.0);
      assertEquals(-9.3, reordered.getResult());

      // text
      NthMostFrequentFormula text = new NthMostFrequentFormula(1);
      addAll(text, "x", "y", "y", "z", "y", "x");
      assertEquals("y", text.getResult());

      NthMostFrequentFormula textSecond = new NthMostFrequentFormula(2);
      addAll(textSecond, "x", "y", "y", "z", "y", "x");
      assertEquals("x", textSecond.getResult());

      // primitives are counted too
      NthMostFrequentFormula prim = new NthMostFrequentFormula(1);
      prim.addValue(4);
      prim.addValue(8);
      prim.addValue(8);
      assertEquals(8, prim.getResult());

      // a clone keeps the counts
      NthMostFrequentFormula copy = (NthMostFrequentFormula) second.clone();
      assertEquals(-9.3, copy.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.min-max#nth-smallest-distinct-values")
   void nthSmallestDistinctValues() {
      Object[] doubles = { 3.6, -9.3, 6.0, 10.0, 6.0, 10.0 };

      // -9.3, 3.6, 6, 10: duplicates count once
      Object[] expected = { -9.3, 3.6, 6.0, 10.0 };

      for(int n = 1; n <= 4; n++) {
         NthSmallestFormula formula = new NthSmallestFormula(n);
         addAll(formula, doubles);
         assertEquals(expected[n - 1], formula.getResult(), "n=" + n);
      }

      // integers
      NthSmallestFormula ints = new NthSmallestFormula(2);
      addAll(ints, 8, 3, 3, 5);
      assertEquals(5, ints.getResult());

      // text
      NthSmallestFormula text1 = new NthSmallestFormula(1);
      NthSmallestFormula text2 = new NthSmallestFormula(2);
      addAll(text1, "pear", "apple", "fig", "apple");
      addAll(text2, "pear", "apple", "fig", "apple");
      assertEquals("apple", text1.getResult());
      assertEquals("fig", text2.getResult());

      // dates
      long day = 86_400_000L;
      long base = 1_700_000_000_000L;
      Date d1 = new Date(base);
      Date d2 = new Date(base + 2 * day);
      Date d3 = new Date(base + 5 * day);
      NthSmallestFormula dates1 = new NthSmallestFormula(1);
      NthSmallestFormula dates2 = new NthSmallestFormula(2);
      addAll(dates1, d3, d1, d2);
      addAll(dates2, d3, d1, d2);
      assertEquals(d1, dates1.getResult());
      assertEquals(d2, dates2.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.min-max#first-last-percentage-of-total")
   void firstLastPercentageOfTotal() {
      FirstFormula first = new FirstFormula(1);
      first.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      first.setTotal(10);
      addPairs(first, pair(5.0, 2), pair(8.0, 1), pair(1.0, 3));
      assertEquals(0.8, first.getResult());
      assertEquals(0.8, first.getDoubleResult());
      assertEquals(8.0, first.getOriginalResult());

      // the total may be a double
      first.setTotal(40.0);
      assertEquals(0.2, first.getResult());

      LastFormula last = new LastFormula(1);
      last.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      last.setTotal(20);
      addPairs(last, pair(5.0, 2), pair(8.0, 1), pair(1.0, 3));
      assertEquals(0.05, last.getResult());
      assertEquals(1.0, last.getOriginalResult());

      // a total of 0 and a negative total give 0
      first.setTotal(0);
      assertEquals(0.0, first.getResult());
      first.setTotal(-10);
      assertEquals(0.0, first.getResult());
      last.setTotal(0);
      assertEquals(0.0, last.getResult());
      last.setTotal(-20);
      assertEquals(0.0, last.getResult());

      // a measure that is not a number gives 0
      FirstFormula text = new FirstFormula(1);
      text.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      text.setTotal(10);
      addPairs(text, pair("a", 1), pair("b", 2));
      assertEquals(0.0, text.getResult());
      assertEquals("a", text.getOriginalResult());

      LastFormula lastText = new LastFormula(1);
      lastText.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      lastText.setTotal(10);
      addPairs(lastText, pair("a", 1), pair("b", 2));
      assertEquals(0.0, lastText.getResult());
      assertEquals("b", lastText.getOriginalResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.min-max#min-max-percentage-of-total")
   void minMaxPercentageOfTotal() {
      MaxFormula max = new MaxFormula();
      max.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      max.setTotal(200.0);
      addAll(max, 10, 50, 20);
      assertEquals(0.25, max.getResult());
      assertEquals(50, max.getOriginalResult());
      assertEquals(XConstants.PERCENTAGE_OF_GRANDTOTAL, max.getPercentageType());

      MinFormula min = new MinFormula();
      min.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      min.setTotal(200);
      addAll(min, 30, 40, 80);
      assertEquals(0.15, min.getResult());
      assertEquals(30, min.getOriginalResult());

      // a negative value gives a negative ratio
      MinFormula negMin = new MinFormula();
      negMin.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      negMin.setTotal(200.0);
      negMin.addValue(-50.0);
      negMin.addValue(10.0);
      assertEquals(-0.25, negMin.getResult());

      MaxFormula negMax = new MaxFormula();
      negMax.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      negMax.setTotal(200.0);
      negMax.addValue(-50.0);
      negMax.addValue(-80.0);
      assertEquals(-0.25, negMax.getResult());

      // a total of 0: null, or 0 with the default result
      min.setTotal(0);
      max.setTotal(0);
      assertNull(min.getResult());
      assertNull(max.getResult());
      min.setDefaultResult(true);
      max.setDefaultResult(true);
      assertEquals(0.0, min.getResult());
      assertEquals(0.0, max.getResult());
      assertEquals(0.0, min.getDoubleResult());
      assertEquals(0.0, max.getDoubleResult());

      // a value that is not a number stays as it is
      MaxFormula text = new MaxFormula();
      text.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      text.setTotal(10);
      addAll(text, "abc", "abd");
      assertEquals("abd", text.getResult());

      MinFormula textMin = new MinFormula();
      textMin.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      textMin.setTotal(10);
      addAll(textMin, "abc", "abd");
      assertEquals("abc", textMin.getResult());

      Date date = new Date(1_700_000_000_000L);
      MinFormula dateMin = new MinFormula();
      dateMin.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      dateMin.setTotal(10);
      dateMin.addValue(date);
      assertEquals(date, dateMin.getResult());

      MaxFormula dateMax = new MaxFormula();
      dateMax.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      dateMax.setTotal(10);
      dateMax.addValue(date);
      assertEquals(date, dateMax.getResult());

      // no percentage type: the plain value, whatever the total
      MaxFormula plain = new MaxFormula();
      plain.setTotal(200.0);
      plain.addValue(50);
      assertEquals(50, plain.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.min-max#first-last-ignore-non-pairs")
   void firstLastIgnoreNonPairs() {
      FirstFormula first = new FirstFormula(1);
      first.addValue((Object) null);
      first.addValue("not a pair");
      first.addValue((Object) 7);
      assertTrue(first.isNull());

      // a pair whose measure is null is skipped, even with the smallest By value
      addPairs(first, pair(null, 1), pair(5, 2));
      assertFalse(first.isNull());
      assertEquals(5, first.getResult());

      first.addValue("not a pair");
      first.addValue((Object) null);
      assertEquals(5, first.getResult());

      LastFormula last = new LastFormula(1);
      last.addValue((Object) null);
      last.addValue("not a pair");
      assertTrue(last.isNull());
      addPairs(last, pair(5, 2), pair(null, 9));
      assertEquals(5, last.getResult());

      // the double array form skips a null measure, a null array and an empty array
      FirstFormula doubles = new FirstFormula(1);
      doubles.addValue((double[]) null);
      doubles.addValue(new double[0]);
      doubles.addValue(new double[] { Tool.NULL_DOUBLE, 1 });
      assertTrue(doubles.isNull());
      doubles.addValue(new double[] { 5, 2 });
      assertEquals(5.0, doubles.getResult());

      LastFormula lastDoubles = new LastFormula(1);
      lastDoubles.addValue(new double[] { Tool.NULL_DOUBLE, 1 });
      assertTrue(lastDoubles.isNull());
      lastDoubles.addValue(new double[] { 5, 2 });
      lastDoubles.addValue(new double[] { Tool.NULL_DOUBLE, 3 });
      assertEquals(5.0, lastDoubles.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.min-max#min-max-no-values-null")
   void minMaxNoValuesNull() {
      MinFormula min = new MinFormula();
      MaxFormula max = new MaxFormula();
      assertNull(min.getResult());
      assertNull(max.getResult());
      assertTrue(min.isNull());
      assertTrue(max.isNull());

      // only nulls, as an object and as the null of each primitive
      addAll(min, null, null);
      addAll(max, null, null);
      min.addValue(Tool.NULL_DOUBLE);
      max.addValue(Tool.NULL_DOUBLE);
      min.addValue(Tool.NULL_INTEGER);
      max.addValue(Tool.NULL_INTEGER);
      min.addValue(Tool.NULL_LONG);
      max.addValue(Tool.NULL_LONG);
      assertNull(min.getResult());
      assertNull(max.getResult());
      assertTrue(min.isNull());
      assertTrue(max.isNull());
      assertEquals(0.0, min.getDoubleResult());
      assertEquals(0.0, max.getDoubleResult());

      // 0 with the default result option
      min.setDefaultResult(true);
      max.setDefaultResult(true);
      assertTrue(min.isDefaultResult());
      assertEquals(0, min.getResult());
      assertEquals(0, max.getResult());

      // a real value wins over the default
      min.addValue(3);
      max.addValue(-3);
      assertEquals(3, min.getResult());
      assertEquals(-3, max.getResult());

      // reset: back to no values
      min.reset();
      max.reset();
      assertEquals(0, min.getResult());
      assertEquals(0, max.getResult());
      min.setDefaultResult(false);
      max.setDefaultResult(false);
      assertNull(min.getResult());
      assertNull(max.getResult());
   }

   @Test
   @DisplayName("Numeric strings compare as numbers and text compares as strings")
   void numericStringsCompareNumericallyAndTextAsStrings() {
      // strings that all parse as numbers compare numerically: 10 is above 9
      MinFormula min = new MinFormula();
      MaxFormula max = new MaxFormula();
      addAll(min, "9", "10");
      addAll(max, "9", "10");
      assertEquals("9", min.getResult());
      assertEquals("10", max.getResult());

      min = new MinFormula();
      max = new MaxFormula();
      addAll(min, "10", "9", "100");
      addAll(max, "10", "9", "100");
      assertEquals("9", min.getResult());
      assertEquals("100", max.getResult());

      // plain text compares as text
      min = new MinFormula();
      max = new MaxFormula();
      addAll(min, "pear", "apple", "fig");
      addAll(max, "pear", "apple", "fig");
      assertEquals("apple", min.getResult());
      assertEquals("pear", max.getResult());

      // a value that does not parse makes the whole comparison textual
      min = new MinFormula();
      addAll(min, "1", "A", "A", "1", null);
      assertEquals("1", min.getResult());

      max = new MaxFormula();
      addAll(max, "100", "A", "A", "1", "");
      assertEquals("A", max.getResult());

      // with the switch off the digits compare as text: "9" is above "10"
      withProperty(PARSE_NUMBER, "false", () -> {
         MaxFormula textMax = new MaxFormula();
         addAll(textMax, "9", "10");
         assertEquals("9", textMax.getResult());
      });
   }

   @Test
   @DisplayName("Min and Max compare numeric strings as numbers by default")
   void minMaxNumericStringsByDefault() {
      // default (parsing on): numeric order
      MinFormula defMin = new MinFormula();
      MaxFormula defMax = new MaxFormula();
      addAll(defMin, "9", "10");
      addAll(defMax, "9", "10");
      assertEquals("9", defMin.getResult());
      assertEquals("10", defMax.getResult());
   }

   @Test
   @DisplayName("Bug #80517: Min honours formula.parse.number like Max")
   @Disabled("Bug #80517: MinFormula reads formula.min.number instead of formula.parse.number, "
      + "so with formula.parse.number=false Min still compares \"9\" and \"10\" as numbers "
      + "(13.8: Bug #80487, fixed)")
   void minMaxTextAndNumericStrings() {
      withProperty(PARSE_NUMBER, "false", () -> {
         MinFormula min = new MinFormula();
         MaxFormula max = new MaxFormula();
         addAll(min, "9", "10");
         addAll(max, "9", "10");
         assertEquals("10", min.getResult());
         assertEquals("9", max.getResult());
      });
   }

   @Test
   @DisplayName("gap:report.summary-formulas.min-max#nth-most-frequent-ties-and-null")
   void nthMostFrequentTiesAndNull() {
      // null counts as a value, and the most frequent one can be null
      NthMostFrequentFormula first = new NthMostFrequentFormula(1);
      NthMostFrequentFormula second = new NthMostFrequentFormula(2);
      addAll(first, null, null, "a");
      addAll(second, null, null, "a");
      assertNull(first.getResult());
      assertEquals("a", second.getResult());
      assertFalse(first.isNull());

      // equal counts are broken by the value order, not by arrival
      Object[] tie = { 30, 10, 20 };
      Object[] expected = { 10, 20, 30 };

      for(int n = 1; n <= 3; n++) {
         NthMostFrequentFormula formula = new NthMostFrequentFormula(n);
         addAll(formula, tie);
         assertEquals(expected[n - 1], formula.getResult(), "n=" + n);
      }

      // a tie in the count is ordered by value; the count still comes first
      NthMostFrequentFormula mixed = new NthMostFrequentFormula(1);
      addAll(mixed, 5, 1, 1, 9, 9, 7);
      assertEquals(1, mixed.getResult());

      NthMostFrequentFormula mixed3 = new NthMostFrequentFormula(3);
      addAll(mixed3, 5, 1, 1, 9, 9, 7);
      assertEquals(5, mixed3.getResult());

      // text ties are ordered as text
      NthMostFrequentFormula text = new NthMostFrequentFormula(1);
      addAll(text, "pear", "apple", "fig");
      assertEquals("apple", text.getResult());

      // only nulls: the result is null but the formula has values
      NthMostFrequentFormula nulls = new NthMostFrequentFormula(1);
      addAll(nulls, null, null);
      assertNull(nulls.getResult());
      assertFalse(nulls.isNull());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.min-max#nth-n-below-one-defaults-to-one")
   void nthNBelowOneDefaultsToOne() {
      int[] counts = { 0, -3 };

      for(int n : counts) {
         NthLargestFormula largest = new NthLargestFormula(n);
         NthSmallestFormula smallest = new NthSmallestFormula(n);
         NthMostFrequentFormula frequent = new NthMostFrequentFormula(n);
         addAll(largest, 4, 9, 2, 9);
         addAll(smallest, 4, 9, 2, 9);
         addAll(frequent, 4, 9, 2, 9);
         assertEquals(9, largest.getResult(), "largest n=" + n);
         assertEquals(2, smallest.getResult(), "smallest n=" + n);
         assertEquals(9, frequent.getResult(), "frequent n=" + n);
      }

      // the same as an explicit 1
      NthLargestFormula one = new NthLargestFormula(1);
      addAll(one, 4, 9, 2, 9);
      assertEquals(9, one.getResult());
   }

   @Test
   @DisplayName("Nth formulas return null for an N beyond the distinct count")
   void nthNBeyondDistinctCountIsNull() {
      NthLargestFormula largest = new NthLargestFormula(3);
      NthSmallestFormula smallest = new NthSmallestFormula(3);
      NthMostFrequentFormula frequent = new NthMostFrequentFormula(3);
      addAll(largest, 1, 2, 2);
      addAll(smallest, 1, 2, 2);
      addAll(frequent, 1, 2, 2);
      assertNull(largest.getResult());
      assertNull(smallest.getResult());
      assertNull(frequent.getResult());
      assertEquals(0.0, largest.getDoubleResult());

      // with the default result: 0 for Nth Largest and Nth Smallest
      largest.setDefaultResult(true);
      smallest.setDefaultResult(true);
      assertEquals(0, largest.getResult());
      assertEquals(0, smallest.getResult());

      // no values at all
      NthLargestFormula none = new NthLargestFormula(1);
      assertNull(none.getResult());
      assertTrue(none.isNull());
      none.setDefaultResult(true);
      assertEquals(0, none.getResult());

      NthMostFrequentFormula noneFrequent = new NthMostFrequentFormula(1);
      assertNull(noneFrequent.getResult());
      assertTrue(noneFrequent.isNull());
      noneFrequent.setDefaultResult(true);
      assertEquals(0, noneFrequent.getResult());
   }

   @Test
   @DisplayName("Nth Most Frequent with N too large is null even with the default result "
      + "(Bug #80488 rejected, kept as is)")
   void nthNBeyondDistinctCount() {
      // 13.8 Bug #80488 asked for 0 when the default result is on and was rejected; 13.8 keeps
      // that expectation @Disabled, StyleBI pins the behavior it keeps
      NthMostFrequentFormula frequent = new NthMostFrequentFormula(3);
      addAll(frequent, 1, 2, 2);
      assertNull(frequent.getResult());
      frequent.setDefaultResult(true);
      assertNull(frequent.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.min-max#nth-text-values")
   void nthTextValues() {
      NthLargestFormula first = new NthLargestFormula(1);
      NthLargestFormula second = new NthLargestFormula(2);
      addAll(first, "9", "10", "abc");
      addAll(second, "9", "10", "abc");
      assertEquals("abc", first.getResult());
      assertEquals("10", second.getResult());

      // numeric strings order numerically: 10 is above 9
      NthLargestFormula digits = new NthLargestFormula(1);
      NthLargestFormula digits2 = new NthLargestFormula(2);
      addAll(digits, "9", "10");
      addAll(digits2, "9", "10");
      assertEquals("10", digits.getResult());
      assertEquals("9", digits2.getResult());
      assertEquals(10.0, digits.getDoubleResult());

      NthSmallestFormula small1 = new NthSmallestFormula(1);
      NthSmallestFormula small2 = new NthSmallestFormula(2);
      addAll(small1, "9", "10");
      addAll(small2, "9", "10");
      assertEquals("9", small1.getResult());
      assertEquals("10", small2.getResult());

      // text orders as text
      NthLargestFormula text = new NthLargestFormula(1);
      NthSmallestFormula textSmall = new NthSmallestFormula(1);
      addAll(text, "kiwi", "apple", "pear");
      addAll(textSmall, "kiwi", "apple", "pear");
      assertEquals("pear", text.getResult());
      assertEquals("apple", textSmall.getResult());

      NthLargestFormula text2 = new NthLargestFormula(2);
      addAll(text2, "kiwi", "apple", "pear");
      assertEquals("kiwi", text2.getResult());

      // the old suite case: text and digits mixed
      NthLargestFormula mixed = new NthLargestFormula(1);
      addAll(mixed, "a", "A", "3", "7", "", null);
      assertEquals("a", mixed.getResult());
   }

   @Test
   @DisplayName("Bug #80738: Max skips a blank string like Sum")
   void maxSkipsABlankString() {
      MaxFormula max = new MaxFormula();
      addAll(max, "1", "", "3");
      assertEquals("3", max.getResult());
      assertFalse(max.isNull());

      // whitespace is blank too, wherever it is in the list; a leading blank does not stick
      max = new MaxFormula();
      addAll(max, "", "5", " ", "1", "  ", "3", "");
      assertEquals("5", max.getResult());

      // numbers mixed with blank strings
      max = new MaxFormula();
      addAll(max, 10, "", 20, "30", "");
      assertEquals("30", max.getResult());

      // the percentage option works on the value that is kept
      max = new MaxFormula();
      addAll(max, "2", "", "4");
      max.setPercentageType(StyleConstants.PERCENTAGE_OF_GRANDTOTAL);
      max.setTotal(8);
      assertEquals(0.5, ((Number) max.getResult()).doubleValue(), 1e-9);
      assertEquals("4", max.getOriginalResult());

      // a clone keeps the value that was kept
      MaxFormula copy = (MaxFormula) max.clone();
      assertEquals("4", copy.getOriginalResult());

      // reset starts over
      max.reset();
      addAll(max, "", "6");
      assertEquals("6", max.getOriginalResult());

      // only blank strings leave nothing to calculate, like only nulls
      MaxFormula blanks = new MaxFormula();
      addAll(blanks, "", " ");
      assertTrue(blanks.isNull());
      assertNull(blanks.getResult());
      blanks.setDefaultResult(true);
      assertEquals(0, blanks.getResult());

      // text still compares as text; the blank is not part of it
      MaxFormula text = new MaxFormula();
      addAll(text, "pear", "", "apple", "fig");
      assertEquals("pear", text.getResult());

      // with numeric parsing off the strings are text and the blank is left alone
      withProperty(PARSE_NUMBER, "false", () -> {
         MaxFormula textMax = new MaxFormula();
         addAll(textMax, "9", "", "10");
         assertEquals("9", textMax.getResult());
         NthSmallestFormula textSmallest = new NthSmallestFormula(1);
         addAll(textSmallest, "9", "", "10");
         assertEquals("", textSmallest.getResult());
      });
   }

   @Test
   @DisplayName("Bug #80738: Nth Largest and Nth Smallest skip a blank string like Sum")
   void nthLargestAndSmallestSkipABlankString() {
      Object[] largestOf = { "3", "1", null };
      Object[] smallestOf = { "1", "3", null };

      for(int n = 1; n <= 3; n++) {
         NthLargestFormula largest = new NthLargestFormula(n);
         NthSmallestFormula smallest = new NthSmallestFormula(n);
         addAll(largest, "1", "", "3");
         addAll(smallest, "1", "", "3");
         assertEquals(largestOf[n - 1], largest.getResult(), "largest " + n);
         assertEquals(smallestOf[n - 1], smallest.getResult(), "smallest " + n);
      }

      NthLargestFormula second = new NthLargestFormula(2);
      addAll(second, "", "5", " ", "1", "  ", "9", "");
      assertEquals("5", second.getResult());
      assertEquals(5.0, second.getDoubleResult(), 1e-9);

      // a clone keeps the values that were kept
      NthSmallestFormula smallest = new NthSmallestFormula(2);
      addAll(smallest, "7", "", "2", "4");
      NthSmallestFormula copy = (NthSmallestFormula) smallest.clone();
      assertEquals("4", smallest.getResult());
      assertEquals("4", copy.getResult());

      // reset starts over
      smallest.reset();
      assertTrue(smallest.isNull());
      addAll(smallest, "", "8");
      assertFalse(smallest.isNull());

      // only blank strings leave nothing to rank, like only nulls
      NthLargestFormula blanks = new NthLargestFormula(1);
      addAll(blanks, "", " ");
      assertTrue(blanks.isNull());
      assertNull(blanks.getResult());
      blanks.setDefaultResult(true);
      assertEquals(0, blanks.getResult());

      // text is still ranked as before
      NthLargestFormula text = new NthLargestFormula(1);
      addAll(text, "pear", "", "apple");
      assertEquals("pear", text.getResult());
   }

   @Test
   @DisplayName("Bug #80738: a table summary of numeric text with blank cells ranks the numbers")
   void blankStringsInATableSummary() {
      TableLens data = new DefaultTableLens(new Object[][] {
         { "g", "v" }, { "a", "1" }, { "a", "" }, { "a", "3" }, { "b", "" }, { "b", "5" },
         { "c", "" }
      });
      Formula[] formulas = {
         new MaxFormula(), new NthLargestFormula(1), new NthLargestFormula(2),
         new NthSmallestFormula(2)
      };
      // rows: group a, group b, group c (only a blank), then the grand total
      Object[][] expected = {
         { "3", "3", "1", "3" }, { "5", "5", null, null }, { null, null, null, null },
         { "5", "5", "3", "3" }
      };

      for(int f = 0; f < formulas.length; f++) {
         SummaryFilter summary = new SummaryFilter(
            data, new int[] { 0 }, new int[] { 1 }, (Formula) formulas[f].clone(),
            (Formula) formulas[f].clone());
         summary.moreRows(Integer.MAX_VALUE);
         assertEquals(5, summary.getRowCount());

         for(int g = 0; g < 4; g++) {
            Object actual = summary.getObject(g + 1, 1);
            String msg = formulas[f].getName() + " row " + (g + 1);

            if(expected[g][f] == null) {
               // no value to calculate: nothing, not a blank ranked as the largest
               assertTrue(actual == null || "".equals(actual), msg + " " + actual);
            }
            else {
               assertEquals(expected[g][f], actual, msg);
            }
         }
      }
   }

   @Test
   @DisplayName("Bug #80738: blank strings change nothing, and other lists are unchanged")
   void blankStringsAreRemovedBeforeMaxAndNth() {
      Random random = new Random(80738L);
      int compared = 0;

      for(int i = 0; i < 3000; i++) {
         // some lists are longer than 32 values
         int size = i % 4 == 0 ? 33 + random.nextInt(8) : random.nextInt(12);
         List<Object> values = new ArrayList<>();
         List<Object> noBlanks = new ArrayList<>();

         for(int j = 0; j < size; j++) {
            Object value = random.nextDouble() < 0.25 ? (random.nextBoolean() ? "" : "  ")
               : random.nextBoolean() ? Integer.toString(random.nextInt(21) - 10)
               : Double.toString(random.nextInt(2000) / 10.0 - 100);
            values.add(value);

            if(!((String) value).trim().isEmpty()) {
               noBlanks.add(value);
            }
         }

         int n = 1 + random.nextInt(6);
         MaxFormula max = new MaxFormula();
         NthLargestFormula largest = new NthLargestFormula(n);
         NthSmallestFormula smallest = new NthSmallestFormula(n);
         MaxFormula maxNoBlanks = new MaxFormula();
         NthLargestFormula largestNoBlanks = new NthLargestFormula(n);
         NthSmallestFormula smallestNoBlanks = new NthSmallestFormula(n);

         for(Object v : values) {
            max.addValue(v);
            largest.addValue(v);
            smallest.addValue(v);
         }

         for(Object v : noBlanks) {
            maxNoBlanks.addValue(v);
            largestNoBlanks.addValue(v);
            smallestNoBlanks.addValue(v);
         }

         String msg = values + " n=" + n;
         assertEquals(maxNoBlanks.getResult(), max.getResult(), msg);
         assertEquals(largestNoBlanks.getResult(), largest.getResult(), msg);
         assertEquals(smallestNoBlanks.getResult(), smallest.getResult(), msg);

         // a list with no blank gives what the algorithm before the fix gives, copied here
         assertEquals(baseMax(noBlanks), max.getResult(), msg);
         assertEquals(baseNth(noBlanks, n, false), largest.getResult(), msg);
         assertEquals(baseNth(noBlanks, n, true), smallest.getResult(), msg);
         compared++;
      }

      assertEquals(3000, compared);

      // text and mixed lists keep their result, with or without a blank string at the end
      Object[] pool = { null, "abc", "b", "10", "9", 3, 7.5, 2L };
      Random mixed = new Random(80739L);

      for(int i = 0; i < 2000; i++) {
         Object[] values = new Object[mixed.nextInt(8)];

         for(int j = 0; j < values.length; j++) {
            values[j] = pool[mixed.nextInt(pool.length)];
         }

         Object[] withBlank = java.util.Arrays.copyOf(values, values.length + 1);
         withBlank[values.length] = "";
         MaxFormula max = new MaxFormula();
         MaxFormula maxBlank = new MaxFormula();
         NthLargestFormula largest = new NthLargestFormula(2);
         NthLargestFormula largestBlank = new NthLargestFormula(2);
         addAll(max, values);
         addAll(largest, values);
         addAll(maxBlank, withBlank);
         addAll(largestBlank, withBlank);
         String msg = java.util.Arrays.toString(values);
         assertEquals(max.getResult(), maxBlank.getResult(), msg);
         assertEquals(largest.getResult(), largestBlank.getResult(), msg);
      }
   }

   // MaxFormula.addValue(Object) for numeric strings before the fix: the first of the largest
   private static Object baseMax(List<Object> values) {
      Object max = null;

      for(Object v : values) {
         if(max == null || Double.compare(Double.parseDouble((String) max),
                                          Double.parseDouble((String) v)) < 0) {
            max = v;
         }
      }

      return max;
   }

   // NthLargestFormula.addValue(Object) and getResult for numeric strings before the fix
   private static Object baseNth(List<Object> values, int n, boolean smallest) {
      java.util.Vector<Object> vs = new java.util.Vector<>();

      for(Object value : values) {
         Object v = value;

         for(int i = vs.size() - 1; i >= 0; i--) {
            double a = Double.parseDouble((String) v);
            double b = Double.parseDouble((String) vs.elementAt(i));
            int c = smallest ? Double.compare(b, a) : Double.compare(a, b);

            if(c == 0) {
               v = null;
               break;
            }
            else if(c < 0) {
               vs.insertElementAt(v, i + 1);
               v = null;
               break;
            }
         }

         if(v != null) {
            vs.insertElementAt(v, 0);
         }

         if(vs.size() > n) {
            vs.setSize(n);
         }
      }

      return vs.size() >= n ? vs.elementAt(n - 1) : null;
   }
}
