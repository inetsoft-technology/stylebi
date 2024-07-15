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
package inetsoft.report.internal;

import java.awt.*;

/**
 * This class defines the bounds in inches.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class Bounds implements java.io.Serializable, Cloneable {
   /**
    * Create a bounds.
    */
   public Bounds() {
      this(0, 0, 0, 0);
   }

   /**
    * Create a Position of specified dimension.
    * @param x x position in inches.
    * @param y y position in inches.
    * @param w width in inches.
    * @param h height in inches.
    */
   public Bounds(float x, float y, float w, float h) {
      this.x = x;
      this.y = y;
      this.width = w;
      this.height = h;
   }

   /**
    * Create a copy of Bounds.
    */
   public Bounds(Bounds box) {
      this(box.x, box.y, box.width, box.height);
   }

   /**
    * Create a bounds from a rectangle.
    */
   public Bounds(Rectangle rect) {
      this(rect.x, rect.y, rect.width, rect.height);
   }

   /**
    * Get a rectangle instance in integer.
    */
   public Rectangle getRectangle() {
      double right = x + width;
      double bottom = y + height;
      int ix = (int) x;
      int iw = (int) (right - ix);
      int iy = (int) y;
      int ih = (int) (bottom - iy);
      return new Rectangle(ix, iy, iw, ih);
   }

   /**
    * Set the location of this bounding area.
    */
   public void setLocation(Point loc) {
      x = loc.x;
      y = loc.y;
   }

   /**
    * Return the location of this bounding area.
    */
   public Point getLocation() {
      return new Point((int) x, (int) y);
   }

   /**
    * Round the bounds to integer boundaries.
    */
   public Bounds round() {
      float right = (float) Math.ceil(x + width);
      float bottom = (float) Math.ceil(y + height);
      float nx = (float) Math.ceil(x);
      float ny = (float) Math.ceil(y);

      return new Bounds(nx, ny, right - nx, bottom - ny);
   }

   /**
    * Compare two bounds.
    */
   public boolean equals(Object val) {
      try {
         Bounds bds = (Bounds) val;

         return x == bds.x && y == bds.y && width == bds.width &&
            height == bds.height;
      }
      catch(Exception ex) {
         return false;
      }
   }

   @Override
   public Object clone() {
      return new Bounds(this);
   }

   public String toString() {
      return "Bounds[" + x + "," + y + " " + width + "x" + height + "]";
   }

   /**
    * X in inches.
    */
   public float x;
   /**
    * Y in inches.
    */
   public float y;
   /**
    * Width in inches.
    */
   public float width;
   /**
    * Height in inches.
    */
   public float height;
}
