/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, Input, OnInit } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup } from "@angular/forms";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { Tool } from "../../../../../shared/util/tool";
import { AxisLinePaneModel } from "../model/dialog/axis-line-pane-model";

@Component({
   selector: "axis-line-pane",
   templateUrl: "axis-line-pane.component.html",
   styleUrls: ["axis-line-pane.component.scss"]
})
export class AxisLinePane implements OnInit {
   @Input() linear: boolean;
   @Input() outer: boolean;
   @Input() timeSeries: boolean;
   @Input() model: AxisLinePaneModel;
   @Input() form: UntypedFormGroup;
   @Input() incrementValid: boolean;
   @Input() minmaxValid: boolean;

   ngOnInit(): void {
      this.initForm();
      if(this.model.fakeScale) {
         this.form.controls["minimum"].disable();
         this.form.controls["maximum"].disable();
         this.form.controls["majorIncrement"].disable();
         this.form.controls["minorIncrement"].disable();
      }
   }

   initForm(): void {
      if(!this.linear) {
         if(this.timeSeries && !this.outer) {
            this.form.addControl("majorIncrement", new UntypedFormControl(this.model.increment, [
               FormValidators.positiveNonZeroOrNull
            ]));
         }

         return;
      }

      this.form.addControl("minimum", new UntypedFormControl(this.model.minimum, [
         FormValidators.integerInRange(),
      ]));
      this.form.addControl("maximum", new UntypedFormControl(this.model.maximum, [
         FormValidators.integerInRange(),
      ]));
      this.form.addControl("majorIncrement", new UntypedFormControl({
         value: this.model.increment,
         disabled: this.model.logarithmicScale
      }, [ FormValidators.positiveNonZeroOrNull ]));
      this.form.addControl("minorIncrement", new UntypedFormControl(this.model.minorIncrement, [
         FormValidators.positiveNonZeroOrNull ]));
   }

   logarithmicScaleChanged(): void {
      if(this.linear) {
         Tool.setFormControlDisabled(this.form.get("majorIncrement"), this.model.logarithmicScale);
      }
   }
}
