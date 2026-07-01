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
 * SelectionListCell — Pass 3: Display
 *
 * Direct instantiation (no ATL render). All pure conditional display logic:
 * icon class computation, ARIA states, geometry branches for bar/text widths,
 * and computed getter properties not covered by the existing spec.
 *
 *   Group 1 — getIconClass: others/more/STATE_SELECTED/INCLUDED/EXCLUDED/default branches (single and multi)
 *   Group 2 — ariaSelected: true for selected-mask states; false otherwise
 *   Group 3 — ariaExpanded: null for list/non-folder; true/false for tree folders
 *   Group 4 — height getter: "auto" for break-word; "{cellHeight}px" otherwise
 *   Group 5 — misc getters: getTreeIconClass, getCellTooltip, isFolder, isList, toggleEnabled, vsWizard, isHTML
 *   Group 6 — barY switch: sanitization called with "2px" / calc-bottom / calc-center per vAlign
 *   Group 7 — setMeasureWidths geometry: all-positive / mixed-positive / mixed-negative / all-negative / NaN guard
 *   Group 8 — setTextWidths: _textWidth derived from measureRatio; clamped to cellWidth - _barWidth
 */

import { SelectionValue } from "../../../composer/data/vs/selection-value";
import { ContextProvider } from "../../context-provider.service";
import {
   createComp,
   makeListModel,
   makeMeasureFormats,
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

describe("SelectionListCell — Pass 3: Display", () => {

   // ── Group 1 — getIconClass ────────────────────────────────────────────────
   describe("Group 1 — getIconClass", () => {
      it("should return 'select-other-circle-icon' when others=true and singleSelection=true", () => {
         const { comp } = createComp({ selectionValue: makeSelectionValue({ others: true }) });
         comp.singleSelection = true;

         expect(comp.getIconClass()).toBe("select-other-circle-icon");
      });

      it("should return 'select-other-icon icon-size1' when others=true and singleSelection=false", () => {
         const { comp } = createComp({ selectionValue: makeSelectionValue({ others: true }) });
         comp.singleSelection = false;

         expect(comp.getIconClass()).toBe("select-other-icon icon-size1");
      });

      it("should return 'chevron-circle-arrow-right-icon icon-size1' when more=true", () => {
         const { comp } = createComp({ selectionValue: makeSelectionValue({ more: true }) });

         expect(comp.getIconClass()).toBe("chevron-circle-arrow-right-icon icon-size1");
      });

      it("should return 'selected-icon icon-size1' for STATE_SELECTED in multi mode", () => {
         const { comp } = createComp({
            selectionValue: makeSelectionValue({ state: SelectionValue.STATE_SELECTED }),
         });
         comp.singleSelection = false;

         expect(comp.getIconClass()).toBe("selected-icon icon-size1");
      });

      it("should return 'selected-auto-circle-icon icon-size1' for STATE_SELECTED in single mode", () => {
         const { comp } = createComp({
            selectionValue: makeSelectionValue({ state: SelectionValue.STATE_SELECTED }),
         });
         comp.singleSelection = true;

         expect(comp.getIconClass()).toBe("selected-auto-circle-icon icon-size1");
      });

      it("should return 'selected-icon icon-size1' for STATE_SELECTED|INCLUDED in multi mode", () => {
         const { comp } = createComp({
            selectionValue: makeSelectionValue({ state: SelectionValue.STATE_SELECTED | SelectionValue.STATE_INCLUDED }),
         });
         comp.singleSelection = false;

         expect(comp.getIconClass()).toBe("selected-icon icon-size1");
      });

      it("should return 'selected-icon icon-size1 icon-disabled' for STATE_SELECTED|EXCLUDED in multi mode", () => {
         const { comp } = createComp({
            selectionValue: makeSelectionValue({ state: SelectionValue.STATE_SELECTED | SelectionValue.STATE_EXCLUDED }),
         });
         comp.singleSelection = false;

         expect(comp.getIconClass()).toBe("selected-icon icon-size1 icon-disabled");
      });

      it("should return 'selected-auto-circle-icon icon-size1 icon-disabled' for STATE_SELECTED|EXCLUDED in single mode", () => {
         const { comp } = createComp({
            selectionValue: makeSelectionValue({ state: SelectionValue.STATE_SELECTED | SelectionValue.STATE_EXCLUDED }),
         });
         comp.singleSelection = true;

         expect(comp.getIconClass()).toBe("selected-auto-circle-icon icon-size1 icon-disabled");
      });

      it("should return 'selected-auto-icon icon-size1' for STATE_INCLUDED in multi mode", () => {
         const { comp } = createComp({
            selectionValue: makeSelectionValue({ state: SelectionValue.STATE_INCLUDED }),
         });
         comp.singleSelection = false;

         expect(comp.getIconClass()).toBe("selected-auto-icon icon-size1");
      });

      it("should return 'select-empty-circle-icon icon-size1' for STATE_INCLUDED in single mode", () => {
         const { comp } = createComp({
            selectionValue: makeSelectionValue({ state: SelectionValue.STATE_INCLUDED }),
         });
         comp.singleSelection = true;

         expect(comp.getIconClass()).toBe("select-empty-circle-icon icon-size1");
      });

      it("should return 'select-excluded-icon icon-size1' for STATE_EXCLUDED in multi mode", () => {
         const { comp } = createComp({
            selectionValue: makeSelectionValue({ state: SelectionValue.STATE_EXCLUDED }),
         });
         comp.singleSelection = false;

         expect(comp.getIconClass()).toBe("select-excluded-icon icon-size1");
      });

      it("should return 'select-excluded-circle-icon icon-size1' for STATE_EXCLUDED in single mode", () => {
         const { comp } = createComp({
            selectionValue: makeSelectionValue({ state: SelectionValue.STATE_EXCLUDED }),
         });
         comp.singleSelection = true;

         expect(comp.getIconClass()).toBe("select-excluded-circle-icon icon-size1");
      });

      it("should return 'select-empty-icon icon-size1' for unselected state in multi mode", () => {
         const { comp } = createComp({ selectionValue: makeSelectionValue({ state: 0 }) });
         comp.singleSelection = false;

         expect(comp.getIconClass()).toBe("select-empty-icon icon-size1");
      });

      it("should return null when selectionValue is null", () => {
         const { comp } = createComp({ withInit: false });
         comp.selectionValue = null;

         expect(comp.getIconClass()).toBeNull();
      });
   });

   // ── Group 2 — ariaSelected ────────────────────────────────────────────────
   describe("Group 2 — ariaSelected", () => {
      it("should return true for STATE_SELECTED", () => {
         const { comp } = createComp({
            selectionValue: makeSelectionValue({ state: SelectionValue.STATE_SELECTED }),
         });

         expect(comp.ariaSelected).toBe(true);
      });

      it("should return true for STATE_SELECTED | STATE_INCLUDED", () => {
         const { comp } = createComp({
            selectionValue: makeSelectionValue({ state: SelectionValue.STATE_SELECTED | SelectionValue.STATE_INCLUDED }),
         });

         expect(comp.ariaSelected).toBe(true);
      });

      it("should return true for STATE_SELECTED | STATE_EXCLUDED", () => {
         const { comp } = createComp({
            selectionValue: makeSelectionValue({ state: SelectionValue.STATE_SELECTED | SelectionValue.STATE_EXCLUDED }),
         });

         expect(comp.ariaSelected).toBe(true);
      });

      it("should return false for STATE_INCLUDED only", () => {
         const { comp } = createComp({
            selectionValue: makeSelectionValue({ state: SelectionValue.STATE_INCLUDED }),
         });

         expect(comp.ariaSelected).toBe(false);
      });

      it("should return false for STATE_EXCLUDED only", () => {
         const { comp } = createComp({
            selectionValue: makeSelectionValue({ state: SelectionValue.STATE_EXCLUDED }),
         });

         expect(comp.ariaSelected).toBe(false);
      });

      it("should return false for unselected state (0)", () => {
         const { comp } = createComp({ selectionValue: makeSelectionValue({ state: 0 }) });

         expect(comp.ariaSelected).toBe(false);
      });
   });

   // ── Group 3 — ariaExpanded ────────────────────────────────────────────────
   describe("Group 3 — ariaExpanded", () => {
      it("should return null for a list assembly (objectType=VSSelectionList)", () => {
         const model = makeListModel({ objectType: "VSSelectionList" });
         const { comp } = createComp({ model });

         expect(comp.ariaExpanded).toBeNull();
      });

      it("should return null for a tree node that is not a folder", () => {
         const model = makeListModel({ objectType: "VSSelectionTree" });
         const sv = makeSelectionValue(); // no selectionList property → not a folder
         const { comp } = createComp({ model, selectionValue: sv });

         expect(comp.ariaExpanded).toBeNull();
      });

      it("should return true for an open folder node in a tree", () => {
         const model = makeListModel({ objectType: "VSSelectionTree" });
         const sv = { ...makeSelectionValue(), selectionList: { selectionValues: [] } } as any;
         const { comp, vsSelection } = createComp({ model, selectionValue: sv });
         vsSelection.controller.isNodeOpen.mockReturnValue(true);

         expect(comp.ariaExpanded).toBe(true);
      });

      it("should return false for a closed folder node in a tree", () => {
         const model = makeListModel({ objectType: "VSSelectionTree" });
         const sv = { ...makeSelectionValue(), selectionList: { selectionValues: [] } } as any;
         const { comp, vsSelection } = createComp({ model, selectionValue: sv });
         vsSelection.controller.isNodeOpen.mockReturnValue(false);

         expect(comp.ariaExpanded).toBe(false);
      });
   });

   // ── Group 4 — height getter ───────────────────────────────────────────────
   describe("Group 4 — height getter", () => {
      it("should return 'auto' when cellFormat.wrapping.wordWrap is 'break-word'", () => {
         const { comp } = createComp();
         (comp as any).cellFormat = { wrapping: { wordWrap: "break-word" } };

         expect(comp.height).toBe("auto");
      });

      it("should return '{cellHeight}px' when wordWrap is not 'break-word'", () => {
         const { comp, vsSelection } = createComp();
         vsSelection.cellHeight = 24; // height getter reads vsSelectionComponent.cellHeight, not model.cellHeight
         (comp as any).cellFormat = { wrapping: { wordWrap: null } };

         expect(comp.height).toBe("24px");
      });
   });

   // ── Group 5 — misc getters ────────────────────────────────────────────────
   describe("Group 5 — misc getters", () => {
      it("getTreeIconClass should return minus-box icon when node is open", () => {
         const { comp, vsSelection } = createComp();
         vsSelection.controller.isNodeOpen.mockReturnValue(true);

         expect(comp.getTreeIconClass()).toBe("minus-box-outline-icon icon-size1");
      });

      it("getTreeIconClass should return plus-box icon when node is closed", () => {
         const { comp, vsSelection } = createComp();
         vsSelection.controller.isNodeOpen.mockReturnValue(false);

         expect(comp.getTreeIconClass()).toBe("plus-box-outline-icon icon-size1");
      });

      it("getCellTooltip should return toggle-style key in viewer context", () => {
         const { comp } = createComp({ contextProvider: makeViewerContext() });

         expect(comp.getCellTooltip()).toBe("_#(js:viewer.viewsheet.selection.toggleStyle)");
      });

      it("getCellTooltip should return empty string in composer context", () => {
         const { comp } = createComp(); // default: viewer=false

         expect(comp.getCellTooltip()).toBe("");
      });

      it("isFolder should return true when selectionValue has 'selectionList' property", () => {
         const sv = { ...makeSelectionValue(), selectionList: { selectionValues: [] } } as any;
         const { comp } = createComp({ selectionValue: sv });

         expect(comp.isFolder()).toBe(true);
      });

      it("isFolder should return false when selectionValue has no 'selectionList' property", () => {
         const { comp } = createComp({ selectionValue: makeSelectionValue() });

         expect(comp.isFolder()).toBe(false);
      });

      it("isList should return true when objectType is VSSelectionList", () => {
         const model = makeListModel({ objectType: "VSSelectionList" });
         const { comp } = createComp({ model });

         expect(comp.isList).toBe(true);
      });

      it("isList should return false when objectType is VSSelectionTree", () => {
         const model = makeListModel({ objectType: "VSSelectionTree" });
         const { comp } = createComp({ model });

         expect(comp.isList).toBe(false);
      });

      it("toggleEnabled should return true in viewer context", () => {
         const { comp } = createComp({ contextProvider: makeViewerContext() });

         expect(comp.toggleEnabled).toBe(true);
      });

      it("toggleEnabled should return false in composer context", () => {
         const { comp } = createComp();

         expect(comp.toggleEnabled).toBe(false);
      });

      it("isHTML should return true for a string containing an HTML element", () => {
         const { comp } = createComp();

         expect(comp.isHTML("<b>bold</b>")).toBe(true);
      });

      it("isHTML should return false for a plain-text string", () => {
         const { comp } = createComp();

         expect(comp.isHTML("plain text")).toBe(false);
      });

      it("isHTML should return false for an empty string", () => {
         const { comp } = createComp();

         expect(comp.isHTML("")).toBe(false);
      });

      it("vsWizard should return true when contextProvider.vsWizard is true", () => {
         // vsWizard is param[6] (0-indexed) in ContextProvider constructor
         const wizardCtx = new ContextProvider(false, false, false, false, false, false, true, false, false, false, false);
         const { comp } = createComp({ contextProvider: wizardCtx });

         expect(comp.vsWizard).toBe(true);
      });

      it("vsWizard should return false in default (composer) context", () => {
         const { comp } = createComp();

         expect(comp.vsWizard).toBe(false);
      });
   });

   // ── Group 6 — barY switch ─────────────────────────────────────────────────
   // updateModelInfo() barY switch tested via createComp with specific vAlign on
   // measureFormats: after ngOnInit(), comp.barY holds the sanitized string value.
   describe("Group 6 — barY switch (updateModelInfo vAlign)", () => {
      it("should set barY to '2px' when measureTextFormat.vAlign is 'top'", () => {
         const model = makeListModel({ measureFormats: makeMeasureFormats(0, "top") });
         const { comp } = createComp({ model });

         expect(comp.barY).toBe("2px");
      });

      it("should set barY to the bottom calc string when vAlign is 'bottom'", () => {
         const model = makeListModel({ measureFormats: makeMeasureFormats(0, "bottom") });
         const { comp } = createComp({ model });

         expect(comp.barY).toBe("calc(100% - 1em - 1px)");
      });

      it("should set barY to the centered calc string for any other vAlign (e.g. 'center')", () => {
         const model = makeListModel({ measureFormats: makeMeasureFormats(0, "center") });
         const { comp } = createComp({ model });

         expect(comp.barY).toBe("calc(50% - 1em / 2)");
      });
   });

   // ── Group 7 — setMeasureWidths geometry ───────────────────────────────────
   // Private method tested directly: three bar-geometry paths (all-positive, mixed,
   // all-negative) each produce distinct barX/barSize values that are visible in the
   // template. Verifying them in isolation avoids binding an entire model + DOM tree
   // just to observe two numeric fields.
   describe("Group 7 — setMeasureWidths geometry", () => {
      // All three cases share: barWidth input=60, cellWidth=200.
      // After init: _barWidth = min(200, 60) = 60; gap=2; barWidth_inner = 60-4 = 56.

      it("should set barX=gap and barSize=innerBarWidth for all-positive range", () => {
         // measureMin=0, measureMax=1, measureValue=0.5 → innerBarWidth=0.5*56=28
         const { comp } = createComp();
         comp.barWidth = 60;
         comp.measureMin = 0;
         comp.measureMax = 1;
         comp.measureRatio = 0.5;
         comp.selectionValue = makeSelectionValue({ measureValue: 0.5 });
         comp.ngOnInit();

         expect((comp as any).barX).toBe(2);   // gap=2
         expect((comp as any).barSize).toBe(28); // 0.5 * 56
      });

      it("should compute barX from zero point for positive value in mixed range", () => {
         // measureMin=-0.5, measureMax=1, measureValue=0.5
         // zero = 56 * 0.5 / (1 - (-0.5)) = 56 * 0.5 / 1.5 ≈ 18.67
         // innerBarWidth = 0.5 * 56 = 28
         // positive bar: barX=zero≈18.67+gap, barSize=28
         const { comp } = createComp();
         comp.barWidth = 60;
         comp.measureMin = -0.5;
         comp.measureMax = 1;
         comp.selectionValue = makeSelectionValue({ measureValue: 0.5 });
         comp.ngOnInit();

         const zero = 56 * 0.5 / 1.5;
         expect((comp as any).barX).toBeCloseTo(zero + 2, 5);
         expect((comp as any).barSize).toBeCloseTo(28, 5);
      });

      it("should compute barX from zero offset for negative value in mixed range", () => {
         // measureMin=-0.5, measureMax=1, measureValue=-0.25
         // negativew = 0.5 * 56 / 1.5 ≈ 18.67; innerBarWidth = -0.25 * 18.67 ≈ -4.67
         // negative bar: barSize=4.67, barX=zero+innerBarWidth+gap
         const { comp } = createComp();
         comp.barWidth = 60;
         comp.measureMin = -0.5;
         comp.measureMax = 1;
         comp.selectionValue = makeSelectionValue({ measureValue: -0.25 });
         comp.ngOnInit();

         const zero = 56 * 0.5 / 1.5;
         const negativew = 0.5 * 56 / 1.5;
         const innerBarWidth = -0.25 * negativew;
         expect((comp as any).barSize).toBeCloseTo(-innerBarWidth, 5);
         expect((comp as any).barX).toBeCloseTo(zero + innerBarWidth + 2, 5);
      });

      it("should set barX from right for all-negative range", () => {
         // measureMin=-1, measureMax=0, measureValue=-0.5
         // innerBarWidth = -0.5 * 56 = -28; all-negative: barX=56+(-28)+gap=30, barSize=28
         const { comp } = createComp();
         comp.barWidth = 60;
         comp.measureMin = -1;
         comp.measureMax = 0;
         comp.selectionValue = makeSelectionValue({ measureValue: -0.5 });
         comp.ngOnInit();

         expect((comp as any).barX).toBe(30); // 56 + (-28) + 2
         expect((comp as any).barSize).toBe(28);
      });

      it("should set barSize to 0 when measureValue is NaN", () => {
         const { comp } = createComp();
         comp.barWidth = 60;
         comp.measureMin = 0;
         comp.measureMax = 1;
         comp.selectionValue = makeSelectionValue({ measureValue: NaN });
         comp.ngOnInit();

         expect((comp as any).barSize).toBe(0);
      });
   });

   // ── Group 8 — setTextWidths ───────────────────────────────────────────────
   describe("Group 8 — setTextWidths", () => {
      // cellWidth=200, barWidth=0 (showBar=true → _barWidth=ceil(200/4)=50),
      // labelLeft=18, nodeIndent=4 → textArea=200-50-18-4=128; measureRatio=0.5
      // textWidth=-1 → _textWidth=ceil(128*0.5)=64

      it("should derive _textWidth from measureRatio when textWidth input is negative", () => {
         const { comp } = createComp();
         comp.barWidth = 0;
         comp.textWidth = -1;
         comp.measureRatio = 0.5;
         comp.measureMin = 0;
         comp.measureMax = 1;
         comp.ngOnInit();

         // _barWidth = ceil(200/4) = 50; textArea = 200-50-18-4 = 128; _textWidth = ceil(128*0.5) = 64
         expect((comp as any)._textWidth).toBe(64);
      });

      it("should use the explicit textWidth input when it is non-negative", () => {
         const { comp } = createComp();
         comp.barWidth = 0;
         comp.textWidth = 30;
         comp.measureRatio = 0.5;
         comp.measureMin = 0;
         comp.measureMax = 1;
         comp.ngOnInit();

         expect((comp as any)._textWidth).toBe(30);
      });

      it("should clamp _textWidth to cellWidth - _barWidth when _textWidth exceeds it", () => {
         const { comp } = createComp();
         comp.barWidth = 150;  // _barWidth = min(200, 150) = 150
         comp.textWidth = 200; // would exceed cellWidth - _barWidth = 50
         comp.measureRatio = 0.5;
         comp.ngOnInit();

         expect((comp as any)._textWidth).toBe(50);
      });
   });
});
