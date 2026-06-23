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
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.*;

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
                                        Principal agent, String token) throws PairingException
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

      return new WorksheetEditService(sessions, runtimeAccess, broadcast);
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
         ed -> ed.addJoin("J", "L", "id", "R", "id", "INNER"));

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
         ed.addJoin("J", "L", "id", "R", "id", "LEFT");
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

      svc.apply("TOK", agent, ed -> ed.addJoin("J", "L", "id", "R", "id", "INNER"));
      svc.apply("TOK", agent, ed -> ed.editJoin("J", "altId", "altId", "LEFT"));

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
         () -> svc.apply("TOK", agent, ed -> ed.editJoin("NOPE", "a", "b", "INNER")));
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
}
