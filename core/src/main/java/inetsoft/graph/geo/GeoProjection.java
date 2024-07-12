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
package inetsoft.graph.geo;

import java.awt.geom.Point2D;
import java.io.Serializable;

/**
 * This defines the project algorithm of a map.
 *
 * @version 11.3
 * @author InetSoft Technology
 */
public abstract class GeoProjection implements Serializable {
   /**
    * Map longitude to x.
    */
   public double projectX(double x) {
      return x;
   }

   /**
    * Map latitude to x.
    */
   public double projectY(double y) {
      return y;
   }

   /**
    * Map x back to longitude.
    */
   public double inverseX(double x) {
      return x;
   }

   /**
    * Map y back to latitude.
    */
   public double inverseY(double y) {
      return y;
   }

   /**
    * Map the longitude/latitude to x/y.
    */
   public Point2D project(double x, double y) {
      return new Point2D.Double(projectX(x), projectY(y));
   }

   /**
    * Invert projection from x/y to lon/lat.
    */
   public Point2D inverse(double x, double y) {
      return new Point2D.Double(inverseX(x), inverseY(y));
   }

   /**
    * Get the minimum of the latitude in this projection.
    */
   public double getYMin() {
      return -90;
   }

   /**
    * Get the maximum of the latitude in this projection.
    */
   public double getYMax() {
      return 90;
   }

   /**
    * Get the minimum of the longitude in this projection.
    */
   public double getXMin() {
      return -180;
   }

   /**
    * Get the maximum of the longitude in this projection.
    */
   public double getXMax() {
      return 180;
   }
}
