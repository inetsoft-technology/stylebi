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
import java.awt.geom.Ellipse2D;

public class mxEllipseShape extends mxBasicShape {

   /**
    *
    */
   public Shape createShape(mxGraphics2DCanvas canvas, mxCellState state)
   {
      Rectangle temp = state.getRectangle();

      return new Ellipse2D.Float(temp.x, temp.y, temp.width, temp.height);
   }

}
