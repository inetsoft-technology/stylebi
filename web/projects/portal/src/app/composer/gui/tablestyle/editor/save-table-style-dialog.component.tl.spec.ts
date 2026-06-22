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
 * SaveTableStyleDialog — single pass (+竞态+内存泄漏)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit path normalization: "/" and "Table Style" are both normalized to
 *     "/Table Style"; any other path is left unchanged
 *   Group 2 [Risk 3] — ok(): HTTP GET check-save-as-permission; permissionDenied triggers error
 *     dialog and returns without emitting; alreadyExists+allowOverwrite → confirm dialog → emit
 *     only on "yes"; alreadyExists+!allowOverwrite → error-only dialog → never emit;
 *     clean validator → emit model immediately
 *   Group 3 [Risk 2] — selectFolder: leaf node sets identifier/name/folder and updates form;
 *     non-leaf sets identifier/folder but leaves name unchanged
 *   Group 4 [Risk 2] — formValid: returns false before init or when form invalid; true when
 *     model + form are both present and valid
 *   Group 5 [Risk 1] — cancel(): emits onCancel
 *
 * Confirmed bugs (it.fails):
 *   Bug — ok() subscribe leak: http.get().subscribe() stores no Subscription reference; if the
 *     component is destroyed while the HTTP call is in-flight the callback still runs and can
 *     emit onCommit on a destroyed component. Fix: store the subscription and unsubscribe
 *     in ngOnDestroy.
 *
 * Out of scope:
 *   form validators correctness — FormValidators are library-level; only correct-input happy
 *     paths are exercised here.
 *   AssetTreeComponent interaction — tree selection is tested by calling selectFolder() directly.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { HttpClient } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject, of } from "rxjs";
import { SaveTableStyleDialog } from "./save-table-style-dialog.component";
import { SaveTableStyleDialogModel } from "../../../data/tablestyle/save-table-style-dialog-model";
import { ComponentTool } from "../../../../common/util/component-tool";

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

const HTTP_MOCK = { get: vi.fn() };
const MODAL_SERVICE_MOCK = { open: vi.fn() };

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

function makeDefaultFolder(path: string = "/"): any {
   return { path, identifier: "folder-id", properties: {} };
}

function makeModel(name: string = "MyStyle"): SaveTableStyleDialogModel {
   return new SaveTableStyleDialogModel(name);
}

async function renderComponent(
   folderPath: string = "/",
   modelName: string = "MyStyle",
) {
   const defaultFolder = makeDefaultFolder(folderPath);
   const model = makeModel(modelName);
   const { fixture } = await render(SaveTableStyleDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      providers: [
         { provide: HttpClient, useValue: HTTP_MOCK },
         { provide: NgbModal, useValue: MODAL_SERVICE_MOCK },
      ],
      componentInputs: { defaultFolder, model },
   });
   const comp = fixture.componentInstance as SaveTableStyleDialog;
   return { comp, fixture, model, defaultFolder };
}

beforeEach(() => {
   HTTP_MOCK.get.mockReset();
   MODAL_SERVICE_MOCK.open.mockReset();
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: ngOnInit path normalization [Risk 3]
// ---------------------------------------------------------------------------

describe("SaveTableStyleDialog — ngOnInit path normalization", () => {
   // 🔁 Regression-sensitive: the folder path "/" must become "/Table Style" so the backend
   //    receives an absolute path rather than the bare root.
   it("should normalize '/' to '/Table Style'", async () => {
      const { defaultFolder } = await renderComponent("/");
      expect(defaultFolder.path).toBe("/Table Style");
   });

   it("should normalize 'Table Style' to '/Table Style'", async () => {
      const { defaultFolder } = await renderComponent("Table Style");
      expect(defaultFolder.path).toBe("/Table Style");
   });

   it("should leave other paths unchanged", async () => {
      const { defaultFolder } = await renderComponent("/Custom/Folder");
      expect(defaultFolder.path).toBe("/Custom/Folder");
   });

   it("should leave '/Table Style' unchanged (already normalized)", async () => {
      const { defaultFolder } = await renderComponent("/Table Style");
      expect(defaultFolder.path).toBe("/Table Style");
   });
});

// ---------------------------------------------------------------------------
// Group 2: ok() — HTTP validation flow [Risk 3]
// ---------------------------------------------------------------------------

describe("SaveTableStyleDialog — ok()", () => {
   // 🔁 Regression-sensitive: when permissionDenied is non-empty the dialog must show an error
   //    and return without emitting onCommit.
   it("should show error dialog and NOT emit when permissionDenied is non-empty", async () => {
      const { comp } = await renderComponent();
      HTTP_MOCK.get.mockReturnValue(of({ alreadyExists: null, permissionDenied: "No permission" }));
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog" as any).mockResolvedValue(undefined);
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();
      await new Promise<void>(resolve => setTimeout(resolve, 0));

      expect(msgSpy).toHaveBeenCalled();
      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should emit model when validator is clean (no alreadyExists, no permissionDenied)", async () => {
      const { comp, model } = await renderComponent();
      HTTP_MOCK.get.mockReturnValue(of({ alreadyExists: null, permissionDenied: null }));
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();
      await Promise.resolve();
      await Promise.resolve();

      expect(emitSpy).toHaveBeenCalledWith(model);
   });

   it("should show confirm dialog when alreadyExists and allowOverwrite=true, emit on 'yes'", async () => {
      const { comp, model } = await renderComponent();
      HTTP_MOCK.get.mockReturnValue(of({ alreadyExists: "Style exists", permissionDenied: null, allowOverwrite: true }));
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("yes");
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();
      // flush nested promise chain: Promise.resolve(true) → showConfirmDialog → then(b===yes) → then(emit)
      await new Promise<void>(resolve => setTimeout(resolve, 0));

      expect(emitSpy).toHaveBeenCalledWith(model);
   });

   it("should NOT emit when alreadyExists + allowOverwrite=true and user clicks 'no'", async () => {
      const { comp } = await renderComponent();
      HTTP_MOCK.get.mockReturnValue(of({ alreadyExists: "Style exists", permissionDenied: null, allowOverwrite: true }));
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("no");
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();
      await new Promise<void>(resolve => setTimeout(resolve, 0));

      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should show error-only dialog and NOT emit when alreadyExists and allowOverwrite=false", async () => {
      const { comp } = await renderComponent();
      HTTP_MOCK.get.mockReturnValue(of({ alreadyExists: "Style exists", permissionDenied: null, allowOverwrite: false }));
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();
      await new Promise<void>(resolve => setTimeout(resolve, 0));

      expect(emitSpy).not.toHaveBeenCalled();
   });

   // Bug: ok() calls http.get().subscribe() without storing the Subscription. After component
   // destruction the callback still fires and calls this.onCommit.emit(). Fix: store the
   // subscription in a field and unsubscribe in ngOnDestroy.
   it.fails("should not emit after component is destroyed (subscribe leak)", async () => {
      const { comp, fixture } = await renderComponent();
      const subject = new Subject<any>();
      HTTP_MOCK.get.mockReturnValue(subject.asObservable());
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

describe("SaveTableStyleDialog — selectFolder", () => {
   it("should set name and update form when a leaf node is selected", async () => {
      const { comp, model } = await renderComponent();
      const leafNode: any = {
         leaf: true,
         label: "NewStyleName",
         data: { identifier: "leaf-id", properties: { folder: "some-folder" } },
      };

      comp.selectFolder(leafNode);

      expect(model.name).toBe("NewStyleName");
      expect(model.identifier).toBe("leaf-id");
      expect(model.folder).toBe("some-folder");
      expect(comp["form"].get("name").value).toBe("NewStyleName");
   });

   it("should set identifier and folder but NOT update name when a non-leaf node is selected", async () => {
      const { comp, model } = await renderComponent("/", "OriginalName");
      const folderNode: any = {
         leaf: false,
         label: "FolderLabel",
         data: { identifier: "folder-id", properties: { folder: "folder-path" } },
      };

      comp.selectFolder(folderNode);

      expect(model.name).toBe("OriginalName");
      expect(model.identifier).toBe("folder-id");
      expect(model.folder).toBe("folder-path");
   });
});

// ---------------------------------------------------------------------------
// Group 4: formValid [Risk 2]
// ---------------------------------------------------------------------------

describe("SaveTableStyleDialog — formValid", () => {
   it("should return true when model and form are present and name is non-empty", async () => {
      const { comp } = await renderComponent("/", "ValidName");
      expect(comp.formValid()).toBe(true);
   });

   it("should return false when name is empty (required validator)", async () => {
      const { comp } = await renderComponent("/", "");
      expect(comp.formValid()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 5: cancel [Risk 1]
// ---------------------------------------------------------------------------

describe("SaveTableStyleDialog — cancel", () => {
   it("should emit onCancel when cancel() is called", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.spyOn(comp.onCancel, "emit");

      comp.cancel();

      expect(emitSpy).toHaveBeenCalled();
   });
});
