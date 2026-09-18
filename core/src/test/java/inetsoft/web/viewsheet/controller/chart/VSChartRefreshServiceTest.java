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
package inetsoft.web.viewsheet.controller.chart;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.viewsheet.event.chart.VSChartRefreshEvent;
import inetsoft.web.viewsheet.controller.VSRefreshController;
import inetsoft.web.viewsheet.event.VSRefreshEvent;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76674, the shape PR #5252 fixed for {@code ModifyCalculateFieldService}.
 *
 * <p>{@code eventHandler} is {@code @ClusterProxyKey}-routed, so it knows the runtime id it is
 * executing for, and the lambda it hands to {@code processEvent} closes over that id. The
 * embedded-assembly branch nonetheless called the four-argument
 * {@code VSRefreshController.refreshViewsheet}, which re-derives the id from
 * {@code RuntimeViewsheetRef} -- a STOMP-message-scoped bean populated only from a native header
 * on a live browser WebSocket session, and null for any caller reached without one.
 *
 * <p>The embedded branch is the one that matters here because it is the only path in
 * {@code refreshChart} that refreshes the whole viewsheet rather than sending a single object
 * command, and therefore the only one that crosses into affinity-keyed cluster routing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSChartRefreshServiceTest {
   /**
    * The second verify is the one that fails against the unfixed code. The first alone would
    * not: the session-deriving overload is just as happy to run on a mock, and the null id it
    * would have carried is never examined in a test -- which is exactly why this defect class
    * kept surviving its own regression tests.
    */
   @Test
   void embeddedAssemblyRefreshUsesTheCallsOwnRuntimeIdNotANullSessionScopedOne() throws Exception {
      String runtimeId = "rt-chartrefresh-1";
      Principal principal = mock(Principal.class);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      VSChartRefreshEvent event = mock(VSChartRefreshEvent.class);
      when(event.getChartName()).thenReturn("Chart1");

      ChartVSAssemblyInfo chartInfo = mock(ChartVSAssemblyInfo.class);
      when(chartInfo.getAbsoluteName()).thenReturn("Chart1");

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      // the branch under test
      when(rvs.hasEmbeddedAssembly()).thenReturn(true);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());

      VSChartControllerService.VSChartStateInfo chartState =
         mock(VSChartControllerService.VSChartStateInfo.class);
      when(chartState.getChartAssemblyInfo()).thenReturn(chartInfo);
      when(chartState.getRuntimeViewsheet()).thenReturn(rvs);

      VSRefreshController vsRefreshController = mock(VSRefreshController.class);

      VSChartRefreshService service = new VSChartRefreshService(
         mock(CoreLifecycleService.class), mock(VSObjectModelFactoryService.class),
         mock(ViewsheetService.class), vsRefreshController,
         mock(VSChartAreasServiceProxy.class));

      try(MockedStatic<VSChartControllerService.VSChartStateInfo> states =
             mockStatic(VSChartControllerService.VSChartStateInfo.class))
      {
         states.when(() -> VSChartControllerService.VSChartStateInfo.createChartState(
            any(), any(), any(), anyString())).thenReturn(chartState);

         service.eventHandler(runtimeId, event, "", principal, dispatcher);
      }

      verify(vsRefreshController).refreshViewsheet(
         eq(runtimeId), any(VSRefreshEvent.class), eq(principal), eq(dispatcher), eq(""));
      verify(vsRefreshController, never()).refreshViewsheet(
         any(VSRefreshEvent.class), any(), any(), any());
   }
}
