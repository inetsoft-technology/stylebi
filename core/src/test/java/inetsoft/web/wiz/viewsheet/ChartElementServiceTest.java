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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.viewsheet.controller.chart.*;
import inetsoft.web.viewsheet.event.chart.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class ChartElementServiceTest {
   /**
    * <b>The construction guard.</b> These events declare no setters — only getters over private
    * fields — so they are built with Jackson, the same way the STOMP layer builds them. If that
    * ever stops landing values, every event would arrive all-default, and an all-default titles
    * event means "show every title": it would silently un-hide titles the user had hidden. So
    * this asserts the conversion works rather than trusting it.
    */
   @Test
   void jacksonActuallyPopulatesTheSetterlessEvents() {
      ObjectMapper mapper = new ObjectMapper();

      VSChartTitlesVisibilityEvent titles = mapper.convertValue(
         ChartElementService.titleFields("Chart1", "y", false),
         VSChartTitlesVisibilityEvent.class);

      assertEquals("Chart1", titles.getChartName(), "chartName comes from the superclass");
      assertTrue(titles.isHide(), "a private boolean with no setter must still be populated");
      assertEquals("y_title", titles.getTitleType());
   }

   @Test
   void aDefaultTitlesEventWouldMeanShowEverything() {
      VSChartTitlesVisibilityEvent empty = new ObjectMapper()
         .convertValue(java.util.Map.of(), VSChartTitlesVisibilityEvent.class);

      assertFalse(empty.isHide(),
                  "hide defaults to false, which this event reads as show-all-titles — which " +
                  "is why the construction guard above matters");
   }

   @Test
   void hidesOneAxisByColumnName() throws Exception {
      Harness h = harness(mock(ChartVSAssembly.class));

      h.service.setVisibility("tok", principal(), "Chart1", "axis", "Region", false, "");

      ArgumentCaptor<VSChartAxesVisibilityEvent> captor =
         ArgumentCaptor.forClass(VSChartAxesVisibilityEvent.class);
      verify(h.axes).eventHandler(eq("rt1"), captor.capture(), anyString(),
                                  any(Principal.class), any());
      assertEquals("Chart1", captor.getValue().getChartName());
      assertEquals("Region", captor.getValue().getColumnName());
      assertTrue(captor.getValue().isHide());
   }

   @Test
   void showsAllAxes() throws Exception {
      Harness h = harness(mock(ChartVSAssembly.class));

      h.service.setVisibility("tok", principal(), "Chart1", "axis", null, true, "");

      ArgumentCaptor<VSChartAxesVisibilityEvent> captor =
         ArgumentCaptor.forClass(VSChartAxesVisibilityEvent.class);
      verify(h.axes).eventHandler(eq("rt1"), captor.capture(), anyString(),
                                  any(Principal.class), any());
      assertFalse(captor.getValue().isHide());
   }

   @Test
   void hidesOneLegendByField() throws Exception {
      Harness h = harness(mock(ChartVSAssembly.class));

      h.service.setVisibility("tok", principal(), "Chart1", "legend", "Category", false, "");

      ArgumentCaptor<VSChartLegendsVisibilityEvent> captor =
         ArgumentCaptor.forClass(VSChartLegendsVisibilityEvent.class);
      verify(h.legends).eventHandler(eq("rt1"), captor.capture(), anyString(),
                                     any(Principal.class), any());
      assertEquals("Category", captor.getValue().getField());
      assertTrue(captor.getValue().isHide());
   }

   // ── the titles footgun ────────────────────────────────────────────────────

   @Test
   void hidesAnAxisTitleWithTheDescriptorToken() throws Exception {
      Harness h = harness(mock(ChartVSAssembly.class));

      h.service.setVisibility("tok", principal(), "Chart1", "title", "y", false, "");

      VSChartTitlesVisibilityEvent event = captureTitle(h);
      assertEquals("y_title", event.getTitleType());
      assertTrue(event.isHide());
   }

   /**
    * The event expresses "show the chart title" as {@code hide=true} plus a special token. An
    * agent that set {@code hide=false} would show every title instead, including ones the user
    * had deliberately hidden.
    */
   @Test
   void showsTheChartTitleThroughTheEventsOwnSpecialCase() throws Exception {
      Harness h = harness(mock(ChartVSAssembly.class));

      h.service.setVisibility("tok", principal(), "Chart1", "title", "chart", true, "");

      VSChartTitlesVisibilityEvent event = captureTitle(h);
      assertTrue(event.isHide(), "hide=true is how this event says 'show the chart title'");
      assertEquals("chart-title-true", event.getTitleType());
   }

   @Test
   void hidesTheChartTitle() throws Exception {
      Harness h = harness(mock(ChartVSAssembly.class));

      h.service.setVisibility("tok", principal(), "Chart1", "title", "chart", false, "");

      VSChartTitlesVisibilityEvent event = captureTitle(h);
      assertEquals("chart-title", event.getTitleType());
   }

   @Test
   void showsAllTitlesOnlyWhenNoTargetWasNamed() throws Exception {
      Harness h = harness(mock(ChartVSAssembly.class));

      h.service.setVisibility("tok", principal(), "Chart1", "title", null, true, "");

      assertFalse(captureTitle(h).isHide(), "hide=false is the event's show-everything case");
   }

   /**
    * The Composer cannot show one axis title on its own, and answering the request with
    * show-everything would un-hide titles the user deliberately hid.
    */
   @Test
   void refusesToShowASingleAxisTitleRatherThanShowingThemAll() {
      Harness h = harness(mock(ChartVSAssembly.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.setVisibility("tok", principal(), "Chart1", "title", "y", true, ""));

      assertTrue(thrown.getMessage().contains("all titles"));
   }

   @Test
   void refusesToShowASingleAxisOrLegend() {
      Harness h = harness(mock(ChartVSAssembly.class));

      assertThrows(Exception.class,
                   () -> h.service.setVisibility("tok", principal(), "Chart1", "axis", "Region",
                                                 true, ""));
      assertThrows(Exception.class,
                   () -> h.service.setVisibility("tok", principal(), "Chart1", "legend", "Cat",
                                                 true, ""));
      verifyNoInteractions(h.sessions);
   }

   @Test
   void refusesAnUnknownTitleTarget() {
      Harness h = harness(mock(ChartVSAssembly.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.setVisibility("tok", principal(), "Chart1", "title", "z", false, ""));

      assertTrue(thrown.getMessage().contains("z"));
   }

   @Test
   void refusesAnUnknownElement() {
      Harness h = harness(mock(ChartVSAssembly.class));

      assertThrows(Exception.class,
                   () -> h.service.setVisibility("tok", principal(), "Chart1", "gridline", null,
                                                 false, ""));
   }

   @Test
   void refusesANonChartAssembly() {
      Harness h = harness(mock(TextVSAssembly.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.setVisibility("tok", principal(), "Text1", "axis", null, true, ""));

      assertTrue(thrown.getMessage().contains("Text1"));
   }

   // ── plot sizing ───────────────────────────────────────────────────────────

   @Test
   void resizesThePlotVertically() throws Exception {
      Harness h = harness(mock(ChartVSAssembly.class));

      h.service.resizePlot("tok", principal(), "Chart1", 0.6, true, false, "");

      ArgumentCaptor<VSChartPlotResizeEvent> captor =
         ArgumentCaptor.forClass(VSChartPlotResizeEvent.class);
      verify(h.plot).eventHandler(eq("rt1"), captor.capture(), anyString(),
                                  any(Principal.class), any());
      assertEquals(0.6, captor.getValue().getSizeRatio());
      assertTrue(captor.getValue().isHeightResized());
      assertFalse(captor.getValue().isReset());
   }

   @Test
   void resetsThePlotWithoutARatio() throws Exception {
      Harness h = harness(mock(ChartVSAssembly.class));

      h.service.resizePlot("tok", principal(), "Chart1", null, false, true, "");

      ArgumentCaptor<VSChartPlotResizeEvent> captor =
         ArgumentCaptor.forClass(VSChartPlotResizeEvent.class);
      verify(h.plot).eventHandler(eq("rt1"), captor.capture(), anyString(),
                                  any(Principal.class), any());
      assertTrue(captor.getValue().isReset());
   }

   @Test
   void refusesARatioOutsideTheUnitRange() {
      Harness h = harness(mock(ChartVSAssembly.class));

      assertThrows(Exception.class,
                   () -> h.service.resizePlot("tok", principal(), "Chart1", 0d, true, false, ""));
      assertThrows(Exception.class,
                   () -> h.service.resizePlot("tok", principal(), "Chart1", 1.5, true, false, ""));
      assertThrows(Exception.class,
                   () -> h.service.resizePlot("tok", principal(), "Chart1", null, true, false,
                                              ""));
      verifyNoInteractions(h.sessions);
   }

   @Test
   void eachVisibilityChangeIsOneCheckpoint() throws Exception {
      Harness h = harness(mock(ChartVSAssembly.class));

      h.service.setVisibility("tok", principal(), "Chart1", "axis", "Region", false, "");

      verify(h.sessions, times(1)).mutate(anyString(), any(Principal.class), any());
   }

   @Test
   void vocabularyNamesTheTitleTargets() {
      Harness h = harness(mock(ChartVSAssembly.class));

      assertTrue(String.valueOf(h.service.vocabulary().get("titleTargets")).contains("y2"));
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private record Harness(ChartElementService service, ViewsheetSessionService sessions,
                          VSChartAxesVisibilityService axes,
                          VSChartLegendsVisibilityService legends,
                          VSChartTitlesVisibilityService titles,
                          VSChartPlotResizeService plot) {}

   private static VSChartTitlesVisibilityEvent captureTitle(Harness h) throws Exception {
      ArgumentCaptor<VSChartTitlesVisibilityEvent> captor =
         ArgumentCaptor.forClass(VSChartTitlesVisibilityEvent.class);
      verify(h.titles).eventHandler(eq("rt1"), captor.capture(), anyString(),
                                    any(Principal.class), any());
      return captor.getValue();
   }

   private static Harness harness(VSAssembly assembly) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      VSChartAxesVisibilityService axes = mock(VSChartAxesVisibilityService.class);
      VSChartLegendsVisibilityService legends = mock(VSChartLegendsVisibilityService.class);
      VSChartTitlesVisibilityService titles = mock(VSChartTitlesVisibilityService.class);
      VSChartPlotResizeService plot = mock(VSChartPlotResizeService.class);

      return new Harness(new ChartElementService(sessions, new ObjectMapper(), axes,
                                                legends, titles, plot),
                         sessions, axes, legends, titles, plot);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
