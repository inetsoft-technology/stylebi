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
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from "@angular/core";
import { DndService } from "../../../../common/dnd/dnd.service";
import { GraphTypes } from "../../../../common/graph-types";
import { ChartRef } from "../../../../common/data/chart-ref";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { Tool } from "../../../../../../../shared/util/tool";
import { ChartModel } from "../../../../graph/model/chart-model";
import { ChartTool } from "../../../../graph/model/chart-tool";
import { AestheticInfo } from "../../../data/chart/aesthetic-info";
import { AllChartAggregateRef } from "../../../data/chart/all-chart-aggregate-ref";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";
import { GraphUtil } from "../../../util/graph-util";
import { AestheticFieldMc } from "./aesthetic-field-mc";

@Component({
   selector: "text-field-mc",
   templateUrl: "text-field-mc.component.html",
   styleUrls: ["aesthetic-field-mc.scss"]
})
export class TextFieldMc extends AestheticFieldMc implements OnChanges {
   @Input() chartModel: ChartModel;
   @Output() onUpdateData: EventEmitter<string> = new EventEmitter<string>();
   measures: string[] = [];
   currentIndex: number = 0;
   currentMeasure: string;

   constructor(editorService: ChartEditorService, dservice: DndService,
               protected uiContextService: UIContextService)
   {
      super(editorService, dservice, uiContextService);
   }

   ngOnChanges(changes: SimpleChanges) {
      super.ngOnChanges(changes);
      this.initMeasures();
   }

   private initMeasures() {
      if(this.isMeasureRollerEnabled()) {
         this.measures = this.getMeasureNames();
         const measureName = this.editorService.measureName;
         this.currentIndex = -1;

         if(measureName) {
            this.currentIndex = this.measures.findIndex(a => a == measureName);
         }

         if(this.currentIndex < 0) {
            this.currentMeasure = this.measures[this.currentIndex = 0];
         }
         else {
            this.currentMeasure = measureName;
         }

         if(this.editorService.textFormat && this.currentMeasure != this.editorService.measureName &&
            // format may be applying an axis label, don't switch it to text format. (60550)
            (!this.editorService.measureName ||
               this.measures.includes(this.editorService.measureName)))
         {
            this.onUpdateData.emit("getTextFormat");
         }

         this.editorService.measureName = this.currentMeasure;
      }
   }

   protected getField(): AestheticInfo {
      if(this.aggr) {
         return this.aggr.textField;
      }

      return this.bindingModel ? this.bindingModel.textField : null;
   }

   getFieldType(): string {
      return "text";
   }

   openTextFormatPane() {
      if(this.isEditEnabled()) {
         // text field always edit the current binding
         if(this.getField()) {
            this.chartModel.chartSelection.regions = [];
         }

         if(this.field?.dataInfo?.fullName) {
            this.editorService.measureName = this.field.dataInfo.fullName;
         }

         this.onUpdateData.emit("showTextFormat");
      }
   }

   setAestheticRef(ref: AestheticInfo) {
      if(this.aggr) {
         this.aggr.textField = ref;
      }
      else {
         this.bindingModel.textField = ref;
      }
   }

   syncAllChartAggregateInfo(): void {
      if(!this.isEditEnabled) {
         return;
      }

      let aggrs: ChartAggregateRef[] = GraphUtil.getAestheticAggregateRefs(this.bindingModel);

      for(let aggr0 of aggrs) {
         aggr0.textField = Tool.clone(this.aggr.textField);
      }
   }

   protected isMixedField(): boolean {
      if(!GraphUtil.isAllChartAggregate(this.aggr)) {
         return false;
      }

      return !(<AllChartAggregateRef>(this.aggr)).visualPaneStatus.textFieldEditable;
   }

   protected isEditEnabled(): boolean {
      return super.isEditEnabled() && this.chartModel &&
         (this.field != null || this.chartModel.showValues &&
            !GraphTypes.isStock(this.chartModel.chartType) ||
            // labels always shows on tree.
            GraphTypes.CHART_TREE == this.chartModel.chartType);
   }

   private getMeasureNames(): string[] {
      if(this.chartModel.scatterMatrix) {
         return ["_YMeasureValue_"];
      }

      // tree text field is apply on target and not source.
      if(!!this.bindingModel.targetField) {
         return [this.bindingModel.targetField.fullName];
      }

      // map text field is apply on last geo.
      if(this.bindingModel.geoFields != null && this.bindingModel.geoFields.length > 0) {
         return [this.bindingModel.geoFields[this.bindingModel.geoFields.length - 1].fullName];
      }

      let measures = this.getMeasures(this.bindingModel.yfields);

      if(measures.length == 0) {
         measures = this.getMeasures(this.bindingModel.xfields);
      }

      return measures.sort();
   }

   private getMeasures(xfields: ChartRef[]): string[] {
      const mset = new Set(xfields
         .filter(r => r.measure && !(<ChartAggregateRef> r).discrete)
         .map(r => r.fullName));
      return Array.from(mset.values());
   }

   prev() {
      this.currentIndex--;

      if(this.currentIndex < 0) {
         this.currentIndex = this.measures.length - 1;
      }

      this.showCurrentFormat();
   }

   next() {
      this.currentIndex++;

      if(this.currentIndex >= this.measures.length) {
         this.currentIndex = 0;
      }

      this.showCurrentFormat();
   }

   private showCurrentFormat() {
      this.editorService.measureName = this.currentMeasure = this.measures[this.currentIndex];
      this.onUpdateData.emit("showTextFormat");
   }

   isMeasureRollerEnabled(): boolean {
      return !this.bindingModel.multiStyles && !this.field && this._isEditEnabled;
   }
}
