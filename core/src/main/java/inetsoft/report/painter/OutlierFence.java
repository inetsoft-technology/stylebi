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

import java.util.Arrays;

/**
 * Tells the values of a column that are outliers, so a bar scaled to the column's data is not
 * flattened by one huge value. A value is an outlier when it is more than {@link #IQR_FACTOR}
 * interquartile ranges beyond the quartiles (Tukey's "far out" fence), so a column with no such
 * value is scaled to its real range. When most of the column is one value (the interquartile range
 * is zero) the 1st and 99th percentiles are the fence instead.
 */
public final class OutlierFence {
   /** How many interquartile ranges beyond a quartile a value must be to be an outlier. */
   public static final double IQR_FACTOR = 3;

   private OutlierFence() {
   }

   /**
    * @param sorted the column's values in ascending order, none of them NaN or infinite.
    * @return {the smallest value that is not an outlier, the largest value that is not an outlier,
    *          the median}, or null when there are no values.
    */
   public static double[] of(double[] sorted) {
      int n = sorted.length;

      if(n == 0) {
         return null;
      }

      double q1 = quantile(sorted, 0.25);
      double q3 = quantile(sorted, 0.75);
      double iqr = q3 - q1;
      double low;
      double high;

      if(iqr > 0) {
         low = q1 - IQR_FACTOR * iqr;
         high = q3 + IQR_FACTOR * iqr;
      }
      else {
         low = quantile(sorted, 0.01);
         high = quantile(sorted, 0.99);
      }

      // the extremes that are inside the fence
      int hi = Arrays.binarySearch(sorted, high);
      int hiIdx = hi >= 0 ? upperEdge(sorted, hi) : -hi - 2;
      int lo = Arrays.binarySearch(sorted, low);
      int loIdx = lo >= 0 ? lowerEdge(sorted, lo) : -lo - 1;
      hiIdx = Math.max(0, Math.min(n - 1, hiIdx));
      loIdx = Math.max(0, Math.min(n - 1, loIdx));

      return new double[] { sorted[loIdx], sorted[hiIdx], quantile(sorted, 0.5) };
   }

   // the last index holding the same value, as binarySearch may land anywhere among duplicates
   private static int upperEdge(double[] sorted, int idx) {
      while(idx + 1 < sorted.length && sorted[idx + 1] == sorted[idx]) {
         idx++;
      }

      return idx;
   }

   private static int lowerEdge(double[] sorted, int idx) {
      while(idx > 0 && sorted[idx - 1] == sorted[idx]) {
         idx--;
      }

      return idx;
   }

   /** The p quantile, interpolating between the two nearest values. */
   static double quantile(double[] sorted, double p) {
      double pos = (sorted.length - 1) * p;
      int below = (int) Math.floor(pos);
      int above = (int) Math.ceil(pos);

      if(below == above) {
         return sorted[below];
      }

      return sorted[below] + (sorted[above] - sorted[below]) * (pos - below);
   }
}
