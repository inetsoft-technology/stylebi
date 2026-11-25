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
package inetsoft.report.painter;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A Bar presenter has no bar for a negative value, so a cell that was given the value's text draws
 * the text at its right edge instead of looking empty.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BarPresenterValueTextTest {
   private static final int W = 90;
   private static final int H = 20;

   @Test
   void aNegativeValueWithTextDrawsTheTextAtTheRightOfTheCell() {
      BarPresenter bar = (BarPresenter) new BarPresenter(100, Color.BLUE).withValueText("-1,013.7");
      BufferedImage image = paint(bar, -1013.7);

      assertFalse(clear(image, 45, 0, W, H), "the text is drawn in the right half");
      assertTrue(clear(image, 0, 0, 20, H), "nothing is drawn at the left of the cell");
   }

   @Test
   void aNaNValueWithTextDrawsTheText() {
      BarPresenter bar = (BarPresenter) new BarPresenter(100, Color.BLUE).withValueText("NaN");

      assertFalse(clear(paint(bar, Double.NaN), 45, 0, W, H));
   }

   @Test
   void aNegativeValueWithoutTextStillDrawsNothing() {
      assertTrue(clear(paint(new BarPresenter(100, Color.BLUE), -5.0), 0, 0, W, H));
   }

   @Test
   void aPositiveValueIsABarAndTheTextIsNotDrawn() {
      BarPresenter bar = (BarPresenter) new BarPresenter(100, Color.BLUE).withValueText("-1");
      BufferedImage image = paint(bar, 50.0);

      assertFalse(clear(image, 0, 0, 40, H), "the bar fills the left half");
      assertTrue(clear(image, 50, 0, W, H), "no text beyond the bar");
   }

   @Test
   void zeroAndNullDrawNothing() {
      BarPresenter bar = (BarPresenter) new BarPresenter(100, Color.BLUE).withValueText("0");

      assertTrue(clear(paint(bar, 0.0), 0, 0, W, H));
      assertTrue(clear(paint(bar, null), 0, 0, W, H));
   }

   @Test
   void theCopyKeepsEveryOtherField() {
      BarPresenter bar = new BarPresenter(250, Color.RED);
      bar.setAutoMaximum(true);
      bar.useModernThickness(8);

      BarPresenter copy = (BarPresenter) bar.withValueText("-3");

      assertNotSame(bar, copy);
      assertEquals("-3", copy.valueText());
      assertNull(bar.valueText(), "the original is never changed");
      assertEquals(250, copy.getMaximum());
      assertEquals(Color.RED, copy.getColor());
      assertTrue(copy.isAutoMaximum());
      assertEquals(8, copy.modernThickness());
   }

   @Test
   void theMaximumResolverCopyKeepsTheText() {
      BarPresenter bar = (BarPresenter) new BarPresenter(100, Color.BLUE).withValueText("-2");

      assertEquals("-2", ((BarPresenter) bar.withMaximumResolver(() -> 50.0)).valueText());
   }

   private static BufferedImage paint(BarPresenter bar, Object value) {
      BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      g.setFont(new Font("SansSerif", Font.PLAIN, 11));
      g.setColor(Color.BLACK);
      bar.paint(g, value, 0, 0, W, H);
      g.dispose();
      return image;
   }

   // true when no pixel of the region has been drawn
   private static boolean clear(BufferedImage image, int x0, int y0, int x1, int y1) {
      for(int x = x0; x < x1; x++) {
         for(int y = y0; y < y1; y++) {
            if((image.getRGB(x, y) >>> 24) != 0) {
               return false;
            }
         }
      }

      return true;
   }
}
