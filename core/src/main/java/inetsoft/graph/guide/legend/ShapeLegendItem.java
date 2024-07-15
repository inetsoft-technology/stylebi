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

/**
 * This class renders a shape legend item.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ShapeLegendItem extends LegendItem {
   /**
    * Create a legend item.
    * @param label the text label.
    * @param value the value of this label.
    * @param frame the frame to get aesthetic attribute.
    * @param colorBound true if color is bound to a field.
    */
   public ShapeLegendItem(Object label, Object value, VisualFrame frame, boolean colorBound) {
      super(label, value, frame);
      this.colorBound = colorBound;
   }

   /**
    * Paint the symbol at the specified location.
    * @param x is the symbol position x.
    * @param y is the symbol position y.
    */
   @Override
   protected void paintSymbol(Graphics2D g, double x, double y) {
      Graphics2D g2 = (Graphics2D) g.create();
      ShapeFrame frame = (ShapeFrame) getVisualFrame();
      GShape gshape = frame.getShape(getValue());

      g2.setColor(getSymbolColor());

      if(gshape instanceof GShape.ImageShape) {
         GShape.ImageShape img = (GShape.ImageShape) gshape.clone();
         // applyColor should match the setting used for rendering, so shouldn't change it here.
         //img.setApplyColor(getSymbolColor() != SYMBOL_COLOR);
         // if column is bound to a field (e.g. state), shapes will have different colors
         // so applying the color on legend doesn't make sense. (59518)
         if(colorBound) {
            img.setApplyColor(false);
         }

         img.setAlignment(GShape.ImageShape.Alignment.CENTER);
         img.setApplySize(true);
         // give icon a little larger area to avoid resize
         img.paint(g2, x + 1, y, SYMBOL_SIZE, SYMBOL_SIZE);
      }
      else if(gshape != null) {
         gshape.paint(g2, x + 2, y + 1, SYMBOL_SIZE - 2, SYMBOL_SIZE - 2);
      }

      g2.dispose();
   }

   private final boolean colorBound;
}
