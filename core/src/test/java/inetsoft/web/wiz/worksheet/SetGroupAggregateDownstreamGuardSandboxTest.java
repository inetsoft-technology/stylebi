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
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionList;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.*;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78144 (WBS-096..101): every reported symptom, checked through the real
 * {@code apply} path with a REAL {@link AssetQuerySandbox}, so a refused call's leftover state
 * is published by the next unrelated successful op ("trigger") exactly as in the report.
 */
@Tag("core")
@WizAgentTestSupport
class SetGroupAggregateDownstreamGuardSandboxTest {
   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private Worksheet ws;
   private WorksheetEditService svc;
   private int triggers;

   @BeforeEach
   void setUp() throws Exception {
      ws = new Worksheet();
      ws.addAssembly(orders(ws, "U"));
      ws.addAssembly(orders(ws, "Z"));
      svc = service(ws);
   }

   // ---- WBS-096 -----------------------------------------------------------

   @Test
   void wbs096BothValidationRefusalsLeaveAliasAndDownstreamSumIntactAfterATrigger()
      throws Exception
   {
      apply(ed -> ed.setGroupAggregate("U", groups("pid"), List.of(sum("qty", "TOTAL"))));
      apply(ed -> ed.addMirror("M2", "U"));
      apply(ed -> ed.setGroupAggregate("M2", List.of(), List.of(sum("TOTAL", null))));
      trigger();
      assertU096Intact("baseline");

      PairingException ex = assertThrows(PairingException.class,
         () -> apply(ed -> ed.setGroupAggregate("U", groups("TOTAL"), List.of())));
      assertTrue(ex.getMessage().contains("not found"), ex.getMessage());
      trigger();
      assertU096Intact("after groups:[TOTAL] refusal + trigger");

      ex = assertThrows(PairingException.class, () -> apply(ed ->
         ed.setGroupAggregate("U", groups("pid"), List.of(sum("NO_SUCH_COL", null)))));
      assertTrue(ex.getMessage().contains("not found"), ex.getMessage());
      trigger();
      assertU096Intact("after aggregates:[NO_SUCH_COL] refusal + trigger");
   }

   private void assertU096Intact(String label) {
      TableAssembly u = table("U");
      assertEquals("TOTAL", col(u, "qty").getAlias(), label + ": U's qty alias");
      assertNotNull(u.getColumnSelection(true).getAttribute("TOTAL"),
         label + ": U publishes TOTAL");
      AggregateInfo m2 = table("M2").getAggregateInfo();
      assertEquals(1, m2.getAggregateCount(), label + ": M2's Sum(TOTAL)");
      assertEquals("TOTAL", m2.getAggregate(0).getDataRef().getAttribute(), label);
   }

   // ---- WBS-097 -----------------------------------------------------------

   @Test
   void wbs097aRefusedDateLevelRegroupAddsNoMonthColumnAfterATrigger() throws Exception {
      apply(ed -> ed.addMirror("DEP", "U"));
      apply(ed -> ed.setGroupAggregate("DEP", groups("pid"), List.of(sum("qty", null))));
      int before = table("U").getColumnSelection(false).getAttributeCount();

      PairingException ex = assertThrows(PairingException.class, () -> apply(ed ->
         ed.setGroupAggregate("U", List.of(new WorksheetMutationSupport.GroupSpec("od", "MONTH")),
            List.of())));
      assertTrue(ex.getMessage().contains("DEP"), ex.getMessage());
      trigger();

      TableAssembly u = table("U");
      assertEquals(before, u.getColumnSelection(false).getAttributeCount(),
         "no column added to U");
      assertNull(rangeColumn(u.getColumnSelection(false)), "no Month(od) in U (private)");
      assertNull(rangeColumn(u.getColumnSelection(true)), "no Month(od) in U (public)");
      assertTrue(u.getAggregateInfo().isEmpty());
      assertEquals(1, table("DEP").getAggregateInfo().getGroupCount());
      assertEquals(1, table("DEP").getAggregateInfo().getAggregateCount());
   }

   @Test
   void wbs097cRefusedRegroupKeepsQuarterFilterAndColumnAfterATrigger() throws Exception {
      apply(ed -> ed.addMirror("Q", "U"));
      apply(ed -> ed.setGroupAggregate("Q",
         List.of(new WorksheetMutationSupport.GroupSpec("od", "QUARTER")),
         List.of(sum("qty", null))));
      String quarter = rangeColumn(table("Q").getColumnSelection(false)).getName();
      apply(ed -> ed.addFilter("Q", quarter, "NOT_NULL"));
      apply(ed -> ed.addMirror("DEP", "Q"));
      apply(ed -> ed.setGroupAggregate("DEP", List.of(), List.of(sum("qty", null))));
      trigger();
      assertEquals(1, ((ConditionList) table("Q").getPreConditionList()).getConditionSize(),
         "sanity: Q keeps its filter through a refresh");

      PairingException ex = assertThrows(PairingException.class,
         () -> apply(ed -> ed.setGroupAggregate("Q", groups("pid"), List.of())));
      assertTrue(ex.getMessage().contains("DEP"), ex.getMessage());
      trigger();

      TableAssembly q = table("Q");
      assertEquals(1, ((ConditionList) q.getPreConditionList()).getConditionSize(),
         "Q's Quarter filter must survive the refused regroup and the trigger");
      DataRef range = rangeColumn(q.getColumnSelection(false));
      assertNotNull(range, "Q's Quarter column must survive");
      assertEquals(quarter, range.getName());
      assertNotNull(q.getColumnSelection(true).getAttribute(quarter), "published too");
      assertEquals(1, q.getAggregateInfo().getGroupCount());
   }

   // ---- WBS-098 -----------------------------------------------------------

   @Test
   void wbs098GroupingTheAliasedAggregateIsRefusedNamingM2() throws Exception {
      apply(ed -> ed.setGroupAggregate("U", groups("pid"), List.of(sum("qty", "TOTAL"))));
      apply(ed -> ed.addMirror("M2", "U"));
      apply(ed -> ed.setGroupAggregate("M2", List.of(), List.of(sum("TOTAL", null))));

      PairingException ex = assertThrows(PairingException.class,
         () -> apply(ed -> ed.setGroupAggregate("U", groups("pid", "qty"), List.of())));
      assertTrue(ex.getMessage().contains("M2"), ex.getMessage());
      trigger();
      assertEquals(1, table("M2").getAggregateInfo().getAggregateCount(), "M2 intact");
      assertEquals("TOTAL", col(table("U"), "qty").getAlias());

      apply(ed -> ed.setGroupAggregate("U", groups("pid", "qty"), List.of(), false, true));
      assertEquals(2, table("U").getAggregateInfo().getGroupCount(), "confirmed:true applies");
   }

   // ---- WBS-099 -----------------------------------------------------------

   @Test
   void wbs099FullClearsThatKeepEveryOutputNameAreNotRefused() throws Exception {
      // S4a: never-aggregated U.
      apply(ed -> ed.addMirror("DEP", "U"));
      apply(ed -> ed.setGroupAggregate("DEP", groups("pid"), List.of(sum("qty", null))));
      apply(ed -> ed.setGroupAggregate("U", List.of(), List.of()));
      trigger();
      assertEquals(1, table("DEP").getAggregateInfo().getGroupCount(), "S4a DEP group");
      assertEquals(1, table("DEP").getAggregateInfo().getAggregateCount(), "S4a DEP sum");

      // S4b: unaliased Sum(qty) on Z, then a full clear.
      apply(ed -> ed.setGroupAggregate("Z", groups("pid"), List.of(sum("qty", null))));
      apply(ed -> ed.addMirror("DEPZ", "Z"));
      apply(ed -> ed.setGroupAggregate("DEPZ", groups("pid"), List.of(sum("qty", null))));
      apply(ed -> ed.setGroupAggregate("Z", List.of(), List.of()));
      trigger();
      assertTrue(table("Z").getAggregateInfo().isEmpty(), "S4b clear applied");
      assertEquals(1, table("DEPZ").getAggregateInfo().getGroupCount(), "S4b DEPZ group");
      assertEquals(1, table("DEPZ").getAggregateInfo().getAggregateCount(), "S4b DEPZ sum");
   }

   // ---- WBS-100 -----------------------------------------------------------

   @Test
   void wbs100CrosstabIsRefusedForTheReportedDependentShape() throws Exception {
      apply(ed -> ed.addMirror("DEP", "U"));
      apply(ed -> ed.setGroupAggregate("DEP", groups("pid"), List.of(sum("qty", null))));

      PairingException ex = assertThrows(PairingException.class, () -> apply(ed ->
         ed.setGroupAggregate("U", groups("pid", "oid"), List.of(sum("qty", null)), true, false)));
      assertTrue(ex.getMessage().contains("DEP"), ex.getMessage());
      trigger();
      assertTrue(table("U").getAggregateInfo().isEmpty(), "crosstab not applied");
      assertEquals(1, table("DEP").getAggregateInfo().getGroupCount(), "DEP group intact");
      assertEquals(1, table("DEP").getAggregateInfo().getAggregateCount(), "DEP sum intact");

      apply(ed -> ed.setGroupAggregate("U", groups("pid", "oid"), List.of(sum("qty", null)),
         true, true));
      assertTrue(table("U").getAggregateInfo().isCrosstab(), "confirmed:true applies");
   }

   @Test
   void wbs100CrosstabIsNotRefusedForADependentOnOnlyTheRowHeader() throws Exception {
      apply(ed -> ed.addMirror("DEP", "U"));
      apply(ed -> ed.setGroupAggregate("DEP", groups("oid"), List.of()));
      apply(ed -> ed.setGroupAggregate("U", groups("pid", "oid"), List.of(sum("qty", null)),
         true, false));
      trigger();
      assertTrue(table("U").getAggregateInfo().isCrosstab());
      assertEquals(1, table("DEP").getAggregateInfo().getGroupCount());
   }

   // ---- WBS-101 -----------------------------------------------------------

   @Test
   void wbs101DownstreamFilterAndExpressionAreGuardedOnBothTools() throws Exception {
      apply(ed -> ed.addMirror("DEP", "U"));
      apply(ed -> ed.addFilter("DEP", "oid", ">", "10500"));
      apply(ed -> ed.addMirror("DEPX", "U"));
      apply(ed -> ed.addExpressionColumn("DEPX", "OID_PLUS", "field['oid'] + 1", "integer", false));

      // S6a/S6b: set_group_aggregate.
      PairingException ex = assertThrows(PairingException.class, () -> apply(ed ->
         ed.setGroupAggregate("U", groups("pid"), List.of(sum("qty", null)))));
      assertTrue(ex.getMessage().contains("'DEP'"), ex.getMessage());
      assertTrue(ex.getMessage().contains("'DEPX'"), ex.getMessage());
      trigger();
      assertTrue(table("U").getAggregateInfo().isEmpty());
      assertEquals(1, ((ConditionList) table("DEP").getPreConditionList()).getConditionSize());

      // S6c: set_column_visibility(hide).
      ex = assertThrows(PairingException.class,
         () -> apply(ed -> ed.setColumnVisibility("U", "oid", false)));
      assertTrue(ex.getMessage().contains("'DEP'"), ex.getMessage());
      trigger();
      assertTrue(col(table("U"), "oid").isVisible(), "refused hide not applied");
      assertEquals(1, ((ConditionList) table("DEP").getPreConditionList()).getConditionSize(),
         "DEP's filter survives the refused hide + trigger");

      // confirmed:true lets both through.
      apply(ed -> ed.setColumnVisibility("U", "oid", false, true));
      assertFalse(col(table("U"), "oid").isVisible());
      apply(ed -> ed.setColumnVisibility("U", "oid", true));
      apply(ed -> ed.setGroupAggregate("U", groups("pid"), List.of(sum("qty", null)), false, true));
      assertFalse(table("U").getAggregateInfo().isEmpty());
   }

   // ---- no dependents: ordinary calls are never refused --------------------

   @Test
   void ordinaryCallsWithNoDependentsAreNotRefused() throws Exception {
      apply(ed -> ed.setGroupAggregate("U", groups("pid"), List.of(sum("qty", "TOTAL"))));
      apply(ed -> ed.setGroupAggregate("U", groups("pid"), List.of(sum("qty", "T2"))));
      apply(ed -> ed.setGroupAggregate("U", groups("pid", "qty"), List.of()));
      apply(ed -> ed.setGroupAggregate("U",
         List.of(new WorksheetMutationSupport.GroupSpec("od", "MONTH")), List.of()));
      apply(ed -> ed.setGroupAggregate("U", groups("pid", "oid"), List.of(sum("qty", null)),
         true, false));
      apply(ed -> ed.setGroupAggregate("U", List.of(), List.of()));
      apply(ed -> ed.setColumnVisibility("U", "oid", false));
      trigger();
      assertTrue(table("U").getAggregateInfo().isEmpty());
      assertFalse(col(table("U"), "oid").isVisible());
   }

   // ---- helpers -----------------------------------------------------------

   private void apply(ThrowingConsumer<WorksheetEditService.Editor> m) throws Exception {
      svc.apply("TOK", agent, m);
   }

   private void trigger() throws Exception {
      String name = "ZM" + (++triggers);
      apply(ed -> ed.addMirror(name, "Z"));
   }

   private TableAssembly table(String name) {
      return (TableAssembly) ws.getAssembly(name);
   }

   private static ColumnRef col(TableAssembly t, String attr) {
      ColumnSelection cs = t.getColumnSelection(false);

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         if(cs.getAttribute(i) instanceof ColumnRef cr && attr.equals(cr.getAttribute())) {
            return cr;
         }
      }

      fail("no column " + attr + " in " + t.getName());
      return null;
   }

   private static DataRef rangeColumn(ColumnSelection cs) {
      for(int i = 0; i < cs.getAttributeCount(); i++) {
         if(cs.getAttribute(i) instanceof ColumnRef cr && cr.getDataRef() instanceof DateRangeRef) {
            return cr;
         }
      }

      return null;
   }

   private static List<WorksheetMutationSupport.GroupSpec> groups(String... fields) {
      return java.util.Arrays.stream(fields).map(WorksheetMutationSupport.GroupSpec::new).toList();
   }

   private static WorksheetMutationSupport.AggregateSpec sum(String field, String alias) {
      return new WorksheetMutationSupport.AggregateSpec(field, "SUM", alias);
   }

   private static EmbeddedTableAssembly orders(Worksheet ws, String name) {
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, name, "pid", "oid", "qty", "od");
      t.setEmbeddedData(new XEmbeddedTable(
         new String[]{ "integer", "integer", "integer", "date" },
         new Object[][]{
            { "pid", "oid", "qty", "od" },
            { 1, 10501, 5, java.sql.Date.valueOf("2024-01-15") },
            { 1, 10502, 7, java.sql.Date.valueOf("2024-04-15") },
            { 2, 10501, 3, java.sql.Date.valueOf("2024-07-15") },
            { 2, 10503, 9, java.sql.Date.valueOf("2024-10-15") },
         }));
      ColumnSelection cs = t.getColumnSelection(false);

      for(String c : List.of("pid", "oid", "qty")) {
         ((ColumnRef) cs.getAttribute(c)).setDataType(XSchema.INTEGER);
      }

      ((ColumnRef) cs.getAttribute("od")).setDataType(XSchema.DATE);
      return t;
   }

   private static WorksheetEditService service(Worksheet ws) throws Exception {
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
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      box.setActive(true);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq("Worksheet/ws1"), any()))
         .thenReturn(rws);

      return new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), securityEngine, mock(InnerJoinService.class));
   }
}
