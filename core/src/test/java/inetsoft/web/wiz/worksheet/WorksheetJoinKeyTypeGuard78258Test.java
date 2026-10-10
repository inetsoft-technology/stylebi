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
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
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
 * Bug #78258 (WBS-109, WBS-110): the join-key type guard must read an aggregated table's
 * OUTPUT column types, and retyping a join-key column must be refused when it breaks a join.
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetJoinKeyTypeGuard78258Test {
   private static final String NUM_EXPR = "parseInt(field['X'])";

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

   // ---------------------------------------------------------------------------------------
   // WBS-109
   // ---------------------------------------------------------------------------------------

   /**
    * CUST(STATE, COMPANY_NAME) grouped by STATE with Count(COMPANY_NAME) AS N_CUST, built through
    * the real set_group_aggregate path, plus ORDERS(ORDER_ID integer, STATE string).
    */
   private WorksheetEditService aggregated(Worksheet ws) throws Exception {
      WorksheetEditService svc = service(ws);
      ws.addAssembly(
         TestWorksheets.nonEmbeddedTableWithColumns(ws, "CUST", "STATE", "COMPANY_NAME"));
      EmbeddedTableAssembly orders =
         TestWorksheets.tableWithColumns(ws, "ORDERS", "ORDER_ID", "STATE");
      setType(orders, "ORDER_ID", XSchema.INTEGER);
      ws.addAssembly(orders);
      svc.apply("TOK", agent, ed -> ed.setGroupAggregate("CUST",
         List.of(new WorksheetMutationSupport.GroupSpec("STATE")),
         List.of(new WorksheetMutationSupport.AggregateSpec("COMPANY_NAME", "COUNT", "N_CUST"))));
      return svc;
   }

   @Test
   void setupBuildsARealAggregateWithDoubleOutput() throws Exception {
      Worksheet ws = new Worksheet();
      aggregated(ws);
      TableAssembly cust = (TableAssembly) ws.getAssembly("CUST");

      assertTrue(cust.isAggregate());
      assertEquals(XSchema.DOUBLE,
         cust.getColumnSelection(true).getAttribute("N_CUST").getDataType());
   }

   @Test
   void aggregateAliasDoubleVsIntegerKeyIsAccepted() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = aggregated(ws);

      svc.apply("TOK", agent, ed -> ed.addJoin(
         "J", "CUST", "N_CUST", "ORDERS", "ORDER_ID", "INNER", null, null));

      assertNotNull(ws.getAssembly("J"));
   }

   @Test
   void aggregateAliasDoubleVsStringKeyIsRefused() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = aggregated(ws);

      PairingException ex = assertThrows(PairingException.class, () -> svc.apply("TOK", agent,
         ed -> ed.addJoin("J", "CUST", "N_CUST", "ORDERS", "STATE", "INNER", null, null)));

      assertTrue(ex.getMessage().contains("\"N_CUST\" is double"), ex.getMessage());
      assertNull(ws.getAssembly("J"));
   }

   @Test
   void aggregateAliasIsCheckedOnTheJoinPathsForm() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = aggregated(ws);

      svc.apply("TOK", agent, ed -> ed.addJoin("J", List.of(
         new WorksheetMutationSupport.JoinPathSpec(
            "CUST", "N_CUST", "ORDERS", "ORDER_ID", "INNER"))));
      assertNotNull(ws.getAssembly("J"));

      PairingException ex = assertThrows(PairingException.class, () -> svc.apply("TOK", agent,
         ed -> ed.addJoin("J2", List.of(new WorksheetMutationSupport.JoinPathSpec(
            "CUST", "N_CUST", "ORDERS", "STATE", "INNER")))));
      assertTrue(ex.getMessage().contains("\"N_CUST\" is double"), ex.getMessage());
   }

   @Test
   void aggregateAliasIsCheckedOnEditJoinAndAddTableToJoin() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = aggregated(ws);
      EmbeddedTableAssembly other = TestWorksheets.tableWithColumns(ws, "OTHER", "OID", "S");
      setType(other, "OID", XSchema.INTEGER);
      ws.addAssembly(other);
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "OTHER2", "S2"));

      svc.apply("TOK", agent, ed -> ed.addJoin(
         "J", "CUST", "N_CUST", "ORDERS", "ORDER_ID", "INNER", null, null));

      PairingException ex = assertThrows(PairingException.class, () -> svc.apply("TOK", agent,
         ed -> ed.editJoin("J", "N_CUST", "STATE", "INNER", null, null)));
      assertTrue(ex.getMessage().contains("\"N_CUST\" is double"), ex.getMessage());

      svc.apply("TOK", agent, ed -> ed.addTableToJoin(
         "J", "CUST", "N_CUST", "OTHER", "OID", "INNER", null, null));
      ex = assertThrows(PairingException.class, () -> svc.apply("TOK", agent,
         ed -> ed.addTableToJoin("J", "CUST", "N_CUST", "OTHER2", "S2", "INNER", null, null)));
      assertTrue(ex.getMessage().contains("\"N_CUST\" is double"), ex.getMessage());
   }

   @Test
   void groupByKeyOnAggregatedTableKeepsItsSourceType() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = aggregated(ws);

      svc.apply("TOK", agent, ed -> ed.addJoin(
         "J", "CUST", "STATE", "ORDERS", "STATE", "INNER", null, null));
      assertNotNull(ws.getAssembly("J"));

      assertThrows(PairingException.class, () -> svc.apply("TOK", agent,
         ed -> ed.addJoin("J2", "CUST", "STATE", "ORDERS", "ORDER_ID", "INNER", null, null)));
   }

   @Test
   void sourceNameKeyOnAggregatedTableIsNotNewlyRefused() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = aggregated(ws);

      // COMPANY_NAME is the aggregate's source column; before the fix it resolved (string vs
      // string) and passed. It must not turn into a refusal.
      svc.apply("TOK", agent, ed -> ed.addJoin(
         "J", "CUST", "COMPANY_NAME", "ORDERS", "STATE", "INNER", null, null));
   }

   @Test
   void plainTablesDoubleVsIntegerAndStringVsStringStayAccepted() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      EmbeddedTableAssembly a = TestWorksheets.tableWithColumns(ws, "A", "d", "s");
      EmbeddedTableAssembly b = TestWorksheets.tableWithColumns(ws, "B", "i", "s");
      setType(a, "d", XSchema.DOUBLE);
      setType(b, "i", XSchema.INTEGER);
      ws.addAssembly(a);
      ws.addAssembly(b);

      svc.apply("TOK", agent, ed -> ed.addJoin("J1", "A", "d", "B", "i", "INNER", null, null));
      svc.apply("TOK", agent, ed -> ed.addJoin("J2", "A", "s", "B", "s", "INNER", null, null));
   }

   // ---------------------------------------------------------------------------------------
   // WBS-110
   // ---------------------------------------------------------------------------------------

   private WorksheetEditService withTables(Worksheet ws) throws Exception {
      WorksheetEditService svc = service(ws);
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "X");
      EmbeddedTableAssembly orders = TestWorksheets.tableWithColumns(ws, "ORDERS", "ORDER_ID");
      setType(orders, "ORDER_ID", XSchema.INTEGER);
      ws.addAssembly(t);
      ws.addAssembly(orders);
      svc.apply("TOK", agent, ed -> ed.addExpressionColumn(
         "T", "K", NUM_EXPR, XSchema.DOUBLE, false));
      return svc;
   }

   /** T(X, K = expression double) joined with ORDERS(ORDER_ID integer) as J on K = ORDER_ID. */
   private WorksheetEditService joined(Worksheet ws) throws Exception {
      WorksheetEditService svc = withTables(ws);
      svc.apply("TOK", agent, ed -> ed.addJoin(
         "J", "T", "K", "ORDERS", "ORDER_ID", "INNER", null, null));
      return svc;
   }

   private static ColumnRef kRef(Worksheet ws) {
      return (ColumnRef) ((TableAssembly) ws.getAssembly("T"))
         .getColumnSelection(false).getAttribute("K");
   }

   private static String kExpression(Worksheet ws) {
      return ((ExpressionRef) kRef(ws).getDataRef()).getExpression();
   }

   @Test
   void changeColumnTypeThatBreaksAJoinIsRefusedNamingJoinAndTypes() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = joined(ws);

      PairingException ex = assertThrows(PairingException.class, () -> svc.apply(
         "TOK", agent, ed -> ed.changeColumnType("T", "K", XSchema.STRING)));

      assertTrue(ex.getMessage().contains("\"J\""), ex.getMessage());
      assertTrue(ex.getMessage().contains(XSchema.STRING), ex.getMessage());
      assertTrue(ex.getMessage().contains(XSchema.INTEGER), ex.getMessage());
      assertEquals(XSchema.DOUBLE, kRef(ws).getDataType(), "refusal must leave the type alone");
   }

   @Test
   void editExpressionWithExplicitStringBreakingAJoinIsRefusedAtomically() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = joined(ws);

      assertThrows(PairingException.class, () -> svc.apply("TOK", agent,
         ed -> ed.editExpression("T", "K", "field['X'] + 'z'", XSchema.STRING, false)));

      assertEquals(XSchema.DOUBLE, kRef(ws).getDataType());
      assertEquals(NUM_EXPR, kExpression(ws));
   }

   @Test
   void editExpressionReInferToStringIsRefusedAndAdvisesAnExplicitType() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "X");
      EmbeddedTableAssembly orders = TestWorksheets.tableWithColumns(ws, "ORDERS", "ORDER_ID");
      setType(orders, "ORDER_ID", XSchema.INTEGER);
      setType(t, "X", XSchema.DOUBLE);
      ws.addAssembly(t);
      ws.addAssembly(orders);
      // No explicit type: the column's numeric type is inferred, so a later edit re-infers it.
      svc.apply("TOK", agent,
         ed -> ed.addExpressionColumn("T", "K", "field['X'] * 2", null, false));
      assertEquals(XSchema.DOUBLE, kRef(ws).getDataType());
      svc.apply("TOK", agent, ed -> ed.addJoin(
         "J", "T", "K", "ORDERS", "ORDER_ID", "INNER", null, null));

      // parseInt(...) is numeric but outside the inference allowlist, so re-inference would
      // silently fall to string and break J.
      PairingException ex = assertThrows(PairingException.class, () -> svc.apply("TOK", agent,
         ed -> ed.editExpression("T", "K", NUM_EXPR, null, false)));

      assertTrue(ex.getMessage().contains("explicit `type`"), ex.getMessage());
      assertEquals("field['X'] * 2", kExpression(ws));
      assertEquals(XSchema.DOUBLE, kRef(ws).getDataType());

      // The advised way out: pass the type explicitly.
      svc.apply("TOK", agent, ed -> ed.editExpression("T", "K", NUM_EXPR, XSchema.DOUBLE, false));
      assertEquals(NUM_EXPR, kExpression(ws));
   }

   @Test
   void stillMergeableRetypeSucceedsWithTheJoinInPlace() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = joined(ws);

      svc.apply("TOK", agent, ed -> ed.changeColumnType("T", "K", XSchema.INTEGER));
      assertEquals(XSchema.INTEGER, kRef(ws).getDataType());

      svc.apply("TOK", agent, ed -> ed.editExpression("T", "K", NUM_EXPR, XSchema.LONG, false));
      assertEquals(XSchema.LONG, kRef(ws).getDataType());
   }

   @Test
   void retypeWithoutADependentJoinSucceeds() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = withTables(ws);

      svc.apply("TOK", agent, ed -> ed.changeColumnType("T", "K", XSchema.STRING));
      assertEquals(XSchema.STRING, kRef(ws).getDataType());
      svc.apply("TOK", agent,
         ed -> ed.editExpression("T", "K", "field['X']", XSchema.DOUBLE, false));
      assertEquals(XSchema.DOUBLE, kRef(ws).getDataType());
   }

   @Test
   void retypingAColumnThatIsNotAJoinKeyIsNotBlocked() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = joined(ws);
      svc.apply("TOK", agent,
         ed -> ed.addExpressionColumn("T", "M", NUM_EXPR, XSchema.DOUBLE, false));

      svc.apply("TOK", agent, ed -> ed.changeColumnType("T", "M", XSchema.STRING));
   }

   @Test
   void alreadyMismatchedJoinDoesNotBlockARepair() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = joined(ws);
      kRef(ws).setDataType(XSchema.STRING);

      // string -> string does not change mergeability; string -> integer repairs it.
      svc.apply("TOK", agent, ed -> ed.changeColumnType("T", "K", XSchema.STRING));
      svc.apply("TOK", agent, ed -> ed.changeColumnType("T", "K", XSchema.INTEGER));
      assertEquals(XSchema.INTEGER, kRef(ws).getDataType());
   }

   @Test
   void aliasKeyedJoinIsDetected() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = withTables(ws);
      TableAssembly t = (TableAssembly) ws.getAssembly("T");
      ColumnSelection cs = t.getColumnSelection(false);
      ((ColumnRef) cs.getAttribute("K")).setAlias("KA");
      t.setColumnSelection(cs, false);
      svc.apply("TOK", agent, ed -> ed.addJoin(
         "J", "T", "KA", "ORDERS", "ORDER_ID", "INNER", null, null));

      assertThrows(PairingException.class, () -> svc.apply("TOK", agent,
         ed -> ed.changeColumnType("T", "KA", XSchema.STRING)));
   }

   @Test
   void entityQualifiedOperatorAttributeIsDetected() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = joined(ws);
      RelationalJoinTableAssembly j = (RelationalJoinTableAssembly) ws.getAssembly("J");
      TableAssemblyOperator op = j.getOperator("T", "ORDERS");
      op.getOperator(0).setLeftAttribute(new ColumnRef(new AttributeRef("T", "K")));
      j.setOperator("T", "ORDERS", op);

      assertThrows(PairingException.class, () -> svc.apply("TOK", agent,
         ed -> ed.changeColumnType("T", "K", XSchema.STRING)));
   }

   @Test
   void joinOnAMirrorOfTheEditedTableIsDetected() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = withTables(ws);
      svc.apply("TOK", agent, ed -> ed.addMirror("MT", "T"));
      svc.apply("TOK", agent, ed -> ed.addJoin(
         "J2", "MT", "K", "ORDERS", "ORDER_ID", "INNER", null, null));

      PairingException ex = assertThrows(PairingException.class, () -> svc.apply("TOK", agent,
         ed -> ed.changeColumnType("T", "K", XSchema.STRING)));
      assertTrue(ex.getMessage().contains("\"J2\""), ex.getMessage());
   }

   /**
    * Gives a join the output columns Composer's refresh would give it (entity-qualified
    * {@code TABLE.COLUMN}); the unit test has no refresh, so a join's selection starts empty.
    */
   private static void exposeColumns(Worksheet ws, String join, String[][] cols) {
      ColumnSelection cs = new ColumnSelection();

      for(String[] c : cols) {
         ColumnRef ref = new ColumnRef(new AttributeRef(c[0], c[1]));
         ref.setDataType(c[2]);
         cs.addAttribute(ref);
      }

      ((TableAssembly) ws.getAssembly(join)).setColumnSelection(cs, false);
   }

   @Test
   void joinNestedOverAnotherJoinIsDetected() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = joined(ws);
      EmbeddedTableAssembly items = TestWorksheets.tableWithColumns(ws, "ITEMS", "ITEM_ID", "N");
      EmbeddedTableAssembly extra = TestWorksheets.tableWithColumns(ws, "EXTRA", "N");
      setType(items, "ITEM_ID", XSchema.INTEGER);
      ws.addAssembly(items);
      ws.addAssembly(extra);
      svc.apply("TOK", agent, ed -> ed.addJoin(
         "J5", "ITEMS", "N", "EXTRA", "N", "INNER", null, null));
      exposeColumns(ws, "J", new String[][] {
         {"T", "K", XSchema.DOUBLE}, {"ORDERS", "ORDER_ID", XSchema.INTEGER}});
      exposeColumns(ws, "J5", new String[][] {
         {"ITEMS", "ITEM_ID", XSchema.INTEGER}, {"EXTRA", "N", XSchema.STRING}});

      // Both sides are joins, so this builds a genuinely nested join J1(J, J5) keyed on T.K.
      svc.apply("TOK", agent, ed -> ed.addJoin(
         "J1", "J", "T.K", "J5", "ITEMS.ITEM_ID", "INNER", null, null));

      PairingException ex = assertThrows(PairingException.class, () -> svc.apply("TOK", agent,
         ed -> ed.changeColumnType("T", "K", XSchema.STRING)));
      assertTrue(ex.getMessage().contains("\"J1\""), ex.getMessage());
   }
}
