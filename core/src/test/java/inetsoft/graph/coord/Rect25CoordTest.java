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
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.guide.axis.Axis2D;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.internal.ICoordinate;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.visual.ElementVO;
import inetsoft.graph.visual.FormVO;
import inetsoft.graph.visual.LineFormVO;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The 2.5D rectangular coordinate: depth handling in fit, axes, border visuals.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class Rect25CoordTest {
   private static final double EPS = 1e-6;

   /** Coordinate, its visual graph, and the scales. */
   private static class Fixture {
      CategoricalScale xs = new CategoricalScale("Cat");
      LinearScale ys = new LinearScale("m");
      RectCoord coord;
      VGraph vg;

      Fixture(boolean depth25, double depth, boolean axisSizes, int xstyle, int ystyle) {
         this(depth25, depth, axisSizes, xstyle, ystyle, 0, 100, false);
      }

      Fixture(boolean depth25, double depth, boolean axisSizes, int xstyle, int ystyle,
              double ymin, double ymax, boolean xgrid)
      {
         xs.init("A", "B", "C");
         xs.setFill(false);
         ys.setMin(ymin);
         ys.setMax(ymax);

         if(xgrid) {
            xs.getAxisSpec().setGridStyle(GraphConstants.THIN_LINE);
         }

         xs.getAxisSpec().setAxisStyle(xstyle);
         ys.getAxisSpec().setAxisStyle(ystyle);

         if(depth25) {
            Rect25Coord c = new Rect25Coord(xs, ys);
            c.setDepth(depth);
            coord = c;
         }
         else {
            coord = new RectCoord(xs, ys);
         }

         vg = new VGraph(coord);
         coord.createAxis(vg);

         if(axisSizes) {
            coord.setAxisSize(ICoordinate.BOTTOM_AXIS, 30);
            coord.setAxisSize(ICoordinate.LEFT_AXIS, 40);
         }
      }

      Point2D m(double x, double y) {
         return vg.getScreenTransform().transform(new Point2D.Double(x, y), null);
      }
   }

   private static Fixture rx(double depth, boolean axisSizes) {
      return new Fixture(true, depth, axisSizes, AxisSpec.AXIS_SINGLE, AxisSpec.AXIS_SINGLE);
   }

   private static void assertBounds(double x, double y, double w, double h, Rectangle2D r) {
      assertEquals(x, r.getX(), EPS, "x of " + r);
      assertEquals(y, r.getY(), EPS, "y of " + r);
      assertEquals(w, r.getWidth(), EPS, "width of " + r);
      assertEquals(h, r.getHeight(), EPS, "height of " + r);
   }

   @Test
   @DisplayName("the depth does not change the total plot area compared with a flat coordinate")
   void plotBoundsLikeFlat() {
      Fixture f = rx(15, true);
      f.coord.fit(0, 0, 400, 300);
      Fixture flat = new Fixture(false, 0, true, AxisSpec.AXIS_SINGLE, AxisSpec.AXIS_SINGLE);
      flat.coord.fit(0, 0, 400, 300);
      assertBounds(40, 30, 360, 270, f.vg.getPlotBounds());
      assertBounds(40, 30, 360, 270, flat.vg.getPlotBounds());
   }

   @Test
   @DisplayName("elements keep the plot area minus the depth strip")
   void elementArea() {
      Fixture f = rx(15, true);
      f.coord.fit(0, 0, 400, 300);
      Point2D origin = f.m(0, 0);
      Point2D corner = f.m(1000, 1000);
      assertEquals(40, origin.getX(), EPS);
      assertEquals(30, origin.getY(), EPS);
      assertEquals(385, corner.getX(), EPS);
      assertEquals(285, corner.getY(), EPS);
   }

   @Test
   @DisplayName("depth 0 behaves like a flat coordinate")
   void depthZeroLikeFlat() {
      Fixture f = rx(0, true);
      f.coord.fit(0, 0, 400, 300);
      Fixture flat = new Fixture(false, 0, true, AxisSpec.AXIS_SINGLE, AxisSpec.AXIS_SINGLE);
      flat.coord.fit(0, 0, 400, 300);

      for(Fixture x : new Fixture[] { f, flat }) {
         assertBounds(40, 30, 360, 270, x.vg.getPlotBounds());
         assertEquals(400, x.m(1000, 1000).getX(), EPS);
         assertEquals(300, x.m(1000, 1000).getY(), EPS);
      }
   }

   @ParameterizedTest
   @ValueSource(doubles = { 0, 15, 40 })
   @DisplayName("the depth is taken from the element area and not from the plot bounds")
   void depthParameter(double depth) {
      Fixture f = rx(depth, true);
      f.coord.fit(0, 0, 400, 300);
      assertBounds(40, 30, 360, 270, f.vg.getPlotBounds());
      assertEquals(400 - depth, f.m(1000, 1000).getX(), EPS);
      assertEquals(300 - depth, f.m(1000, 1000).getY(), EPS);
   }

   @Test
   @DisplayName("an area narrower than the depth: no exception, depth within the area, plot inside")
   void areaNarrowerThanDepth() {
      Fixture f = rx(15, false);
      assertDoesNotThrow(() -> f.coord.fit(0, 0, 10, 200));
      assertTrue(f.coord.getDepth() <= 10, "depth " + f.coord.getDepth());
      assertTrue(new Rectangle2D.Double(0, 0, 10, 200).contains(f.vg.getPlotBounds()),
                 "plot " + f.vg.getPlotBounds());
   }

   @Test
   @DisplayName("an area lower than the depth: no exception, depth within the area, plot inside")
   void areaLowerThanDepth() {
      Fixture f = rx(15, false);
      assertDoesNotThrow(() -> f.coord.fit(0, 0, 200, 10));
      assertTrue(f.coord.getDepth() <= 10, "depth " + f.coord.getDepth());
      assertTrue(new Rectangle2D.Double(0, 0, 200, 10).contains(f.vg.getPlotBounds()),
                 "plot " + f.vg.getPlotBounds());
   }

   @Test
   @DisplayName("one pixel wider than the depth keeps the depth, an equal width never exceeds it")
   void depthBoundary() {
      Fixture wider = rx(15, false);
      wider.coord.fit(0, 0, 16, 200);
      assertEquals(15, wider.coord.getDepth(), EPS);
      assertEquals(1, wider.m(1000, 1000).getX() - wider.m(0, 0).getX(), EPS);

      Fixture equal = rx(15, false);
      assertDoesNotThrow(() -> equal.coord.fit(0, 0, 15, 200));
      assertTrue(equal.coord.getDepth() <= 15, "depth " + equal.coord.getDepth());
   }


   @Test
   @DisplayName("createAxis creates 2.5D axes carrying the depth")
   void axisTypesAndDepth() {
      Fixture f = rx(40, true);
      assertInstanceOf(Axis2D.class, f.coord.getXAxis1());
      assertInstanceOf(Axis2D.class, f.coord.getYAxis1());
      assertEquals(40, ((Axis2D) f.coord.getXAxis1()).getDepth(), EPS);
      assertEquals(40, ((Axis2D) f.coord.getYAxis1()).getDepth(), EPS);
   }

   @Test
   @DisplayName("an axis style of none hides the axis but keeps its grid lines")
   void axisStyleNone() {
      Fixture f = new Fixture(true, 15, true, AxisSpec.AXIS_NONE, AxisSpec.AXIS_SINGLE, 0, 100,
                              true);
      f.coord.fit(0, 0, 400, 300);

      DefaultAxis x1 = f.coord.getXAxis1();
      assertNotNull(x1);
      assertTrue(x1.getZIndex() < 0, "z index " + x1.getZIndex());

      int visible = 0;

      for(DefaultAxis axis : new DefaultAxis[] { x1, f.coord.getXAxis2() }) {
         for(int i = 0; axis != null && i < axis.getGridLineCount(); i++) {
            if(axis.getGridLine(i).getZIndex() >= 0) {
               visible++;
            }
         }
      }

      assertTrue(visible > 0, "visible grid lines");
   }

   @Test
   @DisplayName("pinned: the double axis style shows the primary axes and hides the secondary")
   void doubleStyleHidesSecondaryAxes() {
      Fixture f = new Fixture(true, 15, true, AxisSpec.AXIS_DOUBLE, AxisSpec.AXIS_DOUBLE);
      assertTrue(f.coord.getXAxis1().getZIndex() >= 0, "x1 visible");
      assertTrue(f.coord.getYAxis1().getZIndex() >= 0, "y1 visible");
      assertNotNull(f.coord.getXAxis2());
      assertNotNull(f.coord.getYAxis2());
      assertTrue(f.coord.getXAxis2().getZIndex() < 0, "x2 hidden");
      assertTrue(f.coord.getYAxis2().getZIndex() < 0, "y2 hidden");
   }

   @Test
   @DisplayName("a missing scale leaves that side without an axis and does not break fit")
   void missingScale() {
      Fixture base = rx(15, false);

      Rect25Coord noX = new Rect25Coord(null, base.ys);
      VGraph vgx = new VGraph(noX);
      noX.createAxis(vgx);
      assertDoesNotThrow(() -> noX.fit(0, 0, 400, 300));
      assertNull(noX.getXAxis1());
      assertInstanceOf(Axis2D.class, noX.getYAxis1());

      Rect25Coord noY = new Rect25Coord(base.xs, null);
      VGraph vgy = new VGraph(noY);
      noY.createAxis(vgy);
      assertDoesNotThrow(() -> noY.fit(0, 0, 400, 300));
      assertNull(noY.getYAxis1());
      assertInstanceOf(Axis2D.class, noY.getXAxis1());
   }

   private static AffineTransform transformOf(LineFormVO vo) {
      return vo.getScreenTransform();
   }

   @Test
   @DisplayName("every diagonal border line spans the depth horizontally and vertically")
   void borderDiagonals() {
      Fixture f = rx(15, true);
      f.coord.fit(0, 0, 400, 300);
      int diagonals = 0;

      for(int i = 0; i < f.vg.getVisualCount(); i++) {
         if(!(f.vg.getVisual(i) instanceof LineFormVO)) {
            continue;
         }

         LineFormVO vo = (LineFormVO) f.vg.getVisual(i);
         Point2D[] pts = vo.getPoints();
         AffineTransform t = transformOf(vo);
         Point2D a = t.transform(pts[0], null);
         Point2D b = t.transform(pts[1], null);
         double dx = Math.abs(b.getX() - a.getX());
         double dy = Math.abs(b.getY() - a.getY());

         if(dx > EPS && dy > EPS) {
            diagonals++;
            assertEquals(15, dx, EPS, "dx");
            assertEquals(15, dy, EPS, "dy");
         }
      }

      assertTrue(diagonals > 0, "no diagonal border line found");
   }

   @Test
   @DisplayName("pinned: fit adds 10 border lines and 2 fills, 14 lines when y crosses zero")
   void borderVisualCount() {
      Fixture f = rx(15, true);
      f.coord.fit(0, 0, 400, 300);
      assertEquals(10, countLines(f.vg));
      assertEquals(2, countFills(f.vg));

      Fixture neg = new Fixture(true, 15, true, AxisSpec.AXIS_SINGLE, AxisSpec.AXIS_SINGLE,
                                -50, 50, false);
      neg.coord.fit(0, 0, 400, 300);
      assertEquals(14, countLines(neg.vg));
      assertEquals(2, countFills(neg.vg));
   }

   private static int countLines(VGraph vg) {
      int n = 0;

      for(int i = 0; i < vg.getVisualCount(); i++) {
         if(vg.getVisual(i) instanceof LineFormVO) {
            n++;
         }
      }

      return n;
   }

   private static int countFills(VGraph vg) {
      int n = 0;

      for(int i = 0; i < vg.getVisualCount(); i++) {
         if(vg.getVisual(i) instanceof FormVO && !(vg.getVisual(i) instanceof LineFormVO)) {
            n++;
         }
      }

      return n;
   }

   @Test
   @DisplayName("chart of 3D bars: every bar lies inside the plot bounds")
   void chainBarsInsidePlot() {
      CategoricalScale xs = new CategoricalScale("Cat");
      LinearScale ys = new LinearScale("m");
      ys.setMin(0);
      ys.setMax(100);
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("Cat", "m"));
      Rect25Coord coord = new Rect25Coord(xs, ys);
      coord.setDepth(15);
      graph.setCoordinate(coord);
      graph.setScale("Cat", xs);
      graph.setScale("m", ys);
      Object[][] rows = { { "Cat", "m" }, { "A", 40.0 }, { "B", 80.0 }, { "C", 60.0 } };
      VGraph vg = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 600, 400);

      Rectangle2D plot = vg.getPlotBounds();
      Rectangle2D whole = new Rectangle2D.Double(0, 0, 599, 399);
      assertTrue(whole.contains(plot.getX() - 1, plot.getY() - 1) &&
                 plot.getMaxX() <= 599 + 1 && plot.getMaxY() <= 399 + 1,
                 "plot " + plot + " outside " + whole);
      int bars = 0;

      for(int i = 0; i < vg.getVisualCount(); i++) {
         if(vg.getVisual(i) instanceof ElementVO) {
            bars++;
            Rectangle2D b = vg.getVisual(i).getBounds();
            assertTrue(b.getX() >= plot.getX() - 1 && b.getY() >= plot.getY() - 1 &&
                       b.getMaxX() <= plot.getMaxX() + 1 && b.getMaxY() <= plot.getMaxY() + 1,
                       "bar " + b + " outside plot " + plot);
         }
      }

      assertEquals(3, bars);
   }
}
