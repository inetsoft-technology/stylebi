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
package inetsoft.web.portal.model.database.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties
public class RemoveGraphTableEvent {

   public String getRuntimeId() {
      return runtimeId;
   }

   public void setRuntimeId(String runtimeId) {
      this.runtimeId = runtimeId;
   }

   public List<RemoveTableInfo> getTables() {
      return tables;
   }

   public void setTables(List<RemoveTableInfo> tables) {
      this.tables = tables;
   }

   private String runtimeId;
   private List<RemoveTableInfo> tables;

   @JsonIgnoreProperties
   public static class RemoveTableInfo {
      public String getFullName() {
         return fullName;
      }

      public void setFullName(String fullName) {
         this.fullName = fullName;
      }

      public String getTableName() {
         return tableName;
      }

      public void setTableName(String tableName) {
         this.tableName = tableName;
      }

      private String tableName;
      private String fullName;
   }
}