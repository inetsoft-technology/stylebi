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
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.CompositeTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Catalog;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79594: edit_table refuses dropping a column a downstream mirror groups by or aggregates,
 * naming the mirror, through a chain of mirrors too. edit_sql_query keeps its documented
 * accept-and-warn for the same case (WSQ-009).
 */
@Tag("core")
@WizAgentTestSupport
class TabularEditAggregateLoss79594Test {
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

   /** T(ORDER_ID, CUSTOMER_ID, ORDER_TOTAL) with a mirror M of it. */
   private TableAssembly tableWithMirror(Worksheet ws, WorksheetEditService svc) throws Exception {
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(
         ws, "T", "ORDER_ID", "CUSTOMER_ID", "ORDER_TOTAL");
      setType(t, "ORDER_ID", XSchema.INTEGER);
      setType(t, "CUSTOMER_ID", XSchema.INTEGER);
      setType(t, "ORDER_TOTAL", XSchema.DOUBLE);
      ws.addAssembly(t);
      svc.apply("TOK", agent, ed -> ed.addMirror("M", "T"));
      return t;
   }

   private static ColumnSelection newColumns(String... names) {
      ColumnSelection cols = new ColumnSelection();

      for(String name : names) {
         ColumnRef col = new ColumnRef(new AttributeRef(null, name));
         col.setDataType(XSchema.INTEGER);
         cols.addAttribute(col);
      }

      return cols;
   }

   /** What edit_table runs before committing the new column set. */
   private static void tabularEdit(Worksheet ws, TableAssembly t, ColumnSelection next)
      throws PairingException
   {
      WorksheetAgentController.assertSqlEditAllowsColumnRemoval(
         ws, t, t.getColumnSelection(false), next, "Editing this table would change the type of",
         breaks -> JoinKeyTypeGuard.tabularRemedy(), "Editing table 'T'",
         "Keep them. Nothing was changed.", ColumnLossReport.Scope.TABULAR_EDIT);
   }

   /** What edit_sql_query runs before committing the new SQL. */
   private static void sqlEdit(Worksheet ws, TableAssembly t, ColumnSelection next)
      throws PairingException
   {
      WorksheetAgentController.assertSqlEditAllowsColumnRemoval(
         ws, t, t.getColumnSelection(false), next, "This query would change the type of",
         JoinKeyTypeGuard::sqlRemedy, "The new query of 'T'", "Keep them. Nothing was changed.");
   }

   private void groupBy(WorksheetEditService svc, String mirror, String group, String agg)
      throws Exception
   {
      svc.apply("TOK", agent, ed -> ed.setGroupAggregate(
         mirror, List.of(new WorksheetMutationSupport.GroupSpec(group)),
         List.of(new WorksheetMutationSupport.AggregateSpec(agg, "Count", "n")), false, false));
   }

   @Test
   void droppingAColumnAMirrorGroupsByIsRefusedNamingTheMirror() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly t = tableWithMirror(ws, svc);
      groupBy(svc, "M", "CUSTOMER_ID", "ORDER_ID");

      PairingException ex = assertThrows(PairingException.class,
         () -> tabularEdit(ws, t, newColumns("ORDER_ID", "ORDER_TOTAL")));

      assertTrue(ex.getMessage().contains("\"CUSTOMER_ID\""), ex.getMessage());
      assertTrue(ex.getMessage().contains("'M' (aggregate/group-by on T.CUSTOMER_ID)"),
                 ex.getMessage());
   }

   @Test
   void droppingAColumnAMirrorAggregatesIsRefusedNamingTheMirror() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly t = tableWithMirror(ws, svc);
      groupBy(svc, "M", "CUSTOMER_ID", "ORDER_ID");

      PairingException ex = assertThrows(PairingException.class,
         () -> tabularEdit(ws, t, newColumns("CUSTOMER_ID", "ORDER_TOTAL")));

      assertTrue(ex.getMessage().contains("\"ORDER_ID\""), ex.getMessage());
      assertTrue(ex.getMessage().contains("'M' (aggregate/group-by on "),
                 ex.getMessage());
   }

   @Test
   void aMirrorOfAMirrorIsNamedToo() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly t = tableWithMirror(ws, svc);
      svc.apply("TOK", agent, ed -> ed.addMirror("M2", "M"));
      groupBy(svc, "M2", "CUSTOMER_ID", "ORDER_ID");

      PairingException ex = assertThrows(PairingException.class,
         () -> tabularEdit(ws, t, newColumns("ORDER_ID", "ORDER_TOTAL")));

      assertTrue(ex.getMessage().contains("'M2' (aggregate/group-by on M.CUSTOMER_ID)"),
                 ex.getMessage());
   }

   @Test
   void droppingAColumnNoMirrorReadsIsAccepted() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly t = tableWithMirror(ws, svc);
      groupBy(svc, "M", "CUSTOMER_ID", "ORDER_ID");

      tabularEdit(ws, t, newColumns("ORDER_ID", "CUSTOMER_ID"));
   }

   @Test
   void keepingTheGroupedColumnUnderTheSameNameIsAccepted() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly t = tableWithMirror(ws, svc);
      groupBy(svc, "M", "CUSTOMER_ID", "ORDER_ID");

      tabularEdit(ws, t, newColumns("ORDER_ID", "CUSTOMER_ID", "ORDER_TOTAL_V2"));
   }

   /** WSQ-009 stays: edit_sql_query accepts the same edit and the plugin warns. */
   @Test
   void theSqlEditPathStillAcceptsTheSameEdit() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly t = tableWithMirror(ws, svc);
      groupBy(svc, "M", "CUSTOMER_ID", "ORDER_ID");

      sqlEdit(ws, t, newColumns("ORDER_ID", "ORDER_TOTAL"));
   }

   private static void setType(TableAssembly t, String col, String type) {
      ((ColumnRef) t.getColumnSelection(false).getAttribute(col)).setDataType(type);
   }
}
