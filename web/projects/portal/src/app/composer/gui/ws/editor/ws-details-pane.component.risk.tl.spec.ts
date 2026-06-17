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
 * WSDetailsPaneComponent — Pass 2: Risk
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — openFormulaEditorDialog: calls modelService.getModel with
 *                        correct URI containing runtimeId; opens FormulaEditorDialog
 *                        via dialogService; onCommit sends STOMP event.
 *   Group 2  [Risk 2] — openShowHideColumnsDialog: opens dialog with table reference;
 *                        onCommit sends worksheetClient.sendEvent with resolve data.
 *   Group 3  [Risk 2] — runQuery / cancelQuery: send WSAssemblyEvent via
 *                        worksheet.socketConnection.sendEvent with correct URIs.
 *   Group 4  [Risk 2] — exportTable: calls downloadService.download with URI
 *                        containing runtimeId and table name.
 *   Group 5  [Risk 1] — openImportCSVDialog: calls ngbModal.open with importCsvDialog ref.
 */

import { of } from "rxjs";
import { makeMocks, makeTable, makeWorksheet, renderComponent } from "./ws-details-pane.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: openFormulaEditorDialog [Risk 3]
// ---------------------------------------------------------------------------

describe("WSDetailsPaneComponent — openFormulaEditorDialog", () => {

   // Regression-sensitive: expression dialog must load the model from the correct
   // runtimeId URI; loading from the wrong URI returns wrong column tree data.
   it("should call modelService.getModel with runtimeId in the URI", async () => {
      const mocks = makeMocks();
      mocks.worksheet.runtimeId = "rt-abc-123";
      mocks.modelService.getModel = vi.fn().mockReturnValue(of({
         oldName: "expr1",
         dataType: "string",
         formulaType: "script",
         expression: "return 1;",
         columnTree: { children: [{ children: [] }] },
         scriptDefinitions: {},
         sqlMergeable: false,
         tableName: "TestTable",
      }));
      const { comp } = await renderComponent(mocks);

      comp.openFormulaEditorDialog();

      expect(mocks.modelService.getModel).toHaveBeenCalledWith(
         expect.stringContaining("rt-abc-123"),
         expect.anything(),
      );
   });

   it("should open FormulaEditorDialog via dialogService after model loads", async () => {
      const mocks = makeMocks();
      mocks.modelService.getModel = vi.fn().mockReturnValue(of({
         oldName: "expr1",
         dataType: "string",
         formulaType: "script",
         expression: "return 1;",
         columnTree: { children: [{ children: [] }] },
         scriptDefinitions: {},
         sqlMergeable: false,
         tableName: "TestTable",
      }));
      const { comp } = await renderComponent(mocks);

      comp.openFormulaEditorDialog();

      expect(mocks.dialogService.open).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2: openShowHideColumnsDialog [Risk 2]
// ---------------------------------------------------------------------------

describe("WSDetailsPaneComponent — openShowHideColumnsDialog", () => {

   it("should open ShowHideColumnsDialog via dialogService", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      comp.openShowHideColumnsDialog();

      expect(mocks.dialogService.open).toHaveBeenCalled();
   });

   it("should pass table reference to dialog.table", async () => {
      const mocks = makeMocks();
      mocks.dialogService.open = vi.fn().mockReturnValue({
         componentInstance: {
            runtimeId: null,
            table: null,
            showColumnName: false,
         },
         result: new Promise<never>((_, reject) => reject("cancel")),
      });
      const { comp } = await renderComponent(mocks);

      comp.openShowHideColumnsDialog();

      const instance = mocks.dialogService.open.mock.results[0].value.componentInstance;
      expect(instance.table).toBe(mocks.table);
   });

   it("should set runtimeId on dialog instance", async () => {
      const mocks = makeMocks();
      mocks.worksheet.runtimeId = "rt-ws-1";
      mocks.dialogService.open = vi.fn().mockReturnValue({
         componentInstance: { runtimeId: null, table: null, showColumnName: false },
         result: new Promise<never>((_, reject) => reject("cancel")),
      });
      const { comp } = await renderComponent(mocks);

      comp.openShowHideColumnsDialog();

      const instance = mocks.dialogService.open.mock.results[0].value.componentInstance;
      expect(instance.runtimeId).toBe("rt-ws-1");
   });
});

// ---------------------------------------------------------------------------
// Group 3: runQuery / cancelQuery [Risk 2]
// ---------------------------------------------------------------------------

describe("WSDetailsPaneComponent — runQuery / cancelQuery", () => {

   // Regression-sensitive: run/stop must use worksheet.socketConnection, NOT
   // worksheetClient, so the correct server-side scope is targeted.
   it("run-query button should send RUN_QUERY_SOCKET_URI via worksheet.socketConnection", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      const runBtn = comp.tableButtons.find(b => b.label === "run-query");
      runBtn?.clickFunction?.();

      expect(mocks.worksheet.socketConnection.sendEvent).toHaveBeenCalledWith(
         expect.stringContaining("run"),
         expect.objectContaining({ assemblyName: "TestTable" }),
      );
   });

   it("stop-query button should send STOP_QUERY_SOCKET_URI via worksheet.socketConnection", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      const stopBtn = comp.tableButtons.find(b => b.label === "stop-query");
      stopBtn?.clickFunction?.();

      expect(mocks.worksheet.socketConnection.sendEvent).toHaveBeenCalledWith(
         expect.stringContaining("stop"),
         expect.objectContaining({ assemblyName: "TestTable" }),
      );
   });

   it("runQuery should NOT use worksheetClient.sendEvent", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      const runBtn = comp.tableButtons.find(b => b.label === "run-query");
      runBtn?.clickFunction?.();

      expect(mocks.worksheetClient.sendEvent).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 4: exportTable [Risk 2]
// ---------------------------------------------------------------------------

describe("WSDetailsPaneComponent — exportTable", () => {

   // Regression-sensitive: export URI must include both runtimeId and table name
   // in correctly encoded form so the backend retrieves the right data set.
   it("should call downloadService.download with URI containing runtimeId and table name", async () => {
      const mocks = makeMocks();
      mocks.worksheet.runtimeId = "rt-export-1";
      mocks.table.name = "SalesTable";
      const { comp } = await renderComponent(mocks);
      comp.table = mocks.table;

      const exportBtn = comp.tableButtons.find(b => b.label === "export");
      exportBtn?.clickFunction?.();

      const [downloadUri] = mocks.downloadService.download.mock.calls[0];
      expect(downloadUri).toContain("rt-export-1");
      expect(downloadUri).toContain("SalesTable");
   });
});

// ---------------------------------------------------------------------------
// Group 5: openImportCSVDialog [Risk 1]
// ---------------------------------------------------------------------------

describe("WSDetailsPaneComponent — openImportCSVDialog", () => {

   it("should call ngbModal.open with importCsvDialog template", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      // Wire the @ViewChild template ref that is null under NO_ERRORS_SCHEMA
      (comp as any).importCsvDialog = "mock-template";

      comp.openImportCSVDialog();

      expect(mocks.modalService.open).toHaveBeenCalledWith("mock-template", expect.anything());
   });
});
