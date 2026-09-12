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
 * SaveViewsheetDialog — single pass (+memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — name validation: banned characters, must-start-with-char-digit, required
 *   Group 2 [Risk 3] — ok(): no conflict → emits onCommit immediately (happy path)
 *   Group 3 [Risk 3] — ok(): alreadyExists + allowOverwrite=true → confirm dialog → emit/no-emit
 *   Group 4 [Risk 3] — ok(): alreadyExists + allowOverwrite=false → confirm (optionsOnlyOk) → no emit
 *   Group 5 [Risk 3] — ok(): permissionDenied → showMessageDialog → no emit
 *   Group 6 [Risk 2] — ngOnInit: clears name when it starts with "Untitled-"
 *   Group 7 [Risk 2] — selectFolder: leaf → sets parentId+name; non-leaf → parentId only
 *   Group 8 [Risk 1] — cancelChanges: emits "cancel" via onCancel
 *
 * Old spec ported (Risk 3):
 *   Bug #20421: banned chars (%) trigger "composer.sheet.checkSpeChar" error
 *
 * Out of scope:
 *   initForm() — called by ngOnInit; form state is verified through form control assertions in Group 1.
 *   selectNodeOnLoadFn() — always returns [root.children[0]]; display helper, zero branch logic.
 *   enter() — delegates to zone.run(() => this.ok()); NgZone passthrough, no testable logic.
 *   formValid — lambda derived directly from form.valid; covered implicitly by form control tests.
 */

import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { of } from "rxjs";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { SaveViewsheetDialog } from "./save-viewsheet-dialog.component";
import { AssetTreeComponent } from "../../../widget/asset-tree/asset-tree.component";
import { ViewsheetOptionsPane } from "./viewsheet-options-pane.component";
import { ModalHeaderComponent } from "../../../widget/modal-header/modal-header.component";
import { ModelService } from "../../../widget/services/model.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { SaveViewsheetDialogModel } from "../../data/vs/save-viewsheet-dialog-model";
import { SaveViewsheetDialogModelValidator } from "../../data/vs/save-viewsheet-dialog-model-validator";
import { HttpResponse } from "@angular/common/http";

@Component({ selector: "asset-tree", template: "", standalone: true })
class AssetTreeComponentStub {}

@Component({ selector: "viewsheet-options-pane", template: "", standalone: true })
class ViewsheetOptionsPaneStub {}

@Component({ selector: "modal-header", template: "", standalone: true })
class ModalHeaderComponentStub {}

const MODEL_SERVICE_MOCK = { sendModel: vi.fn(), getModel: vi.fn() };
const MODAL_SERVICE_MOCK = {};

function makeModel(name = "MyViewsheet"): SaveViewsheetDialogModel {
   return { name, parentId: "", updateDepend: true, viewsheetOptionsPaneModel: null as any };
}

function makeValidatorResponse(validator: SaveViewsheetDialogModelValidator) {
   return of(new HttpResponse({ body: validator }));
}

async function renderComponent(model = makeModel()) {
   const { fixture } = await render(SaveViewsheetDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      importOverrides: [
         { replace: AssetTreeComponent, with: AssetTreeComponentStub },
         { replace: ViewsheetOptionsPane, with: ViewsheetOptionsPaneStub },
         { replace: ModalHeaderComponent, with: ModalHeaderComponentStub },
      ],
      providers: [
         { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
         { provide: NgbModal, useValue: MODAL_SERVICE_MOCK },
      ],
      componentProperties: { model, runtimeId: "vs-123" },
   });
   return fixture.componentInstance as SaveViewsheetDialog;
}

beforeEach(() => {
   MODEL_SERVICE_MOCK.sendModel.mockReset();
   MODEL_SERVICE_MOCK.getModel.mockReset();
});
afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: name validation [Risk 3]
// ---------------------------------------------------------------------------

describe("SaveViewsheetDialog — name validation", () => {
   // 🔁 Regression (Bug #20421): banned chars in viewsheet names cause server-side failures;
   //    must be rejected client-side via the assetEntryBannedCharacters validator, which is what
   //    triggers the "composer.sheet.checkSpeChar" error span in the template.
   it("should set assetEntryBannedCharacters error when name contains banned characters (%^&*)", async () => {
      const comp = await renderComponent(makeModel(""));
      comp.form.controls["name"].setValue("autosize%^&&**((");
      expect(comp.form.controls["name"].errors?.["assetEntryBannedCharacters"]).toBeTruthy();
   });

   it("should mark name as invalid when it is empty (required)", async () => {
      const comp = await renderComponent(makeModel(""));
      comp.form.controls["name"].setValue("");
      expect(comp.form.controls["name"].valid).toBe(false);
   });

   it("should mark name as valid when it contains only alphanumeric characters", async () => {
      const comp = await renderComponent(makeModel(""));
      comp.form.controls["name"].setValue("MyDashboard");
      expect(comp.form.controls["name"].valid).toBe(true);
   });

   it("should mark name as valid when it contains underscores and hyphens", async () => {
      const comp = await renderComponent(makeModel(""));
      comp.form.controls["name"].setValue("my-viewsheet_1");
      expect(comp.form.controls["name"].valid).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2: ok() — no conflict → emit immediately [Risk 3]
// ---------------------------------------------------------------------------

describe("SaveViewsheetDialog — ok() no conflict", () => {
   // 🔁 Regression-sensitive: the basic save path; if the Promise chain is broken,
   //    the viewsheet silently fails to save even when the server reports no issues.
   it("should emit onCommit immediately when the server reports no conflict", async () => {
      const comp = await renderComponent();
      MODEL_SERVICE_MOCK.sendModel.mockReturnValue(
         makeValidatorResponse({})
      );
      const emitted: SaveViewsheetDialogModel[] = [];
      comp.onCommit.subscribe(m => emitted.push(m));

      comp.ok();
      await new Promise(r => setTimeout(r, 0));

      expect(emitted).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 3: ok() — alreadyExists + allowOverwrite=true [Risk 3]
// ---------------------------------------------------------------------------

describe("SaveViewsheetDialog — ok() alreadyExists allowOverwrite=true", () => {
   // 🔁 Regression-sensitive: user must confirm before overwriting an existing viewsheet;
   //    skipping the confirm dialog would silently destroy another user's work.
   it("should emit onCommit when user confirms overwrite (clicks Yes)", async () => {
      const comp = await renderComponent();
      MODEL_SERVICE_MOCK.sendModel.mockReturnValue(
         makeValidatorResponse({ alreadyExists: "Viewsheet already exists", allowOverwrite: true })
      );
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("yes");
      const emitted: SaveViewsheetDialogModel[] = [];
      comp.onCommit.subscribe(m => emitted.push(m));

      comp.ok();
      await new Promise(r => setTimeout(r, 0));

      expect(ComponentTool.showConfirmDialog).toHaveBeenCalled();
      expect(emitted).toHaveLength(1);
   });

   it("should NOT emit onCommit when user declines overwrite (clicks No)", async () => {
      const comp = await renderComponent();
      MODEL_SERVICE_MOCK.sendModel.mockReturnValue(
         makeValidatorResponse({ alreadyExists: "Viewsheet already exists", allowOverwrite: true })
      );
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("no");
      const emitted: SaveViewsheetDialogModel[] = [];
      comp.onCommit.subscribe(m => emitted.push(m));

      comp.ok();
      await new Promise(r => setTimeout(r, 0));

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 4: ok() — alreadyExists + allowOverwrite=false [Risk 3]
// ---------------------------------------------------------------------------

describe("SaveViewsheetDialog — ok() alreadyExists allowOverwrite=false", () => {
   // 🔁 Regression-sensitive: when overwrite is not allowed the code calls showConfirmDialog
   //    with optionsOnlyOk, whose .then() always returns false → onCommit must not fire.
   it("should NOT emit onCommit when overwrite is disallowed (allowOverwrite=false)", async () => {
      const comp = await renderComponent();
      MODEL_SERVICE_MOCK.sendModel.mockReturnValue(
         makeValidatorResponse({ alreadyExists: "Duplicate", allowOverwrite: false })
      );
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const emitted: SaveViewsheetDialogModel[] = [];
      comp.onCommit.subscribe(m => emitted.push(m));

      comp.ok();
      await new Promise(r => setTimeout(r, 0));

      expect(ComponentTool.showConfirmDialog).toHaveBeenCalled();
      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 5: ok() — permissionDenied [Risk 3]
// ---------------------------------------------------------------------------

describe("SaveViewsheetDialog — ok() permissionDenied", () => {
   // 🔁 Regression-sensitive: when the server denies permission the user must see an error;
   //    if the guard is removed the save appears to succeed while nothing was persisted.
   it("should NOT emit onCommit when validator returns permissionDenied", async () => {
      const comp = await renderComponent();
      MODEL_SERVICE_MOCK.sendModel.mockReturnValue(
         makeValidatorResponse({ permissionDenied: "Write permission denied" })
      );
      vi.spyOn(ComponentTool, "showMessageDialog" as any).mockResolvedValue(undefined);
      const emitted: SaveViewsheetDialogModel[] = [];
      comp.onCommit.subscribe(m => emitted.push(m));

      comp.ok();
      await new Promise(r => setTimeout(r, 0));

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 6: ngOnInit — "Untitled-" name cleared [Risk 2]
// ---------------------------------------------------------------------------

describe("SaveViewsheetDialog — ngOnInit Untitled- clearing", () => {
   // 🔁 Regression-sensitive: auto-generated names like "Untitled-3" must be cleared so the
   //    user is prompted for a real name; regression leaves a meaningless auto-generated name.
   it("should clear model.name when it starts with 'Untitled-'", async () => {
      const model = makeModel("Untitled-3");
      const comp = await renderComponent(model);
      expect(comp.model.name).toBe("");
   });

   it("should NOT clear model.name when it does not start with 'Untitled-'", async () => {
      const model = makeModel("Sales Dashboard");
      const comp = await renderComponent(model);
      expect(comp.model.name).toBe("Sales Dashboard");
   });
});

// ---------------------------------------------------------------------------
// Group 7: selectFolder [Risk 2]
// ---------------------------------------------------------------------------

describe("SaveViewsheetDialog — selectFolder", () => {
   // 🔁 Regression-sensitive: when user clicks a leaf node (an existing viewsheet), the dialog
   //    pre-fills the name and parent; broken logic would save to the wrong folder.
   it("should set model.parentId to the folder path and model.name to node.label for a leaf node", async () => {
      const comp = await renderComponent();
      const node = {
         leaf: true,
         label: "MyReport",
         data: { identifier: "folder/subfolder/MyReport" },
         children: [],
      } as any;

      comp.selectFolder(node);

      expect(comp.model.parentId).toBe("folder/subfolder");
      expect(comp.model.name).toBe("MyReport");
   });

   it("should set model.parentId to node identifier for a non-leaf (folder) node", async () => {
      const comp = await renderComponent();
      const node = {
         leaf: false,
         label: "MyFolder",
         data: { identifier: "folder/MyFolder" },
         children: [],
      } as any;

      comp.selectFolder(node);

      expect(comp.model.parentId).toBe("folder/MyFolder");
      expect(comp.model.name).toBe("MyViewsheet");
   });
});

// ---------------------------------------------------------------------------
// Group 8: cancelChanges [Risk 1]
// ---------------------------------------------------------------------------

describe("SaveViewsheetDialog — cancelChanges", () => {
   it("should emit 'cancel' via onCancel when cancelChanges is called", async () => {
      const comp = await renderComponent();
      const emitted: string[] = [];
      comp.onCancel.subscribe(v => emitted.push(v));

      comp.cancelChanges();

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe("cancel");
   });
});
