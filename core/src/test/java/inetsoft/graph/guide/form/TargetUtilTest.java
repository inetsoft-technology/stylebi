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

import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79662: null cells (Tool.NULL_DOUBLE) must not be ranked as the lowest value by the
 * Percentile and Quantile targets.
 */
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
}
