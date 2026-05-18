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
package inetsoft.report.composition.region;

import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.graph.ChartRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlotAreaGanttDimsTest {
   @Test
   void yDimsMoveToHeadXDimsToTail() {
      // element.getDims() for a gantt with X bound puts X first, then Y.
      // The reorder must hoist Y to the front so the row identifier leads
      // the tooltip regardless of binding order.
      ChartInfo info = chartInfoWithY("reseller", "datagroup(city)");
      String[] result = PlotArea.ganttDimsYFirst(
         new String[] { "month", "reseller", "datagroup(city)" }, info);

      assertArrayEquals(
         new String[] { "reseller", "datagroup(city)", "month" }, result);
   }

   @Test
   void preservesRelativeOrderWithinHeadAndTail() {
      ChartInfo info = chartInfoWithY("y1", "y2");
      String[] result = PlotArea.ganttDimsYFirst(
         new String[] { "x1", "y1", "x2", "y2", "x3" }, info);

      assertArrayEquals(
         new String[] { "y1", "y2", "x1", "x2", "x3" }, result);
   }

   @Test
   void returnsUnchangedWhenNoDimMatchesY() {
      ChartInfo info = chartInfoWithY("reseller");
      String[] result = PlotArea.ganttDimsYFirst(
         new String[] { "month", "product" }, info);

      assertArrayEquals(new String[] { "month", "product" }, result);
   }

   @Test
   void handlesEmptyDims() {
      ChartInfo info = chartInfoWithY("reseller");
      String[] result = PlotArea.ganttDimsYFirst(new String[0], info);

      assertArrayEquals(new String[0], result);
   }

   private static ChartInfo chartInfoWithY(String... yFullNames) {
      ChartRef[] refs = new ChartRef[yFullNames.length];

      for(int i = 0; i < yFullNames.length; i++) {
         ChartRef ref = mock(ChartRef.class);
         when(ref.getFullName()).thenReturn(yFullNames[i]);
         refs[i] = ref;
      }

      ChartInfo info = mock(ChartInfo.class);
      when(info.getRTYFields()).thenReturn(refs);
      return info;
   }
}
