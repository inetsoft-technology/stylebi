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
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { Observable, Subscription } from "rxjs";
import { debounceTime, distinctUntilChanged, map } from "rxjs/operators";
import { NgbDatepicker, NgbDateStruct, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";
import { ComponentTool } from "../../../common/util/component-tool";
import { GuiTool } from "../../../common/util/gui-tool";
import { XSchema } from "../../../common/data/xschema";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { GetVSObjectModelEvent } from "../../../vsview/event/get-vs-object-model-event";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ContextProvider } from "../../context-provider.service";
import { VSInputSelectionEvent } from "../../event/vs-input-selection-event";
import { VSComboBoxModel } from "../../model/vs-combo-box-model";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { NavigationComponent } from "../abstract-nav-component";
import { NavigationKeys } from "../navigation-keys";
import { Tool } from "../../../../../../shared/util/tool";
import { FormInputService } from "../../util/form-input.service";
import { FirstDayOfWeekService } from "../../../common/services/first-day-of-week.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { TimeInstant } from "../../../common/data/time-instant";
import dayjs from "dayjs";
import timezone from "dayjs/plugin/timezone";
import toObject from "dayjs/plugin/toObject";
import customParseFormat from "dayjs/plugin/customParseFormat";

enum FocusRegions {
   NONE,
   SELECTION,
   INPUT,
   CALENDAR,
   HOUR,
   MINUTE,
   SECOND,
   MERIDIAN
}

dayjs.extend(timezone);
dayjs.extend(toObject);
dayjs.extend(customParseFormat);

@Component({
   selector: "vs-combo-box",
   templateUrl: "vs-combo-box.component.html",
   styleUrls: ["vs-combo-box.component.scss"]
})
export class VSComboBox extends NavigationComponent<VSComboBoxModel> implements OnChanges, OnDestroy, OnInit {
   @Input() set model(value: VSComboBoxModel) {
      const valueChanged = !this._model ||
         !Tool.isEquals(this._model.selectedObject, value.selectedObject) ||
         this._model.serverTZ != value.serverTZ;
      this._model = value;

      if(value.calendar) {
         if(value.minDate != null && value.minDate != "") {
            this.minDate = this.getDate(value.minDate);
         }
         else {
            this.minDate = this.defaultMinDate;
         }

         if(value.maxDate != null && value.maxDate != "") {
            this.maxDate = this.getDate(value.maxDate);
         }
         else {
            this.maxDate = this.defaultMaxDate;
         }
      }

      // don't lose user change if the value has not changed
      if(value.calendar && (valueChanged || !value.editable)) {
         if(value.selectedObject) {
            let dayjsVal = dayjs(value.selectedObject);

            if(value.serverTZ) {
               dayjsVal = dayjs.tz(value.selectedObject, value.serverTZID);
            }

            if(dayjsVal.isValid()) {
               const timeInstant = dayjsVal.toObject() as TimeInstant;

               this.selectedDate = {
                  year: timeInstant.years,
                  month: timeInstant.months + 1,
                  day: timeInstant.date
               };

               this.hours = timeInstant.hours;

               if(this.hours > 12) {
                  this.hours -= 12;
               }
               else if(this.hours < 1) {
                  this.hours = 12;
               }

               this.minutes = timeInstant.minutes;
               this.seconds = timeInstant.seconds;
               this.meridian = timeInstant.hours >= 12 ? "PM" : "AM";
            }

         }
         else {
            this.selectedDate = null;
            this.hours = this.minutes = this.seconds = 0;
            this.meridian = "AM";
         }
      }

      if(value.editable && !value.selectedLabel && !!value.selectedObject) {
         const index = this.getValueIndex(value.selectedObject);

         this._model.selectedLabel =
            index != -1 ? this._model.labels[index] : this._model.selectedObject;
      }
   }

   get model(): VSComboBoxModel {
      return this._model;
   }

   @Input() selected: boolean = false;
   @Input() submitted: Observable<boolean>;
   @Output() comboBoxChanged = new EventEmitter();
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;
   @ViewChild("selection") selection: ElementRef;
   @ViewChild("comboBoxInput") input: ElementRef;
   @ViewChild("calendarButton") calendarButton: ElementRef;
   @ViewChild("hour") hourRef: ElementRef;
   @ViewChild("minute") minuteRef: ElementRef;
   @ViewChild("second") secondRef: ElementRef;
   @ViewChild("meridianRef") meridianRef: ElementRef;
   @ViewChild(NgbDatepicker) dp: NgbDatepicker;
   readonly defaultMinDate: NgbDateStruct = {year: 1900, month: 1, day: 1};
   readonly defaultMaxDate: NgbDateStruct = {year: 2050, month: 12, day: 31};
   minDate: NgbDateStruct = this.defaultMinDate;
   maxDate: NgbDateStruct = this.defaultMaxDate;
   firstDayOfWeek: number = 1;
   isDropdownOpen = false;

   selectedDate: {
      year: number,
      month: number,
      day: number
   };

   hours: number;
   minutes: number;
   seconds: number;
   meridian: string = "AM";
   submittedForm: Subscription;
   private unappliedSelection = false;
   FocusRegions = FocusRegions;
   focused: FocusRegions = FocusRegions.NONE;
   // firefox consumes mousedown on <select>, causing the move handling to think mouse
   // is still pressed when mouse is moved afterwards, which moves combobox with mouse
   // even afater mouse is released. check if this is necessary after interactjs has
   // be upgraded (12.3)
   firefox: boolean = GuiTool.isFF();
   constructor(private socket: ViewsheetClientService,
               private formDataService: CheckFormDataService,
               private formInputService: FormInputService,
               private debounceService: DebounceService,
               protected context: ContextProvider,
               protected dataTipService: DataTipService,
               zone: NgZone,
               private ngbModal: NgbModal,
               private firstDayOfWeekService: FirstDayOfWeekService)
   {
      super(socket, zone, context, dataTipService);
   }

   ngOnInit(): void {
      this.firstDayOfWeekService.getFirstDay().subscribe((model) => {
         this.firstDayOfWeek = model.isoFirstDay;
      });
   }

   ngOnChanges(changes: SimpleChanges) {
      if(this.viewer && changes.submitted && this.submitted) {
         if(this.submittedForm) {
            this.submittedForm.unsubscribe();
            this.submittedForm = null;
         }

         this.submittedForm = this.submitted.subscribe((isSubmitted) => {
            if(isSubmitted && this.unappliedSelection) {
               this.applySelection();
            }
         });
      }
   }

   ngOnDestroy() {
      super.ngOnDestroy();

      if(this.submittedForm) {
         this.submittedForm.unsubscribe();
      }
   }

   toggleDropdown() {
      this.isDropdownOpen = !this.isDropdownOpen;
   }

   selectItem(entry: string) {
      this.isDropdownOpen = false;
      this.onChange(entry);
   }

   getLabelIndex(label: string): number {
      return this._model.labels.indexOf(label);
   }

   getValueIndex(value: string): number {
      return this._model.values.indexOf(value);
   }

   get inputPlaceholder(): string {
      switch(this._model.dataType) {
         case XSchema.DATE:
         case XSchema.TIME_INSTANT:
            return this._model.dateFormat || "yyyy-MM-dd";
         case XSchema.TIME:
            return "HH:mm:ss AM[PM]";
         default:
            return "";
      }
   }

   onChange(option: string): void {
      let index = this.getLabelIndex(option);

      if(this.model.editable || index != -1) {
         this.unappliedSelection = true;
         this._model.selectedLabel = option;
         this._model.selectedObject = index != -1 ? this._model.values[index] : option;

         if(this._model.refresh || this.model.writeBackDirectly) {
            this.applySelection();
         }
         else {
            this.formInputService.addPendingValue(this._model.absoluteName,
                                                  this._model.selectedObject);
         }
      }
   }

   onBlur(option: string): void {
      if(this._model.selectedLabel != option) {
         this.onChange(option);
      }
   }

   onInputDate(text: string): void {
      if(/^\d{4}\-(0?[1-9]|1[012])\-(0?[1-9]|[12][0-9]|3[01])$/.test(text)) {
         let temp = text.split("-");

         const date = {
            year: Number(temp[0]),
            month: Number(temp[1]),
            day: Number(temp[2])
         };

         this.updateDate(date);
      }
      else {
         this.onChange(text);
      }
   }

   onEnter(text: string): void {
      if(!!this.input) {
         this.input.nativeElement.blur();
      }

      this.onInputDate(text);
   }

   labelSearch = (text: Observable<string>) => {
      const model: VSComboBoxModel = this._model;

      return text.pipe(
         debounceTime(200),
         distinctUntilChanged(),
         map((term: string) => {
            if(!!term && !!model) {
               return term.length < 2 ? [] : model.labels.filter(
                  (value: string) => new RegExp(term, "gi").test(value)
               ).splice(0, model.rowCount);
            }

            return [];
         })
      );
   };

   updateDate(date: any): void {
      if(!this.isValidDate(date)) {
         ComponentTool.showMessageDialog(this.ngbModal, "_#(js:Error)",
            "_#(js:viewer.viewsheet.calendar.invalidDateRange)").then(() => {});

         return;
      }

      if(JSON.stringify(date) != JSON.stringify(this.selectedDate)) {
         this.selectedDate = date;
         this.applyDateSelection();
         this.dropdown.close();
      }
   }

   clearCalendar() {
      this.model.selectedLabel = null;
      this.model.selectedObject = null;
      this.model.values = [null];
      this.hours = 0;
      this.minutes = 0;
      this.seconds = 0;
      this.meridian == "AM";
      this.selectedDate = null;

      if(this._model.refresh) {
         const event = new VSInputSelectionEvent(this._model.absoluteName, null);
         this.debounceService.debounce(
            `InputSelectionEvent.${this.model.absoluteName}`,
            (evt, socket) => socket.sendEvent("/events/comboBox/applySelection", evt),
            500, [event, this.socket]);
      }
      else {
         this.formInputService.addPendingValue(this._model.absoluteName, null);
      }
   }

   private isValidDate(date: any): boolean {
      let min: Date = Tool.isEmpty(this.model.minDate)
         ? new Date(this.defaultMinDate.year, this.defaultMinDate.month, this.defaultMinDate.day)
         : this.getTimeInstant(this.model.minDate);
      let max: Date = Tool.isEmpty(this.model.maxDate)
         ? new Date(this.defaultMaxDate.year, this.defaultMaxDate.month, this.defaultMaxDate.day)
         : this.getTimeInstant(this.model.maxDate);

      return this.getDateTime(date).getTime() >= min.getTime()
         && this.getDateTime(date).getTime() <= max.getTime();
   }

   updateHours(hours: number): void {
      this.hours = hours;
      this.applyDateSelection();
   }

   updateMinutes(minutes: number): void {
      this.minutes = minutes;
      this.applyDateSelection();
   }

   updateSeconds(seconds: number): void {
      this.seconds = seconds;
      this.applyDateSelection();
   }

   updateMeridian(meridian: string): void {
      this.meridian = meridian;
      this.applyDateSelection();
   }

   // called when date/time changed
   private applyDateSelection() {
      if(this.ctrlDown) {
         this.pendingChange = true;
         return;
      }

      const today = new Date();
      const selectedDate = this.selectedDate ? this.selectedDate :
         { year: today.getFullYear(), month: today.getMonth() + 1, day: today.getDate()};

      if(!this.isValidDate(selectedDate)) {
         ComponentTool.showMessageDialog(this.ngbModal, "_#(js:Error)",
            "_#(js:viewer.viewsheet.calendar.invalidDateRange)").then(() => {});

         return;
      }

      let time = this.getDateTime(selectedDate).getTime();

      if(this._model.refresh) {
         const event = new VSInputSelectionEvent(this._model.absoluteName, time);
         this.debounceService.debounce(
            `InputSelectionEvent.${this.model.absoluteName}`,
            (evt, socket) => socket.sendEvent("/events/comboBox/applySelection", evt),
            500, [event, this.socket]);
      }
      else {
         this.formInputService.addPendingValue(this._model.absoluteName, time);
      }
   }

   private getDateTime(selectedDate: any): Date {
      let hours: number = this.hours;

      if (this.meridian == "AM" && this.hours == 12) {
         hours = 0;
      } else if (this.meridian == "PM" && this.hours < 12) {
         hours += 12;
      }

      let newDate: Date = new Date(selectedDate.year, selectedDate.month - 1,
         selectedDate.day, hours, this.minutes, this.seconds, 0);

      if (this.model.serverTZ) {
         // first format to a string and then parse date in the server timezone
         newDate = dayjs.tz(
            DateTypeFormatter.format(newDate, DateTypeFormatter.ISO_8601_TIME_INSTANT_FORMAT),
            DateTypeFormatter.ISO_8601_TIME_INSTANT_FORMAT, this.model.serverTZID).toDate();
      }

      return newDate;
   }

   showCalendar(): boolean {
      return this._model.calendar && (this._model.dataType == XSchema.DATE
            || this._model.dataType == XSchema.TIME_INSTANT);
   }

   showTime(): boolean {
      return this._model.calendar && (this._model.dataType == XSchema.TIME_INSTANT
            || this._model.dataType == XSchema.TIME);
   }

   getDateString(): string {
      let dateString = "";

      if(!!this.selectedDate) {
         dateString = this.selectedDate.year + "-" + this.selectedDate.month +
            "-" + this.selectedDate.day;

         if(!!this._model.dateFormat) {
            let instant: TimeInstant = DateTypeFormatter.toTimeInstant(dateString,
               "YYYY-MM-DD");
            dateString = DateTypeFormatter.formatInstant(instant, this._model.dateFormat);
         }
      }

      return dateString;
   }

   getDate(str: string): NgbDateStruct {
      let date: Date = this.getTimeInstant(str);
      return {year: date.getFullYear(), month: date.getMonth() + 1, day: date.getDate()};
   }

   getTimeInstant(str: string) {
      let fmt: string = "YYYY-MM-DD HH:mm:ss";

      if (this.model.serverTZ) {
         // first format to a string and then parse date in the server timezone
         return dayjs.tz(
            str,
            fmt, this.model.serverTZID).toDate();
      }

      let instant: TimeInstant = DateTypeFormatter.toTimeInstant(str, fmt);
      return DateTypeFormatter.timeInstantToDate(instant);
   }

   private applySelection(): void {
      this.unappliedSelection = false;
      this.comboBoxChanged.emit(this._model.absoluteName);

      this.formDataService.checkFormData(
         this.socket.runtimeId, this.model.absoluteName, null,
         () => {
            let event =
               new VSInputSelectionEvent(this._model.absoluteName, this._model.selectedObject);
            this.socket.sendEvent("/events/comboBox/applySelection", event);
         },
         () => {
            let event: GetVSObjectModelEvent =
               new GetVSObjectModelEvent(this.model.absoluteName);
            this.socket.sendEvent("/events/vsview/object/model", event);
         }
      );
   }

   /**
    * Keyboard navigation for this component.
    * @param {NavigationKeys} key
    */
   protected navigate(key: NavigationKeys): void {
      if(this.focused == FocusRegions.NONE) {
         if(this._model.dataType == XSchema.TIME) {
            this.focused = FocusRegions.HOUR;
            this.focusOnRegion();
         }
         else if(this.model.editable && !!this.input) {
            this.focused = FocusRegions.INPUT;
            this.focusOnRegion();
         }
         else if(this.model.calendar) {
            this.focused = FocusRegions.CALENDAR;
         }
         else if(!!this.selection) {
            this.focused = FocusRegions.SELECTION;
            this.focusOnRegion();
         }
      }
      else {
         const calendar: boolean = this.showCalendar();
         const time: boolean = this.showTime();

         if(key == NavigationKeys.RIGHT) {
            if(this.model.editable && !this.model.calendar &&
               this.focused == FocusRegions.INPUT)
            {
               this.focused = FocusRegions.SELECTION;
               this.focusOnRegion();
            }
            else if((calendar || time) && (time ? this.focused <= FocusRegions.MERIDIAN :
                  this.focused < FocusRegions.CALENDAR))
            {
               this.focused++;
               this.focusOnRegion();
            }
         }
         else if(key == NavigationKeys.LEFT) {
            if((this._model.dataType == XSchema.TIME &&
               this.focused >= FocusRegions.HOUR) ||
               (calendar && (this.model.editable ? this.focused >= FocusRegions.CALENDAR :
                  this.focused > FocusRegions.CALENDAR)))
            {
               this.focused--;
               this.focusOnRegion();
            }
            else if(this.model.editable && this.focused == FocusRegions.SELECTION) {
               this.focused = FocusRegions.INPUT;
               this.focusOnRegion();
            }
         }
         else if(key == NavigationKeys.SPACE && this.focused == FocusRegions.CALENDAR &&
            !!this.calendarButton)
         {
            this.calendarButton.nativeElement.click();
         }
      }
   }

   /**
    * Focus on the set region.
    */
   private focusOnRegion(): void {
      switch(this.focused) {
         case FocusRegions.NONE:
            break;
         case FocusRegions.SELECTION:
            if(!!this.selection) {
               this.selection.nativeElement.focus();
            }
            break;
         case FocusRegions.INPUT:
            if(!!this.input) {
               this.input.nativeElement.focus();
            }
            break;
         case FocusRegions.CALENDAR:
            if(!!this.calendarButton) {
               this.calendarButton.nativeElement.focus();
            }
            break;
         case FocusRegions.HOUR:
            if(!!this.hourRef) {
               this.hourRef.nativeElement.focus();
            }
            break;
         case FocusRegions.MINUTE:
            if(!!this.minuteRef) {
               this.minuteRef.nativeElement.focus();
            }
            break;
         case FocusRegions.SECOND:
            if(!!this.secondRef) {
               this.secondRef.nativeElement.focus();
            }
            break;
         case FocusRegions.MERIDIAN:
            if(!!this.meridianRef) {
               this.meridianRef.nativeElement.focus();
            }
            break;
      }
   }

   /**
    * Clear selection made by navigating.
    */
   protected clearNavSelection(): void {
      this.focused = FocusRegions.NONE;

      if(!!this.input) {
         this.input.nativeElement.blur();
      }

      if(!!this.selection) {
         this.selection.nativeElement.blur();
      }
   }

   preventPropagation(event: KeyboardEvent) {
      // Allow spaces
      if(event.keyCode === 32) {
         event.stopPropagation();
      }
   }

   private ctrlDown: boolean = false;
   private pendingChange: boolean = false;

   @HostListener("document: keyup", ["$event"])
   onKeyUp(event: KeyboardEvent) {
      if(event.keyCode == 17) {
         this.ctrlDown = false;

         if(this.pendingChange) {
            this.applyDateSelection();
            this.pendingChange = false;
         }
      }
   }

   @HostListener("document: keydown", ["$event"])
   onKeyDown(event: KeyboardEvent) {
      if(event.keyCode == 17) {
         this.ctrlDown = true;
      }
   }

   isSelected(): boolean {
      const vs: any = this.vsInfo;
      return vs.isAssemblyFocused && vs.isAssemblyFocused(this.model.absoluteName);
   }
}
