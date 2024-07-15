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
package inetsoft.graph.internal;

import java.awt.geom.*;

/**
 * The donut class can be used to create a donut, which is a hollow ellipse.
 * It can also be used to represent a slice of a donut.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class Donut extends Area {
   /**
    * Create a fully enclosed donut.
    * @param x the x position of the outer ellipse.
    * @param y the y position of the outer ellipse.
    * @param w the width of the outer ellipse.
    * @param h the height of the outer ellipse.
    * @param w2 the width of the inner ellipse.
    * @param h2 the height of the inner ellipse.
    */
   public Donut(double x, double y, double w, double h, double w2, double h2) {
      super(new Ellipse2D.Double(x, y, w, h));

      outerArc = new Arc2D.Double(x, y, w, h, 0, 360, Arc2D.PIE);
      double x2 = x + (w - w2) / 2;
      double y2 = y + (h - h2) / 2;
      innerArc = new Arc2D.Double(x2, y2, w2, h2, 0, 360, Arc2D.PIE);

      if(w2 > 0 || h2 > 0) {
         subtract(new Area(new Ellipse2D.Double(x2, y2, w2, h2)));
      }
   }

   /**
    * Create a slice of a donut.
    * @param x the x position of the outer ellipse.
    * @param y the y position of the outer ellipse.
    * @param w the width of the outer ellipse.
    * @param h the height of the outer ellipse.
    * @param w2 the width of the inner ellipse.
    * @param h2 the height of the inner ellipse.
    * @param angleStart starting angle in degrees.
    * @param angleExtent angle extent in degrees.
    */
   public Donut(double x, double y, double w, double h, double w2, double h2,
                double angleStart, double angleExtent)
   {
      // rounding out so adjacent shapes don't have a slight gap. (58887)
      double left = Math.floor(x);
      double right = Math.ceil(x + w);
      double bottom = Math.floor(y);
      double top = Math.ceil(y + h);

      x = left;
      y = bottom;
      w = right - left;
      h = top - bottom;
      w2 = Math.round(w2);
      h2 = Math.round(h2);
      double x2 = x + (w - w2) / 2;
      double y2 = y + (h - h2) / 2;

      if(Math.abs(angleExtent) >= 360) {
         add(new Area(new Ellipse2D.Double(x, y, w, h)));
         subtract(new Area(new Ellipse2D.Double(x2, y2, w2, h2)));
      }
      else {
         Path2D.Double path = new Path2D.Double();

         Arc2D.Double outerArc = new Arc2D.Double(x, y, w, h, angleStart, angleExtent, Arc2D.CHORD);
         Arc2D.Double innerArc = new Arc2D.Double(x2, y2, w2, h2, angleStart + angleExtent,
                                                  -angleExtent, Arc2D.CHORD);

         Point2D first = addPath(path, outerArc.getPathIterator(null), false);
         addPath(path, innerArc.getPathIterator(null), true);
         path.lineTo(first.getX(), first.getY());
         add(new Area(path));
      }

      outerArc = new Arc2D.Double(x, y, w, h, angleStart, angleExtent, Arc2D.PIE);
      innerArc = new Arc2D.Double(x2, y2, w2, h2, angleStart, angleExtent, Arc2D.PIE);
   }

   private Point2D addPath(Path2D.Double path, PathIterator iter1, boolean continuation) {
      double[] point = new double[6];
      Point2D firstPt = null;

      while(!iter1.isDone()) {
         int segmentType = iter1.currentSegment(point);

         switch(segmentType) {
         case PathIterator.SEG_MOVETO:
            if(continuation) {
               path.lineTo(point[0], point[1]);
            }
            else {
               path.moveTo(point[0], point[1]);
            }

            if(firstPt == null) {
               firstPt = new Point2D.Double(point[0], point[1]);
            }
            break;
         case PathIterator.SEG_LINETO:
            path.lineTo(point[0], point[1]);
            break;
         case PathIterator.SEG_QUADTO:
            path.quadTo(point[0], point[1], point[2], point[3]);
            break;
         case PathIterator.SEG_CUBICTO:
            path.curveTo(point[0], point[1], point[2], point[3], point[4], point[5]);
            break;
         case PathIterator.SEG_CLOSE:
         default:
         }

         iter1.next();
      }

      return firstPt;
   }

   /**
    * Get the outer arc.
    */
   public Arc2D getOuterArc() {
      return outerArc;
   }

   /**
    * Get the inner arc.
    */
   public Arc2D getInnerArc() {
      return innerArc;
   }

   /**
    * Get the starting point of the the inner arc.
    */
   public Point2D getInnerStartPoint() {
      return innerArc.getStartPoint();
   }

   /**
    * Get the ending point of the the inner arc.
    */
   public Point2D getInnerEndPoint() {
      return innerArc.getEndPoint();
   }

   /**
    * Get the starting point of the the outer arc.
    */
   public Point2D getOuterStartPoint() {
      return outerArc.getStartPoint();
   }

   /**
    * Get the ending point of the the outer arc.
    */
   public Point2D getOuterEndPoint() {
      return outerArc.getEndPoint();
   }

   /**
    * Get the central point in the donut arc.
    */
   public Point2D getCentroid() {
      Arc2D outerArc = (Arc2D) this.outerArc.clone();
      Arc2D innerArc = (Arc2D) this.innerArc.clone();

      outerArc.setAngleExtent(outerArc.getAngleExtent() / 2);
      innerArc.setAngleExtent(innerArc.getAngleExtent() / 2);

      Point2D p1 = outerArc.getEndPoint();
      Point2D p2 = innerArc.getEndPoint();

      return new Point2D.Double((p1.getX() + p2.getX()) / 2, (p1.getY() + p2.getY()) / 2);
   }

   @Override
   public String toString() {
      return super.toString() + "(" + outerArc.getX() + "," + outerArc.getY() + ": " +
         outerArc.getWidth() + "x" + outerArc.getHeight() + ":" + innerArc.getWidth() + "x" +
         innerArc.getHeight() + ":" +
         outerArc.getAngleStart() + "," + outerArc.getAngleExtent() + ")";
   }

   private Arc2D outerArc;
   private Arc2D innerArc;
}
