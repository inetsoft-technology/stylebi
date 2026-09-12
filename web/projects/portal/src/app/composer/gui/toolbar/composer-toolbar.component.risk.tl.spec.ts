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
 * ComposerToolbarComponent — Pass 2: Risk
 *
 * Risk-first coverage (items not addressed in Pass 1):
 *   Group 1  [Risk 3]  — getPreviewActions[0].visible() (Bug #21103): preview/refresh
 *                         actions must not appear for worksheet / script / tableStyle tabs;
 *                         wrong visible() allows users to click preview on a worksheet,
 *                         sending an invalid event the server rejects silently
 *   Group 2  [Risk 2]  — paste action enabled() full gate: showPaste × isPreview ×
 *                         layoutShowing × pasteEnabled(); all four conditions must be true
 *                         (Bug #17173 partial; pasteEnabled tested in Pass 1)
 *   Group 3  [Risk 2]  — cut/copy action disabled in preview mode (Bugs #18596/#18602)
 *                         and in layout mode; isObjectSelected also required
 *   Group 4  [Risk 2]  — worksheetOperationsDisabled: disabled unless isInitializedWorksheet
 *                         AND not a compositeView; wrong flag enables Add/VPM/Layout buttons
 *                         on uninitialized or read-only composite worksheets
 *   Group 5  [Risk 2]  — loadDataSources: called on ngOnInit; getModel called for both
 *                         tabularDataSourceTypes and databaseDataSources endpoints;
 *                         results stored in the correct component properties
 *   Group 6  [baseline] — alignObjects in layout mode: uses currentLayout.focusedObjects
 *                          instead of currentFocusedAssemblies (old spec test 2 migration)
 *   Group 7  [baseline] — enableLayoutAlign() aggregates three enabled flags;
 *                          driven through the "Arrange" button
 *
 * Old spec coverage map (composer-toolbar.component.spec.ts):
 *   1. "worksheet toolbar status check"        → Group 1 test 1 (Bug #21103)
 *   2. "enable layout align on layouts"        → Group 6 / 7 (Bug #17208)
 *   3. "enable paste when select assemblies"   → Group 2 (Bug #17173)
 *   4. "check button status on layout"         → Group 3 (Bugs #18596/#18602)
 *   5. "check layout align icon status"        → Pass 1 Group 3 ✅
 *   6. "check Resize Max Width"                → Pass 1 Group 4 ✅
 *
 * HTTP: all test groups mock modelService.getModel so no real HTTP goes out; no MSW
 *   handlers needed (the mock bypasses HttpClient entirely)
 *
 * Out of scope this pass: fireMoveResize coordinate math, layoutAlign/Distribute coordinate
 *   math, save debounce timer, zoom boundary, enterParameters dialog flow (Pass 3 / dedicated
 *   pass); addDropdownListeners async (Pass 3)
 */

import "@angular/compiler";
import { of } from "rxjs";
import { Viewsheet } from "../../data/vs/viewsheet";
import { Worksheet } from "../../data/ws/worksheet";
import { VSLayoutModel } from "../../data/vs/vs-layout-model";
import { VSLayoutObjectModel } from "../../data/vs/vs-layout-object-model";
import { ComposerTabModel } from "../composer-tab-model";
import { ComposerToolbarComponent } from "./composer-toolbar.component";
import { TestUtils } from "../../../common/test/test-utils";
import { makeMocks, renderComponent } from "./composer-toolbar.spec-helpers";

// ---------------------------------------------------------------------------
// Global setup
// ---------------------------------------------------------------------------

beforeAll(() => {
   (window as any).BroadcastChannel = (window as any).BroadcastChannel ?? class {
      onmessage: any = null;
      postMessage() {} close() {}
      addEventListener() {} removeEventListener() {}
   };
});

// Load-bearing fields only:
//   objectModel.objectType — alignObjects filters out "VSPageBreak"
//   container / locked    — layoutAlignEnabled / layoutDistributeEnabled / layoutResizeEnabled
//                           count objects where !container && locked !== true
function makeLayoutObject(overrides: Partial<VSLayoutObjectModel> = {}): VSLayoutObjectModel {
   return { objectModel: { objectType: "VSText" } as any, ...overrides } as VSLayoutObjectModel;
}

function makeLayout(focusedObjects: VSLayoutObjectModel[] = []): VSLayoutModel {
   return Object.assign(new VSLayoutModel(), {
      name: "TestLayout",
      objects: null,
      selectedObjects: [],
      printLayout: false,
      guideType: 0,
      currentPrintSection: 1,
      unit: "",
      width: 1024,
      height: 768,
      marginTop: 0, marginLeft: 0, marginRight: 0, marginBottom: 0,
      headerFromEdge: 0,
      footerFromEdge: 0,
      headerObjects: [],
      footerObjects: [],
      horizontal: false,
      runtimeID: "layout1",
      socketConnection: null,
      focusedObjects,
      focusedObjectsSubject: null,
   });
}

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1: getPreviewActions[0].visible() (Risk 3 — Bug #21103)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — getPreviewActions.visible(): hidden for non-viewsheet tabs", () => {
   // 🔁 Regression-sensitive: the preview/refresh actions must never appear for worksheet,
   // script, or tableStyle tabs. Clicking preview on a worksheet sends a viewsheet-specific
   // event that the server rejects without user-visible feedback.
   it("should show preview action for a viewsheet tab", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      comp.sheet = vs;

      const visibleFn = comp.getPreviewActions[0].visible;
      expect(visibleFn!()).toBe(true);
   });

   it("should hide preview action when the focused sheet is a worksheet (Bug #21103)", async () => {
      const { comp } = await renderComponent();
      comp.sheet = new Worksheet();
      comp.focusedTab = new ComposerTabModel("worksheet", comp.sheet);

      const visibleFn = comp.getPreviewActions[0].visible;
      expect(visibleFn!()).toBe(false);
   });

   it("should hide preview action when focused tab is a script asset", async () => {
      const { comp } = await renderComponent();
      comp.focusedTab = new ComposerTabModel("script", { type: "script" } as any);

      const visibleFn = comp.getPreviewActions[0].visible;
      expect(visibleFn!()).toBe(false);
   });

   it("should hide preview action when focused tab is a table style asset", async () => {
      const { comp } = await renderComponent();
      comp.focusedTab = new ComposerTabModel("tableStyle", { type: "tableStyle" } as any);

      const visibleFn = comp.getPreviewActions[0].visible;
      expect(visibleFn!()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2: paste action enabled() gate (Risk 2 — Bug #17173)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — paste action enabled(): full gate conditions", () => {
   // 🔁 Regression-sensitive: paste enabled requires ALL of:
   //   sheet != null, !isPreview, showPaste == true, !layoutShowing, pasteEnabled() == true.
   // A regression in any condition would allow pasting into a read-only context.
   function getPasteEnabled(comp: ComposerToolbarComponent): boolean {
      return comp.getEditActions.find(a => a.buttonClass === "paste-button")!.enabled!();
   }

   it("should disable paste when showPaste is false (no clipboard content)", async () => {
      const { comp } = await renderComponent({ showPaste: false });
      const vs = new Viewsheet();
      comp.sheet = vs;

      expect(getPasteEnabled(comp)).toBe(false);
   });

   it("should disable paste when isPreview is true even if clipboard has content", async () => {
      const { comp } = await renderComponent({ showPaste: true });
      const vs = new Viewsheet();
      vs.preview = true;
      comp.sheet = vs;

      expect(getPasteEnabled(comp)).toBe(false);
   });

   it("should disable paste when layoutShowing (device layout active)", async () => {
      const { comp } = await renderComponent({ showPaste: true });
      const vs = new Viewsheet();
      (vs as any).currentLayout = makeLayout();
      comp.sheet = vs;

      expect(getPasteEnabled(comp)).toBe(false);
   });

   it("should enable paste for a normal viewsheet when showPaste=true", async () => {
      const { comp } = await renderComponent({ showPaste: true });
      const vs = new Viewsheet();
      comp.sheet = vs;

      expect(getPasteEnabled(comp)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 3: cut/copy action disabled in preview and layout modes (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — cut/copy action enabled(): disabled in preview/layout", () => {
   // 🔁 Regression-sensitive: cut/copy must be blocked in preview mode (Bugs #18596/#18602)
   // and in layout mode; allowing cut in these modes sends server events that are ignored
   // without feedback and could corrupt object positions.
   function getCutEnabled(comp: ComposerToolbarComponent): boolean {
      return comp.getEditActions.find(a => a.buttonClass === "cut-button")!.enabled!();
   }

   function getCopyEnabled(comp: ComposerToolbarComponent): boolean {
      return comp.getEditActions.find(a => a.buttonClass === "copy-button")!.enabled!();
   }

   it("should disable cut when isPreview is true (Bug #18596)", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.preview = true;
      vs.currentFocusedAssemblies = [TestUtils.createMockVSSelectionListModel("L1")];
      comp.sheet = vs;

      expect(getCutEnabled(comp)).toBe(false);
   });

   it("should disable copy when isPreview is true (Bug #18602)", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.preview = true;
      vs.currentFocusedAssemblies = [TestUtils.createMockVSSelectionListModel("L1")];
      comp.sheet = vs;

      expect(getCopyEnabled(comp)).toBe(false);
   });

   it("should disable cut when in layout mode (layoutShowing=true)", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.currentFocusedAssemblies = [TestUtils.createMockVSSelectionListModel("L1")];
      (vs as any).currentLayout = makeLayout();
      comp.sheet = vs;

      expect(getCutEnabled(comp)).toBe(false);
   });

   it("should enable cut for a normal viewsheet with selected objects", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.currentFocusedAssemblies = [TestUtils.createMockVSSelectionListModel("L1")];
      comp.sheet = vs;

      expect(getCutEnabled(comp)).toBe(true);
   });

   it("should disable cut when no object is selected even if viewsheet is in edit mode", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.currentFocusedAssemblies = [];
      comp.sheet = vs;

      expect(getCutEnabled(comp)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4: worksheetOperationsDisabled (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — worksheetOperationsDisabled: gate for ws operations", () => {
   // 🔁 Regression-sensitive: Add/VPM/Layout buttons are gated by worksheetOperationsDisabled;
   // showing them for uninitialized or composite-view worksheets sends invalid server events.
   it("should return false for an initialized non-composite worksheet", async () => {
      const { comp } = await renderComponent();
      const ws = new Worksheet();
      (ws as any).init = true;
      vi.spyOn(ws, "isCompositeView").mockReturnValue(false);
      comp.sheet = ws;

      expect(comp.worksheetOperationsDisabled).toBe(false);
   });

   it("should return true for a composite-view worksheet (read-only, no operations)", async () => {
      const { comp } = await renderComponent();
      const ws = new Worksheet();
      (ws as any).init = true;
      vi.spyOn(ws, "isCompositeView").mockReturnValue(true);
      comp.sheet = ws;

      expect(comp.worksheetOperationsDisabled).toBe(true);
   });

   it("should return true when worksheet is not yet initialized", async () => {
      const { comp } = await renderComponent();
      const ws = new Worksheet();
      (ws as any).init = false;
      comp.sheet = ws;

      expect(comp.worksheetOperationsDisabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5: loadDataSources HTTP behavior (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — loadDataSources: sets tabular and database sources", () => {
   // 🔁 Regression-sensitive: the worksheet "Add" dropdown depends on tabularDataSourceTypes
   // and databaseDataSources being populated; wrong URIs or missing subscriptions would
   // show an empty dropdown silently on first open.
   it("should call getModel for tabularDataSourceTypes and databaseDataSources on ngOnInit", async () => {
      const mocks = makeMocks();
      await renderComponent({}, mocks);

      expect(mocks.modelService.getModel).toHaveBeenCalledWith("../api/composer/tabularDataSourceTypes");
      expect(mocks.modelService.getModel).toHaveBeenCalledWith("../api/composer/databaseDataSources");
   });

   it("should populate tabularDataSourceTypes from API response", async () => {
      const tabularTypes = [{ label: "REST", dataSource: "rest" }];
      const mocks = makeMocks();
      (mocks.modelService as any).getModel = vi.fn((url: string) => {
         if(url.includes("tabularDataSourceTypes")) return of(tabularTypes);
         return of({ dataSources: [] });
      });

      const { comp } = await renderComponent({}, mocks);

      expect(comp.tabularDataSourceTypes).toEqual(tabularTypes);
   });

   it("should populate databaseDataSources from API response", async () => {
      const dbSources = [{ dataSource: "MySQL", label: "MySQL" }];
      const mocks = makeMocks();
      (mocks.modelService as any).getModel = vi.fn((url: string) => {
         if(url.includes("databaseDataSources")) return of({ dataSources: dbSources });
         return of([]);
      });

      const { comp } = await renderComponent({}, mocks);

      expect(comp.databaseDataSources).toEqual(dbSources);
   });
});

// ---------------------------------------------------------------------------
// Group 6: alignObjects in layout mode (baseline — Bug #17208)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — alignObjects: uses currentLayout.focusedObjects in layout mode", () => {
   // 🔁 Regression-sensitive: in layout mode, alignObjects must use layout.focusedObjects;
   // using currentFocusedAssemblies instead would align the wrong objects (Bug #17208).
   it("should use layout focusedObjects for alignObjects when currentLayout is set", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      const layoutObj1 = makeLayoutObject({ name: "obj1" });
      const layoutObj2 = makeLayoutObject({ name: "obj2" });
      (vs as any).currentLayout = makeLayout([layoutObj1, layoutObj2]);
      vs.currentFocusedAssemblies = [];  // vs assemblies should be ignored
      comp.sheet = vs;

      expect(comp.alignObjects).toHaveLength(2);
      expect(comp.alignObjects[0].name).toBe("obj1");
      expect(comp.alignObjects[1].name).toBe("obj2");
   });

   it("should ignore VSPageBreak objects in layout mode", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      const layoutObj = makeLayoutObject({ name: "obj1" });
      const pageBreak = makeLayoutObject({ name: "pb1" });
      (pageBreak.objectModel as any).objectType = "VSPageBreak";
      (vs as any).currentLayout = makeLayout([layoutObj, pageBreak]);
      comp.sheet = vs;

      expect(comp.alignObjects).toHaveLength(1);
      expect(comp.alignObjects[0].name).toBe("obj1");
   });

   it("should return [] when currentLayout.focusedObjects is null", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      (vs as any).currentLayout = makeLayout(null as any);
      comp.sheet = vs;

      expect(comp.alignObjects).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 7: enableLayoutAlign() (baseline)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — enableLayoutAlign(): 'Arrange' button gate", () => {
   it("should return true when layoutAlignEnabled is true", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.currentFocusedAssemblies = [
         TestUtils.createMockVSSelectionListModel("L1"),
         TestUtils.createMockVSOvalModel("O1"),
      ];
      comp.sheet = vs;

      expect(comp.enableLayoutAlign()).toBe(true);
   });

   it("should return true when layoutDistributeEnabled is true (>2 objects)", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.currentFocusedAssemblies = [
         TestUtils.createMockVSSelectionListModel("L1"),
         TestUtils.createMockVSOvalModel("O1"),
         TestUtils.createMockVSRadioButtonModel("R1"),
      ];
      comp.sheet = vs;

      expect(comp.enableLayoutAlign()).toBe(true);
   });

   it("should return false when no objects are selected", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.currentFocusedAssemblies = [];
      comp.sheet = vs;

      expect(comp.enableLayoutAlign()).toBe(false);
   });

   it("should return false when only 1 object is selected", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.currentFocusedAssemblies = [TestUtils.createMockVSSelectionListModel("L1")];
      comp.sheet = vs;

      expect(comp.enableLayoutAlign()).toBe(false);
   });
});
