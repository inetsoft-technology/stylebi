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
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.LineElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.CompactDates;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.guide.axis.GridLine;
import inetsoft.graph.guide.axis.VDimensionLabel;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.scale.TimeScale;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSDensityDefaults;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.util.ExtendedDateFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernGanttTicksTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedGanttLabelsEveryTick() {
      Gantt g = gantt(25, VizMark.MODERN_LIGHT, new Dimension(400, 240), false);
      RectCoord coord = (RectCoord) g.gen.createEGraph().getCoordinate();
      TimeScale time = (TimeScale) coord.getYScale();
      Insets padding = VSDensityDefaults.chartPadding(VizContext.of(g.info));
      double tasks = ModernLabelRule.columnLimitPx(
         coord.getXScale().getAxisSpec().getTextSpec().getFont(), 400);
      double plot = 400 - padding.left - padding.right - tasks;
      Font font = time.getAxisSpec().getTextSpec().getFont();

      assertTrue(time.getLabelFitPlotPx() > 0);
      assertEquals(plot, time.getLabelFitPlotPx(), 1e-9);
      assertEquals(GanttGraphGenerator.widestMonthLabel(font, CompactDateRule.locale()) + 4,
                   time.getLabelFitLabelPx(), 1e-9);
      assertFalse(time.getAxisSpec().isLabelBetween());
   }

   @Test
   void gridLinesSitOnlyAtLabelledTicks() {
      Gantt g = gantt(25, VizMark.MODERN_LIGHT, new Dimension(400, 240), false);
      EGraph graph = g.gen.createEGraph();
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(g.gen.getData(), 0, 0, 400, 240);
      RectCoord coord = (RectCoord) vgraph.getCoordinate();
      List<Double> labelX = new ArrayList<>();
      List<String> texts = new ArrayList<>();
      List<Double> lineX = new ArrayList<>();

      for(Axis axis : coord.getAxes(false)) {
         if(!(axis.getScale() instanceof TimeScale)) {
            continue;
         }

         for(VLabel label : axis.getLabels()) {
            if(label.getZIndex() >= 0) {
               labelX.add(label.getBounds().getCenterX());
               texts.add(label.getText());
            }
         }

         for(int i = 0; i < axis.getGridLineCount(); i++) {
            GridLine line = axis.getGridLine(i);

            if(line.getZIndex() >= 0) {
               lineX.add(line.getShape().getBounds2D().getCenterX());
            }
         }
      }

      assertFalse(lineX.isEmpty(), "premise: grid lines");
      assertFalse(labelX.isEmpty(), "premise: labels");

      for(double x : lineX) {
         assertTrue(labelX.stream().anyMatch(lx -> Math.abs(lx - x) < 2),
                    "grid line at " + x + " has no label, labels at " + labelX);
      }

      assertTrue(texts.stream().anyMatch(t -> t.startsWith("Apr")), texts.toString());
      assertTrue(texts.stream().anyMatch(t -> t.startsWith("Feb")), texts.toString());
      assertTrue(texts.stream().anyMatch(t -> t.startsWith("Mar")), texts.toString());
      assertTrue(texts.get(0).contains("2026"), texts.toString());
      assertEquals(1, texts.stream().filter(t -> t.contains("2026")).count(), texts.toString());
   }

   @Test
   void aMarkedGanttAxisStartsAtTheFirstTask() {
      Gantt g = gantt(25, VizMark.MODERN_LIGHT, new Dimension(400, 240), false);
      VGraph vgraph = Plotter.getPlotter(g.gen.createEGraph())
         .plotAndLayout(g.gen.getData(), 0, 0, 400, 240);
      Scale time = ((RectCoord) vgraph.getCoordinate()).getYScale();

      assertInstanceOf(TimeScale.class, time, "premise: the time scale");
      assertEquals(date(2026, Calendar.JANUARY, 4).getTime(), time.getMin(), 0);
   }

   @Test
   void anAuthorIncrementKeepsTheClassicAxis() {
      Gantt g = gantt(25, VizMark.MODERN_LIGHT, new Dimension(400, 240), false);
      g.start.getAxisDescriptor().setIncrement(7);
      GraphGenerator gen = GraphGenerator.getGenerator(g.info, null, g.gen.getData(),
                                                       new VariableTable(), null,
                                                       XSourceInfo.NONE, new Dimension(400, 240));
      TimeScale time = (TimeScale) ((RectCoord) gen.createEGraph().getCoordinate()).getYScale();

      assertEquals(0, time.getLabelFitPlotPx(), 0);
      assertTrue(time.getAxisSpec().isLabelBetween());
   }

   @Test
   void anIntradayGanttKeepsHourTicks() {
      Gantt g = gantt(8, VizMark.MODERN_LIGHT, new Dimension(400, 240), true);
      VGraph vgraph = Plotter.getPlotter(g.gen.createEGraph())
         .plotAndLayout(g.gen.getData(), 0, 0, 400, 240);
      TimeScale time = (TimeScale) ((RectCoord) vgraph.getCoordinate()).getYScale();

      assertTrue(time.getLabelFitPlotPx() > 0, "premise: the fit is on");
      assertEquals(TimeScale.HOUR, time.getType());
   }

   @Test
   void anIntradayGanttLabelsEveryTick() {
      for(Object[] chart : new Object[][] { { GANTT_DAY, SMALL }, { GANTT_DAY, WIDE },
                                            { GANTT_MIN, SMALL } })
      {
         Dimension size = (Dimension) chart[1];
         DefaultAxis axis = timeAxis(gantt((Date[][]) chart[0], VizMark.MODERN_LIGHT, size), size);
         List<Tick> ticks = ticks(axis);
         List<String> texts = texts(ticks);
         String where = size.width + " " + texts;

         assertEquals(TimeScale.HOUR, ((TimeScale) axis.getScale()).getType(), "premise " + where);
         assertTrue(ticks.size() > 1, "premise: hour ticks " + where);
         assertFalse(texts.contains(null), "a tick without a label " + where);
         assertEquals(texts.size(), new HashSet<>(texts).size(), "a repeated label " + where);
         assertEquals("09:00\nMar 2", texts.get(0), where);
         assertTrue(texts.subList(1, texts.size()).stream().allMatch(t -> t.matches("\\d\\d:00")),
                    where);
         assertTrue(ticks.stream().noneMatch(Tick::cut), "a cut label " + where);
      }
   }

   @Test
   void aMultiDayGanttDatesEachDay() {
      for(Dimension size : new Dimension[] { SMALL, WIDE }) {
         DefaultAxis axis = timeAxis(gantt(GANTT_MULTI, VizMark.MODERN_LIGHT, size), size);
         List<Tick> ticks = ticks(axis);
         List<String> texts = texts(ticks);
         String where = size.width + " " + texts;
         Calendar cal = Calendar.getInstance();
         int prevDay = -1;

         assertEquals(TimeScale.HOUR, ((TimeScale) axis.getScale()).getType(), "premise " + where);
         assertFalse(texts.contains(null), "a tick without a label " + where);
         assertTrue(ticks.stream().noneMatch(Tick::cut), "a cut label " + where);

         for(int i = 0; i < ticks.size(); i++) {
            cal.setTime(ticks.get(i).value());
            int day = cal.get(Calendar.DAY_OF_MONTH);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            String date = "Mar " + day;
            String time = String.format("%02d:00", hour);
            // the date under the time where the day changes
            String expected = day == prevDay ? time : time + "\n" + date;

            assertEquals(expected, texts.get(i), where);
            prevDay = day;
         }

         assertTrue(texts.contains("00:00\nMar 3"), "premise: a day change " + where);
      }
   }

   @Test
   void aMinuteGanttLabelsEveryTick() {
      // starts a minute apart, so the data-spaced unit is a minute
      Date[][] spans = { { time(2, 9, 0), time(2, 9, 5) }, { time(2, 9, 1), time(2, 9, 12) },
                         { time(2, 9, 10), time(2, 9, 25) }, { time(2, 9, 20), time(2, 9, 40) } };

      for(Dimension size : new Dimension[] { SMALL, WIDE }) {
         DefaultAxis axis = timeAxis(gantt(spans, VizMark.MODERN_LIGHT, size), size);
         List<String> texts = texts(ticks(axis));
         String where = size.width + " " + texts;

         assertEquals(TimeScale.MINUTE, ((TimeScale) axis.getScale()).getType(),
                      "premise " + where);
         assertTrue(texts.size() > 1, "premise: minute ticks " + where);
         assertFalse(texts.contains(null), "a tick without a label " + where);
         assertEquals(texts.size(), new HashSet<>(texts).size(), "a repeated label " + where);
         assertEquals("09:00\nMar 2", texts.get(0), where);
         assertTrue(texts.subList(1, texts.size()).stream().allMatch(t -> t.matches("09:\\d\\d")),
                    where);
      }
   }

   @Test
   void timeValuesShowNoDate() {
      Date[][] spans = new Date[GANTT_DAY.length][];

      for(int i = 0; i < spans.length; i++) {
         spans[i] = new Date[] { new java.sql.Time(GANTT_DAY[i][0].getTime()),
                                 new java.sql.Time(GANTT_DAY[i][1].getTime()) };
      }

      DefaultAxis axis = timeAxis(gantt(spans, VizMark.MODERN_LIGHT, SMALL), SMALL);
      List<String> texts = texts(ticks(axis));

      assertEquals(TimeScale.HOUR, ((TimeScale) axis.getScale()).getType(), "premise " + texts);
      assertFalse(texts.contains(null), "a tick without a label " + texts);
      assertTrue(texts.stream().allMatch(t -> t.matches("\\d\\d:00")), texts.toString());
   }

   @Test
   void yearStepsDrawTheYear() {
      DefaultAxis axis = timeAxis(gantt(GANTT_YEARS, VizMark.MODERN_LIGHT, SMALL), SMALL);
      List<String> texts = texts(ticks(axis));

      assertEquals(TimeScale.YEAR, ((TimeScale) axis.getScale()).getType(), "premise " + texts);
      assertTrue(texts.size() > 1, "premise: year ticks " + texts);
      assertFalse(texts.contains(null), "a tick without a label " + texts);
      assertTrue(texts.stream().allMatch(t -> t.matches("20\\d\\d")), texts.toString());
      assertEquals(texts.size(), new HashSet<>(texts).size(), "a repeated label " + texts);
   }

   @Test
   void classicIntradayAndYearGanttsKeepTheirLabels() {
      assertEquals(CLASSIC_DAY, texts(ticks(timeAxis(gantt(GANTT_DAY, null, SMALL), SMALL))));
      assertEquals(CLASSIC_DAY, texts(ticks(timeAxis(gantt(GANTT_DAY, null, WIDE), WIDE))));
      assertEquals(CLASSIC_MULTI, texts(ticks(timeAxis(gantt(GANTT_MULTI, null, WIDE), WIDE))));

      // which years the classic thinning hides depends on the font
      List<Tick> years = ticks(timeAxis(gantt(GANTT_YEARS, null, SMALL), SMALL));
      ExtendedDateFormat day = new ExtendedDateFormat("yyyy-MM-dd");
      String where = texts(years).toString();

      assertEquals(CLASSIC_YEARS, years.stream().map(t -> day.format(t.value())).toList(), where);
      assertTrue(years.stream().filter(t -> t.text() != null).count() > 1,
                 "premise: shown labels " + where);
      assertTrue(years.stream().allMatch(t -> t.text() == null ||
                                         t.text().equals(day.format(t.value()))), where);
   }

   @Test
   void anAuthorIncrementKeepsTheIntradayLabels() {
      Gantt g = gantt(GANTT_DAY, VizMark.MODERN_LIGHT, SMALL);
      g.start.getAxisDescriptor().setIncrement(1);
      Gantt inc = new Gantt(GraphGenerator.getGenerator(g.info, null, g.gen.getData(),
                                                        new VariableTable(), null,
                                                        XSourceInfo.NONE, SMALL), g.info, g.start);

      assertEquals(INCREMENT_DAY, texts(ticks(timeAxis(inc, SMALL))));
   }

   @Test
   void otherTimeAxesKeepTheirHourAndYearLabels() {
      Date[] hours = new Date[8];
      Date[] years = new Date[8];

      for(int i = 0; i < hours.length; i++) {
         hours[i] = time(2, 20 + i, 0);
         years[i] = date(2016 + i, Calendar.JULY, 1);
      }

      assertEquals(TODAYS_HOURS, unfittedLabels(TimeScale.HOUR, "yyyy-MM-dd HH", hours));
      assertEquals(TODAYS_YEARS, unfittedLabels(TimeScale.YEAR, "yyyy-MM-dd", years));
   }

   @Test
   void anAuthorDateFormatKeepsTheClassicAxis() {
      Gantt g = gantt(25, VizMark.MODERN_LIGHT, new Dimension(400, 240), false);
      CompositeTextFormat fmt = new CompositeTextFormat();
      fmt.setFormat(new XFormatInfo("DateFormat", "yyyy"));
      g.start.getAxisDescriptor().setAxisLabelTextFormat(fmt);
      GraphGenerator gen = GraphGenerator.getGenerator(g.info, null, g.gen.getData(),
                                                       new VariableTable(), null,
                                                       XSourceInfo.NONE, new Dimension(400, 240));
      TimeScale time = (TimeScale) ((RectCoord) gen.createEGraph().getCoordinate()).getYScale();

      assertEquals(0, time.getLabelFitPlotPx(), 0);
      assertTrue(time.getAxisSpec().isLabelBetween());
   }

   @Test
   void theFitMeasuresTheLocalesMonthLabels() {
      Font font = new Font("Dialog", Font.PLAIN, 10);
      double english = GanttGraphGenerator.widestMonthLabel(font, Locale.ENGLISH);
      double french = GanttGraphGenerator.widestMonthLabel(font, Locale.FRENCH);

      assertTrue(english >= GTool.stringWidth(new String[] { "Sep 2026" }, font), "" + english);
      assertTrue(french >= GTool.stringWidth(new String[] { "sept. 2026" }, font), "" + french);
      assertTrue(french > english, english + " vs " + french);
   }

   @Test
   void classicGanttsAreUnchanged() {
      Gantt g = gantt(25, null, new Dimension(400, 240), false);
      TimeScale time = (TimeScale) ((RectCoord) g.gen.createEGraph().getCoordinate()).getYScale();

      assertEquals(0, time.getLabelFitPlotPx(), 0);
      assertTrue(time.getAxisSpec().isLabelBetween());
   }

   private record Gantt(GraphGenerator gen, ChartVSAssemblyInfo info, VSChartAggregateRef start) {
   }

   // a tick's date, its shown text (null when hidden), and whether the text is cut
   private record Tick(Date value, String text, boolean cut) {
   }

   private static final Dimension SMALL = new Dimension(400, 240);
   private static final Dimension WIDE = new Dimension(1200, 400);
   // one day, starts at least an hour apart
   private static final Date[][] GANTT_DAY = {
      { time(2, 9, 0), time(2, 10, 30) }, { time(2, 10, 0), time(2, 12, 0) },
      { time(2, 12, 0), time(2, 15, 0) }, { time(2, 15, 0), time(2, 17, 0) },
      { time(2, 17, 0), time(2, 18, 0) } };
   // three days, starts at least an hour apart
   private static final Date[][] GANTT_MULTI = {
      { time(2, 9, 0), time(2, 14, 0) }, { time(2, 10, 0), time(3, 11, 0) },
      { time(3, 8, 0), time(3, 16, 0) }, { time(3, 13, 0), time(4, 10, 0) },
      { time(4, 9, 0), time(4, 17, 0) } };
   // an hour and a half, starts 15 minutes apart
   private static final Date[][] GANTT_MIN = {
      { time(2, 9, 0), time(2, 9, 20) }, { time(2, 9, 15), time(2, 9, 45) },
      { time(2, 9, 30), time(2, 10, 10) }, { time(2, 10, 0), time(2, 10, 30) } };
   // 2016 to 2026
   private static final Date[][] GANTT_YEARS = {
      { date(2016, Calendar.FEBRUARY, 1), date(2018, Calendar.JUNE, 30) },
      { date(2018, Calendar.JANUARY, 15), date(2021, Calendar.MARCH, 31) },
      { date(2021, Calendar.JANUARY, 10), date(2024, Calendar.SEPTEMBER, 30) },
      { date(2024, Calendar.MAY, 1), date(2026, Calendar.NOVEMBER, 30) } };
   // today's labels (null for a hidden one)
   private static final List<String> CLASSIC_DAY = List.of("2026-03-02");
   private static final List<String> CLASSIC_MULTI = List.of("2026-03-02", "03-03", "03-04");
   // the dates of the classic year ticks
   private static final List<String> CLASSIC_YEARS = List.of(
      "2016-01-01", "2017-01-01", "2018-01-01", "2019-01-01", "2020-01-01", "2021-01-01",
      "2022-01-01", "2023-01-01", "2024-01-01", "2025-01-01", "2026-01-01");
   private static final List<String> INCREMENT_DAY = List.of("2026-03-02");
   private static final List<String> TODAYS_HOURS = List.of(
      "2026-03-02 20", "03-02 21", "03-02 22", "03-02 23", "03-03 00", "03-03 01", "03-03 02",
      "03-03 03");
   private static final List<String> TODAYS_YEARS = List.of(
      "2016-01-01", "2017-01-01", "2018-01-01", "2019-01-01", "2020-01-01", "2021-01-01",
      "2022-01-01", "2023-01-01");

   // the time axis, laid out and thinned
   private static DefaultAxis timeAxis(Gantt g, Dimension size) {
      VGraph vgraph = Plotter.getPlotter(g.gen.createEGraph())
         .plotAndLayout(g.gen.getData(), 0, 0, size.width, size.height);
      DefaultAxis axis = ((RectCoord) vgraph.getCoordinate()).getYAxis1();
      axis.prepareLabels();
      return axis;
   }

   private static List<Tick> ticks(DefaultAxis axis) {
      List<Tick> ticks = new ArrayList<>();

      for(VLabel label : axis.getLabels()) {
         String text = label.getText();
         boolean shown = label.getZIndex() >= 0 && text != null && !text.isEmpty();
         String drawn = String.join("\n", label.getDisplayLabel());
         ticks.add(new Tick((Date) ((VDimensionLabel) label).getValue(), shown ? text : null,
                            shown && !drawn.equals(text)));
      }

      return ticks;
   }

   private static List<String> texts(List<Tick> ticks) {
      return ticks.stream().map(Tick::text).toList();
   }

   // the labels of a marked time axis of a set type with no fit, as on a time series
   private static List<String> unfittedLabels(int type, String pattern, Date[] dates) {
      TimeScale scale = new TimeScale("Date");
      scale.setType(type);
      AxisSpec spec = scale.getAxisSpec();
      spec.setCompactDates(new CompactDates(CompactDates.FROM_SCALE, Locale.US));
      spec.setAbbreviate(true);
      spec.getTextSpec().setFormat(new ExtendedDateFormat(pattern));
      Object[][] rows = new Object[dates.length + 1][];
      rows[0] = new Object[] { "Date", "Sales" };

      for(int i = 0; i < dates.length; i++) {
         rows[i + 1] = new Object[] { dates[i], (i + 1) * 10 };
      }

      EGraph graph = new EGraph();
      graph.addElement(new LineElement("Date", "Sales"));
      graph.setCoordinate(new RectCoord(scale, new LinearScale("Sales")));
      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, 800, 240);
      DefaultAxis axis = ((RectCoord) vgraph.getCoordinate()).getXAxis1();
      axis.prepareLabels();
      return texts(ticks(axis));
   }

   private static Gantt gantt(int count, VizMark mark, Dimension size, boolean intraday) {
      Date[][] spans = new Date[count][];

      for(int i = 1; i <= count; i++) {
         spans[i - 1] = intraday ? new Date[] { hour(8 + i), hour(9 + i) }
            : new Date[] { date(2026, Calendar.JANUARY, 1 + i * 3),
                           date(2026, Calendar.JANUARY, 21 + i * 3) };
      }

      return gantt(spans, mark, size);
   }

   private static Gantt gantt(Date[][] spans, VizMark mark, Dimension size) {
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
      Object[][] rows = new Object[spans.length + 1][];
      rows[0] = new Object[] { "Task", GraphUtil.getName(start), GraphUtil.getName(end) };

      for(int i = 1; i <= spans.length; i++) {
         rows[i] = new Object[] { "Workstream task number " + i, spans[i - 1][0], spans[i - 1][1] };
      }

      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(gantt);
      info.setChartDescriptor(new ChartDescriptor());
      info.setVizMark(mark);
      info.setPadding(VSDensityDefaults.chartPadding(VizContext.of(info)));
      GraphGenerator gen = GraphGenerator.getGenerator(info, null, new DefaultDataSet(rows),
                                                       new VariableTable(), null,
                                                       XSourceInfo.NONE, size);
      return new Gantt(gen, info, start);
   }

   private static Date date(int year, int month, int day) {
      Calendar cal = Calendar.getInstance();
      cal.clear();
      cal.set(year, month, day);
      return cal.getTime();
   }

   private static Date hour(int hour) {
      Calendar cal = Calendar.getInstance();
      cal.clear();
      cal.set(2026, Calendar.JANUARY, 5, hour, 0);
      return cal.getTime();
   }

   private static Date time(int marchDay, int hour, int minute) {
      Calendar cal = Calendar.getInstance();
      cal.clear();
      cal.set(2026, Calendar.MARCH, marchDay, hour, minute);
      return cal.getTime();
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
