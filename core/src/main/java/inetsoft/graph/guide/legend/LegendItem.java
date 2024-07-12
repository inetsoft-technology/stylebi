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
package inetsoft.graph.guide.legend;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.LineFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.DimensionD;
import inetsoft.graph.internal.GTool;
import inetsoft.util.Tool;

import java.awt.*;
import java.awt.geom.Point2D;

/**
 * Legend item.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class LegendItem extends BoundedContainer {
   /**
    * Constructor.
    * @param label the item show string.
    * @param value the symbol's type.
    * @param frame the legend frame to create item.
    */
   public LegendItem(Object label, Object value, VisualFrame frame) {
      this.value = value;
      this.frame = frame;

      TextSpec spec0 = frame.getLegendSpec().getTextSpec();

      // if label is from explicitly defined alias, don't format
      if(!Tool.equals(label, value) && spec0 != null) {
         spec0 = spec0.clone();
         spec0.setFormat(null);
      }

      Insets padding = frame.getLegendSpec().getPadding();

      if(padding != null) {
         topPadding = padding.top;
         bottomPadding = padding.bottom;
      }

      // use value instead, or VLabel format will cause error
      vlabel = new VLabel(label, spec0);
      vlabel.setAlignmentX(GraphConstants.LEFT_ALIGNMENT);
      vlabel.setAlignmentY(GraphConstants.MIDDLE_ALIGNMENT);
      addVisual(vlabel);
      initSizes();
   }

   /**
    * Get the value of the legend item.
    * @return value the symbol's type.
    */
   public Object getValue() {
      return value;
   }

   /**
    * Get label.
    */
   public String getLabel() {
      return vlabel.getText();
   }

   /**
    * Get minimum width of the item.
    * @return item minimum width.
    */
   @Override
   protected double getMinWidth0() {
      return minW;
   }

   /**
    * Get minimum height of the item.
    * @return item minimum height.
    */
   @Override
   protected double getMinHeight0() {
      return minH;
   }

   /**
    * Get maximum width of the item.
    * @return item maximum width.
    */
   @Override
   protected double getPreferredWidth0() {
      return preferW;
   }

   /**
    * Get preferred width of the item.
    * @return item preferred width.
    */
   @Override
   protected double getPreferredHeight0() {
      return preferH;
   }

   /**
    * Get maximum width of the item.
    * @return item maximum width.
    */
   public double getMaxWidth() {
      return maxW;
   }

   /**
    * Get maximum height of the item.
    * @return item height width.
    */
   public double getMaxHeight() {
      return maxH;
   }

   /**
    * Init min size, preferred size, max size.
    */
   private void initSizes() {
      double symbolW = 0;

      if(frame instanceof LineFrame) {
         symbolW = LINESYMBOL_WIDTH;
      }
      else {
         symbolW = SYMBOL_SIZE;
      }

      double labelMinH = vlabel.getMinHeight();
      double labelPrefH = vlabel.getPreferredHeight();

      // null or empty label should still account for height
      if(labelMinH == 0 && labelPrefH == 0) {
         labelMinH = vlabel.getPreferredHeight("A");
         labelPrefH = vlabel.getPreferredHeight("A");
      }

      minW = symbolW + GAP + vlabel.getWidth(MIN_CHAR_COUNT) + LEFT_PADDING + RIGHT_PADDING;
      minH = topPadding + bottomPadding + Math.max(labelMinH, SYMBOL_SIZE + 2);

      preferW = symbolW + GAP + vlabel.getPreferredWidth() + LEFT_PADDING + RIGHT_PADDING;
      preferH = topPadding + bottomPadding + Math.max(labelMinH, SYMBOL_SIZE + 2);

      maxW = symbolW + GAP + vlabel.getPreferredWidth() + LEFT_PADDING + RIGHT_PADDING;
      maxH = topPadding + bottomPadding + Math.max(labelPrefH, SYMBOL_SIZE + 2);
   }

   /**
    * Get the legend frame of this legend.
    * @return item's frame.
    */
   public VisualFrame getVisualFrame() {
      return frame;
   }

   /**
    * Paint the item.
    * @param g is the graphics context.
    */
   @Override
   public void paint(Graphics2D g) {
      double height = getSize().getHeight();

      // the padding left logic has moved to VLegend
      paintSymbol(g, getPosition().getX(),
                  getPosition().getY() + (height - SYMBOL_SIZE) / 2);
      vlabel.paint(g);
   }

   /**
    * Layout.
    */
   public void layout() {
      double width = getSize().getWidth();
      double height = getSize().getHeight();
      Point2D pos = getPosition();

      // the padding left logic has moved to VLegend
      if(frame instanceof LineFrame) {
         pos = new Point2D.Double(pos.getX() + LINESYMBOL_WIDTH + GAP,
                                  pos.getY() + bottomPadding - topPadding);

         vlabel.setSize(new DimensionD(width - LINESYMBOL_WIDTH - GAP, height));
         vlabel.setPosition(pos);
      }
      else {
         pos = new Point2D.Double(pos.getX() + SYMBOL_SIZE + GAP,
                                  pos.getY() + bottomPadding - topPadding);

         vlabel.setSize(new DimensionD(width - SYMBOL_SIZE - GAP, height));
         vlabel.setPosition(pos);
      }

      pos = new Point2D.Double(pos.getX(), pos.getY() + height / 2);
      Point2D offset = vlabel.getRotationOffset(pos, GraphConstants.RIGHT);
      vlabel.setOffset(offset);
   }

   /**
    * Set the color for drawing item symbol.
    */
   public void setSymbolColor(Color c) {
      this.symbolClr = c;
   }

   /**
    * Get the color for drawing item symbol.
    */
   public Color getSymbolColor() {
      return GTool.getColor(symbolClr, alpha);
   }

   /**
    * Paint the symbol at the specified location.
    * @param x is the symbol position x.
    * @param y is the symbol position y.
    */
   protected abstract void paintSymbol(Graphics2D g, double x, double y);

   /**
    * Set the color alpha of symbol.
    * @param alpha the color alpha.
    */
   void setAlpha(double alpha) {
      this.alpha = alpha;
   }

   /**
    * Get the color alpha of symbol.
    * @return the symbol color alpha.
    */
   double getAlpha() {
      return this.alpha;
   }

   public String toString() {
      return super.toString() + "[" + value + "]";
   }

   protected static final int SYMBOL_SIZE = 12;
   protected static final int LINESYMBOL_WIDTH = 30;
   protected static final Color SYMBOL_BORDER = new Color(0xa3, 0xa3, 0xa3);
   protected static final Color SYMBOL_COLOR = new Color(0x759595);
   private static final int TOP_PADDING = 0;
   private static final int BOTTOM_PADDING = 1;
   private static final int LEFT_PADDING = 5;
   private static final int RIGHT_PADDING = 0;
   private static final int GAP = 5;
   private static final int MIN_CHAR_COUNT = 4;

   private Object value;
   private VisualFrame frame;
   private VLabel vlabel;
   private double minW = 0;
   private double minH = 0;
   private double preferW = 0;
   private double preferH = 0;
   private double maxW = 0;
   private double maxH = 0;
   private Color symbolClr = SYMBOL_COLOR;
   private double alpha = 1;
   private int topPadding = TOP_PADDING;
   private int bottomPadding = BOTTOM_PADDING;
}
