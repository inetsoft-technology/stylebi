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
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from "@angular/core";
import {
   ScheduleConditionModel,
   ScheduleConditionModelType
} from "../../../../../../shared/schedule/model/schedule-condition-model";
import { TaskConditionPaneModel } from "../../../../../../shared/schedule/model/task-condition-pane-model";
import {
   TimeConditionModel,
   TimeConditionType,
   TimeRange
} from "../../../../../../shared/schedule/model/time-condition-model";
import { TimeZoneModel } from "../../../../../../shared/schedule/model/time-zone-model";

export interface TaskConditionChanges {
   valid: boolean;
   model: ScheduleConditionModel;
}

export interface TaskConditionType {
   label: string;
   type: ScheduleConditionModelType;
   subtype?: TimeConditionType;
   excludeCycle?: boolean;
}

@Component({
   selector: "em-task-condition-pane",
   templateUrl: "./task-condition-pane.component.html",
   styleUrls: ["./task-condition-pane.component.scss"]
})
export class TaskConditionPaneComponent implements OnChanges {
   @Input() condition: ScheduleConditionModel;
   @Input() originalTaskName: string;
   @Input() timeZone: string;
   @Input() timeZoneOptions: TimeZoneModel[];
   @Input() taskDefaultTime: boolean;
   @Input() cycle = false;
   @Input() timeRanges: TimeRange[] = [];
   @Input() startTimeEnabled = true;
   @Input() timeRangeEnabled = true;
   @Output() modelChanged = new EventEmitter<TaskConditionChanges>();

   @Input()
   get model(): TaskConditionPaneModel {
      return this._model;
   }

   set model(value: TaskConditionPaneModel) {
      this._model = value;
   }

   get conditionTypes(): TaskConditionType[] {
      let conditionTypes = this._conditionTypes;

      if(this.cycle) {
         conditionTypes = conditionTypes.filter(t => !t.excludeCycle &&
            !(t.subtype === TimeConditionType.EVERY_HOUR && !this.startTimeEnabled));
      }
      else if(!this.startTimeEnabled) {
         conditionTypes = conditionTypes.filter((t) =>
            t.type !== "TimeCondition" ||
            t.subtype !== TimeConditionType.EVERY_HOUR && t.subtype !== TimeConditionType.AT);
      }

      return conditionTypes;
   }

   selectedConditionType: TaskConditionType;

   private _model: TaskConditionPaneModel;
   readonly _conditionTypes: TaskConditionType[] = [
      { label: "_#(js:Daily)", type: "TimeCondition", subtype: TimeConditionType.EVERY_DAY },
      { label: "_#(js:Weekly)", type: "TimeCondition", subtype: TimeConditionType.EVERY_WEEK },
      { label: "_#(js:Monthly)", type: "TimeCondition", subtype: TimeConditionType.EVERY_MONTH },
      { label: "_#(js:Hourly)", type: "TimeCondition", subtype: TimeConditionType.EVERY_HOUR },
      { label: "_#(js:Run Once)", type: "TimeCondition", subtype: TimeConditionType.AT, excludeCycle: true },
      { label: "_#(js:Chained)", type: "CompletionCondition", excludeCycle: true }
   ];

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.hasOwnProperty("condition")) {
         if(this.condition) {
            this.selectedConditionType = this.conditionTypes.find((type) => {
               if(type.type === this.condition.conditionType) {
                  if(type.type === "TimeCondition") {
                     return type.subtype === (<TimeConditionModel> this.condition).type;
                  }

                  return true;
               }

               return false;
            });

            this.condition = Object.assign({}, this.condition);
         }
         else {
            this.selectedConditionType = this.conditionTypes[0];
            this.condition = <TimeConditionModel> {
               conditionType: this.selectedConditionType.type,
               type: this.selectedConditionType.subtype,
               timeZoneOffset: this.model ? this.model.timeZoneOffset : 0 || 0
            };
         }
      }
   }

   changeConditionType(): void {
      if(this.selectedConditionType.type === "TimeCondition") {
         this.condition = this.getDefaultTimeConditionModel(this.selectedConditionType.subtype);
         (<TimeConditionModel>this.condition).timeZone = this.timeZoneOptions[0].timeZoneId;
      }
      else {
         this.condition = {
            label: "_#(js:New Condition)",
            conditionType: this.selectedConditionType.type
         };
      }

      const valid = this.selectedConditionType.type === "TimeCondition" &&
         TimeConditionType.AT == this.selectedConditionType.subtype ||
         TimeConditionType.EVERY_DAY == this.selectedConditionType.subtype;

      this.fireModelChanged(valid);
   }

   private getDefaultTimeConditionModel(optionType: any): TimeConditionModel {
      return <TimeConditionModel> {
         label: "_#(js:New Condition)",
         hour: 1,
         minute: 30,
         second: 0,
         interval: optionType == TimeConditionType.EVERY_DAY ? 1 : null,
         weekdayOnly: false,
         type: optionType,
         conditionType: "TimeCondition",
         daysOfWeek: [],
         monthsOfYear: [],
         monthlyDaySelected: true,
         date: new Date().getTime(),
         timeZoneOffset: this.model ? this.model.timeZoneOffset : 0 || 0
      };
   }

   onModelChanged(value: TaskConditionChanges) {
      this.condition = value.model;
      this.fireModelChanged(value.valid);
   }

   fireModelChanged(valid: boolean): void {
      this.modelChanged.emit({
         valid,
         model: this.condition
      });
   }

   get showMeridian(): boolean {
      return this.model && this.model.twelveHourSystem;
   }
}
