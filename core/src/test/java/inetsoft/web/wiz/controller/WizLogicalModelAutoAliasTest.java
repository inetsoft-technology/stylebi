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

import inetsoft.uql.erm.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.web.portal.model.database.LogicalModelDefinition;
import inetsoft.web.wiz.controller.WizDataModelWire.Err;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82273: an attribute bound to a table that an Auto Alias replaced used to be reported as
 * "does not exist in datasource" (Portal) or "is not in the database" (bridge). Both validators
 * now say that an Auto Alias replaced the table and name the aliases; tables that are really
 * missing keep their old messages. Runs the real {@code LogicalModelService} and bridge
 * validator over a view with an Auto Alias, with metadata named after the applied aliases the
 * way {@code DefaultMetaDataProvider} reports it.
 */
class WizLogicalModelAutoAliasTest extends WizLogicalModelTestBase {
   private static final String VIEW = "pva";
   private XPartition applied;

   /**
    * film and film2 join language (the Auto Alias key; the first join keeps outgoing links, the
    * second does not); language - store, customer - store, cust2 (an alias of customer) - store.
    */
   @BeforeEach
   void setUpAutoAliasView() {
      XPartition view = new XPartition(VIEW);

      for(String t : new String[] { "film", "film2", "language", "store", "customer", "cust2" }) {
         view.addTable(t, new Rectangle(0, 0, 10, 10));
      }

      view.setAliasTable("cust2", "customer");
      view.addRelationship(new XRelationship("film", "l", "language", "l", "="));
      view.addRelationship(new XRelationship("film2", "l", "language", "l", "="));
      view.addRelationship(new XRelationship("language", "s", "store", "s", "="));
      view.addRelationship(new XRelationship("customer", "s", "store", "s", "="));
      view.addRelationship(new XRelationship("cust2", "s", "store", "s", "="));

      AutoAlias auto = new AutoAlias();
      auto.addIncomingJoin(join("film", "test1", true));
      auto.addIncomingJoin(join("film2", "test2", false));
      view.setAutoAlias("language", auto);
      dataModel.addPartition(view);
      DataSourceRegistry.getRegistry().clearCache();
      applied = view.applyAutoAliases();

      // the database metadata of an Auto Alias view is named after the applied aliases
      physical.clear();
      Enumeration<XPartition.PartitionTable> tables = applied.getTables();

      while(tables.hasMoreElements()) {
         physical.put(tables.nextElement().getName(),
                      new LinkedHashMap<>(Map.of("ID", "integer")));
      }
   }

   private static AutoAlias.IncomingJoin join(String source, String alias, boolean keep) {
      AutoAlias.IncomingJoin join = new AutoAlias.IncomingJoin();
      join.setSourceTable(source);
      join.setAlias(alias);
      join.setKeepOutgoing(keep);
      return join;
   }

   private String portalMessage(String table) throws Exception {
      String model = "m_" + table;
      XLogicalModel stored = new XLogicalModel(model);
      stored.setPartition(VIEW);
      XEntity entity = new XEntity("E");
      entity.addAttribute(new XAttribute("A", table, "ID", "string"));
      stored.addEntity(entity);
      dataModel.addLogicalModel(stored);

      LogicalModelDefinition read = modelService.getModel(ds, VIEW, model, null, bob);
      return read.getEntities().get(0).getAttributes().get(0).getErrorMessage();
   }

   private Err bridgeError(String table) {
      WizLogicalModelValidator.Result result = new WizLogicalModelValidator().validate(
         new WizLogicalModelEnv(ds, dataModel, dataSourceService),
         withView(def("M", entity("E", col("A", table, "ID"))), VIEW),
         WizLogicalModelValidator.Mode.CREATE, null);

      return result.errors().isEmpty() ? null : result.errors().get(0);
   }

   private String replacement(String table) {
      return String.join(", ", dataModel.getPartition(VIEW)
         .getAutoAliasReplacements(applied, table));
   }

   @Test
   void premiseTheOriginalsAreNotInTheMetadata() {
      for(String t : new String[] { "language", "store", "customer", "cust2" }) {
         assertFalse(physical.containsKey(t), t);
         assertFalse(replacement(t).isEmpty(), t);
      }
   }

   @Test
   void portalNamesTheAliasesOfTheKeyTable() throws Exception {
      String message = portalMessage("language");

      assertNotNull(message);
      assertTrue(message.contains("[test1, test2]"), message);
      assertTrue(message.contains("Auto Alias"), message);
      assertFalse(message.contains("does not exist in datasource"), message);
   }

   @Test
   void portalNamesTheAliasOfAnOutgoingPhysicalTable() throws Exception {
      String message = portalMessage("customer");

      assertNotNull(message);
      assertTrue(message.contains("[" + replacement("customer") + "]"), message);
      assertFalse(message.contains("does not exist"), message);
   }

   @Test
   void portalNamesTheAliasOfAnOutgoingAliasTableInsteadOfSayingItIsNotInThePhysicalModel()
      throws Exception
   {
      String message = portalMessage("cust2");

      assertNotNull(message);
      assertTrue(message.contains("[" + replacement("cust2") + "]"), message);
      assertFalse(message.contains("does not exist"), message);
   }

   @Test
   void portalKeepsItsOldMessagesForTablesThatAreReallyMissing() throws Exception {
      String notInView = portalMessage("nosuch");
      assertNotNull(notInView);
      assertTrue(notInView.contains("does not exist in the physical model"), notInView);

      physical.remove("film");
      String notInDatabase = portalMessage("film");
      assertNotNull(notInDatabase);
      assertTrue(notInDatabase.contains("does not exist in datasource"), notInDatabase);
   }

   @Test
   void portalLeavesATableThatIsStillInTheViewAlone() throws Exception {
      assertNull(portalMessage("film"));
   }

   @Test
   void bridgeNamesTheAliasesOfTheKeyTableAndKeepsTheCode() {
      Err err = bridgeError("language");

      assertNotNull(err);
      assertEquals("TABLE_NOT_IN_VIEW", err.code());
      assertEquals("entities[E].attributes[A].table", err.field());
      assertTrue(err.message().contains("\"test1\", \"test2\""), err.message());
      assertTrue(err.message().contains("Auto Alias"), err.message());
      assertFalse(err.message().contains("not in the database"), err.message());
   }

   @Test
   void bridgeNamesTheAliasOfAnOutgoingPhysicalAndAnOutgoingAliasTable() {
      for(String table : new String[] { "customer", "cust2" }) {
         Err err = bridgeError(table);

         assertNotNull(err, table);
         assertEquals("TABLE_NOT_IN_VIEW", err.code());
         assertTrue(err.message().contains("\"" + replacement(table) + "\""), err.message());
         assertTrue(err.message().contains("Auto Alias"), err.message());
      }
   }

   @Test
   void bridgeKeepsItsOldMessagesForTablesThatAreReallyMissing() {
      Err notInView = bridgeError("nosuch");
      assertNotNull(notInView);
      assertEquals("TABLE_NOT_IN_VIEW", notInView.code());
      assertTrue(notInView.message().contains("is not in view"), notInView.message());

      physical.remove("film");
      Err notInDatabase = bridgeError("film");
      assertNotNull(notInDatabase);
      assertEquals("TABLE_NOT_IN_VIEW", notInDatabase.code());
      assertTrue(notInDatabase.message().contains("is not in the database"),
                 notInDatabase.message());
   }

   @Test
   void bridgeAcceptsATableThatIsStillInTheView() {
      assertNull(bridgeError("film"));
   }
}
