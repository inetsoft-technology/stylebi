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
 * VSSelection �?Pass 3: Display (dispatchPoints=3)
 *
 * Risk-first coverage:
 *   Group 1 �?updateSelectionState: toggle/toggleAll/singleSelection matrix;
 *                                   cellSelected+toggle keeps selected;
 *                                   dropdown auto-hide on singleSelection click
 *   Group 2 �?navigate: all FocusRegions (SEARCH_BAR/CLEAR_SEARCH/MENU/DROPDOWN/cell index);
 *                       UP/DOWN/LEFT/RIGHT/SPACE per region; scroll-into-view logic
 *   Group 3 �?set model: objectType dispatch (VSSelectionList vs VSSelectionTree);
 *                        expandAll/scriptApplied path; controller creation vs reuse
 *   Group 4 �?disPlayZIndex: viewer+dropdown+maxMode+pop combinations
 *   Group 5 �?topPosition: viewer non-dropdown, viewer dropdown atBottom,
 *                          viewer dropdown inBottomTab (collapsed/expanded/with-search),
 *                          composer dropdown inBottomTab (collapsed-search/no-search)
 *   Group 6 �?calcCellWidth: numCols clamping logic
 *   Group 7 �?getBodyHeight: container vs dropdown vs normal mode
 *   Group 8 �?getTitleWidth: border margin math
 *   Group 9 �?updateListSelectedString: state bitmask 1/9/10 filtering
 *   Group 10 �?getIdentifier: parentNode chain concatenation
 *   Group 11 �?selectAll: force/included/compatible filtering, composite recursion
 *   Group 12 �?trackByIdx: stable virtual-scroll tracking keys (pure-function, no rendering)
 *   Group 13 �?setQuickSwitchHover: overlay button positioning math (direct instantiation)
 */

import { Subject } from "rxjs";

import { NavigationKeys } from "../navigation-keys";
import {
   createMockController,
   makeEmptySelectionList,
   makeMockListModel,
   makeMockSelectionValues,
   makeMockTreeModel,
   makeSelectionValue,
   createSelectionComponent,
   injectController,
   setSelectionValuesForTest,
} from "./vs-selection.component.test-helpers";
import { SelectionValue } from "../../../composer/data/vs/selection-value";
import { VSSelection } from "./vs-selection.component";

afterEach(() => vi.restoreAllMocks());

async function renderComponent(overrides: any = {}) {
   return createSelectionComponent(overrides);
}

describe("VSSelection �?Pass 3: Display", () => {
   describe("Group 1 �?updateSelectionState", () => {
      it("should keep selected state when toggle is true and cell is not selected", async () => {
         const { comp } = await renderComponent();
         comp.model.singleSelection = true;
         const controller = {
            model: comp.model,
            unappliedSubject: new Subject<boolean>(),
            updateViewSubject: new Subject<void>(),
            selectionStateUpdated: vi.fn(),
         };
         injectController(comp, controller);

         const value = makeSelectionValue({ state: 8, level: 0, value: "item1" });
         comp.updateSelectionState(value, { toggle: true, toggleAll: false });

         expect(controller.selectionStateUpdated).toHaveBeenCalled();
      });

      it("should toggle singleSelection for VSSelectionList", async () => {
         const { comp } = await renderComponent();
         comp.model.singleSelection = false;
         const controller = {
            model: comp.model,
            unappliedSubject: new Subject<boolean>(),
            updateViewSubject: new Subject<void>(),
            selectionStateUpdated: vi.fn(),
         };
         injectController(comp, controller);

         const value = makeSelectionValue({ state: 8, level: 0, value: "item1" });
         comp.updateSelectionState(value, { toggle: true, toggleAll: false });

         expect(comp.model.singleSelection).toBe(true);
      });

      it("should add level to singleSelectionLevels for VSSelectionTree", async () => {
         const { comp } = await renderComponent();
         const treeModel = makeMockTreeModel();
         treeModel.singleSelectionLevels = [];
         comp.model = treeModel;
         const controller = {
            model: treeModel,
            unappliedSubject: new Subject<boolean>(),
            updateViewSubject: new Subject<void>(),
            selectionStateUpdated: vi.fn(),
         };
         injectController(comp, controller);

         const value = makeSelectionValue({ state: 8, level: 0, value: "item1" });
         comp.updateSelectionState(value, { toggle: true, toggleAll: false });

         expect(treeModel.singleSelectionLevels).toContain(0);
         expect(treeModel.singleSelection).toBe(true);
      });

      it("should remove level from singleSelectionLevels for VSSelectionTree", async () => {
         const { comp } = await renderComponent();
         const treeModel = makeMockTreeModel();
         treeModel.singleSelectionLevels = [0];
         comp.model = treeModel;
         const controller = {
            model: treeModel,
            unappliedSubject: new Subject<boolean>(),
            updateViewSubject: new Subject<void>(),
            selectionStateUpdated: vi.fn(),
         };
         injectController(comp, controller);

         const value = makeSelectionValue({ state: 8, level: 0, value: "item1" });
         comp.updateSelectionState(value, { toggle: true, toggleAll: false });

         expect(treeModel.singleSelectionLevels).not.toContain(0);
         expect(treeModel.singleSelection).toBe(false);
      });

      it("should call hideSelf when dropdown is singleSelection and cell is clicked", async () => {
         const { comp } = await renderComponent();
         comp.model.dropdown = true;
         comp.model.singleSelection = true;
         comp.model.maxMode = false;
         const controller = {
            model: comp.model,
            unappliedSubject: new Subject<boolean>(),
            updateViewSubject: new Subject<void>(),
            hideSelf: vi.fn(),
            selectionStateUpdated: vi.fn(),
         };
         injectController(comp, controller);

         const value = makeSelectionValue({ state: 8, level: 0, value: "item1" });
         comp.updateSelectionState(value);

         expect(controller.hideSelf).toHaveBeenCalled();
      });
   });

   // Group 2: lastCellSelectedIndex is a private field; injecting it directly lets us test
   // navigate() transitions without simulating a full keyboard interaction chain.
   describe("Group 2 �?navigate", () => {
      it("should move from SEARCH_BAR to CLEAR_SEARCH on RIGHT", async () => {
         const { comp } = await renderComponent();
         comp["lastCellSelectedIndex"] = -4;

         (comp as any).navigate(NavigationKeys.RIGHT);

         expect(comp["lastCellSelectedIndex"]).toBe(-3);
      });

      it("should move from SEARCH_BAR to first cell on DOWN", async () => {
         const { comp } = await renderComponent();
         comp["lastCellSelectedIndex"] = -4;
         comp.model.dropdown = false;

         (comp as any).navigate(NavigationKeys.DOWN);

         expect(comp["lastCellSelectedIndex"]).toBe(0);
      });

      it("should close search on SPACE in CLEAR_SEARCH", async () => {
         const { comp } = await renderComponent();
         comp["lastCellSelectedIndex"] = -3;
         const controller = {
            model: comp.model,
            searchSelections: vi.fn(),
         };
         injectController(comp, controller);

         (comp as any).navigate(NavigationKeys.SPACE);

         expect(controller.searchSelections).toHaveBeenCalledWith("");
      });

      it("should toggle node on LEFT when node is open in tree", async () => {
         const { comp } = await renderComponent();
         const treeModel = makeMockTreeModel();
         comp.model = treeModel;
         comp["lastCellSelectedIndex"] = 0;
         const controller = {
            model: treeModel,
            isNodeOpen: vi.fn(() => true),
            toggleNode: vi.fn(),
            visibleValues: makeMockSelectionValues(),
         };
         injectController(comp, controller);

         (comp as any).navigate(NavigationKeys.LEFT);

         expect(controller.toggleNode).toHaveBeenCalled();
      });

      it("should toggle node on RIGHT when node is closed in tree", async () => {
         const { comp } = await renderComponent();
         const treeModel = makeMockTreeModel();
         comp.model = treeModel;
         comp["lastCellSelectedIndex"] = 0;
         const controller = {
            model: treeModel,
            isNodeOpen: vi.fn(() => false),
            toggleNode: vi.fn(),
            visibleValues: makeMockSelectionValues(),
         };
         injectController(comp, controller);

         (comp as any).navigate(NavigationKeys.RIGHT);

         expect(controller.toggleNode).toHaveBeenCalled();
      });

      it("should updateSelectionState on SPACE when in cell", async () => {
         const { comp } = await renderComponent();
         comp["lastCellSelectedIndex"] = 0;
         const controller = {
            model: comp.model,
            selectionStateUpdated: vi.fn(),
         };
         injectController(comp, controller);

         (comp as any).navigate(NavigationKeys.SPACE);

         expect(controller.selectionStateUpdated).toHaveBeenCalled();
      });

      it("should increment lastCellSelectedIndex on DOWN", async () => {
         const { comp } = await renderComponent();
         comp["lastCellSelectedIndex"] = 0;
         comp.selectedCells = new Map([["item1", new Map([[0, true]])]]);

         (comp as any).navigate(NavigationKeys.DOWN);

         expect(comp["lastCellSelectedIndex"]).toBe(1);
      });

      it("should decrement lastCellSelectedIndex on UP", async () => {
         const { comp } = await renderComponent();
         comp["lastCellSelectedIndex"] = 1;
         comp.selectedCells = new Map([["item1", new Map([[0, true]])]]);

         (comp as any).navigate(NavigationKeys.UP);

         expect(comp["lastCellSelectedIndex"]).toBe(0);
      });

      it("should clamp lastCellSelectedIndex to selectionValues.length - 1", async () => {
         const { comp } = await renderComponent();
         comp["lastCellSelectedIndex"] = 0;
         comp.selectedCells = new Map([["item1", new Map([[0, true]])]]);
         comp.selectionValues = [makeSelectionValue({ value: "only" })];

         (comp as any).navigate(NavigationKeys.DOWN);

         expect(comp["lastCellSelectedIndex"]).toBe(0);
      });
   });

   // Group 3: scriptApplied is a private field that gates the expandAll-on-model-set path;
   // reading/writing it directly is the only way to test the guard without a prior-model lifecycle.
   describe("Group 3 �?set model dispatch", () => {
      it("should create SelectionListController for VSSelectionList", async () => {
         const { comp } = await renderComponent();
         const listModel = makeMockListModel();
         comp.model = listModel;

         expect(comp.controller.model).toBe(listModel);
      });

      it("should create SelectionTreeController for VSSelectionTree", async () => {
         const { comp } = await renderComponent();
         const treeModel = makeMockTreeModel();
         comp.model = treeModel;

         expect(comp.controller.model).toBe(treeModel);
      });

      it("should expand all nodes when expandAll is true and scriptApplied is false", async () => {
         const { comp } = await renderComponent();
         const treeModel = makeMockTreeModel();
         treeModel.expandAll = true;
         comp.model = treeModel;

         expect((comp as any).scriptApplied).toBe(true);
      });

      it("should not expand all nodes when scriptApplied is already true", async () => {
         const { comp } = await renderComponent();
         (comp as any).scriptApplied = true;
         const treeModel = makeMockTreeModel();
         treeModel.expandAll = true;
         comp.model = treeModel;

         expect((comp as any).scriptApplied).toBe(true);
      });

      it("should reuse existing controller when model.absoluteName matches", async () => {
         const { comp } = await renderComponent();
         const listModel = makeMockListModel();
         comp.model = listModel;
         const originalController = comp.controller;

         comp.model = listModel;

         expect(comp.controller).toBe(originalController);
      });

      it("should clear unappliedSelections when model is refreshed", async () => {
         const { comp } = await renderComponent();
         const listModel = makeMockListModel();
         comp.model = listModel;
         comp.controller.unappliedSelections = [{ value: ["test"], selected: true }];

         comp.model = listModel;

         expect(comp.controller.unappliedSelections.length).toBe(0);
      });
   });

   describe("Group 4 �?disPlayZIndex", () => {
      it("should return null when not in viewer", async () => {
         const context = { viewer: false, preview: false };
         const { comp } = await renderComponent({ context });

         expect(comp["disPlayZIndex"]).toBeNull();
      });

      it("should return zIndex + 9999 when dropdown is visible", async () => {
         const { comp } = await renderComponent();
         comp.model.dropdown = true;
         comp.model.objectFormat.zIndex = 1000;

         expect(comp["disPlayZIndex"]).toBe(1000 + 9999);
      });

      it("should return zIndex + 9999 when pop is current in maxMode on mobile", async () => {
         const popService = { isCurrentPopComponent: vi.fn(() => true) };
         const { comp } = await renderComponent({ popService });
         comp.model.maxMode = true;
         comp.model.container = "Container1";
         (comp as any).mobileDevice = true;
         comp.model.objectFormat.zIndex = 1000;

         expect(comp["disPlayZIndex"]).toBe(1000 + 9999);
      });

      it("should return base zIndex when no special conditions", async () => {
         const { comp } = await renderComponent();
         comp.model.dropdown = false;
         comp.model.objectFormat.zIndex = 1000;

         expect(comp["disPlayZIndex"]).toBe(1000);
      });
   });

   describe("Group 5 �?topPosition", () => {
      it("should return objectFormat.top for viewer non-dropdown", async () => {
         const { comp } = await renderComponent();
         const listModel = makeMockListModel();
         listModel.dropdown = false;
         listModel.objectFormat.top = 100;
         comp.model = listModel;

         expect(comp.topPosition).toBe(100);
      });

      it("should shift up for viewer dropdown at bottom when popDown is false", async () => {
         const { comp } = await renderComponent();
         const listModel = makeMockListModel();
         listModel.dropdown = true;
         listModel.hidden = false;
         listModel.maxMode = false;
         listModel.objectFormat.top = 100;
         listModel.titleFormat.height = 20;
         listModel.listHeight = 5;
         listModel.cellHeight = 18;
         comp.model = listModel;
         comp.model.hidden = false;
         comp.atBottom = true;
         comp.objectContainerHeight = 100;

         const result = comp.topPosition;
         const expectedTop = listModel.objectFormat.top - listModel.listHeight * listModel.cellHeight;
         expect(result).toBe(expectedTop);
      });

      it("should return null for composer mode non-dropdown", async () => {
         const context = { viewer: false, preview: false };
         const { comp } = await renderComponent({ context });
         const listModel = makeMockListModel();
         listModel.dropdown = false;
         comp.model = listModel;

         expect(comp.topPosition).toBeNull();
      });
   });

   describe("Group 6 �?calcCellWidth", () => {
      it("should calculate cell width based on body width and numCols", async () => {
         const { comp } = await renderComponent();
         const listModel = makeMockListModel();
         listModel.numCols = 2;
         listModel.objectFormat.width = 200;
         comp.model = listModel;

         comp.calcCellWidth();

         expect(comp.cellWidth).toBe(100);
      });

      it("should clamp numCols to max available selectionValues", async () => {
         const { comp } = await renderComponent();
         const listModel = makeMockListModel();
         listModel.numCols = 100;
         listModel.objectFormat.width = 200;
         setSelectionValuesForTest(comp, [
            makeSelectionValue({ value: "one" }),
            makeSelectionValue({ value: "two" }),
         ]);
         comp.model = listModel;

         comp.calcCellWidth();

         expect(comp.cellWidth).toBe(100);
      });

      it("should use at least 1 column", async () => {
         const { comp } = await renderComponent();
         const listModel = makeMockListModel();
         listModel.numCols = 0;
         listModel.objectFormat.width = 200;
         comp.model = listModel;

         comp.calcCellWidth();

         expect(comp.cellWidth).toBe(200);
      });
   });

   describe("Group 7 �?getBodyHeight", () => {
      it("should calculate body height for container mode", async () => {
         const { comp } = await renderComponent();
         const listModel = makeMockListModel();
         listModel.containerType = "VSSelectionContainer";
         listModel.objectFormat.height = 200;
         listModel.titleFormat.height = 20;
         comp.model = listModel;

         const height = comp.getBodyHeight();
         expect(height).toBe(180);
      });

      it("should calculate body height for dropdown mode", async () => {
         const { comp } = await renderComponent();
         const listModel = makeMockListModel();
         listModel.dropdown = true;
         listModel.maxMode = false;
         listModel.cellHeight = 20;
         listModel.listHeight = 5;
         comp.model = listModel;

         const height = comp.getBodyHeight();
         expect(height).toBe(100);
      });

      it("should account for search bar when displayed", async () => {
         const { comp } = await renderComponent();
         const listModel = makeMockListModel();
         listModel.searchDisplayed = true;
         listModel.titleFormat.height = 20;
         listModel.objectFormat.height = 200;
         comp.model = listModel;

         const height = comp.getBodyHeight();
         expect(height).toBeLessThan(180);
      });
   });

   describe("Group 8 �?getTitleWidth", () => {
      it("should return title width without margins", async () => {
         const { comp } = await renderComponent();
         const listModel = makeMockListModel();
         listModel.titleFormat.width = 150;
         listModel.titleFormat.border = { top: "0px", left: "0px", right: "0px", bottom: "0px" };
         listModel.objectFormat.border = { top: "0px", left: "0px", right: "0px", bottom: "0px" };
         comp.model = listModel;

         expect(comp.getTitleWidth()).toBe(150);
      });

      it("should apply margin adjustment when object margins are present", async () => {
         const { comp } = await renderComponent();
         const listModel = makeMockListModel();
         listModel.titleFormat.width = 150;
         listModel.titleFormat.border = { top: "0px", left: "0px", right: "0px", bottom: "0px" };
         listModel.objectFormat.border = { top: "1px", left: "4px", right: "6px", bottom: "1px" };
         comp.model = listModel;

         // formula: 150 - 4 - 6 + 0 + 0 = 140
         const width = comp.getTitleWidth();
         expect(width).toBe(140);
      });
   });

   describe("Group 9 �?updateListSelectedString", () => {
      it("should include values with state 1", async () => {
         const { comp } = await renderComponent();
         setSelectionValuesForTest(comp, [
            makeSelectionValue({ label: "Selected", value: "sel", state: 1, level: 0 }),
            makeSelectionValue({ label: "Unselected", value: "unsel", state: 8, level: 0 }),
         ]);

         comp.updateListSelectedString();

         expect(comp.listSelectedString).toBe("Selected");
      });

      it("should include values with state 9", async () => {
         const { comp } = await renderComponent();
         setSelectionValuesForTest(comp, [
            makeSelectionValue({ label: "Selected", value: "sel", state: 9, level: 0 }),
         ]);

         comp.updateListSelectedString();

         expect(comp.listSelectedString).toBe("Selected");
      });

      it("should include values with state 10", async () => {
         const { comp } = await renderComponent();
         setSelectionValuesForTest(comp, [
            makeSelectionValue({ label: "Selected", value: "sel", state: 10, level: 0 }),
         ]);

         comp.updateListSelectedString();

         expect(comp.listSelectedString).toBe("Selected");
      });

      it("should join multiple selected values with comma", async () => {
         const { comp } = await renderComponent();
         setSelectionValuesForTest(comp, [
            makeSelectionValue({ label: "Item 1", value: "i1", state: 9, level: 0 }),
            makeSelectionValue({ label: "Item 2", value: "i2", state: 9, level: 0 }),
         ]);

         comp.updateListSelectedString();

         expect(comp.listSelectedString).toBe("Item 1, Item 2");
      });

      it("should return (none) when no values are selected", async () => {
         const { comp } = await renderComponent();
         setSelectionValuesForTest(comp, [
            makeSelectionValue({ label: "Unselected", value: "unsel", state: 8, level: 0 }),
         ]);

         comp.updateListSelectedString();

         expect(comp.listSelectedString).toBe("(none)");
      });
   });

   describe("Group 10 �?getIdentifier", () => {
      it("should return value for leaf node", async () => {
         const { comp } = await renderComponent();
         const value = { value: "leaf", parentNode: null };

         const id = comp.getIdentifier(value as any);

         expect(id).toBe("leaf");
      });

      it("should build identifier from parent chain", async () => {
         const { comp } = await renderComponent();
         const parent = { value: "parent", parentNode: null };
         const child = { value: "child", parentNode: parent };
         const grandchild = { value: "grandchild", parentNode: child };

         const id = comp.getIdentifier(grandchild as any);

         expect(id).toBe("grandchild:child:parent");
      });
   });

   describe("Group 11 �?selectAll", () => {
      it("should select included values", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController(comp.model);
         injectController(comp, controller);
         const values = [makeSelectionValue({ state: SelectionValue.STATE_INCLUDED, value: "test" })];

         comp["selectAll"](values);

         expect(controller.selectionStateUpdated).toHaveBeenCalled();
      });

      it("should select compatible values", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController(comp.model);
         injectController(comp, controller);
         const values = [makeSelectionValue({ state: SelectionValue.STATE_COMPATIBLE, value: "test" })];

         comp["selectAll"](values);

         expect(controller.selectionStateUpdated).toHaveBeenCalled();
      });

      it("should not select excluded values by default", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController(comp.model);
         injectController(comp, controller);
         const values = [makeSelectionValue({ state: 32, value: "test" })];

         comp["selectAll"](values);

         expect(controller.selectionStateUpdated).not.toHaveBeenCalled();
      });

      it("should select excluded values when force is true", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController(comp.model);
         injectController(comp, controller);
         const values = [makeSelectionValue({ state: 32, value: "test" })];

         comp["selectAll"](values, true, true);

         expect(controller.selectionStateUpdated).toHaveBeenCalled();
      });

      it("should recurse into composite selection values", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController(comp.model);
         injectController(comp, controller);
         const childValues = [makeSelectionValue({ state: SelectionValue.STATE_INCLUDED, value: "child", parentNode: null })];
         const values = [{
            ...makeSelectionValue({ state: SelectionValue.STATE_INCLUDED, value: "parent", parentNode: null }),
            selectionList: { ...makeEmptySelectionList(), selectionValues: childValues },
         }];

         comp["selectAll"](values);

         expect(controller.selectionStateUpdated).toHaveBeenCalledTimes(2);
      });

      it("should unselect when select is false", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController(comp.model);
         injectController(comp, controller);
         const values = [makeSelectionValue({ state: 9, value: "test" })];

         comp["selectAll"](values, false);

         expect(controller.selectionStateUpdated).toHaveBeenCalled();
      });
   });

   // ─────────────────────────────────────────────────────────────────────────
   // Group 12 �?trackByIdx: stable virtual-scroll tracking keys
   // Pure-function tests; no rendering required.
   // Ported from vs-selection.component.spec.ts (trackByIdx describe block).
   // ─────────────────────────────────────────────────────────────────────────
   describe("Group 12 �?trackByIdx: stable virtual-scroll tracking keys", () => {
      const trackByIdx = VSSelection.prototype.trackByIdx.bind({});

      it("should return a stable value key for a SelectionValueModel item", () => {
         const item = { value: "California", label: "California", state: 1 };
         expect(trackByIdx(0, item)).toBe("California");
         expect(trackByIdx(3, item)).toBe("California");
      });

      it("should return the same key regardless of position index", () => {
         const item = { value: "East", label: "East", state: 2 };
         expect(trackByIdx(0, item)).toBe(trackByIdx(7, item));
      });

      it("should use item[0].value for an array row (outer loop)", () => {
         const row = [
            { value: "North", label: "North", state: 0 },
            { value: "South", label: "South", state: 0 },
         ];
         expect(trackByIdx(0, row)).toBe("North");
         expect(trackByIdx(5, row)).toBe("North");
      });

      it("should fall back to index for null item", () => {
         expect(trackByIdx(4, null)).toBe(4);
      });

      it("should fall back to index for undefined item", () => {
         expect(trackByIdx(2, undefined)).toBe(2);
      });

      it("should fall back to index for item with no value property", () => {
         expect(trackByIdx(1, {})).toBe(1);
      });

      it("should fall back to index for array row whose first element is null", () => {
         expect(trackByIdx(3, [null])).toBe(3);
      });

      it("should fall back to index for array row whose first element has null value property", () => {
         expect(trackByIdx(5, [{ value: null, label: "Something" }])).toBe(5);
      });

      it("should fall back to index for item with null value property", () => {
         expect(trackByIdx(2, { value: null, label: "Something" })).toBe(2);
      });

      it("should return empty string as the key when value is empty string", () => {
         expect(trackByIdx(0, { value: "", label: "Blank" })).toBe("");
      });

      it("should return different keys for items with different values", () => {
         const itemA = { value: "Alpha", label: "Alpha", state: 0 };
         const itemB = { value: "Beta", label: "Beta", state: 0 };
         expect(trackByIdx(0, itemA)).not.toBe(trackByIdx(0, itemB));
      });
   });

   // ─────────────────────────────────────────────────────────────────────────
   // Group 13 �?setQuickSwitchHover: overlay button positioning math
   // Direct-instantiation tests; no rendering required.
   // Ported from the "quick-switch overlay positioner" describe block in
   // vs-selection.spec.ts, which mocked Renderer2 to capture setStyle calls.
   // ─────────────────────────────────────────────────────────────────────────
   describe("Group 13 �?setQuickSwitchHover: overlay button positioning math", () => {
      function makeRect(left: number, top: number, width: number, height: number): DOMRect {
         return {
            left, top, width, height,
            right: left + width, bottom: top + height,
            x: left, y: top,
            toJSON: () => ({}),
         } as DOMRect;
      }

      function makeCell(rect: DOMRect, hasMeasure: boolean, labelRect?: DOMRect): any {
         return {
            getBoundingClientRect: () => rect,
            querySelector: (sel: string) => {
               if(sel === ".selection-list-cell-content") return hasMeasure ? {} : null;
               if(sel === ".selection-list-cell-label") {
                  return labelRect ? { getBoundingClientRect: () => labelRect } : null;
               }
               return null;
            },
         };
      }

      async function createQsTest(opts: { scale?: number; showScroll?: boolean; listRect?: DOMRect } = {}) {
         const { scale = 1, showScroll = false, listRect = makeRect(0, 0, 200, 300) } = opts;
         const setStyleCalls: Array<{ el: any; prop: string; value: string }> = [];

         const listEl: any = {
            getBoundingClientRect: () => listRect,
            querySelector: vi.fn(() => null),
            querySelectorAll: vi.fn(() => []),
         };
         const btnEl: any = { offsetWidth: 24 };

         const renderer: any = {
            setStyle: (el: any, prop: string, value: string) => setStyleCalls.push({ el, prop, value }),
            removeStyle: vi.fn(),
            setAttribute: vi.fn(),
            addClass: vi.fn(),
            removeClass: vi.fn(),
            listen: vi.fn(() => () => {}),
         };

         const elementRef: any = {
            nativeElement: { querySelector: (sel: string) => sel === ".selection-list" ? listEl : null },
         };

         const { comp } = await createSelectionComponent({ renderer, elementRef });

         (comp as any).quickSwitchOverlay = { nativeElement: btnEl };
         (comp as any).quickSwitchOverlayIcon = { nativeElement: {} };
         (comp as any).verticalScrollWrapper = { nativeElement: {} };
         (comp as any).scale = scale;
         (comp as any).scrollbarWidth = 16;
         Object.defineProperty(comp, "showScroll", { get: () => showScroll, configurable: true });

         const lastStyle = (el: any, prop: string) =>
            [...setStyleCalls].reverse().find(c => c.el === el && c.prop === prop)?.value;

         return { comp, setStyleCalls, listEl, btnEl, lastStyle };
      }

      it("with measure content: anchors button at labelEl.right (column-agnostic)", async () => {
         const { comp, listEl, btnEl, lastStyle } = await createQsTest();
         const cell = makeCell(makeRect(0, 0, 100, 30), true, makeRect(0, 0, 70, 30));
         comp.setQuickSwitchHover(cell, false, () => {});

         // labelRight(70) - listLeft(0) = 70
         expect(lastStyle(btnEl, "left")).toBe("70px");
         expect(lastStyle(btnEl, "right")).toBe("auto");
         expect(lastStyle(listEl, "width")).toBe("200px");
      });

      it("non-last column without measure: anchors button at right edge of hovered cell", async () => {
         const { comp, btnEl, lastStyle } = await createQsTest();
         const cell = makeCell(makeRect(0, 0, 100, 30), false);
         comp.setQuickSwitchHover(cell, false, () => {});

         // cellRight(100) - btn(24) = 76; list(200) - 0 - btn(24) = 176; min = 76
         expect(lastStyle(btnEl, "left")).toBe("76px");
         expect(lastStyle(btnEl, "right")).toBe("auto");
      });

      it("last/single column without measure, with scrollbar: clamps to listRight - scrollbar - btn", async () => {
         const { comp, btnEl, lastStyle } = await createQsTest({ showScroll: true });
         const cell = makeCell(makeRect(0, 0, 200, 30), false);
         comp.setQuickSwitchHover(cell, false, () => {});

         // cellRight(200) - btn(24) = 176; list(200) - scrollbar(16) - btn(24) = 160; min = 160
         expect(lastStyle(btnEl, "left")).toBe("160px");
      });

      it("clamps button left to 0 when cell is narrower than the button", async () => {
         const { comp, btnEl, lastStyle } = await createQsTest();
         const cell = makeCell(makeRect(0, 0, 10, 30), false);
         comp.setQuickSwitchHover(cell, false, () => {});

         // cellRight(10) - btn(24) = -14 �?clamped to 0 by Math.max(0, ...)
         expect(lastStyle(btnEl, "left")).toBe("0px");
      });

      it("does not expand body width or apply margin-left to adjacent columns", async () => {
         const { comp, listEl, btnEl, setStyleCalls } = await createQsTest();
         const cell = makeCell(makeRect(0, 0, 100, 30), false);
         comp.setQuickSwitchHover(cell, false, () => {});

         expect(setStyleCalls.find(c => c.el === btnEl && c.prop === "left")).toBeDefined();
         expect(setStyleCalls.find(c => c.el === listEl && c.prop === "width")).toBeDefined();
         expect(setStyleCalls.find(c => c.prop === "margin-left")).toBeUndefined();
         expect(setStyleCalls.find(c => c.prop === "width" && c.el !== listEl)).toBeUndefined();
      });

      it("converts viewport coordinates to CSS px when scale != 1", async () => {
         const { comp, listEl, btnEl, lastStyle } = await createQsTest({
            scale: 2,
            listRect: makeRect(100, 0, 400, 600),
         });
         // Cell at viewport (100,0) 200×60 px �?list-relative (0,0) 100×30 CSS px (non-last column).
         const cell = makeCell(makeRect(100, 0, 200, 60), false);
         comp.setQuickSwitchHover(cell, false, () => {});

         // (cellRight 300 - listLeft 100) / scale 2 = 100 CSS px �?100 - btn(24) = 76;
         // listWidth 400/2=200 - 0 - btn(24) = 176; min = 76.
         expect(lastStyle(btnEl, "left")).toBe("76px");
         expect(lastStyle(listEl, "width")).toBe("200px");
      });

      it("falls back to cell-rect math when measure content has no labelEl (defensive)", async () => {
         const { comp, btnEl, lastStyle } = await createQsTest();
         // hasMeasure=true but no labelRect �?falls to else branch
         const cell = makeCell(makeRect(0, 0, 100, 30), true /* hasMeasure, no labelRect */);
         comp.setQuickSwitchHover(cell, false, () => {});

         // cellRight(100) - btn(24) = 76
         expect(lastStyle(btnEl, "left")).toBe("76px");
      });

      it("returns early without writing position styles when the list element is missing", async () => {
         const { comp, btnEl, listEl, lastStyle } = await createQsTest();
         (comp as any).elementRef = { nativeElement: { querySelector: () => null } };
         const cell = makeCell(makeRect(0, 0, 100, 30), false);
         comp.setQuickSwitchHover(cell, false, () => {});

         expect(lastStyle(btnEl, "left")).toBeUndefined();
         expect(lastStyle(listEl, "width")).toBeUndefined();
         expect((comp as any)._currentHoverElement).toBe(cell);
      });
   });
});

