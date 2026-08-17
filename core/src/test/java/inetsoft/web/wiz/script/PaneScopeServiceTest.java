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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ScriptTarget.Kind#requiresPaneSession()} and {@link PaneScopeService#check}.
 *
 * <p>{@code Kind.requiresPaneSession()} is deliberately NOT {@code location() == null} -- see the
 * javadoc on that method and Ruling 3 in the G2 progress ledger.
 * {@link #expressionLevelKindsRequireAPaneSession()} exists specifically so that G2 Task 8, which
 * gives {@code WORKSHEET_EXPRESSION}/{@code WORKSHEET_CONDITION} a real {@code Location}, breaks
 * this test the moment it forgets to add those new {@code Location}s to
 * {@code Kind.EXPRESSION_LEVEL_LOCATIONS} -- rather than silently flipping both kinds out of
 * pane-scope enforcement with every other test still green.
 */
@Tag("core")
class PaneScopeServiceTest {

   @Test
   void expressionLevelKindsRequireAPaneSession() {
      for(ScriptTarget.Kind k : List.of(ScriptTarget.Kind.CALC_FIELD,
                                        ScriptTarget.Kind.WORKSHEET_EXPRESSION,
                                        ScriptTarget.Kind.WORKSHEET_CONDITION))
      {
         assertTrue(k.requiresPaneSession(), k.wireName());
      }
   }

   @Test
   void theFourViewsheetLevelKindsDoNot() {
      for(ScriptTarget.Kind k : List.of(ScriptTarget.Kind.VIEWSHEET_ON_INIT,
                                        ScriptTarget.Kind.VIEWSHEET_ON_LOAD,
                                        ScriptTarget.Kind.ASSEMBLY_MAIN,
                                        ScriptTarget.Kind.ASSEMBLY_ON_CLICK))
      {
         assertFalse(k.requiresPaneSession(), k.wireName());
      }
   }

   @Test
   void refusesACalcFieldFromAWholeSheetSessionWithTheReasonNotThePolicy() throws Exception {
      PaneScopeService service = new PaneScopeService(false);
      JoinSession wholeSheetSession = wholeSheetSession();
      ScriptTarget calcFieldTarget = ScriptTarget.of(ScriptTarget.Kind.CALC_FIELD, "Query1", "Margin");

      PairingException ex = assertThrows(PairingException.class,
         () -> service.check(wholeSheetSession, calcFieldTarget));

      assertTrue(ex.getMessage().contains("open its formula editor"), ex.getMessage());
      assertFalse(ex.getMessage().contains("require-script-pane"), ex.getMessage());
   }

   @Test
   void strictPostureAlsoRefusesViewsheetLevelKinds() throws Exception {
      JoinSession wholeSheetSession = wholeSheetSession();
      ScriptTarget onInitTarget = ScriptTarget.of(ScriptTarget.Kind.VIEWSHEET_ON_INIT, null);

      PaneScopeService strict = new PaneScopeService(true);
      assertThrows(PairingException.class, () -> strict.check(wholeSheetSession, onInitTarget));

      assertTrue(new PaneScopeService(false).check(wholeSheetSession, onInitTarget));
   }

   @Test
   void aPaneScopedSessionMaySatisfyAnExpressionLevelKind() throws Exception {
      EditorContext ctx = new EditorContext("calcField", "Query1", "Margin", null);
      JoinSession paneSession = new JoinSession("TOK", "Viewsheet/foo", "alice~;~host-org",
         SheetType.VIEWSHEET, 0L, Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, ctx);
      ScriptTarget calcFieldTarget = ScriptTarget.of(ScriptTarget.Kind.CALC_FIELD, "Query1", "Margin");

      assertTrue(new PaneScopeService(false).check(paneSession, calcFieldTarget));
   }

   private static JoinSession wholeSheetSession() {
      return new JoinSession("TOK", "Viewsheet/foo", "alice~;~host-org", SheetType.VIEWSHEET, 0L,
         Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, null);
   }
}
