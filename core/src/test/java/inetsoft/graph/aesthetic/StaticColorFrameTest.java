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

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StaticColorFrame}: a fixed colour (user, css or default layer) that is replaced
 * by the cell value when a field is bound and the value is a number or a colour.
 */
@Tag("core")
class StaticColorFrameTest {
   private static void assertColor(int r, int g, int b, Color c) {
      assertNotNull(c);
      assertEquals(r, c.getRed(), "red");
      assertEquals(g, c.getGreen(), "green");
      assertEquals(b, c.getBlue(), "blue");
   }

   @Test
   void defaultColourIsFirstColourOfCategoricalPalette() {
      StaticColorFrame f = new StaticColorFrame();
      assertColor(81, 141, 185, f.getColor());
      assertColor(81, 141, 185, f.getColor("x"));
   }

   @Test
   void colourLayersAreUserThenCssThenDefault() {
      Color dflt = new Color(1, 2, 3);
      Color css = new Color(4, 5, 6);
      Color user = new Color(7, 8, 9);
      StaticColorFrame f = new StaticColorFrame();
      f.setDefaultColor(dflt);
      assertEquals(dflt, f.getColor());
      f.setCssColor(css);
      assertEquals(css, f.getColor());
      f.setUserColor(user);
      assertEquals(user, f.getColor());
      f.setUserColor(null);
      assertEquals(css, f.getColor());
      f.setCssColor(null);
      assertEquals(dflt, f.getColor());
   }

   @Test
   void boundFieldTakesNumberAsRgbAndColorAsItself() {
      StaticColorFrame f = new StaticColorFrame("f");
      f.setColor(Color.BLUE);
      assertColor(255, 128, 0, f.getColor(0xFF8000));
      Color orange = new Color(10, 20, 30);
      assertSame(orange, f.getColor(orange));
      assertEquals(Color.BLUE, f.getColor("text"));
   }

   @Test
   void negativeColourIsUsedOnlyForNegativeNumbers() {
      StaticColorFrame f = new StaticColorFrame(Color.BLUE);
      f.setNegativeColor(Color.RED);
      assertEquals(Color.RED, f.getColor(-1));
      assertEquals(Color.BLUE, f.getColor(0));
      assertEquals(Color.BLUE, f.getColor(5));
      assertEquals(Color.BLUE, f.getColor("t"));
   }

   @Test
   @DisplayName("control: without negative colour a bound number is the rgb value")
   void boundNumberIsRgbWithoutNegativeColour() {
      StaticColorFrame f = new StaticColorFrame("f");
      f.setColor(Color.BLUE);
      assertColor(0, 255, 0, f.getColor(0x00FF00));
   }

   @Test
   @DisplayName("with a negative colour set the bound numbers are values: the sign decides")
   void boundNumbersWithNegativeColourAreValues() {
      StaticColorFrame f = new StaticColorFrame("f");
      f.setColor(Color.BLUE);
      f.setNegativeColor(Color.RED);
      assertEquals(Color.BLUE, f.getColor(5));
      assertEquals(Color.BLUE, f.getColor(0));
      assertEquals(Color.RED, f.getColor(-3));
   }

   @Test
   void dataPathUsesStaticColourOrCellValueOfBoundField() {
      DataSet data = new DefaultDataSet(new Object[][] {
         { "c", "f" }, { "a", 0xFF0000 }, { "b", 0x00FF00 }
      });
      StaticColorFrame unbound = new StaticColorFrame(Color.BLUE);

      for(int row = 0; row < 2; row++) {
         assertEquals(Color.BLUE, unbound.getColor(data, "c", row));
      }

      StaticColorFrame bound = new StaticColorFrame("f");
      bound.setColor(Color.BLUE);
      assertColor(255, 0, 0, bound.getColor(data, "c", 0));
      assertColor(0, 255, 0, bound.getColor(data, "c", 1));
   }

   @Test
   void staticFrameNeverShowsALegend() {
      StaticColorFrame f = new StaticColorFrame(Color.RED);
      assertNull(f.getValues());
      assertFalse(f.isVisible());
      assertTrue(f.isApplicable("a"));
      assertTrue(f.isApplicable(null));
   }

   @Test
   void equalsAndClone() {
      assertEquals(new StaticColorFrame(Color.RED), new StaticColorFrame(Color.RED));

      StaticColorFrame withNegative = new StaticColorFrame(Color.RED);
      withNegative.setNegativeColor(Color.BLUE);
      assertNotEquals(new StaticColorFrame(Color.RED), withNegative);

      StaticColorFrame original = new StaticColorFrame(Color.RED);
      StaticColorFrame copy = (StaticColorFrame) original.clone();
      assertNotSame(original, copy);
      copy.setColor(Color.GREEN);
      assertEquals(Color.RED, original.getColor());
      assertEquals(Color.GREEN, copy.getColor());
   }

   @Test
   void brightnessScalesTheColour() {
      StaticColorFrame f = new StaticColorFrame(new Color(200, 100, 40));
      f.setBrightness(0.5);
      assertColor(100, 50, 20, f.getColor("x"));
   }
}
