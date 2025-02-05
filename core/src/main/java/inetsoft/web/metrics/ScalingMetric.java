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

public abstract class ScalingMetric {
   public ScalingMetric(boolean movingAverage, int capacity) {
      this.movingAverage = movingAverage;
      this.capacity = capacity;
      this.buffer = movingAverage ? new double[capacity] : null;
   }

   public final double get() {
      return currentValue;
   }

   public final void update() {
      double nextValue = calculate();

      if(movingAverage) {
         buffer[writePos++] = nextValue;

         if(writePos == capacity) {
            writePos = 0;
         }

         double sum = 0D;

         for(double value : buffer) {
            sum += value;
         }

         currentValue = sum / buffer.length;
      }
      else {
         currentValue = nextValue;
      }
   }

   protected abstract double calculate();

   private final boolean movingAverage;
   private final int capacity;
   private final double[] buffer;
   private int writePos = 0;
   private volatile double currentValue;
}
