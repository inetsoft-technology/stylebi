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

import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The calcTableCell grant: the narrow exemption and its fail-closed edges (Bug #80459). */
@Tag("core")
class PaneScopeServiceCellGrantTest {
   private static JoinSession session(EditorContext ctx) {
      return new JoinSession("TOK", "Viewsheet/foo", "alice~;~host-org", SheetType.VIEWSHEET, 0L,
         Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, ctx);
   }

   private static final EditorContext CELL =
      new EditorContext("calcTableCell", "Calc1", "2,1", null);

   @Test
   void passesOnlyTheExactAssemblyRowAndCol() throws Exception {
      JoinSession s = session(CELL);

      PaneScopeService.requireCellGrant(s, "Calc1", 2, 1);

      PairingException otherCell = assertThrows(PairingException.class,
         () -> PaneScopeService.requireCellGrant(s, "Calc1", 2, 2));
      assertTrue(otherCell.getMessage().contains("[2,1]") &&
                 otherCell.getMessage().contains("[2,2]"), otherCell.getMessage());
      assertThrows(PairingException.class,
                   () -> PaneScopeService.requireCellGrant(s, "Calc2", 2, 1));
   }

   @Test
   void wholeSheetAndUnresolvedSessionsPass() throws Exception {
      PaneScopeService.requireCellGrant(session(null), "Calc1", 0, 0);
      PaneScopeService.requireCellGrant(null, "Calc1", 0, 0);
   }

   @Test
   void everyOtherPaneGrantIsStillRefusedOnTheCellEndpoints() {
      for(EditorContext other : new EditorContext[] {
         new EditorContext("calcField", "Query1", "Margin", null),
         new EditorContext("assemblyMain", "Calc1", null, null),
         new EditorContext("assemblyOnClick", "Calc1", null, null),
         new EditorContext("worksheetExpression", "Q", "c", null),
         new EditorContext("viewsheetOnInit", null, null, null) })
      {
         assertThrows(PairingException.class,
            () -> PaneScopeService.requireCellGrant(session(other), "Calc1", 2, 1), other.kind());
      }
   }

   @Test
   void requireWholeSheetSessionStillRefusesACellGrantWithoutPointingAtTheToolbar() {
      PairingException ex = assertThrows(PairingException.class,
         () -> PaneScopeService.requireWholeSheetSession(session(CELL)));

      assertTrue(ex.getMessage().contains("[2,1]"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Done"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Cancel"), ex.getMessage());
      assertFalse(ex.getMessage().toLowerCase().contains("toolbar"), ex.getMessage());
   }

   @Test
   void aCellGrantMatchesNoScriptTargetAndTheMessageRedirects() throws Exception {
      for(ScriptTarget.Kind kind : ScriptTarget.Kind.values()) {
         ScriptTarget target;

         try {
            target = ScriptTarget.of(kind, "Calc1", kind.requiresPaneSession() ? "2,1" : null);
         }
         catch(PairingException unservable) {
            continue;
         }

         assertFalse(PaneScopeService.matchesGrant(CELL, target), kind.wireName());

         PairingException ex = assertThrows(PairingException.class,
            () -> new PaneScopeService(false).check(session(CELL), target));
         assertTrue(ex.getMessage().contains("set_calc_cell_script"), ex.getMessage());
      }
   }
}
