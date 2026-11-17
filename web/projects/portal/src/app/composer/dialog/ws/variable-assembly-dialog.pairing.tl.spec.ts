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
 * VariableAssemblyDialog - the default-value Expression pairs as a `worksheetVariableDefault`
 * context (bug #80459). The real dialog and the real expression editor are rendered; only the
 * formula dialog is a recording stub, so the spec sees the inputs the chain actually delivers.
 * The target is addressed by the variable's own name (an existing one) or the name typed so far
 * (a new one, pending); a worksheet with no socket, or a new variable with no name, gets none.
 */
// The recording stub binds many inputs of the component it replaces; listing them in `inputs`
// is far shorter than one @Input() per property.
/* eslint-disable @angular-eslint/no-inputs-metadata-property */
import { Component, EventEmitter, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { render } from "@testing-library/angular";
import { of } from "rxjs";
import { ConditionValueType } from "../../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../../common/data/condition/expression-type";
import { FormulaEditorDialog } from "../../../widget/formula-editor/formula-editor-dialog.component";
import { HelpUrlService } from "../../../widget/help-link/help-url.service";
import { ModelService } from "../../../widget/services/model.service";
import { EditorContext } from "../../gui/wiz/editor-context";
import { VariableAssemblyDialog } from "./variable-assembly-dialog.component";
import { makeModel, makeWorksheet } from "./variable-assembly-dialog.component.test-helpers";
import { ExpressionEditor } from "../../../widget/condition/expression-editor.component";

@Component({ selector: "formula-editor-dialog", template: "",
             inputs: ["expression", "formulaType", "columnTreeRoot", "nameVisible", "isVSContext",
                      "isHighlight", "returnTypeVisible", "vsId", "columnTreeEnabled", "columns",
                      "isHyperLink", "functionTreeEnabled", "scriptDefinitions", "resizeable",
                      "showOriginalName", "isCondition", "contextTable", "formulaName",
                      "conditionOperation", "runtimeId", "socketConnection", "vsConditionScope",
                      "pairingContext"] })
class StubFormulaDialog {
   static instances: StubFormulaDialog[] = [];
   runtimeId: string;
   socketConnection: any;
   pairingContext: EditorContext;
   isVSContext: boolean;
   @Output() onCommit = new EventEmitter<any>();
   @Output() onCancel = new EventEmitter<any>();
   constructor() { StubFormulaDialog.instances.push(this); }
}

const SOCKET = { commands: null } as any;

async function renderDialog(modelOverrides: any = {}, worksheetOverrides: any = {}) {
   const result = await render(VariableAssemblyDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [{ provide: ModelService, useValue: { getModel: vi.fn(), sendModel: vi.fn() } },
                  { provide: HelpUrlService, useValue: { getHelpUrl: () => of("") } }],
      configureTestBed: (bed) => {
         bed.overrideComponent(ExpressionEditor, {
            remove: { imports: [FormulaEditorDialog] },
            add: { imports: [StubFormulaDialog, FormsModule] },
         });
      },
      componentProperties: {
         worksheet: makeWorksheet({ socketConnection: SOCKET, ...worksheetOverrides } as any),
         tables: [],
         model: makeModel({
            defaultValue: { jsonType: "expression", type: ExpressionType.JS, expression: "1" } as any,
            ...modelOverrides,
         }),
      } as any,
   });

   return result;
}

async function openFormulaDialog(result: Awaited<ReturnType<typeof renderDialog>>) {
   const button = result.container.querySelector("expression-editor .formula-edit-icon")
      ?.closest("button") as HTMLButtonElement;
   expect(button).toBeTruthy();
   button.click();
   result.fixture.detectChanges();
   await result.fixture.whenStable();
   result.fixture.detectChanges();
   return StubFormulaDialog.instances[StubFormulaDialog.instances.length - 1];
}

afterEach(() => {
   StubFormulaDialog.instances.length = 0;
});

describe("VariableAssemblyDialog - default value pairing (bug #80459)", () => {
   it("pairs an existing variable by its own name, not pending", async () => {
      const result = await renderDialog({ oldName: "Var1" });
      const dialog = await openFormulaDialog(result);

      expect(dialog.pairingContext).toEqual(
         { kind: "worksheetVariableDefault", assembly: "Var1", pending: false });
      expect(dialog.runtimeId).toBe("ws-rt-123");
      expect(dialog.socketConnection).toBe(SOCKET);
      expect(dialog.isVSContext).toBe(false);
   });

   it("pairs a new variable by the name typed so far, pending", async () => {
      const result = await renderDialog({ oldName: null });
      result.fixture.componentInstance.form.get("newName").setValue("  NewVar ");
      const dialog = await openFormulaDialog(result);

      expect(dialog.pairingContext).toEqual(
         { kind: "worksheetVariableDefault", assembly: "NewVar", pending: true });
   });

   it("an existing variable keeps its worksheet name after the Name field is edited", async () => {
      const result = await renderDialog({ oldName: "Var1" });
      result.fixture.componentInstance.form.get("newName").setValue("Renamed");
      const dialog = await openFormulaDialog(result);

      expect(dialog.pairingContext.assembly).toBe("Var1");
   });

   it("a new variable with no name yet gets no context", async () => {
      const result = await renderDialog({ oldName: null });
      result.fixture.componentInstance.form.get("newName").setValue("  ");
      const dialog = await openFormulaDialog(result);

      expect(dialog.pairingContext).toBeFalsy();
   });

   it("a worksheet with no socket gets no context", async () => {
      const result = await renderDialog({ oldName: "Var1" }, { socketConnection: undefined });
      const dialog = await openFormulaDialog(result);

      expect(dialog.pairingContext).toBeFalsy();
   });

   it("a worksheet with no runtime id gets no context", async () => {
      const result = await renderDialog({ oldName: "Var1" }, { runtimeId: undefined });
      const dialog = await openFormulaDialog(result);

      expect(dialog.pairingContext).toBeFalsy();
   });

   it("renders no Expression editor, so no button, for a plain Value default", async () => {
      const result = await renderDialog({ oldName: "Var1", defaultValue: "abc" as any });

      expect(result.container.querySelector("expression-editor")).toBeNull();
      expect(result.fixture.componentInstance.defaultValueType).toBe(ConditionValueType.VALUE);
   });
});
