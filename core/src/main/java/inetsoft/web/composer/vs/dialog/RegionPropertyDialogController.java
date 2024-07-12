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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
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
import inetsoft.util.Tool;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.graph.model.dialog.*;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Controller that provides the REST endpoints for the chart property dialog.
 *
 * @since 12.3
 */
@Controller
public class RegionPropertyDialogController {
   /**
    * Creates a new instance of <tt>ChartPropertyController</tt>.
    *  @param vsObjectPropertyService VSObjectPropertyService instance
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    * @param viewsheetService
    */
   @Autowired
   public RegionPropertyDialogController(
      VSObjectPropertyService vsObjectPropertyService,
      RuntimeViewsheetRef runtimeViewsheetRef,
      ViewsheetService viewsheetService,
      VSBindingService vsBindingService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.viewsheetService = viewsheetService;
      this.vsBindingService = vsBindingService;
   }

   /**
    * Gets the axis property dialog model of the chart
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId the runtime identifier of the chart.
    *
    * @return the property dialog model.
    */

   @RequestMapping(
      value = "/api/composer/vs/axis-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public AxisPropertyDialogModel getAxisPropertyDialogModel(
      @PathVariable("objectId") String objectId,
      @RequestParam("axisType") String axisType,
      @RequestParam("index") String index,
      @RequestParam("field") String field,
      @RemainingPath String runtimeId,
      @LinkUri String linkUri,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
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

   /**
    * Sets the specified chart assembly info.
    *
    * @param objectId   the chart id
    * @param value the axis property dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/axis-property-dialog-model/{objectId}/{axisType}/{index}/{field}")
   public void setAxisPropertyDialogModel(
      @DestinationVariable("objectId") String objectId,
      @DestinationVariable("axisType") String axisType,
      @DestinationVariable("index") int index,
      @DestinationVariable("field") String field,
      @Payload AxisPropertyDialogModel value,
      @LinkUri String linkUri,
      Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      ChartVSAssembly chartAssembly;
      ChartVSAssemblyInfo chartAssemblyInfo;

      try {
         ViewsheetService engine = viewsheetService;
         rvs = engine.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
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
   }

   /**
    * Gets the legend format dialog model of the chart
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId the runtime identifier of the chart.
    *
    * @return the legend format dialog model.
    */

   @RequestMapping(
      value = "/api/composer/vs/legend-format-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public LegendFormatDialogModel getLegendFormatDialogModel(
      @PathVariable("objectId") String objectId,
      @RequestParam("index") String index,
      @RemainingPath String runtimeId,
      @LinkUri String linkUri,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
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

   /**
    * Sets the specified chart assembly info.
    *
    * @param objectId   the chart id
    * @param value the legend format dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/legend-format-dialog-model/{objectId}/{index}")
   public void setLegendFormatDialogModel(
      @DestinationVariable("objectId") String objectId,
      @DestinationVariable("index") int index,
      @Payload LegendFormatDialogModel value,
      @LinkUri String linkUri,
      Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      ChartVSAssembly chartAssembly;
      ChartVSAssemblyInfo chartAssemblyInfo;

      try {
         ViewsheetService engine = viewsheetService;
         rvs = engine.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
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
   }

   /**
    * Gets the title format dialog model of the chart
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId the runtime identifier of the chart.
    *
    * @return the property dialog model.
    */
   @RequestMapping(
      value = "/api/composer/vs/title-format-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public TitleFormatDialogModel getTitleFormatDialogModel(
      @PathVariable("objectId") String objectId,
      @RequestParam("axisType") String axisType,
      @RemainingPath String runtimeId,
      @LinkUri String linkUri,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
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

      ChartDescriptor descriptor = chartAssemblyInfo.getChartDescriptor();
      TitleDescriptor titleDesc = regionHandler.getTitleDescriptor(descriptor, axisType);
      ChartArea chartArea = getChartArea(rvs, chartAssembly, linkUri);
      TitleArea titleArea = regionHandler.getTitleArea(chartArea, axisType);
      String oldTitle = (String) titleArea.getChartAreaInfo().getProperty("titlename");

      return new TitleFormatDialogModel(titleDesc, oldTitle);
   }

   /**
    * Sets the specified chart assembly info.
    *
    * @param objectId   the chart id
    * @param value the title format dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/title-format-dialog-model/{objectId}/{axisType}")
   public void setTitleFormatDialogModel(
      @DestinationVariable("objectId") String objectId,
      @DestinationVariable("axisType") String axisType,
      @Payload TitleFormatDialogModel value,
      @LinkUri String linkUri,
      Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      ChartVSAssembly chartAssembly;
      ChartVSAssemblyInfo chartAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
         vs = rvs.getViewsheet();
         chartAssembly = (ChartVSAssembly) vs.getAssembly(objectId);
         chartAssemblyInfo = (ChartVSAssemblyInfo) chartAssembly.getVSAssemblyInfo();
         chartAssemblyInfo.setRTChartDescriptor(null);
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      ChartDescriptor descriptor = chartAssemblyInfo.getChartDescriptor();
      TitleDescriptor titleDesc = regionHandler.getTitleDescriptor(descriptor, axisType);
      value.updateTitleProperties(titleDesc);

      this.vsObjectPropertyService.editObjectProperty(
         rvs, chartAssemblyInfo, objectId, objectId, linkUri, principal, commandDispatcher);
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
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService viewsheetService;
   private final VSBindingService vsBindingService;
   @Autowired
   private ChartRegionHandler regionHandler;
   private static final Logger LOG =
      LoggerFactory.getLogger(RegionPropertyDialogController.class);
}
