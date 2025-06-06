/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.script;

import inetsoft.report.composition.region.ChartArea;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LegendArrayTest {
   private  LegendArray legendArray;
   private ChartInfo mockChartInfo;
   private LegendsDescriptor mockLegendsDescriptor;
   private ChartRef xRef = mock(ChartRef.class);
   private ChartRef yRef = mock(ChartRef.class);

   @Test
   void testInit() {
      when(xRef.getFullName()).thenReturn("name");
      when(yRef.getFullName()).thenReturn("id1");
      when(yRef.isMeasure()).thenReturn(true);

      mockChartInfo =mock(ChartInfo.class);
      when(mockChartInfo.getXFields()).thenReturn(new ChartRef[] { xRef });
      when(mockChartInfo.getYFields()).thenReturn(new ChartRef[] { yRef });

      mockLegendsDescriptor = mock(LegendsDescriptor.class);
      legendArray = new LegendArray(mockChartInfo, mockLegendsDescriptor, ChartArea.COLOR_LEGEND);

      assertArrayEquals(new Object[] {"id1"}, legendArray.getIds());
      assertFalse(legendArray.has("name", null));
      assertTrue(legendArray.has("id1", null));
      assertEquals("[index]", legendArray.getDisplaySuffix());
      assertEquals("[]", legendArray.getSuffix());

      //check legend is not null
      Set<String> keys = new HashSet<>(2);
      keys.add("label1");
      keys.add("label2");
      LegendDescriptor mockLegendDescriptor = mock(LegendDescriptor.class);
      when(mockLegendDescriptor.getAliasedLabels()).thenReturn(keys);
      when(mockLegendsDescriptor.getColorLegendDescriptor()).thenReturn(mockLegendDescriptor);
      when(mockLegendsDescriptor.getShapeLegendDescriptor()).thenReturn(mockLegendDescriptor);

       assertInstanceOf(LegendScriptable.class, legendArray.get("id1", null));
   }
}
