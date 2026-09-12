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
