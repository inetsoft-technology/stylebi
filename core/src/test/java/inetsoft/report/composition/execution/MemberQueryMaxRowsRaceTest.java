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
import inetsoft.util.CoreTool;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82290: the members of a join or concatenated table that is not merged into one SQL
 * statement run in parallel. Each member wrote its max rows (XQuery.HINT_MAX_ROWS) into one
 * shared variable table and JDBCHandler read it back later, so a member with max rows could
 * run unlimited and a member without could be cut to a sibling's max rows: the same
 * worksheet gave a different row count from run to run. Runs the real AssetQuerySandbox /
 * JoinQuery / ConcatenatedQuery / JDBCHandler path on Derby, configured as in
 * RowFetchFailureTest. The two members use different data sources, so they are joined in
 * memory, as they always are on a database without max rows support (Informix, ClickHouse).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  RowFetchFailureTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MemberQueryMaxRowsRaceTest {
   // the Derby database of RowFetchFailureTest.JdbcConfig
   private static final String DB = "memory:rowfetchfailure";
   private static final int ROWS = 35;
   private static final int MAX = 5;
   private static final int RUNS = 40;
   private static final AtomicInteger RUN = new AtomicInteger();

   @BeforeAll
   static void createTables() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "mqro", "mqrc" }) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(Exception ignore) {
               // first run
            }
         }

         stmt.executeUpdate("create table mqro (cid int)");
         stmt.executeUpdate("create table mqrc (id int)");

         for(int i = 1; i <= ROWS; i++) {
            stmt.executeUpdate("insert into mqro values (" + i + ")");
            stmt.executeUpdate("insert into mqrc values (" + i + ")");
         }
      }
   }

   @BeforeEach
   @AfterEach
   void clear() {
      CoreTool.clearUserMessage();
   }

   /**
    * T (max rows 5, cid 35..31) inner join C (no max rows, id 1..35) on cid = id has 5 rows
    * on every run. Before the fix: 35 (T lost its limit) or 0 (C was cut to 1..5).
    */
   @Test
   void inMemoryJoinKeepsEachMembersMaxRows() throws Exception {
      Map<Integer, Integer> counts = new TreeMap<>();

      for(int i = 0; i < RUNS; i++) {
         counts.merge(rowCount(TableAssemblyOperator.INNER_JOIN), 1, Integer::sum);
      }

      assertEquals(Map.of(MAX, RUNS), counts, "row count -> runs");
   }

   /**
    * T (max rows 5) union all C (no max rows) has 5 + 35 rows on every run. Before the fix:
    * 70 (T lost its limit) or 10 (C was cut to 5 rows).
    */
   @Test
   void concatenationKeepsEachMembersMaxRows() throws Exception {
      Map<Integer, Integer> counts = new TreeMap<>();

      for(int i = 0; i < RUNS; i++) {
         counts.merge(rowCount(TableAssemblyOperator.UNION), 1, Integer::sum);
      }

      assertEquals(Map.of(MAX + ROWS, RUNS), counts, "row count -> runs");
   }

   private static int rowCount(int operation) throws Exception {
      Worksheet ws = new Worksheet();
      // a run-unique literal, so no run is answered from the data cache
      int run = RUN.incrementAndGet();
      SQLBoundTableAssembly t = sqlTable(ws, "T", "dsa", "select mqro.cid from mqro where " +
                                         "mqro.cid > -" + run + " order by mqro.cid desc", "cid");
      t.setMaxRows(MAX);
      SQLBoundTableAssembly c = sqlTable(ws, "C", "dsb", "select mqrc.id from mqrc where " +
                                         "mqrc.id > -" + run, "id");
      TableAssemblyOperator operator = new TableAssemblyOperator();
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setOperation(operation);
      op.setLeftTable("T");
      op.setRightTable("C");
      TableAssembly composite;

      if(operation == TableAssemblyOperator.UNION) {
         op.setDistinct(false);
         operator.addOperator(op);
         composite = new ConcatenatedTableAssembly(
            ws, "U", new TableAssembly[] { t, c }, new TableAssemblyOperator[] { operator });
      }
      else {
         op.setLeftAttribute(t.getColumnSelection(false).getAttribute("cid"));
         op.setRightAttribute(c.getColumnSelection(false).getAttribute("id"));
         operator.addOperator(op);
         composite = new RelationalJoinTableAssembly(
            ws, "J", new TableAssembly[] { t, c }, new TableAssemblyOperator[] { operator });
      }

      ws.addAssembly(composite);
      ((ComposedTableAssembly) composite).update();
      AssetQuerySandbox init = new AssetQuerySandbox(ws);
      AssetEventUtil.initColumnSelection(init, composite);
      init.dispose();
      CoreTool.clearUserMessage();

      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      try {
         TableLens lens = box.getTableLens(composite.getName(), AssetQuerySandbox.RUNTIME_MODE,
                                           new VariableTable());
         assertNotNull(lens, "the query failed, see the log");
         lens.moreRows(XTable.EOT);
         return lens.getRowCount() - 1;
      }
      finally {
         box.dispose();
      }
   }

   private static SQLBoundTableAssembly sqlTable(Worksheet ws, String name, String dsName,
                                                 String text, String... columns)
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

      JDBCDataSource ds = dataSource(dsName);
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

   // a different name and url per member, so the join is not merged into one sql; the
   // connection pool of RowFetchFailureTest.JdbcConfig serves the same Derby database
   private static JDBCDataSource dataSource(String name) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("memberquerymaxrows-" + name);
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB + ";" + name + "=true");
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
