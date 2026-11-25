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
 * Tests for {@link SaturationColorFrame}: the saturation range from 0.1 to 1 applied to the base
 * color while hue and lightness are kept.
 */
@Tag("core")
class SaturationColorFrameTest {
   private static void assertRgbWithin1(int r, int g, int b, Color actual) {
      assertEquals(r, actual.getRed(), 1.0, "red of " + actual);
      assertEquals(g, actual.getGreen(), 1.0, "green of " + actual);
      assertEquals(b, actual.getBlue(), 1.0, "blue of " + actual);
   }

   private static int chroma(Color c) {
      int max = Math.max(c.getRed(), Math.max(c.getGreen(), c.getBlue()));
      int min = Math.min(c.getRed(), Math.min(c.getGreen(), c.getBlue()));
      return max - min;
   }

   @Test
   void endsHaveSaturationOneTenthAndOne() {
      SaturationColorFrame frame = new SaturationColorFrame();
      assertRgbWithin1(176, 184, 191, frame.getColor(0));
      assertRgbWithin1(113, 184, 255, frame.getColor(1));
   }

   @Test
   void middleHasSaturationAboutHalf() {
      assertRgbWithin1(144, 184, 223, new SaturationColorFrame().getColor(0.5));
   }

   @Test
   void chromaGrowsWithTheRatio() {
      SaturationColorFrame frame = new SaturationColorFrame();
      int prev = -1;

      for(int i = 0; i <= 10; i++) {
         int chroma = chroma(frame.getColor(i * 0.1));
         assertTrue(chroma >= prev, "chroma shrinks at step " + i);
         prev = chroma;
      }

      assertTrue(chroma(frame.getColor(1)) > chroma(frame.getColor(0)));
   }

   @Test
   void lightnessAndHueAreKept() {
      SaturationColorFrame frame = new SaturationColorFrame();

      for(int i = 0; i <= 10; i++) {
         Color c = frame.getColor(i * 0.1);
         int max = Math.max(c.getRed(), Math.max(c.getGreen(), c.getBlue()));
         int min = Math.min(c.getRed(), Math.min(c.getGreen(), c.getBlue()));
         assertEquals(184, (max + min) / 2.0, 1.0, "lightness at step " + i + ": " + c);
         assertTrue(c.getBlue() >= c.getGreen() && c.getGreen() >= c.getRed(),
                    "not a blue shade at step " + i + ": " + c);
      }
   }

   @Test
   void lowestSaturationIsNotFullyGrey() {
      Color c = new SaturationColorFrame().getColor(0);
      assertNotEquals(c.getRed(), c.getBlue());
   }

   @Test
   void alphaRangeAndScaleValuesMapLikeTheRatio() {
      SaturationColorFrame frame = new SaturationColorFrame();
      frame.setMinAlpha(0.2);
      frame.setMaxAlpha(1);
      assertEquals(51, frame.getColor(0).getAlpha());
      assertEquals(255, frame.getColor(1).getAlpha());

      frame.setScale(new LinearScale(0, 100));
      assertEquals(frame.getColor(0), frame.getColor((Object) 0.0));
      assertEquals(frame.getColor(0.5), frame.getColor((Object) 50.0));
      assertEquals(frame.getColor(1), frame.getColor((Object) 100.0));
   }
}
