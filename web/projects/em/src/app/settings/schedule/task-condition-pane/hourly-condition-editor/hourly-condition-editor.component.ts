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
import {Component, EventEmitter, Input, OnInit, Output} from "@angular/core";
import {
   FormGroupDirective,
   NgForm,
   UntypedFormBuilder,
   UntypedFormControl,
   UntypedFormGroup,
   ValidationErrors,
   Validators
} from "@angular/forms";
import {ErrorStateMatcher} from "@angular/material/core";
import {TimeConditionModel} from "../../../../../../../shared/schedule/model/time-condition-model";
import {TimeZoneModel} from "../../../../../../../shared/schedule/model/time-zone-model";
import {DateTypeFormatter} from "../../../../../../../shared/util/date-type-formatter";
import {FormValidators} from "../../../../../../../shared/util/form-validators";
import {Tool} from "../../../../../../../shared/util/tool";
import {DateTimeService} from "../date-time.service";
import {TaskConditionChanges} from "../task-condition-pane.component";
import {TimeZoneValue} from "../time-zone-select/time-zone-select-component";

@Component({
   selector: "em-hourly-condition-editor",
   templateUrl: "./hourly-condition-editor.component.html",
   styleUrls: ["./hourly-condition-editor.component.scss"]
})
export class HourlyConditionEditorComponent implements OnInit {
   @Input() showMeridian: boolean;
   @Input()  timeZoneOptions: TimeZoneModel[];
   @Output() modelChanged = new EventEmitter<TaskConditionChanges>();
   startTime: string = null;
   endTime: string = null;

   @Input()
   get condition() {
      return this._condition;
   }

   set condition(value: TimeConditionModel) {
      const oldCondition = this._condition;
      this._condition = Object.assign({}, value);

      if(Tool.isEquals(oldCondition, value)) {
         return;
      }

      this.form.get("startTime").setValue(
         this.dateTimeService.getStartTime(this._condition), { emitEvent: false });
      this.form.get("endTime").setValue(
         this.dateTimeService.getEndTime(this._condition), { emitEvent: false });
      this.form.get("interval").setValue(this._condition.hourlyInterval || 0, { emitEvent: false });
      this.form.get("weekdays").setValue(this._condition.daysOfWeek || [], { emitEvent: false });
      this.form.get("timeZone").setValue({
         timeZoneId: this._condition.timeZone || "",
         timeZoneLabel: this._condition.timeZoneLabel || ""
      } as TimeZoneValue, { emitEvent: false });
   }

   form: UntypedFormGroup;
   endTimeErrorMatcher: ErrorStateMatcher;
   private _condition: TimeConditionModel;

   get timeZoneLabel(): string {
      return (this.form.get("timeZone").value as TimeZoneValue)?.timeZoneLabel || "";
   }

   constructor(private dateTimeService: DateTimeService, fb: UntypedFormBuilder,
               defaultErrorMatcher: ErrorStateMatcher)
   {
      this.form = fb.group(
         {
            startTime: [this.dateTimeService.getTimeString(new Date()), [Validators.required]],
            endTime: [this.dateTimeService.getTimeString(new Date()), [Validators.required]],
            timeZone: [{ timeZoneId: "", timeZoneLabel: "" } as TimeZoneValue, []],
            interval: [1, [Validators.required, FormValidators.positiveNonZeroInRange]],
            weekdays: [[], [Validators.required]]
         },
         {
            validator: this.timeChronological
         }
      );

      this.endTimeErrorMatcher = {
         isErrorState: (control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null) =>
            !!this.form.errors && !!this.form.errors.timeChronological ||
            defaultErrorMatcher.isErrorState(control, form)
      };
   }

   ngOnInit() {
      this.form.get("startTime").valueChanges.subscribe((val) => {
         if(this.startTime != (!val ? val : val.toString())) {
            this.startTime = val.toString();
            this.fireModelChanged();
         }
      });

      this.form.get("endTime").valueChanges.subscribe((val) => {
         if(this.endTime != (!val ? val : val.toString())) {
            this.endTime = val.toString();
            this.fireModelChanged();
         }
      });
   }

   fireModelChanged(): void {
      const oldTZ = this.condition.timeZone;
      const tzValue = this.form.get("timeZone").value as TimeZoneValue;
      const newTZ = tzValue?.timeZoneId || "";
      const format = DateTypeFormatter.ISO_8601_TIME_FORMAT;
      this.startTime = DateTypeFormatter
         .formatStr(this.dateTimeService.validateTimeValue(this.form.get("startTime")?.value), format);
      this.endTime = DateTypeFormatter
         .formatStr(this.dateTimeService.validateTimeValue(this.form.get("endTime")?.value), format);

      this.form.get("startTime").setValue(
         this.dateTimeService.applyTimeZoneOffsetDifference(this.form.get("startTime").value, oldTZ, newTZ),
         {emitEvent: false});
      this.form.get("endTime").setValue(
         this.dateTimeService.applyTimeZoneOffsetDifference(this.form.get("endTime").value, oldTZ, newTZ),
         {emitEvent: false});

      this.dateTimeService.setStartTime(this.form.get("startTime").value, this.condition);
      this.dateTimeService.setEndTime(this.form.get("endTime").value, this.condition);
      this.condition.hourlyInterval = this.form.get("interval").value;
      this.condition.daysOfWeek = this.form.get("weekdays").value;
      this.condition.timeZone = newTZ;
      this.condition.timeZoneLabel = tzValue?.timeZoneLabel || "";

      this.modelChanged.emit({
         valid: this.form.valid,
         model: this.condition
      });
   }

   private timeChronological: (FormGroup) => ValidationErrors | null = (group: UntypedFormGroup) => {
      if(!group) {
         return null;
      }

      const startControl = group.get("startTime");
      const endControl = group.get("endTime");

      if(startControl && endControl) {
         const startTime = DateTypeFormatter.toTimeInstant(
            startControl.value, DateTypeFormatter.ISO_8601_TIME_FORMAT);
         const endTime = DateTypeFormatter.toTimeInstant(
            endControl.value, DateTypeFormatter.ISO_8601_TIME_FORMAT);

         if(startTime && endTime) {
            const startDate =
               new Date(2000, 0, 1, startTime.hours, startTime.minutes, startTime.seconds);
            const endDate = new Date(2000, 0, 1, endTime.hours, endTime.minutes, endTime.seconds);

            if(startDate >= endDate) {
               return { timeChronological: true };
            }
         }
      }

      return null;
   };

}
