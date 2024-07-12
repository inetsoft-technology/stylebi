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
package inetsoft.web.binding;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.internal.binding.TopNInfo;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.erm.CalcAggregate;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.binding.drm.AggregateRefModel;
import inetsoft.web.binding.handler.*;
import inetsoft.web.binding.model.table.CellBindingInfo;
import inetsoft.web.binding.model.table.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;

/**
 * Controller that provides a REST endpoint for composer viewsheet actions.
 */
@RestController
@RequestMapping("/vscalc")
public class VSCalcTableBindingController {
   @RequestMapping(value = "/getcellbinding", method = RequestMethod.PUT)
   public Map getCellBinding(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      Principal principal, @RequestBody List<TableCell> cells)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalcTableVSAssembly assembly =
         (CalcTableVSAssembly) viewsheet.getAssembly(assemblyName);
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      TableLayout layout = ((CalcTableVSAssembly) assembly).getTableLayout();
      Map map = new HashMap();
      getAggregates(assembly);

      if(cells != null && cells.size() > 0) {
         TableCell cell = cells.get(cells.size() - 1);
         int row = cell.getRow();
         int col = cell.getCol();
         TableCellBinding bind = (TableCellBinding) layout.getCellBinding(row, col);
         CellBindingInfo cinfo = null;
         String cellName = null;
         String[] names = layout.getCellNames(true);

         if(bind != null) {
            cinfo = new CellBindingInfo(bind);
            cellName = layout.getRuntimeCellName(bind);
         }
         else {
            cinfo = new CellBindingInfo();
         }

         map.put("binding", cinfo);
         map.put("cellname", cellName);
         map.put("cellnames", names);
         map.put("aggregates", getAggregates(assembly));
         map.put("hasRowGroup", rowColGroupHandler.hasRowGroup(rvs, info, row, col));
         map.put("hasColGroup", rowColGroupHandler.hasColGroup(rvs, info, row, col));
         map.put("groupNum", LayoutTool.getTableCellBindings(layout,
            TableCellBinding.GROUP).size());
      }

      return map;
   }

   @RequestMapping(value = "/setcellbinding", method = RequestMethod.PUT)
   public Map setCellBinding(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("row") String row, @RequestParam("col") String col,
      @RequestBody CellBindingInfo cinfo, Principal principal)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalcTableVSAssembly assembly =
         (CalcTableVSAssembly) viewsheet.getAssembly(assemblyName);
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      CalcTableVSAssemblyInfo ninfo = (CalcTableVSAssemblyInfo) info.clone();
      TableLayout layout = ninfo.getTableLayout();
      int r = Integer.parseInt(row);
      int c = Integer.parseInt(col);
      TableCellBinding bind = (TableCellBinding) layout.getCellBinding(r, c);

      if(bind == null) {
         bind = new TableCellBinding();
         layout.setCellBinding(r, c, bind);
      }

      setBinding(cinfo, bind);
      assemblyInfoHandler.apply(rvs, ninfo, engine);

      CalcTableVSAssemblyInfo newInfo = (CalcTableVSAssemblyInfo) assembly.getInfo();
      TableLayout nlayout = newInfo.getTableLayout();
      String[] names = nlayout.getCellNames(true);
      String cellName = nlayout.getRuntimeCellName(bind);
      TableCellBinding nbind = (TableCellBinding) nlayout.getCellBinding(r, c);
      Map map = new HashMap();
      map.put("binding", new CellBindingInfo(nbind));
      map.put("cellname", cellName);
      map.put("cellnames", names);
      map.put("aggregates", getAggregates(assembly));
      map.put("hasRowGroup", rowColGroupHandler.hasRowGroup(rvs, newInfo, r, c));
      map.put("hasColGroup", rowColGroupHandler.hasColGroup(rvs, newInfo, r, c));
      map.put("groupNum", LayoutTool.getTableCellBindings(nlayout,
         TableCellBinding.GROUP).size());

      return map;
   }

   @RequestMapping(value = "/operation", method = RequestMethod.PUT)
   public Map cellOperation(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("op") String op,
      @RequestParam(value="num", required=false) String num,
      @RequestBody Rectangle selection, Principal principal) throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalcTableVSAssembly assembly =
         (CalcTableVSAssembly) viewsheet.getAssembly(assemblyName);
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      CalcTableVSAssemblyInfo ninfo = (CalcTableVSAssemblyInfo) info.clone();
      TableLayout layout = ninfo.getTableLayout();
      int r = (int)selection.getX();
      int c = (int)selection.getY();
      int n = num == null ? 0 : Integer.parseInt(num);
      layoutHandler.doOperation(ninfo, op, selection, n);
      assemblyInfoHandler.apply(rvs, ninfo, engine);
      CalcTableVSAssemblyInfo newInfo = (CalcTableVSAssemblyInfo) assembly.getInfo();
      TableLayout nlayout = newInfo.getTableLayout();
      String[] names = nlayout.getCellNames(true);
      TableCellBinding nbind = (TableCellBinding) nlayout.getCellBinding(r, c);

      if(nbind == null) {
         nbind = new TableCellBinding();
      }

      String cellName = nlayout.getRuntimeCellName(nbind);
      Map map = new HashMap();
      map.put("binding", new CellBindingInfo(nbind));
      map.put("cellname", cellName);
      map.put("cellnames", names);
      map.put("aggregates", getAggregates(assembly));
      map.put("hasRowGroup", rowColGroupHandler.hasRowGroup(rvs, newInfo, r, c));
      map.put("hasColGroup", rowColGroupHandler.hasColGroup(rvs, newInfo, r, c));
      map.put("groupNum", LayoutTool.getTableCellBindings(nlayout,
         TableCellBinding.GROUP).size());

      return map;
   }

   @RequestMapping(value = "/copy", method = RequestMethod.PUT)
   public Map copyOperation(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("op") String op, @RequestBody List<Rectangle> selections,
      Principal principal) throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalcTableVSAssembly assembly =
         (CalcTableVSAssembly) viewsheet.getAssembly(assemblyName);
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      CalcTableVSAssemblyInfo ninfo = (CalcTableVSAssemblyInfo) info.clone();
      TableLayout layout = ninfo.getTableLayout();
      layoutHandler.copyOperation(ninfo, op, selections.get(0), selections.get(1));
      assemblyInfoHandler.apply(rvs, ninfo, engine);
      CalcTableVSAssemblyInfo newInfo = (CalcTableVSAssemblyInfo) assembly.getInfo();
      TableLayout nlayout = newInfo.getTableLayout();
      String[] names = nlayout.getCellNames(true);
      int r = (int)selections.get(0).getX();
      int c = (int)selections.get(1).getY();
      TableCellBinding nbind = (TableCellBinding) nlayout.getCellBinding(r, c);

      if(nbind == null) {
         nbind = new TableCellBinding();
      }

      String cellName = nlayout.getRuntimeCellName(nbind);
      Map map = new HashMap();
      map.put("binding", new CellBindingInfo(nbind));
      map.put("cellname", cellName);
      map.put("cellnames", names);
      map.put("aggregates", getAggregates(assembly));
      map.put("hasRowGroup", rowColGroupHandler.hasRowGroup(rvs, newInfo, r, c));
      map.put("hasColGroup", rowColGroupHandler.hasColGroup(rvs, newInfo, r, c));
      map.put("groupNum", LayoutTool.getTableCellBindings(nlayout,
         TableCellBinding.GROUP).size());

      return map;
   }

   private void setBinding(CellBindingInfo cinfo, TableCellBinding bind) {
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
      setOrderInfo(cinfo.getOrder(), bind.getOrderInfo(true));
      setTopNInfo(cinfo.getTopn(), bind.getTopN(true));
   }

   private void setOrderInfo(OrderModel order, OrderInfo info) {
      info.setOrder(order.getType());
      info.setSortByCol(order.getSortCol());
      info.setInterval(order.getInterval(), order.getOption());
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

   @RequestMapping(value = "/getcellscript", method = RequestMethod.GET)
   public String getCellScript(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("row") String row, @RequestParam("col") String col,
      Principal principal) throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalcTableVSAssembly assembly =
         (CalcTableVSAssembly) viewsheet.getAssembly(assemblyName);
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      int r = Integer.parseInt(row);
      int c = Integer.parseInt(col);
      return getCellScriptHandler.get(rvs, info, r, c);
   }

   private AggregateRefModel[] getAggregates(CalcTableVSAssembly calc) {
      CalcAggregate[] aggs = LayoutTool.getCalcAggregateFields(calc);
      AggregateRefModel[] aggregates = new AggregateRefModel[aggs.length];

      for(int i = 0; i < aggs.length; i++) {
         AggregateRef agg = (AggregateRef) aggs[i];
         AggregateRefModel aggregate = new AggregateRefModel(agg);
         aggregates[i] = aggregate;
      }

      return aggregates;
   }

   @Autowired
   public void setViewsheetService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @Autowired
   private VSAssemblyInfoHandler assemblyInfoHandler;
   @Autowired
   private VSCalcCellScriptHandler getCellScriptHandler;
   @Autowired
   private VSRowColGroupHandler rowColGroupHandler;
   @Autowired
   private TableLayoutHandler layoutHandler;
   private ViewsheetService viewsheetService;
}
