/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { DynamicValueModel } from "./dynamic-value-model";

export interface IntervalPaneModel {
   level: DynamicValueModel;
   granularity: DynamicValueModel;
   endDayAsToDate: boolean;
   intervalEndDate: DynamicValueModel;
   inclusive: boolean;
   contextLevel: DynamicValueModel;
}

export const enum IntervalLevel {
   DAY = 0x1,
   WEEK = 0x2,
   MONTH = 0x4,
   QUARTER = 0x8,
   YEAR = 0x10,

   TO_DATE = 0x20,
   WEEK_TO_DATE = TO_DATE | WEEK,
   MONTH_TO_DATE = TO_DATE | MONTH,
   QUARTER_TO_DATE = TO_DATE | QUARTER,
   YEAR_TO_DATE = TO_DATE | YEAR,

   SAME_DATE = 0x40,
   SAME_DAY = SAME_DATE | DAY,
   SAME_WEEK = SAME_DATE | WEEK,
   SAME_MONTH = SAME_DATE | MONTH,
   SAME_QUARTER = SAME_DATE | QUARTER
}
