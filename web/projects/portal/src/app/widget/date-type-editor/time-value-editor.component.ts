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
import { NgbTimeStruct } from "@ng-bootstrap/ng-bootstrap";
import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";

@Component({
   selector: "time-value-editor",
   templateUrl: "time-value-editor.component.html",
   styleUrls: ["time-value-editor.component.scss"],
})
export class TimeValueEditorComponent {
   @Input() format: string = DateTypeFormatter.ISO_8601_TIME_FORMAT;
   @Input() disabled: boolean = false;

   @Output() timeChange: EventEmitter<string> = new EventEmitter<string>();

   _model: NgbTimeStruct;

   @Input() set model(timeString: string) {
         if(timeString != null) {
            const timeObject = DateTypeFormatter.toTimeInstant(timeString, this.format,
               DateTypeFormatter.ISO_8601_TIME_FORMAT);

            const hour = timeObject.hours;
            const minute = timeObject.minutes;
            const second = timeObject.seconds;

            this._model = {hour, minute, second};
         }
         else {
            this._model = null;
         }
   }

   changeTime(time: NgbTimeStruct) {
      const minute = Number.isNaN(time.minute) ? 0 : time.minute;
      const second = Number.isNaN(time.second) ? 0 : time.second;
      const timeString = `${time.hour}:${minute}:${second}`;
      const formattedTime = DateTypeFormatter
         .transformValue(timeString, DateTypeFormatter.ISO_8601_TIME_FORMAT, this.format);
      this.timeChange.emit(formattedTime);
   }
}
