/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.visual;

import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.Rect25Coord;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.geometry.ElementGeometry;
import inetsoft.graph.geometry.Geometry;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.GTool;

import java.awt.*;
import java.awt.geom.*;

/**
 * This visual object represents a 3d bar in a graphic output.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class Bar3DVO extends BarVO {
   /**
    * Create a visual object at 0,0 location.
    * @param coord the coordinate the visual object is plotted on.
    */
   public Bar3DVO(Geometry gobj, Coordinate coord) {
      super(gobj, coord);

      depth = ((Rect25Coord) coord).getDepth();
   }

   /**
    * Paint the bar shape.
    */
   @Override
   protected void paintBar(Graphics2D g2, Shape path, Color color,
                           GShape shape, GTexture fill, GLine line, Color lineColor)
   {
      color = applyAlpha(color);
      g2.setColor(color);

      if(fill == null) {
         if(paintRight) {
            paintRightShape(g2, GTool.darken(color), null, line, lineColor);
         }

         if(paintTop) {
            paintTopShape(g2, GTool.brighten(color), null, line, lineColor);
         }

         g2.fill(path);
      }
      else {
         // force a border around texture
         if(line == null) {
            line = new GLine(1);
         }

         if(paintRight) {
            paintRightShape(g2, GTool.darken(color), fill, line, lineColor);
         }

         if(paintTop) {
            paintTopShape(g2, GTool.brighten(color), fill, line, lineColor);
         }

         fill.paint(g2, path);
      }

      if(line != null || lineColor != null) {
         paintLine(g2, path, line, lineColor);
      }
   }

   /**
    * Get the width ratio for overlay.
    */
   @Override
   double getOverlayRatio() {
      return 1;
   }

   /**
    * Paint the top sdie shape.
    * @param g the graphics context to use for painting.
    * @param color the color.
    * @param fill the GTexture.
    */
   private void paintTopShape(Graphics2D g, Color color, GTexture fill,
                              GLine line, Color lineColor)
   {
      Rectangle2D rect = getTopShape();
      AffineTransform trans = new AffineTransform();
      trans.shear(1, 0);
      trans.translate(-(rect.getY() - rect.getHeight()), -rect.getHeight());
      Shape shape = trans.createTransformedShape(rect);

      Graphics2D g2 = (Graphics2D) g.create();
      g2.setColor(color);

      if(fill == null) {
         g2.fill(shape);
      }
      else {
         fill.paint(g2, shape);
      }

      if(line != null || lineColor != null) {
         paintLine(g2, shape, line, lineColor);
      }

      g2.dispose();
   }

   /**
    * Paint the right side shape.
    * @param g the graphics context to use for painting.
    * @param color the color.
    * @param fill the GTexture.
    */
   private void paintRightShape(Graphics2D g, Color color, GTexture fill,
                                GLine line, Color lineColor)
   {
      Rectangle2D rect = getRightShape();
      AffineTransform trans = new AffineTransform();
      trans.shear(0, 1);
      trans.translate(0, -rect.getX());
      Shape shape = trans.createTransformedShape(rect);

      Graphics2D g2 = (Graphics2D) g.create();
      g2.setColor(color);

      if(fill == null) {
         g2.fill(shape);
      }
      else {
         fill.paint(g2, shape);
      }

      if(line != null || lineColor != null) {
         paintLine(g2, shape, line, lineColor);
      }

      g2.dispose();
   }

   /**
    * Get the top side shape.
    * @return the top side shape.
    */
   private Rectangle2D getTopShape() {
      Rectangle2D rect = getPath0(true).getBounds2D();

      return new Rectangle2D.Double(rect.getX(),
                                    rect.getY() + rect.getHeight() + depth,
                                    rect.getWidth(), depth);
   }

   /**
    * Get the rigth side shape.
    * @return the right side shape.
    */
   private Rectangle2D getRightShape() {
      Rectangle2D rect = getPath0(true).getBounds2D();

      return new Rectangle2D.Double(rect.getX() + rect.getWidth(),
                                    rect.getY(), depth, rect.getHeight());
   }

   /**
    * Make the visual object overlay another visual object.
    * @param vo the specified visual object to be overlaid.
    */
   @Override
   public void overlay(VisualObject vo) {
      Rectangle2D bounds0 = ((ElementVO) vo).getShapes()[0].getBounds2D();
      Rectangle2D bounds = shape.getBounds2D();

      paintTop = bounds0.getHeight() <= bounds.getHeight() + 0.1;
      paintRight = bounds0.getWidth() <= bounds.getWidth() + 0.1;

      super.overlay(vo);
   }

   /**
    * Calculate the bar size.
    */
   @Override
   protected double getBarSize(Coordinate coord, double size) {
      double barsize = super.getBarSize(coord, size);
      double maxbar = coord.getMaxWidth();

      // If bar size is the same as max bar size, it is difficult to distinguish
      // from other 3d bar.
      if(Math.abs(barsize - maxbar) < 0.00001) {
         barsize = barsize - 2;
      }

      return barsize;
   }

   /**
    * Correct overlay sides.
    */
   @Override
   public void layoutCompleted(Coordinate coord) {
      super.layoutCompleted(coord);
      
      if(!GTool.isHorizontal(coord.getCoordTransform())) {
         boolean t = paintTop;
         paintTop = paintRight;
         paintRight = t;
      }
   }

   @Override
   void layoutText0(VGraph vgraph, int placement, Rectangle2D box,
                    boolean negative, VOText text)
   {
      super.layoutText0(vgraph, placement, box, negative, text);
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      GraphElement elem = gobj.getElement();

      // move label so it doesn't overlap with bar (the 3d part) (49507).
      if(!negative && !elem.isStack()) {
         Point2D pos = text.getPosition();

         if(text.getCollisionModifier() == VLabel.MOVE_UP) {
            double y = pos.getY() + getTopShape().getBounds2D().getHeight();
            text.setPosition(new Point2D.Double(pos.getX(), y));
         }
         else {
            double x = pos.getX() + getRightShape().getBounds2D().getWidth();
            text.setPosition(new Point2D.Double(x, pos.getY()));
         }
      }
   }

   private double depth;
   private boolean paintTop = true;
   private boolean paintRight = true;
}
