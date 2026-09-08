/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { Optional } from "@angular/core";
import { createCustomElement } from "@angular/elements";
import { createApplication } from "@angular/platform-browser";
import { provideRouter } from "@angular/router";
import { FullScreenService } from "./app/common/services/full-screen.service";
import { UIContextService } from "./app/common/services/ui-context.service";
import { EmbedChartComponent } from "./app/embed/chart/embed-chart.component";
import { embedChartRoutesEager } from "./app/embed/chart/embed-chart.routes-eager";
import { EmbedCrosstabComponent } from "./app/embed/crosstab/embed-crosstab.component";
import { embedElementConfig } from "./app/embed/embed-element.config";
import { EmbedGaugeComponent } from "./app/embed/gauge/embed-gauge.component";
import { EmbedImageComponent } from "./app/embed/image/embed-image.component";
import { EmbedTableComponent } from "./app/embed/table/embed-table.component";
import { EmbedTextComponent } from "./app/embed/text/embed-text.component";
import {
   ComposerToken,
   ContextProvider,
   EmbedAssemblyContextProviderFactory
} from "./app/vsobjects/context-provider.service";
import { RichTextService } from "./app/vsobjects/dialog/rich-text-dialog/rich-text.service";
import { DataTipService } from "./app/vsobjects/objects/data-tip/data-tip.service";
import { PopComponentService } from "./app/vsobjects/objects/data-tip/pop-component.service";
import { MiniToolbarService } from "./app/vsobjects/objects/mini-toolbar/mini-toolbar.service";
import { ShowHyperlinkService } from "./app/vsobjects/show-hyperlink.service";
import { CheckFormDataService } from "./app/vsobjects/util/check-form-data.service";
import { VSTabService } from "./app/vsobjects/util/vs-tab.service";
import { ScaleService } from "./app/widget/services/scale/scale-service";
import { VSScaleService } from "./app/widget/services/scale/vs-scale.service";
import { SlideOutService } from "./app/widget/slide-out/slide-out.service";
import "./main-base-element";

createApplication({
   providers: [
      ...embedElementConfig.providers,
      // Use the eager route variant here (not embedChartRoutes) -- see the comment on
      // embedChartRoutesEager for why: EmbedChartComponent is always needed immediately in this
      // bundle (it's created below regardless), so the lazy loadComponent() portal routing uses
      // only costs this single-component bundle a broken build (Bug #76468).
      provideRouter(embedChartRoutesEager),

      // Shared providers for all embed elements — kept at app level so they are available
      // to standalone custom elements (no router activation occurs in that usage).
      DataTipService,
      PopComponentService,
      MiniToolbarService,
      SlideOutService,
      UIContextService,
      CheckFormDataService,
      ShowHyperlinkService,
      VSTabService,
      RichTextService,
      FullScreenService,
      {
         provide: ScaleService,
         useClass: VSScaleService
      },
      {
         provide: ContextProvider,
         useFactory: EmbedAssemblyContextProviderFactory,
         deps: [[new Optional(), ComposerToken]]
      },
   ]
}).then(app => {
   const injector = app.injector;
   customElements.define("inetsoft-chart",
      createCustomElement(EmbedChartComponent, { injector }));
   customElements.define("inetsoft-crosstab",
      createCustomElement(EmbedCrosstabComponent, { injector }));
   customElements.define("inetsoft-table",
      createCustomElement(EmbedTableComponent, { injector }));
   customElements.define("inetsoft-gauge",
      createCustomElement(EmbedGaugeComponent, { injector }));
   customElements.define("inetsoft-text",
      createCustomElement(EmbedTextComponent, { injector }));
   customElements.define("inetsoft-image",
      createCustomElement(EmbedImageComponent, { injector }));
}).catch(err => console.error(err));

/**
 * Check if inetsoft is connected on app load in case there is no need to log in such as when
 * security is disabled or there is an active session
 */
(window as any).checkInetsoftConnection(null, false);
