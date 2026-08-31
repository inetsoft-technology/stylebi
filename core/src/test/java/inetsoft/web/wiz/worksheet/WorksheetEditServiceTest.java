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
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.pairing.TestPrincipals;
import inetsoft.web.wiz.pairing.TestWorksheets;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.*;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@WizAgentTestSupport
class WorksheetEditServiceTest {

   @Test
   void appliesMutationViaSessionAndBroadcasts() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq("Worksheet/foo-7"), eq(agent)))
         .thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess, broadcast,
         mock(SecurityEngine.class), mock(InnerJoinService.class));
      svc.apply("TOK", agent, ed -> ed.removeColumn("T", "a"));

      assertNull(t.getColumnSelection(false).getAttribute("a"));
      verify(broadcast).broadcastRefresh(eq(rws), eq(SheetType.WORKSHEET), eq("Worksheet/foo-7"), eq(agent));
   }

   /**
    * Bug #76350 follow-on (item A): {@code refreshAssemblies} — called unconditionally at the end
    * of every mutation-applying method, looping over every {@link TableAssembly} in the
    * worksheet, not just the one edited — called {@code refreshColumnSelection} (the call that
    * actually executes a crosstab/grouped table's query) with no bound. A slow-to-execute table
    * anywhere in the worksheet made an unrelated, already-succeeded edit hang and look like a
    * false 30s timeout (PSM-003/PQE-001). Bounding it in {@code RenderWaitSupport.awaitOrRetry}
    * and letting the existing {@code catch(Exception ex)} swallow a timeout like any other
    * per-table failure means the edit itself still returns promptly, successfully.
    */
   @Test
   void applySwallowsATimedOutTableInsteadOfPropagatingTheFailure() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);

      // An unrelated table elsewhere in the same worksheet whose query hasn't run yet in this
      // runtime -- refreshAssemblies loops over every TableAssembly, not just "T".
      TableAssembly slow = TestWorksheets.withGroupSumAndSort(
         TestWorksheets.nonEmbeddedTableWithColumns(ws, "Slow1", "cust", "amount"),
         "cust", "amount");
      ws.addAssembly(slow);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      doAnswer(invocation -> {
         Thread.sleep(3_000);
         return null;
      }).when(box).refreshColumnSelection(eq("Slow1"), anyBoolean());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      // Must not throw -- the mutation on "T" already succeeded; "Slow1" timing out during the
      // best-effort post-edit warm-up must not be reported back as the whole edit failing.
      svc.apply("TOK", agent, ed -> ed.removeColumn("T", "a"));

      assertNull(t.getColumnSelection(false).getAttribute("a"),
                 "the mutation itself must still succeed even though Slow1's warm-up timed out");
   }

   /**
    * The shared wall-clock budget across the whole {@code refreshAssemblies} loop (added so the
    * loop's aggregate cost is capped regardless of table count, instead of N x 2s) means a table
    * that is genuinely slow -- not stuck, just slow -- consumes the entire budget if it sorts
    * early in {@code ws.getAssemblies()}'s iteration order, and every table after it is skipped
    * for that pass rather than getting its own independent wait. This is a deliberate, accepted
    * trade-off (documented on {@code WorksheetEditService.refreshAssemblies} itself) — this test
    * pins down the behavior so it does not silently change.
    */
   @Test
   void refreshAssembliesSharedBudgetSkipsTablesAfterTheFirstSlowOne() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly slow1 = TestWorksheets.withGroupSumAndSort(
         TestWorksheets.nonEmbeddedTableWithColumns(ws, "Slow1", "cust", "amount"),
         "cust", "amount");
      ws.addAssembly(slow1);
      TableAssembly slow2 = TestWorksheets.withGroupSumAndSort(
         TestWorksheets.nonEmbeddedTableWithColumns(ws, "Slow2", "cust", "amount"),
         "cust", "amount");
      ws.addAssembly(slow2);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      // Slow1 alone blocks past the whole shared budget, so it consumes it entirely.
      doAnswer(invocation -> {
         Thread.sleep(3_000);
         return null;
      }).when(box).refreshColumnSelection(eq("Slow1"), anyBoolean());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      svc.apply("TOK", agent, ed -> {});

      verify(box, never()).refreshColumnSelection(eq("Slow2"), anyBoolean());
   }

   @Test
   void rejectsInvalidSession() {
      SheetSessionService sessions = mock(SheetSessionService.class);
      when(sessions.resolve(any(), any())).thenReturn(null);
      WorksheetEditService svc = new WorksheetEditService(sessions,
         mock(SheetRuntimeAccess.class), mock(SheetAgentBroadcastService.class),
         mock(SecurityEngine.class), mock(InnerJoinService.class));
      assertThrows(PairingException.class,
         () -> svc.apply("BAD", TestPrincipals.user("alice", "host-org"), ed -> {}));
   }

   @Test
   void addColumnAddsRef() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));
      svc.apply("TOK", agent, ed -> ed.addColumn("T", "c", "string"));

      assertNotNull(t.getColumnSelection(false).getAttribute("c"));
   }

   @Test
   void renameColumnSetsAlias() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));
      svc.apply("TOK", agent, ed -> ed.renameColumn("T", "a", "alpha"));

      ColumnSelection cs = t.getColumnSelection(false);
      // After setAlias("alpha"), ColumnRef.getName() returns "alpha", so getAttribute
      // must use the new alias name. The original attribute name "a" is no longer the key.
      DataRef ref = cs.getAttribute("alpha");
      assertNotNull(ref);
      if(ref instanceof ColumnRef cr) {
         assertEquals("alpha", cr.getAlias());
      }
   }

   @Test
   void addNamedGroupCreatesStandaloneGroupingWhenTableAndColumnOmitted() throws Exception {
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      List<WorksheetMutationSupport.GroupMapping> mappings = List.of(
         new WorksheetMutationSupport.GroupMapping("Northeast", List.of("NY", "NJ", "CT")),
         new WorksheetMutationSupport.GroupMapping("West", List.of("CA")));

      svc.apply("TOK", agent,
                ed -> ed.addNamedGroup("StateRegion", null, null, "string", mappings, false));

      Assembly a = ws.getAssembly("StateRegion");
      assertInstanceOf(DefaultNamedGroupAssembly.class, a);
      NamedGroupAssembly nga = (NamedGroupAssembly) a;
      assertEquals(AttachedAssembly.DATA_TYPE_ATTACHED, nga.getAttachedType());
      assertEquals(XSchema.STRING, nga.getAttachedDataType());
      assertNull(nga.getAttachedAttribute());
   }

   @Test
   void addNamedGroupDefaultsStandaloneTypeToString() throws Exception {
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      svc.apply("TOK", agent,
                ed -> ed.addNamedGroup("StateRegion", null, null, null, List.of(), false));

      NamedGroupAssembly nga = (NamedGroupAssembly) ws.getAssembly("StateRegion");
      assertEquals(XSchema.STRING, nga.getAttachedDataType());
   }

   @Test
   void addNamedGroupRejectsMismatchedTableAndColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent,
                         ed -> ed.addNamedGroup("G", "T", null, null, List.of(), false)));
   }

   @Test
   void addNamedGroupRejectsInvalidStandaloneType() throws Exception {
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent,
                         ed -> ed.addNamedGroup("G", null, null, "strnig", List.of(), false)));
   }

   @Test
   void addNamedGroupStandaloneConditionUsesThisPlaceholderAndSurvivesClone() throws Exception {
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      List<WorksheetMutationSupport.GroupMapping> mappings = List.of(
         new WorksheetMutationSupport.GroupMapping("Northeast", List.of("NY", "NJ", "CT")),
         new WorksheetMutationSupport.GroupMapping("West", List.of("CA")));

      svc.apply("TOK", agent,
                ed -> ed.addNamedGroup("StateRegion", null, null, "string", mappings, false));

      DefaultNamedGroupAssembly nga = (DefaultNamedGroupAssembly) ws.getAssembly("StateRegion");
      DataRef conditionRef = nga.getNamedGroupInfo().getGroupCondition("Northeast")
         .getConditionItem(0).getAttribute();
      assertNotNull(conditionRef);
      assertEquals("this", conditionRef.getAttribute());

      // Regression for NPE in ConditionItem.toString() (invoked via NamedGroupInfo.clone() during
      // worksheet clone, e.g. TouchAssetService) when the condition's DataRef was left null.
      assertDoesNotThrow(() -> ws.clone());
   }

   @Test
   void addJoinRejectsMissingNameAndDoesNotPoisonAssemblyLookup() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left = TestWorksheets.tableWithColumns(ws, "CUSTOMERS1", "REGION_ID");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "REGIONS1", "REGION_ID");
      ws.addAssembly(left);
      ws.addAssembly(right);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      // Mirrors the real repro: leftKey/rightKey/joinType supplied but name omitted.
      assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent,
                         ed -> ed.addJoin(null, "CUSTOMERS1", "REGION_ID",
                                          "REGIONS1", "REGION_ID", "INNER", null, null)));

      // The rejected join must never have reached Worksheet.addAssembly(), so the name cache
      // is never poisoned: lookups for the pre-existing tables still resolve.
      assertSame(left, ws.getAssembly("CUSTOMERS1"));
      assertSame(right, ws.getAssembly("REGIONS1"));
      assertEquals(2, ws.getAssemblies().length);
   }

   @Test
   void addNamedGroupRejectsMissingNameAndDoesNotPoisonAssemblyLookup() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent,
                         ed -> ed.addNamedGroup(null, null, null, "string", List.of(), false)));

      // The rejected named group must never have reached Worksheet.addAssembly(), so the name
      // cache is never poisoned: lookups for the pre-existing table still resolve.
      assertSame(t, ws.getAssembly("T"));
      assertEquals(1, ws.getAssemblies().length);
   }

   @Test
   void editNamedGroupOnStandaloneGroupSurvivesClone() throws Exception {
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      svc.apply("TOK", agent,
                ed -> ed.addNamedGroup("StateRegion", null, null, "string", List.of(), false));
      svc.apply("TOK", agent,
                ed -> ed.editNamedGroup("StateRegion",
                                        List.of(new WorksheetMutationSupport.GroupMapping(
                                           "West", List.of("CA"))),
                                        false));

      DefaultNamedGroupAssembly nga = (DefaultNamedGroupAssembly) ws.getAssembly("StateRegion");
      DataRef conditionRef = nga.getNamedGroupInfo().getGroupCondition("West")
         .getConditionItem(0).getAttribute();
      assertNotNull(conditionRef);
      assertEquals("this", conditionRef.getAttribute());

      // NamedGroupInfo.clone() catches and swallows its own NPE (logging "Failed to clone
      // object" and returning null) rather than propagating it, so this alone would not have
      // caught the regression — the assertions above on the condition ref are what matter here.
      assertDoesNotThrow(() -> ws.clone());
   }

   /**
    * Guards the invariant {@code CalcTableService.worksheetNamedGroups}/{@code FieldRefFactory}
    * depend on: a named group attached to a worksheet table column must carry
    * {@code SourceInfo(ASSET, null, <worksheet table name>)} — the same convention
    * {@code WizVsService}/{@code VSChartDndService} use for a chart/table/crosstab/calc-table's
    * own bound-table {@code SourceInfo} — even when that table is itself bound to a real
    * datasource/logical-model (a {@link BoundTableAssembly}). Reusing the table's own upstream
    * {@code SourceInfo} here (attempted and reverted during Bug #76097) breaks that matching:
    * those two classes look up a group by comparing the VS assembly's own worksheet-table-name
    * {@code SourceInfo} against the group's {@code attachedSource}, so anything other than the
    * worksheet table's name here makes a real, already-working named group silently stop
    * resolving in chart/table/crosstab/calc-table bindings.
    */
   @Test
   void addNamedGroupSourceInfoIsWorksheetTableNameEvenForBoundTable() throws Exception {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t = new BoundTableAssembly(ws, "Customer1");
      t.setSourceInfo(new SourceInfo(SourceInfo.MODEL, "Examples/Orders", "Order Model"));
      ColumnSelection columns = new ColumnSelection();
      ColumnRef stateRef = new ColumnRef(new AttributeRef("Customer", "State"));
      stateRef.setDataType(XSchema.STRING);
      columns.addAttribute(stateRef);
      t.setColumnSelection(columns);
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      List<WorksheetMutationSupport.GroupMapping> mappings = List.of(
         new WorksheetMutationSupport.GroupMapping("N", List.of("NJ", "NY", "NV")));

      svc.apply("TOK", agent,
                ed -> ed.addNamedGroup("StateNGroup", "Customer1", "State", null, mappings, true));

      DefaultNamedGroupAssembly nga = (DefaultNamedGroupAssembly) ws.getAssembly("StateNGroup");
      SourceInfo attached = nga.getAttachedSource();
      assertNotNull(attached);
      assertEquals(SourceInfo.ASSET, attached.getType());
      assertEquals("Customer1", attached.getSource());
   }

   /**
    * Regression for Bug #76097: a group mapping's negated-equality operation ("!=" /
    * "NOT_EQUAL_TO") over more than one value must test "not equal to any of them" (a negated
    * ONE_OF), not OR together separately-negated single-value EQUAL_TO conditions — the latter
    * is a near-tautology (true for almost every input) since EQUAL_TO only ever reads a
    * condition's first value.
    */
   @Test
   void addNamedGroupNegatedEqualityExcludesAllListedValues() throws Exception {
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      List<WorksheetMutationSupport.GroupMapping> mappings = List.of(
         new WorksheetMutationSupport.GroupMapping("NotNYNJ", List.of("NY", "NJ"), "!="));

      svc.apply("TOK", agent,
                ed -> ed.addNamedGroup("StateNGroup", null, null, "string", mappings, true));

      DefaultNamedGroupAssembly nga = (DefaultNamedGroupAssembly) ws.getAssembly("StateNGroup");
      ConditionList conds = nga.getNamedGroupInfo().getGroupCondition("NotNYNJ");
      assertEquals(1, conds.getSize(),
         "negated equality over multiple values must be a single negated ONE_OF condition, " +
            "not OR'd single-value conditions");
      Condition c = conds.getConditionItem(0).getCondition();
      assertEquals(XCondition.ONE_OF, c.getOperation());
      assertTrue(c.isNegated());
      assertEquals(2, c.getValueCount());
      assertEquals("NY", c.getValue(0));
      assertEquals("NJ", c.getValue(1));
   }

   /**
    * Regression for Bug #76097: a group mapping's {@code operation} must be honored instead of
    * always building an EQUAL_TO condition — "starts with N" should produce a single
    * STARTING_WITH condition per value, not an enumerated equality list.
    */
   @Test
   void addNamedGroupSupportsStartingWithOperator() throws Exception {
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      List<WorksheetMutationSupport.GroupMapping> mappings = List.of(
         new WorksheetMutationSupport.GroupMapping("N", List.of("N"), "STARTING_WITH"),
         new WorksheetMutationSupport.GroupMapping("Others", List.of(), null));

      svc.apply("TOK", agent,
                ed -> ed.addNamedGroup("StateNGroup", null, null, "string", mappings, true));

      DefaultNamedGroupAssembly nga = (DefaultNamedGroupAssembly) ws.getAssembly("StateNGroup");
      Condition c = nga.getNamedGroupInfo().getGroupCondition("N")
         .getConditionItem(0).getCondition();
      assertEquals(XCondition.STARTING_WITH, c.getOperation());
      assertEquals(1, c.getValueCount());
      assertEquals("N", c.getValue(0));
   }

   /**
    * PR #4765 review follow-up: {@code BETWEEN} with any value count other than 2 is not "no
    * matches" the way it silently was before -- {@link Condition#evaluate} only ever reads
    * {@code values.get(0)}/{@code values.get(1)}, so a 1- or 3-value BETWEEN mapping is a
    * malformed request that should fail loud, not be built into a condition that quietly never
    * matches.
    */
   @Test
   void addNamedGroupRejectsBetweenWithWrongValueCount() throws Exception {
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      List<WorksheetMutationSupport.GroupMapping> mappings = List.of(
         new WorksheetMutationSupport.GroupMapping("Mid", List.of("10"), "BETWEEN"));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent,
                         ed -> ed.addNamedGroup("G", null, null, "string", mappings, false)));
      assertTrue(ex.getMessage().contains("BETWEEN"));
      assertNull(ws.getAssembly("G"), "a rejected group must not be partially created");
   }

   /**
    * PR #4765 review follow-up: an empty value list for {@code ONE_OF} matches nothing, but a
    * NEGATED empty {@code ONE_OF} (e.g. {@code "!="} with no values) matches EVERYTHING -- both
    * are almost certainly caller mistakes, not an intentional "match nothing"/"match everything"
    * grouping, so they fail loud instead of silently building a useless or over-broad condition.
    */
   @Test
   void addNamedGroupRejectsEmptyValuesForSetBasedOperation() throws Exception {
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      List<WorksheetMutationSupport.GroupMapping> mappings = List.of(
         new WorksheetMutationSupport.GroupMapping("NotAnything", List.of(), "!="));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent,
                         ed -> ed.addNamedGroup("G2", null, null, "string", mappings, false)));
      assertTrue(ex.getMessage().contains("at least one value"));
      assertNull(ws.getAssembly("G2"), "a rejected group must not be partially created");
   }

   /**
    * Bug #75980: add_join only joined two tables at a time, forcing a chain of assemblies for
    * a 3+-table join instead of a single combined view like Composer's own multi-select join.
    * This exercises the {@code joinPaths} overload end to end with a REAL
    * {@link InnerJoinService} (not a mock) so the join wiring itself is verified, not just that
    * some call was made — mirroring the direct-construction pattern already used by
    * {@link inetsoft.web.composer.ws.joins.InnerJoinServiceOperatorOrientationTest}.
    */
   @Test
   void addJoinWithPathsBuildsSingleAssemblyOverThreeTables() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly orders = TestWorksheets.tableWithColumns(
         ws, "ORDER_DETAILS1", "order_id", "product_id");
      EmbeddedTableAssembly products = TestWorksheets.tableWithColumns(
         ws, "PRODUCTS1", "product_id", "category_id");
      EmbeddedTableAssembly categories = TestWorksheets.tableWithColumns(
         ws, "CATEGORIES1", "category_id", "name");
      ws.addAssembly(orders);
      ws.addAssembly(products);
      ws.addAssembly(categories);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class),
         new InnerJoinService(null, null));

      List<WorksheetMutationSupport.JoinPathSpec> paths = List.of(
         new WorksheetMutationSupport.JoinPathSpec(
            "ORDER_DETAILS1", "product_id", "PRODUCTS1", "product_id", "INNER"),
         new WorksheetMutationSupport.JoinPathSpec(
            "PRODUCTS1", "category_id", "CATEGORIES1", "category_id", "LEFT"));

      svc.apply("TOK", agent, ed -> ed.addJoin("JOINED", paths));

      RelationalJoinTableAssembly joined = (RelationalJoinTableAssembly) ws.getAssembly("JOINED");
      assertNotNull(joined);
      assertEquals(3, joined.getTableAssemblies().length);

      TableAssemblyOperator opOrdersProducts = joined.getOperator("ORDER_DETAILS1", "PRODUCTS1");
      assertNotNull(opOrdersProducts);
      assertEquals(1, opOrdersProducts.getOperatorCount());
      assertEquals(TableAssemblyOperator.INNER_JOIN, opOrdersProducts.getOperator(0).getOperation());
      assertEquals("product_id", opOrdersProducts.getOperator(0).getLeftAttribute().getAttribute());
      assertEquals("product_id", opOrdersProducts.getOperator(0).getRightAttribute().getAttribute());

      TableAssemblyOperator opProductsCategories =
         joined.getOperator("PRODUCTS1", "CATEGORIES1");
      assertNotNull(opProductsCategories);
      assertEquals(1, opProductsCategories.getOperatorCount());
      assertEquals(TableAssemblyOperator.LEFT_JOIN,
                   opProductsCategories.getOperator(0).getOperation());
   }

   /**
    * The star-join shape from the original repro (hub table joined to two others, not a linear
    * left-to-right chain) must work — this is exactly why {@code editExistingJoinTable} is used
    * instead of hand-rolling positional pairing.
    */
   @Test
   void addJoinWithPathsSupportsStarShapedJoin() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly hub = TestWorksheets.tableWithColumns(
         ws, "WORK_PACKAGES", "id", "project_id", "status_id");
      EmbeddedTableAssembly projects = TestWorksheets.tableWithColumns(ws, "PROJECTS", "id");
      EmbeddedTableAssembly statuses = TestWorksheets.tableWithColumns(ws, "STATUSES", "id");
      ws.addAssembly(hub);
      ws.addAssembly(projects);
      ws.addAssembly(statuses);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class),
         new InnerJoinService(null, null));

      List<WorksheetMutationSupport.JoinPathSpec> paths = List.of(
         new WorksheetMutationSupport.JoinPathSpec(
            "WORK_PACKAGES", "project_id", "PROJECTS", "id", "INNER"),
         new WorksheetMutationSupport.JoinPathSpec(
            "WORK_PACKAGES", "status_id", "STATUSES", "id", "INNER"));

      svc.apply("TOK", agent, ed -> ed.addJoin("JOINED", paths));

      RelationalJoinTableAssembly joined = (RelationalJoinTableAssembly) ws.getAssembly("JOINED");
      assertNotNull(joined);
      assertEquals(3, joined.getTableAssemblies().length);
      assertNotNull(joined.getOperator("WORK_PACKAGES", "PROJECTS"));
      assertNotNull(joined.getOperator("WORK_PACKAGES", "STATUSES"));
   }

   @Test
   void addJoinWithPathsRejectsMergeType() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = TestWorksheets.tableWithColumns(ws, "A", "k");
      EmbeddedTableAssembly b = TestWorksheets.tableWithColumns(ws, "B", "k");
      ws.addAssembly(a);
      ws.addAssembly(b);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      List<WorksheetMutationSupport.JoinPathSpec> paths = List.of(
         new WorksheetMutationSupport.JoinPathSpec("A", "k", "B", "k", "MERGE"));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addJoin("JOINED", paths)));
      assertTrue(ex.getMessage().contains("add_merge_join"));
      assertNull(ws.getAssembly("JOINED"), "a rejected multi-join must not be partially created");
   }

   @Test
   void addJoinWithPathsRejectsEmptyPathList() throws Exception {
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addJoin("JOINED", List.of())));
      assertNull(ws.getAssembly("JOINED"));
   }

   /**
    * A CROSS edge is an exclusive operation ({@link TableAssemblyOperator#checkValidity} refuses
    * one once the combined operator holds more than one edge), so it may only appear as the SOLE
    * edge in a joinPaths call — combining it with any other edge must be refused up front, before
    * touching the worksheet, rather than posted and failing deep inside InnerJoinService.
    */
   @Test
   void addJoinWithPathsRejectsCrossCombinedWithOtherEdges() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = TestWorksheets.tableWithColumns(ws, "A", "id");
      EmbeddedTableAssembly b = TestWorksheets.tableWithColumns(ws, "B", "id");
      EmbeddedTableAssembly c = TestWorksheets.tableWithColumns(ws, "C", "id");
      ws.addAssembly(a);
      ws.addAssembly(b);
      ws.addAssembly(c);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      List<WorksheetMutationSupport.JoinPathSpec> paths = List.of(
         new WorksheetMutationSupport.JoinPathSpec("A", "id", "B", "id", "INNER"),
         new WorksheetMutationSupport.JoinPathSpec("B", null, "C", null, "CROSS"));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addJoin("JOINED", paths)));
      assertTrue(ex.getMessage().contains("CROSS"));
      assertNull(ws.getAssembly("JOINED"), "a rejected multi-join must not be partially created");
      assertEquals(3, ws.getAssemblies().length, "no assembly beyond the pre-existing 3 tables");
   }

   /**
    * A lone CROSS edge (the only entry in joinPaths) is exactly what the two-table
    * {@code add_cross_join} already supports, so it must still work here.
    */
   @Test
   void addJoinWithPathsAllowsSoleCrossEdge() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = TestWorksheets.tableWithColumns(ws, "A", "id");
      EmbeddedTableAssembly b = TestWorksheets.tableWithColumns(ws, "B", "id");
      ws.addAssembly(a);
      ws.addAssembly(b);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), eq(ResourceType.CROSS_JOIN), anyString(), any()))
         .thenReturn(true);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), securityEngine, new InnerJoinService(null, null));

      List<WorksheetMutationSupport.JoinPathSpec> paths = List.of(
         new WorksheetMutationSupport.JoinPathSpec("A", null, "B", null, "CROSS"));

      svc.apply("TOK", agent, ed -> ed.addJoin("JOINED", paths));

      RelationalJoinTableAssembly joined = (RelationalJoinTableAssembly) ws.getAssembly("JOINED");
      assertNotNull(joined);
      TableAssemblyOperator op = joined.getOperator("A", "B");
      assertNotNull(op);
      assertEquals(TableAssemblyOperator.CROSS_JOIN, op.getOperator(0).getOperation());
   }

   /**
    * If {@link InnerJoinService#editExistingJoinTable} fails after the new join assembly has
    * already been registered in the live worksheet, the assembly must be removed again rather
    * than left behind half-wired — a caller that never gets an "ok" response should never find a
    * broken assembly on the next read either.
    */
   @Test
   void addJoinWithPathsRemovesAssemblyWhenWiringFailsAfterRegistration() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = TestWorksheets.tableWithColumns(ws, "A", "id");
      EmbeddedTableAssembly b = TestWorksheets.tableWithColumns(ws, "B", "id");
      ws.addAssembly(a);
      ws.addAssembly(b);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      InnerJoinService failingJoinService = mock(InnerJoinService.class);
      doThrow(new RuntimeException("boom")).when(failingJoinService)
         .editExistingJoinTable(any(), any(), any(), anyBoolean());

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), failingJoinService);

      List<WorksheetMutationSupport.JoinPathSpec> paths = List.of(
         new WorksheetMutationSupport.JoinPathSpec("A", "id", "B", "id", "INNER"));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addJoin("JOINED", paths)));
      assertTrue(ex.getMessage().contains("Failed to build multi-table join"));
      assertNull(ws.getAssembly("JOINED"),
                 "a join whose wiring failed after registration must not remain in the worksheet");
      assertEquals(2, ws.getAssemblies().length, "no assembly beyond the pre-existing 2 tables");
   }
}
