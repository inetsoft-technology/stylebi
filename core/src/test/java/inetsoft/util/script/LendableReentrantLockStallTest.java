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
package inetsoft.util.script;

import inetsoft.util.stall.LockStallException;
import inetsoft.util.stall.StallPolicy;
import inetsoft.util.stall.WaitRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The engine lock's acquisition is bounded by the lock-stall watchdog (bug #76967), without
 * changing who is let in (#5531). Every blocking step is bounded and helper threads are
 * daemons.
 */
@Tag("core")
public class LendableReentrantLockStallTest {
   @BeforeEach
   public void setUp() {
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir));
   }

   @AfterEach
   public void tearDown() {
      release.countDown();
      StallPolicy.setOverride(null);
   }

   @Test
   public void waiterFailsWhenTheOwnerIsStuck() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      Thread owner = holdUntilReleased(lock);
      FutureTask<Integer> waiter = new FutureTask<>(() -> {
         try {
            lock.lock();
            lock.unlock();
            return -1;
         }
         catch(LockStallException ex) {
            assertEquals("LendableReentrantLock.lock", ex.getSite());
            return lock.getHoldCount();
         }
      });
      startDaemon(waiter);

      assertEquals(0, waiter.get(15, TimeUnit.SECONDS), "the waiter must fail holding nothing");
      assertTrue(lock.isLocked(), "the owner keeps the lock");
      assertFalse(lock.isLent());
      release.countDown();
      owner.join(5000);
      assertFalse(lock.isLocked());
   }

   @Test
   public void busyOwnerIsProgress() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      CountDownLatch locked = new CountDownLatch(1);
      startDaemon(() -> {
         lock.lock();

         try {
            locked.countDown();
            long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(2500);

            while(System.nanoTime() < end) {
               Thread.onSpinWait();
            }
         }
         finally {
            lock.unlock();
         }
      });
      assertTrue(locked.await(5, TimeUnit.SECONDS));
      FutureTask<Boolean> waiter = new FutureTask<>(() -> {
         lock.lock();
         lock.unlock();
         return true;
      });
      startDaemon(waiter);

      assertTrue(waiter.get(15, TimeUnit.SECONDS), "a running owner is not a stall");
   }

   @Test
   public void offModeWaitsOn() throws Exception {
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.OFF, 1000, 200, dumpDir));
      LendableReentrantLock lock = new LendableReentrantLock();
      Thread owner = holdUntilReleased(lock);
      FutureTask<Boolean> waiter = new FutureTask<>(() -> {
         lock.lock();
         lock.unlock();
         return true;
      });
      startDaemon(waiter);

      Thread.sleep(2500);
      assertFalse(waiter.isDone(), "off mode must not fail the wait");
      release.countDown();
      assertTrue(waiter.get(15, TimeUnit.SECONDS));
      owner.join(5000);
   }

   @Test
   public void fastPathRegistersNothing() {
      LendableReentrantLock lock = new LendableReentrantLock();
      long before = WaitRegistry.global().getBeginCount();

      lock.lock();
      lock.lock();
      lock.unlock();
      lock.unlock();
      assertTrue(lock.tryLock());
      lock.unlock();

      assertEquals(before, WaitRegistry.global().getBeginCount());
   }

   /**
    * #5531: a thread queued before the loan must not get in ahead of the borrower, and the
    * borrower gets in although it queued later, with the watchdog on.
    */
   @Test
   public void loanStillBypassesThreadsQueuedEarlier() throws Exception {
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 60000, 200, dumpDir));
      LendableReentrantLock lock = new LendableReentrantLock();
      LendableReentrantLock.Borrower borrower = new LendableReentrantLock.Borrower();
      CountDownLatch borrowerAcquired = new CountDownLatch(1);
      lock.lock();
      lock.lock();

      Thread other = startDaemon(() -> {
         lock.lock();
         lock.unlock();
      });
      awaitBlocked(other);

      Thread worker = startDaemon(() -> {
         borrower.begin();

         try {
            assertSame(Thread.currentThread(), borrower.getThread());
            lock.lock();

            try {
               borrowerAcquired.countDown();
            }
            finally {
               lock.unlock();
            }
         }
         finally {
            borrower.end();
         }
      });
      awaitBlocked(worker);

      try(LendableReentrantLock.Loan ignored = lock.lend(borrower)) {
         assertTrue(borrowerAcquired.await(5, TimeUnit.SECONDS), "borrower was not let in");
         worker.join(5000);
         assertTrue(other.isAlive(), "a thread other than the borrower got the lent lock");
      }

      assertNull(borrower.getThread(), "an ended task has no thread");
      assertEquals(2, lock.getHoldCount(), "hold count was not restored");
      lock.unlock();
      lock.unlock();
      other.join(5000);
      assertFalse(other.isAlive(), "queued thread did not get the lock after the loan");
   }

   /**
    * A thread shut out while the lock is lent and free waits for the borrower too: a borrower
    * working outside the lock is progress, although the lender is parked (it waits for the
    * borrower in an unregistered wait).
    */
   @Test
   public void busyBorrowerIsProgressForThreadsShutOutByTheLoan() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      LendableReentrantLock.Borrower borrower = new LendableReentrantLock.Borrower();
      CountDownLatch begun = new CountDownLatch(1);
      CountDownLatch lent = new CountDownLatch(1);
      Thread worker = startDaemon(() -> {
         borrower.begin();

         try {
            begun.countDown();
            lent.await(5, TimeUnit.SECONDS);
            long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(3000);

            while(System.nanoTime() < end) {
               Thread.onSpinWait();
            }
         }
         catch(InterruptedException ignore) {
         }
         finally {
            borrower.end();
         }
      });
      assertTrue(begun.await(5, TimeUnit.SECONDS));
      FutureTask<Boolean> shutOut = new FutureTask<>(() -> {
         lock.lock();
         lock.unlock();
         return true;
      });
      lock.lock();

      try {
         try(LendableReentrantLock.Loan ignored = lock.lend(borrower)) {
            Thread thread = startDaemon(shutOut);
            awaitBlocked(thread);
            lent.countDown();
            // the lender parks while its borrower works, as a lens reader would
            worker.join(15000);
            assertFalse(shutOut.isDone(), "a busy borrower is not a stall");
         }
      }
      finally {
         lock.unlock();
      }

      assertTrue(shutOut.get(15, TimeUnit.SECONDS));
   }

   /**
    * A borrower that is parked makes no progress: a thread shut out by the loan fails holding
    * nothing, and the loan is left as it was.
    */
   @Test
   public void parkedBorrowerStallsThreadsShutOutByTheLoan() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      LendableReentrantLock.Borrower borrower = new LendableReentrantLock.Borrower();
      CountDownLatch begun = new CountDownLatch(1);
      Thread worker = startDaemon(() -> {
         borrower.begin();

         try {
            begun.countDown();
            release.await(30, TimeUnit.SECONDS);
         }
         catch(InterruptedException ignore) {
         }
         finally {
            borrower.end();
         }
      });
      assertTrue(begun.await(5, TimeUnit.SECONDS));
      FutureTask<Integer> shutOut = new FutureTask<>(() -> {
         try {
            lock.lock();
            lock.unlock();
            return -1;
         }
         catch(LockStallException ex) {
            assertEquals("LendableReentrantLock.lock", ex.getSite());
            return lock.getHoldCount();
         }
      });
      lock.lock();

      try {
         try(LendableReentrantLock.Loan ignored = lock.lend(borrower)) {
            startDaemon(shutOut);
            assertEquals(0, shutOut.get(15, TimeUnit.SECONDS), "must fail holding nothing");
            assertTrue(lock.isLent(), "the loan is left intact");
            assertFalse(lock.isLocked());
            release.countDown();
            worker.join(5000);
         }

         assertEquals(1, lock.getHoldCount(), "the lender got its hold back");
      }
      finally {
         lock.unlock();
      }

      assertFalse(lock.isLocked());
   }

   private Thread holdUntilReleased(LendableReentrantLock lock) throws InterruptedException {
      CountDownLatch locked = new CountDownLatch(1);
      Thread owner = startDaemon(() -> {
         lock.lock();

         try {
            locked.countDown();
            release.await(30, TimeUnit.SECONDS);
         }
         catch(InterruptedException ignore) {
         }
         finally {
            lock.unlock();
         }
      });
      assertTrue(locked.await(5, TimeUnit.SECONDS));
      return owner;
   }

   private static Thread startDaemon(Runnable runnable) {
      Thread thread = new Thread(runnable);
      thread.setDaemon(true);
      thread.start();
      return thread;
   }

   private static void awaitBlocked(Thread thread) throws InterruptedException {
      long deadline = System.currentTimeMillis() + 5000;

      while(thread.getState() != Thread.State.WAITING &&
         thread.getState() != Thread.State.TIMED_WAITING)
      {
         assertTrue(System.currentTimeMillis() < deadline, "thread never blocked on the lock");
         Thread.sleep(5);
      }
   }

   @TempDir
   File dumpDir;
   private final CountDownLatch release = new CountDownLatch(1);
}
