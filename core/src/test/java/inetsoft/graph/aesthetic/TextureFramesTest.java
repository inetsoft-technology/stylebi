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
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The linear texture frames (grid, right tilt, left tilt, orientation) and
 * {@link CategoricalTextureFrame}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class TextureFramesTest {
   private record Linear(String name, Supplier<LinearTextureFrame> make) {
      @Override
      public String toString() {
         return name;
      }
   }

   static Stream<Linear> linearFrames() {
      return Stream.of(
         new Linear("Grid", GridTextureFrame::new),
         new Linear("RightTilt", RightTiltTextureFrame::new),
         new Linear("LeftTilt", LeftTiltTextureFrame::new),
         new Linear("Orientation", OrientationTextureFrame::new));
   }

   private static LinearTextureFrame scaled(Supplier<LinearTextureFrame> make) {
      LinearTextureFrame frame = make.get();
      frame.setScale(new LinearScale(0, 100));
      return frame;
   }

   /** The value path (Object overload), never the ratio overload. */
   private static GTexture byValue(TextureFrame frame, Object value) {
      return frame.getTexture(value);
   }

   private static DefaultDataSet column(String name, Object... values) {
      Object[][] rows = new Object[values.length + 1][];
      rows[0] = new Object[] { name };

      for(int i = 0; i < values.length; i++) {
         rows[i + 1] = new Object[] { values[i] };
      }

      return new DefaultDataSet(rows);
   }

   private static CategoricalTextureFrame frameOn(Object... values) {
      CategoricalTextureFrame frame = new CategoricalTextureFrame("g");
      frame.init(column("g", values));
      return frame;
   }

   private static GTexture[] patterns() {
      return new GTexture[] {
         GTexture.PATTERN_0, GTexture.PATTERN_1, GTexture.PATTERN_2, GTexture.PATTERN_3,
         GTexture.PATTERN_4, GTexture.PATTERN_5, GTexture.PATTERN_6, GTexture.PATTERN_7,
         GTexture.PATTERN_8, GTexture.PATTERN_9, GTexture.PATTERN_10, GTexture.PATTERN_11,
         GTexture.PATTERN_12, GTexture.PATTERN_13, GTexture.PATTERN_14, GTexture.PATTERN_15,
         GTexture.PATTERN_16, GTexture.PATTERN_17, GTexture.PATTERN_18, GTexture.PATTERN_19 };
   }

   // ------------------------------------------------------------- linear

   @Test
   @DisplayName("grid frame: the line gap shrinks from 12 to 2 as the value grows, two lines at 45 degrees")
   void gridGapByRatio() {
      GridTextureFrame frame = new GridTextureFrame();
      frame.setScale(new LinearScale(0, 100));
      int[] values = { 0, 25, 50, 75, 100 };
      int[] gaps = { 12, 9, 7, 4, 2 };

      for(int i = 0; i < values.length; i++) {
         assertEquals(new GTexture(gaps[i], Math.PI / 4, 2), byValue(frame, values[i]),
                      "value " + values[i]);
      }
   }

   @Test
   @DisplayName("right tilt frame: one line at 45 degrees with gap 12, 7, 2")
   void rightTilt() {
      RightTiltTextureFrame frame = new RightTiltTextureFrame();
      frame.setScale(new LinearScale(0, 100));

      assertEquals(new GTexture(12, Math.PI / 4, 1), byValue(frame, 0));
      assertEquals(new GTexture(7, Math.PI / 4, 1), byValue(frame, 50));
      assertEquals(new GTexture(2, Math.PI / 4, 1), byValue(frame, 100));
   }

   @Test
   @DisplayName("left tilt frame: one line at 135 degrees with gap 12, 7, 2")
   void leftTilt() {
      LeftTiltTextureFrame frame = new LeftTiltTextureFrame();
      frame.setScale(new LinearScale(0, 100));

      assertEquals(new GTexture(12, 3 * Math.PI / 4, 1), byValue(frame, 0));
      assertEquals(new GTexture(7, 3 * Math.PI / 4, 1), byValue(frame, 50));
      assertEquals(new GTexture(2, 3 * Math.PI / 4, 1), byValue(frame, 100));
   }

   @Test
   @DisplayName("grid, right tilt and left tilt frames return different textures for one value")
   void tiltSiblingsDiffer() {
      GTexture grid = byValue(scaled(GridTextureFrame::new), 50);
      GTexture right = byValue(scaled(RightTiltTextureFrame::new), 50);
      GTexture left = byValue(scaled(LeftTiltTextureFrame::new), 50);

      assertNotEquals(grid, right);
      assertNotEquals(grid, left);
      assertNotEquals(right, left);
   }

   @Test
   @DisplayName("orientation frame: the line turns from 0 to -90 degrees with a fixed gap 3 and width 1")
   void orientationAngle() {
      OrientationTextureFrame frame = new OrientationTextureFrame();
      frame.setScale(new LinearScale(0, 100));

      assertEquals(new GTexture(3, -Math.PI / 4, 1), byValue(frame, 50));
      assertEquals(new GTexture(3, -Math.PI / 2, 1), byValue(frame, 100));

      for(int v : new int[] { 0, 25, 50, 100 }) {
         assertEquals(3, byValue(frame, v).getLineGap(), "gap at " + v);
         assertEquals(1, byValue(frame, v).getLineWidth(), "width at " + v);
      }
   }

   @Test
   @DisplayName("orientation frame: the ratio 0 textures of two frames, forward and on a reversed scale, are equal")
   void orientationZeroAngle() {
      OrientationTextureFrame forwardA = new OrientationTextureFrame();
      OrientationTextureFrame forwardB = new OrientationTextureFrame();
      forwardA.setScale(new LinearScale(0, 100));
      forwardB.setScale(new LinearScale(0, 100));

      OrientationTextureFrame reversedA = new OrientationTextureFrame();
      OrientationTextureFrame reversedB = new OrientationTextureFrame();
      LinearScale scaleA = new LinearScale(0, 100);
      LinearScale scaleB = new LinearScale(0, 100);
      scaleA.setReversed(true);
      scaleB.setReversed(true);
      reversedA.setScale(scaleA);
      reversedB.setScale(scaleB);

      assertEquals(3, byValue(forwardA, 0).getLineGap());
      assertEquals(1, byValue(forwardA, 0).getLineWidth());
      assertEquals(3, byValue(reversedA, 100).getLineGap());
      assertEquals(1, byValue(reversedA, 100).getLineWidth());
      assertEquals(byValue(forwardA, 0), byValue(forwardB, 0));
      assertEquals(byValue(reversedA, 100), byValue(reversedB, 100));
   }

   @Test
   @DisplayName("linear texture frames: without a scale every value gets the ratio 1 texture")
   void noScale() {
      assertEquals(new GTexture(2, Math.PI / 4, 2), byValue(new GridTextureFrame(), "x"));
      assertEquals(new GTexture(2, Math.PI / 4, 1), byValue(new RightTiltTextureFrame(), "x"));
      assertEquals(new GTexture(2, 3 * Math.PI / 4, 1), byValue(new LeftTiltTextureFrame(), "x"));
      assertEquals(new GTexture(3, -Math.PI / 2, 1), byValue(new OrientationTextureFrame(), "x"));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("linearFrames")
   @DisplayName("linear texture frames: null and non numeric values have no texture")
   void nullAndText(Linear linear) {
      LinearTextureFrame frame = scaled(linear.make());

      assertNull(byValue(frame, null));
      assertNull(byValue(frame, "abc"));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("linearFrames")
   @DisplayName("linear texture frames: a value above the maximum is clamped to ratio 1")
   void aboveMax(Linear linear) {
      LinearTextureFrame frame = scaled(linear.make());

      assertEquals(byValue(frame, 100), byValue(frame, 150));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("linearFrames")
   @DisplayName("linear texture frames: a value below the minimum is clamped to ratio 0")
   void belowMin(Linear linear) {
      LinearTextureFrame frame = scaled(linear.make());

      assertEquals(byValue(frame, 0), byValue(frame, -50));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("linearFrames")
   @DisplayName("linear texture frames: a reversed scale gives each end the texture of the other end of a forward scale")
   void reversed(Linear linear) {
      LinearTextureFrame forward = scaled(linear.make());
      LinearTextureFrame frame = linear.make().get();
      LinearScale scale = new LinearScale(0, 100);
      scale.setReversed(true);
      frame.setScale(scale);

      assertEquals(byValue(forward, 100), byValue(frame, 0));

      if(linear.name().equals("Orientation")) {
         // ratio 0 gives -0.0 forward and 0.0 reversed: same line, compared by gap and width
         assertEquals(byValue(forward, 0).getLineGap(), byValue(frame, 100).getLineGap());
         assertEquals(byValue(forward, 0).getLineWidth(), byValue(frame, 100).getLineWidth());
      }
      else {
         assertEquals(byValue(forward, 0), byValue(frame, 100));
      }
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("linearFrames")
   @DisplayName("linear texture frames: the data set path reads the field of the row")
   void dataSetPath(Linear linear) {
      LinearTextureFrame frame = linear.make().get();
      frame.setField("v");
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "v", "other" }, { 0, 1 }, { 50, 1 }, { 100, 1 } });
      frame.init(data);

      Object[] values = { 0, 50, 100 };

      for(int row = 0; row < values.length; row++) {
         assertEquals(byValue(frame, values[row]), frame.getTexture(data, "other", row),
                      "row " + row);
      }
   }

   // -------------------------------------------------------- categorical

   @Test
   @DisplayName("default palette: patterns 0 to 19 then 0 to 9 again, 30 entries")
   void defaultPalette() {
      CategoricalTextureFrame frame = new CategoricalTextureFrame();
      GTexture[] p = patterns();

      assertEquals(30, frame.getTextureCount());

      for(int i = 0; i < 30; i++) {
         assertSame(p[i % 20], frame.getTexture(i), "index " + i);
      }
   }

   @Test
   @DisplayName("more values than patterns wrap around the palette")
   void wrapAround() {
      Object[] values = new Object[33];

      for(int i = 0; i < values.length; i++) {
         values[i] = "v" + i;
      }

      CategoricalTextureFrame frame = frameOn(values);

      assertSame(GTexture.PATTERN_0, byValue(frame, "v0"));
      assertSame(GTexture.PATTERN_19, byValue(frame, "v19"));
      assertSame(GTexture.PATTERN_0, byValue(frame, "v20"));
      assertSame(GTexture.PATTERN_9, byValue(frame, "v29"));
      assertSame(GTexture.PATTERN_0, byValue(frame, "v30"));
   }

   @Test
   @DisplayName("an explicit assignment wins, is static, and null removes it")
   void explicitAssignment() {
      CategoricalTextureFrame frame = new CategoricalTextureFrame("g");
      frame.setTexture("B", GTexture.PATTERN_7);
      frame.init(column("g", "A", "B", "C"));

      assertSame(GTexture.PATTERN_7, byValue(frame, "B"));
      assertSame(GTexture.PATTERN_0, byValue(frame, "A"));
      assertSame(GTexture.PATTERN_2, byValue(frame, "C"));
      assertTrue(frame.isStatic("B"));

      frame.setTexture("B", null);
      assertFalse(frame.isStatic("B"));
      assertSame(GTexture.PATTERN_1, byValue(frame, "B"));
   }

   @Test
   @DisplayName("an unknown value gets the default texture, null until one is set")
   void unknownValue() {
      CategoricalTextureFrame frame = frameOn("A", "B");

      assertNull(byValue(frame, "Z"));

      frame.setDefaultTexture(GTexture.PATTERN_5);
      assertSame(GTexture.PATTERN_5, byValue(frame, "Z"));
      assertSame(GTexture.PATTERN_5, frame.getDefaultTexture());
   }

   @Test
   @DisplayName("a null value is a category of its own")
   void nullValue() {
      CategoricalTextureFrame frame = frameOn("A", null, "B");

      assertSame(GTexture.PATTERN_1, byValue(frame, null));
      assertSame(GTexture.PATTERN_2, byValue(frame, "B"));
   }

   @Test
   @DisplayName("measure names path: names get the textures given at the same position")
   void measureNames() {
      CategoricalTextureFrame frame = new CategoricalTextureFrame();
      frame.init(new Object[] { "m1", "m2" },
                 new GTexture[] { GTexture.PATTERN_3, GTexture.PATTERN_4 });

      assertSame(GTexture.PATTERN_4, byValue(frame, "m2"));
      assertSame(GTexture.PATTERN_4, frame.getTexture(column("x", "q"), "m2", 0));
   }

   @Test
   @DisplayName("clone is independent of the original in palette and assignments")
   void cloneIndependence() {
      CategoricalTextureFrame original = frameOn("A", "B");
      CategoricalTextureFrame clone = (CategoricalTextureFrame) original.clone();

      assertEquals(original, clone);

      clone.setTexture(0, GTexture.PATTERN_9);
      clone.setTexture("A", GTexture.PATTERN_8);

      assertSame(GTexture.PATTERN_0, original.getTexture(0));
      assertFalse(original.isStatic("A"));
   }

   @Test
   @DisplayName("equals and unique id see palette slots and assignments")
   void equalsAndUniqueId() {
      CategoricalTextureFrame a = new CategoricalTextureFrame("g");
      CategoricalTextureFrame b = new CategoricalTextureFrame("g");

      assertEquals(a, b);
      assertEquals(a.getUniqueId(), b.getUniqueId());

      b.setTexture(0, GTexture.PATTERN_9);
      assertNotEquals(a, b);

      b = new CategoricalTextureFrame("g");
      b.setTexture("A", GTexture.PATTERN_9);
      assertNotEquals(a, b);
      assertNotEquals(a.getUniqueId(), b.getUniqueId());
   }

   @Test
   @DisplayName("without a field the headers are the categories, with a field the row value is used")
   void noFieldAndDataSetPath() {
      CategoricalTextureFrame headers = new CategoricalTextureFrame();
      headers.init(new DefaultDataSet(new Object[][] { { "m1", "m2" }, { 1, 2 } }));

      assertSame(GTexture.PATTERN_0, byValue(headers, "m1"));
      assertSame(GTexture.PATTERN_1, byValue(headers, "m2"));

      CategoricalTextureFrame byField = new CategoricalTextureFrame("g");
      DefaultDataSet data = column("g", "A", "B");
      byField.init(data);

      assertSame(GTexture.PATTERN_1, byField.getTexture(data, "ignored", 1));
   }

   @Test
   @DisplayName("legend values all have textures and the first 20 are pairwise different")
   void legendItems() {
      Object[] values = new Object[20];

      for(int i = 0; i < values.length; i++) {
         values[i] = "v" + i;
      }

      CategoricalTextureFrame frame = frameOn(values);
      Object[] legend = frame.getValues();

      assertEquals(20, legend.length);

      for(int i = 0; i < legend.length; i++) {
         assertNotNull(byValue(frame, legend[i]));

         for(int j = i + 1; j < legend.length; j++) {
            assertNotEquals(byValue(frame, legend[i]), byValue(frame, legend[j]), i + " vs " + j);
         }
      }
   }
}
