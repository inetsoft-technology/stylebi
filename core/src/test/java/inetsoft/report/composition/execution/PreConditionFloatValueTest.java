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
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.*;
import inetsoft.util.CoreTool;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82410: a worksheet pre-condition on a DECIMAL column with a Float variable value
 * (0.15f) kept no 0.15 row: in memory the Float was compared as the widened double
 * 0.15000000596..., and in the SQL it was bound as a REAL, which Derby compares at that
 * precision too. Both sides must read the value as the decimal 0.15 for every operator.
 * The SQL side runs the real merge chain on embedded Derby (AssetQuerySandbox -> mergeWhere
 * -> PreConditionListHandler -> ConditionListHandler -> JDBCHandler) with a cold data cache;
 * the in-memory side filters a script column that is not merged into the SQL.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PreConditionFloatValueTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PreConditionFloatValueTest {
   private static final String DB = "memory:bug82410";
   /** The SQL text of each query sent to the database, before VarSQL. */
   private static final List<String> SENT = Collections.synchronizedList(new ArrayList<>());
   /** The condition values bound to each query sent to the database. */
   private static final List<Object> BOUND = Collections.synchronizedList(new ArrayList<>());

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

               for(Enumeration<String> e = vars.keys(); e.hasMoreElements();) {
                  String key = e.nextElement();

                  if(key.startsWith("dataselcond")) {
                     BOUND.add(vars.get(key));
                  }
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
            stmt.executeUpdate("drop table orders");
         }
         catch(SQLException ignore) {
            // first run in this JVM
         }

         // the sample database's orders.discount is NUMERIC(3, 2)
         stmt.executeUpdate("create table orders (order_id int, discount numeric(3, 2))");
         stmt.executeUpdate("insert into orders values (1, 0.10)");
         stmt.executeUpdate("insert into orders values (2, 0.15)");
         stmt.executeUpdate("insert into orders values (3, 0.15)");
         stmt.executeUpdate("insert into orders values (4, 0.20)");
      }
   }

   @BeforeEach
   void setUp() {
      clearCaches();
      SENT.clear();
      BOUND.clear();
   }

   @AfterEach
   void tearDown() {
      clearCaches();
   }

   /** label, operator, equal flag, variable values, expected order ids */
   static Stream<Arguments> cases() {
      return Stream.of(
         Arguments.of("= 0.15", XCondition.EQUAL_TO, false, new String[] { "0.15" }, List.of(2, 3)),
         Arguments.of("< 0.15", XCondition.LESS_THAN, false, new String[] { "0.15" }, List.of(1)),
         Arguments.of(">= 0.15", XCondition.GREATER_THAN, true, new String[] { "0.15" },
                      List.of(2, 3, 4)),
         Arguments.of("between 0.15 and 0.2", XCondition.BETWEEN, false,
                      new String[] { "0.15", "0.2" }, List.of(2, 3, 4)),
         Arguments.of("one of 0.15, 0.2", XCondition.ONE_OF, false,
                      new String[] { "0.15", "0.2" }, List.of(2, 3, 4)));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("cases")
   void floatVariableInSql(String label, int op, boolean equal, String[] values,
                           List<Integer> expected) throws Exception
   {
      assertEquals(expected, ids(true, op, equal, XSchema.FLOAT, values),
                   label + "; sql " + lastSql() + " bound " + BOUND);
      assertTrue(lastSql().contains("where"), "the condition must be merged into the SQL: " +
                 lastSql());
      assertFalse(BOUND.isEmpty(), "no bound condition value");
      new ArrayList<>(BOUND).forEach(
         v -> assertInstanceOf(BigDecimal.class, v, "bound " + BOUND));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("cases")
   void floatVariableInMemory(String label, int op, boolean equal, String[] values,
                              List<Integer> expected) throws Exception
   {
      assertEquals(expected, ids(false, op, equal, XSchema.FLOAT, values), label);
   }

   // control: a Double variable already matched on both sides and still does
   @ParameterizedTest(name = "{0}")
   @MethodSource("cases")
   void doubleVariable(String label, int op, boolean equal, String[] values,
                       List<Integer> expected) throws Exception
   {
      assertEquals(expected, ids(true, op, equal, XSchema.DOUBLE, values), "sql " + label);
      assertEquals(expected, ids(false, op, equal, XSchema.DOUBLE, values), "memory " + label);
   }

   // a cold cache for every query: JDBCHandler keys the query data cache by the bound values'
   // text only, so a Float 0.15 and a Double 0.15 query would share one entry
   private static void clearCaches() {
      AssetDataCache.getCache().clearCache();
      XSessionManager.clear();
   }

   private static String lastSql() {
      return SENT.isEmpty() ? "-" : SENT.get(SENT.size() - 1);
   }

   /**
    * Runs "column op $(v0) [, $(v1)]" as a pre-condition of a SQL-bound table, with each value
    * typed as a variable of the given type gets it. With sql, the condition is on the discount
    * column, which has the SQL type NUMERIC as a column dragged from the data source tree has,
    * and is merged into the SQL; otherwise it is on a script column copying discount, which is
    * filtered in memory.
    */
   private List<Integer> ids(boolean sql, int op, boolean equal, String varType, String[] values)
      throws Exception
   {
      clearCaches();
      SENT.clear();
      BOUND.clear();
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = sqlTable(ws);
      AssetCondition cond = new AssetCondition();
      cond.setOperation(op);
      cond.setEqual(equal);
      cond.setType(XSchema.DOUBLE);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      for(int i = 0; i < values.length; i++) {
         cond.addValue(new UserVariable("v" + i));
         box.getVariableTable().put("v" + i, CoreTool.getData(varType, values[i], true));
      }

      ConditionList list = new ConditionList();
      String column = sql ? "discount" : "DISC_JS";
      list.append(new ConditionItem(table.getColumnSelection(false).getAttribute(column), cond, 0));
      table.setPreConditionList(list);

      TableLens lens = box.getTableLens("T1", AbstractSheet.SHEET_RUNTIME_MODE);
      assertNotNull(lens);

      List<Integer> ids = new ArrayList<>();
      lens.moreRows(Integer.MAX_VALUE);
      assertTrue(lens.getColCount() >= 2, "query failed, placeholder lens returned");

      for(int r = lens.getHeaderRowCount(); lens.moreRows(r); r++) {
         ids.add(((Number) lens.getObject(r, 0)).intValue());
      }

      Collections.sort(ids);
      return ids;
   }

   private static SQLBoundTableAssembly sqlTable(Worksheet ws) throws Exception {
      String text = "select orders.order_id, orders.discount from orders";
      UniformSQL usql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class, long.class);
      parse.setAccessible(true);
      parse.invoke(usql, text, 0, 4000L);
      usql.setSQLString(text, false);

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug82410");
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
      AttributeRef idRef = new AttributeRef("order_id");
      idRef.setSqlType(Types.INTEGER);
      ColumnRef id = new ColumnRef(idRef);
      id.setDataType(XSchema.INTEGER);
      columns.addAttribute(id);

      AttributeRef discountRef = new AttributeRef("discount");
      discountRef.setSqlType(Types.NUMERIC);
      ColumnRef discount = new ColumnRef(discountRef);
      discount.setDataType(XSchema.DOUBLE);
      columns.addAttribute(discount);

      ExpressionRef script = new ExpressionRef(null, "DISC_JS");
      script.setExpression("field['discount']");
      ColumnRef discountJs = new ColumnRef(script);
      discountJs.setDataType(XSchema.DOUBLE);
      discountJs.setSQL(false);
      columns.addAttribute(discountJs);

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
