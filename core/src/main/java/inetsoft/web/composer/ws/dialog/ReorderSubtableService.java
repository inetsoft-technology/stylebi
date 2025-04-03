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
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.CompositeTableAssemblyInfo;
import inetsoft.uql.asset.internal.SchemaTableInfo;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.ReorderSubtablesEvent;
import inetsoft.web.composer.ws.event.WSMergeAddJoinTableEvent;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@ClusterProxy
public class ReorderSubtableService extends WorksheetControllerService {

   public ReorderSubtableService(ViewsheetService viewsheetService)
   {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void reorderSubtables(@ClusterProxyKey String runtimeId, ReorderSubtablesEvent event,
                                Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      final RuntimeWorksheet rws = getRuntimeWorksheet(runtimeId, principal);
      final CompositeTableAssembly table =
         (CompositeTableAssembly) rws.getWorksheet().getAssembly(event.parentTable());
      final String[] subtables = event.subtables();

      if(table instanceof AbstractJoinTableAssembly) {
         updateOperators(table, subtables);
      }

      TableAssemblyOperator[] ops = null;

      if(table instanceof ConcatenatedTableAssembly) {
         ops = new TableAssemblyOperator[subtables.length - 1];

         for(int i = 0; i < ops.length; i++) {
            ops[i] = table.getOperator(i);
         }
      }

      table.reorderTableAssemblies(Arrays.stream(subtables).map(
            (subtable) -> (TableAssembly) rws.getWorksheet().getAssembly(subtable))
                                      .toArray(TableAssembly[]::new));

      if(ops != null) {
         for(int i = 0; i < ops.length; i++) {
            table.setOperator(i, ops[i]);
         }
      }

      WorksheetEventUtil.refreshColumnSelection(rws, event.parentTable(), true);
      WorksheetEventUtil.loadTableData(rws, event.parentTable(), true, true);
      WorksheetEventUtil.refreshAssembly(
         rws, event.parentTable(), true, commandDispatcher, principal);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void reorderJoinTable(@ClusterProxyKey String runtimeId, WSMergeAddJoinTableEvent event,
                                Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      final RuntimeWorksheet rws = getRuntimeWorksheet(runtimeId, principal);
      final Worksheet ws = rws.getWorksheet();
      final MergeJoinTableAssembly mergeTable =
         (MergeJoinTableAssembly) ws.getAssembly(event.mergeTableName());
      final TableAssembly newTable = (TableAssembly) ws.getAssembly(event.newTableName());
      final TableAssembly[] oldAssemblies = mergeTable.getTableAssemblies(true);
      final List<TableAssembly> oldAssembliesList = new ArrayList<>(Arrays.asList(oldAssemblies));
      final int index = event.insertionIndex();
      final int oldIndex = oldAssembliesList.stream()
         .map(Assembly::getName)
         .collect(Collectors.toList())
         .indexOf(newTable.getName());

      if(oldIndex == index || oldIndex == index - 1) {
         return null;
      }
      else if(index < oldIndex) {
         oldAssembliesList.remove(oldIndex);
         oldAssembliesList.add(index, newTable);
      }
      else {
         oldAssembliesList.remove(oldIndex);
         oldAssembliesList.add(index - 1, newTable);
      }

      final String[] subTablesName = oldAssembliesList.stream()
         .map(Assembly::getName)
         .toArray(String[]::new);
      updateOperators(mergeTable, subTablesName);
      final TableAssembly[] newAssemblies = oldAssembliesList.toArray(new TableAssembly[0]);
      mergeTable.setTableAssemblies(newAssemblies);
      WorksheetEventUtil.refreshColumnSelection(rws, newTable.getName(), false);
      WorksheetEventUtil.loadTableData(rws, mergeTable.getName(),
                                       false, false);
      WorksheetEventUtil.refreshAssembly(
         rws, event.newTableName(), true, commandDispatcher, principal);

      return null;
   }

   private void updateOperators(CompositeTableAssembly ctable, String[] subtables) {
      final List<String> subtablesList = Arrays.asList(subtables);
      final Enumeration tbls = ctable.getOperatorTables();
      final Map<String, SchemaTableInfo> oldSchemaTableInfos = new HashMap<>(
         ((CompositeTableAssemblyInfo) ctable
            .getTableInfo()).getSchemaTableInfos());
      final ArrayList<String[]> pairs = new ArrayList<>();

      while(tbls.hasMoreElements()) {
         final String[] tables = (String[]) tbls.nextElement();
         pairs.add(tables);
      }

      for(String[] tables : pairs) {
         final TableAssemblyOperator top = ctable.getOperator(tables[0], tables[1]);
         final int leftTableIndex = subtablesList.indexOf(tables[0]);
         final int rightTableIndex = subtablesList.indexOf(tables[1]);

         for(TableAssemblyOperator.Operator op : top.getOperators()) {
            op.setLeftTable(tables[0]);
            op.setRightTable(tables[1]);
         }

         if(leftTableIndex >= 0 && rightTableIndex >= 0 && leftTableIndex > rightTableIndex) {
            for(TableAssemblyOperator.Operator op : top.getOperators()) {
               InnerJoinService.flipOp(op);
            }

            ctable.setOperator(tables[1], tables[0], top);
            ctable.removeOperator(tables[0], tables[1]);
         }
         else {
            ctable.setOperator(tables[0], tables[1], top);
         }
      }

      final CompositeTableAssemblyInfo info = (CompositeTableAssemblyInfo) ctable
         .getTableInfo();

      // Reinstate old schema table positions.
      for(Map.Entry<String, SchemaTableInfo> e : oldSchemaTableInfos.entrySet()) {
         info.setSchemaTableInfo(e.getKey(), e.getValue());
      }
   }
}
