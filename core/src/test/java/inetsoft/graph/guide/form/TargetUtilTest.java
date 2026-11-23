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

import inetsoft.report.filter.MaxFormula;
import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79662: null cells (Tool.NULL_DOUBLE) must not be ranked as the lowest value by the
 * Percentile and Quantile targets.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TargetUtilTest {
   private static final double N = Tool.NULL_DOUBLE;

   @Test
   void percentilesIgnoreNullPlaceholders() {
      double[] values = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
      double[] withNulls = { N, 1, 2, N, 3, 4, 5, N, 6, 7, 8, 9, 10, N, N };
      double[] args = { 10, 25, 50, 75 };

      assertArrayEquals(TargetUtil.getPercentiles(values, args.clone()),
                        TargetUtil.getPercentiles(withNulls, args.clone()), 1e-9);
   }

   @Test
   void callerSampleIsNotModified() {
      double[] withNulls = { N, 3, 1, N, 2 };
      double[] copy = withNulls.clone();
      TargetUtil.getPercentiles(withNulls, new double[] { 50 });
      assertArrayEquals(copy, withNulls);
   }

   @Test
   void allNullSampleReturnsEmpty() {
      assertEquals(0, TargetUtil.getPercentiles(new double[] { N, N, N }, new double[] { 50 }).length);
   }

   @Test
   void emptySampleReturnsEmpty() {
      assertEquals(0, TargetUtil.getPercentiles(new double[0], new double[] { 50 }).length);
   }

   @Test
   void quantileStrategyIgnoresNulls() {
      QuantileStrategy strategy = new QuantileStrategy();
      strategy.setNumberOfQuantiles(4);
      double[] values = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
      double[] withNulls = { N, N, 1, 2, 3, N, 4, 5, 6, 7, 8, N, 9, 10, N };

      double[] expected = strategy.calculateBoundaries(values);
      double[] actual = strategy.calculateBoundaries(withNulls);
      assertEquals(3, actual.length);
      assertArrayEquals(expected, actual, 1e-9);
   }

   @Test
   void percentileStrategyIgnoresNulls() {
      PercentileStrategy strategy = new PercentileStrategy(Arrays.asList(75.0, 25.0, 50.0));
      double[] values = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
      double[] withNulls = { N, 1, 2, 3, N, 4, 5, 6, N, 7, 8, 9, 10, N };

      double[] expected = strategy.calculateBoundaries(values);
      assertArrayEquals(expected, strategy.calculateBoundaries(withNulls), 1e-9);
   }

   @Test
   void allNullSampleGeneratesLabelsWithoutException() {
      double[] allNull = { N, N, N };
      QuantileStrategy quantile = new QuantileStrategy();
      PercentileStrategy percentile = new PercentileStrategy(Arrays.asList(25.0, 50.0, 75.0));

      for(TargetStrategy strategy : new TargetStrategy[] { quantile, percentile }) {
         double[] boundaries = strategy.calculateBoundaries(allNull);
         assertEquals(0, boundaries.length);
         assertDoesNotThrow(() -> {
            assertEquals(0, strategy.generateLabels(boundaries, null, "f", null, false, false).length);
         });
      }
   }

   @Test
   @Disabled("Not filed (only the public constructor reaches it; TargetStrategyWrapper turns null into 0 or drops it): [75,null,25] gives one line instead of two (reorder uses indices of the "
      + "shorter array)")
   @DisplayName("percentile target with a null percentile in the list: other lines and labels stay")
   void percentileStrategyIgnoresNullPercentile() {
      PercentileStrategy strategy = new PercentileStrategy(Arrays.asList(75.0, null, 25.0));
      double[] b = strategy.calculateBoundaries(new double[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
      assertArrayEquals(new double[] { 2.75, 8.25 }, b, 1e-9);
      String[] labels = strategy.generateLabels(b, null, "f", null, false, false);
      assertEquals(2, labels.length);
      assertTrue(labels[0].startsWith("25"), labels[0]);
      assertTrue(labels[1].startsWith("75"), labels[1]);
   }

   @Test
   @Disabled("Not filed (only the public constructor reaches it; TargetStrategyWrapper turns null into 0 or drops it): [50,null,10] gives one line instead of two (reorder uses indices of the "
      + "shorter array)")
   @DisplayName("percentage target with a null percentage in the list: other lines and labels stay")
   void percentageStrategyIgnoresNullPercentage() {
      PercentageStrategy strategy = new PercentageStrategy(Arrays.asList(50.0, null, 10.0),
                                                           new TargetParameter(new MaxFormula()));
      double[] b = strategy.calculateBoundaries(new double[] { 3, 9, 6 });
      assertArrayEquals(new double[] { 0.9, 4.5 }, b, 1e-9);
      String[] labels = strategy.generateLabels(b, null, "f", null, false, false);
      assertEquals(2, labels.length);
      assertTrue(labels[0].startsWith("10"), labels[0]);
      assertTrue(labels[1].startsWith("50"), labels[1]);
   }

   @Test
   @DisplayName("getNonNulls drops null entries and keeps the order")
   void nonNullsDropsNulls() {
      assertArrayEquals(new double[] { 1, 2 },
                        TargetUtil.getNonNulls(new Double[] { 1.0, null, 2.0, null }), 0);
      assertEquals(0, TargetUtil.getNonNulls(new Double[] { null }).length);
      assertEquals(0, TargetUtil.getNonNulls(new Double[0]).length);
   }

   @org.junit.jupiter.params.ParameterizedTest(name = "percentile {0}")
   @org.junit.jupiter.params.provider.ValueSource(doubles = { 0, -5 })
   @Disabled("Bug #82146: Percentile target with a value of 0 or less throws OutOfRangeException")
   @DisplayName("percentile of zero or below is clamped without an exception and gives the minimum")
   void percentileBelowRangeGivesMinimum(double percentile) {
      PercentileStrategy strategy = new PercentileStrategy(Arrays.asList(percentile));
      double[] data = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
      assertArrayEquals(new double[] { 1.0 }, strategy.calculateBoundaries(data), 1e-9);
   }

   @Test
   @DisplayName("percentile above 100 gives the same line as 100, the maximum")
   void percentileAboveRangeGivesMaximum() {
      double[] data = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
      double[] at100 = new PercentileStrategy(Arrays.asList(100.0)).calculateBoundaries(data);
      double[] at150 = new PercentileStrategy(Arrays.asList(150.0)).calculateBoundaries(data);
      assertArrayEquals(new double[] { 10.0 }, at100, 1e-9);
      assertArrayEquals(at100, at150, 1e-9);
   }
}
