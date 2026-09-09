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
import { ProviderToken } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { provideHttpClientTesting } from "@angular/common/http/testing";
import { ActivatedRoute, provideRouter, Router } from "@angular/router";
import { DndService } from "../../common/dnd/dnd.service";
import { FullScreenService } from "../../common/services/full-screen.service";
import { ChartService } from "../../graph/services/chart.service";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { RichTextService } from "../../vsobjects/dialog/rich-text-dialog/rich-text.service";
import { VSChartService } from "../../vsobjects/objects/chart/services/vs-chart.service";
import { DataTipService } from "../../vsobjects/objects/data-tip/data-tip.service";
import { PopComponentService } from "../../vsobjects/objects/data-tip/pop-component.service";
import { MiniToolbarService } from "../../vsobjects/objects/mini-toolbar/mini-toolbar.service";
import { ShowHyperlinkService } from "../../vsobjects/show-hyperlink.service";
import { CheckFormDataService } from "../../vsobjects/util/check-form-data.service";
import { VSTabService } from "../../vsobjects/util/vs-tab.service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { DialogService } from "../../widget/slide-out/dialog-service.service";
import { embedElementConfig } from "../embed-element.config";
import { EMBED_CHART_ROUTE_PROVIDERS } from "./embed-chart.route-providers";

// EmbedChartComponent is created by createCustomElement() in main-elements.ts from the application
// injector, so no route is ever activated in the "elements" bundle. These providers must therefore
// resolve from the root injector configuration that main-elements.ts sets up -- if they only resolve
// from a route-level injector, the <inetsoft-chart> element fails to render with NG0201.
describe("embed chart element providers", () => {
   beforeEach(() => {
      TestBed.configureTestingModule({
         providers: [
            ...embedElementConfig.providers,
            provideRouter([]),
            ...EMBED_CHART_ROUTE_PROVIDERS,
            // AppInfoService (pulled in transitively) fires GET ../api/org/info from its
            // constructor; swap in the testing backend so no real XHR is attempted.
            provideHttpClientTesting()
         ]
      });
   });

   const tokens: [string, ProviderToken<any>][] = [
      ["ScaleService", ScaleService],
      ["MiniToolbarService", MiniToolbarService],
      ["ShowHyperlinkService", ShowHyperlinkService],
      ["ContextProvider", ContextProvider],
      ["DataTipService", DataTipService],
      ["PopComponentService", PopComponentService],
      ["VSChartService", VSChartService],
      ["ChartService", ChartService],
      ["DndService", DndService],
      ["DialogService", DialogService],
      ["VSTabService", VSTabService],
      ["RichTextService", RichTextService],
      ["FullScreenService", FullScreenService],
      ["CheckFormDataService", CheckFormDataService],
      ["Router", Router],
      ["ActivatedRoute", ActivatedRoute]
   ];

   // AdhocFilterService is deliberately absent from this list: it injects Renderer2, which is only
   // resolvable from a node injector, so it can never be instantiated from an environment injector.
   // EmbedChartComponent's own component-level `providers` supply the instance the chart tree uses.
   tokens.forEach(([name, token]) => {
      it(`should resolve ${name} from the application injector`, () => {
         expect(TestBed.inject(token)).toBeTruthy();
      });
   });

   it("should resolve ChartService to the same instance as VSChartService", () => {
      expect(TestBed.inject(ChartService)).toBe(TestBed.inject(VSChartService));
   });
});
