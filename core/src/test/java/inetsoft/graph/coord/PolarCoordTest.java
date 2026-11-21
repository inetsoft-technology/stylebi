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

import inetsoft.graph.AxisSpec;
import inetsoft.graph.internal.Donut;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Shape;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure geometry of {@link PolarCoord}: point, line, rectangle and shape transforms, type and hole
 * ratio, rotate/reflect matrices, delegation to the inner coordinate and clone/equalsContent.
 * Cardinal points give exact hand values on the 1000 x 1000 logical space (maximum radius 500).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class PolarCoordTest {
   private static final double EPS = 1e-6;
   private static final double EPS_PATH = 1e-3;

   /** unit THETA_RHO polar coordinate over a bare RectCoord. */
   private static PolarCoord thetaRho() {
      return new PolarCoord(new RectCoord());
   }

   private static PolarCoord typed(int type) {
      PolarCoord coord = thetaRho();
      coord.setType(type);
      return coord;
   }

   private static Point2D pt(PolarCoord coord, double x, double y) {
      return (Point2D) coord.transformShape(new Point2D.Double(x, y));
   }

   private static void assertPoint(double x, double y, Point2D actual, double eps) {
      assertEquals(x, actual.getX(), eps, "x of " + actual);
      assertEquals(y, actual.getY(), eps, "y of " + actual);
   }

   private static Shape shape(PolarCoord coord, double x, double y, double w, double h) {
      return (Shape) coord.transformShape(new Rectangle2D.Double(x, y, w, h));
   }

   private static Arc2D arc(Shape shape) {
      assertInstanceOf(Arc2D.class, shape);
      return (Arc2D) shape;
   }

   private static List<Point2D> vertices(Shape shape) {
      List<Point2D> list = new ArrayList<>();
      double[] c = new double[6];

      for(PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
         int type = it.currentSegment(c);

         if(type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
            list.add(new Point2D.Double(c[0], c[1]));
         }
      }

      return list;
   }

   private static void assertIdentity(AffineTransform t, double eps) {
      double[] m = new double[6];
      t.getMatrix(m);
      double[] id = { 1, 0, 0, 1, 0, 0 };

      for(int i = 0; i < 6; i++) {
         assertEquals(id[i], m[i], eps, "matrix element " + i);
      }
   }

   // ---- point transform

   @Test
   @DisplayName("THETA_RHO maps the cardinal angles on the outer radius to the compass points")
   void pointCardinalAngles() {
      PolarCoord c = thetaRho();

      assertPoint(500, 0, pt(c, 0, 1000), EPS);
      assertPoint(0, 500, pt(c, 250, 1000), EPS);
      assertPoint(-500, 0, pt(c, 500, 1000), EPS);
      assertPoint(0, -500, pt(c, 750, 1000), EPS);
   }

   @Test
   @DisplayName("THETA_RHO radius is y/2 and x is irrelevant at y = 0")
   void pointRadius() {
      PolarCoord c = thetaRho();

      assertPoint(-250, 0, pt(c, 500, 500), EPS);
      assertPoint(0, 0, pt(c, 123, 0), EPS);
      assertPoint(0, 0, pt(c, 999, 0), EPS);
   }

   @Test
   @DisplayName("position then inverse (atan2/hypot) is the identity for 200 random points")
   void pointRoundTrip() {
      PolarCoord c = thetaRho();
      Random random = new Random(7);

      for(int i = 0; i < 200; i++) {
         double x = random.nextDouble() * 1000;
         double y = 1 + random.nextDouble() * 999;
         Point2D p = pt(c, x, y);
         double theta = Math.atan2(p.getY(), p.getX());

         if(theta < 0) {
            theta += 2 * Math.PI;
         }

         double x2 = theta * 1000 / (2 * Math.PI);
         double dx = Math.abs(x2 - x);
         dx = Math.min(dx, 1000 - dx);

         assertEquals(0, dx, EPS, "x of " + x + "," + y);
         assertEquals(y, 2 * Math.hypot(p.getX(), p.getY()), EPS, "y of " + x + "," + y);
      }
   }

   @ParameterizedTest(name = "y={0}")
   @ValueSource(doubles = { 100, 700, 1000 })
   @DisplayName("x = 0 and x = 1000 are the same point (full turn)")
   void pointWrapsAfterFullTurn(double y) {
      PolarCoord c = thetaRho();

      assertPoint(pt(c, 0, y).getX(), pt(c, 0, y).getY(), pt(c, 1000, y), EPS);
   }

   @Test
   @DisplayName("for a fixed x the distance to the centre strictly increases with y")
   void pointRadiusMonotoneInY() {
      PolarCoord c = thetaRho();
      double last = -1;

      for(double y = 0; y <= 1000; y += 100) {
         double r = pt(c, 300, y).distance(0, 0);

         assertTrue(r > last, "radius at y=" + y);
         last = r;
      }
   }

   @Test
   @DisplayName("THETA type ignores x, y is the angle and the radius is always 500")
   void thetaTypeUsesYAsAngle() {
      PolarCoord c = typed(PolarCoord.THETA);

      assertPoint(0, 500, pt(c, 0, 250), EPS);
      assertPoint(0, 500, pt(c, 777, 250), EPS);
      assertPoint(500, 0, pt(c, 300, 0), EPS);
      assertPoint(-500, 0, pt(c, 300, 500), EPS);
   }

   @Test
   @DisplayName("RHO type has a fixed angle of 2pi, radius y/2")
   void rhoTypeUsesYAsRadius() {
      PolarCoord c = typed(PolarCoord.RHO);

      assertPoint(250, 0, pt(c, 0, 500), EPS);
      assertPoint(250, 0, pt(c, 700, 500), EPS);
      assertPoint(0, 0, pt(c, 300, 0), EPS);
   }

   @Test
   @DisplayName("PLUS type starts at the hole ratio: radius ((y/1000)*(1-hole)+hole)*500")
   void plusTypeRadius() {
      PolarCoord c = typed(PolarCoord.PLUS);

      assertPoint(150, 0, pt(c, 0, 0), EPS);
      assertPoint(325, 0, pt(c, 0, 500), EPS);
      assertPoint(500, 0, pt(c, 0, 1000), EPS);
      assertPoint(0, 500, pt(c, 250, 1000), EPS);
   }

   @Test
   @DisplayName("PLUS hole ratio 0.6 gives inner radius 300 and keeps the outer radius")
   void plusHoleRatio() {
      PolarCoord c = typed(PolarCoord.PLUS);

      assertEquals(0.3, c.getHoleRatio(), 0);
      c.setHoleRatio(0.6);

      assertEquals(0.6, c.getHoleRatio(), 0);
      assertPoint(300, 0, pt(c, 0, 0), EPS);
      assertPoint(500, 0, pt(c, 0, 1000), EPS);
   }

   @Test
   @DisplayName("PLUS with hole 0 behaves like THETA_RHO for 50 random points")
   void plusWithoutHoleEqualsThetaRho() {
      PolarCoord plus = typed(PolarCoord.PLUS);
      PolarCoord base = thetaRho();
      Random random = new Random(11);

      plus.setHoleRatio(0);

      for(int i = 0; i < 50; i++) {
         double x = random.nextDouble() * 1000;
         double y = random.nextDouble() * 1000;
         Point2D expected = pt(base, x, y);

         assertPoint(expected.getX(), expected.getY(), pt(plus, x, y), 1e-9);
      }
   }

   @ParameterizedTest(name = "type={0}")
   @ValueSource(ints = { 0, 5, 8 })
   @DisplayName("an unsupported type throws for points and for rectangles")
   void unsupportedTypeThrows(int type) {
      PolarCoord c = typed(type);

      RuntimeException e1 = assertThrows(RuntimeException.class,
                                         () -> c.transformShape(new Point2D.Double(1, 1)));
      RuntimeException e2 = assertThrows(RuntimeException.class,
                                         () -> c.transformShape(new Rectangle2D.Double(0, 0, 1, 1)));

      assertTrue(e1.getMessage().contains("Unsupported transformation"), e1.getMessage());
      assertTrue(e2.getMessage().contains("Unsupported transformation"), e2.getMessage());
   }

   @Test
   @DisplayName("3D pie coordinate squeezes y by 0.5 and keeps x")
   void pie3dSqueezesY() {
      PolarCoord c = new PolarCoord(new Rect25Coord());

      assertPoint(0, 250, pt(c, 250, 1000), EPS);
      assertPoint(500, 0, pt(c, 0, 1000), EPS);
   }

   @Test
   @DisplayName("3D pie coordinate equals the 2D one with y halved for 50 random points")
   void pie3dAgainstPie2d() {
      PolarCoord c3 = new PolarCoord(new Rect25Coord());
      PolarCoord c2 = thetaRho();
      Random random = new Random(3);

      for(int i = 0; i < 50; i++) {
         double x = random.nextDouble() * 1000;
         double y = random.nextDouble() * 1000;
         Point2D p3 = pt(c3, x, y);
         Point2D p2 = pt(c2, x, y);

         assertEquals(p2.getX(), p3.getX(), 1e-9);
         assertEquals(p2.getY() * 0.5, p3.getY(), 1e-9);
      }
   }

   @Test
   @DisplayName("an inner reflect(false) maps x to 1000 - x before the polar transform")
   void innerReflectHorizontal() {
      RectCoord inner = new RectCoord();
      PolarCoord c = new PolarCoord(inner);

      inner.reflect(false);

      assertPoint(0, -500, pt(c, 250, 1000), EPS);
      assertPoint(pt(thetaRho(), 750, 1000).getX(), pt(thetaRho(), 750, 1000).getY(),
                  pt(c, 250, 1000), EPS);
   }

   @Test
   @DisplayName("an inner transpose swaps x and y before the polar transform")
   void innerTranspose() {
      RectCoord inner = new RectCoord();
      PolarCoord c = new PolarCoord(inner);

      inner.transpose();

      assertPoint(0, 500, pt(c, 1000, 250), EPS);
   }

   @Test
   @DisplayName("null transforms to null and the input point is not modified")
   void nullAndInputUnchanged() {
      PolarCoord c = thetaRho();
      Point2D in = new Point2D.Double(250, 1000);

      assertNull(c.transformShape(null));
      c.transformShape(in);
      assertEquals(250, in.getX(), 0);
      assertEquals(1000, in.getY(), 0);
   }

   // ---- line transform

   @Test
   @DisplayName("a vertical line becomes a radial straight line, PLUS starts at the hole")
   void verticalLineIsRadial() {
      Shape line = (Shape) thetaRho().transformShape(new Line2D.Double(250, 0, 250, 1000));
      Shape plus = (Shape) typed(PolarCoord.PLUS).transformShape(new Line2D.Double(250, 0, 250, 1000));

      Line2D l = (Line2D) line;
      Line2D p = (Line2D) plus;

      assertPoint(0, 0, l.getP1(), EPS);
      assertPoint(0, 500, l.getP2(), EPS);
      assertPoint(0, 150, p.getP1(), EPS);
      assertPoint(0, 500, p.getP2(), EPS);
   }

   @Test
   @Disabled("Bug #82032: Polar grid lines drawn as shapes have twice the radius of the data")
   @DisplayName("a horizontal line becomes an arc with the radius of its transformed endpoints")
   void horizontalLineArcRadius() {
      PolarCoord c = thetaRho();
      Arc2D a = arc((Shape) c.transformShape(new Line2D.Double(0, 1000, 250, 1000)));
      Point2D end = pt(c, 0, 1000);

      assertEquals(end.distance(0, 0), a.getWidth() / 2, EPS);
      assertEquals(500, a.getWidth() / 2, EPS);
   }

   @Test
   @Disabled("Bug #82032: Polar grid lines drawn as shapes have twice the radius of the data")
   @DisplayName("a horizontal line arc joins the transformed endpoints")
   void horizontalLineArcJoinsEndpoints() {
      PolarCoord c = thetaRho();
      Arc2D a = arc((Shape) c.transformShape(new Line2D.Double(0, 500, 250, 500)));

      assertPoint(250, 0, a.getStartPoint(), EPS);
      assertPoint(0, 250, a.getEndPoint(), EPS);
   }

   @Test
   @DisplayName("PLUS horizontal lines are circles of the hole radius and the outer radius")
   void horizontalLineInPlus() {
      PolarCoord c = typed(PolarCoord.PLUS);
      Arc2D inner = arc((Shape) c.transformShape(new Line2D.Double(0, 0, 250, 0)));
      Arc2D outer = arc((Shape) c.transformShape(new Line2D.Double(0, 1000, 250, 1000)));

      assertEquals(300, inner.getWidth(), EPS);
      assertEquals(1000, outer.getWidth(), EPS);
   }

   @Test
   @DisplayName("a diagonal line becomes a polyline from the centre to the transformed end point")
   void diagonalLine() {
      PolarCoord c = thetaRho();
      Shape path = (Shape) c.transformShape(new Line2D.Double(0, 0, 300, 400));
      List<Point2D> v = vertices(path);
      double last = -1;

      assertPoint(0, 0, v.get(0), EPS_PATH);
      assertPoint(-61.80, 190.21, v.get(v.size() - 1), EPS_PATH * 10);

      for(Point2D p : v) {
         double r = p.distance(0, 0);

         assertTrue(r >= last - EPS_PATH, "radius decreased at " + p);
         last = r;
      }
   }

   // ---- rectangle transform

   @Test
   @DisplayName("THETA slice of rect (0,250,10,250) is a pie arc from -90 spanning -90 degrees")
   void thetaSlice() {
      Arc2D a = arc(shape(typed(PolarCoord.THETA), 0, 250, 10, 250));

      assertEquals(Arc2D.PIE, a.getArcType());
      assertEquals(-500, a.getX(), EPS);
      assertEquals(-500, a.getY(), EPS);
      assertEquals(1000, a.getWidth(), EPS);
      assertEquals(1000, a.getHeight(), EPS);
      assertEquals(-90, a.getAngleStart(), EPS);
      assertEquals(-90, a.getAngleExtent(), EPS);
   }

   @Test
   @DisplayName("THETA slice covering the full height is an ellipse")
   void thetaSliceFullCircle() {
      Shape s = shape(typed(PolarCoord.THETA), 0, 0, 10, 1000);

      assertInstanceOf(Ellipse2D.class, s);
      assertEquals(-500, s.getBounds2D().getX(), EPS);
      assertEquals(1000, s.getBounds2D().getWidth(), EPS);
      assertEquals(1000, s.getBounds2D().getHeight(), EPS);
   }

   @Test
   @DisplayName("THETA slice with extent below 359.9 degrees is an arc, above it an ellipse")
   void thetaSliceExtentLimit() {
      PolarCoord c = typed(PolarCoord.THETA);

      assertInstanceOf(Arc2D.class, shape(c, 0, 0, 10, 999.7));
      assertInstanceOf(Ellipse2D.class, shape(c, 0, 0, 10, 999.8));
   }

   @Test
   @DisplayName("THETA slices of heights 100, 300, 600 have extents -36, -108, -216 and are contiguous")
   void thetaSlicesProportional() {
      PolarCoord c = typed(PolarCoord.THETA);
      double[] heights = { 100, 300, 600 };
      double[] extents = { -36, -108, -216 };
      double y = 0;
      double end = 0;

      for(int i = 0; i < 3; i++) {
         Arc2D a = arc(shape(c, 0, y, 10, heights[i]));

         assertEquals(-y * 0.36, a.getAngleStart(), EPS);
         assertEquals(extents[i], a.getAngleExtent(), EPS);
         assertEquals(end, a.getAngleStart(), EPS);
         end = a.getAngleStart() + a.getAngleExtent();
         y += heights[i];
      }

      assertEquals(-360, end, EPS);
   }

   @Test
   @DisplayName("THETA_RHO bar from the centre is a pie arc of half the height as radius")
   void barFromCentre() {
      Arc2D a = arc(shape(thetaRho(), 0, 0, 250, 500));

      assertEquals(Arc2D.PIE, a.getArcType());
      assertEquals(-250, a.getX(), EPS);
      assertEquals(-250, a.getY(), EPS);
      assertEquals(500, a.getWidth(), EPS);
      assertEquals(500, a.getHeight(), EPS);
      assertEquals(0, a.getAngleStart(), EPS);
      assertEquals(-90, a.getAngleExtent(), EPS);
   }

   @Test
   @DisplayName("THETA_RHO bar that does not start at 0 is a ring slice")
   void ringSlice() {
      Donut d = assertInstanceOf(Donut.class, shape(thetaRho(), 0, 500, 250, 500));
      Arc2D outer = d.getOuterArc();
      Arc2D inner = d.getInnerArc();

      assertEquals(-500, outer.getX(), EPS);
      assertEquals(1000, outer.getWidth(), EPS);
      assertEquals(500, inner.getWidth(), EPS);
      assertEquals(500, inner.getHeight(), EPS);
      assertEquals(0, outer.getAngleStart(), EPS);
      assertEquals(-90, outer.getAngleExtent(), EPS);
   }

   @ParameterizedTest(name = "x={0}")
   @ValueSource(doubles = { 0, 700 })
   @DisplayName("RHO type ring ignores x")
   void rhoRing(double x) {
      Donut d = assertInstanceOf(Donut.class, shape(typed(PolarCoord.RHO), x, 500, 10, 500));

      assertEquals(1000, d.getOuterArc().getWidth(), EPS);
      assertEquals(500, d.getInnerArc().getWidth(), EPS);
   }

   @Test
   @DisplayName("RHO type from radius 0 is a solid disc of diameter 500")
   void rhoSolidDisc() {
      Shape s = shape(typed(PolarCoord.RHO), 0, 0, 10, 500);

      assertEquals(500, s.getBounds2D().getWidth(), 1);
      assertTrue(s.contains(0, 0));
      assertTrue(s.contains(200, 0));
   }

   @Test
   @DisplayName("PLUS ring slice has the hole as inner diameter, hole 0 gives a pie arc")
   void plusRingSlice() {
      PolarCoord c = typed(PolarCoord.PLUS);
      Donut d = assertInstanceOf(Donut.class, shape(c, 0, 0, 250, 1000));

      assertEquals(1000, d.getOuterArc().getWidth(), EPS);
      assertEquals(300, d.getInnerArc().getWidth(), EPS);
      assertEquals(-90, d.getOuterArc().getAngleExtent(), EPS);

      c.setHoleRatio(0);

      assertEquals(Arc2D.PIE, arc(shape(c, 0, 0, 250, 1000)).getArcType());
   }

   @Test
   @DisplayName("a full-width donut ring is covered at its middle and open in the hole (#73966)")
   void donutFullRingCovered() {
      Shape ring = shape(thetaRho(), 0, 500, 1000, 500);

      for(int deg = 0; deg < 360; deg += 10) {
         double a = Math.toRadians(deg);

         assertTrue(ring.contains(375 * Math.cos(a), 375 * Math.sin(a)), "angle " + deg);
      }

      assertFalse(ring.contains(0, 0));
      assertFalse(ring.contains(100, 0));
      assertTrue(ring.contains(490, 0));
   }

   @Test
   @DisplayName("four donut slices tile the ring: every sample lies in exactly one slice (#74244)")
   void donutSlicesTileRing() {
      PolarCoord c = thetaRho();
      Shape[] slices = new Shape[4];

      for(int i = 0; i < 4; i++) {
         slices[i] = shape(c, i * 250, 500, 250, 500);
      }

      for(int k = 0; k < 36; k++) {
         double a = Math.toRadians(5 + 10 * k);
         int count = 0;

         for(Shape s : slices) {
            if(s.contains(375 * Math.cos(a), 375 * Math.sin(a))) {
               count++;
            }
         }

         assertEquals(1, count, "sample at " + (5 + 10 * k) + " degrees");
      }
   }

   @Test
   @DisplayName("donut slice angles are contiguous and sum to -360")
   void donutSlicesContiguous() {
      PolarCoord c = thetaRho();
      double[] widths = { 100, 300, 200, 250, 150 };
      double x = 0;
      double prevEnd = 0;
      double sum = 0;

      for(double w : widths) {
         Donut d = assertInstanceOf(Donut.class, shape(c, x, 500, w, 500));
         Arc2D outer = d.getOuterArc();

         assertEquals(prevEnd, outer.getAngleStart(), EPS);
         assertEquals(-w * 0.36, outer.getAngleExtent(), EPS);
         prevEnd = outer.getAngleStart() + outer.getAngleExtent();
         sum += outer.getAngleExtent();
         x += w;
      }

      assertEquals(-360, sum, EPS);
   }

   @Test
   @DisplayName("3D pie slice has half the height of the width")
   void pie3dSlice() {
      Arc2D a = arc(shape(new PolarCoord(new Rect25Coord()), 0, 0, 250, 500));

      assertEquals(-250, a.getX(), EPS);
      assertEquals(-125, a.getY(), EPS);
      assertEquals(500, a.getWidth(), EPS);
      assertEquals(250, a.getHeight(), EPS);
   }

   @Test
   @DisplayName("a rectangle through an inner reflect(false) starts at 270 degrees")
   void rectangleThroughInnerReflect() {
      RectCoord inner = new RectCoord();
      PolarCoord c = new PolarCoord(inner);

      inner.reflect(false);

      Arc2D a = arc(shape(c, 0, 0, 250, 500));

      assertEquals(-270, a.getAngleStart(), EPS);
      assertEquals(-90, a.getAngleExtent(), EPS);
      assertEquals(500, a.getWidth(), EPS);
   }

   // ---- general shape transform

   @Test
   @Disabled("Not filed (not reachable: under polar the BarVO shape stays a Rectangle2D, the generic Shape "
      + "branch has no reachable path): SEG_CLOSE emits moveTo with the untransformed rect-space start point "
      + "(800,800), so the closed triangle's bounds reach x 800; expected every point inside the [-500,500] disc")
   @DisplayName("a closed path stays inside the polar disc")
   void closedPathStaysInsideDisc() {
      Path2D.Double tri = new Path2D.Double();

      tri.moveTo(800, 800);
      tri.lineTo(800, 900);
      tri.lineTo(900, 800);
      tri.closePath();

      Rectangle2D b = ((Shape) thetaRho().transformShape(tri)).getBounds2D();

      assertTrue(b.getMinX() >= -500 && b.getMaxX() <= 500, "x range " + b);
      assertTrue(b.getMinY() >= -500 && b.getMaxY() <= 500, "y range " + b);
   }

   @Test
   @DisplayName("an open polyline has its vertices polar transformed")
   void openPolylineVertices() {
      Path2D.Double path = new Path2D.Double();

      path.moveTo(0, 0);
      path.lineTo(250, 500);
      path.lineTo(500, 1000);

      List<Point2D> v = vertices((Shape) thetaRho().transformShape(path));

      assertEquals(3, v.size());
      assertPoint(0, 0, v.get(0), EPS_PATH);
      assertPoint(0, 250, v.get(1), EPS_PATH);
      assertPoint(-500, 0, v.get(2), EPS_PATH);
   }

   // ---- delegation

   @Test
   @DisplayName("getPosition returns the inner logic position, not a polar one")
   void getPositionDelegates() {
      LinearScale ys = new LinearScale("m");
      ys.setMin(0);
      ys.setMax(100);
      PolarCoord c = new PolarCoord(ys);
      Point2D expected = ((RectCoord) c.getCoordinate()).getPosition(new double[] { 50 });

      assertPoint(expected.getX(), expected.getY(), c.getPosition(new double[] { 50 }), 0);
      assertPoint(0, 500, c.getPosition(new double[] { 50 }), EPS);

      CategoricalScale cs = new CategoricalScale("Cat");
      LinearScale ls = new LinearScale("m");
      ls.setMin(0);
      ls.setMax(100);
      PolarCoord c2 = new PolarCoord(cs, ls);
      Point2D e2 = ((RectCoord) c2.getCoordinate()).getPosition(new double[] { 0.5, 50 });

      assertPoint(e2.getX(), e2.getY(), c2.getPosition(new double[] { 0.5, 50 }), 0);
   }

   @Test
   @DisplayName("sizes, dimension count and scales are those of the inner coordinate")
   void sizesDelegate() {
      CategoricalScale cs = new CategoricalScale("Cat");
      LinearScale ls = new LinearScale("m");
      PolarCoord c = new PolarCoord(cs, ls);
      Coordinate inner = c.getCoordinate();

      assertEquals(2, c.getDimCount());
      assertEquals(inner.getDimCount(), c.getDimCount());
      assertArrayEquals(inner.getScales(), c.getScales());
      assertSame(cs, c.getScales()[0]);
      assertSame(ls, c.getScales()[1]);
      assertEquals(inner.getIntervalSize(10), c.getIntervalSize(10), 0);
      assertEquals(inner.getMaxWidth(), c.getMaxWidth(), 0);
      assertEquals(inner.getMaxHeight(), c.getMaxHeight(), 0);
   }

   @Test
   @DisplayName("constructors set the type and hide the axes of the scales")
   void constructors() {
      LinearScale ls = new LinearScale("m");
      PolarCoord one = new PolarCoord(ls);

      assertEquals(PolarCoord.THETA, one.getType());
      assertNull(((RectCoord) one.getCoordinate()).getXScale());
      assertEquals(AxisSpec.AXIS_NONE, ls.getAxisSpec().getAxisStyle());

      CategoricalScale cs = new CategoricalScale("Cat");
      LinearScale ls2 = new LinearScale("m");
      PolarCoord two = new PolarCoord(cs, ls2);

      assertEquals(PolarCoord.THETA_RHO, two.getType());
      assertEquals(AxisSpec.AXIS_NONE, cs.getAxisSpec().getAxisStyle());
      assertEquals(AxisSpec.AXIS_NONE, ls2.getAxisSpec().getAxisStyle());

      RectCoord rect = new RectCoord();
      PolarCoord three = new PolarCoord(rect);

      assertEquals(PolarCoord.THETA_RHO, three.getType());
      assertSame(rect, three.getCoordinate());
   }

   @Test
   @DisplayName("wrapping a RectCoord makes the categorical scale fill and grid and drops the ticks bit")
   void setCoordinateScaleOptions() {
      CategoricalScale cs = new CategoricalScale("Cat");
      LinearScale ls = new LinearScale("m");

      new PolarCoord(new RectCoord(cs, ls));

      assertTrue(cs.isFill());
      assertTrue(cs.isGrid());
      assertEquals(0, cs.getScaleOption() & Scale.TICKS);
      assertEquals(0, ls.getScaleOption() & Scale.TICKS);
   }

   @Test
   @DisplayName("pinned behavior: wrapping a RectCoord replaces the scale option by ZERO, dropping NO_NULL")
   void setCoordinateReplacesScaleOption() {
      CategoricalScale cs = new CategoricalScale("Cat");

      cs.setScaleOption(Scale.TICKS | Scale.ZERO | Scale.NO_NULL);
      new PolarCoord(new RectCoord(cs, new LinearScale("m")));

      assertEquals(Scale.ZERO, cs.getScaleOption());
   }

   @Test
   @DisplayName("wrapping a parallel or mekko coordinate leaves the scales' options and fill untouched")
   void setCoordinateOtherCoordsUntouched() {
      LinearScale a = new LinearScale("a");
      LinearScale b = new LinearScale("b");
      int oa = a.getScaleOption();
      int ob = b.getScaleOption();

      new PolarCoord(new ParallelCoord(a, b));

      assertEquals(oa, a.getScaleOption());
      assertEquals(ob, b.getScaleOption());

      MekkoCoord mekko = new MekkoCoord("R", "S");
      Scale[] scales = mekko.getScales();
      int[] options = new int[scales.length];
      boolean[] fills = new boolean[scales.length];

      for(int i = 0; i < scales.length; i++) {
         options[i] = scales[i].getScaleOption();
         fills[i] = scales[i] instanceof CategoricalScale && ((CategoricalScale) scales[i]).isFill();
      }

      new PolarCoord(mekko);

      for(int i = 0; i < scales.length; i++) {
         assertEquals(options[i], scales[i].getScaleOption(), "scale " + i);

         if(scales[i] instanceof CategoricalScale) {
            assertEquals(fills[i], ((CategoricalScale) scales[i]).isFill(), "fill " + i);
         }
      }
   }

   @Test
   @DisplayName("init keeps an explicit user range over the data range")
   void initKeepsUserRange() {
      LinearScale ls = new LinearScale("m");
      PolarCoord c = new PolarCoord(ls);

      ls.setMin(0);
      ls.setMax(100);
      c.init(new inetsoft.graph.data.DefaultDataSet(new Object[][] { { "m" }, { 5.0 }, { 500.0 } }));

      assertEquals(0, ls.getMin(), 0);
      assertEquals(100, ls.getMax(), 0);
   }

   @Test
   @DisplayName("init discards the range of the previous data")
   void initDiscardsPreviousDataRange() {
      LinearScale ls = new LinearScale("m");
      PolarCoord c = new PolarCoord(ls);

      c.init(new inetsoft.graph.data.DefaultDataSet(new Object[][] { { "m" }, { 5.0 }, { 500.0 } }));
      assertTrue(ls.getMax() >= 500);

      c.init(new inetsoft.graph.data.DefaultDataSet(new Object[][] { { "m" }, { 5.0 }, { 50.0 } }));
      assertTrue(ls.getMax() < 100, "max " + ls.getMax());
   }

   // ---- rotate / reflect

   @Test
   @DisplayName("rotate(90) maps the x unit vector to the y unit vector, 360 and a back turn are identity")
   void rotateMatrix() {
      PolarCoord c = thetaRho();

      c.rotate(90);
      Point2D p = c.getCoordTransform().transform(new Point2D.Double(1, 0), null);

      assertPoint(0, 1, p, 1e-9);

      PolarCoord full = thetaRho();

      full.rotate(360);
      assertIdentity(full.getCoordTransform(), 1e-9);

      PolarCoord quarters = thetaRho();

      for(int i = 0; i < 4; i++) {
         quarters.rotate(90);
      }

      assertIdentity(quarters.getCoordTransform(), 1e-9);

      PolarCoord back = thetaRho();

      back.rotate(37);
      back.rotate(-37);
      assertIdentity(back.getCoordTransform(), 1e-9);
   }

   @Test
   @DisplayName("rotate(90) has the sign PolarUtil adds to the tick angles: east maps to the south point")
   void rotateDirection() {
      PolarCoord c = thetaRho();

      c.rotate(90);

      assertEquals(Math.PI / 2, inetsoft.graph.internal.GTool.getRotation(c.getCoordTransform()), 1e-9);
      assertPoint(0, 100, c.getCoordTransform().transform(new Point2D.Double(100, 0), null), 1e-9);
   }

   @Test
   @DisplayName("reflect flips about the horizontal or vertical axis, twice is identity, both is rotate(180)")
   void reflectMatrices() {
      PolarCoord v = thetaRho();
      PolarCoord h = thetaRho();

      v.reflect(true);
      h.reflect(false);

      assertPoint(30, -40, v.getCoordTransform().transform(new Point2D.Double(30, 40), null), 1e-9);
      assertPoint(-30, 40, h.getCoordTransform().transform(new Point2D.Double(30, 40), null), 1e-9);

      v.reflect(true);
      h.reflect(false);
      assertIdentity(v.getCoordTransform(), 1e-9);
      assertIdentity(h.getCoordTransform(), 1e-9);

      PolarCoord both = thetaRho();

      both.reflect(true);
      both.reflect(false);
      assertPoint(-30, -40, both.getCoordTransform().transform(new Point2D.Double(30, 40), null), 1e-9);

      PolarCoord r180 = thetaRho();

      r180.rotate(180);
      assertPoint(-30, -40, r180.getCoordTransform().transform(new Point2D.Double(30, 40), null), 1e-9);
   }

   @Test
   @DisplayName("rotate and reflect do not change transformShape: the flags apply at fit time")
   void rotateReflectDoNotAffectTransformShape() {
      PolarCoord c = thetaRho();

      c.rotate(90);
      c.reflect(true);

      assertPoint(0, 500, pt(c, 250, 1000), EPS);
   }

   // ---- clone / equalsContent

   @Test
   @DisplayName("a clone has its own inner coordinate and scales and no axes; changing it leaves the original")
   void cloneIsIndependent() {
      CategoricalScale cs = new CategoricalScale("Cat");
      LinearScale ls = new LinearScale("m");
      PolarCoord orig = new PolarCoord(new RectCoord(cs, ls));

      orig.setType(PolarCoord.PLUS);
      orig.setHoleRatio(0.4);
      orig.setPieRatio(0.7);

      PolarCoord clone = (PolarCoord) orig.clone();

      assertNotSame(orig.getCoordinate(), clone.getCoordinate());
      assertNotSame(orig.getScales()[0], clone.getScales()[0]);
      assertNotSame(orig.getScales()[1], clone.getScales()[1]);
      assertEquals(0, clone.getAxes(false).length);
      assertEquals(PolarCoord.PLUS, clone.getType());
      assertEquals(0.4, clone.getHoleRatio(), 0);
      assertEquals(0.7, clone.getPieRatio(), 0);

      clone.setType(PolarCoord.THETA);
      clone.setHoleRatio(0.1);
      clone.setPieRatio(0.2);
      ((LinearScale) clone.getScales()[1]).setMax(55);

      assertEquals(PolarCoord.PLUS, orig.getType());
      assertEquals(0.4, orig.getHoleRatio(), 0);
      assertEquals(0.7, orig.getPieRatio(), 0);
      assertNull(ls.getUserMax());
   }

   @Test
   @DisplayName("equalsContent is true for a clone and false for each changed property")
   void equalsContentOfVariants() {
      PolarCoord orig = new PolarCoord(new RectCoord(new CategoricalScale("Cat"), new LinearScale("m")));

      assertTrue(orig.equalsContent(orig.clone()));

      PolarCoord type = (PolarCoord) orig.clone();
      type.setType(PolarCoord.THETA);
      assertFalse(orig.equalsContent(type), "type");

      PolarCoord hole = (PolarCoord) orig.clone();
      hole.setHoleRatio(0.5);
      assertFalse(orig.equalsContent(hole), "hole ratio");

      PolarCoord ratio = (PolarCoord) orig.clone();
      ratio.setPieRatio(0.5);
      assertFalse(orig.equalsContent(ratio), "pie ratio");

      PolarCoord range = (PolarCoord) orig.clone();
      ((LinearScale) range.getScales()[1]).setMax(55);
      assertFalse(orig.equalsContent(range), "scale range");

      PolarCoord rotated = (PolarCoord) orig.clone();
      rotated.rotate(90);
      assertFalse(orig.equalsContent(rotated), "rotation");
   }
}
