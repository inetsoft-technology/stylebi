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
 * SaveScriptDialog — single pass (+竞态+内存泄漏)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit path normalization: "/" and "Script Function" are both normalized
 *     to "/Script Function"; other paths are left unchanged
 *   Group 2 [Risk 3] — ok(): HTTP POST to validation endpoint; permissionDenied triggers error
 *     dialog and returns without emitting; alreadyExists+allowOverwrite → confirm → emit only
 *     on "yes"; alreadyExists+!allowOverwrite → error dialog → never emit; clean validator →
 *     emit model immediately
 *   Group 3 [Risk 2] — selectFolder: leaf node sets identifier and updates name/form; non-leaf
 *     sets only identifier (no name change)
 *   Group 4 [Risk 2] — formValid: returns false before init or when form invalid; true when
 *     model.name + form are present and valid
 *   Group 5 [Risk 1] — cancelChanges(): emits onCancel
 *
 * Confirmed bugs (it.fails):
 *   Bug — ok() subscribe leak: http.post().subscribe() stores no Subscription reference; if the
 *     component is destroyed while the HTTP call is in-flight the callback still runs and can
 *     emit onCommit on a destroyed component. Fix: store the subscription and unsubscribe
 *     in ngOnDestroy.
 *
 * Out of scope:
 *   Form validator correctness — FormValidators.invalidJSFunctionName is library-level.
 *   AssetTreeComponent interaction — tree selection is tested by calling selectFolder() directly.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { HttpClient } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject, of } from "rxjs";
import { SaveScriptDialog } from "./save-script-dialog.component";
import { SaveScriptDialogModel } from "../../data/script/save-script-dialog-model";
import { ModelService } from "../../../widget/services/model.service";
import { ComponentTool } from "../../../common/util/component-tool";

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

const HTTP_MOCK = { post: vi.fn() };
const MODAL_SERVICE_MOCK = { open: vi.fn() };
const MODEL_SERVICE_MOCK = { getModel: vi.fn() };

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

function makeDefaultFolder(path: string = "/"): any {
   return { path, identifier: "folder-id", properties: {} };
}

function makeModel(name: string = "myScript"): SaveScriptDialogModel {
   return new SaveScriptDialogModel(name);
}

async function renderComponent(
   folderPath: string = "/",
   modelName: string = "myScript",
) {
   const defaultFolder = makeDefaultFolder(folderPath);
   const model = makeModel(modelName);
   const { fixture } = await render(SaveScriptDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      providers: [
         { provide: HttpClient, useValue: HTTP_MOCK },
         { provide: NgbModal, useValue: MODAL_SERVICE_MOCK },
         { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
      ],
      componentInputs: { defaultFolder, model },
   });
   const comp = fixture.componentInstance as SaveScriptDialog;
   return { comp, fixture, model, defaultFolder };
}

beforeEach(() => {
   HTTP_MOCK.post.mockReset();
   MODAL_SERVICE_MOCK.open.mockReset();
   MODEL_SERVICE_MOCK.getModel.mockReset();
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: ngOnInit path normalization [Risk 3]
// ---------------------------------------------------------------------------

describe("SaveScriptDialog — ngOnInit path normalization", () => {
   // 🔁 Regression-sensitive: the bare root "/" must become "/Script Function" so the backend
   //    receives an absolute path.
   it("should normalize '/' to '/Script Function'", async () => {
      const { defaultFolder } = await renderComponent("/");
      expect(defaultFolder.path).toBe("/Script Function");
   });

   it("should normalize 'Script Function' to '/Script Function'", async () => {
      const { defaultFolder } = await renderComponent("Script Function");
      expect(defaultFolder.path).toBe("/Script Function");
   });

   it("should leave other paths unchanged", async () => {
      const { defaultFolder } = await renderComponent("/Custom/Scripts");
      expect(defaultFolder.path).toBe("/Custom/Scripts");
   });

   it("should leave '/Script Function' unchanged (already normalized)", async () => {
      const { defaultFolder } = await renderComponent("/Script Function");
      expect(defaultFolder.path).toBe("/Script Function");
   });
});

// ---------------------------------------------------------------------------
// Group 2: ok() — HTTP validation flow [Risk 3]
// ---------------------------------------------------------------------------

describe("SaveScriptDialog — ok()", () => {
   // 🔁 Regression-sensitive: when permissionDenied is set the dialog must show an error and
   //    return without emitting onCommit.
   it("should show error dialog and NOT emit when permissionDenied is set", async () => {
      const { comp } = await renderComponent();
      HTTP_MOCK.post.mockReturnValue(of({ alreadyExists: null, permissionDenied: "No permission" }));
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog" as any).mockResolvedValue(undefined);
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();
      await Promise.resolve();

      expect(msgSpy).toHaveBeenCalled();
      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should emit model when validator is clean (no alreadyExists, no permissionDenied)", async () => {
      const { comp, model } = await renderComponent();
      HTTP_MOCK.post.mockReturnValue(of({ alreadyExists: null, permissionDenied: null }));
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();
      await Promise.resolve();
      await Promise.resolve();

      expect(emitSpy).toHaveBeenCalledWith(model);
   });

   it("should show confirm dialog when alreadyExists and allowOverwrite=true, emit on 'yes'", async () => {
      const { comp, model } = await renderComponent();
      HTTP_MOCK.post.mockReturnValue(of({ alreadyExists: "Already exists", permissionDenied: null, allowOverwrite: true }));
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("yes");
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();
      await new Promise<void>(resolve => setTimeout(resolve, 0));

      expect(emitSpy).toHaveBeenCalledWith(model);
   });

   it("should NOT emit when alreadyExists + allowOverwrite=true and user clicks 'no'", async () => {
      const { comp } = await renderComponent();
      HTTP_MOCK.post.mockReturnValue(of({ alreadyExists: "Already exists", permissionDenied: null, allowOverwrite: true }));
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("no");
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();
      await new Promise<void>(resolve => setTimeout(resolve, 0));

      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should show error-only dialog and NOT emit when alreadyExists and allowOverwrite=false", async () => {
      const { comp } = await renderComponent();
      HTTP_MOCK.post.mockReturnValue(of({ alreadyExists: "Already exists", permissionDenied: null, allowOverwrite: false }));
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();
      await new Promise<void>(resolve => setTimeout(resolve, 0));

      expect(emitSpy).not.toHaveBeenCalled();
   });

   // Bug: ok() calls http.post().subscribe() without storing the Subscription. After component
   // destruction the callback still fires and calls this.onCommit.emit(). Fix: store the
   // subscription in a field and unsubscribe in ngOnDestroy.
   it.fails("should not emit after component is destroyed (subscribe leak)", async () => {
      const { comp, fixture } = await renderComponent();
      const subject = new Subject<any>();
      HTTP_MOCK.post.mockReturnValue(subject.asObservable());
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();
      fixture.destroy();

      subject.next({ alreadyExists: null, permissionDenied: null });
      await Promise.resolve();

      // With fix: emit should NOT have been called after destroy
      // Currently: FAILS — callback still runs
      expect(emitSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 3: selectFolder [Risk 2]
// ---------------------------------------------------------------------------

describe("SaveScriptDialog — selectFolder", () => {
   it("should set name and update form when a leaf node is selected", async () => {
      const { comp, model } = await renderComponent();
      const leafNode: any = {
         leaf: true,
         label: "newScriptName",
         data: { identifier: "leaf-id" },
      };

      comp.selectFolder(leafNode);

      expect(model.name).toBe("newScriptName");
      expect(model.identifier).toBe("leaf-id");
      expect(comp["form"].get("name").value).toBe("newScriptName");
   });

   it("should set only identifier when a non-leaf node is selected", async () => {
      const { comp, model } = await renderComponent("/", "originalName");
      const folderNode: any = {
         leaf: false,
         label: "FolderLabel",
         data: { identifier: "folder-id" },
      };

      comp.selectFolder(folderNode);

      expect(model.name).toBe("originalName");
      expect(model.identifier).toBe("folder-id");
   });
});

// ---------------------------------------------------------------------------
// Group 4: formValid [Risk 2]
// ---------------------------------------------------------------------------

describe("SaveScriptDialog — formValid", () => {
   it("should return true when model.name and form are present and valid", async () => {
      const { comp } = await renderComponent("/", "validScript");
      expect(comp.formValid()).toBe(true);
   });

   it("should return falsy when model.name is empty", async () => {
      const { comp } = await renderComponent("/", "");
      expect(comp.formValid()).toBeFalsy();
   });
});

// ---------------------------------------------------------------------------
// Group 5: cancelChanges [Risk 1]
// ---------------------------------------------------------------------------

describe("SaveScriptDialog — cancelChanges", () => {
   it("should emit onCancel when cancelChanges() is called", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.spyOn(comp.onCancel, "emit");

      comp.cancelChanges();

      expect(emitSpy).toHaveBeenCalled();
   });
});
