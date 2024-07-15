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
package inetsoft.graph.mxgraph.shape;

import inetsoft.graph.mxgraph.canvas.mxGraphics2DCanvas;
import inetsoft.graph.mxgraph.util.mxConstants;
import inetsoft.graph.mxgraph.util.mxUtils;
import inetsoft.graph.mxgraph.view.mxCellState;

import java.awt.*;
import java.util.Map;

public class mxRectangleShape extends mxBasicShape {

   /**
    *
    */
   public void paintShape(mxGraphics2DCanvas canvas, mxCellState state)
   {
      Map<String, Object> style = state.getStyle();

      if(mxUtils.isTrue(style, mxConstants.STYLE_ROUNDED, false)) {
         Rectangle tmp = state.getRectangle();

         int x = tmp.x;
         int y = tmp.y;
         int w = tmp.width;
         int h = tmp.height;
         int radius = getArcSize(state, w, h);

         boolean shadow = hasShadow(canvas, state);
         int shadowOffsetX = (shadow) ? mxConstants.SHADOW_OFFSETX : 0;
         int shadowOffsetY = (shadow) ? mxConstants.SHADOW_OFFSETY : 0;

         if(canvas.getGraphics().hitClip(x, y, w + shadowOffsetX,
                                         h + shadowOffsetY))
         {
            // Paints the optional shadow
            if(shadow) {
               canvas.getGraphics().setColor(Color.GRAY);
               canvas.getGraphics().fillRoundRect(
                  x + mxConstants.SHADOW_OFFSETX,
                  y + mxConstants.SHADOW_OFFSETY, w, h, radius,
                  radius);
            }

            // Paints the background
            if(configureGraphics(canvas, state, true)) {
               canvas.getGraphics().fillRoundRect(x, y, w, h, radius,
                                                  radius);
            }

            // Paints the foreground
            if(configureGraphics(canvas, state, false)) {
               canvas.getGraphics().drawRoundRect(x, y, w, h, radius,
                                                  radius);
            }
         }
      }
      else {
         Rectangle rect = state.getRectangle();

         // Paints the background
         if(configureGraphics(canvas, state, true)) {
            canvas.fillShape(rect, hasShadow(canvas, state));
         }

         // Paints the foreground
         if(configureGraphics(canvas, state, false)) {
            canvas.getGraphics().drawRect(rect.x, rect.y, rect.width,
                                          rect.height);
         }
      }
   }

   /**
    * Helper method to configure the given wrapper canvas.
    */
   protected int getArcSize(mxCellState state, double w, double h)
   {
      double f = mxUtils.getDouble(state.getStyle(),
                                   mxConstants.STYLE_ARCSIZE,
                                   mxConstants.RECTANGLE_ROUNDING_FACTOR * 100) / 100;

      return (int) (Math.min(w, h) * f * 2);
   }

}
