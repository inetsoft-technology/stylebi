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
 * Bug #78284: unparseable numeric literals on worksheet condition writes are refused, not stored
 * as 0. Same CSV fixture as 78145 (A,B integer),evaluated on the rows (A,B integer; S1,S2 string; D1,D2 date), not only by its
 * structure.
 */
@WizAgentTestSupport
class WorksheetNumericLiteral78284Test {
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

   private static final String[] BAD = { "abc", "", "  ", "4x" };

   @Test
   void addFilterAndEditConditionRefuseNonNumeric() throws Exception {
      fixture();

      for(String bad : BAD) {
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            svc.apply("TOK", agent, ed -> ed.addFilter("M", "B", "=", bad)));
         assertTrue(ex.getMessage().contains("is not a valid integer for column B"),
                    ex.getMessage());
      }

      assertTrue(m.getPreConditionList() == null || m.getPreConditionList().isEmpty());
      svc.apply("TOK", agent, ed -> ed.addFilter("M", "B", ">", "2"));
      assertEquals(List.of(2, 5), rows(m.getPreConditionList()));
      assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed -> ed.editCondition("M", "B", ">", "abc")));
      assertEquals(List.of(2, 5), rows(m.getPreConditionList()));
   }

   @Test
   void untypedSetConditionsAndMvRefuseNonNumeric() throws Exception {
      fixture();
      assertThrows(IllegalArgumentException.class, () -> svc.apply("TOK", agent,
         ed -> ed.setConditions("M", List.of(cond("B", "=", 0, "abc")))));
      assertThrows(IllegalArgumentException.class, () -> svc.apply("TOK", agent,
         ed -> ed.setConditions("M", List.of(cond("B", "one_of", 0, "1", "abc")))));
      assertThrows(IllegalArgumentException.class, () -> svc.apply("TOK", agent,
         ed -> ed.setMVConditions("M", List.of(cond("B", "=", 0, "abc")), null, null, null,
                                  null)));
      assertTrue(m.getPreConditionList() == null || m.getPreConditionList().isEmpty());
   }

   @Test
   void validNumericLiteralsAndVariablesStayAccepted() throws Exception {
      fixture();

      for(String ok : new String[] { "2", "3.5", " 2 ", "1e3", "$(Floor)" }) {
         svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(cond("B", "=", 0, ok))));
      }

      svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(cond("B", "is_null", 0))));
   }

   @Test
   void readbackResubmitWithIndexedValueSpecStillAccepted() throws Exception {
      fixture();
      svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(new ConditionNode(
         new ConditionSpec("A", "=", List.of("T.B"), false, null,
                           List.of(fieldSpec("T.B", 0))), null, 0))));
      assertEquals(List.of(1, 5), rows(m.getPreConditionList()));
   }

   @Test
   void fieldReferenceLiteralKeepsItsOwnMessage() throws Exception {
      fixture();
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed -> ed.addFilter("M", "A", "=", "T.B")));
      assertFalse(ex.getMessage().contains("is not a valid integer"), ex.getMessage());
   }
}
