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
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.graph.LegendDescriptor;
import inetsoft.uql.viewsheet.graph.LegendsDescriptor;
import inetsoft.web.viewsheet.event.chart.VSChartLegendRelocateEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.geom.Point2D;
import java.security.Principal;

@Service
@ClusterProxy
public class VSChartMoveLegendService extends VSChartControllerService<VSChartLegendRelocateEvent> {
   @Autowired
   public VSChartMoveLegendService(CoreLifecycleService coreLifecycleService,
                                   ViewsheetService viewsheetService,
                                   VSChartAreasServiceProxy vsChartAreasService)
   {
      super(coreLifecycleService, viewsheetService, vsChartAreasService);
   }

   @Override
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void eventHandler(@ClusterProxyKey String runtimeId,
                            VSChartLegendRelocateEvent event,
                            String linkUri,
                            Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {

      processEvent(runtimeId, event, principal, linkUri, dispatcher, chartState -> {
         return moveLegend(event, chartState);
      });

      return null;
   }

   private int moveLegend(VSChartLegendRelocateEvent event, VSChartStateInfo chartState) {
      final LegendsDescriptor legendsDes = chartState.getLegendsDes();
      final int layout = legendsDes.getLayout();
      final int newLayout = event.getLegendPosition();
      final int hint = layout != newLayout ? VSAssembly.VIEW_CHANGED : -1;
      legendsDes.setLayout(newLayout);
      chartState.getChartAssemblyInfo().setRTChartDescriptor(null);

      // only setPosition() if the legend is located in the "center"
      if(newLayout == LegendsDescriptor.IN_PLACE) {
         LegendDescriptor ldesc = GraphUtil.getLegendDescriptor(
            chartState.getChartInfo(), legendsDes, event.getField(),
            event.getTargetFields(), event.getLegendType(), event.isNodeAesthetic());

         if(ldesc != null) {
            Point2D.Double pos = new Point2D.Double(event.getLegendX(), event.getLegendY());
            ldesc.setPosition(pos);
         }
      }

      return hint;
   }
}
