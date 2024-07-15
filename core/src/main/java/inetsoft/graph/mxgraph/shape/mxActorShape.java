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
package inetsoft.graph.mxgraph.shape;

import inetsoft.graph.mxgraph.canvas.mxGraphics2DCanvas;
import inetsoft.graph.mxgraph.view.mxCellState;

import java.awt.*;
import java.awt.geom.GeneralPath;

public class mxActorShape extends mxBasicShape {

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
      float width = w * 2 / 6;

      GeneralPath path = new GeneralPath();

      path.moveTo(x, y + h);
      path.curveTo(x, y + 3 * h / 5, x, y + 2 * h / 5, x + w / 2, y + 2 * h
         / 5);
      path.curveTo(x + w / 2 - width, y + 2 * h / 5, x + w / 2 - width, y, x
         + w / 2, y);
      path.curveTo(x + w / 2 + width, y, x + w / 2 + width, y + 2 * h / 5, x
         + w / 2, y + 2 * h / 5);
      path.curveTo(x + w, y + 2 * h / 5, x + w, y + 3 * h / 5, x + w, y + h);
      path.closePath();

      return path;
   }

}
