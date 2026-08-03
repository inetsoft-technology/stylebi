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
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetContent;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.security.Principal;
import java.util.HashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for a live bug: a saved visualization that holds MORE THAN ONE chart.
 *
 * <p>A saved visualization IS one chart — that is the model the rest of the product enforces
 * ({@code ViewsheetRuntimeController#findChartAssembly} resolves an asset to its FIRST
 * ChartVSAssembly, and open/verify/reload all report that single assembly). An asset can still end
 * up holding two, because {@code persistViewsheet} writes back whatever the runtime contains and a
 * re-bind of a reopened saved viz can leave the superseded chart behind.
 *
 * <p>{@code mergeViewsheet} cloned EVERY assembly, so the orphan came into the dashboard too: a
 * 5-chart board composed to 6 chart assemblies and the PDF export then failed its tile/caption
 * count check with "Dashboard has 6 top-level assemblies but 5 charts were requested". Observed
 * live on a board whose A1 chart had been re-bound after a dataset regeneration.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AddVisualizationServiceOrphanChartTest {

   @Test
   void mergesOnlyThePrimaryChartWhenTheSavedVisualizationHoldsAnOrphan() throws Exception {
      Fixture f = new Fixture();

      f.service.addVisualization("rt-1", f.vizEntry, 0, 0, 1.0f, null, f.principal);

      assertNotNull(f.dashVS.getAssembly("Chart1"), "the primary chart must be merged");
      assertNull(f.dashVS.getAssembly("Chart1_stale"),
         "the superseded chart must NOT be merged — it inflates the dashboard's tile count and " +
         "breaks the board PDF export's tile/caption check");

      long charts = java.util.Arrays.stream(f.dashVS.getAssemblies())
         .filter(a -> a instanceof ChartVSAssembly).count();
      assertEquals(1, charts, "exactly one chart assembly per merged visualization");
   }

   /** Shared mock scaffolding for merging a single-chart visualization into an empty dashboard. */
   private static final class Fixture {
      final Viewsheet dashVS = new Viewsheet(null, true, false, null, null);
      final ChartVSAssembly chart;
      final ChartVSAssembly orphan;
      final AssetEntry vizEntry;
      final Principal principal = mock(Principal.class);
      final AddVisualizationService service;

      Fixture() throws Exception {
         ViewsheetService vsService = mock(ViewsheetService.class);
         AssetRepository assetRepository = mock(AssetRepository.class);
         WsMergeService wsMergeService = mock(WsMergeService.class);
         SecurityEngine securityEngine = mock(SecurityEngine.class);

         when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);

         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(dashVS);
         when(rvs.getEntry()).thenReturn(null);
         ViewsheetSandbox box = mock(ViewsheetSandbox.class);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
         when(vsService.getViewsheet(eq("rt-1"), eq(principal))).thenReturn(rvs);

         AssetEntry vizWsEntry = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws1", null);
         Viewsheet vizVS = new Viewsheet(vizWsEntry);
         chart = new ChartVSAssembly(vizVS, "Chart1");
         vizVS.addAssembly(chart);
         // The ORPHAN: a superseded chart left behind in the same saved asset.
         orphan = new ChartVSAssembly(vizVS, "Chart1_stale");
         vizVS.addAssembly(orphan);

         vizEntry = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "viz1", null);
         when(assetRepository.getSheet(eq(vizEntry), eq(principal), eq(true), any(AssetContent.class)))
            .thenReturn(vizVS);
         Worksheet vizWS = new Worksheet();
         when(assetRepository.getSheet(eq(vizWsEntry), eq(principal), eq(true), any(AssetContent.class)))
            .thenReturn(vizWS);
         when(assetRepository.getSheet(any(AssetEntry.class), isNull(), eq(false), any(AssetContent.class)))
            .thenReturn(new Worksheet());
         when(wsMergeService.mergeWorksheet(eq(vizWS), any(Worksheet.class), anyString(), any()))
            .thenReturn(new HashMap<>());

         service = new AddVisualizationService(vsService, assetRepository, wsMergeService, securityEngine);
      }
   }
}
