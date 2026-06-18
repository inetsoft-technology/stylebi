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
 * Shared test helpers for vs-object-container.component P1/P2/P3 spec files.
 *
 * Uses direct class instantiation.  The constructor subscribes to three
 * service Observables; each service mock provides the Observable via a Subject
 * so no immediate emission occurs (Subject.asObservable() has hot semantics).
 */

import { Subject, Subscription } from "rxjs";
import { of } from "rxjs";
import { VSObjectContainer } from "./vs-object-container.component";
import { VSObjectModel } from "../model/vs-object-model";
import { VSFormatModel } from "../model/vs-format-model";
import { DataTipService } from "./data-tip/data-tip.service";
import { MiniToolbarService } from "./mini-toolbar/mini-toolbar.service";

// ---------------------------------------------------------------------------
// Service mocks
// ---------------------------------------------------------------------------

export function makeMiniToolbarService(overrides: Partial<MiniToolbarService> = {}) {
   return {
      addContainerEvents: vi.fn().mockReturnValue(new Subscription()),
      isMiniToolbarHidden: vi.fn().mockReturnValue(false),
      getActionsWidth: vi.fn().mockReturnValue(200),
      getToolbarLeft: vi.fn().mockReturnValue(0),
      getToolbarWidth: vi.fn().mockReturnValue(100),
      handleMouseEnter: vi.fn(),
      ...overrides,
   };
}

export function makeDataTipService(overrides: Partial<DataTipService> = {}) {
   return {
      showHideDataTip: new Subject<void>(),
      isDataTipVisible: vi.fn().mockReturnValue(false),
      isDataTip: vi.fn().mockReturnValue(false),
      dataTipName: null,
      isDataTipSource: vi.fn().mockReturnValue(false),
      isCurrentDataTip: vi.fn().mockReturnValue(false),
      hasDataTipShowing: vi.fn().mockReturnValue(false),
      getVSObjectId: vi.fn((name: string) => name),
      ...overrides,
   };
}

export function makeContextProvider() {
   return { viewer: false, preview: false, binding: false };
}

export function makeAdhocFilterService() {
   return { adhocFilterShowing: false };
}

export function makePopService() {
   return {
      componentPop: new Subject<string>(),
      getPopComponent: vi.fn().mockReturnValue(""),
      isPopComponent: vi.fn().mockReturnValue(false),
      isPopSource: vi.fn().mockReturnValue(false),
      hasPopUpComponentShowing: vi.fn().mockReturnValue(false),
   };
}

export function makeChangeDetectorRef() {
   return { detectChanges: vi.fn() };
}

export function makeScaleService() {
   return {
      getScale: vi.fn().mockReturnValue(of(1)),
      getCurrentScale: vi.fn().mockReturnValue(1),
   };
}

export function makeElementRef() {
   return {
      nativeElement: { querySelector: vi.fn().mockReturnValue(null) },
   };
}

export function makeViewsheetClient() {
   return { sendEvent: vi.fn() };
}

// ---------------------------------------------------------------------------
// Model factories
// ---------------------------------------------------------------------------

export function makeObjectFormat(overrides: Partial<{
   top: number; left: number; width: number; height: number;
   zIndex: number; border: any;
}> = {}) {
   return {
      top: 10, left: 10, width: 200, height: 100, zIndex: 1,
      border: { top: null, bottom: null, left: null, right: null },
      ...overrides,
   } as VSFormatModel;
}

export function makeVSObject(overrides: Partial<VSObjectModel> = {}): VSObjectModel {
   return {
      absoluteName: "Chart1",
      objectType: "VSChart",
      objectFormat: makeObjectFormat(),
      visible: true,
      container: null,
      containerType: null,
      active: true,
      sheetMaxMode: false,
      selectedAnnotations: [],
      assemblyAnnotationModels: [],
      inEmbeddedViewsheet: false,
      ...overrides,
   } as VSObjectModel;
}

export function makeVsInfo(objects: VSObjectModel[] = [makeVSObject()]) {
   return {
      runtimeId: "rt-abc",
      vsObjects: objects,
   } as any;
}

// ---------------------------------------------------------------------------
// Component factory
// ---------------------------------------------------------------------------

export interface ComponentResult {
   comp: VSObjectContainer;
   miniToolbarSvc: ReturnType<typeof makeMiniToolbarService>;
   dataTipSvc: ReturnType<typeof makeDataTipService>;
   context: ReturnType<typeof makeContextProvider>;
   adhocFilterSvc: ReturnType<typeof makeAdhocFilterService>;
   popSvc: ReturnType<typeof makePopService>;
   changeRef: ReturnType<typeof makeChangeDetectorRef>;
   scaleService: ReturnType<typeof makeScaleService>;
   viewsheetClient: ReturnType<typeof makeViewsheetClient>;
}

export function makeComponent(opts: {
   context?: ReturnType<typeof makeContextProvider>;
   dataTipSvc?: ReturnType<typeof makeDataTipService>;
   popSvc?: ReturnType<typeof makePopService>;
   miniToolbarSvc?: ReturnType<typeof makeMiniToolbarService>;
   vsInfo?: any;
   embeddedVS?: boolean;
   vsObjectActions?: any[];
   selectedAssemblies?: number[];
} = {}): ComponentResult {
   const miniToolbarSvc = opts.miniToolbarSvc ?? makeMiniToolbarService();
   const dataTipSvc = opts.dataTipSvc ?? makeDataTipService();
   const context = opts.context ?? makeContextProvider();
   const adhocFilterSvc = makeAdhocFilterService();
   const popSvc = opts.popSvc ?? makePopService();
   const changeRef = makeChangeDetectorRef();
   const scaleService = makeScaleService();
   const elementRef = makeElementRef();
   const viewsheetClient = makeViewsheetClient();

   const comp = new VSObjectContainer(
      miniToolbarSvc as any,
      dataTipSvc as any,
      context as any,
      adhocFilterSvc as any,
      popSvc as any,
      changeRef as any,
      scaleService as any,
      elementRef as any,
      viewsheetClient as any,
   );

   comp.vsInfo = opts.vsInfo ?? makeVsInfo();
   comp.embeddedVS = opts.embeddedVS ?? false;
   comp.vsObjectActions = opts.vsObjectActions ?? [];
   comp.selectedAssemblies = opts.selectedAssemblies ?? [];

   return { comp, miniToolbarSvc, dataTipSvc, context, adhocFilterSvc, popSvc, changeRef, scaleService, viewsheetClient };
}
