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
import { Component, EventEmitter, Input, Output, OnChanges, SimpleChanges,
         ViewChild } from "@angular/core";
import { AestheticInfo } from "../../../data/chart/aesthetic-info";
import { AllChartAggregateRef } from "../../../data/chart/all-chart-aggregate-ref";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import { ChartBindingModel } from "../../../data/chart/chart-binding-model";
import { ChartModel } from "../../../../graph/model/chart-model";
import { ChartSelection } from "../../../../graph/model/chart-selection";
import { ChartRegion } from "../../../../graph/model/chart-region";
import { GraphUtil } from "../../../util/graph-util";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";
import { ChartTool } from "../../../../graph/model/chart-tool";
import { GraphTypes } from "../../../../common/graph-types";

@Component({
   selector: "aesthetic-pane",
   templateUrl: "aesthetic-pane.component.html",
   styleUrls: ["aesthetic-pane.scss"]
})
export class AestheticPane implements OnChanges {
   @Input() vsId: string;
   @Input() assemblyName: string;
   @Input() objectType: string;
   @Input() bindingModel: ChartBindingModel;
   @Input() chartModel: ChartModel;
   @Input() grayedOutValues: string[] = [];
   @Output() onSizeChange = new EventEmitter<any>();
   @Output() onUpdateData: EventEmitter<string> = new EventEmitter<string>();
   _aggrIndex: number = 0;

   constructor(private chartService: ChartEditorService) {
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.hasOwnProperty("bindingModel")) {
         if(!!this.bindingModel && this.bindingModel.multiStyles
            && this._aggrIndex > this.aggregates.length - 1)
         {
            this.setCurrentAggrIndex(this.aggregates.length - 1);
         }
      }
   }

   get aggregates(): ChartAggregateRef[] {
      if(!this.bindingModel) {
         return [];
      }

      let aggrs: ChartAggregateRef[] = [];

      if(GraphUtil.isMultiAesthetic(this.bindingModel)) {
         let xyaggrs: ChartAggregateRef[] =
            GraphUtil.getAestheticAggregateRefs(this.bindingModel, false);

         for(let i = 0; i < xyaggrs.length; i++) {
            if(!xyaggrs[i].discrete) {
               aggrs = aggrs.concat(xyaggrs[i]);
            }
         }
      }
      else {
         aggrs = <ChartAggregateRef[]> GraphUtil.getModelRefs(this.bindingModel);
      }

      return aggrs;
   }

   get multiStyles(): boolean {
      return this.bindingModel && GraphUtil.isMultiAesthetic(this.bindingModel);
   }

   get currAggregate(): ChartAggregateRef {
      if(!this.multiStyles) {
         return null;
      }

      if(this.aggregates.length > 0 && this._aggrIndex < this.aggregates.length) {
         return this.aggregates[this._aggrIndex];
      }

      return null;
   }

   showNext(): void {
      let aggrs = this.aggregates;

      if(aggrs.length > 1) {
         this.setCurrentAggrIndex(
            this._aggrIndex < aggrs.length - 1 ? this._aggrIndex + 1 : 0);
      }
   }

   showPrevious(): void {
      let aggrs = this.aggregates;

      if(aggrs.length > 1) {
         this.setCurrentAggrIndex(
            this._aggrIndex > 0 ? this._aggrIndex - 1 : aggrs.length - 1);
      }
   }

   selectCurrentAggregate(newCurrent: ChartAggregateRef): void {
      if(!newCurrent) {
         this.setCurrentAggrIndex(0);
      }

      let aggrs = this.aggregates;

      for(let idx in aggrs) {
         if(aggrs[idx].fullName == newCurrent.fullName) {
            this.setCurrentAggrIndex(parseInt(idx, 10));
            break;
         }
      }
   }

   updateData(event: string) {
      if(event == "showTextFormat") {
         this.fixMeasureName(true);
      }

      this.onUpdateData.emit(event);
   }

   private setCurrentAggrIndex(idx: number): void {
      this._aggrIndex = idx;
      this.loadTextFormat();
   }

   private loadTextFormat(): void {
      let hideFormatPane: boolean = false;
      let textField: AestheticInfo = this.currAggregate ? this.currAggregate.textField :
         this.bindingModel ? this.bindingModel.textField : null;

      // check if the ChartBindable has the text field
      if(!textField) {
         hideFormatPane = !this.chartModel.showValues;
      }
      // check if the all aggregate text can be editable.
      else if(GraphUtil.isAllChartAggregate(this.currAggregate)) {
         hideFormatPane = !(<AllChartAggregateRef> this.currAggregate)
            .visualPaneStatus.textFieldEditable;
      }

      this.fixMeasureName(!hideFormatPane);
      this.onUpdateData.emit(hideFormatPane ? "hideFormatPane" : "getTextFormat");
   }

   /**
    * Fix the measure name in chart editor service, make the name as current aggregate
    * name if text format can be edit, null if not.
    */
   private fixMeasureName(editable: boolean): void {
      if(this.chartModel.clearCanvasSubject) {
         this.chartModel.clearCanvasSubject.next(null);
      }

      if(this.currAggregate && editable) {
         this.chartService.measureName = this.currAggregate.fullName;
      }
   }

   isShapeSupported(): boolean {
      const chartType = this.currAggregate ? this.currAggregate.chartType
         : this.chartModel?.chartType;
      return !GraphTypes.isContour(chartType);
   }

   isRelationChart(): boolean {
      return GraphTypes.isRelation(this.chartModel?.chartType);
   }
}
