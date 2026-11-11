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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.Tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Sum, Average, Product and Weighted Average summary formulas, driven directly through the
 * formula API.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SummaryFormulasArithmeticFeatureTest {
   private static final double DELTA = 1e-9;
   private static final String KEEP_TYPE = "formula.avg.keep.type";

   private static void addAll(Formula formula, Object... values) {
      for(Object value : values) {
         formula.addValue(value);
      }
   }

   private static void addPairs(Formula formula, Object[]... pairs) {
      for(Object[] pair : pairs) {
         formula.addValue((Object) pair);
      }
   }

   private static Object[] pair(Object value, Object weight) {
      return new Object[] { value, weight };
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

   @Test
   @DisplayName("gap:report.summary-formulas.arithmetic#average-arithmetic-mean")
   void averageArithmeticMean() {
      AverageFormula average = new AverageFormula();
      addAll(average, 13.0, 19.0);
      assertEquals(16.0, average.getResult());
      assertEquals(16.0, average.getDoubleResult());
      assertFalse(average.isNull());

      // integers are averaged as doubles
      average.reset();
      addAll(average, 1, 2);
      assertEquals(1.5, average.getResult());
      average.addValue(6);
      assertEquals(3.0, average.getResult());

      // numeric strings are numbers, dates and text are not counted
      average.reset();
      addAll(average, "1", "2");
      assertEquals(1.5, average.getResult());
      average.reset();
      addAll(average, new Date(1_000_000L), new Date(2_000_000L), 10);
      assertEquals(10.0, average.getResult());
      average.reset();
      addAll(average, "4", "abc", null, 8);
      assertEquals(6.0, average.getResult());

      // the primitive overloads and the double array, and their null markers
      average.reset();
      average.addValue(2.5);
      average.addValue(1.5f);
      average.addValue(3L);
      average.addValue((short) 5);
      average.addValue(4);
      average.addValue(new double[] { 9.0, 100.0 });
      average.addValue(Tool.NULL_DOUBLE);
      average.addValue(Tool.NULL_FLOAT);
      average.addValue(Tool.NULL_LONG);
      average.addValue(Tool.NULL_SHORT);
      average.addValue(Tool.NULL_INTEGER);
      assertEquals(25.0 / 6, asDouble(average.getResult()), DELTA);

      // negative values and a clone that has its own sum
      average.reset();
      addAll(average, -4, 2, -10);
      assertEquals(-4.0, average.getResult());
      Formula copy = (Formula) average.clone();
      copy.addValue(24);
      assertEquals(3.0, copy.getResult());
      assertEquals(-4.0, average.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.arithmetic#product-multiplies-values")
   void productMultipliesValues() {
      ProductFormula product = new ProductFormula();
      addAll(product, 20, 1, -10, 20, 0.01);
      assertEquals(-40.0, asDouble(product.getResult()), DELTA);
      assertEquals(-40.0, product.getDoubleResult(), DELTA);
      assertFalse(product.isNull());

      product.reset();
      addAll(product, 3, 4);
      assertEquals(12.0, product.getResult());

      // two negative values give a positive product
      product.reset();
      addAll(product, -3, -4, 0.5);
      assertEquals(6.0, product.getResult());

      // a zero makes it zero, and a single value is the value
      product.reset();
      addAll(product, 5, 0, 7);
      assertEquals(0.0, product.getResult());
      product.reset();
      addAll(product, 7.25);
      assertEquals(7.25, product.getResult());
      assertEquals(Double.class, product.getResultType());

      // the primitive overloads and the double array, and their null markers
      product.reset();
      product.addValue(2.0);
      product.addValue(3.0f);
      product.addValue(4L);
      product.addValue((short) 5);
      product.addValue(6);
      product.addValue(new double[] { 7.0, 100.0 });
      product.addValue(Tool.NULL_DOUBLE);
      product.addValue(Tool.NULL_INTEGER);
      assertEquals(5040.0, product.getResult());

      // the product of a clone does not change the original
      Formula copy = (Formula) product.clone();
      copy.addValue(2);
      assertEquals(10080.0, copy.getResult());
      assertEquals(5040.0, product.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.arithmetic#sum-adds-numeric-values")
   void sumAddsNumericValues() {
      SumFormula sum = new SumFormula();
      addAll(sum, 20, 20.01, -30, 10.1);
      assertEquals(20.11, asDouble(sum.getResult()), DELTA);
      assertEquals(20.11, sum.getDoubleResult(), DELTA);
      assertEquals(Double.class, sum.getResultType());
      assertFalse(sum.isNull());

      // integers add up to a Double
      sum.reset();
      addAll(sum, 1, 2, 3);
      assertEquals(6.0, sum.getResult());

      // BigDecimal and Long are numbers
      sum.reset();
      addAll(sum, new BigDecimal("1.25"), 9_000_000_000L, new BigDecimal("-0.25"));
      assertEquals(9_000_000_001.0, sum.getResult());

      // numeric strings are added
      sum.reset();
      addAll(sum, "-10", "20", "0.5");
      assertEquals(10.5, sum.getResult());

      // the primitive overloads and the double array, and their null markers
      sum.reset();
      sum.addValue(1.5);
      sum.addValue(2.5f);
      sum.addValue(3L);
      sum.addValue((short) 4);
      sum.addValue(5);
      sum.addValue(new double[] { 6.0, 100.0 });
      sum.addValue(Tool.NULL_DOUBLE);
      sum.addValue(Tool.NULL_FLOAT);
      sum.addValue(Tool.NULL_LONG);
      sum.addValue(Tool.NULL_SHORT);
      sum.addValue(Tool.NULL_INTEGER);
      sum.addValue(new double[] { Tool.NULL_DOUBLE });
      assertEquals(22.0, sum.getResult());

      // a clone adds on its own
      Formula copy = (Formula) sum.clone();
      copy.addValue(8);
      assertEquals(30.0, copy.getResult());
      assertEquals(22.0, sum.getResult());

      // as a percentage the sum is a share of the total
      sum.reset();
      addAll(sum, 20, 1, 100000, "");
      asPercentage(sum, 100);
      assertEquals(1000.21, asDouble(sum.getResult()), DELTA);
      assertEquals(1000.21, sum.getDoubleResult(), DELTA);
      assertEquals(100021.0, sum.getOriginalResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.arithmetic#weighted-average-weights-second-column")
   void weightedAverageWeightsSecondColumn() {
      WeightedAverageFormula weighted = new WeightedAverageFormula(2);
      assertArrayEquals(new int[] { 2 }, weighted.getSecondaryColumns());
      assertTrue(weighted.isNull());

      // sum(value * weight) / sum(weight)
      addPairs(weighted, pair(-12, 0.5), pair(1, 0.1), pair(20, 0.2), pair(150, 0.01),
               pair(10, 0.19));
      assertEquals(1.5, asDouble(weighted.getResult()), DELTA);
      assertEquals(1.5, weighted.getDoubleResult(), DELTA);
      assertFalse(weighted.isNull());

      // the weight, not the count, decides
      weighted.reset();
      addPairs(weighted, pair(10, 1), pair(20, 3));
      assertEquals(17.5, weighted.getResult());

      // numeric strings in the pair
      weighted.reset();
      addPairs(weighted, pair("10", "1"), pair("20", "3"), pair(30, "4"));
      assertEquals(23.75, weighted.getResult());

      // the double array path: {value, weight}
      weighted.reset();
      weighted.addValue(new double[] { 10.0, 1.0 });
      weighted.addValue(new double[] { 20.0, 3.0 });
      weighted.addValue(new double[] { Tool.NULL_DOUBLE, 5.0 });
      weighted.addValue(new double[] { 99.0, Tool.NULL_DOUBLE });
      assertEquals(17.5, weighted.getResult());
      assertEquals(17.5, weighted.getDoubleResult());

      // a clone keeps the column
      WeightedAverageFormula copy = (WeightedAverageFormula) weighted.clone();
      assertArrayEquals(new int[] { 2 }, copy.getSecondaryColumns());
      assertEquals(17.5, copy.getResult());

      // a value alone is not a pair
      assertThrows(RuntimeException.class, () -> weighted.addValue(5.0));
   }

   @Test
   @DisplayName("gap:report.summary-formulas.arithmetic#average-keeps-integer-type")
   void averageKeepsIntegerType() {
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

   @Test
   @DisplayName("Bug #80540: gap:report.summary-formulas.arithmetic#average-blank-string-gives-nan")
   void averageBlankStringGivesNan() {
      AverageFormula average = new AverageFormula();
      addAll(average, "1", "", null, "a");
      assertEquals(1.0, average.getResult());
      assertFalse(average.isNull());

      average.reset();
      addAll(average, 4, " ", 8);
      assertEquals(6.0, average.getResult());

      // only blanks leave nothing to average
      average.reset();
      addAll(average, "", " ");
      assertTrue(average.isNull());
      assertNull(average.getResult());
   }

   @Test
   @DisplayName("Average of text that is not blank skips the text")
   void averageSkipsText() {
      AverageFormula average = new AverageFormula();
      addAll(average, "1", null, "a", "3");
      assertEquals(2.0, average.getResult());

      average.reset();
      addAll(average, "abc", "x1");
      assertTrue(average.isNull());
      assertNull(average.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.arithmetic#average-no-values-is-null")
   void averageNoValuesIsNull() {
      AverageFormula average = new AverageFormula();
      assertTrue(average.isNull());
      assertNull(average.getResult());
      assertEquals(0.0, average.getDoubleResult());
      assertFalse(average.isDefaultResult());

      // only nulls, in each overload
      addAll(average, null, null, null, null);
      average.addValue(Tool.NULL_DOUBLE);
      average.addValue(Tool.NULL_FLOAT);
      average.addValue(Tool.NULL_LONG);
      average.addValue(Tool.NULL_SHORT);
      average.addValue(Tool.NULL_INTEGER);
      average.addValue((double[]) null);
      average.addValue(new double[0]);
      assertTrue(average.isNull());
      assertNull(average.getResult());

      // the default-result flag turns null into 0
      average.setDefaultResult(true);
      assertTrue(average.isDefaultResult());
      assertEquals(0.0, average.getResult());
      assertTrue(average.isNull());

      // a value ends it, and a reset starts over
      average.addValue(4);
      assertFalse(average.isNull());
      assertEquals(4.0, average.getResult());
      average.reset();
      assertTrue(average.isNull());
      average.setDefaultResult(false);
      assertNull(average.getResult());
      assertEquals(0.0, average.getDoubleResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.arithmetic#percentage-negative-total")
   void percentageNegativeTotal() {
      AverageFormula average = new AverageFormula();
      addAll(average, 13.0, null, null, 19.0);
      asPercentage(average, -100);
      assertEquals(-0.16, asDouble(average.getResult()), DELTA);
      assertEquals(-0.16, average.getDoubleResult(), DELTA);
      assertEquals(16.0, average.getOriginalResult());

      SumFormula sum = new SumFormula();
      addAll(sum, 20, 30);
      asPercentage(sum, -100);
      assertEquals(-0.5, asDouble(sum.getResult()), DELTA);
      assertEquals(-0.5, sum.getDoubleResult(), DELTA);
      assertEquals(50.0, sum.getOriginalResult());

      // a negative sum of a negative total is positive
      sum.reset();
      addAll(sum, -20, -30);
      assertEquals(0.5, asDouble(sum.getResult()), DELTA);

      ProductFormula product = new ProductFormula();
      addAll(product, 3, 4);
      asPercentage(product, -48);
      assertEquals(-0.25, asDouble(product.getResult()), DELTA);
      assertEquals(-0.25, product.getDoubleResult(), DELTA);
      assertEquals(12.0, product.getOriginalResult());

      // the percentage type is kept after the original result is read
      assertEquals(StyleConstants.PERCENTAGE_OF_GROUP, product.getPercentageType());
      assertEquals(-0.25, asDouble(product.getResult()), DELTA);

      // a total that is not a number is not a percentage
      sum.setTotal("100");
      assertEquals(-50.0, sum.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.arithmetic#percentage-total-zero-is-null")
   void percentageTotalZeroIsNull() {
      AverageFormula average = new AverageFormula();
      addAll(average, 10.0, -11.9, 11.9, -10.0, 10.0, -10.0);
      SumFormula sum = new SumFormula();
      addAll(sum, 2, 3);
      ProductFormula product = new ProductFormula();
      addAll(product, 0, 1, 10, null);

      PercentageFormula[] formulas = { average, sum, product };
      double[] originals = { 0.0, 5.0, 0.0 };

      for(int i = 0; i < formulas.length; i++) {
         PercentageFormula formula = formulas[i];
         String name = formula.getName();
         asPercentage(formula, 0);
         assertNull(formula.getResult(), name);
         assertEquals(0.0, formula.getDoubleResult(), name);
         assertEquals(originals[i], asDouble(formula.getOriginalResult()), DELTA, name);

         // a total of 0.0 is 0 as well, and the default-result flag gives 0 instead of null
         formula.setTotal(0.0);
         assertNull(formula.getResult(), name);
         formula.setDefaultResult(true);
         assertEquals(0.0, formula.getResult(), name);
         formula.setDefaultResult(false);

         // a different total is a real percentage again
         formula.setTotal(10);
         assertNotNull(formula.getResult(), name);
      }

      assertEquals(0.5, asDouble(sum.getResult()), DELTA);
   }

   @Test
   @DisplayName("gap:report.summary-formulas.arithmetic#product-skips-text-and-null")
   void productSkipsTextAndNull() {
      ProductFormula product = new ProductFormula();
      addAll(product, "1", "2", "3", "4", "5.5", "a", null);
      assertEquals(132.0, product.getResult());
      assertEquals(132.0, product.getDoubleResult());
      assertFalse(product.isNull());

      // the text and the null do not change the product
      product.reset();
      addAll(product, 2, "abc", null, "x", 3);
      assertEquals(6.0, product.getResult());

      // the text between numbers, with a negative number
      product.reset();
      addAll(product, "-2", "text", 5);
      assertEquals(-10.0, product.getResult());

      // a percentage of the group keeps skipping them
      product.reset();
      addAll(product, 30, 1, -10, 20, 7.2, 0.01, null);
      asPercentage(product, 100);
      assertEquals(-4.32, asDouble(product.getResult()), DELTA);
      assertEquals(-432.0, asDouble(product.getOriginalResult()), DELTA);
   }

   @Test
   @DisplayName("Bug #80540: Product skips a blank string")
   void productBlankString() {
      ProductFormula product = new ProductFormula();
      addAll(product, "2", "", "3");
      assertEquals(6.0, product.getResult());

      product.reset();
      addAll(product, 4, " ", 5);
      assertEquals(20.0, product.getResult());
   }

   @Test
   @DisplayName("Product of text and dates only is 1.0 (Bug #80541 rejected, kept as is)")
   void productOfTextOnly() {
      // ProductFormula.addValue counts a value before it is parsed, so only text or dates give
      // 1.0 and isNull is false, where Sum gives null. 13.8 Bug #80541 asked for null and was
      // rejected; 13.8 keeps that expectation @Disabled, StyleBI pins the behavior it keeps
      ProductFormula product = new ProductFormula();
      addAll(product, "abc", "x1");
      assertFalse(product.isNull());
      assertEquals(1.0, product.getResult());

      product.reset();
      addAll(product, new Date(1_000_000L), new Date(2_000_000L), null);
      assertFalse(product.isNull());
      assertEquals(1.0, product.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.arithmetic#sum-no-numeric-input-is-null")
   void sumNoNumericInputIsNull() {
      SumFormula sum = new SumFormula();
      assertTrue(sum.isNull());
      assertNull(sum.getResult());
      assertEquals(0.0, sum.getDoubleResult());

      // dates and nulls are not numbers
      addAll(sum, new Date(1_000_000L), new Date(2_000_000L), null);
      assertTrue(sum.isNull());
      assertNull(sum.getResult());

      // only nulls, in each overload
      sum.reset();
      addAll(sum, null, null, null, null);
      sum.addValue(Tool.NULL_DOUBLE);
      sum.addValue(Tool.NULL_FLOAT);
      sum.addValue(Tool.NULL_LONG);
      sum.addValue(Tool.NULL_SHORT);
      sum.addValue(Tool.NULL_INTEGER);
      sum.addValue(new double[] { Tool.NULL_DOUBLE });
      assertTrue(sum.isNull());
      assertNull(sum.getResult());

      // the default-result flag turns null into 0
      sum.setDefaultResult(true);
      assertEquals(0.0, sum.getResult());
      assertEquals(0.0, sum.getDoubleResult());
      sum.setDefaultResult(false);

      // a previous total does not leak through a reset
      addAll(sum, 5, 6);
      assertEquals(11.0, sum.getResult());
      sum.reset();
      assertTrue(sum.isNull());
      assertNull(sum.getResult());
      assertEquals(0.0, sum.getDoubleResult());
      addAll(sum, 2);
      assertEquals(2.0, sum.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.arithmetic#sum-skips-text-and-null")
   void sumSkipsTextAndNull() {
      SumFormula sum = new SumFormula();
      addAll(sum, null, "", "abc", "12", 3);
      assertEquals(15.0, sum.getResult());
      assertEquals(15.0, sum.getDoubleResult());
      assertFalse(sum.isNull());

      // only text is nothing
      sum.reset();
      addAll(sum, "", "abc", " ", "x1");
      assertTrue(sum.isNull());
      assertNull(sum.getResult());

      // the numeric strings are added, the text is not
      sum.reset();
      addAll(sum, "FDSFA", "HELLO", "-10", "a", "20");
      assertEquals(10.0, sum.getResult());
      asPercentage(sum, 100);
      assertEquals(0.1, asDouble(sum.getResult()), DELTA);
      assertEquals(10.0, sum.getOriginalResult());

      // text after the numbers does not remove them
      sum = new SumFormula();
      addAll(sum, 4, "oops", "");
      assertEquals(4.0, sum.getResult());
   }

   @Test
   @DisplayName("Bug #80540: gap:report.summary-formulas.arithmetic#weighted-average-blank-text-"
      + "gives-nan")
   void weightedAverageBlankTextGivesNan() {
      WeightedAverageFormula weighted = new WeightedAverageFormula(1);
      addPairs(weighted, pair("", "0.5"), pair("1", "a"), pair("20", "0.2"),
               pair("150", "0.01"), pair("10", "0.19"));
      // the blank pair and the text weight are skipped
      assertEquals(18.5, asDouble(weighted.getResult()), DELTA);

      weighted.reset();
      addPairs(weighted, pair(10, " "), pair(20, 2));
      assertEquals(20.0, weighted.getResult());

      weighted.reset();
      addPairs(weighted, pair(" ", 3), pair("", ""));
      assertTrue(weighted.isNull());
      assertNull(weighted.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.arithmetic#weighted-average-non-numeric-input-null")
   void weightedAverageNonNumericInputNull() {
      WeightedAverageFormula weighted = new WeightedAverageFormula(1);
      assertNull(weighted.getResult());
      assertEquals(0.0, weighted.getDoubleResult());

      // date pairs are not numbers
      addPairs(weighted, pair(new Date(1_000_000L), new Date(2_000_000L)),
               pair(new Date(3_000_000L), new Date(4_000_000L)));
      assertTrue(weighted.isNull());
      assertNull(weighted.getResult());
      assertEquals(0.0, weighted.getDoubleResult());

      // a list of nulls, and nulls inside the pairs, and a null list
      weighted.reset();
      addAll(weighted, null, null, null, null);
      addPairs(weighted, pair(null, null), pair(null, 2), pair(3, null));
      weighted.addValue((double[]) null);
      weighted.addValue(new double[] { Tool.NULL_DOUBLE, Tool.NULL_DOUBLE });
      assertTrue(weighted.isNull());
      assertNull(weighted.getResult());

      // a value that is not a pair is ignored
      weighted.addValue((Object) "text");
      weighted.addValue((Object) 5);
      assertTrue(weighted.isNull());

      // the default-result flag turns null into 0
      weighted.setDefaultResult(true);
      assertEquals(0.0, weighted.getResult());
      weighted.setDefaultResult(false);

      // a pair ends it
      addPairs(weighted, pair(4, 1));
      assertFalse(weighted.isNull());
      assertEquals(4.0, weighted.getResult());
      weighted.reset();
      assertTrue(weighted.isNull());
      assertNull(weighted.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.arithmetic#weighted-average-skips-incomplete-pairs")
   void weightedAverageSkipsIncompletePairs() {
      // the pair with a null value is skipped
      WeightedAverageFormula weighted = new WeightedAverageFormula(1);
      addPairs(weighted, pair(null, 0.5), pair(1, 0.1), pair(20, 0.2), pair(150, 0.01),
               pair(10, 0.19));
      assertEquals(15.0, asDouble(weighted.getResult()), DELTA);

      // a text value, and a null weight
      weighted.reset();
      addPairs(weighted, pair("a", "0.5"), pair("1", null), pair("20", "0.2"),
               pair("150", "0.01"), pair("10", "0.19"));
      assertEquals(18.5, asDouble(weighted.getResult()), DELTA);

      // a text weight
      weighted.reset();
      addPairs(weighted, pair(5, "heavy"), pair(10, 2), pair(20, 2));
      assertEquals(15.0, weighted.getResult());

      // only skipped pairs leave nothing
      weighted.reset();
      addPairs(weighted, pair(null, 1), pair(1, null), pair("a", 1), pair(1, "b"));
      assertTrue(weighted.isNull());
      assertNull(weighted.getResult());

      // the double array form skips the NULL markers
      weighted.reset();
      weighted.addValue(new double[] { Tool.NULL_DOUBLE, 1.0 });
      weighted.addValue(new double[] { 1.0, Tool.NULL_DOUBLE });
      weighted.addValue(new double[] { 6.0, 2.0 });
      assertEquals(6.0, weighted.getResult());
      assertEquals(6.0, weighted.getDoubleResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.arithmetic#weighted-average-zero-weight-sum")
   void weightedAverageZeroWeightSum() {
      WeightedAverageFormula weighted = new WeightedAverageFormula(1);
      addPairs(weighted, pair(5, 0), pair(7, 0));
      assertNull(weighted.getResult());
      assertEquals(0.0, weighted.getDoubleResult());

      // the default-result flag turns null into 0
      weighted.setDefaultResult(true);
      assertEquals(0.0, weighted.getResult());
      assertEquals(0.0, weighted.getDoubleResult());
      weighted.setDefaultResult(false);

      // weights that cancel out
      weighted.reset();
      addPairs(weighted, pair(5, 1), pair(7, -1));
      assertNull(weighted.getResult());
      assertEquals(0.0, weighted.getDoubleResult());
      weighted.setDefaultResult(true);
      assertEquals(0.0, weighted.getResult());
      weighted.setDefaultResult(false);

      // the double array form
      weighted.reset();
      weighted.addValue(new double[] { 5.0, 0.0 });
      weighted.addValue(new double[] { 7.0, 0.0 });
      assertNull(weighted.getResult());

      // a weight that is not 0 gives a result again
      weighted.addValue(new double[] { 9.0, 3.0 });
      assertEquals(9.0, weighted.getResult());
   }

   @Test
   @DisplayName("Bug #80540: Average skips a whitespace string between numeric strings")
   void averageSkipsWhitespaceString() {
      AverageFormula average = new AverageFormula();
      addAll(average, "4", " ", "8");
      assertEquals(6.0, average.getResult());
      assertFalse(average.isNull());
   }

   @Test
   @DisplayName("Bug #80540: Weighted Average with a blank pair uses the other pair")
   void weightedAverageBlankPairUsesOtherPair() {
      WeightedAverageFormula weighted = new WeightedAverageFormula(1);
      addPairs(weighted, pair("", "0.5"), pair("20", "0.2"));
      assertEquals(20.0, weighted.getResult());

      weighted.reset();
      addPairs(weighted, pair("10", "0.5"), pair("20", "0.5"));
      assertEquals(15.0, weighted.getResult());
   }

   @Test
   @DisplayName("Bug #80540: null and non-numeric text are still skipped")
   void nullAndTextStillSkipped() {
      AverageFormula average = new AverageFormula();
      addAll(average, "1", null, "abc", "3");
      assertEquals(2.0, average.getResult());

      ProductFormula product = new ProductFormula();
      addAll(product, "2", null, "abc", "3");
      assertEquals(6.0, product.getResult());

      WeightedAverageFormula weighted = new WeightedAverageFormula(1);
      addPairs(weighted, pair(null, "1"), pair("abc", "1"), pair("10", "1"), pair("20", "1"));
      assertEquals(15.0, weighted.getResult());
   }
}
