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
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.controller.UndoRedoServiceProxy;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #82419: {@link ViewsheetSessionService#batch} makes several {@code mutate} calls one undo
 * step. {@code mutate} alone adds one checkpoint and one broadcast per call; inside a batch the
 * inner calls defer both and the batch adds one of each when it ends.
 */
@WizAgentTestSupport
class ViewsheetSessionServiceBatchTest {
   private RuntimeViewsheet rvs;
   private SheetAgentBroadcastService broadcast;
   private ViewsheetSessionService svc;
   private final Principal agent = TestPrincipals.user("alice", "host-org");

   @BeforeEach
   void setUp() throws Exception {
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      broadcast = mock(SheetAgentBroadcastService.class);
      JoinSession s = new JoinSession("TOK", "Viewsheet/foo-7", "alice~;~host-org",
                                      SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq("Viewsheet/foo-7"),
                                            eq(agent)))
         .thenReturn(rvs);
      svc = new ViewsheetSessionService(sessions, runtimeAccess, broadcast,
                                        mock(UndoRedoServiceProxy.class));
   }

   private void mutateOnce() throws Exception {
      svc.mutate("TOK", agent, (r, runtimeId, dispatcher) -> {});
   }

   @Test
   void mutatesOutsideABatchStillCheckpointAndBroadcastEachTime() throws Exception {
      mutateOnce();
      mutateOnce();
      mutateOnce();

      verify(rvs, times(3)).addCheckpoint(any());
      verify(broadcast, times(3)).broadcastRefresh(any(), any(), any(), any(), any());
      verify(broadcast, times(3)).focusSheet(any(), any());
   }

   @Test
   void threeMutatesInABatchAreOneCheckpointAndOneBroadcast() throws Exception {
      ViewsheetSessionService.BatchResult<String> result = svc.batch("TOK", agent, () -> {
         mutateOnce();
         mutateOnce();
         mutateOnce();
         return "done";
      });

      assertEquals("done", result.value());
      assertEquals(1, result.checkpoints());
      verify(rvs, times(1)).addCheckpoint(any());
      verify(broadcast, times(1)).broadcastRefresh(any(), any(), any(), any(), any());
      verify(broadcast, times(1)).broadcastVisibilityChanges(any(), any(), any(), any(), any());
      verify(broadcast, times(1)).focusSheet(any(), any());
      // the write revision still moves for every write, so a human's stale dialog is refused
      verify(rvs, times(3)).bumpWriteRevision();
   }

   @Test
   void aBatchThatWritesNothingLeavesNoUndoStepAndNoBroadcast() throws Exception {
      ViewsheetSessionService.BatchResult<Void> result = svc.batch("TOK", agent, () -> null);

      assertEquals(0, result.checkpoints());
      verify(rvs, never()).addCheckpoint(any());
      verify(broadcast, never()).broadcastRefresh(any(), any(), any(), any(), any());
   }

   @Test
   void aBodyThatThrowsAfterAWriteStillGetsItsOneCheckpointAndTheExceptionPropagates()
      throws Exception
   {
      IllegalStateException e = assertThrows(IllegalStateException.class, () ->
         svc.batch("TOK", agent, () -> {
            mutateOnce();
            mutateOnce();
            throw new IllegalStateException("boom");
         }));

      assertEquals("boom", e.getMessage());
      verify(rvs, times(1)).addCheckpoint(any());
      verify(broadcast, times(1)).broadcastRefresh(any(), any(), any(), any(), any());
      // a failed body does not move the user's tab
      verify(broadcast, never()).focusSheet(any(), any());
   }

   @Test
   void aMutationThatFailsInsideABatchStillCountsAsAWrite() throws Exception {
      svc.batch("TOK", agent, () -> {
         assertThrows(RuntimeException.class, () -> svc.mutate(
            "TOK", agent, (r, runtimeId, dispatcher) -> {
               throw new RuntimeException("partial failure mid-mutation");
            }));
         return null;
      });

      verify(rvs, times(1)).addCheckpoint(any());
   }

   @Test
   void theBatchScopeEndsWithTheBatch() throws Exception {
      svc.batch("TOK", agent, () -> {
         mutateOnce();
         return null;
      });
      verify(rvs, times(1)).addCheckpoint(any());

      mutateOnce();

      verify(rvs, times(2)).addCheckpoint(any());
   }

   @Test
   void batchesCannotBeNested() throws Exception {
      assertThrows(IllegalStateException.class, () ->
         svc.batch("TOK", agent, () -> svc.batch("TOK", agent, () -> null)));
      verify(rvs, never()).addCheckpoint(any());
   }
}
