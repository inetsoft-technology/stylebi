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

import inetsoft.util.ConfigurationContext;
import inetsoft.util.stall.StallWatchdog;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.function.Supplier;

@Service
@Lazy
public class DeadlockHealthService {
   public DeadlockHealthService() {
      this(StallWatchdog::getUnreleasedStallReason, DeadlockHealthService::findDeadlockedThreads);
   }

   /**
    * @param stallReason       why a lock stall is unreleased, or {@code null}.
    * @param deadlockedThreads the threads of a JVM deadlock, or {@code null} (or none) if there
    *                          is no deadlock.
    */
   DeadlockHealthService(Supplier<String> stallReason, Supplier<ThreadInfo[]> deadlockedThreads) {
      this.stallReason = stallReason;
      this.deadlockedThreads = deadlockedThreads;
   }

   public static DeadlockHealthService getInstance() {
      return ConfigurationContext.getContext().getSpringBean(DeadlockHealthService.class);
   }

   public DeadlockStatus getStatus() {
      ThreadInfo[] deadlocks = deadlockedThreads.get();
      // a lock stall that its timeout did not release (bug #76967)
      String stall = stallReason.get();

      if(deadlocks != null && deadlocks.length > 0) {
         // the watchdog reports this deadlock too, the threads already show it
         return new DeadlockStatus(deadlocks, StallWatchdog.withoutJvmDeadlock(stall));
      }

      return stall != null ?
         new DeadlockStatus(0, new DeadlockedThread[0], stall) : new DeadlockStatus();
   }

   private static ThreadInfo[] findDeadlockedThreads() {
      ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
      long[] deadlocks = threadBean.findDeadlockedThreads();
      return deadlocks == null || deadlocks.length == 0 ? null : threadBean.getThreadInfo(deadlocks);
   }

   private final Supplier<String> stallReason;
   private final Supplier<ThreadInfo[]> deadlockedThreads;
}
