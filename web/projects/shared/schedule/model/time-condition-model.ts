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
import { ResourcePermissionModel } from "../../../em/src/app/settings/security/resource-permission/resource-permission-model";
import { ScheduleConditionModel } from "./schedule-condition-model";

export interface TimeRange {
   name: string;
   label?: string;
   startTime: string;
   endTime: string;
   defaultRange: boolean;
   modified?: boolean;
   permissions?: ResourcePermissionModel;
}

export interface TimeConditionModel extends ScheduleConditionModel {
   hour?: number;
   minute?: number;
   second?: number;
   hourEnd?: number;
   minuteEnd?: number;
   secondEnd?: number;
   dayOfMonth?: number;
   dayOfWeek?: number;
   weekOfMonth?: number;
   type: number;
   interval?: number;
   hourlyInterval?: number;
   weekdayOnly?: boolean;
   daysOfWeek?: number[];
   monthsOfYear?: number[];
   monthlyDaySelected?: boolean;
   date?: number;
   dateEnd?: number;
   timeZone?: string;
   timeZoneOffset?: number;
   timeRange?: TimeRange;
   changed?: boolean;
}

export enum TimeConditionType {
   AT = 0,
   EVERY_DAY = 1,
   DAY_OF_MONTH = 2,
   DAY_OF_WEEK = 3,
   WEEK_OF_MONTH = 4,
   WEEK_OF_YEAR = 5,
   EVERY_WEEK = 6,
   EVERY_MONTH = 7,
   EVERY_HOUR = 8,
   LAST_DAY_OF_MONTH = -1,
}
