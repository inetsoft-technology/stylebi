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
 * Shared test fixtures for ComposerMainComponent spec files.
 *
 * Imported by the three split spec files (interaction / risk / display) so that
 * makeMocks() and renderComponent() stay in sync across all three suites.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { EMPTY, of, Subject } from "rxjs";

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
import { ComposerMainComponent } from "./composer-main.component";
import { ComposerRecentService } from "./composer-recent.service";
import { EventQueueService } from "./vs/event-queue.service";
import { LineAnchorService } from "../services/line-anchor.service";
import { ResizeHandlerService } from "./resize-handler.service";
import { ScriptService } from "./script/script.service";
import { ComposerObjectService } from "./vs/composer-object.service";

export function makeMocks() {
   return {
      composerClient: { connect: vi.fn(), disconnect: vi.fn(), editAsset: EMPTY },
      clipboardService: {
         clipboardEmpty: false,
         sheetClosed: vi.fn(),
         pasteObjects: vi.fn(),
         addToClipboard: vi.fn(),
         checkRemovedAssembly: vi.fn(),
         checkRenamedAssembly: vi.fn(),
         cutObjects: vi.fn(),
      },
      resizeHandlerService: { onVerticalResizeEnd: vi.fn() },
      modalService: {
         // Rejected result simulates a dismissed modal (NgbModal.dismiss() → reject).
         // Promise.resolve() would invoke onCommit() in ComponentTool.showDialog line 153,
         // triggering cascading side effects in dialog commit callbacks.
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

export type ComposerMainMocks = ReturnType<typeof makeMocks>;

export async function renderComponent(
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
   // Wire ViewChildren that are null under NO_ERRORS_SCHEMA
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
