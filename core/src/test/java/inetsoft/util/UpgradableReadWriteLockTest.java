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
package inetsoft.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class UpgradableReadWriteLockTest {
   @Test
   void writeLockRestoredAfterUnlockAll() {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock();
      lock.lockWrite();

      try {
         lock.unlockAll();
         lock.restoreLocks();
      }
      finally {
         assertDoesNotThrow(lock::unlockWrite);
      }

      assertLockFree(lock);
   }

   @Test
   void readLockRestoredAfterUnlockAll() {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock();
      lock.lockRead();

      try {
         lock.unlockAll();
         lock.restoreLocks();
      }
      finally {
         assertDoesNotThrow(lock::unlockRead);
      }

      assertLockFree(lock);
   }

   /**
    * Bug #75813: a nested unlockAll()/restoreLocks() pair used to clear the single-slot
    * saved state, so the enclosing restoreLocks() restored nothing and the outer
    * unlockWrite() failed with EmptyStackException.
    */
   @Test
   void nestedUnlockAllDoesNotDiscardOuterState() {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock();
      lock.lockWrite();

      try {
         // outer region: mirrors ViewsheetSandbox.doExecuteData() releasing the locks
         // held by CoreLifecycleService.refreshViewsheet() for the duration of a fetch
         lock.unlockAll();

         try {
            // a read lock taken while the outer region is unlocked, e.g. by
            // TableVSAQuery.getTableLens()
            lock.lockRead();

            try {
               // nested region: the source assembly of a vs-assembly binding is fetched
               // in the middle of the outer fetch
               lock.unlockAll();
               lock.restoreLocks();
            }
            finally {
               lock.unlockRead();
            }
         }
         finally {
            lock.restoreLocks();
         }
      }
      finally {
         assertDoesNotThrow(lock::unlockWrite);
      }

      assertLockFree(lock);
   }

   @Test
   void deeplyNestedUnlockAllRestoresAllLevels() {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock();
      lock.lockWrite();
      lock.lockRead();

      try {
         lock.unlockAll();
         lock.unlockAll();
         lock.unlockAll();
         lock.restoreLocks();
         lock.restoreLocks();
         lock.restoreLocks();
      }
      finally {
         assertDoesNotThrow(lock::unlockRead);
         assertDoesNotThrow(lock::unlockWrite);
      }

      assertLockFree(lock);
   }

   @Test
   void unmatchedRestoreLocksIsNoOp() {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock();
      lock.lockWrite();

      try {
         assertDoesNotThrow(lock::restoreLocks);
      }
      finally {
         assertDoesNotThrow(lock::unlockWrite);
      }

      assertLockFree(lock);
   }

   @Test
   void writeLockUpgradeReleasesAndReacquiresReadLock() {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock();
      lock.lockRead();

      try {
         lock.lockWrite();
         lock.unlockWrite();
      }
      finally {
         assertDoesNotThrow(lock::unlockRead);
      }

      assertLockFree(lock);
   }

   /**
    * Bug #76907: restoreLocks() used to reacquire the write lock with an unconditional
    * block. If another thread held the read lock and could not release it until this
    * thread first released some unrelated resource it was still holding (in production,
    * the GraalVM script engine's own execution lock -- see UpgradableReadWriteLock's own
    * class-level doc on restoreLocks()), the two threads deadlocked forever: this thread
    * never returned from restoreLocks() to release what the other thread was waiting on.
    * restoreLocks() must now fail fast with a bounded wait instead of hanging, so the
    * caller's own exception handling can recover and the other thread can proceed.
    */
   @Test
   void restoreLocksTimesOutInsteadOfHangingWhenWriteLockUnavailable() throws InterruptedException {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock(200);
      lock.lockWrite();
      lock.unlockAll();

      CountDownLatch readerHoldsLock = new CountDownLatch(1);
      CountDownLatch releaseReader = new CountDownLatch(1);

      Thread reader = new Thread(() -> {
         lock.lockRead();
         readerHoldsLock.countDown();

         try {
            releaseReader.await();
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }
         finally {
            lock.unlockRead();
         }
      });
      reader.start();

      try {
         assertTrue(readerHoldsLock.await(5, TimeUnit.SECONDS),
                     "reader thread never acquired the read lock");

         assertThrows(IllegalStateException.class, lock::restoreLocks,
                      "restoreLocks() should time out instead of hanging forever when " +
                      "another thread holds the read lock");
      }
      finally {
         releaseReader.countDown();
         reader.join(5000);
      }

      assertFalse(reader.isAlive(), "reader thread did not exit");
      assertLockFree(lock);
   }

   /**
    * Bug #76907 review round 1: lockWriteBounded() rewinds any read locks it finds on the
    * per-thread stack before attempting the write lock, but (before this fix) never re-locked
    * them on a timeout -- only on success, via the pushed write level. That's invisible for a
    * bare write restore (stack starts empty, nothing to rewind), which is why
    * {@link #restoreLocksTimesOutInsteadOfHangingWhenWriteLockUnavailable()} stayed green
    * despite the gap. It surfaces on a *nested* upgrade: restoreLocks() first replays a saved
    * read level (stack becomes {@code [0]}), then lockWriteBounded() rewinds that same read
    * (physically unlocking it without popping the stack) before trying -- and failing -- to
    * get the write lock. Without re-locking the rewind, the stack keeps claiming a read lock
    * this thread no longer holds, so a later, unrelated unlockRead()/lockWrite() call on the
    * same pooled thread throws IllegalMonitorStateException against the real lock.
    */
   @Test
   void nestedUpgradeTimeoutDoesNotDesyncLockStateStack() throws InterruptedException {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock(200);

      // Build the exact stack shape restoreLocks() replays after a nested read -> write
      // upgrade: lockRead(), then a reentrant lockWrite() on the same thread (mirrors
      // CalcTableVSAQuery.getTableLens()'s box.lockWrite() reached reentrantly from a script
      // that is itself running inside an already read-locked getData() call), then
      // unlockAll() saves [1, 0] and leaves the thread holding nothing.
      lock.lockRead();
      lock.lockWrite();
      lock.unlockAll();

      CountDownLatch readerHoldsLock = new CountDownLatch(1);
      CountDownLatch releaseReader = new CountDownLatch(1);

      Thread otherReader = new Thread(() -> {
         lock.lockRead();
         readerHoldsLock.countDown();

         try {
            releaseReader.await();
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }
         finally {
            lock.unlockRead();
         }
      });
      otherReader.start();

      try {
         assertTrue(readerHoldsLock.await(5, TimeUnit.SECONDS),
                     "other reader thread never acquired the read lock");

         // restoreLocks() replays lockRead() first (succeeds immediately -- shared), then
         // lockWriteBounded() for the saved write level, which times out because the other
         // thread's read lock is still held.
         assertThrows(IllegalStateException.class, lock::restoreLocks,
                      "restoreLocks() should time out instead of hanging");
      }
      finally {
         releaseReader.countDown();
         otherReader.join(5000);
      }

      assertFalse(otherReader.isAlive(), "other reader thread did not exit");

      // The regression: getStack() must still agree with the real lock. Before the fix,
      // restoreLocks()'s successful lockRead() replay left the stack claiming a held read
      // lock that lockWriteBounded()'s rewind had already physically released and never
      // restored on timeout, so this threw IllegalMonitorStateException instead of
      // completing cleanly.
      assertDoesNotThrow(lock::unlockRead,
                          "the lock-state stack desynced from the real lock after the timeout");

      assertLockFree(lock);
   }

   /**
    * Verifies no lock is left held by the calling thread: another thread must be able to
    * acquire the write lock immediately.
    */
   private void assertLockFree(UpgradableReadWriteLock lock) {
      Thread thread = new Thread(() -> {
         lock.lockWrite();
         lock.unlockWrite();
      });

      thread.start();

      try {
         thread.join(5000);
      }
      catch(InterruptedException ex) {
         Thread.currentThread().interrupt();
         fail("Interrupted while waiting for the write lock");
      }

      assertFalse(thread.isAlive(), "lock is still held by the test thread");
   }
}
