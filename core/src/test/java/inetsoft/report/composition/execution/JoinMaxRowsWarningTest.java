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
import inetsoft.report.internal.Util;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Catalog;
import inetsoft.util.CoreTool;
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
 * Bug #79657: a join that is merged into one SQL statement is limited by join.table.maxrows
 * (JoinQuery sets it as the max rows of the merged query), but the merged query did not carry
 * the limit as a property, so JDBCHandler did not hand it to the result node and
 * XNodeTableLens never warned "Join table limited to N rows". Runs the real
 * AssetQuerySandbox / XSessionManager / JDBCHandler path on Derby, configured as in
 * RowFetchFailureTest.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  RowFetchFailureTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JoinMaxRowsWarningTest {
   // the Derby database of RowFetchFailureTest.JdbcConfig
   private static final String DB = "memory:rowfetchfailure";
   // jm(id, g = id % 2) joined with itself on g: ROWS * ROWS / 2 rows
   private static final int ROWS = 40;
   private static final int FULL = ROWS * ROWS / 2;
   private static final AtomicInteger RUN = new AtomicInteger();

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table jm");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table jm (id int, g int)");

         for(int i = 1; i <= ROWS; i++) {
            stmt.executeUpdate("insert into jm values (" + i + ", " + (i % 2) + ")");
         }
      }
   }

   @BeforeEach
   void clear() {
      CoreTool.clearUserMessage();
   }

   @AfterEach
   void reset() {
      SreeEnv.remove("join.table.maxrows");
      CoreTool.clearUserMessage();
   }

   /** A merged join with more rows than the limit warns once the rows are read. */
   @Test
   void mergedJoinOverTheLimitWarns() throws Exception {
      SreeEnv.setProperty("join.table.maxrows", "100");
      TableLens lens = joinLens();
      lens.moreRows(XTable.EOT);

      assertEquals(100, lens.getRowCount() - 1, "the join is limited to the max rows");
      assertTrue(CoreTool.existUserMessage(warning(100)), "no join limit warning");
   }

   /** The #66074 half: a merged join that does not reach the limit does not warn. */
   @Test
   void mergedJoinUnderTheLimitDoesNotWarn() throws Exception {
      SreeEnv.setProperty("join.table.maxrows", "100000");
      TableLens lens = joinLens();
      lens.moreRows(XTable.EOT);

      assertEquals(FULL, lens.getRowCount() - 1);
      assertFalse(CoreTool.existUserMessage(warning(100000)), "unexpected join limit warning");
   }

   private static String warning(int max) {
      return Catalog.getCatalog().getString("join.table.limited", max);
   }

   // T1(id, g) INNER JOIN T2(id2, g2) ON g = g2, merged into one sql
   private static TableLens joinLens() throws Exception {
      Worksheet ws = new Worksheet();
      int run = RUN.incrementAndGet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", "select jm.id, jm.g from jm where jm.id > -" +
                                          run, "id", "g");
      SQLBoundTableAssembly t2 = sqlTable(ws, "T2", "select jm.id as id2, jm.g as g2 from jm " +
                                          "where jm.id > -" + run, "id2", "g2");
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
      AssetQuerySandbox init = new AssetQuerySandbox(ws);
      AssetEventUtil.initColumnSelection(init, join);
      init.dispose();
      CoreTool.clearUserMessage();

      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      TableLens lens = box.getTableLens("J1", AssetQuerySandbox.RUNTIME_MODE,
                                        new VariableTable());
      assertNotNull(lens, "the query failed, see the log");
      assertNotNull(Util.getNestedTable(lens, XNodeTableLens.class),
                    "the join was not merged into the sql: " + lens.getClass());
      return lens;
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
      ds.setName("joinmaxrowswarning");
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
