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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import {
   UntypedFormBuilder,
   UntypedFormControl,
   UntypedFormGroup,
   FormGroupDirective, NgForm,
   ValidationErrors,
   Validators
} from "@angular/forms";
import { ErrorStateMatcher } from "@angular/material/core";
import { FeatureFlagValue } from "../../../../../../../shared/feature-flags/feature-flags.service";
import {
   TimeConditionModel,
   TimeConditionType, TimeRange
} from "../../../../../../../shared/schedule/model/time-condition-model";
import { TimeZoneModel } from "../../../../../../../shared/schedule/model/time-zone-model";
import { DateTimeService } from "../date-time.service";
import { StartTimeChange, StartTimeData } from "../start-time-editor/start-time-editor.component";
import { TaskConditionChanges } from "../task-condition-pane.component";

@Component({
   selector: "em-monthly-condition-editor",
   templateUrl: "./monthly-condition-editor.component.html",
   styleUrls: ["./monthly-condition-editor.component.scss"]
})
export class MonthlyConditionEditorComponent implements OnInit {
   @Input() timeZone: string;
   @Input() timeZoneOptions: TimeZoneModel[];
   @Input() timeRanges: TimeRange[] = [];
   @Input() startTimeEnabled = true;
   @Input() timeRangeEnabled = true;
   @Input() showMeridian: boolean;
   @Output() modelChanged = new EventEmitter<TaskConditionChanges>();

   readonly FeatureFlagValue = FeatureFlagValue;
   readonly TimeConditionType = TimeConditionType;
   dayOfMonthErrorMatcher: ErrorStateMatcher;
   weekOfMonthErrorMatcher: ErrorStateMatcher;
   dayOfWeekErrorMatcher: ErrorStateMatcher;

   @Input()
   get condition() {
      return this._condition;
   }

   set condition(value: TimeConditionModel) {
      if(this._condition != null &&
         this._condition.label === value.label &&
         this._condition.timeZoneOffset === value.timeZoneOffset &&
         this._condition.hour === value.hour &&
         this._condition.minute === value.minute &&
         this._condition.second === value.second &&
         this._condition.monthlyDaySelected === value.monthlyDaySelected &&
         this._condition.dayOfMonth === value.dayOfMonth &&
         this._condition.weekOfMonth === value.weekOfMonth &&
         this._condition.dayOfWeek === value.dayOfWeek &&
         this._condition.monthsOfYear === value.monthsOfYear)
      {
         return;
      }

      this._condition = Object.assign({}, value);
      this.form.get("monthlyDaySelected").setValue(this._condition.monthlyDaySelected !== false);
      this.form.get("dayOfMonth").setValue(this._condition.dayOfMonth || 1);
      this.form.get("weekOfMonth").setValue(
         !this._condition.weekOfMonth || this._condition.weekOfMonth === -1 ? 1 : this._condition.weekOfMonth);
      this.form.get("dayOfWeek").setValue(
         !this._condition.dayOfWeek || this._condition.dayOfWeek === -1 ? 1 : this._condition.dayOfWeek);
      this.form.get("months").setValue(this._condition.monthsOfYear || []);
      this.form.get("timeZone").setValue(this._condition.timeZone || "");

      this.startTimeData = {
         startTime: this.dateTimeService.getStartTime(this._condition),
         timeRange: this._condition.timeRange,
         startTimeSelected: !this.timeRangeEnabled || !this._condition.timeRange
      };
      this.startTimeValid = !!this.startTimeData.startTime || !!this.startTimeData.timeRange;

      this.onTypeChanged();
      this.timeZoneLabel = this.dateTimeService
         .getTimeZoneLabel(this.timeZoneOptions, this._condition.timeZone, this.timeZone);
   }

   form: UntypedFormGroup;
   startTimeData: StartTimeData;
   timeZoneLabel: string;
   timeZoneEnabled = true;
   private _condition: TimeConditionModel;
   private startTimeValid = false;

   constructor(private dateTimeService: DateTimeService, fb: UntypedFormBuilder,
               defaultErrorMatcher: ErrorStateMatcher)
   {
      this.form = fb.group(
         {
            monthlyDaySelected: [true],
            dayOfMonth: [1],
            weekOfMonth: [1],
            dayOfWeek: [1],
            months: [[], [Validators.required]],
            timeZone: [""]
         },
         {
            validator: [this.validator]
         });

      this.dayOfMonthErrorMatcher = {
         isErrorState: (control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null) =>
            !!this.form.errors && !!this.form.errors.dayOfMonthRequired ||
            defaultErrorMatcher.isErrorState(control, form)
      };

      this.weekOfMonthErrorMatcher = {
         isErrorState: (control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null) =>
            !!this.form.errors && !!this.form.errors.weekOfMonthRequired ||
            defaultErrorMatcher.isErrorState(control, form)
      };

      this.dayOfWeekErrorMatcher = {
         isErrorState: (control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null) =>
            !!this.form.errors && !!this.form.errors.dayOfWeekRequired ||
            defaultErrorMatcher.isErrorState(control, form)
      };
   }

   ngOnInit() {
      this.timeZoneLabel = this.dateTimeService
         .getTimeZoneLabel(this.timeZoneOptions, this.condition?.timeZone, this.timeZone);

      if(!!this.startTimeData) {
         this.timeZoneEnabled = this.startTimeData.startTimeSelected;
      }
   }

   onTypeChanged(): void {
      if(this.form.get("monthlyDaySelected").value) {
         this.form.get("dayOfMonth").enable();
         this.form.get("weekOfMonth").disable();
         this.form.get("dayOfWeek").disable();
      }
      else {
         this.form.get("dayOfMonth").disable();
         this.form.get("weekOfMonth").enable();
         this.form.get("dayOfWeek").enable();
      }
   }

   onStartTimeChanged(event: StartTimeChange) {
      this.startTimeData = {
         startTime: event.startTime,
         timeRange: event.timeRange,
         startTimeSelected: event.startTimeSelected
      };
      this.startTimeValid = event.valid;
      this.fireModelChanged();
   }

   setTimeZoneLabel(label: string): void {
      this.timeZoneLabel = label;
   }

   fireModelChanged() {
      const oldTZ = this.condition.timeZone;
      this.condition.monthlyDaySelected = this.form.get("monthlyDaySelected").value;
      this.condition.dayOfMonth = this.form.get("dayOfMonth").value;
      this.condition.weekOfMonth = this.form.get("weekOfMonth").value;
      this.condition.dayOfWeek = this.form.get("dayOfWeek").value;
      this.condition.monthsOfYear = this.form.get("months").value;
      this.condition.timeZone = this.form.get("timeZone").value;

      if(this.startTimeData) {
         this.startTimeData = this.dateTimeService
            .updateStartTimeDataTimeZone(this.startTimeData, oldTZ, this.condition.timeZone);

         this.dateTimeService.setStartTime(this.startTimeData.startTime, this.condition);
         this.condition.timeRange = this.startTimeData.timeRange;
         this.timeZoneEnabled = this.startTimeData.startTimeSelected;
      }

      this.modelChanged.emit({
         valid: this.form.valid && this.startTimeValid,
         model: this.condition
      });
   }

   private validator = (group: UntypedFormGroup): ValidationErrors | null => {
      if(!group) {
         return null;
      }

      const selected = group.get("monthlyDaySelected");

      if(!selected) {
         return null;
      }

      if(!!selected.value) {
         const dayOfMonth = group.get("dayOfMonth");

         if(dayOfMonth) {
            const errors = Validators.required(dayOfMonth);

            if(errors && errors.required) {
               return { dayOfMonthRequired: true };
            }
         }
      }
      else {
         const weekOfMonth = group.get("weekOfMonth");
         const dayOfWeek = group.get("dayOfWeek");

         if(weekOfMonth && dayOfWeek) {
            let result: ValidationErrors = null;
            let errors = Validators.required(weekOfMonth);

            if(errors && errors.required) {
               result = { weekOfMonthRequired: true };
            }

            errors = Validators.required(dayOfWeek);

            if(errors && errors.required) {
               result = Object.assign({}, result, { dayOfWeekRequired: true });
            }

            return result;
         }
      }

      return null;
   };
}
