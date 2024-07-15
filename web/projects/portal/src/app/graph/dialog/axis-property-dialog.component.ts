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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import {FormGroup, UntypedFormGroup} from "@angular/forms";
import { AxisPropertyDialogModel } from "../model/dialog/axis-property-dialog-model";
import { UIContextService } from "../../common/services/ui-context.service";

@Component({
   selector: "axis-property-dialog",
   templateUrl: "axis-property-dialog.component.html",
})
export class AxisPropertyDialog {
   @Input() axisType: string;
   @Input() model: AxisPropertyDialogModel;
   form: UntypedFormGroup;
   formValid = () => this.isValid();
   @Output() onCommit: EventEmitter<AxisPropertyDialogModel> =
      new EventEmitter<AxisPropertyDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @Output() onApply = new EventEmitter<{collapse: boolean, result: any}>();

   public constructor(private uiContextService: UIContextService) {
      this.form = new UntypedFormGroup({
         lineForm: new UntypedFormGroup({})
      });
   }

   get lineForm(): UntypedFormGroup {
      return this.form?.get("lineForm") as UntypedFormGroup;
   }

   get incrementValid(): boolean {
      const minor = this.model.axisLinePaneModel.minorIncrement;
      const increment = this.model.axisLinePaneModel.increment;

      if(minor == null && increment == null) {
         return true;
      }

      return isNaN(minor) || <any> minor == "" || isNaN(increment) ||
         <any> increment == "" || Number(minor) < Number(increment);
   }

   get minmaxValid(): boolean {
      const minimum = this.model.axisLinePaneModel.minimum;
      const maximum = this.model.axisLinePaneModel.maximum;

      if(minimum == null || maximum == null) {
         return true;
      }

      return isNaN(minimum) || <any> minimum == "" || isNaN(maximum) ||
         <any> maximum == "" || Number(minimum) < Number(maximum);
   }

   close(): void {
      this.onCancel.emit("cancel");
   }

   ok(): void {
      this.onCommit.emit(this.model);
   }

   apply(event: boolean): void {
      this.onApply.emit({collapse: event, result: this.model});
   }

   isValid(): boolean {
      return this.form.valid;
   }

   get defaultTab(): string {
      const defTab =
         this.uiContextService.getDefaultTab("axis-property-dialog", "labelTab");

      if(defTab === "aliasTab" && (this.model.linear || !this.model.aliasSupported)) {
         return "labelTab";
      }

      return defTab;
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab("axis-property-dialog", tab);
   }
}
