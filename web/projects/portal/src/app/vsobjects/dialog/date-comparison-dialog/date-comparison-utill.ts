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
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";
import { Tool } from "../../../../../../shared/util/tool";
import { CustomPeriodPaneModel } from "../../model/custom-period-pane-model";
import { DateComparisonPaneModel } from "../../model/date-comparison-pane-model";
import { DynamicValueModel, ValueTypes } from "../../model/dynamic-value-model";
import { IntervalLevel, IntervalPaneModel } from "../../model/interval-pane-model";
import { StandardPeriodPaneModel } from "../../model/standard-period-pane-model";
import { DateComparisonService } from "../../util/date-comparison.service";
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

    // Validity rules of the date comparison panes. They read only the model, so the
    // dialog footer can evaluate them on any change detection pass (Bug #80818), and the
    // panes use the same rules for their own error messages.

    export function isInvalidStandardPeriodPreCount(model: StandardPeriodPaneModel): boolean {
        return parseInt(model.preCount.value, 10) < 0 && !Tool.isDynamic(model.preCount.value);
    }

    export function isValidStandardPeriodEndDay(model: StandardPeriodPaneModel,
                                                service: DateComparisonService): boolean
    {
        return model.toDayAsEndDay || service.isValidDate(model.endDay.value);
    }

    export function isValidStandardPeriod(model: StandardPeriodPaneModel,
                                          service: DateComparisonService): boolean
    {
        return !isInvalidStandardPeriodPreCount(model) && isValidStandardPeriodEndDay(model, service);
    }

    export function isValidCustomPeriodDates(model: CustomPeriodPaneModel,
                                             service: DateComparisonService): boolean
    {
        let dynamicValue: DynamicValueModel[] = [];

        model.datePeriods.forEach(datePeriod => {
            dynamicValue.push(datePeriod.start);
            dynamicValue.push(datePeriod.end);
        });

        return !dynamicValue.some(value => !service.isValidDate(value.value));
    }

    export function isValidCustomPeriod(model: CustomPeriodPaneModel,
                                        service: DateComparisonService): boolean
    {
        return isValidCustomPeriodDates(model, service) &&
           !service.hasOverlappingFixedPeriods(model.datePeriods);
    }

    export function showIntervalEndDate(model: IntervalPaneModel, customPeriod: boolean): boolean {
        return !customPeriod && (model.level.type != ValueTypes.VALUE ||
           (Number(model.level.value) > IntervalLevel.TO_DATE ||
           model.level.type != ValueTypes.VALUE));
    }

    export function isValidInterval(model: IntervalPaneModel, customPeriod: boolean,
                                    service: DateComparisonService): boolean
    {
        return !showIntervalEndDate(model, customPeriod) || model.endDayAsToDate ||
           service.isValidDate(model.intervalEndDate.value);
    }

    export function isValidDateComparison(model: DateComparisonPaneModel,
                                          service: DateComparisonService): boolean
    {
        const periodPaneModel = model.periodPaneModel;
        const custom = periodPaneModel.custom;
        let validPeriod: boolean;

        if(custom) {
            const customModel = periodPaneModel.customPeriodPaneModel;
            // the custom periods pane adds two blank (invalid) periods on init when there are
            // none, so an empty list is treated as invalid instead of vacuously valid.
            validPeriod = !!customModel?.datePeriods?.length &&
               isValidCustomPeriod(customModel, service);
        }
        else {
            validPeriod = isValidStandardPeriod(periodPaneModel.standardPeriodPaneModel, service);
        }

        return validPeriod && isValidInterval(model.intervalPaneModel, custom, service);
    }
}
