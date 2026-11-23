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

import inetsoft.graph.VGraph;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.AxisSpec;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Axes and fit of the ternary coordinate.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class TriCoordFitTest {
   private static final double EPS = 1e-6;
   private static final double SIN60 = Math.sin(Math.PI / 3);

   private TriCoord coord;
   private VGraph vg;

   private static LinearScale scale() {
      LinearScale s = new LinearScale("v");
      s.setMin(0);
      s.setMax(1);
      s.setIncrement(0.25);
      return s;
   }

   @BeforeEach
   void setUp() {
      coord = new TriCoord(scale());
      vg = new VGraph(coord);
      coord.createAxis(vg);
   }

   private DefaultAxis axis(int i) {
      return (DefaultAxis) coord.getAxes(false)[i];
   }

   private static Point2D start(Axis axis) {
      return axis.getScreenTransform().transform(new Point2D.Double(0, 0), null);
   }

   private static Point2D end(DefaultAxis axis) {
      return axis.getScreenTransform().transform(new Point2D.Double(axis.getLength(), 0), null);
   }

   private Point2D vertex(double x, double y, double z) {
      Point2D p = coord.getPosition(new double[] { x, y, z });
      return vg.getScreenTransform().transform(p, null);
   }

   private static void assertPoint(Point2D expected, Point2D actual, String msg) {
      assertEquals(expected.getX(), actual.getX(), 1e-4, msg + " x");
      assertEquals(expected.getY(), actual.getY(), 1e-4, msg + " y");
   }

   @Test
   @DisplayName("createAxis adds three vertical-type axes of length 1000")
   void createAxisAddsThreeAxes() {
      assertEquals(3, vg.getAxisCount());
      assertEquals(3, coord.getAxes(false).length);

      for(int i = 0; i < 3; i++) {
         assertEquals("y", axis(i).getAxisType());
         assertEquals(1000, axis(i).getLength(), EPS);
      }
   }

   @Test
   @DisplayName("an axis style of none or a missing scale creates no axes and fit still works")
   void noAxes() {
      Scale s = scale();
      s.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);

      for(TriCoord c : new TriCoord[] { new TriCoord(s), new TriCoord() }) {
         VGraph g = new VGraph(c);
         c.createAxis(g);
         assertEquals(0, c.getAxes(false).length);
         assertEquals(0, g.getAxisCount());
         assertDoesNotThrow(() -> c.fit(0, 0, 600, 400));
      }
   }

   @Test
   @DisplayName("before fit the axes run along the sides of the triangle")
   void axisTransformsBeforeFit() {
      double h = 1000 * SIN60;
      assertPoint(new Point2D.Double(0, 0), start(axis(2)), "axis 2 start");
      assertPoint(new Point2D.Double(1000, 0), end(axis(2)), "axis 2 end");
      assertPoint(new Point2D.Double(1000, 0), start(axis(1)), "axis 1 start");
      assertPoint(new Point2D.Double(500, h), end(axis(1)), "axis 1 end");
      assertPoint(new Point2D.Double(500, h), start(axis(0)), "axis 0 start");
      assertPoint(new Point2D.Double(0, 0), end(axis(0)), "axis 0 end");
   }

   @ParameterizedTest
   @CsvSource({ "600,400", "400,600", "300,300" })
   @DisplayName("fit keeps the triangle equilateral for any area shape")
   void fitEquilateral(double w, double h) {
      coord.fit(0, 0, w, h);
      Point2D x = vertex(1, 0, 0);
      Point2D y = vertex(0, 1, 0);
      Point2D z = vertex(0, 0, 1);
      double xy = x.distance(y);
      assertTrue(xy > 0);
      assertEquals(xy, y.distance(z), 1e-4);
      assertEquals(xy, z.distance(x), 1e-4);
   }

   @Test
   @DisplayName("after fit the vertices sit on the plot bounds")
   void verticesOnPlotBox() {
      coord.fit(0, 0, 600, 400);
      Rectangle2D box = vg.getPlotBounds();
      assertPoint(new Point2D.Double(box.getX(), box.getY()), vertex(0, 0, 1), "z vertex");
      assertPoint(new Point2D.Double(box.getMaxX(), box.getY()), vertex(1, 0, 0), "x vertex");
      assertPoint(new Point2D.Double(box.getCenterX(), box.getMaxY()), vertex(0, 1, 0),
                  "y vertex");
   }

   @ParameterizedTest
   @CsvSource({ "600,400", "200,500" })
   @DisplayName("fit centres the triangle in the area and keeps it inside")
   void fitCentredAndContained(double w, double h) {
      coord.fit(10, 20, w, h);
      Rectangle2D box = vg.getPlotBounds();
      assertEquals(10 + w / 2, box.getCenterX(), 1e-4);
      assertTrue(box.getX() >= 10 - 1e-6 && box.getMaxX() <= 10 + w + 1e-6, "x range " + box);
      assertTrue(box.getY() >= 20 - 1e-6 && box.getMaxY() <= 20 + h + 1e-6, "y range " + box);
      assertEquals(1 / SIN60, box.getWidth() / box.getHeight(), 1e-6);
   }

   @Test
   @DisplayName("after fit each axis runs between two vertices of the triangle")
   void axesEndOnVertices() {
      coord.fit(0, 0, 600, 400);
      Point2D vx = vertex(1, 0, 0);
      Point2D vy = vertex(0, 1, 0);
      Point2D vz = vertex(0, 0, 1);
      assertPoint(vz, start(axis(2)), "axis 2 start");
      assertPoint(vx, end(axis(2)), "axis 2 end");
      assertPoint(vx, start(axis(1)), "axis 1 start");
      assertPoint(vy, end(axis(1)), "axis 1 end");
      assertPoint(vy, start(axis(0)), "axis 0 start");
      assertPoint(vz, end(axis(0)), "axis 0 end");
   }

   @ParameterizedTest
   @ValueSource(doubles = { 0.25, 0.5, 0.75 })
   @DisplayName("a point on the bottom edge lies on the bottom axis at its share")
   void edgePointOnBottomAxis(double t) {
      coord.fit(0, 0, 600, 400);
      Rectangle2D box = vg.getPlotBounds();
      Point2D s = start(axis(2));
      Point2D e = end(axis(2));
      Point2D p = vertex(t, 0, 1 - t);
      assertEquals(s.getX() + t * (e.getX() - s.getX()), p.getX(), 1e-4);
      assertEquals(box.getY(), p.getY(), 1e-4);
   }

   @Test
   @Disabled("Bug #82031: TriCoord minimum unit size equals the preferred size")
   @DisplayName("unit sizes follow the size of the bottom axis")
   void unitSizesFollowAxis() {
      coord.fit(0, 0, 600, 400);
      DefaultAxis bottom = axis(2);
      assertEquals(bottom.getMinWidth(), coord.getUnitMinWidth(), EPS, "unit min width");
      assertEquals(bottom.getMinWidth(), coord.getUnitMinHeight(), EPS, "unit min height");
      assertEquals(bottom.getPreferredWidth(), coord.getUnitPreferredWidth(), EPS,
                   "unit preferred width");
      assertEquals(bottom.getPreferredWidth(), coord.getUnitPreferredHeight(), EPS,
                   "unit preferred height");
   }

   @Test
   @DisplayName("a clone made before createAxis builds its own axes")
   void cloneBeforeCreateAxis() {
      TriCoord original = new TriCoord(scale());
      TriCoord copy = (TriCoord) original.clone();
      copy.createAxis(new VGraph(copy));
      assertEquals(0, original.getAxes(false).length);
      assertEquals(3, copy.getAxes(false).length);
   }

   @Test
   @DisplayName("fit without a visual graph does not throw")
   void fitWithoutVGraph() {
      TriCoord c = new TriCoord(scale());
      assertDoesNotThrow(() -> c.fit(0, 0, 600, 400));
   }
}
