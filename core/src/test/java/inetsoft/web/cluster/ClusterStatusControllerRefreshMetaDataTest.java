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
package inetsoft.web.cluster;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MessageEvent;
import inetsoft.sree.security.AuthenticationService;
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
import inetsoft.util.FileSystemService;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.swap.XSwapper;
import inetsoft.web.admin.schedule.SchedulerMonitoringService;
import inetsoft.web.portal.controller.database.DataSourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82079: a node that receives a metadata refresh message for a data source must drop its
 * cached {@link DefaultMetaDataProvider} for that data source, as the refreshing node does.
 * Clearing only the provider's partition cache left its table metadata, so the receiving nodes
 * kept serving the old columns.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  ClusterStatusControllerRefreshMetaDataTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ClusterStatusControllerRefreshMetaDataTest {
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

   static Stream<Serializable> messages() {
      return Stream.of(new RefreshMetaDataMessage(DS, ORG),
                       new ClearLocalNodeMetaDataCacheMessage(DS, ORG));
   }

   // fresh service, providers and columns for each message (state must not carry over)
   @BeforeEach
   void setUp() throws Exception {
      database.put(TABLE, List.of("id"));
      engine = mock(XEngine.class);
      when(engine.bind(any())).thenAnswer(inv -> inv.getArgument(0));
      // the mocked engine acts as the database: it answers with the table's current columns
      when(engine.getMetaData(any(), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> tableMeta(inv.getArgument(2)));
      service = new DataSourceService(mock(AssetRepository.class), mock(SecurityEngine.class),
                                      engine, mock(DataSourceRegistry.class));
      cluster = mock(Cluster.class);
      // addListener() is not called, so no listener thread is started
      controller = new ClusterStatusController(
         mock(SchedulerMonitoringService.class), service, cluster, mock(SecurityEngine.class),
         mock(AuthenticationService.class), mock(FileSystemService.class), engine,
         mock(XSwapper.class));
   }

   @ParameterizedTest
   @MethodSource("messages")
   void receivedRefreshDropsTheDataSourceProvider(Serializable message) throws Exception {
      JDBCDataSource orders = jdbc(DS, "jdbc:h2:mem:orders");
      JDBCDataSource other = jdbc("other_db", "jdbc:h2:mem:other");

      DefaultMetaDataProvider p = service.getDefaultMetaDataProvider(orders, null);
      assertEquals(List.of("id"), columns(orders));
      DefaultMetaDataProvider q = service.getDefaultMetaDataProvider(other, null);
      assertEquals(List.of("id"), columns(other));

      database.put(TABLE, List.of("id", "added_col"));
      controller.messageReceived(new MessageEvent(this, "node-1", false, message));

      assertNotSame(p, service.getDefaultMetaDataProvider(orders, null));
      assertEquals(List.of("id", "added_col"), columns(orders));
      assertSame(q, service.getDefaultMetaDataProvider(other, null),
                 "another data source's provider must not be dropped");

      if(message instanceof RefreshMetaDataMessage) {
         verify(engine).removeMetaDataFiles(ORG, DS);
      }
      else {
         verify(engine).removeMetaCache(DS, ORG);
      }

      verify(cluster).sendMessage(eq("node-1"), argThat(
         m -> m instanceof ServerClusterCompleteMessage &&
            ((ServerClusterCompleteMessage) m).isSuccess()));
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

   private static JDBCDataSource jdbc(String name, String url) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName(name);
      ds.setDriver("org.h2.Driver");
      ds.setURL(url);
      ds.setRequireLogin(true);
      ds.setUser("sa");
      ds.setPassword("secret");
      return ds;
   }

   private static final String DS = "orders_db";
   private static final String ORG = "host-org";
   private static final String TABLE = "t";
   // one node instance, as the editor keeps it: the provider caches table metadata by this key
   private final XNode table = new XNode(TABLE);
   private final Map<String, List<String>> database = new ConcurrentHashMap<>();
   private XEngine engine;
   private Cluster cluster;
   private DataSourceService service;
   private ClusterStatusController controller;
}
