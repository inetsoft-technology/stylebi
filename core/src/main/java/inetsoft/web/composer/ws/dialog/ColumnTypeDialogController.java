/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.ws.dialog;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuery;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSColumnTypeEvent;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.text.Format;
import java.text.ParseException;

@Controller
public class ColumnTypeDialogController extends WorksheetController {
   /**
    * If this method is successful, the column type will change and then the socket controller
    * should be invoked by the client. Otherwise, it will throw an error message and not
    * change the column type.
    */
   @RequestMapping(
      value = "/api/composer/ws/column-type-validation/{runtimeId}",
      method = RequestMethod.POST)
   @ResponseBody
   public String updateColumnType(@PathVariable("runtimeId") String runtimeId,
                                  @RequestBody WSColumnTypeEvent event,
                                  Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String name = event.tableName();
      String type = event.dataType();
      String format_spec = event.formatSpec();
      TableAssembly table = (TableAssembly) ws.getAssembly(name);

      if(table != null) {
         TableAssemblyInfo info = table.getTableInfo();
         ColumnSelection columns = table.getColumnSelection();
         int colIdx = event.columnIndex();

         // skip invisible column in live move
         if(event.live()) {
            for(int i = 0; i <= colIdx; i++) {
               ColumnRef col = (ColumnRef) columns.getAttribute(i);

               if(!col.isVisible()) {
                  colIdx++;
               }
            }
         }

         ColumnRef column = (ColumnRef) columns.getAttribute(colIdx);
         ColumnRef column2 = (ColumnRef) columns.findAttribute(column);
         String oldType = null;

         if(column2 != null) {
            oldType = column2.getDataType();
            column2.setDataType(type);
         }

         Format format = null;
         String formatType = null;
         String pattern = null;

         if(format_spec != null) {
            formatType = format_spec.substring(0, format_spec.indexOf(':'));
            pattern = format_spec.substring(format_spec.indexOf(':') + 1);

            if(pattern.isEmpty() || "NONE".equals(pattern)) {
               pattern = null;
            }

            format = TableFormat.getFormat(formatType, pattern);
         }

         if(info instanceof TabularTableAssemblyInfo) {
            TabularQuery query = ((TabularTableAssemblyInfo) info).getQuery();

            if(query != null) {
               query.setColumnType(column.getDataRef().getName(), type);
               query.setColumnFormat(column.getDataRef().getName(), formatType);
               query.setColumnFormatExtent(column.getDataRef().getName(), pattern);
            }
         }
         else if(table instanceof EmbeddedTableAssembly) {
            EmbeddedTableAssembly etable = (EmbeddedTableAssembly) ws.getAssembly(name);
            XEmbeddedTable data = etable.getEmbeddedData();
            XSwappableTable originalTable = null;

            if(table instanceof SnapshotEmbeddedTableAssembly) {
               originalTable = ((SnapshotEmbeddedTableAssembly) table).getOriginalTable();
            }

            int index = AssetUtil.findColumn(data, column);

            if(index >= 0) {
               try {
                  data.setDataType(index, type, format, originalTable, event.confirmed());
                  // force column selection to be updated
                  // this shouldn't be necessary since the only thing changed is the column
                  // type, which is correct already. reseting column selection causes
                  // column order to be lost
                  // etable.setColumnSelection(new ColumnSelection(), false);
                  etable.setEmbeddedData(data);
               }
               catch(IllegalArgumentException | ParseException ex) {
                  return handleChangeTypeParseException(ex, column2, oldType);
               }
            }
         }
         else if(table instanceof UnpivotTableAssembly) {
            XFormatInfo formatInfo = null;

            if(format_spec != null) {
               formatInfo = new XFormatInfo(formatType, pattern);
            }

            ((UnpivotTableAssembly) table).changeColumnType(column, type, formatInfo, event.confirmed());

            try {
               AssetQuerySandbox box = rws.getAssetQuerySandbox();
               TableAssembly clone = (TableAssembly) table.clone();
               clone.setLiveData(true);
               clone.setRuntime(table.isRuntimeSelected());
               clone.setEditMode(false);
               final int mode = WorksheetEventUtil.getMode(clone);
               VariableTable variableTable = box.getVariableTable().clone();
               AssetQuery query = AssetQuery.createAssetQuery(
                  clone, mode, box, false, -1L, true, false);
               AssetQuery.THROW_EXECUTE_EXCEPTION.set(true);
               query.getTableLens(variableTable);
            }
            catch(IllegalArgumentException | ParseException ex) {
               return handleChangeTypeParseException(ex, column2, oldType);
            }
            finally {
               AssetQuery.THROW_EXECUTE_EXCEPTION.set(false);
            }
         }

         AssetQuerySandbox box = rws.getAssetQuerySandbox();
         box.resetTableLens(name);
         WorksheetEventUtil.clearDataCache(table, box);
      }

      return null;
   }

   private String handleChangeTypeParseException(Exception ex, ColumnRef column, String oldType) {
      Catalog catalog = Catalog.getCatalog();
      String message = catalog.getString("composer.ws.changeColumnType.failed", ex.getMessage());
      String confirmMsg = message + " " + catalog.getString("composer.ws.changeColumnType.proposal");

      if(LOG.isDebugEnabled()) {
         LOG.debug(message, ex);
      }
      else {
         LOG.warn(message);
      }

      if(column != null) {
         column.setDataType(oldType);
      }

      return confirmMsg;
   }

   @Undoable
   @LoadingMask
   @InitWSExecution
   @MessageMapping("composer/worksheet/column-type")
   public void updateColumnType(
      @Payload WSColumnTypeEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String name = event.tableName();
      TableAssembly table = (TableAssembly) ws.getAssembly(name);

      if(table != null) {
         WorksheetEventUtil.refreshColumnSelection(rws, name, true);
         WorksheetEventUtil.syncDataTypes(rws, table, commandDispatcher, principal);
         WorksheetEventUtil.loadTableData(rws, name, true, true);
         WorksheetEventUtil.refreshAssembly(rws, name, true, commandDispatcher, principal);
         AssetEventUtil.refreshTableLastModified(ws, name, true);
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(ColumnTypeDialogController.class);
}
