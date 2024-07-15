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
package inetsoft.graph.schema;

import com.inetsoft.build.tern.TernClass;
import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.geometry.SchemaGeometry;
import inetsoft.graph.internal.GTool;

import java.awt.*;
import java.awt.geom.*;

/**
 * This schema painter renders the candle chart points. Four variables are
 * required for candle charts, in the following order: high, close, open, low.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=setPainter")
public class CandlePainter extends SchemaPainter {
   /**
    * Initialize the schema painter with geometry and coord.
    * @param geometry the specified schema geometry.
    * @param coord the specified coord.
    */
   @Override
   public void init(SchemaGeometry geometry, Coordinate coord) {
      this.geometry = geometry;
      double[] tuple = geometry.getTuple();
      double[] vars = geometry.getVarTuple();
      boolean hasX = coord instanceof RectCoord && ((RectCoord) coord).getXScale() != null;
      Point2D high = coord.getPosition(getSubTuple(0, tuple, vars, hasX));
      Point2D close = coord.getPosition(getSubTuple(1, tuple, vars, hasX));
      Point2D open = coord.getPosition(getSubTuple(2, tuple, vars, hasX));
      Point2D low = coord.getPosition(getSubTuple(3, tuple, vars, hasX));
      double size = geometry.getSize(0);
      SizeFrame sizes = geometry.getVisualModel().getSizeFrame();
      double width = getSize(coord, size, sizes);

      shapes = new Shape[getShapeCount()];
      shapes[0] = new Line2D.Double(high.getX(), high.getY(),
                                     low.getX(), low.getY());

      // open > close, don't use position since scale could be reversed
      if(vars.length > 2) {
         negative = vars[2] > vars[1];
      }

      if(open.getY() > close.getY()) {
         shapes[1] = new Rectangle2D.Double(close.getX(), close.getY(), width,
                                            open.getY() - close.getY());
      }
      else {
         shapes[1] = new Rectangle2D.Double(open.getX(), open.getY(), width,
                                            close.getY() - open.getY());
      }

      shapes[1] = GTool.centerShape(shapes[1], true);
   }

   /**
    * Check if supports frame.
    */
   @Override
   public boolean supportsFrame(VisualFrame frame) {
      return !(frame instanceof ShapeFrame);
   }

   /**
    * Transform the painter with screen affine transform.
    * @param trans the specified affine transform.
    */
   @Override
   public void transformScreen(AffineTransform trans) {
      for(int i = 0; i < getShapeCount(); i++) {
         if(shapes[i] instanceof Line2D) {
            Point2D p1 = ((Line2D) shapes[i]).getP1();
            Point2D p2 = ((Line2D) shapes[i]).getP2();

            p1 = trans.transform(p1, null);
            p2 = trans.transform(p2, null);
            shapes[i] = new Line2D.Double(p1.getX(), p1.getY(),
               p2.getX(), p2.getY());
         }
         else {
            shapes[i] = trans.createTransformedShape(shapes[i]);
         }
      }
   }

   /**
    * Paint the schema painter.
    * @param g the specified graphics.
    */
   @Override
   public void paint(Graphics2D g) {
      Color color = applyAlpha(geometry.getColor(0));
      GLine line = geometry.getLine(0);
      GTexture texture = geometry.getTexture(0);

      g = (Graphics2D) g.create();
      g.setColor(color);

      if(line != null) {
         g.setStroke(line.getStroke());
      }

      for(int i = 0; i < getShapeCount(); i++) {
         if(getShape(i) instanceof Line2D) {
            // @by larryl, jdk 1.6.0 13_b03 bug, drawing a Line2D in PURE mode
            // may cause the line to be ignored altogether. Converting to
            // GeneralPath solves the problem
            g.draw(new GeneralPath(getShape(i)));
         }
         else {
            g.setColor(Color.white);
            g.fill(getShape(i));
            g.setColor(color);

            if(negative) {
               if(texture == null) {
                  g.fill(getShape(i));
                  g.draw(getShape(i));
               }
               else {
                  texture.paint(g, getShape(i));
                  g.draw(getShape(i));
               }
            }
            else {
               g.draw(getShape(i));
            }
         }
      }

      g.dispose();
   }

   /**
    * Get the shape count of the schema painter.
    */
   @Override
   @TernMethod
   public int getShapeCount() {
      return 2;
   }

   /**
    * Get the shape at the specified index of the schema painter.
    */
   @Override
   @TernMethod
   public Shape getShape(int idx) {
      return shapes[idx];
   }

   /**
    * Replace the specified shape.
    */
   @Override
   @TernMethod
   public void setShape(int idx, Shape shape) {
      shapes[idx] = shape;
   }

   /**
    * Clone the shema painter.
    */
   @Override
   public Object clone() {
      CandlePainter painter = (CandlePainter) super.clone();

      if(shapes != null) {
         painter.shapes = (Shape[]) shapes.clone();
      }

      return painter;
   }

   private Shape[] shapes;
   private boolean negative = false;
   private static final long serialVersionUID = 1L;
}
