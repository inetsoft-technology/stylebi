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
package inetsoft.graph.geo;

import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.GeoCoord;

import java.awt.*;
import java.awt.geom.*;
import java.io.Serializable;

/**
 * Base class for all map shape types. This class represent an area on a map,
 * which may consist of multiple shapes.
 *
 * @author InetSoft Technology
 * @since  10.1
 */
public abstract class GeoShape implements Cloneable, Serializable {
   /**
    * Get the bounding rectangle for this shape.
    * @return the bounding rectangle.
    */
   public Rectangle2D getBounds() {
      return bounds;
   }

   /**
    * Set the bounding rectangle for this shape.
    * @param bounds the bounding rectangle.
    */
   public void setBounds(Rectangle2D bounds) {
      this.bounds = bounds;
   }

   /**
    * Get the primary anchor for this feature. The anchor is the coordinates
    * at which any label or other decorations should be placed.
    * @return the primary anchor.
    */
   public Point2D getPrimaryAnchor() {
      if(primaryAnchor == null) {
         primaryAnchor = new Point2D.Double(bounds.getCenterX(),
                                            bounds.getCenterY());
      }

      return primaryAnchor;
   }

   /**
    * Set the primary anchor for this feature.
    * @param primaryAnchor the primary anchor.
    */
   public void setPrimaryAnchor(Point2D primaryAnchor) {
      this.primaryAnchor = primaryAnchor;
   }

   /**
    * Get the secondary anchor for this feature. A secondary anchor is that
    * which should be used if the label or decoration does not fit at the
    * primary anchor.
    * @return the secondary anchor or <tt>null</tt> if none has been defined.
    */
   public Point2D getSecondaryAnchor() {
      return secondaryAnchor;
   }

   /**
    * Set the secondary anchor for this feature.
    * @param secondaryAnchor the secondary anchor.
    */
   public void setSecondaryAnchor(Point2D secondaryAnchor) {
      this.secondaryAnchor = secondaryAnchor;
   }

   /**
    * Get the style class of this shape.
    */
   public String getStyle() {
      return style;
   }

   /**
    * Set the style class of this shape. If the style is set, the shape gets
    * the line style and fill color from the GeoShapeFrame.
    */
   public void setStyle(String style) {
      this.style = style;
   }

   /**
    * Get the label of the shape. If the label is defined, the label will be
    * drawn centered on the primary anchor.
    */
   public String getLabel() {
      return label;
   }

   /**
    * Set the label for this shape.
    */
   public void setLabel(String label) {
      this.label = label;
   }

   /**
    * Get the AWT shape used to draw this shape.
    * @return the AWT shape.
    */
   protected abstract Shape getShape();

   /**
    * Check if the outer border is drawn.
    */
   public boolean isOutline() {
      return true;
   }

   /**
    * Check if the shape should be filled.
    */
   public boolean isFill() {
      return true;
   }

   /**
    * Get the AWT shape used to draw this shape.
    * @param coord the coordinate transform.
    * @return the AWT shape.
    */
   public Shape getShape(Coordinate coord) {
      GeneralPath path = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
      Shape s = getShape();

      GeoCoord.startShape(coord, bounds.getMinX(), bounds.getMaxX());

      PathIterator pi = new CoordPathIterator(s.getPathIterator(null), coord);
      path.append(pi, false);

      GeoCoord.endShape();
      return path;
   }

   /**
    * Check if the shape should be painted with anti-aliasing on.
    */
   protected boolean isAntiAlias() {
      return true;
   }

   /**
    * Calculate the bounding rectangle for a set of points.
    * @param coordinates the coordinates.
    * @return the bounding rectangle.
    */
   protected Rectangle2D calculateBounds(Point2D[] coordinates) {
      double minx = Double.POSITIVE_INFINITY;
      double miny = Double.POSITIVE_INFINITY;
      double maxx = Double.NEGATIVE_INFINITY;
      double maxy = Double.NEGATIVE_INFINITY;

      for(Point2D pt : coordinates) {
         minx = Math.min(pt.getX(), minx);
         miny = Math.min(pt.getY(), miny);
         maxx = Math.max(pt.getX(), maxx);
         maxy = Math.max(pt.getY(), maxy);
      }

      return new Rectangle2D.Double(minx, miny, maxx - minx, maxy - miny);
   }

   private static final class CoordPathIterator implements PathIterator {
      public CoordPathIterator(PathIterator path, Coordinate coord) {
         this.path = path;
         this.coord = coord;
      }

      @Override
      public int getWindingRule() {
         return path.getWindingRule();
      }

      @Override
      public boolean isDone() {
         return path.isDone();
      }

      @Override
      public void next() {
         path.next();
      }

      @Override
      public int currentSegment(float[] coords) {
         int type = path.currentSegment(coords);

         switch(type) {
         case PathIterator.SEG_MOVETO:
         case PathIterator.SEG_LINETO:
            transform(coords, 0);
            break;

         case SEG_QUADTO:
            transform(coords, 0);
            transform(coords, 2);
            break;

         case SEG_CUBICTO:
            transform(coords, 0);
            transform(coords, 2);
            transform(coords, 4);
            break;
         }

         return type;
      }

      @Override
      public int currentSegment(double[] coords) {
         int type = path.currentSegment(coords);

         switch(type) {
         case PathIterator.SEG_MOVETO:
         case PathIterator.SEG_LINETO:
            transform(coords, 0);
            break;

         case SEG_QUADTO:
            transform(coords, 0);
            transform(coords, 2);
            break;

         case SEG_CUBICTO:
            transform(coords, 0);
            transform(coords, 2);
            transform(coords, 4);
            break;
         }

         return type;
      }

      private void transform(float[] coords, int offset) {
         double[] tuple = { coords[offset], (double) coords[offset + 1] };
         Point2D point = coord.getPosition(tuple);
         coords[offset] = (float) point.getX();
         coords[offset + 1] = (float) point.getY();
      }

      private void transform(double[] coords, int offset) {
         double[] tuple = { coords[offset], coords[offset + 1] };
         Point2D point = coord.getPosition(tuple);
         coords[offset] = point.getX();
         coords[offset + 1] = point.getY();
      }

      private final PathIterator path;
      private final Coordinate coord;
   }

   private Rectangle2D bounds = new Rectangle2D.Double();
   private Point2D primaryAnchor = null;
   private Point2D secondaryAnchor = null;
   private String style;
   private String label;
}
