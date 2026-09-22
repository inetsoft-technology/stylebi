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
    * Same regression as {@code ViewsheetSessionServiceTest}'s
    * {@code mutateNeverOvewritesTheRuntimesCurrentSocketSessionIdWithThisSessionsOwnFrozenValue}
    * -- see that test's doc comment. {@code apply} must NOT reapply this session's own
    * pairing-mint-frozen socketSessionId over whatever the runtime's field currently holds
    * (possibly healed since by a human's manual Refresh), or it would silently undo that
    * recovery on every subsequent agent call.
    */
   @Test
   void applyNeverOverwritesTheRuntimesCurrentSocketSessionIdWithThisSessionsOwnFrozenValue()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getSocketSessionId()).thenReturn("human-healed-live-socket");
      when(rws.getSocketUserName()).thenReturn("alice");

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED,
                                     "stale-frozen-at-mint", "alice", null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq("Worksheet/foo-7"), eq(agent)))
         .thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess, broadcast,
         mock(SecurityEngine.class), mock(InnerJoinService.class));
      svc.apply("TOK", agent, ed -> ed.removeColumn("T", "a"));

      verify(rws, never()).setSocketSessionId(any());
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

   /**
    * Bug #76744 (WBS-053): {@code add_join} named the same as one of its own source tables (e.g.
    * re-joining onto an existing "Query1" join, naming the new join "Query1" again) used to reach
    * {@link Worksheet#addAssembly}, which silently evicts and replaces the existing same-named
    * assembly. Because {@link CompositeTableAssembly} resolves its sources by name lazily on every
    * call, the new join's own name then resolves to itself, and any recursive traversal over
    * sources (e.g. {@code checkValidity()}) recurses forever, terminating only in an uncaught
    * {@link StackOverflowError} — with the original assembly already permanently evicted and
    * unrecoverable. Must be rejected before {@code ws.addAssembly} ever runs.
    */
   @Test
   void addJoinRejectsSelfReferencingNameAndDoesNotPoisonAssemblyLookup() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly customers = TestWorksheets.tableWithColumns(
         ws, "CUSTOMERS1", "REGION_ID");
      EmbeddedTableAssembly orders = TestWorksheets.tableWithColumns(ws, "ORDERS1", "REGION_ID");
      ws.addAssembly(customers);
      ws.addAssembly(orders);

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

      // First build the pre-existing "Query1" join the reporter's repro joins onto.
      svc.apply("TOK", agent,
                ed -> ed.addJoin("Query1", "CUSTOMERS1", "REGION_ID",
                                 "ORDERS1", "REGION_ID", "INNER", null, null));
      TableAssembly originalQuery1 = (TableAssembly) ws.getAssembly("Query1");
      assertNotNull(originalQuery1);

      // Naming the new join "Query1" again, with "Query1" itself as one of its own sources.
      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent,
                         ed -> ed.addJoin("Query1", "Query1", "REGION_ID",
                                          "ORDERS1", "REGION_ID", "INNER", null, null)));
      assertTrue(ex.getMessage().contains("Query1"));

      // The pre-existing join must never have been evicted/replaced.
      assertSame(originalQuery1, ws.getAssembly("Query1"));
      assertEquals(3, ws.getAssemblies().length);

      // The corrupted-state symptom this fix prevents: checkValidity() must not stack-overflow.
      assertDoesNotThrow(() -> originalQuery1.checkValidity(true));
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
                ed -> ed.editNamedGroup("StateRegion", null,
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
    * Bug #76731-WBS-051: a {@link GroupRef} bound to a named group clones the mapping once
    * at {@code set_group_aggregate} time ({@code GroupRef.update(Worksheet)}) and never reads
    * it live. Before this fix, {@code editNamedGroup} replaced only the named-group assembly's
    * own {@code NamedGroupInfo} — a genuinely standalone table (not a sub-table of any
    * JOIN/CONCATENATED/MIRROR elsewhere in the worksheet, so never incidentally swept by
    * {@code refreshAssemblies}'s {@code checkValidity}/{@code getTableAssemblies} side effect)
    * stayed frozen on the pre-edit mapping indefinitely. This is deliberately NOT a
    * composite-table sub-table, since that case already happened to pass before this fix and
    * would mask a regression here.
    */
   @Test
   void editNamedGroupRefreshesStandaloneLeafTableGroupRefImmediately() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t1 = TestWorksheets.tableWithColumns(ws, "T1", "state");
      ws.addAssembly(t1);

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
                ed -> ed.addNamedGroup("G", null, null, "string",
                                       List.of(new WorksheetMutationSupport.GroupMapping(
                                          "West", List.of("CA"))),
                                       false));
      svc.apply("TOK", agent,
                ed -> ed.setGroupAggregate("T1",
                   List.of(new WorksheetMutationSupport.GroupSpec("state", null, "G")),
                   List.of()));
      svc.apply("TOK", agent,
                ed -> ed.editNamedGroup("G", null,
                                        List.of(new WorksheetMutationSupport.GroupMapping(
                                           "East", List.of("NY"))),
                                        false));

      GroupRef t1Group = ((TableAssembly) ws.getAssembly("T1")).getAggregateInfo().getGroups()[0];
      assertNull(t1Group.getNamedGroupInfo().getGroupCondition("West"),
                 "T1's GroupRef clone must not still report the pre-edit mapping");
      assertNotNull(t1Group.getNamedGroupInfo().getGroupCondition("East"),
                    "T1's GroupRef clone must pick up the new mapping immediately, without " +
                    "needing set_group_aggregate reissued on T1");
   }

   /**
    * Companion to {@link #editNamedGroupRefreshesStandaloneLeafTableGroupRefImmediately}: a
    * table that IS a JOIN sub-table already self-healed before this fix (incidentally, via
    * {@code refreshAssemblies}'s {@code checkValidity}/{@code getTableAssemblies} sweep of its
    * owning composite table) — confirms the new explicit sweep in {@code editNamedGroup} doesn't
    * break that pre-existing path.
    */
   @Test
   void editNamedGroupRefreshesJoinSubtableGroupRefToo() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t2 = TestWorksheets.tableWithColumns(ws, "T2", "state");
      EmbeddedTableAssembly t3 = TestWorksheets.tableWithColumns(ws, "T3", "state");
      ws.addAssembly(t2);
      ws.addAssembly(t3);

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

      svc.apply("TOK", agent,
                ed -> ed.addNamedGroup("G", null, null, "string",
                                       List.of(new WorksheetMutationSupport.GroupMapping(
                                          "West", List.of("CA"))),
                                       false));
      svc.apply("TOK", agent,
                ed -> ed.setGroupAggregate("T2",
                   List.of(new WorksheetMutationSupport.GroupSpec("state", null, "G")),
                   List.of()));
      svc.apply("TOK", agent,
                ed -> ed.addJoin("JOINED", "T2", "state", "T3", "state", "INNER", null, null));
      svc.apply("TOK", agent,
                ed -> ed.editNamedGroup("G", null,
                                        List.of(new WorksheetMutationSupport.GroupMapping(
                                           "East", List.of("NY"))),
                                        false));

      GroupRef t2Group = ((TableAssembly) ws.getAssembly("T2")).getAggregateInfo().getGroups()[0];
      assertNull(t2Group.getNamedGroupInfo().getGroupCondition("West"));
      assertNotNull(t2Group.getNamedGroupInfo().getGroupCondition("East"));
   }

   /**
    * Locks in that the fix's flat {@code ws.getAssemblies()} sweep reaches a table nested two
    * composite levels deep (a leaf joined into {@code J1}, itself joined into {@code J2}) without
    * needing to walk composite structure recursively — the refuter's own repro shape, since a
    * fix that only checked direct sub-tables would still miss this case.
    */
   @Test
   void editNamedGroupRefreshesNestedCompositeLeafGroupRef() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t5 = TestWorksheets.tableWithColumns(ws, "T5", "state", "k1");
      EmbeddedTableAssembly t6 = TestWorksheets.tableWithColumns(ws, "T6", "k1", "k2");
      EmbeddedTableAssembly t7 = TestWorksheets.tableWithColumns(ws, "T7", "k2");
      ws.addAssembly(t5);
      ws.addAssembly(t6);
      ws.addAssembly(t7);

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

      svc.apply("TOK", agent,
                ed -> ed.addNamedGroup("G", null, null, "string",
                                       List.of(new WorksheetMutationSupport.GroupMapping(
                                          "West", List.of("CA"))),
                                       false));
      svc.apply("TOK", agent,
                ed -> ed.setGroupAggregate("T5",
                   List.of(new WorksheetMutationSupport.GroupSpec("state", null, "G")),
                   List.of()));
      // T5 is a sub-table of J1, which is itself a sub-table of J2 -- two composite levels deep.
      svc.apply("TOK", agent,
                ed -> ed.addJoin("J1", "T5", "k1", "T6", "k1", "INNER", null, null));
      svc.apply("TOK", agent,
                ed -> ed.addJoin("J2", "J1", "k2", "T7", "k2", "INNER", null, null));
      svc.apply("TOK", agent,
                ed -> ed.editNamedGroup("G", null,
                                        List.of(new WorksheetMutationSupport.GroupMapping(
                                           "East", List.of("NY"))),
                                        false));

      GroupRef t5Group = ((TableAssembly) ws.getAssembly("T5")).getAggregateInfo().getGroups()[0];
      assertNull(t5Group.getNamedGroupInfo().getGroupCondition("West"));
      assertNotNull(t5Group.getNamedGroupInfo().getGroupCondition("East"));
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
    * Bug #76744 (WBS-053): the multi-table {@code joinPaths} overload bypasses
    * {@code placeAssembly} entirely and calls {@code ws.addAssembly(join)} directly, so it needs
    * its own self-reference check — the existing {@code catch(Exception e)} guard around
    * {@code editExistingJoinTable} runs too late; by then {@code ws.addAssembly} has already
    * evicted the original same-named assembly. Uses a real {@link InnerJoinService} (not a mock)
    * so the test cannot pass merely because a mocked {@code editExistingJoinTable} never touches
    * the worksheet.
    */
   @Test
   void addJoinWithPathsRejectsSelfReferencingNameAndDoesNotPoisonAssemblyLookup() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly customers = TestWorksheets.tableWithColumns(
         ws, "CUSTOMERS1", "REGION_ID");
      EmbeddedTableAssembly orders = TestWorksheets.tableWithColumns(ws, "ORDERS1", "REGION_ID");
      ws.addAssembly(customers);
      ws.addAssembly(orders);

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

      // Pre-existing "Query1" join, built via the joinPaths overload itself.
      svc.apply("TOK", agent,
                ed -> ed.addJoin("Query1", List.of(new WorksheetMutationSupport.JoinPathSpec(
                   "CUSTOMERS1", "REGION_ID", "ORDERS1", "REGION_ID", "INNER"))));
      TableAssembly originalQuery1 = (TableAssembly) ws.getAssembly("Query1");
      assertNotNull(originalQuery1);

      List<WorksheetMutationSupport.JoinPathSpec> selfReferencingPaths = List.of(
         new WorksheetMutationSupport.JoinPathSpec(
            "Query1", "REGION_ID", "ORDERS1", "REGION_ID", "INNER"));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addJoin("Query1", selfReferencingPaths)));
      assertTrue(ex.getMessage().contains("Query1"));

      assertSame(originalQuery1, ws.getAssembly("Query1"));
      assertEquals(3, ws.getAssemblies().length);
      assertDoesNotThrow(() -> originalQuery1.checkValidity(true));
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

   // Bug #76730 WBS-050: addCrossJoin()/addMergeJoin() built their TableAssemblyOperator.Operator
   // without ever calling setLeftTable()/setRightTable(), unlike the two-table addJoin() overload
   // (~line 774-775) and the multi-table addJoin(joinPaths) path (~line 870-871), both of which do.
   // That left the operator's own leftTable/rightTable null for the life of the join, which in
   // turn made editJoin() silently no-op on a CROSS join (it read the null names and wrote to a
   // brand-new (null,null) map entry instead of the real edge) and made
   // WorksheetReadService.readJoins() filter the edge out of the model entirely (it skips any
   // operator with a null leftTable/rightTable).

   @Test
   void addCrossJoinPopulatesOperatorTableNames() throws Exception {
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
         mock(SheetAgentBroadcastService.class), securityEngine, mock(InnerJoinService.class));

      svc.apply("TOK", agent, ed -> ed.addCrossJoin("CJ", "A", "B"));

      RelationalJoinTableAssembly join = (RelationalJoinTableAssembly) ws.getAssembly("CJ");
      assertNotNull(join);
      TableAssemblyOperator.Operator op = join.getOperator(0).getOperator(0);
      assertEquals("A", op.getLeftTable());
      assertEquals("B", op.getRightTable());
   }

   @Test
   void addMergeJoinPopulatesOperatorTableNames() throws Exception {
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

      svc.apply("TOK", agent, ed -> ed.addMergeJoin("MJ", new String[]{ "A", "B", "C" }));

      MergeJoinTableAssembly join = (MergeJoinTableAssembly) ws.getAssembly("MJ");
      assertNotNull(join);
      TableAssemblyOperator.Operator op0 = join.getOperator(0).getOperator(0);
      assertEquals("A", op0.getLeftTable());
      assertEquals("B", op0.getRightTable());
      TableAssemblyOperator.Operator op1 = join.getOperator(1).getOperator(0);
      assertEquals("B", op1.getLeftTable());
      assertEquals("C", op1.getRightTable());
   }

   @Test
   void editJoinOnCrossJoinActuallyUpdatesTheRealEdge() throws Exception {
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
         mock(SheetAgentBroadcastService.class), securityEngine, mock(InnerJoinService.class));

      svc.apply("TOK", agent, ed -> ed.addCrossJoin("CJ", "A", "B"));
      svc.apply("TOK", agent, ed -> ed.editJoin("CJ", "id", "id", "INNER", null, null));

      RelationalJoinTableAssembly join = (RelationalJoinTableAssembly) ws.getAssembly("CJ");
      TableAssemblyOperator.Operator realOp = join.getOperator("A", "B").getOperator(0);
      assertEquals(TableAssemblyOperator.INNER_JOIN, realOp.getOperation(),
                   "the edit must land on the real (A,B) edge, not a discarded (null,null) one");
      assertEquals("id", realOp.getLeftAttribute().getAttribute());
      assertEquals("id", realOp.getRightAttribute().getAttribute());
      assertNull(join.getOperator((String) null, (String) null),
                 "editJoin must not create an orphan (null,null) operator entry");
   }

   @Test
   void editJoinOnMergeJoinThrowsAccurateMessage() throws Exception {
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

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      svc.apply("TOK", agent, ed -> ed.addMergeJoin("MJ", new String[]{ "A", "B" }));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.editJoin("MJ", "id", "id", "INNER", null, null)));
      assertTrue(ex.getMessage().contains("MERGE"));
      assertTrue(ex.getMessage().contains("position"));
      assertFalse(ex.getMessage().contains("not found"));
   }

   /**
    * Bug #76744 (WBS-053): the self-reference collision check lives in {@code placeAssembly}, the
    * shared single-assembly registration helper used by every {@code add*} creator besides the
    * {@code joinPaths} overload (which has its own check) — {@code addCrossJoin} is one of those
    * shared callers, so it must be protected too, confirming the fix is not join-overload-specific.
    */
   @Test
   void addCrossJoinRejectsSelfReferencingName() throws Exception {
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
         mock(SheetAgentBroadcastService.class), securityEngine, mock(InnerJoinService.class));

      // "A" naming itself as the new cross join's own leftTable.
      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addCrossJoin("A", "A", "B")));
      assertTrue(ex.getMessage().contains("A"));

      assertSame(a, ws.getAssembly("A"));
      assertSame(b, ws.getAssembly("B"));
      assertEquals(2, ws.getAssemblies().length);
   }

   /**
    * Regression guard for the WBS-053 self-reference fix: an ordinary, non-colliding
    * {@code add_join} call (the ordinary case that check must not reject) must still succeed.
    */
   @Test
   void addJoinSucceedsWithNonCollidingName() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly customers = TestWorksheets.tableWithColumns(
         ws, "CUSTOMERS1", "REGION_ID");
      EmbeddedTableAssembly orders = TestWorksheets.tableWithColumns(ws, "ORDERS1", "REGION_ID");
      ws.addAssembly(customers);
      ws.addAssembly(orders);

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
                ed -> ed.addJoin("Query1", "CUSTOMERS1", "REGION_ID",
                                 "ORDERS1", "REGION_ID", "INNER", null, null));

      RelationalJoinTableAssembly join = (RelationalJoinTableAssembly) ws.getAssembly("Query1");
      assertNotNull(join);
      assertEquals(2, join.getTableAssemblies().length);
      assertEquals(3, ws.getAssemblies().length);
   }

   /**
    * Bug #76788 (WBS-065): unlike WBS-053's self-reference guard (which only rejects a new
    * assembly whose name collides with one of its own declared sources), naming a new join after
    * a completely unrelated, pre-existing assembly used to reach {@link Worksheet#addAssembly},
    * which silently evicts and replaces it. Reproduces the reported live transcript: an existing
    * "CrossAB" join (sources CUSTOMERS1+CONTACTS1) gets silently destroyed by a second, unrelated
    * {@code add_join} call also named "CrossAB" (sources CONTACTS1+ORDERS1) -- "CrossAB" is not
    * one of the new join's own sources, so the self-reference check alone does not catch this.
    */
   @Test
   void addJoinRejectsNameCollisionWithUnrelatedExistingAssembly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly customers = TestWorksheets.tableWithColumns(
         ws, "CUSTOMERS1", "REGION_ID");
      EmbeddedTableAssembly contacts = TestWorksheets.tableWithColumns(
         ws, "CONTACTS1", "REGION_ID");
      EmbeddedTableAssembly orders = TestWorksheets.tableWithColumns(ws, "ORDERS1", "REGION_ID");
      ws.addAssembly(customers);
      ws.addAssembly(contacts);
      ws.addAssembly(orders);

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
                ed -> ed.addJoin("CrossAB", "CUSTOMERS1", "REGION_ID",
                                 "CONTACTS1", "REGION_ID", "INNER", null, null));
      TableAssembly originalCrossAB = (TableAssembly) ws.getAssembly("CrossAB");
      assertNotNull(originalCrossAB);

      // A second, unrelated add_join also named "CrossAB" -- neither of its own sources
      // (CONTACTS1/ORDERS1) is "CrossAB" itself, so this is not a self-reference.
      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent,
                         ed -> ed.addJoin("CrossAB", "CONTACTS1", "REGION_ID",
                                          "ORDERS1", "REGION_ID", "INNER", null, null)));
      assertTrue(ex.getMessage().contains("CrossAB"));

      assertSame(originalCrossAB, ws.getAssembly("CrossAB"));
      assertEquals(4, ws.getAssemblies().length);
   }

   /**
    * Bug #76788 (WBS-065): the {@code joinPaths} multi-table overload bypasses
    * {@code placeAssembly} entirely (see {@link
    * #addJoinWithPathsRejectsSelfReferencingNameAndDoesNotPoisonAssemblyLookup}), so it needs its
    * own, independently-inserted name-collision check -- fixing {@code placeAssembly} alone does
    * not cover this path. Uses a real {@link InnerJoinService} (not a mock), per that same test's
    * rationale.
    */
   @Test
   void addJoinWithPathsRejectsNameCollisionWithUnrelatedExistingAssembly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly customers = TestWorksheets.tableWithColumns(
         ws, "CUSTOMERS2", "REGION_ID");
      EmbeddedTableAssembly contacts = TestWorksheets.tableWithColumns(
         ws, "CONTACTS2", "REGION_ID");
      EmbeddedTableAssembly orders = TestWorksheets.tableWithColumns(ws, "ORDERS2", "REGION_ID");
      ws.addAssembly(customers);
      ws.addAssembly(contacts);
      ws.addAssembly(orders);

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

      svc.apply("TOK", agent,
                ed -> ed.addJoin("CrossAB2", List.of(new WorksheetMutationSupport.JoinPathSpec(
                   "CUSTOMERS2", "REGION_ID", "CONTACTS2", "REGION_ID", "INNER"))));
      TableAssembly originalCrossAB2 = (TableAssembly) ws.getAssembly("CrossAB2");
      assertNotNull(originalCrossAB2);

      List<WorksheetMutationSupport.JoinPathSpec> unrelatedPaths = List.of(
         new WorksheetMutationSupport.JoinPathSpec(
            "CONTACTS2", "REGION_ID", "ORDERS2", "REGION_ID", "INNER"));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addJoin("CrossAB2", unrelatedPaths)));
      assertTrue(ex.getMessage().contains("CrossAB2"));

      assertSame(originalCrossAB2, ws.getAssembly("CrossAB2"));
      assertEquals(4, ws.getAssemblies().length);
   }

   /**
    * Bug #76788 (WBS-065): confirms the {@code placeAssembly} fix is not {@code add_join}-specific
    * -- {@code add_merge_join} shares the same {@code placeAssembly} call and must be rejected the
    * same way when named after any unrelated, pre-existing assembly (not just another join).
    */
   @Test
   void addMergeJoinRejectsNameCollisionWithUnrelatedExistingAssembly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly preExisting = TestWorksheets.tableWithColumns(ws, "CrossAB3", "id");
      EmbeddedTableAssembly a = TestWorksheets.tableWithColumns(ws, "A", "id");
      EmbeddedTableAssembly b = TestWorksheets.tableWithColumns(ws, "B", "id");
      ws.addAssembly(preExisting);
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

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addMergeJoin("CrossAB3", new String[]{ "A", "B" })));
      assertTrue(ex.getMessage().contains("CrossAB3"));

      assertSame(preExisting, ws.getAssembly("CrossAB3"));
      assertEquals(3, ws.getAssemblies().length);
   }

   /**
    * Regression guard for the WBS-065 name-collision fix: an ordinary, non-colliding
    * {@code add_merge_join} call must still succeed.
    */
   @Test
   void addMergeJoinSucceedsWithNonCollidingName() throws Exception {
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

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      svc.apply("TOK", agent, ed -> ed.addMergeJoin("MJ", new String[]{ "A", "B" }));

      MergeJoinTableAssembly join = (MergeJoinTableAssembly) ws.getAssembly("MJ");
      assertNotNull(join);
      assertEquals(3, ws.getAssemblies().length);
   }

   // -----------------------------------------------------------------------
   // Bug #76788 (WBS-066): add_table_to_join / add_table_to_merge_join
   // -----------------------------------------------------------------------

   /**
    * Uses a REAL {@link InnerJoinService} (not a mock) so the actual
    * {@code editExistingJoinTable} wiring is exercised. Asserts on the ORIGINAL edge's operator
    * type/keys, not just the source count and the new edge -- this is the exact test gap the
    * refutation found: a delta-only {@code noperator} silently replaces every pre-existing edge
    * with an unconditional CROSS_JOIN (via {@code AbstractJoinTableAssembly.removeOperator}'s
    * zero-operator safety net and the method's own orphaned-tables reconciliation loop), and a
    * test that only checks source count / the new edge's keys would pass against that defective
    * behavior.
    */
   @Test
   void addTableToJoinExtendsExistingJoinInPlace() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly customers = TestWorksheets.tableWithColumns(
         ws, "CUSTOMERS1", "CUSTOMER_ID", "NAME");
      EmbeddedTableAssembly contacts = TestWorksheets.tableWithColumns(
         ws, "CONTACTS1", "CUSTOMER_ID", "PHONE");
      EmbeddedTableAssembly orders = TestWorksheets.tableWithColumns(
         ws, "ORDERS1", "CUSTOMER_ID", "ORDER_ID");
      ws.addAssembly(customers);
      ws.addAssembly(contacts);
      ws.addAssembly(orders);

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

      svc.apply("TOK", agent,
                ed -> ed.addJoin("Query1", "CUSTOMERS1", "CUSTOMER_ID",
                                 "CONTACTS1", "CUSTOMER_ID", "INNER", null, null));
      svc.apply("TOK", agent,
                ed -> ed.addTableToJoin("Query1", "CUSTOMERS1", "CUSTOMER_ID",
                                        "ORDERS1", "CUSTOMER_ID", "INNER", null, null));

      RelationalJoinTableAssembly joined = (RelationalJoinTableAssembly) ws.getAssembly("Query1");
      assertNotNull(joined, "add_table_to_join must extend the SAME assembly, not create a new one");
      assertEquals(3, joined.getTableAssemblies().length);

      TableAssemblyOperator original = joined.getOperator("CUSTOMERS1", "CONTACTS1");
      assertNotNull(original, "the original edge must survive, not be dropped");
      assertEquals(1, original.getOperatorCount());
      assertEquals(TableAssemblyOperator.INNER_JOIN, original.getOperator(0).getOperation(),
                   "the original edge must keep its own join type, not become a CROSS_JOIN");
      assertEquals("CUSTOMER_ID", original.getOperator(0).getLeftAttribute().getAttribute());
      assertEquals("CUSTOMER_ID", original.getOperator(0).getRightAttribute().getAttribute());

      TableAssemblyOperator added = joined.getOperator("CUSTOMERS1", "ORDERS1");
      assertNotNull(added, "the new edge must be present");
      assertEquals(1, added.getOperatorCount());
      assertEquals(TableAssemblyOperator.INNER_JOIN, added.getOperator(0).getOperation());
      assertEquals("CUSTOMER_ID", added.getOperator(0).getLeftAttribute().getAttribute());
      assertEquals("CUSTOMER_ID", added.getOperator(0).getRightAttribute().getAttribute());
   }

   @Test
   void addTableToJoinRejectsUnknownExistingTable() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly customers = TestWorksheets.tableWithColumns(
         ws, "CUSTOMERS1", "CUSTOMER_ID");
      EmbeddedTableAssembly contacts = TestWorksheets.tableWithColumns(
         ws, "CONTACTS1", "CUSTOMER_ID");
      ws.addAssembly(customers);
      ws.addAssembly(contacts);

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

      svc.apply("TOK", agent,
                ed -> ed.addJoin("Query1", "CUSTOMERS1", "CUSTOMER_ID",
                                 "CONTACTS1", "CUSTOMER_ID", "INNER", null, null));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent,
                          ed -> ed.addTableToJoin("Query1", "BOGUS", "CUSTOMER_ID",
                                                  "ORDERS1", "CUSTOMER_ID", "INNER", null, null)));
      assertTrue(ex.getMessage().contains("BOGUS"));

      RelationalJoinTableAssembly joined = (RelationalJoinTableAssembly) ws.getAssembly("Query1");
      assertEquals(2, joined.getTableAssemblies().length, "a rejected call must not mutate the join");
   }

   @Test
   void addTableToJoinRejectsTableAlreadyPresent() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly customers = TestWorksheets.tableWithColumns(
         ws, "CUSTOMERS1", "CUSTOMER_ID");
      EmbeddedTableAssembly contacts = TestWorksheets.tableWithColumns(
         ws, "CONTACTS1", "CUSTOMER_ID");
      ws.addAssembly(customers);
      ws.addAssembly(contacts);

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

      svc.apply("TOK", agent,
                ed -> ed.addJoin("Query1", "CUSTOMERS1", "CUSTOMER_ID",
                                 "CONTACTS1", "CUSTOMER_ID", "INNER", null, null));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent,
                          ed -> ed.addTableToJoin("Query1", "CUSTOMERS1", "CUSTOMER_ID",
                                                  "CONTACTS1", "CUSTOMER_ID", "INNER", null, null)));
      assertTrue(ex.getMessage().contains("CONTACTS1"));
   }

   @Test
   void addTableToJoinRejectsMergeJoinTarget() throws Exception {
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

      svc.apply("TOK", agent, ed -> ed.addMergeJoin("MJ", new String[]{ "A", "B" }));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent,
                          ed -> ed.addTableToJoin("MJ", "A", "id", "C", "id", "INNER", null, null)));
      assertTrue(ex.getMessage().contains("add_table_to_merge_join"));
   }

   @Test
   void addTableToJoinRejectsCrossAndMergeJoinType() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly customers = TestWorksheets.tableWithColumns(
         ws, "CUSTOMERS1", "CUSTOMER_ID");
      EmbeddedTableAssembly contacts = TestWorksheets.tableWithColumns(
         ws, "CONTACTS1", "CUSTOMER_ID");
      EmbeddedTableAssembly orders = TestWorksheets.tableWithColumns(
         ws, "ORDERS1", "CUSTOMER_ID");
      ws.addAssembly(customers);
      ws.addAssembly(contacts);
      ws.addAssembly(orders);

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

      svc.apply("TOK", agent,
                ed -> ed.addJoin("Query1", "CUSTOMERS1", "CUSTOMER_ID",
                                 "CONTACTS1", "CUSTOMER_ID", "INNER", null, null));

      PairingException crossEx = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent,
                          ed -> ed.addTableToJoin("Query1", "CUSTOMERS1", "CUSTOMER_ID",
                                                  "ORDERS1", "CUSTOMER_ID", "CROSS", null, null)));
      assertTrue(crossEx.getMessage().contains("CROSS"));

      PairingException mergeEx = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent,
                          ed -> ed.addTableToJoin("Query1", "CUSTOMERS1", "CUSTOMER_ID",
                                                  "ORDERS1", "CUSTOMER_ID", "MERGE", null, null)));
      assertTrue(mergeEx.getMessage().contains("MERGE"));

      RelationalJoinTableAssembly joined = (RelationalJoinTableAssembly) ws.getAssembly("Query1");
      assertEquals(2, joined.getTableAssemblies().length, "both rejected calls must not mutate the join");
   }

   /**
    * Not a design gap (a table belonging to a different, unrelated join is already excluded by
    * the {@code existingTable} membership check against the TARGET join's own
    * {@code getTableNames()}) but cheap to add so this subvariant doesn't rely solely on
    * {@link #addTableToJoinRejectsUnknownExistingTable}'s "nonexistent name" coverage.
    */
   @Test
   void addTableToJoinRejectsExistingTableFromADifferentJoin() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly customersA = TestWorksheets.tableWithColumns(
         ws, "CUSTOMERS_A", "ID");
      EmbeddedTableAssembly contactsA = TestWorksheets.tableWithColumns(
         ws, "CONTACTS_A", "ID");
      EmbeddedTableAssembly customersB = TestWorksheets.tableWithColumns(
         ws, "CUSTOMERS_B", "ID");
      EmbeddedTableAssembly contactsB = TestWorksheets.tableWithColumns(
         ws, "CONTACTS_B", "ID");
      ws.addAssembly(customersA);
      ws.addAssembly(contactsA);
      ws.addAssembly(customersB);
      ws.addAssembly(contactsB);

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

      svc.apply("TOK", agent,
                ed -> ed.addJoin("JoinA", "CUSTOMERS_A", "ID", "CONTACTS_A", "ID", "INNER", null, null));
      svc.apply("TOK", agent,
                ed -> ed.addJoin("JoinB", "CUSTOMERS_B", "ID", "CONTACTS_B", "ID", "INNER", null, null));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent,
                          ed -> ed.addTableToJoin("JoinA", "CUSTOMERS_B", "ID",
                                                  "ORDERS_A", "ID", "INNER", null, null)));
      assertTrue(ex.getMessage().contains("CUSTOMERS_B"));

      RelationalJoinTableAssembly joinA = (RelationalJoinTableAssembly) ws.getAssembly("JoinA");
      assertEquals(2, joinA.getTableAssemblies().length, "a rejected call must not mutate JoinA");
      assertNotNull(joinA.getOperator("CUSTOMERS_A", "CONTACTS_A"));
   }
}
