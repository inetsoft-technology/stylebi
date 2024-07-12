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
package inetsoft.uql.asset.internal;

import java.awt.geom.Point2D;

public class SchemaTableInfo implements Cloneable {
   public SchemaTableInfo() {
      this(0, 0);
   }

   public SchemaTableInfo(double left, double top) {
      this(left, top, INITIAL_SCHEMA_TABLE_WIDTH);
   }

   public SchemaTableInfo(double left, double top, double width) {
      this.left = left;
      this.top = top;
      this.width = width;
   }

   /**
    * Set the location of the schema table.
    *
    * @param location the new location
    */
   public void setLocation(Point2D.Double location) {
      this.left = location.getX();
      this.top = location.getY();
   }

   /**
    * @return the location of this schema table
    */
   public Point2D.Double getLocation() {
      return new Point2D.Double(left, top);
   }

   /**
    * @return the left position of the schema table
    */
   public double getLeft() {
      return left;
   }

   /**
    * @return the top position of the schema table
    */
   public double getTop() {
      return top;
   }

   /**
    * Set the width of the schema table.
    *
    * @param width the new width of the schema table
    */
   public void setWidth(double width) {
      this.width = width;
   }

   /**
    * @return the width of the schema table
    */
   public double getWidth() {
      return width;
   }

   @Override
   public Object clone() throws CloneNotSupportedException {
      return super.clone();
   }

   public static final int INITIAL_SCHEMA_TABLE_WIDTH = 150;
   public static final int MIN_SCHEMA_TABLE_WIDTH = 40;

   private double left;
   private double top;
   private double width;
}
