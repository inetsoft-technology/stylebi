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
import { TaskActionPaneModel } from "./task-action-pane-model";
import { TaskOptionsPaneModel } from "./task-options-pane-model";
import { TaskConditionPaneModel } from "./task-condition-pane-model";
import { TimeRange } from "./time-condition-model";
import { TimeZoneModel } from "./time-zone-model";

export interface ScheduleTaskDialogModel {
   name: string;
   label: string;
   taskDefaultTime: boolean;
   timeZone: string;
   timeZoneOptions?: TimeZoneModel[];
   taskActionPaneModel: TaskActionPaneModel;
   taskConditionPaneModel: TaskConditionPaneModel;
   taskOptionsPaneModel: TaskOptionsPaneModel;
   internalTask: boolean;
   timeRanges: TimeRange[];
   startTimeEnabled: boolean;
   timeRangeEnabled: boolean;
}
