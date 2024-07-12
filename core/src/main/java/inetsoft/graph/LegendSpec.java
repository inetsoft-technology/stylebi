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
package inetsoft.graph;

import com.inetsoft.build.tern.TernClass;
import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.aesthetic.TextFrame;
import inetsoft.graph.internal.GDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Dimension2D;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.*;

/**
 * This class contains legend formatting attributes.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=LegendSpec")
public class LegendSpec implements Cloneable, Serializable {
   /**
    * Create a legend specification.
    */
   public LegendSpec() {
      textSpec.setFont(GDefaults.DEFAULT_TEXT_FONT);
   }

   /**
    * Get the legend visibility.
    */
   @TernMethod
   public boolean isVisible() {
      return visible;
   }

   /**
    * Set the legend visibility. A legend is displayed on a graph if the visible
    * flag is true and there are more than one value for that legend.
    */
   @TernMethod
   public void setVisible(boolean visible) {
      this.visible = visible;
   }

   /**
    * Get the title to show on the legend.
    */
   @TernMethod
   public String getTitle() {
      return title;
   }

   /**
    * Set the legend title.
    */
   @TernMethod
   public void setTitle(String title) {
      this.title = title;
   }

   /**
    * Get the preferred size of the legend.
    */
   @TernMethod
   public Dimension2D getPreferredSize() {
      return preferredSize;
   }

   /**
    * Set the preferred size for the legend.
    */
   @TernMethod
   public void setPreferredSize(Dimension2D preferredSize) {
      this.preferredSize = preferredSize;
   }

   /**
    * Get the position of the legend.
    */
   @TernMethod
   public Point2D getPosition() {
      return position;
   }

   /**
    * Set the lower-left corner position for the legend.
    * @param position the point location in graph. If the value is between 0 and
    * 1 (non-inclusive), it's treated as a proportion of the width/height. If the
    * value is negative, it's the distance from the right/top of the graph.
    */
   @TernMethod
   public void setPosition(Point2D position) {
      this.position = position;
   }

   /**
    * Get the position relative to the plot area.
    */
   @TernMethod
   public Point2D getPlotPosition() {
      return epos;
   }

   /**
    * Set the position relative to the plot area. If this position is
    * specified, it overrides the regular position. Unlike setPosition(), the plot
    * position specifies the position for the top of the legend instead of bottom.
    */
   @TernMethod
   public void setPlotPosition(Point2D epos) {
      this.epos = epos;
   }

   /**
    * Get the text label attributes.
    */
   @TernMethod
   public TextSpec getTextSpec() {
      return textSpec;
   }

   /**
    * Set the text label attributes.
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
    * Get the border line style of the legends.
    */
   @TernMethod
   public int getBorder() {
      return border;
   }

   /**
    * Set the border line style for the legends, e.g. GraphConstants.THIN_LINE.
    */
   @TernMethod
   public void setBorder(int border) {
      this.border = border;
   }

   /**
    * Get the legend border color.
    */
   @TernMethod
   public Color getBorderColor() {
      return borderColor;
   }

   /**
    * Set the legend border color.
    */
   @TernMethod
   public void setBorderColor(Color borderColor) {
      this.borderColor = borderColor;
   }

   /**
    * Get the legend title text attributes.
    */
   @TernMethod
   public TextSpec getTitleTextSpec() {
      return titleSpec;
   }

   /**
    * Set the legend title text attributes.
    */
   @TernMethod
   public void setTitleTextSpec(TextSpec textSpec) {
      this.titleSpec = textSpec;
   }

   /**
    * Check if the legend title should be displayed.
    */
   @TernMethod
   public boolean isTitleVisible() {
      return titleVisible;
   }

   /**
    * Set whether the legend title should be displayed.
    */
   @TernMethod
   public void setTitleVisible(boolean vis) {
      this.titleVisible = vis;
   }

   /**
    * Get the background fill color.
    */
   @TernMethod
   public Color getBackground() {
      return bg;
   }

   /**
    * Set the background fill color.
    */
   @TernMethod
   public void setBackground(Color bg) {
      this.bg = bg;
   }

   /**
    * Set if items can be ignored if there is no sufficient space.
    */
   @TernMethod
   public void setPartial(boolean partial) {
      this.partial = partial;
   }

   /**
    * Check if items can be ignored if there is no sufficient space.
    */
   @TernMethod
   public boolean isPartial() {
      return partial;
   }

   /**
    * Check if the position of the legend is specified as the top of the legend instead of bottom.
    */
   @TernMethod
   public boolean isTopY() {
      return topY;
   }

   /**
    * Set if the position of the legend is specified as the top of the legend instead of bottom.
    * This affects the position set through setPosition(). The PlotPosition is always specified
    * as the top of the legend.
    */
   @TernMethod
   public void setTopY(boolean topY) {
      this.topY = topY;
   }

   /**
    * Get the gap between the legend and the axis/plot
    */
   @TernMethod
   public int getGap() {
      return gap;
   }

   /**
    * Set the gap between the legend and the axis/plot
    */
   @TernMethod
   public void setGap(int gap) {
      this.gap = gap;
   }

   /**
    * Get the legend item/title padding
    */
   @TernMethod
   public Insets getPadding() {
      return padding;
   }

   /**
    * Set the legend item/title padding
    */
   @TernMethod
   public void setPadding(Insets padding) {
      this.padding = padding;
   }

   /**
    * Set the visibility of legend item.
    */
   @TernMethod
   public void setVisible(Object label, boolean visible) {
      if(visible) {
         hiddenItems.remove(label);
      }
      else {
         hiddenItems.add(label);
      }
   }

   /**
    * Check the visibility of legend item.
    */
   @TernMethod
   public boolean isVisible(Object label) {
      return !hiddenItems.contains(label);
   }

   /**
    * Make a copy of this object.
    */
   @Override
   public LegendSpec clone() {
      try {
         LegendSpec spec = (LegendSpec) super.clone();

         spec.titleSpec = titleSpec.clone();
         spec.textSpec = textSpec.clone();

         if(textFrame != null) {
            spec.textFrame = (TextFrame) textFrame.clone();
         }

         return spec;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone legend specification", ex);
      }

      return null;
   }

   private boolean visible = true;

   @Override
   public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;
      LegendSpec that = (LegendSpec) o;
      return visible == that.visible && border == that.border &&
         titleVisible == that.titleVisible && partial == that.partial && topY == that.topY &&
         gap == that.gap && Objects.equals(borderColor, that.borderColor) &&
         Objects.equals(bg, that.bg) && Objects.equals(title, that.title) &&
         Objects.equals(titleSpec, that.titleSpec) && Objects.equals(textFrame, that.textFrame) &&
         Objects.equals(textSpec, that.textSpec) && Objects.equals(position, that.position) &&
         Objects.equals(epos, that.epos) && Objects.equals(preferredSize, that.preferredSize) &&
         Objects.equals(padding, that.padding) && Objects.equals(hiddenItems, that.hiddenItems);
   }

   @Override
   public int hashCode() {
      return Objects.hash(visible, border, borderColor, bg, title, titleVisible, titleSpec,
                          textFrame, textSpec, partial, position, epos, preferredSize, topY, gap,
                          padding, hiddenItems);
   }

   private int border = GraphConstants.THIN_LINE;
   private Color borderColor = GDefaults.DEFAULT_LINE_COLOR;
   private Color bg;
   private String title = null;
   private boolean titleVisible = true;
   private TextSpec titleSpec = new TextSpec();
   private TextFrame textFrame;
   private TextSpec textSpec = new TextSpec();
   private boolean partial = false;
   private Point2D position = null;
   private Point2D epos = null;
   private Dimension2D preferredSize = null;
   private boolean topY;
   private int gap = 0;
   private Insets padding;
   private Set hiddenItems = new HashSet();

   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(LegendSpec.class);
}
