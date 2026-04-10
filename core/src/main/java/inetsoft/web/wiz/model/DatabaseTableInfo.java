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

import inetsoft.uql.asset.AssetEntry;

public class DatabaseTableInfo {
   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public String getDatabase() {
      return database;
   }

   public void setDatabase(String database) {
      this.database = database;
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

   public String getTable() {
      return table;
   }

   public void setTable(String table) {
      this.table = table;
   }

   public AssetEntry getAssetData() {
      return assetData;
   }

   public void setAssetData(AssetEntry assetData) {
      this.assetData = assetData;
   }

   private String type;
   private String database;
   private String catalog;
   private String schema;
   private String table;
   private AssetEntry assetData;
}
