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
import inetsoft.report.filter.HighlightGroup;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.vswizard.handler.SyncChartHandler;
import inetsoft.web.wiz.model.ChartColorsRequest;
import inetsoft.web.wiz.model.ChartFormatRequest;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WizAutoBindingService's mutation endpoints fill assemblyState after their last change; changeType
 * reads it after the highlight carry (turn-provenance spec 3.3.2).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizAutoBindingServiceAssemblyStateTest {
   // ---- format / colors: the mocked chain of the existing per-endpoint tests ----

   private static RuntimeViewsheet runtimeWith(ChartVSAssembly chart, ViewsheetService viewsheetService)
      throws Exception
   {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("vs_1")).thenReturn(chart);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));
      when(rvs.getID()).thenReturn("rt-1");
      when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);
      return rvs;
   }

   private static WizAutoBindingService mutationService(ViewsheetService viewsheetService,
                                                        WizVsService wizVsService)
      throws Exception
   {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      return new WizAutoBindingService(viewsheetService, null, null, null, null, wizVsService,
                                       securityEngine, null);
   }

   @Test
   void formatFillsTheStateAfterTheDataFetch() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      WizVsService wizVsService = mock(WizVsService.class);
      ChartVSAssemblyInfo info = mock(ChartVSAssemblyInfo.class);
      when(info.getChartDescriptor()).thenReturn(null);
      when(info.getVSChartInfo()).thenReturn(null);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getChartInfo()).thenReturn(info);
      when(chart.getVSAssemblyInfo()).thenReturn(info);
      RuntimeViewsheet rvs = runtimeWith(chart, viewsheetService);
      CreateViewsheetResult fetched = new CreateViewsheetResult();
      when(wizVsService.fetchAssemblyData(anyString(), anyString(), any())).thenReturn(fetched);
      ChartFormatRequest request = new ChartFormatRequest();
      request.setWizRuntimeId("rt-1");
      request.setAssemblyName("vs_1");
      request.setChartTitle("Contacts per Account");

      CreateViewsheetResult result = mutationService(viewsheetService, wizVsService).setChartFormat(request, null);

      assertSame(fetched, result);
      InOrder order = inOrder(wizVsService);
      order.verify(wizVsService).fetchAssemblyData("rt-1", "vs_1", null);
      order.verify(wizVsService).fillAssemblyState(same(rvs), same(chart), isNull(), same(fetched));
   }

   @Test
   void colorsFillsTheStateAfterTheDataFetch() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      WizVsService wizVsService = mock(WizVsService.class);
      VSChartAggregateRef yAgg = mock(VSChartAggregateRef.class);
      VSChartInfo chartInfo = mock(VSChartInfo.class);
      when(chartInfo.getColorField()).thenReturn(null);
      when(chartInfo.getYFields()).thenReturn(new ChartRef[] { yAgg });
      when(chartInfo.getXFields()).thenReturn(new ChartRef[0]);
      when(chartInfo.getRTYFields()).thenReturn(new ChartRef[] { yAgg });
      when(chartInfo.getRTXFields()).thenReturn(new ChartRef[0]);
      ChartVSAssemblyInfo info = mock(ChartVSAssemblyInfo.class);
      when(info.getVSChartInfo()).thenReturn(chartInfo);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getChartInfo()).thenReturn(info);
      RuntimeViewsheet rvs = runtimeWith(chart, viewsheetService);
      CreateViewsheetResult fetched = new CreateViewsheetResult();
      when(wizVsService.fetchAssemblyData(anyString(), anyString(), any())).thenReturn(fetched);
      ChartColorsRequest request = new ChartColorsRequest();
      request.setWizRuntimeId("rt-1");
      request.setAssemblyName("vs_1");
      request.setStaticColor("#d62728");

      CreateViewsheetResult result = mutationService(viewsheetService, wizVsService).setChartColors(request, null);

      assertSame(fetched, result);
      InOrder order = inOrder(wizVsService);
      order.verify(wizVsService).fetchAssemblyData("rt-1", "vs_1", null);
      order.verify(wizVsService).fillAssemblyState(same(rvs), same(chart), isNull(), same(fetched));
   }

   // ---- changeType: real charts, so the state is read from what the highlight carry wrote ----

   private record ChangeTypeHarness(WizAutoBindingService service, WizVsService wiz,
                                    SyncChartHandler syncChartHandler, ChartVSAssembly target)
   {
   }

   private static ChangeTypeHarness changeTypeHarness() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      WizVsService wiz = spy(new WizVsService(viewsheetService, mock(AssetRepository.class),
                                              mock(SecurityEngine.class), null, null, null));
      SyncChartHandler syncChartHandler = mock(SyncChartHandler.class);
      WizAutoBindingService service =
         new WizAutoBindingService(viewsheetService, null, null, null, null, wiz, null, syncChartHandler);

      Viewsheet vs = AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.worksheetEntry());
      ChartVSAssembly target = AssemblyStateFixtures.chart(vs, "chart2");
      vs.addAssembly(target);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt-1");
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());
      when(viewsheetService.getViewsheet("rt-1", null)).thenReturn(rvs);
      return new ChangeTypeHarness(service, wiz, syncChartHandler, target);
   }

   private static CreateViewsheetResult changeTypeResult() {
      CreateViewsheetResult result = new CreateViewsheetResult();
      result.setRuntimeId("rt-1");
      result.setAssemblyName("chart2");
      return result;
   }

   @Test
   void changeTypeStateReflectsTheHighlightCarriedOntoTheNewChart() throws Exception {
      ChangeTypeHarness h = changeTypeHarness();
      doReturn(new CreateViewsheetResult()).when(h.wiz()).fetchAssemblyData("rt-1", "chart2", null);
      ChartVSAssembly source = AssemblyStateFixtures.chart(
         AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.worksheetEntry()), "chart1");
      HighlightGroup carried = AssemblyStateFixtures.group(
         "big", AssemblyStateFixtures.condition("Sales", XCondition.GREATER_THAN, 100));
      AssemblyStateFixtures.measure(source).setHighlightGroup(carried);
      doAnswer(invocation -> {
         ChartVSAssembly to = invocation.getArgument(1);
         AssemblyStateFixtures.measure(to).setHighlightGroup(carried);
         return null;
      }).when(h.syncChartHandler()).syncHighlight(any(), any());
      CreateViewsheetResult result = changeTypeResult();

      h.service().attachChangeTypeData(result, source, null, null);

      verify(h.syncChartHandler()).syncHighlight(same(source), same(h.target()));
      List<CreateViewsheetResult.AssemblyHighlight> highlights = result.getAssemblyState().getHighlights();
      assertEquals(1, highlights.size());
      assertEquals("big", highlights.get(0).name());
   }

   @Test
   void changeTypeStateIsFilledEvenWhenTheDataFetchFails() throws Exception {
      ChangeTypeHarness h = changeTypeHarness();
      doThrow(new IllegalStateException("fetch failed")).when(h.wiz()).fetchAssemblyData("rt-1", "chart2", null);
      ChartVSAssembly source = AssemblyStateFixtures.chart(
         AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.worksheetEntry()), "chart1");
      CreateViewsheetResult result = changeTypeResult();

      h.service().attachChangeTypeData(result, source, null, null);

      assertNotNull(result.getAssemblyState());
      assertEquals("worksheet", result.getAssemblyState().getSource().kind());
   }

   @Test
   void changeTypeWithoutAnAssemblyNameFillsNothing() throws Exception {
      ChangeTypeHarness h = changeTypeHarness();
      CreateViewsheetResult result = new CreateViewsheetResult();

      h.service().attachChangeTypeData(result, null, null, null);

      assertNull(result.getAssemblyState());
      verify(h.wiz(), never()).fillAssemblyState(anyString(), anyString(), any(), any());
   }

   @Test
   void changeTypeFallbackFillSwallowsAFailure() throws Exception {
      ChangeTypeHarness h = changeTypeHarness();
      doThrow(new IllegalStateException("fill failed"))
         .when(h.wiz()).fillAssemblyState(anyString(), anyString(), any(), any());
      CreateViewsheetResult result = changeTypeResult();

      assertDoesNotThrow(() -> h.service().fillChangeTypeAssemblyState(result, "rt-1", (Principal) null));

      verify(h.wiz()).fillAssemblyState("rt-1", "chart2", (Principal) null, result);
   }
}
