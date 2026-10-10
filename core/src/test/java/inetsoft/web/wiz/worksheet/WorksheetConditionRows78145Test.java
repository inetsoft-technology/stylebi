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
import inetsoft.report.filter.ConditionGroup;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.ConditionListWrapper;
import inetsoft.uql.asset.*;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.ConditionNode;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.ConditionSpec;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.ConditionValueSpec;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.JunctionSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78145: the condition writers checked on the reporter's CSV fixture by EVALUATING the
 * stored condition list on the rows (A,B integer; S1,S2 string; D1,D2 date), not only by its
 * structure.
 */
@WizAgentTestSupport
class WorksheetConditionRows78145Test {
   private static final Object[][] DATA = {
      { "A", "B", "S1", "S2", "D1", "D2" },
      { 1, 1, "a", "a", date("2024-01-01"), date("2024-01-01") },
      { 2, 3, "b", "c", date("2024-03-01"), date("2024-04-01") },
      { 5, 5, "e", "e", date("2024-05-01"), date("2024-05-01") },
      { 7, 2, "x", "y", date("2024-07-01"), date("2024-02-01") },
   };

   private static java.sql.Date date(String s) {
      return java.sql.Date.valueOf(s);
   }

   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private Worksheet ws;
   private WorksheetEditService svc;
   private TableAssembly m;

   private void fixture() throws Exception {
      ws = new Worksheet();
      String[] colTypes = { "A:integer", "B:integer", "S1:string", "S2:string", "D1:date",
                            "D2:date" };
      String[] cols = java.util.Arrays.stream(colTypes).map(c -> c.split(":")[0])
         .toArray(String[]::new);
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", cols);

      for(String colType : colTypes) {
         String[] parts = colType.split(":");
         ((ColumnRef) t.getColumnSelection(false).getAttribute(parts[0])).setDataType(parts[1]);

         if(t.getColumnSelection(true).getAttribute(parts[0]) instanceof ColumnRef pub) {
            pub.setDataType(parts[1]);
         }
      }

      ws.addAssembly(t);
      svc = service(ws);
      svc.apply("TOK", agent, ed -> ed.addMirror("M", "T"));
      m = (TableAssembly) ws.getAssembly("M");
   }

   private WorksheetEditService service(Worksheet ws) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = new JoinSession("TOK", "Worksheet/ws1", "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq("Worksheet/ws1"),
                                            eq(agent))).thenReturn(rws);
      return new WorksheetEditService(sessions, runtimeAccess,
                                      mock(SheetAgentBroadcastService.class), securityEngine,
                                      mock(InnerJoinService.class));
   }

   /** The A values of the fixture rows the list keeps. */
   private static List<Integer> rows(ConditionListWrapper wrapper) {
      DefaultTableLens lens = new DefaultTableLens(DATA);
      lens.setHeaderRowCount(1);
      List<Integer> result = new ArrayList<>();

      for(int r = 1; r < DATA.length; r++) {
         if(wrapper == null || wrapper.isEmpty() ||
            new ConditionGroup(lens, wrapper.getConditionList()).evaluate(lens, r))
         {
            result.add((Integer) DATA[r][0]);
         }
      }

      return result;
   }

   private static ConditionNode cond(String field, String op, int level, String... values) {
      return new ConditionNode(new ConditionSpec(field, op, List.of(values), false, null),
                               null, level);
   }

   private static ConditionNode specCond(String field, String op, List<String> values,
                                         ConditionValueSpec... specs)
   {
      return new ConditionNode(
         new ConditionSpec(field, op, values, false, null, List.of(specs)), null, 0);
   }

   private static ConditionValueSpec fieldSpec(String field, Integer index) {
      return new ConditionValueSpec("field", field, null, null, index);
   }

   private static ConditionNode junction(String j, int level) {
      return new ConditionNode(null, new JunctionSpec(j, level), level);
   }

   /** Sanity: the evaluator itself (plain literals, AND, OR). */
   @Test
   void evaluatorSanity() throws Exception {
      fixture();
      svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(
         cond("A", "=", 0, "1"), junction("OR", 0), cond("B", ">", 0, "3"))));
      assertEquals(List.of(1, 5), rows(m.getPreConditionList()));
   }

   /**
    * WSC-007 (string, date, numeric): column = other column via an indexed field valueSpec keeps
    * rows [1,5]; the readback resubmit with the index dropped is refused and the rows stay [1,5].
    */
   @ParameterizedTest
   @CsvSource({ "S1,T.S2", "D1,T.D2", "A,T.B" })
   void leftoverResubmitIsRefusedAndRowsStay(String field, String other) throws Exception {
      fixture();
      svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(
         specCond(field, "=", List.of(other), fieldSpec(other, 0)))));
      assertEquals(List.of(1, 5), rows(m.getPreConditionList()));

      assertThrows(IllegalArgumentException.class, () -> svc.apply("TOK", agent,
         ed -> ed.setConditions("M", List.of(
            specCond(field, "=", List.of(other), fieldSpec(other, null))))));
      assertThrows(IllegalArgumentException.class, () -> svc.apply("TOK", agent,
         ed -> ed.setConditions("M", List.of(
            specCond(field, "=", List.of(other, other), fieldSpec(other, null))))));
      assertEquals(List.of(1, 5), rows(m.getPreConditionList()));
   }

   /** WSC-008 + AC-1: edit_condition/add_filter with values ["T.B"] are refused, nothing changes. */
   @Test
   void editConditionAndAddFilterRefuseFieldTextAndKeepRows() throws Exception {
      fixture();
      svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(
         specCond("A", "=", List.of("T.B"), fieldSpec("T.B", 0)))));
      assertEquals(List.of(1, 5), rows(m.getPreConditionList()));

      assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed -> ed.editCondition("M", "A", "=", "T.B")));
      assertEquals(List.of(1, 5), rows(m.getPreConditionList()));

      svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of()));
      assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed -> ed.addFilter("M", "A", "=", "T.B")));
      assertTrue(m.getPreConditionList() == null || m.getPreConditionList().isEmpty());
   }

   /** WSC-010a: A=1 OR B=2, edit B>3 -> A=1 OR B>3 = rows [1,5] (was A=1 AND B>3 = []). */
   @Test
   void editConditionKeepsOrOnRows() throws Exception {
      fixture();
      svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(
         cond("A", "=", 0, "1"), junction("OR", 0), cond("B", "=", 0, "2"))));
      assertEquals(List.of(1, 7), rows(m.getPreConditionList()));

      svc.apply("TOK", agent, ed -> ed.editCondition("M", "B", ">", "3"));
      assertEquals(List.of(1, 5), rows(m.getPreConditionList()));
   }

   /**
    * WSC-010b: (A=1 AND B=1) OR A=7. Editing B to >=1 gives (A=1 AND B>=1) OR A=7 = [1,7] (the
    * old regroup A=1 AND A=7 AND B... gave []). And editing B>1 gives [7].
    */
   @Test
   void editConditionKeepsNestedGroupOnRows() throws Exception {
      fixture();
      svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(
         cond("A", "=", 1, "1"), junction("AND", 1), cond("B", "=", 1, "1"),
         junction("OR", 0), cond("A", "=", 0, "7"))));
      assertEquals(List.of(1, 7), rows(m.getPreConditionList()));

      svc.apply("TOK", agent, ed -> ed.editCondition("M", "B", ">=", "1"));
      assertEquals(List.of(1, 7), rows(m.getPreConditionList()));

      svc.apply("TOK", agent, ed -> ed.editCondition("M", "B", ">", "1"));
      assertEquals(List.of(7), rows(m.getPreConditionList()));

      // A has two conditions: refused, rows unchanged.
      assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed -> ed.editCondition("M", "A", "=", "2")));
      assertEquals(List.of(7), rows(m.getPreConditionList()));
   }

   /** C-2: remove_filter on a mixed AND/OR list keeps the OR between the surviving groups. */
   @Test
   void removeFilterOnMixedAndOrKeepsRows() throws Exception {
      fixture();
      svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(
         cond("A", "=", 1, "1"), junction("AND", 1), cond("B", "=", 1, "1"),
         junction("OR", 0),
         cond("B", "=", 1, "2"), junction("AND", 1), cond("S1", "=", 1, "x"))));
      assertEquals(List.of(1, 7), rows(m.getPreConditionList()));

      svc.apply("TOK", agent, ed -> ed.removeFilter("M", "B"));
      // A=1 OR S1=x
      assertEquals(List.of(1, 7), rows(m.getPreConditionList()));

      // (A=1 AND B=1) OR A=7, remove B -> A=1 OR A=7 (old rule: A=1 AND A=7 = []).
      svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(
         cond("A", "=", 1, "1"), junction("AND", 1), cond("B", "=", 1, "1"),
         junction("OR", 0), cond("A", "=", 0, "7"))));
      svc.apply("TOK", agent, ed -> ed.removeFilter("M", "B"));
      assertEquals(List.of(1, 7), rows(m.getPreConditionList()));
   }

   /** WSC-011: duplicate replace index refused; C-1 append window accepted in order. */
   @Test
   void duplicateIndexRefusedAppendWindowAccepted() throws Exception {
      fixture();
      svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(
         cond("A", "=", 0, "7"))));

      assertThrows(IllegalArgumentException.class, () -> svc.apply("TOK", agent,
         ed -> ed.setConditions("M", List.of(specCond("A", "ONE_OF", List.of("5", "9"),
            fieldSpec("T.B", 0), fieldSpec("T.A", 0))))));
      assertEquals(List.of(7), rows(m.getPreConditionList()));

      // values ["9"], append B at 1 and then 2 (consecutive appends, accepted today): A IN (9,B,B)
      svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(specCond("A", "ONE_OF",
         List.of("9"), fieldSpec("T.B", 1), fieldSpec("T.B", 2)))));
      assertEquals(3, ((inetsoft.uql.Condition) ((inetsoft.uql.ConditionItem)
         m.getPreConditionList().getConditionList().getItem(0)).getXCondition()).getValueCount());
      assertEquals(List.of(1, 5), rows(m.getPreConditionList()));

      // Mixed: index-less append then an index-1 append must both survive: A IN (2, B, B).
      svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(specCond("A", "ONE_OF",
         List.of("2"), fieldSpec("T.B", null), fieldSpec("T.B", 1)))));
      assertEquals(List.of(1, 2, 5), rows(m.getPreConditionList()));

      // Out of the window refused.
      assertThrows(IllegalArgumentException.class, () -> svc.apply("TOK", agent,
         ed -> ed.setConditions("M", List.of(specCond("A", "ONE_OF", List.of("9"),
            fieldSpec("T.B", 3))))));
      assertEquals(List.of(1, 2, 5), rows(m.getPreConditionList()));
   }

   /** WSC-012: empty MV lists, then the table still clones/serializes and set_conditions works. */
   @Test
   void emptyMvConditionsThenTableStillWorks() throws Exception {
      fixture();
      svc.apply("TOK", agent, ed -> ed.setMVConditions("M", List.of(), List.of(), List.of(),
                                                        List.of(), null));
      assertNotNull(m.getMVUpdatePreConditionList());
      assertNotNull(m.clone());
      java.io.StringWriter sw = new java.io.StringWriter();
      m.writeXML(new java.io.PrintWriter(sw));
      assertTrue(sw.toString().length() > 0);
      Worksheet copy = (Worksheet) ws.clone();
      assertNotNull(copy.getAssembly("M"));

      svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(cond("A", "=", 0, "7"))));
      assertEquals(List.of(7), rows(m.getPreConditionList()));
   }

   /** AC-2: a refusal in a later MV list leaves the earlier list (and its rows) unchanged. */
   @Test
   void refusedMvConditionsAreZeroMutation() throws Exception {
      fixture();
      svc.apply("TOK", agent, ed -> ed.setMVConditions("M", List.of(cond("A", "=", 0, "7")),
                                                        null, null, null, null));
      assertEquals(List.of(7), rows(m.getMVUpdatePreConditionList()));

      assertThrows(IllegalArgumentException.class, () -> svc.apply("TOK", agent,
         ed -> ed.setMVConditions("M", List.of(cond("A", "=", 0, "1")), null,
                                  List.of(cond("A", "bogus", 0, "1")), null, null)));
      assertEquals(List.of(7), rows(m.getMVUpdatePreConditionList()));
      assertTrue(m.getMVDeletePreConditionList() == null ||
                 m.getMVDeletePreConditionList().isEmpty());
   }

   /**
    * WSC-013, backend half: the request the plugin now lets through (type "integer", values
    * ["T.B"], valueSpecs [{field T.B, index 0}]) is accepted and filters A = B, rows [1,5].
    */
   @Test
   void typedIntegerWithIndexedFieldSpecFiltersRows() throws Exception {
      fixture();
      svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(new ConditionNode(
         new ConditionSpec("A", "=", List.of("T.B"), false, "integer",
                           List.of(fieldSpec("T.B", 0))), null, 0))));
      assertEquals(List.of(1, 5), rows(m.getPreConditionList()));
   }
}
