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
package inetsoft.uql.erm;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.IndexedStorage;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.model.database.PhysicalModelDefinition;
import inetsoft.web.portal.service.database.PhysicalGraphService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.cache.Cache;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77943: a caller's unsaved change to the extended (child) logical model or physical view
 * returned by {@link XLogicalModel#getLogicalModel(String)} or
 * {@link XPartition#getPartition(String)} must not be seen by later readers on the node.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Tag("integration")
class ExtendedModelCacheTest {
   private static int count = 0;
   private final Principal bob = () -> "bob";
   private String ds;

   @BeforeEach
   void setUp() throws Exception {
      DataSourceRegistry.getRegistry().init();
      ds = "ds77943_" + (++count);
      XDataModel dataModel = new XDataModel(ds);

      XLogicalModel base = new XLogicalModel("base");
      base.addEntity(new XEntity("E"));
      dataModel.addLogicalModel(base);
      dataModel.getLogicalModel("base").addLogicalModel(new XLogicalModel("child"), true);

      XPartition view = new XPartition("p");
      view.addTable("T1", null, null);
      dataModel.addPartition(view);

      // the child owns T2, with an auto-alias joined from the base table T1
      XPartition child = new XPartition("pc");
      child.addTable("T2", null, null);
      AutoAlias alias = new AutoAlias();
      AutoAlias.IncomingJoin join = new AutoAlias.IncomingJoin();
      join.setSourceTable("T1");
      join.setAlias("T2_T1");
      alias.addIncomingJoin(join);
      child.setAutoAlias("T2", alias);
      dataModel.getPartition("p").addPartition(child, false);

      DataSourceRegistry.getRegistry().clearCache();
   }

   @Test
   void childLogicalModelChangeIsNotSeenByLaterReaders() {
      XLogicalModel child = getChildModel();
      assertNull(child.getEntity("INJ"), "control: before the change");
      child.addEntity(new XEntity("INJ"));
      child.setConnection("other");

      XLogicalModel reread = getChildModel();
      XLogicalModel runtime = new XDataModel(ds).getLogicalModel("base", bob);
      XLogicalModel hidden = new XDataModel(ds).getLogicalModel("base", bob, true);
      assertAll(
         () -> assertNotSame(child, reread),
         () -> assertNull(reread.getEntity("INJ"), "added entity"),
         () -> assertNull(reread.getConnection(), "connection"),
         () -> assertEquals("child", runtime.getName(), "default extended model"),
         () -> assertNull(runtime.getEntity("INJ"), "runtime model added entity"),
         () -> assertNull(hidden.getEntity("INJ"), "runtime model (hide attributes) added entity"));
   }

   @Test
   void childPartitionChangeIsNotSeenByLaterReaders() {
      XPartition child = getChildPartition();
      assertTrue(child.containsTable("T2"), "control: before the change");
      child.addTable("INJ", null, null);
      child.removeTable("T2");
      child.setConnection("other");

      XPartition reread = getChildPartition();
      XPartition runtime = new XDataModel(ds).getPartition("p", bob);
      assertAll(
         () -> assertNotSame(child, reread),
         () -> assertFalse(reread.containsTable("INJ"), "added table"),
         () -> assertTrue(reread.containsTable("T2"), "removed table"),
         () -> assertNull(reread.getConnection(), "connection"),
         () -> assertEquals("pc", runtime.getName(), "default extended view"),
         () -> assertTrue(runtime.containsTable("T2"), "runtime view removed table"));
   }

   @Test
   void cancelledBaseViewTableRemovalDoesNotChangeChild() throws Exception {
      assertNotNull(getChildPartition().getAutoAlias("T2"), "control: child auto-alias before");

      // the physical view editor removes T1 from its draft of the base view, then cancels
      PhysicalModelManagerService manager = createManager();
      String id = manager.openModel(ds, null, "p", null).getId();
      manager.removeTable(id, "T1", "T1");
      manager.closeModel(id);

      XPartition stored = (XPartition) IndexedStorage.getIndexedStorage().getXMLSerializable(
         new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.EXTENDED_PARTITION,
                        ds + "/p/pc", null).toIdentifier(), null);
      assertNotNull(stored.getAutoAlias("T2"), "control: stored child auto-alias");
      assertTrue(new XDataModel(ds).getPartition("p").containsTable("T1"),
                 "control: stored base view has T1");

      XPartition child = getChildPartition();
      XPartition runtime = new XDataModel(ds).getPartition("p", bob);
      assertAll(
         () -> assertNotNull(child.getAutoAlias("T2"), "child auto-alias"),
         () -> assertNotNull(runtime.getAutoAlias("T2"), "runtime view auto-alias"));

      // and the child read after a cache clear is the same
      DataSourceRegistry.getRegistry().clearCache();
      assertNotNull(getChildPartition().getAutoAlias("T2"), "child auto-alias after clear");
   }

   private XLogicalModel getChildModel() {
      return new XDataModel(ds).getLogicalModel("base").getLogicalModel("child");
   }

   private XPartition getChildPartition() {
      return new XDataModel(ds).getPartition("p").getPartition("pc");
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   private PhysicalModelManagerService createManager() throws Exception {
      // the runtime cache keeps a copy, not the object
      Map<String, RuntimePartitionService.RuntimeXPartition> runtimes = new ConcurrentHashMap<>();
      Cache<String, RuntimePartitionService.RuntimeXPartition> cache = mock(Cache.class);
      when(cache.get(any())).thenAnswer(i -> copy(runtimes.get(i.<String>getArgument(0))));
      doAnswer(i -> {
         runtimes.put(i.getArgument(0), copy(i.getArgument(1)));
         return null;
      }).when(cache).put(any(), any());
      Cluster cluster = mock(Cluster.class);
      when(cluster.getCache(anyString(), anyBoolean(), any())).thenReturn((Cache) cache);
      RuntimePartitionService runtimePartitionService = new RuntimePartitionService(cluster);
      runtimePartitionService.init();

      PhysicalModelService modelService = mock(PhysicalModelService.class);
      when(modelService.getDataModel(anyString(), anyString())).thenAnswer(i -> new XDataModel(ds));
      when(modelService.createModel(any(), any())).thenAnswer(i -> {
         PhysicalModelDefinition definition = new PhysicalModelDefinition();
         definition.setId(((RuntimePartitionService.RuntimeXPartition) i.getArgument(1)).getId());
         return definition;
      });
      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(any(), any(), any(), any())).thenReturn(true);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(i -> i.getArgument(0));
      XRepository repository = mock(XRepository.class);
      when(repository.getDataModel(anyString())).thenAnswer(i -> new XDataModel(ds));

      return new PhysicalModelManagerService(
         dataSourceService, modelService, runtimePartitionService, new PhysicalGraphService(),
         repository, null, mock(DependencyHandler.class), null);
   }

   private static RuntimePartitionService.RuntimeXPartition copy(
      RuntimePartitionService.RuntimeXPartition runtime)
   {
      return runtime == null ? null : new RuntimePartitionService.RuntimeXPartition(
         (XPartition) runtime.getPartition().deepClone(true), runtime.getId(),
         runtime.getDataSource());
   }
}
