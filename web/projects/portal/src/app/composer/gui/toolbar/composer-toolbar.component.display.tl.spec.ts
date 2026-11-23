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
 * ComposerToolbarComponent — Pass 3: Display
 *
 * Display-oriented computed properties; no risk-level items remain from Pass 1/2.
 *   Group 1  [baseline] — saveTooltipText(): text varies by sheet type;
 *                          confirms each of the four elif branches returns its own i18n key
 *   Group 2  [baseline] — saveAsTooltipText() + getOptionsTooltipText(): tooltip text varies
 *                          by tab type; getOptionsTooltipText() distinguishes script from others
 *   Group 3  [baseline] — hiddenComposerIcon: window.innerWidth < 350 gates AI assistant button
 *   Group 4  [baseline] — zoomInEnabled/zoomOutEnabled boundary conditions and isZoomItemSelected
 *                          matching current vs.scale
 *   Group 5  [baseline] — isGuideSelected: currentLayout.guideType match;
 *                          isPrintLayoutSelected: currentLayout.currentPrintSection match
 *   Group 6  [baseline] — getDatabaseLabel / getTabularLabel: dedup logic — use label when
 *                          unique, use name/dataSource when multiple sources share the same label
 *   Group 7  [baseline] — snappingOperations: visible() only for viewsheet;
 *                          enabled() gated by sheet, isPreview, layoutShowing
 *
 * Zone.js trap (documented in Pass 1 undo/redo group): setting focusedTab AFTER sheet triggers
 * an extra CD cycle that reaches snappingDropdown.isOpen() before mergeMenuCollapsed is set.
 * This pass avoids the trap by either (a) only setting comp.sheet — never comp.focusedTab
 * after comp.sheet, or (b) passing focusedTab as a componentProperty to renderComponent()
 * so it is applied during Angular initialization rather than as a post-render mutation.
 *
 * HTTP: N/A — display-only tests; no HTTP calls in any tested path
 *
 * Old spec coverage: all 6 cases confirmed covered in Pass 1 and Pass 2 (see Pass 2 header)
 */

import "@angular/compiler";
import { GuideBounds } from "../../../vsobjects/model/layout/guide-bounds";
import { PrintLayoutSection } from "../../../vsobjects/model/layout/print-layout-section";
import { ZoomOptions } from "../../../vsobjects/model/layout/zoom-options";
import { DatabaseDataSource } from "../../../../../../shared/util/model/database-data-source";
import { TabularDataSourceTypeModel } from "../../../../../../shared/util/model/tabular-data-source-type-model";
import { Viewsheet } from "../../data/vs/viewsheet";
import { Worksheet } from "../../data/ws/worksheet";
import { ComposerTabModel } from "../composer-tab-model";
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

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1: saveTooltipText() — correct variant per sheet type (baseline)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — saveTooltipText(): varies by sheet type", () => {
   it("should include saveVSDescription for a viewsheet", async () => {
      const { comp } = await renderComponent();
      comp.sheet = new Viewsheet();

      expect(comp.saveTooltipText()).toContain("saveVSDescription");
   });

   it("should include saveDescription for a worksheet", async () => {
      const { comp } = await renderComponent();
      comp.sheet = new Worksheet();

      expect(comp.saveTooltipText()).toContain("fl.action.saveDescription");
   });

   it("should include saveStyleDescription for a tableStyle tab", async () => {
      // focusedTab passed via componentProperties to avoid the zone.js trap
      const { comp } = await renderComponent({
         focusedTab: new ComposerTabModel("tableStyle", { type: "tableStyle" } as any),
      });

      expect(comp.saveTooltipText()).toContain("saveStyleDescription");
   });

   it("should include saveScriptDescription for a script tab", async () => {
      const { comp } = await renderComponent({
         focusedTab: new ComposerTabModel("script", { type: "script" } as any),
      });

      expect(comp.saveTooltipText()).toContain("saveScriptDescription");
   });
});

// ---------------------------------------------------------------------------
// Group 2: saveAsTooltipText() + getOptionsTooltipText() (baseline)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — saveAsTooltipText() + getOptionsTooltipText()", () => {
   it("should include saveVSAsDescription for a viewsheet save-as tooltip", async () => {
      const { comp } = await renderComponent();
      comp.sheet = new Viewsheet();

      expect(comp.saveAsTooltipText()).toContain("saveVSAsDescription");
   });

   it("should include saveAsDescription for a worksheet save-as tooltip", async () => {
      const { comp } = await renderComponent();
      comp.sheet = new Worksheet();

      expect(comp.saveAsTooltipText()).toContain("fl.action.saveAsDescription");
   });

   it("should include saveStyleAsDescription for a tableStyle save-as tooltip", async () => {
      const { comp } = await renderComponent({
         focusedTab: new ComposerTabModel("tableStyle", { type: "tableStyle" } as any),
      });

      expect(comp.saveAsTooltipText()).toContain("saveStyleAsDescription");
   });

   it("should include saveScriptAsDescription for a script save-as tooltip", async () => {
      const { comp } = await renderComponent({
         focusedTab: new ComposerTabModel("script", { type: "script" } as any),
      });

      expect(comp.saveAsTooltipText()).toContain("saveScriptAsDescription");
   });

   it("should include showScriptOptions in options tooltip for a script tab", async () => {
      const { comp } = await renderComponent({
         focusedTab: new ComposerTabModel("script", { type: "script" } as any),
      });

      expect(comp.getOptionsTooltipText()).toContain("showScriptOptions");
   });

   it("should include showSPropertyDes in options tooltip for a viewsheet tab", async () => {
      const { comp } = await renderComponent();

      expect(comp.getOptionsTooltipText()).toContain("showSPropertyDes");
   });
});

// ---------------------------------------------------------------------------
// Group 3: hiddenComposerIcon (baseline)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — hiddenComposerIcon: window.innerWidth gate", () => {
   it("should return true when window.innerWidth is less than 350", async () => {
      const { comp } = await renderComponent();
      Object.defineProperty(window, "innerWidth", { value: 200, configurable: true });
      try {
         expect(comp.hiddenComposerIcon).toBe(true);
      }
      finally {
         Object.defineProperty(window, "innerWidth", { value: 1024, configurable: true });
      }
   });

   it("should return false when window.innerWidth is 400", async () => {
      const { comp } = await renderComponent();
      Object.defineProperty(window, "innerWidth", { value: 400, configurable: true });
      try {
         expect(comp.hiddenComposerIcon).toBe(false);
      }
      finally {
         Object.defineProperty(window, "innerWidth", { value: 1024, configurable: true });
      }
   });
});

// ---------------------------------------------------------------------------
// Group 4: zoom display — zoomInEnabled, zoomOutEnabled, isZoomItemSelected (baseline)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — zoom: boundary conditions and selection state", () => {
   it("should return true from zoomInEnabled() when scale is 1.0 (below max 2.0)", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.scale = 1.0;
      comp.sheet = vs;

      expect(comp.zoomInEnabled()).toBe(true);
   });

   it("should return false from zoomInEnabled() when scale is 2.0 (at max)", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.scale = 2.0;
      comp.sheet = vs;

      expect(comp.zoomInEnabled()).toBe(false);
   });

   it("should return true from zoomOutEnabled() when scale is 1.0 (above min 0.2)", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.scale = 1.0;
      comp.sheet = vs;

      expect(comp.zoomOutEnabled()).toBe(true);
   });

   it("should return false from zoomOutEnabled() when scale is 0.2 (at min)", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.scale = 0.2;
      comp.sheet = vs;

      expect(comp.zoomOutEnabled()).toBe(false);
   });

   it("should return true from isZoomItemSelected when vs.scale matches the zoom option", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.scale = ZoomOptions.ZOOM_150;
      comp.sheet = vs;

      expect(comp.isZoomItemSelected(ZoomOptions.ZOOM_150)).toBe(true);
   });

   it("should return false from isZoomItemSelected when vs.scale does not match", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.scale = ZoomOptions.ZOOM_100;
      comp.sheet = vs;

      expect(comp.isZoomItemSelected(ZoomOptions.ZOOM_150)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 5: layout guide and print section selection (baseline)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — isGuideSelected / isPrintLayoutSelected", () => {
   function makeCurrentLayout(overrides: Record<string, any> = {}): Record<string, any> {
      return {
         guideType: GuideBounds.GUIDES_NONE,
         currentPrintSection: PrintLayoutSection.CONTENT,
         printLayout: false,
         focusedObjects: [],
         ...overrides,
      };
   }

   it("should return true from isGuideSelected when guideType matches", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      (vs as any).currentLayout = makeCurrentLayout({
         guideType: GuideBounds.GUIDES_16_9_LANDSCAPE,
      });
      comp.sheet = vs;

      expect(comp.isGuideSelected(GuideBounds.GUIDES_16_9_LANDSCAPE)).toBe(true);
   });

   it("should return false from isGuideSelected when guideType does not match", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      (vs as any).currentLayout = makeCurrentLayout({
         guideType: GuideBounds.GUIDES_NONE,
      });
      comp.sheet = vs;

      expect(comp.isGuideSelected(GuideBounds.GUIDES_16_9_LANDSCAPE)).toBe(false);
   });

   it("should return true from isPrintLayoutSelected when currentPrintSection matches", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      (vs as any).currentLayout = makeCurrentLayout({
         currentPrintSection: PrintLayoutSection.HEADER,
      });
      comp.sheet = vs;

      expect(comp.isPrintLayoutSelected(PrintLayoutSection.HEADER)).toBe(true);
   });

   it("should return false from isPrintLayoutSelected when currentPrintSection does not match", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      (vs as any).currentLayout = makeCurrentLayout({
         currentPrintSection: PrintLayoutSection.FOOTER,
      });
      comp.sheet = vs;

      expect(comp.isPrintLayoutSelected(PrintLayoutSection.HEADER)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 6: getDatabaseLabel / getTabularLabel dedup logic (baseline)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — getDatabaseLabel / getTabularLabel: dedup fallback", () => {
   it("should return label from getDatabaseLabel when no other source shares the same label", async () => {
      const { comp } = await renderComponent();
      const db: DatabaseDataSource = { name: "mysql-conn", label: "MySQL" };
      comp.databaseDataSources = [db];

      expect(comp.getDatabaseLabel(db)).toBe("MySQL");
   });

   it("should return name from getDatabaseLabel when two sources share the same label", async () => {
      const { comp } = await renderComponent();
      const db1: DatabaseDataSource = { name: "mysql-conn-1", label: "MySQL" };
      const db2: DatabaseDataSource = { name: "mysql-conn-2", label: "MySQL" };
      comp.databaseDataSources = [db1, db2];

      expect(comp.getDatabaseLabel(db1)).toBe("mysql-conn-1");
      expect(comp.getDatabaseLabel(db2)).toBe("mysql-conn-2");
   });

   it("should return label from getTabularLabel when no other type shares the same label", async () => {
      const { comp } = await renderComponent();
      const tabular: TabularDataSourceTypeModel = {
         name: "rest-type",
         label: "REST",
         dataSource: "rest-ds",
         exists: true,
      };
      comp.tabularDataSourceTypes = [tabular];

      expect(comp.getTabularLabel(tabular)).toBe("REST");
   });

   it("should return dataSource from getTabularLabel when two types share the same label", async () => {
      const { comp } = await renderComponent();
      const t1: TabularDataSourceTypeModel = {
         name: "rest-1",
         label: "REST",
         dataSource: "rest-ds-1",
         exists: true,
      };
      const t2: TabularDataSourceTypeModel = {
         name: "rest-2",
         label: "REST",
         dataSource: "rest-ds-2",
         exists: true,
      };
      comp.tabularDataSourceTypes = [t1, t2];

      expect(comp.getTabularLabel(t1)).toBe("rest-ds-1");
      expect(comp.getTabularLabel(t2)).toBe("rest-ds-2");
   });
});

// ---------------------------------------------------------------------------
// Group 7: snappingOperations visible/enabled (baseline)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — snappingOperations: visible and enabled gates", () => {
   it("should have visible()=true for a viewsheet", async () => {
      const { comp } = await renderComponent();
      comp.sheet = new Viewsheet();

      expect(comp.snappingOperations.visible!()).toBe(true);
   });

   it("should have visible()=false for a worksheet (snapping only applies to viewsheets)", async () => {
      const { comp } = await renderComponent();
      comp.sheet = new Worksheet();

      expect(comp.snappingOperations.visible!()).toBe(false);
   });

   it("should have enabled()=true for a normal editable viewsheet", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      comp.sheet = vs;

      expect(comp.snappingOperations.enabled!()).toBe(true);
   });

   it("should have enabled()=false when isPreview is true", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.preview = true;
      comp.sheet = vs;

      expect(comp.snappingOperations.enabled!()).toBe(false);
   });

   it("should have enabled()=false when in layout mode (layoutShowing=true)", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      (vs as any).currentLayout = {
         guideType: GuideBounds.GUIDES_NONE,
         currentPrintSection: PrintLayoutSection.CONTENT,
         printLayout: false,
         focusedObjects: [],
      };
      comp.sheet = vs;

      expect(comp.snappingOperations.enabled!()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 8: AI connect icon without a focused sheet + composer-level connected state (bug #81980)
// ---------------------------------------------------------------------------

describe("ComposerToolbarComponent — AI connect icon (bug #81980)", () => {
   const allowed = { viewsheetPermission: true, deployed: false };

   async function renderWithPairing(enabled: boolean, props: Record<string, any> = {}) {
      const r = await renderComponent(props);
      (r.comp as any).wizService.sheetAgentPairingEnabled = enabled;
      r.fixture.detectChanges();
      return r;
   }

   const toggle = (fixture: any) =>
      fixture.nativeElement.querySelector("button.wiz-connect-toggle") as HTMLButtonElement;

   function connectStub(vs: Viewsheet) {
      return { runtimeId: vs.runtimeId, socketConnection: vs.socketConnection, requestCode: vi.fn() };
   }

   function newSheet(id: number, runtimeId?: string): Viewsheet {
      const vs = new Viewsheet();
      vs.localId = id;
      vs.socketConnection = {} as any;
      vs.runtimeId = runtimeId;
      return vs;
   }

   it("renders the icon with no sheet focused when pairing is enabled", async () => {
      const { fixture } = await renderWithPairing(true, { sheet: null, ...allowed });
      expect(toggle(fixture)).not.toBeNull();
   });

   it("hides the icon when pairing is disabled", async () => {
      const { fixture } = await renderWithPairing(false, { sheet: null, ...allowed });
      expect(toggle(fixture)).toBeNull();
   });

   // The dropdown branch cannot be rendered in this harness: with a sheet set, the toolbar's
   // snapping dropdown (ngbDropdown stubbed under NO_ERRORS_SCHEMA) throws during change detection
   // (see the zone.js trap in the header). The branch is selected by isWorksheet/isViewsheet.
   it("selects the dropdown branch (sheet type) for a focused worksheet or viewsheet", async () => {
      const { comp } = await renderComponent();
      expect(comp.isWorksheet || comp.isViewsheet).toBeFalsy();
      comp.sheet = new Viewsheet();
      expect(comp.isViewsheet).toBe(true);
      expect(comp.isWorksheet).toBe(false);
      comp.sheet = new Worksheet();
      expect(comp.isWorksheet).toBe(true);
      expect(comp.isViewsheet).toBe(false);
   });

   it("clicking with no sheet asks the host to open a viewsheet", async () => {
      const { fixture, comp } = await renderWithPairing(true, { sheet: null, ...allowed });
      const emitted = vi.fn();
      comp.onConnectWithoutSheet.subscribe(emitted);
      toggle(fixture).click();
      expect(emitted).toHaveBeenCalledTimes(1);
   });

   it("is disabled with an explanatory tooltip and does nothing without viewsheet permission", async () => {
      const { fixture, comp } = await renderWithPairing(
         true, { sheet: null, viewsheetPermission: false, deployed: false });
      const emitted = vi.fn();
      comp.onConnectWithoutSheet.subscribe(emitted);
      expect(toggle(fixture).getAttribute("aria-disabled")).toBe("true");
      expect(comp.connectWithoutSheetAllowed).toBe(false);
      expect(comp.connectTooltipDisabledText).toContain("permission");
      toggle(fixture).click();
      expect(emitted).not.toHaveBeenCalled();
   });

   it("is disabled in a deployed composer", async () => {
      const { comp } = await renderWithPairing(true, { sheet: null, viewsheetPermission: true, deployed: true });
      expect(comp.connectWithoutSheetAllowed).toBe(false);
   });

   describe("auto-connect pinned to the opened viewsheet", () => {
      beforeEach(() => vi.useFakeTimers());
      afterEach(() => vi.useRealTimers());

      it("mints once the target has a runtimeId and is focused", async () => {
         const { comp } = await renderWithPairing(true, { sheet: null, ...allowed });
         const target = newSheet(7);
         (comp as any)._sheet = target;
         const connect = connectStub(target);
         (comp as any).connectToClaude = connect;

         comp.beginAutoConnect(target, () => true);
         vi.advanceTimersByTime(400);
         expect(connect.requestCode).not.toHaveBeenCalled();

         target.runtimeId = "rt-1";
         connect.runtimeId = "rt-1";
         vi.advanceTimersByTime(400);
         expect(connect.requestCode).toHaveBeenCalledTimes(1);
         vi.advanceTimersByTime(2000);
         expect(connect.requestCode).toHaveBeenCalledTimes(1);
      });

      it("never mints against another viewsheet that takes focus during the wait", async () => {
         const { comp } = await renderWithPairing(true, { sheet: null, ...allowed });
         const target = newSheet(7);
         const other = newSheet(8, "rt-other");
         (comp as any)._sheet = other;
         const connect = connectStub(other);
         (comp as any).connectToClaude = connect;

         comp.beginAutoConnect(target, () => true);
         vi.advanceTimersByTime(3000);
         expect(connect.requestCode).not.toHaveBeenCalled();

         target.runtimeId = "rt-1";
         (comp as any)._sheet = target;
         connect.runtimeId = "rt-1";
         connect.socketConnection = target.socketConnection;
         vi.advanceTimersByTime(400);
         expect(connect.requestCode).toHaveBeenCalledTimes(1);
      });

      it("abandons with a notice when the target is closed", async () => {
         const { comp } = await renderWithPairing(true, { sheet: null, ...allowed });
         const target = newSheet(7);
         const other = newSheet(8, "rt-other");
         (comp as any)._sheet = other;
         const connect = connectStub(other);
         (comp as any).connectToClaude = connect;
         const notified = vi.fn();
         comp.onNotification.subscribe(notified);
         let open = true;

         comp.beginAutoConnect(target, () => open);
         open = false;
         vi.advanceTimersByTime(400);
         expect(notified).toHaveBeenCalledTimes(1);
         expect(connect.requestCode).not.toHaveBeenCalled();
         expect((comp as any).pendingConnect).toBeNull();
         vi.advanceTimersByTime(40000);
         expect(notified).toHaveBeenCalledTimes(1);
      });

      it("tells the user when the target never gets a runtime", async () => {
         const { comp } = await renderWithPairing(true, { sheet: null, ...allowed });
         const notified = vi.fn();
         comp.onNotification.subscribe(notified);
         comp.beginAutoConnect(newSheet(7), () => true);
         vi.advanceTimersByTime(31000);
         expect(notified).toHaveBeenCalledTimes(1);
         expect((comp as any).pendingConnect).toBeNull();
      });
   });

   it("shows the connected class and count in tooltip and aria-label when open sheets are connected", async () => {
      const { fixture, comp } = await renderWithPairing(true, { sheet: null, agentConnectedCount: 2, ...allowed });
      expect(toggle(fixture).classList.contains("agent-connected")).toBe(true);
      expect(comp.connectedTooltipText).toContain("(2)");
      expect(toggle(fixture).getAttribute("aria-label")).toContain("(2)");
   });

   it("has no connected class when no open sheet is connected", async () => {
      const { fixture } = await renderWithPairing(true, { sheet: null, agentConnectedCount: 0, ...allowed });
      expect(toggle(fixture).classList.contains("agent-connected")).toBe(false);
   });
});
