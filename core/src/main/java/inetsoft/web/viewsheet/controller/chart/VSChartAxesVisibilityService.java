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
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
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
                                          VSChartAreasServiceProxy vsChartAreasService) {
      super(coreLifecycleService, viewsheetService, vsChartAreasService);
   }

   @Override
   @ClusterWriteMethod
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void eventHandler(@ClusterProxyKey String runtimeId,
                            VSChartAxesVisibilityEvent event,
                            String linkUri, Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      this.processEvent(runtimeId, event, principal, linkUri, dispatcher, chartState -> {
         if(event.isHide()) {
            return hideAxis(event, chartState);
         }
         else {
            return showAllAxes(chartState);
         }
      });

      return null;
   }

   private int hideAxis(VSChartAxesVisibilityEvent event, VSChartStateInfo chartState)
   {
      ChartVSAssemblyInfo info = chartState.getChartAssemblyInfo();
      VSChartInfo chartInfo = chartState.getChartInfo();
      String columnName = event.getColumnName();
      ChartRef chartRef = findAxisRef(info, chartInfo, columnName);
      AxisDescriptor axisDescriptor = null;
      boolean maxMode = info.getMaxSize() != null;

      if(isRadarLabelAxis(chartInfo, columnName)) {
         axisDescriptor = ((RadarVSChartInfo) chartInfo).getLabelAxisDescriptor();
      }
      else if(isMeasureNameAxis(chartInfo, columnName)) {
         axisDescriptor = chartInfo.getAxisDescriptor();
      }
      else if(!usesMergedAxisBranch(chartInfo)) {
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
            chartState.getRuntimeViewsheet().hasEmbeddedAssembly()))
      {
         setVisible(axisDescriptor, false, false);
      }

      chartInfo.clearRuntime();
      return VSAssembly.VIEW_CHANGED;
   }

   /**
    * Finds the ref that a hide-axis column name addresses. This is the one lookup that
    * {@link #hideAxis} and the wiz tool layer's pre-check ({@code ChartAxisTargets}) share, so a
    * name the tool accepts is exactly a name this service resolves, and the two cannot drift.
    *
    * <p>The match is exact and case-sensitive on the ref's full name (e.g.
    * {@code Month(Order:Date)}), in this order: the date-comparison binding ref, the binding refs,
    * the period-part field, then the runtime x/y fields (dynamically created fields, 42152).
    *
    * @return the ref, or null when the name matches none. A null here is what makes
    *         {@code hideAxis} fall back to a chart-level axis descriptor.
    */
   public static ChartRef findAxisRef(ChartVSAssemblyInfo info, VSChartInfo chartInfo,
                                      String columnName)
   {
      ChartRef chartRef = (ChartRef) info.getDCBIndingRef(columnName);

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

      // if the field is dynamically created, get the runtime field, which has the
      // AxisDescriptor set from the parent VSChartDimensionRef. (42152)
      if(chartRef == null) {
         ChartRef[][] fields2 = {chartInfo.getRTYFields(), chartInfo.getRTXFields()};

         chartRef = Arrays.stream(fields2)
            .flatMap(fields -> Stream.of(fields))
            .filter(ref -> ref.getFullName().equals(columnName))
            .findAny()
            .orElse(null);
      }

      return chartRef;
   }

   /**
    * True for the special radar label axis name that {@link #hideAxis} resolves without a ref.
    */
   public static boolean isRadarLabelAxis(VSChartInfo chartInfo, String columnName) {
      return "_Parallel_Label_".equals(columnName) && chartInfo instanceof RadarVSChartInfo;
   }

   /**
    * True for the x/y measure-name pseudo columns, which {@link #hideAxis} resolves without a ref
    * except on a scatter matrix.
    */
   public static boolean isMeasureNameAxis(VSChartInfo chartInfo, String columnName) {
      return (PairsDataSet.YMEASURE_NAME.equals(columnName) ||
         PairsDataSet.XMEASURE_NAME.equals(columnName)) && !GraphTypeUtil.isScatterMatrix(chartInfo);
   }

   /**
    * True when {@link #hideAxis} picks the descriptor from the shared merged-layout branch, the
    * one where a measure ref's own axis is ignored and {@code secondary} alone chooses between
    * the primary and secondary descriptor.
    */
   public static boolean usesMergedAxisBranch(VSChartInfo chartInfo) {
      return !chartInfo.isSeparatedGraph() || chartInfo instanceof StockVSChartInfo ||
         chartInfo instanceof CandleVSChartInfo;
   }

   // find ref in axes.
   private static ChartRef getAxixRef(VSChartInfo info, String name) {
      // don't use getFieldByName, which my find other (e.g. source) dim before x/y.
      return Arrays.stream(info.getBindingRefs(false))
         .filter(f -> f.getFullName().equals(name))
         .findFirst().orElse(null);
   }

   private int showAllAxes(VSChartStateInfo chartState)
   {
      ChartVSAssemblyInfo info = chartState.getChartAssemblyInfo();
      VSChartInfo chartInfo = chartState.getChartInfo();
      ChartDescriptor chartDescriptor = info.getChartDescriptor();

      if(chartInfo == null || chartDescriptor == null) {
         return VSAssembly.VIEW_CHANGED;
      }

      boolean maxMode = info.getMaxSize() != null;
      showAllAxes(chartInfo, maxMode);

      if(maxMode && chartState.getRuntimeViewsheet() != null &&
         (chartState.getRuntimeViewsheet().isBinding() ||
            chartState.getRuntimeViewsheet().hasEmbeddedAssembly()))
      {
         showAllAxes(chartInfo, false);
      }

      chartInfo.clearRuntime();
      return VSAssembly.VIEW_CHANGED;
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

}
