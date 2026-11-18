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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NthLargestFormula.
 *
 * <p>NthLargestFormula maintains a sorted list of the N largest distinct values.
 * getResult() returns the Nth largest value (n=1 is the maximum).
 * Duplicate values are not stored.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class NthLargestFormulaTest {

   // -----------------------------------------------------------------------
   // isNull()
   // -----------------------------------------------------------------------

   @Test
   void isNull_noValuesAdded_returnsTrue() {
      NthLargestFormula formula = new NthLargestFormula(1);
      assertTrue(formula.isNull());
   }

   @Test
   void isNull_afterAddValue_returnsFalse() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Double.valueOf(5.0));
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_afterReset_returnsTrue() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Double.valueOf(5.0));
      formula.reset();
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — no values
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_defaultResultFalse_returnsNull() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
   }

   @Test
   void getResult_noValues_defaultResultTrue_returnsZero() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.setDefaultResult(true);
      // getResult() returns Integer(0) when def=true and no qualifying values
      assertEquals(Integer.valueOf(0), formula.getResult());
   }

   // -----------------------------------------------------------------------
   // n=1: returns the largest value
   // -----------------------------------------------------------------------

   @Test
   void getResult_n1_singleValue_returnsThatValue() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Double.valueOf(42.0));
      assertEquals(42.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_n1_multipleValues_returnsMax() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Double.valueOf(3.0));
      formula.addValue(Double.valueOf(7.0));
      formula.addValue(Double.valueOf(1.0));
      formula.addValue(Double.valueOf(9.0));
      formula.addValue(Double.valueOf(5.0));
      assertEquals(9.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // n=2: returns the second largest
   // -----------------------------------------------------------------------

   @Test
   void getResult_n2_multipleValues_returnsSecondLargest() {
      NthLargestFormula formula = new NthLargestFormula(2);
      formula.addValue(Double.valueOf(3.0));
      formula.addValue(Double.valueOf(7.0));
      formula.addValue(Double.valueOf(1.0));
      formula.addValue(Double.valueOf(9.0));
      formula.addValue(Double.valueOf(5.0));
      assertEquals(7.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_n2_fewerThanNDistinctValues_returnsNullWhenDefaultFalse() {
      NthLargestFormula formula = new NthLargestFormula(2);
      formula.setDefaultResult(false);
      formula.addValue(Double.valueOf(5.0));
      // only one distinct value, can't return 2nd largest
      assertNull(formula.getResult());
   }

   @Test
   void getResult_n2_fewerThanNDistinctValues_returnsZeroWhenDefaultTrue() {
      NthLargestFormula formula = new NthLargestFormula(2);
      formula.setDefaultResult(true);
      formula.addValue(Double.valueOf(5.0));
      // getResult() returns Integer(0) when def=true and no qualifying values
      assertEquals(Integer.valueOf(0), formula.getResult());
   }

   // -----------------------------------------------------------------------
   // n=3: returns the third largest
   // -----------------------------------------------------------------------

   @Test
   void getResult_n3_fiveDistinctValues_returnsThirdLargest() {
      NthLargestFormula formula = new NthLargestFormula(3);
      formula.addValue(Double.valueOf(10.0));
      formula.addValue(Double.valueOf(20.0));
      formula.addValue(Double.valueOf(30.0));
      formula.addValue(Double.valueOf(40.0));
      formula.addValue(Double.valueOf(50.0));
      assertEquals(30.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // Duplicate values are not stored
   // -----------------------------------------------------------------------

   @Test
   void addValue_duplicates_onlyOneEntryKept() {
      NthLargestFormula formula = new NthLargestFormula(2);
      formula.addValue(Double.valueOf(5.0));
      formula.addValue(Double.valueOf(5.0));
      formula.addValue(Double.valueOf(5.0));
      // Only one distinct value, so n=2 cannot be satisfied
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double) — primitive overload
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_normalValues_worksCorrectly() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(4.0d);
      formula.addValue(8.0d);
      formula.addValue(2.0d);
      assertEquals(8.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueDouble_nullSentinel_isIgnored() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueDoubleArray_usesFirstElement() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(new double[]{9.0, 0.0});
      formula.addValue(new double[]{3.0, 0.0});
      assertEquals(9.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_normalValues_worksCorrectly() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(3.0f);
      formula.addValue(7.0f);
      assertEquals(7.0f, ((Number) formula.getResult()).floatValue(), 1e-6f);
   }

   @Test
   void addValueFloat_nullSentinel_isIgnored() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Tool.NULL_FLOAT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_normalValues_returnsLargest() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(100L);
      formula.addValue(200L);
      assertEquals(200L, ((Number) formula.getResult()).longValue());
   }

   @Test
   void addValueLong_nullSentinel_isIgnored() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Tool.NULL_LONG);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_normalValues_returnsLargest() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(5);
      formula.addValue(15);
      formula.addValue(10);
      assertEquals(15, ((Number) formula.getResult()).intValue());
   }

   @Test
   void addValueInt_nullSentinel_isIgnored() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Tool.NULL_INTEGER);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_normalValues_returnsLargest() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue((short) 10);
      formula.addValue((short) 3);
      assertEquals((short) 10, ((Number) formula.getResult()).shortValue());
   }

   @Test
   void addValueShort_nullSentinel_isIgnored() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Tool.NULL_SHORT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getDoubleResult()
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_noValues_returnsZero() {
      NthLargestFormula formula = new NthLargestFormula(1);
      assertEquals(0.0, formula.getDoubleResult(), 1e-10);
   }

   @Test
   void getDoubleResult_withValues_returnsNthLargestAsDouble() {
      NthLargestFormula formula = new NthLargestFormula(2);
      formula.addValue(5.0d);
      formula.addValue(3.0d);
      formula.addValue(7.0d);
      assertEquals(5.0, formula.getDoubleResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsAllValues() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Double.valueOf(10.0));
      formula.reset();
      assertTrue(formula.isNull());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Double.valueOf(100.0));
      formula.reset();
      formula.addValue(Double.valueOf(7.0));
      assertEquals(7.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // clone()
   // -----------------------------------------------------------------------

   @Test
   void clone_copiesAccumulatedValues() {
      NthLargestFormula formula = new NthLargestFormula(2);
      formula.addValue(Double.valueOf(5.0));
      formula.addValue(Double.valueOf(3.0));
      formula.addValue(Double.valueOf(7.0));
      NthLargestFormula cloned = (NthLargestFormula) formula.clone();
      assertEquals(5.0, (Double) cloned.getResult(), 1e-10);
   }

   @Test
   void clone_preservesDefaultResult() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.setDefaultResult(true);
      NthLargestFormula cloned = (NthLargestFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   @Test
   void clone_isIndependentOfOriginal() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue(Double.valueOf(5.0));
      NthLargestFormula cloned = (NthLargestFormula) formula.clone();
      formula.reset();
      // cloned still has the value
      assertFalse(cloned.isNull());
      assertEquals(5.0, (Double) cloned.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // invalid n clamps to 1
   // -----------------------------------------------------------------------

   @Test
   void constructor_invalidNZero_clampsToOne() {
      NthLargestFormula formula = new NthLargestFormula(0);
      formula.addValue(Double.valueOf(42.0));
      assertEquals(42.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void constructor_invalidNNegative_clampsToOne() {
      NthLargestFormula formula = new NthLargestFormula(-5);
      formula.addValue(Double.valueOf(99.0));
      assertEquals(99.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // defaultResult / setDefaultResult
   // -----------------------------------------------------------------------

   @Test
   void setDefaultResult_trueAndFalse_roundTrip() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
      formula.setDefaultResult(false);
      assertFalse(formula.isDefaultResult());
   }

   // -----------------------------------------------------------------------
   // blank strings (Bug #80888)
   // -----------------------------------------------------------------------

   private static final String[][] BLANK_INPUTS = {
      { "", "1", "3" }, { "1", "", "3" }, { "1", "3", "" },
      { " ", "1", "3" }, { "1", " ", "3" }, { "1", "3", " " } };

   @Test
   void addValue_blankStringInAnyPosition_skipped() {
      Object[] expected = { "3", "1", null };

      for(String[] values : BLANK_INPUTS) {
         for(int n = 1; n <= 3; n++) {
            NthLargestFormula formula = new NthLargestFormula(n);

            for(String value : values) {
               formula.addValue(value);
            }

            assertEquals(expected[n - 1], formula.getResult(),
                         "n=" + n + " " + String.join("|", values));
         }
      }
   }

   @Test
   void addValue_onlyBlankStrings_isNull() {
      NthLargestFormula formula = new NthLargestFormula(1);
      formula.addValue("");
      formula.addValue(" ");
      assertTrue(formula.isNull());
   }

   @Test
   void addValue_blankString_parseNumberOff_notSkipped() {
      String old = SreeEnv.getProperty("formula.parse.number");

      try {
         SreeEnv.setProperty("formula.parse.number", "false");
         NthLargestFormula formula = new NthLargestFormula(1);
         formula.addValue("");
         assertEquals("", formula.getResult());
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

   // Bug #80738: Nth Largest skips a blank string like Sum
   @Test
   void addValue_blankStrings_skippedForDoubleResultDefaultAndText() {
      NthLargestFormula second = new NthLargestFormula(2);
      addAll(second, "", "5", " ", "1", "  ", "9", "");
      assertEquals("5", second.getResult());
      assertEquals(5.0, second.getDoubleResult(), 1e-9);

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

         int n = 1 + random.nextInt(6);
         NthLargestFormula largest = new NthLargestFormula(n);
         NthLargestFormula largestNoBlanks = new NthLargestFormula(n);

         for(Object v : values) {
            largest.addValue(v);
         }

         for(Object v : noBlanks) {
            largestNoBlanks.addValue(v);
         }

         String msg = values + " n=" + n;
         assertEquals(largestNoBlanks.getResult(), largest.getResult(), msg);

         // a list with no blank gives what the algorithm before the fix gives, copied here
         assertEquals(baseNth(noBlanks, n, false), largest.getResult(), msg);
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
         NthLargestFormula largest = new NthLargestFormula(2);
         NthLargestFormula largestBlank = new NthLargestFormula(2);
         addAll(largest, values);
         addAll(largestBlank, withBlank);
         assertEquals(largest.getResult(), largestBlank.getResult(), Arrays.toString(values));
      }
   }

   // -----------------------------------------------------------------------
   // distinct values, dates, text, N out of range
   // -----------------------------------------------------------------------

   // gap:report.summary-formulas.min-max#nth-largest-distinct-values
   @Test
   void getResult_valuesWithDuplicates_ranksDistinctValues() {
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

   // gap:report.summary-formulas.min-max#nth-largest-distinct-values
   @Test
   void getResult_dates_ranksChronologically() {
      // a null value is not a date to rank
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
   }

   // gap:report.summary-formulas.min-max#nth-text-values
   @Test
   void getResult_textAndNumericStrings_ranked() {
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

      // text orders as text
      NthLargestFormula text = new NthLargestFormula(1);
      addAll(text, "kiwi", "apple", "pear");
      assertEquals("pear", text.getResult());

      NthLargestFormula text2 = new NthLargestFormula(2);
      addAll(text2, "kiwi", "apple", "pear");
      assertEquals("kiwi", text2.getResult());

      // the old suite case: text and digits mixed
      NthLargestFormula mixed = new NthLargestFormula(1);
      addAll(mixed, "a", "A", "3", "7", "", null);
      assertEquals("a", mixed.getResult());
   }

   // gap:report.summary-formulas.min-max#nth-n-below-one-defaults-to-one
   @Test
   void constructor_nBelowOne_ranksLikeOne() {
      int[] counts = { 0, -3 };

      for(int n : counts) {
         NthLargestFormula largest = new NthLargestFormula(n);
         addAll(largest, 4, 9, 2, 9);
         assertEquals(9, largest.getResult(), "largest n=" + n);
      }

      // the same as an explicit 1
      NthLargestFormula one = new NthLargestFormula(1);
      addAll(one, 4, 9, 2, 9);
      assertEquals(9, one.getResult());
   }

   // Nth formulas return null for an N beyond the distinct count
   @Test
   void getDoubleResult_nBeyondDistinctCount_returnsZero() {
      NthLargestFormula largest = new NthLargestFormula(3);
      addAll(largest, 1, 2, 2);
      assertNull(largest.getResult());
      assertEquals(0.0, largest.getDoubleResult());
   }

   private static void addAll(Formula formula, Object... values) {
      for(Object value : values) {
         formula.addValue(value);
      }
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
