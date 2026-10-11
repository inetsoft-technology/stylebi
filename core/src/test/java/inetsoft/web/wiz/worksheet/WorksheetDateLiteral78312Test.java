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
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
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
 * Bug #78312: unparseable date/time/timeInstant literals on worksheet condition writes are
 * refused, not stored as today/now (or as a different, garbage date). Evaluated on the rows,
 * same fixture shape as {@link WorksheetNumericLiteral78284Test}.
 */
@WizAgentTestSupport
class WorksheetDateLiteral78312Test {
   private static final Object[][] DATA = {
      { "A", "B", "S1", "S2", "D1", "D2", "X" },
      { 1, 1, "a", "a", date("2024-01-01"), date("2024-01-01"), 1.5 },
      { 2, 3, "b", "c", date("2024-03-01"), date("2024-04-01"), 3.5 },
      { 5, 5, "e", "e", date("2024-05-01"), date("2024-05-01"), 1000.0 },
      { 7, 2, "x", "y", date("2024-07-01"), date("2024-02-01"), 0.5 },
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
                            "D2:date", "X:double", "T1:time", "TS:timeInstant" };
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


   private static final String[][] TYPES = {
      { "D1", "date" }, { "T1", "time" }, { "TS", "timeInstant" } };

   private interface Call {
      void run() throws Exception;
   }

   private static void refused(Call c, String field, String type, String bad) {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, c::run);
      assertTrue(ex.getMessage().contains("is not a valid " + type + " for " + field) &&
                 ex.getMessage().contains("\"" + bad + "\""), ex.getMessage());
   }

   @Test
   void addFilterAndEditConditionRefuseUnparseableLiterals() throws Exception {
      fixture();
      String[][] cases = {
         { "D1", "date", "abc" }, { "D1", "date", "2026-13-45" }, { "D1", "date", "" },
         { "D1", "date", "  " }, { "D1", "date", "1" }, { "D1", "date", "12" },
         { "D1", "date", "{garbage}" }, { "D1", "date", "10:30" },
         { "D1", "date", "{t '10:30:00'}" }, { "D1", "date", "20240115" },
         { "D1", "date", "2024-01" },
         { "TS", "timeInstant", "abc" }, { "TS", "timeInstant", "10:30:00" },
         { "TS", "timeInstant", "12" }, { "TS", "timeInstant", "{garbage}" },
         { "T1", "time", "abc" }, { "T1", "time", "abc:def" }, { "T1", "time", "10:30:00 AM" },
         { "T1", "time", "2024-01-15" }, { "T1", "time", "{d '2024-01-15'}" },
      };

      for(String[] c : cases) {
         refused(() -> svc.apply("TOK", agent, ed -> ed.addFilter("M", c[0], "=", c[2])),
                 c[0], c[1], c[2]);
         refused(() -> svc.apply("TOK", agent, ed -> ed.addFilter("M", c[0], "like", c[2])),
                 c[0], c[1], c[2]);
      }

      assertTrue(m.getPreConditionList() == null || m.getPreConditionList().isEmpty());
      svc.apply("TOK", agent, ed -> ed.addFilter("M", "D1", ">", "2024-02-01"));
      assertEquals(List.of(2, 5, 7), rows(m.getPreConditionList()));
      refused(() -> svc.apply("TOK", agent, ed -> ed.editCondition("M", "D1", ">", "abc")),
              "D1", "date", "abc");
      assertEquals(List.of(2, 5, 7), rows(m.getPreConditionList()));
   }

   @Test
   void setConditionsTypedUntypedMvAndMultiValueRefuse() throws Exception {
      fixture();

      for(String[] t : TYPES) {
         refused(() -> svc.apply("TOK", agent,
            ed -> ed.setConditions("M", List.of(cond(t[0], "=", 0, "abc")))), t[0], t[1], "abc");
         refused(() -> svc.apply("TOK", agent,
            ed -> ed.setConditions("M", List.of(cond(t[0], "one_of", 0, "T1".equals(t[0]) ? "10:30:00" : "2024-01-01", "abc")))),
            t[0], t[1], "abc");
         refused(() -> svc.apply("TOK", agent,
            ed -> ed.setConditions("M", List.of(new ConditionNode(
               new ConditionSpec(t[0], "=", List.of("abc"), false, t[1]), null, 0)))),
            t[0], t[1], "abc");
         refused(() -> svc.apply("TOK", agent,
            ed -> ed.setMVConditions("M", List.of(cond(t[0], "=", 0, "abc")), null, null, null,
                                     null)), t[0], t[1], "abc");
      }

      refused(() -> svc.apply("TOK", agent,
         ed -> ed.setConditions("M", List.of(cond("D1", "between", 0, "2024-01-01", "x")))),
         "D1", "date", "x");
      assertTrue(m.getPreConditionList() == null || m.getPreConditionList().isEmpty());
   }

   @Test
   void setPostConditionsRefuses() throws Exception {
      fixture();
      refused(() -> svc.apply("TOK", agent,
         ed -> ed.setPostConditions("M", List.of(cond("D1", "=", 0, "abc")))), "D1", "date", "abc");
   }

   @Test
   void namedGroupMappingsRefuseUnparseableDates() throws Exception {
      fixture();
      ColumnRef ref = new ColumnRef(new AttributeRef(null, "D1"));
      ref.setDataType(XSchema.DATE);

      for(String op : new String[] { null, "one_of", "between" }) {
         List<String> vals = "between".equals(op) ? List.of("2024-01-01", "abc") : List.of("abc");
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            WorksheetMutationSupport.buildGroupConditionList(
               XSchema.DATE, ref, new WorksheetMutationSupport.GroupMapping("G", vals, op), ws));
         assertTrue(ex.getMessage().contains("is not a valid date for G"), ex.getMessage());
      }

      assertNotNull(WorksheetMutationSupport.buildGroupConditionList(
         XSchema.DATE, ref,
         new WorksheetMutationSupport.GroupMapping("G", List.of("2024-01-01", "{d '2024-02-01'}")),
         ws));
   }

   @Test
   void honestFormsAreAccepted() throws Exception {
      fixture();
      String[][] ok = {
         { "D1", "2024-05-01" }, { "D1", " 2024-05-01 " }, { "D1", "{d '2024-05-01'}" },
         { "D1", "$(Floor)" }, { "D1", "2024-05-01 10:00:00" },
         { "TS", "{ts '2024-05-01 10:00:00'}" }, { "TS", "2024-05-01 10:00:00" },
         { "TS", "2024-05-01" }, { "T1", "10:30:00" }, { "T1", "{t '10:30:00'}" },
      };

      for(String[] c : ok) {
         svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(cond(c[0], "=", 0, c[1]))));
      }

      svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(cond("D1", "is_null", 0))));
      svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(
         cond("D1", "date_in", 0, "Last month"))));
   }

   @Test
   void paddedDateIsStoredTrimmedAndFilters() throws Exception {
      fixture();
      svc.apply("TOK", agent, ed -> ed.addFilter("M", "D1", "=", " 2024-05-01 "));
      assertEquals(List.of(5), rows(m.getPreConditionList()));
   }

   @Test
   void fieldValueSpecStaysAccepted() throws Exception {
      fixture();
      svc.apply("TOK", agent, ed -> ed.setConditions("M", List.of(new ConditionNode(
         new ConditionSpec("D1", "=", List.of("T.D2"), false, null,
                           List.of(fieldSpec("T.D2", 0))), null, 0))));
      assertEquals(List.of(1, 5), rows(m.getPreConditionList()));
   }

   @Test
   void numericGuardUnaffected() throws Exception {
      fixture();
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed -> ed.addFilter("M", "B", "=", "abc")));
      assertTrue(ex.getMessage().contains("is not a valid integer"), ex.getMessage());
   }
}
