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
import { Component, forwardRef, Input } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from "@angular/forms";
import { XSchema } from "../../../../../../portal/src/app/common/data/xschema";
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";

export const DATE_TIME_ACCESSOR: any = {
   provide: NG_VALUE_ACCESSOR,
   useExisting: forwardRef(() => DateTimeEditorComponent), // eslint-disable-line @typescript-eslint/no-use-before-define
   multi: true
};

@Component({
   selector: "em-date-time-editor",
   templateUrl: "./date-time-editor.component.html",
   styleUrls: ["./date-time-editor.component.scss"],
   providers: [DATE_TIME_ACCESSOR]
})
export class DateTimeEditorComponent implements ControlValueAccessor {
   @Input() type: string;
   public XSchema = XSchema;

   onChange = (_: any) => {
   };
   onTouched = () => {
   };
   disabled: boolean = false;
   value: string; // the whole formatted value
   time: string; // time portion of the value
   date: Date; // date portion of the value
   _format: string;

   @Input()
   public set format(format: string) {
      this._format = format;
   }

   public get format(): string {
      if(!!this._format) {
         return this._format;
      }

      if(this.type === XSchema.TIME_INSTANT) {
         return DateTypeFormatter.ISO_8601_TIME_INSTANT_FORMAT;
      }
      else if(this.type === XSchema.DATE) {
         return DateTypeFormatter.ISO_8601_DATE_FORMAT;
      }
      else {
         return DateTypeFormatter.ISO_8601_TIME_FORMAT;
      }
   }

   changeValue(date?: any) {
      this.date = !!date ? date : this.date;

      if(this.type === XSchema.TIME_INSTANT) {
         if(!!this.date && !!this.time) {
            this.value = DateTypeFormatter.transformValue(
               this.getDateString(this.date) + "T" + this.time,
               DateTypeFormatter.ISO_8601_TIME_INSTANT_FORMAT, this.format);
         }
         else {
            this.value = null;
         }
      }
      else if(this.type === XSchema.DATE) {
         if(!!this.date) {
            this.value = DateTypeFormatter.transformValue(this.getDateString(this.date),
               DateTypeFormatter.ISO_8601_DATE_FORMAT, this.format);
         }
         else {
            this.value = null;
         }
      }
      else {
         if(!!this.time) {
            this.value = DateTypeFormatter.transformValue(this.time,
               DateTypeFormatter.ISO_8601_TIME_FORMAT, this.format);
         }
         else {
            this.value = null;
         }
      }

      this.propagateValueChange();
   }

   propagateValueChange(touched: boolean = true) {
      if(touched) {
         this.onTouched();
      }

      this.onChange(this.value);
   }

   /**
    * Write a new value to the element.
    */
   writeValue(value: string) {
      if(!!value) {
         this.value = value;

         if(this.type === XSchema.TIME_INSTANT) {
            this.date = this.getDate(value);
            this.time = DateTypeFormatter.transformValue(value,
               this.format, DateTypeFormatter.ISO_8601_TIME_FORMAT);
         }
         else if(this.type === XSchema.DATE) {
            this.date = this.getDate(value);
         }
         else {
            this.time = DateTypeFormatter.transformValue(value,
               this.format, DateTypeFormatter.ISO_8601_TIME_FORMAT);
         }
      }
      else {
         this.value = value;
         this.date = null;
      }
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

   private getDateString(date: Date): string {
      let dateString = `${date.getFullYear()}-` +
         `${((date.getMonth() + 1) < 10 ? "0" : "") + (date.getMonth() + 1)}-` +
         `${(date.getDate() < 10 ? "0" : "") + date.getDate()}`;
      return dateString;
   }

   private getDate(dateStr: string): Date {
      const timeInstant = DateTypeFormatter.toTimeInstant(dateStr, this.format);
      return new Date(timeInstant.years, timeInstant.months, timeInstant.date);
   }
}
