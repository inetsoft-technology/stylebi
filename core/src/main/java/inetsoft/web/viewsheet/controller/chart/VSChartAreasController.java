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
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.RelationCoord;
import inetsoft.graph.internal.DepthException;
import inetsoft.graph.internal.GTool;
import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.report.composition.execution.BoundTableNotFoundException;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.*;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.report.lens.CrossJoinCellCountBeyondLimitException;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.uql.XCube;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.ColumnNotFoundException;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.*;
import inetsoft.util.log.LogContext;
import inetsoft.util.log.LogLevel;
import inetsoft.util.script.ScriptException;
import inetsoft.web.graph.GraphBuilder;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.command.SetChartAreasCommand;
import inetsoft.web.viewsheet.event.chart.VSChartEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.chart.VSChartModel;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;

@Controller
public class VSChartAreasController {
   @Autowired
   public VSChartAreasController(ViewsheetService viewsheetService,
                                 RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.viewsheetService = viewsheetService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   @MessageMapping("/vschart/areas")
   @ExecutionMonitoring
   @HandleAssetExceptions
   @SwitchOrg
   public void getChartAreas(@OrganizationID("getOrgId()") @Payload VSChartEvent event,
                             CommandDispatcher dispatcher, Principal principal)
      throws Exception
   {
      refreshChartAreasModel(event,viewsheetService, runtimeViewsheetRef, dispatcher, principal);
   }

   public static void refreshChartAreasModel(@Payload VSChartEvent event,
                                             ViewsheetService viewsheetService,
                                             RuntimeViewsheetRef runtimeViewsheetRef,
                                             CommandDispatcher dispatcher, Principal principal)
      throws Exception
   {
      VSChartController.VSChartStateInfo state =
         VSChartController.VSChartStateInfo.createChartState(
            event, viewsheetService, principal, runtimeViewsheetRef.getRuntimeId(), true);

      if(state == null) {
         return;
      }

      if(principal instanceof SRPrincipal) {
         ThreadContext.setLocale(((SRPrincipal) principal).getLocale());
      }

      VSChartModel model;
      final ViewsheetSandbox box = state.getViewsheetSandbox();
      ChartVSAssemblyInfo info = state.getChartAssemblyInfo();
      String absoluteName = state.getAssembly().getAbsoluteName();
      String sheetName = box.getAssetEntry() == null ? "" : box.getAssetEntry().getPath();
      IdentityID user = principal != null ? IdentityID.getIdentityIDFromKey(principal.getName()) : new IdentityID("","");
      VGraphPair pair = null;

      Principal ouser = GroupedThread.applyGroupedThread(groupedThread -> {
         Principal threadPrincipal = groupedThread.getPrincipal();
         groupedThread.setPrincipal(principal);
         groupedThread.addRecord(LogContext.DASHBOARD.getRecord(sheetName));
         groupedThread.addRecord(LogContext.ASSEMBLY.getRecord(absoluteName));
         return threadPrincipal;
      });

      try {
         Dimension maxSize = info.getMaxSize();
         box.lockRead();

         try {
            pair = box.getVGraphPair(absoluteName, true, maxSize);
            model = new VSChartModel.VSChartModelFactory().createModel(
               state.getAssembly(), state.getRuntimeViewsheet());
         }
         finally {
            box.unlockRead();
         }

         // it's only possible for model to be null if the rvs has been disposed.
         // in that case it's better to ignore it instead of generating an exception.
         if(model == null) {
            return;
         }

         if(pair != null && !pair.isCompleted()) {
            box.clearGraph(absoluteName);
            pair = box.getVGraphPair(absoluteName);
         }

         if(pair != null) {
            final VSChartInfo chartInfo = state.getChartInfo();
            VGraph vgraph = pair.getRealSizeVGraph();
            double initialWidthRatio = chartInfo.getInitialWidthRatio();
            double initialHeightRatio = chartInfo.getInitialHeightRatio();
            double widthRatio = chartInfo.getEffectiveWidthRatio();
            double heightRatio = chartInfo.getEffectiveHeightRatio();
            boolean resized = chartInfo.isWidthResized() || chartInfo.isHeightResized();
            boolean horizontallyResizable = false;
            boolean verticallyResizable = false;
            double maxHorizontalResize = 0;
            double maxVerticalResize = 0;

            if(vgraph != null) {
               verticallyResizable = GraphUtil.isVResizable(vgraph, chartInfo);
               horizontallyResizable = GraphUtil.isHResizable(vgraph, chartInfo);
               final Coordinate coord = vgraph.getCoordinate();

               // for wordcloud/dotplot, the ratio is equally applied to both width/height.
               if(coord instanceof RelationCoord || GraphTypeUtil.isWordCloud(chartInfo) ||
                  GraphTypeUtil.isDotPlot(chartInfo)) {
                  maxHorizontalResize = maxVerticalResize = 5;
               }
               else if(coord != null) {
                  maxHorizontalResize =
                     initialWidthRatio * GTool.getUnitCount(coord, Coordinate.BOTTOM_AXIS, false);
                  maxVerticalResize =
                     initialHeightRatio * GTool.getUnitCount(coord, Coordinate.LEFT_AXIS, false);
               }
            }

            XCube cube = state.getAssembly().getXCube();
            boolean drill = !state.getRuntimeViewsheet().isTipView(absoluteName) &&
               info.isDrillEnabled();

            if(cube == null) {
               SourceInfo src = state.getAssembly().getSourceInfo();

               if(src != null) {
                  cube = AssetUtil.getCube(src.getPrefix(), src.getSource());
               }
            }

            ChartArea chartArea = null;

            box.lockRead();

            try {
               if(pair.isCompleted() && pair.getRealSizeVGraph() != null) {
                  chartArea = new ChartArea(pair, null, chartInfo, cube, drill, false, absoluteName);
               }

               model = (VSChartModel) new GraphBuilder(
                  state.getAssembly(), chartInfo, chartArea, state.getChartDescriptor(), model).build();

               if(pair.isCompleted() && pair.getRealSizeVGraph() == null) {
                  model.setNoData(true);
               }
            }
            catch(Exception ex) {
               if(pair.isCancelled()) {
                  LOG.debug("Failed to init chart area since graph is cancelled!", ex);
               }
               else {
                  if(ex.getMessage() != null && !ex.getMessage().isEmpty()) {
                     CoreTool.addUserMessage(ex.getMessage());
                  }
                  LOG.warn("Failed to generate chart area: " + ex, ex);
               }
            }
            finally {
               box.unlockRead();
            }

            SetChartAreasCommand command = SetChartAreasCommand.builder()
               .invalid(model.isInvalid())
               .verticallyResizable(verticallyResizable)
               .horizontallyResizable(horizontallyResizable)
               .maxHorizontalResize(maxHorizontalResize)
               .maxVerticalResize(maxVerticalResize)
               .plot(model.getPlot())
               .titles(model.getTitles())
               .axes(model.getAxes())
               .legends(model.getLegends())
               .facets(model.getFacets())
               .legendsBounds(model.getLegendsBounds())
               .contentBounds(model.getContentBounds())
               .legendOption(model.getLegendOption())
               .stringDictionary(model.getStringDictionary())
               .regionMetaDictionary(model.getRegionMetaDictionary())
               .initialWidthRatio(initialWidthRatio)
               .initialHeightRatio(initialHeightRatio)
               .widthRatio(widthRatio)
               .heightRatio(heightRatio)
               .resized(resized)
               .changedByScript(model.isChangedByScript())
               .completed(chartArea != null)
               .noData(model.isNoData())
               .build();
            dispatcher.sendCommand(absoluteName, command);
         }
      }
      catch(DynamicColumnNotFoundException de) {
         SetChartAreasCommand command = SetChartAreasCommand.builder()
            .invalid(false)
            .verticallyResizable(false)
            .horizontallyResizable(false)
            .maxHorizontalResize(1)
            .maxVerticalResize(1)
            .legendOption(0)
            .initialWidthRatio(1)
            .initialHeightRatio(1)
            .widthRatio(1)
            .heightRatio(1)
            .resized(false)
            .changedByScript(false)
            .completed(true)
            .noData(true)
            .build();
         dispatcher.sendCommand(absoluteName, command);
      }
      catch(Exception e) {
         if(pair != null && pair.isCancelled()) {
            return;
         }

         SetChartAreasCommand command = SetChartAreasCommand.builder()
            .invalid(true)
            .verticallyResizable(false)
            .horizontallyResizable(false)
            .maxHorizontalResize(1)
            .maxVerticalResize(1)
            .legendOption(0)
            .initialWidthRatio(1)
            .initialHeightRatio(1)
            .widthRatio(1)
            .heightRatio(1)
            .resized(false)
            .changedByScript(false)
            .completed(true)
            .noData(false)
            .build();
         dispatcher.sendCommand(absoluteName, command);

         if(e instanceof MessageException || e instanceof ConfirmException) {
            throw e;
         }

         if(e instanceof DepthException) {
            LOG.warn("Failed to create chart [{}]", absoluteName, e);
         }
         else if(e instanceof BoundTableNotFoundException || e instanceof ScriptException ||
            e instanceof ColumnNotFoundException || e instanceof ExpiredSheetException ||
            e instanceof CrossJoinCellCountBeyondLimitException)
         {
            if(LOG.isDebugEnabled()) {
               LOG.debug("Failed to create chart: [{}]", absoluteName, e);
            }
            else {
               LOG.warn("Failed to create chart: [{}], {}", absoluteName, e.getMessage());
            }
         }
         else {
            LOG.error("Failed to create chart: [{}]", absoluteName, e);
         }

         if(!box.isRefreshing()) {
            throw new MessageException(e.getMessage(), LogLevel.DEBUG, false);
         }
      }
      finally {
         GroupedThread.withGroupedThread(groupedThread -> {
            groupedThread.setPrincipal(ouser);
            groupedThread.removeRecord(LogContext.DASHBOARD.getRecord(sheetName));
            groupedThread.removeRecord(LogContext.ASSEMBLY.getRecord(absoluteName));
         });
      }
   }

   private final ViewsheetService viewsheetService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;

   private static final Logger LOG = LoggerFactory.getLogger(VSChartAreasController.class);
}
