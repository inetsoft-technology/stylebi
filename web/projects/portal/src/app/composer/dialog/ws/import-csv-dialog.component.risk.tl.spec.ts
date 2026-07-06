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
 * ImportCSVDialog — Pass 2: Risk
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — ok(): sets pending=true; assigns form values to model; sends event via socketConnection
 *   Group 2  [Risk 3] — ok() with tableName input: uses tableName input instead of fileName
 *   Group 3  [Risk 3] — ok() newTableName sanitization: strips extension; replaces invalid chars with '_'
 *   Group 4  [Risk 3] — cancel(): emits onCancel
 *   Group 5  [Risk 3] — initFileToucher: sets an interval that calls http.put at the touch URI
 *   Group 6  [Risk 2] — ngOnDestroy: unsubscribes previewSub; clears fileToucherID
 *   Group 7  [Risk 2] — parsePreviewResponse with warnMsg: shows confirm dialog; sets errorOnServer=true
 *   Group 8  [Risk 2] — parsePreviewResponse clean: sets previewTable; calls validateFirstRow
 *   Group 9  [Risk 1] — initForm structure: creates all expected controls; marks all as touched
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { EMPTY, of } from "rxjs";

import { ImportCSVDialog } from "./import-csv-dialog.component";
import { ModelService } from "../../../widget/services/model.service";
import { FileUploadService } from "../../../common/services/file-upload.service";
import { HttpClient } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../../common/util/component-tool";
import { ImportCSVDialogModel } from "../../data/ws/import-csv-dialog-model";
import { BaseTableCellModel } from "../../../vsobjects/model/base-table-cell-model";

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

const MODEL_SERVICE_MOCK = { getModel: vi.fn(), sendModel: vi.fn() };
const FILE_UPLOAD_SERVICE_MOCK = { upload: vi.fn(), getObserver: vi.fn() };
const HTTP_CLIENT_MOCK = { put: vi.fn(), get: vi.fn() };
const MODAL_SERVICE_MOCK = { open: vi.fn() };

// ---------------------------------------------------------------------------
// Factories
// ---------------------------------------------------------------------------

function makeModel(overrides: Partial<ImportCSVDialogModel> = {}): ImportCSVDialogModel {
   return {
      fileName: "data.csv",
      fileType: "DELIMITED",
      encodingSelected: "UTF-8",
      encodingList: ["UTF-8", "GBK", "Unicode"],
      sheetSelected: null,
      sheetsList: [],
      delimiter: ",",
      delimiterTab: false,
      detectType: true,
      unpivotCB: false,
      headerCols: 1,
      firstRowCB: false,
      removeQuotesCB: false,
      headerNames: null,
      ...overrides,
   };
}

function makeWorksheet() {
   return {
      runtimeId: "ws-rt-1",
      socketConnection: {
         sendEvent: vi.fn(),
      },
   };
}

function makeCell(cellData: string): BaseTableCellModel {
   return { cellData } as any;
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(
   worksheetOverride?: any,
   modelOverride?: Partial<ImportCSVDialogModel>,
   extraInputs?: any
) {
   FILE_UPLOAD_SERVICE_MOCK.getObserver.mockReturnValue(EMPTY);
   HTTP_CLIENT_MOCK.put.mockReturnValue(of(null));
   HTTP_CLIENT_MOCK.get.mockReturnValue(of(null));
   MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(makeModel(modelOverride)));

   const worksheet = worksheetOverride ?? makeWorksheet();

   const { fixture } = await render(ImportCSVDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
         { provide: FileUploadService, useValue: FILE_UPLOAD_SERVICE_MOCK },
         { provide: HttpClient, useValue: HTTP_CLIENT_MOCK },
         { provide: NgbModal, useValue: MODAL_SERVICE_MOCK },
      ],
      componentInputs: { worksheet, ...extraInputs },
   });

   const comp = fixture.componentInstance as ImportCSVDialog;
   comp.fileUploaded = true;
   return { fixture, comp, worksheet };
}

// ---------------------------------------------------------------------------
// Resets
// ---------------------------------------------------------------------------

beforeEach(() => {
   MODEL_SERVICE_MOCK.getModel.mockReset();
   MODEL_SERVICE_MOCK.sendModel.mockReset();
   FILE_UPLOAD_SERVICE_MOCK.upload.mockReset();
   FILE_UPLOAD_SERVICE_MOCK.getObserver.mockReset();
   HTTP_CLIENT_MOCK.put.mockReset();
   HTTP_CLIENT_MOCK.get.mockReset();
   MODAL_SERVICE_MOCK.open.mockReset();
});

afterEach(() => {
   vi.useRealTimers();
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1: ok() [Risk 3]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — ok()", () => {
   // 🔁 Regression-sensitive: ok() must send the event via socketConnection; if it doesn't the
   //    worksheet never receives the import command and the dialog appears to do nothing.
   it("should call worksheet.socketConnection.sendEvent with SUBMIT_URI and model", async () => {
      const { comp, worksheet } = await renderComponent();

      comp.ok();

      expect(worksheet.socketConnection.sendEvent).toHaveBeenCalledWith(
         "/events/ws/dialog/import-csv-dialog-model",
         expect.any(Object)
      );
   });

   it("should emit onCommit with the model", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onCommit.subscribe(spy);

      comp.ok();

      expect(spy).toHaveBeenCalled();
   });

   it("should set pending=true when called, preventing double-submit", async () => {
      const { comp } = await renderComponent();

      comp.ok();

      expect(comp.pending).toBe(true);
   });

   it("should NOT send the event a second time when called while pending", async () => {
      const { comp, worksheet } = await renderComponent();
      comp.pending = true;

      comp.ok();

      expect(worksheet.socketConnection.sendEvent).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2: ok() with tableName [Risk 3]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — ok() with tableName input", () => {
   // 🔁 Regression-sensitive: when a tableName input is supplied the model.tableName must be set
   //    to it; using the file name instead would rename an existing table.
   it("should use tableName input as model.tableName when tableName is provided", async () => {
      const { comp, worksheet } = await renderComponent(undefined, undefined, { tableName: "SalesTable" });

      comp.ok();

      const sentModel: ImportCSVDialogModel = worksheet.socketConnection.sendEvent.mock.calls[0][1];
      expect(sentModel.tableName).toBe("SalesTable");
   });

   it("should NOT set model.newTableName when tableName input is provided", async () => {
      const { comp, worksheet } = await renderComponent(undefined, undefined, { tableName: "SalesTable" });

      comp.ok();

      const sentModel: ImportCSVDialogModel = worksheet.socketConnection.sendEvent.mock.calls[0][1];
      expect(sentModel.newTableName).toBeUndefined();
   });
});

// ---------------------------------------------------------------------------
// Group 3: ok() newTableName sanitization [Risk 3]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — ok() newTableName sanitization", () => {
   // 🔁 Regression-sensitive: newTableName must be stripped of its extension and have special
   //    characters replaced; invalid chars in the table name would fail the server-side query.
   it("should strip file extension from newTableName", async () => {
      const { comp, worksheet } = await renderComponent(undefined, { fileName: "report.csv" });

      comp.ok();

      const sentModel: ImportCSVDialogModel = worksheet.socketConnection.sendEvent.mock.calls[0][1];
      expect(sentModel.newTableName).not.toContain(".csv");
   });

   it("should replace special characters in newTableName with underscore", async () => {
      const { comp, worksheet } = await renderComponent(undefined, { fileName: "my report!.csv" });

      comp.ok();

      const sentModel: ImportCSVDialogModel = worksheet.socketConnection.sendEvent.mock.calls[0][1];
      expect(sentModel.newTableName).toMatch(/^[a-zA-Z0-9 $#_%\-_]+$/);
   });
});

// ---------------------------------------------------------------------------
// Group 4: cancel() [Risk 3]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — cancel()", () => {
   // 🔁 Regression-sensitive: cancel() must emit onCancel; without it the dialog stays open.
   it("should emit onCancel", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onCancel.subscribe(spy);

      comp.cancel();

      expect(spy).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 5: initFileToucher [Risk 3]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — initFileToucher", () => {
   // 🔁 Regression-sensitive: the file toucher keeps the server-side temp file alive; if the
   //    interval is not started the server will delete the file mid-import.
   it("should call http.put after 60 seconds", async () => {
      vi.useFakeTimers();
      const { comp } = await renderComponent();

      vi.advanceTimersByTime(60000);

      expect(HTTP_CLIENT_MOCK.put).toHaveBeenCalledWith(
         expect.stringContaining("touch-file"),
         null
      );
   });

   it("should call http.put twice after 120 seconds", async () => {
      vi.useFakeTimers();
      const { comp } = await renderComponent();

      vi.advanceTimersByTime(120000);

      expect(HTTP_CLIENT_MOCK.put).toHaveBeenCalledTimes(2);
   });
});

// ---------------------------------------------------------------------------
// Group 6: ngOnDestroy [Risk 2]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — ngOnDestroy", () => {
   it("should unsubscribe previewSub on destroy", async () => {
      const { comp, fixture } = await renderComponent();
      const mockSub = { closed: false, unsubscribe: vi.fn() };
      (comp as any).previewSub = mockSub;

      comp.ngOnDestroy();

      expect(mockSub.unsubscribe).toHaveBeenCalledTimes(1);
   });

   it("should clear the fileToucherID interval on destroy", async () => {
      vi.useFakeTimers();
      const clearIntervalSpy = vi.spyOn(global, "clearTimeout");
      const { comp } = await renderComponent();

      comp.ngOnDestroy();

      expect(clearIntervalSpy).toHaveBeenCalled();
   });

   it("should NOT throw when previewSub is already closed", async () => {
      const { comp } = await renderComponent();
      (comp as any).previewSub = { closed: true, unsubscribe: vi.fn() };

      expect(() => comp.ngOnDestroy()).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 7: parsePreviewResponse with warnMsg [Risk 2]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — parsePreviewResponse with warnMsg", () => {
   // 🔁 Regression-sensitive: warnMsg must set errorOnServer=true to disable the OK button
   //    until the user resolves the conflict; skipping this leaves the user confused.
   it("should set errorOnServer=true when response has warnMsg", async () => {
      const { comp } = await renderComponent();
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");

      (comp as any).parsePreviewResponse({
         body: { warnMsg: "Mixed type detected", mixedIndexes: [0] },
      });

      expect(comp.errorOnServer).toBe(true);
   });

   it("should call ComponentTool.showConfirmDialog when response has warnMsg", async () => {
      const { comp } = await renderComponent();
      const spy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");

      (comp as any).parsePreviewResponse({
         body: { warnMsg: "Mixed type detected", mixedIndexes: [0] },
      });

      expect(spy).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 8: parsePreviewResponse clean [Risk 2]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — parsePreviewResponse clean (no warnMsg)", () => {
   it("should set previewTable from response body when no warnMsg", async () => {
      const { comp } = await renderComponent();
      const previewTable = [[makeCell("A"), makeCell("B")]];

      (comp as any).parsePreviewResponse({
         body: {
            previewTable,
            validator: { message: null },
            limitMessage: null,
         },
      });

      expect(comp.previewTable).toBe(previewTable);
   });

   it("should set errorOnServer=false when validator.message is null", async () => {
      const { comp } = await renderComponent();
      comp.errorOnServer = true;

      (comp as any).parsePreviewResponse({
         body: {
            previewTable: [[makeCell("A")]],
            validator: { message: null },
            limitMessage: null,
         },
      });

      expect(comp.errorOnServer).toBe(false);
   });

   it("should set errorOnServer=true when validator.message is set", async () => {
      const { comp } = await renderComponent();
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");

      (comp as any).parsePreviewResponse({
         body: {
            previewTable: [[makeCell("A")]],
            validator: { message: "Invalid header format" },
            limitMessage: null,
         },
      });

      expect(comp.errorOnServer).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 9: initForm structure [Risk 1]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — initForm structure", () => {
   it("should create a form with all expected controls", async () => {
      const { comp } = await renderComponent();

      const expectedControls = [
         "encodingSelected", "sheetSelected", "delimiter", "delimiterTab",
         "detectType", "unpivotCB", "headerCols", "firstRowCB", "removeQuotesCB",
      ];

      for(const name of expectedControls) {
         expect(comp.form.get(name)).not.toBeNull();
      }
   });

   it("should mark all controls as touched after initForm", async () => {
      const { comp } = await renderComponent();

      for(const controlName in comp.form.controls) {
         if(comp.form.controls.hasOwnProperty(controlName)) {
            expect(comp.form.controls[controlName].touched).toBe(true);
         }
      }
   });
});

// ---------------------------------------------------------------------------
// Group 10: unpivot / firstRow mutual exclusion + headerCols validation [legacy regression]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — unpivot and firstRow form coupling", () => {
   it("should clear unpivotCB when firstRowCB is enabled", async () => {
      const { comp } = await renderComponent(undefined, { unpivotCB: true, firstRowCB: false });

      comp.form.get("firstRowCB").patchValue(true);

      expect(comp.form.get("unpivotCB").value).toBeFalsy();
   });

   it("should clear firstRowCB when unpivotCB is enabled", async () => {
      const { comp } = await renderComponent(undefined, { unpivotCB: false, firstRowCB: true });

      comp.form.get("unpivotCB").patchValue(true);

      expect(comp.form.get("firstRowCB").value).toBeFalsy();
   });

   it("should reject negative headerCols when unpivot is enabled", async () => {
      const { comp } = await renderComponent(undefined, { unpivotCB: true, headerCols: 0 });

      const control = comp.form.get("headerCols");
      control.patchValue(-1);
      expect(control.errors).toBeTruthy();

      control.patchValue(0);
      expect(control.errors).toBeFalsy();

      control.patchValue(1);
      expect(control.errors).toBeFalsy();
   });
});
