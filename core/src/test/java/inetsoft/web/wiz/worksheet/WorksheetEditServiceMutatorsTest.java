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
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@WizAgentTestSupport
class WorksheetEditServiceMutatorsTest {

   // =========================================================================
   // Helper — builds a WorksheetEditService with mocked deps for a given rws
   // =========================================================================

   private WorksheetEditService service(RuntimeWorksheet rws, String runtimeId,
                                        Principal agent, String token)
      throws PairingException, inetsoft.sree.security.SecurityException
   {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);

      return service(rws, runtimeId, agent, token, securityEngine);
   }

   /**
    * Same as {@link #service(RuntimeWorksheet, String, Principal, String)} but lets the caller
    * supply an already-stubbed {@link SecurityEngine} mock, so permission checks can be denied
    * (or captured) for specific {@link ResourceType}s.
    */
   private WorksheetEditService service(RuntimeWorksheet rws, String runtimeId,
                                        Principal agent, String token,
                                        SecurityEngine securityEngine)
      throws PairingException, inetsoft.sree.security.SecurityException
   {
      SheetSessionService sessions       = mock(SheetSessionService.class);
      SheetRuntimeAccess  runtimeAccess  = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      JoinSession s = new JoinSession(token, runtimeId, "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null);
      when(sessions.resolve(eq(token), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq(runtimeId), eq(agent)))
         .thenReturn(rws);

      return new WorksheetEditService(sessions, runtimeAccess, broadcast, securityEngine);
   }

   private RuntimeWorksheet rws(Worksheet ws) {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      return rws;
   }

   // =========================================================================
   // Filter tests
   // =========================================================================

   @Test
   void addFilterAddsCondition() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addFilter("T", "a", "=", "hello"));

      assertNotNull(t.getPreConditionList());
      assertFalse(t.getPreConditionList().isEmpty());
   }

   @Test
   void addFilterAppendsSecondConditionWithAnd() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> {
         ed.addFilter("T", "a", "=", "hello");
         ed.addFilter("T", "b", "=", "world");
      });

      // Two conditions + one AND junction = size 3
      assertEquals(3, t.getPreConditionList().getConditionList().getSize());
   }

   @Test
   void removeFilterRemovesCondition() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> {
         ed.addFilter("T", "a", "=", "hello");
         ed.removeFilter("T", "a");
      });

      assertTrue(t.getPreConditionList() == null || t.getPreConditionList().isEmpty());
   }

   @Test
   void removeFilterLeavesOtherConditions() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> {
         ed.addFilter("T", "a", "=", "hello");
         ed.addFilter("T", "b", "=", "world");
         ed.removeFilter("T", "a");
      });

      // Only the "b" condition should remain — size 1 (just the ConditionItem)
      assertNotNull(t.getPreConditionList());
      assertFalse(t.getPreConditionList().isEmpty());
      assertEquals(1, t.getPreConditionList().getConditionList().getSize());
   }

   // =========================================================================
   // Aggregate tests
   // =========================================================================

   @Test
   void setGroupAggregateAppliesInfo() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cat", "val");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T",
                              List.of("cat"),
                              List.of(new WorksheetMutationSupport.AggregateSpec("val", "SUM", null)))
      );

      AggregateInfo ai = t.getAggregateInfo();
      assertNotNull(ai);
      assertFalse(ai.isEmpty());
      assertEquals(1, ai.getGroupCount());
      assertEquals(1, ai.getAggregateCount());
   }

   @Test
   void sameColumnAggregatesKeepDistinctAliases() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cat", "price");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T",
            List.of("cat"),
            List.of(new WorksheetMutationSupport.AggregateSpec("price", "MIN", "min_price"),
                    new WorksheetMutationSupport.AggregateSpec("price", "MAX", "max_price"),
                    new WorksheetMutationSupport.AggregateSpec("price", "COUNT", "n"))));

      AggregateInfo ai = t.getAggregateInfo();
      assertEquals(3, ai.getAggregateCount());

      // Each aggregate must carry its own alias — with a shared ColumnRef the second
      // alias silently overwrote the first (Min/Max both ended up named max_price).
      // The first aggregate aliases the shared column-selection ref (that is how the
      // output column is named); subsequent ones are converted to secondary aggregates
      // on their own expression columns carrying their own aliases.
      ColumnRef ref0 = (ColumnRef) ai.getAggregate(0).getDataRef();
      ColumnRef ref1 = (ColumnRef) ai.getAggregate(1).getDataRef();
      ColumnRef ref2 = (ColumnRef) ai.getAggregate(2).getDataRef();
      assertEquals("min_price", ref0.getAlias());
      assertEquals("max_price", ref1.getAlias());
      assertEquals("n", ref2.getAlias());

      // The 2nd and 3rd secondaries must bind to DISTINCT expression columns —
      // the unique-name scan previously missed aliased expression columns, so the
      // third aggregate collided with the second (both bound to price_1).
      assertEquals("price_1", ref1.getAttribute());
      assertEquals("price_2", ref2.getAttribute());

      // The first alias lands on the shared base ref (output naming mechanism);
      // the second must NOT have overwritten it.
      ColumnRef base = null;
      ColumnSelection cs2 = t.getColumnSelection(false);

      for(int i = 0; i < cs2.getAttributeCount(); i++) {
         if(cs2.getAttribute(i) instanceof ColumnRef cr && "price".equals(cr.getAttribute()) &&
            !(cr.getDataRef() instanceof ExpressionRef))
         {
            base = cr;
            break;
         }
      }

      assertNotNull(base);
      assertEquals("min_price", base.getAlias());
   }

   @Test
   void setGroupAggregateFailsLoudOnUnknownColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cat", "val");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // An unresolvable field previously produced a bogus AttributeRef that the engine
      // silently dropped — a plausible-but-wrong result. It must fail loud instead.
      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.setGroupAggregate("T", List.of("cat"),
               List.of(new WorksheetMutationSupport.AggregateSpec("no_such_col", "SUM", "x")))));
      assertTrue(ex.getMessage().contains("no_such_col"));
      assertTrue(ex.getMessage().contains("Available columns"));

      PairingException ex2 = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.setGroupAggregate("T", List.of("no_such_group"), List.of())));
      assertTrue(ex2.getMessage().contains("no_such_group"));
   }

   // =========================================================================
   // Expression column test
   // =========================================================================

   @Test
   void addExpressionColumnAddsRef() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
         ed -> ed.addExpressionColumn("T", "computed", "field['a'] * 2", "integer", false));

      ColumnSelection cs = t.getColumnSelection(false);
      DataRef ref = cs.getAttribute("computed");
      assertNotNull(ref, "expression column 'computed' should be present");
   }

   @Test
   void addExpressionColumnRewritesDateSubtraction() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t =
         TestWorksheets.tableWithColumns(ws, "T", "start_date", "end_date", "amount");
      ColumnSelection cs = t.getColumnSelection(false);
      ((ColumnRef) cs.getAttribute("start_date")).setDataType(XSchema.TIME_INSTANT);
      ((ColumnRef) cs.getAttribute("end_date")).setDataType(XSchema.TIME_INSTANT);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // Plain date subtraction silently evaluates to null in the Rhino engine —
      // the mutator must rewrite it to the .getTime() form.
      svc.apply("TOK", agent, ed -> ed.addExpressionColumn(
         "T", "days", "(field['end_date'] - field['start_date']) / 86400000",
         "double", false));

      ColumnRef col = (ColumnRef) t.getColumnSelection(false).getAttribute("days");
      assertNotNull(col);
      String expr = ((ExpressionRef) col.getDataRef()).getExpression();
      assertEquals("((field['end_date'].getTime() - field['start_date'].getTime())) / 86400000",
                   expr);

      // Subtraction of non-date columns must be left untouched.
      svc.apply("TOK", agent, ed -> ed.addExpressionColumn(
         "T", "diff", "field['amount'] - field['amount']", "double", false));
      ColumnRef col2 = (ColumnRef) t.getColumnSelection(false).getAttribute("diff");
      String expr2 = ((ExpressionRef) col2.getDataRef()).getExpression();
      assertEquals("field['amount'] - field['amount']", expr2);

      // SQL-mode expressions must be left untouched.
      svc.apply("TOK", agent, ed -> ed.addExpressionColumn(
         "T", "sql_days", "field['end_date'] - field['start_date']", "double", true));
      ColumnRef col3 = (ColumnRef) t.getColumnSelection(false).getAttribute("sql_days");
      String expr3 = ((ExpressionRef) col3.getDataRef()).getExpression();
      assertEquals("field['end_date'] - field['start_date']", expr3);
   }

   // =========================================================================
   // Sort test
   // =========================================================================

   @Test
   void setSortSetsDirectionOnColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.setSort("T", "a", "DESC"));

      SortInfo si = t.getSortInfo();
      assertNotNull(si);
      assertEquals(1, si.getSortCount());
      assertEquals(XConstants.SORT_DESC, si.getSort(0).getOrder());
   }

   @Test
   void setSortReplacesExistingSort() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> {
         ed.setSort("T", "a", "ASC");
         ed.setSort("T", "a", "DESC");
      });

      SortInfo si = t.getSortInfo();
      assertEquals(1, si.getSortCount(), "should have exactly one sort for column a");
      assertEquals(XConstants.SORT_DESC, si.getSort(0).getOrder());
   }

   // =========================================================================
   // Join tests
   // =========================================================================

   @Test
   void addJoinAddsAssembly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id", "name");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id", "value");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
         ed -> ed.addJoin("J", "L", "id", "R", "id", "INNER", null, null));

      Assembly a = ws.getAssembly("J");
      assertNotNull(a, "join assembly 'J' should be in the worksheet");
      assertInstanceOf(RelationalJoinTableAssembly.class, a);
   }

   @Test
   void removeJoinRemovesAssembly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> {
         ed.addJoin("J", "L", "id", "R", "id", "LEFT", null, null);
         ed.removeJoin("J");
      });

      assertNull(ws.getAssembly("J"), "join assembly 'J' should have been removed");
   }

   // =========================================================================
   // Edit-in-place tests
   // =========================================================================

   @Test
   void editConditionReplacesExistingFilter() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> {
         ed.addFilter("T", "a", "=", "old");
         ed.editCondition("T", "a", "=", "new");
      });

      // After edit_condition, exactly one ConditionItem with value "new"
      assertNotNull(t.getPreConditionList());
      assertEquals(1, t.getPreConditionList().getConditionList().getSize());
   }

   @Test
   void editExpressionUpdatesExistingColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.addExpressionColumn("T", "calc", "field['a'] * 1", "integer", false));

      svc.apply("TOK", agent, ed ->
         ed.editExpression("T", "calc", "field['a'] * 2", "integer", false));

      ColumnSelection cs = t.getColumnSelection(false);
      DataRef ref = cs.getAttribute("calc");
      assertNotNull(ref, "'calc' column should still exist");
      // Check the expression was updated
      assertInstanceOf(ColumnRef.class, ref);
      ColumnRef cr = (ColumnRef) ref;
      assertInstanceOf(ExpressionRef.class, cr.getDataRef());
      assertEquals("field['a'] * 2", ((ExpressionRef) cr.getDataRef()).getExpression());
   }

   @Test
   void editExpressionAddsWhenNotFound() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.editExpression("T", "newcalc", "field['a'] + 1", "integer", false));

      assertNotNull(t.getColumnSelection(false).getAttribute("newcalc"),
                    "'newcalc' should be added as a new expression column");
   }

   @Test
   void editJoinUpdatesKeyColumns() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id", "altId");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id", "altId");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addJoin("J", "L", "id", "R", "id", "INNER", null, null));
      svc.apply("TOK", agent, ed -> ed.editJoin("J", "altId", "altId", "LEFT", null, null));

      Assembly a = ws.getAssembly("J");
      assertNotNull(a);
      assertInstanceOf(RelationalJoinTableAssembly.class, a);
      // Verify a LEFT join was set (the operator count stayed 1)
      RelationalJoinTableAssembly join = (RelationalJoinTableAssembly) a;
      @SuppressWarnings("unchecked")
      java.util.Enumeration<TableAssemblyOperator> iter =
         (java.util.Enumeration<TableAssemblyOperator>) join.getOperators();
      assertTrue(iter.hasMoreElements());
      TableAssemblyOperator top = iter.nextElement();
      assertEquals(1, top.getOperatorCount());
      assertEquals(TableAssemblyOperator.LEFT_JOIN, top.getOperator(0).getOperation());
      assertEquals("altId", top.getOperator(0).getLeftAttribute().getAttribute());
      assertEquals("altId", top.getOperator(0).getRightAttribute().getAttribute());
   }

   @Test
   void editJoinThrowsWhenAssemblyNotFound() throws Exception {
      Worksheet ws = new Worksheet();
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.editJoin("NOPE", "a", "b", "INNER", null, null)));
   }

   // =========================================================================
   // Add table test
   // =========================================================================

   @Test
   void addTableAddsAssembly() throws Exception {
      Worksheet ws = new Worksheet();
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addTable("NewTable", "col1", "col2"));

      Assembly a = ws.getAssembly("NewTable");
      assertNotNull(a, "assembly 'NewTable' should be in the worksheet");
      assertInstanceOf(EmbeddedTableAssembly.class, a);

      EmbeddedTableAssembly t = (EmbeddedTableAssembly) a;
      assertNotNull(t.getColumnSelection(false).getAttribute("col1"));
      assertNotNull(t.getColumnSelection(false).getAttribute("col2"));
   }

   // =========================================================================
   // Permission gate tests — addCrossJoin / addExpressionColumn / editExpression
   // =========================================================================
   //
   // Each gated op calls requirePermission(ResourceType), which is backed by
   // securityEngine.checkPermission(agent, <type>, "*", ResourceAction.ACCESS). These tests
   // pin down two things per op: (1) denial throws SecurityException *and* the underlying
   // mutation never happens, and (2) the EXACT ResourceType checked is the one documented for
   // that op — guarding against a copy-paste mix-up between CROSS_JOIN and
   // WORKSHEET_EXPRESSION_COLUMN across the three call sites added together.

   /** Builds a securityEngine mock granting every permission except the one given. */
   private SecurityEngine securityEngineDenying(Principal agent, ResourceType deniedType)
      throws SecurityException
   {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      when(securityEngine.checkPermission(
         eq(agent), eq(deniedType), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);
      return securityEngine;
   }

   /** Builds a securityEngine mock granting every permission — used for ResourceType capture. */
   private SecurityEngine securityEngineGrantingAll() throws SecurityException {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      return securityEngine;
   }

   // --- addCrossJoin -------------------------------------------------------

   @Test
   void addCrossJoinDeniedThrowsAndDoesNotAddAssembly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      SecurityEngine securityEngine = securityEngineDenying(agent, ResourceType.CROSS_JOIN);
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      assertThrows(SecurityException.class, () ->
         svc.apply("TOK", agent, ed -> ed.addCrossJoin("J", "L", "R")));

      assertNull(ws.getAssembly("J"),
         "cross join assembly must not be created when CROSS_JOIN permission is denied");
   }

   @Test
   void addCrossJoinChecksCrossJoinResourceTypeExactly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      SecurityEngine securityEngine = securityEngineGrantingAll();
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      svc.apply("TOK", agent, ed -> ed.addCrossJoin("J", "L", "R"));

      ArgumentCaptor<ResourceType> typeCaptor = ArgumentCaptor.forClass(ResourceType.class);
      verify(securityEngine).checkPermission(
         eq(agent), typeCaptor.capture(), eq("*"), eq(ResourceAction.ACCESS));
      assertEquals(ResourceType.CROSS_JOIN, typeCaptor.getValue(),
         "addCrossJoin must check ResourceType.CROSS_JOIN, not e.g. WORKSHEET_EXPRESSION_COLUMN");

      assertNotNull(ws.getAssembly("J"),
         "cross join assembly should be created when permission is granted");
   }

   // --- addExpressionColumn -------------------------------------------------

   @Test
   void addExpressionColumnDeniedThrowsAndDoesNotAddColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      SecurityEngine securityEngine =
         securityEngineDenying(agent, ResourceType.WORKSHEET_EXPRESSION_COLUMN);
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      assertThrows(SecurityException.class, () ->
         svc.apply("TOK", agent,
            ed -> ed.addExpressionColumn("T", "computed", "field['a'] * 2", "integer", false)));

      assertNull(t.getColumnSelection(false).getAttribute("computed"),
         "expression column must not be added when WORKSHEET_EXPRESSION_COLUMN permission is denied");
   }

   @Test
   void addExpressionColumnChecksExpressionColumnResourceTypeExactly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      SecurityEngine securityEngine = securityEngineGrantingAll();
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      svc.apply("TOK", agent,
         ed -> ed.addExpressionColumn("T", "computed", "field['a'] * 2", "integer", false));

      ArgumentCaptor<ResourceType> typeCaptor = ArgumentCaptor.forClass(ResourceType.class);
      verify(securityEngine).checkPermission(
         eq(agent), typeCaptor.capture(), eq("*"), eq(ResourceAction.ACCESS));
      assertEquals(ResourceType.WORKSHEET_EXPRESSION_COLUMN, typeCaptor.getValue(),
         "addExpressionColumn must check ResourceType.WORKSHEET_EXPRESSION_COLUMN, not e.g. CROSS_JOIN");

      assertNotNull(t.getColumnSelection(false).getAttribute("computed"),
         "expression column should be added when permission is granted");
   }

   // --- editExpression -------------------------------------------------------

   @Test
   void editExpressionDeniedThrowsAndDoesNotChangeColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      // Seed the existing expression column directly via WorksheetMutationSupport, bypassing
      // the Editor entirely, so setup does not itself consult securityEngine.
      WorksheetMutationSupport.addExpressionColumn(t, "calc", "field['a'] * 1", "integer", false);
      Principal agent = TestPrincipals.user("alice", "host-org");
      SecurityEngine securityEngine =
         securityEngineDenying(agent, ResourceType.WORKSHEET_EXPRESSION_COLUMN);
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      assertThrows(SecurityException.class, () ->
         svc.apply("TOK", agent,
            ed -> ed.editExpression("T", "calc", "field['a'] * 2", "integer", false)));

      ColumnRef col = (ColumnRef) t.getColumnSelection(false).getAttribute("calc");
      assertNotNull(col, "'calc' column should still be present");
      assertEquals("field['a'] * 1", ((ExpressionRef) col.getDataRef()).getExpression(),
         "expression must be unchanged when WORKSHEET_EXPRESSION_COLUMN permission is denied");
   }

   @Test
   void editExpressionChecksExpressionColumnResourceTypeExactly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      WorksheetMutationSupport.addExpressionColumn(t, "calc", "field['a'] * 1", "integer", false);
      Principal agent = TestPrincipals.user("alice", "host-org");
      SecurityEngine securityEngine = securityEngineGrantingAll();
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      svc.apply("TOK", agent,
         ed -> ed.editExpression("T", "calc", "field['a'] * 2", "integer", false));

      ArgumentCaptor<ResourceType> typeCaptor = ArgumentCaptor.forClass(ResourceType.class);
      verify(securityEngine).checkPermission(
         eq(agent), typeCaptor.capture(), eq("*"), eq(ResourceAction.ACCESS));
      assertEquals(ResourceType.WORKSHEET_EXPRESSION_COLUMN, typeCaptor.getValue(),
         "editExpression must check ResourceType.WORKSHEET_EXPRESSION_COLUMN, not e.g. CROSS_JOIN");

      ColumnRef col = (ColumnRef) t.getColumnSelection(false).getAttribute("calc");
      assertEquals("field['a'] * 2", ((ExpressionRef) col.getDataRef()).getExpression(),
         "expression should be updated when permission is granted");
   }

   // --- addJoin("CROSS", ...) delegation to the CROSS_JOIN gate --------------

   @Test
   void addJoinCrossTypeDeniedThrowsAndDoesNotAddAssembly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      SecurityEngine securityEngine = securityEngineDenying(agent, ResourceType.CROSS_JOIN);
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      assertThrows(SecurityException.class, () ->
         svc.apply("TOK", agent,
            ed -> ed.addJoin("J", "L", "id", "R", "id", "CROSS", null, null)));

      assertNull(ws.getAssembly("J"),
         "addJoin(\"CROSS\",...) must go through the same CROSS_JOIN gate as addCrossJoin " +
         "and must not create the assembly when denied");
   }

   @Test
   void addJoinNonCrossTypeDoesNotRequireCrossJoinPermission() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      // CROSS_JOIN denied — an INNER join must succeed anyway, proving the gate is scoped to
      // cross joins only.
      SecurityEngine securityEngine = securityEngineDenying(agent, ResourceType.CROSS_JOIN);
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      svc.apply("TOK", agent, ed -> ed.addJoin("J", "L", "id", "R", "id", "INNER", null, null));

      assertNotNull(ws.getAssembly("J"),
         "INNER join must succeed even though CROSS_JOIN permission is denied");
      verify(securityEngine, never()).checkPermission(
         eq(agent), eq(ResourceType.CROSS_JOIN), anyString(), any(ResourceAction.class));
   }

   // --- editJoin("CROSS", ...) must clear the same CROSS_JOIN gate -----------

   @Test
   void editJoinToCrossTypeDeniedThrowsAndDoesNotChangeOperator() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      // CROSS_JOIN denied. An INNER join is allowed (gate is scoped to cross joins),
      // so the attacker first creates one, then tries to rewrite it into a cross join
      // via editJoin — which must hit the same gate and be rejected.
      SecurityEngine securityEngine = securityEngineDenying(agent, ResourceType.CROSS_JOIN);
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      svc.apply("TOK", agent, ed -> ed.addJoin("J", "L", "id", "R", "id", "INNER", null, null));

      assertThrows(SecurityException.class, () ->
         svc.apply("TOK", agent, ed -> ed.editJoin("J", "id", "id", "CROSS", null, null)));

      // The operator must remain the original INNER join — the mutation must not have
      // happened before the permission check threw.
      RelationalJoinTableAssembly join = (RelationalJoinTableAssembly) ws.getAssembly("J");
      assertNotNull(join, "join assembly should still exist");
      @SuppressWarnings("unchecked")
      java.util.Enumeration<TableAssemblyOperator> iter =
         (java.util.Enumeration<TableAssemblyOperator>) join.getOperators();
      assertTrue(iter.hasMoreElements());
      TableAssemblyOperator top = iter.nextElement();
      assertEquals(TableAssemblyOperator.INNER_JOIN, top.getOperator(0).getOperation(),
         "editJoin(\"CROSS\",...) must not downgrade the join to a cross join when " +
         "CROSS_JOIN permission is denied");
   }

   @Test
   void editJoinNonCrossTypeDoesNotRequireCrossJoinPermission() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      // CROSS_JOIN denied — editing to a LEFT join must still succeed, proving the gate
      // is scoped to cross joins only.
      SecurityEngine securityEngine = securityEngineDenying(agent, ResourceType.CROSS_JOIN);
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      svc.apply("TOK", agent, ed -> ed.addJoin("J", "L", "id", "R", "id", "INNER", null, null));
      svc.apply("TOK", agent, ed -> ed.editJoin("J", "id", "id", "LEFT", null, null));

      RelationalJoinTableAssembly join = (RelationalJoinTableAssembly) ws.getAssembly("J");
      @SuppressWarnings("unchecked")
      java.util.Enumeration<TableAssemblyOperator> iter =
         (java.util.Enumeration<TableAssemblyOperator>) join.getOperators();
      assertTrue(iter.hasMoreElements());
      TableAssemblyOperator top = iter.nextElement();
      assertEquals(TableAssemblyOperator.LEFT_JOIN, top.getOperator(0).getOperation(),
         "editing to a LEFT join must succeed even though CROSS_JOIN permission is denied");
      verify(securityEngine, never()).checkPermission(
         eq(agent), eq(ResourceType.CROSS_JOIN), anyString(), any(ResourceAction.class));
   }
}
