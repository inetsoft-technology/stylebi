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
package inetsoft.report.internal;

import java.awt.*;
import java.awt.geom.*;

/**
 * A shape describes multiple lines.
 */
public class PolylineShape implements Shape {
   /**
    * Create a polyline shape.
    */
   public PolylineShape(float[] xPoints, float[] yPoints, int nPoints) {
      this.xPoints = xPoints;
      this.yPoints = yPoints;
      this.nPoints = nPoints;
   }

   /**
    * Get the x positions of points.
    */
   public float[] getXPoints() {
      return xPoints;
   }

   /**
    * Get the y positions of points.
    */
   public float[] getYPoints() {
      return yPoints;
   }

   /**
    * Get the number of points.
    */
   public int getPointCount() {
      return nPoints;
   }

   /**
    * Find the bounding box of the shape.
    */
   @Override
   public Rectangle getBounds() {
      Rectangle2D box = getBounds2D();

      return new Rectangle((int) box.getX(), (int) box.getY(),
         (int) box.getWidth(), (int) box.getHeight());
   }

   /**
    * Find the bounding box of the shape.
    */
   @Override
   public Rectangle2D getBounds2D() {
      float top = Integer.MAX_VALUE;
      float left = Integer.MAX_VALUE;
      float bottom = 0;
      float right = 0;

      for(int i = 0; i < nPoints; i++) {
         top = Math.min(top, yPoints[i]);
         left = Math.min(left, xPoints[i]);
         bottom = Math.max(bottom, yPoints[i]);
         right = Math.max(right, yPoints[i]);
      }

      return new Rectangle2D.Float(left, top, right - left, bottom - top);
   }

   /**
    * Check if a point is contained in this shape.
    */
   @Override
   public boolean contains(double x, double y) {
      return false;
   }

   /**
    * Check if a point is contained in this shape.
    */
   @Override
   public boolean contains(Point2D p) {
      return false;
   }

   /**
    * Check if this shape intersect a shape.
    */
   @Override
   public boolean intersects(double x, double y, double w, double h) {
      return getBounds2D().intersects(new Rectangle2D.Double(x, y, w, h));
   }

   /**
    * Check if this shape intersect a rectangle.
    */
   @Override
   public boolean intersects(Rectangle2D r) {
      return getBounds2D().intersects(r);
   }

   /**
    * Check if this shape contains a shape.
    */
   @Override
   public boolean contains(double x, double y, double w, double h) {
      return false;
   }

   /**
    * Check if this shape contains a shape.
    */
   @Override
   public boolean contains(Rectangle2D r) {
      return false;
   }

   /**
    * Return an iterator to traverse the path.
    */
   @Override
   public PathIterator getPathIterator(AffineTransform at) {
      return new Iterator(at);
   }

   /**
    * Return an iterator to traverse the path.
    */
   @Override
   public PathIterator getPathIterator(AffineTransform at, double flatness) {
      return getPathIterator(at);
   }

   class Iterator implements PathIterator {
      public Iterator(AffineTransform affine) {
         this.affine = affine;
      }

      @Override
      public int getWindingRule() {
         return WIND_NON_ZERO;
      }

      @Override
      public boolean isDone() {
         return idx >= nPoints;
      }

      @Override
      public void next() {
         idx++;
      }

      @Override
      public int currentSegment(float[] coords) {
         coords[0] = xPoints[idx];
         coords[1] = yPoints[idx];

         if(affine != null) {
            affine.transform(coords, 0, coords, 0, 1);
         }

         return SEG_LINETO;
      }

      @Override
      public int currentSegment(double[] coords) {
         coords[0] = xPoints[idx];
         coords[1] = yPoints[idx];

         if(affine != null) {
            affine.transform(coords, 0, coords, 0, 1);
         }

         return SEG_LINETO;
      }

      int idx = 0;
      AffineTransform affine;
   }

   float[] xPoints; // x positions
   float[] yPoints; // y positions
   int nPoints; // number of points
}

