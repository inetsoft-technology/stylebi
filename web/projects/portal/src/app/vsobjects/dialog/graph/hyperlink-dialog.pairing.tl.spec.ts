/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * HyperlinkDialog - the Web Link Expression pairs as a `vsHyperlinkExpression` context (bug
 * #80459). The real dialog and the real expression editor are rendered; only the formula dialog
 * is a recording stub, so the spec sees the inputs the chain actually delivers. The region comes
 * from the dialog's own model; the transport from the Composer's sheet-client registry, which the
 * viewer, preview and wizard never fill: a dialog not hosted by a Composer viewsheet pane gets no
 * context and so no Connect button.
 */
// The recording stub binds many inputs of the component it replaces; listing them in `inputs`
// is far shorter than one @Input() per property.
/* eslint-disable @angular-eslint/no-inputs-metadata-property */
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { Component, EventEmitter, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { render } from "@testing-library/angular";
import { of } from "rxjs";
import { SheetClientRegistry } from "../../../common/viewsheet-client/sheet-client-registry.service";
import { EditorContext } from "../../../composer/gui/wiz/editor-context";
import { ExpressionEditor } from "../../../widget/condition/expression-editor.component";
import { FormulaEditorDialog } from "../../../widget/formula-editor/formula-editor-dialog.component";
import { HelpUrlService } from "../../../widget/help-link/help-url.service";
import { RepositoryTreeService } from "../../../widget/repository-tree/repository-tree.service";
import { ModelService } from "../../../widget/services/model.service";
import { HyperlinkDialogModel } from "../../model/hyperlink-dialog-model";
import { VSTrapService } from "../../util/vs-trap.service";
import { HyperlinkDialog } from "./hyperlink-dialog.component";

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
   isHyperLink: boolean;
   @Output() onCommit = new EventEmitter<any>();
   @Output() onCancel = new EventEmitter<any>();
   constructor() { StubFormulaDialog.instances.push(this); }
}

const CLIENT = { runtimeId: "rt-1", commands: null } as any;

function hyperlinkModel(overrides: Partial<HyperlinkDialogModel>): HyperlinkDialogModel {
   return {
      linkType: 1, webLink: "=1 + 1", assetLinkPath: null, assetLinkId: null, bookmark: null,
      targetFrame: null, self: true, tooltip: null, disableParameterPrompt: false,
      sendViewsheetParameters: true, sendSelectionsAsParameters: false, paramList: [],
      row: 0, col: 0, fields: [], colName: null, table: false, grayedOutFields: [],
      ...overrides,
   } as HyperlinkDialogModel;
}

async function renderDialog(model: HyperlinkDialogModel,
                            registerAs: "VIEWSHEET" | "WORKSHEET" | null = "VIEWSHEET",
                            objectName = "Obj1")
{
   const registry = new SheetClientRegistry();

   if(registerAs) {
      registry.register(CLIENT, registerAs);
   }

   const result = await render(HyperlinkDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      imports: [HttpClientTestingModule],
      providers: [
         { provide: SheetClientRegistry, useValue: registry },
         { provide: VSTrapService, useValue: { checkTrap: vi.fn() } },
         { provide: RepositoryTreeService,
           useValue: { getRootFolder: vi.fn(), getFolder: vi.fn(() => of({ children: [] })),
                       getAliasedPath: vi.fn(), getCSSIcon: vi.fn() } },
         { provide: ModelService, useValue: { getModel: vi.fn(() => of({ children: [] })) } },
         { provide: HelpUrlService, useValue: { getHelpUrl: () => of("") } },
      ],
      configureTestBed: (bed) => {
         bed.overrideComponent(ExpressionEditor, {
            remove: { imports: [FormulaEditorDialog] },
            add: { imports: [StubFormulaDialog, FormsModule] },
         });
      },
      componentProperties: { runtimeId: "rt-1", objectName, model } as any,
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

describe("HyperlinkDialog - Web Link Expression pairing (bug #80459)", () => {
   it("a Text pairs as its one link", async () => {
      const dialog = await openFormulaDialog(
         await renderDialog(hyperlinkModel({}), "VIEWSHEET", "Text1"));

      expect(dialog.pairingContext).toEqual(
         { kind: "vsHyperlinkExpression", assembly: "Text1", name: "link" });
      expect(dialog.runtimeId).toBe("rt-1");
      expect(dialog.socketConnection).toBe(CLIENT);
   });

   it("a Table pairs as the clicked cell", async () => {
      const dialog = await openFormulaDialog(await renderDialog(
         hyperlinkModel({ table: true, row: 2, col: 1 }), "VIEWSHEET", "Table1"));

      expect(dialog.pairingContext).toEqual(
         { kind: "vsHyperlinkExpression", assembly: "Table1", name: "cell[2,1]" });
   });

   it("a Chart pairs as the data field, including one whose name has a space", async () => {
      const dialog = await openFormulaDialog(await renderDialog(
         hyperlinkModel({ colName: "Total Sales" }), "VIEWSHEET", "Chart1"));

      expect(dialog.pairingContext).toEqual(
         { kind: "vsHyperlinkExpression", assembly: "Chart1", name: "field[Total Sales]" });
   });

   it("a Chart title link pairs as title", async () => {
      const dialog = await openFormulaDialog(await renderDialog(
         hyperlinkModel({ titleLink: true }), "VIEWSHEET", "Chart1"));

      expect(dialog.pairingContext.name).toBe("title");
   });

   it("a Chart empty plot link pairs as emptyPlot", async () => {
      const dialog = await openFormulaDialog(await renderDialog(
         hyperlinkModel({ emptyPlotLink: true }), "VIEWSHEET", "Chart1"));

      expect(dialog.pairingContext.name).toBe("emptyPlot");
   });

   it("keeps the hyperlink editor's condition palette flags, and the context is still the hyperlink's",
      async () =>
   {
      const dialog = await openFormulaDialog(
         await renderDialog(hyperlinkModel({}), "VIEWSHEET", "Text1"));

      expect(dialog.isCondition).toBe(true);
      expect(dialog.isHyperLink).toBe(true);
      expect(dialog.pairingContext.kind).toBe("vsHyperlinkExpression");
   });

   it("a dialog hosted by no registered pane (viewer, preview, wizard) gets no context", async () => {
      const dialog = await openFormulaDialog(await renderDialog(hyperlinkModel({}), null, "Text1"));

      expect(dialog.pairingContext).toBeFalsy();
      expect(dialog.runtimeId).toBeFalsy();
      expect(dialog.socketConnection).toBeFalsy();
   });

   it("a worksheet client is never used for a hyperlink", async () => {
      const dialog = await openFormulaDialog(
         await renderDialog(hyperlinkModel({}), "WORKSHEET", "Text1"));

      expect(dialog.pairingContext).toBeFalsy();
   });

   it("a dialog that was not told its object gets no context", async () => {
      const dialog = await openFormulaDialog(
         await renderDialog(hyperlinkModel({}), "VIEWSHEET", ""));

      expect(dialog.pairingContext).toBeFalsy();
   });

   it("a table model with no cell gets no context rather than a half-named one", async () => {
      const dialog = await openFormulaDialog(await renderDialog(
         hyperlinkModel({ table: true, row: undefined, col: undefined }), "VIEWSHEET", "Table1"));

      expect(dialog.pairingContext).toBeFalsy();
   });
});
