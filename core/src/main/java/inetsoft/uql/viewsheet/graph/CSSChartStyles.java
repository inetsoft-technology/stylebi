/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.uql.viewsheet.graph;

import inetsoft.graph.aesthetic.StaticSizeFrame;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.Util;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.XAggregateRef;
import inetsoft.uql.viewsheet.graph.aesthetic.SizeFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticSizeFrameWrapper;
import inetsoft.util.css.*;

import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Applies the css chart styles to the chart object.
 *
 * CSS stylable items:
 *
 * ChartLegend {
 *    border-color: red|#FF0000;
 *    border-style: none|dotted|dashed|solid|double;
 *    padding: 5px;
 *    legend_gap: 20;
 * }
 *
 * ChartPlot {
 *    opacity: 0.5;
 *    background-color: red|#FF0000;
 *    line_x: NONE|ULTRA_THIN_LINE|THIN_THIN_LINE|THIN_LINE|MEDIUM_LINE|THICK_LINE|...;
 *    line_y: NONE|ULTRA_THIN_LINE|THIN_THIN_LINE|THIN_LINE|MEDIUM_LINE|THICK_LINE|...;
 *    line_diagonal: NONE|ULTRA_THIN_LINE|THIN_THIN_LINE|THIN_LINE|MEDIUM_LINE|THICK_LINE|...;
 *    line_quadrant: NONE|ULTRA_THIN_LINE|THIN_THIN_LINE|THIN_LINE|MEDIUM_LINE|THICK_LINE|...;
 *    line_x_color: red|#FF0000;
 *    line_y_color: red|#FF0000;
 *    line_diagonal_color: red|#FF0000;
 *    line_quadrant_color: red|#FF0000;
 *    facet_grid_visible: true|false;
 *    facet_grid_color: red|#FF0000;
 *    explode_pie: true|false;
 *    map_color: red|#FF0000;
 * }
 *
 * ChartAxisLabels[axis=x|y] {
 *    label_rotation: 0|45|90|...;
 *    label_gap: 20;
 * }
 *
 * ChartAxisTitle[axis=x|x2|y|y2] {
 *    label_gap: 20;
 *    visibility: visible|hidden;
 * }
 *
 * ChartSizeFrame {
 *    frame_size: 5;
 * }
 *
 * Also can use the "type" attribute on Chart to style the chart based on its type
 *
 * Chart[type=VBar] ChartPlot {
 *    background-color: red;
 * }
 *
 * Chart[type=Donut] ChartPlot {
 *    background-color: green;
 * }
 */
public class CSSChartStyles {
   public static void apply(ChartDescriptor desc, ChartInfo info, CSSDictionary cssDictionary,
                            List<CSSParameter> parentParams)
   {
      if(desc == null || info == null) {
         return;
      }

      if(cssDictionary == null) {
         cssDictionary = CSSDictionary.getDictionary();
      }

      ChartType chartType = getChartType(info);
      LegendsDescriptor legendsDesc = desc.getLegendsDescriptor();

      if(legendsDesc != null) {
         legendsDesc.resetCompositeValues(CompositeValue.Type.CSS);
         CSSStyle cssStyle = cssDictionary.getStyle(
            CSSParameter.getAllCSSParams(parentParams, new CSSParameter(CSSConstants.CHART_LEGEND,
                                                                        null, null, null)));

         if(cssStyle != null) {
            if(cssStyle.isBorderColorDefined()) {
               legendsDesc.setBorderColor(cssStyle.getBorderColors().topColor, CompositeValue.Type.CSS);
            }

            if(cssStyle.isBorderDefined()) {
               legendsDesc.setBorder(cssStyle.getBorders().top, CompositeValue.Type.CSS);
            }

            if(cssStyle.isPaddingDefined()) {
               legendsDesc.setPadding(cssStyle.getPadding(), CompositeValue.Type.CSS);
            }

            Map<String, String> customProps = cssStyle.getCustomProperties();
            String val = customProps.get("legend_gap");

            if(val != null) {
               int gap = Integer.parseInt(val);
               legendsDesc.setGap(gap, CompositeValue.Type.CSS);
            }
         }
      }

      PlotDescriptor plotDesc = desc.getPlotDescriptor();

      if(plotDesc != null) {
         plotDesc.resetCompositeValues(CompositeValue.Type.CSS);
         CSSStyle cssStyle = cssDictionary.getStyle(
            CSSParameter.getAllCSSParams(parentParams, new CSSParameter(CSSConstants.CHART_PLOT,
                                                                        null, null, null)));

         if(cssStyle != null) {
            if(cssStyle.isAlphaDefined()) {
               plotDesc.setAlpha(cssStyle.getAlpha() / 100.0, CompositeValue.Type.CSS);
            }

            if(cssStyle.isBackgroundDefined()) {
               plotDesc.setBackground(cssStyle.getBackground(), CompositeValue.Type.CSS);
            }

            boolean inverted = info.isInvertedGraph();
            Map<String, String> customProps = cssStyle.getCustomProperties();
            final String[] lineStyles = { "line_x", "line_y", "line_diagonal", "line_quadrant" };

            for(String lineStyle : lineStyles) {
               String val = customProps.get(lineStyle);

               if(val != null) {
                  int styleVal = Util.getStyleConstantsFromString(val);

                  if((!inverted && "line_x".equals(lineStyle)) ||
                     (inverted && "line_y".equals(lineStyle)))
                  {
                     plotDesc.setXGridStyle(styleVal, CompositeValue.Type.CSS);
                  }
                  else if((!inverted && "line_y".equals(lineStyle)) ||
                     (inverted && "line_x".equals(lineStyle)))
                  {
                     plotDesc.setYGridStyle(styleVal, CompositeValue.Type.CSS);
                  }
                  else if("line_diagonal".equals(lineStyle)) {
                     plotDesc.setDiagonalStyle(styleVal, CompositeValue.Type.CSS);
                  }
                  else if("line_quadrant".equals(lineStyle)) {
                     plotDesc.setQuadrantStyle(styleVal, CompositeValue.Type.CSS);
                  }
               }
            }

            final String[] lineColors = { "line_x_color", "line_y_color", "line_diagonal_color",
                                          "line_quadrant_color" };

            for(String lineColor : lineColors) {
               String val = customProps.get(lineColor);

               if(val != null) {
                  Color color = null;

                  try {
                     color = GraphUtil.parseColor(val);
                  }
                  catch(Exception e) {
                  }

                  if((!inverted && "line_x_color".equals(lineColor)) ||
                     (inverted && "line_y_color".equals(lineColor)))
                  {
                     plotDesc.setXGridColor(color, CompositeValue.Type.CSS);
                  }
                  else if((!inverted && "line_y_color".equals(lineColor)) ||
                     (inverted && "line_x_color".equals(lineColor)))
                  {
                     plotDesc.setYGridColor(color, CompositeValue.Type.CSS);
                  }
                  else if("line_diagonal_color".equals(lineColor)) {
                     plotDesc.setDiagonalColor(color, CompositeValue.Type.CSS);
                  }
                  else if("line_quadrant_color".equals(lineColor)) {
                     plotDesc.setQuadrantColor(color, CompositeValue.Type.CSS);
                  }
               }
            }

            String val = customProps.get("facet_grid_visible");

            if(val != null) {
               plotDesc.setFacetGrid("true".equals(val), CompositeValue.Type.CSS);
            }

            val = customProps.get("facet_grid_color");

            if(val != null) {
               try {
                  Color color = GraphUtil.parseColor(val);
                  plotDesc.setFacetGridColor(color, CompositeValue.Type.CSS);
               }
               catch(Exception e) {
                  // do nothing
               }
            }

            val = customProps.get("explode_pie");

            if(val != null) {
               plotDesc.setExploded("true".equalsIgnoreCase(val), CompositeValue.Type.CSS);
            }

            val = customProps.get("map_color");

            if(val != null) {
               try {
                  Color color = GraphUtil.parseColor(val);
                  plotDesc.setEmptyColor(color, CompositeValue.Type.CSS);
               }
               catch(Exception e) {
                  // do nothing
               }
            }

            val = customProps.get("pareto_line_color");

            if(val != null) {
               try {
                  Color color = GraphUtil.parseColor(val);
                  plotDesc.setParetoLineColor(color, CompositeValue.Type.CSS);
               }
               catch(Exception e) {
                  // do nothing
               }
            }
         }
      }

      if(info.getAxisDescriptor() != null) {
         info.getAxisDescriptor().resetCompositeValues(CompositeValue.Type.CSS);
      }

      // x axis
      CSSParameter axisLabelParam = new CSSParameter(CSSConstants.CHART_AXIS_LABELS,
                                                     null, null,
                                                     new CSSAttr("axis", "x"));
      CSSStyle xAxisCssStyle = cssDictionary.getStyle(
         CSSParameter.getAllCSSParams(parentParams, axisLabelParam));

      // y axis
      axisLabelParam = new CSSParameter(CSSConstants.CHART_AXIS_LABELS, null, null,
                                        new CSSAttr("axis", "y"));
      CSSStyle yAxisCssStyle = cssDictionary.getStyle(
         CSSParameter.getAllCSSParams(parentParams, axisLabelParam));

      ChartRef[] candleFields = {};

      if(info instanceof CandleChartInfo) {
         CandleChartInfo cinfo = (CandleChartInfo) info;
         candleFields = new ChartRef[]{ cinfo.getCloseField(), cinfo.getHighField(),
                                        cinfo.getLowField(), cinfo.getOpenField() };
      }

      ChartRef[][] nrefs = { info.getXFields(), info.getRTXFields(), info.getYFields(),
                             info.getRTYFields(), candleFields };

      for(int i = 0; i < nrefs.length; i++) {
         ChartRef[] refs = nrefs[i];

         for(ChartRef ref : refs) {
            if(ref == null) {
               continue;
            }

            AxisDescriptor axisDesc = GraphUtil.getAxisDescriptor(info, refs == candleFields ?
               null : ref);

            if(axisDesc != null) {
               axisDesc.resetCompositeValues(CompositeValue.Type.CSS);
               CSSStyle cssStyle = i == 0 || i == 1 || chartType == ChartType.MARIMEKKO ?
                  xAxisCssStyle : yAxisCssStyle;

               if(cssStyle != null) {
                  Map<String, String> customProps = cssStyle.getCustomProperties();
                  String val = customProps.get("label_rotation");

                  if(val != null) {
                     float rotation = Float.parseFloat(val);
                     axisDesc.getAxisLabelTextFormat().getCSSFormat().setRotation(rotation);

                     for(String col : axisDesc.getColumnLabelTextFormatColumns()) {
                        CompositeTextFormat colFmt = axisDesc.getColumnLabelTextFormat(col);

                        if(colFmt != null) {
                           colFmt.getCSSFormat().setRotation(rotation);
                        }
                     }
                  }

                  val = customProps.get("label_gap");

                  if(val != null) {
                     axisDesc.setLabelGap(Integer.parseInt(val), CompositeValue.Type.CSS);
                  }
               }
            }
         }
      }

      if(chartType == ChartType.MARIMEKKO) {
         boolean ymeasure = info.getYFieldCount() > 0;

         if(ymeasure) {
            ChartRef ref = info.getYField(0);
            setAxisDescriptorColumnFormat(yAxisCssStyle, info.getAxisDescriptor(),
                                          ref.getFullName() + "%");
         }
         else {
            setAxisDescriptorColumnFormat(yAxisCssStyle, info.getAxisDescriptor(), "value%");
            setAxisDescriptorColumnFormat(xAxisCssStyle, info.getAxisDescriptor(), "value");
         }
      }

      TitlesDescriptor titlesDesc = desc.getTitlesDescriptor();

      if(titlesDesc != null) {
         final String[] allAxis = { "x", "x2", "y", "y2" };

         for(String axis : allAxis) {
            CSSParameter axisTitleParam = new CSSParameter(CSSConstants.CHART_AXIS_TITLE,
                                                           null, null, new CSSAttr("axis", axis));
            CSSStyle cssStyle = cssDictionary.getStyle(
               CSSParameter.getAllCSSParams(parentParams, axisTitleParam));

            if(cssStyle != null) {
               TitleDescriptor titleDesc;

               if("x".equals(axis)) {
                  titleDesc = titlesDesc.getXTitleDescriptor();
               }
               else if("x2".equals(axis)) {
                  titleDesc = titlesDesc.getX2TitleDescriptor();
               }
               else if("y".equals(axis)) {
                  titleDesc = titlesDesc.getYTitleDescriptor();
               }
               else { // y2
                  titleDesc = titlesDesc.getY2TitleDescriptor();
               }

               titleDesc.resetCompositeValues(CompositeValue.Type.CSS);

               if(cssStyle.isVisibleDefined()) {
                  boolean visible = cssStyle.isVisible();
                  titleDesc.setVisible(visible, CompositeValue.Type.CSS);
               }

               Map<String, String> customProps = cssStyle.getCustomProperties();
               String val = customProps.get("label_gap");

               if(val != null) {
                  int gap = Integer.parseInt(val);
                  titleDesc.setLabelGap(gap, CompositeValue.Type.CSS);
               }
            }
         }
      }

      CSSParameter sizeFrameParam = new CSSParameter(CSSConstants.CHART_SIZE_FRAME,
                                                     null, null, null);
      CSSStyle cssStyle = cssDictionary.getStyle(
         CSSParameter.getAllCSSParams(parentParams, sizeFrameParam));

      if(cssStyle != null) {
         Double size = null;

         Map<String, String> customProps = cssStyle.getCustomProperties();
         String val = customProps.get("frame_size");

         if(val != null) {
            size = Double.parseDouble(val);
         }

         setSizeFrameValues(info, size, false);
      }
   }

   private static void setSizeFrameValues(ChartInfo info, Double size, boolean resetUser) {
      SizeFrameWrapper sizeFrame = info.getSizeFrameWrapper();
      setSizeFrameValues0(sizeFrame, size, resetUser);

      for(boolean runtime : new boolean[]{ false, true }) {
         for(ChartRef ref : info.getModelRefs(runtime)) {
            if(!(ref instanceof ChartAggregateRef)) {
               continue;
            }

            SizeFrameWrapper sframe = ((ChartAggregateRef) ref).getSizeFrameWrapper();

            if(sframe == null) {
               continue;
            }

            setSizeFrameValues0(sframe, size, resetUser);
         }
      }
   }

   private static void setSizeFrameValues0(SizeFrameWrapper sframe, Double size, boolean resetUser)
   {
      if(sframe instanceof StaticSizeFrameWrapper) {
         ((StaticSizeFrame) sframe.getVisualFrame())
            .resetCompositeValues(resetUser ? CompositeValue.Type.USER : CompositeValue.Type.CSS);

         if(size != null && !resetUser) {
            ((StaticSizeFrameWrapper) sframe).setSize(size, CompositeValue.Type.CSS);
         }
      }
   }

   private static void setAxisDescriptorColumnFormat(CSSStyle cssStyle, AxisDescriptor axisDesc,
                                                     String column)
   {
      if(cssStyle != null) {
         CompositeTextFormat format = axisDesc.getColumnLabelTextFormat(column);

         if(format == null) {
            format = new CompositeTextFormat();
         }

         Map<String, String> customProps = cssStyle.getCustomProperties();
         String val = customProps.get("label_rotation");

         if(val != null) {
            float rotation = Float.parseFloat(val);
            format.getCSSFormat().setRotation(rotation);
         }

         val = customProps.get("label_gap");

         if(val != null) {
            axisDesc.setLabelGap(Integer.parseInt(val), CompositeValue.Type.CSS);
         }

         axisDesc.setColumnLabelTextFormat(column, format);
      }
   }

   public static ChartType getChartType(ChartInfo info) {
      if(info.isMultiStyles()) {
         return ChartType.MULTI;
      }

      int type = info.getRTChartType();

      if(GraphTypes.isBar(type)) {
         boolean ymeasure = info.getYFieldCount() > 0 &&
            info.getYField(info.getYFieldCount() - 1) instanceof XAggregateRef;

         if(ymeasure) {
            return ChartType.VBAR;
         }
         else {
            return ChartType.HBAR;
         }
      }
      else if(GraphTypes.isStepArea(type)) {
         return ChartType.STEP_AREA;
      }
      else if(GraphTypes.isStep(type)) {
         return ChartType.STEP;
      }
      else if(type == GraphTypes.CHART_JUMP) {
         return ChartType.JUMP_LINE;
      }
      else if(GraphTypes.isLine(type)) {
         return ChartType.LINE;
      }
      else if(GraphTypes.isArea(type)) {
         return ChartType.AREA;
      }
      else if(GraphTypeUtil.isHeatMapish(info)) {
         return ChartType.HEATMAP;
      }
      else if(isScatter(type, info)) {
         return ChartType.SCATTER;
      }
      else if(GraphTypes.isPoint(type)) {
         return ChartType.POINT;
      }
      else if(type == GraphTypes.CHART_DONUT) {
         return ChartType.DONUT;
      }
      else if(GraphTypes.isPie(type)) {
         return ChartType.PIE;
      }
      else if(GraphTypes.isRadar(type)) {
         return ChartType.RADAR;
      }
      else if(GraphTypes.isStock(type)) {
         return ChartType.STOCK;
      }
      else if(GraphTypes.isCandle(type)) {
         return ChartType.CANDLE;
      }
      else if(GraphTypes.isBoxplot(type)) {
         return ChartType.BOX_PLOT;
      }
      else if(GraphTypes.isWaterfall(type)) {
         return ChartType.WATERFALL;
      }
      else if(GraphTypes.isPareto(type)) {
         return ChartType.PARETO;
      }
      else if(GraphTypes.isMap(type)) {
         return ChartType.MAP;
      }
      else if(type == GraphTypes.CHART_TREEMAP) {
         return ChartType.TREEMAP;
      }
      else if(type == GraphTypes.CHART_SUNBURST) {
         return ChartType.SUNBURST;
      }
      else if(type == GraphTypes.CHART_CIRCLE_PACKING) {
         return ChartType.CIRCLE_PACKING;
      }
      else if(type == GraphTypes.CHART_ICICLE) {
         return ChartType.ICICLE;
      }
      else if(type == GraphTypes.CHART_MEKKO) {
         return ChartType.MARIMEKKO;
      }
      else if(type == GraphTypes.CHART_CIRCULAR) {
         return ChartType.CIRCULAR_NETWORK;
      }
      else if(GraphTypes.isFunnel(type)) {
         return ChartType.FUNNEL;
      }
      else if(GraphTypes.isGantt(type)) {
         return ChartType.GANTT;
      }
      else if(type == GraphTypes.CHART_NETWORK) {
         return ChartType.NETWORK;
      }
      else if(type == GraphTypes.CHART_TREE) {
         return ChartType.TREE;
      }
      else if(type == GraphTypes.CHART_SCATTER_CONTOUR) {
         return ChartType.SCATTER_CONTOUR;
      }
      else if(type == GraphTypes.CHART_MAP_CONTOUR) {
         return ChartType.MAP_CONTOUR;
      }
      else if(GraphTypes.isInterval(type)) {
         return ChartType.INTERVAL;
      }

      return ChartType.MULTI;
   }

   private static boolean isScatter(int type, ChartInfo info) {
      return GraphTypes.isPoint(type) && GraphUtil.hasMeasureOnX(info) && GraphUtil.hasMeasureOnY(info);
   }

   public static void resetUserValues(ChartDescriptor desc, ChartInfo info) {
      if(desc == null || info == null) {
         return;
      }

      LegendsDescriptor legendsDesc = desc.getLegendsDescriptor();

      if(legendsDesc != null) {
         legendsDesc.resetCompositeValues(CompositeValue.Type.USER);
      }

      PlotDescriptor plotDesc = desc.getPlotDescriptor();

      if(plotDesc != null) {
         plotDesc.resetCompositeValues(CompositeValue.Type.USER);
      }

      AxisDescriptor axisDesc = info.getAxisDescriptor();

      if(axisDesc != null) {
         axisDesc.resetCompositeValues(CompositeValue.Type.USER);
      }

      ChartRef[] candleFields = {};

      if(info instanceof CandleChartInfo) {
         CandleChartInfo cinfo = (CandleChartInfo) info;
         candleFields = new ChartRef[]{ cinfo.getCloseField(), cinfo.getHighField(),
                                        cinfo.getLowField(), cinfo.getOpenField() };
      }

      ChartRef[][] nrefs = { info.getXFields(), info.getYFields(), candleFields };

      for(int i = 0; i < nrefs.length; i++) {
         ChartRef[] refs = nrefs[i];

         for(ChartRef ref : refs) {
            if(ref == null) {
               continue;
            }

            axisDesc = GraphUtil.getAxisDescriptor(info, refs == candleFields ?
               null : ref);

            if(axisDesc != null) {
               axisDesc.resetCompositeValues(CompositeValue.Type.USER);
            }
         }
      }

      TitlesDescriptor titlesDesc = desc.getTitlesDescriptor();

      if(titlesDesc != null) {
         final String[] allAxis = { "x", "x2", "y", "y2" };

         for(String axis : allAxis) {
            TitleDescriptor titleDesc;

            if("x".equals(axis)) {
               titleDesc = titlesDesc.getXTitleDescriptor();
            }
            else if("x2".equals(axis)) {
               titleDesc = titlesDesc.getX2TitleDescriptor();
            }
            else if("y".equals(axis)) {
               titleDesc = titlesDesc.getYTitleDescriptor();
            }
            else { // y2
               titleDesc = titlesDesc.getY2TitleDescriptor();
            }

            titleDesc.resetCompositeValues(CompositeValue.Type.USER);
         }
      }

      setSizeFrameValues(info, null, false);
   }

   public enum ChartType {
      VBAR("VBar"),
      HBAR("HBar"),
      LINE("Line"),
      AREA("Area"),
      POINT("Point"),
      PIE("Pie"),
      DONUT("Donut"),
      RADAR("Radar"),
      STOCK("Stock"),
      CANDLE("Candle"),
      BOX_PLOT("BoxPlot"),
      WATERFALL("Waterfall"),
      PARETO("Pareto"),
      MAP("Map"),
      TREEMAP("Treemap"),
      SUNBURST("Sunburst"),
      CIRCLE_PACKING("CirclePacking"),
      ICICLE("Icicle"),
      MARIMEKKO("Marimekko"),
      HEATMAP("Heatmap"),
      SCATTER("Scatter"),
      CIRCULAR_NETWORK("CircularNetwork"),
      FUNNEL("Funnel"),
      GANTT("Gantt"),
      NETWORK("Network"),
      JUMP_LINE("JumpLine"),
      STEP("StepLine"),
      STEP_AREA("StepArea"),
      TREE("Tree"),
      SCATTER_CONTOUR("ScatterContour"),
      MAP_CONTOUR("ContourMap"),
      INTERVAL("Interval"),
      MULTI("Multi");

      ChartType(String cssName) {
         this.cssName = cssName;
      }

      public String getCssName() {
         return cssName;
      }

      private String cssName;
   }
}
