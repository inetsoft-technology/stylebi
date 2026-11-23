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
 * Tests for the path through the RGB cube of {@link RGBCubeColorFrame}: the interpolation between
 * the path nodes, the clamping of channels, the alpha range, the ratio hooks and equality.
 */
@Tag("core")
class RGBCubeColorFrameTest {
   /** Path black, red, white. */
   private static RGBCubeColorFrame rc3() {
      return new RGBCubeColorFrame(new double[][] { { 0, 0, 0 }, { 1, 0, 0 }, { 1, 1, 1 } });
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
   void pathEndsAreTheFirstAndLastNode() {
      RGBCubeColorFrame frame = rc3();
      assertRgb(0, 0, 0, frame.getColor(0));
      assertRgb(255, 255, 255, frame.getColor(1));
   }

   @Test
   void pathNodeIsExactAtItsRatio() {
      assertRgb(255, 0, 0, rc3().getColor(0.5));
   }

   @Test
   void colorsBetweenNodesAreInterpolatedAndTruncated() {
      RGBCubeColorFrame frame = rc3();
      assertRgb(127, 0, 0, frame.getColor(0.25));
      assertRgb(255, 127, 127, frame.getColor(0.75));
   }

   @Test
   void fourNodePathInterpolatesInsideTheSecondSegment() {
      RGBCubeColorFrame frame = new RGBCubeColorFrame(
         new double[][] { { 0, 0, 0 }, { 1, 0, 0 }, { 1, 1, 0 }, { 1, 1, 1 } });
      assertRgb(255, 127, 0, frame.getColor(0.5));
   }

   /** Pinned current behavior: a frame without a path is black at every ratio. */
   @Test
   void pinnedEmptyPathIsBlack() {
      RGBCubeColorFrame frame = new RGBCubeColorFrame();

      for(double r : new double[] { 0, 0.5, 1 }) {
         assertRgb(0, 0, 0, frame.getColor(r));
      }
   }

   @Disabled("Bug #82070: RGBCubeColorFrame with one colour path or an end component above 1 throws")
   @Test
   void singleColorPathHasThatColorEverywhere() {
      RGBCubeColorFrame frame = new RGBCubeColorFrame(new double[][] { { 1, 0, 0 } });

      for(double r : new double[] { 0, 0.5, 0.99, 1 }) {
         assertRgb(255, 0, 0, frame.getColor(r));
      }
   }

   @Test
   void twoColorPathIsLinear() {
      RGBCubeColorFrame frame = new RGBCubeColorFrame(new double[][] { { 0, 0, 0 }, { 1, 1, 1 } });
      assertRgbWithin1(63.75, 63.75, 63.75, frame.getColor(0.25));
      assertRgbWithin1(127.5, 127.5, 127.5, frame.getColor(0.5));
      assertRgbWithin1(191.25, 191.25, 191.25, frame.getColor(0.75));
   }

   @Test
   void interiorComponentAboveOneIsClampedTo255() {
      RGBCubeColorFrame frame = new RGBCubeColorFrame(new double[][] { { 0, 0, 0 }, { 2, 0, 0 } });
      assertRgb(255, 0, 0, frame.getColor(0.75));
   }

   @Disabled("Bug #82070: RGBCubeColorFrame with one colour path or an end component above 1 throws")
   @Test
   void endComponentAboveOneIsClampedLikeTheInterior() {
      RGBCubeColorFrame frame = new RGBCubeColorFrame(new double[][] { { 0, 0, 0 }, { 1.2, 0, 0 } });
      assertRgb(255, 0, 0, frame.getColor(1));
   }

   @Test
   void alphaFollowsTheAlphaRange() {
      RGBCubeColorFrame frame = rc3();
      frame.setMinAlpha(0);
      frame.setMaxAlpha(1);
      assertEquals(0, frame.getColor(0).getAlpha());
      assertEquals(63, frame.getColor(0.25).getAlpha());
      assertEquals(127, frame.getColor(0.5).getAlpha());
      assertEquals(255, frame.getColor(1).getAlpha());
   }

   @Test
   void alphaRangeMayDecrease() {
      RGBCubeColorFrame frame = rc3();
      frame.setMinAlpha(1);
      frame.setMaxAlpha(0.2);
      assertEquals(255, frame.getColor(0).getAlpha(), 1);
      assertEquals(51, frame.getColor(1).getAlpha(), 1);
   }

   @Test
   void defaultAlphaIsOpaque() {
      RGBCubeColorFrame frame = rc3();

      for(double r : new double[] { 0, 0.3, 0.6, 1 }) {
         assertEquals(255, frame.getColor(r).getAlpha(), "ratio " + r);
      }
   }

   @Test
   void ratioHooksRestrictTheUsedPartOfThePath() {
      RGBCubeColorFrame frame =
         new RGBCubeColorFrame(new double[][] { { 0, 0, 0 }, { 1, 0, 0 }, { 1, 1, 1 } }) {
            @Override
            protected double getMinRatio() {
               return 0.25;
            }

            @Override
            protected double getMaxRatio() {
               return 0.75;
            }
         };

      assertRgb(127, 0, 0, frame.getColor(0));
      assertRgb(255, 127, 127, frame.getColor(1));
   }

   @Test
   void equalsComparesEveryPathEntry() {
      RGBCubeColorFrame a = rc3();
      RGBCubeColorFrame b = rc3();
      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());

      RGBCubeColorFrame differentEntry = new RGBCubeColorFrame(
         new double[][] { { 0, 0, 0 }, { 1, 0, 0 }, { 1, 1, 0.5 } });
      assertNotEquals(a, differentEntry);

      RGBCubeColorFrame differentLength = new RGBCubeColorFrame(
         new double[][] { { 0, 0, 0 }, { 1, 0, 0 } });
      assertNotEquals(a, differentLength);

      RGBCubeColorFrame differentWidth = new RGBCubeColorFrame(
         new double[][] { { 0, 0, 0 }, { 1, 0, 0 }, { 1, 1 } });
      assertNotEquals(a, differentWidth);
   }

   @Test
   void ratioJustBelowOneDoesNotFail() {
      RGBCubeColorFrame frame = rc3();
      Color almost = frame.getColor(0.9999999);
      Color end = frame.getColor(1.0);
      assertEquals(255, almost.getRed());
      assertRgbWithin1(255, 255, 255, almost);
      assertRgb(255, 255, 255, end);
   }

   @Test
   void brightnessScalesAPathNode() {
      RGBCubeColorFrame frame = rc3();
      frame.setBrightness(0.5);
      assertRgb(127, 0, 0, frame.getColor(0.5));
   }
}
