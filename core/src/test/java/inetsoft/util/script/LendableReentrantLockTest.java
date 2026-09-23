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

import inetsoft.util.script.graal.ScriptScope;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LendableReentrantLock}, the script engine execution lock that a
 * condition filter holding it lends to the async lens worker it waits for (bug #76938).
 * Every blocking step is bounded, and all helper threads are daemons, so a regression
 * cannot hang the build.
 */
@Tag("core")
public class LendableReentrantLockTest {
   @Test
   public void reentrantAndExclusive() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      lock.lock();
      lock.lock();
      assertEquals(2, lock.getHoldCount());
      assertFalse(runOnThread(() -> lock.tryLock()).get(5, TimeUnit.SECONDS));
      lock.unlock();
      lock.unlock();
      assertFalse(lock.isLocked());
      assertTrue(runOnThread(() -> {
         boolean locked = lock.tryLock();
         lock.unlock();
         return locked;
      }).get(5, TimeUnit.SECONDS));
      assertThrows(IllegalMonitorStateException.class, lock::unlock);
   }

   /**
    * The contended case: a thread queued on the lock before it is lent (e.g. a script
    * thread that will then take a condition filter's monitor, bug #76918) must not get
    * in ahead of the borrower, and the borrower must get in although it queued later.
    */
   @Test
   public void loanBypassesThreadsQueuedEarlier() throws Exception {
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
         assertTrue(lock.isLent());
         assertTrue(borrowerAcquired.await(5, TimeUnit.SECONDS), "borrower was not let in");
         worker.join(5000);
         assertTrue(other.isAlive(), "a thread other than the borrower got the lent lock");
      }

      assertEquals(2, lock.getHoldCount(), "hold count was not restored");
      assertFalse(lock.isLent());
      assertTrue(other.isAlive());
      lock.unlock();
      lock.unlock();
      other.join(5000);
      assertFalse(other.isAlive(), "queued thread did not get the lock after the loan");
   }

   /**
    * A loan is bound to the task, so it can be made before the task has a thread.
    */
   @Test
   public void loanMadeBeforeWorkerStarts() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      LendableReentrantLock.Borrower borrower = new LendableReentrantLock.Borrower();
      lock.lock();

      try(LendableReentrantLock.Loan ignored = lock.lend(borrower)) {
         Future<Boolean> result = runOnThread(() -> {
            borrower.begin();

            try {
               return lock.tryLock(5, TimeUnit.SECONDS);
            }
            finally {
               lock.unlock();
               borrower.end();
            }
         });

         assertTrue(result.get(5, TimeUnit.SECONDS));
      }

      assertTrue(lock.isHeldByCurrentThread());
      lock.unlock();
   }

   /**
    * A pooled thread that ran the borrower task must not use the loan for its next task.
    */
   @Test
   public void endedTaskCannotUseLoan() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      LendableReentrantLock.Borrower borrower = new LendableReentrantLock.Borrower();
      ExecutorService pool = Executors.newSingleThreadExecutor(LendableReentrantLockTest::daemon);
      lock.lock();

      try(LendableReentrantLock.Loan ignored = lock.lend(borrower)) {
         Future<Boolean> task = pool.submit(() -> {
            borrower.begin();

            try {
               boolean locked = lock.tryLock(5, TimeUnit.SECONDS);
               lock.unlock();
               return locked;
            }
            finally {
               borrower.end();
            }
         });
         assertTrue(task.get(5, TimeUnit.SECONDS));

         // same pooled thread, unrelated task
         Future<Boolean> next = pool.submit(() -> lock.tryLock(200, TimeUnit.MILLISECONDS));
         assertFalse(next.get(5, TimeUnit.SECONDS), "an ended task's thread got the lent lock");
      }
      finally {
         pool.shutdownNow();
      }

      assertEquals(1, lock.getHoldCount());
      lock.unlock();
   }

   /**
    * A borrower may lend the lock on to its own worker. The loans unwind in LIFO order:
    * the first lender cannot reclaim until the nested loan is closed and released, and
    * each lender gets its own hold count back.
    */
   @Test
   public void nestedLoansUnwindLifo() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      LendableReentrantLock.Borrower first = new LendableReentrantLock.Borrower();
      LendableReentrantLock.Borrower second = new LendableReentrantLock.Borrower();
      CountDownLatch outerLent = new CountDownLatch(1);
      CountDownLatch nestedLent = new CountDownLatch(1);
      CountDownLatch reclaimOuter = new CountDownLatch(1);
      CountDownLatch releaseNested = new CountDownLatch(1);
      AtomicBoolean outerReclaimed = new AtomicBoolean();

      Future<Integer> lender = runOnThread(() -> {
         lock.lock();

         try {
            try(LendableReentrantLock.Loan ignored = lock.lend(first)) {
               outerLent.countDown();
               assertTrue(reclaimOuter.await(5, TimeUnit.SECONDS));
            }

            outerReclaimed.set(true);
            return lock.getHoldCount();
         }
         finally {
            lock.unlock();
         }
      });
      assertTrue(outerLent.await(5, TimeUnit.SECONDS));

      Future<Integer> firstWorker = runOnThread(() -> {
         first.begin();

         try {
            lock.lock();
            lock.lock();

            try {
               Future<Boolean> secondWorker;

               try(LendableReentrantLock.Loan ignored = lock.lend(second)) {
                  secondWorker = runOnThread(() -> {
                     second.begin();

                     try {
                        boolean locked = lock.tryLock(5, TimeUnit.SECONDS);
                        nestedLent.countDown();
                        assertTrue(releaseNested.await(5, TimeUnit.SECONDS));
                        lock.unlock();
                        return locked;
                     }
                     finally {
                        second.end();
                     }
                  });
                  assertTrue(secondWorker.get(5, TimeUnit.SECONDS));
               }

               return lock.getHoldCount();
            }
            finally {
               lock.unlock();
               lock.unlock();
            }
         }
         finally {
            first.end();
         }
      });

      assertTrue(nestedLent.await(5, TimeUnit.SECONDS));
      reclaimOuter.countDown();
      Thread.sleep(200);
      assertFalse(outerReclaimed.get(), "outer loan reclaimed while a nested loan was open");
      releaseNested.countDown();

      assertEquals(2, firstWorker.get(5, TimeUnit.SECONDS), "nested hold count not restored");
      assertEquals(1, lender.get(5, TimeUnit.SECONDS), "outer hold count not restored");
      assertFalse(lock.isLocked());
      assertFalse(lock.isLent());
   }

   /**
    * Closing a loan waits for the borrower to release the lock, and only the lender may
    * close it.
    */
   @Test
   public void reclaimWaitsForBorrowerToRelease() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      LendableReentrantLock.Borrower borrower = new LendableReentrantLock.Borrower();
      CountDownLatch lent = new CountDownLatch(1);
      CountDownLatch acquired = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      AtomicBoolean reclaimed = new AtomicBoolean();
      AtomicReference<LendableReentrantLock.Loan> loanRef = new AtomicReference<>();

      Future<Integer> lender = runOnThread(() -> {
         lock.lock();

         try {
            try(LendableReentrantLock.Loan loan = lock.lend(borrower)) {
               loanRef.set(loan);
               lent.countDown();
               assertTrue(acquired.await(5, TimeUnit.SECONDS));
            }

            reclaimed.set(true);
            return lock.getHoldCount();
         }
         finally {
            lock.unlock();
         }
      });
      assertTrue(lent.await(5, TimeUnit.SECONDS));
      assertThrows(IllegalMonitorStateException.class, () -> loanRef.get().close());

      Future<Boolean> worker = runOnThread(() -> {
         borrower.begin();

         try {
            lock.lock();
            acquired.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            lock.unlock();
            return true;
         }
         finally {
            borrower.end();
         }
      });

      assertTrue(acquired.await(5, TimeUnit.SECONDS));
      Thread.sleep(200);
      assertFalse(reclaimed.get(), "loan reclaimed while the borrower held the lock");
      release.countDown();
      assertTrue(worker.get(5, TimeUnit.SECONDS));
      assertEquals(1, lender.get(5, TimeUnit.SECONDS));
      assertTrue(reclaimed.get());
   }

   /**
    * A thread inside script evaluation must never lend: the GraalJS context is in use on
    * that thread.
    */
   @Test
   public void noLendInsideScriptExecution() {
      LendableReentrantLock lock = new LendableReentrantLock();
      LendableReentrantLock.Borrower borrower = new LendableReentrantLock.Borrower();
      lock.lock();
      JavaScriptEngine.pushHeldScriptLock(lock);

      try {
         assertTrue(JavaScriptEngine.holdsScriptLock());
         assertTrue(JavaScriptEngine.canLendScriptLocks(borrower));
         JavaScriptEngine.pushExecScriptable(NOOP_SCOPE);

         try {
            assertFalse(JavaScriptEngine.canLendScriptLocks(borrower));

            try(LendableReentrantLock.Loan loan = JavaScriptEngine.lendScriptLocks(borrower)) {
               assertSame(LendableReentrantLock.NO_LOAN, loan);
               assertFalse(lock.isLent());
            }
         }
         finally {
            JavaScriptEngine.popExecScriptable();
         }

         try(LendableReentrantLock.Loan ignored = JavaScriptEngine.lendScriptLocks(borrower)) {
            assertTrue(lock.isLent());
            assertFalse(lock.isHeldByCurrentThread());
         }

         assertTrue(lock.isHeldByCurrentThread());
         borrower.end();
         assertFalse(JavaScriptEngine.canLendScriptLocks(borrower), "lent to an ended task");
      }
      finally {
         JavaScriptEngine.popHeldScriptLock();
         lock.unlock();
      }

      assertFalse(JavaScriptEngine.holdsScriptLock());
   }

   /**
    * A lens worker that was lent the lock lends it on to the worker it waits for itself
    * (stacked async lenses), without having recorded the lock as held.
    */
   @Test
   public void borrowerLendsOnToItsOwnWorker() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      LendableReentrantLock.Borrower first = new LendableReentrantLock.Borrower();
      LendableReentrantLock.Borrower second = new LendableReentrantLock.Borrower();
      lock.lock();
      JavaScriptEngine.pushHeldScriptLock(lock);

      try(LendableReentrantLock.Loan ignored = JavaScriptEngine.lendScriptLocks(first)) {
         assertTrue(lock.isLent());

         Future<Boolean> firstWorker = runOnThread(() -> {
            first.begin();

            try {
               assertTrue(first.getLentLocks().contains(lock));
               assertFalse(JavaScriptEngine.holdsScriptLock());
               assertTrue(JavaScriptEngine.canLendScriptLocks(second));

               try(LendableReentrantLock.Loan nested = JavaScriptEngine.lendScriptLocks(second)) {
                  assertNotSame(LendableReentrantLock.NO_LOAN, nested);

                  return runOnThread(() -> {
                     second.begin();

                     try {
                        boolean locked = lock.tryLock(5, TimeUnit.SECONDS);
                        lock.unlock();
                        return locked;
                     }
                     finally {
                        second.end();
                     }
                  }).get(5, TimeUnit.SECONDS);
               }
               finally {
                  assertFalse(lock.isHeldByCurrentThread(), "nested lend left the lock held");
               }
            }
            finally {
               first.end();
            }
         });

         assertTrue(firstWorker.get(5, TimeUnit.SECONDS), "nested worker never got the lock");
      }
      finally {
         JavaScriptEngine.popHeldScriptLock();
      }

      assertEquals(1, lock.getHoldCount());
      assertTrue(first.getLentLocks().isEmpty());
      assertNull(LendableReentrantLock.Borrower.current());
      lock.unlock();
   }

   /**
    * A lender that takes the lock again before closing its loan fails instead of
    * waiting for itself forever.
    */
   @Test
   public void lenderRelockingFailsFast() {
      LendableReentrantLock lock = new LendableReentrantLock();
      lock.lock();

      try(LendableReentrantLock.Loan ignored = lock.lend(new LendableReentrantLock.Borrower())) {
         assertThrows(IllegalMonitorStateException.class, lock::lock);
         assertThrows(IllegalMonitorStateException.class, lock::lockInterruptibly);
         assertThrows(IllegalMonitorStateException.class, () -> lock.tryLock(1, TimeUnit.SECONDS));
         assertFalse(lock.tryLock());
      }

      assertEquals(1, lock.getHoldCount());
      lock.unlock();
   }

   /**
    * Closing a loan waits for a re-entrant borrower to release all its holds.
    */
   @Test
   public void reclaimWaitsForAllBorrowerHolds() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      LendableReentrantLock.Borrower borrower = new LendableReentrantLock.Borrower();
      CountDownLatch lent = new CountDownLatch(1);
      CountDownLatch locked = new CountDownLatch(1);
      CountDownLatch releaseOne = new CountDownLatch(1);
      CountDownLatch releaseTwo = new CountDownLatch(1);
      AtomicBoolean reclaimed = new AtomicBoolean();

      Future<Integer> lender = runOnThread(() -> {
         lock.lock();
         lock.lock();

         try {
            try(LendableReentrantLock.Loan ignored = lock.lend(borrower)) {
               lent.countDown();
               assertTrue(locked.await(5, TimeUnit.SECONDS));
            }

            reclaimed.set(true);
            return lock.getHoldCount();
         }
         finally {
            lock.unlock();
            lock.unlock();
         }
      });
      assertTrue(lent.await(5, TimeUnit.SECONDS));

      Future<?> worker = runOnThread(() -> {
         borrower.begin();

         try {
            lock.lock();
            lock.lock();
            locked.countDown();
            assertTrue(releaseOne.await(5, TimeUnit.SECONDS));
            lock.unlock();
            assertTrue(releaseTwo.await(5, TimeUnit.SECONDS));
            lock.unlock();
            return null;
         }
         finally {
            borrower.end();
         }
      });

      assertTrue(locked.await(5, TimeUnit.SECONDS));
      releaseOne.countDown();
      Thread.sleep(200);
      assertFalse(reclaimed.get(), "reclaimed while the borrower still held the lock");
      releaseTwo.countDown();
      worker.get(5, TimeUnit.SECONDS);
      assertEquals(2, lender.get(5, TimeUnit.SECONDS));
   }

   @Test
   public void lockInterruptiblyThrowsAndLeavesNoState() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      lock.lock();
      AtomicReference<Thread> waiter = new AtomicReference<>();

      Future<Boolean> result = runOnThread(() -> {
         waiter.set(Thread.currentThread());

         try {
            lock.lockInterruptibly();
            return false;
         }
         catch(InterruptedException ex) {
            return !lock.isHeldByCurrentThread();
         }
      });

      awaitBlocked(waitFor(waiter));
      waiter.get().interrupt();
      assertTrue(result.get(5, TimeUnit.SECONDS));
      lock.unlock();
      assertFalse(lock.isLocked());

      Thread.currentThread().interrupt();
      assertThrows(InterruptedException.class, lock::lockInterruptibly);
      assertFalse(lock.isLocked());
   }

   @Test
   public void timedTryLockExpiresOrIsInterrupted() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      lock.lock();

      try {
         assertFalse(runOnThread(() -> lock.tryLock(100, TimeUnit.MILLISECONDS))
                        .get(5, TimeUnit.SECONDS));
         assertTrue(runOnThread(() -> {
            Thread.currentThread().interrupt();

            try {
               lock.tryLock(5, TimeUnit.SECONDS);
               return false;
            }
            catch(InterruptedException ex) {
               return true;
            }
         }).get(5, TimeUnit.SECONDS));
      }
      finally {
         lock.unlock();
      }

      assertFalse(lock.isLocked());
   }

   @Test
   public void lockKeepsInterruptFlag() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      lock.lock();
      AtomicReference<Thread> waiter = new AtomicReference<>();

      Future<Boolean> result = runOnThread(() -> {
         waiter.set(Thread.currentThread());
         lock.lock();
         boolean interrupted = Thread.interrupted();
         lock.unlock();
         return interrupted;
      });

      awaitBlocked(waitFor(waiter));
      waiter.get().interrupt();
      Thread.sleep(50);
      lock.unlock();
      assertTrue(result.get(5, TimeUnit.SECONDS), "interrupt flag was lost");
   }

   @Test
   public void unlockByNonOwnerThrows() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      lock.lock();

      try {
         assertTrue(runOnThread(() -> {
            try {
               lock.unlock();
               return false;
            }
            catch(IllegalMonitorStateException ex) {
               return true;
            }
         }).get(5, TimeUnit.SECONDS));
         assertEquals(1, lock.getHoldCount());
      }
      finally {
         lock.unlock();
      }
   }

   private static Thread waitFor(AtomicReference<Thread> ref) throws InterruptedException {
      long deadline = System.currentTimeMillis() + 5000;

      while(ref.get() == null) {
         assertTrue(System.currentTimeMillis() < deadline, "thread never started");
         Thread.sleep(5);
      }

      return ref.get();
   }

   private static <T> Future<T> runOnThread(Callable<T> callable) {
      FutureTask<T> task = new FutureTask<>(callable);
      startDaemon(task);
      return task;
   }

   private static Thread startDaemon(Runnable runnable) {
      Thread thread = daemon(runnable);
      thread.start();
      return thread;
   }

   private static Thread daemon(Runnable runnable) {
      Thread thread = new Thread(runnable);
      thread.setDaemon(true);
      return thread;
   }

   private static void awaitBlocked(Thread thread) throws InterruptedException {
      long deadline = System.currentTimeMillis() + 5000;

      while(thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.TIMED_WAITING) {
         assertTrue(System.currentTimeMillis() < deadline, "thread never blocked on the lock");
         Thread.sleep(5);
      }
   }

   private static final ScriptScope NOOP_SCOPE = new ScriptScope() {
      @Override
      public Object getMember(String name) {
         return null;
      }

      @Override
      public boolean hasMember(String name) {
         return false;
      }

      @Override
      public void putMember(String name, Object value) {
      }

      @Override
      public Object[] getMemberKeys() {
         return new Object[0];
      }
   };
}
