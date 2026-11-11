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

import inetsoft.uql.XConstants;
import inetsoft.util.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FirstFormula.
 *
 * <p>FirstFormula expects Object[] pairs: [measureValue, dimensionValue].
 * It picks the measureValue whose dimensionValue is smallest (first in sort order).
 * addValue(double[]) expects [measureValue, dimensionValue] as doubles.
 * Single primitive addValue overloads throw RuntimeException.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
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

   // -----------------------------------------------------------------------
   // ties, pairs that are skipped, result type
   // -----------------------------------------------------------------------

   // gap:report.summary-formulas.min-max#first-by-second-column
   @Test
   void addValue_tiedDimension_firstRowWins() {
      FirstFormula first = new FirstFormula(1);
      addPairs(first, pair("a", 1), pair("b", 1), pair("c", 2));
      assertEquals("a", first.getResult());

      first.reset();
      assertTrue(first.isNull());
      addPairs(first, pair("a", 5), pair("b", 2), pair("c", 2));
      assertEquals("b", first.getResult());

      // the double array form: {measure, by}
      first.reset();
      first.addValue(new double[] { 10, 3 });
      first.addValue(new double[] { 20, 1 });
      first.addValue(new double[] { 30, 1 });
      assertEquals(20.0, first.getResult());
      assertEquals(20.0, first.getDoubleResult());
   }

   // gap:report.summary-formulas.min-max#first-last-ignore-non-pairs
   @Test
   void addValue_nonPairsAndNullMeasures_skipped() {
      FirstFormula first = new FirstFormula(1);
      first.addValue((Object) null);
      first.addValue("not a pair");
      first.addValue((Object) 7);
      assertTrue(first.isNull());

      // a pair whose measure is null is skipped, even with the smallest By value
      addPairs(first, pair(null, 1), pair(5, 2));
      assertFalse(first.isNull());
      assertEquals(5, first.getResult());

      first.addValue("not a pair");
      first.addValue((Object) null);
      assertEquals(5, first.getResult());

      // the double array form skips a null measure, a null array and an empty array
      FirstFormula doubles = new FirstFormula(1);
      doubles.addValue((double[]) null);
      doubles.addValue(new double[0]);
      doubles.addValue(new double[] { Tool.NULL_DOUBLE, 1 });
      assertTrue(doubles.isNull());
      doubles.addValue(new double[] { 5, 2 });
      assertEquals(5.0, doubles.getResult());
   }

   // gap:report.summary-formulas.common#second-column-formulas-need-pairs
   @Test
   void addValueSingleValue_throwsUnsupportedMethod() {
      FirstFormula pairFormula = new FirstFormula(3);

      // one value alone has no second column to go with it
      for(Runnable single : new Runnable[] {
         () -> pairFormula.addValue(1.0), () -> pairFormula.addValue(1.0f),
         () -> pairFormula.addValue(1L), () -> pairFormula.addValue(1),
         () -> pairFormula.addValue((short) 1)})
      {
         RuntimeException ex = assertThrows(RuntimeException.class, single::run);
         assertEquals("Unsupported method called!", ex.getMessage());
      }
   }

   // gap:report.summary-formulas.common#result-type-defaults-to-input-type
   @Test
   void getResultType_takesTheInputType_returnsNull() {
      assertNull(formula.getResultType());
   }

   // -----------------------------------------------------------------------
   // percentage of total
   // -----------------------------------------------------------------------

   // gap:report.summary-formulas.min-max#first-last-percentage-of-total
   @Test
   void getResult_percentageOfTotal_returnsRatio() {
      FirstFormula first = new FirstFormula(1);
      first.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      first.setTotal(10);
      addPairs(first, pair(5.0, 2), pair(8.0, 1), pair(1.0, 3));
      assertEquals(0.8, first.getResult());
      assertEquals(0.8, first.getDoubleResult());
      assertEquals(8.0, first.getOriginalResult());

      // the total may be a double
      first.setTotal(40.0);
      assertEquals(0.2, first.getResult());

      // a total of 0 and a negative total give 0
      first.setTotal(0);
      assertEquals(0.0, first.getResult());
      first.setTotal(-10);
      assertEquals(0.0, first.getResult());

      // a measure that is not a number gives 0
      FirstFormula text = new FirstFormula(1);
      text.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      text.setTotal(10);
      addPairs(text, pair("a", 1), pair("b", 2));
      assertEquals(0.0, text.getResult());
      assertEquals("a", text.getOriginalResult());
   }

   private static void addPairs(Formula formula, Object[]... pairs) {
      for(Object[] pair : pairs) {
         formula.addValue((Object) pair);
      }
   }

   private static Object[] pair(Object measure, Object by) {
      return new Object[] { measure, by };
   }
}
