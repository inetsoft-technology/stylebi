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
import inetsoft.graph.*;
import inetsoft.graph.coord.GeoCoord;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.geo.service.WebMapService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.ClearMapPanCommand;
import inetsoft.web.viewsheet.event.chart.VSMapPanEvent;
import inetsoft.web.viewsheet.event.chart.VSMapZoomEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.security.Principal;

@Controller
public class VSChartMapController {
   @Autowired
   public VSChartMapController(RuntimeViewsheetRef runtimeViewsheetRef,
                               PlaceholderService placeholderService,
                               ViewsheetService viewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.viewsheetService = viewsheetService;
   }

   public static void applyZoomPan(int zoomIncr, double panX, double panY, ChartDescriptor desc,
                                   VGraph vgraph, EGraph egraph, DataSet data) throws IOException
   {
      PlotDescriptor plot = desc.getPlotDescriptor();
      GeoCoord coord = (GeoCoord) egraph.getCoordinate();
      Dimension2D size = vgraph.getSize();
      Rectangle2D bbox = ((GeoCoord) vgraph.getCoordinate()).getBBox();
      WebMapService service = coord.getWebMapService();

      // save current lon/lat range when zoom/pan is set, so when data is filtered
      // the map will stay at the same position/zoom and not shift.
      if(plot.getLonLat() == null) {
         plot.setLonLat(coord.getLonLat());
      }

      // try out zoom to see if it's valid. since zoom is used in calculation
      // in GeoCoord.init() and MapboxPainter.getURL() to generate the URL,
      // the final lat/lon and width/height is hard to predict without
      // replicating the logic (in reverse). that can be very fragile. so we
      // just set it and see if it generates an exception, and ignore invalid zoom.

      if(zoomIncr != 0) {
         if(service != null && service.isDiscreteZoom()) {
            int zoomLevel = service.getZoomLevel() + zoomIncr;
            zoomLevel = Math.max(0, zoomLevel);
            zoomLevel = Math.min(service.getMaxZoomLevel(), zoomLevel);
            service.setZoomLevel(zoomLevel);
            plot.setZoomLevel(zoomLevel);
         }
         else {
            double zoom = zoomIncr > 0 ? Math.pow(1.2, zoomIncr) : Math.pow(0.8, -zoomIncr);
            // formula for calculating the max/min zoom should match GeoCoord.init()
            // center + range / (2 * zoom) = 180
            // center - range / (2 * zoom) = -180
            zoom = Math.max(bbox.getHeight() / 2 / (180 - bbox.getCenterY()), zoom);
            zoom = Math.max(bbox.getHeight() / 2 / (180 + bbox.getCenterY()), zoom);
            coord.setZoom(plot.getZoom() * zoom);
         }
      }

      // event.panX/Y are percent of width/height
      double adjX = panX * bbox.getWidth();
      double adjY = panY * bbox.getHeight();
      /* GeoCoord.init() handles wrapping around the x boundaries so we shouldn't need to
         restrict the x to be within -360 to 360.
      adjX = Math.min(adjX, 360 - bbox.getMaxX());
      adjX = Math.max(adjX, -360 - bbox.getMinX());
       */
      adjY = Math.min(adjY, 180 - bbox.getMaxY());
      adjY = Math.max(adjY, -180 - bbox.getMinY());
      coord.setPanX(plot.getPanX() + adjX);
      coord.setPanY(plot.getPanY() + adjY);
      coord.setCoordTransform(null);

      VGraph vgraph2 = Plotter.getPlotter(egraph).plotAndLayout(
         data, 0, 0, (int) size.getWidth(), (int) size.getHeight());
      vgraph2.getOrCreatePlotBackground();

      // no exception, set in plot descriptor
      plot.setPanX(coord.getPanX());
      plot.setPanY(coord.getPanY());

      if(service != null && service.isDiscreteZoom()) {
         plot.setZoomLevel(coord.getWebMapService().getZoomLevel());
      }
      else {
         plot.setZoom(coord.getZoom());
      }
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/vsmap/zoom")
   public void zoomIn(@Payload VSMapZoomEvent event,
                      Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      testThenSet(event.getChartName(), rvs, dispatcher, event.getIncrement(), 0, 0);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/vsmap/pan")
   public void pan(@Payload VSMapPanEvent event,
                   Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      testThenSet(event.getChartName(), rvs, dispatcher, 0, event.getPanX(), event.getPanY());
   }

   // test zoom/pan setting, and apply if there is no error from web map service.
   private void testThenSet(String chartName, RuntimeViewsheet rvs, CommandDispatcher dispatcher,
                            int increment, double panX, double panY)
   {
      try {
         ViewsheetSandbox box = rvs.getViewsheetSandbox();
         Viewsheet vs = rvs.getViewsheet();
         final ChartVSAssembly assembly = (ChartVSAssembly) vs.getAssembly(chartName);
         ChartDescriptor desc = assembly.getChartDescriptor();
         ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) assembly.getInfo();
         Dimension maxSize = info.getMaxSize();
         VGraphPair vpair = box.getVGraphPair(chartName, true, maxSize);
         applyZoomPan(increment, panX, panY, desc, vpair.getRealSizeVGraph(),
            vpair.getEGraph(), vpair.getData());
         assembly.getChartInfo().setRTChartDescriptor(null);
         box.clearGraph(chartName);

         placeholderService.refreshVSAssembly(rvs, assembly, dispatcher);
      }
      catch(Exception ex) {
         dispatcher.sendCommand(chartName, new ClearMapPanCommand());

         // if zoom failed (e.g. lat out of range), ignore the zoom
         if(LOG.isDebugEnabled()) {
            LOG.debug("Failed to get map background: " + ex, ex);
         }
      }
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/vsmap/clear")
   public void clear(@Payload VSMapPanEvent event,
                   Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();
      final ChartVSAssembly assembly = (ChartVSAssembly) vs.getAssembly(event.getChartName());
      ChartDescriptor desc = assembly.getChartDescriptor();
      PlotDescriptor plot = desc.getPlotDescriptor();

      clearPanZoom(plot);
      box.clearGraph(event.getChartName());
      placeholderService.refreshVSAssembly(rvs, assembly, dispatcher);
   }

   private static void clearPanZoom(PlotDescriptor plot) {
      plot.setPanX(0);
      plot.setPanY(0);
      plot.setZoom(1);
      plot.setLonLat(null);
   }

   private RuntimeViewsheetRef runtimeViewsheetRef;
   private PlaceholderService placeholderService;
   private ViewsheetService viewsheetService;
   private static final Logger LOG = LoggerFactory.getLogger(VSChartMapController.class);
}
