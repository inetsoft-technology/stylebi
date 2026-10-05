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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * SelectTable used to store table and alias.
 */
public class SelectTable implements Serializable, Cloneable {
   /**
    * Create an empty table.
    */
   public SelectTable() {
      super();
   }

   /**
    * Create a table with alias and table name.
    */
   public SelectTable(String alias, Object name) {
      this(alias, name, null, null);
   }

   /**
    * Create a table with alias, table name and location.
    */
   public SelectTable(String alias, Object name, Point loc, Point scroll) {
      this.name = name;
      this.alias = alias;

      if(loc == null) {
         this.location = new Point(-1, -1);
      }
      else {
         this.location = loc;
      }

      if(scroll == null) {
         this.scrollLocation = new Point(0, 0);
      }
      else {
         this.scrollLocation = scroll;
      }
   }

   /**
    * Get table name.
    */
   public Object getName() {
      return name;
   }

   /**
    * Get table alias.
    */
   public String getAlias() {
      return alias;
   }

   /**
    * Get table location.
    */
   public Point getLocation() {
      return location;
   }

   /**
    * Set table location.
    */
   public void setLocation(Point loc) {
      this.location = loc;
   }

   /**
    * Get table location.
    */
   public Point getScrollLocation() {
      return scrollLocation;
   }

   /**
    * Set table location.
    */
   public void setScrollLocation(Point loc) {
      this.scrollLocation = loc;
   }

   /**
    * Set table name.
    */
   public void setName(Object name) {
      // the quoted segments describe the old name
      if(!Objects.equals(this.name, name)) {
         quotedSegments = null;
      }

      this.name = name;
   }

   /**
    * Get the indexes of the name segments that were written as quoted identifiers ("a",
    * `a` or [a]) in the parsed sql. The name is split at unquoted dots, and the segments
    * are stored without their quotes unless the parser quoted them again.
    * @return the 0-based segment indexes, or <tt>null</tt> if no segment is known to be
    * quoted.
    */
   public int[] getQuotedSegments() {
      return quotedSegments == null ? null : quotedSegments.clone();
   }

   /**
    * Set the indexes of the name segments that were written as quoted identifiers.
    * @param segments the 0-based segment indexes, or <tt>null</tt> if none.
    */
   public void setQuotedSegments(int[] segments) {
      this.quotedSegments = segments == null || segments.length == 0 ? null : segments.clone();
   }

   /**
    * Check if a name segment was written as a quoted identifier.
    * @param index the 0-based segment index.
    */
   public boolean isQuotedSegment(int index) {
      if(quotedSegments != null) {
         for(int seg : quotedSegments) {
            if(seg == index) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Get the quoted segments as a comma separated list, e.g. "0,2", the form persisted in
    * the quotedSegments attribute of the table name.
    * @return the list, or <tt>null</tt> if no segment is quoted.
    */
   public String getQuotedSegmentsString() {
      if(quotedSegments == null) {
         return null;
      }

      StringBuilder sb = new StringBuilder();

      for(int seg : quotedSegments) {
         if(sb.length() > 0) {
            sb.append(',');
         }

         sb.append(seg);
      }

      return sb.toString();
   }

   /**
    * Set the quoted segments from a comma separated list, e.g. "0,2". A missing or
    * malformed value clears them.
    */
   public void setQuotedSegmentsString(String segments) {
      int[] result = null;

      if(segments != null && !segments.trim().isEmpty()) {
         String[] parts = segments.split(",");
         result = new int[parts.length];

         try {
            for(int i = 0; i < parts.length; i++) {
               result[i] = Integer.parseInt(parts[i].trim());

               if(result[i] < 0) {
                  result = null;
                  break;
               }
            }
         }
         catch(NumberFormatException ex) {
            result = null;
         }
      }

      setQuotedSegments(result);
   }

   /**
    * Set table alias.
    */
   public void setAlias(String alias) {
      this.alias = alias;
   }

   /**
    * Get the catalog.
    */
   public String getCatalog() {
      return catalog;
   }

   /**
    * Set the catalog.
    */
   public void setCatalog(Object catalog) {
      this.catalog = catalog == null ? null : catalog.toString();
   }

   /**
    * Get the schema.
    */
   public String getSchema() {
      return schema;
   }

   /**
    * Set the schema.
    */
   public void setSchema(Object schema) {
      this.schema = schema == null ? null : schema.toString();
   }

   /**
    * Check if two tables are identical.
    */
   public boolean equals(Object obj) {
      if(obj instanceof SelectTable) {
         SelectTable tbl = (SelectTable) obj;

         // the alias is compared first, since comparing derived tables generates their sql,
         // which made parsing nested derived tables exponential (Bug #77791)
         return (alias == tbl.alias ||
            alias != null && tbl.alias != null && alias.equals(tbl.alias)) &&
            (name == tbl.name ||
               name != null && tbl.name != null && name.equals(tbl.name));
      }

      return false;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "Table: " + name + "[" + alias + "]{" +
         catalog + ", " + schema + "}";
   }

   /**
    * Create a clone of this object.
    */
   @Override
   public Object clone() {
      try {
         SelectTable obj = (SelectTable) super.clone();

         if(location != null) {
            obj.location = (Point) this.location.clone();
         }

         if(scrollLocation != null) {
            obj.scrollLocation = (Point) this.scrollLocation.clone();
         }

         if(quotedSegments != null) {
            obj.quotedSegments = quotedSegments.clone();
         }

         // a derived table is owned by the copy, as a where/having sub query is, otherwise
         // the in-place rewrites of a query clone (e.g. XUtil.validateConditions) change the
         // original query (Bug #77606)
         if(name instanceof UniformSQL) {
            obj.name = ((UniformSQL) name).clone();
         }

         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   private Object name;
   private String alias;
   private int[] quotedSegments; // indexes of the name segments written quoted
   private Point location;
   private Point scrollLocation;
   private String catalog;
   private String schema;
   // the value computed before quotedSegments was added, so serialized tables still load
   private static final long serialVersionUID = 4366616025453353367L;
   private static final Logger LOG =
      LoggerFactory.getLogger(SelectTable.class);
}
