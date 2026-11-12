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
 * The viewsheet condition-value pairing scope (bug #80459) travels
 * condition-pane -> condition-item-pane -> condition-editor -> expression-editor ->
 * formula-editor-dialog by plain @Input bindings; one dropped hop silently removes the Connect
 * to Claude button. Each hop is rendered with its child replaced by a recording stub, so the
 * test sees exactly which inputs the hop's template forwards. BETWEEN's two value slots and
 * ONE_OF each get their own condition-editor, so both editors must forward `operation`,
 * `isHighlight` and the scope too.
 */
// The recording stubs below bind many inputs of the component they replace; listing them in
// `inputs` is far shorter than one @Input() per property.
/* eslint-disable @angular-eslint/no-inputs-metadata-property */
import { Component, NO_ERRORS_SCHEMA, ViewContainerRef } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { render } from "@testing-library/angular";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../common/data/condition/expression-type";
import { VSConditionPairingScope } from "../../composer/gui/wiz/editor-context";
import { FormulaEditorDialog } from "../formula-editor/formula-editor-dialog.component";
import { BinaryConditionEditor } from "./binary-condition-editor.component";
import { ConditionEditor } from "./condition-editor.component";
import { ConditionItemPane } from "./condition-item-pane.component";
import { ConditionOperationPipe } from "./condition-operation.pipe";
import { ConditionPane } from "./condition-pane.component";
import { makeCondition, makeField, makeProvider } from "./condition-pane.test-helpers";
import { ConditionPipe } from "./condition.pipe";
import { ExpressionEditor } from "./expression-editor.component";
import { JunctionOperatorPipe } from "./junction-operator.pipe";
import { OneOfConditionEditor } from "./one-of-condition-editor.component";

const SCOPE: VSConditionPairingScope = { assembly: "Table1", highlightName: "High" };
const SOCKET = { commands: null } as any;

// Every input the parent templates bind on these children; recording only what matters.
const COMMON = ["dataFunction", "variablesFunction", "columnTreeFunction",
   "scriptDefinitionsFunction", "expressionTypes", "valueTypes", "subqueryTables",
   "fieldsModel", "grayedOutFields", "field", "table", "runtimeId", "socketConnection",
   "showUseList", "source", "isVSContext", "enableBrowseData", "showOriginalName", "vsId",
   "operation", "isHighlight", "vsConditionScope"];

@Component({ selector: "condition-item-pane", template: "",
             inputs: ["provider", "subqueryTables", "availableFields", "isVSContext", "fields",
                      "table", "runtimeId", "socketConnection", "addNoneItem", "condition",
                      "showExpression", "showOriginalName", "vsId", "isHighlight",
                      "vsConditionScope"] })
class StubItemPane {
   static instances: StubItemPane[] = [];
   runtimeId: string;
   socketConnection: any;
   vsConditionScope: VSConditionPairingScope;
   isHighlight: boolean;
   constructor() { StubItemPane.instances.push(this); }
}

@Component({ selector: "condition-field-combo", template: "",
             inputs: ["field", "fieldsModel", "addNoneItem", "grayedOutFields",
                      "showOriginalName"] })
class StubFieldCombo {
}

@Component({ selector: "condition-editor", template: "", inputs: [...COMMON, "value", "values",
                                                                  "isOneOf", "showUseList"] })
class StubConditionEditor {
   static instances: StubConditionEditor[] = [];
   operation: ConditionOperation;
   isHighlight: boolean;
   vsConditionScope: VSConditionPairingScope;
   runtimeId: string;
   socketConnection: any;
   constructor() { StubConditionEditor.instances.push(this); }
}

@Component({ selector: "one-of-condition-editor", template: "",
             inputs: [...COMMON, "values"] })
class StubOneOf {
   static instances: StubOneOf[] = [];
   operation: ConditionOperation;
   isHighlight: boolean;
   vsConditionScope: VSConditionPairingScope;
   constructor() { StubOneOf.instances.push(this); }
}

@Component({ selector: "binary-condition-editor", template: "",
             inputs: [...COMMON, "values"] })
class StubBinary {
   static instances: StubBinary[] = [];
   operation: ConditionOperation;
   isHighlight: boolean;
   vsConditionScope: VSConditionPairingScope;
   constructor() { StubBinary.instances.push(this); }
}

@Component({ selector: "expression-editor", template: "",
             inputs: [...COMMON, "value", "expressionTypes", "columns", "isHyperLink"] })
class StubExpressionEditor {
   static instances: StubExpressionEditor[] = [];
   operation: ConditionOperation;
   isHighlight: boolean;
   vsConditionScope: VSConditionPairingScope;
   constructor() { StubExpressionEditor.instances.push(this); }
}

@Component({ selector: "formula-editor-dialog", template: "",
             inputs: ["expression", "formulaType", "columnTreeRoot", "nameVisible", "isVSContext",
                      "isHighlight", "returnTypeVisible", "vsId", "columnTreeEnabled", "columns",
                      "isHyperLink", "functionTreeEnabled", "scriptDefinitions", "resizeable",
                      "showOriginalName", "isCondition", "contextTable", "formulaName",
                      "conditionOperation", "runtimeId", "socketConnection", "vsConditionScope"] })
class StubFormulaDialog {
   static instances: StubFormulaDialog[] = [];
   isHighlight: boolean;
   conditionOperation: ConditionOperation;
   vsConditionScope: VSConditionPairingScope;
   constructor() { StubFormulaDialog.instances.push(this); }
}

afterEach(() => {
   for(const stub of [StubItemPane, StubConditionEditor, StubOneOf, StubBinary,
                      StubExpressionEditor, StubFormulaDialog])
   {
      stub.instances.length = 0;
   }
});

describe("viewsheet condition pairing scope threading (bug #80459)", () => {
   it("condition-pane hands runtime, socket and scope to the item pane", async () => {
      await render(ConditionPane, {
         componentImports: [StubItemPane, FormsModule, ConditionPipe, JunctionOperatorPipe],
         schemas: [NO_ERRORS_SCHEMA],
         componentProperties: {
            conditionList: [], runtimeId: "rt-1", socketConnection: SOCKET, isHighlight: true,
            vsConditionScope: SCOPE,
         } as any,
      });

      const pane = StubItemPane.instances[0];
      expect(pane.runtimeId).toBe("rt-1");
      expect(pane.socketConnection).toBe(SOCKET);
      expect(pane.vsConditionScope).toBe(SCOPE);
      expect(pane.isHighlight).toBe(true);
   });

   async function renderItemPane(operation: ConditionOperation) {
      await render(ConditionItemPane, {
         componentImports: [StubFieldCombo, FormsModule, StubOneOf, StubBinary,
                            StubConditionEditor, FormulaEditorDialog, ConditionOperationPipe],
         schemas: [NO_ERRORS_SCHEMA],
         componentProperties: {
            provider: makeProvider({
               getConditionOperations: vi.fn(() => [operation]),
               getConditionValueTypes: vi.fn(() => [ConditionValueType.EXPRESSION]),
            } as any),
            condition: makeCondition({
               operation,
               values: [{ type: ConditionValueType.EXPRESSION,
                          value: { expression: null, type: ExpressionType.JS } },
                        { type: ConditionValueType.EXPRESSION,
                          value: { expression: null, type: ExpressionType.JS } }],
            }),
            fields: [makeField("state")],
            runtimeId: "rt-1", socketConnection: SOCKET, isHighlight: true,
            vsConditionScope: SCOPE,
         } as any,
      });
   }

   it("condition-item-pane forwards the scope to a single-value condition-editor", async () => {
      await renderItemPane(ConditionOperation.GREATER_THAN);

      const editor = StubConditionEditor.instances[0];
      expect(editor.vsConditionScope).toBe(SCOPE);
      expect(editor.operation).toBe(ConditionOperation.GREATER_THAN);
      expect(editor.isHighlight).toBe(true);
   });

   it("condition-item-pane forwards operation, isHighlight and scope to the BETWEEN editor",
      async () =>
   {
      await renderItemPane(ConditionOperation.BETWEEN);

      const editor = StubBinary.instances[0];
      expect(editor.vsConditionScope).toBe(SCOPE);
      expect(editor.operation).toBe(ConditionOperation.BETWEEN);
      expect(editor.isHighlight).toBe(true);
   });

   it("condition-item-pane forwards operation, isHighlight and scope to the ONE_OF editor",
      async () =>
   {
      await renderItemPane(ConditionOperation.ONE_OF);

      const editor = StubOneOf.instances[0];
      expect(editor.vsConditionScope).toBe(SCOPE);
      expect(editor.operation).toBe(ConditionOperation.ONE_OF);
      expect(editor.isHighlight).toBe(true);
   });

   it("binary-condition-editor gives BOTH value slots the operation, isHighlight and scope",
      async () =>
   {
      await render(BinaryConditionEditor, {
         componentImports: [StubConditionEditor],
         schemas: [NO_ERRORS_SCHEMA],
         componentProperties: {
            values: [null, null], operation: ConditionOperation.BETWEEN, isHighlight: true,
            vsConditionScope: SCOPE, runtimeId: "rt-1", socketConnection: SOCKET,
         } as any,
      });

      expect(StubConditionEditor.instances.length).toBe(2);

      for(const editor of StubConditionEditor.instances) {
         expect(editor.vsConditionScope).toBe(SCOPE);
         expect(editor.operation).toBe(ConditionOperation.BETWEEN);
         expect(editor.isHighlight).toBe(true);
         expect(editor.runtimeId).toBe("rt-1");
         expect(editor.socketConnection).toBe(SOCKET);
      }
   });

   it("one-of-condition-editor gives its value editor the operation, isHighlight and scope",
      async () =>
   {
      await render(OneOfConditionEditor, {
         componentImports: [StubConditionEditor],
         schemas: [NO_ERRORS_SCHEMA],
         componentProperties: {
            values: [], value: { type: ConditionValueType.VALUE, value: null },
            operation: ConditionOperation.ONE_OF, isHighlight: true, vsConditionScope: SCOPE,
         } as any,
      });

      const editor = StubConditionEditor.instances[0];
      expect(editor.vsConditionScope).toBe(SCOPE);
      expect(editor.operation).toBe(ConditionOperation.ONE_OF);
      expect(editor.isHighlight).toBe(true);
   });

   it("condition-editor hands the scope to its expression editor", async () => {
      await render(ConditionEditor, {
         componentImports: [StubExpressionEditor],
         schemas: [NO_ERRORS_SCHEMA],
         componentProperties: {
            operation: ConditionOperation.EQUAL_TO, isHighlight: true, vsConditionScope: SCOPE,
            value: { type: ConditionValueType.EXPRESSION,
                     value: { expression: "1", type: ExpressionType.JS } },
            field: makeField("state"), valueTypes: [ConditionValueType.EXPRESSION],
         } as any,
      });

      const editor = StubExpressionEditor.instances[0];
      expect(editor.vsConditionScope).toBe(SCOPE);
      expect(editor.operation).toBe(ConditionOperation.EQUAL_TO);
      expect(editor.isHighlight).toBe(true);
   });

   it("expression-editor hands the scope, operation and isHighlight to the formula dialog",
      async () =>
   {
      const { fixture } = await render(ExpressionEditor, {
         componentImports: [FormsModule, StubFormulaDialog],
         schemas: [NO_ERRORS_SCHEMA],
         componentProperties: {
            expressionTypes: [ExpressionType.JS],
            value: { expression: "1", type: ExpressionType.JS },
            operation: ConditionOperation.BETWEEN, isHighlight: true, vsConditionScope: SCOPE,
         } as any,
      });

      const view = fixture.componentRef.injector.get(ViewContainerRef)
         .createEmbeddedView(fixture.componentInstance.formulaEditorDialog,
                             { close: () => {}, dismiss: () => {} });
      view.detectChanges();

      const dialog = StubFormulaDialog.instances[0];
      expect(dialog.vsConditionScope).toBe(SCOPE);
      expect(dialog.conditionOperation).toBe(ConditionOperation.BETWEEN);
      expect(dialog.isHighlight).toBe(true);
   });
});
