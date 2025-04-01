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

package inetsoft.web.binding.dnd.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.execution.*;
import inetsoft.report.internal.Util;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.dnd.*;
import inetsoft.web.binding.event.VSDndEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSTableBindingHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.objects.controller.ComposerVSTableController;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.*;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Service
@ClusterProxy
public class VSTableDndService {

   public VSTableDndService(ViewsheetService viewsheetService,
                            VSTableBindingHandler tableHandler,
                            VSAssemblyInfoHandler assemblyInfoHandler,
                            VSBindingService bfactory,
                            CoreLifecycleService coreLifecycleService)
   {
      this.viewsheetService = viewsheetService;
      this.tableHandler = tableHandler;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.bfactory = bfactory;
      this.coreLifecycleService = coreLifecycleService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void dnd(@ClusterProxyKey String runtimeId, VSDndEvent event, Principal principal,
                   String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      if(rvs == null || event.getTransfer() == null) {
         return null;
      }

      // The data for what's being dropped onto the table, including the assembly that initiated
      // the drag event
      TableTransfer tableData = (TableTransfer) event.getTransfer();
      VSAssembly dragAssembly = rvs.getViewsheet().getAssembly(tableData.getAssembly());


      // The data for the table being dropped onto, in order to add or change the binding of a
      // column, including the table assembly that accepted the drop event
      BindingDropTarget dropTarget = (BindingDropTarget) event.getDropTarget();

      TableVSAssembly tableAssembly = (TableVSAssembly) rvs.getViewsheet().getAssembly(dropTarget.getAssembly());
      TableVSAssembly newTableAssembly = (TableVSAssembly) tableAssembly.clone();

      if(this.checkEmbeddedTableSource(rvs.getViewsheet(), dragAssembly.getTableName(),
                                       dragAssembly, tableAssembly, dispatcher))
      {
         return null;
      }

      SourceInfo sinfo = ((DataVSAssemblyInfo) tableAssembly.getInfo()).getSourceInfo();
      boolean sourceChanged = sinfo == null ||
         assemblyInfoHandler.sourceChanged(dragAssembly.getTableName(), tableAssembly);

      // Handle source changed.
      if(sourceChanged) {
         assemblyInfoHandler.changeSource(newTableAssembly, dragAssembly.getTableName(), event.getSourceType());
      }

      tableHandler.addRemoveColumns(newTableAssembly, dragAssembly, tableData.getDragIndex(),
                                    dropTarget.getDropIndex(), dropTarget.getReplace(),
                                    tableData.getDragType(), tableData.getTransferType());
      applyAssemblyInfo(rvs, tableAssembly, newTableAssembly, dispatcher,
                        event, null, linkUri);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void dndFromTree(@ClusterProxyKey String runtimeId, VSDndEvent event, Principal principal,
                           @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      if(rvs == null) {
         return null;
      }

      ViewsheetSandbox vbox = rvs.getViewsheetSandbox();
      vbox.lockRead();

      try {
         TableVSAssembly assembly = (TableVSAssembly) rvs.getViewsheet().getAssembly(event.name());
         TableVSAssembly nassembly = (TableVSAssembly) assembly.clone();

         SourceInfo sinfo = ((DataVSAssemblyInfo) assembly.getInfo()).getSourceInfo();
         boolean sourceChanged = sinfo == null ||
            assemblyInfoHandler.sourceChanged(event.getTable(), assembly);

         // Handle source changed.
         if(sourceChanged) {
            assemblyInfoHandler.changeSource(nassembly, event.getTable(), event.getSourceType());
         }

         BindingDropTarget dropTarget = (BindingDropTarget) event.getDropTarget();

         if(this.checkEmbeddedTableSource(rvs.getViewsheet(), event.getTable(),
                                          null, assembly, dispatcher))
         {
            return null;
         }

         final int offsetColIndex = getOffsetColIndex(nassembly, dropTarget.getTransferType(),
                                                      dropTarget.getDropIndex());
         tableHandler.addColumns(nassembly, event.getEntries(), offsetColIndex,
                                 dropTarget.getReplace());

         if(nassembly.getColumnSelection().getAttributeCount() > Util.getOrganizationMaxColumn()) {
            MessageCommand command = new MessageCommand();
            command.setMessage(Util.getColumnLimitMessage());
            command.setType(MessageCommand.Type.ERROR);
            dispatcher.sendCommand(command);
            return null;
         }

         applyAssemblyInfo(rvs, assembly, nassembly, dispatcher, event,
                           "/events/vstable/dnd/addColumns", linkUri);
      }
      finally {
         vbox.unlockRead();
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void dndTotree(@ClusterProxyKey String runtimeId, VSDndEvent event, Principal principal,
                         @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      if(rvs == null) {
         return null;
      }

      TableVSAssembly assembly = (TableVSAssembly) rvs.getViewsheet().getAssembly(event.name());
      TableVSAssembly clone = (TableVSAssembly) assembly.clone();
      final TableTransfer transfer = (TableTransfer) event.getTransfer();
      int dragIndex = transfer.getDragIndex();
      final int offsetColIndex = getOffsetColIndex(assembly, transfer.getTransferType(), dragIndex);
      tableHandler.removeColumns(clone, offsetColIndex);
      applyAssemblyInfo(rvs, assembly, (VSAssemblyInfo) clone.getInfo(),
                        dispatcher, event, linkUri, null);

      return null;
   }

   private int getOffsetColIndex(TableVSAssembly assembly, TransferType transferType, int index) {
      final int offsetColIndex;

      switch(transferType) {
      case TABLE:
         offsetColIndex = ComposerVSTableController.getOffsetColumnIndex(
            assembly.getColumnSelection(), index);
         break;
      case FIELD:
         offsetColIndex = index;
         break;
      default:
         throw new IllegalStateException("Unexpected value: " + transferType);
      }

      return offsetColIndex;
   }

   private boolean checkEmbeddedTableSource(Viewsheet vs, String tableName,
                                            VSAssembly drageAssembly,
                                            TableVSAssembly tableAssembly,
                                            CommandDispatcher dispatcher)
   {
      boolean invalid = false;

      if(drageAssembly != null && drageAssembly instanceof TableVSAssembly) {
         if(tableAssembly instanceof EmbeddedTableVSAssembly &&
            !(drageAssembly instanceof EmbeddedTableVSAssembly))
         {
            invalid = true;
         }
      }
      if(VSUtil.isVSAssemblyBinding(tableName)) {
         String vsAssembly = VSUtil.getVSAssemblyBinding(tableName);

         if(StringUtils.isEmpty(vsAssembly)) {
            return false;
         }

         Assembly assembly = vs.getAssembly(vsAssembly);

         if(!(assembly instanceof EmbeddedTableVSAssembly) &&
            tableAssembly instanceof EmbeddedTableVSAssembly)
         {
            invalid = true;
         }
         else {
            invalid = false;
         }
      }
      else {
         Worksheet ws = vs.getBaseWorksheet();
         Assembly assembly = ws.getAssembly(tableName);

         if(assembly == null || tableAssembly == null) {
            return false;
         }

         if(assembly instanceof TableAssembly) {
            TableAssembly tAssembly = assembly instanceof MirrorTableAssembly ?
               ((MirrorTableAssembly) assembly).getTableAssembly() : (TableAssembly) assembly;

            boolean isEmbedded = tAssembly instanceof EmbeddedTableAssembly &&
               (!(tAssembly instanceof SnapshotEmbeddedTableAssembly));

            if(tableAssembly instanceof EmbeddedTableVSAssembly && !isEmbedded) {
               invalid = true;
            }
         }
      }

      if(invalid) {
         MessageCommand command = new MessageCommand();
         command.setMessage(Catalog.getCatalog().getString("common.viewsheet.embeddedTable.binding"));
         command.setType(MessageCommand.Type.ERROR);
         dispatcher.sendCommand(command);
      }

      return invalid;
   }

   protected void applyAssemblyInfo(RuntimeViewsheet rvs, VSAssembly oassembly,
                                    VSAssembly nassembly, CommandDispatcher dispatcher,
                                    VSDndEvent event, String url, String linkUri)
      throws Exception
   {
      applyAssemblyInfo(rvs, oassembly, nassembly, dispatcher,
         event, url, linkUri, null, null);
   }

   protected void applyAssemblyInfo(RuntimeViewsheet rvs, VSAssembly oassembly,
                                    VSAssembly nassembly, CommandDispatcher dispatcher,
                                    VSDndEvent event, String url, String linkUri,
                                    Consumer<VSCrosstabInfo> updateCalculate,
                                    BiConsumer<Map<TableDataPath, VSCompositeFormat>, TableDataPath> clearAliasFormatProcessor)
      throws Exception
   {
      // validate current binding when source changed.
      if(oassembly instanceof DataVSAssembly) {
         SourceInfo osource = ((DataVSAssemblyInfo) oassembly.getInfo()).getSourceInfo();
         SourceInfo nsource = ((DataVSAssemblyInfo) nassembly.getInfo()).getSourceInfo();

         if(osource != null && osource.getSource() != null && nsource != null) {
            if(!osource.getSource().equals(nsource.getSource()) &&
               nsource.getType() == SourceInfo.VS_ASSEMBLY)
            {
               VSAQuery query = VSAQuery.createVSAQuery(rvs.getViewsheetSandbox(),
                                                        nassembly, DataMap.DETAIL);
               query.createAssemblyTable(nassembly.getTableName());
            }

            if(!osource.getSource().equals(nsource.getSource()) ||
               VSUtil.isVSAssemblyBinding(event.getTable()))
            {
               assemblyInfoHandler.validateBinding(nassembly);
            }
         }
      }

      applyAssemblyInfo(rvs, oassembly, (VSAssemblyInfo) nassembly.getInfo(), dispatcher,
         event, url, linkUri, updateCalculate, clearAliasFormatProcessor);
   }

   protected void applyAssemblyInfo(RuntimeViewsheet rvs, VSAssembly assembly,
                                    VSAssemblyInfo clone, CommandDispatcher dispatcher,
                                    VSDndEvent event, String linkUri,
                                    Consumer<VSCrosstabInfo> updateCalculate)
      throws Exception
   {
      applyAssemblyInfo(rvs, assembly, clone, dispatcher, event, null, linkUri, updateCalculate);
   }

   protected void applyAssemblyInfo(RuntimeViewsheet rvs, VSAssembly assembly,
                                    VSAssemblyInfo clone, CommandDispatcher dispatcher,
                                    VSDndEvent event, String url, String linkUri,
                                    Consumer<VSCrosstabInfo> updateCalculate)
      throws Exception
   {
      applyAssemblyInfo(rvs, assembly, clone, dispatcher, event, url,
         linkUri, updateCalculate, null);
   }

   protected void applyAssemblyInfo(RuntimeViewsheet rvs, VSAssembly assembly,
      VSAssemblyInfo clone, CommandDispatcher dispatcher, VSDndEvent event,
      String url, String linkUri, Consumer<VSCrosstabInfo> updateCalculate,
      BiConsumer<Map<TableDataPath, VSCompositeFormat>, TableDataPath> clearAliasFormatProcessor)
      throws Exception
   {
      assemblyInfoHandler.apply(rvs, clone, viewsheetService, event.confirmed(),
         event.checkTrap(), false, false, dispatcher, url,
         event, linkUri, updateCalculate, clearAliasFormatProcessor);

      try {
         final BindingModel binding = bfactory.createModel(assembly);
         final SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
         dispatcher.sendCommand(bcommand);
         coreLifecycleService.refreshVSAssembly(rvs, assembly, dispatcher);
      }
      catch(ConfirmException ex) {
         if(!coreLifecycleService.waitForMV(ex, rvs, dispatcher)) {
            throw ex;
         }
      }
   }

   private final ViewsheetService viewsheetService;
   private final VSTableBindingHandler tableHandler;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final VSBindingService bfactory;
   private final CoreLifecycleService coreLifecycleService;


}
