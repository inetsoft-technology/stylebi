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
package inetsoft.uql.jdbc;

import java.io.Serializable;
import java.util.Objects;

/**
 * OrderByItem is used to store the order by information in the UniformSQL.
 */
public class OrderByItem implements Serializable, Cloneable {
   /**
    * Create an order by item.
    * @param field order column.
    * @param order order, asc or desc.
    */
   public OrderByItem(Object field, String order) {
      this.field = field;
      this.order = order;
   }

   /**
    * Get the order by field.
    */
   public Object getField() {
      return field;
   }

   /**
    * Get the order by order.
    */
   public String getOrder() {
      return order;
   }

   /**
    * Set the order by field.
    */
   public void setField(Object field) {
      this.field = field;
   }

   /**
    * Set the order by order.
    */
   public void setOrder(String order) {
      this.order = order;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "OrderBy: " + field + "[" + order + "]";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;
      OrderByItem that = (OrderByItem) o;
      return Objects.equals(field, that.field) && Objects.equals(order, that.order);
   }

   @Override
   public int hashCode() {
      return Objects.hash(field, order);
   }

   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         // impossible
      }

      return null;
   }

   private Object field;
   private String order;
}

