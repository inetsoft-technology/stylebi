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
 * Bug #78346: the join-key type guard of #78258 also covers the other entry points that can
 * retype a join key -- set_group_aggregate's named group, and the column-selection comparison
 * edit_sql_query / edit_table run before committing.
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetJoinKeyTypeGuard78346Test {
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

   private static void setType(TableAssembly t, String col, String type) {
      ((ColumnRef) t.getColumnSelection(false).getAttribute(col)).setDataType(type);
   }

   private static List<WorksheetMutationSupport.GroupSpec> groups(String field, String namedGroup) {
      return List.of(new WorksheetMutationSupport.GroupSpec(field, null, null, namedGroup));
   }

   private static List<WorksheetMutationSupport.AggregateSpec> count() {
      return List.of(new WorksheetMutationSupport.AggregateSpec("V", "COUNT", "N"));
   }

   private void namedGroup(WorksheetEditService svc, String type, String value) throws Exception {
      svc.apply("TOK", agent, ed -> ed.addNamedGroup(
         "NG", null, null, type,
         List.of(new WorksheetMutationSupport.GroupMapping("G1", List.of(value))), true));
   }

   private void group(WorksheetEditService svc, String namedGroup) throws Exception {
      svc.apply("TOK", agent,
                ed -> ed.setGroupAggregate("CUST", groups("ID", namedGroup), count()));
   }

   private void joinCustOrd(WorksheetEditService svc) throws Exception {
      svc.apply("TOK", agent, ed -> ed.addJoin(
         "J", "CUST", "ID", "ORD", "OID", "INNER", null, null));
   }

   private static TableAssembly cust(Worksheet ws, String idType) {
      TableAssembly cust = TestWorksheets.nonEmbeddedTableWithColumns(ws, "CUST", "ID", "V");
      setType(cust, "ID", idType);
      ws.addAssembly(cust);
      return cust;
   }

   private static EmbeddedTableAssembly ord(Worksheet ws, String type) {
      EmbeddedTableAssembly ord = TestWorksheets.tableWithColumns(ws, "ORD", "OID");
      setType(ord, "OID", type);
      ws.addAssembly(ord);
      return ord;
   }

   private static String keyType(Worksheet ws) {
      return ((TableAssembly) ws.getAssembly("CUST")).getColumnSelection(true)
         .getAttribute("ID").getDataType();
   }

   @Test
   void namedGroupOnAJoinedKeyIsRefusedAndNothingChanges() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly cust = cust(ws, XSchema.INTEGER);
      ord(ws, XSchema.INTEGER);
      namedGroup(svc, "integer", "1");
      group(svc, null);
      joinCustOrd(svc);

      PairingException ex = assertThrows(PairingException.class, () -> group(svc, "NG"));

      assertTrue(ex.getMessage().contains("join \"J\""), ex.getMessage());
      assertTrue(ex.getMessage().contains("integer to string"), ex.getMessage());
      assertTrue(ex.getMessage().contains("\"ORD\".\"OID\" (integer)"), ex.getMessage());
      assertNull(cust.getAggregateInfo().getGroup(0).getNamedGroupInfo());
      assertEquals(XSchema.INTEGER, keyType(ws));
      assertNotNull(ws.getAssembly("J"));
   }

   @Test
   void confirmedDoesNotBypassTheNamedGroupRefusal() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      cust(ws, XSchema.INTEGER);
      ord(ws, XSchema.INTEGER);
      namedGroup(svc, "integer", "1");
      group(svc, null);
      joinCustOrd(svc);

      assertThrows(PairingException.class, () -> svc.apply("TOK", agent,
         ed -> ed.setGroupAggregate("CUST", groups("ID", "NG"), count(), false, true)));
      assertEquals(XSchema.INTEGER, keyType(ws));
   }

   @Test
   void plainGroupOnTheKeyStaysAccepted() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      cust(ws, XSchema.INTEGER);
      ord(ws, XSchema.INTEGER);
      group(svc, null);
      joinCustOrd(svc);

      group(svc, null);

      assertEquals(XSchema.INTEGER, keyType(ws));
   }

   @Test
   void namedGroupOnAKeyWhoseOtherSideIsStringStaysAccepted() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      cust(ws, XSchema.STRING);
      ord(ws, XSchema.STRING);
      namedGroup(svc, "string", "a");
      group(svc, null);
      joinCustOrd(svc);

      group(svc, "NG");

      assertEquals(XSchema.STRING, keyType(ws));
   }

   @Test
   void namedGroupWithoutAJoinIsAccepted() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      cust(ws, XSchema.INTEGER);
      namedGroup(svc, "integer", "1");

      group(svc, "NG");

      assertEquals(XSchema.STRING, keyType(ws));
   }

   @Test
   void removingTheNamedGroupBackToAnIntegerKeyIsRefused() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      cust(ws, XSchema.INTEGER);
      EmbeddedTableAssembly ord = ord(ws, XSchema.STRING);
      namedGroup(svc, "integer", "1");
      group(svc, "NG");
      joinCustOrd(svc);
      assertEquals(XSchema.STRING, keyType(ws));

      PairingException ex = assertThrows(PairingException.class, () -> group(svc, null));

      assertTrue(ex.getMessage().contains("string to integer"), ex.getMessage());
      assertTrue(ex.getMessage().contains("join \"J\""), ex.getMessage());
      assertNotNull(ord);
   }

   // ---------------------------------------------------------------------------------------
   // edit_sql_query / edit_table: the selection comparison and the shared guard
   // ---------------------------------------------------------------------------------------

   private TableAssembly joinedQueryTable(Worksheet ws) throws Exception {
      WorksheetEditService svc = service(ws);
      EmbeddedTableAssembly q = TestWorksheets.tableWithColumns(ws, "Q", "id", "name");
      setType(q, "id", XSchema.INTEGER);
      ws.addAssembly(q);
      EmbeddedTableAssembly r = TestWorksheets.tableWithColumns(ws, "R", "k");
      setType(r, "k", XSchema.INTEGER);
      ws.addAssembly(r);
      svc.apply("TOK", agent, ed -> ed.addJoin("J", "Q", "id", "R", "k", "INNER", null, null));
      return q;
   }

   private static ColumnSelection retyped(ColumnSelection cols, String col, String type) {
      ColumnSelection copy = cols.clone();
      ((ColumnRef) copy.getAttribute(col)).setDataType(type);
      return copy;
   }

   @Test
   void selectionRetypeOfAJoinedKeyIsReported() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly q = joinedQueryTable(ws);
      ColumnSelection old = q.getColumnSelection(false);

      List<JoinKeyTypeGuard.Break> breaks = JoinKeyTypeGuard.findSelectionBreaks(
         ws, q, old, retyped(old, "id", XSchema.STRING));

      assertEquals(1, breaks.size());
      assertEquals("id", breaks.get(0).column());
      assertEquals(XSchema.INTEGER, breaks.get(0).oldType());
      assertEquals("J", breaks.get(0).conflicts().get(0).join());
   }

   @Test
   void nonKeyMergeableOrUnchangedRetypesReportNothing() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly q = joinedQueryTable(ws);
      ColumnSelection old = q.getColumnSelection(false);

      assertTrue(JoinKeyTypeGuard.findSelectionBreaks(
         ws, q, old, retyped(old, "name", XSchema.INTEGER)).isEmpty());
      assertTrue(JoinKeyTypeGuard.findSelectionBreaks(
         ws, q, old, retyped(old, "id", XSchema.DOUBLE)).isEmpty());
      assertTrue(JoinKeyTypeGuard.findSelectionBreaks(ws, q, old, old.clone()).isEmpty());
   }

   @Test
   void alreadyMismatchedKeyDoesNotBlockItsRepair() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly q = joinedQueryTable(ws);
      setType(q, "id", XSchema.STRING);
      ColumnSelection old = q.getColumnSelection(false);

      assertTrue(JoinKeyTypeGuard.findSelectionBreaks(
         ws, q, old, retyped(old, "id", XSchema.INTEGER)).isEmpty());
   }

   @Test
   void sqlEditThatRetypesAJoinedKeyIsRefusedWithTheCastRemedy() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly q = joinedQueryTable(ws);
      ColumnSelection old = q.getColumnSelection(false);
      ColumnSelection next = retyped(old, "id", XSchema.STRING);

      PairingException ex = assertThrows(PairingException.class,
         () -> WorksheetAgentController.assertSqlEditAllowsColumnRemoval(
            ws, q, old, next, "This query would change the type of",
            JoinKeyTypeGuard::sqlRemedy));

      assertTrue(ex.getMessage().contains("join \"J\""), ex.getMessage());
      assertTrue(ex.getMessage().contains("integer to string"), ex.getMessage());
      assertTrue(ex.getMessage().contains("re-detected"), ex.getMessage());
      assertTrue(ex.getMessage().contains("CAST \"id\" back to integer"), ex.getMessage());
   }

   @Test
   void sqlEditKeepingTheKeyTypeIsAccepted() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly q = joinedQueryTable(ws);
      ColumnSelection old = q.getColumnSelection(false);

      WorksheetAgentController.assertSqlEditAllowsColumnRemoval(
         ws, q, old, old.clone(), "x", JoinKeyTypeGuard::sqlRemedy);
   }

   @Test
   void everyConflictingColumnIsListed() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly q = joinedQueryTable(ws);
      WorksheetEditService svc = service(ws);
      setType(q, "name", XSchema.INTEGER);
      EmbeddedTableAssembly s = TestWorksheets.tableWithColumns(ws, "S", "m");
      setType(s, "m", XSchema.INTEGER);
      ws.addAssembly(s);
      svc.apply("TOK", agent, ed -> ed.addJoin("J2", "Q", "name", "S", "m", "INNER", null, null));
      ColumnSelection old = q.getColumnSelection(false);
      ColumnSelection next = retyped(retyped(old, "id", XSchema.STRING), "name", XSchema.STRING);

      PairingException ex = assertThrows(PairingException.class,
         () -> WorksheetAgentController.assertSqlEditAllowsColumnRemoval(
            ws, q, old, next, "This query would change the type of",
            JoinKeyTypeGuard::sqlRemedy));

      assertTrue(ex.getMessage().contains("\"id\""), ex.getMessage());
      assertTrue(ex.getMessage().contains("\"name\""), ex.getMessage());
   }
}
