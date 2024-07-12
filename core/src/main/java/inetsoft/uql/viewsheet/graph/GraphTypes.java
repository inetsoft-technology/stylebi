/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.uql.viewsheet.graph;

import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Catalog;

import java.util.*;

/**
 * Graph types.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class GraphTypes {
   /**
    * Graph type constant for auto charts.
    */
   public static final int CHART_AUTO = 0x00;
   /**
    * Graph type constant for bar charts.
    */
   public static final int CHART_BAR = 0x01;
   /**
    * Graph type constant for bar charts (stack).
    */
   public static final int CHART_BAR_STACK = 0x21;
   /**
    * Graph type constant for bar charts with a 3D effect.
    */
   public static final int CHART_3D_BAR = 0x02;
   /**
    * Graph type constant for bar charts with a 3D effect (stack).
    */
   public static final int CHART_3D_BAR_STACK = 0x22;
   /**
    * Graph type constant for pie charts.
    */
   public static final int CHART_PIE = 0x03;
   /**
    * Graph type constant for donut charts.
    */
   public static final int CHART_DONUT = 0x13;
   /**
    * Graph type constant for sunburst charts.
    */
   public static final int CHART_SUNBURST = 0x41;
   /**
    * Graph type constant for treemap charts.
    */
   public static final int CHART_TREEMAP = 0x42;
   /**
    * Graph type constant for circle packing charts.
    */
   public static final int CHART_CIRCLE_PACKING = 0x43;
   /**
    * Graph type constant for icicle charts.
    */
   public static final int CHART_ICICLE = 0x44;
   /**
    * Graph type constant for pie charts with a 3D effect.
    */
   public static final int CHART_3D_PIE = 0x04;
   /**
    * Graph type constant for line charts.
    */
   public static final int CHART_LINE = 0x05;
   /**
    * Graph type constant for line charts (stack).
    */
   public static final int CHART_LINE_STACK = 0x23;
   /**
    * Graph type constant for area charts.
    */
   public static final int CHART_AREA = 0x06;
   /**
    * Graph type constant for area charts (stack).
    */
   public static final int CHART_AREA_STACK = 0x24;
   /**
    * Graph type constant for stock charts.
    */
   public static final int CHART_STOCK = 0x07;
   /**
    * Graph type constant for point charts.
    */
   public static final int CHART_POINT = 0x08;
   /**
    * Graph type constant for point charts (stack).
    */
   public static final int CHART_POINT_STACK = 0x25;
   /**
    * Graph type constant for radar charts.
    */
   public static final int CHART_RADAR = 0x09;
   /**
    * Graph type constant for filled radar charts.
    */
   public static final int CHART_FILL_RADAR = 0x0A;
   /**
    * Graph type constant for candle charts.
    */
   public static final int CHART_CANDLE = 0x0B;
   /**
    * Graph type constant for box plot charts.
    */
   public static final int CHART_BOXPLOT = 0x45;
   /**
    * Graph type constant for marimekkocharts.
    */
   public static final int CHART_MEKKO = 0x46;
   /**
    * Graph type constant for tree.
    */
   public static final int CHART_TREE = 0x47;
   /**
    * Graph type constant for network.
    */
   public static final int CHART_NETWORK = 0x48;
   /**
    * Graph type constant for circular network.
    */
   public static final int CHART_CIRCULAR = 0x4B;
   /**
    * Graph type constant for step chart.
    */
   public static final int CHART_STEP = 0x49;
   /**
    * Graph type constant for jump line chart.
    */
   public static final int CHART_JUMP = 0x4A;
   /**
    * Graph type constant for gantt chart.
    */
   public static final int CHART_GANTT = 0x4C;
   /**
    * Graph type constant for funnel chart.
    */
   public static final int CHART_FUNNEL = 0x28;
   /**
    * Graph type constant for jump line chart.
    */
   public static final int CHART_STEP_AREA = 0x4E;
   /**
    * Graph type constant for stacked step line chart.
    */
   public static final int CHART_STEP_STACK = 0x26;
   /**
    * Graph type constant for stacked step area chart.
    */
   public static final int CHART_STEP_AREA_STACK = 0x27;
   /**
    * Graph type constant for waterfall charts.
    */
   public static final int CHART_WATERFALL = 0x0C;
   /**
    * Graph type constant for pareto charts.
    */
   public static final int CHART_PARETO = 0x0D;
   /**
    * Graph type constant for map charts.
    */
   public static final int CHART_MAP = 0x0E;
   /**
    * Graph type constant for interval charts.
    */
   public static final int CHART_INTERVAL = 0x4F;
   /**
    * Graph type constant for scattered contour charts.
    */
   public static final int CHART_SCATTER_CONTOUR = 0x50;
   /**
    * Graph type constant for contour map charts.
    */
   public static final int CHART_MAP_CONTOUR = 0x51;
   /**
    * Graph type constant for polygon charts.
    */
   public static final int CHART_POLYGON = 0x0F;

   /**
    * Bar max count.
    */
   public static final int BAR_MAX_COUNT = 5000;
   /**
    * 3DBar max count.
    */
   public static final int BAR_3D_MAX_COUNT = 2000;
   /**
    * Line max count.
    */
   public static final int LINE_MAX_COUNT = 50000;
   /**
    * Point max count.
    */
   public static final int POINT_MAX_COUNT = 100000;
   /**
    * Area max count.
    */
   public static final int AREA_MAX_COUNT = 2000;
   /**
    * Pie max count.
    */
   public static final int PIE_MAX_COUNT = 1000;
   /**
    * 3D pie max count.
    */
   public static final int PIE_3D_MAX_COUNT = 1000;
   /**
    * Radar max count.
    */
   public static final int RADAR_MAX_COUNT = 1000;
   /**
    * Candle max count.
    */
   public static final int CANDLE_MAX_COUNT = 1000;
   /**
    * Stock max count.
    */
   public static final int STOCK_MAX_COUNT = 1000;
   /**
    * Pareto max count.
    */
   public static final int PARETO_MAX_COUNT = 1000;
   /**
    * Waterfall max count.
    */
   public static final int WATERFALL_MAX_COUNT = 1000;

   /**
    * Check if is stack graph.
    */
   public static final boolean isStack(int type) {
      return (type & 0x20) == 0x20;
   }

   /**
    * Check if requires polar coord.
    */
   public static final boolean isPolar(int type) {
      return isPie(type) || type == CHART_RADAR || type == CHART_FILL_RADAR ||
         type == CHART_SUNBURST;
   }

   /**
    * Check if this chart uses RectCoord.
    */
   public static boolean isRect(int type) {
      boolean notRect = GraphTypes.isPolar(type) || GraphTypes.isTreemap(type) ||
         GraphTypes.isRelation(type) || GraphTypes.isGeo(type);
      return !notRect;
   }

   public static final boolean isTreemap(int type) {
      return type == CHART_SUNBURST || type == CHART_TREEMAP ||
         type == CHART_CIRCLE_PACKING || type == CHART_ICICLE;
   }

   public static final boolean isRelation(int type) {
      return type == CHART_TREE || type == CHART_NETWORK || type == CHART_CIRCULAR;
   }

   public static final boolean isGantt(int type) {
      return type == CHART_GANTT;
   }

   public static final boolean isFunnel(int type) {
      return type == CHART_FUNNEL;
   }

   public static final boolean isInterval(int type) {
      return type == CHART_INTERVAL;
   }

   public static final boolean isScatteredContour(int type) {
      return type == CHART_SCATTER_CONTOUR;
   }

   public static final boolean isMapContour(int type) {
      return type == CHART_MAP_CONTOUR;
   }

   public static final boolean isContour(int type) {
      return isScatteredContour(type) || isMapContour(type);
   }

   /**
    * Check if is pie graph.
    */
   public static final boolean isPie(int type) {
      return type == CHART_PIE || type == CHART_3D_PIE || type == CHART_DONUT;
   }

   /**
    * Check if is point graph.
    */
   public static final boolean isPoint(int type) {
      return type == CHART_POINT || type == CHART_POINT_STACK;
   }

   /**
    * Check if is bar graph.
    */
   public static final boolean isBar(int type) {
      return type == CHART_BAR || type == CHART_BAR_STACK ||
         type == CHART_3D_BAR || type == CHART_3D_BAR_STACK;
   }

   /**
    * Check if is area graph.
    */
   public static final boolean isArea(int type) {
      return type == CHART_AREA || type == CHART_AREA_STACK || type == CHART_STEP_AREA ||
         type == CHART_STEP_AREA_STACK;
   }

   /**
    * Check if is line graph.
    */
   public static final boolean isLine(int type) {
      return type == CHART_LINE || type == CHART_LINE_STACK ||
         type == CHART_STEP || type == CHART_JUMP || type == CHART_STEP_STACK;
   }

   /**
    * Check if is step line chart..
    */
   public static final boolean isStep(int type) {
      return type == CHART_STEP || type == CHART_STEP_STACK;
   }

   /**
    * Check if is step area chart..
    */
   public static final boolean isStepArea(int type) {
      return type == CHART_STEP_AREA || type == CHART_STEP_AREA_STACK;
   }

   /**
    * Check if is point graph.
    */
   public static final boolean is3DBar(int type) {
      return type == CHART_3D_BAR || type == CHART_3D_BAR_STACK;
   }

   /**
    * Check if is waterfall graph.
    */
   public static final boolean isWaterfall(int type) {
      return type == CHART_WATERFALL;
   }

   /**
    * Check if is pareto graph.
    */
   public static final boolean isPareto(int type) {
      return type == CHART_PARETO;
   }

   /**
    * Check if is schema graph.
    */
   public static final boolean isSchema(int type) {
      return type == CHART_STOCK || type == CHART_CANDLE || type == CHART_BOXPLOT;
   }

   /**
    * Check if is candle graph.
    */
   public static final boolean isCandle(int type) {
      return type == CHART_CANDLE;
   }

   /**
    * Check if is stock graph.
    */
   public static final boolean isStock(int type) {
      return type == CHART_STOCK;
   }

   /**
    * Check if is boxplot graph.
    */
   public static final boolean isBoxplot(int type) {
      return type == CHART_BOXPLOT;
   }

   public static final boolean isMekko(int type) {
      return type == CHART_MEKKO;
   }

   /**
    * Check if is radar graph.
    */
   public static final boolean isRadar(int type) {
      return type == CHART_RADAR || type == CHART_FILL_RADAR;
   }

   /**
    * Check if this is a radar with multiple measures.
    */
   public static boolean isRadarN(ChartInfo info) {
      return isRadar(info.getChartType()) && !isRadarOne(info);
   }

   /**
    * Check if this is a radar with multiple measures.
    */
   public static boolean isRadarOne(ChartInfo info) {
      if(isRadar(info.getChartType()) && info.getRTGroupFields().length == 1) {
         ChartRef[] yrefs = info.getRTYFields();
         return Arrays.stream(yrefs).filter(r -> GraphUtil.isMeasure(r)).count() == 1;
      }

      return false;
   }

   /**
    * Check if is auto graph.
    */
   public static final boolean isAuto(int type) {
      return type == CHART_AUTO;
   }

   /**
    * Check if is map graph.
    */
   public static final boolean isMap(int type) {
      return type == CHART_MAP;
   }

   public static final boolean isGeo(int type) {
      return isMap(type) || isMapContour(type);
   }

   /**
    * Check if is polygon graph.
    */
   public static final boolean isPolygon(int type) {
      return type == CHART_POLYGON;
   }

   /**
    * Check if this is a donut chart with a middle label.
    */
   public static boolean isDonut(ChartInfo info) {
      return info.isDonut() || info.isMultiStyles() &&
         (info.getYFieldCount() == 2 &&
         info.getYField(0) instanceof ChartAggregateRef &&
         ((ChartAggregateRef) info.getYField(0)).getChartType() == GraphTypes.CHART_PIE &&
         info.getYField(1) instanceof ChartAggregateRef &&
         ((ChartAggregateRef) info.getYField(1)).getChartType() == GraphTypes.CHART_PIE ||
         // a donut can also be created by placing the measures on X. (60158)
         info.getXFieldCount() == 2 &&
         info.getXField(0) instanceof ChartAggregateRef &&
         ((ChartAggregateRef) info.getXField(0)).getChartType() == GraphTypes.CHART_PIE &&
         info.getXField(1) instanceof ChartAggregateRef &&
         ((ChartAggregateRef) info.getXField(1)).getChartType() == GraphTypes.CHART_PIE);
   }

   /**
    * Check if the chart should preview it's new size/position when being resized.
    */
   public static boolean supportResizePreview(int type) {
      return !(isMap(type) || isRadar(type) || isPie(type));
   }
   /**
    * Check if supports point.
    */
   public static boolean supportsPoint(int type, ChartInfo info) {
      return isPoint(type) ||
         isMap(type) && info != null && info.getRTPathField() == null ||
         isPointRadar(type, info);
   }

   private static boolean isPointRadar(int type, ChartInfo info) {
      final boolean isPointLine = Optional.ofNullable(info).map(ChartInfo::getChartDescriptor)
         .map(ChartDescriptor::getPlotDescriptor)
         .map(PlotDescriptor::isPointLine)
         .orElse(false);
      return isPointLine && type == GraphTypes.CHART_RADAR;
   }

   /**
    * Check if supports line.
    */
   public static boolean supportsLine(int type, ChartInfo info) {
      return isLine(type) || isArea(type) ||
         type == GraphTypes.CHART_RADAR && !isPointRadar(type, info) ||
         type == GraphTypes.CHART_FILL_RADAR ||
         type == CHART_TREE || type == CHART_NETWORK || type == CHART_CIRCULAR ||
         type == GraphTypes.CHART_MAP && info != null &&
         info.getRTPathField() != null;
   }

   /**
    * Check if supports texture.
    */
   public static final boolean supportsTexture(int type) {
      return type == CHART_BAR || type == CHART_BAR_STACK ||
         type == CHART_3D_BAR || type == CHART_3D_BAR_STACK ||
         type == CHART_PIE || type == CHART_DONUT || type == CHART_3D_PIE ||
         type == CHART_AUTO || type == CHART_WATERFALL || type == CHART_PARETO ||
         type == CHART_CANDLE || type == CHART_BOXPLOT ||
         type == CHART_SUNBURST || type == CHART_TREEMAP ||
         type == CHART_CIRCLE_PACKING || type == CHART_ICICLE || type == CHART_MEKKO ||
         type == CHART_GANTT || type == CHART_FUNNEL ||
         type == CHART_INTERVAL;
   }

   /**
    * Check if supports shape.
    */
   public static final boolean supportsColor(int type) {
      return type != CHART_SCATTER_CONTOUR && type != CHART_MAP_CONTOUR;
   }

   /**
    * Check if supports shape.
    */
   public static final boolean supportsShape(int type) {
      return type != CHART_STOCK && type != CHART_MEKKO && type != CHART_SCATTER_CONTOUR &&
         type != CHART_MAP_CONTOUR;
   }

   /**
    * Check if supports size.
    */
   public static final boolean supportsSize(int type) {
      return type != CHART_STOCK && type != CHART_MEKKO;
   }

   /**
    * Check if supports Dimension as an Aesthetic field.
    */
   public static final boolean supportsDimensionAesthetic(int chartType, int aestheticType) {
      // @by larryl, we could support dimension color binding by dodging the
      // stock and candle
      //return type != CHART_STOCK && type != CHART_CANDLE;

      if(GraphTypes.isInterval(chartType) && aestheticType == ChartConstants.AESTHETIC_SIZE) {
         return false;
      }

      return true;
   }

   /**
    * Check if supports Aggregate as an X field.
    */
   public static final boolean supportsAggregateInY(int type) {
      return type != CHART_STOCK && type != CHART_CANDLE;
   }

   /**
    * Check if is merged graph type.
    */
   public static final boolean isMergedGraphType(int type) {
      return type == CHART_RADAR || type == CHART_FILL_RADAR ||
         type == CHART_STOCK || type == CHART_CANDLE || type == CHART_BOXPLOT ||
         isGeo(type) || type == CHART_TREEMAP || type == CHART_SUNBURST ||
         type == CHART_CIRCLE_PACKING || type == CHART_ICICLE || type == CHART_MEKKO ||
         type == CHART_TREE || type == CHART_NETWORK || type == CHART_CIRCULAR ||
         type == CHART_GANTT || type == CHART_FUNNEL;
   }

   /**
    * Check if chart type supports setting multi-styles.
    */
   public static final boolean supportsMultiStyles(int type) {
      return !isMergedGraphType(type) && !isScatteredContour(type);
   }

   /**
    * Check if supports inverted chart.
    */
   public static final boolean supportsInvertedChart(int type) {
      return type == CHART_AUTO || type == CHART_BAR ||
         type == CHART_BAR_STACK || type == CHART_3D_BAR ||
         type == CHART_3D_BAR_STACK || type == CHART_BOXPLOT ||
         type == CHART_PIE || type == CHART_DONUT || type == CHART_3D_PIE ||
         type == CHART_LINE || type == CHART_LINE_STACK ||
         type == CHART_STEP || type == CHART_JUMP || type == CHART_STEP_AREA ||
         type == CHART_STEP_STACK || type == CHART_STEP_AREA_STACK ||
         type == CHART_POINT || type == CHART_POINT_STACK ||
         type == CHART_AREA || type == CHART_AREA_STACK ||
         type == CHART_WATERFALL || type == CHART_PARETO ||
         isGeo(type) || type == CHART_GANTT || type == CHART_FUNNEL ||
         type == CHART_INTERVAL || type == CHART_SCATTER_CONTOUR;
   }

   /**
    * Check if the chart type supports secondary axis.
    */
   public static final boolean supportsSecondaryAxis(int type) {
      switch(type) {
      case GraphTypes.CHART_AUTO:
      case GraphTypes.CHART_BAR:
      case GraphTypes.CHART_BAR_STACK:
      case GraphTypes.CHART_LINE:
      case GraphTypes.CHART_LINE_STACK:
      case GraphTypes.CHART_STEP:
      case GraphTypes.CHART_JUMP:
      case GraphTypes.CHART_STEP_AREA:
      case GraphTypes.CHART_STEP_STACK:
      case GraphTypes.CHART_STEP_AREA_STACK:
      case GraphTypes.CHART_AREA:
      case GraphTypes.CHART_AREA_STACK:
      case GraphTypes.CHART_POINT:
      case GraphTypes.CHART_POINT_STACK:
      case GraphTypes.CHART_INTERVAL:
         return true;
      }

      return false;
   }

   /**
    * Check if the chart type supports secondary axis.
    */
   public static final boolean supportsFillTimeGap(int type) {
      // @by stephenwebster, For bug1402949727054 support bar graphs
      return isLine(type) || isArea(type) || isBar(type) || is3DBar(type) || isPoint(type);
   }

   /**
    * Check if chart uses each measures' individual size frame or always use the chart
    * global chart frame.
    */
   public static final boolean supportsMeasureSizeFrame(int type) {
      return !isContour(type);
   }

   /**
    * Check if is incompatible chart type, including merged graph,
    * waterfall, pareto, pie.
    */
   private static final boolean isIncompatibleType(int type) {
      return isMergedGraphType(type) || isWaterfall(type) || isPareto(type) || isTreemap(type) ||
         type == CHART_MEKKO || isRelation(type) || isScatteredContour(type);
   }

   /**
    * Check if the two chart types are compatible.
    */
   public static final boolean isCompatible(int type1, int type2) {
      if(type1 == type2) {
         return true;
      }

      if(isIncompatibleType(type1) || isIncompatibleType(type2)) {
         return false;
      }

      if((isPie(type1) || type1 == CHART_AUTO) && (isPie(type2) || type2 == CHART_AUTO)) {
         return true;
      }

      if(type1 == CHART_BAR || type1 == CHART_BAR_STACK ||
         type1 == CHART_LINE || type1 == CHART_LINE_STACK ||
         type1 == CHART_STEP || type1 == CHART_JUMP || type1 == CHART_STEP_AREA ||
         type1 == CHART_AREA || type1 == CHART_AREA_STACK ||
         type1 == CHART_STEP_AREA_STACK || type1 == CHART_STEP_STACK ||
         type1 == CHART_INTERVAL ||
         isPoint(type1) || type1 == CHART_AUTO)
      {
         return type2 == CHART_BAR || type2 == CHART_LINE ||
            type2 == CHART_STEP || type2 == CHART_JUMP || type2 == CHART_STEP_AREA ||
            type2 == CHART_AREA || type2 == CHART_BAR_STACK ||
            type2 == CHART_STEP_STACK || type2 == CHART_STEP_AREA_STACK ||
            type2 == CHART_LINE_STACK || type2 == CHART_AREA_STACK ||
            type2 == CHART_INTERVAL ||
            isPoint(type2) || type2 == CHART_AUTO;
      }

      return false;
   }

   public static final String getDisplayName(int type) {
      initMap();
      return (String) (map.containsKey(type + "") ? map.get(type + "") : type + "");
   }

   public static final Map<String, String> getAllChartTypes() {
      if(chartTypes.isEmpty()) {
         synchronized(chartTypes) {
            if(chartTypes.isEmpty()) {
               chartTypes.put(CHART_AUTO + "", "Auto");
               chartTypes.put(CHART_BAR + "", "Bar");
               chartTypes.put(CHART_BAR_STACK + "", "Stack Bar");
               chartTypes.put(CHART_3D_BAR + "", "3D Bar");
               chartTypes.put(CHART_3D_BAR_STACK + "", "3D Stack Bar");
               chartTypes.put(CHART_LINE + "", "Line");
               chartTypes.put(CHART_LINE_STACK + "", "Stack Line");
               chartTypes.put(CHART_AREA + "", "Area");
               chartTypes.put(CHART_AREA_STACK + "", "Stack Area");
               chartTypes.put(CHART_POINT + "", "Point");
               chartTypes.put(CHART_POINT_STACK + "", "Stack Point");
               chartTypes.put(CHART_PIE + "", "Pie");
               chartTypes.put(CHART_3D_PIE + "", "3D Pie");
               chartTypes.put(CHART_DONUT + "", "Donut");
               chartTypes.put(CHART_RADAR + "", "Radar");
               chartTypes.put(CHART_FILL_RADAR + "", "Filled Radar");
               chartTypes.put(CHART_STOCK + "", "Stock");
               chartTypes.put(CHART_CANDLE + "", "Candle");
               chartTypes.put(CHART_BOXPLOT + "", "Box Plot");
               chartTypes.put(CHART_WATERFALL + "", "Waterfall");
               chartTypes.put(CHART_PARETO + "", "Pareto");
               chartTypes.put(CHART_MAP + "", "Map");
               chartTypes.put(CHART_TREEMAP + "", "Treemap");
               chartTypes.put(CHART_SUNBURST + "", "Sunburst");
               chartTypes.put(CHART_CIRCLE_PACKING + "", "Circle Packing");
               chartTypes.put(CHART_ICICLE + "", "Icicle");
               chartTypes.put(CHART_MEKKO + "", "Marimekko");
               chartTypes.put(CHART_GANTT + "", "Gantt");
               chartTypes.put(CHART_FUNNEL + "", "Funnel");
               chartTypes.put(CHART_STEP + "", "Step Line");
               chartTypes.put(CHART_STEP_STACK + "", "Stack Step Line");
               chartTypes.put(CHART_JUMP + "", "Jump Line");
               chartTypes.put(CHART_STEP_AREA + "", "Step Area");
               chartTypes.put(CHART_STEP_AREA_STACK + "", "Stack Step Area");
               chartTypes.put(CHART_TREE + "", "Tree");
               chartTypes.put(CHART_NETWORK + "", "Network");
               chartTypes.put(CHART_CIRCULAR + "", "Circular Network");
               chartTypes.put(CHART_INTERVAL + "", "Interval");
               chartTypes.put(CHART_SCATTER_CONTOUR + "", "Scatter Contour");
               chartTypes.put(CHART_MAP_CONTOUR + "", "Contour Map");
            }
         }
      }

      return chartTypes;
   }

   static void initMap() {
      if(map.isEmpty()) {
         Catalog catalog = Catalog.getCatalog();
         map.put(CHART_AUTO + "", catalog.getString("Auto"));
         map.put(CHART_BAR + "", catalog.getString("Bar"));
         map.put(CHART_BAR_STACK + "", catalog.getString("Stack Bar"));
         map.put(CHART_3D_BAR + "", catalog.getString("3D Bar"));
         map.put(CHART_3D_BAR_STACK + "", catalog.getString("3D Stack Bar"));
         map.put(CHART_LINE + "", catalog.getString("Line"));
         map.put(CHART_LINE_STACK + "", catalog.getString("Stack Line"));
         map.put(CHART_AREA + "", catalog.getString("Area"));
         map.put(CHART_AREA_STACK + "", catalog.getString("Stack Area"));
         map.put(CHART_POINT + "", catalog.getString("Point"));
         map.put(CHART_POINT_STACK + "", catalog.getString("Stack Point"));
         map.put(CHART_PIE + "", catalog.getString("Pie"));
         map.put(CHART_3D_PIE + "", catalog.getString("3D Pie"));
         map.put(CHART_DONUT + "", catalog.getString("Donut"));
         map.put(CHART_RADAR + "", catalog.getString("Radar"));
         map.put(CHART_FILL_RADAR + "", catalog.getString("Filled Radar"));
         map.put(CHART_STOCK + "", catalog.getString("Stock"));
         map.put(CHART_CANDLE + "", catalog.getString("Candle"));
         map.put(CHART_BOXPLOT + "", catalog.getString("Box Plot"));
         map.put(CHART_WATERFALL + "", catalog.getString("Waterfall"));
         map.put(CHART_PARETO + "", catalog.getString("Pareto"));
         map.put(CHART_MAP + "", catalog.getString("Map"));
         map.put(CHART_TREEMAP + "", catalog.getString("Treemap"));
         map.put(CHART_SUNBURST + "", catalog.getString("Sunburst"));
         map.put(CHART_CIRCLE_PACKING + "", catalog.getString("Circle Packing"));
         map.put(CHART_ICICLE + "", catalog.getString("Icicle"));
         map.put(CHART_MEKKO + "", catalog.getString("Marimekko"));
         map.put(CHART_GANTT + "", catalog.getString("Gantt"));
         map.put(CHART_FUNNEL + "", catalog.getString("Funnel"));
         map.put(CHART_STEP + "", catalog.getString("Step Line"));
         map.put(CHART_STEP_STACK + "", catalog.getString("Stack Step Line"));
         map.put(CHART_JUMP + "", catalog.getString("Jump Line"));
         map.put(CHART_STEP_AREA + "", catalog.getString("Step Area"));
         map.put(CHART_STEP_AREA_STACK + "", catalog.getString("Stack Step Area"));
         map.put(CHART_TREE + "", catalog.getString("Tree"));
         map.put(CHART_NETWORK + "", catalog.getString("Network"));
         map.put(CHART_CIRCULAR + "", catalog.getString("Circular Network"));
         map.put(CHART_INTERVAL + "", catalog.getString("Interval"));
         map.put(CHART_SCATTER_CONTOUR + "", catalog.getString("Scatter Contour"));
         map.put(CHART_MAP_CONTOUR + "", catalog.getString("Contour Map"));
      }
   }

   /**
    * Get geometry max count.
    */
   public static int getGeomMaxCount(int type) {
      String mstr = null;

      if(GraphTypes.is3DBar(type)) {
         mstr = SreeEnv.getProperty("graph.3dbar.maxcount");
      }
      else if(GraphTypes.isBar(type)) {
         mstr = SreeEnv.getProperty("graph.bar.maxcount");
      }
      else if(type == GraphTypes.CHART_PIE || type == GraphTypes.CHART_DONUT) {
         mstr = SreeEnv.getProperty("graph.pie.maxcount");
      }
      else if(type == GraphTypes.CHART_SUNBURST) {
         mstr = SreeEnv.getProperty("graph.pie.maxcount");
      }
      else if(type == GraphTypes.CHART_TREEMAP) {
         mstr = SreeEnv.getProperty("graph.pie.maxcount");
      }
      else if(type == GraphTypes.CHART_CIRCLE_PACKING) {
         mstr = SreeEnv.getProperty("graph.pie.maxcount");
      }
      else if(type == GraphTypes.CHART_ICICLE) {
         mstr = SreeEnv.getProperty("graph.pie.maxcount");
      }
      else if(type == GraphTypes.CHART_3D_PIE) {
         mstr = SreeEnv.getProperty("graph.3dpie.maxcount");
      }
      else if(GraphTypes.isArea(type)) {
         mstr = SreeEnv.getProperty("graph.area.maxcount");
      }
      else if(GraphTypes.isLine(type)) {
         mstr = SreeEnv.getProperty("graph.line.maxcount");
      }
      else if(GraphTypes.isPoint(type)) {
         mstr = SreeEnv.getProperty("graph.point.maxcount");
      }
      else if(GraphTypes.isRadar(type)) {
         mstr = SreeEnv.getProperty("graph.radar.maxcount");
      }
      else if(GraphTypes.isCandle(type)) {
         mstr = SreeEnv.getProperty("graph.candle.maxcount");
      }
      else if(GraphTypes.isStock(type)) {
         mstr = SreeEnv.getProperty("graph.stock.maxcount");
      }
      else if(GraphTypes.isWaterfall(type)) {
         mstr = SreeEnv.getProperty("graph.waterfall.maxcount");
      }
      else if(GraphTypes.isPareto(type)) {
         mstr = SreeEnv.getProperty("graph.pareto.maxcount");
      }

      if(mstr != null) {
         return Integer.parseInt(mstr);
      }

      return -1;
   }

   private static final HashMap map = new LinkedHashMap();
   private static final HashMap chartTypes = new LinkedHashMap();
}
