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
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { GraphTypes } from "../../common/graph-types";
import { UIContextService } from "../../common/services/ui-context.service";
import { ModelService } from "../../widget/services/model.service";
import {
   FeatureFlagsService,
   FeatureFlagValue
} from "../../../../../shared/feature-flags/feature-flags.service";

export interface ChartStyle {
   label: string;
   data: number;
   custom?: boolean;
}

export interface ChartStylesModel {
   styles: ChartStyle[];
   stackStyles: ChartStyle[];
}

@Component({
   selector: "chart-style-pane",
   styleUrls: ["chart-style-pane.component.scss"],
   templateUrl: "chart-style-pane.component.html"
})
export class ChartStylePane implements OnInit, OnChanges {
   @Input() chartType: number;
   @Input() multiStyles: boolean;
   @Input() refName: string;
   @Input() popup: boolean = true;
   @Input() stackMeasures: boolean;
   @Input() stackMeasuresVisible: boolean;
   @Input() stackMeasuresEnabled: boolean;
   @Input() chartStyles: ChartStylesModel;
   @Input() customChartTypes: string[];
   @Output() changeChartType: EventEmitter<number> = new EventEmitter<number>();
   @Output() changeMultiStyles: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() changeStackMeasures: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() apply: EventEmitter<any> = new EventEmitter<any>();
   stylesModel: Array<{label: string, data: number}>;
   stylesRows: Array<Array<{label: string, data: number}>>;

   constructor(private uiContextService: UIContextService,
               private modelService: ModelService,
               private featureFlagService: FeatureFlagsService)
   {
   }

   ngOnInit() {
      this.createStyles(this.stackChecked());
   }

   ngOnChanges(changes: SimpleChanges) {
      this.createStyles(this.stackChecked());
   }

   private createStyles(stack: boolean) {
      let styles = [];

      if(this.chartStyles) {
         styles = stack ? this.chartStyles.stackStyles : this.chartStyles.styles;
      }

      this.stylesModel = styles
         .filter(pair => !pair.custom ||
            this.customChartTypes && this.customChartTypes.includes(pair.data + ""));
      this.stylesRows = this.stylesToRows();
   }

   getCssIcon(item: any): string {
      let css: string = "chart-style-img chart-style-img-";

      switch(item.data) {
         case GraphTypes.CHART_AUTO:
            css += "auto";
            break;
         case GraphTypes.CHART_BAR:
            css += "bar";
            break;
         case GraphTypes.CHART_3D_BAR:
            css += "3dbar";
            break;
         case GraphTypes.CHART_LINE:
            css += "line";
            break;
         case GraphTypes.CHART_AREA:
            css += "area";
            break;
         case GraphTypes.CHART_POINT:
            css += "point";
            break;
         case GraphTypes.CHART_PIE:
            css += "pie";
            break;
         case GraphTypes.CHART_DONUT:
            css += "donut";
            break;
         case GraphTypes.CHART_SUNBURST:
            css += "sunburst";
            break;
         case GraphTypes.CHART_TREEMAP:
            css += "treemap";
            break;
         case GraphTypes.CHART_CIRCLE_PACKING:
            css += "circle-pack";
            break;
         case GraphTypes.CHART_ICICLE:
            css += "icicle";
            break;
         case GraphTypes.CHART_3D_PIE:
            css += "3dpie";
            break;
         case GraphTypes.CHART_RADAR:
            css += "radar";
            break;
         case GraphTypes.CHART_FILL_RADAR:
            css += "filledRadar";
            break;
         case GraphTypes.CHART_STOCK:
            css += "stock";
            break;
         case GraphTypes.CHART_CANDLE:
            css += "candle";
            break;
         case GraphTypes.CHART_BOXPLOT:
            css += "boxplot";
            break;
         case GraphTypes.CHART_MEKKO:
            css += "mekko";
            break;
         case GraphTypes.CHART_TREE:
            css += "tree";
            break;
         case GraphTypes.CHART_NETWORK:
            css += "network";
            break;
         case GraphTypes.CHART_CIRCULAR:
            css += "circle-network";
            break;
         case GraphTypes.CHART_GANTT:
            css += "gantt";
            break;
         case GraphTypes.CHART_FUNNEL:
            css += "funnel";
            break;
         case GraphTypes.CHART_WATERFALL:
            css += "waterfall";
            break;
         case GraphTypes.CHART_PARETO:
            css += "pareto";
            break;
         case GraphTypes.CHART_MAP:
            css += "map";
            break;
         case GraphTypes.CHART_BAR_STACK:
            css += "stackBar";
            break;
         case GraphTypes.CHART_3D_BAR_STACK:
            css += "stack3DBar";
            break;
         case GraphTypes.CHART_LINE_STACK:
            css += "stackLine";
            break;
         case GraphTypes.CHART_AREA_STACK:
            css += "stackArea";
            break;
         case GraphTypes.CHART_POINT_STACK:
            css += "stackPoint";
            break;
         case GraphTypes.CHART_STEP:
            css += "step";
            break;
         case GraphTypes.CHART_STEP_STACK:
            css += "stack-step";
            break;
         case GraphTypes.CHART_JUMP:
            css += "jump";
            break;
         case GraphTypes.CHART_STEP_AREA:
            css += "step-area";
            break;
         case GraphTypes.CHART_STEP_AREA_STACK:
            css += "stack-step-area";
            break;
         case GraphTypes.CHART_INTERVAL:
            css += "interval";
            break;
         case GraphTypes.CHART_SCATTER_CONTOUR:
            css += "scatter-contour";
            break;
         case GraphTypes.CHART_MAP_CONTOUR:
            css += "map-contour";
            break;
         default:
      }

      return css;
   }

   updateChartType(chartType: number): void {
      this.changeChartType.emit(chartType);
   }

   multiChanged(multiStyles: boolean): void {
      this.changeMultiStyles.emit(multiStyles);
      this.changeChartType.emit(GraphTypes.CHART_AUTO);
   }

   stackEnabled(): boolean {
      if(this.multiStyles && !this.refName) {
         return false;
      }

      return this.chartType == GraphTypes.CHART_BAR
         || this.chartType == GraphTypes.CHART_3D_BAR
         || this.chartType == GraphTypes.CHART_LINE
         || this.chartType == GraphTypes.CHART_AREA
         || this.chartType == GraphTypes.CHART_POINT
         || this.chartType == GraphTypes.CHART_STEP
         || this.chartType == GraphTypes.CHART_STEP_AREA
         || this.stackChecked();
   }

   stackChanged(selected: boolean): void {
      let itemIndex = this.getItemIndex();
      this.createStyles(selected);
      this.changeChartType.emit(this.stylesModel[itemIndex].data);
   }

   stackChecked(): boolean {
      return this.chartType == GraphTypes.CHART_BAR_STACK
         || this.chartType == GraphTypes.CHART_3D_BAR_STACK
         || this.chartType == GraphTypes.CHART_LINE_STACK
         || this.chartType == GraphTypes.CHART_AREA_STACK
         || this.chartType == GraphTypes.CHART_STEP_STACK
         || this.chartType == GraphTypes.CHART_STEP_AREA_STACK
         || this.chartType == GraphTypes.CHART_POINT_STACK;
   }

   stackMeasuresChanged(selected: boolean): void {
      this.changeStackMeasures.emit(selected);
   }

   getImageBorder(chartType: number): string {
      return chartType == this.chartType ? "2px solid #C0C0C0" : "none";
   }

   multiDisabled(): boolean {
      return GraphTypes.isMergedGraphType(this.chartType);
   }

   private getItemIndex(): number {
      for(let i = 0; i < this.stylesModel.length; i++) {
         if(this.stylesModel[i].data == this.chartType) {
            return i;
         }
      }

      return 0;
   }

   applyClick() {
      this.apply.emit(false);
   }

   stylesToRows(): Array<Array<{label: string, data: number}>> {
      const rows: Array<Array<{label: string, data: number}>> = [];
      let row: number = 0;
      rows.push([]);

      for(let i: number = 0; i < this.stylesModel.length; i++) {
         if(rows[rows.length - 1].length >= 6) {
            rows.push([]);
         }

         rows[rows.length - 1].push(this.stylesModel[i]);
      }

      return rows;
   }
}
