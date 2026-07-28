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
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.wiz.model.ChartBinding;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import inetsoft.web.wiz.model.CreateVisualizationModel;
import inetsoft.web.wiz.model.MeasureFieldInfo;
import inetsoft.web.wiz.model.SimpleFieldInfo;
import inetsoft.web.wiz.model.VisualizationConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end regression (through the public {@link WizVsService#createViewsheet}, which is the
 * only reachable entry point to the private {@code applyChartBinding} for a brand-new chart
 * assembly — see {@code createAssembly -> createChartAssembly -> applyChartBinding}) for the
 * dual-Y-axis rendering fix.
 *
 * <p>Setting {@code secondaryY} on a chart measure alone only creates a second Y scale
 * ({@code DefaultGraphGenerator.fixCoordProperties}) — it does NOT, by itself, produce a real
 * side-by-side dual-axis render, because {@link inetsoft.uql.viewsheet.graph.AbstractChartInfo}'s
 * {@code separated} flag defaults to {@code true}, which routes rendering through
 * {@code SeparateGraphGenerator} (one independent small-multiples panel per measure — confirmed
 * live to render as two single-axis panels, not one combo chart). The fix forces
 * {@code chartInfo.setSeparatedGraph(false)} whenever any Y measure has {@code secondaryY=true},
 * which switches rendering to {@code DefaultGraphGenerator} (the renderer that supports one
 * shared coordinate with two Y scales).
 *
 * <p>The fix deliberately does NOT also set {@code labelOnSecondaryAxis} anywhere (chart- or
 * ref-level AxisDescriptor) — an earlier version of this fix did, "to help," and that regressed
 * the PRIMARY axis's own labels to invisible: {@code labelOnSecondaryAxis} ORs in
 * {@code AXIS_LABEL_OPPOSITE_SIDE}, and {@code RectCoord} hides the primary axis's labels
 * whenever that bit is set on either descriptor (see {@code applyChartBinding}'s Bug #74171
 * comment). Once on the shared coordinate, {@code RectCoord.createAxis()} already keeps both
 * Y-axes visible by default via {@code AxisSpec}'s default {@code AXIS_DOUBLE} style, with no
 * further configuration needed. This test both covers the actual fix (separated=false) and
 * guards against that labelOnSecondaryAxis regression coming back.
 *
 * <p>Follows the {@link WizVsServiceFilterCopyTest} construction pattern: a real {@link WizVsService}
 * wrapped in a Mockito spy so the heavy, separately-tested sandbox-execution machinery
 * ({@code executeAndExtract}, {@code collectFlatBinding}, {@code persistViewsheet}) can be stubbed
 * out, while {@code createAssembly}/{@code createChartAssembly}/{@code applyChartBinding} run for
 * real against a real {@link Viewsheet} and {@link VSChartInfo} — no reflection into the private
 * method is used or needed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceSecondaryYAxisRenderTest {

   @Test
   void chartWithASecondaryYMeasureForcesSharedCoordinateAndSecondaryAxisLabels() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      Principal user = mock(Principal.class);

      WizVsService real = new WizVsService(viewsheetService, engine, securityEngine);
      WizVsService service = spy(real);

      // Source worksheet resolveSourceContext must find, with a primary table assembly.
      AssetEntry sourceWsEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws1", null);
      Worksheet worksheet = new Worksheet();
      PhysicalBoundTableAssembly baseTable = new PhysicalBoundTableAssembly(worksheet, "BASE");
      worksheet.addAssembly(baseTable);
      worksheet.setPrimaryAssembly("BASE");
      when(engine.getSheet(any(AssetEntry.class), eq(user), eq(true), any()))
         .thenReturn(worksheet);

      // A brand-new temporary runtime (createdRuntimeId == true; Fix 1's RT-ref-staleness branch
      // is deliberately NOT exercised here -- see WizVsServiceRebindClearsStaleRuntimeChartRefsTest).
      when(viewsheetService.openTemporaryViewsheet(any(), any(), eq(user), any())).thenReturn("rt-1");
      Viewsheet sourceVs = mock(Viewsheet.class);
      when(sourceVs.getWizInfo()).thenReturn(new Viewsheet.WizInfo(true, null, null));
      // rvs is a plain mock -- its setViewsheet(targetVs) call (further down the real code path,
      // right after the assembly is created) does NOT make subsequent rvs.getViewsheet() calls
      // return targetVs; getViewsheetInfo().isMetadata() is read again off this SAME stubbed
      // sourceVs afterward, so it needs a non-null ViewsheetInfo too (the captured targetVs
      // argument below is unaffected by this and is still the real object the assembly was added
      // to).
      ViewsheetInfo sourceVsInfo = mock(ViewsheetInfo.class);
      when(sourceVsInfo.isMetadata()).thenReturn(false);
      when(sourceVs.getViewsheetInfo()).thenReturn(sourceVsInfo);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(sourceVs);
      when(rvs.getID()).thenReturn("rt-1");
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());
      when(viewsheetService.getViewsheet(anyString(), eq(user))).thenReturn(rvs);

      // Bypass the heavy, separately-tested sandbox-execution/binding-echo/persistence machinery --
      // this test is only about what applyChartBinding does to the freshly-built VSChartInfo.
      doReturn(new CreateViewsheetResult()).when(service).executeAndExtract(any(), any(), anyInt());
      doReturn(null).when(service).collectFlatBinding(any());
      doReturn("vs-identifier").when(service).persistViewsheet(any(), any(), any());

      // Two Y measures: "Sales" on the secondary axis, "Profit" left on the primary.
      MeasureFieldInfo salesOnSecondary = new MeasureFieldInfo();
      salesOnSecondary.setField("Sales");
      salesOnSecondary.setAggregateFormula("Sum");
      salesOnSecondary.setSecondaryY(true);

      MeasureFieldInfo profitOnPrimary = new MeasureFieldInfo();
      profitOnPrimary.setField("Profit");
      profitOnPrimary.setAggregateFormula("Sum");
      profitOnPrimary.setSecondaryY(false);

      SimpleFieldInfo category = new SimpleFieldInfo();
      category.setField("Category");

      ChartBinding binding = new ChartBinding();
      binding.setX(List.of(category));
      binding.setY(List.of(salesOnSecondary, profitOnPrimary));

      VisualizationConfig.DataSource dataSource = new VisualizationConfig.DataSource();
      dataSource.setSource(sourceWsEntry.toIdentifier());

      VisualizationConfig config = new VisualizationConfig();
      config.setTitle("Sales and Profit by Category");
      config.setData(dataSource);
      config.setBindingInfo(binding);

      CreateVisualizationModel model = new CreateVisualizationModel();
      model.setVisualizationType("bar");
      model.setConfig(config);

      CreateViewsheetResult result = service.createViewsheet(model, user);

      ArgumentCaptor<Viewsheet> targetVsCaptor = ArgumentCaptor.forClass(Viewsheet.class);
      verify(rvs).setViewsheet(targetVsCaptor.capture());
      Viewsheet targetVs = targetVsCaptor.getValue();

      ChartVSAssembly chartAssembly =
         (ChartVSAssembly) targetVs.getAssembly(result.getAssemblyName());
      assertNotNull(chartAssembly, "chart assembly must have been added to the target viewsheet");
      VSChartInfo chartInfo = chartAssembly.getVSChartInfo();
      assertNotNull(chartInfo);

      // The core render fix: a shared coordinate (NOT one small-multiples panel per measure).
      assertFalse(chartInfo.isSeparatedGraph(),
                  "a chart with a secondaryY measure must render through DefaultGraphGenerator, " +
                  "not SeparateGraphGenerator");

      // Regression guard: the fix must NOT set labelOnSecondaryAxis on the chart-level
      // AxisDescriptor. RectCoord already keeps both Y-axes visible via AxisSpec's default
      // AXIS_DOUBLE style once separated=false; setting labelOnSecondaryAxis here ORs in
      // AXIS_LABEL_OPPOSITE_SIDE, which hides the PRIMARY axis's own labels entirely (a real
      // regression an earlier version of this fix introduced -- see applyChartBinding's #74171
      // comment).
      assertFalse(chartInfo.getAxisDescriptor().isLabelOnSecondaryAxis(),
                  "the chart-level AxisDescriptor must stay untouched -- setting " +
                  "labelOnSecondaryAxis here hides the primary axis's own labels (regression, " +
                  "see #74171)");

      ChartRef[] yFields = chartInfo.getYFields();
      assertEquals(2, yFields.length);

      VSChartAggregateRef secondaryRef = null;
      VSChartAggregateRef primaryRef = null;

      for(ChartRef ref : yFields) {
         VSChartAggregateRef agg = (VSChartAggregateRef) ref;

         if(agg.isSecondaryY()) {
            secondaryRef = agg;
         }
         else {
            primaryRef = agg;
         }
      }

      assertNotNull(secondaryRef, "expected one Y measure with secondaryY=true (Sales)");
      assertNotNull(primaryRef, "expected one Y measure with secondaryY=false (Profit)");

      // Regression guard: the primary (non-secondaryY) measure's OWN ref-level AxisDescriptor must
      // NOT be flipped. An earlier version of this fix did flip it defensively, which hid the
      // primary axis's own labels entirely (labelOnSecondaryAxis on a linear measure axis hides
      // that axis -- see the #74171 comment in applyChartBinding). Only the chart-level descriptor
      // (asserted above) should carry labelOnSecondaryAxis=true.
      assertFalse(primaryRef.getAxisDescriptor().isLabelOnSecondaryAxis(),
                  "the non-secondaryY measure's own ref-level AxisDescriptor must stay untouched, " +
                  "else its axis labels are hidden entirely (regression -- see #74171)");
   }
}
