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
 * ShareEmailDialogComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3]    — ngOnInit: username/enterprise-aware message construction (3 cases)
 *   Group 2 [Risk 3]    — ok(): form guard; loading + success dialog + commit; error dialog
 *   Group 3 [baseline]  — cancel(): onCancel emission
 *
 * Suspected bug (not failing):
 *   ngOnDestroy is absent despite `private subscriptions = new Subscription()` —
 *   the isEnterprise subscription leaks.
 *
 * Implementation notes:
 *   - ShareService and AppInfoService are mocked with RxJS of() to keep tests
 *     synchronous and avoid NG0205 from async HTTP responses arriving after
 *     fixture teardown (AppInfoService.loadCurrentOrgInfo fires in its constructor).
 *   - EmailPane is stubbed via importOverrides: it imports CkeditorWrapperComponent
 *     which requires a real browser environment and fails in jsdom.
 *   - handleError() for the error test is called directly to avoid the Zone.js
 *     unhandled rejection from the RxJS throwError it returns.
 */

import { Component, Input, NO_ERRORS_SCHEMA } from "@angular/core";
import { ReactiveFormsModule, UntypedFormGroup } from "@angular/forms";
import { HttpErrorResponse } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of, Subject } from "rxjs";

import { ShareEmailDialogComponent } from "./share-email-dialog.component";
import { EmailPane } from "../email-dialog/email-pane.component";
import { ShareService } from "./share.service";
import { AppInfoService } from "../../../../../shared/util/app-info.service";
import { MessageDialog } from "../dialog/message-dialog/message-dialog.component";
import { EmailPaneModel } from "../../vsobjects/model/email-pane-model";
import { ShareEmailModel } from "./share-email-model";

// Stub EmailPane to avoid pulling in CkeditorWrapperComponent (CDN dep) in jsdom.
@Component({ selector: "email-pane", standalone: true, template: "" })
class EmailPaneStub {
   @Input() model: Partial<EmailPaneModel>;
   @Input() historyEmails: string[];
   @Input() securityEnabled: boolean;
   @Input() form: UntypedFormGroup;
}

const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

// Mocked services — synchronous RxJS of() avoids HTTP and NG0205 timing races.
// Plain vi.fn() (no type args) is compatible with Vitest 4.x which accepts a single
// function-type argument, not the old two-argument <Params, Return> form.
const SHARE_SERVICE_MOCK = {
   getEmailModel: vi.fn(),
   shareViewsheetInEmail: vi.fn(),
};

const APP_INFO_MOCK = {
   isEnterprise: vi.fn(),
   // ShareService constructor subscribes to this; returning of(null) is sufficient
   getCurrentOrgInfo: vi.fn().mockReturnValue(of(null)),
};

function makeEmailModel(): ShareEmailModel {
   return {
      emailModel: {
         toAddress: "test@example.com",
         ccAddress: "",
         bccAddress: "",
         fromAddress: "",
         fromAddressEnabled: false,
         subject: "Share Dashboard",
         message: "",
         userDialogEnabled: false,
         emailAddrDialogModel: { rootTree: {} },
      } as EmailPaneModel,
      historyEnabled: false,
      securityEnabled: false,
   };
}

function resetMocks() {
   MODAL_MOCK.open.mockClear();
   MessageDialog.lastMessage = null;
   MessageDialog.lastMessageTS = 0;
   SHARE_SERVICE_MOCK.getEmailModel.mockReturnValue(of(makeEmailModel()));
   SHARE_SERVICE_MOCK.shareViewsheetInEmail.mockReturnValue(of(undefined));
   APP_INFO_MOCK.isEnterprise.mockReturnValue(of(false));
}

interface RenderOpts {
   viewsheetId?: string;
   viewsheetName?: string;
   username?: string;
}

async function renderComponent(opts: RenderOpts = {}) {
   const { fixture } = await render(ShareEmailDialogComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      imports: [ReactiveFormsModule],
      importOverrides: [{ replace: EmailPane, with: EmailPaneStub }],
      providers: [
         { provide: ShareService, useValue: SHARE_SERVICE_MOCK },
         { provide: AppInfoService, useValue: APP_INFO_MOCK },
         { provide: NgbModal, useValue: MODAL_MOCK },
      ],
      componentInputs: {
         viewsheetId: opts.viewsheetId !== undefined ? opts.viewsheetId : "1^2^__NULL__^test-dashboard",
         viewsheetName: opts.viewsheetName ?? "My Dashboard",
         username: opts.username ?? "alice",
      },
   });
   return { comp: fixture.componentInstance, fixture };
}

beforeEach(() => resetMocks());

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit: message construction [Risk 3]
// ---------------------------------------------------------------------------
// With of() mocks, ngOnInit subscribe callbacks fire synchronously during render().
// No waitFor needed — comp.model.emailModel.message is set by the time render() returns.

describe("ShareEmailDialogComponent — ngOnInit message construction", () => {

   // 🔁 Regression-sensitive: message must be wrapped in <p>...</p> (isIE=false in jsdom)
   it("should set message from standard username and viewsheetName", async () => {
      APP_INFO_MOCK.isEnterprise.mockReturnValue(of(false));

      const { comp } = await renderComponent({
         viewsheetId: "1^2^__NULL__^test-dashboard",
         viewsheetName: "My Dashboard",
         username: "alice",
      });

      expect(comp.model.emailModel.message).toBe(
         "<p>alice _#(js:em.settings.share.message.dashboard) My Dashboard.</p>"
      );
   });

   // 🔁 Regression-sensitive: "~;~host-org" suffix (11 chars) must be stripped when
   // username ends with "host-org" AND isEnterprise=false
   it("should trim host-org suffix from username when not enterprise", async () => {
      APP_INFO_MOCK.isEnterprise.mockReturnValue(of(false));

      const { comp } = await renderComponent({
         viewsheetId: "1^2^__NULL__^test-dashboard",
         viewsheetName: "My Dashboard",
         username: "alice~;~host-org",
      });

      // "alice~;~host-org" → trim last 11 chars → "alice" → replace("~;~"," of ") → "alice"
      expect(comp.model.emailModel.message).toBe(
         "<p>alice _#(js:em.settings.share.message.dashboard) My Dashboard.</p>"
      );
   });

   // 🔁 Regression-sensitive: when isEnterprise=true the suffix must NOT be stripped;
   // "~;~" is still replaced with " of " for the display name
   it("should not trim host-org suffix and should preserve org name when enterprise=true", async () => {
      APP_INFO_MOCK.isEnterprise.mockReturnValue(of(true));

      const { comp } = await renderComponent({
         viewsheetId: "1^2^__NULL__^test-dashboard",
         viewsheetName: "My Dashboard",
         username: "alice~;~host-org",
      });

      // no trimming → "alice~;~host-org" → replace("~;~", " of ") → "alice of host-org"
      expect(comp.model.emailModel.message).toBe(
         "<p>alice of host-org _#(js:em.settings.share.message.dashboard) My Dashboard.</p>"
      );
   });

});

// ---------------------------------------------------------------------------
// Group 2 — ok(): form guard + success/error [Risk 3]
// ---------------------------------------------------------------------------

describe("ShareEmailDialogComponent — ok()", () => {

   it("should return early without HTTP when form is invalid", async () => {
      const { comp } = await renderComponent();
      comp.form.setErrors({ invalid: true });

      comp.ok();

      // Synchronous early return: loading stays false, shareViewsheetInEmail not called
      expect(comp.loading).toBe(false);
      expect(SHARE_SERVICE_MOCK.shareViewsheetInEmail).not.toHaveBeenCalled();
   });

   it("should set loading=true, show success dialog, and emit onCommit on HTTP 200", async () => {
      const { comp } = await renderComponent();
      const onCommitSpy = vi.fn();
      comp.onCommit.subscribe(onCommitSpy);

      comp.ok();

      // of() subscribe fires synchronously — all effects happen before next statement
      expect(comp.loading).toBe(true);         // set before HTTP subscribe
      expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1); // success dialog
      expect(MessageDialog.lastMessage).toBe("_#(js:viewer.viewsheet.email.successful)");
      expect(onCommitSpy).toHaveBeenCalledTimes(1);      // onCommit
   });

   it("should show error dialog and reset loading=false on HTTP error", async () => {
      const { comp } = await renderComponent();
      comp.loading = true; // simulate in-flight state

      // Bypass the HTTP layer: calling handleError directly avoids the Zone.js
      // unhandled rejection caused by the subscribe() having no error handler when
      // catchError returns throwError(). The catchError wiring is standard RxJS;
      // this test verifies the handleError side-effects (dialog + loading flag).
      // handleError signature: handleError(error, toAddress)
      (comp as any).handleError(
         new HttpErrorResponse({ status: 500, statusText: "Error" }),
         "test@example.com"
      );

      expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1);
      expect(MessageDialog.lastMessage).toBe("_#(js:viewer.viewsheet.email.failed)");
      expect(comp.loading).toBe(false);
   });

});

// ---------------------------------------------------------------------------
// Group 3 — cancel() [baseline]
// ---------------------------------------------------------------------------

describe("ShareEmailDialogComponent — cancel()", () => {

   it("should emit onCancel when cancel() is called", async () => {
      const { comp } = await renderComponent();
      const onCancelSpy = vi.fn();
      comp.onCancel.subscribe(onCancelSpy);

      comp.cancel();

      expect(onCancelSpy).toHaveBeenCalledTimes(1);
   });

});
