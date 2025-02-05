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
   Input, OnDestroy,
   OnInit,
   Output,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal, NgbModalOptions, NgbTimeStruct } from "@ng-bootstrap/ng-bootstrap";
import { Observable, Subscription } from "rxjs";
import { debounceTime, map } from "rxjs/operators";
import { ScheduleConditionModel } from "../../../../../shared/schedule/model/schedule-condition-model";
import {
   TimeConditionModel,
   TimeConditionType
} from "../../../../../shared/schedule/model/time-condition-model";
import { TimeZoneService } from "../../../../../shared/schedule/time-zone.service";
import { Tool } from "../../../../../shared/util/tool";
import { ComponentTool } from "../../common/util/component-tool";
import { LocalStorage } from "../../common/util/local-storage.util";
import { getStoredCondition, storeCondition } from "../../common/util/schedule-condition.util";
import { EmailValidationResponse } from "../../vsobjects/dialog/email/email-validation-response";
import { FileFormatType } from "../../vsobjects/model/file-format-type";
import { SimpleScheduleDialogModel } from "../../vsobjects/model/schedule/simple-schedule-dialog-model";
import { EmailAddrDialogModel } from "../email-dialog/email-addr-dialog-model";
import { StartTimeData } from "./start-time-data";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { EmailDialogData } from "../email-dialog/email-addr-dialog.component";
import { ViewsheetActionModel } from "../../vsobjects/model/schedule/viewsheet-action-model";
import { FeatureFlagValue } from "../../../../../shared/feature-flags/feature-flags.service";

const HISTORY_LIMIT: number = 100;
const CHECK_EMAIL_VALID_URI: string = "../api/vs/check-email-valid";

@Component({
   selector: "simple-schedule-dialog",
   templateUrl: "simple-schedule-dialog.component.html",
})
export class SimpleScheduleDialog implements OnInit, OnDestroy {
   @Input() model: SimpleScheduleDialogModel;
   @Input() exportTypes: {label: string, value: string}[] = [];
   @Input() isReport: boolean = false;
   @Input() securityEnabled: boolean = false;
   @Output() onCommit = new EventEmitter<SimpleScheduleDialogModel>();
   @Output() onCancel = new EventEmitter<string>();
   @ViewChild("emailAddrDialogModel") emailAddrDialogModel: TemplateRef<any>;
   daysOfMonth: string[] = [
      "_#(js:1st)", "_#(js:2nd)", "_#(js:3rd)", "_#(js:4th)", "_#(js:5th)",
      "_#(js:6th)", "_#(js:7th)", "_#(js:8th)", "_#(js:9th)", "_#(js:10th)",
      "_#(js:11th)", "_#(js:12th)", "_#(js:13th)", "_#(js:14th)", "_#(js:15th)",
      "_#(js:16th)", "_#(js:17th)", "_#(js:18th)", "_#(js:19th)", "_#(js:20th)",
      "_#(js:21st)", "_#(js:22nd)", "_#(js:23rd)", "_#(js:24th)", "_#(js:25th)",
      "_#(js:26th)", "_#(js:27th)", "_#(js:28th)", "_#(js:29th)",
      "_#(js:Day before last day)", "_#(js:Last day of month)"];
   daysOfMonthNum: number[] = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
      18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
      TimeConditionType.LAST_DAY_OF_MONTH - 1, TimeConditionType.LAST_DAY_OF_MONTH];
   weeksOfMonth: string[] = ["_#(js:1st)", "_#(js:2nd)", "_#(js:3rd)", "_#(js:4th)",
                             "_#(js:5th)"];
   daysOfWeek: string[] = ["_#(js:Sunday)", "_#(js:Monday)", "_#(js:Tuesday)", "_#(js:Wednesday)",
                           "_#(js:Thursday)", "_#(js:Friday)", "_#(js:Saturday)"];
   readonly FeatureFlagValue = FeatureFlagValue;
   FileFormatType = FileFormatType;
   TimeConditionType = TimeConditionType;
   emailModel: EmailAddrDialogModel;
   excelVisible: boolean = true;
   powerpointVisible: boolean = true;
   pdfVisible: boolean = true;
   pngVisible: boolean = true;
   csvVisible: boolean = true;
   htmlVisible: boolean = true;
   formatStr: any;
   form: UntypedFormGroup;
   startTimeData: StartTimeData;
   subscriptions: Subscription = new Subscription();
   emailHistory: string[];
   editingEmails: string;
   timeZoneId: string = null;
   timeZoneLabel: string = null;

   constructor(private http: HttpClient, private modalService: NgbModal,
               private timeZoneService: TimeZoneService)
   {
   }

   ngOnInit(): void {
      this.model.timeZoneOptions = this.timeZoneService.updateTimeZoneOptions(
         this.model.timeZoneOptions, [this.model.timeConditionModel]);
      this.initForm();

      this.emailHistory = Tool.getHistoryEmails(true);

      this.excelVisible = this.exportTypes.some(p => p.value.toLowerCase() == "excel");
      this.powerpointVisible = this.exportTypes.some(p => p.value.toLowerCase() == "powerpoint");
      this.pdfVisible = this.exportTypes.some(p => p.value.toLowerCase() == "pdf");
      this.htmlVisible = this.exportTypes.some(p => p.value.toLowerCase() == "html");
      this.pngVisible = this.exportTypes.some(p => p.value.toLowerCase() == "png");
      this.csvVisible = this.exportTypes.some(p => p.value.toLowerCase() == "csv");
      const formatType = this.model.actionModel.emailInfoModel.formatType;
      const formatVisible: boolean =
         formatType == this.FileFormatType.EXPORT_TYPE_EXCEL ? this.excelVisible :
         formatType == this.FileFormatType.EXPORT_TYPE_POWERPOINT ? this.powerpointVisible :
         formatType == this.FileFormatType.EXPORT_TYPE_PNG ? this.pngVisible :
         formatType == this.FileFormatType.EXPORT_TYPE_PDF ? this.pdfVisible :
         formatType == this.FileFormatType.EXPORT_TYPE_HTML ? this.htmlVisible : false;
      this.model.actionModel.emailInfoModel.formatType = formatVisible ? formatType :
         this.excelVisible ? this.FileFormatType.EXPORT_TYPE_EXCEL :
         this.powerpointVisible ? this.FileFormatType.EXPORT_TYPE_POWERPOINT :
         this.pngVisible ? this.FileFormatType.EXPORT_TYPE_PNG :
         this.pdfVisible ? this.FileFormatType.EXPORT_TYPE_PDF :
         this.htmlVisible ? this.FileFormatType.EXPORT_TYPE_HTML : formatType;

      const condition: TimeConditionModel = this.getHistoryModel();

      if(condition) {
         this.model.timeConditionModel = condition;

         const startTime = {
            hour: condition.hour >= 0 ? condition.hour : 1,
            minute: condition.minute >= 0 ? condition.minute : 30,
            second: 0,
         };
         const range = condition.timeRange;
         const selected = !range && this.model.startTimeEnabled;
         this.startTimeData = {
            startTime: startTime,
            timeRange: range,
            startTimeSelected: selected,
            valid: selected || !!range
         };

         //If condition is hourly and every day, change condition type to daily
         if(condition.type === TimeConditionType.EVERY_HOUR && condition.daysOfWeek.length === 7) {
            this.model.timeConditionModel.type = TimeConditionType.EVERY_DAY;
            this.model.timeConditionModel.interval = 1;
            this.model.timeConditionModel.weekdayOnly = false;
         }
         // Default to daily condition type.
         else if(condition.type != TimeConditionType.EVERY_DAY &&
            condition.type != TimeConditionType.EVERY_WEEK &&
            condition.type != TimeConditionType.EVERY_MONTH)
         {
            this.model.timeConditionModel.type = TimeConditionType.EVERY_DAY;
            this.model.timeConditionModel.interval = 1;
            this.model.timeConditionModel.weekdayOnly = false;
         }

         this.fixMonthCondition(condition);
         this.model.timeConditionModel.monthsOfYear = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11];
         this.setTimeZone();
      }
      else {
         // Set defaults
         const timeCondition: TimeConditionModel = this.model.timeConditionModel;
         timeCondition.weekdayOnly = false;
         timeCondition.interval = 1;
         timeCondition.dayOfMonth = 1;
         timeCondition.weekOfMonth = 1;
         timeCondition.dayOfWeek = 1;
         timeCondition.monthsOfYear = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11];
         timeCondition.dateEnd = -1;
         timeCondition.hourEnd = -1;
         timeCondition.minuteEnd = -1;
         timeCondition.secondEnd = -1;
         timeCondition.timeZoneOffset = 0;
         timeCondition.hourlyInterval = -1;

         if(this.model.startTimeEnabled) {
            this.startTimeData = {
               startTime: { hour: 1, minute: 30, second: 0 },
               timeRange: null,
               startTimeSelected: true,
               valid: true
            };
         }
         else {
            this.startTimeData = {
               startTime: null,
               timeRange: this.model.timeRanges[0],
               startTimeSelected: false,
               valid: true
            };
         }
      }

      if(!this.formatStr) {
         if(this.model.formatTypes && this.model.formatTypes.length) {
            this.formatStr = this.model.formatTypes[0].value;
         }
         else if(formatType != null) {
            this.formatStr = formatType + "";
         }
      }
   }

   private initForm() {
      if(this.model.emailDeliveryEnabled) {
         this.form = new UntypedFormGroup({
            "emails": new UntypedFormControl(this.model.actionModel.emailInfoModel.emails, [
               Validators.required,
               FormValidators.emailList(",;", true, false, this.getEmailUsers())
            ]),
            "cc": new UntypedFormControl(this.model.actionModel.emailInfoModel.ccAddresses, [
               FormValidators.emailList(",;", true, false, this.getEmailUsers())
            ]),
            "bcc": new UntypedFormControl(this.model.actionModel.emailInfoModel.bccAddresses, [
               FormValidators.emailList(",;", true, false, this.getEmailUsers())
            ]),
            "startTime": new UntypedFormControl(this.startTimeData),
            "timeZone": new UntypedFormControl({value: ""})
         });
      }
      else {
         this.form = new UntypedFormGroup({
            "startTime": new UntypedFormControl(this.startTimeData),
            "timeZone": new UntypedFormControl({value: ""})
         });
      }

      if(this.model.timeZoneOptions) {
         this.timeZoneId = this.model.timeZoneOptions[0].timeZoneId;
         this.form.get("timeZone").setValue(this.timeZoneId);
         this.timeZoneLabel = new Date().toTimeString().match(/\((.+)\)/)[1];
      }

      if(this.model.emailDeliveryEnabled) {
         this.subscriptions.add(this.form.get("emails").valueChanges.subscribe((value) => {
            this.model.actionModel.emailInfoModel.emails = value;
         }));

         this.subscriptions.add(this.form.get("cc").valueChanges.subscribe((value) => {
            this.model.actionModel.emailInfoModel.ccAddresses = value;
         }));

         this.subscriptions.add(this.form.get("bcc").valueChanges.subscribe((value) => {
            this.model.actionModel.emailInfoModel.bccAddresses = value;
         }));
      }
   }

   private fixMonthCondition(condition: TimeConditionModel) {
      if(condition?.type == TimeConditionType.EVERY_MONTH) {
         this.model.timeConditionModel.dayOfMonth =
            !condition.dayOfMonth || condition.dayOfMonth < 1 ? 1 : condition.dayOfMonth;
         this.model.timeConditionModel.weekOfMonth =
            !condition.weekOfMonth || condition.weekOfMonth < 1 ? 1 : condition.weekOfMonth;
         this.model.timeConditionModel.dayOfWeek =
            !condition.dayOfWeek || condition.dayOfWeek < 1 ? 1 : condition.dayOfWeek;
      }
   }

   private getEmailUsers(): string[] {
      let identities: string[] = [];

      if(this.model.users) {
         identities = identities.concat(this.model.users.map(user => user + Tool.USER_SUFFIX));
      }

      if(this.model.groups) {
         identities = identities.concat(this.model.groups.map(group => group + Tool.GROUP_SUFFIX));
      }

      if(this.model.emailGroups) {
         identities = identities.concat(this.model.emailGroups.map(user => user + Tool.GROUP_SUFFIX));
      }

      return identities;
   }

   addEmail() {
      this.editingEmails = this.model?.actionModel?.emailInfoModel?.emails;

      this.openEmailDialog().then(
         (result: EmailDialogData) => {
            this.form.get("emails").setValue(result.emails);
         },
         (reject: any) => {
            // canceled
         }
      );
   }

   addCCEmail() {
      this.editingEmails = this.model?.actionModel?.emailInfoModel?.ccAddresses;

      this.openEmailDialog().then(
         (result: EmailDialogData) => {
            this.form.get("cc").setValue(result.emails);
         },
         (reject: any) => {
            // canceled
         }
      );
   }

   addBCCEmail() {
      this.editingEmails = this.model?.actionModel?.emailInfoModel?.bccAddresses;

      this.openEmailDialog().then(
         (result: EmailDialogData) => {
            this.form.get("bcc").setValue(result.emails);
         },
         (reject: any) => {
            // canceled
         }
      );
   }

   openEmailDialog(): Promise<any> {
      this.emailModel = Tool.clone(this.model.emailAddrDialogModel);
      const options: NgbModalOptions = {
         backdrop: "static",
         windowClass: "email-addr-dialog"
      };

      return this.modalService.open(this.emailAddrDialogModel, options).result;
   }

   selectDayOfWeek(day: number): void {
      let index: number = this.model.timeConditionModel.daysOfWeek.indexOf(day);

      if(index != -1) {
         this.model.timeConditionModel.daysOfWeek.splice(index, 1);
      }
      else {
         this.model.timeConditionModel.daysOfWeek.push(day);
      }
   }

   selectDaysOfWeek(isSelectAll: boolean): void {
      if(isSelectAll) {
         this.model.timeConditionModel.daysOfWeek = [1, 2, 3, 4, 5, 6, 7];
      }
      else {
         this.model.timeConditionModel.daysOfWeek = [];
      }
   }

   selectConditionType(type: TimeConditionType): void {
      if(type == TimeConditionType.EVERY_MONTH) {
         if(this.model.timeConditionModel.monthlyDaySelected == null) {
            this.model.timeConditionModel.monthlyDaySelected = true;
         }

         this.fixMonthCondition(this.model.timeConditionModel);
      }


   }

   updateOnlyDataComponents() {
      if(this.model.actionModel.emailInfoModel.matchLayout) {
         this.model.actionModel.emailInfoModel.onlyDataComponents = false;
      }
   }

   //Bug #16185 Add email auto completion dropdown
   search = (text: Observable<string>) => {
      return text.pipe(
         debounceTime(200),
         map((term: string) => {
            if(term) {
               return this.emailHistory
                  .filter(v => new RegExp(term, "gi").test(v))
                  .slice(0, 10);
            }

            return [];
         })
      );
   };

   changeStartTimeModel(m: StartTimeData) {
      this.startTimeData = m;

      if(m.startTimeSelected) {
         this.form.get("timeZone").enable();
      }
      else {
         this.form.get("timeZone").disable();
      }
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   ok(): void {
      if(this.model.emailDeliveryEnabled) {
         if (!this.model.actionModel.emailInfoModel.emails) {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", "_#(js:em.scheduler.actions.emailEmpty)");
            return;
         }

         if (this.startTimeData.startTimeSelected && !this.startTimeData.valid) {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", "_#(js:em.scheduler.conditions.startTimeInvalid)");
            return;
         }

         if (this.model.timeConditionModel.type == TimeConditionType.EVERY_WEEK &&
             this.model.timeConditionModel.daysOfWeek.length == 0) {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                "_#(js:em.scheduler.conditions.daysOfWeekEmpty)");
            return;
         }
      }

      let emailStr = this.model?.actionModel?.emailInfoModel?.emails ?
         this.model.actionModel.emailInfoModel.emails : "";

      const params = new HttpParams()
         .set("toAddrs", emailStr)
         .set("ccAddrs", "")
         .set("emailDeliveryEnabled", this.model.emailDeliveryEnabled);

      this.http.get<EmailValidationResponse>(CHECK_EMAIL_VALID_URI, {params})
         .subscribe(
            (data: EmailValidationResponse) => {
               const type = data.messageCommand.type;
               const splitChar = this.model?.actionModel?.emailInfoModel?.emails ?
                  this.model.actionModel.emailInfoModel.emails.indexOf(";") > -1 ? ";" : "," : ";";

               if(type == "OK") {
                  this.model.timeConditionModel.conditionType = "TimeCondition";

                  if(this.startTimeData.startTimeSelected) {
                     this.model.timeConditionModel.hour = this.startTimeData.startTime.hour;
                     this.model.timeConditionModel.minute = this.startTimeData.startTime.minute;
                     this.model.timeConditionModel.second = this.startTimeData.startTime.second;
                     this.model.timeConditionModel.timeRange = null;
                  }
                  else {
                     this.model.timeConditionModel.hour = -1;
                     this.model.timeConditionModel.minute = -1;
                     this.model.timeConditionModel.second = -1;
                     this.model.timeConditionModel.timeRange = this.startTimeData.timeRange;
                  }

                  if((this.model.timeConditionModel.type == TimeConditionType.EVERY_DAY &&
                     this.model.timeConditionModel.weekdayOnly) ||
                     this.model.timeConditionModel.type == TimeConditionType.EVERY_WEEK)
                  {
                     this.model.timeConditionModel.interval = 1;
                  }
                  else if(this.model.timeConditionModel.type == TimeConditionType.EVERY_MONTH)
                  {
                     if(this.model.timeConditionModel.monthlyDaySelected) {
                        if(this.model.timeConditionModel.dayOfMonth == 30) {
                           this.model.timeConditionModel.dayOfMonth = -2;
                        }
                        else if(this.model.timeConditionModel.dayOfMonth == 31) {
                           this.model.timeConditionModel.dayOfMonth = -1;
                        }

                        this.model.timeConditionModel.weekOfMonth = null;
                        this.model.timeConditionModel.dayOfWeek = null;
                     }
                     else {
                        this.model.timeConditionModel.dayOfMonth = null;
                     }
                  }

                  data.addressHistory.forEach((address) => {
                     if(this.emailHistory.indexOf(address) == -1) {
                        if(this.emailHistory.length >= HISTORY_LIMIT) {
                           this.emailHistory.shift();
                        }

                        this.emailHistory.push(address);
                     }
                  });

                  this.model.actionModel.emailInfoModel.emails =
                     data.addressHistory.join(splitChar);

                  if(!!this.model.actionModel.emailInfoModel.ccAddresses) {
                     this.model.actionModel.emailInfoModel.ccAddresses =
                        this.model.actionModel.emailInfoModel.ccAddresses.replace(/\s+/g, "");
                  }

                  if(!!this.model.actionModel.emailInfoModel.bccAddresses) {
                     this.model.actionModel.emailInfoModel.bccAddresses =
                        this.model.actionModel.emailInfoModel.bccAddresses.replace(/\s+/g, "");
                  }

                  this.model.actionModel.emailInfoModel.formatStr = this.formatStr;
                  LocalStorage.setItem(LocalStorage.MAIL_HISTORY_KEY, JSON.stringify(this.emailHistory));
                  this.addToHistory(this.model.timeConditionModel);
                  this.onCommit.emit(this.model);
               }
               else {
                  ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                     data.messageCommand.message);
                  return;
               }
            },
            (err) => {
               // TODO handle error
               console.error("Failed to check if export valid: ", err);
            }
         );
   }

   /**
    * Return a default condition from local storage if there is one.
    */
   private getHistoryModel(): TimeConditionModel {
      const condition: ScheduleConditionModel = getStoredCondition();
      return condition && condition.conditionType == "TimeCondition" ?
         condition as TimeConditionModel : null;
   }

   /**
    * Add the edited condition to local storage.
    *
    * @param {TimeConditionModel} condition the condition to add to storage
    */
   private addToHistory(condition: TimeConditionModel): void {
      storeCondition(condition);
   }

   public changeEveryDay(weekday: boolean): void {
      this.model.timeConditionModel.weekdayOnly = weekday;
      this.model.timeConditionModel.interval = weekday ? null : 1;
   }

   public formatChange(value: any): void {
      if(this.isReport) {
         this.model.actionModel.emailInfoModel.formatStr = this.formatStr = value;
      }
      else {
         this.model.actionModel.emailInfoModel.formatType = value;
      }

      if(value != FileFormatType.EXPORT_TYPE_EXCEL) {
         this.model.actionModel.emailInfoModel.onlyDataComponents = false;
      }
   }

   get dataSizeOptionVisible(): boolean {
      return !this.isReport &&
         this.model.actionModel.emailInfoModel.formatType != FileFormatType.EXPORT_TYPE_HTML &&
         this.model.actionModel.emailInfoModel.formatType != FileFormatType.EXPORT_TYPE_CSV &&
         (this.model.actionModel.emailInfoModel.formatType != FileFormatType.EXPORT_TYPE_PDF ||
         !(<ViewsheetActionModel> this.model.actionModel).hasPrintLayout);
   }

   get showMeridian(): boolean {
      return this.model && this.model.twelveHourSystem;
   }

   setTimeZone() {
      const id = this.timeZoneId;
      let tz = this.model.timeZoneOptions.find(option => option.timeZoneId == id);

      if(!tz) {
         tz = this.model.timeZoneOptions[0];
         this.timeZoneId = this.model.timeZoneOptions[0].timeZoneId;
      }

      const date: Date = new Date();
      date.setHours(this.startTimeData.startTime.hour);
      date.setMinutes(this.startTimeData.startTime.minute);
      date.setSeconds(this.startTimeData.startTime.second);
      this.startTimeData.startTime = this.convertTimeZone(date);
      this.model.timeConditionModel.timeZone = this.timeZoneId;
      this.form.get("startTime").setValue(this.startTimeData);

      if (tz == this.model.timeZoneOptions[0]) {
         this.timeZoneLabel = new Date().toTimeString().match(/\((.+)\)/)[1];
      }
      else {
         this.timeZoneLabel = tz.label;
      }
   }

   convertTimeZone(date: Date): NgbTimeStruct {
      if(this.model.timeConditionModel.timeZone == null) {
         this.model.timeConditionModel.timeZone = this.model.timeZoneOptions[0].timeZoneId;
      }

      const newOffset: number = this.getTimezoneOffset(this.timeZoneId);
      const oldOffset: number = this.getTimezoneOffset(this.model.timeConditionModel.timeZone);
      let time = date.getTime();
      time -= newOffset - oldOffset;

      let adjustedDate = new Date(time);

      return {
         hour: adjustedDate.getHours(),
         minute: adjustedDate.getMinutes(),
         second: adjustedDate.getSeconds()
      };
   }

   isEmptyTable(): boolean {
      return this.model && this.model.actionModel.emailInfoModel &&
         this.model.actionModel.emailInfoModel.formatType == FileFormatType.EXPORT_TYPE_CSV &&
         this.model.actionModel.emailInfoModel.csvConfigModel.selectedAssemblies != null &&
         this.model.actionModel.emailInfoModel.csvConfigModel.selectedAssemblies.length == 0;
   }

   private getTimezoneOffset(timeZoneId: string): number {
      let date = new Date();
      const UTC = new Date(date.toLocaleString([], { timeZone: "UTC" }));
      const selectedTZ = new Date(date.toLocaleString([], { timeZone: timeZoneId }));

      return (UTC.getTime() - selectedTZ.getTime());
   }

   ngOnDestroy(): void {
      if(!!this.subscriptions) {
         this.subscriptions.unsubscribe();
         this.subscriptions = null;
      }
   }
}
