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
 * FileFormatPane — Pass 1: Interaction
 *
 * Direct instantiation (see test-helpers.ts) — single constructor dependency (NgbModal),
 * no `inject()` calls.
 *
 * Scope (per prescan Pass 1 method list): ngOnInit, changeFormatType,
 * updateOnlyDataComponents, selectBookmark, selectAll, clearAll.
 *
 * Risk-first coverage:
 *   Group 3 [Risk 3] — selectBookmark: shift-range / ctrl-toggle / plain-click selection,
 *                       the most stateful method in the component
 *   Group 2 [Risk 3] — changeFormatType: HTML reset side effects + the CSV
 *                       no-table-selected error dialog
 *   Group 1 [Risk 2] — ngOnInit: type-list construction + stale-formatType correction
 *   Remaining groups [Risk 1] — single-purpose mutators
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope this pass (covered in file-format-pane.component.display.tl.spec.ts):
 *   getExport (7-way dispatch), matchLayoutVisible (3-condition boolean).
 *
 * C4: MessageDialog.lastMessage/lastMessageTS are reset in beforeEach since
 * changeFormatType's CSV branch goes through ComponentTool.showMessageDialog, which
 * de-dupes identical messages fired within 500ms using those static fields.
 */

import { ComponentTool } from "../../common/util/component-tool";
import { MessageDialog } from "../../widget/dialog/message-dialog/message-dialog.component";
import { FileFormatType } from "../model/file-format-type";
import { createComponent, makeModel } from "./file-format-pane.component.test-helpers";

beforeEach(() => {
   MessageDialog.lastMessage = null;
   MessageDialog.lastMessageTS = 0;
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: ngOnInit [Risk 2]
// ---------------------------------------------------------------------------

describe("FileFormatPane — ngOnInit", () => {
   it("should build the visible type list from exportTypes", () => {
      const { comp } = createComponent({
         exportTypes: [{ label: "Excel", value: "Excel" }, { label: "PDF", value: "PDF" }],
      });

      comp.ngOnInit();

      expect(comp.types).toEqual([
         { label: "Excel", value: FileFormatType.EXPORT_TYPE_EXCEL },
         { label: "PDF", value: FileFormatType.EXPORT_TYPE_PDF },
      ]);
   });

   it("should relabel PNG as 'Embedded PNG' when emailing", () => {
      const { comp } = createComponent({
         email: true,
         exportTypes: [{ label: "PNG", value: "PNG" }],
      });

      comp.ngOnInit();

      expect(comp.types[0].label).toBe("_#(js:Embedded PNG)");
   });

   it("should NOT relabel PNG when not emailing", () => {
      const { comp } = createComponent({
         email: false,
         exportTypes: [{ label: "PNG", value: "PNG" }],
      });

      comp.ngOnInit();

      expect(comp.types[0].label).toBe("PNG");
   });

   it("should default formatType to the first type when the current one isn't in the list", () => {
      const { comp } = createComponent({
         exportTypes: [{ label: "Excel", value: "Excel" }, { label: "PDF", value: "PDF" }],
         model: makeModel({ formatType: FileFormatType.EXPORT_TYPE_CSV }),
      });

      comp.ngOnInit();

      expect(comp.model.formatType).toBe(FileFormatType.EXPORT_TYPE_EXCEL);
   });

   it("should leave formatType unchanged when it is already in the list", () => {
      const { comp } = createComponent({
         exportTypes: [{ label: "Excel", value: "Excel" }, { label: "PDF", value: "PDF" }],
         model: makeModel({ formatType: FileFormatType.EXPORT_TYPE_PDF }),
      });

      comp.ngOnInit();

      expect(comp.model.formatType).toBe(FileFormatType.EXPORT_TYPE_PDF);
   });

   it("should leave formatType unchanged when there are no export types at all", () => {
      const { comp } = createComponent({
         exportTypes: [],
         model: makeModel({ formatType: FileFormatType.EXPORT_TYPE_CSV }),
      });

      comp.ngOnInit();

      expect(comp.model.formatType).toBe(FileFormatType.EXPORT_TYPE_CSV);
   });
});

// ---------------------------------------------------------------------------
// Group 2: changeFormatType [Risk 3]
// ---------------------------------------------------------------------------

describe("FileFormatPane — changeFormatType", () => {
   it("should set the format type", () => {
      const { comp } = createComponent();
      comp.changeFormatType(FileFormatType.EXPORT_TYPE_PDF);
      expect(comp.model.formatType).toBe(FileFormatType.EXPORT_TYPE_PDF);
   });

   it("should force includeCurrent and clear selected bookmarks when switching to HTML", () => {
      const { comp } = createComponent({
         model: makeModel({ includeCurrent: false, selectedBookmarks: ["b1", "b2"] }),
      });

      comp.changeFormatType(FileFormatType.EXPORT_TYPE_HTML);

      expect(comp.model.includeCurrent).toBe(true);
      expect(comp.model.selectedBookmarks).toEqual([]);
   });

   it("should NOT touch includeCurrent/selectedBookmarks for a non-HTML type", () => {
      const { comp } = createComponent({
         model: makeModel({ includeCurrent: false, selectedBookmarks: ["b1"] }),
      });

      comp.changeFormatType(FileFormatType.EXPORT_TYPE_PDF);

      expect(comp.model.includeCurrent).toBe(false);
      expect(comp.model.selectedBookmarks).toEqual(["b1"]);
   });

   it("should show an error dialog when switching to CSV with no table data assemblies selected", () => {
      const { comp, modalService } = createComponent({
         model: makeModel({ tableDataAssemblies: [] }),
      });
      const spy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue(undefined);

      comp.changeFormatType(FileFormatType.EXPORT_TYPE_CSV);

      expect(spy).toHaveBeenCalledWith(
         modalService, "_#(js:Error)", "_#(js:common.repletAction.exportFailed.cvs)"
      );
   });

   it("should NOT show the error dialog when switching to CSV with table data assemblies already selected", () => {
      const { comp } = createComponent({
         model: makeModel({ tableDataAssemblies: ["Table1"] }),
      });
      const spy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue(undefined);

      comp.changeFormatType(FileFormatType.EXPORT_TYPE_CSV);

      expect(spy).not.toHaveBeenCalled();
   });

   it("should NOT show the error dialog for a non-CSV type even with no table data assemblies", () => {
      const { comp } = createComponent({
         model: makeModel({ tableDataAssemblies: [] }),
      });
      const spy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue(undefined);

      comp.changeFormatType(FileFormatType.EXPORT_TYPE_PDF);

      expect(spy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 3: selectBookmark [Risk 3]
// ---------------------------------------------------------------------------

describe("FileFormatPane — selectBookmark", () => {
   function makeEvent(overrides: Partial<MouseEvent> = {}): MouseEvent {
      return Object.assign({ shiftKey: false, ctrlKey: false, metaKey: false }, overrides) as MouseEvent;
   }

   it("should do nothing when the format type is HTML", () => {
      const { comp } = createComponent({
         model: makeModel({ formatType: FileFormatType.EXPORT_TYPE_HTML, selectedBookmarks: ["b1"] }),
      });

      comp.selectBookmark("b2", 1, makeEvent());

      expect(comp.model.selectedBookmarks).toEqual(["b1"]);
   });

   it("should replace the selection with just the clicked bookmark on a plain click", () => {
      const { comp } = createComponent({
         model: makeModel({ selectedBookmarks: ["b1"], allBookmarks: ["b1", "b2", "b3"] }),
      });

      comp.selectBookmark("b2", 1, makeEvent());

      expect(comp.model.selectedBookmarks).toEqual(["b2"]);
   });

   it("should add an unselected bookmark to the selection on ctrl-click", () => {
      const { comp } = createComponent({
         model: makeModel({ selectedBookmarks: ["b1"], allBookmarks: ["b1", "b2", "b3"] }),
      });

      comp.selectBookmark("b2", 1, makeEvent({ ctrlKey: true }));

      expect(comp.model.selectedBookmarks).toEqual(["b1", "b2"]);
   });

   it("should remove an already-selected bookmark from the selection on ctrl-click", () => {
      const { comp } = createComponent({
         model: makeModel({ selectedBookmarks: ["b1", "b2"], allBookmarks: ["b1", "b2", "b3"] }),
      });

      comp.selectBookmark("b1", 0, makeEvent({ ctrlKey: true }));

      expect(comp.model.selectedBookmarks).toEqual(["b2"]);
   });

   it("should toggle via metaKey the same way as ctrlKey", () => {
      const { comp } = createComponent({
         model: makeModel({ selectedBookmarks: ["b1"], allBookmarks: ["b1", "b2", "b3"] }),
      });

      comp.selectBookmark("b2", 1, makeEvent({ metaKey: true }));

      expect(comp.model.selectedBookmarks).toEqual(["b1", "b2"]);
   });

   it("should select a forward range on shift-click from the first selected bookmark", () => {
      const { comp } = createComponent({
         model: makeModel({ selectedBookmarks: ["b1"], allBookmarks: ["b1", "b2", "b3", "b4"] }),
      });

      comp.selectBookmark("b3", 2, makeEvent({ shiftKey: true }));

      expect(comp.model.selectedBookmarks).toEqual(["b1", "b2", "b3"]);
   });

   it("should select a backward range on shift-click when clicking before the first selected bookmark", () => {
      const { comp } = createComponent({
         model: makeModel({ selectedBookmarks: ["b3"], allBookmarks: ["b1", "b2", "b3", "b4"] }),
      });

      comp.selectBookmark("b1", 0, makeEvent({ shiftKey: true }));

      expect(comp.model.selectedBookmarks).toEqual(["b1", "b2", "b3"]);
   });

   it("should select from the very first bookmark on shift-click when nothing was previously selected", () => {
      const { comp } = createComponent({
         model: makeModel({ selectedBookmarks: [], allBookmarks: ["b1", "b2", "b3"] }),
      });

      comp.selectBookmark("b2", 1, makeEvent({ shiftKey: true }));

      expect(comp.model.selectedBookmarks).toEqual(["b1", "b2"]);
   });
});

// ---------------------------------------------------------------------------
// Group 4: selectAll / clearAll [Risk 1]
// ---------------------------------------------------------------------------

describe("FileFormatPane — selectAll / clearAll", () => {
   it("should select every available bookmark", () => {
      const { comp } = createComponent({
         model: makeModel({ allBookmarks: ["b1", "b2"], selectedBookmarks: [] }),
      });

      comp.selectAll();

      expect(comp.model.selectedBookmarks).toEqual(["b1", "b2"]);
   });

   it("should copy allBookmarks rather than alias it", () => {
      const { comp } = createComponent({ model: makeModel({ allBookmarks: ["b1"] }) });
      comp.selectAll();
      expect(comp.model.selectedBookmarks).not.toBe(comp.model.allBookmarks);
   });

   it("should clear the selection", () => {
      const { comp } = createComponent({ model: makeModel({ selectedBookmarks: ["b1", "b2"] }) });
      comp.clearAll();
      expect(comp.model.selectedBookmarks).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 5: updateOnlyDataComponents [Risk 1]
// ---------------------------------------------------------------------------

describe("FileFormatPane — updateOnlyDataComponents", () => {
   it("should disable onlyDataComponents when matchLayout is enabled", () => {
      const { comp } = createComponent({ model: makeModel({ onlyDataComponents: true }) });
      comp.updateOnlyDataComponents(true);
      expect(comp.model.onlyDataComponents).toBe(false);
   });

   it("should leave onlyDataComponents untouched when matchLayout is disabled", () => {
      const { comp } = createComponent({ model: makeModel({ onlyDataComponents: true }) });
      comp.updateOnlyDataComponents(false);
      expect(comp.model.onlyDataComponents).toBe(true);
   });
});
