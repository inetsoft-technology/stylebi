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
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import inetsoft.web.adhoc.model.chart.ChartFormatConstants;
import inetsoft.web.wiz.binding.CalcTableService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.principal;
import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.sessionsFor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80336: chart text sub-regions (axis labels/title, legend title/content, target label) are
 * written through FormatPainterService's per-chart region loop, addressed like the format pane.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetFormatServiceChartRegionTest {
   private static ChartAggregateRef measure(String name, boolean secondary) {
      ChartAggregateRef ref = mock(ChartAggregateRef.class);
      when(ref.getFullName()).thenReturn(name);
      when(ref.isSecondaryY()).thenReturn(secondary);
      return ref;
   }

   private static RuntimeViewsheet rvs(int targets) {
      return rvs(targets, measure("Sum(Total)", false));
   }

   private static RuntimeViewsheet rvs(int targets, ChartRef... yRefs) {
      ChartDescriptor descriptor = mock(ChartDescriptor.class);
      when(descriptor.getTargetCount()).thenReturn(targets);
      ChartRef x = mock(ChartRef.class);
      when(x.getFullName()).thenReturn("Region");
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.getRTXFields()).thenReturn(new ChartRef[]{ x });
      when(info.getRTYFields()).thenReturn(yRefs);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getChartDescriptor()).thenReturn(descriptor);
      when(chart.getVSChartInfo()).thenReturn(info);
      Viewsheet viewsheet = mock(Viewsheet.class);
      when(viewsheet.getAssembly("Chart1")).thenReturn(chart);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getID()).thenReturn("rt1");
      return rvs;
   }

   private static ViewsheetFormatService.ChartRegionFormatRequest request(
      String region, String axis, String field, Integer index)
   {
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setColor("#FF0000");
      return new ViewsheetFormatService.ChartRegionFormatRequest(
         "Chart1", region, axis, field, index, format, false);
   }

   private static FormatVSObjectEvent write(
      ViewsheetFormatService.ChartRegionFormatRequest request, int targets) throws Exception
   {
      return write(request, rvs(targets));
   }

   private static FormatVSObjectEvent write(
      ViewsheetFormatService.ChartRegionFormatRequest request, RuntimeViewsheet rvs)
      throws Exception
   {
      FormatPainterService painter = mock(FormatPainterService.class);
      new ViewsheetFormatService(sessionsFor(rvs), FormatPainterProxies.local(painter),
                                 mock(CalcTableService.class), null)
         .setChartRegionFormat("tok", principal(), request, "");
      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(java.security.Principal.class),
                                any(), anyString());
      return captor.getValue();
   }

   @Test
   void axisLabelsUseTheBottomXAxisAndFirstFieldOfTheShelf() throws Exception {
      FormatVSObjectEvent event = write(request("axis_labels", "x", null, null), 0);
      assertArrayEquals(new String[]{ ChartFormatConstants.BOTTOM_X_AXIS }, event.getRegions());
      assertArrayEquals(new String[]{ "Region" }, event.getColumnNames()[0]);
      assertArrayEquals(new String[]{ "Chart1" }, event.getCharts());
   }

   @Test
   void axisTitleUsesThePainterTitleRegion() throws Exception {
      FormatVSObjectEvent event = write(
         request("axis_title", "y2", null, null),
         rvs(0, measure("Sum(Total)", false), measure("Sum(Qty)", true)));
      assertArrayEquals(new String[]{ "y2_title" }, event.getRegions());
   }

   /** The painter picks the descriptor from the field, so a y2 request on a one-axis chart
    *  used to format the primary y axis and report success. */
   @Test
   void refusesY2LabelsWhenNoMeasureIsOnTheSecondaryAxis() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> write(request("axis_labels", "y2", null, null), 0));
      assertTrue(e.getMessage().contains("y2"), e.getMessage());
   }

   @Test
   void refusesY2TitleWhenNoMeasureIsOnTheSecondaryAxis() {
      assertThrows(IllegalArgumentException.class,
                   () -> write(request("axis_title", "y2", null, null), 0));
   }

   @Test
   void y2LabelsPickTheSecondaryMeasure() throws Exception {
      FormatVSObjectEvent event = write(
         request("axis_labels", "y2", null, null),
         rvs(0, measure("Sum(Total)", false), measure("Sum(Qty)", true)));
      assertArrayEquals(new String[]{ "Sum(Qty)" }, event.getColumnNames()[0]);
   }

   @Test
   void plainYSkipsAMeasureOnTheSecondaryAxis() throws Exception {
      FormatVSObjectEvent event = write(
         request("axis_labels", "y", null, null),
         rvs(0, measure("Sum(Qty)", true), measure("Sum(Total)", false)));
      assertArrayEquals(new String[]{ "Sum(Total)" }, event.getColumnNames()[0]);
   }

   @Test
   void refusesPlainYWhenEveryMeasureIsSecondary() {
      assertThrows(IllegalArgumentException.class,
                   () -> write(request("axis_labels", "y", null, null),
                               rvs(0, measure("Sum(Qty)", true))));
   }

   @Test
   void refusesAnExplicitFieldOnTheOtherYAxisSide() {
      RuntimeViewsheet rvs = rvs(0, measure("Sum(Total)", false), measure("Sum(Qty)", true));
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> write(request("axis_labels", "y2", "Sum(Total)", null), rvs));
      assertTrue(e.getMessage().contains("Sum(Total)"), e.getMessage());
   }

   @Test
   void legendRegionsMapToPainterRegions() throws Exception {
      assertArrayEquals(new String[]{ ChartFormatConstants.LEGEND_TITLE },
                        write(request("legend_title", null, null, null), 0).getRegions());
      FormatVSObjectEvent content = write(request("legend_content", null, null, 1), 0);
      assertArrayEquals(new String[]{ ChartFormatConstants.LEGEND_CONTENT }, content.getRegions());
      assertArrayEquals(new int[]{ 1 }, content.getIndexes()[0]);
   }

   @Test
   void targetLabelIsAddressedByStableIndex() throws Exception {
      FormatVSObjectEvent event = write(request("target_label", null, null, 1), 2);
      assertArrayEquals(new String[]{ FormatPainterService.TARGET_LABEL_BY_INDEX },
                        event.getRegions());
      assertArrayEquals(new int[]{ 1 }, event.getIndexes()[0]);
   }

   @Test
   void refusesUnknownRegionNamingTheField() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> write(request("tick_labels", "x", null, null), 0));
      assertTrue(e.getMessage().contains("'region'"), e.getMessage());
   }

   @Test
   void refusesAxisRegionWithoutAxis() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> write(request("axis_labels", null, null, null), 0));
      assertTrue(e.getMessage().contains("'axis'"), e.getMessage());
   }

   @Test
   void refusesTargetLabelWithMissingTarget() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> write(request("target_label", null, null, 3), 1));
      assertTrue(e.getMessage().contains("'index'"), e.getMessage());
   }
}
