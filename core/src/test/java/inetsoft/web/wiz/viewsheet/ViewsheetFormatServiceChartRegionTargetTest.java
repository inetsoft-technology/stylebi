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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.adhoc.model.chart.ChartFormatConstants;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import inetsoft.web.wiz.binding.CalcTableService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.stream.Stream;

import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.principal;
import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.sessionsFor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80443 sub-gap 1: {@code set_format} targets {@code axisLabel}, {@code axisLabel2},
 * {@code legendTitle}, {@code legendContent} and {@code plotLabel} route to a chart's own
 * region-write mechanism (the same one {@code text} already uses), writing a whole axis/legend/
 * plot-label region instead of one aesthetic-bound field.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetFormatServiceChartRegionTargetTest {
   private static Stream<Arguments> targetsAndRegions() {
      return Stream.of(
         Arguments.of("axisLabel", ChartFormatConstants.LEFT_Y_AXIS),
         Arguments.of("axisLabel2", ChartFormatConstants.RIGHT_Y_AXIS),
         Arguments.of("legendTitle", ChartFormatConstants.LEGEND_TITLE),
         Arguments.of("legendContent", ChartFormatConstants.LEGEND_CONTENT),
         Arguments.of("plotLabel", ChartFormatConstants.PLOT_AREA));
   }

   /**
    * Same as {@link #targetsAndRegions} minus {@code plotLabel}: bug 80443 gave {@code plotLabel}
    * its own per-bound-measure column-name/index resolution (see {@code
    * plotLabelWritesEachBoundMeasuresOwnColumnNameAndIndex} below) instead of the dummy
    * placeholder {@code columnNames}/{@code null} {@code indexes} shape the other four targets
    * still use -- a data label's {@code TextFormat} is owned by its bound measure, never by a
    * chart-wide region descriptor the way an axis/legend region is.
    */
   private static Stream<Arguments> wholeRegionTargetsAndRegions() {
      return Stream.of(
         Arguments.of("axisLabel", ChartFormatConstants.LEFT_Y_AXIS),
         Arguments.of("axisLabel2", ChartFormatConstants.RIGHT_Y_AXIS),
         Arguments.of("legendTitle", ChartFormatConstants.LEGEND_TITLE),
         Arguments.of("legendContent", ChartFormatConstants.LEGEND_CONTENT));
   }

   /**
    * A chart with a secondary (right) Y-axis configured, so every {@code targetsAndRegions} case
    * -- including {@code axisLabel2} -- can exercise the write path without tripping the bug
    * #80443 follow-up refusal ({@code refuseAxisLabel2WithoutSecondaryAxis}).
    */
   private static ChartVSAssembly chart() {
      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      ChartVSAssemblyInfo info = mock(ChartVSAssemblyInfo.class);
      VSChartInfo chartInfo = chartInfo(true);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(assembly.getVSChartInfo()).thenReturn(chartInfo);
      return assembly;
   }

   /** A chart with no secondary axis at all -- the ordinary, common case. */
   private static ChartVSAssembly chartWithoutSecondaryAxis() {
      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      ChartVSAssemblyInfo info = mock(ChartVSAssemblyInfo.class);
      VSChartInfo chartInfo = chartInfo(false);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(assembly.getVSChartInfo()).thenReturn(chartInfo);
      return assembly;
   }

   private static VSChartInfo chartInfo(boolean secondaryY) {
      VSChartInfo info = mock(VSChartInfo.class);
      ChartAggregateRef measure = mock(ChartAggregateRef.class);
      when(measure.isSecondaryY()).thenReturn(secondaryY);
      when(info.getXFields()).thenReturn(new ChartRef[0]);
      when(info.getYFields()).thenReturn(new ChartRef[]{ measure });
      when(info.isInvertedGraph()).thenReturn(false);
      return info;
   }

   private static RuntimeViewsheet rvsWith(String name, VSAssembly assembly) {
      Viewsheet viewsheet = mock(Viewsheet.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(name)).thenReturn(assembly);
      return rvs;
   }

   private static VSObjectFormatInfoModel red() {
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setColor("#FF0000");
      return format;
   }

   private static ViewsheetFormatService.FormatRequest request(String target) {
      return new ViewsheetFormatService.FormatRequest(List.of("Chart1"), red(), false, target,
                                                      null, null);
   }

   private static FormatVSObjectEvent write(RuntimeViewsheet rvs, FormatPainterService painter,
                                            String target) throws Exception
   {
      new ViewsheetFormatService(sessionsFor(rvs), FormatPainterProxies.local(painter), mock(CalcTableService.class), null)
         .setFormat("tok", principal(), request(target), "");
      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(java.security.Principal.class),
                                any(), anyString());
      return captor.getValue();
   }

   @ParameterizedTest
   @MethodSource("wholeRegionTargetsAndRegions")
   void writesTheChartRegionWithAWholeAxisShape(String target, String region) throws Exception {
      FormatVSObjectEvent event =
         write(rvsWith("Chart1", chart()), mock(FormatPainterService.class), target);

      assertArrayEquals(new String[0], event.getObjects(), "objects stays empty, like 'text'");
      assertArrayEquals(new String[]{ "Chart1" }, event.getCharts());
      assertArrayEquals(new String[]{ region }, event.getRegions());
      assertNull(event.getIndexes()[0],
                "null indexes[i] makes setChartFormat resolve the whole region, not one field");
      assertNotNull(event.getColumnNames()[0]);
      assertTrue(event.getColumnNames()[0].length > 0,
                "an empty columnNames[i] would route an axis/plot-area region into " +
                "FormatPainterService's whole-chart bulk-overwrite branch instead");
   }

   @ParameterizedTest
   @MethodSource("targetsAndRegions")
   void refusesTheTargetOnANonChartBeforeMutating(String target, String region) throws Exception {
      TableVSAssembly table = mock(TableVSAssembly.class);
      when(table.getVSAssemblyInfo()).thenReturn(mock(TableVSAssemblyInfo.class));
      ViewsheetSessionService sessions = sessionsFor(rvsWith("Chart1", table));
      FormatPainterService painter = mock(FormatPainterService.class);

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> new ViewsheetFormatService(sessions, FormatPainterProxies.local(painter), mock(CalcTableService.class), null)
            .setFormat("tok", principal(), request(target), ""));

      assertTrue(e.getMessage().contains("'" + target + "' only applies to a chart") &&
                 e.getMessage().contains("'Chart1'") &&
                 e.getMessage().contains("TableVSAssembly") &&
                 e.getMessage().contains("not applied"), e.getMessage());
      verify(sessions, never()).mutate(anyString(), any(java.security.Principal.class), any());
      verifyNoInteractions(painter);
   }

   @Test
   void refusesAxisLabel2WithoutSecondaryAxis() throws Exception {
      ViewsheetSessionService sessions = sessionsFor(rvsWith("Chart1", chartWithoutSecondaryAxis()));
      FormatPainterService painter = mock(FormatPainterService.class);

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> new ViewsheetFormatService(sessions, FormatPainterProxies.local(painter), mock(CalcTableService.class), null)
            .setFormat("tok", principal(), request("axisLabel2"), ""));

      assertTrue(e.getMessage().contains("'axisLabel2'") &&
                 e.getMessage().contains("secondary") &&
                 e.getMessage().contains("'Chart1'"), e.getMessage());
      verify(sessions, never()).mutate(anyString(), any(java.security.Principal.class), any());
      verifyNoInteractions(painter);
   }

   /**
    * Bug 80443: {@code plotLabel} must resolve the chart's own bound measure(s) and route
    * through the per-column branch (real {@code columnNames}, non-null {@code indexes}) instead
    * of the dummy-placeholder "whole region" shape the other four chart-region targets use --
    * that shape lands the write in {@code PlotDescriptor}'s chart-wide fallback {@code
    * TextFormat}, which the renderer never reads once a measure is bound, making the write a
    * silent no-op.
    */
   @Test
   void plotLabelWritesEachBoundMeasuresOwnColumnNameAndIndex() throws Exception {
      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      ChartVSAssemblyInfo info = mock(ChartVSAssemblyInfo.class);
      VSChartInfo chartInfo = mock(VSChartInfo.class);
      ChartAggregateRef measure = mock(ChartAggregateRef.class);
      when(measure.isMeasure()).thenReturn(true);
      when(measure.getFullName()).thenReturn("Sum(QTY)");
      when(chartInfo.getXFields()).thenReturn(new ChartRef[0]);
      when(chartInfo.getYFields()).thenReturn(new ChartRef[]{ measure });
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(assembly.getVSChartInfo()).thenReturn(chartInfo);

      FormatVSObjectEvent event =
         write(rvsWith("Chart1", assembly), mock(FormatPainterService.class), "plotLabel");

      assertArrayEquals(new String[0], event.getObjects(), "objects stays empty, like 'text'");
      assertArrayEquals(new String[]{ "Chart1" }, event.getCharts());
      assertArrayEquals(new String[]{ ChartFormatConstants.PLOT_AREA }, event.getRegions());
      assertArrayEquals(new String[]{ "Sum(QTY)" }, event.getColumnNames()[0],
                        "must resolve the chart's own bound measure, not a dummy placeholder " +
                        "column name");
      assertArrayEquals(new int[]{ 0 }, event.getIndexes()[0],
                        "non-null indexes route setChartFormat's per-column branch instead of " +
                        "the whole-region branch");
      assertTrue(event.isValueText(),
                "plotLabel always means the show-values text, never a separately-bound Text " +
                "aesthetic field");
   }

   /** A chart with no bound measure on either shelf has no data label to format at all. */
   @Test
   void plotLabelRefusesAChartWithNoBoundMeasure() {
      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      ChartVSAssemblyInfo info = mock(ChartVSAssemblyInfo.class);
      VSChartInfo chartInfo = mock(VSChartInfo.class);
      when(chartInfo.getXFields()).thenReturn(new ChartRef[0]);
      when(chartInfo.getYFields()).thenReturn(new ChartRef[0]);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(assembly.getVSChartInfo()).thenReturn(chartInfo);

      ViewsheetSessionService sessions = sessionsFor(rvsWith("Chart1", assembly));
      FormatPainterService painter = mock(FormatPainterService.class);

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> new ViewsheetFormatService(sessions, FormatPainterProxies.local(painter),
                                          mock(CalcTableService.class), null)
            .setFormat("tok", principal(), request("plotLabel"), ""));

      assertTrue(e.getMessage().contains("plotLabel") && e.getMessage().contains("Chart1"),
                e.getMessage());
   }

   @Test
   void refusesAMissingAssembly() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> new ViewsheetFormatService(sessionsFor(rvsWith("Chart1", null)),
                                          FormatPainterProxies.local(mock(FormatPainterService.class)),
                                          mock(CalcTableService.class), null)
            .setFormat("tok", principal(), request("axisLabel"), ""));
      assertTrue(e.getMessage().contains("no assembly named 'Chart1'"), e.getMessage());
   }

   @Test
   void unknownTargetErrorListsTheChartRegionTargets() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> new ViewsheetFormatService(sessionsFor(null), FormatPainterProxies.local(mock(FormatPainterService.class)),
                                          mock(CalcTableService.class), null)
            .setFormat("tok", principal(), request("bogus"), ""));
      assertTrue(e.getMessage().contains("'axisLabel'") && e.getMessage().contains("'axisLabel2'") &&
                 e.getMessage().contains("'legendTitle'") &&
                 e.getMessage().contains("'legendContent'") &&
                 e.getMessage().contains("'plotLabel'"), e.getMessage());
   }
}
