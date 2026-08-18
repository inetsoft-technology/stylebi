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
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Test;

import java.security.Principal;

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

      ViewsheetSessionService svc = new ViewsheetSessionService(sessions, runtimeAccess, broadcast);
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

      ViewsheetSessionService svc = new ViewsheetSessionService(sessions, runtimeAccess, broadcast);

      assertThrows(RuntimeException.class, () -> svc.mutate("TOK", agent, (r, runtimeId, dispatcher) -> {
         throw new RuntimeException("partial failure mid-mutation");
      }));

      verify(rvs).bumpWriteRevision();
   }
}
