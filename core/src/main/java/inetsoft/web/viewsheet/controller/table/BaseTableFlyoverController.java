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
package inetsoft.web.viewsheet.controller.table;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.ShowDetailEvent;
import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.VSAQuery;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TipVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.event.table.FlyoverEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Map;

/**
 * Flyovers for tables, very similar to chart flyover, should probably be refactored
 * at some point
 */
@Controller
public class BaseTableFlyoverController extends BaseTableController<FlyoverEvent> {
   @Autowired
   public BaseTableFlyoverController(RuntimeViewsheetRef runtimeViewsheetRef,
                                     PlaceholderService placeholderService,
                                     ViewsheetService viewsheetService)
   {
      super(runtimeViewsheetRef, placeholderService, viewsheetService);
   }

   @Override
   @LoadingMask
   @MessageMapping("/table/flyover")
   public void eventHandler(@Payload FlyoverEvent event, Principal principal,
                            CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      // get event properties
      String name = event.getAssemblyName();
      Map<Integer, int[]> selectedCells = event.getSelectedCells();

      // get runtime viewsheet and assembly
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         runtimeViewsheetRef.getRuntimeId(), principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet viewsheet = box.getViewsheet();

      box.lockRead();

      try {
         VSAssembly comp = viewsheet.getAssembly(name);
         ConditionList clist = null;

         // If row or col (or both) are less than 0 then use an
         // empty condition list to clear the flyover
         // If map is empty then clear
         if(!selectedCells.isEmpty()) {
            if(comp instanceof TableVSAssembly) {
               clist = this.processTable((TableVSAssembly) comp, box, name, selectedCells);
            }
            else if(comp instanceof CrosstabVSAssembly) {
               clist = this.processCrosstab(box, (CrosstabVSAssembly) comp, name, selectedCells);
            }
            else if(comp instanceof CalcTableVSAssembly) {
               clist = createCalcTableConditions(
                  (CalcTableVSAssembly) comp, box, name, selectedCells, null, dispatcher);
            }
         }
         else {
            clist = new ConditionList();
         }

         applyFlyovers(name, comp, rvs, box.getWorksheet(), clist, linkUri, dispatcher);
      }
      finally {
         box.unlockRead();
      }
   }

   /**
    * Get condition list for VSTable
    */
   private ConditionList processTable(TableVSAssembly comp,
                                      ViewsheetSandbox box,
                                      String name,
                                      Map<Integer, int[]> selectedCells) throws Exception
   {
      TableLens lens = (TableLens) box.getData(name);
      ConditionList clist = new ConditionList();

      for(Map.Entry<Integer, int[]> entry : selectedCells.entrySet()) {
         int row = entry.getKey();
         int[] cols = entry.getValue();

         for(int col : cols) {
            clist = ShowDetailEvent.createTableConditions(comp, clist, row, col, lens, true);
         }
      }

      return clist;
   }

   /**
    * Get condition list for VSCrosstab
    */
   private ConditionList processCrosstab(ViewsheetSandbox box,
                                         CrosstabVSAssembly comp,
                                         String name,
                                         Map<Integer, int[]> selectedCells) throws Exception
   {
      if(box == null) {
         return new ConditionList();
      }

      TableLens lens = (TableLens) box.getData(name);
      VSCrosstabInfo crosstabInfo = comp.getVSCrosstabInfo();
      DataRef[] rheaders = crosstabInfo.getRuntimeRowHeaders();
      DataRef[] cheaders = crosstabInfo.getRuntimeColHeaders();
      boolean period = crosstabInfo.getHeaderColCountWithPeriod() >
         crosstabInfo.getHeaderColCount();
      int offset = period ? 1 : 0;
      SourceInfo sinfo = comp.getSourceInfo();
      String cubeType = VSUtil.getCubeType(sinfo.getPrefix(), sinfo.getSource());
      boolean xmla = XCube.SQLSERVER.equals(cubeType) ||
         XCube.MONDRIAN.equals(cubeType);
      ConditionList rowConds = new ConditionList();
      ConditionList colConds = new ConditionList();

      for(Map.Entry<Integer, int[]> entry : selectedCells.entrySet()) {
         int row = entry.getKey();
         int[] cols = entry.getValue();

         for(int col : cols) {
            TableDataPath path = lens.getDescriptor().getCellDataPath(row, col);

            if(path == null || path.getType() != TableDataPath.SUMMARY &&
               path.getType() != TableDataPath.GRAND_TOTAL &&
               path.getType() != TableDataPath.GROUP_HEADER)
            {
               continue;
            }

            ShowDetailEvent.createCrosstabConditions(
               comp, colConds, cheaders, col, lens, true, cubeType, 0, path, xmla, 1);
            ShowDetailEvent.createCrosstabConditions(
               comp, rowConds, rheaders, row, lens, false, cubeType, offset, path, xmla, 1);
            rowConds.trim();
            colConds.trim();

            if(rowConds.getSize() > 0) {
               rowConds.append(new JunctionOperator(JunctionOperator.OR, 1));
            }

            if(colConds.getSize() > 0) {
               colConds.append(new JunctionOperator(JunctionOperator.OR, 1));
            }
         }
      }

      rowConds.trim();
      colConds.trim();

      if(rowConds.getSize() > 0) {
         rowConds.append(new JunctionOperator(JunctionOperator.AND, 0));
      }

      for(int i = 0; i < colConds.getSize(); i++) {
         rowConds.append(colConds.getItem(i));
      }

      rowConds = VSAQuery.replaceGroupValues(rowConds, comp, true);
      rowConds.trim();
      return rowConds;
   }

   /**
    * Execute the runtime viewsheet with the given condition list
    */
   private void applyFlyovers(String name, VSAssembly comp,
                              RuntimeViewsheet rvs, Worksheet ws,
                              ConditionList clist, String linkUri,
                              CommandDispatcher dispatcher) throws Exception
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(comp == null) {
         return;
      }

      AbstractTableAssembly tassembly = (AbstractTableAssembly)
         ws.getAssembly(comp.getTableName());
      TipVSAssemblyInfo minfo = (TipVSAssemblyInfo) comp.getVSAssemblyInfo();
      String[] views = minfo.getFlyoverViews();
      ConditionList preList = null;

      if(tassembly != null) {
         preList = (ConditionList) tassembly.getPreRuntimeConditionList();
      }

      ArrayList<Integer> hints = new ArrayList<>();

      if(views == null || views.length == 0) {
         return;
      }

      for(String view : views) {
         VSAssembly tip = comp.getViewsheet().getAssembly(view);

         // if the flyover component is not same source with current component,
         // just ignore the flyover component, instead of clear the flyover
         // component from current component, so if user change the flyover
         // source info, it may still working
         // ignore self
         if(tip == null || view.equals(name)) {
            continue;
         }

         int hint = applyCondition(rvs, comp, tip, clist);
         hints.add(hint);

         if(hint != VSAssembly.NONE_CHANGED) {
            // @by stephenwebster, For bug1433886201619
            // This fixes a unique case where flyover elements in a tabbed
            // assembly have their visibility affected by the tip conditions
            // First, make sure all the tip conditions are applied.
            // Second, make sure all scripts get executed on each flyover so
            // that when checking the visibility of an element in a tab the
            // correct tab gets selected based on the new conditions.
            // Third, though not ideal, execute and refresh the assemblies
            // separately in another loop.  This will ensure if the tip
            // assemblies are dependent on each other, their state will be
            // correct before one of the tip assemblies is executed.
            box.executeView(tip.getAbsoluteName(), true);
         }
      }

      // @by stephenwebster, For bug1433886201619
      // Execute elements in a separate loop.
      for(String view : views) {
         VSAssembly tip = comp.getViewsheet().getAssembly(view);

         if(tip == null || view.equals(name)) {
            continue;
         }

         int hint = hints.remove(0);

         if(hint != VSAssembly.NONE_CHANGED) {
            placeholderService.execute(rvs, tip.getAbsoluteName(), linkUri, hint, dispatcher);
            placeholderService.refreshVSAssembly(rvs, view, dispatcher);
         }
         // @by ankitmathur, For bug1432218253134, We need to clear the
         // Pre-Runtime Condition List of the base assembly shared between
         // each "tip" assembly. Because it is never cleared, the base assembly
         // keeps "merging" the condition's of the processed "tip" assemblies
         // and therefore starts incorrectly filtering the upcoming "tip"
         // assemblies.
         // UPDATE: 8-3-2015, For IssueId #409, Reset the Pre-Runtime Condition
         // List after the assembly has been processed. This will prevent
         // removing  any conditions which are inherited from Selection
         // components (or any other non fly-over assemblies).
         // UPDATE: 8-7-2015, Reset the Pre-Runtime Condition List to the
         // original value.
         if(tassembly != null) {
            tassembly.setPreRuntimeConditionList(preList);
         }
      }
   }

   /**
    * Apply the range condition on the worksheet.
    */
   private int applyCondition(RuntimeViewsheet rvs, VSAssembly comp,
                              VSAssembly tip, ConditionList conds) throws Exception
   {
      // Value is only used when chart is the host component
      Object clist = VSUtil.fixCondition(rvs, tip, conds, comp.getName(), null);

      if(Tool.equals(VSAssembly.NONE_CHANGED, clist)) {
         return VSAssembly.NONE_CHANGED;
      }

      tip.setTipConditionList(!(clist instanceof ConditionList) ? null : (ConditionList) clist);
      return VSAssembly.INPUT_DATA_CHANGED;
   }
}
