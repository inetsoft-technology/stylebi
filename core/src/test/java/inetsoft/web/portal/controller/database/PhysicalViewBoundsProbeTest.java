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
import inetsoft.uql.service.DataSourceStorageException;
import inetsoft.web.portal.model.database.PhysicalModelDefinition;
import inetsoft.web.portal.model.database.PhysicalTableModel;
import inetsoft.web.portal.model.database.TableType;
import inetsoft.web.portal.service.database.PhysicalGraphService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.cache.Cache;
import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unknown (a) of the data modeling design (section 6): does a physical view whose tables were
 * added WITHOUT bounds (the {@code PhysicalModelService.createPartition} path of
 * {@code PhysicalModelManagerService.createModel}) survive create, save and reopen? The bridge
 * never uses that path (it adds every table through {@code addTable(..., fixBounds=true)}), so
 * no bridge code depends on the answer; the test records what the server does. The verdict is
 * in {@code 04-build-j4.md}.
 *
 * <p>Harness of {@link PhysicalViewLayoutSaveOpenTest}: real runtime service over a mocked
 * cluster cache, real manager and graph service.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PhysicalViewBoundsProbeTest {
   private final Map<String, RuntimePartitionService.RuntimeXPartition> runtimes =
      new ConcurrentHashMap<>();
   private final XDataModel dataModel = mock(XDataModel.class);
   private final Principal principal = () -> "admin";
   private RuntimePartitionService runtimePartitionService;
   private PhysicalModelManagerService manager;
   private XPartition stored;
   private DataSourceService dataSourceService;

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @BeforeEach
   void setUp() throws Exception {
      Cache<String, RuntimePartitionService.RuntimeXPartition> cache = mock(Cache.class);
      when(cache.get(any())).thenAnswer(i -> copy(runtimes.get(i.<String>getArgument(0))));
      doAnswer(i -> {
         runtimes.put(i.getArgument(0), copy(i.getArgument(1)));
         return null;
      }).when(cache).put(any(), any());
      doAnswer(i -> runtimes.remove(i.<String>getArgument(0)) != null)
         .when(cache).remove(any());
      Cluster cluster = mock(Cluster.class);
      when(cluster.getCache(anyString(), anyBoolean(), any())).thenReturn((Cache) cache);
      when(cluster.runInTransaction(anyLong(), any(), any())).thenAnswer(
         i -> i.<inetsoft.sree.internal.cluster.TransactionalAction<?, ?>>getArgument(2).run());
      runtimePartitionService = new RuntimePartitionService(cluster);
      runtimePartitionService.init();

      when(dataModel.getDataSource()).thenReturn("ds");
      when(dataModel.getLogicalModelNames()).thenReturn(new String[0]);
      when(dataModel.getPartition(anyString())).thenAnswer(
         i -> stored != null && stored.getName().equals(i.getArgument(0)) ? stored.clone() : null);
      doAnswer(i -> {
         stored = i.getArgument(0);
         return null;
      }).when(dataModel).addPartitionOrThrow(any());

      XRepository repository = mock(XRepository.class);
      when(repository.getDataModel("ds")).thenReturn(dataModel);
      dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(any(), any(), any(), any())).thenReturn(true);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(i -> i.getArgument(0));
      PhysicalModelService modelService = spy(new PhysicalModelService(
         runtimePartitionService, repository, null, dataSourceService, null));
      // the DTO of a runtime needs the database metadata; the probe only needs its id
      doAnswer(i -> {
         RuntimePartitionService.RuntimeXPartition rp = i.getArgument(1);
         PhysicalModelDefinition definition = new PhysicalModelDefinition();
         definition.setId(rp.getId());
         definition.setName(rp.getPartition().getName());
         return definition;
      }).when(modelService).createModel(any(XDataModel.class),
                                        any(RuntimePartitionService.RuntimeXPartition.class));
      doReturn(dataModel).when(modelService).getDataModel(anyString(), any());

      manager = new PhysicalModelManagerService(
         dataSourceService, modelService, runtimePartitionService, new PhysicalGraphService(),
         repository, null, mock(DependencyHandler.class), null);
   }

   /**
    * Bug #82205: when the storage did not save the view, the lookup after the save finds nothing
    * and the caller gets a retryable storage failure, not a NullPointerException.
    */
   @Test
   void unsavedViewIsAStorageFailureNotANullPointer() throws Exception {
      when(dataSourceService.getModelAssetEntry(any())).thenReturn(null);
      PhysicalModelDefinition definition = new PhysicalModelDefinition();
      definition.setName("v");
      definition.setTables(new ArrayList<>(List.of(table("ORDERS"))));
      String id = manager.createModel("ds", null, definition, principal).getId();
      PhysicalModelDefinition toSave = new PhysicalModelDefinition();
      toSave.setId(id);
      toSave.setName("v");

      DataSourceStorageException e = assertThrows(
         DataSourceStorageException.class,
         () -> manager.createAndSaveModel("ds", null, null, toSave, principal));
      assertTrue(e.getMessage().contains("retry"), e.getMessage());
   }

   /**
    * Unknown (a): tables with no bounds through create, save and reopen. Records the outcome as
    * the assertions below: tolerant means every table is stored and has bounds afterwards.
    */
   @Test
   void unknownBoundsOnCreateTablesDecided() throws Exception {
      PhysicalModelDefinition definition = new PhysicalModelDefinition();
      definition.setName("v");
      definition.setTables(new ArrayList<>(List.of(
         table("ORDERS"), table("CUSTOMERS"), table("PRODUCTS"))));

      PhysicalModelDefinition created = manager.createModel("ds", null, definition, principal);
      String id = created.getId();
      Map<String, Rectangle> before = bounds(runtimePartitionService.getPartition(id));
      assertEquals(Set.of("ORDERS", "CUSTOMERS", "PRODUCTS"), before.keySet());

      PhysicalModelDefinition toSave = new PhysicalModelDefinition();
      toSave.setId(id);
      toSave.setName("v");
      manager.createAndSaveModel("ds", null, null, toSave, principal);

      assertNotNull(stored, "the view was stored");
      Map<String, Rectangle> saved = bounds(stored);
      assertEquals(Set.of("ORDERS", "CUSTOMERS", "PRODUCTS"), saved.keySet(), "tables survive");
      System.out.println("PROBE(a) bounds before save " + before + " stored " + saved);
      saved.forEach((t, b) -> assertNotNull(b, "bounds of " + t));

      // reopen
      String reopened = manager.openModel("ds", null, "v", principal).getId();
      assertEquals(Set.of("ORDERS", "CUSTOMERS", "PRODUCTS"),
                   bounds(runtimePartitionService.getPartition(reopened)).keySet());
   }

   private static PhysicalTableModel table(String name) {
      PhysicalTableModel table = new PhysicalTableModel();
      table.setName(name);
      table.setQualifiedName(name);
      table.setType(TableType.PHYSICAL);
      table.setJoins(new ArrayList<>());
      table.setAutoAliases(new ArrayList<>());
      return table;
   }

   private static Map<String, Rectangle> bounds(XPartition partition) {
      Map<String, Rectangle> result = new TreeMap<>();
      Collections.list(partition.getTables(true))
         .forEach(t -> result.put(t.getName(), partition.getBounds(t.getName())));
      return result;
   }

   private static RuntimePartitionService.RuntimeXPartition copy(
      RuntimePartitionService.RuntimeXPartition runtime)
   {
      return runtime == null ? null : new RuntimePartitionService.RuntimeXPartition(
         (XPartition) runtime.getPartition().deepClone(true), runtime.getId(),
         runtime.getDataSource());
   }
}
