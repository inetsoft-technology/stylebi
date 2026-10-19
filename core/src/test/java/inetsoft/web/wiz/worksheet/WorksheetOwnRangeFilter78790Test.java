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
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.AggregateSpec;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.GroupSpec;
import org.junit.jupiter.api.*;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78790 (WBS-141): a {@code set_group_aggregate} that removes a date-range column left over
 * from the previous grouping silently deleted the table's OWN condition and sort on that column.
 * It is now refused (naming the table itself) unless {@code confirmed}, and a refusal leaves the
 * table exactly as it was, checked through the real {@code apply} path with a real sandbox so a
 * leftover would be published by the next unrelated op.
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetOwnRangeFilter78790Test {
   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private Worksheet ws;
   private WorksheetEditService svc;
   private String quarter;
   private int triggers;

   @BeforeEach
   void setUp() throws Exception {
      ws = new Worksheet();
      ws.addAssembly(orders(ws, "U"));
      ws.addAssembly(orders(ws, "Z"));
      svc = service(ws);
      // Filters are only allowed on non-embedded tables, so work on a mirror of U.
      apply(ed -> ed.addMirror("Q", "U"));
      apply(ed -> ed.setGroupAggregate("Q", List.of(new GroupSpec("od", "QUARTER")),
         List.of(sum("qty"))));
      quarter = rangeColumn(table("Q").getColumnSelection(false)).getName();
   }

   private void addOwnFilter() throws Exception {
      apply(ed -> ed.addFilter("Q", quarter, "NOT_NULL"));
      trigger();
      assertEquals(1, filterCount(), "sanity: Q keeps its filter through a refresh");
   }

   @Test
   void regroupToMonthWithOwnFilterIsRefusedNamingTheTableItselfAndChangesNothing()
      throws Exception
   {
      addOwnFilter();

      PairingException ex = assertThrows(PairingException.class, () -> regroupToMonth(false));
      assertTrue(ex.getMessage().contains("table 'Q' itself (pre-condition)"),
         ex.getMessage());
      assertTrue(ex.getMessage().contains("confirmed:true"), ex.getMessage());
      trigger();

      assertEquals(1, filterCount(), "the filter survives the refusal and a trigger");
      assertQuarterGrouping();
   }

   @Test
   void regroupToMonthWithOwnFilterIsAcceptedWhenConfirmed() throws Exception {
      addOwnFilter();
      regroupToMonth(true);
      trigger();

      assertEquals(0, filterCount(), "confirmed: the filter on the dropped column is gone");
      assertNull(table("Q").getColumnSelection(false).getAttribute(quarter));
      assertNotNull(rangeColumn(table("Q").getColumnSelection(false)), "Month column present");
      assertEquals(1, table("Q").getAggregateInfo().getGroupCount());
   }

   @Test
   void identicalResendIsNotRefused() throws Exception {
      addOwnFilter();
      apply(ed -> ed.setGroupAggregate("Q", List.of(new GroupSpec("od", "QUARTER")),
         List.of(sum("qty"))));
      trigger();

      assertEquals(1, filterCount());
      assertQuarterGrouping();
   }

   @Test
   void regroupKeepingTheRangeColumnIsNotRefused() throws Exception {
      addOwnFilter();
      apply(ed -> ed.setGroupAggregate("Q",
         List.of(new GroupSpec("od", "QUARTER"), new GroupSpec("pid")), List.of(sum("qty"))));
      trigger();

      assertEquals(1, filterCount(), "the range column is kept, so is the filter");
      assertNotNull(table("Q").getColumnSelection(false).getAttribute(quarter));
   }

   @Test
   void clearWithOwnFilterOnTheRangeColumnIsRefusedAndAcceptedWhenConfirmed() throws Exception {
      addOwnFilter();

      PairingException ex = assertThrows(PairingException.class,
         () -> apply(ed -> ed.setGroupAggregate("Q", List.of(), List.of())));
      assertTrue(ex.getMessage().contains("table 'Q' itself"), ex.getMessage());
      trigger();
      assertEquals(1, filterCount());
      assertQuarterGrouping();

      apply(ed -> ed.setGroupAggregate("Q", List.of(), List.of(), false, true));
      trigger();
      assertEquals(0, filterCount(), "confirmed clear drops the filter");
      assertTrue(table("Q").getAggregateInfo().isEmpty());
   }

   @Test
   void ownSortOnTheRangeColumnIsRefused() throws Exception {
      apply(ed -> ed.setSort("Q", quarter, "ASC"));

      PairingException ex = assertThrows(PairingException.class, () -> regroupToMonth(false));
      assertTrue(ex.getMessage().contains("table 'Q' itself (sort)"), ex.getMessage());
      trigger();
      assertQuarterGrouping();

      regroupToMonth(true);
      assertNull(table("Q").getColumnSelection(false).getAttribute(quarter));
   }

   @Test
   void regroupIsNotRefusedWhenNoOwnStateReferencesTheRangeColumn() throws Exception {
      regroupToMonth(false);
      trigger();
      assertNull(table("Q").getColumnSelection(false).getAttribute(quarter));
      assertNotNull(rangeColumn(table("Q").getColumnSelection(false)));
   }

   // ---- helpers -----------------------------------------------------------

   private void regroupToMonth(boolean confirmed) throws Exception {
      apply(ed -> ed.setGroupAggregate("Q", List.of(new GroupSpec("od", "MONTH")),
         List.of(sum("qty")), false, confirmed));
   }

   /** Q is still grouped by the Quarter range column, which is still in its selection. */
   private void assertQuarterGrouping() {
      TableAssembly q = table("Q");
      DataRef range = rangeColumn(q.getColumnSelection(false));
      assertNotNull(range, "the Quarter column survives");
      assertEquals(quarter, range.getName());
      assertNotNull(q.getColumnSelection(true).getAttribute(quarter), "published too");
      assertEquals(1, q.getAggregateInfo().getGroupCount());
      assertEquals(1, q.getAggregateInfo().getAggregateCount());
   }

   private int filterCount() {
      ConditionList list = (ConditionList) table("Q").getPreConditionList();
      return list == null ? 0 : list.getConditionSize();
   }

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

   private static DataRef rangeColumn(ColumnSelection cs) {
      for(int i = 0; i < cs.getAttributeCount(); i++) {
         if(cs.getAttribute(i) instanceof ColumnRef cr && cr.getDataRef() instanceof DateRangeRef) {
            return cr;
         }
      }

      return null;
   }

   private static AggregateSpec sum(String field) {
      return new AggregateSpec(field, "SUM", null);
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
