/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.web.wiz.model;

/**
 * Describes one column in the primary worksheet table assembly.
 * <p>
 * name    – the underlying DB column name (used to match against FieldInfo.name)
 * alias   – the column identifier as StyleBI knows it in the worksheet;
 * null when the alias equals the DB column name
 * table   – DB table name (used to match against FieldInfo.table)
 * schema  – DB schema (may be empty)
 * catalog – DB catalog (may be empty)
 * path    – datasource path (used to match against FieldInfo.source.path)
 */
public class WorksheetColumnInfo {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getAlias() {
      return alias;
   }

   public void setAlias(String alias) {
      this.alias = alias;
   }

   public String getTable() {
      return table;
   }

   public void setTable(String table) {
      this.table = table;
   }

   public String getSchema() {
      return schema;
   }

   public void setSchema(String schema) {
      this.schema = schema;
   }

   public String getCatalog() {
      return catalog;
   }

   public void setCatalog(String catalog) {
      this.catalog = catalog;
   }

   public String getPath() {
      return path;
   }

   public void setPath(String path) {
      this.path = path;
   }

   private String name;
   private String alias;
   private String table;
   private String schema;
   private String catalog;
   private String path;
}
