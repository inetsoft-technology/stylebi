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

import inetsoft.mv.MVManager;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.ThreadContext;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.swap.SwapFileReadException;
import inetsoft.util.swap.SwapLostTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #78220: a chart whose {@link VGraphPair} init failed (here a lost swap file read by the
 * chart query, a transient failure that {@code getData} does not cache) must not stay cached
 * in the sandbox. Before the fix the failed pair, completed with no graph, was returned to
 * every later request (and to a request waiting on the failing init), so the chart showed
 * "No data is available" after the cause was gone.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  ViewsheetSandboxGraphPairFailureTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetSandboxGraphPairFailureTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   @BeforeEach
   void setUp() {
      AssetDataCache.getCache().clearCache();
      SreeEnv.setProperty(PoolConfig.ENABLED, "false");
      oldPrincipal = ThreadContext.getPrincipal();
      principal = SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()),
         null, false);
      ThreadContext.setPrincipal(principal);
   }

   @AfterEach
   void tearDown() {
      if(executor != null) {
         executor.shutdownNow();
      }

      ThreadContext.setPrincipal(oldPrincipal);
      SreeEnv.remove(PoolConfig.ENABLED);
      AssetDataCache.getCache().clearCache();
   }

   /**
    * The first request fails with a lost swap file; the next one builds a new pair and plots
    * the chart instead of returning the failed pair.
    */
   @Test
   void requestAfterAFailedInitPlotsTheChart() throws Exception {
      ViewsheetSandbox box = sandbox();
      Failing failing = new Failing(1);

      try(MockedStatic<VSAQuery> ignored = failing.install()) {
         Exception ex = assertThrows(Exception.class, () -> box.getVGraphPair(CHART));
         assertTrue(isSwapLost(ex), "the first request fails with the lost swap: " + ex);

         VGraphPair pair = box.getVGraphPair(CHART);
         assertPlotted(pair);
      }
   }

   /**
    * A failed pair still in the map (its init thread has not removed it yet) is rebuilt, not
    * returned.
    */
   @Test
   void failedPairStillInTheMapIsRebuilt() throws Exception {
      ViewsheetSandbox box = sandbox();
      Failing failing = new Failing(1);

      try(MockedStatic<VSAQuery> ignored = failing.install()) {
         // a pair whose init failed, put back as in the window between the init completing
         // and its init thread removing it
         VGraphPair failed = new VGraphPair();
         failed.initSize(box, CHART, null, false);
         assertThrows(Exception.class, () -> failed.initGraph(box, CHART, null, false, 1));
         assertTrue(failed.isCompleted());
         pairs(box).put(CHART, failed);

         VGraphPair pair = box.getVGraphPair(CHART);
         assertNotSame(failed, pair);
         assertPlotted(pair);
      }
   }

   /**
    * A request that waits on the failing init gets its failure, never the empty pair. The
    * next request plots the chart.
    */
   @Test
   void requestWaitingOnAFailingInitGetsTheFailure() throws Exception {
      ViewsheetSandbox box = sandbox();
      Failing failing = new Failing(1);
      failing.block();
      executor = Executors.newFixedThreadPool(2);

      // only the first request reads through the failing query (a static mock is per thread)
      Future<VGraphPair> first = executor.submit(() -> failing.call(() -> box.getVGraphPair(CHART)));
      assertTrue(failing.inQuery.await(30, TimeUnit.SECONDS), "the first init reached the query");

      AtomicReference<Thread> waiter = new AtomicReference<>();
      Future<VGraphPair> second = executor.submit(() -> {
         waiter.set(Thread.currentThread());
         return withPrincipal(() -> box.getVGraphPair(CHART));
      });

      await().atMost(Duration.ofSeconds(30)).until(() -> isWaitingOnInit(waiter.get()));
      failing.release.countDown();

      Throwable firstFailure = failureOf(first);
      assertTrue(isSwapLost(firstFailure), "the first request fails: " + firstFailure);
      Throwable secondFailure = failureOf(second);
      // the waiter reads the real query, so it got the first request's failure, it did not
      // run the init again
      assertTrue(isSwapLost(secondFailure), "the waiting request fails too: " + secondFailure);
      assertNull(pairs(box).get(CHART), "the failed pair is not kept");

      assertPlotted(box.getVGraphPair(CHART));
   }

   /**
    * Unchanged: a sandbox cancel during the init removes and cancels the pair, the cancelled
    * request returns it without an error (51339), and the next request plots the chart.
    */
   @Test
   void sandboxCancelDuringInitIsUnchanged() throws Exception {
      ViewsheetSandbox box = sandbox();
      Failing failing = new Failing(0);
      failing.block();
      executor = Executors.newSingleThreadExecutor();

      Future<VGraphPair> first = executor.submit(() -> failing.call(() -> box.getVGraphPair(CHART)));
      assertTrue(failing.inQuery.await(30, TimeUnit.SECONDS), "the init reached the query");
      VGraphPair running = pairs(box).get(CHART);
      assertNotNull(running);

      box.cancel();
      failing.release.countDown();

      VGraphPair cancelled = first.get(30, TimeUnit.SECONDS);
      assertSame(running, cancelled);
      assertTrue(cancelled.isCancelled());
      assertNull(pairs(box).get(CHART), "the canceller removed the pair");

      VGraphPair pair = box.getVGraphPair(CHART);
      assertNotSame(cancelled, pair);
      assertPlotted(pair);
   }

   private static void assertPlotted(VGraphPair pair) {
      assertNotNull(pair);
      assertTrue(pair.isCompleted());
      assertFalse(pair.isCancelled());
      assertTrue(pair.isPlotted(), "the chart is plotted");
      assertNotNull(pair.getEGraph());
      assertNotNull(pair.getData());
      assertEquals(3, pair.getData().getRowCount());
   }

   private static <T> T withPrincipal(Callable<T> call) throws Exception {
      ThreadContext.setPrincipal(principal);

      try {
         return call.call();
      }
      finally {
         ThreadContext.setPrincipal(null);
      }
   }

   private static Throwable failureOf(Future<?> future) throws Exception {
      try {
         Object value = future.get(30, TimeUnit.SECONDS);
         return fail("returned " + describe(value) + " without an exception");
      }
      catch(ExecutionException ex) {
         return ex.getCause();
      }
   }

   private static String describe(Object value) {
      if(value instanceof VGraphPair pair) {
         return "pair completed=" + pair.isCompleted() + " cancelled=" + pair.isCancelled() +
            " plotted=" + pair.isPlotted();
      }

      return String.valueOf(value);
   }

   private static boolean isWaitingOnInit(Thread thread) {
      return thread != null && thread.getState() == Thread.State.TIMED_WAITING &&
         Arrays.stream(thread.getStackTrace())
            .anyMatch(e -> "waitInit".equals(e.getMethodName()) &&
               VGraphPair.class.getName().equals(e.getClassName()));
   }

   private static boolean isSwapLost(Throwable ex) {
      for(Throwable t = ex; t != null; t = t.getCause()) {
         if(t instanceof SwapFileReadException) {
            return true;
         }
      }

      return false;
   }

   @SuppressWarnings("unchecked")
   private static Map<String, VGraphPair> pairs(ViewsheetSandbox box) throws Exception {
      Field f = ViewsheetSandbox.class.getDeclaredField("pairs");
      f.setAccessible(true);
      return (Map<String, VGraphPair>) f.get(box);
   }

   /**
    * Real queries for every assembly; the chart query's first {@code n} reads fail with a
    * lost swap file. When blocked, the first chart query read waits for {@link #release}.
    */
   private static final class Failing {
      Failing(int n) {
         remaining = new AtomicInteger(n);
      }

      void block() {
         blocking = true;
      }

      /**
       * Run {@code call} on this thread with the failing query installed, as the principal
       * of the test.
       */
      <T> T call(Callable<T> call) throws Exception {
         try(MockedStatic<VSAQuery> ignored = install()) {
            return withPrincipal(call);
         }
      }

      MockedStatic<VSAQuery> install() {
         MockedStatic<VSAQuery> st = Mockito.mockStatic(VSAQuery.class, Mockito.CALLS_REAL_METHODS);
         st.when(() -> VSAQuery.createVSAQuery(Mockito.any(), Mockito.any(), Mockito.anyInt()))
            .thenAnswer(inv -> {
               VSAQuery real = (VSAQuery) inv.callRealMethod();
               VSAssembly assembly = inv.getArgument(1);

               if(real == null || !CHART.equals(assembly.getName())) {
                  return real;
               }

               VSAQuery spy = Mockito.spy(real);
               Mockito.doAnswer(i -> {
                  if(blocking && entered.getAndIncrement() == 0) {
                     inQuery.countDown();

                     if(!release.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("not released");
                     }
                  }

                  if(remaining.getAndDecrement() > 0) {
                     throw SwapLostTestSupport.swapLost();
                  }

                  return i.callRealMethod();
               }).when(spy).getData();
               return spy;
            });
         return st;
      }

      private final AtomicInteger remaining;
      private final AtomicInteger entered = new AtomicInteger();
      private volatile boolean blocking;
      final CountDownLatch inQuery = new CountDownLatch(1);
      final CountDownLatch release = new CountDownLatch(1);
   }

   private static ViewsheetSandbox sandbox() throws Exception {
      Worksheet ws = new Worksheet();
      int rows = 20;
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "id", "b" };

      for(int i = 1; i <= rows; i++) {
         data[i] = new Object[] { i, i % 3 };
      }

      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "S");
      table.setEmbeddedData(new XEmbeddedTable(new String[] { XSchema.INTEGER, XSchema.INTEGER }, data));
      ws.addAssembly(table);

      Viewsheet vs = new Viewsheet();
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);

      ChartVSAssembly chart = new ChartVSAssembly(vs, CHART);
      chart.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, "S"));
      VSChartInfo info = chart.getVSChartInfo();
      VSChartDimensionRef dim = new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "b")));
      dim.setGroupColumnValue("b");
      info.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("id");
      agg.setFormulaValue("Sum");
      info.addYField(agg);
      info.setChartType(GraphTypes.CHART_BAR);
      info.setRTChartType(GraphTypes.CHART_BAR);
      vs.addAssembly(chart);

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "test/ViewsheetSandboxGraphPairFailureTest", null,
         OrganizationManager.getInstance().getCurrentOrgID());
      ViewsheetSandbox box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null,
                                                  false, entry);
      Field wbox = ViewsheetSandbox.class.getDeclaredField("wbox");
      wbox.setAccessible(true);
      wbox.set(box, new AssetQuerySandbox(ws));
      return box;
   }

   private static final String CHART = "Chart1";
   private ExecutorService executor;
   private Principal oldPrincipal;
   private static volatile Principal principal;
}
