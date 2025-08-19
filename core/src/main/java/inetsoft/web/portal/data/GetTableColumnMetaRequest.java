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

package inetsoft.web.portal.data;

public class GetTableColumnMetaRequest {
   public String getDsName() {
      return dsName;
   }

   public void setDsName(String dsName) {
      this.dsName = dsName;
   }

   public String getTableName() {
      return tableName;
   }

   public void setTableName(String tableName) {
      this.tableName = tableName;
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

   public boolean isHasCatalog() {
      return hasCatalog;
   }

   public void setHasCatalog(boolean hasCatalog) {
      this.hasCatalog = hasCatalog;
   }

   public boolean isHasSchema() {
      return hasSchema;
   }

   public void setHasSchema(boolean hasSchema) {
      this.hasSchema = hasSchema;
   }

   public String getCatalogSep() {
      return catalogSep;
   }

   public void setCatalogSep(String catalogSep) {
      this.catalogSep = catalogSep;
   }

   private String dsName;
   private String tableName;
   private String catalog;
   private String schema;
   private boolean hasCatalog;
   private boolean hasSchema;
   private String catalogSep;
}
