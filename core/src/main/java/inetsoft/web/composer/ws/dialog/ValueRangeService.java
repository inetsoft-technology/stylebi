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

package inetsoft.web.composer.ws.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.internal.Util;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ColumnInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.binding.model.*;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.ws.ValueRangeDialogModel;
import inetsoft.web.composer.model.ws.ValueRangeDialogModelValidator;
import inetsoft.web.composer.ws.RenameColumnController;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;

@Service
@ClusterProxy
public class ValueRangeService extends WorksheetControllerService {

   public ValueRangeService(ViewsheetService viewsheetService,
                            DataRefModelFactoryService dataRefModelFactoryService)
   {
      super(viewsheetService);
      this.dataRefModelFactoryService = dataRefModelFactoryService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ValueRangeDialogModel valueRangeModel(@ClusterProxyKey String runtimeId, String tableName,
                                                String expColumnName, String fromColumn, boolean numeric,
                                                Principal principal) throws Exception
   {
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(runtimeId, principal);
      TableAssembly assembly = (TableAssembly) rws.getWorksheet().getAssembly(tableName);

      if(assembly != null) {
         ColumnSelection cols = assembly.getColumnSelection();
         ColumnRef fcolumn = findColumn(cols, fromColumn);

         ValueRangeDialogModel model = new ValueRangeDialogModel();

         if(expColumnName != null) {
            ColumnRef ref = (ColumnRef) assembly.getColumnSelection()
               .getAttribute(expColumnName);

            if(ref.getDataRef() instanceof DateRangeRef) {
               DateRangeRefModel dateRangeRefModel = (DateRangeRefModel) dataRefModelFactoryService
                  .createDataRefModel(ref.getDataRef());
               model.setOldName(dateRangeRefModel.getName());
               model.setRef(dateRangeRefModel);
            }
            else if(ref.getDataRef() instanceof NumericRangeRef) {
               NumericRangeRefModel numericRangeRefModel = (NumericRangeRefModel) dataRefModelFactoryService
                  .createDataRefModel(ref.getDataRef());
               model.setOldName(numericRangeRefModel.getName());
               model.setRef(numericRangeRefModel);
            }
            else {
               return null;
            }
         }
         else if(fcolumn != null) {
            DataRef ref = fcolumn.getDataRef();
            String type = fcolumn.getDataType();
            ExpressionRef rangeRef;

            if(numeric) {
               rangeRef = new NumericRangeRef("", ref);
               ValueRangeInfo info = new ValueRangeInfo();
               info.setValues(new double[0]);
               ((NumericRangeRef) rangeRef).setValueRangeInfo(info);
            }
            else {
               rangeRef = new DateRangeRef("", ref);
               ((DateRangeRef) rangeRef).setOriginalType(type);
               ((DateRangeRef) rangeRef).setDateOption(DateRangeRef.YEAR_INTERVAL);
               ((DateRangeRef) rangeRef).setAutoCreate(false);
            }

            model.setRef((ExpressionRefModel) dataRefModelFactoryService
               .createDataRefModel(rangeRef));
         }

         return model;
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void editValueRange(@ClusterProxyKey String runtimeId, ValueRangeDialogModel model,
                              String tableName, Principal principal,  CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String columnName = model.getOldName();
      TableAssembly table = (TableAssembly) ws.getAssembly(tableName);

      if(table != null) {
         editOldColumn(rws, model, tableName, columnName, commandDispatcher, principal);
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ValueRangeDialogModelValidator validateValueRange(@ClusterProxyKey String runtimeId,
                                                            ValueRangeDialogModel model,
                                                            String tableName, Principal principal) throws Exception
   {
      RuntimeWorksheet rws = super.getWorksheetEngine()
         .getWorksheet(Tool.byteDecode(runtimeId), principal);
      TableAssembly table = (TableAssembly) rws.getWorksheet().getAssembly(tableName);
      ColumnSelection columns = table.getColumnSelection();
      String columnName = model.getNewName();
      ColumnRef conflictingColumn = AssetUtil.findColumnConflictingWithAlias(
         columns, null, columnName, true);
      ValueRangeDialogModelValidator.Builder builder = ValueRangeDialogModelValidator.builder();

      if(conflictingColumn != null) {
         builder.invalidName(RenameColumnController.createColumnConflictErrorMessage(
            model.getNewName(), conflictingColumn));
      }

      return builder.build();
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void newValueRange(@ClusterProxyKey String runtimeId, ValueRangeDialogModel model,
                             String tableName, String fromColumn, Principal principal,
                             CommandDispatcher commandDispatcher) throws Exception
   {
      tableName = Tool.byteDecode(tableName);
      fromColumn = Tool.byteDecode(fromColumn);
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      TableAssembly table = (TableAssembly) ws.getAssembly(tableName);

      if(table != null) {
         if(fromColumn != null) {
            createNewColumn(rws, model.getRef(), table, tableName, fromColumn,
                            model.getNewName(), commandDispatcher, principal);
         }
      }

      return null;
   }

   private void createNewColumn(
      RuntimeWorksheet rws, ExpressionRefModel eref, TableAssembly table,
      String tableName, String fromColumn, String columnName,
      CommandDispatcher commandDispatcher, Principal principal) throws Exception
   {
      ColumnSelection columns = table.getColumnSelection();

      if(columns.getAttributeCount() >= Util.getOrganizationMaxColumn()) {
         MessageCommand command = new MessageCommand();
         command.setMessage(Util.getColumnLimitMessage());
         command.setType(MessageCommand.Type.ERROR);
         commandDispatcher.sendCommand(command);
         return;
      }

      ColumnRef column = findColumn(columns, fromColumn);

      if(AssetUtil.findColumnConflictingWithAlias(columns, null, columnName, true) != null) {
         MessageCommand command = new MessageCommand();
         command.setMessage(Catalog.getCatalog().getString(
            "common.duplicateName"));
         command.setType(MessageCommand.Type.ERROR);
         command.setAssemblyName(tableName);
         commandDispatcher.sendCommand(command);
         return;
      }

      int index = columns.getAttributeCount();
      DataRef ref = column.getDataRef();
      String type = column.getDataType();
      ExpressionRef rangeRef;

      if(eref instanceof NumericRangeRefModel) {
         rangeRef = new NumericRangeRef(columnName, ref);
         ValueRangeInfo info = ((NumericRangeRefModel) eref).getVinfo()
            .convertFromModel();
         ((NumericRangeRef) rangeRef).setValueRangeInfo(info);
      }
      else if(eref instanceof DateRangeRefModel) {
         DateRangeRefModel dref = (DateRangeRefModel) eref;
         rangeRef = new DateRangeRef(columnName, ref);
         ((DateRangeRef) rangeRef).setOriginalType(dref.getOriginalType());
         ((DateRangeRef) rangeRef).setDateOption(dref.getOption());
         ((DateRangeRef) rangeRef).setAutoCreate(dref.getAutoCreate());
      }
      else {
         return;
      }

      ColumnRef ncolumn = new ColumnRef(rangeRef);
      String dtype = rangeRef.getDataType();

      if(dtype != null) {
         ncolumn.setDataType(dtype);
      }

      columns = columns.clone(true);
      columns.addAttribute(index, ncolumn);
      table.setColumnSelection(columns);
      AggregateInfo group = table.getAggregateInfo();

      // @by larryl, if aggregate is defined, add the new formula to the
      // groups so the formula would not 'disappear' from the table
      if(group != null && !group.isEmpty() && table.isAggregate() &&
         table.isPlain())
      {
         group.addGroup(new GroupRef(ncolumn));
      }

      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      WorksheetEventUtil.refreshColumnSelection(rws, tableName, true);
      WorksheetEventUtil.loadTableData(rws, tableName, true, true);
      List<ColumnInfo> items = box
         .getColumnInfos(tableName, AssetEventUtil.getMode(table));

      WorksheetEventUtil.refreshAssembly(rws, tableName, true, commandDispatcher, principal);
      WorksheetEventUtil.layout(rws, commandDispatcher);
      AssetEventUtil.refreshTableLastModified(rws.getWorksheet(), tableName, true);
   }

   private void editOldColumn(
      RuntimeWorksheet rws, ValueRangeDialogModel model, String tableName,
      String columnName, CommandDispatcher commandDispatcher, Principal principal) throws Exception
   {
      Worksheet ws = rws.getWorksheet();
      TableAssembly table = (TableAssembly) ws.getAssembly(tableName);
      AggregateInfo ainfo = table.getAggregateInfo();
      ColumnSelection privColumns = table.getColumnSelection(false);

      ColumnRef column = (ColumnRef) privColumns.getAttribute(columnName);
      int index = privColumns.indexOfAttribute(column);
      privColumns.removeAttribute(column);
      DataRef rangeref = column.getDataRef();

      if(rangeref instanceof NumericRangeRef && model.getRef() instanceof NumericRangeRefModel) {
         ((NumericRangeRef) rangeref).setValueRangeInfo(
            ((NumericRangeRefModel) model.getRef()).getVinfo().convertFromModel());
      }
      else if(rangeref instanceof DateRangeRef && model .getRef() instanceof DateRangeRefModel) {
         GroupRef groupRef = ainfo.getGroup(rangeref.getName());
         DateRangeRef dateRef = (DateRangeRef) rangeref;
         dateRef.setDateOption(((DateRangeRefModel) model.getRef()).getOption());

         if(groupRef != null) {
            dateRef.setName(DateRangeRef.getName(dateRef.getDataRef().getName(),
                                                 dateRef.getDateOption()));
            groupRef.setDateGroup(dateRef.getDateOption());
            groupRef.setDataRef(dateRef);
         }
      }

      ColumnRef ncolumn = new ColumnRef(rangeref);
      ncolumn.copyAttributes(column);
      // data type should not be copied, otherwise change level from part
      // to range or from range to part, the meta data format is wrong
      ncolumn.setDataType(rangeref.getDataType());
      ncolumn.setOldName(column.getOldName());
      ncolumn.setLastOldName(column.getLastOldName());

      if(privColumns.containsAttribute(ncolumn)) {
         privColumns.removeAttribute(column);
      }

      privColumns.addAttribute(index, ncolumn);
      table.setColumnSelection(privColumns);

      String oldName = model.getOldName();
      String newName = model.getNewName();

      if(newName != null && !newName.equals(oldName)) {
         RenameColumnController
            .renameColumn(commandDispatcher, table, column, newName);
      }

      WorksheetEventUtil.refreshColumnSelection(rws, tableName, true);
      WorksheetEventUtil.loadTableData(rws, tableName, true, true);
      WorksheetEventUtil.refreshAssembly(rws, tableName, true, commandDispatcher, principal);
      WorksheetEventUtil.layout(rws, commandDispatcher);
      AssetEventUtil.refreshTableLastModified(ws, tableName, true);
   }

   /**
    * Get name for a DataRef.
    */
   private String getRefName(DataRef ref) {
      if(ref instanceof DateRangeRef) {
         DateRangeRef dRef = (DateRangeRef) ref;
         int option = dRef.getDateOption();
         return DateRangeRef.getName(dRef.getDataRef().getAttribute(), option);
      }

      return ref.getAttribute();
   }

   private ColumnRef findColumn(ColumnSelection cols, String name) {
      ColumnRef fcolumn = (ColumnRef) cols.getAttribute(name);

      if(fcolumn == null) {
         for(int i = 0; i < cols.getAttributeCount(); i++) {
            DataRef ref = cols.getAttribute(i);

            if(StringUtils.isEmpty(ref.getAttribute()) &&
               Tool.equals(name, "Column [" + i + "]", false))
            {
               fcolumn = (ColumnRef) ref;
               break;
            }
         }
      }

      return fcolumn;
   }

   private final DataRefModelFactoryService dataRefModelFactoryService;
}
