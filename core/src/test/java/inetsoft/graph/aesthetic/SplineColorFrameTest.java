/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.graph.rgb.AbstractSplineColorFrame;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.*;
import java.io.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the B-spline colour frames ({@link inetsoft.graph.rgb.SplineColorFrame}, through the
 * ColorBrewer palette frames). Expected colours are hand derived from the uniform cubic B-spline
 * with extrapolated end points: at knot i the value is (v[i-1] + 4 v[i] + v[i+1]) / 6 per channel
 * and at the ends the curve passes through the first and last colour.
 */
@Tag("core")
class SplineColorFrameTest {
   private static void assertColor(int r, int g, int b, Color c) {
      assertNotNull(c);
      assertEquals(r, c.getRed(), "red");
      assertEquals(g, c.getGreen(), "green");
      assertEquals(b, c.getBlue(), "blue");
   }

   static Stream<Arguments> knownColours() {
      return Stream.of(
         // Blues, 9 colours: ends are exact, knots 2 and 4, first and last segment inside
         Arguments.of(new BluesColorFrame(), 0.0, 247, 251, 255),
         Arguments.of(new BluesColorFrame(), 1.0, 8, 48, 107),
         Arguments.of(new BluesColorFrame(), 0.25, 195, 218, 238),
         Arguments.of(new BluesColorFrame(), 0.5, 108, 174, 213),
         Arguments.of(new BluesColorFrame(), 0.0625, 234, 243, 251),
         Arguments.of(new BluesColorFrame(), 0.9375, 8, 64, 131),
         // BrBG, 11 colours, middle knot (f5f5f5 with neighbours f6e8c3 and c7eae5)
         Arguments.of(new BrBGColorFrame(), 0.5, 237, 241, 234));
   }

   @ParameterizedTest
   @MethodSource("knownColours")
   void paletteFrameGivesHandDerivedColour(AbstractSplineColorFrame frame, double ratio,
                                           int r, int g, int b)
   {
      assertColor(r, g, b, frame.getColor(ratio));
   }

   @Test
   @DisplayName("a two colour ramp is interpolated linearly")
   void twoColourRampIsLinear() {
      AbstractSplineColorFrame f = new AbstractSplineColorFrame() {
         @Override
         protected String[] getColorRamps() {
            return new String[] { "000000ffffff" };
         }
      };
      double[] ratios = { 0, 0.25, 0.5, 0.75, 1 };
      int[] expected = { 0, 63, 127, 191, 255 };

      for(int i = 0; i < ratios.length; i++) {
         assertColor(expected[i], expected[i], expected[i], f.getColor(ratios[i]));
      }
   }

   @Test
   @DisplayName("ramp index selects the ramp, a negative index the last one")
   void rampIndexSelectsTheRamp() {
      String[] ramps = { "ff0000ffff00", "00ff000000ff", "0000ffffffff" };

      class Ramps extends AbstractSplineColorFrame {
         Ramps(int idx) {
            super(idx);
         }

         @Override
         protected String[] getColorRamps() {
            return ramps;
         }
      }

      assertColor(0, 0, 255, new Ramps(-1).getColor(0));
      assertColor(255, 0, 0, new Ramps(0).getColor(0));
      assertColor(0, 255, 0, new Ramps(1).getColor(0));
   }

   @ParameterizedTest
   @MethodSource("continuousFrames")
   @DisplayName("neighbouring colours differ by at most 4 per channel (no jump at knots)")
   void colourIsContinuous(AbstractSplineColorFrame frame) {
      Color prev = frame.getColor(0.0);

      for(int i = 1; i <= 1000; i++) {
         Color c = frame.getColor(i / 1000.0);
         assertTrue(Math.abs(c.getRed() - prev.getRed()) <= 4, "red at " + i);
         assertTrue(Math.abs(c.getGreen() - prev.getGreen()) <= 4, "green at " + i);
         assertTrue(Math.abs(c.getBlue() - prev.getBlue()) <= 4, "blue at " + i);
         prev = c;
      }
   }

   static Stream<Arguments> continuousFrames() {
      return Stream.of(Arguments.of(new SpectralColorFrame()), Arguments.of(new BluesColorFrame()));
   }

   @Test
   void alphaRangeAppliesToTheColour() {
      BluesColorFrame f = new BluesColorFrame();
      assertEquals(255, f.getColor(0.5).getAlpha());
      f.setMinAlpha(0);
      f.setMaxAlpha(1);
      assertEquals(0, f.getColor(0.0).getAlpha());
      assertEquals(63, f.getColor(0.25).getAlpha());
      assertEquals(127, f.getColor(0.5).getAlpha());
      assertEquals(255, f.getColor(1.0).getAlpha());
   }

   @Test
   void equalsComparesTheClassOnly() {
      assertEquals(new BluesColorFrame(), new BluesColorFrame());
      assertEquals(new BluesColorFrame().hashCode(), new BluesColorFrame().hashCode());
      assertNotEquals(new BluesColorFrame(), new GreensColorFrame());
      assertNotEquals(new BrBGColorFrame(), new PiYGColorFrame());
      assertNotEquals(null, new BluesColorFrame());
   }

   @Test
   void cloneGivesSameColoursInAnotherInstance() {
      BluesColorFrame f = new BluesColorFrame();
      BluesColorFrame copy = (BluesColorFrame) f.clone();
      assertNotSame(f, copy);

      for(double r : new double[] { 0.1, 0.5, 0.9 }) {
         assertEquals(f.getColor(r), copy.getColor(r));
      }
   }

   @Test
   @DisplayName("serialization round trip rebuilds the interpolator")
   void serializationRoundTrip() throws Exception {
      BluesColorFrame f = new BluesColorFrame();
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(f);
      }

      BluesColorFrame copy;

      try(ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         copy = (BluesColorFrame) in.readObject();
      }

      for(double r : new double[] { 0.1, 0.5, 0.9 }) {
         assertEquals(f.getColor(r), copy.getColor(r));
      }
   }
}
