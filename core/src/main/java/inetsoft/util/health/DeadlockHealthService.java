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

import inetsoft.util.SingletonManager;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public class DeadlockHealthService {
   public static DeadlockHealthService getInstance() {
      return SingletonManager.getInstance(DeadlockHealthService.class);
   }

   public DeadlockStatus getStatus() {
      ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
      long[] deadlocks = threadBean.findDeadlockedThreads();

      if(deadlocks != null && deadlocks.length > 0) {
         return new DeadlockStatus(threadBean.getThreadInfo(deadlocks));
      }

      return new DeadlockStatus();
   }

}
