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
package inetsoft.util.stall;

import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Helpers of the lock-stall watchdog tests (bug #76967). The test's own reader threads come
 * from {@link #readerPool()}; any other thread (a lens worker) is not a reader, so a base
 * table can block, slow down or fail on the workers only.
 */
public final class StallTestSupport {
   private StallTestSupport() {
   }

   /**
    * Daemon threads marked as readers.
    */
   public static ExecutorService readerPool() {
      return Executors.newCachedThreadPool(r -> {
         Thread thread = new Thread(() -> {
            READER.set(true);
            r.run();
         }, "stall-reader-" + SEQ.incrementAndGet());
         thread.setDaemon(true);
         return thread;
      });
   }

   /**
    * Stop the server's watchdog and forget the last dump of the server's dumper, so a test
    * sees neither the watchdog nor the dump back-off window of an earlier test.
    */
   public static void resetGlobalStallState() {
      StallWatchdog.resetForTest();
      StallDumper.global().resetForTest();
   }

   public static boolean isReader() {
      return READER.get();
   }

   /**
    * {@code rows} rows of {@code group, value}, value {@code i} for row {@code i}.
    */
   public static Object[][] data(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "group", "value" };

      for(int i = 1; i <= rows; i++) {
         data[i] = new Object[] { "k" + (i % 3), i };
      }

      return data;
   }

   public static List<List<Object>> drain(TableLens table) {
      List<List<Object>> rows = new ArrayList<>();

      for(int r = 0; table.moreRows(r); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = 0; c < table.getColCount(); c++) {
            row.add(table.getObject(r, c));
         }

         rows.add(row);
      }

      return rows;
   }

   /**
    * Get what {@code future} failed with, failing if it succeeded or is still waiting after
    * {@code capSeconds} (the wait is not bounded).
    */
   public static Throwable failureOf(Future<?> future, long capSeconds) throws Exception {
      try {
         Object value = future.get(capSeconds, TimeUnit.SECONDS);
         fail("expected a lock stall, got " + value);
         return null;
      }
      catch(ExecutionException ex) {
         return ex.getCause();
      }
      catch(TimeoutException ex) {
         fail("still waiting after " + capSeconds + " s, the wait is not bounded");
         return null;
      }
   }

   /**
    * Get the lock stall in the cause chain of {@code failure}, failing if there is none.
    */
   public static LockStallException stallIn(Throwable failure) {
      for(Throwable t = failure; t != null; t = t.getCause()) {
         if(t instanceof LockStallException) {
            return (LockStallException) t;
         }
      }

      throw new AssertionError("not a lock stall: " + failure, failure);
   }

   public static void awaitTrue(BooleanSupplier condition, long capSeconds, String what)
      throws InterruptedException
   {
      long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(capSeconds);

      while(!condition.getAsBoolean()) {
         assertTrue(System.currentTimeMillis() < deadline, what);
         Thread.sleep(20);
      }
   }

   /**
    * A table whose data rows block on non-reader threads until {@link #open()}.
    */
   public static class GatedTable extends DefaultTableLens {
      public GatedTable(int rows) {
         super(data(rows));
      }

      public void open() {
         gate.countDown();
      }

      /**
       * Block until {@link #open()} if this is not a reader thread.
       */
      public void pass() {
         if(!isReader()) {
            try {
               gate.await(60, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }
      }

      @Override
      public boolean moreRows(int row) {
         if(row >= 1) {
            pass();
         }

         return super.moreRows(row);
      }

      private final CountDownLatch gate = new CountDownLatch(1);
   }

   /**
    * A table that costs {@code millisPerRow} for every new data row read on a non-reader
    * thread: slow, but progressing.
    */
   public static class SlowTable extends DefaultTableLens {
      public SlowTable(int rows, long millisPerRow) {
         super(data(rows));
         this.millisPerRow = millisPerRow;
      }

      @Override
      public boolean moreRows(int row) {
         boolean slow;

         synchronized(this) {
            slow = row >= 1 && row > slowest && !isReader();

            if(slow) {
               slowest = row;
            }
         }

         if(slow) {
            pause();
         }

         return super.moreRows(row);
      }

      /**
       * Cost {@code millisPerRow} if this is not a reader thread.
       */
      public void pause() {
         if(millisPerRow > 0 && !isReader()) {
            try {
               Thread.sleep(millisPerRow);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }
      }

      private final long millisPerRow;
      private int slowest;
   }

   /**
    * A table whose rows from {@code failAt} on fail with {@code failure} on non-reader
    * threads, as if a nested wait of the worker stalled.
    */
   public static class FailingTable extends DefaultTableLens {
      public FailingTable(int rows, int failAt, LockStallException failure) {
         super(data(rows));
         this.failAt = failAt;
         this.failure = failure;
      }

      /**
       * Fail with the stall if this is not a reader thread.
       */
      public void failNow() {
         if(!isReader()) {
            throw failure;
         }
      }

      @Override
      public boolean moreRows(int row) {
         if(row >= failAt) {
            failNow();
         }

         return super.moreRows(row);
      }

      private final int failAt;
      private final LockStallException failure;
   }

   private static final ThreadLocal<Boolean> READER = ThreadLocal.withInitial(() -> false);
   private static final AtomicInteger SEQ = new AtomicInteger();
}
