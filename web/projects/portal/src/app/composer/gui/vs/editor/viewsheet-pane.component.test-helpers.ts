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
 * Shared test helpers for VSPane P1/P2/P3 spec files.
 *
 * Key challenges:
 *   (a) VSPane extends CommandProcessor, which subscribes to ViewsheetClientService.commands
 *       in the constructor.
 *   (b) ngOnInit: vs.newSheet=true && !!!vs.runtimeId → takes the "new" path sending only
 *       a NewViewsheetEvent via sendEvent (no HTTP). vs.autoSaveFile=null → no open-auto-save
 *       event either.
 *   (c) vs.focusedAssemblies is a BehaviorSubject — fires immediately on subscribe with [].
 *       updateFormats() returns early when vs.runtimeId===null, so this is safe.
 *   (d) ViewsheetClientService is declared as a component-level provider in @Component.providers;
 *       we override it via componentProviders in render().
 *
 * Strategy:
 *   - ViewsheetClientService mock exposes a Subject<ViewsheetCommandMessage> so tests can
 *     dispatch STOMP commands synchronously.
 *   - All subscriptions on the client use EMPTY / Subject so tests are hermetic.
 *   - Viewsheet is constructed with the real class (new Viewsheet()) so BehaviorSubject
 *     subscriptions work naturally.
 *   - @ViewChild refs wired manually after render (NO_ERRORS_SCHEMA means no real template
 *     children are rendered).
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { EMPTY, of, Subject } from "rxjs";

import { AiAssistantService } from "../../../../../../../shared/ai-assistant/ai-assistant.service";
import { AiAssistantDialogService } from "../../../../common/services/ai-assistant-dialog.service";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import { DownloadService } from "../../../../../../../shared/download/download.service";
import { ViewsheetClientService } from "../../../../common/viewsheet-client/viewsheet-client.service";
import { ViewsheetCommandMessage } from "../../../../common/viewsheet-client/viewsheet-command-message";
import { AssemblyActionFactory } from "../../../../vsobjects/action/assembly-action-factory.service";
import { DataTipService } from "../../../../vsobjects/objects/data-tip/data-tip.service";
import { ChatService } from "../../../../common/chat/chat.service";
import { ModelService } from "../../../../widget/services/model.service";
import { DragService } from "../../../../widget/services/drag.service";
import { ScaleService } from "../../../../widget/services/scale/scale-service";
import { DebounceService } from "../../../../widget/services/debounce.service";
import { DomService } from "../../../../widget/dom-service/dom.service";
import { FontService } from "../../../../widget/services/font.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { VSBindingTreeService } from "../../../../binding/widget/binding-tree/vs-binding-tree.service";
import { ResizeHandlerService } from "../../resize-handler.service";
import { ComposerObjectService } from "../composer-object.service";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { VSPane } from "./viewsheet-pane.component";

// ---------------------------------------------------------------------------
// ViewsheetClientService mock factory
// ---------------------------------------------------------------------------

export function makeVsClientMock() {
   const commandSubject = new Subject<ViewsheetCommandMessage>();
   const mock = {
      commands: commandSubject.asObservable(),
      connect: vi.fn(),
      sendEvent: vi.fn(),
      disconnect: vi.fn(),
      onHeartbeat: EMPTY,
      connectionError: vi.fn().mockReturnValue(of(null)),
      onRenameTransformFinished: EMPTY,
      onTransformFinished: EMPTY,
      runtimeId: null as string | null,
      focusedLayoutName: "Master",
      lastModified: 0,
   };
   return { mock, commandSubject };
}

// ---------------------------------------------------------------------------
// Viewsheet factory — newSheet=true, runtimeId=null → no HTTP in ngOnInit
// ---------------------------------------------------------------------------

export function makeViewsheet(): Viewsheet {
   const vs = new Viewsheet();
   vs.id = "1^1^__NULL^TestVS";
   vs.runtimeId = null;
   vs.newSheet = true;
   vs.autoSaveFile = null;
   vs.label = "Test Viewsheet";
   vs.snapGrid = 20;
   vs.layouts = [];
   vs.scale = 1;
   vs.linkUri = "/";
   vs.isFocused = true;
   return vs;
}

// ---------------------------------------------------------------------------
// Central mocks factory
// ---------------------------------------------------------------------------

export function makeMocks() {
   const { mock: vsClient, commandSubject } = makeVsClientMock();

   return {
      vsClient,
      commandSubject,
      dispatchCommand: (type: string, command: any) =>
         commandSubject.next(new ViewsheetCommandMessage(null, type, command)),
      vs: makeViewsheet(),
      notifications: {
         success: vi.fn(),
         info: vi.fn(),
         warning: vi.fn(),
         danger: vi.fn(),
      },
      dialogService: {
         setSheetId: vi.fn(),
         objectDelete: vi.fn(),
         objectRename: vi.fn(),
         open: vi.fn().mockReturnValue({
            result: new Promise<never>((_, reject) => reject("cancel")),
            componentInstance: {},
         }),
      },
      modelService: {
         getModel: vi.fn().mockReturnValue(of(null)),
         sendModel: vi.fn().mockReturnValue(of({ body: null })),
      },
      modalService: {
         open: vi.fn().mockReturnValue({
            result: new Promise<never>((_, reject) => reject("cancel")),
            componentInstance: {},
         }),
      },
      composerObjectService: {
         updateLayerMovement: vi.fn(),
         removeObjectFromList: vi.fn(),
         getObjectDefaultSize: vi.fn().mockReturnValue({ width: 100, height: 50 }),
         getDataSource: vi.fn().mockReturnValue(null),
         addNewObject: vi.fn(),
         moveFromContainer: vi.fn(),
         applyChangeBinding: vi.fn(),
         handleKeyEvent: vi.fn(),
      },
      aiAssistantDialogService: {
         setViewsheetScriptContext: vi.fn(),
      },
   };
}

export type VSPaneMocks = ReturnType<typeof makeMocks>;

// ---------------------------------------------------------------------------
// renderComponent helper
// ---------------------------------------------------------------------------

export async function renderComponent(mocks = makeMocks()) {
   const { fixture } = await render(VSPane, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      componentProperties: {
         vs: mocks.vs,
         active: true,
      },
      // Override component-level providers declared in @Component.providers
      componentProviders: [
         { provide: ViewsheetClientService, useValue: mocks.vsClient },
         { provide: DialogService, useValue: mocks.dialogService },
         { provide: ChatService, useValue: { closeSession: vi.fn() } },
         { provide: DataTipService, useValue: { clearDataTips: vi.fn() } },
         { provide: AssemblyActionFactory, useValue: { stateProvider: null } },
         { provide: DebounceService, useValue: { debounce: vi.fn() } },
      ],
      providers: [
         { provide: AppInfoService, useValue: {
            getCurrentOrgInfo: vi.fn().mockReturnValue(of({ key: "org1", value: "Org1" })),
         }},
         { provide: AiAssistantService, useValue: {} },
         { provide: AiAssistantDialogService, useValue: mocks.aiAssistantDialogService },
         { provide: NgbModal, useValue: mocks.modalService },
         { provide: ModelService, useValue: mocks.modelService },
         { provide: DragService, useValue: {
            registerDragDataListener: vi.fn().mockReturnValue(EMPTY),
            getDragData: vi.fn().mockReturnValue({}),
            put: vi.fn(),
         }},
         { provide: DownloadService, useValue: { download: vi.fn() } },
         { provide: ScaleService, useValue: { setScale: vi.fn() } },
         { provide: UIContextService, useValue: { objectRenamed: vi.fn() } },
         { provide: VSBindingTreeService, useValue: { resetTreeModel: vi.fn() } },
         { provide: ComposerObjectService, useValue: mocks.composerObjectService },
         { provide: ResizeHandlerService, useValue: { anyResizeSubject: EMPTY } },
         { provide: DomService, useValue: { requestRead: vi.fn() } },
         { provide: FontService, useValue: { defaultFont: "Roboto" } },
         provideHttpClient(),
      ],
   });

   const comp = fixture.componentInstance as VSPane;

   // Wire @ViewChild refs that are null under NO_ERRORS_SCHEMA
   (comp as any).notifications = mocks.notifications;
   (comp as any).variableInputDialog = null;
   (comp as any).consoleDialog = null;
   (comp as any).interactContainer = {
      snap: vi.fn().mockImplementation((pt: any) => pt),
      snapGridSize: 20,
   };

   return { fixture, comp, mocks };
}
