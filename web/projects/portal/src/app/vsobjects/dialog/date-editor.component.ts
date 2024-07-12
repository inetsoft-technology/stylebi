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
import { Component, Input, OnInit, OnDestroy, EventEmitter, Output } from "@angular/core";
import { UntypedFormGroup, UntypedFormControl } from "@angular/forms";
import { DateEditorModel } from "../model/date-editor-model";
import { FormValidators } from "../../../../../shared/util/form-validators";

@Component({
   selector: "date-editor",
   templateUrl: "date-editor.component.html",
   styleUrls: ["date-editor.component.scss"]
})
export class DateEditor implements OnInit, OnDestroy {
   @Input() set model(value: DateEditorModel) {
      this.minDate = null;
      this.maxDate = null;
      this._model = value ? value : <DateEditorModel>{
         minimum: "",
         maximum: "",
         errorMessage: "",
      };

      if(this._model.minimum) {
         let min: string[] = this._model.minimum.split("-");

         if(min.length >= 3) {
            this.minDate = {
               year: parseInt(min[0], 10),
               month: parseInt(min[1], 10),
               day: parseInt(min[2], 10)
            };
         }
      }

      if(this._model.maximum) {
         let max: string[] = this._model.maximum.split("-");

         if(max.length >= 3) {
            this.maxDate = {
               year: parseInt(max[0], 10),
               month: parseInt(max[1], 10),
               day: parseInt(max[2], 10)
            };
         }
      }
   }

   @Input() parentForm: UntypedFormGroup;
   @Output() onUpdate = new EventEmitter<DateEditorModel>();
   _model: DateEditorModel;
   minDate: {
      year: number,
      month: number,
      day: number
   };
   maxDate: {
      year: number,
      month: number,
      day: number
   };
   form: UntypedFormGroup;

   ngOnInit(): void {
      this.form = new UntypedFormGroup({
         "min": new UntypedFormControl(this.minDate, []),
         "max": new UntypedFormControl(this.maxDate, [])
      }, FormValidators.dateSmallerThan("min", "max"));

      if(this.parentForm) {
         this.parentForm.addControl("date-editor", this.form);
      }
   }

   ngOnDestroy(): void {
      if(this.parentForm) {
         this.parentForm.removeControl("date-editor");
      }
   }

   updateMinDate(date: any): void {
      this.minDate = date;

      if(date && date.year) {
         this._model.minimum = date.year + "-" + date.month + "-" + date.day;
      }
      else {
         this._model.minimum = null;
      }

      this.onUpdate.emit(this._model);
   }

   updateMaxDate(date: any): void {
      this.maxDate = date;

      if(date && date.year) {
         this._model.maximum = date.year + "-" + date.month + "-" + date.day;
      }
      else {
         this._model.maximum = null;
      }

      this.onUpdate.emit(this._model);
   }
}
