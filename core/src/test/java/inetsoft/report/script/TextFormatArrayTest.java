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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TextFormatArrayTest {
   private TextFormatArray textFormatArray;
   private PlotDescriptor mockPlotDescriptor;

   private VSChartDimensionRef mockChartDimRef;

   private VSChartAggregateRef mockChartAggRef;

   private ChartInfo mockChartInfo;

   @BeforeEach
   void setUp() {
      mockChartDimRef = mock(VSChartDimensionRef.class);
      when(mockChartDimRef.getFullName()).thenReturn("state");
      mockChartAggRef = mock(VSChartAggregateRef.class);
      when(mockChartAggRef.getFullName()).thenReturn("sum(id)");
      when(mockChartAggRef.isMeasure()).thenReturn(true);

      mockPlotDescriptor = mock(PlotDescriptor.class);
      mockChartInfo = mock(ChartInfo.class);
   }

   /**
    * test init function and other get functons
    */
   @Test
   void testInit() {
      when(mockChartInfo.getXFields()).thenReturn(new ChartRef[] { mockChartDimRef });
      when(mockChartInfo.getYFields()).thenReturn(new ChartRef[] { mockChartAggRef });

      textFormatArray = new TextFormatArray(mockChartInfo, mockPlotDescriptor);

      assertArrayEquals(new Object[] { "sum(id)" }, textFormatArray.getIds());
      assertEquals("[index]", textFormatArray.getDisplaySuffix());
      assertEquals("[]", textFormatArray.getSuffix());

      assertEquals(TextFormatScriptable.class, textFormatArray.getType());
      assertTrue(textFormatArray.has("sum(id)", null));
      assertFalse(textFormatArray.has("sum(id1)", null));
   }

   /**
    * test get function with non-aggregate ref and fmt is not null
    */
   @Test
   void testGetWithNotAggregateRef() {
      when(mockChartInfo.getRTFieldByFullName("state")).thenReturn(mockChartDimRef);
      when(mockChartInfo.isMultiAesthetic()).thenReturn(false);

      ChartRef mockChartRef = mock(ChartRef.class);
      when(mockChartRef.getTextFormat()).thenReturn(new CompositeTextFormat());

      AestheticRef mockAestheticRef = mock(AestheticRef.class);
      when(mockAestheticRef.getDataRef()).thenReturn(mockChartRef);
      when(mockChartInfo.getTextField()).thenReturn(mockAestheticRef);

      textFormatArray = new TextFormatArray(mockChartInfo, mockPlotDescriptor);
      assertInstanceOf(TextFormatScriptable.class, textFormatArray.get("state", null));
   }

   /**
    * test get with aggregate ref and fmt is not null
    */
   @Test
   void testGetWithAggregateRef() {
      when(mockChartInfo.getRTFieldByFullName("sum(id)")).thenReturn(mockChartAggRef);
      when(mockChartInfo.isMultiAesthetic()).thenReturn(true);

      when(mockChartAggRef.getTextFormat()).thenReturn(new CompositeTextFormat());

      textFormatArray = new TextFormatArray(mockChartInfo, mockPlotDescriptor);
      assertInstanceOf(TextFormatScriptable.class, textFormatArray.get("sum(id)", null));
   }
}
