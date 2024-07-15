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
import {
   Component, Input, SimpleChanges, Output, EventEmitter, OnChanges, AfterViewInit,
   ChangeDetectorRef, ViewChild, ElementRef
} from "@angular/core";
import { GuiTool } from "../../../common/util/gui-tool";
import { SelectedDateModel, CurrentDateModel } from "../../model/calendar/current-date-model";
import { SelectionRegions, VSCalendar } from "./vs-calendar.component";
import { VSCalendarModel } from "../../model/calendar/vs-calendar-model";
import { Tool } from "../../../../../../shared/util/tool";
import { TableDataPath } from "../../../common/data/table-data-path";
import { DataPathConstants } from "../../../common/util/data-path-constants";
import { ContextProvider } from "../../context-provider.service";

@Component({
   selector: "year-calendar",
   templateUrl: "year-calendar.component.html",
   styleUrls: ["vs-calendar.component.scss"],
})
export class YearCalendar implements OnChanges, AfterViewInit {
   @Input() model: VSCalendarModel;
   @Input() secondCalendar: boolean = false;
   @Input() year: number;
   @Input() formatPainterMode: boolean = false;
   @Input() selectedRow: number;
   @Input() selectedCol: number;
   @Input() selected: boolean = false;
   @Output() selectedDatesChange = new EventEmitter<boolean>();
   @Output() syncRanges = new EventEmitter<boolean>();
   @Output() syncPeriods = new EventEmitter<boolean>();
   @Output() syncDateChange = new EventEmitter<boolean>();
   @Output() applyCalendar = new EventEmitter<void>();
   @Output() titleChanged: EventEmitter<boolean> = new EventEmitter<boolean>();
   @ViewChild("lastYearRef") lastYearRef: ElementRef;
   @ViewChild("titleRef") titleRef: ElementRef;
   @ViewChild("nextYearRef") nextYearRef: ElementRef;
   @ViewChild("listRef") listRef: ElementRef;
   currentDate: CurrentDateModel;
   ocurrentDate: CurrentDateModel;
   dates: SelectedDateModel[] = [];
   selectedMonth: boolean[] = [];
   inited: boolean;
   private changed: boolean = false;
   SelectionRegions = SelectionRegions;
   months: string[] = [
      "_#(js:January)",
      "_#(js:February)",
      "_#(js:March)",
      "_#(js:April)",
      "_#(js:May)",
      "_#(js:June)",
      "_#(js:July)",
      "_#(js:August)",
      "_#(js:September)",
      "_#(js:October)",
      "_#(js:November)",
      "_#(js:December)"
   ];

   iconColor: string;
   selectedBgColor: string;

   constructor(private changeDetectorRef: ChangeDetectorRef,
               private contextProvider: ContextProvider) {}

   ngOnChanges(changes: SimpleChanges): void {
      const numChanges: number = Object.keys(changes).length;

      // Ignore changes exclusively to the position of the keyboard nav selection.
      if(numChanges == 1 && (!!changes["selectedRow"] || !!changes["selectedCol"]) ||
         numChanges == 2 && !!changes["selectedRow"] && !!changes["selectedCol"])
      {
         return;
      }

      if(changes["model"]) {
         this.currentDate = this.secondCalendar ? this.model.currentDate2 : this.model.currentDate1;
         this.ocurrentDate = Tool.clone(this.currentDate);
         this.dates = this.secondCalendar ? this.model.dates2 : this.model.dates1;
         this.currentDate.month = 0; // month is meaningless in year

         // ensure that calendar is synced with selected dates on init
         if(this.dates && this.dates.length > 0) {
            let selectedDate = this.dates[0];

            if(this.currentDate.year != selectedDate.year) {
               this.currentDate.year = selectedDate.year;

               if(this.secondCalendar) {
                  this.model.calendarTitleView2 = this.currentDate.year + "";
               }
               else {
                  this.model.calendarTitleView1 = this.currentDate.year + "";
               }
            }
         }

         this.checkCurrentDate();
         this.changed = true;
         this.updateSelected();
         this.iconColor = VSCalendar.getIconColor(this.model);

         // if background is set then use a shade of the background color for the selection
         // otherwise use our default selection color
         this.selectedBgColor = GuiTool.getSelectedColor(this.model.yearFormat.background ?
            this.model.yearFormat.background : this.model.objectFormat.background, "#cdf7f6");
      }
   }

   get vsWizard(): boolean {
      return this.contextProvider.vsWizard;
   }

   ngAfterViewInit(): void {
      this.inited = true;
      // Since vs-calendar change variable this.selectedDatesChange manually,
      // we have to detect this change in this situation.
      this.changeDetectorRef.detectChanges();
   }

   resetOldDate(): void {
      this.ocurrentDate = Tool.clone(this.currentDate);
   }

   getMonth(row: number, col: number) {
      let num = row * 4 + col;

      if(this.model.monthNames != null && this.model.monthNames.length == 12)
      {
         return this.model.monthNames[num];
      }

      return num + 1;
   }

   getYear(): string {
      let change: boolean = this.currentDate.year != this.ocurrentDate.year;

      if(!this.secondCalendar && this.model.calendarTitleView1 != null && !change) {
         return this.model.calendarTitleView1;
      }
      else if(this.secondCalendar && this.model.calendarTitleView2 != null && !change) {
         return this.model.calendarTitleView2;
      }

      return `${this.currentDate.year}`;
   }

   // next (1) or previous (-1) year
   nextYear(inc: number, calledOnSync?: boolean) {
      if(this.formatPainterMode) {
         return;
      }

      this.currentDate.year += inc;

      for(let selection of this.dates) {
         selection.year = this.currentDate.year;
      }

      // if single calendar and submit on change, call apply calendar
      if(!this.model.doubleCalendar && this.model.submitOnChange) {
         this.applyCalendar.emit();
      }
      else {
         this.selectedDatesChange.emit(true);
         this.updateSelected();
         this.titleChanged.emit(this.secondCalendar);

         // if is double calendar range mode, make sure current calendar dates are in sync
         if(!calledOnSync && this.model.doubleCalendar && !this.model.period) {
            this.syncDateChange.emit(this.secondCalendar);
         }
      }
   }

   /**
    * todo: Calendar height scales off a constant equal to defh in AssetUtil.java
    * Can scale off objectFormat.height, but the calendar will scale quickly when
    * the size of the dropdown button is increased
    */
   //gets calendar height, which needs to be adjusted in dropdown mode
   getCalendarHeight(): number {
      if(this.model.dropdownCalendar){
         return 18 * 8; //the constant 18 is defh from AssetUtil.java
      }
      else {
         return this.model.objectFormat.height - this.model.titleFormat.height;
      }
   }

   clickCell(row: number, col: number, event: MouseEvent) {
      if(this.formatPainterMode || this.vsWizard) {
         return;
      }

      if(event.ctrlKey && this.selected) {
         event.stopPropagation();
      }

      // if year is selected, de-select it
      if(this.dates.length == 1 && this.dates[0].dateType == "y") {
         this.dates = [];
         this.removeDataPath(DataPathConstants.YEAR_CALENDAR);
         this.removeDataPath(DataPathConstants.CALENDAR_TITLE);
      }

      let selectedDate: SelectedDateModel = <SelectedDateModel> {
         year: this.currentDate.year,
         month: row * 4 + col,
         dateType: "m",
         value: 0
      };

      // if in range mode, always only select clicked cell
      if(this.model.doubleCalendar && !this.model.period
         && (!event.ctrlKey && !event.metaKey && !event.shiftKey && !this.model.multiSelect))
      {
         this.dates = [selectedDate];
         this.removeDataPath(DataPathConstants.CALENDAR_TITLE);
         this.addDataPath(DataPathConstants.YEAR_CALENDAR);
      }
      else {
         let index = this.indexOfSelectedDate(selectedDate);

         if((!event.ctrlKey && !event.metaKey && !event.shiftKey && !this.model.multiSelect) ||
            this.model.singleSelection)
         {
            this.dates = [];
            this.removeDataPath(DataPathConstants.CALENDAR_TITLE);
            this.removeDataPath(DataPathConstants.YEAR_CALENDAR);
         }
         else if((event.ctrlKey || event.metaKey || this.model.multiSelect) && index != -1) {
            this.dates.splice(index, 1);

            let indx: number = this.dates.findIndex((date) => date.dateType == "y");

            if(indx == -1) {
               this.removeDataPath(DataPathConstants.YEAR_CALENDAR);
            }
         }

         if(event.shiftKey) {
            // if no dates selected, ignore shift+click
            if(this.dates.length > 0) {
               let firstIndex: number = this.dates[0].month;
               let lastIndex: number = this.dates[this.dates.length - 1].month;

               if(firstIndex <= selectedDate.month) {
                  lastIndex = selectedDate.month;
               }
               else {
                  firstIndex = selectedDate.month;
               }

               this.dates = [];

               for(let i = firstIndex; i <= lastIndex; i++) {
                  selectedDate = <SelectedDateModel> {
                     year: this.currentDate.year,
                     month: i,
                     dateType: "m",
                     value: 0
                  };
                  this.dates.push(selectedDate);
               }
            }
         }
         else if(index == -1) {
            this.dates.push(selectedDate);
            this.addDataPath(DataPathConstants.YEAR_CALENDAR);
         }

         if(this.inited && this.model.doubleCalendar && this.model.period) {
            this.syncPeriods.emit(this.secondCalendar);
         }
      }

      // if single calendar and submit on change, call apply calendar
      if(!this.model.doubleCalendar && this.model.submitOnChange) {
         this.applyCalendar.emit();
      }
      else {
         this.selectedDatesChange.emit(true);
         this.updateSelected();
      }
   }

   /**
    * Select the year title
    */
   clickYearTitle(event: MouseEvent): void {
      if(this.formatPainterMode || this.vsWizard) {
         return;
      }

      let selectedDate: SelectedDateModel = <SelectedDateModel> {
         year: this.currentDate.year,
         dateType: "y",
         month: 0,
         value: 0
      };

      if((event.ctrlKey || event.metaKey || this.model.multiSelect) &&
         this.indexOfSelectedDate(selectedDate) != -1)
      {
         this.dates = [];
         this.removeDataPath(DataPathConstants.YEAR_CALENDAR);
         this.removeDataPath(DataPathConstants.CALENDAR_TITLE);
      }
      else {
         // if click on year title, only select year no matter what settings
         this.dates = [selectedDate];
         this.removeDataPath(DataPathConstants.YEAR_CALENDAR);
         this.addDataPath(DataPathConstants.CALENDAR_TITLE);
      }

      if(this.inited && this.model.doubleCalendar && this.model.period) {
         this.syncPeriods.emit(this.secondCalendar);
      }

      // if single calendar and submit on change, call apply calendar
      if(!this.model.doubleCalendar && this.model.submitOnChange) {
         this.applyCalendar.emit();
      }
      else {
         this.selectedDatesChange.emit(true);
         this.updateSelected();
      }
   }

   clearSelection() {
      this.dates = [];
      this.selectedMonth = [];
   }

   /**
    * Update the calendars selected cells
    */
   updateSelected(force?: boolean): void {
      // clear months
      this.selectedMonth = [];

      // sort dates so selection string is correct
      this.dates.sort(VSCalendar.calendarComparator);

      // if range mode, let paintRange handle selected cells
      if(!force && this.model.doubleCalendar && !this.model.period) {
         // parent VSCalendar will handle initial painting when all calendars are loaded
         if(this.inited || this.changed) {
            this.syncRanges.emit(this.secondCalendar);
         }
      }
      else {
         for(let selection of this.dates) {
            if(selection.dateType == "m") {
               this.selectedMonth[selection.month] = true;
            }
         }
      }
   }

   private indexOfSelectedDate(newSelectedDate: SelectedDateModel): number {
      for(let i = 0; i < this.dates.length; i++) {
         if(Tool.isEquals(this.dates[i], newSelectedDate)) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Called when double calendar needs to sync current dates
    * @param changeYear  true to update Year
    */
   public syncDate(changeYear: boolean): void {
      this.nextYear(this.secondCalendar ? 1 : -1, true);
   }

   /**
    * Sync comparison values on calendars in period mode
    * @param values  the other calendars selected values
    * @param onApply true if called from apply Calendar method
    */
   public syncPeriod(values: SelectedDateModel[], onApply?: boolean): void {
      if(values.length > 0) {
         if(values[0].dateType == "y") {
            // if other calendar has selected year, select current year
            let selectedDate: SelectedDateModel = <SelectedDateModel> {
               year: this.currentDate.year,
               dateType: "y"
            };
            this.dates = [selectedDate];

            // no need to update self before apply event
            if(!onApply) {
               this.updateSelected();
            }
         }
         else {
            // if this calendar current has year selected, de-select dates
            if(this.dates.length == 1 && this.dates[0].dateType == "y") {
               this.dates = [];
            }

            // if other calendar has more selected dates, match selection length
            if(this.dates.length < values.length) {
               for(let i = values.length - 1;
                   i >= 0 && this.dates.length < values.length; i--)
               {
                  let searchDate: SelectedDateModel = <SelectedDateModel> {
                     year: this.currentDate.year,
                     month: values[i].month,
                     dateType: "m",
                     value: 0
                  };
                  let searchIndex: number = this.indexOfSelectedDate(searchDate);

                  if(searchIndex == -1) {
                     this.dates.push(searchDate);
                  }
               }

               // no need to update self before apply event
               if(!onApply) {
                  this.updateSelected();
               }
            }
         }
      }
   }

   /**
    * Highlight selected cells in range
    * Reimplementation from YearCalendar.as paintRange()
    * @param values  the selected values
    */
   public paintRange(values: SelectedDateModel[], otherCalendarEmpty: boolean = false): void {
      if(!values || values.length <= 0) {
         return;
      }

      values.sort(VSCalendar.calendarComparator);
      let start: number = -1;
      let end: number = -1;
      let first: SelectedDateModel = values[0];
      let last: SelectedDateModel = values[values.length - 1];

      if(values.length > 1) {
         start = Math.min(first.month, last.month);
         end = Math.max(first.month, last.month) + 1;
      }
      else if(values.length == 1) {
         if(!this.secondCalendar) {
            start = first.month;
            end = 12;
         }
         else {
            start = 0;
            end = first.month + 1;
         }
      }

      if(start != -1 && end != -1) {
         this.selectedMonth = [];

         for(let i = start; i < end && i < 12; i++) {
            this.selectedMonth[i] = true;
         }
      }

      if(otherCalendarEmpty) {
         this.dates = [first, last];
      }
      else {
         this.dates = [this.secondCalendar ? last : first];
      }
   }

   public getSelectionString(minimum?: boolean, previousSelection?: string): string {
      let selectionString: string = "";

      if(!this.dates || this.dates.length == 0) {
         return selectionString;
      }

      // use first selection date as basis for conditions
      let firstSelection: SelectedDateModel = this.dates[0];

      if(firstSelection.dateType == "y") {
         selectionString = String(firstSelection.year);
      }
      else {
         if(minimum) {
            selectionString = firstSelection.year + "-" + (firstSelection.month + 1);
         }
         else if(minimum != null) {
            let lastSelection: SelectedDateModel = this.dates[this.dates.length - 1];
            selectionString = lastSelection.year + "-" + (lastSelection.month + 1);
         }
         else {
            selectionString = firstSelection.year + "-" + VSCalendar.getRangeString(this.dates, true);
         }
      }

      return selectionString;
   }

   public getSelectedDates(): SelectedDateModel[] {
      if(this.dates.length > 0 && this.dates[0].dateType == "y") {
          return [];
      }

      return this.dates;
   }

   isYearSelected(): boolean {
      return this.dates.length > 0 && this.dates[0].dateType == "y";
   }

   checkCurrentDate(): void {
      if(this.model.range.maxYear != -1 && this.currentDate.year > this.model.range.maxYear) {
         this.currentDate.year = this.model.range.maxYear;
      }
      else if(this.model.range.minYear != -1 && this.currentDate.year < this.model.range.minYear) {
         this.currentDate.year = this.model.range.minYear;
      }
   }

   isInRange(month: number): boolean {
      // -1 means no range limit is set
      return (this.model.range.minYear == -1 || (this.currentDate.year > this.model.range.minYear ||
         (this.currentDate.year == this.model.range.minYear && month >= this.model.range.minMonth))) &&
         (this.model.range.maxYear == -1 || (this.currentDate.year < this.model.range.maxYear ||
         (this.currentDate.year == this.model.range.maxYear && month <= this.model.range.maxMonth)));
   }

   isBtnEnabled(previous: boolean): boolean {
      if(previous && this.model.range.minYear != -1) {
         return this.currentDate.year - 1 >= this.model.range.minYear;
      }
      else if(!previous && this.model.range.maxYear != -1) {
         return this.currentDate.year + 1 <= this.model.range.maxYear;
      }

      return true;
   }

   getDateArray(): string[] {
      let result: string[] = [];

      result = this.dates.map((date) => {
         let dateString: string = date.dateType + date.year;

         if(date.dateType == "m") {
            dateString += "-" + date.month;
         }

         return dateString;
      });

      return result;
   }

   getCurrentDateString(): string {
      return this.currentDate.month ?
         this.currentDate.year + "-" + this.currentDate.month : String(this.currentDate.year);
   }

   private addDataPath(path: TableDataPath): void {
      if(!this.model.selectedRegions) {
         this.model.selectedRegions = [];
      }

      if(this.model.selectedRegions.indexOf(path) == -1) {
         this.model.selectedRegions.push(path);
      }
   }

   private removeDataPath(path: TableDataPath): void {
      if(!this.model.selectedRegions) {
         return;
      }

      let index: number = this.model.selectedRegions.indexOf(path);

      if(index != -1) {
         this.model.selectedRegions.splice(index, 1);
      }
   }

   isCellFocused(row: number, col: number): boolean {
      return this.selectedRow == row && this.selectedCol == col;
   }
}
