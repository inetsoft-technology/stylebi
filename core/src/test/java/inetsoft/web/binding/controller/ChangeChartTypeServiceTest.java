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
package inetsoft.web.binding.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.binding.event.ChangeChartTypeEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Service-level tests for {@link ChangeChartTypeService}. The static smoothLines
 * transition matrix is covered by {@link ChangeChartTypeServiceSmoothLinesTransitionTest}.
 */
@SreeHome
@ExtendWith(MockitoExtension.class)
// LENIENT: shared @BeforeEach stubs are not all exercised by every test path
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangeChartTypeServiceTest {

   @BeforeEach
   void setup() throws Exception {
      service = new ChangeChartTypeService(
         bindingFactory, runtimeViewsheetRef, coreLifecycleService, chartRefService,
         changeSeparateController, assemblyInfoHandler, chartHandler, bindingTreeController,
         viewsheetService);

      when(runtimeViewsheetRef.getRuntimeId()).thenReturn("vs1");
      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(viewsheetSandbox));
      when(bindingFactory.createModel(any())).thenReturn(mock(BindingModel.class));

      chart = new ChartVSAssembly(viewsheet, "Chart1");
      when(viewsheet.getAssembly("Chart1")).thenReturn(chart);
   }

   /**
    * After Network → Circular the runtime ChartDescriptor must be cleared so the next
    * render reads the design-time smoothLines = true that applySmoothLinesTransition just
    * set. Without this reset a cached RT clone (from any earlier script/foreground access)
    * keeps the chart drawing straight chords despite the checkbox showing on.
    */
   @Test
   void networkToCircular_clearsRuntimeChartDescriptor() throws Exception {
      chart.setVSChartInfo(new RelationVSChartInfo());
      chart.getVSChartInfo().setChartType(GraphTypes.CHART_NETWORK);
      chart.setChartDescriptor(new ChartDescriptor());

      ChartVSAssemblyInfo ainfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      ainfo.setRTChartDescriptor(new ChartDescriptor());
      assertNotNull(ainfo.getRTChartDescriptor(), "precondition: RT descriptor is populated");

      ChangeChartTypeEvent event = new ChangeChartTypeEvent();
      event.setName("Chart1");
      event.setType(GraphTypes.CHART_CIRCULAR);

      try(MockedStatic<GraphTypeUtil> mocked = Mockito.mockStatic(
         GraphTypeUtil.class, Mockito.CALLS_REAL_METHODS))
      {
         mocked.when(() -> GraphTypeUtil.checkChartStylePermission(anyInt())).thenReturn(true);
         service.changeChartType("vs1", event, null, dispatcher, "/link");
      }

      assertNull(ainfo.getRTChartDescriptor(),
         "Network → Circular must invalidate the runtime descriptor");
      assertTrue(ainfo.getChartDescriptor().getPlotDescriptor().isSmoothLines(),
         "smoothLines must be defaulted on for the new Circular type");
   }

   /**
    * The RT reset is unconditional — it must fire even for type changes that
    * applySmoothLinesTransition does not touch (Bar → Pie etc.), because the
    * surrounding code mutates other PlotDescriptor fields (stackMeasures,
    * valuesVisible, target lines) that would otherwise stay masked behind a stale RT clone.
    */
   @Test
   void anyTypeChange_clearsRuntimeChartDescriptor() throws Exception {
      chart.setVSChartInfo(new DefaultVSChartInfo());
      chart.getVSChartInfo().setChartType(GraphTypes.CHART_BAR);
      chart.setChartDescriptor(new ChartDescriptor());

      ChartVSAssemblyInfo ainfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      ainfo.setRTChartDescriptor(new ChartDescriptor());
      assertNotNull(ainfo.getRTChartDescriptor(), "precondition: RT descriptor is populated");

      ChangeChartTypeEvent event = new ChangeChartTypeEvent();
      event.setName("Chart1");
      event.setType(GraphTypes.CHART_PIE);

      try(MockedStatic<GraphTypeUtil> mocked = Mockito.mockStatic(
         GraphTypeUtil.class, Mockito.CALLS_REAL_METHODS))
      {
         mocked.when(() -> GraphTypeUtil.checkChartStylePermission(anyInt())).thenReturn(true);
         service.changeChartType("vs1", event, null, dispatcher, "/link");
      }

      assertNull(ainfo.getRTChartDescriptor(),
         "RT descriptor must be cleared for every chart-type change, not just smoothLines transitions");
   }

   private ChangeChartTypeService service;
   private ChartVSAssembly chart;

   @Mock VSBindingService bindingFactory;
   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock CoreLifecycleService coreLifecycleService;
   @Mock ChartRefModelFactoryService chartRefService;
   @Mock ChangeSeparateStatusController changeSeparateController;
   @Mock VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock VSChartHandler chartHandler;
   @Mock VSBindingTreeController bindingTreeController;
   @Mock ViewsheetService viewsheetService;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock ViewsheetSandbox viewsheetSandbox;
   @Mock CommandDispatcher dispatcher;
}
