/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.metrics;

import inetsoft.sree.SreeEnv;
import inetsoft.util.swap.XSwapper;

public class CacheSwapWaitScalingMetric extends ScalingMetric {
   public CacheSwapWaitScalingMetric(boolean movingAverage, int capacity) {
      super(movingAverage, capacity);
   }

   @Override
   protected double calculate() {
      long threshold = Long.parseLong(
         SreeEnv.getProperty("metric.cacheSwap.excessiveWaitingThreads", "10"));
      double count = XSwapper.getWaitingThreadCount();
      return Math.clamp(count / threshold, 0D, 1D);
   }
}
