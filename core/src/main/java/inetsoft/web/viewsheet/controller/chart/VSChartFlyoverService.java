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

package inetsoft.web.viewsheet.controller.chart;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.AbstractTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.event.chart.VSChartFlyoverEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

@Service
@ClusterProxy
public class VSChartFlyoverService extends VSChartControllerService<VSChartFlyoverEvent> {
   public VSChartFlyoverService(CoreLifecycleService coreLifecycleService,
                              ViewsheetService viewsheetService,
                              VSChartAreasServiceProxy vsChartAreasService)
   {
      super(coreLifecycleService, viewsheetService, vsChartAreasService);
   }

   @Override
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void eventHandler(@ClusterProxyKey String runtimeId,
                            VSChartFlyoverEvent event,
                            String linkUri,
                            Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {

      processEvent(runtimeId, event, principal, chartState -> {
         if(chartState == null) {
            return;
         }

         ViewsheetSandbox box = chartState.getViewsheetSandbox();
         VGraphPair pair = null;

         try {
            Dimension maxSize = chartState.getChartAssemblyInfo().getMaxSize();;
            pair = box.getVGraphPair(event.getChartName(), true, maxSize);

            if(pair != null && pair.isChangedByScript() ||
               chartState.getAssembly().containsBrushSelection() &&
                  !Tool.isEmptyString(event.getConditions()))
            {
               return;
            }

            processFlyover(chartState, linkUri, dispatcher, event.getChartName(),
                           event.getConditions());
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });

      return null;
   }

   /**
    * Process flyover views.
    */
   private void processFlyover(VSChartStateInfo chartState,
                               String linkUri,
                               CommandDispatcher dispatcher,
                               String name, String conds) throws Exception
   {
      final ViewsheetSandbox box = chartState.getViewsheetSandbox();
      ChartVSAssembly chartAssembly = chartState.getAssembly();
      ChartVSAssemblyInfo chartVSAssemblyInfo = chartState.getChartAssemblyInfo();
      String[] views = chartVSAssemblyInfo.getFlyoverViews();

      if(views == null || views.length == 0) {
         return;
      }

      Worksheet ws = box.getWorksheet();
      AbstractTableAssembly tassembly = (AbstractTableAssembly)
         ws.getAssembly(chartAssembly.getTableName());
      Viewsheet vs = chartState.getViewsheet();

      // A Table/Crosstab flyover (BaseTableFlyoverService) sharing the same Flyover View
      // target performs the same save/mutate/execute/restore sequence on the target's
      // condition state, serialized per-target via ViewsheetSandbox.getFlyoverLock(). Without
      // acquiring the same locks here, a chart flyover and a table/crosstab flyover pointed at
      // the same target can interleave that sequence and corrupt the shared condition state,
      // leaving the viewsheet stuck loading. Build the same lock name set (source table name +
      // target view names, sorted for deterministic acquisition order) and acquire them before
      // running the actual flyover logic. See BaseTableFlyoverService.applyFlyovers() for the
      // matching table-side implementation.
      TreeSet<String> lockNames = new TreeSet<>();

      if(tassembly != null) {
         lockNames.add(tassembly.getName());
      }

      for(String view : views) {
         VSAssembly tip = (VSAssembly) vs.getAssembly(view);

         if(tip != null && !view.equals(name)) {
            lockNames.add(tip.getAbsoluteName());
         }
      }

      List<Object> locks = new ArrayList<>();

      for(String lockName : lockNames) {
         locks.add(box.getFlyoverLock(lockName));
      }

      processFlyoverLocked(locks, 0, chartState, linkUri, dispatcher, name, conds, box,
                           chartAssembly, tassembly, views, vs);
   }

   /**
    * Recursively acquire the given per-target flyover locks (sorted into a deterministic order
    * by the caller to avoid deadlock against another request acquiring an overlapping lock
    * set), then run the actual flyover apply/execute/restore logic while holding all of them.
    * This is entered while holding no sandbox lock (see processFlyover()'s caller chain), so
    * unlike BaseTableFlyoverService.applyFlyoversLocked(), no unlockAll()/restoreLocks() dance
    * around lock acquisition is needed here.
    */
   private void processFlyoverLocked(List<Object> locks, int index, VSChartStateInfo chartState,
                                     String linkUri, CommandDispatcher dispatcher, String name,
                                     String conds, ViewsheetSandbox box,
                                     ChartVSAssembly chartAssembly,
                                     AbstractTableAssembly tassembly, String[] views, Viewsheet vs)
      throws Exception
   {
      if(index >= locks.size()) {
         doProcessFlyover(chartState, linkUri, dispatcher, name, conds, box, chartAssembly,
                          tassembly, views, vs);
         return;
      }

      synchronized(locks.get(index)) {
         processFlyoverLocked(locks, index + 1, chartState, linkUri, dispatcher, name, conds,
                              box, chartAssembly, tassembly, views, vs);
      }
   }

   private void doProcessFlyover(VSChartStateInfo chartState, String linkUri,
                                 CommandDispatcher dispatcher, String name, String conds,
                                 ViewsheetSandbox box, ChartVSAssembly chartAssembly,
                                 AbstractTableAssembly tassembly, String[] views, Viewsheet vs)
      throws Exception
   {
      ConditionList preList = null;

      if(tassembly != null) {
         preList = (ConditionList) tassembly.getPreRuntimeConditionList();
      }

      ArrayList<Integer> hints = new ArrayList<>();
      RuntimeViewsheet rvs = chartState.getRuntimeViewsheet();

      for(String view : views) {
         VSAssembly tip = (VSAssembly) vs.getAssembly(view);

         // if the flyover component is not same source with current component,
         // just ignore the flyover component, instead of clear the flyover
         // component from current component, so if user change the flyover
         // source info, it may still working
         // ignore self
         if(tip == null || view.equals(name)) {
            continue;
         }

         ConditionList clist = VSUtil.getConditionList(rvs, chartAssembly, conds);
         int hint = 0;

         box.lockRead();

         try {
            hint = applyCondition(rvs, chartAssembly, tip, clist, conds);
         }
         finally {
            box.unlockRead();
         }

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
         VSAssembly tip = (VSAssembly) vs.getAssembly(view);

         if(tip == null || view.equals(name)) {
            continue;
         }

         int hint = hints.remove(0);

         if(hint != VSAssembly.NONE_CHANGED) {
            // cancel any query still in flight for this tip assembly from a
            // prior, now-superseded hover event before starting the new one
            box.getQueryManager(tip.getAbsoluteName()).cancel();
            execute(rvs, tip.getAbsoluteName(), linkUri, hint, dispatcher);
            refreshVSAssembly(rvs, view, dispatcher);
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
   private int applyCondition(RuntimeViewsheet rvs, ChartVSAssembly chartAssembly,
                              VSAssembly tip, ConditionList conds,
                              String conditions) throws Exception
   {
      Object clist = VSUtil.fixCondition(rvs, tip, conds, chartAssembly.getAbsoluteName(),
                                         conditions);

      if(Tool.equals(VSAssembly.NONE_CHANGED, clist)) {
         return VSAssembly.NONE_CHANGED;
      }

      tip.setTipConditionList(!(clist instanceof ConditionList) ? null :
                                 (ConditionList) clist);
      return VSAssembly.INPUT_DATA_CHANGED;
   }
}
