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
 * Tests for FirstFormula.
 *
 * <p>FirstFormula expects Object[] pairs: [measureValue, dimensionValue].
 * It picks the measureValue whose dimensionValue is smallest (first in sort order).
 * addValue(double[]) expects [measureValue, dimensionValue] as doubles.
 * Single primitive addValue overloads throw RuntimeException.
 */
public class FirstFormulaTest {

   private FirstFormula formula;

   @BeforeEach
   void setUp() {
      formula = new FirstFormula();
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
      formula.addValue(new Object[]{"value", "dim"});
      assertFalse(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(Object) — expects Object[] pair
   // -----------------------------------------------------------------------

   @Test
   void addValue_nonArrayObject_ignored() {
      formula.addValue("notAnArray");
      assertTrue(formula.isNull());
   }

   @Test
   void addValue_arrayWithNullFirst_skipped() {
      formula.addValue(new Object[]{null, "dim"});
      assertTrue(formula.isNull());
   }

   @Test
   void addValue_singlePair_returnsMeasureValue() {
      formula.addValue(new Object[]{"measureA", "dim1"});
      assertEquals("measureA", formula.getResult());
   }

   @Test
   void addValue_multiplePairs_returnsFirstByDimension() {
      // pair[0]=measureValue, pair[1]=dimensionValue
      // FirstFormula picks the entry whose dimension is "first" (smallest)
      formula.addValue(new Object[]{"measure2", "dim2"});
      formula.addValue(new Object[]{"measure1", "dim1"});
      formula.addValue(new Object[]{"measure3", "dim3"});
      assertEquals("measure1", formula.getResult());
   }

   @Test
   void addValue_dimensionNull_treatedAsSmallest() {
      formula.addValue(new Object[]{"measureNull", null});
      formula.addValue(new Object[]{"measureA", "dim1"});
      // null dimension compares as first (smallest)
      assertEquals("measureNull", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double[]) — expects [measureDouble, dimensionDouble]
   // -----------------------------------------------------------------------

   @Test
   void addValueDoubleArray_singlePair_returnsMeasureValue() {
      formula.addValue(new double[]{5.0, 1.0});
      assertEquals(5.0, formula.getResult());
   }

   @Test
   void addValueDoubleArray_nullMeasureSentinel_skipped() {
      formula.addValue(new double[]{Tool.NULL_DOUBLE, 1.0});
      assertTrue(formula.isNull());
   }

   @Test
   void addValueDoubleArray_multiplePairs_returnsFirstByDimension() {
      formula.addValue(new double[]{30.0, 3.0});
      formula.addValue(new double[]{10.0, 1.0});
      formula.addValue(new double[]{20.0, 2.0});
      // dimension 1.0 is first
      assertEquals(10.0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // Unsupported primitive addValue overloads throw RuntimeException
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_throwsRuntimeException() {
      assertThrows(RuntimeException.class, () -> formula.addValue(1.0));
   }

   @Test
   void addValueFloat_throwsRuntimeException() {
      assertThrows(RuntimeException.class, () -> formula.addValue(1.0f));
   }

   @Test
   void addValueLong_throwsRuntimeException() {
      assertThrows(RuntimeException.class, () -> formula.addValue(1L));
   }

   @Test
   void addValueInt_throwsRuntimeException() {
      assertThrows(RuntimeException.class, () -> formula.addValue(1));
   }

   @Test
   void addValueShort_throwsRuntimeException() {
      assertThrows(RuntimeException.class, () -> formula.addValue((short) 1));
   }

   // -----------------------------------------------------------------------
   // getDoubleResult()
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_noValues_returnsZero() {
      assertEquals(0.0, formula.getDoubleResult());
   }

   @Test
   void getDoubleResult_afterAddDoubleArrayPair_returnsMeasureValue() {
      formula.addValue(new double[]{42.0, 1.0});
      assertEquals(42.0, formula.getDoubleResult());
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsState() {
      formula.addValue(new Object[]{"v", "d"});
      formula.reset();
      assertTrue(formula.isNull());
      assertNull(formula.getResult());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      formula.addValue(new Object[]{"first", "d1"});
      formula.reset();
      formula.addValue(new Object[]{"second", "d2"});
      assertEquals("second", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // clone()
   // -----------------------------------------------------------------------

   @Test
   void clone_returnsNonNullInstance() {
      assertNotNull(formula.clone());
   }

   @Test
   void clone_preservesCountButClearsValueArrays() {
      // clone() sets dval=null and mval2=null but does NOT reset cnt.
      // So the clone has cnt > 0, meaning isNull() returns false.
      formula.addValue(new Object[]{"val", "dim"});
      FirstFormula cloned = (FirstFormula) formula.clone();
      assertFalse(cloned.isNull());
      // Value arrays are cleared, so getResult() returns null
      assertNull(cloned.getResult());
   }

   @Test
   void clone_preservesDefaultResult() {
      formula.setDefaultResult(true);
      FirstFormula cloned = (FirstFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   // -----------------------------------------------------------------------
   // getSecondaryColumns()
   // -----------------------------------------------------------------------

   @Test
   void getSecondaryColumns_returnsColumnArray() {
      FirstFormula f = new FirstFormula(3);
      assertArrayEquals(new int[]{3}, f.getSecondaryColumns());
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
