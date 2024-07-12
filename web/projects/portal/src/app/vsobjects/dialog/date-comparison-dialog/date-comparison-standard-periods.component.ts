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
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { Tool } from "../../../../../../shared/util/tool";
import { XConstants } from "../../../common/util/xconstants";
import { ValueMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { DateComparisonService } from "../../util/date-comparison.service";
import { StandardPeriodPaneModel } from "../../model/standard-period-pane-model";
import { ValueTypes } from "../../model/dynamic-value-model";
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";
import { DateComparisonUtil } from "./date-comparison-utill";

@Component({
   selector: "date-comparison-standard-periods",
   templateUrl: "./date-comparison-standard-periods.component.html",
   styleUrls: ["./date-comparison-standard-periods.component.scss"]
})
export class DateComparisonStandardPeriodsComponent implements OnChanges {
   @Input() standardPeriodPaneModel: StandardPeriodPaneModel;
   @Input() variableValues: string[] = [];
   @Input() disable: boolean = false;
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;
   @Input() toDateDisabled: boolean = false;
   @Input() intervalEndDate: any = null;
   @Input() weekly: boolean = false;
   @Output() validChange = new EventEmitter<boolean>();
   mode: ValueMode = ValueMode.NUMBER;
   standardPeriodLevels = [{
         label: "_#(js:Years)",
         value: XConstants.YEAR_DATE_GROUP + ""
      },
      {
         label: "_#(js:Quarters)",
         value: XConstants.QUARTER_DATE_GROUP + ""
      },
      {
         label: "_#(js:Months)",
         value: XConstants.MONTH_DATE_GROUP + ""
      },
      {
         label: "_#(js:Weeks)",
         value: XConstants.WEEK_DATE_GROUP + ""
      },
      {
         label: "_#(js:Days)",
         value: XConstants.DAY_DATE_GROUP + ""
      }];

   constructor(private dateComparisonService: DateComparisonService) {
   }

   ngOnChanges(): void {
      this.validChange.emit(this.isValid());
   }

   get visibleStandardPeriodLevel(): string {
      if(this.standardPeriodPaneModel.dateLevel.type == ValueTypes.VALUE) {
         return this.standardPeriodLevels.some(level => level.value === this.standardPeriodPaneModel.dateLevel.value) ?
            this.standardPeriodPaneModel.dateLevel.value : this.standardPeriodLevels[0].value;
      }
      else if(this.standardPeriodPaneModel.dateLevel.type == ValueTypes.VARIABLE ||
              this.standardPeriodPaneModel.dateLevel.type == ValueTypes.EXPRESSION)
      {
         return this.standardPeriodPaneModel.dateLevel.value;
      }

      return this.standardPeriodLevels[0].value;
   }

   get toDateVisible(): boolean {
      let dateLevel = this.standardPeriodPaneModel?.dateLevel;

      if(!!dateLevel && dateLevel.type == ValueTypes.VALUE &&
         "" + dateLevel.value === "" + XConstants.DAY_DATE_GROUP)
      {
         return false;
      }

      return !!this.toDateLabel;
   }

   get toDateLabel(): string {
      if(this.standardPeriodPaneModel.dateLevel.type == ValueTypes.EXPRESSION ||
         this.standardPeriodPaneModel.dateLevel.type == ValueTypes.VARIABLE)
      {
         return "_#(js:date.comparison.toRangeEnd)";
      }

      const dayOfWeek = this.weekly ? "&nbsp;<b>(" +
         DateTypeFormatter.format(this.endDate, "ddd", false) + ")</b>&nbsp;" : "";
      switch(this.standardPeriodPaneModel.dateLevel.value) {
         case XConstants.QUARTER_DATE_GROUP + "": {
            return this.endDate ? Tool.formatCatalogString("_#(js:date.comparison.toDateQuarter)",
                                                           [this.monthOfQuarter(), DateTypeFormatter.format(this.endDate, "Do"), dayOfWeek]) :
               "_#(js:date.comparison.toDateQuarter.default)";
         }
         case XConstants.MONTH_DATE_GROUP + "": {
            return this.endDate ? Tool.formatCatalogString("_#(js:date.comparison.toDateMonth)",
                                                           [DateTypeFormatter.format(this.endDate, "Do"), dayOfWeek]) :
               "_#(js:date.comparison.toDateMonth.default)";
         }
         case XConstants.WEEK_DATE_GROUP + "":
            return this.endDate ? Tool.formatCatalogString("_#(js:date.comparison.toDateWeek)",
               [this.dayOfWeek()]) : "_#(js:date.comparison.toDateWeek.default)";
         case XConstants.DAY_DATE_GROUP + "":
            return null;
         case XConstants.YEAR_DATE_GROUP + "":
         default: {
            const endDate = DateTypeFormatter.format(this.endDate, "MMM DD", false) ||
               "_#(js:date.comparison.range.endDate)";
            return Tool.formatCatalogString("_#(js:date.comparison.toDateYear)",
                                            [endDate, dayOfWeek]);
         }
      }

      return null;
   }

   private dayOfWeek(): string {
      const day = parseInt(DateTypeFormatter.format(this.endDate, "d", false), 10);
      switch (day) {
         case 1:
            return "_#(js:Monday)";
         case 2:
            return "_#(js:Tuesday)";
         case 3:
            return "_#(js:Wednesday)";
         case 4:
            return "_#(js:Thursday)";
         case 5:
            return "_#(js:Friday)";
         case 6:
            return "_#(js:Saturday)";
         case 0:
            return "_#(js:Sunday)";
         default:
            return null;
      }
   }

   private monthOfQuarter(): string {
      return DateComparisonUtil.monthOfQuarter(this.endDate);
   }

   get inclusiveLabel(): string {
      if(this.standardPeriodPaneModel.dateLevel.type == ValueTypes.EXPRESSION ||
         this.standardPeriodPaneModel.dateLevel.type == ValueTypes.VARIABLE)
      {
         return "_#(js:date.comparison.inclusiveDefault)";
      }

      switch(this.standardPeriodPaneModel.dateLevel.value) {
         case XConstants.QUARTER_DATE_GROUP + "":
            return "_#(js:date.comparison.inclusiveQuarter)";
         case XConstants.MONTH_DATE_GROUP + "":
            return "_#(js:date.comparison.inclusiveMonth)";
         case XConstants.WEEK_DATE_GROUP + "":
            return "_#(js:date.comparison.inclusiveWeek)";
         case XConstants.DAY_DATE_GROUP + "":
            return "_#(js:date.comparison.inclusiveDay)";
         case XConstants.YEAR_DATE_GROUP + "":
         default:
            return "_#(js:date.comparison.inclusiveYear)";
      }

      return null;
   }

   get endDate(): Date {
      if(!this.standardPeriodPaneModel.toDayAsEndDay) {
         if(this.standardPeriodPaneModel.endDay == null ||
             this.standardPeriodPaneModel.endDay.type != ValueTypes.VALUE)
         {
            return null;
         }

         let timeInstant = DateTypeFormatter.toTimeInstant(this.standardPeriodPaneModel.endDay.value,
             DateTypeFormatter.ISO_8601_DATE_FORMAT);

         if(!timeInstant) {
            return null;
         }

         let resultDate = DateTypeFormatter.timeInstantToDate(timeInstant);

         if(!resultDate || isNaN(resultDate.getTime())) {
            return null;
         }

         return resultDate;
      }
      else {
         return new Date();
      }
   }

   showInclusive(): boolean {
      return this.standardPeriodPaneModel.toDate;
   }

   updateVisibleLevel(level: any): void {
      this.standardPeriodPaneModel.dateLevel.value = level;
   }

   updateLevelType(type: number): void {
      this.standardPeriodPaneModel.dateLevel.type =
         this.dateComparisonService.getDateComparisonValueTypeStr(type);
   }

   updatePreviousCountValue(count: any): void {
      this.standardPeriodPaneModel.preCount.value = count;
      this.validChange.emit(this.isValid());
   }

   updatePreviousCountType(type: number): void {
      this.standardPeriodPaneModel.preCount.type =
         this.dateComparisonService.getDateComparisonValueTypeStr(type);
      this.validChange.emit(this.isValid());
   }

   updateValid(): void {
      this.validChange.emit(this.isValid());
   }

   isValid(): boolean {
      return !this.isInvalidStandardPeriodPreCount() && this.isValidStandardPeriodEndDay();
   }

   isInvalidStandardPeriodPreCount(): boolean {
      return parseInt(this.standardPeriodPaneModel.preCount.value, 10) < 0 &&
         !Tool.isDynamic(this.standardPeriodPaneModel.preCount.value);
   }

   isValidStandardPeriodEndDay(): boolean {
      return this.standardPeriodPaneModel.toDayAsEndDay || this.verifyEndDate();
   }

   verifyEndDate(): boolean {
      return this.dateComparisonService.isValidDate(this.standardPeriodPaneModel.endDay.value);
   }
}
