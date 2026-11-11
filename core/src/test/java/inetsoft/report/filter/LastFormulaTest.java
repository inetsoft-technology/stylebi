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
 * Tests for LastFormula.
 *
 * <p>LastFormula extends FirstFormula and picks the measureValue whose
 * dimensionValue is last (largest) in sort order. Its compare() reverses the
 * sort direction relative to FirstFormula.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class LastFormulaTest {

   private LastFormula formula;

   @BeforeEach
   void setUp() {
      formula = new LastFormula();
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
   // addValue(Object) — expects Object[] pair [measureValue, dimensionValue]
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
   void addValue_multiplePairs_returnsLastByDimension() {
      // LastFormula picks the entry with the largest dimension value
      formula.addValue(new Object[]{"measure1", "dim1"});
      formula.addValue(new Object[]{"measure2", "dim2"});
      formula.addValue(new Object[]{"measure3", "dim3"});
      assertEquals("measure3", formula.getResult());
   }

   @Test
   void addValue_unorderedInput_returnsLastByDimension() {
      formula.addValue(new Object[]{"measureC", "dim3"});
      formula.addValue(new Object[]{"measureA", "dim1"});
      formula.addValue(new Object[]{"measureB", "dim2"});
      assertEquals("measureC", formula.getResult());
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
   void addValueDoubleArray_multiplePairs_returnsLastByDimension() {
      formula.addValue(new double[]{10.0, 1.0});
      formula.addValue(new double[]{30.0, 3.0});
      formula.addValue(new double[]{20.0, 2.0});
      // dimension 3.0 is last (largest)
      assertEquals(30.0, formula.getResult());
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
   // Difference from FirstFormula: opposite ordering
   // -----------------------------------------------------------------------

   @Test
   void lastVsFirst_sameInput_returnsDifferentResults() {
      Object[] pairA = new Object[]{"measureA", "dim1"};
      Object[] pairB = new Object[]{"measureB", "dim2"};

      FirstFormula first = new FirstFormula();
      first.addValue(pairA);
      first.addValue(pairB);

      formula.addValue(pairA);
      formula.addValue(pairB);

      assertEquals("measureA", first.getResult());
      assertEquals("measureB", formula.getResult());
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
      formula.addValue(new double[]{99.0, 5.0});
      assertEquals(99.0, formula.getDoubleResult());
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
      // clone() sets dval=null and mval2=null but does NOT reset cnt (inherited from FirstFormula).
      // So the clone has cnt > 0, meaning isNull() returns false.
      formula.addValue(new Object[]{"val", "dim"});
      LastFormula cloned = (LastFormula) formula.clone();
      assertFalse(cloned.isNull());
      // Value arrays are cleared, so getResult() returns null
      assertNull(cloned.getResult());
   }

   // -----------------------------------------------------------------------
   // getName()
   // -----------------------------------------------------------------------

   @Test
   void getName_returnsLastFormula() {
      assertEquals("Last", formula.getName());
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
   // ties, pairs that are skipped, secondary column, result type
   // -----------------------------------------------------------------------

   // gap:report.summary-formulas.min-max#last-by-second-column
   @Test
   void addValue_tiedDimension_laterRowWins() {
      LastFormula last = new LastFormula(1);
      addPairs(last, pair("a", 5), pair("b", 5), pair("c", 1));
      assertEquals("b", last.getResult());

      last.reset();
      addPairs(last, pair("a", 1), pair("b", 4), pair("c", 4));
      assertEquals("c", last.getResult());

      // the double array form: {measure, by}
      last.reset();
      last.addValue(new double[] { 10, 9 });
      last.addValue(new double[] { 20, 9 });
      last.addValue(new double[] { 30, 1 });
      assertEquals(20.0, last.getResult());
   }

   // gap:report.summary-formulas.min-max#first-last-ignore-non-pairs
   @Test
   void addValue_nonPairsAndNullMeasures_skipped() {
      LastFormula last = new LastFormula(1);
      last.addValue((Object) null);
      last.addValue("not a pair");
      assertTrue(last.isNull());

      // a pair whose measure is null is skipped, even with the largest By value
      addPairs(last, pair(5, 2), pair(null, 9));
      assertEquals(5, last.getResult());

      // the double array form skips a null measure
      LastFormula lastDoubles = new LastFormula(1);
      lastDoubles.addValue(new double[] { Tool.NULL_DOUBLE, 1 });
      assertTrue(lastDoubles.isNull());
      lastDoubles.addValue(new double[] { 5, 2 });
      lastDoubles.addValue(new double[] { Tool.NULL_DOUBLE, 3 });
      assertEquals(5.0, lastDoubles.getResult());
   }

   // gap:report.summary-formulas.common#second-column-formulas-need-pairs
   @Test
   void addValueSingleValue_throwsUnsupportedMethod() {
      LastFormula pairFormula = new LastFormula(3);
      assertArrayEquals(new int[] { 3 }, pairFormula.getSecondaryColumns());

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
      LastFormula last = new LastFormula(1);
      last.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      last.setTotal(20);
      addPairs(last, pair(5.0, 2), pair(8.0, 1), pair(1.0, 3));
      assertEquals(0.05, last.getResult());
      assertEquals(1.0, last.getOriginalResult());

      // a total of 0 and a negative total give 0
      last.setTotal(0);
      assertEquals(0.0, last.getResult());
      last.setTotal(-20);
      assertEquals(0.0, last.getResult());

      // a measure that is not a number gives 0
      LastFormula lastText = new LastFormula(1);
      lastText.setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      lastText.setTotal(10);
      addPairs(lastText, pair("a", 1), pair("b", 2));
      assertEquals(0.0, lastText.getResult());
      assertEquals("b", lastText.getOriginalResult());
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
