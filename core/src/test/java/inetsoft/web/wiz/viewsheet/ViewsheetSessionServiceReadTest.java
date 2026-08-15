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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The read-only capturing entry point.
 *
 * <p>Some composer services return their result by <em>dispatching a command</em> rather than
 * returning a value — {@code VSTableLayoutService.getCellScript} and {@code getNamedGroup} are
 * the ones that matter here. Reading those needs a capturing dispatcher, which previously only
 * {@code mutate} supplied. Using {@code mutate} for a read would add a checkpoint to the user's
 * undo history for having <em>looked</em> at something, and broadcast a refresh for a change
 * that never happened.
 */
@Tag("core")
class ViewsheetSessionServiceReadTest {
   @Test
   void readSuppliesACapturingDispatcherWithoutCheckpointingOrBroadcasting() throws Exception {
      Harness h = harness();

      String seen = h.service.read("tok", principal(), (rvs, runtimeId, dispatcher) -> {
         assertNotNull(dispatcher, "a read needs the capturing dispatcher to see command results");
         assertEquals("rt1", runtimeId);
         return "read-result";
      });

      assertEquals("read-result", seen, "the value the read produced must come back");
      verify(h.rvs, never()).addCheckpoint(any());
      verify(h.broadcast, never()).broadcastRefresh(any(), any(), anyString(),
                                                    any(Principal.class));
   }

   @Test
   void readStillFailsLoudOnAnInvalidSession() {
      SheetSessionService sessions = mock(SheetSessionService.class);
      when(sessions.resolve(anyString(), anyString())).thenReturn(null);
      ViewsheetSessionService service = new ViewsheetSessionService(
         sessions, mock(SheetRuntimeAccess.class), mock(SheetAgentBroadcastService.class));

      assertThrows(PairingException.class,
                   () -> service.read("bad", principal(), (rvs, runtimeId, dispatcher) -> null));
   }

   private record Harness(ViewsheetSessionService service, RuntimeViewsheet rvs,
                          SheetAgentBroadcastService broadcast) {}

   private static Harness harness() throws Exception {
      JoinSession session = new JoinSession("tok", "rt1", "admin", SheetType.VIEWSHEET,
                                            Long.MAX_VALUE / 2, Long.MAX_VALUE / 2,
                                            JoinSession.ConnectionMode.PAIRED, null, null);
      SheetSessionService sessions = mock(SheetSessionService.class);
      when(sessions.resolve(anyString(), anyString())).thenReturn(session);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));

      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(runtimeAccess.getSheetForPairing(any(), anyString(), any(Principal.class)))
         .thenReturn(rvs);

      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      return new Harness(new ViewsheetSessionService(sessions, runtimeAccess, broadcast), rvs,
                         broadcast);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
