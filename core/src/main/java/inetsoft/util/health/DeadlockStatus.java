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
import java.lang.management.ThreadInfo;
import java.util.Arrays;

public final class DeadlockStatus implements Serializable {
   public DeadlockStatus() {
      this(0, new DeadlockedThread[0]);
   }

   DeadlockStatus(ThreadInfo[] threads) {
      this(
         threads.length,
         Arrays.stream(threads).map(DeadlockedThread::new).toArray(DeadlockedThread[]::new));
   }

   public DeadlockStatus(int deadlockedThreadCount, DeadlockedThread[] deadlockedThreads) {
      this.deadlockedThreadCount = deadlockedThreadCount;
      this.deadlockedThreads = deadlockedThreads;
   }

   public int getDeadlockedThreadCount() {
      return deadlockedThreadCount;
   }

   public DeadlockedThread[] getDeadlockedThreads() {
      return deadlockedThreads;
   }

   @Override
   public String toString() {
      return "DeadlockStatus{" +
         "deadlockedThreadCount=" + deadlockedThreadCount +
         ", deadlockedThreads=" + Arrays.toString(deadlockedThreads) +
         '}';
   }

   private final int deadlockedThreadCount;
   private final DeadlockedThread[] deadlockedThreads;
   private static final long serialVersionUID = 1L;
}
