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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { FeatureFlagValue } from "../../../../../../../shared/feature-flags/feature-flags.service";
import { TimeConditionModel } from "../../../../../../../shared/schedule/model/time-condition-model";
import { TimeZoneModel } from "../../../../../../../shared/schedule/model/time-zone-model";
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
      let date: Date;

      if(this._condition.date) {
         if(this._condition.timeZone == null) {
            date = this.dateTimeService.getDate(this._condition);
         }
         else {
            date = new Date(this.convertToLocalTime(this._condition.date));
         }
      }
      else {
         date = new Date();
      }

      this.dateValue = new Date(date.getFullYear(), date.getMonth(), date.getDate());
      this.form.get("startTime").setValue(this.dateTimeService.getTimeString(date));
      this.form.get("timeZone").setValue(this._condition.timeZone || "");
   }

   form: UntypedFormGroup;
   private _condition: TimeConditionModel;
   dateValue: Date;
   timeValue: string;
   timeZoneLabel: string;

   constructor(private dateTimeService: DateTimeService, fb: UntypedFormBuilder) {
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

   convertToLocalTime(time: number): number {
      const localOffset = this.dateTimeService.getLocalTimezoneOffset(this._condition.timeZone);
      const serverOffset: number = this._condition.timeZoneOffset;
      return time - localOffset - serverOffset;
   }

   convertToServerTime(time: number): number {
      const localOffset = this.dateTimeService.getLocalTimezoneOffset(this._condition.timeZone);
      const serverOffset: number = this._condition.timeZoneOffset;
      return time + localOffset + serverOffset;
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
         time, this.condition);

      this.condition.date = this.convertToServerTime(this.condition.date);

      this.modelChanged.emit({
         valid: this.form.valid,
         model: this.condition
      });
   }

   updateDateTimeZone(oldDate: Date, oldTZ: string, newTZ: string): Date {
      if(oldTZ == null && newTZ == null) {
         return oldDate;
      }

      let time = oldDate.getTime();

      let date = new Date();
      const oldTZOffset = new Date(date.toLocaleString([], { timeZone: oldTZ })).getTime();
      const newTZOffset = new Date(date.toLocaleString([], { timeZone: newTZ })).getTime();
      time += oldTZOffset - newTZOffset;

      return new Date(time);
   }
}
