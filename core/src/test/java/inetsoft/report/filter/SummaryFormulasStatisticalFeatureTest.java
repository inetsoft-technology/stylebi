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
import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.util.Tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Median, Mode, Pth Percentile, Variance and Standard Deviation summary formulas, driven
 * directly through the formula API.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SummaryFormulasStatisticalFeatureTest {
   private static final double DELTA = 1e-9;

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

   private static PercentageFormula[] varianceFamily() {
      return new PercentageFormula[] {
         new VarianceFormula(), new PopulationVarianceFormula(),
         new StandardDeviationFormula(), new PopulationStandardDeviationFormula() };
   }

   @Test
   @DisplayName("Bug #80539: gap:report.summary-formulas.statistical#median-dates")
   void medianDates() {
      Date early = new Date(1_000_000L);
      Date middle = new Date(2_000_000L);
      Date late = new Date(3_000_000L);

      MedianFormula median = new MedianFormula();
      addAll(median, late, early, middle);
      assertEquals(middle, median.getResult());

      median.reset();
      addAll(median, middle, late, early, middle, late);
      assertEquals(middle, median.getResult());

      median.reset();
      addAll(median, new Timestamp(5_000_000L), new Timestamp(1_000_000L),
             new Timestamp(3_000_000L));
      assertEquals(new Timestamp(3_000_000L), median.getResult());
   }

   @Test
   @DisplayName("Median of dates that are already in order is a Date")
   void medianDatesInOrder() {
      Date early = new Date(1_000_000L);
      Date middle = new Date(2_000_000L);
      Date late = new Date(3_000_000L);

      MedianFormula median = new MedianFormula();
      addAll(median, early, early, middle);
      assertEquals(early, median.getResult());
      assertFalse(median.isNull());

      // the value is returned as a Date, not converted to a number
      median.reset();
      addAll(median, early, middle, late);
      assertEquals(middle, median.getResult());
      assertInstanceOf(Date.class, median.getResult());
      assertEquals(0.0, median.getDoubleResult());

      median.reset();
      addAll(median, middle, null);
      assertEquals(middle, median.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.statistical#median-odd-even")
   void medianOddEven() {
      MedianFormula median = new MedianFormula();
      assertTrue(median.isNull());
      assertNull(median.getResult());
      assertEquals(0.0, median.getDoubleResult());

      // an odd count gives the middle value of the sorted values
      addAll(median, 12, -2.01, 10, 2, 10.01);
      assertEquals(10.0, median.getResult());
      assertEquals(10.0, median.getDoubleResult());
      assertFalse(median.isNull());

      // an even count gives the mean of the two middle values
      median.reset();
      addAll(median, 13, -2.01, 12, 10.6, 2, 10);
      assertEquals(10.3, asDouble(median.getResult()), DELTA);
      assertEquals(10.3, median.getDoubleResult(), DELTA);

      median.reset();
      addAll(median, 4, 1, 3, 2);
      assertEquals(2.5, median.getResult());

      median.reset();
      addAll(median, 7, 3);
      assertEquals(5.0, median.getResult());

      // one value, and two equal values
      median.reset();
      addAll(median, 8);
      assertEquals(8.0, median.getResult());
      median.addValue(8);
      assertEquals(8.0, median.getResult());

      // the primitive overloads add to the same list, and their null markers are ignored
      median.reset();
      median.addValue(9.5);
      median.addValue(1.5f);
      median.addValue(5L);
      median.addValue((short) 7);
      median.addValue(Tool.NULL_DOUBLE);
      median.addValue(Tool.NULL_INTEGER);
      median.addValue(3);
      assertEquals(5.0, median.getResult());

      median.reset();
      median.addValue(new double[] { 4.0 });
      median.addValue(new double[] { Tool.NULL_DOUBLE });
      median.addValue(new double[] { 6.0 });
      assertEquals(5.0, median.getResult());

      // numeric strings count as numbers
      median.reset();
      addAll(median, "1", "30", "20");
      assertEquals(20.0, median.getResult());
      median.addValue("4");
      assertEquals(12.0, median.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.statistical#median-percentage-of-total")
   void medianPercentageOfTotal() {
      MedianFormula median = new MedianFormula();
      addAll(median, -2.01, 2, 10, 10.01);
      asPercentage(median, 100);
      assertEquals(0.06, asDouble(median.getResult()), DELTA);
      assertEquals(6.0, asDouble(median.getOriginalResult()), DELTA);
      // the percentage option is restored after the original result is read
      assertEquals(StyleConstants.PERCENTAGE_OF_GROUP, median.getPercentageType());
      assertEquals(0.06, asDouble(median.getResult()), DELTA);

      // an odd count and a numeric string total
      median.reset();
      addAll(median, 1, 9, 5);
      asPercentage(median, "20");
      assertEquals(0.25, asDouble(median.getResult()), DELTA);

      // a total of 0 gives null, or 0 with the default-result flag, for both counts
      median.setTotal(0);
      assertNull(median.getResult());
      assertEquals(5.0, median.getOriginalResult());
      median.setDefaultResult(true);
      assertEquals(0.0, median.getResult());
      median.addValue(7);
      assertEquals(0.0, median.getResult());
      median.setDefaultResult(false);
      assertNull(median.getResult());

      // the sign follows a negative total
      median.setTotal(-4);
      assertEquals(-1.5, asDouble(median.getResult()), DELTA);

      // the percentage option has no effect without a total
      median.setTotal(null);
      assertEquals(6.0, asDouble(median.getResult()), DELTA);

      // the text value does not stop the median of the numeric values
      MedianFormula withText = new MedianFormula();
      addAll(withText, "-3", "2", "10", "a");
      asPercentage(withText, 10);
      assertEquals(-0.05, asDouble(withText.getResult()), DELTA);
      assertEquals(-0.5, asDouble(withText.getOriginalResult()), DELTA);
   }

   @Test
   @DisplayName("gap:report.summary-formulas.statistical#median-null-and-text")
   void medianNullAndText() {
      MedianFormula median = new MedianFormula();
      addAll(median, -3, 2, 10, null);
      asPercentage(median, 100);
      // the null is not counted: the median of three values is 2
      assertEquals(0.02, asDouble(median.getResult()), DELTA);
      assertEquals(2.0, median.getOriginalResult());

      // an even count of non-numeric text has no result
      MedianFormula text = new MedianFormula();
      addAll(text, "KING", "HELLO", "12RTT", "1234");
      assertNull(text.getResult());
      assertEquals(0.0, text.getDoubleResult());
      asPercentage(text, 100);
      assertNull(text.getResult());
      assertNull(text.getOriginalResult());

      // an odd count gives the middle text
      text.addValue("12");
      assertEquals("12RTT", text.getResult());
      assertEquals("12RTT", text.getOriginalResult());
      assertEquals(0.0, text.getDoubleResult());

      // only nulls leave nothing to calculate
      MedianFormula nulls = new MedianFormula();
      addAll(nulls, null, null, null, null);
      assertTrue(nulls.isNull());
      assertNull(nulls.getResult());
      nulls.setDefaultResult(true);
      assertEquals(0.0, nulls.getResult());
      assertTrue(nulls.isDefaultResult());

      // a null in the middle of numeric strings is skipped as well
      MedianFormula strings = new MedianFormula();
      addAll(strings, "1", null, "3", null, "100");
      assertEquals(3.0, strings.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.statistical#mode-most-frequent")
   void modeMostFrequent() {
      ModeFormula mode = new ModeFormula();
      addAll(mode, "this", "that", "what", "it", "it");
      assertEquals("it", mode.getResult());

      mode.reset();
      addAll(mode, 3, 3, 3, 3, 3, 4, 4, 4, 4, 1, 1, 1, -1);
      assertEquals(3, mode.getResult());
      assertEquals(3.0, mode.getDoubleResult());

      // the frequency wins over the position and the value
      mode.reset();
      addAll(mode, 9, 1, 1, 9, 9, 5, 5);
      assertEquals(9, mode.getResult());

      Date early = new Date(1_000_000L);
      Date late = new Date(2_000_000L);
      mode.reset();
      addAll(mode, early, early, late, "", null);
      assertEquals(early, mode.getResult());

      // a tie returns the smaller value, whatever the order of the input
      mode.reset();
      addAll(mode, 7, 2, 7, 2, 5);
      assertEquals(2, mode.getResult());
      mode.reset();
      addAll(mode, 2, 7, 2, 7, 5);
      assertEquals(2, mode.getResult());
      mode.reset();
      addAll(mode, "pear", "apple", "pear", "apple");
      assertEquals("apple", mode.getResult());

      // the primitive overloads
      mode.reset();
      mode.addValue(4);
      mode.addValue(6);
      mode.addValue(6);
      mode.addValue(4L);
      mode.addValue(2.5);
      assertEquals(6, mode.getResult());
      assertFalse(mode.isNull());

      // a clone counts the same values and is independent of the original
      Formula copy = (Formula) mode.clone();
      assertEquals(6, copy.getResult());
      copy.addValue(4);
      copy.addValue(4);
      copy.addValue(4);
      assertEquals(4, copy.getResult());
      assertEquals(6, mode.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.statistical#mode-no-values-null")
   void modeNoValuesNull() {
      ModeFormula mode = new ModeFormula();
      assertTrue(mode.isNull());
      assertNull(mode.getResult());
      assertEquals(0.0, mode.getDoubleResult());

      mode.setDefaultResult(true);
      assertEquals(0, mode.getResult());

      // after a reset the formula is empty again
      mode.setDefaultResult(false);
      addAll(mode, "a", "a", "b");
      assertEquals("a", mode.getResult());
      mode.reset();
      assertTrue(mode.isNull());
      assertNull(mode.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.statistical#pth-percentile-position")
   void pthPercentilePosition() {
      // the position is round(p / 100 * (n - 1)) in the ascending order
      Object[] values = { 3.6, -9.3, -9.3, 10.0, 10.0, 6.0, 10.0 };
      int[] percentiles = { 0, 10, 25, 50, 60, 75, 100 };
      // positions 0, 1 (0.6), 2 (1.5 rounds up), 3, 4 (3.6), 5 (4.5 rounds up), 6
      Object[] atPosition = { -9.3, -9.3, 3.6, 6.0, 10.0, 10.0, 10.0 };

      for(int i = 0; i < percentiles.length; i++) {
         PthPercentileFormula pth = new PthPercentileFormula(percentiles[i]);
         addAll(pth, values);
         assertEquals(atPosition[i], pth.getResult(), "P" + percentiles[i]);
      }

      // a fraction below one half rounds down: P20 is position round(1.2) = 1, not 2
      PthPercentileFormula p20 = new PthPercentileFormula(20);
      addAll(p20, values);
      assertEquals(-9.3, p20.getResult());
      assertEquals(3.6, percentile(30, values), "P30 is position round(1.8) = 2");

      // four values: P50 is position round(1.5) = 2, the third value
      PthPercentileFormula p50 = new PthPercentileFormula(50);
      addAll(p50, 40, 10, 30, 20);
      assertEquals(30, p50.getResult());

      // P0 and P100 are the extremes, and the percentage is limited to 0..100
      assertEquals(10, percentile(0, 40, 10, 30, 20));
      assertEquals(40, percentile(100, 40, 10, 30, 20));
      assertEquals(40, percentile(250, 40, 10, 30, 20));
      assertEquals(10, percentile(-30, 40, 10, 30, 20));

      // one value is every percentile
      assertEquals(5, percentile(37, 5));

      // numeric strings are ordered by value, not by text
      assertEquals("9", percentile(0, "10", "9", "100"));
      assertEquals("10", percentile(50, "10", "9", "100"));
      assertEquals("100", percentile(100, "10", "9", "100"));

      // the double path orders by value as well, and is the double result
      PthPercentileFormula doubles = new PthPercentileFormula(75);
      doubles.addValue(8.0);
      doubles.addValue(2.0);
      doubles.addValue(6.0);
      doubles.addValue(4.0);
      assertEquals(6.0, doubles.getResult());
      assertEquals(6.0, doubles.getDoubleResult());
   }

   @Test
   @DisplayName("Bug #80539: Pth Percentile of dates in any input order")
   void pthPercentileDatesAnyOrder() {
      Date early = new Date(1_000_000L);
      Date middle = new Date(2_000_000L);
      Date late = new Date(3_000_000L);

      assertEquals(late, percentile(100, early, late, middle));
      assertEquals(early, percentile(0, late, early, middle));
      assertEquals(middle, percentile(50, late, early, middle));
      assertEquals(new Timestamp(9_000_000L),
                   percentile(100, new Timestamp(9_000_000L), new Timestamp(1_000_000L),
                              new Timestamp(4_000_000L)));
   }

   @Test
   @DisplayName("Pth Percentile of dates that are already in order")
   void pthPercentileDatesInOrder() {
      Date early = new Date(1_000_000L);
      Date middle = new Date(2_000_000L);
      Date late = new Date(3_000_000L);

      assertEquals(late, percentile(100, early, middle, late, null));
      assertEquals(early, percentile(20, early, late, late));
      assertEquals(early, percentile(0, early, middle, late));
   }

   private static Object percentile(int percentile, Object... values) {
      PthPercentileFormula formula = new PthPercentileFormula(percentile);
      addAll(formula, values);
      return formula.getResult();
   }

   @Test
   @DisplayName("gap:report.summary-formulas.statistical#pth-percentile-null-and-text")
   void pthPercentileNullAndText() {
      PthPercentileFormula pth = new PthPercentileFormula(10);
      assertNull(pth.getResult());
      assertEquals(0.0, pth.getDoubleResult());

      pth.setDefaultResult(true);
      assertEquals(0.0, pth.getResult());
      pth.setDefaultResult(false);

      // nulls are ignored: seven values remain, P10 is position round(0.6) = 1
      addAll(pth, "a", "A", "3", "7", "", "A", "3", null);
      assertEquals("A", pth.getResult());

      // only nulls
      PthPercentileFormula nulls = new PthPercentileFormula(50);
      addAll(nulls, null, null, null, null);
      assertNull(nulls.getResult());
      nulls.setDefaultResult(true);
      assertEquals(0.0, nulls.getResult());

      // a null does not take a position among the values
      assertEquals(30, percentile(100, 10, null, 20, null, 30));
      assertEquals(10, percentile(0, null, 10, 20, 30));

      // reset starts over
      pth.reset();
      assertNull(pth.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.statistical#pth-percentile-primitive-values")
   void pthPercentilePrimitiveValues() {
      int[] ints = { 50, 10, 40, 20, 30 };

      for(int percentile : new int[] { 0, 25, 50, 75, 100 }) {
         PthPercentileFormula typed = new PthPercentileFormula(percentile);
         PthPercentileFormula boxed = new PthPercentileFormula(percentile);

         for(int value : ints) {
            typed.addValue(value);
            boxed.addValue(Integer.valueOf(value));
         }

         assertEquals(boxed.getResult(), typed.getResult(), "P" + percentile);
      }

      PthPercentileFormula p75 = new PthPercentileFormula(75);

      for(int value : ints) {
         p75.addValue(value);
      }

      assertEquals(40, p75.getResult());

      // each primitive type keeps its own type and sorts its values
      PthPercentileFormula longs = new PthPercentileFormula(100);
      longs.addValue(5L);
      longs.addValue(9_000_000_000L);
      longs.addValue(7L);
      assertEquals(9_000_000_000L, longs.getResult());

      PthPercentileFormula shorts = new PthPercentileFormula(0);
      shorts.addValue((short) 5);
      shorts.addValue((short) -3);
      shorts.addValue((short) 7);
      assertEquals((short) -3, shorts.getResult());

      PthPercentileFormula floats = new PthPercentileFormula(50);
      floats.addValue(2.5f);
      floats.addValue(0.5f);
      floats.addValue(1.5f);
      assertEquals(1.5f, floats.getResult());

      PthPercentileFormula doubles = new PthPercentileFormula(50);
      doubles.addValue(new double[] { 3.0 });
      doubles.addValue(new double[] { 1.0 });
      doubles.addValue(new double[] { 2.0 });
      assertEquals(2.0, doubles.getResult());

      // the null markers of the primitive types are not added
      PthPercentileFormula nulls = new PthPercentileFormula(0);
      nulls.addValue(Tool.NULL_INTEGER);
      nulls.addValue(Tool.NULL_LONG);
      nulls.addValue(Tool.NULL_SHORT);
      nulls.addValue(Tool.NULL_FLOAT);
      nulls.addValue(Tool.NULL_DOUBLE);
      nulls.addValue(new double[] { Tool.NULL_DOUBLE });
      assertNull(nulls.getResult());
      nulls.addValue(4);
      nulls.addValue(Tool.NULL_INTEGER);
      assertEquals(4, nulls.getResult());

      // reset drops the typed lists
      p75.reset();
      assertNull(p75.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.statistical#standard-deviation-sample")
   void standardDeviationSample() {
      StandardDeviationFormula deviation = new StandardDeviationFormula();
      assertNull(deviation.getResult());
      addAll(deviation, 1, 2);
      // sqrt of the sample variance 0.5, which divides by n - 1
      assertEquals(Math.sqrt(0.5), asDouble(deviation.getResult()), DELTA);
      assertEquals(Math.sqrt(0.5), deviation.getDoubleResult(), DELTA);

      deviation.reset();
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

      // the clone calculates from the same values
      StandardDeviationFormula copy = (StandardDeviationFormula) deviation.clone();
      assertEquals(1.0, asDouble(copy.getResult()), DELTA);
   }

   @Test
   @DisplayName("gap:report.summary-formulas.statistical#variance-sample")
   void varianceSample() {
      VarianceFormula variance = new VarianceFormula();
      addAll(variance, 1, 2, 3, 4, 5, 6);
      // the sample variance divides by n - 1
      assertEquals(3.5, variance.getResult());
      assertEquals(3.5, variance.getDoubleResult());
      assertFalse(variance.isNull());

      // text is skipped, the numeric strings count
      variance.reset();
      addAll(variance, null, "SS", "10", "9");
      assertEquals(0.5, variance.getResult());

      variance.reset();
      addAll(variance, 20, 1, -10, 20, 0.01);
      assertEquals(177.16902, asDouble(variance.getResult()), DELTA);

      // the order of the values does not matter
      variance.reset();
      addAll(variance, 6, 1, 5, 2, 4, 3);
      assertEquals(3.5, variance.getResult());

      // the population variance divides by n
      PopulationVarianceFormula population = new PopulationVarianceFormula();
      addAll(population, 6, 1, 5, 2, 4, 3);
      assertEquals(35.0 / 12, asDouble(population.getResult()), DELTA);

      // the clone keeps the values and the sample divisor
      VarianceFormula copy = (VarianceFormula) variance.clone();
      assertEquals(3.5, copy.getResult());
      assertInstanceOf(VarianceFormula.class, copy);
   }

   @Test
   @DisplayName("gap:report.summary-formulas.statistical#population-variance")
   void populationVariance() {
      PopulationVarianceFormula variance = new PopulationVarianceFormula();
      addAll(variance, 1, 2, 3, 4, 5, 6, 7, 8);
      assertEquals(5.25, variance.getResult());
      assertEquals(5.25, variance.getDoubleResult());

      variance.reset();
      addAll(variance, "1", "4", "A", null);
      assertEquals(2.25, variance.getResult());

      variance.reset();
      addAll(variance, 20, 1, -10, 20, 0.01);
      assertEquals(141.735216, asDouble(variance.getResult()), DELTA);

      // two values: the sample variance is double the population variance
      variance.reset();
      addAll(variance, 10, 9);
      assertEquals(0.25, variance.getResult());

      PopulationVarianceFormula copy = (PopulationVarianceFormula) variance.clone();
      assertEquals(0.25, copy.getResult());
      assertEquals(0.25, copy.getDoubleResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.statistical#population-standard-deviation")
   void populationStandardDeviation() {
      PopulationStandardDeviationFormula deviation = new PopulationStandardDeviationFormula();
      addAll(deviation, 20, 1, -10, 20, 0.01);
      assertEquals(11.905260013960216, asDouble(deviation.getResult()), DELTA);
      assertEquals(11.905260013960216, deviation.getDoubleResult(), DELTA);

      // the square root of the population variance, divided by n
      deviation.reset();
      addAll(deviation, "1", "2", "3", "4", "5.5", "a", null);
      assertEquals(1.5620499351813308, asDouble(deviation.getResult()), DELTA);

      deviation.reset();
      addAll(deviation, 1, 3);
      assertEquals(1.0, deviation.getResult());

      deviation.reset();
      addAll(deviation, 0, 1, 10, null);
      assertEquals(4.4969125210773475, asDouble(deviation.getResult()), DELTA);

      PopulationStandardDeviationFormula copy =
         (PopulationStandardDeviationFormula) deviation.clone();
      assertEquals(4.4969125210773475, asDouble(copy.getResult()), DELTA);
   }

   @Test
   @DisplayName("gap:report.summary-formulas.statistical#variance-family-percentage-of-total")
   void varianceFamilyPercentageOfTotal() {
      // the sample variance of 1..6 is 3.5, as a share of a total of 10
      VarianceFormula variance = new VarianceFormula();
      addAll(variance, 1, 2, 3, 4, 5, 6, null, null);
      asPercentage(variance, 10);
      assertEquals(0.35, asDouble(variance.getResult()), DELTA);
      assertEquals(0.35, variance.getDoubleResult(), DELTA);
      assertEquals(3.5, variance.getOriginalResult());
      assertEquals(StyleConstants.PERCENTAGE_OF_GROUP, variance.getPercentageType());

      // the other three formulas divide their own result
      PopulationVarianceFormula population = new PopulationVarianceFormula();
      addAll(population, "1", "4", "A", null);
      asPercentage(population, 20);
      assertEquals(0.1125, asDouble(population.getResult()), DELTA);
      assertEquals(0.1125, population.getDoubleResult(), DELTA);
      assertEquals(2.25, population.getOriginalResult());

      StandardDeviationFormula deviation = new StandardDeviationFormula();
      addAll(deviation, "-1", "-2", "3", "A");
      asPercentage(deviation, 20);
      assertEquals(Math.sqrt(7.0) / 20, asDouble(deviation.getResult()), DELTA);
      assertEquals(Math.sqrt(7.0) / 20, deviation.getDoubleResult(), DELTA);
      assertEquals(Math.sqrt(7.0), asDouble(deviation.getOriginalResult()), DELTA);

      PopulationStandardDeviationFormula popDeviation = new PopulationStandardDeviationFormula();
      addAll(popDeviation, 30, 1, -10, 20, 7.2, 0.01, null);
      asPercentage(popDeviation, 100);
      assertEquals(0.13325869264954788, asDouble(popDeviation.getResult()), DELTA);
      assertEquals(0.13325869264954788, popDeviation.getDoubleResult(), DELTA);
      assertEquals(13.325869264954788, asDouble(popDeviation.getOriginalResult()), DELTA);

      // a zero or negative total gives no percentage, and the original result is unchanged
      for(PercentageFormula formula : varianceFamily()) {
         addAll(formula, 2, 4, 6, 8);
         asPercentage(formula, 0);
         assertNull(formula.getResult(), formula.getName());
         assertEquals(0.0, formula.getDoubleResult(), formula.getName());
         assertNotNull(formula.getOriginalResult(), formula.getName());
         formula.setDefaultResult(true);
         assertNull(formula.getResult(), formula.getName());

         formula.setTotal(-100);
         assertNull(formula.getResult(), formula.getName());
         assertNotNull(formula.getOriginalResult(), formula.getName());

         // a total that is not a number gives none either, a missing total as well
         formula.setTotal("n/a");
         assertNull(formula.getResult(), formula.getName());
         formula.setTotal(null);
         assertNull(formula.getResult(), formula.getName());

         // a numeric string is a total
         formula.setTotal("100");
         assertNotNull(formula.getResult(), formula.getName());
      }

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

   @Test
   @DisplayName("gap:report.summary-formulas.statistical#variance-no-numeric-input-null")
   void varianceNoNumericInputNull() {
      Date first = new Date(1_000_000L);
      Date second = new Date(2_000_000L);

      for(PercentageFormula formula : varianceFamily()) {
         String name = formula.getName();
         assertTrue(formula.isNull(), name);
         assertNull(formula.getResult(), name);
         assertEquals(0.0, formula.getDoubleResult(), name);

         // dates and nulls are not numbers
         addAll(formula, first, second, null);
         assertTrue(formula.isNull(), name);
         assertNull(formula.getResult(), name);

         formula.reset();
         addAll(formula, null, null, null, null);
         assertTrue(formula.isNull(), name);
         assertNull(formula.getResult(), name);

         formula.addValue(Tool.NULL_DOUBLE);
         formula.addValue(Tool.NULL_INTEGER);
         formula.addValue(Tool.NULL_LONG);
         formula.addValue(Tool.NULL_SHORT);
         formula.addValue(Tool.NULL_FLOAT);
         formula.addValue(new double[] { Tool.NULL_DOUBLE });
         addAll(formula, "text", "x1");
         assertTrue(formula.isNull(), name);
         assertNull(formula.getResult(), name);

         // with the default-result flag nothing gives 0 instead of null
         formula.setDefaultResult(true);
         assertEquals(0.0, formula.getResult(), name);
         formula.setDefaultResult(false);

         // a number ends the empty state
         formula.addValue(5);
         assertFalse(formula.isNull(), name);
         assertNotNull(formula.getResult(), name);
      }
   }

   @Test
   @DisplayName("gap:report.summary-formulas.statistical#variance-single-value-is-zero")
   void varianceSingleValueIsZero() {
      for(PercentageFormula formula : varianceFamily()) {
         String name = formula.getName();
         formula.addValue(1);
         assertFalse(formula.isNull(), name);
         assertEquals(0.0, formula.getResult(), name);
         assertEquals(0.0, formula.getDoubleResult(), name);

         // the same for another type, after the nulls and the text that are skipped
         formula.reset();
         addAll(formula, null, "text", 42.5);
         assertEquals(0.0, formula.getResult(), name);

         formula.reset();
         formula.addValue(-7L);
         assertEquals(0.0, formula.getResult(), name);

         // as a percentage it is 0 of a positive total
         asPercentage(formula, 50);
         assertEquals(0.0, asDouble(formula.getResult()), name);
         assertEquals(0.0, formula.getOriginalResult(), name);

         // two different values have a spread
         formula.addValue(-5L);
         assertTrue(asDouble(formula.getOriginalResult()) > 0, name);
      }
   }

   @Test
   @DisplayName("Bug #80539: Pth(0) and Pth(100) of unordered dates are the extremes")
   void pthPercentileDateExtremes() {
      Date a = new Date(1_000_000L);
      Date b = new Date(2_000_000L);
      Date c = new Date(3_000_000L);
      Date d = new Date(4_000_000L);
      assertEquals(a, percentile(0, d, b, a, c));
      assertEquals(d, percentile(100, c, d, a, b));

      Timestamp ta = new Timestamp(10_000L);
      Timestamp tb = new Timestamp(20_000L);
      Timestamp tc = new Timestamp(30_000L);
      assertEquals(ta, percentile(0, tc, ta, tb));
      assertEquals(tc, percentile(100, tb, tc, ta));
      assertInstanceOf(Timestamp.class, percentile(100, tb, tc, ta));
      // a Date and a Timestamp compare by time
      assertEquals(a, percentile(100, tc, a, tb));
   }

   @Test
   @DisplayName("Bug #80539: Median of an even number of dates has no midpoint and stays null")
   void medianEvenDatesIsNull() {
      MedianFormula median = new MedianFormula();
      addAll(median, new Date(3_000_000L), new Date(1_000_000L), new Date(2_000_000L),
             new Date(3_000_000L));
      assertNull(median.getResult());
   }

   @Test
   @DisplayName("Bug #80539: Median and Pth Percentile of numbers and numeric text are unchanged")
   void numericAndTextOrderUnchanged() {
      MedianFormula median = new MedianFormula();
      addAll(median, 30, 10, 20);
      assertEquals(20.0, median.getResult());
      median.reset();
      addAll(median, "10", "9", "100");
      assertEquals(10.0, median.getResult());
      median.reset();
      addAll(median, 4, 1, 3, 2);
      assertEquals(2.5, median.getResult());

      assertEquals(40, percentile(100, 40, 10, 30, 20));
      assertEquals("9", percentile(0, "10", "9", "100"));
      assertEquals("100", percentile(100, "10", "9", "100"));
      assertEquals(10, percentile(0, 40, 10, 30, 20));
   }

   /**
    * Random lists per sort test. 13.8 runs 20000; StyleBI's NumberParser throws on non-numeric
    * text, which makes 20000 take about 30 s per test, so the class would need @Tag("slow").
    * 2000 lists of 2 to 150 values still reach TimSort's comparator contract check.
    */
   private static final int ITERATIONS = 2000;

   /**
    * The StyleBI NumericComparer of before Bug #80583 (13.8: #80539), the order every non-date
    * list must keep. StyleBI parses with org.jnumbers.NumberParser, 13.8 with
    * NumberParserWrapper, so blank and non-numeric strings rank differently between the two.
    */
   private static final java.util.Comparator<Object> BASE_ORDER = (v1, v2) -> {
      double val1 = Double.NaN;
      double val2 = Double.NaN;

      try {
         val1 = (v1 instanceof Number) ? ((Number) v1).doubleValue() :
            org.jnumbers.NumberParser.getDouble(v1.toString());
      }
      catch(Exception ex) {
         v1 = null;
      }

      try {
         val2 = (v2 instanceof Number) ? ((Number) v2).doubleValue() :
            org.jnumbers.NumberParser.getDouble(v2.toString());
      }
      catch(Exception ex) {
         v2 = null;
      }

      if(v1 == null) {
         return (v2 == null) ? 0 : -1;
      }
      else if(v2 == null) {
         return 1;
      }

      return Double.compare(val1, val2);
   };

   private static Object randomValue(java.util.Random random, boolean dates) {
      int kind = random.nextInt(dates ? 9 : 7);

      switch(kind) {
      case 0: return null;
      case 1: return "";
      case 2: return " ";
      case 3: return "abc";
      case 4: return Integer.toString(random.nextInt(20) - 5);
      case 5: return random.nextInt(20) - 5;
      case 6: return random.nextInt(8) / 2.0;
      case 7: return new Date(random.nextInt(6) * 1_000_000L);
      default: return new Timestamp(random.nextInt(6) * 1_000_000L);
      }
   }

   @Test
   @DisplayName("Bug #80539: a list mixing dates with other values sorts without an error")
   void mixedDateListsSortWithoutError() {
      java.util.Random random = new java.util.Random(80539L);
      NumericComparer comparer = new NumericComparer();
      int failures = 0;

      for(int i = 0; i < ITERATIONS; i++) {
         int size = 2 + random.nextInt(150);
         java.util.List<Object> list = new java.util.ArrayList<>();

         for(int j = 0; j < size; j++) {
            list.add(randomValue(random, true));
         }

         try {
            Object[] array = list.toArray();
            java.util.Arrays.sort(array, comparer);
            java.util.Collections.sort(list, comparer);
         }
         catch(IllegalArgumentException ex) {
            failures++;
         }
      }

      assertEquals(0, failures, "lists whose sort violated the comparator contract");
   }

   @Test
   @DisplayName("Bug #80539: lists without dates keep the order of the comparer before the fix")
   void nonDateListsKeepBaseOrder() {
      java.util.Random random = new java.util.Random(80540L);
      NumericComparer comparer = new NumericComparer();

      for(int i = 0; i < ITERATIONS; i++) {
         int size = 2 + random.nextInt(150);
         Object[] expected = new Object[size];

         for(int j = 0; j < size; j++) {
            expected[j] = randomValue(random, false);
         }

         Object[] actual = expected.clone();
         java.util.Arrays.sort(expected, BASE_ORDER);
         java.util.Arrays.sort(actual, comparer);
         assertArrayEquals(expected, actual);
      }
   }

   @Test
   @DisplayName("Bug #80583: dates sort after null and text, before numbers, and by time")
   void datesSortAfterOtherValues() {
      NumericComparer comparer = new NumericComparer();
      Date early = new Date(1_000_000L);
      Date late = new Timestamp(2_000_000L);

      assertTrue(comparer.compare(early, null) > 0);
      assertTrue(comparer.compare(null, early) < 0);
      assertTrue(comparer.compare(early, "abc") > 0);
      assertTrue(comparer.compare("abc", early) < 0);
      // StyleBI ranks a date before a number (NumericComparer.compareToDate, pinned by
      // NumericComparerTest); 13.8 ranks it after every non-date value
      assertTrue(comparer.compare(early, 5) < 0);
      assertTrue(comparer.compare(5, early) > 0);
      assertTrue(comparer.compare(early, late) < 0);
      assertTrue(comparer.compare(late, early) > 0);
      assertEquals(0, comparer.compare(early, new Date(1_000_000L)));
   }

   @Test
   @DisplayName("Bug #80697: Median skips a blank string like Sum")
   void medianSkipsABlankString() {
      MedianFormula median = new MedianFormula();
      addAll(median, "1", "", "3");
      assertEquals(2.0, median.getResult());
      assertEquals(2.0, median.getDoubleResult(), DELTA);
      assertFalse(median.isNull());

      // whitespace is blank too, wherever it is in the list
      median.reset();
      addAll(median, "", "5", " ", "1", "  ", "3", "");
      assertEquals(3.0, median.getResult());

      // numbers mixed with blank strings
      median.reset();
      addAll(median, 10, "", 20, "30", "");
      assertEquals(20.0, median.getResult());

      // the percentage option works on the values that are left
      median.reset();
      addAll(median, "2", "", "4");
      asPercentage(median, 10);
      assertEquals(0.3, asDouble(median.getResult()), DELTA);
      assertEquals(3.0, median.getOriginalResult());

      // a clone keeps the values that were kept
      MedianFormula copy = (MedianFormula) median.clone();
      assertEquals(3.0, copy.getOriginalResult());

      // only blank strings leave nothing to calculate, like only nulls
      MedianFormula blanks = new MedianFormula();
      addAll(blanks, "", " ");
      assertTrue(blanks.isNull());
      assertNull(blanks.getResult());
      blanks.setDefaultResult(true);
      assertEquals(0.0, blanks.getResult());
   }

   @Test
   @DisplayName("Bug #80697: Pth Percentile skips a blank string like Sum")
   void pthPercentileSkipsABlankString() {
      PthPercentileFormula p50 = new PthPercentileFormula(50);
      addAll(p50, "1", "", "3", "5");
      assertEquals("3", p50.getResult());

      PthPercentileFormula p100 = new PthPercentileFormula(100);
      addAll(p100, "1", "", "3");
      assertEquals("3", p100.getResult());

      PthPercentileFormula p0 = new PthPercentileFormula(0);
      addAll(p0, "", "4", " ", "2");
      assertEquals("2", p0.getResult());

      // a clone keeps the values that were kept
      PthPercentileFormula copy = (PthPercentileFormula) p0.clone();
      assertEquals("2", copy.getResult());

      // only blank strings leave nothing to calculate
      PthPercentileFormula blanks = new PthPercentileFormula(50);
      addAll(blanks, "", " ");
      assertNull(blanks.getResult());
      blanks.setDefaultResult(true);
      assertEquals(0.0, blanks.getResult());
   }

   @Test
   @DisplayName("Bug #80697: blank strings change nothing, and other values are unchanged")
   void blankStringsAreRemovedBeforeMedianAndPthPercentile() {
      Object[] pool = { null, "", " ", "1", "2.5", "-4", "abc", 3, 7.5, 2L };
      Random random = new Random(80697L);

      for(int i = 0; i < 3000; i++) {
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

         int percentile = random.nextInt(101);
         MedianFormula median = new MedianFormula();
         MedianFormula medianNoBlanks = new MedianFormula();
         PthPercentileFormula pth = new PthPercentileFormula(percentile);
         PthPercentileFormula pthNoBlanks = new PthPercentileFormula(percentile);
         values.forEach(v -> { median.addValue(v); pth.addValue(v); });
         noBlanks.forEach(v -> { medianNoBlanks.addValue(v); pthNoBlanks.addValue(v); });

         String msg = values + " p" + percentile;
         assertEquals(medianNoBlanks.getResult(), median.getResult(), msg);
         assertEquals(pthNoBlanks.getResult(), pth.getResult(), msg);
      }

      // non-numeric text is still ordered below the numbers, as before
      MedianFormula text = new MedianFormula();
      addAll(text, "-3", "2", "10", "a");
      assertEquals(-0.5, asDouble(text.getResult()), DELTA);
   }

   @Test
   @DisplayName("Bug #80868: Variance, Standard Deviation and the population forms skip a blank string")
   void varianceFamilySkipsABlankString() {
      for(PercentageFormula formula : varianceFamily()) {
         PercentageFormula clean = (PercentageFormula) formula.clone();
         addAll(formula, "2", "", "4", "6");
         addAll(clean, "2", "4", "6");
         assertEquals(clean.getResult(), formula.getResult(), formula.getName());
         assertFalse(Double.isNaN(asDouble(formula.getResult())), formula.getName());

         // text that is not a number was already skipped, and a blank next to it changes nothing
         formula.reset();
         addAll(formula, "2", "abc", "", "4", " ", "6");
         assertEquals(clean.getResult(), formula.getResult(), formula.getName());
      }

      VarianceFormula variance = new VarianceFormula();
      addAll(variance, "2", "", "4", "6");
      assertEquals(4.0, variance.getResult());

      // a group of only blanks has no value, not NaN
      variance.reset();
      addAll(variance, "", "");
      assertTrue(variance.isNull());
      assertNull(variance.getResult());
   }

   @Test
   @DisplayName("Bug #80868: a table summary of numeric text with a blank cell has a variance")
   void blankStringsInAVarianceTableSummary() {
      TableLens data = new DefaultTableLens(new Object[][] {
         { "g", "v" }, { "a", "2" }, { "a", "" }, { "a", "4" }, { "a", "6" }, { "b", "" },
         { "b", "7" }
      });
      Formula[] formulas = {
         new VarianceFormula(), new StandardDeviationFormula(), new PopulationVarianceFormula(),
         new PopulationStandardDeviationFormula()
      };
      double[] a = { 4.0, 2.0, 8.0 / 3, Math.sqrt(8.0 / 3) };
      // the grand total of 2, 4, 6, 7
      double mean = 19.0 / 4;
      double sumSq = (2 - mean) * (2 - mean) + (4 - mean) * (4 - mean) + (6 - mean) * (6 - mean)
         + (7 - mean) * (7 - mean);
      double[] total = { sumSq / 3, Math.sqrt(sumSq / 3), sumSq / 4, Math.sqrt(sumSq / 4) };

      for(int f = 0; f < formulas.length; f++) {
         SummaryFilter summary = new SummaryFilter(
            data, new int[] { 0 }, new int[] { 1 }, (Formula) formulas[f].clone(),
            (Formula) formulas[f].clone());
         summary.moreRows(Integer.MAX_VALUE);

         assertEquals(a[f], asDouble(summary.getObject(1, 1)), DELTA,
                      formulas[f].getName() + " group a");
         assertEquals(total[f], asDouble(summary.getObject(3, 1)), DELTA,
                      formulas[f].getName() + " total");
      }
   }

   @Test
   @DisplayName("Bug #80868: blank strings change nothing in the variance family")
   void varianceFamilyBlankStringsChangeNothing() {
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

         PercentageFormula[] all = varianceFamily();
         PercentageFormula[] clean = varianceFamily();

         for(int f = 0; f < all.length; f++) {
            values.forEach(all[f]::addValue);
            noBlanks.forEach(clean[f]::addValue);
            assertEquals(clean[f].getResult(), all[f].getResult(), all[f].getName() + " " + values);
         }

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
            assertTrue(clean[0].isNull(), noBlanks + "");
         }
         else {
            assertEquals((Double) expected, asDouble(clean[0].getResult()), 1e-9, noBlanks + "");
         }
      }
   }
}
