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

import { Component, Input, ViewChild } from "@angular/core";
import { ComboMode } from "../dynamic-combo-box/dynamic-combo-box-model";
import { DateInputField } from "./date-input-field.component";
import { MeasureInfo, TargetInfo } from "./target-info";
import { GraphTypes } from "../../common/graph-types";
import { Tool } from "../../../../../shared/util/tool";
import { DefaultPalette } from "../color-picker/default-palette";

@Component({
   selector: "line-panel",
   templateUrl: "line-panel.component.html",
})
export class LinePanel {
   @Input() model: TargetInfo;
   @Input() availableFields: MeasureInfo[];
   @Input() variables: string[] = [];
   @Input() hideDcombox: boolean;
   @Input() vsId: string = null;
   @Input() chartType: number;
   valueType: ComboMode = ComboMode.VALUE;
   alphaInvalid: boolean = false;
   enableFormulaLabelLinePanel: boolean = false;
   @ViewChild("dateField") dateField: DateInputField;
   fillPalette = DefaultPalette.bgWithTransparent;

   isChartScopeEnabled() {
      return !!this.model.value && isNaN(parseFloat(this.model.value)) &&
         !(Tool.isExpr(this.model.value) || Tool.isVar(this.model.value));
   }

   isFormulaSupported(): boolean {
      return this.model.measure && !!this.model.measure.label;
   }

   isDateField(): boolean {
      return this.model.measure && this.model.measure.dateField;
   }

   isTimeField(): boolean {
      return this.model.measure && this.model.measure.timeField;
   }

   onValueChange(nvalue: any) {
      this.model.value = nvalue;
   }

   onLabelChange(label: any) {
      this.model.label = label;
   }

   changeAlphaWarning(event) {
      this.alphaInvalid = event;
   }

   enableFromFormulaLabel(enable: boolean) {
      this.enableFormulaLabelLinePanel = enable;
   }

   isValid(): boolean {
      return !this.alphaInvalid && (this.dateField == null || this.dateField.valid);
   }

   get alphaEnabled(): boolean {
      return this.model.supportFill || this.chartType == GraphTypes.CHART_3D_BAR || this.chartType == GraphTypes.CHART_3D_BAR_STACK;
   }
}
