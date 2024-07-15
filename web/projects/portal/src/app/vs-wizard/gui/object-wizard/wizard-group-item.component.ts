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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../../shared/util/tool";
import { BDimensionRef } from "../../../binding/data/b-dimension-ref";
import { SummaryAttrUtil } from "../../../binding/util/summary-attr-util";
import { XConstants } from "../../../common/util/xconstants";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { VSWizardBindingTreeService } from "../../services/vs-wizard-binding-tree.service";
import { VSWizardItem } from "./wizard-item.component";
import { DateLevelExamplesService } from "../../../common/services/date-level-examples.service";

@Component({
   selector: "wizard-group-item",
   templateUrl: "./wizard-group-item.component.html",
   styleUrls: ["./wizard-group-item.component.scss"]
})
export class VSWizardGroupItem extends VSWizardItem<BDimensionRef> implements OnInit {
   @Input() showName: boolean;
   @Input() showMore: boolean;
   @Output() addItem = new EventEmitter<number>();
   @Output() onEditDimension = new EventEmitter<null>();
   dateGroups: any[];
   timeGroups: any[];
   dateTimeGroups: any[];
   dateLevelExamples: string[] = [];

   constructor(protected modalService: NgbModal,
               protected clientService: ViewsheetClientService,
               protected treeService: VSWizardBindingTreeService,
               private examplesService: DateLevelExamplesService)
   {
      super(modalService, clientService, treeService);
   }

   ngOnInit() {
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

      this.examplesService.loadDateLevelExamples(this.getDateLevelOpts().map(val => val.value),
         this.dataRef.dataType).subscribe((data: any) => this.dateLevelExamples = data.dateLevelExamples);
   }

   getDateLevelOpts(): any[] {
      let dataType = this.dataRef.dataType;

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

   dateLevelChange(val: any): void {
      this.dataRef.dateLevel = val;
      let maxValue: number = this.getMaxValue(this.dateLevelNum);

      if(this.dataRef.dateInterval > maxValue) {
         this.dataRef.dateInterval = maxValue;
      }

      if(!this.dataRef.dateInterval && this.getMaxValue(this.dateLevelNum)) {
         this.dataRef.dateInterval = 1;
      }

      this.onEditDimension.emit();
   }

   getMaxValue(order: number): number {
      return SummaryAttrUtil.getMaxForLevel(order);
   }

   get dateLevelNum(): number {
      return parseInt(this.dataRef.dateLevel, 10);
   }

   getDataType(): string {
     return this.dataRef.dataType;
   }

   addGroup(): void {
      this.addItem.emit(this.index);
   }

   getFullName(): string {
      return this.dataRef.fullName;
   }

   get isDynamic(): boolean {
      return Tool.isDynamic(this.dataRef.columnValue);
   }

   isDimension(): boolean {
      return true;
   }

   convertBtnTitle(): string {
      return "_#(js:Convert To Measure)";
   }
}
