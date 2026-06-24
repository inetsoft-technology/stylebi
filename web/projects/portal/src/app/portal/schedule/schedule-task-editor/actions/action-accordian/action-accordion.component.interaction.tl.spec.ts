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
 * ActionAccordion — Pass 1: Interaction tests (pure logic, no dialog flows)
 *
 * Covers: ngOnChanges (allParameters), highlights setter (updateDisabledHighlights),
 * isHighlightSelected, highlightSelectionChange, bookmarksEquivalent, bookmark CRUD,
 * saveFormat setter, missingParameters, form state helpers (updateNotificationStatus,
 * updateEmailDeliveryStatus, updateBundledStatus, updateFormatValue, passwordsMatch),
 * initForm structure, pure getters (isDashboard, bundledDisabled, dataSizeOptionVisible,
 * showMatchAndExpand, showMatchMessage, isPasswordDisable, isCSVFormat, hasExcelFormat,
 * isVSCSVFileValid), path building/parsing, highlight label helpers, email helpers,
 * deleteFile, editFile, deliveryMessage, fromEmail.
 *
 * Dialog flows (addBookmark duplicate, addFile duplicate, checkSameFormatFile confirm,
 * openEmailDialogNotify/To/CC HTTP + modal) → Pass 2 (risk).
 * DOM assertions → Pass 3 (display).
 *
 * Mocking strategy: ScheduleUsersService and NgbModal are provided as vi.fn() mocks
 * (see test-helpers.ts). No MSW is used in this file — there are no HTTP calls in
 * the pure-logic paths under test. The sibling risk spec (action-accordion.component.risk.tl.spec.ts)
 * uses MSW for the HTTP + modal dialog flows; keeping them separate avoids accidental
 * NG0205 errors from constructor-fired HTTP side effects in this file's render calls.
 */

import { SimpleChange } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CSVConfigModel } from "../../../../../../../../shared/schedule/model/csv-config-model";
import {
   makeAction,
   makeBookmark,
   makeHighlight,
   makeModel,
   renderActionAccordion,
   resetMocks,
} from "./action-accordion.component.test-helpers";

beforeEach(() => resetMocks());
afterEach(() => vi.restoreAllMocks());

// ── Group 1: ngOnChanges — allParameters aggregation ─────────────────────────

describe("ActionAccordion — ngOnChanges allParameters", () => {

   it("combines requiredParameters and optionalParameters when requiredParameters changes", async () => {
      const { comp } = await renderActionAccordion({
         requiredParameters: ["A", "B"],
         optionalParameters: ["C"],
      });

      comp.ngOnChanges({
         requiredParameters: new SimpleChange([], ["A", "B"], false),
      });

      expect(comp.allParameters).toEqual(["A", "B", "C"]);
   });

   it("combines requiredParameters and optionalParameters when optionalParameters changes", async () => {
      const { comp } = await renderActionAccordion({
         requiredParameters: ["X"],
         optionalParameters: ["Y", "Z"],
      });

      comp.ngOnChanges({
         optionalParameters: new SimpleChange([], ["Y", "Z"], false),
      });

      expect(comp.allParameters).toEqual(["X", "Y", "Z"]);
   });

   it("ignores null requiredParameters — does not push null items", async () => {
      const { comp } = await renderActionAccordion({ optionalParameters: ["D"] });
      comp.requiredParameters = null;

      comp.ngOnChanges({
         requiredParameters: new SimpleChange(["A"], null, false),
      });

      expect(comp.allParameters).toEqual(["D"]);
   });

   it("ignores null optionalParameters — does not push null items", async () => {
      const { comp } = await renderActionAccordion({ requiredParameters: ["E"] });
      comp.optionalParameters = null;

      comp.ngOnChanges({
         optionalParameters: new SimpleChange([], null, false),
      });

      expect(comp.allParameters).toEqual(["E"]);
   });

   it("does not reset allParameters when an unrelated change key arrives", async () => {
      const { comp } = await renderActionAccordion({
         requiredParameters: ["P"],
         optionalParameters: ["Q"],
      });
      comp.allParameters = ["P", "Q"];

      comp.ngOnChanges({ someOtherProp: new SimpleChange(null, "val", false) });

      expect(comp.allParameters).toEqual(["P", "Q"]);
   });
});

// ── Group 2: highlights setter and updateDisabledHighlights ──────────────────

describe("ActionAccordion — highlights setter / updateDisabledHighlights", () => {

   it("filters out null entries from the input array", async () => {
      const { comp } = await renderActionAccordion();

      comp.highlights = [makeHighlight("T1", "H1"), null as any, makeHighlight("T2", "H2")];

      expect(comp.highlights).toHaveLength(2);
      expect(comp.highlights[0].element).toBe("T1");
      expect(comp.highlights[1].element).toBe("T2");
   });

   it("populates disabledHighlights when action has assemblies not in new highlights", async () => {
      const action = makeAction({
         sheet: "Sheet1",
         highlightAssemblies: ["T1"],
         highlightNames: ["H1"],
      });
      const { comp } = await renderActionAccordion({ action });
      comp.highlightSheet = "Sheet1";

      comp.highlights = [];

      expect(comp.disabledHighlights).toHaveLength(1);
      expect(comp.disabledHighlights[0].element).toBe("T1");
      expect(comp.disabledHighlights[0].highlight).toBe("H1");
   });

   it("clears disabledHighlights when highlightSheet differs from action.sheet", async () => {
      const action = makeAction({
         sheet: "Sheet1",
         highlightAssemblies: ["T1"],
         highlightNames: ["H1"],
      });
      const { comp } = await renderActionAccordion({ action });
      comp.highlightSheet = "OtherSheet";

      comp.highlights = [];

      expect(comp.disabledHighlights).toHaveLength(0);
   });

   it("does not add to disabledHighlights when highlight IS found in new set", async () => {
      const action = makeAction({
         sheet: "Sheet1",
         highlightAssemblies: ["T1"],
         highlightNames: ["H1"],
      });
      const { comp } = await renderActionAccordion({ action });
      comp.highlightSheet = "Sheet1";

      comp.highlights = [makeHighlight("T1", "H1")];

      expect(comp.disabledHighlights).toHaveLength(0);
   });
});

// ── Group 3: isHighlightSelected ─────────────────────────────────────────────

describe("ActionAccordion — isHighlightSelected", () => {

   it("returns false when highlightAssemblies is empty", async () => {
      const action = makeAction({ highlightAssemblies: [], highlightNames: [] });
      const { comp } = await renderActionAccordion({ action });

      expect(comp.isHighlightSelected("T1", "H1")).toBe(false);
   });

   it("returns true when assembly and highlight match exactly", async () => {
      const action = makeAction({
         highlightAssemblies: ["T1"],
         highlightNames: ["H1"],
      });
      const { comp } = await renderActionAccordion({ action });

      expect(comp.isHighlightSelected("T1", "H1")).toBe(true);
   });

   it("returns false when assembly matches but highlight does not", async () => {
      const action = makeAction({
         highlightAssemblies: ["T1"],
         highlightNames: ["H1"],
      });
      const { comp } = await renderActionAccordion({ action });

      expect(comp.isHighlightSelected("T1", "H2")).toBe(false);
   });

   it("matches against highlight name parsed from ' (' suffix", async () => {
      const action = makeAction({
         highlightAssemblies: ["T1"],
         highlightNames: ["H1"],
      });
      const { comp } = await renderActionAccordion({ action });

      expect(comp.isHighlightSelected("T1", "H1 (2)")).toBe(true);
   });
});

// ── Group 4: highlightSelectionChange ────────────────────────────────────────

describe("ActionAccordion — highlightSelectionChange", () => {

   it("clears arrays and resets sheet when highlightSheet differs from action.sheet", async () => {
      const action = makeAction({
         sheet: "Sheet1",
         highlightAssemblies: ["T1"],
         highlightNames: ["OldH"],
      });
      const { comp } = await renderActionAccordion({ action });
      comp.highlightSheet = "OtherSheet";

      comp.highlightSelectionChange("T2", "NewH");

      expect(comp.action.highlightAssemblies).toEqual(["T2"]);
      expect(comp.action.highlightNames).toEqual(["NewH"]);
      expect(comp.highlightSheet).toBe("Sheet1");
   });

   it("adds assembly and highlight when not already selected", async () => {
      const action = makeAction({
         sheet: "Sheet1",
         highlightAssemblies: [],
         highlightNames: [],
      });
      const { comp } = await renderActionAccordion({ action });
      comp.highlightSheet = "Sheet1";

      comp.highlightSelectionChange("T1", "H1");

      expect(comp.action.highlightAssemblies).toContain("T1");
      expect(comp.action.highlightNames).toContain("H1");
   });

   it("removes assembly and highlight when already selected", async () => {
      const action = makeAction({
         sheet: "Sheet1",
         highlightAssemblies: ["T1"],
         highlightNames: ["H1"],
      });
      const { comp } = await renderActionAccordion({ action });
      comp.highlightSheet = "Sheet1";

      comp.highlightSelectionChange("T1", "H1");

      expect(comp.action.highlightAssemblies).toHaveLength(0);
      expect(comp.action.highlightNames).toHaveLength(0);
   });

   // Bug #21295
   it("parses ' (' suffix from highlight name before storing", async () => {
      const action = makeAction({ sheet: "Sheet1", highlightAssemblies: [], highlightNames: [] });
      const { comp } = await renderActionAccordion({ action });
      comp.highlightSheet = "Sheet1";

      comp.highlightSelectionChange("T1", "H1 (3)");

      expect(comp.action.highlightNames[0]).toBe("H1");
   });
});

// ── Group 5: bookmarksEquivalent ──────────────────────────────────────────────

describe("ActionAccordion — bookmarksEquivalent", () => {

   it("returns falsy when original is null", async () => {
      const { comp } = await renderActionAccordion();
      const bm = makeBookmark("Dashboard1");

      expect(comp.bookmarksEquivalent(null, bm)).toBeFalsy();
   });

   it("returns falsy when other is null", async () => {
      const { comp } = await renderActionAccordion();
      const bm = makeBookmark("Dashboard1");

      expect(comp.bookmarksEquivalent(bm, null)).toBeFalsy();
   });

   it("returns true when both label and type match", async () => {
      const { comp } = await renderActionAccordion();
      const a = makeBookmark("Dashboard1", 2);
      const b = makeBookmark("Dashboard1", 2);

      expect(comp.bookmarksEquivalent(a, b)).toBe(true);
   });

   it("returns false when types differ", async () => {
      const { comp } = await renderActionAccordion();
      const a = makeBookmark("Dashboard1", 0);
      const b = makeBookmark("Dashboard1", 1);

      expect(comp.bookmarksEquivalent(a, b)).toBe(false);
   });
});

// ── Group 6: addBookmark / modifyBookmark / removeBookmark / editBookmark ─────

describe("ActionAccordion — bookmark CRUD", () => {

   it("addBookmark adds to bookmarkList and action.bookmarks when not duplicate", async () => {
      const action = makeAction({ bookmarks: [] });
      const bm = makeBookmark("BM1");
      const { comp } = await renderActionAccordion({ action, bookmarks: [bm] });
      comp.selectedBookmark = bm;
      const list: string[] = [];
      comp.bookmarkList = list;

      comp.addBookmark();

      expect(comp.bookmarkList).toContain("BM1");
      expect(comp.action.bookmarks).toContain(bm);
   });

   it("modifyBookmark updates bookmarkList and action.bookmarks when not duplicate", async () => {
      const bm1 = makeBookmark("BM1");
      const bm2 = makeBookmark("BM2");
      const action = makeAction({ bookmarks: [bm1] });
      const { comp } = await renderActionAccordion({ action, bookmarks: [bm1, bm2] });
      comp.bookmarkList = ["BM1"];
      comp.selectedBookmarkListIndex = 0;
      comp.selectedBookmark = bm2;

      comp.modifyBookmark();

      expect(comp.bookmarkList[0]).toBe("BM2");
      expect(comp.action.bookmarks[0]).toBe(bm2);
   });

   it("removeBookmark splices both lists and resets selectedBookmarkListIndex to -1", async () => {
      const bm1 = makeBookmark("BM1");
      const bm2 = makeBookmark("BM2");
      const action = makeAction({ bookmarks: [bm1, bm2] });
      const { comp } = await renderActionAccordion({ action });
      comp.bookmarkList = ["BM1", "BM2"];
      comp.selectedBookmarkListIndex = 0;

      comp.removeBookmark();

      expect(comp.bookmarkList).toEqual(["BM2"]);
      expect(comp.action.bookmarks).toEqual([bm2]);
      expect(comp.selectedBookmarkListIndex).toBe(-1);
   });

   it("editBookmark sets selectedBookmarkListIndex and selectedBookmark from bookmarks list", async () => {
      const bm1 = makeBookmark("BM1");
      const bm2 = makeBookmark("BM2");
      const action = makeAction({ bookmarks: [bm1, bm2] });
      const { comp } = await renderActionAccordion({ action, bookmarks: [bm1, bm2] });
      comp.bookmarkList = ["BM1", "BM2"];

      comp.editBookmark(1);

      expect(comp.selectedBookmarkListIndex).toBe(1);
      expect(comp.selectedBookmark).toBe(bm2);
   });
});

// ── Group 7: saveFormat getter/setter ────────────────────────────────────────

describe("ActionAccordion — saveFormat getter/setter", () => {

   it("setter creates csvSaveModel when format is '3' (CSV) and none exists", async () => {
      const action = makeAction({ csvSaveModel: undefined });
      const { comp } = await renderActionAccordion({ action });

      comp.saveFormat = "3";

      expect(comp.action.csvSaveModel).toBeInstanceOf(CSVConfigModel);
   });

   it("getter returns _saveFormat when set", async () => {
      const { comp } = await renderActionAccordion();
      comp.saveFormat = "9";

      expect(comp.saveFormat).toBe("9");
   });

   it("getter returns saveFormats[0].type when _saveFormat is not set (non-dashboard)", async () => {
      const model = makeModel({ saveFileFormats: [{ type: "9", label: "PDF" }] });
      const action = makeAction({ actionType: "ReportAction", saveToServerEnabled: false });
      const { comp } = await renderActionAccordion({ model, action });
      // _saveFormat defaults to undefined — clear it by accessing a fresh state
      (comp as any)._saveFormat = undefined;

      expect(comp.saveFormat).toBe("9");
   });
});

// ── Group 8: missingParameters ────────────────────────────────────────────────

describe("ActionAccordion — missingParameters", () => {

   it("returns null when requiredParameters is empty", async () => {
      const { comp } = await renderActionAccordion({ requiredParameters: [] });

      expect(comp.missingParameters).toBeNull();
   });

   it("returns all requiredParameters when parameters is empty", async () => {
      const action = makeAction({ parameters: [] });
      const { comp } = await renderActionAccordion({ action, requiredParameters: ["P1", "P2"] });

      expect(comp.missingParameters).toBe("P1, P2");
   });

   it("returns empty string when all requiredParameters are present in parameters", async () => {
      const action = makeAction({
         parameters: [
            { name: "P1", type: "string", value: null, array: false },
            { name: "P2", type: "string", value: null, array: false },
         ],
      });
      const { comp } = await renderActionAccordion({ action, requiredParameters: ["P1", "P2"] });

      expect(comp.missingParameters).toBe("");
   });

   it("returns only the missing requiredParameters", async () => {
      const action = makeAction({
         parameters: [{ name: "P1", type: "string", value: null, array: false }],
      });
      const { comp } = await renderActionAccordion({ action, requiredParameters: ["P1", "P2"] });

      expect(comp.missingParameters).toBe("P2");
   });
});

// ── Group 9: pure getters ─────────────────────────────────────────────────────

describe("ActionAccordion — isDashboard, bundledDisabled, dataSizeOptionVisible", () => {

   it("isDashboard returns true for ViewsheetAction", async () => {
      const { comp } = await renderActionAccordion({ action: makeAction({ actionType: "ViewsheetAction" }) });
      expect(comp.isDashboard).toBe(true);
   });

   it("isDashboard returns false for ReportAction", async () => {
      const { comp } = await renderActionAccordion({ action: makeAction({ actionType: "ReportAction" }) });
      expect(comp.isDashboard).toBe(false);
   });

   it("bundledDisabled returns true for HTML_BUNDLE format", async () => {
      const { comp } = await renderActionAccordion({ action: makeAction({ format: "HTML_BUNDLE" }) });
      expect(comp.bundledDisabled).toBe(true);
   });

   it("bundledDisabled returns true for PNG format (dashboard)", async () => {
      const { comp } = await renderActionAccordion({ action: makeAction({ actionType: "ViewsheetAction", format: "PNG" }) });
      expect(comp.bundledDisabled).toBe(true);
   });

   it("bundledDisabled returns false for PDF format", async () => {
      const { comp } = await renderActionAccordion({ action: makeAction({ format: "PDF" }) });
      expect(comp.bundledDisabled).toBe(false);
   });

   it("dataSizeOptionVisible is true for dashboard with non-HTML/CSV format", async () => {
      const { comp } = await renderActionAccordion({
         action: makeAction({ actionType: "ViewsheetAction", format: "Excel" }),
      });
      expect(comp.dataSizeOptionVisible).toBe(true);
   });

   it("dataSizeOptionVisible is false for non-dashboard action", async () => {
      const { comp } = await renderActionAccordion({
         action: makeAction({ actionType: "ReportAction", format: "Excel" }),
      });
      expect(comp.dataSizeOptionVisible).toBe(false);
   });

   it("dataSizeOptionVisible is false for dashboard with HTML format", async () => {
      const { comp } = await renderActionAccordion({
         action: makeAction({ actionType: "ViewsheetAction", format: "HTML" }),
      });
      expect(comp.dataSizeOptionVisible).toBe(false);
   });
});

// ── Group 10: showMatchAndExpand, showMatchMessage ────────────────────────────

describe("ActionAccordion — showMatchAndExpand / showMatchMessage", () => {

   it("showMatchAndExpand returns false for non-dashboard", async () => {
      const action = makeAction({ actionType: "ReportAction", saveFormats: ["0"] });
      const { comp } = await renderActionAccordion({ action });
      expect(comp.showMatchAndExpand).toBe(false);
   });

   it("showMatchAndExpand returns true for dashboard when not all formats are HTML/VSCSV", async () => {
      const action = makeAction({ actionType: "ViewsheetAction", saveFormats: ["0"] });
      const { comp } = await renderActionAccordion({ action });
      expect(comp.showMatchAndExpand).toBe(true);
   });

   it("showMatchMessage returns false when action has no saveFormats", async () => {
      const action = makeAction({ saveFormats: [] });
      const { comp } = await renderActionAccordion({ action });
      expect(comp.showMatchMessage).toBe(false);
   });

   it("showMatchMessage returns true when mix of HTML and other formats", async () => {
      // FileTypes.HTML = 5; "0" = Excel (hasOther=true) → hasHtml && hasOther = true
      const action = makeAction({ actionType: "ViewsheetAction", saveFormats: ["5", "0"] });
      const { comp } = await renderActionAccordion({ action });
      expect(comp.showMatchMessage).toBe(true);
   });
});

// ── Group 11: isPasswordDisable ───────────────────────────────────────────────

describe("ActionAccordion — isPasswordDisable", () => {

   it("returns true when model.fipsMode is true", async () => {
      const model = makeModel({ fipsMode: true });
      const { comp } = await renderActionAccordion({ model });
      expect(comp.isPasswordDisable()).toBe(true);
   });

   it("returns true when bundledAsZip=false and format is plain PDF", async () => {
      const action = makeAction({ bundledAsZip: false, format: "PDF" });
      const { comp } = await renderActionAccordion({ action });
      expect(comp.isPasswordDisable()).toBe(true);
   });

   it("returns false when bundledAsZip=true and format is HTML_BUNDLE", async () => {
      const action = makeAction({ bundledAsZip: true, format: "HTML_BUNDLE" });
      const model = makeModel({ fipsMode: false });
      const { comp } = await renderActionAccordion({ model, action });
      expect(comp.isPasswordDisable()).toBe(false);
   });
});

// ── Group 12: isCSVFormat, isVSCSVFormat, hasExcelFormat, isVSCSVFileValid ────

describe("ActionAccordion — CSV/Excel format helpers", () => {

   it("isCSVFormat returns true for format '3'", async () => {
      const { comp } = await renderActionAccordion();
      expect(comp.isCSVFormat("3")).toBe(true);
   });

   it("isVSCSVFormat returns true for dashboard + format '6'", async () => {
      const action = makeAction({ actionType: "ViewsheetAction" });
      const { comp } = await renderActionAccordion({ action });
      expect(comp.isVSCSVFormat("6")).toBe(true);
   });

   it("isVSCSVFormat returns false for non-dashboard + format '6'", async () => {
      const action = makeAction({ actionType: "ReportAction" });
      const { comp } = await renderActionAccordion({ action });
      expect(comp.isVSCSVFormat("6")).toBe(false);
   });

   it("hasExcelFormat returns true when saveFormats contains '0'", async () => {
      const action = makeAction({ saveFormats: ["9", "0"] });
      const { comp } = await renderActionAccordion({ action });
      expect(comp.hasExcelFormat()).toBe(true);
   });

   it("hasExcelFormat returns false when saveFormats has no '0'", async () => {
      const action = makeAction({ saveFormats: ["9"] });
      const { comp } = await renderActionAccordion({ action });
      expect(comp.hasExcelFormat()).toBe(false);
   });

   it("isVSCSVFileValid returns false for VSCSV format with empty selectedAssemblies", async () => {
      const action = makeAction({
         actionType: "ViewsheetAction",
         csvSaveModel: { selectedAssemblies: [], delimiter: ",", quote: '"', keepHeader: true } as any,
      });
      const { comp } = await renderActionAccordion({ action });
      comp.saveFormat = "6";

      expect(comp.isVSCSVFileValid()).toBe(false);
   });

   it("isVSCSVFileValid returns true for non-VSCSV format", async () => {
      const { comp } = await renderActionAccordion();
      comp.saveFormat = "9";

      expect(comp.isVSCSVFileValid()).toBe(true);
   });
});

// ── Group 13: deliveryMessage, fromEmail ─────────────────────────────────────

describe("ActionAccordion — deliveryMessage and fromEmail", () => {

   it("deliveryMessage getter returns empty string when action.message is undefined", async () => {
      const action = makeAction({ message: undefined });
      const { comp } = await renderActionAccordion({ action });
      expect(comp.deliveryMessage).toBe("");
   });

   it("deliveryMessage setter sets action.message and htmlMessage=true", async () => {
      const { comp } = await renderActionAccordion();
      comp.deliveryMessage = "Hello World";
      expect(comp.action.message).toBe("Hello World");
      expect(comp.action.htmlMessage).toBe(true);
   });

   it("fromEmail getter returns action.fromEmail when model.fromEmailEnabled=true", async () => {
      const model = makeModel({ fromEmailEnabled: true });
      const action = makeAction({ fromEmail: "action@example.com" });
      const { comp } = await renderActionAccordion({ model, action });
      expect(comp.fromEmail).toBe("action@example.com");
   });

   it("fromEmail getter returns model.defaultFromEmail when fromEmailEnabled=false", async () => {
      const model = makeModel({ fromEmailEnabled: false, defaultFromEmail: "default@example.com" });
      const { comp } = await renderActionAccordion({ model });
      expect(comp.fromEmail).toBe("default@example.com");
   });
});

// ── Group 14: path getter/setter — buildPath and parsePath ───────────────────

describe("ActionAccordion — path getter/setter", () => {

   it("path getter returns filePath directly when no server locations", async () => {
      const model = makeModel({ serverLocations: undefined });
      const { comp } = await renderActionAccordion({ model });
      comp.filePath = "reports/output.xlsx";
      comp.locationPath = null;

      expect(comp.path).toBe("reports/output.xlsx");
   });

   it("path getter combines locationPath + filePath when location exists", async () => {
      const model = makeModel({
         serverLocations: [{ path: "/mnt/nfs", label: "NFS", pathInfoModel: null }],
      });
      const { comp } = await renderActionAccordion({ model });
      comp.locationPath = "/mnt/nfs";
      comp.filePath = "report.xlsx";

      expect(comp.path).toBe("/mnt/nfs/report.xlsx");
   });

   it("path setter splits path into locationPath and filePath when location matches", async () => {
      const model = makeModel({
         serverLocations: [{ path: "/mnt/nfs", label: "NFS", pathInfoModel: null }],
      });
      const { comp } = await renderActionAccordion({ model });

      comp.path = "/mnt/nfs/report.xlsx";

      expect(comp.locationPath).toBe("/mnt/nfs");
      expect(comp.filePath).toBe("report.xlsx");
   });
});

// ── Group 15: getHighlightLabel / getHighlightConditionLabel ──────────────────

describe("ActionAccordion — highlight label helpers", () => {

   it("getHighlightLabel returns highlight as-is when count is 1 and no range pattern", async () => {
      const { comp } = await renderActionAccordion();
      expect(comp.getHighlightLabel("MyHighlight", 1)).toBe("MyHighlight");
   });

   it("getHighlightLabel appends count when count > 1", async () => {
      const { comp } = await renderActionAccordion();
      expect(comp.getHighlightLabel("H1", 3)).toBe("H1(3)");
   });

   it("getHighlightLabel replaces RangeOutput_Range_N pattern", async () => {
      const { comp } = await renderActionAccordion();
      expect(comp.getHighlightLabel("RangeOutput_Range_2", 1)).toBe("_#(js:Range) 2");
   });

   it("getHighlightConditionLabel returns '_#(js:to) condition' for range pattern", async () => {
      const { comp } = await renderActionAccordion();
      expect(comp.getHighlightConditionLabel("RangeOutput_Range_1")).toBe("_#(js:to) RangeOutput_Range_1");
   });

   it("getHighlightConditionLabel returns condition unchanged for non-range", async () => {
      const { comp } = await renderActionAccordion();
      expect(comp.getHighlightConditionLabel("STATE = NJ")).toBe("STATE = NJ");
   });
});

// ── Group 16: email value helpers ────────────────────────────────────────────

describe("ActionAccordion — email value helpers", () => {

   it("updateNotificationEmails sets action.notifications from string value", async () => {
      const { comp } = await renderActionAccordion();
      comp.updateNotificationEmails("a@b.com, c@d.com");
      expect(comp.action.notifications).toBe("a@b.com,c@d.com");
   });

   it("updateNotificationEmails sets action.notifications from {value: ...} object", async () => {
      const { comp } = await renderActionAccordion();
      comp.updateNotificationEmails({ value: "x@y.com" });
      expect(comp.action.notifications).toBe("x@y.com");
   });

   it("updateDeliveryEmails sets action.to from string value", async () => {
      const { comp } = await renderActionAccordion();
      comp.updateDeliveryEmails("r@s.com");
      expect(comp.action.to).toBe("r@s.com");
   });

   it("updateEmailDataOnlyFormat sets emailOnlyDataComponents=false when emailMatchLayout=true", async () => {
      const action = makeAction({ emailMatchLayout: true, emailOnlyDataComponents: true });
      const { comp } = await renderActionAccordion({ action });
      comp.updateEmailDataOnlyFormat();
      expect(comp.action.emailOnlyDataComponents).toBe(false);
   });

   it("updateDataOnlyFormat sets saveOnlyDataComponents=false when saveMatchLayout=true", async () => {
      const action = makeAction({ saveMatchLayout: true, saveOnlyDataComponents: true });
      const { comp } = await renderActionAccordion({ action });
      comp.updateDataOnlyFormat();
      expect(comp.action.saveOnlyDataComponents).toBe(false);
   });
});

// ── Group 17: deleteFile, editFile ───────────────────────────────────────────

describe("ActionAccordion — deleteFile / editFile", () => {

   it("deleteFile removes the item at selectedFormatIndex from all arrays and resets index to -1", async () => {
      const action = makeAction({
         filePaths: ["/a.xlsx", "/b.pdf"],
         saveFormats: ["0", "9"],
         serverFilePaths: [
            { path: "/a.xlsx", ftp: false, useCredential: false, secretId: null, username: null, password: null, oldFormat: -1 },
            { path: "/b.pdf", ftp: false, useCredential: false, secretId: null, username: null, password: null, oldFormat: -1 },
         ],
      });
      const { comp } = await renderActionAccordion({ action });
      comp.saveStrings = ["/a.xlsx - Excel", "/b.pdf - PDF"];
      comp.selectedFormatIndex = 0;

      comp.deleteFile();

      expect(comp.action.filePaths).toEqual(["/b.pdf"]);
      expect(comp.action.saveFormats).toEqual(["9"]);
      expect(comp.saveStrings).toEqual(["/b.pdf - PDF"]);
      expect(comp.selectedFormatIndex).toBe(-1);
   });

   it("editFile populates path fields and saveFormat from action arrays at given index", async () => {
      const action = makeAction({
         filePaths: ["/x.xlsx"],
         saveFormats: ["0"],
         serverFilePaths: [{
            path: "/x.xlsx",
            ftp: true,
            useCredential: true,
            secretId: "sec1",
            username: "user1",
            password: "pass1",
            oldFormat: -1,
         }],
      });
      const { comp } = await renderActionAccordion({ action });

      comp.editFile(0);

      expect(comp.selectedFormatIndex).toBe(0);
      expect(comp.ftp).toBe(true);
      expect(comp.useCredential).toBe(true);
      expect(comp.secretId).toBe("sec1");
      expect(comp.username).toBe("user1");
      expect(comp.password).toBe("pass1");
      expect(comp.saveFormat).toBe("0");
   });
});

// ── Group 18: initForm — form control structure ───────────────────────────────

describe("ActionAccordion — initForm form structure", () => {

   it("always creates notification, from, password, confirmPassword, attachmentName controls", async () => {
      const { comp } = await renderActionAccordion();

      expect(comp.form.get("notification")).not.toBeNull();
      expect(comp.form.get("from")).not.toBeNull();
      expect(comp.form.get("password")).not.toBeNull();
      expect(comp.form.get("confirmPassword")).not.toBeNull();
      expect(comp.form.get("attachmentName")).not.toBeNull();
   });

   it("adds to/cc/bcc controls when emailDeliveryEnabled=true", async () => {
      const model = makeModel({ emailDeliveryEnabled: true });
      const { comp } = await renderActionAccordion({ model });

      expect(comp.form.get("to")).not.toBeNull();
      expect(comp.form.get("cc")).not.toBeNull();
      expect(comp.form.get("bcc")).not.toBeNull();
   });

   it("does not add to/cc/bcc controls when emailDeliveryEnabled=false", async () => {
      const model = makeModel({ emailDeliveryEnabled: false });
      const { comp } = await renderActionAccordion({ model });

      expect(comp.form.get("to")).toBeNull();
      expect(comp.form.get("cc")).toBeNull();
      expect(comp.form.get("bcc")).toBeNull();
   });

   it("registers the form on parentForm as 'action' control", async () => {
      const parentForm = new UntypedFormGroup({});
      const { comp } = await renderActionAccordion({ parentForm });

      expect(parentForm.get("action")).toBe(comp.form);
   });
});

// ── Group 19: updateNotificationStatus ───────────────────────────────────────

describe("ActionAccordion — updateNotificationStatus", () => {

   it("sets validators on notification control when value=true", async () => {
      const { comp } = await renderActionAccordion();
      comp.updateNotificationStatus(true);

      expect(comp.form.get("notification").validator).not.toBeNull();
   });

   it("clears validators on notification control when value=false", async () => {
      const { comp } = await renderActionAccordion();
      comp.updateNotificationStatus(true);
      comp.updateNotificationStatus(false);

      expect(comp.form.get("notification").validator).toBeNull();
   });
});

// ── Group 20: updateEmailDeliveryStatus ──────────────────────────────────────

describe("ActionAccordion — updateEmailDeliveryStatus", () => {

   it("sets validators on 'to' control when value=true", async () => {
      const model = makeModel({ emailDeliveryEnabled: true });
      const { comp } = await renderActionAccordion({ model });
      comp.updateEmailDeliveryStatus(true);

      expect(comp.form.get("to")?.validator).not.toBeNull();
   });

   it("clears validators on 'from' and 'to' when value=false", async () => {
      const model = makeModel({ emailDeliveryEnabled: true });
      const { comp } = await renderActionAccordion({ model });
      comp.updateEmailDeliveryStatus(true);
      comp.updateEmailDeliveryStatus(false);

      expect(comp.form.get("to")?.validator).toBeNull();
   });

   it("sets action.format to mailFormats[0] when deliverEmailsEnabled=true and format is empty", async () => {
      const model = makeModel({
         emailDeliveryEnabled: true,
         vsMailFormats: [{ type: "Excel", label: "Excel" }],
      });
      const action = makeAction({ deliverEmailsEnabled: false, format: "" });
      const { comp } = await renderActionAccordion({ model, action });

      comp.updateEmailDeliveryStatus(true);

      expect(comp.action.format).toBe("Excel");
   });
});

// ── Group 21: updateBundledStatus ────────────────────────────────────────────

describe("ActionAccordion — updateBundledStatus", () => {

   it("adds form.validator (passwordsMatch) when value=true and deliverEmailsEnabled=true", async () => {
      const model = makeModel({ emailDeliveryEnabled: true });
      const action = makeAction({ deliverEmailsEnabled: true });
      const { comp } = await renderActionAccordion({ model, action });

      comp.updateBundledStatus(true);

      expect(comp.form.validator).not.toBeNull();
   });

   it("clears form.validator when value=false", async () => {
      const model = makeModel({ emailDeliveryEnabled: true });
      const action = makeAction({ deliverEmailsEnabled: true });
      const { comp } = await renderActionAccordion({ model, action });
      comp.updateBundledStatus(true);

      comp.updateBundledStatus(false);

      expect(comp.form.validator).toBeNull();
   });
});

// ── Group 22: updateFormatValue ───────────────────────────────────────────────

describe("ActionAccordion — updateFormatValue", () => {

   it("sets action.format to the provided value", async () => {
      const { comp } = await renderActionAccordion();
      comp.updateFormatValue("PDF");
      expect(comp.action.format).toBe("PDF");
   });

   it("sets bundledAsZip=true and format=CSV for ViewsheetAction", async () => {
      const action = makeAction({ actionType: "ViewsheetAction", bundledAsZip: false });
      const { comp } = await renderActionAccordion({ action });
      comp.updateFormatValue("CSV");
      expect(comp.action.bundledAsZip).toBe(true);
   });

   it("creates csvExportModel when format=CSV and none exists", async () => {
      const action = makeAction({ csvExportModel: undefined });
      const { comp } = await renderActionAccordion({ action });
      comp.updateFormatValue("CSV");
      expect(comp.action.csvExportModel).toBeInstanceOf(CSVConfigModel);
   });

   it("clears emailOnlyDataComponents when format is not Excel", async () => {
      const action = makeAction({ emailOnlyDataComponents: true });
      const { comp } = await renderActionAccordion({ action });
      comp.updateFormatValue("PDF");
      expect(comp.action.emailOnlyDataComponents).toBe(false);
   });

   it("sets emailMatchLayout=true when expandEnabled=false", async () => {
      const model = makeModel({ expandEnabled: false });
      const action = makeAction({ emailMatchLayout: false });
      const { comp } = await renderActionAccordion({ model, action });
      comp.updateFormatValue("PDF");
      expect(comp.action.emailMatchLayout).toBe(true);
   });
});

// ── Group 23: passwordsMatch ──────────────────────────────────────────────────

describe("ActionAccordion — passwordsMatch validator", () => {

   it("returns null when both password and confirmPassword are empty", async () => {
      const { comp } = await renderActionAccordion();
      comp.form.get("password").setValue(null);
      comp.form.get("confirmPassword").setValue(null);

      const validator = comp.passwordsMatch("password", "confirmPassword");
      expect(validator(comp.form)).toBeNull();
   });

   it("returns null when passwords match", async () => {
      const { comp } = await renderActionAccordion();
      comp.form.get("password").setValue("secret");
      comp.form.get("confirmPassword").setValue("secret");

      const validator = comp.passwordsMatch("password", "confirmPassword");
      expect(validator(comp.form)).toBeNull();
   });

   it("returns passwordsDoNotMatch error when passwords differ", async () => {
      const { comp } = await renderActionAccordion();
      comp.form.get("password").setValue("abc");
      comp.form.get("confirmPassword").setValue("xyz");

      const validator = comp.passwordsMatch("password", "confirmPassword");
      expect(validator(comp.form)).toEqual({ passwordsDoNotMatch: true });
   });
});
