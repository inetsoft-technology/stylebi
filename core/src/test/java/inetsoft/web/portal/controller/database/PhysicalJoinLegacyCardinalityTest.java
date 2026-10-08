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
import inetsoft.uql.erm.*;
import inetsoft.util.Tool;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.events.EditJoinEvent;
import inetsoft.web.portal.model.database.graph.TableDetailJoinInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import javax.cache.Cache;
import java.awt.*;
import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78072: a legacy physical-view join that has no stored cardinality (0/0) is shown with
 * no cardinality (null) in the join dialogs. Editing only its type through the graph join-line
 * dialog (PUT join) or the joins pane (join/modify) stored it as MANY/MANY, which turned model
 * trap checking on and raised a chasm-trap warning. A missing cardinality now keeps 0/0;
 * explicit picks are stored as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PhysicalJoinLegacyCardinalityTest {
   private RuntimePartitionService runtimePartitionService;
   private PhysicalModelManagerService manager;
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
      manager = new PhysicalModelManagerService(
         null, null, runtimePartitionService, null, null, null, null, null);
      controller = new PhysicalGraphModelController(runtimePartitionService, null, null, manager);
   }

   @Test
   void legacyFixtureHasNoCardinalityAndTrapCheckingOff() throws Exception {
      XPartition legacy = legacyPartition();
      XRelationship rel = legacy.getRelationship(0);
      assertCardinality(rel, 0, 0);
      assertNull(clientJoin(rel).getCardinality(), "0/0 is sent to the client as null");

      TrapContext trap = trap(legacy);
      assertFalse(trap.isCheckTrap());
   }

   @Test
   void typeOnlyEditInJoinDialogKeepsLegacyCardinality() throws Exception {
      XPartition result = editInJoinDialog(JoinType.LEFT_OUTER, null);
      XRelationship rel = result.getRelationship(0);

      assertEquals(XRelationship.LEFT_OUTER, rel.getJoinType());
      assertCardinality(rel, 0, 0);
      TrapContext trap = trap(result);
      assertFalse(trap.isCheckTrap());
      assertFalse(trap.check().showWarning());
   }

   @Test
   void typeOnlyEditInJoinsPaneKeepsLegacyCardinality() throws Exception {
      XPartition result = editInJoinsPane(JoinType.LEFT_OUTER, null);
      XRelationship rel = result.getRelationship(0);

      assertEquals(1, result.getRelationshipCount());
      assertEquals(XRelationship.LEFT_OUTER, rel.getJoinType());
      assertCardinality(rel, 0, 0);
      TrapContext trap = trap(result);
      assertFalse(trap.isCheckTrap());
      assertFalse(trap.check().showWarning());
   }

   @Test
   void explicitManyToOnePickIsStored() throws Exception {
      assertCardinality(
         editInJoinDialog(JoinType.EQUAL, JoinCardinality.MANY_TO_ONE).getRelationship(0),
         XRelationship.MANY, XRelationship.ONE);
      assertCardinality(
         editInJoinsPane(JoinType.EQUAL, JoinCardinality.MANY_TO_ONE).getRelationship(0),
         XRelationship.MANY, XRelationship.ONE);
   }

   @Test
   void explicitOneToOnePickIsStored() throws Exception {
      XPartition dialog = editInJoinDialog(JoinType.EQUAL, JoinCardinality.ONE_TO_ONE);
      assertCardinality(dialog.getRelationship(0), XRelationship.ONE, XRelationship.ONE);
      assertTrue(trap(dialog).isCheckTrap(), "an explicit pick turns trap checking on");

      assertCardinality(
         editInJoinsPane(JoinType.EQUAL, JoinCardinality.ONE_TO_ONE).getRelationship(0),
         XRelationship.ONE, XRelationship.ONE);
   }

   @Test
   void editOfJoinWithStoredCardinalityIsUnchanged() throws Exception {
      // a normal join (MANY_TO_ONE stored), only the type is changed: cardinality is sent back
      for(boolean pane : new boolean[] { false, true }) {
         XPartition partition = legacyPartition();
         XRelationship stored = partition.getRelationship(0);
         stored.setDependentCardinality(XRelationship.MANY);
         stored.setIndependentCardinality(XRelationship.ONE);

         XPartition result = pane ?
            editInJoinsPane(partition, JoinType.LEFT_OUTER, JoinCardinality.MANY_TO_ONE, true) :
            editInJoinDialog(partition, JoinType.LEFT_OUTER, JoinCardinality.MANY_TO_ONE, true);
         XRelationship rel = result.getRelationship(0);

         assertEquals(XRelationship.LEFT_OUTER, rel.getJoinType());
         assertCardinality(rel, XRelationship.MANY, XRelationship.ONE);
         TrapContext trap = trap(result);
         assertTrue(trap.isCheckTrap());
         assertFalse(trap.check().showWarning());
      }
   }

   private XPartition editInJoinDialog(JoinType type, JoinCardinality cardinality)
      throws Exception
   {
      return editInJoinDialog(legacyPartition(), type, cardinality, false);
   }

   /**
    * Graph join-line dialog: PUT /api/data/physicalmodel/join in the join edit pane, then Done,
    * then the save/load XML round trip.
    */
   private XPartition editInJoinDialog(XPartition partition, JoinType type,
                                       JoinCardinality cardinality, boolean keepCardinality)
      throws Exception
   {
      String origin = runtimePartitionService.createModel(partition, "Orders").getId();
      String pane = runtimePartitionService.openNewRuntimePartition(origin);
      XRelationship rel = runtimePartitionService.getPartition(pane).getRelationship(0);
      JoinModel join = clientJoin(rel);
      join.setType(type);

      if(!keepCardinality) {
         join.setCardinality(cardinality);
      }

      assertEquals(cardinality, join.getCardinality());

      TableDetailJoinInfo info = new TableDetailJoinInfo();
      info.setRuntimeId(pane);
      info.setSourceTable("ORDERS");
      info.setSourceColumn("CUSTOMER_ID");
      info.setTargetTable("CUSTOMERS");
      info.setTargetColumn("CUSTOMER_ID");
      EditJoinEvent event = new EditJoinEvent();
      event.setDetailJoinInfo(info);
      event.setJoinModel(join);

      controller.editJoin(event);
      controller.closeJoinEditPane(origin, pane, true);

      return roundTrip(runtimePartitionService.getPartition(origin));
   }

   private XPartition editInJoinsPane(JoinType type, JoinCardinality cardinality)
      throws Exception
   {
      return editInJoinsPane(legacyPartition(), type, cardinality, false);
   }

   /** Joins pane: PUT /api/data/physicalmodel/join/modify, then the XML round trip. */
   private XPartition editInJoinsPane(XPartition partition, JoinType type,
                                      JoinCardinality cardinality, boolean keepCardinality)
      throws Exception
   {
      String origin = runtimePartitionService.createModel(partition, "Orders").getId();
      XRelationship rel = runtimePartitionService.getPartition(origin).getRelationship(0);
      JoinModel oldJoin = clientJoin(rel);
      JoinModel join = clientJoin(rel);
      join.setType(type);

      if(!keepCardinality) {
         join.setCardinality(cardinality);
      }

      assertEquals(cardinality, join.getCardinality());

      manager.updateJoin(origin, join, oldJoin, "ORDERS");

      return roundTrip(runtimePartitionService.getPartition(origin));
   }

   /** A view saved before cardinality existed: the relationship has no cardinality tags. */
   private static XPartition legacyPartition() throws Exception {
      XPartition p = new XPartition("Order View");
      p.addTable("ORDERS", new Rectangle(0, 0, 50, 50));
      p.addTable("CUSTOMERS", new Rectangle(100, 0, 50, 50));
      p.addRelationship(
         new XRelationship("ORDERS", "CUSTOMER_ID", "CUSTOMERS", "CUSTOMER_ID", "="));

      String xml = toXML(p);
      assertTrue(xml.contains("<cardinality>"));
      return fromXML(xml.replaceAll("<cardinality>.*?</cardinality>", ""));
   }

   private static XPartition roundTrip(XPartition partition) throws Exception {
      return fromXML(toXML(partition));
   }

   private static String toXML(XPartition partition) {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      partition.writeXML(writer);
      writer.flush();
      return out.toString();
   }

   private static XPartition fromXML(String xml) throws Exception {
      Document doc = Tool.parseXML(new StringReader(xml));
      XPartition result = new XPartition();
      result.parseXML(doc.getDocumentElement());
      return result;
   }

   /** What PhysicalModelService sends to the client for a relationship. */
   private static JoinModel clientJoin(XRelationship relationship) {
      JoinModel join = new JoinModel();
      join.setTable(relationship.getDependentTable());
      join.setColumn(relationship.getDependentColumn());
      join.setForeignTable(relationship.getIndependentTable());
      join.setForeignColumn(relationship.getIndependentColumn());
      join.setType(JoinType.forType(relationship.getJoinType()));
      join.setMergingRule(MergingRule.forType(relationship.getMerging()));
      join.setOrderPriority(relationship.getOrder());
      join.setWeak(relationship.isWeakJoin());
      int d = relationship.getDependentCardinality();
      int i = relationship.getIndependentCardinality();

      if(d == XRelationship.ONE && i == XRelationship.ONE) {
         join.setCardinality(JoinCardinality.ONE_TO_ONE);
      }
      else if(d == XRelationship.ONE) {
         join.setCardinality(JoinCardinality.ONE_TO_MANY);
      }
      else if(i == XRelationship.ONE) {
         join.setCardinality(JoinCardinality.MANY_TO_ONE);
      }
      else if(d == 0 && i == 0) {
         join.setCardinality(null);
      }
      else {
         join.setCardinality(JoinCardinality.MANY_TO_MANY);
      }

      return join;
   }

   private static void assertCardinality(XRelationship rel, int dependent, int independent) {
      assertEquals(dependent, rel.getDependentCardinality(), rel::toString);
      assertEquals(independent, rel.getIndependentCardinality(), rel::toString);
   }

   /** The real trap gate and check over a model bound to ORDERS and CUSTOMERS. */
   private static TrapContext trap(XPartition partition) {
      XLogicalModel lm = mock(XLogicalModel.class);
      when(lm.getPartition()).thenReturn("Order View");
      XDataModel xdm = mock(XDataModel.class);
      when(xdm.getPartition(eq("Order View"), any())).thenReturn(partition);

      TrapContext context = new TrapContext(lm);
      context.init(xdm);
      return context;
   }

   private static final class TrapContext extends AbstractModelTrapContext {
      TrapContext(XLogicalModel lm) {
         super(null);
         this.lm = lm;
         this.tables = new String[] { "ORDERS", "CUSTOMERS" };
         this.aggs = new DataRef[0];
      }

      void init(XDataModel xdm) {
         init(null, xdm, false);
      }

      TrapInfo check() {
         return checkTrap();
      }
   }
}
