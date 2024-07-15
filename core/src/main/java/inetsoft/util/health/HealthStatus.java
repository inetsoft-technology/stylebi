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

import java.io.Serializable;

public final class HealthStatus implements Serializable {
   public HealthStatus(CacheSwapStatus cacheSwapStatus,
                       DeadlockStatus deadlockStatus,
                       OutOfMemoryStatus outOfMemoryStatus,
                       ReportFailureStatus reportFailureStatus)
   {
      this.cacheSwapStatus = cacheSwapStatus;
      this.deadlockStatus = deadlockStatus;
      this.outOfMemoryStatus = outOfMemoryStatus;
      this.reportFailureStatus = reportFailureStatus;
   }

   public boolean isDown() {
      return cacheSwapStatus.isCriticalMemory() || cacheSwapStatus.isExcessiveWaiting() ||
         deadlockStatus.getDeadlockedThreadCount() > 0 ||
         outOfMemoryStatus.isOutOfMemory() ||
         reportFailureStatus.isExcessiveFailures();
   }

   public CacheSwapStatus getCacheSwapStatus() {
      return cacheSwapStatus;
   }

   public DeadlockStatus getDeadlockStatus() {
      return deadlockStatus;
   }

   public OutOfMemoryStatus getOutOfMemoryStatus() {
      return outOfMemoryStatus;
   }

   public ReportFailureStatus getReportFailureStatus() {
      return reportFailureStatus;
   }

   @Override
   public String toString() {
      return "HealthStatus{" +
         "cacheSwapStatus=" + cacheSwapStatus +
         ", deadlockStatus=" + deadlockStatus +
         ", outOfMemoryStatus=" + outOfMemoryStatus +
         ", reportFailureStatus=" + reportFailureStatus +
         '}';
   }

   private final CacheSwapStatus cacheSwapStatus;
   private final DeadlockStatus deadlockStatus;
   private final OutOfMemoryStatus outOfMemoryStatus;
   private final ReportFailureStatus reportFailureStatus;
   private static final long serialVersionUID = 1L;
}
