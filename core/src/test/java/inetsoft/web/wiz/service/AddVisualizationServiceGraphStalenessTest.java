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
import org.mockito.InOrder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.HashMap;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for a dashboard-compose data-staleness bug: merging a visualization into a
 * dashboard's LIVE runtime viewsheet (as {@link WizDashboardService#composeDashboard} does, once
 * per chart, via {@link AddVisualizationService#addVisualization}) calls
 * {@code dashVS.addAssembly(clonedChart)} to attach the newly-merged chart. That call fires the
 * {@link ViewsheetSandbox}'s {@code actionPerformed} listener SYNCHRONOUSLY (confirmed via a live
 * stack-trace capture: {@code addAssembly -> AbstractSheet.fireEvent -> actionPerformed -> reset0
 * -> ... -> ChartVSAQuery#createBaseTableAssembly0}), which resolves the chart's own bound table
 * (e.g. "RADAR_OUT") through {@code dashVS.getBaseWorksheet()} — {@code dashVS}'s own cached
 * {@code ws} field, which is a SEPARATE object from the freshly-merged {@code dashWS} local
 * variable until that merged worksheet is both (a) persisted to the repository and (b) reloaded
 * back into {@code dashVS} via {@link Viewsheet#repopulateWorksheet}. Doing those two steps
 * AFTER the merge loop (where the bug was first, incorrectly, believed to be) is too late — the
 * listener already ran and logged "is not a data block, or the data block was not found!" by
 * then, even though the eventually-persisted worksheet is correct. The fix persists + repopulates
 * BEFORE {@code mergeViewsheet} ever calls {@code addAssembly}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AddVisualizationServiceGraphStalenessTest {

   @Test
   void mergingAVisualizationPersistsAndRepopulatesTheWorksheetBeforeAddingTheChartAssembly()
      throws Exception
   {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository assetRepository = mock(AssetRepository.class);
      WsMergeService wsMergeService = mock(WsMergeService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);

      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);

      // Dashboard runtime viewsheet: a freshly created wiz sheet with no backing worksheet yet
      // (the first visualization merged into it). Spied so addAssembly's call order relative to
      // the repository can be verified without disturbing its real merge/listener behavior.
      Viewsheet dashVS = spy(new Viewsheet(null, true, false, null, null));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(dashVS);
      when(rvs.getEntry()).thenReturn(null);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(vsService.getViewsheet(eq("rt-1"), eq(principal))).thenReturn(rvs);

      // The visualization being merged in: one chart assembly, "Chart1".
      AssetEntry vizWsEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws1", null);
      Viewsheet vizVS = new Viewsheet(vizWsEntry);
      ChartVSAssembly chart = new ChartVSAssembly(vizVS, "Chart1");
      vizVS.addAssembly(chart);

      AssetEntry vizEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "viz1", null);
      when(assetRepository.getSheet(eq(vizEntry), eq(principal), eq(true), any(AssetContent.class)))
         .thenReturn(vizVS);
      Worksheet vizWS = new Worksheet();
      when(assetRepository.getSheet(eq(vizWsEntry), eq(principal), eq(true), any(AssetContent.class)))
         .thenReturn(vizWS);

      // repopulateWorksheet's internal reload -- permission=false, principal=null (its own
      // hardcoded call), against whatever temp entry doAddVisualization creates for the
      // first-visualization branch.
      when(assetRepository.getSheet(any(AssetEntry.class), isNull(), eq(false), any(AssetContent.class)))
         .thenReturn(new Worksheet());

      when(wsMergeService.mergeWorksheet(eq(vizWS), any(Worksheet.class), anyString(), any()))
         .thenReturn(new HashMap<>());

      AddVisualizationService service =
         new AddVisualizationService(vsService, assetRepository, wsMergeService, securityEngine);

      service.addVisualization("rt-1", vizEntry, 0, 0, 1.0f, null, principal);

      // The merged worksheet must be persisted, and dashVS's own cached base worksheet
      // repopulated from it, BEFORE the chart assembly is added -- otherwise the sandbox's
      // addAssembly listener resolves the chart's bound table against the stale pre-merge
      // worksheet and logs the "not a data block" error even though the eventually-persisted
      // worksheet is correct.
      InOrder order = inOrder(assetRepository, dashVS);
      order.verify(assetRepository).setSheet(any(AssetEntry.class), any(Worksheet.class), eq(principal), anyBoolean());
      order.verify(assetRepository).getSheet(any(AssetEntry.class), isNull(), eq(false), any(AssetContent.class));
      order.verify(dashVS).addAssembly(any());

      // The merged chart must also have its cached VGraphPair invalidated -- defense in depth
      // for any graph cached from a prior merge into the same running dashboard.
      verify(box).clearGraph("Chart1");
   }
}
