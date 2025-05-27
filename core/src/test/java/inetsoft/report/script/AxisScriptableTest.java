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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.UniqueTag;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AxisScriptableTest {
   private AxisScriptable axisScriptable;
   private ChartInfo mockChartInfo;
   private ChartRef chartRef;
   private VSChartRef mockVSChartRef;
   private  AxisDescriptor mockAxisDescriptor;

   @BeforeEach
   void setUp() {
      mockChartInfo = mock(ChartInfo.class);
      mockAxisDescriptor = mock(AxisDescriptor.class);
      mockVSChartRef = mock(VSChartRef.class);
   }

   /**
    * check with RadarChartInfo and field is Parallel_Label
    */
   @Test
   void testInitWithRadarChartInfo() {
      chartRef = mock(ChartRef.class);
      when(mockChartInfo.getFieldByName("state", true)).thenReturn(chartRef);
      axisScriptable = new AxisScriptable(mockChartInfo, "state");

      assertNull(axisScriptable.getObject());

      //check is radarParallel
      when(chartRef.getName()).thenReturn("Parallel_Label");
      RadarChartInfo mockRadarChartInfo = mock(RadarChartInfo.class);
      when(mockRadarChartInfo.getRTXFields()).thenReturn(new ChartRef[] {chartRef});
      when(mockRadarChartInfo.getFieldByName("Parallel_Label", true)).thenReturn(chartRef);
      when(mockRadarChartInfo.getLabelAxisDescriptor()).thenReturn(mockAxisDescriptor);
      when(mockRadarChartInfo.getAxisDescriptor()).thenReturn(mock(AxisDescriptor.class));

      axisScriptable = new AxisScriptable(mockRadarChartInfo, "Parallel_Label");
      assertEquals(mockAxisDescriptor,  axisScriptable.getObject());
   }

   /**
    * cjeck initWith ChartDimensionRef
    */
   @Test
   void testInitWithChartDimensionRef() {
      when(mockChartInfo.isSeparatedGraph()).thenReturn(true);
      when(mockChartInfo.getFieldByName("state", true)).thenReturn(mockVSChartRef);

      //check  VSChartRef and axis is null
      axisScriptable = new AxisScriptable(mockChartInfo, "state");
      assertNull(axisScriptable.getObject());

      //check ChartDimensionRef
      when(mockChartInfo.isSeparatedGraph()).thenReturn(false);
      ChartDimensionRef mockChartDimensionRef = mock(ChartDimensionRef.class);
      when(mockChartDimensionRef.getAxisDescriptor()).thenReturn(mockAxisDescriptor);
      when(mockChartInfo.getFieldByName("state", true)).thenReturn(mockChartDimensionRef);

      axisScriptable = new AxisScriptable(mockChartInfo, "state");
      assertEquals(mockAxisDescriptor,  axisScriptable.getObject());
   }

   /**
    * check VSChartAggregateRef and isSecondaryY is true
    */
   @Test
   void testInitWithVSChartAggregateRef() {
      VSChartAggregateRef mockVSChartAggregateRef = mock(VSChartAggregateRef.class);
      when(mockVSChartAggregateRef.isSecondaryY()).thenReturn(true);

      VSChartInfo mockVSChartInfo = mock(VSChartInfo.class);
      when(mockVSChartInfo.getFieldByName("state", true)).thenReturn(mockVSChartAggregateRef);
      when(mockVSChartInfo.getRTAxisDescriptor2()).thenReturn(mockAxisDescriptor);
      axisScriptable = new AxisScriptable(mockVSChartInfo, "state");

      assertEquals(mockAxisDescriptor,  axisScriptable.getObject());

      //check addDimensionProperties
      assertTrue(axisScriptable.has("noNull", null));
      assertTrue(axisScriptable.has("truncate", null));

      //check else and isMeasure is false
      when(mockVSChartAggregateRef.isSecondaryY()).thenReturn(false);
      when(mockVSChartAggregateRef.isMeasure()).thenReturn(true);
      when(mockVSChartInfo.getRTAxisDescriptor()).thenReturn(mockAxisDescriptor);
      axisScriptable = new AxisScriptable(mockVSChartInfo, "state");

      assertEquals(mockAxisDescriptor,  axisScriptable.getObject());
      assertTrue(axisScriptable.has("minimum", null));
   }

   /**
    * test setLabelAlias,setFieldAxis,put and get
    */
   @Test
   void testOtherFunctions() {
      when(mockChartInfo.isSeparatedGraph()).thenReturn(true);
      when(mockChartInfo.getFieldByName("state", true)).thenReturn(mockVSChartRef);

      axisScriptable = new AxisScriptable(mockChartInfo, "state");
      axisScriptable.setLabelAlias("label", "alias");
      axisScriptable.setFieldAxis(true);

      //labelColor,font,format,rotation
      axisScriptable.put("font", null, mock(PropertyDescriptor.class));
      assertEquals(UniqueTag.NOT_FOUND, axisScriptable.get("font", null));
   }
}
