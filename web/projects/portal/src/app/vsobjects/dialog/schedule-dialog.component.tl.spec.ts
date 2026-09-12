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
 * ScheduleDialog — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3]  — ok() validation: empty bookmark name shows error and blocks HTTP
 *   Group 2 [Risk 3]  — ok() validation: special-character bookmark shows error and blocks HTTP
 *   Group 3 [Risk 3]  — ok() HTTP: type=OK → getSimpleScheduleDialog → onCommit emitted
 *   Group 4 [Risk 3]  — ok() HTTP: type=CONFIRM → confirm dialog shown; confirmed → onCommit;
 *                        cancelled → no commit
 *   Group 5 [Risk 2]  — ok() HTTP: non-OK/CONFIRM type → error dialog shown, no commit
 *   Group 6 [baseline] — cancel() → onCancel emitted
 *   Group 7 [baseline] — ngOnInit: !securityEnabled → model.currentBookmark forced to true
 *
 * Confirmed bugs (it.fails): none
 *
 * Suspected bugs (header only):
 *   Suspicion A — ok() and getSimpleScheduleDialog() use unguarded HTTP subscriptions with no
 *     takeUntilDestroyed; if the dialog is destroyed while a request is in flight the callbacks
 *     still fire on a dead component. Practical impact is low for a short-lived dialog.
 *
 * Out of scope:
 *   getSimpleScheduleDialog() — called by ok() on type=OK; covered transitively via Group 3/4
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { EMPTY, Subject } from "rxjs";

import { server } from "@test-mocks/server";
import { MessageDialog } from "../../widget/dialog/message-dialog/message-dialog.component";
import { ScheduleDialog } from "./schedule-dialog.component";
import { ScheduleDialogModel } from "../model/schedule/schedule-dialog-model";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

// ComponentTool.showMessageDialog / showConfirmDialog call
// modal.componentInstance["onCommit"].subscribe() — must use fresh Subject per call.
const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: EMPTY,
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

beforeEach(() => {
   MODAL_MOCK.open.mockClear();
   // Reset dedup guard so same message strings in consecutive tests don't get silently dropped
   MessageDialog.lastMessage = null;
   MessageDialog.lastMessageTS = 0;
});

function makeModel(overrides: Partial<ScheduleDialogModel> = {}): ScheduleDialogModel {
   return {
      currentBookmark: false,
      bookmark: "MyBookmark",
      bookmarkEnabled: false,
      simpleScheduleDialogModel: null as any,
      ...overrides,
   };
}

async function renderComponent(opts: {
   model?: ScheduleDialogModel;
   runtimeId?: string;
   securityEnabled?: boolean;
} = {}) {
   const { fixture } = await render(ScheduleDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         { provide: NgbModal, useValue: MODAL_MOCK },
      ],
      componentInputs: {
         model: opts.model ?? makeModel(),
         runtimeId: opts.runtimeId ?? "vs-runtime-id",
         securityEnabled: opts.securityEnabled ?? true,
         exportTypes: [],
         principal: "user1",
      },
   });
   return { comp: fixture.componentInstance, fixture };
}

// ---------------------------------------------------------------------------
// Group 1 — ok(): empty bookmark name guard [Risk 3]
// ---------------------------------------------------------------------------

describe("ScheduleDialog — ok(): empty bookmark name guard", () => {
   // 🔁 Regression-sensitive: the empty-name guard must fire BEFORE the HTTP call;
   // losing it means a schedule task can be created without a required bookmark name.
   it("should skip HTTP when bookmarkEnabled=true and bookmark is empty", async () => {
      let httpCalled = false;
      server.use(
         http.get("*/api/vs/check-schedule-dialog/*", () => {
            httpCalled = true;
            return MswHttpResponse.json({ type: "OK", message: "" });
         })
      );
      const { comp } = await renderComponent({
         model: makeModel({ bookmarkEnabled: true, bookmark: "" }),
      });

      comp.ok();

      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1)); // error dialog shown — proves ok() ran the guard
      expect(httpCalled).toBe(false);
   });

   it("should show empty-name error dialog when bookmarkEnabled=true and bookmark is empty", async () => {
      const { comp } = await renderComponent({
         model: makeModel({ bookmarkEnabled: true, bookmark: "" }),
      });

      comp.ok();

      expect(MessageDialog.lastMessage).toBe("_#(js:viewer.viewsheet.bookmark.emptyName)");
   });
});

// ---------------------------------------------------------------------------
// Group 2 — ok(): special-character bookmark guard [Risk 3]
// ---------------------------------------------------------------------------

describe("ScheduleDialog — ok(): special-character bookmark guard", () => {
   // 🔁 Regression-sensitive: bookmark names containing special chars must be rejected
   // before the HTTP call; bypassing creates schedules with invalid names on the server.
   it("should skip HTTP when bookmark contains invalid characters", async () => {
      let httpCalled = false;
      server.use(
         http.get("*/api/vs/check-schedule-dialog/*", () => {
            httpCalled = true;
            return MswHttpResponse.json({ type: "OK", message: "" });
         })
      );
      const { comp } = await renderComponent({
         model: makeModel({ bookmarkEnabled: true, bookmark: "bad/name" }),
      });

      comp.ok();

      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1)); // error dialog shown — proves ok() ran the guard
      expect(httpCalled).toBe(false);
   });

   it("should show special-char error dialog when bookmark contains invalid characters", async () => {
      const { comp } = await renderComponent({
         model: makeModel({ bookmarkEnabled: true, bookmark: "bad/name" }),
      });

      comp.ok();

      expect(MessageDialog.lastMessage).toBe("_#(js:viewer.viewsheet.bookmark.nameFormat)");
   });
});

// ---------------------------------------------------------------------------
// Group 3 — ok() HTTP: type=OK → onCommit emitted [Risk 3]
// ---------------------------------------------------------------------------

describe("ScheduleDialog — ok(): HTTP type=OK → onCommit emitted", () => {
   // 🔁 Regression-sensitive: the full commit chain (check → fetch → modal → emit) must
   // fire in order; a broken link at any step silently drops the schedule creation.

   it("should reach the HTTP check when bookmarkEnabled=false (guard is correctly bypassed)", async () => {
      let httpCalled = false;
      server.use(
         http.get("*/api/vs/check-schedule-dialog/*", () => {
            httpCalled = true;
            return MswHttpResponse.json({ type: "OK", message: "" });
         })
      );
      // Provide a Promise-based result so the component's .result.then() call doesn't throw
      // after the type=OK response triggers getSimpleScheduleDialog → modal.open().
      MODAL_MOCK.open.mockImplementationOnce(() => ({
         result: new Promise(() => {}),
         componentInstance: { onCommit: new Subject<string>() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));
      const { comp } = await renderComponent({
         model: makeModel({ bookmarkEnabled: false }),
      });

      comp.ok();

      await waitFor(() => expect(httpCalled).toBe(true));
   });

   it("should emit onCommit after type=OK HTTP response triggers the simple schedule flow", async () => {
      const { comp } = await renderComponent();
      const committed: ScheduleDialogModel[] = [];
      comp.onCommit.subscribe((m: ScheduleDialogModel) => committed.push(m));

      // Simulate user confirming the modal opened by getSimpleScheduleDialog()
      MODAL_MOCK.open.mockImplementationOnce(() => ({
         result: Promise.resolve({ taskName: "task1" }),
         componentInstance: { onCommit: new Subject<string>() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));

      comp.ok();

      await waitFor(() => expect(committed).toHaveLength(1));
      expect(committed[0]).toBe(comp.model);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — ok() HTTP: type=CONFIRM flow [Risk 3]
// ---------------------------------------------------------------------------

describe("ScheduleDialog — ok(): HTTP type=CONFIRM flow", () => {
   // 🔁 Regression-sensitive: the CONFIRM branch must gate the schedule creation behind
   // user confirmation; skipping it would create duplicates without user awareness.
   it("should proceed to schedule flow when user confirms the CONFIRM dialog", async () => {
      server.use(
         http.get("*/api/vs/check-schedule-dialog/*", () =>
            MswHttpResponse.json({ type: "CONFIRM", message: "A schedule already exists. Replace it?" })
         )
      );

      const { comp } = await renderComponent();
      const committed: ScheduleDialogModel[] = [];
      comp.onCommit.subscribe((m: ScheduleDialogModel) => committed.push(m));

      // First open() is the confirm dialog; user presses OK
      MODAL_MOCK.open
         .mockImplementationOnce(() => ({
            result: Promise.resolve("ok"),
            componentInstance: { onCommit: new Subject<string>() },
            close: vi.fn(),
            dismiss: vi.fn(),
         }))
         // Second open() is the simpleScheduleDialog; user confirms it
         .mockImplementationOnce(() => ({
            result: Promise.resolve({ taskName: "task1" }),
            componentInstance: { onCommit: new Subject<string>() },
            close: vi.fn(),
            dismiss: vi.fn(),
         }));

      comp.ok();

      await waitFor(() => expect(committed).toHaveLength(1));
   });

   it("should NOT emit onCommit when user cancels the CONFIRM dialog", async () => {
      server.use(
         http.get("*/api/vs/check-schedule-dialog/*", () =>
            MswHttpResponse.json({ type: "CONFIRM", message: "A schedule already exists. Replace it?" })
         )
      );

      const { comp } = await renderComponent();
      const committed: ScheduleDialogModel[] = [];
      comp.onCommit.subscribe((m: ScheduleDialogModel) => committed.push(m));

      // User presses Cancel in the confirm dialog
      MODAL_MOCK.open.mockImplementationOnce(() => ({
         result: Promise.resolve("cancel"),
         componentInstance: { onCommit: new Subject<string>() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));

      comp.ok();

      // Pin to exactly 1 open() call: confirms the confirm dialog fired but no second modal
      // (e.g. schedule modal) was opened as a side-effect of the cancel path.
      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1));
      expect(committed).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — ok() HTTP: non-OK/CONFIRM type → error dialog [Risk 2]
// ---------------------------------------------------------------------------

describe("ScheduleDialog — ok(): HTTP non-OK/CONFIRM type", () => {
   it("should show error dialog when HTTP returns type=ERROR", async () => {
      server.use(
         http.get("*/api/vs/check-schedule-dialog/*", () =>
            MswHttpResponse.json({ type: "ERROR", message: "Cannot schedule viewsheet" })
         )
      );

      const { comp } = await renderComponent();

      comp.ok();

      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled());
   });

   it("should NOT emit onCommit when HTTP returns type=ERROR", async () => {
      server.use(
         http.get("*/api/vs/check-schedule-dialog/*", () =>
            MswHttpResponse.json({ type: "ERROR", message: "Cannot schedule viewsheet" })
         )
      );

      const { comp } = await renderComponent();
      const committed: ScheduleDialogModel[] = [];
      comp.onCommit.subscribe((m: ScheduleDialogModel) => committed.push(m));

      comp.ok();

      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled()); // positive gate: error dialog opened
      expect(committed).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — cancel() [baseline]
// ---------------------------------------------------------------------------

describe("ScheduleDialog — cancel()", () => {
   it("should emit onCancel with 'cancel' when cancel() is called", async () => {
      const { comp } = await renderComponent();
      const cancelled: string[] = [];
      comp.onCancel.subscribe((v: string) => cancelled.push(v));

      comp.cancel();

      expect(cancelled).toEqual(["cancel"]);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — ngOnInit: securityEnabled flag [baseline]
// ---------------------------------------------------------------------------

describe("ScheduleDialog — ngOnInit: securityEnabled flag", () => {
   // 🔁 Regression-sensitive: when security is disabled the currentBookmark must default to
   // true so un-authenticated users cannot create new bookmarks via the schedule flow.
   it("should force currentBookmark=true when securityEnabled is false", async () => {
      const model = makeModel({ currentBookmark: false });
      const { comp } = await renderComponent({ model, securityEnabled: false });

      expect(comp.model.currentBookmark).toBe(true);
   });

   it("should NOT change currentBookmark when securityEnabled is true", async () => {
      const model = makeModel({ currentBookmark: false });
      const { comp } = await renderComponent({ model, securityEnabled: true });

      expect(comp.model.currentBookmark).toBe(false);
   });
});
