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
package inetsoft.uql.viewsheet.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChartInfoSnapSupportTest {
   @Test
   void lineChartWithDimensionOnX() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_LINE);
      info.addXField(new VSChartDimensionRef());
      info.addYField(new VSChartAggregateRef());

      assertTrue(info.supportsSnapTooltip());
      assertTrue(info.supportsCombinedTooltip());
   }

   @Test
   void areaChartWithDimensionOnX() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_AREA_STACK);
      info.addXField(new VSChartDimensionRef());
      info.addYField(new VSChartAggregateRef());

      assertTrue(info.supportsSnapTooltip());
   }

   @Test
   void barChartWithDimensionOnX() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR_STACK);
      info.addXField(new VSChartDimensionRef());
      info.addYField(new VSChartAggregateRef());

      assertTrue(info.supportsSnapTooltip(), "Bar with dim on X should support snap");
      assertTrue(info.supportsCombinedTooltip());
   }

   @Test
   void flippedAreaChartWithMeasureOnXIsRejected() {
      // Measure on X, dimension on Y — snap is X-axis based, doesn't apply.
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_AREA);
      info.addXField(new VSChartAggregateRef());
      info.addYField(new VSChartDimensionRef());

      assertFalse(info.supportsSnapTooltip());
      // Combined is orientation-agnostic, still allowed.
      assertTrue(info.supportsCombinedTooltip());
   }

   @Test
   void unsupportedChartTypesAreRejected() {
      for(int type : new int[] { GraphTypes.CHART_PIE, GraphTypes.CHART_POINT,
                                  GraphTypes.CHART_RADAR, GraphTypes.CHART_TREEMAP }) {
         VSChartInfo info = new VSChartInfo();
         info.setChartType(type);
         info.addXField(new VSChartDimensionRef());

         assertFalse(info.supportsSnapTooltip(),
            "Chart type " + type + " should not support snap");
         assertFalse(info.supportsCombinedTooltip(),
            "Chart type " + type + " should not support combined");
      }
   }

   @Test
   void multiStyleChartIsRejected() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_LINE);
      info.setMultiStyles(true);
      info.addXField(new VSChartDimensionRef());

      assertFalse(info.supportsSnapTooltip());
      assertFalse(info.supportsCombinedTooltip());
   }

   @Test
   void emptyXAxisIsRejected() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_LINE);
      // no fields added

      assertFalse(info.supportsSnapTooltip());
   }
}
