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
 * but WITHOUT EVEN THE IMPLIED WARRANTY OF MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package inetsoft.web.wiz.model;

import java.util.ArrayList;
import java.util.List;

public class WorksheetTableMeta {
   private String tableName;
   private String worksheetName;
   private List<ColumnMeta> columns = new ArrayList<>();

   public String getTableName() {
      return tableName;
   }

   public void setTableName(String tableName) {
      this.tableName = tableName;
   }

   public String getWorksheetName() {
      return worksheetName;
   }

   public void setWorksheetName(String worksheetName) {
      this.worksheetName = worksheetName;
   }

   public List<ColumnMeta> getColumns() {
      return columns;
   }

   public void setColumns(List<ColumnMeta> columns) {
      this.columns = columns;
   }

   public String getTableType() {
      return "normalTable";
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

      public String getExpression() {
         return expression;
      }

      public void setExpression(String expression) {
         this.expression = expression;
      }

      public int getRefType() {
         return refType;
      }

      public void setRefType(int refType) {
         this.refType = refType;
      }

      public String getAlias() {
         return alias;
      }

      public void setAlias(String alias) {
         this.alias = alias;
      }

      public String getDescription() {
         return description;
      }

      public void setDescription(String description) {
         this.description = description;
      }

      private String name;
      private String type;
      private String expression;
      private int refType;
      private String alias;
      private String description;
   }
}
