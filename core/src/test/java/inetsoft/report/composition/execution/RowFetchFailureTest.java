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

import inetsoft.mv.MVDef;
import inetsoft.mv.MVDispatcher;
import inetsoft.mv.MVManager;
import inetsoft.mv.data.MV;
import inetsoft.mv.data.MVBuilder;
import inetsoft.mv.fs.FSConfig;
import inetsoft.mv.fs.FSService;
import inetsoft.mv.fs.XFileSystem;
import inetsoft.mv.fs.XServerNode;
import inetsoft.report.CellBinding;
import inetsoft.report.GroupableCellBinding;
import inetsoft.report.TableCellBinding;
import inetsoft.report.TableLayout;
import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.filter.Formula;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.filter.SummaryFilter;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.DistinctTableLens;
import inetsoft.report.lens.JoinTableLens;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.uql.util.TableLoadException;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.*;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.swap.XSwapper;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Bug #77901: a database error raised while the rows of a query result are fetched (here a
 * divide by zero in row 50000 on Derby) stopped loading, and every reader got the rows read
 * before it as if they were the whole result, with no message. The worksheet table, a mirror
 * and a viewsheet table over it now warn with the database error, the failed result is not
 * reused, a scheduled run fails, and an MV is not built from it. Each case runs the real
 * AssetQuerySandbox / AssetDataCache / XSessionManager (data cache on) / JDBCHandler /
 * XNodeTable path; only XEngine.execute is stood in for by JDBCHandler.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  RowFetchFailureTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class RowFetchFailureTest {
   private static final String DB = "memory:rowfetchfailure";
   private static final int ROWS = 60000;
   private static final int FAIL_ROW = 50000;
   private static final String DB_MESSAGE = "Attempt to divide by zero.";
   private static final AtomicInteger RUN = new AtomicInteger();

   @Configuration
   static class JdbcConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      @Bean
      public Plugins plugins(BlobStorageManager blobStorageManager, Cluster cluster,
                             ApplicationEventPublisher eventPublisher)
      {
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster,
                            eventPublisher);
      }

      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         DataSource ds = derby();
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         when(factory.getConnectionPool(any(), any())).thenReturn(ds);
         return factory;
      }

      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory) {
         return new Drivers(plugins, connectionPoolFactory);
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }

      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }

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

      // the data service stands in for XEngine.execute and runs the query on Derby
      @Bean
      public XSessionManager xSessionManager(XSessionService sessionService) throws Exception {
         XDataService dataService = mock(XDataService.class);
         when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
            .thenAnswer(inv -> {
               XQuery query = inv.getArgument(1);
               VariableTable vars = inv.getArgument(2);
               JDBCHandler handler = new JDBCHandler();
               handler.connect(query.getDataSource(), vars);
               return handler.execute(query, vars, inv.getArgument(3), inv.getArgument(5));
            });
         XSessionManager manager =
            new XSessionManager(dataService, sessionService, mock(DataSourceRegistry.class));
         // the shared result cache is on, as on a server
         manager.setCacheData(true);
         return manager;
      }
   }

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table big");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table big (id int, g int)");
         conn.setAutoCommit(false);

         try(java.sql.PreparedStatement insert =
                conn.prepareStatement("insert into big values (?, ?)"))
         {
            for(int i = 1; i <= ROWS; i++) {
               insert.setInt(1, i);
               insert.setInt(2, i % 10);
               insert.addBatch();

               if(i % 5000 == 0) {
                  insert.executeBatch();
               }
            }
         }

         conn.commit();
      }
   }

   @BeforeEach
   void streaming() {
      SreeEnv.setProperty("replet.streaming", "true");
      CoreTool.clearUserMessage();
   }

   @AfterEach
   void reset() {
      SreeEnv.remove("replet.streaming");
      CoreTool.clearUserMessage();
   }

   @Test
   void worksheetTableWarnsAndIsNotReused() throws Exception {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", true);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      TableLens lens = box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());
      assertEquals(FAIL_ROW - 1, dataRows(lens));
      assertWarned();

      // the failed result is run again, not taken from the sandbox
      TableLens again = box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());
      assertNotSame(lens, again);
      dataRows(again);
      assertWarned();
   }

   @Test
   void mirrorWarns() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", true);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M1", t1);
      ws.addAssembly(mirror);
      mirror.update();
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      TableLens lens = box.getTableLens("M1", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());
      assertEquals(FAIL_ROW - 1, dataRows(lens));
      assertWarned();
   }

   @Test
   void viewsheetTableWarns() throws Exception {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", true);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      Object data = vsTable(ws, box).getData("TableV");
      TableLens lens = data instanceof VSDataSet ? ((VSDataSet) data).getTable() : (TableLens) data;

      assertEquals(FAIL_ROW - 1, dataRows(lens));
      assertWarned();
   }

   @Test
   void successfulQueryIsUnchanged() throws Exception {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", false);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      TableLens lens = box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());
      assertEquals(ROWS, dataRows(lens));
      assertNull(CoreTool.getUserMessage());
      assertSame(lens, box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE, new VariableTable()));
   }

   @Test
   void userCancelStaysSilent() throws Exception {
      // a cross join three times the size of big, so the cancel lands while the rows are
      // fetched; a cancel after the end of the rows is a no-op and would prove nothing
      int total = ROWS * 3;
      boolean landed = false;

      for(int i = 0; i < 5 && !landed; i++) {
         CoreTool.clearUserMessage();
         Worksheet ws = new Worksheet();
         sqlTable(ws, "T1", "select a.id, a.g, b.g as x from big a, big b where b.id < 4 and " +
                  "a.id > -" + RUN.incrementAndGet());
         AssetQuerySandbox box = new AssetQuerySandbox(ws);

         TableLens lens = box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());
         XNodeTableLens xlens = (XNodeTableLens) Util.getNestedTable(lens, XNodeTableLens.class);
         xlens.cancel();
         landed = dataRows(lens) < total;

         if(landed) {
            assertTrue(xlens.isCancelled());
            assertNull(xlens.getLoadException());
            assertNull(CoreTool.getUserMessage());
         }
      }

      assertTrue(landed, "the cancel never landed before the end of the rows");
   }

   @Test
   void nonStreamingReportsTheDatabaseError() throws Exception {
      SreeEnv.setProperty("replet.streaming", "false");
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", true);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      TableLens lens = box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());
      UserMessage message = CoreTool.getUserMessage();

      assertNull(lens);
      assertNotNull(message);
      assertTrue(message.getMessage().contains(DB_MESSAGE), message.getMessage());
      assertFalse(message.getMessage().contains(
         Catalog.getCatalog().getString("common.table.queryCancelled")), message.getMessage());
   }

   @Test
   void scheduledRunFails() throws Exception {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", true);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      VariableTable vars = new VariableTable();
      vars.put("__is_scheduler__", Boolean.TRUE);

      RuntimeException ex = assertThrows(RuntimeException.class, () -> dataRows(
         box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE, vars)));
      assertTrue(String.valueOf(ex.getMessage()).contains(DB_MESSAGE), String.valueOf(ex));
   }

   /**
    * An aggregate computed in memory reads the base on a worker, which must not take the
    * worker's load failure for the end of the table in a scheduled run (review r1 I1).
    */
   @Test
   void scheduledSummaryFails() throws Exception {
      TableLens base = baseLens(true, true);
      SummaryFilter summary = summary(base);

      RuntimeException ex = assertThrows(RuntimeException.class, () -> dataRows(summary));
      assertNotNull(TableLoadException.find(ex), String.valueOf(ex));
      assertTrue(String.valueOf(ex.getMessage()).contains(DB_MESSAGE), String.valueOf(ex));
   }

   /**
    * A summary restarts after it reported a base failure (bug #77875). A base that failed to
    * load keeps its load exception, so the next read of a scheduled run fails again instead of
    * taking the rows read so far.
    */
   @Test
   void scheduledSummaryFailsAgainOnTheNextRead() throws Exception {
      TableLens base = baseLens(true, true);
      SummaryFilter summary = summary(base);

      assertThrows(RuntimeException.class, () -> dataRows(summary));
      RuntimeException ex = assertThrows(RuntimeException.class, () -> dataRows(summary));
      assertNotNull(TableLoadException.find(ex), String.valueOf(ex));
   }

   @Test
   void interactiveSummaryWarns() throws Exception {
      TableLens base = baseLens(true, false);
      SummaryFilter summary = summary(base);

      assertTrue(dataRows(summary) > 0);
      assertWarned();
   }

   @Test
   void scheduledSuccessfulSummaryIsUnchanged() throws Exception {
      TableLens base = baseLens(false, true);
      SummaryFilter summary = summary(base);

      assertTrue(dataRows(summary) > 0);
      assertNull(CoreTool.getUserMessage());
   }

   @Test
   void scheduledCrosstabFails() throws Exception {
      TableLens base = baseLens(true, true);
      CrossTabFilter crosstab = new CrossTabFilter(base, 1, 1, 0, new SumFormula());

      RuntimeException ex = assertThrows(RuntimeException.class, () -> dataRows(crosstab));
      assertNotNull(TableLoadException.find(ex), String.valueOf(ex));
   }

   @Test
   void interactiveCrosstabWarns() throws Exception {
      TableLens base = baseLens(true, false);
      CrossTabFilter crosstab = new CrossTabFilter(base, 1, 1, 0, new SumFormula());

      assertTrue(dataRows(crosstab) > 0);
      assertWarned();
   }

   // Bug #77966: a lens that reads its base on a worker (DistinctTableLens, HashJoinTable),
   // and SortFilter, must not take the base's load failure for the end of the table: a
   // scheduled run fails and an interactive reader gets the warning on its own thread

   @Test
   void scheduledDistinctOneColumnFails() throws Exception {
      assertLoadFailure(() -> dataRows(distinctLens(true, true, true)));
   }

   @Test
   void interactiveDistinctOneColumnWarns() throws Exception {
      assertEquals(FAIL_ROW - 1, dataRows(distinctLens(true, false, true)));
      assertWarned();
   }

   @Test
   void scheduledDistinctThreeColumnsFails() throws Exception {
      assertLoadFailure(() -> dataRows(distinctLens(true, true, false)));
   }

   @Test
   void interactiveDistinctThreeColumnsWarns() throws Exception {
      assertEquals(FAIL_ROW - 1, dataRows(distinctLens(true, false, false)));
      assertWarned();
   }

   @Test
   void successfulDistinctIsUnchanged() throws Exception {
      for(boolean oneColumn : new boolean[] { true, false }) {
         assertEquals(ROWS, dataRows(distinctLens(false, true, oneColumn)));
         assertNull(CoreTool.getUserMessage());
      }
   }

   @Test
   void scheduledComboBoxOptionsFail() throws Exception {
      assertLoadFailure(() -> comboBoxOptions(true, true));
   }

   @Test
   void interactiveComboBoxOptionsWarn() throws Exception {
      assertEquals(FAIL_ROW - 1, comboBoxOptions(true, false));
      assertWarned();
   }

   @Test
   void successfulComboBoxOptionsAreUnchanged() throws Exception {
      assertEquals(ROWS, comboBoxOptions(false, true));
      assertNull(CoreTool.getUserMessage());
   }

   @Test
   void scheduledInnerJoinFails() throws Exception {
      assertLoadFailure(() -> dataRows(innerJoinLens(true, true)));
   }

   @Test
   void interactiveInnerJoinWarns() throws Exception {
      assertEquals(FAIL_ROW - 1, dataRows(innerJoinLens(true, false)));
      assertWarned();
   }

   @Test
   void successfulInnerJoinIsUnchanged() throws Exception {
      assertEquals(ROWS, dataRows(innerJoinLens(false, true)));
      assertNull(CoreTool.getUserMessage());
   }

   // Bug #78071: below the NORM memory state JoinTableLens joins with a MergeJoinTable, whose
   // join thread sorts and reads the bases. A base that failed to load must fail a scheduled
   // reader and warn an interactive one, as the hash join does (bug #77966), not end the join
   // with the rows so far (none in a scheduled run).
   private static final int[] JOIN_TYPES = {
      TableAssemblyOperator.INNER_JOIN, TableAssemblyOperator.LEFT_JOIN,
      TableAssemblyOperator.RIGHT_JOIN, TableAssemblyOperator.FULL_JOIN };

   @Test
   void scheduledMergeJoinFails() throws Exception {
      for(int type : JOIN_TYPES) {
         for(boolean failLeft : new boolean[] { true, false }) {
            assertLoadFailure(() -> mergeJoinRows(type, failLeft, !failLeft, true),
                              "join " + type + " failLeft " + failLeft);
         }
      }
   }

   @Test
   void interactiveMergeJoinWarns() throws Exception {
      for(int type : JOIN_TYPES) {
         for(boolean failLeft : new boolean[] { true, false }) {
            String shape = "join " + type + " failLeft " + failLeft;
            // an outer join keeps all the rows of the side that loaded
            boolean keepsAll = type == TableAssemblyOperator.FULL_JOIN ||
               type == TableAssemblyOperator.LEFT_JOIN && !failLeft ||
               type == TableAssemblyOperator.RIGHT_JOIN && failLeft;

            CoreTool.clearUserMessage();
            assertEquals(keepsAll ? ROWS : FAIL_ROW - 1,
                         mergeJoinRows(type, failLeft, !failLeft, false), shape);
            assertWarned(shape);
         }
      }
   }

   @Test
   void successfulMergeJoinIsUnchanged() throws Exception {
      for(int type : JOIN_TYPES) {
         assertEquals(ROWS, mergeJoinRows(type, false, false, true), "join " + type);
         assertNull(CoreTool.getUserMessage(), "join " + type);
      }
   }

   // a MergeJoinTable created under a held script lock joins on the constructing thread
   @Test
   void mergeJoinOnTheConstructingThreadFailsOrWarns() throws Exception {
      for(boolean scheduler : new boolean[] { true, false }) {
         TableLens left = baseLens(true, scheduler);
         TableLens right = baseLens(false, scheduler);
         CoreTool.clearUserMessage();

         withMemoryState(XSwapper.LOW_MEM, () -> {
            JoinTableLens join;
            JavaScriptEngine.pushHeldScriptLock(new ReentrantLock());

            try {
               join = new JoinTableLens(left, right, new int[] { 0 }, new int[] { 0 });
            }
            finally {
               JavaScriptEngine.popHeldScriptLock();
            }

            assertMergeJoin(join);

            if(scheduler) {
               assertLoadFailure(() -> dataRows(join));
            }
            else {
               assertEquals(FAIL_ROW - 1, dataRows(join));
               assertWarned();
            }

            return null;
         });
      }
   }

   // Bug #78071: a calc table formula that reads a table which failed to load must fail a
   // scheduled run, not compute a null value or an "ERROR:" cell from the rows it never read
   private static final String[] CALC_FORMULAS = {
      "sum(data['id'])", "rowList(data, 'g')", "toList(data['g'])", "data['id'].length" };

   @Test
   void scheduledCalcFormulaFails() throws Exception {
      for(String formula : CALC_FORMULAS) {
         TableLens data = baseLens(true, true);
         ViewsheetSandbox box = mock(ViewsheetSandbox.class, RETURNS_DEEP_STUBS);
         CalcTableVSAQuery query = calcQuery(box, data, formula);

         assertLoadFailure(() -> calcCells(query.getTableLens()), formula);
         // the sandbox write lock is still released
         verify(box).unlockWrite();
      }
   }

   @Test
   void interactiveCalcFormulaWarns() throws Exception {
      for(String formula : CALC_FORMULAS) {
         TableLens data = baseLens(true, false);
         CoreTool.clearUserMessage();
         ViewsheetSandbox box = mock(ViewsheetSandbox.class, RETURNS_DEEP_STUBS);
         TableLens lens = calcQuery(box, data, formula).getTableLens();
         Object[][] cells = calcCells(lens);

         assertWarned(formula);

         if(formula.startsWith("sum")) {
            // the sum of the ids read before the failure
            assertEquals((FAIL_ROW - 1) * (double) FAIL_ROW / 2,
                         ((Number) cells[0][0]).doubleValue(), formula);
         }
         else if(formula.startsWith("rowList")) {
            // a row per base row read, then the second row of the default layout
            assertEquals(FAIL_ROW, cells.length, formula);
         }
      }
   }

   @Test
   void successfulCalcFormulaIsUnchanged() throws Exception {
      for(String formula : CALC_FORMULAS) {
         TableLens data = baseLens(false, true);
         CoreTool.clearUserMessage();
         ViewsheetSandbox box = mock(ViewsheetSandbox.class, RETURNS_DEEP_STUBS);
         Object[][] cells = calcCells(calcQuery(box, data, formula).getTableLens());

         assertNull(CoreTool.getUserMessage(), formula);

         switch(formula.substring(0, 3)) {
         case "sum":
            assertEquals(ROWS * (ROWS + 1.0) / 2, ((Number) cells[0][0]).doubleValue());
            break;
         case "row":
            assertEquals(ROWS + 1, cells.length);
            break;
         case "toL":
            // the distinct values of g, 0 to 9
            assertEquals(11, cells.length);
            break;
         default:
            assertEquals(ROWS, ((Number) cells[0][0]).intValue());
         }
      }
   }

   // Bug #78071: the calc formula cases through the real ViewsheetSandbox and the real
   // worksheet table binding, nothing mocked: a scheduled read fails, an interactive one
   // warns with the rows read, and a successful one is unchanged
   @Test
   void calcFormulaThroughTheViewsheetSandbox() throws Exception {
      for(String formula : new String[] { "sum(data['id'])", "rowList(data, 'g')" }) {
         boolean sum = formula.startsWith("sum");
         ViewsheetSandbox scheduled = calcSandbox(true, true, formula);
         assertLoadFailure(() -> calcCells((TableLens) scheduled.getData("Calc1")), formula);

         CoreTool.clearUserMessage();
         Object[][] cells = calcCells((TableLens) calcSandbox(true, false, formula).getData("Calc1"));
         assertWarned(formula);

         if(sum) {
            assertEquals((FAIL_ROW - 1) * (double) FAIL_ROW / 2,
                         ((Number) cells[0][0]).doubleValue(), formula);
         }
         else {
            // a row per base row read, then the second row of the default layout
            assertEquals(FAIL_ROW, cells.length, formula);
         }

         CoreTool.clearUserMessage();
         cells = calcCells((TableLens) calcSandbox(false, true, formula).getData("Calc1"));
         assertNull(CoreTool.getUserMessage(), formula);

         if(sum) {
            assertEquals(ROWS * (ROWS + 1.0) / 2, ((Number) cells[0][0]).doubleValue(), formula);
         }
         else {
            assertEquals(ROWS + 1, cells.length, formula);
         }
      }
   }

   // Bug #78071: a viewsheet onLoad script stops at its read of a table that failed to load
   // in a scheduled run, and the export records that as the script error, instead of the
   // script going on with a null column. Like any onLoad error it does not fail the run. An
   // interactive script reads the rows that were loaded, as before
   @Test
   void onLoadScriptOverAFailedTable() throws Exception {
      String script = "var a = TableV.table['id']; " +
         "throw new Error(a == null ? 'null column' : 'read ' + a.length);";

      for(boolean scheduler : new boolean[] { true, false }) {
         Worksheet ws = new Worksheet();
         sqlTable(ws, "T1", true);
         AssetQuerySandbox box = new AssetQuerySandbox(ws);

         if(scheduler) {
            box.getVariableTable().put("__is_scheduler__", "true");
         }

         ViewsheetSandbox vbox = vsTable(ws, box);
         vbox.getViewsheet().getViewsheetInfo().setOnLoad(script);
         vbox.prepareForExport();
         Exception error = vbox.getExportScriptError();

         assertNotNull(error, "scheduler " + scheduler);

         if(scheduler) {
            assertNotNull(TableLoadException.find(error), String.valueOf(error));
            assertTrue(error.getMessage().contains(DB_MESSAGE), error.getMessage());
         }
         else {
            assertNull(TableLoadException.find(error), String.valueOf(error));
            assertTrue(error.getMessage().contains("read " + (FAIL_ROW - 1)), error.getMessage());
         }
      }
   }

   // Bug #78071: every reader of a failed merge join gets the failure, also a later reader on
   // another thread, and a user cancel of a base of a merge join stays silent
   @Test
   void mergeJoinLaterReaderAndUserCancel() throws Exception {
      for(boolean scheduler : new boolean[] { true, false }) {
         withMemoryState(XSwapper.LOW_MEM, () -> {
            TableLens lens = joinLens(TableAssemblyOperator.INNER_JOIN, true, false, scheduler);
            assertMergeJoin(Util.getNestedTable(lens, JoinTableLens.class));

            for(int i = 0; i < 2; i++) {
               Throwable[] failure = new Throwable[1];
               Thread reader = new Thread(() -> {
                  try {
                     CoreTool.clearUserMessage();

                     if(scheduler) {
                        assertLoadFailure(() -> dataRows(lens), "scheduled reader");
                     }
                     else {
                        assertEquals(FAIL_ROW - 1, dataRows(lens));
                        assertWarned("interactive reader");
                     }
                  }
                  catch(Throwable ex) {
                     failure[0] = ex;
                  }
               });

               reader.start();
               reader.join(60000);
               assertFalse(reader.isAlive(), "reader " + i + " never finished");

               if(failure[0] != null) {
                  throw new AssertionError("reader " + i + ", scheduler " + scheduler,
                                           failure[0]);
               }
            }

            return null;
         });
      }

      for(boolean scheduler : new boolean[] { true, false }) {
         boolean landed = false;

         for(int i = 0; i < 5 && !landed; i++) {
            TableLens left = baseLens(false, scheduler);
            TableLens right = baseLens(false, scheduler);
            XNodeTableLens xlens =
               (XNodeTableLens) Util.getNestedTable(left, XNodeTableLens.class);
            xlens.cancel();
            CoreTool.clearUserMessage();

            int rows = withMemoryState(XSwapper.LOW_MEM, () -> {
               JoinTableLens join =
                  new JoinTableLens(left, right, new int[] { 0 }, new int[] { 0 });
               assertMergeJoin(join);
               return dataRows(join);
            });

            landed = rows < ROWS;

            if(landed) {
               assertTrue(xlens.isCancelled());
               assertNull(xlens.getLoadException());
               assertNull(CoreTool.getUserMessage());
            }
         }

         assertTrue(landed, "the cancel never landed before the end of the rows");
      }
   }

   @Test
   void scheduledSortFails() throws Exception {
      assertLoadFailure(() -> dataRows(sortLens(true, true)));
   }

   @Test
   void interactiveSortWarns() throws Exception {
      TableLens lens = sortLens(true, false);

      assertEquals(FAIL_ROW - 1, dataRows(lens));
      assertWarned();
      // the rows read before the failure are sorted. this worksheet sort's base is not a
      // lens that reports the failure as a cancel, sortOverAFailedBaseIsSorted covers one
      assertSortedDescending(lens, FAIL_ROW - 1);
   }

   // a failed query result reports isCancelled() (bug #77901), which must not be taken for a
   // user cancel that skips the sort
   @Test
   void sortOverAFailedBaseIsSorted() throws Exception {
      TableLens base = Util.getNestedTable(baseLens(true, false), XNodeTableLens.class);
      // read to the end first, so the base has failed when the sort reads it
      dataRows(base);
      assertTrue(((XNodeTableLens) base).isCancelled());
      CoreTool.clearUserMessage();
      SortFilter sort = new SortFilter(base, new int[] { 1 }, false);

      assertEquals(FAIL_ROW - 1, dataRows(sort));
      assertWarned();
      Integer prev = null;
      int rows = 0;

      // g, column 1, descending; getRowCount() is the base's, so the rows are counted here
      for(int r = sort.getHeaderRowCount(); sort.moreRows(r); r++, rows++) {
         int g = ((Number) sort.getObject(r, 1)).intValue();
         assertTrue(prev == null || prev >= g, "not sorted at row " + r);
         prev = g;
      }

      assertEquals(FAIL_ROW - 1, rows);
   }

   @Test
   void successfulSortIsUnchanged() throws Exception {
      TableLens lens = sortLens(false, true);

      assertEquals(ROWS, dataRows(lens));
      assertNull(CoreTool.getUserMessage());
      assertSortedDescending(lens, ROWS);
   }

   // Bug #77966: every reader of a worker lens over a failed fetch gets the failure, not only
   // the first one, each reader gets one copy of the warning also through stacked worker
   // lenses, and a user cancel of the fetch stays silent through each lens

   @Test
   void concurrentReadersOfAWorkerLensAllGetTheFailure() throws Exception {
      for(boolean scheduler : new boolean[] { false, true }) {
         for(String shape : new String[] { "distinct1", "distinct3", "sort", "join" }) {
            TableLens base = baseLens(true, scheduler);
            TableLens other = shape.equals("join") ? baseLens(false, scheduler) : null;
            TableLens lens = workerLens(shape, base, other);
            int readers = 4;
            CyclicBarrier barrier = new CyclicBarrier(readers);
            String[] results = new String[readers];
            Thread[] threads = new Thread[readers];

            for(int i = 0; i < readers; i++) {
               int idx = i;
               threads[i] = new Thread(() -> {
                  try {
                     CoreTool.clearUserMessage();
                     barrier.await();
                     dataRows(lens);
                     UserMessage message = CoreTool.getUserMessage();
                     results[idx] = message == null ? null : message.getMessage();
                  }
                  catch(Throwable ex) {
                     results[idx] = TableLoadException.find(ex) != null ?
                        "TableLoadException" : String.valueOf(ex);
                  }
               });
               threads[i].start();
            }

            for(Thread thread : threads) {
               thread.join(60000);
               assertFalse(thread.isAlive(), shape + " reader hung");
            }

            String expected = scheduler ? "TableLoadException" :
               Catalog.getCatalog().getString("common.table.getDataFailed") + ": " + DB_MESSAGE;

            for(String result : results) {
               assertEquals(expected, result, shape + " scheduler=" + scheduler);
            }
         }
      }
   }

   @Test
   void stackedWorkerLensesWarnOnce() throws Exception {
      // a sort over a multi-column distinct over a join whose two sides both fail
      TableLens join = new JoinTableLens(baseLens(true, false), baseLens(true, false),
                                         new int[] { 0 }, new int[] { 0 });
      TableLens sort = new SortFilter(new DistinctTableLens(join, new int[] { 0, 1 }, false),
                                      new int[] { 1 }, false);
      CoreTool.clearUserMessage();

      for(int i = 0; i < 3; i++) {
         assertEquals(FAIL_ROW - 1, dataRows(sort));
      }

      assertWarned();
   }

   @Test
   void userCancelStaysSilentThroughWorkerLenses() throws Exception {
      for(boolean scheduler : new boolean[] { false, true }) {
         for(String shape : new String[] { "distinct1", "distinct3", "sort", "join" }) {
            CoreTool.clearUserMessage();
            Worksheet ws = new Worksheet();
            // three times the size of big, so the cancel lands while the rows are fetched
            sqlTable(ws, "T1", "select a.id, a.g, b.g as x from big a, big b where b.id < 4 " +
                     "and a.id > -" + RUN.incrementAndGet());
            AssetQuerySandbox box = new AssetQuerySandbox(ws);
            VariableTable vars = new VariableTable();

            if(scheduler) {
               vars.put("__is_scheduler__", Boolean.TRUE);
            }

            TableLens base = box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE, vars);
            XNodeTableLens xlens = (XNodeTableLens) Util.getNestedTable(base, XNodeTableLens.class);
            TableLens other = shape.equals("join") ? baseLens(false, scheduler) : null;
            TableLens lens = workerLens(shape, base, other);
            xlens.cancel();

            dataRows(lens);
            assertTrue(xlens.getRowCount() - 1 < ROWS * 3, shape + " the cancel did not land");
            assertNull(xlens.getLoadException());
            assertNull(CoreTool.getUserMessage(), shape + " scheduler=" + scheduler);
         }
      }
   }

   private static TableLens workerLens(String shape, TableLens base, TableLens other) {
      switch(shape) {
      case "distinct1":
         return new DistinctTableLens(base, new int[] { 1 }, true);
      case "distinct3":
         return new DistinctTableLens(base, new int[] { 0, 1, 2 }, false);
      case "sort":
         return new SortFilter(base, new int[] { 1, 0 }, false);
      case "join":
         return new JoinTableLens(base, other, new int[] { 0 }, new int[] { 0 });
      default:
         throw new IllegalArgumentException(shape);
      }
   }

   private static void assertLoadFailure(Executable read) {
      assertLoadFailure(read, "");
   }

   private static void assertLoadFailure(Executable read, String shape) {
      RuntimeException ex = assertThrows(RuntimeException.class, read, shape);
      assertNotNull(TableLoadException.find(ex), shape + ": " + ex);
      assertTrue(String.valueOf(ex.getMessage()).contains(DB_MESSAGE), shape + ": " + ex);
   }

   // the joined rows of T1 joined with T2 on id by a MergeJoinTable, as JoinTableLens creates
   // below the NORM memory state; the state stays low for the read, which may create the
   // join again
   private static int mergeJoinRows(int type, boolean failLeft, boolean failRight,
                                    boolean scheduler)
      throws Exception
   {
      return withMemoryState(XSwapper.LOW_MEM, () -> {
         TableLens lens = joinLens(type, failLeft, failRight, scheduler);
         assertMergeJoin(Util.getNestedTable(lens, JoinTableLens.class));
         return dataRows(lens);
      });
   }

   private static void assertMergeJoin(TableLens join) throws Exception {
      assertNotNull(join, "no join");
      Field field = JoinTableLens.class.getDeclaredField("delegate");
      field.setAccessible(true);
      assertEquals("MergeJoinTable", field.get(join).getClass().getSimpleName());
   }

   // run with the memory state of the swapper fixed, then restore it
   private static <T> T withMemoryState(int state, Callable<T> call) throws Exception {
      XSwapper swapper = XSwapper.getSwapper();
      Field stateField = XSwapper.class.getDeclaredField("cachedState");
      Field timeField = XSwapper.class.getDeclaredField("stateTS");
      stateField.setAccessible(true);
      timeField.setAccessible(true);
      Object oldState = stateField.get(swapper);
      Object oldTime = timeField.get(swapper);

      try {
         stateField.set(swapper, state);
         // a reading time in the future, so the state is not read again
         timeField.set(swapper, Long.MAX_VALUE / 2);
         return call.call();
      }
      finally {
         stateField.set(swapper, oldState);
         timeField.set(swapper, oldTime);
      }
   }

   // a calc table whose cell A1 is the formula, expanded vertically unless it is a sum, over
   // the data lens; as CalcTableVSAQuerySwapLostTest, only the sandbox is mocked
   private static CalcTableVSAQuery calcQuery(ViewsheetSandbox box, TableLens data,
                                              String formula)
   {
      Viewsheet vs = new Viewsheet();
      CalcTableVSAssembly cassembly = new CalcTableVSAssembly(vs, "Calc1");
      vs.addAssembly(cassembly);
      TableLayout layout = cassembly.getTableLayout();
      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_FORMULA, formula);

      if(!formula.startsWith("sum")) {
         cell.setExpansion(GroupableCellBinding.EXPAND_V);
      }

      layout.setCellBinding(0, 0, cell);
      GraalJavaScriptEnv env = new GraalJavaScriptEnv();
      env.init();
      when(box.getViewsheet()).thenReturn(vs);
      when(box.getVariableTable()).thenReturn(new VariableTable());
      when(box.getID()).thenReturn("vs1");
      when(box.getScope().getScriptEnv()).thenReturn(env);
      TableAssembly table = mock(TableAssembly.class);

      return new CalcTableVSAQuery(box, "Calc1", false) {
         @Override
         public TableAssembly getTableAssembly() {
            return table;
         }

         @Override
         protected TableLens getTableLens(TableAssembly table) {
            return data;
         }
      };
   }

   // the cells of a calc table, none of which may be an error
   private static Object[][] calcCells(TableLens lens) {
      assertNotNull(lens, "no calc table, see the log");
      lens.moreRows(XTable.EOT);
      Object[][] cells = new Object[lens.getRowCount()][lens.getColCount()];

      for(int r = 0; r < cells.length; r++) {
         for(int c = 0; c < cells[r].length; c++) {
            cells[r][c] = lens.getObject(r, c);
            assertFalse(String.valueOf(cells[r][c]).startsWith("ERROR:"),
                        "cell " + r + "," + c + ": " + cells[r][c]);
         }
      }

      return cells;
   }

   // a viewsheet with the calc table Calc1 bound to the worksheet table T1, whose cell A1 is
   // the formula, expanded vertically unless it is a sum
   private static ViewsheetSandbox calcSandbox(boolean fail, boolean scheduler, String formula)
      throws Exception
   {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", fail);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      if(scheduler) {
         box.getVariableTable().put("__is_scheduler__", "true");
      }

      Viewsheet vs = viewsheet(ws);
      CalcTableVSAssembly calc = new CalcTableVSAssembly(vs, "Calc1");
      calc.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, "T1"));
      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_FORMULA, formula);

      if(!formula.startsWith("sum")) {
         cell.setExpansion(GroupableCellBinding.EXPAND_V);
      }

      calc.getTableLayout().setCellBinding(0, 0, cell);
      vs.addAssembly(calc);
      return viewsheetSandbox(vs, box);
   }

   // g descending, the sort column
   private static void assertSortedDescending(TableLens lens, int rows) {
      int col = Util.findColumn(lens, "g");
      assertTrue(col >= 0, "no column g");
      Integer prev = null;
      int count = 0;

      // the rows are counted here, a getRowCount() of a sort is the base's
      for(int r = lens.getHeaderRowCount(); lens.moreRows(r); r++, count++) {
         Integer g = ((Number) lens.getObject(r, col)).intValue();
         assertTrue(prev == null || prev >= g, "not sorted at row " + r);
         prev = g;
      }

      assertEquals(rows, count);
   }

   // T1 with Distinct and Merge SQL off, so the distinct rows are found in memory: one
   // visible column (hash distinct) or three (sort distinct)
   private static TableLens distinctLens(boolean fail, boolean scheduler, boolean oneColumn)
      throws Exception
   {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", fail);

      if(oneColumn) {
         ColumnSelection cols = t1.getColumnSelection(false);
         ((ColumnRef) cols.getAttribute(1)).setVisible(false);
         ((ColumnRef) cols.getAttribute(2)).setVisible(false);
         t1.setColumnSelection(cols, false);
      }

      t1.setSQLMergeable(false);
      t1.setDistinct(true);
      TableLens lens = runtimeLens(ws, "T1", scheduler);
      assertNotNull(Util.getNestedTable(lens, DistinctTableLens.class));
      return lens;
   }

   // T1 sorted on g descending with Merge SQL off, so the rows are sorted in memory
   private static TableLens sortLens(boolean fail, boolean scheduler) throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", fail);
      SortRef sort = new SortRef(t1.getColumnSelection(false).getAttribute("g"));
      sort.setOrder(XConstants.SORT_DESC);
      SortInfo sortInfo = new SortInfo();
      sortInfo.addSort(sort);
      t1.setSortInfo(sortInfo);
      t1.setSQLMergeable(false);
      TableLens lens = runtimeLens(ws, "T1", scheduler);
      assertNotNull(Util.getNestedTable(lens, SortFilter.class));
      return lens;
   }

   private static TableLens innerJoinLens(boolean fail, boolean scheduler) throws Exception {
      return joinLens(TableAssemblyOperator.INNER_JOIN, fail, false, scheduler);
   }

   // T1 joined with T2 on id with Merge SQL off, so the join is done in memory
   private static TableLens joinLens(int type, boolean failLeft, boolean failRight,
                                     boolean scheduler)
      throws Exception
   {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", failLeft);
      SQLBoundTableAssembly t2 = sqlTable(ws, "T2", failRight);
      t1.setSQLMergeable(false);
      t2.setSQLMergeable(false);
      TableAssemblyOperator operator = new TableAssemblyOperator();
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setOperation(type);
      op.setLeftTable("T1");
      op.setRightTable("T2");
      op.setLeftAttribute(t1.getColumnSelection(false).getAttribute("id"));
      op.setRightAttribute(t2.getColumnSelection(false).getAttribute("id"));
      operator.addOperator(op);
      RelationalJoinTableAssembly join = new RelationalJoinTableAssembly(
         ws, "J1", new TableAssembly[] { t1, t2 }, new TableAssemblyOperator[] { operator });
      ws.addAssembly(join);
      join.update();
      join.setSQLMergeable(false);
      AssetQuerySandbox init = new AssetQuerySandbox(ws);
      AssetEventUtil.initColumnSelection(init, join);
      init.dispose();
      CoreTool.clearUserMessage();

      TableLens lens = runtimeLens(ws, "J1", scheduler);
      assertNotNull(Util.getNestedTable(lens, JoinTableLens.class));
      return lens;
   }

   private static TableLens runtimeLens(Worksheet ws, String name, boolean scheduler)
      throws Exception
   {
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      VariableTable vars = new VariableTable();

      if(scheduler) {
         vars.put("__is_scheduler__", Boolean.TRUE);
      }

      TableLens lens = box.getTableLens(name, AssetQuerySandbox.RUNTIME_MODE, vars);
      assertNotNull(lens, "the query failed, see the log");
      return lens;
   }

   // the options of a combo box bound to T1.id, made distinct in memory by InputVSAQuery
   private static int comboBoxOptions(boolean fail, boolean scheduler) throws Exception {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", fail);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      if(scheduler) {
         box.getVariableTable().put("__is_scheduler__", "true");
      }

      Viewsheet vs = viewsheet(ws);
      ComboBoxVSAssembly combo = new ComboBoxVSAssembly(vs, "C1");
      combo.setSourceType(ListInputVSAssembly.BOUND_SOURCE);
      ListBindingInfo binding = new ListBindingInfo();
      binding.setTableName("T1");
      binding.setValueColumn(new ColumnRef(new AttributeRef(null, "id")));
      binding.setLabelColumn(new ColumnRef(new AttributeRef(null, "id")));
      combo.setListBindingInfo(binding);
      vs.addAssembly(combo);
      ViewsheetSandbox vbox = viewsheetSandbox(vs, box);
      Object data = new InputVSAQuery(vbox, "C1").getData();

      assertInstanceOf(ListData.class, data);
      return ((ListData) data).getValues().length;
   }

   private static TableLens baseLens(boolean fail, boolean scheduler) throws Exception {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", fail);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      VariableTable vars = new VariableTable();

      if(scheduler) {
         vars.put("__is_scheduler__", Boolean.TRUE);
      }

      TableLens base = box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE, vars);
      assertNotNull(base, "the query failed, see the log");
      return base;
   }

   // the sum of id by g
   private static SummaryFilter summary(TableLens base) {
      return new SummaryFilter(base, new int[] { 1 }, new int[] { 0 },
                               new Formula[] { new SumFormula() }, null);
   }

   @Test
   void mvIsNotBuiltFromAFailedRead() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", true);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      // as MVDispatcher.getData reads the MV data
      XTable data = AssetDataCache.getCache().getData(
         null, t1, box, null, AssetQuerySandbox.RUNTIME_MODE, false,
         System.currentTimeMillis(), box.getQueryManager());
      data.moreRows(XTable.EOT);

      MVDef def = mock(MVDef.class);
      when(def.getName()).thenReturn("mv77901");
      MVDispatcher dispatcher = new MVDispatcher(def);
      Field field = MVDispatcher.class.getDeclaredField("data");
      field.setAccessible(true);
      field.set(dispatcher, data);

      assertTrue(dispatcher.isCanceled());
   }

   // the real MVSingleDispatcher.dispatch0 stops before it saves the MV files of a failed
   // read, and reaches the save for a successful one
   @Test
   void mvCreationDoesNotSaveAFailedRead() throws Exception {
      for(boolean fail : new boolean[] { true, false }) {
         Worksheet ws = new Worksheet();
         SQLBoundTableAssembly t1 = sqlTable(ws, "T1", fail);
         AssetQuerySandbox box = new AssetQuerySandbox(ws);
         // as MVDispatcher.getData reads the MV data, which dispatch0 then finds set
         XTable data = AssetDataCache.getCache().getData(
            null, t1, box, null, AssetQuerySandbox.RUNTIME_MODE, false,
            System.currentTimeMillis(), box.getQueryManager());

         MVDef def = mock(MVDef.class);
         when(def.getName()).thenReturn("mv77901");
         // MVSingleDispatcher and the methods stubbed below are not visible from this package
         Constructor<?> ctor = Class.forName("inetsoft.mv.MVSingleDispatcher")
            .getDeclaredConstructor(MVDef.class);
         ctor.setAccessible(true);
         MVDispatcher dispatcher = (MVDispatcher) spy(ctor.newInstance(def));
         Field field = MVDispatcher.class.getDeclaredField("data");
         field.setAccessible(true);
         field.set(dispatcher, data);

         Method getBuilder = MVDispatcher.class.getDeclaredMethod("getMVBuilder");
         getBuilder.setAccessible(true);
         Method saveTempFile = MVDispatcher.class.getDeclaredMethod("saveTempFile",
                                                                    MVBuilder.class);
         saveTempFile.setAccessible(true);
         Method dispatch0 = MVDispatcher.class.getDeclaredMethod("dispatch0");
         dispatch0.setAccessible(true);

         MVBuilder builder = mock(MVBuilder.class);
         when(builder.getMV()).thenReturn(mock(MV.class));
         // like the real MVBuilder, it reads all the rows
         getBuilder.invoke(doAnswer(inv -> {
            data.moreRows(XTable.EOT);
            return builder;
         }).when(dispatcher));
         RuntimeException saved = new RuntimeException("MV files saved");
         saveTempFile.invoke(doThrow(saved).when(dispatcher), builder);

         XServerNode server = mock(XServerNode.class);
         when(server.getConfig()).thenReturn(mock(FSConfig.class));
         when(server.getFSystem()).thenReturn(mock(XFileSystem.class));
         Throwable thrown = null;

         try(MockedStatic<FSService> fsService = mockStatic(FSService.class)) {
            fsService.when(FSService::getServer).thenReturn(server);
            dispatch0.invoke(dispatcher);
         }
         catch(InvocationTargetException ex) {
            thrown = ex.getCause();
         }

         if(fail) {
            assertInstanceOf(CancelledException.class, thrown);
            saveTempFile.invoke(verify(dispatcher, never()), builder);
         }
         else {
            assertSame(saved, thrown);
         }
      }
   }

   private static int dataRows(TableLens lens) {
      assertNotNull(lens, "the query failed, see the log");
      lens.moreRows(XTable.EOT);
      return lens.getRowCount() - lens.getHeaderRowCount();
   }

   private static void assertWarned() {
      assertWarned("");
   }

   private static void assertWarned(String shape) {
      UserMessage message = CoreTool.getUserMessage();
      assertNotNull(message, shape + ": no message for the failed fetch");
      assertEquals(Catalog.getCatalog().getString("common.table.getDataFailed") + ": " +
                   DB_MESSAGE, message.getMessage(), shape);
   }

   // a distinct where clause per table, so no case reads a cached result of another
   private static SQLBoundTableAssembly sqlTable(Worksheet ws, String name, boolean fail)
      throws Exception
   {
      return sqlTable(ws, name, "select big.id, big.g, " +
         (fail ? "1/(big.id-" + FAIL_ROW + ")" : "big.g") + " as x from big where big.id > -" +
         RUN.incrementAndGet());
   }

   // the query must return the columns id, g and x
   private static SQLBoundTableAssembly sqlTable(Worksheet ws, String name, String text)
      throws Exception
   {
      UniformSQL usql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class,
                                                        long.class);
      parse.setAccessible(true);
      // parse(String, int, long) is package private, PARSE_ALL is 0
      parse.invoke(usql, text, 0, 4000L);
      usql.setSQLString(text, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), text);

      JDBCDataSource ds = dataSource();
      usql.setDataSource(ds);
      JDBCQuery query = new JDBCQuery();
      query.setName(name);
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, name);
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);

      ColumnSelection columns = new ColumnSelection();

      for(String column : new String[] { "id", "g", "x" }) {
         ColumnRef ref = new ColumnRef(new AttributeRef(column));
         ref.setDataType(XSchema.INTEGER);
         columns.addAttribute(ref);
      }

      table.setColumnSelection(columns, false);
      table.setColumnSelection((ColumnSelection) columns.clone(), true);
      ws.addAssembly(table);
      return table;
   }

   private static ViewsheetSandbox vsTable(Worksheet ws, AssetQuerySandbox box)
      throws Exception
   {
      Viewsheet vs = viewsheet(ws);
      TableVSAssembly table = new TableVSAssembly(vs, "TableV");
      table.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, "T1"));
      ColumnSelection cols = new ColumnSelection();

      for(String name : new String[] { "id", "g", "x" }) {
         cols.addAttribute(new ColumnRef(new AttributeRef(null, name)));
      }

      table.setColumnSelection(cols);
      vs.addAssembly(table);
      return viewsheetSandbox(vs, box);
   }

   private static Viewsheet viewsheet(Worksheet ws) throws Exception {
      Viewsheet vs = new Viewsheet();
      // as PooledWorksheetOpenReadAheadTest: the base worksheet is wired directly
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);
      return vs;
   }

   private static ViewsheetSandbox viewsheetSandbox(Viewsheet vs, AssetQuerySandbox box)
      throws Exception
   {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/RowFetchFailureTest",
         null, OrganizationManager.getInstance().getCurrentOrgID());
      ViewsheetSandbox vbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null,
                                                   false, entry);
      Field wbox = ViewsheetSandbox.class.getDeclaredField("wbox");
      wbox.setAccessible(true);
      wbox.set(vbox, box);
      return vbox;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("rowfetchfailure");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
