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
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.ThreadContext;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;

/**
 * Bug #78033 (reopened), Mechanism B (H2', "wrong-{@code QueryManager} field race", see
 * docs/teams/2026-10-10-bug-78033-brush-nodata/01-hypothesis-assetdatacache.md and
 * 02-root-cause.md). {@code AssetQuerySandbox} (the {@code wbox} shared by every assembly of one
 * {@code ViewsheetSandbox}, {@code ViewsheetSandbox.java} line ~242) has a single, plain,
 * unsynchronized {@code queryMgr} field. {@code VSAQuery.getTableLens(TableAssembly)} writes it
 * unconditionally on every entry ({@code wbox.setQueryManager(qmgr)}, line ~1085) with the
 * correct, per-assembly manager; {@code AssetQuery.getRuntimeTableLens()} later reads it back
 * ({@code QueryManager qmgr = box.getQueryManager();}, line ~878) into a local variable that
 * shadows the correctly-set instance field {@code this.qmgr} and registers the table being built
 * on whatever it reads (line ~950-951, {@code qmgr.addPending(base)}).
 *
 * <p>If a second assembly's own {@code VSAQuery.getTableLens()} call writes the shared field in
 * the window between the victim's own write of that line and its own read, the victim's table is
 * registered on the second assembly's query manager instead of its own. When the second
 * assembly's own next, perfectly routine query self-cancels its manager ({@code cancelForQuery()},
 * unconditional on entry — the exact case the original #78033 fix instruments), the victim's
 * table is cancelled as a side effect. But the victim's own {@code VSAQuery} snapshotted its own,
 * correct manager ({@code fetchQueryManager}, {@code VSAQuery.java} line ~1080) *before* this race
 * window opened, and never consults the shared field again — so {@code isFetchCancelled()}/
 * {@code isFetchCancelledByQuery()}/{@code ChartVSAQuery.isDataCancelled()} all report
 * <b>not cancelled</b>, and {@code ViewsheetSandbox.getData()} caches the truncated result into
 * {@code dmap} as if it were genuine data, on the very first attempt — no retry, no second
 * coincidence of the kind Mechanism A needs.</p>
 *
 * <p>The race is forced deterministically with a Mockito spy standing in for the shared
 * {@code AssetQuerySandbox} ({@code ViewsheetSandbox}'s private {@code wbox} field, swapped in by
 * reflection): the spy holds the victim's own thread exactly at its
 * {@code AssetQuery.getRuntimeTableLens()} read of the shared field, after the victim's own write
 * of that field has already happened (synchronously, before the victim's fetch is dispatched to
 * the query thread pool) but before the victim reads it back. While held, the clobber assembly's
 * own full, undisturbed query runs and overwrites the shared field with its own manager, and the
 * victim's held read then resolves to the clobber assembly's manager instead of its own.</p>
 *
 * <p>The victim is a brushed chart's own ZOOM (all-data) sub-query, the same shape
 * {@link SiblingChartQueryCancelCacheTest} uses, and the same held row (row 10, excluded from the
 * brush's own output) — needed because, confirmed empirically, a plain (unbrushed) chart's single
 * query reads every row synchronously inside {@code AssetDataCache.Processor.run0()}, with no
 * later, separate pass for a cancel to actually interrupt; a brushed chart's ZOOM sub-query's own
 * summarization happens in a later, lazy pass instead, which does check for a cancel landing
 * mid-read.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class CrossAssemblyQueryManagerFieldRaceTest {
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
    * The victim's table ends up registered on, and cancelled by, the clobber assembly's query
    * manager instead of its own. The victim's data must not be lost, and its own cancel-detection
    * must not be fooled into caching the loss as if it were genuine data.
    */
   @Test
   void siblingAssemblyOverwritesTheSharedQueryManagerDuringAnotherAssemblysFetch()
      throws Exception
   {
      String state = burst();
      assertVictimHasAllData(state);
   }

   /**
    * A brushed chart (the victim, with its own brush sibling) and an unrelated, unbrushed chart
    * (the clobber assembly) bound to the same worksheet table. Hold the victim's own read of the
    * shared {@code AssetQuerySandbox.queryMgr} field (via a Mockito spy standing in for it) right
    * after the victim's own write of that field, and let the clobber assembly's own full query
    * run and overwrite it in that window. Release the victim, which now reads back the clobber
    * assembly's manager, and let the victim's own table read reach a held row, so its table is
    * registered (and still pending) when the clobber assembly's own next, routine query cancels
    * its own manager — cancelling the victim's table as a side effect.
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

      chart(vs, VICTIM_BRUSH, source, "Sum");
      chart(vs, VICTIM, source, "Sum");
      chart(vs, CLOBBER, source, "Average");
      VSSelection brush = new VSSelection();
      VSPoint point = new VSPoint();
      point.addValue(new VSFieldValue("type", "NAMED"));
      brush.addPoint(point);
      ((ChartVSAssembly) vs.getAssembly(VICTIM_BRUSH)).setBrushSelection(brush);
      assertNotNull(box.getBrushingChart(VICTIM), "fixture: the victim is brushed");

      expected = rows(box.getData(VICTIM, true, DataMap.ZOOM));
      expectedTotal = total(box.getData(VICTIM, true, DataMap.ZOOM));
      int brushedRows = rows(box.getData(VICTIM));
      assertTrue(expected > brushedRows && brushedRows > 0,
                 "fixture: all data " + expected + " rows, brushed " + brushedRows);
      assertTrue(rows(box.getData(CLOBBER)) > 0, "fixture: clobber assembly has data");
      box.resetDataMap(VICTIM);
      box.resetDataMap(CLOBBER);
      AssetDataCache.getCache().clear();

      AssetQuerySandbox realWbox = box.getAssetQuerySandbox();
      AssetQuerySandbox spy = Mockito.spy(realWbox);
      setAssetQuerySandbox(box, spy);

      AtomicBoolean armed = new AtomicBoolean(true);
      CountDownLatch victimHeldAtRead = new CountDownLatch(1);
      CountDownLatch victimReleaseRead = new CountDownLatch(1);

      // VSAQuery.getTableLens(TableAssembly) already wrote the shared field with the victim's
      // own, correct manager, and snapshotted it into this VSAQuery instance's own
      // fetchQueryManager field, before dispatching the victim's fetch to the query thread
      // pool -- this holds the pool thread exactly at AssetQuery.getRuntimeTableLens()'s read
      // of that shared field back, the window the falsifiable claim in
      // 01-hypothesis-assetdatacache.md describes.
      doAnswer(inv -> {
         if(calledFrom(AssetQuery.class.getName(), "getRuntimeTableLens") &&
            armed.compareAndSet(true, false))
         {
            victimHeldAtRead.countDown();
            victimReleaseRead.await(WAIT, TimeUnit.SECONDS);
         }

         return inv.callRealMethod();
      }).when(spy).getQueryManager();

      Principal principal = ThreadContext.getContextPrincipal();
      ExecutorService threads = Executors.newFixedThreadPool(3);

      try {
         Future<Object> victim = threads.submit(() -> {
            ThreadContext.setContextPrincipal(principal);
            return box.getData(VICTIM, true, DataMap.ZOOM);
         });
         assertTrue(victimHeldAtRead.await(WAIT, TimeUnit.SECONDS),
                    "the victim's own read of the shared query manager was not held");

         // the clobber assembly's own, undisturbed query: VSAQuery.getTableLens() writes
         // wbox.setQueryManager(qmgrClobber) unconditionally on entry, landing in the window
         // while the victim is held. Not yet armed against the embedded table's held row, so
         // this read runs straight through.
         assertTrue(rows(box.getData(CLOBBER)) > 0, "fixture: the clobber assembly's own read");

         // arm the embedded table's hold only now, so the clobber assembly's own read above
         // could not consume it instead of the victim's upcoming one
         data.hold.set(true);
         victimReleaseRead.countDown();

         assertTrue(data.held.await(WAIT, TimeUnit.SECONDS),
                    "the victim's own table read was not held");

         // the clobber assembly's own next, routine query: cancelForQuery() on entry is
         // unconditional and ordinary -- exactly the case the original #78033 fix instruments --
         // but by now the victim's table is (mis)registered on the clobber assembly's manager,
         // not its own
         box.resetDataMap(CLOBBER);
         Future<Object> clobberAgain = threads.submit(() -> {
            ThreadContext.setContextPrincipal(principal);
            return box.getData(CLOBBER);
         });
         clobberAgain.get(WAIT, TimeUnit.SECONDS);

         data.release.countDown();
         Object victimResult = victim.get(WAIT, TimeUnit.SECONDS);

         return "victim's own fetch: " + rows(victimResult) + " rows, total " +
            total(victimResult) + "; expected: " + expected + " rows, total " + expectedTotal +
            "; victim cached as: " + describe(dmap(box).get(VICTIM, DataMap.ZOOM)) +
            "; table read held on: " + data.heldOn[0];
      }
      finally {
         victimReleaseRead.countDown();
         data.release.countDown();
         threads.shutdownNow();
         assertTrue(threads.awaitTermination(WAIT, TimeUnit.SECONDS), "a read did not end");
      }
   }

   /**
    * After the race, the victim's data must still be complete, and whatever is cached for it
    * must reflect that -- not a result quietly cut short by a manager the victim never shared
    * with anyone, cached as if it were genuine.
    */
   private void assertVictimHasAllData(String state) throws Exception {
      Object cached = dmap(box).get(VICTIM, DataMap.ZOOM);
      assertAll(
         () -> assertNotNull(cached, "fixture: the victim's result should be cached (" +
                             state + ")"),
         () -> assertEquals(expected, rows(cached),
                            "the victim lost rows to a sibling assembly's query manager, " +
                            "without being detected as cancelled (" + state + ")"),
         () -> assertEquals(expectedTotal, total(cached), 0.0001,
                            "the victim's cached sum is wrong -- truncated by a sibling " +
                            "assembly's query manager without being detected as cancelled (" +
                            state + ")"));
   }

   private static void chart(Viewsheet vs, String name, SourceInfo source, String formula) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      chart.setSourceInfo(source);
      VSChartInfo info = chart.getVSChartInfo();
      VSChartDimensionRef dim =
         new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "type")));
      dim.setGroupColumnValue("type");
      info.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("wind");
      // the victim and the clobber assembly must not collide on the same AssetDataCache DataKey
      // -- its computation depends on the TableAssembly's own aggregate/condition content, not
      // the VS assembly's name (01-hypothesis-assetdatacache.md Step 7). A different aggregate
      // formula is enough to keep the two keys apart while both still read the same underlying
      // table rows.
      agg.setFormulaValue(formula);
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
         rows(data) + " rows, total " + total(data) : String.valueOf(data);
   }

   /**
    * Sum of the victim's "wind" aggregate column across every data row. A truncated read can
    * still show the same *row count* (the same distinct "type" groups may already have been
    * seen before the held row) while missing some underlying rows' contribution to the sum --
    * this catches that case, which a bare row count would not.
    */
   private static double total(Object data) {
      if(data == null) {
         return Double.NaN;
      }

      TableLens lens = data instanceof VSDataSet ? ((VSDataSet) data).getTable() : (TableLens) data;
      lens.moreRows(Integer.MAX_VALUE);
      double sum = 0;

      for(int r = lens.getHeaderRowCount(); r < lens.getRowCount(); r++) {
         for(int c = 0; c < lens.getColCount(); c++) {
            Object v = lens.getObject(r, c);

            if(v instanceof Number) {
               sum += ((Number) v).doubleValue();
            }
         }
      }

      return sum;
   }

   private static void setAssetQuerySandbox(ViewsheetSandbox box, AssetQuerySandbox wbox)
      throws Exception
   {
      Field field = ViewsheetSandbox.class.getDeclaredField("wbox");
      field.setAccessible(true);
      field.set(box, wbox);
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
    * The worksheet table's data, shared by both assemblies. Holds the first read of row 10 that
    * is not part of a fetch (the summary of a table the fetch already returned, computed on a
    * thread of its own while the query reads it) -- the same row and the same mechanism
    * {@code SiblingChartQueryCancelCacheTest} uses -- so the victim's own table read can be held
    * open (with its table already registered, mis-registered on the clobber assembly's manager)
    * while the clobber assembly's own next query cancels it.
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
      private final String[] heldOn = new String[1];
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   private Principal oldPrincipal;
   private ViewsheetSandbox box;
   private HeldData data;
   private int expected;
   private double expectedTotal;
   private static final String TABLE = "TableView1";
   private static final String VICTIM = "Victim78033";
   private static final String VICTIM_BRUSH = "VictimBrush78033";
   private static final String CLOBBER = "Clobber78033";
   private static final int HOLD_ROW = 10;
   private static final String PROCESSOR = AssetDataCache.class.getName() + "$Processor";
   private static final long WAIT = 30;
}
