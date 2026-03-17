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
 * Tests for MaxFormula.
 */
public class MaxFormulaTest {

   private MaxFormula formula;

   @BeforeEach
   void setUp() {
      formula = new MaxFormula();
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
      formula.addValue(5.0);
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_afterAddNullObject_remainsTrue() {
      formula.addValue((Object) null);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — empty cases
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_defaultResultFalse_returnsNull() {
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
   }

   @Test
   void getResult_noValues_defaultResultTrue_returnsZero() {
      formula.setDefaultResult(true);
      assertEquals(Integer.valueOf(0), formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(Object)
   // -----------------------------------------------------------------------

   @Test
   void addValue_singleObject_returnsItself() {
      formula.addValue(Integer.valueOf(42));
      assertEquals(42, formula.getResult());
   }

   @Test
   void addValue_multipleIntegers_returnsMaximum() {
      formula.addValue(Integer.valueOf(10));
      formula.addValue(Integer.valueOf(50));
      formula.addValue(Integer.valueOf(30));
      assertEquals(50, formula.getResult());
   }

   @Test
   void addValue_nullObject_skipped() {
      formula.addValue(Integer.valueOf(5));
      formula.addValue((Object) null);
      assertEquals(5, formula.getResult());
   }

   @Test
   void addValue_strings_returnsLexicographicMax() {
      formula.addValue("banana");
      formula.addValue("apple");
      formula.addValue("cherry");
      assertEquals("cherry", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double)
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_singleValue_returnsItself() {
      formula.addValue(3.14);
      assertEquals(3.14, formula.getResult());
   }

   @Test
   void addValueDouble_multipleValues_returnsMax() {
      formula.addValue(5.0);
      formula.addValue(2.0);
      formula.addValue(8.0);
      assertEquals(8.0, formula.getResult());
   }

   @Test
   void addValueDouble_nullSentinel_skipped() {
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueDouble_negativeValues_handledCorrectly() {
      formula.addValue(-3.0);
      formula.addValue(-10.0);
      formula.addValue(-1.0);
      assertEquals(-1.0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double[])
   // -----------------------------------------------------------------------

   @Test
   void addValueDoubleArray_singleElement_tracked() {
      formula.addValue(new double[]{7.0});
      assertEquals(7.0, formula.getResult());
   }

   @Test
   void addValueDoubleArray_nullSentinel_skipped() {
      formula.addValue(new double[]{Tool.NULL_DOUBLE});
      assertTrue(formula.isNull());
   }

   @Test
   void addValueDoubleArray_emptyArray_skipped() {
      formula.addValue(new double[]{});
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_multipleValues_returnsMax() {
      formula.addValue(1.5f);
      formula.addValue(4.0f);
      formula.addValue(3.0f);
      assertEquals(4.0f, formula.getResult());
   }

   @Test
   void addValueFloat_nullSentinel_skipped() {
      formula.addValue(Tool.NULL_FLOAT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_multipleValues_returnsMax() {
      formula.addValue(50L);
      formula.addValue(300L);
      formula.addValue(200L);
      assertEquals(300L, formula.getResult());
   }

   @Test
   void addValueLong_nullSentinel_skipped() {
      formula.addValue(Tool.NULL_LONG);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_multipleValues_returnsMax() {
      formula.addValue(5);
      formula.addValue(100);
      formula.addValue(50);
      assertEquals(100, formula.getResult());
   }

   @Test
   void addValueInt_nullSentinel_skipped() {
      formula.addValue(Tool.NULL_INTEGER);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_multipleValues_returnsMax() {
      formula.addValue((short) 3);
      formula.addValue((short) 10);
      formula.addValue((short) 7);
      assertEquals((short) 10, formula.getResult());
   }

   @Test
   void addValueShort_nullSentinel_skipped() {
      formula.addValue(Tool.NULL_SHORT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getDoubleResult()
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_noValues_returnsZero() {
      assertEquals(0.0, formula.getDoubleResult());
   }

   @Test
   void getDoubleResult_afterAddDoubleValues_returnsMax() {
      formula.addValue(2.0);
      formula.addValue(9.0);
      formula.addValue(5.0);
      assertEquals(9.0, formula.getDoubleResult());
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsMaxValue() {
      formula.addValue(100.0);
      formula.reset();
      assertTrue(formula.isNull());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      formula.addValue(100.0);
      formula.reset();
      formula.addValue(10.0);
      formula.addValue(5.0);
      assertEquals(10.0, formula.getResult());
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
      MaxFormula cloned = (MaxFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
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
}
