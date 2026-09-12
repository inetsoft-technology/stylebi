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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NoneFormula.
 *
 * <p>NoneFormula is a pass-through — it stores the last-added value and returns it.
 * Each addValue overload stores in its own typed slot. getResult() prioritises
 * in the order: double, float, long, int, short, Object. Null sentinels are treated
 * as valid values (no special-casing), except for addValue(short) which ignores
 * Tool.NULL_SHORT.
 */
public class NoneFormulaTest {

   private NoneFormula formula;

   @BeforeEach
   void setUp() {
      formula = new NoneFormula();
   }

   // -----------------------------------------------------------------------
   // isNull() — true when getResult() returns null
   // -----------------------------------------------------------------------

   @Test
   void isNull_noValuesAdded_returnsTrue() {
      assertTrue(formula.isNull());
   }

   @Test
   void isNull_afterAddObjectValue_returnsFalse() {
      formula.addValue("hello");
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_afterAddNullObject_remainsTrue() {
      // addValue(Object) stores null; getResult() returns null when val==null
      // and no numeric slot is set
      formula.addValue((Object) null);
      assertTrue(formula.isNull());
   }

   @Test
   void isNull_afterAddDouble_returnsFalse() {
      formula.addValue(1.0);
      assertFalse(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — empty (no value added)
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_defaultResultFalse_returnsNull() {
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
   }

   @Test
   void getResult_noValues_defaultResultTrue_returnsZero() {
      formula.setDefaultResult(true);
      assertEquals(0.0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(Object)
   // -----------------------------------------------------------------------

   @Test
   void addValue_object_storedAndReturned() {
      formula.addValue("testValue");
      assertEquals("testValue", formula.getResult());
   }

   @Test
   void addValue_integerObject_storedAndReturned() {
      formula.addValue(Integer.valueOf(99));
      assertEquals(Integer.valueOf(99), formula.getResult());
   }

   @Test
   void addValue_nullObject_resultIsNull() {
      formula.addValue((Object) null);
      assertNull(formula.getResult());
   }

   @Test
   void addValue_overwritesPreviousObjectValue() {
      formula.addValue("first");
      formula.addValue("second");
      assertEquals("second", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double)
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_stored_returnedFromGetResult() {
      formula.addValue(3.14);
      assertEquals(3.14, formula.getResult());
   }

   @Test
   void addValueDouble_overwritesPreviousDouble() {
      formula.addValue(1.0);
      formula.addValue(2.0);
      assertEquals(2.0, formula.getResult());
   }

   @Test
   void addValueDouble_prioritisedOverObjectSlot() {
      formula.addValue("someString");
      formula.addValue(5.5);
      // double slot is checked first in getResult()
      assertEquals(5.5, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double[])
   // -----------------------------------------------------------------------

   @Test
   void addValueDoubleArray_storedInObjectSlot() {
      double[] arr = {1.0, 2.0};
      formula.addValue(arr);
      assertArrayEquals(arr, (double[]) formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_stored_returnedFromGetResult() {
      formula.addValue(2.5f);
      assertEquals(2.5f, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_stored_returnedFromGetResult() {
      formula.addValue(1000L);
      assertEquals(1000L, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_stored_returnedFromGetResult() {
      formula.addValue(42);
      assertEquals(42, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_normalValue_stored_returnedFromGetResult() {
      formula.addValue((short) 7);
      assertEquals((short) 7, formula.getResult());
   }

   @Test
   void addValueShort_nullSentinel_notStored_resultRemainsNull() {
      formula.addValue(Tool.NULL_SHORT);
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // getDoubleResult()
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_afterAddDoubleValue_returnsThatValue() {
      formula.addValue(9.9);
      assertEquals(9.9, formula.getDoubleResult());
   }

   @Test
   void getDoubleResult_noDoubleAdded_returnsNullDoubleSentinel() {
      // dval is Tool.NULL_DOUBLE when nothing set
      assertEquals(Tool.NULL_DOUBLE, formula.getDoubleResult());
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsAllSlots() {
      formula.addValue("something");
      formula.addValue(1.0);
      formula.addValue(2.0f);
      formula.addValue(3L);
      formula.addValue(4);
      formula.addValue((short) 5);
      formula.reset();
      assertTrue(formula.isNull());
      assertNull(formula.getResult());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      formula.addValue(100.0);
      formula.reset();
      formula.addValue("new");
      assertEquals("new", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // clone()
   // -----------------------------------------------------------------------

   @Test
   void clone_returnsNonNullInstance() {
      assertNotNull(formula.clone());
   }

   @Test
   void clone_preservesDefaultResult() {
      formula.setDefaultResult(true);
      NoneFormula cloned = (NoneFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   @Test
   void clone_copiesCurrentState() {
      formula.addValue("state");
      NoneFormula cloned = (NoneFormula) formula.clone();
      assertEquals("state", cloned.getResult());
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
