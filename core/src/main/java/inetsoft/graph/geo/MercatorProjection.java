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
package inetsoft.graph.geo;

/**
 * Mercator projection.
 *
 * @version 11.3
 * @author InetSoft Technology
 */
public class MercatorProjection extends GeoProjection {
   /**
    * Map the longitude/latitude to x/y.
    */
   @Override
   public double projectY(double y) {
      y = Math.log(Math.tan(Math.PI / 4 + Math.toRadians(y) / 2));
      y = Math.toDegrees(y);
      return y;
   }

   @Override
   public double inverseY(double y) {
      y = 2 * Math.atan(Math.exp(Math.toRadians(y))) - Math.PI / 2;
      y = Math.toDegrees(y);
      return y;
   }

   /**
    * Get the minimum of the latitude in this projection.
    */
   @Override
   public double getYMin() {
      return -89.5;
   }

   /**
    * Get the maximum of the latitude in this projection.
    */
   @Override
   public double getYMax() {
      return 89.5;
   }
}
