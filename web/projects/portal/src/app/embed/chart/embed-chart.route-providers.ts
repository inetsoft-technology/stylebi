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
import { Injector, Optional } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { DndService } from "../../common/dnd/dnd.service";
import { VSDndService } from "../../common/dnd/vs-dnd.service";
import { FullScreenService } from "../../common/services/full-screen.service";
import { UIContextService } from "../../common/services/ui-context.service";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { ChartService } from "../../graph/services/chart.service";
import {
   ComposerToken,
   ContextProvider,
   EmbedAssemblyContextProviderFactory
} from "../../vsobjects/context-provider.service";
import { RichTextService } from "../../vsobjects/dialog/rich-text-dialog/rich-text.service";
import { VSChartService } from "../../vsobjects/objects/chart/services/vs-chart.service";
import { DataTipService } from "../../vsobjects/objects/data-tip/data-tip.service";
import { PopComponentService } from "../../vsobjects/objects/data-tip/pop-component.service";
import { MiniToolbarService } from "../../vsobjects/objects/mini-toolbar/mini-toolbar.service";
import { ShowHyperlinkService } from "../../vsobjects/show-hyperlink.service";
import { CheckFormDataService } from "../../vsobjects/util/check-form-data.service";
import { VSTabService } from "../../vsobjects/util/vs-tab.service";
import { ModelService } from "../../widget/services/model.service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { VSScaleService } from "../../widget/services/scale/vs-scale.service";
import {
   DialogService,
   ViewerDialogServiceFactory
} from "../../widget/slide-out/dialog-service.service";
import { SlideOutService } from "../../widget/slide-out/slide-out.service";
import { AdhocFilterService } from "../../vsobjects/objects/data-tip/adhoc-filter.service";

// Split out of embed-chart.routes.ts so the "elements" web-component bundle's eager route file
// (embed-chart.routes-eager.ts) can share this provider set without importing anything that
// contains a dynamic import() -- see embed-chart.routes-eager.ts for why that matters.
export const EMBED_CHART_ROUTE_PROVIDERS = [
   DataTipService,
   PopComponentService,
   MiniToolbarService,
   VSChartService,
   SlideOutService,
   UIContextService,
   CheckFormDataService,
   ShowHyperlinkService,
   VSTabService,
   RichTextService,
   FullScreenService,
   AdhocFilterService,
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
   {
      provide: ChartService,
      useExisting: VSChartService
   },
   NgbModal
];
