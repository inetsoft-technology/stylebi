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

package inetsoft.report.lens;

import inetsoft.report.TableLens;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.filter.SummaryFilter;
import inetsoft.report.internal.table.MergedTable;
import inetsoft.test.*;
import inetsoft.util.CoreTool;
import inetsoft.util.MessageException;
import inetsoft.util.UserMessage;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.LendableReentrantLock;
import inetsoft.util.script.ScriptException;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A summary filter, sort filter, self join or cross join over a base whose read fails (e.g. a
 * set table whose merged table can't be created, bug #77524) fails its reads too, it never
 * reports a completed empty or partial table, nor waits forever, and a later read recovers
 * once the base does (bug #77875).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
public class SummaryFilterBaseFailureTest {
   @BeforeEach
   public void setUp() {
      CoreTool.clearUserMessage();
   }

   /** The reporter's input: a summary over a minus whose merged table can't be created. */
   @Test
   public void summaryOverFailingMinusThrows() throws Exception {
      FailingMinus minus = failingMinus();
      SummaryFilter summary = summary(minus);

      assertThrows(SetTableLens.SetOperationException.class,
                   () -> summary.moreRows(TableLens.EOT));
      // each read fails again within the retry delay of the base, never 0 rows
      assertThrows(SetTableLens.SetOperationException.class, () -> summary.getObject(1, 1));
      assertThrows(SetTableLens.SetOperationException.class, () -> summary.moreRows(1));
      assertRowCountFails(summary, SetTableLens.SetOperationException.class);
   }

   /** The same on the synchronous path, the reading thread holds a script engine lock. */
   @Test
   public void summaryOverFailingMinusThrowsOnSyncPath() {
      FailingMinus minus = failingMinus();
      SummaryFilter summary = summary(minus);
      LendableReentrantLock lock = new LendableReentrantLock();
      lock.lock();
      JavaScriptEngine.pushHeldScriptLock(lock);

      try {
         assertThrows(SetTableLens.SetOperationException.class, summary::getRowCount);
         assertThrows(SetTableLens.SetOperationException.class,
                      () -> summary.moreRows(TableLens.EOT));
      }
      finally {
         JavaScriptEngine.popHeldScriptLock();
         lock.unlock();
      }
   }

   /** A reported failure restarts the summary, which recovers with the base. */
   @Test
   public void summaryRecoversWithBase() {
      FailingMinus minus = failingMinus();
      minus.control.retryDelay = 0;
      SummaryFilter summary = summary(minus);

      assertThrows(SetTableLens.SetOperationException.class,
                   () -> summary.moreRows(TableLens.EOT));

      minus.control.failCreate.set(false);
      assertFalse(summary.moreRows(TableLens.EOT));
      assertEquals(6, summary.getRowCount());
      assertEquals("k1", summary.getObject(1, 0));
      assertEquals(1, ((Number) summary.getObject(1, 1)).doubleValue());
   }

   /** A header read on the calling thread fails, the next header read retries it. */
   @Test
   public void summaryHeaderReadRecoversWithBase() {
      FailingMinus minus = failingMinus();
      minus.control.retryDelay = 0;
      SummaryFilter summary = summary(minus);

      assertThrows(SetTableLens.SetOperationException.class, () -> summary.getObject(0, 0));

      minus.control.failCreate.set(false);
      assertEquals("id", summary.getObject(0, 0));
      assertFalse(summary.moreRows(TableLens.EOT));
      assertEquals(6, summary.getRowCount());
   }

   /** A message exception of a plain base's data rows fails the summary. */
   @Test
   public void summaryOverMessageExceptionThrows() {
      AtomicBoolean fail = new AtomicBoolean(true);
      SummaryFilter summary = summary(failingRows(fail, () -> new MessageException("base failed")));

      MessageException ex =
         assertThrows(MessageException.class, () -> summary.moreRows(TableLens.EOT));
      assertEquals("base failed", ex.getMessage());

      fail.set(false);
      assertFalse(summary.moreRows(TableLens.EOT));
      assertEquals(6, summary.getRowCount());
   }

   /** A wrapped swap file read failure of a plain base's data rows fails the summary. */
   @Test
   public void summaryOverSwapFailureThrows() throws Exception {
      AtomicBoolean fail = new AtomicBoolean(true);
      SummaryFilter summary = summary(failingRows(fail, () -> new IllegalStateException(
         "wrapped", new SwapFileReadException(new File("swap"), new IOException("gone")))));

      assertThrows(SwapFileReadException.class, () -> summary.moreRows(TableLens.EOT));
      assertRowCountFails(summary, SwapFileReadException.class);
   }

   /**
    * A script failure keeps its user message and the partial table, even if its cause is a
    * message exception, e.g. of a calc field reading a failing table.
    */
   @Test
   public void summaryOverScriptExceptionIsTolerated() {
      AtomicBoolean fail = new AtomicBoolean(true);
      SummaryFilter summary = summary(failingRows(fail, () -> new ScriptException(
         "bad calc", new MessageException("base failed"))));

      assertFalse(assertDoesNotThrow(() -> summary.moreRows(TableLens.EOT)));
      assertEquals(1, assertDoesNotThrow(summary::getRowCount));
      UserMessage msg = CoreTool.getUserMessage();
      assertNotNull(msg);
      assertTrue(msg.getMessage().contains("bad calc"), msg.getMessage());
   }

   /**
    * A reader waiting for a failed pass that another reader restarted rethrows the failure,
    * it neither takes the pass for the end of the table nor computes the table again.
    */
   @Test
   public void supersededFailedPassRethrows() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      FailingMinus minus = failingMinus();
      minus.control.retryDelay = 0;
      minus.control.gate = release;
      // the base fails once, then recovers
      minus.control.failCreate.set(false);
      minus.control.failOnce = true;
      SummaryFilter summary = summary(minus);
      ExecutorService readers = Executors.newFixedThreadPool(2);

      try {
         Thread[] threads = new Thread[2];
         Future<?>[] results = new Future<?>[2];

         for(int i = 0; i < 2; i++) {
            int idx = i;
            results[i] = readers.submit(() -> {
               threads[idx] = Thread.currentThread();
               return summary.moreRows(TableLens.EOT);
            });
         }

         // both readers wait for the rows of the first pass before the base fails
         awaitWaiting(threads);
         release.countDown();

         for(Future<?> result : results) {
            ExecutionException ex = assertThrows(ExecutionException.class,
                                                 () -> result.get(30, TimeUnit.SECONDS));
            assertInstanceOf(SetTableLens.SetOperationException.class, ex.getCause());
         }

         assertEquals(1, minus.control.creates.get(), "no reader computed the table again");
      }
      finally {
         release.countDown();
         readers.shutdownNow();
      }

      assertFalse(summary.moreRows(TableLens.EOT));
      assertEquals(6, summary.getRowCount());
   }

   /** The production grouping shape: a summary over a sort over the failing minus. */
   @Test
   public void summaryOverSortOverFailingMinusThrows() throws Exception {
      FailingMinus minus = failingMinus();
      minus.control.retryDelay = 0;
      SummaryFilter summary = summary(new SortFilter(minus, new int[] { 0 }, true));

      assertThrows(SetTableLens.SetOperationException.class,
                   () -> summary.moreRows(TableLens.EOT));
      assertRowCountFails(summary, SetTableLens.SetOperationException.class);

      minus.control.failCreate.set(false);
      assertFalse(summary.moreRows(TableLens.EOT));
      assertEquals(6, summary.getRowCount());
   }

   /** A sort over the failing minus fails its row iteration, then sorts once it recovers. */
   @Test
   public void sortOverFailingMinusThrowsThenSorts() {
      FailingMinus minus = failingMinus();
      SortFilter sort = new SortFilter(minus, new int[] { 1 }, false);

      assertThrows(SetTableLens.SetOperationException.class, () -> sort.moreRows(1));
      assertThrows(SetTableLens.SetOperationException.class,
                   () -> sort.moreRows(TableLens.EOT));

      minus.control.retryDelay = 0;
      minus.control.failCreate.set(false);
      assertTrue(sort.moreRows(1));
      assertEquals(5, sort.getObject(1, 1));
      assertFalse(sort.moreRows(TableLens.EOT));
      assertEquals(6, sort.getRowCount());
   }

   /** A self join over the failing minus fails, then joins again once it recovers. */
   @Test
   public void selfJoinOverFailingMinusThrowsThenRecovers() {
      FailingMinus minus = failingMinus();
      minus.control.retryDelay = 0;
      SelfJoinTableLens join = new SelfJoinTableLens(minus);

      assertThrows(SetTableLens.SetOperationException.class,
                   () -> join.moreRows(TableLens.EOT));

      minus.control.failCreate.set(false);
      assertFalse(join.moreRows(TableLens.EOT));
      assertEquals(6, join.getRowCount());
   }

   /**
    * A cross join whose loader thread dies of a failing set lens fails its readers instead of
    * leaving them waiting forever, with the set lens failure as the cause (bug #77907).
    */
   @Test
   public void crossJoinOverFailingMinusThrows() {
      FailingMinus minus = new FailingMinus(data(5), data(0));
      CrossJoinTableLens join = new CrossJoinTableLens(minus, new DefaultTableLens(data(2)));
      assertFalse(join.moreRows(TableLens.EOT));
      assertEquals(11, join.getRowCount());

      // the base fails later, its change event invalidates the join
      minus.control.failCreate.set(true);
      minus.invalidate();

      assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
         CrossJoinTableLens.CrossJoinException ex = assertThrows(
            CrossJoinTableLens.CrossJoinException.class, () -> join.moreRows(TableLens.EOT));
         assertInstanceOf(SetTableLens.SetOperationException.class, ex.getCause());
         assertThrows(CrossJoinTableLens.CrossJoinException.class, join::getRowCount);
      });
   }

   private static SummaryFilter summary(TableLens base) {
      return new SummaryFilter(base, new int[] { 0 }, new int[] { 1 }, new SumFormula(), null);
   }

   private static FailingMinus failingMinus() {
      FailingMinus minus = new FailingMinus(data(5), data(0));
      minus.control.failCreate.set(true);
      return minus;
   }

   /**
    * A table whose data rows from the third one fail while {@code fail} is set.
    */
   private static DefaultTableLens failingRows(AtomicBoolean fail,
                                               java.util.function.Supplier<RuntimeException> ex)
   {
      return new DefaultTableLens(data(5)) {
         @Override
         public Object getObject(int r, int c) {
            if(r >= 3 && fail.get()) {
               throw ex.get();
            }

            return super.getObject(r, c);
         }
      };
   }

   /**
    * The row count of a failing table is "loading" (negative) until a pass completes, then
    * the failure, never a count.
    */
   private static void assertRowCountFails(TableLens table, Class<? extends Throwable> type)
      throws InterruptedException
   {
      long end = System.currentTimeMillis() + 20000;

      while(true) {
         int count;

         try {
            count = table.getRowCount();
         }
         catch(Throwable ex) {
            assertInstanceOf(type, ex);
            return;
         }

         assertTrue(count < 0, "a failed table has no row count: " + count);
         assertTrue(System.currentTimeMillis() < end, "the row count never failed");
         Thread.sleep(10);
      }
   }

   private static void awaitWaiting(Thread[] threads) throws InterruptedException {
      long end = System.currentTimeMillis() + 20000;

      for(int i = 0; i < threads.length; i++) {
         while(threads[i] == null || !isWaiting(threads[i])) {
            assertTrue(System.currentTimeMillis() < end, "the readers never waited");
            Thread.sleep(10);
         }
      }

      // both stay waiting, i.e. it is the wait for the rows
      Thread.sleep(200);

      for(Thread thread : threads) {
         assertTrue(isWaiting(thread), "the reader waits for the rows");
      }
   }

   private static boolean isWaiting(Thread thread) {
      Thread.State state = thread.getState();
      return state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING;
   }

   private static Object[][] data(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "id", "value" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { "k" + r, r };
      }

      return data;
   }

   private static final class Control {
      MergedTable create(Factory factory) throws Exception {
         CountDownLatch gate = this.gate;

         if(gate != null) {
            gate.await();
         }

         int n = creates.incrementAndGet();

         if(failCreate.get() || failOnce && n == 1) {
            throw new IOException("Cannot create the cache temp file of a merged table");
         }

         return factory.create();
      }

      final AtomicBoolean failCreate = new AtomicBoolean();
      final AtomicInteger creates = new AtomicInteger();
      volatile long retryDelay = 60000;
      volatile boolean failOnce;
      volatile CountDownLatch gate;
   }

   private interface Factory {
      MergedTable create() throws Exception;
   }

   private static final class FailingMinus extends MinusTableLens {
      FailingMinus(Object[][] left, Object[][] right) {
         super(new DefaultTableLens(left), new DefaultTableLens(right));
      }

      @Override
      protected MergedTable createMergedTable() throws Exception {
         return control.create(super::createMergedTable);
      }

      @Override
      long getFailureRetryDelay() {
         return control.retryDelay;
      }

      final Control control = new Control();
   }
}
