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
import java.awt.geom.Rectangle2D;

/**
 * This class renders a texture legend item.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class TextureLegendItem extends LegendItem {
   /**
    * Create a legend item.
    * @param label the text label.
    * @param value the value of this label.
    * @param frame the frame to get aesthetic attribute.
    */
   public TextureLegendItem(Object label, Object value, VisualFrame frame) {
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
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
			  RenderingHints.VALUE_STROKE_PURE);

      Rectangle2D rect= new Rectangle2D.Double(x, y, SYMBOL_SIZE, SYMBOL_SIZE);
      TextureFrame frame = (TextureFrame) getVisualFrame();
      GTexture gt = frame.getTexture(getValue());

      g2.setColor(getSymbolColor());

      if(gt != null) {
         gt.paint(g2, rect);
      }
      else {
         g2.fill(rect);
      }

      g2.draw(rect);
      g2.dispose();
   }
}
