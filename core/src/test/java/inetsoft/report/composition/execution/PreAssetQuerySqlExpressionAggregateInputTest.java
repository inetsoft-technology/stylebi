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
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.jdbc.*;
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
 * Bug #82381: on a mergeable SQL-bound table whose aggregate info is not merged into the SQL
 * (Mode is not mergeable; Derby can't group by an expression), a SQL expression column that
 * reads a column some aggregate also uses was not selected from the database. It was then
 * run as JavaScript, so {@code CASE ... END} failed with a SyntaxError and
 * {@code id + 9007199254740992} was computed in JS doubles (wrong groups). The real merge
 * chain runs on embedded Derby: AssetQuerySandbox -> PreAssetQuery.merge -> JDBCHandler.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PreAssetQuerySqlExpressionAggregateInputTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PreAssetQuerySqlExpressionAggregateInputTest {
   private static final String DB = "memory:bug82381";
   private static final String[] STATES = { "NY", "CA", "TX", "nj", "Wa" };
   private static final int ROWS = 35;
   /** The SQL text of each query sent to the database. */
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
            stmt.executeUpdate("drop table customers");
         }
         catch(Exception ignore) {
         }

         stmt.executeUpdate("create table customers (customer_id int, state varchar(10))");

         for(int id = 1; id <= ROWS; id++) {
            stmt.executeUpdate("insert into customers values (" + id + ", '" + state(id) + "')");
         }
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

   /**
    * The reporter's shape: group by a SQL CASE expression that reads both aggregated
    * columns, with Count(customer_id) and Mode(state). The CASE is evaluated by the database.
    */
   @Test
   void caseExpressionGroupWithCountAndMode() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = sqlTable(ws);
      ColumnRef x = addExpression(table, "CASE WHEN MOD(field['customer_id'],2)=1 " +
         "THEN UPPER(field['state']) ELSE LOWER(field['state']) END", XSchema.STRING, true);
      AggregateInfo ainfo = table.getAggregateInfo();
      table.setAggregate(true);
      ainfo.addGroup(new GroupRef(x));
      ainfo.addAggregate(new AggregateRef(column(table, "customer_id"), AggregateFormula.COUNT_ALL));
      ainfo.addAggregate(new AggregateRef(column(table, "state"), AggregateFormula.MODE));

      Map<Object, Object> expected = new TreeMap<>();

      for(int id = 1; id <= ROWS; id++) {
         String key = id % 2 == 1 ? state(id).toUpperCase() : state(id).toLowerCase();
         expected.merge(key, 1, (a, b) -> (Integer) a + (Integer) b);
      }

      assertEquals(10, expected.size());
      assertEquals(expected, pairs(run(ws), "X", "customer_id"), String.valueOf(SENT));
   }

   /**
    * A SQL expression that is valid JavaScript (number + a constant above 2^53) used as a
    * group with Count reading the same column. The database computes it exactly, so every
    * customer is its own group (JS doubles merged neighbours into 19 groups).
    */
   @Test
   void jsValidSqlExpressionGroupIsExact() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = sqlTable(ws);
      ColumnRef x = addExpression(table, "field['customer_id'] + 9007199254740992",
                                  XSchema.LONG, true);
      AggregateInfo ainfo = table.getAggregateInfo();
      table.setAggregate(true);
      ainfo.addGroup(new GroupRef(x));
      ainfo.addAggregate(new AggregateRef(column(table, "customer_id"), AggregateFormula.COUNT_ALL));

      Map<Object, Object> groups = pairs(run(ws), "X", "customer_id");
      assertEquals(ROWS, groups.size(), String.valueOf(groups));
      assertTrue(groups.values().stream().allMatch(c -> ((Number) c).intValue() == 1),
                 String.valueOf(groups));
   }

   /** A SQL CASE flag used as a Sum input, grouped by state, with Count(customer_id). */
   @Test
   void caseExpressionAsSumInput() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = sqlTable(ws);
      ColumnRef x = addExpression(table, "CASE WHEN MOD(field['customer_id'],2)=1 THEN 1 ELSE 0 END",
                                  XSchema.INTEGER, true);
      AggregateInfo ainfo = table.getAggregateInfo();
      table.setAggregate(true);
      ainfo.addGroup(new GroupRef(column(table, "state")));
      ainfo.addAggregate(new AggregateRef(x, AggregateFormula.SUM));
      ainfo.addAggregate(new AggregateRef(column(table, "customer_id"), AggregateFormula.COUNT_ALL));

      Map<Object, Object> expected = new TreeMap<>();

      for(int id = 1; id <= ROWS; id++) {
         expected.merge(state(id), (double) (id % 2), (a, b) -> (Double) a + (Double) b);
      }

      assertEquals(expected, pairs(run(ws), "state", "X"), String.valueOf(SENT));
   }

   /**
    * A SQL expression whose own text is an aggregate, {@code max(field['customer_id'])}, used
    * as a Sum input keeps today's result: it is not selected next to the detail columns
    * (that SQL fails), and evaluates per row as before.
    */
   @Test
   void sqlAggregateTextAsSumInputUnchanged() throws Exception {
      assertEquals(sumOfIdsByState(), maxAsSumInput(true), String.valueOf(SENT));
   }

   /** The same text as a script expression is unchanged. */
   @Test
   void scriptAggregateTextAsSumInputUnchanged() throws Exception {
      assertEquals(sumOfIdsByState(), maxAsSumInput(false), String.valueOf(SENT));
   }

   private Map<Object, Object> maxAsSumInput(boolean sql) throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = sqlTable(ws);
      ColumnRef x = addExpression(table, "max(field['customer_id'])", XSchema.DOUBLE, sql);
      AggregateInfo ainfo = table.getAggregateInfo();
      table.setAggregate(true);
      ainfo.addGroup(new GroupRef(column(table, "state")));
      ainfo.addAggregate(new AggregateRef(x, AggregateFormula.SUM));
      ainfo.addAggregate(new AggregateRef(column(table, "customer_id"), AggregateFormula.COUNT_ALL));
      return pairs(run(ws), "state", "X");
   }

   private static Map<Object, Object> sumOfIdsByState() {
      Map<Object, Object> expected = new TreeMap<>();

      for(int id = 1; id <= ROWS; id++) {
         expected.merge(state(id), (double) id, (a, b) -> (Double) a + (Double) b);
      }

      return expected;
   }

   private static String state(int id) {
      return STATES[id % STATES.length];
   }

   private TableLens run(Worksheet ws) throws Exception {
      AssetDataCache.getCache().clearCache();
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      return box.getTableLens("T1", AbstractSheet.SHEET_RUNTIME_MODE);
   }

   /**
    * Map of the key column's value to the value column's value, over the data rows.
    * Numbers in the value column are normalised (counts to Integer, others to Double).
    */
   private static Map<Object, Object> pairs(TableLens lens, String key, String value) {
      assertNotNull(lens, "the query failed: " + SENT);
      lens.moreRows(TableLens.EOT);
      int kcol = col(lens, key);
      int vcol = col(lens, value);
      Map<Object, Object> map = new TreeMap<>(Comparator.comparing(Object::toString));

      for(int r = lens.getHeaderRowCount(); lens.moreRows(r); r++) {
         Object k = lens.getObject(r, kcol);
         Object v = lens.getObject(r, vcol);

         if(v instanceof Number n) {
            v = "customer_id".equals(value) ? (Object) n.intValue() : (Object) n.doubleValue();
         }

         assertNull(map.put(k, v), "duplicate group " + k + " in " + SENT);
      }

      return map;
   }

   private static int col(TableLens lens, String name) {
      for(int c = 0; c < lens.getColCount(); c++) {
         if(name.equals(String.valueOf(lens.getObject(0, c)))) {
            return c;
         }
      }

      fail("no column " + name + " in the result; SQL: " + SENT);
      return -1;
   }

   private static ColumnRef column(TableAssembly table, String name) {
      return (ColumnRef) table.getColumnSelection(false).getAttribute(name);
   }

   /** Add the expression column X to T1, SQL ({@code sql} true) or script. */
   private static ColumnRef addExpression(SQLBoundTableAssembly table, String expression,
                                          String dataType, boolean sql)
   {
      ExpressionRef exp = new ExpressionRef(null, "X");
      exp.setExpression(expression);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(dataType);
      column.setSQL(sql);
      ColumnSelection columns = table.getColumnSelection(false);
      columns.addAttribute(column);
      table.setColumnSelection(columns, false);
      return column;
   }

   /** T1: {@code select customers.customer_id, customers.state from customers} on Derby. */
   private static SQLBoundTableAssembly sqlTable(Worksheet ws) throws Exception {
      String text = "select customers.customer_id, customers.state from customers";
      UniformSQL usql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class, long.class);
      parse.setAccessible(true);
      parse.invoke(usql, text, 0, 4000L);
      usql.setSQLString(text, false);
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug82381");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
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

      for(String column : new String[] { "customer_id", "state" }) {
         ColumnRef ref = new ColumnRef(new AttributeRef(column));
         ref.setDataType("customer_id".equals(column) ? XSchema.INTEGER : XSchema.STRING);
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
