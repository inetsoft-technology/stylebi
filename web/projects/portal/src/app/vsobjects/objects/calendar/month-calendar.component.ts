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
   AfterViewInit,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   Input,
   OnChanges,
   Output,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { TableDataPath } from "../../../common/data/table-data-path";
import { DataPathConstants } from "../../../common/util/data-path-constants";
import { Tool } from "../../../../../../shared/util/tool";
import { CurrentDateModel, SelectedDateModel } from "../../model/calendar/current-date-model";
import { VSCalendarModel } from "../../model/calendar/vs-calendar-model";
import { SelectionRegions, VSCalendar } from "./vs-calendar.component";
import { FirstDayOfWeekService } from "../../../common/services/first-day-of-week.service";
import { ContextProvider } from "../../context-provider.service";
import { GuiTool } from "../../../common/util/gui-tool";

interface DayInfo {
   year: number;
   month: number;
   day: number;
   selected?: boolean;
   disabled?: boolean;
}

@Component({
   selector: "month-calendar",
   templateUrl: "month-calendar.component.html",
   styleUrls: ["vs-calendar.component.scss"]
})
export class MonthCalendar implements OnChanges, AfterViewInit {
   @Input() model: VSCalendarModel;
   @Input() secondCalendar: boolean = false;
   @Input() formatPainterMode: boolean = false;
   @Input() selectedRow: number = -7;
   @Input() selectedCol: number = -1;
   @Input() selected: boolean = false;
   @Output() selectedDatesChange: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() syncRanges: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() syncPeriods: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() syncDateChange: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() selectRange: EventEmitter<SelectedDateModel> = new EventEmitter<SelectedDateModel>();
   @Output() titleChanged: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() applyCalendar: EventEmitter<any> = new EventEmitter<any>();
   @ViewChild("lastYearRef") lastYearRef: ElementRef;
   @ViewChild("lastMonthRef") lastMonthRef: ElementRef;
   @ViewChild("titleRef") titleRef: ElementRef;
   @ViewChild("nextMonthRef") nextMonthRef: ElementRef;
   @ViewChild("nextYearRef") nextYearRef: ElementRef;
   @ViewChild("listRef") listRef: ElementRef;
   currentDate: CurrentDateModel;
   ocurrentDate: CurrentDateModel;
   monthTitle: string;
   days: DayInfo[] = [];
   daysInMonth: number[];
   readonly monthNames: string[] = [
      "_#(js:January)", "_#(js:February)", "_#(js:March)", "_#(js:April)",
      "_#(js:May)", "_#(js:June)", "_#(js:July)", "_#(js:August)",
      "_#(js:September)", "_#(js:October)", "_#(js:November)", "_#(js:December)"];
   inited: boolean;
   private changed: boolean = false;
   public readonly dayTitles = [
      "_#(js:Sun)",
      "_#(js:Mon)",
      "_#(js:Tue)",
      "_#(js:Wed)",
      "_#(js:Thu)",
      "_#(js:Fri)",
      "_#(js:Sat)"
   ];
   SelectionRegions = SelectionRegions;
   monthDays: string[] = [
      "_#(js:1st)",
      "_#(js:2nd)",
      "_#(js:3rd)",
      "_#(js:4th)",
      "_#(js:5th)",
      "_#(js:6th)",
      "_#(js:7th)",
      "_#(js:8th)",
      "_#(js:9th)",
      "_#(js:10th)",
      "_#(js:11th)",
      "_#(js:12th)",
      "_#(js:13th)",
      "_#(js:14th)",
      "_#(js:15th)",
      "_#(js:16th)",
      "_#(js:17th)",
      "_#(js:18th)",
      "_#(js:19th)",
      "_#(js:20th)",
      "_#(js:21th)",
      "_#(js:22nd)",
      "_#(js:23rd)",
      "_#(js:24th)",
      "_#(js:25th)",
      "_#(js:26th)",
      "_#(js:27th)",
      "_#(js:28th)",
      "_#(js:29th)",
      "_#(js:30th)",
      "_#(js:31st)"
   ];
   firstDayOfWeek: number = 1;
   mobile: boolean = GuiTool.isMobileDevice();
   iconColor: string;
   selectedBgColor: string;

   constructor(private changeDetectorRef: ChangeDetectorRef,
               private firstDayOfWeekService: FirstDayOfWeekService,
               private contextProvider: ContextProvider)
   {
   }

   ngOnChanges(changes: SimpleChanges): void {
      const numChanges: number = Object.keys(changes).length;

      // Ignore changes made exclusively to the position of the keyboard nav selection.
      if(numChanges == 1 && (!!changes["selectedRow"] || !!changes["selectedCol"]) ||
         numChanges == 2 && !!changes["selectedRow"] && !!changes["selectedCol"])
      {
         return;
      }

      // ignore selected status, date shouldn't be changed
      // if current date is undefined, it needs to be initialized
      if(changes["selected"] && this.currentDate) {
         return;
      }

      this.currentDate = this.secondCalendar ? this.model.currentDate2 : this.model.currentDate1;
      this.resetOldDate();
      this.dates = this.secondCalendar ? this.model.dates2 : this.model.dates1;

      //ensure that calendar is synced with selected dates on init
      if(this.dates && this.dates.length > 0) {
         let selectedDate = this.dates[0];

         if(this.currentDate.year != selectedDate.year) {
            this.currentDate.year = selectedDate.year;
         }

         if(this.currentDate.month != selectedDate.month) {
            this.currentDate.month = selectedDate.month;
         }
      }

      this.checkCurrentDate();
      this.changed = true;
      this.dateChanged();

      if(changes["model"]) {
         this.iconColor = VSCalendar.getIconColor(this.model);

         // if background is set then use a shade of the background color for the selection
         // otherwise use our default selection color
         this.selectedBgColor = GuiTool.getSelectedColor(this.model.monthFormat.background ?
            this.model.monthFormat.background : this.model.objectFormat.background, "#cdf7f6");
         this.titleChanged.emit(this.secondCalendar);
      }
   }

   ngAfterViewInit(): void {
      this.inited = true;
      // Since vs-calendar change variable this.selectedDatesChange manually,
      // we have to detect this change in this situation.
      this.changeDetectorRef.detectChanges();

      this.firstDayOfWeekService.getFirstDay().subscribe((model) => {
         this.firstDayOfWeek = model.javaFirstDay;
         this.dateChanged();
      });
   }

   get dates(): SelectedDateModel[] {
      return (this.secondCalendar ? this.model.dates2 : this.model.dates1) || [];
   }

   set dates(dates: SelectedDateModel[]) {
      if(this.secondCalendar) {
         this.model.dates2 = dates;
      }
      else {
         this.model.dates1 = dates;
      }
   }

   get vsWizard(): boolean {
      return this.contextProvider.vsWizard;
   }

   resetOldDate(): void {
      this.ocurrentDate = Tool.clone(this.currentDate);
   }

   // next (1) or previous (-1) year
   nextYear(inc: number, calledOnSync?: boolean): void {
      if(this.formatPainterMode) {
         return;
      }

      this.currentDate.year += inc;

      if(inc == -1 && this.currentDate.year == this.model.range.minYear) {
         if(this.currentDate.month < this.model.range.minMonth) {
            this.currentDate.month = this.model.range.minMonth;
         }
      }
      else if(inc == 1 && this.currentDate.year == this.model.range.maxYear) {
         if(this.currentDate.month > this.model.range.maxMonth) {
            this.currentDate.month = this.model.range.maxMonth;
         }
      }

      this.updateSelectedDates();

      // if single calendar and submit on change, call apply calendar
      if(!this.model.doubleCalendar && this.model.submitOnChange) {
         this.applyCalendar.emit();
      }
      else {
         this.dateChanged();
         this.selectedDateChanged();
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

   // next (1) or previous (-1) month
   nextMonth(inc: number, calledOnSync?: boolean): void {
      if(this.formatPainterMode) {
         return;
      }

      this.currentDate.month += inc;

      if(this.currentDate.month < 0) {
         this.currentDate.month = 11;
         this.currentDate.year--;
      }
      else if(this.currentDate.month > 11) {
         this.currentDate.month = 0;
         this.currentDate.year++;
      }

      this.updateSelectedDates();

      // if single calendar and submit on change, call apply calendar
      if(!this.model.doubleCalendar && this.model.submitOnChange) {
         this.applyCalendar.emit();
      }
      else {
         this.dateChanged();
         this.selectedDateChanged();
         this.titleChanged.emit(this.secondCalendar);

         // if is double calendar range mode, make sure current calendar dates are in sync
         if(!calledOnSync && this.model.doubleCalendar && !this.model.period) {
            this.syncDateChange.emit(this.secondCalendar);
         }
      }
   }

   /**
    * Called when user clicks on a day cell
    * @param row     the row clicked
    * @param col     the col clicked
    * @param event   the click event
    */
   clickCell(row: number, col: number, event: MouseEvent): void {
      let day = this.days[row * 7 + col];

      if(this.formatPainterMode || this.vsWizard || (!!day && day.disabled)) {
         return;
      }

      if(event.ctrlKey && this.selected) {
         event.stopPropagation();
      }

      // if month is selected, de-select it
      if(this.dates.length == 1 && this.dates[0].dateType == "m") {
         this.dates = [];
         this.removeDataPath(DataPathConstants.MONTH_CALENDAR);
         this.removeDataPath(DataPathConstants.CALENDAR_TITLE);
      }

      let selectedDate: SelectedDateModel;

      if(!this.model.daySelection) {
         selectedDate = <SelectedDateModel> {
            year: this.currentDate.year,
            month: this.currentDate.month,
            dateType: "w",
            value: row + 1
         };
      }
      else {
         // account for previous months days
         const startweek = this.getStartWeek();

         selectedDate = <SelectedDateModel> {
            year: this.currentDate.year,
            month: this.currentDate.month,
            dateType: "d",
            value: row * 7 + col - startweek + 1
         };
      }

      // if in range mode, always only select clicked cell
      if(this.model.doubleCalendar && !this.model.period
         && (!event.ctrlKey && !event.metaKey && !event.shiftKey && !this.model.multiSelect))
      {
         this.dates = [selectedDate];
         this.removeDataPath(DataPathConstants.CALENDAR_TITLE);
         this.addDataPath(DataPathConstants.MONTH_CALENDAR);
      }
      else {
         // check if this date was already selected
         let index = this.indexOfSelectedDate(selectedDate);

         if((!event.ctrlKey && !event.metaKey && !event.shiftKey && !this.model.multiSelect) ||
            this.model.singleSelection)
         {
            this.dates = [];
            this.removeDataPath(DataPathConstants.MONTH_CALENDAR);
            this.removeDataPath(DataPathConstants.CALENDAR_TITLE);
         }
         else if((event.ctrlKey || event.metaKey || this.model.multiSelect) && index != -1) {
            this.dates.splice(index, 1);

            let indx: number = this.dates.findIndex((date) => date.dateType == "m");

            if(indx == -1) {
               this.removeDataPath(DataPathConstants.MONTH_CALENDAR);
            }
         }

         if(event.shiftKey) {
            if(this.dates.length == 0) {
               // if no dates selected, select only clicked cell
               this.dates = [selectedDate];
            }
            else {
               let firstIndex: number = this.dates[0].value;
               let lastIndex: number = this.dates[this.dates.length - 1].value;

               if(firstIndex <= selectedDate.value) {
                  lastIndex = selectedDate.value;
               }
               else {
                  firstIndex = selectedDate.value;
               }

               this.dates = [];

               for(let i = firstIndex; i <= lastIndex; i++) {
                  selectedDate = <SelectedDateModel> {
                     year: this.currentDate.year,
                     month: this.currentDate.month,
                     dateType: this.model.daySelection ? "d" : "w",
                     value: i
                  };
                  this.dates.push(selectedDate);
               }
            }

            this.addDataPath(DataPathConstants.MONTH_CALENDAR);
         }
         else if(index == -1) {
            this.dates.push(selectedDate);
            this.addDataPath(DataPathConstants.MONTH_CALENDAR);
         }

         if(this.inited && this.model.doubleCalendar && this.model.period) {
            this.syncPeriods.emit(this.secondCalendar);
         }
      }

      this.resetDays();
      this.updateSelected();

      // if single calendar and submit on change, call apply calendar
      if(!this.model.doubleCalendar && this.model.submitOnChange) {
         this.applyCalendar.emit();
      }
      else {
         this.selectedDateChanged();
      }
   }

   clickDayTitle(weekday: number, event: MouseEvent): void {
      if(this.formatPainterMode || this.vsWizard) {
         return;
      }

      if(!this.model.daySelection) {
         event.preventDefault();
         return;
      }

      // if month is selected, de-select it
      if(this.dates.length == 1 && this.dates[0].dateType == "m") {
         this.dates = [];
         this.removeDataPath(DataPathConstants.CALENDAR_TITLE);
         this.removeDataPath(DataPathConstants.MONTH_CALENDAR);
      }

      // use to account for previous months days
      const startweek = this.getStartWeek();

      // if in range mode, only select first/last dates
      if(this.model.doubleCalendar && !this.model.period) {
         this.dates = [];
         let firstDay: SelectedDateModel;
         let lastDay: SelectedDateModel;

         for(let i = 0; i < 6; i++) {
            let day: number = weekday + 7 * i;

            if(this.days[day].disabled) {
               continue;
            }

            let selectedDate: SelectedDateModel = <SelectedDateModel> {
               year: this.currentDate.year,
               month: this.currentDate.month,
               dateType: "d",
               value: day - startweek + 1
            };

            if(!firstDay) {
               firstDay = selectedDate;
            }
            else {
               lastDay = selectedDate;
            }
         }

         if(firstDay) {
            this.dates = this.secondCalendar && lastDay ? [lastDay] : [firstDay];

            if(lastDay) {
               this.selectRange.emit(this.secondCalendar ? firstDay : lastDay);
            }
         }

         this.addDataPath(DataPathConstants.MONTH_CALENDAR);
      }
      else {
         let newDates: SelectedDateModel[] = [];

         for(let i = 0; i < 6; i++) {
            let day: number = weekday + 7 * i;

            if(this.days[day].disabled) {
               continue;
            }

            let selectedDate: SelectedDateModel = <SelectedDateModel> {
               year: this.currentDate.year,
               month: this.currentDate.month,
               dateType: "d",
               value: day - startweek + 1
            };

            // check if this date was already selected
            let index = this.indexOfSelectedDate(selectedDate);

            if((event.ctrlKey || event.metaKey || this.model.multiSelect) && index != -1) {
               this.dates.splice(index, 1);

               let indx: number = this.dates.findIndex((date) => date.dateType == "m");

               if(indx == -1) {
                  this.removeDataPath(DataPathConstants.MONTH_CALENDAR);
               }
            }
            else {
               newDates.push(selectedDate);
               this.addDataPath(DataPathConstants.MONTH_CALENDAR);
            }
         }

         if((!event.ctrlKey && !event.metaKey && !this.model.multiSelect) ||
            this.model.singleSelection)
         {
            this.dates = [];
            this.removeDataPath(DataPathConstants.CALENDAR_TITLE);
            this.addDataPath(DataPathConstants.MONTH_CALENDAR);
         }

         this.dates = this.dates.concat(newDates);

         if(this.inited && this.model.doubleCalendar && this.model.period) {
            this.syncPeriods.emit(this.secondCalendar);
         }
      }

      // if single calendar and submit on change, call apply calendar
      if(!this.model.doubleCalendar && this.model.submitOnChange) {
         this.applyCalendar.emit();
      }
      else {
         this.resetDays();
         this.updateSelected();
         this.selectedDateChanged();
      }
   }

   /**
    * Select the month title
    */
   clickMonthTitle(event: MouseEvent): void {
      if(this.formatPainterMode || this.vsWizard) {
         return;
      }

      let selectedDate: SelectedDateModel = <SelectedDateModel> {
         year: this.currentDate.year,
         month: this.currentDate.month,
         dateType: "m",
         value: 0
      };

      if((event.ctrlKey || event.metaKey || this.model.multiSelect) &&
         this.indexOfSelectedDate(selectedDate) != -1)
      {
         this.dates = [];
         this.removeDataPath(DataPathConstants.MONTH_CALENDAR);
         this.removeDataPath(DataPathConstants.CALENDAR_TITLE);
      }
      else {
         // if click on month title, only select month no matter what settings
         this.dates = [selectedDate];
         this.removeDataPath(DataPathConstants.MONTH_CALENDAR);
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
         this.resetDays();
         this.updateSelected();
         this.selectedDateChanged();
      }
   }

   /**
    * Update the calendars selected cells
    */
   updateSelected(force?: boolean): void {
      const startweek = this.getStartWeek();
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
            // if week type, select all days in selected week
            if(selection.dateType == "w") {
               for(let i = 0; i < 7; i++) {
                  this.days[(selection.value - 1) * 7 + i].selected = true;
               }
            }
            else if(selection.dateType == "d") {
               // if day type, select day
               this.days[((selection.value - 1) + startweek)].selected = true;
            }
         }
      }
   }

   // repaint days
   dateChanged(): void {
      this.daysInMonth = this.isLeapYear(this.currentDate.year)
         ? [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
         : [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

      this.monthTitle = this.monthNames[this.currentDate.month] + " " + this.currentDate.year;

      let pyear: number = this.currentDate.year; // previous month year
      let pmonth: number = this.currentDate.month - 1; // previous month
      let nyear: number = this.currentDate.year; // next month year
      let nmonth: number = this.currentDate.month + 1; // next month

      if(pmonth < 0) {
         pmonth = 11;
         pyear--;
      }

      if(nmonth >= 12) {
         nmonth = 0;
         nyear++;
      }

      const startweek = this.getStartWeek();

      let i: number;
      let day: number;

      // previous month days
      for(i = 0; i < startweek; i++) {
         day = this.daysInMonth[pmonth] - (startweek - i) + 1;
         this.days[i] = {year: pyear, month: pmonth, day: day};
         this.days[i].disabled = true;
      }

      // current month
      for(i = 0; i < this.daysInMonth[this.currentDate.month]; i++) {
         day = i + 1;
         this.days[i + startweek] = {year: this.currentDate.year,
                                     month: this.currentDate.month,
                                     day: day};
         this.days[i + startweek].disabled = !this.isInRange(this.currentDate.year,
                                                             this.currentDate.month, day);
      }

      // next month
      i = this.daysInMonth[this.currentDate.month] + startweek;

      for(let n: number = 1; i < 6 * 7; i++, n++) {
         this.days[i] = {year: nyear, month: nmonth, day: n};
         this.days[i].disabled = true;
      }

      this.updateSelected();
   }

   /**
    * Check if year is a leap year
    * @param year          the year
    * @returns {boolean} true if is leap year, false otherwise
    */
   private isLeapYear(year: number): boolean {
      if(year % 4 == 0) {
         if(year % 100 == 0) {
            return year % 400 == 0;
         }

         return true;
      }

      return false;
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
    * Update year and month on selected dates to currentDate month and year
    */
   updateSelectedDates(): void {
      for(let selection of this.dates) {
         selection.year = this.currentDate.year;
         selection.month = this.currentDate.month;
      }
   }

   /**
    * Called when double calendar needs to sync current dates
    * @param changeYear  true to update Year
    */
   public syncDate(changeYear: boolean): void {
      if(changeYear) {
         this.nextYear(this.secondCalendar ? 1 : -1, true);
      }
      else {
         this.nextMonth(this.secondCalendar ? 1 : -1, true);
      }
   }

   /**
    * Sync comparison values on calendars in period mode
    * @param values  the other calendars selected values
    * @param onApply true if called from apply Calendar method
    */
   public syncPeriod(values: SelectedDateModel[], onApply?: boolean): void {
      if(values.length > 0) {
         if(values[0].dateType == "m") {
            // if other calendar has selected month, select current month
            let selectedDate: SelectedDateModel = <SelectedDateModel> {
               year: this.currentDate.year,
               month: this.currentDate.month,
               dateType: "m"
            };
            this.dates = [selectedDate];

            // no need to update self before apply event
            if(!onApply) {
               this.resetDays();
               this.updateSelected();
            }
         }
         else {
            // if this calendar current has month selected, de-select dates
            if(this.dates.length == 1 && this.dates[0].dateType == "m") {
               this.dates = [];
            }

            // if other calendar has more selected dates, match selection length
            if(this.dates.length < values.length) {
               for(let i = values.length - 1;
                   i >= 0 && this.dates.length < values.length; i--)
               {
                  let searchDate: SelectedDateModel = <SelectedDateModel> {
                     year: this.currentDate.year,
                     month: this.currentDate.month,
                     value: values[i].value,
                     dateType: values[i].dateType
                  };
                  let searchIndex: number = this.indexOfSelectedDate(searchDate);

                  if(searchIndex == -1) {
                     this.dates.push(searchDate);
                  }
               }

               // no need to update self before apply event
               if(!onApply) {
                  this.resetDays();
                  this.updateSelected();
               }
            }
         }
      }
   }

   /**
    * Highlight selected cells in range
    * Reimplementation from MonthCalendar.as paintRange()
    * @param values  the selected values
    */
   public paintRange(values: SelectedDateModel[], otherCalendarEmpty: boolean = false): void {
      if(!values || values.length <= 0) {
         return;
      }

      values.sort(VSCalendar.calendarComparator);
      const startweek = this.getStartWeek();

      let start: number = -1;
      let end: number = -1;
      let first: SelectedDateModel = values[0];
      let last: SelectedDateModel = values[values.length - 1];

      if(values.length > 1) {
         start = Math.min(first.value, last.value);
         end = Math.max(first.value, last.value) + 1;

         if(this.model.daySelection) {
            start += startweek - 1;
            end += startweek - 1;
         }
         else {
            start = (start - 1) * 7;
            end = (end - 1) * 7;
         }
      }
      else if(values.length == 1) {
         if(!this.secondCalendar) {
            start = first.value;
            end = this.days.length + 1;

            if(this.model.daySelection) {
               start += startweek - 1;
            }
            else {
               start = (start - 1) * 7;
            }
         }
         else {
            start = 0;
            end = first.value + 1;

            if(this.model.daySelection) {
               end += startweek - 1;
            }
            else {
               end = (end - 1) * 7;
            }
         }
      }

      if(start != -1 && end != -1) {
         this.resetDays();

         for(let i = start; i < end && i < this.days.length; i++) {
            if(!this.model.daySelection || !this.days[i].disabled) {
               this.days[i].selected = true;
            }
         }
      }

      if(otherCalendarEmpty) {
         this.dates = [first, last];
      }
      else {
         this.dates = [this.secondCalendar ? last : first];
      }
   }

   /**
    * Get the calendars selection string for the title
    * Reimplementation of logic from MonthCalendar.as getSelectionString()
    * @param minimum             if this is the minimum for range comparison
    * @param previousSelection   the previous calendar selection string
    * @returns {string} the selection string for this calendar
    */
   getSelectionString(minimum?: boolean, previousSelection?: string): string {
      let selectionString: string = "";

      if(!this.dates || this.dates.length == 0) {
         return selectionString;
      }

      // use first selection date as basis for conditions
      let firstSelection: SelectedDateModel = this.dates[0];

      //if the month is selected just return year and month
      if(firstSelection.dateType == "m") {
         selectionString = firstSelection.year + "-" + (firstSelection.month + 1);
      }
      else if(firstSelection.dateType == "d") {
         if(minimum) {
            selectionString = firstSelection.year + "-" + (firstSelection.month + 1) + "-" +
               firstSelection.value;
         }
         else if(minimum != null) {
            let lastSelection: SelectedDateModel = this.dates[this.dates.length - 1];
            selectionString = lastSelection.year + "-" + (lastSelection.month + 1) + "-" +
               lastSelection.value;
         }
         else {
            selectionString = firstSelection.year + "-" + (firstSelection.month + 1) + "-" +
                  VSCalendar.getRangeString(this.dates, false);
         }
      }
      else {
         let oyear: number = -1;
         let omonth: number = -1;
         let first: boolean = true;
         let minDate;
         let maxDate;

         if(this.model.range != null) {
            minDate = new Date(this.model.range.minYear, this.model.range.minMonth, this.model.range.minDay);
            maxDate = new Date(this.model.range.maxYear, this.model.range.maxMonth, this.model.range.maxDay);
         }

         for(let i = 0; i < this.dates.length; i++) {
            if(minimum != null && !minimum && i < this.dates.length - 1) {
               continue;
            }

            if(first) {
               first = false;
            }
            else {
               selectionString += ",";
            }

            let startweek: number = (this.dates[i].value - 1) * 7;
            let sarr: string[] = ["", ""];
            let startWeekDay = this.days[startweek];
            let startDayOffset = -1;
            let endDayOffset = -1;

            if(startWeekDay) {
               let startWeekDate = new Date(startWeekDay.year, startWeekDay.month, startWeekDay.day);
               let endWeekDate = new Date(startWeekDay.year, startWeekDay.month, startWeekDay.day + 6);

               if(minDate && minDate.getTime() > startWeekDate.getTime() && minDate.getTime() <= endWeekDate.getTime()) {
                  startDayOffset = (minDate.getTime() - startWeekDate.getTime()) / 1000 / 3600 / 24;
               }

               if(maxDate && maxDate.getTime() < endWeekDate.getTime() && maxDate.getTime() >= startWeekDate.getTime()) {
                  endDayOffset = (endWeekDate.getTime() - maxDate.getTime()) / 1000 / 3600 / 24;
               }
            }

            // get the strings for the start and end of week
            for(let j: number = startweek, k: number = 0; j < startweek + 7;
                j += 6, k++)
            {
               let currentDay = this.days[j];

               if(j == startweek && startDayOffset != -1) {
                  currentDay = this.days[j + startDayOffset];
               }
               else if(j == startweek + 6 && endDayOffset != -1) {
                  currentDay = this.days[j - endDayOffset];
               }

               if(currentDay.year != oyear) {
                  sarr[k] = currentDay.year + "-";
               }

               if(currentDay.month != omonth) {
                  sarr[k] += (currentDay.month + 1) + "-";
               }

               sarr[k] += currentDay.day;
               oyear = currentDay.year;
               omonth = currentDay.month;
            }

            if(minimum) {
               selectionString += sarr[0];
               break;
            }
            else if(minimum != null && !minimum) {
               let pdates: string[] = previousSelection == null ? null
                  : previousSelection.split("-");
               let endDate: string[] = sarr[1].split("-");
               let endDay: string = endDate[endDate.length - 1];

               if(pdates != null && pdates.length == 3 && oyear != -1 &&
                  String(oyear) == pdates[0] &&
                  String(omonth + 1) == pdates[1])
               {
                  if(endDate.length == 3) {
                     selectionString += sarr[1];
                  }
                  else if(endDate.length == 2) {
                     selectionString += oyear + "-" + sarr[1];
                  }
                  else {
                     selectionString += sarr[1];
                  }
               }
               else {
                  selectionString += oyear + "-" + (omonth + 1) + "-" + endDay;
               }
            }
            else {
               selectionString += sarr[0] + "." + sarr[1];
            }
         }
      }

      return selectionString;
   }

   getSelectedDates(): SelectedDateModel[] {
      if(this.dates.length > 0 && this.dates[0].dateType == "m") {
         return [];
      }

      return this.dates;
   }

   resetDays(): void {
      for(let day of this.days) {
         day.selected = false;
      }
   }

   isMonthSelected(): boolean {
      return this.dates.length === 1 && this.dates[0].dateType === "m";
   }

   isRowSelectable(row: number): boolean {
      return !this.model.daySelection && this.days[row * 7].disabled &&
         this.days[row * 7 + 6].disabled;
   }

   checkCurrentDate(): void {
      if(this.model.range.maxYear != -1 && this.currentDate.year > this.model.range.maxYear) {
         this.currentDate.year = this.model.range.maxYear;
         this.currentDate.month = this.model.range.maxMonth;
      }
      else if(this.model.range.minYear != -1 && this.currentDate.year < this.model.range.minYear) {
         this.currentDate.year = this.model.range.minYear;
         this.currentDate.month = this.model.range.minMonth;
      }
      else if(this.model.range.minYear != -1 && this.model.range.minMonth != -1 &&
         this.currentDate.year == this.model.range.minYear &&
         this.currentDate.month < this.model.range.minMonth)
      {
         this.currentDate.month = this.model.range.minMonth;
      }
      else if(this.model.range.maxYear != -1 && this.model.range.maxMonth != -1 &&
         this.currentDate.year == this.model.range.maxYear &&
         this.currentDate.month > this.model.range.maxMonth)
      {
         this.currentDate.month = this.model.range.maxMonth;
      }
   }

   isInRange(year: number, month: number, day: number): boolean {
      // -1 means no range limit is set
      return (this.model.range.minYear == -1 ||
              (year > this.model.range.minYear ||
               (year == this.model.range.minYear && month > this.model.range.minMonth) ||
               (year == this.model.range.minYear && month == this.model.range.minMonth &&
                day >= this.model.range.minDay))) &&
         (this.model.range.maxYear == -1 ||
          (year < this.model.range.maxYear ||
           (year == this.model.range.maxYear && month < this.model.range.maxMonth) ||
           (year == this.model.range.maxYear && month == this.model.range.maxMonth &&
            day <= this.model.range.maxDay)));
   }

   isBtnEnabled(previous: boolean, year: boolean): boolean {
      if(previous && this.model.range.minYear != -1) {
         if(year || this.currentDate.month == 0) {
            return this.currentDate.year - 1 >= this.model.range.minYear;
         }
         else if(this.currentDate.year == this.model.range.minYear) {
            return this.currentDate.month - 1 >= this.model.range.minMonth;
         }
      }
      else if(!previous && this.model.range.maxYear != -1) {
         if(year || this.currentDate.month == 11) {
            return this.currentDate.year + 1 <= this.model.range.maxYear;
         }
         else if(this.currentDate.year == this.model.range.maxYear) {
            return this.currentDate.month + 1 <= this.model.range.maxMonth;
         }
      }

      return true;
   }

   getDateArray(): string[] {
      return this.dates.map((date) => {
         let dateString: string = date.dateType + date.year + "-" + date.month;

         if(date.dateType == "w" || date.dateType == "d") {
            dateString += "-" + date.value;
         }

         return dateString;
      });
   }

   getCurrentDateString(): string {
      return this.currentDate.year + "-" + this.currentDate.month;
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

   isSelectedDayCell(row: number, col: number): boolean {
      return this.days[(row - 1) * 7 + col].selected &&
         (!this.model.daySelection || !this.days[(row - 1) * 7 + col].disabled);
   }

   getDayCellBackground(row: number, col: number): string {
      return this.isSelectedDayCell(row, col) && !this.days[(row - 1) * 7 + col].disabled
         ? this.selectedBgColor : this.model.monthFormat.background;
   }

   isCellFocused(row: number, col: number): boolean {
      return this.selectedRow == row && this.selectedCol == col;
   }

   ariaDateLabel(day: number): string {
      const month: string = this.monthNames[this.currentDate.month];
      const year: number = this.currentDate.year;
      const dayStr: string = this.monthDays[day - 1];

      return month + " " + dayStr + " " + year;
   }

   getDay(row: number, col: number) {
      let idx = (row - 1) * 7 + col;

      if(this.model.dayNames != null) {
         return this.model.dayNames[this.days[idx].day];
      }

      return this.days[idx].day;
   }

   getWeekName(index: number) {
      if(this.model.weekNames != null && this.model.weekNames.length == 7) {
         return this.model.weekNames[index];
      }

      return this.dayTitles[index];
   }

   getMonth(): string {
      let change: boolean = this.currentDate.year != this.ocurrentDate.year ||
         this.currentDate.month != this.ocurrentDate.month;

      if(!this.secondCalendar && this.model.calendarTitleView1 != null && !change) {
         return this.model.calendarTitleView1;
      }
      else if(this.secondCalendar && this.model.calendarTitleView2 != null && !change) {
         return this.model.calendarTitleView2;
      }

      return this.monthTitle;
   }

   /**
    * Returns the offset of the first week of the month
    */
   getStartWeek(): number {
      let startday = new Date(this.currentDate.year, this.currentDate.month, 1);
      return (7 - this.firstDayOfWeek + startday.getDay() + 1) % 7;
   }

   private selectedDateChanged() {
      if(this.dates && this.dates.length) {
         this.selectedDatesChange.emit(true);
      }
   }
}
