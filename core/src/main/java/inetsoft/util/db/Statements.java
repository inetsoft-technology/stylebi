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
package inetsoft.util.db;

/**
 * Class that encapsulates the SQL for a table or query.
 *
 * @since 12.2
 */
public class Statements {
   /**
    * Gets the name of the table or query.
    *
    * @return the name.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets the name of the table or query.
    *
    * @param name the name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Gets the SQL statements used to create the table.
    *
    * @return the SQL statements.
    */
   public String[] getCreate() {
      return create;
   }

   /**
    * Sets the SQL statements used to create the table.
    *
    * @param create the SQL statements.
    */
   public void setCreate(String[] create) {
      this.create = create;
   }

   /**
    * Gets the SQL statements used to drop the table.
    *
    * @return the SQL statements.
    */
   public String[] getDrop() {
      return drop;
   }

   /**
    * Sets the SQL statements used to drop the table.
    *
    * @param drop the SQL statements.
    */
   public void setDrop(String[] drop) {
      this.drop = drop;
   }

   /**
    * Gets the SQL statements used to determine if the table exists.
    *
    * @return the SQL statements.
    */
   public String[] getExists() {
      return exists;
   }

   /**
    * Sets the SQL statements used to determine if the table exists.
    *
    * @param exists the SQL statements.
    */
   public void setExists(String[] exists) {
      this.exists = exists;
   }

   /**
    * Gets the SQL statements used to insert rows into the table.
    *
    * @return the SQL statements.
    */
   public String[] getInsert() {
      return insert;
   }

   /**
    * Sets the SQL statements used to insert rows into the table.
    *
    * @param insert the SQL statements.
    */
   public void setInsert(String[] insert) {
      this.insert = insert;
   }

   /**
    * Gets the SQL statements used to update rows in the table.
    *
    * @return the SQL statements.
    */
   public String[] getUpdate() {
      return update;
   }

   /**
    * Sets the SQL statements used to update rows in the table.
    *
    * @param update the SQL statements.
    */
   public void setUpdate(String[] update) {
      this.update = update;
   }

   /**
    * Gets the SQL statements used to delete rows from the table.
    *
    * @return the SQL statements.
    */
   public String[] getDelete() {
      return delete;
   }

   /**
    * Sets the SQL statements used to delete rows from the table.
    *
    * @param delete the SQL statements.
    */
   public void setDelete(String[] delete) {
      this.delete = delete;
   }

   /**
    * Gets the SQL statements used to select rows from the table.
    *
    * @return the SQL statements.
    */
   public String[] getSelect() {
      return select;
   }

   /**
    * Sets the SQL statements used to select rows from the table.
    *
    * @param select the SQL statements.
    */
   public void setSelect(String[] select) {
      this.select = select;
   }

   private String name;
   private String[] create;
   private String[] drop;
   private String[] exists;
   private String[] insert;
   private String[] update;
   private String[] delete;
   private String[] select;
}
