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
import { FormsModule } from "@angular/forms";
import { NgbDropdownModule, NgbNavModule } from "@ng-bootstrap/ng-bootstrap";
import { ColorPickerModule } from "../color-picker/color-picker.module";
import { ComputationComboBoxModule } from "../dialog/computation-combo-box/computation-combo-box.module";
import { WidgetDirectivesModule } from "../directive/widget-directives.module";
import { DynamicComboBoxModule } from "../dynamic-combo-box/dynamic-combo-box.module";
import { WidgetFormatModule } from "../format/widget-format.module";
import { ModalHeaderModule } from "../modal-header/modal-header.module";
import { BCategoricalColorPane } from "./b-categorical-color-pane.component";
import { BandPanel } from "./band-panel.component";
import { ChartTargetDialog } from "./chart-target-dialog.component";
import { DateInputField } from "./date-input-field.component";
import { GraphPaletteDialog } from "./graph-palette-dialog.component";
import { LabelInputField } from "./label-input-field.component";
import { LinePanel } from "./line-panel.component";
import { StatPanel } from "./stat-panel.component";
import { TargetComboBox } from "./target-combo-box.component";
import { TargetLabelPane } from "./target-label-pane.component";
import { ValueInputField } from "./value-input-field.component";
import {FixedDropdownModule} from "../fixed-dropdown/fixed-dropdown.module";
import {MouseEventModule} from "../mouse-event/mouse-event.module";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      NgbDropdownModule,
      ColorPickerModule,
      WidgetFormatModule,
      ComputationComboBoxModule,
      DynamicComboBoxModule,
      NgbNavModule,
      ModalHeaderModule,
      FixedDropdownModule,
      MouseEventModule,
   ],
   declarations: [
      BCategoricalColorPane,
      BandPanel,
      ChartTargetDialog,
      DateInputField,
      GraphPaletteDialog,
      LabelInputField,
      LinePanel,
      StatPanel,
      TargetComboBox,
      TargetLabelPane,
      ValueInputField
   ],
   exports: [
      BCategoricalColorPane,
      BandPanel,
      ChartTargetDialog,
      DateInputField,
      GraphPaletteDialog,
      LabelInputField,
      LinePanel,
      StatPanel,
      TargetComboBox,
      TargetLabelPane,
      ValueInputField
   ],
   providers: [],
})
export class TargetModule {
}
