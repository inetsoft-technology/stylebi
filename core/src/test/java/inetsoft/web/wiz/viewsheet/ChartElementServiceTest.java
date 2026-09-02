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
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.viewsheet.controller.chart.*;
import inetsoft.web.viewsheet.event.chart.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.Map;

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

   /**
    * Unlike legends, neither axis event has a real hide-everything mode:
    * {@code VSChartAxesVisibilityEvent#getColumnName}'s own javadoc documents a null target as
    * meaning "show all axis", never hide-all, and {@code hideAxis()} falls a null target through
    * to one arbitrary descriptor. This used to run silently and report
    * "Hid all axiss on Chart1" while touching no axis label at all — confirmed live 2026-09-02.
    */
   @Test
   void refusesToHideEveryAxisWithNoTarget() {
      Harness h = harness(mock(ChartVSAssembly.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.setVisibility("tok", principal(), "Chart1", "axis", null, false, ""));

      assertTrue(thrown.getMessage().contains("no 'target'"));
      verifyNoInteractions(h.axes);
   }

   @Test
   void hidesEveryLegendWhenNoneIsNamed() throws Exception {
      Harness h = harness(mock(ChartVSAssembly.class));

      h.service.setVisibility("tok", principal(), "Chart1", "legend", null, false, "");

      ArgumentCaptor<VSChartLegendsVisibilityEvent> captor =
         ArgumentCaptor.forClass(VSChartLegendsVisibilityEvent.class);
      verify(h.legends).eventHandler(eq("rt1"), captor.capture(), anyString(),
                                     any(Principal.class), any());
      assertTrue(captor.getValue().isHide());
      assertNull(captor.getValue().getAestheticType(),
                 "no aestheticType IS the event's hide-all, which is what was asked for");
   }

   /**
    * The defect this path was rewritten for. A named legend used to be sent as {@code field}
    * alone, and the event reads a hide with no {@code aestheticType} as "hide every legend" — so
    * naming one legend of two hid both and reported success naming the one. With no laid-out
    * graph there is nothing to resolve the name against, so the call is refused rather than
    * quietly becoming hide-all.
    */
   @Test
   void refusesToHideANamedLegendItCannotResolve() {
      Harness h = harness(mock(ChartVSAssembly.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.setVisibility("tok", principal(), "Chart1", "legend", "Category", false,
                                       ""));

      assertTrue(thrown.getMessage().contains("without 'target'"), "and say what to call instead");
      verifyNoInteractions(h.legends);
   }

   /**
    * A blank target is no target. The tool layer's own show-with-a-target guard already reads
    * {@code ""} as absent, so a server reading it as present refused a call the tool had passed —
    * and on the legend path it refused it by naming an empty legend.
    */
   @Test
   void readsABlankTargetAsNoTarget() throws Exception {
      Harness h = harness(mock(ChartVSAssembly.class));

      h.service.setVisibility("tok", principal(), "Chart1", "legend", "  ", false, "");

      ArgumentCaptor<VSChartLegendsVisibilityEvent> captor =
         ArgumentCaptor.forClass(VSChartLegendsVisibilityEvent.class);
      verify(h.legends).eventHandler(eq("rt1"), captor.capture(), anyString(),
                                     any(Principal.class), any());
      assertNull(captor.getValue().getAestheticType(), "which is the event's hide-all");
      assertDoesNotThrow(
         () -> h.service.setVisibility("tok", principal(), "Chart1", "legend", "", true, ""),
         "and showing with a blank target is showing them all, not a refused single show");
   }

   /**
    * And it costs nothing. {@code ViewsheetSessionService.mutate} adds an undo checkpoint and
    * broadcasts a refresh in a {@code finally} — deliberately, because a composer service can
    * partially apply before it ERRORs — so resolving the target inside the mutation would spend
    * a Ctrl+Z step and a Composer refresh on a call that applied nothing.
    */
   @Test
   void refusingALegendTargetDoesNotOpenAMutation() throws Exception {
      Harness h = harness(mock(ChartVSAssembly.class));

      assertThrows(
         Exception.class,
         () -> h.service.setVisibility("tok", principal(), "Chart1", "legend", "Category", false,
                                       ""));

      verify(h.sessions, never()).mutate(anyString(), any(Principal.class), any());
   }

   /**
    * {@code aestheticType} is the whole fix: without it the event hides every legend. Pinned on
    * the field map for the same reason the titles event is — an all-default event is the
    * destructive case, so the values have to be asserted rather than assumed.
    */
   @Test
   void aNamedLegendCarriesItsChannelOntoTheEvent() {
      ChartRegionResolver.Legends legends = twoLegends();
      VSChartLegendsVisibilityEvent event = new ObjectMapper().convertValue(
         ChartElementService.legendFields("Chart1", false, legends.legends().get(1), legends),
         VSChartLegendsVisibilityEvent.class);

      assertTrue(event.isHide());
      assertEquals("Customer:Reseller", event.getField());
      assertEquals("Shape", event.getAestheticType(),
                   "without this the event hides every legend on the chart");
   }

   /**
    * The Composer's own rule, over the same legend list: a colour legend that is not rendered
    * separately is drawn inside another legend's box, so hiding that box has to take it too.
    */
   @Test
   void marksTheColourLegendMergedOnTheSameTestTheComposerMakes() {
      ChartRegionResolver.Legends legends = twoLegends();

      // One colour legend, hiding the colour legend: merged, so its descriptor goes too.
      assertTrue((Boolean) ChartElementService
         .legendFields("Chart1", false, legends.legends().get(0), legends).get("colorMerged"));

      // One colour legend, hiding the SHAPE legend: the colour legend stands on its own and must
      // survive. This is the case StyleBI's own bug 58639 was about.
      assertFalse((Boolean) ChartElementService
         .legendFields("Chart1", false, legends.legends().get(1), legends).get("colorMerged"));
   }

   private static ChartRegionResolver.Legends twoLegends() {
      return new ChartRegionResolver.Legends(
         java.util.List.of(
            new ChartRegionResolver.LegendTarget("Customer:Region", "Color",
                                                 java.util.List.of(), false),
            new ChartRegionResolver.LegendTarget("Customer:Reseller", "Shape",
                                                 java.util.List.of(), false)),
         true);
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
    * {@code ChartElementService.titleFields()} maps a no-target hide specifically to
    * {@code "chart-title"} — there is no branch that hides every title. This used to run silently
    * and report "Hid all titles on Chart1" while only ever hiding the chart's own title; the x/y
    * axis titles stayed fully visible — confirmed live 2026-09-02.
    */
   @Test
   void refusesToHideEveryTitleWithNoTarget() {
      Harness h = harness(mock(ChartVSAssembly.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.setVisibility("tok", principal(), "Chart1", "title", null, false, ""));

      assertTrue(thrown.getMessage().contains("no 'target'"));
      verifyNoInteractions(h.titles);
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
      Harness h = harness(resizableChart(1, 1));

      h.service.resizePlot("tok", principal(), "Chart1", 2d, true, false, "");

      ArgumentCaptor<VSChartPlotResizeEvent> captor =
         ArgumentCaptor.forClass(VSChartPlotResizeEvent.class);
      verify(h.plot).eventHandler(eq("rt1"), captor.capture(), anyString(),
                                  any(Principal.class), any());
      assertEquals(2d, captor.getValue().getSizeRatio());
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

   /**
    * The ratio scales the plot's MINIMUM size — {@code VGraphPair} does
    * {@code minPlotHeight *= heightRatio} — so a value above 1 enlarges the plot and makes it
    * scrollable, and a value at or below 1 usually changes nothing visible because the plot
    * already fills the space.
    *
    * <p>This test previously asserted that 1.5 was <em>refused</em>, which certified the tool's
    * original contract: "the plot's share of the assembly, 0 to 1". That contract was wrong, and
    * because the validation enforced it, the tool could only ever be handed values from the
    * ineffective range — which is why it looked inert. Found live on local-1196.
    */
   @Test
   void acceptsARatioAboveOneBecauseThatIsTheRangeThatDoesAnything() throws Exception {
      Harness h = harness(resizableChart(1, 1));

      h.service.resizePlot("tok", principal(), "Chart1", 1.5, true, false, "");

      ArgumentCaptor<VSChartPlotResizeEvent> captor =
         ArgumentCaptor.forClass(VSChartPlotResizeEvent.class);
      verify(h.plot).eventHandler(eq("rt1"), captor.capture(), anyString(),
                                  any(Principal.class), any());
      assertEquals(1.5, captor.getValue().getSizeRatio());
   }

   /**
    * The lower bound. Below the chart's own baseline the write is stored and inert -
    * {@code VGraphPair} re-derives the ratio only while {@code ratio / initialRatio} is at least
    * 1 - so it is refused rather than answered with a success that changes nothing.
    */
   @Test
   void refusesARatioBelowTheChartsOwnBaseline() throws Exception {
      Harness h = harness(resizableChart(2.75, 2.75));

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.resizePlot("tok", principal(), "Chart1", 2d, true, false, ""));

      assertTrue(thrown.getMessage().contains("2.75"), "the refusal has to name the bound");
      assertTrue(thrown.getMessage().contains("reset"), "and the way to make the plot smaller");
      verifyNoInteractions(h.plot);
      verify(h.sessions, never()).mutate(anyString(), any(Principal.class), any());
   }

   @Test
   void acceptsARatioExactlyAtTheBaseline() throws Exception {
      Harness h = harness(resizableChart(2.75, 2.75));

      h.service.resizePlot("tok", principal(), "Chart1", 2.75, true, false, "");

      verify(h.plot).eventHandler(eq("rt1"), any(VSChartPlotResizeEvent.class), anyString(),
                                  any(Principal.class), any());
   }

   /**
    * {@code VSChartPlotResizeService} applies one ratio to <em>both</em> directions on a circular
    * chart, dividing it by each direction's own baseline, so both bounds have to hold. Checking
    * only the direction the caller named would let half of such a write through inert.
    */
   @Test
   void checksBothBaselinesOnAChartThatResizesSquare() {
      ChartVSAssembly circular = resizableChart(3, 1);
      when(circular.getVSChartInfo().getChartType()).thenReturn(GraphTypes.CHART_CIRCULAR);
      Harness h = harness(circular);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.resizePlot("tok", principal(), "Chart1", 2d, true, false, ""));

      assertTrue(thrown.getMessage().contains("width"),
                 "a vertical resize on a square chart is still bounded by the width baseline");
   }

   /**
    * A chart with no info has neither baseline nor maximum to check against, so the range check
    * has nothing to say and must stand aside rather than throw on the way to reading a bound that
    * does not exist.
    */
   @Test
   void aChartWithNoInfoIsLeftToTheBluntGuard() throws Exception {
      Harness h = harness(mock(ChartVSAssembly.class));

      h.service.resizePlot("tok", principal(), "Chart1", 2d, true, false, "");

      verify(h.plot).eventHandler(eq("rt1"), any(VSChartPlotResizeEvent.class), anyString(),
                                  any(Principal.class), any());
   }

   @Test
   void refusesANonPositiveOrAbsurdRatio() {
      Harness h = harness(mock(ChartVSAssembly.class));

      assertThrows(Exception.class,
                   () -> h.service.resizePlot("tok", principal(), "Chart1", 0d, true, false, ""));
      assertThrows(Exception.class,
                   () -> h.service.resizePlot("tok", principal(), "Chart1", -1d, true, false, ""));
      // An unbounded multiplier on the minimum plot size is an allocation risk for a tool driven
      // by a model, so absurd values are refused rather than attempted.
      assertThrows(Exception.class,
                   () -> h.service.resizePlot("tok", principal(), "Chart1", 500d, true, false, ""));
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

      assertTrue(String.valueOf(h.service.vocabulary().get("titleTargets")).contains("y2"),
                 "without an assembly this is the flat what-can-this-tool-address answer");
   }

   /**
    * <b>Why the assembly form exists.</b> The flat vocabulary offered {@code y2} on every chart
    * in the product, and nothing downstream checked — which is how a caller was talked into
    * reading and then writing an axis that is not there. Named a chart, it answers about that
    * chart.
    */
   @Test
   void vocabularyFiltersTitleTargetsToTheAxesTheChartActuallyHas() throws Exception {
      Harness h = harness(boundChart(false));

      Map<String, Object> out = h.service.vocabulary("tok", principal(), "Chart1");

      assertEquals(java.util.List.of("x", "y"), out.get("axes"));
      assertEquals(java.util.List.of("x", "y", "chart"), out.get("titleTargets"),
                   "no y2 on the chart means no y2 in the vocabulary - and the chart title, " +
                   "which is not an axis title, stays");
   }

   @Test
   void vocabularyKeepsY2WhenAMeasureActuallyUsesTheSecondaryAxis() throws Exception {
      Harness h = harness(boundChart(true));

      Map<String, Object> out = h.service.vocabulary("tok", principal(), "Chart1");

      assertEquals(java.util.List.of("x", "y", "y2"), out.get("axes"),
                   "filtering must not amount to always hiding y2");
   }

   /**
    * An inference is not a measurement. Reporting the basis is what lets a caller weigh the
    * answer instead of trusting it, which is the difference between this and the phantom it
    * replaces.
    */
   @Test
   void vocabularySaysWhetherTheAxesWereMeasuredOrInferred() throws Exception {
      Harness h = harness(boundChart(false));

      Map<String, Object> out = h.service.vocabulary("tok", principal(), "Chart1");

      assertEquals(false, out.get("axesMeasured"), "a mocked runtime has no laid-out graph");
      assertTrue(String.valueOf(out.get("axesBasis")).contains("binding"));
   }

   @Test
   void readingTheVocabularyForOneChartSpendsNoUndoCheckpoint() throws Exception {
      Harness h = harness(boundChart(false));

      h.service.vocabulary("tok", principal(), "Chart1");

      verify(h.sessions, never()).mutate(anyString(), any(Principal.class), any());
   }

   // ── plot sizing: the read ─────────────────────────────────────────────────

   /**
    * <b>The point of this method.</b> A plot resize had no observable of any kind — the ratio
    * scales the plot's minimum size, which the browser shows as scrollbars, while the agent's
    * render is fitted to the assembly box — so the ratio and its resized flag have to come back
    * verbatim for the write to be checkable at all.
    */
   @Test
   void readsBackTheRatioAndTheFlagThatGatesIt() throws Exception {
      Harness h = harness(chart(3d, true));

      Map<String, Object> result = h.service.readPlotSize("tok", principal(), "Chart1");

      @SuppressWarnings("unchecked")
      Map<String, Object> height = (Map<String, Object>) result.get("height");
      assertEquals(3d, height.get("ratio"));
      assertEquals(true, height.get("resized"), "a ratio with this false is stored but inert");
      assertEquals(false, result.get("default"));
      assertEquals("Chart1", result.get("assembly"));
   }

   @Test
   void reportsAnUnresizedChartAsDefault() throws Exception {
      Harness h = harness(chart(1d, false));

      Map<String, Object> result = h.service.readPlotSize("tok", principal(), "Chart1");

      assertEquals(true, result.get("default"));
   }

   /**
    * Reading must go through {@code read}, not {@code mutate}. {@code mutate} opens a checkpoint
    * and broadcasts, so reading through it would put a stray Ctrl+Z step in the user's own
    * history for having <em>looked</em> at the plot size, and tell their Composer to refresh for
    * a change that never happened.
    */
   @Test
   void readingSpendsNoUndoCheckpoint() throws Exception {
      Harness h = harness(chart(1d, false));

      h.service.readPlotSize("tok", principal(), "Chart1");

      verify(h.sessions, never()).mutate(anyString(), any(Principal.class), any());
      verify(h.sessions, times(1)).read(anyString(), any(Principal.class), any());
   }

   /**
    * Geometry needs a laid-out graph. Saying so beats returning zeroes, which would read as a
    * plot that had collapsed to nothing.
    */
   @Test
   void saysWhyGeometryIsMissingRatherThanReturningZeroes() throws Exception {
      Harness h = harness(chart(1d, false));

      Map<String, Object> result = h.service.readPlotSize("tok", principal(), "Chart1");

      assertTrue(result.containsKey("geometryUnavailable"),
                 "no sandbox means no laid-out graph, and that has to be stated");
      assertFalse(result.containsKey("plot"), "a missing plot must not be reported as 0x0");
      assertTrue(result.containsKey("width"),
                 "the ratios are still readable without geometry, and are the write's read-back");
   }

   @Test
   void refusesToReadPlotSizeOfANonChart() {
      Harness h = harness(mock(TextVSAssembly.class));

      Exception thrown = assertThrows(
         Exception.class, () -> h.service.readPlotSize("tok", principal(), "Text1"));

      assertTrue(thrown.getMessage().contains("Text1"));
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

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Read<?> read = invocation.getArgument(2);
            return read.run(rvs, "rt1", null);
         }).when(sessions).read(anyString(), any(Principal.class), any());
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

   /** A chart bound with a dimension on x and one or two measures on y. */
   private static ChartVSAssembly boundChart(boolean secondaryAxis) {
      // Built before any stubbing: mocking inside a when(...) argument is nested stubbing.
      VSChartAggregateRef primary = mock(VSChartAggregateRef.class);
      when(primary.isSecondaryY()).thenReturn(false);
      ChartRef[] y = new ChartRef[] { primary };

      if(secondaryAxis) {
         VSChartAggregateRef secondary = mock(VSChartAggregateRef.class);
         when(secondary.isSecondaryY()).thenReturn(true);
         y = new ChartRef[] { primary, secondary };
      }

      ChartRef[] x = new ChartRef[] { mock(VSChartDimensionRef.class) };
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.getXFields()).thenReturn(x);
      when(info.getYFields()).thenReturn(y);
      when(info.isInvertedGraph()).thenReturn(false);
      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(info);
      return assembly;
   }

   /** A chart whose info carries one height ratio, which is what the read has to hand back. */
   private static ChartVSAssembly chart(double heightRatio, boolean heightResized) {
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.getUnitHeightRatio()).thenReturn(heightRatio);
      when(info.isHeightResized()).thenReturn(heightResized);
      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(info);
      return assembly;
   }

   /** A chart whose plot-resize baselines are the minimums the Composer's slider is drawn with. */
   private static ChartVSAssembly resizableChart(double initialWidth, double initialHeight) {
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.getInitialWidthRatio()).thenReturn(initialWidth);
      when(info.getInitialHeightRatio()).thenReturn(initialHeight);
      when(info.getChartType()).thenReturn(GraphTypes.CHART_BAR);
      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(info);
      return assembly;
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
