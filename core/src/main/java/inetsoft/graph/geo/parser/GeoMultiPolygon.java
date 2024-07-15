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
package inetsoft.graph.geo.parser;

import inetsoft.graph.geo.GeoShape;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Map shape that represents one or more grouped polygons.
 *
 * @author InetSoft Technology
 * @since  10.1
 */
class GeoMultiPolygon extends GeoShape {
   /**
    * Creates a new instance of <tt>GeoMultiPolygon</tt>.
    */
   public GeoMultiPolygon() {
   }

   /**
    * Adds a polygon to this multi-polygon.
    * @param polygon the polygon to add.
    * @param rawBounds true if using the bounds as-is, otherwise check for bounds across
    *                  date line and have the bounds reflecting the real shape size.
    */
   public void addPolygon(GeoPolygon polygon, boolean rawBounds) {
      polygons.add(polygon);

      if(polygons.size() == 1) {
         setBounds(polygon.getBounds());
      }
      else {
         Rectangle2D b1 = getBounds();
         Rectangle2D b2 = polygon.getBounds();

         // using bounds as-is is produces more balanced (centered) world map. (52243)
         if(!rawBounds) {
            // alaska's west most point x is > 0 (west of date line) and east most point is < 0
            // (east of date line). if we just union the shapes, we will get a range of x from
            // -179 to 179, which is the entire earth. (52065)
            if(b1.getX() > 0 && b2.getX() < 0) {
               b1 = new Rectangle2D.Double(b1.getX() - 360, b1.getY(), b1.getWidth(),
                                           b1.getHeight());
            }
            else if(b2.getX() > 0 && b1.getX() < 0) {
               b2 = new Rectangle2D.Double(b2.getX() - 360, b2.getY(), b2.getWidth(),
                                           b2.getHeight());
            }
         }

         b1.add(b2);
         setBounds(b1);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected Shape getShape() {
      GeneralPath path = new GeneralPath(GeneralPath.WIND_EVEN_ODD);

      for(GeoPolygon polygon : polygons) {
         path.append(polygon.getShape(), false);
      }

      return path;
   }

   private List<GeoPolygon> polygons = new ArrayList<>();

   private static final long serialVersionUID = 1L;
}
