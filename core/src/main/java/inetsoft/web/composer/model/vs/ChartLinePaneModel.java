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
package inetsoft.web.composer.model.vs;

import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;

import java.awt.*;
import java.io.Serializable;
import java.util.List;
import java.util.*;

public class ChartLinePaneModel implements Serializable {
   public ChartLinePaneModel() {
   }

   public ChartLinePaneModel(ChartInfo info, PlotDescriptor plotDesc) {
      initPlotComVisible(info);
      getPlotXYGird(info, plotDesc);
      getPlotQuadrantGrid(plotDesc);
      getPlotDiagonalGrid(plotDesc);
      getPlotFacetGrid(plotDesc);
      getPlotTrendLineGrid(info, plotDesc);
      getPlotComsEnable(info);

      lineTabVisible = gridLineVisible || innerLineVisible || facetGridVisible;
      trendLineVisible = !GraphTypeUtil.isWaterfall(info) && innerLineVisible &&
         !GraphTypes.isTreemap(info.getChartType()) &&
         !GraphTypes.isRelation(info.getChartType()) &&
         info.getChartType() != GraphTypes.CHART_BOXPLOT &&
         info.getChartType() != GraphTypes.CHART_MEKKO &&
         info.getChartType() != GraphTypes.CHART_FUNNEL &&
         info.getChartType() != GraphTypes.CHART_GANTT;
   }

   public void updateChartLinePaneModel(ChartInfo cinfo, PlotDescriptor plotDesc) {
      Color color;
      updatePlotXYGird(cinfo, plotDesc);
      updatePlotTrendLineGrid(plotDesc);

      plotDesc.setDiagonalStyle(diagonalLineStyle, false);
      color = Tool.getColorFromHexString(diagonalLineColor);
      plotDesc.setDiagonalColor(color, false);
      plotDesc.setQuadrantStyle(quadrantGridLineStyle, false);
      color = Tool.getColorFromHexString(quadrantGridLineColor);
      plotDesc.setQuadrantColor(color, false);

      plotDesc.setFacetGrid(facetGrid, false);
      color = Tool.getColorFromHexString(facetGridColor);
      plotDesc.setFacetGridColor(color, false);
   }

   private void updatePlotTrendLineGrid(PlotDescriptor plotDesc) {
      plotDesc.setTrendline(getIndexByName(TRENDLINE_NAMES, trendLineType));
      plotDesc.setTrendLineStyle(trendLineStyle);
      Color color = Tool.getColorFromHexString(trendLineColor);
      plotDesc.setTrendLineColor(color);
      plotDesc.setTrendPerColor(trendPerColor);
      plotDesc.setProjectTrendLineForward(projectForward);

      Set all = new HashSet(Arrays.asList(measures));
      all.removeAll(Arrays.asList(trendLineMeasures));
      plotDesc.getTrendLineExcludedMeasures().clear();
      plotDesc.getTrendLineExcludedMeasures().addAll(all);
   }

   /**
    * Gets the properties of the trend line in plot of current chart.
    * @plotDesc. descriptor of the plot for current chat.
    */
   private void getPlotTrendLineGrid(ChartInfo info, PlotDescriptor plotDesc) {
      if(plotDesc.getTrendLineColor() != null) {
         trendLineColor = "#" + Tool.colorToHTMLString(plotDesc.getTrendLineColor());
      }

      trendLineType = TRENDLINE_NAMES[plotDesc.getTrendline()];
      trendPerColor = plotDesc.isTrendPerColor();
      trendLineStyle = plotDesc.getTrendLineStyle();
      projectForward = plotDesc.getProjectTrendLineForward();
      Set<String> trendLineExcludedMeasures = plotDesc.getTrendLineExcludedMeasures();

      ChartRef[] xaggrs = info.getRTXFields();
      ChartRef[] yaggrs = info.getRTYFields();
      List<ChartRef> highLow = new ArrayList();

      if(info instanceof CandleChartInfo ||info instanceof StockChartInfo) {
         highLow.add(((CandleChartInfo) info).getRTHighField());
         highLow.add(((CandleChartInfo) info).getRTLowField());
         highLow.add(((CandleChartInfo) info).getRTOpenField());
         highLow.add(((CandleChartInfo) info).getRTCloseField());
      }

      if(Arrays.stream(yaggrs).anyMatch(a -> !GraphUtil.isDimension(a))) {
         measures = Arrays.stream(yaggrs)
            .filter(a -> !GraphUtil.isDimension(a))
            .map(a -> GraphUtil.getName(a))
            .toArray(String[]::new);
      }
      else if(Arrays.stream(xaggrs).anyMatch(a -> !GraphUtil.isDimension(a))){
         measures = Arrays.stream(xaggrs)
            .filter(a -> !GraphUtil.isDimension(a))
            .map(a -> GraphUtil.getName(a))
            .toArray(String[]::new);
      }
      else {
         measures = highLow.stream()
            .filter(Objects::nonNull)
            .distinct()
            .map(a -> GraphUtil.getName(a))
            .toArray(String[]::new);
      }

      Set<String> all = new HashSet<>(Arrays.asList(measures));
      all.removeAll(trendLineExcludedMeasures);
      trendLineMeasures = all.toArray(new String[0]);
   }

   /**
    * Sets X and Y Grid. Style and Color.
    */
   private void updatePlotXYGird(ChartInfo cinfo, PlotDescriptor plotDesc) {
      Color color = null;

      if(cinfo.isInvertedGraph()) {
         plotDesc.setXGridStyle(yGridLineStyle, false);
         plotDesc.setYGridStyle(xGridLineStyle, false);
         color = Tool.getColorFromHexString(yGridLineColor);
         plotDesc.setXGridColor(color, false);
         color = Tool.getColorFromHexString(xGridLineColor);
         plotDesc.setYGridColor(color, false);
      }
      else {
         plotDesc.setXGridStyle(xGridLineStyle, false);
         plotDesc.setYGridStyle(yGridLineStyle, false);
         color = Tool.getColorFromHexString(xGridLineColor);
         plotDesc.setXGridColor(color, false);
         color = Tool.getColorFromHexString(yGridLineColor);
         plotDesc.setYGridColor(color, false);
      }
   }

   /**
    * Gets the facet grid properties.
    * @plotDesc, descriptor of the plot.
    */
   private void getPlotFacetGrid(PlotDescriptor plotDesc) {
      if(plotDesc.getFacetGridColor() != null) {
         facetGridColor = "#" + Tool.colorToHTMLString(plotDesc.getFacetGridColor());
      }

      facetGrid = plotDesc.isFacetGrid();
   }

   /**
    * Gets facet gird properties.
    * @plotDesc. descriptor of the plot for the current chart.
    */
   private void getPlotDiagonalGrid(PlotDescriptor plotDesc){
      if(plotDesc.getDiagonalColor() != null) {
         diagonalLineColor = "#" + Tool.colorToHTMLString(plotDesc.getDiagonalColor());
      }

      diagonalLineStyle = plotDesc.getDiagonalStyle();
   }

   /**
    * Gets quadrant grid properties. (gird style/color)
    * @plotDesc, plot descriptor.
    */
   private void getPlotQuadrantGrid(PlotDescriptor plotDesc){
      if(plotDesc.getQuadrantColor() != null) {
         quadrantGridLineColor = "#" + Tool.colorToHTMLString(plotDesc.getQuadrantColor());
      }

      quadrantGridLineStyle = plotDesc.getQuadrantStyle();
   }

   /**
    * Sets X and Y grid binding property. (color/grid style).
    * @cinfo, chart info of the current chart.
    * @plotDesc, plot descriptor of the current chart.
    * @return, void.
    */
   private void getPlotXYGird(ChartInfo cinfo, PlotDescriptor plotDesc){
      if(cinfo.isInvertedGraph()) {
         xGridLineStyle = plotDesc.getYGridStyle();
         yGridLineStyle = plotDesc.getXGridStyle();

         if(plotDesc.getXGridColor() != null) {
            xGridLineColor = "#"+ Tool.colorToHTMLString(plotDesc.getYGridColor());
         }

         if(plotDesc.getYGridColor() != null) {
            yGridLineColor = "#"+ Tool.colorToHTMLString(plotDesc.getXGridColor());
         }
      }
      else {
         xGridLineStyle = plotDesc.getXGridStyle();
         yGridLineStyle = plotDesc.getYGridStyle();

         if(plotDesc.getXGridColor() != null) {
            xGridLineColor = "#" + Tool.colorToHTMLString(plotDesc.getXGridColor());
         }

         if(plotDesc.getYGridColor() != null) {
            yGridLineColor = "#" + Tool.colorToHTMLString(plotDesc.getYGridColor());
         }
      }
   }

   /**
    * Sets the enablility of the components in the ui.
    * @param cinfo,  Chart Info
    */
   private void getPlotComsEnable(ChartInfo cinfo) {
      final Set<Integer> ctypes = new HashSet<>();
      GraphTypeUtil.checkType(cinfo, c -> {
         ctypes.add(c);
         return false;
      });

      boolean facetGridEnabled0 = GraphTypeUtil.checkType(cinfo, ctype ->
         ctype != GraphTypes.CHART_3D_BAR &&
            ctype != GraphTypes.CHART_3D_BAR_STACK);
      facetGridEnabled = facetGridEnabled0;
      projectForwardEnabled = cinfo instanceof AbstractChartInfo && cinfo.canProjectForward();

      if(!projectForwardEnabled) {
         projectForward = 0;
      }
   }

   /**
    * Initional visible of the plot property components.
    * @model, plot properties model.
    * @cinfo, chart info.
    * @return void.
    */
   private void initPlotComVisible(ChartInfo cinfo) {
      gridLineVisible = innerLineVisible = true;
      trendLineVisible = true;
      facetGridVisible = cinfo.isFacet();

      if(GraphTypeUtil.isPolar(cinfo, false) || cinfo instanceof MapInfo ||
         GraphTypes.isTreemap(cinfo.getChartType()) || GraphTypes.isMekko(cinfo.getChartType()) ||
         GraphTypes.isRelation(cinfo.getChartType()))
      {
         gridLineVisible = false;
         innerLineVisible = false;
      }
   }

   /**
    * Gets array index by name.
    */
   private int getIndexByName(String[] arr, String name) {
      int index = 0;

      for(int i = 0; i < arr.length; i++) {
         if(name.equals(arr[i])) {
            index = i;
         }
      }

      return index;
   }

   public String getxGridLineColor() {
      return xGridLineColor;
   }

   public void setxGridLineColor(String xGridLineColor) {
      this.xGridLineColor = xGridLineColor;
   }

   public int getxGridLineStyle() {
      return xGridLineStyle;
   }

   public void setxGridLineStyle(int xGridLineStyle) {
      this.xGridLineStyle = xGridLineStyle;
   }

   public boolean isGridLineVisible() {
      return gridLineVisible;
   }

   public void setGridLineVisible(boolean gridLineVisible) {
      this.gridLineVisible = gridLineVisible;
   }

   public boolean isInnerLineVisible() {
      return innerLineVisible;
   }

   public void setInnerLineVisible(boolean innerLineVisible) {
      this.innerLineVisible = innerLineVisible;
   }

   public int getyGridLineStyle() {
      return yGridLineStyle;
   }

   public void setyGridLineStyle(int yGridLineStyle) {
      this.yGridLineStyle = yGridLineStyle;
   }

   public String getyGridLineColor() {
      return yGridLineColor;
   }

   public void setyGridLineColor(String yGridLineColor) {
      this.yGridLineColor = yGridLineColor;
   }

   public int getQuadrantGridLineStyle() {
      return quadrantGridLineStyle;
   }

   public void setQuadrantGridLineStyle(int quadrantGridLineStyle) {
      this.quadrantGridLineStyle = quadrantGridLineStyle;
   }

   public String getQuadrantGridLineColor() {
      return quadrantGridLineColor;
   }

   public void setQuadrantGridLineColor(String quadrantGridLineColor) {
      this.quadrantGridLineColor = quadrantGridLineColor;
   }

   public int getDiagonalLineStyle() {
      return diagonalLineStyle;
   }

   public void setDiagonalLineStyle(int diagonalLineStyle) {
      this.diagonalLineStyle = diagonalLineStyle;
   }

   public String getDiagonalLineColor() {
      return diagonalLineColor;
   }

   public void setDiagonalLineColor(String diagonalLineColor) {
      this.diagonalLineColor = diagonalLineColor;
   }

   public String getTrendLineType() {
      return trendLineType;
   }

   public void setTrendLineType(String trendLineType) {
      this.trendLineType = trendLineType;
   }

   public boolean isTrendPerColor() {
      return trendPerColor;
   }

   public void setTrendPerColor(boolean trendPerColor) {
      this.trendPerColor = trendPerColor;
   }

   public int getTrendLineStyle() {
      return trendLineStyle;
   }

   public void setTrendLineStyle(int trendLineStyle) {
      this.trendLineStyle = trendLineStyle;
   }

   public String getTrendLineColor() {
      return trendLineColor;
   }

   public void setTrendLineColor(String trendLineColor) {
      this.trendLineColor = trendLineColor;
   }

   public boolean isTrendLineVisible() {
      return trendLineVisible;
   }

   public void setTrendLineVisible(boolean trendLineVisible) {
      this.trendLineVisible = trendLineVisible;
   }

   public int getProjectForward() {
      return projectForward;
   }

   public void setProjectForward(int projectForward) {
      this.projectForward = projectForward;
   }

   public boolean isProjectForwardEnabled() {
      return projectForwardEnabled;
   }

   public void setProjectForwardEnabled(boolean projectForwardEnabled) {
      this.projectForwardEnabled = projectForwardEnabled;
   }

   public boolean isLineTabVisible() {
      return lineTabVisible;
   }

   public void setLineTabVisible(boolean lineTabVisible) {
      this.lineTabVisible = lineTabVisible;
   }

   public boolean isFacetGrid() {
      return facetGrid;
   }

   public void setFacetGrid(boolean facetGrid) {
      this.facetGrid = facetGrid;
   }

   public String getFacetGridColor() {
      return facetGridColor;
   }

   public void setFacetGridColor(String facetGridColor) {
      this.facetGridColor = facetGridColor;
   }

   public boolean isFacetGridVisible() {
      return facetGridVisible;
   }

   public void setFacetGridVisible(boolean facetGridVisible) {
      this.facetGridVisible = facetGridVisible;
   }

   public boolean isFacetGridEnabled() {
      return facetGridEnabled;
   }

   public void setFacetGridEnabled(boolean facetGridEnabled) {
      this.facetGridEnabled = facetGridEnabled;
   }

   public String[] getTrendLineMeasures() {
      return trendLineMeasures;
   }

   public void setTrendLineMeasures(String[] trendLineMeasures) {
      this.trendLineMeasures = trendLineMeasures;
   }

   public String[] getMeasures() {
      return measures;
   }

   public void setMeasures(String[] measures) {
      this.measures = measures;
   }

   private int xGridLineStyle;
   private String xGridLineColor;
   private int yGridLineStyle;
   private String yGridLineColor;
   private boolean gridLineVisible;
   private int quadrantGridLineStyle;
   private String quadrantGridLineColor;
   private int diagonalLineStyle;
   private String diagonalLineColor;
   private boolean innerLineVisible;
   private String trendLineType;
   private boolean trendPerColor;
   private int trendLineStyle;
   private String trendLineColor;
   private boolean trendLineVisible;
   private int projectForward;
   private boolean projectForwardEnabled;
   private boolean lineTabVisible;
   private boolean facetGrid;
   private String facetGridColor;
   private boolean facetGridVisible;
   private boolean facetGridEnabled;
   private String [] trendLineMeasures;
   private String[] measures;

   private static final String[] TRENDLINE_NAMES =
      {"NONE", "Linear", "Quadratic", "Cubic", "Exponential", "Logarithmic", "Power"};
}
