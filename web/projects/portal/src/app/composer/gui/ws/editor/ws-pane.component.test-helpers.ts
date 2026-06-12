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
 * Shared test helpers for ws-pane.component P1/P2/P3 spec files.
 *
 * The key challenge is that WSPaneComponent:
 *   (a) extends CommandProcessor, which subscribes to ViewsheetClientService.commands
 *       in the constructor, and
 *   (b) in ngOnInit wires worksheet.socketConnection, calls setup() (connect + heartbeat),
 *       openWorksheet(), and initDragAssetColumnsListener().
 *
 * Strategy:
 *   - worksheet.newSheet=true so openWorksheet() issues only a sendEvent (no HTTP call).
 *   - ViewsheetClientService mock exposes a Subject<ViewsheetCommandMessage> via `.commands`
 *     so tests can dispatch STOMP-style commands synchronously.
 *   - All subscriptions use EMPTY / Subject so tests are hermetic.
 */

import { NO_ERRORS_SCHEMA, DOCUMENT } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { EMPTY, of, Subject } from "rxjs";

import { AiAssistantService } from "../../../../../../../shared/ai-assistant/ai-assistant.service";
import { AiAssistantDialogService } from "../../../../common/services/ai-assistant-dialog.service";
import { ViewsheetClientService } from "../../../../common/viewsheet-client/viewsheet-client.service";
import { ViewsheetCommandMessage } from "../../../../common/viewsheet-client/viewsheet-command-message";
import { ModelService } from "../../../../widget/services/model.service";
import { DragService } from "../../../../widget/services/drag.service";
import { DownloadService } from "../../../../../../../shared/download/download.service";
import { DebounceService } from "../../../../widget/services/debounce.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { ComposerToolbarService } from "../../composer-toolbar.service";
import { ResizeHandlerService } from "../../resize-handler.service";
import { GettingStartedService } from "../../../../widget/dialog/getting-started-dialog/service/getting-started.service";
import { WsChangeService } from "./ws-change.service";
import { WSPaneComponent } from "./ws-pane.component";
import { Worksheet } from "../../../data/ws/worksheet";
import { AssetType } from "../../../../../../../shared/data/asset-type";
import { AssetTreeService } from "../../../../widget/asset-tree/asset-tree.service";

// ---------------------------------------------------------------------------
// Commands Subject — exposed so tests can dispatch STOMP commands
// ---------------------------------------------------------------------------

export let commandSubject: Subject<ViewsheetCommandMessage>;

/** Dispatches a global STOMP command to the component under test. */
export function dispatchCommand(type: string, command: any): void {
   commandSubject.next(new ViewsheetCommandMessage(null, type, command));
}

// ---------------------------------------------------------------------------
// ViewsheetClientService mock factory (re-created per test suite)
// ---------------------------------------------------------------------------

export function makeWsClientMock() {
   commandSubject = new Subject<ViewsheetCommandMessage>();

   return {
      commands: commandSubject.asObservable(),
      connect: vi.fn(),
      sendEvent: vi.fn(),
      disconnect: vi.fn(),
      onHeartbeat: EMPTY,
      connectionError: vi.fn().mockReturnValue(of(null)),
      onRenameTransformFinished: EMPTY,
      onTransformFinished: EMPTY,
      runtimeId: "test-ws-rt",
      setSheetId: vi.fn(),
   };
}

// ---------------------------------------------------------------------------
// DragService mock — registerDragDataListener must return an Observable
// ---------------------------------------------------------------------------

export function makeDragServiceMock() {
   return {
      registerDragDataListener: vi.fn().mockReturnValue(EMPTY),
      dragEndSubject: new Subject<void>(),
      disposeDragDataListener: vi.fn(),
      put: vi.fn(),
      getDragData: vi.fn().mockReturnValue({}),
   };
}

// ---------------------------------------------------------------------------
// Worksheet factory — newSheet=true avoids HTTP in openWorksheet()
// ---------------------------------------------------------------------------

export function makeWorksheet(overrides: Partial<any> = {}): any {
   const ws: any = {
      socketConnection: null,
      runtimeId: "ws-rt1",
      id: "ws-1",
      label: "Test Worksheet",
      tables: [],
      variables: [],
      groupings: [],
      loading: false,
      saving: false,
      newSheet: true,          // ← skip openExistingWorksheet() HTTP path
      autoSaveFile: null,
      init: true,
      gettingStarted: false,
      singleQuery: false,
      closeProhibited: false,
      messageLevels: {},
      hasVPMPrincipal: false,
      autoSaveTS: 0,
      currentTS: 0,
      jspAssemblyGraph: { setSuspendDrawing: vi.fn() },
      jspSchemaGraph: null,
      currentFocusedAssemblies: [],
      focusedTable: null,
      selectedCompositeTable: null,
      points: 3,
      current: 1,
      savePoint: 1,
      callBackFun: null,
      clearFocusedAssemblies: vi.fn(),
      isModified: vi.fn().mockReturnValue(false),
      isAssemblyFocused: vi.fn().mockReturnValue(false),
      replaceFocusedAssembly: vi.fn(),
      deselectAssembly: vi.fn(),
      deselectAllAssemblies: vi.fn(),
      selectOnlyAssembly: vi.fn(),
      selectAssembly: vi.fn(),
      assemblies: vi.fn().mockReturnValue([]),
      updateSecondaryAssemblyReferences: vi.fn(),
      ...overrides,
   };
   return ws;
}

// ---------------------------------------------------------------------------
// Central render helper
// ---------------------------------------------------------------------------

export function makeMocks() {
   return {
      wsClient: makeWsClientMock(),
      dragService: makeDragServiceMock(),
      worksheet: makeWorksheet(),
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
         getModel: vi.fn().mockReturnValue(of(null)),
         sendModel: vi.fn().mockReturnValue(of({ body: null })),
      },
      composerToolbarService: new ComposerToolbarService(),
      notifications: {
         success: vi.fn(),
         info: vi.fn(),
         warning: vi.fn(),
         danger: vi.fn(),
      },
      aiAssistantDialogService: {
         setWorksheetContext: vi.fn(),
      },
   };
}

export type WSPaneMocks = ReturnType<typeof makeMocks>;

export async function renderComponent(mocks = makeMocks()) {
   const { fixture } = await render(WSPaneComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      componentProperties: {
         // worksheet setter is called on componentInstance post-render; provide newSheet=true
         worksheet: mocks.worksheet,
      },
      componentProviders: [
         { provide: ViewsheetClientService, useValue: mocks.wsClient },
         { provide: DebounceService, useValue: { debounce: vi.fn() } },
         { provide: DialogService, useValue: {
            open: vi.fn().mockReturnValue({ componentInstance: {} }),
            setSheetId: vi.fn(),
            objectDelete: vi.fn(),
         }},
         { provide: WsChangeService, useValue: { changedAssembly: vi.fn() } },
      ],
      providers: [
         { provide: NgbModal, useValue: mocks.modalService },
         { provide: ModelService, useValue: mocks.modelService },
         { provide: DragService, useValue: mocks.dragService },
         { provide: DownloadService, useValue: { download: vi.fn() } },
         { provide: ComposerToolbarService, useValue: mocks.composerToolbarService },
         { provide: ResizeHandlerService, useValue: { onHorizontalDrag: vi.fn() } },
         { provide: GettingStartedService, useValue: {
            isCreateQuery: vi.fn().mockReturnValue(false),
            showGettingStartedMessage: false,
         }},
         { provide: AiAssistantService, useValue: {} },
         { provide: AiAssistantDialogService, useValue: mocks.aiAssistantDialogService },
         provideHttpClient(),
      ],
   });

   const comp = fixture.componentInstance as WSPaneComponent;

   // Wire @ViewChild refs that are null under NO_ERRORS_SCHEMA
   (comp as any).notifications = mocks.notifications;
   (comp as any).concatenateTablesDialog = null;
   (comp as any).variableInputDialog = null;

   // Provide access to the commandSubject from makeMocks
   return { fixture, comp, mocks };
}
