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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82212: a Portal save from an editor opened before another writer (for example the wiz
 * plugin adding an alias) saved the view is refused instead of silently overwriting the change.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PhysicalViewStaleSaveTest {
   private final Map<String, RuntimePartitionService.RuntimeXPartition> runtimes =
      new ConcurrentHashMap<>();
   private final AtomicLong clock = new AtomicLong(1000);
   private final XDataModel dataModel = mock(XDataModel.class);
   private RuntimePartitionService runtimePartitionService;
   private PhysicalModelManagerService manager;
   private XPartition stored;

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @BeforeEach
   void setUp() throws Exception {
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
      runtimePartitionService = spy(new RuntimePartitionService(cluster));
      runtimePartitionService.init();

      stored = new XPartition("v");
      stored.addTable("ORDERS", new Rectangle(10, 10, 80, 60));
      stored.setLastModified(clock.get());

      when(dataModel.getDataSource()).thenReturn("ds");
      when(dataModel.getLogicalModelNames()).thenReturn(new String[0]);
      when(dataModel.getPartition(anyString())).thenAnswer(i -> stored.clone());
      // like XDataModel.addPartition, a save stamps the stored view
      doAnswer(i -> {
         stored = i.getArgument(0);
         stored.setLastModified(clock.incrementAndGet());
         return null;
      }).when(dataModel).addPartition(any());

      PhysicalModelService modelService = mock(PhysicalModelService.class);
      when(modelService.getDataModel(anyString(), anyString())).thenReturn(dataModel);
      when(modelService.createModel(any(), any())).thenAnswer(i -> definition(
         ((RuntimePartitionService.RuntimeXPartition) i.getArgument(1)).getId()));
      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(any(), any(), any(), any())).thenReturn(true);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(i -> i.getArgument(0));
      XRepository repository = mock(XRepository.class);
      when(repository.getDataModel("ds")).thenReturn(dataModel);

      manager = new PhysicalModelManagerService(
         dataSourceService, modelService, runtimePartitionService, new PhysicalGraphService(),
         repository, null, mock(DependencyHandler.class), null);
   }

   @Test
   void staleEditorSaveIsRefusedAndTheExternalChangeSurvives() throws Exception {
      String id = open();
      externalWrite("ALIAS1");

      MessageException e = assertThrows(MessageException.class, () -> save(id));
      assertTrue(e.getMessage().contains("v"), e.getMessage());
      assertNotNull(stored.getPartitionTable("ALIAS1"), "the external change must survive");
   }

   @Test
   void sameEditorSavesRepeatedly() throws Exception {
      String id = open();
      save(id);
      save(id);
      assertNotNull(stored.getPartitionTable("ORDERS"));
   }

   @Test
   void freshOpenAfterExternalWriteSaves() throws Exception {
      open();
      externalWrite("ALIAS1");
      String id = open();
      save(id);
      assertNotNull(stored.getPartitionTable("ALIAS1"));
   }

   @Test
   void staleEditorIsStillRefusedAfterAnotherEditorSaved() throws Exception {
      String first = open();
      String second = open();
      save(second);
      assertThrows(MessageException.class, () -> save(first));
   }

   @Test
   void saveStaysUsableWhenTheRuntimeRefreshFailsOnceAfterTheWrite() throws Exception {
      String id = open();
      // the view is written, then the runtime is busy once
      doThrow(new MessageException("busy")).doCallRealMethod()
         .when(runtimePartitionService).update(anyString(), any());

      save(id);
      save(id);
      assertNotNull(stored.getPartitionTable("ORDERS"));
   }

   private void externalWrite(String table) {
      XPartition changed = (XPartition) stored.clone();
      changed.addTable(table, new Rectangle(100, 10, 80, 60));
      changed.setLastModified(clock.incrementAndGet());
      stored = changed;
   }

   private String open() throws Exception {
      return manager.openModel("ds", null, "v", null).getId();
   }

   private void save(String id) throws Exception {
      manager.updateAndSaveModel("ds", null, null, "v", definition(id), null);
   }

   private static PhysicalModelDefinition definition(String id) {
      PhysicalModelDefinition definition = new PhysicalModelDefinition();
      definition.setId(id);
      definition.setName("v");
      return definition;
   }

   // the cluster cache hands out copies that keep every field
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
