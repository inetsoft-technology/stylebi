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
package inetsoft.report;

/**
 * This class defines a position as inches.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class Position implements java.io.Serializable, Cloneable {
   /**
    * Create a position.
    */
   public Position() {
      this(0, 0);
   }

   /**
    * Create a copy of the position.
    */
   public Position(Position pos) {
      this(pos.x, pos.y);
   }

   /**
    * Create a Position of specified dimension.
    * @param x x position in inches.
    * @param y y position in inches.
    */
   public Position(float x, float y) {
      this.x = x;
      this.y = y;
   }

   /**
    * Create a Position of specified dimension.
    * @param x x position in inches.
    * @param y y position in inches.
    */
   public Position(double x, double y) {
      this((float) x, (float) y);
   }

   public String toString() {
      return "Position[" + x + "," + y + "]";
   }

   public boolean equals(Object obj) {
      if(obj instanceof Position) {
         return x == ((Position) obj).x && y == ((Position) obj).y;
      }

      return false;
   }

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      return new Position(x, y);
   }

   /**
    * X in inches.
    */
   public float x;
   /**
    * Y in inches.
    */
   public float y;
}

