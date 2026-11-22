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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the brightness and grayscale adjustment that {@link ColorFrame#process} applies to
 * the color of every frame, driven through a {@link StaticColorFrame}.
 */
@Tag("core")
class ColorFrameProcessTest {
   private static void assertArgb(int r, int g, int b, int a, Color actual) {
      assertNotNull(actual);
      assertEquals(r, actual.getRed(), "red of " + actual);
      assertEquals(g, actual.getGreen(), "green of " + actual);
      assertEquals(b, actual.getBlue(), "blue of " + actual);
      assertEquals(a, actual.getAlpha(), "alpha of " + actual);
   }

   @Test
   void brightnessOneLeavesTheColorUnchanged() {
      StaticColorFrame frame = new StaticColorFrame(new Color(200, 100, 40, 255));
      assertArgb(200, 100, 40, 255, frame.getColor((Object) null));
   }

   @Test
   void brightnessHalfScalesChannelsAndKeepsAlpha() {
      StaticColorFrame opaque = new StaticColorFrame(new Color(200, 100, 40, 255));
      opaque.setBrightness(0.5);
      assertArgb(100, 50, 20, 255, opaque.getColor((Object) null));

      StaticColorFrame translucent = new StaticColorFrame(new Color(200, 100, 40, 128));
      translucent.setBrightness(0.5);
      assertArgb(100, 50, 20, 128, translucent.getColor((Object) null));
   }

   @Disabled("Bug #82045: Colour frame brightness above 1 or below 0 throws IllegalArgumentException")
   @Test
   void brightnessAboveOneClampsChannels() {
      StaticColorFrame frame = new StaticColorFrame(new Color(200, 100, 40));
      frame.setBrightness(1.5);
      assertArgb(255, 150, 60, 255, frame.getColor((Object) null));
   }

   @Test
   void grayscaleUsesTheLuminanceAndKeepsAlpha() {
      StaticColorFrame frame = new StaticColorFrame(new Color(200, 100, 40, 128));
      frame.setGrayscale(true);
      assertArgb(123, 123, 123, 128, frame.getColor((Object) null));
   }

   @Test
   void grayscaleAppliesBrightnessFirst() {
      StaticColorFrame frame = new StaticColorFrame(new Color(200, 100, 40));
      frame.setGrayscale(true);
      frame.setBrightness(0.5);
      assertArgb(61, 61, 61, 255, frame.getColor((Object) null));
   }

   @Test
   void grayscaleOfGreysKeepsThemWithinOne() {
      for(int v : new int[] { 0, 128, 255 }) {
         StaticColorFrame frame = new StaticColorFrame(new Color(v, v, v));
         frame.setGrayscale(true);
         Color actual = frame.getColor((Object) null);
         assertEquals(v, actual.getRed(), 1.0, "red of grey " + v);
         assertEquals(v, actual.getGreen(), 1.0, "green of grey " + v);
         assertEquals(v, actual.getBlue(), 1.0, "blue of grey " + v);

         if(v == 0 || v == 255) {
            assertArgb(v, v, v, 255, actual);
         }
      }
   }
}
