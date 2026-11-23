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
package inetsoft.graph.guide.axis;

import inetsoft.graph.*;
import inetsoft.graph.coord.FacetCoord;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.TimeScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Axes of a real chart: EGraph, interval element, {@code Plotter.plotAndLayout}, then the axes
 * of the resulting {@link RectCoord} are inspected.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class DefaultAxisChartPathTest {
   private static final double EPS = 1e-6;
   private static final Object[][] ROWS = {
      { "Cat", "m" }, { "A", 40.0 }, { "B", 80.0 }, { "C", 60.0 }
   };

   /**
    * Chart under test: categorical x axis A,B,C with grid lines, linear y axis 0..100 step 25
    * with grid lines.
    */
   private static class Chart {
      CategoricalScale xs = new CategoricalScale("Cat");
      LinearScale ys = new LinearScale("m");
      RectCoord coord = new RectCoord();
      Object[][] rows = ROWS;
      VGraph vg;

      Chart() {
         xs.getAxisSpec().setGridStyle(GraphConstants.THIN_LINE);
         ys.setMin(0);
         ys.setMax(100);
         ys.setIncrement(25);
         ys.getAxisSpec().setGridStyle(GraphConstants.THIN_LINE);
      }

      Chart plot() {
         EGraph graph = new EGraph();
         graph.addElement(new IntervalElement("Cat", "m"));
         coord.setXScale(xs);
         coord.setYScale(ys);
         graph.setCoordinate(coord);
         graph.setScale("Cat", xs);
         graph.setScale("m", ys);
         vg = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 600, 400);
         return this;
      }

      RectCoord coord() {
         return (RectCoord) vg.getCoordinate();
      }

      Rectangle2D plotBounds() {
         return vg.getPlotBounds();
      }
   }

   private static List<Object> values(DefaultAxis axis) {
      List<Object> list = new ArrayList<>();

      for(VLabel label : axis.getLabels()) {
         list.add(label.getValue());
      }

      return list;
   }

   private static List<Object> numbers(DefaultAxis axis) {
      List<Object> list = new ArrayList<>();

      for(VLabel label : axis.getLabels()) {
         list.add(((Number) label.getValue()).doubleValue());
      }

      return list;
   }

   private static List<VLabel> visible(DefaultAxis axis) {
      List<VLabel> list = new ArrayList<>();

      for(VLabel label : axis.getLabels()) {
         if(label.getZIndex() >= 0) {
            list.add(label);
         }
      }

      return list;
   }

   private static List<Line2D> gridLines(DefaultAxis... axes) {
      List<Line2D> lines = new ArrayList<>();

      for(DefaultAxis axis : axes) {
         if(axis == null) {
            continue;
         }

         for(int i = 0; i < axis.getGridLineCount(); i++) {
            GridLine line = axis.getGridLine(i);

            if(line.getZIndex() >= 0 && line.getShape() instanceof Line2D) {
               lines.add((Line2D) line.getShape());
            }
         }
      }

      return lines;
   }

   // ---------------------------------------------------------------- default chart

   @Test
   @DisplayName("default chart: x labels A,B,C, y labels 0..100, axis types x and y")
   void defaultAxes() {
      Chart chart = new Chart().plot();
      RectCoord coord = chart.coord();

      assertEquals(List.of("A", "B", "C"), values(coord.getXAxis1()));
      assertEquals(List.of(0.0, 25.0, 50.0, 75.0, 100.0), numbers(coord.getYAxis1()));
      assertEquals("x", coord.getXAxis1().getAxisType());
      assertEquals("y", coord.getYAxis1().getAxisType());
   }

   @Test
   @DisplayName("default chart: labels lie outside the plot, below and left of it")
   void defaultLabelSides() {
      Chart chart = new Chart().plot();
      Rectangle2D plot = chart.plotBounds();

      for(VLabel label : visible(chart.coord().getXAxis1())) {
         assertTrue(label.getBounds().getMaxY() <= plot.getMinY() + 1,
                    "x label above the plot bottom: " + label.getBounds());
      }

      for(VLabel label : visible(chart.coord().getYAxis1())) {
         assertTrue(label.getBounds().getMaxX() <= plot.getMinX() + 1,
                    "y label right of the plot left edge: " + label.getBounds());
      }
   }

   @Test
   @DisplayName("single y axis without grid lines has no secondary axis")
   void singleAxisNoGrid() {
      Chart chart = new Chart();
      chart.ys.getAxisSpec().setAxisStyle(AxisSpec.AXIS_SINGLE);
      chart.ys.getAxisSpec().setGridStyle(GraphConstants.NONE);
      chart.plot();

      assertNull(chart.coord().getYAxis2());
      assertNotNull(chart.coord().getYAxis1());
   }

   @Test
   @DisplayName("single y axis with grid lines keeps a hidden secondary axis holding the lines")
   void singleAxisWithGrid() {
      Chart chart = new Chart();
      chart.ys.getAxisSpec().setAxisStyle(AxisSpec.AXIS_SINGLE);
      chart.plot();
      DefaultAxis y1 = chart.coord().getYAxis1();
      DefaultAxis y2 = chart.coord().getYAxis2();

      assertNotNull(y2);
      assertTrue(y2.getZIndex() < 0);
      assertTrue(y2.getGridLineCount() > 0);
      assertTrue(y1.getZIndex() >= 0);
   }

   @Test
   @DisplayName("y axis labels on the opposite side: shown on the secondary axis right of the plot")
   void yLabelsOppositeSide() {
      Chart chart = new Chart();
      chart.ys.getAxisSpec().setAxisStyle(AxisSpec.AXIS_SINGLE2);
      chart.plot();
      RectCoord coord = chart.coord();
      Rectangle2D plot = chart.plotBounds();

      assertEquals(List.of(0.0, 25.0, 50.0, 75.0, 100.0), numbers(coord.getYAxis2()));

      for(VLabel label : visible(coord.getYAxis2())) {
         assertTrue(label.getBounds().getMinX() >= plot.getMaxX() - 1, "" + label.getBounds());
      }

      assertTrue(coord.getYAxis1() == null || coord.getYAxis1().getZIndex() < 0);
   }

   @Test
   @DisplayName("x axis labels on the opposite side: shown on the secondary axis above the plot")
   void xLabelsOppositeSide() {
      Chart chart = new Chart();
      chart.xs.getAxisSpec().setAxisStyle(AxisSpec.AXIS_SINGLE2);
      chart.plot();
      RectCoord coord = chart.coord();
      Rectangle2D plot = chart.plotBounds();

      assertEquals(List.of("A", "B", "C"), values(coord.getXAxis2()));

      for(VLabel label : visible(coord.getXAxis2())) {
         assertTrue(label.getBounds().getMinY() >= plot.getMaxY() - 1, "" + label.getBounds());
      }
   }

   @Test
   @DisplayName("double axis with opposite labels: labels on the secondary axis only, both lines shown")
   void doubleAxisOppositeLabels() {
      Chart chart = new Chart();
      chart.ys.getAxisSpec().setAxisStyle(AxisSpec.AXIS_DOUBLE2);
      chart.plot();
      DefaultAxis y1 = chart.coord().getYAxis1();
      DefaultAxis y2 = chart.coord().getYAxis2();

      assertFalse(y1.isLabelVisible());
      assertTrue(y2.isLabelVisible());
      assertTrue(y1.isLineVisible() && y1.getZIndex() >= 0);
      assertTrue(y2.isLineVisible() && y2.getZIndex() >= 0);
   }

   @Test
   @DisplayName("double axis: the secondary axis shares the ticks and shows no labels")
   void secondaryAxisSharesTicks() {
      Chart chart = new Chart().plot();
      DefaultAxis y1 = chart.coord().getYAxis1();
      DefaultAxis y2 = chart.coord().getYAxis2();

      assertArrayEquals(y1.getTicks(), y2.getTicks(), EPS);
      assertTrue(visible(y2).isEmpty());
   }

   @Test
   @DisplayName("a separate secondary scale gives the secondary axis its own ticks")
   void ownSecondaryScale() {
      Chart chart = new Chart();
      LinearScale ys2 = new LinearScale("m");
      ys2.setMin(0);
      ys2.setMax(50);
      ys2.setIncrement(10);
      chart.coord.setYScale2(ys2);
      chart.plot();
      DefaultAxis y1 = chart.coord().getYAxis1();
      DefaultAxis y2 = chart.coord().getYAxis2();

      assertArrayEquals(new double[] { 0, 10, 20, 30, 40, 50 }, y2.getTicks(), EPS);
      assertFalse(Arrays.equals(y1.getTicks(), y2.getTicks()));
   }

   // ---------------------------------------------------------------- grid lines

   @Test
   @DisplayName("y grid lines: equally spaced, ascending, spanning the plot height")
   void yGridLines() {
      Chart chart = new Chart().plot();
      RectCoord coord = chart.coord();
      Rectangle2D plot = chart.plotBounds();
      List<Line2D> lines = gridLines(coord.getYAxis2(), coord.getYAxis1());

      assertTrue(lines.size() >= 3, "lines: " + lines.size());
      double gap = lines.get(1).getY1() - lines.get(0).getY1();

      for(int i = 1; i < lines.size(); i++) {
         double step = lines.get(i).getY1() - lines.get(i - 1).getY1();
         assertTrue(step > 0, "strictly increasing");
         assertEquals(gap, step, 0.5, "equal gaps");
      }

      assertEquals(plot.getHeight(), gap * 4, 2.0);
      assertTrue(lines.get(0).getY1() >= plot.getMinY() - 1);
      assertTrue(lines.get(lines.size() - 1).getY1() <= plot.getMaxY() + 1);
   }

   @Test
   @DisplayName("category grid lines: one vertical line per category center")
   void categoryGridLines() {
      Chart chart = new Chart().plot();
      RectCoord coord = chart.coord();
      Rectangle2D plot = chart.plotBounds();
      List<Line2D> lines = gridLines(coord.getXAxis2(), coord.getXAxis1());

      assertEquals(3, lines.size());

      for(int k = 0; k < 3; k++) {
         double expected = plot.getMinX() + (k + 0.5) * plot.getWidth() / 3;
         assertEquals(expected, lines.get(k).getX1(), 0.5, "line " + k);
      }
   }

   // ---------------------------------------------------------------- orientation

   @Test
   @DisplayName("transposed chart: categories run along the vertical axis, values along the horizontal")
   void transposedChart() {
      Chart chart = new Chart();
      chart.coord.transpose();
      chart.plot();
      RectCoord coord = chart.coord();

      assertEquals(List.of("A", "B", "C"), values(coord.getXAxis1()));
      assertTrue(coord.getXAxis1().isVertical());
      List<VLabel> cats = visible(coord.getXAxis1());
      assertEquals(3, cats.size());
      double step = cats.get(1).getBounds().getCenterY() - cats.get(0).getBounds().getCenterY();
      assertNotEquals(0, step, EPS);

      for(int i = 1; i < cats.size(); i++) {
         double d = cats.get(i).getBounds().getCenterY() - cats.get(i - 1).getBounds().getCenterY();
         assertTrue(d * step > 0, "strictly monotonic category labels");
      }

      assertTrue(coord.getYAxis1().isHorizontal());
      List<VLabel> vals = visible(coord.getYAxis1());

      for(int i = 1; i < vals.size(); i++) {
         assertTrue(vals.get(i).getBounds().getCenterX() >
                       vals.get(i - 1).getBounds().getCenterX(), "x ascending");
      }
   }

   @Test
   @DisplayName("reversed y scale: the larger value is drawn lower than the smaller value")
   void reversedY() {
      Chart chart = new Chart();
      chart.ys = new LinearScale("m");
      chart.ys.setReversed(true);
      chart.plot();
      List<VLabel> labels = visible(chart.coord().getYAxis1());
      assertTrue(labels.size() >= 2);

      VLabel first = labels.get(0);
      VLabel last = labels.get(labels.size() - 1);
      double v1 = ((Number) first.getValue()).doubleValue();
      double v2 = ((Number) last.getValue()).doubleValue();
      VLabel larger = v1 > v2 ? first : last;
      VLabel smaller = v1 > v2 ? last : first;

      assertTrue(larger.getBounds().getCenterY() < smaller.getBounds().getCenterY(),
                 "larger " + larger.getBounds() + " smaller " + smaller.getBounds());
   }

   @Test
   @DisplayName("reversed x categories: labels run C,B,A with ascending x")
   void reversedX() {
      Chart chart = new Chart();
      chart.xs.setReversed(true);
      chart.plot();
      List<VLabel> labels = visible(chart.coord().getXAxis1());

      assertEquals(List.of("C", "B", "A"), values(chart.coord().getXAxis1()));
      assertEquals(3, labels.size());

      for(int i = 1; i < labels.size(); i++) {
         assertTrue(labels.get(i).getBounds().getCenterX() >
                       labels.get(i - 1).getBounds().getCenterX());
      }
   }

   // ---------------------------------------------------------------- scales and data

   @Test
   @DisplayName("time x axis with explicit range: month labels ascend and the first lies in the plot")
   void timeXAxis() {
      TimeScale ts = new TimeScale("t");
      ts.setMin(new GregorianCalendar(2021, 0, 1).getTime());
      ts.setMax(new GregorianCalendar(2021, 3, 1).getTime());
      ts.setType(TimeScale.MONTH);
      ts.getAxisSpec().getTextSpec().setFormat(new SimpleDateFormat("yyyy-MM", Locale.US));
      Object[][] rows = {
         { "t", "m" },
         { new GregorianCalendar(2021, 0, 1).getTime(), 40.0 },
         { new GregorianCalendar(2021, 1, 1).getTime(), 80.0 },
         { new GregorianCalendar(2021, 2, 1).getTime(), 60.0 }
      };
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("t", "m"));
      graph.setScale("t", ts);
      LinearScale ys = new LinearScale("m");
      ys.setMin(0);
      ys.setMax(100);
      graph.setScale("m", ys);
      VGraph vg = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 600, 400);
      DefaultAxis x1 = ((RectCoord) vg.getCoordinate()).getXAxis1();
      List<VLabel> labels = visible(x1);

      assertTrue(labels.size() >= 2);
      assertEquals("2021-01", labels.get(0).getText());

      for(int i = 1; i < labels.size(); i++) {
         assertTrue(labels.get(i).getBounds().getCenterX() >
                       labels.get(i - 1).getBounds().getCenterX());
      }

      double cx = labels.get(0).getBounds().getCenterX();
      assertTrue(cx >= vg.getPlotBounds().getMinX() && cx <= vg.getPlotBounds().getMaxX());
   }

   @Test
   @DisplayName("chart with one category: one label centered inside the plot, no error")
   void oneCategory() {
      Chart chart = new Chart();
      chart.rows = new Object[][] { { "Cat", "m" }, { "A", 40.0 } };
      chart.plot();
      List<VLabel> labels = visible(chart.coord().getXAxis1());
      Rectangle2D plot = chart.plotBounds();

      assertEquals(1, labels.size());
      double cx = labels.get(0).getBounds().getCenterX();
      assertTrue(cx >= plot.getMinX() && cx <= plot.getMaxX());
   }

   @Test
   @DisplayName("chart without data rows: no error and the y axis still has a label")
   void emptyData() {
      Chart chart = new Chart();
      chart.rows = new Object[][] { { "Cat", "m" } };
      chart.plot();

      assertTrue(chart.coord().getYAxis1().getLabels().length >= 1);
   }

   // ---------------------------------------------------------------- sizes

   @Test
   @DisplayName("axis sizes assigned by the coordinate are at least the axis minimum sizes")
   void axisSizeBounds() {
      Chart chart = new Chart().plot();
      DefaultAxis y1 = chart.coord().getYAxis1();
      DefaultAxis x1 = chart.coord().getXAxis1();

      assertTrue(y1.getAxisSize() >= y1.getMinWidth(),
                 "y size " + y1.getAxisSize() + " min " + y1.getMinWidth());
      assertTrue(x1.getAxisSize() >= x1.getMinHeight(),
                 "x size " + x1.getAxisSize() + " min " + x1.getMinHeight());
   }

   @Test
   @DisplayName("the text of all visible axis labels lies inside the graph bounds")
   void axesInsideGraph() {
      Chart chart = new Chart().plot();
      Rectangle2D graph = chart.vg.getBounds();

      for(DefaultAxis axis : new DefaultAxis[] { chart.coord().getXAxis1(),
                                                  chart.coord().getYAxis1() })
      {
         // painting prepares the labels (moves them inside, removes overlaps) before drawing
         axis.prepareLabels();

         for(VLabel label : visible(axis)) {
            Rectangle2D box = label.getTransformedBounds().getBounds2D();
            assertTrue(box.getMinX() >= graph.getMinX() - 1 && box.getMaxX() <= graph.getMaxX() + 1 &&
                       box.getMinY() >= graph.getMinY() - 1 && box.getMaxY() <= graph.getMaxY() + 1,
                       "label text " + box + " outside " + graph);
         }
      }
   }

   @Test
   @DisplayName("facet chart: the outer axis centers its labels in the cells and lists the outer values")
   void facetOuterAxisCentered() {
      Object[][] rows = {
         { "Region", "Cat", "m" },
         { "East", "A", 10.0 }, { "East", "B", 20.0 },
         { "West", "A", 30.0 }, { "West", "B", 40.0 }
      };
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("Region", "Cat", "m"));
      VGraph vg = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 600, 400);
      FacetCoord facet = (FacetCoord) vg.getCoordinate();
      RectCoord outer = (RectCoord) facet.getOuterCoordinate();
      DefaultAxis found = null;

      for(DefaultAxis axis : new DefaultAxis[] { outer.getXAxis1(), outer.getXAxis2() }) {
         if(axis != null && axis.getLabels().length > 0 && axis.getZIndex() >= 0) {
            found = axis;
         }
      }

      assertNotNull(found, "outer axis with labels");
      assertTrue(found.isCenterLabel());
      assertEquals(List.of("East", "West"), values(found));
   }
}
