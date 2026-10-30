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
import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.VarSQL;
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
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79742: a worksheet pre-condition whose value is an SQL expression with a variable
 * and other text, e.g. {@code trim($(who))}, is merged into the SQL with the variable's
 * value spliced as a literal. The value was quoted without escaping, so an apostrophe
 * broke the query (no data) and a value could change the WHERE clause. The real merge
 * chain runs on embedded Derby: AssetQuerySandbox -> mergeWhere -> PreConditionListHandler
 * -> JDBCHandler -> VarSQL.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PreConditionSQLExpressionEscapeTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PreConditionSQLExpressionEscapeTest {
   private static final String DB = "memory:bug79742";
   /** The SQL text of each query sent to the database, before VarSQL. */
   private static final List<String> SENT = Collections.synchronizedList(new ArrayList<>());

   @Configuration
   static class TestConfig {
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
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster, eventPublisher);
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

      // runs the query's SQL on the Derby table with the real JDBCHandler
      @Bean
      public XSessionManager xSessionManager(XSessionService sessionService) throws Exception {
         XDataService dataService = mock(XDataService.class);
         when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
            .thenAnswer(inv -> {
               XQuery query = inv.getArgument(1);
               VariableTable vars = inv.getArgument(2);

               if(query instanceof JDBCQuery jquery) {
                  SENT.add(jquery.getSQLAsString());
               }

               JDBCHandler handler = new JDBCHandler();
               handler.connect(query.getDataSource(), vars);
               return handler.execute(query, vars, inv.getArgument(3), inv.getArgument(5));
            });
         return new XSessionManager(dataService, sessionService, mock(DataSourceRegistry.class));
      }
   }

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table people");
         }
         catch(Exception ignore) {
         }

         stmt.executeUpdate("create table people (id int, name varchar(40))");
         stmt.executeUpdate("insert into people values (1, 'Alice')");
         stmt.executeUpdate("insert into people values (2, 'O''Brien')");
         stmt.executeUpdate("insert into people values (3, 'Bob')");
         stmt.executeUpdate("insert into people values (4, 'C:\\dir')");
      }
   }

   @BeforeEach
   void setUp() {
      AssetDataCache.getCache().clearCache();
      SENT.clear();
   }

   @AfterEach
   void tearDown() {
      AssetDataCache.getCache().clearCache();
   }

   /** An apostrophe in the value is part of the string, so the matching row is returned. */
   @Test
   void apostropheInValueMatchesRow() throws Exception {
      assertEquals(List.of(2), ids("name", XCondition.EQUAL_TO, "trim($(who))", "O'Brien"));
      assertTrue(lastSQL().contains("trim('O''Brien')"), lastSQL());
   }

   /** The value can't close the literal and change the WHERE clause. */
   @Test
   void valueCannotChangeWhereClause() throws Exception {
      assertEquals(List.of(), ids("name", XCondition.EQUAL_TO, "trim($(who))",
                                  "nobody') OR ('1'='1"));
      assertEquals(List.of(), ids("name", XCondition.EQUAL_TO, "upper($(who))",
                                  "NOBODY') OR ('1'='1"));
   }

   /** A value without quotes is spliced as before. */
   @Test
   void plainValueUnchanged() throws Exception {
      assertEquals(List.of(1), ids("name", XCondition.EQUAL_TO, "trim($(who))", "Alice"));
      assertTrue(lastSQL().contains("trim('Alice')"), lastSQL());
   }

   /** A number is spliced unquoted, as before. */
   @Test
   void numberUnchanged() throws Exception {
      assertEquals(List.of(2), ids("id", XCondition.EQUAL_TO, "abs($(who))", 2));
      assertTrue(lastSQL().contains("abs(2)"), lastSQL());
   }

   /**
    * Each value of a multi-value variable is quoted and escaped on its own. The expression
    * has text besides the variable (a space), so the values are spliced into the IN list.
    */
   @Test
   void arrayValueEscapesEachElement() throws Exception {
      assertEquals(List.of(1, 2), ids("name", XCondition.ONE_OF, " $(who)",
                                      new Object[] { "Alice", "O'Brien" }), String.valueOf(SENT));
      assertTrue(lastSQL().contains("'Alice','O''Brien'"), lastSQL());
      assertEquals(List.of(), ids("name", XCondition.ONE_OF, " $(who)",
                                  new Object[] { "x", "nobody') OR ('1'='1" }));
   }

   /** Backslashes are doubled only for a dialect that treats them as an escape (MySQL). */
   @Test
   void backslashEscapedOnlyWhenDialectSaysSo() {
      assertEquals("O''Brien\\", VarSQL.escapeSQLLiteralValue("O'Brien\\", '\'', false));
      assertEquals("O''Brien\\\\", VarSQL.escapeSQLLiteralValue("O'Brien\\", '\'', true));
   }

   /**
    * On a dialect where a backslash is not an escape (Derby), it is kept as is, so a value
    * with a backslash still matches its row.
    */
   @Test
   void backslashKeptWhenDialectDoesNotEscapeIt() throws Exception {
      assertEquals(List.of(4), ids("name", XCondition.EQUAL_TO, "trim($(who))", "C:\\dir"));
      assertTrue(lastSQL().contains("trim('C:\\dir')"), lastSQL());
   }

   /**
    * On a dialect where a backslash is an escape (MySQL), the query's own SQL helper is
    * used, so the backslash is doubled and a {@code \'} in the value can't end the literal.
    * Only the SQL text is checked, because the database here is Derby.
    */
   @Test
   void backslashDoubledForMySQLDataSource() throws Exception {
      sqlText("mysql", "name", "trim($(who))", "x\\') OR ('1'='1");
      assertTrue(lastSQL().contains("trim('x\\\\'') OR (''1''=''1')"), lastSQL());
   }

   /** An expression that is exactly the variable is still bound as a JDBC parameter. */
   @Test
   void bareVariableStillBound() throws Exception {
      assertEquals(List.of(2), ids("name", XCondition.EQUAL_TO, "$(who)", "O'Brien"));
      assertFalse(lastSQL().contains("Brien"), lastSQL());
      assertEquals(List.of(), ids("name", XCondition.EQUAL_TO, "$(who)", "nobody' OR '1'='1"));
   }

   /**
    * The ids of T1 with the pre-condition {@code column op [SQL expression]} and the
    * variable {@code who}.
    */
   private List<Object> ids(String column, int op, String exp, Object who) throws Exception {
      TableLens lens = run(null, column, op, exp, who);
      assertTrue(lens != null && lens.getColCount() == 2, "the query failed: " + SENT);
      List<Object> vals = new ArrayList<>();
      lens.moreRows(0);

      for(int r = lens.getHeaderRowCount(); lens.moreRows(r); r++) {
         Object v = lens.getObject(r, 0);
         vals.add(v instanceof Number n ? n.intValue() : v);
      }

      Collections.sort(vals, Comparator.comparing(Object::toString));
      return vals;
   }

   /**
    * Run T1 with the pre-condition {@code column = [SQL expression]} on a data source whose
    * dialect is {@code product}, only to record the SQL text sent.
    */
   private void sqlText(String product, String column, String exp, Object who)
      throws Exception
   {
      try {
         run(product, column, XCondition.EQUAL_TO, exp, who);
      }
      catch(Exception ignore) {
         // the dialect's SQL may not run on Derby, the sent text is what is checked
      }
   }

   private TableLens run(String product, String column, int op, String exp, Object who)
      throws Exception
   {
      AssetDataCache.getCache().clearCache();
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = sqlTable(ws, product);
      ExpressionValue eval = new ExpressionValue();
      eval.setType(ExpressionValue.SQL);
      eval.setExpression(exp);
      AssetCondition cond = new AssetCondition();
      cond.setOperation(op);
      cond.setType("id".equals(column) ? XSchema.INTEGER : XSchema.STRING);
      cond.addValue(eval);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(table.getColumnSelection(false).getAttribute(column), cond, 0));
      table.setPreConditionList(list);

      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      box.getVariableTable().put("who", who);
      return box.getTableLens("T1", AbstractSheet.SHEET_RUNTIME_MODE);
   }

   private static String lastSQL() {
      assertFalse(SENT.isEmpty(), "no query sent");
      return SENT.get(SENT.size() - 1);
   }

   /**
    * T1: {@code select people.id, people.name from people} on the Derby table. A non-null
    * {@code product} makes the data source's SQL helper that dialect's.
    */
   private static SQLBoundTableAssembly sqlTable(Worksheet ws, String product)
      throws Exception
   {
      String text = "select people.id, people.name from people";
      UniformSQL usql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class, long.class);
      parse.setAccessible(true);
      parse.invoke(usql, text, 0, 4000L);
      usql.setSQLString(text, false);
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug79742");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);

      if(product != null) {
         ds.setName("bug79742_" + product);
         ds.setRuntimeProductName(product);
      }

      usql.setDataSource(ds);
      JDBCQuery query = new JDBCQuery();
      query.setName("T1");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);
      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      ColumnSelection columns = new ColumnSelection();

      for(String column : new String[] { "id", "name" }) {
         ColumnRef ref = new ColumnRef(new AttributeRef(column));
         ref.setDataType("id".equals(column) ? XSchema.INTEGER : XSchema.STRING);
         columns.addAttribute(ref);
      }

      table.setColumnSelection(columns, false);
      table.setColumnSelection((ColumnSelection) columns.clone(), true);
      ws.addAssembly(table);
      return table;
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
