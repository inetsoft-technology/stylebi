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
package inetsoft.uql.jdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.Serializable;

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
      this.name = name;
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

         return (name == tbl.name ||
               name != null && tbl.name != null && name.equals(tbl.name)) &&
            (alias == tbl.alias ||
            alias != null && tbl.alias != null && alias.equals(tbl.alias));
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

         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   private Object name;
   private String alias;
   private Point location;
   private Point scrollLocation;
   private String catalog;
   private String schema;
   private static final Logger LOG =
      LoggerFactory.getLogger(SelectTable.class);
}
