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

import inetsoft.graph.aesthetic.SizeFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.internal.GTool;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * This class renders a size legend item.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class SizeLegendItem extends LegendItem {
   /**
    * Create a legend item.
    * @param label the text label.
    * @param value the value of this label.
    * @param frame the frame to get aesthetic attribute.
    */
   public SizeLegendItem(Object label, Object value, VisualFrame frame) {
      super(label, value, frame);
   }

   /**
    * Paint the symbol at the specified location.
    * @param x is the symbol position x.
    * @param y is the symbol position y.
    */
   @Override
   protected void paintSymbol(Graphics2D g, double x, double y) {
      SizeFrame frame = (SizeFrame) getVisualFrame();
      Graphics2D g2 = (Graphics2D) g.create();
      double size = frame.getSize(getValue());
      double smallest = frame.getSmallest();
      double largest = frame.getLargest();
      double sizeRatio = (size - smallest) / (largest - smallest);
      double r = 1 + (SYMBOL_SIZE  - 1) * sizeRatio;

      r = Math.min(r, SYMBOL_SIZE);
      double x0 = x + SYMBOL_SIZE / 2 - r / 2 + 0.5;
      g2.setColor(getSymbolColor());
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.fill(new Rectangle2D.Double(x0, y, r, SYMBOL_SIZE));
      g2.setColor(SYMBOL_BORDER);

      if(!GTool.isVectorGraphics(g2)) {
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
      }

      g2.draw(new Rectangle2D.Double(x, y, SYMBOL_SIZE, SYMBOL_SIZE));
      g2.dispose();
   }
}
