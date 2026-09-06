/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.vslayout.LayoutInfo;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.objects.command.SetCurrentFormatCommand;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import inetsoft.web.composer.vs.objects.event.GetVSObjectFormatEvent;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.viewsheet.command.ViewsheetCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@Tag("core")
class FormatPainterServiceTest {

   @BeforeEach
   void setup() throws Exception {
      service = new FormatPainterService(coreLifecycleService,
                                         chartRegionHandler, viewsheetEngine,
                                         objectModelService, bindingService,
                                         vsLayoutService);

      when(viewsheetEngine.getViewsheet(anyString(), nullable(Principal.class)))
         .thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(viewsheetSandbox));
      doNothing().when(graphPair).waitInit();
      when(viewsheetSandbox.getVGraphPair(anyString(), anyBoolean(), nullable(Dimension.class)))
         .thenReturn(graphPair);

      chart = new ChartVSAssembly(viewsheet, "Chart1");
      chart.setVSChartInfo(new VSChartInfo());
      chart.setXCube(new VSCube());

      ChartDescriptor chartDescriptor = new ChartDescriptor();
      chart.setChartDescriptor(chartDescriptor);

      when(viewsheet.getAssembly("Chart1")).thenReturn(chart);
   }

   @Test
   void should_initialize_text_format_when_reading_chart_title() throws Exception {
      TitlesDescriptor titlesDescriptor = new TitlesDescriptor();
      TitleDescriptor titleDescriptor = spy(new TitleDescriptor());
      titleDescriptor.setTextFormat(null);
      titlesDescriptor.setXTitleDescriptor(titleDescriptor);
      chart.getChartDescriptor().setTitlesDescriptor(titlesDescriptor);

      GetVSObjectFormatEvent event = new GetVSObjectFormatEvent();
      event.setName("Chart1");
      event.setRegion("x_title");

      service.getFormat("Viewsheet1", event, null, dispatcher);

      assertNotNull(titlesDescriptor.getXTitleDescriptor().getTextFormat());
      verify(titleDescriptor, atLeast(2)).setTextFormat(any());
   }

   @Test
   @Disabled
   void should_enable_horizontal_alignment_for_legend_content() throws Exception {
      GetVSObjectFormatEvent event = new GetVSObjectFormatEvent();
      event.setName("Chart1");
      event.setRegion("legend_content");
      event.setDimensionColumn(true);
      event.setIndex(0);

      service.getFormat("Viewsheet1", event, null, dispatcher);

      verify(dispatcher).sendCommand(any(ViewsheetCommand.class));
      verify(dispatcher).sendCommand(argCaptor.capture());

      List<SetCurrentFormatCommand> commands = argCaptor.getAllValues();
      assertTrue(commands.getFirst().getModel().isHAlignmentEnabled());
      assertFalse(commands.getFirst().getModel().isVAlignmentEnabled());
   }

   // Bug #16423 when selecting axis label, ensure correct alignment options are enabled.
   @Test
   void should_enable_horizontal_alignment_for_y_axis_label() throws Exception {
      AxisDescriptor axisDescriptor = new AxisDescriptor();

      doReturn(axisDescriptor).when(chartRegionHandler)
         .getAxisDescriptor(nullable(VSChartInfo.class), nullable(ChartArea.class),
                            nullable(String.class), anyInt(), nullable(String.class));

      GetVSObjectFormatEvent event = new GetVSObjectFormatEvent();
      event.setName("Chart1");
      event.setRegion("left_y_axis");
      event.setDimensionColumn(true);
      event.setIndex(0);
      event.setColumnName("");

      service.getFormat("Viewsheet1", event, null, dispatcher);

      event.setDimensionColumn(false);

      service.getFormat("Viewsheet1", event, null, dispatcher);

      verify(dispatcher, times(2)).sendCommand(any(ViewsheetCommand.class));
      verify(dispatcher, times(2)).sendCommand(argCaptor.capture());

      List<SetCurrentFormatCommand> commands = argCaptor.getAllValues();
      assertTrue(commands.get(0).getModel().isHAlignmentEnabled());
      assertFalse(commands.get(0).getModel().isVAlignmentEnabled());
      assertFalse(commands.get(1).getModel().isHAlignmentEnabled());
      assertFalse(commands.get(1).getModel().isVAlignmentEnabled());
   }

   /**
    * VBS-005: before this guard, a `field` that resolved to nothing on the chart did not error —
    * it fell all the way through {@code GraphFormatUtil.setBindingTextFormat} to
    * {@code plot.setTextFormat(fmt)}, silently overwriting the chart's whole shared
    * {@code PlotDescriptor} default text format with a format meant for one field. This asserts
    * both that the call now refuses instead, and that the plot's default text format is left
    * untouched (the real prior bug, not merely "a disconnected format got created").
    */
   @Test
   void refusesATextFieldThatDoesNotResolveToAnyBoundField() {
      when(viewsheet.getLayoutInfo()).thenReturn(new LayoutInfo());

      // PlotDescriptor always starts with its own fresh default CompositeTextFormat (never
      // null) — the prior bug replaced this exact instance via plot.setTextFormat(fmt), so the
      // regression check is identity, not nullity.
      CompositeTextFormat originalPlotFormat =
         chart.getChartDescriptor().getPlotDescriptor().getTextFormat();

      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setFormat("currency");
      format.setFormatSpec("$#,##0");

      FormatVSObjectEvent event = new FormatVSObjectEvent();
      event.setObjects(new String[0]);
      event.setCharts(new String[]{ "Chart1" });
      event.setRegions(new String[]{ "text" });
      event.setColumnNames(new String[][]{ { "NOT_A_REAL_FIELD" } });
      event.setIndexes(new int[][]{ { -1 } });
      event.setFormat(format);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.setFormat("Viewsheet1", event, null, dispatcher, ""));
      assertTrue(thrown.getMessage().contains("NOT_A_REAL_FIELD"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Chart1"), thrown.getMessage());
      assertSame(originalPlotFormat,
                chart.getChartDescriptor().getPlotDescriptor().getTextFormat(),
                "the plot's shared default text format must not be replaced by a bogus field");
   }

   /**
    * VBS-005 correction #1: {@code info.getDCBIndingRef()} only searches
    * {@code getRuntimeDateComparisonRefs()}, not the full runtime-field lookup
    * {@code getChartBindable()} itself falls back to
    * ({@code getFieldByName(columnName, true)} gated on {@code isAppliedDateComparison()}). The
    * new guard must replicate that fallback, or a legitimately-bound runtime-only field on a
    * date-comparison chart would incorrectly fail the unresolved-field check.
    */
   @Test
   void resolvesATextFieldThroughTheDateComparisonRuntimeFallback() throws Exception {
      when(viewsheet.getLayoutInfo()).thenReturn(new LayoutInfo());

      CompositeTextFormat originalPlotFormat =
         chart.getChartDescriptor().getPlotDescriptor().getTextFormat();

      ChartRef dcOnlyField = mock(ChartRef.class);
      when(dcOnlyField.getFullName()).thenReturn("DC_ONLY_FIELD");

      VSChartInfo chartInfo = chart.getVSChartInfo();
      chartInfo.setRTXFields(new ChartRef[]{ dcOnlyField });
      chartInfo.setRuntimeDateComparisonRefs(new ChartRef[]{ dcOnlyField });

      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setFormat("currency");
      format.setFormatSpec("$#,##0");

      FormatVSObjectEvent event = new FormatVSObjectEvent();
      event.setObjects(new String[0]);
      event.setCharts(new String[]{ "Chart1" });
      event.setRegions(new String[]{ "text" });
      event.setColumnNames(new String[][]{ { "DC_ONLY_FIELD" } });
      event.setIndexes(new int[][]{ { -1 } });
      event.setFormat(format);

      assertDoesNotThrow(() -> service.setFormat("Viewsheet1", event, null, dispatcher, ""));
      verify(dcOnlyField).setTextFormat(any());
      assertSame(originalPlotFormat,
                chart.getChartDescriptor().getPlotDescriptor().getTextFormat(),
                "a resolved field must write its own text format, not the plot's shared default");
   }

   @Captor ArgumentCaptor<SetCurrentFormatCommand> argCaptor;
   @Mock ViewsheetEngine viewsheetEngine;
   @Mock
   CoreLifecycleService coreLifecycleService;
   @Mock ChartRegionHandler chartRegionHandler;
   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock VSObjectModelFactoryService objectModelService;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock ViewsheetSandbox viewsheetSandbox;
   @Mock CommandDispatcher dispatcher;
   @Mock VSBindingService bindingService;
   @Mock VSLayoutService vsLayoutService;
   @Mock VGraphPair graphPair;
   private ChartVSAssembly chart;
   private FormatPainterService service;
}
