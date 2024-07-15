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
import { NgbNavModule } from "@ng-bootstrap/ng-bootstrap";
import { ChartLinePane } from "../vsobjects/dialog/graph/chart-line-pane.component";
import { ColorPickerModule } from "../widget/color-picker/color-picker.module";
import { WidgetDirectivesModule } from "../widget/directive/widget-directives.module";
import { DynamicComboBoxModule } from "../widget/dynamic-combo-box/dynamic-combo-box.module";
import { WidgetFormatModule } from "../widget/format/widget-format.module";
import { ModalHeaderModule } from "../widget/modal-header/modal-header.module";
import { MultiSelectModule } from "../widget/multi-select/multi-select.module";
import { SlideOutModule } from "../widget/slide-out/slide-out.module";
import { TargetModule } from "../widget/target/target.module";
import { TooltipModule } from "../widget/tooltip/tooltip.module";
import { AliasPane } from "./dialog/alias-pane.component";
import { AlignmentCombo } from "./dialog/alignment-combo.component";
import { AxisLabelPane } from "./dialog/axis-label-pane.component";
import { AxisLinePane } from "./dialog/axis-line-pane.component";
import { AxisPropertyDialog } from "./dialog/axis-property-dialog.component";
import { ChartPlotOptionsPaneComponent } from "./dialog/chart-plot-options-pane.component";
import { ChartTargetLinesPane } from "./dialog/chart-target-lines-pane.component";
import { LegendFormatDialog } from "./dialog/legend-format-dialog.component";
import { LegendFormatGeneralPane } from "./dialog/legend-format-general-pane.component";
import { LegendScalePane } from "./dialog/legend-scale-pane.component";
import { TitleFormatDialog } from "./dialog/title-format-dialog.component";
import { TitleFormatPane } from "./dialog/title-format-pane.component";
import { ChartArea } from "./objects/chart-area.component";
import { ChartAxisArea } from "./objects/chart-axis-area.component";
import { ChartFacetArea } from "./objects/chart-facet.component";
import { ChartLegendArea } from "./objects/chart-legend-area.component";
import { ChartLegendContainer } from "./objects/chart-legend-container.component";
import { ChartNavBar } from "./objects/chart-nav-bar.component";
import { ChartPlotArea } from "./objects/chart-plot-area.component";
import { ChartTitleArea } from "./objects/chart-title-area.component";
import { ResizeModule } from "../widget/resize/resize.module";
import { MouseEventModule } from "../widget/mouse-event/mouse-event.module";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      ReactiveFormsModule,
      WidgetDirectivesModule,
      TooltipModule,
      WidgetFormatModule,
      ColorPickerModule,
      MultiSelectModule,
      DynamicComboBoxModule,
      ModalHeaderModule,
      SlideOutModule,
      TargetModule,
      NgbNavModule,
      ResizeModule,
      MouseEventModule
   ],
   declarations: [
      AliasPane,
      AlignmentCombo,
      AxisLabelPane,
      AxisLinePane,
      AxisPropertyDialog,
      ChartArea,
      ChartAxisArea,
      ChartFacetArea,
      ChartLegendArea,
      ChartLegendContainer,
      ChartLinePane,
      ChartPlotArea,
      ChartPlotOptionsPaneComponent,
      ChartTargetLinesPane,
      ChartTitleArea,
      ChartNavBar,
      LegendFormatDialog,
      LegendFormatGeneralPane,
      LegendScalePane,
      TitleFormatDialog,
      TitleFormatPane,
   ],
   exports: [
      AliasPane,
      AlignmentCombo,
      AxisLabelPane,
      AxisLinePane,
      AxisPropertyDialog,
      ChartArea,
      ChartAxisArea,
      ChartFacetArea,
      ChartLegendArea,
      ChartLegendContainer,
      ChartLinePane,
      ChartPlotArea,
      ChartPlotOptionsPaneComponent,
      ChartTargetLinesPane,
      ChartTitleArea,
      LegendFormatDialog,
      LegendFormatGeneralPane,
      LegendScalePane,
      ChartNavBar,
      TitleFormatDialog,
      TitleFormatPane,
   ],
   providers: []
})
export class GraphModule {
}
