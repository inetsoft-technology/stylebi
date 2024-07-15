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
package inetsoft.web.viewsheet.controller.chart;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.chart.VSChartTitlesVisibilityEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class VSChartTitlesVisibilityController extends VSChartController<VSChartTitlesVisibilityEvent> {
   @Autowired
   public VSChartTitlesVisibilityController(RuntimeViewsheetRef runtimeViewsheetRef,
                                            PlaceholderService placeholderService,
                                            VSObjectPropertyService vsObjectPropertyService,
                                            ViewsheetService viewsheetService)
   {
      super(runtimeViewsheetRef, placeholderService, viewsheetService);
      this.vsObjectPropertyService = vsObjectPropertyService;
   }

   /**
    * Show all titles
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if the titles could not be shown.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/vschart/titles-visibility")
   public void eventHandler(@Payload VSChartTitlesVisibilityEvent event,
                            @LinkUri String linkUri, Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      this.processEvent(event, principal, chartState -> {
         if(event.isHide()) {
            if("chart-title-true".equals(event.getTitleType())) {
               showChartTitle(chartState, linkUri, principal, dispatcher);
            }
            else if("chart-title".equals(event.getTitleType())) {
               hideChartTitle(chartState, linkUri, principal, dispatcher);
            }
            else {
               hideTitle(event, chartState, linkUri, principal, dispatcher);
            }
         }
         else {
            showAllTitles(chartState, linkUri, principal, dispatcher);
         }
      });
   }

   private void hideTitle(VSChartTitlesVisibilityEvent event,
                          VSChartStateInfo chartState,
                          String linkUri,
                          Principal principal,
                          CommandDispatcher dispatcher)
   {
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) Tool.clone(chartState.getChartAssemblyInfo());
      boolean maxMode = info.getMaxSize() != null;
      String axisType = event.getTitleType();
      hideTitle(axisType, info, maxMode);

      if(maxMode && chartState.getRuntimeViewsheet() != null &&
         (chartState.getRuntimeViewsheet().isBinding() ||
            chartState.getRuntimeViewsheet().getEmbedAssemblyInfo() != null))
      {
         hideTitle(axisType, info, false);
      }

      try {
         vsObjectPropertyService.editObjectProperty(
            chartState.getRuntimeViewsheet(), info, info.getAbsoluteName(), info.getAbsoluteName(),
            linkUri, principal, dispatcher);
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   private void hideTitle(String axisType, ChartVSAssemblyInfo info, boolean maxMode) {
      ChartDescriptor chartDescriptor = info.getChartDescriptor();
      hideDescriptorTitle(chartDescriptor, axisType, maxMode);

      //also change the runtime values if they exist, since the values from the runtime
      //chart descriptor are usually used if it exists
      if(info.getRTChartDescriptor() != null) {
         hideDescriptorTitle(info.getRTChartDescriptor(), axisType, maxMode);
      }
   }

   private void hideDescriptorTitle(ChartDescriptor chartDescriptor, String axisType,
                                    boolean maxMode)
   {
      if(chartDescriptor == null) {
         return;
      }

      TitlesDescriptor titlesDescriptor = chartDescriptor.getTitlesDescriptor();
      TitleDescriptor titleDescriptor = null;

      if("x_title".equals(axisType)) {
         titleDescriptor = titlesDescriptor.getXTitleDescriptor();
      }
      else if("x2_title".equals(axisType)) {
         titleDescriptor = titlesDescriptor.getX2TitleDescriptor();
      }
      else if("y_title".equals(axisType)) {
         titleDescriptor = titlesDescriptor.getYTitleDescriptor();
      }
      else if("y2_title".equals(axisType)) {
         titleDescriptor = titlesDescriptor.getY2TitleDescriptor();
      }

      if(titleDescriptor == null) {
         return;
      }

      titleDescriptor.setMaxModeVisible(false);

      if(!maxMode) {
         titleDescriptor.setVisible(false);
      }
   }

   private void hideChartTitle(VSChartStateInfo chartState,
                               String linkUri,
                               Principal principal,
                               CommandDispatcher dispatcher)
   {
      ChartVSAssemblyInfo chartInfo = (ChartVSAssemblyInfo) Tool.clone(chartState.getChartAssemblyInfo());
      chartInfo.setTitleVisibleValue(false);

      try {
         vsObjectPropertyService.editObjectProperty(
            chartState.getRuntimeViewsheet(), chartInfo, chartInfo.getAbsoluteName(), chartInfo.getAbsoluteName(),
            linkUri, principal, dispatcher);
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   private void showAllTitles(VSChartStateInfo chartState,
                              String linkUri,
                              Principal principal,
                              CommandDispatcher dispatcher)
   {
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) Tool.clone(chartState.getChartAssemblyInfo());
      boolean maxMode = info.getMaxSize() != null;
      showTitles(info, maxMode);
      info.setTitleVisibleValue(true);

      if(maxMode && chartState.getRuntimeViewsheet() != null &&
         (chartState.getRuntimeViewsheet().isBinding() ||
            chartState.getRuntimeViewsheet().getEmbedAssemblyInfo() != null))
      {
         showTitles(info, false);
      }

      try {
         vsObjectPropertyService.editObjectProperty(
            chartState.getRuntimeViewsheet(), info, info.getAbsoluteName(), info.getAbsoluteName(),
            linkUri, principal, dispatcher);
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   private void showChartTitle(VSChartStateInfo chartState,
                               String linkUri,
                               Principal principal,
                               CommandDispatcher dispatcher)
   {
      ChartVSAssemblyInfo chartInfo = (ChartVSAssemblyInfo) Tool.clone(chartState.getChartAssemblyInfo());
      chartInfo.setTitleVisibleValue(true);

      try {
         vsObjectPropertyService.editObjectProperty(
            chartState.getRuntimeViewsheet(), chartInfo, chartInfo.getAbsoluteName(), chartInfo.getAbsoluteName(),
            linkUri, principal, dispatcher);
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   private void showTitles(ChartVSAssemblyInfo info, boolean maxMode) {
      ChartInfo chartInfo = info.getVSChartInfo();
      ChartDescriptor chartDescriptor = info.getChartDescriptor();
      showDescriptorTitles(chartDescriptor, maxMode);

      //also change the runtime values if they exist, since the values from the runtime
      //chart descriptor are usually used if it exists
      if(info.getRTChartDescriptor() != null) {
         showDescriptorTitles(info.getRTChartDescriptor(), maxMode);
      }

      if(chartInfo instanceof MergedVSChartInfo) {
         ChartRef[] fields = chartInfo.getBindingRefs(true);

         if(fields != null) {
            for(ChartRef field : fields) {
               if(field instanceof ChartAggregateRef) {
                  TitleDescriptor title = ((ChartAggregateRef) field).getTitleDescriptor();
                  title.setMaxModeVisible(true);

                  if(!maxMode) {
                     title.setVisible(true);
                  }
               }
            }
         }
      }
   }

   private void showDescriptorTitles(ChartDescriptor chartDescriptor, boolean maxMode) {
      TitlesDescriptor titlesDesc = chartDescriptor.getTitlesDescriptor();
      TitleDescriptor xtitle = titlesDesc.getXTitleDescriptor();
      TitleDescriptor x2title = titlesDesc.getX2TitleDescriptor();
      TitleDescriptor ytitle = titlesDesc.getYTitleDescriptor();
      TitleDescriptor y2title = titlesDesc.getY2TitleDescriptor();

      xtitle.setMaxModeVisible(true);
      x2title.setMaxModeVisible(true);
      ytitle.setMaxModeVisible(true);
      y2title.setMaxModeVisible(true);

      if(!maxMode) {
         xtitle.setVisible(true);
         x2title.setVisible(true);
         ytitle.setVisible(true);
         y2title.setVisible(true);
      }
   }

   private final VSObjectPropertyService vsObjectPropertyService;
}
