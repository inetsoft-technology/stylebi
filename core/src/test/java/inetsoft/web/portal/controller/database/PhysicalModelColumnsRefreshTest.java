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

import inetsoft.report.XSessionManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MessageEvent;
import inetsoft.sree.security.AuthenticationService;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.XNode;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.XUtil;
import inetsoft.util.FileSystemService;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.swap.XSwapper;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.admin.schedule.SchedulerMonitoringService;
import inetsoft.web.cluster.ClusterStatusController;
import inetsoft.web.cluster.RefreshMetaDataMessage;
import inetsoft.web.portal.data.DatabaseDatasourcesController;
import inetsoft.web.portal.model.database.GraphColumnInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bugs #82078 and #82079, read through the column path of
 * {@code POST /api/data/physicalmodel/columns}: {@link PhysicalModelService#getMetaDataProvider0}
 * (a registry copy of the data source, marked from portal) and
 * {@link PhysicalModelService#getTableColumns} with a new table node for every request.
 * After a metadata refresh, on the refreshing node (#82078) or on a node that receives the
 * cluster message (#82079), one read must show the column added to the table.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  PhysicalModelColumnsRefreshTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PhysicalModelColumnsRefreshTest {
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
      // the registry hands out a copy of each data source
      when(engine.getDataSource(anyString())).thenAnswer(inv -> copy(inv.getArgument(0)));
      when(engine.getDataSource(anyString(), eq(true)))
         .thenAnswer(inv -> copy(inv.getArgument(0)));
      service = new DataSourceService(mock(AssetRepository.class), mock(SecurityEngine.class),
                                      engine, mock(DataSourceRegistry.class));
      modelService = new PhysicalModelService(mock(RuntimePartitionService.class), engine,
                                              mock(AssetRepository.class), service,
                                              mock(XSessionManager.class));
      partition = new XPartition("p");
      partition.addTable(TABLE, null, null);
   }

   @Test
   void bug82078RefreshOnThisNodeShowsAddedColumnInOneRead() throws Exception {
      sources.put("run1", jdbc("run1"));
      sources.put("run2", jdbc("run2"));
      assertEquals(sources.get("run1"), sources.get("run2"),
                   "precondition: same connection settings, different names");

      // run1's columns were read, then run1 was deleted: its provider stays in the list
      assertEquals(List.of("ID"), readColumns("run1"));
      assertEquals(List.of("ID"), readColumns("run2"));

      database.put(TABLE, List.of("ID", "ADDED_COL"));
      DatabaseDatasourcesController controller = new DatabaseDatasourcesController(
         mock(DatabaseDatasourcesService.class), mock(DatabaseModelBrowserService.class),
         mock(DataModelFolderManagerService.class), service, engine);
      assertTrue(controller.refreshMetadata("run2"));

      assertEquals(List.of("ID", "ADDED_COL"), readColumns("run2"));
   }

   @Test
   void bug82079RefreshMessageOnReceivingNodeShowsAddedColumnInOneRead() throws Exception {
      sources.put("orders_db", jdbc("orders_db"));
      assertEquals(List.of("ID"), readColumns("orders_db"));

      database.put(TABLE, List.of("ID", "ADDED_COL"));
      ClusterStatusController controller = new ClusterStatusController(
         mock(SchedulerMonitoringService.class), service, mock(Cluster.class),
         mock(SecurityEngine.class), mock(AuthenticationService.class),
         mock(FileSystemService.class), engine, mock(XSwapper.class));
      controller.messageReceived(new MessageEvent(
         this, "node-1", false, new RefreshMetaDataMessage("orders_db", "host-org")));

      assertEquals(List.of("ID", "ADDED_COL"), readColumns("orders_db"));
   }

   // what loadTableColumns does for a physical table without catalog or schema, except that
   // the table node comes from the provider's table lookup there: a new node of the same name
   private List<String> readColumns(String database) throws Exception {
      DefaultMetaDataProvider provider = modelService.getMetaDataProvider0(
         database, null, XUtil.OUTER_MOSE_LAYER_DATABASE);
      XNode tableNode = new XNode(TABLE);
      List<GraphColumnInfo> cols = modelService.getTableColumns(
         tableNode, provider, partition.getPartitionTable(TABLE));
      return cols.stream().map(GraphColumnInfo::getName).collect(Collectors.toList());
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

   private JDBCDataSource copy(String name) {
      JDBCDataSource ds = sources.get(name);
      return ds == null ? null : (JDBCDataSource) ds.clone();
   }

   private static JDBCDataSource jdbc(String name) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName(name);
      ds.setDriver("org.h2.Driver");
      ds.setURL("jdbc:h2:mem:physicalmodelcolumns");
      ds.setRequireLogin(true);
      ds.setUser("sa");
      ds.setPassword("secret");
      return ds;
   }

   private static final String TABLE = "T";
   private final Map<String, List<String>> database = new ConcurrentHashMap<>();
   private final Map<String, JDBCDataSource> sources = new ConcurrentHashMap<>();
   private XEngine engine;
   private DataSourceService service;
   private PhysicalModelService modelService;
   private XPartition partition;
}
