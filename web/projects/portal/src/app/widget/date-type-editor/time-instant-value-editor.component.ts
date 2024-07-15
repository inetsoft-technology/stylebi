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
import { Component, EventEmitter, forwardRef, Input, Output, ViewChild } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from "@angular/forms";
import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";
import { DateValueEditorComponent } from "./date-value-editor.component";

export const TIME_INSTANT_VALUE_ACCESSOR: any = {
   provide: NG_VALUE_ACCESSOR,
   useExisting: forwardRef(() => TimeInstantValueEditorComponent), // eslint-disable-line @typescript-eslint/no-use-before-define
   multi: true
};

const zeroTime = "00:00:00";

@Component({
   selector: "time-instant-value-editor",
   templateUrl: "time-instant-value-editor.component.html",
   styleUrls: ["time-instant-value-editor.component.scss"],
   providers: [TIME_INSTANT_VALUE_ACCESSOR]
})
export class TimeInstantValueEditorComponent implements ControlValueAccessor {
   @ViewChild(DateValueEditorComponent) dateValueEditor;
   @Input() format: string = DateTypeFormatter.ISO_8601_TIME_INSTANT_FORMAT;
   @Input() disabled: boolean = false;
   @Input() fullContainer: boolean = false;
   @Output() timeChange: EventEmitter<string> = new EventEmitter<string>();
   formattedDate: string;
   formattedTime: string;
   onChange = (_: any) => {};
   onTouched = () => {};

   changeDate(value: string) {
      this.formattedDate = value;

      // If no time selected, set time to 0.
      if(this.formattedTime == null) {
         this.formattedTime = zeroTime;
      }

      this.propagateTimeInstantChange();
   }

   changeTime(value: string) {
      this.formattedTime = value;
      this.timeChange.emit(this.getTimeInstant());
   }

   propagateTimeInstantChange(touched: boolean = true) {
      if(touched) {
         this.onTouched();
      }

      this.onChange(this.getTimeInstant());
      this.timeChange.emit(this.getTimeInstant());
   }

   private getTimeInstant(): string {
      if(this.formattedDate != null && this.formattedTime != null) {
         const newTimeInstant = `${this.formattedDate}T${this.formattedTime}`;
         return DateTypeFormatter.transformValue(newTimeInstant,
            DateTypeFormatter.ISO_8601_TIME_INSTANT_FORMAT, this.format);
      }
      else {
         return null;
      }
   }

   /**
    * Write a new value to the element.
    */
   writeValue(timeInstant: string) {
      let formattedTimeInstant: string;
      this.formattedDate = null;
      this.formattedTime = null;

      if(timeInstant != null) {
         formattedTimeInstant = DateTypeFormatter
            .transformValue(timeInstant, this.format,
               DateTypeFormatter.ISO_8601_TIME_INSTANT_FORMAT);

         if(formattedTimeInstant) {
            let split = formattedTimeInstant.split("T");
            this.formattedDate = split[0];
            this.formattedTime = split[1];
         }
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

   closeDatepicker(event: MouseEvent) {
      if(this.dateValueEditor) {
         this.dateValueEditor.closeDatepicker(event);
      }
   }
}
