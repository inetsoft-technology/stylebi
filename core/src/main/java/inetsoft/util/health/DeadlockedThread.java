/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.health;

import java.io.Serializable;
import java.lang.management.ThreadInfo;

public final class DeadlockedThread implements Serializable {
   DeadlockedThread(ThreadInfo thread) {
      this(thread.getThreadName(), thread.getLockName(), thread.getLockOwnerName());
   }

   public DeadlockedThread(String threadName, String lockName, String lockOwnerName) {
      this.threadName = threadName;
      this.lockName = lockName;
      this.lockOwnerName = lockOwnerName;
   }

   public String getThreadName() {
      return threadName;
   }

   public String getLockName() {
      return lockName;
   }

   public String getLockOwnerName() {
      return lockOwnerName;
   }

   @Override
   public String toString() {
      return "DeadlockedThread{" +
         "threadName='" + threadName + '\'' +
         ", lockName='" + lockName + '\'' +
         ", lockOwnerName='" + lockOwnerName + '\'' +
         '}';
   }

   private final String threadName;
   private final String lockName;
   private final String lockOwnerName;
   private static final long serialVersionUID = 1L;
}
