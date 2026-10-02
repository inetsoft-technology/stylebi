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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.uql.erm.*;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.events.EditJoinsEvent;
import inetsoft.web.portal.model.database.events.GetGraphModelEvent;
import inetsoft.web.portal.model.database.graph.TableDetailJoinInfo;
import inetsoft.web.portal.model.database.graph.TableJoinInfo;
import org.junit.jupiter.api.*;

import javax.cache.Cache;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77338: in the physical view table editor (Outgoing Joins), removing the only join to an
 * auto-alias table and adding it back attached the join to another auto-alias of the same
 * table, creating a cycle. The table editor edits the main runtime partition, so the removal
 * stays eager and the removed {@link AutoAlias.IncomingJoin} is remembered on the runtime and
 * restored when a join between the same tables is added again.
 * <p>
 * The requests go through {@link PhysicalModelController} with bodies in the shape the client
 * sends. The partition mirrors Examples &gt; Orders &gt; "Order View": REGIONS is auto-aliased
 * as "Customer Region" (from CUSTOMERS) and "Salesperson Region" (from SALES_EMPLOYEES).
 */
@Tag("core")
class PhysicalTableEditorAutoAliasTest {
   private RuntimePartitionService runtimePartitionService;
   private PhysicalModelManagerService manager;
   private PhysicalModelService physicalModelService;
   private PhysicalModelController controller;
   private PhysicalGraphModelController graphController;
   private final ObjectMapper objectMapper = new ObjectMapper();

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @BeforeEach
   void setUp() throws Exception {
      Map<String, RuntimePartitionService.RuntimeXPartition> store = new ConcurrentHashMap<>();
      Cache<String, RuntimePartitionService.RuntimeXPartition> cache = mock(Cache.class);
      when(cache.get(any())).thenAnswer(i -> store.get(i.<String>getArgument(0)));
      doAnswer(i -> { store.put(i.getArgument(0), i.getArgument(1)); return null; })
         .when(cache).put(any(), any());
      when(cache.remove(any())).thenAnswer(i -> store.remove(i.<String>getArgument(0)) != null);
      Cluster cluster = mock(Cluster.class);
      when(cluster.getCache(anyString(), anyBoolean(), any())).thenReturn((Cache) cache);

      runtimePartitionService = new RuntimePartitionService(cluster);
      runtimePartitionService.init();
      physicalModelService =
         spy(new PhysicalModelService(runtimePartitionService, null, null, null, null));
      // no data source: joins are created with the default cardinality
      doReturn(mock(DefaultMetaDataProvider.class))
         .when(physicalModelService).getMetaDataProvider(anyString());
      manager = new PhysicalModelManagerService(
         null, physicalModelService, runtimePartitionService, null, null, null, null, null);
      controller = new PhysicalModelController(
         runtimePartitionService, null, null, null, null, manager, null);
      graphController = new PhysicalGraphModelController(
         runtimePartitionService, physicalModelService, null, manager);
   }

   @Test
   void removeAndAddCustomerJoinKeepsCustomerRegion() throws Exception {
      String id = openModel(buildOrderView());

      removeJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");
      addJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");

      Set<String> rels = appliedRelationships(partition(id));
      assertTrue(rels.contains("CUSTOMERS.REGION_ID=Customer Region.REGION_ID"), rels::toString);
      assertFalse(rels.contains("CUSTOMERS.REGION_ID=Salesperson Region.REGION_ID"),
                  rels::toString);
      assertEquals(appliedRelationships(buildOrderView()), rels);
      assertEquals(ORIGINAL_INCOMING, incoming(partition(id), "REGIONS"));
      assertEquals(XPartition.VALID, partition(id).applyAutoAliases().getStatus());
   }

   @Test
   void removeAndAddSalespersonJoinKeepsSalespersonRegionAndOrder() throws Exception {
      String id = openModel(buildOrderView());

      removeJoin(id, "SALES_EMPLOYEES", "REGION_ID", "REGIONS", "REGION_ID");
      addJoin(id, "SALES_EMPLOYEES", "REGION_ID", "REGIONS", "REGION_ID");

      Set<String> rels = appliedRelationships(partition(id));
      assertFalse(rels.contains("SALES_EMPLOYEES.REGION_ID=Customer Region.REGION_ID"),
                  rels::toString);
      assertEquals(appliedRelationships(buildOrderView()), rels);
      // restored at its original position
      assertEquals(ORIGINAL_INCOMING, incoming(partition(id), "REGIONS"));
   }

   @Test
   void addOnDifferentColumnsKeepsCustomerRegion() throws Exception {
      String id = openModel(buildOrderView());

      removeJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");
      addJoin(id, "CUSTOMERS", "CITY", "REGIONS", "REGION");

      Set<String> rels = appliedRelationships(partition(id));
      assertTrue(rels.contains("CUSTOMERS.CITY=Customer Region.REGION"), rels::toString);
      assertFalse(rels.contains("CUSTOMERS.CITY=Salesperson Region.REGION"), rels::toString);
   }

   @Test
   void removeOnlyIsUnchanged() throws Exception {
      String id = openModel(buildOrderView());

      removeJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");

      XPartition result = partition(id);
      assertEquals(List.of("SALES_EMPLOYEES->Salesperson Region"), incoming(result, "REGIONS"));
      assertFalse(appliedTables(result).contains("Customer Region"));
      assertEquals(XPartition.VALID, result.applyAutoAliases().getStatus());
      assertNotNull(runtime(id).getRemovedIncomingJoin("REGIONS", "CUSTOMERS"));
   }

   @Test
   void removeWhileAnotherJoinRemainsRemembersNothing() throws Exception {
      XPartition view = buildOrderView();
      view.addRelationship(new XRelationship("CUSTOMERS", "CITY", "REGIONS", "REGION", "="));
      String id = openModel(view);

      removeJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");

      assertEquals(ORIGINAL_INCOMING, incoming(partition(id), "REGIONS"));
      assertNull(runtime(id).getRemovedIncomingJoin("REGIONS", "CUSTOMERS"));
   }

   @Test
   void secondAddDoesNotRestoreAgain() throws Exception {
      String id = openModel(buildOrderView());

      removeJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");
      addJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");
      addJoin(id, "CUSTOMERS", "CITY", "REGIONS", "REGION");

      assertEquals(ORIGINAL_INCOMING, incoming(partition(id), "REGIONS"));
      assertNull(runtime(id).getRemovedIncomingJoin("REGIONS", "CUSTOMERS"));
   }

   @Test
   void paneDoneBetweenRemoveAndAddKeepsMemory() throws Exception {
      String id = openModel(buildOrderView());

      removeJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");
      String pane = graphController.openJoinEditPane(id);

      // the pane gets a deep copy of the memory
      RuntimePartitionService.RemovedIncomingJoin originEntry =
         runtime(id).getRemovedIncomingJoin("REGIONS", "CUSTOMERS");
      RuntimePartitionService.RemovedIncomingJoin paneEntry =
         runtime(pane).getRemovedIncomingJoin("REGIONS", "CUSTOMERS");
      assertNotNull(paneEntry);
      assertNotSame(originEntry, paneEntry);
      assertNotSame(originEntry.getJoin(), paneEntry.getJoin());
      assertEquals(originEntry.getJoin().getAlias(), paneEntry.getJoin().getAlias());

      graphController.closeJoinEditPane(id, pane, true);
      addJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");

      assertEquals(appliedRelationships(buildOrderView()), appliedRelationships(partition(id)));
      assertEquals(ORIGINAL_INCOMING, incoming(partition(id), "REGIONS"));
   }

   @Test
   void paneCancelKeepsOriginMemory() throws Exception {
      String id = openModel(buildOrderView());

      removeJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");
      String pane = graphController.openJoinEditPane(id);
      graphController.createJoin(detail(pane, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID"));
      graphController.closeJoinEditPane(id, pane, false);
      addJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");

      assertEquals(appliedRelationships(buildOrderView()), appliedRelationships(partition(id)));
      assertEquals(ORIGINAL_INCOMING, incoming(partition(id), "REGIONS"));
   }

   @Test
   void paneCreateKeepsUsersChoiceOnLaterRemoveAndAdd() throws Exception {
      String id = openModel(buildOrderView());

      removeJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");
      String pane = graphController.openJoinEditPane(id);
      graphController.createJoin(detail(pane, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID"));
      graphController.closeJoinEditPane(id, pane, true);

      Set<String> chosen = appliedRelationships(partition(id));
      assertTrue(chosen.contains("CUSTOMERS.REGION_ID=Salesperson Region.REGION_ID"),
                 chosen::toString);
      assertNull(runtime(id).getRemovedIncomingJoin("REGIONS", "CUSTOMERS"));

      removeJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");
      addJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");

      assertEquals(chosen, appliedRelationships(partition(id)));
      assertFalse(appliedTables(partition(id)).contains("Customer Region"));
   }

   @Test
   void paneCreateThenDeleteStillRestoresOnAdd() throws Exception {
      String id = openModel(buildOrderView());

      removeJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");
      String pane = graphController.openJoinEditPane(id);
      graphController.createJoin(detail(pane, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID"));
      graphController.deleteJoin(detail(pane, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID"));
      graphController.closeJoinEditPane(id, pane, true);
      addJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");

      assertEquals(appliedRelationships(buildOrderView()), appliedRelationships(partition(id)));
   }

   /**
    * Dragging a table onto another one in the main graph opens the join edit pane, which asks
    * for the graph with autoCreateColumnJoin and so adds the join without calling createJoin.
    */
   @Test
   void paneDragAutoCreateThenLinkDeleteDoesNotRestore() throws Exception {
      String id = openModel(buildOrderView());

      removeJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");
      String pane = graphController.openJoinEditPane(id);
      dragJoin(pane, "CUSTOMERS", "REGIONS");
      assertNotNull(partition(pane).findRelationship("CUSTOMERS", "REGIONS"),
                    "the graph request auto-created the join");
      graphController.closeJoinEditPane(id, pane, true);

      assertTrue(appliedRelationships(partition(id))
                    .contains("CUSTOMERS.REGION_ID=Salesperson Region.REGION_ID"));
      assertNull(runtime(id).getRemovedIncomingJoin("REGIONS", "CUSTOMERS"));

      TableJoinInfo link = new TableJoinInfo();
      link.setRuntimeId(id);
      link.setSourceTable("CUSTOMERS");
      link.setTargetTable("REGIONS");
      graphController.deleteJoins(link);
      addJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");

      assertFalse(appliedTables(partition(id)).contains("Customer Region"));
      assertEquals(List.of("SALES_EMPLOYEES->Salesperson Region"),
                   incoming(partition(id), "REGIONS"));
   }

   @Test
   void aliasNameTakenDoesNotRestore() throws Exception {
      String id = openModel(buildOrderView());

      removeJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");
      RuntimePartitionService.RuntimeXPartition rp = runtime(id);
      rp.getPartition().addTable("Customer Region", new Rectangle(0, 0, 50, 50));
      runtimePartitionService.saveRuntimePartition(rp);
      addJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");

      assertEquals(List.of("SALES_EMPLOYEES->Salesperson Region"),
                   incoming(partition(id), "REGIONS"));
      assertNull(runtime(id).getRemovedIncomingJoin("REGIONS", "CUSTOMERS"));
   }

   @Test
   void explicitAutoAliasEditForgetsTable() throws Exception {
      String id = openModel(buildOrderView());

      removeJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");
      manager.updateAutoAliasing(id, regionsTable("SALES_EMPLOYEES", "Salesperson Region"));
      assertNull(runtime(id).getRemovedIncomingJoin("REGIONS", "CUSTOMERS"));
      addJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");

      assertEquals(List.of("SALES_EMPLOYEES->Salesperson Region"),
                   incoming(partition(id), "REGIONS"));
      assertFalse(appliedTables(partition(id)).contains("Customer Region"));
   }

   @Test
   void forgetTableMatchesSourceTable() throws Exception {
      String id = openModel(buildOrderView());

      removeJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");
      PhysicalTableModel customers = new PhysicalTableModel();
      customers.setName("CUSTOMERS");
      customers.setQualifiedName("CUSTOMERS");
      manager.updateAutoAliasing(id, customers);

      assertNull(runtime(id).getRemovedIncomingJoin("REGIONS", "CUSTOMERS"));
   }

   /**
    * removeTable forgets the entries of the table. Driving it end to end needs the asset
    * registry (XPartition.removeTable lists the extended views), so check the memory directly.
    */
   @Test
   void forgetTableMatchesEitherKeyPosition() {
      RuntimePartitionService.RuntimeXPartition rp = new RuntimePartitionService.RuntimeXPartition(
         buildOrderView(), "id", "Orders");
      rp.rememberRemovedIncomingJoin(removed("REGIONS", "CUSTOMERS", "Customer Region"));
      rp.rememberRemovedIncomingJoin(removed("CUSTOMERS", "REGIONS", "Region Customer"));
      rp.rememberRemovedIncomingJoin(removed("REGIONS", "SALES_EMPLOYEES", "Salesperson Region"));

      rp.forgetRemovedIncomingJoins("CUSTOMERS");

      assertNull(rp.getRemovedIncomingJoin("REGIONS", "CUSTOMERS"));
      assertNull(rp.getRemovedIncomingJoin("CUSTOMERS", "REGIONS"));
      assertNotNull(rp.getRemovedIncomingJoin("REGIONS", "SALES_EMPLOYEES"));

      rp.forgetRemovedIncomingJoins("REGIONS");
      assertNull(rp.getRemovedIncomingJoin("REGIONS", "SALES_EMPLOYEES"));
   }

   @Test
   void clearJoinsForgetsAll() throws Exception {
      String id = openModel(buildOrderView());

      removeJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");
      graphController.clearJoin(id);
      addJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");

      assertNull(partition(id).getAutoAlias("REGIONS"));
      assertFalse(appliedTables(partition(id)).contains("Customer Region"));
      assertTrue(appliedTables(partition(id)).contains("REGIONS"));
   }

   @Test
   void autoJoinRestoresCustomerRegion() throws Exception {
      String id = openModel(buildOrderView());

      removeJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");
      // no data source: the cardinality detection fails and is logged, the join is still added
      manager.addAutoJoin(id, event(id, "add", "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID")
         .getJoinItems()[0].getJoin(), "CUSTOMERS", null);

      assertEquals(appliedRelationships(buildOrderView()), appliedRelationships(partition(id)));
      assertEquals(ORIGINAL_INCOMING, incoming(partition(id), "REGIONS"));
   }

   @Test
   void extendedViewRestoresOwnAliasAndLeavesBaseAlone() throws Exception {
      XPartition base = buildOrderView();
      AutoAlias baseRegions = base.getAutoAlias("REGIONS");
      XPartition child = new XPartition("Extended");
      child.setBaseParitition(base);
      child.addTable("TERRITORIES", new Rectangle(0, 0, 50, 50));
      child.addRelationship(
         new XRelationship("CUSTOMERS", "TERRITORY_ID", "TERRITORIES", "TERRITORY_ID", "="));
      child.addRelationship(
         new XRelationship("SALES_EMPLOYEES", "TERRITORY_ID", "TERRITORIES", "TERRITORY_ID", "="));
      child.setAutoAlias("TERRITORIES", autoAlias(
         "CUSTOMERS", "Customer Territory", "SALES_EMPLOYEES", "Salesperson Territory"));

      String id = openModel(child);
      removeJoin(id, "CUSTOMERS", "TERRITORY_ID", "TERRITORIES", "TERRITORY_ID");
      addJoin(id, "CUSTOMERS", "TERRITORY_ID", "TERRITORIES", "TERRITORY_ID");

      Set<String> rels = appliedRelationships(partition(id));
      assertTrue(rels.contains("CUSTOMERS.TERRITORY_ID=Customer Territory.TERRITORY_ID"),
                 rels::toString);
      assertFalse(rels.contains("CUSTOMERS.TERRITORY_ID=Salesperson Territory.TERRITORY_ID"),
                  rels::toString);
      assertEquals(List.of("CUSTOMERS->Customer Territory",
                           "SALES_EMPLOYEES->Salesperson Territory"),
                   incoming(partition(id), "TERRITORIES"));
      assertEquals(ORIGINAL_INCOMING, incoming(partition(id).getBasePartition(), "REGIONS"));
      assertEquals(ORIGINAL_INCOMING, incoming(base, "REGIONS"));
      assertSame(baseRegions, base.getAutoAlias("REGIONS"));
   }

   @Test
   void extendedViewBaseOwnedAliasIsNotRemembered() throws Exception {
      // the base keeps the CUSTOMERS incoming join, the join itself lives in the child
      XPartition base = buildOrderView();
      base.removeRelationship(base.findRelationship("CUSTOMERS", "REGIONS"));
      XPartition child = new XPartition("Extended");
      child.setBaseParitition(base);
      child.addRelationship(
         new XRelationship("CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID", "="));

      String id = openModel(child);
      removeJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");
      assertNull(runtime(id).getRemovedIncomingJoin("REGIONS", "CUSTOMERS"));

      AutoAlias runtimeBaseRegions = partition(id).getBasePartition().getAutoAlias("REGIONS");
      int count = runtimeBaseRegions.getIncomingJoinCount();
      addJoin(id, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");

      // the base-owned auto-alias is never written by a restore
      assertSame(runtimeBaseRegions, partition(id).getBasePartition().getAutoAlias("REGIONS"));
      assertEquals(count, runtimeBaseRegions.getIncomingJoinCount());
   }

   private void removeJoin(String id, String table, String column, String foreignTable,
                           String foreignColumn) throws Exception
   {
      controller.removeJoin(event(id, "remove", table, column, foreignTable, foreignColumn));
   }

   private void addJoin(String id, String table, String column, String foreignTable,
                        String foreignColumn) throws Exception
   {
      controller.addJoin(event(id, "add", table, column, foreignTable, foreignColumn));
   }

   /**
    * What the client sends when a table is dropped onto another one in the graph: the pane
    * graph with autoCreateColumnJoin, handled by the real JoinGraphModel code.
    */
   private void dragJoin(String pane, String source, String target) throws Exception {
      PhysicalModelDefinition model = new PhysicalModelDefinition();
      model.setTables(List.of(graphTable(source), graphTable(target)));
      model.setMetaData(mock(DefaultMetaDataProvider.class));
      doReturn(null).when(physicalModelService).getDataModel(anyString(), anyString());
      doReturn(model).when(physicalModelService)
         .createModel(anyString(), any(), any(XPartition.class), anyBoolean());

      TableJoinInfo info = new TableJoinInfo();
      info.setRuntimeId(pane);
      info.setSourceTable(source);
      info.setTargetTable(target);
      info.setAutoCreateColumnJoin(true);
      GetGraphModelEvent event = new GetGraphModelEvent();
      event.setDatasource("Orders");
      event.setPhysicalName("Order View");
      event.setRuntimeID(pane);
      event.setTableJoinInfo(info);

      graphController.physicalGraphModel(event);
   }

   private static PhysicalTableModel graphTable(String name) {
      GraphColumnInfo column = new GraphColumnInfo();
      column.setName("REGION_ID");
      column.setType("integer");
      column.setTable(name);

      PhysicalTableModel table = new PhysicalTableModel();
      table.setName(name);
      table.setQualifiedName(name);
      table.setBounds(new Rectangle(0, 0, 50, 50));
      table.setCols(new ArrayList<>(List.of(column)));
      return table;
   }

   private static RuntimePartitionService.RemovedIncomingJoin removed(String table,
                                                                      String source,
                                                                      String alias)
   {
      AutoAlias.IncomingJoin join = new AutoAlias.IncomingJoin();
      join.setSourceTable(source);
      join.setAlias(alias);
      return new RuntimePartitionService.RemovedIncomingJoin(table, 0, join);
   }

   private static PhysicalTableModel regionsTable(String source, String alias) {
      AutoAliasJoinModel join = new AutoAliasJoinModel();
      join.setForeignTable(source);
      join.setAlias(alias);
      join.setSelected(true);

      PhysicalTableModel table = new PhysicalTableModel();
      table.setName("REGIONS");
      table.setQualifiedName("REGIONS");
      table.setAutoAliases(new ArrayList<>(List.of(join)));
      table.setAutoAliasesEnabled(true);
      return table;
   }

   /** The request body of PUT /join/remove and POST /join/add, as the client sends it. */
   private EditJoinsEvent event(String id, String action, String table, String column,
                                String foreignTable, String foreignColumn) throws Exception
   {
      String json = "{\"id\":\"" + id + "\",\"joinItems\":[{\"actionType\":\"" + action +
         "\",\"table\":{\"qualifiedName\":\"" + table + "\",\"name\":\"" + table + "\"}," +
         "\"join\":{\"table\":\"" + table + "\",\"column\":\"" + column +
         "\",\"foreignTable\":\"" + foreignTable + "\",\"foreignColumn\":\"" + foreignColumn +
         "\",\"type\":0,\"mergingRule\":0,\"orderPriority\":1,\"weak\":false," +
         "\"cardinality\":2,\"baseJoin\":false}}]}";
      return objectMapper.readValue(json, EditJoinsEvent.class);
   }

   private static TableDetailJoinInfo detail(String runtimeId, String src, String srcCol,
                                             String tgt, String tgtCol)
   {
      TableDetailJoinInfo info = new TableDetailJoinInfo();
      info.setRuntimeId(runtimeId);
      info.setSourceTable(src);
      info.setSourceColumn(srcCol);
      info.setTargetTable(tgt);
      info.setTargetColumn(tgtCol);
      return info;
   }

   private String openModel(XPartition partition) {
      return runtimePartitionService.createModel(partition, "Orders").getId();
   }

   private RuntimePartitionService.RuntimeXPartition runtime(String runtimeId) {
      return runtimePartitionService.getRuntimePartition(runtimeId);
   }

   private XPartition partition(String runtimeId) {
      return runtimePartitionService.getPartition(runtimeId);
   }

   private static XPartition buildOrderView() {
      XPartition p = new XPartition("Order View");

      for(String t : new String[]{ "ORDERS", "CUSTOMERS", "SALES_EMPLOYEES", "REGIONS" }) {
         p.addTable(t, new Rectangle(0, 0, 50, 50));
      }

      p.addRelationship(
         new XRelationship("ORDERS", "CUSTOMER_ID", "CUSTOMERS", "CUSTOMER_ID", "="));
      p.addRelationship(
         new XRelationship("ORDERS", "EMPLOYEE_ID", "SALES_EMPLOYEES", "EMPLOYEE_ID", "="));
      p.addRelationship(new XRelationship("CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID", "="));
      p.addRelationship(
         new XRelationship("SALES_EMPLOYEES", "REGION_ID", "REGIONS", "REGION_ID", "="));
      p.setAutoAlias("REGIONS", autoAlias(
         "CUSTOMERS", "Customer Region", "SALES_EMPLOYEES", "Salesperson Region"));
      return p;
   }

   private static AutoAlias autoAlias(String src1, String alias1, String src2, String alias2) {
      AutoAlias autoAlias = new AutoAlias();
      AutoAlias.IncomingJoin j1 = new AutoAlias.IncomingJoin();
      j1.setSourceTable(src1);
      j1.setAlias(alias1);
      autoAlias.addIncomingJoin(j1);

      AutoAlias.IncomingJoin j2 = new AutoAlias.IncomingJoin();
      j2.setSourceTable(src2);
      j2.setAlias(alias2);
      autoAlias.addIncomingJoin(j2);

      return autoAlias;
   }

   private static List<String> incoming(XPartition p, String table) {
      AutoAlias autoAlias = p.getAutoAlias(table);
      List<String> result = new ArrayList<>();

      for(int i = 0; autoAlias != null && i < autoAlias.getIncomingJoinCount(); i++) {
         AutoAlias.IncomingJoin join = autoAlias.getIncomingJoin(i);
         result.add(join.getSourceTable() + "->" + join.getAlias());
      }

      return result;
   }

   private static Set<String> appliedRelationships(XPartition p) {
      Set<String> result = new TreeSet<>();
      Enumeration<XRelationship> rels = p.applyAutoAliases().getRelationships();

      while(rels.hasMoreElements()) {
         XRelationship r = rels.nextElement();
         result.add(r.getDependentTable() + "." + r.getDependentColumn() + "=" +
                    r.getIndependentTable() + "." + r.getIndependentColumn());
      }

      return result;
   }

   private static Set<String> appliedTables(XPartition p) {
      Set<String> result = new TreeSet<>();
      Enumeration<XPartition.PartitionTable> tables = p.applyAutoAliases().getTables();

      while(tables.hasMoreElements()) {
         result.add(tables.nextElement().getName());
      }

      return result;
   }

   private static final List<String> ORIGINAL_INCOMING =
      List.of("CUSTOMERS->Customer Region", "SALES_EMPLOYEES->Salesperson Region");
}
