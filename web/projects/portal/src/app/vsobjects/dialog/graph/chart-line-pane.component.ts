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
import { Component, Input, OnInit } from "@angular/core";
import { ChartLinePaneModel } from "../../model/chart-line-pane-model";
import { UntypedFormControl, UntypedFormGroup } from "@angular/forms";
import { FormValidators } from "../../../../../../shared/util/form-validators";

@Component({
   selector: "chart-line-pane",
   templateUrl: "chart-line-pane.component.html",
   styleUrls: ["chart-line-pane.component.scss"]
})
export class ChartLinePane implements OnInit {
   @Input() model: ChartLinePaneModel;
   @Input() form: UntypedFormGroup;
   trendlineOptions: {value, label}[] = [
      {value: "NONE", label: "_#(js:None)"},
      {value: "Linear", label: "_#(js:Linear)"},
      {value: "Quadratic", label: "_#(js:Quadratic)"},
      {value: "Cubic", label: "_#(js:Cubic)"},
      {value: "Exponential", label: "_#(js:Exponential)"},
      {value: "Logarithmic", label: "_#(js:Logarithmic)"},
      {value: "Power", label: "_#(js:Power)"}];

   ngOnInit(): void {
      this.initForm();
   }

   initForm(): void {
      if(this.model.lineTabVisible) {
         this.form = new UntypedFormGroup({
            projectForward: new UntypedFormControl(
               {value: this.model.projectForward, disabled: this.projectForwardDisabled},
               [FormValidators.positiveIntegerInRange, FormValidators.isInteger()]
            ),
         });
      }
      else {
         this.form = new UntypedFormGroup({});
      }
   }

   get noTrendlineSelected() {
      return this.model.trendLineType === this.trendlineOptions[0].value;
   }

   get projectForwardDisabled() {
      return !this.model.projectForwardEnabled || this.noTrendlineSelected;
   }

   setEnabled() {
      this.form.controls["projectForward"]
         .reset({value: this.model.projectForward, disabled: this.projectForwardDisabled});
   }

   onInputProjectForward(evt: KeyboardEvent): void {
      // only accept 0~9
      if(!(evt.charCode >= 48 && evt.charCode <= 57 || evt.charCode == 0)) {
         evt.preventDefault();
         evt.stopPropagation();
      }
   }
}
