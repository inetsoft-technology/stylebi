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
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.math.BigDecimal;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the value to color mapping that every {@link LinearColorFrame} shares: the ratio of a
 * value on the scale, the clamping at the ends, reversed scales, the alpha range, and the
 * equality of frames.
 */
@Tag("core")
class LinearColorFrameTest {
   /** Gradient from black to (200,100,40): at ratio r the color is (200r, 100r, 40r). */
   private static GradientColorFrame grx() {
      GradientColorFrame frame = new GradientColorFrame();
      frame.setFromColor(new Color(0, 0, 0));
      frame.setToColor(new Color(200, 100, 40));
      return frame;
   }

   /** A scale with user min 0 and max 100. */
   private static LinearScale s100(boolean reversed) {
      LinearScale scale = new LinearScale(0, 100);

      if(reversed) {
         scale.setReversed(true);
      }

      return scale;
   }

   private static GradientColorFrame grx100(boolean reversed) {
      GradientColorFrame frame = grx();
      LinearScale scale = s100(reversed);
      frame.setScale(scale);

      if(reversed) {
         assertEquals(100, scale.getMin(), 0, "fixture: reversed scale min");
         assertEquals(0, scale.getMax(), 0, "fixture: reversed scale max");
      }
      else {
         assertEquals(0, scale.getMin(), 0, "fixture: scale min");
         assertEquals(100, scale.getMax(), 0, "fixture: scale max");
      }

      return frame;
   }

   private static void assertRgb(int r, int g, int b, Color actual) {
      assertNotNull(actual);
      assertEquals(r, actual.getRed(), "red of " + actual);
      assertEquals(g, actual.getGreen(), "green of " + actual);
      assertEquals(b, actual.getBlue(), "blue of " + actual);
   }

   private static DefaultDataSet dataOf(double... values) {
      Object[][] rows = new Object[values.length + 1][];
      rows[0] = new Object[] { "v" };

      for(int i = 0; i < values.length; i++) {
         rows[i + 1] = new Object[] { values[i] };
      }

      return new DefaultDataSet(rows);
   }

   @Test
   void valuesMapToLinearRatioOfTheScale() {
      GradientColorFrame frame = grx100(false);
      assertRgb(0, 0, 0, frame.getColor((Object) 0.0));
      assertRgb(50, 25, 10, frame.getColor((Object) 25.0));
      assertRgb(100, 50, 20, frame.getColor((Object) 50.0));
      assertRgb(150, 75, 30, frame.getColor((Object) 75.0));
      assertRgb(200, 100, 40, frame.getColor((Object) 100.0));
   }

   @ParameterizedTest
   @ValueSource(doubles = { -10, -1e9 })
   void valueBelowMinimumGetsTheMinimumColor(double v) {
      assertRgb(0, 0, 0, grx100(false).getColor((Object) v));
   }

   @ParameterizedTest
   @ValueSource(doubles = { 110, 1e9 })
   void valueAboveMaximumGetsTheMaximumColor(double v) {
      assertRgb(200, 100, 40, grx100(false).getColor((Object) v));
   }

   @Test
   void reversedScaleMirrorsTheColors() {
      GradientColorFrame normal = grx100(false);
      GradientColorFrame reversed = grx100(true);

      for(int v = 0; v <= 100; v += 10) {
         Color expected = normal.getColor((Object) (double) (100 - v));
         assertEquals(expected, reversed.getColor((Object) (double) v), "reversed at " + v);
      }

      assertRgb(0, 0, 0, reversed.getColor((Object) 100.0));
   }

   @Test
   void reversedScaleClampsBeyondTheEnds() {
      GradientColorFrame reversed = grx100(true);
      assertRgb(0, 0, 0, reversed.getColor((Object) 150.0));
      assertRgb(200, 100, 40, reversed.getColor((Object) (-50.0)));
   }

   @Test
   void nonNumericValuesGetNoColor() {
      GradientColorFrame frame = grx100(false);
      assertNull(frame.getColor((Object) null), "null");
      assertNull(frame.getColor((Object) Double.NaN), "NaN");
      assertNull(frame.getColor((Object) "abc"), "string");
      assertNull(frame.getColor((Object) Boolean.TRUE), "boolean");
   }

   @Test
   void nanWithGrayscaleGetsGreyOfTheDefaultColor() {
      GradientColorFrame frame = grx100(false);
      frame.setGrayscale(true);
      assertRgb(127, 127, 127, frame.getColor((Object) Double.NaN));
   }

   @Test
   void infinitiesGetTheEndColors() {
      GradientColorFrame frame = grx100(false);
      assertEquals(frame.getColor(1.0), frame.getColor((Object) Double.POSITIVE_INFINITY));
      assertEquals(frame.getColor(0.0), frame.getColor((Object) Double.NEGATIVE_INFINITY));
   }

   /**
    * Pinned current behavior: a scale with min equal to max gives a NaN ratio. The gradient
    * answers opaque black, the heat frame transparent black. LinearScale.init never produces
    * such a scale, so only a script-built scale reaches it.
    */
   @Test
   void pinnedZeroRangeScaleGivesBlack() {
      GradientColorFrame gradient = new GradientColorFrame();
      gradient.setScale(new LinearScale(5, 5));
      assertEquals(new Color(0, 0, 0, 255), gradient.getColor((Object) 5.0));

      HeatColorFrame heat = new HeatColorFrame();
      heat.setScale(new LinearScale(5, 5));
      Color c = heat.getColor((Object) 5.0);
      assertRgb(0, 0, 0, c);
      assertEquals(0, c.getAlpha());
   }

   /** Pinned current behavior: a frame without a scale answers the color of ratio one. */
   @Test
   void pinnedFrameWithoutScaleUsesTheMaximumColor() {
      GradientColorFrame frame = grx();
      assertRgb(200, 100, 40, frame.getColor((Object) "x"));
      assertRgb(200, 100, 40, frame.getColor((Object) 5));
   }

   @Test
   void dataSetRowsMapIgnoringTheColumnArgument() {
      GradientColorFrame frame = grx100(false);
      frame.setField("v");
      DefaultDataSet data = dataOf(0, 50, 100);

      for(String col : new String[] { "x", "y" }) {
         assertRgb(0, 0, 0, frame.getColor(data, col, 0));
         assertRgb(100, 50, 20, frame.getColor(data, col, 1));
         assertRgb(200, 100, 40, frame.getColor(data, col, 2));
      }
   }

   @Test
   void nullCellGetsNoColor() {
      GradientColorFrame frame = grx100(false);
      frame.setField("v");
      DefaultDataSet data = new DefaultDataSet(new Object[][] { { "v" }, { null } });
      assertNull(frame.getColor(data, "v", 0));
   }

   @Test
   void initializedScaleMapsValuesByItsOwnRange() {
      GradientColorFrame frame = grx();
      frame.setField("v");
      frame.init(dataOf(3, 17, 42, 88));
      LinearScale scale = (LinearScale) frame.getScale();
      double min = scale.getMin();
      double max = scale.getMax();
      assertTrue(max > min);

      for(double v : new double[] { 3, 17, 42, 88 }) {
         assertEquals(frame.getColor((v - min) / (max - min)), frame.getColor((Object) v),
                      "value " + v);
      }

      assertNotEquals(frame.getColor((Object) 3.0), frame.getColor((Object) 88.0));
   }

   @Test
   void initializedScaleOfZeroToHundredMapsByHand() {
      GradientColorFrame frame = grx();
      frame.setField("v");
      frame.init(dataOf(0, 50, 100));
      assertRgb(0, 0, 0, frame.getColor((Object) 0.0));
      assertRgb(100, 50, 20, frame.getColor((Object) 50.0));
      assertRgb(200, 100, 40, frame.getColor((Object) 100.0));
   }

   @Test
   void secondInitKeepsTheFirstScale() {
      GradientColorFrame frame = grx();
      frame.setField("v");
      frame.init(dataOf(0, 50, 100));
      LinearScale scale = (LinearScale) frame.getScale();
      double max = scale.getMax();
      assertEquals(100, max, 0);

      frame.init(dataOf(0, 500, 1000));
      assertSame(scale, frame.getScale());
      assertEquals(max, ((LinearScale) frame.getScale()).getMax(), 0);
   }

   @Test
   void alphaInterpolatesBetweenMinAndMaxAlpha() {
      GradientColorFrame frame = grx();
      frame.setMinAlpha(0.2);
      frame.setMaxAlpha(1);
      assertEquals(0.2, frame.getAlpha(0), 1e-9);
      assertEquals(0.6, frame.getAlpha(0.5), 1e-9);
      assertEquals(1.0, frame.getAlpha(1), 1e-9);
   }

   @Test
   void alphaRangeMayDecrease() {
      GradientColorFrame frame = grx();
      frame.setMinAlpha(1);
      frame.setMaxAlpha(0);
      assertEquals(0.75, frame.getAlpha(0.25), 1e-9);
   }

   @Test
   void legendVisibilityFollowsTheLegendSpec() {
      GradientColorFrame frame = grx();
      assertTrue(frame.isVisible());
      frame.getLegendSpec().setVisible(false);
      assertFalse(frame.isVisible());
   }

   @Test
   void equalFramesHaveEqualHashCodes() {
      GradientColorFrame a = grx();
      GradientColorFrame b = grx();
      a.setField("v");
      b.setField("v");
      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());

      b.setField("w");
      assertNotEquals(a, b);

      HeatColorFrame heat = new HeatColorFrame("v");
      assertNotEquals(a, heat);
      assertNotEquals(heat, a);
   }

   @Test
   void equalsIgnoresBrightnessButNotGrayscale() {
      GradientColorFrame a = grx();
      GradientColorFrame b = grx();
      b.setBrightness(0.5);
      assertEquals(a, b);

      b.setBrightness(1);
      b.setGrayscale(true);
      assertNotEquals(a, b);
   }

   /** Pinned current behavior: the alpha range is not part of equals. */
   @Test
   void pinnedEqualsIgnoresTheAlphaRange() {
      HeatColorFrame a = new HeatColorFrame();
      HeatColorFrame b = new HeatColorFrame();
      b.setMinAlpha(0.2);
      assertEquals(a, b);
   }

   static Stream<Supplier<LinearColorFrame>> allFamilies() {
      return Stream.of(GradientColorFrame::new, HeatColorFrame::new, BrightnessColorFrame::new,
                       SaturationColorFrame::new, BluesColorFrame::new);
   }

   @ParameterizedTest
   @MethodSource("allFamilies")
   void everyFamilyClampsOutOfRangeValues(Supplier<LinearColorFrame> factory) {
      LinearColorFrame frame = factory.get();
      frame.setScale(s100(false));
      assertEquals(frame.getColor((Object) 100.0), frame.getColor((Object) 150.0), "above");
      assertEquals(frame.getColor((Object) 0.0), frame.getColor((Object) (-50.0)), "below");
   }

   @ParameterizedTest
   @MethodSource("numbers")
   void numberTypesMapTheSame(Number value) {
      assertRgb(100, 50, 20, grx100(false).getColor((Object) value));
   }

   static Stream<Number> numbers() {
      return Stream.of(50, 50L, 50f, 50.0, new BigDecimal("50"));
   }
}
