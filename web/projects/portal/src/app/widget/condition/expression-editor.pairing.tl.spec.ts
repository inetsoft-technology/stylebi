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
 * ExpressionEditor - the `pairing` input (bug #80459): a host that is not a condition names its
 * own pairing target. The function is evaluated ONCE when the formula dialog opens (it sits in an
 * ng-template that is re-checked every change detection), and what it returns reaches the dialog
 * as runtime, socket and `pairingContext`. The formula dialog is replaced by a recording stub, so
 * the spec sees exactly which inputs the editor's template forwards.
 */
// The recording stub binds many inputs of the component it replaces; listing them in `inputs`
// is far shorter than one @Input() per property.
/* eslint-disable @angular-eslint/no-inputs-metadata-property */
import { Component, EventEmitter, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { render } from "@testing-library/angular";
import { of } from "rxjs";
import { ExpressionType } from "../../common/data/condition/expression-type";
import { EditorContext, ExpressionPairing } from "../../composer/gui/wiz/editor-context";
import { FormulaEditorDialog } from "../formula-editor/formula-editor-dialog.component";
import { ExpressionEditor } from "./expression-editor.component";

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
   isCondition: boolean;
   @Output() onCommit = new EventEmitter<any>();
   @Output() onCancel = new EventEmitter<any>();
   constructor() { StubFormulaDialog.instances.push(this); }
}

const HOST_SOCKET = { host: true } as any;
const PAIR_SOCKET = { pair: true } as any;
const CONTEXT: EditorContext = { kind: "vsHyperlinkExpression", assembly: "Text1", name: "link" };

async function renderEditor(pairing: (() => ExpressionPairing | null) | null | undefined,
                            extra: any = {})
{
   const result = await render(ExpressionEditor, {
      schemas: [NO_ERRORS_SCHEMA],
      configureTestBed: (bed) => {
         bed.overrideComponent(ExpressionEditor, {
            remove: { imports: [FormulaEditorDialog] },
            add: { imports: [StubFormulaDialog, FormsModule] },
         });
      },
      componentProperties: {
         expressionTypes: [ExpressionType.JS],
         columnTreeFunction: () => of({ children: [] }),
         value: { expression: "=1", type: ExpressionType.JS },
         ...(pairing !== undefined ? { pairing } : {}),
         ...extra,
      } as any,
   });

   return result;
}

async function open(result: Awaited<ReturnType<typeof renderEditor>>) {
   result.fixture.componentInstance.openFormulaWindow();
   result.fixture.detectChanges();
   await result.fixture.whenStable();
   result.fixture.detectChanges();
   return StubFormulaDialog.instances[StubFormulaDialog.instances.length - 1];
}

afterEach(() => {
   StubFormulaDialog.instances.length = 0;
});

describe("ExpressionEditor - pairing input (bug #80459)", () => {
   it("hands the host's runtime, socket and context to the formula dialog", async () => {
      const pairing = vi.fn(() => ({ runtimeId: "rt-1", socketConnection: PAIR_SOCKET,
                                      editorContext: CONTEXT }));
      const dialog = await open(await renderEditor(pairing));

      expect(dialog.runtimeId).toBe("rt-1");
      expect(dialog.socketConnection).toBe(PAIR_SOCKET);
      expect(dialog.pairingContext).toBe(CONTEXT);
   });

   it("evaluates the function once per open, not once per change detection", async () => {
      const pairing = vi.fn(() => ({ runtimeId: "rt-1", socketConnection: PAIR_SOCKET,
                                      editorContext: CONTEXT }));
      const result = await renderEditor(pairing);
      await open(result);
      result.fixture.detectChanges();
      result.fixture.detectChanges();
      result.fixture.detectChanges();

      expect(pairing).toHaveBeenCalledTimes(1);
   });

   it("evaluates it again when the dialog is opened again", async () => {
      const pairing = vi.fn(() => ({ runtimeId: "rt-1", socketConnection: PAIR_SOCKET,
                                      editorContext: CONTEXT }));
      const result = await renderEditor(pairing);
      await open(result);
      await open(result);

      expect(pairing).toHaveBeenCalledTimes(2);
   });

   it("a function that answers null leaves the dialog on the host's own runtime and socket", async () => {
      const dialog = await open(await renderEditor(
         () => null, { runtimeId: "rt-host", socketConnection: HOST_SOCKET }));

      expect(dialog.runtimeId).toBe("rt-host");
      expect(dialog.socketConnection).toBe(HOST_SOCKET);
      expect(dialog.pairingContext).toBeFalsy();
   });

   it("without the input the condition hosts are unchanged: their own runtime and socket, no context",
      async () =>
   {
      const dialog = await open(await renderEditor(
         undefined, { runtimeId: "rt-host", socketConnection: HOST_SOCKET }));

      expect(dialog.runtimeId).toBe("rt-host");
      expect(dialog.socketConnection).toBe(HOST_SOCKET);
      expect(dialog.pairingContext).toBeFalsy();
      expect(dialog.isCondition).toBe(true);
   });

   it("the pairing runtime and socket win over the host's", async () => {
      const dialog = await open(await renderEditor(
         () => ({ runtimeId: "rt-1", socketConnection: PAIR_SOCKET, editorContext: CONTEXT }),
         { runtimeId: "rt-host", socketConnection: HOST_SOCKET }));

      expect(dialog.runtimeId).toBe("rt-1");
      expect(dialog.socketConnection).toBe(PAIR_SOCKET);
   });
});
