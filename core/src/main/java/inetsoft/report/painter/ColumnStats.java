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

/**
 * The numbers of a table column that a presenter can scale itself to. NaN and infinite values are
 * left out of all of them.
 *
 * @param min       the smallest value.
 * @param max       the largest value.
 * @param mean      the average value.
 * @param cappedMin the smallest value that is not an outlier (see {@link OutlierFence}).
 * @param cappedMax the largest value that is not an outlier.
 * @param median    the middle value.
 */
public record ColumnStats(double min, double max, double mean, double cappedMin, double cappedMax,
                          double median)
{
   /**
    * Stats of a column with no outliers: the capped range is the whole range and the median is
    * taken as the mean.
    */
   public ColumnStats(double min, double max, double mean) {
      this(min, max, mean, min, max, mean);
   }
}
