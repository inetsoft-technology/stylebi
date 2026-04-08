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
import {UntypedFormBuilder, UntypedFormGroup, Validators} from "@angular/forms";
import {TimeConditionModel, TimeRange} from "../../../../../../../shared/schedule/model/time-condition-model";
import {TimeZoneModel} from "../../../../../../../shared/schedule/model/time-zone-model";
import {FormValidators} from "../../../../../../../shared/util/form-validators";
import {Tool} from "../../../../../../../shared/util/tool";
import {DateTimeService} from "../date-time.service";
import {StartTimeChange, StartTimeData} from "../start-time-editor/start-time-editor.component";
import {TaskConditionChanges} from "../task-condition-pane.component";
import {TimeZoneValue} from "../time-zone-select/time-zone-select-component";

@Component({
   selector: "em-daily-condition-editor",
   templateUrl: "./daily-condition-editor.component.html",
   styleUrls: ["./daily-condition-editor.component.scss"]
})
export class DailyConditionEditorComponent implements OnInit {
   @Input() timeZone: string;
   @Input() timeZoneOptions: TimeZoneModel[];
   @Input() timeRanges: TimeRange[] = [];
   @Input() startTimeEnabled = true;
   @Input() timeRangeEnabled = true;
   @Input() showMeridian: boolean;
   @Output() modelChanged = new EventEmitter<TaskConditionChanges>();

   @Input()
   get condition(): TimeConditionModel {
      return this._condition;
   }

   set condition(value: TimeConditionModel) {
      const oldCondition = this._condition;
      this._condition = Object.assign({}, value);

      if(Tool.isEquals(oldCondition, value)) {
         return;
      }

      this.form.get("weekdayOnly").setValue(this._condition.weekdayOnly || false);
      this.form.get("interval").setValue(this._condition.interval || 0);
      this.form.get("timeZone").setValue({
         timeZoneId: this._condition.timeZone || "",
         timeZoneLabel: this._condition.timeZoneLabel || ""
      } as TimeZoneValue);
      this.initFormState();

      this.startTimeData = {
         startTime: this.dateTimeService.getStartTime(this._condition),
         timeRange: this._condition.timeRange,
         startTimeSelected: !this.timeRangeEnabled || !this._condition.timeRange
      };

      this.startTimeValid = !!this.startTimeData.startTime || !!this.startTimeData.timeRange;

      if(!!this.startTimeData) {
         this.timeZoneEnabled = this.startTimeData.startTimeSelected;
      }
   }

   get weekdayOnly(): boolean {
      return this.form.get("weekdayOnly").value;
   }

   form: UntypedFormGroup;
   startTimeData: StartTimeData;
   timeZoneEnabled = true;

   get timeZoneLabel(): string {
      return (this.form.get("timeZone").value as TimeZoneValue)?.timeZoneLabel || "";
   }
   private _condition: TimeConditionModel;
   private startTimeValid = false;

   constructor(private dateTimeService: DateTimeService, fb: UntypedFormBuilder) {
      this.form = fb.group({
         interval: [1, [Validators.required, FormValidators.positiveNonZeroIntegerInRange]],
         weekdayOnly: [false],
         timeZone: [{ timeZoneId: "", timeZoneLabel: "" } as TimeZoneValue]
      });
   }

   ngOnInit() {
      if(!!this.startTimeData) {
         this.timeZoneEnabled = this.startTimeData.startTimeSelected;
      }
   }

   onStartTimeChanged(event: StartTimeChange) {
      const startTimeData = {
         startTime: event.startTime,
         timeRange: event.timeRange,
         startTimeSelected: event.startTimeSelected
      };

      if(Tool.isEquals(startTimeData, this.startTimeData)) {
         return;
      }

      this.startTimeData = startTimeData;

      this.startTimeValid = event.valid;
      this.fireModelChanged();
   }

   fireModelChanged(): void {
      const oldTZ = this.condition.timeZone;
      const tzValue = this.form.get("timeZone").value as TimeZoneValue;
      this.condition.interval = this.form.get("interval").value;
      this.condition.timeZone = tzValue?.timeZoneId || "";
      this.condition.timeZoneLabel = tzValue?.timeZoneLabel || "";
      this.condition.weekdayOnly = this.weekdayOnly;
      this.initFormState();

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

   private initFormState(): void {
      if(this.condition.weekdayOnly) {
         this.form.get("interval").disable();
      }
      else {
         this.form.get("interval").enable();
      }
   }
}
