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

public class mxDoubleRectangleShape extends mxRectangleShape {

   /**
    *
    */
   public void paintShape(mxGraphics2DCanvas canvas, mxCellState state)
   {
      super.paintShape(canvas, state);

      int inset = (int) Math.round((mxUtils.getFloat(state.getStyle(),
                                                     mxConstants.STYLE_STROKEWIDTH, 1) + 3)
                                      * canvas.getScale());

      Rectangle rect = state.getRectangle();
      int x = rect.x + inset;
      int y = rect.y + inset;
      int w = rect.width - 2 * inset;
      int h = rect.height - 2 * inset;

      canvas.getGraphics().drawRect(x, y, w, h);
   }

}
