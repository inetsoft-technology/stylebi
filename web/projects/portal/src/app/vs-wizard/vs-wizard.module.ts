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
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { BindingModule } from "../binding/binding.module";
import { FormatModule } from "../format/format.module";
import { VSChartModule } from "../vsobjects/objects/chart/vs-chart.module";
import { VSObjectModule } from "../vsobjects/vs-object.module";
import { ConsoleDialogModule } from "../widget/console-dialog/console-dialog.module";
import { TreeModule } from "../widget/tree/tree.module";
import { NewViewsheetDialog } from "./gui/new-viewsheet-dialog.component";
import { ObjectWizardToolBarComponent } from "./gui/object-wizard-tool-bar.component";
import { ObjectSubTypePane } from "./gui/object-wizard/object-sub-type-pane.component";
import { ObjectTypeIcon } from "./gui/object-wizard/object-type-icon.component";
import { ObjectTypePane } from "./gui/object-wizard/object-type-pane.component";
import { ObjectWizardPane } from "./gui/object-wizard/object-wizard-pane.component";
import { VSWizardAggregateItem } from "./gui/object-wizard/wizard-aggregate-item.component";
import { VSWizardAggregatePane } from "./gui/object-wizard/wizard-aggregate-pane.component";
import { WizardBindingTree } from "./gui/object-wizard/wizard-binding-tree.component";
import { VSWizardDetailItem } from "./gui/object-wizard/wizard-detail-item.component";
import { VSWizardGroupItem } from "./gui/object-wizard/wizard-group-item.component";
import { WizardPreviewContainer } from "./gui/object-wizard/wizard-preview-container.component";
import { VSWizardPreviewPane } from "./gui/object-wizard/wizard-preview-pane.component";
import { WizardVisualizationPane } from "./gui/object-wizard/wizard-visualization-pane.component";
import { VsWizardObjectComponent } from "./gui/objects/vs-wizard-object.component";
import { WizardNewObject } from "./gui/objects/wizard-new-object.component";
import { VsWizardComponent } from "./gui/vs-wizard.component";
import { VsWizardGridPaneComponent } from "./gui/wizard-pane/vs-wizard-grid-pane.component";
import { VsWizardPane } from "./gui/wizard-pane/vs-wizard-pane.component";
import { WizardToolBarComponent } from "./gui/wizard-tool-bar/wizard-tool-bar.component";
import { StatusBarModule } from "../status-bar/status-bar.module";
import {
   VSLoadingDisplayModule
} from "../vsobjects/objects/vs-loading-display/vs-loading-display.module";
import { WidgetDirectivesModule } from "../widget/directive/widget-directives.module";
import { InteractModule } from "../widget/interact/interact.module";
import { DynamicComboBoxModule } from "../widget/dynamic-combo-box/dynamic-combo-box.module";
import { ModalHeaderModule } from "../widget/modal-header/modal-header.module";
import { NotificationsModule } from "../widget/notifications/notifications.module";
import { SplitPaneModule } from "../widget/split-pane/split-pane.module";
import { ToolbarGroupModule } from "../widget/toolbar/toolbar-group/toolbar-group.module";
import { FixedDropdownModule } from "../widget/fixed-dropdown/fixed-dropdown.module";
import { AssetTreeModule } from "../widget/asset-tree/asset-tree.module";
import { HelpLinkModule } from "../widget/help-link/help-link.module";
import { NgbTooltipModule } from "@ng-bootstrap/ng-bootstrap";
import { MiniToolbarModule } from "../vsobjects/objects/mini-toolbar/mini-toolbar.module";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      ReactiveFormsModule,
      FormatModule,
      VSObjectModule,
      BindingModule,
      StatusBarModule,
      VSLoadingDisplayModule,
      VSChartModule,
      WidgetDirectivesModule,
      InteractModule,
      DynamicComboBoxModule,
      TreeModule,
      ModalHeaderModule,
      NotificationsModule,
      SplitPaneModule,
      ToolbarGroupModule,
      FixedDropdownModule,
      AssetTreeModule,
      HelpLinkModule,
      NgbTooltipModule,
      MiniToolbarModule,
      ConsoleDialogModule
   ],
   declarations: [
      NewViewsheetDialog,
      ObjectSubTypePane,
      ObjectTypeIcon,
      ObjectTypePane,
      ObjectWizardPane,
      ObjectWizardToolBarComponent,
      VSWizardAggregateItem,
      VSWizardAggregatePane,
      VsWizardComponent,
      VSWizardDetailItem,
      VsWizardGridPaneComponent,
      VSWizardGroupItem,
      VsWizardObjectComponent,
      VsWizardPane,
      VSWizardPreviewPane,
      WizardBindingTree,
      WizardNewObject,
      WizardPreviewContainer,
      WizardToolBarComponent,
      WizardVisualizationPane
   ],
   exports: [
      NewViewsheetDialog,
      ObjectSubTypePane,
      ObjectTypeIcon,
      ObjectTypePane,
      ObjectWizardPane,
      ObjectWizardToolBarComponent,
      VSWizardAggregateItem,
      VSWizardAggregatePane,
      VsWizardComponent,
      VSWizardDetailItem,
      VsWizardGridPaneComponent,
      VSWizardGroupItem,
      VsWizardObjectComponent,
      VsWizardPane,
      VSWizardPreviewPane,
      WizardBindingTree,
      WizardNewObject,
      WizardPreviewContainer,
      WizardToolBarComponent,
      WizardVisualizationPane
   ]
})
export class VsWizardModule {
}
