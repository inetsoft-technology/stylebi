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
import org.junit.jupiter.api.Disabled;
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
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MinFormula.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class MinFormulaTest {

   private MinFormula formula;

   @BeforeEach
   void setUp() {
      formula = new MinFormula();
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
   void addValue_multipleIntegers_returnsMinimum() {
      formula.addValue(Integer.valueOf(30));
      formula.addValue(Integer.valueOf(10));
      formula.addValue(Integer.valueOf(20));
      assertEquals(10, formula.getResult());
   }

   @Test
   void addValue_nullObject_skipped() {
      formula.addValue(Integer.valueOf(5));
      formula.addValue((Object) null);
      assertEquals(5, formula.getResult());
   }

   @Test
   void addValue_strings_returnsLexicographicMin() {
      formula.addValue("banana");
      formula.addValue("apple");
      formula.addValue("cherry");
      assertEquals("apple", formula.getResult());
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
   void addValueDouble_multipleValues_returnsMin() {
      formula.addValue(5.0);
      formula.addValue(2.0);
      formula.addValue(8.0);
      assertEquals(2.0, formula.getResult());
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
      assertEquals(-10.0, formula.getResult());
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

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_multipleValues_returnsMin() {
      formula.addValue(4.0f);
      formula.addValue(1.5f);
      formula.addValue(3.0f);
      assertEquals(1.5f, formula.getResult());
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
   void addValueLong_multipleValues_returnsMin() {
      formula.addValue(200L);
      formula.addValue(50L);
      formula.addValue(300L);
      assertEquals(50L, formula.getResult());
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
   void addValueInt_multipleValues_returnsMin() {
      formula.addValue(100);
      formula.addValue(5);
      formula.addValue(50);
      assertEquals(5, formula.getResult());
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
   void addValueShort_multipleValues_returnsMin() {
      formula.addValue((short) 10);
      formula.addValue((short) 3);
      formula.addValue((short) 7);
      assertEquals((short) 3, formula.getResult());
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
   void getDoubleResult_afterAddDoubleValues_returnsMin() {
      formula.addValue(5.0);
      formula.addValue(2.0);
      formula.addValue(9.0);
      assertEquals(2.0, formula.getDoubleResult());
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsMinValue() {
      formula.addValue(5.0);
      formula.reset();
      assertTrue(formula.isNull());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      formula.addValue(5.0);
      formula.reset();
      formula.addValue(100.0);
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
      MinFormula cloned = (MinFormula) formula.clone();
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
   // blank strings (Bug #80888) - Min is unchanged
   // -----------------------------------------------------------------------

   @Test
   void addValue_blankStringInAnyPosition_returnsSmallestNumber() {
      String[][] inputs = { { "", "1", "3" }, { "1", "", "3" }, { "1", "3", "" } };

      for(String[] values : inputs) {
         MinFormula min = new MinFormula();

         for(String value : values) {
            min.addValue(value);
         }

         assertEquals("1", min.getResult(), String.join("|", values));
      }
   }

   @Test
   void addValue_textWithBlank_returnsBlank() {
      formula.addValue("Bob");
      formula.addValue("");
      formula.addValue("Alice");
      assertEquals("", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // more numeric input, dates, result type, clone and reset
   // -----------------------------------------------------------------------

   // gap:report.summary-formulas.min-max#min-numeric
   @Test
   void addValue_negativeWideAndBoxedValues_returnsMin() {
      formula.addValue(4);
      formula.addValue(-1);
      formula.addValue(6);
      assertEquals(-1, formula.getResult());

      formula.reset();
      formula.addValue(1.2);
      formula.addValue(-3.5);
      formula.addValue(0.25);
      assertEquals(-3.5, formula.getResult());
      assertEquals(-3.5, formula.getDoubleResult());

      formula.reset();
      formula.addValue(2.5);
      formula.addValue(1.25);
      assertEquals(1.25, formula.getResult());

      // a long is a Long
      formula.reset();
      formula.addValue(10_000_000_000L);
      formula.addValue(5_000_000_000L);
      assertEquals(5_000_000_000L, formula.getResult());

      formula.reset();
      formula.addValue(2.5f);
      formula.addValue(-8.5f);
      assertEquals(-8.5f, formula.getResult());

      MinFormula shorts = new MinFormula();
      shorts.addValue((short) 4);
      shorts.addValue((short) -9);
      assertEquals((short) -9, shorts.getResult());

      // boxed values keep their type
      formula.reset();
      addAll(formula, -1, -1, 1, 1, 4, 5, 6);
      assertEquals(-1, formula.getResult());

      // the null value of a primitive is skipped
      formula.reset();
      formula.addValue(7.5);
      formula.addValue(Tool.NULL_DOUBLE);
      assertEquals(7.5, formula.getResult());
   }

   // gap:report.summary-formulas.min-max#min-max-dates
   @Test
   void addValue_datesTimestampsAndTimes_returnsEarliestKeepingType() {
      long hour = 3_600_000L;
      long base = 1_700_000_000_000L;

      Date d1 = new Date(base);
      Date d2 = new Date(base + 48 * hour);
      Date d3 = new Date(base + 24 * hour);
      addAll(formula, d2, d1, d3, null);
      assertEquals(Date.class, formula.getResult().getClass());
      assertEquals(d1, formula.getResult());

      Timestamp t1 = new Timestamp(base);
      Timestamp t2 = new Timestamp(base + 48 * hour);
      Timestamp t3 = new Timestamp(base + 24 * hour);
      formula.reset();
      addAll(formula, t3, t2, t1);
      assertEquals(Timestamp.class, formula.getResult().getClass());
      assertEquals(t1, formula.getResult());

      Time m1 = new Time(base);
      Time m2 = new Time(base + 3 * hour);
      Time m3 = new Time(base + hour);
      formula.reset();
      addAll(formula, m3, m2, m1);
      assertEquals(Time.class, formula.getResult().getClass());
      assertEquals(m1, formula.getResult());

      // chronological, whatever the order of arrival
      Date early = new Date(base - 400 * 24 * hour);
      formula.reset();
      addAll(formula, d1, early);
      assertEquals(early, formula.getResult());
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
      formula.addValue(3);
      assertEquals(3, formula.getResult());

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

      // a total of 0: null, or 0 with the default result
      min.setTotal(0);
      assertNull(min.getResult());
      min.setDefaultResult(true);
      assertEquals(0.0, min.getResult());
      assertEquals(0.0, min.getDoubleResult());

      // a value that is not a number stays as it is
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
   }

   // -----------------------------------------------------------------------
   // numeric strings
   // -----------------------------------------------------------------------

   // Numeric strings compare as numbers and text compares as strings; Min compares numeric
   // strings as numbers by default
   @Test
   void addValue_numericStrings_compareAsNumbers() {
      // strings that all parse as numbers compare numerically: 10 is above 9
      addAll(formula, "9", "10");
      assertEquals("9", formula.getResult());

      MinFormula min = new MinFormula();
      addAll(min, "10", "9", "100");
      assertEquals("9", min.getResult());

      // a value that does not parse makes the whole comparison textual
      min = new MinFormula();
      addAll(min, "1", "A", "A", "1", null);
      assertEquals("1", min.getResult());
   }

   // Bug #80517: Min honours formula.parse.number like Max
   @Test
   @Disabled("Bug #80517: MinFormula reads formula.min.number instead of formula.parse.number, "
      + "so with formula.parse.number=false Min still compares \"9\" and \"10\" as numbers "
      + "(13.8: Bug #80487, fixed)")
   void addValue_numericStrings_parseNumberOff_compareAsText() {
      withProperty("formula.parse.number", "false", () -> {
         MinFormula min = new MinFormula();
         addAll(min, "9", "10");
         assertEquals("10", min.getResult());
      });
   }

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
}
