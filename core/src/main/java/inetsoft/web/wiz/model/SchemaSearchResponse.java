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

import java.util.List;

public class SchemaSearchResponse {
   public List<SchemaSearchResult> getResults() {
      return results;
   }

   public void setResults(List<SchemaSearchResult> results) {
      this.results = results;
   }

   public static class SchemaSearchResult {
      public String getDatasource() {
         return datasource;
      }

      public void setDatasource(String datasource) {
         this.datasource = datasource;
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

      public String getType() {
         return type;
      }

      public void setType(String type) {
         this.type = type;
      }

      public List<ColumnMatch> getMatchedColumns() {
         return matchedColumns;
      }

      public void setMatchedColumns(List<ColumnMatch> matchedColumns) {
         this.matchedColumns = matchedColumns;
      }

      private String datasource;
      private String catalog;
      private String schema;
      private String table;
      private String type;
      private List<ColumnMatch> matchedColumns;
   }

   public static class ColumnMatch {
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

      private String name;
      private String type;
   }

   private List<SchemaSearchResult> results;
}
