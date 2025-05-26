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

import inetsoft.graph.*;
import inetsoft.graph.coord.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.scale.Scale;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.HLColorFrame;
import inetsoft.report.filter.Highlight;
import inetsoft.report.script.viewsheet.ChartVSAScriptable;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChartHighlightedArrayTest {
   private ChartHighlightedArray chartHighlightedArray;
   private ChartVSAScriptable mockChartVSAScriptable;

   private ViewsheetSandbox mockViewsheetSandbox;

   private EGraph eGraph;

   private HLColorFrame hlColorFrame;

   /**
    * check has with ColorFrame and no scale
    */
   @Test
   void testHasGetWithColorFrame() {
      eGraph = new EGraph();
      IntervalElement intervalElement = new IntervalElement();
      mockHighlightColorFrame("highlight1");
      intervalElement.setColorFrame(hlColorFrame);
      eGraph.addElement(intervalElement);

      mockCommonChartScriptable(eGraph);

      chartHighlightedArray = new ChartHighlightedArray(mockChartVSAScriptable);
      assertTrue(chartHighlightedArray.has("highlight1", null));
      //hcolor no highlight
      assertFalse((boolean)chartHighlightedArray.get("highlight1", null));

      assertEquals("[highlightname]", chartHighlightedArray.getDisplaySuffix());
      assertEquals("[]", chartHighlightedArray.getSuffix());
      assertArrayEquals(new Object[] {"highlight1"}, chartHighlightedArray.getIds());
   }

   /**
    * check has and get with Scale and ColorFrame highlight is true
    */
   @Test
   void testHasGetWithScale() {
      //mock AxisSpec
      mockHighlightColorFrame("hightlight2");

      AxisSpec mockAxisSpec = mock(AxisSpec.class);
      when(mockAxisSpec.getColorFrame()).thenReturn(hlColorFrame);

      // mock scale
      Scale mockScale = mock(Scale.class);
      when(mockScale.getAxisSpec()).thenReturn(mockAxisSpec);

      //mock AbstractParallelCoord
      AbstractParallelCoord mockCoordinate = mock(AbstractParallelCoord.class);
      when(mockCoordinate.getScales()).thenReturn(new Scale[]{ mockScale });
      when(mockCoordinate.getAxisLabelScale()).thenReturn(mockScale);

      EGraph mockEGraph = mock(EGraph.class);
      when(mockEGraph.getCoordinate()).thenReturn(mockCoordinate);

      mockCommonChartScriptable(mockEGraph);

      chartHighlightedArray = new ChartHighlightedArray(mockChartVSAScriptable);

      assertTrue(chartHighlightedArray.has("hightlight2", null));
      assertFalse((boolean)chartHighlightedArray.get("hightlight2", null));
   }

   private void mockCommonChartScriptable(EGraph eGraph) {
      mockViewsheetSandbox = mock(ViewsheetSandbox.class);
      when(mockViewsheetSandbox.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      mockChartVSAScriptable = mock(ChartVSAScriptable.class);
      when(mockChartVSAScriptable.getDataSet()).thenReturn(mock(DataSet.class));
      when(mockChartVSAScriptable.getEGraph()).thenReturn(eGraph);
      when(mockChartVSAScriptable.getViewsheetSandbox()).thenReturn(mockViewsheetSandbox);
   }

   private void mockHighlightColorFrame(String highligtName) {
      Highlight mockHighlight = mock(Highlight.class);
      when(mockHighlight.getName()).thenReturn(highligtName);

      hlColorFrame = mock(HLColorFrame.class);
      when(hlColorFrame.getHighlights()).thenReturn(new Highlight[]{ mockHighlight });
   }
}
