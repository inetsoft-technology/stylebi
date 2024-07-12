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
package inetsoft.web.composer.ws.joins;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.web.composer.ws.TableModeController;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSMergeAddJoinTableEvent;
import inetsoft.web.composer.ws.event.WSMergeJoinEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Controller that provides a REST endpoint for creating and fetching the assemblies of merge joins.
 */
@Controller
public class MergeJoinController extends WorksheetController {
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/dialog/inner-join-dialog/merge-join")
   public void doMergeJoin(
      @Payload WSMergeJoinEvent event,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      MergeJoinTableAssembly mergeTable = process(
         rws, event.getTableNames(), commandDispatcher, principal);

      if(mergeTable == null) {
         // should not happen
         MessageCommand command = new MessageCommand();
         command.setType(MessageCommand.Type.ERROR);
         command.setMessage("Could not merge join tables");
         commandDispatcher.sendCommand(command);
         return;
      }

      TableModeController.setDefaultTableMode(mergeTable, rws.getAssetQuerySandbox());
      WorksheetEventUtil.createAssembly(rws, mergeTable, commandDispatcher, principal);
      WorksheetEventUtil.focusAssembly(mergeTable.getName(), commandDispatcher);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/add-join-table")
   public void addJoinTable(
      @Payload WSMergeAddJoinTableEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      MergeJoinTableAssembly mergeTable =
         (MergeJoinTableAssembly) ws.getAssembly(event.mergeTableName());
      TableAssembly newTable =
         (TableAssembly) ws.getAssembly(event.newTableName());

      if(mergeTable != null && newTable != null) {
         insertJoinTable(rws, mergeTable, newTable, event.insertionIndex());
         rws.getWorksheet().checkDependencies();
         WorksheetEventUtil.refreshAssembly(
            rws, event.newTableName(), true, commandDispatcher, principal);
      }
   }

   private void insertJoinTable(
      RuntimeWorksheet rws, MergeJoinTableAssembly mergeTable, TableAssembly newTable,
      int index) throws Exception
   {
      if(mergeTable.getTableAssembly(newTable.getName()) != null) {
         return;
      }

      TableAssembly[] oldAssemblies = mergeTable.getTableAssemblies(true);
      TableAssembly[] newAssemblies = new TableAssembly[oldAssemblies.length + 1];

      System.arraycopy(oldAssemblies, 0, newAssemblies, 0, index);
      newAssemblies[index] = newTable;
      System.arraycopy(oldAssemblies, index,
                       newAssemblies, index + 1, oldAssemblies.length - index);


      TableAssemblyOperator operator = new TableAssemblyOperator();
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setOperation(TableAssemblyOperator.MERGE_JOIN);
      operator.addOperator(op);

      if(index - 1 >= 0) {
         mergeTable.setOperator(newAssemblies[index - 1].getName(), newTable.getName(),
                                (TableAssemblyOperator) operator.clone());
      }

      if(index + 1 < newAssemblies.length) {
         mergeTable.setOperator(newTable.getName(), newAssemblies[index + 1].getName(),
                                (TableAssemblyOperator) operator.clone());
      }

      if(index - 1 >= 0 && index + 1 < newAssemblies.length) {
         mergeTable.removeOperator(
            newAssemblies[index - 1].getName(),
            newAssemblies[index + 1].getName());
      }

      mergeTable.setTableAssemblies(newAssemblies);
      AssetEventUtil.initColumnSelection(rws, mergeTable);
      WorksheetEventUtil.loadTableData(rws, mergeTable.getName(),
                                       false, false);
   }

   /**
    * Process merge join table event.
    * From 12.2 MergejoinTableEvent.
    */
   public MergeJoinTableAssembly process(
      RuntimeWorksheet rws, String[] assemblies,
      CommandDispatcher commandDispatcher, Principal principal) throws Exception
   {
      MergeJoinTableAssembly mergeTable = null;

      for(int i = 1; i < assemblies.length; i++) {
         if(mergeTable == null) {
            mergeTable = concatenateTable(assemblies[i - 1], assemblies[i],
                                          rws);
         }
         else {
            mergeTable = concatenateTable(mergeTable.getName(), assemblies[i],
                                          rws);
         }
      }

      rws.getWorksheet().checkDependencies();
      WorksheetEventUtil.refreshAssembly(
         rws, mergeTable.getName(), true, commandDispatcher, principal);
      WorksheetEventUtil.loadTableData(rws, mergeTable.getName(),
                                       false, false);
      WorksheetEventUtil.layout(rws, commandDispatcher);

      return mergeTable;
   }

   /**
    * Concatenate tables.
    *
    * @param src  src table name.
    * @param dest dest table name.
    * @param rws  running worksheet.
    *
    * @return concatenated assembly.
    */
   private MergeJoinTableAssembly concatenateTable(
      String src, String dest,
      RuntimeWorksheet rws) throws Exception
   {
      Worksheet ws = rws.getWorksheet();
      TableAssembly stable = (TableAssembly) ws.getAssembly(src);
      TableAssembly dtable = (TableAssembly) ws.getAssembly(dest);

      final String nname = AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET);
      TableAssemblyOperator operator = new TableAssemblyOperator();
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setOperation(TableAssemblyOperator.MERGE_JOIN);
      operator.addOperator(op);
      MergeJoinTableAssembly mergeTable;
      boolean newtbl = false;

      if(stable instanceof MergeJoinTableAssembly) {
         mergeTable = (MergeJoinTableAssembly) stable;
         checkContainsSubtable(rws, mergeTable, dtable);
      }
      else if(dtable instanceof MergeJoinTableAssembly) {
         mergeTable = (MergeJoinTableAssembly) dtable;
         checkContainsSubtable(rws, mergeTable, stable);
      }
      else {
         newtbl = true;
         mergeTable = new MergeJoinTableAssembly(ws, nname,
                                        new TableAssembly[]{ stable, dtable },
                                        new TableAssemblyOperator[]{ operator });

         ws.addAssembly(mergeTable);
         AssetEventUtil.initColumnSelection(rws, mergeTable);
//         WorksheetEventUtil.createAssembly(rws, jTable);
         AssetEventUtil.layoutResultantTable(stable, dtable, mergeTable);
      }

      if(!newtbl) {
         TableAssembly[] arr = mergeTable.getTableAssemblies(true);
         TableAssembly[] narr = new TableAssembly[arr.length + 1];

         if(mergeTable == stable) {
            System.arraycopy(arr, 0, narr, 0, arr.length);
            narr[arr.length] = (TableAssembly) dtable;
            mergeTable.setOperator(narr[narr.length - 2].getName(),
                                   narr[narr.length - 1].getName(), operator);
         }
         else {
            System.arraycopy(arr, 0, narr, 1, arr.length);
            narr[0] = (TableAssembly) stable;
            mergeTable.setOperator(narr[0].getName(), narr[1].getName(),
                                   operator);
         }

         mergeTable.setTableAssemblies(narr);
         ws.removeAssembly(mergeTable);
         ws.addAssembly(mergeTable);
         AssetEventUtil.initColumnSelection(rws, mergeTable);
      }

      return mergeTable;
   }

   private void checkContainsSubtable(
      RuntimeWorksheet rws, MergeJoinTableAssembly mergeTable, TableAssembly subtable)
   {
      if(JoinUtil.tableContainsSubtable(mergeTable, subtable.getAbsoluteName())) {
         rws.rollback();
         throw new MessageException(catalog.getString(
            "common.table.recursiveJoin"));
      }
   }

   private static final Catalog catalog = Catalog.getCatalog();
}
