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
package inetsoft.report.internal.graph;

import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSMapInfo;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L3-Group2, findings G2-1/G2-2: {@code SwapXYBindingService.swapXYBinding} — a shared,
 * {@code @ClusterProxy} service both the native Composer's swap button and the wiz agent's
 * {@code swap_chart_axes} call — had no {@code GraphTypes}-based gate at all before this fix,
 * so a chart type {@link SwapXYBindingProcessor#swapChartXYFields()} cannot invert (e.g.
 * {@code mekko}) silently dropped a bound measure moving from {@code y} to {@code x} instead of
 * swapping it. Live-confirmed 2026-09-01: {@code swap_chart_axes} on a mekko chart returned
 * {@code ok:true} and deleted the {@code y} shelf's measure outright.
 */
@WizAgentTestSupport
class SwapXYBindingProcessorTest {
   @Test
   void refusesASwapThatWouldDropAYMeasureOnAMekkoChart() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_MEKKO);
      info.addXField(new VSChartDimensionRef());
      info.addYField(new VSChartAggregateRef());

      SwapXYBindingProcessor processor = new SwapXYBindingProcessor(info, null);

      assertThrows(IllegalStateException.class, processor::requireInvertible);
   }

   @Test
   void allowsASwapOnAChartTypeThatSupportsInversion() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      info.addXField(new VSChartDimensionRef());
      info.addYField(new VSChartAggregateRef());

      SwapXYBindingProcessor processor = new SwapXYBindingProcessor(info, null);

      assertDoesNotThrow(processor::requireInvertible);
   }

   @Test
   void allowsASwapWhenYHoldsOnlyDimensions() {
      // A dimension moving from y to x is never dropped -- swapChartXYFields' skip-and-continue
      // only applies to the measure branch -- so mekko is fine here despite not inverting.
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_MEKKO);
      info.addXField(new VSChartAggregateRef());
      info.addYField(new VSChartDimensionRef());

      SwapXYBindingProcessor processor = new SwapXYBindingProcessor(info, null);

      assertDoesNotThrow(processor::requireInvertible);
   }

   @Test
   void skipsTheCheckEntirelyForAMapChart() {
      // swapMapXYFields partitions x/y by measure/dimension unconditionally and never drops a
      // field -- the finding is specific to swapChartXYFields, so a MapInfo is exempt.
      VSMapInfo info = new VSMapInfo();
      info.addYField(new VSChartAggregateRef());

      SwapXYBindingProcessor processor = new SwapXYBindingProcessor(info, null);

      assertDoesNotThrow(processor::requireInvertible);
   }

   /**
    * L3-Group2 G2-2 (the geo/all-measure half of the same finding, re-examined): recorded as
    * "blocked, unverified" because the connected live environment had no geo-typed column to
    * construct a real {@code VSMapInfo} chart against. The claim itself: "a map needs a dimension
    * somewhere on x/y to draw from, and swapping two all-measure shelves cannot introduce one" --
    * i.e. a map with zero dimensions on x/y (all-measure) stays zero-dimension after a swap. That
    * claim is true but not a *new* problem the swap causes: dimension *count* is conserved by
    * {@code swapMapXYFields()} (proven below and in the mixed-binding test above) -- it only moves
    * dimensions between x and y, never creates or destroys one. A zero-dimension map is invalid
    * before the swap and stays invalid after it, exactly as bad either way; the swap itself is a
    * safe no-op in this case (no exception, no further corruption), not a data-loss vector like
    * G2-1's mekko case. Whether a human or the agent can even construct a zero-dimension map chart
    * in the first place is a binding-construction question, not a swap-axes one, and is out of
    * this finding's scope.
    */
   @Test
   void swapMapXYFieldsIsASafeNoOpWhenBothShelvesAreAllMeasure() {
      VSMapInfo info = new VSMapInfo();
      VSChartAggregateRef xMeasure = new VSChartAggregateRef();
      xMeasure.setColumnValue("population");
      VSChartAggregateRef yMeasure = new VSChartAggregateRef();
      yMeasure.setColumnValue("sales");
      info.addXField(xMeasure);
      info.addYField(yMeasure);

      SwapXYBindingProcessor processor = new SwapXYBindingProcessor(info, null);

      assertDoesNotThrow(processor::process);
      assertEquals(1, info.getXFields().length);
      assertEquals(1, info.getYFields().length);
      assertTrue(java.util.Arrays.asList(info.getXFields()).contains(xMeasure));
      assertTrue(java.util.Arrays.asList(info.getYFields()).contains(yMeasure));
   }

   /**
    * The other half of G2-2's re-examination: proves the general claim (dimension swap, measure
    * passthrough, nothing dropped) with fields on both shelves, matching the original finding's
    * "x/y fields with measures" scenario -- REFUTED, not a gap: no live-session or plugin-side
    * change needed.
    */
   @Test
   void swapMapXYFieldsPreservesEveryFieldForAMixedDimensionAndMeasureBinding() {
      VSMapInfo info = new VSMapInfo();
      VSChartDimensionRef xDim = new VSChartDimensionRef();
      xDim.setGroupColumnValue("state");
      VSChartAggregateRef xMeasure = new VSChartAggregateRef();
      xMeasure.setColumnValue("population");
      VSChartDimensionRef yDim = new VSChartDimensionRef();
      yDim.setGroupColumnValue("county");
      VSChartAggregateRef yMeasure = new VSChartAggregateRef();
      yMeasure.setColumnValue("sales");

      info.addXField(xDim);
      info.addXField(xMeasure);
      info.addYField(yDim);
      info.addYField(yMeasure);

      SwapXYBindingProcessor processor = new SwapXYBindingProcessor(info, null);
      processor.process();

      // Every original field is still present somewhere -- none discarded.
      java.util.List<inetsoft.uql.viewsheet.graph.ChartRef> after = new java.util.ArrayList<>();
      after.addAll(java.util.Arrays.asList(info.getXFields()));
      after.addAll(java.util.Arrays.asList(info.getYFields()));
      assertEquals(4, after.size(), "no field should be dropped by a map axis swap");
      assertTrue(after.contains(xDim));
      assertTrue(after.contains(xMeasure));
      assertTrue(after.contains(yDim));
      assertTrue(after.contains(yMeasure));

      // Dimensions swap shelves; measures stay put -- the map-specific swap semantic.
      assertTrue(java.util.Arrays.asList(info.getXFields()).contains(yDim));
      assertTrue(java.util.Arrays.asList(info.getYFields()).contains(xDim));
      assertTrue(java.util.Arrays.asList(info.getXFields()).contains(xMeasure));
      assertTrue(java.util.Arrays.asList(info.getYFields()).contains(yMeasure));
   }
}
