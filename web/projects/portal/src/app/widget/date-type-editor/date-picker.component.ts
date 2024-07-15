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
import { Component, EventEmitter, HostListener, Input, Output } from "@angular/core";
import { NgbDatepickerConfig } from "@ng-bootstrap/ng-bootstrap";
import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";
import { TimeInstant } from "../../common/data/time-instant";
import { DateTimeChangeType } from "./date-time-change-type";

@Component({
   selector: "date-picker",
   templateUrl: "./date-picker.component.html",
   styleUrls: ["./date-picker.component.scss"]
})
export class DatePickerComponent {
   @Input() promptTime: boolean = false;
   @Input() dateTime: TimeInstant;
   @Output() onCommit = new EventEmitter<string>();
   @Output() valueChanged = new EventEmitter<{value: string, changeType: DateTimeChangeType}>();
   format: string = DateTypeFormatter.ISO_8601_DATE_FORMAT;
   years: any[] = this.getOptionYears();
   months: any[] = [
      {value: 0, label: "_#(js:January)"},
      {value: 1, label: "_#(js:February)"},
      {value: 2, label: "_#(js:March)"},
      {value: 3, label: "_#(js:April)"},
      {value: 4, label: "_#(js:May)"},
      {value: 5, label: "_#(js:June)"},
      {value: 6, label: "_#(js:July)"},
      {value: 7, label: "_#(js:August)"},
      {value: 8, label: "_#(js:September)"},
      {value: 9, label: "_#(js:October)"},
      {value: 10, label: "_#(js:November)"},
      {value: 11, label: "_#(js:December)"}];
   private _dateHeaders: string[] = [
      "_#(js:Sun)", "_#(js:Mon)", "_#(js:Tue)", "_#(js:Wed)",
      "_#(js:Thu)", "_#(js:Fri)", "_#(js:Sat)"
   ];

   constructor(private ngbDatepickerConfig: NgbDatepickerConfig) {
   }

   get dateHeaders(): string[] {
      const firstDoW = this.ngbDatepickerConfig.firstDayOfWeek;
      return [0, 1, 2, 3, 4, 5, 6].map(n => this._dateHeaders[(n + firstDoW) % 7]);
   }

   formatTimeString(dateTime: TimeInstant): string {
      return DateTypeFormatter.formatInstant(dateTime, this.format);
   }

   getOptionYears(): any {
      let years: any[] = [];

      for(let i = 1900; i < 2101; i++) {
         years.push({value: i, label: i});
      }

      return years;
   }

   loadCalender(month, year): any {
      month = +month;
      year = +year;
      const cells: number[][] = [];
      const firstDate: Date = new Date(year, month, 1);
      const firstDoW: number = this.ngbDatepickerConfig.firstDayOfWeek;
      let firstDay: number = firstDate.getDay() - firstDoW % 7;
      const lastDate: Date = new Date(new Date(year, month + 1, 1).getTime() - 1);
      const lastDay: number = lastDate.getDate();
      let index: number = 0;

      if(firstDay < 0) {
         firstDay += 7;
      }

      for(let row = 0; row < 6; row++) {
         cells[row] = [];

         for(let col = 0; col < 7; col++) {
            if(row == 0 && col < firstDay) {
               cells[row][col] = -1;
            }
            else if(index >= lastDay) {
               cells[row][col] = -1;
            }
            else {
               index++;
               cells[row][col] = index;
            }
         }
      }

      return cells;
   }

   @HostListener("mousedown", ["$event"])
   onMousedown(event: MouseEvent): void {
      if(this.promptTime) {
         event.stopPropagation();
      }
   }

   selectDay(day: number): void {
      this.dateTime.date = day;
      this.dateValueChanged(DateTimeChangeType.DATE);
   }

   selectYear(year: number) {
      this.dateTime.years = year;
      this.dateValueChanged(DateTimeChangeType.YEAR);
   }

   selectMonth(month: number) {
      this.dateTime.months = month;
      this.dateValueChanged(DateTimeChangeType.MONTH);
   }

   private dateValueChanged(changeType: DateTimeChangeType) {
      this.onCommit.emit(this.formatTimeString(this.dateTime));
      this.valueChanged.emit({value: this.formatTimeString(this.dateTime), changeType: changeType});
   }
}
