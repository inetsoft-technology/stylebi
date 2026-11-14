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
package inetsoft.report.composition.graph;

import inetsoft.graph.element.*;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("core")
class ModernMarkSizesTest {
   @Test
   void bubblesCapAt8() {
      assertEquals(8, ModernMarkSizes.largest(GraphTypes.CHART_POINT, false, false, 15));
      assertEquals(8, ModernMarkSizes.largest(GraphTypes.CHART_POINT_STACK, false, false, 15));
      assertEquals(8, ModernMarkSizes.largest(GraphTypes.CHART_MAP, false, false, 15));
   }

   @Test
   void wordCloudsAndContoursKeepTheirs() {
      assertEquals(15, ModernMarkSizes.largest(GraphTypes.CHART_POINT, true, false, 15));
      assertEquals(15, ModernMarkSizes.largest(GraphTypes.CHART_POINT, false, true, 15));
   }

   @Test
   void relationEdgesCapAt6() {
      assertEquals(6, ModernMarkSizes.largest(GraphTypes.CHART_TREE, false, false, 15));
      assertEquals(6, ModernMarkSizes.largest(GraphTypes.CHART_NETWORK, false, false, 15));
      assertEquals(6, ModernMarkSizes.largest(GraphTypes.CHART_CIRCULAR, false, false, 15));
   }

   @Test
   void otherTypesKeepTheClassicLargest() {
      assertEquals(25, ModernMarkSizes.largest(GraphTypes.CHART_BAR, false, false, 25));
      assertEquals(15, ModernMarkSizes.largest(GraphTypes.CHART_LINE, false, false, 15));
   }

   @Test
   void aMilestoneIs3AndAGanttBarIsUntouched() {
      assertEquals(3, ModernMarkSizes.staticSize(GraphTypes.CHART_GANTT, new PointElement(), 9));
      assertEquals(15,
                   ModernMarkSizes.staticSize(GraphTypes.CHART_GANTT, new IntervalElement(), 15));
   }

   @Test
   void radarLinesAre3() {
      assertEquals(3, ModernMarkSizes.staticSize(GraphTypes.CHART_RADAR, new LineElement(), 1));
      assertEquals(3,
                   ModernMarkSizes.staticSize(GraphTypes.CHART_FILL_RADAR, new AreaElement(), 1));
   }

   @Test
   void otherTypesKeepTheClassicStaticSize() {
      assertEquals(1, ModernMarkSizes.staticSize(GraphTypes.CHART_LINE, new LineElement(), 1));
   }
}
