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
import inetsoft.report.XSessionManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.path.XSelection;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79445. Derby rejects more than one DISTINCT aggregate in a select without a group by
 * ("Multiple DISTINCT aggregates are not supported at this time"), but the push-down check
 * asked about each aggregate alone, so a table with no groups and two Count Distinct was
 * merged into a query Derby refuses. The set is now aggregated in memory. A grouped table,
 * a single Count Distinct and a database other than Derby are still pushed down.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  PreAssetQueryMultiDistinctTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PreAssetQueryMultiDistinctTest {
   private static final String DB = "memory:bug79445multidistinct";

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
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         when(factory.getConnectionPool(any(), any())).thenReturn(derby());
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
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      @Bean
      public XRepository xRepository() throws Exception {
         // the product version SQLHelper reads for Derby; from 10.3 it groups by the SQL columns
         XNode version = new XNode();
         version.setAttribute("DBProductVersion", "10.17");
         XRepository repository = mock(XRepository.class);
         when(repository.getMetaData(any(), any(), any(), anyBoolean(), any()))
            .thenReturn(version);
         return repository;
      }

      // the data service stands in for XEngine.execute and runs the query on Derby
      @Bean
      public XSessionManager xSessionManager(XSessionService sessionService) throws Exception {
         XRepository dataService = mock(XRepository.class);
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

   // a has 5 distinct values over 8 rows, b has 3, c has 2 and a null
   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table t");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table t (g varchar(5), a int, b int, c int)");
         stmt.executeUpdate(
            "insert into t values ('x', 1, 1, 1), ('x', 2, 1, 1), ('x', 3, 2, 2), " +
            "('x', 3, 2, null), ('y', 4, 3, 2), ('y', 5, 3, 2), ('y', 5, 3, 2), ('y', 1, 1, 1)");
      }
   }

   @Test
   void ungroupedTwoCountDistinctOnDerbyIsAggregatedInMemory() throws Exception {
      Fixture f = fixture(dataSource(), null, "a", "b");
      assertFalse(f.query.isAggregateInfoMergeable());
      // the true counts, not Derby's refusal
      assertEquals(List.of(List.of(5, 3)), rows(f));
   }

   @Test
   void ungroupedThreeCountDistinctIncludingNullColumn() throws Exception {
      Fixture f = fixture(dataSource(), null, "a", "b", "c");
      assertFalse(f.query.isAggregateInfoMergeable());
      // a null is not counted, as in count(distinct c)
      assertEquals(List.of(List.of(5, 3, 2)), rows(f));
   }

   @Test
   void groupedTwoCountDistinctIsStillPushedDown() throws Exception {
      Fixture f = fixture(dataSource(), "g", "a", "b");
      assertTrue(f.query.isAggregateInfoMergeable());
      List<List<Object>> rows = rows(f);
      rows.sort(Comparator.comparing(Object::toString));
      assertEquals(List.of(List.of("x", 3, 2), List.of("y", 3, 2)), rows);
   }

   @Test
   void ungroupedSingleCountDistinctIsStillPushedDown() throws Exception {
      Fixture f = fixture(dataSource(), null, "a");
      assertTrue(f.query.isAggregateInfoMergeable());
      assertEquals(List.of(List.of(5)), rows(f));
   }

   @Test
   void ungroupedTwoCountDistinctOnOtherDatabaseIsStillPushedDown() throws Exception {
      Fixture f = fixture(postgresql(), null, "a", "b");
      assertTrue(f.query.isAggregateInfoMergeable());
   }

   private record Fixture(AssetQuerySandbox box, SQLBoundTableAssembly table,
                          SQLBoundQuery query, VariableTable vars) {
   }

   private static Fixture fixture(JDBCDataSource ds, String group, String... distinct)
      throws Exception
   {
      Worksheet ws = new Worksheet();
      String select = (group == null ? "" : group + ", ") + String.join(", ", distinct);
      SQLBoundTableAssembly table = newTable(ws, "select " + select + " from t", ds);
      AggregateInfo info = new AggregateInfo();
      ColumnSelection columns = table.getColumnSelection(false);

      if(group != null) {
         info.addGroup(new GroupRef(column(columns, group)));
      }

      for(String name : distinct) {
         info.addAggregate(new AggregateRef(column(columns, name),
                                            AggregateFormula.COUNT_DISTINCT));
      }

      table.setAggregateInfo(info);
      table.setAggregate(true);
      VariableTable vars = new VariableTable();
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      SQLBoundQuery query = new SQLBoundQuery(AssetQuerySandbox.RUNTIME_MODE, box, table,
                                              false, false);
      query.merge(vars);
      return new Fixture(box, table, query, vars);
   }

   private static DataRef column(ColumnSelection columns, String name) {
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         if(columns.getAttribute(i).getName().replace("\"", "").equalsIgnoreCase(name)) {
            return columns.getAttribute(i);
         }
      }

      return fail("no column " + name + " in " + columns);
   }

   // the table run as the worksheet runs it, the aggregate in SQL or in memory as decided
   private static List<List<Object>> rows(Fixture f) throws Exception {
      TableLens lens = f.box.getTableLens(f.table.getName(), AssetQuerySandbox.RUNTIME_MODE);
      assertNotNull(lens, "the table returned no lens");
      lens.moreRows(Integer.MAX_VALUE);
      List<List<Object>> rows = new ArrayList<>();

      for(int r = lens.getHeaderRowCount(); r < lens.getRowCount(); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = 0; c < lens.getColCount(); c++) {
            Object val = lens.getObject(r, c);
            row.add(val instanceof Number ? (Object) ((Number) val).intValue() : val);
         }

         rows.add(row);
      }

      return rows;
   }

   private static SQLBoundTableAssembly newTable(Worksheet ws, String sqlText, JDBCDataSource ds)
      throws Exception
   {
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds);
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class,
                                                        long.class);
      parse.setAccessible(true);
      parse.invoke(usql, sqlText, 0, 4000L);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sqlText);
      usql.setSQLString(sqlText, false);

      JDBCQuery query = new JDBCQuery();
      query.setName("bug79445");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);

      ColumnSelection columns = new ColumnSelection();
      XSelection selection = usql.getSelection();

      for(int i = 0; i < selection.getColumnCount(); i++) {
         String path = selection.getColumn(i);
         String name = path.substring(path.lastIndexOf('.') + 1);
         ColumnRef column = new ColumnRef(new AttributeRef(null, name));
         column.setDataType("g".equals(name) ? XSchema.STRING : XSchema.INTEGER);
         columns.addAttribute(column);
      }

      table.setColumnSelection(columns, false);
      table.setColumnSelection(columns, true);
      ws.addAssembly(table);
      return table;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug79445multidistinct");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   private static JDBCDataSource postgresql() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug79445postgresql");
      ds.setDriver("org.postgresql.Driver");
      ds.setURL("jdbc:postgresql://localhost/db");
      ds.setRuntimeProductName("postgresql");
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
