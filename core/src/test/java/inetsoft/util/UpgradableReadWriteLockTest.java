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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EmptyStackException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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

   /**
    * Bug #76986: restoring an upgrade ({@code [READ, WRITE]}) while another thread holds a read
    * lock times out on the bounded write. The read locks rewound for it used to be taken back
    * with a plain lock(), which parks behind a writer that queued during the bounded wait: the
    * bound fired but the thread never returned.
    */
   @Test
   void upgradeRestoreFailsWithinBoundWhenWriterQueuesDuringWait() throws Exception {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock(BOUND_MS);
      Restorer restorer = new Restorer(lock, () -> {
         lock.lockRead();
         lock.lockWrite();
         lock.unlockAll();
      }, () -> {
         lock.unlockWrite();
         lock.unlockRead();
      });

      restorer.awaitSaved();
      // released by the test, not when the restore returns: the writer then queues behind the
      // reader even if the bound expired first (the outcome is the same either way)
      CountDownLatch release = new CountDownLatch(1);
      Peer reader = new Peer(lock::lockRead, lock::unlockRead, release);
      reader.awaitHolds();
      restorer.restore();
      restorer.awaitInBoundedWait();
      Peer writer = new Peer(lock::lockWrite, lock::unlockWrite, release);
      awaitState(writer.thread, Thread.State.WAITING);

      try {
         restorer.assertFailedWithinBound();
      }
      finally {
         release.countDown();
      }

      reader.join();
      writer.join();
      assertLockFree(lock);
   }

   /**
    * Bug #76986: for an upgrade the saved read level was re-locked with a plain lock() before
    * the bounded write was attempted at all, so a writer that was already queued behind
    * another reader parked the restore before any bound applied.
    */
   @Test
   void upgradeRestoreFailsWithinBoundWhenWriterAlreadyQueued() throws Exception {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock(BOUND_MS);
      Restorer restorer = new Restorer(lock, () -> {
         lock.lockRead();
         lock.lockWrite();
         lock.unlockAll();
      }, () -> {
         lock.unlockWrite();
         lock.unlockRead();
      });

      restorer.awaitSaved();
      Peer reader = new Peer(lock::lockRead, lock::unlockRead, restorer.done);
      reader.awaitHolds();
      Peer writer = new Peer(lock::lockWrite, lock::unlockWrite, restorer.done);
      awaitState(writer.thread, Thread.State.WAITING);
      restorer.restore();

      restorer.assertFailedWithinBound();
      reader.join();
      writer.join();
      assertLockFree(lock);
   }

   /**
    * Bug #76986: same as above with a writer that holds the write lock when the upgrade is
    * restored. The restore cannot get the read back either, so it must leave this thread
    * holding nothing, with every entry recorded (and counted) as skipped.
    */
   @Test
   void upgradeRestoreFailsWithinBoundWhenWriterHoldsLock() throws Exception {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock(BOUND_MS);
      Restorer restorer = new Restorer(lock, () -> {
         lock.lockRead();
         lock.lockWrite();
         lock.unlockAll();
      }, () -> {
         ReentrantReadWriteLock raw = rawLock(lock);
         assertEquals(0, raw.getReadHoldCount(), "physical reads after the failed restore");
         assertFalse(raw.isWriteLockedByCurrentThread());
         assertEquals(2, lock.getSkippedCount(), "the read and the write were not restored");
         lock.unlockWrite();
         lock.unlockRead();
         assertThrows(EmptyStackException.class, lock::unlockRead);
      });

      restorer.awaitSaved();
      Peer writer = new Peer(lock::lockWrite, lock::unlockWrite, restorer.done);
      writer.awaitHolds();
      restorer.restore();

      restorer.assertFailedWithinBound();
      writer.join();
      assertLockFree(lock);
   }

   /**
    * Bug #76986: every read level below the write is recorded, not only the one the restore
    * started from, so each frame unwinds its own entry.
    */
   @Test
   void upgradeRestoreOfTwoReadsFailsWithinBoundWhenWriterHoldsLock() throws Exception {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock(BOUND_MS);
      Restorer restorer = new Restorer(lock, () -> {
         lock.lockRead();
         lock.lockRead();
         lock.lockWrite();
         lock.unlockAll();
      }, () -> {
         assertEquals(0, rawLock(lock).getReadHoldCount(), "physical reads after the failed restore");
         assertEquals(3, lock.getSkippedCount(), "the two reads and the write were not restored");
         lock.unlockWrite();
         lock.unlockRead();
         lock.unlockRead();
         assertThrows(EmptyStackException.class, lock::unlockRead);
      });

      restorer.awaitSaved();
      Peer writer = new Peer(lock::lockWrite, lock::unlockWrite, restorer.done);
      writer.awaitHolds();
      restorer.restore();

      restorer.assertFailedWithinBound();
      writer.join();
      assertLockFree(lock);
   }

   /**
    * Bug #76986: the reads a bounded write restore rewinds must not be taken back with a
    * blocking lock() when a writer took the lock during the bounded wait. They are recorded as
    * skipped instead, and the thread holds nothing.
    *
    * <p>The bounded wait is ended by an interrupt, which takes the same failure path as a
    * timeout, once the writer is known to hold the lock. With a short bound the writer would
    * have to win a race against it.</p>
    */
   @Test
   void rewoundReadsAreSkippedWhenWriterTakesLockDuringWait() throws Exception {
      ThreadLocal<Boolean> inScript = ThreadLocal.withInitial(() -> false);
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock(60_000, inScript::get);
      Restorer restorer = new Restorer(lock, () -> {
         // an NB_READ is restored by a non-blocking tryLock(), so the bounded write below has
         // a physically held read to rewind
         inScript.set(true);
         lock.lockRead();
         inScript.set(false);
         lock.lockWrite();
         lock.unlockAll();
      }, () -> {
         assertEquals(0, rawLock(lock).getReadHoldCount(), "physical reads after the failed restore");
         assertEquals(2, lock.getSkippedCount(), "the rewound read and the write were not restored");
         lock.unlockWrite();
         lock.unlockRead();
         assertThrows(EmptyStackException.class, lock::unlockRead);
      });

      restorer.awaitSaved();
      CountDownLatch releaseReader = new CountDownLatch(1);
      Peer reader = new Peer(lock::lockRead, lock::unlockRead, releaseReader);
      reader.awaitHolds();
      Peer writer = new Peer(lock::lockWrite, lock::unlockWrite, restorer.done);
      awaitState(writer.thread, Thread.State.WAITING);
      restorer.restore();
      restorer.awaitInBoundedWait();
      // the queued writer gets the lock while the restore is inside its bounded wait
      releaseReader.countDown();
      writer.awaitHolds();

      restorer.assertFailedAfterInterrupt();
      reader.join();
      writer.join();
      assertLockFree(lock);
   }

   /**
    * Bug #76986: entries a failed restore records as skipped are counted, so a caller that
    * catches the exception and continues knows it ran without the lock
    * (ViewsheetSandbox.isLockSkippedSince()).
    */
   @Test
   void failedRestoreCountsSkippedEntries() throws InterruptedException {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock(200);
      lock.lockWrite();
      lock.lockWrite();
      lock.unlockAll();

      runWhileAnotherThreadHoldsRead(lock, () ->
         assertThrows(IllegalStateException.class, lock::restoreLocks));

      assertEquals(2, lock.getSkippedCount());
      lock.unlockWrite();
      lock.unlockWrite();
      assertLockFree(lock);
   }

   /**
    * An uncontended upgrade restore takes the write first and records the read below it
    * without a physical read, the state lockWrite() leaves after rewinding it; unlockWrite()
    * takes the read back as a downgrade.
    */
   @Test
   void upgradeRestoreEndsInUpgradedState() {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock(BOUND_MS);
      ReentrantReadWriteLock raw = rawLock(lock);
      lock.lockRead();
      lock.lockWrite();
      lock.unlockAll();
      assertEquals(0, raw.getReadHoldCount());
      assertFalse(raw.isWriteLockedByCurrentThread());

      lock.restoreLocks();
      assertTrue(raw.isWriteLockedByCurrentThread());
      assertEquals(0, raw.getReadHoldCount());
      assertEquals(0, lock.getSkippedCount());

      lock.unlockWrite();
      assertFalse(raw.isWriteLocked());
      assertEquals(1, raw.getReadHoldCount());

      lock.unlockRead();
      assertEquals(0, raw.getReadHoldCount());
      assertLockFree(lock);
   }

   private static ReentrantReadWriteLock rawLock(UpgradableReadWriteLock lock) {
      return getField(lock, "thisLock");
   }

   @SuppressWarnings("unchecked")
   private static <T> T getField(UpgradableReadWriteLock lock, String name) {
      try {
         Field field = UpgradableReadWriteLock.class.getDeclaredField(name);
         field.setAccessible(true);
         return (T) field.get(lock);
      }
      catch(ReflectiveOperationException ex) {
         throw new AssertionError(ex);
      }
   }

   private static void awaitState(Thread thread, Thread.State state) throws InterruptedException {
      long deadline = System.currentTimeMillis() + 5000;

      while(thread.getState() != state && System.currentTimeMillis() < deadline) {
         Thread.sleep(5);
      }

      assertEquals(state, thread.getState(), thread.getName() + " state");
   }

   private static void awaitQuietly(CountDownLatch latch, long timeoutMs) {
      try {
         latch.await(timeoutMs, TimeUnit.MILLISECONDS);
      }
      catch(InterruptedException ex) {
         Thread.currentThread().interrupt();
      }
   }

   /**
    * Another thread that takes a lock and holds it until {@code release} opens, capped so a
    * test that fails with a parked restore still ends.
    */
   private static final class Peer {
      Peer(Runnable lock, Runnable unlock, CountDownLatch release) {
         thread = new Thread(() -> {
            lock.run();
            holds.countDown();

            try {
               awaitQuietly(release, PEER_CAP_MS);
            }
            finally {
               unlock.run();
            }
         }, "peer");
         thread.setDaemon(true);
         thread.start();
      }

      void awaitHolds() throws InterruptedException {
         assertTrue(holds.await(5, TimeUnit.SECONDS), "peer never acquired the lock");
      }

      void join() throws InterruptedException {
         thread.join(PEER_CAP_MS + 5000);
         assertFalse(thread.isAlive(), "peer thread did not exit");
      }

      final CountDownLatch holds = new CountDownLatch(1);
      final Thread thread;
   }

   /**
    * The thread under test: saves its locks with {@code save}, restores them on
    * {@link #restore()}, then unwinds its frames with {@code unwind}, all on one thread since
    * the lock state is per thread.
    */
   private static final class Restorer {
      Restorer(UpgradableReadWriteLock lock, Runnable save, Runnable unwind) {
         bound = getField(lock, "restoreWriteLockTimeoutMs");
         thread = new Thread(() -> {
            try {
               save.run();
               saved.countDown();
               awaitQuietly(go, PEER_CAP_MS);
               long start = System.nanoTime();

               try {
                  lock.restoreLocks();
               }
               catch(RuntimeException ex) {
                  restoreError = ex;
               }

               restoreMs = (System.nanoTime() - start) / 1_000_000;
               unwind.run();
            }
            catch(Throwable ex) {
               error = ex;
            }
            finally {
               done.countDown();
            }
         }, "restorer");
         thread.setDaemon(true);
         thread.start();
      }

      void awaitSaved() throws InterruptedException {
         assertTrue(saved.await(5, TimeUnit.SECONDS), "restorer never saved its locks");
      }

      void restore() {
         go.countDown();
      }

      /**
       * Wait until the restore is parked in the bounded write wait (not in the wait for
       * {@link #restore()}, which is timed too).
       */
      void awaitInBoundedWait() throws InterruptedException {
         long deadline = System.currentTimeMillis() + 5000;

         while(!isInBoundedWait() && System.currentTimeMillis() < deadline) {
            Thread.sleep(2);
         }

         assertTrue(isInBoundedWait(), "restore never reached the bounded write wait");
      }

      // matches UpgradableReadWriteLock.lockWriteBounded() by name, update it on a rename
      private boolean isInBoundedWait() {
         return thread.getState() == Thread.State.TIMED_WAITING &&
            Arrays.stream(thread.getStackTrace())
               .anyMatch(frame -> frame.getMethodName().equals("lockWriteBounded"));
      }

      /**
       * The restore must throw IllegalStateException about when its bound expires (not park
       * after it), and the frames must unwind cleanly.
       */
      void assertFailedWithinBound() throws InterruptedException {
         assertFinished(bound + HANG_GUARD_MS, "a " + bound + "ms bound");
         assertInstanceOf(IllegalStateException.class, restoreError);
         assertTrue(restoreMs < bound + 1000, "restoreLocks() took " + restoreMs + "ms");
      }

      /**
       * Interrupt the bounded wait: the restore must throw IllegalStateException right away
       * (not park after it), and the frames must unwind cleanly.
       */
      void assertFailedAfterInterrupt() throws InterruptedException {
         assertTrue(isInBoundedWait(), "restore left the bounded wait before the interrupt");
         thread.interrupt();
         assertFinished(HANG_GUARD_MS, "the interrupt");
         assertInstanceOf(IllegalStateException.class, restoreError);
         assertInstanceOf(InterruptedException.class, restoreError.getCause());
      }

      private void assertFinished(long timeoutMs, String after) throws InterruptedException {
         thread.join(timeoutMs);

         if(thread.isAlive()) {
            fail("restoreLocks() still parked " + timeoutMs + "ms after " + after + ", at " +
                 Arrays.toString(thread.getStackTrace()));
         }

         if(error != null) {
            throw new AssertionError("restorer failed", error);
         }
      }

      final long bound;
      final Thread thread;
      final CountDownLatch saved = new CountDownLatch(1);
      final CountDownLatch go = new CountDownLatch(1);
      final CountDownLatch done = new CountDownLatch(1);
      volatile RuntimeException restoreError;
      volatile Throwable error;
      volatile long restoreMs;
   }

   private static final long BOUND_MS = 300;
   // how long a restore may still be parked after its bound (or interrupt) before it counts
   // as hung
   private static final long HANG_GUARD_MS = 2700;
   private static final long PEER_CAP_MS = 6000;

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
