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
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import {
   UntypedFormArray,
   UntypedFormControl,
   UntypedFormGroup,
   ValidationErrors,
   ValidatorFn,
   Validators
} from "@angular/forms";
import { NgbDateStruct, NgbModal, NgbTimeStruct } from "@ng-bootstrap/ng-bootstrap";
import { FeatureFlagValue } from "../../../../../../../shared/feature-flags/feature-flags.service";
import { CompletionConditionModel } from "../../../../../../../shared/schedule/model/completion-condition-model";
import { ScheduleConditionModel } from "../../../../../../../shared/schedule/model/schedule-condition-model";
import { TaskConditionPaneModel } from "../../../../../../../shared/schedule/model/task-condition-pane-model";
import {
   TimeConditionModel,
   TimeConditionType,
   TimeRange
} from "../../../../../../../shared/schedule/model/time-condition-model";
import { TimeZoneModel } from "../../../../../../../shared/schedule/model/time-zone-model";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../../shared/util/tool";
import { ComponentTool } from "../../../../common/util/component-tool";
import { LocalStorage } from "../../../../common/util/local-storage.util";
import {
   getStoredCondition,
   storeCondition
} from "../../../../common/util/schedule-condition.util";
import { StartTimeData } from "../../../../widget/schedule/start-time-data";
import { Observable } from "rxjs";
import { ScheduleTaskDialogModel } from "../../../../../../../shared/schedule/model/schedule-task-dialog-model";

const TASK_URI = "../api/portal/schedule/task/condition";
const TZ_STORAGE_KEY: string = "inetsoft_conditionServerTimeZone";

enum ChangeType {
   /**
    * Doing nothing for the time.
    */
   None,
   /**
    * Change time to server time zone.
    */
   ToServerTimeZone,
   /**
    * Change time to local time zone.
    */
   ToLocalTimeZone,
   /**
    * Adjust time for model from server.
    *
    * 1. If AT condition, adjust time zone for date if client is using server time zone,
    *    and start/endTime will be adjust by date.
    * 2. If not AT condition, adjust time zone for start/endTime if client is using local time zone.
    */
   AdjustServerModel
}

@Component({
   selector: "task-condition-pane",
   templateUrl: "./task-condition-pane.component.html",
   styleUrls: ["./task-condition-pane.component.scss"]
})
export class TaskConditionPane implements OnInit, OnChanges {
   @Input() oldTaskName: string;
   @Input() taskName: string;
   @Input() timeZone: string;
   @Input() timeZoneOptions: TimeZoneModel[];
   @Input() taskOwner: string;
   @Input() taskDefaultTime: boolean;
   @Input() parentForm: UntypedFormGroup;
   @Input() listView: boolean = false;
   @Input() timeRanges: TimeRange[] = [];
   @Input() startTimeEnabled = true;
   @Input() timeRangeEnabled = true;
   @Input() newTask: boolean;
   @Input() saveTask: () => Promise<any>;
   @Output() loaded = new EventEmitter<ScheduleTaskDialogModel>();
   @Output() updateTaskName = new EventEmitter<string>();
   @Output() listViewChanged = new EventEmitter<boolean>();
   @Output() closeEditor = new EventEmitter<TaskConditionPaneModel>();
   @Output() cancelTask = new EventEmitter();
   @Input() public set model(value: TaskConditionPaneModel) {
      this._model = value;

      if(!value.conditions || value.conditions.length == 0) {
         this.conditionIndex = -1;
      }
   }

   public get model(): TaskConditionPaneModel {
      return this._model;
   }

   allOptions = [
      { label: "_#(js:Daily)", value: TimeConditionType.EVERY_DAY },
      { label: "_#(js:Weekly)", value: TimeConditionType.EVERY_WEEK },
      { label: "_#(js:Monthly)", value: TimeConditionType.EVERY_MONTH },
      { label: "_#(js:Hourly)", value: TimeConditionType.EVERY_HOUR },
      { label: "_#(js:Custom)", value: "UserCondition" },
      { label: "_#(js:Run Once)", value: TimeConditionType.AT },
      { label: "_#(js:Chained)", value: "CompletionCondition" }

   ];
   enabledOptions = [];

   weekdays: string[] = [
      "_#(js:Sunday)",
      "_#(js:Monday)",
      "_#(js:Tuesday)",
      "_#(js:Wednesday)",
      "_#(js:Thursday)",
      "_#(js:Friday)",
      "_#(js:Saturday)"
   ];
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
      "_#(js:Day before last day)",
      "_#(js:Last day of month)"
   ];
   daysOfMonthNum: number[] = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
      18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
      TimeConditionType.LAST_DAY_OF_MONTH - 1, TimeConditionType.LAST_DAY_OF_MONTH];
   monthWeeks: string[] = [
      "_#(js:1st)",
      "_#(js:2nd)",
      "_#(js:3rd)",
      "_#(js:4th)",
      "_#(js:5th)"
   ];
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
   defaultDayOfMonth: number = this.daysOfMonthNum[0];
   defaultWeekOfMonth: number = 1;
   defaultDayOfWeek: number = 1;

   readonly FeatureFlagValue = FeatureFlagValue;
   private _model: TaskConditionPaneModel;
   private conditionIndex: number = 0;
   public TimeConditionType = TimeConditionType;

   selectedOption: any = this.allOptions[0].value;
   selectedConditions: number[] = [];
   conditionLastClicked: number = -1;

   date: NgbDateStruct;
   _startTime: NgbTimeStruct;
   endTime: NgbTimeStruct;
   startTimeData: StartTimeData;

   timeZoneName: string;
   serverTimeZone: boolean = true;
   timeZoneOffset: number;
   localTimeZoneId: string = null;
   localTimeZoneLabel: string;

   get startTime(): NgbTimeStruct {
      return this._startTime;
   }

   set startTime(startTime: NgbTimeStruct) {
      this._startTime = this.taskDefaultTime !== false ? startTime : null;
   }

   form: UntypedFormGroup = null;

   // keep track of the values for each condition type
   chainedCondition: CompletionConditionModel;
   dailyCondition: TimeConditionModel;
   weeklyCondition: TimeConditionModel;
   monthlyCondition: TimeConditionModel;
   hourlyCondition: TimeConditionModel;
   runOnceCondition: TimeConditionModel;

   get showMeridian(): boolean {
      return this.model && this.model.twelveHourSystem;
   }

   constructor(private http: HttpClient,
      private modalService: NgbModal) {
   }

   get condition(): ScheduleConditionModel {
      let conditionsLength = this.model.conditions.length;
      let defaultEmptyCondition: ScheduleConditionModel = {
         label: null,
         conditionType: "CompletionCondition"
      };

      if(this.conditionIndex == -1 && this.model.conditions && conditionsLength > 0) {
         return this.model.conditions[conditionsLength - 1];
      }

      if(this.model.conditions && conditionsLength > this.conditionIndex &&
         conditionsLength > 0) {
         return this.model.conditions[this.conditionIndex];
      }

      this.model.conditions.push(defaultEmptyCondition);

      return defaultEmptyCondition;
   }

   set condition(value: ScheduleConditionModel) {
      if(this.model.conditions) {
         this.conditionIndex = Math.max(0, this.conditionIndex);

         for(let i = this.model.conditions.length; i < this.conditionIndex + 1; i++) {
            this.model.conditions.push(null);
         }

         this.model.conditions[this.conditionIndex] = value;
      }
      else {
         this.model.conditions = [];

         for(let i = 0; i < this.conditionIndex - 1; i++) {
            this.model.conditions.push(null);
         }

         this.model.conditions.push(value);
      }
   }

   get timeCondition(): TimeConditionModel {
      return this.condition as TimeConditionModel;
   }

   get completionCondition(): CompletionConditionModel {
      return this.condition as CompletionConditionModel;
   }

   get conditionNames(): string[] {
      return !this.model.conditions ? [] : this.model.conditions.map(a => a.label);
   }

   ngOnInit(): void {
      if(!this.date) {
         const date = new Date();
         this.date = <NgbDateStruct>{
            day: date.getUTCDate(),
            month: date.getUTCMonth() + 1,
            year: date.getUTCFullYear()
         };
      }

      if(!this.taskDefaultTime) {
         this.setStartTime(null);
      }

      if(this.model.userDefinedClasses.length == 0) {
         this.allOptions.splice(4, 1);
      }

      this.getTimeZone();
      this.updateValues();
      this.enabledOptions = this.allOptions.filter((option) =>
         this.startTimeEnabled || option.value !== TimeConditionType.AT &&
         option.value !== TimeConditionType.EVERY_HOUR);
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(!!changes.listView && !changes.listView.currentValue && !changes.listView.firstChange) {
         this.addCondition();
      }
   }

   public changeConditionClass(label: string) {
      const index: number = this.model.userDefinedClassLabels.indexOf(label);
   }

   private convertTimeZone(time: number, type: ChangeType): Date {
      if(type == ChangeType.AdjustServerModel) {
         type = this.serverTimeZone ? ChangeType.ToServerTimeZone : ChangeType.None;
      }

      if(type === ChangeType.None) {
         return new Date(time);
      }

      if(type === ChangeType.ToServerTimeZone) {
         return this.convertTimeToServerTimeZone(time);
      }
      else {
         return this.convertTimeToLocalTimeZone(time);
      }
   }

   private convertTimeToServerTimeZone(time: number): Date {
      if(this.serverTimeZone) {
         return new Date(time);
      }

      const localOffset: number = this.getLocalTimezoneOffset();
      const serverOffset: number = (<TimeConditionModel>this.condition).timeZoneOffset || 0;
      time += localOffset + serverOffset;

      return new Date(time);
   }

   private convertTimeToLocalTimeZone(time: number): Date {
      const localOffset: number = this.getLocalTimezoneOffset();
      const serverOffset: number = (<TimeConditionModel>this.condition).timeZoneOffset || 0;
      time -= localOffset + serverOffset;

      return new Date(time);
   }

   private getLocalTimezoneOffset(): number {
      if(!this.timeZoneOffset) {
         let date = new Date();

         if(!!this.localTimeZoneId) {
            const UTC = new Date(date.toLocaleString([], { timeZone: "UTC" }));
            const selectedTZ = new Date(date.toLocaleString([], { timeZone: this.localTimeZoneId }));
            this.timeZoneOffset = (UTC.getTime() - selectedTZ.getTime());
         }
         else {
            this.timeZoneOffset = new Date().getTimezoneOffset() * 60000;
         }
      }

      return this.timeZoneOffset;
   }

   /**
    * Adjust given time to local timezone if necessary.
    * @param date
    */
   private adjustTimeZone(date: Date, type: ChangeType): NgbTimeStruct {
      let time: number = date.getTime();

      // for model from server, hour/minute/second don't need to adjust time zone.
      if(type == ChangeType.AdjustServerModel) {
         type = this.serverTimeZone ? ChangeType.None : ChangeType.ToLocalTimeZone;
      }

      let adjustedDate = this.convertTimeZone(time, type);

      return {
         hour: adjustedDate.getHours(),
         minute: adjustedDate.getMinutes(),
         second: adjustedDate.getSeconds()
      };
   }

   /**
    * If the timezone has been changed to local, change back to server.
    */
   private revertTimeZone(condition: ScheduleConditionModel): void {
      if(!this.serverTimeZone) {
         const localOffset: number = new Date().getTimezoneOffset() * 60000;
         const serverOffset: number =
            (<TimeConditionModel>condition).timeZoneOffset || 0;

         let startDate: Date = new Date();
         startDate.setHours((<TimeConditionModel>condition).hour);
         startDate.setMinutes((<TimeConditionModel>condition).minute);
         startDate.setSeconds((<TimeConditionModel>condition).second);
         let startTime: number = startDate.getTime();
         startTime += localOffset + serverOffset;
         startDate = new Date(startTime);
         (<TimeConditionModel>condition).hour = startDate.getHours();
         (<TimeConditionModel>condition).minute = startDate.getMinutes();
         (<TimeConditionModel>condition).second = startDate.getSeconds();

         let endDate: Date = new Date();
         endDate.setHours((<TimeConditionModel>condition).hourEnd);
         endDate.setMinutes((<TimeConditionModel>condition).minuteEnd);
         endDate.setSeconds((<TimeConditionModel>condition).secondEnd);
         let endTime: number = endDate.getTime();
         endTime += localOffset + serverOffset;
         endDate = new Date(endTime);
         (<TimeConditionModel>condition).hourEnd = endDate.getHours();
         (<TimeConditionModel>condition).minuteEnd = endDate.getMinutes();
         (<TimeConditionModel>condition).secondEnd = endDate.getSeconds();

         const time: number = (<TimeConditionModel>condition).date;
         const date: Date = this.convertTimeZone(time, ChangeType.ToServerTimeZone);
         (<TimeConditionModel>condition).date = date.getTime();
      }
   }

   private updateStartTime(type: ChangeType): void {
      if((<TimeConditionModel>this.condition).type != TimeConditionType.AT) {
         const date: Date = new Date();
         date.setHours((<TimeConditionModel>this.condition).hour);
         date.setMinutes((<TimeConditionModel>this.condition).minute);
         date.setSeconds((<TimeConditionModel>this.condition).second);

         this.startTime = {
            hour: (<TimeConditionModel>this.condition).hour,
            minute: (<TimeConditionModel>this.condition).minute,
            second: (<TimeConditionModel>this.condition).second
         };
      }
      else {
         const time: number = (<TimeConditionModel>this.condition).date;
         this.startTime = this.adjustTimeZone(new Date(time), type);
      }
   }

   private updateStartTimeData(): void {
      const range = ((<TimeConditionModel>this.condition).timeRange);
      const selected = !range && this.startTimeEnabled;
      const defaultTimeRange = this.timeRanges.length ? this.timeRanges[0] : null;
      this.startTimeData = {
         startTime: this.startTime,
         timeRange: range || defaultTimeRange,
         startTimeSelected: selected,
         valid: selected && !!this.startTime || !!(range || defaultTimeRange)
      };

      if(this.selectedOption !== TimeConditionType.AT && this.selectedOption !== TimeConditionType.EVERY_HOUR) {
         this.form?.controls["startTime"]?.setValue(this.startTimeData);
      }
   }

   public userSetStartTime(time: NgbTimeStruct): void {
      this.setStartTime(time);

      this.taskDefaultTime = !!time;
   }

   onStartTimeDataChanged(data: StartTimeData): void {
      if(data) {
         if(data.startTimeSelected) {
            this.setStartTime(data.startTime);
            (<TimeConditionModel>this.condition).timeRange = null;

            if(!this.serverTimeZone) {
               this.form.get("timeZone").enable();
            }
         }
         else {
            this.setStartTime(null);
            (<TimeConditionModel>this.condition).timeRange = data.timeRange;
            this.form.get("timeZone").disable();
         }
      }
      else {
         this.setStartTime(null);
         (<TimeConditionModel>this.condition).timeRange = null;
      }

      this.startTimeData = data;
      this.taskDefaultTime = !!data && data.valid;
   }

   private setStartTime(time: NgbTimeStruct): void {
      if(time && (<TimeConditionModel>this.condition).type != TimeConditionType.AT) {
         (<TimeConditionModel>this.condition).hour = time.hour;
         (<TimeConditionModel>this.condition).minute = time.minute;
         (<TimeConditionModel>this.condition).second = 0;
      }
      else if((<TimeConditionModel>this.condition).type == TimeConditionType.AT) {
         const dateTime: number = (<TimeConditionModel>this.condition).date;
         const localDate = this.convertTimeToLocalTimeZone(dateTime);

         //dateChange method will reduce one month
         this.dateChange({
            year: localDate.getFullYear(),
            month: localDate.getMonth(),
            day: localDate.getDate()
         }, false, time);
      }

      this.startTime = time;
   }

   private updateDate(type: ChangeType): void {
      const time: number = (<TimeConditionModel>this.condition).date;

      if(time) {
         const date: Date = this.convertTimeZone(time, type);
         //(<TimeConditionModel>this.condition).date = date.getTime();

         this.date = {
            year: date.getFullYear(),
            month: date.getMonth() + 1,
            day: date.getDate()
         };
      }
      else {
         this.date = null;
      }
   }

   public dateChange(date: NgbDateStruct, isYearToDay: boolean = true, time?: NgbTimeStruct): void {
      let month = isYearToDay ? date.month - 1 : date.month;
      let compiledDate: Date = null;
      let cond: TimeConditionModel = <TimeConditionModel>this.condition;

      if((<TimeConditionModel>this.condition).type != TimeConditionType.AT) {
         compiledDate = new Date(date.year, month, date.day, cond.hour, cond.minute, cond.second);
      }
      else {
         const date0: Date = new Date(cond.date);

         if(time) {
            compiledDate = new Date(date.year, month, date.day, time.hour, time.minute, 0);
            compiledDate = this.convertTimeToServerTimeZone(compiledDate.getTime());
         }
         else if(cond.hour != undefined && !Number.isNaN(cond.hour) && cond.hour != -1 &&
            cond.minute != undefined && !Number.isNaN(cond.minute) && cond.minute != -1 &&
            cond.second != undefined && !Number.isNaN(cond.second) && cond.second != -1) {
            compiledDate = new Date(date.year, month, date.day, cond.hour, cond.minute, cond.second);
         }
         else {
            compiledDate = new Date(date.year, month, date.day,
               date0.getHours(), date0.getMinutes(), date0.getSeconds());
         }
      }

      cond.date = compiledDate.getTime();
      cond.hour = -1;
      cond.minute = -1;
      cond.second = -1;
   }

   public updateEndTime(type: ChangeType): void {
      this.endTime = {
         hour: (<TimeConditionModel>this.condition).hourEnd,
         minute: (<TimeConditionModel>this.condition).minuteEnd,
         second: (<TimeConditionModel>this.condition).secondEnd
      };
   }

   public setEndTime(time: NgbTimeStruct): void {
      if(time) {
         (<TimeConditionModel>this.condition).hourEnd = time.hour;
         (<TimeConditionModel>this.condition).minuteEnd = time.minute;
         (<TimeConditionModel>this.condition).secondEnd = time.second;
      }
   }

   private setSelectedOption(): void {
      let optionType;

      if(!!(<TimeConditionModel>this.condition).type
         || (<TimeConditionModel>this.condition).type == 0) {
         optionType = (<TimeConditionModel>this.condition).type;
      }
      else {
         optionType = this.condition.conditionType;
      }

      this.selectedOption = optionType;
      this.initForm();
   }

   public changeConditionType(option: any): void {
      this.saveConditionType(this.selectedOption);
      const optionType = option.value;

      if(optionType != this.condition.conditionType &&
         optionType != (<TimeConditionModel>this.condition).type) {
         this.selectedOption = optionType;

         if(optionType === "CompletionCondition") {
            this.condition = this.chainedCondition;
         }
         else if(optionType === TimeConditionType.EVERY_DAY) {
            this.condition = this.dailyCondition;
         }
         else if(optionType === TimeConditionType.EVERY_WEEK) {
            this.condition = this.weeklyCondition;
         }
         else if(optionType === TimeConditionType.EVERY_MONTH) {
            this.condition = this.monthlyCondition;
         }
         else if(optionType === TimeConditionType.EVERY_HOUR) {
            this.condition = this.hourlyCondition;
         }
         else if(optionType === TimeConditionType.AT) {
            this.condition = this.runOnceCondition;
         }

         this.condition.label = "_#(js:New Condition)";
         this.getTimeZone();
         this.initForm();
         this.updateTimesAndDates(ChangeType.None);
      }
   }

   public changeView(multi: boolean): void {
      this.listView = multi;
   }

   public save(ok: boolean): void {
      this.saveTask().then(() => {
         storeCondition(this.condition);
         this.updateTimesAndDates(ChangeType.AdjustServerModel);
         this.form.markAsPristine();
         this.updateTaskName.emit(this.taskName);

         if(ok) {
            // if(!this.serverTimeZone) {
            //    this.updateTimesAndDates(ChangeType.ToServerTimeZone);
            // }

            if(this.model.conditions.length > 1) {
               this.changeView(true);
            }
         }
      });
   }

   private handleSaveError(error: any) {
      if(error.error == "Dependency cycle found!") {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", error.error);
      }
      else {
         ComponentTool.showHttpError("Failed to save schedule task", error, this.modalService);
      }
   }

   public addCondition(): void {
      const storedCondition = getStoredCondition();
      this.model.conditions.push(storedCondition ? storedCondition : this.condition);
      this.conditionIndex = this.model.conditions.length - 1;
      this.updateValues();
      this.listView = false;
   }

   public deleteCondition(): void {
      const message: string = "_#(js:em.scheduler.conditions.removeNote)";
      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", message).then(
         (result: string) => {
            if(result === "ok") {
               let taskName: string = this.oldTaskName.indexOf(this.taskOwner + ":") == 0 ?
                  this.oldTaskName : this.taskOwner + ":" + this.oldTaskName;
               const params = new HttpParams().set("name", Tool.byteEncode(taskName));
               const conditions: number[] = Tool.clone(this.selectedConditions);
               conditions.sort();
               conditions.reverse();

               this.http.post(TASK_URI + "/delete", conditions, { params: params })
                  .subscribe((condition: any) => {
                     for(let index of conditions) {
                        this._model.conditions.splice(index, 1);
                     }

                     this.selectedConditions = [];
                     this.conditionIndex = this._model.conditions.length - 1;
                  },
                     (error: string) => {
                        // Error
                     });
            }
         });
   }

   public editCondition(): void {
      if(this.selectedConditions.length == 0) {
         return;
      }

      this.conditionIndex = this.selectedConditions[0];
      this.condition = this.model.conditions[this.conditionIndex];
      this.getTimeZone();
      this.updateValues();
      this.listView = false;
      this.setSelectedOption();
   }

   private updateValues(): void {
      this.timeZoneOffset = null;

      if(this.localTimeZoneId) {
         this.getLocalTimezoneOffset();
      }
      else {
         this.timeZoneOffset = new Date().getTimezoneOffset() * 60000;
      }

      this.updateTimesAndDates(ChangeType.ToLocalTimeZone);
      this.listView = this.model.conditions && this.model.conditions.length > 1;
      this.setSelectedOption();
      this.initConditions();
   }

   private updateTimesAndDates(type: ChangeType): void {
      if(this.condition && this.condition.conditionType == "TimeCondition") {
         this.updateDate(type);
         this.updateStartTime(type);
         this.updateStartTimeData();
         this.updateEndTime(type);
      }
   }

   public changeMonthRadioOption(value: boolean): void {
      (<TimeConditionModel>this.condition).monthlyDaySelected = value;

      if(value) {
         this.addDayOfMonthControl();
      }
      else {
         this.addWeekOfMonthControl();
      }

      if(value) {
         (<TimeConditionModel>this.condition).weekOfMonth = null;
         (<TimeConditionModel>this.condition).dayOfWeek = null;
         (<TimeConditionModel>this.condition).dayOfMonth = this.defaultDayOfMonth;
      }
      else {
         (<TimeConditionModel>this.condition).dayOfMonth = null;
         (<TimeConditionModel>this.condition).weekOfMonth = this.defaultWeekOfMonth;
         (<TimeConditionModel>this.condition).dayOfWeek = this.defaultDayOfWeek;
      }
   }

   setLocalTimeZone(init: boolean = false) {
      if(!this.timeZoneOptions) {
         return;
      }

      const id = this.localTimeZoneId;
      let tz = this.timeZoneOptions.find(option => option.timeZoneId == id);

      if(!tz) {
         tz = this.timeZoneOptions[0];
         this.localTimeZoneId = this.timeZoneOptions[0].timeZoneId;
      }

      const date = new Date();
      const UTC = new Date(date.toLocaleString([], { timeZone: "UTC" }));
      const selectedTZ = new Date(date.toLocaleString([], { timeZone: this.localTimeZoneId }));
      this.timeZoneOffset = (UTC.getTime() - selectedTZ.getTime());

      if(tz == this.timeZoneOptions[0]) {
         this.localTimeZoneLabel = new Date().toTimeString().match(/\((.+)\)/)[1];
      }
      else {
         this.localTimeZoneLabel = tz.label;
      }

      if(!this.serverTimeZone && !init) {
         this.updateTimeZone();
      }

      if(!init) {
         this.updateTimeZone();

         if(!!this.form.get("startTime")) {
            if(this.selectedOption === TimeConditionType.AT ||
               this.selectedOption === TimeConditionType.EVERY_HOUR) {
               this.form.get("startTime").setValue(this.startTime);
            }
            else {
               this.form.get("startTime").setValue(this.startTimeData);
            }
         }

         if(!!this.form.get("endTime")) {
            this.form.get("endTime").setValue(this.endTime);
         }
      }
      else {
         this.timeZoneName = this.serverTimeZone ? this.timeZone :
            this.localTimeZoneLabel != null ? this.localTimeZoneLabel :
               new Date().toTimeString().match(/\((.+)\)/)[1];

         if(this.serverTimeZone) {
            this.form.get("timeZone").disable();
         }
         else {
            this.form.get("timeZone").enable();
         }
      }
   }

   initForm() {
      let startControl: UntypedFormControl;
      let timeZoneControl = new UntypedFormControl({ value: "" });

      if(this.selectedOption === TimeConditionType.AT ||
         this.selectedOption === TimeConditionType.EVERY_HOUR) {
         startControl = new UntypedFormControl(this.startTime, this.checkTimeNotNull);
      }
      else {
         startControl = new UntypedFormControl(this.startTimeData, this.checkStartTimeData);
      }

      // Daily Condition
      if(this.selectedOption == TimeConditionType.EVERY_DAY) {
         const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;

         this.form = new UntypedFormGroup({
            "startTime": startControl,
            "timeZone": timeZoneControl,
            "interval": new UntypedFormControl({ value: "", disabled: timeCondition.weekdayOnly })
         });

         if(!timeCondition.weekdayOnly) {
            this.addIntervalControl();
         }
         else {
            this.removeIntervalControl();
         }

         this.setLocalTimeZone(true);
      }
      // Weekly Condition
      else if(this.selectedOption == TimeConditionType.EVERY_WEEK) {
         const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;
         let weekdayControls: UntypedFormControl[] = this.getWeekdayControls();

         this.form = new UntypedFormGroup({
            "startTime": startControl,
            "timeZone": timeZoneControl,
            "interval": new UntypedFormControl(timeCondition.interval, [
               Validators.required,
               FormValidators.positiveNonZeroIntegerInRange,
            ]),
            "weekdays": new UntypedFormArray(
               weekdayControls,
               this.atLeastOneSelected)
         });

         this.setLocalTimeZone(true);
      }
      // Monthly Condition
      else if(this.selectedOption == TimeConditionType.EVERY_MONTH) {
         const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;
         let monthControls: UntypedFormControl[] = this.getMonthControls();

         this.form = new UntypedFormGroup({
            "startTime": startControl,
            "timeZone": timeZoneControl,
            "dayOfMonth": new UntypedFormControl({
               value: "",
               disabled: !timeCondition.monthlyDaySelected
            }),
            "weekOfMonth": new UntypedFormControl({
               value: "",
               disabled: timeCondition.monthlyDaySelected
            }),
            "dayOfWeek": new UntypedFormControl({
               value: "",
               disabled: timeCondition.monthlyDaySelected
            }),
            "months": new UntypedFormArray(
               monthControls,
               this.atLeastOneSelected)
         });

         if(timeCondition.monthlyDaySelected) {
            this.addDayOfMonthControl();
            let dayOfMonth = (<TimeConditionModel>this.condition).dayOfMonth;
            (<TimeConditionModel>this.condition).dayOfMonth =
               dayOfMonth ? dayOfMonth : this.defaultDayOfMonth;
         }
         else {
            this.addWeekOfMonthControl();
            let weekOfMonth = (<TimeConditionModel>this.condition).weekOfMonth;
            let dayOfWeek = (<TimeConditionModel>this.condition).dayOfWeek;
            (<TimeConditionModel>this.condition).weekOfMonth =
               weekOfMonth ? weekOfMonth : this.defaultWeekOfMonth;
            (<TimeConditionModel>this.condition).dayOfWeek =
               dayOfWeek ? dayOfWeek : this.defaultDayOfWeek;
         }

         this.setLocalTimeZone(true);
      }
      // Hourly Condition
      else if(this.selectedOption == TimeConditionType.EVERY_HOUR) {
         const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;
         let weekdayControls: UntypedFormControl[] = this.getWeekdayControls();

         this.form = new UntypedFormGroup({
            "startTime": startControl,
            "timeZone": timeZoneControl,
            "endTime": new UntypedFormControl(this.endTime, this.checkTimeNotNull),
            "interval": new UntypedFormControl(timeCondition.hourlyInterval, [
               Validators.required,
               FormValidators.positiveNonZeroInRange
            ]),
            "weekdays": new UntypedFormArray(
               weekdayControls,
               this.atLeastOneSelected)
         }, this.timeSmallerThan("startTime", "endTime"));

         this.setLocalTimeZone(true);
      }
      // Run Once Condition
      else if(this.selectedOption == TimeConditionType.AT) {
         const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;

         this.form = new UntypedFormGroup({
            "startTime": startControl,
            "timeZone": timeZoneControl,
            "date": new UntypedFormControl(timeCondition.date, Validators.required),
         });

         this.setLocalTimeZone(true);
      }
      // Chained Condition
      else if(this.selectedOption == "CompletionCondition") {
         const completionCondition = this.condition as CompletionConditionModel;
         let taskName = (<CompletionConditionModel>this.condition).taskName;

         if(this.model.allTasks) {
            if(!taskName || !this.model.allTasks.find(task =>
               task.name == (<CompletionConditionModel>this.condition).taskName)) {
               (<CompletionConditionModel>this.condition).taskName =
                  this.model.allTasks.length > 0 ? this.model.allTasks[0].name : null;
            }
         }
         else {
            (<CompletionConditionModel>this.condition).taskName = taskName;
         }

         this.form = new UntypedFormGroup({
            "task": new UntypedFormControl(completionCondition.taskName, Validators.required)
         });
      }
   }

   private addIntervalControl(): void {
      const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;

      this.form.controls["interval"].setValidators([
         Validators.required,
         FormValidators.positiveNonZeroIntegerInRange,
      ]);
      this.form.controls["interval"]
         .reset({ value: 1, disabled: timeCondition.weekdayOnly });
      this.form.controls["interval"].updateValueAndValidity();
   }

   private removeIntervalControl(): void {
      const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;

      this.form.controls["interval"].setValidators(null);
      this.form.controls["interval"]
         .reset({ value: "", disabled: timeCondition.weekdayOnly });
      this.form.controls["interval"].updateValueAndValidity();
   }

   private addDayOfMonthControl(): void {
      const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;

      this.form.controls["weekOfMonth"].setValidators(null);
      this.form.controls["weekOfMonth"]
         .reset({ value: "", disabled: timeCondition.monthlyDaySelected });
      this.form.controls["weekOfMonth"].updateValueAndValidity();

      this.form.controls["dayOfWeek"].setValidators(null);
      this.form.controls["dayOfWeek"]
         .reset({ value: "", disabled: timeCondition.monthlyDaySelected });
      this.form.controls["dayOfWeek"].updateValueAndValidity();

      this.form.controls["dayOfMonth"].setValidators(Validators.required);
      this.form.controls["dayOfMonth"]
         .reset({ value: "", disabled: !timeCondition.monthlyDaySelected });
      this.form.controls["dayOfMonth"].updateValueAndValidity();
   }

   private addWeekOfMonthControl(): void {
      const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;

      this.form.controls["dayOfMonth"].setValidators(null);
      this.form.controls["dayOfMonth"]
         .reset({ value: "", disabled: !timeCondition.monthlyDaySelected });
      this.form.controls["dayOfMonth"].updateValueAndValidity();

      this.form.controls["weekOfMonth"].setValidators(Validators.required);
      this.form.controls["weekOfMonth"]
         .reset({ value: "", disabled: timeCondition.monthlyDaySelected });
      this.form.controls["weekOfMonth"].updateValueAndValidity();

      this.form.controls["dayOfWeek"].setValidators(Validators.required);
      this.form.controls["dayOfWeek"]
         .reset({ value: "", disabled: timeCondition.monthlyDaySelected });
      this.form.controls["dayOfWeek"].updateValueAndValidity();
   }

   public updateDaysOfWeekStatus(): void {
      const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;

      const weekdayControl: UntypedFormArray = this.form.controls["weekdays"] as UntypedFormArray;

      for(let i = 0; i < weekdayControl.length; i++) {
         weekdayControl.at(i)
            .reset(this.isPresent(timeCondition.daysOfWeek, i + 1));
      }

      this.form.controls["weekdays"].updateValueAndValidity();
   }

   public updateMonthsOfYearStatus(): void {
      const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;

      const monthControl: UntypedFormArray = this.form.controls["months"] as UntypedFormArray;

      for(let i = 0; i < monthControl.length; i++) {
         monthControl.at(i)
            .reset(this.isPresent(timeCondition.monthsOfYear, i));
      }

      this.form.controls["months"].updateValueAndValidity();
   }

   private getWeekdayControls(): UntypedFormControl[] {
      const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;
      let weekdayControls: UntypedFormControl[] = [];

      for(let i = 0; i < this.weekdays.length; i++) {
         weekdayControls
            .push(new UntypedFormControl(this.isPresent(timeCondition.daysOfWeek, i + 1)));
      }

      return weekdayControls;
   }

   private getMonthControls(): UntypedFormControl[] {
      const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;
      let monthControls: UntypedFormControl[] = [];

      for(let i = 0; i < this.months.length; i++) {
         monthControls
            .push(new UntypedFormControl(this.isPresent(timeCondition.monthsOfYear, i)));
      }

      return monthControls;
   }

   public updateWeekdayOnly(event: boolean): void {
      const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;

      timeCondition.weekdayOnly = event;

      if(timeCondition.weekdayOnly) {
         this.removeIntervalControl();
      }
      else {
         this.addIntervalControl();
      }
   }

   private checkTimeNotNull(control: UntypedFormControl): any {
      const value = control.value;

      if(value == null || value.hour == null || value.minute == null ||
         value.hour === "" || value.minute === "" || value.hour < 0 || value.minute < 0) {
         return { timeIsNull: true };
      }

      return null;
   }

   private checkStartTimeData(control: UntypedFormControl): ValidationErrors | null {
      if(control) {
         if(!control.value || !control.value.valid) {
            return { "validStartTimeData": true };
         }
      }

      return null;
   }

   private timeSmallerThan(min: string, max: string): ValidatorFn {
      return (group: UntypedFormGroup): ValidationErrors => {
         let minimum: any = group.controls[min].value;
         let maximum: any = group.controls[max].value;

         if(minimum && maximum) {
            let minDate: Date =
               new Date(2000, 0, 1, minimum.hour, minimum.minute, minimum.second, 0);
            let maxDate: Date =
               new Date(2000, 0, 1, maximum.hour, maximum.minute, maximum.second, 0);

            return minDate >= maxDate ? { timeNotChronological: true } : null;
         }
         else {
            return null;
         }
      };
   }

   private atLeastOneSelected(array: UntypedFormArray): any {
      let valid = false;

      for(let i = 0; i < array.length; i++) {
         if(array.at(i).value) {
            valid = true;
            break;
         }
      }
      return valid ? null : { noCheckboxSelected: true };
   }

   public isPresent(array: number[], item: number): boolean {
      return array && array.indexOf(item) > -1;
   }

   public updateList(array: number[], item: number): void {
      if(!array) {
         return;
      }

      const index: number = array.indexOf(item);

      if(index == -1) {
         array.push(item);
      }
      else {
         array.splice(index, 1);
      }
   }

   public selectAll(array: any[], length: number, startIndex: number = 0) {
      for(let i = 0; i < length; i++) {
         array[i] = i + startIndex;
      }
   }

   private initConditions() {
      // clear the conditions first
      this.chainedCondition = null;
      this.dailyCondition = null;
      this.weeklyCondition = null;
      this.monthlyCondition = null;
      this.hourlyCondition = null;
      this.runOnceCondition = null;

      // set the current condition
      let optionType;

      if(!!(<TimeConditionModel>this.condition).type
         || (<TimeConditionModel>this.condition).type == 0) {
         optionType = (<TimeConditionModel>this.condition).type;
      }
      else {
         optionType = this.condition.conditionType;
      }

      this.saveConditionType(optionType);

      this.chainedCondition = this.chainedCondition || <CompletionConditionModel>{
         taskName: null,
         conditionType: "CompletionCondition"
      };

      this.dailyCondition = this.dailyCondition ||
         this.getDefaultTimeConditionModel(TimeConditionType.EVERY_DAY);
      this.weeklyCondition = this.weeklyCondition ||
         this.getDefaultTimeConditionModel(TimeConditionType.EVERY_WEEK);
      this.monthlyCondition = this.monthlyCondition ||
         this.getDefaultTimeConditionModel(TimeConditionType.EVERY_MONTH);
      this.hourlyCondition = this.hourlyCondition ||
         this.getDefaultTimeConditionModel(TimeConditionType.EVERY_HOUR);
      this.runOnceCondition = this.runOnceCondition ||
         this.getDefaultTimeConditionModel(TimeConditionType.AT);
   }

   private getDefaultTimeConditionModel(optionType: any): TimeConditionModel {
      return <TimeConditionModel>{
         hour: 1,
         minute: 30,
         second: 0,
         hourEnd: 2,
         minuteEnd: 30,
         secondEnd: 0,
         interval: optionType == this.TimeConditionType.EVERY_DAY ? 1 : null,
         weekdayOnly: false,
         type: optionType,
         conditionType: "TimeCondition",
         daysOfWeek: [],
         monthsOfYear: [],
         monthlyDaySelected: true,
         date: new Date().getTime(),
         timeZoneOffset: (<TimeConditionModel>this.condition).timeZoneOffset
      };
   }

   private saveConditionType(optionType: any): void {
      const condition: ScheduleConditionModel = Tool.clone(this.condition);

      if(optionType === "CompletionCondition") {
         this.chainedCondition = <CompletionConditionModel>condition;
      }
      else if(optionType === TimeConditionType.EVERY_DAY) {
         this.dailyCondition = <TimeConditionModel>condition;
      }
      else if(optionType === TimeConditionType.EVERY_WEEK) {
         this.weeklyCondition = <TimeConditionModel>condition;
      }
      else if(optionType === TimeConditionType.EVERY_MONTH) {
         this.monthlyCondition = <TimeConditionModel>condition;
      }
      else if(optionType === TimeConditionType.EVERY_HOUR) {
         this.hourlyCondition = <TimeConditionModel>condition;
      }
      else if(optionType === TimeConditionType.AT) {
         this.runOnceCondition = <TimeConditionModel>condition;
      }
   }

   /**
    * Get which timezone to display from local storage (local or server).
    */
   private getTimeZone(): void {
      const serverTZ: string = LocalStorage.getItem(TZ_STORAGE_KEY);
      this.serverTimeZone = serverTZ ? serverTZ == "true" : true;

      if(this.serverTimeZone && this.timeZoneOptions) {
         let timeZones = this.timeZoneOptions.slice(1);
         let timeZoneModel =
            timeZones.find(timeZone => timeZone?.timeZoneId == this.timeCondition.timeZone);

         if(timeZoneModel && timeZoneModel.label != this.timeZoneName) {
            this.serverTimeZone = false;
         }
      }

      this.timeZoneName = this.serverTimeZone ? this.timeZone :
         new Date().toTimeString().match(/\((.+)\)/)[1];
      this.localTimeZoneId = this.timeCondition.timeZone;
   }

   /**
    * Set which timezone to display from local storage (local or server).
    */
   private setServerTimeZone(): void {
      LocalStorage.setItem(TZ_STORAGE_KEY, this.serverTimeZone + "");
   }

   changeNonATConditionTimeZone(timeZone: string) {
      let oldTimeZone = this.localTimeZoneId;
      this.localTimeZoneId = timeZone;
      this.setLocalTimeZone(false);
      this.updateNonRunOnceStartTimeAndEndTime(oldTimeZone, timeZone);
   }

   private updateNonRunOnceStartTimeAndEndTime(oldTimeZone: string, newTimeZone: string) {
      if(oldTimeZone != newTimeZone && oldTimeZone && newTimeZone) {
         let startTimeDate = new Date((<TimeConditionModel>this.condition).date || 0);
         startTimeDate.setHours(this.startTime.hour);
         startTimeDate.setMinutes(this.startTime.minute);
         startTimeDate.setSeconds(this.startTime.second);
         let newDate = this.applyTimeZoneOffsetDifference(startTimeDate, oldTimeZone, newTimeZone);

         this.startTime = {
            hour: newDate.getHours(),
            minute: newDate.getMinutes(),
            second: newDate.getSeconds()
         };

         if(this.selectedOption !== TimeConditionType.AT &&
            this.selectedOption !== TimeConditionType.EVERY_HOUR) {
            this.startTimeData = {
               startTime: this.startTime,
               timeRange: this.startTimeData.timeRange,
               startTimeSelected: this.startTimeData.startTimeSelected,
               valid: this.startTimeData.valid
            };
         }

         if(!!this.form.get("startTime")) {
            if(this.selectedOption === TimeConditionType.AT ||
               this.selectedOption === TimeConditionType.EVERY_HOUR) {
               this.form.get("startTime").setValue(this.startTime);
            }
            else {
               this.form.get("startTime").setValue(this.startTimeData);
            }
         }

         if(this.selectedOption === TimeConditionType.EVERY_HOUR && this.endTime) {
            let endTimeDate = new Date((<TimeConditionModel>this.condition).date || 0);
            endTimeDate.setHours(this.endTime.hour);
            endTimeDate.setMinutes(this.endTime.minute);
            endTimeDate.setSeconds(this.endTime.second);
            newDate = this.applyTimeZoneOffsetDifference(endTimeDate, oldTimeZone, newTimeZone);

            this.endTime = {
               hour: newDate.getHours(),
               minute: newDate.getMinutes(),
               second: newDate.getSeconds()
            };

            if(!!this.form.get("endTime")) {
               this.form.get("endTime").setValue(this.endTime);
            }
         }
      }
   }

   applyTimeZoneOffsetDifference(oldDate: Date, oldTZ: string, newTZ: string): Date {
      let time = oldDate.getTime();

      let date = new Date();
      const oldTZOffset = new Date(date.toLocaleString([], { timeZone: oldTZ })).getTime();
      const newTZOffset = new Date(date.toLocaleString([], { timeZone: newTZ })).getTime();
      time += newTZOffset - oldTZOffset;

      return new Date(time);
   }

   updateTimeZone(): void {
      this.serverTimeZone = !this.serverTimeZone;

      if(this.serverTimeZone ||
         (this.startTimeData != null && !this.startTimeData.startTimeSelected)) {
         this.timeCondition.timeZone = null;
         this.form.get("timeZone").disable();
      }
      else {
         this.timeCondition.timeZone = this.localTimeZoneId;
         this.form.get("timeZone").enable();
      }

      this.timeZoneName = this.serverTimeZone ? this.timeZone :
         this.localTimeZoneLabel != null ? this.localTimeZoneLabel :
            new Date().toTimeString().match(/\((.+)\)/)[1];
      this.updateTimesAndDates(ChangeType.ToLocalTimeZone);
      this.setServerTimeZone();
   }

   changeShowServerTimeZone() {
      this.updateTimeZone();

      if(this.selectedOption !== TimeConditionType.AT) {
         let oldTimeZone = this.localTimeZoneId;
         let newTimeZone = this.localTimeZoneId;
         let serverTimeZoneId = this.timeZoneOptions.find(t =>
            this.getTimeZoneOffset(t.timeZoneId) == (<TimeConditionModel>this.condition).timeZoneOffset);

         if(this.serverTimeZone) {
            if(serverTimeZoneId) {
               newTimeZone = serverTimeZoneId.timeZoneId;
            }
         }
         else {
            if(serverTimeZoneId) {
               oldTimeZone = serverTimeZoneId.timeZoneId;
            }
         }

         this.updateNonRunOnceStartTimeAndEndTime(oldTimeZone, newTimeZone);
      }
      else if(this.serverTimeZone) {
         this.updateTimesAndDates(ChangeType.None);
      }
   }

   private getTimeZoneOffset(timeZoneId: string): number {
      let date = new Date();
      const UTC = new Date(date.toLocaleString([], { timeZone: "UTC" }));
      const timezone = new Date(date.toLocaleString([], { timeZone: timeZoneId }));

      return timezone.getTime() - UTC.getTime();
   }
}
