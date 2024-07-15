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
import { AddParameterDialogModel } from "../../../../../shared/schedule/model/add-parameter-dialog-model";
import { CompletionConditionModel } from "../../../../../shared/schedule/model/completion-condition-model";
import {
   ScheduleConditionModel,
   ScheduleConditionModelType
} from "../../../../../shared/schedule/model/schedule-condition-model";
import { TimeConditionModel, TimeConditionType } from "../../../../../shared/schedule/model/time-condition-model";
import { Tool } from "../../../../../shared/util/tool";
import { LocalStorage } from "./local-storage.util";

// Taps into conditions stored by reports into local storage.
const localStorageKey: string = "inetsoft_scheduleDefaultCondition";

declare var window: any;

/**
 * Convert 12.3 ScheduleConditionModel to a report friendly json model.
 * @param model ScheduleConditionModel
 * @returns json string.
 */
function convertFromCondition(model: ScheduleConditionModel): string {
   const conditionType = model.conditionType;
   let newModel: any = { conditionType };

   if(conditionType == "CompletionCondition") {
      const completion: CompletionConditionModel = model as CompletionConditionModel;
      newModel["task"] = Tool.byteEncode(completion.taskName);
   }
   else {
      newModel = {};
      const properties: string[] = Object.getOwnPropertyNames(model);
      const propertyMap: any = {
         "hourEnd": "hour_end",
         "minuteEnd": "minute_end",
         "conditionType": "type",
         "type": "timeType",
         "secondEnd": "second_end",
         "weekdayOnly": "weekday",
         "timeZoneOffset": "tzoffset"
      };

      for(let i = 0; i < properties.length; i++) {
         const key: string = propertyMap[properties[i]] ?
            propertyMap[properties[i]] : properties[i];

         if(key === "dayOfMonth") {
            newModel[key] = Number(model[properties[i]]);
         }
         else {
            newModel[key] = model[properties[i]];
         }
      }

      newModel.time = newModel.date;
   }

   return JSON.stringify(newModel);
}

/**
 * Convert report friendly json structure for conditions to 12.3 ScheduleConditionModel
 * @param json string.
 * @returns model ScheduleConditionModel
 */
function convertToCondition(json: string): ScheduleConditionModel {
   let condition: any = JSON.parse(json);
   const conditionType: ScheduleConditionModelType = condition.conditionType;
   let model: any = { conditionType };

   if(conditionType === "CompletionCondition") {
      const completion: CompletionConditionModel = model as CompletionConditionModel;
      completion.taskName = Tool.byteDecode(condition.task);
   }
   else {
      const timeCondition: TimeConditionModel = {
         label: "_#(js:New Time Condition)",
         hour: 1,
         minute: 30,
         dayOfMonth: 0,
         dayOfWeek: 0,
         weekOfMonth: 0,
         type: TimeConditionType.EVERY_DAY,
         interval: 1,
         weekdayOnly: false,
         daysOfWeek: [],
         monthsOfYear: [],
         monthlyDaySelected: false,
         conditionType: "TimeCondition"
      };
      const properties: string[] = Object.getOwnPropertyNames(condition);
      const propertyMap: any = {
         "hour_end": "hourEnd",
         "minute_end": "minuteEnd",
         "type": "conditionType",
         "timeType": "type",
         "second_end": "secondEnd",
         "weekday": "weekdayOnly",
         "time": "date",
         "tzoffset": "timeZoneOffset"
      };

      for(let i = 0; i < properties.length; i++) {
         const key: string = propertyMap[properties[i]] ?
            propertyMap[properties[i]] : properties[i];
         timeCondition[key] = condition[properties[i]];

         // In reports these end up string, need to convert them to numbers
         if(key == "monthsOfYear" || key == "daysOfWeek") {
            const items: any[] = condition[properties[i]];
            timeCondition[key] = [];

            for(let j = 0; j < items.length; j++) {
               timeCondition[key][j] = Number(items[j]);
            }
         }
      }

      timeCondition.monthlyDaySelected = !!condition.dayOfMonth;
      model = timeCondition;
   }

   return model as ScheduleConditionModel;
}

/**
 * Store condition model in local storage in a report friendly format.
 * @param model ScheduleConditionModel
 */
export function storeCondition(model: ScheduleConditionModel): void {
   LocalStorage.setItem(localStorageKey, convertFromCondition(model));
}

/**
 * Get condition model from local storage if available.
 * @returns model ScheduleConditionModel
 */
export function getStoredCondition(): ScheduleConditionModel {
   const json: any = LocalStorage.getItem(localStorageKey);
   return json ? convertToCondition(json) : null;
}
