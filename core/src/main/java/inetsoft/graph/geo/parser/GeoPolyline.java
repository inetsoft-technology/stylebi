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
package inetsoft.graph.geo.parser;

import inetsoft.graph.geo.GeoShape;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Map shape that represents one or more polylines.
 *
 * @author InetSoft Technology
 * @since  10.1
 */
class GeoPolyline extends GeoShape {
   /**
    * Creates a new instance of <tt>GeoPolyline</tt>.
    */
   public GeoPolyline() {
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected Shape getShape() {
      GeneralPath path = new GeneralPath();

      for(Point2D[] coords : segments) {
         for(int i = 0; i < coords.length; i++) {
            if(i == 0) {
               path.moveTo((float) coords[i].getX(), (float) coords[i].getY());
            }
            else {
               path.lineTo((float) coords[i].getX(), (float) coords[i].getY());
            }
         }
      }

      return path;
   }

   /**
    * Adds a disjoint segment to this polyline.
    *
    * @param coordinates the line coordinates.
    */
   public void addSegment(List<Point2D> coordinates) {
      addSegment(coordinates.toArray(new Point2D[0]));
   }

   /**
    * Adds a disjoint segment to this polyline.
    *
    * @param coordinates the line coordinates.
    */
   public void addSegment(Point2D[] coordinates) {
      segments.add(coordinates);

      if(segments.size() == 1) {
         setBounds(calculateBounds(coordinates));
      }
      else {
         getBounds().add(calculateBounds(coordinates));
      }
   }

   @Override
   public boolean isFill() {
      return false;
   }

   @Override
   protected boolean isAntiAlias() {
      return segments.stream().anyMatch(pts -> {
            for(int i = 1; i < pts.length; i++) {
               if(pts[i].getX() != pts[i - 1].getX() && pts[i].getY() != pts[i - 1].getY()) {
                  return true;
               }
            }

            return false;
         });
   }

   private List<Point2D[]> segments = new ArrayList<>();

   private static final long serialVersionUID = 1L;
}
