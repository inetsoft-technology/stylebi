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
package inetsoft.graph.coord;

import inetsoft.graph.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.MekkoElement;
import inetsoft.graph.geometry.MekkoGeometry;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.internal.ICoordinate;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.MekkoVO;
import inetsoft.graph.visual.VisualObject;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Rectangle2D;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Marimekko chart: {@link MekkoElement} and {@link MekkoCoord} plotted with
 * {@code Plotter.plotAndLayout}; the visual objects, axes and plot bounds are inspected.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class MekkoCoordChartPathTest {
   private static final double EPS = 1e-6;

   /** Rows (group, stack, value) of the standard chart: East 40, West 60, North 100. */
   private static final Object[] STANDARD = {
      "East", "x", 30.0, "East", "y", 10.0, "West", "x", 20.0, "West", "y", 40.0,
      "North", "x", 60.0, "North", "y", 40.0 };

   private static class Chart {
      MekkoCoord coord = new MekkoCoord("R", "S");
      MekkoElement element = new MekkoElement("R", "P", "S");
      Object[] triples;
      VGraph vg;

      Chart(Object... triples) {
         this.triples = triples;
      }

      DefaultDataSet data() {
         Object[][] rows = new Object[triples.length / 3 + 1][];
         rows[0] = new Object[] { "R", "P", "S" };

         for(int i = 0; i < triples.length / 3; i++) {
            rows[i + 1] = new Object[] { triples[3 * i], triples[3 * i + 1], triples[3 * i + 2] };
         }

         return new DefaultDataSet(rows);
      }

      Chart plot() {
         return plot(600, 400);
      }

      Chart plot(int w, int h) {
         return plotGraph(prepare(new EGraph()), w, h);
      }

      EGraph prepare(EGraph graph) {
         graph.setScale("R", coord.getScales()[0]);
         graph.addElement(element);
         graph.setCoordinate(coord);
         return graph;
      }

      Chart plotGraph(EGraph graph, int w, int h) {
         vg = Plotter.getPlotter(graph).plotAndLayout(data(), 0, 0, w, h);
         return this;
      }

      Rectangle2D plot0() {
         return vg.getPlotBounds();
      }

      MekkoCoord coord() {
         return (MekkoCoord) vg.getCoordinate();
      }

      /** Bounds of every visual object keyed by the index of its data row. */
      Map<Integer, Rectangle2D> boxes() {
         return boxesOf(vg);
      }
   }

   private static Map<Integer, Rectangle2D> boxesOf(VGraph vg) {
      Map<Integer, Rectangle2D> map = new TreeMap<>();

      for(VisualObject vo : GTool.getVOs(vg)) {
         if(vo instanceof MekkoVO) {
            int row = ((MekkoGeometry) ((MekkoVO) vo).getGeometry()).getSubRowIndex();
            map.put(row, ((MekkoVO) vo).getBounds());
         }
      }

      return map;
   }

   /** Rows 0,1 belong to the first group, 2,3 to the second, 4,5 to the third. */
   private static List<Integer> rowsOf(int group) {
      return List.of(2 * group, 2 * group + 1);
   }

   private static double widthRatio(Chart chart, int row) {
      return chart.boxes().get(row).getWidth() / chart.plot0().getWidth();
   }

   // ---------------------------------------------------------------- widths and stacks

   @Test
   @DisplayName("group widths are proportional to the group totals and add up to the plot width")
   void groupWidthsProportional() {
      Chart chart = new Chart(STANDARD).plot();
      double[] expected = { 0.2, 0.3, 0.5 };
      double sum = 0;

      for(int g = 0; g < 3; g++) {
         double ratio = widthRatio(chart, 2 * g);
         assertEquals(expected[g], ratio, EPS, "group " + g);
         assertEquals(ratio, widthRatio(chart, 2 * g + 1), EPS);
         sum += ratio * chart.plot0().getWidth();
      }

      assertEquals(chart.plot0().getWidth(), sum, EPS);
   }

   @Test
   @DisplayName("groups are contiguous from the plot left edge to the plot right edge")
   void groupsContiguous() {
      Chart chart = new Chart(STANDARD).plot();
      Rectangle2D plot = chart.plot0();
      List<Rectangle2D> groups = new ArrayList<>();

      for(int g = 0; g < 3; g++) {
         groups.add(chart.boxes().get(2 * g));
      }

      groups.sort(Comparator.comparingDouble(Rectangle2D::getX));
      assertEquals(plot.getX(), groups.get(0).getX(), EPS);

      for(int i = 1; i < 3; i++) {
         assertEquals(groups.get(i - 1).getMaxX(), groups.get(i).getX(), EPS);
      }

      assertEquals(plot.getMaxX(), groups.get(2).getMaxX(), EPS);
   }

   @Test
   @DisplayName("stacked segments fill the plot height with heights value/total and no gaps")
   void stacksFillHeight() {
      Chart chart = new Chart(STANDARD).plot();
      double plotH = chart.plot0().getHeight();
      double[] fractions = { 30.0 / 40, 10.0 / 40, 20.0 / 60, 40.0 / 60, 60.0 / 100, 40.0 / 100 };

      for(int row = 0; row < 6; row++) {
         assertEquals(fractions[row], chart.boxes().get(row).getHeight() / plotH, EPS,
                      "row " + row);
      }

      for(int g = 0; g < 3; g++) {
         List<Rectangle2D> segs = new ArrayList<>();

         for(int row : rowsOf(g)) {
            segs.add(chart.boxes().get(row));
         }

         segs.sort(Comparator.comparingDouble(Rectangle2D::getY));
         assertEquals(chart.plot0().getY(), segs.get(0).getY(), EPS);
         assertEquals(segs.get(0).getMaxY(), segs.get(1).getY(), EPS);
         assertEquals(chart.plot0().getMaxY(), segs.get(1).getMaxY(), EPS);
      }
   }

   @Test
   @DisplayName("relative widths and heights of every visual object are the same at two sizes")
   void sameAtTwoSizes() {
      Chart big = new Chart(STANDARD).plot(600, 400);
      Chart small = new Chart(STANDARD).plot(300, 200);

      for(int row = 0; row < 6; row++) {
         Rectangle2D b = big.boxes().get(row);
         Rectangle2D s = small.boxes().get(row);
         assertEquals(b.getWidth() / big.plot0().getWidth(),
                      s.getWidth() / small.plot0().getWidth(), EPS, "width " + row);
         assertEquals(b.getHeight() / big.plot0().getHeight(),
                      s.getHeight() / small.plot0().getHeight(), EPS, "height " + row);
      }
   }

   @Test
   @DisplayName("a negative value counts by absolute value: widths unchanged, heights 0.75 and 0.25")
   void negativeValueUsesAbsolute() {
      Chart chart = new Chart("East", "x", 30.0, "East", "y", -10.0, "West", "x", 20.0,
                              "West", "y", 40.0, "North", "x", 60.0, "North", "y", 40.0).plot();

      assertEquals(0.2, widthRatio(chart, 0), EPS);
      assertEquals(0.75, chart.boxes().get(0).getHeight() / chart.plot0().getHeight(), EPS);
      assertEquals(0.25, chart.boxes().get(1).getHeight() / chart.plot0().getHeight(), EPS);
   }

   @Test
   @DisplayName("a null measure draws nothing; the group width comes from the numeric rows")
   void nullMeasureValue() {
      Chart chart = new Chart("East", "x", 30.0, "East", "y", null, "West", "x", 20.0,
                              "West", "y", 40.0, "North", "x", 60.0, "North", "y", 40.0);
      assertDoesNotThrow(() -> chart.plot());

      Map<Integer, Rectangle2D> boxes = chart.boxes();
      assertFalse(boxes.containsKey(1));
      assertEquals(30.0 / 190, widthRatio(chart, 0), EPS);
      assertEquals(60.0 / 190, widthRatio(chart, 2), EPS);
      assertEquals(100.0 / 190, widthRatio(chart, 4), EPS);
   }

   @Test
   @DisplayName("all measures zero: no exception and no NaN or infinite bounds")
   void allMeasuresZero() {
      Chart chart = new Chart("East", "x", 0.0, "West", "x", 0.0, "North", "x", 0.0);
      assertDoesNotThrow(() -> chart.plot());

      for(Rectangle2D box : chart.boxes().values()) {
         assertTrue(Double.isFinite(box.getX()) && Double.isFinite(box.getY()) &&
                       Double.isFinite(box.getWidth()) && Double.isFinite(box.getHeight()),
                    "bounds " + box);
      }
   }

   @Test
   @DisplayName("a single row covers the whole plot")
   void singleRow() {
      Chart chart = new Chart("East", "x", 30.0).plot();
      Rectangle2D plot = chart.plot0();

      assertEquals(1, chart.boxes().size());
      Rectangle2D box = chart.boxes().get(0);
      assertEquals(plot.getWidth(), box.getWidth(), EPS);
      assertEquals(plot.getHeight(), box.getHeight(), EPS);
   }

   @Test
   @DisplayName("header-only data: no exception, no visual object, finite plot bounds")
   void emptyData() {
      Chart chart = new Chart();
      assertDoesNotThrow(() -> chart.plot());

      assertTrue(chart.boxes().isEmpty());
      Rectangle2D plot = chart.plot0();
      assertFalse(Double.isNaN(plot.getX()) || Double.isNaN(plot.getY()) ||
                     Double.isNaN(plot.getWidth()) || Double.isNaN(plot.getHeight()));
   }

   @Test
   @DisplayName("dimension dropped (var only coord and element without dimension): no exception, blank plot")
   void dimensionDropped() {
      Chart chart = new Chart(STANDARD);
      chart.coord = new MekkoCoord();
      chart.coord.setVar("S");
      chart.element = new MekkoElement();
      chart.element.setInnerDimension("P");
      chart.element.addVar("S");

      assertDoesNotThrow(() -> chart.plot());
      assertTrue(chart.boxes().isEmpty());
   }

   @Test
   @DisplayName("measure dropped (dim only coord and element without measure): no exception, blank plot")
   void measureDropped() {
      Chart chart = new Chart(STANDARD);
      chart.coord = new MekkoCoord();
      chart.coord.setDim("R");
      chart.element = new MekkoElement();
      chart.element.addDim("R");
      chart.element.setInnerDimension("P");

      assertDoesNotThrow(() -> chart.plot());
      assertTrue(chart.boxes().isEmpty());
   }

   // ---------------------------------------------------------------- axes

   private static List<Object> labels(Axis axis) {
      List<Object> list = new ArrayList<>();

      for(VLabel label : ((DefaultAxis) axis).getLabels()) {
         list.add(label.getValue());
      }

      return list;
   }

   @Test
   @DisplayName("axes of the chart: bottom x, top x2 and left y are returned by getAxisAt, right is null")
   void axesOfChart() {
      Chart chart = new Chart(STANDARD).plot();
      MekkoCoord coord = chart.coord();
      Axis[] axes = coord.getAxes(false);

      assertEquals(3, axes.length);
      assertSame(axes[0], coord.getAxisAt(ICoordinate.BOTTOM_AXIS));
      assertSame(axes[1], coord.getAxisAt(ICoordinate.TOP_AXIS));
      assertSame(axes[2], coord.getAxisAt(ICoordinate.LEFT_AXIS));
      assertNull(coord.getAxisAt(ICoordinate.RIGHT_AXIS));
   }

   @Test
   @DisplayName("axis labels: groups at the bottom, group totals on top, 0%..100% on the left")
   void axisLabels() {
      Chart chart = new Chart(STANDARD).plot();
      MekkoCoord coord = chart.coord();
      Map<String, Double> totals = Map.of("East", 40.0, "West", 60.0, "North", 100.0);
      Object[] groups = ((CategoricalScale) coord.getScales()[0]).getValues();
      List<Object> expectedTop = new ArrayList<>();

      for(Object group : groups) {
         expectedTop.add(totals.get(group));
      }

      assertEquals(Arrays.asList(groups), labels(coord.getAxisAt(ICoordinate.BOTTOM_AXIS)));
      assertEquals(expectedTop, labels(coord.getAxisAt(ICoordinate.TOP_AXIS)));

      List<Object> left = labels(coord.getAxisAt(ICoordinate.LEFT_AXIS));
      assertEquals(6, left.size());

      for(int i = 0; i < 6; i++) {
         assertEquals(0.2 * i, ((Number) left.get(i)).doubleValue(), EPS);
      }
   }

   @Test
   @DisplayName("top axis ticks coincide with the bottom axis ticks, one per group")
   void topTicksAlignWithBottom() {
      Chart chart = new Chart(STANDARD).plot();
      double[] bottom = chart.coord().getAxisAt(ICoordinate.BOTTOM_AXIS).getTicks();
      double[] top = chart.coord().getAxisAt(ICoordinate.TOP_AXIS).getTicks();

      assertEquals(3, bottom.length);
      assertArrayEquals(bottom, top, EPS);
   }

   private static double overlap(Rectangle2D a, Rectangle2D b) {
      Rectangle2D inter = a.createIntersection(b);
      return inter.getWidth() > 0 && inter.getHeight() > 0 ? inter.getWidth() * inter.getHeight() : 0;
   }

   @Test
   @DisplayName("plot and axes lie inside the graph at two sizes and the axes do not overlap the plot")
   void layoutInsideGraph() {
      int[][] sizes = { { 600, 400 }, { 300, 200 } };

      for(int[] size : sizes) {
         Chart chart = new Chart(STANDARD).plot(size[0], size[1]);
         Rectangle2D graph = new Rectangle2D.Double(0, 0, size[0], size[1]);
         Rectangle2D plot = chart.plot0();

         assertTrue(plot.getWidth() > 0 && plot.getHeight() > 0);
         assertTrue(graph.contains(plot.getX() + EPS, plot.getY() + EPS,
                                   plot.getWidth() - 2 * EPS, plot.getHeight() - 2 * EPS));

         for(Axis axis : chart.coord().getAxes(false)) {
            Rectangle2D bounds = axis.getBounds();
            assertTrue(graph.contains(bounds.getX() + EPS, bounds.getY() + EPS,
                                      bounds.getWidth() - 2 * EPS, bounds.getHeight() - 2 * EPS),
                       "axis bounds " + bounds + " in " + graph);
            assertEquals(0, overlap(bounds, plot), 1e-3, "axis overlaps plot");
         }
      }
   }

   @Test
   @DisplayName("NO_NULL: the row with a null group draws nothing and the other groups fill the width")
   void noNullInChart() {
      Chart chart = new Chart("East", "x", 30.0, "East", "y", 10.0, "West", "x", 20.0,
                              "West", "y", 40.0, "North", "x", 60.0, "North", "y", 40.0,
                              null, "x", 50.0);
      chart.coord.getScales()[0].setScaleOption(Scale.NO_NULL);
      chart.plot();

      Map<Integer, Rectangle2D> boxes = chart.boxes();
      assertFalse(boxes.containsKey(6));
      double sum = 0;

      for(int g = 0; g < 3; g++) {
         sum += boxes.get(2 * g).getWidth();
      }

      assertEquals(chart.plot0().getWidth(), sum, EPS);
   }

   @Test
   @DisplayName("equal totals give widths 0.25, 0.25, 0.5, contiguous groups and top labels 50, 50, 100")
   void equalTotals() {
      Chart chart = new Chart("East", "x", 30.0, "East", "y", 20.0, "West", "x", 25.0,
                              "West", "y", 25.0, "North", "x", 60.0, "North", "y", 40.0).plot();

      assertEquals(0.25, widthRatio(chart, 0), EPS);
      assertEquals(0.25, widthRatio(chart, 2), EPS);
      assertEquals(0.5, widthRatio(chart, 4), EPS);

      List<Object> top = new ArrayList<>();
      Map<String, Double> totals = Map.of("East", 50.0, "West", 50.0, "North", 100.0);

      for(Object group : ((CategoricalScale) chart.coord().getScales()[0]).getValues()) {
         top.add(totals.get(group));
      }

      assertEquals(top, labels(chart.coord().getAxisAt(ICoordinate.TOP_AXIS)));
   }

   @Test
   @DisplayName("hiding the top axis labels enlarges the plot height and keeps the group widths")
   void hiddenTopLabels() {
      Chart visible = new Chart(STANDARD).plot();
      Chart hidden = new Chart(STANDARD);
      hidden.coord.getX2AxisSpec().setLabelVisible(false);
      assertDoesNotThrow(() -> hidden.plot());

      assertTrue(hidden.plot0().getHeight() > visible.plot0().getHeight());
      assertEquals(visible.plot0().getWidth(), hidden.plot0().getWidth(), EPS);

      for(int g = 0; g < 3; g++) {
         assertEquals(widthRatio(visible, 2 * g), widthRatio(hidden, 2 * g), EPS);
      }
   }

   @Test
   @DisplayName("hiding the bottom axis labels enlarges the plot height and keeps the group widths")
   void hiddenBottomLabels() {
      Chart visible = new Chart(STANDARD).plot();
      Chart hidden = new Chart(STANDARD);
      hidden.coord.getXAxisSpec().setLabelVisible(false);
      hidden.plot();

      assertTrue(hidden.plot0().getHeight() > visible.plot0().getHeight());

      for(int g = 0; g < 3; g++) {
         assertEquals(widthRatio(visible, 2 * g), widthRatio(hidden, 2 * g), EPS);
      }
   }

   @Test
   @DisplayName("thirty groups of weight 1..30: widths proportional, contiguous, sum to the plot width")
   void manyGroups() {
      Object[] triples = new Object[30 * 3];

      for(int i = 0; i < 30; i++) {
         triples[3 * i] = String.format("G%02d", i + 1);
         triples[3 * i + 1] = "x";
         triples[3 * i + 2] = (double) (i + 1);
      }

      Chart chart = new Chart(triples).plot();
      Map<Integer, Rectangle2D> boxes = chart.boxes();
      double total = 30 * 31 / 2.0;
      double sum = 0;

      assertEquals(30, boxes.size());

      for(int i = 0; i < 30; i++) {
         assertEquals((i + 1) / total, widthRatio(chart, i), EPS, "group " + i);
         sum += boxes.get(i).getWidth();
      }

      assertEquals(chart.plot0().getWidth(), sum, EPS);

      for(int i = 1; i < 30; i++) {
         assertEquals(boxes.get(i - 1).getMaxX(), boxes.get(i).getX(), EPS);
      }
   }

   @Test
   @DisplayName("two clones of one unplotted graph plotted separately get the same axes and widths")
   void plotAgainAfterClone() {
      Chart chart = new Chart(STANDARD);
      EGraph graph = chart.prepare(new EGraph());
      EGraph c1 = (EGraph) graph.clone();
      EGraph c2 = (EGraph) graph.clone();
      VGraph v1 = Plotter.getPlotter(c1).plotAndLayout(chart.data(), 0, 0, 600, 400);
      VGraph v2 = Plotter.getPlotter(c2).plotAndLayout(chart.data(), 0, 0, 600, 400);

      Map<Integer, Rectangle2D> a = boxesOf(v1);
      Map<Integer, Rectangle2D> b = boxesOf(v2);

      assertEquals(3, v1.getCoordinate().getAxes(false).length);
      assertEquals(3, v2.getCoordinate().getAxes(false).length);
      assertEquals(6, a.size());
      assertEquals(a.keySet(), b.keySet());

      for(int row : a.keySet()) {
         assertEquals(a.get(row).getWidth() / v1.getPlotBounds().getWidth(),
                      b.get(row).getWidth() / v2.getPlotBounds().getWidth(), EPS);
      }
   }

   @Test
   @DisplayName("all measures zero: one visual object per data row, every bounds finite")
   void allMeasuresZeroDrawsOneVoPerRow() {
      Chart chart = new Chart("East", "x", 0.0, "West", "x", 0.0, "North", "x", 0.0).plot();
      Map<Integer, Rectangle2D> boxes = chart.boxes();

      assertEquals(3, boxes.size());

      for(Rectangle2D box : boxes.values()) {
         assertTrue(Double.isFinite(box.getX()) && Double.isFinite(box.getY()) &&
                       Double.isFinite(box.getWidth()) && Double.isFinite(box.getHeight()),
                    "bounds " + box);
      }
   }
}
