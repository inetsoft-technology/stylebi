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
import { DateInputField } from "./date-input-field.component";
import { MeasureInfo, TargetInfo } from "./target-info";
import { GraphTypes } from "../../common/graph-types";
import { Tool } from "../../../../../shared/util/tool";
import { DefaultPalette } from "../color-picker/default-palette";

@Component({
   selector: "band-panel",
   templateUrl: "band-panel.component.html",
})
export class BandPanel {
   @Input() model: TargetInfo;
   @Input() availableFields: MeasureInfo[];
   @Input() variables: string[] = [];
   @Input() vsId: string = null;
   @Input() hideDcombox: boolean;
   @Input() chartType: number;
   enableFromFormulaLabelBandPanel: boolean = false;
   enableToFormulaLabelBandPanel: boolean = false;
   alphaInvalid: boolean = false;
   @ViewChild("dateField1") dateField1: DateInputField;
   @ViewChild("dateField2") dateField2: DateInputField;
   fillPalette = DefaultPalette.bgWithTransparent;

   isChartScopeEnabled(): boolean {
      return (!!this.model.value && isNaN(parseFloat(this.model.value)) ||
         !!this.model.toValue && isNaN(parseFloat(this.model.toValue))) &&
         !(Tool.isExpr(this.model.value) || Tool.isVar(this.model.value));
   }

   isDateField(): boolean {
      return this.model.measure && this.model.measure.dateField;
   }

   isTimeField(): boolean {
      return this.model.measure && this.model.measure.timeField;
   }

   isFormulaSupported(): boolean {
      return !!(this.model.measure && this.model.measure.label);
   }

   onFromValueChange(value: any) {
      this.model.value = value;
   }

   onToValueChange(value: any) {
      this.model.toValue = value;
   }

   onFromLabelChange(label: any) {
      this.model.label = label;
   }

   onToLabelChange(label: any) {
      this.model.toLabel = label;
   }

   enableFromFormulaLabel(enable: boolean) {
      this.enableFromFormulaLabelBandPanel = enable;
   }

   enableToFormulaLabel(enable: boolean) {
      this.enableToFormulaLabelBandPanel = enable;
   }

   changeAlphaWarning(event) {
      this.alphaInvalid = event;
   }

   isValid(): boolean {
      return !this.alphaInvalid &&
         (!this.isDateField() || (this.dateField1 == null || this.dateField1.valid)
          && (this.dateField2 == null || this.dateField2.valid));
   }

   get alphaEnabled(): boolean {
      return this.model.supportFill || this.chartType == GraphTypes.CHART_3D_BAR || this.chartType == GraphTypes.CHART_3D_BAR_STACK;
   }
}
