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
package inetsoft.web.wiz.script;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.pairing.TestPrincipals;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@WizAgentTestSupport
class ScriptEditServiceTest {

   @Test
   void applyOnRuntimeIfChangedBroadcastsWhenPredicateIsTrue() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Viewsheet/foo-7", "alice~;~host-org",
                                      SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq("Viewsheet/foo-7"), eq(agent)))
         .thenReturn(rvs);

      ScriptEditService svc = new ScriptEditService(sessions, runtimeAccess, broadcast);
      String result = svc.applyOnRuntimeIfChanged("TOK", agent, r -> "executed", r -> true);

      assertEquals("executed", result);
      verify(broadcast).broadcastRefresh(eq(rvs), eq(SheetType.VIEWSHEET), eq("Viewsheet/foo-7"), eq(agent));
   }

   @Test
   void applyOnRuntimeIfChangedSkipsBroadcastWhenPredicateIsFalse() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Viewsheet/foo-7", "alice~;~host-org",
                                      SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq("Viewsheet/foo-7"), eq(agent)))
         .thenReturn(rvs);

      ScriptEditService svc = new ScriptEditService(sessions, runtimeAccess, broadcast);
      String result = svc.applyOnRuntimeIfChanged("TOK", agent, r -> "no-op", r -> false);

      assertEquals("no-op", result);
      verifyNoInteractions(broadcast);
   }

   @Test
   void applyOnRuntimeIfChangedRejectsInvalidSession() {
      SheetSessionService sessions = mock(SheetSessionService.class);
      when(sessions.resolve(any(), any())).thenReturn(null);
      ScriptEditService svc = new ScriptEditService(sessions,
         mock(SheetRuntimeAccess.class), mock(SheetAgentBroadcastService.class));

      assertThrows(PairingException.class, () -> svc.applyOnRuntimeIfChanged(
         "BAD", TestPrincipals.user("alice", "host-org"), r -> "x", r -> true));
   }
}
