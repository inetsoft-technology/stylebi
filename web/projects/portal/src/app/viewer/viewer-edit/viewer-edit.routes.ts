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
import { Routes } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { SERVICE_PROVIDERS } from "../../composer/services.provider";
import { UIContextService } from "../../common/services/ui-context.service";
import { FullScreenService } from "../../common/services/full-screen.service";
import { ChartService } from "../../graph/services/chart.service";
import { ComposerToken, ContextProvider, ViewerContextProviderFactory } from "../../vsobjects/context-provider.service";
import { RichTextService } from "../../vsobjects/dialog/rich-text-dialog/rich-text.service";
import { VSChartService } from "../../vsobjects/objects/chart/services/vs-chart.service";
import { AdhocFilterService } from "../../vsobjects/objects/data-tip/adhoc-filter.service";
import { DataTipService } from "../../vsobjects/objects/data-tip/data-tip.service";
import { PopComponentService } from "../../vsobjects/objects/data-tip/pop-component.service";
import { MiniToolbarService } from "../../vsobjects/objects/mini-toolbar/mini-toolbar.service";
import { ShowHyperlinkService } from "../../vsobjects/show-hyperlink.service";
import { CheckFormDataService } from "../../vsobjects/util/check-form-data.service";
import { VSTabService } from "../../vsobjects/util/vs-tab.service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { VSScaleService } from "../../widget/services/scale/vs-scale.service";
import { DialogService, ViewerDialogServiceFactory } from "../../widget/slide-out/dialog-service.service";
import { SlideOutService } from "../../widget/slide-out/slide-out.service";
import { ViewerEditComponent } from "./viewer-edit.component";

export const viewerEditRoutes: Routes = [
   {
      path: "",
      component: ViewerEditComponent,
      providers: [
         ...SERVICE_PROVIDERS,
         DataTipService,
         PopComponentService,
         AdhocFilterService,
         MiniToolbarService,
         VSChartService,
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
            useFactory: ViewerContextProviderFactory,
            deps: [[new Optional(), ComposerToken]]
         },
         {
            provide: DialogService,
            useFactory: ViewerDialogServiceFactory,
            deps: [NgbModal, SlideOutService, Injector, UIContextService]
         },
         {
            provide: ChartService,
            useExisting: VSChartService
         }
      ]
   }
];
