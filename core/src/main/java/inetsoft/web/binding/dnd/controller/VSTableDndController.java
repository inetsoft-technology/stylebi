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
package inetsoft.web.binding.dnd.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Util;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.web.binding.dnd.*;
import inetsoft.web.binding.event.VSDndEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSTableBindingHandler;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.objects.controller.ComposerVSTableController;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * This class handles get vsobjectmodel from the server.
 */
@Controller
public class VSTableDndController extends VSAssemblyDndController {
   /**
    * Creates a new instance of <tt>VSViewController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public VSTableDndController(RuntimeViewsheetRef runtimeViewsheetRef,
                               VSBindingService bfactory,
                               VSAssemblyInfoHandler assemblyInfoHandler,
                               VSTableBindingHandler tableHandler,
                               VSObjectModelFactoryService objectModelService,
                               ViewsheetService viewsheetService,
                               PlaceholderService placeholderService)
   {
      super(runtimeViewsheetRef, bfactory, assemblyInfoHandler, objectModelService,
            viewsheetService, placeholderService);
      this.tableHandler = tableHandler;
   }

   /**
    * Add a column to a table or change the binding of a column in the table via drag and drop
    * from another assembly onto the target table assembly
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value="/vstable/dnd/addRemoveColumns")
   public void dnd(@Payload VSDndEvent event, Principal principal,
      @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = getRuntimeVS(principal);

      if(rvs == null || event.getTransfer() == null) {
         return;
      }

      // The data for what's being dropped onto the table, including the assembly that initiated
      // the drag event
      TableTransfer tableData = (TableTransfer) event.getTransfer();
      VSAssembly dragAssembly = getVSAssembly(rvs, tableData.getAssembly());

      // The data for the table being dropped onto, in order to add or change the binding of a
      // column, including the table assembly that accepted the drop event
      BindingDropTarget dropTarget = (BindingDropTarget) event.getDropTarget();

      TableVSAssembly tableAssembly = (TableVSAssembly) getVSAssembly(rvs, dropTarget.getAssembly());
      TableVSAssembly newTableAssembly = (TableVSAssembly) tableAssembly.clone();

      if(this.checkEmbeddedTableSource(rvs.getViewsheet(), dragAssembly.getTableName(),
         dragAssembly, tableAssembly, dispatcher))
      {
         return;
      }

      // Handle source changed.
      if(sourceChanged(tableAssembly, dragAssembly.getTableName())) {
         changeSource(newTableAssembly, dragAssembly.getTableName(), event.getSourceType());
      }

      tableHandler.addRemoveColumns(newTableAssembly, dragAssembly, tableData.getDragIndex(),
                                    dropTarget.getDropIndex(), dropTarget.getReplace(),
                                    tableData.getDragType(), tableData.getTransferType());
      applyAssemblyInfo(rvs, tableAssembly, newTableAssembly, dispatcher,
         event, null, linkUri);
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

   /**
    * This method is to get vsobjectmodel for edit binding pane.
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value="/vstable/dnd/addColumns")
   public void dndFromTree(@Payload VSDndEvent event, Principal principal,
                           @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = getRuntimeVS(principal);

      if(rvs == null) {
         return;
      }

      ViewsheetSandbox vbox = rvs.getViewsheetSandbox();
      vbox.lockRead();

      try {
         TableVSAssembly assembly = (TableVSAssembly) getVSAssembly(rvs, event.name());
         TableVSAssembly nassembly = (TableVSAssembly) assembly.clone();

         // Handle source changed.
         if(sourceChanged(assembly, event.getTable())) {
            changeSource(nassembly, event.getTable(), event.getSourceType());
         }

         BindingDropTarget dropTarget = (BindingDropTarget) event.getDropTarget();

         if(this.checkEmbeddedTableSource(rvs.getViewsheet(), event.getTable(),
            null, assembly, dispatcher))
         {
            return;
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
            return;
         }

         applyAssemblyInfo(rvs, assembly, nassembly, dispatcher, event,
                           "/events/vstable/dnd/addColumns", linkUri);
      }
      finally {
         vbox.unlockRead();
      }
   }

   /**
    * This method is to get vsobjectmodel for edit binding pane.
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value="/vstable/dnd/removeColumns")
   public void dndTotree(@Payload VSDndEvent event, Principal principal,
                         @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = getRuntimeVS(principal);

      if(rvs == null) {
         return;
      }

      TableVSAssembly assembly = (TableVSAssembly) getVSAssembly(rvs, event.name());
      TableVSAssembly clone = (TableVSAssembly) assembly.clone();
      final TableTransfer transfer = (TableTransfer) event.getTransfer();
      int dragIndex = transfer.getDragIndex();
      final int offsetColIndex = getOffsetColIndex(assembly, transfer.getTransferType(), dragIndex);
      tableHandler.removeColumns(clone, offsetColIndex);
      applyAssemblyInfo(rvs, assembly, (VSAssemblyInfo) clone.getInfo(),
                        dispatcher, event, linkUri, null);
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

   private VSTableBindingHandler tableHandler;
}
