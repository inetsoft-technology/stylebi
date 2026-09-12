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
 * Tests for ProductFormula.
 */
public class ProductFormulaTest {

   private ProductFormula formula;

   @BeforeEach
   void setUp() {
      formula = new ProductFormula();
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
      formula.addValue(2.0);
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
      assertEquals(Double.valueOf(0), formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(Object)
   // -----------------------------------------------------------------------

   @Test
   void addValue_singleInteger_returnsProduct() {
      formula.addValue(Integer.valueOf(5));
      assertEquals(Double.valueOf(5.0), formula.getResult());
   }

   @Test
   void addValue_multipleIntegers_returnsCorrectProduct() {
      formula.addValue(Integer.valueOf(2));
      formula.addValue(Integer.valueOf(3));
      formula.addValue(Integer.valueOf(4));
      assertEquals(Double.valueOf(24.0), formula.getResult());
   }

   @Test
   void addValue_nullObject_skipped() {
      formula.addValue(Integer.valueOf(6));
      formula.addValue((Object) null);
      assertEquals(Double.valueOf(6.0), formula.getResult());
   }

   @Test
   void addValue_numericString_parsedAndMultiplied() {
      formula.addValue("5");
      formula.addValue("4");
      assertEquals(Double.valueOf(20.0), formula.getResult());
   }

   @Test
   void addValue_nonNumericString_ignoredButCountIncremented() {
      // cnt increments before the multiplication; a NumberFormatException is
      // swallowed; the product remains 1 but cnt is 1
      formula.addValue("abc");
      // cnt=1 so not null, product=1
      assertEquals(Double.valueOf(1.0), formula.getResult());
   }

   @Test
   void addValue_includingZero_productIsZero() {
      formula.addValue(Integer.valueOf(5));
      formula.addValue(Integer.valueOf(0));
      formula.addValue(Integer.valueOf(3));
      assertEquals(Double.valueOf(0.0), formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double)
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_singleValue_returnsItself() {
      formula.addValue(7.0);
      assertEquals(Double.valueOf(7.0), formula.getResult());
   }

   @Test
   void addValueDouble_multipleValues_returnsProduct() {
      formula.addValue(2.0);
      formula.addValue(5.0);
      assertEquals(Double.valueOf(10.0), formula.getResult());
   }

   @Test
   void addValueDouble_nullSentinel_skipped() {
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(double[])
   // -----------------------------------------------------------------------

   @Test
   void addValueDoubleArray_singleElement_multiplied() {
      formula.addValue(new double[]{4.0});
      assertEquals(Double.valueOf(4.0), formula.getResult());
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
   void addValueFloat_multiplied() {
      formula.addValue(3.0f);
      assertEquals(3.0, (Double) formula.getResult(), 1e-6);
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
   void addValueLong_multiplied() {
      formula.addValue(2L);
      formula.addValue(10L);
      assertEquals(Double.valueOf(20.0), formula.getResult());
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
   void addValueInt_multiplied() {
      formula.addValue(3);
      formula.addValue(7);
      assertEquals(Double.valueOf(21.0), formula.getResult());
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
   void addValueShort_multiplied() {
      formula.addValue((short) 4);
      formula.addValue((short) 5);
      assertEquals(Double.valueOf(20.0), formula.getResult());
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
   void getDoubleResult_afterAddValues_returnsProduct() {
      formula.addValue(3.0);
      formula.addValue(4.0);
      assertEquals(12.0, formula.getDoubleResult());
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsProduct() {
      formula.addValue(5.0);
      formula.addValue(6.0);
      formula.reset();
      assertTrue(formula.isNull());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      formula.addValue(100.0);
      formula.reset();
      formula.addValue(2.0);
      formula.addValue(3.0);
      assertEquals(Double.valueOf(6.0), formula.getResult());
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
      ProductFormula cloned = (ProductFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   @Test
   void clone_clonedInstanceIsIndependent() {
      formula.addValue(4.0);
      ProductFormula cloned = (ProductFormula) formula.clone();
      cloned.reset();
      assertEquals(Double.valueOf(4.0), formula.getResult());
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
   void getResultType_returnsDoubleClass() {
      assertEquals(Double.class, formula.getResultType());
   }
}
