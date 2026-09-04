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
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.model.vs.ChartAdvancedPaneModel;
import inetsoft.web.composer.model.vs.ChartPropertyDialogModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.viewsheet.service.ChartPropertyService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.VSDialogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

/**
 * The glossyEffect+sparkline mutual exclusion and enableDrilling-on-Mekko gate (parity audit L7,
 * mirroring chart-advanced-pane.component.html/.ts). Both are normalized -- not refused -- right
 * after the try block that resolves {@code vsChartInfo}, before anything else in the patch is
 * applied: a human can never actually express either combination through the Composer UI (both
 * are disabled-checkbox states), so a stale value that got POSTed back is silently corrected
 * rather than rejected. Because the checks mutate the same {@code ChartAdvancedPaneModel}
 * instance that was passed in, the tests below can observe normalization directly on
 * {@code advancedPane} even though the rest of the (heavy) apply path isn't fully mocked here and
 * may itself throw further down -- that's caught and ignored since it's out of scope.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@Tag("core")
class ChartPropertyDialogServiceTest {
   @BeforeEach
   void setup() {
      service = new ChartPropertyDialogService(
         vsObjectPropertyService, chartPropertyService, vsChartHandler, dialogService,
         viewsheetService, vsBindingService, assemblyInfoHandler, trapService);
   }

   @Test
   void normalizesGlossyEffectWhenSparklineSupportedAndOn() throws Exception {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(new VSChartInfo());

      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(chartAssembly);
      when(chartAssembly.getVSAssemblyInfo()).thenReturn(info);
      // Isolate the sparkline clause under test: glossyEffectSupported=true so the sibling
      // !glossyEffectSupported clause (added later, see normalizesGlossyEffectWhenNotSupported
      // below) never fires here.
      when(chartPropertyService.isSupported(any(), eq("effectEnabled"), eq(false))).thenReturn(true);

      ChartPropertyDialogModel model = new ChartPropertyDialogModel();
      ChartAdvancedPaneModel advancedPane = new ChartAdvancedPaneModel();
      // Match ChartDescriptor's own defaults (both true) so the unrelated
      // sortOthersLast/rankPerGroup 'clear shared frames' branch does not fire.
      advancedPane.setSortOthersLast(true);
      advancedPane.setRankPerGroup(true);
      advancedPane.setGlossyEffect(true);
      advancedPane.setSparklineSupported(true);
      advancedPane.setSparkline(true);
      model.setChartAdvancedPaneModel(advancedPane);

      try {
         service.setChartPropertyModel("Viewsheet1", "Chart1", model, "", null, commandDispatcher);
      }
      catch(Exception ignoredUnrelatedApplyPathFailure) {
         // Setup here is intentionally minimal -- only enough to reach the normalization check
         // under test. Anything past it is out of scope for this test.
      }

      assertFalse(advancedPane.isGlossyEffect(),
                   "glossyEffect must be normalized to false when sparkline wins the gate");
      assertTrue(advancedPane.isSparkline(), "sparkline itself must be left untouched");
   }

   /** Guards against over-normalization: when sparkline isn't supported, its value never applies -- so glossyEffect should not be cleared either. */
   @Test
   void doesNotNormalizeGlossyEffectWhenSparklineIsNotSupported() throws Exception {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(new VSChartInfo());

      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(chartAssembly);
      when(chartAssembly.getVSAssemblyInfo()).thenReturn(info);
      // Isolate the sparkline clause: glossyEffectSupported=true so this test's negative result
      // is actually exercising the sparkline gate, not the sibling !glossyEffectSupported clause.
      when(chartPropertyService.isSupported(any(), eq("effectEnabled"), eq(false))).thenReturn(true);

      ChartPropertyDialogModel model = new ChartPropertyDialogModel();
      ChartAdvancedPaneModel advancedPane = new ChartAdvancedPaneModel();
      advancedPane.setSortOthersLast(true);
      advancedPane.setRankPerGroup(true);
      advancedPane.setGlossyEffect(true);
      advancedPane.setSparklineSupported(false);
      advancedPane.setSparkline(true);
      model.setChartAdvancedPaneModel(advancedPane);

      try {
         service.setChartPropertyModel("Viewsheet1", "Chart1", model, "", null, commandDispatcher);
      }
      catch(Exception ignoredUnrelatedApplyPathFailure) {
         // Out of scope for this test; see class javadoc.
      }

      assertTrue(advancedPane.isGlossyEffect(),
                  "glossyEffect must not be normalized when sparklineSupported is false");
   }

   /**
    * Mirrors the other half of chart-advanced-pane.component.html's disabled condition:
    * {@code [disabled]="!model.glossyEffectSupported || (model.sparklineSupported && model.sparkline)"}.
    * A stale glossyEffect=true left over from a chart-type change that dropped support (e.g.
    * effectEnabled no longer applicable to the new chart style) must be normalized even when the
    * sparkline clause never fires.
    */
   @Test
   void normalizesGlossyEffectWhenNotSupported() throws Exception {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(new VSChartInfo());

      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(chartAssembly);
      when(chartAssembly.getVSAssemblyInfo()).thenReturn(info);
      when(chartPropertyService.isSupported(any(), eq("effectEnabled"), eq(false))).thenReturn(false);

      ChartPropertyDialogModel model = new ChartPropertyDialogModel();
      ChartAdvancedPaneModel advancedPane = new ChartAdvancedPaneModel();
      advancedPane.setSortOthersLast(true);
      advancedPane.setRankPerGroup(true);
      advancedPane.setGlossyEffect(true);
      advancedPane.setSparklineSupported(false);
      advancedPane.setSparkline(false);
      model.setChartAdvancedPaneModel(advancedPane);

      try {
         service.setChartPropertyModel("Viewsheet1", "Chart1", model, "", null, commandDispatcher);
      }
      catch(Exception ignoredUnrelatedApplyPathFailure) {
         // Out of scope for this test; see class javadoc.
      }

      assertFalse(advancedPane.isGlossyEffect(),
                   "glossyEffect must be normalized to false when glossyEffectSupported is false, " +
                   "even with the sparkline clause never firing");
   }

   @Test
   void normalizesEnableDrillingOnAMekkoChart() throws Exception {
      VSChartInfo chartInfo = new VSChartInfo();
      chartInfo.setChartType(GraphTypes.CHART_MEKKO);
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(chartInfo);

      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(chartAssembly);
      when(chartAssembly.getVSAssemblyInfo()).thenReturn(info);

      ChartPropertyDialogModel model = new ChartPropertyDialogModel();
      ChartAdvancedPaneModel advancedPane = new ChartAdvancedPaneModel();
      // Match ChartDescriptor's own defaults (both true) so the unrelated
      // sortOthersLast/rankPerGroup 'clear shared frames' branch does not fire.
      advancedPane.setSortOthersLast(true);
      advancedPane.setRankPerGroup(true);
      advancedPane.setEnableDrilling(true);
      model.setChartAdvancedPaneModel(advancedPane);

      try {
         service.setChartPropertyModel("Viewsheet1", "Chart1", model, "", null, commandDispatcher);
      }
      catch(Exception ignoredUnrelatedApplyPathFailure) {
         // Out of scope for this test; see class javadoc.
      }

      assertFalse(advancedPane.isEnableDrilling(),
                   "enableDrilling must be normalized to false on a Mekko chart");
   }

   /** Guards against over-normalization: drilling stays settable on a non-Mekko chart type. */
   @Test
   void enableDrillingAloneIsNotNormalizedOnANonMekkoChart() throws Exception {
      VSChartInfo chartInfo = new VSChartInfo();
      chartInfo.setChartType(GraphTypes.CHART_BAR);
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(chartInfo);

      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(chartAssembly);
      when(chartAssembly.getVSAssemblyInfo()).thenReturn(info);

      ChartPropertyDialogModel model = new ChartPropertyDialogModel();
      ChartAdvancedPaneModel advancedPane = new ChartAdvancedPaneModel();
      // Match ChartDescriptor's own defaults (both true) so the unrelated
      // sortOthersLast/rankPerGroup 'clear shared frames' branch does not fire.
      advancedPane.setSortOthersLast(true);
      advancedPane.setRankPerGroup(true);
      advancedPane.setEnableDrilling(true);
      model.setChartAdvancedPaneModel(advancedPane);

      try {
         service.setChartPropertyModel("Viewsheet1", "Chart1", model, "", null, commandDispatcher);
      }
      catch(Exception ignoredUnrelatedApplyPathFailure) {
         // Out of scope for this test; see class javadoc.
      }

      assertTrue(advancedPane.isEnableDrilling(),
                  "enableDrilling must not be normalized on a bar chart");
   }

   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock ChartPropertyService chartPropertyService;
   @Mock VSChartHandler vsChartHandler;
   @Mock VSDialogService dialogService;
   @Mock ViewsheetService viewsheetService;
   @Mock VSBindingService vsBindingService;
   @Mock VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock VSTrapService trapService;
   @Mock CommandDispatcher commandDispatcher;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock ChartVSAssembly chartAssembly;

   private ChartPropertyDialogService service;
}
