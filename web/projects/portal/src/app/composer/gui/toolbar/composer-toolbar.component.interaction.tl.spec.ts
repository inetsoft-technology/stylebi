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
 * ComposerToolbarComponent — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3]  — save0: routing emits correct @Output per focusedTab.type × newSheet;
 *                         wrong emit causes silent save-as/save/close misrouting
 *   Group 2  [Risk 2]  — isPreview: viewsheet.preview / viewsheet.linkview both set flag;
 *                         flag gates cut/copy/paste/undo/format-painter enabled states
 *   Group 3  [Risk 2]  — layoutAlignEnabled / layoutDistributeEnabled / layoutResizeEnabled:
 *                         locked or container objects excluded from count (Bug #20636);
 *                         ≥2 non-locked/container → align; >2 → distribute (Bug #19212/#20796)
 *   Group 4  [Risk 2]  — layoutResize RESIZE_MAX_W: all objects resized to widest (Bug #20112)
 *   Group 5  [Risk 2]  — undoEnabled / redoEnabled: boundary at current==0 and current==points-1
 *   Group 6  [Risk 2]  — concatEnabled: requires ≥2 TableAssembly with identical column count;
 *                         joinEnabled: requires ≥2 TableAssembly (column count irrelevant)
 *   Group 7  [baseline] — type flags: isWorksheet / isViewsheet / isScript / isTableStyle /
 *                          isObjectSelected / layoutShowing / isPrintLayout / isInitializedWorksheet
 *   Group 8  [baseline] — simple emitters: cut / copy / paste / snapToGridChanged /
 *                          snapToObjectsChanged / openViewsheetWizard / newWorksheet / refresh
 *   Group 9  [baseline] — pasteEnabled: worksheet isCompositeView blocks paste (Bug #17173)
 *
 * Confirmed bugs (it.fails): none in this pass
 *
 * Suspected bugs (header only):
 *   Suspicion A — layoutAlign TOP/BOTTOM/MIDDLE/LEFT/CENTER/RIGHT: the first selected object
 *     is the reference. If the first object is a container child, it is excluded by the filter
 *     but the reference baseline is still taken from selectedObjects[0] (post-filter), which
 *     is fine. However if selectedObjects is empty, firstSelected is undefined → runtime error.
 *
 * HTTP: N/A — all methods tested via direct property / method calls; no HTTP in Pass 1 paths
 *
 * Out of scope this pass: loadDataSources, ngAfterViewInit, addDropdownListeners,
 *   fireMoveResize, layoutAlign (coordinate math), layoutDistribute, enterParameters,
 *   newEmbeddedTable, newDatabaseQuery, newVariable, newGrouping, newTabularQuery (Pass 2);
 *   enableFormatPainter, layoutShowing detail, hiddenComposerIcon, getDatabaseLabel,
 *   getTabularLabel (Pass 3)
 */

import "@angular/compiler";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { NgbModal, NgbTooltipConfig } from "@ng-bootstrap/ng-bootstrap";
import { of, Subject } from "rxjs";

import { ModelService } from "../../../widget/services/model.service";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { FullScreenService } from "../../../common/services/full-screen.service";
import { ComposerToolbarService } from "../composer-toolbar.service";
import { EventQueueService } from "../vs/event-queue.service";
import { DropdownObserver } from "../../../widget/services/dropdown-observer.service";
import { ChatService } from "../../../common/chat/chat.service";
import { AiAssistantDialogService } from "../../../common/services/ai-assistant-dialog.service";
import { Viewsheet } from "../../data/vs/viewsheet";
import { Worksheet } from "../../data/ws/worksheet";
import { ComposerTabModel } from "../composer-tab-model";
import { TestUtils } from "../../../common/test/test-utils";
import { ComposerToolbarComponent } from "./composer-toolbar.component";

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

function makeMocks() {
   return {
      modelService: {
         getModel: vi.fn(() => of([])),
         sendModel: vi.fn(() => of({ body: null })),
         errorHandler: null as any,
      },
      modalService: {
         open: vi.fn().mockReturnValue({
            result: new Promise<never>((_, reject) => reject("cancel")),
            componentInstance: {
               onCommit: { subscribe: vi.fn(() => ({ unsubscribe: vi.fn() })) },
               onCancel: { subscribe: vi.fn(() => ({ unsubscribe: vi.fn() })) },
            },
         }),
      },
      fullScreenService: {
         fullScreenChange: new Subject<void>(),
         fullScreenMode: false,
         enterFullScreen: vi.fn(),
         exitFullScreen: vi.fn(),
      },
      composerToolbarService: {
         jdbcExists: true,
         sqlEnabled: true,
         crossJoinEnabled: false,
      },
      eventQueueService: { addResizeEvent: vi.fn() },
      scaleService: {
         getScale: vi.fn(() => of(1)),
         setScale: vi.fn(),
      },
      chatService: {
         isChatOngoing: vi.fn(() => false),
         openSession: vi.fn(),
      },
      dropdownObserver: {
         onDropdownOpened: vi.fn(),
         onDropdownClosed: vi.fn(),
      },
   };
}

async function renderComponent(
   componentProperties: Record<string, any> = {},
   mocks = makeMocks()
) {
   const defaultVs = new Viewsheet();
   defaultVs.localId = 1;

   const result = await render(ComposerToolbarComponent, {
      componentProperties: {
         focusedTab: new ComposerTabModel("viewsheet", defaultVs),
         ...componentProperties,
      },
      componentImports: [],
      componentProviders: [
         { provide: FullScreenService, useValue: mocks.fullScreenService },
         { provide: NgbTooltipConfig, useValue: {} },
      ],
      providers: [
         { provide: ModelService, useValue: mocks.modelService },
         { provide: NgbModal, useValue: mocks.modalService },
         { provide: EventQueueService, useValue: mocks.eventQueueService },
         { provide: ComposerToolbarService, useValue: mocks.composerToolbarService },
         { provide: ScaleService, useValue: mocks.scaleService },
         { provide: ChatService, useValue: mocks.chatService },
         { provide: DropdownObserver, useValue: mocks.dropdownObserver },
         { provide: AiAssistantDialogService, useValue: {} },
         provideHttpClient(),
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   const comp = result.fixture.componentInstance as ComposerToolbarComponent;
   return { fixture: result.fixture, comp, mocks };
}

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1: save0 routing (Risk 3)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — save0: @Output routing", () => {
   // 🔁 Regression-sensitive: save0 routes to four separate @Outputs; wrong branch causes the
   // server to receive the wrong event (save vs. save-as vs. save-and-close).
   it("should emit onSaveViewsheetAs when focused on a new viewsheet", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.newSheet = true;
      const tab = new ComposerTabModel("viewsheet", vs);
      comp.focusedTab = tab;
      comp.sheet = vs;
      const spy = vi.fn();
      comp.onSaveViewsheetAs.subscribe(spy);

      (comp as any).save0();

      expect(spy).toHaveBeenCalledWith(vs);
   });

   it("should emit onSaveViewsheet when focused on an existing viewsheet", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.newSheet = false;
      const tab = new ComposerTabModel("viewsheet", vs);
      comp.focusedTab = tab;
      comp.sheet = vs;
      const spy = vi.fn();
      comp.onSaveViewsheet.subscribe(spy);

      (comp as any).save0();

      expect(spy).toHaveBeenCalledWith(vs);
   });

   it("should emit onSaveWorksheetAs when focused on a new worksheet", async () => {
      const { comp } = await renderComponent();
      const ws = new Worksheet();
      ws.newSheet = true;
      const tab = new ComposerTabModel("worksheet", ws);
      comp.focusedTab = tab;
      comp.sheet = ws;
      const spy = vi.fn();
      comp.onSaveWorksheetAs.subscribe(spy);

      (comp as any).save0();

      expect(spy).toHaveBeenCalledWith(ws);
   });

   it("should emit onSaveWorksheet when focused on an existing worksheet", async () => {
      const { comp } = await renderComponent();
      const ws = new Worksheet();
      ws.newSheet = false;
      const tab = new ComposerTabModel("worksheet", ws);
      comp.focusedTab = tab;
      comp.sheet = ws;
      const spy = vi.fn();
      comp.onSaveWorksheet.subscribe(spy);

      (comp as any).save0();

      expect(spy).toHaveBeenCalledWith(ws);
   });
});

// ---------------------------------------------------------------------------
// Group 2: isPreview flag (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — isPreview: gates cut/copy/paste/undo enabled", () => {
   // 🔁 Regression-sensitive: isPreview controls disabled state for cut, copy, paste, undo,
   // formatPainter; if either preview or linkview is missed those buttons activate incorrectly.
   it("should be true when viewsheet.preview is true (Bug #18462)", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.preview = true;
      comp.sheet = vs;

      expect(comp.isPreview).toBe(true);
   });

   it("should be true when viewsheet.linkview is true", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.linkview = true;
      comp.sheet = vs;

      expect(comp.isPreview).toBe(true);
   });

   it("should be false for a normal editable viewsheet", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      comp.sheet = vs;

      expect(comp.isPreview).toBe(false);
   });

   it("should be false for a worksheet (Bug #21103 preview-button guard)", async () => {
      const { comp } = await renderComponent();
      comp.sheet = new Worksheet();

      expect(comp.isPreview).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: layout alignment gates (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — layoutAlignEnabled/DistributeEnabled/ResizeEnabled", () => {
   // 🔁 Regression-sensitive: locked objects must be excluded; treating locked objects as
   // valid targets causes the server to reject the alignment event silently (Bug #20636).
   it("should enable layoutAlignEnabled when ≥2 non-locked non-container objects selected (Bug #17208)", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      const list = TestUtils.createMockVSSelectionListModel("list1");
      const oval = TestUtils.createMockVSOvalModel("oval1");
      vs.currentFocusedAssemblies = [list, oval];
      comp.sheet = vs;

      expect(comp.layoutAlignEnabled).toBe(true);
   });

   it("should disable layoutAlignEnabled when any non-container object is locked (Bug #20636)", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      const list = TestUtils.createMockVSSelectionListModel("list1");
      const oval = TestUtils.createMockVSOvalModel("oval1");
      (oval as any).locked = true;
      vs.currentFocusedAssemblies = [list, oval];
      comp.sheet = vs;

      expect(comp.layoutAlignEnabled).toBe(false);
   });

   // 🔁 Regression-sensitive: container objects must not count toward the threshold; a group
   // container's children are already captured separately (Bug #19212).
   it("should enable layoutDistributeEnabled only when >2 non-container non-locked objects", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      const list = TestUtils.createMockVSSelectionListModel("list1");
      const radio = TestUtils.createMockVSRadioButtonModel("radio1");
      const oval = TestUtils.createMockVSOvalModel("oval1");
      const group = TestUtils.createMockVSGroupContainerModel("group1");
      vs.currentFocusedAssemblies = [list, radio, oval, group];
      comp.sheet = vs;

      expect(comp.layoutDistributeEnabled).toBe(true);
   });

   it("should disable layoutDistributeEnabled when ≤2 non-container objects (Bug #19212)", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      const list = TestUtils.createMockVSSelectionListModel("list1");
      const group = TestUtils.createMockVSGroupContainerModel("group1");
      const radio = TestUtils.createMockVSRadioButtonModel("radio1");
      radio.container = "group1";
      radio.containerType = "VSGroupContainer";
      vs.currentFocusedAssemblies = [list, group, radio];
      comp.sheet = vs;

      expect(comp.layoutDistributeEnabled).toBe(false);
   });

   it("should enable layoutResizeEnabled when ≥2 non-locked non-container objects (Bug #20796)", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      const list = TestUtils.createMockVSSelectionListModel("list1");
      const oval = TestUtils.createMockVSOvalModel("oval1");
      vs.currentFocusedAssemblies = [list, oval];
      comp.sheet = vs;

      expect(comp.layoutResizeEnabled).toBe(true);
   });

   it("should disable layoutResizeEnabled when a locked object is present (Bug #20636)", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      const list = TestUtils.createMockVSSelectionListModel("list1");
      const oval = TestUtils.createMockVSOvalModel("oval1");
      (oval as any).locked = true;
      vs.currentFocusedAssemblies = [list, oval];
      comp.sheet = vs;

      expect(comp.layoutResizeEnabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4: layoutResize RESIZE_MAX_W (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — layoutResize: resize to max width (Bug #20112)", () => {
   // 🔁 Regression-sensitive: RESIZE_MAX_W must set ALL selected objects to the widest value;
   // if min/max are confused the narrowest object widens while the widest shrinks.
   it("should resize all selected objects to the maximum width", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.localId = 1;
      (vs as any).socketConnection = { sendEvent: vi.fn() };
      const list1 = TestUtils.createMockVSSelectionListModel("list1");
      const list2 = TestUtils.createMockVSSelectionListModel("list2");

      list1.objectFormat.width = 199;
      list1.objectFormat.height = 108;
      list1.objectFormat.left = 209;
      list1.objectFormat.top = 132;
      list2.objectFormat.width = 276;
      list2.objectFormat.height = 108;
      list2.objectFormat.left = 522;
      list2.objectFormat.top = 121;

      vs.currentFocusedAssemblies = [list1, list2];
      comp.sheet = vs;

      comp.layoutResize(9 /* RESIZE_MAX_W */);

      expect(list1.objectFormat.width).toBe(276);
      expect(list2.objectFormat.width).toBe(276);
   });
});

// ---------------------------------------------------------------------------
// Group 5: undoEnabled / redoEnabled (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — undoEnabled / redoEnabled", () => {
   // 🔁 Regression-sensitive: undo/redo buttons must be disabled at the stack boundaries;
   // enabling undo at current==0 sends an event the server ignores without feedback.
   // Note: focusedTab must be set BEFORE sheet (or left as the default viewsheet tab from
   // renderComponent) to avoid zone.js scheduling a post-assignment change detection that
   // triggers snappingDropdown.isOpen() on a non-NgbDropdown element.
   it("should enable undoEnabled when sheet.current > 0", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.current = 2;
      comp.sheet = vs;

      expect(comp.undoEnabled).toBe(true);
   });

   it("should disable undoEnabled when sheet.current == 0", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.current = 0;
      comp.sheet = vs;

      expect(comp.undoEnabled).toBe(false);
   });

   it("should enable redoEnabled when current < points - 1", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.current = 0;
      vs.points = 3;
      comp.sheet = vs;

      expect(comp.redoEnabled).toBe(true);
   });

   it("should disable redoEnabled when current == points - 1", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.current = 2;
      vs.points = 3;
      comp.sheet = vs;

      expect(comp.redoEnabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 6: concatEnabled / joinEnabled (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — concatEnabled / joinEnabled", () => {
   function makeWorksheet(assemblies: any[]): Worksheet {
      const ws = new Worksheet();
      (ws as any).init = true;
      ws.currentFocusedAssemblies = assemblies;
      return ws;
   }

   function makeTable(name: string, cols: number) {
      return {
         name,
         classType: "TableAssembly",
         getPublicColumnSelection: vi.fn(() => Array(cols).fill({})),
         objectFormat: { left: 0, top: 0, width: 100, height: 50 },
      } as any;
   }

   // 🔁 Regression-sensitive: concatEnabled requires identical column count; mismatched tables
   // would generate an invalid event that the server rejects without error.
   it("should enable concatEnabled when ≥2 TableAssemblies with equal column counts", async () => {
      const { comp } = await renderComponent();
      comp.sheet = makeWorksheet([makeTable("T1", 3), makeTable("T2", 3)]);

      expect(comp.concatEnabled()).toBe(true);
   });

   it("should disable concatEnabled when tables have different column counts", async () => {
      const { comp } = await renderComponent();
      comp.sheet = makeWorksheet([makeTable("T1", 3), makeTable("T2", 4)]);

      expect(comp.concatEnabled()).toBe(false);
   });

   it("should disable concatEnabled with only 1 table selected", async () => {
      const { comp } = await renderComponent();
      comp.sheet = makeWorksheet([makeTable("T1", 3)]);

      expect(comp.concatEnabled()).toBe(false);
   });

   it("should enable joinEnabled when ≥2 TableAssemblies are selected", async () => {
      const { comp } = await renderComponent();
      comp.sheet = makeWorksheet([makeTable("T1", 3), makeTable("T2", 5)]);

      expect(comp.joinEnabled()).toBe(true);
   });

   it("should disable joinEnabled when a non-TableAssembly is in the selection", async () => {
      const { comp } = await renderComponent();
      const notATable = { name: "V1", classType: "EmbeddedTableAssembly" } as any;
      comp.sheet = makeWorksheet([makeTable("T1", 3), notATable]);

      expect(comp.joinEnabled()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 7: type flags and compound getters — baseline
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — type detection flags", () => {
   it("should report isWorksheet=true for a Worksheet sheet", async () => {
      const { comp } = await renderComponent();
      comp.sheet = new Worksheet();
      expect(comp.isWorksheet).toBe(true);
   });

   it("should report isViewsheet=true for a Viewsheet sheet", async () => {
      const { comp } = await renderComponent();
      comp.sheet = new Viewsheet();
      expect(comp.isViewsheet).toBe(true);
   });

   it("should report isScript=true when focusedTab.type is 'script'", async () => {
      const { comp } = await renderComponent();
      comp.focusedTab = new ComposerTabModel("script", { type: "script" } as any);
      expect(comp.isScript).toBe(true);
   });

   it("should report isTableStyle=true when focusedTab.type is 'tableStyle'", async () => {
      const { comp } = await renderComponent();
      comp.focusedTab = new ComposerTabModel("tableStyle", { type: "tableStyle" } as any);
      expect(comp.isTableStyle).toBe(true);
   });

   it("should report isObjectSelected=true when focusedObjects is non-empty", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.currentFocusedAssemblies = [TestUtils.createMockVSSelectionListModel("L1")];
      comp.sheet = vs;
      expect(comp.isObjectSelected).toBe(true);
   });

   it("should report layoutShowing=true when viewsheet has a currentLayout", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      (vs as any).currentLayout = { name: "DeviceLayout", printLayout: false };
      comp.sheet = vs;
      expect(comp.layoutShowing).toBe(true);
   });

   it("should report isPrintLayout=true when currentLayout.printLayout is true", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      (vs as any).currentLayout = { name: "PrintLayout", printLayout: true };
      comp.sheet = vs;
      expect(comp.isPrintLayout).toBe(true);
   });

   it("should report isInitializedWorksheet=true for worksheet with init=true", async () => {
      const { comp } = await renderComponent();
      const ws = new Worksheet();
      (ws as any).init = true;
      comp.sheet = ws;
      expect(comp.isInitializedWorksheet()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 8: simple emitters — baseline
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — simple @Output emitters", () => {
   it("should emit onCut when cut() is called", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      comp.sheet = vs;
      const spy = vi.fn();
      comp.onCut.subscribe(spy);

      comp.cut();

      expect(spy).toHaveBeenCalledWith(vs);
   });

   it("should emit onCopy when copy() is called", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      comp.sheet = vs;
      const spy = vi.fn();
      comp.onCopy.subscribe(spy);

      comp.copy();

      expect(spy).toHaveBeenCalledWith(vs);
   });

   it("should emit onPaste when paste() is called", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      comp.sheet = vs;
      const spy = vi.fn();
      comp.onPaste.subscribe(spy);

      comp.paste();

      expect(spy).toHaveBeenCalledWith(vs);
   });

   it("should emit onToggleSnapToGrid with current snapToGrid value when snapToGridChanged()", async () => {
      const { comp } = await renderComponent({ snapToGrid: true });
      const spy = vi.fn();
      comp.onToggleSnapToGrid.subscribe(spy);

      comp.snapToGridChanged();

      expect(spy).toHaveBeenCalledWith(true);
   });

   it("should emit onToggleSnapToObjects with current value when snapToObjectsChanged()", async () => {
      const { comp } = await renderComponent({ snapToObjects: false });
      const spy = vi.fn();
      comp.onToggleSnapToObjects.subscribe(spy);

      comp.snapToObjectsChanged();

      expect(spy).toHaveBeenCalledWith(false);
   });

   it("should emit onOpenViewsheetWizard when openViewsheetWizard() is called", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onOpenViewsheetWizard.subscribe(spy);

      comp.openViewsheetWizard();

      expect(spy).toHaveBeenCalled();
   });

   it("should emit onNewWorksheet when newWorksheet() is called", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onNewWorksheet.subscribe(spy);

      comp.newWorksheet();

      expect(spy).toHaveBeenCalledWith(true);
   });

   it("should emit onRefreshViewsheet when refresh() is called", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      comp.sheet = vs;
      const spy = vi.fn();
      comp.onRefreshViewsheet.subscribe(spy);

      comp.refresh();

      expect(spy).toHaveBeenCalledWith(vs);
   });
});

// ---------------------------------------------------------------------------
// Group 9: pasteEnabled — worksheet composite view guard (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — pasteEnabled: worksheet composite view (Bug #17173)", () => {
   // 🔁 Regression-sensitive: paste must be blocked for composite-view worksheets; pasting into
   // a composite view causes the server to throw an error since the target is read-only.
   it("should return true for a viewsheet (paste always allowed)", async () => {
      const { comp } = await renderComponent();
      comp.sheet = new Viewsheet();
      expect(comp.pasteEnabled()).toBe(true);
   });

   it("should return true for a regular worksheet (not composite view)", async () => {
      const { comp } = await renderComponent();
      const ws = new Worksheet();
      vi.spyOn(ws, "isCompositeView").mockReturnValue(false);
      comp.sheet = ws;
      expect(comp.pasteEnabled()).toBe(true);
   });

   it("should return false when worksheet is a composite view", async () => {
      const { comp } = await renderComponent();
      const ws = new Worksheet();
      vi.spyOn(ws, "isCompositeView").mockReturnValue(true);
      comp.sheet = ws;
      expect(comp.pasteEnabled()).toBe(false);
   });
});
