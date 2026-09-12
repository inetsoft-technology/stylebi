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
 * Shared test helpers for VSSlider TL spec.
 *
 * VSSlider has an 8-parameter constructor:
 *   (viewsheetClient, formDataService, formInputService, changeRef,
 *    zone, context, dataTipService, debounceService)
 *
 * Direct instantiation (not ATL render()) is used throughout.
 * The model setter computes verticalCenter and tickSize (calls GuiTool.measureText).
 * ngOnChanges() computes handlePosition, sliderLabel, and ticks.
 *
 * Default model: min=0, max=100, increment=20, value=50, objectFormat.width=200, height=60.
 * Computed values:
 *   verticalCenter = Math.max(17, Math.min(30, 60-36)) = Math.max(17, 24) = 24
 *   handlePosition = (50-0)/(100-0) * 200 = 100
 *
 * GuiTool.measureText must be spied on before calling createVSSlider() because
 * the model setter calls it. Set the spy up in beforeEach so vi.restoreAllMocks()
 * in afterEach cleans it automatically.
 */

import { NEVER } from "rxjs";
import { SimpleChange } from "@angular/core";
import { TestUtils } from "../../../common/test/test-utils";
import { VSSliderModel } from "../../model/vs-slider-model";
import { ContextProvider } from "../../context-provider.service";
import { VSSlider } from "./vs-slider.component";

export interface VSSliderTestContext {
   comp: VSSlider;
   viewsheetClient: {
      sendEvent: ReturnType<typeof vi.fn>;
      runtimeId: string;
      commands: any;
   };
   formDataService: { checkFormData: ReturnType<typeof vi.fn> };
   formInputService: { addPendingValue: ReturnType<typeof vi.fn> };
   changeRef: { detectChanges: ReturnType<typeof vi.fn> };
   debounceService: { debounce: ReturnType<typeof vi.fn> };
}

export function makeVSSliderModel(overrides: Partial<VSSliderModel> = {}): VSSliderModel {
   const base = TestUtils.createMockVSSliderModel("Slider1");
   return Object.assign(base, {
      min: 0,
      max: 100,
      increment: 20,
      value: 50,
      currentLabel: "",
      refresh: true,
      objectFormat: {
         ...TestUtils.createMockVSFormatModel(),
         width: 200,
         height: 60,
         font: "12px Arial",
      },
   } as any, overrides) as VSSliderModel;
}

/**
 * Creates a ready-to-test VSSlider instance.
 *
 * The spy on GuiTool.measureText must be active before calling this function.
 * Call vi.spyOn(GuiTool, "measureText").mockReturnValue(10) in beforeEach.
 */
export function createVSSlider(
   overrides: Partial<VSSliderModel> = {},
): VSSliderTestContext {
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

   const formInputService = { addPendingValue: vi.fn() };
   const changeRef = { detectChanges: vi.fn() };
   const zone = {
      run: vi.fn().mockImplementation((fn: any) => fn()),
      runOutsideAngular: vi.fn().mockImplementation((fn: any) => fn()),
   };
   const context = new ContextProvider(
      false, false, false, false, false, false, false, false, false, false, false,
   );
   const dataTipService = { isDataTip: vi.fn().mockReturnValue(false) };
   const debounceService = {
      debounce: vi.fn().mockImplementation((_key: any, fn: any) => fn()),
   };

   const comp = new VSSlider(
      viewsheetClient as any,
      formDataService as any,
      formInputService as any,
      changeRef as any,
      zone as any,
      context as any,
      dataTipService as any,
      debounceService as any,
   );

   comp.model = makeVSSliderModel(overrides);
   comp.ngOnChanges({ model: new SimpleChange(null, comp.model, true) } as any);

   return {
      comp,
      viewsheetClient: viewsheetClient as any,
      formDataService: formDataService as any,
      formInputService,
      changeRef,
      debounceService: debounceService as any,
   };
}
