/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.lens;

import inetsoft.report.TableLens;
import inetsoft.test.*;
import inetsoft.util.ThreadPool;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.LendableReentrantLock;
import inetsoft.util.stall.StallPolicy;
import inetsoft.util.stall.StallTestSupport;
import inetsoft.util.stall.StallWatchdog;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A read of a distinct table lens concurrent with invalidate() returns the distinct row, never
 * the header or a cell of a disposed row list, and a worker started before invalidate() never
 * adds rows to, or completes, the rows of the next pass (bug #77333).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
public class DistinctTableLensInvalidateRaceTest {
   @BeforeEach
   public void setUp() {
      StallTestSupport.resetGlobalStallState();
      // the production policy: a wait fails only once a cycle is confirmed
      StallPolicy.setOverride(
         new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir, 20, false));
   }

   @AfterEach
   public void tearDown() {
      StallTestSupport.clearOverride();
   }

   @Test
   public void readsDuringInvalidateReturnDistinctRow() throws Exception {
      readsDuringInvalidate(new int[] { 0 });
   }

   /**
    * As readsDuringInvalidateReturnDistinctRow, with the distinct rows found by sorting.
    */
   @Test
   public void readsDuringInvalidateReturnSortedDistinctRow() throws Exception {
      readsDuringInvalidate(new int[] { 0, 1 });
   }

   private void readsDuringInvalidate(int[] cols) throws Exception {
      DistinctTableLens lens = new DistinctTableLens(new DefaultTableLens(data(ROWS)), cols);
      assertTrue(lens.moreRows(ROWS));

      AtomicBoolean done = new AtomicBoolean();
      AtomicLong invalidations = new AtomicLong();
      Map<String, Integer> results = new ConcurrentHashMap<>();

      Thread invalidator = new Thread(() -> {
         while(!done.get()) {
            lens.invalidate();
            lens.moreRows(TableLens.EOT);
            invalidations.incrementAndGet();
         }
      }, "DistinctTableLensInvalidateRaceTest-invalidator");

      Thread[] readers = new Thread[READERS];

      for(int i = 0; i < readers.length; i++) {
         readers[i] = new Thread(() -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();

            while(!done.get()) {
               // every id is distinct: distinct row r is base row r, id = value = r
               int r = 1 + random.nextInt(ROWS);
               String result;

               try {
                  Object value = lens.getObject(r, 1);

                  if(Integer.valueOf(r).equals(value)) {
                     result = "ok";
                  }
                  else if("value".equals(value)) {
                     result = "header";
                  }
                  else {
                     result = "wrong";
                  }
               }
               catch(Throwable ex) {
                  result = ex.getClass().getSimpleName();
               }

               results.merge(result, 1, Integer::sum);
            }
         }, "DistinctTableLensInvalidateRaceTest-reader-" + i);
      }

      invalidator.start();

      for(Thread reader : readers) {
         reader.start();
      }

      Thread.sleep(DURATION_MS);
      done.set(true);
      invalidator.join(JOIN_MS);

      for(Thread reader : readers) {
         reader.join(JOIN_MS);
      }

      assertFalse(invalidator.isAlive(), "invalidator did not stop");

      for(Thread reader : readers) {
         assertFalse(reader.isAlive(), "reader did not stop");
      }

      String summary = "invalidations=" + invalidations.get() + " results=" + results;
      assertTrue(invalidations.get() > 0, summary);
      assertTrue(results.getOrDefault("ok", 0) > 0, summary);
      assertEquals(0, results.getOrDefault("header", 0), summary);
      // no other wrong value or exception either
      assertEquals(1, results.size(), summary);
   }

   @Test
   public void workerStartedBeforeInvalidateDoesNotAddRowsToNextPass() throws Exception {
      supersededWorkerSchedule(new int[] { 0 });
   }

   /**
    * As workerStartedBeforeInvalidateDoesNotAddRowsToNextPass, with the distinct rows found
    * by sorting (more than one column).
    */
   @Test
   public void sortingWorkerStartedBeforeInvalidateDoesNotAddRowsToNextPass() throws Exception {
      supersededWorkerSchedule(new int[] { 0, 1 });
   }

   private void supersededWorkerSchedule(int[] cols) throws Exception {
      List<List<Object>> expected = drain(new DistinctTableLens(new DefaultTableLens(data(ROWS)),
                                                                cols));
      GatedTable base = new GatedTable(ROWS, GATE_ROW, null);
      DistinctTableLens lens = new DistinctTableLens(base, cols);
      FREE.set(true);

      try {
         // starts the worker, which stops halfway, at the gate. with sorting, row 1 is only
         // there once the worker passes the gate
         Future<Boolean> first = CompletableFuture.supplyAsync(() -> lens.moreRows(1));
         Thread worker = base.awaitParked(CAP_SECONDS);
         assertNotSame(Thread.currentThread(), worker, "the pass runs in the background");

         lens.invalidate();
         base.open();
         first.get(CAP_SECONDS, TimeUnit.SECONDS);
         lens.moreRows(TableLens.EOT);
         int count = lens.getRowCount();

         awaitIdle();
         List<List<Object>> actual = drain(lens);

         String summary = "rows when moreRows(EOT) returned=" + count +
            ", once the workers are done=" + actual.size() + ", fresh=" + expected.size();
         assertEquals(expected.size(), count, summary);
         assertTrue(expected.equals(actual), summary);
      }
      finally {
         FREE.remove();
         base.open();
      }
   }

   /**
    * A lens read to the end, also after invalidate(), serializes with its rows.
    */
   @Test
   public void serializesRowsOfCurrentPass() throws Exception {
      DistinctTableLens lens = new DistinctTableLens(new DefaultTableLens(data(ROWS)),
                                                     new int[] { 0 });
      List<List<Object>> expected = drain(lens);
      lens.invalidate();
      assertFalse(lens.moreRows(TableLens.EOT));

      TableLens copy = (TableLens) TestSerializeUtils.serializeAndDeserialize(lens);
      assertTrue(expected.equals(drain(copy)), "rows after the round trip");
      assertEquals(expected.size(), copy.getRowCount());
   }

   /**
    * A pass that is cancelled and then invalidated ends, with none of its rows in the next
    * pass. The cancel stays on the lens, so the next pass may end early too.
    */
   @Test
   public void cancelledPassThenInvalidateEnds() throws Exception {
      GatedTable base = new GatedTable(ROWS, GATE_ROW, null);
      DistinctTableLens lens = new DistinctTableLens(base, new int[] { 0 });
      FREE.set(true);

      try {
         Future<Boolean> first = CompletableFuture.supplyAsync(() -> lens.moreRows(1));
         base.awaitParked(CAP_SECONDS);
         lens.cancel();
         lens.invalidate();
         base.open();
         first.get(CAP_SECONDS, TimeUnit.SECONDS);

         assertTimeoutPreemptively(Duration.ofSeconds(CAP_SECONDS),
                                   () -> lens.moreRows(TableLens.EOT));
         awaitIdle();
         assertPrefixOfFresh(drain(lens));
      }
      finally {
         FREE.remove();
         base.open();
      }
   }

   /**
    * A reader waiting for rows returns once the lens is disposed and its worker ends.
    */
   @Test
   public void readerWaitingOnDisposedLensReturns() throws Exception {
      GatedTable base = new GatedTable(ROWS, GATE_ROW, null);
      DistinctTableLens lens = new DistinctTableLens(base, new int[] { 0 });
      ExecutorService pool = freePool();

      try {
         Future<Boolean> reader = pool.submit(() -> lens.moreRows(TableLens.EOT));
         base.awaitParked(CAP_SECONDS);
         lens.dispose();
         base.open();

         assertFalse(reader.get(CAP_SECONDS, TimeUnit.SECONDS));
         awaitIdle();
         assertNull(StallWatchdog.getUnreleasedStallReason());
      }
      finally {
         base.open();
         pool.shutdownNow();
      }
   }

   /**
    * invalidate() between validate() and the start of the worker: the worker still works on
    * the rows validate() found, which invalidate() replaced, so it adds nothing to the next
    * pass. The on-demand pool is kept busy to hold the worker in its queue.
    */
   @Test
   public void workerQueuedBeforeInvalidateDoesNotAddRowsToNextPass() throws Exception {
      List<List<Object>> expected = drain(new DistinctTableLens(new DefaultTableLens(data(ROWS)),
                                                                new int[] { 0 }));
      DistinctTableLens lens = new DistinctTableLens(new DefaultTableLens(data(ROWS)),
                                                     new int[] { 0 });
      Field poolField = ThreadPool.class.getDeclaredField("onDemandPool");
      poolField.setAccessible(true);
      ThreadPool onDemand = (ThreadPool) poolField.get(null);
      Field threadsField = ThreadPool.class.getDeclaredField("threads");
      threadsField.setAccessible(true);
      List<?> threads = (List<?>) threadsField.get(onDemand);
      int soft = onDemand.getSoftLimit();
      int hard = onDemand.getHardLimit();
      CountDownLatch release = new CountDownLatch(1);
      AtomicLong busy = new AtomicLong();

      try {
         // no new thread, and every thread busy: the worker waits in the queue
         int limit = Math.max(1, threads.size());
         onDemand.resize(limit, limit);
         long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(CAP_SECONDS);

         while(threads.isEmpty() || busy.get() < threads.size()) {
            assertTrue(System.currentTimeMillis() < deadline, "the on-demand pool is not busy");

            if(onDemand.getPendingCount() == 0) {
               ThreadPool.addOnDemand(() -> {
                  busy.incrementAndGet();

                  try {
                     release.await(CAP_SECONDS, TimeUnit.SECONDS);
                  }
                  catch(InterruptedException ex) {
                     Thread.currentThread().interrupt();
                  }
               });
            }

            Thread.sleep(10);
         }

         int pending = onDemand.getPendingCount();
         Future<Boolean> first = CompletableFuture.supplyAsync(() -> lens.moreRows(1));
         awaitTrue(() -> onDemand.getPendingCount() > pending, "the worker is queued");
         lens.invalidate();
         release.countDown();

         assertTrue(first.get(CAP_SECONDS, TimeUnit.SECONDS));
         lens.moreRows(TableLens.EOT);
         awaitIdle();
         List<List<Object>> actual = drain(lens);
         assertTrue(expected.equals(actual),
                    "rows=" + actual.size() + ", fresh=" + expected.size());
      }
      finally {
         release.countDown();
         onDemand.resize(soft, hard);
      }
   }

   /**
    * Fail unless {@code rows} are the header and the first distinct rows of a fresh lens.
    */
   private static void assertPrefixOfFresh(List<List<Object>> rows) {
      List<List<Object>> fresh = drain(new DefaultTableLens(data(ROWS)));
      assertTrue(rows.size() <= fresh.size() && fresh.subList(0, rows.size()).equals(rows),
                 () -> "rows=" + rows.size() + ", starting " +
                    rows.subList(0, Math.min(3, rows.size())));
   }

   /**
    * A reader holding an engine lock lends it to the worker it waits for. When invalidate()
    * supersedes that worker while it is inside a base read taking the lock, the reader goes
    * on with the next pass, it is not left waiting for the old one.
    */
   @Test
   public void lendingReaderIsNotLeftWaitingWhenWorkerIsSuperseded() throws Exception {
      lendingReaderSchedule(true);
   }

   /**
    * As lendingReaderIsNotLeftWaitingWhenWorkerIsSuperseded, the superseded worker is waiting
    * to take the lock it was lent.
    */
   @Test
   public void lendingReaderIsNotLeftWaitingWhenWorkerWaitsForLock() throws Exception {
      lendingReaderSchedule(false);
   }

   private void lendingReaderSchedule(boolean gateInsideLock) throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      // the base takes the lock itself, it is not visible to ChainScriptLock, as with a
      // calc field under the lens, so a lock-free first reader starts a worker
      GatedTable base = new GatedTable(ROWS, GATE_ROW, lock, gateInsideLock);
      DistinctTableLens lens = new DistinctTableLens(base, new int[] { 0 });
      ExecutorService pool = freePool();
      CountDownLatch locked = new CountDownLatch(1);
      CountDownLatch go = new CountDownLatch(1);

      try {
         Future<List<List<Object>>> reader = pool.submit(() -> {
            lock.lock();

            try {
               locked.countDown();
               go.await();
               JavaScriptEngine.pushHeldScriptLock(lock);

               try {
                  lens.moreRows(TableLens.EOT);
                  return drain(lens);
               }
               finally {
                  JavaScriptEngine.popHeldScriptLock();
               }
            }
            finally {
               lock.unlock();
            }
         });

         assertTrue(locked.await(CAP_SECONDS, TimeUnit.SECONDS));
         // a lock-free reader starts the worker
         assertTrue(pool.submit(() -> lens.moreRows(1)).get(CAP_SECONDS, TimeUnit.SECONDS));
         go.countDown();

         base.awaitParked(CAP_SECONDS);
         awaitTrue(lock::isLent, "the reader lends the lock to the worker");
         lens.invalidate();
         base.open();

         List<List<Object>> rows = reader.get(CAP_SECONDS, TimeUnit.SECONDS);
         awaitIdle();

         // no row of the superseded worker in the rows of the next pass
         List<List<Object>> expected = drain(new DefaultTableLens(data(ROWS)));
         assertTrue(expected.equals(rows), () -> "the lending reader read " + rows.size() +
            " rows, starting " + rows.subList(0, Math.min(3, rows.size())) + ", fresh=" +
            expected.size());
         assertNull(StallWatchdog.getUnreleasedStallReason());
      }
      finally {
         base.open();
         pool.shutdownNow();
      }
   }

   /**
    * With the engine lock visible to ChainScriptLock, every pass runs on a reader holding the
    * lock. A reader that read the lens, keeps the lock and reads it again after invalidate()
    * computes the next pass itself, and a second reader waits only for the lock.
    */
   @Test
   public void chainLockReaderReadsAgainAfterInvalidate() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      ScriptLockTable base = new ScriptLockTable(ROWS, lock);
      DistinctTableLens lens = new DistinctTableLens(base, new int[] { 0 });
      ExecutorService pool = freePool();
      CountDownLatch firstRead = new CountDownLatch(1);
      CountDownLatch invalidated = new CountDownLatch(1);

      try {
         Future<List<List<Object>>> reader = pool.submit(() -> {
            lock.lock();
            JavaScriptEngine.pushHeldScriptLock(lock);

            try {
               assertTrue(lens.moreRows(50));
               firstRead.countDown();
               invalidated.await();
               assertEquals(51, lens.getObject(51, 1));
               return drain(lens);
            }
            finally {
               JavaScriptEngine.popHeldScriptLock();
               lock.unlock();
            }
         });

         assertTrue(firstRead.await(CAP_SECONDS, TimeUnit.SECONDS));
         lens.invalidate();

         CompletableFuture<Thread> secondThread = new CompletableFuture<>();
         Future<Boolean> second = pool.submit(() -> {
            secondThread.complete(Thread.currentThread());
            return lens.moreRows(51);
         });
         Thread waiter = secondThread.get(CAP_SECONDS, TimeUnit.SECONDS);
         awaitTrue(() -> isIn(waiter, LendableReentrantLock.class),
                   "the second reader waits for the lock");
         invalidated.countDown();

         assertEquals(ROWS + 1, reader.get(CAP_SECONDS, TimeUnit.SECONDS).size());
         assertTrue(second.get(CAP_SECONDS, TimeUnit.SECONDS));
         assertNull(StallWatchdog.getUnreleasedStallReason());
      }
      finally {
         pool.shutdownNow();
      }
   }

   /**
    * Wait until no thread but this one runs code of the lens, i.e. every pass is done.
    */
   private static void awaitIdle() throws InterruptedException {
      long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(CAP_SECONDS);

      while(true) {
         Optional<Thread> running = Thread.getAllStackTraces().keySet().stream()
            .filter(t -> t != Thread.currentThread() && isIn(t, DistinctTableLens.class))
            .findFirst();

         if(running.isEmpty()) {
            break;
         }

         if(System.currentTimeMillis() >= deadline) {
            fail("a worker is still running: " + running.get() + " " + running.get().getState() +
                    "\n" + Arrays.toString(running.get().getStackTrace()).replace(", ", "\n  "));
         }

         Thread.sleep(20);
      }

      Thread.sleep(IDLE_MS);
   }

   private static boolean isIn(Thread thread, Class<?> cls) {
      for(StackTraceElement element : thread.getStackTrace()) {
         // a pool thread appends the stack that created it
         if(element.getClassName().equals(Thread.class.getName()) &&
            element.getMethodName().equals("getStackTrace"))
         {
            break;
         }

         if(element.getClassName().equals(cls.getName())) {
            return true;
         }
      }

      return false;
   }

   private static void awaitTrue(java.util.function.BooleanSupplier condition, String what)
      throws InterruptedException
   {
      long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(CAP_SECONDS);

      while(!condition.getAsBoolean()) {
         assertTrue(System.currentTimeMillis() < deadline, what);
         Thread.sleep(10);
      }
   }

   private static List<List<Object>> drain(TableLens table) {
      List<List<Object>> rows = new ArrayList<>();

      for(int r = 0; table.moreRows(r); r++) {
         rows.add(Arrays.asList(table.getObject(r, 0), table.getObject(r, 1)));
      }

      return rows;
   }

   /**
    * {@code rows} rows of {@code id, value}, both {@code r} for row {@code r}.
    */
   private static Object[][] data(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "id", "value" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { r, r };
      }

      return data;
   }

   /**
    * Threads that the gate of a {@link GatedTable} lets through.
    */
   private static ExecutorService freePool() {
      return Executors.newCachedThreadPool(r -> {
         Thread thread = new Thread(() -> {
            FREE.set(true);
            r.run();
         });
         thread.setDaemon(true);
         return thread;
      });
   }

   /**
    * A table that stops a worker at its first read of row {@code gateRow} or later until
    * {@link #open()}, and that takes {@code lock}, if any, when a worker reads row
    * {@code gateRow} or later.
    */
   private static class GatedTable extends DefaultTableLens {
      GatedTable(int rows, int gateRow, LendableReentrantLock lock) {
         this(rows, gateRow, lock, false);
      }

      GatedTable(int rows, int gateRow, LendableReentrantLock lock, boolean gateInsideLock) {
         super(data(rows));
         this.gateRow = gateRow;
         this.lock = lock;
         this.gateInsideLock = gateInsideLock;
      }

      @Override
      public boolean moreRows(int row) {
         if(row >= gateRow && !FREE.get()) {
            if(!gateInsideLock) {
               pass(row);
            }

            if(lock != null) {
               lock.lock();

               try {
                  if(gateInsideLock) {
                     pass(row);
                  }
               }
               finally {
                  lock.unlock();
               }
            }
         }

         return super.moreRows(row);
      }

      private void pass(int row) {
         // the first read at or past the gate row, e.g. moreRows(EOT) of a sort
         if(row < gateRow || gate.getCount() == 0) {
            return;
         }

         parked.complete(Thread.currentThread());

         try {
            gate.await(CAP_SECONDS, TimeUnit.SECONDS);
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }
      }

      Thread awaitParked(long capSeconds) throws Exception {
         return parked.get(capSeconds, TimeUnit.SECONDS);
      }

      void open() {
         gate.countDown();
      }

      private final int gateRow;
      private final LendableReentrantLock lock;
      private final boolean gateInsideLock;
      private final CountDownLatch gate = new CountDownLatch(1);
      private final CompletableFuture<Thread> parked = new CompletableFuture<>();
   }

   /**
    * A table whose reads take an engine lock that ChainScriptLock finds.
    */
   private static class ScriptLockTable extends DefaultTableLens implements ChainScriptLock.Source {
      ScriptLockTable(int rows, LendableReentrantLock lock) {
         super(data(rows));
         this.lock = lock;
      }

      @Override
      public Lock getScriptLock() {
         return lock;
      }

      @Override
      public boolean moreRows(int row) {
         // the header is read without the lock, by invalidate() too
         if(row < 1) {
            return super.moreRows(row);
         }

         lock.lock();

         try {
            return super.moreRows(row);
         }
         finally {
            lock.unlock();
         }
      }

      private final LendableReentrantLock lock;
   }

   @TempDir
   File dumpDir;

   private static final int ROWS = 300;
   private static final int GATE_ROW = 150;
   private static final int READERS = 2;
   private static final long DURATION_MS = 3000;
   private static final long JOIN_MS = 10000;
   private static final long CAP_SECONDS = 30;
   private static final long IDLE_MS = 200;
   private static final ThreadLocal<Boolean> FREE = ThreadLocal.withInitial(() -> false);
}
