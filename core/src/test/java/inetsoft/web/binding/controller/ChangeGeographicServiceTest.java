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
package inetsoft.web.binding.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.service.graph.ChartGeoInfoFactory;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.binding.event.ChangeGeographicEvent;
import inetsoft.web.binding.event.RefreshBindingTreeEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.binding.service.VSChartBindingFactory;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76674, the same shape #4971 fixed in {@code ModifyCalculateFieldService}.
 *
 * <p>{@code changeGeographic} ends by repopulating the binding tree, and used to do it through
 * {@code VSBindingTreeController} -- a {@code @MessageMapping} controller that re-derives the
 * runtime id from {@code RuntimeViewsheetRef}, a STOMP-message-scoped bean populated only from a
 * native header on a live browser WebSocket session. Reached without one the id is null, and the
 * null lands in Ignite's affinity routing. This method is {@code @ClusterProxyKey}-routed and so
 * already holds a live id; the fix calls {@code VSBindingTreeControllerServiceProxy} with it.
 *
 * <p>The test drives the shortest path that still reaches the refresh: a clear-geographic change
 * with an unchanged source, so neither the auto-detect branch nor the process/execute branch runs.
 * Those branches are irrelevant to the id being threaded and only add mock surface.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChangeGeographicServiceTest {
   @Test
   void refreshesBindingTreeWithTheCallsOwnRuntimeIdNotANullSessionScopedOne() throws Exception {
      String id = "rt-changegeo-1";
      Principal principal = mock(Principal.class);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      ChangeGeographicEvent event = mock(ChangeGeographicEvent.class);
      when(event.name()).thenReturn("Chart1");
      when(event.table()).thenReturn("Orders");
      when(event.refName()).thenReturn("State");
      when(event.type()).thenReturn(ChangeGeographicService.CLEAR_GEOGRAPHIC);
      when(event.isDim()).thenReturn(true);

      VSChartInfo cinfo = mock(VSChartInfo.class);
      when(cinfo.clone()).thenReturn(mock(VSChartInfo.class));
      when(cinfo.getMapType()).thenReturn(null);

      ChartVSAssemblyInfo oinfo = mock(ChartVSAssemblyInfo.class);
      when(oinfo.getSourceInfo()).thenReturn(null);
      when(oinfo.getVSChartInfo()).thenReturn(cinfo);

      ChartVSAssemblyInfo ninfo = mock(ChartVSAssemblyInfo.class);
      when(ninfo.getSourceInfo()).thenReturn(null);
      when(ninfo.getVSChartInfo()).thenReturn(cinfo);

      // getChartInfo() returns the assembly info; the method clones it to hold the "before" state
      ChartVSAssemblyInfo chartInfoForClone = mock(ChartVSAssemblyInfo.class);
      when(chartInfoForClone.clone()).thenReturn(oinfo);

      Viewsheet vs = mock(Viewsheet.class);

      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getChartInfo()).thenReturn(chartInfoForClone);
      when(chart.getViewsheet()).thenReturn(vs);
      when(chart.getVSAssemblyInfo()).thenReturn(ninfo);
      when(chart.getAbsoluteName()).thenReturn("Chart1");

      when(vs.getAssembly("Chart1")).thenReturn(chart);

      ChartVSAssembly updated = mock(ChartVSAssembly.class);
      when(updated.getVSAssemblyInfo()).thenReturn(ninfo);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(eq(id), eq(principal))).thenReturn(rvs);

      VSBindingService bindingFactory = mock(VSBindingService.class);
      when(bindingFactory.createModel(chart)).thenReturn(mock(BindingModel.class));
      when(bindingFactory.updateAssembly(any(), eq(chart))).thenReturn(updated);

      VSAssemblyInfoHandler assemblyInfoHandler = mock(VSAssemblyInfoHandler.class);
      when(assemblyInfoHandler.handleSourceChanged(any(), anyString(), anyString(), any(), any(),
                                                   any())).thenReturn(false);

      VSChartHandler chartHandler = mock(VSChartHandler.class);
      when(chartHandler.fixMapInfo(any(), anyString(), anyString())).thenReturn(false);

      VSBindingTreeControllerServiceProxy vsBindingTreeService =
         mock(VSBindingTreeControllerServiceProxy.class);

      ChangeGeographicService service = new ChangeGeographicService(
         bindingFactory, mock(CoreLifecycleService.class), vsBindingTreeService,
         assemblyInfoHandler, chartHandler, mock(VSObjectModelFactoryService.class),
         viewsheetService, mock(ChartGeoInfoFactory.VSChartGeoInfoFactory.class),
         mock(VSChartBindingFactory.class));

      service.changeGeographic(id, event, principal, dispatcher, "");

      verify(vsBindingTreeService).getBinding(
         eq(id), any(RefreshBindingTreeEvent.class), eq(principal), eq(dispatcher));
      verify(vsBindingTreeService, never()).getBinding(
         isNull(), any(RefreshBindingTreeEvent.class), any(), any());
   }
}
