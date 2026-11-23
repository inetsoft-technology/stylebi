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
package inetsoft.graph.aesthetic;

import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.scale.LinearScale;
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

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The linear shape frames (oval, triangle, polygon, fill, orientation) and the static shape
 * frame: the shape each sibling returns for a ratio or value, its geometry and the shared
 * handling of missing scale, null, out of range and reversed scales.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class ShapeFramesTest {
   private static final double EPS = 1e-3;

   private record Linear(String name, Supplier<LinearShapeFrame> make) {
      @Override
      public String toString() {
         return name;
      }
   }

   private static LinearShapeFrame scaled(Supplier<LinearShapeFrame> make) {
      LinearShapeFrame frame = make.get();
      frame.setScale(new LinearScale(0, 100));
      return frame;
   }

   private static Stream<Linear> linearFrames() {
      return Stream.of(
         new Linear("Oval", OvalShapeFrame::new),
         new Linear("Triangle", TriangleShapeFrame::new),
         new Linear("Polygon", PolygonShapeFrame::new),
         new Linear("Fill", FillShapeFrame::new),
         new Linear("Orientation", OrientationShapeFrame::new));
   }

   /** The value path (the Object overload), never the ratio overload. */
   private static GShape byValue(ShapeFrame frame, Object value) {
      return frame.getShape(value);
   }

   private static Shape box(GShape shape) {
      return shape.getShape(10, 20, 30, 30);
   }

   private static void assertBounds(Shape shape, double x, double y, double w, double h) {
      Rectangle2D b = shape.getBounds2D();
      assertEquals(x, b.getX(), EPS, "x of " + b);
      assertEquals(y, b.getY(), EPS, "y of " + b);
      assertEquals(w, b.getWidth(), EPS, "width of " + b);
      assertEquals(h, b.getHeight(), EPS, "height of " + b);
   }

   private static int segments(Shape shape) {
      int n = 0;

      for(PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
         n++;
      }

      return n;
   }

   // ---------------------------------------------------------------- Oval

   static Stream<Arguments> ovalRatios() {
      return Stream.of(
         Arguments.of(0.0, 0, 10.0, 20.0, 30.0, 10.0),
         Arguments.of(0.25, 25, 12.5, 20.0, 25.0, 15.0),
         Arguments.of(0.5, 50, 15.0, 20.0, 20.0, 20.0),
         Arguments.of(0.75, 75, 17.5, 20.0, 15.0, 25.0),
         Arguments.of(1.0, 100, 20.0, 20.0, 10.0, 30.0));
   }

   @ParameterizedTest(name = "ratio {0}")
   @MethodSource("ovalRatios")
   @DisplayName("oval frame: ellipse narrows and grows taller with the ratio, by ratio and by value")
   void ovalGeometry(double ratio, int value, double x, double y, double w, double h) {
      OvalShapeFrame frame = new OvalShapeFrame();
      frame.setScale(new LinearScale(0, 100));

      Shape byRatio = box(frame.getShape(ratio));
      Shape byVal = box(byValue(frame, value));

      assertInstanceOf(Ellipse2D.class, byRatio);
      assertBounds(byRatio, x, y, w, h);
      assertBounds(byVal, x, y, w, h);
   }

   // ------------------------------------------------------------ Triangle

   static Stream<Arguments> triangleRatios() {
      return Stream.of(
         Arguments.of(0.0, 10.0, 20.0, 30.0, 30.0),
         Arguments.of(0.25, 13.75, 20.0, 22.5, 30.0),
         Arguments.of(0.5, 17.5, 20.0, 15.0, 30.0),
         Arguments.of(1.0, 10.0, 20.0, 30.0, 30.0));
   }

   @ParameterizedTest(name = "ratio {0}")
   @MethodSource("triangleRatios")
   @DisplayName("triangle frame: bounds of the trapezoid for a ratio (ratio .5 is a rectangle)")
   void triangleBounds(double ratio, double x, double y, double w, double h) {
      assertBounds(box(new TriangleShapeFrame().getShape(ratio)), x, y, w, h);
   }

   @Test
   @DisplayName("triangle frame: ratio 0 has its apex at the bottom")
   void triangleApexAtBottom() {
      Shape shape = box(new TriangleShapeFrame().getShape(0.0));

      assertTrue(shape.contains(25, 45));
      assertTrue(shape.contains(15, 25));
      assertFalse(shape.contains(11, 45));
      assertFalse(shape.contains(15, 45));
   }

   @Test
   @DisplayName("triangle frame: ratio 1 has its apex at the top")
   void triangleApexAtTop() {
      Shape shape = box(new TriangleShapeFrame().getShape(1.0));

      assertTrue(shape.contains(25, 25));
      assertTrue(shape.contains(15, 45));
      assertFalse(shape.contains(11, 25));
      assertFalse(shape.contains(15, 25));
   }

   @Test
   @DisplayName("triangle frame: width is 30*max(r,1-r), height 30, centred on x 25")
   void triangleWidthLaw() {
      TriangleShapeFrame frame = new TriangleShapeFrame();

      for(double r : new double[] { 0, 0.25, 0.5, 0.75, 1 }) {
         Rectangle2D b = box(frame.getShape(r)).getBounds2D();

         assertEquals(30 * Math.max(r, 1 - r), b.getWidth(), EPS, "width at " + r);
         assertEquals(30, b.getHeight(), EPS, "height at " + r);
         assertEquals(25, b.getCenterX(), EPS, "centre at " + r);
      }
   }

   // ------------------------------------------------------------- Polygon

   static Stream<Arguments> polygonRatios() {
      return Stream.of(
         Arguments.of(0.0, 12.0096, 27.5, 25.9808, 22.5, 4),
         Arguments.of(0.25, 10.0, 20.0, 30.0, 30.0, 5),
         Arguments.of(0.5, 12.0096, 20.0, 25.9808, 30.0, 7),
         Arguments.of(0.75, 10.3761, 21.4855, 29.2478, 28.5145, 8),
         Arguments.of(1.0, 10.2279, 20.9046, 29.5442, 29.0954, 10));
   }

   @ParameterizedTest(name = "ratio {0}")
   @MethodSource("polygonRatios")
   @DisplayName("polygon frame: 3 + 6*ratio vertices on the ellipse of the cell")
   void polygonGeometry(double ratio, double x, double y, double w, double h, int segments) {
      Shape shape = box(new PolygonShapeFrame().getShape(ratio));

      assertBounds(shape, x, y, w, h);
      assertEquals(segments, segments(shape));
   }

   @Test
   @DisplayName("polygon frame: segment count never decreases with the ratio, 4 at 0 and 10 at 1")
   void polygonEdgeCountGrows() {
      PolygonShapeFrame frame = new PolygonShapeFrame();
      int previous = 0;

      for(int i = 0; i <= 10; i++) {
         int count = segments(box(frame.getShape(i / 10.0)));

         assertTrue(count >= previous, "segments at " + i / 10.0);
         previous = count;
      }

      assertEquals(4, segments(box(frame.getShape(0.0))));
      assertEquals(10, segments(box(frame.getShape(1.0))));
   }

   // ---------------------------------------------------------------- Fill

   @ParameterizedTest(name = "ratio {0}, arc {1}")
   @MethodSource("fillOutlineCases")
   @DisplayName("fill frame: the outline is the whole circle whatever the ratio, and the shape is filled")
   void fillOutline(double ratio, boolean arc) {
      FillShapeFrame frame = new FillShapeFrame();
      frame.setArc(arc);
      GShape shape = frame.getShape(ratio);

      assertInstanceOf(Ellipse2D.class, box(shape));
      assertBounds(box(shape), 10, 20, 30, 30);
      assertTrue(shape.isFill());
   }

   static Stream<Arguments> fillOutlineCases() {
      return Stream.of(Arguments.of(0.0, false), Arguments.of(1.0, false),
                       Arguments.of(0.0, true), Arguments.of(1.0, true));
   }

   private static BufferedImage paintRed(GShape shape) {
      BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();

      try {
         g.setColor(Color.WHITE);
         g.fillRect(0, 0, 40, 40);
         g.setColor(Color.RED);
         shape.paint(g, 5, 5, 30, 30);
      }
      finally {
         g.dispose();
      }

      return img;
   }

   private static boolean isRed(BufferedImage img, int x, int y) {
      return (img.getRGB(x, y) & 0xFFFFFF) == 0xFF0000;
   }

   @Test
   @DisplayName("fill frame: ratio .25 without arc fills the top quarter of the box")
   void fillPaintsFromTopDown() {
      BufferedImage img = paintRed(new FillShapeFrame().getShape(0.25));

      assertTrue(isRed(img, 20, 8));
      assertFalse(isRed(img, 20, 20));
      assertFalse(isRed(img, 20, 28));
   }

   @Test
   @DisplayName("fill frame: ratio .25 with arc fills the top right quadrant as a pie")
   void fillPaintsArcQuadrant() {
      FillShapeFrame frame = new FillShapeFrame();
      frame.setArc(true);
      BufferedImage img = paintRed(frame.getShape(0.25));

      assertTrue(isRed(img, 28, 12));
      assertFalse(isRed(img, 12, 12));
      assertFalse(isRed(img, 12, 28));
      assertFalse(isRed(img, 28, 28));
   }

   @Test
   @DisplayName("fill frame: arc is off by default and frames with a different arc are not equal")
   void fillArcFlag() {
      FillShapeFrame a = new FillShapeFrame("f");
      FillShapeFrame b = new FillShapeFrame("f");

      assertFalse(a.isArc());
      b.setArc(true);
      assertTrue(b.isArc());
      assertNotEquals(a, b);
   }

   // --------------------------------------------------------- Orientation

   private static void assertLine(Shape shape, double x1, double y1, double x2, double y2) {
      Line2D line = assertInstanceOf(Line2D.class, shape);

      assertEquals(x1, line.getX1(), EPS);
      assertEquals(y1, line.getY1(), EPS);
      assertEquals(x2, line.getX2(), EPS);
      assertEquals(y2, line.getY2(), EPS);
   }

   @Test
   @DisplayName("orientation frame: ratio 0 is horizontal")
   void orientationHorizontal() {
      assertLine(box(new OrientationShapeFrame().getShape(0.0)), 10, 35, 40, 35);
   }

   @Test
   @DisplayName("orientation frame: ratio .5 is a 45 degree line")
   void orientationDiagonal() {
      assertLine(box(new OrientationShapeFrame().getShape(0.5)),
                 14.3934, 45.6066, 35.6066, 24.3934);
   }

   @Test
   @DisplayName("orientation frame: ratio 1 is vertical")
   void orientationVertical() {
      assertLine(box(new OrientationShapeFrame().getShape(1.0)), 25, 50, 25, 20);
   }

   // ---------------------------------------------- shared linear behaviour

   @Test
   @DisplayName("linear shape frames: the five siblings return pairwise different shapes")
   void siblingsAreDifferent() {
      List<GShape> shapes = linearFrames().map(l -> l.make().get().getShape(0.5)).toList();

      for(int i = 0; i < shapes.size(); i++) {
         for(int j = i + 1; j < shapes.size(); j++) {
            assertNotEquals(shapes.get(i), shapes.get(j), i + " vs " + j);
         }
      }
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("linearFrames")
   @DisplayName("linear shape frames: without a scale every value gets the ratio 1 shape")
   void noScaleGivesFullRatio(Linear linear) {
      LinearShapeFrame frame = linear.make().get();

      assertEquals(frame.getShape(1.0), byValue(frame, "x"));
   }

   @Test
   @DisplayName("oval frame: without a scale the shape is the ratio 1 ellipse")
   void ovalNoScale() {
      assertBounds(box(byValue(new OvalShapeFrame(), "x")), 20, 20, 10, 30);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("linearFrames")
   @DisplayName("linear shape frames: null and non numeric values have no shape")
   void nullAndTextHaveNoShape(Linear linear) {
      LinearShapeFrame frame = scaled(linear.make());

      assertNull(byValue(frame, null));
      assertNull(byValue(frame, "abc"));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("linearFrames")
   @DisplayName("linear shape frames: a value above the maximum is clamped to ratio 1")
   void aboveMaxIsClamped(Linear linear) {
      LinearShapeFrame frame = scaled(linear.make());

      assertEquals(byValue(frame, 100), byValue(frame, 150));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("linearFrames")
   @DisplayName("linear shape frames: a value below the minimum is clamped to ratio 0")
   void belowMinIsClamped(Linear linear) {
      LinearShapeFrame frame = scaled(linear.make());

      assertEquals(byValue(frame, 0), byValue(frame, -50));
   }

   @Test
   @DisplayName("polygon frame: a value far below the minimum still yields a drawable polygon")
   void polygonFarBelowMin() {
      PolygonShapeFrame frame = new PolygonShapeFrame();
      frame.setScale(new LinearScale(0, 100));
      GShape shape = byValue(frame, -50);

      assertNotNull(shape);
      assertDoesNotThrow(() -> box(shape));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("linearFrames")
   @DisplayName("linear shape frames: a reversed scale swaps the ratio 0 and ratio 1 shapes")
   void reversedScaleSwapsEnds(Linear linear) {
      LinearShapeFrame frame = linear.make().get();
      LinearScale scale = new LinearScale(0, 100);
      scale.setReversed(true);
      frame.setScale(scale);

      assertEquals(frame.getShape(1.0), byValue(frame, 0));
      assertEquals(frame.getShape(0.0), byValue(frame, 100));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("linearFrames")
   @DisplayName("linear shape frames: the data set path reads the field of the row and ignores the column")
   void dataSetPath(Linear linear) {
      LinearShapeFrame frame = linear.make().get();
      frame.setField("v");
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "v", "other" }, { 0, 1 }, { 50, 1 }, { 100, 1 } });
      frame.init(data);

      Object[] values = { 0, 50, 100 };

      for(int row = 0; row < values.length; row++) {
         assertEquals(byValue(frame, values[row]), frame.getShape(data, "other", row), "row " + row);
         assertEquals(byValue(frame, values[row]), frame.getShape(data, "v", row), "row " + row);
      }
   }

   private record FlagFrame(String name, Supplier<VisualFrame> make, Consumer<VisualFrame> flip) {
      @Override
      public String toString() {
         return name;
      }
   }

   static Stream<FlagFrame> flagFrames() {
      return Stream.of(
         new FlagFrame("Oval fill", () -> new OvalShapeFrame("f"),
                       f -> ((OvalShapeFrame) f).setFill(true)),
         new FlagFrame("Triangle fill", () -> new TriangleShapeFrame("f"),
                       f -> ((TriangleShapeFrame) f).setFill(true)),
         new FlagFrame("Polygon fill", () -> new PolygonShapeFrame("f"),
                       f -> ((PolygonShapeFrame) f).setFill(true)),
         new FlagFrame("Fill arc", () -> new FillShapeFrame("f"),
                       f -> ((FillShapeFrame) f).setArc(true)));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("flagFrames")
   @DisplayName("linear shape frames: equal when the configuration is the same, unequal when the flag differs")
   void equalsFollowsFlag(FlagFrame flag) {
      VisualFrame a = flag.make().get();
      VisualFrame b = flag.make().get();

      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());

      flag.flip().accept(b);
      assertNotEquals(a, b);
   }

   @Test
   @DisplayName("linear shape frames: frames of different classes are never equal")
   void differentClassesAreNotEqual() {
      assertNotEquals(new OvalShapeFrame("f"), new TriangleShapeFrame("f"));
   }

   // -------------------------------------------------------------- Static

   @Test
   @DisplayName("static shape frame: every value gets the filled circle by default")
   void staticDefault() {
      StaticShapeFrame frame = new StaticShapeFrame();

      assertSame(GShape.FILLED_CIRCLE, frame.getShape((Object) "x"));
      assertSame(GShape.FILLED_CIRCLE, frame.getShape((Object) 5));
      assertSame(GShape.FILLED_CIRCLE, frame.getShape((Object) (-1)));
      assertSame(GShape.FILLED_CIRCLE, frame.getShape((Object) null));
   }

   @Test
   @DisplayName("static shape frame: only negative numbers get the negative shape")
   void staticNegativeShape() {
      StaticShapeFrame frame = new StaticShapeFrame();
      frame.setNegativeShape(GShape.STAR);

      assertSame(GShape.STAR, frame.getShape((Object) (-1)));
      assertSame(GShape.FILLED_CIRCLE, frame.getShape((Object) 0));
      assertSame(GShape.FILLED_CIRCLE, frame.getShape((Object) 5));
      assertSame(GShape.FILLED_CIRCLE, frame.getShape((Object) Double.NaN));
      assertSame(GShape.FILLED_CIRCLE, frame.getShape((Object) "s"));
   }

   @Test
   @DisplayName("static shape frame: with a field the value names a GShape constant or is the shape")
   void staticShapeByName() {
      StaticShapeFrame frame = new StaticShapeFrame("f");

      assertSame(GShape.STAR, frame.getShape((Object) "STAR"));
      assertSame(GShape.CROSS, frame.getShape((Object) GShape.CROSS));
      assertSame(GShape.FILLED_CIRCLE, frame.getShape((Object) "NOPE"));
   }

   @Test
   @DisplayName("static shape frame: the data set path reads the field, and uses the column without a field")
   void staticDataSetPath() {
      DefaultDataSet data = new DefaultDataSet(new Object[][] { { "s" }, { "STAR" }, { "CIRCLE" } });
      StaticShapeFrame withField = new StaticShapeFrame("s");

      assertSame(GShape.STAR, withField.getShape(data, "ignored", 0));
      assertSame(GShape.CIRCLE, withField.getShape(data, "ignored", 1));
      assertSame(GShape.FILLED_CIRCLE, new StaticShapeFrame().getShape(data, "STAR", 0));
   }

   @Test
   @DisplayName("static shape frame: frames of CIRCLE and FILLED_CIRCLE differ in equals and in unique id")
   void staticLegendIdentity() {
      StaticShapeFrame outline = new StaticShapeFrame(GShape.CIRCLE);
      StaticShapeFrame filled = new StaticShapeFrame(GShape.FILLED_CIRCLE);

      assertNotEquals(outline, filled);
      assertNotEquals(outline.getUniqueId(), filled.getUniqueId());
   }

   // ----------------------------------------------------- minimum sizes

   @Test
   @DisplayName("pinned: minimum size of the shape of every linear and multi field shape frame")
   void minSizes() {
      assertEquals(8, new OvalShapeFrame().getShape(0.5).getMinSize());
      assertEquals(8, new TriangleShapeFrame().getShape(0.5).getMinSize());
      assertEquals(8, new PolygonShapeFrame().getShape(0.5).getMinSize());
      assertEquals(4, new FillShapeFrame().getShape(0.5).getMinSize());
      assertEquals(4, new OrientationShapeFrame().getShape(0.5).getMinSize());

      double[] three = { 1, 1, 1 };

      assertEquals(15, new BarShapeFrame().getShape(three).getMinSize());
      assertEquals(15, new ProfileShapeFrame().getShape(three).getMinSize());
      assertEquals(32, new StarShapeFrame().getShape(three).getMinSize());
      assertEquals(8, new ThermoShapeFrame().getShape(three).getMinSize());
      assertEquals(16, new PieShapeFrame().getShape(three).getMinSize());
      assertEquals(20, new SunShapeFrame().getShape(three).getMinSize());
      assertEquals(24, new VineShapeFrame().getShape(three).getMinSize());
   }

   // ------------------------------------------------------ legend

   @ParameterizedTest(name = "{0}")
   @MethodSource("linearFrames")
   @DisplayName("linear shape frames: the legend is visible with no field")
   void linearFramesAreVisible(Linear linear) {
      assertTrue(linear.make().get().isVisible());
   }

   @Test
   @DisplayName("categorical shape frame: visible with two different shapes, hidden when all shapes are alike")
   void categoricalVisibility() {
      CategoricalShapeFrame different = new CategoricalShapeFrame();
      different.init("A", "B");
      assertTrue(different.isVisible());

      CategoricalShapeFrame alike = new CategoricalShapeFrame();
      alike.init(new Object[] { "A", "B" }, new GShape[] { GShape.CIRCLE });
      assertFalse(alike.isVisible());
   }
}
