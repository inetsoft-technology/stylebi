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
 * Regression test for bug #76418: ComposerMainComponent used to declare its own
 * component-scoped `ResizeHandlerService` provider, which shadowed the composer
 * route's provider for ComposerMainComponent and its whole descendant subtree
 * (WSPaneComponent -> WSDetailsPaneComponent -> WSDetailsTableDataComponent, etc).
 * Because only the route-scoped instance ever had initListeners() called on it
 * (in ComposerAppComponent.ngOnInit()), the shadowed instance used by the
 * descendants never received real `window: resize` events, so tables never
 * re-rendered their virtualized column range after a browser maximize/restore.
 *
 * IMPORTANT — why this file does NOT use composer-main.spec-helpers.ts's
 * renderComponent(): that helper calls `testBed.overrideComponent(ComposerMainComponent,
 * { set: { providers: [...] } })`, and Angular's `set` *fully replaces* the
 * @Component decorator's providers array metadata for the test — it does not read
 * or reflect whatever the real composer-main.component.ts source file actually
 * declares. Its override list never includes ResizeHandlerService, so a test built
 * on that helper would pass identically whether or not the real source still
 * declares the duplicate provider — i.e. it would not actually catch this
 * regression. (This was confirmed empirically in review: reintroducing the bug in
 * composer-main.component.ts's real providers array did not fail a test built on
 * that helper.)
 *
 * Instead, this file uses `remove`/`add` on `overrideComponent`, which Angular
 * applies to the REAL, currently-compiled providers array: `remove` filters out
 * entries that structurally match the given list (compared by provider identity,
 * not by array position), and `add` appends replacements — critically, entries
 * NOT listed in `remove` pass through untouched from the real source. Every
 * component-level provider ComposerMainComponent declares *other than*
 * ResizeHandlerService is listed in `remove` (with a mock substituted via `add`,
 * so the component can still be constructed without needing their real
 * dependencies). `ResizeHandlerService` is deliberately left out of both lists, so
 * whatever composer-main.component.ts's real @Component metadata currently
 * declares for it — present (bug) or absent (fixed) — is exactly what participates
 * in this test's DI resolution.
 */

import "@angular/compiler";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { HttpClient, provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { EMPTY as EMPTY_OBS } from "rxjs";

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
import { VSScaleService } from "../../widget/services/scale/vs-scale.service";
import { CheckFormDataService } from "../../vsobjects/util/check-form-data.service";
import { RichTextService } from "../../vsobjects/dialog/rich-text-dialog/rich-text.service";
import { CKEditorRichTextService } from "../../vsobjects/dialog/rich-text-dialog/ckeditor-rich-text.service";
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
import { EventQueueService } from "./vs/event-queue.service";
import { LineAnchorService } from "../services/line-anchor.service";
import { ResizeHandlerService } from "./resize-handler.service";
import { ScriptService } from "./script/script.service";
import { ComposerObjectService } from "./vs/composer-object.service";

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

async function renderWithRealProvidersArray() {
   vi.spyOn(GuiTool, "isTouchDevice").mockResolvedValue(false);

   const mocks = {
      composerClient: { connect: vi.fn(), disconnect: vi.fn(), editAsset: EMPTY_OBS },
      clipboardService: {
         clipboardEmpty: false,
         sheetClosed: vi.fn(),
         pasteObjects: vi.fn(),
         addToClipboard: vi.fn(),
         checkRemovedAssembly: vi.fn(),
         checkRenamedAssembly: vi.fn(),
         cutObjects: vi.fn(),
      },
      resizeHandlerService: { onVerticalResizeEnd: vi.fn(), onHorizontalDrag: vi.fn(), initListeners: vi.fn() },
      modalService: { open: vi.fn() },
      modelService: { getModel: vi.fn(() => EMPTY_OBS), sendModel: vi.fn(() => EMPTY_OBS), errorHandler: null as any },
      gettingStartedService: {
         editSheet: EMPTY_OBS,
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
      hyperLinkService: { showLinkSheetSubject: EMPTY_OBS },
      assetTreeService: { loadAssetTreeSubject: EMPTY_OBS },
      uiContextService: {
         sheetHide: vi.fn(), sheetShow: vi.fn(), sheetClose: vi.fn(),
         isVS: vi.fn(), isAdhoc: vi.fn(() => false),
      },
      composerRecentService: { addRecentlyViewed: vi.fn(), updateRecentlyViewed: vi.fn() },
      composerObjectService: {
         removeObjects: vi.fn(), sendToFarthestIndex: vi.fn(), shiftLayerIndex: vi.fn(),
      },
      router: { navigate: vi.fn(), events: EMPTY_OBS },
      aiAssistantService: {
         loadCurrentUser: vi.fn(), aiAssistantVisible: false,
         resetContextMap: vi.fn(), setContextTypeFieldValue: vi.fn(),
      },
      aiAssistantDialogService: { setWorksheetContext: vi.fn(), setViewsheetScriptContext: vi.fn() },
   };

   const result = await render(ComposerMainComponent, {
      componentProperties: { deployed: true },
      componentImports: [],
      configureTestBed: testBed => {
         testBed.overrideComponent(ComposerMainComponent, {
            // Only remove/replace the 16 component-level providers that are NOT
            // ResizeHandlerService. ResizeHandlerService is deliberately absent from
            // both remove and add, so whatever the real @Component metadata currently
            // declares for it passes through untouched into the test.
            remove: {
               providers: [
                  ComposerClientService,
                  { provide: ScaleService, useClass: VSScaleService },
                  ComposerObjectService,
                  EventQueueService,
                  LineAnchorService,
                  ClipboardService,
                  ScriptService,
                  ShowHyperlinkService,
                  MiniToolbarService,
                  VSTabService,
                  SelectionMobileService,
                  FormInputService,
                  GlobalSubmitService,
                  CheckFormDataService,
                  FullScreenService,
                  { provide: RichTextService, useClass: CKEditorRichTextService, deps: [FontService, NgbModal, HttpClient] },
               ],
            },
            add: {
               providers: [
                  { provide: ComposerClientService, useValue: mocks.composerClient },
                  { provide: ScaleService, useValue: {} },
                  { provide: ComposerObjectService, useValue: mocks.composerObjectService },
                  { provide: EventQueueService, useValue: {} },
                  { provide: LineAnchorService, useValue: {} },
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
            },
         });
      },
      providers: [
         // Module ("route") level, mirroring composer.routes.ts — this is what
         // ComposerMainComponent's subtree should fall back to when it does NOT
         // shadow ResizeHandlerService with its own component-level provider.
         { provide: ResizeHandlerService, useValue: mocks.resizeHandlerService },
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

   return { comp, mocks };
}

describe("ComposerMainComponent — ResizeHandlerService DI resolution (bug #76418)", () => {
   it("resolves the same ResizeHandlerService instance provided by an ancestor injector", async () => {
      const { comp } = await renderWithRealProvidersArray();

      const ancestorInstance = TestBed.inject(ResizeHandlerService);

      expect((comp as any).resizeHandlerService).toBe(ancestorInstance);
   });

   it("routes onSplitDragEnd's resize notification through the ancestor-provided instance", async () => {
      const { comp, mocks } = await renderWithRealProvidersArray();

      comp.onSplitDragEnd(null);

      expect(mocks.resizeHandlerService.onVerticalResizeEnd).toHaveBeenCalled();
   });
});
