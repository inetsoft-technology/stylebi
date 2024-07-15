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
package inetsoft.util;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;

/**
 * Double ellipse2d implementation.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class DEllipse2D extends Ellipse2D implements Cloneable, Serializable {
   /**
    * Constructor.
    */
   public DEllipse2D() {
      super();
   }

   /**
    * Constructor.
    */
   public DEllipse2D(double x, double y, double w, double h) {
      this();

      setFrame(x, y, w, h);
   }

   /**
    * Constructor.
    */
   public DEllipse2D(Ellipse2D.Double e2dd) {
      this(e2dd.x, e2dd.y, e2dd.width, e2dd.height);
   }

   /**
    * Sets the location and size of this DEllipse2D.
    */
   @Override
   public void setFrame(double x, double y, double w, double h) {
      this.x = x;
      this.y = y;
      this.width = w;
      this.height = h;
   }

   /**
    * Get the x value.
    */
   @Override
   public double getX() {
      return x;
   }

   /**
    * Get the y value.
    */
   @Override
   public double getY() {
      return y;
   }

   /**
    * Get the width value.
    */
   @Override
   public double getWidth() {
      return width;
   }

   /**
    * Get the height value.
    */
   @Override
   public double getHeight() {
      return height;
   }

   /**
    * Get the rectangle value.
    */
   @Override
   public Rectangle2D getBounds2D() {
      return new DRectangle(x, y, width, height);
   }

   /**
    * Check if is empty.
    */
   @Override
   public boolean isEmpty() {
      return (width <= 0.0) || (height <= 0.0);
   }

   /**
    * Returns the hashcode.
    */
   public int hashCode() {
       long bits = java.lang.Double.doubleToLongBits(x);
       bits += java.lang.Double.doubleToLongBits(y) * 37;
       bits += java.lang.Double.doubleToLongBits(width) * 43;
       bits += java.lang.Double.doubleToLongBits(height) * 47;

       return (((int) bits) ^ ((int) (bits >> 32)));
   }

   /**
    * Determines whether or not the specified Object is
    * equal to this DEllipse2D.
    */
   public boolean equals(Object obj) {
      if(obj == this) {
         return true;
      }

      if(obj instanceof Ellipse2D) {
        Ellipse2D e2d = (Ellipse2D) obj;
        return ((x == e2d.getX()) && (y == e2d.getY()) &&
           (width == e2d.getWidth()) && (height == e2d.getHeight()));
      }

      return false;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "DEllipse2D[x=" + x + ",y=" + y + ",w=" + width + ",h=" + height +
             "]";
   }

   public double x;
   public double y;
   public double width;
   public double height;
}
