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

import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.PointElement;
import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
      // Regression guard: only word-cloud PointElements get the dim-first
      // treatment, regular point charts (scatter, etc.) keep measure-first.
      PointElement point = mock(PointElement.class);
      when(point.isWordCloud()).thenReturn(false);

      assertTrue(PlotArea.cardPutsMeasureFirst(
         chartInfo(ChartInfo.TooltipStyle.CARD, GraphTypes.CHART_POINT),
         point));
   }

   private static ChartInfo chartInfo(ChartInfo.TooltipStyle style, int chartType) {
      ChartInfo info = mock(ChartInfo.class);
      when(info.getTooltipStyle()).thenReturn(style);
      when(info.getChartType()).thenReturn(chartType);
      return info;
   }

   private static PointElement wordCloudElement() {
      PointElement element = mock(PointElement.class);
      when(element.isWordCloud()).thenReturn(true);
      return element;
   }
}
