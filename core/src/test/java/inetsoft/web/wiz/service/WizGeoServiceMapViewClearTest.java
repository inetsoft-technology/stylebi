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
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.wiz.model.GeoClearRequest;
import inetsoft.web.wiz.model.MapViewRequest;
import inetsoft.web.wiz.model.MapViewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for bug #80334: {@link WizGeoService#clear} (Clear Geographic) and the
 * map-view get/set pair. Verifies the loud-failure contract: clearing a column that is not
 * geographic, and set-view calls that are malformed or aimed at a non-map chart, throw instead of
 * reporting success.
 */
@Tag("core")
class WizGeoServiceMapViewClearTest {
   private ViewsheetService vsService;
   private VSChartHandler chartHandler;
   private SecurityEngine securityEngine;
   private Principal principal;
   private ChartVSAssembly chart;
   private VSChartInfo chartInfo;
   private PlotDescriptor plot;
   private WizGeoService service;

   @BeforeEach
   void setUp() throws Exception {
      vsService = mock(ViewsheetService.class);
      chartHandler = mock(VSChartHandler.class);
      securityEngine = mock(SecurityEngine.class);
      principal = mock(Principal.class);
      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      chart = mock(ChartVSAssembly.class);
      chartInfo = mock(VSChartInfo.class);
      ChartDescriptor descriptor = mock(ChartDescriptor.class);
      plot = mock(PlotDescriptor.class);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);

      when(vsService.getViewsheet("rt1", principal)).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt1");
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(vs.getAssembly("Chart1")).thenReturn(chart);
      ChartVSAssemblyInfo assemblyInfo = mock(ChartVSAssemblyInfo.class);
      when(chart.getVSChartInfo()).thenReturn(chartInfo);
      when(chart.getVSAssemblyInfo()).thenReturn(assemblyInfo);
      when(chart.getChartInfo()).thenReturn(assemblyInfo);
      when(assemblyInfo.getVSChartInfo()).thenReturn(chartInfo);
      when(chart.getChartDescriptor()).thenReturn(descriptor);
      when(descriptor.getPlotDescriptor()).thenReturn(plot);

      service = new WizGeoService(vsService, chartHandler, securityEngine);
   }

   private static MapViewRequest viewRequest() {
      MapViewRequest request = new MapViewRequest();
      request.setRuntimeId("rt1");
      request.setAssemblyName("Chart1");
      return request;
   }

   // ── map view ──────────────────────────────────────────────────────────────

   @Test
   void getMapViewReadsTheSavedDescriptor() throws Exception {
      when(chartInfo.getChartType()).thenReturn(GraphTypes.CHART_MAP);
      when(plot.getZoom()).thenReturn(2.0);
      when(plot.getZoomLevel()).thenReturn(4);
      when(plot.getPanX()).thenReturn(0.25);
      when(plot.getPanY()).thenReturn(-0.5);
      when(plot.isWebMap()).thenReturn(true);

      MapViewResponse response = service.getMapView(viewRequest(), principal);

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
         () -> service.getMapView(viewRequest(), principal));
      assertTrue(thrown.getMessage().contains("not a map"));

      MapViewRequest set = viewRequest();
      set.setReset(true);
      assertThrows(IllegalArgumentException.class, () -> service.setMapView(set, principal));
      verify(plot, never()).setZoom(1);
   }

   @Test
   void setMapViewResetIsExclusiveWithTheOtherFields() {
      when(chartInfo.getChartType()).thenReturn(GraphTypes.CHART_MAP);
      MapViewRequest request = viewRequest();
      request.setReset(true);
      request.setZoomBy(1);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.setMapView(request, principal));
      assertTrue(thrown.getMessage().contains("reset"));
   }

   @Test
   void setMapViewWithNothingToSetFailsLoud() {
      when(chartInfo.getChartType()).thenReturn(GraphTypes.CHART_MAP);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.setMapView(viewRequest(), principal));
      assertTrue(thrown.getMessage().contains("Nothing to set"));
   }

   @Test
   void setMapViewRejectsANonFinitePan() {
      when(chartInfo.getChartType()).thenReturn(GraphTypes.CHART_MAP);
      MapViewRequest request = viewRequest();
      request.setPanX(Double.NaN);

      assertThrows(IllegalArgumentException.class, () -> service.setMapView(request, principal));
   }

   @Test
   void setMapViewDoesNotReportSuccessWhenTheChartHasNotRendered() {
      when(chartInfo.getChartType()).thenReturn(GraphTypes.CHART_MAP);
      MapViewRequest request = viewRequest();
      request.setZoomBy(1);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.setMapView(request, principal));
      assertTrue(thrown.getMessage().contains("Chart1"));
   }

   @Test
   void setMapViewResetRestoresTheDefaultView() throws Exception {
      when(chartInfo.getChartType()).thenReturn(GraphTypes.CHART_MAP_CONTOUR);
      MapViewRequest request = viewRequest();
      request.setReset(true);

      service.setMapView(request, principal);

      verify(plot).setZoom(1);
      verify(plot).setPanX(0);
      verify(plot).setPanY(0);
   }

   @Test
   void mapViewIsDeniedWithoutComposerAccess() throws Exception {
      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> service.getMapView(viewRequest(), principal));
   }

   // ── clear geographic ──────────────────────────────────────────────────────

   private static GeoClearRequest clearRequest() {
      GeoClearRequest request = new GeoClearRequest();
      request.setRuntimeId("rt1");
      request.setAssemblyName("Chart1");
      request.setColumn("State");
      return request;
   }

   @Test
   void clearRefusesAColumnThatIsNotGeographic() throws Exception {
      when(chartInfo.getRTGeoColumns()).thenReturn(new ColumnSelection());
      when(chartInfo.getGeoColumns()).thenReturn(new ColumnSelection());

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.clear(clearRequest(), principal));

      assertTrue(thrown.getMessage().contains("'State'"));
      verify(chartHandler, never()).changeGeographic(any(), any(), any(), anyBoolean());
   }

   @Test
   void clearRefreshesRuntimeGeoColumnsBeforeLookingThemUp() throws Exception {
      when(chartInfo.getRTGeoColumns()).thenReturn(new ColumnSelection());
      when(chartInfo.getGeoColumns()).thenReturn(new ColumnSelection());

      assertThrows(IllegalArgumentException.class, () -> service.clear(clearRequest(), principal));

      verify(chartHandler).updateGeoColumns(any(), any(), eq(chart), eq(chartInfo));
   }

   @Test
   void clearIsDeniedWithoutComposerAccess() throws Exception {
      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> service.clear(clearRequest(), principal));
   }

   @Test
   void clearRequiresAColumn() {
      GeoClearRequest request = clearRequest();
      request.setColumn("");

      assertThrows(IllegalArgumentException.class, () -> service.clear(request, principal));
   }
}
