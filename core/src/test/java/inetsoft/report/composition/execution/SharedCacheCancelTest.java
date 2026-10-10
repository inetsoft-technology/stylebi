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

import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.lens.JoinTableLens;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.QueryManager;
import inetsoft.uql.util.TableLoadException;
import inetsoft.util.*;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78200: two sandboxes of the same user read the same worksheet table, and the second
 * got the first one's still-loading lens from AssetDataCache. AssetDataCache hands every
 * reader a shallow copy that shares the tables under it, so a cancel by one reader
 * (QueryManager.cancel(), e.g. cancel loading or a viewsheet superseded or disposed; or the
 * Stop query statement) ended the rows of the other reader too, and that reader took the
 * rows read so far for the whole table, with no message. A reader whose rows were ended by
 * another reader's cancel must get the whole table or be told; the reader that cancelled
 * stays silent. Runs the real AssetQuerySandbox / AssetDataCache / XSessionManager /
 * JDBCHandler path on Derby, configured as in RowFetchFailureTest.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  RowFetchFailureTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SharedCacheCancelTest {
   // the Derby database of RowFetchFailureTest.JdbcConfig
   private static final String DB = "memory:rowfetchfailure";
   // the plain table: a cross join of PROWS rows with 300 rows, slow enough to share loading
   private static final int PROWS = 2000;
   private static final int PFULL = PROWS * 300;
   // the join: JROWS rows joined with themselves on id % 2
   private static final int JROWS = 2000;
   private static final int JFULL = JROWS * JROWS / 2;
   private static final AtomicInteger RUN = new AtomicInteger();

   @BeforeAll
   static void createTables() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         for(String t : new String[] { "sc78200", "sj78200" }) {
            try {
               stmt.executeUpdate("drop table " + t);
            }
            catch(Exception ignore) {
               // first run
            }

            stmt.executeUpdate("create table " + t + " (id int, g int)");
         }

         conn.setAutoCommit(false);
         fill(conn, "sc78200", PROWS);
         fill(conn, "sj78200", JROWS);
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

   /** Cancel loading, a viewsheet superseded or disposed: the first reader's query manager. */
   @Test
   void queryManagerCancelOfTheFirstReaderTellsTheSecond() throws Exception {
      Shared shared = plain();
      shared.box1.getQueryManager().cancel();

      shared.assertSecondReaderWholeOrTold("QueryManager.cancel()");
      shared.assertFirstReaderSilent();
   }

   /** The WSQueryService.stopQuery statement (the Stop query button) of the first reader. */
   @Test
   void stopQueryOfTheFirstReaderTellsTheSecond() throws Exception {
      Shared shared = plain();
      stop(shared.lens1);

      shared.assertSecondReaderWholeOrTold("Stop of the first reader");
      shared.assertFirstReaderSilent();
   }

   /** The Stop query of the second reader ends the rows of the first one as well. */
   @Test
   void stopQueryOfTheSecondReaderTellsTheFirst() throws Exception {
      Shared shared = plain();
      stop(shared.lens2);

      int rows1 = rows(shared.lens1);
      UserMessage message = CoreTool.getUserMessage();
      assertWholeOrTold(rows1, PFULL, message, "Stop of the second reader");

      rows(shared.lens2);
      assertNull(CoreTool.getUserMessage(), "the reader that stopped the query is not told");
   }

   /** The second reader waits for the rows when the first reader cancels. */
   @Test
   void cancelWhileTheSecondReaderWaitsTellsIt() throws Exception {
      Shared shared = plain();
      AtomicReference<Object> result = new AtomicReference<>();
      Thread reader = new Thread(() -> {
         try {
            CoreTool.clearUserMessage();
            int rows = rows(shared.lens2);
            result.set(new Object[] { rows, CoreTool.getUserMessage() });
         }
         catch(Throwable ex) {
            result.set(ex);
         }
      }, "SharedCacheCancelTest-reader");
      reader.start();

      try {
         // let the reader wait for the rows still loading
         Thread.sleep(200);
         shared.box1.getQueryManager().cancel();
      }
      finally {
         reader.join(TimeUnit.MINUTES.toMillis(5));
      }

      assertFalse(reader.isAlive(), "the reader never finished");
      assertFalse(result.get() instanceof Throwable, () -> "the reader failed: " + result.get());
      Object[] read = (Object[]) result.get();
      assertWholeOrTold((Integer) read[0], PFULL, (UserMessage) read[1],
                        "QueryManager.cancel() while the second reader waits");
   }

   /**
    * A cancel of the JDBC statement only, as the query monitor kill (its query manager holds
    * only the statement): the reader is told, and a later reader gets the whole table instead
    * of the rows read before the cancel.
    */
   @Test
   void statementOnlyCancelTellsTheReaderAndIsNotCached() throws Exception {
      Shared shared = plain();
      QueryManager monitor = new QueryManager();
      monitor.addPending(statement(shared.box1.getQueryManager()));
      monitor.cancel();

      shared.assertSecondReaderWholeOrTold("cancel of the statement");
      assertTrue(shared.x1.isCancelled() || shared.x1.getRowCount() - 1 == PFULL,
                 "the table read up to the cancel is not cancelled");

      AssetQuerySandbox box3 = new AssetQuerySandbox(shared.ws);
      box3.setQueryManager(new QueryManager());
      TableLens lens3 = box3.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE,
                                          new VariableTable());
      assertEquals(PFULL, rows(lens3), "a later reader got the rows read before the cancel");
   }

   /** The in-memory join (the JoinCancelCacheTest shape): the bases loaded, the join running. */
   @Test
   void queryManagerCancelOfARunningJoinTellsTheSecondReader() throws Exception {
      for(int i = 0; i < 5; i++) {
         Worksheet ws = joinWorksheet();
         AssetQuerySandbox box1 = new AssetQuerySandbox(ws);
         box1.setQueryManager(new QueryManager());
         TableLens lens1 = box1.getTableLens("J1", AssetQuerySandbox.RUNTIME_MODE,
                                             new VariableTable());
         assertNotNull(lens1, "the query failed, see the log");
         JoinTableLens join1 = (JoinTableLens) Util.getNestedTable(lens1, JoinTableLens.class);
         assertNotNull(join1, chain(lens1));
         join1.getLeftTable().moreRows(XTable.EOT);
         join1.getRightTable().moreRows(XTable.EOT);

         AssetQuerySandbox box2 = new AssetQuerySandbox(ws);
         box2.setQueryManager(new QueryManager());
         TableLens lens2 = box2.getTableLens("J1", AssetQuerySandbox.RUNTIME_MODE,
                                             new VariableTable());
         JoinTableLens join2 = (JoinTableLens) Util.getNestedTable(lens2, JoinTableLens.class);

         // the join finished before the second reader got it: try again
         if(join1.getRowCount() >= 0 || join1 != join2) {
            continue;
         }

         box1.getQueryManager().cancel();
         int rows2 = rows(lens2);
         assertWholeOrTold(rows2, JFULL, CoreTool.getUserMessage(),
                           "QueryManager.cancel() of a running join");

         rows(lens1);
         assertNull(CoreTool.getUserMessage(), "the reader that cancelled is not told");
         return;
      }

      fail("the join always finished before the second reader got it");
   }

   /**
    * A scheduled reader must not use partial data: it fails instead of getting a warning.
    */
   @Test
   void cancelOfTheFirstReaderFailsAScheduledSecondReader() throws Exception {
      Shared shared = plain(scheduled(), scheduled());
      shared.box1.getQueryManager().cancel();

      TableLoadException ex = assertThrows(TableLoadException.class,
                                           () -> rows(shared.lens2),
                                           "the scheduled reader took the rows read so far");
      assertEquals(Catalog.getCatalog().getString("common.table.queryCancelled"),
                   ex.getMessage());
      shared.assertFirstReaderSilent();
   }

   /**
    * Two readers of the same loading table with no cancel: both get the whole table with no
    * message, and the complete table stays cached for a later reader.
    */
   @Test
   void sharedReadersWithoutCancelGetTheWholeCachedTable() throws Exception {
      Shared shared = plain();

      assertEquals(PFULL, rows(shared.lens2));
      assertNull(CoreTool.getUserMessage(), "the second reader was told of no cancel");
      assertEquals(PFULL, rows(shared.lens1));
      assertNull(CoreTool.getUserMessage(), "the first reader was told of no cancel");

      AssetQuerySandbox box3 = new AssetQuerySandbox(shared.ws);
      box3.setQueryManager(new QueryManager());
      TableLens lens3 = box3.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE,
                                          new VariableTable());
      assertSame(shared.x1, Util.getNestedTable(lens3, XNodeTableLens.class),
                 "the complete table was not served from the cache");
      assertEquals(PFULL, rows(lens3));
      assertNull(CoreTool.getUserMessage(), "the later reader was told of no cancel");
      assertFalse(shared.x1.isCancelled());
   }

   private static VariableTable scheduled() {
      VariableTable vars = new VariableTable();
      vars.put("__is_scheduler__", "true");
      return vars;
   }

   /** The two readers of the plain table, the second one sharing the first one's lens. */
   private static Shared plain() throws Exception {
      return plain(new VariableTable(), new VariableTable());
   }

   /** The two readers of the plain table, with their variables. */
   private static Shared plain(VariableTable vars1, VariableTable vars2) throws Exception {
      Shared shared = new Shared();
      shared.ws = new Worksheet();
      int run = RUN.incrementAndGet();
      // the run number keeps each test's query apart in the caches
      sqlTable(shared.ws, "T1", "select a.id, a.g from sc78200 a, sc78200 b " +
               "where b.id <= 300 and a.id > -" + run, "id", "g");

      shared.box1 = new AssetQuerySandbox(shared.ws);
      shared.box1.setQueryManager(new QueryManager());
      shared.lens1 = shared.box1.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE,
                                              vars1);
      assertNotNull(shared.lens1, "the query failed, see the log");
      shared.x1 = (XNodeTableLens) Util.getNestedTable(shared.lens1, XNodeTableLens.class);

      shared.box2 = new AssetQuerySandbox(shared.ws);
      shared.box2.setQueryManager(new QueryManager());
      shared.lens2 = shared.box2.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE,
                                              vars2);
      assertNotNull(shared.lens2, "the query failed, see the log");
      XNodeTableLens x2 = (XNodeTableLens) Util.getNestedTable(shared.lens2,
                                                               XNodeTableLens.class);

      // otherwise the test proves nothing
      assertSame(shared.x1, x2, "the second reader did not share the first one's lens");
      assertTrue(x2.getRowCount() < 0, "the table loaded before the cancel");
      return shared;
   }

   private static final class Shared {
      void assertSecondReaderWholeOrTold(String cancel) {
         int rows2 = rows(lens2);
         assertWholeOrTold(rows2, PFULL, CoreTool.getUserMessage(), cancel);
      }

      void assertFirstReaderSilent() {
         rows(lens1);
         assertNull(CoreTool.getUserMessage(), "the reader that cancelled is not told");
      }

      Worksheet ws;
      AssetQuerySandbox box1;
      AssetQuerySandbox box2;
      TableLens lens1;
      TableLens lens2;
      XNodeTableLens x1;
   }

   /**
    * A reader got the whole table, or was told that the rows were cancelled.
    */
   private static void assertWholeOrTold(int rows, int full, UserMessage message, String cancel) {
      if(rows < full) {
         String cancelled = Catalog.getCatalog().getString("common.table.queryCancelled");
         assertTrue(message != null && Objects.equals(cancelled, message.getMessage()),
                    cancel + ": the reader got " + rows + " of " + full +
                    " rows with no message, message: " +
                    (message == null ? null : message.getMessage()));
      }
   }

   private static int rows(TableLens lens) {
      lens.moreRows(XTable.EOT);
      return lens.getRowCount() - lens.getHeaderRowCount();
   }

   /** The WSQueryService.stopQuery statement. */
   private static void stop(TableLens lens) {
      CancellableTableLens cancel = (CancellableTableLens) Util.getNestedTable(
         lens, CancellableTableLens.class);
      assertNotNull(cancel);
      cancel.cancel();
   }

   /** The JDBC statement JDBCHandler registered in a query manager. */
   private static Object statement(QueryManager qmgr) throws Exception {
      Field field = QueryManager.class.getDeclaredField("queries");
      field.setAccessible(true);

      for(Object ref : new ArrayList<>((Collection<?>) field.get(qmgr))) {
         Object val = ((WeakReference<?>) ref).get();

         if(val instanceof Statement) {
            return val;
         }
      }

      fail("no statement in the query manager");
      return null;
   }

   private static Worksheet joinWorksheet() throws Exception {
      Worksheet ws = new Worksheet();
      int run = RUN.incrementAndGet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", "select sj78200.id, sj78200.g from sj78200 " +
                                          "where sj78200.id > -" + run, "id", "g");
      SQLBoundTableAssembly t2 = sqlTable(ws, "T2", "select sj78200.id as id2, sj78200.g as g2 " +
                                          "from sj78200 where sj78200.id > -" + run, "id2", "g2");
      t1.setSQLMergeable(false);
      t2.setSQLMergeable(false);
      TableAssemblyOperator operator = new TableAssemblyOperator();
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setOperation(TableAssemblyOperator.INNER_JOIN);
      op.setLeftTable("T1");
      op.setRightTable("T2");
      op.setLeftAttribute(t1.getColumnSelection(false).getAttribute("g"));
      op.setRightAttribute(t2.getColumnSelection(false).getAttribute("g2"));
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
      return ws;
   }

   private static String chain(TableLens lens) {
      List<String> names = new ArrayList<>();

      for(TableLens table = lens; table != null;
          table = table instanceof TableFilter ? ((TableFilter) table).getTable() : null)
      {
         names.add(table.getClass().getSimpleName());

         if(table instanceof JoinTableLens) {
            break;
         }
      }

      return String.join(" > ", names);
   }

   private static SQLBoundTableAssembly sqlTable(Worksheet ws, String name, String text,
                                                 String... columns)
      throws Exception
   {
      UniformSQL usql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class,
                                                        long.class);
      parse.setAccessible(true);
      parse.invoke(usql, text, 0, 4000L);
      usql.setSQLString(text, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), text);

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("sharedcachecancel");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
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
      ColumnSelection selection = new ColumnSelection();

      for(String column : columns) {
         ColumnRef ref = new ColumnRef(new AttributeRef(column));
         ref.setDataType(XSchema.INTEGER);
         selection.addAttribute(ref);
      }

      table.setColumnSelection(selection, false);
      table.setColumnSelection((ColumnSelection) selection.clone(), true);
      ws.addAssembly(table);
      return table;
   }

   private static void fill(Connection conn, String table, int rows) throws Exception {
      try(java.sql.PreparedStatement insert =
             conn.prepareStatement("insert into " + table + " values (?, ?)"))
      {
         for(int i = 1; i <= rows; i++) {
            insert.setInt(1, i);
            insert.setInt(2, i % 2);
            insert.addBatch();
         }

         insert.executeBatch();
      }
   }

   private static EmbeddedDataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
