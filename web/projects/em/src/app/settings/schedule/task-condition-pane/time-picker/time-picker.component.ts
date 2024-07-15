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
import { Component, forwardRef, Input, OnInit } from "@angular/core";
import {
   ControlValueAccessor,
   NG_VALUE_ACCESSOR,
} from "@angular/forms";

export interface TimeData {
   hour: number;
   minute: number;
   second: number;
}

@Component({
   selector: "em-time-picker",
   templateUrl: "./time-picker.component.html",
   styleUrls: ["./time-picker.component.scss"],
   providers: [
      {
         provide: NG_VALUE_ACCESSOR,
         useExisting: forwardRef(() => TimePickerComponent),
         multi: true
      }
   ]
})
export class TimePickerComponent implements OnInit, ControlValueAccessor {
   _model: TimeData;
   @Input() meridian: boolean = false;
   @Input() seconds: boolean = false;
   am: boolean;
   private onChange = (fn: any) => {};
   private onTouched: any;
   hour: number;
   minute: number;
   second: number = 0;
   formattedHour: string = "";
   formattedMinute: string = "";
   formattedSecond: string = "";
   disabled: boolean;

   constructor() {
   }

   ngOnInit(): void {
   }

   set model(d: TimeData) {
      if(!this.compareTimeData(d)) {
         if(!!d) {
            this._model = d;
         }

         this.onChange(this.timeDataToString(d));
      }
   }

   get model() {
      return this._model;
   }

   updateHour(hour: string) {
      this.formattedHour = hour;
      this.model = this.getStartTime();
   }

   updateMinute(minute: string) {
      this.formattedMinute = minute;
      this.model = this.getStartTime();
   }

   updateSecond(second: string) {
      this.formattedSecond = second;
      this.model = this.getStartTime();
   }

   changeMeridian() {
      this.am = !this.am;
      this._model = this.getStartTime();
      this.onChange(this.timeDataToString(this.model));
   }

   getMeridianLabel() {
      return this.am ? "_#(js:AM)" : "_#(js:PM)";
   }

   private getStartTime(): TimeData {
      const value = {
         hour: 0,
         minute: 0,
         second: 0
      };

      if(this.meridian && this.am && this.toInteger(this.formattedHour) > 12) {
         this.am = !this.am;
      }

      let carry = 0;
      let s: number = this.toInteger(this.formattedSecond);

      if(s && s >= 60) {
         carry = Math.floor(s / 60);
         value.second = s % 60;
      }
      else {
         value.second = s;
      }

      let m = this.toInteger(this.formattedMinute);

      if(carry > 0) {
         m += carry;
      }

      if(m && m >= 60) {
         carry = Math.floor(m / 60);
         value.minute = m % 60;
      }
      else {
         value.minute = m;
      }

      let h = this.formatHour(this.toInteger(this.formattedHour));

      if(carry > 0) {
         h += carry;
      }

      value.hour = this.meridian && !this.am ? h + 12 : h;
      this.formatTime(value);

      return value;
   }

   formatHour(hour: number): number {
      return this.meridian ? hour % 12 : hour;
   }

   registerOnChange(fn: any): void {
      this.onChange = fn;
   }

   registerOnTouched(fn: any): void {
      this.onTouched = fn;
   }

   setDisabledState(isDisabled: boolean): void {
      this.disabled = isDisabled;
   }

   writeValue(obj: any): void {
      if(obj) {
         this._model = <TimeData> this.formatTimeData(obj);
         this.am = this._model.hour < 12;
         this.formatTime(this._model);
      }
      else {
         // no need to clear all values. otherwise if a user clears hour, minute will
         // also be clearted.
         /*
         this.am = true;
         this.formattedHour = "";
         this.formattedMinute = "";
         this.formattedSecond = "";
         */
      }
   }

   isEmpty(d: TimeData) {
      return !d.hour || !d.minute;
   }

   compareTimeData(data: TimeData) {
      return this._model?.hour == data?.hour && this._model?.minute == data?.minute
         && this._model?.second == data?.second;
   }

   private formatTime(data: TimeData): void {
      // Format Hour
      if(this.isNumber(data.hour)) {
         if(this.meridian) {
            this.formattedHour = "";
            let hour = data.hour % 12 == 0 ? 12 : data.hour % 12;
            setTimeout(()=> this.formattedHour = this.padNumber(hour));
            this.am = data.hour % 24 < 12;
         }
         else {
            data.hour = data.hour % 24;
            this.formattedHour = this.padNumber(data.hour % 24);
         }
      }
      else {
         this.formattedHour = this.padNumber(NaN);
      }

      // Format Minute
      this.formattedMinute = this.padNumber(data.minute);

      // Format Second
      this.formattedSecond = this.padNumber(data.second);
   }

   padNumber(value: number): string {
      if(this.isNumber(value)) {
         return `0${value}`.slice(-2);
      }
      else {
         return "";
      }
   }

   toInteger(value: any): number {
      return parseInt(`${value}`, 10);
   }

   isNumber(value: any): value is number {
      return !isNaN(this.toInteger(value));
   }

   timeDataToString(data: TimeData): string {
      return data ? data.hour + ":" + data.minute + ":" + (this.seconds ? data.second : "00") : null;
   }

   formatTimeData(timeValue: string): TimeData {
      let strs = timeValue ? timeValue.split(":") : null;
      return strs ? {hour: parseInt(strs[0], 10),
         minute: parseInt(strs[1], 10), second: parseInt(strs[2], 10)} : null;
   }
}
