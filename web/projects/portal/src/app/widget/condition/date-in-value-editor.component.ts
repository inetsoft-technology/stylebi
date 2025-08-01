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
import { Component, Input, Output, EventEmitter, OnInit } from "@angular/core";
import { Observable } from "rxjs";
import { BrowseDataModel } from "../../common/data/browse-data-model";

@Component({
   selector: "date-in-value-editor",
   templateUrl: "date-in-value-editor.component.html"
})
export class DateInValueEditor implements OnInit {
   @Input() dataFunction: () => Observable<BrowseDataModel>;
   @Input() set value(val: string) {
      this._rangeVal = val;
   }

   get value(): string {
      if(!!this._rangeVal) {
         return this._rangeVal;
      }

      return !!this.dateRanges && this.dateRanges.length > 0 ? this.dateRanges[0] : null;
   }

   @Output() valueChange: EventEmitter<string> = new EventEmitter<string>();
   private _rangeVal: string;
   dateRanges: string[];

   ngOnInit(): void {
      this.fetchDateRanges();
   }

   fetchDateRanges(): void {
      const observable = this.dataFunction();

      if(observable != null) {
         observable.subscribe((data) => {
            this.dateRanges = data.values;

            if(!this._rangeVal) {
               this.value = data[0];
               this.valueChange.emit(this.value);
            }
         });
      }
   }

   getDateRangeLabels(dateRange) {
      switch(dateRange) {
         case "Last year":
            return "_#(js:Last year)";
         case "This year":
            return "_#(js:This year)";
         case "This quarter":
            return "_#(js:This quarter)";
         case "Last quarter":
            return "_#(js:Last quarter)";
         case "This quarter last year":
            return "_#(js:This quarter last year)";
         case "Last quarter last year":
            return "_#(js:Last quarter last year)";
         case "1st quarter this year":
            return "_#(js:1st quarter this year)";
         case "2nd quarter this year":
            return "_#(js:2nd quarter this year)";
         case "3rd quarter this year":
            return "_#(js:3rd quarter this year)";
         case "4th quarter this year":
            return "_#(js:4th quarter this year)";
         case "1st quarter last year":
            return "_#(js:1st quarter last year)";
         case "2nd quarter last year":
            return "_#(js:2nd quarter last year)";
         case "3rd quarter last year":
            return "_#(js:3rd quarter last year)";
         case "4th quarter last year":
            return "_#(js:4th quarter last year)";
         case "1st half of this year":
            return "_#(js:1st half of this year)";
         case "2nd half of this year":
            return "_#(js:2nd half of this year)";
         case "1st half of last year":
            return "_#(js:1st half of last year)";
         case "2nd half of last year":
            return "_#(js:2nd half of last year)";
         case "This month":
            return "_#(js:This month)";
         case "Last month":
            return "_#(js:Last month)";
         case "Last 1 months":
            return "_#(js:Last 1 months)";
         case "Last 3 months":
            return "_#(js:Last 3 months)";
         case "Last 6 months":
            return "_#(js:Last 6 months)";
         case "Last 12 months":
            return "_#(js:Last 12 months)";
         case "Last 18 months":
            return "_#(js:Last 18 months)";
         case "Last 24 months":
            return "_#(js:Last 24 months)";
         case "Last 36 months":
            return "_#(js:Last 36 months)";
         case "Last 48 months":
            return "_#(js:Last 48 months)";
         case "Last 60 months":
            return "_#(js:Last 60 months)";
         case "Last 72 months":
            return "_#(js:Last 72 months)";
         case "Last 84 months":
            return "_#(js:Last 84 months)";
         case "Last 96 months":
            return "_#(js:Last 96 months)";
         case "Last 108 months":
            return "_#(js:Last 108 months)";
         case "Last 120 months":
            return "_#(js:Last 120 months)";
         case "This month last year":
            return "_#(js:This month last year)";
         case "Last month last year":
            return "_#(js:Last month last year)";
         case "This January":
            return "_#(js:This January)";
         case "This February":
            return "_#(js:This February)";
         case "This March":
            return "_#(js:This March)";
         case "This April":
            return "_#(js:This April)";
         case "This May":
            return "_#(js:This May)";
         case "This June":
            return "_#(js:This June)";
         case "This July":
            return "_#(js:This July)";
         case "This August":
            return "_#(js:This August)";
         case "This September":
            return "_#(js:This September)";
         case "This October":
            return "_#(js:This October)";
         case "This November":
            return "_#(js:This November)";
         case "This December":
            return "_#(js:This December)";
         case "Last January":
            return "_#(js:Last January)";
         case "Last February":
            return "_#(js:Last February)";
         case "Last March":
            return "_#(js:Last March)";
         case "Last April":
            return "_#(js:Last April)";
         case "Last May":
            return "_#(js:Last May)";
         case "Last June":
            return "_#(js:Last June)";
         case "Last July":
            return "_#(js:Last July)";
         case "Last August":
            return "_#(js:Last August)";
         case "Last September":
            return "_#(js:Last September)";
         case "Last October":
            return "_#(js:Last October)";
         case "Last November":
            return "_#(js:Last November)";
         case "Last December":
            return "_#(js:Last December)";
         case "This week":
            return "_#(js:This week)";
         case "Last week":
            return "_#(js:Last week)";
         case "Week before last week":
            return "_#(js:Week before last week)";
         case "Last 4 weeks":
            return "_#(js:Last 4 weeks)";
         case "Last 5-8 weeks":
            return "_#(js:Last 5-8 weeks)";
         case "Last 7 days":
            return "_#(js:Last 7 days)";
         case "Last 8-14 days":
            return "_#(js:Last 8-14 days)";
         case "Last 30 days":
            return "_#(js:Last 30 days)";
         case "Last 31-60 days":
            return "_#(js:Last 31-60 days)";
         case "Today":
            return "_#(js:Today)";
         case "Tomorrow":
            return "_#(js:Tomorrow)";
         case "Yesterday":
            return "_#(js:Yesterday)";
         case "Year to date":
            return "_#(js:Year to date)";
         case "Year to date last year":
            return "_#(js:Year to date last year)";
         case "Quarter to date":
            return "_#(js:Quarter to date)";
         case "Quarter to date last year":
            return "_#(js:Quarter to date last year)";
         case "Quarter to date last quarter":
            return "_#(js:Quarter to date last quarter)";
         case "Month to date":
            return "_#(js:Month to date)";
         case "Month to date last year":
            return "_#(js:Month to date last year)";
         case "Month to date last month":
            return "_#(js:Month to date last month)";
         default:
            return dateRange;
      }
   }
}
