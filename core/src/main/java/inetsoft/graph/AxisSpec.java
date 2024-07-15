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
package inetsoft.graph;

import com.inetsoft.build.tern.*;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.internal.GDefaults;

import java.awt.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * This class holds the attributes used for creating an axis for a scale.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=AreaElement")
public class AxisSpec implements Cloneable, Serializable {
   /**
    * No axis on this coord.
    */
   @TernField
   public static final int AXIS_NONE = 0;
   /**
    * Use a single axis for each scale.
    */
   @TernField
   public static final int AXIS_SINGLE = 0x1;
   /**
    * Use the secondary axis. For rectangular coordinate, the bottom and left are
    * considered as primary, and top/right are secondary. For facet, the top
    * is treated as primary and bottom is treated as secondary.
    */
   @TernField
   public static final int AXIS_SINGLE2 = 0x2 | 0x10;
   /**
    * Create two axes for each scale, with one opposite the default axis.
    */
   @TernField
   public static final int AXIS_DOUBLE = 0x3;
   /**
    * Create two axes and display the label on the secondary axis.
    */
   @TernField
   public static final int AXIS_DOUBLE2 = AXIS_DOUBLE | 0x10;
   /**
    * Place the axis at the zero position of the cross axis.
    */
   @TernField
   public static final int AXIS_CROSS = 0x101;

   /**
    * Create a new AxisSpec.
    */
   public AxisSpec() {
      textSpec.setFont(GDefaults.DEFAULT_TEXT_FONT);
   }

   /**
    * Set the axis style.
    * @param style one of the following, AXIS_NONE, AXIS_DOUBLE, AXIS_SINGLE.
    */
   @TernMethod
   public void setAxisStyle(int style) {
      this.style = style;
   }

   /**
    * Get the axis style for this coord.
    */
   @TernMethod
   public int getAxisStyle() {
      return style;
   }

   /**
    * Get the axis line color.
    */
   @TernMethod
   public Color getLineColor() {
      return lineColor;
   }

   /**
    * Set the axis line color property.
    */
   @TernMethod
   public void setLineColor(Color lineColor) {
      this.lineColor = lineColor;
   }

   /**
    * Get the secondary axis line color.
    */
   @TernMethod
   public Color getLine2Color() {
      return line2Color;
   }

   /**
    * Set the secondary axis line color.
    */
   @TernMethod
   public void setLine2Color(Color line2Color) {
      this.line2Color = line2Color;
   }

   /**
    * Get axis label text formatting attributes.
    */
   @TernMethod
   public TextSpec getTextSpec() {
      return textSpec;
   }

   /**
    * Set axis label text formatting attributes.
    */
   @TernMethod
   public void setTextSpec(TextSpec textSpec) {
      this.textSpec = (textSpec == null) ? new TextSpec() : textSpec;
   }

   /**
    * Set a text frame to map scale values to axis labels.
    */
   @TernMethod
   public void setTextFrame(TextFrame textFrame) {
      this.textFrame = textFrame;
   }

   /**
    * Get a text frame for mapping scale values to axis label.
    */
   @TernMethod
   public TextFrame getTextFrame() {
      return textFrame;
   }

   /**
    * Set a text frame to map scale values to label fonts.
    */
   @TernMethod
   public void setFontFrame(FontFrame fontFrame) {
      this.fontFrame = fontFrame;
   }

   /**
    * Get a font frame for mapping scale values to label fonts.
    */
   @TernMethod
   public FontFrame getFontFrame() {
      return fontFrame;
   }

   /**
    * Set a text frame to map scale values to label colors.
    */
   @TernMethod
   public void setColorFrame(ColorFrame colorFrame) {
      this.colorFrame = colorFrame;
   }

   /**
    * Get a color frame for mapping scale values to label colors.
    */
   @TernMethod
   public ColorFrame getColorFrame() {
      return colorFrame;
   }

   /**
    * Get the color for axis label.
    */
   @TernMethod
   public Color getColor(Object value) {
      Color color = null;

      if(colorFrame != null) {
         color = colorFrame.getColor(value);
      }

      if(color == null && textSpec != null) {
         color = textSpec.getColor();
      }

      return color;
   }

   /**
    * Get the font for axis label.
    */
   @TernMethod
   public Font getFont(Object value) {
      Font font = null;

      if(fontFrame != null) {
         font = fontFrame.getFont(value);
      }

      if(font == null && textSpec != null) {
         font = textSpec.getFont();
      }

      return font;
   }

   /**
    * Check whether label should be abbreviated.
    */
   @TernMethod
   public boolean isAbbreviate() {
      return abbreviate;
   }

   /**
    * Set label abbreviation. If set to true, the common prefix of
    * date labels, or common prefix/suffix of numeric labels (e.g. %, $)
    * is dropped when appropriate.
    */
   @TernMethod
   public void setAbbreviate(boolean abbr) {
      this.abbreviate = abbr;
   }

   /**
    * Set whether to include ticks whose labels are ignored
    * by abbreviation.
    */
   @TernMethod
   public void setAllTicks(boolean allTick) {
      this.allTick = allTick;
   }

   /**
    * Check whether to include ticks whose labels are ignored
    * by abbreviation.
    */
   @TernMethod
   public boolean isAllTicks() {
      return allTick;
   }

   /**
    * Check whether label could be truncated with "..".
    */
   @TernMethod
   public boolean isTruncate() {
      return truncate;
   }

   /**
    * Set whethert label could be truncated. If set to true, the label will be
    * truncated with ".." when there is no splace to dislay it.
    */
   @TernMethod
   public void setTruncate(boolean truncated) {
      this.truncate = truncated;
   }

   /**
    * Get the axis label visibility.
    */
   @TernMethod
   public boolean isLabelVisible() {
      return labelVisible && isAxisVisible();
   }

   /**
    * Set the axis label visibility.
    */
   @TernMethod
   public void setLabelVisible(boolean labelVisible) {
      this.labelVisible = labelVisible;
   }

   /**
    * Get the axis line visibility.
    */
   @TernMethod
   public boolean isLineVisible() {
      return lineVisible && isAxisVisible();
   }

   /**
    * Set the axis line visibility.
    */
   @TernMethod
   public void setLineVisible(boolean lineVisible) {
      this.lineVisible = lineVisible;
   }

   /**
    * Get the axis ticks visibility.
    */
   @TernMethod
   public boolean isTickVisible() {
      return tickVisible && isAxisVisible();
   }

   /**
    * Set the axis ticks visibility.
    */
   @TernMethod
   public void setTickVisible(boolean tickVisible) {
      this.tickVisible = tickVisible;
   }

   /**
    * Check if axis is visible, if axis style is none, it implies the axis is
    * invisible.
    */
   @TernMethod
   private boolean isAxisVisible() {
      return getAxisStyle() != AXIS_NONE;
   }

   /**
    * Get the grid line color.
    */
   @TernMethod
   public Color getGridColor() {
      return gridColor;
   }

   /**
    * Set the grid line color.
    */
   @TernMethod
   public void setGridColor(Color gridColor) {
      this.gridColor = gridColor;
   }

   /**
    * Get the grid line style.
    */
   @TernMethod
   public int getGridStyle() {
      return gridStyle;
   }

   /**
    * Set the grid line style, e.g., GraphConstants.DOT_LINE.
    */
   @TernMethod
   public void setGridStyle(int gridStyle) {
      this.gridStyle = gridStyle;
   }

   /**
    * Check if grid lines are treated as shapes or positions.
    */
   @TernMethod
   public boolean isGridAsShape() {
      return gridAsShape;
   }

   /**
    * Set whether grid lines are treated as shapes or positions. If grid lines are
    * treated as shapes, they may be transformed into curves. Otherwise, they
    * remain as straight lines with only the end points transformed.
    */
   @TernMethod
   public void setGridAsShape(boolean asShape) {
      this.gridAsShape = asShape;
   }

   /**
    * Check whether grid lines should be drawn on top of visual objects.
    */
   @TernMethod
   public boolean isGridOnTop() {
      return gridOnTop;
   }

   /**
    * Set whether grid lines should be drawn on top of visual objects.
    */
   @TernMethod
   public void setGridOnTop(boolean top) {
       this.gridOnTop = top;
   }

   /**
    * Check if grid lines should be drawn between ticks.
    */
   @TernMethod
   public boolean isGridBetween() {
      return gridBetween;
   }

   /**
    * Set if grid lines should be drawn between ticks.
    */
   @TernMethod
   public void setGridBetween(boolean between) {
      this.gridBetween = between;
   }

   /**
    * Check if the grid should extend across labels (as a facet table).
    */
   @TernMethod
   public boolean isFacetGrid() {
      return facetGrid;
   }

   /**
    * Set if the grid should extend across labels (as a facet table).
    */
   @TernMethod
   public void setFacetGrid(boolean facetGrid) {
      this.facetGrid = facetGrid;
   }

   /**
    * Set whether space should be reserved for the max label so it doesn't
    * need to be moved to be fit inside the plot.
    */
   @TernMethod
   public void setInPlot(boolean inside) {
      this.inPlot = inside;
   }

   /**
    * Check if space should be reserved for the max label.
    */
   @TernMethod
   public boolean isInPlot() {
      return inPlot;
   }

   /**
    * Check if to keep only one line or all lines in case of truncating multi-line text.
    */
   @TernMethod
   public boolean isLastOrAll() {
      return lastOrAll;
   }

   /**
    * Set in the case a line is truncated from a multi-line text, whether to keep only
    * the last line or as many as can fit.
    */
   @TernMethod
   public void setLastOrAll(boolean lastOrAll) {
      this.lastOrAll = lastOrAll;
   }

   /**
    * Set the fixed size of the axis. It's the width and height for the
    * vertical and horizontal axis respectively.
    */
   @TernMethod
   public void setAxisSize(double size) {
      this.asize = size;
   }

   /**
    * Get the fixed size of the axis.
    * @return the fixed width/height of the axis, or 0 to calculate
    * the size from axis content.
    */
   @TernMethod
   public double getAxisSize() {
      return asize;
   }

   /**
    * Set the extra spacing between label and axis line.
    */
   @TernMethod
   public void setLabelGap(int gap) {
      this.labelGap = gap;
   }

   /**
    * Get the spacing between label and axis line.
    */
   @TernMethod
   public int getLabelGap() {
      return labelGap;
   }

   /**
    * Get the padding space for the min label.
    */
   @TernMethod
   public int getMinPadding() {
      return minPadding;
   }

   /**
    * Set the padding space for the min label.
    */
   @TernMethod
   public void setMinPadding(int minPadding) {
      this.minPadding = minPadding;
   }

   /**
    * Get the padding space for the max label.
    */
   @TernMethod
   public int getMaxPadding() {
      return maxPadding;
   }

   /**
    * Set the padding space for the max label.
    */
   @TernMethod
   public void setMaxPadding(int maxPadding) {
      this.maxPadding = maxPadding;
   }

   /**
    * Get the space reserved between labels. Labels with less than the label spacing is
    * considered to be overlapped and may be dropped.
    */
   @TernMethod
   public int getMaxLabelSpacing() {
      return maxLabelSpacing;
   }

   /**
    * Set the space reserved between labels. This is used as the maximum spacing
    * when calculating the label spacing based on available space and label distribution.
    */
   @TernMethod
   public void setMaxLabelSpacing(int maxLabelSpacing) {
      this.maxLabelSpacing = maxLabelSpacing;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;
      AxisSpec axisSpec = (AxisSpec) o;
      return style == axisSpec.style && tickVisible == axisSpec.tickVisible &&
         labelVisible == axisSpec.labelVisible && lineVisible == axisSpec.lineVisible &&
         labelGap == axisSpec.labelGap && abbreviate == axisSpec.abbreviate &&
         allTick == axisSpec.allTick && truncate == axisSpec.truncate &&
         inPlot == axisSpec.inPlot && lastOrAll == axisSpec.lastOrAll &&
         gridStyle == axisSpec.gridStyle && gridAsShape == axisSpec.gridAsShape &&
         gridOnTop == axisSpec.gridOnTop && gridBetween == axisSpec.gridBetween &&
         facetGrid == axisSpec.facetGrid &&
         Double.compare(asize, axisSpec.asize) == 0 && minPadding == axisSpec.minPadding &&
         maxPadding == axisSpec.maxPadding && maxLabelSpacing == axisSpec.maxLabelSpacing &&
         Objects.equals(lineColor, axisSpec.lineColor) &&
         Objects.equals(line2Color, axisSpec.line2Color) &&
         Objects.equals(textFrame, axisSpec.textFrame) &&
         Objects.equals(colorFrame, axisSpec.colorFrame) &&
         Objects.equals(fontFrame, axisSpec.fontFrame) &&
         Objects.equals(textSpec, axisSpec.textSpec) &&
         Objects.equals(gridColor, axisSpec.gridColor);
   }

   @Override
   public int hashCode() {
      return Objects.hash(style, tickVisible, labelVisible, lineVisible, lineColor, line2Color,
                          labelGap, textFrame, colorFrame, fontFrame, textSpec, abbreviate,
                          allTick, truncate, inPlot, lastOrAll, gridColor, gridStyle, gridAsShape,
                          gridOnTop, gridBetween, facetGrid, asize,
                          minPadding, maxPadding, maxLabelSpacing);
   }

   /**
    * Make a copy of the spec.
    */
   @Override
   public AxisSpec clone() {
      try {
         AxisSpec obj = (AxisSpec) super.clone();

         if(textFrame != null) {
            obj.textFrame = (TextFrame) textFrame.clone();
         }

         if(colorFrame != null) {
            obj.colorFrame = (ColorFrame) colorFrame.clone();
         }

         if(fontFrame != null) {
            obj.fontFrame = (FontFrame) fontFrame.clone();
         }

         if(textSpec != null) {
            obj.textSpec = textSpec.clone();
         }

         return obj;
      }
      catch(Exception ex) {
         // impossible
      }

      return null;
   }

   private int style = AXIS_DOUBLE;

   private boolean tickVisible = true;
   private boolean labelVisible = true;
   private boolean lineVisible = true;
   private Color lineColor = GDefaults.DEFAULT_LINE_COLOR;
   private Color line2Color;
   private int labelGap = 0;

   private TextFrame textFrame;
   private ColorFrame colorFrame;
   private FontFrame fontFrame;
   private TextSpec textSpec = new TextSpec();
   private boolean abbreviate = false;
   private boolean allTick = false; // ignore abbreviated (ignored) ticks
   private boolean truncate = true;
   private boolean inPlot = false;
   private boolean lastOrAll = false;

   private Color gridColor = GDefaults.DEFAULT_GRIDLINE_COLOR;
   private int gridStyle = GraphConstants.NONE;
   private boolean gridAsShape = true;
   private boolean gridOnTop = false;
   private boolean gridBetween = false;
   private boolean facetGrid = false;
   private double asize = 0; // fixed size of the axis (width or height)
   private int minPadding = 0;
   private int maxPadding = 0;
   private int maxLabelSpacing = 2;

   private static final long serialVersionUID = 1L;
}
