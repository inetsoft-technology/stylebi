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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79405: a Print Layout Line or Oval whose line style is None must not
 * stroke itself (a zero-width Java2D stroke is a 1px hairline, not invisible).
 * A Line's arrows are still drawn, as in the viewer.
 */
@Tag("core")
class PageLayoutNoneStyleTest {
   @Test
   void noneStyleLineDrawsNothing() {
      assertEquals(0, opaquePixels(paint(line(StyleConstants.NO_BORDER, false, 0))));
   }

   @Test
   void noneStyleLineKeepsItsArrows() {
      int arrowOnly = opaquePixels(
         paint(line(StyleConstants.NO_BORDER, true, PageLayout.Line.END)));
      int thinWithArrow = opaquePixels(
         paint(line(StyleConstants.THIN_LINE, true, PageLayout.Line.END)));

      assertTrue(arrowOnly > 0, "arrow should still be drawn");
      assertTrue(arrowOnly < thinWithArrow, "the line itself should be skipped");
   }

   @Test
   void noneStyleLineKeepsBeginAndBothArrows() {
      assertTrue(opaquePixels(
         paint(line(StyleConstants.NO_BORDER, true, PageLayout.Line.START))) > 0);
      assertTrue(opaquePixels(
         paint(line(StyleConstants.NO_BORDER, true, PageLayout.Line.ALL))) > 0);
   }

   @Test
   void thinAndThickLinesStillDraw() {
      int thin = opaquePixels(paint(line(StyleConstants.THIN_LINE, false, 0)));
      int thick = opaquePixels(paint(line(StyleConstants.THICK_LINE, false, 0)));

      assertTrue(thin > 0);
      assertTrue(thick > thin);
   }

   @Test
   void noneStyleOvalDrawsNoOutline() {
      assertEquals(0, opaquePixels(paint(oval(StyleConstants.NO_BORDER, null))));
   }

   @Test
   void noneStyleOvalStillFills() {
      BufferedImage img = paint(oval(StyleConstants.NO_BORDER, Color.BLUE));

      assertTrue(opaquePixels(img) > 0, "fill should still be painted");
   }

   @Test
   void thinAndThickOvalsStillDrawAnOutline() {
      assertTrue(opaquePixels(paint(oval(StyleConstants.THIN_LINE, null))) > 0);
      assertTrue(opaquePixels(paint(oval(StyleConstants.THICK_LINE, null))) > 0);
   }

   @Test
   void noneStyleRectangleDrawsNothing() {
      PageLayout.Rectangle rect = new PageLayout.Rectangle(20, 20, 60, 40);
      rect.setColor(Color.RED);
      rect.setStyle(StyleConstants.NO_BORDER);

      assertEquals(0, opaquePixels(paint(rect)));
   }

   private static PageLayout.Line line(int style, boolean arrow, int location) {
      PageLayout.Line line = new PageLayout.Line(10, 60, 110, 60);
      line.setColor(Color.RED);
      line.setStyle(style);
      line.setArrow(arrow);
      line.setArrowLocation(location);
      return line;
   }

   private static PageLayout.Oval oval(int style, Paint fill) {
      PageLayout.Oval oval = new PageLayout.Oval(20, 20, 60, 40);
      oval.setColor(Color.RED);
      oval.setStyle(style);
      oval.setFillColor(fill);
      return oval;
   }

   private static BufferedImage paint(PageLayout.Shape shape) {
      BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();

      try {
         shape.paint(g);
      }
      finally {
         g.dispose();
      }

      return img;
   }

   private static int opaquePixels(BufferedImage img) {
      int count = 0;

      for(int y = 0; y < img.getHeight(); y++) {
         for(int x = 0; x < img.getWidth(); x++) {
            if(((img.getRGB(x, y) >> 24) & 0xff) > 0) {
               count++;
            }
         }
      }

      return count;
   }
}
