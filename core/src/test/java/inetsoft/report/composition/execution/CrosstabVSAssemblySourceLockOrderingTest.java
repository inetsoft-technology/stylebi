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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.uql.ColumnSelection;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for bug #77030: a crosstab bound to another VS assembly
 * ({@link SourceInfo#VS_ASSEMBLY}) executed that assembly from its prepare phase, inside
 * {@code synchronized(cinfo)} in {@code AbstractCrosstabVSAQuery.getTableLens0()}. Executing it
 * releases the sandbox lock ({@code doExecuteData()} upgrades to the write lock, then
 * {@code unlockAll()} / {@code restoreLocks()} around the source query), so a writer that took
 * the write lock in that window and then needed the crosstab info monitor
 * ({@code doExecuteData(crosstab) -> updateAssembly -> refreshMetaData -> VSCrosstabInfo.update()})
 * deadlocked against the reader parked in the read restore.
 *
 * <p>The reader drives the real {@link CrosstabVSAQuery#getTableLens()} path on a real
 * {@link ViewsheetSandbox} lock, down to {@code VSAQuery.createAssemblyTable()}. Two steps that
 * need a deployed server are replaced. The source fetch, {@link ViewsheetSandbox#getTableData},
 * replays the lock sequence of a data cache miss ({@code getData} {@code lockRead},
 * {@code doExecuteData} {@code lockWrite} / {@code unlockWrite}, {@code unlockAll}, the source
 * query, {@code restoreLocks}) with the real lock. The execution of the prepared base table, which
 * runs outside the monitor since #76549, answers with the source rows. The writer runs the
 * real {@link VSCrosstabInfo#update} under the real write lock, in the two orderings that close the
 * cycle (bug #77030 refute, A1):
 * <ul>
 *    <li>mode 2: the writer finds the source cached and never releases the write lock;</li>
 *    <li>mode 0: the writer misses the source itself, releases at its own upgrade, runs its source
 *        query and restores the write lock before the reader restores.</li>
 * </ul>
 * All threads are daemons and all waits are bounded, so a regression fails with a thread dump
 * instead of hanging the build.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CrosstabVSAssemblySourceLockOrderingTest {
   @BeforeEach
   void setUp() throws Exception {
      Viewsheet vs = new Viewsheet();

      // Viewsheet.setBaseWorksheet() is private and only reachable through a full update()
      // against an asset repository, so wire the base worksheet directly -- same approach as
      // ViewsheetSandboxProcessOnInitTest.
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, new Worksheet());

      crosstab = new CrosstabVSAssembly(vs, CROSSTAB);
      crosstab.setSourceInfo(new SourceInfo(SourceInfo.VS_ASSEMBLY, null, SOURCE));
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      VSDimensionRef dim = new VSDimensionRef(new ColumnRef(new AttributeRef(null, "state")));
      dim.setGroupColumnValue("state");
      cinfo.setDesignRowHeaders(new DataRef[] { dim });
      vs.addAssembly(crosstab);

      // what refreshMetaData() does before a query: fill in the runtime refs
      columns = new ColumnSelection();
      columns.addAttribute(new ColumnRef(new AttributeRef(null, "state")));
      columns.addAttribute(new ColumnRef(new AttributeRef(null, "sales")));
      cinfo.update(vs, columns, null, true, null, null);
      assertEquals(1, cinfo.getRuntimeRowHeaders().length, "runtime row headers");

      box = new FetchingBox(vs);
      pool = Executors.newCachedThreadPool(r -> {
         Thread thread = new Thread(r, "b77030-" + SEQ.incrementAndGet());
         thread.setDaemon(true);
         threads.add(thread.getId());
         return thread;
      });
   }

   @AfterEach
   void tearDown() {
      pool.shutdownNow();
   }

   /**
    * Without a concurrent writer: the source is fetched exactly once, outside the crosstab info
    * monitor, and prepare still builds the crosstab table from it.
    */
   @Test
   void sourceIsFetchedOnceOutsideTheCrosstabInfoMonitor() throws Exception {
      Future<TableLens> reader = pool.submit(() -> query().getTableLens());

      TableLens result = await(reader, "crosstab query");
      assertEquals(1, box.fetches.get(), "the source assembly must be fetched exactly once");
      assertEquals(0, box.fetchesUnderMonitor.get(),
                   "the source assembly was executed while holding the VSCrosstabInfo monitor");
      assertEquals(1, executions.get(), "the prepared crosstab base table was not executed");
      assertEquals(0, executionsUnderMonitor.get(),
                   "the crosstab base table was executed while holding the VSCrosstabInfo monitor");
      assertNotNull(result, "the crosstab query returned no table");
   }

   /**
    * Mode 2: the writer finds the source cached, so it holds the write lock from its
    * {@code lockWrite()} straight to {@code VSCrosstabInfo.update()}.
    */
   @Test
   void writerHoldingWriteLockDoesNotDeadlockWithVSAssemblySourceFetch() throws Exception {
      runAgainstWriter(false);
   }

   /**
    * Mode 0: the writer misses the source itself, releases the write lock at its own upgrade,
    * finishes its source query and restores the write lock before the reader restores, then
    * needs the crosstab info monitor.
    */
   @Test
   void writerMissingSourceItselfDoesNotDeadlockWithVSAssemblySourceFetch() throws Exception {
      runAgainstWriter(true);
   }

   private void runAgainstWriter(boolean writerMisses) throws Exception {
      CountDownLatch inWindow = new CountDownLatch(1);
      Started writer = new Started();

      box.readerWindow = () -> {
         inWindow.countDown();
         // let the writer take the write lock in the release window and reach
         // VSCrosstabInfo.update() before this thread restores its locks
         awaitParkedOrDone(writer);
      };

      Future<TableLens> reader = pool.submit(() -> {
         box.reader = Thread.currentThread();
         return query().getTableLens();
      });

      assertTrue(inWindow.await(CAP, TimeUnit.SECONDS),
                 "the reader never reached the source fetch");

      writer.future = pool.submit(() -> {
         writer.thread = Thread.currentThread();
         box.lockWrite();

         try {
            if(writerMisses) {
               // refreshMetaData() -> getDefaultColumnSelection() fetches the source first
               box.getTableData(SOURCE);
            }

            crosstab.getVSCrosstabInfo().update(crosstab.getViewsheet(), columns, null, true,
                                                null, null);
         }
         finally {
            box.unlockWrite();
         }

         return null;
      });

      await(writer.future, "writer (lockWrite -> VSCrosstabInfo.update)");
      TableLens result = await(reader, "reader (crosstab prepare with a VS assembly source)");
      assertEquals(0, box.fetchesUnderMonitor.get(),
                   "the source assembly was executed while holding the VSCrosstabInfo monitor");
      assertNotNull(result, "the crosstab query returned no table");
   }

   /**
    * The real crosstab query, except that the prepared base table is not run through the asset
    * data cache (which needs a deployed server). That step is outside the monitor since #76549
    * and is not part of this cycle; it answers with the source rows.
    */
   private CrosstabVSAQuery query() {
      return new CrosstabVSAQuery(box, CROSSTAB, false) {
         @Override
         protected TableLens getTableLens(TableAssembly table) {
            executions.incrementAndGet();

            if(Thread.holdsLock(crosstab.getVSCrosstabInfo())) {
               executionsUnderMonitor.incrementAndGet();
            }

            return sourceRows();
         }
      };
   }

   private static TableLens sourceRows() {
      return new DefaultTableLens(new Object[][] {
         { "state", "sales" },
         { "NJ", 1 },
         { "NY", 2 }
      });
   }

   private static void awaitParkedOrDone(Started started) throws InterruptedException {
      long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(CAP);

      while(System.currentTimeMillis() < deadline) {
         Future<?> future = started.future;
         Thread thread = started.thread;

         if(future != null && future.isDone()) {
            return;
         }

         if(thread != null) {
            Thread.State state = thread.getState();

            if(state == Thread.State.BLOCKED || state == Thread.State.WAITING) {
               return;
            }
         }

         Thread.sleep(2);
      }
   }

   private <T> T await(Future<T> future, String what) throws Exception {
      try {
         return future.get(CAP, TimeUnit.SECONDS);
      }
      catch(TimeoutException ex) {
         fail(what + " did not finish within " + CAP + " s (lock cycle?)\n" + dump());
         return null;
      }
      catch(ExecutionException ex) {
         if(ex.getCause() instanceof Exception) {
            throw (Exception) ex.getCause();
         }

         throw ex;
      }
   }

   /**
    * Dump the threads of this case only; threads left deadlocked by an earlier case are not.
    */
   private String dump() {
      StringBuilder sb = new StringBuilder();

      for(ThreadInfo info : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
         if(threads.contains(info.getThreadId())) {
            sb.append(info);
         }
      }

      return sb.toString();
   }

   /**
    * A sandbox whose source fetch replays the lock sequence of a data cache miss with the real
    * lock, and records whether it was entered while holding the crosstab info monitor.
    */
   private final class FetchingBox extends ViewsheetSandbox {
      FetchingBox(Viewsheet vs) {
         super(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, null);
      }

      @Override
      public TableLens getTableData(String name) throws Exception {
         fetches.incrementAndGet();

         if(Thread.holdsLock(crosstab.getVSCrosstabInfo())) {
            fetchesUnderMonitor.incrementAndGet();
         }

         lockRead(); // getData() on a miss

         try {
            lockWrite(); // doExecuteData() upgrades, updates the source assembly
            unlockWrite();
            unlockAll(); // and releases around the source query

            try {
               if(Thread.currentThread() == reader && readerWindow != null) {
                  readerWindow.run();
               }
            }
            finally {
               restoreLocks();
            }
         }
         finally {
            unlockRead();
         }

         return sourceRows();
      }

      final AtomicInteger fetches = new AtomicInteger();
      final AtomicInteger fetchesUnderMonitor = new AtomicInteger();
      volatile Thread reader;
      volatile Window readerWindow;
   }

   @FunctionalInterface
   private interface Window {
      void run() throws Exception;
   }

   private static final class Started {
      volatile Future<?> future;
      volatile Thread thread;
   }

   private static final String CROSSTAB = "Crosstab1";
   private static final String SOURCE = Assembly.TABLE_VS_BOUND + "Table1";
   private static final long CAP = 10;
   private static final AtomicInteger SEQ = new AtomicInteger();

   private final AtomicInteger executions = new AtomicInteger();
   private final AtomicInteger executionsUnderMonitor = new AtomicInteger();
   private CrosstabVSAssembly crosstab;
   private ColumnSelection columns;
   private FetchingBox box;
   private ExecutorService pool;
   private final Set<Long> threads = ConcurrentHashMap.newKeySet();
}
