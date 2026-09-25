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
package inetsoft.uql.table;

import inetsoft.test.*;
import inetsoft.util.script.LendableReentrantLock;
import inetsoft.util.stall.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static inetsoft.util.stall.StallTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The bounded {@code moreRows} of {@link XSwappableTable} throws on a stall and never
 * reports it as the end of the table (bug #76967).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
public class XSwappableTableStallTest {
   @BeforeEach
   public void setUp() {
      resetGlobalStallState();
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir));
      pool = readerPool();
   }

   @AfterEach
   public void tearDown() {
      pool.shutdownNow();
      StallPolicy.setOverride(null);
   }

   @Test
   public void stallThrowsInsteadOfEndOfTable() throws Exception {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });
      Future<Boolean> reader =
         pool.submit(() -> table.moreRows(1, "test.site", () -> 0, () -> new Thread[0]));

      LockStallException stall = stallIn(failureOf(reader, 15));
      assertEquals("test.site", stall.getSite());
      assertFalse(table.isCompleted());
   }

   @Test
   public void producerAddingRowsIsProgress() throws Exception {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });
      Thread producer = new Thread(() -> {
         try {
            for(int i = 1; i <= 8; i++) {
               Thread.sleep(300);
               table.addRow(new Object[] { "r" + i, i });
            }
         }
         catch(InterruptedException ignore) {
         }
         finally {
            table.complete();
         }
      });
      producer.setDaemon(true);
      producer.start();
      Future<Boolean> reader =
         pool.submit(() -> table.moreRows(8, "test.site", () -> 0, () -> new Thread[0]));

      assertTrue(reader.get(15, TimeUnit.SECONDS), "slow but progressing is not a stall");
   }

   @Test
   public void completedTableRegistersNothing() {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });
      table.complete();
      long before = WaitRegistry.global().getBeginCount();

      assertFalse(table.moreRows(5, "test.site", () -> 0, () -> new Thread[0]));
      assertTrue(table.moreRows(0, "test.site", () -> 0, () -> new Thread[0]));
      assertEquals(before, WaitRegistry.global().getBeginCount());
   }

   @Test
   public void rowAlreadyThereRegistersNothing() {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });
      table.addRow(new Object[] { "c", "d" });
      long before = WaitRegistry.global().getBeginCount();

      assertTrue(table.moreRows(1, "test.site", () -> 0, () -> new Thread[0]));
      assertFalse(table.isCompleted());
      assertEquals(before, WaitRegistry.global().getBeginCount());
   }

   /**
    * A lock holder waits in the plain, unbounded {@code moreRows(int)} for a first row that
    * takes longer than the limit, e.g. a JDBC socket read of a database-side sort: the
    * producer runs, the holder is not RUNNABLE. A waiter shut out of the lock is credited
    * through the holder's credit-only wait and must not trip.
    */
   @Test
   public void waiterBehindAHolderOfASlowFirstRowDoesNotTrip() throws Exception {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });
      LendableReentrantLock lock = new LendableReentrantLock();
      CountDownLatch holding = new CountDownLatch(1);
      Thread producer = daemon(() -> {
         long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(3500);

         while(System.nanoTime() - end < 0) {
            Thread.onSpinWait();
         }

         table.addRow(new Object[] { "r1", 1 });
         table.complete();
      });
      table.setProducer(producer);
      producer.start();
      AtomicReference<Thread> holderThread = new AtomicReference<>();
      Future<Boolean> holder = pool.submit(() -> {
         lock.lock();

         try {
            holderThread.set(Thread.currentThread());
            holding.countDown();
            return table.moreRows(1);
         }
         finally {
            lock.unlock();
         }
      });
      assertTrue(holding.await(5, TimeUnit.SECONDS));
      awaitTrue(() -> holderThread.get().getState() == Thread.State.TIMED_WAITING, 5,
                "the holder waits for the row");
      Future<Object> shutOut = pool.submit(() -> {
         lock.lock();
         lock.unlock();
         return null;
      });

      assertTrue(holder.get(15, TimeUnit.SECONDS), "the row arrives");
      assertNull(shutOut.get(15, TimeUnit.SECONDS),
                 "a waiter behind a healthy slow producer is not a stall");
   }

   /**
    * Same as {@link #waiterBehindAHolderOfASlowFirstRowDoesNotTrip()}, but the producer is
    * parked (e.g. queued, or in a wait of its own that is not registered): no credit, the
    * shut-out waiter trips.
    */
   @Test
   public void waiterBehindAHolderOfAParkedProducerTrips() throws Exception {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });
      LendableReentrantLock lock = new LendableReentrantLock();
      CountDownLatch holding = new CountDownLatch(1);
      CountDownLatch parked = new CountDownLatch(1);
      Thread producer = daemon(() -> {
         try {
            parked.await(30, TimeUnit.SECONDS);
         }
         catch(InterruptedException ignore) {
         }
         finally {
            table.complete();
         }
      });
      table.setProducer(producer);
      producer.start();
      Future<Boolean> holder = pool.submit(() -> {
         lock.lock();

         try {
            holding.countDown();
            return table.moreRows(1);
         }
         finally {
            lock.unlock();
         }
      });

      try {
         assertTrue(holding.await(5, TimeUnit.SECONDS));
         Future<Object> shutOut = pool.submit(() -> {
            lock.lock();
            lock.unlock();
            return null;
         });

         assertEquals("LendableReentrantLock.lock", stallIn(failureOf(shutOut, 15)).getSite());
         assertFalse(holder.isDone(), "the credit-only wait itself never fails");
      }
      finally {
         parked.countDown();
      }

      assertFalse(holder.get(15, TimeUnit.SECONDS));
   }

   /**
    * The producer needs the lock the waiting reader holds: a real cycle through the
    * unbounded wait still trips the producer's registered wait, and the reader gets the end
    * of the (failed) table instead of hanging.
    */
   @Test
   public void cycleThroughTheUnboundedWaitStillTrips() throws Exception {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });
      LendableReentrantLock lock = new LendableReentrantLock();
      CountDownLatch holding = new CountDownLatch(1);
      AtomicReference<LockStallException> stall = new AtomicReference<>();
      Thread producer = daemon(() -> {
         try {
            holding.await(5, TimeUnit.SECONDS);
            lock.lock();
            lock.unlock();
            table.addRow(new Object[] { "r1", 1 });
         }
         catch(LockStallException ex) {
            stall.set(ex);
         }
         catch(InterruptedException ignore) {
         }
         finally {
            table.complete();
         }
      });
      table.setProducer(producer);
      producer.start();
      Future<Boolean> holder = pool.submit(() -> {
         lock.lock();

         try {
            holding.countDown();
            return table.moreRows(1);
         }
         finally {
            lock.unlock();
         }
      });

      assertFalse(holder.get(15, TimeUnit.SECONDS), "the failed producer ends the table");
      assertNotNull(stall.get(), "members of a cycle must not credit each other");
   }

   @Test
   public void unboundedWaitRegistersNothingOnItsFastPath() {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });
      table.addRow(new Object[] { "c", "d" });
      long before = WaitRegistry.global().getBeginCount();

      assertTrue(table.moreRows(1));
      table.complete();
      assertFalse(table.moreRows(5));
      assertEquals(before, WaitRegistry.global().getBeginCount());
   }

   @Test
   public void unboundedWaitIsRegisteredAsCreditOnly() throws Exception {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });
      long before = WaitRegistry.global().getBeginCount();
      Future<Boolean> reader = pool.submit(() -> table.moreRows(1));

      awaitTrue(() -> WaitRegistry.global().getActive().stream()
                   .anyMatch(r -> r.isCreditOnly() &&
                      "XSwappableTable.moreRows".equals(r.getWhat())), 5,
                "the slow path registers a credit-only wait");
      // well past the limit: the credit-only wait never fails
      Thread.sleep(1500);
      assertFalse(reader.isDone());
      table.complete();
      assertFalse(reader.get(15, TimeUnit.SECONDS));
      assertEquals(before + 1, WaitRegistry.global().getBeginCount());
      awaitTrue(() -> WaitRegistry.global().getActive().isEmpty(), 5, "closed in finally");
   }

   private static Thread daemon(Runnable runnable) {
      Thread thread = new Thread(runnable);
      thread.setDaemon(true);
      return thread;
   }

   @TempDir
   File dumpDir;
   private ExecutorService pool;
}
