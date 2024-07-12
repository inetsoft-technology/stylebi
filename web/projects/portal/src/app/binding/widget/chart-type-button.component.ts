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
import { Component, Input, Output, ViewChild, OnDestroy, EventEmitter } from "@angular/core";
import { NgbModal, NgbDropdown } from "@ng-bootstrap/ng-bootstrap";
import { GraphTypes } from "../../common/graph-types";
import { ChartAggregateRef } from "../data/chart/chart-aggregate-ref";
import { ChartRef } from "../../common/data/chart-ref";
import { ChartEditorService } from "../services/chart/chart-editor.service";
import { Tool } from "../../../../../shared/util/tool";
import { FixedDropdownDirective } from "../../widget/fixed-dropdown/fixed-dropdown.directive";
import { GraphUtil } from "../util/graph-util";
import { ComponentTool } from "../../common/util/component-tool";
import { ChartBindingModel } from "../data/chart/chart-binding-model";
import { ChartStylesModel } from "./chart-style-pane.component";

@Component({
   selector: "chart-type-button",
   templateUrl: "chart-type-button.component.html",
   styleUrls: ["chart-type-button.component.scss"]
})
export class ChartTypeButton {
   @Input() chartType: number;
   @Input() multiStyles: boolean;
   @Input() stackMeasures: boolean;
   @Input() refName: string;
   @Output() onChange = new EventEmitter<any>();
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;
   url: string = "/api/adhoc/chart/changeChartType";
   private outsideClickListener: () => any;
   customChartTypes: string[];
   chartStyles: ChartStylesModel;

   constructor(private editorService: ChartEditorService,
               private modalService: NgbModal)
   {
      editorService.getCustomChartTypes()
         .subscribe((types: string[]) => this.customChartTypes = types);
      editorService.getChartStyles()
         .subscribe((styles: ChartStylesModel) => this.chartStyles = styles);
   }

   private get originalChartType(): number {
      let cAggr: ChartAggregateRef = this.getCurrentAggregate();
      let binding = this.editorService.bindingModel;

      return cAggr ? cAggr.chartType : binding.chartType;
   }

   private get originalMultiStyles(): boolean {
      let binding = this.editorService.bindingModel;

      return binding.multiStyles;
   }

   private get originalStackMeasures(): boolean {
      let binding = this.editorService.bindingModel;

      return binding.stackMeasures;
   }

   updateChartType(chartType: number): void {
      this.chartType = chartType;
   }

   updateMultiStyles(multiStyles: boolean): void {
      this.multiStyles = multiStyles;
   }

   updateStackMeasures(stackMeasures: boolean): void {
      this.stackMeasures = stackMeasures;
   }

   changeChartType(): void {
      this.checkValid();
      this.dropdown.close();
   }

   checkValid(): void {
      if(this.originalChartType === this.chartType &&
         this.originalMultiStyles === this.multiStyles &&
         this.originalStackMeasures === this.stackMeasures)
      {
         return;
      }

      let modelRefs: ChartRef[] = GraphUtil.getModelRefs(this.editorService.bindingModel);

      if(this.refName) {
         for(let i = 0; i < modelRefs.length; i++) {
            let aggregate: ChartAggregateRef = <ChartAggregateRef> modelRefs[i];

            if(aggregate.fullName == this.refName) {
               continue;
            }

            // we shouldn't use rtchartType otherwise we can't create two pies (for donut)
            if(!GraphTypes.isCompatible(this.chartType, aggregate.chartType)) {
               let label = GraphTypes.getDisplayName(this.chartType);
               let clabel = GraphTypes.getDisplayName(aggregate.rtchartType);
               let message = "_#(js:em.common.graph.incompatibleTypes)" +
                  "_*" + label + "," + clabel;
               this.resetChartType();
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)", message);
               return;
            }
         }
      }

      this.processChangeChartType();
   }

   processChangeChartType(): void {
      this.editorService.changeChartType(this.chartType, this.multiStyles,
         this.stackMeasures, this.refName);
      this.onChange.emit(this.chartType);
   }

   getIconPath(): string {
      if(this.multiStyles && !this.refName) {
         return "chart-multi-style-icon";
      }

      switch(this.chartType) {
         case GraphTypes.CHART_AUTO:
            return "auto-chart-type-icon";
         case GraphTypes.CHART_BAR:
            return "chart-bar-icon";
         case GraphTypes.CHART_3D_BAR:
            return "chart-3D-bar-icon";
         case GraphTypes.CHART_LINE:
            return "chart-line-icon";
         case GraphTypes.CHART_AREA:
            return "chart-area-icon";
         case GraphTypes.CHART_POINT:
            return "chart-scattered-icon";
         case GraphTypes.CHART_PIE:
            return "chart-pie-icon";
         case GraphTypes.CHART_DONUT:
            return "chart-donut-icon";
         case GraphTypes.CHART_SUNBURST:
            return "chart-sunburst-icon";
         case GraphTypes.CHART_TREEMAP:
            return "chart-treemap-icon";
         case GraphTypes.CHART_CIRCLE_PACKING:
            return "chart-circle-pack-icon";
         case GraphTypes.CHART_ICICLE:
            return "chart-icicle-icon";
         case GraphTypes.CHART_3D_PIE:
            return "chart-3D-pie-icon";
         case GraphTypes.CHART_RADAR:
            return "chart-radar-icon";
         case GraphTypes.CHART_FILL_RADAR:
            return "chart-radar-filled-icon";
         case GraphTypes.CHART_STOCK:
            return "chart-stock-icon";
         case GraphTypes.CHART_CANDLE:
            return "chart-candle-icon";
         case GraphTypes.CHART_BOXPLOT:
            return "chart-boxplot-icon";
         case GraphTypes.CHART_MEKKO:
            return "chart-mekko-icon";
         case GraphTypes.CHART_TREE:
            return "chart-tree-icon";
         case GraphTypes.CHART_NETWORK:
            return "chart-network-icon";
         case GraphTypes.CHART_CIRCULAR:
            return "chart-circle-network-icon";
         case GraphTypes.CHART_GANTT:
            return "chart-gantt-icon";
         case GraphTypes.CHART_FUNNEL:
            return "chart-funnel-icon";
         case GraphTypes.CHART_WATERFALL:
            return "chart-waterfall-icon";
         case GraphTypes.CHART_PARETO:
            return "chart-pareto-icon";
         case GraphTypes.CHART_MAP:
            return "chart-map-icon";
         case GraphTypes.CHART_BAR_STACK:
            return "chart-bar-stacked-icon";
         case GraphTypes.CHART_3D_BAR_STACK:
            return "chart-3D-bar-stacked-icon";
         case GraphTypes.CHART_LINE_STACK:
            return "chart-line-stacked-icon";
         case GraphTypes.CHART_STEP:
            return "chart-step-icon";
         case GraphTypes.CHART_STEP_STACK:
            return "chart-step-stacked-icon";
         case GraphTypes.CHART_JUMP:
            return "chart-jump-icon";
         case GraphTypes.CHART_STEP_AREA:
            return "chart-step-area-icon";
         case GraphTypes.CHART_STEP_AREA_STACK:
            return "chart-step-area-stacked-icon";
         case GraphTypes.CHART_AREA_STACK:
            return "chart-area-stacked-icon";
         case GraphTypes.CHART_POINT_STACK:
            return "chart-scattered-stacked-icon";
         case GraphTypes.CHART_INTERVAL:
            return "chart-interval-icon";
         case GraphTypes.CHART_SCATTER_CONTOUR:
            return "chart-scatter-contour-icon";
         case GraphTypes.CHART_MAP_CONTOUR:
            return "chart-map-contour-icon";
         default:
            return "auto-chart-type-icon";
      }
   }

   private getCurrentAggregate(): ChartAggregateRef {
      let aggregates: ChartRef[] =
         GraphUtil.getModelRefs(this.editorService.bindingModel);

      for(let aggr of aggregates) {
         if(aggr.fullName == this.refName) {
            return <ChartAggregateRef> aggr;
         }
      }

      return null;
   }

   private resetChartType(): void {
      this.chartType = this.originalChartType;
   }

   toggled(open: boolean) {
      if(!open) {
         this.changeChartType();
      }
   }

   get isStackMeasures(): boolean {
      return this.stackMeasures && !this.editorService.bindingModel.separated;
   }

   isStackMeasuresEnabled(): boolean {
      const bindingModel = this.editorService.bindingModel;
      const secondaries = new Set();

      bindingModel.xfields.concat(bindingModel.yfields)
         .filter(field => (<ChartAggregateRef> field).secondaryY != null)
         .forEach(field => secondaries.add((<ChartAggregateRef> field).secondaryY));

      if(secondaries.size > 1) {
         return false;
      }

      if(this.multiStyles) {
         let stack = this.checkType(bindingModel,
            (ctype) => GraphTypes.isStack(ctype) && ctype != GraphTypes.CHART_FUNNEL);
         let ctypes = new Set<number>();

         this.checkType(bindingModel, (ctype) => {
            ctypes.add(ctype);
            return false;
         });

         return stack && ctypes.size == 1 && !bindingModel.separated;
      }
      else {
         const type = this.chartType == GraphTypes.CHART_AUTO
            ? bindingModel.rtchartType : this.chartType;

         return GraphTypes.isStack(type) && type != GraphTypes.CHART_FUNNEL &&
            !bindingModel.separated;
      }
   }

   checkType(bindingModel: ChartBindingModel, callback: (ctype: number) => boolean): boolean {
      if(bindingModel == null) {
         return false;
      }

      let fields = new Array<ChartRef[]>();
      fields.push(bindingModel.xfields);
      fields.push(bindingModel.yfields);

      for(let i = 0; i < fields.length; i++) {
         const axisFields = fields[i];

         for(let j = 0; j < axisFields.length; j++) {
            const field = axisFields[j];

            if(field.measure) {
               let ctype = bindingModel.chartType == GraphTypes.CHART_AUTO ?
                  (<ChartAggregateRef> field).rtchartType : (<ChartAggregateRef> field).chartType;

               if(callback(ctype)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   isStackMeasuresVisible(): boolean {
      if(!this.editorService.bindingModel) {
         return false;
      }

      return GraphUtil.getXYAggregateRefs(this.editorService.bindingModel).length > 1 &&
         this.isStackMeasuresEnabled();
   }
}
