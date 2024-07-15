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
package inetsoft.web.viewsheet.controller.chart;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.AbstractTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.event.chart.VSChartFlyoverEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;
import java.util.ArrayList;

@Controller
public class VSChartFlyoverController extends VSChartController<VSChartFlyoverEvent> {
   @Autowired
   public VSChartFlyoverController(RuntimeViewsheetRef runtimeViewsheetRef,
                                   PlaceholderService placeholderService,
                                   ViewsheetService viewsheetService)
   {
      super(runtimeViewsheetRef, placeholderService, viewsheetService);
   }


   /**
    * Apply flyover condition
    */
   @LoadingMask
   @MessageMapping("/vschart/flyover")
   public void eventHandler(@Payload VSChartFlyoverEvent event,
                            @LinkUri String linkUri,
                            Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      processEvent(event, principal, chartState -> {
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
      Worksheet ws = box.getWorksheet();
      AbstractTableAssembly tassembly = (AbstractTableAssembly)
         ws.getAssembly(chartAssembly.getTableName());
      ConditionList preList = null;

      if(tassembly != null) {
         preList = (ConditionList) tassembly.getPreRuntimeConditionList();
      }

      if(views == null || views.length == 0) {
         return;
      }

      ArrayList<Integer> hints = new ArrayList<>();
      Viewsheet vs = chartState.getViewsheet();
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
