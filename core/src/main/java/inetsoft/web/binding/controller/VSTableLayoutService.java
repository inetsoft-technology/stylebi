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

package inetsoft.web.binding.controller;


import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.binding.*;
import inetsoft.report.internal.table.ReportLayoutTool;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.CalcAggregate;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.binding.command.*;
import inetsoft.web.binding.drm.AggregateRefModel;
import inetsoft.web.binding.event.*;
import inetsoft.web.binding.handler.*;
import inetsoft.web.binding.model.NamedGroupInfoModel;
import inetsoft.web.binding.model.table.*;
import inetsoft.web.binding.model.table.CellBindingInfo;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.condition.ConditionExpression;
import inetsoft.web.composer.vs.objects.event.ResizeCalcTableCellEvent;
import inetsoft.web.viewsheet.command.AssemblyLoadingCommand;
import inetsoft.web.viewsheet.command.ClearAssemblyLoadingCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Service
@ClusterProxy
public class VSTableLayoutService {
   public VSTableLayoutService(
      VSRowColGroupHandler rowColGroupHandler,
      DataRefModelFactoryService refModelService,
      VSAssemblyInfoHandler assemblyInfoHandler,
      TableLayoutHandler layoutHandler,
      VSCalcCellScriptHandler cellScriptHandler,
      VSColumnHandler columnsHandler,
      ViewsheetService viewsheetService)
   {
      this.rowColGroupHandler = rowColGroupHandler;
      this.refModelService = refModelService;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.layoutHandler = layoutHandler;
      this.cellScriptHandler = cellScriptHandler;
      this.columnsHandler = columnsHandler;
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void getLayout(@ClusterProxyKey String id, GetTableLayoutEvent event,
                         Principal principal, CommandDispatcher dispatcher) throws Exception
   {

      if(!quickCheckForCalc(id, event.getName(), principal)) {
         return null;
      }

      try {
         dispatcher.sendCommand(event.getName(), new AssemblyLoadingCommand());
         String name = event.getName();
         ViewsheetService engine = viewsheetService;
         RuntimeViewsheet rvs = engine.getViewsheet(id, principal);
         CalcTableVSAssembly assembly = getCalcTableVSAssembly(rvs, name);
         GetTableLayoutCommand cmd = createTableLayoutCommand(rvs, assembly);

         if(cmd != null) {
            dispatcher.sendCommand(name, cmd);
         }
      }
      finally {
         dispatcher.sendCommand(event.getName(), new ClearAssemblyLoadingCommand());
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void getCellBinding(@ClusterProxyKey String id, GetCellBindingEvent event,
                              Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      if(!quickCheckForCalc(id, event.getName(), principal)) {
         return null;
      }

      String name = event.getName();
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(id, principal);
      CalcTableVSAssembly nassembly = getCalcTableVSAssembly(rvs, name);
      TableCell[] cells = event.getSelectCells();

      if(cells != null && cells.length > 0) {
         TableCell cell = cells[cells.length - 1];
         int row = cell.getRow();
         int col = cell.getCol();
         dispatcher.sendCommand(name, createCellBindingCommand(rvs, nassembly, row, col));
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void changeColumnValue(@ClusterProxyKey String id, ChangeColumnValueEvent event,
                                 String linkUri, Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      String name = event.name();
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(id, principal);
      CalcTableVSAssembly assembly = getCalcTableVSAssembly(rvs, name);
      CalcTableVSAssemblyInfo oinfo = (CalcTableVSAssemblyInfo) assembly.getInfo();
      CalcTableVSAssemblyInfo ninfo = oinfo.clone(false);
      int row = event.row();
      int col = event.col();
      boolean isCalcAggr = isCalcAggrgate(ninfo.getTableName(),
                                          event.columnValue(), rvs.getViewsheet());
      TableCellBinding cellBinding =
         layoutHandler.createDefalutCellBinding(!isCalcAggr, event.columnValue());

      if(isCalcAggr) {
         cellBinding.setBType(TableCellBinding.SUMMARY);
      }

      layoutHandler.setCellBinding(ninfo, row, col, cellBinding);
      assemblyInfoHandler.apply(rvs, ninfo, viewsheetService, event.confirmed(),
                                event.checkTrap(), false, false, dispatcher,
                                "/events/vs/calctable/tablelayout/changeColumnValue",
                                event, linkUri, null);
      dispatcher.sendCommand(name, createCellBindingCommand(rvs, assembly, row, col));
      dispatcher.sendCommand(name, createTableLayoutCommand(rvs, assembly));
      assemblyInfoHandler.getGrayedOutFields(rvs, dispatcher);
      return null;
   }

   private boolean isCalcAggrgate(String table, String name, Viewsheet vs) {
      CalculateRef ref = vs == null ? null : vs.getCalcField(table, name);
      return ref != null && !ref.isBaseOnDetail();
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setCellBinding(@ClusterProxyKey String id, SetCellBindingEvent event,
                              Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      if(!quickCheckForCalc(id, event.getName(), principal)) {
         return null;
      }

      String name = event.getName();
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(id, principal);
      CalcTableVSAssembly assembly = getCalcTableVSAssembly(rvs, name);
      TableCell[] cells = event.getSelectCells();

      if(cells == null || cells.length == 0) {
         return null;
      }

      TableCell cell = cells[cells.length - 1];
      int row = cell.getRow();
      int col = cell.getCol();
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      CalcTableVSAssemblyInfo ninfo = (CalcTableVSAssemblyInfo) info.clone();
      TableLayout layout = ninfo.getTableLayout();
      AssetNamedGroupInfo[] infos = getPredefinedNamedGroup(rvs, name,
                                                            event.getBinding().getValue());
      setBinding(event.getBinding(),
                 getBindingFromLayout(layout, row, col), principal, infos);

      assemblyInfoHandler.apply(rvs, ninfo, engine, false, false, false,
                                false, false, dispatcher, null, null, null, null);

      dispatcher.sendCommand(name, createCellBindingCommand(rvs, assembly, row, col));
      dispatcher.sendCommand(name, createTableLayoutCommand(rvs, assembly));
      assemblyInfoHandler.getGrayedOutFields(rvs, dispatcher);
      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public CellBindingInfo checkTrapForCalc(@ClusterProxyKey String vsId,
                                                           String name,
                                                           int row, int col,
                                                           CellBindingInfo binding,
                                                           Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      CalcTableVSAssembly assembly = getCalcTableVSAssembly(rvs, name);
      CalcTableVSAssembly oassembly = (CalcTableVSAssembly) assembly.clone();
      CellBindingInfo oldCellBinding = getCellBindingInfo(oassembly, row, col);
      CalcTableVSAssemblyInfo oinfo = (CalcTableVSAssemblyInfo) oassembly.getInfo();
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      CalcTableVSAssemblyInfo ninfo = (CalcTableVSAssemblyInfo) info.clone();
      TableLayout layout = ninfo.getTableLayout();
      AssetNamedGroupInfo[] infos =
         getPredefinedNamedGroup(rvs, name, binding.getValue());
      setBinding(binding,
                 getBindingFromLayout(layout, row, col), principal, infos);
      assembly.setVSAssemblyInfo(ninfo);
      VSModelTrapContext context = new VSModelTrapContext(rvs);
      boolean containsTrap = context.isCheckTrap() &&
         context.checkTrap(oinfo, ninfo).showWarning();
      assembly.setVSAssemblyInfo(oinfo);

      return containsTrap ? oldCellBinding : null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void modifyLayout(@ClusterProxyKey String id, ModifyTableLayoutEvent event,
                            Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      if(!quickCheckForCalc(id, event.getName(), principal)) {
         return null;
      }

      String name = event.getName();
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(id, principal);
      CalcTableVSAssembly assembly = getCalcTableVSAssembly(rvs, name);
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      CalcTableVSAssemblyInfo ninfo = (CalcTableVSAssemblyInfo) info.clone();
      layoutHandler.doOperation(ninfo, event.getOp(), event.getSelection(), event.getNum());
      assemblyInfoHandler.apply(rvs, ninfo, engine, false, false, false, false, dispatcher);
      dispatcher.sendCommand(name, createTableLayoutCommand(rvs, assembly));
      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void copyCut(@ClusterProxyKey String id, CopyCutCalcCellEvent event,
                       Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      if(!quickCheckForCalc(id, event.getName(), principal)) {
         return null;
      }

      String name = event.getName();
      Rectangle[] selections = event.getSelections();

      if(selections[0] == null || selections[1] == null) {
         return null;
      }

      int row = (int) selections[0].getY();
      int col = (int) selections[1].getX();
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(id, principal);
      CalcTableVSAssembly assembly = getCalcTableVSAssembly(rvs, name);
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      CalcTableVSAssemblyInfo ninfo = (CalcTableVSAssemblyInfo) info.clone();
      layoutHandler.copyOperation(ninfo, event.getOp(), selections[0], selections[1]);
      assemblyInfoHandler.apply(rvs, ninfo, engine, false, false, false, false, dispatcher);
      dispatcher.sendCommand(name, createCellBindingCommand(rvs, assembly, row, col));
      dispatcher.sendCommand(name, createTableLayoutCommand(rvs, assembly));
      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void getCellScript(@ClusterProxyKey String id, GetCellScriptEvent event,
                             Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      if(!quickCheckForCalc(id, event.getName(), principal)) {
         return null;
      }

      String name = event.getName();
      int row = event.getRow();
      int col = event.getCol();
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(id, principal);
      CalcTableVSAssembly assembly = getCalcTableVSAssembly(rvs, name);
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      String script = cellScriptHandler.get(rvs, info, row, col);
      dispatcher.sendCommand(name, new GetCellScriptCommand(script));
      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void getNamedGroup(@ClusterProxyKey String id, GetPredefinedNamedGroupEvent event,
                             Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      if(!quickCheckForCalc(id, event.getName(), principal)) {
         return null;
      }

      String name = event.getName();
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(id, principal);
      AssetNamedGroupInfo[] infos = getPredefinedNamedGroup(rvs, name, event.getColumn());

      if(infos == null) {
         return null;
      }

      dispatcher.sendCommand(name, new GetPredefinedNamedGroupCommand(infos));
      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void resizeCalcTableCell(@ClusterProxyKey String id, ResizeCalcTableCellEvent event, Principal principal,
                                   CommandDispatcher dispatcher) throws Exception
   {
      int row = event.getRow();
      int col = event.getCol();
      String tableName = event.getName();

      if(row < 0 || col < 0 || tableName == null || Double.isNaN(event.getWidth())
         || Double.isNaN(event.getHeight())) {
         return null;
      }

      int width = (int) event.getWidth();
      int height = (int) event.getHeight();
      String op = event.getOp();
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(id, principal);
      Viewsheet vs = rvs.getViewsheet();
      CalcTableVSAssembly table = (CalcTableVSAssembly) vs.getAssembly(tableName);
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) table.getInfo();

      switch(op) {
      case "ResizeColumn":
         info.getTableLayout().setColWidth(col, width);
         break;
      case "ResizeRow":
         info.getTableLayout().setRowHeight(row, height);
         break;
      }

      dispatcher.sendCommand(tableName, createTableLayoutCommand(rvs, table));
      return null;
   }

   public GetCellBindingCommand createCellBindingCommand(RuntimeViewsheet rvs,
                                                         CalcTableVSAssembly assembly, int row, int col)
   {
      Viewsheet vs = rvs.getViewsheet();
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      ColumnSelection selection = VSUtil.getColumnsForVSAssembly(vs, assembly, false);
      selection = VSUtil.sortColumns(selection);

      TableLayout layout = info.getTableLayout();
      String[] names = layout.getCellNames(true);
      TableCellBinding bind = getBindingFromLayout(layout, row, col);
      CellBindingInfo cinfo = createCellBinding(bind);
      String cellName = layout.getRuntimeCellName(bind);
      cinfo.setRuntimeName(cellName);
      OrderInfo cellOrderInfo = bind.getOrderInfo(true);

      if(cellOrderInfo.isSpecific()) {
         final OrderModel order = cinfo.getOrder();
         order.setType(OrderInfo.SORT_SPECIFIC);
         order.setManualOrder(cellOrderInfo.getManualOrder());
      }

      GetCellBindingCommand cmd = new GetCellBindingCommand(cinfo);
      cmd.setCellNames(names);
      cmd.setCellRow(row);
      cmd.setCellCol(col);
      cmd.setAggregates(getAggregates(assembly, selection));
      cmd.setRowGroup(rowColGroupHandler.hasRowGroup(rvs, info, row, col));
      cmd.setColGroup(rowColGroupHandler.hasColGroup(rvs, info, row, col));
      cmd.setGroupNum(LayoutTool.getTableCellBindings(layout, TableCellBinding.GROUP).size());
      return cmd;
   }

   public CellBindingInfo getCellBindingInfo(CalcTableVSAssembly assembly, int row, int col) {
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      TableLayout layout = info.getTableLayout();
      String[] names = layout.getCellNames(true);
      TableCellBinding bind = getBindingFromLayout(layout, row, col);

      return createCellBinding(bind);
   }

   public GetTableLayoutCommand createTableLayoutCommand(RuntimeViewsheet rvs,
                                                         CalcTableVSAssembly assembly)
      throws Exception
   {
      TableLens lens = assembly.getBaseTable();

      if(lens == null) {
         ViewsheetSandbox box = rvs.getViewsheetSandbox();
         lens = box.getVSTableLens(assembly.getAbsoluteName(), false);
      }

      if(lens == null) {
         return null;
      }

      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      FormatInfo formatInfo = assembly.getFormatInfo();
      CalcTableLayout model = new CalcTableLayout(info, formatInfo, lens);
      GetTableLayoutCommand command = new GetTableLayoutCommand(model);
      return command;
   }

   private boolean quickCheckForCalc(String id, String name, Principal user) throws Exception {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(id, user);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = viewsheet.getAssembly(name);

      if(!(assembly instanceof CalcTableVSAssembly)) {
         return false;
      }

      return true;
   }

   private CalcTableVSAssembly getCalcTableVSAssembly(RuntimeViewsheet rvs, String name) {
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = viewsheet.getAssembly(name);
      return (CalcTableVSAssembly) assembly;
   }

   private TableCellBinding getBindingFromLayout(TableLayout layout, int row, int col) {
      TableCellBinding bind = (TableCellBinding) layout.getCellBinding(row, col);

      if(bind == null) {
         bind = new TableCellBinding();
         layout.setCellBinding(row, col, bind);
      }

      return bind;
   }

   private void setBinding(CellBindingInfo cinfo, TableCellBinding bind,
                           Principal principal, AssetNamedGroupInfo[] infos) throws Exception
   {
      bind.setType(cinfo.getType());
      bind.setBType(cinfo.getBtype());
      bind.setCellName(cinfo.getName());
      bind.setExpansion(cinfo.getExpansion());
      bind.setMergeCells(cinfo.getMergeCells());
      bind.setMergeRowGroup(cinfo.getMergeRowGroup());
      bind.setMergeColGroup(cinfo.getMergeColGroup());
      bind.setRowGroup(cinfo.getRowGroup());
      bind.setColGroup(cinfo.getColGroup());
      bind.setValue(cinfo.getValue());
      bind.setFormula(cinfo.getFormula());
      bind.setTimeSeries(cinfo.isTimeSeries());
      setOrderInfo(cinfo.getOrder(), bind.getOrderInfo(true), principal, infos);
      setTopNInfo(cinfo.getTopn(), bind.getTopN(true));
   }

   private void setOrderInfo(OrderModel order, OrderInfo info, Principal principal,
                             AssetNamedGroupInfo[] infos) throws Exception
   {
      info.setOrder(order.getType());
      info.setSortByCol(order.getSortCol());
      info.setInterval(order.getInterval(), order.getOption());
      info.setOthers(order.isOthers() ? OrderInfo.GROUP_OTHERS : OrderInfo.LEAVE_OTHERS);
      info.setManualOrder(order.getManualOrder());
      setNamedGroupInfo(order, info, principal, infos);
   }

   private AssetNamedGroupInfo[] getPredefinedNamedGroup(RuntimeViewsheet rvs,
                                                         String name, String colName) throws Exception
   {
      CalcTableVSAssembly assembly = getCalcTableVSAssembly(rvs, name);
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      SourceInfo sinfo = info.getSourceInfo();

      if(info == null || sinfo == null) {
         return null;
      }

      ColumnSelection cols = columnsHandler.getColumnSelection(rvs, name,
                                                               sinfo.getSource(), null, false, true, false, false, false, false);
      AssetRepository rep = AssetUtil.getAssetRepository(false);

      if(rep == null) {
         return null;
      }

      DataRef fld = cols.getAttribute(colName);

      if(fld == null) {
         return null;
      }

      AssetNamedGroupInfo[] infos =
         SummaryAttr.getAssetNamedGroupInfos(fld, rep, null);
      ArrayList<AssetNamedGroupInfo> namedGroups = new ArrayList<>();

      for(int i = 0; i < infos.length; i++) {
         AssetNamedGroupInfo groupInfo = infos[i];

         if(ReportLayoutTool.isSimpleNamedGroup(groupInfo)) {
            namedGroups.add(groupInfo);
         }
      }

      return namedGroups.toArray(new AssetNamedGroupInfo[namedGroups.size()]);
   }

   private void setNamedGroupInfo(OrderModel order, OrderInfo info, Principal principal,
                                  AssetNamedGroupInfo[] infos) throws Exception
   {
      NamedGroupInfoModel ng = order.getInfo();

      if(ng == null) {
         info.setNamedGroupInfo(null);
      }
      else if(infos != null && ng.getType() == XNamedGroupInfo.ASSET_NAMEDGROUP_INFO) {
         String name = ng.getName();

         for(int i = 0; i < infos.length; i++) {
            if(name.equals(infos[i].getName())) {
               info.setNamedGroupInfo(infos[i]);
               return;
            }
         }
      }
      else if(ng.getType() == XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO) {
         List<ConditionExpression> conds = ng.getConditions();

         if(conds == null || conds.size() == 0) {
            info.setNamedGroupInfo(null);
            return;
         }

         ExpertNamedGroupInfo nng = new ExpertNamedGroupInfo();

         for(int i = 0; i < conds.size(); i++) {
            ConditionExpression ex = conds.get(i);
            String gname = ex.getName();
            ConditionList list = ex.extractConditionList(null, viewsheetService, principal);
            nng.setGroupCondition(gname, list);
         }

         info.setNamedGroupInfo(nng);
      }
   }

   private void setTopNInfo(TopNModel top, TopNInfo info) {
      if(top.getType() == 0) {
         info.setTopN(-1);
      }
      else {
         info.setTopN(top.getTopn());
         info.setTopNReverse(top.getType() == 10);
         info.setTopNSummaryCol(top.getSumCol());
         info.setOthers(top.getOthers());
      }
   }

   private AggregateRefModel[] getAggregates(CalcTableVSAssembly calc,
                                             ColumnSelection selection)
   {
      CalcAggregate[] aggs = layoutHandler.getCalcAggregateFields(calc, selection);
      AggregateRefModel[] aggregates = new AggregateRefModel[aggs.length];

      for(int i = 0; i < aggs.length; i++) {
         AggregateRef agg = (AggregateRef) aggs[i];
         AggregateRefModel aggregate =
            (AggregateRefModel) refModelService.createDataRefModel(agg);
         aggregates[i] = aggregate;
      }

      return aggregates;
   }

   // Process expert named group info.
   private CellBindingInfo createCellBinding(TableCellBinding bind) {
      OrderInfo info = bind.getOrderInfo(true);
      XNamedGroupInfo ong = info.getNamedGroupInfo();
      CellBindingInfo cinfo = new CellBindingInfo(bind);
      OrderModel omodel = cinfo.getOrder();
      NamedGroupInfoModel nng = omodel.getInfo();

      if(ong instanceof ExpertNamedGroupInfo) {
         ExpertNamedGroupInfo eng = (ExpertNamedGroupInfo) ong;
         String[] groups = eng.getGroups();

         for(int i = 0; i < groups.length; i++) {
            ConditionList conds = eng.getGroupCondition(groups[i]);
            ConditionExpression ex = new ConditionExpression();
            ex.populateConditionListModel(conds, refModelService);
            ex.setName(groups[i]);
            nng.addCondition(ex);
         }

         nng.setName("Custom");
      }

      return cinfo;
   }

   private final VSRowColGroupHandler rowColGroupHandler;
   private final DataRefModelFactoryService refModelService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final VSCalcCellScriptHandler cellScriptHandler;
   private final TableLayoutHandler layoutHandler;
   private final VSColumnHandler columnsHandler;
   private final ViewsheetService viewsheetService;
   private static final Logger LOG = LoggerFactory.getLogger(VSTableLayoutService.class);
}
