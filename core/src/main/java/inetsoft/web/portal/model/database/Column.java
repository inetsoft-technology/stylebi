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
package inetsoft.web.portal.model.database;

import java.io.Serializable;

/**
 * Columns used for vpm and query clause in condition pane.
 */
@SuppressWarnings({ "unused", "WeakerAccess" })
public class Column implements Serializable {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public String getColumnName() {
      return columnName;
   }

   public void setColumnName(String columnName) {
      this.columnName = columnName;
   }

   public String getTableName() {
      return tableName;
   }

   public void setTableName(String tableName) {
      this.tableName = tableName;
   }

   public String getPhysicalTableName() {
      return physicalTableName;
   }

   public void setPhysicalTableName(String physicalTableName) {
      this.physicalTableName = physicalTableName;
   }

   private String name;
   private String type;
   private String columnName;
   private String tableName;
   private String physicalTableName;
}
