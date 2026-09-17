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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.report.composition.region.ChartAreaInfo;
import inetsoft.report.composition.region.TitleArea;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.CompositeTextFormat;
import inetsoft.uql.viewsheet.graph.TitleDescriptor;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.graph.model.dialog.TitleFormatDialogModel;
import inetsoft.web.viewsheet.controller.chart.VSChartAreasService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redmine #76732 VCC-001. {@code RegionPropertyDialogService.getTitleFormatDialogModel} is the
 * shared crash site: {@code ChartArea} only builds a {@code TitleArea} for an axis whose title is
 * currently visible, and this method dereferenced a hidden axis's null {@code TitleArea}
 * unconditionally. This is a second, independent caller of that same method beyond the wiz-agent
 * path ({@code ChartRegionPropertyService}, covered separately) -- the native Composer's own
 * {@code RegionPropertyDialogController} REST endpoint calls straight through to this service, so
 * the fix has to live here too, not only in the wiz-agent's upstream guard.
 */
@Tag("core")
class RegionPropertyDialogServiceTest {
   @Test
   void refusesAHiddenTitleInsteadOfNpeing() throws Exception {
      Harness h = harness();
      doReturn(mock(ChartArea.class)).when(h.service)
         .getChartArea(h.rvs, h.chartAssembly, "");
      when(h.regionHandler.getTitleArea(any(), eq("y"))).thenReturn(null);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.getTitleFormatDialogModel("rt1", "Chart1", "y", "", principal()));

      assertTrue(thrown.getMessage().contains("y"));
      assertTrue(thrown.getMessage().toLowerCase().contains("not visible"));
   }

   /** The working path must be unaffected: a visible title still returns its format model. */
   @Test
   void stillGetsATitleFormatModelWhenVisible() throws Exception {
      Harness h = harness();
      ChartArea chartArea = mock(ChartArea.class);
      TitleArea titleArea = mock(TitleArea.class);
      ChartAreaInfo areaInfo = mock(ChartAreaInfo.class);
      when(areaInfo.getProperty("titlename")).thenReturn("Revenue");
      when(titleArea.getChartAreaInfo()).thenReturn(areaInfo);
      when(h.titleDescriptor.getTextFormat()).thenReturn(mock(CompositeTextFormat.class));

      doReturn(chartArea).when(h.service).getChartArea(h.rvs, h.chartAssembly, "");
      when(h.regionHandler.getTitleArea(chartArea, "y")).thenReturn(titleArea);

      TitleFormatDialogModel model =
         h.service.getTitleFormatDialogModel("rt1", "Chart1", "y", "", principal());

      assertEquals("Revenue", model.getOldTitle());
   }

   private record Harness(RegionPropertyDialogService service, RuntimeViewsheet rvs,
                          ChartVSAssembly chartAssembly, ChartRegionHandler regionHandler,
                          TitleDescriptor titleDescriptor) {}

   private static Harness harness() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      ChartVSAssembly chartAssembly = mock(ChartVSAssembly.class);
      ChartVSAssemblyInfo assemblyInfo = mock(ChartVSAssemblyInfo.class);
      ChartDescriptor descriptor = mock(ChartDescriptor.class);
      TitleDescriptor titleDescriptor = mock(TitleDescriptor.class);
      ChartRegionHandler regionHandler = mock(ChartRegionHandler.class);

      when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getAssembly("Chart1")).thenReturn(chartAssembly);
      when(chartAssembly.getVSAssemblyInfo()).thenReturn(assemblyInfo);
      when(assemblyInfo.getChartDescriptor()).thenReturn(descriptor);
      when(regionHandler.getTitleDescriptor(descriptor, "y")).thenReturn(titleDescriptor);

      RegionPropertyDialogService service = spy(new RegionPropertyDialogService(
         mock(VSObjectPropertyService.class), viewsheetService, mock(VSBindingService.class),
         mock(VSChartAreasService.class), regionHandler));

      return new Harness(service, rvs, chartAssembly, regionHandler, titleDescriptor);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
