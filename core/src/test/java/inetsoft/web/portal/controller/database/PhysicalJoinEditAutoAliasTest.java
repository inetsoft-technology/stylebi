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
import inetsoft.uql.erm.*;
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
 * Bug #77312: in the physical view join edit pane, deleting the only join condition to an
 * auto-alias table and re-creating it attached the join to another auto-alias of the same
 * table. The auto-alias identity (the {@link AutoAlias.IncomingJoin}) must survive inside the
 * pane session and orphans are pruned when the pane is closed with Done.
 * <p>
 * The partition mirrors Examples &gt; Orders &gt; "Order View": REGIONS is auto-aliased as
 * "Customer Region" (from CUSTOMERS) and "Salesperson Region" (from SALES_EMPLOYEES).
 */
@Tag("core")
class PhysicalJoinEditAutoAliasTest {
   private RuntimePartitionService runtimePartitionService;
   private PhysicalGraphModelController controller;

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @BeforeEach
   void setUp() {
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
      PhysicalModelManagerService manager = new PhysicalModelManagerService(
         null, null, runtimePartitionService, null, null, null, null, null);
      controller = new PhysicalGraphModelController(runtimePartitionService, null, null, manager);
   }

   @Test
   void recreateOnlyCustomerJoinInPaneKeepsCustomerRegion() throws Exception {
      String origin = openModel(buildOrderView());
      String pane = runtimePartitionService.openNewRuntimePartition(origin);

      controller.deleteJoin(detail(pane, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID"));
      recreate(pane, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID");
      controller.closeJoinEditPane(origin, pane, true);

      Set<String> rels = appliedRelationships(partition(origin));
      assertTrue(rels.contains("CUSTOMERS.REGION_ID=Customer Region.REGION_ID"), rels::toString);
      assertTrue(rels.contains("SALES_EMPLOYEES.REGION_ID=Salesperson Region.REGION_ID"),
                 rels::toString);
      assertFalse(rels.contains("CUSTOMERS.REGION_ID=Salesperson Region.REGION_ID"),
                  rels::toString);
      assertEquals(appliedRelationships(buildOrderView()), rels);
      assertEquals(List.of("CUSTOMERS->Customer Region", "SALES_EMPLOYEES->Salesperson Region"),
                   incoming(partition(origin), "REGIONS"));
   }

   @Test
   void recreateOnlySalespersonJoinInPaneKeepsSalespersonRegion() throws Exception {
      String origin = openModel(buildOrderView());
      String pane = runtimePartitionService.openNewRuntimePartition(origin);

      controller.deleteJoin(detail(pane, "SALES_EMPLOYEES", "REGION_ID", "REGIONS", "REGION_ID"));
      recreate(pane, "SALES_EMPLOYEES", "REGION_ID", "REGIONS", "REGION_ID");
      controller.closeJoinEditPane(origin, pane, true);

      Set<String> rels = appliedRelationships(partition(origin));
      assertTrue(rels.contains("SALES_EMPLOYEES.REGION_ID=Salesperson Region.REGION_ID"),
                 rels::toString);
      assertFalse(rels.contains("SALES_EMPLOYEES.REGION_ID=Customer Region.REGION_ID"),
                  rels::toString);
      assertEquals(appliedRelationships(buildOrderView()), rels);
   }

   @Test
   void recreateWithDifferentColumnsKeepsCustomerRegion() throws Exception {
      String origin = openModel(buildOrderView());
      String pane = runtimePartitionService.openNewRuntimePartition(origin);

      controller.deleteJoin(detail(pane, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID"));
      recreate(pane, "CUSTOMERS", "CITY", "REGIONS", "REGION");
      controller.closeJoinEditPane(origin, pane, true);

      Set<String> rels = appliedRelationships(partition(origin));
      assertTrue(rels.contains("CUSTOMERS.CITY=Customer Region.REGION"), rels::toString);
      assertFalse(rels.contains("CUSTOMERS.CITY=Salesperson Region.REGION"), rels::toString);
   }

   @Test
   void deleteOnlyJoinInPaneThenDoneRemovesAlias() throws Exception {
      String origin = openModel(buildOrderView());
      String pane = runtimePartitionService.openNewRuntimePartition(origin);

      controller.deleteJoin(detail(pane, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID"));
      controller.closeJoinEditPane(origin, pane, true);

      XPartition result = partition(origin);
      assertEquals(List.of("SALES_EMPLOYEES->Salesperson Region"), incoming(result, "REGIONS"));
      assertFalse(appliedTables(result).contains("Customer Region"));

      // same result as the eager removal on the main graph
      XPartition eager = buildOrderView();
      new PhysicalModelManagerService(null, null, null, null, null, null, null, null)
         .deleteJoin(eager, find(eager, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID"));
      assertEquals(appliedRelationships(eager), appliedRelationships(result));
      assertEquals(appliedTables(eager), appliedTables(result));
   }

   @Test
   void cancelPaneLeavesOriginUntouched() throws Exception {
      String origin = openModel(buildOrderView());
      String pane = runtimePartitionService.openNewRuntimePartition(origin);

      controller.deleteJoin(detail(pane, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID"));
      controller.closeJoinEditPane(origin, pane, false);

      XPartition result = partition(origin);
      assertEquals(List.of("CUSTOMERS->Customer Region", "SALES_EMPLOYEES->Salesperson Region"),
                   incoming(result, "REGIONS"));
      assertEquals(appliedRelationships(buildOrderView()), appliedRelationships(result));
      assertNull(runtimePartitionService.getRuntimePartition(pane));
   }

   @Test
   void deleteLinkOnMainGraphStillRemovesAliasImmediately() {
      String origin = openModel(buildOrderView());
      TableJoinInfo info = new TableJoinInfo();
      info.setRuntimeId(origin);
      info.setSourceTable("CUSTOMERS");
      info.setTargetTable("REGIONS");

      controller.deleteJoins(info);

      assertEquals(List.of("SALES_EMPLOYEES->Salesperson Region"),
                   incoming(partition(origin), "REGIONS"));
   }

   @Test
   void extendedViewRecreateKeepsOwnAliasAndBaseAliases() throws Exception {
      // base: Order View with its own REGIONS auto-alias
      XPartition base = buildOrderView();
      // extended view: adds TERRITORIES auto-aliased from CUSTOMERS and SALES_EMPLOYEES
      XPartition child = new XPartition("Extended");
      child.setBaseParitition(base);

      child.addTable("TERRITORIES", new Rectangle(0, 0, 50, 50));
      child.addRelationship(
         new XRelationship("CUSTOMERS", "TERRITORY_ID", "TERRITORIES", "TERRITORY_ID", "="));
      child.addRelationship(
         new XRelationship("SALES_EMPLOYEES", "TERRITORY_ID", "TERRITORIES", "TERRITORY_ID", "="));
      child.setAutoAlias("TERRITORIES", autoAlias(
         "CUSTOMERS", "Customer Territory", "SALES_EMPLOYEES", "Salesperson Territory"));

      String origin = openModel(child);
      String pane = runtimePartitionService.openNewRuntimePartition(origin);

      controller.deleteJoin(
         detail(pane, "CUSTOMERS", "TERRITORY_ID", "TERRITORIES", "TERRITORY_ID"));
      recreate(pane, "CUSTOMERS", "TERRITORY_ID", "TERRITORIES", "TERRITORY_ID");
      controller.closeJoinEditPane(origin, pane, true);

      XPartition result = partition(origin);
      Set<String> rels = appliedRelationships(result);
      assertTrue(rels.contains("CUSTOMERS.TERRITORY_ID=Customer Territory.TERRITORY_ID"),
                 rels::toString);
      assertFalse(rels.contains("CUSTOMERS.TERRITORY_ID=Salesperson Territory.TERRITORY_ID"),
                  rels::toString);
      assertEquals(List.of("CUSTOMERS->Customer Territory",
                           "SALES_EMPLOYEES->Salesperson Territory"),
                   incoming(result, "TERRITORIES"));
      // base-backed auto-alias (relationships live in the base) is kept
      assertEquals(List.of("CUSTOMERS->Customer Region", "SALES_EMPLOYEES->Salesperson Region"),
                   incoming(result, "REGIONS"));
      assertEquals(List.of("CUSTOMERS->Customer Region", "SALES_EMPLOYEES->Salesperson Region"),
                   incoming(base, "REGIONS"));
   }

   @Test
   void preExistingOrphanAliasIsPrunedOnDone() throws Exception {
      XPartition view = buildOrderView();
      AutoAlias regions = view.getAutoAlias("REGIONS");
      AutoAlias.IncomingJoin orphan = new AutoAlias.IncomingJoin();
      orphan.setSourceTable("ORDERS");
      orphan.setAlias("Order Region");
      regions.addIncomingJoin(orphan);

      String origin = openModel(view);
      String pane = runtimePartitionService.openNewRuntimePartition(origin);
      controller.closeJoinEditPane(origin, pane, true);

      XPartition result = partition(origin);
      assertEquals(List.of("CUSTOMERS->Customer Region", "SALES_EMPLOYEES->Salesperson Region"),
                   incoming(result, "REGIONS"));
      assertFalse(appliedTables(result).contains("Order Region"));
      assertEquals(appliedRelationships(buildOrderView()), appliedRelationships(result));
   }

   @Test
   void sourceSideAliasIsPrunedWhenItsLastJoinIsDeletedInPane() throws Exception {
      XPartition view = buildOrderView();
      // auto-alias keyed by the dependent table of the join
      view.setAutoAlias("CUSTOMERS", autoAlias("REGIONS", "Region Customer", null, null));

      String origin = openModel(view);
      String pane = runtimePartitionService.openNewRuntimePartition(origin);
      controller.deleteJoin(detail(pane, "CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID"));
      assertNotNull(partition(pane).getAutoAlias("CUSTOMERS"), "kept during the pane session");
      controller.closeJoinEditPane(origin, pane, true);

      assertNull(partition(origin).getAutoAlias("CUSTOMERS"));
   }

   private String openModel(XPartition partition) {
      return runtimePartitionService.createModel(partition, "Orders").getId();
   }

   private XPartition partition(String runtimeId) {
      return runtimePartitionService.getPartition(runtimeId);
   }

   /** What PhysicalGraphModelController.createJoin does to the partition. */
   private void recreate(String runtimeId, String src, String srcCol, String tgt, String tgtCol) {
      XPartition partition = partition(runtimeId);
      partition.addRelationship(new XRelationship(src, srcCol, tgt, tgtCol, "="));
      runtimePartitionService.updatePartition(runtimeId, partition);
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

      if(src2 != null) {
         AutoAlias.IncomingJoin j2 = new AutoAlias.IncomingJoin();
         j2.setSourceTable(src2);
         j2.setAlias(alias2);
         autoAlias.addIncomingJoin(j2);
      }

      return autoAlias;
   }

   private static XRelationship find(XPartition p, String src, String srcCol, String tgt,
                                     String tgtCol)
   {
      for(int i = 0; i < p.getRelationshipCount(); i++) {
         XRelationship r = p.getRelationship(i);

         if(r.getDependentTable().equals(src) && r.getDependentColumn().equals(srcCol) &&
            r.getIndependentTable().equals(tgt) && r.getIndependentColumn().equals(tgtCol))
         {
            return r;
         }
      }

      return null;
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
}
