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
package inetsoft.util;

import org.junit.jupiter.api.*;

import java.util.EmptyStackException;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77153: a failed bounded {@link UpgradableReadWriteLock#restoreLocks()} records the
 * entries it could not take back as lost. The thread fails fast on the lock while lost entries
 * remain (live or saved by a nested unlockAll()), the frames unwind without touching the real
 * lock, and the owner can detect the loss after its unlock through getLostCount().
 */
@Tag("core")
class UpgradableReadWriteLockLostTest {
   static final long BOUND = 200;

   /** The restore failure is a LockRestoreException and the lock is reported lost. */
   @Test
   void failedRestoreMarksLockLostAndFailsFast() throws Exception {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock(BOUND);
      long lost0 = lock.getLostCount();
      lock.lockWrite(); // outer owner, e.g. CoreLifecycleService.refreshViewsheet()
      lock.lockRead();  // e.g. getData()
      lock.unlockAll(); // doExecuteData()

      holdingRead(lock, () ->
         assertThrows(LockRestoreException.class, lock::restoreLocks));

      assertTrue(lock.isLockLost());
      assertEquals(2, lock.getLostCount() - lost0);
      assertEquals(2, lock.getSkippedCount(), "lost entries are still counted as skipped");

      // nested frames can no longer take the lock piecemeal; nothing is pushed
      assertThrows(LockRestoreException.class, lock::lockRead);
      assertThrows(LockRestoreException.class, lock::lockWrite);

      // each frame unwinds its own entry, no EmptyStackException / IllegalMonitorState
      assertDoesNotThrow(lock::unlockRead);
      assertTrue(lock.isLockLost());
      assertDoesNotThrow(lock::unlockWrite);

      // the fail-fast pushed nothing: the stack is balanced and empty again
      assertFalse(lock.isLockLost());
      assertThrows(EmptyStackException.class, lock::unlockRead);
      assertEquals(2, lock.getLostCount() - lost0, "the count only grows");

      lock.lockWrite();
      lock.unlockWrite();
      assertLockFree(lock);
   }

   /**
    * A nested unlockAll()/restoreLocks() pair carries the lost state through: the live stack is
    * empty in between but the thread is still lost, and the restore does not heal the lost
    * write into a real one with tryLock().
    */
   @Test
   void lostStateIsCarriedThroughNestedUnlockAll() throws Exception {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock(BOUND);
      lock.lockWrite();
      lock.unlockAll();

      holdingRead(lock, () ->
         assertThrows(LockRestoreException.class, lock::restoreLocks));

      // uncontended now: the old code turned the skipped write into a real NB_WRITE here
      lock.unlockAll();
      assertTrue(lock.isLockLost(), "lost entry saved by the nested unlockAll()");
      assertThrows(LockRestoreException.class, lock::lockRead);
      lock.restoreLocks();
      assertTrue(lock.isLockLost());
      assertFalse(rawLock(lock).isWriteLockedByCurrentThread(), "lost write was healed");
      assertTrue(otherThreadGetsWrite(lock), "the lost write is not held");

      lock.unlockWrite();
      assertFalse(lock.isLockLost());
      assertLockFree(lock);
   }

   /** Lost state is per lock: an embedded sandbox's lost lock does not poison the parent's. */
   @Test
   void lostStateIsPerLock() throws Exception {
      UpgradableReadWriteLock parent = new UpgradableReadWriteLock(BOUND);
      UpgradableReadWriteLock child = new UpgradableReadWriteLock(BOUND);
      parent.lockWrite();
      parent.unlockAll(); // ViewsheetSandbox.getData() embedded branch
      child.lockRead();
      child.lockWrite();
      child.unlockAll();

      holdingRead(child, () ->
         assertThrows(LockRestoreException.class, child::restoreLocks));

      assertTrue(child.isLockLost());
      assertFalse(parent.isLockLost());
      child.unlockWrite();
      child.unlockRead();
      assertFalse(child.isLockLost());

      parent.restoreLocks();
      assertTrue(rawLock(parent).isWriteLockedByCurrentThread());
      parent.lockRead();
      parent.unlockRead();
      parent.unlockWrite();
      assertEquals(0, parent.getLostCount());
      assertLockFree(parent);
      assertLockFree(child);
   }

   /** A pooled thread that lost the lock on one request is healthy on the next one. */
   @Test
   void pooledThreadIsHealthyOnNextUse() throws Exception {
      UpgradableReadWriteLock lock = new UpgradableReadWriteLock(BOUND);
      ExecutorService pool = Executors.newSingleThreadExecutor();

      try {
         long lost1 = pool.submit(() -> {
            long lost0 = lock.getLostCount();
            lock.lockWrite();

            try {
               lock.lockRead();

               try {
                  lock.unlockAll();
                  holdingRead(lock, () ->
                     assertThrows(LockRestoreException.class, lock::restoreLocks));
               }
               finally {
                  lock.unlockRead();
               }
            }
            finally {
               lock.unlockWrite();
            }

            return lock.getLostCount() - lost0;
         }).get(10, TimeUnit.SECONDS);
         assertEquals(2, lost1, "first request lost its locks");

         long lost2 = pool.submit(() -> {
            assertFalse(lock.isLockLost());
            long lost0 = lock.getLostCount();
            lock.lockWrite();

            try {
               lock.lockRead();
               lock.unlockAll();
               lock.restoreLocks();
               lock.unlockRead();
            }
            finally {
               lock.unlockWrite();
            }

            return lock.getLostCount() - lost0;
         }).get(10, TimeUnit.SECONDS);
         assertEquals(0, lost2, "second request on the same thread ran locked");
      }
      finally {
         pool.shutdownNow();
      }

      assertLockFree(lock);
   }

   // ---------------- helpers ----------------

   interface Action {
      void run() throws Exception;
   }

   /** Run the action while another thread holds a read lock, so a restored write times out. */
   static void holdingRead(UpgradableReadWriteLock lock, Action action)
      throws InterruptedException
   {
      CountDownLatch holds = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      Thread reader = new Thread(() -> {
         lock.lockRead();
         holds.countDown();

         try {
            release.await(10, TimeUnit.SECONDS);
         }
         catch(InterruptedException ignore) {
         }
         finally {
            lock.unlockRead();
         }
      });
      reader.setDaemon(true);
      reader.start();

      try {
         assertTrue(holds.await(5, TimeUnit.SECONDS));
         action.run();
      }
      catch(Exception ex) {
         throw new AssertionError(ex);
      }
      finally {
         release.countDown();
         reader.join(5000);
      }

      assertFalse(reader.isAlive());
   }

   static boolean otherThreadGetsWrite(UpgradableReadWriteLock lock) throws Exception {
      ReentrantReadWriteLock raw = rawLock(lock);
      FutureTask<Boolean> task = new FutureTask<>(() -> {
         if(raw.writeLock().tryLock(2, TimeUnit.SECONDS)) {
            raw.writeLock().unlock();
            return true;
         }

         return false;
      });
      Thread t = new Thread(task);
      t.setDaemon(true);
      t.start();
      return task.get(5, TimeUnit.SECONDS);
   }

   static void assertLockFree(UpgradableReadWriteLock lock) throws Exception {
      assertTrue(otherThreadGetsWrite(lock), "lock is still held by the test thread");
   }

   static ReentrantReadWriteLock rawLock(UpgradableReadWriteLock lock) {
      try {
         java.lang.reflect.Field f = UpgradableReadWriteLock.class.getDeclaredField("thisLock");
         f.setAccessible(true);
         return (ReentrantReadWriteLock) f.get(lock);
      }
      catch(ReflectiveOperationException ex) {
         throw new AssertionError(ex);
      }
   }
}
