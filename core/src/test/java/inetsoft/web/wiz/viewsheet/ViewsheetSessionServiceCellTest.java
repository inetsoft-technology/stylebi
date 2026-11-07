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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The cell-scoped entry points of ViewsheetSessionService (Bug #80459). */
@WizAgentTestSupport
class ViewsheetSessionServiceCellTest {
   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private final RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
   private final SheetSessionService sessions = mock(SheetSessionService.class);

   private ViewsheetSessionService serviceFor(EditorContext ctx) throws Exception {
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = new JoinSession("TOK", "Viewsheet/foo-7", "alice~;~host-org",
                                      SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, ctx);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq("Viewsheet/foo-7"),
                                            eq(agent))).thenReturn(rvs);
      return new ViewsheetSessionService(sessions, runtimeAccess,
                                         mock(SheetAgentBroadcastService.class));
   }

   private static final EditorContext CELL = new EditorContext("calcTableCell", "Calc1", "1,2", null);

   @Test
   void aCellSessionServesItsOwnCellOnAllThreeEntryPoints() throws Exception {
      ViewsheetSessionService svc = serviceFor(CELL);
      AtomicBoolean scoped = new AtomicBoolean();

      assertSame(rvs, svc.resolveForCell("TOK", agent, "Calc1", 1, 2));
      assertEquals("r", svc.readForCell("TOK", agent, "Calc1", 1, 2, (r, id, d) -> "r"));
      svc.mutateForCell("TOK", agent, "Calc1", 1, 2, (r, id, d, cell) -> scoped.set(cell));
      assertTrue(scoped.get());
   }

   @Test
   void aCellSessionIsRefusedForAnotherCellOnAllThreeEntryPoints() throws Exception {
      ViewsheetSessionService svc = serviceFor(CELL);

      assertThrows(PairingException.class, () -> svc.resolveForCell("TOK", agent, "Calc1", 0, 0));
      assertThrows(PairingException.class,
                   () -> svc.readForCell("TOK", agent, "Calc1", 0, 0, (r, id, d) -> "r"));
      assertThrows(PairingException.class,
                   () -> svc.mutateForCell("TOK", agent, "Calc1", 0, 0, (r, id, d, c) -> {}));
   }

   @Test
   void aCellSessionIsStillRefusedByTheWholeSheetEntryPoints() throws Exception {
      ViewsheetSessionService svc = serviceFor(CELL);

      assertThrows(PairingException.class, () -> svc.resolve("TOK", agent));
      assertThrows(PairingException.class, () -> svc.runtimeId("TOK", agent));
      assertThrows(PairingException.class, () -> svc.mutate("TOK", agent, (r, id, d) -> {}));
      assertThrows(PairingException.class, () -> svc.read("TOK", agent, (r, id, d) -> "r"));
   }

   @Test
   void calcFieldAndAssemblyMainGrantsAreRefusedOnTheCellEntryPoints() throws Exception {
      for(EditorContext other : new EditorContext[] {
         new EditorContext("calcField", "Query1", "Margin", null),
         new EditorContext("assemblyMain", "Calc1", null, null) })
      {
         ViewsheetSessionService svc = serviceFor(other);

         assertThrows(PairingException.class,
                      () -> svc.resolveForCell("TOK", agent, "Calc1", 1, 2), other.kind());
         assertThrows(PairingException.class,
                      () -> svc.readForCell("TOK", agent, "Calc1", 1, 2, (r, id, d) -> "r"),
                      other.kind());
         assertThrows(PairingException.class,
                      () -> svc.mutateForCell("TOK", agent, "Calc1", 1, 2, (r, id, d, c) -> {}),
                      other.kind());
      }
   }

   @Test
   void aWholeSheetSessionStillServesAnyCell() throws Exception {
      ViewsheetSessionService svc = serviceFor(null);
      AtomicBoolean scoped = new AtomicBoolean(true);

      assertSame(rvs, svc.resolveForCell("TOK", agent, "Calc1", 5, 5));
      svc.mutateForCell("TOK", agent, "Calc1", 5, 5, (r, id, d, cell) -> scoped.set(cell));
      assertFalse(scoped.get());
   }
}
