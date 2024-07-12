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
import { Component, Input, QueryList, ViewChild, ViewChildren } from "@angular/core";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { ComparisonOption, DateComparisonPaneModel } from "../../model/date-comparison-pane-model";
import { DateComparisonIntervalPaneComponent } from "./date-comparison-interval-pane.component";
import { DateComparisonPeriodsPaneComponent } from "./date-comparison-periods-pane.component";
import { VSObjectType } from "../../../common/data/vs-object-type";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { AssemblyType } from "../../../composer/gui/vs/assembly-type";
import { IntervalLevel } from "../../model/interval-pane-model";
import { DynamicValueModel, ValueTypes } from "../../model/dynamic-value-model";
import { XConstants } from "../../../common/util/xconstants";

@Component({
   selector: "date-comparison-pane",
   templateUrl: "./date-comparison-pane.component.html",
   styleUrls: ["./date-comparison-pane.component.scss"]
})
export class DateComparisonPaneComponent {
   @Input() dateComparisonPaneModel: DateComparisonPaneModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   @Input() variableValues: string[] = [];
   @Input() assemblyType: VSObjectType;
   @Input() disable: boolean = false;
   @Input() toDateDisabled: boolean = false;
   @Input() shareAssemblyType: number;
   @Input() intervalEndDate: any;
   dialogOpened: boolean = false;
   protected oframes: string;

   @ViewChild(DateComparisonPeriodsPaneComponent) periodPane: DateComparisonPeriodsPaneComponent;
   @ViewChild(DateComparisonIntervalPaneComponent) intervalPane: DateComparisonIntervalPaneComponent;
   @ViewChildren(FixedDropdownDirective) dropdowns: QueryList<FixedDropdownDirective>;

   comparisonOption = [{
         label: "_#(js:Value Only)",
         value: ComparisonOption.VALUE
      },
      {
         label: "_#(js:Change)",
         value: ComparisonOption.CHANGE
      },
      {
         label: "_#(js:Change and Value)",
         value: ComparisonOption.CHANGE_VALUE
      },
      {
         label: "_#(js:Percent Change)",
         value: ComparisonOption.PERCENT
      },
      {
         label: "_#(js:Percent Change and Value)",
         value: ComparisonOption.PERCENT_VALUE
      }];

   get isValidDateComparison(): boolean {
      return this.periodPane.isValidPeriod && this.intervalPane.isValidInterval();
   }

   showAsFacet(): boolean {
      return this.isChart();
   }

   isChart() {
      return this.assemblyType === "VSChart";
   }

   isOnlyShowRecentDateVisible(): boolean {
      if(!this.isChart() || !this.dateComparisonPaneModel) {
         return false;
      }

      if(this.dateComparisonPaneModel.periodPaneModel.custom) {
         return true;
      }

      // with period added to axis instead of color, we no longer need this option for
      // non-custom periods.
      let periodVal = this.dateComparisonPaneModel.periodPaneModel.standardPeriodPaneModel.dateLevel;
      let intervalVal = this.dateComparisonPaneModel.intervalPaneModel.level;
      let contextLevelVal = this.dateComparisonPaneModel.intervalPaneModel.contextLevel;
      let granularityVal = this.dateComparisonPaneModel.intervalPaneModel.granularity;

      let intervalLevel = this.dcIntervalLevelToDateGroupLevel(intervalVal);
      let contextLevel = this.dcIntervalLevelToDateGroupLevel(contextLevelVal);
      let granularity = this.dcIntervalLevelToDateGroupLevel(granularityVal);

      if(contextLevel != -1 && contextLevel == intervalLevel && contextLevel == granularity) {
         return this.showPartLevelAsDate();
      }

      return (periodVal.value != contextLevel || contextLevel != intervalLevel) || this.showPartLevelAsDate();
   }

   private showPartLevelAsDate(): boolean {
      let dateLevel = this.dateComparisonPaneModel.periodPaneModel.standardPeriodPaneModel.dateLevel;
      let granularity = this.dateComparisonPaneModel.intervalPaneModel.granularity;

      if(dateLevel.type == ValueTypes.VALUE && dateLevel.value !== XConstants.YEAR_DATE_GROUP + "" &&
         dateLevel.value !== XConstants.QUARTER_DATE_GROUP + "")
      {
         return false;
      }

      if(granularity.type == ValueTypes.VALUE && granularity.value !== IntervalLevel.DAY + "" &&
         granularity.value !== IntervalLevel.WEEK + "")
      {
         return false;
      }

      return true;
   }

   private dcIntervalLevelToDateGroupLevel(intervalValue: DynamicValueModel): number {
      if(intervalValue.type != ValueTypes.VALUE) {
         return -1;
      }

      let interval = parseInt(intervalValue.value, 10);

      if((interval & IntervalLevel.YEAR) == IntervalLevel.YEAR) {
         return XConstants.YEAR_DATE_GROUP;
      }
      else if((interval & IntervalLevel.QUARTER) == IntervalLevel.QUARTER) {
         return XConstants.QUARTER_DATE_GROUP;
      }
      else if((interval & IntervalLevel.MONTH) == IntervalLevel.MONTH) {
         return XConstants.MONTH_DATE_GROUP;
      }
      else if((interval & IntervalLevel.WEEK) == IntervalLevel.WEEK) {
         return XConstants.WEEK_DATE_GROUP;
      }
      else if((interval & IntervalLevel.DAY) == IntervalLevel.DAY) {
         return XConstants.DAY_DATE_GROUP;
      }

      return -1;
   }


   openChanged(open: boolean) {
      if(open) {
         this.oframes = JSON.stringify(this.dateComparisonPaneModel.visualFrameModel);
      }
      else {
         this.dropdowns.forEach(d => d.close());
      }
   }

   editColorDisable() {
      return this.disable && this.shareAssemblyType == AssemblyType.CHART_ASSET;
   }

   getPeriodEndDay(): DynamicValueModel {
      if(this.dateComparisonPaneModel.periodPaneModel.custom) {
         let datePeriods = this.dateComparisonPaneModel?.periodPaneModel?.customPeriodPaneModel?.datePeriods;

         if(!datePeriods || datePeriods.length == 0) {
            return null;
         }

         let first = datePeriods[0];

         if(!first) {
            return null;
         }

         return first.end;
      }
      else {
         return this.dateComparisonPaneModel?.periodPaneModel?.standardPeriodPaneModel?.endDay;
      }
   }

   isWeekly(): boolean {
      const granularity = this.dateComparisonPaneModel?.intervalPaneModel?.granularity?.value;
      const level = this.dateComparisonPaneModel?.intervalPaneModel?.level?.value;
      const contextLevel = this.dateComparisonPaneModel?.intervalPaneModel?.contextLevel?.value;

      return granularity == IntervalLevel.WEEK + "" ||
         level == IntervalLevel.SAME_WEEK + "" ||
         level == IntervalLevel.WEEK_TO_DATE + "" || contextLevel + "" == XConstants.WEEK_DATE_GROUP + "";
   }
}
