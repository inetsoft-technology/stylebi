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
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.event.chart.VSChartEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;

@Controller
public class VSChartMaxModeController extends VSChartController<VSChartEvent> {
   @Autowired
   public VSChartMaxModeController(RuntimeViewsheetRef runtimeViewsheetRef,
                                   PlaceholderService placeholderService,
                                   ViewsheetService viewsheetService)
   {
      super(runtimeViewsheetRef, placeholderService, viewsheetService);
   }

   /**
    * Generate a chart model for a max mode chart. If max mode is already open then
    * sets the assembly max size to null to close max mode.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    */
   @Override
   @LoadingMask
   @MessageMapping("/vschart/toggle-max-mode")
   public void eventHandler(@Payload VSChartEvent event, @LinkUri String linkUri,
                            Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      processEvent(event, principal, linkUri, dispatcher,
                   chartState -> toggleMaxMode(chartState, event.getMaxSize()));
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
      info.setMaxSize(maxSize);
      chartState.getViewsheet().setMaxMode(maxSize != null);

      Viewsheet viewsheet = chartState.getViewsheet();
      int zAdjust = maxSize == null ? -100000 : 100000;

      while(viewsheet.getViewsheet() != null) {
         // make sure embedded vs is on top when maximizing chart inside
         viewsheet.setZIndex(viewsheet.getZIndex() + zAdjust);
         viewsheet = viewsheet.getViewsheet();
         viewsheet.setMaxMode(maxSize != null);
      }

      if(maxSize != null) {
         final Assembly[] assemblies = viewsheet.getAssemblies(true, true);

         if(assemblies != null) {
            final VSAssembly topAssembly = (VSAssembly) assemblies[assemblies.length - 1];
            final int zIndex = topAssembly.getVSAssemblyInfo().getZIndex() + 1;
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

      if(chartState.getViewsheet() != null && chartState.getViewsheet().isMaxMode()) {
         super.complete(chartState, hint, linkUri, dispatcher, event, principal);
      }
      else {
         reloadVSAssemblies(chartState.getRuntimeViewsheet(), event.getChartName(), linkUri, dispatcher, principal);
      }
   }
}
