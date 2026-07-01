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
 * PreviewTableComponent — Pass 3: Display
 *
 * Risk-first coverage:
 *   Group 1  [Risk 2] — getSortLabel: 3-way switch must return the exact CSS class name
 *   Group 2  [Risk 2] — sortClicked: state-transition matrix (NONE→ASC→DESC→NONE, new col)
 *   Group 3  [Risk 3] — selectCell: ctrlKey deselect, row==0 rename, shiftKey range,
 *                       plain col==-1 row==-1 clear
 *   Group 4  [Risk 1] — isHeaderValid: checkHeaderValid gate + special-char pattern
 *   Group 5  [Risk 2] — getCellLabel: null cell, cellLabel priority, \n→\\n, cellData fallback
 *   Group 6  [Risk 1] — isTableStyleApplied: true/false scan of vsFormatModel
 *   Group 7  [Risk 2] — isSelected / selectRectangle / deselectCell / clearSelection
 *   Group 8  [Risk 1] — isRowVisible / tableBodyWidth / isForceTab
 *   Group 9  [Risk 1] — getTarget: delegates to HyperlinkViewModel.fromHyperlinkModel
 *   Group 10 [Risk 2] — updateVerticalScrollTooltip: placement left vs right
 *
 * Out of scope this pass: ngOnDestroy, ngAfterContentChecked, ngAfterViewChecked, onClick,
 *   horizontalScroll, resizeEnd, formatClicked, touchVScroll, touchHScroll,
 *   verticalScrollHandler, wheelScrollHandler, clickLink, apply, dragStart, dragOverTable,
 *   onLeave, dropOnTable, changeCellText, openVisibilityContextMenu, resizeListener
 *   → covered in preview-table.component.interaction.tl.spec.ts
 *   Async: tableData/colWidths setter microtasks, updateWidths setTimeout
 *   → covered in preview-table.component.risk.tl.spec.ts
 */

import { GuiTool } from "../../../common/util/gui-tool";
import { XConstants } from "../../../common/util/xconstants";
import { HyperlinkViewModel } from "../../../common/data/hyperlink-model";
import { SortInfo } from "./sort-info";
import { createPreviewComponent, makeCell, makeScrollable, makeTableData } from "./preview-table.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ── Group 1 — getSortLabel ────────────────────────────────────────────────────

describe("Group 1 — getSortLabel: 3-way switch", () => {
   it("should return 'sort-ascending' when sortInfo.col matches and sortValue=ASC", () => {
      const { comp } = createPreviewComponent();
      comp.sortInfo = { col: 1, sortValue: XConstants.SORT_ASC } as SortInfo;
      expect(comp.getSortLabel(1)).toBe("sort-ascending");
   });

   it("should return 'sort-descending' when sortInfo.col matches and sortValue=DESC", () => {
      const { comp } = createPreviewComponent();
      comp.sortInfo = { col: 1, sortValue: XConstants.SORT_DESC } as SortInfo;
      expect(comp.getSortLabel(1)).toBe("sort-descending");
   });

   it("should return 'sort' for SORT_NONE on the matching column", () => {
      const { comp } = createPreviewComponent();
      comp.sortInfo = { col: 1, sortValue: XConstants.SORT_NONE } as SortInfo;
      expect(comp.getSortLabel(1)).toBe("sort");
   });

   it("should return 'sort' when the queried column does not match sortInfo.col", () => {
      const { comp } = createPreviewComponent();
      comp.sortInfo = { col: 0, sortValue: XConstants.SORT_ASC } as SortInfo;
      expect(comp.getSortLabel(2)).toBe("sort");
   });

   it("should return 'sort' when sortInfo is null", () => {
      const { comp } = createPreviewComponent();
      comp.sortInfo = null;
      expect(comp.getSortLabel(0)).toBe("sort");
   });
});

// ── Group 2 — sortClicked: state-transition matrix ───────────────────────────

describe("Group 2 — sortClicked: state transitions", () => {
   it("should transition SORT_NONE → SORT_ASC when same column is clicked", () => {
      const { comp } = createPreviewComponent();
      comp.sortInfo = { col: 1, sortValue: XConstants.SORT_NONE } as SortInfo;
      comp.sortClicked(new MouseEvent("click"), 1);
      expect(comp.sortInfo.sortValue).toBe(XConstants.SORT_ASC);
   });

   it("should transition SORT_ASC → SORT_DESC when same column is clicked again", () => {
      const { comp } = createPreviewComponent();
      comp.sortInfo = { col: 1, sortValue: XConstants.SORT_ASC } as SortInfo;
      comp.sortClicked(new MouseEvent("click"), 1);
      expect(comp.sortInfo.sortValue).toBe(XConstants.SORT_DESC);
   });

   it("should transition SORT_DESC → SORT_NONE when same column is clicked again", () => {
      const { comp } = createPreviewComponent();
      comp.sortInfo = { col: 1, sortValue: XConstants.SORT_DESC } as SortInfo;
      comp.sortClicked(new MouseEvent("click"), 1);
      expect(comp.sortInfo.sortValue).toBe(XConstants.SORT_NONE);
   });

   it("should create new sortInfo with SORT_ASC when a different column is clicked", () => {
      const { comp } = createPreviewComponent();
      comp.sortInfo = { col: 0, sortValue: XConstants.SORT_DESC } as SortInfo;
      comp.sortClicked(new MouseEvent("click"), 2);
      expect(comp.sortInfo.col).toBe(2);
      expect(comp.sortInfo.sortValue).toBe(XConstants.SORT_ASC);
   });

   it("should emit onSort with updated sortInfo after each click", () => {
      const { comp } = createPreviewComponent();
      const emitted: SortInfo[] = [];
      comp.onSort.subscribe(s => emitted.push(s));
      comp.sortInfo = { col: 0, sortValue: XConstants.SORT_ASC } as SortInfo;

      comp.sortClicked(new MouseEvent("click"), 0);

      expect(emitted).toHaveLength(1);
      expect(emitted[0].sortValue).toBe(XConstants.SORT_DESC);
   });
});

// ── Group 3 — selectCell: conditional paths ───────────────────────────────────

describe("Group 3 — selectCell: conditional paths", () => {
   it("should deselect a cell when ctrlKey is pressed on an already-selected cell", () => {
      const { comp } = createPreviewComponent();
      comp.selectCell(new MouseEvent("click"), 1, 0);
      expect(comp.isSelected(1, 0)).toBe(true);

      comp.selectCell(new MouseEvent("click", { ctrlKey: true }), 1, 0);
      expect(comp.isSelected(1, 0)).toBe(false);
   });

   it("should set renaming=true and keep selection when row==0 is clicked twice", () => {
      const { comp } = createPreviewComponent();
      // first click: not yet selected → plain select
      comp.selectCell(new MouseEvent("click"), 0, 1);
      expect(comp.renaming).toBe(false);

      // second click on same header cell: row==0, already selected → renaming
      comp.selectCell(new MouseEvent("click"), 0, 1);
      expect(comp.renaming).toBe(true);
   });

   it("should expand the selection rectangle when shiftKey is held and lastSelection exists", () => {
      const { comp } = createPreviewComponent();
      comp.selectCell(new MouseEvent("click"), 0, 0);
      // shift-click to extend selection to (1, 2)
      comp.selectCell(new MouseEvent("click", { shiftKey: true }), 1, 2);

      // rectangle (0,0)–(1,2): rows 0-1 in cols 0-2
      expect(comp.isSelected(0, 0)).toBe(true);
      expect(comp.isSelected(1, 0)).toBe(true);
      expect(comp.isSelected(0, 2)).toBe(true);
      expect(comp.isSelected(1, 2)).toBe(true);
   });

   it("should clear columnSelection when col==-1 is passed (row-header deselect)", () => {
      const { comp } = createPreviewComponent();
      comp.selectCell(new MouseEvent("click"), 0, 0);
      expect(comp.columnSelection.length).toBeGreaterThan(0);

      comp.selectCell(new MouseEvent("click"), 0, -1);
      expect(comp.columnSelection).toHaveLength(0);
   });
});

// ── Group 4 — isHeaderValid ───────────────────────────────────────────────────

describe("Group 4 — isHeaderValid", () => {
   it("should always return true when checkHeaderValid=false", () => {
      const { comp } = createPreviewComponent();
      comp.checkHeaderValid = false;
      // cell with special chars: still valid because gate is off
      const cell = makeCell({ cellLabel: "col@#$" });
      expect(comp.isHeaderValid(cell)).toBe(true);
   });

   it("should return false when checkHeaderValid=true and label has special characters", () => {
      const { comp } = createPreviewComponent();
      comp.checkHeaderValid = true;
      const cell = makeCell({ cellLabel: "col@name" }); // '@' fails the regex
      expect(comp.isHeaderValid(cell)).toBe(false);
   });

   it("should return true when checkHeaderValid=true and label is alphanumeric", () => {
      const { comp } = createPreviewComponent();
      comp.checkHeaderValid = true;
      const cell = makeCell({ cellLabel: "ValidName" });
      expect(comp.isHeaderValid(cell)).toBe(true);
   });
});

// ── Group 5 — getCellLabel ────────────────────────────────────────────────────

describe("Group 5 — getCellLabel", () => {
   it("should return empty string for null cell", () => {
      const { comp } = createPreviewComponent();
      expect(comp.getCellLabel(null)).toBe("");
   });

   it("should return cellLabel when present, over cellData", () => {
      const { comp } = createPreviewComponent();
      const cell = makeCell({ cellLabel: "Header", cellData: "raw" });
      expect(comp.getCellLabel(cell)).toBe("Header");
   });

   it("should fall back to cellData when cellLabel is null", () => {
      const { comp } = createPreviewComponent();
      const cell = makeCell({ cellLabel: null, cellData: "rawData" });
      expect(comp.getCellLabel(cell)).toBe("rawData");
   });

   it("should replace \\n with \\\\n to avoid literal newlines in cell labels", () => {
      // 🔁 Regression-sensitive: newlines in labels would break table cell rendering
      const { comp } = createPreviewComponent();
      const cell = makeCell({ cellLabel: "Line1\nLine2", cellData: null });
      expect(comp.getCellLabel(cell)).toBe("Line1\\nLine2");
   });

   it("should return empty string when both cellLabel and cellData are null", () => {
      const { comp } = createPreviewComponent();
      const cell = makeCell({ cellLabel: null, cellData: null });
      expect(comp.getCellLabel(cell)).toBe("");
   });
});

// ── Group 6 — isTableStyleApplied ────────────────────────────────────────────

describe("Group 6 — isTableStyleApplied", () => {
   it("should return false when no cell has a vsFormatModel", () => {
      const { comp } = createPreviewComponent({
         tableData: makeTableData(2, 2),   // cells have vsFormatModel: null
      });
      expect(comp.isTableStyleApplied()).toBe(false);
   });

   it("should return true when any cell has a non-null vsFormatModel", () => {
      const data = makeTableData(2, 2);
      data[1][1] = makeCell({ row: 1, col: 1, vsFormatModel: { alpha: 1 } as any });
      const { comp } = createPreviewComponent({ tableData: data });
      expect(comp.isTableStyleApplied()).toBe(true);
   });
});

// ── Group 7 — isSelected / selectRectangle / deselectCell / clearSelection ───

describe("Group 7 — selection state management", () => {
   it("isSelected should return false before any selection", () => {
      const { comp } = createPreviewComponent();
      expect(comp.isSelected(0, 0)).toBe(false);
   });

   it("isSelected should return true after selectCell", () => {
      const { comp } = createPreviewComponent();
      comp.selectCell(new MouseEvent("click"), 1, 0);
      expect(comp.isSelected(1, 0)).toBe(true);
      expect(comp.isSelected(0, 0)).toBe(false); // different row
   });

   it("selectRectangle should mark all cells within the given bounds as selected", () => {
      const { comp } = createPreviewComponent();
      comp.selectRectangle(0, 0, 1, 1); // rows 0-1, cols 0-1
      expect(comp.isSelected(0, 0)).toBe(true);
      expect(comp.isSelected(0, 1)).toBe(true);
      expect(comp.isSelected(1, 0)).toBe(true);
      expect(comp.isSelected(1, 1)).toBe(true);
      expect(comp.isSelected(2, 0)).toBe(false); // outside
   });

   it("deselectCell should remove a single cell from selectedData", () => {
      const { comp } = createPreviewComponent();
      comp.selectCell(new MouseEvent("click"), 1, 0);
      expect(comp.isSelected(1, 0)).toBe(true);

      comp.deselectCell(1, 0);
      expect(comp.isSelected(1, 0)).toBe(false);
   });

   it("clearSelection should reset selectedData and columnSelection", () => {
      const { comp } = createPreviewComponent();
      comp.selectCell(new MouseEvent("click"), 1, 0);
      comp.clearSelection();
      expect(comp.columnSelection).toHaveLength(0);
      expect(comp.isSelected(1, 0)).toBe(false);
   });
});

// ── Group 8 — isRowVisible / tableBodyWidth / isForceTab ─────────────────────

describe("Group 8 — isRowVisible / tableBodyWidth / isForceTab", () => {
   it("isRowVisible should return true for rows in the visible window [currentRow, currentRow+100)", () => {
      const { comp } = createPreviewComponent();
      (comp as any).currentRow = 5;
      expect(comp.isRowVisible(5)).toBe(true);
      expect(comp.isRowVisible(104)).toBe(true);
      expect(comp.isRowVisible(105)).toBe(false); // >= currentRow+100
      expect(comp.isRowVisible(4)).toBe(false);   // < currentRow
   });

   it("tableBodyWidth should be tableWidth + 2 (borderWidth * 2)", () => {
      const { comp } = createPreviewComponent();
      const expectedWidth = (comp as any).tableWidth + 2;
      expect(comp.tableBodyWidth).toBe(expectedWidth);
   });

   it("isForceTab should return false when contextProvider.composer is false", () => {
      const { comp } = createPreviewComponent({ contextProvider: { composer: false } });
      expect(comp.isForceTab()).toBe(false);
   });

   it("isForceTab should return true when contextProvider.composer is true", () => {
      const { comp } = createPreviewComponent({ contextProvider: { composer: true } });
      expect(comp.isForceTab()).toBe(true);
   });
});

// ── Group 9 — getTarget ───────────────────────────────────────────────────────

describe("Group 9 — getTarget: delegates to HyperlinkViewModel.fromHyperlinkModel", () => {
   it("should return the target string from the resolved HyperlinkViewModel", () => {
      const { comp } = createPreviewComponent();
      const mockModel = { target: "_self" };
      vi.spyOn(HyperlinkViewModel, "fromHyperlinkModel").mockReturnValue(mockModel as any);

      const cell = makeCell({ hyperlinks: [{ linkType: 1 } as any] });
      expect(comp.getTarget(cell)).toBe("_self");
   });

   it("should return null when HyperlinkViewModel.fromHyperlinkModel returns null", () => {
      const { comp } = createPreviewComponent();
      vi.spyOn(HyperlinkViewModel, "fromHyperlinkModel").mockReturnValue(null);
      const cell = makeCell({ hyperlinks: [{ linkType: 1 } as any] });
      expect(comp.getTarget(cell)).toBeNull();
   });
});

// ── Group 10 — updateVerticalScrollTooltip: placement ────────────────────────

describe("Group 10 — updateVerticalScrollTooltip: tooltip placement", () => {
   it("should set placement to 'right' when tooltip fits within the viewport", () => {
      const { comp } = createPreviewComponent();
      makeScrollable(comp);
      // GuiTool.measureText returns a small width → total x well within 1024
      vi.spyOn(GuiTool, "measureText").mockReturnValue(20);
      // right = 500, total = 520 ≤ 1024 → right placement
      (comp as any).verticalScrollWrapper.nativeElement.getBoundingClientRect
         .mockReturnValue({ right: 500, height: 100 });

      comp.updateVerticalScrollTooltip(true);

      expect((comp as any).verticalScrollTooltip.placement).toBe("right");
   });

   it("should set placement to 'left' when tooltip would overflow the viewport", () => {
      const { comp } = createPreviewComponent();
      makeScrollable(comp);
      // measureText returns big width so right + width > window.innerWidth (1024)
      vi.spyOn(GuiTool, "measureText").mockReturnValue(200);
      (comp as any).verticalScrollWrapper.nativeElement.getBoundingClientRect
         .mockReturnValue({ right: 900, height: 100 });  // 900+200+10 > 1024

      comp.updateVerticalScrollTooltip(true);

      expect((comp as any).verticalScrollTooltip.placement).toBe("left");
   });

   it("should update currentRow from scrollY / cellHeight", () => {
      const { comp } = createPreviewComponent();
      comp.scrollY = 84;  // cellHeight = 28, floor(84/28) = 3
      comp.updateVerticalScrollTooltip(false); // not scrollable by default, so no tooltip open
      expect((comp as any).currentRow).toBe(3);
   });
});
