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
package inetsoft.graph.internal;

import java.awt.geom.Dimension2D;
import java.io.Serializable;

/**
 * This is a double implementation of Dimension2D.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class DimensionD extends Dimension2D implements Serializable {
   /**
    * Default constructor.
    */
   public DimensionD() {
   }

   public DimensionD(double w, double h) {
      this.width = w;
      this.height = h;
   }

   /**
    * Returns the width of this <code>Dimension</code> in double
    * precision.
    * @return the width of this <code>Dimension</code>.
    */
   @Override
   public double getWidth() {
      return width;
   }

   /**
    * Returns the height of this <code>Dimension</code> in double
    * precision.
    * @return the height of this <code>Dimension</code>.
    */
   @Override
   public double getHeight() {
      return height;
   }

   /**
    * Sets the size of this <code>Dimension</code> object to the
    * specified width and height.
    * This method is included for completeness, to parallel the
    * {@link java.awt.Component#getSize getSize} method of
    * {@link java.awt.Component}.
    * @param width  the new width for the <code>Dimension</code>
    * object
    * @param height  the new height for the <code>Dimension</code>
    * object
    */
   @Override
   public void setSize(double width, double height) {
      this.width = width;
      this.height = height;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "DimensionD[" + width + "," + height + "]";
   }

   private double width;
   private double height;
}
