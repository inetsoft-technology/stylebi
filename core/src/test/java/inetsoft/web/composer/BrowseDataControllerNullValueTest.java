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
package inetsoft.web.composer;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.report.XSessionManager;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.DistributedTableCacheStore;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.*;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.composer.model.BrowseDataModel;
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
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #80589. {@code BrowseDataController.executeByQuery()} silently dropped every null-valued
 * row with a bare {@code continue}, and {@code BrowseDataModel} had no field to say so -
 * {@code browse_condition_values} returned a plausible-looking but incomplete value list with no
 * signal that blanks were excluded. This drives the real {@code executeByQuery()} code path (a
 * worksheet table bound directly to a SQL data source, not a logical model) end to end against a
 * real Derby table, matching the composer plugin's own reported repro.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  BrowseDataControllerNullValueTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BrowseDataControllerNullValueTest {
   private static final String DB = "memory:bug80589browsenull";

   @Configuration
   static class JdbcConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         var ctor = CredentialService.class.getDeclaredConstructor();
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
         XNode version = new XNode();
         version.setAttribute("DBProductVersion", "10.17");
         XRepository repository = mock(XRepository.class);
         when(repository.getMetaData(any(), any(), any(), anyBoolean(), any()))
            .thenReturn(version);
         return repository;
      }

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

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table customers");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table customers (state varchar(5))");
         stmt.executeUpdate(
            "insert into customers values ('CA'), ('NY'), ('NY'), (null)");
      }
   }

   @Test
   void mixedNullAndNonNullColumnReportsNullExcludedAndOmitsTheNull() throws Exception {
      BrowseDataModel model = browse("STATE");

      assertEquals(List.of("CA", "NY"), Arrays.asList(model.values()));
      assertTrue(model.nullExcluded(), "a null row was present but not reported");
   }

   private static BrowseDataModel browse(String columnName) throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = newTable(ws, "select state from customers", dataSource());

      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      BrowseDataController controller = new BrowseDataController();
      controller.setName(table.getName());
      controller.setColumn(column(table.getColumnSelection(false), columnName));

      BrowseDataModel model = controller.process(box);
      assertNotNull(model, "browse data returned no result");
      return model;
   }

   private static ColumnRef column(ColumnSelection columns, String name) {
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef ref = (ColumnRef) columns.getAttribute(i);

         if(ref.getName().replace("\"", "").equalsIgnoreCase(name)) {
            return ref;
         }
      }

      return fail("no column " + name + " in " + columns);
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
      query.setName("bug80589");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "CustomersTable");
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
         column.setDataType(XSchema.STRING);
         columns.addAttribute(column);
      }

      table.setColumnSelection(columns, false);
      table.setColumnSelection(columns, true);
      ws.addAssembly(table);
      return table;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug80589browsenull");
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
