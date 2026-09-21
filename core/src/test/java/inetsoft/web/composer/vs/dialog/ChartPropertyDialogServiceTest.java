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
import inetsoft.uql.asset.AssemblyRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.VSCube;
import inetsoft.uql.viewsheet.VSDimension;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.model.vs.ChartAdvancedPaneModel;
import inetsoft.web.composer.model.vs.ChartPropertyDialogModel;
import inetsoft.web.composer.model.vs.HierarchyPropertyPaneModel;
import inetsoft.web.composer.model.vs.OutputColumnRefModel;
import inetsoft.web.composer.model.vs.VSDimensionMemberModel;
import inetsoft.web.composer.model.vs.VSDimensionModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.viewsheet.service.ChartPropertyService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.VSDialogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Point;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

   /**
    * Regression test for bug #76826: commit 9a572fc60 turned
    * {@code ChartPropertyDialogModel.getChartAdvancedPaneModel()} into an always-non-null
    * lazy-init getter, which silently made {@code getChartPropertyDialogModel0}'s
    * {@code if(chartAdvancedPaneModel == null)} guard dead code, so the real
    * {@code chartAssemblyInfo}-populated {@code ChartAdvancedPaneModel} (and its
    * {@code chartPlotOptionsPaneModel}) was never built -- crashing
    * ChartPlotOptionsPaneComponent's template on a null model.
    */
   @Test
   void getChartPropertyDialogModel0PopulatesChartPlotOptionsPaneModelForAFoundAssembly()
      throws Exception
   {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(new VSChartInfo());

      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(chartAssembly);
      when(chartAssembly.getVSAssemblyInfo()).thenReturn(info);
      when(chartAssembly.getDependedWSAssemblies()).thenReturn(new AssemblyRef[0]);
      when(dialogService.getAssemblyPosition(any(), any())).thenReturn(new Point(0, 0));
      when(dialogService.getAssemblySize(any(), any())).thenReturn(new Dimension(1, 1));
      when(vsObjectPropertyService.getSupportedTablePopComponents(any(), nullable(String.class), any(Boolean.class)))
         .thenReturn(new String[0]);

      ChartPropertyDialogModel result =
         service.getChartPropertyDialogModel0("Chart1", "Viewsheet1", null);

      assertNotNull(result.getChartAdvancedPaneModel().getChartPlotOptionsPaneModel(),
                     "chartPlotOptionsPaneModel must be populated for an ordinary, found chart, " +
                     "not left null by a dead getChartAdvancedPaneModel()==null guard");
   }

   /**
    * Parallel assertion for the {@code chartLinePaneModel} half of the same defect (bug #76826):
    * the identical dead-guard shape left every {@code ChartLinePaneModel} sent to the client as a
    * bare, all-defaults object, silently dropping every Line-tab setting.
    */
   @Test
   void getChartPropertyDialogModel0PopulatesChartLinePaneModelFromTheRealChartDescriptor()
      throws Exception
   {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(new VSChartInfo());
      PlotDescriptor plotDescriptor = info.getChartDescriptor().getPlotDescriptor();
      plotDescriptor.setFacetGrid(true);

      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(chartAssembly);
      when(chartAssembly.getVSAssemblyInfo()).thenReturn(info);
      when(chartAssembly.getDependedWSAssemblies()).thenReturn(new AssemblyRef[0]);
      when(dialogService.getAssemblyPosition(any(), any())).thenReturn(new Point(0, 0));
      when(dialogService.getAssemblySize(any(), any())).thenReturn(new Dimension(1, 1));
      when(vsObjectPropertyService.getSupportedTablePopComponents(any(), nullable(String.class), any(Boolean.class)))
         .thenReturn(new String[0]);

      ChartPropertyDialogModel result =
         service.getChartPropertyDialogModel0("Chart1", "Viewsheet1", null);

      assertTrue(result.getChartLinePaneModel().isFacetGrid(),
                 "chartLinePaneModel must reflect the real chart descriptor's facet grid setting, " +
                 "not a bare all-defaults object left by a dead getChartLinePaneModel()==null guard");
   }

   /**
    * Regression test for Redmine #76861 VCX-001: {@code setChartHierarchy} (the narrow write
    * {@code HierarchyDimensionService$ChartTarget} now uses) must commit the cube without ever
    * engaging {@code setChartPropertyModel}'s wide dialog-save dependencies -- the ones a
    * hierarchy-only write has no business touching, and where the reported NPE actually lived
    * ({@code chartPropertyService}/{@code vsChartHandler}/{@code dialogService}/
    * {@code vsBindingService}/{@code assemblyInfoHandler}/{@code trapService} are never even
    * called). A fresh chart whose ChartDescriptor was never populated through the Properties
    * dialog is exactly the scenario the reported 500 needed.
    */
   @Test
   void setChartHierarchyCommitsTheCubeWithoutTouchingTheWideDialogDependencies()
      throws Exception
   {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(new VSChartInfo());

      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(chartAssembly);
      when(chartAssembly.getVSAssemblyInfo()).thenReturn(info);
      when(vsObjectPropertyService.convertModelToVSDimension(any())).thenReturn(new VSDimension());

      OutputColumnRefModel regionColumn = new OutputColumnRefModel();
      regionColumn.setEntity("Customer");
      regionColumn.setAttribute("Region");
      VSDimensionMemberModel memberModel = new VSDimensionMemberModel();
      memberModel.setDataRef(regionColumn);
      VSDimensionModel dimensionModel = new VSDimensionModel();
      dimensionModel.setMembers(new VSDimensionMemberModel[] { memberModel });

      // The real caller (HierarchyDimensionService.add()) always submits a columnList that has
      // already had every dimension member's own column removed -- see setCube's class doc for
      // why a columnList that still contains one crashes either way (double-booked measure, or
      // an UnsupportedOperationException removing from Arrays.asList's fixed-size view).
      HierarchyPropertyPaneModel pane = new HierarchyPropertyPaneModel();
      pane.setColumnList(new OutputColumnRefModel[0]);
      pane.setDimensions(new VSDimensionModel[] { dimensionModel });

      service.setChartHierarchy("Viewsheet1", "Chart1", pane, "", null, commandDispatcher);

      ArgumentCaptor<ChartVSAssemblyInfo> captor = ArgumentCaptor.forClass(ChartVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         eq(rvs), captor.capture(), eq("Chart1"), eq("Chart1"), eq(""),
         nullable(Principal.class), eq(commandDispatcher), eq(true));
      assertTrue(captor.getValue().getXCube() instanceof VSCube,
                  "the committed assembly info must carry the built cube");

      verifyNoInteractions(chartPropertyService, vsChartHandler, dialogService, vsBindingService,
                           assemblyInfoHandler, trapService);
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
