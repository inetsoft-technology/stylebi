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
package inetsoft.util.health;

import inetsoft.sree.SreeEnv;
import inetsoft.util.swap.XSwapper;

import java.io.Serial;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

public final class CacheSwapStatus implements Serializable {
   public CacheSwapStatus() {
      this(false, 0L);
   }

   CacheSwapStatus(CacheSwapStatus previous) {
      int excessiveWaitingThreads = Integer.parseInt(
         SreeEnv.getProperty("health.cacheSwap.excessiveWaitingThreads", "20"));
      long excessiveWaitingTime = Long.parseLong(SreeEnv.getProperty(
         "health.cacheSwap.excessiveWaitingTime",
         Long.toString(TimeUnit.MINUTES.convert(30L, TimeUnit.MILLISECONDS))));

      if(XSwapper.getWaitingThreadCount() > excessiveWaitingThreads) {
         if(previous.excessiveWaitingStart == 0L) {
            excessiveWaiting = false;
            excessiveWaitingStart = System.currentTimeMillis();
         }
         else {
            excessiveWaitingStart = previous.excessiveWaitingStart;
            excessiveWaiting =
               System.currentTimeMillis() - excessiveWaitingStart > excessiveWaitingTime;
         }
      }
      else {
         excessiveWaiting = false;
         excessiveWaitingStart = 0L;
      }
   }

   public CacheSwapStatus(boolean excessiveWaiting, long excessiveWaitingStart) {
      this.excessiveWaiting = excessiveWaiting;
      this.excessiveWaitingStart = excessiveWaitingStart;
   }

   public boolean isExcessiveWaiting() {
      return excessiveWaiting;
   }

   public long getExcessiveWaitingStart() {
      return excessiveWaitingStart;
   }

   @Override
   public String toString() {
      return "CacheSwapStatus{" +
         "excessiveWaiting=" + excessiveWaiting +
         ", excessiveWaitingStart=" + excessiveWaitingStart +
         '}';
   }

   private final boolean excessiveWaiting;
   private final long excessiveWaitingStart;
   @Serial
   private static final long serialVersionUID = 1L;
}
