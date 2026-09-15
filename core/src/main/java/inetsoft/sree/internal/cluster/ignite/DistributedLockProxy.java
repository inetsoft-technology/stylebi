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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

public class DistributedLockProxy implements Lock {
   public DistributedLockProxy(String lockName, Lock realLock) {
      this.lockName = lockName;
      this.realLock = realLock;
   }

   /**
    * {@inheritDoc}
    *
    * <p>Waits indefinitely while the lock is simply held elsewhere, as {@link Lock#lock()}
    * requires — callers use the standard {@code lock(); try { … } finally { unlock(); }} idiom and
    * are not written to handle a failure to acquire. Only repeated <em>errors</em> from the
    * underlying distributed lock are bounded, at {@link #MAX_TRY_COUNT} attempts.
    *
    * <p>A long wait is logged rather than aborted, so a lock held far longer than expected is
    * visible in the log instead of silent. {@code IgniteCluster} also runs a watchdog that warns
    * about locks held beyond {@code MIN_LOCK_DURATION_MILLIS}.
    */
   @Override
   public void lock() {
      Exception exception = null;
      int attempts = 0;
      int timeouts = 0;

      while(true) {
         try {
            if(realLock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
               return;
            }

            // Held by someone else. Keep waiting: aborting here would break the Lock contract.
            // Log periodically so a lock that is never released is diagnosable.
            if(++timeouts % TIMEOUT_LOG_INTERVAL == 0) {
               LOG.warn("Still waiting for the {} lock after {} seconds", lockName,
                        (long) timeouts * LOCK_TIMEOUT_SECONDS);
            }
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock acquisition interrupted for: " + lockName, e);
         }
         catch(Exception ex) {
            exception = ex;
            LOG.debug("Failed to acquire {} lock for {} attempts: ", lockName, attempts++, ex);

            if(attempts >= MAX_TRY_COUNT) {
               break;
            }

            try {
               Thread.sleep(RETRY_DELAY_MS);
            }
            catch(InterruptedException e) {
               Thread.currentThread().interrupt();
               throw new RuntimeException("Lock acquisition interrupted for: " + lockName, e);
            }
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
   public boolean tryLock(long time, @NotNull TimeUnit unit) throws InterruptedException {
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

   private final Lock realLock;
   private final String lockName;
   private static final int MAX_TRY_COUNT = 10;
   // warn roughly once a minute while waiting (20 x 3s)
   private static final int TIMEOUT_LOG_INTERVAL = 20;
   private static final int LOCK_TIMEOUT_SECONDS = 3;
   private static final int RETRY_DELAY_MS = 3_000;
   private static final Logger LOG = LoggerFactory.getLogger(DistributedLockProxy.class);
}
