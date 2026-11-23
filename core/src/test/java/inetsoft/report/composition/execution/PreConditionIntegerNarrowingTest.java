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
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.*;
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
import java.math.BigInteger;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82099: a worksheet pre-condition "INTEGER column = $(v)" merged into the SQL narrowed
 * a Number value with {@code Number.intValue()}, so a Long 4294967297 (2^32+1) matched the row
 * with id 1 and a fractional 2.5 matched id 2. A number that is not exactly an int must match
 * no row for "=" and every non-null row for "is not equal to". The real merge chain runs on
 * embedded Derby: AssetQuerySandbox -> mergeWhere -> PreConditionListHandler ->
 * ConditionListHandler.fixConditionValue -> JDBCHandler.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PreConditionIntegerNarrowingTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PreConditionIntegerNarrowingTest {
   private static final String DB = "memory:bug82099";
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
            stmt.executeUpdate("drop table cats");
         }
         catch(SQLException ignore) {
            // first run in this JVM
         }

         stmt.executeUpdate("create table cats (id int, name varchar(40))");
         stmt.executeUpdate("insert into cats values (1, 'Games')");
         stmt.executeUpdate("insert into cats values (2, 'Educational')");
         stmt.executeUpdate("insert into cats values (3, 'Other')");
         stmt.executeUpdate("insert into cats values (null, 'Unknown')");
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

   /** Numbers that are not exactly an int, so no INTEGER value can equal them. */
   static Stream<Arguments> inexactValues() {
      return Stream.of(
         Arguments.of("Long 2^32+1", 4294967297L),
         Arguments.of("Long 2^32+2", 4294967298L),
         Arguments.of("Long -(2^32)+3", -4294967293L),
         Arguments.of("Long Integer.MAX_VALUE+1", 2147483648L),
         Arguments.of("BigDecimal 2^32+1", new BigDecimal("4294967297")),
         Arguments.of("BigInteger 2^32+2", new BigInteger("4294967298")),
         Arguments.of("Double 2.5", 2.5d));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("inexactValues")
   void equalToInexactNumberMatchesNoRow(String label, Object value) throws Exception {
      assertEquals(List.of(), ids(value, false),
                   "= " + label + " must match no row; sql " + lastSql());
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("inexactValues")
   void notEqualToInexactNumberMatchesEveryNonNullRow(String label, Object value)
      throws Exception
   {
      assertEquals(List.of(1, 2, 3), ids(value, true),
                   "<> " + label + " must match every non-null row; sql " + lastSql());
   }

   /** Numbers that are exactly an int keep matching. */
   static Stream<Arguments> exactValues() {
      return Stream.of(
         Arguments.of("Integer 2", 2),
         Arguments.of("Long 2", 2L),
         Arguments.of("Double 2.0", 2.0d),
         Arguments.of("BigDecimal 2.00", new BigDecimal("2.00")),
         Arguments.of("Short 2", (short) 2),
         Arguments.of("String 2", "2"));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("exactValues")
   void equalToExactValueMatchesItsRow(String label, Object value) throws Exception {
      assertEquals(List.of(2), ids(value, false), "= " + label + "; sql " + lastSql());
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("exactValues")
   void notEqualToExactValueExcludesItsRow(String label, Object value) throws Exception {
      assertEquals(List.of(1, 3), ids(value, true), "<> " + label + "; sql " + lastSql());
   }

   private static String lastSql() {
      return SENT.isEmpty() ? "-" : SENT.get(SENT.size() - 1);
   }

   /**
    * Runs "id = $(v)" (or its negation) as a pre-condition of a SQL-bound table whose id column
    * has the SQL type INTEGER, as a column dragged from the data source tree has.
    */
   private List<Object> ids(Object value, boolean negated) throws Exception {
      AssetDataCache.getCache().clearCache();
      SENT.clear();
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = sqlTable(ws);
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setType(XSchema.INTEGER);
      cond.setNegated(negated);
      cond.addValue(new UserVariable("v"));
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(table.getColumnSelection(false).getAttribute("id"), cond, 0));
      table.setPreConditionList(list);

      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      box.getVariableTable().put("v", value);
      TableLens lens = box.getTableLens("T1", AbstractSheet.SHEET_RUNTIME_MODE);
      assertNotNull(lens);
      assertTrue(lastSql().contains("where"), "the condition must be merged into the SQL: " +
                 lastSql());

      List<Object> ids = new ArrayList<>();
      lens.moreRows(Integer.MAX_VALUE);
      assertTrue(lens.getColCount() >= 2, "query failed, placeholder lens returned");

      for(int r = lens.getHeaderRowCount(); lens.moreRows(r); r++) {
         ids.add(lens.getObject(r, 0));
      }

      ids.sort(Comparator.nullsFirst(Comparator.comparing(o -> ((Number) o).intValue())));
      return ids;
   }

   private static SQLBoundTableAssembly sqlTable(Worksheet ws) throws Exception {
      String text = "select cats.id, cats.name from cats";
      UniformSQL usql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class, long.class);
      parse.setAccessible(true);
      parse.invoke(usql, text, 0, 4000L);
      usql.setSQLString(text, false);

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug82099");
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

      for(String column : new String[] { "id", "name" }) {
         AttributeRef aref = new AttributeRef(column);

         if("id".equals(column)) {
            aref.setSqlType(Types.INTEGER);
         }

         ColumnRef ref = new ColumnRef(aref);
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
