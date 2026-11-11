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
 * Tests for VarianceFormula (sample variance, divides by N-1).
 *
 * <p>Known values: for {2, 4, 4, 4, 5, 5, 7, 9}, sample variance = 4.571428...
 * For {2, 4, 4, 4, 5, 5, 7, 9} mean=5, sum of sq deviations=32, sample var=32/7≈4.5714
 */
@Tag("core")
public class VarianceFormulaTest {

   private VarianceFormula formula;

   @BeforeEach
   void setUp() {
      formula = new VarianceFormula();
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
      formula.addValue(1.0d);
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_afterReset_returnsTrue() {
      formula.addValue(1.0d);
      formula.reset();
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — empty / single value
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_defaultResultFalse_returnsNull() {
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
   }

   @Test
   void getResult_noValues_defaultResultTrue_returnsZero() {
      formula.setDefaultResult(true);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_singleValue_returnsZero() {
      // variance with one value: count==1, getRawResult returns 0 (count <= 1 guard)
      formula.addValue(5.0d);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // Known mathematical values — sample variance
   // -----------------------------------------------------------------------

   @Test
   void getResult_twoIdenticalValues_returnsZero() {
      formula.addValue(3.0d);
      formula.addValue(3.0d);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_twoValues_correctSampleVariance() {
      // {1, 3}: mean=2, deviations=1,1, sum sq dev=2, sample var = 2/(2-1) = 2
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_classicExample_correctSampleVariance() {
      // {2, 4, 4, 4, 5, 5, 7, 9}: mean=5, sum sq dev=32, sample var = 32/7
      double[] values = {2, 4, 4, 4, 5, 5, 7, 9};
      for(double v : values) {
         formula.addValue(v);
      }
      double expected = 32.0 / 7.0;
      assertEquals(expected, (Double) formula.getResult(), 1e-6);
   }

   @Test
   void getResult_simpleIntegers_correctSampleVariance() {
      // {1, 2, 3, 4, 5}: mean=3, deviations=-2,-1,0,1,2, sum sq dev=10, sample var=10/4=2.5
      formula.addValue(1.0d);
      formula.addValue(2.0d);
      formula.addValue(3.0d);
      formula.addValue(4.0d);
      formula.addValue(5.0d);
      assertEquals(2.5, (Double) formula.getResult(), 1e-10);
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
      formula.addValue(Integer.valueOf(2));
      formula.addValue(Integer.valueOf(4));
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValue_numericString_isParsedAndAccepted() {
      formula.addValue("2");
      formula.addValue("4");
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValue_nonNumericString_isIgnored() {
      formula.addValue("hello");
      assertTrue(formula.isNull());
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
      formula.addValue(new double[]{2.0, 99.0});
      formula.addValue(new double[]{4.0, 99.0});
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueDoubleArray_nullSentinelInFirstPosition_isIgnored() {
      formula.addValue(new double[]{Tool.NULL_DOUBLE, 99.0});
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_normalValues_computesVariance() {
      formula.addValue(2.0f);
      formula.addValue(4.0f);
      assertEquals(2.0, (Double) formula.getResult(), 1e-5);
   }

   @Test
   void addValueFloat_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_FLOAT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_normalValues_computesVariance() {
      formula.addValue(2L);
      formula.addValue(4L);
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueLong_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_LONG);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_normalValues_computesVariance() {
      formula.addValue(2);
      formula.addValue(4);
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void addValueInt_nullSentinel_isIgnored() {
      formula.addValue(Tool.NULL_INTEGER);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_normalValues_computesVariance() {
      formula.addValue((short) 2);
      formula.addValue((short) 4);
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

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
      assertEquals(2.0, formula.getDoubleResult(), 1e-10);
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
      formula.addValue(1.0d);
      formula.addValue(9.0d);
      formula.reset();
      formula.addValue(2.0d);
      formula.addValue(4.0d);
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // clone()
   // -----------------------------------------------------------------------

   @Test
   void clone_copiesAccumulatedValues() {
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      VarianceFormula cloned = (VarianceFormula) formula.clone();
      assertEquals(2.0, (Double) cloned.getResult(), 1e-10);
   }

   @Test
   void clone_isIndependentOfOriginal() {
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      VarianceFormula cloned = (VarianceFormula) formula.clone();
      formula.reset();
      assertEquals(2.0, (Double) cloned.getResult(), 1e-10);
   }

   @Test
   void clone_preservesDefaultResult() {
      formula.setDefaultResult(true);
      VarianceFormula cloned = (VarianceFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   // -----------------------------------------------------------------------
   // getOriginalResult() and percentage
   // -----------------------------------------------------------------------

   @Test
   void getOriginalResult_noPercentage_matchesGetResult() {
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      assertEquals(formula.getResult(), formula.getOriginalResult());
   }

   @Test
   void setPercentageType_getPercentageType_roundTrip() {
      formula.setPercentageType(1);
      assertEquals(1, formula.getPercentageType());
      formula.setPercentageType(0);
      assertEquals(0, formula.getPercentageType());
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

   // Bug #80974: a blank string is no value, same as null
   @Test
   void blankString_isSkipped() {
      VarianceFormula f = new VarianceFormula();
      for(Object o : new Object[] { 2, "", 4, " ", 6 }) {
         f.addValue(o);
      }
      assertEquals(4.0, ((Number) f.getResult()).doubleValue(), 1e-9);
   }

   @Test
   void allBlank_sameAsAllNull() {
      VarianceFormula blank = new VarianceFormula();
      VarianceFormula nulls = new VarianceFormula();
      blank.setDefaultResult(false);
      nulls.setDefaultResult(false);
      blank.addValue("");
      blank.addValue("");
      nulls.addValue((Object) null);
      nulls.addValue((Object) null);
      assertNull(nulls.getResult());
      assertNull(blank.getResult());
      assertEquals(nulls.isNull(), blank.isNull());
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

   // gap:report.summary-formulas.statistical#variance-sample
   @Test
   void getResult_textSkippedAndOrderIgnored() {
      VarianceFormula variance = new VarianceFormula();

      // text is skipped, the numeric strings count
      addAll(variance, null, "SS", "10", "9");
      assertEquals(0.5, variance.getResult());

      variance.reset();
      addAll(variance, 20, 1, -10, 20, 0.01);
      assertEquals(177.16902, asDouble(variance.getResult()), DELTA);

      // the order of the values does not matter
      variance.reset();
      addAll(variance, 6, 1, 5, 2, 4, 3);
      assertEquals(3.5, variance.getResult());
   }

   // gap:report.summary-formulas.statistical#variance-family-percentage-of-total
   @Test
   void getResult_percentageOfTotal() {
      // the sample variance of 1..6 is 3.5, as a share of a total of 10
      VarianceFormula variance = new VarianceFormula();
      addAll(variance, 1, 2, 3, 4, 5, 6, null, null);
      asPercentage(variance, 10);
      assertEquals(0.35, asDouble(variance.getResult()), DELTA);
      assertEquals(0.35, variance.getDoubleResult(), DELTA);
      assertEquals(3.5, variance.getOriginalResult());
      assertEquals(StyleConstants.PERCENTAGE_OF_GROUP, variance.getPercentageType());

      // a zero or negative total gives no percentage, and the original result is unchanged
      VarianceFormula other = new VarianceFormula();
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

      // zeros against a total of 0
      VarianceFormula zeros = new VarianceFormula();
      addAll(zeros, null, 0, 0, 0, 0, 0, 0, 0, 0, null);
      asPercentage(zeros, 0);
      assertNull(zeros.getResult());
      assertEquals(0.0, zeros.getOriginalResult());

      // a variance of 0 is 0 of any positive total
      VarianceFormula constant = new VarianceFormula();
      addAll(constant, 111, 111, 111, 111);
      asPercentage(constant, 100);
      assertEquals(0.0, constant.getResult());
   }

   // gap:report.summary-formulas.statistical#variance-no-numeric-input-null
   @Test
   void getResult_noNumericInput_isNull() {
      VarianceFormula variance = new VarianceFormula();

      // dates and nulls are not numbers
      addAll(variance, new Date(1_000_000L), new Date(2_000_000L), null);
      assertTrue(variance.isNull());
      assertNull(variance.getResult());

      variance.reset();
      addAll(variance, null, null, null, null);
      assertTrue(variance.isNull());
      assertNull(variance.getResult());

      variance.addValue(Tool.NULL_DOUBLE);
      variance.addValue(Tool.NULL_INTEGER);
      variance.addValue(Tool.NULL_LONG);
      variance.addValue(Tool.NULL_SHORT);
      variance.addValue(Tool.NULL_FLOAT);
      variance.addValue(new double[] { Tool.NULL_DOUBLE });
      addAll(variance, "text", "x1");
      assertTrue(variance.isNull());
      assertNull(variance.getResult());

      // with the default-result flag nothing gives 0 instead of null
      variance.setDefaultResult(true);
      assertEquals(0.0, variance.getResult());
      variance.setDefaultResult(false);

      // a number ends the empty state
      variance.addValue(5);
      assertFalse(variance.isNull());
      assertNotNull(variance.getResult());
   }

   // gap:report.summary-formulas.statistical#variance-single-value-is-zero
   @Test
   void getResult_singleValueOfAnyType_isZero() {
      VarianceFormula variance = new VarianceFormula();

      // the same for another type, after the nulls and the text that are skipped
      addAll(variance, null, "text", 42.5);
      assertEquals(0.0, variance.getResult());

      variance.reset();
      variance.addValue(-7L);
      assertEquals(0.0, variance.getResult());

      // as a percentage it is 0 of a positive total
      asPercentage(variance, 50);
      assertEquals(0.0, asDouble(variance.getResult()));
      assertEquals(0.0, variance.getOriginalResult());

      // two different values have a spread
      variance.addValue(-5L);
      assertTrue(asDouble(variance.getOriginalResult()) > 0);
   }

   // Bug #80868: Variance skips a blank string next to text that is not a number
   @Test
   void blankStringNextToText_isSkipped() {
      VarianceFormula clean = new VarianceFormula();
      addAll(clean, "2", "4", "6");

      // text that is not a number was already skipped, and a blank next to it changes nothing
      VarianceFormula variance = new VarianceFormula();
      addAll(variance, "2", "abc", "", "4", " ", "6");
      assertEquals(clean.getResult(), variance.getResult());
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

         VarianceFormula all = new VarianceFormula();
         VarianceFormula clean = new VarianceFormula();
         values.forEach(all::addValue);
         noBlanks.forEach(clean::addValue);
         assertEquals(clean.getResult(), all.getResult(), String.valueOf(values));

         // a list without blanks gives what the old algorithm did, a plain sample variance of
         // the numbers (numeric text counts, other text and null do not)
         List<Double> numbers = new ArrayList<>();

         for(Object value : noBlanks) {
            if(value instanceof Number) {
               numbers.add(((Number) value).doubleValue());
            }
            else if(value instanceof String && !((String) value).startsWith("x")
               && !((String) value).equals("abc"))
            {
               numbers.add(Double.parseDouble(((String) value).trim()));
            }
         }

         Object expected = null;

         if(numbers.size() == 1) {
            expected = 0.0;
         }
         else if(numbers.size() > 1) {
            double mean = numbers.stream().mapToDouble(d -> d).average().getAsDouble();
            double sum = numbers.stream().mapToDouble(d -> (d - mean) * (d - mean)).sum();
            expected = sum / (numbers.size() - 1);
         }

         if(expected == null) {
            assertTrue(clean.isNull(), noBlanks + "");
         }
         else {
            assertEquals((Double) expected, asDouble(clean.getResult()), 1e-9, noBlanks + "");
         }
      }
   }
}
