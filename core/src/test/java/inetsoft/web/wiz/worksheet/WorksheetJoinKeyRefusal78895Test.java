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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.*;
import inetsoft.util.Catalog;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78895: remove_column, rename_column and set_column_visibility(visible:false) on a column a
 * join keys on are refused (unchanged) with an error that names the join, as edit_sql_query /
 * edit_table / edit_date_range_column do, not the legacy "Some connectors depend on this column".
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetJoinKeyRefusal78895Test {
   private final Principal agent = TestPrincipals.user("alice", "host-org");

   private WorksheetEditService service(Worksheet ws) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      JoinSession s = new JoinSession("TOK", "Worksheet/ws1", "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(
         eq(SheetType.WORKSHEET), eq("Worksheet/ws1"), eq(agent))).thenReturn(rws);
      return new WorksheetEditService(sessions, runtimeAccess, broadcast, securityEngine,
                                      new InnerJoinService(null, null));
   }

   /** ORDERS + CUSTOMERS joined as JoinKeyRm on ORDERS.CUSTOMER_ID = CUSTOMERS.CUSTOMER_ID. */
   private TableAssembly ordersJoined(Worksheet ws, WorksheetEditService svc) throws Exception {
      EmbeddedTableAssembly orders =
         TestWorksheets.tableWithColumns(ws, "ORDERS", "ORDER_ID", "CUSTOMER_ID");
      EmbeddedTableAssembly customers =
         TestWorksheets.tableWithColumns(ws, "CUSTOMERS", "CUSTOMER_ID", "COMPANY_NAME");
      ws.addAssembly(orders);
      ws.addAssembly(customers);
      svc.apply("TOK", agent, ed -> ed.addJoin(
         "JoinKeyRm", "ORDERS", "CUSTOMER_ID", "CUSTOMERS", "CUSTOMER_ID", "INNER", null, null));
      return orders;
   }

   private static void assertNamesTheJoin(PairingException ex, String join) {
      assertTrue(ex.getMessage().contains("'" + join + "' (join key on \"CUSTOMER_ID\")"),
                 ex.getMessage());
      assertFalse(ex.getMessage().contains(
         Catalog.getCatalog().getString("common.columnDependency", "CUSTOMER_ID")),
                  ex.getMessage());
      assertTrue(ex.getMessage().endsWith("Nothing was changed."), ex.getMessage());
   }

   private static void assertKeyUnchanged(TableAssembly orders) {
      ColumnRef key = (ColumnRef) orders.getColumnSelection(false).getAttribute("CUSTOMER_ID");
      assertNotNull(key, "the join key must survive the refusal");
      assertNull(key.getAlias(), "the join key must not be renamed");
      assertTrue(key.isVisible(), "the join key must stay visible");
   }

   @Test
   void removeColumnOnAJoinKeyIsRefusedNamingTheJoin() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly orders = ordersJoined(ws, svc);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.removeColumn("ORDERS", "CUSTOMER_ID", false)));

      assertTrue(ex.getMessage().startsWith(
         "Cannot remove column \"CUSTOMER_ID\" from table \"ORDERS\""), ex.getMessage());
      assertNamesTheJoin(ex, "JoinKeyRm");
      assertKeyUnchanged(orders);
   }

   @Test
   void renameColumnOnAJoinKeyIsRefusedNamingTheJoin() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly orders = ordersJoined(ws, svc);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.renameColumn("ORDERS", "CUSTOMER_ID", "CUST_ID")));

      assertTrue(ex.getMessage().startsWith(
         "Cannot rename column \"CUSTOMER_ID\" of table \"ORDERS\" to \"CUST_ID\""),
                 ex.getMessage());
      assertNamesTheJoin(ex, "JoinKeyRm");
      assertKeyUnchanged(orders);
   }

   @Test
   void hidingAJoinKeyIsRefusedNamingTheJoin() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly orders = ordersJoined(ws, svc);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent,
                         ed -> ed.setColumnVisibility("ORDERS", "CUSTOMER_ID", false, false)));

      assertTrue(ex.getMessage().startsWith(
         "Cannot hide column \"CUSTOMER_ID\" of table \"ORDERS\""), ex.getMessage());
      assertNamesTheJoin(ex, "JoinKeyRm");
      assertKeyUnchanged(orders);
   }

   /** The join is keyed on a mirror of ORDERS: the same walk still refuses and names it. */
   @Test
   void removingAKeyAJoinUsesThroughAMirrorIsRefusedNamingTheJoin() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      EmbeddedTableAssembly orders =
         TestWorksheets.tableWithColumns(ws, "ORDERS", "ORDER_ID", "CUSTOMER_ID");
      EmbeddedTableAssembly customers =
         TestWorksheets.tableWithColumns(ws, "CUSTOMERS", "CUSTOMER_ID", "COMPANY_NAME");
      ws.addAssembly(orders);
      ws.addAssembly(customers);
      svc.apply("TOK", agent, ed -> {
         ed.addMirror("M", "ORDERS");
         ed.addJoin("JoinKeyM", "M", "CUSTOMER_ID", "CUSTOMERS", "CUSTOMER_ID", "INNER",
                    null, null);
      });

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.removeColumn("ORDERS", "CUSTOMER_ID", false)));

      assertNamesTheJoin(ex, "JoinKeyM");
      assertKeyUnchanged(orders);
   }

   @Test
   void aColumnNoJoinKeysOnCanStillBeRemovedRenamedAndHidden() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly orders = ordersJoined(ws, svc);
      EmbeddedTableAssembly extra =
         TestWorksheets.tableWithColumns(ws, "EXTRA", "A", "B", "C");
      ws.addAssembly(extra);

      svc.apply("TOK", agent, ed -> {
         ed.setColumnVisibility("ORDERS", "ORDER_ID", false, false);
         ed.removeColumn("EXTRA", "A", false);
         ed.renameColumn("EXTRA", "B", "B2");
      });

      assertFalse(((ColumnRef) orders.getColumnSelection(false).getAttribute("ORDER_ID"))
                     .isVisible());
      assertNull(extra.getColumnSelection(false).getAttribute("A"));
      assertNotNull(WorksheetMutationSupport.resolveFieldOrNull(extra, "B2", false));
   }
}
