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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { ComboMode, ValueMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { NumericRangePaneModel } from "../../data/vs/numeric-range-pane-model";
import { UntypedFormGroup } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

@Component({
   selector: "numeric-range-pane",
   templateUrl: "numeric-range-pane.component.html",
})
export class NumericRangePane {
   @Input() model: NumericRangePaneModel;
   @Input() form: UntypedFormGroup;
   @Input() variableValues: string[];
   @Input() vsId: string = null;
   @Output() isInputValid: EventEmitter<boolean> = new EventEmitter<boolean>();
   mode: ValueMode = ValueMode.NUMBER;
   incrementType: ComboMode = ComboMode.VALUE;

   constructor(private modalService: NgbModal) {
   }

   onMinimumChange(minimum: any) {
      if(this.isNullOrUndefined(minimum) || (+this.model.minimum >= +this.model.maximum)) {
         this.isInputValid.emit(false);
      }
      else {
         this.isInputValid.emit(true);
      }
   }

   onMaximumChange(maximum: any) {
      if(this.isNullOrUndefined(maximum) || (+this.model.minimum >= +this.model.maximum)) {
         this.isInputValid.emit(false);
      }
      else {
         this.isInputValid.emit(true);
      }
   }

   onTypeChange(type: ComboMode) {
      this.incrementType = type;
      this.onIncrementChange(this.model.increment);
   }

   onIncrementChange(increment: any) {
      let incrementString = increment + "";

      if(this.isNullOrUndefined(increment) || !this.isIncrementValid()) {
         this.isInputValid.emit(false);
      }
      else {
         this.isInputValid.emit(true);
      }
   }

   isNullOrUndefined(value: any): boolean {
      return !value && ((value + "") != "0");
   }

   isInvalidMinimumValue(): boolean {
      return !this.isNullOrUndefined(this.model.minimum) &&
         !this.isNullOrUndefined(this.model.maximum) &&
         (+this.model.minimum >= +this.model.maximum);
   }

   isIncrementValid(): boolean {
      return !!this.model.increment &&
         (this.incrementType != ComboMode.VALUE || Number(this.model.increment) > 0);
   }
}
