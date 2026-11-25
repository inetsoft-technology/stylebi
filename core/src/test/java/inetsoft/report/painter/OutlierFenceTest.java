/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.report.painter;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which values of a column are outliers, for scaling a bar to the column without one huge value
 * flattening the rest.
 */
@Tag("core")
class OutlierFenceTest {
   @Test
   void aColumnWithoutOutliersKeepsItsWholeRange() {
      double[] fence = OutlierFence.of(new double[] { 10, 20, 30, 40, 50, 60, 70 });

      assertEquals(10, fence[0]);
      assertEquals(70, fence[1]);
      assertEquals(40, fence[2]);
   }

   @Test
   void oneHugeValueIsLeftOut() {
      double[] fence = OutlierFence.of(new double[] { 10, 20, 30, 40, 50, 99999990000.0 });

      assertEquals(10, fence[0]);
      assertEquals(50, fence[1], "the largest value that is not an outlier");
      assertEquals(35, fence[2]);
   }

   @Test
   void aHugeNegativeValueIsLeftOutOfTheLowEnd() {
      double[] fence = OutlierFence.of(new double[] { -99999990000.0, 10, 20, 30, 40, 50 });

      assertEquals(10, fence[0], "the smallest value that is not an outlier");
      assertEquals(50, fence[1]);
   }

   @Test
   void aLongTailThatIsNotFarOutIsKept() {
      // 20 is well above the rest but within three interquartile ranges of the upper quartile
      double[] fence = OutlierFence.of(new double[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 20 });

      assertEquals(20, fence[1]);

      // 100 is not
      assertEquals(9, OutlierFence.of(new double[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 100 })[1]);
   }

   @Test
   void aColumnThatIsMostlyOneValueUsesThePercentiles() {
      // the interquartile range is zero: the 99th percentile fences it, not the one big value
      double[] values = new double[200];
      java.util.Arrays.fill(values, 5);
      values[199] = 1e9;
      double[] fence = OutlierFence.of(values);

      assertEquals(5, fence[1]);
      assertEquals(5, fence[0]);
   }

   @Test
   void aSingleValueAndAnEmptyColumn() {
      double[] one = OutlierFence.of(new double[] { 7 });
      assertEquals(7, one[0]);
      assertEquals(7, one[1]);
      assertEquals(7, one[2]);

      assertNull(OutlierFence.of(new double[0]));
   }

   @Test
   void duplicatesOfTheFenceValueAreHandled() {
      double[] fence = OutlierFence.of(new double[] { 1, 2, 2, 2, 2, 2, 3, 3, 3, 1e12, 1e12 });

      // both huge values are outliers, 3 is the largest value inside the fence
      assertEquals(3, fence[1]);
   }

   @Test
   void theQuantileInterpolates() {
      assertEquals(2.5, OutlierFence.quantile(new double[] { 1, 2, 3, 4 }, 0.5));
      assertEquals(1, OutlierFence.quantile(new double[] { 1, 2, 3, 4 }, 0));
      assertEquals(4, OutlierFence.quantile(new double[] { 1, 2, 3, 4 }, 1));
   }
}
