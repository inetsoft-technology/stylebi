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
import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";
import { DateTimeChangeType } from "../../widget/date-type-editor/date-time-change-type";

@Component({
   selector: "date-time-value-dialog",
   templateUrl: "date-time-value-dialog.component.html",
   styleUrls: ["./date-time-value-dialog.component.scss"]
})
export class DateTimeValueDialog implements OnInit {
   @Input() promptTime: boolean;
   @Input() promptDate: boolean = true;
   @Input() format: string = DateTypeFormatter.ISO_8601_DATE_FORMAT;
   @Input() date: string = "";
   @Output() onCommit = new EventEmitter<string>();
   @Output() onCancel = new EventEmitter<string>();

   ngOnInit() {
   }

   get title() {
      return this.promptTime ? "_#(js:Select a Date/Time)" : "_#(js:Select a Date)";
   }

   dateTimeChange(dateTime: string, changeType: DateTimeChangeType) {
      this.date = dateTime;

      if(!this.promptTime && changeType != DateTimeChangeType.YEAR && changeType != DateTimeChangeType.MONTH &&
         changeType != DateTimeChangeType.AUTO)
      {
         this.ok();
      }
   }

   ok() {
      this.onCommit.emit(this.date);
   }

   cancel() {
      this.onCancel.emit("cancel");
   }

   clear() {
      this.onCancel.emit("clear");
   }
}
