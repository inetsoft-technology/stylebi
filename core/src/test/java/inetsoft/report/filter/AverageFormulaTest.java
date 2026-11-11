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

import inetsoft.report.StyleConstants;
import inetsoft.sree.SreeEnv;
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

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AverageFormula.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class AverageFormulaTest {

   private static final double DELTA = 1e-9;
   private static final String KEEP_TYPE = "formula.avg.keep.type";

   private AverageFormula formula;

   @BeforeEach
   void setUp() {
      formula = new AverageFormula();
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
      formula.addValue(10.0);
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_afterAddNullObject_remainsTrue() {
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
   // addValue(Object) — numeric objects
   // -----------------------------------------------------------------------

   @Test
   void addValue_singleIntegerObject_returnsDoubleAverage() {
      formula.addValue(Integer.valueOf(10));
      assertEquals(10.0, formula.getResult());
   }

   @Test
   void addValue_multipleIntegers_returnsCorrectAverage() {
      formula.addValue(Integer.valueOf(10));
      formula.addValue(Integer.valueOf(20));
      formula.addValue(Integer.valueOf(30));
      assertEquals(20.0, formula.getResult());
   }

   @Test
   void addValue_nullObject_skipped_doesNotAffectAverage() {
      formula.addValue(Integer.valueOf(10));
      formula.addValue((Object) null);
      formula.addValue(Integer.valueOf(20));
      assertEquals(15.0, formula.getResult());
   }

   @Test
   void addValue_numericString_parsedAndIncluded() {
      formula.addValue("15");
      assertEquals(15.0, formula.getResult());
   }

   @Test
   void addValue_nonNumericString_ignored() {
      formula.addValue("abc");
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(double)
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_singleValue_returnsItself() {
      formula.addValue(5.0);
      assertEquals(5.0, formula.getResult());
   }

   @Test
   void addValueDouble_multipleValues_returnsCorrectAverage() {
      formula.addValue(4.0);
      formula.addValue(8.0);
      assertEquals(6.0, formula.getResult());
   }

   @Test
   void addValueDouble_nullSentinel_skipped() {
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueDouble_nullMixedWithReal_onlyRealCounted() {
      formula.addValue(Tool.NULL_DOUBLE);
      formula.addValue(10.0);
      formula.addValue(Tool.NULL_DOUBLE);
      assertEquals(10.0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double[])
   // -----------------------------------------------------------------------

   @Test
   void addValueDoubleArray_singleElement_usesFirstElement() {
      formula.addValue(new double[]{6.0});
      assertEquals(6.0, formula.getResult());
   }

   @Test
   void addValueDoubleArray_nullSentinel_skipped() {
      formula.addValue(new double[]{Tool.NULL_DOUBLE});
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_singleValue_returnsCorrectAverage() {
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
   void addValueLong_singleValue_returnsCorrectAverage() {
      formula.addValue(100L);
      assertEquals(100.0, formula.getResult());
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
   void addValueInt_singleValue_returnsCorrectAverage() {
      formula.addValue(7);
      assertEquals(7.0, formula.getResult());
   }

   @Test
   void addValueInt_nullSentinel_skipped() {
      formula.addValue(Tool.NULL_INTEGER);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueInt_multipleValues_returnsCorrectAverage() {
      formula.addValue(2);
      formula.addValue(4);
      formula.addValue(6);
      assertEquals(4.0, formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_singleValue_returnsCorrectAverage() {
      formula.addValue((short) 20);
      assertEquals(20.0, formula.getResult());
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
   void getDoubleResult_afterAddValue_returnsAverage() {
      formula.addValue(10.0);
      formula.addValue(20.0);
      assertEquals(15.0, formula.getDoubleResult());
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsAccumulatedValues() {
      formula.addValue(10.0);
      formula.addValue(20.0);
      formula.reset();
      assertTrue(formula.isNull());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      formula.addValue(100.0);
      formula.reset();
      formula.addValue(5.0);
      assertEquals(5.0, formula.getResult());
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
      AverageFormula cloned = (AverageFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   @Test
   void clone_clonedInstanceIsIndependent() {
      formula.addValue(10.0);
      AverageFormula cloned = (AverageFormula) formula.clone();
      // clone shares the accumulated state (super.clone())
      cloned.reset();
      // original should be unaffected
      assertEquals(10.0, formula.getResult());
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

   // -----------------------------------------------------------------------
   // blank strings are skipped (Bug #80584)
   // -----------------------------------------------------------------------

   @Test
   void addValue_blankAndWhitespaceStrings_areSkipped() {
      formula.addValue("1");
      formula.addValue("");
      formula.addValue("   ");
      formula.addValue((Object) null);
      formula.addValue("abc");
      formula.addValue("3");
      assertEquals(2.0, formula.getDoubleResult(), 0.0001);
   }

   @Test
   void addValue_onlyBlankStrings_isNull() {
      formula.addValue("");
      formula.addValue(" ");
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // mixed inputs, ported from the 13.8 summary formula tests
   // -----------------------------------------------------------------------

   // gap:report.summary-formulas.arithmetic#average-arithmetic-mean
   @Test
   void getResult_integers_averagedAsDoubles() {
      addAll(formula, 1, 2);
      assertEquals(1.5, formula.getResult());
      formula.addValue(6);
      assertEquals(3.0, formula.getResult());

      // numeric strings are averaged the same way
      formula.reset();
      addAll(formula, "1", "2");
      assertEquals(1.5, formula.getResult());
   }

   // gap:report.summary-formulas.arithmetic#average-arithmetic-mean
   @Test
   void addValue_datesTextAndNull_notCounted() {
      addAll(formula, new Date(1_000_000L), new Date(2_000_000L), 10);
      assertEquals(10.0, formula.getResult());

      formula.reset();
      addAll(formula, "4", "abc", null, 8);
      assertEquals(6.0, formula.getResult());
   }

   // gap:report.summary-formulas.arithmetic#average-arithmetic-mean
   @Test
   void addValue_allPrimitiveOverloadsWithNullSentinels_returnsAverage() {
      // the primitive overloads and the double array, and their null markers
      formula.addValue(2.5);
      formula.addValue(1.5f);
      formula.addValue(3L);
      formula.addValue((short) 5);
      formula.addValue(4);
      formula.addValue(new double[] { 9.0, 100.0 });
      formula.addValue(Tool.NULL_DOUBLE);
      formula.addValue(Tool.NULL_FLOAT);
      formula.addValue(Tool.NULL_LONG);
      formula.addValue(Tool.NULL_SHORT);
      formula.addValue(Tool.NULL_INTEGER);
      assertEquals(25.0 / 6, asDouble(formula.getResult()), DELTA);
   }

   // gap:report.summary-formulas.arithmetic#average-arithmetic-mean
   @Test
   void clone_negativeValues_cloneAveragesOnItsOwn() {
      addAll(formula, -4, 2, -10);
      assertEquals(-4.0, formula.getResult());
      Formula copy = (Formula) formula.clone();
      copy.addValue(24);
      assertEquals(3.0, copy.getResult());
      assertEquals(-4.0, formula.getResult());
   }

   // gap:report.summary-formulas.arithmetic#average-keeps-integer-type
   @Test
   void getResult_keepTypeProperty_truncatesIntegerAverage() {
      withProperty(KEEP_TYPE, "true", () -> {
         // integer values truncate to a whole number, toward zero. StyleBI returns the truncated
         // value as a Long (Bug #70172); 13.8 returns it as a Double such as 1.0
         AverageFormula integers = new AverageFormula();
         addAll(integers, 1, 2);
         assertEquals(1L, integers.getResult());

         integers.reset();
         addAll(integers, -1, -2);
         assertEquals(-1L, integers.getResult());

         AverageFormula longs = new AverageFormula();
         addAll(longs, 1L, 2L, 2L);
         assertEquals(1L, longs.getResult());

         AverageFormula shorts = new AverageFormula();
         addAll(shorts, (short) 7, (short) 8);
         assertEquals(7L, shorts.getResult());

         // the primitive overloads keep the type as well
         AverageFormula primitive = new AverageFormula();
         primitive.addValue(1);
         primitive.addValue(2);
         assertEquals(1L, primitive.getResult());
         assertEquals(1.5, primitive.getDoubleResult());

         // mixed types, in either order, are not truncated
         AverageFormula mixed = new AverageFormula();
         addAll(mixed, 1, 2.0);
         assertEquals(1.5, mixed.getResult());

         AverageFormula mixedOtherWay = new AverageFormula();
         addAll(mixedOtherWay, 2.0, 1);
         assertEquals(1.5, mixedOtherWay.getResult());

         AverageFormula mixedIntegers = new AverageFormula();
         addAll(mixedIntegers, 1, 2L);
         assertEquals(1.5, mixedIntegers.getResult());

         // doubles are never truncated
         AverageFormula doubles = new AverageFormula();
         addAll(doubles, 1.0, 2.0);
         assertEquals(1.5, doubles.getResult());

         // a percentage of the total is not truncated
         AverageFormula percentage = new AverageFormula();
         addAll(percentage, 1, 2);
         asPercentage(percentage, 3);
         assertEquals(0.5, asDouble(percentage.getResult()), DELTA);
      });

      // without the property the average of integers has the fraction
      withProperty(KEEP_TYPE, "false", () -> {
         AverageFormula integers = new AverageFormula();
         addAll(integers, 1, 2);
         assertEquals(1.5, integers.getResult());

         AverageFormula primitive = new AverageFormula();
         primitive.addValue(1);
         primitive.addValue(2);
         assertEquals(1.5, primitive.getResult());

         AverageFormula longs = new AverageFormula();
         addAll(longs, 1L, 2L);
         assertEquals(1.5, longs.getResult());
      });
   }

   // gap:report.summary-formulas.arithmetic#average-no-values-is-null
   @Test
   void addValue_nullsInEveryOverload_remainsNull() {
      assertFalse(formula.isDefaultResult());

      // only nulls, in each overload
      addAll(formula, null, null, null, null);
      formula.addValue(Tool.NULL_DOUBLE);
      formula.addValue(Tool.NULL_FLOAT);
      formula.addValue(Tool.NULL_LONG);
      formula.addValue(Tool.NULL_SHORT);
      formula.addValue(Tool.NULL_INTEGER);
      formula.addValue((double[]) null);
      formula.addValue(new double[0]);
      assertTrue(formula.isNull());
      assertNull(formula.getResult());

      // the default-result flag turns null into 0
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
      assertEquals(0.0, formula.getResult());
      assertTrue(formula.isNull());

      // a value ends it, and a reset starts over
      formula.addValue(4);
      assertFalse(formula.isNull());
      assertEquals(4.0, formula.getResult());
      formula.reset();
      assertTrue(formula.isNull());
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
      assertEquals(0.0, formula.getDoubleResult());
   }

   // gap:report.summary-formulas.arithmetic#percentage-negative-total
   @Test
   void getResult_negativePercentageTotal_returnsNegativeShare() {
      addAll(formula, 13.0, null, null, 19.0);
      asPercentage(formula, -100);
      assertEquals(-0.16, asDouble(formula.getResult()), DELTA);
      assertEquals(-0.16, formula.getDoubleResult(), DELTA);
      assertEquals(16.0, formula.getOriginalResult());
   }

   // gap:report.summary-formulas.arithmetic#percentage-total-zero-is-null
   @Test
   void getResult_percentageTotalZero_returnsNull() {
      addAll(formula, 10.0, -11.9, 11.9, -10.0, 10.0, -10.0);
      asPercentage(formula, 0);
      assertNull(formula.getResult());
      assertEquals(0.0, formula.getDoubleResult());
      assertEquals(0.0, asDouble(formula.getOriginalResult()), DELTA);

      // a total of 0.0 is 0 as well, and the default-result flag gives 0 instead of null
      formula.setTotal(0.0);
      assertNull(formula.getResult());
      formula.setDefaultResult(true);
      assertEquals(0.0, formula.getResult());
      formula.setDefaultResult(false);

      // a different total is a real percentage again
      formula.setTotal(10);
      assertNotNull(formula.getResult());
   }

   // gap:report.summary-formulas.common#formula-reused-after-reset
   @Test
   void reset_afterClone_eachCopyKeepsItsOwnValues() {
      Formula fresh = (Formula) formula.clone();
      formula.addValue(4.0);
      formula.addValue(6.0);
      assertFalse(formula.isNull());
      Object first = formula.getResult();
      assertNotNull(first);

      // a clone is its own copy
      Formula copy = (Formula) formula.clone();
      assertEquals(first, copy.getResult());
      copy.addValue(100.0);
      copy.addValue(-50.0);
      assertEquals(first, formula.getResult());
      assertNotEquals(first, copy.getResult());

      // reset starts over, and it can take another group
      formula.reset();
      assertEquals(fresh.isNull(), formula.isNull());
      assertEquals(fresh.getResult(), formula.getResult());
      formula.addValue(4.0);
      formula.addValue(6.0);
      assertEquals(first, formula.getResult());

      // the copy was not reset by it
      assertNotEquals(first, copy.getResult());
   }

   private static void addAll(Formula formula, Object... values) {
      for(Object value : values) {
         formula.addValue(value);
      }
   }

   private static void asPercentage(PercentageFormula formula, Object total) {
      formula.setPercentageType(StyleConstants.PERCENTAGE_OF_GROUP);
      formula.setTotal(total);
   }

   private static double asDouble(Object result) {
      assertInstanceOf(Double.class, result);
      return (Double) result;
   }

   private static void withProperty(String name, String value, Runnable action) {
      String old = SreeEnv.getProperty(name);

      try {
         SreeEnv.setProperty(name, value);
         action.run();
      }
      finally {
         if(old == null) {
            SreeEnv.remove(name);
         }
         else {
            SreeEnv.setProperty(name, old);
         }
      }
   }
}
