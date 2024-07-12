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
package inetsoft.web.graph.model.dialog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.graph.geo.service.MapboxService;
import inetsoft.graph.geo.service.MapboxStyle;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartPlotOptionsPaneModel {
   public ChartPlotOptionsPaneModel() {
   }

   public ChartPlotOptionsPaneModel(ChartInfo info, PlotDescriptor plotDesc) {
      initPlotComVisible(info);
      getPlotCheckBoxsSelected(plotDesc);
      getPlotBinding(info, plotDesc);
      getPlotCheckBoxsVisible(info);

      if(plotDesc.getBackground() != null) {
         backgroundColor = "#" + Tool.colorToHTMLString(plotDesc.getBackground());
      }

      if(plotDesc.getEmptyColor() != null) {
         mapEmptyColor = "#" + Tool.colorToHTMLString(plotDesc.getEmptyColor());
      }

      if(plotDesc.getBorderColor() != null) {
         borderColor = "#" + Tool.colorToHTMLString(plotDesc.getBorderColor());
      }

      if(plotDesc.getParetoLineColor() != null) {
         paretoLineColor = "#" + Tool.colorToHTMLString(plotDesc.getParetoLineColor());
      }

      getPlotComsEnable(info);
      hasXDimension = info.getXFieldCount() > 0;

      if(info instanceof GanttChartInfo) {
         GanttChartInfo ganttChartInfo = (GanttChartInfo) info;
         hasXDimension = ganttChartInfo.getEndField() != null ||
            ganttChartInfo.getStartField() != null || ganttChartInfo.getMilestoneField() != null;
      }

      if(info.getChartType() == GraphTypes.CHART_RADAR) {
         hasYDimension = containsDimension(info.getYFields());
      }
      else if(info instanceof CandleChartInfo) {
         CandleChartInfo candleChartInfo = (CandleChartInfo) info;
         hasYDimension = candleChartInfo.getHighField() != null ||
            candleChartInfo.getCloseField() != null || candleChartInfo.getOpenField() != null ||
            candleChartInfo.getLowField() != null;
      }
      else {
         hasYDimension = info.getYFieldCount() > 0;
      }

      backgroundEnabled = info.getChartType() != GraphTypes.CHART_TREEMAP &&
         !GraphTypes.isMekko(info.getChartType());
      alpha = plotDesc.getAlpha() >= 0 ? (int) (plotDesc.getAlpha() * 100) : null;
      wordCloudFontScale = plotDesc.getWordCloudFontScale() >= 0 ? plotDesc.getWordCloudFontScale()  : null;
      alphaEnabled = backgroundEnabled || info.getChartType() == GraphTypes.CHART_TREEMAP ||
         info.getChartType() == GraphTypes.CHART_MEKKO;
      this.mapEmptyColorVisible = GraphTypes.isGeo(info.getChartType());
      this.borderColorVisible = isBorderColorVisible(info);
      this.paretoLineColorVisible = isParetoLineColorVisible(info);
      this.webMap = plotDesc.isWebMap();
      this.webMapVisible = SreeEnv.getProperty("webmap.service") != null &&
         GraphTypes.isGeo(info.getChartType());
      this.webMapStyle = plotDesc.getWebMapStyle();
      this.mapPolygon = GraphUtil.containsMapPolygon(info);
      this.contourLevels = plotDesc.getContourLevels();
      this.contourBandwidth = plotDesc.getContourBandwidth();
      this.contourEdgeAlpha = (int) (plotDesc.getContourEdgeAlpha() * 100);
      this.contourCellSize = plotDesc.getContourCellSize();
      this.contourEnabled = GraphTypes.isContour(info.getChartType());
      this.includeParentLabels = plotDesc.isIncludeParentLabels();
      this.includeParentLabelsVisible = GraphTypes.isTreemap(info.getChartType()) &&
         info.getChartType() != GraphTypes.CHART_SUNBURST &&
         info.getChartType() != GraphTypes.CHART_ICICLE;
      this.applyAestheticsToSource = plotDesc.isApplyAestheticsToSource();
      this.applyAestheticsToSourceVisible = GraphTypes.isRelation(info.getChartType());
      this.wordCloud = GraphTypeUtil.isWordCloud(info);
      this.fillGapWithDashVisible = isFillGapWithDashVisible(info, plotDesc);
      this.pieRatio = plotDesc.getPieRatio() > 0 ? plotDesc.getPieRatio() : null;

      try {
         if(MapInfo.MAPBOX.equals(SreeEnv.getProperty("webmap.service"))) {
            this.mapboxStyles = new MapboxService().getStyles(true);
         }
      }
      catch(IOException e) {
         LOG.info("Failed to retrieve mapbox styles: " + e, e);
      }
   }

   public static boolean isParetoLineColorVisible(ChartInfo info) {
      return GraphTypes.isPareto(info.getChartType()) && info.getColorField() != null;
   }

   public static boolean isFillGapWithDashVisible(ChartInfo info, PlotDescriptor plot) {
      return GraphTypeUtil.isChartType(info, true, a -> GraphTypes.isLine(a) || GraphTypes.isRadar(a))
         || GraphTypeUtil.isChartType(info, true, GraphTypes::isPoint) && plot.isPointLine();
   }

   public static boolean isBorderColorVisible(ChartInfo info) {
      return info.getChartType() == GraphTypes.CHART_CIRCLE_PACKING ||
         info.getXFieldCount() + info.getYFieldCount() == 0 &&
         !GraphTypes.isTreemap(info.getChartType()) &&
         !GraphTypes.isRelation(info.getChartType()) &&
         !GraphTypes.isGeo(info.getChartType()) &&
         !GraphTypes.isCandle(info.getChartType()) &&
         !GraphTypes.isStock(info.getChartType()) &&
         !GraphTypes.isPie(info.getChartType()) &&
         !GraphTypes.isGantt(info.getChartType());
   }

   private boolean containsDimension(ChartRef[] refs) {
      if(refs == null || refs.length == 0) {
         return false;
      }

      return Arrays.stream(refs).anyMatch(ref -> ref instanceof ChartDimensionRef);
   }

   public void updateChartPlotOptionsPaneModel(ChartInfo info, PlotDescriptor plotDesc) {
      Color color;
      updatePlotBanding(plotDesc);
      plotDesc.setExploded(explodedPie, false);
      plotDesc.setInPlot(keepElementInPlot);
      plotDesc.setReferenceLineVisible(showReferenceLine);
      plotDesc.setValuesVisible(showValues);
      plotDesc.setStackValue(stackValues);
      plotDesc.setPointLine(showPoints);
      plotDesc.setPolygonColor(polygonColor);
      plotDesc.setAlpha(alpha == null ? -1 : alpha / 100.0, false);
      plotDesc.setWordCloudFontScale(wordCloudFontScale == null ? -1 : wordCloudFontScale, false);
      plotDesc.setFillTimeGap(fillTimeGap);
      plotDesc.setFillZero(fillZero);
      plotDesc.setFillGapWithDash(fillGapWithDash);
      color = Tool.getColorFromHexString(backgroundColor);
      plotDesc.setBackground(color, false);
      color = Tool.getColorFromHexString(mapEmptyColor);
      plotDesc.setEmptyColor(color, false);
      color = Tool.getColorFromHexString(borderColor);
      plotDesc.setBorderColor(color);
      color = Tool.getColorFromHexString(paretoLineColor);
      plotDesc.setParetoLineColor(color, false);
      plotDesc.setWebMap(webMap);
      plotDesc.setWebMapStyle(webMapStyle);
      plotDesc.setContourLevels(contourLevels);
      plotDesc.setContourBandwidth(contourBandwidth);
      plotDesc.setContourEdgeAlpha(contourEdgeAlpha / 100.0);
      plotDesc.setContourCellSize(contourCellSize);
      plotDesc.setIncludeParentLabels(includeParentLabels);
      plotDesc.setApplyAestheticsToSource(applyAestheticsToSource);
      plotDesc.setPieRatio(pieRatio != null ? pieRatio : 0);
      plotDesc.setOneLine(oneLine);
   }

   /**
    * Sets binding property. X/Y color and size
    * @plotDesc, Plot descriptor
    */
   private void updatePlotBanding(PlotDescriptor plotDesc) {
      Color color = null;
      color = Tool.getColorFromHexString(bandingXColor);
      plotDesc.setXBandColor(color);
      color = Tool.getColorFromHexString(bandingYColor);
      plotDesc.setYBandColor(color);
      plotDesc.setXBandSize(bandingXSize);
      plotDesc.setYBandSize(bandingYSize);
   }

   /**
    * Initional visible of the plot property components.
    * @model, plot properties model.
    * @cinfo, chart info.
    * @return void.
    */
   private void initPlotComVisible(ChartInfo cinfo) {
      keepElementInPlotVisible = true;
      showPointsVisible = true;
      lineTabVisible = true;

      if(GraphTypeUtil.isPolar(cinfo, true) || GraphTypes.isMekko(cinfo.getChartType()) ||
         GraphTypes.isRelation(cinfo.getChartType()) || GraphTypes.isGantt(cinfo.getChartType()))
      {
         keepElementInPlotVisible = false;
      }

      lineTabVisible = cinfo.isFacet() || !GraphTypeUtil.isPolar(cinfo, false)
         && !(cinfo instanceof MapInfo) && !GraphTypes.isTreemap(cinfo.getChartType())
         && !GraphTypes.isMekko(cinfo.getChartType());
   }

   /**
    * Sets the enablility of the components in the ui.
    * @param cinfo,  Chart Info
    */
   private void getPlotComsEnable(ChartInfo cinfo) {
      boolean appliedDc = cinfo instanceof VSChartInfo &&
         ((VSChartInfo) cinfo).isAppliedDateComparison();
      boolean stack = GraphTypeUtil.checkType(
         cinfo, appliedDc, c -> GraphTypes.isStack(c) || GraphTypes.isPareto(c));
      stackValuesEnabled = stack && (cinfo.getRTPathField() == null ||
         !GraphTypes.isLine(cinfo.getRTChartType()));
      stackValuesVisible = stack && !GraphTypes.isFunnel(cinfo.getChartType()) &&
         !GraphTypes.isContour(cinfo.getChartType());
   }

   /**
    * Get the status of the check box.
    */
   private void getPlotCheckBoxsSelected(PlotDescriptor plotDesc) {
      explodedPie = plotDesc.isExploded();
      keepElementInPlot = plotDesc.isInPlot();
      showReferenceLine = plotDesc.isReferenceLineVisible();
      showValues = plotDesc.isValuesVisible();
      stackValues = plotDesc.isStackValue();
      showPoints = plotDesc.isPointLine();
      polygonColor = plotDesc.isPolygonColor();
      fillTimeGap = plotDesc.isFillTimeGap();
      fillZero = plotDesc.isFillZero();
      fillGapWithDash = plotDesc.isFillGapWithDash();
      oneLine = plotDesc.isOneLine();
   }

   /**
    * Gets x/y banding controls for plot properties.
    * @cinfo, chart info of the current chart.
    * @plotDesc, plot descritor.
    */
   private void getPlotBinding(ChartInfo cinfo, PlotDescriptor plotDesc) {
      if(plotDesc.getXBandColor() != null) {
         bandingXColor = "#" + Tool.colorToHTMLString(plotDesc.getXBandColor());
      }

      if(plotDesc.getYBandColor() != null) {
         bandingYColor = "#" + Tool.colorToHTMLString(plotDesc.getYBandColor());
      }

      boolean xBand = GraphUtil.isXBandingEnabled(cinfo);
      boolean yBand = GraphUtil.isYBandingEnabled(cinfo);
      bandingXColorEnabled = xBand;
      bandingXSizeEnabled = xBand;
      bandingYColorEnabled = yBand;
      bandingYSizeEnabled = yBand;
      bandingXSize = plotDesc.getXBandSize();
      bandingYSize = plotDesc.getYBandSize();
      bandingXVisible = xBand || yBand;
      bandingYVisible = xBand || yBand;
   }

   /**
    * Check checkbox visible in different styles
    */
   private void getPlotCheckBoxsVisible(ChartInfo cinfo) {
      boolean isPie = supportsPie(cinfo);
      boolean isArea = GraphTypeUtil.hasVSChartType(cinfo, GraphTypes.CHART_AREA) ||
         GraphTypeUtil.hasVSChartType(cinfo, GraphTypes.CHART_AREA_STACK) ||
         GraphTypeUtil.hasVSChartType(cinfo, GraphTypes.CHART_STEP_AREA) ||
         GraphTypeUtil.hasVSChartType(cinfo, GraphTypes.CHART_STEP_AREA_STACK);
      boolean isAnyRadar = GraphTypes.isRadar(cinfo.getChartType());
      boolean isTreemap = GraphTypes.isTreemap(cinfo.getChartType());
      boolean isRelation = GraphTypes.isRelation(cinfo.getChartType());
      boolean isTree = cinfo.getChartType() == GraphTypes.CHART_TREE;
      boolean isGantt = GraphTypes.isGantt(cinfo.getChartType());
      boolean isLine = GraphTypeUtil.hasLineChartType(cinfo) ||
         GraphTypes.isGeo(cinfo.getChartType()) && cinfo.getRTPathField() != null;
      boolean isPoint = GraphTypeUtil.hasVSPointChartType(cinfo);

      if(isAnyRadar) {
         showValuesEnabled = supportShowValues(cinfo);
         showValuesVisible = true;
      }
      else {
         // tree always show at least one label (node label or text binding).
         showValuesEnabled = !(isTree && cinfo.getTextField() == null);
         showValuesVisible = supportShowValues(cinfo) && !isTreemap && !isGantt;
      }

      if(cinfo instanceof MapInfo && GraphUtil.containsOnlyExpPoint((MapInfo) cinfo)) {
         showValues = false;
         showValuesEnabled = false;
      }

      polygonColorVisible = cinfo instanceof MapInfo && !GraphTypes.isContour(cinfo.getChartType());
      final boolean isSimpleRadar = GraphTypes.CHART_RADAR == cinfo.getChartType();
      showPointsVisible = (isLine || isPoint || isSimpleRadar) &&
         !GraphTypeUtil.isWordCloud(cinfo) && !GraphTypeUtil.isHeatMapish(cinfo);

      if(isLine || isSimpleRadar) {
         showPointsLabel = Catalog.getCatalog().getString("Show Points");
      }
      else {
         showPointsLabel = Catalog.getCatalog().getString("Show Lines");
      }

      explodedPieVisible = isPie;
      boolean isMap = GraphTypes.isMap(cinfo.getChartType());
      showReferenceLineVisible = !isMap && (isPoint || isLine || isArea);
      fillTimeVisible = isTimeSeries(cinfo);
   }

   /**
    * Check if is time series.
    */
   private boolean isTimeSeries(ChartInfo cinfo) {
      if(!cinfo.isMultiStyles() && !GraphTypes.supportsFillTimeGap(cinfo.getRTChartType())) {
         return false;
      }

      java.util.List<ChartRef[]> rtfields = new ArrayList();
      rtfields.add(cinfo.getRTXFields());
      rtfields.add(cinfo.getRTYFields());

      boolean hastime = false;
      boolean hasline = false;

      for(int i = 0; i < rtfields.size(); i++) {
         DataRef[] fields = rtfields.get(i);

         if(fields.length > 0 &&
            fields[fields.length - 1] instanceof XDimensionRef &&
            ((XDimensionRef) fields[fields.length - 1]).isDateTime() &&
            ((XDimensionRef) fields[fields.length - 1]).isTimeSeries())
         {
            hastime = true;
         }

         for(int j = 0; j < fields.length; j++) {
            DataRef field = fields[j];
            if(field instanceof ChartAggregateRef) {
               int ctype  = ((ChartAggregateRef) field).getRTChartType();
               hasline = hasline || GraphTypes.supportsFillTimeGap(ctype);
            }
         }
      }

      return hastime && (hasline || !cinfo.isMultiStyles());
   }

   /**
    * Check if there is a pie chart.
    */
   private boolean supportsPie(ChartInfo cinfo) {
      return GraphTypeUtil.checkType(cinfo, (ctype) -> GraphTypes.isPie(ctype));
   }

   /**
    * Check if support show values.
    */
   private boolean supportShowValues(ChartInfo cinfo) {
      return !(cinfo instanceof CandleChartInfo) && !(cinfo instanceof StockChartInfo);
   }

   public Integer getAlpha() {
      return alpha;
   }

   public void setAlpha(Integer alpha) {
      this.alpha = alpha;
   }

   public boolean isAlphaEnabled() {
      return alphaEnabled;
   }

   public void setAlphaEnabled(boolean alphaEnabled) {
      this.alphaEnabled = alphaEnabled;
   }

   public boolean isShowValues() {
      return showValues;
   }

   public void setShowValues(boolean showValues) {
      this.showValues = showValues;
   }

   public boolean isShowValuesVisible() {
      return showValuesVisible;
   }

   public void setShowValuesVisible(boolean showValuesVisible) {
      this.showValuesVisible = showValuesVisible;
   }

   public boolean isShowValuesEnabled() {
      return showValuesEnabled;
   }

   public void setShowValuesEnabled(boolean showValuesEnabled) {
      this.showValuesEnabled = showValuesEnabled;
   }

   public boolean isStackValues() {
      return stackValues;
   }

   public void setStackValues(boolean stackValues) {
      this.stackValues = stackValues;
   }

   public boolean isStackValuesVisible() {
      return stackValuesVisible;
   }

   public void setStackValuesVisible(boolean stackValuesVisible) {
      this.stackValuesVisible = stackValuesVisible;
   }

   public boolean isStackValuesEnabled() {
      return stackValuesEnabled;
   }

   public void setStackValuesEnabled(boolean stackValuesEnabled) {
      this.stackValuesEnabled = stackValuesEnabled;
   }

   public boolean isShowReferenceLine() {
      return showReferenceLine;
   }

   public void setShowReferenceLine(boolean showReferenceLine) {
      this.showReferenceLine = showReferenceLine;
   }

   public boolean isShowReferenceLineVisible() {
      return showReferenceLineVisible;
   }

   public void setShowReferenceLineVisible(boolean showReferenceLineVisible) {
      this.showReferenceLineVisible = showReferenceLineVisible;
   }

   public boolean isKeepElementInPlot() {
      return keepElementInPlot;
   }

   public void setKeepElementInPlot(boolean keepElementInPlot) {
      this.keepElementInPlot = keepElementInPlot;
   }

   public boolean isKeepElementInPlotVisible() {
      return keepElementInPlotVisible;
   }

   public void setKeepElementInPlotVisible(boolean keepElementInPlotVisible) {
      this.keepElementInPlotVisible = keepElementInPlotVisible;
   }

   public String getBandingXColor() {
      return bandingXColor;
   }

   public void setBandingXColor(String bandingXColor) {
      this.bandingXColor = bandingXColor;
   }

   public int getBandingXSize() {
      return bandingXSize;
   }

   public void setBandingXSize(int bandingXSize) {
      this.bandingXSize = bandingXSize;
   }

   public boolean isBandingXVisible() {
      return bandingXVisible;
   }

   public void setBandingXVisible(boolean bandingXVisible) {
      this.bandingXVisible = bandingXVisible;
   }

   public boolean isBandingXColorEnabled() {
      return bandingXColorEnabled;
   }

   public void setBandingXColorEnabled(boolean bandingXColorEnabled) {
      this.bandingXColorEnabled = bandingXColorEnabled;
   }

   public boolean isBandingXSizeEnabled() {
      return bandingXSizeEnabled;
   }

   public void setBandingXSizeEnabled(boolean bandingXSizeEnabled) {
      this.bandingXSizeEnabled = bandingXSizeEnabled;
   }

   public String getBandingYColor() {
      return bandingYColor;
   }

   public void setBandingYColor(String bandingYColor) {
      this.bandingYColor = bandingYColor;
   }

   public int getBandingYSize() {
      return bandingYSize;
   }

   public void setBandingYSize(int bandingYSize) {
      this.bandingYSize = bandingYSize;
   }

   public boolean isBandingYVisible() {
      return bandingYVisible;
   }

   public void setBandingYVisible(boolean bandingYVisible) {
      this.bandingYVisible = bandingYVisible;
   }

   public boolean isBandingYColorEnabled() {
      return bandingYColorEnabled;
   }

   public void setBandingYColorEnabled(boolean bandingYColorEnabled) {
      this.bandingYColorEnabled = bandingYColorEnabled;
   }

   public boolean isBandingYSizeEnabled() {
      return bandingYSizeEnabled;
   }

   public void setBandingYSizeEnabled(boolean bandingYSizeEnabled) {
      this.bandingYSizeEnabled = bandingYSizeEnabled;
   }

   public String getBackgroundColor() {
      return backgroundColor;
   }

   public void setBackgroundColor(String backgroundColor) {
      this.backgroundColor = backgroundColor;
   }

   public boolean isBackgroundEnabled() {
      return backgroundEnabled;
   }

   public void setBackgroundEnabled(boolean backgroundEnabled) {
      this.backgroundEnabled = backgroundEnabled;
   }

   public boolean isShowPoints() {
      return showPoints;
   }

   public void setShowPoints(boolean showPoints) {
      this.showPoints = showPoints;
   }

   public boolean isShowPointsVisible() {
      return showPointsVisible;
   }

   public void setShowPointsVisible(boolean showPointsVisible) {
      this.showPointsVisible = showPointsVisible;
   }

   public String getShowPointsLabel() {
      return showPointsLabel;
   }

   public void setShowPointsLabel(String showPointsLabel) {
      this.showPointsLabel = showPointsLabel;
   }

   public boolean isLineTabVisible() {
      return lineTabVisible;
   }

   public void setLineTabVisible(boolean lineTabVisible) {
      this.lineTabVisible = lineTabVisible;
   }

   public boolean isExplodedPie() {
      return explodedPie;
   }

   public void setExplodedPie(boolean explodedPie) {
      this.explodedPie = explodedPie;
   }

   public boolean isExplodedPieVisible() {
      return explodedPieVisible;
   }

   public void setExplodedPieVisible(boolean explodedPieVisible) {
      this.explodedPieVisible = explodedPieVisible;
   }

   public boolean isFillTimeVisible() {
      return fillTimeVisible;
   }

   public void setFillTimeVisible(boolean fillTimeVisible) {
      this.fillTimeVisible = fillTimeVisible;
   }

   public boolean isFillTimeGap() {
      return fillTimeGap;
   }

   public void setFillTimeGap(boolean fillTimeGap) {
      this.fillTimeGap = fillTimeGap;
   }

   public boolean isFillZero() {
      return fillZero;
   }

   public void setFillZero(boolean fillZero) {
      this.fillZero = fillZero;
   }

   public boolean isFillGapWithDash() {
      return fillGapWithDash;
   }

   public void setFillGapWithDash(boolean fillGapWithDash) {
      this.fillGapWithDash = fillGapWithDash;
   }

   public boolean isFillGapWithDashVisible() {
      return fillGapWithDashVisible;
   }

   public void setFillGapWithDashVisible(boolean fillGapWithDashVisible) {
      this.fillGapWithDashVisible = fillGapWithDashVisible;
   }

   public boolean isPolygonColor() {
      return polygonColor;
   }

   public void setPolygonColor(boolean polygonColor) {
      this.polygonColor = polygonColor;
   }

   public boolean isPolygonColorVisible() {
      return polygonColorVisible;
   }

   public void setPolygonColorVisible(boolean polygonColorVisible) {
      this.polygonColorVisible = polygonColorVisible;
   }

   public boolean isHasXDimension() {
      return hasXDimension;
   }

   public void setHasXDimension(boolean hasXDimension) {
      this.hasXDimension = hasXDimension;
   }

   public boolean isHasYDimension() {
      return hasYDimension;
   }

   public void setHasYDimension(boolean hasYDimension) {
      this.hasYDimension = hasYDimension;
   }

   public String getMapEmptyColor() {
      return mapEmptyColor;
   }

   public void setMapEmptyColor(String mapEmptyColor) {
      this.mapEmptyColor = mapEmptyColor;
   }

   public boolean isMapEmptyColorVisible() {
      return mapEmptyColorVisible;
   }

   public void setMapEmptyColorVisible(boolean mapEmptyColorVisible) {
      this.mapEmptyColorVisible = mapEmptyColorVisible;
   }

   public String getBorderColor() {
      return borderColor;
   }

   public void setBorderColor(String borderColor) {
      this.borderColor = borderColor;
   }

   public boolean isBorderColorVisible() {
      return borderColorVisible;
   }

   public void setBorderColorVisible(boolean borderColorVisible) {
      this.borderColorVisible = borderColorVisible;
   }

   public String getParetoLineColor() {
      return paretoLineColor;
   }

   public void setParetoLineColor(String paretoLineColor) {
      this.paretoLineColor = paretoLineColor;
   }

   public boolean isParetoLineColorVisible() {
      return paretoLineColorVisible;
   }

   public void setParetoLineColorVisible(boolean paretoLineColorVisible) {
      this.paretoLineColorVisible = paretoLineColorVisible;
   }

   public boolean isWebMap() {
      return webMap;
   }

   public void setWebMap(boolean webMap) {
      this.webMap = webMap;
   }

   public boolean isWebMapVisible() {
      return webMapVisible;
   }

   public void setWebMapVisible(boolean webMapVisible) {
      this.webMapVisible = webMapVisible;
   }

   public String getWebMapStyle() {
      return webMapStyle;
   }

   public void setWebMapStyle(String style) {
      this.webMapStyle = style;
   }

   public List<MapboxStyle> getMapboxStyles() {
      return mapboxStyles;
   }

   public void setMapboxStyles(List<MapboxStyle> mapboxStyles) {
      this.mapboxStyles = mapboxStyles;
   }

   public int getContourLevels() {
      return contourLevels;
   }

   public void setContourLevels(int contourLevels) {
      this.contourLevels = contourLevels;
   }

   public int getContourBandwidth() {
      return contourBandwidth;
   }

   public void setContourBandwidth(int contourBandwidth) {
      this.contourBandwidth = contourBandwidth;
   }

   public int getContourCellSize() {
      return contourCellSize;
   }

   public void setContourCellSize(int contourCellSize) {
      this.contourCellSize = contourCellSize;

   }
   public int getContourEdgeAlpha() {
      return contourEdgeAlpha;
   }

   public void setContourEdgeAlpha(int contourEdgeAlpha) {
      this.contourEdgeAlpha = contourEdgeAlpha;
   }

   public boolean isContourEnabled() {
      return contourEnabled;
   }

   public void setContourEnabled(boolean contourEnabled) {
      this.contourEnabled = contourEnabled;
   }

   public boolean isMapPolygon() {
      return mapPolygon;
   }

   public void setMapPolygon(boolean mapPolygon) {
      this.mapPolygon = mapPolygon;
   }

   public boolean isIncludeParentLabels() {
      return includeParentLabels;
   }

   public void setIncludeParentLabels(boolean includeParentLabels) {
      this.includeParentLabels = includeParentLabels;
   }

   public boolean isIncludeParentLabelsVisible() {
      return includeParentLabelsVisible;
   }

   public void setIncludeParentLabelsVisible(boolean includeParentLabelsVisible) {
      this.includeParentLabelsVisible = includeParentLabelsVisible;
   }

   public boolean isApplyAestheticsToSourceVisible() {
      return applyAestheticsToSourceVisible;
   }

   public void setApplyAestheticsToSourceVisible(boolean applyAestheticsToSourceVisible) {
      this.applyAestheticsToSourceVisible = applyAestheticsToSourceVisible;
   }

   public boolean isApplyAestheticsToSource() {
      return applyAestheticsToSource;
   }

   public void setApplyAestheticsToSource(boolean applyAestheticsToSource) {
      this.applyAestheticsToSource = applyAestheticsToSource;
   }

   public boolean isWordCloud() {
      return wordCloud;
   }

   public void setWordCloud(boolean wordCloud) {
      this.wordCloud = wordCloud;
   }

   public Double getWordCloudFontScale() {
      return wordCloudFontScale;
   }

   public void setWordCloudFontScale(Double wordCloudFontScale) {
      this.wordCloudFontScale = wordCloudFontScale;
   }

   public Double getPieRatio() {
      return pieRatio;
   }

   public void setPieRatio(Double pieRatio) {
      this.pieRatio = pieRatio;
   }

   public boolean isOneLine() {
      return oneLine;
   }

   public void setOneLine(boolean oneLine) {
      this.oneLine = oneLine;
   }

   private Integer alpha;
   private boolean alphaEnabled;
   private boolean showValues;
   private boolean showValuesVisible;
   private boolean showValuesEnabled;
   private boolean stackValues;
   private boolean stackValuesVisible;
   private boolean stackValuesEnabled;
   private boolean showReferenceLine;
   private boolean showReferenceLineVisible;
   private boolean keepElementInPlot;
   private boolean keepElementInPlotVisible;
   private String bandingXColor;
   private int bandingXSize;
   private boolean bandingXVisible;
   private boolean bandingXColorEnabled;
   private boolean bandingXSizeEnabled;
   private String bandingYColor;
   private int bandingYSize;
   private boolean bandingYVisible;
   private boolean bandingYColorEnabled;
   private boolean bandingYSizeEnabled;
   private String backgroundColor;
   private boolean backgroundEnabled;
   private boolean showPoints;
   private boolean showPointsVisible;
   private String showPointsLabel;
   private boolean lineTabVisible = true;
   private boolean explodedPie;
   private boolean explodedPieVisible;
   private boolean fillTimeVisible;
   private boolean fillTimeGap;
   private boolean fillZero;
   private boolean fillGapWithDash;
   private boolean fillGapWithDashVisible;
   private boolean polygonColor;
   private boolean polygonColorVisible;
   private boolean hasXDimension;
   private boolean hasYDimension;
   private String mapEmptyColor;
   private boolean mapEmptyColorVisible;
   private String borderColor;
   private boolean borderColorVisible;
   private String paretoLineColor;
   private boolean paretoLineColorVisible;
   private boolean webMap;
   private boolean webMapVisible;
   private String webMapStyle;
   private boolean mapPolygon;
   private int contourLevels;
   private int contourBandwidth;
   private int contourEdgeAlpha;
   private int contourCellSize;
   private boolean contourEnabled = false;
   private List<MapboxStyle> mapboxStyles;
   private boolean includeParentLabels;
   private boolean includeParentLabelsVisible;
   private boolean applyAestheticsToSource;
   private boolean applyAestheticsToSourceVisible;
   private boolean wordCloud = false;
   private Double wordCloudFontScale;
   private Double pieRatio;
   private boolean oneLine;
   private final static Logger LOG = LoggerFactory.getLogger(ChartPlotOptionsPaneModel.class);
}
