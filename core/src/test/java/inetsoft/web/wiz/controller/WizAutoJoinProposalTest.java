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

import inetsoft.web.wiz.controller.WizDataModelErrors.DmFailure;
import inetsoft.web.wiz.controller.WizPhysicalViewMapper.JoinDef;
import inetsoft.web.wiz.controller.WizPhysicalViewWire.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The join assembly of the Auto Join Tables function: the part of the Portal dialog that turns the
 * backend's candidate columns into joins.
 */
@Tag("core")
class WizAutoJoinProposalTest {
   private static final List<String> TABLES = List.of("CUSTOMERS", "ORDERS", "ORDER_DETAILS");

   @Test
   void nameModeProposesEveryPairOfTheTablesThatShareAColumnInViewOrder() {
      // the server hands the tables of a column in hash order; the proposal fixes the order
      List<NameColumn> names = List.of(
         new NameColumn("ORDER_ID", List.of("ORDER_DETAILS", "ORDERS")),
         new NameColumn("CUSTOMER_ID", List.of("ORDERS", "CUSTOMERS")));
      AutoJoinResponse response = WizAutoJoinProposal.propose(
         names, List.of(), false, TABLES, List.of(), null, null);

      assertEquals("name", response.mode());
      assertFalse(response.metaAvailable());
      assertEquals(List.of(
         new ProposedJoin("CUSTOMERS", "CUSTOMER_ID", "ORDERS", "CUSTOMER_ID", "EQUAL"),
         new ProposedJoin("ORDERS", "ORDER_ID", "ORDER_DETAILS", "ORDER_ID", "EQUAL")),
                   response.joins());
      assertEquals(List.of("CUSTOMER_ID", "ORDER_ID"),
                   response.nameColumns().stream().map(NameColumn::column).toList());
      assertTrue(response.skipped().isEmpty());
   }

   @Test
   void aColumnSharedByThreeTablesGivesTheCliqueAndAnAdvice() {
      AutoJoinResponse response = WizAutoJoinProposal.propose(
         List.of(new NameColumn("ID", List.of("ORDERS", "CUSTOMERS", "ORDER_DETAILS"))),
         List.of(), false, TABLES, List.of(), null, null);

      assertEquals(3, response.joins().size());
      assertEquals(1, response.notes().size());
      assertTrue(response.notes().get(0).contains("3 tables share column \"ID\""),
                 response.notes().get(0));
   }

   @Test
   void metadataIsTheDefaultWhenAvailableAndFollowsEveryKeyReference() {
      List<MetaColumn> metas = List.of(
         new MetaColumn("ORDERS", "CUSTOMER_ID",
                        List.of(new ColumnRef("CUSTOMERS", "CUSTOMER_ID"))),
         new MetaColumn("ORDER_DETAILS", "ORDER_ID",
                        List.of(new ColumnRef("ORDERS", "ORDER_ID"))));
      AutoJoinResponse response = WizAutoJoinProposal.propose(
         List.of(new NameColumn("ORDER_ID", List.of("ORDERS", "ORDER_DETAILS"))), metas, true,
         TABLES, List.of(), null, null);

      assertEquals("metadata", response.mode());
      assertTrue(response.metaAvailable());
      // candidates are ordered by table, then column: "ORDERS" sorts before "ORDER_DETAILS"
      assertEquals(List.of(
         new ProposedJoin("ORDERS", "CUSTOMER_ID", "CUSTOMERS", "CUSTOMER_ID", "EQUAL"),
         new ProposedJoin("ORDER_DETAILS", "ORDER_ID", "ORDERS", "ORDER_ID", "EQUAL")),
                   response.joins());
   }

   @Test
   void explicitNameModeWinsOverTheMetadataDefault() {
      AutoJoinResponse response = WizAutoJoinProposal.propose(
         List.of(new NameColumn("ORDER_ID", List.of("ORDERS", "ORDER_DETAILS"))),
         List.of(new MetaColumn("ORDERS", "CUSTOMER_ID",
                                List.of(new ColumnRef("CUSTOMERS", "CUSTOMER_ID")))),
         true, TABLES, List.of(), " Name ", null);

      assertEquals("name", response.mode());
      assertEquals(1, response.joins().size());
   }

   @Test
   void aPairAlreadyJoinedInEitherDirectionIsSkippedAndListed() {
      List<JoinDef> existing = List.of(join("ORDER_DETAILS", "ORDER_ID", "ORDERS", "ORDER_ID"));
      List<NameColumn> names =
         List.of(new NameColumn("ORDER_ID", List.of("ORDERS", "ORDER_DETAILS")));
      AutoJoinResponse byName = WizAutoJoinProposal.propose(
         names, List.of(), false, TABLES, existing, null, null);
      AutoJoinResponse byMeta = WizAutoJoinProposal.propose(
         List.of(), List.of(new MetaColumn("ORDERS", "ORDER_ID",
                                           List.of(new ColumnRef("ORDER_DETAILS", "ORDER_ID")))),
         true, TABLES, existing, null, null);

      for(AutoJoinResponse response : List.of(byName, byMeta)) {
         assertTrue(response.joins().isEmpty(), response.toString());
         assertEquals(1, response.skipped().size());
         assertEquals("ALREADY_JOINED", response.skipped().get(0).reason());
      }
   }

   @Test
   void aKeyThatPointsAtItselfOrAtATableOutsideTheViewIsSkippedWithItsReason() {
      List<MetaColumn> metas = List.of(
         new MetaColumn("ORDERS", "PARENT_ID", List.of(new ColumnRef("ORDERS", "ORDER_ID"))),
         new MetaColumn("ORDERS", "REGION_ID", List.of(new ColumnRef("OTHER.REGIONS", "ID"))));
      AutoJoinResponse response = WizAutoJoinProposal.propose(
         List.of(), metas, true, TABLES, List.of(), "metadata", null);

      assertTrue(response.joins().isEmpty());
      assertEquals(List.of("SELF_JOIN", "TABLE_NOT_IN_VIEW"),
                   response.skipped().stream().map(SkippedJoin::reason).toList());
      assertEquals("OTHER.REGIONS", response.skipped().get(1).table2());
   }

   @Test
   void twoKeysDescribingTheSameJoinGiveOneJoin() {
      List<MetaColumn> metas = List.of(
         new MetaColumn("ORDERS", "ORDER_ID", List.of(new ColumnRef("ORDER_DETAILS", "ORDER_ID"))),
         new MetaColumn("ORDER_DETAILS", "ORDER_ID", List.of(new ColumnRef("ORDERS", "ORDER_ID"))));
      AutoJoinResponse response = WizAutoJoinProposal.propose(
         List.of(), metas, true, TABLES, List.of(), null, null);

      assertEquals(1, response.joins().size());
      assertTrue(response.skipped().isEmpty());
   }

   @Test
   void theColumnsAskedForAreTheOnlyOnesJoined() {
      List<NameColumn> names = List.of(
         new NameColumn("ORDER_ID", List.of("ORDERS", "ORDER_DETAILS")),
         new NameColumn("CUSTOMER_ID", List.of("ORDERS", "CUSTOMERS")));
      AutoJoinResponse response = WizAutoJoinProposal.propose(
         names, List.of(), false, TABLES, List.of(), "name", List.of("CUSTOMER_ID"));

      assertEquals(1, response.joins().size());
      assertEquals("CUSTOMER_ID", response.joins().get(0).fromColumn());
      // the candidate lists still show everything the caller could have picked
      assertEquals(2, response.nameColumns().size());
   }

   @Test
   void aColumnThatIsNoCandidateFailsNamingTheCandidates() {
      DmFailure failure = assertThrows(DmFailure.class, () -> WizAutoJoinProposal.propose(
         List.of(new NameColumn("ORDER_ID", List.of("ORDERS", "ORDER_DETAILS"))), List.of(),
         false, TABLES, List.of(), "name", List.of("order_id")));

      assertEquals(WizDataModelWire.INVALID, failure.reason());
      assertEquals("columns", failure.field());
      assertTrue(failure.getMessage().contains("[order_id]"), failure.getMessage());
      assertTrue(failure.getMessage().contains("candidates: [ORDER_ID]"), failure.getMessage());
   }

   @Test
   void metadataModeWithoutKeyMetadataAndAnUnknownModeFailLoud() {
      DmFailure unavailable = assertThrows(DmFailure.class, () -> WizAutoJoinProposal.propose(
         List.of(), List.of(), false, TABLES, List.of(), "metadata", null));
      DmFailure unknown = assertThrows(DmFailure.class, () -> WizAutoJoinProposal.propose(
         List.of(), List.of(), true, TABLES, List.of(), "fk", null));

      assertEquals("mode", unavailable.field());
      assertTrue(unavailable.getMessage().contains("use mode name"), unavailable.getMessage());
      assertEquals("mode", unknown.field());
      assertTrue(unknown.getMessage().contains("name, metadata"), unknown.getMessage());
   }

   @Test
   void noCandidatesGivesAnEmptyProposalAndANoteNotAnError() {
      AutoJoinResponse response = WizAutoJoinProposal.propose(
         List.of(), List.of(), false, TABLES, List.of(), null, null);

      assertTrue(response.joins().isEmpty());
      assertEquals(1, response.notes().size());
      assertTrue(response.notes().get(0).contains("no candidate columns"));
   }

   @Test
   void theWireFieldNamesAreThePluginContract() {
      // plugin/composer/src/tools/dataModel/common/bridgeTypes.ts and test/dataModel/bridgeWire.test.ts
      Map<Class<?>, String> wire = new LinkedHashMap<>();
      wire.put(AutoJoinRequest.class, "database,name,parent,tables,connection,mode,columns");
      wire.put(ColumnRef.class, "table,column");
      wire.put(NameColumn.class, "column,tables");
      wire.put(MetaColumn.class, "table,column,references");
      wire.put(ProposedJoin.class, "fromTable,fromColumn,toTable,toColumn,type");
      wire.put(SkippedJoin.class, "reason,table1,column1,table2,column2");
      wire.put(AutoJoinResponse.class,
               "ok,mode,metaAvailable,nameColumns,metaColumns,joins,skipped,notes");

      for(Map.Entry<Class<?>, String> entry : wire.entrySet()) {
         List<String> actual = new ArrayList<>();

         for(RecordComponent component : entry.getKey().getRecordComponents()) {
            actual.add(component.getName());
         }

         assertEquals(entry.getValue(), String.join(",", actual), entry.getKey().getSimpleName());
      }
   }

   private static JoinDef join(String fromTable, String fromColumn, String toTable,
                               String toColumn)
   {
      return new JoinDef(fromTable, fromColumn, toTable, toColumn, "EQUAL", "and", false, 1, null,
                         false);
   }
}
