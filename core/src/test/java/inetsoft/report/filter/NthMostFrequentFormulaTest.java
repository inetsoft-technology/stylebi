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

import inetsoft.util.Tool;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
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
      // null is ignored (Bug #81181): it is neither the most frequent value nor a rank
      NthMostFrequentFormula first = new NthMostFrequentFormula(1);
      NthMostFrequentFormula second = new NthMostFrequentFormula(2);
      addAll(first, null, null, "a");
      addAll(second, null, null, "a");
      assertEquals("a", first.getResult());
      assertNull(second.getResult());
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

      // only nulls: same as no values at all
      NthMostFrequentFormula nulls = new NthMostFrequentFormula(1);
      addAll(nulls, null, null);
      assertNull(nulls.getResult());
      assertTrue(nulls.isNull());
   }

   // Bug #81181: null is ignored, whatever the storage type of the column
   @Test
   void nullIsIgnored() {
      for(int n = 1; n <= 2; n++) {
         NthMostFrequentFormula object = new NthMostFrequentFormula(n);
         addAll(object, null, null, 5);
         NthMostFrequentFormula mode = new NthMostFrequentFormula(n);
         mode.addValue(Tool.NULL_DOUBLE);
         mode.addValue(Tool.NULL_DOUBLE);
         mode.addValue(5.0);

         if(n == 1) {
            assertEquals(5, object.getResult());
            assertEquals(5.0, mode.getResult());
         }
         else {
            assertNull(object.getResult(), "object n=" + n);
            assertNull(mode.getResult(), "primitive n=" + n);
         }
      }

      // 5 x2, 7 x1, null x3: the null does not take a rank
      NthMostFrequentFormula second = new NthMostFrequentFormula(2);
      addAll(second, null, null, null, 5, 5, 7);
      assertEquals(7, second.getResult());

      ModeFormula mode = new ModeFormula();
      addAll(mode, null, null, "x");
      assertEquals("x", mode.getResult());
   }

   @Test
   void primitiveOverloadsIgnoreTheirNullSentinel() {
      NthMostFrequentFormula[] formulas = new NthMostFrequentFormula[6];

      for(int i = 0; i < formulas.length; i++) {
         formulas[i] = new NthMostFrequentFormula(1);
      }

      formulas[0].addValue(Tool.NULL_DOUBLE);
      formulas[0].addValue(new double[] { Tool.NULL_DOUBLE });
      formulas[1].addValue(Tool.NULL_FLOAT);
      formulas[2].addValue(Tool.NULL_LONG);
      formulas[3].addValue(Tool.NULL_INTEGER);
      formulas[4].addValue(Tool.NULL_SHORT);

      for(int i = 0; i < 5; i++) {
         assertTrue(formulas[i].isNull(), "overload " + i);
         assertNull(formulas[i].getResult(), "overload " + i);
      }

      formulas[5].addValue(Tool.NULL_INTEGER);
      formulas[5].addValue(Tool.NULL_INTEGER);
      formulas[5].addValue(3);
      assertEquals(3, formulas[5].getResult());
   }

   // all-null group follows the empty-group behavior, including the default result (Bug #81181)
   @Test
   void allNullGroupIsLikeEmptyGroup() {
      NthMostFrequentFormula nulls = new NthMostFrequentFormula(1);
      nulls.setDefaultResult(true);
      addAll(nulls, null, null);
      assertEquals(0, nulls.getResult());
      assertEquals(0.0, nulls.getDoubleResult());
      assertTrue(nulls.isNull());
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

   @Disabled("Bug #81205, mismatch N1")
   @Test
   @DisplayName("N1: getDoubleResult of a text mode is 0.0, like other non-numeric objects")
   void n1_doubleResultOfText_isZeroWithoutException() {
      NthMostFrequentFormula mode = new NthMostFrequentFormula(1);
      addAll(mode, "abc", "abc", "x");
      assertEquals("abc", mode.getResult());
      assertEquals(0.0, mode.getDoubleResult());
   }

   @Test
   @DisplayName("N2: the count decides before the case of the text")
   void n2_textCase_countDecides() {
      NthMostFrequentFormula mode = new NthMostFrequentFormula(1);
      addAll(mode, "a", "a", "A");
      assertEquals("a", mode.getResult());

      NthMostFrequentFormula mode2 = new NthMostFrequentFormula(1);
      addAll(mode2, "A", "a", "A");
      assertEquals("A", mode2.getResult());
   }

   @Test
   @DisplayName("PIN N2 (current behavior, pending dev #80953): case-different texts with " +
      "equal counts")
   void n2_pin_caseDifferentTie() {
      NthMostFrequentFormula forward = new NthMostFrequentFormula(1);
      NthMostFrequentFormula reverse = new NthMostFrequentFormula(1);
      addAll(forward, "B", "B", "a", "a");
      addAll(reverse, "a", "a", "B", "B");
      assertEquals(N2_TIE, forward.getResult());
      assertEquals(N2_TIE, reverse.getResult());
   }

   private static final String N2_TIE = "B";

   @Test
   @DisplayName("N3: a tie between values of different types does not depend on arrival order")
   void n3_mixedTypeTie_isOrderIndependent() {
      NthMostFrequentFormula first = new NthMostFrequentFormula(1);
      NthMostFrequentFormula second = new NthMostFrequentFormula(1);
      addAll(first, 1, "a");
      addAll(second, "a", 1);
      assertEquals(1, first.getResult());
      assertEquals(1, second.getResult());
   }

   @Test
   @DisplayName("N6: an n beyond the distinct count, even Integer.MAX_VALUE, gives null")
   void n6_hugeN_isNull() {
      NthMostFrequentFormula formula = new NthMostFrequentFormula(Integer.MAX_VALUE);
      addAll(formula, "a");
      assertNull(formula.getResult());
   }

   @Test
   @DisplayName("N7: reset keeps n")
   void n7_resetKeepsN() {
      NthMostFrequentFormula formula = new NthMostFrequentFormula(2);
      addAll(formula, "x", "x", "y");
      formula.reset();
      assertTrue(formula.isNull());
      addAll(formula, "a", "a", "b");
      assertEquals("b", formula.getResult());
   }

   private static void addAll(Formula formula, Object... values) {
      for(Object value : values) {
         formula.addValue(value);
      }
   }
}
