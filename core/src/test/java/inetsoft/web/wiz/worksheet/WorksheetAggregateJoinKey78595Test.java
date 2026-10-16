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
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78595: once a join keys on an aggregate OUTPUT of a table, an edit that keeps that output
 * (same source column, same alias) must be accepted; row-level to aggregated, removal, re-alias
 * and a type change of the key stay refused.
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetAggregateJoinKey78595Test {
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
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      box.setActive(true);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      JoinSession s = new JoinSession("TOK", "Worksheet/ws1", "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(
         eq(SheetType.WORKSHEET), eq("Worksheet/ws1"), eq(agent))).thenReturn(rws);
      return new WorksheetEditService(sessions, runtimeAccess, broadcast, securityEngine,
                                      mock(InnerJoinService.class));
   }

   private static EmbeddedTableAssembly cust(Worksheet ws) {
      EmbeddedTableAssembly t =
         TestWorksheets.tableWithColumns(ws, "CUST", "STATE", "COMPANY_NAME", "CUSTOMER_ID");
      t.setEmbeddedData(new XEmbeddedTable(
         new String[]{ "string", "string", "integer" },
         new Object[][]{
            { "STATE", "COMPANY_NAME", "CUSTOMER_ID" },
            { "NJ", "A", 1 }, { "NJ", "B", 2 }, { "NY", "C", 3 }
         }));
      ((ColumnRef) t.getColumnSelection(false).getAttribute("CUSTOMER_ID"))
         .setDataType(XSchema.INTEGER);
      return t;
   }

   // Embedded data is required: the sandbox refresh rebuilds the columns of a dataless table.
   private static EmbeddedTableAssembly orders(
      Worksheet ws, String[] names, String[] types, Object[] row)
   {
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "ORDERS", names);
      t.setEmbeddedData(new XEmbeddedTable(types, new Object[][]{ names, row }));

      for(int i = 0; i < names.length; i++) {
         ((ColumnRef) t.getColumnSelection(false).getAttribute(names[i])).setDataType(types[i]);
      }

      return t;
   }

   private static List<WorksheetMutationSupport.GroupSpec> state() {
      return List.of(new WorksheetMutationSupport.GroupSpec("STATE"));
   }

   private static WorksheetMutationSupport.AggregateSpec agg(
      String field, String formula, String alias)
   {
      return new WorksheetMutationSupport.AggregateSpec(field, formula, alias);
   }

   private void aggregate(WorksheetEditService svc, List<WorksheetMutationSupport.GroupSpec> g,
                          List<WorksheetMutationSupport.AggregateSpec> a) throws Exception
   {
      svc.apply("TOK", agent, ed -> ed.setGroupAggregate("CUST", g, a));
   }

   /**
    * CUST(STATE, COMPANY_NAME, CUSTOMER_ID) grouped by STATE, Count(COMPANY_NAME) AS N_CUST, and
    * J = CUST.N_CUST (double) join ORDERS.CUSTOMER_ID (integer).
    */
   private WorksheetEditService joinedOnAggregate(Worksheet ws) throws Exception {
      WorksheetEditService svc = service(ws);
      ws.addAssembly(cust(ws));
      ws.addAssembly(orders(ws, new String[]{ "CUSTOMER_ID", "STATE" },
                            new String[]{ "integer", "string" }, new Object[]{ 1, "NJ" }));
      aggregate(svc, state(), List.of(agg("COMPANY_NAME", "COUNT", "N_CUST")));
      svc.apply("TOK", agent, ed -> ed.addJoin(
         "J", "CUST", "N_CUST", "ORDERS", "CUSTOMER_ID", "INNER", null, null));
      return svc;
   }

   // aggregate N_CUST (Count) -> the identical aggregate: accepted
   @Test
   void identicalResendKeepingTheAggregateKeyIsAccepted() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = joinedOnAggregate(ws);

      aggregate(svc, state(), List.of(agg("COMPANY_NAME", "COUNT", "N_CUST")));

      assertNotNull(ws.getAssembly("J"));
      assertNotNull(((TableAssembly) ws.getAssembly("CUST"))
         .getColumnSelection(true).getAttribute("N_CUST"));
   }

   // aggregate N_CUST -> same aggregate plus another aggregate: accepted
   @Test
   void addingAnotherAggregateKeepsTheAggregateKey() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = joinedOnAggregate(ws);

      aggregate(svc, state(), List.of(agg("COMPANY_NAME", "COUNT", "N_CUST"),
                                      agg("CUSTOMER_ID", "MAX", "MAXID")));

      assertNotNull(ws.getAssembly("J"));
      assertNotNull(((TableAssembly) ws.getAssembly("CUST"))
         .getColumnSelection(true).getAttribute("MAXID"));
   }

   // aggregate N_CUST double -> Max(COMPANY_NAME) string, against an integer key: refused as a
   // TYPE change, not with the row-level wording
   @Test
   void aggregateKeyChangingTypeIsRefusedNamingTheTypeChange() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = joinedOnAggregate(ws);

      PairingException ex = assertThrows(PairingException.class,
         () -> aggregate(svc, state(), List.of(agg("COMPANY_NAME", "MAX", "N_CUST"))));

      assertTrue(ex.getMessage().contains("This aggregate would change the type of"),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("double to string"), ex.getMessage());
      assertFalse(ex.getMessage().contains("plain row-level column"), ex.getMessage());
   }

   // aggregate N_CUST -> removed: still refused
   @Test
   void removingTheAggregateKeyIsStillRefused() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = joinedOnAggregate(ws);

      PairingException ex = assertThrows(PairingException.class,
         () -> aggregate(svc, state(), List.of(agg("CUSTOMER_ID", "MAX", "MAXID"))));

      assertTrue(ex.getMessage().contains("still used as a join key"), ex.getMessage());
   }

   // aggregate N_CUST -> re-aliased N2: still refused
   @Test
   void reAliasingTheAggregateKeyIsStillRefused() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = joinedOnAggregate(ws);

      PairingException ex = assertThrows(PairingException.class,
         () -> aggregate(svc, state(), List.of(agg("COMPANY_NAME", "COUNT", "N2"))));

      assertTrue(ex.getMessage().contains("still used as a join key"), ex.getMessage());
   }

   // aggregate N_CUST -> same alias re-pointed at a different source column: still refused
   @Test
   void rePointingTheAggregateKeyToAnotherSourceColumnIsStillRefused() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = joinedOnAggregate(ws);

      PairingException ex = assertThrows(PairingException.class,
         () -> aggregate(svc, state(), List.of(agg("CUSTOMER_ID", "COUNT", "N_CUST"))));

      assertTrue(ex.getMessage().contains("still used as a join key"), ex.getMessage());
   }

   // row-level group key COMPANY_NAME -> aggregated under the same name: still refused
   @Test
   void rowLevelKeyBecomingAnAggregateIsStillRefused() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      ws.addAssembly(cust(ws));
      ws.addAssembly(orders(ws, new String[]{ "NAME" }, new String[]{ "string" },
                            new Object[]{ "A" }));
      aggregate(svc, List.of(new WorksheetMutationSupport.GroupSpec("STATE"),
                             new WorksheetMutationSupport.GroupSpec("COMPANY_NAME")),
                List.of(agg("CUSTOMER_ID", "MAX", "MAXID")));
      svc.apply("TOK", agent, ed -> ed.addJoin(
         "J", "CUST", "COMPANY_NAME", "ORDERS", "NAME", "INNER", null, null));

      PairingException ex = assertThrows(PairingException.class,
         () -> aggregate(svc, state(), List.of(agg("COMPANY_NAME", "COUNT", null))));

      assertTrue(ex.getMessage().contains("still used as a join key"), ex.getMessage());
   }
}
