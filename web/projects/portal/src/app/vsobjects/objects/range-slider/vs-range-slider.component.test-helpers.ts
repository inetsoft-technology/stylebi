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
 * Shared test helpers for VSRangeSlider multi-pass TL specs.
 *
 * VSRangeSlider has a 13-parameter constructor:
 *   (viewsheetClient, formDataService, renderer, elementRef, modelService,
 *    modalService, adhocFilterService, zone, context, dataTipService,
 *    debounceService, dropdownService, globalSubmitService)
 *
 * Direct instantiation (not ATL render()) is used throughout.
 * After construction, ngOnInit() subscribes to globalSubmitService.globalSubmit(),
 * then ngOnChanges() triggers calculatePositions().
 *
 * Default model uses 5 labels ["A".."E"], maxRangeBarWidth=200, selectStart=1, selectEnd=3.
 * Computed positions: rangeLineWidth=191, widthBetweenTicks=47.75,
 *   _leftHandlePosition=47.75, _rightHandlePosition=143.25.
 *
 * GuiTool.measureText must be spied on before calling createVSRangeSlider() because
 * calculatePositions() calls it for textSize. Set the spy up in beforeEach so
 * vi.restoreAllMocks() in afterEach cleans it automatically.
 */

import { NEVER, Subject } from "rxjs";
import { SimpleChange } from "@angular/core";
import { TestUtils } from "../../../common/test/test-utils";
import { VSRangeSliderModel } from "../../model/vs-range-slider-model";
import { ContextProvider } from "../../context-provider.service";
import { VSRangeSlider } from "./vs-range-slider.component";

export interface VSRangeSliderTestContext {
   comp: VSRangeSlider;
   viewsheetClient: {
      sendEvent: ReturnType<typeof vi.fn>;
      runtimeId: string;
      commands: any;
   };
   formDataService: { checkFormData: ReturnType<typeof vi.fn> };
   modelService: { getModel: ReturnType<typeof vi.fn> };
   modalService: { open: ReturnType<typeof vi.fn> };
   debounceService: { debounce: ReturnType<typeof vi.fn> };
   dropdownService: { open: ReturnType<typeof vi.fn> };
   globalSubmitService: {
      globalSubmit: ReturnType<typeof vi.fn>;
      updateState: ReturnType<typeof vi.fn>;
   };
   adhocFilterService: { showFilter: ReturnType<typeof vi.fn> };
   renderer: { listen: ReturnType<typeof vi.fn> };
   globalSubmitSubject: Subject<string>;
}

export function makeVSRangeSliderModel(
   overrides: Partial<VSRangeSliderModel> = {},
): VSRangeSliderModel {
   const base = TestUtils.createMockVSRangeSliderModel("RangeSlider1");
   return Object.assign(base, {
      labels: ["A", "B", "C", "D", "E"],
      values: ["10", "20", "30", "40", "50"],
      selectStart: 1,
      selectEnd: 3,
      maxRangeBarWidth: 200,
      upperInclusive: true,
      submitOnChange: true,
      dataType: "integer",
      objectFormat: {
         ...TestUtils.createMockVSFormatModel(),
         width: 220,
         height: 80,
         font: "12px Arial",
      },
      titleFormat: {
         ...TestUtils.createMockVSFormatModel(),
         height: 30,
      },
   } as any, overrides) as VSRangeSliderModel;
}

/**
 * Creates a ready-to-test VSRangeSlider instance.
 *
 * The spy on GuiTool.measureText must be active before calling this function.
 * Call vi.spyOn(GuiTool, "measureText").mockReturnValue(10) in beforeEach.
 *
 * @param options.viewer  set true to use a viewer ContextProvider (enables showRangeSliderEditDialog
 *                        quick-click path and fixSlideOutOptions popup=true)
 */
export function createVSRangeSlider(
   overrides: Partial<VSRangeSliderModel> = {},
   options: { viewer?: boolean } = {},
): VSRangeSliderTestContext {
   const globalSubmitSubject = new Subject<string>();

   const viewsheetClient = {
      sendEvent: vi.fn(),
      runtimeId: "vs-1^128^__^Sheet1",
      commands: NEVER,
   };

   const formDataService = {
      checkFormData: vi.fn().mockImplementation(
         (_rid: any, _name: any, _null: any, success: any, _fail: any) => success(),
      ),
   };

   const renderer = { listen: vi.fn().mockReturnValue(() => {}) };
   const elementRef = { nativeElement: document.createElement("div") };
   const modelService = { getModel: vi.fn() };
   const modalService = { open: vi.fn() };
   const adhocFilterService = { showFilter: vi.fn().mockReturnValue(() => {}) };
   const zone = {
      run: vi.fn().mockImplementation((fn: any) => fn()),
      runOutsideAngular: vi.fn().mockImplementation((fn: any) => fn()),
   };
   const viewer = options.viewer ?? false;
   const context = new ContextProvider(
      viewer, false, false, false, false, false, false, false, false, false, false,
   );
   const dataTipService = { isDataTip: vi.fn().mockReturnValue(false) };
   const debounceService = {
      debounce: vi.fn().mockImplementation((_key: any, fn: any) => fn()),
   };
   const dropdownService = { open: vi.fn() };
   const globalSubmitService = {
      globalSubmit: vi.fn().mockReturnValue(globalSubmitSubject.asObservable()),
      updateState: vi.fn(),
   };

   const comp = new VSRangeSlider(
      viewsheetClient as any,
      formDataService as any,
      renderer as any,
      elementRef as any,
      modelService as any,
      modalService as any,
      adhocFilterService as any,
      zone as any,
      context as any,
      dataTipService as any,
      debounceService as any,
      dropdownService as any,
      globalSubmitService as any,
   );

   comp.model = makeVSRangeSliderModel(overrides);
   comp.ngOnInit();
   comp.ngOnChanges({ model: new SimpleChange(null, comp.model, true) } as any);

   return {
      comp,
      viewsheetClient: viewsheetClient as any,
      formDataService: formDataService as any,
      modelService,
      modalService,
      debounceService: debounceService as any,
      dropdownService,
      globalSubmitService: globalSubmitService as any,
      adhocFilterService: adhocFilterService as any,
      renderer: renderer as any,
      globalSubmitSubject,
   };
}

/** Stubs @ViewChild DOM refs needed by navigate() and focusSelectedHandle(). */
export function stubViewChildRefs(comp: VSRangeSlider): void {
   (comp as any).leftHandle = { nativeElement: { blur: vi.fn(), focus: vi.fn() } };
   (comp as any).rightHandle = { nativeElement: { blur: vi.fn(), focus: vi.fn() } };
   (comp as any).middleHandle = { nativeElement: { blur: vi.fn(), focus: vi.fn() } };
   (comp as any).rangeSliderContainer = { nativeElement: { focus: vi.fn() } };
   (comp as any).miniMenu = {
      nativeElement: {
         focus: vi.fn(),
         getBoundingClientRect: vi.fn().mockReturnValue({ left: 0, top: 0, height: 0 }),
      },
   };
   (comp as any).miniMenuComponent = { openMenu: vi.fn() };
   (comp as any).collapseButtonRef = { nativeElement: { focus: vi.fn() } };
}
