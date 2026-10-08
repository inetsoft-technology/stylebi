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
 * Bug #78033: after rapid selection changes a brushed chart stayed on "No data is available!".
 * A query of the chart cancels the chart's query manager when it reaches
 * {@code VSAQuery.getTableLens}. That also cancels a table that another query of the same chart
 * already got back and was still summarizing. That query's data, the chart's data without the
 * brush, came back with no rows and was cached until the next selection change. The brushed
 * chart then had nothing to draw and was marked as having no data.
 *
 * <p>The test holds the summary of the table fetched for the chart's data without the brush,
 * runs a read of the chart's brushed data through its cancel, and then lets the summary finish.
 * The chart must then have all its data.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class SiblingChartQueryCancelCacheTest {
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
    * The brushed read cancels the chart's query manager while the table of the data without the
    * brush is being summarized.
    */
   @Test
   void siblingCancelWhileTheDataIsReadDoesNotLeaveTheChartWithoutData() throws Exception {
      String state = burst(true);
      assertChartHasAllData(state);
   }

   /**
    * Control: the same reads, with the brushed read after the data without the brush was read.
    */
   @Test
   void siblingQueryAfterTheDataWasReadLeavesTheData() throws Exception {
      String state = burst(false);
      assertChartHasAllData(state);
   }

   /**
    * Brush the chart, read its data without the brush and hold the summary of the fetched table,
    * then read its brushed data, before or after the summary is released.
    *
    * @param cancelWhileReading true to run the brushed read to its cancel while the summary is
    *                           held; false to run it after the first read finished.
    *
    * @return the state after both reads, for the failure message.
    */
   private String burst(boolean cancelWhileReading) throws Exception {
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
      AssetDataCache.getCache().clear();

      HeldQueryManager qmgr = new HeldQueryManager();
      queryManagers(box).put(CHART, qmgr);
      Principal principal = ThreadContext.getContextPrincipal();
      ExecutorService threads = Executors.newFixedThreadPool(2);

      try {
         data.hold.set(true);
         Future<Object> all = threads.submit(() -> {
            ThreadContext.setContextPrincipal(principal);
            return box.getData(CHART, true, DataMap.ZOOM);
         });
         assertTrue(data.held.await(WAIT, TimeUnit.SECONDS), "the table was not held");

         Future<Object> brushedRead = null;

         if(cancelWhileReading) {
            brushedRead = threads.submit(() -> {
               ThreadContext.setContextPrincipal(principal);
               qmgr.siblingThread = Thread.currentThread();
               return box.getData(CHART);
            });
            assertTrue(qmgr.siblingCancelled.await(WAIT, TimeUnit.SECONDS),
                       "the brushed read did not cancel the chart's query manager");
         }

         data.release.countDown();
         all.get(WAIT, TimeUnit.SECONDS);

         if(!cancelWhileReading) {
            brushedRead = threads.submit(() -> {
               ThreadContext.setContextPrincipal(principal);
               qmgr.siblingThread = Thread.currentThread();
               return box.getData(CHART);
            });
         }

         brushedRead.get(WAIT, TimeUnit.SECONDS);
         return "cached data without the brush: " +
            describe(dmap(box).get(CHART, DataMap.ZOOM)) + ", expected rows: " + expected +
            ", tables cancelled by the brushed read: " + qmgr.siblingCancelCount +
            ", table read held on: " + data.heldOn[0];
      }
      finally {
         data.release.countDown();
         threads.shutdownNow();
         assertTrue(threads.awaitTermination(WAIT, TimeUnit.SECONDS), "a read did not end");
      }
   }

   private void assertChartHasAllData(String state) throws Exception {
      Object all = box.getData(CHART, true, DataMap.ZOOM);
      VGraphPair pair = box.getVGraphPair(CHART, true, null);
      ChartVSAssembly chart = (ChartVSAssembly) box.getViewsheet().getAssembly(CHART);
      assertAll(
         () -> assertEquals(expected, rows(all),
                            "the chart's data without the brush lost rows (" + state + ")"),
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
    * The worksheet table's data. Once armed, it holds the first read of a row that is not part
    * of a fetch: the summary of a table the fetch already returned, which is computed on a
    * thread of its own while the query reads it.
    */
   private static final class HeldData extends XEmbeddedTable {
      HeldData(XTable table) {
         super(createTypes(table), table);
      }

      @Override
      public Object getObject(int r, int c) {
         if(r == HOLD_ROW && hold.get() && !calledFrom(PROCESSOR, "run0") &&
            hold.compareAndSet(true, false))
         {
            heldOn[0] = Thread.currentThread().getName() +
               (calledFrom(SummaryFilter.class.getName(), "process") ? " in SummaryFilter" : "");
            held.countDown();

            try {
               release.await(WAIT, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }

         return super.getObject(r, c);
      }

      private final AtomicBoolean hold = new AtomicBoolean();
      private final CountDownLatch held = new CountDownLatch(1);
      private final CountDownLatch release = new CountDownLatch(1);
      // shared with the clones the queries read
      private final String[] heldOn = new String[1];
   }

   /**
    * The chart's query manager. It reports the cancel of the brushed read.
    */
   private static final class HeldQueryManager extends QueryManager {
      HeldQueryManager() {
         super(true);
      }

      @Override
      public int cancel() {
         int count = super.cancel();

         if(Thread.currentThread() == siblingThread) {
            siblingCancelCount = count;
            siblingCancelled.countDown();
         }

         return count;
      }

      private volatile Thread siblingThread;
      private volatile int siblingCancelCount;
      private final CountDownLatch siblingCancelled = new CountDownLatch(1);
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   private Principal oldPrincipal;
   private ViewsheetSandbox box;
   private HeldData data;
   private int expected;
   private static final String TABLE = "TableView1";
   private static final String CHART = "Chart78033";
   private static final String BRUSH = "Brush78033";
   private static final int HOLD_ROW = 10;
   private static final String PROCESSOR = AssetDataCache.class.getName() + "$Processor";
   private static final long WAIT = 30;
}
