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
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.AestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.wiz.model.ChartAestheticModel;
import inetsoft.web.wiz.model.ChartAestheticModelRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * /chart/aestheticModel is what makes a colour request a decision rather than a guess: the caller cannot
 * tell staticColor from measureColors from a palette without knowing which field colours the chart. These
 * tests pin the three answers it has to distinguish, since a wrong one silently sends the caller down the
 * branch whose parameters cannot apply.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizAutoBindingServiceAestheticModelTest {
   private WizAutoBindingService service;
   private WizVsService wizVsService;
   private RuntimeViewsheet rvs;
   private VSChartInfo vsChartInfo;

   @BeforeEach
   void setUp() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      wizVsService = mock(WizVsService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      service = new WizAutoBindingService(
         viewsheetService, null, null, null, null, wizVsService, securityEngine, null);

      vsChartInfo = mock(VSChartInfo.class);
      ChartVSAssemblyInfo info = mock(ChartVSAssemblyInfo.class);
      when(info.getVSChartInfo()).thenReturn(vsChartInfo);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getChartInfo()).thenReturn(info);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("vs_1")).thenReturn(chart);
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt-1");
      when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);
   }

   private static ChartAestheticModelRequest request() {
      ChartAestheticModelRequest request = new ChartAestheticModelRequest();
      request.setWizRuntimeId("rt-1");
      request.setAssemblyName("vs_1");
      return request;
   }

   private void bindColorField(Object dataRef) {
      AestheticRef colorField = mock(AestheticRef.class);
      when(colorField.getDataRef()).thenReturn((inetsoft.uql.erm.DataRef) dataRef);
      when(vsChartInfo.getColorField()).thenReturn(colorField);
   }

   @Test
   void reportsAnEmptyColorAestheticAsNullSoTheCallerReachesForStaticOrMeasureColors() throws Exception {
      when(vsChartInfo.getColorField()).thenReturn(null);

      ChartAestheticModel model = service.getChartAestheticModel(request(), null);

      assertNull(model.getColorField());
      assertNull(model.getColorValues(), "no color field means there is nothing to enumerate");
      // No point paying for a dataset read when no dimension can consume the values.
      verify(wizVsService, never()).collectColorValues(any(), anyString(), anyString());
   }

   @Test
   void reportsAMeasureOnColorAsAContinuousScaleAndSkipsTheValueRead() throws Exception {
      VSChartAggregateRef measure = mock(VSChartAggregateRef.class);
      when(measure.getFullName()).thenReturn("Sum(sales)");
      bindColorField(measure);

      ChartAestheticModel model = service.getChartAestheticModel(request(), null);

      assertNotNull(model.getColorField());
      assertEquals("measure", model.getColorField().getRole());
      assertEquals("Sum(sales)", model.getColorField().getFullName());
      // Per-value colours are meaningless on a gradient, so the values are not read at all.
      assertNull(model.getColorValues());
      verify(wizVsService, never()).collectColorValues(any(), anyString(), anyString());
   }

   @Test
   void reportsADimensionOnColorWithItsValues() throws Exception {
      VSChartDimensionRef dimension = mock(VSChartDimensionRef.class);
      when(dimension.getFullName()).thenReturn("STATE");
      bindColorField(dimension);
      when(wizVsService.collectColorValues(rvs, "vs_1", "STATE"))
         .thenReturn(new ChartAestheticModel.ColorValues(List.of("CA", "NY"), false));

      ChartAestheticModel model = service.getChartAestheticModel(request(), null);

      assertEquals("dimension", model.getColorField().getRole());
      assertEquals(List.of("CA", "NY"), model.getColorValues().getValues());
      assertEquals(false, model.getColorValues().isTruncated());
   }

   @Test
   void echoesTheLiveRuntimeIdSoARestoredRuntimeIsNotLost() throws Exception {
      when(rvs.getID()).thenReturn("rt-restored");

      assertEquals("rt-restored", service.getChartAestheticModel(request(), null).getRuntimeId());
   }

   @Test
   void prefersRuntimeAestheticAggregatesAndFallsBackToTheDesignRefs() throws Exception {
      VSChartAggregateRef design = mock(VSChartAggregateRef.class);
      when(design.getFullName()).thenReturn("Sum(design)");
      VSChartAggregateRef runtime = mock(VSChartAggregateRef.class);
      when(runtime.getFullName()).thenReturn("Sum(runtime)");
      when(vsChartInfo.getAestheticAggregateRefs(true)).thenReturn(new ArrayList<>(List.of(runtime)));
      when(vsChartInfo.getAestheticAggregateRefs(false)).thenReturn(new ArrayList<>(List.of(design)));

      // Runtime is what the renderer reads, and the two diverge after a chart-type change.
      assertEquals("Sum(runtime)",
         service.getChartAestheticModel(request(), null).getAestheticAggregates().get(0).getFullName());

      when(vsChartInfo.getAestheticAggregateRefs(true)).thenReturn(new ArrayList<>());

      assertEquals("Sum(design)",
         service.getChartAestheticModel(request(), null).getAestheticAggregates().get(0).getFullName());
   }

   @Test
   void deduplicatesAggregatesByFullNameAndDropsUnnamedOnes() throws Exception {
      VSChartAggregateRef first = mock(VSChartAggregateRef.class);
      when(first.getFullName()).thenReturn("Sum(sales)");
      VSChartAggregateRef duplicate = mock(VSChartAggregateRef.class);
      when(duplicate.getFullName()).thenReturn("Sum(sales)");
      VSChartAggregateRef unnamed = mock(VSChartAggregateRef.class);
      when(unnamed.getFullName()).thenReturn("");
      when(vsChartInfo.getAestheticAggregateRefs(anyBoolean()))
         .thenReturn(new ArrayList<>(List.of(first, duplicate, unnamed)));

      List<ChartAestheticModel.AestheticAggregate> aggregates =
         service.getChartAestheticModel(request(), null).getAestheticAggregates();

      // The caller keys measureColors by these strings, so a duplicate reads as two different targets and
      // an empty one as a nameless target it can never match.
      assertEquals(1, aggregates.size());
      assertEquals("Sum(sales)", aggregates.get(0).getFullName());
   }

   @Test
   void returnsAnEmptyAggregateListRatherThanNullWhenTheChartHasNoBindingInfo() throws Exception {
      when(vsChartInfo.getAestheticAggregateRefs(anyBoolean())).thenReturn(null);

      ChartAestheticModel model = service.getChartAestheticModel(request(), null);

      // The caller treats an empty list as authoritative ("nothing here can carry a colour"), so it must
      // never have to distinguish null from empty on this field.
      assertNotNull(model.getAestheticAggregates());
      assertTrue(model.getAestheticAggregates().isEmpty());
   }
}
