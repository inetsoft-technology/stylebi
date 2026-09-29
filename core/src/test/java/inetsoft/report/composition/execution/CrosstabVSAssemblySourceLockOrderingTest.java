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
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

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
    * A crosstab inside an embedded viewsheet resolves a VS assembly source against its parent
    * once, in the fetch step, and the build step names the table by that resolved name rather
    * than resolving it again.
    */
   @Test
   void embeddedCrosstabSourceIsResolvedOnceAgainstItsParent() throws Exception {
      CrosstabVSAssembly embedded = spy(crosstab);
      doReturn("Emb1." + CROSSTAB).when(embedded).getAbsoluteName();
      CrosstabVSAQuery query = new CrosstabVSAQuery(box, CROSSTAB, false) {
         @Override
         protected VSAssembly getAssembly() {
            return embedded;
         }
      };

      VSAQuery.AssemblyTableData data = query.getAssemblyTableData(SOURCE);
      String resolved = Assembly.TABLE_VS_BOUND + "Emb1.Table1";

      assertEquals(SOURCE, data.boundName());
      assertEquals(resolved, data.resolvedName());
      assertEquals(resolved, box.lastFetched, "the source was fetched by another name");

      TableAssembly table = query.buildAssemblyTable(data);

      assertInstanceOf(MirrorTableAssembly.class, table);
      assertEquals(resolved, ((MirrorTableAssembly) table).getTableAssembly().getName());
      assertEquals(1, box.fetches.get(), "building the table must not fetch again");
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

   /**
    * A chart brushed on the same source makes prepare apply its brush condition, which used to
    * refresh the chart under the monitor: {@code setSharedCondition -> box.updateAssembly(chart)
    * -> refreshMetaData(chart)} fetches the source again, the same sandbox-lock release as the
    * crosstab's own fetch. The chart must be refreshed before the monitor as well.
    */
   @Test
   void brushingChartIsRefreshedOutsideTheCrosstabInfoMonitor() throws Exception {
      addBrushingChart();
      Future<TableLens> reader = pool.submit(() -> query().getTableLens());

      TableLens result = await(reader, "crosstab query with a brushing chart");
      // refreshing the chart fetches the source for its meta data (more than once)
      assertTrue(box.fetches.get() > 1, "the brushing chart never fetched the source");
      assertEquals(0, box.fetchesUnderMonitor.get(),
                   "the source assembly was executed while holding the VSCrosstabInfo monitor");
      assertEquals(0, box.updatesUnderMonitor.get(),
                   "the brushing chart was refreshed while holding the VSCrosstabInfo monitor");
      assertNotNull(result, "the crosstab query returned no table");
   }

   /**
    * Mode 2 in the release window of the brushing chart's source fetch.
    */
   @Test
   void writerDoesNotDeadlockWithBrushingChartSourceFetch() throws Exception {
      addBrushingChart();
      runAgainstWriter(false, 2);
   }

   /**
    * Bug #77156: a binding edit switches the source without the sandbox lock
    * ({@code VSAssemblyInfoHandler.apply -> setVSAssemblyInfo}). A crosstab whose source is not
    * a VS assembly when the gather reads it, and is one by the time prepare runs, must not fetch
    * the new source inside the crosstab info monitor: it leaves the monitor and gathers again.
    */
   @Test
   void nonVSToVSSwitchDuringGatherDoesNotFetchUnderMonitor() throws Exception {
      crosstab.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Query1"));
      box.gatherHook = () -> switchSource(SOURCE);
      Future<TableLens> reader = pool.submit(() -> {
         box.reader = Thread.currentThread();
         return query().getTableLens();
      });

      TableLens result = await(reader, "crosstab query with a source switched during the gather");
      assertEquals(0, box.fetchesUnderMonitor.get(),
                   "the switched-to source was executed while holding the VSCrosstabInfo monitor");
      assertEquals(1, box.fetches.get(), "the switched-to source must be fetched once");
      assertNotNull(result, "the crosstab query returned no table after the switch");
   }

   /**
    * Bug #77156 refute: a cube crosstab gathers nothing, so a switch from CUBE to a VS assembly
    * after the first read of the source reached prepare with no gathered sources, which
    * fetched the source (and refreshed any brushing chart) inside the monitor.
    */
   @Test
   void cubeToVSSwitchAfterFirstReadDoesNotFetchUnderMonitor() throws Exception {
      crosstab.setSourceInfo(new SourceInfo(SourceInfo.CUBE, null, "Cube1"));
      HookedQuery query = new HookedQuery();
      query.onCubeDrill = () -> switchSource(SOURCE);
      Future<TableLens> reader = pool.submit(() -> {
         box.reader = Thread.currentThread();
         return query.getTableLens();
      });

      await(reader, "crosstab query with a cube source switched after the first read");
      assertEquals(0, box.fetchesUnderMonitor.get(),
                   "the switched-to source was executed while holding the VSCrosstabInfo monitor");
      assertEquals(0, box.updatesUnderMonitor.get(),
                   "an assembly was refreshed while holding the VSCrosstabInfo monitor");
      assertEquals(1, box.fetches.get(), "the switched-to source must be fetched once");
   }

   /**
    * Bug #77156 deadlock: the source switches from non-VS to VS during the gather, and a thread
    * that holds the sandbox read lock then needs the crosstab info monitor
    * ({@code refreshMetaData -> VSCrosstabInfo.update()}, the shape of the binding apply's own
    * {@code updateAssembly}). If the reader fetched the new source inside the monitor, its
    * write-lock upgrade would wait for that read lock while the other thread waits for the
    * monitor.
    */
   @Test
   void sourceSwitchDoesNotDeadlockWithReadLockHolderInCrosstabInfoUpdate() throws Exception {
      crosstab.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Query1"));
      Started reading = new Started();
      Started other = new Started();
      CountDownLatch otherHoldsRead = new CountDownLatch(1);

      box.gatherHook = () -> {
         switchSource(SOURCE);
         other.future = pool.submit(() -> {
            other.thread = Thread.currentThread();
            box.lockRead();

            try {
               otherHoldsRead.countDown();
               // wait until the reader blocks on its write-lock upgrade for the fetch
               awaitParkedOrDone(reading);
               crosstab.getVSCrosstabInfo().update(crosstab.getViewsheet(), columns, null, true,
                                                   null, null);
            }
            finally {
               box.unlockRead();
            }

            return null;
         });

         assertTrue(otherHoldsRead.await(CAP, TimeUnit.SECONDS),
                    "the other thread never took the read lock");
      };

      Future<TableLens> reader = pool.submit(() -> {
         box.reader = Thread.currentThread();
         reading.thread = Thread.currentThread();
         return query().getTableLens();
      });
      reading.future = reader;

      TableLens result = await(reader, "reader (crosstab prepare after a source switch)");
      assertTrue(other.future != null, "the source was never switched");
      await(other.future, "read-lock holder (VSCrosstabInfo.update)");
      assertEquals(0, box.fetchesUnderMonitor.get(),
                   "the switched-to source was executed while holding the VSCrosstabInfo monitor");
      assertNotNull(result, "the crosstab query returned no table");
   }

   /**
    * Bug #77156: a switch from one VS assembly source to another while prepare runs inside the
    * monitor must not produce a table built from the old source for the new binding; prepare
    * is redone from the new source, fetched outside the monitor.
    */
   @Test
   void sourceSwitchDuringPrepareRebuildsFromTheNewSource() throws Exception {
      HookedQuery query = new HookedQuery();
      AtomicInteger prepares = new AtomicInteger();
      query.onPostSort = () -> {
         if(prepares.incrementAndGet() == 1) {
            switchSource(SOURCE2);
         }
      };
      Future<TableLens> reader = pool.submit(() -> {
         box.reader = Thread.currentThread();
         return query.getTableLens();
      });

      TableLens result = await(reader, "crosstab query with a source switched during prepare");
      assertEquals(0, box.fetchesUnderMonitor.get(),
                   "a source was executed while holding the VSCrosstabInfo monitor");
      assertEquals(SOURCE2, box.lastFetched, "the new source was never fetched");
      assertEquals(2, prepares.get(), "prepare was not redone for the new source");
      Set<String> names = tableNames(query.executed);
      assertTrue(names.contains(SOURCE2), "the executed table is not built from the new source: " +
                 names);
      assertFalse(names.contains(SOURCE), "the executed table is built from the old source: " +
                  names);
      assertEquals(1, crosstab.getVSCrosstabInfo().getRuntimeRowHeaders().length,
                   "the abandoned prepare left its runtime ref rewrite on the crosstab info");
      assertNotNull(result, "the crosstab query returned no table");
   }

   /**
    * Bug #77156: a binding that keeps switching while prepare runs is retried a bounded number
    * of times, then the query answers from its last consistent snapshot instead of throwing or
    * spinning, still without executing anything inside the monitor.
    */
   @Test
   void continuousSourceSwitchingIsRetriedBoundedly() throws Exception {
      HookedQuery query = new HookedQuery();
      AtomicInteger prepares = new AtomicInteger();
      query.onPostSort = () ->
         switchSource(prepares.incrementAndGet() % 2 == 1 ? SOURCE2 : SOURCE);
      Future<TableLens> reader = pool.submit(() -> {
         box.reader = Thread.currentThread();
         return query.getTableLens();
      });

      TableLens result = await(reader, "crosstab query with a continuously switching source");
      assertEquals(0, box.fetchesUnderMonitor.get(),
                   "a source was executed while holding the VSCrosstabInfo monitor");
      assertEquals(3, prepares.get(), "prepare attempts are not bounded at 3");
      assertEquals(3, box.fetches.get(), "each attempt gathers its source once");
      assertEquals(1, query.executions.get(), "the last snapshot was not executed");
      assertNotNull(result, "the crosstab query returned no table after the retries");
   }

   /**
    * Bug #77156: when the retries run out, the query answers from the last attempt's snapshot as
    * a whole: the table is built from that snapshot's source and grouped by that snapshot's
    * crosstab info, never the old source with the new crosstab info (or the reverse), and the
    * edit that landed after the snapshot is left in place, neither reverted nor rewritten.
    */
   @Test
   void exhaustedRetryAnswersFromOneConsistentSnapshot() throws Exception {
      HookedQuery query = new HookedQuery();
      AtomicInteger gathers = new AtomicInteger();
      AtomicInteger prepares = new AtomicInteger();
      AtomicReference<VSCrosstabInfo> lastSnapshotInfo = new AtomicReference<>();
      // every attempt: a binding edit replacing both the source and the crosstab info, as
      // apply() does, between the attempt's snapshot and its monitor
      box.gatherHook = new Window() {
         @Override
         public void run() {
            int n = gathers.incrementAndGet();
            lastSnapshotInfo.set(crosstab.getVSCrosstabInfo());
            // alternate the source type too, so a build from the live source cannot pass
            crosstab.setSourceInfo(n % 2 == 1 ? new SourceInfo(SourceInfo.ASSET, null, QUERY) :
                                   new SourceInfo(SourceInfo.VS_ASSEMBLY, null, SOURCE));
            crosstab.setVSCrosstabInfo(n % 2 == 1 ? crosstabInfo("sales", "state") :
                                       crosstabInfo("state", null));

            if(n < 3) {
               box.gatherHook = this;
            }
         }
      };
      query.onPostSort = prepares::incrementAndGet;
      Future<TableLens> reader = pool.submit(() -> {
         box.reader = Thread.currentThread();
         return query.getTableLens();
      });

      TableLens result = await(reader, "crosstab query with a continuously changing binding");
      assertNotNull(result, "the crosstab query returned no table after the retries");
      assertEquals(3, gathers.get(), "each of the 3 attempts gathers once");
      // attempts 1 and 2 see the change on entering the monitor and skip prepare
      assertEquals(1, prepares.get(), "only the last attempt prepares");
      assertEquals(0, box.fetchesUnderMonitor.get(),
                   "a source was executed while holding the VSCrosstabInfo monitor");
      assertEquals(1, query.executions.get(), "the last snapshot was not executed");

      // the last attempt snapshotted what edit 2 set: SOURCE grouped by state; live is now
      // the worksheet query grouped by sales with a Count(state)
      Set<String> names = tableNames(query.executed);
      assertTrue(names.contains(SOURCE), "the executed table is not built from the snapshot " +
                 "source: " + names);
      assertFalse(names.contains(QUERY), "the executed table is built from the live source, " +
                  "not the snapshot: " + names);
      Set<String> built = new java.util.HashSet<>();
      ColumnSelection selection = query.executed.getColumnSelection(true);

      for(int i = 0; i < selection.getAttributeCount(); i++) {
         built.add(selection.getAttribute(i).getAttribute());
      }

      assertTrue(built.contains("state"), "the executed table is not built for the " +
                 "snapshot crosstab info: " + built);
      assertFalse(built.contains("sales"), "the executed table is built for the live " +
                  "crosstab info, not the snapshot: " + built);

      // the edit made after the snapshot is left alone
      assertEquals(new SourceInfo(SourceInfo.ASSET, null, QUERY), crosstab.getSourceInfo(),
                   "the query reverted the source edit made after its last snapshot");
      VSCrosstabInfo live = crosstab.getVSCrosstabInfo();
      assertNotSame(lastSnapshotInfo.get(), live, "crosstab info edit");
      assertEquals(1, live.getRuntimeRowHeaders().length, "live row headers");
      assertEquals("sales", live.getRuntimeRowHeaders()[0].getAttribute(),
                   "the query rewrote the runtime refs of the crosstab info it did not prepare");
      assertEquals(1, live.getRuntimeAggregates().length,
                   "the query restored another crosstab info's runtime aggregates on the live one");
      // and the snapshot's rewrite was undone
      assertEquals(1, lastSnapshotInfo.get().getRuntimeRowHeaders().length,
                   "the prepared crosstab info kept its runtime ref rewrite");
   }

   /**
    * A crosstab info as a binding edit creates it, grouped by a row header, with a Count
    * aggregate of another column or none, runtime refs filled in.
    */
   private VSCrosstabInfo crosstabInfo(String row, String count) {
      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      VSDimensionRef dim = new VSDimensionRef(new ColumnRef(new AttributeRef(null, row)));
      dim.setGroupColumnValue(row);
      cinfo.setDesignRowHeaders(new DataRef[] { dim });

      if(count != null) {
         VSAggregateRef agg = new VSAggregateRef();
         agg.setColumnValue(count);
         agg.setFormulaValue("Count");
         cinfo.setDesignAggregates(new DataRef[] { agg });
      }

      cinfo.update(crosstab.getViewsheet(), columns, null, true, null, null);
      return cinfo;
   }

   /**
    * Bug #77156: a binding edit that lands after prepare, while the base table runs outside the
    * monitor, must not be reverted by the query restoring the source info it cloned in prepare.
    */
   @Test
   void queryDoesNotRevertAConcurrentSourceSwitch() throws Exception {
      HookedQuery query = new HookedQuery();
      query.onExecute = () -> switchSource(SOURCE2);
      Future<TableLens> reader = pool.submit(() -> {
         box.reader = Thread.currentThread();
         return query.getTableLens();
      });

      await(reader, "crosstab query with a source switched during execution");
      assertEquals(SOURCE2, crosstab.getSourceInfo().getSource(),
                   "the query reverted the concurrent source switch");
   }

   /**
    * What a binding edit does to the source: replace the live SourceInfo, with no sandbox lock.
    */
   private void switchSource(String source) {
      crosstab.setSourceInfo(new SourceInfo(SourceInfo.VS_ASSEMBLY, null, source));
   }

   private static Set<String> tableNames(TableAssembly table) {
      Set<String> names = new java.util.HashSet<>();
      collectTableNames(table, names);
      return names;
   }

   private static void collectTableNames(TableAssembly table, Set<String> names) {
      if(table == null) {
         return;
      }

      names.add(table.getName());

      if(table instanceof ComposedTableAssembly composed) {
         for(TableAssembly child : composed.getTableAssemblies(false)) {
            collectTableNames(child, names);
         }
      }
   }

   private void addBrushingChart() {
      Viewsheet vs = crosstab.getViewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.setSourceInfo(new SourceInfo(SourceInfo.VS_ASSEMBLY, null, SOURCE));
      VSPoint point = new VSPoint();
      point.addValue(new VSFieldValue("state", "NJ"));
      VSSelection selection = new VSSelection();
      selection.addPoint(point);
      vs.addAssembly(chart);
      // after adding it: adding validates the selection against the (empty) chart binding
      chart.setBrushSelection(selection);
      assertSame(chart, box.getBrushingChart(CROSSTAB), "brushing chart");
   }

   private void runAgainstWriter(boolean writerMisses) throws Exception {
      runAgainstWriter(writerMisses, 1);
   }

   /**
    * @param windowAt the reader's source fetch, counted from 1, in whose release window the
    *                 writer runs.
    */
   private void runAgainstWriter(boolean writerMisses, int windowAt) throws Exception {
      CountDownLatch inWindow = new CountDownLatch(1);
      Started writer = new Started();
      AtomicInteger readerFetches = new AtomicInteger();

      box.readerWindow = () -> {
         if(readerFetches.incrementAndGet() != windowAt) {
            return;
         }

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

   /**
    * {@link #query()} with hooks at points of the query that bracket the phases a binding edit
    * can land in: before the gather ({@code isCubeDrill}), inside prepare ({@code isPostSort},
    * under the monitor) and during the base table execution (outside it).
    */
   private final class HookedQuery extends CrosstabVSAQuery {
      HookedQuery() {
         super(CrosstabVSAssemblySourceLockOrderingTest.this.box, CROSSTAB, false);
      }

      @Override
      protected boolean isCubeDrill() {
         Runnable hook = onCubeDrill;
         onCubeDrill = null;

         if(hook != null) {
            hook.run();
         }

         return super.isCubeDrill();
      }

      @Override
      protected boolean isPostSort() {
         if(onPostSort != null) {
            onPostSort.run();
         }

         return super.isPostSort();
      }

      @Override
      protected TableLens getTableLens(TableAssembly table) {
         executions.incrementAndGet();
         executed = table;

         if(onExecute != null) {
            onExecute.run();
         }

         return sourceRows();
      }

      volatile Runnable onCubeDrill;
      volatile Runnable onPostSort;
      volatile Runnable onExecute;
      volatile TableAssembly executed;
      final AtomicInteger executions = new AtomicInteger();
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
         // an entry, which executing a chart's dynamic values needs
         super(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false,
               new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                              "test/" + CROSSTAB, null));
      }

      /**
       * Refreshing an assembly runs its dynamic values, which take the script engine lock
       * (bug #77030 refute, A3), and its meta data, which can fetch its source.
       */
      @Override
      void updateAssembly(VSAssembly assembly, boolean out) throws Exception {
         if(Thread.holdsLock(crosstab.getVSCrosstabInfo())) {
            updatesUnderMonitor.incrementAndGet();
         }

         super.updateAssembly(assembly, out);
      }

      /**
       * The gather resolves the brushing chart after reading the source type; a hook here
       * lands a binding edit between the gather's read and prepare (bug #77156).
       */
      @Override
      public ChartVSAssembly getBrushingChart(String vname) {
         Window hook = gatherHook;

         if(hook != null && Thread.currentThread() == reader) {
            gatherHook = null;

            try {
               hook.run();
            }
            catch(Exception ex) {
               throw new RuntimeException(ex);
            }
         }

         return super.getBrushingChart(vname);
      }

      @Override
      public TableLens getTableData(String name) throws Exception {
         fetches.incrementAndGet();
         lastFetched = name;

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
      final AtomicInteger updatesUnderMonitor = new AtomicInteger();
      volatile Thread reader;
      volatile String lastFetched;
      volatile Window readerWindow;
      volatile Window gatherHook;
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
   private static final String SOURCE2 = Assembly.TABLE_VS_BOUND + "Table2";
   private static final String QUERY = "Query1";
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
