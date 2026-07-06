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
 * VSTable — Pass 3: Display
 *
 * Pure conditional/label-computation branches driven by model state — no DOM required.
 *
 *   Group 1  — isSorted: null model; ASC→asc/desc; DESC→desc; other→none
 *   Group 2  — displaySortNumber: sortPositionNum>1+col>-1; num≤1; col=-1
 *   Group 3  — sortPositionNum: count of columns with position>-1
 *   Group 4  — getRowVisibility: 1 (visible), 2 (quick-scroll), 0 (outside)
 *   Group 5  — isHeaderSortEnabled: !embedded; embedded+viewer+form+enabled; embedded+!viewer
 *   Group 6  — isHeaderSortVisible: null type; type=0; type=1
 *   Group 7  — getDetailTableTopPosition: no loadedRows; start=0; start>0
 *   Group 8  — getHeaderRowHeight: wrapped+headerRowCount=1; non-wrapped
 *   Group 9  — getTooltip: no rowHyperlinks (super path); row tooltip; dataTip suffix
 *   Group 10 — selectCell modifier-key paths: ctrlKey multi-add; shiftKey range; plain clear;
 *                right-click-on-selected no-op
 *   Group 11 — displayColWidths: sum<width expands last col; sum>=width no expand
 *   Group 12 — getObjectTop: non-viewer/non-shrink/maxMode early return; shrink+bottomTabs offset
 */

import { ViewsheetInfo } from "../../data/viewsheet-info";
import { XConstants } from "../../../common/util/xconstants";
import {
   createTableComponent,
   makeTableCell,
} from "./vs-table.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("VSTable — Pass 3: Display", () => {

   // ── Group 1 — isSorted ────────────────────────────────────────────────────
   describe("Group 1 — isSorted", () => {
      it("should return null when headersSortType is null", () => {
         const { comp } = createTableComponent();
         comp.model.headersSortType = null;

         expect(comp.isSorted(0, "asc")).toBeNull();
      });

      it("should return true for type='asc' when column has SORT_ASC", () => {
         const { comp } = createTableComponent();
         comp.model.headersSortType = [XConstants.SORT_ASC, 0, 0];

         expect(comp.isSorted(0, "asc")).toBe(true);
      });

      it("should return false for type='desc' when column has SORT_ASC", () => {
         const { comp } = createTableComponent();
         comp.model.headersSortType = [XConstants.SORT_ASC, 0, 0];

         expect(comp.isSorted(0, "desc")).toBe(false);
      });

      it("should return true for type='desc' when column has SORT_DESC", () => {
         const { comp } = createTableComponent();
         comp.model.headersSortType = [XConstants.SORT_DESC, 0, 0];

         expect(comp.isSorted(0, "desc")).toBe(true);
      });

      it("should return true for type='none' when column has SORT_NONE (default)", () => {
         const { comp } = createTableComponent();
         comp.model.headersSortType = [XConstants.SORT_NONE, 0, 0];

         expect(comp.isSorted(0, "none")).toBe(true);
      });
   });

   // ── Group 2 — displaySortNumber ───────────────────────────────────────────
   describe("Group 2 — displaySortNumber", () => {
      it("should return col+1 when sortPositionNum>1 and column sort position >-1", () => {
         const { comp } = createTableComponent();
         // sortPositions: [-1, 0, 1] → two columns with position > -1 → sortPositionNum()=2 > 1
         comp.model.sortPositions = [-1, 0, 1];
         const cell = makeTableCell({ row: 0, col: 2 });

         // sortPositions[2]=1 → result = 1+1 = 2
         expect(comp.displaySortNumber(cell)).toBe(2);
      });

      it("should return 0 when sortPositionNum is 1 (single sort)", () => {
         const { comp } = createTableComponent();
         comp.model.sortPositions = [-1, 0, -1];
         const cell = makeTableCell({ row: 0, col: 1 });

         // only one column with sortPositions > -1 → sortPositionNum=1 → return 0
         expect(comp.displaySortNumber(cell)).toBe(0);
      });

      it("should return 0 when column sort position is -1 even if sortPositionNum>1", () => {
         const { comp } = createTableComponent();
         comp.model.sortPositions = [-1, 0, 1];
         const cell = makeTableCell({ row: 0, col: 0 });

         // sortPositions[0]=-1 → condition false → return 0
         expect(comp.displaySortNumber(cell)).toBe(0);
      });
   });

   // ── Group 3 — sortPositionNum ─────────────────────────────────────────────
   describe("Group 3 — sortPositionNum", () => {
      it("should return count of columns with sortPosition > -1", () => {
         const { comp } = createTableComponent();
         comp.model.sortPositions = [0, 1, 2];

         expect((comp as any).sortPositionNum()).toBe(3);
      });

      it("should return 0 when all sortPositions are -1", () => {
         const { comp } = createTableComponent();
         comp.model.sortPositions = [-1, -1, -1];

         expect((comp as any).sortPositionNum()).toBe(0);
      });

      it("should count only the non-(-1) positions", () => {
         const { comp } = createTableComponent();
         comp.model.sortPositions = [1, -1, 0];

         expect((comp as any).sortPositionNum()).toBe(2);
      });
   });

   // ── Group 4 — getRowVisibility ────────────────────────────────────────────
   // getRowVisibility returns: 1 = visible, 2 = quick-scroll buffer, 0 = ignored.
   // loadedRows, currentRow, lastVisibleRow are private/protected — seeded directly because
   // no public API populates them outside of the full command-dispatch flow.
   describe("Group 4 — getRowVisibility", () => {
      it("should return 1 for a header row (idx < headerRowCount)", () => {
         const { comp } = createTableComponent();
         // header row has idx=0 < headerRowCount=1 → isRowVisible returns true
         (comp as any).loadedRows = { start: 0, end: 20 };
         (comp as any).lastVisibleRow = 10;

         expect(comp.getRowVisibility(0)).toBe(1);
      });

      it("should return 2 for a data row idx<200 not currently visible", () => {
         const { comp } = createTableComponent();
         // currentRow=0, lastVisibleRow=5 → idx=100 is out of view but < 200
         (comp as any).loadedRows = { start: 0, end: 200 };
         (comp as any).currentRow = 0;
         (comp as any).lastVisibleRow = 5;

         expect(comp.getRowVisibility(100)).toBe(2);
      });

      it("should return 0 for a data row idx>=200 not currently visible", () => {
         const { comp } = createTableComponent();
         (comp as any).loadedRows = { start: 0, end: 300 };
         (comp as any).currentRow = 0;
         (comp as any).lastVisibleRow = 5;

         expect(comp.getRowVisibility(250)).toBe(0);
      });
   });

   // ── Group 5 — isHeaderSortEnabled ─────────────────────────────────────────
   // sortColumnVisible is protected — seeded directly in every test because ngOnChanges
   // populates it via isActionVisibleInViewer("Sort Column") which requires a full actions
   // dispatch; there is no public setter.
   describe("Group 5 — isHeaderSortEnabled", () => {
      it("should return true when table is not embedded and sortColumnVisible=true", () => {
         const { comp } = createTableComponent({ model: { embedded: false } });
         (comp as any).sortColumnVisible = true;

         expect(comp.isHeaderSortEnabled()).toBe(true);
      });

      it("should return true when embedded + viewer + form + enabled", () => {
         const { comp } = createTableComponent({
            model: { embedded: true, form: true, enabled: true },
         });
         (comp as any).sortColumnVisible = true;
         // contextProvider.viewer=true by default in test-helpers

         expect(comp.isHeaderSortEnabled()).toBe(true);
      });

      it("should return false when embedded and not in viewer mode", () => {
         const { comp } = createTableComponent({
            model: { embedded: true, form: true, enabled: true },
            contextProvider: { viewer: false, preview: false, binding: false, composer: true, vsWizard: false, vsWizardPreview: false, embedAssembly: false },
         });
         (comp as any).sortColumnVisible = true;

         expect(comp.isHeaderSortEnabled()).toBe(false);
      });
   });

   // ── Group 6 — isHeaderSortVisible ─────────────────────────────────────────
   describe("Group 6 — isHeaderSortVisible", () => {
      it("should return false when headersSortType is null", () => {
         const { comp } = createTableComponent();
         comp.model.headersSortType = null;
         const cell = makeTableCell({ col: 0 });

         expect(comp.isHeaderSortVisible(cell)).toBe(false);
      });

      it("should return false when headersSortType[col] is 0 (SORT_NONE)", () => {
         const { comp } = createTableComponent();
         comp.model.headersSortType = [0, 1, 2];
         const cell = makeTableCell({ col: 0 });

         expect(comp.isHeaderSortVisible(cell)).toBe(false);
      });

      it("should return true when headersSortType[col] is non-zero", () => {
         const { comp } = createTableComponent();
         comp.model.headersSortType = [0, 1, 2];
         const cell = makeTableCell({ col: 1 });

         expect(comp.isHeaderSortVisible(cell)).toBe(true);
      });
   });

   // ── Group 7 — getDetailTableTopPosition ───────────────────────────────────
   // scrollY and topRowHeight are protected/private — seeded directly because the public
   // surface only updates them via scroll events and loadTableData (full DOM interaction).
   describe("Group 7 — getDetailTableTopPosition", () => {
      it("should return -scrollY - headerHeight when loadedRows is null", () => {
         const { comp } = createTableComponent();
         (comp as any).loadedRows = null;
         (comp as any).scrollY = 0;
         // headerHeight = sum(headerRowHeights) = 20

         expect(comp.getDetailTableTopPosition()).toBe(-20);
      });

      it("should return -scrollY - headerHeight when loadedRows.start=0", () => {
         const { comp } = createTableComponent();
         (comp as any).loadedRows = { start: 0, end: 10 };
         (comp as any).scrollY = 30;
         // -30 - 20 = -50

         expect(comp.getDetailTableTopPosition()).toBe(-50);
      });

      it("should return -scrollY + topRowHeight when loadedRows.start>0", () => {
         const { comp } = createTableComponent();
         (comp as any).loadedRows = { start: 5, end: 20 };
         (comp as any).scrollY = 30;
         (comp as any).topRowHeight = 90;
         // -30 + 90 = 60

         expect(comp.getDetailTableTopPosition()).toBe(60);
      });
   });

   // ── Group 8 — getHeaderRowHeight ──────────────────────────────────────────
   describe("Group 8 — getHeaderRowHeight", () => {
      it("should return getHeaderHeight() when wrapped=true and headerRowCount=1", () => {
         const { comp } = createTableComponent({
            // headerRowPositions[headerRowCount] = [0,24][1] = 24
            // (getHeaderHeight uses headerRowPositions, not headerRowHeights, when wrapped=true)
            model: { wrapped: true, headerRowCount: 1, headerRowHeights: [24], headerRowPositions: [0, 24] },
         });

         expect(comp.getHeaderRowHeight(0)).toBe(24);
      });

      it("should return getRowHeight(row) when not wrapped", () => {
         const { comp } = createTableComponent({
            model: { wrapped: false, headerRowCount: 1, headerRowHeights: [20], dataRowHeight: 18 },
         });

         // getRowHeight(0): row<headerRowCount → headerRowHeights[0 % 1]=20
         expect(comp.getHeaderRowHeight(0)).toBe(20);
      });
   });

   // ── Group 9 — getTooltip ──────────────────────────────────────────────────
   describe("Group 9 — getTooltip", () => {
      it("should fall back to super.getTooltip when no rowHyperlinks", () => {
         const { comp } = createTableComponent();
         comp.rowHyperlinks = [];
         const cell = makeTableCell({ cellLabel: "CellLabel" });

         expect(comp.getTooltip(cell)).toBe("CellLabel");
      });

      it("should return row hyperlink tooltip when rowHyperlinks present and row>-1", () => {
         const { comp } = createTableComponent();
         comp.rowHyperlinks = [{ tooltip: "Row tip", label: "", url: "", linkType: 0 } as any];
         const cell = makeTableCell({ hyperlinks: [] });

         expect(comp.getTooltip(cell, 0)).toBe("Row tip");
      });

      it("should fall back to super when row tooltip is empty", () => {
         const { comp } = createTableComponent();
         comp.rowHyperlinks = [{ tooltip: "", label: "", url: "", linkType: 0 } as any];
         const cell = makeTableCell({ cellLabel: "Data" });

         // tooltip is empty → length=0 → falls through to super
         expect(comp.getTooltip(cell, 0)).toBe("Data");
      });

      it("should append ctrlSelect suffix when !mobileDevice, dataTip set, and isTipOnClick=true", () => {
         const { comp } = createTableComponent({ model: { dataTip: "MyTip", isTipOnClick: true } });
         comp.rowHyperlinks = [{ tooltip: "Row tip", label: "", url: "", linkType: 0 } as any];
         // mobileDevice=false by default in jsdom (GuiTool.isMobileDevice() = false)
         const cell = makeTableCell({ hyperlinks: [] });

         const result = comp.getTooltip(cell, 0);

         // "Row tip" + "_#(js:composer.graph.ctrlSelect)"
         expect(result).toContain("Row tip");
         expect(result).toContain("_#(js:composer.graph.ctrlSelect)");
      });
   });

   // ── Group 10 — selectCell modifier-key paths ──────────────────────────────
   describe("Group 10 — selectCell modifier-key paths", () => {
      // All tests run in viewer context (default) so the early-return guard is bypassed.
      // model.sortPositions must exist for the header path.

      it("ctrlKey path: should add to existing selection without clearing", () => {
         const { comp } = createTableComponent();
         comp.model.selectedData = new Map([[2, [0]]]);
         comp.model.firstSelectedRow = 2;
         comp.model.firstSelectedColumn = 0;
         const cell = makeTableCell({ row: 3, col: 1 });
         const event = { button: 0, ctrlKey: true, shiftKey: false, stopPropagation: vi.fn(), ignoreClick: false } as any;

         comp.selectCell(event, cell, false);

         const cols = comp.model.selectedData?.get(3);
         expect(cols).toContain(1);
         // prior row-2 selection should still be there
         expect(comp.model.selectedData?.get(2)).toContain(0);
      });

      it("ctrlKey path: should deselect when clicking an already-selected cell with ctrl", () => {
         const { comp } = createTableComponent();
         comp.model.selectedData = new Map([[1, [0]]]);
         const cell = makeTableCell({ row: 1, col: 0 });
         const event = { button: 0, ctrlKey: true, shiftKey: false, stopPropagation: vi.fn(), ignoreClick: false } as any;

         comp.selectCell(event, cell, false);

         const cols = comp.model.selectedData?.get(1);
         expect(cols ?? []).not.toContain(0);
      });

      it("plain click: should clear existing selection and set only clicked cell", () => {
         const { comp } = createTableComponent();
         comp.model.selectedData = new Map([[0, [0, 1]], [1, [2]]]);
         const cell = makeTableCell({ row: 2, col: 1 });
         const event = { button: 0, ctrlKey: false, shiftKey: false, stopPropagation: vi.fn(), ignoreClick: false } as any;

         comp.selectCell(event, cell, false);

         expect(comp.model.selectedData?.size).toBe(1);
         expect(comp.model.selectedData?.get(2)).toContain(1);
      });

      it("right-click on selected cell: should not change selection", () => {
         const { comp } = createTableComponent();
         comp.model.selectedData = new Map([[0, [0]]]);
         const cell = makeTableCell({ row: 0, col: 0 });
         const event = { button: 2, ctrlKey: false, shiftKey: false, stopPropagation: vi.fn(), ignoreClick: false } as any;

         comp.selectCell(event, cell, false);

         // selection unchanged: still has row 0, col 0
         expect(comp.model.selectedData?.get(0)).toContain(0);
      });

      it("shiftKey path: should range-select from lastSelected to clicked cell", () => {
         const { comp } = createTableComponent();
         comp.model.lastSelected = { row: 1, column: 0 };
         comp.model.selectedData = new Map([[1, [0]]]);
         // loadedRows is private — seeded directly because only loadTableData populates it,
         // which requires a full command-dispatch flow not exercised here.
         (comp as any).loadedRows = { start: 0, end: 10 };
         // tableData/tableHeaders initialized to empty arrays so the if(tableRow) guard
         // is reached safely — addDataPath is skipped, selection range is still populated.
         comp.tableData = [];
         comp.tableHeaders = [];
         const cell = makeTableCell({ row: 3, col: 0 });
         const event = { button: 0, ctrlKey: false, shiftKey: true, stopPropagation: vi.fn(), ignoreClick: false } as any;

         comp.selectCell(event, cell, false);

         // rows 1→3 should all be in selectedData
         expect(comp.model.selectedData?.has(1)).toBe(true);
         expect(comp.model.selectedData?.has(2)).toBe(true);
         expect(comp.model.selectedData?.has(3)).toBe(true);
      });

      it("middle-click (button=1): should return early without modifying selection", () => {
         const { comp } = createTableComponent();
         comp.model.selectedData = new Map([[0, [0]]]);
         const cell = makeTableCell({ row: 1, col: 1 });
         const event = { button: 1, ctrlKey: false, shiftKey: false, stopPropagation: vi.fn(), ignoreClick: false } as any;

         comp.selectCell(event, cell, false);

         // only the original row/col should remain
         expect(comp.model.selectedData?.get(0)).toContain(0);
         expect(comp.model.selectedData?.get(1)).toBeUndefined();
      });
   });

   // ── Group 11 — displayColWidths ───────────────────────────────────────────
   // updateDisplayColumnWidth is called inside updateLayout() whenever model is set.
   // The factory sets model synchronously, so displayColWidths is already populated
   // by the time createTableComponent() returns.
   describe("Group 11 — displayColWidths: updateDisplayColumnWidth", () => {
      // updateDisplayColumnWidth is called from ngOnChanges, not from the model setter.
      // Direct-instantiation tests must call it explicitly because Angular's change
      // detection lifecycle (ngOnChanges) is never triggered without a TestBed host.

      it("should expand the last column when sum(colWidths) < container width", () => {
         // sum(30+30+90+40)=190 < objectFormat.width=300 → last col expands by 110 → 150
         const { comp } = createTableComponent({
            model: { colWidths: [30, 30, 90, 40], colCount: 4 },
         });
         comp.updateDisplayColumnWidth();

         expect(comp.displayColWidths[3]).toBe(150);
      });

      it("should not expand any column when sum(colWidths) >= container width", () => {
         // sum(100+100+200)=400 >= 300 → no expansion, cols stay as-is
         const { comp } = createTableComponent({
            model: { colWidths: [100, 100, 200], colCount: 3 },
         });
         comp.updateDisplayColumnWidth();

         expect(comp.displayColWidths[2]).toBe(200);
      });
   });

   // ── Group 12 — getObjectTop ───────────────────────────────────────────────
   // getObjectTop shifts a shrunk table down inside a bottom-tabs VSTab so its
   // rendered bottom stays flush with the tab strip. The early-return guards cover
   // most cases; only the one specific combination triggers the offset.
   describe("Group 12 — getObjectTop: shrink+bottomTabs offset", () => {
      it("should return objectFormat.top when not in viewer context", () => {
         const { comp } = createTableComponent({
            contextProvider: {
               viewer: false, preview: false, binding: false, composer: true,
               vsWizard: false, vsWizardPreview: false, embedAssembly: false,
            },
            model: { shrink: true },
         });

         expect(comp.getObjectTop()).toBe(comp.model.objectFormat.top);
      });

      it("should return objectFormat.top when shrink=false", () => {
         const { comp } = createTableComponent({ model: { shrink: false } });

         expect(comp.getObjectTop()).toBe(comp.model.objectFormat.top);
      });

      it("should return objectFormat.top when maxMode=true even with shrink=true", () => {
         const { comp } = createTableComponent({ model: { shrink: true, maxMode: true } as any });

         expect(comp.getObjectTop()).toBe(comp.model.objectFormat.top);
      });

      it("should return objectFormat.top when containerType is not VSTab", () => {
         const { comp } = createTableComponent({
            model: { shrink: true, containerType: "VSTable" },
         });

         // guard: containerType === "VSTab" is false — offset skipped
         expect(comp.getObjectTop()).toBe(comp.model.objectFormat.top);
      });

      it("should return objectFormat.top when vsInfo is null even with containerType VSTab", () => {
         const { comp } = createTableComponent({
            model: { shrink: true, containerType: "VSTab", container: "tab1" },
         });
         comp.vsInfo = null;

         // guard: this.vsInfo is falsy — offset skipped
         expect(comp.getObjectTop()).toBe(comp.model.objectFormat.top);
      });

      it("should return objectFormat.top when parent VSTab has bottomTabs=false", () => {
         const { comp } = createTableComponent({
            model: { shrink: true, containerType: "VSTab", container: "tab1" },
         });
         comp.vsInfo = new ViewsheetInfo(
            [{ absoluteName: "tab1", bottomTabs: false }] as any, null, false, "vs1",
         );

         expect(comp.getObjectTop()).toBe(comp.model.objectFormat.top);
      });

      it("should offset top by (designHeight - renderedHeight) when shrink=true and bottomTabs=true", () => {
         const { comp } = createTableComponent({
            model: { shrink: true, containerType: "VSTab", container: "tab1" },
         });
         const designTop = comp.model.objectFormat.top;       // 50
         const designHeight = comp.model.objectFormat.height; // 200
         comp.vsInfo = new ViewsheetInfo(
            [{ absoluteName: "tab1", bottomTabs: true }] as any, null, false, "vs1",
         );
         // getObjectHeight() implementation reads tableHeight+scrollHeight — mock to control the offset.
         vi.spyOn(comp as any, "getObjectHeight").mockReturnValue(120);

         // top + (height - renderedHeight) = 50 + (200 - 120) = 130
         expect(comp.getObjectTop()).toBe(designTop + designHeight - 120);
      });
   });
});
