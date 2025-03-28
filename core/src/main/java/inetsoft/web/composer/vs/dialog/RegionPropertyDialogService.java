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

package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.report.composition.region.TitleArea;
import inetsoft.uql.XCube;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.graph.model.dialog.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.security.Principal;

@Service
@ClusterProxy
public class RegionPropertyDialogService {

   public RegionPropertyDialogService(VSObjectPropertyService vsObjectPropertyService,
                                      ViewsheetService viewsheetService,
                                      VSBindingService vsBindingService,
                                      ChartRegionHandler regionHandler)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.viewsheetService = viewsheetService;
      this.vsBindingService = vsBindingService;
      this.regionHandler = regionHandler;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public AxisPropertyDialogModel getAxisPropertyDialogModel(@ClusterProxyKey String runtimeId,
                                                             String objectId, String axisType, String index,
                                                             String field, String linkUri,
                                                             Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      ChartVSAssembly chartAssembly;
      ChartVSAssemblyInfo chartAssemblyInfo;

      try {
         ViewsheetService engine = viewsheetService;
         rvs = engine.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         chartAssembly = (ChartVSAssembly) vs.getAssembly(objectId);
         chartAssemblyInfo = (ChartVSAssemblyInfo) chartAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      int axisIdx = Integer.parseInt(index);
      VSChartInfo cinfo = chartAssemblyInfo.getVSChartInfo();
      ChartDescriptor descriptor = chartAssemblyInfo.getChartDescriptor();
      PlotDescriptor plotDesc = descriptor.getPlotDescriptor();

      return regionHandler.createAxisPropertyDialogModel(
         cinfo, getChartArea(rvs, chartAssembly, linkUri, rvs.getViewsheet().isMaxMode()), axisType,
         axisIdx, field, plotDesc.isFacetGrid(), vs.isMaxMode());
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setAxisPropertyDialogModel(@ClusterProxyKey String runtimeId, String objectId, String axisType,
                                          int index, String field, AxisPropertyDialogModel value, String linkUri,
                                          Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      ChartVSAssembly chartAssembly;
      ChartVSAssemblyInfo chartAssemblyInfo;

      try {
         ViewsheetService engine = viewsheetService;
         rvs = engine.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         chartAssembly = (ChartVSAssembly) vs.getAssembly(objectId);
         chartAssemblyInfo = (ChartVSAssemblyInfo) chartAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      VSChartInfo cinfo = chartAssemblyInfo.getVSChartInfo();
      ChartArea chartArea = getChartArea(rvs, chartAssembly, linkUri);
      regionHandler.updateAxisPropertyDialogModel(value, cinfo, chartArea, axisType, index, field,
                                                  vs.isMaxMode());
      chartAssemblyInfo.resetRuntimeValues();
      cinfo.clearRuntime();
      rvs.getViewsheetSandbox().updateAssembly(chartAssembly.getAbsoluteName());
      rvs.getViewsheetSandbox().clearGraph(chartAssembly.getAbsoluteName());
      this.vsObjectPropertyService.editObjectProperty(
         rvs, chartAssemblyInfo, objectId, objectId, linkUri, principal, commandDispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public LegendFormatDialogModel getLegendFormatDialogModel(@ClusterProxyKey String runtimeId,
                                                             String objectId,  String index,
                                                             String linkUri, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      ChartVSAssembly chartAssembly;
      ChartVSAssemblyInfo chartAssemblyInfo;

      try {
         ViewsheetService engine = viewsheetService;
         rvs = engine.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         chartAssembly = (ChartVSAssembly) vs.getAssembly(objectId);
         chartAssemblyInfo = (ChartVSAssemblyInfo) chartAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      int legendIdx = Integer.parseInt(index);
      VSChartInfo cinfo = chartAssemblyInfo.getVSChartInfo();
      ChartArea chartArea = getChartArea(rvs, chartAssembly, linkUri);
      ChartDescriptor descriptor = chartAssemblyInfo.getChartDescriptor();
      return regionHandler.createLegendFormatDialogModel(cinfo, chartArea,
                                                         descriptor, legendIdx);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setLegendFormatDialogModel(@ClusterProxyKey String runtimeId, String objectId,
                                          int index, LegendFormatDialogModel value, String linkUri,
                                          Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      ChartVSAssembly chartAssembly;
      ChartVSAssemblyInfo chartAssemblyInfo;

      try {
         ViewsheetService engine = viewsheetService;
         rvs = engine.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         chartAssembly = (ChartVSAssembly) vs.getAssembly(objectId);
         chartAssemblyInfo = (ChartVSAssemblyInfo) chartAssembly.getVSAssemblyInfo();
         chartAssemblyInfo.setRTChartDescriptor(null);
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      VSChartInfo cinfo = chartAssemblyInfo.getVSChartInfo();
      ChartDescriptor descriptor = chartAssemblyInfo.getChartDescriptor();
      ChartArea chartArea = getChartArea(rvs, chartAssembly, linkUri);
      regionHandler.updateLegendFormatDialogModel(value, cinfo, chartArea, descriptor, index);

      this.vsObjectPropertyService.editObjectProperty(
         rvs, chartAssemblyInfo, objectId, objectId, linkUri, principal, commandDispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public TitleFormatDialogModel getTitleFormatDialogModel(@ClusterProxyKey String runtimeId,
                                                           String objectId, String axisType,
                                                           String linkUri, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      ChartVSAssembly chartAssembly;
      ChartVSAssemblyInfo chartAssemblyInfo;

      try {
         ViewsheetService engine = viewsheetService;
         rvs = engine.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         chartAssembly = (ChartVSAssembly) vs.getAssembly(objectId);
         chartAssemblyInfo = (ChartVSAssemblyInfo) chartAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         throw e;
      }

      ChartDescriptor descriptor = chartAssemblyInfo.getChartDescriptor();
      TitleDescriptor titleDesc = regionHandler.getTitleDescriptor(descriptor, axisType);
      ChartArea chartArea = getChartArea(rvs, chartAssembly, linkUri);
      TitleArea titleArea = regionHandler.getTitleArea(chartArea, axisType);
      String oldTitle = (String) titleArea.getChartAreaInfo().getProperty("titlename");

      return new TitleFormatDialogModel(titleDesc, oldTitle);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setTitleFormatDialogModel(@ClusterProxyKey String runtimeId, String objectId,
                                         String axisType, TitleFormatDialogModel value, String linkUri,
                                         Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      ChartVSAssembly chartAssembly;
      ChartVSAssemblyInfo chartAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         chartAssembly = (ChartVSAssembly) vs.getAssembly(objectId);
         chartAssemblyInfo = (ChartVSAssemblyInfo) chartAssembly.getVSAssemblyInfo();
         chartAssemblyInfo.setRTChartDescriptor(null);
      }
      catch(Exception e) {
         throw e;
      }

      ChartDescriptor descriptor = chartAssemblyInfo.getChartDescriptor();
      TitleDescriptor titleDesc = regionHandler.getTitleDescriptor(descriptor, axisType);
      value.updateTitleProperties(titleDesc);

      this.vsObjectPropertyService.editObjectProperty(
         rvs, chartAssemblyInfo, objectId, objectId, linkUri, principal, commandDispatcher);

      return null;
   }

   /**
    * Get chart area.
    */
   public ChartArea getChartArea(RuntimeViewsheet rvs, ChartVSAssembly chartAssembly,
                                 String linkUri)
      throws Exception
   {
      return getChartArea(rvs, chartAssembly, linkUri, false);
   }

   /**
    * Get chart area.
    */
   public ChartArea getChartArea(RuntimeViewsheet rvs, ChartVSAssembly chartAssembly,
                                 String linkUri, boolean maxMode)
      throws Exception
   {
      // Get ChartArea, using mechanism lifted from GetChartAreaEvent
      ChartArea chartArea;

      try {
         ViewsheetSandbox box = rvs.getViewsheetSandbox();
         VSChartInfo cinfo = chartAssembly.getVSChartInfo();
         ChartVSAssemblyInfo chartInfo = chartAssembly.getChartInfo();
         final String absoluteName = chartAssembly.getAbsoluteName();
         cinfo.setLocalMap(
            VSUtil.getLocalMap(rvs.getViewsheet(), absoluteName));

         VGraphPair pair;

         if(maxMode && chartInfo != null && chartInfo.getMaxSize() != null) {
            pair = box.getVGraphPair(absoluteName, true, chartInfo.getMaxSize());
         }
         else {
            pair = box.getVGraphPair(absoluteName);
         }

         if(pair != null && !pair.isCompleted()) {
            box.clearGraph(absoluteName);
            pair = box.getVGraphPair(absoluteName);
         }

         XCube cube = chartAssembly.getXCube();
         boolean drill = !rvs.isTipView(absoluteName) &&
            ((ChartVSAssemblyInfo) chartAssembly.getInfo()).isDrillEnabled();

         if(cube == null) {
            SourceInfo src = chartAssembly.getSourceInfo();

            if(src != null) {
               cube = AssetUtil.getCube(src.getPrefix(), src.getSource());
            }
         }

         chartArea = pair == null || !pair.isCompleted() || pair.getRealSizeVGraph() == null
            ? null : new ChartArea(pair, linkUri, cinfo, cube, drill);

      }
      catch(Exception ex) {
         LOG.error("Failed to process request", ex);
         return null;
      }

      return chartArea;
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final ViewsheetService viewsheetService;
   private final VSBindingService vsBindingService;
   private ChartRegionHandler regionHandler;
   private static final Logger LOG = LoggerFactory.getLogger(RegionPropertyDialogService.class);
}
