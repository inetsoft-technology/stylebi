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
import {
   AfterViewInit,
   ChangeDetectorRef,
   Component,
   DoCheck,
   ElementRef,
   EventEmitter,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   Output,
   Renderer2,
   SimpleChanges,
   ViewChild,
   HostListener, OnInit
} from "@angular/core";
import { TinyColor } from "@ctrl/tinycolor";
import { Subscription } from "rxjs";
import { DataPathConstants } from "../../../common/util/data-path-constants";
import { Tool } from "../../../../../../shared/util/tool";
import { GuiTool } from "../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { GetVSObjectModelEvent } from "../../../vsview/event/get-vs-object-model-event";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { CalendarActions } from "../../action/calendar-actions";
import { ContextProvider } from "../../context-provider.service";
import { CalendarSelectionEvent } from "../../event/calendar/calendar-selection-event";
import { ToggleDoubleCalendarEvent } from "../../event/calendar/toggle-double-calendar-event";
import { ToggleRangeComparisonEvent } from "../../event/calendar/toggle-range-comparison-event";
import { ToggleYearViewEvent } from "../../event/calendar/toggle-year-view-event";
import { ChangeVSObjectTextEvent } from "../../event/change-vs-object-text-event";
import { CurrentDateModel, SelectedDateModel } from "../../model/calendar/current-date-model";
import { VSCalendarModel } from "../../model/calendar/vs-calendar-model";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { MonthCalendar } from "./month-calendar.component";
import { YearCalendar } from "./year-calendar.component";
import { NavigationComponent } from "../abstract-nav-component";
import { NavigationKeys } from "../navigation-keys";
import { MiniMenu } from "../mini-toolbar/mini-menu.component";
import { DataTipService } from "../data-tip/data-tip.service";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { VSUtil } from "../../util/vs-util";
import { HttpClient } from "@angular/common/http";
import { CalendarDateFormatModel } from "../../model/calendar/calendar-date-format-model";
import { GlobalSubmitService } from "../../util/global-submit.service";

export enum SelectionRegions {
   NONE = -9,
   MENU = -8,
   DROPDOWN = -7,
   SWITCH = -6,
   LAST_YEAR = -5,
   LAST_MONTH = -4,
   TITLE = -3,
   NEXT_MONTH = -2,
   NEXT_YEAR = -1
}

const FORMATE_SELECTED_STRING = "../api/calendar/formatdates";
const FORMATE_CALENDAR_TITLE = "../api/calendar/formatTitle";

@Component({
   selector: "vs-calendar",
   templateUrl: "vs-calendar.component.html",
   styleUrls: ["vs-calendar.component.scss"],
})
export class VSCalendar extends NavigationComponent<VSCalendarModel>
   implements AfterViewInit, DoCheck, OnDestroy, OnChanges, OnInit {
   @Input() selected: boolean = false;
   @Input() atBottom = false;
   @Input() objectContainerHeight: number;
   @Output() onTitleResizeMove = new EventEmitter<number>();
   @Output() onTitleResizeEnd = new EventEmitter<void>();
   @Output() onToggleDoubleCalendar = new EventEmitter<boolean>();
   @Output() onOpenFormatPane = new EventEmitter<VSCalendarModel>();
   @ViewChild("calendar1") calendar1: MonthCalendar | YearCalendar;
   @ViewChild("calendar2") calendar2: MonthCalendar | YearCalendar;
   @ViewChild("minitoolbar") minitoolbar: ElementRef;
   @ViewChild("menu", {read: ElementRef}) miniMenu: ElementRef;
   @ViewChild("menu") miniMenuComponent: MiniMenu;
   @ViewChild("dropdownToggleRef", {read: ElementRef}) dropdownToggleRef: ElementRef;
   @ViewChild("toggleComparisonRef") toggleComparisonRef: ElementRef;
   private submitSubscription: Subscription;
   private mouseUpListener: Function;
   selectionTitle: string;
   selectedString: string;
   selectedDateFormat: string;
   formatedSelectedString: string;
   private _actions: CalendarActions;
   private actionSubscription: Subscription;
   editingTitle: boolean = false;
   SelectionRegions = SelectionRegions;
   selectedCellRow: number = SelectionRegions.NONE;
   selectedCellCol: number = SelectionRegions.NONE;
   leftCalendar: boolean = true;
   private miniMenuOpen: boolean = false;
   keyNavFocused: boolean = false;
   rowResizeLabel: string = null;
   iconColor: string;

   @Input()
   set model(value: VSCalendarModel) {
      this._model = value;
      this.updateSelectedTitle();
   }

   get model(): VSCalendarModel {
      return this._model;
   }

   get topPosition(): number {
      if(this.viewer || this.embeddedVS) {
         if(this.model.dropdownCalendar) {
            if(this.atBottom && this.model.calendarsShown) {
               const height = 18 * 8; //the constant 18 is defh from AssetUtil.java
               let popDown = this.objectContainerHeight - this.model.objectFormat.top -
                  this.model.titleFormat.height - height > 0;

               return popDown ? this.model.objectFormat.top : this.model.objectFormat.top - height;
            }
         }

         return this.model.objectFormat.top;
      }

      return null;
   }

   get height(): number {
      if(this.model.dropdownCalendar) {
         if(this.atBottom && this.model.calendarsShown) {
            const height = 18 * 8; //the constant 18 is defh from AssetUtil.java
            return this.model.objectFormat.height + height;
         }
         else {
            return this.model.titleFormat.height;
         }
      }

      return this.model.objectFormat.height;
   }

   get toolbarActions(): AssemblyActionGroup[] {
      return !!this._actions ? this._actions.toolbarActions : null;
   }

   constructor(private elementRef: ElementRef, private socket: ViewsheetClientService,
               private renderer: Renderer2, private changeDetectorRef: ChangeDetectorRef,
               private formDataService: CheckFormDataService,
               protected dataTipService: DataTipService,
               protected context: ContextProvider, zone: NgZone,
               protected dropdownService: FixedDropdownService,
               private http: HttpClient,
               private globalSubmitService: GlobalSubmitService) {
      super(socket, zone, context, dataTipService);
   }

   ngAfterViewInit(): void {
      this.changeDetectorRef.detectChanges();
   }

   ngOnInit() {
      if(!!this.globalSubmitService) {
         this.submitSubscription = this.globalSubmitService.globalSubmit()
            .subscribe(eventSource => {
               if(!this.model.submitOnChange) {
                  this.applyCalendar(eventSource);
               }
            });
      }
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes["selected"] && !this.selected) {
         this.editingTitle = false;
      }

      if(changes["model"]) {
         this.iconColor = VSCalendar.getIconColor(this.model);
      }
   }

   ngDoCheck() {
      this.updateSelectionString();

      // when finish init calendars, paint range highlight
      if(this._model.doubleCalendar && !this._model.period) {
         this.syncRangeHighlight(true);
      }
   }

   @Input()
   set actions(value: CalendarActions) {
      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }

      this._actions = value;

      if(value) {
         this.actionSubscription = value.onAssemblyActionEvent.subscribe((event) => {
            if("calendar toggle-year" === event.id) {
               this.toggleYear();
            }
            else if("calendar toggle-double-calendar" === event.id) {
               this.toggleDoubleCalendar();
            }
            else if("calendar clear" === event.id) {
               this.clearCalendar();
            }
            else if("calendar toggle-range-comparison" === event.id) {
               this.toggleRangeComparison();
            }
            else if("calendar multi-select" === event.id) {
               this.model.multiSelect = !this.model.multiSelect;
            }
            else if("calendar apply" === event.id) {
               this.applyCalendar();
            }
            else if("calendar show" === event.id || "calendar hide" === event.id) {
               this.toggleDropdown();
            }
            else if("menu actions" == event.id) {
               VSUtil.showDropdownMenus(event.event, this.getMenuActions(), this.dropdownService);
            }
            else if("calendar show-format-pane" == event.id) {
               this.onOpenFormatPane.emit(this.model);
            }
            else if("more actions" == event.id) {
               VSUtil.showDropdownMenus(event.event, this.getMoreActions(), this.dropdownService);
            }
         });
      }
   }

   ngOnDestroy(): void {
      super.ngOnDestroy();

      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }

      if(this.submitSubscription) {
         this.submitSubscription.unsubscribe();
         this.submitSubscription = null;
      }
   }

   /**
    * Called after selection is changed on a calendar, updates title selection
    */
   updateSelectionString(): void {
      let selectionString: string;
      let selection1: string;
      let selection2: string;

      if(this._model.doubleCalendar && this.calendar2) {
         if(this._model.period) {
            selection1 = this.calendar1.getSelectionString();
            selection2 = this.calendar2.getSelectionString();

            if(selection1 || selection2) {
               selectionString = selection1 + " & " + selection2;
            }
         }
         else {
            selection1 = this.calendar1.getSelectionString(true);
            selection2 = this.calendar2.getSelectionString(false, selection1);

            if(!selection1 || selection1.length == 0) {
               selection1 = this.calendar2.getSelectionString(true);
            }
            else if(!selection2 || selection2.length == 0) {
               selection2 = this.calendar1.getSelectionString(false);
            }

            if(selection1 || selection2) {
               // /u2192 is unicode for right arrow
               selectionString = selection1 + " \u2192 " + selection2;
            }
         }
      }
      else if(!!this.calendar1) {
         selectionString = this.calendar1.getSelectionString();
      }

      if(!selectionString) {
         this.selectedString = selectionString;
         this.updateSelectedTitle();
      }
      // format changed or selection changed.
      else if(this.selectedDateFormat !== this.model.selectedDateFormat ||
         this.selectedString != selectionString) {
         this.selectedString = selectionString;
         this.selectedDateFormat = this.model.selectedDateFormat;
         this.updateFormatedSelectedString(selectionString);
         this.globalSubmitService.updateState(
            this.model.absoluteName,
            [selectionString],
            !this.model.submitOnChange
         );
      }
   }

   updateFormatedSelectedString(selectionString: string) {
      let formatModel = {
         runtimeId: this.socket.runtimeId,
         assemblyName: this._model.absoluteName,
         dates: selectionString,
         doubleCalendar: this._model.doubleCalendar,
         period: this._model.period,
         monthView: !this._model.yearView
      };

      this.http.post<string>(FORMATE_SELECTED_STRING, formatModel).subscribe((res) => {
         if(!!res) {
            this.formatedSelectedString = res;
         }

         this.updateSelectedTitle();
      });
   }

   updateSelectedTitle() {
      if(this._model.title) {
         let displayTitle = this._model.title;

         if(!!this.selectedString && !!this.formatedSelectedString) {
            displayTitle += ": " + this.formatedSelectedString;
         }

         this.selectionTitle = displayTitle;
      }
      else {
         this.selectionTitle = this.formatedSelectedString;
      }
   }

   updateCalendarTitleView(secondCalendar: boolean) {
      // incase template not refresh finished before updating calendar title(60014).
      setTimeout(() => {
         this.updateCalendarTitleView0(secondCalendar);
      }, 0);
   }

   private updateCalendarTitleView0(secondCalendar: boolean) {
      if(secondCalendar && !this.calendar2 || !secondCalendar && !this.calendar1) {
         return;
      }

      let stringDate = secondCalendar ? this.calendar2.getCurrentDateString() :
         this.calendar1.getCurrentDateString();
      let formatModel = {
         runtimeId: this.socket.runtimeId,
         assemblyName: this._model.absoluteName,
         dates: stringDate,
         doubleCalendar: this._model.doubleCalendar,
         period: this._model.period,
         monthView: !this._model.yearView
      };

      this.http.post<string>(FORMATE_CALENDAR_TITLE, formatModel).subscribe((res) => {
         if(!!!res) {
            return;
         }

         if(secondCalendar) {
            this.model.calendarTitleView2 = res;

            if(this.calendar2) {
               this.calendar2.resetOldDate();
            }
         }
         else {
            this.model.calendarTitleView1 = res;

            if(this.calendar1) {
               this.calendar1.resetOldDate();
            }
         }
      });
   }

   clearCalendar(): void {
      if(this._model.submitOnChange) {
         this.socket.sendEvent("/events/calendar/clearCalendar/" + this._model.absoluteName);
      }
      else {
         this.model.dates1 = [];
         this.model.dates2 = [];
         this.selectedCellRow = SelectionRegions.NONE;
         this.selectedCellCol = SelectionRegions.NONE;

         [this.calendar1, this.calendar2].filter(a => a != null).forEach(cal => {
            if(cal instanceof MonthCalendar) {
               (<MonthCalendar> cal).resetDays();
            }
            else if(cal instanceof YearCalendar) {
               (<YearCalendar> cal).clearSelection();
            }
         });

         this.applyCalendar();
      }
   }

   toggleRangeComparison(): void {
      if(this._model.doubleCalendar) {
         let dateArray: string[];
         let currentDate1: string;
         let currentDate2: string;

         if(this._model.period) {
            // changing to range mode, only 1 selection per calendar
            if(this.calendar1.dates.length > 1) {
               this.calendar1.dates = this.calendar1.dates.slice(0, 1);
            }

            if(this.calendar2.dates.length > 1) {
               this.calendar2.dates = this.calendar2.dates.slice(-1);
            }

            // swap current dates if calendar1 has later date
            if(this.calendar1.currentDate.year > this.calendar2.currentDate.year ||
               (!this._model.yearView &&
                  this.calendar1.currentDate.year == this.calendar2.currentDate.year &&
                  this.calendar1.currentDate.month > this.calendar2.currentDate.month)) {
               currentDate1 = this.calendar2.getCurrentDateString();
               currentDate2 = this.calendar1.getCurrentDateString();
               dateArray = this.calendar2.getDateArray().concat(this.calendar1.getDateArray());
            }
            else if(this.calendar1.dates.length > 0 && this.calendar2.dates.length > 0 &&
               (this.calendar1.dates[0].month > this.calendar2.dates[0].month ||
                  (this.calendar1.dates[0].month == this.calendar2.dates[0].month &&
                     this.calendar1.dates[0].value > this.calendar2.dates[0].value))) {
               dateArray = this.calendar2.getDateArray().concat(this.calendar1.getDateArray());
            }
         }
         else if(this.model.comparisonVisible) {
            if(this.calendar1.dates.length < this.calendar2.dates.length) {
               let first: SelectedDateModel = Tool.clone(this.calendar2.dates[0]);
               first.year = this.calendar1.currentDate.year;
               first.month = first.dateType != "m" ?
                  this.calendar1.currentDate.month : first.month;
               this.calendar1.dates = [first];
               this.calendar2.dates = this.calendar2.dates.slice(-1, 1);
            }
            else if(this.calendar2.dates.length < this.calendar1.dates.length) {
               let last: SelectedDateModel =
                  Tool.clone(this.calendar1.dates[this.calendar1.dates.length - 1]);
               last.year = this.calendar2.currentDate.year;
               last.month = last.dateType != "m" ?
                  this.calendar2.currentDate.month : last.month;
               this.calendar1.dates = this.calendar1.dates.slice(0, 1);
               this.calendar2.dates = [last];
            }
         }
         else {
            return;
         }

         if(!currentDate1) {
            currentDate1 = this.calendar1.getCurrentDateString();
         }

         if(!currentDate2) {
            currentDate2 = this.calendar2.getCurrentDateString();
         }

         if(!dateArray) {
            dateArray = this.calendar1.getDateArray().concat(this.calendar2.getDateArray());
         }

         let event: ToggleRangeComparisonEvent = new ToggleRangeComparisonEvent(
            dateArray, currentDate1, currentDate2, !this._model.period);
         this.socket.sendEvent("/events/calendar/toggleRangeComparison/" +
            this._model.absoluteName, event);
      }
   }

   toggleDoubleCalendar(): void {
      this.formDataService.checkFormData(
         this.socket.runtimeId, this.model.absoluteName, null,
         () => {
            this.sendToggleDoubleCalendar();
            this.onToggleDoubleCalendar.emit(!this.model.doubleCalendar);
         });
   }

   private sendToggleDoubleCalendar(): void {
      let currentDate1: string = this.calendar1.getCurrentDateString();
      let currentDate2: string;

      if(!this._model.doubleCalendar || !this.calendar2) {
         let year: number = this._model.currentDate2.year;
         let month: number = this._model.currentDate2.month;

         if(this._model.range.maxYear != -1) {
            if(year > this._model.range.maxYear) {
               year = this._model.range.maxYear;
            }

            if(year == this._model.range.maxYear &&
               month > this._model.range.maxMonth) {
               month = this._model.range.maxMonth;
            }
         }

         if(year < this.calendar1.currentDate.year) {
            year = this.calendar1.currentDate.year;
         }

         if(year == this.calendar1.currentDate.year &&
            month < this.calendar1.currentDate.month) {
            month = this.calendar1.currentDate.month;
         }

         currentDate2 = year + "-" + month;
      }
      else if(this.calendar2) {
         currentDate2 = this.calendar2.getCurrentDateString();
      }

      let event: ToggleDoubleCalendarEvent = new ToggleDoubleCalendarEvent(
         currentDate1, currentDate2, !this._model.doubleCalendar);
      this.socket.sendEvent("/events/calendar/toggleDoubleCalendar/" + this._model.absoluteName,
         event);
   }

   toggleYear(): void {
      this.formDataService.checkFormData(
         this.socket.runtimeId, this.model.absoluteName, null,
         () => {
            this.sendToggleYear();
         });
   }

   private sendToggleYear(): void {
      // if switching to month view, make sure current date has a month in range
      if(this._model.yearView) {
         if(this.calendar1.currentDate.month == null) {
            if(this.calendar1.currentDate.year == this._model.range.minYear) {
               this.calendar1.currentDate.month = this._model.range.minMonth;
            }
            else {
               this.calendar1.currentDate.month = 0;
            }
         }

         if(this.calendar2 && this.calendar2.currentDate.month == null) {
            if(this.calendar2.currentDate.year == this._model.range.maxYear) {
               this.calendar2.currentDate.month = this._model.range.maxMonth;
            }
            else {
               this.calendar2.currentDate.month = 12;
            }
         }
      }

      let currentDate1: string = this.calendar1.getCurrentDateString();
      let currentDate2: string = this.calendar2 ?
         this.calendar2.getCurrentDateString() : "";

      let event: ToggleYearViewEvent = new ToggleYearViewEvent(
         currentDate1, currentDate2, !this._model.yearView);
      this.socket.sendEvent("/events/calendar/toggleYearView/" + this._model.absoluteName, event);
   }

   private ctrlDown: boolean = false;
   pendingChange: boolean = false;

   @HostListener("document: keyup", ["$event"])
   onKeyUp(event: KeyboardEvent): void {
      if(event.keyCode == 17) {
         this.ctrlDown = false;

         if(this.pendingChange && this.model.submitOnChange) {
            this.applyCalendar();
            this.pendingChange = false;
         }
      }
   }

   @HostListener("document: keydown", ["$event"])
   onKeyDown(event: KeyboardEvent): void {
      if(event.keyCode == 17) {
         this.ctrlDown = true;
      }
   }

   applyCalendar0(): void {
      if(this.ctrlDown) {
         this.pendingChange = true;

         if(this.calendar1) {
            this.calendar1.updateSelected();
         }

         if(this.calendar2) {
            this.calendar2.updateSelected();
         }
      }
      else {
         this.applyCalendar();
      }
   }

   applyCalendar(eventSource: string = null): void {
      this.pendingChange = false;
      this.formDataService.checkFormData(
         this.socket.runtimeId, this.model.absoluteName, null,
         () => {
            this.sendApplyCalendar(eventSource);
         },
         () => {
            let event: GetVSObjectModelEvent =
               new GetVSObjectModelEvent(this.model.absoluteName);
            this.socket.sendEvent("/events/vsview/object/model", event);
         });
   }

   selectedDatesChange() {
      this.pendingChange = true;
   }

   private sendApplyCalendar(eventSource: string): void {
      let dateArray: string[];
      let currentDate1: string = this.calendar1.getCurrentDateString();
      let currentDate2: string = this.calendar2 ?
         this.calendar2.getCurrentDateString() : null;

      if(this._model.doubleCalendar && this.calendar2) {
         if(this._model.period) {
            if(this.calendar1.dates.length < this.calendar2.dates.length) {
               this.calendar1.syncPeriod(this.calendar2.dates, true);
            }
            else if(this.calendar2.dates.length < this.calendar1.dates.length) {
               this.calendar2.syncPeriod(this.calendar1.dates, true);
            }
         }

         dateArray = this.calendar1.getDateArray().concat(this.calendar2.getDateArray());
      }
      else {
         dateArray = this.calendar1.getDateArray();
      }

      let event: CalendarSelectionEvent = new CalendarSelectionEvent(
         dateArray, currentDate1, currentDate2, eventSource);
      this.socket.sendEvent("/events/calendar/applyCalendar/" + this._model.absoluteName, event);
   }

   toggleDropdown(): void {
      if(this.model.calendarsShown) {
         this.onHide();
      }
      else {
         this.onShow();
      }
   }

   getActions(): AssemblyActionGroup[] {
      return this._actions ? this._actions.showingActions : [];
   }

   getMenuActions(): AssemblyActionGroup[] {
      return this._actions ? this._actions.menuActions : [];
   }

   getMoreActions(): AssemblyActionGroup[] {
      return this._actions ? this._actions.getMoreActions() : [];
   }

   updateTitle(): void {
      if(!this.viewer) {
         let event: ChangeVSObjectTextEvent = new ChangeVSObjectTextEvent(
            this._model.absoluteName, this._model.title);

         this.socket.sendEvent("/events/composer/viewsheet/objects/changeTitle", event);
         this.updateSelectedTitle();
         this.updateSelectionString();
      }
   }

   public syncDateChange(secondCalendar: boolean): void {
      if(!this.calendar1 || !this.calendar2) {
         return;
      }

      let currentCalendar: MonthCalendar | YearCalendar =
         secondCalendar ? this.calendar2 : this.calendar1;
      let otherCalendar: MonthCalendar | YearCalendar =
         secondCalendar ? this.calendar1 : this.calendar2;
      let currentDate: CurrentDateModel = currentCalendar.currentDate;
      let otherDate: CurrentDateModel = otherCalendar.currentDate;

      if(secondCalendar) {
         if(currentDate.year < otherDate.year) {
            otherCalendar.syncDate(true);
         }
         else if(!this._model.yearView && currentDate.year == otherDate.year &&
            currentDate.month < otherDate.month) {
            otherCalendar.syncDate(false);
         }
      }
      else {
         if(currentDate.year > otherDate.year) {
            otherCalendar.syncDate(true);
         }
         else if(!this._model.yearView && currentDate.year == otherDate.year &&
            currentDate.month > otherDate.month) {
            otherCalendar.syncDate(false);
         }
      }
   }

   public syncPeriods(secondCalendar: boolean): void {
      if(!this.calendar1 || !this.calendar2) {
         return;
      }

      // make other calendar update its dates to sync with calling calendar
      if(!secondCalendar) {
         this.calendar2.syncPeriod(this.calendar1.dates);
      }
      else {
         this.calendar1.syncPeriod(this.calendar2.dates);
      }
   }

   /**
    * Synchronize highlights across calendars in range mode.
    * Reimplementation of VSCalendarObj.as syncRangeHighlight()
    * @param secondCalendar   true if the second calendar called this function
    */
   public syncRangeHighlight(secondCalendar: boolean): void {
      if(!this.calendar1 || !this.calendar2) {
         return;
      }

      let currentCalendar: MonthCalendar | YearCalendar =
         secondCalendar ? this.calendar2 : this.calendar1;
      let otherCalendar: MonthCalendar | YearCalendar =
         secondCalendar ? this.calendar1 : this.calendar2;
      let currentDates: SelectedDateModel[] = currentCalendar.getSelectedDates();
      let otherDates: SelectedDateModel[] = otherCalendar.getSelectedDates();

      if(otherDates.length == 0 && currentDates.length == 1) {
         currentCalendar.updateSelected(true);
      }
      else if(Tool.isEquals(currentCalendar.currentDate, otherCalendar.currentDate)) {
         let selectionArray: SelectedDateModel[];

         // Select one cell on each calendar for range
         if(currentDates.length == 1 && otherDates.length == 1) {
            selectionArray = [currentDates[0], otherDates[0]];
         }
         // Select more than one cell on one calendar
         else if(currentDates.length > 1 && otherDates.length == 0) {
            selectionArray = [currentDates[0], currentDates[currentDates.length - 1]];
         }
         // select multiple cell on both calendar
         else if(currentDates.length > 0) {
            selectionArray = currentDates.concat(otherDates);
            selectionArray.sort(VSCalendar.calendarComparator);
         }

         if(selectionArray) {
            currentCalendar.paintRange(selectionArray);
            otherCalendar.paintRange(selectionArray);
         }
      }
      // if at least one cell is selected in both then paint range for both
      else if(otherDates.length > 0 && currentDates.length > 0) {
         currentCalendar.paintRange(currentDates);
         otherCalendar.paintRange(otherDates);
      }
         // if cell is selected in current calendar and 0 in other calendar
      // then paint range only for current calendar
      else if(otherDates.length == 0 && currentDates.length > 1) {
         currentCalendar.paintRange(currentDates, true);
      }
   }

   /**
    * When a month calendar in range mode selects day title, sync calendars
    * @param selectDay        the new selected day for other calendar
    * @param secondCalendar   if called calendar is second calendar
    */
   public selectRange(selectDay: SelectedDateModel, secondCalendar: boolean): void {
      if(!this.calendar1 || !this.calendar2) {
         return;
      }

      let otherCalendar: MonthCalendar | YearCalendar =
         secondCalendar ? this.calendar1 : this.calendar2;

      // select day in other calendar to sync with calling calendar
      otherCalendar.currentDate.year = selectDay.year;
      otherCalendar.currentDate.month = selectDay.month;
      otherCalendar.dates = [selectDay];
      (<MonthCalendar>otherCalendar).dateChanged();
   }

   /**
    * Reimplementation of VSCalendarObj.as getRangeString()
    */
   public static getRangeString(dates: SelectedDateModel[], zero: boolean): string {
      let start: number = -1;
      let end: number = -1;
      let rangeString: string = "";

      for (let selection of dates) {
         let newRange: boolean = false;

         // month type selected dates need to check against 'month' variable
         if(start < 0 || (selection.dateType == "m" ?
            selection.month > end + 1 : selection.value > end + 1)) {
            newRange = true;
         }
         else {
            end = selection.dateType == "m" ?
               selection.month : selection.value;
         }

         if(newRange) {
            if(start >= 0) {
               rangeString = this.appendRange(rangeString, start, end, zero);
            }

            start = selection.dateType == "m" ?
               selection.month : selection.value;
            end = start;
         }
      }

      return this.appendRange(rangeString, start, end, zero);
   }

   /**
    * Reimplementation of VSCalendarObj.as appendRange()
    * @param str
    * @param start
    * @param end
    * @param zero
    * @returns {string}
    */
   public static appendRange(str: string, start: number, end: number, zero: boolean): string {
      if(str.length > 0) {
         str += ",";
      }

      if(zero) {
         start++;
         end++;
      }

      if(start == end) {
         str += start;
      }
      else {
         str += start + "." + end;
      }

      return str;
   }

   /**
    * Comparator for year calendar selected dates, sorts ascending by year and month
    */
   public static calendarComparator(a: SelectedDateModel, b: SelectedDateModel): number {
      if(a.year < b.year) {
         return -1;
      }
      else if(a.year > b.year) {
         return 1;
      }
      else {
         if(a.month < b.month) {
            return -1;
         }
         else if(a.month > b.month) {
            return 1;
         }
         else {
            if(a.value < b.value) {
               return -1;
            }
            else if(a.value > b.value) {
               return 1;
            }
         }
      }

      return 0;
   }

   selectTitle(sel: boolean): void {
      // select vsobject before select parts
      if(!this.selected && !this.vsInfo.formatPainterMode && !this.viewer) {
         return;
      }

      if(!sel) {
         this.editingTitle = false;
      }

      if(!this._model.selectedRegions) {
         this._model.selectedRegions = [];
      }
      else if(this._model.selectedRegions.length > 0 && !sel) {
         this._model.selectedRegions = this._model.selectedRegions.filter(region => {
            return region != DataPathConstants.TITLE;
         });
      }

      if(this.viewer) {
         return;
      }

      if(sel && this._model.selectedRegions.indexOf(DataPathConstants.TITLE) == -1) {
         this._model.selectedRegions.push(DataPathConstants.TITLE);
      }
   }

   isTitleSelected(): boolean {
      return this._model.selectedRegions
         && this._model.selectedRegions.indexOf(DataPathConstants.TITLE) != -1;
   }

   titleResizeMove(event: any): void {
      this.rowResizeLabel = Math.max(GuiTool.MINIMUM_TITLE_HEIGHT, event.rect.height) + "";
      this.onTitleResizeMove.emit(event.rect.height);
   }

   titleResizeEnd(): void {
      this.rowResizeLabel = null;
      this.onTitleResizeEnd.emit();
   }

   onHide(): void {
      this.model.calendarsShown = false;
   }

   onShow(): void {
      this.model.calendarsShown = true;

      // Listen for mouseup if the mousedown was not on the viewsheet-pane or
      // viewer-app scrollbar
      this.mouseUpListener = this.renderer.listen(
         "document", "mousedown", (event: MouseEvent) => {
            if(GuiTool.parentContainsClass(<any>event.target, "fixed-dropdown") ||
               GuiTool.parentContainsClass(<any>event.target, "mobile-toolbar")) {
               return;
            }

            if(!this.elementRef.nativeElement.contains(event.target)) {
               this.onHide();
               this.mouseUpListener();
            }
         });
   }

   /**
    * Keyboard navigation for this component.
    * @param {NavigationKeys} key
    */
   protected navigate(key: NavigationKeys): void {
      this.keyNavFocused = true;

      if(this.miniMenuOpen) {
         return;
      }

      const maxCol: number = this.model.yearView ? 4 : 7;
      const maxRow: number = this.model.yearView ? 3 : 6;
      const calendarOpen: boolean = this.model.dropdownCalendar ?
         !!this.model.calendarsShown : true;

      if(key == NavigationKeys.DOWN && this.selectedCellRow <= SelectionRegions.NONE) {
         if(this.model.dropdownCalendar && !this.model.calendarsShown) {
            this.selectedCellRow = SelectionRegions.MENU;
            this.selectedCellCol = 0;
         }
         else if(this.miniToolbarFocus) {
            this.selectedCellRow = SelectionRegions.TITLE;
            this.selectedCellCol = 0;
         }
         else {
            this.selectedCellRow = 0;
            this.selectedCellCol = 0;
         }

         this.leftCalendar = true;
         this.updateMiniToolbarFocus(false);
         this.focus();
         return;
      }

      if(key == NavigationKeys.DOWN && calendarOpen) {
         if(this.isMiniBarSelected) {
            this.selectedCellRow = this.SelectionRegions.TITLE;
            this.selectedCellCol = 0;
         }
         else {
            this.selectedCellRow++;

            if(this.selectedCellRow <= 0) {
               this.selectedCellRow = 0;
               this.selectedCellCol = 0;
            }
            else if(this.selectedCellRow >= maxRow) {
               this.selectedCellRow = maxRow - 1;
            }
         }

         this.focus();
      }
      else if(key == NavigationKeys.UP && calendarOpen) {
         if(this.isMiniBarSelected) {
            return;
         }

         this.selectedCellRow--;

         if(this.miniToolbarFocus) {
            // Do nothing
         }
         else if(this.selectedCellRow == -1) {
            this.selectedCellRow = SelectionRegions.TITLE;
            this.selectedCellCol = 0;
         }
         else if(this.selectedCellRow < 0) {
            if(this.model.dropdownCalendar) {
               this.selectedCellRow = SelectionRegions.MENU;
               this.selectedCellCol = SelectionRegions.NONE;
            }
            else {
               this.selectedCellRow = SelectionRegions.NONE;
               this.selectedCellCol = SelectionRegions.NONE;
               this.updateMiniToolbarFocus(true);
            }
         }
      }
      else if(key == NavigationKeys.LEFT) {
         if(this.selectedCellRow == SelectionRegions.DROPDOWN) {
            this.selectedCellRow = SelectionRegions.MENU;
         }
         else if(this.selectedCellRow == SelectionRegions.MENU) {
         }
         else if(this.selectedCellRow < 0) {
            if(this.selectedCellRow == SelectionRegions.SWITCH) {
               this.selectedCellRow = SelectionRegions.NEXT_YEAR;
               this.selectedCellCol = -1;
               this.leftCalendar = true;
               this.focus();
               return;
            }

            this.selectedCellRow--;

            if(this.model.yearView && this.selectedCellRow == SelectionRegions.LAST_MONTH) {
               this.selectedCellRow = SelectionRegions.LAST_YEAR;
            }
            else if(this.model.yearView && this.selectedCellRow == SelectionRegions.NEXT_MONTH) {
               this.selectedCellRow = SelectionRegions.TITLE;
            }

            if(this.selectedCellRow < SelectionRegions.LAST_YEAR && this.leftCalendar) {
               this.selectedCellRow = SelectionRegions.LAST_YEAR;
            }

            if(this.leftCalendar && this.selectedCellRow < SelectionRegions.LAST_YEAR) {
               this.selectedCellRow = SelectionRegions.LAST_YEAR;
            }
            else if(this.selectedCellRow < SelectionRegions.LAST_YEAR &&
               !this.leftCalendar && this.model.doubleCalendar) {
               this.selectedCellRow = SelectionRegions.SWITCH;
            }
            else if(this.model.doubleCalendar && !this.leftCalendar &&
               this.selectedCellRow < SelectionRegions.LAST_YEAR) {
               this.leftCalendar = true;
               this.selectedCellRow = SelectionRegions.NEXT_YEAR;
            }

            this.focus();
            return;
         }

         this.selectedCellCol--;

         if(this.leftCalendar && this.selectedCellCol < 0) {
            this.selectedCellCol = 0;
         }
         else if(this.model.doubleCalendar && !this.leftCalendar &&
            this.selectedCellCol < 0) {
            this.leftCalendar = true;
            this.selectedCellCol = maxCol - 1;
         }
      }
      else if(key == NavigationKeys.RIGHT) {
         if(this.selectedCellRow == SelectionRegions.MENU) {
            this.selectedCellRow = SelectionRegions.DROPDOWN;
         }
         else if(this.selectedCellRow == SelectionRegions.DROPDOWN) {
         }
         else if(this.selectedCellRow < 0) {
            if(this.selectedCellRow == SelectionRegions.SWITCH) {
               this.selectedCellRow = SelectionRegions.LAST_YEAR;
               this.selectedCellCol = -1;
               this.leftCalendar = false;
               this.focus();
               return;
            }

            this.selectedCellRow++;

            if(this.model.yearView && this.selectedCellRow == SelectionRegions.LAST_MONTH) {
               this.selectedCellRow = SelectionRegions.TITLE;
            }
            else if(this.model.yearView && this.selectedCellRow == SelectionRegions.NEXT_MONTH) {
               this.selectedCellRow = SelectionRegions.NEXT_YEAR;
            }

            if(this.selectedCellRow > SelectionRegions.NEXT_YEAR && this.leftCalendar &&
               this.model.doubleCalendar) {
               this.selectedCellRow = SelectionRegions.SWITCH;
            }
            else if(this.model.doubleCalendar && this.leftCalendar &&
               this.selectedCellRow > SelectionRegions.NEXT_YEAR) {
               this.leftCalendar = false;
               this.selectedCellRow = SelectionRegions.LAST_YEAR;
            }
            else if(this.selectedCellRow > SelectionRegions.NEXT_YEAR) {
               this.selectedCellRow = -1;
            }

            this.focus();
            return;
         }

         this.selectedCellCol++;

         if(this.model.doubleCalendar && this.leftCalendar &&
            this.selectedCellCol >= maxCol) {
            this.leftCalendar = false;
            this.selectedCellCol = 0;
         }
         else if(this.selectedCellCol >= maxCol) {
            this.selectedCellCol = maxCol - 1;
         }
      }
      else if(key == NavigationKeys.SPACE) {
         const mockEvent: any = {ctrlKey: false, metaKey: false, shiftKey: false};
         const calendar: MonthCalendar | YearCalendar = this.leftCalendar ?
            this.calendar1 : this.calendar2;

         if(this.selectedCellRow == SelectionRegions.LAST_YEAR) {
            calendar.nextYear(-1);
         }
         else if(this.selectedCellRow == SelectionRegions.LAST_MONTH) {
            (calendar as MonthCalendar).nextMonth(-1);
         }
         else if(this.selectedCellRow == SelectionRegions.TITLE) {
            this.model.yearView ? (calendar as YearCalendar).clickYearTitle(mockEvent) :
               (calendar as MonthCalendar).clickMonthTitle(mockEvent);
         }
         else if(this.selectedCellRow == SelectionRegions.NEXT_MONTH) {
            (calendar as MonthCalendar).nextMonth(1);
         }
         else if(this.selectedCellRow == SelectionRegions.NEXT_YEAR) {
            calendar.nextYear(1);
         }
         else if(this.selectedCellRow == SelectionRegions.SWITCH) {
            this.toggleRangeComparison();
         }
         else if(this.selectedCellRow == SelectionRegions.DROPDOWN) {
            this.model.calendarsShown ? this.onHide() : this.onShow();
         }
         else if(this.selectedCellRow == SelectionRegions.MENU) {
            if(!!this.miniMenu) {
               const box: ClientRect =
                  this.miniMenu.nativeElement.getBoundingClientRect();
               const x: number = box.left;
               const y: number = box.top + box.height;
               const event: MouseEvent = new MouseEvent("click", {
                  bubbles: true,
                  clientX: x,
                  clientY: y
               });
               this.miniMenuComponent.openMenu(event);
               this.miniMenuOpen = true;
            }
         }
         else {
            calendar.clickCell(this.selectedCellRow, this.selectedCellCol, mockEvent);
         }
      }

      this.focus();
   }

   /**
    * Focus on regions for screen readers.
    */
   private focus(): void {
      const calendar: MonthCalendar | YearCalendar = this.leftCalendar ?
         this.calendar1 : this.calendar2;
      const year: boolean = this.model.yearView;

      if(this.selectedCellRow == SelectionRegions.NONE) {
         // Do nothing
      }
      else if(this.selectedCellRow == SelectionRegions.LAST_YEAR) {
         if(!!calendar.lastYearRef) {
            calendar.lastYearRef.nativeElement.focus();
         }
      }
      else if(this.selectedCellRow == SelectionRegions.LAST_MONTH && !year) {
         const monthCalendar: MonthCalendar = calendar as MonthCalendar;

         if(!!monthCalendar.lastMonthRef) {
            monthCalendar.lastMonthRef.nativeElement.focus();
         }
      }
      else if(this.selectedCellRow == SelectionRegions.TITLE) {
         if(!!calendar.titleRef) {
            calendar.titleRef.nativeElement.focus();
         }
      }
      else if(this.selectedCellRow == SelectionRegions.NEXT_MONTH && !year) {
         const monthCalendar: MonthCalendar = calendar as MonthCalendar;

         if(!!monthCalendar.nextMonthRef) {
            monthCalendar.nextMonthRef.nativeElement.focus();
         }
      }
      else if(this.selectedCellRow == SelectionRegions.NEXT_YEAR) {
         if(!!calendar.nextYearRef) {
            calendar.nextYearRef.nativeElement.focus();
         }
      }
      else if(this.selectedCellRow == SelectionRegions.SWITCH) {
         if(!!this.toggleComparisonRef) {
            this.toggleComparisonRef.nativeElement.focus();
         }
      }
      else if(this.selectedCellRow == SelectionRegions.DROPDOWN) {
         if(!!this.dropdownToggleRef) {
            this.dropdownToggleRef.nativeElement.focus();
         }
      }
      else if(this.selectedCellRow == SelectionRegions.MENU) {
         if(!!this.miniMenu) {
            this.miniMenu.nativeElement.focus();
         }
      }
      else {
         const firstIndex: number = year ? this.selectedCellRow : this.selectedCellCol;
         const childIndex: number = year ? this.selectedCellCol : this.selectedCellRow + 1;

         if(!!calendar.listRef && !!calendar.listRef.nativeElement &&
            !!calendar.listRef.nativeElement.children &&
            calendar.listRef.nativeElement.children.length > firstIndex) {
            const colRef = calendar.listRef.nativeElement.children[firstIndex];

            if(!!colRef && !!colRef.children && colRef.children.length > childIndex) {
               const cellRef = colRef.children[childIndex];

               if(!!cellRef) {
                  cellRef.focus();
               }
            }
         }
      }
   }

   /**
    * Clear selection made by navigating.
    */
   protected clearNavSelection(): void {
      this.selectedCellRow = SelectionRegions.NONE;
      this.selectedCellCol = SelectionRegions.NONE;
      this.leftCalendar = true;
      this.keyNavFocused = false;
   }

   get isMiniBarSelected(): boolean {
      return this.selectedCellRow == SelectionRegions.MENU ||
         this.selectedCellRow == SelectionRegions.DROPDOWN;
   }

   miniMenuClosed(): void {
      this.miniMenuOpen = false;
   }

   // toggle dropdown on entire header on mobile (usability)
   public headerClick() {
      if(this.mobileDevice && this.model.dropdownCalendar) {
         if(this.model.calendarsShown) {
            this.onHide();
         }
         else {
            this.onShow();
         }
      }
   }

   /**
    * Get an icon color that is based on the assembly's color
    */
   public static getIconColor(model: VSCalendarModel): string {
      if(model && model.objectFormat) {
         let color = new TinyColor(model.objectFormat.foreground);

         if(color.isValid) {
            return color.tint(30).greyscale().toHexString(false);
         }
      }

      return "inherit";
   }

   public getPendingIconPosition(model: VSCalendarModel): number {
      let pos;

      if(model.titleVisible) {
         pos = 3;
      }
      else {
         pos = 40;
      }

      return pos;
   }

   getHTMLText(): string {
      return GuiTool.getHTMLText(this.selectionTitle);
   }
}
