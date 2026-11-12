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
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.QueryManager;
import inetsoft.util.CoreTool;
import inetsoft.util.UserMessage;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81091: the user message of a join with no usable join column must reach every reader,
 * not only the one that ran the query. AssetDataCache used to cache the empty join and hand
 * it to the next sandbox without the message. Runs the real AssetQuerySandbox /
 * AssetDataCache / JDBCHandler path on Derby, configured as in RowFetchFailureTest.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  RowFetchFailureTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class InvalidJoinMessageCacheTest {
   private static final String DB = "memory:rowfetchfailure";
   private static final AtomicInteger RUN = new AtomicInteger();

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table pjm");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table pjm (id int, g int)");

         for(int i = 1; i <= 4; i++) {
            stmt.executeUpdate("insert into pjm values (" + i + ", " + (i % 2) + ")");
         }
      }
   }

   @BeforeEach
   void clear() {
      AssetDataCache.getCache().clearCache();
      CoreTool.clearUserMessage();
   }

   @AfterEach
   void reset() {
      CoreTool.clearUserMessage();
   }

   @Test
   void unresolvedJoinColumnMessageReachesTheSecondSandbox() throws Exception {
      Worksheet ws = joinWorksheet(true);

      for(int i = 0; i < 3; i++) {
         AssetQuerySandbox box = new AssetQuerySandbox(ws);
         box.setQueryManager(new QueryManager());
         TableLens lens = box.getTableLens("J1", AssetQuerySandbox.RUNTIME_MODE,
                                           new VariableTable());
         assertNotNull(lens, "the query failed, see the log");
         lens.moreRows(XTable.EOT);
         UserMessage message = CoreTool.getUserMessage();

         assertNotNull(message, "run " + i + " has no user message, it came from the cache");
         assertTrue(message.getMessage().contains("g2"), message.getMessage());
         CoreTool.clearUserMessage();
      }
   }

   /** The viewsheet path reads worksheet tables through AssetDataCache.getData. */
   @Test
   void unresolvedJoinColumnMessageReachesTheSecondGetData() throws Exception {
      Worksheet ws = joinWorksheet(true);

      for(int i = 0; i < 3; i++) {
         AssetQuerySandbox box = new AssetQuerySandbox(ws);
         box.setQueryManager(new QueryManager());
         TableLens lens = AssetDataCache.getCache().getData(
            "81091-" + i, (TableAssembly) ws.getAssembly("J1"), box, new QueryManager());
         assertNotNull(lens, "the query failed, see the log");
         lens.moreRows(XTable.EOT);
         UserMessage message = CoreTool.getUserMessage();

         assertNotNull(message, "run " + i + " has no user message, it came from the cache");
         assertTrue(message.getMessage().contains("g2"), message.getMessage());
         CoreTool.clearUserMessage();
      }
   }

   /**
    * A pending error on the thread, left by the tables built before, must not make the table
    * look as if its own run produced none: the mirror is read alone afterwards.
    */
   @Test
   void mirrorOverInvalidJoinKeepsItsMessageAfterLeftoverError() throws Exception {
      Worksheet ws = joinWorksheet(true);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M1", (WSAssembly) ws.getAssembly("J1"));
      ws.addAssembly(mirror);
      mirror.update();
      AssetQuerySandbox init = new AssetQuerySandbox(ws);
      AssetEventUtil.initColumnSelection(init, mirror);
      init.dispose();
      CoreTool.clearUserMessage();

      // the composer builds several tables in one request without clearing the messages
      AssetQuerySandbox first = new AssetQuerySandbox(ws);
      first.setQueryManager(new QueryManager());
      first.getTableLens("J1", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());
      assertNotNull(first.getTableLens("M1", AssetQuerySandbox.RUNTIME_MODE,
                                       new VariableTable()));
      CoreTool.clearUserMessage();

      for(int i = 0; i < 3; i++) {
         AssetQuerySandbox box = new AssetQuerySandbox(ws);
         box.setQueryManager(new QueryManager());
         TableLens lens = box.getTableLens("M1", AssetQuerySandbox.RUNTIME_MODE,
                                           new VariableTable());
         assertNotNull(lens, "the query failed, see the log");
         lens.moreRows(XTable.EOT);
         assertNotNull(CoreTool.getUserMessage(), "read " + i + " of the mirror has no message");
         assertEquals(0, cachedEntries("M1"), "the mirror of an invalid join was cached");
         assertEquals(0, cachedEntries("J1"), "the invalid join was cached");
      }
   }

   /** Messages that are not errors, and errors pending before the query, do not stop caching. */
   @Test
   void leftoverMessagesDoNotStopCachingAValidJoin() throws Exception {
      Worksheet ws = joinWorksheet(false);
      CoreTool.addUserWarning("left over warning");
      CoreTool.addUserMessage("left over info");
      CoreTool.addUserMessage(new UserMessage("left over error", ConfirmException.ERROR));
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      box.setQueryManager(new QueryManager());
      TableLens lens = box.getTableLens("J1", AssetQuerySandbox.RUNTIME_MODE,
                                        new VariableTable());
      assertNotNull(lens, "the query failed, see the log");
      lens.moreRows(XTable.EOT);

      assertTrue(cachedEntries("J1") > 0, "the valid join was not cached");
   }

   @Test
   void errorCountSeesDuplicatesButNotWarnings() {
      long start = CoreTool.getErrorMessageCount();
      CoreTool.addUserWarning("w");
      CoreTool.addUserMessage("i");
      assertEquals(start, CoreTool.getErrorMessageCount());
      CoreTool.addUserMessage(new UserMessage("e", ConfirmException.ERROR));
      CoreTool.addUserMessage(new UserMessage("e", ConfirmException.ERROR));
      assertEquals(start + 2, CoreTool.getErrorMessageCount());
   }

   private static long cachedEntries(String table) {
      return AssetDataCache.getCache().keySet().stream()
         .filter(k -> k.getAssemblyEntry() != null &&
                      k.getAssemblyEntry().toString().contains(table))
         .count();
   }

   @Test
   void validJoinGivesNoMessage() throws Exception {
      Worksheet ws = joinWorksheet(false);

      for(int i = 0; i < 2; i++) {
         AssetQuerySandbox box = new AssetQuerySandbox(ws);
         box.setQueryManager(new QueryManager());
         TableLens lens = box.getTableLens("J1", AssetQuerySandbox.RUNTIME_MODE,
                                           new VariableTable());
         assertNotNull(lens, "the query failed, see the log");
         lens.moreRows(XTable.EOT);
         assertTrue(lens.getRowCount() > 1);
         assertNull(CoreTool.getUserMessage());
      }
   }

   // T1(id, g) INNER JOIN T2(id2, g2) ON g = g2 (or g = nosuch), in memory
   private static Worksheet joinWorksheet(boolean broken) throws Exception {
      Worksheet ws = new Worksheet();
      int run = RUN.incrementAndGet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", "select pjm.id, pjm.g from pjm where " +
                                          "pjm.id > -" + run, "id", "g");
      // broken: the declared column g2 is not in the SQL result, as after a source change
      String t2Select = broken ? "select pjm.id as id2, pjm.g as g2x from " :
         "select pjm.id as id2, pjm.g as g2 from ";
      SQLBoundTableAssembly t2 = sqlTable(ws, "T2", t2Select + "pjm where pjm.id > -" + run,
                                          "id2", "g2");
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
      ds.setName("invalidjoinmsg");
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
}
