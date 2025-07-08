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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompositePopulationVarianceFormulaTest {

   private CompositePopulationVarianceFormula formula;

   @BeforeEach
   void setUp() {
      formula = new CompositePopulationVarianceFormula();
   }

   @Test
   void testGetResult() {
      formula.addValue(new double[]{10, 3, 5});
      Object result = formula.getResult();
      assertNotNull(result);
      assertEquals(0.05, ((Number) result).doubleValue());
      assertEquals(0.05, formula.getDoubleResult());

      formula.reset();
      assertEquals(0.0, formula.getResult());
      assertEquals(0.0, formula.getDoubleResult());
   }

   @Test
   void testReset() {
      assertTrue(formula.isNull());
      formula.addValue(new double[]{10, 3, 5});
      assertFalse(formula.isNull());
      formula.reset();
      assertTrue(formula.isNull());
      assertEquals(0.0, formula.getDoubleResult());
   }

   @Test
   void testSetDefaultResult() {
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
      assertEquals(0.0, formula.getResult());

      formula.setDefaultResult(false);
      assertFalse(formula.isDefaultResult());
      assertNull(formula.getResult());
   }

   @Test
   void testClone() {
      CompositePopulationVarianceFormula clonedFormula = (CompositePopulationVarianceFormula) formula.clone();
      assertNotNull(clonedFormula);
      assertEquals(formula.getResultType(), clonedFormula.getResultType());
   }

   @Test
   void testGetResultType() {
      assertEquals(Double.class, formula.getResultType());
   }
}