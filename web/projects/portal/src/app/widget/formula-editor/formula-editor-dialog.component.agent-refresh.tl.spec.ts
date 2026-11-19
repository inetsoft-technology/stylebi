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
 * FormulaEditorDialog — agent script live-refresh (bug #80447 symptom 1).
 *
 * Covers the calc-field formula editor case the bug report names explicitly: an agent writes a
 * calc field's expression (via update_script or edit_calc_field) while this dialog is already
 * open on that exact field, and the dialog must refresh itself rather than stay stuck on the
 * stale text it opened with -- unless the human has typed a local edit since, in which case it
 * must defer rather than clobber.
 */
import { Subject } from "rxjs";
import { ViewsheetCommandMessage } from "../../common/viewsheet-client";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { ExpressionType } from "../../common/data/condition/expression-type";
import { createDialog } from "./formula-editor-dialog.component.test-helpers";

function socket() {
   const commands = new Subject<ViewsheetCommandMessage>();
   return { commands, connection: { commands } as any };
}

function agentScriptCommand(overrides: Partial<{ kind: string, assembly: string, name: string, text: string }>) {
   return new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", {
      kind: "calcField", assembly: "Orders", name: "Margin", text: "new();", ...overrides,
   } as any);
}

function calcFieldDialog() {
   const { comp, ...rest } = createDialog();
   const { commands, connection } = socket();
   comp.isCalc = true;
   comp.contextTable = "Orders";
   comp.formulaName = "Margin";
   comp.runtimeId = "Viewsheet1";
   comp.socketConnection = connection;
   comp.expression = "old();";
   comp.ngOnInit();
   return { comp, commands, ...rest };
}

describe("FormulaEditorDialog — agent script live-refresh (bug #80447)", () => {
   it("applies a push for the exact calc field this dialog is editing", () => {
      const { comp, commands } = calcFieldDialog();

      commands.next(agentScriptCommand({ text: "field['Price'] - field['Cost']" }));

      expect(comp.expression).toBe("field['Price'] - field['Cost']");
      expect(comp.staleExpression).toBeNull();
   });

   it("ignores a push for a different calc field name", () => {
      const { comp, commands } = calcFieldDialog();

      commands.next(agentScriptCommand({ name: "Total", text: "irrelevant();" }));

      expect(comp.expression).toBe("old();");
   });

   it("ignores a push for a different table", () => {
      const { comp, commands } = calcFieldDialog();

      commands.next(agentScriptCommand({ assembly: "Products", text: "irrelevant();" }));

      expect(comp.expression).toBe("old();");
   });

   it("defers (does not clobber) when the human has typed since this dialog opened", () => {
      const { comp, commands } = calcFieldDialog();
      comp.expression = "old(); // my local edit";

      commands.next(agentScriptCommand({ text: "new();" }));

      expect(comp.expression).toBe("old(); // my local edit");
      expect(comp.staleExpression).toBe("new();");
   });

   it("reloadStaleExpression() applies the deferred push and clears the stale flag", () => {
      const { comp, commands } = calcFieldDialog();
      comp.expression = "old(); // my local edit";
      commands.next(agentScriptCommand({ text: "new();" }));

      comp.reloadStaleExpression();

      expect(comp.expression).toBe("new();");
      expect(comp.staleExpression).toBeNull();
   });

   it("does nothing when this dialog has no runtimeId/socketConnection (no pairing transport)", () => {
      const { comp } = createDialog();
      comp.isCalc = true;
      comp.contextTable = "Orders";
      comp.formulaName = "Margin";
      comp.expression = "old();";

      // No runtimeId/socketConnection set -- ngOnInit must not throw subscribing to nothing.
      expect(() => comp.ngOnInit()).not.toThrow();
      expect(comp.expression).toBe("old();");
   });
});

function worksheetExpressionDialog(isNew: boolean) {
   const { comp, ...rest } = createDialog();
   const { commands, connection } = socket();
   comp.isCalc = false;
   comp.isVSContext = false;
   comp.isNewExpression = isNew;
   comp.contextTable = "Query1";
   comp.formulaName = isNew ? "Expression1" : "Margin";
   comp.runtimeId = "Worksheet1";
   comp.socketConnection = connection;
   comp.expression = "";
   comp.ngOnInit();
   return { comp, commands, ...rest };
}

function worksheetExpressionCommand(name: string, text: string) {
   return new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", {
      kind: "worksheetExpression", assembly: "Query1", name, text,
   } as any);
}

describe("FormulaEditorDialog — worksheet expression agent push (bug #80462)", () => {
   it("applies an agent draft pushed into the Create Expression dialog", () => {
      const { comp, commands } = worksheetExpressionDialog(true);

      commands.next(worksheetExpressionCommand("Expression1", "field['price'] * 2"));

      expect(comp.expression).toBe("field['price'] * 2");
      expect(comp.staleExpression).toBeNull();
   });

   it("parks the draft behind the reload prompt when the user already typed", () => {
      const { comp, commands } = worksheetExpressionDialog(true);
      comp.expression = "typed by hand";

      commands.next(worksheetExpressionCommand("Expression1", "field['price'] * 2"));

      expect(comp.expression).toBe("typed by hand");
      expect(comp.staleExpression).toBe("field['price'] * 2");
   });

   it("refreshes an Edit Expression dialog when the agent writes that column", () => {
      const { comp, commands } = worksheetExpressionDialog(false);
      comp.expression = "field['price']";
      comp.ngOnInit();

      commands.next(worksheetExpressionCommand("Margin", "field['cost']"));

      expect(comp.expression).toBe("field['cost']");
   });
});

function modeCommand(overrides: Record<string, any>) {
   return new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", {
      kind: "worksheetExpression", assembly: "Query1", name: "Margin", text: null, ...overrides,
   } as any);
}

describe("FormulaEditorDialog — agent SQL/Script mode and return type draft (bug #81790)", () => {
   function dialog(formulaType: string, dataType: string) {
      const { comp, ...rest } = createDialog();
      const { commands, connection } = socket();
      comp.isCalc = false;
      comp.isVSContext = false;
      comp.contextTable = "Query1";
      comp.formulaName = "Margin";
      comp.runtimeId = "Worksheet1";
      comp.socketConnection = connection;
      comp.expression = "field['price']";
      comp.formulaType = formulaType;
      comp.dataType = dataType;
      comp.ngOnInit();
      return { comp, commands, ...rest };
   }

   it("switches the dialog to SQL and sets the return type, leaving the text alone", () => {
      const { comp, commands } = dialog("Script", "string");

      commands.next(modeCommand({ sql: true, returnType: "integer" }));

      expect(comp.form.get("formulaType").value).toBe("SQL");
      expect(comp.form.get("dataType").value).toBe("integer");
      expect(comp.expression).toBe("field['price']");
      expect(comp.hasStalePush).toBe(false);
   });

   it("switches back to Script when sql is false", () => {
      const { comp, commands } = dialog("SQL", "string");

      commands.next(modeCommand({ sql: false }));

      expect(comp.form.get("formulaType").value).toBe("Script");
   });

   it("parks the mode behind the reload prompt when the user already changed it", () => {
      const { comp, commands } = dialog("Script", "string");
      comp.form.get("formulaType").setValue("SQL");
      comp.form.get("dataType").setValue("double");

      commands.next(modeCommand({ sql: false, returnType: "integer" }));

      expect(comp.form.get("formulaType").value).toBe("SQL");
      expect(comp.form.get("dataType").value).toBe("double");
      expect(comp.hasStalePush).toBe(true);

      comp.reloadStaleExpression();

      expect(comp.form.get("formulaType").value).toBe("Script");
      expect(comp.form.get("dataType").value).toBe("integer");
      expect(comp.hasStalePush).toBe(false);
   });

   it("ignores a mode push addressed to another column", () => {
      const { comp, commands } = dialog("Script", "string");

      commands.next(modeCommand({ name: "Other", sql: true }));

      expect(comp.form.get("formulaType").value).toBe("Script");
   });

   it("flips a worksheet condition value's ExpressionType, which has no radio control", () => {
      const { comp, ...rest } = createDialog();
      const { commands, connection } = socket();
      comp.isVSContext = false;
      comp.isCondition = true;
      comp.returnTypeVisible = false;
      comp.contextTable = "Query1";
      comp.formulaName = "Price";
      comp.conditionOperation = ConditionOperation.GREATER_THAN;
      comp.runtimeId = "Worksheet1";
      comp.socketConnection = connection;
      comp.expression = "0";
      comp.formulaType = ExpressionType.JS;
      comp.ngOnInit();
      expect(comp.editorContext?.kind).toBe("worksheetConditionValue");

      commands.next(modeCommand({ kind: "worksheetConditionValue", name: "Price", sql: true }));
      expect(comp.formulaType).toBe(ExpressionType.SQL);
      expect(comp.isSqlType()).toBe(true);

      commands.next(modeCommand({ kind: "worksheetConditionValue", name: "Price", sql: false }));
      expect(comp.formulaType).toBe(ExpressionType.JS);
   });
});
