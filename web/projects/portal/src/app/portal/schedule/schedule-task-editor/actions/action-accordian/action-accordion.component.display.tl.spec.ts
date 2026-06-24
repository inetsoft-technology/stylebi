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
 * ActionAccordion — Pass 3: Display tests (DOM structure, visibility gates, disabled states).
 *
 * Button disambiguation strategy:
 *   Bookmarks tests  — model.saveToDiskEnabled=false removes the Save-to-Disk Add/Modify buttons.
 *   Save-to-disk tests — actionType=ReportAction removes the Bookmarks card entirely.
 * This avoids needing within() for every lookup.
 *
 * Mocking strategy: ScheduleUsersService and NgbModal are provided as vi.fn() mocks
 * (see test-helpers.ts). No MSW is used in this file — DOM assertions do not require
 * live HTTP interception. The sibling risk spec (action-accordion.component.risk.tl.spec.ts)
 * uses MSW for the HTTP + modal dialog flows; keeping them separate avoids accidental
 * NG0205 errors from constructor-fired HTTP side effects in this file's render calls.
 */

import { screen } from "@testing-library/angular";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
   makeAction,
   makeHighlight,
   makeModel,
   renderActionAccordion,
   resetMocks,
} from "./action-accordion.component.test-helpers";

beforeEach(() => resetMocks());
afterEach(() => vi.restoreAllMocks());

// ── Group 1: Top-level shell ──────────────────────────────────────────────────

describe("ActionAccordion — top-level shell", () => {

   it("renders .action-accordion-content once action and form are initialized", async () => {
      await renderActionAccordion();
      expect(document.querySelector(".action-accordion-content")).not.toBeNull();
   });
});

// ── Group 2: Bookmarks card ───────────────────────────────────────────────────

describe("ActionAccordion — Bookmarks card visibility", () => {

   it("renders the Bookmarks section for ViewsheetAction", async () => {
      const action = makeAction({ actionType: "ViewsheetAction" });
      await renderActionAccordion({ action, model: makeModel({ saveToDiskEnabled: false }) });
      expect(screen.queryByText("_#(Bookmarks)")).not.toBeNull();
   });

   it("does not render the Bookmarks section for ReportAction", async () => {
      const action = makeAction({ actionType: "ReportAction" });
      await renderActionAccordion({ action });
      expect(screen.queryByText("_#(Bookmarks)")).toBeNull();
   });

   it("Add bookmark button is disabled when bookmarks array is empty", async () => {
      const action = makeAction({ actionType: "ViewsheetAction" });
      const { fixture } = await renderActionAccordion({
         action,
         bookmarks: [],
         model: makeModel({ saveToDiskEnabled: false }),
      });
      fixture.detectChanges();
      expect(screen.getByRole("button", { name: "_#(Add)" })).toBeDisabled();
   });

   it("Add bookmark button is enabled when bookmarks array has at least one entry", async () => {
      const action = makeAction({ actionType: "ViewsheetAction" });
      const { fixture } = await renderActionAccordion({
         action,
         bookmarks: [{ label: "BM1", type: 0, name: "BM1" } as any],
         model: makeModel({ saveToDiskEnabled: false }),
      });
      fixture.detectChanges();
      expect(screen.getByRole("button", { name: "_#(Add)" })).not.toBeDisabled();
   });

   it("Modify bookmark button is disabled when selectedBookmarkListIndex < 0", async () => {
      const action = makeAction({ actionType: "ViewsheetAction" });
      const { comp, fixture } = await renderActionAccordion({
         action,
         bookmarks: [{ label: "BM1", type: 0, name: "BM1" } as any],
         model: makeModel({ saveToDiskEnabled: false }),
      });
      comp.selectedBookmarkListIndex = -1;
      fixture.detectChanges();
      expect(screen.getByRole("button", { name: "_#(Modify)" })).toBeDisabled();
   });

   it("Remove bookmark button is disabled when bookmarkList has only one item", async () => {
      const action = makeAction({ actionType: "ViewsheetAction" });
      const { comp, fixture } = await renderActionAccordion({
         action,
         bookmarks: [{ label: "BM1", type: 0, name: "BM1" } as any],
         model: makeModel({ saveToDiskEnabled: false }),
      });
      comp.bookmarkList = ["BM1"];
      comp.selectedBookmarkListIndex = 0;
      fixture.detectChanges();
      expect(screen.getByRole("button", { name: "_#(Remove)" })).toBeDisabled();
   });

   it("Remove bookmark button is enabled when bookmarkList has more than one item", async () => {
      const action = makeAction({ actionType: "ViewsheetAction" });
      const { comp, fixture } = await renderActionAccordion({
         action,
         bookmarks: [
            { label: "BM1", type: 0, name: "BM1" } as any,
            { label: "BM2", type: 0, name: "BM2" } as any,
         ],
         model: makeModel({ saveToDiskEnabled: false }),
      });
      comp.bookmarkList = ["BM1", "BM2"];
      comp.selectedBookmarkListIndex = 0;
      fixture.detectChanges();
      expect(screen.getByRole("button", { name: "_#(Remove)" })).not.toBeDisabled();
   });
});

// ── Group 3: Notification card ────────────────────────────────────────────────

describe("ActionAccordion — Notification card visibility", () => {

   it("renders the notification checkbox when notificationEmailEnabled=true", async () => {
      const model = makeModel({ notificationEmailEnabled: true });
      await renderActionAccordion({ model });
      expect(document.getElementById("notification")).not.toBeNull();
   });

   it("does not render the notification card when notificationEmailEnabled=false", async () => {
      const model = makeModel({ notificationEmailEnabled: false });
      await renderActionAccordion({ model });
      expect(document.getElementById("notification")).toBeNull();
   });

   it("notify email input is hidden when notificationEnabled=false", async () => {
      const action = makeAction({ notificationEnabled: false });
      const model = makeModel({ notificationEmailEnabled: true });
      await renderActionAccordion({ action, model });
      expect(document.getElementById("notify")).toBeNull();
   });

   it("notify email input appears when notificationEnabled=true", async () => {
      const action = makeAction({ notificationEnabled: true });
      const model = makeModel({ notificationEmailEnabled: true });
      await renderActionAccordion({ action, model });
      expect(document.getElementById("notify")).not.toBeNull();
   });

   it("'Notify only if failed' checkbox is visible when notificationEnabled=true", async () => {
      const action = makeAction({ notificationEnabled: true });
      const model = makeModel({ notificationEmailEnabled: true });
      await renderActionAccordion({ action, model });
      expect(document.getElementById("notifyFailed")).not.toBeNull();
   });
});

// ── Group 4: Email delivery card ──────────────────────────────────────────────

describe("ActionAccordion — Email delivery card visibility", () => {

   it("renders the deliver-email checkbox when emailDeliveryEnabled=true", async () => {
      const model = makeModel({ emailDeliveryEnabled: true });
      await renderActionAccordion({ model });
      expect(document.getElementById("deliverEmail")).not.toBeNull();
   });

   it("does not render the deliver-email card when emailDeliveryEnabled=false", async () => {
      const model = makeModel({ emailDeliveryEnabled: false });
      await renderActionAccordion({ model });
      expect(document.getElementById("deliverEmail")).toBeNull();
   });

   it("From/To/CC/BCC inputs are hidden when deliverEmailsEnabled=false", async () => {
      const action = makeAction({ deliverEmailsEnabled: false });
      const model = makeModel({ emailDeliveryEnabled: true });
      await renderActionAccordion({ action, model });
      expect(document.getElementById("from")).toBeNull();
      expect(document.querySelector("[formControlName='to']")).toBeNull();
      expect(document.querySelector("[formControlName='cc']")).toBeNull();
      expect(document.querySelector("[formControlName='bcc']")).toBeNull();
   });

   it("From/To/CC/BCC inputs are visible when deliverEmailsEnabled=true", async () => {
      const action = makeAction({ deliverEmailsEnabled: true });
      const model = makeModel({ emailDeliveryEnabled: true });
      await renderActionAccordion({ action, model });
      expect(document.getElementById("from")).not.toBeNull();
      expect(document.querySelector("[formControlName='to']")).not.toBeNull();
      expect(document.querySelector("[formControlName='cc']")).not.toBeNull();
      expect(document.querySelector("[formControlName='bcc']")).not.toBeNull();
   });

   it("'Bundled as zip' checkbox is disabled when format=HTML_BUNDLE", async () => {
      const action = makeAction({ deliverEmailsEnabled: true, format: "HTML_BUNDLE" });
      const model = makeModel({ emailDeliveryEnabled: true });
      const { fixture } = await renderActionAccordion({ action, model });
      fixture.detectChanges();
      expect(document.getElementById("zip")).toBeDisabled();
   });

   it("'Bundled as zip' checkbox is not disabled when format=Excel", async () => {
      const action = makeAction({ deliverEmailsEnabled: true, format: "Excel" });
      const model = makeModel({ emailDeliveryEnabled: true });
      const { fixture } = await renderActionAccordion({ action, model });
      fixture.detectChanges();
      expect(document.getElementById("zip")).not.toBeDisabled();
   });
});

// ── Group 5: dataSizeOptionVisible radios (email section) ─────────────────────

describe("ActionAccordion — Match Layout / Expand Components radios (email section)", () => {

   it("match / expand radios appear when dataSizeOptionVisible=true (ViewsheetAction, Excel)", async () => {
      const action = makeAction({
         actionType: "ViewsheetAction",
         deliverEmailsEnabled: true,
         format: "Excel",
      });
      const model = makeModel({ emailDeliveryEnabled: true });
      await renderActionAccordion({ action, model });
      expect(document.getElementById("match")).not.toBeNull();
      expect(document.getElementById("expand")).not.toBeNull();
   });

   it("match / expand radios are absent for ReportAction (isDashboard=false)", async () => {
      const action = makeAction({
         actionType: "ReportAction",
         deliverEmailsEnabled: true,
         format: "Excel",
      });
      const model = makeModel({ emailDeliveryEnabled: true });
      await renderActionAccordion({ action, model });
      expect(document.getElementById("match")).toBeNull();
      expect(document.getElementById("expand")).toBeNull();
   });

   it("'Only Data Elements' checkbox is visible when format=Excel and dataSizeOptionVisible=true", async () => {
      const action = makeAction({
         actionType: "ViewsheetAction",
         deliverEmailsEnabled: true,
         format: "Excel",
      });
      const model = makeModel({ emailDeliveryEnabled: true });
      await renderActionAccordion({ action, model });
      expect(document.getElementById("onlyDataComponents")).not.toBeNull();
   });

   it("'Only Data Elements' checkbox is absent when format=PDF", async () => {
      const action = makeAction({
         actionType: "ViewsheetAction",
         deliverEmailsEnabled: true,
         format: "PDF",
      });
      const model = makeModel({
         emailDeliveryEnabled: true,
         vsMailFormats: [{ type: "PDF", label: "PDF" }],
      });
      await renderActionAccordion({ action, model });
      expect(document.getElementById("onlyDataComponents")).toBeNull();
   });
});

// ── Group 6: Password / Confirm Password fields ───────────────────────────────

describe("ActionAccordion — Password / Confirm Password visibility", () => {

   it("Password fields are hidden when bundledAsZip=false and format=Excel (isPasswordDisable=true)", async () => {
      const action = makeAction({ deliverEmailsEnabled: true, bundledAsZip: false, format: "Excel" });
      const model = makeModel({ emailDeliveryEnabled: true, fipsMode: false });
      await renderActionAccordion({ action, model });
      expect(document.getElementById("password")).toBeNull();
      expect(document.getElementById("confirmPassword")).toBeNull();
   });

   it("Password fields are hidden when fipsMode=true", async () => {
      const action = makeAction({ deliverEmailsEnabled: true, bundledAsZip: true, format: "Excel" });
      const model = makeModel({ emailDeliveryEnabled: true, fipsMode: true });
      await renderActionAccordion({ action, model });
      expect(document.getElementById("password")).toBeNull();
      expect(document.getElementById("confirmPassword")).toBeNull();
   });

   it("Password fields are visible when bundledAsZip=true and fipsMode=false", async () => {
      const action = makeAction({ deliverEmailsEnabled: true, bundledAsZip: true, format: "Excel" });
      const model = makeModel({ emailDeliveryEnabled: true, fipsMode: false });
      await renderActionAccordion({ action, model });
      expect(document.getElementById("password")).not.toBeNull();
      expect(document.getElementById("confirmPassword")).not.toBeNull();
   });

   it("passwordsDoNotMatch error is visible when password and confirmPassword form controls differ", async () => {
      const action = makeAction({ deliverEmailsEnabled: true, bundledAsZip: true, format: "Excel" });
      const model = makeModel({ emailDeliveryEnabled: true, fipsMode: false });
      const { comp, fixture } = await renderActionAccordion({ action, model });
      comp.form.controls["password"].setValue("abc");
      comp.form.controls["confirmPassword"].setValue("xyz");
      fixture.detectChanges();
      expect(screen.queryByText("_#(em.changePassword.mustMatch)", { exact: false })).not.toBeNull();
   });
});

// ── Group 7: Save to Disk card ────────────────────────────────────────────────

describe("ActionAccordion — Save to Disk card visibility", () => {

   it("renders the Save to Disk checkbox when saveToDiskEnabled=true", async () => {
      const model = makeModel({ saveToDiskEnabled: true });
      await renderActionAccordion({ model });
      expect(document.getElementById("saveToServer")).not.toBeNull();
   });

   it("does not render the Save to Disk card when saveToDiskEnabled=false", async () => {
      const model = makeModel({ saveToDiskEnabled: false });
      await renderActionAccordion({ model });
      expect(document.getElementById("saveToServer")).toBeNull();
   });

   it("Path input is hidden when saveToServerEnabled=false", async () => {
      const action = makeAction({ actionType: "ReportAction", saveToServerEnabled: false });
      await renderActionAccordion({ action });
      expect(document.getElementById("path")).toBeNull();
   });

   it("Path input is visible when saveToServerEnabled=true", async () => {
      const action = makeAction({ actionType: "ReportAction", saveToServerEnabled: true });
      await renderActionAccordion({ action });
      expect(document.getElementById("path")).not.toBeNull();
   });

   it("Add file button is disabled when filePath is empty", async () => {
      const action = makeAction({ actionType: "ReportAction", saveToServerEnabled: true });
      const { comp, fixture } = await renderActionAccordion({ action });
      comp.filePath = "";
      fixture.detectChanges();
      expect(screen.getByRole("button", { name: "_#(Add)" })).toBeDisabled();
   });

   it("Delete file button is disabled when selectedFormatIndex < 0", async () => {
      const action = makeAction({ actionType: "ReportAction", saveToServerEnabled: true });
      const { comp, fixture } = await renderActionAccordion({ action });
      comp.selectedFormatIndex = -1;
      fixture.detectChanges();
      expect(screen.getByRole("button", { name: "_#(Delete)" })).toBeDisabled();
   });

   it("Delete file button is enabled when selectedFormatIndex >= 0", async () => {
      const action = makeAction({ actionType: "ReportAction", saveToServerEnabled: true });
      const { comp, fixture } = await renderActionAccordion({ action });
      comp.selectedFormatIndex = 0;
      fixture.detectChanges();
      expect(screen.getByRole("button", { name: "_#(Delete)" })).not.toBeDisabled();
   });

   it("file-warning alert is shown when saveStrings is empty and saveToServerEnabled=true", async () => {
      // makeAction default has saveFormats=[] → saveStrings=[] after ngOnChanges
      const action = makeAction({ actionType: "ReportAction", saveToServerEnabled: true });
      await renderActionAccordion({ action });
      expect(screen.queryByText("_#(viewer.schedule.action.saveFileWarning)", { exact: false })).not.toBeNull();
   });
});

// ── Group 8: showMatchAndExpand radios (Save section) ────────────────────────

describe("ActionAccordion — showMatchAndExpand radios (Save section)", () => {

   it("match2 / expand2 radios appear when showMatchAndExpand=true (ViewsheetAction, Excel format)", async () => {
      const action = makeAction({
         actionType: "ViewsheetAction",
         saveToServerEnabled: true,
         saveFormats: ["0"],    // "0" = Excel → not HTML, not VSCSV → showMatchAndExpand = isDashboard
         filePaths: ["r.xlsx"],
         serverFilePaths: [{
            path: "r.xlsx", ftp: false, useCredential: false,
            secretId: null, username: null, password: null, oldFormat: -1,
         }],
      });
      await renderActionAccordion({ action });
      expect(document.getElementById("match2")).not.toBeNull();
      expect(document.getElementById("expand2")).not.toBeNull();
   });

   it("match2 / expand2 radios are absent when isDashboard=false (ReportAction)", async () => {
      const action = makeAction({
         actionType: "ReportAction",
         saveToServerEnabled: true,
         saveFormats: ["0"],
         filePaths: ["r.xlsx"],
         serverFilePaths: [{
            path: "r.xlsx", ftp: false, useCredential: false,
            secretId: null, username: null, password: null, oldFormat: -1,
         }],
      });
      await renderActionAccordion({ action });
      expect(document.getElementById("match2")).toBeNull();
      expect(document.getElementById("expand2")).toBeNull();
   });
});

// ── Group 9: showMatchMessage HTML warning ────────────────────────────────────

describe("ActionAccordion — .html-match-message visibility", () => {

   it("html-match-message is visible when saveFormats mixes HTML and non-HTML entries", async () => {
      // FileTypes.HTML=5 → "5"; Excel save format → "0"
      // hasHtml=true + hasOther=true → showMatchMessage=true
      const action = makeAction({
         actionType: "ViewsheetAction",
         saveToServerEnabled: true,
         saveFormats: ["5", "0"],
         filePaths: ["r.html", "r.xlsx"],
         serverFilePaths: [
            { path: "r.html", ftp: false, useCredential: false, secretId: null, username: null, password: null, oldFormat: -1 },
            { path: "r.xlsx", ftp: false, useCredential: false, secretId: null, username: null, password: null, oldFormat: -1 },
         ],
      });
      await renderActionAccordion({ action });
      expect(document.querySelector(".html-match-message")).not.toBeNull();
   });

   it("html-match-message is absent when saveFormats has only Excel entries", async () => {
      const action = makeAction({
         actionType: "ViewsheetAction",
         saveToServerEnabled: true,
         saveFormats: ["0"],
         filePaths: ["r.xlsx"],
         serverFilePaths: [{
            path: "r.xlsx", ftp: false, useCredential: false,
            secretId: null, username: null, password: null, oldFormat: -1,
         }],
      });
      await renderActionAccordion({ action });
      expect(document.querySelector(".html-match-message")).toBeNull();
   });
});

// ── Group 10: Parameters section ─────────────────────────────────────────────

describe("ActionAccordion — Parameters section", () => {

   it("shows 'Required Parameters:' warning when missingParameters is non-empty", async () => {
      const action = makeAction({ parameters: [] });
      await renderActionAccordion({ action, requiredParameters: ["P1", "P2"] });
      expect(screen.queryByText("_#(Required Parameters):", { exact: false })).not.toBeNull();
   });

   it("does not show 'Required Parameters:' warning when all required params are present", async () => {
      const action = makeAction({
         parameters: [{ name: "P1", type: "string", value: null, array: false }],
      });
      await renderActionAccordion({ action, requiredParameters: ["P1"] });
      expect(screen.queryByText("_#(Required Parameters):", { exact: false })).toBeNull();
   });

   it("shows optional parameter names when optionalParameters is non-empty", async () => {
      await renderActionAccordion({ optionalParameters: ["OPT1", "OPT2"] });
      expect(screen.queryByText("Optional Parameters:", { exact: false })).not.toBeNull();
      expect(screen.queryByText("OPT1", { exact: false })).not.toBeNull();
   });

   it("does not show optional parameters section when optionalParameters is empty", async () => {
      await renderActionAccordion({ optionalParameters: [] });
      expect(screen.queryByText("Optional Parameters:", { exact: false })).toBeNull();
   });
});

// ── Group 11: Highlights / Alert card ────────────────────────────────────────

describe("ActionAccordion — Highlights (Alert) card visibility", () => {

   it("does not render the Alert card when highlights array is empty", async () => {
      await renderActionAccordion({ highlights: [] });
      expect(screen.queryByText("_#(Alert)")).toBeNull();
   });

   it("renders the Alert card label when highlights has entries", async () => {
      const action = makeAction({ actionType: "ViewsheetAction" });
      await renderActionAccordion({ action, highlights: [makeHighlight("T1", "H1")] });
      expect(screen.queryByText("_#(Alert)")).not.toBeNull();
   });

   it("renders a row per highlight element when highlightsSelected=true", async () => {
      const action = makeAction({ actionType: "ViewsheetAction" });
      const { comp, fixture } = await renderActionAccordion({
         action,
         highlights: [makeHighlight("Assembly1", "Highlight1"), makeHighlight("Assembly2", "Highlight2")],
      });
      (comp.action as any).highlightsSelected = true;
      fixture.detectChanges();
      expect(screen.queryByText("Assembly1", { exact: false })).not.toBeNull();
      expect(screen.queryByText("Assembly2", { exact: false })).not.toBeNull();
   });
});
