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
package inetsoft.util;

import java.awt.geom.Rectangle2D;
import java.io.Serializable;

/**
 * Double rectangle implementation.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class DRectangle extends Rectangle2D implements Cloneable, Serializable {
   /**
    * Constructor.
    */
   public DRectangle() {
      super();
   }

   /**
    * Constructor.
    */
   public DRectangle(double x, double y, double w, double h) {
      this();

      setRect(x, y, w, h);
   }

   /**
    * Constructor.
    */
   public DRectangle(Rectangle2D rec) {
      this(rec.getX(), rec.getY(), rec.getWidth(), rec.getHeight());
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
    * Check if is empty.
    */
   @Override
   public boolean isEmpty() {
      return (width <= 0.0) || (height <= 0.0);
   }

   /**
    * Set the rectangle values.
    */
   @Override
   public void setRect(double x, double y, double w, double h) {
      this.x = x;
      this.y = y;
      this.width = w;
      this.height = h;
   }

   /**
    * Set the rectangle values.
    */
   @Override
   public void setRect(Rectangle2D r) {
      this.x = r.getX();
      this.y = r.getY();
      this.width = r.getWidth();
      this.height = r.getHeight();
   }

   /**
    * Get the out code value for a point.
    */
   @Override
   public int outcode(double x, double y) {
      int out = 0;

      if(this.width <= 0) {
         out |= OUT_LEFT | OUT_RIGHT;
      }
      else if(x < this.x) {
         out |= OUT_LEFT;
      }
      else if(x > this.x + this.width) {
         out |= OUT_RIGHT;
      }

      if(this.height <= 0) {
         out |= OUT_TOP | OUT_BOTTOM;
      }
      else if(y < this.y) {
         out |= OUT_TOP;
      }
      else if(y > this.y + this.height) {
         out |= OUT_BOTTOM;
      }

      return out;
   }

   /**
    * Get the rectangle value.
    */
   @Override
   public Rectangle2D getBounds2D() {
      return new DRectangle(x, y, width, height);
   }

   /**
    * Get the intersection value.
    */
   @Override
   public Rectangle2D createIntersection(Rectangle2D r) {
      Rectangle2D dest = new Rectangle2D.Double();
      Rectangle2D.intersect(this, r, dest);
      return dest;
   }

   /**
    * Get the union value.
    */
   @Override
   public Rectangle2D createUnion(Rectangle2D r) {
      Rectangle2D dest = new Rectangle2D.Double();
      Rectangle2D.union(this, r, dest);
      return dest;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "DRectange[x=" + x + ",y=" + y + ",w=" + width + ",h=" + height +
             "]";
   }

   public double x;
   public double y;
   public double width;
   public double height;
}
