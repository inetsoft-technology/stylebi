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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { ChartSubType } from "../../model/recommender/chart-sub-type";
import { GaugeFaceType } from "../../model/recommender/gauge-face-type";
import { GraphTypes } from "../../../common/graph-types";
import { VSFilterType } from "../../model/recommender/vs-filter-type";
import { VSSubType } from "../../model/recommender/vs-sub-type";

@Component({
   selector: "object-type-icon",
   templateUrl: "object-type-icon.component.html",
   styleUrls: ["object-type-icon.component.scss"]
})
export class ObjectTypeIcon {
   @Input() type: VSSubType = null;
   @Output() onSelected: EventEmitter<VSSubType> = new EventEmitter<VSSubType>();

   private getPrefix(): string {
      if((<ChartSubType> this.type).facet) {
         return "_#(js:Facet) ";
      }

      return "";
   }

   getImageInfo(): any {
      const info = this.getImageInfo0();
      info.tooltip = this.getPrefix() + info.tooltip;

      return info;
   }

   private getImageInfo0(): any {
      let chartType: ChartSubType = <ChartSubType> this.type;

      if(this.type.classType == "ChartSubType") {
         if(chartType.wordCloud) {
            return {
               src: "assets/vs-wizard/chart-word-cloud.svg",
               tooltip: "_#(js:Word Cloud)"
            };
         }
         else if(chartType.heatMap) {
            return {
               src: "assets/vs-wizard/chart-heat-map.svg",
               tooltip: "_#(js:Heat Map)"
            };
         }
         else if(chartType.dotplot) {
            return {
               src: "assets/vs-wizard/chart-dotplot.svg",
               tooltip: "_#(js:Dot Plot)"
            };
         }
         else if(chartType.histogram) {
            return {
               src: "assets/vs-wizard/chart-histogram.svg",
               tooltip: "_#(js:Histogram)"
            };
         }
         else if(chartType.scatter) {
            return {
               src: "assets/vs-wizard/chart-scatter.svg",
               tooltip: "_#(js:Scatter Plot)"
            };
         }
         else if(chartType.scatterMatrix) {
            return {
               src: "assets/vs-wizard/chart-scatter-matrix.svg",
               tooltip: "_#(js:Scatter Matrix)"
            };
         }
         else if(chartType.donut) {
            return {
               src: "assets/vs-wizard/chart-donut.svg",
               tooltip: "_#(js:Donut)"
            };
         }
         else if(chartType.dualAxis) {
            return {
               src: "assets/vs-wizard/chart-bar.svg",
               tooltip: "_#(js:Dual Axis)"
            };
         }
      }

      switch(this.type.type) {
         case VSFilterType.SELECTION_LIST:
            return {
               src: "assets/vs-wizard/selection-list.svg",
               tooltip: "_#(js:Selection List)"
            };
         case VSFilterType.SELECTION_TREE:
            return {
               src: "assets/vs-wizard/selection-tree.svg",
               tooltip: "_#(js:Selection Tree)"
            };
         case VSFilterType.CALENDAR:
            return {
               src: "assets/vs-wizard/calendar.svg",
               tooltip: "_#(js:Calendar)"
            };
         case VSFilterType.RANGE_SLIDER:
            return {
               src: "assets/vs-wizard/range-slider.svg",
               tooltip: "_#(js:Range Slider)"
            };
         case GraphTypes.CHART_BAR.toString():
            if(chartType.rotated) {
               return {
                  src: "assets/vs-wizard/chart-hbar.svg",
                  tooltip: "_#(js:Horizontal Bar)"
               };
            }
            else {
               return {
                  src: "assets/vs-wizard/chart-bar.svg",
                  tooltip: "_#(js:Bar)"
               };
            }
         case GraphTypes.CHART_LINE.toString():
            return {
               src: "assets/vs-wizard/chart-line.svg",
               tooltip: "_#(js:Line)"
            };
         case GraphTypes.CHART_AREA.toString():
            return {
               src: "assets/vs-wizard/chart-area.svg",
               tooltip: "_#(js:Area)"
            };
         case GraphTypes.CHART_AREA_STACK.toString():
            return {
               src: "assets/vs-wizard/chart-area.svg",
               tooltip: "_#(js:Stack Area)"
            };
         case GraphTypes.CHART_POINT.toString():
            return {
               src: "assets/vs-wizard/chart-point.svg",
               tooltip: "_#(js:Point)"
            };
         case GraphTypes.CHART_PIE.toString():
            return {
               src: "assets/vs-wizard/chart-pie.svg",
               tooltip: "_#(js:Pie)"
            };
         case GraphTypes.CHART_RADAR.toString():
            return {
               src: "assets/vs-wizard/chart-radar.svg",
               tooltip: "_#(js:Radar)"
            };
         case GraphTypes.CHART_MAP.toString():
            return {
               src: "assets/vs-wizard/chart-map.svg",
               tooltip: "_#(js:Map)"
            };
         case GraphTypes.CHART_TREEMAP.toString():
            return {
               src: "assets/vs-wizard/chart-treemap.svg",
               tooltip: "_#(js:Treemap)"
            };
         case GraphTypes.CHART_SUNBURST.toString():
            return {
               src: "assets/vs-wizard/chart-sunburst.svg",
               tooltip: "_#(js:Sunburst)"
            };
         case GraphTypes.CHART_CIRCLE_PACKING.toString():
            return {
               src: "assets/vs-wizard/chart-circle-packing.svg",
               tooltip: "_#(js:Circle Packing)"
            };
         case GraphTypes.CHART_ICICLE.toString():
            return {
               src: "assets/vs-wizard/chart-icicle.svg",
               tooltip: "_#(js:Icicle)"
            };
         case GraphTypes.CHART_WATERFALL.toString():
            return {
               src: "assets/vs-wizard/chart-waterfall.svg",
               tooltip: "_#(js:Waterfall)"
            };
         case GraphTypes.CHART_PARETO.toString():
            return {
               src: "assets/vs-wizard/chart-pareto.svg",
               tooltip: "_#(js:Pareto)"
            };
         case GraphTypes.CHART_BAR_STACK.toString():
            if(chartType.rotated) {
               return {
                  src: "assets/vs-wizard/chart-stack-hbar.svg",
                  tooltip: "_#(js:Horizontal Stack Bar)"
               };
            }
            else {
               return {
                  src: "assets/vs-wizard/chart-stack-bar.svg",
                  tooltip: "_#(js:Stack Bar)"
               };
            }
         case GraphTypes.CHART_LINE_STACK.toString():
            return {
               src: "assets/vs-wizard/chart-stack-line.svg",
               tooltip: "_#(js:Stack Line)"
            };
         case GraphTypes.CHART_STEP.toString():
            return {
               src: "assets/vs-wizard/chart-step.svg",
               tooltip: "_#(js:Step Line)"
            };
         case GraphTypes.CHART_STEP_STACK.toString():
            return {
               src: "assets/vs-wizard/chart-stack-step.svg",
               tooltip: "_#(js:Stack Step Line)"
            };
         case GraphTypes.CHART_JUMP.toString():
            return {
               src: "assets/vs-wizard/chart-jump.svg",
               tooltip: "_#(js:Jump Line)"
            };
         case GraphTypes.CHART_STEP_AREA.toString():
            return {
               src: "assets/vs-wizard/chart-step-area.svg",
               tooltip: "_#(js:Step Area)"
            };
         case GraphTypes.CHART_STEP_AREA_STACK.toString():
            return {
               src: "assets/vs-wizard/chart-stack-step-area.svg",
               tooltip: "_#(js:Stack Step Area)"
            };
         case GraphTypes.CHART_POINT_STACK.toString():
            return {
               src: "assets/vs-wizard/chart-stack-point.svg",
               tooltip: "_#(js:Stack Point)"
            };
         case GraphTypes.CHART_BOXPLOT.toString():
            return {
               src: "assets/vs-wizard/chart-boxplot.svg",
               tooltip: "_#(js:Box Plot)"
            };
         case GraphTypes.CHART_MEKKO.toString():
            return {
               src: "assets/vs-wizard/chart-mekko.svg",
               tooltip: "_#(js:Marimekko)"
            };
         case GraphTypes.CHART_TREE.toString():
            return {
               src: "assets/vs-wizard/chart-tree.svg",
               tooltip: "_#(js:Tree)"
            };
         case GraphTypes.CHART_NETWORK.toString():
            return {
               src: "assets/vs-wizard/chart-network.svg",
               tooltip: "_#(js:Network)"
            };
         case GraphTypes.CHART_CIRCULAR.toString():
            return {
               src: "assets/vs-wizard/chart-circle-network.svg",
               tooltip: "_#(js:Circular Network)"
            };
         case GraphTypes.CHART_GANTT.toString():
            return {
               src: "assets/vs-wizard/chart-gantt.svg",
               tooltip: "_#(js:Gantt)"
            };
         case GraphTypes.CHART_FUNNEL.toString():
            return {
               src: "assets/vs-wizard/chart-funnel.svg",
               tooltip: "_#(js:Funnel)"
            };
         case GraphTypes.CHART_INTERVAL.toString():
            return {
               src: "assets/vs-wizard/chart-interval.svg",
               tooltip: "_#(js:Interval)"
            };
         case GraphTypes.CHART_SCATTER_CONTOUR.toString():
            return {
               src: "assets/vs-wizard/chart-scatter-contour.svg",
               tooltip: "_#(js:Scatter Contour)"
            };
         case GraphTypes.CHART_MAP_CONTOUR.toString():
            return {
               src: "assets/vs-wizard/chart-map-contour.svg",
               tooltip: "_#(js:Contour Map)"
            };
         case GaugeFaceType.GAUGE_10120:
            return {
               src: "assets/vs-wizard/gauge-10120.svg",
               tooltip: "_#(js:Gauge)"
            };
         case GaugeFaceType.GAUGE_10220:
            return {
               src: "assets/vs-wizard/gauge-10220.svg",
               tooltip: "_#(js:Gauge)"
            };
         case GaugeFaceType.GAUGE_10910:
            return {
               src: "assets/vs-wizard/gauge-10910.svg",
               tooltip: "_#(js:Gauge)"
            };
         case GaugeFaceType.GAUGE_90820:
            return {
               src: "assets/vs-wizard/gauge-90820.svg",
               tooltip: "_#(js:Gauge)"
            };
         case GaugeFaceType.GAUGE_10920:
            return {
               src: "assets/vs-wizard/gauge-10920.svg",
               tooltip: "_#(js:Gauge)"
            };
         case GaugeFaceType.GAUGE_13000:
            return {
               src: "assets/vs-wizard/gauge-13000.svg",
               tooltip: "_#(js:Gauge)"
            };
         default:
            return {
               src: null,
               tooltip: null
            };
      }
   }

   isFacet() {
      if(!!this.type && this.type.classType == "ChartSubType") {
         let subType: ChartSubType = <ChartSubType> this.type;

         return subType.facet;
      }

      return false;
   }

   isDualAxis() {
      if(!!this.type && this.type.classType == "ChartSubType") {
         let subType: ChartSubType = <ChartSubType> this.type;

         return subType.dualAxis;
      }

      return false;
   }
}
