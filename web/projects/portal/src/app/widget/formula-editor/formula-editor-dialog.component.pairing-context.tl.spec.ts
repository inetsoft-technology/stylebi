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
 * FormulaEditorDialog - `pairingContext`, the exact target a host names for itself (bug #80459):
 * returned as is BEFORE every flag (so a hyperlink editor, which keeps `isCondition` for the
 * script palette, is never mistaken for a vsConditionValue), the two new draft-only kinds are
 * listed in the addressability tables so a blank assembly or name hides the button, and the cube
 * dialog never offers a button.
 */
import { Subject } from "rxjs";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { ViewsheetCommandMessage } from "../../common/viewsheet-client";
import { createDialog } from "./formula-editor-dialog.component.test-helpers";

function dialogWith(context: any, withTransport = true) {
   const { comp, ...rest } = createDialog();
   const commands = new Subject<ViewsheetCommandMessage>();
   comp.expression = "1";
   comp.pairingContext = context;

   if(withTransport) {
      comp.runtimeId = "rt-1";
      comp.socketConnection = { commands } as any;
   }

   return { comp, commands, ...rest };
}

function conditionHost(comp: any) {
   comp.isVSContext = true;
   comp.isCondition = true;
   comp.isHyperLink = true;
   comp.contextTable = "Text1";
   comp.assemblyName = "Text1";
   comp.conditionOperation = ConditionOperation.EQUAL_TO;
   comp.vsConditionScope = { assembly: "Other", highlightName: null };
}

describe("FormulaEditorDialog - pairingContext (bug #80459)", () => {
   it("returns the explicit context as is, over every flag the hyperlink editor keeps", () => {
      const context = { kind: "vsHyperlinkExpression", assembly: "Table1", name: "cell[2,1]" };
      const { comp } = dialogWith(context);
      conditionHost(comp);

      expect(comp.editorContext).toBe(context);
      expect(comp.canPair).toBe(true);
   });

   it("the same flags WITHOUT an explicit context derive vsConditionValue (hazard H3 control)", () => {
      const { comp } = dialogWith(undefined);
      conditionHost(comp);

      expect(comp.editorContext.kind).toBe("vsConditionValue");
      expect(comp.editorContext.assembly).toBe("Other");
   });

   it("wins over dynamicProperty, isCalc and isCalcTable too", () => {
      const context = { kind: "worksheetVariableDefault", assembly: "Var1", pending: true };
      const { comp } = dialogWith(context);
      comp.dynamicProperty = "visible";
      comp.isCalc = true;
      comp.isCalcTable = true;

      expect(comp.editorContext).toBe(context);
   });

   it("a null or absent pairingContext leaves the derivation to the flags", () => {
      const { comp } = dialogWith(null);
      comp.isVSContext = true;
      comp.dynamicProperty = "visible";
      comp.contextTable = "Text1";

      expect(comp.editorContext).toEqual(
         { kind: "dynamicValue", assembly: "Text1", name: "visible" });
   });

   it("offers a worksheet variable default with an assembly and no name", () => {
      const { comp } = dialogWith(
         { kind: "worksheetVariableDefault", assembly: "Var1", pending: false });

      expect(comp.canPair).toBe(true);
   });

   it.each([[undefined], [null], [""]])(
      "hides the button for a worksheet variable default with the blank assembly %p",
      (assembly: any) =>
   {
      const { comp } = dialogWith({ kind: "worksheetVariableDefault", assembly, pending: true });

      expect(comp.canPair).toBe(false);
   });

   it("hides the button for a worksheet variable default that carries a name", () => {
      const { comp } = dialogWith(
         { kind: "worksheetVariableDefault", assembly: "Var1", name: "x" });

      expect(comp.canPair).toBe(false);
   });

   it.each([
      [{ kind: "vsHyperlinkExpression", assembly: "Text1" }],
      [{ kind: "vsHyperlinkExpression", assembly: "Text1", name: "" }],
      [{ kind: "vsHyperlinkExpression", name: "link" }],
   ])("hides the button for the unaddressed hyperlink context %j", (context: any) => {
      const { comp } = dialogWith(context);

      expect(comp.canPair).toBe(false);
   });

   it("offers a hyperlink context with both an assembly and a region", () => {
      const { comp } = dialogWith({ kind: "vsHyperlinkExpression", assembly: "Text1", name: "link" });

      expect(comp.canPair).toBe(true);
   });

   it("shows no button without a pairing transport, whatever the context", () => {
      const { comp } = dialogWith(
         { kind: "worksheetVariableDefault", assembly: "Var1" }, false);

      expect(comp.canPair).toBe(false);
      expect(comp.editorContext).toBeNull();
   });

   it("applies an agent push for exactly the explicit context", () => {
      const { comp, commands } = dialogWith(
         { kind: "vsHyperlinkExpression", assembly: "Table1", name: "cell[2,1]" });
      conditionHost(comp);
      comp.ngOnInit();

      commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand",
         { kind: "vsHyperlinkExpression", assembly: "Table1", name: "cell[2,1]", text: "=1 + 1" }));
      expect(comp.expression).toBe("=1 + 1");

      commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand",
         { kind: "vsHyperlinkExpression", assembly: "Table1", name: "cell[0,0]", text: "=9" }));
      commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand",
         { kind: "vsConditionValue", assembly: "Table1", name: "filter", text: "=9" }));
      expect(comp.expression).toBe("=1 + 1");
   });
});

describe("FormulaEditorDialog - cube edit never pairs (bug #80459 decision D2)", () => {
   it("the cube Create/Edit Measure dialog has no context and no button", () => {
      const { comp } = dialogWith(undefined);
      comp.isCube = true;
      comp.isCalc = true;
      comp.formulaName = "Measure1";
      comp.contextTable = "Cube1";
      comp.assemblyName = "Cube1";

      expect(comp.editorContext).toBeNull();
      expect(comp.canPair).toBe(false);
   });

   it("the same calc dialog without isCube still pairs (control)", () => {
      const { comp } = dialogWith(undefined);
      comp.isCalc = true;
      comp.formulaName = "Measure1";
      comp.contextTable = "Table1";
      comp.assemblyName = "Table1";

      expect(comp.canPair).toBe(true);
      expect(comp.editorContext.kind).toBe("calcField");
   });
});
