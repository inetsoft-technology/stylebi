/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.graph.VSMapInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.viewsheet.controller.chart.VSChartMapService;
import inetsoft.web.wiz.model.MapViewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.awt.geom.Rectangle2D;
import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for bug #80334: {@link WizGeoService#clear} (Clear Geographic) and the
 * map-view get/set pair. Verifies the loud-failure contract: clearing a column that is not
 * geographic, and set-view calls that are malformed or aimed at a non-map chart, throw instead of
 * reporting success.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizGeoServiceMapViewClearTest {
   private ViewsheetService vsService;
   private VSChartHandler chartHandler;
   private SecurityEngine securityEngine;
   private Principal principal;
   private ChartVSAssembly chart;
   private VSChartInfo chartInfo;
   private PlotDescriptor plot;
   private ViewsheetSandbox box;
   private ChartVSAssemblyInfo assemblyInfo;
   private WizGeoService service;
   private RuntimeViewsheet rvs;

   @BeforeEach
   void setUp() throws Exception {
      vsService = mock(ViewsheetService.class);
      chartHandler = mock(VSChartHandler.class);
      securityEngine = mock(SecurityEngine.class);
      principal = mock(Principal.class);
      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      chart = mock(ChartVSAssembly.class);
      chartInfo = mock(VSChartInfo.class);
      ChartDescriptor descriptor = mock(ChartDescriptor.class);
      plot = new PlotDescriptor();
      box = mock(ViewsheetSandbox.class);

      when(vsService.getViewsheet("rt1", principal)).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt1");
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(vs.getAssembly("Chart1")).thenReturn(chart);
      assemblyInfo = mock(ChartVSAssemblyInfo.class);
      when(chart.getVSChartInfo()).thenReturn(chartInfo);
      when(chart.getVSAssemblyInfo()).thenReturn(assemblyInfo);
      when(chart.getChartInfo()).thenReturn(assemblyInfo);
      when(assemblyInfo.getVSChartInfo()).thenReturn(chartInfo);
      when(chart.getChartDescriptor()).thenReturn(descriptor);
      when(descriptor.getPlotDescriptor()).thenReturn(plot);

      service = new WizGeoService(vsService, chartHandler, securityEngine);
   }

   /** Test-side holder for set-view arguments. */
   private static class MapViewRequest {
      Integer zoomBy;
      Double panX;
      Double panY;
      Boolean reset;

      void setZoomBy(Integer v) { zoomBy = v; }
      void setPanX(Double v) { panX = v; }
      void setPanY(Double v) { panY = v; }
      void setReset(Boolean v) { reset = v; }
   }

   private static MapViewRequest viewRequest() {
      return new MapViewRequest();
   }

   private MapViewResponse setView(MapViewRequest r) throws Exception {
      return service.setMapView(rvs, "Chart1", r.zoomBy, r.panX, r.panY, r.reset);
   }


   // ── map view ──────────────────────────────────────────────────────────────

   @Test
   void getMapViewReadsTheSavedDescriptor() throws Exception {
      when(chartInfo.getChartType()).thenReturn(GraphTypes.CHART_MAP);
      plot.setZoom(2.0);
      plot.setZoomLevel(4);
      plot.setPanX(0.25);
      plot.setPanY(-0.5);
      plot.setWebMap(true);

      MapViewResponse response = service.getMapView(rvs, "Chart1");

      assertEquals(2.0, response.getZoom());
      assertEquals(4, response.getZoomLevel());
      assertEquals(0.25, response.getPanX());
      assertEquals(-0.5, response.getPanY());
      assertTrue(response.isWebMap());
   }

   @Test
   void mapViewRefusesANonMapChart() {
      when(chartInfo.getChartType()).thenReturn(GraphTypes.CHART_BAR);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.getMapView(rvs, "Chart1"));
      assertTrue(thrown.getMessage().contains("not a map"));

      MapViewRequest set = viewRequest();
      set.setReset(true);
      assertThrows(IllegalArgumentException.class, () -> setView(set));
   }

   @Test
   void setMapViewResetIsExclusiveWithTheOtherFields() {
      when(chartInfo.getChartType()).thenReturn(GraphTypes.CHART_MAP);
      MapViewRequest request = viewRequest();
      request.setReset(true);
      request.setZoomBy(1);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> setView(request));
      assertTrue(thrown.getMessage().contains("reset"));
   }

   @Test
   void setMapViewWithNothingToSetFailsLoud() {
      when(chartInfo.getChartType()).thenReturn(GraphTypes.CHART_MAP);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> setView(viewRequest()));
      assertTrue(thrown.getMessage().contains("Nothing to set"));
   }

   @Test
   void setMapViewRejectsANonFinitePan() {
      when(chartInfo.getChartType()).thenReturn(GraphTypes.CHART_MAP);
      MapViewRequest request = viewRequest();
      request.setPanX(Double.NaN);

      assertThrows(IllegalArgumentException.class, () -> setView(request));
   }

   @Test
   void setMapViewDoesNotReportSuccessWhenTheChartHasNotRendered() {
      when(chartInfo.getChartType()).thenReturn(GraphTypes.CHART_MAP);
      MapViewRequest request = viewRequest();
      request.setZoomBy(1);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> setView(request));
      assertTrue(thrown.getMessage().contains("Chart1"));
   }

   @Test
   void setMapViewResetRestoresTheDefaultView() throws Exception {
      when(chartInfo.getChartType()).thenReturn(GraphTypes.CHART_MAP_CONTOUR);
      MapViewRequest request = viewRequest();
      request.setReset(true);

      setView(request);

      assertEquals(1.0, plot.getZoom());
      assertEquals(0.0, plot.getPanX());
      assertEquals(0.0, plot.getPanY());
   }

   // ── clear geographic ──────────────────────────────────────────────────────


   @Test
   void clearRefusesAColumnThatIsNotGeographic() throws Exception {
      when(chartInfo.getRTGeoColumns()).thenReturn(new ColumnSelection());
      when(chartInfo.getGeoColumns()).thenReturn(new ColumnSelection());

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.clear(rvs, "Chart1", "State"));

      assertTrue(thrown.getMessage().contains("'State'"));
      verify(chartHandler, never()).changeGeographic(any(), any(), any(), anyBoolean());
   }

   @Test
   void clearRefreshesRuntimeGeoColumnsBeforeLookingThemUp() throws Exception {
      when(chartInfo.getRTGeoColumns()).thenReturn(new ColumnSelection());
      when(chartInfo.getGeoColumns()).thenReturn(new ColumnSelection());

      assertThrows(IllegalArgumentException.class, () -> service.clear(rvs, "Chart1", "State"));

      verify(chartHandler).updateGeoColumns(any(), any(), eq(chart), eq(chartInfo));
   }

   @Test
   void clearRequiresAColumn() {

      assertThrows(IllegalArgumentException.class, () -> service.clear(rvs, "Chart1", ""));
   }

   private VSMapInfo geoMapInfo() {
      VSMapInfo info = new VSMapInfo();
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(new AttributeRef(null, "State"));
      info.setGeoColumns(cols);
      info.updateGeoColumns(null, null);
      when(assemblyInfo.getVSChartInfo()).thenReturn(info);
      return info;
   }

   private static void removeStateFromDesign(VSChartInfo info) {
      ColumnSelection cols = info.getGeoColumns();
      cols.removeAttribute(cols.getAttribute("State"));
   }

   private void stubClearRemovesState() throws Exception {
      when(chartHandler.changeGeographic(any(), eq("State"), any(), anyBoolean()))
         .thenAnswer(inv -> {
            VSChartInfo target = inv.getArgument(0);
            DataRef removed = target.getGeoColumns().getAttribute("State");
            removeStateFromDesign(target);
            return removed;
         });
   }

   @Test
   void clearRemovesTheGeographicColumnAndRefreshesTheChart() throws Exception {
      VSMapInfo info = geoMapInfo();
      stubClearRemovesState();

      service.clear(rvs, "Chart1", "State");

      assertNull(info.getGeoColumns().getAttribute("State"));
      verify(chartHandler).fixMapInfo(eq(info), eq("State"), any());
      verify(box).updateAssembly(any());
      verify(assemblyInfo, never()).setVSChartInfo(any());
   }

   @Test
   void clearRollsBackWhenChangeGeographicReturnsNull() throws Exception {
      VSMapInfo info = geoMapInfo();
      when(chartHandler.changeGeographic(any(), eq("State"), any(), anyBoolean()))
         .thenAnswer(inv -> {
            removeStateFromDesign(inv.getArgument(0));
            return null;
         });

      assertThrows(IllegalStateException.class, () -> service.clear(rvs, "Chart1", "State"));

      ArgumentCaptor<VSChartInfo> restored = ArgumentCaptor.forClass(VSChartInfo.class);
      verify(assemblyInfo).setVSChartInfo(restored.capture());
      assertNotSame(info, restored.getValue());
      assertNotNull(restored.getValue().getGeoColumns().getAttribute("State"));
      verify(chartHandler, never()).fixMapInfo(any(), any(), any());
   }

   @Test
   void clearRollsBackWhenTheColumnIsStillGeographic() throws Exception {
      VSMapInfo info = geoMapInfo();
      // simulates the RT-vs-design index mismatch: another column is touched, State stays
      when(chartHandler.changeGeographic(any(), eq("State"), any(), anyBoolean()))
         .thenAnswer(inv -> {
            VSChartInfo target = inv.getArgument(0);
            target.getGeoColumns().addAttribute(new AttributeRef(null, "City"));
            return new AttributeRef(null, "City");
         });

      Exception thrown = assertThrows(IllegalStateException.class,
         () -> service.clear(rvs, "Chart1", "State"));
      assertTrue(thrown.getMessage().contains("still geographic"));

      ArgumentCaptor<VSChartInfo> restored = ArgumentCaptor.forClass(VSChartInfo.class);
      verify(assemblyInfo).setVSChartInfo(restored.capture());
      assertNotSame(info, restored.getValue());
      assertNull(restored.getValue().getGeoColumns().getAttribute("City"));
      assertNotNull(restored.getValue().getGeoColumns().getAttribute("State"));
   }

   @Test
   void clearRollsBackWhenALaterStepThrows() throws Exception {
      geoMapInfo();
      stubClearRemovesState();
      doThrow(new IllegalStateException("update failed")).when(box).updateAssembly(any());

      assertThrows(IllegalStateException.class, () -> service.clear(rvs, "Chart1", "State"));

      ArgumentCaptor<VSChartInfo> restored = ArgumentCaptor.forClass(VSChartInfo.class);
      verify(assemblyInfo).setVSChartInfo(restored.capture());
      assertNotNull(restored.getValue().getGeoColumns().getAttribute("State"));
   }

   // ── map view set: success and rollback ────────────────────────────────────

   private void renderedGraph() throws Exception {
      when(box.getVGraphPair(eq("Chart1"), eq(true), any())).thenReturn(mock(VGraphPair.class));
   }

   @Test
   void setMapViewAppliesZoomAndPanThenInvalidatesTheRuntimeGraph() throws Exception {
      when(chartInfo.getChartType()).thenReturn(GraphTypes.CHART_MAP);
      renderedGraph();
      MapViewRequest request = viewRequest();
      request.setZoomBy(2);
      request.setPanX(0.25);

      try(MockedStatic<VSChartMapService> mocked = mockStatic(VSChartMapService.class)) {
         mocked.when(() -> VSChartMapService.applyZoomPan(
            anyInt(), anyDouble(), anyDouble(), any(), any(), any(), any()))
            .thenAnswer(inv -> {
               plot.setZoom(1.44);
               plot.setPanX(0.25);
               return null;
            });

         MapViewResponse response = setView(request);

         mocked.verify(() -> VSChartMapService.applyZoomPan(
            eq(2), eq(0.25), eq(0.0), any(), any(), any(), any()));
         assertEquals(1.44, response.getZoom());
         assertEquals(0.25, response.getPanX());
      }

      verify(assemblyInfo).setRTChartDescriptor(null);
      verify(box).clearGraph("Chart1");
   }

   @Test
   void setMapViewRestoresTheWholeDescriptorWhenApplyingFails() throws Exception {
      when(chartInfo.getChartType()).thenReturn(GraphTypes.CHART_MAP);
      renderedGraph();
      Rectangle2D lonLat = new Rectangle2D.Double(-10, -20, 30, 40);
      plot.setZoom(2.0);
      plot.setZoomLevel(4);
      plot.setPanX(0.1);
      plot.setPanY(0.2);
      plot.setLonLat(lonLat);
      MapViewRequest request = viewRequest();
      request.setZoomBy(3);

      try(MockedStatic<VSChartMapService> mocked = mockStatic(VSChartMapService.class)) {
         // the discrete-zoom branch mutates the design plot before the layout step fails
         mocked.when(() -> VSChartMapService.applyZoomPan(
            anyInt(), anyDouble(), anyDouble(), any(), any(), any(), any()))
            .thenAnswer(inv -> {
               plot.setZoomLevel(7);
               plot.setZoom(9.0);
               plot.setPanX(0.9);
               plot.setPanY(0.9);
               plot.setLonLat(null);
               throw new java.io.IOException("layout failed");
            });

         Exception thrown = assertThrows(IllegalArgumentException.class,
            () -> setView(request));
         assertTrue(thrown.getMessage().contains("layout failed"));
      }

      assertEquals(2.0, plot.getZoom());
      assertEquals(4, plot.getZoomLevel());
      assertEquals(0.1, plot.getPanX());
      assertEquals(0.2, plot.getPanY());
      assertEquals(lonLat, plot.getLonLat());
      verify(box, never()).clearGraph(any());
   }
}
