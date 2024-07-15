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
import { ChartBindingModel } from "../binding/data/chart/chart-binding-model";
import { VSChartModel } from "../vsobjects/model/vs-chart-model";

/**
 * Graph types.
 * @version 12.2
 * @author InetSoft Technology Corp
 */

export class GraphTypes {
   /**
    * Graph type constant for auto charts.
    */
   static get CHART_AUTO(): number {
      return 0x00;
   }

   /**
    * Graph type constant for bar charts.
    */
   static get CHART_BAR(): number {
      return 0x01;
   }

   /**
    * Graph type constant for bar charts (stack).
    */
   static get CHART_BAR_STACK(): number {
      return 0x21;
   }

   /**
    * Graph type constant for bar charts with a 3D effect.
    */
   static get CHART_3D_BAR(): number {
      return 0x02;
   }

   /**
    * Graph type constant for bar charts with a 3D effect (stack).
    */
   static get CHART_3D_BAR_STACK(): number {
      return 0x22;
   }

   /**
    * Graph type constant for pie charts.
    */
   static get CHART_PIE(): number {
      return 0x03;
   }

   /**
    * Graph type constant for donut charts.
    */
   static get CHART_DONUT(): number {
      return 0x13;
   }

   /**
    * Graph type constant for sunburst charts.
    */
   static get CHART_SUNBURST(): number {
      return 0x41;
   }

   /**
    * Graph type constant for treemap charts.
    */
   static get CHART_TREEMAP(): number {
      return 0x42;
   }

   /**
    * Graph type constant for circlepacking charts.
    */
   static get CHART_CIRCLE_PACKING(): number {
      return 0x43;
   }

   /**
    * Graph type constant for icicle charts.
    */
   static get CHART_ICICLE(): number {
      return 0x44;
   }

   /**
    * Graph type constant for pie charts with a 3D effect.
    */
   static get CHART_3D_PIE(): number {
      return 0x04;
   }

   /**
    * Graph type constant for line charts.
    */
   static get CHART_LINE(): number {
      return 0x05;
   }

   /**
    * Graph type constant for line charts (stack).
    */
   static get CHART_LINE_STACK(): number {
      return 0x23;
   }

   /**
    * Graph type constant for area charts.
    */
   static get CHART_AREA(): number {
      return 0x06;
   }

   /**
    * Graph type constant for area charts (stack).
    */
   static get CHART_AREA_STACK(): number {
      return 0x24;
   }

   /**
    * Graph type constant for stock charts.
    */
   static get CHART_STOCK(): number {
      return 0x07;
   }

   /**
    * Graph type constant for point charts.
    */
   static get CHART_POINT(): number {
      return 0x08;
   }

   /**
    * Graph type constant for point charts (stack).
    */
   static get CHART_POINT_STACK(): number {
      return 0x25;
   }

   /**
    * Graph type constant for radar charts.
    */
   static get CHART_RADAR(): number {
      return 0x09;
   }

   /**
    * Graph type constant for filled radar charts.
    */
   static get CHART_FILL_RADAR(): number {
      return 0x0A;
   }

   /**
    * Graph type constant for candle charts.
    */
   static get CHART_CANDLE(): number {
      return 0x0B;
   }

   /**
    * Graph type constant for boxplot charts.
    */
   static get CHART_BOXPLOT(): number {
      return 0x45;
   }

   /**
    * Graph type constant for marimekko charts.
    */
   static get CHART_MEKKO(): number {
      return 0x46;
   }

   /**
    * Graph type constant for tree charts.
    */
   static get CHART_TREE(): number {
      return 0x47;
   }

   /**
    * Graph type constant for network charts.
    */
   static get CHART_NETWORK(): number {
      return 0x48;
   }

   /**
    * Graph type constant for gantt charts.
    */
   static get CHART_GANTT(): number {
      return 0x4C;
   }

   /**
    * Graph type constant for funnel charts.
    */
   static get CHART_FUNNEL(): number {
      return 0x28;
   }

   /**
    * Graph type constant for step charts.
    */
   static get CHART_STEP(): number {
      return 0x49;
   }

   /**
    * Graph type constant for jump line charts.
    */
   static get CHART_JUMP(): number {
      return 0x4A;
   }

   /**
    * Graph type constant for step area charts.
    */
   static get CHART_STEP_AREA(): number {
      return 0x4E;
   }

   /**
    * Graph type constant for stacked step line charts.
    */
   static get CHART_STEP_STACK(): number {
      return 0x26;
   }

   /**
    * Graph type constant for stacked step area charts.
    */
   static get CHART_STEP_AREA_STACK(): number {
      return 0x27;
   }

   /**
    * Graph type constant for circular network charts.
    */
   static get CHART_CIRCULAR(): number {
      return 0x4B;
   }

   /**
    * Graph type constant for waterfall charts.
    */
   static get CHART_WATERFALL(): number {
      return 0x0C;
   }

   /**
    * Graph type constant for pareto charts.
    */
   static get CHART_PARETO(): number {
      return 0x0D;
   }

   /**
    * Graph type constant for map charts.
    */
   static get CHART_MAP(): number {
      return 0x0E;
   }

   /**
    * Graph type constant for interval charts.
    */
   static get CHART_INTERVAL(): number {
      return 0x4F;
   }

   /**
    * Graph type constant for scattered contour charts.
    */
   static get CHART_SCATTER_CONTOUR(): number {
      return 0x50;
   }

   /**
    * Graph type constant for contour map charts.
    */
   static get CHART_MAP_CONTOUR(): number {
      return 0x51;
   }

   /**
    * Check if supports line.
    */
   static supportsLine(type: number, chartBindingModel: ChartBindingModel): boolean {
      return type == GraphTypes.CHART_LINE ||
         type == GraphTypes.CHART_LINE_STACK ||
         type == GraphTypes.CHART_STEP ||
         type == GraphTypes.CHART_STEP_STACK ||
         type == GraphTypes.CHART_JUMP ||
         type == GraphTypes.CHART_STEP_AREA ||
         type == GraphTypes.CHART_STEP_AREA_STACK ||
         type == GraphTypes.CHART_AREA ||
         type == GraphTypes.CHART_AREA_STACK ||
         type == GraphTypes.CHART_FILL_RADAR ||
         type == GraphTypes.CHART_RADAR && !chartBindingModel.pointLine ||
         type == GraphTypes.CHART_TREE ||
         type == GraphTypes.CHART_NETWORK ||
         type == GraphTypes.CHART_CIRCULAR ||
         GraphTypes.isGeo(type) && chartBindingModel.pathField != null;
   }

   /**
    * Check if supports texture frame.
    */
   static supportsTexture(type: number): boolean {
      return type == GraphTypes.CHART_BAR ||
         type == GraphTypes.CHART_BAR_STACK ||
         type == GraphTypes.CHART_3D_BAR ||
         type == GraphTypes.CHART_3D_BAR_STACK ||
         type == GraphTypes.CHART_PIE ||
         type == GraphTypes.CHART_DONUT ||
         type == GraphTypes.CHART_SUNBURST ||
         type == GraphTypes.CHART_TREEMAP ||
         type == GraphTypes.CHART_CIRCLE_PACKING ||
         type == GraphTypes.CHART_ICICLE ||
         type == GraphTypes.CHART_3D_PIE ||
         type == GraphTypes.CHART_AUTO ||
         type == GraphTypes.CHART_WATERFALL ||
         type == GraphTypes.CHART_PARETO ||
         type == GraphTypes.CHART_CANDLE ||
         type == GraphTypes.CHART_BOXPLOT ||
         type == GraphTypes.CHART_MEKKO ||
         type == GraphTypes.CHART_GANTT ||
         type == GraphTypes.CHART_FUNNEL ||
         type == GraphTypes.CHART_INTERVAL;
   }

   /**
    * Check if is merged graph type.
    */
   static isMergedGraphType(type: number): boolean {
      return type == GraphTypes.CHART_RADAR
         || type == GraphTypes.CHART_FILL_RADAR
         || type == GraphTypes.CHART_STOCK
         || type == GraphTypes.CHART_CANDLE
         || type == GraphTypes.CHART_BOXPLOT
         || type == GraphTypes.CHART_MEKKO
         || type == GraphTypes.CHART_TREE
         || type == GraphTypes.CHART_NETWORK
         || type == GraphTypes.CHART_CIRCULAR
         || GraphTypes.isGeo(type)
         || type == GraphTypes.CHART_TREEMAP
         || type == GraphTypes.CHART_SUNBURST
         || type == GraphTypes.CHART_ICICLE
         || type == GraphTypes.CHART_CIRCLE_PACKING
         || type == GraphTypes.CHART_GANTT
         || type == GraphTypes.CHART_FUNNEL
         || type == GraphTypes.CHART_SCATTER_CONTOUR;
   }

   /**
    * Check if is incompatible chart type, including merged graph,
    * waterfall, pareto, pie.
    */
   static isIncompatibleType(type: number): boolean {
      return GraphTypes.isMergedGraphType(type)
         || type == GraphTypes.CHART_WATERFALL
         || type == GraphTypes.CHART_PARETO;
   }

   /**
    * Check if is line graph.
    */
   static isLine(type: number): boolean {
      return type == GraphTypes.CHART_LINE || type == GraphTypes.CHART_LINE_STACK ||
         type == GraphTypes.CHART_STEP || type == GraphTypes.CHART_JUMP ||
         type == GraphTypes.CHART_STEP_STACK;
   }

   /**
    * Check if is bar graph.
    */
   static isBar(type: number): boolean {
      return type == GraphTypes.CHART_BAR || type == GraphTypes.CHART_BAR_STACK ||
         type == GraphTypes.CHART_3D_BAR || type == GraphTypes.CHART_3D_BAR_STACK;
   }

   /**
    * Check if is area graph.
    */
   static isArea(type: number): boolean {
      return type == GraphTypes.CHART_AREA || type == GraphTypes.CHART_AREA_STACK ||
         type == GraphTypes.CHART_STEP_AREA || type == GraphTypes.CHART_STEP_AREA_STACK;
   }

   /**
    * Check if is stack graph.
    */
   static isStack(type: number): boolean {
      return (type & 0x20) == 0x20;
   }

   /**
    * Check if is 3D bar.
    */
   static is3DBar(type: number): boolean {
      return type == GraphTypes.CHART_3D_BAR
         || type == GraphTypes.CHART_3D_BAR_STACK;
   }

   /**
    * Check if is point graph.
    */
   static isPoint(type: number): boolean {
      return type == GraphTypes.CHART_POINT
         || type == GraphTypes.CHART_POINT_STACK;
   }

   /**
    * Check if is pie graph.
    */
   static isPie(type: number): boolean {
      return type == GraphTypes.CHART_PIE ||
         type == GraphTypes.CHART_DONUT ||
         type == GraphTypes.CHART_3D_PIE;
   }

   /**
    * Check if is map graph.
    */
   static isMap(type: number): boolean {
      return type == GraphTypes.CHART_MAP;
   }

   static isGeo(type: number): boolean {
      return GraphTypes.isMap(type) || GraphTypes.isMapContour(type);
   }

   /**
    * Check if is auto graph.
    */
   static isAuto(type: number): boolean {
      return type == GraphTypes.CHART_AUTO;
   }

   /**
    * Check if is radar graph.
    */
   static isRadar(type: number): boolean {
      return type == GraphTypes.CHART_RADAR ||
         type == GraphTypes.CHART_FILL_RADAR;
   }

   /**
    * Check if is treemap (equivalent) graph.
    */
   static isTreemap(type: number): boolean {
      return type == GraphTypes.CHART_TREEMAP ||
         type == GraphTypes.CHART_SUNBURST ||
         type == GraphTypes.CHART_CIRCLE_PACKING ||
         type == GraphTypes.CHART_ICICLE;
   }

   /**
    * Check if is candle graph.
    */
   static isCandle(type: number): boolean {
      return type == GraphTypes.CHART_CANDLE;
   }

   /**
    * Check if is stock graph.
    */
   static isStock(type: number): boolean {
      return type == GraphTypes.CHART_STOCK;
   }

   static isMekko(type: number): boolean {
      return type == GraphTypes.CHART_MEKKO;
   }

   static isBoxplot(type: number): boolean {
      return type == GraphTypes.CHART_BOXPLOT;
   }

   static isRelation(type: number): boolean {
      return type == GraphTypes.CHART_TREE || type == GraphTypes.CHART_NETWORK ||
         type == GraphTypes.CHART_CIRCULAR;
   }

   static isGantt(type: number): boolean {
      return type == GraphTypes.CHART_GANTT;
   }

   static isFunnel(type: number): boolean {
      return type == GraphTypes.CHART_FUNNEL;
   }

   /**
    * Check if is waterfall graph.
    */
   static isWaterfall(type: number): boolean {
      return type == GraphTypes.CHART_WATERFALL;
   }

   /**
    * Check if requires polar coord.
    */
   static isPolar(type: number): boolean {
      return GraphTypes.isPie(type) ||
         type == GraphTypes.CHART_RADAR ||
         type == GraphTypes.CHART_FILL_RADAR;
   }

   /**
    * Check if is pareto graph..
    */
   static isPareto(type: number): boolean {
      return type == GraphTypes.CHART_PARETO;
   }

   static isInterval(type: number): boolean {
      return type == GraphTypes.CHART_INTERVAL;
   }

   static isScatteredContour(type: number): boolean {
      return type == GraphTypes.CHART_SCATTER_CONTOUR;
   }

   static isMapContour(type: number): boolean {
      return type == GraphTypes.CHART_MAP_CONTOUR;
   }

   static isContour(type: number): boolean {
      return GraphTypes.isScatteredContour(type) || GraphTypes.isMapContour(type);
   }

   static isCompatible(type1: number, type2: number): boolean {
      if(type1 == type2) {
         return true;
      }

      if(GraphTypes.isIncompatibleType(type1) || GraphTypes.isIncompatibleType(type2)) {
         return false;
      }

      if((GraphTypes.isPie(type1) || type1 == GraphTypes.CHART_AUTO) &&
         (GraphTypes.isPie(type2) || type2 == GraphTypes.CHART_AUTO))
      {
         return true;
      }

      if(type1 == GraphTypes.CHART_AUTO || type2 == GraphTypes.CHART_AUTO) {
         return true;
      }

      if(type1 == GraphTypes.CHART_BAR ||
         type1 == GraphTypes.CHART_BAR_STACK ||
         type1 == GraphTypes.CHART_LINE ||
         type1 == GraphTypes.CHART_LINE_STACK ||
         type1 == GraphTypes.CHART_STEP ||
         type1 == GraphTypes.CHART_STEP_STACK ||
         type1 == GraphTypes.CHART_JUMP ||
         type1 == GraphTypes.CHART_STEP_AREA ||
         type1 == GraphTypes.CHART_STEP_AREA_STACK ||
         type1 == GraphTypes.CHART_AREA ||
         type1 == GraphTypes.CHART_AREA_STACK ||
         type1 == GraphTypes.CHART_INTERVAL ||
         GraphTypes.isPoint(type1))
      {
         return type2 == GraphTypes.CHART_BAR ||
            type2 == GraphTypes.CHART_LINE ||
            type2 == GraphTypes.CHART_AREA ||
            type2 == GraphTypes.CHART_BAR_STACK ||
            type2 == GraphTypes.CHART_LINE_STACK ||
            type2 == GraphTypes.CHART_STEP ||
            type2 == GraphTypes.CHART_STEP_STACK ||
            type2 == GraphTypes.CHART_JUMP ||
            type2 == GraphTypes.CHART_STEP_AREA ||
            type2 == GraphTypes.CHART_STEP_AREA_STACK ||
            type2 == GraphTypes.CHART_AREA_STACK ||
            type2 == GraphTypes.CHART_INTERVAL ||
            GraphTypes.isPoint(type2);
      }

      return false;
   }

   /**
    * Check if is tree chart graph.
    * @param type
    */
   static isTree(type: number): boolean {
      return this.CHART_TREE == type;
   }

   /**
    * Check if is network chart graph.
    * @param type
    */
   static isNetwork(type: number): boolean {
      return this.CHART_NETWORK == type;
   }

   /**
    * Check if is circular network chart graph.
    * @param type
    */
   static isCircularNetwork(type: number): boolean {
      return this.CHART_CIRCULAR == type;
   }

   /**
    * Return display name.
    */
   static getDisplayName(type: number): string {
      for(let i = 0; i < GraphTypes.styles.length; i++) {
         if(type == GraphTypes.styles[i].data) {
            return GraphTypes.styles[i].label;
         }
      }

      return "";
   }

   /**
    * Check if supports point.
    */
   private static supportsPoint(type: number, chartBindingModel: ChartBindingModel): boolean {
      return type == GraphTypes.CHART_POINT ||
         type == GraphTypes.CHART_POINT_STACK ||
         type == GraphTypes.CHART_SCATTER_CONTOUR ||
         type == GraphTypes.CHART_MAP_CONTOUR ||
         type == GraphTypes.CHART_MAP &&
         chartBindingModel.pathField == null;
   }

   /**
    * Check if supports inverted chart.
    */
   static supportsInvertedChart(type: number): boolean {
      return type == GraphTypes.CHART_AUTO
         || type == GraphTypes.CHART_BAR
         || type == GraphTypes.CHART_BAR_STACK
         || type == GraphTypes.CHART_3D_BAR
         || type == GraphTypes.CHART_3D_BAR_STACK
         || type == GraphTypes.CHART_PIE
         || type == GraphTypes.CHART_DONUT
         || type == GraphTypes.CHART_3D_PIE
         || type == GraphTypes.CHART_LINE
         || type == GraphTypes.CHART_LINE_STACK
         || type == GraphTypes.CHART_STEP
         || type == GraphTypes.CHART_STEP_STACK
         || type == GraphTypes.CHART_JUMP
         || type == GraphTypes.CHART_STEP_AREA
         || type == GraphTypes.CHART_STEP_AREA_STACK
         || type == GraphTypes.CHART_POINT
         || type == GraphTypes.CHART_POINT_STACK
         || type == GraphTypes.CHART_AREA
         || type == GraphTypes.CHART_AREA_STACK
         || type == GraphTypes.CHART_WATERFALL
         || type == GraphTypes.CHART_PARETO
         || type == GraphTypes.CHART_BOXPLOT
         || type == GraphTypes.CHART_MAP
         || type == GraphTypes.CHART_FUNNEL
         || type == GraphTypes.CHART_INTERVAL
         || type == GraphTypes.CHART_SCATTER_CONTOUR
         || type == GraphTypes.CHART_MAP_CONTOUR;
   }

   /**
    * Check if the chart style is point line.
    */
   static isPointLine(type: number, chartBindingModel: ChartBindingModel): boolean {
      return GraphTypes.supportsPoint(type, chartBindingModel)
         || type == GraphTypes.CHART_LINE
         || type == GraphTypes.CHART_LINE_STACK;
   }

   static isHistogram(model: VSChartModel) {
      return GraphTypes.CHART_BAR && model.axisFields.find(a => a.startsWith("Range@")) &&
         model.axisFields.find(a => a.startsWith("Count("));
   }

   static get styles(): Array<{ label: string, data: number }> {
      return [
         {label: "Auto", data: GraphTypes.CHART_AUTO},
         {label: "Bar", data: GraphTypes.CHART_BAR},
         {label: "3D Bar", data: GraphTypes.CHART_3D_BAR},
         {label: "Line", data: GraphTypes.CHART_LINE},
         {label: "Step Line", data: GraphTypes.CHART_STEP},
         {label: "Stack Step Line", data: GraphTypes.CHART_STEP_STACK},
         {label: "Jump", data: GraphTypes.CHART_JUMP},
         {label: "Step Area", data: GraphTypes.CHART_STEP_AREA},
         {label: "Stack Step Area", data: GraphTypes.CHART_STEP_AREA_STACK},
         {label: "Area", data: GraphTypes.CHART_AREA},
         {label: "Point", data: GraphTypes.CHART_POINT},
         {label: "Stack Point", data: GraphTypes.CHART_POINT_STACK},
         {label: "Stack Bar", data: GraphTypes.CHART_BAR_STACK},
         {label: "3D Stack Bar", data: GraphTypes.CHART_3D_BAR_STACK},
         {label: "Stack Line", data: GraphTypes.CHART_LINE_STACK},
         {label: "Stack Area", data: GraphTypes.CHART_AREA_STACK},
         {label: "Pie", data: GraphTypes.CHART_PIE},
         {label: "Donut", data: GraphTypes.CHART_DONUT},
         {label: "Treemap", data: GraphTypes.CHART_TREEMAP},
         {label: "Sunburst", data: GraphTypes.CHART_SUNBURST},
         {label: "Circle Packing", data: GraphTypes.CHART_CIRCLE_PACKING},
         {label: "Icicle", data: GraphTypes.CHART_ICICLE},
         {label: "3D Pie", data: GraphTypes.CHART_3D_PIE},
         {label: "Radar", data: GraphTypes.CHART_RADAR},
         {label: "Filled Radar", data: GraphTypes.CHART_FILL_RADAR},
         {label: "Stock", data: GraphTypes.CHART_STOCK},
         {label: "Candle", data: GraphTypes.CHART_CANDLE},
         {label: "Box Plot", data: GraphTypes.CHART_BOXPLOT},
         {label: "Marimekko", data: GraphTypes.CHART_MEKKO},
         {label: "Tree", data: GraphTypes.CHART_TREE},
         {label: "Network", data: GraphTypes.CHART_NETWORK},
         {label: "Circular Network", data: GraphTypes.CHART_CIRCULAR},
         {label: "Funnel", data: GraphTypes.CHART_FUNNEL},
         {label: "Gantt", data: GraphTypes.CHART_GANTT},
         {label: "Waterfall", data: GraphTypes.CHART_WATERFALL},
         {label: "Pareto", data: GraphTypes.CHART_PARETO},
         {label: "Map", data: GraphTypes.CHART_MAP},
         {label: "Interval", data: GraphTypes.CHART_INTERVAL},
         {label: "Scatter Contour", data: GraphTypes.CHART_SCATTER_CONTOUR},
         {label: "Contour Map", data: GraphTypes.CHART_MAP_CONTOUR},
      ];
   }

   /**
    * Check if the chart type supports secondary axis.
    */
   static supportsSecondaryAxis(type: number): boolean {
      return type == GraphTypes.CHART_AUTO ||
             type == GraphTypes.CHART_BAR ||
             type == GraphTypes.CHART_BAR_STACK ||
             type == GraphTypes.CHART_LINE ||
             type == GraphTypes.CHART_LINE ||
             type == GraphTypes.CHART_LINE_STACK ||
             type == GraphTypes.CHART_STEP ||
             type == GraphTypes.CHART_STEP_STACK ||
             type == GraphTypes.CHART_JUMP ||
             type == GraphTypes.CHART_STEP_AREA ||
             type == GraphTypes.CHART_STEP_AREA_STACK ||
             type == GraphTypes.CHART_AREA ||
             type == GraphTypes.CHART_AREA_STACK ||
             type == GraphTypes.CHART_POINT ||
             type == GraphTypes.CHART_POINT_STACK ||
             type == GraphTypes.CHART_INTERVAL;
   }

   static isMultiAesthetic(model: ChartBindingModel): boolean {
      return !!model && (this.isGantt(model.chartType) || model.multiStyles);
   }
}
