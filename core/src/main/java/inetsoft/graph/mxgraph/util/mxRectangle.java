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
package inetsoft.graph.mxgraph.util;

import java.awt.*;
import java.awt.geom.*;

/**
 * Implements a 2-dimensional rectangle with double precision coordinates.
 */
public class mxRectangle extends mxPoint {

   /**
    *
    */
   private static final long serialVersionUID = 1L;

   /**
    * Holds the width and the height. Default is 0.
    */
   protected double width, height;

   /**
    * Constructs a new rectangle at (0, 0) with the width and height set to 0.
    */
   public mxRectangle() {
      this(0, 0, 0, 0);
   }

   /**
    * Constructs a copy of the given rectangle.
    *
    * @param rect Rectangle to construct a copy of.
    */
   public mxRectangle(Rectangle2D rect) {
      this(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
   }

   /**
    * Constructs a copy of the given rectangle.
    *
    * @param rect Rectangle to construct a copy of.
    */
   public mxRectangle(mxRectangle rect) {
      this(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
   }

   /**
    * Constructs a rectangle using the given parameters.
    *
    * @param x      X-coordinate of the new rectangle.
    * @param y      Y-coordinate of the new rectangle.
    * @param width  Width of the new rectangle.
    * @param height Height of the new rectangle.
    */
   public mxRectangle(double x, double y, double width, double height) {
      super(x, y);

      setWidth(width);
      setHeight(height);
   }

   /**
    * Returns the width of the rectangle.
    *
    * @return Returns the width.
    */
   public double getWidth()
   {
      return width;
   }

   /**
    * Sets the width of the rectangle.
    *
    * @param value Double that specifies the new width.
    */
   public void setWidth(double value)
   {
      width = value;
   }

   /**
    * Returns the height of the rectangle.
    *
    * @return Returns the height.
    */
   public double getHeight()
   {
      return height;
   }

   /**
    * Sets the height of the rectangle.
    *
    * @param value Double that specifies the new height.
    */
   public void setHeight(double value)
   {
      height = value;
   }

   /**
    * Sets this rectangle to the specified values
    *
    * @param x the new x-axis position
    * @param y the new y-axis position
    * @param w the new width of the rectangle
    * @param h the new height of the rectangle
    */
   public void setRect(double x, double y, double w, double h)
   {
      this.x = x;
      this.y = y;
      this.width = w;
      this.height = h;
   }

   /**
    * Adds the given rectangle to this rectangle.
    */
   public void add(mxRectangle rect)
   {
      if(rect != null) {
         double minX = Math.min(x, rect.x);
         double minY = Math.min(y, rect.y);
         double maxX = Math.max(x + width, rect.x + rect.width);
         double maxY = Math.max(y + height, rect.y + rect.height);

         x = minX;
         y = minY;
         width = maxX - minX;
         height = maxY - minY;
      }
   }

   /**
    * Returns the x-coordinate of the center.
    *
    * @return Returns the x-coordinate of the center.
    */
   public double getCenterX()
   {
      return getX() + getWidth() / 2;
   }

   /**
    * Returns the y-coordinate of the center.
    *
    * @return Returns the y-coordinate of the center.
    */
   public double getCenterY()
   {
      return getY() + getHeight() / 2;
   }

   /**
    * Grows the rectangle by the given amount, that is, this method subtracts
    * the given amount from the x- and y-coordinates and adds twice the amount
    * to the width and height.
    *
    * @param amount Amount by which the rectangle should be grown.
    */
   public void grow(double amount)
   {
      x -= amount;
      y -= amount;
      width += 2 * amount;
      height += 2 * amount;
   }

   /**
    * Returns true if the given point is contained in the rectangle.
    *
    * @param x X-coordinate of the point.
    * @param y Y-coordinate of the point.
    *
    * @return Returns true if the point is contained in the rectangle.
    */
   public boolean contains(double x, double y)
   {
      return (this.x <= x && this.x + width >= x && this.y <= y && this.y
         + height >= y);
   }

   /**
    * Returns the point at which the specified point intersects the perimeter
    * of this rectangle or null if there is no intersection.
    *
    * @param x0 the x co-ordinate of the first point of the line
    * @param y0 the y co-ordinate of the first point of the line
    * @param x1 the x co-ordinate of the second point of the line
    * @param y1 the y co-ordinate of the second point of the line
    *
    * @return the point at which the line intersects this rectangle, or null
    * if there is no intersection
    */
   public mxPoint intersectLine(double x0, double y0, double x1, double y1)
   {
      mxPoint result = null;

      result = mxUtils.intersection(x, y, x + width, y, x0, y0, x1, y1);

      if(result == null) {
         result = mxUtils.intersection(x + width, y, x + width, y + height,
                                       x0, y0, x1, y1);
      }

      if(result == null) {
         result = mxUtils.intersection(x + width, y + height, x, y + height,
                                       x0, y0, x1, y1);
      }

      if(result == null) {
         result = mxUtils.intersection(x, y, x, y + height, x0, y0, x1, y1);
      }

      return result;
   }

   /**
    * Returns the bounds as a new rectangle.
    *
    * @return Returns a new rectangle for the bounds.
    */
   public Rectangle getRectangle()
   {
      int ix = (int) Math.round(x);
      int iy = (int) Math.round(y);
      int iw = (int) Math.round(width - ix + x);
      int ih = (int) Math.round(height - iy + y);

      return new Rectangle(ix, iy, iw, ih);
   }

   /**
    * Rotates this rectangle by 90 degree around its center point.
    */
   public void rotate90()
   {
      double t = (this.width - this.height) / 2;
      this.x += t;
      this.y -= t;
      double tmp = this.width;
      this.width = this.height;
      this.height = tmp;
   }

   /**
    * Returns true if the given object equals this rectangle.
    */
   public boolean equals(Object obj)
   {
      if(obj instanceof mxRectangle) {
         mxRectangle rect = (mxRectangle) obj;

         return rect.getX() == getX() && rect.getY() == getY()
            && rect.getWidth() == getWidth()
            && rect.getHeight() == getHeight();
      }

      return false;
   }

   /**
    * Returns a new instance of the same rectangle.
    */
   public Object clone() {
      mxRectangle clone = (mxRectangle) super.clone();

      clone.setWidth(getWidth());
      clone.setHeight(getHeight());

      return clone;
   }

   @Override
   public void transformPosition(AffineTransform trans) {
      Point2D center = new Point2D.Double(getCenterX(), getCenterY());
      center = trans.transform(center, null);

      setX(center.getX() - getWidth() / 2);
      setY(center.getY() - getHeight() / 2);
   }

   @Override
   public void transform(AffineTransform trans) {
      Point2D br = new Point2D.Double(getX() + width, getY() + height);
      br = trans.transform(br, null);

      super.transform(trans);

      width = br.getX() - getX();
      height = br.getY() - getY();
   }

   // get the connector line from this vertex to the target.
   public Line2D getConnector(mxRectangle target) {
      double left = getX();
      double top = getY();
      double right = left + getWidth();
      double bottom = top + getHeight();
      double centerX = getCenterX();
      double centerY = getCenterY();
      double left2 = target.getX();
      double top2 = target.getY();
      double right2 = left2 + target.getWidth();
      double bottom2 = top2 + target.getHeight();
      double centerX2 = target.getCenterX();
      double centerY2 = target.getCenterY();
      double hgap = 0, vgap = 0;

      if(left2 > right) {
         hgap = left2 - right;
      }
      else if(right2 < left) {
         hgap = left - right2;
      }

      if(top2 > bottom) {
         vgap = top2 - bottom;
      }
      else if(bottom2 < top) {
         vgap = top - bottom2;
      }

      // favor the line at the sides of nodes with greater distance. for example, if two nodes
      // are on top and bottom of each other with the 2nd node slightly on the right, we want
      // to draw the line at the bottom and top of 1st/2nd nodes, instead of right and left
      // of the 1st/2nd nodes.
      if(hgap > vgap) {
         if(left2 > right) {
            return new Line2D.Double(right, centerY, left2, centerY2);
         }
         else if(right2 < left) {
            return new Line2D.Double(right2, centerY2, left, centerY);
         }
      }
      else if(top2 > bottom) {
         return new Line2D.Double(centerX2, top2, centerX, bottom);
      }
      else if(bottom2 < top) {
         return new Line2D.Double(centerX2, bottom2, centerX, top);
      }

      return new Line2D.Double(centerX, centerY, centerX2, centerY2);
   }

   /**
    * Returns the <code>String</code> representation of this
    * <code>mxRectangle</code>.
    *
    * @return a <code>String</code> representing this
    * <code>mxRectangle</code>.
    */
   @Override
   public String toString()
   {
      StringBuilder builder = new StringBuilder(32);
      builder.append(getClass().getSimpleName());
      builder.append(" [");
      builder.append("x=");
      builder.append(x);
      builder.append(", y=");
      builder.append(y);
      builder.append(", width=");
      builder.append(width);
      builder.append(", height=");
      builder.append(height);
      builder.append("]");

      return builder.toString();
   }
}
