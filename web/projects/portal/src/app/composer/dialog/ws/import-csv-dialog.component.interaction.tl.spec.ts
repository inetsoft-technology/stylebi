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
 * ImportCSVDialog — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — updateFile empty files array: calls reset(), does NOT upload
 *   Group 2  [Risk 3] — updateFile zero-size file: calls handleEmptyFile() → shows dialog; resets model
 *   Group 3  [Risk 3] — updateFile valid file: calls fileUploadService.upload(); sets fileUploaded=true on resolve
 *   Group 4  [Risk 3] — file type detection: .xls → XLS, .xlsx → XLSX, .csv → DELIMITED
 *   Group 5  [Risk 3] — upload error: calls ComponentTool.showMessageDialog; resets state
 *   Group 6  [Risk 2] — updateForm DELIMITED: enables all controls; disables sheetSelected; enables detectType
 *   Group 7  [Risk 2] — updateForm non-DELIMITED (XLS/XLSX): disables encoding/delimiter/detectType/removeQuotesCB
 *   Group 8  [Risk 2] — validateHeaders: sets duplicateHeaders=true for duplicate names;
 *                        sets invalidCharacters=true for special chars
 *   Group 9  [Risk 2] — validateFirstRow: sets invalidCharacters=true for special chars in first row
 *   Group 10 [Risk 2] — onHeaderRename: initializes headerNames; stores renamed header; calls validateHeaders()
 *   Group 11 [Risk 2] — unpivotEnabled: true when previewTable has >1 column; false otherwise
 *   Group 12 [Risk 1] — ngAfterViewChecked: calls updatePreviewTable when previewOutOfDate=true
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
      fileName: "",
      fileType: null,
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

function makeFile(name: string, size = 100): File {
   return { name, size } as any;
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(
   worksheetOverride?: any,
   modelOverride?: Partial<ImportCSVDialogModel>
) {
   FILE_UPLOAD_SERVICE_MOCK.getObserver.mockReturnValue(EMPTY);
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
      componentInputs: { worksheet },
   });

   const comp = fixture.componentInstance as ImportCSVDialog;
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

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: updateFile empty files array [Risk 3]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — updateFile: empty files array", () => {
   // 🔁 Regression-sensitive: an empty FileList must NOT trigger an upload; doing so
   //    would send a malformed request and leave fileUploaded in a dirty state.
   it("should NOT call fileUploadService.upload when files list is empty", async () => {
      const { comp } = await renderComponent();
      comp.updateFile({ target: { files: [] } });
      expect(FILE_UPLOAD_SERVICE_MOCK.upload).not.toHaveBeenCalled();
   });

   it("should reset model.fileName to empty string when files list is empty", async () => {
      const { comp } = await renderComponent();
      comp.updateFile({ target: { files: [] } });
      expect(comp.model.fileName).toBe("");
   });

   it("should set fileUploaded to false when files list is empty", async () => {
      const { comp } = await renderComponent();
      comp.fileUploaded = true;
      comp.updateFile({ target: { files: [] } });
      expect(comp.fileUploaded).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2: updateFile zero-size file [Risk 3]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — updateFile: zero-size file", () => {
   // 🔁 Regression-sensitive: zero-size files must show an error dialog, not proceed
   //    with an upload of an empty payload.
   it("should NOT call fileUploadService.upload when file size is 0", async () => {
      const { comp } = await renderComponent();
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");

      comp.updateFile({ target: { files: [makeFile("empty.csv", 0)] } });

      expect(FILE_UPLOAD_SERVICE_MOCK.upload).not.toHaveBeenCalled();
   });

   it("should call ComponentTool.showConfirmDialog with error message for zero-size file", async () => {
      const { comp } = await renderComponent();
      const spy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");

      comp.updateFile({ target: { files: [makeFile("empty.csv", 0)] } });

      expect(spy).toHaveBeenCalledWith(
         expect.anything(),
         expect.stringContaining("Error"),
         expect.any(String),
         expect.anything(),
         expect.anything()
      );
   });
});

// ---------------------------------------------------------------------------
// Group 3: updateFile valid file [Risk 3]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — updateFile: valid file", () => {
   // 🔁 Regression-sensitive: a valid file must trigger upload and set fileUploaded=true
   //    on success; not setting it would disable the OK button permanently.
   it("should call fileUploadService.upload with the correct URI", async () => {
      const { comp, worksheet } = await renderComponent();
      FILE_UPLOAD_SERVICE_MOCK.upload.mockResolvedValue({
         model: makeModel({ fileName: "data.csv" }),
         limitMessage: null,
      });

      comp.updateFile({ target: { files: [makeFile("data.csv")] } });

      expect(FILE_UPLOAD_SERVICE_MOCK.upload).toHaveBeenCalledWith(
         expect.stringContaining(worksheet.runtimeId),
         expect.any(Array)
      );
   });

   it("should set fileUploaded=true after a successful upload", async () => {
      const { comp } = await renderComponent();
      FILE_UPLOAD_SERVICE_MOCK.upload.mockResolvedValue({
         model: makeModel({ fileName: "data.csv" }),
         limitMessage: null,
      });

      comp.updateFile({ target: { files: [makeFile("data.csv")] } });
      await vi.waitFor(() => expect(comp.fileUploaded).toBe(true));
   });
});

// ---------------------------------------------------------------------------
// Group 4: file type detection [Risk 3]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — updateFile: file type detection", () => {
   // 🔁 Regression-sensitive: wrong fileType disables the correct form controls;
   //    for example treating XLSX as DELIMITED leaves encoding enabled (confusing users).
   it("should set fileType to 'XLS' for a .xls file", async () => {
      const { comp } = await renderComponent();
      FILE_UPLOAD_SERVICE_MOCK.upload.mockResolvedValue({ model: makeModel(), limitMessage: null });

      comp.updateFile({ target: { files: [makeFile("report.xls")] } });

      expect(comp.model.fileType).toBe("XLS");
   });

   it("should set fileType to 'XLSX' for a .xlsx file", async () => {
      const { comp } = await renderComponent();
      FILE_UPLOAD_SERVICE_MOCK.upload.mockResolvedValue({ model: makeModel(), limitMessage: null });

      comp.updateFile({ target: { files: [makeFile("report.xlsx")] } });

      expect(comp.model.fileType).toBe("XLSX");
   });

   it("should set fileType to 'DELIMITED' for a .csv file", async () => {
      const { comp } = await renderComponent();
      FILE_UPLOAD_SERVICE_MOCK.upload.mockResolvedValue({ model: makeModel(), limitMessage: null });

      comp.updateFile({ target: { files: [makeFile("data.csv")] } });

      expect(comp.model.fileType).toBe("DELIMITED");
   });

   it("should set fileType to 'DELIMITED' for a .txt file", async () => {
      const { comp } = await renderComponent();
      FILE_UPLOAD_SERVICE_MOCK.upload.mockResolvedValue({ model: makeModel(), limitMessage: null });

      comp.updateFile({ target: { files: [makeFile("data.txt")] } });

      expect(comp.model.fileType).toBe("DELIMITED");
   });
});

// ---------------------------------------------------------------------------
// Group 5: upload error [Risk 3]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — updateFile: upload error", () => {
   // 🔁 Regression-sensitive: upload failure must show an error dialog and reset state;
   //    leaving fileUploaded=true after an error enables the OK button with stale data.
   it("should show error dialog via ComponentTool.showMessageDialog on upload failure", async () => {
      const { comp } = await renderComponent();
      FILE_UPLOAD_SERVICE_MOCK.upload.mockRejectedValue(new Error("MaxUploadSizeExceededException"));
      const spy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");

      comp.updateFile({ target: { files: [makeFile("huge.csv")] } });
      await vi.waitFor(() => expect(spy).toHaveBeenCalled());
   });

   it("should reset fileUploaded to false after upload error", async () => {
      const { comp } = await renderComponent();
      FILE_UPLOAD_SERVICE_MOCK.upload.mockRejectedValue(new Error("MaxUploadSizeExceededException"));
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");

      comp.updateFile({ target: { files: [makeFile("huge.csv")] } });
      await vi.waitFor(() => expect(comp.fileUploaded).toBe(false));
   });
});

// ---------------------------------------------------------------------------
// Group 6: updateForm DELIMITED [Risk 2]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — updateForm: DELIMITED file type", () => {
   it("should enable the form after updateForm when fileType is DELIMITED", async () => {
      const { comp } = await renderComponent(undefined, { fileType: "DELIMITED" });
      comp.model.fileType = "DELIMITED";
      comp.model.encodingSelected = "UTF-8";

      comp.updateForm();

      expect(comp.form.get("encodingSelected").enabled).toBe(true);
   });

   it("should disable sheetSelected for DELIMITED files", async () => {
      const { comp } = await renderComponent(undefined, { fileType: "DELIMITED" });
      comp.model.fileType = "DELIMITED";

      comp.updateForm();

      expect(comp.form.get("sheetSelected").disabled).toBe(true);
   });

   it("should enable detectType for DELIMITED files", async () => {
      const { comp } = await renderComponent(undefined, { fileType: "DELIMITED" });
      comp.model.fileType = "DELIMITED";

      comp.updateForm();

      expect(comp.form.get("detectType").enabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 7: updateForm non-DELIMITED [Risk 2]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — updateForm: non-DELIMITED file type (XLS/XLSX)", () => {
   it("should disable encodingSelected for XLS files", async () => {
      const { comp } = await renderComponent(undefined, { fileType: "XLS" });
      comp.model.fileType = "XLS";

      comp.updateForm();

      expect(comp.form.get("encodingSelected").disabled).toBe(true);
   });

   it("should disable detectType for XLSX files", async () => {
      const { comp } = await renderComponent(undefined, { fileType: "XLSX" });
      comp.model.fileType = "XLSX";

      comp.updateForm();

      expect(comp.form.get("detectType").disabled).toBe(true);
   });

   it("should disable delimiter for XLS files", async () => {
      const { comp } = await renderComponent(undefined, { fileType: "XLS" });
      comp.model.fileType = "XLS";

      comp.updateForm();

      expect(comp.form.get("delimiter").disabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 8: validateHeaders [Risk 2]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — validateHeaders", () => {
   // 🔁 Regression-sensitive: duplicateHeaders flag controls whether OK button is disabled;
   //    false positives lock users out; false negatives corrupt the data.
   it("should set duplicateHeaders=true when two column headers are identical", async () => {
      const { comp } = await renderComponent();
      comp.previewTable = [
         [makeCell("ColA"), makeCell("ColB"), makeCell("ColA")],
      ];
      comp.model.headerNames = {};

      comp.validateHeaders();

      expect(comp.duplicateHeaders).toBe(true);
   });

   it("should set duplicateHeaders=false when all column headers are distinct", async () => {
      const { comp } = await renderComponent();
      comp.previewTable = [
         [makeCell("ColA"), makeCell("ColB"), makeCell("ColC")],
      ];
      comp.model.headerNames = {};

      comp.validateHeaders();

      expect(comp.duplicateHeaders).toBe(false);
   });

   it("should set invalidCharacters=true when a header contains special characters", async () => {
      const { comp } = await renderComponent();
      comp.previewTable = [[makeCell("Col[A]")]];
      comp.model.headerNames = {};

      comp.validateHeaders();

      expect(comp.invalidCharacters).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 9: validateFirstRow [Risk 2]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — validateFirstRow", () => {
   it("should set invalidCharacters=true when first-row cell has special characters", async () => {
      const { comp } = await renderComponent();
      comp.previewTable = [[makeCell("Col[Bad]")]];

      comp.validateFirstRow();

      expect(comp.invalidCharacters).toBe(true);
   });

   it("should set invalidCharacters=false when all first-row cells are clean", async () => {
      const { comp } = await renderComponent();
      comp.previewTable = [[makeCell("ColA"), makeCell("ColB")]];

      comp.validateFirstRow();

      expect(comp.invalidCharacters).toBe(false);
   });

   it("should do nothing when previewTable is null", async () => {
      const { comp } = await renderComponent();
      comp.previewTable = null;

      expect(() => comp.validateFirstRow()).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 10: onHeaderRename [Risk 2]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — onHeaderRename", () => {
   it("should initialize model.headerNames when it is null", async () => {
      const { comp } = await renderComponent();
      comp.previewTable = [[makeCell("OldName")]];
      comp.model.headerNames = null;

      comp.onHeaderRename({ column: 0, newName: "NewName" });

      expect(comp.model.headerNames).not.toBeNull();
   });

   it("should store the renamed header at the given column index", async () => {
      const { comp } = await renderComponent();
      comp.previewTable = [[makeCell("A"), makeCell("B"), makeCell("C")]];
      comp.model.headerNames = {};

      comp.onHeaderRename({ column: 1, newName: "Renamed" });

      expect(comp.model.headerNames[1]).toBe("Renamed");
   });
});

// ---------------------------------------------------------------------------
// Group 11: unpivotEnabled [Risk 2]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — unpivotEnabled getter", () => {
   // 🔁 Regression-sensitive: unpivotEnabled controls whether the unpivot checkbox is rendered;
   //    wrong value silently hides or shows a UI control.
   it("should return true when previewTable has more than one column", async () => {
      const { comp } = await renderComponent();
      comp.previewTable = [[makeCell("A"), makeCell("B")]];
      expect(comp.unpivotEnabled).toBe(true);
   });

   it("should return false when previewTable has exactly one column", async () => {
      const { comp } = await renderComponent();
      comp.previewTable = [[makeCell("A")]];
      expect(comp.unpivotEnabled).toBe(false);
   });

   it("should return false when previewTable is null", async () => {
      const { comp } = await renderComponent();
      comp.previewTable = null;
      // getter returns null (&&-short-circuit), not false — A2 pattern
      expect(comp.unpivotEnabled).toBeFalsy();
   });

   it("should return false when previewTable is empty", async () => {
      const { comp } = await renderComponent();
      comp.previewTable = [];
      // getter returns undefined ([][0] is undefined), not false — A2 pattern
      expect(comp.unpivotEnabled).toBeFalsy();
   });
});

// ---------------------------------------------------------------------------
// Group 12: ngAfterViewChecked [Risk 1]
// ---------------------------------------------------------------------------

describe("ImportCSVDialog — ngAfterViewChecked", () => {
   it("should call updatePreviewTable when previewOutOfDate is true", async () => {
      const { comp } = await renderComponent();
      const spy = vi.spyOn(comp as any, "updatePreviewTable").mockImplementation(() => {});
      (comp as any).previewOutOfDate = true;
      comp.fileUploaded = true;

      comp.ngAfterViewChecked();

      expect(spy).toHaveBeenCalled();
   });

   it("should NOT call updatePreviewTable when previewOutOfDate is false", async () => {
      const { comp } = await renderComponent();
      const spy = vi.spyOn(comp as any, "updatePreviewTable");
      (comp as any).previewOutOfDate = false;

      comp.ngAfterViewChecked();

      expect(spy).not.toHaveBeenCalled();
   });

   it("should reset previewOutOfDate to false after calling updatePreviewTable", async () => {
      const { comp } = await renderComponent();
      vi.spyOn(comp as any, "updatePreviewTable").mockImplementation(() => {});
      (comp as any).previewOutOfDate = true;

      comp.ngAfterViewChecked();

      expect((comp as any).previewOutOfDate).toBe(false);
   });
});
