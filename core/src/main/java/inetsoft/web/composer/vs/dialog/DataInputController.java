/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.InputVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CheckBoxVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.InputVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.service.VSInputService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@Controller
public class DataInputController {
   /**
    * @param vsInputService      VSInputService instance
    * @param viewsheetService
    */
   @Autowired
   public DataInputController(
      VSInputService vsInputService,
      ViewsheetService viewsheetService)
   {
      this.vsInputService = vsInputService;
      this.viewsheetService = viewsheetService;
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
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(Tool.byteDecode(runtimeId), principal);
      ColumnSelection selection = this.vsInputService.getTableColumns(rvs,
                                                                      Tool.byteDecode(table), true, principal);
      String[] columnList = new String[selection.getAttributeCount()];
      String[] descriptionList = new String[selection.getAttributeCount()];

      for(int i = 0; i < selection.getAttributeCount(); i++) {
         ColumnRef columnref = (ColumnRef) selection.getAttribute(i);
         columnList[i] = columnref.getName();
         descriptionList[i] = columnref.getDescription();
      }

      Map<String, String[]> result = new HashMap<>();
      result.put("columnlist", columnList);
      result.put("descriptionlist", descriptionList);

      return result;
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
      table = Tool.byteDecode(table);
      column = Tool.byteDecode(column);
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(runtimeId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();

      if(column.startsWith("$")) {
         String variableAssembly = column.substring(2, column.length() - 1);

         InputVSAssembly assembly = (InputVSAssembly) viewsheet.getAssembly(variableAssembly);
         InputVSAssemblyInfo assemblyInfo = (InputVSAssemblyInfo) assembly.getVSAssemblyInfo();
         String selectedColumn = null;

         if(assemblyInfo instanceof CheckBoxVSAssemblyInfo) {
            Object[] objects =  assemblyInfo.getSelectedObjects();

            if(objects.length > 0) {
               selectedColumn = assemblyInfo.getSelectedObjects()[0].toString();
            }
         }
         else {
            selectedColumn = Objects.toString(assemblyInfo.getSelectedObject(), null);
         }

         return this.vsInputService.getColumnRows(rvs, table, selectedColumn, principal);
      }

      return this.vsInputService.getColumnRows(rvs, table, column, principal);
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
      PopupEmbeddedTable result = new PopupEmbeddedTable();
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return result;
      }

      Worksheet ws = vs.getBaseWorksheet();

      if(ws != null && VSEventUtil.checkBaseWSPermission(
         vs, principal, engine.getAssetRepository(), ResourceAction.READ))
      {
         TableAssembly tableAssembly = (TableAssembly) ws.getAssembly(table);

         if(tableAssembly instanceof SnapshotEmbeddedTableAssembly) {
            result = new PopupEmbeddedTable(
               ((SnapshotEmbeddedTableAssembly)tableAssembly).getEmbeddedData(), table);
         }
         else if(tableAssembly instanceof EmbeddedTableAssembly) {
            result = new PopupEmbeddedTable(
               VSEventUtil.getVSEmbeddedData((EmbeddedTableAssembly) tableAssembly), table);
         }
      }

      return result;
   }

   public static final class PopupEmbeddedTable {
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

   private final VSInputService vsInputService;
   private final ViewsheetService viewsheetService;
}
