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
import inetsoft.graph.data.PairsDataSet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.event.chart.VSChartAxesVisibilityEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Arrays;
import java.util.stream.Stream;

@Service
@ClusterProxy
public class VSChartAxesVisibilityService extends VSChartControllerService<VSChartAxesVisibilityEvent> {
   public VSChartAxesVisibilityService(CoreLifecycleService coreLifecycleService,
                                          ViewsheetService viewsheetService,
                                          VSChartAreasServiceProxy vsChartAreasService,
                                          VSObjectPropertyService vsObjectPropertyService) {
      super(coreLifecycleService, viewsheetService, vsChartAreasService);
      this.vsObjectPropertyService = vsObjectPropertyService;
   }

   @Override
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void eventHandler(@ClusterProxyKey String runtimeId,
                            VSChartAxesVisibilityEvent event, 
                            String linkUri, Principal principal, 
                            CommandDispatcher dispatcher) throws Exception 
   {
      this.processEvent(runtimeId, event, principal, chartState -> {
         if(event.isHide()) {
            hideAxis(event, chartState, linkUri, principal, dispatcher);
         }
         else {
            showAllAxes(chartState, linkUri, principal, dispatcher);
         }
      });

      return null;
   }

   private void hideAxis(VSChartAxesVisibilityEvent event, VSChartStateInfo chartState,
                         String linkUri, Principal principal, CommandDispatcher dispatcher)
   {
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) Tool.clone(chartState.getChartAssemblyInfo());
      VSChartInfo chartInfo = info.getVSChartInfo();
      String columnName = event.getColumnName();
      ChartRef chartRef = null;

      if(chartState.getChartAssemblyInfo() != null) {
         chartRef = (ChartRef) info.getDCBIndingRef(columnName);
      }

      if(chartRef == null) {
         chartRef = getAxixRef(chartInfo, columnName);
      }

      if(chartRef == null && chartInfo.isPeriodPartRef(columnName) &&
         chartInfo.getPeriodField() != null)
      {
         ChartRef periodField = (ChartRef) chartInfo.getPeriodField().clone();

         if(periodField instanceof VSDimensionRef) {
            ((VSDimensionRef) periodField).setDates(null);
         }

         chartRef = chartInfo.getFieldByName(periodField.getFullName(), false);
      }

      AxisDescriptor axisDescriptor = null;
      boolean maxMode = info.getMaxSize() != null;

      // if the field is dynamically created, get the runtime field, which has the
      // AxisDescriptor set from the parent VSChartDimensionRef. (42152)
      if(chartRef == null) {
         ChartRef[][] fields2 = {chartInfo.getRTYFields(), chartInfo.getRTXFields()};

         String finalColumnName = columnName;
         chartRef = Arrays.stream(fields2)
            .flatMap(fields -> Stream.of(fields))
            .filter(ref -> ref.getFullName().equals(finalColumnName))
            .findAny()
            .orElse(null);
      }

      if("_Parallel_Label_".equals(columnName) && chartInfo instanceof RadarVSChartInfo) {
         axisDescriptor = ((RadarVSChartInfo) chartInfo).getLabelAxisDescriptor();
      }
      else if((PairsDataSet.YMEASURE_NAME.equals(columnName) ||
         PairsDataSet.XMEASURE_NAME.equals(columnName)) && !GraphTypeUtil.isScatterMatrix(chartInfo))
      {
         axisDescriptor = chartInfo.getAxisDescriptor();
      }
      else if(chartInfo.isSeparatedGraph() && !(chartInfo instanceof StockVSChartInfo) &&
         !(chartInfo instanceof CandleVSChartInfo))
      {
         if(chartRef != null) {
            axisDescriptor = chartRef.getAxisDescriptor();
         }
      }
      else {
         if(chartRef != null && !chartRef.isMeasure()) {
            axisDescriptor = chartRef.getAxisDescriptor();
         }
         else if(event.isSecondary()) {
            axisDescriptor = chartInfo.getAxisDescriptor2();
         }
         else {
            axisDescriptor = chartInfo.getAxisDescriptor();
         }
      }

      // mekko left axis uses chart axis descriptor. this logic is same as
      // SeparateGraphGenerator.getAxisDescriptor.
      if(axisDescriptor == null) {
         axisDescriptor = chartInfo.getAxisDescriptor();
      }

      setVisible(axisDescriptor, false, maxMode);

      if(maxMode && chartState.getRuntimeViewsheet() != null &&
         (chartState.getRuntimeViewsheet().isBinding() ||
            chartState.getRuntimeViewsheet().getEmbedAssemblyInfo() != null))
      {
         setVisible(axisDescriptor, false, false);
      }

      try {
         info.resetRuntimeValues();
         chartInfo.clearRuntime();
         vsObjectPropertyService.editObjectProperty(
            chartState.getRuntimeViewsheet(), info, info.getAbsoluteName(), info.getAbsoluteName(),
            linkUri, principal, dispatcher);
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   // find ref in axes.
   private static ChartRef getAxixRef(VSChartInfo info, String name) {
      // don't use getFieldByName, which my find other (e.g. source) dim before x/y.
      return Arrays.stream(info.getBindingRefs(false))
         .filter(f -> f.getFullName().equals(name))
         .findFirst().orElse(null);
   }

   private void showAllAxes(VSChartStateInfo chartState,
                            String linkUri,
                            Principal principal,
                            CommandDispatcher dispatcher)
   {
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo)
         Tool.clone(chartState.getChartAssemblyInfo());
      VSChartInfo chartInfo = info.getVSChartInfo();
      ChartDescriptor chartDescriptor = info.getChartDescriptor();

      if(chartInfo == null || chartDescriptor == null) {
         return;
      }

      boolean maxMode = info.getMaxSize() != null;
      showAllAxes(chartInfo, maxMode);

      if(maxMode && chartState.getRuntimeViewsheet() != null &&
         (chartState.getRuntimeViewsheet().isBinding() ||
            chartState.getRuntimeViewsheet().getEmbedAssemblyInfo() != null))
      {
         showAllAxes(chartInfo, false);
      }

      try {
         info.resetRuntimeValues();
         vsObjectPropertyService.editObjectProperty(
            chartState.getRuntimeViewsheet(), info, info.getAbsoluteName(), info.getAbsoluteName(),
            linkUri, principal, dispatcher);
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   private void showAllAxes(VSChartInfo chartInfo, boolean maxMode) {
      ChartRef[][] fields = {chartInfo.getBindingRefs(false), chartInfo.getBindingRefs(true)};

      for(ChartRef[] arr : fields) {
         for(ChartRef field : arr) {
            if(field != null) {
               setVisible(field.getAxisDescriptor(), true, maxMode);
            }
         }
      }

      ChartRef[] rtDCRefs = chartInfo.getRuntimeDateComparisonRefs();

      for(ChartRef field : rtDCRefs) {
         if(field != null) {
            setVisible(field.getAxisDescriptor(), true, maxMode);
         }
      }

      setVisible(chartInfo.getAxisDescriptor(), true, maxMode);
      setVisible(chartInfo.getAxisDescriptor2(), true, maxMode);

      if(chartInfo instanceof RadarVSChartInfo) {
         setVisible(((RadarVSChartInfo) chartInfo).getLabelAxisDescriptor(), true, maxMode);
      }
   }

   private void setVisible(AxisDescriptor axis, boolean isVisible, boolean maxMode) {
      if(axis != null) {
         axis.setMaxModeLineVisible(isVisible);
         axis.setMaxModeLabelVisible(isVisible);

         if(!maxMode) {
            axis.setLineVisible(isVisible);
            axis.setLabelVisible(isVisible);
         }
      }
   }

   private final VSObjectPropertyService vsObjectPropertyService;
}
