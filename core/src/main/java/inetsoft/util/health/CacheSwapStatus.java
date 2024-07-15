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

import java.io.Serializable;

public final class CacheSwapStatus implements Serializable {
   public CacheSwapStatus() {
      this(false, 0L, false, 0L);
   }

   CacheSwapStatus(CacheSwapStatus previous) {
      long criticalMemoryTime = Long.parseLong(
         SreeEnv.getProperty("health.cacheSwap.criticalMemoryTime", "120000"));
      int excessiveWaitingThreads = Integer.parseInt(
         SreeEnv.getProperty("health.cacheSwap.excessiveWaitingThreads", "5"));
      long excessiveWaitingTime = Long.parseLong(
         SreeEnv.getProperty("health.cacheSwap.excessiveWaitingTime", "120000"));

      if(XSwapper.getMemoryState() == XSwapper.CRITICAL_MEM) {
         if(previous.criticalMemoryStart == 0L) {
            criticalMemory = false;
            criticalMemoryStart = System.currentTimeMillis();
         }
         else {
            criticalMemoryStart = previous.criticalMemoryStart;
            criticalMemory =
               System.currentTimeMillis() - criticalMemoryStart > criticalMemoryTime;
         }
      }
      else {
         criticalMemory = false;
         criticalMemoryStart = 0L;
      }

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

   public CacheSwapStatus(boolean criticalMemory, long criticalMemoryStart,
                          boolean excessiveWaiting, long excessiveWaitingStart)
   {
      this.criticalMemory = criticalMemory;
      this.criticalMemoryStart = criticalMemoryStart;
      this.excessiveWaiting = excessiveWaiting;
      this.excessiveWaitingStart = excessiveWaitingStart;
   }

   public boolean isCriticalMemory() {
      return criticalMemory;
   }

   public long getCriticalMemoryStart() {
      return criticalMemoryStart;
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
         "criticalMemory=" + criticalMemory +
         ", criticalMemoryStart=" + criticalMemoryStart +
         ", excessiveWaiting=" + excessiveWaiting +
         ", excessiveWaitingStart=" + excessiveWaitingStart +
         '}';
   }

   private final boolean criticalMemory;
   private final long criticalMemoryStart;
   private final boolean excessiveWaiting;
   private final long excessiveWaitingStart;
   private static final long serialVersionUID = 1L;
}
