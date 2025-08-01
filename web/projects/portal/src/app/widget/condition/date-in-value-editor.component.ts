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
   dateRangeLabels: string[];

   ngOnInit(): void {
      this.fetchDateRanges();
   }

   fetchDateRanges(): void {
      const observable = this.dataFunction();

      if(observable != null) {
         observable.subscribe((data) => {
            this.dateRanges = data.values;
            this.dateRangeLabels = data.labels;

            if(!this._rangeVal) {
               this.value = data[0];
               this.valueChange.emit(this.value);
            }
         });
      }
   }
}
