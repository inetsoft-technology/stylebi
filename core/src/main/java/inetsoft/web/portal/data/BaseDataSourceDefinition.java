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
package inetsoft.web.portal.data;

public class BaseDataSourceDefinition {
   /**
    * Gets the name of this data source connection.
    *
    * @return the name.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets the name of this data source connection.
    *
    * @param name the name.
    */
   public void setName(String name) {
      this.name = name;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   /**
    * Gets the parentPath of this data source connection.
    *
    * @return the parentPath.
    */
   public String getParentPath() {
      return parentPath;
   }

   /**
    * Sets the parentPath of this data source connection.
    */
   public void setParentPath(String parentPath) {
      this.parentPath = parentPath;
   }

   private String name;
   private String description;
   private String parentPath;
}
