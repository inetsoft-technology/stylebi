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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { EMPTY, Subject, of } from "rxjs";

import { AiAssistantService, ContextType } from "../../../../../shared/ai-assistant/ai-assistant.service";
import { AiAssistantDialogService } from "../../common/services/ai-assistant-dialog.service";
import { FullScreenService } from "../../common/services/full-screen.service";
import { UIContextService } from "../../common/services/ui-context.service";
import { ComponentTool } from "../../common/util/component-tool";
import { GuiTool } from "../../common/util/gui-tool";
import { AssetTreeService } from "../../widget/asset-tree/asset-tree.service";
import { GettingStartedService } from "../../widget/dialog/getting-started-dialog/service/getting-started.service";
import { FontService } from "../../widget/services/font.service";
import { ModelService } from "../../widget/services/model.service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { CheckFormDataService } from "../../vsobjects/util/check-form-data.service";
import { RichTextService } from "../../vsobjects/dialog/rich-text-dialog/rich-text.service";
import { FormInputService } from "../../vsobjects/util/form-input.service";
import { GlobalSubmitService } from "../../vsobjects/util/global-submit.service";
import { MiniToolbarService } from "../../vsobjects/objects/mini-toolbar/mini-toolbar.service";
import { SelectionMobileService } from "../../vsobjects/objects/selection/services/selection-mobile.service";
import { ShowHyperlinkService } from "../../vsobjects/show-hyperlink.service";
import { VSTabService } from "../../vsobjects/util/vs-tab.service";
import { ClipboardService } from "./clipboard.service";
import { ComposerClientService } from "./composer-client.service";
import { ComposerMainComponent } from "./composer-main.component";
import { ComposerRecentService } from "./composer-recent.service";
import { ComposerTabModel } from "./composer-tab-model";
import { EventQueueService } from "./vs/event-queue.service";
import { LineAnchorService } from "../services/line-anchor.service";
import { ResizeHandlerService } from "./resize-handler.service";
import { ScriptService } from "./script/script.service";
import { ComposerObjectService } from "./vs/composer-object.service";
import { Viewsheet } from "../data/vs/viewsheet";
import { Worksheet } from "../data/ws/worksheet";
import { loadingScriptTreePaneModel } from "../data/script/script-tree-pane-model";

// ---------------------------------------------------------------------------
// Global setup
// ---------------------------------------------------------------------------

beforeAll(() => {
   (window as any).BroadcastChannel = class {
      onmessage: any = null;
      postMessage() {}
      close() {}
      addEventListener() {}
      removeEventListener() {}
   };
});

function makeMocks() {
   return {
      composerClient: { connect: vi.fn(), disconnect: vi.fn(), editAsset: EMPTY },
      clipboardService: {
         clipboardEmpty: false,
         sheetClosed: vi.fn(),
         pasteObjects: vi.fn(),
         addToClipboard: vi.fn(),
         checkRemovedAssembly: vi.fn(),
         cutObjects: vi.fn(),
      },
      resizeHandlerService: { onVerticalResizeEnd: vi.fn() },
      modalService: {
         open: vi.fn().mockReturnValue({
            result: new Promise<never>((_, reject) => reject("cancel")),
            componentInstance: {
               onCommit: { subscribe: vi.fn(() => ({ unsubscribe: vi.fn() })) },
               onCancel: { subscribe: vi.fn(() => ({ unsubscribe: vi.fn() })) },
            },
         }),
      },
      modelService: {
         getModel: vi.fn(() => of(null)),
         sendModel: vi.fn(() => of({ body: null })),
         errorHandler: null as any,
      },
      gettingStartedService: {
         editSheet: new Subject<any>(),
         isEditWs: vi.fn(() => false),
         isCreateDashboard: vi.fn(() => false),
         isStartFromScratch: vi.fn(() => false),
         isProcessing: vi.fn(() => false),
         isUploadFile: vi.fn(() => false),
         isCreateQuery: vi.fn(() => false),
         continue: vi.fn(),
         finish: vi.fn(),
         getWorksheetId: vi.fn(),
         setWorksheetId: vi.fn(),
         finished: false,
         openVsOnPortal: vi.fn(),
      },
      hyperLinkService: { showLinkSheetSubject: new Subject<any>() },
      assetTreeService: { loadAssetTreeSubject: new Subject<any>() },
      uiContextService: {
         sheetHide: vi.fn(),
         sheetShow: vi.fn(),
         sheetClose: vi.fn(),
         isVS: vi.fn(),
         isAdhoc: vi.fn(() => false),
      },
      composerRecentService: {
         addRecentlyViewed: vi.fn(),
         updateRecentlyViewed: vi.fn(),
      },
      composerObjectService: {
         removeObjects: vi.fn(),
         sendToFarthestIndex: vi.fn(),
         shiftLayerIndex: vi.fn(),
      },
      router: { navigate: vi.fn(), events: EMPTY },
      aiAssistantService: {
         loadCurrentUser: vi.fn(),
         aiAssistantVisible: false,
         resetContextMap: vi.fn(),
         setContextTypeFieldValue: vi.fn(),
      },
      aiAssistantDialogService: {
         setWorksheetContext: vi.fn(),
         setViewsheetScriptContext: vi.fn(),
      },
   };
}

async function renderComponent(
   componentProperties: Record<string, any> = {},
   mocks = makeMocks()
) {
   vi.spyOn(GuiTool, "isTouchDevice").mockResolvedValue(false);

   const result = await render(ComposerMainComponent, {
      componentProperties: { deployed: true, ...componentProperties },
      componentImports: [],
      componentProviders: [
         { provide: ComposerClientService, useValue: mocks.composerClient },
         { provide: ScaleService, useValue: {} },
         { provide: ComposerObjectService, useValue: mocks.composerObjectService },
         { provide: EventQueueService, useValue: {} },
         { provide: LineAnchorService, useValue: {} },
         { provide: ResizeHandlerService, useValue: mocks.resizeHandlerService },
         { provide: ClipboardService, useValue: mocks.clipboardService },
         { provide: ScriptService, useValue: {} },
         { provide: ShowHyperlinkService, useValue: mocks.hyperLinkService },
         { provide: MiniToolbarService, useValue: {} },
         { provide: VSTabService, useValue: {} },
         { provide: SelectionMobileService, useValue: {} },
         { provide: FormInputService, useValue: {} },
         { provide: GlobalSubmitService, useValue: {} },
         { provide: CheckFormDataService, useValue: {} },
         { provide: FullScreenService, useValue: {} },
         { provide: RichTextService, useValue: {} },
      ],
      providers: [
         { provide: NgbModal, useValue: mocks.modalService },
         { provide: ModelService, useValue: mocks.modelService },
         { provide: UIContextService, useValue: mocks.uiContextService },
         { provide: GettingStartedService, useValue: mocks.gettingStartedService },
         { provide: AssetTreeService, useValue: mocks.assetTreeService },
         { provide: ComposerRecentService, useValue: mocks.composerRecentService },
         { provide: FontService, useValue: { defaultFont: "Roboto" } },
         { provide: Router, useValue: mocks.router },
         { provide: AiAssistantService, useValue: mocks.aiAssistantService },
         { provide: AiAssistantDialogService, useValue: mocks.aiAssistantDialogService },
         provideHttpClient(),
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   const comp = result.fixture.componentInstance as ComposerMainComponent;
   (comp as any).notifications = {
      success: vi.fn(), info: vi.fn(), warning: vi.fn(), danger: vi.fn(),
   };
   (comp as any).splitPane = {
      getSizes: vi.fn(() => [25, 75]),
      setSizes: vi.fn(),
      collapse: vi.fn(),
   };
   return { fixture: result.fixture, comp, mocks };
}

afterEach(() => {
   vi.restoreAllMocks();
   localStorage.clear();
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
   it("should call modelService.getModel with the recycle auto-save URL", async () => {
      const { comp, mocks } = await renderComponent();
      mocks.modelService.getModel.mockClear();

      comp.recycleAutoSaveFiles(["file1"]);

      expect(mocks.modelService.getModel).toHaveBeenCalledWith(
         expect.stringContaining("recycleAutoSave")
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
         expect.stringContaining("cancel-ws-join")
      );
   });
});
