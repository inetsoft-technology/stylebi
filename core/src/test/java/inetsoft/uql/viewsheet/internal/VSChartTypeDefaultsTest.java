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

import inetsoft.report.StyleConstants;
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

   @Test
   void theXGridFollowsTwoContinuousAxes() {
      for(int t : new int[] { GraphTypes.CHART_POINT, GraphTypes.CHART_POINT_STACK,
                              GraphTypes.CHART_SCATTER_CONTOUR }) {
         assertEquals(StyleConstants.THIN_LINE, VSChartTypeDefaults.xGridStyle(rt(t, true, true)),
                      "type " + t);
      }

      assertEquals(StyleConstants.NONE,
                   VSChartTypeDefaults.xGridStyle(rt(GraphTypes.CHART_POINT, false, true)));
      assertEquals(StyleConstants.NONE,
                   VSChartTypeDefaults.xGridStyle(rt(GraphTypes.CHART_POINT, true, false)));

      for(int t : new int[] { GraphTypes.CHART_BAR, GraphTypes.CHART_LINE,
                              GraphTypes.CHART_FUNNEL, GraphTypes.CHART_MAP }) {
         assertEquals(StyleConstants.NONE, VSChartTypeDefaults.xGridStyle(rt(t, true, true)),
                      "type " + t);
      }
   }

   @Test
   void axisTitlesFollowTheType() {
      String[] axes = { "x", "x2", "y", "y2" };
      int[] everyAxis = {
         GraphTypes.CHART_POINT, GraphTypes.CHART_POINT_STACK, GraphTypes.CHART_SCATTER_CONTOUR,
         GraphTypes.CHART_PARETO, GraphTypes.CHART_MEKKO
      };

      for(int t : everyAxis) {
         for(String axis : axes) {
            assertTrue(VSChartTypeDefaults.axisTitleVisible(rt(t, false, true), axis),
                       "vertical " + axis + " of " + t);
            assertTrue(VSChartTypeDefaults.axisTitleVisible(rt(t, true, false), axis),
                       "horizontal " + axis + " of " + t);
         }
      }

      // a box plot names its value axis only
      ResolvedChartType vertical = rt(GraphTypes.CHART_BOXPLOT, false, true);
      assertFalse(VSChartTypeDefaults.axisTitleVisible(vertical, "x"), "vertical box x");
      assertFalse(VSChartTypeDefaults.axisTitleVisible(vertical, "x2"), "vertical box x2");
      assertTrue(VSChartTypeDefaults.axisTitleVisible(vertical, "y"), "vertical box y");
      assertTrue(VSChartTypeDefaults.axisTitleVisible(vertical, "y2"), "vertical box y2");

      ResolvedChartType horizontal = rt(GraphTypes.CHART_BOXPLOT, true, false);
      assertTrue(VSChartTypeDefaults.axisTitleVisible(horizontal, "x"), "horizontal box x");
      assertTrue(VSChartTypeDefaults.axisTitleVisible(horizontal, "x2"), "horizontal box x2");
      assertFalse(VSChartTypeDefaults.axisTitleVisible(horizontal, "y"), "horizontal box y");
      assertFalse(VSChartTypeDefaults.axisTitleVisible(horizontal, "y2"), "horizontal box y2");

      int[] noAxis = {
         GraphTypes.CHART_BAR, GraphTypes.CHART_LINE, GraphTypes.CHART_AREA,
         GraphTypes.CHART_INTERVAL, GraphTypes.CHART_WATERFALL, GraphTypes.CHART_CANDLE,
         GraphTypes.CHART_STOCK, GraphTypes.CHART_PIE, GraphTypes.CHART_DONUT,
         GraphTypes.CHART_RADAR, GraphTypes.CHART_FUNNEL, GraphTypes.CHART_GANTT,
         GraphTypes.CHART_TREEMAP, GraphTypes.CHART_TREE, GraphTypes.CHART_MAP,
         GraphTypes.CHART_AUTO
      };

      for(int t : noAxis) {
         for(String axis : axes) {
            assertFalse(VSChartTypeDefaults.axisTitleVisible(rt(t, false, true), axis),
                        "vertical " + axis + " of " + t);
            assertFalse(VSChartTypeDefaults.axisTitleVisible(rt(t, true, false), axis),
                        "horizontal " + axis + " of " + t);
         }
      }
   }

   private static ResolvedChartType rt(int type, boolean x, boolean y) {
      return new ResolvedChartType(type, x, y, List.of(), List.of());
   }
}
