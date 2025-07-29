/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.internal.cluster.ignite;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.*;

public class DistributedLockProxy implements Lock {
   public DistributedLockProxy(String lockName) {
      this.lockName = lockName;
   }

   public void setRealLock(Lock lock) {
      this.realLock = lock;
   }

   @Override
   public void lock() {
      Exception exception = null;

      for(int i = 0; i < MAX_TRY_COUNT; i++) {
         try {
            realLock.lock();
            return;
         }
         catch(Exception ex) {
            exception = ex;
            LOG.debug("Failed to acquire {} lock for {} attempts: ", lockName, i, ex);
         }

         try {
            Thread.sleep(3_000);
         }
         catch(InterruptedException ignore) {
         }
      }

      throw new RuntimeException("Failed to acquire target lock after " + MAX_TRY_COUNT + " attempts: " + lockName, exception);
   }

   @Override
   public void lockInterruptibly() throws InterruptedException {
      realLock.lockInterruptibly();
   }

   @Override
   public boolean tryLock() {
      return realLock.tryLock();
   }

   @Override
   public boolean tryLock(long time, @NotNull java.util.concurrent.TimeUnit unit) throws InterruptedException {
      return realLock.tryLock(time, unit);
   }

   @Override
   public void unlock() {
      realLock.unlock();
   }

   @NotNull
   @Override
   public Condition newCondition() {
      return realLock.newCondition();
   }

   private Lock realLock;
   private String lockName;
   private static final int MAX_TRY_COUNT = 10;
   private static final Logger LOG = LoggerFactory.getLogger(DistributedLockProxy.class);
}
