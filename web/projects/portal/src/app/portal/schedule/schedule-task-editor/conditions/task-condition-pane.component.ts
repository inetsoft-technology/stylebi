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
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { CompletionConditionModel } from "../../../../../../../shared/schedule/model/completion-condition-model";
import { ScheduleConditionModel } from "../../../../../../../shared/schedule/model/schedule-condition-model";
import { ScheduleTaskDialogModel } from "../../../../../../../shared/schedule/model/schedule-task-dialog-model";
import { TaskConditionPaneModel } from "../../../../../../../shared/schedule/model/task-condition-pane-model";
import {
   TimeConditionModel,
   TimeConditionType,
   TimeRange
} from "../../../../../../../shared/schedule/model/time-condition-model";
import { TimeZoneModel } from "../../../../../../../shared/schedule/model/time-zone-model";
import { TimeZoneService } from "../../../../../../../shared/schedule/time-zone.service";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../../shared/util/tool";
import { ComponentTool } from "../../../../common/util/component-tool";
import { LocalStorage } from "../../../../common/util/local-storage.util";
import {
   getStoredCondition,
   storeCondition
} from "../../../../common/util/schedule-condition.util";
import { StartTimeData } from "../../../../widget/schedule/start-time-data";

const TASK_URI = "../api/portal/schedule/task/condition";
const TZ_STORAGE_KEY: string = "inetsoft_conditionServerTimeZone";

dayjs.extend(utc);

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
   @Output() showMessage: EventEmitter<string> = new EventEmitter<string>();

   @Input()
   get model(): TaskConditionPaneModel {
      return this._model;
   }

   set model(value: TaskConditionPaneModel) {
      this._model = value;

      if(!!value) {
         this.serverTimeZoneOffset = value.timeZoneOffset;
         this.serverTimeZoneId = value.serverTimeZoneId;
      }

      if(!value || !value.conditions || value.conditions.length == 0) {
         this.conditionIndex = -1;
      }
   }

   allOptions = [
      { label: "_#(js:Daily)", value: TimeConditionType.EVERY_DAY },
      { label: "_#(js:Weekly)", value: TimeConditionType.EVERY_WEEK },
      { label: "_#(js:Monthly)", value: TimeConditionType.EVERY_MONTH },
      { label: "_#(js:Hourly)", value: TimeConditionType.EVERY_HOUR },
      { label: "_#(js:Custom)", value: "UserCondition" },
      { label: "_#(js:Run Once)", value: TimeConditionType.AT },
      { label: "_#(js:Chained)",  value: "CompletionCondition" }

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

   private _model: TaskConditionPaneModel;
   private conditionIndex: number = 0;
   public TimeConditionType = TimeConditionType;

   selectedOption: any = this.allOptions[0].value;
   selectedConditions: number[] = [];

   date: NgbDateStruct;
   private _startTime: NgbTimeStruct;
   private startTimeData: StartTimeData;
   endTime: NgbTimeStruct;

   timeZoneName: string;
   serverTimeZone: boolean = false;
   private serverTimeZoneOffset = 0;
   private serverTimeZoneId: string;
   localTimeZoneOffset = 0;
   localTimeZoneId: string;
   localTimeZoneLabel: string;
   isSaving:boolean = false;

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
               private modalService: NgbModal,
               private timeZoneService: TimeZoneService)
   {
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
         conditionsLength > 0)
      {
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

   get formStartTimeData(): StartTimeData {
      return this.startTimeData;
   }

   set formStartTimeData(value: StartTimeData) {
      this.onStartTimeDataChanged(value);
   }

   get formStartTime(): NgbTimeStruct {
      return this.startTime;
   }

   set formStartTime(value: NgbTimeStruct) {
      this.userSetStartTime(value);
   }

   get formEndTime(): NgbTimeStruct {
      return this.endTime;
   }

   set formEndTime(value: NgbTimeStruct) {
      this.setEndTime(value);
      this.updateEndTime();
   }

   get formDate(): NgbDateStruct {
      return this.date;
   }

   set formDate(value: NgbDateStruct) {
      this.dateChange(value);
   }

   ngOnInit(): void {
      if(!this.date) {
         const date = new Date();
         this.date = <NgbDateStruct> {
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

      this.initConditions();
      this.initTimeZone();
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

   private updateStartTime(): void {
      if((<TimeConditionModel>this.condition).type != TimeConditionType.AT) {
         this.startTime = {
            hour: (<TimeConditionModel> this.condition).hour,
            minute: (<TimeConditionModel> this.condition).minute,
            second: (<TimeConditionModel> this.condition).second
         };
      }
      else {
         const time: number = (<TimeConditionModel> this.condition).date;
         const date = dayjs(time).utcOffset((this.currentTimeZoneOffset || 0) / 60000);

         this.startTime = {
            hour: date.hour(),
            minute: date.minute(),
            second: date.second()
         };
      }
   }

   private updateStartTimeData(): void {
      const range = ((<TimeConditionModel> this.condition).timeRange);
      const selected = !range && this.startTimeEnabled;
      const defaultTimeRange = this.timeRanges.length ? this.timeRanges[0] : null;
      this.startTimeData = {
         startTime: this.startTime,
         timeRange: range || defaultTimeRange,
         startTimeSelected: selected,
         valid: selected && !!this.startTime || !!(range || defaultTimeRange)
      };

      if(this.selectedOption !== TimeConditionType.AT && this.selectedOption !== TimeConditionType.EVERY_HOUR) {
         this.form?.controls["startTime"]?.setValue(this.formStartTimeData);
      }
   }

   private userSetStartTime(time: NgbTimeStruct): void {
      this.setStartTime(time);
      this.taskDefaultTime = !!time;
   }

   private onStartTimeDataChanged(data: StartTimeData): void {
      if(data) {
         if(data.startTimeSelected) {
            this.setStartTime(data.startTime);
            (<TimeConditionModel> this.condition).timeRange = null;

            if(!this.serverTimeZone) {
               this.form.get("timeZone").enable();
            }
         }
         else {
            this.setStartTime(data.startTime);
            (<TimeConditionModel> this.condition).timeRange = data.timeRange;
            this.form.get("timeZone").disable();
         }
      }
      else {
         this.setStartTime(null);
         (<TimeConditionModel> this.condition).timeRange = null;
      }

      if(!Tool.isEquals(this.startTimeData, data)) {
         this.startTimeData = data;
         this.taskDefaultTime = !!data && data.valid;
      }
   }

   private setStartTime(time: NgbTimeStruct): void {
      if(time && (<TimeConditionModel> this.condition).type != TimeConditionType.AT) {
         (<TimeConditionModel> this.condition).hour = time.hour;
         (<TimeConditionModel> this.condition).minute = time.minute;
         (<TimeConditionModel> this.condition).second = 0;
      }
      else if((<TimeConditionModel> this.condition).type == TimeConditionType.AT) {
         const dateTime: number = (<TimeConditionModel>this.condition).date;
         const date = dayjs(dateTime).utcOffset((this.currentTimeZoneOffset || 0) / 60000);
         this.dateChange({
            year: date.year(),
            month: date.month() + 1,
            day: date.date()
         }, time);
      }

      this.startTime = time;
   }

   private updateDate(): void {
      const time: number = (<TimeConditionModel>this.condition).date;

      if(time) {
         const date = dayjs(time).utcOffset((this.currentTimeZoneOffset || 0) / 60000);
         this.date = {
            year: date.year(),
            month: date.month() + 1,
            day: date.date()
         };
      }
      else {
         this.date = null;
      }
   }

   private dateChange(date: NgbDateStruct, time?: NgbTimeStruct): void {
      // Ngb month from the date picker starts with 1 whereas dayjs month starts with 0
      // Change it to 0-based month since it will be used in dayjs
      let month = date.month - 1;
      let cond: TimeConditionModel = <TimeConditionModel> this.condition;

      if((<TimeConditionModel> this.condition).type != TimeConditionType.AT) {
         const compiled = dayjs().utcOffset((this.currentTimeZoneOffset || 0) / 60000)
            .year(date.year)
            .month(month)
            .date(date.day)
            .hour(cond.hour)
            .minute(cond.minute)
            .second(cond.second)
            .millisecond(0);
         cond.date = compiled.valueOf();
      }
      else {
         if(time) {
            const compiled = dayjs()
               .utcOffset((this.currentTimeZoneOffset || 0) / 60000)
               .year(date.year)
               .month(month)
               .date(date.day)
               .hour(time.hour)
               .minute(time.minute)
               .second(0)
               .millisecond(0);
            cond.date = compiled.valueOf();
         }
         else if(cond.hour != undefined && !Number.isNaN(cond.hour) && cond.hour != -1 &&
            cond.minute != undefined && !Number.isNaN(cond.minute) && cond.minute != -1 &&
            cond.second != undefined && !Number.isNaN(cond.second) && cond.second != -1)
         {
            const compiled = dayjs()
               .utcOffset((this.currentTimeZoneOffset || 0) / 60000)
               .year(date.year)
               .month(month)
               .date(date.day)
               .hour(cond.hour)
               .minute(cond.minute)
               .second(cond.second)
               .millisecond(0);
            cond.date = compiled.valueOf();
         }
         else {
            const compiled = dayjs(cond.date)
               .utcOffset((this.currentTimeZoneOffset || 0) / 60000)
               .year(date.year)
               .month(month)
               .date(date.day)
               .millisecond(0);
            cond.date = compiled.valueOf();
         }
      }
      cond.hour = -1;
      cond.minute = -1;
      cond.second = -1;
   }

   private updateEndTime(): void {
      this.endTime = {
         hour: (<TimeConditionModel> this.condition).hourEnd,
         minute: (<TimeConditionModel> this.condition).minuteEnd,
         second: (<TimeConditionModel> this.condition).secondEnd
      } as NgbTimeStruct;
   }

   private setEndTime(time: NgbTimeStruct): void {
      if(time) {
         (<TimeConditionModel> this.condition).hourEnd = time.hour;
         (<TimeConditionModel> this.condition).minuteEnd = time.minute;
         (<TimeConditionModel> this.condition).secondEnd = time.second;
      }
   }

   private setSelectedOption(): void {
      let optionType;

      if(!!(<TimeConditionModel> this.condition).type ||
         (<TimeConditionModel> this.condition).type == 0)
      {
         optionType = (<TimeConditionModel> this.condition).type;
      }
      else {
         optionType = this.condition.conditionType;
      }

      this.selectedOption = optionType;
      this.initForm();
   }

   public changeConditionType(option: any): void {
      if(this.isSaving) {
         this.showMessage.emit("_#(js:portal.schedule.isSaving.warning)");
         return;
      }

      this.saveConditionType(this.selectedOption);
      const optionType = option.value;

      if(optionType != this.condition.conditionType &&
         optionType != (<TimeConditionModel> this.condition).type)
      {
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
         this.initForm();
         this.updateTimesAndDates();
      }
   }

   public changeView(multi: boolean): void {
      this.listView = multi;
   }

   public save(ok: boolean): void {
      this.isSaving = true;
      this.saveTask().then(() => {
         this.isSaving = false;
         storeCondition(this.condition);
         this.updateTimesAndDates();
         this.form.markAsPristine();
         this.updateTaskName.emit(this.taskName);

         if(ok && this.model.conditions.length > 1 && !this.listView) {
            this.changeView(true);
         }
      }).finally(() => {
         this.isSaving = false;
      });
   }

   public addCondition(): void {
      const storedCondition = getStoredCondition();
      this.model.conditions.push(storedCondition ? storedCondition : this.condition);
      this.conditionIndex = this.model.conditions.length - 1;
      this.updateValues();
      this.listView = false;
   }

   public deleteCondition(): void {
      const message: string = "_#(em.scheduler.conditions.removeNote)";
      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", message).then(
         (result: string) => {
            if(result === "ok") {
               const params = new HttpParams().set("name", Tool.byteEncode(this.oldTaskName));
               const conditions: number[] = Tool.clone(this.selectedConditions);
               conditions.sort();
               conditions.reverse();

               this.http.post(TASK_URI + "/delete", conditions, { params: params })
                  .subscribe(() => {
                        for(let index of conditions) {
                           this._model.conditions.splice(index, 1);
                        }

                        this.selectedConditions = [];
                        this.conditionIndex = this._model.conditions.length - 1;
                     },
                     () => {
                        // Error
                     });
            }
         });
   }

   public editCondition(): void {
      if(this.selectedConditions.length == 0) {
         return;
      }

      this.localTimeZoneId = null;
      this.conditionIndex = this.selectedConditions[0];
      this.condition = this.model.conditions[this.conditionIndex];
      this.initTimeZone(true);
      this.updateValues();
      this.listView = false;
      this.setSelectedOption();
   }

   private updateValues(): void {
      this.updateTimesAndDates();
      this.listView = this.model.conditions && this.model.conditions.length > 1;
      this.setSelectedOption();
      this.initConditions();
   }

   private updateTimesAndDates(): void {
      if(this.isTimeCondition(this.condition)) {
         this.updateDate();
         this.updateStartTime();
         this.updateStartTimeData();
         this.updateEndTime();
      }
   }

   public changeMonthRadioOption(value: boolean): void {
      (<TimeConditionModel> this.condition).monthlyDaySelected = value;

      if(value) {
         this.addDayOfMonthControl();
      }
      else {
         this.addWeekOfMonthControl();
      }

      if(value) {
         (<TimeConditionModel> this.condition).weekOfMonth = null;
         (<TimeConditionModel> this.condition).dayOfWeek = null;
         (<TimeConditionModel> this.condition).dayOfMonth = this.defaultDayOfMonth;
      }
      else {
         (<TimeConditionModel> this.condition).dayOfMonth = null;
         (<TimeConditionModel> this.condition).weekOfMonth = this.defaultWeekOfMonth;
         (<TimeConditionModel> this.condition).dayOfWeek = this.defaultDayOfWeek;
      }
   }

   setLocalTimeZone(newLocalTimeZoneId: any) {
      const oldLocalTimeZoneId = this.localTimeZoneId;

      if(!this.timeZoneOptions) {
         return;
      }

      this.localTimeZoneId = newLocalTimeZoneId;
      const id = this.localTimeZoneId;
      let tz = this.timeZoneOptions.find(option => option.timeZoneId == id);

      if(!tz) {
         tz = this.timeZoneOptions[0];
         this.localTimeZoneId = this.timeZoneOptions[0].timeZoneId;
      }

      if(tz == this.timeZoneOptions[0]) {
         this.localTimeZoneLabel = new Date().toTimeString().match(/\((.+)\)/)[1];
      }
      else {
         this.localTimeZoneLabel = tz.label;
      }

      this.updateTimeZone();

      if(!this.serverTimeZone && oldLocalTimeZoneId != null) {
         this.convertToTimeZone(this.timeZoneService.calculateTimezoneOffset(oldLocalTimeZoneId),
            this.localTimeZoneOffset);
      }
   }

   initForm() {
      let startControl: UntypedFormControl;
      let timeZoneControl = new UntypedFormControl({value: this.localTimeZoneId || ""});

      if(this.selectedOption === TimeConditionType.AT ||
         this.selectedOption === TimeConditionType.EVERY_HOUR)
      {
         startControl = new UntypedFormControl(this.formStartTime, this.checkTimeNotNull);
      }
      else {
         startControl = new UntypedFormControl(this.formStartTimeData, this.checkStartTimeData);
      }

      // Daily Condition
      if(this.selectedOption == TimeConditionType.EVERY_DAY) {
         const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;

         this.form = new UntypedFormGroup({
            "startTime": startControl,
            "timeZone": timeZoneControl,
            "interval": new UntypedFormControl({value: "", disabled: timeCondition.weekdayOnly})
         });

         if(!timeCondition.weekdayOnly) {
            this.addIntervalControl();
         }
         else {
            this.removeIntervalControl();
         }
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
            let dayOfMonth = (<TimeConditionModel> this.condition).dayOfMonth;
            (<TimeConditionModel> this.condition).dayOfMonth =
               dayOfMonth ? dayOfMonth : this.defaultDayOfMonth;
         }
         else {
            this.addWeekOfMonthControl();
            let weekOfMonth = (<TimeConditionModel> this.condition).weekOfMonth;
            let dayOfWeek = (<TimeConditionModel> this.condition).dayOfWeek;
            (<TimeConditionModel> this.condition).weekOfMonth =
               weekOfMonth ? weekOfMonth : this.defaultWeekOfMonth;
            (<TimeConditionModel> this.condition).dayOfWeek =
               dayOfWeek ? dayOfWeek : this.defaultDayOfWeek;
         }
      }
      // Hourly Condition
      else if(this.selectedOption == TimeConditionType.EVERY_HOUR) {
         const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;
         let weekdayControls: UntypedFormControl[] = this.getWeekdayControls();

         this.form = new UntypedFormGroup({
            "startTime": startControl,
            "timeZone": timeZoneControl,
            "endTime": new UntypedFormControl(this.formEndTime, this.checkTimeNotNull),
            "interval": new UntypedFormControl(timeCondition.hourlyInterval, [
               Validators.required,
               FormValidators.positiveNonZeroInRange
            ]),
            "weekdays": new UntypedFormArray(
               weekdayControls,
               this.atLeastOneSelected)
         }, this.timeSmallerThan("startTime", "endTime"));
      }
      // Run Once Condition
      else if(this.selectedOption == TimeConditionType.AT) {
         const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;

         this.form = new UntypedFormGroup({
            "startTime": startControl,
            "timeZone": timeZoneControl,
            "date": new UntypedFormControl(timeCondition.date, Validators.required),
         });
      }
      // Chained Condition
      else if(this.selectedOption == "CompletionCondition") {
         const completionCondition = this.condition as CompletionConditionModel;
         let taskName = (<CompletionConditionModel> this.condition).taskName;

         if(this.model.allTasks) {
            if(!taskName || !this.model.allTasks.find(task =>
               task.name == (<CompletionConditionModel> this.condition).taskName))
            {
               (<CompletionConditionModel> this.condition).taskName =
                  this.model.allTasks.length > 0 ? this.model.allTasks[0].name : null;
            }
         }
         else {
            (<CompletionConditionModel> this.condition).taskName = taskName;
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
         .reset({value: 1, disabled: timeCondition.weekdayOnly});
      this.form.controls["interval"].updateValueAndValidity();
   }

   private removeIntervalControl(): void {
      const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;

      this.form.controls["interval"].setValidators(null);
      this.form.controls["interval"]
         .reset({value: "", disabled: timeCondition.weekdayOnly});
      this.form.controls["interval"].updateValueAndValidity();
   }

   private addDayOfMonthControl(): void {
      const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;

      this.form.controls["weekOfMonth"].setValidators(null);
      this.form.controls["weekOfMonth"]
         .reset({value: "", disabled: timeCondition.monthlyDaySelected});
      this.form.controls["weekOfMonth"].updateValueAndValidity();

      this.form.controls["dayOfWeek"].setValidators(null);
      this.form.controls["dayOfWeek"]
         .reset({value: "", disabled: timeCondition.monthlyDaySelected});
      this.form.controls["dayOfWeek"].updateValueAndValidity();

      this.form.controls["dayOfMonth"].setValidators(Validators.required);
      this.form.controls["dayOfMonth"]
         .reset({value: "", disabled: !timeCondition.monthlyDaySelected});
      this.form.controls["dayOfMonth"].updateValueAndValidity();
   }

   private addWeekOfMonthControl(): void {
      const timeCondition: TimeConditionModel = this.condition as TimeConditionModel;

      this.form.controls["dayOfMonth"].setValidators(null);
      this.form.controls["dayOfMonth"]
         .reset({value: "", disabled: !timeCondition.monthlyDaySelected});
      this.form.controls["dayOfMonth"].updateValueAndValidity();

      this.form.controls["weekOfMonth"].setValidators(Validators.required);
      this.form.controls["weekOfMonth"]
         .reset({value: "", disabled: timeCondition.monthlyDaySelected});
      this.form.controls["weekOfMonth"].updateValueAndValidity();

      this.form.controls["dayOfWeek"].setValidators(Validators.required);
      this.form.controls["dayOfWeek"]
         .reset({value: "", disabled: timeCondition.monthlyDaySelected});
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
         value.hour === "" || value.minute === "" || value.hour < 0 || value.minute < 0)
      {
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
         || (<TimeConditionModel>this.condition).type == 0)
      {
         optionType = (<TimeConditionModel>this.condition).type;
      }
      else {
         optionType = this.condition.conditionType;
      }

      this.saveConditionType(optionType);

      this.chainedCondition = this.chainedCondition || <CompletionConditionModel> {
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
      return <TimeConditionModel> {
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
         timeZoneOffset: (<TimeConditionModel> this.condition).timeZoneOffset || this.serverTimeZoneOffset
      };
   }

   private saveConditionType(optionType: any): void {
      const condition: ScheduleConditionModel = Tool.clone(this.condition);

      if(optionType === "CompletionCondition") {
         this.chainedCondition = <CompletionConditionModel> condition;
      }
      else if(optionType === TimeConditionType.EVERY_DAY) {
         this.dailyCondition = <TimeConditionModel> condition;
      }
      else if(optionType === TimeConditionType.EVERY_WEEK) {
         this.weeklyCondition = <TimeConditionModel> condition;
      }
      else if(optionType === TimeConditionType.EVERY_MONTH) {
         this.monthlyCondition = <TimeConditionModel> condition;
      }
      else if(optionType === TimeConditionType.EVERY_HOUR) {
         this.hourlyCondition = <TimeConditionModel> condition;
      }
      else if(optionType === TimeConditionType.AT) {
         this.runOnceCondition = <TimeConditionModel> condition;
      }
   }

   /**
    * Init which timezone to display from local storage (local or server).
    */
   private initTimeZone(edit?: boolean): void {
      this.setLocalTimeZone(this.timeCondition.timeZone);
      this.localTimeZoneOffset = this.timeZoneService.calculateTimezoneOffset(this.localTimeZoneId);
      const serverTZ: string = LocalStorage.getItem(TZ_STORAGE_KEY);
      this.changeServerTimeZone(serverTZ ? serverTZ == "true" : false);

      if(!edit || !this.timeCondition?.timeZone || this.serverTimeZone) {
         this.timeZoneName = this.serverTimeZone ? this.timeZone :
            new Date().toTimeString().match(/\((.+)\)/)[1];
      }
   }

   get currentTimeZoneOffset(): number {
      return this.serverTimeZone ? this.serverTimeZoneOffset : this.localTimeZoneOffset;
   }

   /**
    * Change show server time setting.
    */
   changeServerTimeZone(serverTimeZone: boolean): void {
      const changed = this.serverTimeZone != serverTimeZone;
      this.serverTimeZone = serverTimeZone;

      if(changed) {
         if(this.serverTimeZone) {
            this.convertToTimeZone(this.localTimeZoneOffset, this.serverTimeZoneOffset);
         }
         else {
            this.convertToTimeZone(this.serverTimeZoneOffset, this.localTimeZoneOffset);
         }

         this.updateTimeZone();
         LocalStorage.setItem(TZ_STORAGE_KEY, this.serverTimeZone + "");
      }
   }

   /**
    * Updates relevant fields after time zone has changed
    */
   updateTimeZone(): void {
      if(this.serverTimeZone ||
         (this.startTimeData != null && !this.startTimeData.startTimeSelected))
      {
         if(this.isTimeCondition(this.condition)) {
            this.timeCondition.timeZone = this.serverTimeZoneId;
         }

         this.form?.get("timeZone")?.disable();
      }
      else {
         if(this.isTimeCondition(this.condition)) {
            this.timeCondition.timeZone = this.localTimeZoneId;
         }

         this.form?.get("timeZone")?.enable();
      }

      this.timeZoneName = this.serverTimeZone ? this.timeZone :
         this.localTimeZoneLabel != null ? this.localTimeZoneLabel :
            new Date().toTimeString().match(/\((.+)\)/)[1];
      this.localTimeZoneOffset = this.timeZoneService.calculateTimezoneOffset(this.localTimeZoneId);
   }

   /**
    * Converts all the dates and times to the new time zone
    */
   private convertToTimeZone(oldTzOffset: number, newTzOffset: number) {
      // if same offset then no need to convert
      if(oldTzOffset == newTzOffset) {
         return;
      }

      // update all the conditions when the time zone changes
      for(let condition of this.timeConditions) {
         this.convertTimeCondition(condition, oldTzOffset, newTzOffset);
      }

      // propagate the changes from the condition object to the ui
      this.updateTimesAndDates();
   }

   private convertTimeCondition(condition: TimeConditionModel, oldTzOffset: number, newTzOffset: number) {
      // start time
      const startTime = this.convertTime({
         hour: condition.hour, minute: condition.minute,
         second: condition.second
      }, oldTzOffset, newTzOffset);
      condition.hour = startTime.hour;
      condition.minute = startTime.minute;
      condition.second = startTime.second;

      // end time
      const endTime = this.convertTime({
         hour: condition.hourEnd, minute: condition.minuteEnd,
         second: condition.secondEnd
      }, oldTzOffset, newTzOffset);
      condition.hourEnd = endTime.hour;
      condition.minuteEnd = endTime.minute;
      condition.secondEnd = endTime.second;
   }

   get timeConditions(): Set<TimeConditionModel> {
      const set = new Set([this.dailyCondition, this.weeklyCondition,
         this.monthlyCondition, this.hourlyCondition, this.runOnceCondition]);

      if(this.isTimeCondition(this.condition)) {
         set.add(this.condition as TimeConditionModel);
      }

      return set;
   }

   convertTime(value: NgbTimeStruct, oldTzOffset: number, newTzOffset: number): NgbTimeStruct {
      const oldTime = dayjs()
         .utcOffset(oldTzOffset / 60000)
         .hour(value.hour)
         .minute(value.minute)
         .second(value.second)
         .millisecond(0);
      const newTime = dayjs(oldTime.valueOf())
         .utcOffset((newTzOffset || 0) / 60000);
      return {
         hour: newTime.hour(),
         minute: newTime.minute(),
         second: newTime.second()
      } as NgbTimeStruct;
   }

   private isTimeCondition(condition: ScheduleConditionModel): boolean {
      return condition && condition.conditionType == "TimeCondition";
   }
}
