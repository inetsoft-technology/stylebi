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
import inetsoft.graph.aesthetic.ColorFrame;
import inetsoft.graph.aesthetic.StaticColorFrame;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.wiz.model.ChartColorsRequest;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * setChartColors mutates the runtime chart info in place. The sandbox's cached VGraphPair
 * holds a reference to that same VSChartInfo, so its staleness check (equalsContent against
 * itself) can never detect the mutation — the service MUST explicitly clear the cached graph
 * or every subsequent render (including brand-new embed connections) serves the stale colors.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizAutoBindingServiceSetChartColorsTest {
   private WizAutoBindingService service;
   private ViewsheetSandbox box;
   private VSChartAggregateRef yAgg;
   private VSChartAggregateRef rtYAgg;
   private SecurityEngine securityEngine;
   private ViewsheetService viewsheetService;

   @BeforeEach
   void setUp() throws Exception {
      viewsheetService = mock(ViewsheetService.class);
      WizVsService wizVsService = mock(WizVsService.class);
      // collaborators not used by setChartColors; their classes can't be initialized in
      // a plain unit-test environment (no Spring context), so pass null instead of mocks.
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      service = new WizAutoBindingService(
         viewsheetService, null, null, null, null, wizVsService, securityEngine);

      // Real Viewsheet/assembly/info objects need SreeEnv (Spring context), so mock the
      // whole chain and hand the service a mock measure ref to capture the applied frame.
      yAgg = mock(VSChartAggregateRef.class);
      rtYAgg = mock(VSChartAggregateRef.class);
      VSChartInfo vsChartInfo = mock(VSChartInfo.class);
      when(vsChartInfo.getColorField()).thenReturn(null);
      when(vsChartInfo.getYFields()).thenReturn(new ChartRef[] { yAgg });
      when(vsChartInfo.getXFields()).thenReturn(new ChartRef[0]);
      when(vsChartInfo.getRTYFields()).thenReturn(new ChartRef[] { rtYAgg });
      when(vsChartInfo.getRTXFields()).thenReturn(new ChartRef[0]);

      ChartVSAssemblyInfo info = mock(ChartVSAssemblyInfo.class);
      when(info.getVSChartInfo()).thenReturn(vsChartInfo);

      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getChartInfo()).thenReturn(info);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("vs_1")).thenReturn(chart);

      box = mock(ViewsheetSandbox.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(rvs.getID()).thenReturn("rt-1");
      when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);
      when(wizVsService.fetchAssemblyData(anyString(), anyString(), any(Principal.class)))
         .thenReturn(new CreateViewsheetResult());
   }

   private static ChartColorsRequest staticRed() {
      ChartColorsRequest request = new ChartColorsRequest();
      request.setWizRuntimeId("rt-1");
      request.setAssemblyName("vs_1");
      request.setStaticColor("#d62728");
      return request;
   }

   @Test
   void staticColorIsAppliedToTheMeasureRef() throws Exception {
      service.setChartColors(staticRed(), null);

      ArgumentCaptor<ColorFrame> captor = ArgumentCaptor.forClass(ColorFrame.class);
      verify(yAgg).setColorFrame(captor.capture());
      StaticColorFrame frame = assertInstanceOf(StaticColorFrame.class, captor.getValue());
      assertEquals(new Color(0xd6, 0x27, 0x28), frame.getColor(),
         "staticColor must land on the bound measure's color frame");
   }

   @Test
   void staticColorIsAppliedToTheRuntimeMeasureRefTheRendererActuallyReads() throws Exception {
      // The renderer (VSFrameVisitor.getAggregates) reads getRTYFields()/getRTXFields() — the
      // runtime clones — not the design refs. Painting only the design refs leaves the next
      // render (even after a cache clear) on the old color.
      service.setChartColors(staticRed(), null);

      ArgumentCaptor<ColorFrame> captor = ArgumentCaptor.forClass(ColorFrame.class);
      verify(rtYAgg).setColorFrame(captor.capture());
      StaticColorFrame frame = assertInstanceOf(StaticColorFrame.class, captor.getValue());
      assertEquals(new Color(0xd6, 0x27, 0x28), frame.getColor(),
         "staticColor must also land on the runtime measure ref used for rendering");
   }

   @Test
   void staticColorClearsTheCachedGraphSoTheChangeActuallyRenders() throws Exception {
      service.setChartColors(staticRed(), null);

      verify(box).clearGraph("vs_1");
   }

   @Test
   void deniedPermissionThrowsAndSkipsRuntimeLookupAndMutation() throws Exception {
      // The action gate is the first statement in setChartColors, before the runtime is even
      // resolved. A denied caller must throw and touch nothing.
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(false);

      assertThrows(SecurityException.class, () -> service.setChartColors(staticRed(), null));

      verify(viewsheetService, never()).getViewsheet(anyString(), any());
      verify(yAgg, never()).setColorFrame(any());
      verify(rtYAgg, never()).setColorFrame(any());
      verify(box, never()).clearGraph(anyString());
   }
}
