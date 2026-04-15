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
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.geometry.SchemaGeometry;
import inetsoft.graph.internal.GTool;

import java.awt.*;
import java.awt.geom.*;

/**
 * This schema painter renders the box-plot chart points. Five variables are
 * required for box plot, in the following order: high, upper-quantile,
 * median, lower-quantile, low. Outliners are not supported in this
 * implementation.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=setPainter")
public class BoxPainter extends SchemaPainter {
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
      Point2D upper = coord.getPosition(getSubTuple(1, tuple, vars, hasX));
      Point2D median = coord.getPosition(getSubTuple(2, tuple, vars, hasX));
      Point2D lower = coord.getPosition(getSubTuple(3, tuple, vars, hasX));
      Point2D low = coord.getPosition(getSubTuple(4, tuple, vars, hasX));
      double size = geometry.getSize(0);
      SizeFrame sizes = geometry.getVisualModel().getSizeFrame();
      double width = getSize(coord, size, sizes);
      double end = width / 4;

      shapes = new Shape[getShapeCount()];

      // center vertical line
      shapes[0] = new Line2D.Double(high.getX(), high.getY(),
                                     low.getX(), low.getY());
      // top horizontal line
      shapes[1] = new Line2D.Double(high.getX() - end, high.getY(),
                                     high.getX() + end, high.getY());
      // bottom horizontal line
      shapes[2] = new Line2D.Double(low.getX() - end, low.getY(),
                                     low.getX() + end, low.getY());
      // box
      shapes[3] = new Rectangle2D.Double(lower.getX(), lower.getY(), width,
                                         upper.getY() - lower.getY());
      shapes[3] = GTool.centerShape(shapes[3], true);
      // median line
      shapes[4] = new Line2D.Double(lower.getX() - width/2, median.getY(),
                                    lower.getX() + width/2, median.getY());
   }

   /**
    * Check if supports frame.
    */
   @Override
   public boolean supportsFrame(VisualFrame frame) {
      return !(frame instanceof ShapeFrame || frame instanceof TextureFrame);
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
            shapes[i] = new Line2D.Double(p1.getX(), p1.getY(), p2.getX(), p2.getY());
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
      GraphElement elem = geometry.getElement();
      Color borderColor = elem.getBorderColor();

      if(borderColor != null && elem.getColorFrame().getField() == null) {
         color = borderColor;
      }

      g = (Graphics2D) g.create();
      g.setColor(color);

      if(line != null) {
         g.setStroke(line.getStroke());
      }

      Stroke baseStroke = g.getStroke();
      float baseWidth = baseStroke instanceof BasicStroke ? ((BasicStroke) baseStroke).getLineWidth() : 1f;
      Stroke thickStroke = new BasicStroke(baseWidth * 2.0f,
                                           BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);

      for(int i = 0; i < getShapeCount(); i++) {
         if(getShape(i) instanceof Line2D) {
            if(i == 1 || i == 2) {
               // @see CandlePainter
               g.setStroke(thickStroke);
               g.draw(new GeneralPath(getShape(i)));
               g.setStroke(baseStroke);
            }
            else if(i == 4) {
               // Median — white pill, double stroke width, inset from box edges, rounded ends.
               Line2D med = (Line2D) getShape(i);
               float strokeWidth = g.getStroke() instanceof BasicStroke
                  ? ((BasicStroke) g.getStroke()).getLineWidth() : 1f;
               float medH = Math.max(strokeWidth * 2, 3f);
               float inset = medH * 2;
               float x = (float) Math.min(med.getX1(), med.getX2()) + inset;
               float y = (float) med.getY1() - medH / 2;
               float w = (float) Math.abs(med.getX2() - med.getX1()) - inset * 2;

               if(w > 0) {
                  g.setColor(Color.WHITE);
                  g.fill(new RoundRectangle2D.Float(x, y, w, medH, medH, medH));
                  g.setColor(color);
               }
            }
            else {
               // Center whisker line (shape 0).
               g.setStroke(thickStroke);
               g.draw(new GeneralPath(getShape(i)));
               g.setStroke(baseStroke);
            }
         }
         else {
            // Box — build a RoundRectangle2D from the screen-space bounds so the arc radius
            // is in pixels, not chart units. Computing the arc in init() (chart coords) causes
            // the transform to distort it: a uniform arc becomes a tall narrow ellipse when the
            // Y axis has a larger pixel-per-unit scale than X, rounding top/bottom but not sides.
            Rectangle2D b = getShape(i).getBounds2D();
            double shortDim = Math.min(b.getWidth(), b.getHeight());
            double arc = shortDim / 3.0;
            Shape box = new RoundRectangle2D.Double(b.getX(), b.getY(),
                                                    b.getWidth(), b.getHeight(), arc, arc);

            // Intentional visual change: fill the box with the series color rather than white.
            // This gives box plots a modern filled appearance consistent with the series palette.
            // The median is rendered as a white pill (see shape index 4 above) to remain visible
            // against the colored fill.
            if(texture == null) {
               g.fill(box);
            }
            else {
               texture.paint(g, box);
            }

            g.draw(box);
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
      return 5;
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
      BoxPainter painter = (BoxPainter) super.clone();

      if(shapes != null) {
         painter.shapes = (Shape[]) shapes.clone();
      }

      return painter;
   }

   @Override
   protected double getBaseWidth(double maxw) {
      return maxw / 3;
   }

   private Shape[] shapes;
   private static final long serialVersionUID = 1L;
}
