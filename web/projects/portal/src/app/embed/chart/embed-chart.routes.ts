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
import { Routes, UrlMatchResult, UrlSegment } from "@angular/router";
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
import { canDeactivateGuard } from "../../common/services/can-deactivate-guard.service";
import { EmbedChartComponent } from "./embed-chart.component";

export function EMBED_CHART_URL_MATCHER(url: UrlSegment[]): UrlMatchResult {
   let result: UrlMatchResult = null;

   if(url && url.length > 0) {
      const params: { [name: string]: UrlSegment } = {};
      result = {
         consumed: url,
         posParams: params
      };

      if(url.length > 0) {
         let assetScope: UrlSegment = null;
         let assetOwner: UrlSegment = null;
         let assetPath: string = null;
         let assetId: string = null;

         if(url[0].path === "global") {
            assetScope = url[0];

            if(url.length > 1) {
               assetPath = url.slice(1, url.length - 1).map((s) => s.path).join("/");
               assetId = `1^128^__NULL__^${assetPath}`;
            }
         }
         else if(url[0].path === "user") {
            assetScope = url[0];

            if(url.length > 1) {
               assetOwner = url[1];

               if(url.length > 2) {
                  assetPath = url.slice(2, url.length - 1).map((s) => s.path).join("/");
                  assetId = `4^128^${assetOwner.path}^${assetPath}`;
               }
            }
         }
         else {
            assetId = url.slice(0, url.length - 1).map((s) => s.path).join("/");
            const match = /^([14])+\^128\^([^^]+)\^(.+)$/.exec(assetId);

            if(match) {
               if(match[1] == "1") {
                  assetScope = new UrlSegment("global", {});
               }
               else {
                  assetScope = new UrlSegment("user", {});
                  assetOwner = new UrlSegment(match[2], {});
               }

               assetPath = match[3];
            }
         }

         if(assetScope) {
            params.assetScope = assetScope;
         }

         if(assetOwner) {
            params.assetOwner = assetOwner;
         }

         if(assetPath) {
            params.assetPath = new UrlSegment(assetPath, {});
         }

         if(assetId) {
            params.assetId = new UrlSegment(assetId, {});
         }

         params.assemblyName = new UrlSegment(url[url.length - 1].path, {});
      }
   }

   return result;
}

export const embedChartRoutes: Routes = [
   {
      component: EmbedChartComponent,
      canDeactivate: [canDeactivateGuard],
      matcher: EMBED_CHART_URL_MATCHER,
      providers: [
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
      ]
   }
];
