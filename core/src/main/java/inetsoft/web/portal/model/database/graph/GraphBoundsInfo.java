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
package inetsoft.web.portal.model.database.graph;

import java.awt.*;
import java.util.Objects;

public class GraphBoundsInfo implements Cloneable {
   private String tableName;
   private Rectangle bounds;
   private boolean base;

   public GraphBoundsInfo() {
   }

   public String getTableName() {
      return tableName;
   }

   public void setTableName(String tableName) {
      this.tableName = tableName;
   }

   public Rectangle getBounds() {
      return bounds;
   }

   public void setBounds(Rectangle bounds) {
      this.bounds = bounds;
   }

   public boolean isBase() {
      return base;
   }

   public void setBase(boolean base) {
      this.base = base;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      }

      if (o == null || getClass() != o.getClass()) {
         return false;
      }

      GraphBoundsInfo that = (GraphBoundsInfo) o;

      return tableName.equals(that.tableName) &&
         bounds.equals(that.bounds) && base == that.base;
   }

   @Override
   public int hashCode() {
      return Objects.hash(tableName, bounds, base);
   }

   @Override
   public Object clone() throws CloneNotSupportedException {
      GraphBoundsInfo clone = (GraphBoundsInfo) super.clone();
      clone.tableName = this.tableName;
      clone.bounds = this.bounds == null ? null : this.bounds.getBounds();
      clone.base = base;

      return clone;
   }
}
