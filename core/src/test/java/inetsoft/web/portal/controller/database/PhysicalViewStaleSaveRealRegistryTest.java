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
package inetsoft.web.portal.controller.database;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.MessageException;
import inetsoft.web.portal.model.database.PhysicalModelDefinition;
import inetsoft.web.portal.service.database.PhysicalGraphService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.cache.Cache;
import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82212: the stale-save check against a real {@link XDataModel} and registry, so the
 * stored last-modified time is the one a real save stamps, for a view, a newly created view
 * and an extended view.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Tag("integration")
class PhysicalViewStaleSaveRealRegistryTest {
   private static int count = 0;
   private final Map<String, RuntimePartitionService.RuntimeXPartition> runtimes =
      new ConcurrentHashMap<>();
   private String ds;
   private PhysicalModelManagerService manager;

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @BeforeEach
   void setUp() throws Exception {
      DataSourceRegistry.getRegistry().init();
      ds = "ds82212_" + (++count);
      XDataModel dataModel = new XDataModel(ds);
      XPartition view = new XPartition("v");
      view.addTable("T1", null, null);
      dataModel.addPartition(view);
      XPartition child = new XPartition("pc");
      child.addTable("T2", null, null);
      dataModel.getPartition("v").addPartition(child, false);
      DataSourceRegistry.getRegistry().clearCache();

      Cache<String, RuntimePartitionService.RuntimeXPartition> cache = mock(Cache.class);
      when(cache.get(any())).thenAnswer(i -> copy(runtimes.get(i.<String>getArgument(0))));
      doAnswer(i -> {
         runtimes.put(i.getArgument(0), copy(i.getArgument(1)));
         return null;
      }).when(cache).put(any(), any());
      Cluster cluster = mock(Cluster.class);
      when(cluster.getCache(anyString(), anyBoolean(), any())).thenReturn((Cache) cache);
      when(cluster.runInTransaction(anyLong(), any(), any())).thenAnswer(
         i -> i.<inetsoft.sree.internal.cluster.TransactionalAction<?, ?>>getArgument(2).run());
      RuntimePartitionService runtimePartitionService = new RuntimePartitionService(cluster);
      runtimePartitionService.init();

      PhysicalModelService modelService = mock(PhysicalModelService.class);
      when(modelService.getDataModel(anyString(), anyString()))
         .thenAnswer(i -> new XDataModel(ds));
      when(modelService.createModel(any(), any())).thenAnswer(i -> {
         RuntimePartitionService.RuntimeXPartition runtime = i.getArgument(1);
         return definition(runtime.getId(), runtime.getPartition().getName());
      });
      when(modelService.createPartition(any())).thenAnswer(i -> {
         XPartition created = new XPartition("n");
         created.addTable("T3", new Rectangle(10, 10, 80, 60));
         return created;
      });
      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(any(), any(), any(), any())).thenReturn(true);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(i -> i.getArgument(0));
      XRepository repository = mock(XRepository.class);
      when(repository.getDataModel(ds)).thenAnswer(i -> new XDataModel(ds));

      manager = new PhysicalModelManagerService(
         dataSourceService, modelService, runtimePartitionService, new PhysicalGraphService(),
         repository, null, mock(DependencyHandler.class), null);
   }

   @Test
   void saveStampsTheStoredViewAndTheSameEditorSavesAgain() throws Exception {
      String id = manager.openModel(ds, null, "v", null).getId();
      long before = stored("v", null).getLastModified();
      tick();

      save(id, null, "v");
      long after = stored("v", null).getLastModified();
      assertTrue(after > before, "a real save stamps the stored view");

      tick();
      save(id, null, "v");
      assertTrue(stored("v", null).getLastModified() > after);
   }

   @Test
   void staleEditorIsRefusedAfterAnExternalWrite() throws Exception {
      String stale = manager.openModel(ds, null, "v", null).getId();
      tick();
      XDataModel other = new XDataModel(ds);
      XPartition changed = other.getPartition("v");
      changed.addTable("ALIAS1", null, null);
      other.addPartition(changed);

      assertThrows(MessageException.class, () -> save(stale, null, "v"));
      assertTrue(stored("v", null).containsTable("ALIAS1"), "external change survives");

      String fresh = manager.openModel(ds, null, "v", null).getId();
      tick();
      save(fresh, null, "v");
      assertTrue(stored("v", null).containsTable("ALIAS1"));
   }

   @Test
   void newViewCanBeSavedAgainFromTheSameEditor() throws Exception {
      PhysicalModelDefinition model = manager.createModel(ds, null, definition(null, "n"), () -> "bob");
      String id = model.getId();
      manager.createAndSaveModel(ds, null, null, definition(id, "n"), () -> "bob");
      assertNotNull(stored("n", null));
      tick();

      save(id, null, "n");
      tick();
      save(id, null, "n");

      // and a write by someone else is still detected
      XDataModel other = new XDataModel(ds);
      XPartition changed = other.getPartition("n");
      changed.addTable("ALIAS1", null, null);
      tick();
      other.addPartition(changed);
      assertThrows(MessageException.class, () -> save(id, null, "n"));
   }

   @Test
   void extendedViewIsCheckedAgainstItsOwnStoredTime() throws Exception {
      String stale = manager.openModel(ds, "v", "pc", null).getId();
      String same = manager.openModel(ds, "v", "pc", null).getId();
      tick();

      save(same, "v", "pc");
      long after = stored("pc", "v").getLastModified();
      assertThrows(MessageException.class, () -> save(stale, "v", "pc"));
      assertEquals(after, stored("pc", "v").getLastModified(), "refused save writes nothing");

      tick();
      save(same, "v", "pc");
      assertTrue(stored("pc", "v").getLastModified() > after);
   }

   private void save(String id, String parent, String name) throws Exception {
      manager.updateAndSaveModel(ds, null, parent, name, definition(id, name), null);
   }

   private XPartition stored(String name, String parent) {
      DataSourceRegistry.getRegistry().clearCache();
      XDataModel model = new XDataModel(ds);
      XPartition partition = model.getPartition(parent == null ? name : parent);
      return parent == null || partition == null ? partition : partition.getPartition(name);
   }

   // lastModified has millisecond resolution
   private static void tick() throws InterruptedException {
      Thread.sleep(5);
   }

   private static PhysicalModelDefinition definition(String id, String name) {
      PhysicalModelDefinition definition = new PhysicalModelDefinition();
      definition.setId(id);
      definition.setName(name);
      return definition;
   }

   private static RuntimePartitionService.RuntimeXPartition copy(
      RuntimePartitionService.RuntimeXPartition runtime)
   {
      if(runtime == null) {
         return null;
      }

      RuntimePartitionService.RuntimeXPartition copy =
         new RuntimePartitionService.RuntimeXPartition(
            (XPartition) runtime.getPartition().deepClone(true), runtime.getId(),
            runtime.getDataSource());
      copy.setStoredModified(runtime.getStoredModified());
      return copy;
   }
}
