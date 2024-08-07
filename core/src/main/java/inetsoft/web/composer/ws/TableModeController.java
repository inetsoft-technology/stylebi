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

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XQuery;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.TableAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.*;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Controller
public class TableModeController extends WorksheetController {
   /**
    * From 12.2 DefaultEvent.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/table-mode/default")
   public void setDefaultMode(
      @Payload WSAssemblyEvent event,
      Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String tableName = event.getAssemblyName();
      TableAssembly table = (TableAssembly) ws.getAssembly(tableName);
      AssetQuerySandbox box = rws.getAssetQuerySandbox();

      if(table != null) {
         setDefaultTableMode(table, box);
         applyChanges(commandDispatcher, rws, tableName, principal);
      }
   }

   @LoadingMask
   @MessageMapping("/composer/worksheet/dependings-table-mode/default")
   public void setDependencyJoinTableToDefaultMode(@Payload WSRefreshAssemblyEvent event,
                                                   Principal principal,
                                                   CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String tableName = event.getAssemblyName();
      TableAssembly table = (TableAssembly) ws.getAssembly(tableName);
      AssetQuerySandbox box = rws.getAssetQuerySandbox();

      if(table != null) {
         setDependingsDefaultTableMode(table, ws, box);
         applyChanges(commandDispatcher, rws, tableName, principal, event.isRecursive(),
            event.isReset());
      }
   }

   /**
    * From 12.2 LivePreviewEvent.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/table-mode/live")
   public void setLiveMode(
      @Payload WSAssemblyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String tableName = event.getAssemblyName();
      TableAssembly table = (TableAssembly) ws.getAssembly(tableName);

      if(table != null) {
         setLiveTableMode(table);
         applyChanges(commandDispatcher, rws, tableName, principal);
      }
   }

   /**
    * From 12.2 FullEvent.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/table-mode/full")
   public void setFullMode(
      @Payload WSAssemblyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String tableName = event.getAssemblyName();
      TableAssembly table = (TableAssembly) ws.getAssembly(tableName);
      AssetQuerySandbox box = rws.getAssetQuerySandbox();

      if(table != null) {
         table.setLiveData(false);
         table.setRuntime(false);
         table.setEditMode(false);
         TableAssemblyInfo info = table.getTableInfo();
         info.setAggregate(false);
         info.setPixelSize(new Dimension(AssetUtil.defw, info.getPixelSize().height));

         if(table.isComposed() && table instanceof ComposedTableAssembly) {
            ComposedTableAssembly ctable = (ComposedTableAssembly) table;

            if(!table.isRuntime()) {
               ctable.setHierarchical(false);
               box.resetTableLens(ctable.getName(), AssetQuerySandbox.DESIGN_MODE);
            }
         }

         applyChanges(commandDispatcher, rws, tableName, principal);
      }
   }

   /**
    * From 12.2 DetailPreviewEvent.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/table-mode/detail")
   public void setDetailMode(
      @Payload WSAssemblyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String tableName = event.getAssemblyName();
      TableAssembly table = (TableAssembly) ws.getAssembly(tableName);

      if(table != null) {
         table.setLiveData(true);
         table.setRuntime(false);
         table.setEditMode(false);
         TableAssemblyInfo info = table.getTableInfo();
         info.setAggregate(false);
         info.setPixelSize(new Dimension(AssetUtil.defw, info.getPixelSize().height));
         applyChanges(commandDispatcher, rws, tableName, principal);
      }
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/table-mode/set-runtime")
   public void setRuntime(
      @Payload WSSetRuntimeEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String tableName = event.tableName();
      TableAssembly table = (TableAssembly) ws.getAssembly(tableName);
      final AssetQuerySandbox box = rws.getAssetQuerySandbox();
      box.getVariableTable().remove(XQuery.HINT_MAX_ROWS);

      if(table != null) {
         table.setRuntimeSelected(event.runtimeSelected());
         setLiveTableMode(table);
         applyChanges(commandDispatcher, rws, tableName, principal);
      }
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/table-mode/edit")
   public void setEditMode(
      @Payload WSAssemblyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String tableName = event.getAssemblyName();
      TableAssembly table = (TableAssembly) ws.getAssembly(tableName);

      if(table != null) {
         table.setRuntime(false);
         table.setEditMode(true);
         TableAssemblyInfo info = table.getTableInfo();
         info.setAggregate(false);
         info.setPixelSize(new Dimension(AssetUtil.defw, info.getPixelSize().height));
      }

      applyChanges(commandDispatcher, rws, tableName, principal);
   }

   @LoadingMask
   @MessageMapping("/composer/worksheet/refresh-data")
   public void refreshLiveData(
      @Payload WSAssemblyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String tableName = event.getAssemblyName();
      TableAssembly table = (TableAssembly) ws.getAssembly(tableName);

      if(table != null) {
         AssetQuerySandbox box = rws.getAssetQuerySandbox();

         try {
            int mode = AssetEventUtil.getMode(table);
            DataKey key = AssetDataCache.getCacheKey(table, box, null, mode, true);
            AssetDataCache.removeCachedData(key);
            box.resetTableLens(tableName);

            TableAssembly clone = (TableAssembly) table.clone();
            AssetQuery query = AssetQuery.createAssetQuery(
               clone, mode, box, false, -1L, true, false);

            if(query instanceof BoundQuery) {
               VariableTable vars = (VariableTable) box.getVariableTable().clone();
               vars.put(XQuery.HINT_PREVIEW, "true");
               ((BoundQuery) query).clearQueryCache(box.getVariableTable());
            }

            box.getVariableTable().put("__refresh_report__", "true");
            applyChanges(commandDispatcher, rws, tableName, principal);
         }
         catch(Exception ex) {
            LOG.debug("Failed to remove cached data", ex);
         }
         finally {
            box.getVariableTable().remove("__refresh_report__");
         }
      }
   }

   @LoadingMask
   @MessageMapping("/composer/worksheet/table/refresh-data")
   public void refreshData(
      @Payload WSRefreshAssemblyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      String tableName = event.getAssemblyName();
      applyChanges(commandDispatcher, rws, tableName, principal, event.isRecursive(),
         event.isReset());
   }

   public static void setDependingsDefaultTableMode(TableAssembly table, Worksheet ws,
                                                    AssetQuerySandbox box)
   {
      setDependingsDefaultTableMode0(table, ws, box, false, new HashMap<>());
   }

   public static void setDependingsDefaultTableMode0(TableAssembly table, Worksheet ws,
                                                     AssetQuerySandbox box, boolean isJoinTableSub,
                                                     Map<TableAssembly, Boolean> visited)
   {
      if(isJoinTableSub && table.isLiveData()) {
         setDefaultTableMode(table, box);
      }

      if(visited.get(table) != null) {
         return;
      }

      visited.put(table, true);
      AssemblyRef[] assemblyRefs = ws.getDependings(table.getAssemblyEntry());

      if(assemblyRefs == null) {
         return;
      }

      for(AssemblyRef assemblyRef : assemblyRefs) {
         if(assemblyRef == null || assemblyRef.getEntry() == null) {
            continue;
         }

         Assembly assembly = ws.getAssembly(assemblyRef.getEntry().getName());

         if(assembly instanceof CompositeTableAssembly) {
            TableAssemblyOperator operator = ((CompositeTableAssembly) assembly)
               .getOperator(table.getName());

            if(operator == null) {
               continue;
            }

            TableAssemblyOperator.Operator[] operators = operator.getOperators();

            for(TableAssemblyOperator.Operator op : operators) {
               if(op.isJoin()) {
                  setDefaultTableMode((TableAssembly) assembly, box);
                  isJoinTableSub = true;
               }
            }
         }

         setDependingsDefaultTableMode0((TableAssembly) assembly, ws, box, isJoinTableSub, visited);
      }
   }

   public static void setDefaultTableMode(TableAssembly table, AssetQuerySandbox box) {
      table.setLiveData(table instanceof EmbeddedTableAssembly);
      table.setRuntime(false);
      table.setEditMode(false);
      TableAssemblyInfo info = table.getTableInfo();
      AggregateInfo group = table.getAggregateInfo();
      info.setAggregate(group != null && !group.isEmpty());
      info.setPixelSize(new Dimension(AssetUtil.defw, info.getPixelSize().height));

      if(table.isComposed() || table instanceof EmbeddedTableAssembly) {
         if(table instanceof ComposedTableAssembly && !table.isRuntime()) {
            ComposedTableAssembly ctable = (ComposedTableAssembly) table;
            ctable.setHierarchical(false);
            box.resetTableLens(ctable.getName(), AssetQuerySandbox.DESIGN_MODE);
            ctable.setPixelSize(new Dimension(AssetUtil.defw, ctable.getPixelSize().height));
         }
      }
   }

   private void setLiveTableMode(TableAssembly table) {
      table.setLiveData(true);
      table.setRuntime(table.isRuntimeSelected());
      table.setEditMode(false);
      TableAssemblyInfo info = table.getTableInfo();
      AggregateInfo group = table.getAggregateInfo();
      info.setAggregate(group != null && !group.isEmpty());
      info.setPixelSize(new Dimension(AssetUtil.defw, info.getPixelSize().height));
   }

   private void applyChanges(
      CommandDispatcher commandDispatcher, RuntimeWorksheet rws, String tableName,
      Principal principal) throws Exception
   {
      applyChanges(commandDispatcher, rws, tableName, principal, false, false);
   }

   private void applyChanges(
      CommandDispatcher commandDispatcher, RuntimeWorksheet rws, String tableName,
      Principal principal, boolean recursive, boolean reset) throws Exception
   {
      WorksheetEventUtil.loadTableData(rws, tableName, recursive, reset);
      WorksheetEventUtil.refreshAssembly(rws, tableName, recursive, commandDispatcher, principal);
      WorksheetEventUtil.layout(rws, commandDispatcher);
      final UserMessage msg = Tool.getUserMessage();

      if(msg != null) {
         final MessageCommand command = MessageCommand.fromUserMessage(msg);
         command.setAssemblyName(tableName);
         commandDispatcher.sendCommand(command);
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(TableModeController.class);
}
