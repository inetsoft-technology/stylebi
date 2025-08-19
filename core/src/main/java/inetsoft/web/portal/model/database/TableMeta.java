/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.web.portal.controller.database.DatasourceMetaController;

import java.util.ArrayList;
import java.util.List;

public class TableMeta {
   public String getName() {
      return name;
   }

   public void setName(String table) {
      this.name = table;
   }

   public List<ColumnMeta> getColumns() {
      return columns;
   }

   public void setColumns(List<ColumnMeta> columns) {
      this.columns = columns;
   }

   public String getDataSource() {
      return dataSource;
   }

   public void setDataSource(String dataSource) {
      this.dataSource = dataSource;
   }

   public String getCatalog() {
      return catalog;
   }

   public void setCatalog(String catalog) {
      this.catalog = catalog;
   }

   public String getSchema() {
      return schema;
   }

   public void setSchema(String schema) {
      this.schema = schema;
   }

   public static class ColumnMeta {
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

      public boolean isPrimaryKey() {
         return primaryKey;
      }

      public void setPrimaryKey(boolean primaryKey) {
         this.primaryKey = primaryKey;
      }

      public List<String[]> getForeignKeys() {
         return foreignKeys;
      }

      public void setForeignKeys(List<String[]> foreignKeys) {
         this.foreignKeys = foreignKeys;
      }

      public int getLength() {
         return length;
      }

      public void setLength(int length) {
         this.length = length;
      }

      private String name;
      private String type;
      private int length;
      private boolean primaryKey;
      private List<String[]> foreignKeys;
   }

   private String dataSource;
   private String catalog;
   private String schema;
   private String name;
   private List<ColumnMeta> columns = new ArrayList<>();
}
