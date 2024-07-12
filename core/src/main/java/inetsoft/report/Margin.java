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
package inetsoft.report;

/**
 * This class defines the page margin parameters. The values are specified
 * in inches.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class Margin implements java.io.Serializable, Cloneable {
   /**
    * Create an empty margin.
    */
   public Margin() {
      this(0, 0, 0, 0);
   }

   /**
    * Create a margin of specified dimension.
    * @param t top margin.
    * @param l left margin.
    * @param b bottom margin.
    * @param r right margin.
    */
   public Margin(double t, double l, double b, double r) {
      top = t;
      left = l;
      bottom = b;
      right = r;
   }

   /**
    * Make a copy of the margin object.
    */
   public Margin(Margin margin) {
      this(margin.top, margin.left, margin.bottom, margin.right);
   }

   public String toString() {
      return "Margin[" + top + "," + left + "," + bottom + "," + right + "]";
   }

   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ignore) {
      }
      
      return null;
   }
   
   /**
    * Top margin in inches.
    */
   public double top;
   /**
    * Left margin in inches.
    */
   public double left;
   /**
    * Bottom margin in inches.
    */
   public double bottom;
   /**
    * Right margin in inches.
    */
   public double right;
}

