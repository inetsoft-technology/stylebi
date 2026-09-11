/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DistributedLockProxy.lock() retry scenario table:
 *  [Acquired]        tryLock succeeds immediately          -> returns, no retry
 *  [Late success]    tryLock times out repeatedly, then succeeds -> returns without throwing
 *  [Contention]      tryLock keeps returning false         -> keeps waiting, does NOT throw
 *
 * The error path (tryLock throwing repeatedly, bounded at MAX_TRY_COUNT) is unchanged legacy
 * behavior and is not covered here: exercising it means sleeping through the full RETRY_DELAY_MS
 * budget, which costs ~27s for no coverage of anything this change touched.
 *
 * The contention case is the important one. lock() must honour the java.util.concurrent.locks.Lock
 * contract and block until the lock is acquired: every caller uses the
 * lock(); try { ... } finally { unlock(); } idiom and none of them handle a failure to acquire.
 * Bounding that wait would turn ordinary contention -- an MV generation or asset import holding
 * the lock for a while -- into a hard failure. Only repeated *errors* from the underlying
 * distributed lock are bounded, at MAX_TRY_COUNT.
 */
@Tag("core")
class DistributedLockProxyTest {
   // [Scenario: acquired] the underlying lock is granted on the first try -> no retry loop
   @Test
   void lock_acquiredImmediately_returnsWithoutRetrying() throws Exception {
      Lock realLock = mock(Lock.class);
      when(realLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);

      new DistributedLockProxy("test", realLock).lock();

      verify(realLock, times(1)).tryLock(anyLong(), any(TimeUnit.class));
   }

   // [Scenario: late success] many timeouts followed by success -> returns normally. The retry
   // budget that bounds the *error* path must not apply here, so this deliberately times out more
   // than MAX_TRY_COUNT times before succeeding.
   @Test
   void lock_timesOutMoreThanMaxTryCountThenSucceeds_returnsWithoutThrowing() throws Exception {
      Lock realLock = mock(Lock.class);
      Boolean[] timeouts = new Boolean[MAX_TRY_COUNT * 2];
      Arrays.fill(timeouts, Boolean.FALSE);
      when(realLock.tryLock(anyLong(), any(TimeUnit.class)))
         .thenReturn(Boolean.FALSE, Arrays.copyOf(timeouts, timeouts.length))
         .thenReturn(Boolean.TRUE);

      assertTimeoutPreemptively(
         java.time.Duration.ofSeconds(30),
         () -> new DistributedLockProxy("test", realLock).lock(),
         "lock() must keep waiting through contention rather than giving up");

      verify(realLock, times(timeouts.length + 2)).tryLock(anyLong(), any(TimeUnit.class));
   }

   // [Scenario: contention] the lock is simply held elsewhere for a long time. lock() must keep
   // waiting -- the Lock contract -- not throw. Guards against bounding the wait, which would turn
   // ordinary contention into a hard failure at all 34 cluster-lock call sites, none of which
   // handle a failure to acquire.
   @Test
   void lock_heldElsewhere_keepsWaitingInsteadOfThrowing() throws Exception {
      Lock realLock = mock(Lock.class);
      AtomicInteger calls = new AtomicInteger();
      // Return false far more often than MAX_TRY_COUNT, then succeed, so the test still finishes.
      when(realLock.tryLock(anyLong(), any(TimeUnit.class)))
         .thenAnswer(inv -> calls.incrementAndGet() > MAX_TRY_COUNT * 5);

      assertTimeoutPreemptively(
         java.time.Duration.ofSeconds(30),
         () -> new DistributedLockProxy("test", realLock).lock());

      assertTrue(calls.get() > MAX_TRY_COUNT,
                 "lock() gave up after " + calls.get() + " attempts; it must block until acquired");
   }

   private static final int MAX_TRY_COUNT = 10;
}
