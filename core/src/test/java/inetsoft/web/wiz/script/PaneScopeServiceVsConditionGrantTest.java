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

/** The vsConditionValue grant: exact (assembly, name) scope and fail-closed edges (Bug #80459). */
@Tag("core")
class PaneScopeServiceVsConditionGrantTest {
   private static JoinSession session(EditorContext ctx) {
      return new JoinSession("TOK", "Viewsheet/foo", "alice~;~host-org", SheetType.VIEWSHEET, 0L,
         Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, ctx);
   }

   private static final EditorContext FILTER =
      new EditorContext("vsConditionValue", "Table1", "filter", null);
   private static final EditorContext HIGHLIGHT =
      new EditorContext("vsConditionValue", "Table1", "highlight:Hi", null);

   private static ScriptTarget target(ScriptTarget.Kind kind, String assembly, String name)
      throws Exception
   {
      return ScriptTarget.of(kind, assembly, name);
   }

   @Test
   void matchesOnlyTheEqualAssemblyAndName() throws Exception {
      ScriptTarget.Kind k = ScriptTarget.Kind.VS_CONDITION_VALUE;

      assertTrue(PaneScopeService.matchesGrant(FILTER, target(k, "Table1", "filter")));
      assertTrue(PaneScopeService.matchesGrant(HIGHLIGHT, target(k, "Table1", "highlight:Hi")));

      assertFalse(PaneScopeService.matchesGrant(FILTER, target(k, "Table2", "filter")));
      assertFalse(PaneScopeService.matchesGrant(FILTER, target(k, "Table1", "highlight:Hi")));
      assertFalse(PaneScopeService.matchesGrant(HIGHLIGHT, target(k, "Table1", "filter")));
      assertFalse(PaneScopeService.matchesGrant(HIGHLIGHT, target(k, "Table1", "highlight:Lo")));
   }

   @Test
   void theTableAliasDoesNotApply() throws Exception {
      EditorContext withTable = new EditorContext("vsConditionValue", "Table1", "filter", "Other");

      assertTrue(PaneScopeService.matchesGrant(withTable,
         target(ScriptTarget.Kind.VS_CONDITION_VALUE, "Table1", "filter")));
      assertFalse(PaneScopeService.matchesGrant(withTable,
         target(ScriptTarget.Kind.VS_CONDITION_VALUE, "Other", "filter")));
   }

   @Test
   void theGrantNeverReachesAnotherKindOnTheSameAssembly() throws Exception {
      assertFalse(PaneScopeService.matchesGrant(FILTER,
         target(ScriptTarget.Kind.ASSEMBLY_MAIN, "Table1", null)));
      assertFalse(PaneScopeService.matchesGrant(FILTER,
         target(ScriptTarget.Kind.ASSEMBLY_ON_CLICK, "Table1", null)));
      assertFalse(PaneScopeService.matchesGrant(FILTER,
         target(ScriptTarget.Kind.CALC_FIELD, "Table1", "filter")));
      assertFalse(PaneScopeService.matchesGrant(FILTER,
         target(ScriptTarget.Kind.VIEWSHEET_ON_INIT, null, null)));
      assertFalse(PaneScopeService.matchesGrant(FILTER,
         target(ScriptTarget.Kind.VIEWSHEET_ON_LOAD, null, null)));
   }

   @Test
   void otherGrantsNeverReachTheKind() throws Exception {
      ScriptTarget t = target(ScriptTarget.Kind.VS_CONDITION_VALUE, "Table1", "filter");

      for(EditorContext other : new EditorContext[] {
         new EditorContext("assemblyMain", "Table1", null, null),
         new EditorContext("assemblyOnClick", "Table1", null, null),
         new EditorContext("calcField", "Table1", "filter", null),
         new EditorContext("worksheetConditionValue", "Table1", "filter", null),
         new EditorContext("viewsheetOnInit", null, null, null),
         new EditorContext("viewsheetOnLoad", null, null, null),
         new EditorContext("calcTableCell", "Table1", "0,0", null) })
      {
         assertFalse(PaneScopeService.matchesGrant(other, t), other.kind());
      }
   }

   /**
    * The old trailing {@code return true} matched everything for a kind no earlier branch
    * classified. For every kind the grant must match a fabricated different target only when it is
    * one of the two whole-viewsheet script kinds, whose kind equality is their whole identity.
    */
   @Test
   void everyKindHasAMatchesGrantBranch() throws Exception {
      for(ScriptTarget.Kind granted : ScriptTarget.Kind.values()) {
         EditorContext grant = new EditorContext(granted.wireName(), "A", "filter", null);

         for(ScriptTarget.Kind k : ScriptTarget.Kind.values()) {
            ScriptTarget fabricated = fabricate(k, "B");
            boolean viewsheetScript = granted == ScriptTarget.Kind.VIEWSHEET_ON_INIT ||
               granted == ScriptTarget.Kind.VIEWSHEET_ON_LOAD;

            if(viewsheetScript && granted == k) {
               continue;
            }

            assertFalse(PaneScopeService.matchesGrant(grant, fabricated),
               granted.wireName() + " grant matched " + k.wireName() + " on another assembly");
         }
      }
   }

   private static ScriptTarget fabricate(ScriptTarget.Kind k, String assembly) throws Exception {
      return switch(k) {
         case VIEWSHEET_ON_INIT, VIEWSHEET_ON_LOAD -> ScriptTarget.of(k, null, null);
         case ASSEMBLY_MAIN, ASSEMBLY_ON_CLICK -> ScriptTarget.of(k, assembly, null);
         case VS_CONDITION_VALUE -> ScriptTarget.of(k, assembly, "filter");
         default -> ScriptTarget.of(k, assembly, "n");
      };
   }

   @Test
   void checkRefusesAnotherTargetAndNamesTheGrant() throws Exception {
      PaneScopeService svc = new PaneScopeService(false);
      JoinSession s = session(HIGHLIGHT);

      assertTrue(svc.check(s,
         target(ScriptTarget.Kind.VS_CONDITION_VALUE, "Table1", "highlight:Hi")));

      PairingException other = assertThrows(PairingException.class, () -> svc.check(s,
         target(ScriptTarget.Kind.VS_CONDITION_VALUE, "Table1", "filter")));
      assertTrue(other.getMessage().contains("This session is paired to"), other.getMessage());
      assertTrue(other.getMessage().contains("highlight 'Hi'"), other.getMessage());
      assertTrue(other.getMessage().contains("may not act on"), other.getMessage());
      assertFalse(other.getMessage().contains("'vsConditionValue'"), other.getMessage());

      PairingException main = assertThrows(PairingException.class, () -> svc.check(s,
         target(ScriptTarget.Kind.ASSEMBLY_MAIN, "Table1", null)));
      assertTrue(main.getMessage().contains("highlight 'Hi'"), main.getMessage());
   }

   @Test
   void aWholeSheetSessionCannotReachTheKind() throws Exception {
      PaneScopeService svc = new PaneScopeService(false);
      PairingException ex = assertThrows(PairingException.class, () -> svc.check(session(null),
         target(ScriptTarget.Kind.VS_CONDITION_VALUE, "Table1", "filter")));

      assertTrue(ex.getMessage().contains(
         "a viewsheet condition value has no name at sheet level"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Connect to Claude"), ex.getMessage());
   }

   @Test
   void requireWholeSheetSessionRefusesTheGrantAndNamesIt() {
      PairingException ex = assertThrows(PairingException.class,
         () -> PaneScopeService.requireWholeSheetSession(session(FILTER)));

      assertTrue(ex.getMessage().contains("filter condition"), ex.getMessage());
      assertTrue(ex.getMessage().contains("'Table1'"), ex.getMessage());
   }

   @Test
   void theCellEndpointsRefuseTheGrant() {
      assertThrows(PairingException.class,
         () -> PaneScopeService.requireCellGrant(session(FILTER), "Table1", 0, 0));
   }
}
