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
 * ShareGoogleChatDialog — single pass
 *
 * Risk-first coverage:
 *   Group 1 [baseline]  — ngOnInit: message field initialized from component inputs
 *   Group 2 [Risk 3]    — ok(): form guard blocks HTTP; loading flag; success commit; error dialog
 *   Group 3 [baseline]  — cancel(): onCancel emission
 *
 * Mocking strategy — real ShareService + AppInfoService via MSW (not vi.fn() mocks):
 *   ShareGoogleChatDialog does NOT inject AppInfoService directly. ShareService's
 *   constructor subscribes to AppInfoService.getCurrentOrgInfo(), but that HTTP call
 *   completes before fixture teardown (MSW responds synchronously) and causes no NG0205.
 *   Contrast with share-email-dialog.component.tl.spec.ts, where AppInfoService is
 *   injected directly and its constructor-fired GET /api/org/info was arriving after
 *   teardown — requiring full vi.fn() mocks to eliminate all HTTP. The strategies are
 *   intentionally different; both rely on MSW viewerHandlers being active in the suite.
 *
 * SilentErrorHandler absorbs the RxJS unhandled rejection from handleError → throwError()
 * to prevent Zone.js bleed between tests.
 */

import { ErrorHandler, NO_ERRORS_SCHEMA } from "@angular/core";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { HttpErrorResponse, provideHttpClient } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";

import { ShareGoogleChatDialog } from "./share-google-chat-dialog.component";
import { ShareService } from "./share.service";
import { AppInfoService } from "../../../../../shared/util/app-info.service";
import { MessageDialog } from "../dialog/message-dialog/message-dialog.component";

// Absorbs the Zone.js unhandled rejection from handleError → throwError(httpError).
class SilentErrorHandler extends ErrorHandler {
   override handleError(_error: any): void {}
}

const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

interface RenderOpts {
   viewsheetId?: string;
   viewsheetName?: string;
   username?: string;
}

async function renderComponent(opts: RenderOpts = {}) {
   const { fixture } = await render(ShareGoogleChatDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      imports: [FormsModule, ReactiveFormsModule],
      providers: [
         provideHttpClient(),
         ShareService,
         AppInfoService,
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: ErrorHandler, useClass: SilentErrorHandler },
      ],
      componentInputs: {
         viewsheetId: opts.viewsheetId !== undefined ? opts.viewsheetId : "1^2^__NULL__^test-dashboard",
         viewsheetName: opts.viewsheetName ?? "My Dashboard",
         username: opts.username ?? "alice",
      },
   });
   return { comp: fixture.componentInstance, fixture };
}

beforeEach(() => {
   MODAL_MOCK.open.mockClear();
   MessageDialog.lastMessage = null;
   MessageDialog.lastMessageTS = 0;
});

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit: message initialization [baseline]
// ---------------------------------------------------------------------------

describe("ShareGoogleChatDialog — ngOnInit", () => {

   it("should initialize message from username and viewsheetName when viewsheetId is provided", async () => {
      const { comp } = await renderComponent({
         viewsheetId: "1^2^__NULL__^test-dashboard",
         viewsheetName: "My Dashboard",
         username: "alice",
      });

      expect(comp.form.get("message")!.value).toBe(
         "alice _#(js:em.settings.share.message.dashboard) My Dashboard."
      );
   });

   it("should leave message undefined when viewsheetId is empty", async () => {
      const { comp } = await renderComponent({ viewsheetId: "" });

      // viewsheetId is falsy → message variable stays undefined → form invalid
      expect(comp.form.get("message")!.value).toBeUndefined();
   });

});

// ---------------------------------------------------------------------------
// Group 2 — ok(): form guard + HTTP success/error [Risk 3]
// ---------------------------------------------------------------------------

describe("ShareGoogleChatDialog — ok()", () => {

   it("should not call HTTP when form is invalid (empty message)", async () => {
      const { comp } = await renderComponent();
      comp.form.get("message")!.setValue("");

      comp.ok();

      // Synchronous early return — loading stays false (set synchronously before HTTP)
      // loading=false is the meaningful guard: if the form guard were removed, loading would
      // be set to true before HTTP fires. httpCalled would still be false (HTTP is async),
      // so synchronous httpCalled assertions cannot catch that regression.
      expect(comp.loading).toBe(false);
   });

   it("should set loading=true when ok() is called with a valid form", async () => {
      const { comp } = await renderComponent();
      comp.ok();
      // loading is set synchronously before the HTTP subscribe
      expect(comp.loading).toBe(true);
   });

   it("should emit onCommit after successful HTTP response", async () => {
      const { comp } = await renderComponent();
      const onCommitSpy = vi.fn();
      comp.onCommit.subscribe(onCommitSpy);

      comp.ok();

      await waitFor(() => expect(onCommitSpy).toHaveBeenCalledTimes(1));
   });

   it("should show error dialog and reset loading=false on HTTP error", async () => {
      const { comp } = await renderComponent();
      comp.loading = true; // simulate in-flight state

      // Bypass the HTTP layer: calling handleError directly avoids the Zone.js
      // unhandled rejection caused by the subscribe() having no error handler when
      // catchError returns throwError(). The catchError wiring is standard RxJS;
      // this test verifies the handleError side-effects (dialog + loading flag).
      (comp as any).handleError(new HttpErrorResponse({ status: 500, statusText: "Error" }));

      expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1);
      expect(MessageDialog.lastMessage).toBe("Failed to post message to chat.");
      expect(comp.loading).toBe(false);
   });

});

// ---------------------------------------------------------------------------
// Group 3 — cancel() [baseline]
// ---------------------------------------------------------------------------

describe("ShareGoogleChatDialog — cancel()", () => {

   it("should emit onCancel when cancel() is called", async () => {
      const { comp } = await renderComponent();
      const onCancelSpy = vi.fn();
      comp.onCancel.subscribe(onCancelSpy);

      comp.cancel();

      expect(onCancelSpy).toHaveBeenCalledTimes(1);
   });

});
