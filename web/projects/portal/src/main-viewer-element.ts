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
import { createApplication } from "@angular/platform-browser";
import { createCustomElement } from "@angular/elements";
import { HttpClient } from "@angular/common/http";
import { Injector, Optional } from "@angular/core";
import { provideRouter } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { embedElementConfig } from "./app/embed/embed-element.config";
import { EmbedViewerComponent } from "./app/embed/viewer/embed-viewer.component";
import { FontService } from "./app/widget/services/font.service";
import { ModelService } from "./app/widget/services/model.service";

// VSObjectModule chain providers
import { CheckFormDataService } from "./app/vsobjects/util/check-form-data.service";
import { DataTreeValidatorService } from "./app/vsobjects/dialog/data-tree-validator.service";
import { FormInputService } from "./app/vsobjects/util/form-input.service";
import { GlobalSubmitService } from "./app/vsobjects/util/global-submit.service";
import { FileUploadService } from "./app/common/services/file-upload.service";
import { PropertyDialogService } from "./app/vsobjects/util/property-dialog.service";
import { FullScreenService } from "./app/common/services/full-screen.service";
import { RichTextService } from "./app/vsobjects/dialog/rich-text-dialog/rich-text.service";
import { CKEditorRichTextService } from "./app/vsobjects/dialog/rich-text-dialog/ckeditor-rich-text.service";
import { DndService } from "./app/common/dnd/dnd.service";
import { VSDndService } from "./app/common/dnd/vs-dnd.service";
import { DateComparisonService } from "./app/vsobjects/util/date-comparison.service";
import { ChartEditorService } from "./app/binding/services/chart/chart-editor.service";
import { VSChartEditorService } from "./app/binding/services/chart/vs-chart-editor.service";
import { BindingService } from "./app/binding/services/binding.service";
import { VSBindingService } from "./app/binding/services/vs-binding.service";
import { ToolbarActionsHandler } from "./app/vsobjects/toolbar-actions-handler";
import { ComposerRecentService } from "./app/composer/gui/composer-recent.service";
import { ScriptService } from "./app/composer/gui/script/script.service";
import { SelectionMobileService } from "./app/vsobjects/objects/selection/services/selection-mobile.service";

// Smaller widget module providers
import { AssetTreeService } from "./app/widget/asset-tree/asset-tree.service";
import { RecentColorService } from "./app/widget/color-picker/recent-color.service";
import { ConditionDialogService } from "./app/widget/condition/condition-dialog.service";
import { FormulaFunctionAnalyzerService } from "./app/widget/dialog/script-pane/formula-function-analyzer.service";
import { FixedDropdownService } from "./app/widget/fixed-dropdown/fixed-dropdown.service";
import { DropdownStackService } from "./app/widget/fixed-dropdown/dropdown-stack.service";
import { FormulaEditorService } from "./app/widget/formula-editor/formula-editor.service";
import { HelpUrlService } from "./app/widget/help-link/help-url.service";
import { RepositoryTreeService } from "./app/widget/repository-tree/repository-tree.service";
import { TooltipService } from "./app/widget/tooltip/tooltip.service";

// EmbedViewerModule providers
import { MiniToolbarService } from "./app/vsobjects/objects/mini-toolbar/mini-toolbar.service";
import { VSChartService } from "./app/vsobjects/objects/chart/services/vs-chart.service";
import { SlideOutService } from "./app/widget/slide-out/slide-out.service";
import { UIContextService } from "./app/common/services/ui-context.service";
import { ShowHyperlinkService } from "./app/vsobjects/show-hyperlink.service";
import { VSTabService } from "./app/vsobjects/util/vs-tab.service";
import { PageTabService } from "./app/viewer/services/page-tab.service";
import {
   DialogService,
   ViewerDialogServiceFactory
} from "./app/widget/slide-out/dialog-service.service";
import { ViewsheetClientService } from "./app/common/viewsheet-client";
import { ScaleService } from "./app/widget/services/scale/scale-service";
import { VSScaleService } from "./app/widget/services/scale/vs-scale.service";
import {
   ComposerToken,
   ContextProvider,
   EmbedToken,
   ViewerContextProviderFactory
} from "./app/vsobjects/context-provider.service";
import { ChartService } from "./app/graph/services/chart.service";

import "./main-base-element";

(window as any).globalPostParams = null;

createApplication({
   providers: [
      ...embedElementConfig.providers,
      provideRouter([]),

      // VSObjectModule chain providers
      DataTreeValidatorService,
      FormInputService,
      GlobalSubmitService,
      FileUploadService,
      PropertyDialogService,
      {
         provide: RichTextService,
         useClass: CKEditorRichTextService,
         deps: [FontService, NgbModal, HttpClient]
      },
      {
         provide: DndService,
         useClass: VSDndService,
         deps: [HttpClient]
      },
      DateComparisonService,
      {
         provide: ChartEditorService,
         useClass: VSChartEditorService,
         deps: [BindingService, ModelService]
      },
      {
         provide: BindingService,
         useClass: VSBindingService,
         deps: [ModelService, HttpClient]
      },
      ToolbarActionsHandler,
      ComposerRecentService,
      ScriptService,
      SelectionMobileService,

      // Smaller widget module providers
      AssetTreeService,
      RecentColorService,
      ConditionDialogService,
      FormulaFunctionAnalyzerService,
      FixedDropdownService,
      DropdownStackService,
      FormulaEditorService,
      HelpUrlService,
      RepositoryTreeService,

      // EmbedViewerModule providers (these override VSObjectModule versions for duplicate tokens)
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
      NgbModal
   ]
}).then(app => {
   const embedViewer = createCustomElement(EmbedViewerComponent, {injector: app.injector});
   customElements.define("inetsoft-viewer", embedViewer);
}).catch(err => console.error(err));

/**
 * Check if inetsoft is connected on app load in case there is no need to log in such as when
 * security is disabled or there is an active session
 */
(window as any).checkInetsoftConnection(null, false);
