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
import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NthSmallestFormula.
 *
 * <p>NthSmallestFormula extends NthLargestFormula with an inverted comparator so it tracks the
 * N smallest distinct values instead. n=1 returns the minimum.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class NthSmallestFormulaTest {

   // -----------------------------------------------------------------------
   // isNull()
   // -----------------------------------------------------------------------

   @Test
   void isNull_noValuesAdded_returnsTrue() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      assertTrue(formula.isNull());
   }

   @Test
   void isNull_afterAddValue_returnsFalse() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Double.valueOf(5.0));
      assertFalse(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — no values
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_defaultResultFalse_returnsNull() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
   }

   @Test
   void getResult_noValues_defaultResultTrue_returnsZero() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.setDefaultResult(true);
      // getResult() returns Integer(0) when def=true and no qualifying values
      assertEquals(Integer.valueOf(0), formula.getResult());
   }

   // -----------------------------------------------------------------------
   // n=1: returns the minimum value
   // -----------------------------------------------------------------------

   @Test
   void getResult_n1_singleValue_returnsThatValue() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Double.valueOf(42.0));
      assertEquals(42.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_n1_multipleValues_returnsMin() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Double.valueOf(3.0));
      formula.addValue(Double.valueOf(7.0));
      formula.addValue(Double.valueOf(1.0));
      formula.addValue(Double.valueOf(9.0));
      formula.addValue(Double.valueOf(5.0));
      assertEquals(1.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // n=2: returns the second smallest
   // -----------------------------------------------------------------------

   @Test
   void getResult_n2_multipleValues_returnsSecondSmallest() {
      NthSmallestFormula formula = new NthSmallestFormula(2);
      formula.addValue(Double.valueOf(3.0));
      formula.addValue(Double.valueOf(7.0));
      formula.addValue(Double.valueOf(1.0));
      formula.addValue(Double.valueOf(9.0));
      formula.addValue(Double.valueOf(5.0));
      assertEquals(3.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_n2_fewerThanNDistinctValues_returnsNullWhenDefaultFalse() {
      NthSmallestFormula formula = new NthSmallestFormula(2);
      formula.setDefaultResult(false);
      formula.addValue(Double.valueOf(5.0));
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // n=3: returns the third smallest
   // -----------------------------------------------------------------------

   @Test
   void getResult_n3_fiveValues_returnsThirdSmallest() {
      NthSmallestFormula formula = new NthSmallestFormula(3);
      formula.addValue(Double.valueOf(10.0));
      formula.addValue(Double.valueOf(20.0));
      formula.addValue(Double.valueOf(30.0));
      formula.addValue(Double.valueOf(40.0));
      formula.addValue(Double.valueOf(50.0));
      assertEquals(30.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // Duplicates are not stored
   // -----------------------------------------------------------------------

   @Test
   void addValue_duplicates_onlyOneEntryKept() {
      NthSmallestFormula formula = new NthSmallestFormula(2);
      formula.addValue(Double.valueOf(5.0));
      formula.addValue(Double.valueOf(5.0));
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // Ordering: NthSmallest vs NthLargest should differ for same inputs
   // -----------------------------------------------------------------------

   @Test
   void getResult_n1_smallestAndLargestReturnDifferentValues() {
      NthSmallestFormula smallest = new NthSmallestFormula(1);
      NthLargestFormula largest = new NthLargestFormula(1);
      Double[] values = {Double.valueOf(1.0), Double.valueOf(5.0), Double.valueOf(9.0)};
      for(Double v : values) {
         smallest.addValue(v);
         largest.addValue(v);
      }
      assertEquals(1.0, (Double) smallest.getResult(), 1e-10);
      assertEquals(9.0, (Double) largest.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(double) — primitive overload
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_normalValues_returnsSmallest() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(8.0d);
      formula.addValue(2.0d);
      formula.addValue(5.0d);
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueDouble_nullSentinel_isIgnored() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_normalValues_returnsSmallest() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(30);
      formula.addValue(10);
      formula.addValue(20);
      assertEquals(10, ((Number) formula.getResult()).intValue());
   }

   @Test
   void addValueInt_nullSentinel_isIgnored() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Tool.NULL_INTEGER);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_normalValues_returnsSmallest() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(300L);
      formula.addValue(100L);
      assertEquals(100L, ((Number) formula.getResult()).longValue());
   }

   @Test
   void addValueLong_nullSentinel_isIgnored() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Tool.NULL_LONG);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_normalValues_returnsSmallest() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(9.0f);
      formula.addValue(3.0f);
      assertEquals(3.0f, ((Number) formula.getResult()).floatValue(), 1e-6f);
   }

   @Test
   void addValueFloat_nullSentinel_isIgnored() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Tool.NULL_FLOAT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_normalValues_returnsSmallest() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue((short) 20);
      formula.addValue((short) 5);
      assertEquals((short) 5, ((Number) formula.getResult()).shortValue());
   }

   @Test
   void addValueShort_nullSentinel_isIgnored() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Tool.NULL_SHORT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getDoubleResult()
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_noValues_returnsZero() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      assertEquals(0.0, formula.getDoubleResult(), 1e-10);
   }

   @Test
   void getDoubleResult_withValues_returnsNthSmallestAsDouble() {
      NthSmallestFormula formula = new NthSmallestFormula(2);
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      formula.addValue(5.0d);
      assertEquals(3.0, formula.getDoubleResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsAllValues() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Double.valueOf(10.0));
      formula.reset();
      assertTrue(formula.isNull());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Double.valueOf(100.0));
      formula.reset();
      formula.addValue(Double.valueOf(7.0));
      assertEquals(7.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // clone()
   // -----------------------------------------------------------------------

   @Test
   void clone_returnsNthSmallestFormulaInstance() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue(Double.valueOf(5.0));
      Object cloned = formula.clone();
      assertInstanceOf(NthSmallestFormula.class, cloned);
   }

   @Test
   void clone_copiesAccumulatedValues() {
      NthSmallestFormula formula = new NthSmallestFormula(2);
      formula.addValue(Double.valueOf(1.0));
      formula.addValue(Double.valueOf(3.0));
      formula.addValue(Double.valueOf(5.0));
      NthSmallestFormula cloned = (NthSmallestFormula) formula.clone();
      assertEquals(3.0, (Double) cloned.getResult(), 1e-10);
   }

   @Test
   void clone_preservesDefaultResult() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.setDefaultResult(true);
      NthSmallestFormula cloned = (NthSmallestFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   // -----------------------------------------------------------------------
   // invalid n clamps to 1
   // -----------------------------------------------------------------------

   @Test
   void constructor_invalidNZero_clampsToOne() {
      NthSmallestFormula formula = new NthSmallestFormula(0);
      formula.addValue(Double.valueOf(42.0));
      assertEquals(42.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // blank strings (Bug #80888)
   // -----------------------------------------------------------------------

   @Test
   void addValue_blankStringInAnyPosition_skipped() {
      String[][] inputs = {
         { "", "1", "3" }, { "1", "", "3" }, { "1", "3", "" },
         { " ", "1", "3" }, { "1", " ", "3" }, { "1", "3", " " } };
      Object[] expected = { "1", "3", null };

      for(String[] values : inputs) {
         for(int n = 1; n <= 3; n++) {
            NthSmallestFormula formula = new NthSmallestFormula(n);

            for(String value : values) {
               formula.addValue(value);
            }

            assertEquals(expected[n - 1], formula.getResult(),
                         "n=" + n + " " + String.join("|", values));
         }
      }
   }

   @Test
   void addValue_textWithBlank_returnsFirstNonBlank() {
      NthSmallestFormula formula = new NthSmallestFormula(1);
      formula.addValue("Bob");
      formula.addValue("");
      formula.addValue("Alice");
      assertEquals("Alice", formula.getResult());
   }

   @Test
   void addValue_blankString_parseNumberOff_ranksSmallest() {
      String old = SreeEnv.getProperty("formula.parse.number");

      try {
         SreeEnv.setProperty("formula.parse.number", "false");
         NthSmallestFormula formula = new NthSmallestFormula(1);
         formula.addValue("Bob");
         formula.addValue("");
         formula.addValue("Alice");
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

   // Bug #80738: with numeric parsing off the strings are text and the blank is left alone
   @Test
   void addValue_numericStringsWithBlank_parseNumberOff_ranksBlankSmallest() {
      withProperty("formula.parse.number", "false", () -> {
         NthSmallestFormula textSmallest = new NthSmallestFormula(1);
         addAll(textSmallest, "9", "", "10");
         assertEquals("", textSmallest.getResult());
      });
   }

   // Bug #80738: Nth Smallest skips a blank string like Sum
   @Test
   void addValue_blankStrings_skippedForCloneAndReset() {
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
         NthSmallestFormula smallest = new NthSmallestFormula(n);
         NthSmallestFormula smallestNoBlanks = new NthSmallestFormula(n);

         for(Object v : values) {
            smallest.addValue(v);
         }

         for(Object v : noBlanks) {
            smallestNoBlanks.addValue(v);
         }

         String msg = values + " n=" + n;
         assertEquals(smallestNoBlanks.getResult(), smallest.getResult(), msg);

         // a list with no blank gives what the algorithm before the fix gives, copied here
         assertEquals(baseNth(noBlanks, n, true), smallest.getResult(), msg);
         compared++;
      }

      assertEquals(3000, compared);
   }

   // -----------------------------------------------------------------------
   // distinct values, dates, text, N out of range
   // -----------------------------------------------------------------------

   // gap:report.summary-formulas.min-max#nth-smallest-distinct-values
   @Test
   void getResult_valuesWithDuplicates_ranksDistinctValues() {
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

   // gap:report.summary-formulas.min-max#nth-text-values
   @Test
   void getResult_textAndNumericStrings_ranked() {
      NthSmallestFormula small1 = new NthSmallestFormula(1);
      NthSmallestFormula small2 = new NthSmallestFormula(2);
      addAll(small1, "9", "10");
      addAll(small2, "9", "10");
      assertEquals("9", small1.getResult());
      assertEquals("10", small2.getResult());

      // text orders as text
      NthSmallestFormula textSmall = new NthSmallestFormula(1);
      addAll(textSmall, "kiwi", "apple", "pear");
      assertEquals("apple", textSmall.getResult());
   }

   // gap:report.summary-formulas.min-max#nth-n-below-one-defaults-to-one
   @Test
   void constructor_nBelowOne_ranksLikeOne() {
      int[] counts = { 0, -3 };

      for(int n : counts) {
         NthSmallestFormula smallest = new NthSmallestFormula(n);
         addAll(smallest, 4, 9, 2, 9);
         assertEquals(2, smallest.getResult(), "smallest n=" + n);
      }
   }

   // Nth formulas return null for an N beyond the distinct count
   @Test
   void getResult_nBeyondDistinctCount_nullOrZeroWithDefaultResult() {
      NthSmallestFormula smallest = new NthSmallestFormula(3);
      addAll(smallest, 1, 2, 2);
      assertNull(smallest.getResult());

      // with the default result: 0
      smallest.setDefaultResult(true);
      assertEquals(0, smallest.getResult());
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
