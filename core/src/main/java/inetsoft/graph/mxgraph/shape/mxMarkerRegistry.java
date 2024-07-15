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
import inetsoft.graph.mxgraph.util.*;
import inetsoft.graph.mxgraph.view.mxCellState;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.Hashtable;
import java.util.Map;

public class mxMarkerRegistry {
   /**
    *
    */
   protected static Map<String, mxIMarker> markers = new Hashtable<String, mxIMarker>();

   static {
      mxIMarker tmp = new mxIMarker() {
         public mxPoint paintMarker(mxGraphics2DCanvas canvas,
                                    mxCellState state, String type, mxPoint pe, double nx,
                                    double ny, double size, boolean source)
         {
            Polygon poly = new Polygon();
            poly.addPoint((int) Math.round(pe.getX()),
                          (int) Math.round(pe.getY()));
            poly.addPoint((int) Math.round(pe.getX() - nx - ny / 2),
                          (int) Math.round(pe.getY() - ny + nx / 2));

            if(type.equals(mxConstants.ARROW_CLASSIC)) {
               poly.addPoint((int) Math.round(pe.getX() - nx * 3 / 4),
                             (int) Math.round(pe.getY() - ny * 3 / 4));
            }

            poly.addPoint((int) Math.round(pe.getX() + ny / 2 - nx),
                          (int) Math.round(pe.getY() - ny - nx / 2));

            if(mxUtils.isTrue(state.getStyle(), (source) ? "startFill" : "endFill", true)) {
               canvas.fillShape(poly);
            }

            canvas.getGraphics().draw(poly);

            return new mxPoint(-nx, -ny);
         }
      };

      registerMarker(mxConstants.ARROW_CLASSIC, tmp);
      registerMarker(mxConstants.ARROW_BLOCK, tmp);

      registerMarker(mxConstants.ARROW_OPEN, new mxIMarker() {
         public mxPoint paintMarker(mxGraphics2DCanvas canvas,
                                    mxCellState state, String type, mxPoint pe, double nx,
                                    double ny, double size, boolean source)
         {
            canvas.getGraphics().draw(
               new Line2D.Float((int) Math.round(pe.getX() - nx - ny
                  / 2),
                                (int) Math.round(pe.getY() - ny + nx / 2),
                                (int) Math.round(pe.getX() - nx / 6),
                                (int) Math.round(pe.getY() - ny / 6)));
            canvas.getGraphics().draw(
               new Line2D.Float((int) Math.round(pe.getX() - nx / 6),
                                (int) Math.round(pe.getY() - ny / 6),
                                (int) Math.round(pe.getX() + ny / 2 - nx),
                                (int) Math.round(pe.getY() - ny - nx / 2)));

            return new mxPoint(-nx / 2, -ny / 2);
         }
      });

      registerMarker(mxConstants.ARROW_OVAL, new mxIMarker() {
         public mxPoint paintMarker(mxGraphics2DCanvas canvas,
                                    mxCellState state, String type, mxPoint pe, double nx,
                                    double ny, double size, boolean source)
         {
            double cx = pe.getX() - nx / 2;
            double cy = pe.getY() - ny / 2;
            double a = size / 2;
            Shape shape = new Ellipse2D.Double(cx - a, cy - a, size, size);

            if(mxUtils.isTrue(state.getStyle(), (source) ? "startFill" : "endFill", true)) {
               canvas.fillShape(shape);
            }

            canvas.getGraphics().draw(shape);

            return new mxPoint(-nx / 2, -ny / 2);
         }
      });


      registerMarker(mxConstants.ARROW_DIAMOND, new mxIMarker() {
         public mxPoint paintMarker(mxGraphics2DCanvas canvas,
                                    mxCellState state, String type, mxPoint pe, double nx,
                                    double ny, double size, boolean source)
         {
            Polygon poly = new Polygon();
            poly.addPoint((int) Math.round(pe.getX()),
                          (int) Math.round(pe.getY()));
            poly.addPoint((int) Math.round(pe.getX() - nx / 2 - ny / 2),
                          (int) Math.round(pe.getY() + nx / 2 - ny / 2));
            poly.addPoint((int) Math.round(pe.getX() - nx),
                          (int) Math.round(pe.getY() - ny));
            poly.addPoint((int) Math.round(pe.getX() - nx / 2 + ny / 2),
                          (int) Math.round(pe.getY() - ny / 2 - nx / 2));

            if(mxUtils.isTrue(state.getStyle(), (source) ? "startFill" : "endFill", true)) {
               canvas.fillShape(poly);
            }

            canvas.getGraphics().draw(poly);

            return new mxPoint(-nx / 2, -ny / 2);
         }
      });
   }

   /**
    *
    */
   public static mxIMarker getMarker(String name)
   {
      return markers.get(name);
   }

   /**
    *
    */
   public static void registerMarker(String name, mxIMarker marker)
   {
      markers.put(name, marker);
   }

}
