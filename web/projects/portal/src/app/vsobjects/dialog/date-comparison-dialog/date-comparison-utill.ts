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
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";
import dayjs from "dayjs";
import dayOfYear from "dayjs/plugin/dayOfYear";
import weekday from "dayjs/plugin/weekday";

dayjs.extend(dayOfYear);
dayjs.extend(weekday);

export namespace DateComparisonUtil {
    export function monthOfQuarter(dateValue: Date): string {
        const month = parseInt(DateTypeFormatter.format(dateValue, "M"), 10);

       switch (month % 3) {
          case 1:
             return "_#(js:1st)";
          case 2:
             return "_#(js:2nd)";
          case 0:
             return "_#(js:3rd)";
          default:
             return null;
       }
    }

    export function weekOfQuarter(dateValue: Date, firstDayOfWeek: number = 0): string {
        return weekOfQuarterOrMonth(dateValue, true, firstDayOfWeek);
    }

    export function weekOfMonth(dateValue: Date, firstDayOfWeek: number = 0): string {
        return weekOfQuarterOrMonth(dateValue, false, firstDayOfWeek);
    }

    function weekOfQuarterOrMonth(dateValue: Date, quarter: boolean, firstDayOfWeek: number = 0): string {
        let date = dayjs(dateValue);
        let toDateDayOfYear = date.dayOfYear();

        if(quarter) {
            date.add(-date.get("month") % 3, "month");
        }

        date.date(1);

        // the day count of fist day to the fist week start.
        let weekOffset = (firstDayOfWeek - date.weekday() + 7) % 7;
        let dayOfLevelStart = date.dayOfYear();
        let dayOffset = toDateDayOfYear - dayOfLevelStart + 1;
        let weekNumber = 0;

        if(dayOffset - weekOffset <= 0) {
            weekNumber = 1;
        }
        else {
            weekNumber += (weekOffset > 0 ? 1 : 0);
            weekNumber += Math.floor((dayOffset - weekOffset) / 7);
            weekNumber += Math.floor((dayOffset - weekOffset) % 7) > 0 ? 1 : 0;
        }

        return numberToOrdinal(weekNumber);
    }

    export function dayOfQuarter(dateValue: Date): string {
        let date = dayjs(dateValue);
        let endDayOfYear = date.dayOfYear();
        date.add(-date.get("month") % 3, "month");
        date.date(1);
        let startDayOfYear = date.dayOfYear();

        return numberToOrdinal(endDayOfYear - startDayOfYear + 1);
    }

    export function numberToOrdinal(n: number) {
        let suffix = ["th", "st", "nd", "rd", "th"];
        let ord = n < 21 ? (n < 4 ? suffix[n] : suffix[0]) : (n % 10 > 4 ? suffix[0] : suffix[n % 10]);

        return n + ord;
    }
}
