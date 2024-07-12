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

import inetsoft.util.Tool;

import java.io.Serializable;

/**
 * The XField contains the information about the field definded in the
 * select statement.
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XField implements Serializable, Cloneable {
   /**
    * String type.
    */
   public static final String STRING_TYPE = "string";

   /**
    * Create a field with alias and a name same as the alias.
    * @param alias field name and alias.
    */
   public XField(String alias) {
      this(alias, alias);
   }

   /**
    * Create a field with alias and name.
    * @param alias field alias.
    * @param name field name.
    */
   public XField(String alias, Object name) {
      this(alias, name, "");
   }

   /**
    * Create a field with associated table.
    * @param alias field alias.
    * @param name field name.
    * @param table the table field belongs to.
    */
   public XField(String alias, Object name, String table) {
      this(alias, name, table, STRING_TYPE);
   }

   /**
    * Create a field with associated table.
    * @param alias field alias.
    * @param name field name.
    * @param table the table field belongs to.
    * @param type field type.
    */
   public XField(String alias, Object name, String table, String type) {
      this(alias, name, table, type, null);
   }

   /**
    * Create a field with associated table.
    * @param alias field alias.
    * @param name field name.
    * @param table the table field belongs to.
    * @param type field type.
    */
   public XField(String alias, Object name, String table, String type,
                String physicalTable) {
      this.alias = alias;
      this.name = name;
      this.table = table;
      this.type = type;
      this.physicalTable = physicalTable;
   }

   /**
    * Get field alias.
    */
   public String getAlias() {
      return alias;
   }

   /**
    * Get field name.
    */
   public Object getName() {
      return name;
   }

   /**
    * Get field table name.
    */
   public String getTable() {
      return table;
   }

   /**
    * Get physical table.
    */
   public String getPhysicalTable() {
      return physicalTable;
   }

   /**
    * Get field type.
    */
   public String getType() {
      return type;
   }

   /**
    * Set field type.
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * Set field table.
    */
   public void setTable(String table) {
      this.table = table;
   }

   /**
    * Set field name.
    */
   public void setName(Object name) {
      this.name = name;
   }

   /**
    * Set field alias.
    */
   public void setAlias(String alias) {
      this.alias = alias;
   }

   public String toString() {
      return "Field[" + name + "," + alias + "," + table + "," + type + "]";
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

   public boolean equals(Object field) {
      XField xf = (XField) field;

      return xf != null && Tool.equals(xf.getAlias(), alias) &&
	 Tool.equals(xf.getName(), name) &&
         Tool.equals(xf.getTable(), table) &&
         Tool.equals(xf.getType(), type);
   }

   private String alias;
   private Object name;
   private String table;
   private String physicalTable = null;
   private String type;
}
