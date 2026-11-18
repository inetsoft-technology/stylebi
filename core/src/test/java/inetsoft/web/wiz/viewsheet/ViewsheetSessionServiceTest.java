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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.web.viewsheet.controller.UndoRedoServiceProxy;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.command.LoadTableDataCommand;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.command.RemoveVSObjectCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Write coordination (2026-08-17-write-coordination-design.md / -implementation.md): {@code mutate}
 * is the choke point for every wiz agent write tool that does not go directly through
 * {@code VSObjectPropertyService}/{@code VSConditionDialogService}/
 * {@code ViewsheetPropertyDialogService} (chart binding, aesthetics, table binding, calc layout,
 * ...). Review finding on stylebi#4626: those three services bump the revision themselves, but
 * nothing bumped it for writes landing through this seam, so a human's dialog -- read at a
 * revision an agent write here never moved -- would still match on commit and silently overwrite
 * the agent's change. This is the fix.
 */
@WizAgentTestSupport
class ViewsheetSessionServiceTest {

   @Test
   void mutateBumpsTheWriteRevisionOnSuccess() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Viewsheet/foo-7", "alice~;~host-org",
                                      SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq("Viewsheet/foo-7"), eq(agent)))
         .thenReturn(rvs);

      ViewsheetSessionService svc = new ViewsheetSessionService(sessions, runtimeAccess, broadcast, mock(UndoRedoServiceProxy.class));
      svc.mutate("TOK", agent, (r, runtimeId, dispatcher) -> {});

      verify(rvs).bumpWriteRevision();
   }

   /**
    * The undo checkpoint and the broadcast happen even when the mutation only partially applied
    * (see the comment above the checkpoint call in {@code mutate}) -- the write revision must
    * follow the same rule. A partial edit is a real edit; a dialog holding a pre-edit revision
    * must not treat it as unchanged.
    */
   @Test
   void mutateBumpsTheWriteRevisionEvenWhenTheMutationThrows() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Viewsheet/foo-7", "alice~;~host-org",
                                      SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq("Viewsheet/foo-7"), eq(agent)))
         .thenReturn(rvs);

      ViewsheetSessionService svc = new ViewsheetSessionService(sessions, runtimeAccess, broadcast, mock(UndoRedoServiceProxy.class));

      assertThrows(RuntimeException.class, () -> svc.mutate("TOK", agent, (r, runtimeId, dispatcher) -> {
         throw new RuntimeException("partial failure mid-mutation");
      }));

      verify(rvs).bumpWriteRevision();
   }

   /**
    * Bug #80379: a successful agent edit hints the Composer to follow it to this sheet, once, and
    * after the refresh that shows the edit.
    */
   @Test
   void mutateHintsTheComposerToFocusTheEditedSheetOnSuccess80379() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Viewsheet/foo-7", "alice~;~host-org",
                                      SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq("Viewsheet/foo-7"), eq(agent)))
         .thenReturn(rvs);

      ViewsheetSessionService svc = new ViewsheetSessionService(
         sessions, runtimeAccess, broadcast, mock(UndoRedoServiceProxy.class));
      svc.mutate("TOK", agent, (r, runtimeId, dispatcher) -> {});

      org.mockito.InOrder order = inOrder(broadcast);
      order.verify(broadcast).broadcastRefresh(eq(rvs), eq(SheetType.VIEWSHEET),
                                               eq("Viewsheet/foo-7"), eq(agent), any());
      order.verify(broadcast).focusSheet(rvs, "Viewsheet/foo-7");
      verify(broadcast, times(1)).focusSheet(any(), any());
   }

   /** Bug #80379: a failed edit is repainted (see above) but must not move the user's tab. */
   @Test
   void mutateDoesNotHintTheComposerWhenTheMutationFails80379() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Viewsheet/foo-7", "alice~;~host-org",
                                      SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq("Viewsheet/foo-7"), eq(agent)))
         .thenReturn(rvs);

      ViewsheetSessionService svc = new ViewsheetSessionService(
         sessions, runtimeAccess, broadcast, mock(UndoRedoServiceProxy.class));

      assertThrows(RuntimeException.class, () -> svc.mutate("TOK", agent, (r, runtimeId, dispatcher) -> {
         throw new RuntimeException("partial failure mid-mutation");
      }));

      verify(broadcast).broadcastRefresh(any(), any(), any(), any(), any());
      verify(broadcast, never()).focusSheet(any(), any());
   }

   /**
    * VBS-003 review round 1 (important, confidence 85): a caught post-write refresh failure
    * dispatches a {@code MessageCommand} of {@code Type.WARNING}, but {@code mutate} discarded
    * whatever the dispatcher captured -- so a wiz-agent call that hit one of those guarded
    * failures returned a clean success indistinguishable from one where nothing degraded.
    * Drives the REAL {@link inetsoft.web.wiz.dispatch.CapturingCommandDispatcher} {@code mutate}
    * constructs internally (not a mock), so this proves the wiring, not just that some method
    * was called.
    */
   @Test
   void mutateReturnsTheDispatchersCapturedWarnings() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Viewsheet/foo-7", "alice~;~host-org",
                                      SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq("Viewsheet/foo-7"), eq(agent)))
         .thenReturn(rvs);

      ViewsheetSessionService svc = new ViewsheetSessionService(sessions, runtimeAccess, broadcast, mock(UndoRedoServiceProxy.class));

      List<String> warnings = svc.mutate("TOK", agent, (r, runtimeId, dispatcher) -> {
         MessageCommand warning = new MessageCommand();
         warning.setType(MessageCommand.Type.WARNING);
         warning.setMessage("Crosstab1: could not refresh the crosstab's aggregate info after " +
                            "the write; the calc field was saved");
         dispatcher.sendCommand(warning);
      });

      assertEquals(List.of("Crosstab1: could not refresh the crosstab's aggregate info after " +
                            "the write; the calc field was saved"), warnings);
   }

   /**
    * Bug #81569: what the paired viewer draws is read before the edit runs; an assembly the edit
    * shows again is added to it before the refresh, and the table data of the added assembly
    * follows the refresh next to the data the edit itself produced.
    */
   @Test
   void mutateAddsTheAssembliesTheEditShowsAgainBeforeTheRefresh() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      java.util.Set<String> drawnBefore = java.util.Set.of("Text2");
      when(broadcast.drawnAssemblyNames(rvs)).thenReturn(drawnBefore);
      CommandDispatcher.Command addedData = mock(CommandDispatcher.Command.class);
      when(broadcast.broadcastVisibilityChanges(eq(rvs), any(), any(), eq(drawnBefore), any()))
         .thenReturn(List.of(addedData));

      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Viewsheet/foo-7", "alice~;~host-org",
                                      SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq("Viewsheet/foo-7"), eq(agent)))
         .thenReturn(rvs);

      ViewsheetSessionService svc = new ViewsheetSessionService(sessions, runtimeAccess, broadcast, mock(UndoRedoServiceProxy.class));
      boolean[] snapshotBeforeEdit = new boolean[1];

      svc.mutate("TOK", agent, (r, runtimeId, dispatcher) ->
         snapshotBeforeEdit[0] = !mockingDetails(broadcast).getInvocations().stream()
            .noneMatch(i -> i.getMethod().getName().equals("drawnAssemblyNames")));

      assertTrue(snapshotBeforeEdit[0], "the drawn assemblies must be read before the edit runs");
      org.mockito.InOrder order = inOrder(broadcast);
      order.verify(broadcast).broadcastVisibilityChanges(eq(rvs), eq("Viewsheet/foo-7"),
                                                           eq(agent), eq(drawnBefore), any());
      order.verify(broadcast).broadcastRefresh(eq(rvs), eq(SheetType.VIEWSHEET),
                                               eq("Viewsheet/foo-7"), eq(agent),
                                               eq(List.of(addedData)));
   }

   /** A mutation that dispatches nothing must not report a phantom warning. */
   @Test
   void mutateReturnsAnEmptyListWhenNothingWarned() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Viewsheet/foo-7", "alice~;~host-org",
                                      SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq("Viewsheet/foo-7"), eq(agent)))
         .thenReturn(rvs);

      ViewsheetSessionService svc = new ViewsheetSessionService(sessions, runtimeAccess, broadcast, mock(UndoRedoServiceProxy.class));

      List<String> warnings = svc.mutate("TOK", agent, (r, runtimeId, dispatcher) -> {});

      assertTrue(warnings.isEmpty());
   }

   /**
    * A write whose browser session is gone is saved but not shown; the notice from the broadcast
    * has to reach the agent next to the mutation's own warnings instead of an empty list.
    */
   @Test
   void mutateAddsTheNotDeliveredNoticeToTheWarnings() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      when(broadcast.broadcastRefresh(any(), any(), any(), any(), any()))
         .thenReturn(SheetAgentBroadcastService.SESSION_GONE_NOTICE);

      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Viewsheet/foo-7", "alice~;~host-org",
                                      SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq("Viewsheet/foo-7"), eq(agent)))
         .thenReturn(rvs);

      ViewsheetSessionService svc = new ViewsheetSessionService(sessions, runtimeAccess, broadcast, mock(UndoRedoServiceProxy.class));

      List<String> warnings = svc.mutate("TOK", agent, (r, runtimeId, dispatcher) -> {});

      assertEquals(List.of(SheetAgentBroadcastService.SESSION_GONE_NOTICE), warnings);
   }

   /**
    * Review finding on the join-time-stamp fix: {@link JoinSession#socketSessionId()} is frozen
    * at pairing-mint time forever ({@code SheetSessionService.resolve()} copies the same value
    * forward on every call, never re-derives it). If {@code mutate} reapplied this session's own
    * frozen value unconditionally, it would silently UNDO a human's own later recovery -- a
    * manual Composer Refresh unconditionally re-stamps the runtime's socket from a live
    * dispatcher (see {@code CoreLifecycleService}/{@code VSLifecycleControllerService}), and the
    * very next agent call on the same un-re-paired token would stomp that fresh value right back
    * to the stale one this session has always carried. This is the exact scenario the
    * unconditional-overwrite version of this fix regressed: confirmed here that a runtime's
    * current (possibly human-healed) socket survives an agent mutation under a token whose own
    * recorded socket differs. The join-time fix instead lives in
    * {@code SheetJoinService.join} -- see {@code SheetJoinServiceTest}'s matching regression
    * test for the case this method's fill-only-if-null guard is deliberately NOT responsible for.
    */
   @Test
   void mutateNeverOvewritesTheRuntimesCurrentSocketSessionIdWithThisSessionsOwnFrozenValue()
      throws Exception
   {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      // Simulates a human's manual Refresh having just healed the runtime to the browser's
      // CURRENT live socket -- newer than anything this pairing token has ever recorded.
      when(rvs.getSocketSessionId()).thenReturn("human-healed-live-socket");
      when(rvs.getSocketUserName()).thenReturn("alice");
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      Principal agent = TestPrincipals.user("alice", "host-org");
      // This token's OWN socket, frozen at its mint -- stale relative to the runtime's current,
      // human-healed value.
      JoinSession s = new JoinSession("TOK", "Viewsheet/foo-7", "alice~;~host-org",
                                      SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED,
                                      "stale-frozen-at-mint", "alice", null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq("Viewsheet/foo-7"), eq(agent)))
         .thenReturn(rvs);

      ViewsheetSessionService svc = new ViewsheetSessionService(sessions, runtimeAccess, broadcast, mock(UndoRedoServiceProxy.class));
      svc.mutate("TOK", agent, (r, runtimeId, dispatcher) -> {});

      verify(rvs, never()).setSocketSessionId(any());
   }

   /**
    * {@code isSessionLive} is a bare passthrough to {@code SheetSessionService.isLive} -- no
    * owner/pane-scope check of its own -- for {@code LayoutSessionService}'s scheduled cleanup
    * sweep, which has no {@code Principal} and needs none: it is deciding whether to free a
    * cached preview-clone runtime, not authorizing an agent action.
    */
   @Test
   void isSessionLiveIsABarePassthroughToTheUnderlyingSessionStore() {
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      when(sessions.isLive("live-token")).thenReturn(true);
      when(sessions.isLive("dead-token")).thenReturn(false);

      ViewsheetSessionService svc = new ViewsheetSessionService(sessions, runtimeAccess, broadcast, mock(UndoRedoServiceProxy.class));

      assertTrue(svc.isSessionLive("live-token"));
      assertFalse(svc.isSessionLive("dead-token"));
   }

   /**
    * Bug #81307: a checkpoint holds the sheet definition only, so restoring it alone left a Form
    * table's pending edit in place while reporting undone:true. The restore must run the native
    * undo/redo service (whose refresh drops the pending edits) and report whether the pointer
    * moved.
    */
   @Test
   void restoreCheckpointRunsTheNativeUndoAndReportsThatThePointerMoved() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getID()).thenReturn("Viewsheet/foo-7");
      java.util.concurrent.atomic.AtomicInteger current =
         new java.util.concurrent.atomic.AtomicInteger(3);
      when(rvs.getCurrent()).thenAnswer(inv -> current.get());
      UndoRedoServiceProxy undoRedo = mock(UndoRedoServiceProxy.class);
      doAnswer(inv -> {
         current.decrementAndGet();
         return null;
      }).when(undoRedo).undo(eq("Viewsheet/foo-7"), any(), eq("link"), any());
      Principal agent = TestPrincipals.user("alice", "host-org");
      ViewsheetSessionService svc = new ViewsheetSessionService(
         mock(SheetSessionService.class), mock(SheetRuntimeAccess.class),
         mock(SheetAgentBroadcastService.class), undoRedo);

      assertTrue(svc.restoreCheckpoint(rvs, agent, "link", true).moved());

      verify(undoRedo).undo(eq("Viewsheet/foo-7"), eq(agent), eq("link"), any());
      verify(undoRedo, never()).redo(any(), any(), any(), any());
   }

   @Test
   void restoreCheckpointRunsTheNativeRedo() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getID()).thenReturn("Viewsheet/foo-7");
      java.util.concurrent.atomic.AtomicInteger current =
         new java.util.concurrent.atomic.AtomicInteger(2);
      when(rvs.getCurrent()).thenAnswer(inv -> current.get());
      UndoRedoServiceProxy undoRedo = mock(UndoRedoServiceProxy.class);
      doAnswer(inv -> {
         current.incrementAndGet();
         return null;
      }).when(undoRedo).redo(eq("Viewsheet/foo-7"), any(), any(), any());
      Principal agent = TestPrincipals.user("alice", "host-org");
      ViewsheetSessionService svc = new ViewsheetSessionService(
         mock(SheetSessionService.class), mock(SheetRuntimeAccess.class),
         mock(SheetAgentBroadcastService.class), undoRedo);

      assertTrue(svc.restoreCheckpoint(rvs, agent, "", false).moved());

      verify(undoRedo, never()).undo(any(), any(), any(), any());
   }

   @Test
   void restoreCheckpointReportsFalseWhenThePointerDidNotMove() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getID()).thenReturn("Viewsheet/foo-7");
      when(rvs.getCurrent()).thenReturn(0);
      Principal agent = TestPrincipals.user("alice", "host-org");
      ViewsheetSessionService svc = new ViewsheetSessionService(
         mock(SheetSessionService.class), mock(SheetRuntimeAccess.class),
         mock(SheetAgentBroadcastService.class), mock(UndoRedoServiceProxy.class));

      assertFalse(svc.restoreCheckpoint(rvs, agent, "", true).moved());
   }

   /**
    * The pointer has moved by the time the refresh dispatches an ERROR, so the error must not
    * fail the call: the caller still has to broadcast, and a retry would undo one more step.
    */
   @Test
   void restoreCheckpointStillReportsTheMoveWhenTheRefreshDispatchesAnError() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getID()).thenReturn("Viewsheet/foo-7");
      java.util.concurrent.atomic.AtomicInteger current =
         new java.util.concurrent.atomic.AtomicInteger(3);
      when(rvs.getCurrent()).thenAnswer(inv -> current.get());
      UndoRedoServiceProxy undoRedo = mock(UndoRedoServiceProxy.class);
      doAnswer(inv -> {
         current.decrementAndGet();
         MessageCommand error = new MessageCommand();
         error.setType(MessageCommand.Type.ERROR);
         error.setMessage("query failed");
         ((CommandDispatcher) inv.getArgument(3)).sendCommand(error);
         return null;
      }).when(undoRedo).undo(any(), any(), any(), any());
      Principal agent = TestPrincipals.user("alice", "host-org");
      ViewsheetSessionService svc = new ViewsheetSessionService(
         mock(SheetSessionService.class), mock(SheetRuntimeAccess.class),
         mock(SheetAgentBroadcastService.class), undoRedo);

      assertTrue(svc.restoreCheckpoint(rvs, agent, "", true).moved());
   }

   /**
    * Bug #81305: table, crosstab and calc table cells reach the paired canvas only through
    * LoadTableDataCommand, which the capturing dispatcher swallows, so the restore must hand the
    * ones the native refresh dispatched back to the caller -- for undo and redo alike -- and only
    * those: a RemoveVSObjectCommand is already sent by broadcastRemovedAssemblies.
    */
   @Test
   void restoreCheckpointReturnsTheTableDataTheUndoRefreshLoaded() throws Exception {
      assertTableDataReturned(true);
   }

   @Test
   void restoreCheckpointReturnsTheTableDataTheRedoRefreshLoaded() throws Exception {
      assertTableDataReturned(false);
   }

   private void assertTableDataReturned(boolean undo) throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getID()).thenReturn("Viewsheet/foo-7");
      java.util.concurrent.atomic.AtomicInteger current =
         new java.util.concurrent.atomic.AtomicInteger(3);
      when(rvs.getCurrent()).thenAnswer(inv -> current.get());
      LoadTableDataCommand table = mock(LoadTableDataCommand.class);
      LoadTableDataCommand crosstab = mock(LoadTableDataCommand.class);
      org.mockito.stubbing.Answer<Object> refresh = inv -> {
         current.addAndGet(undo ? -1 : 1);
         CommandDispatcher dispatcher = inv.getArgument(3);
         dispatcher.sendCommand("Table1", table);
         dispatcher.sendCommand("Crosstab1", crosstab);
         dispatcher.sendCommand("Gone1", new RemoveVSObjectCommand());
         return null;
      };
      UndoRedoServiceProxy undoRedo = mock(UndoRedoServiceProxy.class);
      doAnswer(refresh).when(undoRedo).undo(any(), any(), any(), any());
      doAnswer(refresh).when(undoRedo).redo(any(), any(), any(), any());
      Principal agent = TestPrincipals.user("alice", "host-org");
      ViewsheetSessionService svc = new ViewsheetSessionService(
         mock(SheetSessionService.class), mock(SheetRuntimeAccess.class),
         mock(SheetAgentBroadcastService.class), undoRedo);

      ViewsheetSessionService.CheckpointRestore restored =
         svc.restoreCheckpoint(rvs, agent, "", undo);

      assertTrue(restored.moved());
      assertEquals(2, restored.tableData().size());
      assertEquals("Table1", restored.tableData().get(0).getAssembly());
      assertSame(table, restored.tableData().get(0).getCommand());
      assertEquals("Crosstab1", restored.tableData().get(1).getAssembly());
      assertSame(crosstab, restored.tableData().get(1).getCommand());
   }
}
