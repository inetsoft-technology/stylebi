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
 * Request body for DELETE /api/wiz/ws/table.
 */
public class DeleteWorksheetTablesRequest {

   /**
    * wsId of the worksheet to modify.
    */
   private String worksheetId;

   /**
    * Names of the tables to delete.
    * Tables with external dependents (assemblies not in this list that reference
    * them) are skipped and reported in the response.
    */
   private List<String> tableNames;

   public String getWorksheetId() {
      return worksheetId;
   }

   public void setWorksheetId(String worksheetId) {
      this.worksheetId = worksheetId;
   }

   public List<String> getTableNames() {
      return tableNames;
   }

   public void setTableNames(List<String> tableNames) {
      this.tableNames = tableNames;
   }
}
