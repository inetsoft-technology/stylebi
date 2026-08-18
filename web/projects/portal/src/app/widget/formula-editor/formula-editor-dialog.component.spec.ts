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

   it("derives a worksheetCondition context for a worksheet-hosted condition", () => {
      const comp = pairedDialog();
      comp.isVSContext = false;
      comp.isCondition = true;
      comp.assemblyName = "Table1";

      expect(comp.editorContext.kind).toBe("worksheetCondition");
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

   /* The condition branch derives worksheetCondition and supplies no `name` at all, so it can
    * never be addressed -- pinned here so the suppression is a stated property, not an accident
    * of that branch's shape. */
   it("offers no connect button for a worksheet-hosted condition, which carries no column name",
      () => {
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
