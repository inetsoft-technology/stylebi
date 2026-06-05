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
import { createApplication } from "@angular/platform-browser";
import { createCustomElement } from "@angular/elements";
import { Injector, Optional } from "@angular/core";
import { provideRouter } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

import { embedElementConfig } from "./app/embed/embed-element.config";

import { EmbedChartComponent } from "./app/embed/chart/embed-chart.component";
import { EmbedCrosstabComponent } from "./app/embed/crosstab/embed-crosstab.component";
import { EmbedTableComponent } from "./app/embed/table/embed-table.component";
import { EmbedGaugeComponent } from "./app/embed/gauge/embed-gauge.component";
import { EmbedTextComponent } from "./app/embed/text/embed-text.component";
import { EmbedImageComponent } from "./app/embed/image/embed-image.component";

import { DndService } from "./app/common/dnd/dnd.service";
import { VSDndService } from "./app/common/dnd/vs-dnd.service";
import { FullScreenService } from "./app/common/services/full-screen.service";
import { UIContextService } from "./app/common/services/ui-context.service";
import { ViewsheetClientService } from "./app/common/viewsheet-client";
import { ChartService } from "./app/graph/services/chart.service";
import {
   ComposerToken,
   ContextProvider,
   EmbedAssemblyContextProviderFactory
} from "./app/vsobjects/context-provider.service";
import { RichTextService } from "./app/vsobjects/dialog/rich-text-dialog/rich-text.service";
import { VSChartService } from "./app/vsobjects/objects/chart/services/vs-chart.service";
import { DataTipService } from "./app/vsobjects/objects/data-tip/data-tip.service";
import { PopComponentService } from "./app/vsobjects/objects/data-tip/pop-component.service";
import { MiniToolbarService } from "./app/vsobjects/objects/mini-toolbar/mini-toolbar.service";
import { ShowHyperlinkService } from "./app/vsobjects/show-hyperlink.service";
import { CheckFormDataService } from "./app/vsobjects/util/check-form-data.service";
import { VSTabService } from "./app/vsobjects/util/vs-tab.service";
import { ModelService } from "./app/widget/services/model.service";
import { ScaleService } from "./app/widget/services/scale/scale-service";
import { VSScaleService } from "./app/widget/services/scale/vs-scale.service";
import {
   DialogService,
   ViewerDialogServiceFactory
} from "./app/widget/slide-out/dialog-service.service";
import { SlideOutService } from "./app/widget/slide-out/slide-out.service";
import { AdhocFilterService } from "./app/vsobjects/objects/data-tip/adhoc-filter.service";

import "./main-base-element";

createApplication({
   providers: [
      ...embedElementConfig.providers,
      provideRouter([]),

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
      AdhocFilterService,
      NgbModal,
      {
         provide: DialogService,
         useFactory: ViewerDialogServiceFactory,
         deps: [NgbModal, SlideOutService, Injector, UIContextService]
      },
      {
         provide: DndService,
         useClass: VSDndService,
         deps: [ModelService, NgbModal, ViewsheetClientService]
      },
      {
         provide: ScaleService,
         useClass: VSScaleService
      },
      {
         provide: ContextProvider,
         useFactory: EmbedAssemblyContextProviderFactory,
         deps: [[new Optional(), ComposerToken]]
      },

      // Chart-specific providers
      VSChartService,
      {
         provide: ChartService,
         useExisting: VSChartService
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
