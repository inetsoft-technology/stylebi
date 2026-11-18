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

import inetsoft.sree.SreeEnv;
import inetsoft.uql.XConstants;
import inetsoft.util.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MaxFormula.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class MaxFormulaTest {

   private MaxFormula formula;

   @BeforeEach
   void setUp() {
      formula = new MaxFormula();
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
      formula.addValue(5.0);
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
      assertEquals(Integer.valueOf(0), formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(Object)
   // -----------------------------------------------------------------------

   @Test
   void addValue_singleObject_returnsItself() {
      formula.addValue(Integer.valueOf(42));
      assertEquals(42, formula.getResult());
   }

   @Test
   void addValue_multipleIntegers_returnsMaximum() {
      formula.addValue(Integer.valueOf(10));
      formula.addValue(Integer.valueOf(50));
      formula.addValue(Integer.valueOf(30));
      assertEquals(50, formula.getResult());
   }

   @Test
   void addValue_nullObject_skipped() {
      formula.addValue(Integer.valueOf(5));
      formula.addValue((Object) null);
      assertEquals(5, formula.getResult());
   }

   @Test
   void addValue_strings_returnsLexicographicMax() {
      formula.addValue("banana");
      formula.addValue("apple");
      formula.addValue("cherry");
      assertEquals("cherry", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double)
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_singleValue_returnsItself() {
      formula.addValue(3.14);
      assertEquals(3.14, formula.getResult());
   }

   @Test
   void addValueDouble_multipleValues_returnsMax() {
      formula.addValue(5.0);
      formula.addValue(2.0);
      formula.addValue(8.0);
      assertEquals(8.0, formula.getResult());
   }

   @Test
   void addValueDouble_nullSentinel_skipped() {
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueDouble_negativeValues_handledCorrectly() {
      formula.addValue(-3.0);
      formula.addValue(-10.0);
      formula.addValue(-1.0);
      assertEquals(-1.0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double[])
   // -----------------------------------------------------------------------

   @Test
   void addValueDoubleArray_singleElement_tracked() {
      formula.addValue(new double[]{7.0});
      assertEquals(7.0, formula.getResult());
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
   void addValueFloat_multipleValues_returnsMax() {
      formula.addValue(1.5f);
      formula.addValue(4.0f);
      formula.addValue(3.0f);
      assertEquals(4.0f, formula.getResult());
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
   void addValueLong_multipleValues_returnsMax() {
      formula.addValue(50L);
      formula.addValue(300L);
      formula.addValue(200L);
      assertEquals(300L, formula.getResult());
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
   void addValueInt_multipleValues_returnsMax() {
      formula.addValue(5);
      formula.addValue(100);
      formula.addValue(50);
      assertEquals(100, formula.getResult());
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
   void addValueShort_multipleValues_returnsMax() {
      formula.addValue((short) 3);
      formula.addValue((short) 10);
      formula.addValue((short) 7);
      assertEquals((short) 10, formula.getResult());
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
   void getDoubleResult_afterAddDoubleValues_returnsMax() {
      formula.addValue(2.0);
      formula.addValue(9.0);
      formula.addValue(5.0);
      assertEquals(9.0, formula.getDoubleResult());
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsMaxValue() {
      formula.addValue(100.0);
      formula.reset();
      assertTrue(formula.isNull());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      formula.addValue(100.0);
      formula.reset();
      formula.addValue(10.0);
      formula.addValue(5.0);
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
      MaxFormula cloned = (MaxFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
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
   // blank strings (Bug #80888)
   // -----------------------------------------------------------------------

   @Test
   void addValue_blankStringInAnyPosition_skipped() {
      String[][] inputs = {
         { "", "1", "3" }, { "1", "", "3" }, { "1", "3", "" },
         { " ", "1", "3" }, { "1", " ", "3" }, { "1", "3", " " } };

      for(String[] values : inputs) {
         MaxFormula max = new MaxFormula();

         for(String value : values) {
            max.addValue(value);
         }

         assertEquals("3", max.getResult(), String.join("|", values));
      }
   }

   @Test
   void addValue_onlyBlankStrings_isNull() {
      formula.addValue("");
      formula.addValue(" ");
      assertTrue(formula.isNull());
      assertNull(formula.getResult());
   }

   @Test
   void addValue_textWithBlank_returnsLexicographicMax() {
      formula.addValue("Bob");
      formula.addValue("");
      formula.addValue("Alice");
      assertEquals("Bob", formula.getResult());
   }

   @Test
   void addValue_blankString_parseNumberOff_notSkipped() {
      String old = SreeEnv.getProperty("formula.parse.number");

      try {
         SreeEnv.setProperty("formula.parse.number", "false");
         MaxFormula max = new MaxFormula();
         max.addValue("");
         assertEquals("", max.getResult());
      }
      finally {
         if(old == null) {
            SreeEnv.remove("formula.parse.number");
         }
         else {
            SreeEnv.setProperty("formula.parse.number", old);
         }
      }
   }

   // -----------------------------------------------------------------------
   // more numeric input, dates, result type, clone and reset
   // -----------------------------------------------------------------------

   // gap:report.summary-formulas.min-max#max-numeric
   @Test
   void addValue_negativeWideAndBoxedValues_returnsMax() {
      // all negative
      formula.addValue(-7);
      formula.addValue(-2);
      formula.addValue(-9);
      assertEquals(-2, formula.getResult());

      formula.reset();
      formula.addValue(1.5);
      formula.addValue(-2.5);
      formula.addValue(3.25);
      assertEquals(3.25, formula.getResult());
      assertEquals(3.25, formula.getDoubleResult());

      formula.reset();
      formula.addValue(-1.5);
      formula.addValue(-0.25);
      assertEquals(-0.25, formula.getResult());

      formula.reset();
      formula.addValue(10_000_000_000L);
      formula.addValue(-1L);
      assertEquals(10_000_000_000L, formula.getResult());

      formula.reset();
      formula.addValue(2.5f);
      formula.addValue(-8.5f);
      assertEquals(2.5f, formula.getResult());

      // boxed values keep their type
      formula.reset();
      addAll(formula, 4, 11, -3);
      assertEquals(11, formula.getResult());

      formula.reset();
      addAll(formula, 1.2, 2.1, 3.2, 2.3, 3.22);
      assertEquals(3.22, formula.getResult());

      // the null value of a primitive is skipped
      formula.reset();
      formula.addValue(-1.0);
      formula.addValue(Tool.NULL_DOUBLE);
      assertEquals(-1.0, formula.getResult());
   }

   // gap:report.summary-formulas.min-max#min-max-dates
   @Test
   void addValue_datesTimestampsAndTimes_returnsLatestKeepingType() {
      long hour = 3_600_000L;
      long base = 1_700_000_000_000L;

      Date d1 = new Date(base);
      Date d2 = new Date(base + 48 * hour);
      Date d3 = new Date(base + 24 * hour);
      addAll(formula, d2, d1, d3, null);
      assertEquals(Date.class, formula.getResult().getClass());
      assertEquals(d2, formula.getResult());

      Timestamp t1 = new Timestamp(base);
      Timestamp t2 = new Timestamp(base + 48 * hour);
      Timestamp t3 = new Timestamp(base + 24 * hour);
      formula.reset();
      addAll(formula, t3, t2, t1);
      assertEquals(Timestamp.class, formula.getResult().getClass());
      assertEquals(t2, formula.getResult());

      Time m1 = new Time(base);
      Time m2 = new Time(base + 3 * hour);
      Time m3 = new Time(base + hour);
      formula.reset();
      addAll(formula, m3, m2, m1);
      assertEquals(Time.class, formula.getResult().getClass());
      assertEquals(m2, formula.getResult());

      // chronological, whatever the order of arrival
      Date early = new Date(base - 400 * 24 * hour);
      formula.reset();
      addAll(formula, early, d1);
      assertEquals(d1, formula.getResult());
   }

   // gap:report.summary-formulas.common#result-type-defaults-to-input-type
   @Test
   void getResultType_takesTheInputType_returnsNull() {
      assertNull(formula.getResultType());
   }

   // gap:report.summary-formulas.min-max#min-max-no-values-null
   @Test
   void getResult_onlyNullsThenDefaultResult_realValueWinsAndResetGoesBack() {
      // only nulls, as an object and as the null of each primitive
      addAll(formula, null, null);
      formula.addValue(Tool.NULL_DOUBLE);
      formula.addValue(Tool.NULL_INTEGER);
      formula.addValue(Tool.NULL_LONG);
      assertNull(formula.getResult());
      assertTrue(formula.isNull());
      assertEquals(0.0, formula.getDoubleResult());

      // 0 with the default result option
      formula.setDefaultResult(true);
      assertEquals(0, formula.getResult());

      // a real value wins over the default
      formula.addValue(-3);
      assertEquals(-3, formula.getResult());

      // reset: back to no values
      formula.reset();
      assertEquals(0, formula.getResult());
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
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

   // gap:report.summary-formulas.min-max#min-max-percentage-of-total
   @Test
   void getResult_percentageOfTotal_returnsRatio() {
      MaxFormula max = new MaxFormula();
      max.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      max.setTotal(200.0);
      addAll(max, 10, 50, 20);
      assertEquals(0.25, max.getResult());
      assertEquals(50, max.getOriginalResult());
      assertEquals(XConstants.PERCENTAGE_OF_GRANDTOTAL, max.getPercentageType());

      // a negative value gives a negative ratio
      MaxFormula negMax = new MaxFormula();
      negMax.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      negMax.setTotal(200.0);
      negMax.addValue(-50.0);
      negMax.addValue(-80.0);
      assertEquals(-0.25, negMax.getResult());

      // a total of 0: null, or 0 with the default result
      max.setTotal(0);
      assertNull(max.getResult());
      max.setDefaultResult(true);
      assertEquals(0.0, max.getResult());
      assertEquals(0.0, max.getDoubleResult());

      // a value that is not a number stays as it is
      MaxFormula text = new MaxFormula();
      text.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      text.setTotal(10);
      addAll(text, "abc", "abd");
      assertEquals("abd", text.getResult());

      Date date = new Date(1_700_000_000_000L);
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

   // -----------------------------------------------------------------------
   // numeric strings
   // -----------------------------------------------------------------------

   // Numeric strings compare as numbers and text compares as strings; Max compares numeric
   // strings as numbers by default
   @Test
   void addValue_numericStrings_compareAsNumbers() {
      // strings that all parse as numbers compare numerically: 10 is above 9
      addAll(formula, "9", "10");
      assertEquals("10", formula.getResult());

      MaxFormula max = new MaxFormula();
      addAll(max, "10", "9", "100");
      assertEquals("100", max.getResult());

      // a value that does not parse makes the whole comparison textual
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

   // -----------------------------------------------------------------------
   // blank strings (Bug #80738)
   // -----------------------------------------------------------------------

   // Bug #80738: Max skips a blank string like Sum
   @Test
   void addValue_blankStrings_skippedWithNumbersPercentageCloneAndReset() {
      // whitespace is blank too, wherever it is in the list; a leading blank does not stick
      MaxFormula max = new MaxFormula();
      addAll(max, "", "5", " ", "1", "  ", "3", "");
      assertEquals("5", max.getResult());
      assertFalse(max.isNull());

      // numbers mixed with blank strings
      max = new MaxFormula();
      addAll(max, 10, "", 20, "30", "");
      assertEquals("30", max.getResult());

      // the percentage option works on the value that is kept
      max = new MaxFormula();
      addAll(max, "2", "", "4");
      max.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
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
      });
   }

   // Bug #80738: blank strings change nothing, and other lists are unchanged
   @Test
   void addValue_randomListsWithBlanks_sameAsWithoutBlanks() {
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

         // NthLargestFormulaTest and NthSmallestFormulaTest draw their N here; draw it too so
         // the same seed gives the same lists
         random.nextInt(6);
         MaxFormula max = new MaxFormula();
         MaxFormula maxNoBlanks = new MaxFormula();

         for(Object v : values) {
            max.addValue(v);
         }

         for(Object v : noBlanks) {
            maxNoBlanks.addValue(v);
         }

         String msg = values.toString();
         assertEquals(maxNoBlanks.getResult(), max.getResult(), msg);

         // a list with no blank gives what the algorithm before the fix gives, copied here
         assertEquals(baseMax(noBlanks), max.getResult(), msg);
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

         Object[] withBlank = Arrays.copyOf(values, values.length + 1);
         withBlank[values.length] = "";
         MaxFormula max = new MaxFormula();
         MaxFormula maxBlank = new MaxFormula();
         addAll(max, values);
         addAll(maxBlank, withBlank);
         assertEquals(max.getResult(), maxBlank.getResult(), Arrays.toString(values));
      }
   }

   private static final String PARSE_NUMBER = "formula.parse.number";

   private static void addAll(Formula formula, Object... values) {
      for(Object value : values) {
         formula.addValue(value);
      }
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
}
