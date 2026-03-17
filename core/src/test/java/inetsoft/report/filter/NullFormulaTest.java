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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NullFormula.
 *
 * <p>NullFormula is a no-op formula: all addValue calls are ignored,
 * getResult() always returns null, isNull() always returns true,
 * and getDoubleResult() always throws RuntimeException.
 *
 * <p>NullFormula has package-private visibility, so tests must reside in
 * the same package.
 */
public class NullFormulaTest {

   private NullFormula formula;

   @BeforeEach
   void setUp() {
      formula = new NullFormula();
   }

   // -----------------------------------------------------------------------
   // isNull() — always true
   // -----------------------------------------------------------------------

   @Test
   void isNull_noValues_returnsTrue() {
      assertTrue(formula.isNull());
   }

   @Test
   void isNull_afterAddObjectValue_alwaysTrue() {
      formula.addValue("anything");
      assertTrue(formula.isNull());
   }

   @Test
   void isNull_afterAddDouble_alwaysTrue() {
      formula.addValue(1.0);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — always null
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_alwaysNull() {
      assertNull(formula.getResult());
   }

   @Test
   void getResult_afterAddValues_alwaysNull() {
      formula.addValue("value");
      formula.addValue(42.0);
      formula.addValue(100L);
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // getDoubleResult() — always throws
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_alwaysThrowsRuntimeException() {
      assertThrows(RuntimeException.class, () -> formula.getDoubleResult());
   }

   // -----------------------------------------------------------------------
   // addValue() — all overloads are no-ops (state unchanged)
   // -----------------------------------------------------------------------

   @Test
   void addValue_object_noEffect() {
      formula.addValue("ignored");
      assertNull(formula.getResult());
   }

   @Test
   void addValue_double_noEffect() {
      formula.addValue(99.0);
      assertNull(formula.getResult());
   }

   @Test
   void addValue_doubleArray_noEffect() {
      formula.addValue(new double[]{1.0, 2.0});
      assertNull(formula.getResult());
   }

   @Test
   void addValue_float_noEffect() {
      formula.addValue(1.5f);
      assertNull(formula.getResult());
   }

   @Test
   void addValue_long_noEffect() {
      formula.addValue(123L);
      assertNull(formula.getResult());
   }

   @Test
   void addValue_int_noEffect() {
      formula.addValue(7);
      assertNull(formula.getResult());
   }

   @Test
   void addValue_short_noEffect() {
      formula.addValue((short) 3);
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // reset() — no-op, stays null
   // -----------------------------------------------------------------------

   @Test
   void reset_noEffect_staysNull() {
      formula.reset();
      assertNull(formula.getResult());
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // clone() — returns same instance (reference equality)
   // -----------------------------------------------------------------------

   @Test
   void clone_returnsSameInstance() {
      assertSame(formula, formula.clone());
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
   // getName()
   // -----------------------------------------------------------------------

   @Test
   void getName_returnsNone() {
      assertEquals("none", formula.getName());
   }
}
