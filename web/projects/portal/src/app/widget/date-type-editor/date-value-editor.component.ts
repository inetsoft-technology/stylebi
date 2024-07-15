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
import {
   Component,
   ElementRef,
   forwardRef,
   Input,
   Renderer2,
   ViewChild
} from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from "@angular/forms";
import { NgbDateParserFormatter, NgbDateStruct } from "@ng-bootstrap/ng-bootstrap";
import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";
import { Tool } from "../../../../../shared/util/tool";

export const DATE_VALUE_ACCESSOR: any = {
   provide: NG_VALUE_ACCESSOR,
   useExisting: forwardRef(() => DateValueEditorComponent), // eslint-disable-line @typescript-eslint/no-use-before-define
   multi: true
};

@Component({
   selector: "date-value-editor",
   templateUrl: "date-value-editor.component.html",
   styleUrls: ["date-value-editor.component.scss"],
   providers: [DATE_VALUE_ACCESSOR]
})
export class DateValueEditorComponent implements ControlValueAccessor {
   @ViewChild("datepickerMax") ngbDatepicker;
   @Input() format: string = DateTypeFormatter.ISO_8601_DATE_FORMAT;
   @Input() disabled: boolean = false;
   date: NgbDateStruct;
   initDate: NgbDateStruct;
   dateValue: any;
   onChange = (_: any) => {};
   onTouched = () => {};
   private closeDatepickerListener: () => any;

   @Input() set model(dateString: string) {
      let dateObject = new Date(dateString);

      if(!!dateString && !isNaN(dateObject.getTime())) {
         let date1: NgbDateStruct = {
            year: dateObject.getFullYear(),
            month: dateObject.getMonth() + 1,
            day: dateObject.getDate()
         };

         this.changeDate(date1);
         this.initDate = this.date;
      }
   }

   constructor(private ngbDateParserFormatter: NgbDateParserFormatter,
               private renderer: Renderer2,
               private element: ElementRef)
   {
   }

   changeDate(date: string | NgbDateStruct) {
      if(!(typeof date === "string") && !Tool.isEquals(this.date, date)) {
         this.date = date;
         this.propagateDateChange();
      }
   }

   change(date: any): void {
      if(typeof date === "string") {
         const fmtDate = DateTypeFormatter.formatStr(date, DateTypeFormatter.ISO_8601_DATE_FORMAT);

         if(fmtDate != null) {
            const newDate = this.ngbDateParserFormatter.parse(fmtDate);

            if(newDate != null && !Tool.isEquals(this.date, newDate)) {
               this.date = newDate;
               this.initDate = this.date;
               this.propagateDateChange();
            }
         }
         else {
            const newDate = this.ngbDateParserFormatter.parse(date);

            if(newDate != null && !Tool.isEquals(this.date, newDate)) {
               this.date = newDate;
               this.initDate = this.date;
               this.propagateDateChange();

               return;
            }

            this.initDate = {year: null, month: null, day: null};

            if(this.date != null) {
               this.date = null;
               this.propagateDateChange();
            }
         }
      }
   }

   propagateDateChange(touched: boolean = true) {
      if(touched) {
         this.onTouched();
      }

      if(this.date != null) {
         if(this.date.year && this.date.year < 100) {
            this.date.year = Math.floor(new Date().getFullYear() / 100) * 100 + this.date.year;
         }

         let formattedDate = this.ngbDateParserFormatter.format(this.date);
         formattedDate = DateTypeFormatter
            .transformValue(formattedDate, DateTypeFormatter.ISO_8601_DATE_FORMAT, this.format);
         this.onChange(formattedDate);
      }
      else {
         this.onChange(null);
      }
   }

   /**
    * Write a new value to the element.
    */
   writeValue(date: string) {
      let formattedDate = DateTypeFormatter
         .transformValue(date, this.format, DateTypeFormatter.ISO_8601_DATE_FORMAT);
      this.date = this.ngbDateParserFormatter.parse(formattedDate);
      this.initDate = this.date ? this.date : { year: null, month: null, day: null };
   }

   /**
    * Set the function to be called when the control receives a change event.
    */
   registerOnChange(fn: any) {
      this.onChange = fn;
   }

   /**
    * Set the function to be called when the control receives a touch event.
    */
   registerOnTouched(fn: any) {
      this.onTouched = fn;
   }

   /**
    * This function is called when the control status changes to or from "DISABLED".
    * Depending on the value, it will enable or disable the appropriate DOM element.
    *
    * @param isDisabled
    */
   setDisabledState(isDisabled: boolean) {
      this.disabled = isDisabled;
   }

   toggleDatepicker() {
      this.ngbDatepicker.toggle();
      this.initDateValue();
      this.initCloseDatepickerListener();
   }

   initDateValue() {
      if(this.initDate == null || this.initDate.year == null &&
         this.initDate.month == null && this.initDate.day == null)
      {
         let dateObject = new Date();
         this.initDate = {
            year: dateObject.getFullYear(),
            month: dateObject.getMonth() + 1,
            day: dateObject.getDate()
         };

         this.date = this.initDate;
         this.propagateDateChange();
      }
   }

   initCloseDatepickerListener(): void {
      if(this.closeDatepickerListener == null) {
         this.closeDatepickerListener = this.renderer.listen("document", "mouseup",
            (event: MouseEvent) => {
               if(!this.element.nativeElement.contains(event.target)) {
                  this.ngbDatepicker.close();

                  // remove the listener
                  this.closeDatepickerListener();
                  this.closeDatepickerListener = null;
               }
            });
      }
   }

   closeDatepicker(event: MouseEvent) {
      if(!this.element.nativeElement.contains(event.target)) {
         this.ngbDatepicker.close();
      }
   }
}
