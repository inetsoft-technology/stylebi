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

import java.io.Serial;
import java.io.Serializable;
import java.lang.management.ThreadInfo;
import java.util.Arrays;

public final class DeadlockStatus implements Serializable {
   public DeadlockStatus() {
      this(0, new DeadlockedThread[0]);
   }

   DeadlockStatus(ThreadInfo[] threads, String stallReason) {
      this(
         threads.length,
         Arrays.stream(threads).map(DeadlockedThread::new).toArray(DeadlockedThread[]::new),
         stallReason);
   }

   public DeadlockStatus(int deadlockedThreadCount, DeadlockedThread[] deadlockedThreads) {
      this(deadlockedThreadCount, deadlockedThreads, null);
   }

   public DeadlockStatus(int deadlockedThreadCount, DeadlockedThread[] deadlockedThreads,
                         String stallReason)
   {
      this.deadlockedThreadCount = deadlockedThreadCount;
      this.deadlockedThreads = deadlockedThreads;
      this.stallReason = stallReason;
   }

   public int getDeadlockedThreadCount() {
      return deadlockedThreadCount;
   }

   public DeadlockedThread[] getDeadlockedThreads() {
      return deadlockedThreads;
   }

   /**
    * Get why a lock stall is considered unreleased, i.e. its timeout did not free the
    * stalled thread (bug #76967), or {@code null} if there is none.
    */
   public String getStallReason() {
      return stallReason;
   }

   /**
    * Check if a lock stall is unreleased.
    */
   public boolean isStalled() {
      return stallReason != null;
   }

   @Override
   public String toString() {
      return "DeadlockStatus{" +
         "deadlockedThreadCount=" + deadlockedThreadCount +
         ", deadlockedThreads=" + Arrays.toString(deadlockedThreads) +
         ", stallReason='" + stallReason + '\'' +
         '}';
   }

   private final int deadlockedThreadCount;
   private final DeadlockedThread[] deadlockedThreads;
   private final String stallReason;
   @Serial
   private static final long serialVersionUID = 1L;
}
