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
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Map shape that represents a single point.
 *
 * @author InetSoft Technology
 * @since  10.1
 */
class GeoPoint extends GeoShape {
   /**
    * Creates a new instance of <tt>GeoPoint</tt>.
    */
   public GeoPoint() {
   }

   /**
    * Gets the coordinates of this point.
    *
    * @return the coordinates.
    */
   public Point2D getPoint() {
      return point;
   }

   /**
    * Sets the coordinates of this point.
    *
    * @param point the coordinates.
    */
   public void setPoint(Point2D point) {
      this.point = point;
      setBounds(new Rectangle2D.Double(point.getX(), point.getY(), 0.0, 0.0));
      setPrimaryAnchor(point);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected Shape getShape() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   private Point2D point = null;

   private static final long serialVersionUID = 1L;
}
