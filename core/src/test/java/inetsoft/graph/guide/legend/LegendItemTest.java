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
package inetsoft.graph.guide.legend;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;

import static inetsoft.graph.guide.legend.LegendTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A single legend item: label and format, sizes and symbol painting.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class LegendItemTest {
   private static ColorLegendItem item(Object label, Object value, int symbolSize) {
      CategoricalColorFrame frame = color("S", "A", "B");
      frame.getLegendSpec().setSymbolSize(symbolSize);
      return new ColorLegendItem(label, value, frame);
   }

   private static ColorLegendItem item(Object label, Object value) {
      return item(label, value, 12);
   }

   private static ColorLegendItem formattedItem(Object label, Object value) {
      CategoricalColorFrame frame = color("S", "A", "B");
      frame.getLegendSpec().getTextSpec().setFormat(new DecimalFormat("0.00"));
      return new ColorLegendItem(label, value, frame);
   }

   @Test
   void labelEqualToTheValueIsFormatted() {
      assertEquals("1.50", formattedItem(1.5, 1.5).getLabel());
   }

   @Test
   void aliasLabelIsNotFormatted() {
      assertEquals("9", formattedItem(9, 1.5).getLabel());
   }

   @Test
   void labelEqualToTheValueIsFormattedAlsoForIntegers() {
      assertEquals("9.00", formattedItem(9, 9).getLabel());
   }

   @Test
   void emptyAndNullLabelsHaveTheHeightOfARealLabel() {
      LegendItem real = item("A", "A", 6);
      LegendItem empty = item("", "", 6);
      LegendItem none = item(null, null, 6);

      assertEquals(real.getPreferredHeight(), empty.getPreferredHeight(), 1e-9);
      assertEquals(real.getPreferredHeight(), none.getPreferredHeight(), 1e-9);
      assertEquals(real.getMinHeight(), empty.getMinHeight(), 1e-9);
      assertEquals(real.getMinHeight(), none.getMinHeight(), 1e-9);
   }

   @Test
   void symbolSizeAddsToTheWidthAndRaisesTheHeight() {
      LegendItem small = item("label", "A", 6);
      LegendItem medium = item("label", "A", 12);
      LegendItem large = item("label", "A", 50);

      assertEquals(44, large.getPreferredWidth() - small.getPreferredWidth(), 1e-9);
      assertEquals(6, medium.getPreferredWidth() - small.getPreferredWidth(), 1e-9);
      assertTrue(medium.getPreferredHeight() >= small.getPreferredHeight());
      assertTrue(large.getPreferredHeight() >= medium.getPreferredHeight());
      assertTrue(small.getPreferredHeight() >= 6 + 2);
      assertTrue(medium.getPreferredHeight() >= 12 + 2);
      assertTrue(large.getPreferredHeight() >= 50 + 2);
   }

   @Test
   void lineItemSymbolIsThirtyWideInsteadOfTheSymbolSize() {
      CategoricalColorFrame colors = color("S", "A", "B");
      colors.getLegendSpec().setSymbolSize(12);
      LegendItem colorItem = new ColorLegendItem("label", "A", colors);
      LegendItem lineItem = new LineLegendItem("label", "A", line("S", "A", "B"));

      assertEquals(30 - 12, lineItem.getPreferredWidth() - colorItem.getPreferredWidth(), 1e-9);
   }

   @Test
   void longerLabelMakesAWiderItemAndMinimumWidthDoesNotExceedPreferredWidth() {
      LegendItem shortItem = item("abc", "A");
      LegendItem longItem = item("a".repeat(30), "A");

      assertTrue(longItem.getPreferredWidth() > shortItem.getPreferredWidth());
      assertTrue(shortItem.getMinWidth() <= shortItem.getPreferredWidth());
      assertTrue(longItem.getMinWidth() <= longItem.getPreferredWidth());
   }

   @Test
   void paddingAddsTopAndBottomToTheHeight() {
      CategoricalColorFrame plain = color("S", "A", "B");
      CategoricalColorFrame padded = color("S", "A", "B");
      padded.getLegendSpec().setPadding(new Insets(3, 0, 5, 0));
      LegendItem a = new ColorLegendItem("A", "A", plain);
      LegendItem b = new ColorLegendItem("A", "A", padded);

      // padding replaces the default bottom padding of 1
      assertEquals((3 + 5) - (0 + 1), b.getPreferredHeight() - a.getPreferredHeight(), 1e-9);
   }

   @Test
   void labelAndValueAreKeptApart() {
      LegendItem item = item("Alias", "raw");

      assertEquals("Alias", item.getLabel());
      assertEquals("raw", item.getValue());
   }

   private static BufferedImage paintSymbol(LegendItem item) {
      BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();

      try {
         item.paintSymbol(g, 0, 0);
      }
      finally {
         g.dispose();
      }

      return image;
   }

   private static ColorLegendItem colorItem(Color color) {
      CategoricalColorFrame frame = new CategoricalColorFrame("S");
      frame.init(new Object[] { "A" }, new Color[] { color });
      return new ColorLegendItem("A", "A", frame);
   }

   @Test
   void colorSymbolIsFilledWithTheColorOfTheValue() {
      BufferedImage image = paintSymbol(colorItem(Color.RED));

      assertEquals(0xFFFF0000, image.getRGB(6, 6));
   }

   @Test
   void nearWhiteSymbolGetsAGreyBorderAndOtherLightColorsDoNot() {
      BufferedImage white = paintSymbol(colorItem(new Color(250, 250, 250)));
      BufferedImage grey = paintSymbol(colorItem(new Color(249, 249, 249)));

      assertEquals(0xFFA3A3A3, white.getRGB(0, 0));
      assertEquals(0xFFF9F9F9, grey.getRGB(0, 0));
   }
}
