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
import inetsoft.graph.mxgraph.util.mxPoint;
import inetsoft.graph.mxgraph.view.mxCellState;

import java.awt.*;

public class mxArrowShape extends mxBasicShape {

   /**
    *
    */
   public Shape createShape(mxGraphics2DCanvas canvas, mxCellState state)
   {
      double scale = canvas.getScale();
      mxPoint p0 = state.getAbsolutePoint(0);
      mxPoint pe = state.getAbsolutePoint(state.getAbsolutePointCount() - 1);

      // Geometry of arrow
      double spacing = mxConstants.ARROW_SPACING * scale;
      double width = mxConstants.ARROW_WIDTH * scale;
      double arrow = mxConstants.ARROW_SIZE * scale;

      double dx = pe.getX() - p0.getX();
      double dy = pe.getY() - p0.getY();
      double dist = Math.sqrt(dx * dx + dy * dy);
      double length = dist - 2 * spacing - arrow;

      // Computes the norm and the inverse norm
      double nx = dx / dist;
      double ny = dy / dist;
      double basex = length * nx;
      double basey = length * ny;
      double floorx = width * ny / 3;
      double floory = -width * nx / 3;

      // Computes points
      double p0x = p0.getX() - floorx / 2 + spacing * nx;
      double p0y = p0.getY() - floory / 2 + spacing * ny;
      double p1x = p0x + floorx;
      double p1y = p0y + floory;
      double p2x = p1x + basex;
      double p2y = p1y + basey;
      double p3x = p2x + floorx;
      double p3y = p2y + floory;
      // p4 not required
      double p5x = p3x - 3 * floorx;
      double p5y = p3y - 3 * floory;

      Polygon poly = new Polygon();
      poly.addPoint((int) Math.round(p0x), (int) Math.round(p0y));
      poly.addPoint((int) Math.round(p1x), (int) Math.round(p1y));
      poly.addPoint((int) Math.round(p2x), (int) Math.round(p2y));
      poly.addPoint((int) Math.round(p3x), (int) Math.round(p3y));
      poly.addPoint((int) Math.round(pe.getX() - spacing * nx), (int) Math
         .round(pe.getY() - spacing * ny));
      poly.addPoint((int) Math.round(p5x), (int) Math.round(p5y));
      poly.addPoint((int) Math.round(p5x + floorx), (int) Math.round(p5y
                                                                        + floory));

      return poly;
   }

}
