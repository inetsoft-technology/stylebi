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
package inetsoft.web.composer.ws;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.ws.ShowHideColumnsDialogModel;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSSetColumnVisibilityEvent;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Controller
public class SetColumnVisibleController extends WorksheetController {
   /** From 12.2 SetColumnVisibleEvent */
   @Undoable
   @LoadingMask
   @InitWSExecution
   @MessageMapping("/composer/worksheet/set-column-visibility")
   public void setColumnVisibility(
      @Payload WSSetColumnVisibilityEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      final RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      final Worksheet ws = rws.getWorksheet();
      final String tname = event.getAssemblyName();
      final String[] columnName = event.getColumnName();
      final boolean showAll = event.getShowAll();
      final TableAssembly table = (TableAssembly) ws.getAssembly(tname);

      if(table == null) {
         return;
      }

      final ColumnSelection columns = table.getColumnSelection();
      boolean changed = false;

      if(showAll) {
         for(int i = 0; i < columns.getAttributeCount(); i++) {
            final ColumnRef ref = (ColumnRef) columns.getAttribute(i);
            ref.setVisible(true);
         }

         changed = true;
      }
      else {
         for(int i = 0; i < columnName.length; i++) {
            final ColumnRef ref = (ColumnRef) columns.getAttribute(columnName[i]);
            final int index = columns.indexOfAttribute(ref);
            final ColumnRef column = index < 0 ? null : (ColumnRef) columns.getAttribute(index);

            if(column != null) {
               if(column.isVisible() && !supportHideColumn(table) && columnName.length - 1 == i) {
                  MessageCommand command = new MessageCommand();
                  command.setMessage(Catalog.getCatalog(principal).getString("composer.ws.embeddedTable.noVisibleColumn"));
                  command.setType(MessageCommand.Type.WARNING);
                  command.setAssemblyName(tname);
                  commandDispatcher.sendCommand(command);
                  continue;
               }

               if(column.isVisible() && !allowsDeletion(ws, table, column)) {
                  MessageCommand command = new MessageCommand();
                  command.setMessage(Catalog.getCatalog(principal).getString(
                     "common.columnDependency", column.getAttribute()));
                  command.setType(MessageCommand.Type.WARNING);
                  command.setAssemblyName(tname);
                  commandDispatcher.sendCommand(command);
                  continue;
               }

               column.setVisible(!column.isVisible());
               changed = true;
            }
         }
      }

      if(changed) {
         refreshColumns(table, columns, rws, tname, commandDispatcher, principal);
         AssetEventUtil.refreshTableLastModified(ws, tname, true);
      }
   }

   @RequestMapping(
      value = "/api/composer/ws/dialog/show-hide-columns-dialog-model/{runtimeid}",
      method = RequestMethod.GET)
   @ResponseBody
   public ShowHideColumnsDialogModel getModel(
      @PathVariable("runtimeid") String runtimeId,
      @RequestParam("table") String tableName,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      tableName = Tool.byteDecode(tableName);
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(runtimeId, principal);
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      AbstractTableAssembly table =
         (AbstractTableAssembly) rws.getWorksheet().getAssembly(tableName);

      if(table == null) {
         return null;
      }

      ColumnSelection columnSelection = table.getColumnSelection(false);
      List<ColumnRefModel> cols = new ArrayList<>();
      List<ColumnRef> aggRefs = new ArrayList<>();
      AggregateInfo aggregateInfo = table.getAggregateInfo();
      boolean aggregate = aggregateInfo != null && !aggregateInfo.isEmpty() &&
         table.getTableInfo() != null && table.getTableInfo().isAggregate();

      // When table has groups or aggregates, and is in MetaDataView or LiveDataView,
      // should get the column refs referenced by all groups and aggregates.
      if(aggregate) {
         GroupRef[] groups = aggregateInfo.getGroups();

         // If table is crosstab mode, only row header groups need to be shown.
         for(int i = 0; i < groups.length; i++) {
            if(table.isCrosstab() && i == 0) {
               continue;
            }

            aggRefs.add((ColumnRef) groups[i].getDataRef());
         }

         if(!table.isCrosstab()) {
            AggregateRef[] aggregates = aggregateInfo.getAggregates();

            for(AggregateRef aggregateRef : aggregates) {
               aggRefs.add((ColumnRef) aggregateRef.getDataRef());
            }
         }
      }

      for(int i = 0; i < columnSelection.getAttributeCount(); i++) {
         ColumnRef ref = (ColumnRef) columnSelection.getAttribute(i);
         ColumnRefModel refModel = null;

         if(!aggregate) {
            refModel = (ColumnRefModel) this.dataRefModelFactoryService.createDataRefModel(ref);
         }
         else if(aggRefs.contains(ref)) {
            refModel = (ColumnRefModel) this.dataRefModelFactoryService.createDataRefModel(ref);
         }

         if(refModel != null) {
            if(refModel.getAttribute().isEmpty()) {
               refModel.setAttribute("Column[" + cols.size() + "]");
            }

            cols.add(refModel);
         }
      }

      return ShowHideColumnsDialogModel
         .builder()
         .columns(cols)
         .build();
   }

   /**
    * Embedded table has no meta mode, so don't support hide all the columns, that will cause the
    * columns no longer be show.
    */
   private boolean supportHideColumn(TableAssembly table) {
      if(!(table instanceof EmbeddedTableAssembly || table instanceof MirrorTableAssembly)) {
         return true;
      }

      ColumnSelection columns = table.getColumnSelection();
      int count = columns.getAttributeCount();
      int hiddenCount = columns.getHiddenColumnCount();

      return count - hiddenCount > 1;
   }

   /**
    * Refresh the columns.
    */
   private void refreshColumns( TableAssembly table, ColumnSelection columns, RuntimeWorksheet rws,
                                String tname, CommandDispatcher commandDispatcher,
                                Principal principal) throws Exception
   {
      table.setColumnPropertyName("columns");
      table.setColumnSelection(columns);
      WorksheetEventUtil.refreshColumnSelection(rws, tname, true);
      WorksheetEventUtil.loadTableData(rws, tname, true, true);
      WorksheetEventUtil.refreshAssembly(rws, tname, true, commandDispatcher, principal);
      WorksheetEventUtil.layout(rws, commandDispatcher);
   }

   @Autowired
   private void setDataRefModelFactoryService(DataRefModelFactoryService dataRefModelFactoryService) {
      this.dataRefModelFactoryService = dataRefModelFactoryService;
   }

   private DataRefModelFactoryService dataRefModelFactoryService;
}
