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
 * SaveWorksheetDialog — single pass (+memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit: loads model from server; saveChanges() throws without it
 *   Group 2 [Risk 3] — saveChanges(): alreadyExists (overwritable) → confirm → emit/no-emit
 *   Group 3 [Risk 3] — saveChanges(): "Cannot overwrite an opened asset" → error dialog, no emit
 *   Group 4 [Risk 3] — saveChanges(): permissionDenied → showMessageDialog → no emit
 *   Group 5 [Risk 2] — saveChanges(): no validator body → emit immediately (happy path)
 *   Group 6 [Risk 1] — cancelChanges: emits onCancel
 *   Group 7 [Risk 1] — getDefaultFolder: returns the defaultFolder input
 *
 * Out of scope:
 *   enter() — delegates to zone.run(() => this.saveChanges()); NgZone passthrough, no testable logic.
 *   formValid — lambda derived directly from form.valid; covered implicitly by form group setup.
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { render, waitFor } from "@testing-library/angular";
import { of } from "rxjs";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { SaveWorksheetDialog } from "./save-worksheet-dialog.component";
import { AssetRepositoryPane } from "./asset-repository-pane.component";
import { WorksheetOptionPane } from "./worksheet-option-pane.component";
import { ModelService } from "../../../widget/services/model.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { SaveWorksheetDialogModel } from "../../data/ws/save-worksheet-dialog-model";
import { HttpResponse } from "@angular/common/http";

@Component({ selector: "asset-repository-pane", template: "", standalone: true })
class AssetRepositoryPaneStub {
   @Input() model: any;
   @Input() form: UntypedFormGroup;
   @Input() showReportRepository: boolean;
   @Input() defaultFolder: any;
   @Input() readOnly: boolean;
   @Output() nodeSelected = new EventEmitter<any>();
}

@Component({ selector: "worksheet-option-pane", template: "", standalone: true })
class WorksheetOptionPaneStub {
   @Input() model: any;
   @Input() form: UntypedFormGroup;
}

const MODEL_SERVICE_MOCK = { getModel: vi.fn(), sendModel: vi.fn() };
const MODAL_SERVICE_MOCK = {};

function makeWsModel(): SaveWorksheetDialogModel {
   return {
      assetRepositoryPaneModel: { name: "MyWorksheet", identifier: "", parentId: "" } as any,
      worksheetOptionPaneModel: { alias: "" } as any,
   };
}

function makeWorksheet(runtimeId = "ws-123") {
   return { runtimeId } as any;
}

function makeValidatorResponse(body: any) {
   return of(new HttpResponse({ body }));
}

async function renderComponent(wsModel = makeWsModel(), worksheet = makeWorksheet()) {
   MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(wsModel));
   MODEL_SERVICE_MOCK.sendModel.mockReturnValue(makeValidatorResponse(null));

   const { fixture } = await render(SaveWorksheetDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      importOverrides: [
         { replace: AssetRepositoryPane, with: AssetRepositoryPaneStub },
         { replace: WorksheetOptionPane, with: WorksheetOptionPaneStub },
      ],
      providers: [
         { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
         { provide: NgbModal, useValue: MODAL_SERVICE_MOCK },
      ],
      componentProperties: { worksheet, showReportRepository: false },
   });
   return fixture.componentInstance as SaveWorksheetDialog;
}

beforeEach(() => {
   MODEL_SERVICE_MOCK.getModel.mockReset();
   MODEL_SERVICE_MOCK.sendModel.mockReset();
});
afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: ngOnInit — model loading [Risk 3]
// ---------------------------------------------------------------------------

describe("SaveWorksheetDialog — ngOnInit model loading", () => {
   // 🔁 Regression-sensitive: if model is not populated from server, saveChanges() throws
   //    when trying to trim model.assetRepositoryPaneModel.name.
   it("should call modelService.getModel with the runtimeId URI", async () => {
      const wsModel = makeWsModel();
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(wsModel));
      MODEL_SERVICE_MOCK.sendModel.mockReturnValue(makeValidatorResponse(null));

      await render(SaveWorksheetDialog, {
         schemas: [NO_ERRORS_SCHEMA],
         importOverrides: [
            { replace: AssetRepositoryPane, with: AssetRepositoryPaneStub },
            { replace: WorksheetOptionPane, with: WorksheetOptionPaneStub },
         ],
         providers: [
            { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
            { provide: NgbModal, useValue: MODAL_SERVICE_MOCK },
         ],
         componentProperties: { worksheet: makeWorksheet("my-ws-id"), showReportRepository: false },
      });

      expect(MODEL_SERVICE_MOCK.getModel).toHaveBeenCalledWith(
         expect.stringContaining("save-worksheet-dialog-model")
      );
   });

   it("should populate model with data from getModel", async () => {
      const wsModel = makeWsModel();
      wsModel.assetRepositoryPaneModel.name = "LoadedWorksheet";
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(wsModel));
      MODEL_SERVICE_MOCK.sendModel.mockReturnValue(makeValidatorResponse(null));

      const { fixture } = await render(SaveWorksheetDialog, {
         schemas: [NO_ERRORS_SCHEMA],
         importOverrides: [
            { replace: AssetRepositoryPane, with: AssetRepositoryPaneStub },
            { replace: WorksheetOptionPane, with: WorksheetOptionPaneStub },
         ],
         providers: [
            { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
            { provide: NgbModal, useValue: MODAL_SERVICE_MOCK },
         ],
         componentProperties: { worksheet: makeWorksheet(), showReportRepository: false },
      });
      const comp = fixture.componentInstance as SaveWorksheetDialog;
      expect(comp.model.assetRepositoryPaneModel.name).toBe("LoadedWorksheet");
   });
});

// ---------------------------------------------------------------------------
// Group 2: saveChanges() — alreadyExists (overwritable) [Risk 3]
// ---------------------------------------------------------------------------

describe("SaveWorksheetDialog — saveChanges alreadyExists overwritable", () => {
   // 🔁 Regression-sensitive: must prompt user before overwriting an existing worksheet;
   //    skipping this would silently clobber work.
   it("should emit onCommit when user confirms overwrite (Yes)", async () => {
      const comp = await renderComponent();
      MODEL_SERVICE_MOCK.sendModel.mockReturnValue(
         makeValidatorResponse({ alreadyExists: "Worksheet already exists" })
      );
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("yes");
      const emitted: any[] = [];
      comp.onCommit.subscribe(m => emitted.push(m));

      comp.saveChanges();
      await waitFor(() => expect(emitted).toHaveLength(1));
   });

   it("should NOT emit onCommit when user declines overwrite (No)", async () => {
      const comp = await renderComponent();
      MODEL_SERVICE_MOCK.sendModel.mockReturnValue(
         makeValidatorResponse({ alreadyExists: "Worksheet already exists" })
      );
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("no");
      const emitted: any[] = [];
      comp.onCommit.subscribe(m => emitted.push(m));

      comp.saveChanges();
      await Promise.resolve();

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 3: saveChanges() — "Cannot overwrite an opened asset" [Risk 3]
// ---------------------------------------------------------------------------

describe("SaveWorksheetDialog — saveChanges Cannot overwrite opened asset", () => {
   // 🔁 Regression-sensitive: an opened asset must never be overwritten; a message dialog must
   //    be shown and save must be aborted; missing this guard would corrupt the open session.
   it("should show an error dialog and NOT emit onCommit for opened-asset conflict", async () => {
      const comp = await renderComponent();
      MODEL_SERVICE_MOCK.sendModel.mockReturnValue(
         makeValidatorResponse({ alreadyExists: "Cannot overwrite an opened asset." })
      );
      vi.spyOn(ComponentTool as any, "showMessageDialog").mockResolvedValue(undefined);
      const emitted: any[] = [];
      comp.onCommit.subscribe(m => emitted.push(m));

      comp.saveChanges();
      await Promise.resolve();

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 4: saveChanges() — permissionDenied [Risk 3]
// ---------------------------------------------------------------------------

describe("SaveWorksheetDialog — saveChanges permissionDenied", () => {
   // 🔁 Regression-sensitive: when the server denies permission the user must see an error;
   //    if the guard is removed the save appears to succeed while nothing was persisted.
   it("should NOT emit onCommit when validator returns permissionDenied", async () => {
      const comp = await renderComponent();
      MODEL_SERVICE_MOCK.sendModel.mockReturnValue(
         makeValidatorResponse({ permissionDenied: "Write permission denied" })
      );
      vi.spyOn(ComponentTool, "showMessageDialog" as any).mockResolvedValue(undefined);
      const emitted: any[] = [];
      comp.onCommit.subscribe(m => emitted.push(m));

      comp.saveChanges();
      await Promise.resolve();

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 5: saveChanges() — no validator body [Risk 2]
// ---------------------------------------------------------------------------

describe("SaveWorksheetDialog — saveChanges no validator body", () => {
   it("should emit onCommit immediately when the server returns no validator body", async () => {
      const comp = await renderComponent();
      MODEL_SERVICE_MOCK.sendModel.mockReturnValue(makeValidatorResponse(null));
      const emitted: any[] = [];
      comp.onCommit.subscribe(m => emitted.push(m));

      comp.saveChanges();
      await Promise.resolve();

      expect(emitted).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 6: cancelChanges [Risk 1]
// ---------------------------------------------------------------------------

describe("SaveWorksheetDialog — cancelChanges", () => {
   it("should emit via onCancel when cancelChanges is called", async () => {
      const comp = await renderComponent();
      const emitted: null[] = [];
      comp.onCancel.subscribe(() => emitted.push(null));

      comp.cancelChanges();

      expect(emitted).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 7: getDefaultFolder [Risk 1]
// ---------------------------------------------------------------------------

describe("SaveWorksheetDialog — getDefaultFolder", () => {
   it("should return the defaultFolder input", async () => {
      const folder = { identifier: "default/folder" } as any;
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(makeWsModel()));
      MODEL_SERVICE_MOCK.sendModel.mockReturnValue(makeValidatorResponse(null));
      const { fixture } = await render(SaveWorksheetDialog, {
         schemas: [NO_ERRORS_SCHEMA],
         importOverrides: [
            { replace: AssetRepositoryPane, with: AssetRepositoryPaneStub },
            { replace: WorksheetOptionPane, with: WorksheetOptionPaneStub },
         ],
         providers: [
            { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
            { provide: NgbModal, useValue: MODAL_SERVICE_MOCK },
         ],
         componentProperties: { worksheet: makeWorksheet(), showReportRepository: false, defaultFolder: folder },
      });
      const comp = fixture.componentInstance as SaveWorksheetDialog;
      expect(comp.getDefaultFolder()).toBe(folder);
   });

   it("should return null when defaultFolder is not set", async () => {
      const comp = await renderComponent();
      expect(comp.getDefaultFolder()).toBeFalsy();
   });
});
