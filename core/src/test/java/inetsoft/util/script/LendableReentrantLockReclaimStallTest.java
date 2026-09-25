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

import inetsoft.util.stall.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A lender waiting in the loan reclaim for a borrower that legitimately works with the lent
 * lock is not a stall (bug #76967, final review I1): the watchdog must not report the lender
 * as unreleased (health DOWN) nor dump it. A borrower that is truly stuck is still reported,
 * but the reclaim never throws.
 */
@Tag("core")
public class LendableReentrantLockReclaimStallTest {
   @BeforeEach
   public void setUp() {
      StallTestSupport.resetGlobalStallState();
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir));
   }

   @AfterEach
   public void tearDown() {
      release.countDown();
      StallPolicy.setOverride(null);
      StallTestSupport.resetGlobalStallState();
   }

   @Test
   public void busyBorrowerIsProgressForItsLenderInReclaim() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      LendableReentrantLock.Borrower borrower = new LendableReentrantLock.Borrower();
      CountDownLatch lent = new CountDownLatch(1);
      CountDownLatch acquired = new CountDownLatch(1);
      // the borrower holds the lent lock, running, for over 3 times the limit
      Thread worker = startDaemon(() -> {
         borrower.begin();

         try {
            lent.await(5, TimeUnit.SECONDS);
            lock.lock();

            try {
               acquired.countDown();
               long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(3500);

               while(System.nanoTime() < end) {
                  Thread.onSpinWait();
               }
            }
            finally {
               lock.unlock();
            }
         }
         catch(InterruptedException ignore) {
         }
         finally {
            borrower.end();
         }
      });
      FutureTask<Integer> lender = lender(lock, borrower, worker, lent, acquired);
      Thread lenderThread = startDaemon(lender);

      long deadline = System.currentTimeMillis() + 15000;

      while(!lender.isDone()) {
         assertTrue(System.currentTimeMillis() < deadline, "the reclaim never completed");
         String reason = scan(lenderThread);
         assertNull(reason, "a busy borrower is not a stall");

         for(WaitRecord record : WaitRegistry.global().getActive()) {
            if(record.getThread() == lenderThread) {
               assertNull(record.getDumpPath(), "a busy borrower must not be dumped");
               assertFalse(record.isTripped(), "a busy borrower must not trip the lender");
            }
         }

         Thread.sleep(100);
      }

      assertEquals(1, lender.get(), "the lender got its hold back");
      assertFalse(lock.isLocked());
      assertFalse(lock.isLent());
   }

   @Test
   public void parkedBorrowerIsReportedButTheReclaimNeverThrows() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      LendableReentrantLock.Borrower borrower = new LendableReentrantLock.Borrower();
      CountDownLatch lent = new CountDownLatch(1);
      CountDownLatch acquired = new CountDownLatch(1);
      // the borrower holds the lent lock, parked until released
      Thread worker = startDaemon(() -> {
         borrower.begin();

         try {
            lent.await(5, TimeUnit.SECONDS);
            lock.lock();

            try {
               acquired.countDown();
               release.await(30, TimeUnit.SECONDS);
            }
            finally {
               lock.unlock();
            }
         }
         catch(InterruptedException ignore) {
         }
         finally {
            borrower.end();
         }
      });
      FutureTask<Integer> lender = lender(lock, borrower, worker, lent, acquired);
      Thread lenderThread = startDaemon(lender);

      long deadline = System.currentTimeMillis() + 10000;

      while(true) {
         String reason = scan(lenderThread);

         if(reason != null) {
            assertTrue(reason.contains("LendableReentrantLock.reclaim"), reason);
            break;
         }

         assertTrue(System.currentTimeMillis() < deadline, "a stuck borrower is not reported");
         Thread.sleep(100);
      }

      assertFalse(lender.isDone(), "the lender must keep waiting for its borrower");
      release.countDown();
      assertEquals(1, lender.get(15, TimeUnit.SECONDS), "the lender got its hold back");
      worker.join(5000);
      assertNull(scan(lenderThread), "the reclaim ended");
      assertFalse(lock.isLocked());
      assertFalse(lock.isLent());
   }

   @Test
   public void reclaimOfAFreeLoanRegistersNothing() {
      LendableReentrantLock lock = new LendableReentrantLock();
      LendableReentrantLock.Borrower borrower = new LendableReentrantLock.Borrower();
      long before = WaitRegistry.global().getBeginCount();
      lock.lock();

      try {
         lock.lend(borrower).close();
         assertEquals(1, lock.getHoldCount());
      }
      finally {
         lock.unlock();
      }

      assertEquals(before, WaitRegistry.global().getBeginCount());
   }

   /**
    * A lens reader: its own wait is registered (and not sampled while it reclaims), it lends
    * the lock to the worker and closes the loan while the worker still holds the lock.
    */
   private static FutureTask<Integer> lender(LendableReentrantLock lock,
                                             LendableReentrantLock.Borrower borrower,
                                             Thread worker, CountDownLatch lent,
                                             CountDownLatch acquired)
   {
      return new FutureTask<>(() -> {
         try(WaitRecord ignored = WaitRegistry.begin("test.lensWait", () -> 0L,
                                                     () -> new Thread[] { worker }))
         {
            lock.lock();

            try {
               LendableReentrantLock.Loan loan = lock.lend(borrower);
               lent.countDown();
               assertTrue(acquired.await(5, TimeUnit.SECONDS), "borrower was not let in");
               loan.close();
               return lock.getHoldCount();
            }
            finally {
               lock.unlock();
            }
         }
      });
   }

   /**
    * Scan with the server's watchdog (its thread may scan too, with the same scan count), and
    * get the unreleased stalls of the waits of {@code thread}. Other tests of the same JVM may
    * have left hung threads registered, they are ignored.
    */
   private static String scan(Thread thread) {
      StallWatchdog.global().scan();
      String reason = StallWatchdog.getUnreleasedStallReason();
      String name = "on thread \"" + thread.getName() + "\"";

      if(reason == null) {
         return null;
      }

      String mine = java.util.Arrays.stream(reason.split("; "))
         .filter(part -> part.contains(name))
         .collect(java.util.stream.Collectors.joining("; "));
      return mine.isEmpty() ? null : mine;
   }

   private static Thread startDaemon(Runnable runnable) {
      Thread thread = new Thread(runnable, "reclaim-test-" + SEQ.incrementAndGet());
      thread.setDaemon(true);
      thread.start();
      return thread;
   }

   @TempDir
   File dumpDir;
   private final CountDownLatch release = new CountDownLatch(1);
   private static final java.util.concurrent.atomic.AtomicInteger SEQ =
      new java.util.concurrent.atomic.AtomicInteger();
}
