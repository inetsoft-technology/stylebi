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
package inetsoft.graph.visual;

import inetsoft.graph.element.GraphElement;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Shape;
import java.awt.geom.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link Pie3DVO}: slice extents, contiguity, ellipse ratio, bounds, shape cache, explosion,
 * hit test and labels of a 3D pie plotted through {@code Plotter.plotAndLayout}, and the
 * {@code BarVO.explode} arithmetic.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class Pie3DVOTest {
   private static final double EPS = 1e-6;

   private static Arc2D arc(Shape shape) {
      Arc2D arc = BarVO.getOuterArc(shape);
      assertNotNull(arc, "not an arc or ellipse: " + shape);
      return arc;
   }

   private static double extent(Shape shape) {
      return Math.abs(arc(shape).getAngleExtent());
   }

   private static double[] extents(Pie3DVO vo) {
      Shape[] shapes = vo.getShapes();
      double[] result = new double[shapes.length];

      for(int i = 0; i < shapes.length; i++) {
         result[i] = extent(shapes[i]);
      }

      return result;
   }

   private static double mod360(double angle) {
      double a = angle % 360;
      return a < 0 ? a + 360 : a;
   }

   // ---------------------------------------------------------------- slice extents

   @Test
   @DisplayName("slices of 10, 30 and 60 span 36, 108 and 216 degrees")
   void sliceExtents() {
      double[] extents = extents(new Pie3DChartFixture().plot().pie());

      assertArrayEquals(new double[] { 36, 108, 216 }, extents, EPS);
   }

   static Stream<Arguments> valueSets() {
      return Stream.of(
         Arguments.of((Object) new Object[] { 1.0, 1.0, 1.0 }),
         Arguments.of((Object) new Object[] { 5.0, 0.0, 5.0 }),
         Arguments.of((Object) new Object[] { 1.0, 2.0, 3.0, 4.0 }),
         Arguments.of((Object) new Object[] { 7.0 }));
   }

   @ParameterizedTest
   @MethodSource("valueSets")
   @DisplayName("slice extents are proportional to the values and sum to 360")
   void extentsProportionalAndSumTo360(Object[] values) {
      double[] extents = extents(new Pie3DChartFixture().values(values).plot().pie());
      double total = 0;
      double sum = 0;

      for(Object value : values) {
         total += (Double) value;
      }

      assertEquals(values.length, extents.length);

      for(int i = 0; i < values.length; i++) {
         assertEquals((Double) values[i] / total * 360, extents[i], EPS, "slice " + i);
         sum += extents[i];
      }

      assertEquals(360, sum, EPS);
   }

   @Test
   @DisplayName("each slice starts where the previous one ends, in the same direction")
   void slicesContiguous() {
      Shape[] shapes = new Pie3DChartFixture().plot().pie().getShapes();

      assertEquals(3, shapes.length);

      for(int i = 0; i + 1 < shapes.length; i++) {
         Arc2D cur = arc(shapes[i]);
         Arc2D next = arc(shapes[i + 1]);
         double end = cur.getAngleStart() + cur.getAngleExtent();

         assertEquals(0, mod360(next.getAngleStart() - end + 180) - 180, EPS, "slice " + i);
         assertEquals(Math.signum(cur.getAngleExtent()), Math.signum(next.getAngleExtent()));
      }
   }

   @Test
   @DisplayName("every slice is an ellipse of height width/2 and all slices share one frame")
   void ellipseRatio() {
      Shape[] shapes = new Pie3DChartFixture().plot().pie().getShapes();
      Arc2D first = arc(shapes[0]);

      assertEquals(3, shapes.length);

      for(Shape shape : shapes) {
         Arc2D arc = arc(shape);

         assertEquals(arc.getWidth() / 2, arc.getHeight(), EPS);
         assertEquals(first.getX(), arc.getX(), EPS);
         assertEquals(first.getY(), arc.getY(), EPS);
         assertEquals(first.getWidth(), arc.getWidth(), EPS);
         assertEquals(first.getHeight(), arc.getHeight(), EPS);
      }
   }

   @Test
   @DisplayName("a zero value gives a zero-degree slice; the others share 360")
   void zeroValue() {
      double[] extents = extents(new Pie3DChartFixture().values(0.0, 40.0, 60.0).plot().pie());

      assertArrayEquals(new double[] { 0, 144, 216 }, extents, EPS);
   }

   @Test
   @DisplayName("a null value draws no slice; the sub row indexes skip it")
   void nullValue() {
      Pie3DVO vo = new Pie3DChartFixture().values(10.0, null, 60.0).plot().pie();
      double[] extents = extents(vo);

      assertEquals(2, extents.length);
      assertEquals(10.0 / 70 * 360, extents[0], 1e-4);
      assertEquals(60.0 / 70 * 360, extents[1], 1e-4);
      assertArrayEquals(new int[] { 0, 2 }, vo.getSubRowIndexes());
   }

   @Test
   @DisplayName("a negative value counts by its absolute value")
   void negativeValue() {
      double[] extents = extents(new Pie3DChartFixture().values(10.0, -30.0, 60.0).plot().pie());

      assertArrayEquals(new double[] { 36, 108, 216 }, extents, EPS);
   }

   @Test
   @DisplayName("all-zero data does not throw: every extent is 0 and all bounds are finite")
   void allZero() {
      Pie3DChartFixture fixture = new Pie3DChartFixture().values(0.0, 0.0, 0.0);

      assertDoesNotThrow(fixture::plot);

      Pie3DVO vo = fixture.pie();

      for(double extent : extents(vo)) {
         assertEquals(0, extent, EPS);
      }

      for(Shape shape : vo.getShapes()) {
         Rectangle2D bounds = shape.getBounds2D();

         assertTrue(Double.isFinite(bounds.getX()) && Double.isFinite(bounds.getY())
                       && Double.isFinite(bounds.getWidth()) && Double.isFinite(bounds.getHeight()));
      }

      assertNotNull(vo.getBounds());
   }

   @Test
   @DisplayName("header-only data plots and paints without error; null or empty bounds are tolerated")
   void emptyData() {
      Pie3DChartFixture fixture = new Pie3DChartFixture();
      fixture.emptyData = true;

      assertDoesNotThrow(fixture::plot);

      for(Pie3DVO vo : fixture.pies()) {
         assertDoesNotThrow(() -> {
            vo.getBounds();
            java.awt.image.BufferedImage img =
               new java.awt.image.BufferedImage(600, 400, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            vo.paint(g);
            g.dispose();
         });
      }
   }

   @Test
   @DisplayName("a single row is one slice covering 360 degrees")
   void singleSlice() {
      Shape[] shapes = new Pie3DChartFixture().values(5.0).plot().pie().getShapes();

      assertEquals(1, shapes.length);
      assertEquals(360, extent(shapes[0]), EPS);
   }

   // ---------------------------------------------------------------- bounds and cache

   @Disabled("Bug #82054: Pie3DVO.getBounds returns only the first slice")
   @Test
   @DisplayName("getBounds contains the bounds of every slice")
   void boundsContainEverySlice() {
      Pie3DVO vo = new Pie3DChartFixture().plot().pie();
      Rectangle2D bounds = vo.getBounds();

      assertNotNull(bounds);

      for(Shape shape : vo.getShapes()) {
         Rectangle2D slice = shape.getBounds2D();

         assertTrue(bounds.getX() <= slice.getX() + EPS && bounds.getY() <= slice.getY() + EPS
                       && bounds.getMaxX() >= slice.getMaxX() - EPS
                       && bounds.getMaxY() >= slice.getMaxY() - EPS,
                    "bounds " + bounds + " do not contain slice " + slice);
      }
   }

   @Disabled("Bug #82054: Pie3DVO.getBounds returns only the first slice")
   @Test
   @DisplayName("getBounds equals the union of the slice bounds")
   void boundsEqualUnion() {
      Pie3DVO vo = new Pie3DChartFixture().plot().pie();
      Rectangle2D union = null;

      for(Shape shape : vo.getShapes()) {
         union = union == null ? shape.getBounds2D() : union.createUnion(shape.getBounds2D());
      }

      Rectangle2D bounds = vo.getBounds();

      assertEquals(union.getX(), bounds.getX(), EPS);
      assertEquals(union.getY(), bounds.getY(), EPS);
      assertEquals(union.getWidth(), bounds.getWidth(), EPS);
      assertEquals(union.getHeight(), bounds.getHeight(), EPS);
   }

   @Test
   @DisplayName("replacing the screen transform by a doubled one doubles every shape")
   void shapesFollowScreenTransform() {
      Pie3DVO vo = new Pie3DChartFixture().plot().pie();
      Shape[] before = vo.getShapes();
      Rectangle2D[] boxes = new Rectangle2D[before.length];

      for(int i = 0; i < before.length; i++) {
         boxes[i] = before[i].getBounds2D();
      }

      AffineTransform doubled = AffineTransform.getScaleInstance(2, 2);
      doubled.concatenate(vo.getScreenTransform());
      vo.setScreenTransform(doubled);

      Shape[] after = vo.getShapes();

      assertEquals(before.length, after.length);

      for(int i = 0; i < after.length; i++) {
         Rectangle2D box = after[i].getBounds2D();

         assertEquals(2 * boxes[i].getX(), box.getX(), 1e-4, "x " + i);
         assertEquals(2 * boxes[i].getY(), box.getY(), 1e-4, "y " + i);
         assertEquals(2 * boxes[i].getWidth(), box.getWidth(), 1e-4, "w " + i);
         assertEquals(2 * boxes[i].getHeight(), box.getHeight(), 1e-4, "h " + i);
      }
   }

   // ---------------------------------------------------------------- explode arithmetic

   private static ElementVO explodedHint(String hint) {
      ElementVO vo = mock(ElementVO.class);
      when(vo.getHint(GraphElement.HINT_EXPLODED)).thenReturn(hint);
      return vo;
   }

   @Test
   @DisplayName("exploding a quarter slice shrinks it by 1/20 and moves it along the middle angle")
   void explodeQuarter() {
      Arc2D arc = new Arc2D.Double(-100, -50, 200, 100, 0, -90, Arc2D.PIE);
      Arc2D result = (Arc2D) BarVO.explode(arc, explodedHint("true"), null);

      assertEquals(-91.4645, result.getX(), 1e-4);
      assertEquals(-45.7322, result.getY(), 1e-4);
      assertEquals(190, result.getWidth(), EPS);
      assertEquals(95, result.getHeight(), EPS);
      assertEquals(0, result.getAngleStart(), EPS);
      assertEquals(-90, result.getAngleExtent(), EPS);
      assertEquals(Arc2D.PIE, result.getArcType());
   }

   @Test
   @DisplayName("exploding a half slice moves it straight left")
   void explodeHalf() {
      Arc2D arc = new Arc2D.Double(-100, -50, 200, 100, -90, -180, Arc2D.PIE);
      Arc2D result = (Arc2D) BarVO.explode(arc, explodedHint("true"), null);

      assertEquals(-100, result.getX(), 1e-4);
      assertEquals(-47.5, result.getY(), 1e-4);
      assertEquals(190, result.getWidth(), EPS);
      assertEquals(95, result.getHeight(), EPS);
      assertEquals(-90, result.getAngleStart(), EPS);
      assertEquals(-180, result.getAngleExtent(), EPS);
   }

   @Test
   @DisplayName("a full circle, a missing hint and a false hint are returned unchanged")
   void explodeUnchanged() {
      Arc2D full = new Arc2D.Double(-100, -50, 200, 100, 0, -360, Arc2D.PIE);
      Arc2D quarter = new Arc2D.Double(-100, -50, 200, 100, 0, -90, Arc2D.PIE);

      assertSame(full, BarVO.explode(full, explodedHint("true"), null));
      assertSame(quarter, BarVO.explode(quarter, explodedHint(null), null));
      assertSame(quarter, BarVO.explode(quarter, explodedHint("false"), null));
   }

   // ---------------------------------------------------------------- exploded chart

   @Test
   @DisplayName("an exploded pie has slices 0.95 of the plain size, inside the plain ellipse, "
      + "moved radius/20 along the middle angle")
   void explodedChart() {
      Shape[] plain = new Pie3DChartFixture().plot().pie().getShapes();
      Pie3DChartFixture fixture = new Pie3DChartFixture();
      fixture.exploded = true;
      Shape[] exploded = fixture.plot().pie().getShapes();

      assertEquals(plain.length, exploded.length);

      for(int i = 0; i < plain.length; i++) {
         Arc2D p = arc(plain[i]);
         Arc2D e = arc(exploded[i]);
         double r = p.getWidth() / 2;
         double angle = Math.toRadians(p.getAngleStart() + p.getAngleExtent() / 2);
         double dx = e.getCenterX() - p.getCenterX();
         double dy = e.getCenterY() - p.getCenterY();

         assertEquals(0.95 * p.getWidth(), e.getWidth(), 1e-4, "width " + i);
         assertEquals(0.95 * p.getHeight(), e.getHeight(), 1e-4, "height " + i);
         assertEquals(r / 20 * Math.abs(Math.cos(angle)), Math.abs(dx), 1e-4, "dx " + i);
         assertEquals(r / 20 * (p.getHeight() / p.getWidth()) * Math.abs(Math.sin(angle)),
                      Math.abs(dy), 1e-4, "dy " + i);

         Rectangle2D frame = new Rectangle2D.Double(p.getX(), p.getY(), p.getWidth(), p.getHeight());
         Rectangle2D box = exploded[i].getBounds2D();

         assertTrue(frame.getX() - 1e-4 <= box.getX() && frame.getY() - 1e-4 <= box.getY()
                       && box.getMaxX() <= frame.getMaxX() + 1e-4
                       && box.getMaxY() <= frame.getMaxY() + 1e-4, "inside plain ellipse " + i);
      }
   }

   // ---------------------------------------------------------------- hit test and labels

   @Test
   @DisplayName("slices do not overlap and their areas add up to the ellipse")
   void slicesDisjoint() {
      Shape[] shapes = new Pie3DChartFixture().plot().pie().getShapes();
      Arc2D first = arc(shapes[0]);
      double ellipse = Math.PI * first.getWidth() / 2 * first.getHeight() / 2;
      double sum = 0;

      for(int i = 0; i < shapes.length; i++) {
         sum += area(new Area(shapes[i]));

         for(int j = i + 1; j < shapes.length; j++) {
            Area overlap = new Area(shapes[i]);
            overlap.intersect(new Area(shapes[j]));

            assertTrue(area(overlap) < ellipse * 1e-3, "slices " + i + " and " + j + " overlap");
         }
      }

      assertEquals(ellipse, sum, ellipse * 0.01);
   }

   /** Area of a polygonal Area by the shoelace formula. */
   private static double area(Area area) {
      double[] c = new double[6];
      double total = 0;
      double sx = 0, sy = 0, px = 0, py = 0;

      for(PathIterator it = area.getPathIterator(null, 0.1); !it.isDone(); it.next()) {
         int type = it.currentSegment(c);

         if(type == PathIterator.SEG_MOVETO) {
            px = sx = c[0];
            py = sy = c[1];
         }
         else if(type == PathIterator.SEG_LINETO) {
            total += px * c[1] - c[0] * py;
            px = c[0];
            py = c[1];
         }
         else if(type == PathIterator.SEG_CLOSE) {
            total += px * sy - sx * py;
            px = sx;
            py = sy;
         }
      }

      return Math.abs(total) / 2;
   }

   @Test
   @DisplayName("a point at half radius on the middle angle of slice i is in slice i only")
   void hitByPoint() {
      Shape[] shapes = new Pie3DChartFixture().plot().pie().getShapes();

      for(int i = 0; i < shapes.length; i++) {
         Arc2D arc = arc(shapes[i]);
         double angle = Math.toRadians(arc.getAngleStart() + arc.getAngleExtent() / 2);
         double x = arc.getCenterX() + arc.getWidth() / 4 * Math.cos(angle);
         double y = arc.getCenterY() - arc.getHeight() / 4 * Math.sin(angle);

         for(int j = 0; j < shapes.length; j++) {
            assertEquals(i == j, shapes[j].contains(x, y), "point of slice " + i + " in " + j);
         }
      }
   }

   @Test
   @DisplayName("every label centre lies outside the pie ellipse")
   void labelsOutside() {
      Pie3DVO vo = new Pie3DChartFixture().labels("L").plot().pie();
      Arc2D arc = arc(vo.getShapes()[0]);
      VOText[] texts = vo.getVOTexts();
      int found = 0;

      for(VOText text : texts) {
         assertNotNull(text);
         found++;

         Rectangle2D box = text.getBounds();
         double nx = (box.getCenterX() - arc.getCenterX()) / (arc.getWidth() / 2);
         double ny = (box.getCenterY() - arc.getCenterY()) / (arc.getHeight() / 2);

         assertTrue(nx * nx + ny * ny > 1, "label " + text.getLabel() + " centre " + box);
      }

      assertEquals(3, found);
   }
}
