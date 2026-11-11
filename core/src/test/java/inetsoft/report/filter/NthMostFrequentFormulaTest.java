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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NthMostFrequentFormula, in particular that the no-arg constructor (used by
 * Kryo) behaves the same as {@code new NthMostFrequentFormula(1)}.
 */
@Tag("core")
class NthMostFrequentFormulaTest {
   @Test
   void noArgConstructorReturnsMostFrequentValue() {
      NthMostFrequentFormula formula = new NthMostFrequentFormula();
      formula.addValue("a");
      formula.addValue("a");
      formula.addValue("b");

      assertEquals("a", formula.getResult());
   }

   @Test
   void noArgConstructorDoubleResult() {
      NthMostFrequentFormula formula = new NthMostFrequentFormula();
      formula.addValue(2.0);
      formula.addValue(2.0);
      formula.addValue(3.0);

      assertEquals(2.0, formula.getDoubleResult());
   }

   @Test
   void noArgConstructorMatchesIntConstructorOne() {
      NthMostFrequentFormula noArg = new NthMostFrequentFormula();
      NthMostFrequentFormula one = new NthMostFrequentFormula(1);

      for(String v : new String[] { "a", "b", "b", "c", "c", "c" }) {
         noArg.addValue(v);
         one.addValue(v);
      }

      assertEquals(one.getResult(), noArg.getResult());
      assertEquals("c", noArg.getResult());
   }

   @Test
   void noArgConstructorCloneKeepsResult() {
      NthMostFrequentFormula formula = new NthMostFrequentFormula();
      formula.addValue("a");
      formula.addValue("a");
      formula.addValue("b");

      assertEquals("a", ((NthMostFrequentFormula) formula.clone()).getResult());
   }

   @Test
   void secondMostFrequent() {
      NthMostFrequentFormula formula = new NthMostFrequentFormula(2);
      formula.addValue("a");
      formula.addValue("a");
      formula.addValue("b");

      assertEquals("b", formula.getResult());
   }

   @Test
   void invalidNFallsBackToOne() {
      NthMostFrequentFormula formula = new NthMostFrequentFormula(0);
      formula.addValue("a");
      formula.addValue("a");
      formula.addValue("b");

      assertEquals("a", formula.getResult());
   }

   // gap:report.summary-formulas.min-max#nth-most-frequent-by-count
   @Test
   void ranksValuesByCount() {
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

   // gap:report.summary-formulas.min-max#nth-most-frequent-ties-and-null
   @Test
   void tiesAreOrderedByValueAndNullCounts() {
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

   // gap:report.summary-formulas.min-max#nth-n-below-one-defaults-to-one
   @Test
   void nBelowOneRanksLikeOne() {
      int[] counts = { 0, -3 };

      for(int n : counts) {
         NthMostFrequentFormula frequent = new NthMostFrequentFormula(n);
         addAll(frequent, 4, 9, 2, 9);
         assertEquals(9, frequent.getResult(), "frequent n=" + n);
      }
   }

   // Nth formulas return null for an N beyond the distinct count
   @Test
   void nBeyondDistinctCountOrNoValuesIsNull() {
      NthMostFrequentFormula frequent = new NthMostFrequentFormula(3);
      addAll(frequent, 1, 2, 2);
      assertNull(frequent.getResult());

      // no values at all: null, or 0 with the default result
      NthMostFrequentFormula noneFrequent = new NthMostFrequentFormula(1);
      assertNull(noneFrequent.getResult());
      assertTrue(noneFrequent.isNull());
      noneFrequent.setDefaultResult(true);
      assertEquals(0, noneFrequent.getResult());
   }

   // Nth Most Frequent with N too large is null even with the default result (Bug #80488
   // rejected, kept as is)
   @Test
   void nBeyondDistinctCountIsNullEvenWithDefaultResult() {
      // 13.8 Bug #80488 asked for 0 when the default result is on and was rejected; 13.8 keeps
      // that expectation @Disabled, StyleBI pins the behavior it keeps
      NthMostFrequentFormula frequent = new NthMostFrequentFormula(3);
      addAll(frequent, 1, 2, 2);
      assertNull(frequent.getResult());
      frequent.setDefaultResult(true);
      assertNull(frequent.getResult());
   }

   private static void addAll(Formula formula, Object... values) {
      for(Object value : values) {
         formula.addValue(value);
      }
   }
}
