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

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.geo.service.MapboxService;
import inetsoft.graph.geo.service.MapboxStyle;
import inetsoft.graph.internal.GDefaults;
import inetsoft.report.StyleConstants;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.ContentObject;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.PrintWriter;
import java.util.List;
import java.util.*;

/**
 * This descriptor keeps the information of plot.
 *
 * @version 10.0
 * @author InetSoft Technology Corp.
 */
public class PlotDescriptor implements AssetObject, ContentObject {
   /**
    * Create a PlotDescriptor.
    */
   public PlotDescriptor() {
      fmt = new CompositeTextFormat();
      fmt.getCSSFormat().setCSSType(CSSConstants.CHART_PLOTLABELS);
      initPlotErrorFormat();
   }

   public void initDefaultFormat() {
      initDefaultFormat(false);
   }

   public void initDefaultFormat(boolean vs) {
      fmt.getDefaultFormat().setColor(GDefaults.DEFAULT_TEXT_COLOR);
      fmt.getDefaultFormat().setFont(vs ? VSAssemblyInfo.getDefaultFont(VSUtil.getDefaultFont()) :
         VSUtil.getDefaultFont());
   }

   private void initPlotErrorFormat() {
      errorFormat = new CompositeTextFormat();
      errorFormat.getCSSFormat().setCSSType(CSSConstants.CHART_PLOT_ERROR);
      errorFormat.getDefaultFormat().setColor(Color.decode("#fe688b"));
      errorFormat.getDefaultFormat().setBackground(Color.decode("#f0f0f0"));
      errorFormat.getDefaultFormat().setAlignment(StyleConstants.H_CENTER | StyleConstants.V_CENTER);
      Font font = VSUtil.getDefaultFont();
      font = font.deriveFont(13f);
      errorFormat.getDefaultFormat().setFont(font);
   }

   /**
    * Get text format.
    */
   public CompositeTextFormat getTextFormat() {
      return fmt;
   }

   /**
    * Set the text format.
    * @param textFormat the text format.
    */
   public void setTextFormat(CompositeTextFormat textFormat) {
      this.fmt = textFormat;
   }

   /**
    * Get the trendline.
    */
   public int getTrendline() {
      return trendline;
   }

   /**
    * Set the trendline.
    * @param trendline the trendline.
    */
   public void setTrendline(int trendline) {
      this.trendline = trendline;
   }

   /**
    * Get the number of forward increments for trend line projection
    */
   // @by: ChrisSpagnoli feature1379102629417 2015-4-2
   // Every call to getProjectTrendLineForward() needs to be wrapped by
   // AbstractChartInfo.canProjectForward()
   public int getProjectTrendLineForward() {
      return projectTrendLineForward;
   }

   /**
    * Set the number of forward increments for trend line projection
    * @param inc the number of forward increments.
    */
   public void setProjectTrendLineForward(int inc) {
      this.projectTrendLineForward = inc;
   }

   /**
    * Get the x grid color.
    */
   public Color getXGridColor() {
      return xGridColor.get();
   }

   /**
    * Set the x grid color.
    * @param xGridColor the x grid color.
    */
   public void setXGridColor(Color xGridColor) {
      setXGridColor(xGridColor, true);
   }

   /**
    * Set the x grid color.
    * @param xGridColor the x grid color.
    * @param force if true always sets the USER value; otherwise only sets the USER value if
    *              it's different from the current value
    */
   public void setXGridColor(Color xGridColor, boolean force) {
      if(force || !Tool.equals(xGridColor, getXGridColor())) {
         if(xGridColor == null) {
            this.xGridColor.resetValue(CompositeValue.Type.USER);
         }
         else {
            setXGridColor(xGridColor, CompositeValue.Type.USER);
         }
      }
   }

   /**
    * Set the x grid color.
    * @param xGridColor the x grid color.
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setXGridColor(Color xGridColor, CompositeValue.Type type) {
      this.xGridColor.setValue(xGridColor, type);
   }

   /**
    * Get the y grid color.
    */
   public Color getYGridColor() {
      return yGridColor.get();
   }

   /**
    * Set the y grid color.
    * @param yGridColor the y grid color.
    */
   public void setYGridColor(Color yGridColor) {
      setYGridColor(yGridColor, true);
   }

   /**
    * Set the y grid color.
    * @param yGridColor the y grid color.
    * @param force if true always sets the USER value; otherwise only sets the USER value if
    *              it's different from the current value
    */
   public void setYGridColor(Color yGridColor, boolean force) {
      if(force || !Tool.equals(yGridColor, getYGridColor())) {
         if(yGridColor == null) {
            this.yGridColor.resetValue(CompositeValue.Type.USER);
         }
         else {
            setYGridColor(yGridColor, CompositeValue.Type.USER);
         }
      }
   }

   /**
    * Set the y grid color.
    * @param yGridColor the y grid color.
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setYGridColor(Color yGridColor, CompositeValue.Type type) {
      this.yGridColor.setValue(yGridColor, type);
   }

   /**
    * Get the x grid style.
    */
   public int getXGridStyle() {
      return xGridStyle.get();
   }

   /**
    * Set the x grid style.
    * @param xGridStyle the x grid style.
    */
   public void setXGridStyle(int xGridStyle) {
      setXGridStyle(xGridStyle, true);
   }

   /**
    * Set the x grid style.
    * @param xGridStyle the x grid style.
    * @param force if true always sets the USER value; otherwise only sets the USER value if
    *              it's different from the current value
    */
   public void setXGridStyle(int xGridStyle, boolean force) {
      if(force || xGridStyle != getXGridStyle()) {
         setXGridStyle(xGridStyle, CompositeValue.Type.USER);
      }
   }

   /**
    * Set the x grid style.
    * @param xGridStyle the x grid style.
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setXGridStyle(int xGridStyle, CompositeValue.Type type) {
      this.xGridStyle.setValue(xGridStyle, type);
   }

   /**
    * Get the y grid style.
    */
   public int getYGridStyle() {
      return yGridStyle.get();
   }

   /**
    * Set the y grid style.
    * @param yGridStyle the y grid style.
    */
   public void setYGridStyle(int yGridStyle) {
      setYGridStyle(yGridStyle, true);
   }

   /**
    * Set the y grid style.
    * @param yGridStyle the y grid style.
    * @param force if true always sets the USER value; otherwise only sets the USER value if
    *              it's different from the current value
    */
   public void setYGridStyle(int yGridStyle, boolean force) {
      if(force || yGridStyle != getYGridStyle()) {
         setYGridStyle(yGridStyle, CompositeValue.Type.USER);
      }
   }

   /**
    * Set the y grid style.
    * @param yGridStyle the y grid style.
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setYGridStyle(int yGridStyle, CompositeValue.Type type) {
      this.yGridStyle.setValue(yGridStyle, type);
   }

   /**
    * Get the diagonal line color.
    */
   public Color getDiagonalColor() {
      return diagonalColor.get();
   }

   /**
    * Set the diagonal line color.
    * @param color diagonal line color
    */
   public void setDiagonalColor(Color color) {
      setDiagonalColor(color, true);
   }

   /**
    * Set the diagonal line color.
    * @param color diagonal line color
    * @param force if true always sets the USER value; otherwise only sets the USER value if
    *              it's different from the current value
    */
   public void setDiagonalColor(Color color, boolean force) {
      if(force || !Tool.equals(color, getDiagonalColor())) {
         if(color == null) {
            this.diagonalColor.resetValue(CompositeValue.Type.USER);
         }
         else {
            setDiagonalColor(color, CompositeValue.Type.USER);
         }
      }
   }

   /**
    * Set the diagonal line color.
    * @param color diagonal line color
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setDiagonalColor(Color color, CompositeValue.Type type) {
      this.diagonalColor.setValue(color, type);
   }

   /**
    * Get the diagonal line style.
    */
   public int getDiagonalStyle() {
      return diagonalStyle.get();
   }

   /**
    * Set the diagonal style.
    * @param style diagonal line style
    */
   public void setDiagonalStyle(int style) {
      setDiagonalStyle(style, true);
   }

   /**
    * Set the diagonal style.
    * @param style diagonal line style
    * @param force if true always sets the USER value; otherwise only sets the USER value if
    *              it's different from the current value
    */
   public void setDiagonalStyle(int style, boolean force) {
      if(force || style != getDiagonalStyle()) {
         setDiagonalStyle(style, CompositeValue.Type.USER);
      }
   }

   /**
    * Set the diagonal style.
    * @param style diagonal line style
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setDiagonalStyle(int style, CompositeValue.Type type) {
      this.diagonalStyle.setValue(style, type);
   }

   /**
    * Get the quadrant line color.
    */
   public Color getQuadrantColor() {
      return quadrantColor.get();
   }

   /**
    * Set the quadrant line color.
    * @param color quadrant line color
    */
   public void setQuadrantColor(Color color) {
      setQuadrantColor(color, true);
   }

   /**
    * Set the quadrant line color.
    * @param color quadrant line color
    * @param force if true always sets the USER value; otherwise only sets the USER value if
    *              it's different from the current value
    */
   public void setQuadrantColor(Color color, boolean force) {
      if(force || !Tool.equals(color, getQuadrantColor())) {
         if(color == null) {
            this.quadrantColor.resetValue(CompositeValue.Type.USER);
         }
         else {
            setQuadrantColor(color, CompositeValue.Type.USER);
         }
      }
   }

   /**
    * Set the quadrant line color.
    * @param color quadrant line color
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setQuadrantColor(Color color, CompositeValue.Type type) {
      this.quadrantColor.setValue(color, type);
   }

   /**
    * Get the quadrant line style.
    */
   public int getQuadrantStyle() {
      return quadrantStyle.get();
   }

   /**
    * Set the quadrant style.
    * @param style quadrant line style
    */
   public void setQuadrantStyle(int style) {
      setQuadrantStyle(style, true);
   }

   /**
    * Set the quadrant style.
    * @param style quadrant line style
    * @param force if true always sets the USER value; otherwise only sets the USER value if
    *              it's different from the current value
    */
   public void setQuadrantStyle(int style, boolean force) {
      if(force || style != getQuadrantStyle()) {
         setQuadrantStyle(style, CompositeValue.Type.USER);
      }
   }

   /**
    * Set the quadrant style.
    * @param style quadrant line style
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setQuadrantStyle(int style, CompositeValue.Type type) {
      this.quadrantStyle.setValue(style, type);
   }

   /**
    * Get the facet grid color.
    */
   public Color getFacetGridColor() {
      return facetColor.get();
   }

   /**
    * Set the facet grid color.
    * @param color facet grid color
    */
   public void setFacetGridColor(Color color) {
      setFacetGridColor(color, true);
   }

   /**
    * Set the facet grid color.
    * @param color facet grid color
    * @param force if true always sets the USER value; otherwise only sets the USER value if
    *              it's different from the current value
    */
   public void setFacetGridColor(Color color, boolean force) {
      if(force || !Tool.equals(color, getFacetGridColor())) {
         if(color == null) {
            this.facetColor.resetValue(CompositeValue.Type.USER);
         }
         else {
            setFacetGridColor(color, CompositeValue.Type.USER);
         }
      }
   }

   /**
    * Set the facet grid color.
    * @param color facet grid color
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setFacetGridColor(Color color, CompositeValue.Type type) {
      this.facetColor.setValue(color, type);
   }

   /**
    * Check if facet grid is drawn.
    */
   public boolean isFacetGrid() {
      return facetGrid.get();
   }

   /**
    * Set if facet grid is drawn.
    * @param grid true if grid is drawn; false otherwise
    */
   public void setFacetGrid(boolean grid) {
      setFacetGrid(grid, true);
   }

   /**
    * Set if facet grid is drawn.
    * @param grid true if grid is drawn; false otherwise
    * @param force if true always sets the USER value; otherwise only sets the USER value if
    *              it's different from the current value
    */
   public void setFacetGrid(boolean grid, boolean force) {
      if(force || grid != isFacetGrid()) {
         setFacetGrid(grid, CompositeValue.Type.USER);
      }
   }

   /**
    * Set if facet grid is drawn.
    * @param grid true if grid is drawn; false otherwise
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setFacetGrid(boolean grid, CompositeValue.Type type) {
      this.facetGrid.setValue(grid, type);
   }

   /**
    * Check if it is exploded.
    */
   public boolean isExploded() {
      return exploded.get();
   }

   /**
    * Set whether it can be exploded.
    * @param exploded true if exploded; false otherwise
    */
   public void setExploded(boolean exploded) {
      setExploded(exploded, true);
   }

   /**
    * Set whether it can be exploded.
    * @param exploded true if exploded; false otherwise
    * @param force if true always sets the USER value; otherwise only sets the USER value if
    *              it's different from the current value
    */
   public void setExploded(boolean exploded, boolean force) {
      if(force || exploded != isExploded()) {
         setExploded(exploded, CompositeValue.Type.USER);
      }
   }

   /**
    * Set whether it can be exploded.
    * @param exploded true if exploded; false otherwise
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setExploded(boolean exploded, CompositeValue.Type type) {
      this.exploded.setValue(exploded, type);
   }

   /**
    * Check if all elements should be forced inside the plot area.
    */
   public boolean isInPlot() {
      return inPlot;
   }

   /**
    * Set if all elements should be forced inside the plot area.
    */
   public void setInPlot(boolean inPlot) {
      this.inPlot = inPlot;
   }

   /**
    * Check if the color should always be applied (fill) to polygon in maps.
    */
   public boolean isPolygonColor() {
      return polygonColor;
   }

   /**
    * Set whether the color should always be applied (fill) to polygon in maps.
    */
   public void setPolygonColor(boolean polygonColor) {
      this.polygonColor = polygonColor;
   }

   /**
    * Check if missing time series gap should be filled with value.
    */
   public boolean isFillTimeGap() {
      return fillGap;
   }

   /**
    * Set if missing time series gap should be filled with value.
    */
   public void setFillTimeGap(boolean fillGap) {
      this.fillGap = fillGap;
   }

   /**
    * Check if missing time series gap should be filled with zero or null.
    */
   public boolean isFillZero() {
      return fillZero;
   }

   /**
    * Set if missing time series gap should be filled with zero or null.
    */
   public void setFillZero(boolean fillZero) {
      this.fillZero = fillZero;
   }

   /**
    * Check if gaps in lines should be drawn as dashed line instead of empty space.
    */
   public boolean isFillGapWithDash() {
      return fillGapWithDash;
   }

   /**
    * Set if gaps in lines should be drawn as dashed line instead of empty space.
    */
   public void setFillGapWithDash(boolean fillGapWithDash) {
      this.fillGapWithDash = fillGapWithDash;
   }

   /**
    * Check if gaps in lines should be drawn as dashed line instead of empty space.
    */
   public double getWordCloudFontScale() {
      return wordCloudFontScale.get();
   }

   /**
    * Set the font scale for word cloud.
    * @param scale value from 0 to 1, or -1 to use the default.
    */
   public void setWordCloudFontScale(double scale) {
      setWordCloudFontScale(scale, true);
   }

   public void setWordCloudFontScale(double scale, boolean force) {
      if(force || scale != getWordCloudFontScale()) {
         setWordCloudFontScale(scale, CompositeValue.Type.USER);
      }
   }

   public void setWordCloudFontScale(double scale, CompositeValue.Type type) {
      this.wordCloudFontScale.setValue(scale, type);
   }

   /**
    * Get the alpha value.
    */
   public double getAlpha() {
      return alpha.get();
   }

   /**
    * Set the alpha value.
    * @param alpha value from 0 to 1, or -1 to use the default.
    */
   public void setAlpha(double alpha) {
      setAlpha(alpha, true);
   }

   /**
    * Set the alpha value.
    * @param alpha value from 0 to 1, or -1 to use the default.
    * @param force if true always sets the USER value; otherwise only sets the USER value if
    *              it's different from the current value
    */
   public void setAlpha(double alpha, boolean force) {
      if(force || alpha != getAlpha()) {
         setAlpha(alpha, CompositeValue.Type.USER);
      }
   }

   /**
    * Set the alpha value.
    * @param alpha value from 0 to 1, or -1 to use the default.
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setAlpha(double alpha, CompositeValue.Type type) {
      this.alpha.setValue(alpha, type);
   }

   /**
    * Check if the referenceLine is visible.
    */
   public boolean isReferenceLineVisible() {
      return rLineVisible;
   }

   /**
    * Set the visibility of the referenceLine.
    */
   public void setReferenceLineVisible(boolean visible) {
      this.rLineVisible = visible;
   }

   /**
    * Check if the values are visible.
    */
   public boolean isValuesVisible() {
      return valuesVisible;
   }

   /**
    * Set the visibility of the values.
    */
   public void setValuesVisible(boolean visible) {
      this.valuesVisible = visible;
   }

   /**
    * Check whether to show one value per stack or per data point.
    */
   public boolean isStackValue() {
      return stackValue;
   }

   /**
    * Set whether to show one value per stack or per data point.
    */
   public void setStackValue(boolean stack) {
      this.stackValue = stack;
   }

   /**
    * Check whether to stack all measures instead of display them as separate bars.
    */
   public boolean isStackMeasures() {
      return stackMeasures;
   }

   /**
    * Set whether to stack all measures instead of display them as separate bars.
    */
   public void setStackMeasures(boolean stack) {
      this.stackMeasures = stack;
   }

   /**
    * Check if the both points and lines are drawn for point/line chart.
    */
   public boolean isPointLine() {
      return pointsVisible;
   }

   /**
    * Set whether the both points and lines are drawn for point/line chart.
    */
   public void setPointLine(boolean visible) {
      this.pointsVisible = visible;
   }

   /**
    * Check if a single line should be rendered instead of broken into one line per color/shape.
    */
   public boolean isOneLine() {
      return oneLine;
   }

   /**
    * Set if a single line should be rendered instead of broken into one line per color/shape.
    */
   public void setOneLine(boolean oneLine) {
      this.oneLine = oneLine;
   }

   /**
    * Get the color of the trend line.
    */
   public Color getTrendLineColor() {
      return trendLineColor;
   }

   /**
    * Set the color of the trend line.
    */
   public void setTrendLineColor(Color trendLineColor) {
      this.trendLineColor = trendLineColor;
   }

   /**
    * Get the line style of the trend line.
    */
   public int getTrendLineStyle() {
      return trendLineStyle;
   }

   /**
    * Set the line style of the trend line.
    */
   public void setTrendLineStyle(int style) {
      this.trendLineStyle = style;
   }

   /**
    * Get the measure to exclude from trend lines.
    */
   public Set<String> getTrendLineExcludedMeasures() {
      return trendLineExcludedMeasures;
   }

   /**
    * Check if one trend line should be drawn per color if data is broken up
    * by color dimension.
    */
   public boolean isTrendPerColor() {
      return trendPerColor;
   }

   /**
    * Set if one trend line should be drawn per color if data is broken up
    * by color dimension.
    */
   public void setTrendPerColor(boolean perColor) {
      this.trendPerColor = perColor;
   }

   /**
    * Gets the x axis band color for plot banding.
    * @return Color of x axis band or null if not set.
    */
   public Color getXBandColor() {
      return xBandColor;
   }

   /**
    * Sets the x axis band color for plot banding.
    * @param color The color to set.  null clears the color.
    */
   public void setXBandColor(Color color) {
      this.xBandColor = color;
   }

   /**
    * Gets the y axis band color for plot banding.
    * @return Color of y axis band or null if not set.
    */
   public Color getYBandColor() {
      return yBandColor;
   }

   /**
    * Sets the y axis band color for plot banding.
    * @param color The color to set.  null clears the color.
    */
   public void setYBandColor(Color color) {
      this.yBandColor = color;
   }

   /**
    * Gets the x axis band size for plot banding.
    * @return Size of x axis band or 1 if not set.
    */
   public int getXBandSize() {
      return xBandSize;
   }

   /**
    * Sets the x axis band size for plot banding.
    * @param size The size to set.
    */
   public void setXBandSize(int size) {
      this.xBandSize = size;
   }

   /**
    * Gets the y axis band size for plot banding.
    * @return Size of y axis band or 1 if not set.
    */
   public int getYBandSize() {
      return yBandSize;
   }

   /**
    * Sets the y axis band size for plot banding.
    * @param size The size to set.
    */
   public void setYBandSize(int size) {
      this.yBandSize = size;
   }

   /**
    * Get the background color of the plot
    */
   public Color getBackground() {
      return bgColor.get();
   }

   /**
    * Set the background color of the plot
    * @param color bg color
    */
   public void setBackground(Color color) {
      setBackground(color, true);
   }

   /**
    * Set the background color of the plot
    * @param color bg color
    * @param force if true always sets the USER value; otherwise only sets the USER value if
    *              it's different from the current value
    */
   public void setBackground(Color color, boolean force) {
      if(force || !Tool.equals(color, getBackground())) {
         setBackground(color, CompositeValue.Type.USER);
      }
   }

   /**
    * Set the background color of the plot
    * @param color bg color
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setBackground(Color color, CompositeValue.Type type) {
      this.bgColor.setValue(color, type);
   }

   /**
    * Get the empty color of the plot
    */
   public Color getEmptyColor() {
      return emptyColor.get();
   }

   /**
    * Set the empty color of the plot
    * @param emptyColor empty color of the plot
    */
   public void setEmptyColor(Color emptyColor) {
      setEmptyColor(emptyColor, true);
   }

   /**
    * Set the empty color of the plot
    * @param emptyColor empty color of the plot
    * @param force if true always sets the USER value; otherwise only sets the USER value if
    *              it's different from the current value
    */
   public void setEmptyColor(Color emptyColor, boolean force) {
      if(force || !Tool.equals(emptyColor, getEmptyColor())) {
         setEmptyColor(emptyColor, CompositeValue.Type.USER);
      }
   }

   /**
    * Set the empty color of the plot
    * @param emptyColor empty color of the plot
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setEmptyColor(Color emptyColor, CompositeValue.Type type) {
      this.emptyColor.setValue(emptyColor, type);
   }

   /**
    * Get the border line color.
    */
   public Color getBorderColor() {
      return borderColor;
   }

   /**
    * Set the border line color, which is used when no axis exists on a chart.
    */
   public void setBorderColor(Color borderColor) {
      this.borderColor = borderColor;
   }

   /**
    * Get the pareto line color.
    */
   public Color getParetoLineColor() {
      return paretoLineColor.get();
   }

   /**
    * Set the pareto line color.
    */
   public void setParetoLineColor(Color paretoColor, CompositeValue.Type type) {
      this.paretoLineColor.setValue(paretoColor, type);
   }

   // for script
   public void setParetoLineColor(Color color) {
      setParetoLineColor(color, true);
   }

   public void setParetoLineColor(Color color, boolean force) {
      if(force || !Tool.equals(color, getParetoLineColor())) {
         if(color == null) {
            paretoLineColor.resetValue(CompositeValue.Type.USER);
         }
         else {
            setParetoLineColor(color, CompositeValue.Type.USER);
         }
      }
   }

   /**
    * Check whether to use web map as background.
    */
   public boolean isWebMap() {
      return webMap;
   }

   /**
    * Set whether to use web map as background.
    */
   public void setWebMap(boolean webMap) {
      this.webMap = webMap;
   }

   /**
    * Get the zoom factor of this map.
    */
   public double getZoom() {
      return zoom;
   }

   /**
    * Set the zoom factor of this map.
    */
   public void setZoom(double zoom) {
      this.zoom = zoom;
   }

   /**
    * Get the discrete zoom level for web map that requires discrete zoom level.
    */
   public int getZoomLevel() {
      return zoomLevel;
   }

   /**
    * Set the discrete zoom level for web map that requires discrete zoom level.
    */
   public void setZoomLevel(int zoomLevel) {
      this.zoomLevel = zoomLevel;
   }

   /**
    * Get the pan offset of x (lon).
    */
   public double getPanX() {
      return panX;
   }

   /**
    * Set the pan offset of x (lon).
    */
   public void setPanX(double panX) {
      this.panX = panX;
   }

   /**
    * Get the pan offset of y (lat).
    */
   public double getPanY() {
      return panY;
   }

   /**
    * Set the pan offset of y (lat).
    */
   public void setPanY(double panY) {
      this.panY = panY;
   }

   /**
    * Get the longitude/latitude range used as the basis for zoom/pan.
    */
   public Rectangle2D getLonLat() {
      return lonlat;
   }

   /**
    * Set the longitude/latitude range used as the basis for zoom/pan.
    */
   public void setLonLat(Rectangle2D lonlat) {
      this.lonlat = lonlat;
   }

   /**
    * Check if pan or zoom is defined.
    * @return
    */
   public boolean hasPanZoom() {
      return zoom != 1 || panX != 0 || panY != 0 || zoomLevel >= 0;
   }

   /**
    * Get the user selected map style.
    */
   public String getWebMapStyle() {
      return webMapStyle;
   }

   /**
    * Set the user selected map style. If style is not set on the chart, the global setting
    * is used as the default.
    */
   public void setWebMapStyle(String webMapStyle) {
      this.webMapStyle = webMapStyle;
   }

   /**
    * Get the web map style name (instead of id).
    */
   public String getWebMapStyleName() {
      try {
         if(SreeEnv.getProperty("webmap.service") != null) {
            MapboxService service = new MapboxService();
            List<MapboxStyle> styles = service.getStyles(false);

            return styles.stream()
               .filter(s -> Objects.equals(s.getId(), webMapStyle))
               .findFirst()
               .map(s -> s.getName())
               .orElse(null);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to get mapbox styles: " + ex);
      }

      return null;
   }

   /**
    * Set the web map style by name (instead of id).
    */
   public void setWebMapStyleName(String styleName) {
      try {
         MapboxService service = new MapboxService();
         List<MapboxStyle> styles = service.getStyles(false);

         webMapStyle = styles.stream()
            .filter(s -> Objects.equals(s.getName(), styleName))
            .findFirst()
            .map(s -> s.getId())
            // if no name found, assume styleName is id
            .orElse(styleName);
      }
      catch(Exception ex) {
         LOG.warn("Failed to get mapbox styles: " + ex);
      }
   }

   /**
    * Get the density contour levels.
    */
   public int getContourLevels() {
      return contourLevels;
   }

   /**
    * Set the density contour levels. This determines how many contours are used to represent
    * each density region.
    */
   public void setContourLevels(int contourLevels) {
      this.contourLevels = contourLevels;
   }

   /**
    * Get the kernel density bandwidth.
    */
   public int getContourBandwidth() {
      return contourBandwidth;
   }

   /**
    * Set the kernel density bandwidth. The higher the value, the more points are covered
    * by each contour.
    */
   public void setContourBandwidth(int contourBandwidth) {
      this.contourBandwidth = contourBandwidth;
   }

   /**
    * Get the alpha for the edge of contour to fade into.
    */
   public double getContourEdgeAlpha() {
      return contourEdgeAlpha;
   }

   /**
    * Set the alpha for the edge of contour to fade into.
    */
   public void setContourEdgeAlpha(double contourEdgeAlpha) {
      this.contourEdgeAlpha = contourEdgeAlpha;
   }

   /**
    * Get the cell size used in contour.
    */
   public int getContourCellSize() {
      return contourCellSize;
   }

   /**
    * Set the cell size used in contour. This controls to the size of contour cluster.
    */
   public void setContourCellSize(int contourCellSize) {
      this.contourCellSize = contourCellSize;
   }

   /**
    * Check if treemap parent labels should be displayed.
    */
   public boolean isIncludeParentLabels() {
      return includeParentLabels;
   }

   /**
    * Set if treemap parent labels should be displayed.
    */
   public void setIncludeParentLabels(boolean includeParentLabels) {
      this.includeParentLabels = includeParentLabels;
   }

   /**
    * Check if aesthetic binding should be applied to source node.
    */
   public boolean isApplyAestheticsToSource() {
      return applyAestheticsToSource;
   }

   /**
    * Set if aesthetic binding should be applied to source node.
    */
   public void setApplyAestheticsToSource(boolean applyAestheticsToSource) {
      this.applyAestheticsToSource = applyAestheticsToSource;
   }

   public double getPieRatio() {
      return pieRatio;
   }

   public void setPieRatio(double pieRatio) {
      this.pieRatio = pieRatio;
   }

   /**
    * Get the circle packing container formats.
    * @level the nesting level, with the top circle having a level of 0.
    */
   public CompositeTextFormat getCircleFormat(int level) {
      return circleFormats.computeIfAbsent(level, k -> {
         CompositeTextFormat fmt = new CompositeTextFormat();
         fmt.getCSSFormat().setCSSType(CSSConstants.CHART_CIRCLE_PACKING);
         Map<String, String> attrs = new HashMap<>();
         attrs.put("level", k + "");
         fmt.getCSSFormat().setCSSAttributes(attrs);
         return fmt;
      });
   }

   /**
    * Set the circle packing container formats. Only background color (and alpha)
    * is supported.
    */
   public void setCircleFormat(int level, CompositeTextFormat format) {
      circleFormats.put(level, format);
   }

   public CompositeTextFormat getErrorFormat() {
      return errorFormat;
   }

   /**
    * Parse xml.
    */
   @Override
   public void parseXML(Element node) throws Exception {
      parseAttributes(node);
      parseContents(node);
   }

   /**
    * Parse attributes.
    */
   private void parseAttributes(Element node) {
      String val;

      if((val = Tool.getAttribute(node, "trendline")) != null) {
         trendline = Integer.parseInt(val);
      }

      if((val = Tool.getAttribute(node, "trendLineColor")) != null) {
         trendLineColor = new Color(Integer.parseInt(val));
      }

      if((val = Tool.getAttribute(node, "trendLineStyle")) != null) {
         trendLineStyle = Integer.parseInt(val);
      }

      if((val = Tool.getAttribute(node, "trendPerColor")) != null) {
         trendPerColor = "true".equals(val);
      }

      if((val = Tool.getAttribute(node, "projectTrendLineForward")) != null) {
         projectTrendLineForward = Integer.parseInt(val);
      }

      if((val = Tool.getAttribute(node, "xGridColor")) != null) {
         xGridColor.parse(val);
      }

      if((val = Tool.getAttribute(node, "yGridColor")) != null) {
         yGridColor.parse(val);
      }

      if((val = Tool.getAttribute(node, "xGridStyle")) != null) {
         xGridStyle.parse(val);
      }

      if((val = Tool.getAttribute(node, "yGridStyle")) != null) {
         yGridStyle.parse(val);
      }

      if((val = Tool.getAttribute(node, "diagonalColor")) != null) {
         diagonalColor.parse(val);
      }

      if((val = Tool.getAttribute(node, "diagonalStyle")) != null) {
         diagonalStyle.parse(val);
      }

      if((val = Tool.getAttribute(node, "quadrantColor")) != null) {
         quadrantColor.parse(val);
      }

      if((val = Tool.getAttribute(node, "quadrantStyle")) != null) {
         quadrantStyle.parse(val);
      }

      if((val = Tool.getAttribute(node, "facetColor")) != null) {
         facetColor.parse(val);
      }

      if((val = Tool.getAttribute(node, "facetGrid")) != null) {
         facetGrid.parse(val);
      }

      if((val = Tool.getAttribute(node, "exploded")) != null) {
         exploded.parse(val);
      }

      if((val = Tool.getAttribute(node, "rLineVisible")) != null) {
         rLineVisible = "true".equals(val);
      }

      if((val = Tool.getAttribute(node, "valuesVisible")) != null) {
         valuesVisible = "true".equals(val);
      }

      if((val = Tool.getAttribute(node, "stackValue")) != null) {
         stackValue = "true".equals(val);
      }

      if((val = Tool.getAttribute(node, "stackMeasures")) != null) {
         stackMeasures = "true".equals(val);
      }

      if((val = Tool.getAttribute(node, "webMap")) != null) {
         webMap = "true".equals(val);
      }

      if((val = Tool.getAttribute(node, "pointsVisible")) != null) {
         pointsVisible = "true".equals(val);
      }

      if((val = Tool.getAttribute(node, "inPlot")) != null) {
         inPlot = "true".equals(val);
      }

      if((val = Tool.getAttribute(node, "polygonColor")) != null) {
         polygonColor = "true".equals(val);
      }

      if((val = Tool.getAttribute(node, "alpha")) != null) {
         alpha.parse(val);
      }

      if((val = Tool.getAttribute(node, "xBandColor")) != null) {
         this.xBandColor = new Color(Integer.valueOf(val));
      }

      if((val = Tool.getAttribute(node, "yBandColor")) != null) {
         this.yBandColor = new Color(Integer.valueOf(val));
      }

      if((val = Tool.getAttribute(node, "xBandSize")) != null) {
         this.xBandSize = Integer.valueOf(val);
      }

      if((val = Tool.getAttribute(node, "yBandSize")) != null) {
         this.yBandSize = Integer.valueOf(val);
      }

      if((val = Tool.getAttribute(node, "bgColor")) != null) {
         this.bgColor.parse(val);
      }

      if((val = Tool.getAttribute(node, "emptyColor")) != null) {
         this.emptyColor.parse(val);
      }

      if((val = Tool.getAttribute(node, "paretoLineColor")) != null) {
         this.paretoLineColor.parse(val);
      }

      if((val = Tool.getAttribute(node, "borderColor")) != null) {
         this.borderColor = !val.isEmpty() ? new Color(Integer.valueOf(val)) : null;
      }

      fillGap = "true".equals(Tool.getAttribute(node, "fillGap"));
      fillZero = "true".equals(Tool.getAttribute(node, "fillZero"));
      fillGapWithDash = "true".equals(Tool.getAttribute(node, "fillGapWithDash"));
      oneLine = "true".equals(Tool.getAttribute(node, "oneLine"));

      if((val = Tool.getAttribute(node, "zoom")) != null) {
         zoom = Double.parseDouble(val);
      }

      if((val = Tool.getAttribute(node, "zoomLevel")) != null) {
         zoomLevel = Integer.parseInt(val);
      }

      if((val = Tool.getAttribute(node, "panX")) != null) {
         panX = Double.parseDouble(val);
      }

      if((val = Tool.getAttribute(node, "panY")) != null) {
         panY = Double.parseDouble(val);
      }

      if((val = Tool.getAttribute(node, "contourLevels")) != null) {
         contourLevels = Integer.parseInt(val);
      }

      if((val = Tool.getAttribute(node, "contourBandwidth")) != null) {
         contourBandwidth = Integer.parseInt(val);
      }

      if((val = Tool.getAttribute(node, "contourCellSize")) != null) {
         contourCellSize = Integer.parseInt(val);
      }

      if((val = Tool.getAttribute(node, "contourEdgeAlpha")) != null) {
         contourEdgeAlpha = Double.parseDouble(val);
      }

      if((val = Tool.getAttribute(node, "pieRatio")) != null) {
         pieRatio = Double.parseDouble(val);
      }

      String lonMin = Tool.getAttribute(node, "lonMin");
      String lonMax = Tool.getAttribute(node, "lonMax");
      String latMin = Tool.getAttribute(node, "latMin");
      String latMax = Tool.getAttribute(node, "latMax");

      if(lonMin != null) {
         lonlat = new Rectangle2D.Double(Double.parseDouble(lonMin),
                                         Double.parseDouble(latMin),
                                         Double.parseDouble(lonMax) - Double.parseDouble(lonMin),
                                         Double.parseDouble(latMax) - Double.parseDouble(latMin));
      }

      webMapStyle = Tool.getAttribute(node, "webMapStyle");

      if((val = Tool.getAttribute(node, "includeParentLabels")) != null) {
         includeParentLabels = "true".equals(val);
      }

      includeParentLabels = "true".equals(Tool.getAttribute(node, "includeParentLabels"));
      applyAestheticsToSource = "true".equals(Tool.getAttribute(node, "applyAestheticsToSource"));

      if((val = Tool.getAttribute(node, "wordCloudFontScale")) != null) {
         wordCloudFontScale.parse(val);
      }
   }

   /**
    * Parse contents.
    */
   private void parseContents(Element node) throws Exception {
      Element elem = Tool.getChildNodeByTagName(node, "compositeTextFormat");

      if(elem != null) {
         fmt.parseXML(elem);
      }

      elem = Tool.getChildNodeByTagName(node, "trendLineExcludedMeasures");

      if(elem != null) {
         NodeList anodes = Tool.getChildNodesByTagName(elem, "measure");

         for(int i = 0; i < anodes.getLength(); i++) {
            Element anode = (Element) anodes.item(i);
            trendLineExcludedMeasures.add(Tool.getValue(anode));
         }
      }

      NodeList nodes = Tool.getChildNodesByTagName(node, "circleFormat");

      for(int i = 0; i < nodes.getLength(); i++) {
         Element node0 = (Element) nodes.item(i);
         int level = Integer.parseInt(node0.getAttribute("level"));
         CompositeTextFormat format = new CompositeTextFormat();
         Element child = Tool.getChildNodeByTagName(node0, "compositeTextFormat");
         format.parseXML(child);
         circleFormats.put(level, format);
      }
   }

   /**
    * Write xml.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<plotDescriptor ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.println("</plotDescriptor>");
   }

   /**
    * Write attributes.
    */
   private void writeAttributes(PrintWriter writer) {
      if(trendLineColor != null) {
         writer.print(" trendLineColor=\"" + trendLineColor.getRGB() + "\" ");
      }

      writer.print(" xGridColor=\"" + xGridColor + "\" ");
      writer.print(" yGridColor=\"" + yGridColor + "\" ");
      writer.print(" diagonalColor=\"" + diagonalColor + "\" ");
      writer.print(" quadrantColor=\"" + quadrantColor + "\" ");
      writer.print(" facetColor=\"" + facetColor + "\" ");

      if(this.xBandColor != null) {
         writer.print(" xBandColor=\"" + xBandColor.getRGB() + "\" ");
      }

      if(this.yBandColor != null) {
         writer.print(" yBandColor=\"" + yBandColor.getRGB() + "\" ");
      }

      writer.print(" bgColor=\"" + bgColor + "\" ");
      writer.print(" emptyColor=\"" + emptyColor + "\" ");
      writer.print(" paretoLineColor=\"" + paretoLineColor + "\" ");

      if(this.borderColor != null) {
         writer.print(" borderColor=\"" + borderColor.getRGB() + "\" ");
      }
      else {
         writer.print(" borderColor=\"\" ");
      }

      writer.print(" trendline=\"" + trendline + "\" ");
      writer.print(" trendLineStyle=\"" + trendLineStyle + "\" ");
      writer.print(" trendPerColor=\"" + trendPerColor + "\" ");
      // @by: ChrisSpagnoli feature1379102629417 2015-1-5
      writer.print(" projectTrendLineForward=\"" + projectTrendLineForward + "\" ");
      writer.print(" xGridStyle=\"" + xGridStyle + "\" ");
      writer.print(" yGridStyle=\"" + yGridStyle + "\" ");
      writer.print(" diagonalStyle=\"" + diagonalStyle + "\" ");
      writer.print(" quadrantStyle=\"" + quadrantStyle + "\" ");
      writer.print(" facetGrid=\"" + facetGrid + "\" ");
      writer.print(" exploded=\"" + exploded + "\" ");
      writer.print(" inPlot=\"" + inPlot + "\" ");
      writer.print(" polygonColor=\"" + polygonColor + "\" ");
      writer.print(" alpha=\"" + alpha + "\" ");
      writer.print(" rLineVisible=\"" + rLineVisible + "\" ");
      writer.print(" valuesVisible=\"" + valuesVisible + "\" ");
      writer.print(" stackValue=\"" + stackValue + "\" ");
      writer.print(" stackMeasures=\"" + stackMeasures + "\" ");
      writer.print(" webMap=\"" + webMap + "\" ");
      writer.print(" pointsVisible=\"" + pointsVisible + "\" ");
      writer.print(" fillGap=\"" + fillGap + "\" ");
      writer.print(" fillZero=\"" + fillZero + "\" ");
      writer.print(" fillGapWithDash=\"" + fillGapWithDash + "\" ");
      writer.print(" xBandSize=\"" + xBandSize + "\" ");
      writer.print(" yBandSize=\"" + yBandSize + "\" ");
      writer.print(" zoom=\"" + zoom + "\" ");
      writer.print(" zoomLevel=\"" + zoomLevel + "\" ");
      writer.print(" panX=\"" + panX + "\" ");
      writer.print(" panY=\"" + panY + "\" ");
      writer.print(" contourLevels=\"" + contourLevels + "\" ");
      writer.print(" contourBandwidth=\"" + contourBandwidth + "\" ");
      writer.print(" contourEdgeAlpha=\"" + contourEdgeAlpha + "\" ");
      writer.print(" contourCellSize=\"" + contourCellSize + "\" ");
      writer.print(" includeParentLabels=\"" + includeParentLabels + "\" ");
      writer.print(" applyAestheticsToSource=\"" + applyAestheticsToSource + "\" ");
      writer.print(" wordCloudFontScale=\"" + wordCloudFontScale + "\" ");
      writer.print(" pieRatio=\"" + pieRatio + "\" ");
      writer.print(" oneLine=\"" + oneLine + "\" ");

      if(lonlat != null) {
         writer.print(" lonMin=\"" + lonlat.getMinX() + "\"");
         writer.print(" lonMax=\"" + lonlat.getMaxX() + "\"");
         writer.print(" latMin=\"" + lonlat.getMinY() + "\"");
         writer.print(" latMax=\"" + lonlat.getMaxY() + "\"");
      }

      if(webMapStyle != null) {
         writer.print(" webMapStyle=\"" + webMapStyle + "\" ");
      }
   }

   /**
    * Write contents.
    */
   private void writeContents(PrintWriter writer) {
      if(fmt != null) {
         fmt.writeXML(writer);
      }

      writer.println("<trendLineExcludedMeasures>");
      trendLineExcludedMeasures.forEach(
         m -> writer.println("<measure><![CDATA[" + m + "]]></measure>"));
      writer.println("</trendLineExcludedMeasures>");

      for(Integer level : circleFormats.keySet()) {
         CompositeTextFormat format = circleFormats.get(level);

         writer.println("<circleFormat level=\"" + level + "\">");
         format.writeXML(writer);
         writer.println("</circleFormat>");
      }
   }

   /**
    * Clone this object.
    */
   @Override
   public Object clone() {
      try {
         PlotDescriptor obj = (PlotDescriptor) super.clone();

         if(fmt != null) {
            obj.fmt = (CompositeTextFormat) fmt.clone();
         }

         obj.xGridColor = xGridColor.clone();
         obj.xGridStyle = xGridStyle.clone();
         obj.yGridColor = yGridColor.clone();
         obj.yGridStyle = yGridStyle.clone();
         obj.diagonalColor = diagonalColor.clone();
         obj.diagonalStyle = diagonalStyle.clone();
         obj.quadrantColor = quadrantColor.clone();
         obj.quadrantStyle = quadrantStyle.clone();
         obj.facetColor = facetColor.clone();
         obj.facetGrid = facetGrid.clone();
         obj.exploded = exploded.clone();
         obj.alpha = alpha.clone();
         obj.wordCloudFontScale = wordCloudFontScale.clone();
         obj.bgColor = bgColor.clone();
         obj.emptyColor = emptyColor.clone();
         obj.paretoLineColor = paretoLineColor.clone();
         obj.trendLineExcludedMeasures = new HashSet<>(trendLineExcludedMeasures);
         obj.circleFormats = Tool.deepCloneMap(circleFormats);
         obj.errorFormat = obj.errorFormat.clone();

         return obj;
      }
      catch(Exception e) {
         LOG.error("Failed to clone PlotDescriptor", e);
         return null;
      }
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Equals by content.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof PlotDescriptor)) {
         return false;
      }

      PlotDescriptor desc = (PlotDescriptor) obj;

      return Tool.equals(fmt, desc.fmt) &&
         Tool.equals(xGridColor, desc.xGridColor) &&
         Tool.equals(yGridColor, desc.yGridColor) &&
         Tool.equals(diagonalColor, desc.diagonalColor) &&
         Tool.equals(quadrantColor, desc.quadrantColor) &&
         Tool.equals(facetColor, desc.facetColor) &&
         Tool.equals(trendLineColor, desc.trendLineColor) &&
         Tool.equals(xBandColor, desc.xBandColor) &&
         Tool.equals(yBandColor, desc.yBandColor) &&
         Tool.equals(bgColor, desc.bgColor) &&
         Tool.equals(emptyColor, desc.emptyColor) &&
         Tool.equals(paretoLineColor, desc.paretoLineColor) &&
         Tool.equals(borderColor, desc.borderColor) &&
         Tool.equals(trendLineExcludedMeasures, desc.trendLineExcludedMeasures) &&
         Tool.equals(xGridStyle, desc.xGridStyle) &&
         Tool.equals(yGridStyle, desc.yGridStyle) &&
         Tool.equals(diagonalStyle, desc.diagonalStyle) &&
         Tool.equals(quadrantStyle, desc.quadrantStyle) &&
         Tool.equals(facetGrid, desc.facetGrid) &&
         trendline == desc.trendline &&
         trendLineStyle == desc.trendLineStyle &&
         trendPerColor == desc.trendPerColor &&
         // @by: ChrisSpagnoli feature1379102629417 2015-1-5
         projectTrendLineForward == desc.projectTrendLineForward &&
         Tool.equals(exploded, desc.exploded) &&
         inPlot == desc.inPlot &&
         fillGap == desc.fillGap &&
         fillZero == desc.fillZero &&
         fillGapWithDash == desc.fillGapWithDash &&
         Tool.equals(alpha, desc.alpha) &&
         rLineVisible == desc.rLineVisible &&
         valuesVisible == desc.valuesVisible &&
         stackValue == desc.stackValue &&
         stackMeasures == desc.stackMeasures &&
         webMap == desc.webMap &&
         pointsVisible == desc.pointsVisible &&
         polygonColor == desc.polygonColor &&
         xBandSize == desc.xBandSize &&
         yBandSize == desc.yBandSize &&
         zoom == desc.zoom &&
         zoomLevel == desc.zoomLevel &&
         panX == desc.panX &&
         panY == desc.panY &&
         contourLevels == desc.contourLevels &&
         contourBandwidth == desc.contourBandwidth &&
         contourCellSize == desc.contourCellSize &&
         contourEdgeAlpha == desc.contourEdgeAlpha &&
         Tool.equals(webMapStyle, desc.webMapStyle) &&
         includeParentLabels == desc.includeParentLabels &&
         applyAestheticsToSource == desc.applyAestheticsToSource &&
         wordCloudFontScale == desc.wordCloudFontScale &&
         pieRatio == desc.pieRatio &&
         circleFormats.equals(desc.circleFormats) &&
         Tool.equals(errorFormat, desc.errorFormat) &&
         includeParentLabels == desc.includeParentLabels &&
         oneLine == desc.oneLine;
   }

   public void resetCompositeValues(CompositeValue.Type type) {
      xGridColor.resetValue(type);
      xGridStyle.resetValue(type);
      yGridColor.resetValue(type);
      yGridStyle.resetValue(type);
      diagonalColor.resetValue(type);
      diagonalStyle.resetValue(type);
      quadrantColor.resetValue(type);
      quadrantStyle.resetValue(type);
      facetColor.resetValue(type);
      facetGrid.resetValue(type);
      exploded.resetValue(type);
      alpha.resetValue(type);
      bgColor.resetValue(type);
      emptyColor.resetValue(type);
      paretoLineColor.resetValue(type);
      wordCloudFontScale.resetValue(type);
   }

   private CompositeTextFormat fmt;
   private int trendline = StyleConstants.NONE;
   private Color trendLineColor = GDefaults.DEFAULT_TARGET_LINE_COLOR;
   private int trendLineStyle = StyleConstants.THIN_LINE;
   // @by: ChrisSpagnoli feature1379102629417 2015-1-5
   private int projectTrendLineForward = 0;
   private boolean trendPerColor = true;
   private Set<String> trendLineExcludedMeasures = new HashSet<>();
   private CompositeValue<Color> xGridColor = new CompositeValue<>(
      Color.class, ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, "x"));
   private CompositeValue<Color> yGridColor = new CompositeValue<>(
      Color.class, ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, "y"));
   private CompositeValue<Integer> xGridStyle =
      new CompositeValue<>(Integer.class, StyleConstants.NONE);
   private CompositeValue<Integer> yGridStyle =
      new CompositeValue<>(Integer.class, StyleConstants.THIN_LINE);
   private CompositeValue<Color> diagonalColor = new CompositeValue<>(Color.class,
      ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, "diagonal"));
   private CompositeValue<Integer> diagonalStyle =
      new CompositeValue<>(Integer.class, StyleConstants.NONE);
   private CompositeValue<Color> quadrantColor = new CompositeValue<>(
      Color.class, ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, "quadrant"));
   private CompositeValue<Integer> quadrantStyle =
      new CompositeValue<>(Integer.class, StyleConstants.NONE);
   private CompositeValue<Color> facetColor =
      new CompositeValue<>(Color.class, GDefaults.DEFAULT_LINE_COLOR);
   private CompositeValue<Boolean> facetGrid = new CompositeValue<>(Boolean.class, false);
   private Color xBandColor = null;
   private Color yBandColor = null;
   private CompositeValue<Boolean> exploded = new CompositeValue<>(Boolean.class, false);
   private boolean rLineVisible = false;
   private boolean valuesVisible = false;
   private boolean pointsVisible = false;
   private boolean stackValue = false;
   private boolean inPlot = true;
   private boolean polygonColor = false;
   private CompositeValue<Double> alpha = new CompositeValue<>(Double.class, -1d); // -1 is auto
   private CompositeValue<Double> wordCloudFontScale = new CompositeValue<>(Double.class, 1.2);
   private boolean fillGap = false;
   private boolean fillZero = false;
   private int xBandSize = 1;
   private int yBandSize = 1;
   private CompositeValue<Color> bgColor = new CompositeValue<>(Color.class, null);
   private boolean stackMeasures = false;
   private CompositeValue<Color> emptyColor = new CompositeValue<>(Color.class, null);
   private boolean webMap = "true".equals(SreeEnv.getProperty("webmap.default"));
   private String webMapStyle;
   private double zoom = 1;
   private int zoomLevel = -1;
   private double panX, panY;
   private Rectangle2D lonlat;
   private int contourLevels = 10;
   private int contourBandwidth = 20;
   private double contourEdgeAlpha = 0.2;
   private int contourCellSize = 5;
   private boolean includeParentLabels = false;
   private Color borderColor = GDefaults.DEFAULT_LINE_COLOR;
   private CompositeValue<Color> paretoLineColor =
      new CompositeValue<>(Color.class, CategoricalColorFrame.COLOR_PALETTE[0]);
   private boolean applyAestheticsToSource = false;
   private Map<Integer, CompositeTextFormat> circleFormats = new HashMap<>();
   private boolean fillGapWithDash = true;
   private CompositeTextFormat errorFormat;
   private double pieRatio = 0;
   private boolean oneLine = false;

   private static final Logger LOG = LoggerFactory.getLogger(PlotDescriptor.class);
}
