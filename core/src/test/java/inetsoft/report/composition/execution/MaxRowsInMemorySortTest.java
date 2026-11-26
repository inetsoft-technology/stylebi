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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82389: with Max Rows N on a worksheet table whose sort can't be pushed into the SQL
 * (a sort on a script column), the source fetch was cut to N rows in storage order before the
 * in-memory sort, so the table showed the first N stored rows, sorted, instead of the top N of
 * the sorted data. The real chain runs on embedded Derby: AssetQuerySandbox ->
 * PhysicalBoundQuery / MirrorQuery -> XSessionManager -> JDBCHandler.
 * <p>
 * PRODUCTS holds ids 1..8 in storage order with categories 3,3,6,1,1,5,1,2.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  MaxRowsInMemorySortTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MaxRowsInMemorySortTest {
   private static final String DB = "memory:bug82389";
   private static final String DS_NAME = "bug82389";
   /** The SQL text of each query sent to the database. */
   private static final List<String> SENT = Collections.synchronizedList(new ArrayList<>());
   /** The HINT_MAX_ROWS variable of each query sent to the database. */
   private static final List<Object> HINTS = Collections.synchronizedList(new ArrayList<>());

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

      // PhysicalBoundQuery looks the data source up by the source prefix
      @Bean
      public XRepository xRepository() throws Exception {
         XRepository repository = mock(XRepository.class);
         JDBCDataSource ds = dataSource();
         when(repository.getDataSource(anyString())).thenReturn(ds);
         when(repository.getDataSource(anyString(), anyBoolean())).thenReturn(ds);
         when(repository.getDataSource(anyString(), anyString())).thenReturn(ds);
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

               HINTS.add(vars == null ? null : vars.get(XQuery.HINT_MAX_ROWS));
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
            stmt.executeUpdate("drop table products");
         }
         catch(SQLException ignore) {
            // first run in this JVM
         }

         stmt.executeUpdate("create table products (product_id int, category_id int)");
         int[] cats = { 3, 3, 6, 1, 1, 5, 1, 2 };

         for(int i = 0; i < cats.length; i++) {
            stmt.executeUpdate("insert into products values (" + (i + 1) + ", " + cats[i] + ")");
         }
      }
   }

   @BeforeEach
   void setUp() {
      AssetDataCache.getCache().clearCache();
      SENT.clear();
      HINTS.clear();
   }

   @AfterEach
   void tearDown() {
      AssetDataCache.getCache().clearCache();
   }

   @ParameterizedTest(name = "mode {0}")
   @ValueSource(ints = { AssetQuerySandbox.RUNTIME_MODE, AssetQuerySandbox.LIVE_MODE })
   void scriptSortAscendingReturnsTopRowsOfSortedData(int mode) throws Exception {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = products(ws, "T", true, true);
      sort(table, "CAT_JS", XConstants.SORT_ASC);
      table.setMaxRows(3);

      Result result = run(ws, "T", mode);
      assertEquals(List.of(1, 1, 1), result.column("CAT_JS"), result.toString());
      assertEquals(List.of(4, 5, 7), sorted(result.column("PRODUCT_ID")), result.toString());
   }

   @ParameterizedTest(name = "mode {0}")
   @ValueSource(ints = { AssetQuerySandbox.RUNTIME_MODE, AssetQuerySandbox.LIVE_MODE })
   void scriptSortDescendingReturnsTopRowsOfSortedData(int mode) throws Exception {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = products(ws, "T", true, true);
      sort(table, "CAT_JS", XConstants.SORT_DESC);
      table.setMaxRows(3);

      Result result = run(ws, "T", mode);
      assertEquals(List.of(6, 5, 3), result.column("CAT_JS"), result.toString());
   }

   @ParameterizedTest(name = "mode {0}")
   @ValueSource(ints = { AssetQuerySandbox.RUNTIME_MODE, AssetQuerySandbox.LIVE_MODE })
   void mixedScriptAndColumnSortReturnsTopRowsOfSortedData(int mode) throws Exception {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = products(ws, "T", true, true);
      sort(table, "CAT_JS", XConstants.SORT_DESC);
      sort(table, "PRODUCT_ID", XConstants.SORT_ASC);
      table.setMaxRows(3);

      Result result = run(ws, "T", mode);
      assertEquals(List.of(3, 6, 1), result.column("PRODUCT_ID"), result.toString());
   }

   @ParameterizedTest(name = "mode {0}")
   @ValueSource(ints = { AssetQuerySandbox.RUNTIME_MODE, AssetQuerySandbox.LIVE_MODE })
   void distinctWithScriptSortReturnsTopRowsOfSortedData(int mode) throws Exception {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = products(ws, "T", false, true);
      table.setDistinct(true);
      sort(table, "CAT_JS", XConstants.SORT_DESC);
      table.setMaxRows(3);

      Result result = run(ws, "T", mode);
      assertEquals(List.of(6, 5, 3), result.column("CAT_JS"), result.toString());
   }

   /** The script column, its sort and the max rows are on a mirror of the physical table. */
   @ParameterizedTest(name = "mode {0}")
   @ValueSource(ints = { AssetQuerySandbox.RUNTIME_MODE, AssetQuerySandbox.LIVE_MODE })
   void scriptSortOnMirrorReturnsTopRowsOfSortedData(int mode) throws Exception {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly base = products(ws, "P", true, false);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", base);
      ws.addAssembly(mirror);
      mirror.updateColumnSelection();
      addScriptColumn(mirror);
      sort(mirror, "CAT_JS", XConstants.SORT_ASC);
      mirror.setMaxRows(3);

      Result result = run(ws, "M", mode);
      assertEquals(List.of(1, 1, 1), result.column("CAT_JS"), result.toString());
   }

   /** A mirror reads a sub-table whose script sort and max rows are its own. */
   @ParameterizedTest(name = "mode {0}")
   @ValueSource(ints = { AssetQuerySandbox.RUNTIME_MODE, AssetQuerySandbox.LIVE_MODE })
   void mirrorOverScriptSortedSubTableReturnsTopRowsOfSortedData(int mode) throws Exception {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = products(ws, "T", true, true);
      sort(table, "CAT_JS", XConstants.SORT_ASC);
      table.setMaxRows(3);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", table);
      ws.addAssembly(mirror);
      mirror.updateColumnSelection();

      Result result = run(ws, "M", mode);
      assertEquals(List.of(1, 1, 1), result.column("CAT_JS"), result.toString());
   }

   /** Control: a sort pushed into ORDER BY keeps the source row limit. */
   @ParameterizedTest(name = "mode {0}")
   @ValueSource(ints = { AssetQuerySandbox.RUNTIME_MODE, AssetQuerySandbox.LIVE_MODE })
   void pushedSortKeepsSourceRowLimit(int mode) throws Exception {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = products(ws, "T", true, true);
      sort(table, "CATEGORY_ID", XConstants.SORT_DESC);
      table.setMaxRows(3);

      Result result = run(ws, "T", mode);
      assertEquals(List.of(6, 5, 3), result.column("CATEGORY_ID"), result.toString());
      assertTrue(result.sql.toLowerCase().contains("order by"), result.toString());
      assertEquals("3", result.hint, "the pushed sort keeps the source limit: " + result);
   }

   /** The script sort leaves the source fetch unlimited; the cut to N happens after it. */
   @Test
   void scriptSortDoesNotLimitSourceFetch() throws Exception {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = products(ws, "T", true, true);
      sort(table, "CAT_JS", XConstants.SORT_ASC);
      table.setMaxRows(3);

      Result result = run(ws, "T", AssetQuerySandbox.RUNTIME_MODE);
      assertNull(result.hint, "no source limit for an in-memory sort: " + result);
      assertEquals(3, result.rows.size(), result.toString());
   }

   /** PRODUCT_ID, CATEGORY_ID and, if script, CAT_JS = field['CATEGORY_ID']. */
   private static PhysicalBoundTableAssembly products(Worksheet ws, String name, boolean withId,
                                                      boolean script)
   {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, name);
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, DS_NAME, "PRODUCTS"));
      ColumnSelection columns = new ColumnSelection();

      for(String column : withId ? new String[] { "PRODUCT_ID", "CATEGORY_ID" }
                                 : new String[] { "CATEGORY_ID" })
      {
         AttributeRef aref = new AttributeRef(null, column);
         aref.setSqlType(Types.INTEGER);
         ColumnRef ref = new ColumnRef(aref);
         ref.setDataType(XSchema.INTEGER);
         columns.addAttribute(ref);
      }

      table.setColumnSelection(columns, false);
      table.setColumnSelection((ColumnSelection) columns.clone(), true);
      ws.addAssembly(table);

      if(script) {
         addScriptColumn(table);
      }

      return table;
   }

   private static void addScriptColumn(TableAssembly table) {
      ExpressionRef exp = new ExpressionRef(null, "CAT_JS");
      exp.setExpression("field['CATEGORY_ID']");
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(XSchema.INTEGER);
      column.setSQL(false);

      for(boolean pub : new boolean[] { false, true }) {
         ColumnSelection columns = table.getColumnSelection(pub);
         columns.addAttribute((ColumnRef) column.clone());
         table.setColumnSelection(columns, pub);
      }
   }

   private static void sort(TableAssembly table, String column, int order) {
      ColumnRef ref = (ColumnRef) table.getColumnSelection(false).getAttribute(column);
      assertNotNull(ref, "column " + column);
      SortRef sort = new SortRef(ref);
      sort.setOrder(order);
      SortInfo info = table.getSortInfo();
      info.addSort(sort);
      table.setSortInfo(info);
   }

   private static Result run(Worksheet ws, String name, int mode) throws Exception {
      AssetDataCache.getCache().clearCache();
      SENT.clear();
      HINTS.clear();
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      try {
         TableLens lens = box.getTableLens(name, mode);
         assertNotNull(lens);
         lens.moreRows(Integer.MAX_VALUE);
         assertTrue(lens.getColCount() >= 1, "query failed, placeholder lens returned");

         List<String> headers = new ArrayList<>();

         for(int c = 0; c < lens.getColCount(); c++) {
            headers.add(String.valueOf(lens.getObject(0, c)));
         }

         List<List<Object>> rows = new ArrayList<>();

         for(int r = lens.getHeaderRowCount(); lens.moreRows(r); r++) {
            List<Object> row = new ArrayList<>();

            for(int c = 0; c < lens.getColCount(); c++) {
               row.add(lens.getObject(r, c));
            }

            rows.add(row);
         }

         String sql = SENT.isEmpty() ? "-" : SENT.get(SENT.size() - 1);
         Object hint = HINTS.isEmpty() ? null : HINTS.get(HINTS.size() - 1);
         return new Result(headers, rows, sql, hint == null ? null : hint.toString());
      }
      finally {
         box.dispose();
      }
   }

   private static List<Integer> sorted(List<Integer> values) {
      List<Integer> list = new ArrayList<>(values);
      Collections.sort(list);
      return list;
   }

   private record Result(List<String> headers, List<List<Object>> rows, String sql, String hint) {
      List<Integer> column(String name) {
         int col = -1;

         for(int i = 0; i < headers.size(); i++) {
            if(headers.get(i).equalsIgnoreCase(name) || headers.get(i).endsWith("." + name)) {
               col = i;
            }
         }

         assertTrue(col >= 0, "column " + name + " not in " + this);
         List<Integer> values = new ArrayList<>();

         for(List<Object> row : rows) {
            values.add(row.get(col) == null ? null : ((Number) row.get(col)).intValue());
         }

         return values;
      }

      @Override
      public String toString() {
         return "headers " + headers + " rows " + rows + " sql " + sql + " hint " + hint;
      }
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName(DS_NAME);
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
