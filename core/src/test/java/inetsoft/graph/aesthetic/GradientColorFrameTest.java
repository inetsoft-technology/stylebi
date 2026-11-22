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

import inetsoft.graph.scale.LinearScale;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the two color interpolation of {@link GradientColorFrame}: the default colors, exact
 * grid values, the from and to color layers (user, css, default) and the state of the frame.
 */
@Tag("core")
class GradientColorFrameTest {
   private static final double[] GRID = { 0, 0.25, 0.5, 0.75, 1 };

   /** Gradient from black to (200,100,40). */
   private static GradientColorFrame grx() {
      GradientColorFrame frame = new GradientColorFrame();
      frame.setFromColor(new Color(0, 0, 0));
      frame.setToColor(new Color(200, 100, 40));
      return frame;
   }

   private static void assertRgb(int r, int g, int b, Color actual) {
      assertNotNull(actual);
      assertEquals(r, actual.getRed(), "red of " + actual);
      assertEquals(g, actual.getGreen(), "green of " + actual);
      assertEquals(b, actual.getBlue(), "blue of " + actual);
   }

   private static void assertRgbWithin1(double r, double g, double b, Color actual) {
      assertEquals(r, actual.getRed(), 1.0, "red of " + actual);
      assertEquals(g, actual.getGreen(), 1.0, "green of " + actual);
      assertEquals(b, actual.getBlue(), 1.0, "blue of " + actual);
   }

   @Test
   void defaultColorsAreTheEnds() {
      GradientColorFrame frame = new GradientColorFrame();
      assertRgb(255, 153, 204, frame.getColor(0));
      assertRgb(0, 128, 0, frame.getColor(1));
   }

   @Test
   void defaultColorsMiddleIsTheMidpoint() {
      assertRgbWithin1(127.5, 140.5, 102, new GradientColorFrame().getColor(0.5));
   }

   @Test
   void exactGridValues() {
      GradientColorFrame frame = grx();
      assertRgb(0, 0, 0, frame.getColor(0));
      assertRgb(50, 25, 10, frame.getColor(0.25));
      assertRgb(100, 50, 20, frame.getColor(0.5));
      assertRgb(150, 75, 30, frame.getColor(0.75));
      assertRgb(200, 100, 40, frame.getColor(1));
   }

   @Test
   void swappedColorsMirrorEachOther() {
      GradientColorFrame a = grx();
      GradientColorFrame b = new GradientColorFrame();
      b.setFromColor(new Color(200, 100, 40));
      b.setToColor(new Color(0, 0, 0));

      for(double r : GRID) {
         assertEquals(a.getColor(r), b.getColor(1 - r), "ratio " + r);
      }
   }

   @Test
   void channelsGrowSteadilyFromBlackToWhite() {
      GradientColorFrame frame = new GradientColorFrame();
      frame.setFromColor(Color.BLACK);
      frame.setToColor(Color.WHITE);
      Color prev = frame.getColor(0);

      for(int i = 1; i <= 20; i++) {
         Color c = frame.getColor(i * 0.05);
         assertTrue(c.getRed() >= prev.getRed() && c.getGreen() >= prev.getGreen() &&
                       c.getBlue() >= prev.getBlue(), "channels decrease at step " + i);
         // 255 * 0.05 = 12.75 per step, truncated: a step changes a channel by 12 or 13
         int step = c.getRed() - prev.getRed();
         assertTrue(step == 12 || step == 13, "step " + i + " changes red by " + step);
         prev = c;
      }
   }

   @Test
   void settingFromColorRefreshesTheMapping() {
      GradientColorFrame frame = grx();
      frame.getColor(0.5);
      frame.setFromColor(new Color(100, 100, 100));
      assertRgb(100, 100, 100, frame.getColor(0));
   }

   @Test
   void settingToColorRefreshesTheMapping() {
      GradientColorFrame frame = grx();
      frame.getColor(0.5);
      frame.setToColor(new Color(100, 100, 100));
      assertRgb(100, 100, 100, frame.getColor(1));
   }

   @Test
   void fromColorLayersUserOverCssOverDefault() {
      Color c1 = new Color(10, 20, 30);
      Color c2 = new Color(40, 50, 60);
      Color c3 = new Color(70, 80, 90);
      GradientColorFrame frame = new GradientColorFrame();
      frame.setCssFromColor(c1);
      frame.setUserFromColor(c2);
      frame.setDefaultFromColor(c3);

      assertEquals(c2, frame.getFromColor());
      assertRgb(40, 50, 60, frame.getColor(0));

      frame.setUserFromColor(null);
      assertEquals(c1, frame.getFromColor());
      assertRgb(10, 20, 30, frame.getColor(0));

      frame.setCssFromColor(null);
      assertEquals(c3, frame.getFromColor());
      assertRgb(70, 80, 90, frame.getColor(0));
   }

   @Test
   void toColorLayersUserOverCssOverDefault() {
      Color c1 = new Color(10, 20, 30);
      Color c2 = new Color(40, 50, 60);
      Color c3 = new Color(70, 80, 90);
      GradientColorFrame frame = new GradientColorFrame();
      frame.setCssToColor(c1);
      frame.setUserToColor(c2);
      frame.setDefaultToColor(c3);

      assertEquals(c2, frame.getToColor());
      assertRgb(40, 50, 60, frame.getColor(1));

      frame.setUserToColor(null);
      assertEquals(c1, frame.getToColor());
      assertRgb(10, 20, 30, frame.getColor(1));

      frame.setCssToColor(null);
      assertEquals(c3, frame.getToColor());
      assertRgb(70, 80, 90, frame.getColor(1));
   }

   @Test
   void defaultColorChangeDoesNotOverrideTheUserColor() {
      GradientColorFrame frame = new GradientColorFrame();
      frame.setDefaultFromColor(Color.BLUE);
      assertRgb(0, 0, 255, frame.getColor(0));

      frame.setUserFromColor(Color.RED);
      assertRgb(255, 0, 0, frame.getColor(0));

      frame.setDefaultFromColor(Color.GREEN);
      assertRgb(255, 0, 0, frame.getColor(0));
      assertEquals(Color.GREEN, frame.getDefaultFromColor());
   }

   @Disabled("Bug #82044: Contour chart: a Gradient colour ignores the plot alpha")
   @Test
   void alphaRangeIsApplied() {
      GradientColorFrame frame = grx();
      frame.setMinAlpha(0.2);
      frame.setMaxAlpha(1);
      assertEquals(51, frame.getColor(0).getAlpha());
      assertEquals(153, frame.getColor(0.5).getAlpha());
      assertEquals(255, frame.getColor(1).getAlpha());
   }

   @Disabled("Bug #82044: Contour chart: a Gradient colour ignores the plot alpha")
   @Test
   void alphaOfTheTwoColorsIsInterpolated() {
      GradientColorFrame frame = new GradientColorFrame();
      frame.setFromColor(new Color(255, 0, 0, 0));
      frame.setToColor(new Color(255, 0, 0, 255));
      assertEquals(0, frame.getColor(0).getAlpha());
      assertEquals(127, frame.getColor(0.5).getAlpha());
      assertEquals(255, frame.getColor(1).getAlpha());
   }

   @Test
   void grayscaleUsesTheLuminanceOfTheColor() {
      GradientColorFrame frame = new GradientColorFrame();
      frame.setGrayscale(true);
      assertRgb(189, 189, 189, frame.getColor(0));
   }

   @Test
   void equalityFollowsFromAndToColors() {
      GradientColorFrame a = grx();
      GradientColorFrame b = grx();
      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());

      b.setToColor(new Color(1, 2, 3));
      assertNotEquals(a, b);
   }

   /** Pinned current behavior: equals compares the color layers, not the effective color. */
   @Test
   void pinnedEqualsComparesLayersNotTheEffectiveColor() {
      GradientColorFrame a = new GradientColorFrame();
      GradientColorFrame b = new GradientColorFrame();
      a.setUserFromColor(Color.RED);
      b.setDefaultFromColor(Color.RED);
      assertEquals(a.getFromColor(), b.getFromColor());
      assertNotEquals(a, b);
   }

   @Test
   void defaultAlphaIsOpaqueAtEveryRatio() {
      for(GradientColorFrame frame : new GradientColorFrame[] { new GradientColorFrame(), grx() }) {
         for(double r : new double[] { 0, 0.25, 0.5, 1 }) {
            assertEquals(255, frame.getColor(r).getAlpha(), "ratio " + r);
         }
      }
   }

   @Test
   void cloneIsIndependent() {
      GradientColorFrame frame = grx();
      GradientColorFrame copy = (GradientColorFrame) frame.clone();
      assertEquals(frame, copy);
      assertEquals(frame.getColor(0.5), copy.getColor(0.5));

      copy.setFromColor(Color.RED);
      assertRgb(0, 0, 0, frame.getColor(0));
      assertRgb(255, 0, 0, copy.getColor(0));
   }

   @Test
   void brightnessScalesEachChannel() {
      GradientColorFrame frame = new GradientColorFrame();
      frame.setBrightness(0.5);
      assertRgb(127, 76, 102, frame.getColor(0));
   }

   @Test
   void interpolationStaysWithinOneOfTheExactLinearValue() {
      GradientColorFrame frame = new GradientColorFrame();
      frame.setFromColor(new Color(3, 250, 77));
      frame.setToColor(new Color(240, 9, 200));

      for(int i = 1; i <= 9; i++) {
         double r = i / 10.0;
         assertRgbWithin1(3 + 237 * r, 250 - 241 * r, 77 + 123 * r, frame.getColor(r));
      }
   }

   @Test
   void scaleValueMapsLikeItsRatio() {
      GradientColorFrame frame = new GradientColorFrame();
      frame.setScale(new LinearScale(0, 100));
      assertEquals(frame.getColor(0.5), frame.getColor((Object) 50.0));
   }
}
