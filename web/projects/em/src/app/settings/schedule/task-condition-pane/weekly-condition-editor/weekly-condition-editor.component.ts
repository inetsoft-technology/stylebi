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
import { UntypedFormBuilder, UntypedFormGroup, Validators, FormsModule, ReactiveFormsModule } from "@angular/forms";
import {
   TimeConditionModel,
   TimeRange
} from "../../../../../../../shared/schedule/model/time-condition-model";
import { TimeZoneModel } from "../../../../../../../shared/schedule/model/time-zone-model";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../../shared/util/tool";
import { DateTimeService } from "../date-time.service";
import { StartTimeChange, StartTimeData, StartTimeEditorComponent } from "../start-time-editor/start-time-editor.component";
import { TaskConditionChanges } from "../task-condition-pane.component";
import { TimeZoneValue, TimeZoneSelectComponent } from "../time-zone-select/time-zone-select-component";
import { MatOption } from "@angular/material/core";
import { MatSelect } from "@angular/material/select";
import { MatInput } from "@angular/material/input";
import { MatFormField, MatLabel, MatHint, MatError } from "@angular/material/form-field";
import { MatCard, MatCardContent } from "@angular/material/card";

@Component({
    selector: "em-weekly-condition-editor",
    templateUrl: "./weekly-condition-editor.component.html",
    styleUrls: ["./weekly-condition-editor.component.scss"],
    imports: [MatCard, MatCardContent, FormsModule, ReactiveFormsModule, StartTimeEditorComponent, TimeZoneSelectComponent, MatFormField, MatLabel, MatInput, MatHint, MatError, MatSelect, MatOption]
})
export class WeeklyConditionEditorComponent implements OnInit {
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

      this.form.get("interval").setValue(this._condition.interval);
      this.form.get("weekdays").setValue(this._condition.daysOfWeek);
      this.form.get("timeZone").setValue({
         timeZoneId: this._condition.timeZone || "",
         timeZoneLabel: this._condition.timeZoneLabel || ""
      } as TimeZoneValue);

      this.startTimeData = {
         startTime: this.dateTimeService.getStartTime(this._condition),
         timeRange: this._condition.timeRange,
         startTimeSelected: !this.timeRangeEnabled || !this._condition.timeRange
      };
      this.startTimeValid = !!this.startTimeData.startTime || !!this.startTimeData.timeRange;
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
         weekdays: [[], [Validators.required]],
         timeZone: [{ timeZoneId: "", timeZoneLabel: "" } as TimeZoneValue]
      });
   }

   ngOnInit() {
      if(!!this.startTimeData) {
         this.timeZoneEnabled = this.startTimeData.startTimeSelected;
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

   fireModelChanged(): void {
      const oldTZ = this.condition.timeZone;
      const tzValue = this.form.get("timeZone").value as TimeZoneValue;
      this.condition.interval = this.form.get("interval").value;
      this.condition.daysOfWeek = this.form.get("weekdays").value;
      this.condition.timeZone = tzValue?.timeZoneId || "";
      this.condition.timeZoneLabel = tzValue?.timeZoneLabel || "";

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
}
