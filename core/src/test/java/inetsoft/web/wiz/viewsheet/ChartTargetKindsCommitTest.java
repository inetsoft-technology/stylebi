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
package inetsoft.web.wiz.viewsheet;

import inetsoft.graph.GraphConstants;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.adhoc.model.property.ColorInfo;
import inetsoft.web.adhoc.model.property.MeasureInfo;
import inetsoft.web.adhoc.model.property.TargetInfo;
import inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import inetsoft.web.composer.model.vs.ChartAdvancedPaneModel;
import inetsoft.web.composer.model.vs.ChartPropertyDialogModel;
import inetsoft.web.composer.model.vs.ChartTargetLinesPaneModel;
import inetsoft.web.composer.vs.dialog.ChartPropertyDialogService;
import inetsoft.web.viewsheet.service.ChartPropertyService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #80116. The unit tests in {@link ChartTargetLineServiceTest} check the {@code TargetInfo}
 * the service builds; these hand that same bean to the real {@link ChartPropertyService} commit
 * path -- the one the Composer's Targets dialog uses -- and read the {@code GraphTarget} back, so
 * a field the commit ignores, or a bean it chokes on, shows up here rather than as a target that
 * saves and draws nothing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartTargetKindsCommitTest {
   @Test
   void aFormulaLineWithEntireChartCommitsAsAnAllScopeAverageLine() throws Exception {
      TargetInfo built = build(h -> h.service.addFormulaLine(
         "tok", user(), "Chart1", "Sum(Total)", "Average", true, "Avg {0}", "dashed", "#cc0000", null, null,
         ""));

      GraphTarget target = commit(built);

      assertTrue(target.isChartScope(), "Entire Chart survives the commit");
      assertEquals("Sum(Total)", target.getField());
      assertEquals(GraphConstants.DASH_LINE, target.getLineStyle());
      DynamicLineWrapper strategy = (DynamicLineWrapper) target.getStrategy();
      assertEquals(1, strategy.getParameters().length);
      assertNotNull(strategy.getParameters()[0].getFormula());
      assertEquals("Average", strategy.getParameters()[0].getFormula().getName());
      assertEquals("Avg {0}", target.getLabelFormats()[0].getDValue());

      TargetInfo back = service().getTargetInfo(chartInfo(), target, false);
      assertEquals(TargetInfo.LINE_TARGET, back.getTabFlag());
      assertEquals("Average", back.getValue());
      assertTrue(back.isChartScope());
   }

   @Test
   void aFormulaLineWithoutEntireChartCommitsAsASubchartLine() throws Exception {
      TargetInfo built = build(h -> h.service.addFormulaLine(
         "tok", user(), "Chart1", null, "Max", null, null, null, null, null, null, ""));

      GraphTarget target = commit(built);

      assertFalse(target.isChartScope());
      assertEquals("Max",
                   ((DynamicLineWrapper) target.getStrategy()).getParameters()[0].getFormula()
                      .getName());
   }

   @Test
   void aBandCommitsWithBothBoundariesLabelsAndFill() throws Exception {
      TargetInfo built = build(h -> h.service.addBand(
         "tok", user(), "Chart1", null, "100", "250.5", "Low {0}", "High {0}", "#cce5ff", null,
         null, null, null, null, ""));

      GraphTarget target = commit(built);

      DynamicLineWrapper strategy = (DynamicLineWrapper) target.getStrategy();
      assertEquals(2, strategy.getParameters().length);
      assertEquals("100", strategy.getParameters()[0].getConstantValue().getDValue());
      assertEquals("250.5", strategy.getParameters()[1].getConstantValue().getDValue());
      assertEquals("Low {0}", target.getLabelFormats()[0].getDValue());
      assertEquals("High {0}", target.getLabelFormats()[1].getDValue());
      assertEquals(new java.awt.Color(0xcc, 0xe5, 0xff), target.getBandFill().getColor(0));

      TargetInfo back = service().getTargetInfo(chartInfo(), target, false);
      assertEquals(TargetInfo.BAND_TARGET, back.getTabFlag());
      assertEquals("100", back.getValue());
      assertEquals("250.5", back.getToValue());
   }

   @Test
   void aBandWithAFormulaBoundaryAndEntireChartCommits() throws Exception {
      TargetInfo built = build(h -> h.service.addBand(
         "tok", user(), "Chart1", null, "Min", "Max", null, null, null, true, null, null, null, null, ""));

      GraphTarget target = commit(built);

      DynamicLineWrapper strategy = (DynamicLineWrapper) target.getStrategy();
      assertEquals("Min", strategy.getParameters()[0].getFormula().getName());
      assertEquals("Max", strategy.getParameters()[1].getFormula().getName());
      assertTrue(target.isChartScope());
   }

   @Test
   void aStandardDeviationCommitsWithItsFactorsAndSampleFlag() throws Exception {
      TargetInfo built = build(h -> h.service.addStatistics(
         "tok", user(), "Chart1", null, "standard_deviation", "-2,2", false, true, "SD {0}, ok",
         null, null, null, null, null, ""));

      GraphTarget target = commit(built);

      assertInstanceOf(StandardDeviationWrapper.class, target.getStrategy());
      StandardDeviationWrapper strategy = (StandardDeviationWrapper) target.getStrategy();
      assertEquals("-2,2", strategy.getFactors());
      assertEquals("false", strategy.isSample());
      assertTrue(target.isChartScope());
      // The Composer's own commit escapes the comma before splitting, so a statistics target
      // has one label for all its lines; the comma survives as text.
      assertEquals(1, target.getLabelFormats().length);
      assertEquals("SD {0}, ok", target.getLabelFormats()[0].getDValue());

      TargetInfo back = service().getTargetInfo(chartInfo(), target, false);
      assertEquals(TargetInfo.STATISTICS_TARGET, back.getTabFlag());
      assertEquals("Standard Deviation", back.getStrategyInfo().getName());
   }

   @Test
   void theOtherStatisticsCommitAsTheirOwnStrategies() throws Exception {
      assertInstanceOf(ConfidenceIntervalWrapper.class, commit(build(h -> h.service.addStatistics(
         "tok", user(), "Chart1", null, "confidence_interval", "90", null, null, null, null,
         null, null, null, null, ""))).getStrategy());
      assertInstanceOf(PercentileWrapper.class, commit(build(h -> h.service.addStatistics(
         "tok", user(), "Chart1", null, "percentiles", "25,75", null, null, null, null, null, null, null, null,
         ""))).getStrategy());
      assertInstanceOf(QuantileWrapper.class, commit(build(h -> h.service.addStatistics(
         "tok", user(), "Chart1", null, "quantiles", "5", null, null, null, null, null, null, null, null,
         ""))).getStrategy());
   }

   /** Runs one add against a mocked session and returns the target it appended. */
   private static TargetInfo build(AddCall call) throws Exception {
      ChartTargetLinesPaneModel pane = new ChartTargetLinesPaneModel();
      pane.setSupportsTarget(true);
      pane.setChartTargets(new TargetInfo[0]);
      pane.setNewTargetInfo(template());
      pane.setAvailableFields(new MeasureInfo[] {
         new MeasureInfo("", "", false), new MeasureInfo("Sum(Total)", "Sum(Total)", false) });

      VSChartInfo info = mock(VSChartInfo.class);
      when(info.isMultiStyles()).thenReturn(false);
      when(info.getChartType()).thenReturn(GraphTypes.CHART_BAR);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(chart);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt1");

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      ChartPropertyDialogService chartService = mock(ChartPropertyDialogService.class);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
      ChartAdvancedPaneModel advanced = new ChartAdvancedPaneModel();
      advanced.setChartTargetLinesPaneModel(pane);
      ChartPropertyDialogModel model = new ChartPropertyDialogModel();
      model.setChartAdvancedPaneModel(advanced);
      when(chartService.getChartPropertyDialogModel(anyString(), anyString(),
                                                    any(Principal.class))).thenReturn(model);

      call.run(new Harness(new ChartTargetLineService(sessions, chartService)));

      ArgumentCaptor<ChartPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ChartPropertyDialogModel.class);
      verify(chartService).setChartPropertyModel(anyString(), anyString(), captor.capture(),
                                                 anyString(), any(Principal.class), any());
      TargetInfo[] written =
         captor.getValue().getChartAdvancedPaneModel().getChartTargetLinesPaneModel()
            .getChartTargets();
      return written[written.length - 1];
   }

   /** What the Composer's own commit does with the appended bean. */
   private static GraphTarget commit(TargetInfo built) {
      ChartDescriptor descriptor = new ChartDescriptor();

      service().updateAllTargets(descriptor, new TargetInfo[] { built });

      assertEquals(1, descriptor.getTargetCount(), "the commit appended the target");
      return descriptor.getTarget(0);
   }

   /**
    * What {@code getTargetInfo(info, new GraphTarget(), rt)} hands back, with the real
    * categorical fill model a statistics target's commit reads.
    */
   private static TargetInfo template() {
      TargetInfo info = new TargetInfo();
      info.setMeasure(new MeasureInfo("", "", false));
      info.setAlpha("100");
      info.setLineStyle(GraphConstants.THIN_LINE);
      info.setLineColor(new ColorInfo("#000000", ChartPropertyService.COLOR_PALETTE));
      info.setFillAboveColor(new ColorInfo("", ChartPropertyService.COLOR_PALETTE));
      info.setFillBelowColor(new ColorInfo("", ChartPropertyService.COLOR_PALETTE));
      info.setFillBandColor(new ColorInfo("", ChartPropertyService.COLOR_PALETTE));
      info.setBandFill(new CategoricalColorModel(new GraphTarget().getBandFill()));
      return info;
   }

   private static ChartPropertyService service() {
      return new ChartPropertyService(mock(VisualFrameModelFactoryService.class));
   }

   private static VSChartInfo chartInfo() {
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.getModelRefsX(anyBoolean())).thenReturn(new ChartRef[0]);
      when(info.getModelRefsY(anyBoolean())).thenReturn(new ChartRef[0]);
      when(info.getXFields()).thenReturn(new ChartRef[0]);
      when(info.getYFields()).thenReturn(new ChartRef[0]);
      when(info.getRTXFields()).thenReturn(new ChartRef[0]);
      when(info.getRTYFields()).thenReturn(new ChartRef[0]);
      return info;
   }

   private static Principal user() {
      return () -> "admin";
   }

   private record Harness(ChartTargetLineService service) {}

   @FunctionalInterface
   private interface AddCall {
      void run(Harness h) throws Exception;
   }
}
