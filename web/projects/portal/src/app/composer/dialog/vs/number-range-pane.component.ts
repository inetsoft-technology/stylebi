/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, Input, OnInit } from "@angular/core";
import {
   UntypedFormControl,
   UntypedFormGroup,
   Validators,
   ValidatorFn,
   ValidationErrors
} from "@angular/forms";

import { NumberRangePaneModel } from "../../data/vs/number-range-pane-model";
import { ValueMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { FormValidators } from "../../../../../../shared/util/form-validators";

@Component({
   selector: "number-range-pane",
   templateUrl: "number-range-pane.component.html",
})
export class NumberRangePane implements OnInit {
   @Input() model: NumberRangePaneModel;
   @Input() form: UntypedFormGroup;
   @Input() variableValues: string[];
   @Input() vsId: string = null;
   mode: ValueMode = ValueMode.NUMBER;

   ngOnInit() {
      this.initForm();
   }

   initForm(): void {
      this.form.addControl("min", new UntypedFormControl(this.model.min, [ Validators.required ]));
      this.form.addControl("max", new UntypedFormControl(this.model.max, [ Validators.required,
                                                                    this.largerThan("min") ]));
      this.form.addControl("minorIncrement", new UntypedFormControl(this.model.minorIncrement,
                                 [ FormValidators.positiveNonZeroOrNull]));
      this.form.addControl("majorIncrement", new UntypedFormControl(this.model.majorIncrement,
                                 [ this.largerThan("minorIncrement"),
                                   FormValidators.positiveNonZeroOrNull
                                 ]));
   }

   largerThan(min: string): ValidatorFn {
      return (control: UntypedFormControl): ValidationErrors => {
         if(this.form.controls[min]) {
            if(parseFloat(control.value) <= parseFloat(this.form.controls[min].value)) {
               return { lessThan: true };
            }
         }
         return null;
      };
   }

   updateValue(value: string, controlName: string, partnerControl: string): void {
      this.form.controls[controlName].setValue(value);
      this.form.controls[partnerControl].updateValueAndValidity();
   }
}
