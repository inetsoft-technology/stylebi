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
}
