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

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ColorValueColorFrame}: the cell value itself is the colour, either an encoded
 * rgb number or a hex string, and anything else yields the default colour.
 */
@Tag("core")
class ColorValueColorFrameTest {
   private static void assertColor(int r, int g, int b, int a, Color c) {
      assertNotNull(c);
      assertEquals(r, c.getRed(), "red");
      assertEquals(g, c.getGreen(), "green");
      assertEquals(b, c.getBlue(), "blue");
      assertEquals(a, c.getAlpha(), "alpha");
   }

   @Test
   void numberIsEncodedRgbForEveryNumberType() {
      ColorValueColorFrame f = new ColorValueColorFrame();
      assertColor(255, 0, 0, 255, f.getColor(Integer.valueOf(0xFF0000)));
      assertColor(255, 0, 0, 255, f.getColor(Long.valueOf(0xFF0000)));
      assertColor(255, 0, 0, 255, f.getColor(Double.valueOf(0xFF0000)));
      assertColor(0, 255, 0, 255, f.getColor(0x00FF00));
   }

   @Test
   void hexStringsWithAndWithoutHashAndAlpha() {
      ColorValueColorFrame f = new ColorValueColorFrame();
      assertColor(255, 0, 0, 255, f.getColor("#FF0000"));
      assertColor(255, 128, 0, 255, f.getColor("ff8000"));
      assertColor(255, 0, 0, 128, f.getColor("#FF000080"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "zzz", "#12" })
   void invalidStringUsesDefaultColour(String text) {
      ColorValueColorFrame f = new ColorValueColorFrame();
      f.setDefaultColor(Color.RED);
      assertEquals(Color.RED, f.getColor(text));
   }

   @Test
   @DisplayName("pinned: an empty text gives null (VisualModel.getColor replaces it with the default), null gives the default")
   void pinnedEmptyStringGivesNull() {
      ColorValueColorFrame f = new ColorValueColorFrame();
      f.setDefaultColor(Color.RED);
      assertNull(f.getColor(""));
      assertEquals(Color.RED, f.getColor(null));
   }

   @Test
   void nullUsesDefaultColourOrNothing() {
      ColorValueColorFrame f = new ColorValueColorFrame();
      assertNull(f.getColor(null));
      f.setDefaultColor(Color.RED);
      assertEquals(Color.RED, f.getColor(null));
   }

   @Test
   void dataPathReadsBoundFieldOrTheColumn() {
      DataSet data = new DefaultDataSet(new Object[][] {
         { "c", "f" }, { 0xFF0000, 0x00FF00 }, { 0x0000FF, 0xFFFF00 }
      });
      ColorValueColorFrame bound = new ColorValueColorFrame("f");
      assertColor(0, 255, 0, 255, bound.getColor(data, "c", 0));
      assertColor(255, 255, 0, 255, bound.getColor(data, "c", 1));

      ColorValueColorFrame unbound = new ColorValueColorFrame();
      assertColor(255, 0, 0, 255, unbound.getColor(data, "c", 0));
      assertColor(0, 0, 255, 255, unbound.getColor(data, "c", 1));
   }

   @Test
   void brightnessScalesTheColour() {
      ColorValueColorFrame f = new ColorValueColorFrame();
      f.setBrightness(0.5);
      assertColor(100, 50, 20, 255, f.getColor(0xC86428));
   }
}
