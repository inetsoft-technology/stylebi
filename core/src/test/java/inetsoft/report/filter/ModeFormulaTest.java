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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ModeFormula}, the most frequent value.
 */
@Tag("core")
class ModeFormulaTest {
   private static void addAll(Formula formula, Object... values) {
      for(Object value : values) {
         formula.addValue(value);
      }
   }

   // gap:report.summary-formulas.statistical#mode-most-frequent
   @Test
   void getResult_mostFrequentValue() {
      ModeFormula mode = new ModeFormula();
      addAll(mode, "this", "that", "what", "it", "it");
      assertEquals("it", mode.getResult());

      mode.reset();
      addAll(mode, 3, 3, 3, 3, 3, 4, 4, 4, 4, 1, 1, 1, -1);
      assertEquals(3, mode.getResult());
      assertEquals(3.0, mode.getDoubleResult());

      // the frequency wins over the position and the value
      mode.reset();
      addAll(mode, 9, 1, 1, 9, 9, 5, 5);
      assertEquals(9, mode.getResult());

      Date early = new Date(1_000_000L);
      Date late = new Date(2_000_000L);
      mode.reset();
      addAll(mode, early, early, late, "", null);
      assertEquals(early, mode.getResult());

      // a tie returns the smaller value, whatever the order of the input
      mode.reset();
      addAll(mode, 7, 2, 7, 2, 5);
      assertEquals(2, mode.getResult());
      mode.reset();
      addAll(mode, 2, 7, 2, 7, 5);
      assertEquals(2, mode.getResult());
      mode.reset();
      addAll(mode, "pear", "apple", "pear", "apple");
      assertEquals("apple", mode.getResult());

      // the primitive overloads
      mode.reset();
      mode.addValue(4);
      mode.addValue(6);
      mode.addValue(6);
      mode.addValue(4L);
      mode.addValue(2.5);
      assertEquals(6, mode.getResult());
      assertFalse(mode.isNull());

      // a clone counts the same values and is independent of the original
      Formula copy = (Formula) mode.clone();
      assertEquals(6, copy.getResult());
      copy.addValue(4);
      copy.addValue(4);
      copy.addValue(4);
      assertEquals(4, copy.getResult());
      assertEquals(6, mode.getResult());
   }

   // gap:report.summary-formulas.statistical#mode-no-values-null
   @Test
   void getResult_noValues_isNull() {
      ModeFormula mode = new ModeFormula();
      assertTrue(mode.isNull());
      assertNull(mode.getResult());
      assertEquals(0.0, mode.getDoubleResult());

      mode.setDefaultResult(true);
      assertEquals(0, mode.getResult());

      // after a reset the formula is empty again
      mode.setDefaultResult(false);
      addAll(mode, "a", "a", "b");
      assertEquals("a", mode.getResult());
      mode.reset();
      assertTrue(mode.isNull());
      assertNull(mode.getResult());
   }

   @Disabled("Bug #81205: ModeFormula.clone() returns a NthMostFrequentFormula")
   @Test
   @DisplayName("The clone of a Mode formula is a Mode formula")
   void clone_keepsTheFormulaType() {
      ModeFormula mode = new ModeFormula();
      addAll(mode, "a", "a", "b");
      Object copy = mode.clone();
      assertInstanceOf(ModeFormula.class, copy);
      assertEquals(mode.getName(), ((Formula) copy).getName());
      assertEquals("a", ((Formula) copy).getResult());
   }
}
