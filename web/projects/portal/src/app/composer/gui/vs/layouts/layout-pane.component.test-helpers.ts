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
 * Shared test helpers for LayoutPane P1/P2/P3 spec files.
 *
 * Strategy: direct class instantiation.
 *   - Constructor calls CommandProcessor.super() which subscribes to viewsheetClientService.commands.
 *     With EMPTY, the subscription closes immediately → no side effects.
 *   - ngOnInit() is NOT called here; tests that need it set up @Inputs and call it manually.
 *   - @ViewChild 'layoutPane' is wired manually so getGuideSize() can call
 *     layoutPane.nativeElement.clientWidth/clientHeight.
 */

import { EMPTY, Subject } from "rxjs";
import { LayoutPane } from "./layout-pane.component";
import { VSLayoutModel } from "../../../data/vs/vs-layout-model";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { VSLayoutObjectModel } from "../../../data/vs/vs-layout-object-model";

// ---------------------------------------------------------------------------
// Service mocks
// ---------------------------------------------------------------------------

export function makeViewsheetClientServiceMock() {
   return {
      commands: EMPTY,
      connect: vi.fn(),
      sendEvent: vi.fn(),
      onHeartbeat: EMPTY,
      runtimeId: "",
      focusedLayoutName: "",
   };
}

export function makeDebounceServiceMock() {
   return {
      debounce: vi.fn(),
      cancel: vi.fn(),
   };
}

export function makeChangeDetectorRefMock() {
   return {
      detectChanges: vi.fn(),
      markForCheck: vi.fn(),
   };
}

export function makeZoneMock() {
   return {
      run: vi.fn((fn: () => any) => fn()),
      runOutsideAngular: vi.fn((fn: () => any) => fn()),
   };
}

export function makeComposerObjectServiceMock() {
   return {
      addKeyEventAdapter: vi.fn(),
      removeKeyEventAdapter: vi.fn(),
      moveObject: vi.fn(),
   };
}

export function makeResizeHandlerServiceMock() {
   return {
      anyResizeSubject: EMPTY,
   };
}

// ---------------------------------------------------------------------------
// VSLayoutObjectModel factory
// ---------------------------------------------------------------------------

export function makeLayoutObject(name: string, overrides: Partial<any> = {}): VSLayoutObjectModel {
   return {
      name,
      left: 10,
      top: 10,
      width: 100,
      height: 50,
      editable: true,
      childModels: [],
      objectModel: {
         objectType: "VSText",
         absoluteName: name,
         objectFormat: { left: 10, top: 10, width: 100, height: 50 },
         container: null,
      } as any,
      ...overrides,
   } as any;
}

// ---------------------------------------------------------------------------
// Component factory interface
// ---------------------------------------------------------------------------

export interface LayoutPaneMocks {
   viewsheetClientService: ReturnType<typeof makeViewsheetClientServiceMock>;
   renderer: any;
   debounceService: ReturnType<typeof makeDebounceServiceMock>;
   changeRef: ReturnType<typeof makeChangeDetectorRefMock>;
   zone: ReturnType<typeof makeZoneMock>;
   composerObjectService: ReturnType<typeof makeComposerObjectServiceMock>;
   resizeHandlerService: ReturnType<typeof makeResizeHandlerServiceMock>;
   vsLayout: VSLayoutModel;
   vs: Viewsheet;
}

export function makeComponent(overrides: Partial<LayoutPaneMocks> = {}): {
   comp: LayoutPane;
   mocks: LayoutPaneMocks;
} {
   const viewsheetClientService = overrides.viewsheetClientService ?? makeViewsheetClientServiceMock();
   const renderer              = overrides.renderer              ?? { addClass: vi.fn(), removeClass: vi.fn(), setStyle: vi.fn() };
   const debounceService       = overrides.debounceService       ?? makeDebounceServiceMock();
   const changeRef             = overrides.changeRef             ?? makeChangeDetectorRefMock();
   const zone                  = overrides.zone                  ?? makeZoneMock();
   const composerObjectService = overrides.composerObjectService ?? makeComposerObjectServiceMock();
   const resizeHandlerService  = overrides.resizeHandlerService  ?? makeResizeHandlerServiceMock();

   const comp = new LayoutPane(
      viewsheetClientService as any,
      renderer as any,
      debounceService as any,
      changeRef as any,
      zone as any,
      composerObjectService as any,
      resizeHandlerService as any,
   );

   // Wire @ViewChild so getGuideSize() can read dimensions without DOM
   const layoutPaneEl = {
      nativeElement: {
         clientWidth: 800,
         clientHeight: 600,
         scrollTop: 0,
         scrollLeft: 0,
         scrollHeight: 600,
         offsetWidth: 800,
         getBoundingClientRect: vi.fn().mockReturnValue({ top: 0, left: 0, width: 800, height: 600 }),
         querySelector: vi.fn().mockReturnValue(null),
      },
   };
   (comp as any).layoutPane = layoutPaneEl;

   // Set required @Input properties
   const vsLayout = overrides.vsLayout ?? new VSLayoutModel();
   vsLayout.objects       = vsLayout.objects       ?? [];
   vsLayout.headerObjects = vsLayout.headerObjects ?? [];
   vsLayout.footerObjects = vsLayout.footerObjects ?? [];
   vsLayout.runtimeID     = vsLayout.runtimeID     ?? "rt1";
   vsLayout.name          = vsLayout.name          ?? "Layout1";
   comp.vsLayout = vsLayout;

   const vs = overrides.vs ?? new Viewsheet();
   vs.snapGrid = vs.snapGrid ?? 10;
   comp.vs = vs;

   comp.runtimeId = "rt1";
   comp.vsPaneBounds = { width: 800, height: 600 } as ClientRect;
   comp.layoutChange = EMPTY;

   const mocks: LayoutPaneMocks = {
      viewsheetClientService, renderer, debounceService, changeRef, zone,
      composerObjectService, resizeHandlerService, vsLayout, vs,
   };

   return { comp, mocks };
}
