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

/**
 * The grants of {@code worksheetVariableDefault}, {@code vsHyperlinkExpression} and
 * {@code worksheetSqlQuery}: exact scope, default deny (Bug #80459). The Free Form SQL permission
 * is NOT decided here (it needs a principal, see {@code WorksheetScriptService}); this class
 * pins what the principal-free scope check does and does not allow.
 */
@Tag("core")
class PaneScopeServiceNewKindsGrantTest {
   private static final ScriptTarget.Kind VARIABLE = ScriptTarget.Kind.WORKSHEET_VARIABLE_DEFAULT;
   private static final ScriptTarget.Kind HYPERLINK = ScriptTarget.Kind.VS_HYPERLINK_EXPRESSION;
   private static final ScriptTarget.Kind SQL = ScriptTarget.Kind.WORKSHEET_SQL_QUERY;
   private static final ScriptTarget.Kind DYNAMIC = ScriptTarget.Kind.DYNAMIC_VALUE;

   private static final EditorContext VAR1 =
      new EditorContext("worksheetVariableDefault", "Var1", null, null);
   private static final EditorContext SQL1 =
      new EditorContext("worksheetSqlQuery", "Q1", null, null);
   private static final EditorContext LINK =
      new EditorContext("vsHyperlinkExpression", "Table1", "cell[2,1]", null);

   private static JoinSession session(EditorContext ctx, SheetType type) {
      return new JoinSession("TOK", "Sheet/foo", "alice~;~host-org", type, 0L, Long.MAX_VALUE,
         JoinSession.ConnectionMode.PAIRED, null, null, ctx);
   }

   private static ScriptTarget target(ScriptTarget.Kind kind, String assembly, String name)
      throws Exception
   {
      return ScriptTarget.of(kind, assembly, name);
   }

   // ---- worksheetVariableDefault --------------------------------------------------------------

   @Test
   void aVariableGrantMatchesOnlyItsOwnVariable() throws Exception {
      assertTrue(PaneScopeService.matchesGrant(VAR1, target(VARIABLE, "Var1", null)));

      assertFalse(PaneScopeService.matchesGrant(VAR1, target(VARIABLE, "Var2", null)));
      assertFalse(PaneScopeService.matchesGrant(VAR1, target(VARIABLE, "var1", null)));
   }

   @Test
   void aBlankNameOnTheVariableGrantStillMatchesTheNamelessTarget() throws Exception {
      EditorContext blankName = new EditorContext("worksheetVariableDefault", "Var1", "", null);

      assertTrue(PaneScopeService.matchesGrant(blankName, target(VARIABLE, "Var1", null)));
   }

   @Test
   void aVariableGrantNeverReachesASqlTargetOfTheSameName() throws Exception {
      assertFalse(PaneScopeService.matchesGrant(VAR1, target(SQL, "Var1", null)));
      assertFalse(PaneScopeService.matchesGrant(
         new EditorContext("worksheetSqlQuery", "Var1", null, null), target(VARIABLE, "Var1", null)));
   }

   // ---- worksheetSqlQuery ---------------------------------------------------------------------

   @Test
   void aSqlGrantMatchesOnlyItsOwnTable() throws Exception {
      assertTrue(PaneScopeService.matchesGrant(SQL1, target(SQL, "Q1", null)));

      assertFalse(PaneScopeService.matchesGrant(SQL1, target(SQL, "Q2", null)));
      assertFalse(PaneScopeService.matchesGrant(SQL1, target(SQL, "q1", null)));
   }

   @Test
   void aSqlGrantNeverReachesAnotherKindOnTheSameName() throws Exception {
      assertFalse(PaneScopeService.matchesGrant(SQL1, target(VARIABLE, "Q1", null)));
      assertFalse(PaneScopeService.matchesGrant(SQL1,
         target(ScriptTarget.Kind.WORKSHEET_CONDITION_VALUE, "Q1", "price")));
      assertFalse(PaneScopeService.matchesGrant(SQL1,
         target(ScriptTarget.Kind.WORKSHEET_CONDITION, "Q1", "price")));
      assertFalse(PaneScopeService.matchesGrant(SQL1,
         target(ScriptTarget.Kind.WORKSHEET_EXPRESSION, "Q1", "price")));
      assertFalse(PaneScopeService.matchesGrant(SQL1,
         target(ScriptTarget.Kind.CALC_FIELD, "Q1", "price")));
      assertFalse(PaneScopeService.matchesGrant(SQL1, target(DYNAMIC, "Q1", "visible")));
      assertFalse(PaneScopeService.matchesGrant(SQL1,
         target(ScriptTarget.Kind.ASSEMBLY_MAIN, "Q1", null)));
   }

   @Test
   void aWorksheetConditionValueGrantNeverReachesASqlTarget() throws Exception {
      EditorContext conditionValue =
         new EditorContext("worksheetConditionValue", "Q1", "price", null);

      assertFalse(PaneScopeService.matchesGrant(conditionValue, target(SQL, "Q1", null)));
      assertFalse(PaneScopeService.matchesGrant(conditionValue, target(VARIABLE, "Q1", null)));
   }

   // ---- vsHyperlinkExpression -----------------------------------------------------------------

   @Test
   void aHyperlinkGrantMatchesOnlyTheEqualAssemblyAndRegion() throws Exception {
      assertTrue(PaneScopeService.matchesGrant(LINK, target(HYPERLINK, "Table1", "cell[2,1]")));

      assertFalse(PaneScopeService.matchesGrant(LINK, target(HYPERLINK, "Table1", "cell[2,2]")));
      assertFalse(PaneScopeService.matchesGrant(LINK, target(HYPERLINK, "Table1", "cell[1,2]")));
      assertFalse(PaneScopeService.matchesGrant(LINK, target(HYPERLINK, "Table2", "cell[2,1]")));
   }

   @Test
   void aChartFieldRegionIsComparedByTheExactColumnString() throws Exception {
      EditorContext field = new EditorContext("vsHyperlinkExpression", "Chart1",
                                              "field[Total Sales]", null);

      assertTrue(PaneScopeService.matchesGrant(field,
         target(HYPERLINK, "Chart1", "field[Total Sales]")));
      assertFalse(PaneScopeService.matchesGrant(field,
         target(HYPERLINK, "Chart1", "field[Total sales]")));
      assertFalse(PaneScopeService.matchesGrant(field,
         target(HYPERLINK, "Chart1", "field[Total Sales ]")));
      assertFalse(PaneScopeService.matchesGrant(field,
         target(HYPERLINK, "Chart1", "axis[Total Sales]")));
      assertFalse(PaneScopeService.matchesGrant(field,
         target(HYPERLINK, "Chart1", "text[Total Sales]")));
      assertFalse(PaneScopeService.matchesGrant(field, target(HYPERLINK, "Chart1", "title")));
   }

   @Test
   void theTableAliasDoesNotApplyToTheHyperlinkGrant() throws Exception {
      EditorContext withTable =
         new EditorContext("vsHyperlinkExpression", "Table1", "cell[2,1]", "Other");

      assertTrue(PaneScopeService.matchesGrant(withTable, target(HYPERLINK, "Table1", "cell[2,1]")));
      assertFalse(PaneScopeService.matchesGrant(withTable, target(HYPERLINK, "Other", "cell[2,1]")));
   }

   @Test
   void aHyperlinkGrantNeverReachesAConditionOrDynamicTargetOfTheSameNames() throws Exception {
      EditorContext filterLink = new EditorContext("vsHyperlinkExpression", "Table1", "link", null);

      assertFalse(PaneScopeService.matchesGrant(filterLink,
         target(ScriptTarget.Kind.VS_CONDITION_VALUE, "Table1", "filter")));
      assertFalse(PaneScopeService.matchesGrant(filterLink, target(DYNAMIC, "Table1", "title")));
      assertFalse(PaneScopeService.matchesGrant(
         new EditorContext("dynamicValue", "Table1", "title", null),
         target(HYPERLINK, "Table1", "title")));
      assertFalse(PaneScopeService.matchesGrant(
         new EditorContext("vsConditionValue", "Table1", "filter", null),
         target(HYPERLINK, "Table1", "link")));
   }

   // ---- default deny --------------------------------------------------------------------------

   @Test
   void everyOtherGrantKindIsRefusedTheThreeTargets() throws Exception {
      ScriptTarget[] targets = { target(VARIABLE, "Var1", null), target(SQL, "Var1", null),
                                 target(HYPERLINK, "Var1", "link") };

      for(EditorContext other : new EditorContext[] {
         new EditorContext("assemblyMain", "Var1", null, null),
         new EditorContext("assemblyOnClick", "Var1", null, null),
         new EditorContext("calcField", "Var1", "link", null),
         new EditorContext("worksheetExpression", "Var1", "link", null),
         new EditorContext("worksheetCondition", "Var1", "link", null),
         new EditorContext("worksheetConditionValue", "Var1", "link", null),
         new EditorContext("dynamicValue", "Var1", "link", null),
         new EditorContext("vsConditionValue", "Var1", "filter", null),
         new EditorContext("viewsheetOnInit", null, null, null),
         new EditorContext("viewsheetOnLoad", null, null, null),
         new EditorContext("calcTableCell", "Var1", "0,0", null) })
      {
         for(ScriptTarget t : targets) {
            assertFalse(PaneScopeService.matchesGrant(other, t), other.kind() + " -> " + t);
         }
      }
   }

   @Test
   void anUnrecognizedGrantKindMatchesNothing() throws Exception {
      assertFalse(PaneScopeService.matchesGrant(
         new EditorContext("worksheetSqlQueries", "Q1", null, null), target(SQL, "Q1", null)));
   }

   @Test
   void everyNewKindIsInTheExplicitFamilyOrItWouldFallToDefaultDeny() throws Exception {
      // A grant matches its own target for each of the three: dropping the kind from the explicit
      // branch of matchesGrant makes every one of these false (the last statement of the method).
      assertTrue(PaneScopeService.matchesGrant(VAR1, target(VARIABLE, "Var1", null)));
      assertTrue(PaneScopeService.matchesGrant(SQL1, target(SQL, "Q1", null)));
      assertTrue(PaneScopeService.matchesGrant(LINK, target(HYPERLINK, "Table1", "cell[2,1]")));
   }

   // ---- check ---------------------------------------------------------------------------------

   @Test
   void checkAcceptsTheGrantAndRefusesAnotherTargetNamingTheGrant() throws Exception {
      PaneScopeService svc = new PaneScopeService(false);
      JoinSession variable = session(VAR1, SheetType.WORKSHEET);

      assertTrue(svc.check(variable, target(VARIABLE, "Var1", null)));

      PairingException other = assertThrows(PairingException.class,
         () -> svc.check(variable, target(VARIABLE, "Var2", null)));
      assertTrue(other.getMessage().contains("This session is paired to the default-value " +
                                             "expression of worksheet variable 'Var1'"),
                 other.getMessage());
      assertTrue(other.getMessage().contains("may not act on the default-value expression of " +
                                             "worksheet variable 'Var2'"), other.getMessage());

      PairingException sql = assertThrows(PairingException.class,
         () -> svc.check(variable, target(SQL, "Var1", null)));
      assertTrue(sql.getMessage().contains("the SQL of worksheet query table 'Var1'"),
                 sql.getMessage());
   }

   @Test
   void checkNamesTheHyperlinkRegionInWords() throws Exception {
      PaneScopeService svc = new PaneScopeService(false);
      JoinSession link = session(LINK, SheetType.VIEWSHEET);

      assertTrue(svc.check(link, target(HYPERLINK, "Table1", "cell[2,1]")));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.check(link, target(HYPERLINK, "Table1", "cell[0,3]")));
      assertTrue(ex.getMessage().contains("This session is paired to the web-link expression " +
                                          "of the link of cell row 2, col 1 on 'Table1'"),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("may not act on the web-link expression of the link " +
                                          "of cell row 0, col 3 on 'Table1'"), ex.getMessage());
      assertFalse(ex.getMessage().contains("'vsHyperlinkExpression'"), ex.getMessage());
   }

   @Test
   void checkNamesEveryHyperlinkRegionForm() throws Exception {
      PaneScopeService svc = new PaneScopeService(false);
      String[][] cases = {
         { "link", "the link" }, { "title", "the title link" },
         { "emptyPlot", "the empty plot link" },
         { "field[Total Sales]", "the link of field 'Total Sales'" },
         { "axis[Region]", "the link of the axis of 'Region'" },
         { "text[Sales]", "the link of text 'Sales'" } };

      for(String[] c : cases) {
         JoinSession s = session(new EditorContext("vsHyperlinkExpression", "Chart1", c[0], null),
                                 SheetType.VIEWSHEET);
         PairingException ex = assertThrows(PairingException.class,
            () -> svc.check(s, target(ScriptTarget.Kind.ASSEMBLY_MAIN, "Chart1", null)), c[0]);

         assertTrue(ex.getMessage().contains("the web-link expression of " + c[1] + " on " +
                                             "'Chart1'"), ex.getMessage());
      }
   }

   @Test
   void aWholeSheetSessionCannotReachAnyOfTheThree() throws Exception {
      PaneScopeService svc = new PaneScopeService(false);
      String[][] cases = {
         { "worksheetVariableDefault", "a worksheet variable's default value has no name at " +
            "sheet level" },
         { "vsHyperlinkExpression", "a hyperlink expression has no name at sheet level" },
         { "worksheetSqlQuery", "a worksheet query's SQL has no name at sheet level" } };
      ScriptTarget[] targets = { target(VARIABLE, "Var1", null),
                                 target(HYPERLINK, "Table1", "link"), target(SQL, "Q1", null) };

      for(boolean strict : new boolean[] { false, true }) {
         for(int i = 0; i < targets.length; i++) {
            ScriptTarget t = targets[i];
            PairingException ex = assertThrows(PairingException.class,
               () -> new PaneScopeService(strict).check(session(null, SheetType.WORKSHEET), t));

            assertTrue(ex.getMessage().contains(cases[i][1]), ex.getMessage());
            assertTrue(ex.getMessage().contains("Connect to Claude"), ex.getMessage());
         }
      }
   }

   @Test
   void theWholeSheetSurfacesRefuseTheGrantAndNameIt() {
      PairingException ex = assertThrows(PairingException.class,
         () -> PaneScopeService.requireWholeSheetSession(session(SQL1, SheetType.WORKSHEET)));

      assertTrue(ex.getMessage().contains("the SQL of worksheet query table 'Q1'"),
                 ex.getMessage());
      assertThrows(PairingException.class,
         () -> PaneScopeService.requireCellGrant(session(SQL1, SheetType.WORKSHEET), "Q1", 0, 0));
   }
}
