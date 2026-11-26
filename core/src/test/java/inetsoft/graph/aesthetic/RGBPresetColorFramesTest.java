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

import java.awt.Color;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the color paths of the preset frames built on {@link RGBCubeColorFrame}: circular,
 * rainbow, bipolar and heat.
 */
@Tag("core")
class RGBPresetColorFramesTest {
   private static void assertRgb(int r, int g, int b, Color actual) {
      assertNotNull(actual);
      assertEquals(r, actual.getRed(), "red of " + actual);
      assertEquals(g, actual.getGreen(), "green of " + actual);
      assertEquals(b, actual.getBlue(), "blue of " + actual);
   }

   private static void assertRgbWithin1(int r, int g, int b, Color actual) {
      assertEquals(r, actual.getRed(), 1.0, "red of " + actual);
      assertEquals(g, actual.getGreen(), 1.0, "green of " + actual);
      assertEquals(b, actual.getBlue(), 1.0, "blue of " + actual);
   }

   @Test
   void circularFollowsItsFiveNodes() {
      CircularColorFrame frame = new CircularColorFrame();
      assertRgb(0, 0, 255, frame.getColor(0));
      assertRgb(0, 255, 0, frame.getColor(0.25));
      assertRgb(255, 255, 0, frame.getColor(0.5));
      assertRgb(255, 0, 0, frame.getColor(0.75));
      assertRgb(0, 0, 255, frame.getColor(1));
   }

   @Test
   void circularReturnsToItsStart() {
      CircularColorFrame frame = new CircularColorFrame();
      assertEquals(frame.getColor(0), frame.getColor(1));
   }

   @Test
   void circularBetweenNodes() {
      assertRgb(0, 127, 127, new CircularColorFrame().getColor(0.125));
   }

   @Test
   void rainbowFollowsItsFiveNodes() {
      RainbowColorFrame frame = new RainbowColorFrame();
      assertRgb(0, 0, 255, frame.getColor(0));
      assertRgb(0, 255, 255, frame.getColor(0.25));
      assertRgb(0, 255, 0, frame.getColor(0.5));
      assertRgb(255, 255, 0, frame.getColor(0.75));
      assertRgb(255, 0, 0, frame.getColor(1));
   }

   @Test
   void rainbowBetweenNodes() {
      assertRgb(0, 127, 255, new RainbowColorFrame().getColor(0.125));
   }

   @Test
   void bipolarFollowsItsThreeNodes() {
      BipolarColorFrame frame = new BipolarColorFrame();
      assertRgb(0, 255, 0, frame.getColor(0));
      assertRgb(0, 0, 0, frame.getColor(0.5));
      assertRgb(255, 0, 0, frame.getColor(1));
   }

   @Test
   void bipolarBetweenNodes() {
      BipolarColorFrame frame = new BipolarColorFrame();
      assertRgb(0, 127, 0, frame.getColor(0.25));
      assertRgb(127, 0, 0, frame.getColor(0.75));
   }

   @Test
   void bipolarIsSymmetricAroundTheCenter() {
      BipolarColorFrame frame = new BipolarColorFrame();

      for(double d : new double[] { 0.1, 0.2, 0.3, 0.4 }) {
         Color low = frame.getColor(0.5 - d);
         Color high = frame.getColor(0.5 + d);
         assertEquals(low.getGreen(), high.getRed(), 1.0, "distance " + d);
         assertEquals(0, low.getRed(), "red of low side at " + d);
         assertEquals(0, low.getBlue(), "blue of low side at " + d);
         assertEquals(0, high.getGreen(), "green of high side at " + d);
         assertEquals(0, high.getBlue(), "blue of high side at " + d);
      }
   }

   @Test
   void heatLowEndUsesTwentyPercentOfThePath() {
      assertRgbWithin1(102, 51, 0, new HeatColorFrame().getColor(0));
   }

   @Test
   void heatHighEndUsesEightyPercentOfThePath() {
      assertRgbWithin1(255, 204, 153, new HeatColorFrame().getColor(1));
   }

   @Test
   void heatMiddleIsTheOrangeNode() {
      assertRgbWithin1(255, 127, 0, new HeatColorFrame().getColor(0.5));
   }

   @Test
   void heatNeverReachesBlackOrWhite() {
      HeatColorFrame frame = new HeatColorFrame();

      for(double r : new double[] { 0, 1 }) {
         assertNotEquals(0, frame.getColor(r).getRGB() & 0xFFFFFF, "black at " + r);
         assertNotEquals(0xFFFFFF, frame.getColor(r).getRGB() & 0xFFFFFF, "white at " + r);
      }
   }

   @Test
   void heatGetsBrighterWithTheRatio() {
      HeatColorFrame frame = new HeatColorFrame();
      double prev = -1;

      for(int i = 0; i <= 20; i++) {
         Color c = frame.getColor(i * 0.05);
         double luma = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
         assertTrue(luma > prev, "luma does not increase at step " + i);
         prev = luma;
      }
   }

   @Test
   void presetsAreDistinctFramesAndCopiesAreEqual() {
      List<Supplier<LinearColorFrame>> factories = List.of(
         CircularColorFrame::new, RainbowColorFrame::new, BipolarColorFrame::new,
         HeatColorFrame::new, RGBCubeColorFrame::new);

      for(int i = 0; i < factories.size(); i++) {
         LinearColorFrame a = factories.get(i).get();
         LinearColorFrame copy = factories.get(i).get();
         assertEquals(a, copy, "copy of " + a.getClass().getSimpleName());
         assertEquals(a.hashCode(), copy.hashCode());

         for(int j = i + 1; j < factories.size(); j++) {
            LinearColorFrame b = factories.get(j).get();
            assertNotEquals(a, b, a.getClass().getSimpleName() + " vs " +
               b.getClass().getSimpleName());
            assertNotEquals(b, a);
         }
      }
   }
}
