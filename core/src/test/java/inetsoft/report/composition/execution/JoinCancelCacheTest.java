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
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.QueryManager;
import inetsoft.util.CoreTool;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78135: a worksheet join computed in memory whose bases were loaded was cancelled while
 * it was still joining (Stop query, cancel loading). The cancel completed the join with the
 * rows so far, but the join reported not cancelled because its loaded bases are not, so
 * AssetDataCache handed the partial join to the next sandbox as the whole join, with no
 * message. The next sandbox must compute the join again. Runs the real AssetQuerySandbox /
 * AssetDataCache / XSessionManager / JDBCHandler path on Derby, configured as in
 * RowFetchFailureTest. Also checks that a caller of AssetDataCache.getData keeps its interrupt.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  RowFetchFailureTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JoinCancelCacheTest {
   // the Derby database of RowFetchFailureTest.JdbcConfig
   private static final String DB = "memory:rowfetchfailure";
   // pj(id, g = id % 2) joined with itself on g: ROWS * ROWS / 2 rows, slow enough to cancel
   private static final int ROWS = 2000;
   private static final int FULL = ROWS * ROWS / 2;
   private static final AtomicInteger RUN = new AtomicInteger();

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table pj");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table pj (id int, g int)");
         conn.setAutoCommit(false);

         try(java.sql.PreparedStatement insert =
                conn.prepareStatement("insert into pj values (?, ?)"))
         {
            for(int i = 1; i <= ROWS; i++) {
               insert.setInt(1, i);
               insert.setInt(2, i % 2);
               insert.addBatch();
            }

            insert.executeBatch();
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

   /** The statement of WSQueryService.stopQuery (the worksheet Stop query button). */
   @Test
   void stopQueryOfARunningJoinIsNotCached() throws Exception {
      assertNextSandboxRecomputes((box, lens) -> {
         CancellableTableLens cancel = (CancellableTableLens) Util.getNestedTable(
            lens, CancellableTableLens.class);
         assertNotNull(cancel);
         cancel.cancel();
      });
   }

   /** QueryManager.cancel(), e.g. CancelLoadingService or a viewsheet event superseded. */
   @Test
   void queryManagerCancelOfARunningJoinIsNotCached() throws Exception {
      assertNextSandboxRecomputes((box, lens) -> box.getQueryManager().cancel());
   }

   /**
    * A caller of AssetDataCache.getData() that is interrupted while it waits for the query
    * still gets the whole result, and keeps its interrupt.
    */
   @Test
   void getDataCallerKeepsItsInterrupt() throws Exception {
      Worksheet ws = new Worksheet();
      // a count over a cross join: no row comes back before Derby scanned it all
      sqlTable(ws, "C1", "select count(*) as n from pj a, pj b, pj c where c.id <= 3 and " +
               "a.id > -" + RUN.incrementAndGet(), "n");
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      TableAssembly table = (TableAssembly) ws.getAssembly("C1");
      AtomicReference<Object> result = new AtomicReference<>();
      AtomicReference<Boolean> interrupted = new AtomicReference<>();

      Thread caller = new Thread(() -> {
         try {
            TableLens data = AssetDataCache.getCache().getData(
               null, table, box, box.getQueryManager());
            interrupted.set(Thread.currentThread().isInterrupted());
            data.moreRows(XTable.EOT);
            result.set(data.getRowCount() == 2 ? data.getObject(1, 0) : data.getRowCount());
         }
         catch(Throwable ex) {
            result.set(ex);
         }
      }, "join-cancel-78135-caller");
      caller.setDaemon(true);
      caller.start();

      assertTrue(awaitJoining(caller), "the caller never waited for the query");
      caller.interrupt();
      caller.join(TimeUnit.SECONDS.toMillis(120));

      assertFalse(caller.isAlive(), "getData() never returned");
      assertEquals(ROWS * ROWS * 3, ((Number) result.get()).intValue(), "result: " + result);
      assertTrue(interrupted.get(), "the caller's interrupt was lost");
   }

   private static void assertNextSandboxRecomputes(Canceller canceller) throws Exception {
      boolean landed = false;

      // the join is cancelled once its bases are loaded; a try where the join had already
      // completed by then proves nothing and is run again with a new query
      for(int i = 0; i < 5 && !landed; i++) {
         Worksheet ws = joinWorksheet();
         AssetQuerySandbox box1 = new AssetQuerySandbox(ws);
         // as RuntimeWorksheet does
         box1.setQueryManager(new QueryManager());
         TableLens lens1 = box1.getTableLens("J1", AssetQuerySandbox.RUNTIME_MODE,
                                             new VariableTable());
         assertNotNull(lens1, "the query failed, see the log");
         String chain = chain(lens1);
         // a renamed join column adds an AssetTableLens, which a cancel does not pass
         assertFalse(chain.contains("AssetTableLens"), chain);
         JoinTableLens join1 = (JoinTableLens) Util.getNestedTable(lens1, JoinTableLens.class);
         assertNotNull(join1, chain);

         join1.getLeftTable().moreRows(XTable.EOT);
         join1.getRightTable().moreRows(XTable.EOT);

         if(join1.getRowCount() >= 0) {
            continue;
         }

         canceller.cancel(box1, lens1);
         lens1.moreRows(XTable.EOT);
         int rows1 = join1.getRowCount() - join1.getHeaderRowCount();

         if(rows1 >= FULL) {
            continue;
         }

         landed = true;
         System.out.println("JoinCancelCacheTest: " + chain + ", cancelled at " + rows1 +
                            " of " + FULL + " rows");
         AssetQuerySandbox box2 = new AssetQuerySandbox(ws);
         TableLens lens2 = box2.getTableLens("J1", AssetQuerySandbox.RUNTIME_MODE,
                                             new VariableTable());
         assertNotNull(lens2, "the query failed, see the log");
         JoinTableLens join2 = (JoinTableLens) Util.getNestedTable(lens2, JoinTableLens.class);
         lens2.moreRows(XTable.EOT);
         int rows2 = lens2.getRowCount() - lens2.getHeaderRowCount();

         assertNotSame(join1, join2, "the join cancelled at " + rows1 + " rows was taken " +
            "from the cache, the next sandbox got " + rows2 + " of " + FULL + " rows");
         assertEquals(FULL, rows2);
         assertTrue(join1.isCancelled(), "join stopped at " + rows1 + " of " + FULL +
            " rows reports not cancelled");
      }

      assertTrue(landed, "the cancel never landed while the join was running");
   }

   // T1(id, g) INNER JOIN T2(id2, g2) ON g = g2, in memory; no column is renamed
   private static Worksheet joinWorksheet() throws Exception {
      Worksheet ws = new Worksheet();
      int run = RUN.incrementAndGet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", "select pj.id, pj.g from pj where pj.id > -" +
                                          run, "id", "g");
      SQLBoundTableAssembly t2 = sqlTable(ws, "T2", "select pj.id as id2, pj.g as g2 from pj " +
                                          "where pj.id > -" + run, "id2", "g2");
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

   // the lenses from the top to the first one that is not a filter
   private static String chain(TableLens lens) {
      List<String> names = new ArrayList<>();

      for(TableLens table = lens; table != null;
          table = table instanceof TableFilter ? ((TableFilter) table).getTable() : null)
      {
         names.add(table.getClass().getSimpleName() +
                      (table instanceof CancellableTableLens ? "(C)" : ""));

         if(table instanceof JoinTableLens) {
            break;
         }
      }

      return String.join(" > ", names);
   }

   private static boolean awaitJoining(Thread thread) {
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);

      while(System.nanoTime() < end && thread.isAlive()) {
         for(StackTraceElement e : thread.getStackTrace()) {
            if(e.getClassName().endsWith("AssetDataCache$Processor") &&
               e.getMethodName().equals("join"))
            {
               return true;
            }
         }

         Thread.onSpinWait();
      }

      return false;
   }

   private static SQLBoundTableAssembly sqlTable(Worksheet ws, String name, String text,
                                                 String... columns)
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

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("joincancelcache");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   private static EmbeddedDataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }

   private interface Canceller {
      void cancel(AssetQuerySandbox box, TableLens lens) throws Exception;
   }
}
