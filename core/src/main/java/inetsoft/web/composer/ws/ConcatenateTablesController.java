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
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.command.ConcatCompatibilityCommand;
import inetsoft.web.composer.ws.event.*;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;

@Controller
public class ConcatenateTablesController extends WorksheetController {
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/concatenate-tables")
   public void concatenateTables(
      @Payload WSConcatenateEvent event,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      rws.cloneWS();
      process(rws, event, commandDispatcher, principal);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/concatenate/add-table")
   public void addSubTable(
      @Payload WSConcatAddSubTableEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      ConcatenatedTableAssembly assembly = concatenateTable(
         event.concatTableName(), event.newTableName(), rws, event.operator(),
         event.index(), event.concatenateWithLeftTable(), commandDispatcher, principal);

      if(assembly != null) {
         WorksheetEventUtil.loadTableData(rws, assembly.getName(), false, true);
         WorksheetEventUtil.refreshAssembly(
            rws, assembly.getName(), true, commandDispatcher, principal);
      }
   }

   @RequestMapping(
      value = "api/composer/worksheet/concat/compatible-insertion-tables/{runtimeid}",
      method = RequestMethod.GET)
   @ResponseBody
   public ArrayList<String> getCompatibleInsertionTables(
      @PathVariable("runtimeid") String runtimeId,
      @RequestParam("concatTable") String concatTable,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      Worksheet ws = getWorksheetEngine()
         .getWorksheet(runtimeId, principal).getWorksheet();
      TableAssembly sourceTable = (TableAssembly) ws.getAssembly(concatTable);
      ArrayList<String> validTables = new ArrayList<>();
      Assembly[] assemblies = ws.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(assembly instanceof TableAssembly && sourceTable != assembly &&
            getInnerTablesCompatibility(ws, sourceTable, (TableAssembly) assembly).isCompatible())
         {
            validTables.add(assembly.getAbsoluteName());
         }
      }

      return validTables;
   }

   /**
    * Checks the compatibility of the source table and the rest of the tables
    * in the worksheet.
    */
   @MessageMapping("/composer/worksheet/concat/compatibility")
   public void checkCompatibility(
      @Payload ConcatCompatibilityEvent event,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      if(event.getOtherTables().length == 0) {
         MessageCommand command = new MessageCommand();
         command.setType(MessageCommand.Type.WARNING);
         command.setMessage("There are no other tables to concatenate with.");
         command.setAssemblyName(event.getSourceTable());
         commandDispatcher.sendCommand(command);
         return;
      }

      Worksheet ws = getRuntimeWorksheet(principal).getWorksheet();
      TableAssembly sourceTable = (TableAssembly) ws.getAssembly(event.getSourceTable());
      ConcatCompatibilityCommand compatibilityCommand = new ConcatCompatibilityCommand();

      if(sourceTable == null) {
         compatibilityCommand.getInvalidTables().add(event.getSourceTable());
      }
      // Populate command with invalid tables
      else {
         for(int i = 0; i < event.getOtherTables().length; i++) {
            TableAssembly otherTable = (TableAssembly) ws
               .getAssembly(event.getOtherTables()[i]);

            if(otherTable == null) {
               compatibilityCommand.getInvalidTables().add(event.getOtherTables()[i]);
            }
            else {
               ConcatenationCompatibility concatenationCompatibility =
                  getTableCompatibility(ws, otherTable, sourceTable);

               if(!concatenationCompatibility.isCompatible()) {
                  compatibilityCommand.getInvalidTables().add(otherTable.getAbsoluteName());
               }
            }
         }
      }

      commandDispatcher.sendCommand(compatibilityCommand);
   }

   /**
    * Process concatenate table event.
    */
   private ConcatenatedTableAssembly process(
      RuntimeWorksheet rws, WSConcatenateEvent event, CommandDispatcher commandDispatcher,
      Principal principal) throws Exception
   {
      ConcatenatedTableAssembly ctbl = null;

      String[] names = event.getTables();
      String[] assemblies = new String[names.length];
      System.arraycopy(names, 0, assemblies, 0, names.length);

      if(assemblies.length == 0) {
         throw new MessageException("No table assemblies found.", LogLevel.WARN, false);
      }

      for(int i = 1; i < assemblies.length; i++) {
         if(i == 1) {
            ctbl = concatenateTable(assemblies[i - 1], assemblies[i],
                                    rws, event.getOperator(), commandDispatcher, principal);
         }
         else {
            ctbl = concatenateTable(
               ctbl.getName(), assemblies[i], rws, event.getOperator(),
               commandDispatcher, principal);
         }

         if(ctbl == null) {
            String msg = Catalog.getCatalog()
               .getString("common.concatenateIncompatible");
            throw new MessageException(msg, LogLevel.WARN, false);
         }
      }

      if(ctbl != null) {
         WorksheetEventUtil.loadTableData(rws, ctbl.getName(), false, true);
         WorksheetEventUtil.refreshAssembly(rws, ctbl.getName(), true, commandDispatcher, principal);
      }

      WorksheetEventUtil.layout(rws, commandDispatcher);
      return ctbl;
   }

   /**
    * Concatenate tables.
    *
    * @param left  left table name.
    * @param right right table name.
    * @param rws   running worksheet.
    * @param op    the concatenation operation.
    *
    * @param principal
    * @return concatenated assembly.
    */
   private ConcatenatedTableAssembly concatenateTable(
      String left, String right,
      RuntimeWorksheet rws, int op, CommandDispatcher commandDispatcher, Principal principal) throws Exception
   {
      return concatenateTable(left, right, rws, op, -1, false, commandDispatcher, principal);
   }

   /**
    * Concatenate tables.
    *
    * @param left                     left table name.
    * @param right                    right table name.
    * @param rws                      running worksheet.
    * @param op                       the concatenation operation.
    * @param index                    the index to add the table to if a concat table
    *                                 already exists
    * @param concatenateWithLeftTable if true, concat with left table, otherwise right
    *
    * @param principal
    * @return concatenated assembly.
    */
   private ConcatenatedTableAssembly concatenateTable(
      String left, String right,
      RuntimeWorksheet rws, int op, int index,
      boolean concatenateWithLeftTable,
      CommandDispatcher commandDispatcher, Principal principal) throws Exception
   {
      Worksheet ws = rws.getWorksheet();
      TableAssembly leftTable = (TableAssembly) ws.getAssembly(left);
      TableAssembly rightTable = (TableAssembly) ws.getAssembly(right);

      if(leftTable == null || rightTable == null) {
         return null;
      }

      ConcatenationCompatibility concatenationCompatibility =
         getTableCompatibility(ws, leftTable, rightTable);

      if(!concatenationCompatibility.isCompatible()) {
         rws.rollback();

         if(concatenationCompatibility.getCompatibilityFailureMessage() != null) {
            throw new MessageException(
               concatenationCompatibility.getCompatibilityFailureMessage());
         }
         else {
            return null;
         }
      }

      String name = AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET);
      TableAssemblyOperator operator = new TableAssemblyOperator();
      TableAssemblyOperator.Operator ope = new TableAssemblyOperator.Operator();
      ope.setOperation(op);
      operator.addOperator(ope);
      ConcatenatedTableAssembly ctbl = null;
      boolean newtbl;

      if(leftTable instanceof ConcatenatedTableAssembly) {
         ctbl = (ConcatenatedTableAssembly) leftTable;

         // @by stephenwebster, if the table is not mergeable and the
         // concatenated tables are, create a new concatenated table
         newtbl = !getTableColumnCompatibility(
            ctbl.getTableAssemblies(false)[0], rightTable).isCompatible();
      }
      else {
         newtbl = true;
      }

      if(newtbl) {
         ctbl = new ConcatenatedTableAssembly(
            ws, name, new TableAssembly[]{ leftTable, rightTable },
            new TableAssemblyOperator[]{ operator });
         ws.addAssembly(ctbl);
         TableModeController.setDefaultTableMode(ctbl, rws.getAssetQuerySandbox());
         AssetEventUtil.initColumnSelection(rws, ctbl);
         WorksheetEventUtil.createAssembly(rws, ctbl, commandDispatcher, principal);
         WorksheetEventUtil.focusAssembly(ctbl.getName(), commandDispatcher);
         AssetEventUtil.layoutResultantTable(leftTable, rightTable, ctbl);
      }

      if(!newtbl) {
         if(index >= 0) {
            addTableToExistingConcatenation(ctbl, rightTable, index,
                                                   concatenateWithLeftTable, operator);
         }
         else {
            TableAssembly[] arr = ctbl.getTableAssemblies(true);
            TableAssembly[] narr = new TableAssembly[arr.length + 1];

            if(ctbl == leftTable) {
               System.arraycopy(arr, 0, narr, 0, arr.length);
               narr[arr.length] = rightTable;
               ctbl.setOperator(narr[narr.length - 2].getName(),
                                narr[narr.length - 1].getName(), operator);
            }
            else { // ctbl == rightTable
               System.arraycopy(arr, 0, narr, 1, arr.length);
               narr[0] = leftTable;
               ctbl.setOperator(narr[0].getName(), narr[1].getName(),
                                operator);
            }

            ctbl.setTableAssemblies(narr);
         }

         ws.removeAssembly(ctbl);
         ws.addAssembly(ctbl);
      }

      return ctbl;
   }

   /**
    * Adds new table to concatenation and updates operators around it.
    *
    * @param ctbl                     the concat table to add the table to
    * @param addedTable               the table to add
    * @param index                    the index at which the table is to be added
    * @param concatenateWithLeftTable if true, concatenate with left table, else right table
    * @param operator                 the operator the concatenation should have
    */
   private void addTableToExistingConcatenation(
      ConcatenatedTableAssembly ctbl, TableAssembly addedTable, int index,
      boolean concatenateWithLeftTable, TableAssemblyOperator operator)
   {
      TableAssembly[] arr = ctbl.getTableAssemblies(true);
      TableAssembly[] narr = new TableAssembly[arr.length + 1];
      System.arraycopy(arr, 0, narr, 0, index);
      narr[index] = addedTable;
      System.arraycopy(arr, index, narr, index + 1, arr.length - index);
      ctbl.setTableAssemblies(narr);

      if(concatenateWithLeftTable && index >= 1) {
         String ltable = narr[index - 1].getName();
         ctbl.setOperator(ltable, addedTable.getName(), operator);

         if(index + 1 < narr.length) {
            String rtable = narr[index + 1].getName();
            TableAssemblyOperator oldOperator = ctbl.getOperator(ltable, rtable);

            if(oldOperator != null) {
               Arrays.stream(oldOperator.getOperators())
                  .forEach((o) -> {
                     if(o.getLeftTable() != null) {
                        o.setLeftTable(addedTable.getName());
                     }
                  });
               ctbl.setOperator(addedTable.getName(), rtable, oldOperator);
            }

            ctbl.removeOperator(ltable, rtable);
         }
      }
      else if(!concatenateWithLeftTable && index + 1 < narr.length) {
         String rtable = narr[index + 1].getName();
         ctbl.setOperator(addedTable.getName(), rtable, operator);

         if(index - 1 >= 0) {
            String ltable = narr[index - 1].getName();
            TableAssemblyOperator oldOperator = ctbl.getOperator(ltable, rtable);

            if(oldOperator != null) {
               Arrays.stream(oldOperator.getOperators())
                  .forEach((o) -> o.setRightTable(addedTable.getName()));
               ctbl.setOperator(ltable, addedTable.getName(), oldOperator);
            }

            ctbl.removeOperator(ltable, rtable);
         }
      }
      else {
         throw new ArrayIndexOutOfBoundsException(index);
      }
   }

   /**
    * Check if two tables can be concatenated.
    */
   static ConcatenationCompatibility getTableColumnCompatibility(
      TableAssembly leftTable, TableAssembly rightTable)
   {
      ColumnSelection leftColumns = leftTable.getColumnSelection(true);
      ColumnSelection rightColumns = rightTable.getColumnSelection(true);

      if(leftColumns.getAttributeCount() != rightColumns.getAttributeCount()) {
         String msg = Catalog.getCatalog().getString(
            "common.table.unionMismatch");
         return new ConcatenationCompatibility(false, msg);
      }

      return new ConcatenationCompatibility(true);
   }

   /**
    * Check whether inner tables are compatible.
    *
    * @param leftTable  the left table
    * @param rightTable the right table
    *
    * @return true if tables are compatible, false otherwise
    */
   private ConcatenationCompatibility getInnerTablesCompatibility(
      Worksheet ws, WSAssembly leftTable, WSAssembly rightTable)
   {
      if(!(leftTable instanceof TableAssembly) ||
         !(rightTable instanceof TableAssembly))
      {
         return new ConcatenationCompatibility(false);
      }

      if(leftTable instanceof ConcatenatedTableAssembly) {
         ConcatenatedTableAssembly ctable =
            (ConcatenatedTableAssembly) leftTable;

         ConcatenationCompatibility compatibility =
            checkValidity(ws, ctable, (TableAssembly) rightTable);

         if(compatibility != null) {
            return compatibility;
         }
      }
      else if(rightTable instanceof ConcatenatedTableAssembly) {
         ConcatenatedTableAssembly ctable =
            (ConcatenatedTableAssembly) rightTable;

         ConcatenationCompatibility compatibility =
            checkValidity(ws, ctable, (TableAssembly) leftTable);

         if(compatibility != null) {
            return compatibility;
         }
      }

      return new ConcatenationCompatibility(false);
   }

   private ConcatenationCompatibility checkValidity(
      Worksheet ws, ConcatenatedTableAssembly ctable, TableAssembly otherTable)
   {
      if(getTableColumnCompatibility(
         ctable.getTableAssemblies(false)[0], otherTable).isCompatible())
      {
         for(String name : ctable.getTableNames()) {
            if(name.equals(otherTable.getName())) {
               String msg = Catalog.getCatalog().getString(
                  "common.table.unionDuplicate");
               return new ConcatenationCompatibility(false, msg);
            }
         }

         if(WorksheetEventUtil.checkCyclicalDependency(ws, ctable, otherTable)) {
            String msg = Catalog.getCatalog().getString(
               "common.dependencyCycle");
            return new ConcatenationCompatibility(false, msg);
         }

         return new ConcatenationCompatibility(true);
      }

      return null;
   }

   /**
    * Check if two tables can be concatenated.
    *
    * @param leftTable  the left table
    * @param rightTable the right table
    *
    * @return true if the tables can be concatenated, false otherwise
    */
   private ConcatenationCompatibility getTableCompatibility(
      Worksheet ws, WSAssembly leftTable, WSAssembly rightTable)
   {
      ConcatenationCompatibility innerTableCompatibility =
         getInnerTablesCompatibility(ws, leftTable, rightTable);

      if(innerTableCompatibility.isCompatible()) {
         return innerTableCompatibility;
      }

      boolean bothTablesConcat = leftTable instanceof ConcatenatedTableAssembly &&
         rightTable instanceof ConcatenatedTableAssembly;
      boolean neitherTableConcat = !(leftTable instanceof ConcatenatedTableAssembly ||
         rightTable instanceof ConcatenatedTableAssembly);

      if(bothTablesConcat) {
         if(WorksheetEventUtil.checkCyclicalDependency(ws, leftTable, rightTable)) {
            String msg = Catalog.getCatalog().getString(
               "common.dependencyCycle");
            return new ConcatenationCompatibility(false, msg);
         }
      }

      if(bothTablesConcat || neitherTableConcat) {
         ConcatenationCompatibility outerTablesCompatibility = getTableColumnCompatibility(
            (TableAssembly) leftTable, (TableAssembly) rightTable);

         if(outerTablesCompatibility.isCompatible()) {
            return outerTablesCompatibility;
         }
      }

      return innerTableCompatibility;
   }

   /**
    * Information class for whether or not tables are compatible for concatenation.
    * An optional failure message can contain the reason for the failure.
    */
   static class ConcatenationCompatibility {
      private final boolean compatible;
      private final String compatibilityFailureMessage;

      public ConcatenationCompatibility(boolean compatible) {
         this.compatible = compatible;
         this.compatibilityFailureMessage = null;
      }

      public ConcatenationCompatibility(
         boolean compatible, String compatibilityFailureMessage)
      {
         this.compatible = compatible;
         this.compatibilityFailureMessage = compatibilityFailureMessage;
      }

      public boolean isCompatible() {
         return compatible;
      }

      public String getCompatibilityFailureMessage() {
         return compatibilityFailureMessage;
      }
   }
}
