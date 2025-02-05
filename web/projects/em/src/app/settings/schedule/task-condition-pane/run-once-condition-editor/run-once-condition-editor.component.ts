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
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import dayjs from "dayjs";
import { FeatureFlagValue } from "../../../../../../../shared/feature-flags/feature-flags.service";
import { TimeConditionModel } from "../../../../../../../shared/schedule/model/time-condition-model";
import { TimeZoneModel } from "../../../../../../../shared/schedule/model/time-zone-model";
import { TimeZoneService } from "../../../../../../../shared/schedule/time-zone.service";
import { DateTypeFormatter } from "../../../../../../../shared/util/date-type-formatter";
import { Tool } from "../../../../../../../shared/util/tool";
import { DateTimeService } from "../date-time.service";
import { TaskConditionChanges } from "../task-condition-pane.component";

@Component({
   selector: "em-run-once-condition-editor",
   templateUrl: "./run-once-condition-editor.component.html",
   styleUrls: ["./run-once-condition-editor.component.scss"]
})
export class RunOnceConditionEditorComponent implements OnInit {
   @Input() timeZone: string;
   @Input() timeZoneOptions: TimeZoneModel[];
   @Input() showMeridian: boolean;
   @Output() modelChanged = new EventEmitter<TaskConditionChanges>();
   readonly FeatureFlagValue = FeatureFlagValue;

   @Input()
   get condition(): TimeConditionModel {
      return this._condition;
   }

   set condition(value: TimeConditionModel) {
      if(this._condition != null &&
         this._condition.label === value.label &&
         this._condition.timeZoneOffset === value.timeZoneOffset &&
         this._condition.date === value.date)
      {
         return;
      }

      this._condition = Object.assign({}, value);
      const date = dayjs(this._condition.date)
         .utcOffset(this.timeZoneService.calculateTimezoneOffset(this._condition.timeZone) / 60000);
      this.dateValue = new Date(date.year(), date.month(), date.date(), date.hour(), date.minute());
      this.form.get("startTime").setValue(this.dateTimeService.getTimeString(this.dateValue), {emitEvent: false});
      this.form.get("timeZone").setValue(this._condition.timeZone || "", {emitEvent: false});
      this.timeZoneLabel = this.dateTimeService
         .getTimeZoneLabel(this.timeZoneOptions, this._condition.timeZone, this.timeZone);
   }

   form: UntypedFormGroup;
   private _condition: TimeConditionModel;
   dateValue: Date;
   timeValue: string;
   timeZoneLabel: string;

   constructor(private dateTimeService: DateTimeService, fb: UntypedFormBuilder,
               private timeZoneService: TimeZoneService)
   {
      this.form = fb.group({
         date: new UntypedFormGroup({}),
         startTime: [this.dateTimeService.getTimeString(new Date()), [Validators.required]],
         timeZone: [""]
      });

      this.form.get("startTime").valueChanges.subscribe(value => {
         if(!!!this.timeValue) {
            // init
            this.timeValue = value;
         }
         else if(!Tool.isEquals(value, this.timeValue)) {
            this.timeValue = value;
            this.fireModelChanged();
         }
      });
   }

   ngOnInit(): void {
      this.timeZoneLabel = this.dateTimeService
         .getTimeZoneLabel(this.timeZoneOptions, this.condition?.timeZone, this.timeZone);
    }

   setTimeZoneLabel(label: string): void {
      this.timeZoneLabel = label;
   }

   fireModelChanged(_date?: Date): void {
      const oldTZ = this.condition.timeZone;
      const newTZ = this.form.get("timeZone").value;
      let newDateTime = this.dateTimeService.applyDateTimeZoneOffsetDifference(this.form.get("startTime").value,
         oldTZ, newTZ, this.dateValue);

      if(!!this.form.get("startTime").value) {
         this.timeValue = this.dateTimeService.getTimeString(newDateTime);

         if(this.form.get("startTime").value) {
            this.form.get("startTime").setValue(this.timeValue);
         }
      }

      if(this.dateValue) {
         this.dateValue = newDateTime;
         this.dateTimeService.resetTimeOfDate(this.dateValue);
      }

      this.dateValue = !!_date ? _date : this.dateValue;
      const date = this.dateValue || new Date();
      const time = this.form.get("startTime").value || "00:00:00";
      this.condition.timeZone = this.form.get("timeZone").value;
      this.dateTimeService.setDate(
         DateTypeFormatter.format(date, DateTypeFormatter.ISO_8601_DATE_FORMAT),
         time, this.timeZoneService.calculateTimezoneOffset(this.condition.timeZone),
         this.condition);

      this.modelChanged.emit({
         valid: this.form.valid,
         model: this.condition
      });
   }
}
