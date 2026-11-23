/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.portal.controller.database;

import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XNode;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.rgraph.TableNode;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.portal.data.DatabaseDatasourcesController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82078: refreshing a data source's metadata must drop that data source's cached
 * {@link DefaultMetaDataProvider} (found by name), not the first provider whose data source has
 * equal connection settings. A leftover provider of a deleted data source on the same connection
 * used to be evicted instead, so the first refresh still served the old columns.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DataSourceServiceMetaDataProviderTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceServiceMetaDataProviderTest {
   @Configuration
   static class TestConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         // package-private constructor, normally reached by component scanning
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      // XAgent.getAgent() reads Config; without it getTableMetaData() logs and returns null
      @Bean
      public Config config() {
         return mock(Config.class);
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      database.put(TABLE, List.of("ID"));
      engine = mock(XEngine.class);
      when(engine.bind(any())).thenAnswer(inv -> inv.getArgument(0));
      // the mocked engine acts as the database: it answers with the table's current columns
      when(engine.getMetaData(any(), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> tableMeta(inv.getArgument(2)));
      service = new DataSourceService(mock(AssetRepository.class), mock(SecurityEngine.class),
                                      engine, mock(DataSourceRegistry.class));
   }

   @Test
   void oneRefreshShowsAddedColumnDespiteLeftoverOfDeletedSameConnectionDataSource()
      throws Exception
   {
      JDBCDataSource run1 = jdbc("run1");
      JDBCDataSource run2 = jdbc("run2");
      assertEquals(run1, run2, "precondition: same connection settings, different names");

      // run1 was read, then deleted: its provider stays in the list
      DefaultMetaDataProvider p1 = service.getDefaultMetaDataProvider(run1, null);
      assertEquals(List.of("ID"), columns(run1));
      DefaultMetaDataProvider p2 = service.getDefaultMetaDataProvider(run2, null);
      assertNotSame(p1, p2);
      assertEquals(List.of("ID"), columns(run2));

      database.put(TABLE, List.of("ID", "ADDED_COL"));
      // the registry hands out a copy
      when(engine.getDataSource("run2")).thenAnswer(inv -> run2.clone());
      DatabaseDatasourcesController controller = new DatabaseDatasourcesController(
         mock(DatabaseDatasourcesService.class), mock(DatabaseModelBrowserService.class),
         mock(DataModelFolderManagerService.class), service, engine);

      assertTrue(controller.refreshMetadata("run2"));
      verify(engine).refreshMetaData("run2");

      assertEquals(List.of("ID", "ADDED_COL"), columns(run2));
      assertNotSame(p2, service.getDefaultMetaDataProvider(run2, null));
      assertSame(p1, service.getDefaultMetaDataProvider(run1, null),
                 "another data source's provider must not be evicted");
   }

   @Test
   void removeDropsEveryProviderOfTheName() throws Exception {
      JDBCDataSource before = jdbc("run2");
      DefaultMetaDataProvider pBefore = service.getDefaultMetaDataProvider(before, null);
      assertEquals(List.of("ID"), columns(before));

      // settings edited between reads: the lookup misses and appends a second "run2" provider
      JDBCDataSource after = (JDBCDataSource) before.clone();
      after.setDescription("edited");
      assertNotEquals(before, after, "precondition: description is part of equals");
      DefaultMetaDataProvider pAfter = service.getDefaultMetaDataProvider(after, null);
      assertNotSame(pBefore, pAfter);

      service.removeDefaultMetaDataProviderCache((XDataSource) after.clone());

      assertNotSame(pBefore, service.getDefaultMetaDataProvider(before, null));
      assertNotSame(pAfter, service.getDefaultMetaDataProvider(after, null));
   }

   @Test
   void concurrentFirstReadsShareOneProvider() throws Exception {
      JDBCDataSource run2 = jdbc("run2");
      int threads = 8;
      CountDownLatch ready = new CountDownLatch(threads);
      CountDownLatch go = new CountDownLatch(1);
      ExecutorService executor = Executors.newFixedThreadPool(threads);

      try {
         List<Future<DefaultMetaDataProvider>> futures = new ArrayList<>();

         for(int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
               ready.countDown();
               go.await();
               return service.getDefaultMetaDataProvider(run2, null);
            }));
         }

         assertTrue(ready.await(10, TimeUnit.SECONDS));
         go.countDown();
         DefaultMetaDataProvider first = futures.get(0).get(10, TimeUnit.SECONDS);

         for(Future<DefaultMetaDataProvider> future : futures) {
            assertSame(first, future.get(10, TimeUnit.SECONDS));
         }
      }
      finally {
         executor.shutdownNow();
         assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
      }
   }

   private List<String> columns(XDataSource ds) throws Exception {
      TableNode node = service.getDefaultMetaDataProvider(ds, null).getTableMetaData(table);
      assertNotNull(node, "table metadata of " + ds.getFullName());
      List<String> result = new ArrayList<>();

      for(int i = 0; i < node.getColumnCount(); i++) {
         result.add(node.getColumn(i));
      }

      return result;
   }

   private XNode tableMeta(XNode mtype) {
      XNode root = new XNode("root");
      XNode tnode = new XNode(mtype.getName());

      for(String col : database.getOrDefault(mtype.getName(), List.of())) {
         tnode.addChild(new XNode(col));
      }

      root.addChild(tnode);
      return root;
   }

   private static JDBCDataSource jdbc(String name) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName(name);
      ds.setDriver("org.h2.Driver");
      ds.setURL("jdbc:h2:mem:bug82078");
      ds.setRequireLogin(true);
      ds.setUser("sa");
      ds.setPassword("secret");
      return ds;
   }

   private static final String TABLE = "T";
   // one node instance, as the editor keeps it: the provider caches table metadata by this key
   private final XNode table = new XNode(TABLE);
   private final Map<String, List<String>> database = new ConcurrentHashMap<>();
   private XEngine engine;
   private DataSourceService service;
}
