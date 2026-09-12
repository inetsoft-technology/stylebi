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
 * Tests for ConcatFormula.
 *
 * <p>getResult() returns null when cnt==0 and def==true (default), empty string when
 * def==false and cnt==0. Otherwise returns the comma-separated string.
 */
public class ConcatFormulaTest {

   private ConcatFormula formula;

   @BeforeEach
   void setUp() {
      formula = new ConcatFormula();
   }

   // -----------------------------------------------------------------------
   // isNull()
   // -----------------------------------------------------------------------

   @Test
   void isNull_noValuesAdded_returnsTrue() {
      assertTrue(formula.isNull());
   }

   @Test
   void isNull_afterAddValue_returnsFalse() {
      formula.addValue("hello");
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_afterAddNullValue_remainsTrue() {
      formula.addValue((Object) null);
      assertTrue(formula.isNull());
   }

   @Test
   void isNull_afterAddNullDouble_remainsTrue() {
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — empty cases
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_defaultResultFalse_returnsEmpty() {
      formula.setDefaultResult(false);
      assertEquals("", formula.getResult());
   }

   @Test
   void getResult_noValues_defaultResultTrue_returnsNull() {
      formula.setDefaultResult(true);
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(Object) — string values
   // -----------------------------------------------------------------------

   @Test
   void addValue_singleString_getResultReturnsThatString() {
      formula.addValue("alpha");
      assertEquals("alpha", formula.getResult());
   }

   @Test
   void addValue_multipleStrings_getResultReturnsCommaSeparated() {
      formula.addValue("a");
      formula.addValue("b");
      formula.addValue("c");
      assertEquals("a,b,c", formula.getResult());
   }

   @Test
   void addValue_nullObject_skipped_resultUnchanged() {
      formula.addValue("a");
      formula.addValue((Object) null);
      formula.addValue("b");
      assertEquals("a,b", formula.getResult());
   }

   @Test
   void addValue_nullOnly_resultIsEmpty() {
      formula.addValue((Object) null);
      formula.setDefaultResult(false);
      assertEquals("", formula.getResult());
   }

   @Test
   void addValue_numericObject_convertedToString() {
      formula.addValue(Integer.valueOf(42));
      assertEquals("42", formula.getResult());
   }

   @Test
   void addValue_doubleObject_convertedToString() {
      formula.addValue(Double.valueOf(3.14));
      assertEquals("3.14", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double)
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_nullDouble_skipped() {
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueDouble_normalValue_appended() {
      formula.addValue(1.5d);
      assertEquals("1.5", formula.getResult());
   }

   @Test
   void addValueDouble_multipleNormalValues_commaSeparated() {
      formula.addValue(1.0d);
      formula.addValue(2.0d);
      assertEquals("1.0,2.0", formula.getResult());
   }

   @Test
   void addValueDouble_nullSentinelMixedWithReal_onlyRealIncluded() {
      formula.addValue(Tool.NULL_DOUBLE);
      formula.addValue(5.0d);
      formula.addValue(Tool.NULL_DOUBLE);
      assertEquals("5.0", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_nullFloat_skipped() {
      formula.addValue(Tool.NULL_FLOAT);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueFloat_normalValue_appended() {
      formula.addValue(2.5f);
      assertEquals("2.5", formula.getResult());
   }

   @Test
   void addValueFloat_nullMixedWithReal_onlyRealIncluded() {
      formula.addValue(Tool.NULL_FLOAT);
      formula.addValue(3.0f);
      assertEquals("3.0", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_nullLong_skipped() {
      formula.addValue(Tool.NULL_LONG);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueLong_normalValue_appended() {
      formula.addValue(100L);
      assertEquals("100", formula.getResult());
   }

   @Test
   void addValueLong_multiple_commaSeparated() {
      formula.addValue(1L);
      formula.addValue(2L);
      assertEquals("1,2", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_nullInteger_skipped() {
      formula.addValue(Tool.NULL_INTEGER);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueInt_normalValue_appended() {
      formula.addValue(7);
      assertEquals("7", formula.getResult());
   }

   @Test
   void addValueInt_multiple_commaSeparated() {
      formula.addValue(10);
      formula.addValue(20);
      formula.addValue(30);
      assertEquals("10,20,30", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_nullShort_skipped() {
      formula.addValue(Tool.NULL_SHORT);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueShort_normalValue_appended() {
      formula.addValue((short) 5);
      assertEquals("5", formula.getResult());
   }

   @Test
   void addValueShort_multiple_commaSeparated() {
      formula.addValue((short) 1);
      formula.addValue((short) 2);
      assertEquals("1,2", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsBufferAndCounter() {
      formula.addValue("x");
      formula.addValue("y");
      assertFalse(formula.isNull());
      formula.reset();
      assertTrue(formula.isNull());
      formula.setDefaultResult(false);
      assertEquals("", formula.getResult());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      formula.addValue("first");
      formula.reset();
      formula.addValue("second");
      assertEquals("second", formula.getResult());
   }

   @Test
   void reset_doubleReset_stillIsNull() {
      formula.addValue("a");
      formula.reset();
      formula.reset();
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // clone()
   // -----------------------------------------------------------------------

   @Test
   void clone_preservesDefaultResult() {
      formula.setDefaultResult(true);
      ConcatFormula cloned = (ConcatFormula) formula.clone();
      assertNotNull(cloned);
      assertTrue(cloned.isDefaultResult());
   }

   @Test
   void clone_doesNotCopyAccumulatedValues() {
      formula.addValue("a");
      formula.addValue("b");
      ConcatFormula cloned = (ConcatFormula) formula.clone();
      // clone starts fresh
      assertTrue(cloned.isNull());
   }

   // -----------------------------------------------------------------------
   // isDefaultResult / setDefaultResult
   // -----------------------------------------------------------------------

   @Test
   void setDefaultResult_trueAndFalse_roundTrip() {
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
      formula.setDefaultResult(false);
      assertFalse(formula.isDefaultResult());
   }

   // -----------------------------------------------------------------------
   // getResultType()
   // -----------------------------------------------------------------------

   @Test
   void getResultType_returnsStringClass() {
      assertEquals(String.class, formula.getResultType());
   }

   // -----------------------------------------------------------------------
   // getDoubleResult() — should throw
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_throwsRuntimeException() {
      assertThrows(RuntimeException.class, () -> formula.getDoubleResult());
   }
}
