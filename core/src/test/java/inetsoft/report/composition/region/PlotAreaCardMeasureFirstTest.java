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
import inetsoft.graph.data.BoxDataSet;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.PointElement;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

   @Test
   void cardEmitsOriginalMeasureBeforeCalcDerived() {
      // Original measure leads at tier-1, derived Change follows at tier-2.
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);

      int calcKey = palette.put("Change from previous year of Year(Date): Sum(Quantity Purchased)");
      int calcVal = palette.put("24");
      int origKey = palette.put("Sum(Quantity Purchased)");
      int origVal = palette.put("2,865.00");

      PlotArea.appendMeasurePair(tip, calcKey, calcVal, origKey, origVal, true);

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">Sum(Quantity Purchased)" +
         ChartToolTip.COLON + "2,865.00"), "original measure leads at tier-1");
      assertTrue(out.contains("<div class=\"tt-tier-2\">Change from previous year"),
         "derived Change value follows at tier-2");
   }

   @Test
   void defaultOrderKeepsCalcFirst() {
      // originalFirst=false keeps the legacy calc-then-original order.
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);

      int calcKey = palette.put("Change");
      int calcVal = palette.put("24");
      int origKey = palette.put("Sum");
      int origVal = palette.put("2865");

      PlotArea.appendMeasurePair(tip, calcKey, calcVal, origKey, origVal, false);

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">Change"),
         "calc value stays first when originalFirst is false");
      assertTrue(out.contains("<div class=\"tt-tier-2\">Sum"));
   }

   @Test
   void appendMeasurePairWithoutOriginalIsNoop() {
      // No calc (no ovalue) emits only the single pair, regardless of the flag.
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);

      int key = palette.put("Sum");
      int val = palette.put("100");

      PlotArea.appendMeasurePair(tip, key, val, null, null, true);

      assertEquals(2, tip.getTooltipList().size(), "exactly one pair added");
      assertTrue(tip.getTooltip(palette).contains("<div class=\"tt-tier-1\">Sum"));
   }

   @Test
   void derivedMeasureGroupsAtTierTwoAboveDimensions() {
      // Derived measure present: original tier-1, Change tier-2, dims tier-3.
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);

      // measure pairs: original (headline) then derived
      tip.addTooltip(palette.put("Sum(Quantity Purchased)"), palette.put("2,934.00"));
      tip.addTooltip(palette.put("Change from previous year of Year(Date): Sum(Quantity Purchased)"),
         palette.put("268"));
      // dimension pairs
      tip.addTooltip(palette.put("QuarterOfYear(Date)"), palette.put("2nd"));
      tip.addTooltip(palette.put("Year(Date)"), palette.put("2019"));

      // 2 measure pairs → group the 1 non-headline measure at tier-2.
      tip.setGroupedTiers(true);
      tip.setTier2GroupSize(1);

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">Sum(Quantity Purchased)" +
         ChartToolTip.COLON + "2,934.00"), "original measure leads at tier-1");
      assertTrue(out.contains("<div class=\"tt-tier-2\">Change from previous year"),
         "derived Change value at tier-2");
      assertTrue(out.contains("<div class=\"tt-tier-3\">QuarterOfYear(Date)" +
         ChartToolTip.COLON + "2nd"), "dimension drops to tier-3");
      assertTrue(out.contains("<div class=\"tt-tier-3\">Year(Date)" +
         ChartToolTip.COLON + "2019"), "dimension drops to tier-3");
      assertFalse(out.contains("tt-subtitle"), "no X-dim subtitle lift when a derived measure leads");
   }

   @Test
   void groupMeasuresAtTier2OnlyWhenDerivedMeasureAndMultiplePairs() {
      String[] measures = { "Sum(Quantity Purchased)",
         "Change from previous year of Year(Date): Sum(Quantity Purchased)" };
      Set<String> derived = new HashSet<>(Collections.singletonList(
         "Change from previous year of Year(Date): Sum(Quantity Purchased)"));

      // Derived measure present + 2 pairs → group the derived measure at tier-2.
      assertTrue(PlotArea.groupMeasuresAtTier2(measures, derived, 2));

      // A single measure pair has no non-headline measure to rank → no grouping.
      assertFalse(PlotArea.groupMeasuresAtTier2(measures, derived, 1));

      // No derived measure (plain multi-measure card) → keep the X-dim subtitle lift.
      assertFalse(PlotArea.groupMeasuresAtTier2(
         new String[]{ "Sum(A)", "Avg(B)" }, new HashSet<>(), 2));

      // Single plain measure: guard fires on measurePairs <= 1 → no grouping.
      assertFalse(PlotArea.groupMeasuresAtTier2(
         new String[]{ "Sum(A)" }, new HashSet<>(), 1));
   }

   @Test
   void reorderBoxMeasuresMedianFirstPutsMedianFirst() {
      // egeom.getVars() yields the stats Max-first; reorder to Median, Q1, Q3,
      // Min, Max so Median headlines, the IQR groups, and the whiskers trail.
      String[] in = { BoxDataSet.MAX_PREFIX + "Total", BoxDataSet.Q75_PREFIX + "Total",
         BoxDataSet.MEDIUM_PREFIX + "Total", BoxDataSet.Q25_PREFIX + "Total",
         BoxDataSet.MIN_PREFIX + "Total" };

      assertArrayEquals(new String[] {
         BoxDataSet.MEDIUM_PREFIX + "Total", BoxDataSet.Q25_PREFIX + "Total",
         BoxDataSet.Q75_PREFIX + "Total", BoxDataSet.MIN_PREFIX + "Total",
         BoxDataSet.MAX_PREFIX + "Total" },
         PlotArea.reorderBoxMeasuresMedianFirst(in));
   }

   @Test
   void reorderBoxMeasuresHandlesMissingStatsAndNulls() {
      // A partial stat set (no Min) and a null entry: survivors stay in priority
      // order, the null is dropped, no NPE.
      String[] in = { BoxDataSet.MAX_PREFIX + "T", null, BoxDataSet.MEDIUM_PREFIX + "T",
         BoxDataSet.Q25_PREFIX + "T" };

      assertArrayEquals(new String[] {
         BoxDataSet.MEDIUM_PREFIX + "T", BoxDataSet.Q25_PREFIX + "T", BoxDataSet.MAX_PREFIX + "T" },
         PlotArea.reorderBoxMeasuresMedianFirst(in));
   }

   @Test
   void reorderBoxMeasuresPassesThroughNonBoxNames() {
      // Unrecognized names keep their order after the ranked stats.
      String[] in = { BoxDataSet.MAX_PREFIX + "T", "Foo", BoxDataSet.MEDIUM_PREFIX + "T" };

      assertArrayEquals(new String[] {
         BoxDataSet.MEDIUM_PREFIX + "T", BoxDataSet.MAX_PREFIX + "T", "Foo" },
         PlotArea.reorderBoxMeasuresMedianFirst(in));
   }

   @Test
   void boxBaseMeasureCountCountsDistinctBases() {
      // One measure's five stats → one base; the dedicated layout only applies here.
      assertEquals(1, PlotArea.boxBaseMeasureCount(new String[] {
         BoxDataSet.MAX_PREFIX + "Total", BoxDataSet.Q75_PREFIX + "Total",
         BoxDataSet.MEDIUM_PREFIX + "Total", BoxDataSet.Q25_PREFIX + "Total",
         BoxDataSet.MIN_PREFIX + "Total" }));

      // Two measures → two bases → multi-measure boxplot falls through.
      assertEquals(2, PlotArea.boxBaseMeasureCount(new String[] {
         BoxDataSet.MEDIUM_PREFIX + "Total", BoxDataSet.MEDIUM_PREFIX + "Paid" }));

      // A null entry is skipped, not counted.
      assertEquals(1, PlotArea.boxBaseMeasureCount(new String[] {
         BoxDataSet.MEDIUM_PREFIX + "Total", null }));
   }

   @Test
   void boxIqrCountCountsQ1Q3FromAdded() {
      // Counts only the rendered IQR rows; Median/Min/Max and dims don't count.
      Set<String> added = new HashSet<>(Arrays.asList(
         BoxDataSet.MEDIUM_PREFIX + "Total", BoxDataSet.Q25_PREFIX + "Total",
         BoxDataSet.Q75_PREFIX + "Total", BoxDataSet.MIN_PREFIX + "Total",
         BoxDataSet.MAX_PREFIX + "Total", "Category"));
      assertEquals(2, PlotArea.boxIqrCount(added));

      assertEquals(0, PlotArea.boxIqrCount(new HashSet<>(Arrays.asList(
         BoxDataSet.MEDIUM_PREFIX + "Total", "Category"))));
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
