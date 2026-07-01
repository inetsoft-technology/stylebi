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
 * PreviewTableComponent — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 1] — ngOnDestroy: window-listener teardown via clearListeners
 *   Group 2  [Risk 1] — ngAfterContentChecked / ngAfterViewChecked: height-change → detectChanges
 *   Group 3  [Risk 2] — onClick (@HostListener document:click): clears selection when outside host
 *   Group 4  [Risk 1] — horizontalScroll / resizeListener: scroll-state updates
 *   Group 5  [Risk 3] — resizeEnd: HTTP PUT via modelService (branch by isDetails)
 *   Group 6  [Risk 2] — formatClicked: subscribes to formatGetter and sets formatModel
 *   Group 7  [Risk 2] — sortClicked: emits onSort with updated SortInfo (basic happy path)
 *   Group 8  [Risk 1] — touchVScroll / touchHScroll: delta-based scroll updates
 *   Group 9  [Risk 1] — verticalScrollHandler / wheelScrollHandler: native scroll handlers
 *   Group 10 [Risk 2] — clickLink: invokes hyperlinkService to build and show actions
 *   Group 11 [Risk 2] — apply: emits onFormatChange and closes all dropdowns
 *   Group 12 [Risk 3] — DnD: dragStart/dragOverTable/onLeave/dropOnTable lifecycle → emits onOrder
 *   Group 13 [Risk 3] — changeCellText: Enter key → emits onRename with new column name
 *   Group 14 [Risk 2] — openVisibilityContextMenu: opens dropdown with hide/show-columns actions
 *
 * Out of scope this pass:
 *   getSortLabel, selectCell display-paths, isHeaderValid, getCellLabel, isTableStyleApplied,
 *   isSelected, selectRectangle, deselectCell, clearSelection, isRowVisible, tableBodyWidth,
 *   isForceTab, getTarget, updateVerticalScrollTooltip
 *   → covered in preview-table.component.display.tl.spec.ts
 *   Async: tableData/colWidths setter microtasks, updateWidths setTimeout
 *   → covered in preview-table.component.risk.tl.spec.ts
 */

import { of } from "rxjs";
import { SortInfo } from "./sort-info";
import { DetailDndInfo } from "./detail-dnd-info";
import {
   createPreviewComponent,
   makeCell,
   makeScrollable,
   makeTableData,
} from "./preview-table.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ── Group 1 — ngOnDestroy ─────────────────────────────────────────────────────

describe("Group 1 — ngOnDestroy: window-listener teardown", () => {
   it("should call each registered window listener to remove it", () => {
      const { comp, renderer } = createPreviewComponent();
      const removeFn1 = vi.fn();
      const removeFn2 = vi.fn();
      renderer.listen
         .mockReturnValueOnce(removeFn1)
         .mockReturnValueOnce(removeFn2);

      comp.startResize({ preventDefault: vi.fn(), pageX: 100 } as any, 0);
      comp.ngOnDestroy();

      expect(removeFn1).toHaveBeenCalled();
      expect(removeFn2).toHaveBeenCalled();
   });

   it("should clear the windowListeners array after destroy", () => {
      const { comp } = createPreviewComponent();
      comp.startResize({ preventDefault: vi.fn(), pageX: 100 } as any, 0);
      comp.ngOnDestroy();
      expect((comp as any).windowListeners).toHaveLength(0);
   });
});

// ── Group 2 — ngAfterContentChecked / ngAfterViewChecked ─────────────────────

describe("Group 2 — ngAfterContentChecked / ngAfterViewChecked", () => {
   it("ngAfterContentChecked should update horizontalDist from previewContainer state", () => {
      const { comp, previewContainerEl } = createPreviewComponent();
      previewContainerEl.clientWidth = 600;
      previewContainerEl.scrollLeft = 50;
      comp.ngAfterContentChecked();
      // horizontalDist = clientWidth + scrollLeft = 650
      expect((comp as any).horizontalDist).toBe(650);
   });

   it("ngAfterViewChecked should call detectChanges when headerTable height changed", () => {
      const { comp, changeRef } = createPreviewComponent();
      (comp as any).headerTable.nativeElement.offsetHeight = 50; // different from stored 28
      comp.ngAfterViewChecked();
      expect(changeRef.detectChanges).toHaveBeenCalled();
   });

   it("ngAfterViewChecked should NOT call detectChanges when heights are unchanged", () => {
      const { comp, changeRef } = createPreviewComponent();
      // Store current headerHeight then call checked — heights match → no detectChanges
      (comp as any).headerHeight = (comp as any).headerTable.nativeElement.offsetHeight;
      (comp as any).tableHeight = (comp as any).table.nativeElement.offsetHeight;
      comp.ngAfterViewChecked();
      expect(changeRef.detectChanges).not.toHaveBeenCalled();
   });
});

// ── Group 3 — onClick (@HostListener document:click) ─────────────────────────

describe("Group 3 — onClick: clears selection when click is outside host", () => {
   it("should clear selection when click target is outside the host element and not renaming", () => {
      const { comp, hostElement } = createPreviewComponent();
      hostElement.nativeElement.contains.mockReturnValue(false); // click is outside
      comp.selectCell(new MouseEvent("click"), 1, 0);
      expect(comp.isSelected(1, 0)).toBe(true);

      comp.onClick(new MouseEvent("click"));
      expect(comp.isSelected(1, 0)).toBe(false);
   });

   it("should NOT clear selection when renaming is true, even if click is outside", () => {
      const { comp, hostElement } = createPreviewComponent();
      hostElement.nativeElement.contains.mockReturnValue(false);
      comp.renaming = true;
      comp.selectCell(new MouseEvent("click"), 1, 0);

      comp.onClick(new MouseEvent("click"));
      expect(comp.isSelected(1, 0)).toBe(true); // selection preserved
   });

   it("should NOT clear selection when click is inside the host element", () => {
      const { comp, hostElement } = createPreviewComponent();
      hostElement.nativeElement.contains.mockReturnValue(true); // inside
      comp.selectCell(new MouseEvent("click"), 1, 0);

      comp.onClick(new MouseEvent("click"));
      expect(comp.isSelected(1, 0)).toBe(true);
   });
});

// ── Group 4 — horizontalScroll / resizeListener ───────────────────────────────

describe("Group 4 — horizontalScroll / resizeListener", () => {
   it("horizontalScroll should update scrollXPos from previewContainer.scrollLeft", () => {
      const { comp, previewContainerEl } = createPreviewComponent();
      previewContainerEl.scrollLeft = 120;
      comp.horizontalScroll();
      expect(comp.scrollXPos).toBe(120);
   });

   it("resizeListener should invoke horizontalScroll (updates horizontalDist)", () => {
      const { comp, previewContainerEl } = createPreviewComponent();
      previewContainerEl.scrollLeft = 80;
      previewContainerEl.clientWidth = 400;
      comp.resizeListener();
      // horizontalDist = clientWidth + scrollLeft
      expect((comp as any).horizontalDist).toBe(480);
   });
});

// ── Group 5 — resizeEnd: HTTP PUT ─────────────────────────────────────────────

describe("Group 5 — resizeEnd: calls modelService.putModel for column width persistence", () => {
   it("should call putModel with the CHART_DETAIL URI when isDetails=true", () => {
      const { comp, modelService } = createPreviewComponent();
      comp.isDetails = true;

      comp.startResize({ preventDefault: vi.fn(), pageX: 200 } as any, 1);
      (comp as any).resizeEnd({ pageX: 250 } as MouseEvent);

      expect(modelService.putModel).toHaveBeenCalledWith(
         expect.stringContaining("showdetails"),
         expect.any(String),
         expect.anything(),
      );
   });

   it("should call putModel with the CHART_DATA URI when isDetails=false", () => {
      const { comp, modelService } = createPreviewComponent();
      comp.isDetails = false;

      comp.startResize({ preventDefault: vi.fn(), pageX: 200 } as any, 1);
      (comp as any).resizeEnd({ pageX: 250 } as MouseEvent);

      expect(modelService.putModel).toHaveBeenCalledWith(
         expect.stringContaining("showdata"),
         expect.any(String),
         expect.anything(),
      );
   });

   it("should clear the resize listeners after resizeEnd completes", () => {
      const { comp, renderer } = createPreviewComponent();
      const removeFn = vi.fn();
      renderer.listen.mockReturnValue(removeFn);

      comp.startResize({ preventDefault: vi.fn(), pageX: 200 } as any, 0);
      (comp as any).resizeEnd({ pageX: 220 } as MouseEvent);

      expect(removeFn).toHaveBeenCalled();
   });
});

// ── Group 6 — formatClicked: formatGetter subscribe ──────────────────────────

describe("Group 6 — formatClicked: subscribes to formatGetter", () => {
   it("should set formatModel from the Observable returned by formatGetter", () => {
      const { comp } = createPreviewComponent();
      const mockFormat = { format: "date" } as any;
      comp.formatGetter = vi.fn().mockReturnValue(of(mockFormat));

      comp.formatClicked(2, true);

      expect(comp.formatModel).toEqual(mockFormat);
      expect(comp.col).toBe(2);
   });

   it("should emit onFormatChange with the stored formatModel when open=false", () => {
      const { comp } = createPreviewComponent();
      const mockFormat = { format: "number" } as any;
      comp.formatGetter = vi.fn().mockReturnValue(of(mockFormat));
      comp.formatClicked(1, true);  // loads formatModel

      const emitted: any[] = [];
      comp.onFormatChange.subscribe(e => emitted.push(e));
      comp.formatClicked(1, false); // commits

      expect(emitted).toHaveLength(1);
      expect(emitted[0].column).toEqual([1]);
      expect(emitted[0].format).toEqual(mockFormat);
   });
});

// ── Group 7 — sortClicked (basic happy path) ──────────────────────────────────

describe("Group 7 — sortClicked: emits onSort", () => {
   it("should emit onSort after updating the sort state", () => {
      const { comp } = createPreviewComponent();
      const emitted: SortInfo[] = [];
      comp.onSort.subscribe(s => emitted.push(s));

      comp.sortClicked(new MouseEvent("click"), 0);

      expect(emitted).toHaveLength(1);
      expect(emitted[0].col).toBe(0);
   });
});

// ── Group 8 — touchVScroll / touchHScroll ────────────────────────────────────

describe("Group 8 — touch scroll", () => {
   it("touchVScroll should update scrollY when component is vertically scrollable", () => {
      const { comp } = createPreviewComponent();
      makeScrollable(comp);
      comp.scrollY = 100;
      comp.tableHeight = 500;
      (comp as any).tableContainer.nativeElement.clientHeight = 200;

      comp.touchVScroll(20);

      expect(comp.scrollY).toBe(80); // max(0, 100-20)=80; min(80, 500-200)=80
   });

   it("touchVScroll should do nothing when component is NOT vertically scrollable", () => {
      const { comp } = createPreviewComponent();  // default: not scrollable
      comp.scrollY = 100;
      comp.touchVScroll(20);
      expect(comp.scrollY).toBe(100); // unchanged
   });

   it("touchHScroll should call renderer.setProperty to shift scrollLeft", () => {
      const { comp, renderer, previewContainerEl } = createPreviewComponent();
      previewContainerEl.scrollLeft = 100;
      comp.touchHScroll(30);
      expect(renderer.setProperty).toHaveBeenCalledWith(
         (comp as any).previewContainer.nativeElement,
         "scrollLeft",
         70,  // max(0, 100-30)
      );
   });
});

// ── Group 9 — verticalScrollHandler / wheelScrollHandler ──────────────────────

describe("Group 9 — native scroll handlers", () => {
   it("verticalScrollHandler should set scrollY from event.target.scrollTop", () => {
      const { comp } = createPreviewComponent();
      comp.verticalScrollHandler({ target: { scrollTop: 56 } });
      expect(comp.scrollY).toBe(56);
   });

   it("wheelScrollHandler should increment verticalScrollWrapper.scrollTop by deltaY", () => {
      const { comp } = createPreviewComponent();
      const vswEl = (comp as any).verticalScrollWrapper.nativeElement;
      vswEl.scrollTop = 40;
      const event = { deltaY: 100, preventDefault: vi.fn() };
      comp.wheelScrollHandler(event);
      expect(vswEl.scrollTop).toBe(140);
      expect(event.preventDefault).toHaveBeenCalled();
   });
});

// ── Group 10 — clickLink ──────────────────────────────────────────────────────

describe("Group 10 — clickLink: invokes hyperlinkService to open hyperlink dropdown", () => {
   it("should call createHyperlinkActions with the cell hyperlinks and linkUri", () => {
      const { comp, hyperlinkService } = createPreviewComponent();
      const cell = makeCell({ hyperlinks: [{ linkType: 1 } as any] });
      comp.clickLink(cell, { pageX: 10, pageY: 20 });
      expect(hyperlinkService.createHyperlinkActions).toHaveBeenCalledWith(
         cell.hyperlinks,
         comp.linkUri,
         "vs1",
      );
   });

   it("should call createActionsContextmenu after building the actions", () => {
      const { comp, hyperlinkService } = createPreviewComponent();
      const cell = makeCell({ hyperlinks: [{ linkType: 1 } as any] });
      comp.clickLink(cell, { pageX: 10, pageY: 20 });
      expect(hyperlinkService.createActionsContextmenu).toHaveBeenCalled();
   });
});

// ── Group 11 — apply ──────────────────────────────────────────────────────────

describe("Group 11 — apply: emits onFormatChange and closes dropdowns", () => {
   it("should emit onFormatChange when apply(true) is called", () => {
      const { comp } = createPreviewComponent();
      comp.formatModel = { format: "percent" } as any;
      comp.sortInfo = { col: 0, sortValue: 1 } as SortInfo;
      comp.worksheetId = "ws1";
      (comp as any).col = 0;

      const emitted: any[] = [];
      comp.onFormatChange.subscribe(e => emitted.push(e));
      comp.apply(true);

      expect(emitted).toHaveLength(1);
      expect(emitted[0].format).toEqual(comp.formatModel);
      expect(emitted[0].column).toEqual([0]);
   });

   it("should call forEach on the dropdowns QueryList when apply(true) is called", () => {
      const { comp } = createPreviewComponent();
      comp.apply(true);
      expect((comp as any).dropdowns.forEach).toHaveBeenCalled();
   });

   it("should NOT emit onFormatChange when apply(false) is called", () => {
      const { comp } = createPreviewComponent();
      const emitted: any[] = [];
      comp.onFormatChange.subscribe(e => emitted.push(e));
      comp.apply(false);
      expect(emitted).toHaveLength(0);
   });
});

// ── Group 12 — DnD lifecycle ──────────────────────────────────────────────────

describe("Group 12 — DnD: dragStart/dragOverTable/onLeave/dropOnTable", () => {
   const makeDragEvent = (overrides?: any) => ({
      dataTransfer: { effectAllowed: "", setData: vi.fn() },
      preventDefault: vi.fn(),
      stopPropagation: vi.fn(),
      ...overrides,
   } as any as DragEvent);

   it("dragStart should set dragIndex to the dragged column", () => {
      const { comp } = createPreviewComponent();
      comp.dragStart(makeDragEvent(), 2);
      expect(comp.dragIndex).toBe(2);
   });

   it("dragOverTable should set dropIndex to idx+1 when pointer is in the right half of cell", () => {
      const { comp } = createPreviewComponent();
      comp.dragStart(makeDragEvent(), 0);

      // columnWidths[1] capped at 150 by Math.min; left=0 → posx=200 > 150/2=75 → right half → dropIndex=2
      const overEvent = makeDragEvent({
         clientX: 200,
         currentTarget: { getBoundingClientRect: vi.fn().mockReturnValue({ left: 0 }) },
      });
      comp.dragOverTable(overEvent, 1);
      expect(comp.dropIndex).toBe(2);
   });

   it("dragOverTable should set dropIndex to idx when pointer is in the left half of cell", () => {
      const { comp } = createPreviewComponent();
      comp.dragStart(makeDragEvent(), 2);

      // posx=20 < 150/2=75 → left half → dropIndex=1
      const overEvent = makeDragEvent({
         clientX: 20,
         currentTarget: { getBoundingClientRect: vi.fn().mockReturnValue({ left: 0 }) },
      });
      comp.dragOverTable(overEvent, 1);
      expect(comp.dropIndex).toBe(1);
   });

   it("onLeave should reset dropIndex to -1 when mouse leaves the table bounds", () => {
      const { comp } = createPreviewComponent();
      comp.dropIndex = 2;
      // table bounds: left=0, right=500, top=0, bottom=200 (from mock)
      comp.onLeave({ clientX: 600, clientY: 100 } as MouseEvent); // X > right-2=498
      expect(comp.dropIndex).toBe(-1);
   });

   it("dropOnTable should emit onOrder with dragIndexes and dropIndex", () => {
      const { comp } = createPreviewComponent();
      comp.dragStart(makeDragEvent(), 0);
      comp.dropIndex = 2;

      const emitted: DetailDndInfo[] = [];
      comp.onOrder.subscribe(d => emitted.push(d));
      comp.dropOnTable(makeDragEvent());

      expect(emitted).toHaveLength(1);
      expect(emitted[0].dragIndexes).toEqual([0]);
      expect(emitted[0].dropIndex).toBe(2);
   });

   it("dropOnTable should reset indices and not emit when drop is not acceptable", () => {
      const { comp } = createPreviewComponent();
      comp.dragIndex = 1;
      comp.dropIndex = 1; // same as dragIndex → not acceptable

      const emitted: any[] = [];
      comp.onOrder.subscribe(d => emitted.push(d));
      comp.dropOnTable(makeDragEvent());

      expect(emitted).toHaveLength(0);
      expect(comp.dragIndex).toBe(-1);
      expect(comp.dropIndex).toBe(-1);
   });
});

// ── Group 13 — changeCellText ─────────────────────────────────────────────────

describe("Group 13 — changeCellText: column rename on Enter", () => {
   it("should emit onRename with the new label when Enter is pressed after editing a header", () => {
      const { comp } = createPreviewComponent();
      // Select row 0 twice to enter rename mode
      comp.selectCell(new MouseEvent("click"), 0, 0);
      comp.selectCell(new MouseEvent("click"), 0, 0); // second click → renaming = true

      // Mutate the cell label to simulate editing
      comp.tableData[0][0].cellLabel = "Renamed";
      // _oheaders[0] was set to "H0" when tableData was first set → "H0" ≠ "Renamed"

      const emitted: any[] = [];
      comp.onRename.subscribe(e => emitted.push(e));
      comp.changeCellText({ keyCode: 13 } as KeyboardEvent);

      expect(emitted).toHaveLength(1);
      expect(emitted[0].column).toBe(0);
      expect(emitted[0].newName).toBe("Renamed");
   });

   it("should NOT emit onRename when the label is unchanged from the original header", () => {
      const { comp } = createPreviewComponent();
      comp.selectCell(new MouseEvent("click"), 0, 0);
      comp.selectCell(new MouseEvent("click"), 0, 0);
      // tableData[0][0].cellLabel = "H0" (unchanged from _oheaders[0])

      const emitted: any[] = [];
      comp.onRename.subscribe(e => emitted.push(e));
      comp.changeCellText({ keyCode: 13 } as KeyboardEvent);

      expect(emitted).toHaveLength(0);
   });

   it("should do nothing when the key is not Enter (keyCode !== 13)", () => {
      const { comp } = createPreviewComponent();
      comp.selectCell(new MouseEvent("click"), 0, 0);

      const emitted: any[] = [];
      comp.onRename.subscribe(e => emitted.push(e));
      comp.changeCellText({ keyCode: 27 } as KeyboardEvent); // Escape

      expect(emitted).toHaveLength(0);
   });
});

// ── Group 14 — openVisibilityContextMenu ──────────────────────────────────────

describe("Group 14 — openVisibilityContextMenu: dropdown with hide/show-columns actions", () => {
   it("should open the dropdown service when called", () => {
      const { comp, dropdownService } = createPreviewComponent();
      comp.openVisibilityContextMenu(
         { clientX: 100, clientY: 200 } as MouseEvent, 0, 0,
      );
      expect(dropdownService.open).toHaveBeenCalled();
   });

   it("should select the cell before opening the menu if it is not already selected", () => {
      const { comp, dropdownService } = createPreviewComponent();
      comp.openVisibilityContextMenu(
         { clientX: 50, clientY: 80 } as MouseEvent, 1, 0,
      );
      // After selectCell(null, 1, 0) is called internally, (1, 0) is selected
      expect(comp.isSelected(1, 0)).toBe(true);
      expect(dropdownService.open).toHaveBeenCalled();
   });

   it("hide-column action visible() should be true when hideEnabled and columnSelection has items", () => {
      const { comp, dropdownService } = createPreviewComponent();
      comp.hideEnabled = true;
      comp.selectCell(new MouseEvent("click"), 1, 0); // pre-select so columnSelection > 0

      let capturedActions: any;
      dropdownService.open.mockImplementation((_comp: any, _opts: any) => {
         return { componentInstance: { get actions() { return capturedActions; }, set actions(v: any) { capturedActions = v; } }, closed: true };
      });

      comp.openVisibilityContextMenu({ clientX: 10, clientY: 10 } as MouseEvent, 1, 0);
      const hideAction = capturedActions[0].actions[0];
      expect(hideAction.visible()).toBe(true);
   });
});
