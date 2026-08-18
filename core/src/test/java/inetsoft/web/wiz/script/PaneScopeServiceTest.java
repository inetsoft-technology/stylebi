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

   /**
    * The kinds this suite asserts MUST require a pane session. Hoisted out of the test body so
    * {@link #everyKindIsClassifiedByExactlyOneGuard()} can prove the two lists between them cover
    * {@code Kind.values()} — see that test for why an inline {@code List.of} was not enough.
    */
   private static final List<ScriptTarget.Kind> EXPRESSION_LEVEL_KINDS =
      List.of(ScriptTarget.Kind.CALC_FIELD, ScriptTarget.Kind.WORKSHEET_EXPRESSION,
              ScriptTarget.Kind.WORKSHEET_CONDITION);

   /** The kinds this suite asserts must NOT require one. See {@link #EXPRESSION_LEVEL_KINDS}. */
   private static final List<ScriptTarget.Kind> SHEET_LEVEL_KINDS =
      List.of(ScriptTarget.Kind.VIEWSHEET_ON_INIT, ScriptTarget.Kind.VIEWSHEET_ON_LOAD,
              ScriptTarget.Kind.ASSEMBLY_MAIN, ScriptTarget.Kind.ASSEMBLY_ON_CLICK);

   @Test
   void expressionLevelKindsRequireAPaneSession() {
      for(ScriptTarget.Kind k : EXPRESSION_LEVEL_KINDS) {
         assertTrue(k.requiresPaneSession(), k.wireName());
      }
   }

   @Test
   void theFourViewsheetLevelKindsDoNot() {
      for(ScriptTarget.Kind k : SHEET_LEVEL_KINDS) {
         assertFalse(k.requiresPaneSession(), k.wireName());
      }
   }

   /**
    * Whole-branch review finding 4: both guards above detect a CHANGE to a kind they already name
    * and are blind to an ADDITION. Adding the next kind the spec names ({@code highlight},
    * {@code cell}, {@code dynamicValue}) with a real {@link ScriptTarget.Location} and forgetting
    * to classify it left {@code requiresPaneSession()} silently returning {@code false} with the
    * whole suite green — a security-relevant rule inverted by an omission.
    *
    * <p>This closes the half the compiler cannot see. {@code Kind.isExpressionLevel} is now an
    * exhaustive {@code switch} over {@link ScriptTarget.Location} with no {@code default}, so a
    * NEW LOCATION fails the BUILD; a new {@code Kind} mapped onto an EXISTING location compiles
    * fine, and is what this test refuses. Together: every {@code Kind} constant must appear in
    * exactly one of the two lists above, so a new kind cannot reach {@code main} unclassified.
    */
   @Test
   void everyKindIsClassifiedByExactlyOneGuard() {
      for(ScriptTarget.Kind k : ScriptTarget.Kind.values()) {
         boolean expressionLevel = EXPRESSION_LEVEL_KINDS.contains(k);
         boolean sheetLevel = SHEET_LEVEL_KINDS.contains(k);

         assertTrue(expressionLevel || sheetLevel,
            "New kind '" + k.wireName() + "' is not classified by either guard in this class. " +
            "Decide whether it has an identity at whole-sheet level and add it to " +
            "EXPRESSION_LEVEL_KINDS or SHEET_LEVEL_KINDS -- leaving it out means " +
            "requiresPaneSession() is unasserted for it.");
         assertFalse(expressionLevel && sheetLevel,
            "Kind '" + k.wireName() + "' is in BOTH guard lists, which asserts two opposite " +
            "things about requiresPaneSession().");
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

   // -----------------------------------------------------------------------------------------
   // Task 7 — a pane session may only touch its own location (the scope of the grant, not a
   // policy). The second test below is the load-bearing one: it pins that the grant does not
   // weaken when the opt-in strict posture is off.
   // -----------------------------------------------------------------------------------------

   private static JoinSession sessionScopedTo(EditorContext ctx) {
      return new JoinSession("TOK", "Viewsheet/foo", "alice~;~host-org", SheetType.VIEWSHEET, 0L,
         Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, ctx);
   }

   private static ScriptTarget targetFor(String wireKind, String assembly) throws PairingException {
      return ScriptTarget.of(ScriptTarget.Kind.fromWire(wireKind), assembly);
   }

   @Test
   void aPaneSessionRefusesATargetOutsideItsGrant() throws Exception {
      JoinSession pane = sessionScopedTo(new EditorContext("assemblyMain", "Chart1", null, null));

      PairingException ex = assertThrows(PairingException.class,
         () -> new PaneScopeService(true).check(pane, targetFor("assemblyMain", "Chart2")));

      assertTrue(ex.getMessage().contains("Chart2"), ex.getMessage());
   }

   /**
    * The test that matters most: it pins that the grant is SCOPE, not a policy. If this ever
    * passed only because {@code strict} happened to be on, the refusal would be a configurable
    * posture rather than what the grant itself means -- and a session minted for one chart's
    * script could edit another chart's the moment an administrator left strict posture off
    * (which is the documented default).
    */
   @Test
   void theGrantDoesNotWeakenWhenStrictPostureIsOff() throws Exception {
      JoinSession pane = sessionScopedTo(new EditorContext("assemblyMain", "Chart1", null, null));

      PairingException ex = assertThrows(PairingException.class,
         () -> new PaneScopeService(false).check(pane, targetFor("assemblyMain", "Chart2")));

      assertTrue(ex.getMessage().contains("Chart2"), ex.getMessage());
   }

   /** Defensive counterpart: the refusal must not depend on strict posture in EITHER direction. */
   @Test
   void theGrantDoesNotWeakenWhenStrictPostureIsOn() throws Exception {
      JoinSession pane = sessionScopedTo(new EditorContext("assemblyMain", "Chart1", null, null));

      assertThrows(PairingException.class,
         () -> new PaneScopeService(true).check(pane, targetFor("assemblyMain", "Chart2")));
   }

   /**
    * Ruling: a grant for {@code assemblyMain} on an assembly also covers {@code assemblyOnClick}
    * on that SAME assembly -- its "dialog sibling", per {@code ClickableScriptPane}, which hosts
    * both scripts behind one radio toggle sharing a single {@code editorContext}. See
    * {@link PaneScopeService#matchesGrant}'s javadoc for the full reasoning.
    */
   @Test
   void aPaneScopedSessionMaySatisfyItsDialogSiblingOnTheSameAssembly() throws Exception {
      JoinSession pane = sessionScopedTo(new EditorContext("assemblyMain", "Chart1", null, null));

      assertTrue(new PaneScopeService(false).check(pane, targetFor("assemblyOnClick", "Chart1")));
   }

   /** The sibling allowance is per-assembly, not per-kind — it must not also cross assemblies. */
   @Test
   void aPaneScopedSessionRefusesItsDialogSiblingOnADifferentAssembly() throws Exception {
      JoinSession pane =
         sessionScopedTo(new EditorContext("assemblyOnClick", "Chart1", null, null));

      PairingException ex = assertThrows(PairingException.class,
         () -> new PaneScopeService(false).check(pane, targetFor("assemblyMain", "Chart2")));

      assertTrue(ex.getMessage().contains("Chart2"), ex.getMessage());
   }

   /**
    * A calc field's formula editor has no dialog sibling — unlike assemblyMain/assemblyOnClick,
    * two DIFFERENT fields on the SAME table must not match each other's grant.
    */
   @Test
   void aPaneScopedSessionMayNotActOnADifferentCalcFieldOnTheSameTable() throws Exception {
      EditorContext ctx = new EditorContext("calcField", "Query1", "Margin", null);
      JoinSession pane = sessionScopedTo(ctx);
      ScriptTarget otherField = ScriptTarget.of(ScriptTarget.Kind.CALC_FIELD, "Query1", "TaxRate");

      PairingException ex = assertThrows(PairingException.class,
         () -> new PaneScopeService(false).check(pane, otherField));

      assertTrue(ex.getMessage().contains("TaxRate"), ex.getMessage());
   }

   /**
    * The two viewsheet-level whole-viewsheet kinds are not dialog siblings of each other — a
    * pane session scoped to {@code viewsheetOnInit} must not roam to {@code viewsheetOnLoad},
    * even with strict posture off (where the addressability gate alone would let either kind
    * through from ANY session, pane-scoped or not).
    */
   @Test
   void aPaneScopedSessionMayNotActOnADifferentViewsheetLevelKind() throws Exception {
      JoinSession pane =
         sessionScopedTo(new EditorContext("viewsheetOnInit", null, null, null));
      ScriptTarget onLoad = ScriptTarget.of(ScriptTarget.Kind.VIEWSHEET_ON_LOAD, null);

      assertThrows(PairingException.class,
         () -> new PaneScopeService(false).check(pane, onLoad));
   }

   /**
    * {@code EditorContext.table()} — the record's own dedicated field, as against the
    * {@code assembly}-carries-the-table-name alias {@link #aPaneScopedSessionMaySatisfyAnExpressionLevelKind}
    * already covers — must independently identify the calc field's table.
    */
   @Test
   void aPaneScopedCalcFieldGrantMatchesViaTheTableField() throws Exception {
      EditorContext ctx = new EditorContext("calcField", null, "Margin", "Query1");
      JoinSession pane = sessionScopedTo(ctx);
      ScriptTarget calcFieldTarget = ScriptTarget.of(ScriptTarget.Kind.CALC_FIELD, "Query1", "Margin");

      assertTrue(new PaneScopeService(false).check(pane, calcFieldTarget));
   }

   // -----------------------------------------------------------------------------------------
   // G2 Task 8 — worksheetExpression/worksheetCondition are addressed by (table, field) exactly
   // like calcField (see ScriptTarget.Kind's javadoc on each). matchesGrant's CALC_FIELD-only
   // branch, written while these two were still reserved (no Location, therefore unreachable),
   // fell through to its final `return true` for them -- which is correct ONLY for a kind with
   // no name/identity at all (viewsheetOnInit/viewsheetOnLoad). The moment WORKSHEET_EXPRESSION/
   // WORKSHEET_CONDITION got real Locations, that fallthrough would have let a grant for one
   // column match a target naming a DIFFERENT column, silently -- a second instance of the same
   // "gains a Location, quietly loses a security check" hazard EXPRESSION_LEVEL_LOCATIONS exists
   // to catch for requiresPaneSession(). These pin the fix.
   // -----------------------------------------------------------------------------------------

   @Test
   void aPaneScopedWorksheetExpressionSessionMaySatisfyItsOwnColumn() throws Exception {
      EditorContext ctx = new EditorContext("worksheetExpression", "Query1", "Margin", null);
      JoinSession pane = sessionScopedTo(ctx);
      ScriptTarget target =
         ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_EXPRESSION, "Query1", "Margin");

      assertTrue(new PaneScopeService(false).check(pane, target));
   }

   /**
    * The load-bearing regression: without matchesGrant treating worksheetExpression like
    * calcField, this would incorrectly return {@code true} (same kind, no per-field check),
    * letting a session paired to Query1.Margin's expression editor also edit Query1.TaxRate's.
    */
   @Test
   void aPaneScopedSessionMayNotActOnADifferentWorksheetExpressionColumnOnTheSameTable()
      throws Exception
   {
      EditorContext ctx = new EditorContext("worksheetExpression", "Query1", "Margin", null);
      JoinSession pane = sessionScopedTo(ctx);
      ScriptTarget otherColumn =
         ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_EXPRESSION, "Query1", "TaxRate");

      PairingException ex = assertThrows(PairingException.class,
         () -> new PaneScopeService(false).check(pane, otherColumn));

      assertTrue(ex.getMessage().contains("TaxRate"), ex.getMessage());
   }

   /** Same regression, other table — the field match alone must not be enough either. */
   @Test
   void aPaneScopedSessionMayNotActOnTheSameWorksheetExpressionFieldOnADifferentTable()
      throws Exception
   {
      EditorContext ctx = new EditorContext("worksheetExpression", "Query1", "Margin", null);
      JoinSession pane = sessionScopedTo(ctx);
      ScriptTarget otherTable =
         ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_EXPRESSION, "Query2", "Margin");

      assertThrows(PairingException.class,
         () -> new PaneScopeService(false).check(pane, otherTable));
   }

   @Test
   void aPaneScopedWorksheetConditionSessionMaySatisfyItsOwnColumn() throws Exception {
      EditorContext ctx = new EditorContext("worksheetCondition", "Query1", "Price", null);
      JoinSession pane = sessionScopedTo(ctx);
      ScriptTarget target =
         ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_CONDITION, "Query1", "Price");

      assertTrue(new PaneScopeService(false).check(pane, target));
   }

   @Test
   void aPaneScopedSessionMayNotActOnADifferentWorksheetConditionColumnOnTheSameTable()
      throws Exception
   {
      EditorContext ctx = new EditorContext("worksheetCondition", "Query1", "Price", null);
      JoinSession pane = sessionScopedTo(ctx);
      ScriptTarget otherColumn =
         ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_CONDITION, "Query1", "Quantity");

      PairingException ex = assertThrows(PairingException.class,
         () -> new PaneScopeService(false).check(pane, otherColumn));

      assertTrue(ex.getMessage().contains("Quantity"), ex.getMessage());
   }

   /** worksheetExpression and worksheetCondition are different dialogs -- never siblings. */
   @Test
   void aWorksheetExpressionGrantDoesNotMatchAWorksheetConditionTargetOnTheSameColumn()
      throws Exception
   {
      EditorContext ctx = new EditorContext("worksheetExpression", "Query1", "Margin", null);
      JoinSession pane = sessionScopedTo(ctx);
      ScriptTarget conditionTarget =
         ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_CONDITION, "Query1", "Margin");

      assertThrows(PairingException.class,
         () -> new PaneScopeService(false).check(pane, conditionTarget));
   }

   // -----------------------------------------------------------------------------------------
   // Whole-branch review finding 1 -- requireWholeSheetSession, the mirror image of check()
   // -----------------------------------------------------------------------------------------

   /**
    * A pane token is a write handle for ONE expression. On a surface with no
    * {@link ScriptTarget} to check it against, there is nothing to narrow it with, so it is
    * refused outright -- otherwise it is simply a whole-sheet handle carrying an unread field.
    */
   @Test
   void requireWholeSheetSessionRefusesAPaneScopedSessionAndNamesWhereItIsBound() {
      JoinSession pane = sessionScopedTo(new EditorContext("calcField", "Query1", "Margin", null));

      PairingException ex = assertThrows(PairingException.class,
         () -> PaneScopeService.requireWholeSheetSession(pane));

      assertTrue(ex.getMessage().contains("Query1.Margin"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Connect to Claude"), ex.getMessage());
   }

   /** Whole-sheet toolbar pairing must be completely unaffected -- it is in use today. */
   @Test
   void requireWholeSheetSessionAcceptsAToolbarSession() {
      assertDoesNotThrow(() -> PaneScopeService.requireWholeSheetSession(wholeSheetSession()));
   }

   /**
    * A null session means the token did not resolve at all. Answering "you are pane-scoped"
    * would be false, and would send the caller to re-pair from the toolbar when the real problem
    * is an expired or foreign token -- so the caller's own resolution gets to report it.
    */
   @Test
   void requireWholeSheetSessionSaysNothingAboutAnUnresolvedToken() {
      assertDoesNotThrow(() -> PaneScopeService.requireWholeSheetSession(null));
   }

}
