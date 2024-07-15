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
package inetsoft.web.composer.ws.dialog;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.ColumnInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.*;
import inetsoft.web.binding.drm.AbstractDataRefModel;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.ws.ReorderColumnsDialogModel;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller
public class ReorderColumnsDialogController extends WorksheetController {
   @RequestMapping(
      value = "/api/composer/ws/dialog/reorder-columns-dialog-model/{runtimeid}",
      method = RequestMethod.GET)
   @ResponseBody
   public ReorderColumnsDialogModel getModel(
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

      List<ColumnRefModel> cols = getColumnRefModels(box, table);

      return ReorderColumnsDialogModel
         .builder()
         .columns(cols)
         .indexes(IntStream.range(0, cols.size()).toArray())
         .build();
   }

   private List<ColumnRefModel> getColumnRefModels(AssetQuerySandbox box, AbstractTableAssembly table) throws Exception {
      List<ColumnInfo> columnInfos = box.getColumnInfos(
         table.getAbsoluteName(), WorksheetEventUtil.getMode(table));
      List<String> columnInfoNames = columnInfos.stream()
         .map(c -> c.getName())
         .collect(Collectors.toList());
      ColumnSelection columnSelection = table.getColumnSelection(false);
      List<ColumnRefModel> cols = new ArrayList<>();

      for(String columnName : columnInfoNames) {
         if(columnSelection.getAttribute(columnName) != null) {
            DataRef colRef = columnSelection.getAttribute(columnName);
            ColumnRefModel ref = (ColumnRefModel) this.dataRefModelFactoryService
               .createDataRefModel(colRef);
            ref.setName(AbstractDataRefModel.stripOuterPrefix(((ColumnRef) colRef).getDataRef().getName()));
            cols.add(ref);
         }
      }
      return cols;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/ws/dialog/reorder-columns-dialog-model/{table}")
   public void reorderColumns(
      @Payload ReorderColumnsDialogModel model,
      @DestinationVariable("table") String tableName,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      tableName = Tool.byteDecode(tableName);
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      TableAssembly table = (TableAssembly) ws.getAssembly(tableName);

      if(table == null || model.indexes() == null) {
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage("Could not reorder columns.");
         messageCommand.setType(MessageCommand.Type.ERROR);
         messageCommand.setAssemblyName(tableName);
         commandDispatcher.sendCommand(messageCommand);
         return;
      }

      int[] indexes = model.indexes();
      ColumnSelection oldColumnSelection = table.getColumnSelection();
      ColumnSelection newColumnSelection = new ColumnSelection();
      //The column names of current selected data view
      OrderedSet<String> dataViewColumnNames = new OrderedSet<>();
      AbstractTableAssembly tableObj =
         (AbstractTableAssembly) rws.getWorksheet().getAssembly(tableName);
      List<ColumnRefModel> cols = model.columns();

      if(cols.size() == 0) {
         cols = getColumnRefModels(rws.getAssetQuerySandbox(), tableObj);
      }

      for(int i = 0; i < indexes.length; i++) {
         ColumnRefModel refModel = cols.get(indexes[i]);
         dataViewColumnNames.add(refModel.getAlias() != null ? refModel.getAlias() :
            (refModel.getEntity() == null ? refModel.getName() :
            refModel.getEntity() + "." +  refModel.getAttribute()));
      }

      List<Integer> oldIndex = new ArrayList<>();
      List<String> newColumnRefNames = new ArrayList<>();

      for(String columnName : dataViewColumnNames) {
         for(int i = 0; i < oldColumnSelection.getAttributeCount(); i++) {
            DataRef ref = oldColumnSelection.getAttribute(i);
            newColumnRefNames.add(ref.getName());

            if(Tool.equals(columnName, ref.getName())) {
               oldIndex.add(i);
            }
         }
      }

      oldIndex.sort(new DefaultComparator());

      for(int i = 0; i < oldIndex.size(); i++) {
         int idx = oldIndex.get(i);
         newColumnRefNames.remove(idx);
         newColumnRefNames.add(idx, dataViewColumnNames.get(i));
      }

      for(String newColumnRefName : newColumnRefNames) {
         ColumnRef ref = (ColumnRef) oldColumnSelection.getAttribute(newColumnRefName);
         newColumnSelection.addAttribute(ref);
      }

      table.setColumnSelection(newColumnSelection, false);

      WorksheetEventUtil.refreshColumnSelection(rws, tableName, true);
      WorksheetEventUtil.loadTableData(rws, tableName, true, true);
      WorksheetEventUtil.refreshAssembly(rws, tableName, true, commandDispatcher, principal);
      WorksheetEventUtil.layout(rws, commandDispatcher);
      AssetEventUtil.refreshTableLastModified(ws, tableName, true);
   }

   @Autowired
   public void setDataRefModelFactoryService(
      DataRefModelFactoryService dataRefModelFactoryService)
   {
      this.dataRefModelFactoryService = dataRefModelFactoryService;
   }

   private DataRefModelFactoryService dataRefModelFactoryService;
}
