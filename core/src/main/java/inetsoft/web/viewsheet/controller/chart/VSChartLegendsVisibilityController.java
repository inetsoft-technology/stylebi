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
package inetsoft.web.viewsheet.controller.chart;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.graph.GraphBuilder;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.chart.VSChartLegendsVisibilityEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class VSChartLegendsVisibilityController
   extends VSChartController<VSChartLegendsVisibilityEvent>
{
   @Autowired
   public VSChartLegendsVisibilityController(RuntimeViewsheetRef runtimeViewsheetRef,
                                             PlaceholderService placeholderService,
                                             VSObjectPropertyService vsObjectPropertyService,
                                             ViewsheetService viewsheetService,
                                             VSWizardTemporaryInfoService temporaryInfoService)
   {
      super(runtimeViewsheetRef, placeholderService, viewsheetService);
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.temporaryInfoService = temporaryInfoService;
   }

   /**
    * Show/hide chart legends
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if the axes could not be shown.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/vschart/legends-visibility")
   public void eventHandler(@Payload VSChartLegendsVisibilityEvent event,
                            @LinkUri String linkUri, Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      this.processEvent(event, principal, chartState -> {
         if(event.isHide()) {
            if(event.getAestheticType() == null) {
               showAllLegends(chartState, linkUri, principal, dispatcher, false);
            }
            else {
               hideLegend(event, chartState, linkUri, principal, dispatcher);
            }
         }
         else {
            showAllLegends(chartState, linkUri, principal, dispatcher, true);
         }

         if(event.isWizard()) {
            try {
               RuntimeViewsheet rtv = getViewsheetEngine().getViewsheet(getRuntimeId(), principal);
               VSTemporaryInfo temporaryInfo = temporaryInfoService.getVSTemporaryInfo(rtv);
               temporaryInfo.setShowLegend(!event.isHide());
            } catch (Exception ex) {
               throw new RuntimeException(ex);
            }
         }
      });
   }

   private void hideLegend(VSChartLegendsVisibilityEvent event,
                           VSChartStateInfo chartState, String linkUri,
                           Principal principal, CommandDispatcher dispatcher)
   {
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo)
         Tool.clone(chartState.getChartAssemblyInfo());
      RuntimeViewsheet rvs = chartState.getRuntimeViewsheet();
      boolean changeNonMaxMode = rvs != null &&
         (rvs.isBinding() || rvs.getEmbedAssemblyInfo() != null);

      hideDescriptorLegends(info, info.getChartDescriptor(), event, changeNonMaxMode);

      //also change the runtime values if they exist, since the values from the runtime
      //chart descriptor are usually used if it exists
      if(info.getRTChartDescriptor() != null) {
         hideDescriptorLegends(info, info.getRTChartDescriptor(), event, changeNonMaxMode);
      }

      try {
         vsObjectPropertyService.editObjectProperty(
            chartState.getRuntimeViewsheet(), info, info.getAbsoluteName(),
            info.getAbsoluteName(), linkUri, principal, dispatcher);
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   private void hideDescriptorLegends(ChartVSAssemblyInfo info,
                                      ChartDescriptor chartDescriptor,
                                      VSChartLegendsVisibilityEvent event,
                                      boolean changeNonMaxMode)
   {
      VSChartInfo chartInfo = info.getVSChartInfo();
      LegendsDescriptor legendsDescriptor = chartDescriptor.getLegendsDescriptor();
      LegendDescriptor legendDescriptor = GraphUtil.getLegendDescriptor(
         chartInfo, legendsDescriptor, event.getField(), event.getTargetFields(),
         event.getAestheticType(), event.isNodeAesthetic());
      boolean colorMerged = event.isColorMerged();
      boolean maxMode = info.getMaxSize() != null;

      if(info.getVSChartInfo() instanceof RelationChartInfo) {
         // if color legend exists, it is either the only legend, or merged with the other two
         if(colorMerged) {
            LegendDescriptor colorLegendDescriptor = GraphUtil.getLegendDescriptor(
               chartInfo, legendsDescriptor, event.getField(), event.getTargetFields(),
               ChartArea.COLOR_LEGEND, !event.isNodeAesthetic());
            hideDescriptorLegends(colorLegendDescriptor, maxMode);

            if(changeNonMaxMode && maxMode) {
               hideDescriptorLegends(colorLegendDescriptor, false);
            }
         }
      }

      LegendDescriptor colorLegendDescriptor = null;

      if(colorMerged) {
         colorLegendDescriptor = GraphUtil.getLegendDescriptor(
            chartInfo, legendsDescriptor, event.getField(), event.getTargetFields(),
            ChartArea.COLOR_LEGEND, event.isNodeAesthetic());
      }

      hideDescriptorLegends(legendDescriptor, maxMode);
      hideDescriptorLegends(colorLegendDescriptor, maxMode);

      if(changeNonMaxMode && maxMode) {
         hideDescriptorLegends(legendDescriptor, false);
         hideDescriptorLegends(colorLegendDescriptor, false);
      }
   }

   private void hideDescriptorLegends(LegendDescriptor legendDescriptor, boolean maxMode) {
      if(legendDescriptor != null) {
         legendDescriptor.setMaxModeVisible(false);

         if(!maxMode) {
            legendDescriptor.setVisible(false);
         }
      }
   }

   private void showAllLegends(VSChartStateInfo chartState, String linkUri, Principal principal,
                               CommandDispatcher dispatcher, boolean visible)
   {
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo)
         Tool.clone(chartState.getChartAssemblyInfo());
      boolean maxMode = info.getMaxSize() != null;
      ChartDescriptor chartDescriptor = info.getChartDescriptor();
      showAllLegends(info, visible, maxMode);

      if(maxMode && chartState.getRuntimeViewsheet() != null &&
         (chartState.getRuntimeViewsheet().isBinding() ||
            chartState.getRuntimeViewsheet().getEmbedAssemblyInfo() != null))
      {
         showAllLegends(info, visible, false);
      }

      LegendsDescriptor legendsDescriptor = chartDescriptor.getLegendsDescriptor();

      if(legendsDescriptor.getLayout() == LegendsDescriptor.NO_LEGEND) {
         legendsDescriptor.setLayout(LegendsDescriptor.RIGHT);
      }

      try {
         vsObjectPropertyService.editObjectProperty(
            chartState.getRuntimeViewsheet(), info, info.getAbsoluteName(),
            info.getAbsoluteName(), linkUri, principal, dispatcher);
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   private void showAllLegends(ChartVSAssemblyInfo info, boolean visible, boolean maxMode) {
      ChartInfo chartInfo = info.getVSChartInfo();
      ChartDescriptor chartDescriptor = info.getChartDescriptor();
      showAllDescriptorLegends(chartDescriptor, chartInfo, visible, maxMode);
      ChartDescriptor runtimeChartDescriptor = info.getRTChartDescriptor();

      // also change the runtime values if they exist, since the values from the runtime
      // chart descriptor are usually used if it exists
      if(runtimeChartDescriptor != null) {
         showAllDescriptorLegends(runtimeChartDescriptor, chartInfo, visible, maxMode);
      }
   }

   public static void showAllDescriptorLegends(ChartDescriptor chartDescriptor, ChartInfo chartInfo,
                                               boolean showLegend, boolean maxMode)
   {
      boolean donut = GraphTypes.isPie(chartInfo.getChartStyle()) && chartInfo.getYFieldCount() > 1
         && chartInfo.getYField(0) instanceof ChartAggregateRef;

      if(donut && showLegend && chartInfo.isMultiStyles()) {
         // make sure other legends are hidden
         showAllDescriptorLegends(chartDescriptor, chartInfo, false, maxMode);

         ChartAggregateRef aggr = (ChartAggregateRef) chartInfo.getYField(0);

         if(aggr.getColorField() != null) {
            aggr.getColorField().getLegendDescriptor().setMaxModeVisible(true);

            if(!maxMode) {
               aggr.getColorField().getLegendDescriptor().setVisible(true);
            }
         }

         if(aggr.getShapeField() != null) {
            aggr.getShapeField().getLegendDescriptor().setMaxModeVisible(true);

            if(!maxMode) {
               aggr.getShapeField().getLegendDescriptor().setVisible(true);
            }
         }

         if(aggr.getSizeField() != null) {
            aggr.getSizeField().getLegendDescriptor().setMaxModeVisible(true);

            if(!maxMode) {
               aggr.getSizeField().getLegendDescriptor().setVisible(true);
            }
         }
      }
      else {
         for(LegendDescriptor legend :
            GraphBuilder.getLegendDescriptors(chartInfo, chartDescriptor))
         {
            if(legend != null) {
               legend.setMaxModeVisible(showLegend);

               if(!maxMode) {
                  legend.setVisible(showLegend);
               }
            }
         }
      }
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSWizardTemporaryInfoService temporaryInfoService;
}
