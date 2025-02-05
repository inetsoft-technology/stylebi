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

package inetsoft.web.metrics;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

public class JvmMemoryScalingMetric extends ScalingMetric {
   public JvmMemoryScalingMetric(boolean movingAverage, int capacity) {
      super(movingAverage, capacity);
   }

   @Override
   protected double calculate() {
      MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
      long used = memoryBean.getHeapMemoryUsage().getUsed();
      long max = memoryBean.getHeapMemoryUsage().getMax();
      return (double) used / max;
   }
}
