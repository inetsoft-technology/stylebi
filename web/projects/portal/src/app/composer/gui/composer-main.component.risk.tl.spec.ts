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
 * ComposerMainComponent — Pass 2: Risk
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — set focusedTab: sheet tab → _focusedSheet updated via updateFocusedSheet;
 *                       LibraryAsset (script) tab → _focusedSheet set to null
 *   Group 2 [Risk 3] — getScriptTreePane: loadingScriptTreePaneModel set synchronously before
 *                       data arrives; response data replaces it
 *   Group 3 [Risk 3] — removeAssembly: simple assembly removed directly; VSGroupContainer child
 *                       → whole group removed; VSSelectionContainer model matches → only child
 *                       removed, container stays; non-matching → container removed
 *   Group 4 [Risk 3] — openAutoSaveAsset (via openAutoSaveFiles): WORKSHEET → Worksheet; VS →
 *                       Viewsheet; id constructed from 4-part path; multiple files → multiple sheets
 *   Group 5 [Risk 2] — recycleAutoSaveFiles: calls modelService.getModel with the recycle URL
 *   Group 6 [Risk 2] — refreshAiAssistantContext: null focusedSheet → resetContextMap only; worksheet
 *                       → setWorksheetContext; viewsheet → setViewsheetScriptContext
 *   Group 7 [Risk 2] — openScriptOptions: non-script tab → no dialog; script tab → dialog opened
 *                       with correct comment and scriptFontSize pre-populated
 *   Group 8 [baseline] — checkRemovedAssembly, clearFocusedObjects, worksheetCancel
 *
 * HTTP: N/A — ModelService is provider-mocked (useValue); no real HTTP in Pass 2 methods
 *
 * Out of scope this pass: updateFocusedSheet (private, via set focusedTab), updateFocusedTab
 *   (private, via set focusedTab), openAutoSaveAsset (private, via openAutoSaveFiles), isSheet
 *   (private, via set focusedTab), removeKeydownListener (private, via ngOnDestroy in Pass 1),
 *   getParent (AssetEntryHelper static utility — not defined in this component),
 *   sendEvent (method on socketConnection — not defined in this component)
 */

import "@angular/compiler";
import { Subject, of } from "rxjs";
import { ComponentTool } from "../../common/util/component-tool";
import { ComposerTabModel } from "./composer-tab-model";
import { Viewsheet } from "../data/vs/viewsheet";
import { Worksheet } from "../data/ws/worksheet";
import { loadingScriptTreePaneModel } from "../data/script/script-tree-pane-model";
import { makeMocks, renderComponent, setupComposerMainTlEnv } from "./composer-main.spec-helpers";

// ---------------------------------------------------------------------------
// Global setup
// ---------------------------------------------------------------------------

vi.setConfig({ testTimeout: 30000 });

beforeAll(() => {
   setupComposerMainTlEnv();
});

afterEach(() => {
   vi.restoreAllMocks();
   localStorage.clear();
   setupComposerMainTlEnv();
});

// ---------------------------------------------------------------------------
// Group 1: set focusedTab — asset-type routing (Risk 3)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — set focusedTab: asset-type routing", () => {
   // 🔁 Regression-sensitive: focusedTab setter must route asset type correctly — setting a sheet
   // tab updates focusedSheet; setting a script tab must set focusedSheet to null so the sidebar
   // and toolbar do not behave as if a viewsheet is still active.
   it("should update _focusedSheet when a viewsheet tab is set", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.localId = 1;
      vs.runtimeId = "vs1-rt";
      comp.sheets.push(vs);
      comp.openedTabs.push(new ComposerTabModel("viewsheet", vs));

      comp.focusedTab = new ComposerTabModel("viewsheet", vs);

      expect((comp as any)._focusedSheet).toBe(vs);
   });

   it("should set _focusedSheet to null when a script (LibraryAsset) tab is set", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.localId = 1;
      vs.runtimeId = "vs1-rt";
      comp.sheets.push(vs);
      comp.openedTabs.push(new ComposerTabModel("viewsheet", vs));
      (comp as any)._focusedSheet = vs;

      const scriptAsset = { type: "script", label: "MyScript", isModified: false } as any;
      comp.focusedTab = new ComposerTabModel("script", scriptAsset);

      expect((comp as any)._focusedSheet).toBeNull();
   });

   // 🔁 Regression-sensitive: refreshAiAssistantContext must fire on every tab switch so the AI
   // assistant context stays in sync; missing this call leaves the AI operating on stale context.
   it("should call refreshAiAssistantContext on every focusedTab assignment", async () => {
      const { comp, mocks } = await renderComponent();
      mocks.aiAssistantService.resetContextMap.mockClear();
      const vs = new Viewsheet();
      vs.localId = 1;
      vs.runtimeId = "vs1-rt";

      comp.focusedTab = new ComposerTabModel("viewsheet", vs);

      expect(mocks.aiAssistantService.resetContextMap).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2: getScriptTreePane — loading state + data (Risk 3)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — getScriptTreePane: loading state and data update", () => {
   // 🔁 Regression-sensitive: the loading state must be set synchronously before the HTTP response
   // so the UI shows a spinner immediately; if skipped, the pane appears blank until data arrives.
   it("should set scriptTreePaneModel to loading before data arrives", async () => {
      const { comp, mocks } = await renderComponent();
      const dataSubject = new Subject<any>();
      mocks.modelService.getModel.mockReturnValueOnce(dataSubject.asObservable() as any);

      comp.getScriptTreePane();

      expect(comp.scriptTreePaneModel).toBe(loadingScriptTreePaneModel);
   });

   it("should update scriptTreePaneModel with the response data", async () => {
      const { comp, mocks } = await renderComponent();
      const treeData = { functionTree: { items: ["fn1"] } };
      mocks.modelService.getModel.mockReturnValueOnce(of(treeData) as any);

      comp.getScriptTreePane();

      expect(comp.scriptTreePaneModel).toBe(treeData);
   });
});

// ---------------------------------------------------------------------------
// Group 3: removeAssembly — container type routing (Risk 3)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — removeAssembly: container routing", () => {
   function makeVs(focused: any[], vsObjects: any[]) {
      const vs = new Viewsheet();
      vs.localId = 1;
      vi.spyOn(vs as any, "getCurrentFocusedAssemblies").mockReturnValue(focused);
      vi.spyOn(vs, "clearFocusedAssemblies").mockImplementation(() => {});
      (vs as any).vsObjects = vsObjects;
      return vs;
   }

   // 🔁 Regression-sensitive: simple (non-contained) assemblies must be removed by name; grouping
   // logic must not accidentally swallow standalone objects.
   it("should remove a standalone assembly directly", async () => {
      const { comp, mocks } = await renderComponent();
      const asm = { absoluteName: "Text1", container: null, objectType: "VSText" } as any;
      const vs = makeVs([asm], [asm]);
      comp.sheets.push(vs);
      comp.openedTabs.push(new ComposerTabModel("viewsheet", vs));
      comp.focusedSheet = vs;

      comp.removeAssembly(asm);

      expect(mocks.composerObjectService.removeObjects).toHaveBeenCalledWith(vs, ["Text1"]);
   });

   // 🔁 Regression-sensitive: selecting a child of a VSGroupContainer must remove the whole group,
   // not just the child; removing only the child leaves an invalid group state on the server.
   it("should remove the entire VSGroupContainer when a child is selected", async () => {
      const { comp, mocks } = await renderComponent();
      const group = { absoluteName: "Group1", objectType: "VSGroupContainer", container: null } as any;
      const child = { absoluteName: "Text1", objectType: "VSText", container: "Group1" } as any;
      const vs = makeVs([child], [group, child]);
      comp.sheets.push(vs);
      comp.openedTabs.push(new ComposerTabModel("viewsheet", vs));
      comp.focusedSheet = vs;

      comp.removeAssembly(child);

      expect(mocks.composerObjectService.removeObjects).toHaveBeenCalledWith(vs, ["Group1"]);
   });

   // 🔁 Regression-sensitive (Bug #12.2 parity): when the model that initiated remove IS a child
   // of the VSSelectionContainer, only that child is removed and the container stays.
   it("should remove only the child and keep the container when model.container matches", async () => {
      const { comp, mocks } = await renderComponent();
      const sc = { absoluteName: "SC1", objectType: "VSSelectionContainer", container: null } as any;
      const child = { absoluteName: "SL1", objectType: "VSSelectionList", container: "SC1" } as any;
      const vs = makeVs([child], [sc, child]);
      comp.sheets.push(vs);
      comp.openedTabs.push(new ComposerTabModel("viewsheet", vs));
      comp.focusedSheet = vs;

      comp.removeAssembly(child);

      const [, names] = mocks.composerObjectService.removeObjects.mock.calls[0];
      expect(names).toContain("SL1");
      expect(names).not.toContain("SC1");
   });

   // 🔁 Regression-sensitive: when the initiating model is NOT a direct child of the focused
   // assembly's container, the entire container is removed (consistent with 12.2 behavior).
   it("should remove the container when model.container does not match", async () => {
      const { comp, mocks } = await renderComponent();
      const sc = { absoluteName: "SC1", objectType: "VSSelectionContainer", container: null } as any;
      const child = { absoluteName: "SL1", objectType: "VSSelectionList", container: "SC1" } as any;
      // model comes from a different container
      const otherModel = { absoluteName: "SL2", objectType: "VSSelectionList", container: "SC2" } as any;
      const vs = makeVs([child], [sc, child]);
      comp.sheets.push(vs);
      comp.openedTabs.push(new ComposerTabModel("viewsheet", vs));
      comp.focusedSheet = vs;

      comp.removeAssembly(otherModel);

      const [, names] = mocks.composerObjectService.removeObjects.mock.calls[0];
      expect(names).toContain("SC1");
      expect(names).not.toContain("SL1");
   });
});

// ---------------------------------------------------------------------------
// Group 4: openAutoSaveAsset via openAutoSaveFiles — WS/VS branching (Risk 3)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — openAutoSaveFiles: auto-save sheet creation", () => {
   // 🔁 Regression-sensitive: the WORKSHEET/VS branch determines which sheet class is pushed; a
   // wrong branch pushes a Viewsheet for a worksheet autosave, losing worksheet-specific features.
   it("should push a Worksheet for a WORKSHEET auto-save file", async () => {
      const { comp } = await renderComponent();
      const before = comp.sheets.length;

      comp.openAutoSaveFiles(["1^WORKSHEET^user1^MyWS"]);

      expect(comp.sheets.length).toBe(before + 1);
      expect(comp.sheets[comp.sheets.length - 1]).toBeInstanceOf(Worksheet);
   });

   it("should push a Viewsheet for a non-WORKSHEET auto-save file", async () => {
      const { comp } = await renderComponent();
      const before = comp.sheets.length;

      comp.openAutoSaveFiles(["1^VIEWSHEET^user1^MyVS"]);

      expect(comp.sheets.length).toBe(before + 1);
      expect(comp.sheets[comp.sheets.length - 1]).toBeInstanceOf(Viewsheet);
   });

   it("should construct the sheet id from a 4-part auto-save path", async () => {
      const { comp } = await renderComponent();

      comp.openAutoSaveFiles(["1^WORKSHEET^user1^MyWS"]);

      const ws = comp.sheets[comp.sheets.length - 1] as Worksheet;
      expect(ws.id).toBe("1^2^user1^MyWS");
   });

   it("should push one sheet per file when multiple files are provided", async () => {
      const { comp } = await renderComponent();
      const before = comp.sheets.length;

      comp.openAutoSaveFiles(["1^WORKSHEET^u^A", "1^VIEWSHEET^u^B"]);

      expect(comp.sheets.length).toBe(before + 2);
   });
});

// ---------------------------------------------------------------------------
// Group 5: recycleAutoSaveFiles — modelService delegation (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — recycleAutoSaveFiles", () => {
   // NOTE: the autoSaveFiles argument is intentionally not asserted here.
   // The current implementation (composer-main.component.ts:589-591) ignores it entirely:
   // it calls getModel with no file list, so ["file1"] is never forwarded to the server.
   // This test accurately reflects that behaviour. If the server needs the list to know
   // which files to delete, that is a separate implementation bug to fix.
   it("should call modelService.getModel with the recycle auto-save URL", async () => {
      const { comp, mocks } = await renderComponent();
      mocks.modelService.getModel.mockClear();

      comp.recycleAutoSaveFiles(["file1"]);

      expect(mocks.modelService.getModel).toHaveBeenCalledWith(
         "../api/composer/viewsheet/recycleAutoSave"
      );
   });
});

// ---------------------------------------------------------------------------
// Group 6: refreshAiAssistantContext — context type routing (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — refreshAiAssistantContext: context routing", () => {
   // 🔁 Regression-sensitive: resetContextMap must always fire to prevent stale AI context
   // entries from prior sheets leaking into the new selection.
   it("should call resetContextMap and no context setter when focusedSheet is null", async () => {
      const { comp, mocks } = await renderComponent();
      (comp as any)._focusedSheet = null;
      mocks.aiAssistantService.resetContextMap.mockClear();

      comp.refreshAiAssistantContext();

      expect(mocks.aiAssistantService.resetContextMap).toHaveBeenCalled();
      expect(mocks.aiAssistantDialogService.setWorksheetContext).not.toHaveBeenCalled();
      expect(mocks.aiAssistantDialogService.setViewsheetScriptContext).not.toHaveBeenCalled();
   });

   it("should call setWorksheetContext when focusedSheet is a Worksheet", async () => {
      const { comp, mocks } = await renderComponent();
      const ws = new Worksheet();
      ws.localId = 1;
      (comp as any)._focusedSheet = ws;

      comp.refreshAiAssistantContext();

      expect(mocks.aiAssistantDialogService.setWorksheetContext).toHaveBeenCalledWith(ws);
      expect(mocks.aiAssistantDialogService.setViewsheetScriptContext).not.toHaveBeenCalled();
   });

   it("should call setViewsheetScriptContext when focusedSheet is a Viewsheet", async () => {
      const { comp, mocks } = await renderComponent();
      const vs = new Viewsheet();
      vs.localId = 1;
      (comp as any)._focusedSheet = vs;

      comp.refreshAiAssistantContext();

      expect(mocks.aiAssistantDialogService.setViewsheetScriptContext).toHaveBeenCalledWith(vs);
      expect(mocks.aiAssistantDialogService.setWorksheetContext).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 7: openScriptOptions — guard and dialog setup (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — openScriptOptions: dialog guard", () => {
   it("should not open a dialog when the focused tab is not a script tab", async () => {
      const { comp } = await renderComponent();
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog");
      const vs = new Viewsheet();
      vs.localId = 1;
      (comp as any)._focusedTab = new ComposerTabModel("viewsheet", vs);

      comp.openScriptOptions();

      expect(showDialogSpy).not.toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: the dialog must be pre-populated with the current comment and font
   // size so the user sees their last-saved values; missing this means the user overwrites their
   // comment with an empty string on every open.
   it("should open a dialog pre-populated with asset comment and scriptFontSize", async () => {
      const { comp } = await renderComponent();
      const dialogInstance = { comment: "", fontSize: 0 };
      vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialogInstance as any);

      const scriptAsset = { type: "script", comment: "// my script", isModified: false } as any;
      (comp as any)._focusedTab = new ComposerTabModel("script", scriptAsset);
      (comp as any).scriptFontSize = 16;

      comp.openScriptOptions();

      expect(dialogInstance.comment).toBe("// my script");
      expect(dialogInstance.fontSize).toBe(16);
   });
});

// ---------------------------------------------------------------------------
// Group 8: checkRemovedAssembly / clearFocusedObjects / worksheetCancel — baseline
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — baseline delegation methods", () => {
   it("should delegate checkRemovedAssembly to clipboardService", async () => {
      const { comp, mocks } = await renderComponent();

      comp.checkRemovedAssembly("Text1");

      expect(mocks.clipboardService.checkRemovedAssembly).toHaveBeenCalledWith("Text1");
   });

   it("should call clearFocusedAssemblies on the focused sheet", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.localId = 1;
      (comp as any)._focusedSheet = vs;
      const clearSpy = vi.spyOn(vs, "clearFocusedAssemblies");

      comp.clearFocusedObjects();

      expect(clearSpy).toHaveBeenCalled();
   });

   it("should send the cancel event on the worksheet socket connection", async () => {
      const { comp } = await renderComponent();
      const ws = new Worksheet();
      const sendEventSpy = vi.fn();
      (ws as any).socketConnection = { sendEvent: sendEventSpy };

      comp.worksheetCancel([ws, 0]);

      expect(sendEventSpy).toHaveBeenCalledWith(
         "/events/composer/ws/join/cancel-ws-join/"
      );
   });
});
