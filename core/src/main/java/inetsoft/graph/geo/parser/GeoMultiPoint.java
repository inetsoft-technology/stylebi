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
package inetsoft.graph.geo.parser;

import inetsoft.graph.geo.GeoShape;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Map shape that represents one or more related points.
 *
 * @author InetSoft Technology
 * @since  10.1
 */
class GeoMultiPoint extends GeoShape {
   /**
    * Creates a new instance of <tt>GeoMultiPoint</tt>.
    */
   public GeoMultiPoint() {
   }

   /**
    * Adds a point to this multi-point.
    *
    * @param point the point to add.
    */
   public void addPoint(Point2D point) {
      points.add(point);

      if(points.size() == 0) {
         setBounds(new Rectangle2D.Double(point.getX(), point.getY(), 0, 0));
      }
      else {
         getBounds().add(point);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected Shape getShape() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   private List<Point2D> points = new ArrayList<>();

   private static final long serialVersionUID = 1L;
}
