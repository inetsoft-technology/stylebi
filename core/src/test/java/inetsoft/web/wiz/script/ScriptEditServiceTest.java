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
import inetsoft.uql.viewsheet.Viewsheet;
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

   /**
    * Not just "throws" -- a CALC_FIELD target matches no case in write()'s switch STATEMENT,
    * so Java does not require exhaustiveness and an unhandled case would compile clean and
    * silently do nothing. This is reachable today: of()/resolve() build CALC_FIELD targets
    * happily since its Location is non-null. Refusing loudly here is the fix; asserting the
    * specific message (naming Task 3, not permanent) is what proves it isn't just an
    * accidental compile-time throw.
    */
   @Test
   void writeRefusesACalcFieldTargetUntilTask3WiresItIn() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(new Viewsheet());
      ScriptEditService svc = new ScriptEditService(mock(SheetSessionService.class),
         mock(SheetRuntimeAccess.class), mock(SheetAgentBroadcastService.class));
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.CALC_FIELD, "Query1", "Margin");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.write(rvs, target, "new text"));
      assertTrue(ex.getMessage().contains("Task 3"),
                 "must name what's missing, not just refuse: " + ex.getMessage());
   }

   /** setEnabled's refusal is PERMANENT -- a calc field has no per-field enable flag at all. */
   @Test
   void setEnabledRefusesACalcFieldTargetPermanently() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(new Viewsheet());
      ScriptEditService svc = new ScriptEditService(mock(SheetSessionService.class),
         mock(SheetRuntimeAccess.class), mock(SheetAgentBroadcastService.class));
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.CALC_FIELD, "Query1", "Margin");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.setEnabled(rvs, target, true));
      assertTrue(ex.getMessage().contains("enable flag"),
                 "must explain there is nothing to enable, not just refuse: " + ex.getMessage());
   }
}
