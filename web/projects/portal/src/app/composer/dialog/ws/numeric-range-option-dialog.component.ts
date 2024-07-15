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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormControl, Validators } from "@angular/forms";
import { NumericRangeRef } from "../../../common/data/numeric-range-ref";
import { NumericRange } from "../../../widget/dialog/value-range-selectable-list/value-range-selectable-list.component";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { ValueRangeDialogModel } from "../../data/ws/value-range-dialog-model";
import { InclusiveType } from "../../../common/data/inclusive-type.enum";

@Component({
   selector: "numeric-range-option-dialog",
   templateUrl: "numeric-range-option-dialog.component.html",
   styleUrls: ["numeric-range-option-dialog.component.scss"]
})
export class NumericRangeOptionDialog implements OnInit {
   @Input() model: ValueRangeDialogModel;
   @Input() submitCallback: (model: ValueRangeDialogModel) => Promise<boolean> =
      () => Promise.resolve(true);
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   nameControl: UntypedFormControl;
   selectedIndex: number = 0;
   rangeValue: number;
   ref: NumericRangeRef;
   rangeList: NumericRange[] = [];
   labelsMap: Map<string, string>;
   formValid = () => !this.okDisabled();

   ngOnInit() {
      if((<NumericRangeRef> this.model.ref).vinfo) {
         this.ref = <NumericRangeRef> this.model.ref;
         this.initLabelsMap();
         this.updateRangeList();
      }
      else {
         console.error(this.model);
      }

      if(this.model.oldName == null) {
         this.nameControl = new UntypedFormControl(this.model.oldName,
            [Validators.required, FormValidators.notWhiteSpace,
               FormValidators.calcSpecialCharacters]);
      }
   }

   get upperInclusive(): boolean {
      return this.ref.vinfo.inclusiveType === InclusiveType.UPPER;
   }

   set upperInclusive(upper: boolean) {
      this.ref.vinfo.inclusiveType = upper == true ? InclusiveType.UPPER : InclusiveType.LOWER;
   }

   initLabelsMap() {
      this.labelsMap = new Map<string, string>();
      const labels = this.ref.vinfo.labels;

      if(labels.length > 0) {
         const values = this.ref.vinfo.values;
         let bottomOffset = 0;

         if(this.ref.vinfo.showingBottomValue) {
            const lowerOp = this.getBoundsOperator(false);
            const key = `${lowerOp} ${values[0]}`;
            this.labelsMap.set(key, !!labels[0] ? labels[0] : "");
            bottomOffset = 1;
         }

         for(let i = 0; i < values.length - 1; i++) {
            const key = `${values[i]} - ${values[i + 1]}`;
            const label = labels[i + bottomOffset];
            this.labelsMap.set(key, !!label ? label : "");
         }

         if(this.ref.vinfo.showingTopValue) {
            const bottom = values[values.length - 1];
            const upperOp = this.getBoundsOperator(true);
            const key = `${upperOp} ${bottom}`;
            const label = labels[labels.length - 1];
            this.labelsMap.set(key, !!label ? label : "");
         }

         if(values.length === 1 && this.ref.vinfo.showingBottomValue &&
            !this.ref.vinfo.showingTopValue)
         {
            const key = `${values[0]} - ${values[0]}`;
            this.labelsMap.set(key, !!labels[0] ? labels[0] : "");
         }
      }
   }

   private updateRangeList() {
      if(this.ref.vinfo.values.length === 0) {
         this.rangeList = [];
      }
      else {
         const newRangeList: NumericRange[] = [];
         const values = this.ref.vinfo.values;

         if(this.ref.vinfo.showingBottomValue) {
            const lowerOp = this.getBoundsOperator(false);
            const key = `${lowerOp} ${values[0]}`;
            const label = this.labelsMap.get(key);

            newRangeList.push({
               top: values[0],
               bottom: null,
               key: key,
               label: label != null ? label : ""
            });
         }

         for(let i = 0; i < values.length - 1; i++) {
            const bottom = values[i];
            const top = values[i + 1];
            const key = `${bottom} - ${top}`;
            const label = this.labelsMap.get(key);

            newRangeList.push({
               top,
               bottom,
               key,
               label: label != null ? label : ""
            });
         }

         if(this.ref.vinfo.showingTopValue) {
            const bottom = values[values.length - 1];
            const upperOp = this.getBoundsOperator(true);
            const key = `${upperOp} ${bottom}`;
            const label = this.labelsMap.get(key);

            newRangeList.push({
               top: null,
               bottom,
               key,
               label: label != null ? label : ""
            });
         }

         if(newRangeList.length === 0) {
            const value = values[0];
            const key = `${value} - ${value}`;
            const label = this.labelsMap.get(key);

            newRangeList.push({
               bottom: value,
               top: value,
               key,
               label: label != null ? label : ""
            });
         }

         this.rangeList = newRangeList;
      }

      if(this.rangeList.length === 0) {
         this.selectedIndex = undefined;
      }
      else {
         this.selectedIndex = Math.min(this.selectedIndex || 0, this.rangeList.length - 1);
      }
   }

   /**
    * Get the operator for the upper and lower bounds
    *
    * @return one of '>=', '>', '<', or '<='
    */
   private getBoundsOperator(upper: boolean = false) {
      if(upper) {
         return this.upperInclusive ? ">" : ">=";
      }
      else {
         return this.upperInclusive ? "<=" : "<";
      }
   }

   close() {
      this.onCancel.emit("cancel");
   }

   ok() {
      this.prepareModel();
      this.submitCallback(this.model).then((validated) => {
         if(validated) {
            this.onCommit.emit(this.model);
         }
      });
   }

   enterRange(event: KeyboardEvent) {
      if(!event.repeat) {
         this.add();
      }
   }

   add(): void {
      if(this.rangeValue == null) {
         return;
      }

      let values = this.ref.vinfo.values;
      let i: number;

      for(i = 0; i < values.length; i++) {
         if(values[i] === this.rangeValue)
         {
            return;
         }

         if(values[i] > this.rangeValue) {
            break;
         }
      }

      this.ref.vinfo.values = [...values.slice(0, i), this.rangeValue, ...values.slice(i)];
      this.updateRangeList();
      this.rangeValue = null;
   }

   remove() {
      const range = this.rangeList[this.selectedIndex];
      const value = range.bottom != null ? range.bottom : range.top;
      let values = this.ref.vinfo.values;
      let removeIndex = values.indexOf(value);
      values.splice(removeIndex, 1);
      this.updateRangeList();
   }

   clear() {
      this.ref.vinfo.values = [];
      this.selectedIndex = 0;
      this.updateRangeList();
   }

   minChange(checked: boolean) {
      this.updateRangeList();

      if(checked) {
         this.selectedIndex = Math.min(this.rangeList.length, this.selectedIndex + 1);
      }
      else {
         this.selectedIndex = Math.max(0, this.selectedIndex - 1);
      }
   }

   maxChange(checked: boolean) {
      if(!checked && this.selectedIndex === this.ref.vinfo.values.length - 1) {
         this.selectedIndex = this.ref.vinfo.values.length - 2;
      }

      this.updateRangeList();
   }

   okDisabled(): boolean {
      return this.nameControl && !this.nameControl.valid;
   }

   updateLabel(index: number, label: string) {
      const ref = this.rangeList[index];
      const oldLabel = ref.label;
      const newLabel = label.trim();

      if(oldLabel !== newLabel) {
         this.labelsMap.set(ref.key, newLabel);
      }
   }

   private prepareModel() {
      if(this.nameControl) {
         this.model.newName = this.nameControl.value.trim();
      }

      this.updateRangeList();
      this.ref.vinfo.labels = [];

      for(const rangeRef of this.rangeList) {
         let label: string = this.labelsMap.get(rangeRef.key);

         if(label == null || label === "") {
            label = null;
         }
         else {
            label = label.trim();
         }

         this.ref.vinfo.labels.push(label);
      }
   }

   inclusiveChange(event: any) {
      this.updateRangeList();
   }

   get baseColumn(): string {
      return this.model && this.model.ref && this.model.ref.ref ? this.model.ref.ref.view : "";
   }
}
