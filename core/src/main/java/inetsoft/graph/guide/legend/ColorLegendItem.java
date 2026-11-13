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
package inetsoft.graph.guide.legend;

import inetsoft.graph.aesthetic.ColorFrame;
import inetsoft.graph.aesthetic.GShape;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.internal.GTool;

import java.awt.*;
import java.awt.geom.RectangularShape;

/**
 * This class renders a single color legend item.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ColorLegendItem extends LegendItem {
   /**
    * Create a legend item.
    * @param label the text label.
    * @param value the value of this label.
    * @param frame the frame to get aesthetic attribute.
    */
   public ColorLegendItem(Object label, Object value, VisualFrame frame) {
      this(label, value, frame, null);
   }

   /**
    * Create a legend item.
    * @param label the text label.
    * @param value the value of this label.
    * @param frame the frame to get aesthetic attribute.
    * @param marker the shape of the mark this color is applied to (e.g. a Gantt milestone
    *               diamond), or null to draw the default square swatch.
    */
   public ColorLegendItem(Object label, Object value, VisualFrame frame, GShape marker) {
      super(label, value, frame);
      this.marker = marker;
   }

   /**
    * Paint the symbol at the specified location.
    * @param x is the symbol position x.
    * @param y is the symbol position y.
    */
   @Override
   protected void paintSymbol(Graphics2D g, double x, double y) {
      Graphics2D g2 = (Graphics2D) g.create();
      int size = getSymbolSize();
      ColorFrame frame = (ColorFrame) getVisualFrame();
      Color color = frame.getColor(getValue());
      color = GTool.getColor(color, getAlpha());
      g2.setColor(color);

      if(marker != null) {
         // same box as the shape legend item so the glyph matches the mark on the chart
         marker.paint(g2, x + 2, y + 1, size - 2, size - 2);
         g2.dispose();
         return;
      }

      RectangularShape rect = createSymbolRect(x, y, size, size);

      if(frame.getLegendSpec().isSymbolRoundCorners()) {
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      }

      g2.fill(rect);

      // best aesthetics, only draw border for 'white' color
      if(color != null && color.getRed() >= 250 && color.getGreen() >= 250 &&
         color.getBlue() >= 250)
      {
         g2.setColor(SYMBOL_BORDER);
         g2.draw(rect);
      }

      g2.dispose();
   }

   private final GShape marker;
}
