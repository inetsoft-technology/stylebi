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
package inetsoft.report;

import inetsoft.uql.viewsheet.ShapeShadow;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76416 (#1): Print Layout rectangles/ovals never carried any shadow
 * state and never drew one. The follow-up to that fix replaced the unblurred,
 * fully opaque offset fill it introduced with a real blurred, translucent
 * shadow layer, so these go past "the field round-trips and paint() does not
 * throw" and assert the pixels: that the shadow lands on the side the
 * direction names, that it is translucent, and that it fades.
 */
@Tag("core")
class PageLayoutShapeShadowTest {
   @Test
   void rectangleHasNoShadowByDefault() {
      PageLayout.Rectangle rect = new PageLayout.Rectangle(0, 0, 10, 10);
      assertNull(rect.getShadow());
   }

   @Test
   void rectangleShadowRoundTrips() {
      PageLayout.Rectangle rect = new PageLayout.Rectangle(0, 0, 10, 10);
      ShapeShadow shadow = new ShapeShadow();
      shadow.setDirection(ShapeShadow.SOUTH_EAST);
      shadow.setDistance(5);

      rect.setShadow(shadow);

      assertSame(shadow, rect.getShadow());
   }

   @Test
   void rectangleCopyCarriesOverTheShadow() {
      PageLayout.Rectangle src = new PageLayout.Rectangle(0, 0, 10, 10);
      ShapeShadow shadow = new ShapeShadow();
      src.setShadow(shadow);

      PageLayout.Rectangle dest = new PageLayout.Rectangle(0, 0, 10, 10);
      dest.copy(src);

      assertSame(shadow, dest.getShadow());
   }

   @Test
   void rectanglePaintsWithoutThrowingWhenAShadowIsSet() {
      PageLayout.Rectangle rect = new PageLayout.Rectangle(5, 5, 20, 20);
      rect.setColor(Color.BLACK);
      rect.setFillColor(Color.WHITE);

      ShapeShadow shadow = new ShapeShadow();
      shadow.setDirection(ShapeShadow.SOUTH_EAST);
      shadow.setDistance(5);
      shadow.setBlur(6);
      rect.setShadow(shadow);

      paintOnABlankCanvas(rect);
   }

   @Test
   void ovalHasNoShadowByDefault() {
      PageLayout.Oval oval = new PageLayout.Oval(0, 0, 10, 10);
      assertNull(oval.getShadow());
   }

   @Test
   void ovalShadowRoundTrips() {
      PageLayout.Oval oval = new PageLayout.Oval(0, 0, 10, 10);
      ShapeShadow shadow = new ShapeShadow();
      shadow.setDirection(ShapeShadow.NORTH_WEST);
      shadow.setDistance(3);

      oval.setShadow(shadow);

      assertSame(shadow, oval.getShadow());
   }

   @Test
   void ovalPaintsWithoutThrowingWhenAShadowIsSet() {
      PageLayout.Oval oval = new PageLayout.Oval(5, 5, 20, 20);
      oval.setColor(Color.BLACK);
      oval.setFillColor(Color.WHITE);

      ShapeShadow shadow = new ShapeShadow();
      shadow.setDirection(ShapeShadow.NORTH_WEST);
      shadow.setDistance(4);
      shadow.setBlur(0);
      oval.setShadow(shadow);

      paintOnABlankCanvas(oval);
   }

   @Test
   void shapeWithNoShadowStillPaintsUnchanged() {
      PageLayout.Rectangle rect = new PageLayout.Rectangle(0, 0, 10, 10);
      rect.setColor(Color.BLACK);

      assertDoesNotThrow(() -> paintOnABlankCanvas(rect));
   }

   @Test
   void rectangleShadowFallsOnTheSideTheDirectionNames() {
      PageLayout.Rectangle rect = new PageLayout.Rectangle(40, 40, 40, 40);
      rect.setColor(Color.BLACK);
      rect.setFillColor(Color.WHITE);
      rect.setShadow(shadow(ShapeShadow.SOUTH_EAST, 10, 6));

      BufferedImage img = paintOnABlankCanvas(rect);

      // just past the bottom-right corner, where an SE shadow falls
      assertTrue(alphaAt(img, 85, 85) > 0,
                 "an SE shadow should reach past the bottom-right corner");
      // the opposite corner is outside the shape and outside the shadow
      assertEquals(0, alphaAt(img, 35, 35),
                   "an SE shadow must not reach past the top-left corner");
   }

   @Test
   void rectangleShadowIsTranslucentAndFades() {
      PageLayout.Rectangle rect = new PageLayout.Rectangle(40, 40, 40, 40);
      rect.setColor(Color.BLACK);
      rect.setFillColor(Color.WHITE);
      // 30% opacity, the shipped default
      rect.setShadow(shadow(ShapeShadow.SOUTH_EAST, 10, 6));

      BufferedImage img = paintOnABlankCanvas(rect);
      int near = alphaAt(img, 84, 84);
      int far = alphaAt(img, 95, 95);

      assertTrue(near > 0 && near < 255,
                 "the configured opacity should survive, alpha was " + near);
      assertTrue(far < near,
                 "the blur should fade with distance, " + far + " vs " + near);
   }

   @Test
   void rectangleShadowStaysHardEdgedWithNoBlur() {
      PageLayout.Rectangle rect = new PageLayout.Rectangle(40, 40, 40, 40);
      rect.setColor(Color.BLACK);
      rect.setFillColor(Color.WHITE);
      rect.setShadow(shadow(ShapeShadow.SOUTH_EAST, 10, 0));

      BufferedImage img = paintOnABlankCanvas(rect);

      // the whole offset band carries the same alpha, and it stops dead at
      // the offset rather than fading past it
      assertEquals(alphaAt(img, 84, 84), alphaAt(img, 89, 89));
      assertEquals(0, alphaAt(img, 91, 91));
   }

   @Test
   void ovalShadowFallsOnTheSideTheDirectionNames() {
      PageLayout.Oval oval = new PageLayout.Oval(40, 40, 40, 40);
      oval.setColor(Color.BLACK);
      oval.setFillColor(Color.WHITE);
      oval.setShadow(shadow(ShapeShadow.NORTH_WEST, 10, 6));

      BufferedImage img = paintOnABlankCanvas(oval);

      assertTrue(alphaAt(img, 50, 33) > 0,
                 "a NW shadow should reach past the top of the oval");
      assertEquals(0, alphaAt(img, 50, 87),
                   "a NW shadow must not reach past the bottom of the oval");
   }

   @Test
   void shapeWithNoShadowLeavesEverythingOutsideItAlone() {
      PageLayout.Rectangle rect = new PageLayout.Rectangle(40, 40, 40, 40);
      rect.setColor(Color.BLACK);
      rect.setFillColor(Color.WHITE);

      BufferedImage img = paintOnABlankCanvas(rect);

      assertEquals(0, alphaAt(img, 85, 85));
   }

   private static ShapeShadow shadow(String direction, int distance, int blur) {
      ShapeShadow shadow = new ShapeShadow();
      shadow.setDirection(direction);
      shadow.setDistance(distance);
      shadow.setBlur(blur);

      return shadow;
   }

   private static int alphaAt(BufferedImage img, int x, int y) {
      return (img.getRGB(x, y) >> 24) & 0xff;
   }

   private static BufferedImage paintOnABlankCanvas(PageLayout.Shape shape) {
      BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();

      try {
         assertDoesNotThrow(() -> shape.paint(g));
      }
      finally {
         g.dispose();
      }

      return img;
   }
}
