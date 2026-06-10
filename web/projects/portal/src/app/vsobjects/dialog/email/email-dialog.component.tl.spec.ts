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
 * EmailDialog — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ok(): email validation → sendFunction dispatch chain
 *   Group 2 [Risk 2] — ok() guard: empty bookmark selection blocks HTTP call
 *   Group 3 [Risk 1] — cancel(), ngOnInit, emailExportTypes
 *
 * Confirmed bugs (it.fails): none
 *
 * Suspected bugs (header only):
 *   Suspicion A — ok(): no takeUntilDestroyed on the HTTP subscription; if the
 *     dialog is destroyed while the request is in flight the callback will still
 *     fire and attempt to call showLoading/onCommit on a destroyed component.
 *     Practical impact is low (dialogs are short-lived), so not failing here.
 *
 * Out of scope:
 *   validate()       — always returns false; no logic to contract-test
 *   initForm()       — called by ngOnInit; covered transitively
 *   addToHistory()   — called inside ok() success callback; covered transitively
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ReactiveFormsModule } from "@angular/forms";
import { provideHttpClient } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { EMPTY, Subject } from "rxjs";

import { server } from "@test-mocks/server";
import { EmailDialog } from "./email-dialog.component";
import { ModelService } from "../../../widget/services/model.service";
import { EmailDialogModel } from "../../model/email-dialog-model";
import { EmailPaneModel } from "../../model/email-pane-model";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

// ComponentTool.showMessageDialog calls modal.componentInstance["onCommit"].subscribe()
// and modal.result — use mockImplementation so each open() gets a fresh Subject
const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: EMPTY,
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

function makeModel(overrides: Partial<EmailDialogModel> = {}): EmailDialogModel {
   return {
      id: "",
      emailPaneModel: { toAddress: "test@example.com", ccAddress: "" } as EmailPaneModel,
      fileFormatPaneModel: {
         formatType: 0,
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
      historyEnabled: false,
      ...overrides,
   };
}

async function renderComponent(opts: {
   modelOverrides?: Partial<any>;
   sendFn?: (model: any, commit: Function, stop: Function) => void;
   exportTypes?: { label: string; value: string }[];
} = {}) {
   const sendFunction = opts.sendFn ?? vi.fn((m: any, commit: Function) => commit());
   const { fixture } = await render(EmailDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      imports: [ReactiveFormsModule],
      providers: [
         provideHttpClient(),
         ModelService,
         { provide: NgbModal, useValue: MODAL_MOCK },
      ],
      componentInputs: {
         model: makeModel(opts.modelOverrides),
         exportTypes: opts.exportTypes ?? [
            { label: "Excel", value: "Excel" },
            { label: "PDF", value: "PDF" },
            { label: "Snapshot", value: "Snapshot" },
         ],
         sendFunction,
      },
   });
   return { comp: fixture.componentInstance, sendFunction, fixture };
}

// ---------------------------------------------------------------------------
// Group 1 — ok(): email validation → sendFunction dispatch chain [Risk 3]
// ---------------------------------------------------------------------------

describe("EmailDialog — ok(): email validation + send chain", () => {

   beforeEach(() => {
      MODAL_MOCK.open.mockClear();
   });

   // 🔁 Regression-sensitive: showLoading must be reset to false after commit callback fires
   it("should call sendFunction and emit onCommit when server returns type=OK", async () => {
      const { comp, sendFunction } = await renderComponent();
      const commitSpy = vi.fn();
      comp.onCommit.subscribe(commitSpy);

      comp.ok();

      await waitFor(() => expect(sendFunction).toHaveBeenCalled());
      // commit callback fires synchronously inside sendFn — showLoading reset to false
      expect(comp.showLoading).toBe(false);
      expect(commitSpy).toHaveBeenCalledWith(comp.model);
   });

   // 🔁 Regression-sensitive: sendFunction must NOT be invoked when type != "OK"
   it("should show error dialog and NOT call sendFunction when server returns error type", async () => {
      server.use(
         http.get("*/api/vs/check-email-valid", () =>
            MswHttpResponse.json({
               messageCommand: { type: "ERROR", message: "Invalid address" },
               addressHistory: [],
            })
         )
      );

      const { comp, sendFunction } = await renderComponent();

      comp.ok();

      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled());
      expect(sendFunction).not.toHaveBeenCalled();
   });

   it("should set showLoading=true before invoking sendFunction", async () => {
      let loadingWhenSendCalled = false;
      const { comp } = await renderComponent({
         sendFn: (m, commit) => {
            loadingWhenSendCalled = (comp as any).showLoading;
            // do not call commit — leaves loading true
         },
      });

      comp.ok();

      await waitFor(() => expect(loadingWhenSendCalled).toBe(true));
   });

   it("should reset showLoading=false when stop callback is invoked", async () => {
      const { comp } = await renderComponent({
         sendFn: (m, _commit, stop) => {
            stop();
         },
      });

      comp.ok();

      await waitFor(() => expect(comp.showLoading).toBe(false));
   });
});

// ---------------------------------------------------------------------------
// Group 2 — ok() guard: empty selection blocks HTTP call [Risk 2]
// ---------------------------------------------------------------------------

describe("EmailDialog — ok() guard: no bookmark selection", () => {

   beforeEach(() => {
      MODAL_MOCK.open.mockClear();
   });

   // 🔁 Regression-sensitive: both conditions (includeCurrent=false AND selectedBookmarks=[])
   //    must be true to trigger the guard; one false is not enough
   it("should show error dialog and skip HTTP when includeCurrent=false and no bookmarks", async () => {
      let httpCalled = false;
      server.use(
         http.get("*/api/vs/check-email-valid", () => {
            httpCalled = true;
            return MswHttpResponse.json({
               messageCommand: { type: "OK", message: "" },
               addressHistory: [],
            });
         })
      );

      const { comp, sendFunction } = await renderComponent({
         modelOverrides: {
            fileFormatPaneModel: { includeCurrent: false, selectedBookmarks: [] },
         },
      });

      comp.ok();

      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled());
      expect(httpCalled).toBe(false);
      expect(sendFunction).not.toHaveBeenCalled();
   });

   it("should proceed with HTTP when includeCurrent=false but bookmarks are selected", async () => {
      const { comp, sendFunction } = await renderComponent({
         modelOverrides: {
            fileFormatPaneModel: { includeCurrent: false, selectedBookmarks: ["bookmark1"] },
         },
      });

      comp.ok();

      await waitFor(() => expect(sendFunction).toHaveBeenCalled());
   });

   it("should proceed with HTTP when includeCurrent=true regardless of selectedBookmarks", async () => {
      const { comp, sendFunction } = await renderComponent({
         modelOverrides: {
            fileFormatPaneModel: { includeCurrent: true, selectedBookmarks: [] },
         },
      });

      comp.ok();

      await waitFor(() => expect(sendFunction).toHaveBeenCalled());
   });
});

// ---------------------------------------------------------------------------
// Group 3 — cancel(), ngOnInit, emailExportTypes [Risk 1]
// ---------------------------------------------------------------------------

describe("EmailDialog — cancel, ngOnInit, emailExportTypes", () => {

   beforeEach(() => {
      MODAL_MOCK.open.mockClear();
   });

   it("should emit 'cancel' when cancel() is called", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onCancel.subscribe(spy);

      comp.cancel();

      expect(spy).toHaveBeenCalledWith("cancel");
   });

   it("should initialise form group on ngOnInit", async () => {
      const { comp } = await renderComponent();

      expect(comp.form).toBeDefined();
      expect(comp.form.get("emailForm")).toBeDefined();
   });

   // 🔁 Regression-sensitive: filter must match exact string "Snapshot" (case-sensitive)
   it("should exclude Snapshot from emailExportTypes while keeping others", async () => {
      const { comp } = await renderComponent({
         exportTypes: [
            { label: "Excel", value: "Excel" },
            { label: "Snapshot", value: "Snapshot" },
            { label: "PDF", value: "PDF" },
         ],
      });

      const values = comp.emailExportTypes.map(t => t.value);
      expect(values).not.toContain("Snapshot");
      expect(values).toContain("Excel");
      expect(values).toContain("PDF");
   });

   it("should return all types unchanged when no Snapshot entry is present", async () => {
      const { comp } = await renderComponent({
         exportTypes: [{ label: "Excel", value: "Excel" }],
      });

      expect(comp.emailExportTypes).toHaveLength(1);
      expect(comp.emailExportTypes[0].value).toBe("Excel");
   });
});
