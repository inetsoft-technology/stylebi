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

/**
 * Response from POST /api/wiz/ws/table.
 * <p>
 * {@code wsId} must be passed as {@code worksheetId} in every subsequent
 * call that adds another table to the same worksheet.
 * {@code columns} contains the names and types that the newly created table
 * exposes — use these exact names in later steps to reference this table's
 * fields (e.g. as {@code baseTables} source columns or subquery column).
 */
public class WorksheetTableResponse {

   /** Asset identifier of the worksheet; pass as {@code worksheetId} in subsequent calls. */
   private String wsId;

   /** The actual name StyleBI assigned to the table (matches the requested name). */
   private String tableName;

   /** Columns exposed by the newly created table. */
   private List<ColumnData> columns;

   private boolean success;
   private String errorMessage;

   public String getWsId() { return wsId; }
   public void setWsId(String wsId) { this.wsId = wsId; }

   public String getTableName() { return tableName; }
   public void setTableName(String tableName) { this.tableName = tableName; }

   public List<ColumnData> getColumns() { return columns; }
   public void setColumns(List<ColumnData> columns) { this.columns = columns; }

   public boolean isSuccess() { return success; }
   public void setSuccess(boolean success) { this.success = success; }

   public String getErrorMessage() { return errorMessage; }
   public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

   public static class ColumnData {
      private String name;
      private String type;

      public ColumnData() {}

      public ColumnData(String name, String type) {
         this.name = name;
         this.type = type;
      }

      public String getName() { return name; }
      public void setName(String name) { this.name = name; }
      public String getType() { return type; }
      public void setType(String type) { this.type = type; }
   }
}
