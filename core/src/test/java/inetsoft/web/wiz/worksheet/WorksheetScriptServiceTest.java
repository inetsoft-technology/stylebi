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
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.script.ScriptTarget;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * G2 Task 8: {@link WorksheetScriptService} is the front door that lands
 * {@code worksheetExpression}/{@code worksheetCondition} on
 * {@link WorksheetAgentController}'s existing {@code edit_expression}/{@code edit_condition}
 * ops. Every write here must (1) be refused for a whole-sheet session -- these two kinds have
 * no identity outside their own pane, see {@code PaneScopeServiceTest} -- and (2) go through
 * {@link WorksheetAgentController#edit} and nothing else, so undo/redo and the refresh broadcast
 * stay coherent with every other worksheet mutation.
 */
@WizAgentTestSupport
class WorksheetScriptServiceTest {

   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private final WorksheetEditService editService = mock(WorksheetEditService.class);
   private final WorksheetAgentController worksheetController =
      mock(WorksheetAgentController.class);
   private final WorksheetScriptService service =
      new WorksheetScriptService(editService, worksheetController);

   // ---------------------------------------------------------------------------
   // Fixtures
   // ---------------------------------------------------------------------------

   private static JoinSession sessionScopedTo(EditorContext ctx) {
      return new JoinSession("TOK", "Worksheet/ws-1", "alice~;~host-org", SheetType.WORKSHEET,
         0L, Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, ctx);
   }

   private static JoinSession wholeSheetSession() {
      return new JoinSession("TOK", "Worksheet/ws-1", "alice~;~host-org", SheetType.WORKSHEET,
         0L, Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, null);
   }

   /** A worksheet with one table carrying a single existing expression column. */
   private void seedExpressionColumn(String table, String name, String expression, boolean sql)
      throws PairingException
   {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, table, "price", "cost");
      ws.addAssembly(t);
      WorksheetMutationSupport.addExpressionColumn(t, name, expression, "double", sql);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(editService.resolve("TOK", agent)).thenReturn(rws);
   }

   // ---------------------------------------------------------------------------
   // worksheetExpression
   // ---------------------------------------------------------------------------

   @Test
   void writesAnExpressionThroughTheWorksheetEditOp() throws Exception {
      seedExpressionColumn("Query1", "Margin", "field['price']", false);
      JoinSession session =
         sessionScopedTo(new EditorContext("worksheetExpression", "Query1", "Margin", null));
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_EXPRESSION, "Query1", "Margin");

      service.write(session, agent, target, "field['price'] - field['cost']");

      ArgumentCaptor<EditRequest> captor = ArgumentCaptor.forClass(EditRequest.class);
      verify(worksheetController).edit(eq("TOK"), captor.capture(), eq(agent));
      EditRequest req = captor.getValue();
      assertEquals("edit_expression", req.op());
      assertEquals("Query1", req.table());
      assertEquals("Margin", req.name());
      assertEquals("field['price'] - field['cost']", req.expression());
      assertNull(req.type(), "type must never change through this pane -- null means unchanged");
   }

   /**
    * The existing column's {@code sql} flag must be carried through untouched -- {@code sql} is
    * a required primitive on {@code editExpression}, so if this pane silently defaulted it
    * instead of reading the current value, every edit of a SQL expression column would flip it
    * to script (or vice versa).
    */
   @Test
   void preservesTheExistingColumnsSqlFlag() throws Exception {
      seedExpressionColumn("Query1", "Margin", "price + cost", true);
      JoinSession session =
         sessionScopedTo(new EditorContext("worksheetExpression", "Query1", "Margin", null));
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_EXPRESSION, "Query1", "Margin");

      service.write(session, agent, target, "price - cost");

      ArgumentCaptor<EditRequest> captor = ArgumentCaptor.forClass(EditRequest.class);
      verify(worksheetController).edit(eq("TOK"), captor.capture(), eq(agent));
      assertTrue(captor.getValue().sql(), "a SQL expression column must stay SQL through a text-only edit");
   }

   @Test
   void refusesToChangeTheColumnType() throws Exception {
      JoinSession session =
         sessionScopedTo(new EditorContext("worksheetExpression", "Query1", "Margin", null));
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_EXPRESSION, "Query1", "Margin");

      PairingException ex = assertThrows(PairingException.class,
         () -> service.write(session, agent, target, "x", Map.of("type", "string")));

      assertTrue(ex.getMessage().contains("worksheet-chat"), ex.getMessage());
      verifyNoInteractions(worksheetController);
   }

   @Test
   void refusesToCreateANewExpressionColumnThroughThisPane() throws Exception {
      seedExpressionColumn("Query1", "Margin", "field['price']", false);
      JoinSession session =
         sessionScopedTo(new EditorContext("worksheetExpression", "Query1", "NewCol", null));
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_EXPRESSION, "Query1", "NewCol");

      PairingException ex = assertThrows(PairingException.class,
         () -> service.write(session, agent, target, "field['price']"));

      assertTrue(ex.getMessage().contains("add_expression_column"), ex.getMessage());
      verifyNoInteractions(worksheetController);
   }

   // ---------------------------------------------------------------------------
   // worksheetCondition
   // ---------------------------------------------------------------------------

   @Test
   void writesAConditionThroughTheWorksheetEditOp() throws Exception {
      JoinSession session =
         sessionScopedTo(new EditorContext("worksheetCondition", "Query1", "Price", null));
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_CONDITION, "Query1", "Price");

      service.write(session, agent, target, "> 0");

      ArgumentCaptor<EditRequest> captor = ArgumentCaptor.forClass(EditRequest.class);
      verify(worksheetController).edit(eq("TOK"), captor.capture(), eq(agent));
      EditRequest req = captor.getValue();
      assertEquals("edit_condition", req.op());
      assertEquals("Query1", req.table());
      assertEquals("Price", req.field());
      assertEquals(">", req.operation());
      assertEquals(List.of("0"), req.values());
   }

   @Test
   void parsesBetweenIntoTwoValues() throws Exception {
      JoinSession session =
         sessionScopedTo(new EditorContext("worksheetCondition", "Query1", "Price", null));
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_CONDITION, "Query1", "Price");

      service.write(session, agent, target, "BETWEEN 1 AND 10");

      ArgumentCaptor<EditRequest> captor = ArgumentCaptor.forClass(EditRequest.class);
      verify(worksheetController).edit(eq("TOK"), captor.capture(), eq(agent));
      assertEquals("BETWEEN", captor.getValue().operation());
      assertEquals(List.of("1", "10"), captor.getValue().values());
   }

   @Test
   void parsesOneOfIntoACommaSeparatedList() throws Exception {
      JoinSession session =
         sessionScopedTo(new EditorContext("worksheetCondition", "Query1", "Region", null));
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_CONDITION, "Query1", "Region");

      service.write(session, agent, target, "ONE_OF East, West, North");

      ArgumentCaptor<EditRequest> captor = ArgumentCaptor.forClass(EditRequest.class);
      verify(worksheetController).edit(eq("TOK"), captor.capture(), eq(agent));
      assertEquals("ONE_OF", captor.getValue().operation());
      assertEquals(List.of("East", "West", "North"), captor.getValue().values());
   }

   @Test
   void nullOperatorNeedsNoValue() throws Exception {
      JoinSession session =
         sessionScopedTo(new EditorContext("worksheetCondition", "Query1", "Price", null));
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_CONDITION, "Query1", "Price");

      service.write(session, agent, target, "NULL");

      ArgumentCaptor<EditRequest> captor = ArgumentCaptor.forClass(EditRequest.class);
      verify(worksheetController).edit(eq("TOK"), captor.capture(), eq(agent));
      assertEquals("NULL", captor.getValue().operation());
      assertEquals(List.of(), captor.getValue().values());
   }

   @Test
   void refusesAnUnrecognizedConditionOperatorWithARedirect() throws Exception {
      JoinSession session =
         sessionScopedTo(new EditorContext("worksheetCondition", "Query1", "Price", null));
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_CONDITION, "Query1", "Price");

      PairingException ex = assertThrows(PairingException.class,
         () -> service.write(session, agent, target, "EQUALS 5"));

      assertTrue(ex.getMessage().contains("set_conditions"), ex.getMessage());
      verifyNoInteractions(worksheetController);
   }

   /**
    * The mutation must come from {@code editCondition} alone -- this pins that
    * {@link WorksheetScriptService} never resolves or touches the worksheet runtime itself for a
    * condition write (unlike worksheetExpression, which legitimately reads current state to
    * preserve {@code sql} -- see {@link #preservesTheExistingColumnsSqlFlag}).
    */
   @Test
   void doesNotReimplementTheWriter() throws Exception {
      JoinSession session =
         sessionScopedTo(new EditorContext("worksheetCondition", "Query1", "Price", null));
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_CONDITION, "Query1", "Price");

      service.write(session, agent, target, "> 0");

      verify(worksheetController).edit(eq("TOK"), any(EditRequest.class), eq(agent));
      verifyNoMoreInteractions(worksheetController);
      verifyNoInteractions(editService);
   }

   // ---------------------------------------------------------------------------
   // Pane-scope enforcement -- the point of the exercise
   // ---------------------------------------------------------------------------

   @Test
   void aWholeSheetSessionMayNotWriteAWorksheetExpression() throws Exception {
      JoinSession session = wholeSheetSession();
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_EXPRESSION, "Query1", "Margin");

      PairingException ex = assertThrows(PairingException.class,
         () -> service.write(session, agent, target, "field['price']"));

      assertTrue(ex.getMessage().contains("editor"), ex.getMessage());
      verifyNoInteractions(worksheetController);
   }

   @Test
   void aWholeSheetSessionMayNotWriteAWorksheetCondition() throws Exception {
      JoinSession session = wholeSheetSession();
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_CONDITION, "Query1", "Price");

      assertThrows(PairingException.class,
         () -> service.write(session, agent, target, "> 0"));

      verifyNoInteractions(worksheetController);
   }

   @Test
   void aPaneSessionMayNotWriteADifferentColumnsExpression() throws Exception {
      JoinSession session =
         sessionScopedTo(new EditorContext("worksheetExpression", "Query1", "Margin", null));
      ScriptTarget otherColumn =
         ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_EXPRESSION, "Query1", "TaxRate");

      assertThrows(PairingException.class,
         () -> service.write(session, agent, otherColumn, "field['price']"));

      verifyNoInteractions(worksheetController);
   }

   @Test
   void refusesASessionJoinedToTheWrongSheetType() throws Exception {
      JoinSession viewsheetSession = new JoinSession("TOK", "Viewsheet/vs-1", "alice~;~host-org",
         SheetType.VIEWSHEET, 0L, Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null,
         new EditorContext("worksheetExpression", "Query1", "Margin", null));
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_EXPRESSION, "Query1", "Margin");

      assertThrows(PairingException.class,
         () -> service.write(viewsheetSession, agent, target, "field['price']"));

      verifyNoInteractions(worksheetController);
   }

   @Test
   void refusesATargetThisServiceDoesNotServe() throws Exception {
      JoinSession session = wholeSheetSession();
      ScriptTarget calcField = ScriptTarget.of(ScriptTarget.Kind.CALC_FIELD, "Query1", "Margin");

      PairingException ex = assertThrows(PairingException.class,
         () -> service.write(session, agent, calcField, "field['price']"));

      assertTrue(ex.getMessage().contains("calcField"), ex.getMessage());
      verifyNoInteractions(worksheetController);
   }
}
