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

import inetsoft.graph.VGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * The binding fallback and the refusal. The laid-out-graph path is the primary source and needs a
 * real graph, so it is covered by the live case rather than here; what is testable in isolation is
 * the inference these tests pin — including the axis flip that is the whole reason the binding is
 * a fallback and not the primary source.
 */
@Tag("core")
class ChartRegionResolverTest {
   @Test
   void infersTheBoundAxesWhenThereIsNoLaidOutGraph() {
      ChartRegionResolver.Axes axes = resolve(info(true, false, false));

      assertEquals(java.util.List.of("x", "y"), axes.ordered());
      assertFalse(axes.measured(), "an inference must not be reported as a measurement");
      assertTrue(axes.basis().contains("binding"), "the caller has to be able to tell which");
   }

   @Test
   void reportsY2OnlyWhenAMeasureUsesTheSecondaryAxis() {
      assertFalse(resolve(info(true, false, false)).has("y2"),
                  "this is the phantom the whole class exists to stop");
      assertTrue(resolve(info(true, true, false)).has("y2"));
   }

   /**
    * The flip. The secondary axis belongs to a measure and lands opposite whichever shelf holds
    * the measures, so an inverted graph puts it on the top axis. Reading {@code isSecondaryY} off
    * the y shelf regardless would report x2 as y2 — a plausible wrong answer, which is exactly
    * the failure mode being fixed.
    */
   @Test
   void anInvertedGraphPutsTheSecondaryAxisOnTopNotOnTheRight() {
      ChartRegionResolver.Axes axes = resolve(info(true, false, true, true));

      assertTrue(axes.has("x2"), "measures are on x when inverted, so the secondary is x2");
      assertFalse(axes.has("y2"));
   }

   @Test
   void anUnboundChartHasNoAxesAtAll() {
      ChartRegionResolver.Axes axes = resolve(info(false, false, false));

      assertTrue(axes.ordered().isEmpty());
   }

   /**
    * {@code ChartRegionHandler.getAxisArea} accepts the long area names as well as the short
    * ones, so both have to land on the same answer here. {@code right_y_axis} is the alias a
    * caller reaching for a phantom y2 would most naturally use.
    */
   @Test
   void foldsTheLongAreaNamesOntoTheShortOnes() {
      assertEquals("y2", ChartRegionResolver.canonical("right_y_axis"));
      assertEquals("x2", ChartRegionResolver.canonical("TOP_X_AXIS"));
      assertEquals("y", ChartRegionResolver.canonical("left_y_axis"));
      assertEquals("x", ChartRegionResolver.canonical(" bottom_x_axis "));
      assertTrue(resolve(info(true, true, false)).has("right_y_axis"),
                 "the alias must reach the same axis the short name does");
   }

   @Test
   void refusalNamesTheAxesTheChartDoesHaveAndWhereThatCameFrom() {
      ChartRegionResolver.Axes axes = resolve(info(true, false, false));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartRegionResolver.requireAxis(axes, "axis", "y2"));

      assertTrue(thrown.getMessage().contains("y2"), "name what was asked for");
      assertTrue(thrown.getMessage().contains("x, y"), "and what is actually there");
      assertTrue(thrown.getMessage().contains("binding"), "and on what basis");
   }

   @Test
   void anExistingAxisIsNotRefused() {
      ChartRegionResolver.Axes axes = resolve(info(true, false, false));

      assertDoesNotThrow(() -> ChartRegionResolver.requireAxis(axes, "axis", "y"));
      assertDoesNotThrow(() -> ChartRegionResolver.requireAxis(axes, "axis", "left_y_axis"));
   }

   // ── the graph, which may only add ─────────────────────────────────────────

   /**
    * <b>The live bug this class shipped with.</b> The first version read
    * {@code getAxesAt(RIGHT_AXIS).length > 0} as proof of a y2, and reported one on an ordinary
    * single-measure bar chart — so the phantom went straight through the guard, confirmed against
    * a real runtime. {@code RectCoord.createAxis} builds {@code yaxis2} unconditionally and keeps
    * it whenever it carries grid lines, so an axis object at the right proves nothing; what
    * distinguishes a real secondary axis is having its own scale, which the same method records by
    * leaving {@code primaryAxis} null.
    */
   @Test
   void aRightAxisThatMirrorsThePrimaryIsNotAY2() {
      ChartRegionResolver.Axes axes = resolveWithGraph(info(true, false, false), true);

      assertFalse(axes.has("y2"),
                  "a grid-line carrier at the right is not a secondary axis");
      assertEquals(java.util.List.of("x", "y"), axes.ordered());
      assertTrue(axes.measured());
   }

   @Test
   void aRightAxisWithItsOwnScaleIsAY2EvenWhenTheBindingDoesNotShowOne() {
      // Date comparison and the percent scale both create a secondary scale the binding never
      // mentions, which is the reason the graph is consulted at all.
      ChartRegionResolver.Axes axes = resolveWithGraph(info(true, false, false), false);

      assertTrue(axes.has("y2"));
      assertTrue(axes.measured());
   }

   /**
    * The graph must never subtract. A hidden axis leaves the coordinate entirely, and refusing to
    * format or unhide a real axis because it is currently hidden would be worse than the phantom.
    */
   @Test
   void anEmptyGraphDoesNotRemoveTheBoundAxes() {
      VGraph graph = mock(VGraph.class);
      RectCoord coord = mock(RectCoord.class);
      when(graph.getCoordinate()).thenReturn(coord);
      when(graph.getAxesAt(anyInt())).thenReturn(new DefaultAxis[0]);

      ChartRegionResolver.Axes axes = resolve(info(true, false, false), graph);

      assertEquals(java.util.List.of("x", "y"), axes.ordered());
   }

   // ── legends ────────────────────────────────────────────────────────────────────

   /**
    * The bound that was missing: an out-of-range legend index used to reach StyleBI and come back
    * as a raw HTTP 500 page, in a tool whose whole purpose is to be called before writing.
    */
   @Test
   void refusesALegendIndexOutsideTheChartsRange() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartRegionResolver.requireLegend(new ChartRegionResolver.Legends(1, true), 7));

      assertTrue(thrown.getMessage().contains("1 legend"), "name the range");
      assertTrue(thrown.getMessage().contains("7"), "and what was asked for");
      assertTrue(thrown.getMessage().contains("get_chart_aesthetics"),
                 "and where to look it up");
   }

   @Test
   void refusesANegativeLegendIndex() {
      assertThrows(
         IllegalArgumentException.class,
         () -> ChartRegionResolver.requireLegend(new ChartRegionResolver.Legends(2, true), -1));
   }

   @Test
   void allowsEveryIndexInRange() {
      assertDoesNotThrow(
         () -> ChartRegionResolver.requireLegend(new ChartRegionResolver.Legends(2, true), 1));
   }

   @Test
   void refusesAnyLegendOnAChartThatRendersNone() {
      assertThrows(
         IllegalArgumentException.class,
         () -> ChartRegionResolver.requireLegend(new ChartRegionResolver.Legends(0, true), 0));
   }

   /**
    * An unmeasured count is zero for want of a graph, not because the chart has no legends.
    * Refusing on it would block a legitimate write whenever the layout was unavailable.
    */
   @Test
   void doesNotRefuseWhenTheCountCouldNotBeMeasured() {
      assertDoesNotThrow(
         () -> ChartRegionResolver.requireLegend(new ChartRegionResolver.Legends(0, false), 5));
   }

   private static ChartRegionResolver.Axes resolveWithGraph(VSChartInfo info, boolean mirrored) {
      DefaultAxis right = mock(DefaultAxis.class);

      if(mirrored) {
         when(right.getPrimaryAxis()).thenReturn(mock(DefaultAxis.class));
      }

      DefaultAxis[] rightAxes = new DefaultAxis[] { right };
      DefaultAxis[] none = new DefaultAxis[0];
      RectCoord coord = mock(RectCoord.class);
      VGraph graph = mock(VGraph.class);

      when(graph.getCoordinate()).thenReturn(coord);
      when(graph.getAxesAt(anyInt())).thenReturn(none);
      when(graph.getAxesAt(Coordinate.RIGHT_AXIS)).thenReturn(rightAxes);
      return resolve(info, graph);
   }

   /**
    * Reaches the graph branch without a sandbox by handing the resolver a graph directly — the
    * sandbox plumbing is the live case's business; the decision rule is what these tests pin.
    */
   private static ChartRegionResolver.Axes resolve(VSChartInfo info, VGraph graph) {
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      return ChartRegionResolver.resolve(chart, graph);
   }

   private static ChartRegionResolver.Axes resolve(VSChartInfo info) {
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      // A mocked runtime returns an empty Optional for the sandbox, so there is no laid-out
      // graph and the resolver falls back to the binding -- the path under test.
      return ChartRegionResolver.resolve(mock(RuntimeViewsheet.class), chart);
   }

   private static VSChartInfo info(boolean bound, boolean secondary, boolean inverted) {
      return info(bound, secondary, inverted, false);
   }

   private static VSChartInfo info(boolean bound, boolean secondary, boolean inverted,
                                   boolean secondaryOnX)
   {
      // Every ref is built before any stubbing starts: mocking inside a when(...) argument is
      // nested stubbing, which Mockito rejects as UnfinishedStubbing.
      ChartRef[] x = !bound
         ? new ChartRef[0]
         : new ChartRef[] { secondaryOnX ? aggregate(true) : mock(VSChartDimensionRef.class) };
      ChartRef[] y = bound ? new ChartRef[] { aggregate(secondary) } : new ChartRef[0];
      VSChartInfo info = mock(VSChartInfo.class);

      when(info.isInvertedGraph()).thenReturn(inverted);
      when(info.getXFields()).thenReturn(x);
      when(info.getYFields()).thenReturn(y);
      return info;
   }

   private static VSChartAggregateRef aggregate(boolean secondary) {
      VSChartAggregateRef ref = mock(VSChartAggregateRef.class);
      when(ref.isSecondaryY()).thenReturn(secondary);
      return ref;
   }
}
