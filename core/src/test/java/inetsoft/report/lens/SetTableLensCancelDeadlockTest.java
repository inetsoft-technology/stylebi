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
import inetsoft.report.composition.execution.TableFilter2;
import inetsoft.report.filter.ColumnMapFilter;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.internal.table.MergedRow;
import inetsoft.report.internal.table.MergedTable;
import inetsoft.test.*;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.LendableReentrantLock;
import inetsoft.util.stall.StallPolicy;
import inetsoft.util.stall.StallTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * invalidate(), dispose() or cancel() of a minus table lens whose merge is traversing its
 * merged table returns, and the lens then ends: the merge holds the btree monitor for the
 * whole traversal and takes the merged table's monitor per key, so closing the btree while
 * holding the merged table's (or the lens's) monitor deadlocked (bug #77397). The cancel is
 * what the worksheet Stop button and closing a worksheet do.
 *
 * <p>Every action runs on a daemon thread with a bounded join, and a deadlock is reported by
 * the thread MX bean, so a regression fails instead of hanging the build.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class SetTableLensCancelDeadlockTest {
   @BeforeEach
   public void setUp() {
      StallTestSupport.resetGlobalStallState();
      // the production policy: a wait fails only once a cycle is confirmed
      StallPolicy.setOverride(
         new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir, 20, false));
      deadlockedBefore = deadlocked();
   }

   @AfterEach
   public void tearDown() {
      StallTestSupport.clearOverride();
   }

   @Test
   public void invalidateDuringMergeReturns() throws Exception {
      GatedMinus lens = parkedMinus();
      runBlockedOnMerge(lens, lens::invalidate, "invalidate()");

      assertFalse(bounded(() -> lens.moreRows(TableLens.EOT)));
      List<List<Object>> expected = drain(freshMinus());
      assertEquals(expected.size(), (int) bounded(lens::getRowCount));
      awaitIdle();
      assertEquals(expected, drain(lens));
   }

   @Test
   public void disposeDuringMergeReturns() throws Exception {
      GatedMinus lens = parkedMinus();
      runBlockedOnMerge(lens, lens::dispose, "dispose()");

      assertFalse(bounded(() -> lens.moreRows(TableLens.EOT)));
      // an empty complete table, not "still loading"
      assertEquals(0, (int) bounded(lens::getRowCount));
      awaitIdle();
   }

   /**
    * A cancelled merge completes the rows found so far: a truncated table that reports itself
    * complete, and cancelled.
    */
   @Test
   public void cancelDuringMergeReturns() throws Exception {
      GatedMinus lens = parkedMinus();
      runBlockedOnMerge(lens, lens::cancel, "cancel()");

      assertCancelledPrefix(lens);
   }

   /**
    * The cancel reaches the lens through a filter, as a worksheet's cancel does.
    */
   @Test
   public void filterCancelDuringMergeReturns() throws Exception {
      GatedMinus lens = parkedMinus();
      ColumnMapFilter filter = new ColumnMapFilter(lens, new int[] { 0, 1 });
      runBlockedOnMerge(lens, filter::cancel, "ColumnMapFilter.cancel()");

      assertCancelledPrefix(lens);
   }

   /**
    * The worksheet Stop button (WSQueryService.stopQuery) cancels the outermost cancellable
    * table of the sandbox's table, a TableFilter2 over the query's filters, which forwards
    * the cancel down to the lens.
    */
   @Test
   public void worksheetStopCancelDuringMergeReturns() throws Exception {
      GatedMinus lens = parkedMinus();
      TableLens top = new TableFilter2(new ColumnMapFilter(lens, new int[] { 0, 1 }));
      CancellableTableLens cancelTable = (CancellableTableLens) Util.getNestedTable(
         top, CancellableTableLens.class);
      assertInstanceOf(TableFilter2.class, cancelTable);
      runBlockedOnMerge(lens, cancelTable::cancel, "TableFilter2.cancel()");

      assertCancelledPrefix(lens);
      assertTrue(cancelTable.isCancelled());
   }

   /**
    * A reader holding a script lock merges on its own thread, inside the lens's monitor. A
    * cancel from another thread returns, and so does the reader.
    */
   @Test
   public void cancelDuringInExecMergeReturns() throws Exception {
      GatedMinus lens = new GatedMinus(GATE_VISIT);
      LendableReentrantLock lock = new LendableReentrantLock();
      CompletableFuture<Boolean> result = new CompletableFuture<>();
      Thread reader = daemon(() -> {
         lock.lock();
         JavaScriptEngine.pushHeldScriptLock(lock);

         try {
            result.complete(lens.moreRows(TableLens.EOT));
         }
         catch(Throwable ex) {
            result.completeExceptionally(ex);
         }
         finally {
            JavaScriptEngine.popHeldScriptLock();
            lock.unlock();
         }
      }, "reader");

      assertSame(reader, lens.awaitParked(), "the reader merges on its own thread");
      runBlockedOnMerge(lens, lens::cancel, "cancel()");

      reader.join(JOIN_MS);
      assertFalse(reader.isAlive(), "the merging reader did not return");
      assertNoNewDeadlock();
      assertFalse(result.get(CAP_SECONDS, TimeUnit.SECONDS));
      assertCancelledPrefix(lens);
   }

   /**
    * A merge superseded by invalidate() while it traverses adds none of its rows to the rows
    * of the next pass, which may run to the end while the superseded merge is still parked.
    */
   @Test
   public void supersededMergeDoesNotAddToNextPass() throws Exception {
      GatedMinus lens = parkedMinus();
      Thread invalidator = daemon(lens::invalidate, "invalidate()");

      try {
         // invalidate() waits for the btree the parked merge holds
         awaitTrue(() -> invalidator.getState() == Thread.State.BLOCKED || !invalidator.isAlive(),
                   "invalidate() blocks on the merge");
         List<List<Object>> expected = drain(freshMinus());
         // the next pass, while the superseded merge is parked
         assertFalse(bounded(() -> lens.moreRows(TableLens.EOT)), "the next pass ends");
         assertEquals(expected.size(), (int) bounded(lens::getRowCount));
      }
      finally {
         lens.open();
      }

      invalidator.join(JOIN_MS);
      assertFalse(invalidator.isAlive(), "invalidate() did not return");
      assertNoNewDeadlock();
      awaitIdle();

      List<List<Object>> expected = drain(freshMinus());
      List<List<Object>> actual = drain(lens);
      assertEquals(expected.size(), lens.getRowCount());
      assertEquals(new HashSet<>(actual).size(), actual.size(), "duplicate rows");
      assertEquals(expected, actual);
   }

   /**
    * Start a merge of a minus lens and wait until it is parked in its traversal.
    */
   private GatedMinus parkedMinus() throws Exception {
      GatedMinus lens = new GatedMinus(GATE_VISIT);
      // the first read starts the merge in the background, and waits for its first row
      Thread reader = daemon(() -> lens.moreRows(1), "first reader");
      Thread worker = lens.awaitParked();
      assertNotSame(reader, worker, "the merge runs in the background");
      return lens;
   }

   /**
    * Run the action on a daemon thread, which blocks until the parked merge goes on, let the
    * merge go on and check that the action returns.
    */
   private void runBlockedOnMerge(GatedMinus lens, Runnable action, String name)
      throws Exception
   {
      Thread thread = daemon(action, name);

      try {
         // the action waits for the btree the parked merge holds
         awaitTrue(() -> thread.getState() == Thread.State.BLOCKED || !thread.isAlive(),
                   name + " blocks on the merge");
      }
      finally {
         lens.open();
      }

      thread.join(JOIN_MS);
      assertFalse(thread.isAlive(), () -> name + " did not return: " + stack(thread));
      assertNoNewDeadlock();
   }

   private static void assertCancelledPrefix(SetTableLens lens) throws Exception {
      assertFalse(bounded(() -> lens.moreRows(TableLens.EOT)), "a cancelled lens ends");
      assertTrue(lens.isCancelled());
      int count = bounded(lens::getRowCount);
      assertTrue(count >= 1, "complete, with the header: " + count);
      awaitIdle();

      List<List<Object>> rows = drain(lens);
      List<List<Object>> fresh = drain(freshMinus());
      assertEquals(count, rows.size());
      assertTrue(rows.size() <= fresh.size() && fresh.subList(0, rows.size()).equals(rows),
                 "rows=" + rows.size() + ", fresh=" + fresh.size());
   }

   private void assertNoNewDeadlock() {
      Set<Long> now = deadlocked();
      now.removeAll(deadlockedBefore);

      if(!now.isEmpty()) {
         StringBuilder dump = new StringBuilder("deadlocked threads:");

         for(ThreadInfo info : ManagementFactory.getThreadMXBean()
            .getThreadInfo(now.stream().mapToLong(Long::longValue).toArray(), true, true))
         {
            if(info != null) {
               dump.append("\n").append(info);
            }
         }

         fail(dump.toString());
      }
   }

   private static Set<Long> deadlocked() {
      long[] ids = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
      Set<Long> set = new HashSet<>();

      if(ids != null) {
         for(long id : ids) {
            set.add(id);
         }
      }

      return set;
   }

   private static <T> T bounded(Callable<T> call) throws Exception {
      FutureTask<T> task = new FutureTask<>(call);
      Thread thread = daemon(task, "bounded");

      try {
         return task.get(CAP_SECONDS, TimeUnit.SECONDS);
      }
      catch(TimeoutException ex) {
         fail("did not return: " + stack(thread));
         return null;
      }
   }

   private static Thread daemon(Runnable run, String name) {
      Thread thread = new Thread(run, "SetTableLensCancelDeadlockTest-" + name);
      thread.setDaemon(true);
      thread.start();
      return thread;
   }

   private static String stack(Thread thread) {
      return thread + " " + thread.getState() + "\n  " +
         Arrays.toString(thread.getStackTrace()).replace(", ", "\n  ");
   }

   /**
    * Wait until no thread but this one runs code of the lens, i.e. every merge is done.
    */
   private static void awaitIdle() throws InterruptedException {
      long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(CAP_SECONDS);

      while(true) {
         Optional<Thread> running = Thread.getAllStackTraces().keySet().stream()
            .filter(t -> t != Thread.currentThread() && isInMerge(t))
            .findFirst();

         if(running.isEmpty()) {
            break;
         }

         if(System.currentTimeMillis() >= deadline) {
            fail("a merge is still running: " + stack(running.get()));
         }

         Thread.sleep(20);
      }
   }

   private static boolean isInMerge(Thread thread) {
      for(StackTraceElement element : thread.getStackTrace()) {
         // a pool thread appends the stack that created it
         if(element.getClassName().equals(Thread.class.getName()) &&
            element.getMethodName().equals("getStackTrace"))
         {
            break;
         }

         if(element.getClassName().startsWith(SetTableLens.class.getName()) ||
            element.getClassName().startsWith(MergedTable.class.getName()))
         {
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

   private static MinusTableLens freshMinus() {
      return new MinusTableLens(new DefaultTableLens(data(LEFT_ROWS)),
                                new DefaultTableLens(data(RIGHT_ROWS)));
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
    * A minus lens whose merge parks at the given merged row of its first traversal, holding
    * the btree monitor as a merge does between two keys, until {@link #open()}.
    */
   private static class GatedMinus extends MinusTableLens {
      GatedMinus(int gateVisit) {
         super(new DefaultTableLens(data(LEFT_ROWS)), new DefaultTableLens(data(RIGHT_ROWS)));
         this.gateVisit = gateVisit;
      }

      @Override
      protected MergedTable createMergedTable() throws Exception {
         return new MergedTable() {
            @Override
            protected MergedRow createMergedRow() {
               // adding a table creates its rows inside this monitor, the traversal outside
               if(!Thread.holdsLock(this) && visits.incrementAndGet() == gateVisit) {
                  parked.complete(Thread.currentThread());

                  try {
                     gate.await(CAP_SECONDS, TimeUnit.SECONDS);
                  }
                  catch(InterruptedException ex) {
                     Thread.currentThread().interrupt();
                  }
               }

               return super.createMergedRow();
            }
         };
      }

      Thread awaitParked() throws Exception {
         return parked.get(CAP_SECONDS, TimeUnit.SECONDS);
      }

      void open() {
         gate.countDown();
      }

      private final int gateVisit;
      private final AtomicInteger visits = new AtomicInteger();
      private final CountDownLatch gate = new CountDownLatch(1);
      private final CompletableFuture<Thread> parked = new CompletableFuture<>();
   }

   @TempDir
   File dumpDir;
   private Set<Long> deadlockedBefore;

   private static final int LEFT_ROWS = 2000;
   private static final int RIGHT_ROWS = 100;
   private static final int GATE_VISIT = 500;
   private static final long JOIN_MS = 5000;
   private static final long CAP_SECONDS = 10;
}
