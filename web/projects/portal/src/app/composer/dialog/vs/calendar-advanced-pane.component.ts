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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { CalendarAdvancedPaneModel } from "../../data/vs/calendar-advanced-pane-model";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { FeatureFlagValue } from "../../../../../../shared/feature-flags/feature-flags.service";
import { UntypedFormControl, UntypedFormGroup, ValidationErrors, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { DynamicValueModel, ValueTypes } from "../../../vsobjects/model/dynamic-value-model";

@Component({
   selector: "calendar-advanced-pane",
   templateUrl: "calendar-advanced-pane.component.html",
})
export class CalendarAdvancedPane implements OnInit {
   @Input() model: CalendarAdvancedPaneModel;
   @Input() form: UntypedFormGroup;
   @Input() variableValues: string[];
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;
   @Output() viewMode = new EventEmitter<number>();
   @Output() showType = new EventEmitter<number>();
   FeatureFlag = FeatureFlagValue;

   ngOnInit() {
      this.initForm();
   }

   changeShowType(type: number) {
      this.model.showType = type;
      this.showType.emit(type);
   }

   changeViewMode(value: number) {
      this.model.viewMode = value;

      if(value == 2) {
         this.model.submitOnChange = false;
      }

      this.viewMode.emit(value);
   }

   updateForm(): void {
      if(!this.form) {
         return;
      }

      this.form.get("min").setValue(this.model.min.value);
      this.form.get("max").setValue(this.model.max.value);
      this.form.get("minGreaterThankMax").setValue([this.model.min.value, this.model.max.value]);
   }

   private initForm(): void {
      if(!this.form) {
         return;
      }

      this.form.addControl("min", new UntypedFormControl(this.model.min.value, [
         this.minAndMaxValidator(this.model.min)
      ]));

      this.form.addControl("max", new UntypedFormControl(this.model.max.value, [
         this.minAndMaxValidator(this.model.max)
      ]));

      this.form.addControl("minGreaterThankMax", new UntypedFormControl(this.model.max.value, [
         this.minGreaterThanMaxValidator()
      ]));
   }

   private minAndMaxValidator(dateModel: DynamicValueModel): (FormControl) => ValidationErrors | null {
      return (control) => {
         if(dateModel.type === ValueTypes.EXPRESSION || dateModel.type === ValueTypes.VARIABLE) {
            return null;
         }

         if(!control.value) {
            return null;
         }

         return FormValidators.isDate()(control);
      };
   }

   private minGreaterThanMaxValidator(): (FormControl) => ValidationErrors | null {
      return (control) => {
         if(this.model.max.type === ValueTypes.EXPRESSION || this.model.max.type === ValueTypes.VARIABLE ||
            this.model.min.type === ValueTypes.EXPRESSION || this.model.min.type === ValueTypes.VARIABLE)
         {
            return null;
         }

         let minValue = this.form.get("min")?.value;
         let maxValue = this.form.get("max")?.value;

         if(!maxValue || !minValue) {
            return null;
         }

         if(new Date(minValue).getTime() < new Date(maxValue).getTime()) {
            return null;
         }

         return { minGreaterThankMax: true };
      };
   }
}
