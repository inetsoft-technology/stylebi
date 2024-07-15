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
import { Component, Input, OnInit } from "@angular/core";
import { IntervalPaneModel, IntervalLevel } from "../../model/interval-pane-model";
import { DateComparisonService } from "../../util/date-comparison.service";
import { DynamicValueModel, ValueTypes } from "../../model/dynamic-value-model";
import { XConstants } from "../../../common/util/xconstants";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";
import { DateComparisonUtil } from "./date-comparison-utill";
import { Tool } from "../../../../../../shared/util/tool";
import { FirstDayOfWeekService } from "../../../common/services/first-day-of-week.service";

@Component({
   selector: "date-comparison-interval-pane",
   templateUrl: "./date-comparison-interval-pane.component.html",
   styleUrls: ["./date-comparison-interval-pane.component.scss"]
})
export class DateComparisonIntervalPaneComponent implements OnInit{
   @Input() intervalPaneModel: IntervalPaneModel;
   @Input() variableValues: string[] = [];
   @Input() disable: boolean = false;
   @Input() standardPeriodLevel: DynamicValueModel;
   @Input() periodEndDay: DynamicValueModel;
   @Input() isCustomPeriod: boolean;
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;
   private firstDayOfWeek: number;
   intervalLevels = [{
         label: "_#(js:All)",
         value: "0"
      },
      {
         label: "_#(js:Year To Date)",
         value: IntervalLevel.YEAR_TO_DATE + ""
      },
      {
         label: "_#(js:Quarter To Date)",
         value: IntervalLevel.QUARTER_TO_DATE + ""
      },
      {
         label: "_#(js:Month To Date)",
         value: IntervalLevel.MONTH_TO_DATE + ""
      },
      {
         label: "_#(js:Week To Date)",
         value: IntervalLevel.WEEK_TO_DATE + ""
      },
      {
         label: "_#(js:Same Quarter)",
         value: IntervalLevel.SAME_QUARTER + ""
      },
      {
         label: "_#(js:Same Month)",
         value: IntervalLevel.SAME_MONTH + ""
      },
      {
         label: "_#(js:Same Week)",
         value: IntervalLevel.SAME_WEEK + ""
      },
      {
         label: "_#(js:Same Day)",
         value: IntervalLevel.SAME_DAY + ""
      }];
   granularities = [{
         label: "_#(js:Year)",
         value: IntervalLevel.YEAR + ""
      },
      {
         label: "_#(js:Quarter)",
         value: IntervalLevel.QUARTER + ""
      },
      {
         label: "_#(js:Month)",
         value: IntervalLevel.MONTH + ""
      },
      {
         label: "_#(js:Week)",
         value: IntervalLevel.WEEK + ""
      },
      {
         label: "_#(js:Day)",
         value: IntervalLevel.DAY + ""
      }];
   custom_granularities = [{
      label: "_#(js:All)",
      value: "0"
   }].concat(this.granularities);
   contextLevels = [{
         label: "_#(js:Year)",
         value: XConstants.YEAR_DATE_GROUP
      },
      {
         label: "_#(js:Quarter)",
         value: XConstants.QUARTER_DATE_GROUP
      },
      {
         label: "_#(js:Month)",
         value: XConstants.MONTH_DATE_GROUP
      },
      {
         label: "_#(js:Week)",
         value: XConstants.WEEK_DATE_GROUP
      }];

   constructor(private dateComparisonService: DateComparisonService,
               private firstDayOfWeekService: FirstDayOfWeekService) {
   }

   ngOnInit(): void {
      this.firstDayOfWeekService.getFirstDay().subscribe((model) => {
         this.firstDayOfWeek = model.javaFirstDay;
      });
      //init to update the value in order.
      this.visibleIntervalLevel;
      this.contextLevelValue;
      this.visibleGranularity;
   }

   get isEndDateDisable(): boolean {
      return this.intervalPaneModel.endDayAsToDate || this.disable;
   }

   get visibleIntervalLevel(): string {
      const levels = this.getIntervalLevels();

      if(this.intervalPaneModel.level.type == ValueTypes.VALUE) {
         const visibleLevel = levels.find(level => level.value === this.intervalPaneModel.level.value);
         const value = !!visibleLevel ? visibleLevel.value : levels[0].value;
         this.intervalPaneModel.level.value = value;

         return value;
      }
      else if(this.intervalPaneModel.level.type == ValueTypes.VARIABLE ||
              this.intervalPaneModel.level.type == ValueTypes.EXPRESSION)
      {
         return this.intervalPaneModel.level.value;
      }

      return levels[0].value;
   }

   get visibleGranularity(): string {
      const granularities = this.getGranularities();

      if(this.intervalPaneModel.granularity.type == ValueTypes.VALUE) {
         const visibleGranularity = granularities.find(granularity => granularity.value === this.intervalPaneModel.granularity.value);
         return !!visibleGranularity ? visibleGranularity.value : granularities[0].value;
      }
      else if(this.intervalPaneModel.granularity.type == ValueTypes.VARIABLE ||
              this.intervalPaneModel.granularity.type == ValueTypes.EXPRESSION)
      {
         return this.intervalPaneModel.granularity.value;
      }

      return granularities[0].value;
   }

   get contextLevelValue(): string {
      const levels = this.getContextLevels();
      let intervalContextLevel = this.intervalPaneModel.contextLevel;

      if(intervalContextLevel?.type == ValueTypes.VALUE) {
         const contextLevel = levels
            .find(level => parseInt(level.value, 10) === parseInt(intervalContextLevel.value, 10));
         const value = !!contextLevel ? contextLevel.value : levels[0].value;
         intervalContextLevel.value = value;

         return value;
      }
      else if(intervalContextLevel.type == ValueTypes.VARIABLE ||
         intervalContextLevel.type == ValueTypes.EXPRESSION)
      {
         return intervalContextLevel.value;
      }

      return levels[0].value;
   }

   getIntervalLevels(): Array<any> {
      const isValueType = this.standardPeriodLevel.type == ValueTypes.VALUE;

      if(this.isCustomPeriod) {
         return this.intervalLevels.filter(level => {
            return (Number(level.value) & IntervalLevel.SAME_DATE) != IntervalLevel.SAME_DATE;
         });
      }
      else if(this.standardPeriodLevel.type != ValueTypes.VALUE ||
         (isValueType && this.standardPeriodLevel.value == XConstants.YEAR_DATE_GROUP))
      {
         return this.intervalLevels;
      }
      else if(isValueType && this.standardPeriodLevel.value == XConstants.QUARTER_DATE_GROUP) {
         return this.intervalLevels.filter(level => {
            return (IntervalLevel.YEAR & Number(level.value)) != IntervalLevel.YEAR &&
               Number(level.value) != IntervalLevel.SAME_QUARTER;
         });
      }
      else if(isValueType && this.standardPeriodLevel.value == XConstants.MONTH_DATE_GROUP) {
         return this.intervalLevels.filter(level => {
            return (IntervalLevel.YEAR & Number(level.value)) != IntervalLevel.YEAR &&
               (IntervalLevel.QUARTER & Number(level.value)) != IntervalLevel.QUARTER &&
               Number(level.value) != IntervalLevel.SAME_MONTH;
         });
      }
      else if(isValueType && this.standardPeriodLevel.value == XConstants.WEEK_DATE_GROUP) {
         return this.intervalLevels.filter(level => {
            return (IntervalLevel.YEAR & Number(level.value)) != IntervalLevel.YEAR &&
               (IntervalLevel.QUARTER & Number(level.value)) != IntervalLevel.QUARTER &&
               (IntervalLevel.MONTH & Number(level.value)) != IntervalLevel.MONTH &&
               Number(level.value) != IntervalLevel.SAME_WEEK;
         });
      }
      else if(isValueType && this.standardPeriodLevel.value == XConstants.DAY_DATE_GROUP) {
         return this.intervalLevels.filter(level => {
            return (IntervalLevel.YEAR & Number(level.value)) != IntervalLevel.YEAR &&
               (IntervalLevel.QUARTER & Number(level.value)) != IntervalLevel.QUARTER &&
               (IntervalLevel.MONTH & Number(level.value)) != IntervalLevel.MONTH &&
               (IntervalLevel.WEEK & Number(level.value)) != IntervalLevel.WEEK &&
               Number(level.value) != IntervalLevel.SAME_DAY;
         });
      }
      else {
         return this.intervalLevels;
      }
   }

   getGranularities(): Array<any> {
      if(this.isCustomPeriod) {
         return this.custom_granularities;
      }

      if(this.intervalPaneModel.level.type != ValueTypes.VALUE || this.isCustomPeriod) {
         return this.granularities;
      }

      const level = this.intervalPaneModel.level.value;

      return this.granularities.filter(g => {
         switch(level) {
            case "0":
               return this.granularitiesAllIntervalVisible(this.standardPeriodLevel, g);
            case IntervalLevel.YEAR_TO_DATE + "":
               return true;
            case IntervalLevel.QUARTER_TO_DATE + "":
            case IntervalLevel.SAME_QUARTER + "":
               return g.value == IntervalLevel.QUARTER + "" ||
                  g.value == IntervalLevel.MONTH + "" ||
                  g.value == IntervalLevel.WEEK + "" ||
                  g.value == IntervalLevel.DAY + "";
            case IntervalLevel.MONTH_TO_DATE + "":
            case IntervalLevel.SAME_MONTH + "":
               return g.value == IntervalLevel.MONTH + "" ||
                  g.value == IntervalLevel.WEEK + "" ||
                  g.value == IntervalLevel.DAY + "";
            case IntervalLevel.WEEK_TO_DATE + "":
            case IntervalLevel.SAME_WEEK + "":
               return g.value == IntervalLevel.WEEK + "" ||
                  g.value == IntervalLevel.DAY + "";
            case IntervalLevel.SAME_DAY + "":
            default:
               return g.value == IntervalLevel.DAY + "";
         }
      });
   }

   granularitiesAllIntervalVisible(periodLevel: DynamicValueModel, granularity: any): boolean {
      const isValueType = periodLevel?.type == ValueTypes.VALUE;

      if(this.isCustomPeriod || !isValueType) {
         return true;
      }

      switch(periodLevel.value + "") {
         case XConstants.YEAR_DATE_GROUP + "":
            return true;
         case XConstants.QUARTER_DATE_GROUP + "":
            return granularity.value == IntervalLevel.QUARTER + "" ||
               granularity.value == IntervalLevel.MONTH + "" ||
               granularity.value == IntervalLevel.WEEK + "" ||
               granularity.value == IntervalLevel.DAY + "";
         case XConstants.MONTH_DATE_GROUP + "":
            return granularity.value == IntervalLevel.MONTH + "" ||
               granularity.value == IntervalLevel.WEEK + "" ||
               granularity.value == IntervalLevel.DAY + "";
         case XConstants.WEEK_DATE_GROUP + "":
            return granularity.value == IntervalLevel.WEEK + "" ||
               granularity.value == IntervalLevel.DAY + "";
         case XConstants.DAY_DATE_GROUP + "":
            return granularity.value == IntervalLevel.DAY + "";
      }

      return false;
   }

   getContextLevels(): Array<any> {
      const isValueType = this.standardPeriodLevel.type == ValueTypes.VALUE;
      const intervalValueType = this.intervalPaneModel.level.type == ValueTypes.VALUE;
      const intervalLevel =
         this.intervalLevelConvertToGroupLevel(this.intervalPaneModel.level.value);
      let isSameLevel = false;

      if(intervalValueType &&
         (parseInt(this.intervalPaneModel.level.value, 10) & IntervalLevel.SAME_DATE) ==
         IntervalLevel.SAME_DATE)
      {
         isSameLevel = true;
      }

      if((!isValueType || this.isCustomPeriod) && !intervalValueType) {
         return this.contextLevels;
      }
      else if((!isValueType || this.isCustomPeriod) && intervalValueType) {
         return this.contextLevels.filter(level => {
            return(intervalLevel < 0 || isSameLevel ? level.value > intervalLevel :
               level.value >= intervalLevel);
         });
      }
      else if((!this.isCustomPeriod && isValueType) && !intervalValueType) {
         return this.contextLevels.filter(level => {
            return level.value <= parseInt(this.standardPeriodLevel.value, 10);
         });
      }
      else {
         return this.contextLevels.filter(level => {
            return level.value <= parseInt(this.standardPeriodLevel.value, 10) &&
               (intervalLevel < 0 || isSameLevel ? level.value > intervalLevel :
               level.value >= intervalLevel);
         });
      }
   }

   intervalLevelConvertToGroupLevel(level: number): number {
      if((level & IntervalLevel.YEAR) == IntervalLevel.YEAR) {
         return XConstants.YEAR_DATE_GROUP;
      }
      else if((level & IntervalLevel.QUARTER) == IntervalLevel.QUARTER) {
         return XConstants.QUARTER_DATE_GROUP;
      }
      else if((level & IntervalLevel.MONTH) == IntervalLevel.MONTH) {
         return XConstants.MONTH_DATE_GROUP;
      }
      else if((level & IntervalLevel.WEEK) == IntervalLevel.WEEK) {
         return XConstants.WEEK_DATE_GROUP;
      }
      else if((level & IntervalLevel.DAY) == IntervalLevel.DAY) {
         return XConstants.DAY_DATE_GROUP;
      }

      return -1;
   }

   showEndDate(): boolean {
      return !this.isCustomPeriod && (this.intervalPaneModel.level.type != ValueTypes.VALUE ||
         (Number(this.intervalPaneModel.level.value) > IntervalLevel.TO_DATE ||
         this.intervalPaneModel.level.type != ValueTypes.VALUE));
   }

   showInclusive(): boolean {
      return (this.intervalPaneModel.level.value & IntervalLevel.TO_DATE) == IntervalLevel.TO_DATE
         || this.intervalPaneModel.level?.type != ValueTypes.VALUE;
   }

   updateLevelType(type: number): void {
      this.intervalPaneModel.level.type =
         this.dateComparisonService.getDateComparisonValueTypeStr(type);
   }

   updateGranularityType(type: number): void {
      this.intervalPaneModel.granularity.type =
         this.dateComparisonService.getDateComparisonValueTypeStr(type);
   }

   updateContextLevelType(type: number): void {
      this.intervalPaneModel.contextLevel.type =
         this.dateComparisonService.getDateComparisonValueTypeStr(type);
   }

   updateVisibleLevel(value: any): void {
      if(this.variableValues?.includes(value)) {
         this.intervalPaneModel.level.type = ValueTypes.VARIABLE;
      }

      this.intervalPaneModel.level.value = value;
      let granularities = this.getGranularities();
      this.intervalPaneModel.granularity.value = granularities[0].value;
   }

   updateVisibleGranularity(value: any): void {
      if(this.variableValues?.includes(value)) {
         this.intervalPaneModel.granularity.type = ValueTypes.VARIABLE;
      }

      this.intervalPaneModel.granularity.value = value;
   }

   updateContextLevel(value: any): void {
      if(this.variableValues?.includes(value)) {
         this.intervalPaneModel.contextLevel.type = ValueTypes.VARIABLE;
      }

      this.intervalPaneModel.contextLevel.value = value;
   }

   isValidInterval(): boolean {
      return !this.showEndDate() || this.intervalPaneModel.endDayAsToDate || this.verifyEndDate();
   }

   verifyEndDate(): boolean {
      return this.dateComparisonService.isValidDate(this.intervalPaneModel.intervalEndDate.value);
   }

   get toDateLabel(): string {
      switch(this.intervalPaneModel.level.value) {
         case IntervalLevel.YEAR_TO_DATE + "":
            return "_#(js:date.comparison.xToDate.year)";
         case IntervalLevel.QUARTER_TO_DATE + "":
            switch(this.intervalPaneModel.contextLevel.value) {
               case XConstants.YEAR_DATE_GROUP:
                  return "_#(js:date.comparison.xToDate.quarterByYear)";
               default:
                  return "_#(js:date.comparison.xToDate.quarter)";
            }
         case IntervalLevel.MONTH_TO_DATE + "":
            switch(this.intervalPaneModel.contextLevel.value) {
               case XConstants.YEAR_DATE_GROUP:
                  return "_#(js:date.comparison.xToDate.monthByYear)";
               case XConstants.QUARTER_DATE_GROUP:
                  return "_#(js:date.comparison.xToDate.monthByQuarter)";
               default:
                  return "_#(js:date.comparison.xToDate.month)";
            }
         case IntervalLevel.WEEK_TO_DATE + "":
            switch(this.intervalPaneModel.contextLevel.value) {
               case XConstants.YEAR_DATE_GROUP:
                  return "_#(js:date.comparison.xToDate.weekByYear)";
               case XConstants.QUARTER_DATE_GROUP:
                  return "_#(js:date.comparison.xToDate.weekByQuarter)";
               case XConstants.MONTH_DATE_GROUP:
                  return "_#(js:date.comparison.xToDate.weekByMonth)";
               default:
                  return "_#(js:date.comparison.xToDate.week)";
            }
         case IntervalLevel.SAME_QUARTER + "":
            return "_#(js:date.comparison.sameX.quarterByYear)";
         case IntervalLevel.SAME_MONTH + "":
            switch(this.intervalPaneModel.contextLevel.value) {
               case XConstants.QUARTER_DATE_GROUP:
                  return "_#(js:date.comparison.sameX.monthByQuarter)";
               default:
                  return "_#(js:date.comparison.sameX.monthByYear)";
            }
         case IntervalLevel.SAME_WEEK + "":
            switch(this.intervalPaneModel.contextLevel.value) {
               case XConstants.QUARTER_DATE_GROUP:
                  return "_#(js:date.comparison.sameX.weekByQuarter)";
               case XConstants.MONTH_DATE_GROUP:
                  return "_#(js:date.comparison.sameX.weekByMonth)";
               default:
                  return "_#(js:date.comparison.sameX.weekByYear)";
            }
         case IntervalLevel.SAME_DAY + "":
            switch(this.intervalPaneModel.contextLevel.value) {
               case XConstants.QUARTER_DATE_GROUP:
                  return "_#(js:date.comparison.sameX.dayByQuarter)";
               case XConstants.MONTH_DATE_GROUP:
                  return "_#(js:date.comparison.sameX.dayByMonth)";
               case XConstants.WEEK_DATE_GROUP:
                  return "_#(js:date.comparison.sameX.dayByWeek)";
               default:
                  return "_#(js:date.comparison.sameX.dayByYear)";
            }
         default:
            return "_#(js:date.comparison.unknown)";
      }

      return null;
   }

   toDateIsValue(): boolean {
      return this.intervalPaneModel.intervalEndDate.type == ValueTypes.VALUE;
   }

   get toDate(): Date {
      let endDateValue = this.intervalPaneModel.endDayAsToDate ? this.periodEndDay :
          this.intervalPaneModel.intervalEndDate;

      if(endDateValue == null || endDateValue.type && endDateValue.type != ValueTypes.VALUE) {
         return null;
      }

      if(!endDateValue.value) {
         return new Date();
      }

      let timeInstant = DateTypeFormatter.toTimeInstant(endDateValue.value, DateTypeFormatter.ISO_8601_DATE_FORMAT);

      if(!timeInstant) {
         return null;
      }

      let resultDate = DateTypeFormatter.timeInstantToDate(timeInstant);

      if(!resultDate || isNaN(resultDate.getTime())) {
         return null;
      }

      return resultDate;
   }

   getToDateLabel(): string {
      // label for quarter/week hard to understand. ignore for now pending further consideration.
      /*
      if(this.intervalPaneModel.level.type != ValueTypes.VALUE) {
         return null;
      }

      let intervalToDate = this.toDate;

      if(!intervalToDate) {
         return null;
      }

      switch(this.intervalPaneModel.level.value) {
         case IntervalLevel.YEAR_TO_DATE + "":
            return Tool.formatCatalogString("_#(js:date.comparison.xToDate.year.label)",
                [DateTypeFormatter.format(intervalToDate, "MMM dd")]);
         case IntervalLevel.QUARTER_TO_DATE + "":
            switch(this.intervalPaneModel.contextLevel.value) {
               case XConstants.YEAR_DATE_GROUP:
                  return Tool.formatCatalogString("_#(js:date.comparison.xToDate.quarterByYear.label)",
                      [DateTypeFormatter.format(intervalToDate, "Qo"), DateComparisonUtil.monthOfQuarter(intervalToDate),
                         DateTypeFormatter.format(intervalToDate, "Do")]);
               default:
                  return Tool.formatCatalogString("_#(js:date.comparison.xToDate.quarter.label)",
                      [DateComparisonUtil.monthOfQuarter(intervalToDate),
                         DateTypeFormatter.format(intervalToDate, "Do")]);
            }
         case IntervalLevel.MONTH_TO_DATE + "":
            switch(this.intervalPaneModel.contextLevel.value) {
               case XConstants.YEAR_DATE_GROUP:
                  return Tool.formatCatalogString("_#(js:date.comparison.xToDate.monthByYear.label)",
                      [DateTypeFormatter.format(intervalToDate, "MMM"),
                         DateTypeFormatter.format(intervalToDate, "Do")]);
               case XConstants.QUARTER_DATE_GROUP:
                  return Tool.formatCatalogString("_#(js:date.comparison.xToDate.monthByQuarter.label)",
                      [DateComparisonUtil.monthOfQuarter(intervalToDate),
                         DateTypeFormatter.format(intervalToDate, "Do")]);
               default:
                  return Tool.formatCatalogString("_#(js:date.comparison.xToDate.month.label)",
                   [DateTypeFormatter.format(intervalToDate, "Do")]);;
            }
         case IntervalLevel.WEEK_TO_DATE + "":
            switch(this.intervalPaneModel.contextLevel.value) {
               case XConstants.YEAR_DATE_GROUP:
                  return Tool.formatCatalogString("_#(js:date.comparison.xToDate.weekByYear.label)",
                      [DateTypeFormatter.format(intervalToDate, "Wo"),
                         DateTypeFormatter.format(intervalToDate, "dddd", false)]);
               case XConstants.QUARTER_DATE_GROUP:
                  return Tool.formatCatalogString("_#(js:date.comparison.xToDate.weekByQuarter.label)",
                      [DateComparisonUtil.weekOfQuarter(intervalToDate, this.firstDayOfWeek),
                         DateTypeFormatter.format(intervalToDate, "dddd", false)]);
               case XConstants.MONTH_DATE_GROUP:
                  return Tool.formatCatalogString("_#(js:date.comparison.xToDate.weekByMonth.label)",
                      [DateComparisonUtil.weekOfMonth(intervalToDate, this.firstDayOfWeek),
                         DateTypeFormatter.format(intervalToDate, "dddd", false)]);
               default:
                  return Tool.formatCatalogString("_#(js:date.comparison.xToDate.week.label)",
                      [DateTypeFormatter.format(intervalToDate, "dddd", false)]);
            }
         case IntervalLevel.SAME_QUARTER + "":
            return Tool.formatCatalogString("_#(js:date.comparison.sameX.quarterByYear.label)",
                [DateTypeFormatter.format(intervalToDate, "Qo")]);
         case IntervalLevel.SAME_MONTH + "":
            switch(this.intervalPaneModel.contextLevel.value) {
               case XConstants.QUARTER_DATE_GROUP:
                  return Tool.formatCatalogString("_#(js:date.comparison.sameX.monthByQuarter.label)",
                      [DateComparisonUtil.monthOfQuarter(intervalToDate)]);
               default:
                  return Tool.formatCatalogString("_#(js:date.comparison.sameX.monthByYear.label)",
                      [DateTypeFormatter.format(intervalToDate, "MMM")]);
            }
         case IntervalLevel.SAME_WEEK + "":
            switch(this.intervalPaneModel.contextLevel.value) {
               case XConstants.QUARTER_DATE_GROUP:
                  return Tool.formatCatalogString("_#(js:date.comparison.sameX.weekByQuarter.label)",
                      [DateComparisonUtil.weekOfQuarter(intervalToDate, this.firstDayOfWeek)]);
               case XConstants.MONTH_DATE_GROUP:
                  return Tool.formatCatalogString("_#(js:date.comparison.sameX.weekByMonth.label)",
                      [DateComparisonUtil.weekOfMonth(intervalToDate, this.firstDayOfWeek)]);
               default:
                  return Tool.formatCatalogString("_#(js:date.comparison.sameX.weekByYear.label)",
                      [DateTypeFormatter.format(intervalToDate, "Wo")]);
            }
         case IntervalLevel.SAME_DAY + "":
            switch(this.intervalPaneModel.contextLevel.value) {
               case XConstants.QUARTER_DATE_GROUP:
                  return Tool.formatCatalogString("_#(js:date.comparison.sameX.dayByQuarter.label)",
                      [DateComparisonUtil.dayOfQuarter(intervalToDate)]);
               case XConstants.MONTH_DATE_GROUP:
                  return Tool.formatCatalogString("_#(js:date.comparison.sameX.dayByMonth.label)",
                      [DateTypeFormatter.format(intervalToDate, "Do")]);
               case XConstants.WEEK_DATE_GROUP:
                  return Tool.formatCatalogString("_#(js:date.comparison.sameX.dayByWeek.label)",
                      [DateTypeFormatter.format(intervalToDate, "dddd", false)]);
               default:
                  return Tool.formatCatalogString("_#(js:date.comparison.sameX.dayByYear.label)",
                      [DateTypeFormatter.format(intervalToDate, "DDDo")]);
            }
      }
      */

      return null;
   }
}
