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
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.wiz.model.ChartFormatRequest;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * setChartFormat mutates the live runtime chart in place. save_viewsheet clones the assembly from
 * the PERSISTED viewsheet (assetRepository.getSheet), NOT this runtime — so a format change left
 * only on the runtime (the chart title above the plot in particular) is silently dropped on save.
 * The service MUST commit the mutation back to the backing asset via persistViewsheet.
 * Regression test for the live "title still shows 'Chart' after set + save" bug (2026-06-25).
 */
@Tag("core")
class WizAutoBindingServiceSetChartFormatTest {
   private WizAutoBindingService service;
   private WizVsService wizVsService;
   private Viewsheet vs;
   private ChartVSAssemblyInfo info;
   private ViewsheetSandbox box;

   @BeforeEach
   void setUp() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      wizVsService = mock(WizVsService.class);
      // collaborators not used by setChartFormat; their classes can't be initialized in a plain
      // unit-test environment (no Spring context), so pass null instead of mocks.
      service = new WizAutoBindingService(
         viewsheetService, null, null, null, null, wizVsService);

      // getChartInfo() and getVSAssemblyInfo() both return the ChartVSAssemblyInfo for a chart;
      // one mock backs both. A title-only request leaves the descriptor null, so every axis/
      // scale/legend/marker block is guarded out.
      info = mock(ChartVSAssemblyInfo.class);
      when(info.getChartDescriptor()).thenReturn(null);
      when(info.getVSChartInfo()).thenReturn(null);

      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getChartInfo()).thenReturn(info);
      when(chart.getVSAssemblyInfo()).thenReturn(info);

      vs = mock(Viewsheet.class);
      when(vs.getAssembly("vs_1")).thenReturn(chart);

      box = mock(ViewsheetSandbox.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);
      when(wizVsService.fetchAssemblyData(anyString(), anyString(), any()))
         .thenReturn(new CreateViewsheetResult());
   }

   private static ChartFormatRequest titleRequest(String identifier) {
      ChartFormatRequest request = new ChartFormatRequest();
      request.setWizRuntimeId("rt-1");
      request.setAssemblyName("vs_1");
      request.setChartTitle("Contacts per Account");
      request.setViewsheetIdentifier(identifier);
      return request;
   }

   @Test
   void chartTitleIsAppliedAndMadeVisible() throws Exception {
      service.setChartFormat(titleRequest("visualizations-xyz"), null);

      verify(info).setTitleValue("Contacts per Account");
      verify(info).setTitleVisible(true);
   }

   @Test
   void chartTitleIsPersistedToTheBackingAssetSoSaveKeepsIt() throws Exception {
      // The crux: without this persist, save_viewsheet reads the stored asset (title "Chart") and
      // the runtime title is lost. Must write the runtime viewsheet back to its identifier.
      service.setChartFormat(titleRequest("visualizations-xyz"), null);

      verify(wizVsService).persistViewsheet(same(vs), eq("visualizations-xyz"), isNull());
   }

   @Test
   void noPersistWhenThereIsNoBackingIdentifier() throws Exception {
      // With no identifier there is no managed asset to update; persisting would mint a stray new
      // asset. The title still applies to the runtime; we just must not persist blindly.
      service.setChartFormat(titleRequest(null), null);

      verify(info).setTitleValue("Contacts per Account");
      verify(wizVsService, never()).persistViewsheet(any(), any(), any());
   }
}
