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
package inetsoft.graph.mxgraph.view;

import inetsoft.graph.mxgraph.util.mxPoint;

/**
 * Defines an object that contains the constraints about how to connect one
 * side of an edge to its terminal.
 */
public class mxConnectionConstraint {
   /**
    * Point that specifies the fixed location of the connection point.
    */
   protected mxPoint point;

   /**
    * Boolean that specifies if the point should be projected onto the perimeter
    * of the terminal.
    */
   protected boolean perimeter;

   /**
    * Constructs an empty connection constraint.
    */
   public mxConnectionConstraint()
   {
      this(null);
   }

   /**
    * Constructs a connection constraint for the given point.
    */
   public mxConnectionConstraint(mxPoint point)
   {
      this(point, true);
   }

   /**
    * Constructs a new connection constraint for the given point and boolean
    * arguments.
    *
    * @param point     Optional mxPoint that specifies the fixed location of the point
    *                  in relative coordinates. Default is null.
    * @param perimeter Optional boolean that specifies if the fixed point should be
    *                  projected onto the perimeter of the terminal. Default is true.
    */
   public mxConnectionConstraint(mxPoint point, boolean perimeter)
   {
      setPoint(point);
      setPerimeter(perimeter);
   }

   /**
    * Returns the point.
    */
   public mxPoint getPoint()
   {
      return point;
   }

   /**
    * Sets the point.
    */
   public void setPoint(mxPoint value)
   {
      point = value;
   }

   /**
    * Returns perimeter.
    */
   public boolean isPerimeter()
   {
      return perimeter;
   }

   /**
    * Sets perimeter.
    */
   public void setPerimeter(boolean value)
   {
      perimeter = value;
   }

}
