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
 * ComposerMainComponent — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3]  — ngOnInit: setPrincipalCommand → permission fields bound to component;
 *                         fixAutoSaveFiles called only when worksheetPermission=true, autoSaveFiles
 *                         non-empty, and vsWizard/wsWizard are both false
 *   Group 2  [Risk 3]  — openSheet: embedded=true → embeddedId = focusedSheet.runtimeId;
 *                         embedded=false → embeddedId = null (Bug #16301)
 *   Group 3  [Risk 2]  — updateSidebar: device layout → formatPaneDisabled=true (Bug #18803);
 *                         print layout → formatPaneDisabled=false (Bug #18805)
 *   Group 4  [Risk 2]  — beforeunloadHandler: modified sheet sets event.returnValue; unmodified
 *                         sheets leave event.returnValue unset; return value matches
 *   Group 5  [Risk 2]  — onSheetClosed: forceClose=false + isModified → shows confirm dialog;
 *                         forceClose=true + isModified → bypasses dialog
 *   Group 6  [Risk 2]  — previewViewsheet: label = "Preview label (layoutName)" for device layout;
 *                         print layout appends name since direct call is unconditional
 *                         (Bugs #19832/#20165 affect getUpdatedLayoutPreviewSheet, covered separately)
 *   Group 7  [baseline] — openNewWorksheet: pushes Worksheet to sheets + openedTabs; sets focusedSheet;
 *                          componentsPaneDisabled=true for worksheet (migratedold spec Bug #N/A)
 *   Group 8  [baseline] — openNewViewsheet: pushes Viewsheet with newSheet=true to sheets
 *   Group 9  [baseline] — onToggleSnapToGrid / onToggleSnapToObjects: toggles booleans + persists
 *   Group 10 [baseline] — processNotification: routes type to correct notifications method
 *   Group 11 [baseline] — pasteObjects: snap to nearest 20 when snapToGrid=true;
 *                          uses pos directly when snapToGrid=false
 *   Group 12 [baseline] — ngOnDestroy: disconnect() called; subscriptions cleaned up
 *   Group 13 [baseline] — toggleSplitPane: collapse ↔ expand; calls resizeHandlerService
 *   Group 14 [baseline] — copySheet/cutSheet delegate to clipboardService.addToClipboard
 *   Group 15 [baseline] — onTabClick: sets selectedTab; setGrayedOutFields / toggleImportDialog
 *   Group 16 [Risk 2]  — closePreview: focus returns to parent VS after preview closed (Bug #19612)
 *
 * Confirmed bugs (it.fails): none in this pass
 *
 * Suspected bugs (header only):
 *   Suspicion A — onSheetClosed + closeOnComplete: when closeOnComplete=true and the last sheet is
 *     closed without modification, closed.emit(sheet.id) fires. If multiple tabs are open and the
 *     user closes a non-last sheet, closed is NOT emitted. The guard checks sheets.length==0 AFTER
 *     splice, so closing the second-to-last sheet when it's the focused sheet may not emit.
 *
 * HTTP: MSW inline server.use() — for onNewTableStyle (GET ../api/composer/table-style/new),
 *   openNewScriptAsset (GET ../api/script/new), openLibraryAsset script/tableStyle paths;
 *   ModelService stub for modelService.getModel() calls.
 *
 * Out of scope this pass: focusedTab setter, updateFocusedSheet, openAutoSaveFiles,
 *   recycleAutoSaveFiles (Pass 2); showPaste, getLinkVSLabel, updateFormat,
 *   layoutFormatObjects, layoutShowing, openFormatPane (Pass 3)
 */

import "@angular/compiler";
import { ComponentTool } from "../../common/util/component-tool";
import { Point } from "../../common/data/point";
import { ComposerMainComponent, SidebarTab } from "./composer-main.component";
import { ComposerTabModel } from "./composer-tab-model";
import { Viewsheet } from "../data/vs/viewsheet";
import { Worksheet } from "../data/ws/worksheet";
import { LocalStorage } from "../../common/util/local-storage.util";
import { makeMocks, renderComponent } from "./composer-main.spec-helpers";

// ---------------------------------------------------------------------------
// Global setup
// ---------------------------------------------------------------------------

beforeAll(() => {
   (window as any).BroadcastChannel = (window as any).BroadcastChannel ?? class {
      onmessage: any = null;
      postMessage() {}
      close() {}
      addEventListener() {}
      removeEventListener() {}
   };
});

afterEach(() => {
   vi.restoreAllMocks();
   localStorage.clear();
});

// ---------------------------------------------------------------------------
// Group 1: ngOnInit — setPrincipalCommand permissions (Risk 3)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — ngOnInit: setPrincipalCommand", () => {
   // 🔁 Regression-sensitive: all permission fields must be copied; a missing field silently
   // disables the corresponding feature for the session without error.
   it("should bind all permission fields from setPrincipalCommand", async () => {
      const cmd = {
         principal: "user1",
         securityEnabled: true,
         viewsheetPermission: true,
         worksheetPermission: true,
         tableStylePermission: true,
         scriptPermission: true,
         aiAssistantPermission: false,
         autoSaveFiles: [] as string[],
      };
      const { comp } = await renderComponent({ setPrincipalCommand: cmd });

      expect(comp.principal).toBe("user1");
      expect(comp.securityEnabled).toBe(true);
      expect(comp.viewsheetPermission).toBe(true);
      expect(comp.worksheetPermission).toBe(true);
      expect(comp.tableStylePermission).toBe(true);
      expect(comp.scriptPermission).toBe(true);
      expect(comp.aiAssistantPermission).toBe(false);
   });

   // 🔁 Regression-sensitive: fixAutoSaveFiles is guarded by vsWizard=false, wsWizard=false,
   // worksheetPermission=true, and autoSaveFiles.length>0. Removing any gate causes an unwanted
   // restore dialog on every composer open.
   it("should call fixAutoSaveFiles when worksheetPermission=true and autoSaveFiles is non-empty", async () => {
      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("cancel");
      const cmd = {
         principal: "user1",
         securityEnabled: true,
         viewsheetPermission: true,
         worksheetPermission: true,
         tableStylePermission: false,
         scriptPermission: false,
         aiAssistantPermission: false,
         autoSaveFiles: ["file1"],
      };
      await renderComponent({ setPrincipalCommand: cmd, vsWizard: false, wsWizard: false });

      expect(dialogSpy).toHaveBeenCalled();
   });

   it("should NOT call fixAutoSaveFiles when worksheetPermission is false", async () => {
      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("cancel");
      const cmd = {
         principal: "user1",
         securityEnabled: false,
         viewsheetPermission: false,
         worksheetPermission: false,
         tableStylePermission: false,
         scriptPermission: false,
         aiAssistantPermission: false,
         autoSaveFiles: ["file1"],
      };
      await renderComponent({ setPrincipalCommand: cmd });

      expect(dialogSpy).not.toHaveBeenCalled();
   });

   it("should NOT call fixAutoSaveFiles when vsWizard is true", async () => {
      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("cancel");
      const cmd = {
         principal: "user1",
         securityEnabled: true,
         viewsheetPermission: true,
         worksheetPermission: true,
         tableStylePermission: false,
         scriptPermission: false,
         aiAssistantPermission: false,
         autoSaveFiles: ["file1"],
      };
      await renderComponent({ setPrincipalCommand: cmd, vsWizard: true });

      expect(dialogSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2: openViewsheet — embeddedId (Bug #16301) (Risk 3)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — openViewsheet: embeddedId (Bug #16301)", () => {
   // 🔁 Regression-sensitive: embeddedId is used by the server to wire the embedded vs to its
   // parent. Setting it to null for non-embedded opens, or to the wrong parent, breaks the
   // embedded vs feature without any visible client-side error.
   it("should set embeddedId to focusedSheet.runtimeId when opening an embedded viewsheet", async () => {
      const { comp } = await renderComponent();
      const parent = new Viewsheet();
      parent.localId = 1;
      parent.id = "parentId";
      parent.runtimeId = "parent-rt-001";
      comp.sheets.push(parent);
      comp.openedTabs.push(new ComposerTabModel("viewsheet", parent));
      comp.focusedSheet = parent;

      comp.openViewsheet("childId", true /* embedded */);

      const child = comp.sheets.find(s => s.id === "childId") as Viewsheet;
      expect(child).toBeTruthy();
      expect(child.embeddedId).toBe("parent-rt-001");
   });

   it("should set embeddedId to null when opening a non-embedded viewsheet", async () => {
      const { comp } = await renderComponent();
      const parent = new Viewsheet();
      parent.localId = 1;
      parent.id = "parentId";
      parent.runtimeId = "parent-rt-002";
      comp.sheets.push(parent);
      comp.openedTabs.push(new ComposerTabModel("viewsheet", parent));
      comp.focusedSheet = parent;

      comp.openViewsheet("standaloneId", false /* not embedded */);

      const child = comp.sheets.find(s => s.id === "standaloneId") as Viewsheet;
      expect(child).toBeTruthy();
      expect(child.embeddedId).toBeNull();
   });

   it("should set embeddedId to null when embedded=true but no focusedSheet", async () => {
      const { comp } = await renderComponent();

      comp.openViewsheet("childId", true /* embedded */);

      const child = comp.sheets.find(s => s.id === "childId") as Viewsheet;
      expect(child.embeddedId).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 3: updateSidebar — format pane state (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — updateSidebar: layout mode", () => {
   // updateSidebar takes a separate "deployed" branch that always enables formatPane; these
   // tests must run with deployed=false to exercise the real layout logic.

   // 🔁 Regression-sensitive: the format pane disabled state must track device vs print layout.
   // Bug #18803: format pane incorrectly enabled for device layout (should be disabled).
   it("should disable formatPane when viewsheet is in device layout mode (Bug #18803)", async () => {
      // deployed=false: initComposerClient() is called but composerClient is mocked
      const { comp } = await renderComponent({ deployed: false });
      const vs = new Viewsheet();
      vs.localId = 1;
      vs.id = "vs1";
      vs.runtimeId = "vs1-rt";
      (vs as any).currentLayout = { name: "Device", printLayout: false };
      comp.sheets.push(vs);
      comp.openedTabs.push(new ComposerTabModel("viewsheet", vs));
      comp.focusedSheet = vs;

      comp.onSheetUpdated(vs);

      expect(comp.formatPaneDisabled).toBe(true);
   });

   // 🔁 Regression-sensitive: Bug #18805 — print layout must keep format pane enabled so users
   // can apply formatting to print layout objects.
   it("should enable formatPane when viewsheet is in print layout mode (Bug #18805)", async () => {
      const { comp } = await renderComponent({ deployed: false });
      const vs = new Viewsheet();
      vs.localId = 1;
      vs.id = "vs1";
      vs.runtimeId = "vs1-rt";
      (vs as any).currentLayout = { name: "Print Layout", printLayout: true };
      comp.sheets.push(vs);
      comp.openedTabs.push(new ComposerTabModel("viewsheet", vs));
      comp.focusedSheet = vs;

      comp.onSheetUpdated(vs);

      expect(comp.formatPaneDisabled).toBe(false);
   });

   it("should disable both toolbox and formatPane when sheet is null (non-deployed)", async () => {
      const { comp } = await renderComponent({ deployed: false });

      (comp as any).updateSidebar(null);

      expect(comp.toolboxDisabled).toBe(true);
      expect(comp.formatPaneDisabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4: beforeunloadHandler — @HostListener (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — beforeunloadHandler", () => {
   // 🔁 Regression-sensitive: the handler must set event.returnValue to trigger the browser's
   // "leave page?" confirmation. If omitted, unsaved changes are silently discarded on navigation.
   it("should set event.returnValue when a sheet has unsaved changes", async () => {
      const { comp } = await renderComponent();
      const ws = new Worksheet();
      ws.id = "ws1";
      ws.runtimeId = "ws1-rt";
      vi.spyOn(ws, "isModified").mockReturnValue(true);
      comp.sheets.push(ws);

      const event: any = {};
      comp.beforeunloadHandler(event);

      expect(event.returnValue).toBeTruthy();
   });

   it("should not set event.returnValue when no sheets have unsaved changes", async () => {
      const { comp } = await renderComponent();
      const ws = new Worksheet();
      ws.id = "ws1";
      ws.runtimeId = "ws1-rt";
      vi.spyOn(ws, "isModified").mockReturnValue(false);
      comp.sheets.push(ws);

      const event: any = {};
      const result = comp.beforeunloadHandler(event);

      expect(event.returnValue).toBeUndefined();
      expect(result).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 5: onSheetClosed — close flow (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — onSheetClosed", () => {
   // 🔁 Regression-sensitive: the confirm dialog must appear when a sheet has unsaved changes
   // and forceClose=false. Removing the guard silently discards edits.
   it("should show confirm dialog when sheet isModified and forceClose=false", async () => {
      const { comp } = await renderComponent();
      const ws = new Worksheet();
      ws.id = "ws1";
      ws.runtimeId = "ws1-rt";
      vi.spyOn(ws, "isModified").mockReturnValue(true);

      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("cancel");

      comp.onSheetClosed(ws, false);

      expect(dialogSpy).toHaveBeenCalled();
   });

   it("should bypass the confirm dialog when forceClose=true even if sheet isModified", async () => {
      const { comp, mocks } = await renderComponent();
      const ws = new Worksheet();
      ws.id = "ws1";
      ws.runtimeId = "ws1-rt";
      vi.spyOn(ws, "isModified").mockReturnValue(true);

      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("cancel");

      comp.onSheetClosed(ws, true);

      expect(dialogSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 6: previewViewsheet — label construction (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — previewViewsheet: label", () => {
   // 🔁 Regression-sensitive: Bugs #19832/#20165 — preview label must include layout name for
   // device layouts so users can distinguish which layout is being previewed.
   it("should append layout name to preview label when device layout is active", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.localId = 1;
      vs.label = "SalesVS";
      vs.id = "vs1";
      vs.runtimeId = "vs1-rt";
      (vs as any).currentLayout = { name: "Tablet Layout", printLayout: false };
      comp.sheets.push(vs);
      comp.openedTabs.push(new ComposerTabModel("viewsheet", vs));

      comp.previewViewsheet(vs);

      const previewSheet = comp.sheets[comp.sheets.length - 1];
      expect(previewSheet.label).toContain("SalesVS");
      expect(previewSheet.label).toContain("(Tablet Layout)");
   });

   it("should not append layout name when there is no current layout", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.localId = 1;
      vs.label = "SalesVS";
      vs.id = "vs1";
      vs.runtimeId = "vs1-rt";
      (vs as any).currentLayout = null;
      comp.sheets.push(vs);
      comp.openedTabs.push(new ComposerTabModel("viewsheet", vs));

      comp.previewViewsheet(vs);

      const previewSheet = comp.sheets[comp.sheets.length - 1];
      expect(previewSheet.label).toBe("_#(js:Preview) SalesVS");
   });

   it("should set preview=true on the new preview sheet", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.localId = 1;
      vs.label = "SalesVS";
      vs.id = "vs1";
      vs.runtimeId = "vs1-rt";
      (vs as any).currentLayout = null;
      comp.sheets.push(vs);
      comp.openedTabs.push(new ComposerTabModel("viewsheet", vs));

      comp.previewViewsheet(vs);

      const previewSheet = comp.sheets[comp.sheets.length - 1] as Viewsheet;
      expect(previewSheet.preview).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 7: openNewWorksheet — baseline
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — openNewWorksheet", () => {
   it("should add a worksheet to sheets and openedTabs", async () => {
      const { comp } = await renderComponent();
      const initialCount = comp.sheets.length;

      comp.openNewWorksheet();

      expect(comp.sheets.length).toBe(initialCount + 1);
      expect(comp.sheets[comp.sheets.length - 1].type).toBe("worksheet");
      expect(comp.openedTabs.length).toBe(initialCount + 1);
   });

   it("should set newSheet=true on the created worksheet", async () => {
      const { comp } = await renderComponent();

      comp.openNewWorksheet();

      const ws = comp.sheets[comp.sheets.length - 1] as Worksheet;
      expect(ws.newSheet).toBe(true);
   });

   it("should set componentsPaneDisabled=true when a worksheet is focused", async () => {
      const { comp } = await renderComponent();

      comp.openNewWorksheet();

      expect(comp.componentsPaneDisabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 8: openNewViewsheet — baseline
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — openNewViewsheet", () => {
   it("should add a viewsheet with newSheet=true to sheets", async () => {
      const { comp } = await renderComponent();

      comp.openNewViewsheet(null);

      const vs = comp.sheets[comp.sheets.length - 1] as Viewsheet;
      expect(vs.type).toBe("viewsheet");
      expect(vs.newSheet).toBe(true);
   });

   it("should set baseEntry on the viewsheet when a non-folder entry is provided", async () => {
      const { comp } = await renderComponent();
      const entry = { folder: false, type: 4 /* WORKSHEET */ } as any;

      comp.openNewViewsheet(entry);

      const vs = comp.sheets[comp.sheets.length - 1] as Viewsheet;
      expect(vs.baseEntry).toBe(entry);
   });
});

// ---------------------------------------------------------------------------
// Group 9: onToggleSnapToGrid / onToggleSnapToObjects — baseline
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — onToggleSnapToGrid / onToggleSnapToObjects", () => {
   it("should toggle snapToGrid and persist to localStorage", async () => {
      const { comp } = await renderComponent();
      const initial = comp.snapToGrid;

      comp.onToggleSnapToGrid();

      expect(comp.snapToGrid).toBe(!initial);
      expect(LocalStorage.getItem("snap-to-grid")).toBe(String(!initial));
   });

   it("should toggle snapToObjects and persist to localStorage", async () => {
      const { comp } = await renderComponent();
      const initial = comp.snapToObjects;

      comp.onToggleSnapToObjects();

      expect(comp.snapToObjects).toBe(!initial);
      expect(LocalStorage.getItem("snap-to-objects")).toBe(String(!initial));
   });
});

// ---------------------------------------------------------------------------
// Group 10: processNotification — baseline
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — processNotification", () => {
   it("should call notifications.success for type 'success'", async () => {
      const { comp } = await renderComponent();
      const successSpy = (comp as any).notifications.success as ReturnType<typeof vi.fn>;

      comp.processNotification({ type: "success", message: "Saved!" } as any);

      expect(successSpy).toHaveBeenCalledWith("Saved!");
   });

   it("should call notifications.danger for type 'danger'", async () => {
      const { comp } = await renderComponent();
      const dangerSpy = (comp as any).notifications.danger as ReturnType<typeof vi.fn>;

      comp.processNotification({ type: "danger", message: "Error!" } as any);

      expect(dangerSpy).toHaveBeenCalledWith("Error!");
   });
});

// ---------------------------------------------------------------------------
// Group 11: pasteObjects — snap math (baseline)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — pasteObjects", () => {
   // 🔁 Regression-sensitive: snap math must round to nearest 20 independently for x and y.
   // Incorrect rounding causes objects to paste at unaligned positions that look off-grid.
   it("should snap pos to nearest 20 when snapToGrid=true", async () => {
      const { comp, mocks } = await renderComponent();
      const vs = new Viewsheet();
      vs.localId = 1;
      vs.id = "vs1";
      vs.runtimeId = "vs1-rt";
      comp.snapToGrid = true;

      comp.pasteObjects(vs, new Point(37, 53));

      expect(mocks.clipboardService.pasteObjects).toHaveBeenCalledWith(
         vs,
         new Point(40, 60)
      );
   });

   it("should use pos as-is when snapToGrid=false", async () => {
      const { comp, mocks } = await renderComponent();
      const vs = new Viewsheet();
      vs.localId = 1;
      vs.id = "vs1";
      vs.runtimeId = "vs1-rt";
      comp.snapToGrid = false;

      comp.pasteObjects(vs, new Point(37, 53));

      expect(mocks.clipboardService.pasteObjects).toHaveBeenCalledWith(vs, new Point(37, 53));
   });
});

// ---------------------------------------------------------------------------
// Group 12: ngOnDestroy — subscription cleanup (baseline)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — ngOnDestroy", () => {
   // 🔁 Regression-sensitive: composerClient.disconnect() must be called on destroy to close the
   // WebSocket. A missing call leaves a dangling connection that can cause ghost messages.
   it("should call composerClient.disconnect() on destroy", async () => {
      const { fixture, mocks } = await renderComponent();

      fixture.destroy();

      expect(mocks.composerClient.disconnect).toHaveBeenCalled();
   });

   it("should not push a sheet when hyperLinkService emits after the component is destroyed", async () => {
      // 🔁 Regression-sensitive: the constructor subscribes hyperLinkService.showLinkSheetSubject
      // to call showLinkVSInTab. If that subscription leaks, a link-open message from another tab
      // fires into the destroyed component and mutates its sheet list.
      const { fixture, comp, mocks } = await renderComponent();
      const initialCount = comp.sheets.length;

      fixture.destroy();
      mocks.hyperLinkService.showLinkSheetSubject.next({
         id: "1^128^null^MyVS",
         queryParameters: new Map(),
      });

      expect(comp.sheets.length).toBe(initialCount);
   });
});

// ---------------------------------------------------------------------------
// Group 13: toggleSplitPane — baseline
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — toggleSplitPane", () => {
   it("should collapse the split pane when it is currently expanded", async () => {
      const { comp, mocks } = await renderComponent();
      comp.splitPaneCollapsed = false;
      const splitPane = (comp as any).splitPane;

      comp.toggleSplitPane();

      expect(splitPane.collapse).toHaveBeenCalledWith(0);
      expect(comp.splitPaneCollapsed).toBe(true);
      expect(mocks.resizeHandlerService.onVerticalResizeEnd).toHaveBeenCalled();
   });

   it("should expand the split pane when it is currently collapsed", async () => {
      const { comp, mocks } = await renderComponent();
      comp.splitPaneCollapsed = true;
      comp.splitPaneSize = 30;
      const splitPane = (comp as any).splitPane;

      comp.toggleSplitPane();

      expect(splitPane.setSizes).toHaveBeenCalledWith([30, 70]);
      expect(comp.splitPaneCollapsed).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 14: clipboard delegation — baseline
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — clipboard delegation", () => {
   it("should call clipboardService.addToClipboard(sheet, false) for copySheet", async () => {
      const { comp, mocks } = await renderComponent();
      const vs = new Viewsheet();
      vs.localId = 1;

      comp.copySheet(vs);

      expect(mocks.clipboardService.addToClipboard).toHaveBeenCalledWith(vs, false);
   });

   it("should call clipboardService.addToClipboard(sheet, true) for cutSheet", async () => {
      const { comp, mocks } = await renderComponent();
      const vs = new Viewsheet();
      vs.localId = 1;

      comp.cutSheet(vs);

      expect(mocks.clipboardService.addToClipboard).toHaveBeenCalledWith(vs, true);
   });
});

// ---------------------------------------------------------------------------
// Group 15: onTabClick / setGrayedOutFields / toggleImportDialog — baseline
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — onTabClick / setGrayedOutFields / toggleImportDialog", () => {
   it("should set selectedTab when onTabClick is called", async () => {
      const { comp } = await renderComponent();

      comp.onTabClick(SidebarTab.FORMAT);

      expect(comp.selectedTab).toBe(SidebarTab.FORMAT);
   });

   it("should store refs via setGrayedOutFields", async () => {
      const { comp } = await renderComponent();
      const refs = [{ name: "col1" } as any];

      comp.setGrayedOutFields(refs);

      expect(comp.grayedOutFields).toBe(refs);
   });

   it("should set importDialogOpen via toggleImportDialog", async () => {
      const { comp } = await renderComponent();

      comp.toggleImportDialog(true);
      expect(comp.importDialogOpen).toBe(true);

      comp.toggleImportDialog(false);
      expect(comp.importDialogOpen).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 16: closePreview — focus returns to parent VS (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — closePreview: parent focus (Bug #19612)", () => {
   function makePreviewPair(comp: ComposerMainComponent) {
      const parentVS = new Viewsheet();
      parentVS.localId = 101;
      parentVS.runtimeId = "vs1";
      parentVS.label = "vs1";
      (parentVS as any).socketConnection = { sendEvent: vi.fn() };

      const previewVS = new Viewsheet();
      previewVS.localId = 102;
      previewVS.runtimeId = "preview-vs1";
      previewVS.label = "_#(js:Preview) vs1";
      previewVS.preview = true;
      previewVS.parentSheet = parentVS;

      comp.sheets.push(parentVS);
      comp.sheets.push(previewVS);
      comp.openedTabs.push(new ComposerTabModel("viewsheet", parentVS));
      comp.openedTabs.push(new ComposerTabModel("viewsheet", previewVS));
      comp.focusedSheet = previewVS;
      (comp as any)._focusedTab = comp.openedTabs[comp.openedTabs.length - 1];

      return { parentVS, previewVS };
   }

   // 🔁 Regression-sensitive: closeSheet detects preview=true and uses parentSheet to determine the
   // next focused sheet; if the preview branch is ever removed the user is left with a null focus.
   it("should restore focus to the parent viewsheet after closing its preview tab", async () => {
      const { comp } = await renderComponent();
      makePreviewPair(comp);

      comp.closePreview(1);

      expect(comp.focusedSheet!.runtimeId).toBe("vs1");
   });

   // 🔁 Regression-sensitive: preview sheet must be removed from both arrays so the tab row
   // disappears; a stale entry in openedTabs renders a phantom tab.
   it("should remove the preview sheet from sheets and openedTabs", async () => {
      const { comp } = await renderComponent();
      makePreviewPair(comp);

      comp.closePreview(1);

      expect(comp.sheets).toHaveLength(1);
      expect(comp.openedTabs).toHaveLength(1);
   });
});
