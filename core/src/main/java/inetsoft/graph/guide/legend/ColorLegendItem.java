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
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.internal.GTool;

import java.awt.*;
import java.awt.geom.Rectangle2D;

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
      super(label, value, frame);
   }

   /**
    * Paint the symbol at the specified location.
    * @param x is the symbol position x.
    * @param y is the symbol position y.
    */
   @Override
   protected void paintSymbol(Graphics2D g, double x, double y) {
      Graphics2D g2 = (Graphics2D) g.create();
      Rectangle2D rect = new Rectangle2D.Double(x, y, SYMBOL_SIZE, SYMBOL_SIZE);
      ColorFrame frame = (ColorFrame) getVisualFrame();
      Color color = frame.getColor(getValue());
      color = GTool.getColor(color, getAlpha());
      g2.setColor(color);
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
}
