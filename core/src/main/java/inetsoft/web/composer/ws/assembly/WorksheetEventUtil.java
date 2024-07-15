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
package inetsoft.web.composer.ws.assembly;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.*;
import inetsoft.report.lens.CrossJoinCellCountBeyondLimitException;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.*;
import inetsoft.web.composer.model.ws.TableAssemblyOperatorModel;
import inetsoft.web.composer.model.ws.WorksheetModel;
import inetsoft.web.composer.ws.TableModeController;
import inetsoft.web.composer.ws.command.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Point2D;
import java.security.Principal;
import java.util.List;
import java.util.*;

public class WorksheetEventUtil {
   /**
    * Reset variables.
    *
    * @param rws  the specified runtime worksheet.
    * @param name the specified assembly name, <tt>null</tt> means to reset
    *             the variable table.
    *
    * @return <tt>true</tt> if contains variables, <tt>false</tt> otherwise.
    */
   public static boolean refreshVariables(
      RuntimeWorksheet rws, WorksheetService worksheetService, String name,
      CommandDispatcher commandDispatcher)
   {
      return refreshVariables(rws, worksheetService, name, commandDispatcher, false);
   }

   /**
    * Reset variables.
    *
    * @param rws  the specified runtime worksheet.
    * @param name the specified assembly name, <tt>null</tt> means to reset
    *             the variable table.
    *
    * @return <tt>true</tt> if contains variables, <tt>false</tt> otherwise.
    */
   public static boolean refreshVariables(
      RuntimeWorksheet rws, WorksheetService worksheetService, String name,
      CommandDispatcher commandDispatcher,  boolean refreshColumn)
   {
      return refreshVariables(rws, worksheetService, commandDispatcher, name == null,
         refreshColumn);
   }

   /**
    * Reset variables.
    *
    * @param rws            the specified runtime worksheet.
    * @return <tt>true</tt> if contains variables, <tt>false</tt> otherwise.
    */
   public static boolean refreshVariables(
      RuntimeWorksheet rws, WorksheetService worksheetService,
      CommandDispatcher commandDispatcher, boolean reset)
   {
      return refreshVariables(rws, worksheetService, commandDispatcher, reset, false);
   }

   /**
    * Reset variables.
    *
    * @param rws            the specified runtime worksheet.
    * @return <tt>true</tt> if contains variables, <tt>false</tt> otherwise.
    */
   public static boolean refreshVariables(
      RuntimeWorksheet rws, WorksheetService worksheetService,
      CommandDispatcher commandDispatcher, boolean reset, boolean refreshColumn)
   {
      WSCollectVariablesCommand command = refreshVariables(rws, worksheetService, reset,
         refreshColumn);

      if(command != null) {
         commandDispatcher.sendCommand(command);
         return true;
      }
      else {
         return false;
      }
   }

   // Ripped from AssetEventUtil.refreshVariables()
   public static WSCollectVariablesCommand refreshVariables(
      RuntimeWorksheet rws, WorksheetService worksheetService, boolean reset)
   {
      return refreshVariables(rws, worksheetService, reset, false);
   }

   // Ripped from AssetEventUtil.refreshVariables()
   public static WSCollectVariablesCommand refreshVariables(
      RuntimeWorksheet rws, WorksheetService worksheetService, boolean reset, boolean refreshColumn)
   {
      Worksheet worksheet = rws.getWorksheet();
      WorksheetInfo wsInfo = worksheet.getWorksheetInfo();

      if(wsInfo.isSingleQueryMode()) {
         return null;
      }

      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      VariableTable vart = box.getVariableTable();

      if(reset) {
         box.resetVariableTable();
      }

      if(vart != null) {
         vart = box.getVariableTable();
      }

      UserVariable[] vars = AssetEventUtil.executeVariables(worksheetService, box, vart, null, null,
                                                            rws.getEntry().getSheetName(), null);

      if(vars.length != 0) {
         ArrayList<VariableAssemblyModelInfo> infos = new ArrayList<>();

         for(UserVariable var : vars) {
            infos.add(new VariableAssemblyModelInfo(var));
         }

         return WSCollectVariablesCommand.builder()
            .varInfos(infos)
            .refreshColumns(refreshColumn)
            .build();
      }

      return null;
   }

   /**
    * Load table data.
    *
    * @param rws       runtime worksheet.
    * @param tname     table name.
    * @param recursive touch dependency or not.
    * @param reset     <tt>true</tt> to reset data, <tt>false</tt> otherwise.
    */
   public static void loadTableData(
      RuntimeWorksheet rws, String tname,
      boolean recursive, boolean reset)
   {
      try {
         Worksheet ws = rws.getWorksheet();
         WSAssembly table = (WSAssembly) ws.getAssembly(tname);
         boolean tempSort = false;

         if(table instanceof TableAssembly) {
            tempSort = ((TableAssembly) table).getSortInfo().isTempSort();
            ((TableAssembly) table).getSortInfo().setTempSort(false);
         }

         if(table == null) {
            return;
         }

         AssetQuerySandbox box = rws.getAssetQuerySandbox();
         table.checkValidity();

         if(table instanceof TableAssembly) {
            if(reset || tempSort) {
               box.reset(table.getAssemblyEntry(), false);
            }

            if(!table.isVisible()) {
               return;
            }
         }

         if(recursive) {
            ws.checkDependencies();

            AssemblyRef[] arr = ws.getDependings(table.getAssemblyEntry());
            WSAssembly[] assemblies = new WSAssembly[arr.length];

            for(int i = 0; i < arr.length; i++) {
               AssemblyEntry entry = arr[i].getEntry();
               assemblies[i] = (WSAssembly) ws.getAssembly(entry.getName());
            }

            // sort assemblies according to dependencies
            Arrays.sort(assemblies, new DependencyComparator(ws, true));

            for(WSAssembly sub : assemblies) {
               sub.update();
               loadTableData(rws, sub.getName(), true, reset);
            }
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to load data for table assembly: " + tname, ex);
      }
   }

   /**
    * Refresh assembly.
    *  @param rws       runtime worksheet.
    * @param name      the specified assembly name.
    * @param recursive touch dependency or not.
    * @param principal
    */
   public static void refreshAssembly(
      RuntimeWorksheet rws, String name,
      boolean recursive, CommandDispatcher commandDispatcher, Principal principal)
      throws Exception
   {
      refreshAssembly(rws, name, null, recursive, commandDispatcher, principal);
   }

   /**
    * Refresh assembly.
    *  @param rws               runtime worksheet.
    * @param newName           specified new assembly name.
    * @param oldName           specified old assembly name.
    * @param recursive         touch dependency or not.
    * @param commandDispatcher command dispatcher.
    * @param principal
    */
   public static void refreshAssembly(
      RuntimeWorksheet rws, String newName, String oldName, boolean recursive,
      CommandDispatcher commandDispatcher, Principal principal) throws Exception
   {
      refreshAssembly(rws, newName, oldName, recursive, null, commandDispatcher, principal);
   }

   /**
    * Refresh assembly.
    *  @param rws               runtime worksheet.
    * @param newName           specified new assembly name.
    * @param oldName           specified old assembly name.
    * @param recursive         touch dependency or not.
    * @param assemblyDeps      the assembly-dependings map, or null if this is the root assembly
    * @param commandDispatcher command dispatcher.
    * @param principal
    */
   private static void refreshAssembly(
      RuntimeWorksheet rws, String newName, String oldName,
      boolean recursive, Map<String, Set<String>> assemblyDeps,
      CommandDispatcher commandDispatcher, Principal principal) throws Exception
   {
      refreshAssembly(rws, newName, oldName, recursive, assemblyDeps,
         commandDispatcher, principal, true);
   }

   /**
    * Refresh assembly.
    *  @param rws               runtime worksheet.
    * @param newName           specified new assembly name.
    * @param oldName           specified old assembly name.
    * @param recursive         touch dependency or not.
    * @param assemblyDeps      the assembly-dependings map, or null if this is the root assembly
    * @param commandDispatcher command dispatcher.
    * @param principal
    */
   private static void refreshAssembly(
      RuntimeWorksheet rws, String newName, String oldName,
      boolean recursive, Map<String, Set<String>> assemblyDeps,
      CommandDispatcher commandDispatcher, Principal principal, boolean refreshOperators)
      throws Exception
   {
      Worksheet ws = rws.getWorksheet();

      if(oldName != null && !oldName.equals(newName)) {
         renameAssembly(rws, newName, oldName, commandDispatcher, principal);
      }

      WSAssembly assembly = (WSAssembly) ws.getAssembly(newName);

      if(assembly == null || !assembly.isVisible()) {
         return;
      }

      try {
         assembly.checkValidity();
      }
      catch(MessageException | ConfirmException e) {
         if(refreshOperators && e.getCause() instanceof CrossJoinException) {
            dealCrossJoinException((CrossJoinException) e.getCause(), rws, commandDispatcher);
         }

         throw e;
      }
      catch(Exception ex) {
         LOG.warn("Failed to check the worksheet assembly validity: " + newName, ex);
      }

      fixAssemblyInfo(rws, assembly);
      WSRefreshAssemblyCommand command = new WSRefreshAssemblyCommand();

      if(oldName == null || oldName.equals(newName)) {
         command.setOldName(newName);
      }
      else {
         command.setOldName(oldName);
      }

      try {
         command.setAssembly(WSAssemblyModelFactory.createModelFrom(assembly, rws, principal));
      }
      catch(CrossJoinCellCountBeyondLimitException ex) {
         WSAssembly errorAssembly = assembly;

         if(!Tool.isEmptyString(ex.getTableName())) {
            errorAssembly = (WSAssembly) ws.getAssembly(ex.getTableName());
         }

         if(errorAssembly instanceof RelationalJoinTableAssembly) {
            String[] restTable =
               ((RelationalJoinTableAssembly) errorAssembly).removeCrossJoinOperator();

            if(restTable == null || restTable.length == 0) {
               removeAssembly(rws, errorAssembly, commandDispatcher);
            }
            else {
               command.setAssembly(WSAssemblyModelFactory.createModelFrom(errorAssembly, rws,
                  principal));
            }

            String message = Catalog.getCatalog().getString("composer.ws.crossJoin.limitedMessage");
            UserMessage userMessage = new UserMessage(message, ConfirmException.WARNING);
            Tool.addUserMessage(userMessage);

            if(commandDispatcher != null) {
               refreshAssembly(rws, errorAssembly.getName() ,true, commandDispatcher, principal);
               return;
            }
         }
      }

      commandDispatcher.sendCommand(command);

      if(recursive) {
         ws.checkDependencies();

         AssemblyRef[] arr = ws.getDependings(assembly.getAssemblyEntry());
         WSAssembly[] assemblies = new WSAssembly[arr.length];

         for(int i = 0; i < arr.length; i++) {
            AssemblyEntry entry = arr[i].getEntry();
            assemblies[i] = (WSAssembly) ws.getAssembly(entry.getName());
         }

         // sort assemblies according to dependencies
         Arrays.sort(assemblies, new DependencyComparator(ws, true));

         if(assemblyDeps == null) {
            assemblyDeps = new HashMap<>();
            populateBlockingAssemblyDependencies(ws, assembly, assemblyDeps);
         }

         for(WSAssembly sub : assemblies) {
            Set<String> dependencies = assemblyDeps.get(sub.getAbsoluteName());
            dependencies.remove(assembly.getAbsoluteName());

            // If there are still blocking dependencies, do not refresh.
            if(dependencies.size() > 0) {
               continue;
            }

            sub.update();

            // TODO extraneous? May be because a different refresh isn't happening
            if(sub instanceof TableAssembly && canResetTableLens((TableAssembly) sub)) {
               refreshColumnSelection(rws, sub.getName(), false);
               loadTableData(rws, sub.getName(), true, true);
            }

            refreshAssembly(rws, sub.getName(), null, true, assemblyDeps,
                            commandDispatcher, principal);
         }
      }
   }

   private static void dealCrossJoinException(CrossJoinException e, RuntimeWorksheet rws,
                                              CommandDispatcher commandDispatcher)
      throws Exception
   {
      if(e == null || rws == null || commandDispatcher == null) {
         return;
      }

      if(e.getJoinTable() != null) {
         Assembly crossJoinTable = rws.getWorksheet().getAssembly(e.getJoinTable());

         if(crossJoinTable instanceof RelationalJoinTableAssembly) {
            String[] restTable = ((RelationalJoinTableAssembly) crossJoinTable)
               .removeOperator(e.getLeftTable(), e.getRightTable(), e.getOperator());

            if(restTable == null || restTable.length == 0) {
               removeAssembly(rws, crossJoinTable, true, commandDispatcher);
            }
         }
      }
   }

   /**
    * Fix assembly info.
    *  @param rws       the runtime worksheet.
    * @param assembly  the assembly.
    */
   public static void fixAssemblyInfo(
      RuntimeWorksheet rws, WSAssembly assembly)
   {
      WSAssemblyInfo info = assembly.isTable() ?
         ((TableAssembly) assembly).getTableInfo() :
         (WSAssemblyInfo) assembly.getInfo();

      fixAssemblyInfo0(info, assembly, rws);
   }

   /**
    * Fix assembly info.
    *
    * @param info     the specified assembly info.
    * @param assembly the specified assembly.
    * @param rws      the specified runtime worksheet.
    */
   private static void fixAssemblyInfo0(
      WSAssemblyInfo info, WSAssembly assembly,
      RuntimeWorksheet rws)
   {
      // fix class name
      String cls = assembly.getClass().getName();

      if(assembly instanceof DataTableAssembly && rws.isPreview()) {
         cls = "WSPreviewTable";
      }

      info.setClassName(cls);

      // fix size
      Dimension size = assembly.getPixelSize();
      info.setPixelSize(size);

      // fix message
      if(assembly instanceof ConditionAssembly) {
         ConditionAssembly cAssembly = (ConditionAssembly) assembly;
         ConditionList conds = cAssembly.getConditionList();
         info.setMessage(conds.toString());
         SourceInfo source = cAssembly.getAttachedSource();
         ((ConditionAssemblyInfo) info).setAttachedSource(source);
      }
      else if(assembly instanceof DateRangeAssembly) {
         DateCondition cond = ((DateRangeAssembly) assembly).getDateRange();
         info.setMessage(cond.toString());
      }
      else if(assembly instanceof NamedGroupAssembly) {
         NamedGroupInfo ninfo =
            ((NamedGroupAssembly) assembly).getNamedGroupInfo();
         info.setMessage(ninfo.toString());
      }
      else if(assembly instanceof VariableAssembly) {
         AssetVariable var = ((VariableAssembly) assembly).getVariable();
         String vname = assembly.getName();
         String valueString = "";
         AssetQuerySandbox box = rws.getAssetQuerySandbox();
         VariableTable vtable = box.getVariableTable();

         try {
            Object val = vtable.get(vname);

            if(val == null) {
               valueString = "";
            }
            else if(val instanceof Object[]) {
               Object[] vals = (Object[]) val;
               final StringBuilder valueStringBuilder = new StringBuilder();

               for(Object o : vals) {
                  valueStringBuilder.append(Tool.toString(o)).append(",");
               }

               valueString = valueStringBuilder.toString();

               if(valueString.length() > 0) {
                  valueString = valueString.substring(0, valueString.length() - 1);
                  valueString = "[" + valueString + "]";
               }
            }
            else {
               valueString = "[" + Tool.toString(val) + "]";
            }
         }
         catch(Exception ex) {
            // ignore
         }

         info.setMessage("".equals(valueString) ? var.toString() :
                            var.toString() + "\n" + Catalog.getCatalog().
                               getString("viewer.viewsheet.variable.currentValue",
                                         valueString));

         // expand the size to show the current value
         if(size.equals(new Dimension(AssetUtil.defw, 2 * AssetUtil.defh)) &&
            !"".equals(valueString))
         {
            info.setPixelSize(new Dimension(2 * AssetUtil.defw, 3 * AssetUtil.defh));
         }
      }
      else {
         info.setMessage(null);
      }

      if(assembly instanceof TableAssembly) {
         TableAssembly table = (TableAssembly) assembly;
         TableAssemblyInfo tinfo = table.getTableInfo();

         // fix aggregate defined
         AggregateInfo group = (table).getAggregateInfo();
         tinfo.setAggregateDefined(group != null && !group.isEmpty());

         // fix condition defined
         ConditionList preconds = table.getPreConditionList().getConditionList();
         ConditionList postconds = table.getPostConditionList().getConditionList();
         ConditionList topns = table.getRankingConditionList().getConditionList();
         boolean hasCondition = (preconds != null && preconds.getSize() > 0) ||
            (postconds != null && postconds.getSize() > 0) ||
            (topns != null && topns.getSize() > 0);

         if(!hasCondition) {
            ConditionListWrapper mvcond =
               ((TableAssembly) assembly).getMVConditionList();
            hasCondition = mvcond != null && !mvcond.isEmpty();
         }

         tinfo.setConditionDefined(hasCondition);

         // fix expression defined
         ColumnSelection columnSelection = table.getColumnSelection(false);
         boolean expressionDefined = columnSelection.stream()
            .anyMatch(a -> a.isExpression() &&
               !(((ColumnRef) a).getDataRef() instanceof DateRangeRef));
         tinfo.setExpressionDefined(expressionDefined);

         // fix sort defined
         tinfo.setSortDefined(!table.getSortInfo().isEmpty());
      }

      if(assembly instanceof EmbeddedTableAssembly) {
         EmbeddedTableAssembly etable = (EmbeddedTableAssembly) assembly;
         XTable data = etable instanceof SnapshotEmbeddedTableAssembly
            ? ((SnapshotEmbeddedTableAssembly) etable).getTable()
            : etable.getEmbeddedData();
         int count = data.getRowCount() - 1;
         count = Math.max(0, count);
         ((EmbeddedTableAssemblyInfo) info).setRowCount(count);
      }

      // fix primary
      boolean primary =
         assembly.equals(assembly.getWorksheet().getPrimaryAssembly());
      info.setPrimary(primary);
   }

   /**
    * Layout worksheet.
    *
    * @param rws the specified runtime worksheet.
    */
   public static void layout(RuntimeWorksheet rws, CommandDispatcher commandDispatcher) {
      layout(rws, new String[0], commandDispatcher);
   }

   /**
    * Layout worksheet.
    *
    * @param rws   the specified runtime worksheet.
    * @param names the specified assemblies.
    */
   public static void layout(RuntimeWorksheet rws, String[] names,
                                CommandDispatcher commandDispatcher)
   {
      if(names.length > 0) {
         double[] tops = new double[names.length];
         double[] lefts = new double[names.length];
         Worksheet ws = rws.getWorksheet();

         for(int i = 0; i < names.length; i++) {
            AbstractWSAssembly assembly = (AbstractWSAssembly) ws.getAssembly(names[i]);

            if(assembly != null && assembly.getPixelOffset() != null) {
               tops[i] = assembly.getPixelOffset().getY();
               lefts[i] = assembly.getPixelOffset().getX();
            }
         }

         WSMoveAssembliesCommand command = new WSMoveAssembliesCommand();
         command.setAssemblyNames(names);
         command.setTops(tops);
         command.setLefts(lefts);
         commandDispatcher.sendCommand(command);
      }
   }

   public static void layoutSchema(
      AbstractJoinTableAssembly joinTable, String[] names,
      CommandDispatcher commandDispatcher)
   {
      if(names.length > 0) {
         final double[] tops = new double[names.length];
         final double[] lefts = new double[names.length];
         final CompositeTableAssemblyInfo info = (CompositeTableAssemblyInfo) joinTable.getTableInfo();

         for(int i = 0; i < names.length; i++) {
            final Point2D.Double p = info.getSchemaPixelPosition(names[i]);

            if(p != null) {
               tops[i] = p.getY();
               lefts[i] = p.getX();
            }
         }

         final WSMoveSchemaTablesCommand command = new WSMoveSchemaTablesCommand();
         command.setJoinTableName(joinTable.getAbsoluteName());
         command.setAssemblyNames(names);
         command.setTops(tops);
         command.setLefts(lefts);
         commandDispatcher.sendCommand(command);
      }
   }

   /**
    * Refresh worksheet.
    *
    * @param rws the specified runtime worksheet.
    * @param principal
    */
   public static void refreshWorksheet(
      RuntimeWorksheet rws, WorksheetService worksheetService,
      CommandDispatcher commandDispatcher, Principal principal)
   {
      refreshWorksheet(rws, worksheetService, true, commandDispatcher, principal);
   }

   /**
    * Refresh worksheet.
    *  @param rws     the specified runtime worksheet.
    * @param initing <tt>true</tt> if is an initing worksheet.
    * @param principal
    */
   public static void refreshWorksheet(
      RuntimeWorksheet rws, WorksheetService worksheetService, boolean initing,
      CommandDispatcher commandDispatcher, Principal principal)
   {
      refreshWorksheet(rws, worksheetService, initing, true, commandDispatcher, principal);
   }

   /**
    * Refresh worksheet.
    *  @param rws     the specified runtime worksheet.
    * @param initing <tt>true</tt> if is an initing worksheet.
    * @param reset   <tt>true</tt> to reset cached data.
    * @param principal
    */
   public static void refreshWorksheet(
      RuntimeWorksheet rws, WorksheetService worksheetService,
      boolean initing, boolean reset, CommandDispatcher commandDispatcher, Principal principal)
   {
      Worksheet sheet = rws.getWorksheet();
      sheet.setLastSize(sheet.getPixelSize());
      AssetQuerySandbox box = rws.getAssetQuerySandbox();

      if(reset) {
         box.reset();
      }

      if(reset && refreshVariables(rws, worksheetService, null, commandDispatcher)) {
         return;
      }

      Assembly[] assemblies = sheet.getAssemblies(true);
      ArrayList<WSAssemblyModel> assemblyModels = new ArrayList<>();

      for(final Assembly assembly : assemblies) {
         if(initing) {
            fixAssemblyInfo(rws, (WSAssembly) assembly);
            refreshColumnSelection(rws, assembly.getName(), false);
         }

         try {
            box.setTimeLimited(assembly.getName(), false);

            if(assembly.isVisible() && assembly instanceof WSAssembly &&
               !assembly.getName().startsWith(Assembly.TABLE_VS_BOUND))
            {
               WSAssemblyModel model =
                  WSAssemblyModelFactory.createModelFrom((WSAssembly) assembly, rws, principal);

               if(model != null) {
                  assemblyModels.add(model);
               }
            }
         }
         catch(Exception ex) {
            LOG.warn("Failed to add init table data command to table assembly: " +
                        assembly.getName(), ex);
         }
      }

      RefreshWorksheetCommand command = RefreshWorksheetCommand.builder()
         .assemblies(assemblyModels)
         .build();
      commandDispatcher.sendCommand(command);
   }

   /**
    * Create an assembly in client side.
    *  @param rws      the specified runtime worksheet.
    * @param assembly the specified assembly.
    * @param commandDispatcher the command dispatcher.
    * @param principal
    */
   public static void createAssembly(
      RuntimeWorksheet rws, WSAssembly assembly, CommandDispatcher commandDispatcher,
      Principal principal) throws Exception
   {
      if(!assembly.isVisible()) {
         return;
      }

      WSAddAssemblyCommand command = WSAddAssemblyCommand.builder()
         .assembly(WSAssemblyModelFactory.createModelFrom(assembly, rws, principal))
         .build();
      commandDispatcher.sendCommand(command);
   }

   /**
    * Refresh column selection.
    *
    * @param rws       runtime worksheet.
    * @param name      assembly name.
    * @param recursive touch dependency or not.
    */
   public static void refreshColumnSelection(RuntimeWorksheet rws, String name, boolean recursive) {
      AssetQuerySandbox box = rws.getAssetQuerySandbox();

      if(box == null) {
         return;
      }

      try {
         box.refreshColumnSelection(name, recursive);
      }
      catch(Exception ex) {
         LOG.warn("Failed to refresh the column selection for assembly: " + name, ex);
      }
   }

   /**
    * Refresh column selection.
    *
    * @param rws       runtime worksheet.
    * @param name      assembly name.
    * @param recursive touch dependency or not.
    */
   public static void refreshColumnSelection(RuntimeWorksheet rws, String name, boolean recursive,
                                             CommandDispatcher dispatcher)
   {
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      Assembly[] tables = rws.getWorksheet().getAssemblies();

      try {
         try {
            box.refreshColumnSelection(name, recursive);
         }
         catch(CrossJoinCellCountBeyondLimitException ex) {
            String tname = ex.getTableName() != null ? ex.getTableName() : name;
            Assembly assembly = rws.getWorksheet().getAssembly(tname);

            if(assembly instanceof RelationalJoinTableAssembly) {
               String[] restTable = ((RelationalJoinTableAssembly) assembly).removeCrossJoinOperator();

               if(restTable == null || restTable.length == 0) {
                  removeAssembly(rws, assembly, dispatcher);
               }
               else {
                  box.refreshColumnSelection(name, recursive);
               }

               String message = Catalog.getCatalog().getString("composer.ws.crossJoin.limitedMessage");
               UserMessage userMessage = new UserMessage(message, ConfirmException.WARNING);
               Tool.addUserMessage(userMessage);

               if(dispatcher != null) {
                  refreshAssembly(rws, tname ,true, dispatcher, null);
               }
            }
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to refresh the column selection for assembly: " + name, ex);
      }
   }

   /**
    * Remove assembly.
    *
    * @param rws               the specified worksheet.
    * @param assembly          the specified assembly.
    * @param commandDispatcher the specified command dispatcher.
    */
   public static void removeAssembly(
      RuntimeWorksheet rws, Assembly assembly,
      CommandDispatcher commandDispatcher) throws Exception
   {
      removeAssembly(rws, assembly, false, commandDispatcher);
   }

   /**
    * Remove assembly.
    *
    * @param rws               the specified worksheet.
    * @param assembly          the specified assembly.
    * @param commandDispatcher the specified command dispatcher.
    */
   private static void removeAssembly(
      RuntimeWorksheet rws, Assembly assembly, boolean recursive,
      CommandDispatcher commandDispatcher) throws Exception
   {
      Worksheet ws = rws.getWorksheet();

      if(ws.removeAssembly(assembly.getName())) {
         if(assembly instanceof DateRangeAssembly) {
            refreshDateRange(ws);
         }

         AssetQuerySandbox box = rws.getAssetQuerySandbox();
         box.reset(assembly.getAssemblyEntry(), false);

         WSRemoveAssemblyCommand command = new WSRemoveAssemblyCommand();
         command.setAssemblyName(assembly.getName());
         commandDispatcher.sendCommand(command);

         if(recursive) {
            ws.checkDependencies();
            AssemblyRef[] arr = ws.getDependings(assembly.getAssemblyEntry());
            WSAssembly[] assemblies = new WSAssembly[arr.length];

            for(int i = 0; i < arr.length; i++) {
               AssemblyEntry entry = arr[i].getEntry();
               assemblies[i] = (WSAssembly) ws.getAssembly(entry.getName());
            }

            for(WSAssembly sub : assemblies) {
               if(sub == null) {
                  continue;
               }

               removeAssembly(rws, sub, true, commandDispatcher);
            }
         }
      }
      else {
         MessageCommand command = new MessageCommand();
         command.setMessage(Catalog.getCatalog().getString(
            "common.removeAssemblyFailed"));
         command.setType(MessageCommand.Type.WARNING);
         command.setAssemblyName(assembly.getName());
         commandDispatcher.sendCommand(command);
      }
   }

   /**
    * Open worksheet.
    * @param engine the specified worksheet engine.
    * @param user the specified user.
    * @param entry the specified worksheet entry.
    * @param openAutoSaved whether the worksheet getting opened is autosaved.
    * @param commandDispatcher the specified command dispatcher.
    *
    * @return the runtime id of the open worksheet
    */
   public static String openWorksheet(
      WorksheetService engine, Principal user, AssetEntry entry, boolean openAutoSaved,
      CommandDispatcher commandDispatcher) throws Exception
   {
      return openWorksheet(engine, user, entry, openAutoSaved, false, commandDispatcher);
   }

   /**
    * Open worksheet.
    * @param engine the specified worksheet engine.
    * @param user the specified user.
    * @param entry the specified worksheet entry.
    * @param openAutoSaved whether the worksheet getting opened is autosaved.
    * @param commandDispatcher the specified command dispatcher.
    *
    * @return the runtime id of the open worksheet
    */
   public static String openWorksheet(
      WorksheetService engine, Principal user, AssetEntry entry, boolean openAutoSaved,
      boolean gettingStartedCreateQuery, CommandDispatcher commandDispatcher) throws Exception
   {
      String id = engine.openWorksheet(entry, user);
      RuntimeWorksheet rws = engine.getWorksheet(id, user);
      fixWorksheetMode(rws);
      clearGettingStatedRuntimeWs(rws, gettingStartedCreateQuery);

      List errors = (List) AssetRepository.ASSET_ERRORS.get();

      String label, alias = entry.getAlias();

      if(alias != null && alias.length() > 0) {
         label = alias;
      }
      else {
         label = entry.getName();
      }

      if(openAutoSaved) {
         rws.setSavePoint(-1);
      }

      WorksheetModel worksheet = new WorksheetModel();
      worksheet.setId(entry.toIdentifier());
      worksheet.setRuntimeId(id);
      worksheet.setLabel(label);
      worksheet.setNewSheet("true".equals(entry.getProperty("openAutoSaved")));
      worksheet.setType("worksheet");
      worksheet.setCurrent(rws.getCurrent());
      worksheet.setSavePoint(rws.getSavePoint());
      worksheet.setSingleQuery(rws.getWorksheet().getWorksheetInfo().isSingleQueryMode());

      OpenWorksheetCommand command = new OpenWorksheetCommand();
      command.setWorksheet(worksheet);
      commandDispatcher.sendCommand(command);
      commandDispatcher.sendCommand(new WSInitCommand(user));

      resetTableModes(rws);
      refreshWorksheet(rws, engine, commandDispatcher, user);

      Worksheet ws = rws.getWorksheet();
      String[] levels = null;

      if(ws != null) {
         WorksheetInfo wsInfo = ws.getWorksheetInfo();

         if(wsInfo != null) {
            levels = wsInfo.getMessageLevels();
         }
      }

      WSSetMessageLevelsCommand messageLevelsCommand = new WSSetMessageLevelsCommand();
      messageLevelsCommand.setMessageLevels(levels);
      commandDispatcher.sendCommand(messageLevelsCommand);

      if(!openAutoSaved && rws.getWorksheet() != null) {
         rws.replaceCheckpoint(rws.getWorksheet().prepareCheckpoint(), null);
      }

      if(errors != null && errors.size() > 0) {
         StringBuilder sb = new StringBuilder();

         for(int i = 0; i < errors.size(); i++) {
            if(i > 0) {
               sb.append(", ");
            }

            sb.append(errors.get(i));
         }

         sb.append("(").append(entry.getDescription()).append(")");

         errors.clear();

         String msg = Catalog.getCatalog().getString(
            "common.mirrorAssemblies.updateFailed", sb.toString());
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(msg);
         messageCommand.setType(MessageCommand.Type.ERROR);
         commandDispatcher.sendCommand(messageCommand);
      }

      WorksheetInfo worksheetInfo = ws.getWorksheetInfo();
      Assembly[] assemblies = ws.getAssemblies();

      if(worksheetInfo.isSingleQueryMode() && assemblies != null && assemblies.length == 1) {
         Assembly assembly = assemblies[0];

         if(assembly instanceof SQLBoundTableAssembly) {
            WSEditAssemblyCommand editAssemblyCommand = WSEditAssemblyCommand.builder()
               .assembly(WSAssemblyModelFactory.createModelFrom((WSAssembly) assembly, rws, user))
               .build();
            commandDispatcher.sendCommand(editAssemblyCommand);
         }
      }

      return id;
   }

   private static void fixWorksheetMode(RuntimeWorksheet rws) {
      Worksheet worksheet = rws.getWorksheet();
      Assembly[] assemblies = worksheet.getAssemblies();
      WorksheetInfo worksheetInfo = worksheet.getWorksheetInfo();

      if(worksheetInfo.isSingleQueryMode()) {
         if(assemblies == null) {
            return;
         }

         if(assemblies.length > 1 ||
            !(assemblies[0] instanceof SQLBoundTableAssembly))
         {
            worksheetInfo.setMashupMode();
         }
      }
   }

   public static void updateWorksheetMode(RuntimeWorksheet rws) {
      Worksheet worksheet = rws.getWorksheet();
      Assembly[] assemblies = worksheet.getAssemblies();
      WorksheetInfo worksheetInfo = worksheet.getWorksheetInfo();

      if(worksheetInfo.isDefaultMode() && assemblies != null && assemblies.length == 1 &&
         assemblies[0] instanceof SQLBoundTableAssembly)
      {
         worksheetInfo.setSingleQueryMode();
      }
   }

   private static void clearGettingStatedRuntimeWs(RuntimeWorksheet rws,
                                                   boolean gettingStartedCreateQuery)
   {
      if(rws == null || !rws.isGettingStarted()) {
         return;
      }

      Worksheet worksheet = rws.getWorksheet();
      WorksheetInfo worksheetInfo = worksheet.getWorksheetInfo();
      Assembly[] assemblies = worksheet.getAssemblies();

      if(gettingStartedCreateQuery && worksheetInfo.isSingleQueryMode() && assemblies != null &&
         assemblies.length == 1)
      {
         return;
      }

      if(assemblies == null || assemblies.length == 0) {
         return;
      }

      for(Assembly assembly : assemblies) {
         worksheet.removeAssembly(assembly);
      }
   }

   /**
    * Reset the table modes of the worksheet to design mode.
    *
    * @param rws the worksheet to reset the tables of
    */
   private static void resetTableModes(RuntimeWorksheet rws) {
      final Worksheet worksheet = rws.getWorksheet();
      final TableAssembly[] tables = Arrays.stream(worksheet.getAssemblies())
         .filter(TableAssembly.class::isInstance)
         .map(TableAssembly.class::cast)
         .toArray(TableAssembly[]::new);

      for(TableAssembly table : tables) {
         TableModeController.setDefaultTableMode(table, rws.getAssetQuerySandbox());
      }
   }

   /**
    * Open worksheet.
    * @param engine the specified worksheet engine.
    * @param user the specified user.
    * @param id the specified worksheet id.
    * @param commandDispatcher the specified command dispatcher.
    */
   public static void openWorksheet(WorksheetService engine, Principal user,
                                    String id, CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeWorksheet rws = engine.getWorksheet(id, user);
      refreshWorksheet(rws, engine, commandDispatcher, user);
   }

   /**
    * Get builtin condition and daterange assemblies.
    *
    * @param ws worksheet.
    */
   public static void refreshDateRange(Worksheet ws) {
      DateCondition[] builtin = DateCondition.getBuiltinDateConditions();

      // clone the condition
      for(int i = 0; i < builtin.length; i++) {
         builtin[i] = (DateCondition) builtin[i].clone();
      }

      ArrayList<DateRangeAssembly> daterange = new ArrayList<>();
      Assembly[] assemblies = ws.getAssemblies();

      // clone the daterange
      for(final Assembly assembly : assemblies) {
         if(assembly instanceof DateRangeAssembly && assembly.isVisible()) {
            daterange.add((DateRangeAssembly) assembly.clone());
         }
      }

      DateRangeAssembly[] range = new DateRangeAssembly[daterange.size()];
      daterange.toArray(range);
   }

   private static void renameAssembly(
      RuntimeWorksheet rws, String nname, String oname,
      CommandDispatcher commandDispatcher, Principal principal) throws Exception
   {
      Worksheet ws = rws.getWorksheet();

      if(!oname.equals(nname)) {
         if(ws.renameAssembly(oname, nname, true)) {
            refreshAssembly(rws, nname, false, commandDispatcher, principal);
            Assembly assembly = ws.getAssembly(nname);

            // if we do not refresh the depending assemblies, the infos
            // might be out-of-date, then the editing process might be false
            AssemblyRef[] refs = ws.getDependings(assembly.getAssemblyEntry());

            for(final AssemblyRef ref : refs) {
               AssemblyEntry entry = ref.getEntry();

               if(entry.isWSAssembly()) {
                  refreshAssembly(rws, entry.getName(), false, commandDispatcher, principal);
                  refreshColumnSelection(rws, entry.getName(), false);
                  loadTableData(rws, entry.getName(), false, false);
               }
            }

            if(ws.getAssembly(nname) instanceof DateRangeAssembly) {
               refreshDateRange(ws);
            }

            AssetEventUtil.refreshTableLastModified(ws, nname, true);
         }
      }
   }

   /**
    * Get the mode of a table assembly.
    *
    * @param table the specified table assembly.
    *
    * @return the mode of the table assembly.
    */
   public static int getMode(TableAssembly table) {
      if(table instanceof ComposedTableAssembly) {
         ComposedTableAssembly cTable = (ComposedTableAssembly) table;

         if(cTable.isComposed() && !cTable.isRuntime() && cTable.isHierarchical()) {
            cTable.setLiveData(true);
            TableAssemblyInfo info = table.getTableInfo();
            AggregateInfo group = table.getAggregateInfo();
            info.setAggregate(group != null && !group.isEmpty());
         }
      }

      if(table.isEditMode()) {
         return AssetQuerySandbox.EDIT_MODE;
      }
      else if(table.isRuntime()) {
         return AssetQuerySandbox.RUNTIME_MODE;
      }
      else if(table.isLiveData()) {
         return AssetQuerySandbox.LIVE_MODE;
      }

      return AssetQuerySandbox.DESIGN_MODE;
   }

   /**
    * Check whether or not the table lens of this table is allowed to be reset.
    * This check helps in preventing excessive query execution.
    *
    * @param table the table to check
    *
    * @return true if the table can be reset, false otherwise
    */
   private static boolean canResetTableLens(TableAssembly table) {
      return WorksheetEventUtil.getMode(table) != AssetQuerySandbox.RUNTIME_MODE;
   }

   public static TableAssemblyOperator.Operator convertOperator(
      Worksheet ws, TableAssemblyOperatorModel operator)
   {
      TableAssemblyOperator.Operator temp = new TableAssemblyOperator.Operator();

      temp.setOperation(operator.getOperation());
      temp.setDistinct(operator.getDistinct());
      temp.setLeftTable(operator.getLtable());
      temp.setRightTable(operator.getRtable());

      if(operator.getLtable() != null && !"null".equals(operator.getLtable())
         && operator.getLref() != null)
      {
         temp.setLeftAttribute(((TableAssembly) ws.getAssembly(operator.getLtable()))
                                  .getColumnSelection(true)
                                  .getAttribute(operator.getLref().getName()));
      }

      if(operator.getRtable() != null && !"null".equals(operator.getRtable()) &&
         operator.getRref() != null)
      {
         temp.setRightAttribute(((TableAssembly) ws.getAssembly(operator.getRtable()))
               .getColumnSelection(true)
               .getAttribute(operator.getRref().getName()));
      }

      return temp;
   }

   /**
    * Focus the given assembly.
    * @param assemblyName the names of the assemblies to focus
    * @param commandDispatcher the command dispatcher
    */
   public static void focusAssembly(
      String assemblyName, CommandDispatcher commandDispatcher)
   {
      WSFocusAssembliesCommand focusAssembliesCommand = WSFocusAssembliesCommand.builder()
         .addAssemblyNames(assemblyName)
         .build();
      commandDispatcher.sendCommand(focusAssembliesCommand);
   }

   /**
    * Focus the given assemblies.
    * @param assemblyNames the names of the assemblies to focus
    * @param commandDispatcher the command dispatcher
    */
   public static void focusAssemblies(
      List<String> assemblyNames, CommandDispatcher commandDispatcher)
   {
      WSFocusAssembliesCommand focusAssembliesCommand = WSFocusAssembliesCommand.builder()
         .assemblyNames(assemblyNames)
         .build();
      commandDispatcher.sendCommand(focusAssembliesCommand);
   }

   /**
    * Checks whether there would exist a cyclical dependency between two worksheet assemblies if
    * the otherAssembly becomes a depended of the targetAssembly.
    *
    * @param ws             the worksheet the two assemblies are contained in
    * @param targetAssembly the target assembly
    * @param otherAssembly  the other assembly
    *
    * @return true if there would exist a cyclical dependency between the two assemblies,
    * false otherwise
    */
   public static boolean checkCyclicalDependency(
      Worksheet ws, WSAssembly targetAssembly, WSAssembly otherAssembly)
   {
      Assembly[] dependedAssemblies =
         AssetUtil.getDependedAssemblies(ws, otherAssembly, false);

      return Arrays.stream(dependedAssemblies).anyMatch(
         (assembly) -> assembly.getName().equals(targetAssembly.getName()));

   }

   /**
    * Populates assemblyDeps such that for every assembly key, the value will be a set containing
    * the "blocking assemblies" dependings for that assembly. A "blocking assembly" is an assembly
    * that should be refreshed before the current assembly is refreshed. This way, each assembly
    * is refreshed exactly once.
    *
    * @param ws           the given worksheet
    * @param assembly     the given assembly
    * @param assemblyDeps the assembly-dependings map
    */
   private static void populateBlockingAssemblyDependencies(
      Worksheet ws, WSAssembly assembly,
      Map<String, Set<String>> assemblyDeps)
   {
      AssemblyRef[] arr = ws.getDependings(assembly.getAssemblyEntry());
      WSAssembly[] subAssemblies = Arrays.stream(arr)
         .map((ref) -> ws.getAssembly(ref.getEntry().getName()))
         .map(WSAssembly.class::cast)
         .toArray(WSAssembly[]::new);

      for(WSAssembly subAssembly : subAssemblies) {
         Set<String> names = assemblyDeps.computeIfAbsent(subAssembly.getAbsoluteName(),
                                                          k -> new HashSet<>());

         if(!names.contains(assembly.getAbsoluteName())) {
            names.add(assembly.getAbsoluteName());
            populateBlockingAssemblyDependencies(ws, subAssembly, assemblyDeps);
         }
      }
   }

   public static void syncDataTypes(RuntimeWorksheet rws, WSAssembly assembly,
                                    CommandDispatcher commandDispatcher, Principal principal) throws Exception
   {
      syncDataTypes(rws, assembly, commandDispatcher, principal, true);
   }

   /**
    *  After base table in ws in changes, should update join table data types to sync.
    *
    * @param rws           the given worksheet
    * @param assembly     the given assembly
    */
   public static void syncDataTypes(RuntimeWorksheet rws, WSAssembly assembly,
      CommandDispatcher commandDispatcher, Principal principal, boolean refreshOperators)
      throws Exception
   {
      Worksheet ws = rws.getWorksheet();
      AssemblyRef[] arr = ws.getDependings(assembly.getAssemblyEntry());

      if(!(assembly instanceof SnapshotEmbeddedTableAssembly)) {
         return;
      }

      SnapshotEmbeddedTableAssembly snap = (SnapshotEmbeddedTableAssembly) assembly;

      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      ColumnSelection cols = snap.getColumnSelection();
      String tableName = snap.getName();

      for(int i = 0; i < arr.length; i++) {
         AssemblyEntry entry = arr[i].getEntry();
         WSAssembly ass = (WSAssembly) ws.getAssembly(entry.getName());

         if(ass instanceof RelationalJoinTableAssembly) {
            RelationalJoinTableAssembly join = (RelationalJoinTableAssembly) ass;
            syncColumnDataTypes(join.getTableInfo().getPublicColumnSelection(), tableName, cols);
            syncColumnDataTypes(join.getTableInfo().getPrivateColumnSelection(), tableName, cols);
            clearDataCache(join, box);
            WorksheetEventUtil.refreshAssembly(rws, ass.getAbsoluteName(), null, true, null,
               commandDispatcher, principal, refreshOperators);
         }
      }
   }

   public static void clearDataCache(TableAssembly table, AssetQuerySandbox box) throws Exception {
      DataKey key1 = AssetDataCache.getCacheKey(table, box, null,
         AssetQuerySandbox.LIVE_MODE, true);
      DataKey key2 = AssetDataCache.getCacheKey(table, box, null,
         AssetQuerySandbox.RUNTIME_MODE, true);
      AssetDataCache.removeCachedData(key1);
      AssetDataCache.removeCachedData(key2);
      AssetQueryCacheNormalizer.clearCache(table, box);
   }

   private static void syncColumnDataTypes(ColumnSelection cols, String tname,
                                           ColumnSelection ncols)
   {
      for(int i = 0; i < cols.getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) cols.getAttribute(i);

         // check if the column is from the base table
         if(!Tool.equals(tname, col.getEntity())) {
            continue;
         }

         ColumnRef ncol = (ColumnRef) ncols.getAttribute(col.getAttribute());

         if(ncol != null) {
            col.setDataType(ncol.getDataType());
         }
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(AssetEventUtil.class);
}
