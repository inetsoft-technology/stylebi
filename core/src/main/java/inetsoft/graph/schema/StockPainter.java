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
 * This schema painter renders high-low-close chart points. Three variables are
 * required for the stock painter, in the following order: high, close, low.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=StockPainter")
public class StockPainter extends SchemaPainter {
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
      int idx = 0;
      boolean hasX = coord instanceof RectCoord && ((RectCoord) coord).getXScale() != null;
      Point2D high = coord.getPosition(getSubTuple(idx++, tuple, vars, hasX));
      Point2D close = coord.getPosition(getSubTuple(idx++, tuple, vars, hasX));
      Point2D open = (vars.length < 4) ? null
	 : coord.getPosition(getSubTuple(idx++, tuple, vars, hasX));
      Point2D low = coord.getPosition(getSubTuple(idx++, tuple, vars, hasX));

      shapes = new Shape[open == null ? 2 : 3];
      shapes[0] = new Line2D.Double(high.getX(), high.getY(), low.getX(), low.getY());
      shapes[1] = new Line2D.Double(close.getX(), close.getY(),
                                    close.getX() + LENGTH, close.getY());

      if(open != null) {
         shapes[2] = new Line2D.Double(open.getX(), open.getY(),
                                       open.getX() - LENGTH, open.getY());
      }
   }

   /**
    * Transform the painter with screen affine transform.
    * @param trans the specified affine transform.
    */
   @Override
   public void transformScreen(AffineTransform trans) {
      for(int i = 0; i < getShapeCount(); i++) {
         shapes[i] = trans.createTransformedShape(shapes[i]);
      }
   }

   /**
    * Paint the schema painter.
    * @param g the specified graphics.
    */
   @Override
   public void paint(Graphics2D g) {
      Color color = applyAlpha(geometry.getColor(0));
      double size = geometry.getSize(0);
      GLine line = geometry.getLine(0);

      g = (Graphics2D) g.create();
      g.setColor(color);

      double linew = (line == null) ? 1 : GTool.getLineWidth(line.getStyle());
      size = linew + (size - 1) * 0.1;

      if(line != null) {
         g.setStroke(line.getStroke(size));
      }
      else {
         g.setStroke(GLine.THIN_LINE.getStroke(size));
      }

      for(int i = 0; i < getShapeCount(); i++) {
         g.draw(getShape(i));
      }

      g.dispose();
   }

   /**
    * Get the shape count of the schema painter.
    */
   @Override
   @TernMethod
   public int getShapeCount() {
      return (shapes == null) ? 2 : shapes.length;
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
    * Check if supports frame.
    */
   @Override
   public boolean supportsFrame(VisualFrame frame) {
      return !(frame instanceof ShapeFrame || frame instanceof TextureFrame);
   }

   /**
    * Clone the shema painter.
    */
   @Override
   public Object clone() {
      StockPainter painter = (StockPainter) super.clone();

      if(shapes != null) {
         painter.shapes = (Shape[]) shapes.clone();
      }

      return painter;
   }

   private static final int LENGTH = 10;
   private static final long serialVersionUID = 1L;
   private Shape[] shapes;
}
