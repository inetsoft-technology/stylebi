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
 * SelectionListCell — Pass 1: Interaction
 *
 * Direct instantiation (no ATL render) — all 5 constructor deps mocked.
 * Covers the interaction methods not exercised in the existing spec.ts:
 * selectedCells setter, clickLabel, onDragStart, setMenuCell,
 * onResizeCellHeight* lifecycle, and onResizeMeasures* lifecycle.
 *
 * See selection-list-cell.component.spec.ts for quick-switch, long-press,
 * and mouse-enter/leave coverage which remain in that file.
 *
 *   Group 1 — selectedCells setter: region flags set from cell map; reset when absent; keyNav focus
 *   Group 2 — clickLabel: delegates to click() in viewer/preview; no-op in composer
 *   Group 3 — onDragStart: early-return when embedded; sets objectType; stops propagation
 *   Group 4 — setMenuCell: assigns contextMenuCell; delegates to selectRegion(LABEL)
 *   Group 5 — onResizeCellHeightStart: dialog when wordWrap=break-word; resizeshow otherwise
 *   Group 6 — onResizeCellHeight: accumulates height; skips when wordWrap=break-word
 *   Group 7 — onResizeCellHeightEnd: updates model.cellHeight; calls updateCellHeight; skips when break-word
 *   Group 8 — onResizeMeasuresMove: adjusts _barWidth+barX for bar region; _textWidth for text region
 *   Group 9 — onResizeMeasuresEnd: clamps _barWidth and _textWidth; emits resizeMeasures
 */

import { ComponentTool } from "../../../common/util/component-tool";
import { Tool } from "../../../../../../shared/util/tool";
import { CellRegion } from "./cell-region";
import {
   createComp,
   makeListModel,
   makeSelectionValue,
   makeViewerContext,
} from "./selection-list-cell.component.test-helpers";

// ─────────────────────────────────────────────────────────────────────────────

beforeAll(() => {
   vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({
      font: "",
      measureText: () => ({ width: 0 }),
   } as any);
});

afterEach(() => vi.restoreAllMocks());

describe("SelectionListCell — Pass 1: Interaction", () => {

   // ── Group 1 — selectedCells setter ───────────────────────────────────────
   // Private region-flag state accessed directly: labelSelected, measureTextSelected,
   // measureBarSelected, measureNBarSelected have no public accessors and are driven
   // exclusively by the selectedCells setter. Direct field reads are the only way to
   // verify the branch logic without triggering a full change-detection cycle.
   describe("Group 1 — selectedCells setter", () => {
      it("should set labelSelected to true when identifier is present with LABEL=true", () => {
         const { comp, vsSelection } = createComp();
         vsSelection.getIdentifier.mockReturnValue("cell-A");

         comp.selectedCells = new Map([["cell-A", new Map([[CellRegion.LABEL, true]])]]);

         expect((comp as any).labelSelected).toBe(true);
      });

      it("should set all four region flags from the cell map", () => {
         const { comp, vsSelection } = createComp();
         vsSelection.getIdentifier.mockReturnValue("cell-A");
         const regionMap = new Map([
            [CellRegion.LABEL, true],
            [CellRegion.MEASURE_TEXT, true],
            [CellRegion.MEASURE_BAR, false],
            [CellRegion.MEASURE_N_BAR, true],
         ]);

         comp.selectedCells = new Map([["cell-A", regionMap]]);

         expect((comp as any).labelSelected).toBe(true);
         expect((comp as any).measureTextSelected).toBe(true);
         expect((comp as any).measureBarSelected).toBe(false);
         expect((comp as any).measureNBarSelected).toBe(true);
      });

      it("should reset all region flags to false when identifier is absent", () => {
         const { comp, vsSelection } = createComp();
         vsSelection.getIdentifier.mockReturnValue("cell-A");
         // pre-set flags to true
         (comp as any).labelSelected = true;
         (comp as any).measureTextSelected = true;

         comp.selectedCells = new Map([["cell-B", new Map([[CellRegion.LABEL, true]])]]);

         expect((comp as any).labelSelected).toBe(false);
         expect((comp as any).measureTextSelected).toBe(false);
      });

      it("should reset all region flags to false when cells map is null", () => {
         const { comp } = createComp();
         (comp as any).labelSelected = true;

         comp.selectedCells = null;

         expect((comp as any).labelSelected).toBe(false);
      });

      it("should call cell.nativeElement.focus() when keyNav=true and cell is set", () => {
         const { comp, vsSelection } = createComp();
         vsSelection.getIdentifier.mockReturnValue("cell-A");
         comp.keyNav = true;
         const focusSpy = vi.spyOn((comp as any).cell.nativeElement, "focus");

         comp.selectedCells = new Map([["cell-A", new Map([[CellRegion.LABEL, true]])]]);

         expect(focusSpy).toHaveBeenCalled();
      });

      it("should not call focus() when keyNav=false", () => {
         const { comp, vsSelection } = createComp();
         vsSelection.getIdentifier.mockReturnValue("cell-A");
         comp.keyNav = false;
         const focusSpy = vi.spyOn((comp as any).cell.nativeElement, "focus");

         comp.selectedCells = new Map([["cell-A", new Map([[CellRegion.LABEL, true]])]]);

         expect(focusSpy).not.toHaveBeenCalled();
      });
   });

   // ── Group 2 — clickLabel ──────────────────────────────────────────────────
   describe("Group 2 — clickLabel", () => {
      it("should call click() in viewer context", () => {
         const { comp } = createComp({ contextProvider: makeViewerContext() });
         const clickSpy = vi.spyOn(comp, "click");

         comp.clickLabel(new MouseEvent("click"));

         expect(clickSpy).toHaveBeenCalled();
      });

      it("should not call click() in composer context (non-viewer, non-preview)", () => {
         const { comp } = createComp(); // default: viewer=false, preview=false
         const clickSpy = vi.spyOn(comp, "click");

         comp.clickLabel(new MouseEvent("click"));

         expect(clickSpy).not.toHaveBeenCalled();
      });
   });

   // ── Group 3 — onDragStart ─────────────────────────────────────────────────
   describe("Group 3 — onDragStart", () => {
      it("should return early when isEmbedded is true", () => {
         const setTransferSpy = vi.spyOn(Tool, "setTransferData").mockImplementation(() => {});
         const { comp } = createComp();
         comp.isEmbedded = true;

         comp.onDragStart({ dataTransfer: {}, stopPropagation: vi.fn() } as any);

         expect(setTransferSpy).not.toHaveBeenCalled();
      });

      it("should call Tool.setTransferData with objectType 'vsselection' when not embedded", () => {
         const setTransferSpy = vi.spyOn(Tool, "setTransferData").mockImplementation(() => {});
         const { comp } = createComp();
         comp.isEmbedded = false;

         comp.onDragStart({ dataTransfer: {}, stopPropagation: vi.fn() } as any);

         expect(setTransferSpy).toHaveBeenCalledWith(
            expect.anything(),
            expect.objectContaining({
               dragSource: expect.objectContaining({ objectType: "vsselection" }),
            }),
         );
      });

      it("should stop propagation when not embedded", () => {
         vi.spyOn(Tool, "setTransferData").mockImplementation(() => {});
         const { comp } = createComp();
         comp.isEmbedded = false;
         const stopSpy = vi.fn();

         comp.onDragStart({ dataTransfer: {}, stopPropagation: stopSpy } as any);

         expect(stopSpy).toHaveBeenCalled();
      });
   });

   // ── Group 4 — setMenuCell ─────────────────────────────────────────────────
   describe("Group 4 — setMenuCell", () => {
      it("should assign selectionValue to model.contextMenuCell", () => {
         const { comp, vsSelection } = createComp();
         const sv = makeSelectionValue({ label: "USA East" });
         comp.selectionValue = sv;

         comp.setMenuCell(new MouseEvent("contextmenu"));

         expect(vsSelection.model.contextMenuCell).toBe(sv);
      });

      it("should delegate to selectRegion with CellRegion.LABEL", () => {
         const { comp } = createComp();
         const regionSpy = vi.spyOn(comp, "selectRegion");
         const event = new MouseEvent("contextmenu");

         comp.setMenuCell(event);

         expect(regionSpy).toHaveBeenCalledWith(event, CellRegion.LABEL);
      });
   });

   // ── Group 5 — onResizeCellHeightStart ────────────────────────────────────
   describe("Group 5 — onResizeCellHeightStart", () => {
      it("should show error dialog and not set resizeshow when wordWrap=break-word", () => {
         const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("");
         const model = makeListModel();
         const { comp } = createComp({ model });
         // override cellFormat wordWrap after init
         (comp as any).cellFormat = {
            wrapping: { wordWrap: "break-word" },
         };

         comp.onResizeCellHeightStart();

         expect(dialogSpy).toHaveBeenCalled();
         expect((comp as any).resizeshow).toBe(false);
      });

      it("should set resizeshow=true and capture cellHeight when wordWrap is not break-word", () => {
         const { comp, vsSelection } = createComp();
         vsSelection.cellHeight = 24;
         (comp as any).cellFormat = { wrapping: { wordWrap: null } };

         comp.onResizeCellHeightStart();

         expect((comp as any).resizeshow).toBe(true);
         expect((comp as any).resizeBorderHeight).toBe(24);
      });
   });

   // ── Group 6 — onResizeCellHeight ─────────────────────────────────────────
   describe("Group 6 — onResizeCellHeight", () => {
      it("should accumulate deltaRect.bottom into resizeBorderHeight", () => {
         const { comp } = createComp();
         (comp as any).cellFormat = { wrapping: { wordWrap: null } };
         (comp as any).resizeBorderHeight = 20;

         comp.onResizeCellHeight({ deltaRect: { bottom: 5 } });

         expect((comp as any).resizeBorderHeight).toBe(25);
      });

      it("should skip accumulation when wordWrap=break-word", () => {
         const { comp } = createComp();
         (comp as any).cellFormat = { wrapping: { wordWrap: "break-word" } };
         (comp as any).resizeBorderHeight = 20;

         comp.onResizeCellHeight({ deltaRect: { bottom: 5 } });

         expect((comp as any).resizeBorderHeight).toBe(20);
      });
   });

   // ── Group 7 — onResizeCellHeightEnd ──────────────────────────────────────
   describe("Group 7 — onResizeCellHeightEnd", () => {
      it("should update model.cellHeight with resizeBorderHeight", () => {
         const { comp, vsSelection } = createComp();
         (comp as any).cellFormat = { wrapping: { wordWrap: null, whiteSpace: "nowrap" } };
         (comp as any).resizeBorderHeight = 30;

         comp.onResizeCellHeightEnd();

         expect(vsSelection.model.cellHeight).toBe(30);
      });

      it("should call vsSelectionComponent.updateCellHeight()", () => {
         const { comp, vsSelection } = createComp();
         (comp as any).cellFormat = { wrapping: { wordWrap: null, whiteSpace: "nowrap" } };

         comp.onResizeCellHeightEnd();

         expect(vsSelection.updateCellHeight).toHaveBeenCalled();
      });

      it("should skip update when wordWrap=break-word", () => {
         const { comp, vsSelection } = createComp();
         (comp as any).cellFormat = { wrapping: { wordWrap: "break-word" } };
         vsSelection.model.cellHeight = 18;
         (comp as any).resizeBorderHeight = 30;

         comp.onResizeCellHeightEnd();

         expect(vsSelection.model.cellHeight).toBe(18);
         expect(vsSelection.updateCellHeight).not.toHaveBeenCalled();
      });
   });

   // ── Group 8 — onResizeMeasuresMove ───────────────────────────────────────
   describe("Group 8 — onResizeMeasuresMove", () => {
      it("should set barResizing=true and decrease _barWidth and barX by deltaRect.left for MEASURE_BAR", () => {
         const { comp } = createComp();
         (comp as any)._barWidth = 100;
         (comp as any).barX = 50;

         comp.onResizeMeasuresMove({ deltaRect: { left: 10 } }, CellRegion.MEASURE_BAR);

         expect((comp as any).barResizing).toBe(true);
         expect((comp as any)._barWidth).toBe(90);
         expect((comp as any).barX).toBe(40);
      });

      it("should decrease _textWidth by deltaRect.left for MEASURE_TEXT", () => {
         const { comp } = createComp();
         (comp as any)._textWidth = 80;

         comp.onResizeMeasuresMove({ deltaRect: { left: 15 } }, CellRegion.MEASURE_TEXT);

         expect((comp as any)._textWidth).toBe(65);
         expect((comp as any).barResizing).toBe(false);
      });
   });

   // ── Group 9 — onResizeMeasuresEnd ────────────────────────────────────────
   describe("Group 9 — onResizeMeasuresEnd", () => {
      // cellWidth=200, labelLeft=18 → maxBarWidth=182

      it("should clamp _barWidth to Math.ceil(cellWidth * 0.05) when it goes negative", () => {
         const { comp } = createComp();
         (comp as any)._barWidth = -5;
         (comp as any)._textWidth = 80;

         comp.onResizeMeasuresEnd();

         // Math.ceil(200 * 0.05) = 10
         expect((comp as any)._barWidth).toBe(10);
      });

      it("should clamp _barWidth to maxBarWidth when it exceeds cellWidth - labelLeft", () => {
         const { comp } = createComp();
         (comp as any)._barWidth = 300;
         (comp as any)._textWidth = 20;

         comp.onResizeMeasuresEnd();

         // maxBarWidth = 200 - 18 = 182
         expect((comp as any)._barWidth).toBe(182);
      });

      it("should clamp _textWidth to minWidth when it falls below the minimum", () => {
         const { comp } = createComp();
         comp.minWidth = 20;
         (comp as any)._barWidth = 50;
         (comp as any)._textWidth = 5;

         comp.onResizeMeasuresEnd();

         expect((comp as any)._textWidth).toBe(20);
      });

      it("should emit resizeMeasures with final _textWidth and _barWidth", () => {
         const { comp } = createComp();
         (comp as any)._barWidth = 60;
         (comp as any)._textWidth = 40;
         const emitted: any[] = [];
         comp.resizeMeasures.subscribe(v => emitted.push(v));

         comp.onResizeMeasuresEnd();

         expect(emitted.length).toBe(1);
         expect(emitted[0]).toEqual({ text: 40, bar: 60 });
      });
   });
});
