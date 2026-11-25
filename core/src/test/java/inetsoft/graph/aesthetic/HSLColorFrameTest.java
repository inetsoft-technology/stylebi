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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.Color;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the base color handling of {@link HSLColorFrame}: the RGB to HSL conversion of the
 * base color, the round trip through the frame, the color layers (user, css, default) and the
 * state of the frame.
 */
@Tag("core")
class HSLColorFrameTest {
   private static final double EPS = 1e-9;

   private static SaturationColorFrame frameOf(Color color) {
      SaturationColorFrame frame = new SaturationColorFrame();
      frame.setUserColor(color);
      return frame;
   }

   private static void assertHsl(double h, double s, double l, double eps, HSLColorFrame frame) {
      assertEquals(h, frame.hue, eps, "hue");
      assertEquals(s, frame.saturation, eps, "saturation");
      assertEquals(l, frame.brightness, eps, "lightness");
   }

   @Test
   void primaryAndSecondaryColorsHaveTheirHue() {
      Color[] colors = { Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN,
                         Color.MAGENTA };
      double[] hues = { 0, 1.0 / 3, 2.0 / 3, 1.0 / 6, 0.5, 5.0 / 6 };

      for(int i = 0; i < colors.length; i++) {
         assertHsl(hues[i], 1, 0.5, EPS, frameOf(colors[i]));
      }
   }

   @Test
   void achromaticColorsHaveNoHueAndNoSaturation() {
      assertHsl(0, 0, 128 / 255.0, EPS, frameOf(new Color(128, 128, 128)));
      assertHsl(0, 0, 1, EPS, frameOf(Color.WHITE));
      assertHsl(0, 0, 0, EPS, frameOf(Color.BLACK));
   }

   @Test
   void darkColorUsesTheDarkSaturationFormula() {
      assertHsl(5.0 / 7, 7.0 / 11, 110.0 / 510, EPS, frameOf(new Color(40, 20, 90)));
   }

   @Test
   void lightColorUsesTheLightSaturationFormula() {
      assertHsl(7.0 / 12, 51.0 / 71, 368.0 / 510, EPS, new SaturationColorFrame());
   }

   @Test
   void negativeHueWrapsAround() {
      SaturationColorFrame frame = frameOf(new Color(255, 0, 128));
      assertEquals(0.91634, frame.hue, 1e-5);
      assertTrue(frame.hue >= 0);
   }

   static Stream<Color> baseColors() {
      return Stream.of(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN,
                       Color.MAGENTA, new Color(255, 0, 128), new Color(255, 128, 0),
                       new Color(10, 200, 30), new Color(40, 20, 90), new Color(133, 184, 235),
                       new Color(128, 128, 128), Color.WHITE, Color.BLACK);
   }

   @ParameterizedTest
   @MethodSource("baseColors")
   void baseColorSurvivesTheRoundTripThroughTheFrame(Color base) {
      BrightnessColorFrame frame = new BrightnessColorFrame();
      frame.setUserColor(base);
      frame.setMinBrightness(frame.brightness);
      frame.setMaxBrightness(frame.brightness);
      Color actual = frame.getColor(0.3);

      assertEquals(base.getRed(), actual.getRed(), 1.0, "red of " + actual);
      assertEquals(base.getGreen(), actual.getGreen(), 1.0, "green of " + actual);
      assertEquals(base.getBlue(), actual.getBlue(), 1.0, "blue of " + actual);
   }

   @Test
   void baseColorLayersUserOverCssOverDefault() {
      SaturationColorFrame frame = new SaturationColorFrame();
      frame.setDefaultColor(Color.BLUE);
      frame.setCssColor(Color.RED);
      frame.setUserColor(Color.GREEN);

      assertEquals(Color.GREEN, frame.getColor());
      assertEquals(1.0 / 3, frame.hue, EPS);

      frame.setUserColor(null);
      assertEquals(Color.RED, frame.getColor());
      assertEquals(0, frame.hue, EPS);

      frame.setCssColor(null);
      assertEquals(Color.BLUE, frame.getColor());
      assertEquals(2.0 / 3, frame.hue, EPS);
   }

   @Test
   void nullParentParametersClearTheCssColor() {
      SaturationColorFrame frame = new SaturationColorFrame();
      frame.setCssColor(Color.RED);
      assertEquals(0, frame.hue, EPS);

      frame.setParentParams(null);
      assertNull(frame.getCssColor());
      assertEquals(new Color(-8013589), frame.getColor());
      assertEquals(7.0 / 12, frame.hue, EPS);
   }

   @Test
   void brightnessAndSaturationFramesShareTheDefaultColor() {
      assertEquals(new Color(133, 184, 235), new BrightnessColorFrame().getColor());
      assertEquals(new Color(133, 184, 235), new SaturationColorFrame().getColor());
   }

   @Test
   void equalsComparesTheColorLayers() {
      SaturationColorFrame a = new SaturationColorFrame();
      SaturationColorFrame b = new SaturationColorFrame();
      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());

      b.setUserColor(Color.RED);
      assertNotEquals(a, b);

      assertNotEquals(new BrightnessColorFrame(), new SaturationColorFrame());
   }

   @Test
   void equalsComparesTheDefaultColor() {
      SaturationColorFrame a = new SaturationColorFrame();
      SaturationColorFrame b = new SaturationColorFrame();
      b.setDefaultColor(Color.RED);
      assertNotEquals(a, b);
   }

   /** Pinned current behavior: equals compares the color layers, not the effective color. */
   @Test
   void pinnedEqualsComparesLayersNotTheEffectiveColor() {
      SaturationColorFrame a = new SaturationColorFrame();
      SaturationColorFrame b = new SaturationColorFrame();
      a.setUserColor(Color.RED);
      b.setDefaultColor(Color.RED);
      assertEquals(a.getColor(), b.getColor());
      assertNotEquals(a, b);
   }

   @Test
   void cloneIsIndependent() {
      BrightnessColorFrame frame = new BrightnessColorFrame();
      BrightnessColorFrame copy = (BrightnessColorFrame) frame.clone();
      assertEquals(frame, copy);
      Color before = frame.getColor(0.5);

      copy.setUserColor(Color.RED);
      assertEquals(before, frame.getColor(0.5));
      assertNotEquals(before, copy.getColor(0.5));
   }
}
