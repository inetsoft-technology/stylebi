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

/**
 * FormulaEditorDialog — editorContext / canPair
 *
 * The formula editor dialog is the single dialog behind every expression
 * location in the product (calc fields, worksheet/viewsheet conditions,
 * highlight/hyperlink expressions, plain script expressions). These tests
 * pin how it derives a pane-scoped `editorContext` from the inputs it
 * already takes, and how `canPair` gates both the getter and the mounted
 * connect button on there being an actual sheet runtime + socket to pair
 * against.
 *
 * Ruling (overrides the original task brief): the brief's "derives a
 * condition context" test set isCondition=true but never set isVSContext,
 * whose declared default is `true` — so the getter's own condition branch
 * would return "assemblyMain", not "worksheetCondition", and the brief's
 * test would fail as written. The getter is right, not the test: a
 * viewsheet condition is viewsheet-hosted. Fixed here by setting
 * isVSContext = false to exercise the worksheet branch, with a second test
 * pinning isVSContext = true -> "assemblyMain".
 */

import { createDialog } from "./formula-editor-dialog.component.test-helpers";
import { FormulaEditorDialog } from "./formula-editor-dialog.component";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { ConditionOperation } from "../../common/data/condition/condition-operation";

function pairedDialog(): FormulaEditorDialog {
   const { comp } = createDialog();
   comp.runtimeId = "vs-1";
   comp.socketConnection = {} as ViewsheetClientService;
   return comp;
}

describe("FormulaEditorDialog — editorContext", () => {
   it("derives a calcField context from its own inputs", () => {
      const comp = pairedDialog();
      comp.isVSContext = true;
      comp.isCalc = true;
      comp.assemblyName = "Query1";
      comp.formulaName = "Margin";

      expect(comp.editorContext).toEqual(
         { kind: "calcField", assembly: "Query1", name: "Margin" });
   });

   /*
    * stylebi#4654's second review, finding 1: this dialog edits a condition's VALUE, never its
    * operator, so a worksheet-hosted condition mints "worksheetConditionValue", not
    * "worksheetCondition" (whole-condition replace) -- the latter would let an agent silently
    * overwrite an operator the user never opened this box to change. Only reachable for a
    * single-value operator; see the next two tests.
    */
   it("derives a worksheetConditionValue context for a single-value worksheet condition", () => {
      const comp = pairedDialog();
      comp.isVSContext = false;
      comp.isCondition = true;
      comp.assemblyName = "Table1";
      comp.formulaName = "Amount";
      comp.conditionOperation = ConditionOperation.EQUAL_TO;

      expect(comp.editorContext).toEqual(
         { kind: "worksheetConditionValue", assembly: "Table1", name: "Amount" });
   });

   /*
    * BETWEEN has two value slots, so there is no single "the value" this pane could mean --
    * omitting `name` here (rather than minting a code with one) is what canPair's suppression
    * below keys on.
    */
   it("omits the name for a worksheet condition using a multi-value operator", () => {
      const comp = pairedDialog();
      comp.isVSContext = false;
      comp.isCondition = true;
      comp.assemblyName = "Table1";
      comp.formulaName = "Amount";
      comp.conditionOperation = ConditionOperation.BETWEEN;

      expect(comp.editorContext).toEqual(
         { kind: "worksheetConditionValue", assembly: "Table1" });
   });

   /*
    * BinaryConditionEditor/OneOfConditionEditor never bind an operation into the ConditionEditor
    * instances they host (they know it structurally, from which component they are), so this
    * dialog sees `conditionOperation` as undefined for BETWEEN/ONE_OF in practice -- must be
    * treated the same as an explicitly multi-value operator, not as "unknown, so allow it".
    */
   it("omits the name for a worksheet condition with no known operation", () => {
      const comp = pairedDialog();
      comp.isVSContext = false;
      comp.isCondition = true;
      comp.assemblyName = "Table1";
      comp.formulaName = "Amount";
      comp.conditionOperation = undefined;

      expect(comp.editorContext).toEqual(
         { kind: "worksheetConditionValue", assembly: "Table1" });
   });

   it("derives an assemblyMain context for a viewsheet-hosted condition", () => {
      const comp = pairedDialog();
      comp.isVSContext = true;
      comp.isCondition = true;
      comp.assemblyName = "Table1";

      expect(comp.editorContext.kind).toBe("assemblyMain");
   });

   it("derives a worksheetExpression context for a plain worksheet expression", () => {
      const comp = pairedDialog();
      comp.isVSContext = false;
      comp.assemblyName = "Table1";
      comp.formulaName = "Field1";

      expect(comp.editorContext).toEqual(
         { kind: "worksheetExpression", assembly: "Table1", name: "Field1" });
   });

   it("derives an assemblyMain context for a plain viewsheet expression", () => {
      const comp = pairedDialog();
      comp.isVSContext = true;
      comp.assemblyName = "Chart1";
      comp.formulaName = "Field1";

      expect(comp.editorContext).toEqual(
         { kind: "assemblyMain", assembly: "Chart1", name: "Field1" });
   });

   it("is null when there is no sheet runtime to pair against", () => {
      const { comp } = createDialog();
      comp.runtimeId = null;
      comp.socketConnection = {} as ViewsheetClientService;
      comp.isCalc = true;

      expect(comp.editorContext).toBeNull();
   });

   // ScriptTarget.java's CALC_FIELD kind is addressed by (table, field name): `assembly`
   // carries the owning table, and the server throws a PairingException without it.
   // `assemblyName` is a plain @Input already consumed by populateColumnTree /
   // populateScriptDefinitions elsewhere on this component, so callers that know the
   // table but never set `assemblyName` (calc-field creation, worksheet column editing)
   // need a field of their own: `contextTable`.
   it("prefers contextTable over assemblyName for a calcField's table", () => {
      const comp = pairedDialog();
      comp.isCalc = true;
      comp.assemblyName = "Query1"; // set to a *different* value than contextTable, so this
                                    // test can only pass if contextTable, not assemblyName,
                                    // is what reaches `assembly`.
      comp.contextTable = "Table1";
      comp.formulaName = "Margin";

      expect(comp.editorContext).toEqual(
         { kind: "calcField", assembly: "Table1", name: "Margin" });
   });

   it("falls back to assemblyName for a calcField's table when contextTable is absent", () => {
      const comp = pairedDialog();
      comp.isCalc = true;
      comp.assemblyName = "Table2";
      comp.formulaName = "Margin";

      expect(comp.editorContext).toEqual(
         { kind: "calcField", assembly: "Table2", name: "Margin" });
   });
});

describe("FormulaEditorDialog — canPair", () => {
   it("offers no connect button without a sheet runtime", () => {
      const { comp } = createDialog();
      comp.runtimeId = null;
      comp.socketConnection = {} as ViewsheetClientService;

      expect(comp.canPair).toBe(false);
   });

   it("offers no connect button without a socket connection", () => {
      const { comp } = createDialog();
      comp.runtimeId = "vs-1";
      comp.socketConnection = null;

      expect(comp.canPair).toBe(false);
   });

   it("offers a connect button when both a runtime and a socket are supplied", () => {
      const comp = pairedDialog();

      expect(comp.canPair).toBe(true);
   });
});

/*
 * Whole-branch review finding 3, browser half. The server now refuses a mint for a
 * column-addressed kind carrying no `name` -- because such a grant matches NO target, so pairing
 * used to report success and then refuse every edit with 'Query1.null'. A button whose only
 * possible outcome is a server error is worse than no button, and `script-edit-pane` already sets
 * the precedent of suppressing rather than minting a code that cannot be joined.
 */
describe("FormulaEditorDialog — canPair suppresses an unaddressable location", () => {
   /* The reachable case: "new expression column" opens this dialog with no formulaName, because
    * the user has not named the thing yet. */
   it("offers no connect button for a worksheet expression with no formula name", () => {
      const comp = pairedDialog();
      comp.isVSContext = false;
      comp.assemblyName = "Table1";
      comp.formulaName = null;

      expect(comp.canPair).toBe(false);
   });

   it("offers no connect button for a new calculated field with no name yet", () => {
      const comp = pairedDialog();
      comp.isCalc = true;
      comp.assemblyName = "Query1";
      comp.formulaName = "";

      expect(comp.canPair).toBe(false);
   });

   /* A column-addressed kind needs BOTH halves of (table, field): the server checks the column
    * against that table's own column selection. */
   it("offers no connect button for a calculated field with no owning table", () => {
      const comp = pairedDialog();
      comp.isCalc = true;
      comp.formulaName = "Margin";

      expect(comp.canPair).toBe(false);
   });

   /* A worksheet-hosted condition is column-addressed (COLUMN_ADDRESSED_KINDS), so it needs a
    * column name just like calcField/worksheetExpression -- suppressed when the caller hasn't
    * supplied one (e.g. no field selected yet), addressable once it has (for a single-value
    * operator; see the multi-value/unknown-operator cases below). */
   it("offers no connect button for a worksheet-hosted condition with no column name", () => {
      const comp = pairedDialog();
      comp.isVSContext = false;
      comp.isCondition = true;
      comp.assemblyName = "Table1";
      comp.formulaName = null;
      comp.conditionOperation = ConditionOperation.EQUAL_TO;

      expect(comp.canPair).toBe(false);
   });

   it("offers a connect button for a worksheet-hosted condition on a real column with a single-value operator", () => {
      const comp = pairedDialog();
      comp.isVSContext = false;
      comp.isCondition = true;
      comp.assemblyName = "Table1";
      comp.formulaName = "Amount";
      comp.conditionOperation = ConditionOperation.EQUAL_TO;

      expect(comp.canPair).toBe(true);
   });

   /*
    * stylebi#4654's second review, finding 1: BETWEEN has two value slots, ONE_OF a list -- this
    * pane edits one text box, so neither has a single "the value" it could mean. Suppressing here
    * beats minting a worksheetConditionValue code the server would then reject.
    */
   it("offers no connect button for a worksheet condition using a multi-value operator", () => {
      const comp = pairedDialog();
      comp.isVSContext = false;
      comp.isCondition = true;
      comp.assemblyName = "Table1";
      comp.formulaName = "Amount";
      comp.conditionOperation = ConditionOperation.BETWEEN;

      expect(comp.canPair).toBe(false);
   });

   it("offers no connect button for a worksheet condition using the ONE_OF operator", () => {
      const comp = pairedDialog();
      comp.isVSContext = false;
      comp.isCondition = true;
      comp.assemblyName = "Table1";
      comp.formulaName = "Region";
      comp.conditionOperation = ConditionOperation.ONE_OF;

      expect(comp.canPair).toBe(false);
   });

   /* BinaryConditionEditor/OneOfConditionEditor never bind an operation into the ConditionEditor
    * instances they host, so this dialog sees conditionOperation as undefined for BETWEEN/ONE_OF
    * in real usage -- must suppress, not default to allowing it. */
   it("offers no connect button for a worksheet condition with no known operation", () => {
      const comp = pairedDialog();
      comp.isVSContext = false;
      comp.isCondition = true;
      comp.assemblyName = "Table1";
      comp.formulaName = "Amount";

      expect(comp.canPair).toBe(false);
   });

   /* The other half: kinds addressed by assembly alone are unaffected, and a fully addressed
    * column location is still offered. Without these, "suppress everything" would also pass. */
   it("still offers a connect button for an assembly-addressed location", () => {
      const comp = pairedDialog();
      comp.isVSContext = true;
      comp.isCondition = true;
      comp.assemblyName = "Table1";

      expect(comp.canPair).toBe(true);
   });

   it("still offers a connect button for a fully addressed calculated field", () => {
      const comp = pairedDialog();
      comp.isCalc = true;
      comp.assemblyName = "Query1";
      comp.formulaName = "Margin";

      expect(comp.canPair).toBe(true);
   });
});
