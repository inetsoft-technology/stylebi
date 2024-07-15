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
import { Component, EventEmitter, OnInit } from "@angular/core";
import { NgbDateStruct } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../../shared/util/tool";
import { FirstDayOfWeekService } from "../../../common/services/first-day-of-week.service";

@Component({
   selector: "vso-vs-table-cell-calendar",
   templateUrl: "vs-table-cell-calendar.component.html",
   styleUrls: ["vs-table-cell-calendar.component.scss"]
})
export class VSTableCellCalendar implements OnInit {
   date: NgbDateStruct;
   onDateChange = new EventEmitter<NgbDateStruct>();
   minDate: NgbDateStruct = {year: 1900, month: 1, day: 1};
   maxDate: NgbDateStruct = {year: 2050, month: 12, day: 31};
   firstDayOfWeek: number = 1;

   constructor(private firstDayOfWeekService: FirstDayOfWeekService) {
   }

   ngOnInit() {
      this.firstDayOfWeekService.getFirstDay().subscribe((model) => {
         this.firstDayOfWeek = model.isoFirstDay;
      });
   }

   dateChanged(date: NgbDateStruct) {
      this.onDateChange.emit(date);
   }
}
