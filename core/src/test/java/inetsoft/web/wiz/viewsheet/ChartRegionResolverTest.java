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

import java.util.ArrayList;
import java.util.List;

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
         () -> ChartRegionResolver.requireLegend(legends(1, true), 7));

      assertTrue(thrown.getMessage().contains("1 legend"), "name the range");
      assertTrue(thrown.getMessage().contains("7"), "and what was asked for");
      assertTrue(thrown.getMessage().contains("get_chart_aesthetics"),
                 "and where to look it up");
      // "the valid indexes are 0 - '7'" read as a range in live output. A dash between the bound
      // and the offending index is exactly the wrong punctuation here.
      assertFalse(thrown.getMessage().contains("0 - "), "the bound must not read as a range");
   }

   @Test
   void refusesANegativeLegendIndex() {
      assertThrows(
         IllegalArgumentException.class,
         () -> ChartRegionResolver.requireLegend(legends(2, true), -1));
   }

   @Test
   void spellsOutARangeWhenThereIsMoreThanOneLegend() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartRegionResolver.requireLegend(legends(3, true), 9));

      assertTrue(thrown.getMessage().contains("0 to 2"), "a real range, spelled out");
   }

   @Test
   void allowsEveryIndexInRange() {
      assertDoesNotThrow(
         () -> ChartRegionResolver.requireLegend(legends(2, true), 1));
   }

   @Test
   void refusesAnyLegendOnAChartThatRendersNone() {
      assertThrows(
         IllegalArgumentException.class,
         () -> ChartRegionResolver.requireLegend(legends(0, true), 0));
   }

   /**
    * An unmeasured count is zero for want of a graph, not because the chart has no legends.
    * Refusing on it would block a legitimate write whenever the layout was unavailable.
    */
   @Test
   void doesNotRefuseWhenTheCountCouldNotBeMeasured() {
      assertDoesNotThrow(
         () -> ChartRegionResolver.requireLegend(legends(0, false), 5));
   }

   // ── naming a legend ────────────────────────────────────────────────────────────
   //
   // The index above is the region tools' vocabulary. These are the visibility tool's: it names
   // the aesthetic FIELD, and an unresolved one is the case that hid every legend.

   @Test
   void resolvesALegendByItsField() {
      ChartRegionResolver.LegendTarget legend =
         ChartRegionResolver.requireLegendField(twoLegends(), "Customer:Reseller");

      assertEquals("Shape", legend.aestheticType(), "the channel is what the event needs");
      assertEquals("shape", legend.channel(), "reported lower-cased, as the aesthetic tools do");
   }

   @Test
   void resolvesALegendFieldIgnoringCaseAndSpace() {
      assertEquals(
         "Color",
         ChartRegionResolver.requireLegendField(twoLegends(), "  customer:region ").aestheticType(),
         "forgiving where the intent is unambiguous");
   }

   /** A caller who says "the color legend" means the one colour legend, when there is only one. */
   @Test
   void resolvesALegendByItsChannelWhenOnlyOneIsOnThatChannel() {
      assertEquals("Customer:Region",
                   ChartRegionResolver.requireLegendField(twoLegends(), "color").field());
      assertEquals("Customer:Reseller",
                   ChartRegionResolver.requireLegendField(twoLegends(), "Shape").field());
   }

   /**
    * A line chart renders its shape aesthetic as a Line legend, while get_chart_aesthetics reports
    * the field on the shape channel — so a caller who read the aesthetics and said "shape" is
    * naming this legend correctly. The event folds Shape, Line and Texture onto one descriptor
    * anyway, so there is nothing ambiguous to protect here. Found live on a line chart.
    */
   @Test
   void takesShapeToMeanTheLegendALineChartRendersAsLine() {
      ChartRegionResolver.Legends legends = new ChartRegionResolver.Legends(
         List.of(new ChartRegionResolver.LegendTarget("Customer:Region", "Color", List.of(), false),
                 new ChartRegionResolver.LegendTarget("Customer:Reseller", "Line", List.of(),
                                                      false)),
         true);

      assertEquals("Customer:Reseller",
                   ChartRegionResolver.requireLegendField(legends, "shape").field());
      assertEquals("Customer:Reseller",
                   ChartRegionResolver.requireLegendField(legends, "texture").field(),
                   "the same fold in the other direction");
      assertEquals("Customer:Region",
                   ChartRegionResolver.requireLegendField(legends, "color").field(),
                   "and colour is still its own family");
   }

   @Test
   void refusesAChannelNameWhenTwoLegendsShareIt() {
      ChartRegionResolver.Legends legends = new ChartRegionResolver.Legends(
         List.of(new ChartRegionResolver.LegendTarget("Sales", "Color", List.of(), false),
                 new ChartRegionResolver.LegendTarget("Region", "Color", List.of(), false)),
         true);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartRegionResolver.requireLegendField(legends, "color"));

      assertTrue(thrown.getMessage().contains("more than one"), "say why it cannot be resolved");
      assertTrue(thrown.getMessage().contains("Sales") && thrown.getMessage().contains("Region"),
                 "and name both, so the caller can pick one");
   }

   /**
    * The finding this whole path exists for: a target the chart has never heard of did not no-op,
    * it hid the chart's real legends and reported success naming the one asked for. So an
    * unknown field has to be refused, not passed through.
    */
   @Test
   void refusesAFieldNoLegendHas() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartRegionResolver.requireLegendField(twoLegends(), "NoSuchField"));

      assertTrue(thrown.getMessage().contains("NoSuchField"), "name what was asked for");
      assertTrue(thrown.getMessage().contains("Customer:Region") &&
                 thrown.getMessage().contains("Customer:Reseller"),
                 "and the legends it does have");
      assertTrue(thrown.getMessage().contains("color") && thrown.getMessage().contains("shape"),
                 "with their channels, since a channel is also accepted");
   }

   /**
    * Without a graph there is nothing to resolve against, and passing through would hide every
    * legend - the exact defect. Unlike the index check, silence here is not the safe side.
    */
   @Test
   void refusesToResolveAFieldWithNoLaidOutGraph() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartRegionResolver.requireLegendField(
            new ChartRegionResolver.Legends(List.of(), false), "Customer:Region"));

      assertTrue(thrown.getMessage().contains("without 'target'"),
                 "and say what to call instead");
   }

   @Test
   void refusesAnyFieldOnAChartThatRendersNoLegends() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartRegionResolver.requireLegendField(
            new ChartRegionResolver.Legends(List.of(), true), "Customer:Region"));

      assertTrue(thrown.getMessage().contains("no legends"), "say so plainly");
   }

   /** A legend with no field of its own keeps its slot, because the index addresses it. */
   @Test
   void keepsAnUnnamedLegendInThePositionTheIndexAddresses() {
      ChartRegionResolver.Legends legends = new ChartRegionResolver.Legends(
         List.of(new ChartRegionResolver.LegendTarget(null, null, List.of(), false),
                 new ChartRegionResolver.LegendTarget("Region", "Color", List.of(), false)),
         true);

      assertEquals(2, legends.count(), "both slots counted");
      assertDoesNotThrow(() -> ChartRegionResolver.requireLegend(legends, 1),
                         "and the empty slot's index is still addressable");
      assertEquals("Region", ChartRegionResolver.requireLegendField(legends, "color").field(),
                   "but only the described one can be named");
      assertThrows(IllegalArgumentException.class,
                   () -> ChartRegionResolver.requireLegendField(legends, ""),
                   "and the unnamed one is reachable by no target at all");
   }

   private static ChartRegionResolver.Legends twoLegends() {
      return new ChartRegionResolver.Legends(
         List.of(new ChartRegionResolver.LegendTarget("Customer:Region", "Color", List.of(), false),
                 new ChartRegionResolver.LegendTarget("Customer:Reseller", "Shape", List.of(),
                                                      false)),
         true);
   }

   /** A chart with {@code count} legends, for the index checks that predate fields. */
   private static ChartRegionResolver.Legends legends(int count, boolean measured) {
      List<ChartRegionResolver.LegendTarget> legends = new ArrayList<>();

      for(int i = 0; i < count; i++) {
         legends.add(
            new ChartRegionResolver.LegendTarget("Field" + i, "Color", List.of(), false));
      }

      return new ChartRegionResolver.Legends(legends, measured);
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
