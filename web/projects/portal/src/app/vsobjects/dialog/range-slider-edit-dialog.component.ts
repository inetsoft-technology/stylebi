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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators, AbstractControl, ValidationErrors, ValidatorFn } from "@angular/forms";

@Component({
   selector: "range-slider-edit-dialog",
   templateUrl: "range-slider-edit-dialog.component.html",
})
export class RangeSliderEditDialog implements OnInit {
   @Input() currentMin: number | Date; //selected minimum value
   @Input() currentMax: number | Date; //selected max value
   @Input() rangeMin: number | Date; //range slider min value
   @Input() rangeMax: number | Date; //range slider max value

   @Output() onCommit: EventEmitter<{min: number | Date, max: number | Date}> =
      new EventEmitter<{min: number | Date, max: number | Date}>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();

   rangeForm: UntypedFormGroup;
   isDateType: boolean;
   timeIncrement: string;

   ngOnInit() {
      this.initForm();
   }

   initForm(): void {
      this.rangeForm = new UntypedFormGroup({});
      if(typeof this.rangeMin == 'number' && typeof this.rangeMax == 'number'){
         this.isDateType = false;
         this.rangeForm.addControl("min", new UntypedFormControl({}, [
            Validators.required,
            Validators.min(this.rangeMin),
            Validators.max(this.rangeMax)
         ]));
         this.rangeForm.addControl("max", new UntypedFormControl({}, [
            Validators.required,
            Validators.min(this.rangeMin),
            Validators.max(this.rangeMax)
         ]));
      }
      else if (this.rangeMin instanceof Date && this.rangeMax instanceof Date){
         this.isDateType = true;
         this.rangeForm.addControl("min", new UntypedFormControl(this.formatDate(this.currentMin), [
            Validators.required,
            this.dateRangeValidatorMin(this.rangeMin),
            this.dateRangeValidatorMax(this.rangeMax)
         ]));
         this.rangeForm.addControl("max", new UntypedFormControl(this.formatDate(this.currentMax), [
            Validators.required,
            this.dateRangeValidatorMin(this.rangeMin),
            this.dateRangeValidatorMax(this.rangeMax)
         ]));
         this.rangeForm.get('min')?.valueChanges.subscribe(value => {
           this.currentMin = value;
         });
         this.rangeForm.get('max')?.valueChanges.subscribe(value => {
           this.currentMax = value;
         });
      }
   }

   formatDate(date): string {
      const d = new Date(date);
      const year = d.getFullYear();
      const month = ('0' + (d.getMonth() + 1)).slice(-2);
      const day = ('0' + d.getDate()).slice(-2);

      if (this.timeIncrement != 't') {
         return `${year}-${month}-${day}`;
      } else {
         const hours = d.getHours().toString().padStart(2, '0');
         const minutes = d.getMinutes().toString().padStart(2, '0');
         return `${year}-${month}-${day}T${hours}:${minutes}`;
      }
   }

   dateRangeValidatorMin(min: Date): ValidatorFn {
      const minTime = min.getTime();

      return (control: AbstractControl): ValidationErrors | null => {
         const value = control.value;

         if (!value) {
            return null;
         }

         const valueTime = this.timeIncrement !== 't' ? new Date(value + 'T00:00').getTime() :
                                                         new Date(value).getTime();

         if (valueTime < minTime){
            return {
               dateMinError: {
                  requiredMin: min,
                  actual: valueTime
               }
            };
         }

         return null;
      };
   }

   dateRangeValidatorMax(max: Date): ValidatorFn {
      const maxTime = max.getTime();

      return (control: AbstractControl): ValidationErrors | null => {
         const value = control.value;

         if (!value) {
            return null;
         }

         const valueTime = this.timeIncrement !== 't' ? new Date(value + 'T00:00').getTime() :
                                                                  new Date(value).getTime();

         if (valueTime > maxTime) {
            return {
               dateMaxError: {
                  requiredMax: max,
                  actual: valueTime
               }
            };
         }

         return null;
      };
   }

   public getTimeIncrement(){
      return this.timeIncrement;
   }

   public setTimeIncrement(t: string){
      return this.timeIncrement = t;
   }

   close(): void {
      this.onCancel.emit("cancel");
   }

   ok(): void {
      this.onCommit.emit({
         min: this.currentMin,
         max: this.currentMax
      });
   }
}
