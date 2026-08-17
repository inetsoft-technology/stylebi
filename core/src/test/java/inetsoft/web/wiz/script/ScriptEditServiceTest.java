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
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.CalculateRef;
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
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
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
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
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
    * write() now delegates a CALC_FIELD target to {@link CalcFieldService} for real, replacing
    * the Task-3-placeholder throw. Verifies the wiring, not {@code CalcFieldService}'s own
    * behavior (that is Task 2's coverage) -- the expression on the live {@link ExpressionRef}
    * must actually change.
    */
   @Test
   void writeDelegatesACalcFieldTargetToCalcFieldService() throws Exception {
      ExpressionRef inner = new ExpressionRef();
      inner.setName("Margin");
      inner.setExpression("field['PRICE'] - field['COST']");
      CalculateRef calc = new CalculateRef(true);
      calc.setDataRef(inner);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getCalcFields("Query1")).thenReturn(new CalculateRef[]{ calc });

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      ScriptEditService svc = new ScriptEditService(mock(SheetSessionService.class),
         mock(SheetRuntimeAccess.class), mock(SheetAgentBroadcastService.class));
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.CALC_FIELD, "Query1", "Margin");

      svc.write(rvs, target, "field['PRICE'] * 2");

      assertEquals("field['PRICE'] * 2", inner.getExpression());
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
