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

public class mxHexagonShape extends mxBasicShape {

   /**
    *
    */
   public Shape createShape(mxGraphics2DCanvas canvas, mxCellState state)
   {
      Rectangle temp = state.getRectangle();
      int x = temp.x;
      int y = temp.y;
      int w = temp.width;
      int h = temp.height;
      String direction = mxUtils.getString(state.getStyle(),
                                           mxConstants.STYLE_DIRECTION, mxConstants.DIRECTION_EAST);
      Polygon hexagon = new Polygon();

      if(direction.equals(mxConstants.DIRECTION_NORTH)
         || direction.equals(mxConstants.DIRECTION_SOUTH))
      {
         hexagon.addPoint(x + (int) (0.5 * w), y);
         hexagon.addPoint(x + w, y + (int) (0.25 * h));
         hexagon.addPoint(x + w, y + (int) (0.75 * h));
         hexagon.addPoint(x + (int) (0.5 * w), y + h);
         hexagon.addPoint(x, y + (int) (0.75 * h));
         hexagon.addPoint(x, y + (int) (0.25 * h));
      }
      else {
         hexagon.addPoint(x + (int) (0.25 * w), y);
         hexagon.addPoint(x + (int) (0.75 * w), y);
         hexagon.addPoint(x + w, y + (int) (0.5 * h));
         hexagon.addPoint(x + (int) (0.75 * w), y + h);
         hexagon.addPoint(x + (int) (0.25 * w), y + h);
         hexagon.addPoint(x, y + (int) (0.5 * h));
      }

      return hexagon;
   }

}
