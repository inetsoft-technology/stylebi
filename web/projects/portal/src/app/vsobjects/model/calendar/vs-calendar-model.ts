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
import { VSObjectModel } from "../vs-object-model";
import { VSFormatModel } from "../vs-format-model";
import { CurrentDateModel, SelectedDateModel } from "./current-date-model";
import { CalendarRangeModel } from "./calendar-range-model";

export interface VSCalendarModel extends VSObjectModel {
   selectedDateFormat: string;
   titleFormat: VSFormatModel;
   calendarTitleFormat: VSFormatModel;
   monthFormat: VSFormatModel;
   yearFormat: VSFormatModel;
   title: string;
   titleVisible: boolean;
   dropdownCalendar: boolean;
   doubleCalendar: boolean;
   yearView: boolean;
   showName: boolean;
   daySelection: boolean;
   singleSelection: boolean;
   submitOnChange: boolean;
   period: boolean;
   dates1: SelectedDateModel[];
   dates2: SelectedDateModel[];
   monthNames: string[];
   weekNames: string[];
   dayNames: string[];
   calendarTitleView1: string;
   calendarTitleView2: string;
   range: CalendarRangeModel;
   rangeRefreshRequired: boolean;
   currentDate1: CurrentDateModel;
   currentDate2: CurrentDateModel;
   calendarsShown?: boolean;
   multiSelect?: boolean;
   comparisonVisible: boolean;
}
