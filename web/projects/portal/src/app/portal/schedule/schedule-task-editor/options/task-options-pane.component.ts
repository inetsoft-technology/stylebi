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
import { HttpClient } from "@angular/common/http";
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbDateStruct, NgbModal, NgbInputDatepicker, NgbDatepicker } from "@ng-bootstrap/ng-bootstrap";
import { IdentityIdWithLabel } from "../../../../../../../em/src/app/settings/security/users/idenity-id-with-label";
import { IdentityId } from "../../../../../../../em/src/app/settings/security/users/identity-id";
import { IdentityType } from "../../../../../../../shared/data/identity-type";
import { TaskOptionsPaneModel } from "../../../../../../../shared/schedule/model/task-options-pane-model";
import { TimeZoneModel } from "../../../../../../../shared/schedule/model/time-zone-model";
import { ScheduleUsersService } from "../../../../../../../shared/schedule/schedule-users.service";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { ComponentTool } from "../../../../common/util/component-tool";
import { CustomSelectOption, CustomSelectComponent } from "../../../../widget/custom-select/custom-select.component";
import { ExecuteAsDialog } from "../execute-as-dialog/execute-as-dialog.component";
import { Observable } from "rxjs";
import { ScheduleTaskDialogModel } from "../../../../../../../shared/schedule/model/schedule-task-dialog-model";


@Component({
    selector: "task-options-pane",
    templateUrl: "./task-options-pane.component.html",
    styleUrls: ["./task-options-pane.component.scss"],
    imports: [FormsModule, ReactiveFormsModule, NgbInputDatepicker, CustomSelectComponent]
})
export class TaskOptionsPane implements OnInit {
   private readonly defaultYearWindow: number = 10;
   @Input() set model(value: TaskOptionsPaneModel) {
      this._model = value;
      const start: Date = new Date(value.startFrom);
      this.startDate = value.startFrom ? {
         year: start.getFullYear(),
         month: start.getMonth() + 1,
         day: start.getDate()
      } : null;
      const end: Date = new Date(value.stopOn);
      this.endDate = value.stopOn ? {
         year: end.getFullYear(),
         month: end.getMonth() + 1,
         day: end.getDate()
      } : null;

      this.localTimeZoneId = value.timeZone == null ?
         Intl.DateTimeFormat().resolvedOptions().timeZone : value.timeZone;
      this.getExecuteAsName();
      this.executeAsType = value.idType;

      if(this.form) {
         this.form.get("start").setValue(this.startDate);
         this.form.get("stop").setValue(this.endDate);
      }
   }
   @Input() oldTaskName: string;
   @Input() taskName: string;
   @Input() parentForm: UntypedFormGroup;
   @Input() newTask: boolean;
   @Input() timeZoneOptions: TimeZoneModel[];
   @Input() saveTask: () =>  Promise<any>;
   @Output() doneLoading = new EventEmitter<ScheduleTaskDialogModel>();
   @Output() updateTaskName = new EventEmitter<string>();
   @Output() closeEditor = new EventEmitter<TaskOptionsPaneModel>();
   @Output() cancelTask = new EventEmitter();
   _model: TaskOptionsPaneModel;
   owners: IdentityIdWithLabel[];
   groups: IdentityId[];
   adminName: string;
   startDate: NgbDateStruct;
   endDate: NgbDateStruct;
   form: UntypedFormGroup = null;
   executeAsName: string;
   executeAsType: number;
   localTimeZoneId: string;

   public static DEFAULT_LOCALE: string = "Default";
   executeAsTypes: any[] = [
      {value: IdentityType.USER, label: "_#(js:User)"},
      {value: IdentityType.GROUP, label: "_#(js:Group)"}
   ];

   constructor(private modalService: NgbModal, private usersService: ScheduleUsersService,
               private http: HttpClient)
   {
      usersService.getOwners().subscribe(value => this.owners = value);
      usersService.getGroups().subscribe(value => this.groups = value);
      usersService.getAdminName().subscribe(value => this.adminName = value);
   }

   ngOnInit() {
      this.initForm();
   }

   get locale(): string {
      return !!this._model.locale ? this._model.locale : TaskOptionsPane.DEFAULT_LOCALE;
   }

   set locale(loc: string) {
      this._model.locale = loc != TaskOptionsPane.DEFAULT_LOCALE ? loc : null;
   }

   get timeZoneSelectOptions(): CustomSelectOption<string>[] {
      return (this.timeZoneOptions || []).map((tz) => ({
         value: tz.timeZoneId,
         label: `${tz.hourOffset} ${tz.label}`,
         title: tz.timeZoneId
      }));
   }

   get executeAsTypeSelectOptions(): CustomSelectOption<number>[] {
      return this.executeAsTypes.map((type) => ({
         value: type.value,
         label: type.label
      }));
   }

   get localeSelectOptions(): CustomSelectOption<string>[] {
      return [
         {value: TaskOptionsPane.DEFAULT_LOCALE, label: "_#(js:Default)"},
         ...(this._model?.locales || []).map((locale) => ({value: locale, label: locale}))
      ];
   }

   getMonthSelectOptions(datepicker: NgbDatepicker): CustomSelectOption<number>[] {
      const displayed = this.getDisplayedMonth(datepicker);

      return this.getAvailableMonths(datepicker, displayed.year).map((month) => ({
         value: month,
         label: datepicker.i18n.getMonthShortName(month, displayed.year)
      }));
   }

   getYearSelectOptions(datepicker: NgbDatepicker): CustomSelectOption<number>[] {
      const displayed = this.getDisplayedMonth(datepicker);
      const minYear = datepicker.state.minDate?.year ?? displayed.year - this.defaultYearWindow;
      const maxYear = datepicker.state.maxDate?.year ?? displayed.year + this.defaultYearWindow;
      const options: CustomSelectOption<number>[] = [];

      for(let year = minYear; year <= maxYear; year++) {
         options.push({
            value: year,
            label: datepicker.i18n.getYearNumerals(year)
         });
      }

      return options;
   }

   selectMonth(datepicker: NgbDatepicker, month: number): void {
      const displayed = this.getDisplayedMonth(datepicker);
      datepicker.navigateTo({ year: displayed.year, month, day: 1 });
   }

   selectYear(datepicker: NgbDatepicker, year: number): void {
      const displayed = this.getDisplayedMonth(datepicker);
      const availableMonths = this.getAvailableMonths(datepicker, year);
      const month = availableMonths.includes(displayed.month) ? displayed.month : availableMonths[0];

      datepicker.navigateTo({ year, month, day: 1 });
   }

   navigateMonth(datepicker: NgbDatepicker, offset: number): void {
      const displayed = this.getDisplayedMonth(datepicker);
      let year = displayed.year;
      let month = displayed.month + offset;

      while(month < 1) {
         month += 12;
         year--;
      }

      while(month > 12) {
         month -= 12;
         year++;
      }

      const minDate = datepicker.state.minDate;
      const maxDate = datepicker.state.maxDate;

      if(minDate && (year < minDate.year || year === minDate.year && month < minDate.month)) {
         year = minDate.year;
         month = minDate.month;
      }

      if(maxDate && (year > maxDate.year || year === maxDate.year && month > maxDate.month)) {
         year = maxDate.year;
         month = maxDate.month;
      }

      datepicker.navigateTo({ year, month, day: 1 });
   }

   canNavigateMonth(datepicker: NgbDatepicker, offset: number): boolean {
      const displayed = this.getDisplayedMonth(datepicker);
      const minDate = datepicker.state.minDate;
      const maxDate = datepicker.state.maxDate;
      let year = displayed.year;
      let month = displayed.month + offset;

      while(month < 1) {
         month += 12;
         year--;
      }

      while(month > 12) {
         month -= 12;
         year++;
      }

      if(minDate && (year < minDate.year || year === minDate.year && month < minDate.month)) {
         return false;
      }

      if(maxDate && (year > maxDate.year || year === maxDate.year && month > maxDate.month)) {
         return false;
      }

      return true;
   }

   public startDateChange(date: NgbDateStruct): void {
      if(date) {
         this.startDate = date;
         this._model.startFrom = new Date(date.year, date.month - 1, date.day, 0, 0, 0)
            .getTime();
      }
   }

   public endDateChange(date: NgbDateStruct): void {
      if(date) {
         this.endDate = date;
         this._model.stopOn = new Date(date.year, date.month - 1, date.day, 0, 0, 0)
            .getTime();
      }
   }

   public clearStartDate(): void {
      this.startDate = null;
      this._model.startFrom = 0;
      this.form.get("start").setValue(this.startDate);
   }

   public clearEndDate(): void {
      this.endDate = null;
      this._model.stopOn = 0;
      this.form.get("stop").setValue(this.endDate);
   }

   public clearUser(): void {
      this.executeAsName = "";
      this._model.idName = null;
      this._model.idType = null;
   }

   private getExecuteAsName(): void {
      let idName = this._model.owner;

      if(!!this._model.idName) {
         idName = !this._model.idAlias ? this._model.idName : this._model.idAlias;
      }
      else if(!!this._model.ownerAlias) {
         idName = this._model.ownerAlias;
      }

      let _executeAsName = "";

      if(this.adminName && !this._model.securityEnabled) {
         _executeAsName = "";
      }
      else if(this.adminName) {
         _executeAsName = idName === "anonymous" ? "" : idName;
      }
      else {
         _executeAsName = idName;
      }

      this.executeAsName = _executeAsName;
   }

   public getExecuteAsType(): string {
      const idName: string = this._model.idName == null ? this._model.owner
         : this._model.idName;

      if(this.adminName && !this._model.securityEnabled) {
         return "";
      }
      else if(this.adminName) {
         return !idName ? "" : this._model.idType == IdentityType.USER ? "_#(js:User)" : "_#(js:Group)";
      }
      else {
         return this._model.idType == IdentityType.USER ? "_#(js:User)" : "_#(js:Group)";
      }
   }

   public disableExecuteAs(): boolean {
      return !(this.adminName && this._model.securityEnabled && !this._model.selfOrg) || this.loadingUsers;
   }

   public updateExecuteAs(name: string) {
      if(this.disableExecuteAs()) {
         return;
      }

      this.executeAsName = name;

      if(this.executeAsName == "" || this.executeAsName == this._model.owner) {
         this._model.idName = null;
      }
      else {
         this._model.idName = this.executeAsName;
      }
   }

   public save(): void {
      this.saveTask().then(() => {
         this.form.markAsPristine();
         this.updateTaskName.emit(this.taskName);
      });
   }

   public openExecuteAsDialog(): void {
      let dialog: ExecuteAsDialog = ComponentTool.showDialog(this.modalService, ExecuteAsDialog,
         (identity: {name: string, type: number, alias?: string}) => {
            this._model.idName = identity.name;
            this._model.idType = identity.type;
            this._model.idAlias = identity.alias;
            this.getExecuteAsName();
         });

      dialog.users = this.owners;
      dialog.groups = this.getGroupModel();
      dialog.type = this.executeAsType;
   }

   getGroupModel(): IdentityId[] {
      return this.groups;
   }

   initForm() {
      this.form = new UntypedFormGroup({
         "start": new UntypedFormControl(this.startDate, []),
         "stop": new UntypedFormControl(this.endDate, []),
         "timeZone": new UntypedFormControl(this._model.timeZone)
      }, FormValidators.dateSmallerThan("start", "stop"));

      this.form.get("start").valueChanges.subscribe((date) => {
         this.startDateChange(date);
      });

      this.form.get("stop").valueChanges.subscribe((date) => {
         this.endDateChange(date);
      });

      this.form.get("timeZone").valueChanges.subscribe((timeZoneId) => {
         this._model.timeZone = timeZoneId;
      });
   }

   private getDisplayedMonth(datepicker: NgbDatepicker): NgbDateStruct {
      const displayedMonth: any = datepicker.state.months?.[0];
      return displayedMonth?.firstDate ?? displayedMonth ?? datepicker.state.firstDate ?? this.startDate ?? this.endDate;
   }

   private getAvailableMonths(datepicker: NgbDatepicker, year: number): number[] {
      const minDate = datepicker.state.minDate;
      const maxDate = datepicker.state.maxDate;
      const startMonth = minDate && minDate.year === year ? minDate.month : 1;
      const endMonth = maxDate && maxDate.year === year ? maxDate.month : 12;
      const months: number[] = [];

      for(let month = startMonth; month <= endMonth; month++) {
         months.push(month);
      }

      return months;
   }

   get loadingUsers(): boolean {
      return this.usersService.isLoading && this._model.securityEnabled;
   }
}
