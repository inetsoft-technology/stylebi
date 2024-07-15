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
package inetsoft.graph.treeviz.tree.circlemap;

/**
 * Describes a Circle by its radius and the location of its center.
 *
 * @author Werner Randelshofer
 * @version 1.0 Jan 17, 2008 Created.
 */
public class Circle implements Cloneable {

   public double cx;
   public double cy;
   public double radius;

   /**
    * Creates a new circle at location 0,0 and a radius of 0.
    */
   public Circle() {

   }

   /**
    * Creates a new circle with the specified coordinates and radius.
    */
   public Circle(double cx, double cy, double r) {
      this.cx = cx;
      this.cy = cy;
      this.radius = r;
   }

   /**
    * Returns the radius of the circle.
    */
   public double getRadius() {
      return radius;
   }

   /**
    * Returns the x-coordinate of the center of the circle.
    *
    * @return the x-coordinate of the center.
    */
   public double getCX() {
      return cx;
   }

   /**
    * Returns the y-coordinate of the center of the circle.
    *
    * @return the y-coordinate of the center.
    */
   public double getCY() {
      return cy;
   }

   /**
    * Returns true, if this circle intersects that circle.
    */
   public boolean intersects(Circle that) {
      return intersects(that, 0);
   }

   public boolean intersects(Circle that, double error) {
      double dist = /*Math.sqrt(*/
         (this.cx - that.cx) * (this.cx - that.cx) +
            (this.cy - that.cy) * (this.cy - that.cy)/*)*/;

      return dist < (this.radius + that.radius) * (this.radius + that.radius) - error;
      //return dist < (this.radius + that.radius) * (this.radius + that.radius);
   }

   /**
    * Returns true, if this circle intersects that circle.
    */
   public double getIntersectionRadius(Circle that) {
      double dist = /*Math.sqrt(*/
         (this.cx - that.cx) * (this.cx - that.cx) +
            (this.cy - that.cy) * (this.cy - that.cy)/*)*/;

      return Math.sqrt(dist) - that.radius;
   }

   /**
    * Returns true, if this circle contains that circle.
    */
   public boolean contains(Circle that) {
      return contains(that, 0);
   }

   public boolean contains(Circle that, double error) {
      double dist = Math.sqrt(
         (this.cx - that.cx) * (this.cx - that.cx) +
            (this.cy - that.cy) * (this.cy - that.cy));
      return this.radius >= dist + that.radius - error;
   }

   /**
    * Returns true, if this circle contains the specified point.
    */
   public boolean contains(double px, double py) {
      double dist = Math.sqrt(
         (this.cx - px) * (this.cx - px) +
            (this.cy - py) * (this.cy - py));
      return this.radius >= dist;
   }

   @Override
   public String toString() {
      return this.getClass() + "[x:" + cx + ",y:" + cy + ",r:" + radius + "]";
   }

   @Override
   public Circle clone() {
      try {
         return (Circle) super.clone();
      }
      catch(CloneNotSupportedException ex) {
         throw new InternalError("Cloneable interface not implemented");
      }
   }
}
