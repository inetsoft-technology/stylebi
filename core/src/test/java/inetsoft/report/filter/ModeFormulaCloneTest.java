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

import inetsoft.uql.XConstants;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that cloning a report.filter mode / nth-most-frequent formula keeps its class.
 */
@Tag("core")
class ModeFormulaCloneTest {
   @Test
   void modeCloneKeepsClassAndName() {
      ModeFormula formula = new ModeFormula();
      formula.addValue("a");
      formula.addValue("b");
      formula.addValue("b");

      Object copy = formula.clone();

      assertInstanceOf(ModeFormula.class, copy);
      assertEquals(XConstants.MODE_FORMULA, ((ModeFormula) copy).getName());
      assertEquals("b", ((ModeFormula) copy).getResult());
   }

   @Test
   void nthMostFrequentCloneIsIndependent() {
      NthMostFrequentFormula formula = new NthMostFrequentFormula(2);
      formula.addValue("a");
      formula.addValue("b");
      formula.addValue("b");

      NthMostFrequentFormula copy = (NthMostFrequentFormula) formula.clone();
      assertSame(NthMostFrequentFormula.class, copy.getClass());
      assertEquals("a", copy.getResult());

      copy.addValue("a");
      copy.addValue("a");
      assertEquals("b", copy.getResult());
      assertEquals("a", formula.getResult());
   }
}
