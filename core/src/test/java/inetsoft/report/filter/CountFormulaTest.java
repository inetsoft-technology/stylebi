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
 * Tests for CountFormula.
 *
 * <p>CountFormula counts non-null values. isNull() always returns false.
 * getResult() returns the integer count.
 */
public class CountFormulaTest {

   private CountFormula formula;

   @BeforeEach
   void setUp() {
      formula = new CountFormula();
   }

   // -----------------------------------------------------------------------
   // isNull() — CountFormula always returns false
   // -----------------------------------------------------------------------

   @Test
   void isNull_noValuesAdded_alwaysFalse() {
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_afterAddingValues_alwaysFalse() {
      formula.addValue("x");
      assertFalse(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — empty case
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_returnsZero() {
      assertEquals(0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(Object)
   // -----------------------------------------------------------------------

   @Test
   void addValue_singleNonNullObject_countIsOne() {
      formula.addValue("hello");
      assertEquals(1, formula.getResult());
   }

   @Test
   void addValue_multipleObjects_countIsCorrect() {
      formula.addValue("a");
      formula.addValue("b");
      formula.addValue("c");
      assertEquals(3, formula.getResult());
   }

   @Test
   void addValue_nullObject_notCounted() {
      formula.addValue((Object) null);
      assertEquals(0, formula.getResult());
   }

   @Test
   void addValue_mixedNullAndNonNull_onlyNonNullCounted() {
      formula.addValue("a");
      formula.addValue((Object) null);
      formula.addValue("b");
      formula.addValue((Object) null);
      assertEquals(2, formula.getResult());
   }

   @Test
   void addValue_numericObject_counted() {
      formula.addValue(Integer.valueOf(42));
      assertEquals(1, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double)
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_normalValue_counted() {
      formula.addValue(3.14);
      assertEquals(1, formula.getResult());
   }

   @Test
   void addValueDouble_nullSentinel_notCounted() {
      formula.addValue(Tool.NULL_DOUBLE);
      assertEquals(0, formula.getResult());
   }

   @Test
   void addValueDouble_multipleValues_countIsCorrect() {
      formula.addValue(1.0);
      formula.addValue(2.0);
      formula.addValue(3.0);
      assertEquals(3, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double[])
   // -----------------------------------------------------------------------

   @Test
   void addValueDoubleArray_normalValue_counted() {
      formula.addValue(new double[]{5.0});
      assertEquals(1, formula.getResult());
   }

   @Test
   void addValueDoubleArray_nullSentinel_notCounted() {
      formula.addValue(new double[]{Tool.NULL_DOUBLE});
      assertEquals(0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_normalValue_counted() {
      formula.addValue(2.5f);
      assertEquals(1, formula.getResult());
   }

   @Test
   void addValueFloat_nullSentinel_notCounted() {
      formula.addValue(Tool.NULL_FLOAT);
      assertEquals(0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_normalValue_counted() {
      formula.addValue(100L);
      assertEquals(1, formula.getResult());
   }

   @Test
   void addValueLong_nullSentinel_notCounted() {
      formula.addValue(Tool.NULL_LONG);
      assertEquals(0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_normalValue_counted() {
      formula.addValue(7);
      assertEquals(1, formula.getResult());
   }

   @Test
   void addValueInt_nullSentinel_notCounted() {
      formula.addValue(Tool.NULL_INTEGER);
      assertEquals(0, formula.getResult());
   }

   @Test
   void addValueInt_multipleValues_countIsCorrect() {
      formula.addValue(1);
      formula.addValue(2);
      formula.addValue(3);
      assertEquals(3, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_normalValue_counted() {
      formula.addValue((short) 5);
      assertEquals(1, formula.getResult());
   }

   @Test
   void addValueShort_nullSentinel_notCounted() {
      formula.addValue(Tool.NULL_SHORT);
      assertEquals(0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // getDoubleResult()
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_noValues_returnsZero() {
      assertEquals(0.0, formula.getDoubleResult());
   }

   @Test
   void getDoubleResult_afterAddingValues_returnsCount() {
      formula.addValue("a");
      formula.addValue("b");
      assertEquals(2.0, formula.getDoubleResult());
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsCount() {
      formula.addValue("x");
      formula.addValue("y");
      formula.reset();
      assertEquals(0, formula.getResult());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      formula.addValue("first");
      formula.reset();
      formula.addValue("second");
      formula.addValue("third");
      assertEquals(2, formula.getResult());
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
      CountFormula cloned = (CountFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   @Test
   void clone_clonedInstanceIsIndependent() {
      formula.addValue("a");
      CountFormula cloned = (CountFormula) formula.clone();
      cloned.reset();
      assertEquals(1, formula.getResult());
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
   // getResultType()
   // -----------------------------------------------------------------------

   @Test
   void getResultType_returnsIntegerClass() {
      assertEquals(Integer.class, formula.getResultType());
   }
}
