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
 * Shared test helpers for VSImage multi-pass TL specs.
 *
 * VSImage has an 11-parameter constructor and does not issue any HTTP calls of its own
 * (getSrc() only builds an image URL string). Direct instantiation (not ATL render()) is
 * used so every dependency can be a minimal vi.fn() mock and the template/child directives
 * (VSAnnotation, VSDataTipDirective, ...) never need to be resolved via Angular DI.
 */

import { SimpleChange } from "@angular/core";
import { TestUtils } from "../../../../common/test/test-utils";
import { ViewsheetInfo } from "../../../data/viewsheet-info";
import { VSImage } from "./vs-image.component";
import { VSImageModel } from "../../../model/output/vs-image-model";

export function makeMockImageModel(overrides: Partial<VSImageModel> = {}): VSImageModel {
   const base = TestUtils.createMockVSImageModel("Image1");
   const fmt = TestUtils.createMockVSFormatModel();

   return Object.assign(base, {
      noImageFlag: false,
      hyperlinks: [],
      alpha: "0.6",
      tooltipVisible: false,
      customTooltipString: null,
      defaultAnnotationContent: null,
      objectFormat: { ...fmt, width: 200, height: 100, alpha: 1 },
      genTime: 1,
   } as any, overrides) as VSImageModel;
}

export interface ImageTestOverrides {
   viewsheetClient?: any;
   popComponentService?: any;
   dataTipService?: any;
   contextProvider?: any;
   debounceService?: any;
   hyperlinkService?: any;
   richTextService?: any;
   model?: Partial<VSImageModel>;
}

export interface ImageTestContext {
   comp: VSImage;
   viewsheetClient: any;
   popComponentService: any;
   dataTipService: any;
   contextProvider: any;
   debounceService: any;
   hyperlinkService: any;
   richTextService: any;
   dropdownService: any;
   changeDetectorRef: any;
}

export function createImageComponent(overrides: ImageTestOverrides = {}): ImageTestContext {
   const viewsheetClient = overrides.viewsheetClient ?? {
      sendEvent: vi.fn(),
      runtimeId: "Viewsheet1",
      isLayoutFocused: false,
   };

   const popComponentService = overrides.popComponentService ?? {
      setPopLocation: vi.fn(),
      toggle: vi.fn(),
      isPopComponent: vi.fn().mockReturnValue(false),
      isPopSource: vi.fn().mockReturnValue(false),
      registerOnClickFlagged: vi.fn(),
   };

   const dataTipService = overrides.dataTipService ?? {
      isDataTip: vi.fn().mockReturnValue(false),
   };

   const contextProvider = overrides.contextProvider ?? {
      viewer: true,
      preview: false,
      composer: false,
      binding: false,
   };

   const debounceService = overrides.debounceService ?? {
      debounce: vi.fn(),
      cancel: vi.fn(),
   };

   const hyperlinkService = overrides.hyperlinkService ?? {
      showHyperlinks: vi.fn(),
      clickLink: vi.fn(),
      singleClick: false,
   };

   const richTextService = overrides.richTextService ?? {
      showAnnotationDialog: vi.fn().mockReturnValue({ subscribe: vi.fn() }),
   };

   const dropdownService = { open: vi.fn() };
   const changeDetectorRef = { detectChanges: vi.fn(), markForCheck: vi.fn() };

   const comp = new VSImage(
      viewsheetClient as any,
      popComponentService as any,
      { open: vi.fn() } as any,                                                    // modalService
      dropdownService as any,
      { run: (fn: any) => fn(), runOutsideAngular: (fn: any) => fn() } as any,      // zone
      contextProvider as any,
      dataTipService as any,
      debounceService as any,
      changeDetectorRef as any,
      hyperlinkService as any,
      richTextService as any,
   );

   comp.vsInfo = new ViewsheetInfo([], "/link/", false, "Viewsheet1");
   comp.model = makeMockImageModel(overrides.model ?? {});

   return {
      comp, viewsheetClient, popComponentService, dataTipService, contextProvider,
      debounceService, hyperlinkService, richTextService, dropdownService, changeDetectorRef,
   };
}

export function makeModelChange(previous: VSImageModel, current: VSImageModel,
                                 isFirst: boolean = false): SimpleChange
{
   return new SimpleChange(previous, current, isFirst);
}
