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
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78201: a worksheet table whose output headers PostProcessor.renameColumns changes (an
 * in-memory join whose joined columns get the _1 aliases, a column with a user alias) has an
 * AssetTableLens in the middle of its chain. It was not cancellable, so Stop query and
 * QueryManager.cancel() (cancel loading) stopped at it and the query ran to completion. Runs
 * the real AssetQuerySandbox / AssetDataCache / JDBCHandler path on Derby, configured as in
 * RowFetchFailureTest.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  RowFetchFailureTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AliasedTableCancelTest {
   // the Derby database of RowFetchFailureTest.JdbcConfig
   private static final String DB = "memory:rowfetchfailure";
   // pa(id, g = id % 2) joined with itself on g: ROWS * ROWS / 2 rows, slow enough to cancel
   private static final int ROWS = 1500;
   private static final int FULL = ROWS * ROWS / 2;
   // the cross join of pa with itself, for a single table
   private static final int CROSS = ROWS * ROWS;
   private static final AtomicInteger RUN = new AtomicInteger();

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table pa");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table pa (id int, g int)");
         conn.setAutoCommit(false);

         try(PreparedStatement insert = conn.prepareStatement("insert into pa values (?, ?)")) {
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
   void stopQueryStopsAnAliasedJoin() throws Exception {
      assertCancelStopsJoin(true, AliasedTableCancelTest::stopQuery);
   }

   /** QueryManager.cancel(), e.g. CancelLoadingService or a viewsheet event superseded. */
   @Test
   void queryManagerCancelStopsAnAliasedJoin() throws Exception {
      assertCancelStopsJoin(true, (box, lens) -> box.getQueryManager().cancel());
   }

   /** The same join whose right columns are not renamed has no AssetTableLens. */
   @Test
   void stopQueryStopsAJoinWithoutAliases() throws Exception {
      assertCancelStopsJoin(false, AliasedTableCancelTest::stopQuery);
   }

   /** A cancel after the aliased join completed leaves it whole and cached. */
   @Test
   void cancelOfACompleteAliasedJoinKeepsItCached() throws Exception {
      Worksheet ws = joinWorksheet(true);
      AssetQuerySandbox box1 = new AssetQuerySandbox(ws);
      box1.setQueryManager(new QueryManager());
      TableLens lens1 = box1.getTableLens("J1", AssetQuerySandbox.RUNTIME_MODE,
                                          new VariableTable());
      assertNotNull(lens1, "the query failed, see the log");
      String chain = chain(lens1);
      assertTrue(chain.contains("AssetTableLens"), chain);
      JoinTableLens join1 = (JoinTableLens) Util.getNestedTable(lens1, JoinTableLens.class);
      assertNotNull(join1, chain);
      lens1.moreRows(XTable.EOT);
      assertEquals(FULL, join1.getRowCount() - join1.getHeaderRowCount());

      stopQuery(box1, lens1);
      box1.getQueryManager().cancel();

      assertFalse(join1.isCancelled(), "a cancel of a complete join must leave it whole");
      assertFalse(AssetDataCache.isCancelled(lens1), chain);
      AssetQuerySandbox box2 = new AssetQuerySandbox(ws);
      TableLens lens2 = box2.getTableLens("J1", AssetQuerySandbox.RUNTIME_MODE,
                                          new VariableTable());
      assertNotNull(lens2, "the query failed, see the log");
      lens2.moreRows(XTable.EOT);

      assertSame(join1, Util.getNestedTable(lens2, JoinTableLens.class),
                 "the complete join was not kept in the cache");
      assertEquals(FULL, lens2.getRowCount() - lens2.getHeaderRowCount());
   }

   /** A single SQL table with a user column alias, Stop query while it is loading. */
   @Test
   void stopQueryStopsATableWithAUserAlias() throws Exception {
      assertStopQueryStopsSingleTable(true);
   }

   /** The same table without the alias has no AssetTableLens. */
   @Test
   void stopQueryStopsATableWithoutAlias() throws Exception {
      assertStopQueryStopsSingleTable(false);
   }

   private static void assertCancelStopsJoin(boolean alias, Canceller canceller)
      throws Exception
   {
      // the join is cancelled once its bases are loaded; a try where the join had already
      // completed by then proves nothing and is run again with a new query
      for(int i = 0; i < 5; i++) {
         Worksheet ws = joinWorksheet(alias);
         AssetQuerySandbox box1 = new AssetQuerySandbox(ws);
         // as RuntimeWorksheet does
         box1.setQueryManager(new QueryManager());
         TableLens lens1 = box1.getTableLens("J1", AssetQuerySandbox.RUNTIME_MODE,
                                             new VariableTable());
         assertNotNull(lens1, "the query failed, see the log");
         String chain = chain(lens1);
         // the renamed right columns add the AssetTableLens this test is about
         assertEquals(alias, chain.contains("AssetTableLens"), chain);
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
         System.out.println("AliasedTableCancelTest: " + chain + ", cancelled at " + rows1 +
                            " of " + FULL + " rows");

         assertTrue(rows1 < FULL, "the cancel did not stop the running join: " + chain);
         assertTrue(join1.isCancelled(), "join stopped at " + rows1 + " of " + FULL +
            " rows reports not cancelled");
         assertTrue(AssetDataCache.isCancelled(lens1), chain);

         AssetQuerySandbox box2 = new AssetQuerySandbox(ws);
         TableLens lens2 = box2.getTableLens("J1", AssetQuerySandbox.RUNTIME_MODE,
                                             new VariableTable());
         assertNotNull(lens2, "the query failed, see the log");
         JoinTableLens join2 = (JoinTableLens) Util.getNestedTable(lens2, JoinTableLens.class);
         lens2.moreRows(XTable.EOT);

         assertNotSame(join1, join2, "the cancelled join was taken from the cache");
         assertEquals(FULL, lens2.getRowCount() - lens2.getHeaderRowCount());
         return;
      }

      fail("the cancel never landed while the join was running");
   }

   private static void assertStopQueryStopsSingleTable(boolean alias) throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = sqlTable(
         ws, "S1", "select a.id, b.g from pa a, pa b where a.id > -" + RUN.incrementAndGet(),
         "id", "g");
      table.setSQLMergeable(false);

      if(alias) {
         for(boolean pub : new boolean[] { false, true }) {
            ((ColumnRef) table.getColumnSelection(pub).getAttribute(0)).setAlias("id_alias");
         }
      }

      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      box.setQueryManager(new QueryManager());
      TableLens lens = box.getTableLens("S1", AssetQuerySandbox.RUNTIME_MODE,
                                        new VariableTable());
      assertNotNull(lens, "the query failed, see the log");
      String chain = chain(lens);
      assertEquals(alias, chain.contains("AssetTableLens"), chain);
      assertEquals(alias ? "id_alias" : "id", lens.getObject(0, 0));

      XTable leaf = lens;

      while(leaf instanceof TableFilter) {
         leaf = ((TableFilter) leaf).getTable();
      }

      assertInstanceOf(CancellableTableLens.class, leaf, chain);
      lens.moreRows(1000);
      stopQuery(box, lens);
      lens.moreRows(XTable.EOT);
      int rows = lens.getRowCount() - lens.getHeaderRowCount();
      System.out.println("AliasedTableCancelTest: " + chain + ", cancelled at " + rows +
                         " of " + CROSS + " rows");

      assertTrue(rows < CROSS, "the cancel did not stop the loading table: " + chain);
      assertTrue(((CancellableTableLens) leaf).isCancelled(), chain);
   }

   // the statement of WSQueryService.stopQuery
   private static void stopQuery(AssetQuerySandbox box, TableLens lens) {
      CancellableTableLens cancel = (CancellableTableLens) Util.getNestedTable(
         lens, CancellableTableLens.class);

      if(cancel != null) {
         cancel.cancel();
      }
      else {
         box.getQueryManager().cancel();
      }
   }

   // T1(id, g) INNER JOIN T2 ON g, in memory; T2(id, g) gets the aliases id_1, g_1 in the
   // join, T2(id2, g2) keeps its names
   private static Worksheet joinWorksheet(boolean alias) throws Exception {
      Worksheet ws = new Worksheet();
      int run = RUN.incrementAndGet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", "select pa.id, pa.g from pa where pa.id > -" +
                                          run, "id", "g");
      SQLBoundTableAssembly t2 = alias ?
         sqlTable(ws, "T2", "select pa.id, pa.g from pa where pa.id > -" + run, "id", "g") :
         sqlTable(ws, "T2", "select pa.id as id2, pa.g as g2 from pa where pa.id > -" + run,
                  "id2", "g2");
      t1.setSQLMergeable(false);
      t2.setSQLMergeable(false);
      TableAssemblyOperator operator = new TableAssemblyOperator();
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setOperation(TableAssemblyOperator.INNER_JOIN);
      op.setLeftTable("T1");
      op.setRightTable("T2");
      op.setLeftAttribute(t1.getColumnSelection(false).getAttribute("g"));
      op.setRightAttribute(t2.getColumnSelection(false).getAttribute(alias ? "g" : "g2"));
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

      for(XTable table = lens; table != null;
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
      ds.setName("aliasedtablecancel");
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
