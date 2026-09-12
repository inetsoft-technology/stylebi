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
package inetsoft.web.composer.vs.dialog;

import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.Tool;
import inetsoft.web.factory.RemainingPath;
import org.springframework.beans.factory.annotation.Autowired;
import inetsoft.web.viewsheet.service.VSInputServiceProxy;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.security.Principal;
import java.util.*;

@Controller
public class DataInputController {

   @Autowired
   public DataInputController(VSInputServiceProxy vsInputServiceProxy)
   {
      this.vsInputServiceProxy = vsInputServiceProxy;
   }

   @RequestMapping(
      value = "/vs/dataInput/columns/{runtimeId}/{table}",
      method = RequestMethod.GET
   )
   @ResponseBody
   public Map<String, String[]> getTableColumns(@PathVariable("runtimeId") String runtimeId,
                                   @PathVariable("table") String table,
                                   Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return vsInputServiceProxy.getTableColumns(runtimeId, table, principal);
   }

   @RequestMapping(
      value = "/vs/dataInput/rows/{runtimeId}/{table}/{column}",
      method = RequestMethod.GET
   )
   @ResponseBody
   public String[] getColumnRows(@PathVariable("runtimeId") String runtimeId,
                                  @PathVariable("table") String table,
                                  @PathVariable("column") String column,
                                  Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return vsInputServiceProxy.getColumnRows(runtimeId, table, column, principal);
   }

   @RequestMapping(
      value = "/vs/dataInput/popupTable/{table}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public PopupEmbeddedTable getPopupTable(@RemainingPath String runtimeId,
                                           @PathVariable("table") String table,
                                           Principal principal)
      throws Exception
   {
      return vsInputServiceProxy.getPopupTable(runtimeId, table, principal);
   }

   public static final class PopupEmbeddedTable implements Serializable {
      public PopupEmbeddedTable() {}

      public PopupEmbeddedTable(XEmbeddedTable xTable, String tableName) {
         this.tableName = tableName;
         // First row is column headers
         numRows = xTable.getRowCount() > 0 ? xTable.getRowCount() - 1 : 0;
         columnHeaders = new String[xTable.getColCount()];

         for(int i = 0; i < columnHeaders.length; i++) {
            columnHeaders[i] = Tool.toString(xTable.getObject(0, i));
         }

         rowData = new String[numRows][columnHeaders.length];

         for(int row = 0; row < numRows; row++) {
            for(int col = 0; col < columnHeaders.length; col++) {
               rowData[row][col] = Tool.toString(xTable.getObject(row + 1, col));
            }
         }
      }

      public String getTableName() {
         return tableName;
      }

      public void setTableName(String tableName) {
         this.tableName = tableName;
      }

      public int getNumRows() {
         return numRows;
      }

      public void setNumRows(int numRows) {
         this.numRows = numRows;
      }

      public String[] getColumnHeaders() {
         return columnHeaders;
      }

      public void setColumnHeaders(String[] columnHeaders) {
         this.columnHeaders = columnHeaders;
      }

      public String[][] getRowData() {
         return rowData;
      }

      public void setRowData(String[][] rowData) {
         this.rowData = rowData;
      }

      private String tableName;
      private int numRows;
      private String[] columnHeaders;
      private String[][] rowData;
   }

   private final VSInputServiceProxy vsInputServiceProxy;
}
