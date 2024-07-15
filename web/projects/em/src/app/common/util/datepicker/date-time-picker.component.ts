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
import { XSchema } from "../../../../../../portal/src/app/common/data/xschema";
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";
import { TimeInstant } from "../../../../../../portal/src/app/common/data/time-instant";

@Component({
   selector: "em-date-time-picker",
   templateUrl: "date-time-picker.component.html",
   styleUrls: ["date-time-picker.component.scss"]
})
export class DateTimePickerComponent implements OnInit {
   @Input() value: string;
   @Input() type: string;
   @Output() valueChange = new EventEmitter<string>();

   public XSchema = XSchema;
   time: string; // time portion of the value
   date: Date; // date portion of the value
   _format: string;
   dateTime: TimeInstant;

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

   ngOnInit() {
      let autoSetCurrent = false;

      if(!this.value || !DateTypeFormatter.formatStr(this.value, this.format)) {
         this.value = DateTypeFormatter.currentTimeInstantInFormat(this.format);
         autoSetCurrent = true;
      }

      this.dateTime = DateTypeFormatter.toTimeInstant(this.value, this.format);
      this.date = DateTypeFormatter.timeInstantToDate(this.dateTime);
      this.initTime(this.dateTime);

      if(autoSetCurrent) {
         this.valueChange.emit(this.value);
      }
   }

   initTime(dateTime: TimeInstant): void {
      this.time = DateTypeFormatter.formatInstant(dateTime, DateTypeFormatter.ISO_8601_TIME_FORMAT);
   }

   dateChange(date?: Date) {
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

      this.valueChange.emit(this.value);
   }

   private getDateString(date: Date): string {
      let dateString = `${date.getFullYear()}-` +
         `${((date.getMonth() + 1) < 10 ? "0" : "") + (date.getMonth() + 1)}-` +
         `${(date.getDate() < 10 ? "0" : "") + date.getDate()}`;
      return dateString;
   }
}
