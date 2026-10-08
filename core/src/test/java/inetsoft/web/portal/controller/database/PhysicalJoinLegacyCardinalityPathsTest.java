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
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.*;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.util.Tool;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.events.EditJoinEvent;
import inetsoft.web.portal.model.database.events.EditJoinsEvent;
import inetsoft.web.portal.model.database.graph.TableDetailJoinInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import javax.cache.Cache;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78072: the join write paths around a legacy physical-view join that has no stored
 * cardinality (0/0), driven with the JSON the server really sends to the client (from
 * PhysicalModelService.createModel) and the request bodies the client sends back.
 * <ul>
 *    <li>type-only, weak-only and order-priority-only edits keep 0/0 through the graph join
 *    dialog (PUT join + Done) and the joins pane (PUT join/modify), also for a join to an
 *    inline view and for a join in an extended physical view, and trap checking stays off;</li>
 *    <li>each explicit cardinality pick is stored on a legacy and on a normal join;</li>
 *    <li>new joins (add join, auto join, graph drag) never end up at 0/0.</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PhysicalJoinLegacyCardinalityPathsTest {
   private static final String VIEW = "V_CUST";
   private final ObjectMapper objectMapper = new ObjectMapper();
   private RuntimePartitionService runtimePartitionService;
   private PhysicalModelService physicalModelService;
   private PhysicalModelManagerService manager;
   private PhysicalModelController controller;
   private PhysicalGraphModelController graphController;
   private DataSourceService dataSourceService;

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

      // no database: no table metadata and no primary keys
      dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.getDefaultMetaDataProvider(any(), any()))
         .thenAnswer(i -> mock(DefaultMetaDataProvider.class));
      XRepository repository = mock(XRepository.class);
      when(repository.getDataModel(any())).thenReturn(mock(XDataModel.class));

      physicalModelService = spy(new PhysicalModelService(
         runtimePartitionService, repository, null, dataSourceService, null));
      doReturn(mock(DefaultMetaDataProvider.class))
         .when(physicalModelService).getMetaDataProvider(anyString());
      // column lists are not needed (an inline view would run its SQL to get them)
      doReturn(new ArrayList<>()).when(physicalModelService).getTableColumns(any(), any(), any());
      manager = new PhysicalModelManagerService(
         dataSourceService, physicalModelService, runtimePartitionService, null, repository,
         null, null, null);
      controller = new PhysicalModelController(
         runtimePartitionService, null, null, null, null, manager, null);
      graphController = new PhysicalGraphModelController(
         runtimePartitionService, physicalModelService, null, manager);
   }

   // ---------------------------------------------------------------- server -> client

   @Test
   void clientJsonOfLegacyJoinHasNoCardinality() throws Exception {
      for(boolean graphView : new boolean[] { false, true }) {
         ObjectNode json = clientJson(legacyPartition(), "ORDERS", "CUSTOMERS", graphView);
         assertTrue(json.has("cardinality"), json::toString);
         assertTrue(json.get("cardinality").isNull(), json::toString);
      }

      XPartition normal = legacyPartition();
      setCardinality(normal.getRelationship(0), XRelationship.MANY, XRelationship.ONE);
      ObjectNode json = clientJson(normal, "ORDERS", "CUSTOMERS", false);
      assertEquals(JoinCardinality.MANY_TO_ONE.toValue(), json.get("cardinality").asInt());
   }

   // ---------------------------------------------------------------- legacy edits

   @Test
   void typeWeakAndOrderOnlyEditsKeepLegacyCardinalityInJoinDialog() throws Exception {
      for(Consumer<ObjectNode> edit : nonCardinalityEdits()) {
         XPartition result = editInJoinDialog(legacyPartition(), "ORDERS", "CUSTOMERS", edit);
         assertLegacyKept(result, "ORDERS", "CUSTOMERS");
      }
   }

   @Test
   void typeWeakAndOrderOnlyEditsKeepLegacyCardinalityInJoinsPane() throws Exception {
      for(Consumer<ObjectNode> edit : nonCardinalityEdits()) {
         XPartition result = editInJoinsPane(legacyPartition(), "ORDERS", "CUSTOMERS", edit);
         assertEquals(1, result.getRelationshipCount());
         assertLegacyKept(result, "ORDERS", "CUSTOMERS");
      }
   }

   @Test
   void editOfLegacyJoinToInlineViewKeepsCardinality() throws Exception {
      Consumer<ObjectNode> leftOuter = j -> j.put("type", JoinType.LEFT_OUTER.toValue());

      XPartition dialog = editInJoinDialog(inlineViewPartition(), "ORDERS", VIEW, leftOuter);
      assertLegacyKept(dialog, "ORDERS", VIEW);
      assertEquals(XRelationship.LEFT_OUTER, find(dialog, "ORDERS", VIEW).getJoinType());

      XPartition pane = editInJoinsPane(inlineViewPartition(), "ORDERS", VIEW, leftOuter);
      assertLegacyKept(pane, "ORDERS", VIEW);
      assertEquals(XRelationship.LEFT_OUTER, find(pane, "ORDERS", VIEW).getJoinType());
   }

   @Test
   void editOfLegacyJoinInExtendedViewKeepsCardinality() throws Exception {
      Consumer<ObjectNode> leftOuter = j -> j.put("type", JoinType.LEFT_OUTER.toValue());

      for(boolean pane : new boolean[] { false, true }) {
         XPartition extended = extendedPartition();
         XPartition base = extended.getBasePartition();
         XPartition result = pane ?
            editInJoinsPane(extended, "CUSTOMERS", "REGIONS", leftOuter) :
            editInJoinDialog(extended, "CUSTOMERS", "REGIONS", leftOuter);

         // the extension's own join is edited and kept at 0/0, the base join untouched
         XRelationship own = find(result, "CUSTOMERS", "REGIONS");
         assertEquals(XRelationship.LEFT_OUTER, own.getJoinType());
         assertCardinality(own, 0, 0);
         assertCardinality(find(base, "ORDERS", "CUSTOMERS"), 0, 0);
         assertEquals(2, result.getRelationshipCount());
         assertFalse(trap(result, "ORDERS", "CUSTOMERS", "REGIONS").isCheckTrap());
      }
   }

   // ---------------------------------------------------------------- explicit picks

   @Test
   void everyExplicitPickIsStoredOnLegacyAndNormalJoins() throws Exception {
      int[][] expected = {
         { XRelationship.ONE, XRelationship.ONE },   // ONE_TO_ONE
         { XRelationship.ONE, XRelationship.MANY },  // ONE_TO_MANY
         { XRelationship.MANY, XRelationship.ONE },  // MANY_TO_ONE
         { XRelationship.MANY, XRelationship.MANY }, // MANY_TO_MANY
      };

      for(JoinCardinality pick : JoinCardinality.values()) {
         int[] want = expected[pick.ordinal()];
         Consumer<ObjectNode> edit = j -> j.put("cardinality", pick.toValue());

         for(boolean legacy : new boolean[] { true, false }) {
            for(boolean pane : new boolean[] { false, true }) {
               XPartition partition = legacyPartition();

               if(!legacy) {
                  setCardinality(partition.getRelationship(0), XRelationship.MANY,
                                 XRelationship.MANY);
               }

               XPartition result = pane ?
                  editInJoinsPane(partition, "ORDERS", "CUSTOMERS", edit) :
                  editInJoinDialog(partition, "ORDERS", "CUSTOMERS", edit);
               String label = pick + (legacy ? " legacy" : " normal") +
                  (pane ? " pane" : " dialog");
               XRelationship rel = find(result, "ORDERS", "CUSTOMERS");
               assertEquals(want[0], rel.getDependentCardinality(), label);
               assertEquals(want[1], rel.getIndependentCardinality(), label);
               assertTrue(trap(result, "ORDERS", "CUSTOMERS").isCheckTrap(), label);
            }
         }
      }
   }

   @Test
   void typeOnlyEditOfNormalJoinsIsUnchanged() throws Exception {
      Consumer<ObjectNode> leftOuter = j -> j.put("type", JoinType.LEFT_OUTER.toValue());
      int[][] stored = {
         { XRelationship.ONE, XRelationship.ONE }, { XRelationship.ONE, XRelationship.MANY },
         { XRelationship.MANY, XRelationship.ONE }, { XRelationship.MANY, XRelationship.MANY }
      };

      for(int[] card : stored) {
         for(boolean pane : new boolean[] { false, true }) {
            XPartition partition = legacyPartition();
            setCardinality(partition.getRelationship(0), card[0], card[1]);
            XPartition result = pane ?
               editInJoinsPane(partition, "ORDERS", "CUSTOMERS", leftOuter) :
               editInJoinDialog(partition, "ORDERS", "CUSTOMERS", leftOuter);
            XRelationship rel = find(result, "ORDERS", "CUSTOMERS");
            assertEquals(XRelationship.LEFT_OUTER, rel.getJoinType());
            assertCardinality(rel, card[0], card[1]);
         }
      }
   }

   // ---------------------------------------------------------------- new joins

   @Test
   void addJoinDialogDefaultIsStored() throws Exception {
      String id = open(twoTables());
      // add-join-dialog.component.ts defaults to MANY_TO_ONE; the cardinality lookup can only
      // replace it with another non-null value
      controller.addJoin(joinsEvent(id, "add", newJoin("ORDERS", "CUSTOMERS"), null));

      assertCardinality(find(stored(id), "ORDERS", "CUSTOMERS"),
                        XRelationship.MANY, XRelationship.ONE);
   }

   @Test
   void autoJoinIsNeverLeftWithoutCardinality() throws Exception {
      // key lookup over metadata without keys
      String id = open(twoTables());
      controller.addAutoJoins(joinsEvent(id, "add", newJoin("ORDERS", "CUSTOMERS"), null),
                              null);
      XRelationship rel = find(stored(id), "ORDERS", "CUSTOMERS");
      assertNotEquals(0, rel.getDependentCardinality(), rel::toString);
      assertNotEquals(0, rel.getIndependentCardinality(), rel::toString);

      // key lookup fails: the dialog default MANY_TO_ONE is kept
      when(dataSourceService.getDataSource(any(), any()))
         .thenThrow(new RuntimeException("no data source"));
      String id2 = open(twoTables());
      controller.addAutoJoins(joinsEvent(id2, "add", newJoin("ORDERS", "CUSTOMERS"), null),
                              null);
      assertCardinality(find(stored(id2), "ORDERS", "CUSTOMERS"),
                        XRelationship.MANY, XRelationship.ONE);
   }

   @Test
   void graphDragJoinIsNeverLeftWithoutCardinality() throws Exception {
      String origin = open(twoTables());
      String pane = graphController.openJoinEditPane(origin);
      graphController.createJoin(
         detail(pane, "ORDERS", "CUSTOMER_ID", "CUSTOMERS", "CUSTOMER_ID"));
      graphController.closeJoinEditPane(origin, pane, true);

      XRelationship rel = find(stored(origin), "ORDERS", "CUSTOMERS");
      assertNotEquals(0, rel.getDependentCardinality(), rel::toString);
      assertNotEquals(0, rel.getIndependentCardinality(), rel::toString);
   }

   // ---------------------------------------------------------------- paths

   /** Graph join-line dialog: PUT /api/data/physicalmodel/join in the pane, then Done. */
   private XPartition editInJoinDialog(XPartition partition, String table, String foreign,
                                       Consumer<ObjectNode> edit) throws Exception
   {
      String origin = open(partition);
      String pane = graphController.openJoinEditPane(origin);
      ObjectNode join = clientJson(runtimePartitionService.getPartition(pane), table, foreign,
                                   true);
      edit.accept(join);

      ObjectNode body = objectMapper.createObjectNode();
      body.set("detailJoinInfo", objectMapper.valueToTree(detail(
         pane, table, join.get("column").asText(), foreign, join.get("foreignColumn").asText())));
      body.set("joinModel", join);
      graphController.editJoin(objectMapper.treeToValue(body, EditJoinEvent.class));
      graphController.closeJoinEditPane(origin, pane, true);

      return stored(origin);
   }

   /** Joins pane: PUT /api/data/physicalmodel/join/modify. */
   private XPartition editInJoinsPane(XPartition partition, String table, String foreign,
                                      Consumer<ObjectNode> edit) throws Exception
   {
      String id = open(partition);
      ObjectNode oldJoin = clientJson(runtimePartitionService.getPartition(id), table, foreign,
                                      false);
      ObjectNode join = oldJoin.deepCopy();
      edit.accept(join);

      // physical-table-joins.component.ts sends nothing for an unchanged join
      if(!oldJoin.equals(join)) {
         controller.modifyJoin(joinsEvent(id, "modify", join, oldJoin));
      }

      return stored(id);
   }

   /** The join JSON the client receives in the physical model (createModel). */
   private ObjectNode clientJson(XPartition partition, String table, String foreign,
                                 boolean graphView) throws Exception
   {
      PhysicalModelDefinition model =
         physicalModelService.createModel("Orders", null, partition, graphView);

      for(PhysicalTableModel tableModel : model.getTables()) {
         for(JoinModel join : tableModel.getJoins()) {
            if(table.equals(join.getTable()) && foreign.equals(join.getForeignTable())) {
               ObjectNode json = objectMapper.valueToTree(join);
               json.remove("relationship");
               return json;
            }
         }
      }

      throw new AssertionError("no join " + table + "->" + foreign);
   }

   private EditJoinsEvent joinsEvent(String id, String action, ObjectNode join,
                                     ObjectNode oldJoin) throws Exception
   {
      ObjectNode item = objectMapper.createObjectNode();
      item.put("actionType", action);
      ObjectNode tableNode = item.putObject("table");
      tableNode.put("qualifiedName", join.get("table").asText());
      tableNode.put("name", join.get("table").asText());
      item.set("join", join);

      if(oldJoin != null) {
         item.set("oldJoin", oldJoin);
      }

      ObjectNode body = objectMapper.createObjectNode();
      body.put("id", id);
      body.putArray("joinItems").add(item);
      return objectMapper.treeToValue(body, EditJoinsEvent.class);
   }

   /** What add-join-dialog and auto-join-tables-dialog send for a new join. */
   private ObjectNode newJoin(String table, String foreign) {
      ObjectNode join = objectMapper.createObjectNode();
      join.put("type", JoinType.EQUAL.toValue());
      join.put("orderPriority", 1);
      join.put("weak", false);
      join.put("mergingRule", MergingRule.AND.toValue());
      join.put("cardinality", JoinCardinality.MANY_TO_ONE.toValue());
      join.put("table", table);
      join.put("column", "CUSTOMER_ID");
      join.put("foreignTable", foreign);
      join.put("foreignColumn", "CUSTOMER_ID");
      join.put("baseJoin", false);
      return join;
   }

   private static List<Consumer<ObjectNode>> nonCardinalityEdits() {
      return List.of(
         j -> j.put("type", JoinType.LEFT_OUTER.toValue()),
         j -> j.put("weak", true),
         j -> j.put("orderPriority", 5));
   }

   private static TableDetailJoinInfo detail(String runtimeId, String table, String column,
                                             String foreign, String foreignColumn)
   {
      TableDetailJoinInfo info = new TableDetailJoinInfo();
      info.setRuntimeId(runtimeId);
      info.setSourceTable(table);
      info.setSourceColumn(column);
      info.setTargetTable(foreign);
      info.setTargetColumn(foreignColumn);
      return info;
   }

   private String open(XPartition partition) {
      return runtimePartitionService.createModel(partition, "Orders").getId();
   }

   /** The stored partition after the save/load XML round trip. */
   private XPartition stored(String runtimeId) throws Exception {
      XPartition partition = runtimePartitionService.getPartition(runtimeId);
      XPartition result = fromXML(toXML(partition));

      if(partition.getBasePartition() != null) {
         result.setBaseParitition(fromXML(toXML(partition.getBasePartition())));
      }

      return result;
   }

   // ---------------------------------------------------------------- fixtures

   private static XPartition twoTables() {
      XPartition p = new XPartition("Order View");
      p.addTable("ORDERS", new Rectangle(0, 0, 50, 50));
      p.addTable("CUSTOMERS", new Rectangle(100, 0, 50, 50));
      return p;
   }

   /** A view saved before cardinality existed: no cardinality tags. */
   private static XPartition legacyPartition() throws Exception {
      XPartition p = twoTables();
      p.addRelationship(
         new XRelationship("ORDERS", "CUSTOMER_ID", "CUSTOMERS", "CUSTOMER_ID", "="));
      return legacy(p);
   }

   private static XPartition inlineViewPartition() throws Exception {
      XPartition p = new XPartition("Order View");
      p.addTable("ORDERS", new Rectangle(0, 0, 50, 50));
      p.addTable(VIEW, PartitionTable.VIEW, "select CUSTOMER_ID from CUSTOMERS",
                 new Rectangle(100, 0, 50, 50), null, null);
      p.addRelationship(new XRelationship("ORDERS", "CUSTOMER_ID", VIEW, "CUSTOMER_ID", "="));
      return legacy(p);
   }

   /** Base ORDERS-CUSTOMERS (0/0), extension adds REGIONS with CUSTOMERS-REGIONS (0/0). */
   private static XPartition extendedPartition() throws Exception {
      XPartition base = legacyPartition();
      XPartition ext = new XPartition("Region Extension");
      ext.addTable("REGIONS", new Rectangle(200, 0, 50, 50));
      ext.addRelationship(
         new XRelationship("CUSTOMERS", "REGION_ID", "REGIONS", "REGION_ID", "="));
      ext = legacy(ext);
      ext.setBaseParitition(base);
      return ext;
   }

   private static XPartition legacy(XPartition p) throws Exception {
      String xml = toXML(p);
      assertTrue(xml.contains("<cardinality>"));
      XPartition result = fromXML(xml.replaceAll("<cardinality>.*?</cardinality>", ""));

      for(int i = 0; i < result.getRelationshipCount(); i++) {
         assertCardinality(result.getRelationship(i), 0, 0);
      }

      return result;
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

   private static XRelationship find(XPartition partition, String table, String foreign) {
      for(int i = 0; i < partition.getRelationshipCount(); i++) {
         XRelationship rel = partition.getRelationship(i);

         if(table.equals(rel.getDependentTable()) && foreign.equals(rel.getIndependentTable())) {
            return rel;
         }
      }

      throw new AssertionError("no relationship " + table + "->" + foreign);
   }

   private static void setCardinality(XRelationship rel, int dependent, int independent) {
      rel.setDependentCardinality(dependent);
      rel.setIndependentCardinality(independent);
   }

   private static void assertCardinality(XRelationship rel, int dependent, int independent) {
      assertEquals(dependent, rel.getDependentCardinality(), rel::toString);
      assertEquals(independent, rel.getIndependentCardinality(), rel::toString);
   }

   private static void assertLegacyKept(XPartition result, String table, String foreign) {
      assertCardinality(find(result, table, foreign), 0, 0);
      TrapContext trap = trap(result, table, foreign);
      assertFalse(trap.isCheckTrap());
      assertFalse(trap.check().showWarning());
   }

   /** The real trap gate and check over a model bound to the given tables. */
   private static TrapContext trap(XPartition partition, String... tables) {
      XLogicalModel lm = mock(XLogicalModel.class);
      when(lm.getPartition()).thenReturn("Order View");
      XDataModel xdm = mock(XDataModel.class);
      when(xdm.getPartition(eq("Order View"), any())).thenReturn(partition);

      TrapContext context = new TrapContext(lm, tables);
      context.init(xdm);
      return context;
   }

   private static final class TrapContext extends AbstractModelTrapContext {
      TrapContext(XLogicalModel lm, String[] tables) {
         super(null);
         this.lm = lm;
         this.tables = tables;
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
