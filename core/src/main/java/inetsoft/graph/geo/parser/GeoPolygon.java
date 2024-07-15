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
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Map shape that represents a single polygon. A polygon consists of a shell and
 * zero or more holes.
 *
 * @author InetSoft Technology
 * @since  10.1
 */
class GeoPolygon extends GeoShape {
   /**
    * Creates a new instance of <tt>GeoPolygon</tt>.
    */
   public GeoPolygon() {
   }

   /**
    * Sets the shell (exterior ring) of this polygon.
    *
    * @param shell the points of the shell.
    */
   public void setShell(List<Point2D> shell) {
      setShell(shell.toArray(new Point2D[0]));
   }

   /**
    * Sets the shell (exterior ring) of this polygon.
    *
    * @param shell the points of the shell.
    */
   public void setShell(Point2D[] shell) {
      this.shell = shell;
      setBounds(calculateBounds(shell));
   }

   /**
    * Adds a hole (interior ring) to this polygon.
    *
    * @param hole the points of the hole.
    */
   public void addHole(List<Point2D> hole) {
      addHole(hole.toArray(new Point2D[0]));
   }

   /**
    * Adds a hole (interior ring) to this polygon.
    *
    * @param hole the points of the hole.
    */
   public void addHole(Point2D[] hole) {
      holes.add(hole);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected Shape getShape() {
      GeneralPath path = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
      path.append(createRing(shell), false);

      for(Point2D[] hole : holes) {
         path.append(createRing(hole), false);
      }

      return path;
   }

   /**
    * Creates the path for a ring.
    *
    * @param coords the coordinates of the ring.
    */
   private GeneralPath createRing(Point2D[] coords) {
      GeneralPath path = new GeneralPath(GeneralPath.WIND_EVEN_ODD);

      for(int i = 0; i < coords.length; i++) {
         if(i == 0) {
            path.moveTo((float) coords[i].getX(), (float) coords[i].getY());
         }
         else {
            path.lineTo((float) coords[i].getX(), (float) coords[i].getY());
         }
      }

      path.closePath();
      return path;
   }

   @Override
   protected boolean isAntiAlias() {
      if(holes.size() > 0) {
         return true;
      }

      if(shell != null) {
         for(int i = 1; i < shell.length; i++) {
            if(shell[i].getX() != shell[i - 1].getX() && shell[i].getY() != shell[i - 1].getY()) {
               return true;
            }
         }
      }

      return false;
   }

   private Point2D[] shell = null;
   private List<Point2D[]> holes = new ArrayList<>();

   private static final long serialVersionUID = 1L;
}
