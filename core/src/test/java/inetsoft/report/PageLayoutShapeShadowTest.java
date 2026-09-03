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
 * state and never drew one. These are lightweight, scaffolding-free checks
 * (no existing PageLayout/PageLayout.Shape test coverage to extend) proving
 * the shadow field round-trips and paint() renders without throwing --
 * not a pixel-level assertion of the (intentionally unblurred, see
 * PageLayout.Rectangle/Oval.paint()) shadow rendering itself.
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

   private static void paintOnABlankCanvas(PageLayout.Shape shape) {
      BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();

      try {
         assertDoesNotThrow(() -> shape.paint(g));
      }
      finally {
         g.dispose();
      }
   }
}
