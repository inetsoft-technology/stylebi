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
 * Tests for DefaultFormula.
 *
 * <p>DefaultFormula stores the last value set per typed slot and returns the
 * first non-sentinel value in priority order: Object, double, float, long,
 * int, short. isNull() is true only when no double value has been set
 * (i.e., dv == Double.MIN_VALUE).
 */
public class DefaultFormulaTest {

   private DefaultFormula formula;

   @BeforeEach
   void setUp() {
      formula = new DefaultFormula();
   }

   // -----------------------------------------------------------------------
   // isNull() — based on whether dv was set
   // -----------------------------------------------------------------------

   @Test
   void isNull_noValuesAdded_returnsTrue() {
      assertTrue(formula.isNull());
   }

   @Test
   void isNull_afterAddDoubleValue_returnsFalse() {
      formula.addValue(1.0);
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_afterAddObjectOnly_returnsTrue() {
      // isNull() only checks dv; adding an Object does not clear the isNull flag
      formula.addValue("text");
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — priority: Object > double > float > long > int > short
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValuesAdded_returnsNull() {
      assertNull(formula.getResult());
   }

   @Test
   void getResult_objectAdded_returnsObject() {
      formula.addValue("hello");
      assertEquals("hello", formula.getResult());
   }

   @Test
   void getResult_doubleAdded_returnsDouble() {
      formula.addValue(3.14);
      assertEquals(3.14, formula.getResult());
   }

   @Test
   void getResult_floatAdded_returnsFloat() {
      formula.addValue(2.5f);
      assertEquals(2.5f, formula.getResult());
   }

   @Test
   void getResult_longAdded_returnsLong() {
      formula.addValue(100L);
      assertEquals(100L, formula.getResult());
   }

   @Test
   void getResult_intAdded_returnsInt() {
      formula.addValue(42);
      assertEquals(42, formula.getResult());
   }

   @Test
   void getResult_shortAdded_returnsShort() {
      formula.addValue((short) 7);
      assertEquals((short) 7, formula.getResult());
   }

   @Test
   void getResult_shortNullSentinel_notStored_returnsNull() {
      formula.addValue(Tool.NULL_SHORT);
      assertNull(formula.getResult());
   }

   @Test
   void getResult_objectPrioritisedOverDouble() {
      // Object slot is checked first in getResult()
      formula.addValue("priority");
      formula.addValue(99.9);
      assertEquals("priority", formula.getResult());
   }

   @Test
   void getResult_doublePrioritisedOverFloat() {
      formula.addValue(5.5);
      formula.addValue(1.1f);
      assertEquals(5.5, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(Object)
   // -----------------------------------------------------------------------

   @Test
   void addValue_object_overwritesPreviousObject() {
      formula.addValue("first");
      formula.addValue("second");
      assertEquals("second", formula.getResult());
   }

   @Test
   void addValue_nullObject_storesNull() {
      formula.addValue("something");
      formula.addValue((Object) null);
      // v is now null, but double was never set; result is null
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double)
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_stored_returnedFromGetResult() {
      formula.addValue(2.71);
      assertEquals(2.71, formula.getResult());
   }

   @Test
   void addValueDouble_overwritesPreviousDouble() {
      formula.addValue(1.0);
      formula.addValue(9.9);
      assertEquals(9.9, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double[])
   // -----------------------------------------------------------------------

   @Test
   void addValueDoubleArray_firstElementStored() {
      formula.addValue(new double[]{6.0, 99.0});
      assertEquals(6.0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_stored_returnedWhenNoDoublePresent() {
      formula.addValue(3.0f);
      assertEquals(3.0f, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_stored_returnedWhenHigherPrioritySlotsEmpty() {
      formula.addValue(500L);
      assertEquals(500L, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_stored_returnedWhenHigherPrioritySlotsEmpty() {
      formula.addValue(77);
      assertEquals(77, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_normalValue_stored() {
      formula.addValue((short) 15);
      assertEquals((short) 15, formula.getResult());
   }

   @Test
   void addValueShort_nullSentinel_notStored() {
      formula.addValue(Tool.NULL_SHORT);
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // getDoubleResult()
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_noDoubleAdded_returnsZero() {
      assertEquals(0.0, formula.getDoubleResult());
   }

   @Test
   void getDoubleResult_afterAddDouble_returnsThatValue() {
      formula.addValue(4.5);
      assertEquals(4.5, formula.getDoubleResult());
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsAllSlots() {
      formula.addValue("text");
      formula.addValue(1.0);
      formula.addValue(2.0f);
      formula.addValue(3L);
      formula.addValue(4);
      formula.addValue((short) 5);
      formula.reset();
      assertNull(formula.getResult());
      assertTrue(formula.isNull());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      formula.addValue(10.0);
      formula.reset();
      formula.addValue(20.0);
      assertEquals(20.0, formula.getResult());
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
      DefaultFormula cloned = (DefaultFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   @Test
   void clone_copiesCurrentState() {
      formula.addValue(7.7);
      DefaultFormula cloned = (DefaultFormula) formula.clone();
      assertEquals(7.7, cloned.getResult());
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
