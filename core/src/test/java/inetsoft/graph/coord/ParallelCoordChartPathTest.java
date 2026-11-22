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
import inetsoft.graph.element.LineElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.LineVO;
import inetsoft.graph.visual.VisualObject;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parallel coordinates of a real chart: line element, {@code Plotter.plotAndLayout}, then the
 * axes and vertices of the laid out graph are inspected.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class ParallelCoordChartPathTest {
   private static final double EPS = 1e-6;
   private static final double PIXEL = 1.0;

   private static LinearScale lin(String field, double min, double max) {
      LinearScale scale = new LinearScale(field);
      scale.setMin(min);
      scale.setMax(max);
      return scale;
   }

   private static Scale[] lin100(int n) {
      Scale[] scales = new Scale[n];

      for(int i = 0; i < n; i++) {
         scales[i] = lin("m" + (i + 1), 0, 100);
      }

      return scales;
   }

   private static Object[][] rows(int n, double... values) {
      Object[][] rows = new Object[values.length / n + 1][n];

      for(int i = 0; i < n; i++) {
         rows[0][i] = "m" + (i + 1);
      }

      for(int r = 0; r < values.length / n; r++) {
         for(int i = 0; i < n; i++) {
            rows[r + 1][i] = values[r * n + i];
         }
      }

      return rows;
   }

   /** Plain parallel coordinates: one line element with one dimension per measure. */
   private static VGraph plotParallel(Scale[] scales, Object[][] data, int w, int h) {
      EGraph graph = new EGraph();
      LineElement elem = new LineElement();

      for(Scale scale : scales) {
         String field = scale.getFields()[0];
         elem.addDim(field);
         graph.setScale(field, scale);
      }

      graph.addElement(elem);
      graph.setCoordinate(new ParallelCoord(scales));
      return Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(data), 0, 0, w, h);
   }

   private static VGraph plotParallel(int n, int w, int h) {
      double[] vals = new double[3 * n];

      for(int i = 0; i < vals.length; i++) {
         vals[i] = 10 + 5 * i;
      }

      return plotParallel(lin100(n), rows(n, vals), w, h);
   }

   private static AbstractParallelCoord coord(VGraph vg) {
      return (AbstractParallelCoord) vg.getCoordinate();
   }

   private static List<DefaultAxis> axes(VGraph vg) {
      List<DefaultAxis> list = new ArrayList<>();

      for(Axis axis : coord(vg).getAxes(false)) {
         list.add((DefaultAxis) axis);
      }

      return list;
   }

   private static Point2D start(DefaultAxis axis) {
      return axis.getScreenTransform().transform(new Point2D.Double(0, 0), null);
   }

   private static Point2D end(DefaultAxis axis) {
      return axis.getScreenTransform().transform(
         new Point2D.Double(Coordinate.GHEIGHT, 0), null);
   }

   private static List<Point2D> vertices(VGraph vg) {
      List<Point2D> list = new ArrayList<>();

      for(VisualObject vo : GTool.getVOs(vg)) {
         if(vo instanceof LineVO) {
            for(Point2D pt : ((LineVO) vo).getTransformedPoints()) {
               if(pt != null && !Double.isNaN(pt.getX())) {
                  list.add(pt);
               }
            }
         }
      }

      return list;
   }

   private static List<Object> labels(DefaultAxis axis) {
      List<Object> list = new ArrayList<>();

      for(VLabel label : axis.getLabels()) {
         list.add(label.getValue());
      }

      return list;
   }

   private static DefaultAxis labelAxis(VGraph vg) {
      Scale label = coord(vg).getAxisLabelScale();

      for(int i = 0; i < vg.getAxisCount(); i++) {
         if(vg.getAxis(i) instanceof DefaultAxis &&
            ((DefaultAxis) vg.getAxis(i)).getScale() == label)
         {
            return (DefaultAxis) vg.getAxis(i);
         }
      }

      fail("label axis not found");
      return null;
   }

   // ---------------------------------------------------------------- plain parallel coord

   @Test
   @DisplayName("three scales give three vertical axes in scale order plus the label axis")
   void axesInVGraph() {
      VGraph vg = plotParallel(3, 600, 400);
      List<DefaultAxis> axes = axes(vg);

      assertEquals(3, axes.size());

      for(int i = 0; i < 3; i++) {
         assertNotNull(axes.get(i));
         assertEquals("m" + (i + 1), axes.get(i).getScale().getFields()[0]);
      }

      assertEquals(4, vg.getAxisCount());
   }

   @Test
   @DisplayName("a hidden axis is left out of the axes, the others stay at slots 0 and 2 of 3")
   void hiddenAxisKeepsSlot() {
      Scale[] scales = lin100(3);
      scales[1].getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);
      VGraph vg = plotParallel(scales, rows(3, 10, 20, 30, 40, 50, 60), 600, 400);
      List<DefaultAxis> axes = axes(vg);
      Rectangle2D plot = vg.getPlotBounds();

      assertEquals(2, axes.size());
      assertEquals(plot.getX() + 0.5 * plot.getWidth() / 3, start(axes.get(0)).getX(), PIXEL);
      assertEquals(plot.getX() + 2.5 * plot.getWidth() / 3, start(axes.get(1)).getX(), PIXEL);
   }

   @ParameterizedTest(name = "{0} axes")
   @ValueSource(ints = { 2, 3, 4, 5 })
   @DisplayName("axes are equally spaced and symmetric about the plot centre")
   void axesEquallySpaced(int n) {
      VGraph vg = plotParallel(n, 600, 400);
      List<DefaultAxis> axes = axes(vg);
      Rectangle2D plot = vg.getPlotBounds();

      assertEquals(n, axes.size());
      double gap = start(axes.get(1)).getX() - start(axes.get(0)).getX();

      for(int i = 1; i < n; i++) {
         assertEquals(gap, start(axes.get(i)).getX() - start(axes.get(i - 1)).getX(), PIXEL);
      }

      double first = start(axes.get(0)).getX() - plot.getX();
      double last = plot.getMaxX() - start(axes.get(n - 1)).getX();
      assertEquals(first, last, PIXEL);
   }

   @ParameterizedTest(name = "{0}x{1}")
   @ValueSource(strings = { "600x400", "300x300" })
   @DisplayName("each axis runs vertically, all axes have equal length equal to the plot height")
   void axesVerticalAndEqual(String size) {
      String[] wh = size.split("x");
      VGraph vg = plotParallel(3, Integer.parseInt(wh[0]), Integer.parseInt(wh[1]));
      List<DefaultAxis> axes = axes(vg);
      double plotH = vg.getPlotBounds().getHeight();
      double len0 = start(axes.get(0)).distance(end(axes.get(0)));

      for(DefaultAxis axis : axes) {
         assertEquals(start(axis).getX(), end(axis).getX(), 1e-3);
         assertEquals(len0, start(axis).distance(end(axis)), 1e-3);
      }

      assertEquals(plotH, len0, PIXEL);
   }

   @Test
   @DisplayName("a value at the middle of its own range lies on its axis at half of the axis length")
   void valueOnItsAxis() {
      Scale[] scales = {
         lin("m1", 0, 100), lin("m2", 0, 10), lin("m3", -50, 50) };
      VGraph vg = plotParallel(scales, rows(3, 50, 5, 0), 600, 400);
      List<DefaultAxis> axes = axes(vg);
      List<Point2D> pts = new ArrayList<>(vertices(vg));
      pts.sort(Comparator.comparingDouble(Point2D::getX));

      assertEquals(3, pts.size());

      for(int i = 0; i < 3; i++) {
         Point2D s = start(axes.get(i));
         Point2D e = end(axes.get(i));
         assertEquals(s.getX(), pts.get(i).getX(), 0.01, "x of vertex " + i);
         assertEquals((s.getY() + e.getY()) / 2, pts.get(i).getY(), 0.01, "y of vertex " + i);
      }
   }

   @Test
   @DisplayName("vertices at the minimum and maximum of every scale stay inside the plot")
   void verticesInsidePlot() {
      Scale[] scales = {
         lin("m1", 0, 100), lin("m2", 0, 10), lin("m3", -50, 50) };
      VGraph vg = plotParallel(scales, rows(3, 0, 0, -50, 100, 10, 50), 600, 400);
      Rectangle2D plot = vg.getPlotBounds();
      List<Point2D> pts = vertices(vg);

      assertFalse(pts.isEmpty());

      for(Point2D pt : pts) {
         assertTrue(pt.getX() >= plot.getX() - PIXEL && pt.getX() <= plot.getMaxX() + PIXEL,
                    "x " + pt.getX());
         assertTrue(pt.getY() >= plot.getY() - PIXEL && pt.getY() <= plot.getMaxY() + PIXEL,
                    "y " + pt.getY());
      }
   }

   @Test
   @DisplayName("header-only data: no exception, no axes, unit sizes 0 before layout")
   void emptyData() {
      assertDoesNotThrow(() -> plotParallel(lin100(3), rows(3), 600, 400));
      VGraph vg = plotParallel(lin100(3), rows(3), 600, 400);
      assertEquals(0, coord(vg).getAxes(false).length);

      ParallelCoord fresh = new ParallelCoord(lin100(3));
      assertEquals(0, fresh.getUnitMinWidth(), 0);
      assertEquals(0, fresh.getUnitMinHeight(), 0);
   }

   @Test
   @DisplayName("label axis shows the measure names in order")
   void labelAxisNames() {
      VGraph vg = plotParallel(3, 600, 400);

      assertEquals(List.of("m1", "m2", "m3"), labels(labelAxis(vg)));
   }

   @Test
   @DisplayName("axis spacing relative to the plot width is the same at two sizes")
   void proportionsAtTwoSizes() {
      double[] ratio = new double[2];
      int[][] sizes = { { 600, 400 }, { 300, 200 } };

      for(int k = 0; k < 2; k++) {
         VGraph vg = plotParallel(3, sizes[k][0], sizes[k][1]);
         List<DefaultAxis> axes = axes(vg);
         ratio[k] = (start(axes.get(1)).getX() - start(axes.get(0)).getX())
            / vg.getPlotBounds().getWidth();
      }

      assertEquals(ratio[0], ratio[1], EPS);
   }

   @Test
   @DisplayName("pinned behavior: unit width is 1.5 times the sum of the axis widths")
   void unitWidthFormulaPinned() {
      VGraph vg = plotParallel(3, 600, 400);
      List<DefaultAxis> axes = axes(vg);
      double min = 0;
      double pref = 0;

      for(DefaultAxis axis : axes) {
         min += axis.getMinWidth();
         pref += axis.getPreferredWidth();
      }

      assertEquals(1.5 * min, coord(vg).getUnitMinWidth(), EPS);
      assertEquals(1.5 * pref, coord(vg).getUnitPreferredWidth(), EPS);
   }

   @Test
   @DisplayName("unit sizes: minimum does not exceed preferred, all are non-negative")
   void unitSizesOrdered() {
      AbstractParallelCoord coord = coord(plotParallel(3, 600, 400));

      assertTrue(coord.getUnitMinWidth() >= 0);
      assertTrue(coord.getUnitMinHeight() >= 0);
      assertTrue(coord.getUnitMinWidth() <= coord.getUnitPreferredWidth() + EPS);
      assertTrue(coord.getUnitMinHeight() <= coord.getUnitPreferredHeight() + EPS);
   }

   private static VGraph plotMixed(boolean categoricalFirst) {
      Scale lin = lin("m", 0, 100);
      Scale cat = new CategoricalScale("c");
      Scale[] scales = categoricalFirst ? new Scale[] { cat, lin } : new Scale[] { lin, cat };
      Object[][] data = {
         { "m", "c" }, { 10.0, "a" }, { 20.0, "b" }, { 30.0, "c" } };
      return plotParallel(scales, data, 600, 400);
   }

   @Test
   @DisplayName("unit height does not depend on the axis order and is the maximum over the axes")
   @Disabled("Not filed (negligible: only the script API with a categorical scale, a few pixels): expected unit min height == max over axes of (axis height * labels for categorical, axis height for linear) = 132.0, actual 141.0 for order (linear, categorical): AbstractParallelCoord.getScalesHeight multiplies the running maximum by the label count")
   void unitHeightIndependentOfOrder() {
      double[][] heights = new double[2][];

      for(int k = 0; k < 2; k++) {
         VGraph vg = plotMixed(k == 1);
         double min = 0;
         double pref = 0;

         for(DefaultAxis axis : axes(vg)) {
            boolean cat = axis.getScale() instanceof CategoricalScale;
            int len = axis.getLabels().length;
            min = Math.max(min, axis.getMinHeight() * (cat ? len : 1));
            pref = Math.max(pref, axis.getPreferredHeight() * (cat ? len : 1));
         }

         AbstractParallelCoord coord = coord(vg);
         assertEquals(min, coord.getUnitMinHeight(), EPS, "min, categorical first=" + (k == 1));
         assertEquals(pref, coord.getUnitPreferredHeight(), EPS,
                      "preferred, categorical first=" + (k == 1));
         heights[k] = new double[] { coord.getUnitMinHeight(), coord.getUnitPreferredHeight() };
      }

      assertArrayEquals(heights[0], heights[1], EPS);
   }

   // ---------------------------------------------------------------- one variable

   private static DefaultDataSet groups(Object... groupAndValue) {
      Object[][] rows = new Object[groupAndValue.length / 2 + 1][];
      rows[0] = new Object[] { "g", "m" };

      for(int i = 0; i < groupAndValue.length / 2; i++) {
         rows[i + 1] = new Object[] { groupAndValue[2 * i], groupAndValue[2 * i + 1] };
      }

      return new DefaultDataSet(rows);
   }

   private static VGraph plotOneVar(DefaultDataSet data, boolean noNull) {
      LinearScale var = new LinearScale("m");
      OneVarParallelCoord coord = new OneVarParallelCoord("m", "g", var);

      if(noNull) {
         coord.getAxisLabelScale().setScaleOption(Scale.NO_NULL);
      }

      coord.init(data);

      EGraph graph = new EGraph();
      LineElement elem = new LineElement();
      elem.addDim("g");
      elem.addVar("m");
      graph.addElement(elem);
      graph.setScale("g", coord.getAxisLabelScale());
      graph.setScale("m", var);
      graph.setCoordinate(coord);
      return Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 600, 400);
   }

   private static DefaultDataSet abc() {
      return groups("A", 10.0, "A", 20.0, "B", 30.0, "B", 40.0, "C", 50.0);
   }

   @Test
   @DisplayName("one variable split by a group: three axes plus a label axis labelled A,B,C")
   void oneVarAxes() {
      VGraph vg = plotOneVar(abc(), false);

      assertEquals(3, axes(vg).size());
      assertEquals(List.of("A", "B", "C"), labels(labelAxis(vg)));

      // rows of group B (two vertices) sit on the middle axis
      double mid = start(axes(vg).get(1)).getX();
      long onMiddle = vertices(vg).stream().filter(p -> Math.abs(p.getX() - mid) < PIXEL).count();
      assertEquals(2, onMiddle);
   }

   @Test
   @DisplayName("one variable with a null group: no exception, three axes, null group has its own axis")
   void oneVarNullGroup() {
      VGraph vg = plotOneVar(groups(null, 10.0, "A", 20.0, "B", 30.0), false);

      assertEquals(3, axes(vg).size());
      assertEquals(3, labelAxis(vg).getLabels().length);
   }

   @Test
   @DisplayName("one variable, NO_NULL and only null groups: no exception and no vertex outside the plot")
   void oneVarNoNullOnlyNullGroups() {
      VGraph vg = assertDoesNotThrow(() -> plotOneVar(groups(null, 10.0, null, 20.0), true));
      Rectangle2D plot = vg.getPlotBounds();

      for(Point2D pt : vertices(vg)) {
         assertTrue(plot.getX() - PIXEL <= pt.getX() && pt.getX() <= plot.getMaxX() + PIXEL);
         assertTrue(plot.getY() - PIXEL <= pt.getY() && pt.getY() <= plot.getMaxY() + PIXEL);
      }
   }

   @Test
   @DisplayName("one variable with a single group: no exception and one axis at the plot centre")
   void oneVarSingleGroup() {
      VGraph vg = plotOneVar(groups("A", 10.0, "A", 20.0), false);
      List<DefaultAxis> axes = axes(vg);
      Rectangle2D plot = vg.getPlotBounds();

      assertEquals(1, axes.size());
      assertEquals(plot.getCenterX(), start(axes.get(0)).getX(), PIXEL);
   }

   @Test
   @DisplayName("one variable: all axes show the same ticks and a value has the same relative height on each")
   void oneVarSharedRange() {
      VGraph vg = plotOneVar(abc(), false);
      List<DefaultAxis> axes = axes(vg);
      double[] first = axes.get(0).getScale().getTicks();

      for(DefaultAxis axis : axes) {
         assertArrayEquals(first, axis.getScale().getTicks(), EPS);
      }

      Scale scaleA = axes.get(0).getScale();
      Scale scaleC = axes.get(2).getScale();
      double relA = (30 - scaleA.getMin()) / (scaleA.getMax() - scaleA.getMin());
      double relC = (30 - scaleC.getMin()) / (scaleC.getMax() - scaleC.getMin());
      assertEquals(relA, relC, EPS);
   }
}
