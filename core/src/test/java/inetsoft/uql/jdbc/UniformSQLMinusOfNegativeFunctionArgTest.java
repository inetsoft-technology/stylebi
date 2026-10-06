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
package inetsoft.uql.jdbc;

import inetsoft.mv.MVManager;
import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.report.composition.execution.*;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.*;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77754 and Bug #77642 together. A minus of a negative after a qualified column inside a
 * function argument or a group by item ({@code abs(a.V - -5)}) needs both fixes: #77754 keeps
 * the text from becoming the line comment {@code a.V-- 5}, and #77642 keeps SQLHelper from
 * quoting {@code V- - 5} as one column ({@code abs(a."V- - 5")}). Before both it was
 * {@code abs(a."V-- 5")}, which the database rejects.
 *
 * Also a SQL bound worksheet table whose select list holds {@code abs(o.amt - 5) as d}, merged
 * and generated from its structure, which was generated as {@code abs(o."amt-5")} (Bug #77642).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  UniformSQLMinusOfNegativeFunctionArgTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLMinusOfNegativeFunctionArgTest {
   private static final String DB = "memory:bug77754funcarg";

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

      // the derby helper reads the product version through the repository (expression
      // group by from 10.3)
      @Bean
      public XRepository xRepository() throws Exception {
         XRepository repository = mock(XRepository.class);
         when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
            XNode node = new XNode("properties");
            node.setAttribute("DBProductVersion", "10.17");
            return node;
         });
         return repository;
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
         manager.setCacheData(false);
         return manager;
      }
   }

   @BeforeAll
   static void createTables() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "a", "o" }) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(Exception ignore) {
               // first run
            }
         }

         stmt.executeUpdate("create table a (id int, V int, W int)");
         stmt.executeUpdate("insert into a values (1, 2, 1), (2, 9, 3), (3, 4, 6)");
         stmt.executeUpdate("create table o (cat varchar(5), amt int)");
         stmt.executeUpdate("insert into o values ('x', 10), ('x', 20), ('y', 5), ('y', 7), " +
                               "('z', 1)");
      }
   }

   /**
    * The generated text on Derby, and the rows of the generated sql are the rows of the sql
    * as written. Was abs(a."V-- 5") and so on, "Column 'A.V-- 5' is either not in any table".
    */
   @Test
   void minusOfNegativeRunsAsWritten() throws Exception {
      String[][] queries = {
         { "select abs(a.V - -5) from a", "select abs(a.V- - 5) from a" },
         { "select sum(a.V - -5) from a group by a.id", "select sum(a.V- - 5) from a group by a.id" },
         { "select a.V - -5, count(*) from a group by a.V - -5",
           "select a.V- - 5, count(*) from a group by a.V- - 5" },
         { "select a.id from a order by abs(a.V - -5)",
           "select a.id from a order by abs(a.V- - 5) asc" },
         { "select max(a.V - -5) as m from a order by max(a.V - -5)", null },
         { "select a.id from a where a.V in (select abs(a.W - -1) from a)", null },
      };

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         for(String[] query : queries) {
            UniformSQL sql = parse(query[0], dataSource());
            assertEquals("DerbyHelper", SQLHelper.getSQLHelper(sql).getClass().getSimpleName());
            String generated = regenerate(sql);

            if(query[1] != null) {
               assertEquals(query[1], generated, query[0]);
            }

            assertFalse(generated.contains("--"), generated);
            assertFalse(generated.contains("\""), generated);
            // round-trip guard
            assertEquals(generated, regenerate(parse(generated, dataSource())), generated);

            List<String> expected = rows(stmt, query[0]);
            assertFalse(expected.isEmpty() || expected.get(0).startsWith("ERR"),
                        query[0] + " " + expected);
            assertEquals(expected, rows(stmt, generated), query[0] + " -> " + generated);
         }
      }
   }

   /**
    * The helpers that store the segments unquoted write the function argument as parsed.
    */
   @Test
   void minusOfNegativeOnOtherHelpers() throws Exception {
      Object[][] helpers = {
         { null, "SQLHelper" },
         { dataSource("oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:x", "oracle"),
           "OracleSQLHelper" },
         { dataSource("com.microsoft.sqlserver.jdbc.SQLServerDriver", "jdbc:sqlserver://localhost",
                      "sql server"), "SQLServerHelper" },
      };

      for(Object[] helper : helpers) {
         UniformSQL sql = parse("select abs(a.V - -5) from a", (JDBCDataSource) helper[0]);
         assertEquals(helper[1], SQLHelper.getSQLHelper(sql).getClass().getSimpleName());
         assertEquals("select abs(a.V- - 5) from a", regenerate(sql), (String) helper[1]);

         sql = parse("select a.V - -5, count(*) from a group by a.V - -5",
                     (JDBCDataSource) helper[0]);
         assertTrue(regenerate(sql).endsWith(" group by a.V- - 5"),
                    helper[1] + ": " + regenerate(sql));
      }
   }

   /**
    * A SQL bound worksheet table with abs(o.amt - 5) as d, made distinct or with a column
    * hidden in the worksheet, is merged and generated from its structure. It was generated
    * as abs(o."amt-5") and failed.
    */
   @Test
   void mergedWorksheetFunctionOfMinus() throws Exception {
      String text = "select o.cat, abs(o.amt - 5) as d from o";

      for(boolean distinct : new boolean[] { true, false }) {
         Worksheet ws = new Worksheet();
         SQLBoundTableAssembly table = sqlTable(ws, text);

         if(distinct) {
            table.setDistinct(true);
         }
         else {
            ColumnSelection columns = table.getColumnSelection(false);
            ((ColumnRef) find(columns, "cat")).setVisible(false);
            table.setColumnSelection(columns, false);
         }

         table.update();

         VariableTable vars = new VariableTable();
         AssetQuery merged = AssetQuery.createAssetQuery(
            table, AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), false, -1L, true,
            false);
         merged.merge(vars);
         JDBCQuery jquery = merged.getQuery();
         String sql = jquery.getSQLAsString().replaceAll("\\s+", " ").trim();
         assertFalse(((UniformSQL) jquery.getSQLDefinition()).hasSQLString(), sql);
         assertEquals(distinct ? "select distinct o.cat, abs(o.amt-5) as d from o" :
                         "select abs(o.amt-5) as d from o", sql);

         try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
            String expected = distinct ? "select distinct o.cat, abs(o.amt - 5) as d from o" :
               "select abs(o.amt - 5) as d from o";
            assertEquals(rows(stmt, expected), rows(stmt, sql), sql);
         }

         AssetQuery query = AssetQuery.createAssetQuery(
            table, AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), false, -1L, true,
            false);
         TableLens lens = query.getTableLens(vars);
         assertNotNull(lens, "the query failed, see the log: " + sql);
         assertEquals(distinct ? List.of("x|15", "x|5", "y|0", "y|2", "z|4") :
                         List.of("0", "15", "2", "4", "5"), rows(lens), sql);
      }
   }

   // as SQLQueryDialogService.setUpTableWithSQLString builds a sql-edited table, with the
   // columns named as QueryManagerService.getColumnSelection names them
   private static SQLBoundTableAssembly sqlTable(Worksheet ws, String text) throws Exception {
      UniformSQL usql = new UniformSQL();
      JDBCDataSource ds = dataSource();
      usql.setDataSource(ds);

      synchronized(usql) {
         usql.setParseSQL(true);
         usql.setSQLString(text, true);
         usql.wait(10000);
      }

      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), text);

      JDBCQuery query = new JDBCQuery();
      query.setName("T1");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);
      table.setProperty("no_cache", "true");

      JDBCSelection selection = (JDBCSelection) usql.getSelection();
      ColumnSelection columns = new ColumnSelection();

      for(int i = 0; i < selection.getColumnCount(); i++) {
         String path = selection.getColumn(i);
         String alias = selection.getAlias(i);
         String name = alias != null && !alias.isEmpty() ? alias :
            path.substring(path.lastIndexOf('.') + 1);
         ColumnRef ref = new ColumnRef(new AttributeRef(name));
         ref.setQueryExpressionField(selection.isExpression(i));
         ref.setDataType(name.equals("cat") ? XSchema.STRING : XSchema.INTEGER);
         columns.addAttribute(ref);
      }

      table.setColumnSelection(columns, false);
      table.setColumnSelection((ColumnSelection) columns.clone(), true);
      ws.addAssembly(table);
      return table;
   }

   private static DataRef find(ColumnSelection columns, String name) {
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(name.equals(column.getAttribute())) {
            return column;
         }
      }

      throw new AssertionError("no column " + name + " in " + columns);
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();

      if(ds != null) {
         sql.setDataSource(ds);
      }

      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      return sql;
   }

   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   // the rows as sorted cells, in the order of the result if the query has an order by
   private static List<String> rows(Statement stmt, String query) {
      List<String> rows = new ArrayList<>();

      try(ResultSet rs = stmt.executeQuery(query)) {
         int count = rs.getMetaData().getColumnCount();

         while(rs.next()) {
            List<String> row = new ArrayList<>();

            for(int i = 1; i <= count; i++) {
               row.add(String.valueOf(rs.getObject(i)));
            }

            Collections.sort(row);
            rows.add(String.join("|", row));
         }
      }
      catch(SQLException ex) {
         return List.of("ERR " + ex.getMessage());
      }

      if(!query.toLowerCase(Locale.ROOT).contains(" order by ")) {
         Collections.sort(rows);
      }

      return rows;
   }

   private static List<String> rows(TableLens lens) {
      List<String> rows = new ArrayList<>();
      lens.moreRows(Integer.MAX_VALUE);

      for(int r = 1; r < lens.getRowCount(); r++) {
         List<String> row = new ArrayList<>();

         for(int c = 0; c < lens.getColCount(); c++) {
            Object value = lens.getObject(r, c);
            row.add(value instanceof Number ? String.valueOf(((Number) value).intValue()) :
                       String.valueOf(value));
         }

         rows.add(String.join("|", row));
      }

      Collections.sort(rows);
      return rows;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77754funcarg");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   // a data source with a real driver and url, so the helper of the database is used
   private static JDBCDataSource dataSource(String driver, String url, String product) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77754funcarg" + product);
      ds.setDriver(driver);
      ds.setURL(url);
      ds.setRuntimeProductName(product);
      // otherwise the oracle helper asks the repository for it
      ds.setProductVersion("10.0");
      return ds;
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
