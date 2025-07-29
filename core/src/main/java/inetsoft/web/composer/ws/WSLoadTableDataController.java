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
package inetsoft.web.composer.ws;

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.filter.SortFilter;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.GroupedThread;
import inetsoft.util.Tool;
import inetsoft.util.log.LogContext;
import inetsoft.util.script.ExpressionFailedException;
import inetsoft.web.composer.model.ws.WSTableData;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.command.WSLoadTableDataCommand;
import inetsoft.web.composer.ws.event.WSLoadTableDataEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class WSLoadTableDataController extends WorksheetController {
   /**
    * Load WS table data
    */
   @MessageMapping("/ws/table/reload-table-data/{name}")
   public void loadWSTableData(
      @DestinationVariable("name") String assemblyName,
      @Payload WSLoadTableDataEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      int startRow = event.start();
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      TableAssembly table = (TableAssembly) rws.getWorksheet().getAssembly(assemblyName);

      if(table == null) {
         return;
      }

      boolean databricks = Tool.isDatabricks(table.getSource());
      SortInfo sInfo = table.getSortInfo();
      boolean sortData = sInfo != null && sInfo.isTempSort() && sInfo.getSortCount() > 0;

      GroupedThread.withGroupedThread(groupedThread -> {
         AssetEntry wsEntry = rws.getEntry();

         if(wsEntry != null) {
            groupedThread.addRecord(LogContext.WORKSHEET, wsEntry.getPath());
         }

         groupedThread.addRecord(LogContext.ASSEMBLY, assemblyName);
      });

      if(table != null) {
         TableLens lens = box.getTableLens(assemblyName, WorksheetEventUtil.getMode(table));
         Exception ex = null;

         if(sortData) {
            if(event.firstChange()) {
               sInfo.setTempSort(false);
               WorksheetEventUtil.loadTableData(rws, assemblyName, true, true);
               WorksheetEventUtil.refreshAssembly(rws, assemblyName, true, commandDispatcher, principal);
            }
            else {
               SortRef sortRef = table.getSortInfo().getSort(0);

               for(int i = 0; i < lens.getColCount(); i++) {
                  final String column = lens.getColumnIdentifier(i);
                  String header = lens.getObject(0, i).toString();

                  if(Tool.equals(column, sortRef.getName()) || Tool.equals(header, sortRef.getName())) {
                     lens = new SortFilter(lens, new int[] { i },
                                           sortRef.getOrder() == XConstants.SORT_ASC);

                     break;
                  }
               }
            }
         }

         try {
            lens.moreRows(startRow + event.blockSize());
         }
         catch(ExpressionFailedException e) {
            ex = e;
         }

         final int numCols = lens.getColCount();
         final int rowCount = lens.getRowCount();
         final boolean completed = rowCount >= 0 || ex != null;
         final int availableRowCount = Math.max((rowCount < 0 ? -rowCount - 1 : rowCount) - 1, 0);
         final int numRows = Math.min(availableRowCount, event.blockSize());

         String[][] rows = new String[numRows][numCols];
         int endRow = Math.min(startRow + numRows, availableRowCount);
         int dataSize = 0;
         // limit size of data to avoid oom (42576).
         final int MAX_DATA = 20 * 1024 * 1024; // 20m
         final int MAX_CELL = 32767; // same as xls

         for(int row = startRow; row < endRow; row++) {
            for(int col = 0; col < numCols; col++) {
               Object val = null;

               // script may fail, we just load null for failed cell. (58626)
               try {
                  val = lens.getObject(row + 1, col);
               }
               catch(Exception e) {
                  ex = e;
               }

               String str = AssetUtil.formatDbData(val, databricks);

               if(str.length() > MAX_CELL) {
                  str = str.substring(0, MAX_CELL);
               }

               rows[row - startRow][col] = str;
               dataSize += str.length();
            }

            if(dataSize > MAX_DATA) {
               endRow = row + 1;
               break;
            }
         }

         final WSTableData tableData = WSTableData.builder()
            .loadedRows(rows)
            .startRow(startRow)
            .endRow(endRow)
            .completed(completed)
            .build();

         final WSLoadTableDataCommand command = new WSLoadTableDataCommand();
         command.setTableData(tableData);
         command.setContinuation(event.continuation());
         command.setRequestId(event.requestId());

         commandDispatcher.sendCommand(assemblyName, command);

         if(ex != null) {
            throw ex;
         }
      }

      GroupedThread.withGroupedThread(groupedThread -> {
         AssetEntry wsEntry = rws.getEntry();

         if(wsEntry != null) {
            groupedThread.removeRecord(LogContext.WORKSHEET.getRecord(wsEntry.getPath()));
         }

         groupedThread.removeRecord(LogContext.ASSEMBLY.getRecord(assemblyName));
      });
   }
}
