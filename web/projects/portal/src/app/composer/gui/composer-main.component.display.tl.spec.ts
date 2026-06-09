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
 * ComposerMainComponent — Pass 3: Display
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — showLinkVSInTab + getLinkVSLabel: hyperlink subject emission pushes a
 *                       Viewsheet with linkview=true, closeOnServer=true, parentSheet=focusedSheet,
 *                       and label extracted as the last path segment before the org-id suffix
 *   Group 2 [Risk 2] — updateFormat: no-layout path sends event via focusedSheet.socketConnection;
 *                       layout path sends via layout.socketConnection; null model → event.reset=true
 *   Group 3 [Risk 2] — layoutFormatObjects: all-editable focused objects → returns objectModels;
 *                       any non-editable object present → returns empty array
 *   Group 4 [baseline] — openFormatPane sets selectedTab to FORMAT; showPaste mirrors
 *                         clipboardService.clipboardEmpty; layoutShowing reflects currentLayout
 *
 * HTTP: N/A — all paths tested via direct method calls; no HTTP in Pass 3 methods
 *
 * Out of scope this pass: all methods covered in Pass 1 and Pass 2
 */

import "@angular/compiler";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { EMPTY, Subject } from "rxjs";
import { of } from "rxjs";

import { AiAssistantService } from "../../../../../shared/ai-assistant/ai-assistant.service";
import { AiAssistantDialogService } from "../../common/services/ai-assistant-dialog.service";
import { FullScreenService } from "../../common/services/full-screen.service";
import { UIContextService } from "../../common/services/ui-context.service";
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
import { ComposerMainComponent, SidebarTab } from "./composer-main.component";
import { ComposerRecentService } from "./composer-recent.service";
import { ComposerTabModel } from "./composer-tab-model";
import { EventQueueService } from "./vs/event-queue.service";
import { LineAnchorService } from "../services/line-anchor.service";
import { ResizeHandlerService } from "./resize-handler.service";
import { ScriptService } from "./script/script.service";
import { ComposerObjectService } from "./vs/composer-object.service";
import { Viewsheet } from "../data/vs/viewsheet";

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
// Group 1: showLinkVSInTab + getLinkVSLabel — sheet creation and label (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — showLinkVSInTab: link sheet creation", () => {
   // 🔁 Regression-sensitive: linkview and closeOnServer flags drive server-side linked-VS
   // lifecycle; if either is missing the server opens an independent VS instead of a linked one.
   it("should push a Viewsheet with linkview=true and closeOnServer=true", async () => {
      const { comp, mocks } = await renderComponent();

      mocks.hyperLinkService.showLinkSheetSubject.next({
         id: "1^128^user^folder^MyVS^orgId",
         queryParameters: new Map(),
      });

      const vs = comp.sheets[comp.sheets.length - 1] as Viewsheet;
      expect(vs.linkview).toBe(true);
      expect(vs.closeOnServer).toBe(true);
   });

   // 🔁 Regression-sensitive: parentSheet wires the linked VS back to its parent for server
   // disposal; if null is stored the server cannot locate the parent to clean up on close.
   it("should set parentSheet to the focusedSheet at the time of emission", async () => {
      const { comp, mocks } = await renderComponent();
      const parent = new Viewsheet();
      parent.localId = 1;
      parent.runtimeId = "parent-rt";
      (comp as any)._focusedSheet = parent;

      mocks.hyperLinkService.showLinkSheetSubject.next({
         id: "1^128^user^MyVS^orgId",
         queryParameters: new Map(),
      });

      const vs = comp.sheets[comp.sheets.length - 1] as Viewsheet;
      expect(vs.parentSheet).toBe(parent);
   });

   // 🔁 Regression-sensitive: getLinkVSLabel strips the trailing org-id segment and returns the
   // asset name; a wrong label shows a confusing internal path in the tab title.
   it("should derive the VS label as the last path segment before the org-id", async () => {
      const { comp, mocks } = await renderComponent();

      mocks.hyperLinkService.showLinkSheetSubject.next({
         id: "1^128^user^folder^MyReport^orgId",
         queryParameters: new Map(),
      });

      const vs = comp.sheets[comp.sheets.length - 1] as Viewsheet;
      expect(vs.label).toBe("MyReport");
   });

   it("should extract the label correctly for a short two-part path before org-id", async () => {
      const { comp, mocks } = await renderComponent();

      mocks.hyperLinkService.showLinkSheetSubject.next({
         id: "1^128^user^Dashboard^orgId",
         queryParameters: new Map(),
      });

      const vs = comp.sheets[comp.sheets.length - 1] as Viewsheet;
      expect(vs.label).toBe("Dashboard");
   });
});

// ---------------------------------------------------------------------------
// Group 2: updateFormat — dispatch path (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — updateFormat: socketConnection dispatch", () => {
   function makeVsWithSocket() {
      const vs = new Viewsheet();
      vs.localId = 1;
      const sendEventSpy = vi.fn();
      (vs as any).socketConnection = { sendEvent: sendEventSpy };
      (vs as any).origFormat = null;
      vi.spyOn(vs as any, "getCurrentFocusedAssemblies").mockReturnValue([]);
      return { vs, sendEventSpy };
   }

   // 🔁 Regression-sensitive: updateFormat must send to focusedSheet.socketConnection when no
   // layout is active; sending to the wrong connection silently drops the format change.
   it("should send the format event via focusedSheet.socketConnection when no layout is active", async () => {
      const { comp } = await renderComponent();
      const { vs, sendEventSpy } = makeVsWithSocket();
      (comp as any)._focusedSheet = vs;

      comp.updateFormat({} as any);

      expect(sendEventSpy).toHaveBeenCalledWith(
         "/events/composer/viewsheet/format",
         expect.anything()
      );
   });

   // 🔁 Regression-sensitive: when model is null the event.reset flag must be true so the server
   // reverts to the default format; if reset stays false the server applies an empty format instead.
   it("should set event.reset=true when model is null", async () => {
      const { comp } = await renderComponent();
      const { vs, sendEventSpy } = makeVsWithSocket();
      (comp as any)._focusedSheet = vs;

      comp.updateFormat(null as any);

      const event = sendEventSpy.mock.calls[0][1];
      expect(event.reset).toBe(true);
   });

   it("should send the format event via layout.socketConnection when a layout is active", async () => {
      const { comp } = await renderComponent();
      const layoutSendSpy = vi.fn();
      const vs = new Viewsheet();
      vs.localId = 1;
      (vs as any).currentLayout = {
         origFormat: null,
         currentPrintSection: 0,
         focusedObjects: [],
         socketConnection: { sendEvent: layoutSendSpy },
      };
      (vs as any).origFormat = null;
      vi.spyOn(vs as any, "getCurrentFocusedAssemblies").mockReturnValue([]);
      (comp as any)._focusedSheet = vs;

      comp.updateFormat({} as any);

      expect(layoutSendSpy).toHaveBeenCalledWith(
         "/events/composer/viewsheet/format",
         expect.anything()
      );
   });
});

// ---------------------------------------------------------------------------
// Group 3: layoutFormatObjects — editable filter display (Risk 2)
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — layoutFormatObjects: editable-only filter", () => {
   // 🔁 Regression-sensitive: format controls must only appear when ALL focused objects are
   // editable; showing format pane for a non-editable object lets the user apply formats that
   // the server silently ignores, causing confusion.
   it("should return objectModels when all focused objects are editable", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.localId = 1;
      (vs as any).currentLayout = {
         focusedObjects: [
            { editable: true, objectModel: { absoluteName: "T1" } },
            { editable: true, objectModel: { absoluteName: "T2" } },
         ],
      };
      (comp as any)._focusedSheet = vs;

      const result = comp.layoutFormatObjects;

      expect(result).toHaveLength(2);
      expect(result.map((o: any) => o.absoluteName)).toEqual(["T1", "T2"]);
   });

   it("should return an empty array when any focused object is not editable", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.localId = 1;
      (vs as any).currentLayout = {
         focusedObjects: [
            { editable: true, objectModel: { absoluteName: "T1" } },
            { editable: false, objectModel: { absoluteName: "T2" } },
         ],
      };
      (comp as any)._focusedSheet = vs;

      expect(comp.layoutFormatObjects).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 4: openFormatPane / showPaste / layoutShowing — baseline display
// ---------------------------------------------------------------------------

describe("ComposerMainComponent — baseline display flags", () => {
   it("should set selectedTab to FORMAT when openFormatPane is called", async () => {
      const { comp } = await renderComponent();

      comp.openFormatPane({} as any);

      expect(comp.selectedTab).toBe(SidebarTab.FORMAT);
   });

   it("should return true for showPaste when clipboardEmpty is false", async () => {
      const mocks = makeMocks();
      mocks.clipboardService.clipboardEmpty = false;
      const { comp } = await renderComponent({}, mocks);

      expect(comp.showPaste).toBe(true);
   });

   it("should return false for showPaste when clipboardEmpty is true", async () => {
      const mocks = makeMocks();
      mocks.clipboardService.clipboardEmpty = true;
      const { comp } = await renderComponent({}, mocks);

      expect(comp.showPaste).toBe(false);
   });

   // 🔁 Regression-sensitive: layoutShowing controls whether the layout-specific toolbar and
   // format pane are shown; if it reads focusedSheet directly instead of focusedViewsheet the
   // check fails for worksheet sheets and the layout toolbar appears on worksheets.
   it("should report layoutShowing=true when the focused viewsheet has a currentLayout", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.localId = 1;
      (vs as any).currentLayout = { name: "DeviceLayout", printLayout: false };
      (comp as any)._focusedSheet = vs;

      expect((comp as any).layoutShowing).toBe(true);
   });

   it("should report layoutShowing=false when the focused viewsheet has no currentLayout", async () => {
      const { comp } = await renderComponent();
      const vs = new Viewsheet();
      vs.localId = 1;
      (comp as any)._focusedSheet = vs;

      expect((comp as any).layoutShowing).toBe(false);
   });
});
