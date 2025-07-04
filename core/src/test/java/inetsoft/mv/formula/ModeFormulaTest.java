/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.mv.formula;

import inetsoft.uql.XConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModeFormulaTest {

   private ModeFormula formula;

   @BeforeEach
   void setUp() {
      formula = new ModeFormula();
   }

   @Test
   void testReset() {
      formula.addValue(1.0);
      assertFalse(formula.isNull());
      formula.reset();
      assertTrue(formula.isNull());
   }

   @Test
   void testAddValueWithDouble() {
      formula.addValue(1.0);
      formula.addValue(1.0);
      formula.addValue(2.0);
      formula.addValue(3.0);
      formula.addValue(3.0);
      formula.addValue(3.0);

      assertFalse(formula.isNull());
      assertEquals(3.0, formula.getResult());
      assertEquals(3.0, formula.getDoubleResult());
   }

   @Test
   void testSetDefaultResult() {
      formula.reset();
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
      assertEquals(0, formula.getResult());
      assertEquals(0.0, formula.getDoubleResult());

      formula.reset();
      formula.setDefaultResult(false);
      assertFalse(formula.isDefaultResult());
      assertNull(formula.getResult());
      assertEquals(0.0, formula.getDoubleResult());
   }

//   @Test
//   void testClone() {
//      formula.addValue(1.0);
//      formula.addValue(2.0);
//
//      ModeFormula clonedFormula = (ModeFormula) formula.clone(); //bug #71786
//      assertNotNull(clonedFormula);
//      assertEquals(formula.getDisplayName(), clonedFormula.getDisplayName());
//      assertEquals(formula.getResult(), clonedFormula.getResult());
//   }

   @Test
   void testGetDisplayName() {
      assertEquals("Mode", formula.getDisplayName());
   }

   @Test
   void testGetName() {
      assertEquals(XConstants.MODE_FORMULA, formula.getName());
   }
}
