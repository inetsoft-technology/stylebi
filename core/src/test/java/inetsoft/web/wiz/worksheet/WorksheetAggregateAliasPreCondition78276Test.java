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
import inetsoft.uql.asset.*;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.AggregateSpec;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.ConditionNode;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.ConditionSpec;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.GroupSpec;
import org.junit.jupiter.api.*;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78276: a pre-aggregate condition (set_conditions / set_mv_conditions update-pre and
 * delete-pre) naming an aggregate OUTPUT alias is refused instead of being silently re-targeted
 * onto the aggregated input column.
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetAggregateAliasPreCondition78276Test {
   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private Worksheet ws;
   private WorksheetEditService svc;

   @BeforeEach
   void setUp() throws Exception {
      ws = new Worksheet();
      ws.addAssembly(TestWorksheets.nonEmbeddedTableWithColumns(
         ws, "T", "State", "Amount", "ORDER_ID"));
      svc = service(ws);
      apply(ed -> ed.addMirror("M", "T"));
   }

   private TableAssembly m() {
      return (TableAssembly) ws.getAssembly("M");
   }

   private void apply(ThrowingConsumer<WorksheetEditService.Editor> c) throws Exception {
      svc.apply("TOK", agent, c);
   }

   private static ConditionNode cond(String field) {
      return new ConditionNode(
         new ConditionSpec(field, ">", List.of("1"), false, null), null, 0);
   }

   private void assertRefused(Executable_ e) {
      PairingException ex = assertThrows(PairingException.class, e::run);
      assertTrue(ex.getMessage().contains("does not exist before aggregation"), ex.getMessage());
      assertTrue(ex.getMessage().contains("set_post_conditions"), ex.getMessage());
   }

   private interface Executable_ {
      void run() throws Exception;
   }

   private void pre(String f) throws Exception {
      apply(ed -> ed.setConditions("M", List.of(cond(f))));
   }

   private void aggregateCount(String alias) throws Exception {
      apply(ed -> ed.setGroupAggregate("M", List.of(new GroupSpec("State")),
         List.of(new AggregateSpec("ORDER_ID", "COUNT", alias))));
   }

   @Test
   void countAliasRefusedInSetConditionsAndBothMvPreLists() throws Exception {
      aggregateCount("CNT");
      assertRefused(() -> pre("CNT"));
      assertRefused(() -> apply(ed -> ed.setMVConditions(
         "M", List.of(cond("CNT")), null, null, null, null)));
      assertRefused(() -> apply(ed -> ed.setMVConditions(
         "M", null, null, List.of(cond("CNT")), null, null)));
      assertTrue(m().getPreConditionList() == null || m().getPreConditionList().isEmpty());
   }

   @Test
   void aliasCollidingWithRawColumnRefused() throws Exception {
      apply(ed -> ed.setGroupAggregate("M", List.of(new GroupSpec("State")),
         List.of(new AggregateSpec("ORDER_ID", "COUNT", "Amount"))));
      assertRefused(() -> pre("Amount"));
   }

   @Test
   void secondaryAggregateAliasesRefused() throws Exception {
      apply(ed -> ed.setGroupAggregate("M", List.of(new GroupSpec("State")),
         List.of(new AggregateSpec("Amount", "MIN", "MN"),
                 new AggregateSpec("Amount", "MAX", "MX"))));
      assertRefused(() -> pre("MN"));
      assertRefused(() -> pre("MX"));
   }

   @Test
   void crosstabAggregateAliasRefused() throws Exception {
      apply(ed -> ed.setGroupAggregate("M",
         List.of(new GroupSpec("State"), new GroupSpec("ORDER_ID")),
         List.of(new AggregateSpec("Amount", "SUM", "TOT")), true));
      assertRefused(() -> pre("TOT"));
   }

   @Test
   void renameOfAliasedAggregateColumnDoesNotBypassGuard() throws Exception {
      aggregateCount("CNT");
      apply(ed -> ed.renameColumn("M", "CNT", "CNT9"));
      assertRefused(() -> pre("CNT9"));
   }

   @Test
   void fieldOperandNamingAliasRefusedInPreListOnly() throws Exception {
      aggregateCount("CNT");
      ConditionNode node = new ConditionNode(new ConditionSpec(
         "State", "=", List.of("CNT"), false, null,
         List.of(new WorksheetMutationSupport.ConditionValueSpec(
            "field", "CNT", null, null, 0))), null, 0);
      assertRefused(() -> apply(ed -> ed.setConditions("M", List.of(node))));
      assertRefused(() -> apply(ed -> ed.setMVConditions(
         "M", List.of(node), null, null, null, null)));
      apply(ed -> ed.setPostConditions("M", List.of(node)));
   }

   @Test
   void renameBackToOriginalAliasStillRefused() throws Exception {
      aggregateCount("CNT");
      apply(ed -> ed.renameColumn("M", "CNT", "CNT9"));
      apply(ed -> ed.renameColumn("M", "CNT9", "CNT"));
      assertRefused(() -> pre("CNT"));
   }

   @Test
   void renamingNonAliasedColumnLeavesRecordedSetIntact() throws Exception {
      aggregateCount("CNT");
      apply(ed -> ed.renameColumn("M", "State", "ST"));
      assertEquals("CNT",
         m().getProperty(WorksheetMutationSupport.AGGREGATE_OUTPUT_ALIASES));
      assertRefused(() -> pre("CNT"));
   }

   @Test
   void renamedColumnAggregatedWithoutAliasStillAccepted() throws Exception {
      apply(ed -> ed.renameColumn("M", "Amount", "Total"));
      apply(ed -> ed.setGroupAggregate("M", List.of(new GroupSpec("State")),
         List.of(new AggregateSpec("Total", "SUM", null))));
      pre("Total");
   }

   @Test
   void aggregateWithoutAliasAndGroupColumnsStillAccepted() throws Exception {
      apply(ed -> ed.setGroupAggregate("M", List.of(new GroupSpec("State")),
         List.of(new AggregateSpec("Amount", "SUM", null))));
      pre("Amount");
      pre("State");
   }

   @Test
   void renamedGroupColumnStillAccepted() throws Exception {
      apply(ed -> ed.renameColumn("M", "State", "ST"));
      aggregateCount("CNT");
      pre("ST");
   }

   @Test
   void postConditionOnAliasStillAccepted() throws Exception {
      aggregateCount("CNT");
      apply(ed -> ed.setPostConditions("M", List.of(cond("CNT"))));
   }

   @Test
   void mirrorOfAggregatedTableAcceptsOutputName() throws Exception {
      aggregateCount("CNT");
      apply(ed -> ed.addMirror("M2", "M"));
      apply(ed -> ed.setConditions("M2", List.of(cond("CNT"))));
   }

   @Test
   void tableWithoutRecordedAliasesIsUnchanged() throws Exception {
      aggregateCount("CNT");
      m().setProperty(WorksheetMutationSupport.AGGREGATE_OUTPUT_ALIASES, null);
      pre("CNT");
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
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);
      return new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), securityEngine, mock(InnerJoinService.class));
   }
}
