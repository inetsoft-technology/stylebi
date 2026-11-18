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

import inetsoft.graph.AxisSpec;
import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.AttributeDataSet;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.data.HRef;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.CompactDates;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.TimeScale;
import inetsoft.graph.scale.Scale;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XConstants;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.GradientColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.util.ExtendedDateFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The axis label rotation and length limit GraphGenerator picks for a marked chart.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernAxisLabelRuleTest {
   private static final double CLASSIC_FACET_300_WIDTH = 300;

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void eightShortNamesStayFlat() {
      Scale x = xScale(barChart(names("", 8, false)), VizMark.MODERN_LIGHT);

      assertEquals(0, rotation(x));
      assertEquals(66, x.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void thirtyStoresTurnTo45() {
      Scale x = xScale(barChart(names("Store ", 30, true)), VizMark.MODERN_LIGHT);

      assertEquals(45, rotation(x));
      assertEquals(66, x.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void classicThirtyStoresStillTurn90() {
      Scale x = xScale(barChart(names("Store ", 30, true)), null);

      assertEquals(90, rotation(x));
      assertEquals(0, x.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void dailyDatesStayFlat() {
      VSChartDimensionRef day = dim("Day");
      day.setDataType(XSchema.DATE);
      day.setDateLevelValue(String.valueOf(XConstants.DAY_DATE_GROUP));
      long start = 1_700_000_000_000L;
      Object[][] rows = new Object[106][];
      rows[0] = new Object[2];

      for(int i = 1; i <= 105; i++) {
         rows[i] = new Object[] { new java.sql.Date(start + i * 86_400_000L), (double) i };
      }

      Scale x = xScale(barChart(day, rows), VizMark.MODERN_LIGHT);

      assertEquals(0, rotation(x));
   }

   @Test
   void aHorizontalBarsCategoriesGetTheColumnLimit() {
      VSChartDimensionRef store = dim("Store");
      VSChartAggregateRef total = agg("Total");
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      cinfo.addXField(total);
      cinfo.addYField(store);
      cinfo.updateChartType(false);
      Object[][] rows = new Object[7][];
      rows[0] = new Object[] { GraphUtil.getName(store), GraphUtil.getName(total) };

      for(int i = 1; i < 7; i++) {
         rows[i] = new Object[] { "Store " + i, i * 10.0 };
      }

      EGraph graph = generator(new Chart(cinfo, rows), VizMark.MODERN_LIGHT).createEGraph();
      Scale y = scaleOf(graph, "Store");

      assertEquals(0, rotation(y));
      assertEquals(110, y.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void facetInnerLabelsFollowTheRule() {
      EGraph graph = generator(facetChart(), VizMark.MODERN_LIGHT).createEGraph();
      Scale inner = scaleOf(graph, "State");

      assertNotEquals(90, rotation(inner));
      assertEquals(66, inner.getAxisSpec().getLabelLimit(), 0.01);
      Scale outer = scaleOf(graph, "Region");
      assertNotNull(outer, "premise: a facet outer axis");
      assertEquals(0, rotation(outer));
      assertEquals(0, outer.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void classicFacetInnerStillTurns90() {
      EGraph graph = generator(facetChart(), null).createEGraph();

      assertEquals(90, rotation(scaleOf(graph, "State")));
   }

   @Test
   void cjkLabelsTurn45Too() {
      String[] cjk = new String[30];

      for(int i = 0; i < cjk.length; i++) {
         cjk[i] = "商店名称" + (i + 1);
      }

      assertEquals(45, rotation(xScale(barChart(cjk), VizMark.MODERN_LIGHT)));
   }

   @Test
   void anAuthorsRotationKeepsTodaysLabels() {
      Chart chart = barChart(names("Store ", 30, true));
      chart.info().getXField(0).getAxisDescriptor().getAxisLabelTextFormat().setRotation(30);
      Scale x = xScale(chart, VizMark.MODERN_LIGHT);

      assertEquals(30, rotation(x));
      assertEquals(0, x.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void truncateOffKeepsWrapping() {
      Chart chart = barChart(names("Store ", 30, true));
      chart.info().getXField(0).getAxisDescriptor().setTruncate(false);
      Scale x = xScale(chart, VizMark.MODERN_LIGHT);

      assertEquals(0, x.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void aDateComparisonChartKeepsTodaysXLabels() {
      Chart chart = barChart(names("Store ", 30, true));
      VSChartInfo spy = Mockito.spy(chart.info());
      Mockito.doReturn(true).when(spy).isAppliedDateComparison();
      GraphGenerator gen = generator(new Chart(spy, chart.rows()), VizMark.MODERN_LIGHT);

      assertFalse(gen.isModernLabelAxis(new AxisSpec(), true));
      assertTrue(gen.isModernLabelAxis(new AxisSpec(), false));
   }

   @Test
   void aMonthlyBarGetsCompactDates() {
      Scale x = xScale(dateChart(XConstants.MONTH_DATE_GROUP, XConstants.SORT_ASC),
                       VizMark.MODERN_LIGHT);

      assertEquals(new CompactDates(TimeScale.MONTH, CompactDateRule.locale()),
                   x.getAxisSpec().getCompactDates());
   }

   @Test
   void classicDatesGetNone() {
      Scale x = xScale(dateChart(XConstants.MONTH_DATE_GROUP, XConstants.SORT_ASC), null);

      assertNull(x.getAxisSpec().getCompactDates());
   }

   @Test
   void anAuthorsDateFormatKeepsTodaysLabels() {
      Chart chart = dateChart(XConstants.MONTH_DATE_GROUP, XConstants.SORT_ASC);
      XFormatInfo fmt = new XFormatInfo(TableFormat.DATE_FORMAT, "yyyy/MM");
      chart.info().getXField(0).getAxisDescriptor().getAxisLabelTextFormat().setFormat(fmt);
      Scale x = xScale(chart, VizMark.MODERN_LIGHT);

      assertNull(x.getAxisSpec().getCompactDates());
      assertEquals("yyyy/MM", ((SimpleDateFormat) x.getAxisSpec().getTextSpec().getFormat())
         .toPattern());
   }

   @Test
   void aDateAxisOutOfOrderShowsTheYearOnEveryLabel() {
      Scale x = xScale(dateChart(XConstants.MONTH_DATE_GROUP, XConstants.SORT_VALUE_DESC),
                       VizMark.MODERN_LIGHT);

      assertNull(x.getAxisSpec().getCompactDates());
      assertEquals("MMM yyyy", ((SimpleDateFormat) x.getAxisSpec().getTextSpec().getFormat())
         .toPattern());
   }

   @Test
   void hourlyDatesDropARepeatedDate() {
      Scale x = xScale(dateChart(XConstants.HOUR_DATE_GROUP, XConstants.SORT_ASC),
                       VizMark.MODERN_LIGHT);

      assertTrue(x.getAxisSpec().isAbbreviate());
      assertNull(x.getAxisSpec().getCompactDates());
   }

   @Test
   void aTimeSeriesAxisTakesItsTickUnit() {
      Chart chart = dateChart(XConstants.MONTH_DATE_GROUP, XConstants.SORT_ASC);
      ((VSChartDimensionRef) chart.info().getXField(0)).setTimeSeries(true);
      Scale x = xScale(chart, VizMark.MODERN_LIGHT);

      assertTrue(x instanceof TimeScale, "premise: a time scale");
      assertEquals(new CompactDates(CompactDates.FROM_SCALE, CompactDateRule.locale()),
                   x.getAxisSpec().getCompactDates());
   }

   @Test
   void aDrilledLevelDrawsItsOwnPattern() {
      Chart chart = dateChart(XConstants.MONTH_DATE_GROUP, XConstants.SORT_ASC);
      VSChartDimensionRef design = (VSChartDimensionRef) chart.info().getXField(0);
      VSChartDimensionRef drilled = (VSChartDimensionRef) design.clone();
      drilled.setDateLevelValue(String.valueOf(XConstants.DAY_DATE_GROUP));
      chart.info().setRTXFields(new ChartRef[] { drilled });
      chart.rows()[0][0] = GraphUtil.getName(drilled);
      Scale x = xScale(chart, VizMark.MODERN_LIGHT);

      assertEquals(new CompactDates(TimeScale.DAY, CompactDateRule.locale()),
                   x.getAxisSpec().getCompactDates());
   }

   @Test
   void facetDatesAreCompactToo() {
      VSChartDimensionRef year = dateDim("Year", XConstants.YEAR_DATE_GROUP, XConstants.SORT_ASC);
      VSChartDimensionRef month = dateDim("Month", XConstants.MONTH_DATE_GROUP,
                                          XConstants.SORT_ASC);
      VSChartAggregateRef total = agg("Total");
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      cinfo.addXField(year);
      cinfo.addXField(month);
      cinfo.addYField(total);
      cinfo.updateChartType(false);
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { GraphUtil.getName(year), GraphUtil.getName(month),
                              GraphUtil.getName(total) });

      for(int y = 0; y < 2; y++) {
         for(int m = 0; m < 4; m++) {
            rows.add(new Object[] { new java.sql.Date(100 + y, 0, 1),
                                    new java.sql.Date(100 + y, m, 1), 10.0 * (m + 1) });
         }
      }

      EGraph graph = generator(new Chart(cinfo, rows.toArray(new Object[0][])),
                               VizMark.MODERN_LIGHT).createEGraph();
      Scale inner = scaleOf(graph, "Month");
      Scale outer = scaleOf(graph, "Year");

      assertNotNull(outer, "premise: a facet outer axis");
      assertEquals(new CompactDates(TimeScale.MONTH, CompactDateRule.locale()),
                   inner.getAxisSpec().getCompactDates());
      assertNull(outer.getAxisSpec().getCompactDates());
   }

   @Test
   void aDateComparisonChartGetsNoCompactDatesOnAnyAxis() {
      Chart chart = monthByMonthChart();
      VSChartInfo spy = Mockito.spy(chart.info());
      Mockito.doReturn(true).when(spy).isAppliedDateComparison();
      GraphGenerator plain = generator(chart, VizMark.MODERN_LIGHT);
      GraphGenerator dc = generator(new Chart(spy, chart.rows()), VizMark.MODERN_LIGHT);
      String x = GraphUtil.getName(chart.info().getXField(0));
      String y = GraphUtil.getName(chart.info().getYField(0));

      assertNotNull(compactDates(plain, new CategoricalScale(x)), "premise: a plain X axis");
      assertNotNull(compactDates(plain, new CategoricalScale(y)), "premise: a plain Y axis");
      assertNotNull(compactDates(plain, new TimeScale(y)), "premise: a plain time axis");
      assertNull(compactDates(dc, new CategoricalScale(x)));
      assertNull(compactDates(dc, new CategoricalScale(y)));
      assertNull(compactDates(dc, new TimeScale(y)));
      assertTrue(dc.isDateComparisonAxis(true), "the label rule still skips the X axis only");
      assertFalse(dc.isDateComparisonAxis(false));
   }

   @Test
   void aMarkedLineKeepsItsFittedYRange() {
      double[] classic = yRange(lineChart(), null);
      double[] marked = yRange(lineChart(), VizMark.MODERN_LIGHT);

      assertTrue(classic[0] > 0, "premise: a fitted range " + Arrays.toString(classic));
      assertArrayEquals(classic, marked, 1e-9, "marked " + Arrays.toString(marked));
   }

   @Test
   void aMarkedCandleKeepsItsFittedYRange() {
      double[] classic = yRange(candleChart(), null);
      double[] marked = yRange(candleChart(), VizMark.MODERN_LIGHT);

      assertTrue(classic[0] > 0, "premise: a fitted range " + Arrays.toString(classic));
      assertArrayEquals(classic, marked, 1e-9, "marked " + Arrays.toString(marked));
   }

   @Test
   void anHourlyGroupedChartDropsTheRepeatedDate() {
      for(VizMark mark : new VizMark[] { null, VizMark.MODERN_LIGHT }) {
         GraphGenerator gen = generator(hourlyChart(), mark, new Dimension(800, 240),
                                        rows -> new HourFormatDataSet(rows));
         EGraph graph = gen.createEGraph();
         VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(gen.getData(), 0, 0, 800, 240);
         DefaultAxis axis = ((RectCoord) vgraph.getCoordinate()).getXAxis1();
         axis.prepareLabels();
         List<String> texts = shownFullTexts(axis);

         assertEquals("2026-01-05 09", texts.get(0), mark + " " + texts);

         if(mark != null) {
            assertEquals("01-05 10", texts.get(1), texts.toString());
         }
         else {
            assertEquals("2026-01-05 10", texts.get(1), "premise: classic repeats the date");
         }
      }
   }

   @Test
   void ganttTasksStayFlatInAColumn() {
      EGraph graph = generator(ganttChart(30, false), VizMark.MODERN_LIGHT, new Dimension(400, 480))
         .createEGraph();
      Scale tasks = ((RectCoord) graph.getCoordinate()).getXScale();

      assertInstanceOf(CategoricalScale.class, tasks, "premise: the task scale");
      assertEquals(0, rotation(tasks));
      assertEquals(110, tasks.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void classicGanttTasksUnchanged() {
      EGraph graph = generator(ganttChart(30, false), null, new Dimension(400, 480)).createEGraph();
      Scale tasks = ((RectCoord) graph.getCoordinate()).getXScale();

      assertEquals(0, rotation(tasks));
      assertEquals(0, tasks.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void aGanttTimeAxisTakesCompactDates() {
      GraphGenerator gen = generator(ganttChart(6, true), VizMark.MODERN_LIGHT,
                                     new Dimension(400, 480));
      EGraph graph = gen.createEGraph();
      Scale time = ((RectCoord) graph.getCoordinate()).getYScale();

      assertInstanceOf(TimeScale.class, time, "premise: the time scale");
      assertEquals(new CompactDates(CompactDates.FROM_SCALE, CompactDateRule.locale()),
                   time.getAxisSpec().getCompactDates());

      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(gen.getData(), 0, 0, 400, 480);
      DefaultAxis axis = ((RectCoord) vgraph.getCoordinate()).getYAxis1();
      axis.prepareLabels();
      List<String> texts = shownTexts(axis);

      assertTrue(texts.size() >= 3, texts.toString());
      assertEquals(List.of("Jan 2026", "Feb", "Mar"), texts.subList(0, 3));
   }

   @Test
   void classicGanttTimeAxisGetsNoCompactDates() {
      EGraph graph = generator(ganttChart(6, true), null, new Dimension(400, 480)).createEGraph();

      assertNull(((RectCoord) graph.getCoordinate()).getYScale().getAxisSpec().getCompactDates());
   }

   @Test
   void aHeatMapsYCategoriesGetTheColumnLimit() {
      EGraph graph = generator(heatMap(), VizMark.MODERN_LIGHT).createEGraph();
      Scale y = scaleOf(graph, "State");

      assertTrue(GraphTypeUtil.isHeatMap(heatMap().info()), "premise: a heat map");
      assertInstanceOf(CategoricalScale.class, y, "premise: a category Y axis");
      assertEquals(0, rotation(y));
      assertEquals(110, y.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void aFacetRowKeepsTodaysLabels() {
      VSChartDimensionRef store = dim("Store");
      VSChartDimensionRef region = dim("Region");
      VSChartAggregateRef total = agg("Total");
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      cinfo.addXField(store);
      cinfo.addYField(region);
      cinfo.addYField(total);
      cinfo.updateChartType(false);
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { GraphUtil.getName(store), GraphUtil.getName(region),
                              GraphUtil.getName(total) });

      for(int r = 0; r < 2; r++) {
         for(int s = 0; s < 4; s++) {
            rows.add(new Object[] { "Store " + s, "Region number " + r, 10.0 * (s + 1) });
         }
      }

      EGraph graph = generator(new Chart(cinfo, rows.toArray(new Object[0][])),
                               VizMark.MODERN_LIGHT).createEGraph();
      Scale row = scaleOf(graph, "Region");

      assertNotNull(row, "premise: a facet row axis");
      assertEquals(0, row.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void classicHeatMapsYCategoriesUnchanged() {
      EGraph graph = generator(heatMap(), null).createEGraph();
      Scale y = scaleOf(graph, "State");

      assertEquals(0, rotation(y));
      assertEquals(0, y.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void aMarkedLineWithManyCategoriesStaysInItsCard() {
      Chart chart = lineChart(campaigns(105));
      VGraph vgraph = plot(chart, VizMark.MODERN_LIGHT, 400, 240);
      DefaultAxis axis = ((RectCoord) vgraph.getCoordinate()).getXAxis1();
      axis.prepareLabels();
      List<Integer> idx = shownIndices(axis);

      assertEquals(400, expandedWidth(vgraph, chart), 0.5);
      // only the line's own minimum of 5px a category
      assertEquals(105 * 5, vgraph.getMinPlotWidth(), 0.5);
      assertEquals(45, (int) axis.getScale().getAxisSpec().getTextSpec().getRotation());
      assertTrue(idx.size() > 2 && idx.size() < 105, "thinned " + idx);
      // a crowded first label is hidden, the others stay on the step
      assertEquals(0, idx.get(0) % (idx.get(1) - idx.get(0)), "on the step " + idx);

      for(int i = 1; i < idx.size(); i++) {
         assertEquals(idx.get(1) - idx.get(0), idx.get(i) - idx.get(i - 1), "one step " + idx);
      }
   }

   @Test
   void aMarkedMonthlyBarThinsInOneStepWithItsYears() {
      // 24 months from Jan 2024
      Object[][] rows = new Object[25][];
      rows[0] = new Object[2];

      for(int i = 1; i <= 24; i++) {
         rows[i] = new Object[] { new java.sql.Date(124, i - 1, 1), i * 10.0 };
      }

      Chart chart = barChart(dateDim("Date", XConstants.MONTH_DATE_GROUP, XConstants.SORT_ASC),
                             rows);
      DefaultAxis axis = ((RectCoord) plot(chart, VizMark.MODERN_LIGHT, 400, 240).getCoordinate())
         .getXAxis1();
      axis.prepareLabels();
      List<Integer> idx = shownIndices(axis);
      List<String> texts = shownTexts(axis);

      assertTrue(idx.size() > 2 && idx.size() < 24, "thinned " + texts);
      assertOneStep(idx, texts);

      for(int i = 0; i < idx.size(); i++) {
         boolean starts = i == 0 || idx.get(i) >= 12 && idx.get(i - 1) < 12;
         assertEquals(starts, texts.get(i).contains("202"), "year where it changes " + texts);
      }

      assertGap(axis, texts);
   }

   @Test
   void thirtyStoresAt45ShowTheSmallestStepThatKeepsTheGap() {
      // at 45 degrees neighbours need (line height + 2) / sin 45 between slots
      Chart chart = barChart(names("Store ", 30, true));
      DefaultAxis axis = ((RectCoord) plot(chart, VizMark.MODERN_LIGHT, 400, 240).getCoordinate())
         .getXAxis1();
      axis.prepareLabels();
      List<Integer> idx = shownIndices(axis);
      List<String> texts = shownTexts(axis);
      int step = idx.get(2) - idx.get(1);
      VLabel a = axis.getLabels()[idx.get(1)];
      VLabel b = axis.getLabels()[idx.get(2)];
      double slot = (b.getTextPosition().getX() - a.getTextPosition().getX()) / step;
      double need = (GTool.getFontMetrics(a.getFont()).getHeight() + 2) / Math.sin(Math.PI / 4);

      assertEquals(45, (int) axis.getScale().getAxisSpec().getTextSpec().getRotation());
      assertTrue(step * slot >= need - 1e-6, step + " x " + slot + " for " + need + " " + texts);
      assertTrue((step - 1) * slot < need, step + " x " + slot + " for " + need + " " + texts);
      assertEquals(0, idx.get(0) % step, idx + " " + texts);
      assertTrue(idx.get(0) <= step, idx + " " + texts);
      assertOneStep(idx, texts);
   }

   @Test
   void largerMarkedLabelsThinInOneStepFromTheFirst() {
      Chart chart = barChart(names("Store ", 30, true));
      CompositeTextFormat format = chart.info().getXField(0).getAxisDescriptor()
         .getAxisLabelTextFormat();
      format.setFont(format.getFont().deriveFont(14f));
      Scale x = xScale(chart, VizMark.MODERN_LIGHT);
      DefaultAxis axis = ((RectCoord) plot(chart, VizMark.MODERN_LIGHT, 400, 240).getCoordinate())
         .getXAxis1();
      axis.prepareLabels();
      List<Integer> idx = shownIndices(axis);

      int step = idx.get(1) - idx.get(0);

      assertEquals(92.4, x.getAxisSpec().getLabelLimit(), 0.01);
      assertEquals(45, rotation(x));
      // the step runs from the first label, which is hidden only if, slid inside, it crowds
      // the next one in step
      assertEquals(0, idx.get(0) % step, "shown " + idx);
      assertTrue(idx.get(0) <= step, "shown " + idx);
      assertOneStep(idx, shownTexts(axis));
   }

   @Test
   void stackedCategoryLabelsKeepTheirLineSpacing() {
      // twelve tasks a line apart: the gap between labels side by side doesn't apply
      GraphGenerator gen = generator(ganttChart(12, false), VizMark.MODERN_LIGHT,
                                     new Dimension(400, 240));
      VGraph vgraph = Plotter.getPlotter(gen.createEGraph())
         .plotAndLayout(gen.getData(), 0, 0, 400, 240);
      DefaultAxis tasks = ((RectCoord) vgraph.getCoordinate()).getXAxis1();
      tasks.prepareLabels();

      assertEquals(110, tasks.getScale().getAxisSpec().getLabelLimit(), 0.01);
      assertEquals(12, shownIndices(tasks).size(), shownTexts(tasks).toString());
   }

   @Test
   void aMarkedBarWithManyCategoriesKeepsItsCardWidth() {
      // 50 bars at their 10px minimum fit under the 1.5x forced scroll
      Chart chart = barChart(names("Store ", 50, true));

      assertEquals(400, expandedWidth(plot(chart, VizMark.MODERN_LIGHT, 400, 240), chart), 0.5);
   }

   @Test
   void aMarkedBarScrollsOnlyForItsBars() {
      Chart chart = barChart(campaigns(105));
      VGraph vgraph = plot(chart, VizMark.MODERN_LIGHT, 400, 240);

      // 105 bars at their 10px minimum
      assertEquals(1050, vgraph.getMinPlotWidth(), 0.5);
   }

   @Test
   void aMarkedFacetFitsItsCard() {
      for(Dimension size : new Dimension[] { new Dimension(300, 180), new Dimension(400, 240) }) {
         Chart chart = regionFacetChart();
         VGraph vgraph = plot(chart, VizMark.MODERN_LIGHT, size.width, size.height);

         assertEquals(size.width, expandedWidth(vgraph, chart), 0.5, "at " + size);
      }
   }

   @Test
   void classicChartsKeepTodaysWidth() {
      Chart bars = barChart(campaigns(105));
      Chart facet = regionFacetChart();
      VGraph classic = plot(bars, null, 400, 240);
      Font font = ((RectCoord) classic.getCoordinate()).getXAxis1().getLabels()[0].getFont();
      double side = classic.getBounds().getWidth() - classic.getPlotBounds().getWidth();

      // today's chart scrolls to one line of label text per category, measured before the
      // label limit, beside its Y axis
      assertEquals(105 * GTool.getFontMetrics(font).getHeight() + side,
                   expandedWidth(classic, bars), 0.5);
      assertEquals(CLASSIC_FACET_300_WIDTH, expandedWidth(plot(facet, null, 300, 180), facet), 0.5);
   }

   @Test
   void aGanttTimeAxisThinsInsteadOfCutting() {
      GraphGenerator gen = generator(ganttChart(30, false), VizMark.MODERN_LIGHT,
                                     new Dimension(400, 480));
      EGraph graph = gen.createEGraph();
      Scale time = ((RectCoord) graph.getCoordinate()).getYScale();
      assertEquals(66, time.getAxisSpec().getLabelLimit(), 0.01);
      assertEquals(0, rotation(time));

      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(gen.getData(), 0, 0, 400, 480);
      DefaultAxis axis = ((RectCoord) vgraph.getCoordinate()).getYAxis1();
      axis.prepareLabels();
      List<String> texts = shownTexts(axis);
      List<Integer> idx = shownIndices(axis);

      assertTrue(texts.size() > 2, texts.toString());
      assertTrue(texts.stream().noneMatch(t -> t.endsWith("..")), texts.toString());
      // week dates fit under the limit, so none is cut either
      assertTrue(texts.stream().noneMatch(t -> t.endsWith("…")), texts.toString());
      assertTrue(texts.get(0).contains("2026"), texts.toString());

      for(int i = 1; i < idx.size(); i++) {
         assertEquals(idx.get(1) - idx.get(0), idx.get(i) - idx.get(i - 1), "one step " + idx);
      }

      List<Rectangle2D> boxes = shownBounds(axis);

      for(int i = 0; i < boxes.size() - 1; i++) {
         assertTrue(boxes.get(i + 1).getX() - boxes.get(i).getMaxX() >= 4 - 1e-3,
                    "gap after " + texts.get(i) + " " + texts);
      }
   }

   @Test
   void aGanttTimeAxisSlidesItsFirstTickInside() {
      // the first tick's full date runs past the axis start: it slides inside the axis, and
      // while a slid first label crowds the next one in step it is hidden and the next one
      // takes the year; the step is the one on the ticks
      GraphGenerator gen = generator(ganttChart(30, false), VizMark.MODERN_LIGHT,
                                     new Dimension(400, 480));
      VGraph vgraph = Plotter.getPlotter(gen.createEGraph())
         .plotAndLayout(gen.getData(), 0, 0, 400, 480);
      DefaultAxis axis = ((RectCoord) vgraph.getCoordinate()).getYAxis1();
      axis.prepareLabels();
      List<Integer> idx = shownIndices(axis);
      List<String> texts = shownTexts(axis);
      Rectangle2D first = shownBounds(axis).get(0);

      assertEquals(3, idx.get(1) - idx.get(0), texts.toString());
      assertEquals(0, idx.get(0) % 3, texts.toString());
      assertTrue(texts.get(0).contains("2026"), texts.toString());
      assertTrue(first.getX() >= vgraph.getPlotBounds().getX() - 0.01,
                 "first label at " + first.getX() + ", plot at " + vgraph.getPlotBounds().getX());
      assertOneStep(idx, texts);
   }

   @Test
   void aDateFacetSlidesEachPanelsFirstPeriodInside() {
      VSChartDimensionRef year = dateDim("Year", XConstants.YEAR_DATE_GROUP, XConstants.SORT_ASC);
      VSChartDimensionRef month = dateDim("Month", XConstants.MONTH_DATE_GROUP,
                                          XConstants.SORT_ASC);
      VSChartAggregateRef total = agg("Total");
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      cinfo.addXField(year);
      cinfo.addXField(month);
      cinfo.addYField(total);
      cinfo.updateChartType(false);
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { GraphUtil.getName(year), GraphUtil.getName(month),
                              GraphUtil.getName(total) });

      for(int y = 0; y < 2; y++) {
         for(int m = 0; m < 12; m++) {
            rows.add(new Object[] { new java.sql.Date(124 + y, 0, 1),
                                    new java.sql.Date(124 + y, m, 1), 10.0 * (m + 1) });
         }
      }

      VGraph vgraph = plot(new Chart(cinfo, rows.toArray(new Object[0][])),
                           VizMark.MODERN_LIGHT, 400, 240);
      int panels = 0;

      for(DefaultAxis axis : vgraph.getCoordinate().getAxesAt(
         inetsoft.graph.coord.Coordinate.BOTTOM_AXIS))
      {
         if(!axis.getScale().getFields()[0].contains("Month")) {
            continue;
         }

         axis.prepareLabels();
         List<Integer> idx = shownIndices(axis);
         List<String> texts = shownTexts(axis);
         panels++;

         assertEquals(3, idx.get(0), "panel " + panels + " " + texts);
         assertEquals(3, idx.get(1) - idx.get(0), "panel " + panels + " " + texts);
         assertTrue(texts.get(0).contains(" 202"), "the year on the first " + texts);
         assertOneStep(idx, texts);
      }

      assertEquals(2, panels, "premise: two panels");
   }

   @Test
   void compactDatesBringTheLabelLimit() {
      GraphGenerator gen = generator(monthByMonthChart(), VizMark.MODERN_LIGHT);
      String y = GraphUtil.getName(monthByMonthChart().info().getYField(0));
      CompositeTextFormat rotated = new CompositeTextFormat();
      rotated.setRotation(30);
      AxisSpec time = new AxisSpec();
      AxisSpec outer = new AxisSpec();
      AxisSpec author = new AxisSpec();
      AxisSpec wrapped = new AxisSpec();
      wrapped.setTruncate(false);

      gen.applyCompactDates(new TimeScale(y), time, new CompositeTextFormat(), new String[] { y },
                            true);
      gen.applyCompactDates(new CategoricalScale(y), outer, new CompositeTextFormat(),
                            new String[] { y }, false);
      gen.applyCompactDates(new TimeScale(y), author, rotated, new String[] { y }, true);
      gen.applyCompactDates(new TimeScale(y), wrapped, new CompositeTextFormat(),
                            new String[] { y }, true);

      assertEquals(66, time.getLabelLimit(), 0.01);
      assertNotNull(outer.getCompactDates(), "premise: an outer facet date axis");
      assertEquals(0, outer.getLabelLimit(), 0.01, "a facet's outer labels are not cut");
      assertNotNull(author.getCompactDates(), "premise: compact under an author rotation");
      assertEquals(0, author.getLabelLimit(), 0.01);
      assertEquals(0, wrapped.getLabelLimit(), 0.01);
   }

   @Test
   void classicGanttTimeAxisKeepsTodaysCut() {
      EGraph graph = generator(ganttChart(30, false), null, new Dimension(400, 480)).createEGraph();

      assertEquals(0, ((RectCoord) graph.getCoordinate()).getYScale().getAxisSpec()
         .getLabelLimit(), 0.01);
   }

   private static void assertOneStep(List<Integer> idx, List<String> texts) {
      for(int i = 1; i < idx.size(); i++) {
         assertEquals(idx.get(1) - idx.get(0), idx.get(i) - idx.get(i - 1),
                      "one step " + idx + " " + texts);
      }
   }

   // flat labels side by side keep 4px between them
   private static void assertGap(DefaultAxis axis, List<String> texts) {
      List<Rectangle2D> boxes = shownBounds(axis);

      for(int i = 0; i < boxes.size() - 1; i++) {
         assertTrue(boxes.get(i + 1).getX() - boxes.get(i).getMaxX() >= 4 - 1e-3,
                    "gap after " + texts.get(i) + " " + texts);
      }
   }

   // the width VGraphPair draws the chart at: the card, or the plot's minimum if it scrolls
   private static double expandedWidth(VGraph vgraph, Chart chart) {
      double width = vgraph.getBounds().getWidth();
      double plot = vgraph.getPlotBounds().getWidth();
      double min = Math.min(vgraph.getMinPlotWidth(), 1000000);

      if(GraphUtil.isScrollable(vgraph, chart.info()) && min >= plot &&
         GraphUtil.isHScrollable(vgraph, chart.info()))
      {
         return min + width - plot;
      }

      return width;
   }

   private static VGraph plot(Chart chart, VizMark mark, int width, int height) {
      GraphGenerator gen = generator(chart, mark, new Dimension(width, height));
      return Plotter.getPlotter(gen.createEGraph()).plotAndLayout(gen.getData(), 0, 0, width,
                                                                 height);
   }

   private static String[] campaigns(int count) {
      String[] names = new String[count];

      for(int i = 0; i < count; i++) {
         names[i] = String.format("Campaign %03d", i + 1);
      }

      return names;
   }

   private static Chart lineChart(String[] categories) {
      Chart chart = barChart(categories);
      chart.info().setChartType(GraphTypes.CHART_LINE);
      chart.info().setRTChartType(GraphTypes.CHART_LINE);
      chart.info().updateChartType(false);
      return chart;
   }

   // three regions of four states each
   private static Chart regionFacetChart() {
      VSChartDimensionRef region = dim("Region");
      VSChartDimensionRef state = dim("State");
      VSChartAggregateRef total = agg("Total");
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      cinfo.addXField(region);
      cinfo.addXField(state);
      cinfo.addYField(total);
      cinfo.updateChartType(false);
      String[][] states = { { "Illinois", "Ohio", "Michigan", "Indiana" },
                            { "New York", "New Jersey", "Pennsylvania", "Massachusetts" },
                            { "California", "Oregon", "Washington", "Nevada" } };
      String[] regions = { "Central", "East", "West" };
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { GraphUtil.getName(region), GraphUtil.getName(state),
                              GraphUtil.getName(total) });

      for(int r = 0; r < regions.length; r++) {
         for(int s = 0; s < states[r].length; s++) {
            rows.add(new Object[] { regions[r], states[r][s], 100.0 * (s + 1) });
         }
      }

      return new Chart(cinfo, rows.toArray(new Object[0][]));
   }

   private static List<Integer> shownIndices(DefaultAxis axis) {
      List<Integer> idx = new ArrayList<>();
      VLabel[] labels = axis.getLabels();

      for(int i = 0; i < labels.length; i++) {
         if(labels[i].getZIndex() >= 0) {
            idx.add(i);
         }
      }

      return idx;
   }

   private static List<Rectangle2D> shownBounds(DefaultAxis axis) {
      List<Rectangle2D> list = new ArrayList<>();

      for(int i : shownIndices(axis)) {
         VLabel label = axis.getLabels()[i];
         Font font = label.getFont();
         list.add(new Rectangle2D.Double(label.getTextPosition().getX(),
                                         label.getTextPosition().getY(),
                                         GTool.stringWidth(label.getDisplayLabel(), font),
                                         GTool.getFontMetrics(font).getHeight()));
      }

      return list;
   }

   private static CompactDates compactDates(GraphGenerator gen, Scale scale) {
      AxisSpec axis = new AxisSpec();
      gen.applyCompactDates(scale, axis, new CompositeTextFormat(), scale.getFields(), true);
      return axis.getCompactDates();
   }

   // a month axis on X and on Y
   private static Chart monthByMonthChart() {
      VSChartDimensionRef order = dateDim("Order", XConstants.MONTH_DATE_GROUP,
                                          XConstants.SORT_ASC);
      VSChartDimensionRef ship = dateDim("Ship", XConstants.MONTH_DATE_GROUP,
                                         XConstants.SORT_ASC);
      VSChartAggregateRef total = agg("Total");
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_POINT);
      cinfo.setRTChartType(GraphTypes.CHART_POINT);
      cinfo.addXField(order);
      cinfo.addYField(ship);
      cinfo.updateChartType(false);
      Object[][] rows = {
         { GraphUtil.getName(order), GraphUtil.getName(ship), GraphUtil.getName(total) },
         { new java.sql.Date(125, 0, 1), new java.sql.Date(125, 1, 1), 10.0 },
         { new java.sql.Date(125, 1, 1), new java.sql.Date(125, 2, 1), 20.0 },
      };

      return new Chart(cinfo, rows);
   }

   private static double[] yRange(Chart chart, VizMark mark) {
      GraphGenerator gen = generator(chart, mark);
      VGraph vgraph = Plotter.getPlotter(gen.createEGraph())
         .plotAndLayout(gen.getData(), 0, 0, 400, 240);
      Scale y = ((RectCoord) vgraph.getCoordinate()).getYScale();
      return new double[] { y.getMin(), y.getMax() };
   }

   // eight stores between 1010 and 1080
   private static Chart lineChart() {
      VSChartDimensionRef store = dim("Store");
      VSChartAggregateRef total = agg("Total");
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_LINE);
      cinfo.setRTChartType(GraphTypes.CHART_LINE);
      cinfo.addXField(store);
      cinfo.addYField(total);
      cinfo.updateChartType(false);
      Object[][] rows = new Object[9][];
      rows[0] = new Object[] { GraphUtil.getName(store), GraphUtil.getName(total) };

      for(int i = 1; i < 9; i++) {
         rows[i] = new Object[] { "Store " + i, 1000.0 + i * 10 };
      }

      return new Chart(cinfo, rows);
   }

   // eight days of prices around 1050
   private static Chart candleChart() {
      VSChartAggregateRef high = agg("High");
      VSChartAggregateRef close = agg("Close");
      VSChartAggregateRef open = agg("Open");
      VSChartAggregateRef low = agg("Low");
      CandleVSChartInfo cinfo = new CandleVSChartInfo();
      cinfo.addXField(dim("Day"));
      cinfo.setHighField(high);
      cinfo.setRTHighField(high);
      cinfo.setCloseField(close);
      cinfo.setRTCloseField(close);
      cinfo.setOpenField(open);
      cinfo.setRTOpenField(open);
      cinfo.setLowField(low);
      cinfo.setRTLowField(low);
      cinfo.setChartType(GraphTypes.CHART_CANDLE);
      cinfo.setRTChartType(GraphTypes.CHART_CANDLE);
      cinfo.updateChartType(false);
      Object[][] rows = new Object[9][];
      rows[0] = new Object[] { "Day", GraphUtil.getName(high), GraphUtil.getName(close),
                               GraphUtil.getName(open), GraphUtil.getName(low) };

      for(int i = 1; i < 9; i++) {
         rows[i] = new Object[] { "D" + i, 1080.0 + i, 1050.0 + i, 1040.0 + i, 1010.0 + i };
      }

      return new Chart(cinfo, rows);
   }

   // eight hours of one day, grouped by hour
   private static Chart hourlyChart() {
      VSChartDimensionRef hour = dateDim("Date", XConstants.HOUR_DATE_GROUP, XConstants.SORT_ASC);
      hour.setDataType(XSchema.TIME_INSTANT);
      Object[][] rows = new Object[9][];
      rows[0] = new Object[2];

      for(int i = 1; i < 9; i++) {
         rows[i] = new Object[] { new java.sql.Timestamp(126, 0, 5, 8 + i, 0, 0, 0), i * 10.0 };
      }

      return barChart(hour, rows);
   }

   // the hour level's pattern, as a chart's data carries it
   private static final class HourFormatDataSet extends DefaultDataSet implements AttributeDataSet {
      HourFormatDataSet(Object[][] rows) {
         super(rows);
      }

      @Override
      public HRef getHyperlink(int col, int row) {
         return null;
      }

      @Override
      public void setHyperlink(int col, int row, HRef link) {
      }

      @Override
      public Format getFormat(int col, int row) {
         return col == 0 ? new ExtendedDateFormat("yyyy-MM-dd HH") : null;
      }
   }

   // tasks with long names, starting on the 1st of each month or every 3 days
   private static Chart ganttChart(int count, boolean monthly) {
      VSChartDimensionRef task = dim("Task");
      VSChartAggregateRef start = agg("Start");
      start.setFormulaValue("Min");
      VSChartAggregateRef end = agg("End");
      end.setFormulaValue("Max");
      GanttVSChartInfo gantt = new GanttVSChartInfo();
      gantt.setChartType(GraphTypes.CHART_GANTT);
      gantt.setRTChartType(GraphTypes.CHART_GANTT);
      gantt.addYField(task);
      gantt.setStartField(start);
      gantt.setEndField(end);
      gantt.setRTYFields(new ChartRef[] { task });
      gantt.setRTStartField(start);
      gantt.setRTEndField(end);
      Object[][] rows = new Object[count + 1][];
      rows[0] = new Object[] { "Task", GraphUtil.getName(start), GraphUtil.getName(end) };

      for(int i = 1; i <= count; i++) {
         Date from = monthly ? date(2026, Calendar.JANUARY + i - 1, 1)
            : date(2026, Calendar.JANUARY, 1 + i * 3);
         Date to = monthly ? date(2026, Calendar.JANUARY + i - 1, 20)
            : date(2026, Calendar.JANUARY, 21 + i * 3);
         rows[i] = new Object[] { "Workstream task number " + i, from, to };
      }

      return new Chart(gantt, rows);
   }

   // regions by state, with the total on Color
   private static Chart heatMap() {
      VSChartDimensionRef region = dim("Region");
      VSChartDimensionRef state = dim("State");
      VSChartAggregateRef total = agg("Total");
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(total);
      color.setRTDataRef(total);
      color.setVisualFrameWrapper(new GradientColorFrameWrapper());
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_POINT);
      cinfo.setRTChartType(GraphTypes.CHART_POINT);
      cinfo.addXField(region);
      cinfo.addYField(state);
      cinfo.setColorField(color);
      cinfo.updateChartType(false);
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { GraphUtil.getName(region), GraphUtil.getName(state),
                              GraphUtil.getName(total) });

      for(int r = 0; r < 3; r++) {
         for(int s = 0; s < 5; s++) {
            rows.add(new Object[] { "Region " + r, "State number " + s, 10.0 * (r + s + 1) });
         }
      }

      return new Chart(cinfo, rows.toArray(new Object[0][]));
   }

   private static Date date(int year, int month, int day) {
      Calendar cal = Calendar.getInstance();
      cal.clear();
      cal.set(year, month, day);
      return cal.getTime();
   }

   private static List<String> shownTexts(DefaultAxis axis) {
      List<String> texts = new ArrayList<>();

      for(VLabel label : axis.getLabels()) {
         if(label.getZIndex() >= 0) {
            texts.add(String.join("", label.getDisplayLabel()));
         }
      }

      return texts;
   }

   // the shown labels' texts before the length limit cuts them
   private static List<String> shownFullTexts(DefaultAxis axis) {
      List<String> texts = new ArrayList<>();

      for(VLabel label : axis.getLabels()) {
         if(label.getZIndex() >= 0) {
            texts.add(label.getText());
         }
      }

      return texts;
   }

   private static VSChartDimensionRef dateDim(String name, int level, int order) {
      VSChartDimensionRef dim = dim(name);
      dim.setDataType(XSchema.DATE);
      dim.setDateLevelValue(String.valueOf(level));
      dim.setOrder(order);
      return dim;
   }

   private static Chart dateChart(int level, int order) {
      VSChartDimensionRef dim = dateDim("Date", level, order);
      Object[][] rows = new Object[7][];
      rows[0] = new Object[2];

      for(int i = 1; i < 7; i++) {
         rows[i] = new Object[] { new java.sql.Date(100, i, 1), i * 10.0 };
      }

      return barChart(dim, rows);
   }

   private static String[] names(String prefix, int count, boolean numbered) {
      String[] names = new String[count];

      for(int i = 0; i < count; i++) {
         names[i] = numbered ? String.format("%s%02d", prefix, i + 1)
            : String.valueOf((char) ('A' + i));
      }

      return names;
   }

   private static Scale scaleOf(EGraph graph, String field) {
      for(Scale scale : graph.getCoordinate().getScales()) {
         if(scale.getFields().length > 0 && scale.getFields()[0].contains(field)) {
            return scale;
         }
      }

      return null;
   }

   private static int rotation(Scale scale) {
      return (int) scale.getAxisSpec().getTextSpec().getRotation();
   }

   private static Scale xScale(Chart chart, VizMark mark) {
      EGraph graph = generator(chart, mark).createEGraph();
      return ((RectCoord) graph.getCoordinate()).getXScale();
   }

   private record Chart(VSChartInfo info, Object[][] rows) {
   }

   private static Chart barChart(String[] categories) {
      VSChartDimensionRef store = dim("Store");
      Object[][] rows = new Object[categories.length + 1][];
      rows[0] = new Object[2];

      for(int i = 0; i < categories.length; i++) {
         rows[i + 1] = new Object[] { categories[i], (i + 1) * 10.0 };
      }

      return barChart(store, rows);
   }

   private static Chart barChart(VSChartDimensionRef dim, Object[][] rows) {
      VSChartAggregateRef total = agg("Total");
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      cinfo.addXField(dim);
      cinfo.addYField(total);
      cinfo.updateChartType(false);
      rows[0][0] = GraphUtil.getName(dim);
      rows[0][1] = GraphUtil.getName(total);
      return new Chart(cinfo, rows);
   }

   private static Chart facetChart() {
      VSChartDimensionRef region = dim("Region");
      VSChartDimensionRef state = dim("State");
      VSChartAggregateRef total = agg("Total");
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      cinfo.addXField(region);
      cinfo.addXField(state);
      cinfo.addYField(total);
      cinfo.updateChartType(false);
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { GraphUtil.getName(region), GraphUtil.getName(state),
                              GraphUtil.getName(total) });

      for(int r = 0; r < 2; r++) {
         for(int s = 0; s < 4; s++) {
            rows.add(new Object[] { "R" + r, "State " + s, 10.0 * (s + 1) });
         }
      }

      return new Chart(cinfo, rows.toArray(new Object[0][]));
   }

   private static GraphGenerator generator(Chart chart, VizMark mark) {
      return generator(chart, mark, new Dimension(400, 240));
   }

   private static GraphGenerator generator(Chart chart, VizMark mark, Dimension size) {
      return generator(chart, mark, size, DefaultDataSet::new);
   }

   private static GraphGenerator generator(Chart chart, VizMark mark, Dimension size,
                                           java.util.function.Function<Object[][], DataSet> data)
   {
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(chart.info());
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);

      return GraphGenerator.getGenerator(assemblyInfo, null, data.apply(chart.rows()),
                                         new VariableTable(), null, XSourceInfo.NONE, size);
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new BaseField(name));
      agg.setFormulaValue("Sum");
      return agg;
   }
}
