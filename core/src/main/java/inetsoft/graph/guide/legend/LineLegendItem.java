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

import inetsoft.graph.aesthetic.*;

import java.awt.*;
import java.awt.geom.Point2D;

/**
 * This class renders a line legend item.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class LineLegendItem extends LegendItem {
   /**
    * Create a legend item.
    * @param label the text label.
    * @param value the value of this label.
    * @param frame the frame to get aesthetic attribute.
    */
   public LineLegendItem(Object label, Object value, VisualFrame frame) {
      super(label, value, frame);
   }

   /**
    * Paint the symbol at the specified location.
    * @param x is the symbol position x.
    * @param y is the symbol position y.
    */
   @Override
   protected void paintSymbol(Graphics2D g, double x, double y) {
      LineFrame frame = (LineFrame) getVisualFrame();
      GLine gline = frame.getLine(getValue());
      Point2D start = new Point2D.Double(x, y + SYMBOL_SIZE / 2);
      Point2D end = new Point2D.Double(x + LINESYMBOL_WIDTH, y + SYMBOL_SIZE/2);
      double size = gline.getLineWidth();
      Shape shape = gline.getShape(start, end, size, size, gline, gline);
      Color lineColor = getSymbolColor();

      gline.paint(g, start, end, lineColor, lineColor, shape);
   }
}
