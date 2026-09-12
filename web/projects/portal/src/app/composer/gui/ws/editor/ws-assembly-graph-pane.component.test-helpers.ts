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
 * Shared test helpers for ws-assembly-graph-pane.component P1/P2 spec files.
 *
 * These helpers use direct class instantiation (no TestBed) because the
 * constructor calls jspInitGraphMain() inside zone.runOutsideAngular(), so the
 * spec files must vi.mock() the jsplumb modules before imports are evaluated.
 *
 * Each spec file inlines a compatible makeJsp() inside its vi.mock() factory so
 * that Vitest hoisting does not prevent the factory from running.  The exported
 * makeJspMock() here is provided for tests that run after construction and need
 * to re-inspect or replace the instance.
 */

import { EMPTY } from "rxjs";
import { WSAssemblyGraphPaneComponent } from "./ws-assembly-graph-pane.component";
import { Worksheet } from "../../../data/ws/worksheet";

// ---------------------------------------------------------------------------
// jsPlumb mock factory (exported so test groups can inspect calls)
// ---------------------------------------------------------------------------

export function makeJspMock(): any {
   const endpoints = {
      setType: vi.fn().mockReturnThis(),
      setVisible: vi.fn().mockReturnThis(),
      get: vi.fn().mockReturnValue({ isVisible: vi.fn().mockReturnValue(false) }),
      isVisible: vi.fn().mockReturnValue(false),
      length: 0,
      each: vi.fn(),
      addType: vi.fn().mockReturnThis(),
      removeType: vi.fn().mockReturnThis(),
   };

   return {
      bind: vi.fn(),
      setContainer: vi.fn(),
      setSuspendDrawing: vi.fn(),
      isSuspendDrawing: vi.fn().mockReturnValue(false),
      reset: vi.fn(),
      getAllConnections: vi.fn().mockReturnValue([]),
      selectEndpoints: vi.fn().mockReturnValue(endpoints),
      unmakeEverySource: vi.fn(),
      clearDragSelection: vi.fn(),
      addToDragSelection: vi.fn(),
      repaintEverything: vi.fn(),
      draggable: vi.fn(),
      addEndpoint: vi.fn().mockReturnValue({ setVisible: vi.fn().mockReturnThis() }),
      removeAllEndpoints: vi.fn(),
      getConnections: vi.fn().mockReturnValue([]),
      connect: vi.fn().mockReturnValue({
         endpoints: [
            { canvas: { title: "" }, addType: vi.fn(), removeType: vi.fn() },
            { canvas: { title: "" }, addType: vi.fn(), removeType: vi.fn() },
         ],
         addType: vi.fn(),
         removeType: vi.fn(),
         hideOverlay: vi.fn(),
         showOverlay: vi.fn(),
         hasType: vi.fn().mockReturnValue(false),
         sourceId: "src1",
         targetId: "tgt1",
      }),
      deleteConnection: vi.fn(),
      isSource: vi.fn().mockReturnValue(false),
      makeSource: vi.fn(),
      registerEndpointTypes: vi.fn(),
      registerConnectionTypes: vi.fn(),
      importDefaults: vi.fn(),
      restoreDefaults: vi.fn(),
      getContainer: vi.fn().mockReturnValue({ scrollTop: 0, scrollLeft: 0 }),
   };
}

// ---------------------------------------------------------------------------
// NgZone mock — both run() and runOutsideAngular() call their callback
// ---------------------------------------------------------------------------

export function makeZoneMock(): any {
   return {
      run: vi.fn((fn: () => any) => fn()),
      // no-op: prevents jspInitGraphMain() from running in the constructor;
      // comp.jsp is set to our mock by makeComponent() after instantiation.
      runOutsideAngular: vi.fn(),
   };
}

// ---------------------------------------------------------------------------
// ViewsheetClientService mock
// ---------------------------------------------------------------------------

export function makeWsClientMock(): any {
   return {
      commands: EMPTY,
      sendEvent: vi.fn(),
      runtimeId: "test-ws-rt",
   };
}

// ---------------------------------------------------------------------------
// Renderer2 mock
// ---------------------------------------------------------------------------

export function makeRendererMock(): any {
   return {
      removeAttribute: vi.fn(),
      appendChild: vi.fn(),
      setStyle: vi.fn(),
      removeChild: vi.fn(),
      listen: vi.fn().mockReturnValue(() => {}),
      removeClass: vi.fn(),
      addClass: vi.fn(),
   };
}

// ---------------------------------------------------------------------------
// Document mock
// ---------------------------------------------------------------------------

export function makeDocumentMock(): any {
   return {
      getElementById: vi.fn().mockReturnValue(null),
   };
}

// ---------------------------------------------------------------------------
// Worksheet factory — uses a real Worksheet so all methods work correctly
// ---------------------------------------------------------------------------

export function makeWorksheet(overrides: Partial<Worksheet> = {}): Worksheet {
   const ws = new Worksheet();
   // Provide a minimal jspAssemblyGraph so openContextMenu / ngOnInit don't crash
   (ws as any).jspAssemblyGraph = makeJspMock();
   Object.assign(ws, overrides);
   return ws;
}

// ---------------------------------------------------------------------------
// Component factory — direct instantiation
// ---------------------------------------------------------------------------

export interface GraphPaneMocks {
   zone: any;
   wsClient: any;
   dragService: any;
   dropdownService: any;
   modalService: any;
   modelService: any;
   renderer: any;
   document: any;
   worksheet: Worksheet;
   jsp: any;
}

export function makeComponent(overrides: Partial<GraphPaneMocks> = {}): {
   comp: WSAssemblyGraphPaneComponent;
   mocks: GraphPaneMocks;
} {
   const jsp = overrides.jsp ?? makeJspMock();
   const zone = overrides.zone ?? makeZoneMock();
   const wsClient = overrides.wsClient ?? makeWsClientMock();
   const dragService = overrides.dragService ?? { put: vi.fn(), get: vi.fn().mockReturnValue(null) };
   const dropdownService = overrides.dropdownService ?? { open: vi.fn() };
   const modalService = overrides.modalService ?? { open: vi.fn() };
   const modelService = overrides.modelService ?? { sendModel: vi.fn() };
   const renderer = overrides.renderer ?? makeRendererMock();
   const document = overrides.document ?? makeDocumentMock();
   const worksheet = overrides.worksheet ?? makeWorksheet();

   const comp = new WSAssemblyGraphPaneComponent(
      zone, wsClient, dragService, dropdownService, modalService, modelService, renderer, document,
   );

   // After construction the component's this.jsp is whatever jspInitGraphMain() returned.
   // Replace it with our controlled mock so tests can inspect calls.
   comp.jsp = jsp;
   worksheet.jspAssemblyGraph = jsp;
   comp.worksheet = worksheet;

   // Wire jspContainerMain so methods that reference it don't crash
   (comp as any).jspContainerMain = {
      nativeElement: { scrollTop: 0, scrollLeft: 0, style: {} },
   };

   const mocks: GraphPaneMocks = {
      zone, wsClient, dragService, dropdownService, modalService, modelService,
      renderer, document, worksheet, jsp,
   };

   return { comp, mocks };
}
