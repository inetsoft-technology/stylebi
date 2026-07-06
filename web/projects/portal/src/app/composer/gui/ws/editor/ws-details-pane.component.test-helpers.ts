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
 * Shared test helpers for WSDetailsPaneComponent P1/P2 spec files.
 *
 * Key design:
 *   - No component-level providers; all services are root-level providers.
 *   - ViewsheetClientService mock provides sendEvent() spy.
 *   - Table mock provides realistic shape to exercise getTableStatus() and
 *     populateTableModeButtons() without importing real AbstractTableAssembly subclasses.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { EMPTY, of, Subject } from "rxjs";

import { AiAssistantService } from "../../../../../../../shared/ai-assistant/ai-assistant.service";
import { AiAssistantDialogService } from "../../../../common/services/ai-assistant-dialog.service";
import { DownloadService } from "../../../../../../../shared/download/download.service";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { ModelService } from "../../../../widget/services/model.service";
import { DragService } from "../../../../widget/services/drag.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { WSDetailsPaneComponent } from "./ws-details-pane.component";
import { BoundTableAssembly } from "../../../data/ws/bound-table-assembly";

// ---------------------------------------------------------------------------
// Table mock factory
// ---------------------------------------------------------------------------

export function makeTable(overrides: Partial<any> = {}): any {
   return {
      name: "TestTable",
      tableClassType: "BoundTableAssembly",
      modes: ["default", "live"],
      mode: "default",
      tableButtons: ["condition", "group", "sort", "expression", "export", "visible"],
      colInfos: [],
      info: { editable: true, runtime: false, live: true, mirrorInfo: null, sourceInfo: null },
      totalRows: null,
      duration: null,
      exceededMaximum: null,
      aggregateInfo: null,
      isEmbeddedTable: vi.fn().mockReturnValue(false),
      isSnapshotTable: vi.fn().mockReturnValue(false),
      isRuntime: vi.fn().mockReturnValue(false),
      ...overrides,
   };
}

/**
 * Returns a plain-object table mock whose prototype chain satisfies
 * `instanceof BoundTableAssembly`, bypassing the constructor so the
 * BoundTableAssembly-only branches in getTableStatus() are reachable.
 */
export function makeBoundTable(overrides: Partial<any> = {}): any {
   return Object.assign(Object.create(BoundTableAssembly.prototype), makeTable(overrides));
}

// ---------------------------------------------------------------------------
// Worksheet mock factory
// ---------------------------------------------------------------------------

export function makeWorksheet(overrides: Partial<any> = {}): any {
   const socketConnection = { sendEvent: vi.fn() };
   return {
      runtimeId: "ws-rt1",
      socketConnection,
      messageLevels: {},
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Central mocks factory
// ---------------------------------------------------------------------------

export function makeMocks() {
   const worksheetClient = {
      sendEvent: vi.fn(),
      commands: { subscribe: vi.fn() },
      runtimeId: "ws-rt1",
   };
   const dialogService = {
      open: vi.fn().mockReturnValue({
         componentInstance: {},
         result: new Promise<never>((_, reject) => reject("cancel")),
      }),
      setSheetId: vi.fn(),
      objectDelete: vi.fn(),
   };
   const modalService = {
      open: vi.fn().mockReturnValue({
         result: new Promise<never>((_, reject) => reject("cancel")),
         componentInstance: { onCommit: new Subject<string>() },
      }),
   };
   const modelService = {
      getModel: vi.fn().mockReturnValue(of(null)),
      sendModel: vi.fn().mockReturnValue(of({ body: null })),
   };
   const downloadService = { download: vi.fn() };
   const dragService = {
      registerDragDataListener: vi.fn().mockReturnValue(EMPTY),
      put: vi.fn(),
   };

   return {
      worksheetClient,
      dialogService,
      modalService,
      modelService,
      downloadService,
      dragService,
      table: makeTable(),
      worksheet: makeWorksheet(),
   };
}

export type WSDetailsMocks = ReturnType<typeof makeMocks>;

// ---------------------------------------------------------------------------
// renderComponent helper
// ---------------------------------------------------------------------------

export async function renderComponent(mocks = makeMocks()) {
   const { fixture } = await render(WSDetailsPaneComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      componentProperties: {
         table: mocks.table,
         worksheet: mocks.worksheet,
         freeFormSqlEnabled: true,
         expressionColumnEnabled: false,
      },
      providers: [
         { provide: ViewsheetClientService, useValue: mocks.worksheetClient },
         { provide: DialogService, useValue: mocks.dialogService },
         { provide: NgbModal, useValue: mocks.modalService },
         { provide: ModelService, useValue: mocks.modelService },
         { provide: DownloadService, useValue: mocks.downloadService },
         { provide: DragService, useValue: mocks.dragService },
         { provide: AiAssistantService, useValue: {} },
         { provide: AiAssistantDialogService, useValue: { setWorksheetScriptContext: vi.fn() } },
         provideHttpClient(),
      ],
   });

   const comp = fixture.componentInstance as WSDetailsPaneComponent;

   return { fixture, comp, mocks };
}
