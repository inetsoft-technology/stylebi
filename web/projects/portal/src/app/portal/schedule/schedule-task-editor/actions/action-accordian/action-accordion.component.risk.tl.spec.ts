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
 * ActionAccordion — Pass 2: Risk tests (dialog flows, HTTP interactions).
 *
 * Covers:
 *   - addBookmark duplicate guard → showMessageDialog
 *   - modifyBookmark duplicate guard → showMessageDialog
 *   - addFile path-validation errors → showMessageDialog (separator, invalid chars)
 *   - addFile duplicate saveString → showMessageDialog (diskFileDuplicate)
 *   - checkSameFormatFile confirm (ok → replaces entry, cancel → keeps entry)
 *   - openEmailDialogNotify: HTTP isSelfOrgUser + template-ref modal commit/cancel
 *   - openEmailDialogTo: deliverEmailsEnabled=false guard; commit updates action.to
 *   - openEmailDialogCC: deliverEmailsEnabled=false guard; commit updates ccAddress / bccAddress
 *
 * HTTP interception: MSW inline server.use() per test for
 *   GET [wildcard]/api/portal/schedule/isSelfOrgUser
 *
 * Dialog interception: modalMock (NgbModal stub) set up via mockModalOk, mockModalCancel,
 * mockModalEmail, and mockModalEmailCancel helpers.
 */

import { http, HttpResponse } from "msw";
import { waitFor } from "@testing-library/angular";
import { Subject } from "rxjs";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@test-mocks/server";
import { MessageDialog } from "../../../../../widget/dialog/message-dialog/message-dialog.component";
import {
   makeAction,
   makeBookmark,
   makeModel,
   modalMock,
   renderActionAccordion,
   resetMocks,
} from "./action-accordion.component.test-helpers";

// ── Per-test modal helpers ─────────────────────────────────────────────────────

function mockModalOk(): void {
   modalMock.open.mockImplementationOnce(() => ({
      result: Promise.resolve("ok"),
      componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
}

function mockModalCancel(): void {
   modalMock.open.mockImplementationOnce(() => ({
      result: Promise.resolve("cancel"),
      componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
}

function mockModalEmail(emails: string): void {
   modalMock.open.mockImplementationOnce(() => ({
      result: Promise.resolve({ emails }),
      componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
}

function mockModalEmailCancel(): void {
   modalMock.open.mockImplementationOnce(() => ({
      result: Promise.reject("cancel"),
      componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
}

// ── Shared setup ──────────────────────────────────────────────────────────────

beforeEach(() => {
   resetMocks();
   MessageDialog.lastMessage = null;
   MessageDialog.lastMessageTS = 0;
});
afterEach(() => vi.restoreAllMocks());

// ── Group 1: addBookmark duplicate guard ──────────────────────────────────────

describe("ActionAccordion — addBookmark duplicate guard", () => {

   it("opens error dialog when selected bookmark already exists in list", async () => {
      const bm = makeBookmark("BM1");
      const action = makeAction({ bookmarks: [bm] });
      const { comp } = await renderActionAccordion({ action, bookmarks: [bm] });
      comp.selectedBookmark = bm;

      comp.addBookmark();

      expect(modalMock.open).toHaveBeenCalledWith(MessageDialog, expect.any(Object));
      expect(modalMock.open).toHaveBeenCalledTimes(1);
   });

   it("does not push to bookmarkList or action.bookmarks when duplicate", async () => {
      const bm = makeBookmark("BM1");
      const action = makeAction({ bookmarks: [bm] });
      const { comp } = await renderActionAccordion({ action, bookmarks: [bm] });
      comp.bookmarkList = ["BM1"];
      comp.selectedBookmark = bm;

      comp.addBookmark();

      expect(comp.action.bookmarks).toHaveLength(1);
      expect(comp.bookmarkList).toHaveLength(1);
   });
});

// ── Group 2: modifyBookmark duplicate guard ───────────────────────────────────

describe("ActionAccordion — modifyBookmark duplicate guard", () => {

   it("opens error dialog when modified bookmark conflicts with existing entry", async () => {
      const bm1 = makeBookmark("BM1");
      const bm2 = makeBookmark("BM2");
      const action = makeAction({ bookmarks: [bm1, bm2] });
      const { comp } = await renderActionAccordion({ action, bookmarks: [bm1, bm2] });
      comp.bookmarkList = ["BM1", "BM2"];
      comp.selectedBookmarkListIndex = 0;
      comp.selectedBookmark = bm2;    // same name as existing bm2 at slot 1 → duplicate

      comp.modifyBookmark();

      expect(modalMock.open).toHaveBeenCalledWith(MessageDialog, expect.any(Object));
      expect(modalMock.open).toHaveBeenCalledTimes(1);
   });

   it("leaves original bookmark unchanged when duplicate is detected", async () => {
      const bm1 = makeBookmark("BM1");
      const bm2 = makeBookmark("BM2");
      const action = makeAction({ bookmarks: [bm1, bm2] });
      const { comp } = await renderActionAccordion({ action, bookmarks: [bm1, bm2] });
      comp.bookmarkList = ["BM1", "BM2"];
      comp.selectedBookmarkListIndex = 0;
      comp.selectedBookmark = bm2;

      comp.modifyBookmark();

      expect(comp.action.bookmarks[0]).toBe(bm1);
   });
});

// ── Group 3: addFile — path-validation errors ─────────────────────────────────

describe("ActionAccordion — addFile path-validation errors", () => {

   it("shows error dialog when path ends with a path separator", async () => {
      const { comp } = await renderActionAccordion();
      comp.filePath = "output/";
      comp.saveStrings = [];

      comp.addFile();

      expect(modalMock.open).toHaveBeenCalledWith(MessageDialog, expect.any(Object));
   });

   it("shows error dialog when path contains invalid characters", async () => {
      const { comp } = await renderActionAccordion();
      comp.filePath = 'report"output.xlsx';
      comp.saveStrings = [];

      comp.addFile();

      expect(modalMock.open).toHaveBeenCalledWith(MessageDialog, expect.any(Object));
   });

   it("shows error dialog for malformed ftp:// path", async () => {
      const { comp } = await renderActionAccordion();
      comp.filePath = "ftp://invalid";    // missing required /<filename> part
      comp.saveStrings = [];

      comp.addFile();

      expect(modalMock.open).toHaveBeenCalledWith(MessageDialog, expect.any(Object));
   });

   it("does not push to filePaths when path is invalid", async () => {
      const action = makeAction({ filePaths: [], saveFormats: [], serverFilePaths: [] });
      const { comp } = await renderActionAccordion({ action });
      comp.filePath = "output/";
      comp.saveStrings = [];

      comp.addFile();

      expect(comp.action.filePaths).toHaveLength(0);
   });
});

// ── Group 4: addFile — duplicate saveString ───────────────────────────────────

describe("ActionAccordion — addFile duplicate saveString", () => {

   it("shows diskFileDuplicate error dialog when path and format already in list", async () => {
      const model = makeModel({ vsSaveFileFormats: [{ type: "0", label: "Excel" }] });
      const action = makeAction({
         filePaths: ["report.xlsx"],
         saveFormats: ["0"],
         serverFilePaths: [{
            path: "report.xlsx", ftp: false, useCredential: false,
            secretId: null, username: null, password: null, oldFormat: -1,
         }],
      });
      const { comp } = await renderActionAccordion({ model, action });
      comp.filePath = "report.xlsx";
      comp.saveStrings = ["report.xlsx - Excel"];
      comp.saveFormat = "0";

      comp.addFile();

      expect(modalMock.open).toHaveBeenCalledWith(MessageDialog, expect.any(Object));
   });

   it("does not push to filePaths when duplicate path+format", async () => {
      const model = makeModel({ vsSaveFileFormats: [{ type: "0", label: "Excel" }] });
      const action = makeAction({
         filePaths: ["report.xlsx"],
         saveFormats: ["0"],
         serverFilePaths: [{
            path: "report.xlsx", ftp: false, useCredential: false,
            secretId: null, username: null, password: null, oldFormat: -1,
         }],
      });
      const { comp } = await renderActionAccordion({ model, action });
      comp.filePath = "report.xlsx";
      comp.saveStrings = ["report.xlsx - Excel"];
      comp.saveFormat = "0";

      comp.addFile();

      expect(comp.action.filePaths).toHaveLength(1);
   });
});

// ── Group 5: checkSameFormatFile — confirm / cancel ───────────────────────────

describe("ActionAccordion — checkSameFormatFile confirm/cancel", () => {

   it("replaces the existing same-format entry when user confirms", async () => {
      const model = makeModel({ vsSaveFileFormats: [{ type: "0", label: "Excel" }] });
      const action = makeAction({
         filePaths: ["old.xlsx"],
         saveFormats: ["0"],
         serverFilePaths: [{
            path: "old.xlsx", ftp: false, useCredential: false,
            secretId: null, username: null, password: null, oldFormat: -1,
         }],
      });
      const { comp } = await renderActionAccordion({ model, action });
      comp.saveStrings = ["old.xlsx - Excel"];
      comp.filePath = "new.xlsx";        // different path, same format
      comp.saveFormat = "0";
      mockModalOk();

      comp.addFile();

      await waitFor(() => {
         expect(comp.action.filePaths[0]).toBe("new.xlsx");
      });
      expect(comp.saveStrings[0]).toContain("new.xlsx");
   });

   it("opens the confirm dialog exactly once when same format is found", async () => {
      const model = makeModel({ vsSaveFileFormats: [{ type: "0", label: "Excel" }] });
      const action = makeAction({
         filePaths: ["old.xlsx"],
         saveFormats: ["0"],
         serverFilePaths: [{
            path: "old.xlsx", ftp: false, useCredential: false,
            secretId: null, username: null, password: null, oldFormat: -1,
         }],
      });
      const { comp } = await renderActionAccordion({ model, action });
      comp.saveStrings = ["old.xlsx - Excel"];
      comp.filePath = "new.xlsx";
      comp.saveFormat = "0";
      mockModalOk();

      comp.addFile();

      await waitFor(() => expect(modalMock.open).toHaveBeenCalled());
      expect(modalMock.open).toHaveBeenCalledWith(MessageDialog, expect.any(Object));
      expect(modalMock.open).toHaveBeenCalledTimes(1);
   });

   it("keeps the original entry when user cancels the same-format confirm", async () => {
      const model = makeModel({ vsSaveFileFormats: [{ type: "0", label: "Excel" }] });
      const action = makeAction({
         filePaths: ["old.xlsx"],
         saveFormats: ["0"],
         serverFilePaths: [{
            path: "old.xlsx", ftp: false, useCredential: false,
            secretId: null, username: null, password: null, oldFormat: -1,
         }],
      });
      const { comp } = await renderActionAccordion({ model, action });
      comp.saveStrings = ["old.xlsx - Excel"];
      comp.filePath = "new.xlsx";
      comp.saveFormat = "0";
      mockModalCancel();

      comp.addFile();

      await waitFor(() => expect(modalMock.open).toHaveBeenCalled());
      expect(comp.action.filePaths[0]).toBe("old.xlsx");
      expect(comp.saveStrings[0]).toBe("old.xlsx - Excel");
   });
});

// ── Group 6: openEmailDialogNotify ────────────────────────────────────────────

describe("ActionAccordion — openEmailDialogNotify", () => {

   it("updates action.notifications with committed email addresses", async () => {
      server.use(
         http.get("*/api/portal/schedule/isSelfOrgUser", () => HttpResponse.json(true))
      );
      mockModalEmail("notify@example.com");
      const { comp } = await renderActionAccordion();

      comp.openEmailDialogNotify();

      await waitFor(() => {
         expect(comp.action.notifications).toBe("notify@example.com");
      });
   });

   it("sets initialAddresses from action.notifications before opening", async () => {
      server.use(
         http.get("*/api/portal/schedule/isSelfOrgUser", () => HttpResponse.json(true))
      );
      mockModalEmail("other@example.com");
      const action = makeAction({ notifications: "existing@example.com" });
      const { comp } = await renderActionAccordion({ action });

      comp.openEmailDialogNotify();

      await waitFor(() => expect(modalMock.open).toHaveBeenCalled());
      expect(comp.initialAddresses).toBe("existing@example.com");
   });

   it("does not update notifications when email dialog is cancelled", async () => {
      server.use(
         http.get("*/api/portal/schedule/isSelfOrgUser", () => HttpResponse.json(true))
      );
      mockModalEmailCancel();
      const action = makeAction({ notifications: "original@example.com" });
      const { comp } = await renderActionAccordion({ action });

      comp.openEmailDialogNotify();

      await waitFor(() => expect(modalMock.open).toHaveBeenCalled());
      expect(comp.action.notifications).toBe("original@example.com");
   });

   it("strips whitespace from committed addresses", async () => {
      server.use(
         http.get("*/api/portal/schedule/isSelfOrgUser", () => HttpResponse.json(true))
      );
      mockModalEmail("a@b.com, c@d.com");
      const { comp } = await renderActionAccordion();

      comp.openEmailDialogNotify();

      await waitFor(() => {
         expect(comp.action.notifications).toBe("a@b.com,c@d.com");
      });
   });
});

// ── Group 7: openEmailDialogTo ────────────────────────────────────────────────

describe("ActionAccordion — openEmailDialogTo", () => {

   it("is a no-op when deliverEmailsEnabled=false", async () => {
      const action = makeAction({ deliverEmailsEnabled: false });
      const { comp } = await renderActionAccordion({ action });

      comp.openEmailDialogTo();

      expect(modalMock.open).not.toHaveBeenCalled();
   });

   it("updates action.to with committed email addresses", async () => {
      server.use(
         http.get("*/api/portal/schedule/isSelfOrgUser", () => HttpResponse.json(true))
      );
      mockModalEmail("to@example.com");
      const action = makeAction({ deliverEmailsEnabled: true });
      const { comp } = await renderActionAccordion({ action });

      comp.openEmailDialogTo();

      await waitFor(() => {
         expect(comp.action.to).toBe("to@example.com");
      });
   });

   it("sets initialAddresses from action.to before opening", async () => {
      server.use(
         http.get("*/api/portal/schedule/isSelfOrgUser", () => HttpResponse.json(true))
      );
      mockModalEmail("other@example.com");
      const action = makeAction({ deliverEmailsEnabled: true, to: "current@example.com" });
      const { comp } = await renderActionAccordion({ action });

      comp.openEmailDialogTo();

      await waitFor(() => expect(modalMock.open).toHaveBeenCalled());
      expect(comp.initialAddresses).toBe("current@example.com");
   });
});

// ── Group 8: openEmailDialogCC ────────────────────────────────────────────────

describe("ActionAccordion — openEmailDialogCC", () => {

   it("is a no-op when deliverEmailsEnabled=false", async () => {
      const action = makeAction({ deliverEmailsEnabled: false });
      const { comp } = await renderActionAccordion({ action });

      comp.openEmailDialogCC();

      expect(modalMock.open).not.toHaveBeenCalled();
   });

   it("updates ccAddress when bcc=false on commit", async () => {
      server.use(
         http.get("*/api/portal/schedule/isSelfOrgUser", () => HttpResponse.json(true))
      );
      mockModalEmail("cc@example.com");
      const action = makeAction({ deliverEmailsEnabled: true, ccAddress: "" });
      const { comp } = await renderActionAccordion({ action });

      comp.openEmailDialogCC(false);

      await waitFor(() => {
         expect(comp.action.ccAddress).toBe("cc@example.com");
      });
   });

   it("updates bccAddress when bcc=true on commit", async () => {
      server.use(
         http.get("*/api/portal/schedule/isSelfOrgUser", () => HttpResponse.json(true))
      );
      mockModalEmail("bcc@example.com");
      const action = makeAction({ deliverEmailsEnabled: true, bccAddress: "" });
      const { comp } = await renderActionAccordion({ action });

      comp.openEmailDialogCC(true);

      await waitFor(() => {
         expect(comp.action.bccAddress).toBe("bcc@example.com");
      });
   });

   it("sets initialAddresses from ccAddress when bcc=false", async () => {
      server.use(
         http.get("*/api/portal/schedule/isSelfOrgUser", () => HttpResponse.json(true))
      );
      mockModalEmail("new@example.com");
      const action = makeAction({ deliverEmailsEnabled: true, ccAddress: "old-cc@example.com" });
      const { comp } = await renderActionAccordion({ action });

      comp.openEmailDialogCC(false);

      await waitFor(() => expect(modalMock.open).toHaveBeenCalled());
      expect(comp.initialAddresses).toBe("old-cc@example.com");
   });

   it("sets initialAddresses from bccAddress when bcc=true", async () => {
      server.use(
         http.get("*/api/portal/schedule/isSelfOrgUser", () => HttpResponse.json(true))
      );
      mockModalEmail("new@example.com");
      const action = makeAction({ deliverEmailsEnabled: true, bccAddress: "old-bcc@example.com" });
      const { comp } = await renderActionAccordion({ action });

      comp.openEmailDialogCC(true);

      await waitFor(() => expect(modalMock.open).toHaveBeenCalled());
      expect(comp.initialAddresses).toBe("old-bcc@example.com");
   });
});
