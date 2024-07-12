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
package inetsoft.web.composer.ws.assembly;

import inetsoft.report.composition.*;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.lens.CrossJoinCellCountBeyondLimitException;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.web.binding.drm.AggregateRefModel;
import inetsoft.web.binding.model.AggregateInfoModel;
import inetsoft.web.binding.model.GroupRefModel;
import inetsoft.web.composer.model.ws.WSTableStatusIndicatorTooltipContainer;
import inetsoft.web.composer.ws.assembly.tableassembly.*;
import inetsoft.web.composer.ws.assembly.tableinfo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

public class TableAssemblyModelFactory {
   private TableAssemblyModelFactory() {
   }

   public static TableAssemblyModel createModelFrom(AbstractTableAssembly assembly,
                                                    RuntimeWorksheet rws, Principal principal)
   {
      final TableAssemblyModel table;

      if(assembly instanceof ConcatenatedTableAssembly) {
         table = new ConcatenatedTableAssemblyModel((ConcatenatedTableAssembly) assembly, rws);
      }
      else if(assembly instanceof CompositeTableAssembly) {
         table = new CompositeTableAssemblyModel((CompositeTableAssembly) assembly, rws);
      }
      else if(assembly instanceof SQLBoundTableAssembly) {
         boolean sqlEnabled = false;

         try {
            sqlEnabled = SecurityEngine.getSecurity().checkPermission(
                    principal, ResourceType.PHYSICAL_TABLE, "*", ResourceAction.ACCESS);
         }
         catch(SecurityException ignore) {
         }

         table = new SQLBoundTableAssemblyModel((SQLBoundTableAssembly) assembly, rws, sqlEnabled);
      }
      else {
         table = new TableAssemblyModel(assembly, rws);
      }

      setTableLensMetaData(assembly, rws, table);
      setAssemblyInfo(assembly, table, principal);
      setColumnInfos(assembly, rws, table);
      setTooltipContainer(assembly, table);
      setColumnTypeEnabled(assembly, table);
      setAggregateInfo(assembly, table);
      return table;
   }

   private static void setTableLensMetaData(TableAssembly assembly, RuntimeWorksheet rws,
                                            TableAssemblyModel model)
   {
      final AssetQuerySandbox box = rws.getAssetQuerySandbox();
      XTable lens = null;

      if(box == null) {
         return;
      }

      try {
         lens = box.getTableLens(assembly.getAbsoluteName(), WorksheetEventUtil.getMode(assembly));
      }
      catch(CrossJoinCellCountBeyondLimitException ex) {
         throw ex;
      }
      catch(Exception e) {
         if(box.isDisposed()) {
            return;
         }

         LOG.error("Failed to execute table: " + assembly.getAbsoluteName(), e);
      }

      int numRows = lens == null ? 0 : lens.getRowCount();
      model.setRowsCompleted(numRows >= 0);
      numRows = numRows < 0 ? -numRows - 1 : numRows;
      model.setTotalRows(Math.max(numRows - 1, 0));
      model.setHasMaxRow(AssetEventUtil.hasMaxRowSetting(assembly));

      if(model.isHasMaxRow()) {
         model.setExceededMaximum(AssetEventUtil.getExceededMsg(assembly, model.getTotalRows()));
      }
   }

   private static void setAssemblyInfo(TableAssembly assembly, TableAssemblyModel model,
                                       Principal principal)
   {
      final TableAssemblyInfoModel info;

      if(assembly.getTableInfo() instanceof TabularTableAssemblyInfo) {
         info = new TabularTableAssemblyInfoModel(
            (TabularTableAssemblyInfo) assembly.getTableInfo(), principal);
      }
      else if(assembly.getTableInfo() instanceof MirrorTableAssemblyInfo) {
         info = new MirrorTableAssemblyInfoModel((MirrorTableAssemblyInfo) assembly.getTableInfo());
      }
      else if(assembly.getTableInfo() instanceof BoundTableAssemblyInfo) {
         info = new BoundTableAssemblyInfoModel((BoundTableAssemblyInfo) assembly.getTableInfo());
      }
      else if(assembly.getTableInfo() instanceof CompositeTableAssemblyInfo) {
         info = new CompositeTableAssemblyInfoModel(
            (CompositeTableAssemblyInfo) assembly.getInfo());
      }
      else if(assembly.getTableInfo() instanceof UnpivotTableAssemblyInfo) {
         info = new UnpivotTableAssemblyInfoModel(
            (UnpivotTableAssemblyInfo) assembly.getTableInfo());
      }
      else {
         info = new TableAssemblyInfoModel(assembly.getTableInfo());
      }

      model.setInfo(info);
   }

   private static void setColumnInfos(TableAssembly assembly, RuntimeWorksheet rws,
                                      TableAssemblyModel model)
   {
      final ArrayList<ColumnInfoModel> columnInfoModels = new ArrayList<>();
      final AssetQuerySandbox box = rws.getAssetQuerySandbox();

      try {
         int mode = WorksheetEventUtil.getMode(assembly);
         final List<ColumnInfo> columnInfos = box.getColumnInfos(
            assembly.getAbsoluteName(), WorksheetEventUtil.getMode(assembly));
         boolean pub = AssetQuerySandbox.isRuntimeMode(mode) || AssetQuerySandbox.isEmbeddedMode(mode) ||
            assembly.isAggregate();
         ColumnSelection columns = assembly.getColumnSelection(pub);

         for(ColumnInfo col : columnInfos) {
            int index = columns.indexOfAttribute(col.getColumnRef());
            columnInfoModels.add(new ColumnInfoModel(col, index));
         }
      }
      catch(Exception e) {
         if(box == null || box.isDisposed()) {
            return;
         }

         LOG.warn("Failed to get column infos: " + e, e);
      }

      model.setColInfos(columnInfoModels);
   }

   private static void setTooltipContainer(AbstractTableAssembly assembly, TableAssemblyModel table)
   {
      final ToolTipGenerator gen = new ToolTipGenerator(assembly);
      final ToolTipContainer toolTipContainer = gen.generateToolTip();

      table.getInfo().setTooltipContainer(
         WSTableStatusIndicatorTooltipContainer.builder()
            .aggregate(toolTipContainer.getAggregate())
            .condition(toolTipContainer.getCondition())
            .sort(toolTipContainer.getSort())
            .build());
   }

   private static void setColumnTypeEnabled(AbstractTableAssembly assembly,
                                            TableAssemblyModel table)
   {
      final boolean columnTypeEnabled;

      if(assembly instanceof EmbeddedTableAssembly || assembly instanceof UnpivotTableAssembly) {
         columnTypeEnabled = true;
      }
      else if(assembly instanceof TabularTableAssembly) {
         columnTypeEnabled = isColumnTypeEnabled((TabularTableAssembly) assembly);
      }
      else {
         columnTypeEnabled = false;
      }

      table.setColumnTypeEnabled(columnTypeEnabled);
   }

   private static boolean isColumnTypeEnabled(TabularTableAssembly assembly) {
      final boolean columnTypeEnabled;
      final TabularTableAssemblyInfo info = (TabularTableAssemblyInfo) assembly.getTableInfo();
      final TabularQuery query = info.getQuery();

      if(query != null) {
         final TabularDataSource dataSource = (TabularDataSource) query.getDataSource();
         columnTypeEnabled = dataSource != null && dataSource.isTypeConversionSupported();
      }
      else {
         columnTypeEnabled = false;
      }

      return columnTypeEnabled;
   }

   private static void setAggregateInfo(AbstractTableAssembly assembly, TableAssemblyModel table) {
      final AggregateInfo aggregateInfo = assembly.getAggregateInfo();
      final AggregateInfoModel model = new AggregateInfoModel();
      final List<AggregateRefModel> aggregates = Arrays.stream(aggregateInfo.getAggregates())
         .map(AggregateRefModel::new)
         .collect(Collectors.toList());
      final List<GroupRefModel> groups = Arrays.stream(aggregateInfo.getGroups())
         .map(GroupRefModel::new)
         .collect(Collectors.toList());
      final List<AggregateRefModel> secondaryAggregates =
         Arrays.stream(aggregateInfo.getSecondaryAggregates())
         .map(AggregateRefModel::new)
         .collect(Collectors.toList());

      model.setGroups(groups);
      model.setAggregates(aggregates);
      model.setSecondaryAggregates(secondaryAggregates);
      model.setCrosstab(aggregateInfo.isCrosstab());
      table.setAggregateInfo(model);

      if(groups.isEmpty() && aggregates.isEmpty()) {
         table.getInfo().setHasAggregate(false);
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(TableAssemblyModelFactory.class);
}
