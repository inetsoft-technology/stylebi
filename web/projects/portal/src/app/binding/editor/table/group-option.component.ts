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
import { Component, Input, Output, EventEmitter, OnInit } from "@angular/core";
import { BindingService } from "../../services/binding.service";
import { BDimensionRef } from "../../data/b-dimension-ref";
import { CrosstabBindingModel } from "../../data/table/crosstab-binding-model";
import { XConstants } from "../../../common/util/xconstants";
import { XSchema } from "../../../common/data/xschema";
import { TableBindingModel } from "../../data/table/table-binding-model";
import { UIContextService } from "../../../common/services/ui-context.service";
import { SummaryAttrUtil } from "../../util/summary-attr-util";
import { StyleConstants } from "../../../common/util/style-constants";
import { DateLevelExamplesService } from "../../../common/services/date-level-examples.service";
import { SourceInfo } from "../../data/source-info";
import {Tool} from "../../../../../../shared/util/tool";
import {DataRefType} from "../../../common/data/data-ref-type";

@Component({
   selector: "group-option",
   templateUrl: "group-option.component.html",
   styleUrls: ["group-option.component.scss"]
})
export class GroupOption implements OnInit {
   @Input() field: BDimensionRef;
   @Input() fieldType: string;
   @Input() dragIndex: number;
   @Input() vsId: any;
   @Input() variables: any;
   @Input() grayedOutValues: string[] = [];
   @Input() isOuterDimRef: boolean;
   @Input() source: SourceInfo;
   @Output() apply: EventEmitter<any> = new EventEmitter<any>();
   @Output() dialogOpen = new EventEmitter<boolean>();
   dateGroups: any[];
   timeGroups: any[];
   dateTimeGroups: any[];
   summs: any[];
   dateLevelOpts: any[];
   dateLevelExamples: string[];

   public constructor(private bindingService: BindingService,
                      private uiContextService: UIContextService,
                      private examplesService: DateLevelExamplesService)
   {
      this.initOptions();
   }

   applyClick() {
      let manualOrders = this.field?.manualOrder;

      if((manualOrders == null || manualOrders.length == 0) &&
         this.field.order == StyleConstants.SORT_SPECIFIC &&
         this.field.specificOrderType == "manual")
      {
         this.field.order = StyleConstants.SORT_NONE;
      }
      else if(this.field != null && this.field.order != StyleConstants.SORT_SPECIFIC) {
         this.field.manualOrder = null;
      }

      this.apply.emit(false);
   }

   initOptions() {
      this.timeGroups =  [
         { label: "_#(js:Hour)", value: XConstants.HOUR_DATE_GROUP  + "" },
         { label: "_#(js:Minute)", value: XConstants.MINUTE_DATE_GROUP + "" },
         { label: "_#(js:Second)", value: XConstants.SECOND_DATE_GROUP + "" },
         { label: "_#(js:Hour of Day)", value: XConstants.HOUR_OF_DAY_DATE_GROUP + "" },
         { label: "_#(js:Minute of Hour)", value: XConstants.MINUTE_OF_HOUR_DATE_GROUP + "" },
         { label: "_#(js:Second of Minute)", value: XConstants.SECOND_OF_MINUTE_DATE_GROUP + "" },
         { label: "_#(js:None)", value: XConstants.NONE_DATE_GROUP + "" }
      ];
      this.dateGroups = [
         { label: "_#(js:Year)", value: XConstants.YEAR_DATE_GROUP + "" },
         { label: "_#(js:Quarter)", value: XConstants.QUARTER_DATE_GROUP + "" },
         { label: "_#(js:Month)", value: XConstants.MONTH_DATE_GROUP + "" },
         { label: "_#(js:Week)", value: XConstants.WEEK_DATE_GROUP + "" },
         { label: "_#(js:Day)", value: XConstants.DAY_DATE_GROUP + "" },
         { label: "_#(js:Quarter of Year)", value: XConstants.QUARTER_OF_YEAR_DATE_GROUP + "" },
         { label: "_#(js:Month of Year)", value: XConstants.MONTH_OF_YEAR_DATE_GROUP + "" },
         { label: "_#(js:Week of Year)", value: XConstants.WEEK_OF_YEAR_DATE_GROUP + "" },
         { label: "_#(js:Day of Month)", value: XConstants.DAY_OF_MONTH_DATE_GROUP + "" },
         { label: "_#(js:Day of Week)", value: XConstants.DAY_OF_WEEK_DATE_GROUP + "" },
         { label: "_#(js:None)", value: XConstants.NONE_DATE_GROUP + "" }
      ];
      this.dateTimeGroups = [
         { label: "_#(js:Year)", value: XConstants.YEAR_DATE_GROUP + "" },
         { label: "_#(js:Quarter)", value: XConstants.QUARTER_DATE_GROUP + "" },
         { label: "_#(js:Month)", value: XConstants.MONTH_DATE_GROUP + "" },
         { label: "_#(js:Week)", value: XConstants.WEEK_DATE_GROUP + "" },
         { label: "_#(js:Day)", value: XConstants.DAY_DATE_GROUP + "" },
         { label: "_#(js:Hour)", value: XConstants.HOUR_DATE_GROUP + "" },
         { label: "_#(js:Minute)", value: XConstants.MINUTE_DATE_GROUP + "" },
         { label: "_#(js:Second)", value: XConstants.SECOND_DATE_GROUP + "" },
         { label: "_#(js:Quarter of Year)", value: XConstants.QUARTER_OF_YEAR_DATE_GROUP + "" },
         { label: "_#(js:Month of Year)", value: XConstants.MONTH_OF_YEAR_DATE_GROUP + "" },
         { label: "_#(js:Week of Year)", value: XConstants.WEEK_OF_YEAR_DATE_GROUP + "" },
         { label: "_#(js:Day of Month)", value: XConstants.DAY_OF_MONTH_DATE_GROUP + "" },
         { label: "_#(js:Day of Week)", value: XConstants.DAY_OF_WEEK_DATE_GROUP + "" },
         { label: "_#(js:Hour of Day)", value: XConstants.HOUR_OF_DAY_DATE_GROUP + "" },
         { label: "_#(js:Minute of Hour)", value: XConstants.MINUTE_OF_HOUR_DATE_GROUP + "" },
         { label: "_#(js:Second of Minute)", value: XConstants.SECOND_OF_MINUTE_DATE_GROUP + "" },
         { label: "_#(js:None)", value: XConstants.NONE_DATE_GROUP + "" }
      ];
      this.summs = [{ label: "_#(js:Show)", value: "true" }, { label: "_#(js:Hide)", value: "false"}];
   }

   ngOnInit(): void {
      this.dateLevelOpts = this.getDateLevelOpts();
      this.examplesService.loadDateLevelExamples(this.dateLevelOpts.map((opt) => opt.value), this.field.dataType)
         .subscribe((data: any) => this.dateLevelExamples = data.dateLevelExamples);
   }

   isTable(): boolean {
      return this.bindingService.objectType === "table";
   }

   isCrosstab(): boolean {
      return this.bindingService.objectType === "crosstab";
   }

   hasAgg(): boolean {
      return !!this.bindingModel && !!this.bindingModel.aggregates &&
         this.bindingModel.aggregates.length > 0;
   }

   isLast(): boolean {
      if(this.isTable()) {
         return false;
      }

      let binding: CrosstabBindingModel = this.crosstabBindingModel;

      if(this.fieldType === "rows") {
         return this.dragIndex == binding.rows.length - 1;
      }
      else if(this.fieldType === "cols") {
         return this.dragIndex == binding.cols.length - 1;
      }

      return false;
   }

   isDateType() {
      let dataType = this.field.dataType;
      return XSchema.isDateType(dataType) && (this.field.refType & DataRefType.CUBE) == 0;
   }

   getDateLevelOpts(): any[] {
      let dataType = this.field.dataType;

      if(dataType == "time") {
         return this.timeGroups;
      }
      else if(dataType == "date") {
         return this.dateGroups;
      }
      else {
         return this.dateTimeGroups;
      }
   }

   get crosstabBindingModel(): CrosstabBindingModel {
      return this.isTable() ?
         null : <CrosstabBindingModel> this.bindingService.getBindingModel();
   }

   get bindingModel(): TableBindingModel {
      return <TableBindingModel> this.bindingService.getBindingModel();
   }

   getFieldGourpTotalKey(): string {
      return this.field.name + ":" + this.fieldType + this.dragIndex;
   }

   get summarize(): string {
      if(this.isTable()) {
         return this.field.summarize;
      }

      if(!this.crosstabBindingModel ||
         !this.crosstabBindingModel.suppressGroupTotal)
      {
         return "false";
      }

      return !this.crosstabBindingModel.suppressGroupTotal[this.getFieldGourpTotalKey()] + "";
   }

   set summarize(val: string) {
      if(this.isTable()) {
         this.field.summarize = val;
      }

      if(this.crosstabBindingModel &&
         this.crosstabBindingModel.suppressGroupTotal)
      {
         this.crosstabBindingModel.suppressGroupTotal[this.getFieldGourpTotalKey()] = val == "false";
      }
   }

   get summarizeCheck(): boolean {
      return this.summarize === "true";
   }

   set summarizeCheck(value: boolean) {
      this.summarize = value ? "true" : "false";
   }

   getMaxValue(order: number): number {
      return SummaryAttrUtil.getMaxForLevel(order);
   }

   get dateLevelNum(): number {
      return parseInt(this.field.dateLevel, 10);
   }

   get dateInterval(): number {
      return this.field.dateInterval;
   }

   set dateInterval(dateInterval: number) {
      let maxValue: number = this.getMaxValue(this.dateLevelNum);
      this.field.dateInterval = dateInterval > maxValue ? maxValue : dateInterval;
      this.field.order = this.field.dateInterval > 1 && this.field.order == StyleConstants.SORT_SPECIFIC
         ? StyleConstants.SORT_ASC : this.field.order;
   }

   dateLevelChange(val: any) {
      if(val != this.field.dateLevel &&
         (this.field.order & StyleConstants.SORT_SPECIFIC) != 0 && !!this.field.manualOrder)
      {
         this.field.manualOrder = null;
         let order = this.field.order;
         order = order & ~StyleConstants.SORT_SPECIFIC;
         this.field.order = order;
      }

      this.field.dateLevel = val;

      if(this.field.dateLevel == XConstants.NONE_DATE_GROUP + "") {
         this.field.timeSeries = false;
         this.field.order = this.field.order == StyleConstants.SORT_SPECIFIC
            ? StyleConstants.SORT_ASC : this.field.order;
      }
   }

   getSummarizeLabel(): string {
      for(let i = 0; i < this.summs.length; i++) {
         if(this.summs[i].value == this.summarize) {
            return this.summs[i].label;
         }
      }

      return this.summarize;
   }

   changeSummarizeValue(val: string) {
      this.summarize = val;
   }

   timeSeriesSupported(): boolean {
      return this.isDateType() && !this.isOuterDimRef && this.dateLevelNum != 0 &&
         (this.dateLevelNum & XConstants.PART_DATE_GROUP) == 0 && (!this.haveVisibleDetails() ||
         this.bindingModel.option.summaryOnly != false);
   }

   haveVisibleDetails() {
      if(!this.bindingModel.details || this.bindingModel.details.length == 0) {
         return false;
      }

      for(let i = 0; i < this.bindingModel.details.length; i++) {
         let detail: any = this.bindingModel.details[i];

         if(detail && detail.dataRefModel && detail.dataRefModel.visible) {
            return true;
         }
      }

      return false;
   }

   isTimeSeries(): boolean {
      return this.timeSeriesSupported() && this.field.timeSeries;
   }

   toggleTimeSeries(status: boolean): void {
      if(status) {
         this.dateInterval = 1;
         this.field.order = StyleConstants.SORT_ASC;
      }
   }
}
