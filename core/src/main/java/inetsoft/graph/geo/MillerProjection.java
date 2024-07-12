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

/**
 * Miller projection.
 *
 * @version 11.3
 * @author InetSoft Technology
 */
public class MillerProjection extends GeoProjection {
   /**
    * Map the longitude/latitude to x/y.
    */
   @Override
   public double projectY(double y) {
      y = 1.25 * Math.log(Math.tan(Math.PI / 4 + 0.8 * Math.toRadians(y) / 2));
      y = Math.toDegrees(y);
      return y;
   }

   @Override
   public double inverseY(double y) {
      y = (5 / 4.0) * Math.atan(Math.sinh(4 * Math.toRadians(y) / 5));
      y = Math.toDegrees(y);
      return y;
   }
}
