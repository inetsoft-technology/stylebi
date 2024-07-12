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
import {
   Component,
   EventEmitter,
   Input,
   OnInit,
   Output,
} from "@angular/core";
import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";
import { TimeInstant } from "../../common/data/time-instant";
import { DateTimeChangeType } from "./date-time-change-type";

@Component({
   selector: "date-time-picker",
   templateUrl: "date-time-picker.component.html",
   styleUrls: ["date-time-picker.component.scss"],
})
export class DateTimePickerComponent implements OnInit {
   @Input() promptTime: boolean;
   @Input() promptDate: boolean = true;
   @Input() date: string = "";
   @Input() emitAutoSet: boolean = false;
   @Input() format: string = DateTypeFormatter.ISO_8601_DATE_FORMAT;
   @Output() onCommit = new EventEmitter<string>();
   @Output() valueChanged = new EventEmitter<{value: string, changeType: DateTimeChangeType}>();
   timeFormat: string = DateTypeFormatter.ISO_8601_TIME_FORMAT;
   selectTime: string;
   dateTime: TimeInstant;

   ngOnInit(): void {
      let autoSetCurrent = false;

      if(!this.date || !DateTypeFormatter.formatStr(this.date, this.format)) {
         this.date = DateTypeFormatter.currentTimeInstantInFormat(this.format);
         autoSetCurrent = true;
      }

      this.dateTime = DateTypeFormatter.toTimeInstant(this.date, this.format);
      this.initTime(this.dateTime);

      if(this.emitAutoSet && autoSetCurrent) {
         this.dateTimeValueChange(DateTimeChangeType.AUTO);
      }
   }

   initTime(dateTime: TimeInstant): void {
      this.selectTime = `${dateTime.hours}:${dateTime.minutes}:${dateTime.seconds}`;
   }

   dateChange(changeType: DateTimeChangeType) {
      this.dateTimeValueChange(changeType);
   }

   timeChange(time: string) {
      const timeObj = DateTypeFormatter.toTimeInstant(time, this.timeFormat);
      let changeType;

      if(this.dateTime.hours != timeObj.hours) {
         changeType = DateTimeChangeType.HOUR;
      }
      else if(this.dateTime.minutes != timeObj.minutes) {
         changeType = DateTimeChangeType.MINUTE;
      }
      else {
         changeType = DateTimeChangeType.SECOND;
      }

      this.dateTime.hours = timeObj.hours;
      this.dateTime.minutes = timeObj.minutes;
      this.dateTime.seconds = timeObj.seconds;
      this.dateTimeValueChange(changeType);
   }

   formatTimeString(dateTime: TimeInstant): string {
      return DateTypeFormatter.formatInstant(dateTime, this.format);
   }

   dateTimeValueChange(changeType: DateTimeChangeType) {
      this.onCommit.emit(this.formatTimeString(this.dateTime));
      this.valueChanged.emit({value: this.formatTimeString(this.dateTime), changeType: changeType});
   }
}
