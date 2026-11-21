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
package inetsoft.web.wiz.controller;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XNode;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.rgraph.TableNode;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.service.database.PhysicalGraphService;
import inetsoft.web.wiz.controller.WizPhysicalViewWire.*;
import org.junit.jupiter.api.BeforeEach;

import javax.cache.Cache;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The harness of the physical view bridge tests: the REAL data model registry (through
 * {@link WizLogicalModelTestBase}), the REAL {@code RuntimePartitionService} over a map-backed
 * cluster cache (so a test can count the runtimes left on the server), the REAL
 * {@code PhysicalModelManagerService} and {@code PhysicalModelService}, with only the database
 * metadata, the permission checks and the dependency handlers mocked.
 *
 * <p>The database has tables {@code T1 (ID, AMOUNT, NAME)}, {@code T2 (CODE, T1_ID)},
 * {@code T3 (ID, T2_CODE)} and {@code T4 (X)}. The inherited views {@code pv} and {@code pv2}
 * are over {@code T1} and {@code T2}.</p>
 */
abstract class WizPhysicalViewTestBase extends WizLogicalModelTestBase {
   /** The runtimes the server holds: empty when nothing leaked. */
   protected final Map<String, RuntimePartitionService.RuntimeXPartition> runtimes =
      new ConcurrentHashMap<>();
   /** How many times a runtime was stored: zero when a request opened none. */
   protected final java.util.concurrent.atomic.AtomicInteger runtimePuts =
      new java.util.concurrent.atomic.AtomicInteger();
   protected RuntimePartitionService runtimePartitionService;
   protected PhysicalModelService physicalService;
   protected PhysicalModelManagerService manager;
   protected SecurityEngine security;
   protected WizPhysicalViewController physicalController;

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @BeforeEach
   void setUpPhysicalHarness() throws Exception {
      runtimes.clear();
      runtimePuts.set(0);
      Cache<String, RuntimePartitionService.RuntimeXPartition> cache = mock(Cache.class);
      when(cache.get(any())).thenAnswer(i -> copy(runtimes.get(i.<String>getArgument(0))));
      doAnswer(i -> {
         runtimes.put(i.getArgument(0), copy(i.getArgument(1)));
         runtimePuts.incrementAndGet();
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

      physical.get("T2").put("T1_ID", "integer");
      physical.put("T3", new LinkedHashMap<>(Map.of("ID", "integer")));
      physical.get("T3").put("T2_CODE", "string");
      physical.put("T4", new LinkedHashMap<>(Map.of("X", "string")));

      when(metaProvider.getTable(any(String.class), any(String.class)))
         .thenAnswer(i -> tableNode(i.getArgument(0)));
      when(metaProvider.getTable(any(), any(), anyString(), any(), anyBoolean()))
         .thenAnswer(i -> i.getArgument(1) != null && "NOPE".equals(i.getArgument(1)) ? null :
            tableNode(i.getArgument(2)));
      when(metaProvider.getTableMetaData(any(XNode.class)))
         .thenAnswer(i -> tableMetaData(((XNode) i.getArgument(0)).getName()));
      when(metaProvider.getDataSource()).thenReturn(jdbcSource);
      when(jdbcSource.getFullName()).thenReturn(ds);

      physicalService = spy(new PhysicalModelService(
         runtimePartitionService, repository, assetRepository, dataSourceService, null));
      manager = spy(new PhysicalModelManagerService(
         dataSourceService, physicalService, runtimePartitionService, new PhysicalGraphService(),
         repository, DataSourceRegistry.getRegistry(), dependencyHandler, transformHandler));
      security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), eq(ResourceType.CROSS_JOIN), anyString(), any()))
         .thenReturn(true);
      physicalController = newPhysicalController(manager);
   }

   protected WizPhysicalViewController newPhysicalController(PhysicalModelManagerService pmms) {
      return new WizPhysicalViewController(
         repository, dataSourceService, modelService, pmms, physicalService,
         runtimePartitionService, new PhysicalGraphService(), security,
         new WizPhysicalViewOps(dataSourceService, pmms));
   }

   private XNode tableNode(String name) {
      if(!physical.containsKey(name)) {
         return null;
      }

      XNode node = new XNode(name);
      node.setAttribute("type", "TABLE");
      node.setAttribute("supportCatalog", "false");
      return node;
   }

   private TableNode tableMetaData(String name) {
      Map<String, String> columns = physical.get(name);

      if(columns == null) {
         return null;
      }

      TableNode table = new TableNode(name);

      for(Map.Entry<String, String> column : columns.entrySet()) {
         table.addColumn(column.getKey());
         table.addColumnType(column.getKey(), column.getValue());
      }

      return table;
   }

   private static RuntimePartitionService.RuntimeXPartition copy(
      RuntimePartitionService.RuntimeXPartition runtime)
   {
      return runtime == null ? null : new RuntimePartitionService.RuntimeXPartition(
         (XPartition) runtime.getPartition().deepClone(true), runtime.getId(),
         runtime.getDataSource());
   }

   // -- builders -------------------------------------------------------------------------------

   protected static Map<String, Object> plain(String name) {
      return new LinkedHashMap<>(Map.of("name", name));
   }

   protected static Map<String, Object> alias(String name, String of) {
      return new LinkedHashMap<>(Map.of("name", name, "aliasOf", of));
   }

   protected static Map<String, Object> inline(String name, String sql) {
      return new LinkedHashMap<>(Map.of("name", name, "sql", sql));
   }

   protected static Map<String, Object> join(String from, String fromColumn, String to,
                                             String toColumn)
   {
      Map<String, Object> join = new LinkedHashMap<>();
      join.put("from", new LinkedHashMap<>(Map.of("table", from, "column", fromColumn)));
      join.put("to", new LinkedHashMap<>(Map.of("table", to, "column", toColumn)));
      join.put("type", "EQUAL");
      join.put("cardinality", "MANY_TO_ONE");
      return join;
   }

   @SafeVarargs
   protected static Map<String, Object> definition(String name, List<Map<String, Object>> tables,
                                                   Map<String, Object>... joins)
   {
      Map<String, Object> definition = new LinkedHashMap<>();
      definition.put("name", name);
      definition.put("tables", new ArrayList<>(tables));
      definition.put("joins", new ArrayList<>(List.of(joins)));
      return definition;
   }

   @SafeVarargs
   protected static List<Map<String, Object>> tables(Map<String, Object>... tables) {
      return new ArrayList<>(List.of(tables));
   }

   protected WriteResponse createView(Map<String, Object> definition) throws Exception {
      Object result = physicalController.create(
         new WriteRequest(ds, null, null, definition, null), bob);
      return (WriteResponse) result;
   }

   protected Object createViewRaw(Map<String, Object> definition, Boolean dryRun)
      throws Exception
   {
      return physicalController.create(new WriteRequest(ds, null, null, definition, dryRun),
                                       bob);
   }

   protected Object updateView(String name, Map<String, Object> definition, Boolean dryRun)
      throws Exception
   {
      return physicalController.update(new WriteRequest(ds, name, null, definition, dryRun), bob);
   }

   /** The success body of a call, or a failure that prints the server's answer. */
   protected static WriteResponse ok(Object result) {
      assertTrue(result instanceof WriteResponse, String.valueOf(result));
      return (WriteResponse) result;
   }

   protected GetResponse getView(String name) throws Exception {
      return (GetResponse) physicalController.get(new GetRequest(ds, name, null), bob);
   }

   protected XPartition storedView(String name) {
      return fresh().getPartition(name);
   }
}
