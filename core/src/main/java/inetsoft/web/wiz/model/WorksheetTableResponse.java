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
   private List<WorksheetColumnData> columns;

   /**
    * Primary-table fields (name, alias, type, source) StyleBI exposes for visualization binding.
    * Populated only when this table was created with {@code asPrimaryTable = true}; null otherwise.
    */
   private List<WorksheetColumnInfo> primaryTableFields;

   /**
    * The shape of the response the endpoint returned — which paths exist and what type each leaf
    * is, carrying none of the values. Present only for a {@code tabular table}, and only when the
    * connector reported one.
    *
    * <p>Distinct from {@code columns}, and not a duplicate of it. {@code columns} is what the row
    * path actually produced, flattened; this is the WHOLE response before that path was applied,
    * nested. A caller reads it to pick {@code jsonPath} (which of the response's arrays holds the
    * rows) and {@code expandedPath} (which nested array to expand), neither of which the flat
    * column list can answer.</p>
    *
    * <p>Untyped because core does not interpret it: the connector produced it, the caller consumes
    * it. See {@code TabularQuery.getResponseShape}.</p>
    */
   private Object responseSchema;

   /**
    * True when {@code responseSchema} was cut off by a node or depth cap. Under truncation a path's
    * absence means "not reached", not "not present" — a consumer that conflates the two will report
    * a field as nonexistent when it was merely beyond the cap.
    */
   private boolean responseSchemaTruncated;

   private boolean success;
   private String errorMessage;

   public String getWsId() { return wsId; }
   public void setWsId(String wsId) { this.wsId = wsId; }

   public String getTableName() { return tableName; }
   public void setTableName(String tableName) { this.tableName = tableName; }

   public List<WorksheetColumnData> getColumns() { return columns; }
   public void setColumns(List<WorksheetColumnData> columns) { this.columns = columns; }

   public List<WorksheetColumnInfo> getPrimaryTableFields() { return primaryTableFields; }
   public void setPrimaryTableFields(List<WorksheetColumnInfo> primaryTableFields) {
      this.primaryTableFields = primaryTableFields;
   }

   public Object getResponseSchema() { return responseSchema; }
   public void setResponseSchema(Object responseSchema) { this.responseSchema = responseSchema; }

   public boolean isResponseSchemaTruncated() { return responseSchemaTruncated; }

   public void setResponseSchemaTruncated(boolean responseSchemaTruncated) {
      this.responseSchemaTruncated = responseSchemaTruncated;
   }

   public boolean isSuccess() { return success; }
   public void setSuccess(boolean success) { this.success = success; }

   public String getErrorMessage() { return errorMessage; }
   public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

}
