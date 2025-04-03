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
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package inetsoft.web.composer.ws;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.*;
import inetsoft.report.internal.Util;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.Catalog;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSEditTableDataEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
@ClusterProxy
public class WSEditTableDataService extends WorksheetControllerService {

   public WSEditTableDataService(ViewsheetService viewsheetService)
   {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void editTableData(@ClusterProxyKey String runtimeId,  WSEditTableDataEvent event,
                             Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      final WSEditTableDataKeyTuple keyTuple =
         new WSEditTableDataKeyTuple(runtimeId, event.getTableName());
      WSEditTableDataQueue<WSEditTableDataEvent> queue;
      boolean workingThread = false;

      synchronized(queueMap) {
         queue = queueMap.get(keyTuple);

         if(queue == null) {
            workingThread = true;
            queue = new WSEditTableDataQueue<>();
            queueMap.put(keyTuple, queue);
         }

         queue.add(event);
      }

      if(!workingThread) {
         return null;
      }

      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String name = event.getTableName();
      EmbeddedTableAssembly table =
         (EmbeddedTableAssembly) ws.getAssembly(name);

      while(!queue.isEmpty()) {
         event = queue.poll();
         String data = event.getEditData();

         XEmbeddedTable tdata = table.getEmbeddedData();
         ColumnSelection selection = table.getColumnSelection();
         int x = event.getX();
         int y = event.getY();

         if(data != null && data.length() == 0) {
            data = null;
         }

         if(data != null && data.length() > Util.getOrganizationMaxCellSize()) {
            data = data.substring(0, Util.getOrganizationMaxCellSize());
            MessageCommand command = new MessageCommand();
            command.setMessage(Util.getTextLimitMessage());
            command.setType(MessageCommand.Type.WARNING);
            command.setAssemblyName(name);
            commandDispatcher.sendCommand(command);
         }

         // find the index in the embedded data table (without formula)
         x = AssetUtil.findColumn(tdata, selection.getAttribute(x));
         ColumnRef column = AssetUtil.findColumn(tdata, x, selection);

         if(column == null) {
            LOG.warn("Column not found: " + x);
            MessageCommand command = new MessageCommand();
            command.setMessage(
               Catalog.getCatalog().
                  getString("common.invalidTableColumn", x + ""));
            command.setType(MessageCommand.Type.ERROR);
            command.setAssemblyName(name);
            commandDispatcher.sendCommand(command);
            continue;
         }

         boolean error = false;
         data = data == null || data.length() == 0 ? null : data;

         try {
            if(column.getDataType().equalsIgnoreCase(XSchema.DATE) && "1900-01-01".equals(data)) {
               tdata.setObject(y, x, new SimpleDateFormat("yyyy-MM-dd").parse("1900-01-01"));
            }
            else {
               tdata.setObject(y, x, AssetUtil.parse(column.getDataType(), data));
            }

            table.setEmbeddedData(tdata);
         }
         catch(Exception e) {
            error = true;
         }

         if(!error) {
            queue.lastSuccesfulEvent = event;
         }

         if(table instanceof SnapshotEmbeddedTableAssembly) {
            ((SnapshotEmbeddedTableAssembly) table).deleteDataFiles(
               "Embedded data edited: " + name);
         }

         if(error) {
            MessageCommand command = new MessageCommand();
            command.setMessage(
               Catalog.getCatalog().getString("common.dataFormatErrorParam", data));
            command.setType(MessageCommand.Type.ERROR);
            command.setAssemblyName(name);
            commandDispatcher.sendCommand(command);
         }
         else {
            // fix bug1335947769178, the DataKey is created by some data value,
            // so some data changed will not changed the key, so remove it here
            try {
               AssetQuerySandbox box = rws.getAssetQuerySandbox();
               int mode = AssetEventUtil.getMode(table);
               DataKey key = AssetDataCache.getCacheKey(table, box, null, mode, true);
               AssetDataCache.removeCachedData(key);
            }
            catch(Exception ex) {
               LOG.debug("Failed to remove cached data", ex);
            }
         }

         synchronized(queueMap) {
            if(queue.size() == 0) {
               queueMap.remove(keyTuple);

               // this step is intentionally within the synchronized block to force other
               // invocations to wait and build up the queue.
               if(queue.lastSuccesfulEvent != null) {
                  int lastY = queue.lastSuccesfulEvent.getY();
                  int start = 1;

                  if(lastY / WorksheetController.BLOCK > 0) {
                     start = ((lastY - 1) / WorksheetController.BLOCK) *
                        WorksheetController.BLOCK + 1;
                  }

                  WorksheetEventUtil.loadTableData(rws, name, true, true);
                  WorksheetEventUtil.refreshAssembly(
                     rws, name, true, commandDispatcher, principal);
                  AssetEventUtil.refreshTableLastModified(ws, name, true);
               }

               break;
            }
         }
      }

      return null;
   }


   private class WSEditTableDataQueue<T> extends ConcurrentLinkedQueue<T> {
      WSEditTableDataEvent lastSuccesfulEvent;
   }

   private class WSEditTableDataKeyTuple {
      private String runtimeId;
      private String tableName;

      public WSEditTableDataKeyTuple(String runtimeId, String tableName) {
         this.runtimeId = runtimeId;
         this.tableName = tableName;
      }

      @Override
      public boolean equals(Object other) {
         if(this == other) {
            return true;
         }

         if(!(other instanceof WSEditTableDataKeyTuple)) {
            return false;
         }

         WSEditTableDataKeyTuple tuple = (WSEditTableDataKeyTuple) other;
         return Objects.equals(runtimeId, tuple.runtimeId) &&
            Objects.equals(tableName, tuple.tableName);
      }

      @Override
      public int hashCode() {
         return Objects.hash(runtimeId, tableName);
      }
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(WSEditTableDataController.class);
   private final Map<WSEditTableDataKeyTuple, WSEditTableDataQueue<WSEditTableDataEvent>> queueMap = new HashMap<>();

}
