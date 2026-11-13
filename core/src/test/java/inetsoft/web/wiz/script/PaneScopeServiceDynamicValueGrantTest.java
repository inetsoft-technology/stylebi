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

/** The dynamicValue grant: exact (assembly, name) scope, default deny (Bug #80459). */
@Tag("core")
class PaneScopeServiceDynamicValueGrantTest {
   private static final ScriptTarget.Kind DYNAMIC = ScriptTarget.Kind.DYNAMIC_VALUE;
   private static final ScriptTarget.Kind CONDITION = ScriptTarget.Kind.VS_CONDITION_VALUE;

   private static final EditorContext VISIBLE =
      new EditorContext("dynamicValue", "Text1", "visible", null);

   private static JoinSession session(EditorContext ctx) {
      return new JoinSession("TOK", "Viewsheet/foo", "alice~;~host-org", SheetType.VIEWSHEET, 0L,
         Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, ctx);
   }

   private static ScriptTarget target(ScriptTarget.Kind kind, String assembly, String name)
      throws Exception
   {
      return ScriptTarget.of(kind, assembly, name);
   }

   @Test
   void matchesOnlyTheEqualAssemblyAndName() throws Exception {
      assertTrue(PaneScopeService.matchesGrant(VISIBLE, target(DYNAMIC, "Text1", "visible")));

      assertFalse(PaneScopeService.matchesGrant(VISIBLE, target(DYNAMIC, "Text1", "enabled")));
      assertFalse(PaneScopeService.matchesGrant(VISIBLE, target(DYNAMIC, "Text2", "visible")));
      assertFalse(PaneScopeService.matchesGrant(VISIBLE, target(DYNAMIC, "Text1", "Visible")));
   }

   @Test
   void aNamespacedNameIsComparedByExactString() throws Exception {
      EditorContext dc = new EditorContext("dynamicValue", "Chart1", "dateComparison:periodsBack",
                                           null);

      assertTrue(PaneScopeService.matchesGrant(dc,
         target(DYNAMIC, "Chart1", "dateComparison:periodsBack")));
      assertFalse(PaneScopeService.matchesGrant(dc, target(DYNAMIC, "Chart1", "periodsBack")));
      assertFalse(PaneScopeService.matchesGrant(dc,
         target(DYNAMIC, "Chart1", "dateComparison:level")));
   }

   @Test
   void theTableAliasDoesNotApply() throws Exception {
      EditorContext withTable = new EditorContext("dynamicValue", "Text1", "visible", "Other");

      assertTrue(PaneScopeService.matchesGrant(withTable, target(DYNAMIC, "Text1", "visible")));
      assertFalse(PaneScopeService.matchesGrant(withTable, target(DYNAMIC, "Other", "visible")));
   }

   @Test
   void aConditionValueGrantNeverMatchesADynamicValueTargetAndTheReverse() throws Exception {
      EditorContext filter = new EditorContext("vsConditionValue", "Text1", "filter", null);

      assertFalse(PaneScopeService.matchesGrant(filter, target(DYNAMIC, "Text1", "filter")));
      assertFalse(PaneScopeService.matchesGrant(VISIBLE, target(CONDITION, "Text1", "filter")));

      // Even when the two names are textually identical ('filter' is also a bare dynamic name).
      EditorContext dynamicFilter = new EditorContext("dynamicValue", "Text1", "filter", null);
      assertFalse(PaneScopeService.matchesGrant(dynamicFilter, target(CONDITION, "Text1", "filter")));
      assertTrue(PaneScopeService.matchesGrant(dynamicFilter, target(DYNAMIC, "Text1", "filter")));
   }

   @Test
   void theGrantNeverReachesAnotherKindOnTheSameAssembly() throws Exception {
      assertFalse(PaneScopeService.matchesGrant(VISIBLE,
         target(ScriptTarget.Kind.ASSEMBLY_MAIN, "Text1", null)));
      assertFalse(PaneScopeService.matchesGrant(VISIBLE,
         target(ScriptTarget.Kind.ASSEMBLY_ON_CLICK, "Text1", null)));
      assertFalse(PaneScopeService.matchesGrant(VISIBLE,
         target(ScriptTarget.Kind.CALC_FIELD, "Text1", "visible")));
      assertFalse(PaneScopeService.matchesGrant(VISIBLE,
         target(ScriptTarget.Kind.VIEWSHEET_ON_INIT, null, null)));
      assertFalse(PaneScopeService.matchesGrant(VISIBLE,
         target(ScriptTarget.Kind.VIEWSHEET_ON_LOAD, null, null)));
   }

   @Test
   void otherGrantsNeverReachTheKind() throws Exception {
      ScriptTarget t = target(DYNAMIC, "Text1", "visible");

      for(EditorContext other : new EditorContext[] {
         new EditorContext("assemblyMain", "Text1", null, null),
         new EditorContext("assemblyOnClick", "Text1", null, null),
         new EditorContext("calcField", "Text1", "visible", null),
         new EditorContext("worksheetConditionValue", "Text1", "visible", null),
         new EditorContext("viewsheetOnInit", null, null, null),
         new EditorContext("viewsheetOnLoad", null, null, null),
         new EditorContext("calcTableCell", "Text1", "0,0", null) })
      {
         assertFalse(PaneScopeService.matchesGrant(other, t), other.kind());
      }
   }

   @Test
   void anUnrecognizedGrantKindMatchesNothing() throws Exception {
      assertFalse(PaneScopeService.matchesGrant(
         new EditorContext("dynamicValues", "Text1", "visible", null),
         target(DYNAMIC, "Text1", "visible")));
   }

   @Test
   void checkRefusesAnotherTargetAndNamesTheGrant() throws Exception {
      PaneScopeService svc = new PaneScopeService(false);
      JoinSession s = session(VISIBLE);

      assertTrue(svc.check(s, target(DYNAMIC, "Text1", "visible")));

      PairingException name = assertThrows(PairingException.class,
         () -> svc.check(s, target(DYNAMIC, "Text1", "enabled")));
      assertTrue(name.getMessage().contains("This session is paired to the expression of the " +
                                            "'visible' property of 'Text1'"), name.getMessage());
      assertTrue(name.getMessage().contains("may not act on the expression of the 'enabled' " +
                                            "property of 'Text1'"), name.getMessage());
      assertFalse(name.getMessage().contains("'dynamicValue'"), name.getMessage());

      PairingException assembly = assertThrows(PairingException.class,
         () -> svc.check(s, target(DYNAMIC, "Chart1", "visible")));
      assertTrue(assembly.getMessage().contains("'Chart1'"), assembly.getMessage());

      PairingException main = assertThrows(PairingException.class,
         () -> svc.check(s, target(ScriptTarget.Kind.ASSEMBLY_MAIN, "Text1", null)));
      assertTrue(main.getMessage().contains("'visible' property of 'Text1'"), main.getMessage());

      PairingException condition = assertThrows(PairingException.class,
         () -> svc.check(s, target(CONDITION, "Text1", "filter")));
      assertTrue(condition.getMessage().contains("'visible' property of 'Text1'"),
                 condition.getMessage());
   }

   @Test
   void aWholeSheetSessionCannotReachTheKind() throws Exception {
      PaneScopeService svc = new PaneScopeService(false);
      PairingException ex = assertThrows(PairingException.class,
         () -> svc.check(session(null), target(DYNAMIC, "Text1", "visible")));

      assertTrue(ex.getMessage().contains("a dynamic value has no name at sheet level"),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("Connect to Claude"), ex.getMessage());
   }

   @Test
   void aWholeSheetSessionAlsoCannotReachTheKindInStrictPosture() throws Exception {
      PairingException ex = assertThrows(PairingException.class,
         () -> new PaneScopeService(true).check(session(null), target(DYNAMIC, "Text1", "visible")));

      assertTrue(ex.getMessage().contains("a dynamic value has no name at sheet level"),
                 ex.getMessage());
   }

   @Test
   void requireWholeSheetSessionRefusesTheGrantAndNamesIt() {
      PairingException ex = assertThrows(PairingException.class,
         () -> PaneScopeService.requireWholeSheetSession(session(VISIBLE)));

      assertTrue(ex.getMessage().contains("'visible' property of 'Text1'"), ex.getMessage());
   }

   @Test
   void theCellEndpointsRefuseTheGrant() {
      assertThrows(PairingException.class,
         () -> PaneScopeService.requireCellGrant(session(VISIBLE), "Text1", 0, 0));
   }
}
