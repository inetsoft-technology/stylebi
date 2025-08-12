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
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.viewsheet.event.chart.VSChartEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;

@Service
@ClusterProxy
public class VSChartMaxModeService extends VSChartControllerService<VSChartEvent> {
   public VSChartMaxModeService(CoreLifecycleService coreLifecycleService,
                              ViewsheetService viewsheetService,
                              VSChartAreasServiceProxy vsChartAreasService)
   {
      super(coreLifecycleService, viewsheetService, vsChartAreasService);
   }

   @Override
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void eventHandler(@ClusterProxyKey String runtimeId,
                            VSChartEvent event,
                            String linkUri,
                            Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      processEvent(runtimeId, event, principal, linkUri, dispatcher,
                   chartState -> toggleMaxMode(chartState, event.getMaxSize()));

      return null;
   }

   /**
    * Sets max mode enabled or disabled on this chart
    *
    * @param maxSize The size of the chart in max mode or null
    *
    * @return the hint to reset view if this value is changed
    */
   private int toggleMaxMode(VSChartStateInfo chartState, Dimension maxSize) {
      final ChartVSAssemblyInfo info = chartState.getChartAssemblyInfo();
      RuntimeViewsheet runtimeViewsheet = chartState.getRuntimeViewsheet();
      boolean isBinding = runtimeViewsheet.isBinding();
      info.setMaxSize(maxSize);
      chartState.getViewsheet().setMaxMode(maxSize != null);

      Viewsheet viewsheet = chartState.getViewsheet();
      int zAdjust = maxSize == null ? -100000 : 100000;
      boolean embeddedViewsheet = false;

      while(viewsheet.getViewsheet() != null) {
         // make sure embedded vs is on top when maximizing chart inside
         viewsheet.setZIndex(viewsheet.getZIndex() + zAdjust);
         viewsheet = viewsheet.getViewsheet();
         viewsheet.setMaxMode(maxSize != null);
         embeddedViewsheet = true;
      }

      if(maxSize != null) {
         final Assembly[] assemblies = viewsheet.getAssemblies(true, true);
         int parentZAdjust = embeddedViewsheet && !isBinding ? zAdjust : 0;

         if(assemblies != null) {
            final VSAssembly topAssembly = (VSAssembly) assemblies[assemblies.length - 1];
            final int zIndex = topAssembly.getVSAssemblyInfo().getZIndex() + 1 + parentZAdjust;
            info.setMaxModeZIndex(zIndex);
         }
      }

      return VSAssembly.VIEW_CHANGED;
   }

   @Override
   protected void complete(VSChartStateInfo chartState, int hint, String linkUri,
                           CommandDispatcher dispatcher, VSChartEvent event, Principal principal)
   {
      // visibility will be changed when toggling max-mode (48432).
      // @see VSObjectModel.visible and VSEventUtil.isVisible().

      boolean changeToMax = chartState.getViewsheet() != null &&
         chartState.getViewsheet().isMaxMode();
      reloadVSAssemblies(chartState.getRuntimeViewsheet(), event.getChartName(), linkUri,
                         dispatcher, principal, !changeToMax);
   }
}
