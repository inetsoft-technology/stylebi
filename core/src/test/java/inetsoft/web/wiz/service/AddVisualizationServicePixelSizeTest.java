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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the merged chart's rendered SIZE, as distinct from its drop POSITION
 * (already covered by {@link AddVisualizationServiceGraphStalenessTest} and the grid-origin tests
 * in {@link WizDashboardServiceGridTest}): {@link AddVisualizationService#mergeViewsheet}
 * previously called only {@code setPixelOffset} on each cloned assembly, never
 * {@code setPixelSize} — so a dashboard tile's computed (spanCols, spanRows) footprint only ever
 * reserved grid spacing and never resized the actual chart, which is why merged charts kept
 * rendering at their original single-visualization size regardless of the tile they were placed
 * into. {@link AddVisualizationService#addVisualization} now takes an explicit
 * {@link Dimension} pixelSize parameter: non-null resizes the merged chart, null (the drag-and-
 * drop composer path's behavior) leaves its saved size untouched.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AddVisualizationServicePixelSizeTest {

   @Test
   void mergingAVisualizationAppliesTheGivenPixelSizeToTheMergedChart() throws Exception {
      Fixture f = new Fixture();
      Dimension requestedSize = new Dimension(1280, 840);

      f.service.addVisualization("rt-1", f.vizEntry, 0, 0, 1.0f, requestedSize, f.principal);

      assertEquals(requestedSize, ((ChartVSAssembly) f.dashVS.getAssembly("Chart1")).getPixelSize());
   }

   @Test
   void mergingAVisualizationLeavesTheChartsOriginalSizeWhenPixelSizeIsNull() throws Exception {
      Fixture f = new Fixture();
      Dimension originalSize = f.chart.getPixelSize();

      f.service.addVisualization("rt-1", f.vizEntry, 0, 0, 1.0f, null, f.principal);

      assertEquals(originalSize, ((ChartVSAssembly) f.dashVS.getAssembly("Chart1")).getPixelSize());
   }

   /** Shared mock scaffolding for merging a single-chart visualization into an empty dashboard. */
   private static final class Fixture {
      final Viewsheet dashVS = new Viewsheet(null, true, false, null, null);
      final ChartVSAssembly chart;
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
