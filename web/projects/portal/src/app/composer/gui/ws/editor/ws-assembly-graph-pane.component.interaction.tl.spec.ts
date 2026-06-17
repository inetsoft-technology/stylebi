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
 * WSAssemblyGraphPaneComponent — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — @Output emitters: cutAssembly, copyAssembly, toggleAutoUpdate,
 *                        paste, notify emit correct payloads
 *   Group 2  [Risk 3] — selectAssembly: single-select, ctrl/shift multi-select,
 *                        skip when already focused; sets selectAssemblyFlag
 *   Group 3  [Risk 3] — clickAssembly: clears flag; ctrl+click deselects focused assembly
 *   Group 4  [Risk 2] — onSelectionBox: filters assemblies by rectangle intersection
 *   Group 5  [Risk 2] — getThumbnailClasses: CSS class map based on focused/dimmed/primary state
 *   Group 6  [Risk 2] — oozKeyDown: Delete → removeFocusedAssemblies; ctrl+A → select all;
 *                        ctrl+V → emit onPaste; arrow keys → arrowKeyMove
 *   Group 7  [Risk 1] — getAssemblyName: always returns null
 *   Group 8  [Risk 1] — trackByFn: returns assembly.name
 *   Group 9  [Risk 1] — isWorksheetEmpty: true only when tables/variables/groupings all empty
 *   Group 10 [Risk 1] — startEditName: calls worksheet.selectOnlyAssembly
 *   Group 11 [Risk 1] — tableEndpoints: returns TABLE_ENDPOINTS from config
 *   Group 12 [Risk 1] — selectDependent: adds dependent assemblies to focused set
 *
 * Confirmed bugs: none
 *
 * Suspected bugs (header only):
 *   Suspicion A — selectAssembly does NOT push if already focused, but clickAssembly
 *     deselects only on ctrl+click. A plain click on an already-focused assembly does
 *     nothing via either path; there is no deselect-on-second-click path for single-click.
 *
 * Out of scope this pass (covered in ws-assembly-graph-pane.component.risk.tl.spec.ts):
 *   clearSelection, selectCompositeTable, removeFocusedAssemblies, removeAssemblies,
 *   destroyAssembly, arrowKeyMove, arrowKeyMoveEnd, moveAssemblies, subscribeToFocus
 */

import { EMPTY, of } from "rxjs";
import { HttpResponse } from "@angular/common/http";
import { WSAssemblyGraphPaneComponent } from "./ws-assembly-graph-pane.component";
import { Worksheet } from "../../../data/ws/worksheet";
import { WSAssembly } from "../../../data/ws/ws-assembly";
import { CompositeTableAssembly } from "../../../data/ws/composite-table-assembly";
import { ComponentTool } from "../../../../common/util/component-tool";
import { Point } from "../../../../common/data/point";
import { Rectangle } from "../../../../common/data/rectangle";
import {
   makeComponent,
   makeWorksheet,
   makeJspMock,
} from "./ws-assembly-graph-pane.component.test-helpers";

// ---------------------------------------------------------------------------
// Per-test reset
// ---------------------------------------------------------------------------

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeAssembly(overrides: Partial<WSAssembly> = {}): WSAssembly {
   return {
      name: "TestAssembly",
      description: "",
      top: 10,
      left: 10,
      width: 100,
      height: 50,
      dependeds: [],
      dependings: [],
      primary: false,
      info: { editable: true, mirrorInfo: null } as any,
      classType: "TableAssembly",
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Group 1: @Output emitters [Risk 3]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — @Output emitters", () => {

   // 🔁 Regression-sensitive: cut/copy must carry the worksheet ref, not the assembly,
   //    so the parent can identify which sheet is being operated on.
   it("cutAssembly should emit onCut with the worksheet", () => {
      const { comp, mocks } = makeComponent();
      const spy = vi.fn();
      comp.onCut.subscribe(spy);
      const assembly = makeAssembly();

      comp.cutAssembly(assembly);

      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy).toHaveBeenCalledWith(mocks.worksheet);
   });

   it("copyAssembly should emit onCopy with the worksheet", () => {
      const { comp, mocks } = makeComponent();
      const spy = vi.fn();
      comp.onCopy.subscribe(spy);
      const assembly = makeAssembly();

      comp.copyAssembly(assembly);

      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy).toHaveBeenCalledWith(mocks.worksheet);
   });

   it("toggleAutoUpdate should emit onToggleAutoUpdate with the assembly", () => {
      const { comp } = makeComponent();
      const spy = vi.fn();
      comp.onToggleAutoUpdate.subscribe(spy);
      const assembly = makeAssembly({ name: "AutoUpdateAssembly" });

      comp.toggleAutoUpdate(assembly);

      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy).toHaveBeenCalledWith(assembly);
   });

   it("paste should emit onPaste with [worksheet, position]", () => {
      const { comp, mocks } = makeComponent();
      const spy = vi.fn();
      comp.onPaste.subscribe(spy);
      const position = new Point(42, 99);

      comp.paste(position);

      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy).toHaveBeenCalledWith([mocks.worksheet, position]);
   });

   it("notify should emit onNotification with the notification object", () => {
      const { comp } = makeComponent();
      const spy = vi.fn();
      comp.onNotification.subscribe(spy);
      const notification = { type: "success" as const, message: "Done!" };

      comp.notify(notification);

      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy).toHaveBeenCalledWith(notification);
   });
});

// ---------------------------------------------------------------------------
// Group 2: selectAssembly [Risk 3]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — selectAssembly", () => {

   // 🔁 Regression-sensitive: if already focused we must NOT push again because
   //    duplicate entries in currentFocusedAssemblies cause the details pane to
   //    render the same assembly twice.
   it("should not change focused assemblies when assembly is already focused", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "A1" });
      mocks.worksheet.currentFocusedAssemblies = [assembly];
      const before = mocks.worksheet.currentFocusedAssemblies.slice();

      comp.selectAssembly({ ctrlKey: false, shiftKey: false } as MouseEvent, assembly);

      expect(mocks.worksheet.currentFocusedAssemblies).toEqual(before);
   });

   it("should call worksheet.selectAssembly when ctrl key is held", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "B1" });
      const spy = vi.spyOn(mocks.worksheet, "selectAssembly");

      comp.selectAssembly({ ctrlKey: true, shiftKey: false } as MouseEvent, assembly);

      expect(spy).toHaveBeenCalledWith(assembly);
   });

   it("should call worksheet.selectAssembly when shift key is held", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "C1" });
      const spy = vi.spyOn(mocks.worksheet, "selectAssembly");

      comp.selectAssembly({ ctrlKey: false, shiftKey: true } as MouseEvent, assembly);

      expect(spy).toHaveBeenCalledWith(assembly);
   });

   it("should set currentFocusedAssemblies to [assembly] for plain click on unfocused", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "D1" });

      comp.selectAssembly({ ctrlKey: false, shiftKey: false } as MouseEvent, assembly);

      expect(mocks.worksheet.currentFocusedAssemblies).toEqual([assembly]);
   });

   it("should set selectAssemblyFlag to true after selection", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "E1" });

      comp.selectAssembly({ ctrlKey: false, shiftKey: false } as MouseEvent, assembly);

      expect((comp as any).selectAssemblyFlag).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 3: clickAssembly [Risk 3]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — clickAssembly", () => {

   it("should clear selectAssemblyFlag when it is true (assembly was just selected)", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "F1" });
      (comp as any).selectAssemblyFlag = true;

      comp.clickAssembly({ ctrlKey: false, shiftKey: false } as MouseEvent, assembly);

      expect((comp as any).selectAssemblyFlag).toBe(false);
   });

   it("should NOT deselect assembly when selectAssemblyFlag is true (even with ctrl)", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "G1" });
      mocks.worksheet.currentFocusedAssemblies = [assembly];
      (comp as any).selectAssemblyFlag = true;
      const spy = vi.spyOn(mocks.worksheet, "deselectAssembly");

      comp.clickAssembly({ ctrlKey: true, shiftKey: false } as MouseEvent, assembly);

      expect(spy).not.toHaveBeenCalled();
   });

   it("ctrl+click on focused assembly should call deselectAssembly when flag is false", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "H1" });
      mocks.worksheet.currentFocusedAssemblies = [assembly];
      (comp as any).selectAssemblyFlag = false;
      const spy = vi.spyOn(mocks.worksheet, "deselectAssembly");

      comp.clickAssembly({ ctrlKey: true, shiftKey: false } as MouseEvent, assembly);

      expect(spy).toHaveBeenCalledWith(assembly);
   });

   it("plain click on focused assembly with flag=false should be a no-op", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "I1" });
      mocks.worksheet.currentFocusedAssemblies = [assembly];
      (comp as any).selectAssemblyFlag = false;
      const spy = vi.spyOn(mocks.worksheet, "deselectAssembly");

      comp.clickAssembly({ ctrlKey: false, shiftKey: false } as MouseEvent, assembly);

      expect(spy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 4: onSelectionBox [Risk 2]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — onSelectionBox", () => {

   // 🔁 Regression-sensitive: box intersection is the sole mechanism for rubber-band
   //    multi-select; off-by-one errors here lose assemblies from selection.
   it("should set currentFocusedAssemblies to assemblies intersecting the box", () => {
      const { comp, mocks } = makeComponent();

      const inside = makeAssembly({ name: "Inside", left: 20, top: 20, width: 30, height: 20 });
      const outside = makeAssembly({ name: "Outside", left: 300, top: 300, width: 30, height: 20 });
      mocks.worksheet.tables = [inside, outside] as any;

      // Selection box covers 0–100 in both axes
      const selectionBox = new Rectangle(0, 0, 100, 100);
      comp.onSelectionBox({ box: selectionBox } as any);

      expect(mocks.worksheet.currentFocusedAssemblies).toContain(inside);
      expect(mocks.worksheet.currentFocusedAssemblies).not.toContain(outside);
   });

   it("should produce an empty selection when no assemblies intersect the box", () => {
      const { comp, mocks } = makeComponent();
      const farAway = makeAssembly({ name: "Far", left: 500, top: 500, width: 50, height: 50 });
      mocks.worksheet.tables = [farAway] as any;

      const selectionBox = new Rectangle(0, 0, 10, 10);
      comp.onSelectionBox({ box: selectionBox } as any);

      expect(mocks.worksheet.currentFocusedAssemblies).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 5: getThumbnailClasses [Risk 2]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — getThumbnailClasses", () => {

   it("should include ws-assembly-graph-element--selected when assembly is focused", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "Sel" });
      mocks.worksheet.currentFocusedAssemblies = [assembly];

      const classes = comp.getThumbnailClasses(assembly);

      expect(classes["ws-assembly-graph-element--selected"]).toBe(true);
   });

   it("should NOT mark selected when assembly is not focused", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "NotSel" });
      mocks.worksheet.currentFocusedAssemblies = [];

      const classes = comp.getThumbnailClasses(assembly);

      expect(classes["ws-assembly-graph-element--selected"]).toBe(false);
   });

   it("should include ws-assembly-graph-element--dimmed when not in nonDimmedAssemblies and set non-empty", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "Dimmed" });
      const other = makeAssembly({ name: "Other" });
      // Set nonDimmedAssemblies to contain only 'other', not 'assembly'
      comp.nonDimmedAssemblies = new Set(["Other"]);
      (comp as any).isDragging = false;

      const classes = comp.getThumbnailClasses(assembly);

      expect(classes["ws-assembly-graph-element--dimmed"]).toBe(true);
   });

   it("should NOT include dimmed when isDragging is true", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "DragAssembly" });
      comp.nonDimmedAssemblies = new Set(["Other"]);
      (comp as any).isDragging = true;

      const classes = comp.getThumbnailClasses(assembly);

      expect(classes["ws-assembly-graph-element--dimmed"]).toBe(false);
   });

   it("should include ws-assembly-graph-element--primary when assembly.primary is true", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "Primary", primary: true });

      const classes = comp.getThumbnailClasses(assembly);

      expect(classes["ws-assembly-graph-element--primary"]).toBe(true);
   });

   it("should NOT include primary class when assembly.primary is false", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "NotPrimary", primary: false });

      const classes = comp.getThumbnailClasses(assembly);

      expect(classes["ws-assembly-graph-element--primary"]).toBe(false);
   });

   it("should include selection-adjacent when assembly is not focused but is in nonDimmedAssemblies", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "Adj" });
      mocks.worksheet.currentFocusedAssemblies = [];
      comp.nonDimmedAssemblies = new Set(["Adj"]);

      const classes = comp.getThumbnailClasses(assembly);

      expect(classes["ws-assembly-graph-element--selection-adjacent"]).toBe(true);
   });

   it("should NOT include selection-adjacent when assembly is focused", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "FocusedAdj" });
      mocks.worksheet.currentFocusedAssemblies = [assembly];
      comp.nonDimmedAssemblies = new Set(["FocusedAdj"]);

      const classes = comp.getThumbnailClasses(assembly);

      expect(classes["ws-assembly-graph-element--selection-adjacent"]).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 6: oozKeyDown [Risk 2]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — oozKeyDown", () => {

   function makeKeyEvent(overrides: Partial<KeyboardEvent>): KeyboardEvent {
      return {
         repeat: false,
         keyCode: 0,
         key: "",
         ctrlKey: false,
         shiftKey: false,
         altKey: false,
         preventDefault: vi.fn(),
         stopPropagation: vi.fn(),
         target: {},
         ...overrides,
      } as any;
   }

   it("Delete key should call removeFocusedAssemblies via zone.run when worksheet is focused", () => {
      const { comp, mocks } = makeComponent();
      mocks.worksheet.isFocused = true;
      const removeSpy = vi.spyOn(comp, "removeFocusedAssemblies").mockImplementation(() => {});

      comp.oozKeyDown(makeKeyEvent({ keyCode: 46 }));

      expect(removeSpy).toHaveBeenCalled();
   });

   it("Delete key should NOT call removeFocusedAssemblies when worksheet is not focused", () => {
      const { comp, mocks } = makeComponent();
      mocks.worksheet.isFocused = false;
      const removeSpy = vi.spyOn(comp, "removeFocusedAssemblies").mockImplementation(() => {});

      comp.oozKeyDown(makeKeyEvent({ keyCode: 46 }));

      expect(removeSpy).not.toHaveBeenCalled();
   });

   it("ctrl+A should set all assemblies as focused when worksheet is focused", () => {
      const { comp, mocks } = makeComponent();
      mocks.worksheet.isFocused = true;
      const a1 = makeAssembly({ name: "X1" });
      const a2 = makeAssembly({ name: "X2" });
      mocks.worksheet.tables = [a1] as any;
      mocks.worksheet.groupings = [] as any;
      mocks.worksheet.variables = [a2] as any;

      comp.oozKeyDown(makeKeyEvent({ keyCode: 65, ctrlKey: true }));

      // currentFocusedAssemblies should contain all assemblies returned by worksheet.assemblies()
      const names = mocks.worksheet.currentFocusedAssemblies.map((a: WSAssembly) => a.name);
      expect(names).toContain("X1");
      expect(names).toContain("X2");
   });

   it("ctrl+V should emit onPaste when worksheet is focused and target is not a text editor", () => {
      const { comp, mocks } = makeComponent();
      mocks.worksheet.isFocused = true;
      const spy = vi.fn();
      comp.onPaste.subscribe(spy);

      // keyCode 86 = V
      comp.oozKeyDown(makeKeyEvent({ keyCode: 86, ctrlKey: true, target: {} as HTMLElement }));

      expect(spy).toHaveBeenCalled();
   });

   it("ctrl+V should NOT emit onPaste when target is an HTMLInputElement", () => {
      const { comp, mocks } = makeComponent();
      mocks.worksheet.isFocused = true;
      const spy = vi.fn();
      comp.onPaste.subscribe(spy);

      const inputEl = document.createElement("input");
      comp.oozKeyDown(makeKeyEvent({ keyCode: 86, ctrlKey: true, target: inputEl }));

      expect(spy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 7: getAssemblyName [Risk 1]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — getAssemblyName", () => {

   it("should always return null", () => {
      const { comp } = makeComponent();

      expect(comp.getAssemblyName()).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 8: trackByFn [Risk 1]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — trackByFn", () => {

   it("should return assembly.name", () => {
      const { comp } = makeComponent();
      const assembly = makeAssembly({ name: "TrackMe" });

      expect(comp.trackByFn(0, assembly)).toBe("TrackMe");
   });

   it("should use the index parameter but track by name regardless of index", () => {
      const { comp } = makeComponent();
      const assembly = makeAssembly({ name: "TrackMe2" });

      expect(comp.trackByFn(99, assembly)).toBe("TrackMe2");
   });
});

// ---------------------------------------------------------------------------
// Group 9: isWorksheetEmpty [Risk 1]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — isWorksheetEmpty", () => {

   it("should return true when tables, variables, and groupings are all empty", () => {
      const { comp, mocks } = makeComponent();
      mocks.worksheet.tables = [];
      mocks.worksheet.variables = [];
      mocks.worksheet.groupings = [];

      expect(comp.isWorksheetEmpty()).toBe(true);
   });

   it("should return false when tables has items", () => {
      const { comp, mocks } = makeComponent();
      mocks.worksheet.tables = [makeAssembly()] as any;
      mocks.worksheet.variables = [];
      mocks.worksheet.groupings = [];

      expect(comp.isWorksheetEmpty()).toBe(false);
   });

   it("should return false when variables has items", () => {
      const { comp, mocks } = makeComponent();
      mocks.worksheet.tables = [];
      mocks.worksheet.variables = [makeAssembly()] as any;
      mocks.worksheet.groupings = [];

      expect(comp.isWorksheetEmpty()).toBe(false);
   });

   it("should return false when groupings has items", () => {
      const { comp, mocks } = makeComponent();
      mocks.worksheet.tables = [];
      mocks.worksheet.variables = [];
      mocks.worksheet.groupings = [makeAssembly()] as any;

      expect(comp.isWorksheetEmpty()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 10: startEditName [Risk 1]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — startEditName", () => {

   it("should call worksheet.selectOnlyAssembly with the assembly", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "EditMe" });
      const spy = vi.spyOn(mocks.worksheet, "selectOnlyAssembly");

      comp.startEditName(assembly);

      expect(spy).toHaveBeenCalledWith(assembly);
   });
});

// ---------------------------------------------------------------------------
// Group 11: tableEndpoints [Risk 1]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — tableEndpoints", () => {

   it("should return the real TABLE_ENDPOINTS array from the config", () => {
      const { comp } = makeComponent();

      // TABLE_ENDPOINTS is the real export: [top, bottom, left, right] — 4 endpoint configs
      expect(Array.isArray(comp.tableEndpoints)).toBe(true);
      expect(comp.tableEndpoints.length).toBe(4);
   });
});

// ---------------------------------------------------------------------------
// Group 12: selectDependent [Risk 1]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — selectDependent", () => {

   // 🔁 Regression-sensitive: selectDependent must walk the dependency graph in both
   //    directions (assemblies this one depends on AND assemblies that depend on this
   //    one).  Missing either direction leaves orphan nodes un-highlighted.
   it("should select assemblies that the focused assembly depends on (dependeds)", () => {
      const { comp, mocks } = makeComponent();

      const depAssembly = makeAssembly({ name: "Dep", dependeds: [], dependings: [] });
      const focusedAssembly = makeAssembly({
         name: "Focused",
         dependeds: [{ assemblyName: "Dep", types: [] }],
         dependings: [],
      });

      mocks.worksheet.tables = [focusedAssembly, depAssembly] as any;
      mocks.worksheet.currentFocusedAssemblies = [focusedAssembly];

      const selectSpy = vi.spyOn(mocks.worksheet, "selectAssembly");

      comp.selectDependent();

      expect(selectSpy).toHaveBeenCalledWith(depAssembly);
   });

   it("should select assemblies that depend on the focused assembly (dependings via dependeds)", () => {
      const { comp, mocks } = makeComponent();

      const focusedAssembly = makeAssembly({ name: "Source", dependeds: [], dependings: [] });
      const dependingAssembly = makeAssembly({
         name: "Consumer",
         dependeds: [{ assemblyName: "Source", types: [] }],
         dependings: ["Source"],
      });

      mocks.worksheet.tables = [focusedAssembly, dependingAssembly] as any;
      mocks.worksheet.currentFocusedAssemblies = [focusedAssembly];

      const selectSpy = vi.spyOn(mocks.worksheet, "selectAssembly");

      comp.selectDependent();

      expect(selectSpy).toHaveBeenCalledWith(dependingAssembly);
   });

   it("should not select anything when focused assembly has no dependencies", () => {
      const { comp, mocks } = makeComponent();

      const isolated = makeAssembly({ name: "Isolated", dependeds: [], dependings: [] });
      mocks.worksheet.tables = [isolated] as any;
      mocks.worksheet.currentFocusedAssemblies = [isolated];

      const selectSpy = vi.spyOn(mocks.worksheet, "selectAssembly");

      comp.selectDependent();

      expect(selectSpy).not.toHaveBeenCalled();
   });
});
