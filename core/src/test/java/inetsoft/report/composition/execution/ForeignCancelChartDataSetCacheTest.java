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
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.lens.xnode.XNodeTableLens;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78342: a chart data set whose shared table another assembly's query manager cancelled
 * while the data set read it was cached in {@code dmap}, and handed out again on every later
 * request. The chart's own query manager never saw that cancel, so {@code isDataCancelled}
 * missed it, and the dmap hit check only dropped a data set whose table a script stopped.
 *
 * <p>A brush chart, a victim and a cutter with identical bindings share one AssetDataCache
 * entry. The cutter's table is held mid-read, the victim reads the cached copy, and the cutter's
 * next query cancels its own query manager, cutting the victim's table.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class ForeignCancelChartDataSetCacheTest {
   @BeforeEach
   void setUp() {
      oldPrincipal = ThreadContext.getContextPrincipal();
      AssetDataCache.getCache().clear();
   }

   @AfterEach
   void restoreThreadPrincipal() {
      ThreadContext.setContextPrincipal(oldPrincipal);
   }

   /**
    * dmap must not keep a data set another manager cancelled, and the next request must have
    * all the data.
    */
   @Test
   void dataSetCutByAnotherManagerIsNotKept() throws Exception {
      String state = race();
      Object cached = dmap(box).get(VICTIM, DataMap.ZOOM);
      assertFalse(AssetDataCache.isDataSetCancelled(cached),
                  "dmap keeps a data set a sibling's query manager cancelled (" + state + ")");
      Object next = box.getData(VICTIM, true, DataMap.ZOOM);
      assertAll(
         () -> assertFalse(AssetDataCache.isDataSetCancelled(next),
                           "the next read returns the cancelled data set (" + state + ")"),
         () -> assertEquals(expected, rows(next), "rows (" + state + ")"),
         () -> assertEquals(expectedTotal, total(next), 0.0001, "total (" + state + ")"));
   }

   /**
    * The first caller gets the full data too: the cut data is computed again once.
    */
   @Test
   void firstCallerGetsTheFullDataAfterTheCut() throws Exception {
      String state = race();
      assertAll(
         () -> assertFalse(AssetDataCache.isDataSetCancelled(victimFirst),
                           "the first caller got the cancelled data set (" + state + ")"),
         () -> assertEquals(expected, rows(victimFirst), "rows (" + state + ")"),
         () -> assertEquals(expectedTotal, total(victimFirst), 0.0001, "total (" + state + ")"));
   }

   /**
    * A cancel after the data set read all its table is a no-op, so the cached data set is kept.
    */
   @Test
   void dataSetCancelledAfterItWasReadIsKept() throws Exception {
      prepare();
      Object first = box.getData(VICTIM, true, DataMap.ZOOM);
      assertEquals(expected, rows(first));
      assertTrue(box.getQueryManager(VICTIM).cancel() > 0,
                 "fixture: the manager held no lens to cancel");
      assertFalse(AssetDataCache.isDataSetCancelled(first));
      assertSame(first, box.getData(VICTIM, true, DataMap.ZOOM));
   }

   /**
    * A cancelled data set found in dmap is a miss: the next read computes the data again.
    */
   @Test
   void cancelledDataSetInDmapIsComputedAgain() throws Exception {
      prepare();
      VSDataSet cut = new VSDataSet(new FlaggedLens(true, null), null);
      assertTrue(AssetDataCache.isDataSetCancelled(cut), "fixture: the data set is cancelled");
      dmap(box).put(VICTIM, cut, DataMap.ZOOM);
      Object next = box.getData(VICTIM, true, DataMap.ZOOM);
      assertTrue(next != cut, "the cancelled data set was served from dmap");
      assertEquals(expected, rows(next));
      assertEquals(expectedTotal, total(next), 0.0001);
   }

   /**
    * A complete data set, and one over a load failure, are still served from dmap.
    */
   @Test
   void completeAndLoadFailedDataSetsAreServedFromDmap() throws Exception {
      prepare();
      VSDataSet complete = new VSDataSet(new FlaggedLens(false, null), null);
      dmap(box).put(VICTIM, complete, DataMap.ZOOM);
      assertSame(complete, box.getData(VICTIM, true, DataMap.ZOOM));

      VSDataSet failed =
         new VSDataSet(new FlaggedLens(true, new RuntimeException("load failure")), null);
      assertFalse(AssetDataCache.isDataSetCancelled(failed),
                  "a load failure is not a cancel cut");
      dmap(box).put(VICTIM, failed, DataMap.ZOOM);
      assertSame(failed, box.getData(VICTIM, true, DataMap.ZOOM));
   }

   @Test
   void predicateOnlyMatchesACancelledDataSet() throws Exception {
      prepare();
      assertFalse(AssetDataCache.isDataSetCancelled(null));
      assertFalse(AssetDataCache.isDataSetCancelled("data"));
      assertFalse(AssetDataCache.isDataSetCancelled(box.getData(VICTIM, true, DataMap.ZOOM)));
   }

   private void prepare() throws Exception {
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
      chart(vs, VICTIM, source);
      chart(vs, CUTTER, source);
      VSSelection brush = new VSSelection();
      VSPoint point = new VSPoint();
      point.addValue(new VSFieldValue("type", "NAMED"));
      brush.addPoint(point);
      ((ChartVSAssembly) vs.getAssembly(BRUSH)).setBrushSelection(brush);
      assertNotNull(box.getBrushingChart(VICTIM), "fixture: the victim is brushed");
      assertNotNull(box.getBrushingChart(CUTTER), "fixture: the cutter is brushed");

      expected = rows(box.getData(VICTIM, true, DataMap.ZOOM));
      expectedTotal = total(box.getData(VICTIM, true, DataMap.ZOOM));
      int brushedRows = rows(box.getData(VICTIM));
      assertTrue(expected > brushedRows && brushedRows > 0,
                 "fixture: all data " + expected + " rows, brushed " + brushedRows);
      box.resetDataMap(VICTIM);
      box.resetDataMap(CUTTER);
      box.clearGraph(VICTIM);
      box.clearGraph(CUTTER);
      AssetDataCache.getCache().clear();
   }

   /**
    * Hold the cutter's read of the shared table, let the victim read the cached copy, then let
    * the cutter's next query cancel its manager.
    *
    * @return the state after the race, for the failure message.
    */
   private String race() throws Exception {
      prepare();
      RecordingQueryManager cutterMgr = new RecordingQueryManager();
      queryManagers(box).put(CUTTER, cutterMgr);
      Principal principal = ThreadContext.getContextPrincipal();
      ExecutorService threads = Executors.newFixedThreadPool(3);

      try {
         data.hold.set(true);
         Future<Object> cutter = threads.submit(() -> {
            ThreadContext.setContextPrincipal(principal);
            return box.getData(CUTTER, true, DataMap.ZOOM);
         });
         assertTrue(data.held.await(WAIT, TimeUnit.SECONDS), "the cutter's read was not held");
         assertFalse(cutterMgr.registered.isEmpty(), "fixture: the cutter registered no lens");

         Future<Object> victim = threads.submit(() -> {
            ThreadContext.setContextPrincipal(principal);
            return box.getData(VICTIM, true, DataMap.ZOOM);
         });
         // the victim reaches the cached copy of the cutter's lens and waits in its read
         Thread.sleep(2000);
         assertFalse(victim.isDone(), "fixture: the victim did not wait for the held lens");

         box.resetDataMap(CUTTER, DataMap.ZOOM);
         Future<Object> cutterAgain = threads.submit(() -> {
            ThreadContext.setContextPrincipal(principal);
            return box.getData(CUTTER, true, DataMap.ZOOM);
         });
         assertTrue(cutterMgr.cancelled.await(WAIT, TimeUnit.SECONDS),
                    "fixture: the cutter's second query did not cancel its manager");
         cutterAgain.get(WAIT, TimeUnit.SECONDS);
         data.release.countDown();
         cutter.get(WAIT, TimeUnit.SECONDS);
         victimFirst = victim.get(WAIT, TimeUnit.SECONDS);
         return "cutter cancel cut " + cutterMgr.lastCancelCount + " lens(es); expected " +
            expected + " rows, total " + expectedTotal + "; held on " + data.heldOn[0];
      }
      finally {
         data.release.countDown();
         threads.shutdownNow();
         assertTrue(threads.awaitTermination(WAIT, TimeUnit.SECONDS), "a read did not end");
      }
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
      // the same formula for the victim and the cutter: one AssetDataCache key
      agg.setFormulaValue("Sum");
      info.addYField(agg);
      vs.addAssembly(chart);
   }

   private static int rows(Object data) {
      TableLens lens = data instanceof VSDataSet ? ((VSDataSet) data).getTable() : (TableLens) data;
      lens.moreRows(Integer.MAX_VALUE);
      return lens.getRowCount() - lens.getHeaderRowCount();
   }

   private static double total(Object data) {
      TableLens lens = data instanceof VSDataSet ? ((VSDataSet) data).getTable() : (TableLens) data;
      lens.moreRows(Integer.MAX_VALUE);
      double sum = 0;

      for(int r = lens.getHeaderRowCount(); r < lens.getRowCount(); r++) {
         for(int c = 0; c < lens.getColCount(); c++) {
            if(lens.getObject(r, c) instanceof Number n) {
               sum += n.doubleValue();
            }
         }
      }

      return sum;
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
    * of a fetch: the lazy summary pass of the all-data query of a brushed chart.
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
            heldOn[0] = Thread.currentThread().getName();
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
    * The cutter's query manager: records the lenses registered on it and the first cancel that
    * finds one.
    */
   private static final class RecordingQueryManager extends QueryManager {
      RecordingQueryManager() {
         super(true);
      }

      @Override
      public synchronized void addPending(Object stmt) {
         if(stmt != null) {
            registered.add(stmt);
         }

         super.addPending(stmt);
      }

      @Override
      public int cancel() {
         int count = super.cancel();

         if(count > 0 && cancelled.getCount() > 0) {
            lastCancelCount = count;
            cancelled.countDown();
         }

         return count;
      }

      private final Set<Object> registered =
         Collections.newSetFromMap(Collections.synchronizedMap(new IdentityHashMap<>()));
      private final CountDownLatch cancelled = new CountDownLatch(1);
      private volatile int lastCancelCount;
   }

   /**
    * An empty lens reporting the given cancel and load failure state.
    */
   private static final class FlaggedLens extends XNodeTableLens {
      FlaggedLens(boolean cancelled, Exception failure) {
         this.cancelled = cancelled;
         this.failure = failure;
      }

      @Override
      public boolean isCancelled() {
         return cancelled;
      }

      @Override
      public Exception getLoadException() {
         return failure;
      }

      private final boolean cancelled;
      private final Exception failure;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   private Principal oldPrincipal;
   private ViewsheetSandbox box;
   private HeldData data;
   private Object victimFirst;
   private int expected;
   private double expectedTotal;
   private static final String TABLE = "TableView1";
   private static final String BRUSH = "Brush78342";
   private static final String VICTIM = "Victim78342";
   private static final String CUTTER = "Cutter78342";
   private static final int HOLD_ROW = 10;
   private static final String PROCESSOR = AssetDataCache.class.getName() + "$Processor";
   private static final long WAIT = 30;
}
