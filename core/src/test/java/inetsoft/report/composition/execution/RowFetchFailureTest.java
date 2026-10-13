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
import inetsoft.mv.MVLoadFailedException;
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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.filter.Formula;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.filter.SummaryFilter;
import inetsoft.report.internal.Util;
import inetsoft.report.io.viewsheet.excel.CSVUtil;
import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
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
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;
import java.util.zip.ZipInputStream;

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

   /**
    * The query of a failing table that fails before its table is returned, or {@code null}
    * for one that fails while its rows are fetched. It takes the run number of the distinct
    * where clause.
    */
   private static String failingQuery;

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
      failingQuery = null;
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

   // Bug #78071: a reader that swallows the load failure of a scheduled calc table, e.g. the
   // large-table precheck of an Excel export, must not leave the sandbox a cached "no data":
   // every later read fails with the load failure too
   @Test
   void scheduledCalcFailsAgainAfterASwallowedRead() throws Exception {
      for(String formula : new String[] { "sum(data['id'])", "rowList(data, 'g')" }) {
         ViewsheetSandbox box = calcSandbox(true, true, formula);
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
         when(rvs.getViewsheet()).thenReturn(box.getViewsheet());

         // as ViewsheetAction does before an Excel export
         assertFalse(CSVUtil.hasLargeDataTable(rvs), formula);

         assertLoadFailure(() -> calcCells(box.getVSTableLens("Calc1", false)), formula);
         assertLoadFailure(() -> calcCells((TableLens) box.getData("Calc1")), formula);
         // and again, a failed read is not cached either
         assertLoadFailure(() -> calcCells((TableLens) box.getData("Calc1")), formula);
      }
   }

   // the export after the precheck fails with the database error, not with no calc table
   @Test
   void scheduledCsvExportAfterThePrecheckFails() throws Exception {
      ViewsheetSandbox box = calcSandbox(true, true, "sum(data['id'])");
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(rvs.getViewsheet()).thenReturn(box.getViewsheet());
      assertFalse(CSVUtil.hasLargeDataTable(rvs));

      CSVVSExporter exporter = new CSVVSExporter(new ByteArrayOutputStream(), null);
      assertLoadFailure(() -> {
         exporter.export(box, "Calc", null);
         exporter.write();
      });
   }

   // Bug #78071: a viewsheet onLoad script stops at its read of a table that failed to load
   // in a scheduled run, and the export records that as the script error, instead of the
   // script going on with a null column. Unlike other onLoad errors, a load failure fails the
   // export (#78445). An interactive script reads the rows that were loaded, as before
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

         if(scheduler) {
            assertLoadFailure(assertThrows(TableLoadException.class, vbox::prepareForExport),
                              DB_MESSAGE);
         }
         else {
            vbox.prepareForExport();
         }

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

   // Bug #78083: a range condition that reads another table, which failed to load in a
   // scheduled run, must fail the calc read. The swallowed condition read null, which the range
   // took as "selected", so the cell summed every row of the good table. Interactive and
   // successful runs select no row (TableV has rows), as before
   @Test
   void scriptReadInARangeConditionOverAFailedTable() throws Exception {
      String formula = "sum(data['id?TableV.table.length < 0'])";
      assertLoadFailure(() -> calcCells(
         (TableLens) conditionSandbox(true, true, formula).getData("Calc1")), formula);

      for(boolean fail : new boolean[] { true, false }) {
         CoreTool.clearUserMessage();
         Object[][] cells = calcCells(
            (TableLens) conditionSandbox(fail, !fail, formula).getData("Calc1"));
         assertNull(cells[0][0], "fail " + fail);
      }
   }

   // Bug #78083: a range condition of a summary formula over a table that failed to load in a
   // scheduled run fails the calc read instead of giving a null cell
   @Test
   void scriptReadInASummaryRangeOverAFailedTable() throws Exception {
      for(String formula : new String[] { "sum(data, 'id?g > 3')", "none(data, 'id?g > 3')" }) {
         boolean sum = formula.startsWith("sum");
         assertLoadFailure(() -> calcCells(
            (TableLens) calcSandbox(true, true, formula).getData("Calc1")), formula);

         CoreTool.clearUserMessage();
         Object[][] cells = calcCells(
            (TableLens) calcSandbox(true, false, formula).getData("Calc1"));
         assertWarned(formula);
         // the sum of the ids with g > 3 of the rows read before the failure
         assertEquals(sum ? 7.50045E8 : FAIL_ROW - 1, ((Number) cells[0][0]).doubleValue(),
                      formula);

         CoreTool.clearUserMessage();
         cells = calcCells((TableLens) calcSandbox(false, true, formula).getData("Calc1"));
         assertNull(CoreTool.getUserMessage(), formula);
         assertEquals(sum ? 1.080054E9 : ROWS - 1, ((Number) cells[0][0]).doubleValue(),
                      formula);
      }
   }

   // Bug #78083: an onLoad script that reads a calc table, or an output bound to a table, which
   // failed to load in a scheduled run stops at that read, as for a plain table (#78071),
   // instead of going on with a null table or value
   @Test
   void scriptReadOfACalcTableOrOutputOverAFailedTable() throws Exception {
      String[] scripts = {
         "var t = Calc1.table; throw new Error(t == null ? 'null' : 'len ' + t.length);",
         "var t = Calc1.data; throw new Error(t == null ? 'null' : 'len ' + t.length);",
         "var v = Text1.value; throw new Error('v=' + v);" };
      String[] successful = { "len 2", "len 2", "v=1800030000" };

      // every script is checked, also when an earlier one fails
      assertAll(IntStream.range(0, scripts.length).mapToObj(i -> (Executable) () -> {
         String script = scripts[i];
         Exception error = onLoadError(true, true, script);
         assertNotNull(error, script);
         assertNotNull(TableLoadException.find(error), script + ": " + error);
         assertTrue(error.getMessage().contains(DB_MESSAGE), script + ": " + error);

         // interactive: the old warn-and-partial-rows behaviour, no load failure
         error = onLoadError(true, false, script);
         assertNotNull(error, script);
         assertNull(TableLoadException.find(error), script + ": " + error);

         error = onLoadError(false, true, script);
         assertNotNull(error, script);
         assertNull(TableLoadException.find(error), script + ": " + error);
         assertTrue(error.getMessage().contains(successful[i]), script + ": " + error);
      }));
   }

   // Bug #78083: a scheduled CSV export of a calc table whose range condition reads a hidden
   // table that failed to load fails, instead of exporting the sum of every row as if the
   // condition selected them all. The hidden table is not exported, so its own read does not
   // fail the export. Interactive and successful exports are unchanged (no row selected)
   @Test
   void scheduledExportOfARangeConditionOverAHiddenFailedTable() throws Exception {
      String formula = "sum(data['id?TableV.table.length < 0'])";

      for(int run = 0; run < 3; run++) {
         boolean fail = run != 2;
         boolean scheduler = run != 1;
         ViewsheetSandbox box = conditionSandbox(fail, scheduler, formula);
         VSAssembly hidden = (VSAssembly) box.getViewsheet().getAssembly("TableV");
         hidden.getVSAssemblyInfo().setVisibleValue(String.valueOf(VSAssembly.ALWAYS_HIDE));
         assertFalse(hidden.isVisible());
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         CSVVSExporter exporter = new CSVVSExporter(out, null);
         CoreTool.clearUserMessage();

         if(fail && scheduler) {
            assertLoadFailure(() -> {
               exporter.export(box, "Calc", null);
               exporter.write();
            });
            continue;
         }

         exporter.export(box, "Calc", null);
         exporter.write();
         String csv = "";

         try(ZipInputStream zip =
                new ZipInputStream(new ByteArrayInputStream(out.toByteArray())))
         {
            while(zip.getNextEntry() != null) {
               csv += new String(zip.readAllBytes(), StandardCharsets.UTF_8);
            }
         }

         // the empty cell of Calc1, no row of T2 selected
         assertFalse(csv.contains("1800030000"), "run " + run + ": " + csv);
         assertTrue(csv.startsWith(","), "run " + run + ": " + csv);

         if(fail) {
            assertWarned();
         }
         else {
            assertNull(CoreTool.getUserMessage());
         }
      }
   }

   // Bug #78411: a query that fails before its table is returned (at statement execute, as
   // opposed to a row fetch) failed a scheduled run with the raw database error, which every
   // reader that turns its other failures into a null value swallowed, and ViewsheetSandbox
   // cached as no data. A missing column or table was swallowed into an empty table by
   // AssetQuery itself, so a scheduled export succeeded with no rows. Each now reaches the
   // reader as a load failure with the database message, as a row fetch error does
   private static final String[][] EXECUTE_FAILURES = {
      // Derby reads every row for the group by before it returns the first one
      { "select max(big.id) as id, big.g, sum(1/(big.id-" + FAIL_ROW + ")) as x from big " +
        "where big.id > -%d group by big.g", DB_MESSAGE },
      { "select big.id, big.g, big.nosuch as x from big where big.id > -%d",
        "Column 'BIG.NOSUCH' is either not in any table" },
      { "select nt.id, nt.g, nt.x from nosuchtable nt where nt.id > -%d",
        "Table/View 'NOSUCHTABLE' does not exist." } };

   @Test
   void scheduledWorksheetReadOfAnExecuteFailureFails() throws Exception {
      for(String[] failure : EXECUTE_FAILURES) {
         failingQuery = failure[0];
         String message = failure[1];

         Worksheet ws = new Worksheet();
         sqlTable(ws, "T1", true);
         AssetQuerySandbox box = new AssetQuerySandbox(ws);
         // as ScheduleTask sets it
         VariableTable vars = new VariableTable();
         vars.put("__is_scheduler__", Boolean.TRUE);

         Exception ex = assertThrows(Exception.class, () -> dataRows(
            box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE, vars)), message);
         TableLoadException loadFailure = TableLoadException.find(ex);
         assertNotNull(loadFailure, message + ": " + ex);
         // the database message, not the text of a wrapping exception
         assertTrue(loadFailure.getMessage().startsWith(message), loadFailure.getMessage());
         assertTrue(hasSqlException(loadFailure), message + ": " + loadFailure.getCause());

         // interactive: no rows and the warning, as before
         CoreTool.clearUserMessage();
         Worksheet ws2 = new Worksheet();
         sqlTable(ws2, "T1", true);
         TableLens lens = new AssetQuerySandbox(ws2)
            .getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());
         assertTrue(lens == null || dataRows(lens) == 0, message);
         UserMessage warning = CoreTool.getUserMessage();
         assertNotNull(warning, message);
         assertTrue(warning.getMessage().contains(message), warning.getMessage());
      }
   }

   @Test
   void scheduledScriptReadsAndExportOfAnExecuteFailureFail() throws Exception {
      String script =
         "var t = TableV.table; throw new Error(t == null ? 'null' : 'len ' + t.length);";

      for(String[] failure : EXECUTE_FAILURES) {
         failingQuery = failure[0];
         String message = failure[1];

         ViewsheetSandbox vbox = executeFailureSandbox(true, script);
         assertLoadFailure(assertThrows(TableLoadException.class, vbox::prepareForExport),
                           message);
         assertLoadFailure(vbox.getExportScriptError(), message);

         // the failed read was not cached as no data: a later read fails again, and the export
         // fails with the database error, not a NullPointerException over the cached null
         assertLoadFailure(assertThrows(Exception.class, () -> vbox.getData("TableV")), message);
         CSVVSExporter exporter = new CSVVSExporter(new ByteArrayOutputStream(), null);
         assertLoadFailure(assertThrows(Exception.class, () -> {
            exporter.export(vbox, "TableV", null);
            exporter.write();
         }), message);

         // the export with no script fails with the load failure too
         ViewsheetSandbox exportBox = executeFailureSandbox(true, null);
         CSVVSExporter exporter2 = new CSVVSExporter(new ByteArrayOutputStream(), null);
         assertLoadFailure(assertThrows(Exception.class, () -> {
            exporter2.export(exportBox, "TableV", null);
            exporter2.write();
         }), message);

         // interactive: the script reads no load failure, the export has no rows, as before
         ViewsheetSandbox interactive = executeFailureSandbox(false, script);
         interactive.prepareForExport();
         Exception error = interactive.getExportScriptError();
         assertNotNull(error, message);
         assertNull(TableLoadException.find(error), message + ": " + error);
         CSVVSExporter exporter3 = new CSVVSExporter(new ByteArrayOutputStream(), null);
         exporter3.export(executeFailureSandbox(false, null), "TableV", null);
         exporter3.write();
      }
   }

   // Bug #78445: a scheduled CSV export of a viewsheet whose onLoad reads a hidden table that
   // failed to load fails with the database error, instead of finishing with an empty file and
   // only a logged script error. The hidden table is not exported, so the onLoad read is the
   // only reader. An interactive export, and a scheduled export whose onLoad fails for another
   // reason, still complete with the script error recorded for the warning (#76780)
   @Test
   void scheduledExportOfAnOnLoadReadOfAHiddenFailedTableFails() throws Exception {
      String read = "var n = TableV.table.length; parameter.r = 'rows=' + n;";

      ViewsheetSandbox scheduled = hiddenFailedTableSandbox(true, read);
      CSVVSExporter exporter = new CSVVSExporter(new ByteArrayOutputStream(), null);
      RuntimeException ex = assertThrows(RuntimeException.class, () -> {
         exporter.export(scheduled, "Text", null);
         exporter.write();
      });
      assertLoadFailure(ex, DB_MESSAGE);
      assertTrue(hasSqlException(TableLoadException.find(ex)), String.valueOf(ex));

      // interactive: the script reads the rows that were loaded and the export completes
      ViewsheetSandbox interactive = hiddenFailedTableSandbox(false, read);
      CSVVSExporter exporter2 = new CSVVSExporter(new ByteArrayOutputStream(), null);
      exporter2.export(interactive, "Text", null);
      exporter2.write();
      assertNull(interactive.getExportScriptError());
      // the header row and the rows read before the failure
      assertEquals("rows=" + FAIL_ROW, interactive.getVariableTable().get("r"));

      // a scheduled onLoad error that is not a load failure warns and continues
      ViewsheetSandbox plain = hiddenFailedTableSandbox(true, "throw 'PLAINX';");
      CSVVSExporter exporter3 = new CSVVSExporter(new ByteArrayOutputStream(), null);
      exporter3.export(plain, "Text", null);
      exporter3.write();
      Exception error = plain.getExportScriptError();
      assertNotNull(error);
      assertNull(TableLoadException.find(error), String.valueOf(error));
      assertTrue(String.valueOf(error.getMessage()).contains("PLAINX"), String.valueOf(error));
   }

   // Bug #78445: the export's script error keeps the first error, so a plain onLoad error of the
   // host must not hide a load failure of an embedded viewsheet's onLoad, which runs after it:
   // the scheduled export still fails
   @Test
   void hostOnLoadErrorDoesNotHideAnEmbeddedLoadFailure() throws Exception {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", true);
      Viewsheet child = viewsheet(ws);
      child.setEntry(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/RowFetchFailureTest/child",
         null, OrganizationManager.getInstance().getCurrentOrgID()));
      child.createVSAssembly("Viewsheet1");
      TableVSAssembly table = new TableVSAssembly(child, "TableV");
      table.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, "T1"));
      ColumnSelection cols = new ColumnSelection();

      for(String name : new String[] { "id", "g", "x" }) {
         cols.addAttribute(new ColumnRef(new AttributeRef(null, name)));
      }

      table.setColumnSelection(cols);
      child.addAssembly(table);
      child.getViewsheetInfo().setOnLoad("var n = TableV.table.length; parameter.n = n;");

      Viewsheet host = viewsheet(ws);
      host.getViewsheetInfo().setOnLoad("throw 'HOSTX';");
      host.addAssembly(child);
      ViewsheetSandbox box = viewsheetSandbox(host, querySandbox(ws, true));
      assertNotNull(box.getSandbox("Viewsheet1"));

      assertLoadFailure(assertThrows(TableLoadException.class, box::prepareForExport),
                        DB_MESSAGE);
      // the first error, the host's, is still the one recorded for the warning
      assertTrue(String.valueOf(box.getExportScriptError()).contains("HOSTX"),
                 String.valueOf(box.getExportScriptError()));
   }

   // Bug #78445: through the real ViewsheetAction save to disk (CSV), a scheduled task whose
   // onLoad reads a hidden table that failed to load fails with the database error, and neither
   // the target file nor the task's temp file is left behind. A plain onLoad error still warns,
   // and the task finishes and saves the file
   @Test
   void scheduledSaveToDiskFailsOnAnOnLoadLoadFailure() throws Exception {
      String read = "var n = TableV.table.length; parameter.r = 'rows=' + n;";
      java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("vsaction78445");

      try {
         java.util.Set<String> cache = cacheListing();
         java.util.List<String> saved = new java.util.ArrayList<>();
         Throwable ex = runSaveToDisk(hiddenFailedTableSandbox(true, read), dir, saved);
         assertLoadFailure(ex, DB_MESSAGE);
         assertTrue(hasSqlException(TableLoadException.find(ex)), String.valueOf(ex));
         assertTrue(saved.isEmpty(), String.valueOf(saved));
         assertEquals(0, dir.toFile().list().length);
         assertEquals(cache, cacheListing());

         ViewsheetSandbox plain = hiddenFailedTableSandbox(true, "throw 'PLAINX';");
         assertNull(runSaveToDisk(plain, dir, saved));
         assertEquals(1, saved.size(), String.valueOf(saved));
      }
      finally {
         Tool.deleteFile(dir.toFile());
      }
   }

   // Bug #78445: a load failure in the onLoad of a viewsheet embedded two levels down fails the
   // scheduled export; interactive, the same sheet exports without an error
   @Test
   void embeddedLoadFailureTwoLevelsDownFails() throws Exception {
      for(boolean scheduler : new boolean[] { true, false }) {
         Worksheet ws = new Worksheet();
         sqlTable(ws, "T1", true);
         Viewsheet leaf = embeddedViewsheet(ws, "Leaf", true,
                                            "var n = TableV.table.length; parameter.n = n;");
         Viewsheet mid = embeddedViewsheet(ws, "Mid", false, "parameter.m = 1;");
         mid.addAssembly(leaf);
         Viewsheet host = viewsheet(ws);
         host.getViewsheetInfo().setOnLoad("parameter.h = 1;");
         host.addAssembly(mid);
         ViewsheetSandbox box = viewsheetSandbox(host, querySandbox(ws, scheduler));
         assertNotNull(box.getSandbox("Mid"));
         assertNotNull(box.getSandbox("Mid.Leaf"));

         if(scheduler) {
            assertLoadFailure(assertThrows(TableLoadException.class, box::prepareForExport),
                              DB_MESSAGE);
         }
         else {
            box.prepareForExport();
            assertNull(box.getExportScriptError());
         }
      }
   }

   // Bug #78445: a host onLoad load failure followed by a plain error in an embedded onLoad
   // still fails the scheduled export, and the host's failure stays the recorded error
   @Test
   void hostLoadFailureThenEmbeddedPlainErrorFails() throws Exception {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", true);
      ViewsheetSandbox box = vsTable(ws, querySandbox(ws, true));
      Viewsheet host = box.getViewsheet();
      host.getViewsheetInfo().setOnLoad("var n = TableV.table.length; parameter.n = n;");
      host.addAssembly(embeddedViewsheet(ws, "Viewsheet1", false, "throw 'CHILDX';"));
      assertNotNull(box.getSandbox("Viewsheet1"));

      assertLoadFailure(assertThrows(TableLoadException.class, box::prepareForExport),
                        DB_MESSAGE);
      assertNotNull(TableLoadException.find(box.getExportScriptError()));
   }

   // an embedded viewsheet named name over ws, with the table TableV bound to T1 if table
   private static Viewsheet embeddedViewsheet(Worksheet ws, String name, boolean table,
                                              String onLoad)
      throws Exception
   {
      Viewsheet child = viewsheet(ws);
      child.setEntry(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "test/RowFetchFailureTest/" + name, null,
         OrganizationManager.getInstance().getCurrentOrgID()));
      child.createVSAssembly(name);

      if(table) {
         TableVSAssembly tableV = new TableVSAssembly(child, "TableV");
         tableV.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, "T1"));
         ColumnSelection cols = new ColumnSelection();

         for(String col : new String[] { "id", "g", "x" }) {
            cols.addAttribute(new ColumnRef(new AttributeRef(null, col)));
         }

         tableV.setColumnSelection(cols);
         child.addAssembly(tableV);
      }

      child.getViewsheetInfo().setOnLoad(onLoad);
      return child;
   }

   private static java.util.Set<String> cacheListing() throws Exception {
      java.nio.file.Path cdir =
         java.nio.file.Paths.get(FileSystemService.getInstance().getCacheDirectory());

      try(java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(cdir)) {
         return files.map(f -> f.getFileName().toString())
            .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
      }
   }

   // runs a real ViewsheetAction that saves the home bookmark of the sandbox's viewsheet as CSV
   // to dir, with only the viewsheet open/close, the bookmark list and the external storage
   // stood in for; returns what the task threw, and adds each saved file to saved
   private static Throwable runSaveToDisk(ViewsheetSandbox box, java.nio.file.Path dir,
                                          java.util.List<String> saved)
      throws Exception
   {
      inetsoft.sree.security.IdentityID pid = new inetsoft.sree.security.IdentityID(
         "admin", OrganizationManager.getInstance().getCurrentOrgID());
      java.security.Principal principal = new inetsoft.sree.security.SRPrincipal(pid);
      java.security.Principal oldPrincipal = ThreadContext.getContextPrincipal();
      Viewsheet vs = box.getViewsheet();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getID()).thenReturn("rvs-78445");
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(rvs.getEntry()).thenReturn(box.getAssetEntry());
      when(rvs.getOriginalBookmark(any(), any())).thenReturn(vs);

      inetsoft.sree.schedule.ViewsheetAction action =
         new inetsoft.sree.schedule.ViewsheetAction(box.getAssetEntry().toIdentifier(), null)
      {
         @Override
         public RuntimeViewsheet getRuntimeViewsheet(
            java.security.Principal user, inetsoft.sree.schedule.ScheduleViewsheetService service)
         {
            return rvs;
         }

         @Override
         public void closeViewsheet(String id, java.security.Principal user,
                                    inetsoft.sree.schedule.ScheduleViewsheetService service)
         {
         }
      };
      action.setBookmarks(new String[] { VSBookmark.HOME_BOOKMARK });
      action.setBookmarkTypes(new int[] { 0 });
      action.setBookmarkUsers(new inetsoft.sree.security.IdentityID[] { pid });
      action.setFilePath(FileFormatInfo.EXPORT_TYPE_CSV, dir.resolve("out78445").toString());

      VSBookmarkInfo home = new VSBookmarkInfo(VSBookmark.HOME_BOOKMARK, 0, pid, false, 0L);
      inetsoft.storage.ExternalStorageService storage =
         mock(inetsoft.storage.ExternalStorageService.class);
      doAnswer(inv -> saved.add(inv.getArgument(0)))
         .when(storage).write(any(), any(java.nio.file.Path.class), any());

      try(MockedStatic<inetsoft.uql.viewsheet.internal.VSUtil> vsUtil =
             mockStatic(inetsoft.uql.viewsheet.internal.VSUtil.class, CALLS_REAL_METHODS);
          MockedStatic<inetsoft.sree.schedule.ScheduleViewsheetService> service =
             mockStatic(inetsoft.sree.schedule.ScheduleViewsheetService.class);
          MockedStatic<inetsoft.storage.ExternalStorageService> external =
             mockStatic(inetsoft.storage.ExternalStorageService.class))
      {
         vsUtil.when(() -> inetsoft.uql.viewsheet.internal.VSUtil.getBookmarks(
            any(AssetEntry.class), any())).thenReturn(new VSBookmarkInfo[] { home });
         service.when(inetsoft.sree.schedule.ScheduleViewsheetService::getInstance)
            .thenReturn(mock(inetsoft.sree.schedule.ScheduleViewsheetService.class));
         external.when(inetsoft.storage.ExternalStorageService::getInstance).thenReturn(storage);
         action.run(principal);
         return null;
      }
      catch(Throwable ex) {
         return ex;
      }
      finally {
         ThreadContext.setContextPrincipal(oldPrincipal);
      }
   }

   // the table TableV bound to the failing T1, hidden, and the text Text1, with the onLoad script
   private static ViewsheetSandbox hiddenFailedTableSandbox(boolean scheduler, String script)
      throws Exception
   {
      ViewsheetSandbox vbox = executeFailureSandbox(scheduler, script);
      Viewsheet vs = vbox.getViewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      VSAssembly hidden = (VSAssembly) vs.getAssembly("TableV");
      hidden.getVSAssemblyInfo().setVisibleValue(String.valueOf(VSAssembly.ALWAYS_HIDE));
      assertFalse(hidden.isVisible());
      return vbox;
   }

   @Test
   void scheduledCalcOutputAndRangeReadsOfAnExecuteFailureFail() throws Exception {
      String[] scripts = {
         "var t = Calc1.table; throw new Error(t == null ? 'null' : 'len ' + t.length);",
         "var t = Calc1.data; throw new Error(t == null ? 'null' : 'len ' + t.length);",
         "var v = Text1.value; throw new Error('v=' + v);" };
      String range = "sum(data['id?TableV.table.length < 0'])";

      for(String[] failure : EXECUTE_FAILURES) {
         failingQuery = failure[0];
         String message = failure[1];

         for(String script : scripts) {
            assertLoadFailure(onLoadError(true, true, script), message, script);

            Exception error = onLoadError(true, false, script);
            assertNotNull(error, script);
            assertNull(TableLoadException.find(error), script + ": " + error);
         }

         // the swallowed read was null, which the range took as selected: the sum of every row
         assertLoadFailure(assertThrows(Exception.class, () -> calcCells(
            (TableLens) conditionSandbox(true, true, range).getData("Calc1")), message), message);
      }
   }

   @Test
   void scheduledSuccessfulAggregateIsUnchanged() throws Exception {
      failingQuery = "select max(big.id) as id, big.g, sum(big.g) as x from big " +
         "where big.id > -%d group by big.g";
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      CSVVSExporter exporter = new CSVVSExporter(out, null);
      exporter.export(executeFailureSandbox(true, null), "TableV", null);
      exporter.write();
      assertTrue(out.size() > 0);
      assertNull(CoreTool.getUserMessage());

      Object data = executeFailureSandbox(true, null).getData("TableV");
      TableLens lens = data instanceof VSDataSet ? ((VSDataSet) data).getTable() : (TableLens) data;
      assertEquals(10, dataRows(lens));
   }

   // the table TableV bound to the failing T1, with the onLoad script if not null
   private static ViewsheetSandbox executeFailureSandbox(boolean scheduler, String script)
      throws Exception
   {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", true);
      ViewsheetSandbox vbox = vsTable(ws, querySandbox(ws, scheduler));

      if(script != null) {
         vbox.getViewsheet().getViewsheetInfo().setOnLoad(script);
      }

      return vbox;
   }

   private static void assertLoadFailure(Throwable ex, String message) {
      assertLoadFailure(ex, message, "");
   }

   private static void assertLoadFailure(Throwable ex, String message, String shape) {
      assertNotNull(ex, message + " " + shape);
      assertNotNull(TableLoadException.find(ex), message + " " + shape + ": " + ex);
      assertTrue(String.valueOf(ex.getMessage()).contains(message),
                 message + " " + shape + ": " + ex);
   }

   // the load failure is caused by the database error
   private static boolean hasSqlException(Throwable ex) {
      for(int i = 0; ex != null && i < 16; ex = ex.getCause(), i++) {
         if(ex instanceof java.sql.SQLException) {
            return true;
         }
      }

      return false;
   }

   // Bug #78083 (follow-up of #6558): a chart script that reads a hidden table which failed to
   // load in a scheduled run fails the export of the chart, instead of VGraphPair swallowing
   // the script failure and rendering the chart without the script's effect. An interactive
   // plot renders past the script error, and a scheduled plot over good tables is unchanged
   @Test
   void scheduledChartScriptOverAHiddenFailedTable() throws Exception {
      String script = "parameter.probe_reached = 'start'; var n = TableV.table.length; " +
         "parameter.probe_reached = 'len ' + n;";
      Principal oprincipal = ThreadContext.getPrincipal();
      ThreadContext.setPrincipal(SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()),
         null, false));

      try {
         assertLoadFailure(() -> chartScriptSandbox(true, true, script)
            .getVGraphPair("Chart1", true, null, true, 1));

         for(boolean fail : new boolean[] { true, false }) {
            CoreTool.clearUserMessage();
            // interactive over the failed table, scheduled over the good one
            ViewsheetSandbox box = chartScriptSandbox(fail, !fail, script);
            VGraphPair pair = box.getVGraphPair("Chart1", true, null, true, 1);

            assertNotNull(pair, "fail " + fail);
            assertTrue(pair.isCompleted() && pair.getEGraph() != null, "fail " + fail);
            // the header row and the rows read (before the failure)
            assertEquals("len " + (fail ? FAIL_ROW : ROWS + 1),
                         box.getVariableTable().get("probe_reached"), "fail " + fail);
         }
      }
      finally {
         ThreadContext.setPrincipal(oprincipal);
      }
   }

   // Bug #78083 (follow-up of #6558): an onLoad script that reads the data of a chart bound to
   // a table which failed to load in a scheduled run stops at that read, as for a calc table,
   // instead of going on with an empty chart table or a null dataset. Not merged into the
   // chart's SQL, the row fetch fails; merged, the query fails at execute, which a scheduled
   // run reports as a load failure too (Bug #78411). Interactive and successful reads get the
   // chart data, as before
   @Test
   void scriptReadOfChartDataOverAFailedTable() throws Exception {
      String[] scripts = {
         "var t = Chart1.table; throw new Error(t == null ? 'null' : 'len ' + t.length);",
         "var d = Chart1.dataset; throw new Error(d == null ? 'null' : 'rows ' + d.getRowCount());" };
      Principal oprincipal = ThreadContext.getPrincipal();
      ThreadContext.setPrincipal(SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()),
         null, false));

      try {
         assertAll(IntStream.range(0, scripts.length).mapToObj(i -> (Executable) () -> {
            String script = scripts[i];
            Exception error = chartOnLoadError(true, true, script);
            assertNotNull(error, script);
            assertNotNull(TableLoadException.find(error), script + ": " + error);
            assertTrue(error.getMessage().contains(DB_MESSAGE), script + ": " + error);

            // merged into the chart's SQL, the query fails at execute (Bug #78411)
            error = chartOnLoadError(true, true, script, true);
            assertNotNull(error, script);
            assertNotNull(TableLoadException.find(error), "merged " + script + ": " + error);
            assertTrue(error.getMessage().contains(DB_MESSAGE), "merged " + script + ": " + error);

            // interactive: the chart of the rows read before the failure, with the warning
            CoreTool.clearUserMessage();
            error = chartOnLoadError(true, false, script);
            assertNotNull(error, script);
            assertNull(TableLoadException.find(error), script + ": " + error);
            assertTrue(error.getMessage().contains(i == 0 ? "len 11" : "rows 10"),
                       script + ": " + error);

            // successful scheduled read
            error = chartOnLoadError(false, true, script);
            assertNotNull(error, script);
            assertNull(TableLoadException.find(error), script + ": " + error);
            assertTrue(error.getMessage().contains(i == 0 ? "len 11" : "rows 10"),
                       script + ": " + error);
         }));
      }
      finally {
         ThreadContext.setPrincipal(oprincipal);
      }
   }

   // Bug #78083 (follow-up of #6558): the scheduled export of a chart bound to a table which
   // failed to load fails, instead of VSDataSet taking the load failure for an empty data set
   // and the chart plotting no data. Interactive and successful plots are unchanged
   @Test
   void scheduledChartOverAFailedTable() throws Exception {
      Principal oprincipal = ThreadContext.getPrincipal();
      ThreadContext.setPrincipal(SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()),
         null, false));

      try {
         assertLoadFailure(() -> chartSandbox(true, true)
            .getVGraphPair("Chart1", true, null, true, 1));

         for(boolean fail : new boolean[] { true, false }) {
            CoreTool.clearUserMessage();
            VGraphPair pair = chartSandbox(fail, !fail)
               .getVGraphPair("Chart1", true, null, true, 1);

            assertNotNull(pair, "fail " + fail);
            assertTrue(pair.isCompleted() && pair.getEGraph() != null, "fail " + fail);
            assertEquals(10, pair.getData().getRowCount(), "fail " + fail);
         }
      }
      finally {
         ThreadContext.setPrincipal(oprincipal);
      }
   }

   // Bug #78083: a chart script that sets its dataset from a hidden table which failed to load
   // (a script-created dataset, #75580) fails the scheduled export as well; interactive plots
   @Test
   void scheduledChartScriptDatasetFromAFailedTable() throws Exception {
      String script = "dataset = TableV.table; parameter.probe_reached = 'set';";
      Principal oprincipal = ThreadContext.getPrincipal();
      ThreadContext.setPrincipal(SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()),
         null, false));

      try {
         assertLoadFailure(() -> chartScriptSandbox(true, true, script)
            .getVGraphPair("Chart1", true, null, true, 1));

         CoreTool.clearUserMessage();
         ViewsheetSandbox box = chartScriptSandbox(true, false, script);
         VGraphPair pair = box.getVGraphPair("Chart1", true, null, true, 1);

         assertNotNull(pair);
         assertTrue(pair.isCompleted() && pair.getEGraph() != null);
         assertEquals("set", box.getVariableTable().get("probe_reached"));
      }
      finally {
         ThreadContext.setPrincipal(oprincipal);
      }
   }

   // Bug #78083: a chart script error that is not a load failure is still rendered past, also
   // in a scheduled export (only DataUnavailable and TableLoadException are rethrown)
   @Test
   void chartScriptErrorOverGoodTablesStillPlots() throws Exception {
      String script = "parameter.probe_reached = 'start'; throw new Error('plain');";
      Principal oprincipal = ThreadContext.getPrincipal();
      ThreadContext.setPrincipal(SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()),
         null, false));

      try {
         for(boolean scheduler : new boolean[] { true, false }) {
            ViewsheetSandbox box = chartScriptSandbox(false, scheduler, script);
            VGraphPair pair = box.getVGraphPair("Chart1", true, null, true, 1);

            assertNotNull(pair, "scheduler " + scheduler);
            assertTrue(pair.isCompleted() && pair.getEGraph() != null, "scheduler " + scheduler);
            assertEquals("start", box.getVariableTable().get("probe_reached"),
                         "scheduler " + scheduler);
         }
      }
      finally {
         ThreadContext.setPrincipal(oprincipal);
      }
   }

   // a viewsheet with the chart Chart1 bound to T1, which fails if fail; T1 is not merged
   // into the chart's SQL, so the chart's group and sort are done in memory over the fetched
   // rows (merged, the query fails at execute; see scriptReadOfChartDataOverAFailedTable)
   private static ViewsheetSandbox chartSandbox(boolean fail, boolean scheduler)
      throws Exception
   {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", fail).setSQLMergeable(false);
      Viewsheet vs = viewsheet(ws);
      vs.addAssembly(chart(vs, "T1", "Sum"));
      return viewsheetSandbox(vs, querySandbox(ws, scheduler));
   }

   // a viewsheet with the hidden table TableV bound to T1, which fails if fail, and the bar
   // chart Chart1 (x g, y Sum(id)) bound to the good table T2 with the chart script
   private static ViewsheetSandbox chartScriptSandbox(boolean fail, boolean scheduler,
                                                      String script)
      throws Exception
   {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", fail);
      sqlTable(ws, "T2", false);
      Viewsheet vs = viewsheet(ws);
      TableVSAssembly table = new TableVSAssembly(vs, "TableV");
      table.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, "T1"));
      ColumnSelection cols = new ColumnSelection();

      for(String name : new String[] { "id", "g", "x" }) {
         cols.addAttribute(new ColumnRef(new AttributeRef(null, name)));
      }

      table.setColumnSelection(cols);
      table.getVSAssemblyInfo().setVisibleValue(String.valueOf(VSAssembly.ALWAYS_HIDE));
      vs.addAssembly(table);
      ChartVSAssembly chart = chart(vs, "T2", "Sum");
      chart.getVSAssemblyInfo().setScriptEnabled(true);
      chart.getVSAssemblyInfo().setScript(script);
      vs.addAssembly(chart);
      return viewsheetSandbox(vs, querySandbox(ws, scheduler));
   }

   // the error of an onLoad script of a viewsheet with the chart Chart1 bound to T1
   private static Exception chartOnLoadError(boolean fail, boolean scheduler, String script)
      throws Exception
   {
      return chartOnLoadError(fail, scheduler, script, false);
   }

   // the error of an onLoad script of a viewsheet with the chart Chart1 bound to T1. Not
   // merged, the chart's group and sort are done in memory, so its rows are fetched and the
   // fetch fails; merged into the SQL (Sum(id) group by g), the query fails at execute
   private static Exception chartOnLoadError(boolean fail, boolean scheduler, String script,
                                             boolean merged)
      throws Exception
   {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", fail).setSQLMergeable(merged);
      Viewsheet vs = viewsheet(ws);
      vs.addAssembly(chart(vs, "T1", "Sum"));
      vs.getViewsheetInfo().setOnLoad(script);
      ViewsheetSandbox vbox = viewsheetSandbox(vs, querySandbox(ws, scheduler));
      vbox.prepareForExport();
      return vbox.getExportScriptError();
   }

   // a bar chart Chart1, x g, y formula(id), over the worksheet table
   private static ChartVSAssembly chart(Viewsheet vs, String source, String formula) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, source));
      VSChartInfo info = chart.getVSChartInfo();
      VSChartDimensionRef dim =
         new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "g")));
      dim.setGroupColumnValue("g");
      info.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("id");
      agg.setFormulaValue(formula);
      info.addYField(agg);
      info.setChartType(GraphTypes.CHART_BAR);
      info.setRTChartType(GraphTypes.CHART_BAR);
      return chart;
   }

   // the error of an onLoad script of a viewsheet with the calc table Calc1 (sum of id) and
   // the text Text1 (Sum of id), both bound to T1
   private static Exception onLoadError(boolean fail, boolean scheduler, String script)
      throws Exception
   {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", fail);
      Viewsheet vs = viewsheet(ws);
      vs.addAssembly(calcTable(vs, "T1", "sum(data['id'])"));
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      ScalarBindingInfo binding = new ScalarBindingInfo();
      binding.setTableName("T1");
      binding.setColumnValue("id");
      binding.setColumn(new ColumnRef(new AttributeRef(null, "id")));
      binding.setAggregateValue("Sum");
      binding.changeColumnType("Sum", XSchema.INTEGER);
      text.setScalarBindingInfo(binding);
      vs.addAssembly(text);
      vs.getViewsheetInfo().setOnLoad(script);

      ViewsheetSandbox vbox = viewsheetSandbox(vs, querySandbox(ws, scheduler));

      // Bug #78445: a scheduled load failure fails the export, and is recorded as the script
      // error as well. Any other run must not throw
      if(fail && scheduler) {
         TableLoadException ex = assertThrows(TableLoadException.class, vbox::prepareForExport,
                                              script);
         assertNotNull(TableLoadException.find(vbox.getExportScriptError()), script);
         return ex;
      }

      vbox.prepareForExport();
      return vbox.getExportScriptError();
   }

   // a viewsheet with the table TableV bound to T1, which fails if fail, and the calc table
   // Calc1 bound to the good table T2, whose cell A1 is the formula
   private static ViewsheetSandbox conditionSandbox(boolean fail, boolean scheduler,
                                                    String formula)
      throws Exception
   {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", fail);
      sqlTable(ws, "T2", false);
      Viewsheet vs = viewsheet(ws);
      TableVSAssembly table = new TableVSAssembly(vs, "TableV");
      table.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, "T1"));
      ColumnSelection cols = new ColumnSelection();

      for(String name : new String[] { "id", "g", "x" }) {
         cols.addAttribute(new ColumnRef(new AttributeRef(null, name)));
      }

      table.setColumnSelection(cols);
      vs.addAssembly(table);
      vs.addAssembly(calcTable(vs, "T2", formula));
      return viewsheetSandbox(vs, querySandbox(ws, scheduler));
   }

   // the calc table Calc1 bound to the worksheet table, whose cell A1 is the formula
   private static CalcTableVSAssembly calcTable(Viewsheet vs, String source, String formula) {
      CalcTableVSAssembly calc = new CalcTableVSAssembly(vs, "Calc1");
      calc.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, source));
      calc.getTableLayout().setCellBinding(
         0, 0, new TableCellBinding(CellBinding.BIND_FORMULA, formula));
      return calc;
   }

   private static AssetQuerySandbox querySandbox(Worksheet ws, boolean scheduler) {
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      if(scheduler) {
         box.getVariableTable().put("__is_scheduler__", "true");
      }

      return box;
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
            // a failed read is reported as a load failure, not a user cancel (Bug #78117),
            // and still carries the real database error as its cause
            MVLoadFailedException loadFailed = assertInstanceOf(MVLoadFailedException.class, thrown);
            assertTrue(loadFailed.getMessage().contains("mv77901"), loadFailed.getMessage());
            assertNotNull(loadFailed.getCause());
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
      if(fail && failingQuery != null) {
         return sqlTable(ws, name, String.format(failingQuery, RUN.incrementAndGet()));
      }

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
