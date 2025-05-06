/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import { Injector, NgModule, Optional } from "@angular/core";
import { createCustomElement } from "@angular/elements";
import { DownloadModule } from "../../../../../shared/download/download.module";
import { AngularResizeEventModule } from "../../../../../shared/resize-event/angular-resize-event.module";
import { VSObjectModule } from "../../vsobjects/vs-object.module";
import { EmbedViewerComponent } from "./embed-viewer.component";
import { CommonModule } from "@angular/common";
import {
   DataTipDirectivesModule
} from "../../vsobjects/objects/data-tip/data-tip-directives.module";
import { MiniToolbarModule } from "../../vsobjects/objects/mini-toolbar/mini-toolbar.module";
import { InteractModule } from "../../widget/interact/interact.module";
import { PageTabService } from "../../viewer/services/page-tab.service";
import { UIContextService } from "../../common/services/ui-context.service";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { DataTipService } from "../../vsobjects/objects/data-tip/data-tip.service";
import { PopComponentService } from "../../vsobjects/objects/data-tip/pop-component.service";
import { MiniToolbarService } from "../../vsobjects/objects/mini-toolbar/mini-toolbar.service";
import { VSChartService } from "../../vsobjects/objects/chart/services/vs-chart.service";
import { SlideOutService } from "../../widget/slide-out/slide-out.service";
import { CheckFormDataService } from "../../vsobjects/util/check-form-data.service";
import { ShowHyperlinkService } from "../../vsobjects/show-hyperlink.service";
import { VSTabService } from "../../vsobjects/util/vs-tab.service";
import { RichTextService } from "../../vsobjects/dialog/rich-text-dialog/rich-text.service";
import { FullScreenService } from "../../common/services/full-screen.service";
import {
   DialogService,
   ViewerDialogServiceFactory
} from "../../widget/slide-out/dialog-service.service";
import { DndService } from "../../common/dnd/dnd.service";
import { VSDndService } from "../../common/dnd/vs-dnd.service";
import { ModelService } from "../../widget/services/model.service";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { VSScaleService } from "../../widget/services/scale/vs-scale.service";
import {
   ComposerToken,
   ContextProvider,
   EmbedToken,
   ViewerContextProviderFactory
} from "../../vsobjects/context-provider.service";
import { ChartService } from "../../graph/services/chart.service";


@NgModule({
   imports: [
      CommonModule,
      DownloadModule,
      DataTipDirectivesModule,
      MiniToolbarModule,
      AngularResizeEventModule,
      InteractModule,
      VSObjectModule
   ],
   declarations: [EmbedViewerComponent],
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
      PageTabService,
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
         provide: EmbedToken,
         useValue: true
      },
      {
         provide: ContextProvider,
         useFactory: ViewerContextProviderFactory,
         deps: [[new Optional(), ComposerToken], [new Optional(), EmbedToken]]
      },
      {
         provide: ChartService,
         useExisting: VSChartService
      },
      NgbModal,
      PageTabService
   ],
   bootstrap: [EmbedViewerComponent]
})
export class EmbedViewerModule {
   constructor(public injector: Injector) {
      const embedViewer = createCustomElement(EmbedViewerComponent,
         {injector});
      customElements.define("inetsoft-viewer", embedViewer);
   }
}
