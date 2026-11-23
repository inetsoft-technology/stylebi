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
package inetsoft.graph.guide.form;

import inetsoft.report.composition.graph.ConfidenceIntervalWrapper;
import inetsoft.report.composition.graph.TargetParameterWrapper;
import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Confidence interval target: t-interval of the mean; null cells (Tool.NULL_DOUBLE) are not
 * values.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ConfidenceIntervalStrategyTest {
   private static final double N = Tool.NULL_DOUBLE;
   private static final double[] ONE_TO_FIVE = { 1, 2, 3, 4, 5 };
   private static final double[] CI95_OF_ONE_TO_FIVE = { 1.0368, 4.9632 };

   private static double[] bounds(double level, double... data) {
      return new ConfidenceIntervalStrategy(level).calculateBoundaries(data);
   }

   static Stream<Arguments> knownIntervals() {
      return Stream.of(
         Arguments.of(new double[] { 1, 2, 3, 4, 5 }, 95, new double[] { 1.0368, 4.9632 }),
         Arguments.of(new double[] { 1, 2, 3, 4, 5 }, 90, new double[] { 1.4926, 4.5074 }),
         Arguments.of(new double[] { 1, 2, 3, 4, 5 }, 99, new double[] { -0.2556, 6.2556 }),
         Arguments.of(new double[] { 10, 20 }, 95, new double[] { -48.5310, 78.5310 }),
         Arguments.of(new double[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }, 95,
                      new double[] { 3.3341, 7.6659 }),
         Arguments.of(new double[] { -1, -2, -3, -4, -5 }, 95, new double[] { -4.9632, -1.0368 }));
   }

   @ParameterizedTest(name = "level {1} of {0}")
   @MethodSource("knownIntervals")
   @DisplayName("interval matches the hand-computed t-interval of the mean")
   void intervalMatchesTInterval(double[] data, double level, double[] expected) {
      assertArrayEquals(expected, bounds(level, data), 1e-4);
   }

   @Test
   @Disabled("Bug #82145: Confidence Interval target counts null cells in n and degrees of freedom")
   @DisplayName("null cells are ignored: [N,1..5,N] gives the interval of [1..5]")
   void nullCellsAreIgnoredForFiveValues() {
      assertArrayEquals(CI95_OF_ONE_TO_FIVE, bounds(95, N, 1, 2, 3, 4, 5, N), 1e-4);
   }

   @Test
   @Disabled("Bug #82145: Confidence Interval target counts null cells in n and degrees of freedom")
   @DisplayName("null cells are ignored: [N,10,N,20] gives the interval of [10,20]")
   void nullCellsAreIgnoredForTwoValues() {
      assertArrayEquals(new double[] { -48.5310, 78.5310 }, bounds(95, N, 10, N, 20), 1e-4);
   }

   @Test
   @Disabled("Bug #82145: Confidence Interval target counts null cells in n and degrees of freedom")
   @DisplayName("one non-null value among nulls gives no interval")
   void oneNonNullValueGivesNoInterval() {
      double[] result = bounds(95, N, N, 5);
      assertEquals(0, result.length, Arrays.toString(result));
   }

   @Test
   @Disabled("Bug #82145: Confidence Interval target counts null cells in n and degrees of freedom")
   @DisplayName("all-null data gives an empty result without NaN")
   void allNullGivesEmpty() {
      double[] result = bounds(95, N, N, N);
      assertEquals(0, result.length, Arrays.toString(result));
   }

   @Test
   @DisplayName("empty and single-value data give no interval")
   void emptyAndSingleValueGiveNoInterval() {
      assertEquals(0, bounds(95).length);
      assertEquals(0, bounds(95, 5).length);
   }

   @ParameterizedTest(name = "level {0}")
   @ValueSource(doubles = { 0, -5, Double.NaN })
   @DisplayName("level of zero, negative or NaN gives no interval")
   void unusableLevelGivesNoInterval(double level) {
      double[] result = bounds(level, ONE_TO_FIVE);
      assertEquals(0, result.length, Arrays.toString(result));
   }

   @Test
   @DisplayName("level 100 gives no NaN bounds and brackets the mean")
   void level100IsNotNaN() {
      double[] result = bounds(100, ONE_TO_FIVE);
      assertEquals(2, result.length);
      assertFalse(Double.isNaN(result[0]) || Double.isNaN(result[1]));
      assertTrue(result[0] < 3 && 3 < result[1]);
   }

   @Test
   @DisplayName("levels above 100 are clamped to the same interval as 100")
   void levelAbove100IsClamped() {
      assertArrayEquals(bounds(100, ONE_TO_FIVE), bounds(150, ONE_TO_FIVE));
   }

   @Test
   @DisplayName("a small level 0.1 gives a narrower band than 95 and still brackets the mean")
   void smallLevelIsNarrower() {
      double[] narrow = bounds(0.1, ONE_TO_FIVE);
      assertEquals(2, narrow.length);
      assertTrue(narrow[0] < 3 && 3 < narrow[1]);
      assertTrue(narrow[1] - narrow[0] < CI95_OF_ONE_TO_FIVE[1] - CI95_OF_ONE_TO_FIVE[0]);
   }

   @Test
   @DisplayName("interval is symmetric around the mean")
   void intervalIsSymmetricAroundMean() {
      double[] result = bounds(95, 2, 9, 4, 7, 1);
      assertEquals(4.6, (result[0] + result[1]) / 2, 1e-9);
      assertTrue(result[0] < result[1]);
   }

   @Test
   @DisplayName("identical values give a zero-width interval at the value")
   void identicalValuesGiveZeroWidth() {
      assertArrayEquals(new double[] { 4, 4 }, bounds(95, 4, 4, 4, 4), 1e-9);
   }

   @Test
   @DisplayName("large magnitudes keep the relative accuracy")
   void largeMagnitudes() {
      double[] result = bounds(95, 1e15, 2e15, 3e15);
      double[] expected = { -4.8414e14, 4.4841e15 };
      assertEquals(2, result.length);

      for(int i = 0; i < 2; i++) {
         assertEquals(expected[i], result[i], Math.abs(expected[i]) * 1e-4);
      }
   }

   @Test
   @DisplayName("pinned current behavior: a NaN data value makes both bounds NaN")
   void nanDataValueGivesNaNBounds() {
      assertArrayEquals(new double[] { Double.NaN, Double.NaN }, bounds(95, 1, Double.NaN, 3, 4));
   }

   @Test
   @DisplayName("null cells do not move the middle of the interval: [N,1..5,N] is centered on 3")
   void nullCellsKeepIntervalCenteredOnMean() {
      double[] result = bounds(95, N, 1, 2, 3, 4, 5, N);
      assertEquals(2, result.length);
      assertEquals(3.0, (result[0] + result[1]) / 2, 1e-9);
   }

   @Test
   @DisplayName("labels: every line says Confidence Interval with the level")
   void labelsContainLevel() {
      ConfidenceIntervalStrategy strategy = new ConfidenceIntervalStrategy(95);
      double[] b = strategy.calculateBoundaries(ONE_TO_FIVE);
      String[] labels = strategy.generateLabels(b, null, "f", null, false, false);
      assertEquals(2, labels.length);
      assertEquals(labels[0], labels[1]);
      assertTrue(labels[0].contains("95"), labels[0]);
   }

   @Test
   @DisplayName("labels: empty result gives no labels and no exception")
   void emptyResultGivesNoLabels() {
      ConfidenceIntervalStrategy strategy = new ConfidenceIntervalStrategy(95);
      double[] b = new double[0];
      assertDoesNotThrow(() -> {
         assertEquals(0, strategy.generateLabels(b, null, "f", null, false, false).length);
      });
   }

   @Test
   @DisplayName("generic label puts the level in parentheses")
   void genericLabelHasLevel() {
      assertTrue(ConfidenceIntervalStrategy.getGenericLabel("99.5").contains("(99.5%)"));
   }

   static Stream<Arguments> wrapperLevels() {
      return Stream.of(Arguments.of("95", 95.0), Arguments.of("", 0.0),
                       Arguments.of("abc", 0.0), Arguments.of("-5", -5.0));
   }

   @ParameterizedTest(name = "wrapper level \"{0}\" gives {1}")
   @MethodSource("wrapperLevels")
   @DisplayName("wrapper unwrap: level text becomes the strategy level (first value, 0 if unusable)")
   void wrapperUnwrapLevel(String text, double expected) {
      ConfidenceIntervalStrategy strategy =
         (ConfidenceIntervalStrategy) new ConfidenceIntervalWrapper(text).unwrap();
      assertEquals(expected, strategy.getConfidenceLevel(), 1e-9);
   }

   @Test
   @DisplayName("wrapper setParameters with several parameters uses the first as the level")
   void wrapperSetParametersUsesFirst() {
      ConfidenceIntervalWrapper wrapper = new ConfidenceIntervalWrapper();
      wrapper.setParameters(new TargetParameterWrapper("95"), new TargetParameterWrapper("99"));
      assertEquals("95", wrapper.getConfidenceLevel());
      assertEquals(95.0, ((ConfidenceIntervalStrategy) wrapper.unwrap()).getConfidenceLevel(), 1e-9);
   }

   @Test
   @DisplayName("wrapper setParameters with a single parameter sets that level")
   void wrapperSetParametersSingle() {
      ConfidenceIntervalWrapper wrapper = new ConfidenceIntervalWrapper();
      wrapper.setParameters(new TargetParameterWrapper("90"));
      assertEquals("90", wrapper.getConfidenceLevel());
      assertEquals(90.0, ((ConfidenceIntervalStrategy) wrapper.unwrap()).getConfidenceLevel(), 1e-9);
   }

   @Test
   @DisplayName("a strategy instance holds no state between calls")
   void repeatedCallsAreIndependent() {
      ConfidenceIntervalStrategy reused = new ConfidenceIntervalStrategy(95);
      double[] other = { 100, 200, 300, 400 };

      assertArrayEquals(bounds(95, ONE_TO_FIVE), reused.calculateBoundaries(ONE_TO_FIVE), 1e-12);
      assertArrayEquals(bounds(95, other), reused.calculateBoundaries(other), 1e-12);
      assertArrayEquals(bounds(95, ONE_TO_FIVE), reused.calculateBoundaries(ONE_TO_FIVE), 1e-12);
   }
}
