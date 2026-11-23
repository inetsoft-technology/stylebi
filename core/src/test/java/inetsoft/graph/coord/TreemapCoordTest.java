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

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.TreemapSizeFrame;
import inetsoft.graph.coord.TreemapCoord.Layout;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.TreemapElement;
import inetsoft.graph.visual.TreemapVO;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mapping of the treemap layout square (0,0)-(1000,1000) to the plot area.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class TreemapCoordTest {
   private static final double EPS = 1e-6;

   private static class Fit {
      final TreemapCoord coord = new TreemapCoord();
      final VGraph vg = new VGraph(coord);

      Fit(Layout layout, int padding, double x, double y, double w, double h) {
         if(layout != null) {
            coord.setLayout(layout);
         }

         coord.setPadding(padding);
         coord.fit(x, y, w, h);
      }

      Point2D m(double a, double b) {
         return vg.getScreenTransform().transform(new Point2D.Double(a, b), null);
      }
   }

   private static void assertPoint(double x, double y, Point2D p) {
      assertEquals(x, p.getX(), EPS, "x of " + p);
      assertEquals(y, p.getY(), EPS, "y of " + p);
   }

   private static void assertBounds(double x, double y, double w, double h, Rectangle2D r) {
      assertEquals(x, r.getX(), EPS, "x of " + r);
      assertEquals(y, r.getY(), EPS, "y of " + r);
      assertEquals(w, r.getWidth(), EPS, "width of " + r);
      assertEquals(h, r.getHeight(), EPS, "height of " + r);
   }

   @Test
   @DisplayName("fill: the square is stretched over the whole plot")
   void fill() {
      Fit f = new Fit(Layout.FILL, 0, 0, 0, 300, 200);
      assertBounds(0, 0, 299, 199, f.vg.getPlotBounds());
      assertPoint(0, 0, f.m(0, 0));
      assertPoint(299, 199, f.m(1000, 1000));
      assertPoint(149.5, 99.5, f.m(500, 500));
   }

   @Test
   @DisplayName("fill at an offset moves the plot and the mapping")
   void fillOffset() {
      Fit f = new Fit(Layout.FILL, 0, 10, 20, 300, 200);
      assertBounds(10, 20, 299, 199, f.vg.getPlotBounds());
      assertPoint(10, 20, f.m(0, 0));
      assertPoint(309, 219, f.m(1000, 1000));
   }

   @Test
   @DisplayName("fill with padding shrinks the mapping but not the plot bounds")
   void fillPadding() {
      Fit f = new Fit(Layout.FILL, 4, 0, 0, 300, 200);
      assertBounds(0, 0, 299, 199, f.vg.getPlotBounds());
      assertPoint(2, 2, f.m(0, 0));
      assertPoint(297, 197, f.m(1000, 1000));
   }

   @Test
   @DisplayName("square in a landscape area: a centred square of the plot height")
   void squareLandscape() {
      Fit f = new Fit(Layout.SQUARE, 0, 0, 0, 300, 200);
      assertPoint(50, 0, f.m(0, 0));
      assertPoint(249, 199, f.m(1000, 1000));
   }

   @Test
   @DisplayName("square in a portrait area: a centred square of the plot width")
   void squarePortrait() {
      Fit f = new Fit(Layout.SQUARE, 0, 0, 0, 200, 300);
      assertPoint(0, 50, f.m(0, 0));
      assertPoint(199, 249, f.m(1000, 1000));
   }

   @Test
   @DisplayName("square with padding")
   void squarePadding() {
      Fit f = new Fit(Layout.SQUARE, 4, 0, 0, 300, 200);
      assertPoint(52, 2, f.m(0, 0));
      assertPoint(247, 197, f.m(1000, 1000));
   }

   @Test
   @DisplayName("fill-x in a landscape area stretches like fill")
   void fillXLandscape() {
      Fit f = new Fit(Layout.FILL_X, 0, 0, 0, 300, 200);
      assertPoint(0, 0, f.m(0, 0));
      assertPoint(299, 199, f.m(1000, 1000));
   }

   @Test
   @DisplayName("fill-x in a portrait area fills the width")
   void fillXPortrait() {
      Fit f = new Fit(Layout.FILL_X, 0, 0, 0, 200, 300);
      assertEquals(0, f.m(0, 0).getX(), EPS);
      assertEquals(199, f.m(1000, 1000).getX(), EPS);
      assertPoint(99.5, 149.5, f.m(500, 500));
   }

   @Test
   @DisplayName("the default layout is fill")
   void defaultLayout() {
      Fit f = new Fit(null, 0, 0, 0, 300, 200);
      assertPoint(0, 0, f.m(0, 0));
      assertPoint(299, 199, f.m(1000, 1000));
   }

   static Stream<Arguments> combinations() {
      List<Arguments> list = new ArrayList<>();

      for(Layout layout : new Layout[] { Layout.SQUARE, Layout.FILL_X, Layout.FILL }) {
         for(double[] area : new double[][] { { 300, 200 }, { 200, 300 }, { 250, 250 } }) {
            for(int padding : new int[] { 0, 1, 4 }) {
               list.add(Arguments.of(layout, area[0], area[1], padding));
            }
         }
      }

      return list.stream();
   }

   @ParameterizedTest
   @MethodSource("combinations")
   @DisplayName("the centre of the square maps to the centre of the plot")
   void centreMapsToCentre(Layout layout, double w, double h, int padding) {
      Fit f = new Fit(layout, padding, 5, 7, w, h);
      assertPoint(5 + (w - 1) / 2, 7 + (h - 1) / 2, f.m(500, 500));
   }

   @ParameterizedTest
   @MethodSource("combinations")
   @DisplayName("the mapped square stays inside the plot box")
   void mappedSquareInsidePlot(Layout layout, double w, double h, int padding) {
      Fit f = new Fit(layout, padding, 5, 7, w, h);
      Rectangle2D box = f.vg.getPlotBounds();
      Point2D a = f.m(0, 0);
      Point2D b = f.m(1000, 1000);

      for(Point2D p : new Point2D[] { a, b }) {
         assertTrue(p.getX() >= box.getX() - EPS && p.getX() <= box.getMaxX() + EPS &&
                    p.getY() >= box.getY() - EPS && p.getY() <= box.getMaxY() + EPS,
                    p + " outside " + box);
      }
   }

   @ParameterizedTest
   @org.junit.jupiter.params.provider.CsvSource({ "0,0", "0,50" })
   @DisplayName("a zero sized area does not throw and gives a finite transform")
   void zeroSize(double w, double h) {
      Fit f = assertDoesNotThrow(() -> new Fit(Layout.FILL, 0, 0, 0, w, h));
      assertFinite(f.vg.getScreenTransform());
   }

   @ParameterizedTest
   @org.junit.jupiter.params.provider.EnumSource(value = Layout.class,
                                                 names = { "FILL", "SQUARE" })
   @DisplayName("a padding larger than the area does not throw and gives a finite transform")
   void paddingLargerThanArea(Layout layout) {
      Fit f = assertDoesNotThrow(() -> new Fit(layout, 20, 0, 0, 10, 10));
      assertFinite(f.vg.getScreenTransform());
   }

   private static void assertFinite(java.awt.geom.AffineTransform t) {
      double[] m = new double[6];
      t.getMatrix(m);

      for(double v : m) {
         assertTrue(Double.isFinite(v), "transform " + t);
      }
   }

   @Test
   @DisplayName("a clone keeps layout and padding and is independent of the original")
   void cloneIsIndependent() {
      TreemapCoord c = new TreemapCoord();
      c.setLayout(Layout.SQUARE);
      c.setPadding(4);
      TreemapCoord copy = (TreemapCoord) c.clone();
      assertEquals(Layout.SQUARE, copy.isLayout());
      assertEquals(4, copy.getPadding());
      copy.setPadding(9);
      assertEquals(4, c.getPadding());
   }

   @Test
   @DisplayName("pinned: constant unit sizes, no dimensions, scales or axes")
   void constantMembers() {
      TreemapCoord c = new TreemapCoord();
      assertEquals(100, c.getUnitMinWidth(), EPS);
      assertEquals(100, c.getUnitMinHeight(), EPS);
      assertEquals(100, c.getUnitPreferredWidth(), EPS);
      assertEquals(100, c.getUnitPreferredHeight(), EPS);
      assertEquals(0, c.getDimCount());
      assertEquals(0, c.getScales().length);
      assertEquals(0, c.getAxes(true).length);
      assertPoint(0, 0, c.getPosition(new double[] { 3, 4 }));
      assertEquals(0, c.getIntervalSize(7), EPS);
   }

   // ------------------------------------------------------------------ chain

   private static List<TreemapVO> plot(Layout layout, int padding) {
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "Name", "Size" }, { "A", 1 }, { "B", 2 }, { "C", 3 }, { "D", 4 } });
      TreemapSizeFrame size = new TreemapSizeFrame("Size");
      size.init(data);
      size.getLegendSpec().setVisible(false);
      TreemapElement element = new TreemapElement("Name");
      element.setSizeFrame(size);
      EGraph graph = new EGraph();
      graph.addElement(element);
      TreemapCoord coord = new TreemapCoord();
      coord.setLayout(layout);
      coord.setPadding(padding);
      graph.setCoordinate(coord);
      VGraph vg = Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 600, 400);
      List<TreemapVO> vos = new ArrayList<>();

      for(int i = 0; i < vg.getVisualCount(); i++) {
         if(vg.getVisual(i) instanceof TreemapVO) {
            vos.add((TreemapVO) vg.getVisual(i));
         }
      }

      return vos;
   }

   private static Rectangle2D union(List<TreemapVO> vos) {
      Rectangle2D u = null;

      for(TreemapVO vo : vos) {
         Rectangle2D b = vo.getBounds();
         u = u == null ? (Rectangle2D) b.clone() : u.createUnion(b);
      }

      return u;
   }

   private static void assertNear(double x, double y, double w, double h, double tol,
                                  Rectangle2D r)
   {
      assertEquals(x, r.getX(), tol, "x of " + r);
      assertEquals(y, r.getY(), tol, "y of " + r);
      assertEquals(w, r.getWidth(), tol, "width of " + r);
      assertEquals(h, r.getHeight(), tol, "height of " + r);
   }

   @Test
   @DisplayName("chart: the leaves fill the plot and their areas are proportional to the sizes")
   void chainAreas() {
      List<TreemapVO> vos = plot(Layout.FILL, 0);
      assertEquals(4, vos.size());
      Rectangle2D u = union(vos);
      assertNear(0, 0, 598, 398, 1, u);
      double[] ratios = new double[4];

      for(int i = 0; i < 4; i++) {
         Rectangle2D b = vos.get(i).getBounds();
         ratios[i] = b.getWidth() * b.getHeight() / (u.getWidth() * u.getHeight());
      }

      Arrays.sort(ratios);
      assertArrayEquals(new double[] { 0.1, 0.2, 0.3, 0.4 }, ratios, 1e-6);
   }

   @Test
   @DisplayName("chart: the square layout draws a centred square")
   void chainSquare() {
      Rectangle2D u = union(plot(Layout.SQUARE, 0));
      assertEquals(u.getWidth(), u.getHeight(), 0.5);
      assertEquals(299, u.getCenterX(), 0.5);
      assertEquals(199, u.getCenterY(), 0.5);
   }

   @Test
   @DisplayName("chart: padding insets the leaves on every side")
   void chainPadding() {
      assertNear(2, 2, 594, 394, 1, union(plot(Layout.FILL, 4)));
   }
}
