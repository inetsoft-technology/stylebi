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
import inetsoft.graph.scale.Scale;
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
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The multi field shape frames (star, sun, thermo, bar, pie, vine, profile): the geometry each
 * sibling builds from a tuple of normalised values, and the shared handling of fields, scales,
 * legend tuples, validity and equality in {@link MultiShapeFrame}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class MultiShapeFrameTest {
   private static final double EPS = 1e-3;

   /** The tuple overload ({@code double...}), never the Object overload. */
   private static GShape tuple(MultiShapeFrame frame, double... values) {
      return frame.getShape(values);
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

   private static void assertColorAt(BufferedImage img, int x, int y, Color color) {
      assertEquals(color.getRGB() & 0xFFFFFF, img.getRGB(x, y) & 0xFFFFFF,
                   "pixel (" + x + "," + y + ")");
   }

   private static void assertNotColorAt(BufferedImage img, int x, int y, Color color) {
      assertNotEquals(color.getRGB() & 0xFFFFFF, img.getRGB(x, y) & 0xFFFFFF,
                      "pixel (" + x + "," + y + ")");
   }

   private static BufferedImage paint(GShape shape) {
      BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();

      try {
         g.setColor(Color.WHITE);
         g.fillRect(0, 0, 40, 40);
         shape.paint(g, 5, 5, 30, 30);
      }
      finally {
         g.dispose();
      }

      return img;
   }

   private static PieShapeFrame pie(String... fields) {
      PieShapeFrame frame = new PieShapeFrame(fields);
      CategoricalColorFrame colors = new CategoricalColorFrame();
      colors.setColor("a", Color.RED);
      colors.setColor("b", Color.BLUE);
      frame.setColorFrame(colors);
      return frame;
   }

   private static LinearScale[] scales(int n, double max) {
      LinearScale[] scales = new LinearScale[n];

      for(int i = 0; i < n; i++) {
         scales[i] = new LinearScale(0, max);
      }

      return scales;
   }

   // ---------------------------------------------------------------- Star

   @Test
   @DisplayName("star frame: three full values make a triangle star with its first point on the east")
   void starThreeValues() {
      assertBounds(box(tuple(new StarShapeFrame(), 1, 1, 1)), 17.5, 22.0096, 22.5, 25.9808);
   }

   @Test
   @DisplayName("star frame: four full values make a diamond filling the cell")
   void starFourValues() {
      assertBounds(box(tuple(new StarShapeFrame(), 1, 1, 1, 1)), 10, 20, 30, 30);
   }

   @Test
   @DisplayName("star frame: the legend item of a field raises the zero values to .2")
   void starLegendTuple() {
      StarShapeFrame frame = new StarShapeFrame("a", "b", "c");
      frame.setScales(scales(3, 10));

      assertBounds(box(frame.getShape((Object) "a")), 23.5, 32.4019, 16.5, 5.1962);
   }

   // ----------------------------------------------------------------- Sun

   @Test
   @DisplayName("sun frame: four full values reach the cell edge in four directions")
   void sunFullValues() {
      assertBounds(box(tuple(new SunShapeFrame(), 1, 1, 1, 1)), 10, 20, 30, 30);
   }

   @Test
   @DisplayName("sun frame: zero values keep a stem of length 2")
   void sunZeroValues() {
      assertBounds(box(tuple(new SunShapeFrame(), 0, 0, 0, 0)), 23, 33, 4, 4);
   }

   @Test
   @DisplayName("sun frame: values (1,0) give a long stem to the east and a short one to the west")
   void sunTwoValues() {
      assertBounds(box(tuple(new SunShapeFrame(), 1, 0)), 23, 35, 17, 0);
   }

   // -------------------------------------------------------------- Thermo

   @Test
   @DisplayName("thermo frame: one value uses the default width ratio .6")
   void thermoOneValue() {
      Shape shape = box(tuple(new ThermoShapeFrame(), 0.5));

      assertInstanceOf(Rectangle2D.class, shape);
      assertBounds(shape, 15.2, 20, 19.6, 30);
   }

   @Test
   @DisplayName("thermo frame: the second value sets the width, the first only the fill level")
   void thermoTwoValues() {
      ThermoShapeFrame frame = new ThermoShapeFrame();

      assertBounds(box(tuple(frame, 1, 1)), 10, 20, 30, 30);
      assertBounds(box(tuple(frame, 0.3, 0)), 23, 20, 4, 30);
   }

   // ----------------------------------------------------------------- Bar

   @Test
   @DisplayName("bar frame: bar heights are the values times the cell height")
   void barHeights() {
      BarShapeFrame frame = new BarShapeFrame();

      assertBounds(box(tuple(frame, 1, 0.5)), 10, 20, 30, 30);
      assertBounds(box(tuple(frame, 0.5, 0.5)), 10, 20, 30, 15);
   }

   @Test
   @DisplayName("bar frame: n full bars fill the cell width for any n")
   void barWidthLaw() {
      BarShapeFrame frame = new BarShapeFrame();

      for(int n = 1; n <= 5; n++) {
         double[] values = new double[n];
         java.util.Arrays.fill(values, 1);

         assertBounds(box(tuple(frame, values)), 10, 20, 30, 30);
      }
   }

   @Test
   @DisplayName("bar frame: a NaN value skips its bar, all NaN leaves an empty path")
   void barNaN() {
      BarShapeFrame frame = new BarShapeFrame();

      assertBounds(box(tuple(frame, Double.NaN, 1)), 25, 20, 15, 30);
      assertBounds(box(tuple(frame, Double.NaN, Double.NaN)), 0, 0, 0, 0);
   }

   // ----------------------------------------------------------------- Pie

   @Test
   @DisplayName("pie frame: the outline is the square of the shorter side of the cell")
   void pieOutline() {
      Shape shape = tuple(new PieShapeFrame(), 1, 1).getShape(10, 20, 40, 30);

      assertInstanceOf(Rectangle2D.class, shape);
      assertBounds(shape, 15, 20, 30, 30);
   }

   @Test
   @DisplayName("pie frame: two equal slices, the first is the lower half")
   void pieEqualSlices() {
      BufferedImage img = paint(tuple(pie("a", "b"), 1, 1));

      assertColorAt(img, 20, 30, Color.RED);
      assertColorAt(img, 20, 10, Color.BLUE);
   }

   @Test
   @DisplayName("pie frame: values (3,1) leave the top right quadrant to the second slice")
   void pieUnequalSlices() {
      BufferedImage img = paint(tuple(pie("a", "b"), 3, 1));

      assertColorAt(img, 12, 12, Color.RED);
      assertColorAt(img, 12, 28, Color.RED);
      assertColorAt(img, 28, 12, Color.BLUE);
      assertColorAt(img, 28, 28, Color.RED);
   }

   @Test
   @DisplayName("pie frame: a NaN value has no slice and the last value fills the whole disc")
   void pieNaNValue() {
      BufferedImage img = paint(tuple(pie("a", "b"), Double.NaN, 1));

      assertColorAt(img, 20, 20, Color.BLUE);
      assertColorAt(img, 12, 12, Color.BLUE);
      assertColorAt(img, 28, 28, Color.BLUE);
      assertNotColorAt(img, 12, 12, Color.RED);
   }

   @Test
   @DisplayName("pie frame: a donut leaves the centre unpainted, a pie paints it")
   void pieDonut() {
      PieShapeFrame frame = pie("a");

      BufferedImage solid = paint(tuple(frame, 1));
      assertColorAt(solid, 20, 20, Color.RED);

      frame.setDonut(true);
      BufferedImage donut = paint(tuple(frame, 1));
      assertNotColorAt(donut, 20, 20, Color.RED);
      assertColorAt(donut, 20, 8, Color.RED);
   }

   // ---------------------------------------------------------------- Vine

   @ParameterizedTest(name = "direction {0}, length {1}")
   @MethodSource("vineStems")
   @DisplayName("vine frame: the stem direction runs 0 to 90 degrees and its length 2 to the radius")
   void vineStem(double direction, double length, double x, double y, double w, double h) {
      assertBounds(box(tuple(new VineShapeFrame(), direction, length)), x, y, w, h);
   }

   static Stream<Arguments> vineStems() {
      return Stream.of(
         Arguments.of(1.0, 1.0, 25.0, 35.0, 0.0, 15.0),
         Arguments.of(0.0, 1.0, 25.0, 35.0, 15.0, 0.0),
         Arguments.of(0.0, 0.0, 25.0, 35.0, 2.0, 0.0),
         Arguments.of(0.5, 0.5, 25.0, 35.0, 6.0104, 6.0104));
   }

   @ParameterizedTest(name = "direction {0}")
   @MethodSource("vineStartAngleStems")
   @DisplayName("vine frame: with a start angle of 30 the stem angle is 30 + 60*direction degrees")
   void vineStartAngle(double direction, double x, double y, double w, double h) {
      VineShapeFrame frame = new VineShapeFrame();
      frame.setStartAngle(30);
      frame.setEndAngle(90);

      assertBounds(box(tuple(frame, direction, 1)), x, y, w, h);
   }

   static Stream<Arguments> vineStartAngleStems() {
      return Stream.of(
         Arguments.of(0.0, 25.0, 35.0, 12.9904, 7.5),
         Arguments.of(1.0, 25.0, 35.0, 0.0, 15.0),
         Arguments.of(0.5, 25.0, 35.0, 7.5, 12.9904));
   }

   @Test
   @DisplayName("vine frame: a start and end angle of 0 and 180 point direction .5 straight down")
   void vineAngleRange() {
      VineShapeFrame frame = new VineShapeFrame();
      frame.setStartAngle(0);
      frame.setEndAngle(180);

      assertEquals(0, frame.getStartAngle());
      assertEquals(180, frame.getEndAngle());
      assertBounds(box(tuple(frame, 0.5, 1)), 25, 35, 0, 15);
   }

   // ------------------------------------------------------------- Profile

   @Test
   @DisplayName("profile frame: the outline follows the values over the cell width")
   void profileOutline() {
      ProfileShapeFrame frame = new ProfileShapeFrame();

      assertBounds(box(tuple(frame, 0, 1, 0.5)), 10, 20, 30, 30);
      assertBounds(box(tuple(frame, 0.5, 0.5, 0.5)), 10, 20, 30, 15);
   }

   // ------------------------------------------------- MultiShapeFrame base

   @Test
   @DisplayName("multi shape frame: setField creates the field list, then replaces its first entry")
   void setFieldCreatesAndReplaces() {
      StarShapeFrame frame = new StarShapeFrame();

      frame.setField("a");
      assertArrayEquals(new String[] { "a" }, frame.getFields());

      frame.setField("z");
      assertArrayEquals(new String[] { "z" }, frame.getFields());
      assertEquals("z", frame.getField());
   }

   @Test
   @DisplayName("multi shape frame: without scales getShape(value) returns null")
   void noScalesGivesNull() {
      assertNull(new StarShapeFrame("a", "b").getShape((Object) "a"));
   }

   @Test
   @DisplayName("multi shape frame: the legend tuple of a field is 1 for it and 0 for the others")
   void legendTuple() {
      SunShapeFrame frame = new SunShapeFrame("a", "b");
      frame.setScales(scales(2, 10));

      assertBounds(box(frame.getShape((Object) "a")), 23, 35, 17, 0);
      assertBounds(box(frame.getShape((Object) "b")), 10, 35, 17, 0);
   }

   @Test
   @DisplayName("multi shape frame: the data set path normalises each field with its scale")
   void dataSetPath() {
      SunShapeFrame frame = new SunShapeFrame("a", "b");
      frame.setScales(scales(2, 10));
      DefaultDataSet data = new DefaultDataSet(new Object[][] { { "a", "b" }, { 10, 0 } });

      assertBounds(box(frame.getShape(data, "ignored", 0)), 23, 35, 17, 0);
      assertEquals(tuple(frame, 1, 0), frame.getShape(data, "ignored", 0));
   }

   @Test
   @DisplayName("vine frame: every field has its own scale")
   void perFieldScales() {
      VineShapeFrame frame = new VineShapeFrame("a", "b");
      frame.setScales(new LinearScale(0, 10), new LinearScale(0, 100));
      DefaultDataSet data = new DefaultDataSet(new Object[][] { { "a", "b" }, { 10, 10 } });

      assertBounds(frame.getShape(data, "ignored", 0).getShape(10, 20, 30, 30), 25, 35, 0, 3.3);
   }

   private static DefaultDataSet sharedScaleData() {
      return new DefaultDataSet(new Object[][] {
         { "a", "b" }, { 0, 0 }, { 10, 100 }, { 10, 10 } });
   }

   @Test
   @DisplayName("sun frame shares one scale across its fields, vine frame does not")
   void sharedScale() {
      SunShapeFrame sun = new SunShapeFrame("a", "b");
      sun.init(sharedScaleData());
      Scale[] sunScales = sun.getScales();

      assertSame(sunScales[0], sunScales[1]);
      assertBounds(sun.getShape(sharedScaleData(), "ignored", 2).getShape(10, 20, 30, 30),
                   21.7, 35, 6.6, 0);

      VineShapeFrame vine = new VineShapeFrame("a", "b");
      vine.init(sharedScaleData());
      Scale[] vineScales = vine.getScales();

      assertNotSame(vineScales[0], vineScales[1]);
   }

   @Test
   @DisplayName("multi shape frame: invalid before init, valid after, invalid again with an uninitialised scale")
   void validity() {
      StarShapeFrame frame = new StarShapeFrame("a", "b");
      assertFalse(frame.isValid());

      frame.init(sharedScaleData());
      assertTrue(frame.isValid());

      Scale good = frame.getScales()[0];
      frame.setScales(good, new LinearScale());
      assertFalse(frame.isValid());
   }

   @Test
   @DisplayName("multi shape frame: the values are the fields, and a null cell leaves out only its bar")
   void valuesAndNullCell() {
      BarShapeFrame frame = new BarShapeFrame("a", "b");
      frame.setScales(scales(2, 10));
      DefaultDataSet data = new DefaultDataSet(new Object[][] { { "a", "b" }, { 0, 0 }, { 10, null } });

      assertArrayEquals(new String[] { "a", "b" }, frame.getValues());
      assertBounds(box(frame.getShape(data, "ignored", 1)), 10, 20, 15, 30);
   }

   @Test
   @DisplayName("multi shape frames: equal with the same class and fields, unequal otherwise")
   void equalsClassAndFields() {
      StarShapeFrame a = new StarShapeFrame("a", "b");

      assertEquals(a, new StarShapeFrame("a", "b"));
      assertEquals(a.hashCode(), new StarShapeFrame("a", "b").hashCode());
      assertNotEquals(a, new StarShapeFrame("a", "c"));
      assertNotEquals(a, new SunShapeFrame("a", "b"));
   }

   @Test
   @DisplayName("bar frame: frames with a different colour frame are not equal")
   void barEqualsSeesColorFrame() {
      BarShapeFrame a = new BarShapeFrame("a", "b");
      BarShapeFrame b = new BarShapeFrame("a", "b");
      assertEquals(a, b);

      CategoricalColorFrame colors = new CategoricalColorFrame();
      colors.setColor("a", Color.RED);
      b.setColorFrame(colors);
      assertNotEquals(a, b);
   }

   @Test
   @DisplayName("vine frame: frames with different angles are not equal")
   void vineEqualsSeesAngles() {
      VineShapeFrame a = new VineShapeFrame("a", "b");
      VineShapeFrame b = new VineShapeFrame("a", "b");
      assertEquals(a, b);

      b.setStartAngle(10);
      assertNotEquals(a, b);
   }

   @Test
   @DisplayName("pie frame: frames that differ in donut are not equal")
   void pieEqualsSeesDonut() {
      PieShapeFrame a = new PieShapeFrame("a", "b");
      PieShapeFrame b = new PieShapeFrame("a", "b");
      assertEquals(a, b);

      b.setDonut(true);
      assertNotEquals(a, b);
   }

   @Test
   @DisplayName("multi shape frames: the seven siblings return pairwise different shapes for the same values")
   void siblingsAreDifferent() {
      List<MultiShapeFrame> frames = List.of(
         new StarShapeFrame("a", "b", "c"), new SunShapeFrame("a", "b", "c"),
         new BarShapeFrame("a", "b", "c"), new PieShapeFrame("a", "b", "c"),
         new ThermoShapeFrame("a", "b", "c"), new ProfileShapeFrame("a", "b", "c"),
         new VineShapeFrame("a", "b", "c"));

      for(int i = 0; i < frames.size(); i++) {
         for(int j = i + 1; j < frames.size(); j++) {
            GShape x = tuple(frames.get(i), 1, 0.5, 0.25);
            GShape y = tuple(frames.get(j), 1, 0.5, 0.25);

            assertNotEquals(x, y, frames.get(i).getClass().getSimpleName() + " vs " +
               frames.get(j).getClass().getSimpleName());
         }
      }
   }
}
