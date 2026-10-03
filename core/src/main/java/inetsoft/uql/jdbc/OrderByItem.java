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
    * Check if the quoting of the field is recorded on this item. An item added without it
    * (e.g. by UniformSQL.setOrderBy), or whose field was replaced by another name since, is
    * quoted if a group by or order by field of the same text was written as a quoted
    * identifier (UniformSQL.isQuotedField).
    */
   public boolean isQuoteSet() {
      return quoteSet && JDBCSelection.isSameQuotedName(quotedFor, quotedColumn, field);
   }

   /**
    * Check if the field was written as a quoted identifier (e.g. "x y"). Only meaningful if
    * isQuoteSet() is true.
    */
   public boolean isQuoted() {
      return quotedColumn != null;
   }

   /**
    * Get the column segment, as written, of a field written as a qualified quoted identifier
    * (t."MixedCase").
    * @return the segment, or <tt>null</tt> for a bare quoted identifier or an unquoted field.
    */
   public String getQuotedColumn() {
      return quotedColumn == null || quotedColumn.isEmpty() ? null : quotedColumn;
   }

   /**
    * Record whether the field was written as a quoted identifier. It is kept on the item, two
    * items may have the same text ("MixedCase" and MixedCase, Bug #77573).
    * @param quoted <tt>true</tt> if quoted.
    * @param segment the column segment of a qualified quoted identifier as written, or
    *                <tt>null</tt> for a bare identifier.
    */
   public void setQuoted(boolean quoted, String segment) {
      this.quoteSet = true;
      this.quotedFor = field;
      this.quotedColumn = !quoted ? null : segment == null ? "" : segment;
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
      return Objects.equals(field, that.field) && Objects.equals(order, that.order) &&
         quoteSet == that.quoteSet && Objects.equals(quotedColumn, that.quotedColumn) &&
         Objects.equals(quotedFor, that.quotedFor);
   }

   @Override
   public int hashCode() {
      return Objects.hash(field, order, quoteSet, quotedColumn, quotedFor);
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
   private boolean quoteSet;
   // the field the quoting was recorded for
   private Object quotedFor;
   // the quoted column segment ("" if bare) if written as a quoted identifier, else null
   private String quotedColumn;
}

