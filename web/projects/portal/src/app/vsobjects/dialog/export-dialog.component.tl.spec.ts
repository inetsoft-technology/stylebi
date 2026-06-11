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
 * ExportDialog — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ok(): HTTP validation → onCommit dispatch chain
 *   Group 2 [Risk 2] — ok() guard: three-condition check (includeCurrent/bookmarks/formatType)
 *   Group 3 [Risk 1] — close(), ngOnInit, isEmptyTable()
 *
 * Confirmed bugs (it.fails): none
 *
 * Suspected bugs (header only):
 *   Suspicion A — ok(): no takeUntilDestroyed on the HTTP subscription; if the dialog is
 *     destroyed while the request is in flight, ok2() will still fire on a dead component.
 *     Practical impact is low (short-lived dialog).
 *
 * Out of scope:
 *   getExportTypes()  — trivially returns this.exportTypes; no logic to contract-test
 *   initForm()        — called by ngOnInit; covered transitively
 *   ok2()             — called by ok() on success; covered transitively
 *   saveToStorage()   — called by ok2(); covered transitively via onCommit assertion
 *   getStorageModel() — called by ngOnInit; LocalStorage is empty in test env so branch skipped
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ReactiveFormsModule } from "@angular/forms";
import { provideHttpClient } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { EMPTY, Subject } from "rxjs";

import { server } from "@test-mocks/server";
import { ComponentTool } from "../../common/util/component-tool";
import { ExportDialog } from "./export-dialog.component";
import { ExportDialogModel } from "../model/export-dialog-model";
import { FileFormatPaneModel } from "../model/file-format-pane-model";
import { FileFormatType } from "../model/file-format-type";

// Allows partial overrides of the nested fileFormatPaneModel (deep merge in makeModel)
type ModelOverrides = {
   fileFormatPaneModel?: Partial<FileFormatPaneModel>;
};

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

// saveToStorage() writes to localStorage on every ok() success; clear between tests
// so getStorageModel() in ngOnInit does not overwrite the test's model settings.
beforeEach(() => {
   localStorage.clear();
});

// ComponentTool.showMessageDialog calls modal.componentInstance["onCommit"].subscribe()
const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: EMPTY,
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

function makeModel(overrides: ModelOverrides = {}): ExportDialogModel {
   const base: ExportDialogModel = {
      fileFormatPaneModel: {
         formatType: FileFormatType.EXPORT_TYPE_EXCEL,
         matchLayout: false,
         expandSelections: false,
         includeCurrent: true,
         linkVisible: false,
         sendLink: false,
         selectedBookmarks: [],
         allBookmarks: [],
         allBookmarkLabels: [],
         expandEnabled: false,
         onlyDataComponents: false,
      },
   };
   return {
      ...base,
      fileFormatPaneModel: overrides.fileFormatPaneModel
         ? { ...base.fileFormatPaneModel, ...overrides.fileFormatPaneModel }
         : base.fileFormatPaneModel,
   };
}

async function renderComponent(opts: {
   modelOverrides?: ModelOverrides;
   runtimeId?: string;
   exportTypes?: { label: string; value: string }[];
} = {}) {
   const { fixture } = await render(ExportDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      imports: [ReactiveFormsModule],
      providers: [
         provideHttpClient(),
         { provide: NgbModal, useValue: MODAL_MOCK },
      ],
      componentInputs: {
         model: makeModel(opts.modelOverrides),
         runtimeId: opts.runtimeId ?? "test-runtime-id",
         exportTypes: opts.exportTypes ?? [
            { label: "Excel", value: "Excel" },
            { label: "PDF", value: "PDF" },
         ],
      },
   });
   return { comp: fixture.componentInstance, fixture };
}

// ---------------------------------------------------------------------------
// Group 1 — ok(): HTTP validation → onCommit chain [Risk 3]
// ---------------------------------------------------------------------------

describe("ExportDialog — ok(): HTTP validation + commit chain", () => {

   beforeEach(() => {
      MODAL_MOCK.open.mockClear();
   });

   // 🔁 Regression-sensitive: onCommit must only fire after a successful HTTP check
   it("should emit onCommit when server returns type=OK", async () => {
      const { comp } = await renderComponent();
      const commitSpy = vi.fn();
      comp.onCommit.subscribe(commitSpy);

      comp.ok();

      await waitFor(() => expect(commitSpy).toHaveBeenCalledWith(comp.model));
   });

   // 🔁 Regression-sensitive: non-OK response must show dialog and block onCommit
   it("should show error dialog and NOT emit onCommit when server returns non-OK type", async () => {
      server.use(
         http.get("*/export/check/*", () =>
            MswHttpResponse.json({ type: "ERROR", message: "Export not allowed" })
         )
      );

      const { comp } = await renderComponent();
      const commitSpy = vi.fn();
      comp.onCommit.subscribe(commitSpy);

      comp.ok();

      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled());
      expect(commitSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2 — ok() guard: three-condition check [Risk 2]
// ---------------------------------------------------------------------------

describe("ExportDialog — ok() guard", () => {

   beforeEach(() => {
      MODAL_MOCK.open.mockClear();
   });

   // 🔁 Regression-sensitive: guard fires only when ALL THREE conditions are met
   //    (!includeCurrent AND no bookmarks AND formatType != SNAPSHOT)
   //    Bug #17235: verify exact i18n keys for title and message
   it("should show error dialog and skip HTTP when includeCurrent=false, no bookmarks, non-snapshot", async () => {
      let httpCalled = false;
      server.use(
         http.get("*/export/check/*", () => {
            httpCalled = true;
            return MswHttpResponse.json({ type: "OK", message: "" });
         })
      );
      const showMessageSpy = vi.spyOn(ComponentTool, "showMessageDialog");

      const { comp } = await renderComponent({
         modelOverrides: {
            fileFormatPaneModel: {
               includeCurrent: false,
               selectedBookmarks: [],
               formatType: FileFormatType.EXPORT_TYPE_EXCEL,
            },
         },
      });

      comp.ok();

      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled());
      expect(httpCalled).toBe(false);
      expect(showMessageSpy.mock.calls[0][1]).toEqual("_#(js:Error)");
      expect(showMessageSpy.mock.calls[0][2]).toEqual("_#(js:common.fileformatPane.notvoid)");
      showMessageSpy.mockRestore();
   });

   // 🔁 Regression-sensitive: SNAPSHOT format bypasses the guard — the third condition
   //    (formatType != SNAPSHOT) is false, so the guard never fires even with no bookmarks.
   //    FileFormatPane (child) mutates formatType in ngOnInit when the value isn't in its
   //    types list; restore the intended value after render before calling ok().
   it("should NOT trigger guard and proceed to HTTP when formatType is SNAPSHOT", async () => {
      const { comp } = await renderComponent({
         modelOverrides: {
            fileFormatPaneModel: { includeCurrent: false, selectedBookmarks: [] },
         },
      });
      // Restore after FileFormatPane.ngOnInit() mutation
      comp.model.fileFormatPaneModel.formatType = FileFormatType.EXPORT_TYPE_SNAPSHOT;

      const commitSpy = vi.fn();
      comp.onCommit.subscribe(commitSpy);

      comp.ok();

      await waitFor(() => expect(commitSpy).toHaveBeenCalled());
      expect(MODAL_MOCK.open).not.toHaveBeenCalled();
   });

   it("should NOT trigger guard when bookmarks are selected", async () => {
      const { comp } = await renderComponent({
         modelOverrides: {
            fileFormatPaneModel: {
               includeCurrent: false,
               selectedBookmarks: ["bookmark1"],
               formatType: FileFormatType.EXPORT_TYPE_EXCEL,
            },
         },
      });
      const commitSpy = vi.fn();
      comp.onCommit.subscribe(commitSpy);

      comp.ok();

      await waitFor(() => expect(commitSpy).toHaveBeenCalled());
   });
});

// ---------------------------------------------------------------------------
// Group 3 — close(), ngOnInit, isEmptyTable() [Risk 1]
// ---------------------------------------------------------------------------

describe("ExportDialog — close, ngOnInit, isEmptyTable", () => {

   beforeEach(() => {
      MODAL_MOCK.open.mockClear();
   });

   it("should emit 'cancel' when close() is called", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onCancel.subscribe(spy);

      comp.close();

      expect(spy).toHaveBeenCalledWith("cancel");
   });

   it("should initialize form group on ngOnInit", async () => {
      const { comp } = await renderComponent();

      expect(comp.form).toBeDefined();
      expect(comp.form.get("exportForm")).toBeDefined();
   });

   // 🔁 Regression-sensitive: CSV + empty tableDataAssemblies = empty export guard.
   //    FileFormatPane (child) mutates formatType in ngOnInit; restore after render.
   it("should return true from isEmptyTable when formatType is CSV and tableDataAssemblies is empty", async () => {
      const { comp } = await renderComponent({
         modelOverrides: {
            fileFormatPaneModel: { tableDataAssemblies: [] },
         },
      });
      // Restore after FileFormatPane.ngOnInit() mutation
      comp.model.fileFormatPaneModel.formatType = FileFormatType.EXPORT_TYPE_CSV;

      expect(comp.isEmptyTable()).toBe(true);
   });
});
