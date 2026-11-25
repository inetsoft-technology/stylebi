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
package inetsoft.report.painter;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82288: the Stars presenter never ends its row in a star cut off by the cell edge, marks a
 * row that has more stars than fit, draws zero and negative values as something other than a blank
 * cell, and draws a small fraction wide enough to read. Every case runs on the classic image and
 * on the modern (shape) stars.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class IconCounterPresenterTest {
   private static final int W = 80;
   private static final int H = 16;
   // both the built-in image and the modern star are 12px, with 2px before each star
   private static final int STAR = 12;
   private static final int STEP = STAR + 2;

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void aValueTooLargeForTheCellEndsInWholeStarsAndAPlusMarker(boolean modern) {
      BufferedImage big = paint(presenter(modern), 59000.0);
      // 80px holds five whole stars (2 + 5 * 14 = 72) and the marker after them
      int stars = 5;

      for(int i = 1; i < stars; i++) {
         assertTrue(sameColumns(big, 2, 2 + i * STEP, STAR), "star " + i + " is whole");
      }

      int after = 2 + stars * STEP;
      assertFalse(clear(big, after, 0, W, H), "a marker follows the last whole star");
      assertFalse(sameColumns(big, 2, after, W - after),
                  "what follows the last whole star is not a star cut off by the cell edge");
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void aValueThatFitsHasNoMarker(boolean modern) {
      BufferedImage five = paint(presenter(modern), 5.0);
      BufferedImage six = paint(presenter(modern), 6.0);

      assertTrue(clear(five, 2 + 4 * STEP + STAR, 0, W, H), "five stars fit, so nothing follows");
      assertFalse(clear(six, 2 + 5 * STEP, 0, W, H), "six do not, so the marker shows");
      assertTrue(sameColumns(six, 2, 2 + 4 * STEP, STAR), "the fifth star of six is whole");
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void threeIsThreeWholeStars(boolean modern) {
      BufferedImage image = paint(presenter(modern), 3.0);

      assertTrue(sameColumns(image, 2, 2 + 2 * STEP, STAR));
      assertTrue(clear(image, 2 + 2 * STEP + STAR, 0, W, H));
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void zeroIsAFaintStarNotABlankCell(boolean modern) {
      BufferedImage zero = paint(presenter(modern), 0.0);
      BufferedImage one = paint(presenter(modern), 1.0);

      assertFalse(clear(zero, 0, 0, W, H), "zero is not blank");
      assertTrue(ink(zero) < ink(one) * 0.8, "zero is fainter than one star");
      assertTrue(clear(zero, 2 + STAR, 0, W, H), "only one star");
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void aNegativeValueIsNotBlank(boolean modern) {
      BufferedImage neg = paint(presenter(modern), -0.5);
      BufferedImage nan = paint(presenter(modern), Double.NaN);

      assertFalse(clear(neg, 0, 0, W, H));
      assertFalse(clear(nan, 0, 0, W, H));
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void aNegativeValueWithTextDrawsTheText(boolean modern) {
      IconCounterPresenter stars = (IconCounterPresenter) presenter(modern).withValueText("-0.5");
      BufferedImage image = paint(stars, -0.5);

      assertFalse(clear(image, W / 2, 0, W, H), "the text is drawn at the right of the cell");
      assertTrue(clear(image, 0, 0, 20, H), "no star is drawn");
      assertTrue(sameColumns(paint(stars, 3.0), 2, 2 + 2 * STEP, STAR),
                 "a positive value still draws stars");
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void nullIsBlank(boolean modern) {
      assertTrue(clear(paint(presenter(modern), null), 0, 0, W, H));
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void aSmallFractionIsAReadablePartOfAStar(boolean modern) {
      BufferedImage image = paint(presenter(modern), 0.2);
      int right = rightmost(image);

      // at least 40% of a star: columns 2 to 2 + ceil(4.8) - 1
      assertTrue(right >= 2 + 4, "a fifth of a star is drawn at least 40% wide, was " + right);
      assertTrue(right < 2 + STAR - 1, "but still less than a whole star, was " + right);
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void aFractionKeepsItsPartialStar(boolean modern) {
      BufferedImage image = paint(presenter(modern), 3.4);
      int start = 2 + 3 * STEP;
      int right = rightmost(image);

      assertTrue(sameColumns(image, 2, 2 + 2 * STEP, STAR), "three whole stars");
      assertTrue(right >= start + 4 && right < start + STAR - 1, "and part of a fourth, was " + right);
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void thePreferredSizeIsNeverNegative(boolean modern) {
      IconCounterPresenter stars = presenter(modern);

      assertTrue(stars.getPreferredSize(-3.0).width > 0);
      assertTrue(stars.getPreferredSize(0.0).width > 0);
      assertEquals(3 * STEP + 2, stars.getPreferredSize(3.0).width);
   }

   private static IconCounterPresenter presenter(boolean modern) {
      IconCounterPresenter stars = new IconCounterPresenter();

      if(modern) {
         stars.useModernStyle(new Color(0xF5, 0xA6, 0x23));
      }

      return stars;
   }

   private static BufferedImage paint(IconCounterPresenter stars, Object value) {
      BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      g.setClip(0, 0, W, H);
      g.setColor(Color.BLACK);
      g.setFont(new Font("Dialog", Font.PLAIN, 10));
      stars.paint(g, value, 0, 0, W, H);
      g.dispose();
      return image;
   }

   // the columns [a, a + n) and [b, b + n) hold the same pixels
   private static boolean sameColumns(BufferedImage image, int a, int b, int n) {
      for(int dx = 0; dx < n; dx++) {
         for(int y = 0; y < H; y++) {
            if(b + dx >= W || image.getRGB(a + dx, y) != image.getRGB(b + dx, y)) {
               return false;
            }
         }
      }

      return true;
   }

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

   // the sum of the alpha of every pixel
   private static long ink(BufferedImage image) {
      long sum = 0;

      for(int x = 0; x < W; x++) {
         for(int y = 0; y < H; y++) {
            sum += image.getRGB(x, y) >>> 24;
         }
      }

      return sum;
   }

   private static int rightmost(BufferedImage image) {
      for(int x = W - 1; x >= 0; x--) {
         if(!clear(image, x, 0, x + 1, H)) {
            return x;
         }
      }

      return -1;
   }
}
