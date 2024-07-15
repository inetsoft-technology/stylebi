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
import { Component, Input, OnInit, Output, EventEmitter } from "@angular/core";
import { XSchema } from "../../../../common/data/xschema";
import { XConstants } from "../../../../common/util/xconstants";
import { ChartDimensionRef } from "../../../data/chart/chart-dimension-ref";
import { ChartBindingModel } from "../../../data/chart/chart-binding-model";
import { GraphUtil } from "../../../util/graph-util";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { Tool } from "../../../../../../../shared/util/tool";
import { DateRangeRef } from "../../../../common/util/date-range-ref";
import { DataRefType } from "../../../../common/data/data-ref-type";
import { BindingService } from "../../../services/binding.service";
import { GraphTypes } from "../../../../common/graph-types";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import { DateLevelExamplesService } from "../../../../common/services/date-level-examples.service";
import { StyleConstants } from "../../../../common/util/style-constants";

@Component({
   selector: "dimension-editor",
   templateUrl: "dimension-editor.component.html",
   styleUrls: ["dimension-editor.component.scss"]
})
export class DimensionEditor implements OnInit {
   @Input() dimension: ChartDimensionRef;
   @Input() fieldType: string;
   @Input() variables: any;
   @Input() vsId: string;
   @Input() grayedOutValues: string[] = [];
   @Input() isOuterDimRef: boolean;
   @Input() sortSupported: boolean;
   @Output() apply: EventEmitter<any> = new EventEmitter<any>();
   @Output() dialogOpen = new EventEmitter<boolean>();
   private _bindingModel: ChartBindingModel;
   private oldTimeSeries: boolean;
   private isValueOfDateLevel: boolean;
   dateLevelOpts: any[];
   dateLevelExamples: string[];

   @Input()
   set bindingModel(model: ChartBindingModel) {
      this._bindingModel = model;

      if(!this.isTimeVisible() && !!this.dimension) {
         this.dimension.timeSeries = false;
      }
   }

   get bindingModel(): ChartBindingModel {
      return this._bindingModel;
   }

   constructor(private uiContextService: UIContextService,
               private bindingService: BindingService,
               private examplesService: DateLevelExamplesService)
   {
   }

   ngOnInit() {
      if(!this.timeSeriesSupported()) {
         this.dimension.timeSeries = false;
      }

      this.oldTimeSeries = this.dimension.timeSeries;
      this.dateLevelOpts = this.getDateLevelOpts();
      this.examplesService.loadDateLevelExamples(this.dateLevelOpts.map((opt) => opt.value), this.dimension.dataType)
         .subscribe((data: any) => this.dateLevelExamples = data.dateLevelExamples);

      this.isValueOfDateLevel = this.dimension.dateLevel ?
         this.dimension.dateLevel.indexOf("$") != 0 &&
         this.dimension.dateLevel.indexOf("=") != 0 : false;
   }

   get timeSeries(): boolean {
      return this.dimension.timeSeries && this.isTimeVisible() && this.timeSeriesSupported();
   }

   set timeSeries(value: boolean) {
      this.dimension.timeSeries = value;
   }

   isDateType(): boolean {
      return XSchema.isDateType(this.dimension.dataType) &&
         (this.dimension.refType & DataRefType.CUBE) == 0;
   }

   dateLevelChange(nlevel: string) {
      if(nlevel != this.dimension.dateLevel &&
         (this.dimension.order & StyleConstants.SORT_SPECIFIC) != 0 && !!this.dimension.manualOrder)
      {
         this.dimension.manualOrder = null;
         let order = this.dimension.order;
         order = order & ~StyleConstants.SORT_SPECIFIC;

         if(order == StyleConstants.SORT_NONE) {
            order = StyleConstants.SORT_ASC;
         }

         this.dimension.order = order;
      }

      this.isValueOfDateLevel = nlevel.indexOf("$") != 0 && nlevel.indexOf("=") != 0;
      this.dimension.dateLevel = nlevel;

      this.dimension.timeSeries = this.timeSeriesSupported() ? this.oldTimeSeries : false;
   }

   get dateLevel(): string {
      if(this.dimension && this.dimension.dateLevel) {
         if(!this.isValueOfDateLevel) {
            return this.dimension.dateLevel;
         }

         return this.dateLevelOpts.some(opt => opt.value === this.dimension.dateLevel) ?
            this.dimension.dateLevel : this.dateLevelOpts[0].value;
      }

      return this.dateLevelOpts[0].value;
   }

   timeSeriesSupported(): boolean {
      let level: number = parseInt(this.dimension.dateLevel, 10);
      let ftype = this.fieldType;
      let dateType = (Tool.isDynamic(this.dimension.columnValue) ||
         XSchema.isDateType(this.dimension.dataType) &&
         DateRangeRef.isDateTime(level)) &&
         (this.dimension.refType & DataRefType.CUBE) == 0 &&
         level != XConstants.NONE_DATE_GROUP;
      let fenabled = ftype != "groupfields" && ftype != "path" && !this.isOuterDimRef
         // aesthetic as change column comparison should allow time series to fill in missing dates
         || GraphUtil.isAestheticType(ftype) && this.isChangeCalcDim();

      return dateType && fenabled;
   }

   isChangeCalcDim(): boolean {
      return this.bindingModel.xfields.concat(this.bindingModel.yfields)
         .filter(f => (<any> f).calculateInfo && (<any> f).calculateInfo.classType == "CHANGE")
         .map(f => (<any> f).calculateInfo.columnName)
         .indexOf(this.dimension.fullName) >= 0;
   }

   isTimeSeries(): boolean {
      return this.isDateType() && this.isTimeVisible()
         && this.timeSeriesSupported() && this.timeSeries;
   }

   isTimeVisible(): boolean {
      let binding = this.bindingModel;

      return !GraphUtil.isWaterfall(binding) &&
         !GraphUtil.isPolar(binding, false) &&
         (!GraphUtil.isMergedGraphType(binding) ||
            binding.chartType == GraphTypes.CHART_STOCK ||
            binding.chartType == GraphTypes.CHART_CANDLE ||
            binding.chartType == GraphTypes.CHART_BOXPLOT);
   }

   getDateLevelOpts(): Array<any> {
      let dataType = this.dimension.dataType;

      if(dataType == "time") {
         return [{
               label: "_#(js:Hour)",
               value: XConstants.HOUR_DATE_GROUP + ""
            },
            {
               label: "_#(js:Minute)",
               value: XConstants.MINUTE_DATE_GROUP + ""
            },
            {
               label: "_#(js:Second)",
               value: XConstants.SECOND_DATE_GROUP + ""
            },
            {
               label: "_#(js:Hour of Day)",
               value: XConstants.HOUR_OF_DAY_DATE_GROUP + ""
            },
            {
               label: "_#(js:Minute of Hour)",
               value: XConstants.MINUTE_OF_HOUR_DATE_GROUP + ""
            },
            {
               label: "_#(js:Second of Minute)",
               value: XConstants.SECOND_OF_MINUTE_DATE_GROUP + ""
            },
            {
               label: "_#(js:None)",
               value: XConstants.NONE_DATE_GROUP + ""
            }];
      }
      else if(dataType == "date") {
         return [{
               label: "_#(js:Year)",
               value: XConstants.YEAR_DATE_GROUP + ""
            },
            {
               label: "_#(js:Quarter)",
               value: XConstants.QUARTER_DATE_GROUP + ""
            },
            {
               label: "_#(js:Month)",
               value: XConstants.MONTH_DATE_GROUP + ""
            },
            {
               label: "_#(js:Week)",
               value: XConstants.WEEK_DATE_GROUP + ""
            },
            {
               label: "_#(js:Day)",
               value: XConstants.DAY_DATE_GROUP + ""
            },
            {
               label: "_#(js:Quarter of Year)",
               value: XConstants.QUARTER_OF_YEAR_DATE_GROUP + ""
            },
            {
               label: "_#(js:Month of Year)",
               value: XConstants.MONTH_OF_YEAR_DATE_GROUP + ""
            },
            {
               label: "_#(js:Week of Year)",
               value: XConstants.WEEK_OF_YEAR_DATE_GROUP + ""
            },
            {
               label: "_#(js:Day of Month)",
               value: XConstants.DAY_OF_MONTH_DATE_GROUP + ""
            },
            {
               label: "_#(js:Day of Week)",
               value: XConstants.DAY_OF_WEEK_DATE_GROUP + ""
            },
            {
               label: "_#(js:None)",
               value: XConstants.NONE_DATE_GROUP + ""
            }];
      }
      else {
         return [{
               label: "_#(js:Year)",
               value: XConstants.YEAR_DATE_GROUP + ""
            },
            {
               label: "_#(js:Quarter)",
               value: XConstants.QUARTER_DATE_GROUP + ""
            },
            {
               label: "_#(js:Month)",
               value: XConstants.MONTH_DATE_GROUP + ""
            },
            {
               label: "_#(js:Week)",
               value: XConstants.WEEK_DATE_GROUP + ""
            },
            {
               label: "_#(js:Day)",
               value: XConstants.DAY_DATE_GROUP + ""
            },
            {
               label: "_#(js:Hour)",
               value: XConstants.HOUR_DATE_GROUP + ""
            },
            {
               label: "_#(js:Minute)",
               value: XConstants.MINUTE_DATE_GROUP + ""
            },
            {
               label: "_#(js:Second)",
               value: XConstants.SECOND_DATE_GROUP + ""
            },
            {
               label: "_#(js:Quarter of Year)",
               value: XConstants.QUARTER_OF_YEAR_DATE_GROUP + ""
            },
            {
               label: "_#(js:Month of Year)",
               value: XConstants.MONTH_OF_YEAR_DATE_GROUP + ""
            },
            {
               label: "_#(js:Week of Year)",
               value: XConstants.WEEK_OF_YEAR_DATE_GROUP + ""
            },
            {
               label: "_#(js:Day of Month)",
               value: XConstants.DAY_OF_MONTH_DATE_GROUP + ""
            },
            {
               label: "_#(js:Day of Week)",
               value: XConstants.DAY_OF_WEEK_DATE_GROUP + ""
            },
            {
               label: "_#(js:Hour of Day)",
               value: XConstants.HOUR_OF_DAY_DATE_GROUP + ""
            },
            {
               label: "_#(js:Minute of Hour)",
               value: XConstants.MINUTE_OF_HOUR_DATE_GROUP + ""
            },
            {
               label: "_#(js:Second of Minute)",
               value: XConstants.SECOND_OF_MINUTE_DATE_GROUP + ""
            },
            {
               label: "_#(js:None)",
               value: XConstants.NONE_DATE_GROUP + ""
            }];
      }
   }

   get assemblyName(): string {
      return this.bindingService.assemblyName;
   }

   isBoxplot(): boolean {
      return this.bindingModel.chartType == GraphTypes.CHART_BOXPLOT;
   }

   isOtherSupported(): boolean {
      if(this.isOuterDimRef) {
         return false;
      }

      return !GraphTypes.isRelation(this.bindingModel.chartType) &&
         !GraphTypes.isGantt(this.bindingModel.chartType);
   }

   isRankingSupported(): boolean {
      if(this.isBoxplot()) {
         return false;
      }

      return true;
   }

   applyClick() {
      let manualOrders = this.dimension?.manualOrder;

      if((manualOrders == null || manualOrders.length == 0) &&
         this.dimension.order == StyleConstants.SORT_SPECIFIC &&
         this.dimension.specificOrderType == "manual")
      {
         this.dimension.order = StyleConstants.SORT_NONE;
      }

      if(!this.isTimeVisible() && !!this.dimension) {
         this.dimension.timeSeries = false;
      }

      this.apply.emit(false);
   }
}
