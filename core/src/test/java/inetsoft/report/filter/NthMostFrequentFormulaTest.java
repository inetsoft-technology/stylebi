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
}
