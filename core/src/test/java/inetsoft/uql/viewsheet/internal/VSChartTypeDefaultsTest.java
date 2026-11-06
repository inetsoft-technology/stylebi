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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.viewsheet.graph.GraphTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The per-type predicates, one per seeded value.
 */
@Tag("core")
class VSChartTypeDefaultsTest {
   @Test
   void oneContinuousAxisLosesItsLine() {
      int[] types = {
         GraphTypes.CHART_BAR, GraphTypes.CHART_BAR_STACK, GraphTypes.CHART_LINE,
         GraphTypes.CHART_AREA, GraphTypes.CHART_STEP, GraphTypes.CHART_INTERVAL,
         GraphTypes.CHART_WATERFALL, GraphTypes.CHART_PARETO, GraphTypes.CHART_BOXPLOT,
         GraphTypes.CHART_CANDLE, GraphTypes.CHART_STOCK, GraphTypes.CHART_MEKKO,
         GraphTypes.CHART_FUNNEL, GraphTypes.CHART_POINT, GraphTypes.CHART_AUTO
      };

      for(int t : types) {
         assertFalse(VSChartTypeDefaults.valueAxisLineVisible(rt(t, false, true)), "vertical " + t);
         assertFalse(VSChartTypeDefaults.valueAxisLineVisible(rt(t, true, false)), "horizontal " + t);
      }
   }

   @Test
   void twoContinuousAxesKeepTheirLines() {
      for(int t : new int[] { GraphTypes.CHART_POINT, GraphTypes.CHART_SCATTER_CONTOUR }) {
         assertTrue(VSChartTypeDefaults.valueAxisLineVisible(rt(t, true, true)), "type " + t);
      }
   }

   @Test
   void nonRectTypesAndGanttKeepTheClassicLine() {
      int[] types = {
         GraphTypes.CHART_PIE, GraphTypes.CHART_DONUT, GraphTypes.CHART_RADAR,
         GraphTypes.CHART_FILL_RADAR, GraphTypes.CHART_TREEMAP, GraphTypes.CHART_SUNBURST,
         GraphTypes.CHART_TREE, GraphTypes.CHART_NETWORK, GraphTypes.CHART_CIRCULAR,
         GraphTypes.CHART_MAP, GraphTypes.CHART_GANTT
      };

      for(int t : types) {
         assertTrue(VSChartTypeDefaults.valueAxisLineVisible(rt(t, false, true)), "vertical " + t);
         assertTrue(VSChartTypeDefaults.valueAxisLineVisible(rt(t, true, false)), "horizontal " + t);
         assertTrue(VSChartTypeDefaults.valueAxisLineVisible(rt(t, false, false)), "no measure " + t);
      }
   }

   private static ResolvedChartType rt(int type, boolean x, boolean y) {
      return new ResolvedChartType(type, x, y, List.of(), List.of());
   }
}
