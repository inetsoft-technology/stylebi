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

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;

public abstract class OperatingSystemScalingMetric extends ScalingMetric {
   public OperatingSystemScalingMetric(boolean movingAverage, int capacity) {
      super(movingAverage, capacity);
   }

   @Override
   protected final double calculate() {
      java.lang.management.OperatingSystemMXBean bean =
         ManagementFactory.getOperatingSystemMXBean();

      if(bean instanceof OperatingSystemMXBean os) {
         return calculate(os);
      }

      return 0D;
   }

   protected abstract double calculate(OperatingSystemMXBean bean);
}
