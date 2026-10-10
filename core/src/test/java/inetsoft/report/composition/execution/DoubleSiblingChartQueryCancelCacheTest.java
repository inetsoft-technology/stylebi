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
import inetsoft.report.filter.SummaryFilter;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.QueryManager;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78033 (reopened), Mechanism A ("retry-survives-into-VGraphPair", see
 * docs/teams/2026-10-10-bug-78033-brush-nodata/01-hypothesis-vgraphpair.md and
 * 02-root-cause.md). {@code SiblingChartQueryCancelCacheTest} already proves that the chart's
 * one #78033 retry recovers from a *single* cancel of the chart's query manager by a sibling
 * query. This test proves that recovery doesn't repeat: when the retry *itself* is cut short
 * by a second sibling query of the same chart's query manager, {@code ViewsheetSandbox.getData()}
 * has no third attempt. It correctly refuses to cache the twice-cut result (cache=false), but
 * its return statement is unconditional, so the twice-cut data still reaches
 * {@code VGraphPair.initGraph0()}, which bakes it into a {@code VGraphPair} that
 * {@code initGraph()}'s {@code finally} marks completed regardless, and
 * {@code ViewsheetSandbox.getVGraphPair()} never rebuilds a completed, non-cancelled pair with
 * unchanged chart size/info. So the chart is left on "No data is available!" permanently,
 * because nothing ever calls {@code clearGraph} for it again.
 *
 * <p>The race is driven through {@code box.getVGraphPair(CHART, true, null)} directly (not a
 * bare {@code box.getData(...)} call), because that is the exact call whose own internal
 * {@code box.getData(cname, true, DataMap.ZOOM)} (the chart's all-data/"ZOOM" sub-query,
 * {@code VGraphPair.java} line ~252) must be the one that gets cut twice: a later, separate
 * {@code getVGraphPair()} call would simply re-fetch cleanly once the race is over, which would
 * not reproduce the permanently-stuck pair the reopened bug reports.</p>
 *
 * <p>The held row (row 10 of the embedded table) is excluded from the brush condition's
 * ("type" = "NAMED") *output*, the same row {@code SiblingChartQueryCancelCacheTest} holds on —
 * but a brushed query's own condition filter still scans every row of the base table to decide
 * pass/fail, so its own {@code SummaryFilter}/{@code SortFilter} pass touches row 10 too. Left
 * alone, the chart's own first internal call ({@code box.getData(cname)}, the brushed/NORMAL
 * sub-query, {@code VGraphPair.java} line ~240, which {@code initGraph0} makes *before* its
 * ZOOM/all-data call at line ~252) would consume this test's first hold itself, instead of the
 * ZOOM call this test means to target. To avoid that, a NORMAL/brushed entry is pre-warmed into
 * {@code dmap} right before the race (see {@code burst()}), so the subject's own first internal
 * call is a cache hit — it never touches the embedded table at all — and only its second (ZOOM)
 * call is a cache miss that actually reads row 10. Each sibling query's own NORMAL read would
 * otherwise re-populate that same cache entry too, so {@code dmap}'s NORMAL entry for the chart
 * is invalidated again right before each sibling runs.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class DoubleSiblingChartQueryCancelCacheTest {
   @BeforeEach
   void setUp() {
      oldPrincipal = ThreadContext.getContextPrincipal();
      // the reads must run their fetches, not take the tables another test cached
      AssetDataCache.getCache().clear();
   }

   @AfterEach
   void restoreThreadPrincipal() {
      ThreadContext.setContextPrincipal(oldPrincipal);
   }

   /**
    * The chart's existing #78033 retry (triggered by a first sibling cancel) is itself cut by a
    * second, independent sibling query of the same chart's query manager. The resulting
    * VGraphPair must not be left showing "No data".
    */
   @Test
   void secondSiblingCancelDuringTheRetryDoesNotLeaveTheChartWithoutData() throws Exception {
      String state = burst();
      assertChartHasAllData(state);
   }

   /**
    * Brush the chart, start building its VGraphPair, and hold the table read for its all-data
    * ("ZOOM") sub-query at row 10. Cancel the chart's query manager with a first sibling
    * {@code cancelForQuery()}, which cuts that first attempt short and makes
    * {@code ViewsheetSandbox.getData()} retry once (the existing #78033 fix). Hold the retry's
    * own table read at row 10 again, and cancel the query manager a second time, with a second,
    * independent sibling {@code cancelForQuery()}, while the retry is still being read. Release
    * both holds and let the VGraphPair build finish.
    *
    * @return the state after the race, for the failure message.
    */
   private String burst() throws Exception {
      box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      Worksheet ws = box.getWorksheet();
      Assembly bound = ws.getAssembly(source.getSource());

      while(bound instanceof MirrorTableAssembly mirror) {
         bound = ws.getAssembly(mirror.getAssemblyName());
      }

      EmbeddedTableAssembly table = (EmbeddedTableAssembly) bound;
      data = new HeldData(table.getEmbeddedData());
      table.setEmbeddedData(data);

      chart(vs, BRUSH, source);
      chart(vs, CHART, source);
      VSSelection brush = new VSSelection();
      VSPoint point = new VSPoint();
      point.addValue(new VSFieldValue("type", "NAMED"));
      brush.addPoint(point);
      ((ChartVSAssembly) vs.getAssembly(BRUSH)).setBrushSelection(brush);
      assertNotNull(box.getBrushingChart(CHART), "fixture: the chart is brushed");

      expected = rows(box.getData(CHART, true, DataMap.ZOOM));
      int brushed = rows(box.getData(CHART));
      assertTrue(expected > brushed && brushed > 0,
                 "fixture: all data " + expected + " rows, brushed " + brushed);
      box.resetDataMap(CHART);
      box.clearGraph(CHART);
      AssetDataCache.getCache().clear();

      // pre-warm a clean NORMAL/brushed entry in dmap: VGraphPair.initGraph0() calls
      // box.getData(cname) (NORMAL, line ~240) before box.getData(cname, true, DataMap.ZOOM)
      // (line ~252), and the brushed query's own condition scan reads every row of the base
      // table to decide pass/fail (HOLD_ROW is only excluded from its *output*, not from the
      // scan), so without this the subject's own first internal call would consume hold1
      // instead of its second (ZOOM) call, the one this test means to target
      rows(box.getData(CHART));
      AssetDataCache.getCache().clear();

      HeldQueryManager qmgr = new HeldQueryManager();
      queryManagers(box).put(CHART, qmgr);
      Principal principal = ThreadContext.getContextPrincipal();
      ExecutorService threads = Executors.newFixedThreadPool(3);

      try {
         data.hold1.set(true);
         Future<VGraphPair> subject = threads.submit(() -> {
            ThreadContext.setContextPrincipal(principal);
            return box.getVGraphPair(CHART, true, null);
         });
         assertTrue(data.held1.await(WAIT, TimeUnit.SECONDS),
                    "the chart's own all-data fetch (inside getVGraphPair) was not held");

         // the first sibling cancel: a second, full query of CHART (its own brushed/NORMAL
         // read), exactly as SiblingChartQueryCancelCacheTest drives its one cancel. Force a
         // fresh query instead of a dmap hit on the entry pre-warmed above.
         box.resetDataMap(CHART, DataMap.NORMAL);
         Future<Object> sibling1 = threads.submit(() -> {
            ThreadContext.setContextPrincipal(principal);
            qmgr.sibling1Thread = Thread.currentThread();
            return box.getData(CHART);
         });
         assertTrue(qmgr.sibling1Cancelled.await(WAIT, TimeUnit.SECONDS),
                    "the first sibling read did not cancel the chart's query manager");
         // let the first sibling read run all the way to completion (not just past its own
         // cancelForQuery() entry) before arming the second hold below: its own brush-condition
         // scan can touch row 10 too (HOLD_ROW is only excluded from the final, filtered output,
         // not from the scan that decides it), and would otherwise race to steal that hold
         sibling1.get(WAIT, TimeUnit.SECONDS);

         // arm the second hold only once the first sibling is completely done, then release the
         // first attempt: the #78033 retry runs synchronously, on the same (subject) thread,
         // immediately after the first attempt returns
         data.hold2.set(true);
         data.release1.countDown();

         assertTrue(data.held2.await(WAIT, TimeUnit.SECONDS),
                    "the #78033 retry's own fetch was not held");

         // the second sibling cancel, which must land specifically while the retry's own fetch
         // is held. Force a fresh query instead of a dmap hit (the first sibling's own NORMAL
         // read already cached one), so this one also reaches VSAQuery.getTableLens() and
         // cancels the chart's query manager again.
         box.resetDataMap(CHART, DataMap.NORMAL);
         Future<Object> sibling2 = threads.submit(() -> {
            ThreadContext.setContextPrincipal(principal);
            qmgr.sibling2Thread = Thread.currentThread();
            return box.getData(CHART);
         });
         assertTrue(qmgr.sibling2Cancelled.await(WAIT, TimeUnit.SECONDS),
                    "the second sibling read did not cancel the chart's query manager during " +
                    "the retry");
         sibling2.get(WAIT, TimeUnit.SECONDS);

         data.release2.countDown();
         pair = subject.get(WAIT, TimeUnit.SECONDS);

         return "cached data without the brush: " +
            describe(dmap(box).get(CHART, DataMap.ZOOM)) + ", expected rows: " + expected +
            ", first cancel count: " + qmgr.sibling1CancelCount +
            ", second cancel count: " + qmgr.sibling2CancelCount +
            ", table read held on: " + data.heldOn[0] + " / " + data.heldOn[1] +
            ", pair completed: " + pair.isCompleted();
      }
      finally {
         data.release1.countDown();
         data.release2.countDown();
         threads.shutdownNow();
         assertTrue(threads.awaitTermination(WAIT, TimeUnit.SECONDS), "a read did not end");
      }
   }

   private void assertChartHasAllData(String state) throws Exception {
      Object all = box.getData(CHART, true, DataMap.ZOOM);
      ChartVSAssembly chart = (ChartVSAssembly) box.getViewsheet().getAssembly(CHART);
      assertAll(
         () -> assertTrue(pair.isCompleted(), "fixture: the VGraphPair should be completed (" +
                          state + ")"),
         () -> assertFalse(chart.getChartInfo().isNoData() || pair.getRealSizeVGraph() == null,
                           "the brushed chart is drawn as \"No data\" (no data: " +
                           chart.getChartInfo().isNoData() + ", graph: " +
                           (pair.getRealSizeVGraph() != null) + ", " + state + ")"));
   }

   private static void chart(Viewsheet vs, String name, SourceInfo source) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
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
   }

   private static int rows(Object data) {
      if(data == null) {
         return -1;
      }

      TableLens lens = data instanceof VSDataSet ? ((VSDataSet) data).getTable() : (TableLens) data;
      lens.moreRows(Integer.MAX_VALUE);
      return lens.getRowCount() - lens.getHeaderRowCount();
   }

   private static String describe(Object data) {
      return data == null ? "none" : data instanceof VSDataSet ?
         rows(data) + " rows" : String.valueOf(data);
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
    * The worksheet table's data. Holds the first read of row 10 that is not part of a fetch
    * (the summary of a table the fetch already returned, computed while the query reads it) in
    * each of two independent stages, so the chart's own all-data fetch can be held once for its
    * original attempt and once more for the #78033 retry.
    */
   private static final class HeldData extends XEmbeddedTable {
      HeldData(XTable table) {
         super(createTypes(table), table);
      }

      @Override
      public Object getObject(int r, int c) {
         if(!calledFrom(PROCESSOR, "run0")) {
            if(r == HOLD_ROW && hold1.get() && hold1.compareAndSet(true, false)) {
               hold(0, held1, release1);
            }
            else if(r == HOLD_ROW2 && hold2.get() && hold2.compareAndSet(true, false)) {
               hold(1, held2, release2);
            }
         }

         return super.getObject(r, c);
      }

      private void hold(int stage, CountDownLatch held, CountDownLatch release) {
         heldOn[stage] = Thread.currentThread().getName() +
            (calledFrom(SummaryFilter.class.getName(), "process") ? " in SummaryFilter" : "");
         held.countDown();

         try {
            release.await(WAIT, TimeUnit.SECONDS);
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }
      }

      private final AtomicBoolean hold1 = new AtomicBoolean();
      private final AtomicBoolean hold2 = new AtomicBoolean();
      private final CountDownLatch held1 = new CountDownLatch(1);
      private final CountDownLatch held2 = new CountDownLatch(1);
      private final CountDownLatch release1 = new CountDownLatch(1);
      private final CountDownLatch release2 = new CountDownLatch(1);
      // shared with the clones the queries read
      private final String[] heldOn = new String[2];
   }

   /**
    * The chart's query manager. It reports the cancel of each of the two sibling reads
    * separately.
    */
   private static final class HeldQueryManager extends QueryManager {
      HeldQueryManager() {
         super(true);
      }

      @Override
      public int cancel() {
         int count = super.cancel();

         if(Thread.currentThread() == sibling1Thread) {
            sibling1CancelCount = count;
            sibling1Cancelled.countDown();
         }
         else if(Thread.currentThread() == sibling2Thread) {
            sibling2CancelCount = count;
            sibling2Cancelled.countDown();
         }

         return count;
      }

      private volatile Thread sibling1Thread;
      private volatile Thread sibling2Thread;
      private volatile int sibling1CancelCount;
      private volatile int sibling2CancelCount;
      private final CountDownLatch sibling1Cancelled = new CountDownLatch(1);
      private final CountDownLatch sibling2Cancelled = new CountDownLatch(1);
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   private Principal oldPrincipal;
   private ViewsheetSandbox box;
   private HeldData data;
   private VGraphPair pair;
   private int expected;
   private static final String TABLE = "TableView1";
   private static final String CHART = "Chart78033";
   private static final String BRUSH = "Brush78033";
   private static final int HOLD_ROW = 10;
   private static final int HOLD_ROW2 = 10;
   private static final String PROCESSOR = AssetDataCache.class.getName() + "$Processor";
   private static final long WAIT = 30;
}
