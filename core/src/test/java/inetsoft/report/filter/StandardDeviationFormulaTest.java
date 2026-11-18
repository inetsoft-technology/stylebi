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
import inetsoft.util.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StandardDeviationFormula (sample standard deviation, sqrt of sample variance).
 *
 * <p>Known values:
 * <ul>
 *   <li>{2, 4, 4, 4, 5, 5, 7, 9}: sample variance=32/7, stddev=sqrt(32/7)≈2.1381</li>
 *   <li>{1, 2, 3, 4, 5}: sample variance=2.5, stddev=sqrt(2.5)≈1.5811</li>
 *   <li>{1, 3}: sample variance=2, stddev=sqrt(2)≈1.4142</li>
 * </ul>
 */
@Tag("core")
public class StandardDeviationFormulaTest {

   private StandardDeviationFormula formula;

   @BeforeEach
   void setUp() {
      formula = new StandardDeviationFormula();
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
      formula.addValue(5.0d);
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_afterReset_returnsTrue() {
      formula.addValue(5.0d);
      formula.reset();
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — empty / single value
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_returnsNull() {
      assertNull(formula.getResult());
   }

   @Test
   void getResult_singleValue_returnsZero() {
      // stddev of one value: variance=0, sqrt(0)=0
      formula.addValue(5.0d);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // Known mathematical values — sample standard deviation
   // -----------------------------------------------------------------------

   @Test
   void getResult_twoIdenticalValues_returnsZero() {
      formula.addValue(4.0d);
      formula.addValue(4.0d);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_twoValues_correctSampleStdDev() {
      // {1, 3}: variance=2, stddev=sqrt(2)
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      assertEquals(Math.sqrt(2.0), (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_classicExample_correctSampleStdDev() {
      // {2, 4, 4, 4, 5, 5, 7, 9}: sample var=32/7, stddev=sqrt(32/7)
      double[] values = {2, 4, 4, 4, 5, 5, 7, 9};
      for(double v : values) {
         formula.addValue(v);
      }
      double expected = Math.sqrt(32.0 / 7.0);
      assertEquals(expected, (Double) formula.getResult(), 1e-6);
   }

   @Test
   void getResult_oneToFive_correctSampleStdDev() {
      // {1, 2, 3, 4, 5}: variance=2.5, stddev=sqrt(2.5)
      formula.addValue(1.0d);
      formula.addValue(2.0d);
      formula.addValue(3.0d);
      formula.addValue(4.0d);
      formula.addValue(5.0d);
      assertEquals(Math.sqrt(2.5), (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // stddev = sqrt(variance)
   // -----------------------------------------------------------------------

   @Test
   void getResult_equalsSquareRootOfSampleVariance() {
      double[] values = {3, 7, 1, 9, 5};
      VarianceFormula varianceFormula = new VarianceFormula();
      for(double v : values) {
         formula.addValue(v);
         varianceFormula.addValue(v);
      }
      double variance = (Double) varianceFormula.getResult();
      double stddev = (Double) formula.getResult();
      assertEquals(Math.sqrt(variance), stddev, 1e-10);
   }

   // -----------------------------------------------------------------------
   // getResultType()
   // -----------------------------------------------------------------------

   @Test
   void getResultType_returnsDoubleClass() {
      assertEquals(Double.class, formula.getResultType());
   }

   // -----------------------------------------------------------------------
   // addValue(Object)
   // -----------------------------------------------------------------------

   @Test
   void addValue_nullObject_isIgnored() {
      formula.addValue((Object) null);
      assertTrue(formula.isNull());
   }

   @Test
   void addValue_numericObject_isAccepted() {
      formula.addValue(Integer.valueOf(1));
      formula.addValue(Integer.valueOf(3));
      assertEquals(Math.sqrt(2.0), (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(double)
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueDoubleArray_usesFirstElement() {
      formula.addValue(new double[]{1.0, 99.0});
      formula.addValue(new double[]{3.0, 99.0});
      assertEquals(Math.sqrt(2.0), (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_normalValues_computesStdDev() {
      formula.addValue(1);
      formula.addValue(3);
      assertEquals(Math.sqrt(2.0), (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueInt_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_INTEGER);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_FLOAT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_LONG);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_SHORT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getDoubleResult()
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_matchesGetResult() {
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      assertEquals(Math.sqrt(2.0), formula.getDoubleResult(), 1e-10);
      assertEquals((Double) formula.getResult(), formula.getDoubleResult(), 1e-10);
   }

   @Test
   void getDoubleResult_noValues_returnsZero() {
      assertEquals(0.0, formula.getDoubleResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsAllValues() {
      formula.addValue(1.0d);
      formula.addValue(2.0d);
      formula.reset();
      assertTrue(formula.isNull());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      formula.addValue(100.0d);
      formula.addValue(200.0d);
      formula.reset();
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      assertEquals(Math.sqrt(2.0), (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // clone()
   // -----------------------------------------------------------------------

   @Test
   void clone_returnsStandardDeviationFormulaInstance() {
      Object cloned = formula.clone();
      assertInstanceOf(StandardDeviationFormula.class, cloned);
   }

   @Test
   void clone_copiesAccumulatedValues() {
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      StandardDeviationFormula cloned = (StandardDeviationFormula) formula.clone();
      assertEquals(Math.sqrt(2.0), (Double) cloned.getResult(), 1e-10);
   }

   @Test
   void clone_isIndependentOfOriginal() {
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      StandardDeviationFormula cloned = (StandardDeviationFormula) formula.clone();
      formula.reset();
      assertEquals(Math.sqrt(2.0), (Double) cloned.getResult(), 1e-10);
   }

   @Test
   void clone_preservesDefaultResult() {
      formula.setDefaultResult(true);
      StandardDeviationFormula cloned = (StandardDeviationFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   // -----------------------------------------------------------------------
   // defaultResult / setDefaultResult
   // -----------------------------------------------------------------------

   @Test
   void setDefaultResult_trueAndFalse_roundTrip() {
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
      formula.setDefaultResult(false);
      assertFalse(formula.isDefaultResult());
   }

   @Test
   void blankString_isSkipped_bug80974() {
      StandardDeviationFormula f = new StandardDeviationFormula();
      for(Object o : new Object[] { 2, "", 4, " ", 6 }) {
         f.addValue(o);
      }
      assertEquals(2.0, ((Number) f.getResult()).doubleValue(), 1e-9);
   }

   // -----------------------------------------------------------------------
   // ported from 13.8 (gap:report.summary-formulas.statistical)
   // -----------------------------------------------------------------------

   private static final double DELTA = 1e-9;

   private static void addAll(Formula target, Object... values) {
      for(Object value : values) {
         target.addValue(value);
      }
   }

   private static void asPercentage(PercentageFormula target, Object total) {
      target.setPercentageType(StyleConstants.PERCENTAGE_OF_GROUP);
      target.setTotal(total);
   }

   private static double asDouble(Object result) {
      assertInstanceOf(Double.class, result);
      return (Double) result;
   }

   // gap:report.summary-formulas.statistical#standard-deviation-sample
   @Test
   void getResult_mixedInputTypes_sampleDeviation() {
      StandardDeviationFormula deviation = new StandardDeviationFormula();
      addAll(deviation, 20, 1, -10, 20, 0.01);
      assertEquals(13.31048534051257, asDouble(deviation.getResult()), DELTA);

      // numeric strings are parsed and text is skipped
      deviation.reset();
      addAll(deviation, "-1", "-2", "3", "A", null);
      assertEquals(Math.sqrt(7.0), asDouble(deviation.getResult()), DELTA);

      deviation.reset();
      deviation.addValue(2.5);
      deviation.addValue(2.5f);
      deviation.addValue(1L);
      deviation.addValue(2);
      deviation.addValue((short) 3);
      deviation.addValue(Tool.NULL_DOUBLE);
      deviation.addValue(new double[] { 4.0 });
      assertEquals(1.0, asDouble(deviation.getResult()), DELTA);
   }

   // gap:report.summary-formulas.statistical#variance-family-percentage-of-total
   @Test
   void getResult_percentageOfTotal() {
      StandardDeviationFormula deviation = new StandardDeviationFormula();
      addAll(deviation, "-1", "-2", "3", "A");
      asPercentage(deviation, 20);
      assertEquals(Math.sqrt(7.0) / 20, asDouble(deviation.getResult()), DELTA);
      assertEquals(Math.sqrt(7.0) / 20, deviation.getDoubleResult(), DELTA);
      assertEquals(Math.sqrt(7.0), asDouble(deviation.getOriginalResult()), DELTA);

      // a zero or negative total gives no percentage, and the original result is unchanged
      StandardDeviationFormula other = new StandardDeviationFormula();
      addAll(other, 2, 4, 6, 8);
      asPercentage(other, 0);
      assertNull(other.getResult());
      assertEquals(0.0, other.getDoubleResult());
      assertNotNull(other.getOriginalResult());
      other.setDefaultResult(true);
      assertNull(other.getResult());

      other.setTotal(-100);
      assertNull(other.getResult());
      assertNotNull(other.getOriginalResult());

      // a total that is not a number gives none either, a missing total as well
      other.setTotal("n/a");
      assertNull(other.getResult());
      other.setTotal(null);
      assertNull(other.getResult());

      // a numeric string is a total
      other.setTotal("100");
      assertNotNull(other.getResult());
   }

   // gap:report.summary-formulas.statistical#variance-no-numeric-input-null
   @Test
   void getResult_noNumericInput_isNull() {
      StandardDeviationFormula deviation = new StandardDeviationFormula();

      // dates and nulls are not numbers
      addAll(deviation, new Date(1_000_000L), new Date(2_000_000L), null);
      assertTrue(deviation.isNull());
      assertNull(deviation.getResult());

      deviation.reset();
      addAll(deviation, null, null, null, null);
      assertTrue(deviation.isNull());
      assertNull(deviation.getResult());

      deviation.addValue(Tool.NULL_DOUBLE);
      deviation.addValue(Tool.NULL_INTEGER);
      deviation.addValue(Tool.NULL_LONG);
      deviation.addValue(Tool.NULL_SHORT);
      deviation.addValue(Tool.NULL_FLOAT);
      deviation.addValue(new double[] { Tool.NULL_DOUBLE });
      addAll(deviation, "text", "x1");
      assertTrue(deviation.isNull());
      assertNull(deviation.getResult());

      // with the default-result flag nothing gives 0 instead of null
      deviation.setDefaultResult(true);
      assertEquals(0.0, deviation.getResult());
      deviation.setDefaultResult(false);

      // a number ends the empty state
      deviation.addValue(5);
      assertFalse(deviation.isNull());
      assertNotNull(deviation.getResult());
   }

   // gap:report.summary-formulas.statistical#variance-single-value-is-zero
   @Test
   void getResult_singleValueOfAnyType_isZero() {
      StandardDeviationFormula deviation = new StandardDeviationFormula();

      // the same for another type, after the nulls and the text that are skipped
      addAll(deviation, null, "text", 42.5);
      assertEquals(0.0, deviation.getResult());

      deviation.reset();
      deviation.addValue(-7L);
      assertEquals(0.0, deviation.getResult());

      // as a percentage it is 0 of a positive total
      asPercentage(deviation, 50);
      assertEquals(0.0, asDouble(deviation.getResult()));
      assertEquals(0.0, deviation.getOriginalResult());

      // two different values have a spread
      deviation.addValue(-5L);
      assertTrue(asDouble(deviation.getOriginalResult()) > 0);
   }

   // Bug #80868: Standard Deviation skips a blank string next to text that is not a number
   @Test
   void blankStringNextToText_isSkipped() {
      StandardDeviationFormula clean = new StandardDeviationFormula();
      addAll(clean, "2", "4", "6");

      // text that is not a number was already skipped, and a blank next to it changes nothing
      StandardDeviationFormula deviation = new StandardDeviationFormula();
      addAll(deviation, "2", "abc", "", "4", " ", "6");
      assertEquals(clean.getResult(), deviation.getResult());
   }

   // Bug #80868: blank strings change nothing in the variance family
   @Test
   void blankStrings_changeNothingInRandomLists() {
      Random random = new Random(80868L);
      Object[] pool = { 1, 2.5, -3, 10L, "4", "-7.5", " 8 ", "", "  ", "abc", "x1", null };

      for(int i = 0; i < 400; i++) {
         List<Object> values = new ArrayList<>();
         List<Object> noBlanks = new ArrayList<>();
         int size = random.nextInt(8);

         for(int j = 0; j < size; j++) {
            Object value = pool[random.nextInt(pool.length)];
            values.add(value);

            if(!(value instanceof String) || !((String) value).trim().isEmpty()) {
               noBlanks.add(value);
            }
         }

         StandardDeviationFormula all = new StandardDeviationFormula();
         StandardDeviationFormula clean = new StandardDeviationFormula();
         values.forEach(all::addValue);
         noBlanks.forEach(clean::addValue);
         assertEquals(clean.getResult(), all.getResult(), String.valueOf(values));
      }
   }
}
