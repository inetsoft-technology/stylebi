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

import java.util.EmptyStackException;
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
      //
      // The frame that took the write lock unwinds first: since bug #76905 the failed restore
      // records the write it could not restore as skipped, so that frame's unlockWrite() pops
      // its own entry (without touching the real lock) instead of the read below it.
      assertDoesNotThrow(lock::unlockWrite,
                          "the write frame's unlock after the timeout");
      assertDoesNotThrow(lock::unlockRead,
                          "the lock-state stack desynced from the real lock after the timeout");

      assertLockFree(lock);
   }

   /**
    * Bug #76905: after a bounded write restore timed out, the caller's
    * {@code finally unlockWrite()} (CalcTableVSAQuery.getTableLens()) popped an empty stack and
    * threw EmptyStackException, which replaced the informative IllegalStateException and left
    * the client with "An unexpected error has occurred". Every frame that owned an entry the
    * failed restore did not get back must be able to unwind cleanly.
    */
   @Test
   void unlockAfterFailedWriteRestoreDoesNotThrowEmptyStack() throws InterruptedException {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock(200);
      lock.lockWrite(); // outer caller, e.g. CoreLifecycleService
      lock.lockWrite(); // CalcTableVSAQuery.getTableLens()
      lock.unlockAll(); // VSAQuery.getDataWithoutSandboxLock()

      runWhileAnotherThreadHoldsRead(lock, () ->
         assertThrows(IllegalStateException.class, lock::restoreLocks));

      assertDoesNotThrow(lock::unlockWrite, "inner frame's unlock after the failed restore");
      assertDoesNotThrow(lock::unlockWrite, "outer frame's unlock after the failed restore");
      assertLockFree(lock);

      // the stack is balanced again: the thread can use the lock normally
      lock.lockWrite();
      lock.unlockWrite();
      assertLockFree(lock);
   }

   /**
    * Same as above with a partial restore: the outer read is restored, the inner write is not.
    * The inner unlockWrite() used to pop the outer read entry and throw
    * IllegalMonitorStateException on the write lock it did not hold.
    */
   @Test
   void unlockAfterPartialRestoreUnwindsEachFrame() throws InterruptedException {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock(200);
      lock.lockRead();  // e.g. ViewsheetSandbox.getVSTableLens0()
      lock.lockWrite(); // e.g. CalcTableVSAQuery.getTableLens()
      lock.unlockAll();

      runWhileAnotherThreadHoldsRead(lock, () ->
         assertThrows(IllegalStateException.class, lock::restoreLocks));

      assertDoesNotThrow(lock::unlockWrite, "inner frame's unlock after the partial restore");
      assertDoesNotThrow(lock::unlockRead, "outer frame's unlock after the partial restore");
      assertLockFree(lock);
   }

   /** The failed-restore tolerance must not hide a genuine unlock without a lock. */
   @Test
   void unbalancedUnlockStillFailsFast() {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock();
      assertThrows(EmptyStackException.class, lock::unlockWrite);
      assertThrows(EmptyStackException.class, lock::unlockRead);
   }

   /**
    * Bug #76905: in non-blocking mode (a thread running a script) a lock that is not available
    * is skipped instead of waited for, and the matching unlock follows the recorded decision.
    */
   @Test
   void nonBlockingLockIsSkippedWhileAnotherThreadHoldsWrite() throws InterruptedException {
      // per thread, like JavaScriptEngine.isScriptThread()
      ThreadLocal<Boolean> inScript = ThreadLocal.withInitial(() -> false);
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock(inScript::get);

      runWhileAnotherThreadHoldsWrite(lock, () -> {
         inScript.set(true);

         try {
            lock.lockRead();
            assertEquals(1, lock.getSkippedCount());
            lock.lockWrite();
            assertEquals(2, lock.getSkippedCount());
            // the decision is replayed even though the predicate changed in between
            inScript.set(false);
            assertDoesNotThrow(lock::unlockWrite);
            assertDoesNotThrow(lock::unlockRead);
         }
         finally {
            inScript.set(false);
         }
      });

      assertLockFree(lock);
   }

   @Test
   void nonBlockingLockIsTakenWhenAvailable() {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock(() -> true);
      lock.lockRead();
      assertEquals(0, lock.getSkippedCount());
      // an upgrade would have to drop the read first, and could not get it back without
      // blocking, so the write is skipped while the read is held
      lock.lockWrite();
      assertEquals(1, lock.getSkippedCount());
      lock.unlockWrite();
      lock.unlockRead();
      assertLockFree(lock);

      lock.lockWrite();
      lock.lockRead();
      assertEquals(1, lock.getSkippedCount());
      lock.unlockRead();
      lock.unlockWrite();
      assertLockFree(lock);
   }

   /**
    * In non-blocking mode unlockAll() releases only what was taken in that mode; locks taken
    * before (by the caller that started the script) stay held, because they could not be taken
    * back without blocking.
    */
   @Test
   void nonBlockingUnlockAllKeepsLocksTakenBefore() throws Exception {
      // per thread, like JavaScriptEngine.isScriptThread()
      ThreadLocal<Boolean> inScript = ThreadLocal.withInitial(() -> false);
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock(inScript::get);
      lock.lockWrite();

      try {
         inScript.set(true);
         lock.lockRead();

         try {
            lock.unlockAll();

            try {
               Thread reader = new Thread(() -> {
                  lock.lockRead();
                  lock.unlockRead();
               });
               reader.setDaemon(true);
               reader.start();
               reader.join(300);
               assertTrue(reader.isAlive(), "the write lock taken before the script was released");
            }
            finally {
               lock.restoreLocks();
            }
         }
         finally {
            lock.unlockRead();
            inScript.set(false);
         }
      }
      finally {
         lock.unlockWrite();
      }

      assertLockFree(lock);
   }

   /**
    * Releasing an upgraded write lock re-takes the rewound read locks before releasing the
    * write (a downgrade), so it cannot wait behind another thread's queued writer.
    */
   @Test
   void downgradeDoesNotWaitBehindQueuedWriter() throws InterruptedException {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock();
      lock.lockRead();
      lock.lockWrite();

      CountDownLatch writerHolds = new CountDownLatch(1);
      Thread writer = new Thread(() -> {
         lock.lockWrite();
         writerHolds.countDown();

         try {
            Thread.sleep(3000);
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }
         finally {
            lock.unlockWrite();
         }
      });
      writer.setDaemon(true);
      writer.start();

      long deadline = System.currentTimeMillis() + 5000;

      while(writer.getState() != Thread.State.WAITING && System.currentTimeMillis() < deadline) {
         Thread.sleep(10);
      }

      long start = System.currentTimeMillis();
      lock.unlockWrite();
      long elapsed = System.currentTimeMillis() - start;

      assertTrue(elapsed < 1000, "unlockWrite() waited " + elapsed + "ms for the queued writer");
      assertEquals(1, writerHolds.getCount(), "the writer got the lock while the read was held");

      lock.unlockRead();
      writer.join(10000);
      assertFalse(writer.isAlive());
      assertLockFree(lock);
   }

   private static void runWhileAnotherThreadHoldsWrite(UpgradableReadWriteLock lock,
                                                       Runnable action)
      throws InterruptedException
   {
      CountDownLatch writerHoldsLock = new CountDownLatch(1);
      CountDownLatch releaseWriter = new CountDownLatch(1);

      Thread writer = new Thread(() -> {
         lock.lockWrite();
         writerHoldsLock.countDown();

         try {
            releaseWriter.await();
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }
         finally {
            lock.unlockWrite();
         }
      });
      writer.setDaemon(true);
      writer.start();

      try {
         assertTrue(writerHoldsLock.await(5, TimeUnit.SECONDS),
                    "writer thread never acquired the write lock");
         action.run();
      }
      finally {
         releaseWriter.countDown();
         writer.join(5000);
      }

      assertFalse(writer.isAlive(), "writer thread did not exit");
   }

   private static void runWhileAnotherThreadHoldsRead(UpgradableReadWriteLock lock,
                                                      Runnable action)
      throws InterruptedException
   {
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
      reader.setDaemon(true);
      reader.start();

      try {
         assertTrue(readerHoldsLock.await(5, TimeUnit.SECONDS),
                    "reader thread never acquired the read lock");
         action.run();
      }
      finally {
         releaseReader.countDown();
         reader.join(5000);
      }

      assertFalse(reader.isAlive(), "reader thread did not exit");
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
