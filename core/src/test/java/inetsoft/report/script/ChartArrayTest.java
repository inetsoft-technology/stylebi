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

import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.ScriptRuntime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChartArrayTest {
   private ChartArray chartArray;
   private VSChartInfo mockVSChartInfo;
   private ChartRef chartRef = mock(ChartRef.class);

   /**
    * test init with Axis
    */
   @Test
   void testInitWithAxis() {
      //check not CandleChartInfo  and StockChartInfo
      mockVSChartInfo = mock(VSChartInfo.class);
      when(chartRef.getFullName()).thenReturn("test0");
      when(mockVSChartInfo.getXFields()).thenReturn(new ChartRef[] { chartRef });
      when(mockVSChartInfo.getYFields()).thenReturn(new ChartRef[] { chartRef });

      chartArray = implementedChartArray("Axis", mockVSChartInfo);
      chartArray.init();

      assertArrayEquals(new Object[] {"test0", "test0"}, chartArray.getIds());

      //check CandleChartInfo  and StockChartInfo
      CandleChartInfo mockCandleChartInfo = mock(CandleChartInfo.class);
      when(chartRef.getFullName()).thenReturn("test1");
      when(mockCandleChartInfo.getXFields()).thenReturn(new ChartRef[] { chartRef });
      when(mockCandleChartInfo.getYFields()).thenReturn(new ChartRef[] { chartRef });
      when(mockCandleChartInfo.getBindingRefs(true)).thenReturn(new ChartRef[] { chartRef });

      chartArray = implementedChartArray("Axis", mockCandleChartInfo);
      chartArray.init();

      assertArrayEquals(new Object[] {"test1"}, chartArray.getIds());
   }

   /**
    * test init with Frame
    */
   @Test
   void testInitWithFrame() {
      RadarChartInfo mockRadarChartInfo = mock(RadarChartInfo.class);
      when(chartRef.getFullName()).thenReturn("testRadar");
      when(mockRadarChartInfo.getModelRefs(false)).thenReturn(new ChartRef[] { chartRef });

      chartArray = implementedChartArray("Frame", mockRadarChartInfo);
      chartArray.init();

      assertArrayEquals(new Object[] { "testRadar", "Parallel_Label"}, chartArray.getIds());

      assertTrue((boolean)chartArray.getDefaultValue(ScriptRuntime.BooleanClass));
      assertEquals(ScriptRuntime.NaNobj, chartArray.getDefaultValue(ScriptRuntime.NumberClass));
      assertEquals("[field]", chartArray.getDisplaySuffix());
   }

   @Test
   void testSetGet() {
      MergedChartInfo mockVSChartInfo = mock(MergedChartInfo.class);
      chartArray = implementedChartArray("colorFrame", mockVSChartInfo);
      assertNull(chartArray.getIds());

      chartArray.put("title", null, "Test Chart Title");
      assertTrue(chartArray.has("title", null));
   }

   private ChartArray implementedChartArray(String property, ChartInfo chartInfo) {
      chartArray = new ChartArray(property, Object.class) {
         @Override
         public ChartInfo getInfo() {
            return chartInfo;
         }
      };

      return chartArray;
   }
}
