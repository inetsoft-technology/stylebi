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

import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.PointElement;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("core")
class PlotAreaCardMeasureFirstTest {
   @Test
   void cardOnNormalChartPromotesMeasure() {
      assertTrue(PlotArea.cardPutsMeasureFirst(
         chartInfo(ChartInfo.TooltipStyle.CARD, GraphTypes.CHART_BAR),
         mock(GraphElement.class)));
   }

   @Test
   void cardOnWordCloudPromotesDim() {
      // Word cloud's text dim is the rendered shape; promoting the measure
      // would push the identifying value below an auxiliary size frame.
      assertFalse(PlotArea.cardPutsMeasureFirst(
         chartInfo(ChartInfo.TooltipStyle.CARD, GraphTypes.CHART_POINT),
         wordCloudElement()));
   }

   @Test
   void cardOnGanttPromotesDim() {
      assertFalse(PlotArea.cardPutsMeasureFirst(
         chartInfo(ChartInfo.TooltipStyle.CARD, GraphTypes.CHART_GANTT),
         mock(GraphElement.class)));
   }

   @Test
   void defaultStyleNeverPromotesMeasure() {
      assertFalse(PlotArea.cardPutsMeasureFirst(
         chartInfo(ChartInfo.TooltipStyle.DEFAULT, GraphTypes.CHART_BAR),
         mock(GraphElement.class)));
   }

   @Test
   void nullChartInfoIsSafe() {
      assertFalse(PlotArea.cardPutsMeasureFirst(null, mock(GraphElement.class)));
   }

   @Test
   void nullElementIsSafe() {
      // instanceof short-circuits on null, so a null element is not flagged as
      // word cloud and the method falls through to true.
      assertTrue(PlotArea.cardPutsMeasureFirst(
         chartInfo(ChartInfo.TooltipStyle.CARD, GraphTypes.CHART_BAR), null));
   }

   @Test
   void cardOnPointElementNotWordCloudPromotesMeasure() {
      // Regression guard: a dot plot (a dimension on X, measure on Y) keeps
      // measure-first. Only word cloud and scatter (measures on both axes) get
      // the dim-first / no-headline treatment.
      ChartInfo info = chartInfo(ChartInfo.TooltipStyle.CARD, GraphTypes.CHART_POINT);
      when(info.getXFieldCount()).thenReturn(1);
      when(info.getYFieldCount()).thenReturn(1);
      when(info.getXField(0)).thenReturn(mock(ChartDimensionRef.class));
      when(info.getYField(0)).thenReturn(mock(ChartAggregateRef.class));

      PointElement point = mock(PointElement.class);
      when(point.isWordCloud()).thenReturn(false);

      assertTrue(PlotArea.cardPutsMeasureFirst(info, point));
   }

   @Test
   void cardOnScatterPromotesDim() {
      // Scatter (measures on both X and Y) → coordinates of equal weight, so no
      // single measure leads as the headline.
      assertFalse(PlotArea.cardPutsMeasureFirst(scatterInfo(), mock(GraphElement.class)));
   }

   @Test
   void scatterPlotDetectedWhenMeasuresOnBothAxes() {
      assertTrue(PlotArea.isScatterPlot(scatterInfo()));
   }

   @Test
   void scatterPlotNotDetectedWithDimensionOnAxis() {
      // A dimension on X makes it a dot plot, not a scatter.
      ChartInfo info = scatterInfo();
      when(info.getXField(0)).thenReturn(mock(ChartDimensionRef.class));
      assertFalse(PlotArea.isScatterPlot(info));
   }

   @Test
   void scatterPlotNotDetectedForNonPointChart() {
      assertFalse(PlotArea.isScatterPlot(
         chartInfo(ChartInfo.TooltipStyle.CARD, GraphTypes.CHART_BAR)));
   }

   @Test
   void scatterIdentityDimsLeadWithColorThenShape() {
      // Color identifies a scatter point most directly, so it leads as the
      // tier-1 headline; remaining aesthetic dims follow by frame priority.
      GraphElement element = mock(GraphElement.class);
      ColorFrame color = mock(ColorFrame.class);
      ShapeFrame shape = mock(ShapeFrame.class);
      when(color.getField()).thenReturn("State");
      when(shape.getField()).thenReturn("Region");
      when(element.getColorFrame()).thenReturn(color);
      when(element.getShapeFrame()).thenReturn(shape);

      DataSet dataset = mock(DataSet.class);
      when(dataset.isMeasure(anyString())).thenReturn(false);

      List<String> idDims = PlotArea.getScatterIdentityDims(
         element, dataset, new HashSet<>(Arrays.asList("State", "Region")));

      assertEquals(Arrays.asList("State", "Region"), idDims);
   }

   @Test
   void soloCardLiftsXDimToHeader() {
      // Solo measure-first card: the X-dim is lifted out of the value rows onto
      // the header so it renders as the tier-1 subtitle under the measure.
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.addTooltip(palette.put("Sales"), palette.put("100"));
      int xKey = palette.put("Year");
      tip.addTooltip(xKey, palette.put("2024"));

      PlotArea.captureCombinedCardHeader(tip, new int[]{ xKey }, ChartInfo.TooltipStyle.CARD);

      assertEquals(xKey, tip.getHeaderKey(), "X-dim lifts to the subtitle header");
      assertFalse(tip.containsTooltip(xKey), "lifted X-dim is removed from the value rows");
   }

   @Test
   void soloCardHeaderLiftSkippedForDefaultStyle() {
      // Default style is the flat tooltip, so no header lift.
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      int xKey = palette.put("Year");
      tip.addTooltip(xKey, palette.put("2024"));

      PlotArea.captureCombinedCardHeader(tip, new int[]{ xKey }, ChartInfo.TooltipStyle.DEFAULT);

      assertFalse(tip.hasHeader(), "non-card style must not lift a header");
      assertTrue(tip.containsTooltip(xKey));
   }

   private static ChartInfo chartInfo(ChartInfo.TooltipStyle style, int chartType) {
      ChartInfo info = mock(ChartInfo.class);
      when(info.getTooltipStyle()).thenReturn(style);
      when(info.getChartType()).thenReturn(chartType);
      return info;
   }

   private static ChartInfo scatterInfo() {
      ChartInfo info = chartInfo(ChartInfo.TooltipStyle.CARD, GraphTypes.CHART_POINT);
      when(info.getXFieldCount()).thenReturn(2);
      when(info.getYFieldCount()).thenReturn(2);
      when(info.getXField(anyInt())).thenReturn(mock(ChartAggregateRef.class));
      when(info.getYField(anyInt())).thenReturn(mock(ChartAggregateRef.class));
      return info;
   }

   private static PointElement wordCloudElement() {
      PointElement element = mock(PointElement.class);
      when(element.isWordCloud()).thenReturn(true);
      return element;
   }
}
