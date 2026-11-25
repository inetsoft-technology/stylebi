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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BrightnessColorFrame}: the lightness range applied to the base color, the
 * direction, the alpha range and the interaction with the global brightness factor.
 */
@Tag("core")
class BrightnessColorFrameTest {
   private static void assertRgbWithin1(int r, int g, int b, Color actual) {
      assertEquals(r, actual.getRed(), 1.0, "red of " + actual);
      assertEquals(g, actual.getGreen(), 1.0, "green of " + actual);
      assertEquals(b, actual.getBlue(), 1.0, "blue of " + actual);
   }

   private static double luma(Color c) {
      return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
   }

   @Test
   void middleRatioHasLightnessOneHalf() {
      assertRgbWithin1(35, 127, 219, new BrightnessColorFrame().getColor(0.5));
   }

   @Test
   void endsHaveLightnessTwoTenthsAndEightTenths() {
      BrightnessColorFrame frame = new BrightnessColorFrame();
      assertRgbWithin1(14, 51, 87, frame.getColor(0));
      assertRgbWithin1(167, 204, 240, frame.getColor(1));
   }

   @Test
   void brightnessGrowsOrShrinksWithTheDirection() {
      BrightnessColorFrame frame = new BrightnessColorFrame();

      for(boolean increase : new boolean[] { true, false }) {
         frame.setIncrease(increase);
         double prev = increase ? -1 : 1000;

         for(int i = 0; i <= 10; i++) {
            double l = luma(frame.getColor(i * 0.1));
            assertTrue(increase ? l > prev : l < prev,
                       "increase=" + increase + " step " + i + ": " + prev + " then " + l);
            prev = l;
         }
      }
   }

   @Test
   void decreasingFrameMirrorsTheIncreasingOne() {
      BrightnessColorFrame up = new BrightnessColorFrame();
      BrightnessColorFrame down = new BrightnessColorFrame();
      down.setIncrease(false);

      for(double r : new double[] { 0, 0.25, 0.5, 0.75, 1 }) {
         Color expected = up.getColor(1 - r);
         Color actual = down.getColor(r);
         assertRgbWithin1(expected.getRed(), expected.getGreen(), expected.getBlue(), actual);
      }
   }

   @Test
   void minAndMaxBrightnessBoundTheLightness() {
      BrightnessColorFrame frame = new BrightnessColorFrame();
      frame.setUserColor(new Color(128, 128, 128));
      frame.setMinBrightness(0.1);
      frame.setMaxBrightness(0.9);
      assertRgbWithin1(25, 25, 25, frame.getColor(0));
      assertRgbWithin1(127, 127, 127, frame.getColor(0.5));
      assertRgbWithin1(229, 229, 229, frame.getColor(1));
   }

   @Test
   void hueAndSaturationStayInTheBlueRange() {
      BrightnessColorFrame frame = new BrightnessColorFrame();

      for(int i = 1; i <= 9; i++) {
         Color c = frame.getColor(i * 0.1);
         assertTrue(c.getBlue() >= c.getGreen() && c.getGreen() >= c.getRed(),
                    "not a blue shade at step " + i + ": " + c);
      }
   }

   @Test
   void alphaFollowsTheRatioNotTheBrightness() {
      BrightnessColorFrame frame = new BrightnessColorFrame();
      frame.setMinAlpha(0.2);
      frame.setMaxAlpha(1);
      assertEquals(51, frame.getColor(0).getAlpha(), 1);
      assertEquals(153, frame.getColor(0.5).getAlpha(), 1);
      assertEquals(255, frame.getColor(1).getAlpha(), 1);
   }

   @Test
   void scaleValuesMapLikeTheirRatio() {
      BrightnessColorFrame frame = new BrightnessColorFrame();
      frame.setScale(new LinearScale(0, 100));
      assertEquals(frame.getColor(0), frame.getColor((Object) 0.0));
      assertEquals(frame.getColor(0.5), frame.getColor((Object) 50.0));
      assertEquals(frame.getColor(1), frame.getColor((Object) 100.0));
   }

   @Test
   void equalsComparesMinMaxAndDirection() {
      BrightnessColorFrame a = new BrightnessColorFrame();
      BrightnessColorFrame same = new BrightnessColorFrame();
      assertEquals(a, same);

      BrightnessColorFrame differentMin = new BrightnessColorFrame();
      differentMin.setMinBrightness(0.1);
      assertNotEquals(a, differentMin);

      BrightnessColorFrame differentMax = new BrightnessColorFrame();
      differentMax.setMaxBrightness(0.9);
      assertNotEquals(a, differentMax);

      BrightnessColorFrame differentDirection = new BrightnessColorFrame();
      differentDirection.setIncrease(false);
      assertNotEquals(a, differentDirection);
   }

   @Test
   void globalBrightnessIsAppliedOnTop() {
      BrightnessColorFrame frame = new BrightnessColorFrame();
      frame.setBrightness(0.5);
      assertRgbWithin1(17, 63, 109, frame.getColor(0.5));
   }

   @Test
   void changingTheBaseColorRefreshesTheMapping() {
      BrightnessColorFrame frame = new BrightnessColorFrame();
      frame.getColor(0.5);
      frame.setUserColor(Color.RED);
      assertRgbWithin1(255, 0, 0, frame.getColor(0.5));
   }
}
