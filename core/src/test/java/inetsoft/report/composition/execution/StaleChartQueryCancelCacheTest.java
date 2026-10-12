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
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.QueryManager;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.util.ThreadContext;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78033: after rapid selection changes a chart stayed on "No data is available!". A query
 * of the chart that started before the last selection reset cancels the chart's query manager
 * when it reaches {@code VSAQuery.getTableLens}. If that happens after the query that started
 * after the reset submitted its data fetch, the fetch returns no table and no error, and the
 * chart's data was cached as empty until the next selection change.
 *
 * <p>The test runs that order on one viewsheet: a stale read of the chart is held before it
 * takes the sandbox lock for its query, a selection resets the chart, the final read of the
 * chart submits its fetch and is held there, the stale read is released and cancels the query
 * manager, and then the fetch runs. After both reads finish, the chart must have its data.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class StaleChartQueryCancelCacheTest {
   @BeforeEach
   void setUp() {
      oldPrincipal = ThreadContext.getContextPrincipal();
      // the final read must run its fetch, not take the table another test cached
      AssetDataCache.getCache().clear();
   }

   @AfterEach
   void restoreThreadPrincipal() {
      ThreadContext.setContextPrincipal(oldPrincipal);
   }

   /**
    * The stale query cancels the chart's query manager after the final read submitted its
    * fetch. The fetch is cancelled, but the chart must still get the data of the last selection.
    */
   @Test
   void staleCancelAfterTheFetchDoesNotLeaveTheChartWithoutData() throws Exception {
      String state = burst(true);
      assertChartHasData(state);
   }

   /**
    * Control: the same burst with the stale query's cancel after the final read finished.
    */
   @Test
   void staleCancelAfterTheFinalReadLeavesTheData() throws Exception {
      String state = burst(false);
      assertChartHasData(state);
   }

   /**
    * Select a value, start a read of the chart for it and hold the read before its query takes
    * the sandbox lock (so before the query cancels the chart's query manager), select another
    * value, and read the chart's graph for it, holding its data fetch where the fetch first
    * checks whether it was cancelled. Then release the stale read before or after the fetch.
    *
    * @param cancelAfterFetch true to release the stale read, and wait for its cancel, before
    *                         the fetch is released; false to release it after the final read.
    *
    * @return the state of the chart after both reads, for the failure message.
    */
   private String burst(boolean cancelAfterFetch) throws Exception {
      box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      HeldChart chart = chart(vs, source);
      SelectionListVSAssembly selection = selection(vs, source.getSource());

      assertTrue(rows(box.getData(CHART)) > 0, "fixture: the chart has data before the burst");

      HeldQueryManager qmgr = new HeldQueryManager();
      queryManagers(box).put(CHART, qmgr);
      Principal principal = ThreadContext.getContextPrincipal();
      ExecutorService threads = Executors.newFixedThreadPool(2);

      try {
         select(box, selection, "UNNAMED");
         chart.holdThread = true;
         Future<Object> stale = threads.submit(() -> {
            ThreadContext.setContextPrincipal(principal);
            chart.heldThread = Thread.currentThread();
            qmgr.staleThread = Thread.currentThread();
            return box.getData(CHART);
         });
         assertTrue(chart.held.await(WAIT, TimeUnit.SECONDS), "the stale read was not held");

         select(box, selection, "NAMED");

         qmgr.holdFetch = true;
         Future<VGraphPair> last = threads.submit(() -> {
            ThreadContext.setContextPrincipal(principal);
            return box.getVGraphPair(CHART, true, null);
         });
         assertTrue(qmgr.fetchHeld.await(WAIT, TimeUnit.SECONDS), "the final fetch was not held");

         if(cancelAfterFetch) {
            // the cancel is in a later millisecond than the fetch was created
            while(System.currentTimeMillis() <= qmgr.fetchHeldAt) {
               Thread.onSpinWait();
            }

            chart.release.countDown();
            assertTrue(qmgr.staleCancelled.await(WAIT, TimeUnit.SECONDS),
                       "the stale query did not cancel the chart's query manager");
            qmgr.releaseFetch.countDown();
            last.get(WAIT, TimeUnit.SECONDS);
            stale.get(WAIT, TimeUnit.SECONDS);
         }
         else {
            qmgr.releaseFetch.countDown();
            last.get(WAIT, TimeUnit.SECONDS);
            chart.release.countDown();
            assertTrue(qmgr.staleCancelled.await(WAIT, TimeUnit.SECONDS),
                       "the stale query did not cancel the chart's query manager");
            stale.get(WAIT, TimeUnit.SECONDS);
         }

         return "cached data: " + dmap(box).get(CHART, DataMap.NORMAL) +
            ", fetch held at: " + qmgr.fetchHeldAt + ", last cancelled: " + qmgr.lastCancelled();
      }
      finally {
         chart.release.countDown();
         qmgr.releaseFetch.countDown();
         threads.shutdownNow();
         assertTrue(threads.awaitTermination(WAIT, TimeUnit.SECONDS), "a read did not end");
      }
   }

   /**
    * After the burst the chart shows the rows of the last selection.
    */
   private void assertChartHasData(String state) throws Exception {
      Object data = box.getData(CHART);
      VGraphPair pair = box.getVGraphPair(CHART, true, null);
      assertAll(
         () -> assertTrue(rows(data) > 0, "the chart has no data after the burst (" + state + ")"),
         () -> assertNotNull(pair.getRealSizeVGraph(),
                             "the chart is drawn as \"No data\" after the burst (" + state + ")"));
   }

   private static HeldChart chart(Viewsheet vs, SourceInfo source) {
      HeldChart chart = new HeldChart(vs, CHART);
      chart.setSourceInfo(source);
      VSChartInfo info = chart.getVSChartInfo();
      VSChartDimensionRef dim =
         new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "type")));
      dim.setGroupColumnValue("type");
      info.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      info.addYField(agg);
      vs.addAssembly(chart);
      return chart;
   }

   private static SelectionListVSAssembly selection(Viewsheet vs, String table) {
      SelectionListVSAssembly selection = new SelectionListVSAssembly(vs, "Type78033");
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) selection.getInfo();
      info.setTableName(table);
      info.setDataRef(new ColumnRef(new AttributeRef(null, "type")));
      vs.addAssembly(selection);
      return selection;
   }

   /**
    * Select a value in the selection list, as VSSelectionService.applySelection does.
    */
   private static void select(ViewsheetSandbox box, SelectionListVSAssembly selection,
                              String selected) throws Exception
   {
      box.lockWrite();

      try {
         SelectionList list = new SelectionList();
         SelectionValue value = new SelectionValue(selected, selected);
         value.setState(SelectionValue.STATE_SELECTED);
         list.setSelectionValues(new SelectionValue[] { value });
         int hint = selection.setStateSelectionList(list);
         box.processChange(selection.getAbsoluteName(), hint, new ChangedAssemblyList());
      }
      finally {
         box.unlockWrite();
      }
   }

   private static int rows(Object data) {
      if(data == null) {
         return -1;
      }

      TableLens lens = data instanceof VSDataSet ? ((VSDataSet) data).getTable() : (TableLens) data;
      lens.moreRows(Integer.MAX_VALUE);
      return lens.getRowCount() - lens.getHeaderRowCount();
   }

   @SuppressWarnings("unchecked")
   private static Map<String, QueryManager> queryManagers(ViewsheetSandbox box) throws Exception {
      Field field = ViewsheetSandbox.class.getDeclaredField("qmgrs");
      field.setAccessible(true);
      return (Map<String, QueryManager>) field.get(box);
   }

   private static DataMap dmap(ViewsheetSandbox box) throws Exception {
      Field field = ViewsheetSandbox.class.getDeclaredField("dmap");
      field.setAccessible(true);
      return (DataMap) field.get(box);
   }

   private static boolean calledFrom(String className, String method) {
      return StackWalker.getInstance().walk(frames -> frames.anyMatch(
         f -> f.getClassName().equals(className) && f.getMethodName().equals(method)));
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   /**
    * A chart that holds the stale read in {@code ChartVSAQuery.getData}, where the query holds
    * no sandbox lock yet (it looks for a brushing chart first).
    */
   private static final class HeldChart extends ChartVSAssembly {
      HeldChart(Viewsheet vs, String name) {
         super(vs, name);
      }

      @Override
      public VSSelection getBrushSelection() {
         if(holdThread && Thread.currentThread() == heldThread &&
            calledFrom(ChartVSAQuery.class.getName(), "getData"))
         {
            holdThread = false;
            held.countDown();

            try {
               release.await(WAIT, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }

         return super.getBrushSelection();
      }

      private volatile boolean holdThread;
      private volatile Thread heldThread;
      private final CountDownLatch held = new CountDownLatch(1);
      private final CountDownLatch release = new CountDownLatch(1);
   }

   /**
    * The chart's query manager. It holds the final read's data fetch when the fetch first checks
    * whether it was cancelled (the fetch was created by then), and reports the cancel of the
    * stale query.
    */
   private static final class HeldQueryManager extends QueryManager {
      HeldQueryManager() {
         super(true);
      }

      @Override
      public long lastCancelled() {
         if(holdFetch && calledFrom(PROCESSOR, "run")) {
            holdFetch = false;
            fetchHeldAt = System.currentTimeMillis();
            fetchHeld.countDown();

            try {
               releaseFetch.await(WAIT, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }

         return super.lastCancelled();
      }

      @Override
      public int cancel() {
         int count = super.cancel();

         if(Thread.currentThread() == staleThread) {
            staleCancelled.countDown();
         }

         return count;
      }

      private volatile boolean holdFetch;
      private volatile long fetchHeldAt;
      private volatile Thread staleThread;
      private final CountDownLatch fetchHeld = new CountDownLatch(1);
      private final CountDownLatch releaseFetch = new CountDownLatch(1);
      private final CountDownLatch staleCancelled = new CountDownLatch(1);
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   private Principal oldPrincipal;
   private ViewsheetSandbox box;
   private static final String TABLE = "TableView1";
   private static final String CHART = "Chart78033";
   private static final String PROCESSOR = AssetDataCache.class.getName() + "$Processor";
   private static final long WAIT = 30;
}
