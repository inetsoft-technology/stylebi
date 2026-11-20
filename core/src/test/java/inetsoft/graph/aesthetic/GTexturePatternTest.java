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

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The built-in texture patterns are tiles of upright lines, dots and shapes, repeated from the
 * bottom-left corner of whatever they fill.
 */
@Tag("core")
class GTexturePatternTest {
   private static final int COUNT = 20;
   private static final int BLUE = 0xFF2E75B6;
   private static final int WHITE = 0xFFFFFFFF;

   private static GTexture pattern(int i) throws Exception {
      return (GTexture) GTexture.class.getField("PATTERN_" + i).get(null);
   }

   /** Paint the texture into a transparent image, in the shape at (x, y, w, h). */
   private static BufferedImage paint(GTexture texture, int size, double x, double y, double w,
                                      double h)
   {
      BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      g.setColor(new Color(BLUE));
      texture.paint(g, new Rectangle2D.Double(x, y, w, h));
      g.dispose();
      return img;
   }

   @Test
   void thereAreTwentyDistinctPatterns() throws Exception {
      List<GTexture> all = new ArrayList<>();

      for(int i = 0; i < COUNT; i++) {
         GTexture t = pattern(i);
         assertFalse(all.contains(t), "pattern " + i + " repeats an earlier one");
         all.add(t);
      }
   }

   @Test
   void everyPatternHasInkAndBackground() throws Exception {
      for(int i = 0; i < COUNT; i++) {
         BufferedImage img = paint(pattern(i), 80, 10, 10, 60, 60);
         int ink = 0;
         int white = 0;

         for(int y = 10; y < 70; y++) {
            for(int x = 10; x < 70; x++) {
               int rgb = img.getRGB(x, y);

               if(rgb == WHITE) {
                  white++;
               }
               else if(((rgb >> 16) & 0xFF) < 0xC0) {
                  ink++;
               }
            }
         }

         assertTrue(ink > 60 * 60 / 40, "pattern " + i + " draws almost nothing: " + ink);
         assertTrue(white > 60 * 60 / 5, "pattern " + i + " leaves no background: " + white);
      }
   }

   @Test
   void paintingStaysInsideTheShape() throws Exception {
      for(int i = 0; i < COUNT; i++) {
         BufferedImage img = paint(pattern(i), 80, 20, 20, 40, 40);

         for(int y = 0; y < 80; y++) {
            for(int x = 0; x < 80; x++) {
               boolean inside = x >= 20 && x < 60 && y >= 20 && y < 60;

               if(!inside) {
                  assertEquals(0, img.getRGB(x, y), "pattern " + i + " painted outside at " + x +
                     "," + y);
               }
            }
         }
      }
   }

   @Test
   void theTilesStartAtTheBottomLeftOfTheShape() throws Exception {
      // the same shape drawn somewhere else shows the same picture, so every bar of a series
      // looks the same and the edges line up
      for(int i = 0; i < COUNT; i++) {
         BufferedImage a = paint(pattern(i), 120, 10, 10, 37, 53);
         BufferedImage b = paint(pattern(i), 120, 47, 31, 37, 53);

         for(int y = 0; y < 53; y++) {
            for(int x = 0; x < 37; x++) {
               assertEquals(a.getRGB(10 + x, 10 + y), b.getRGB(47 + x, 31 + y),
                            "pattern " + i + " differs at " + x + "," + y);
            }
         }
      }
   }

   @Test
   void theTilesStartAtTheBottomLeftInsideTheMargin() throws Exception {
      // a 20 x 20 shape at (5, 5) has the pattern in (7, 7) to (23, 23), a margin of 2. Pattern 0
      // is fine horizontal lines on a 5px tile, the first line at the top of each tile, so the
      // tiles are at y = 18, 13, 8 and 3 (clipped away), counted up from the bottom, 23
      GTexture t = pattern(0);
      BufferedImage img = paint(t, 40, 5, 5, 20, 20);

      for(int y = 5; y < 25; y++) {
         int rgb = img.getRGB(12, y);
         boolean line = y == 8 || y == 13 || y == 18;
         assertEquals(line, ((rgb >> 16) & 0xFF) < 0xC0, "row " + y);
      }
   }

   @Test
   void leavesAWhiteMarginSoThePatternNeverRunsIntoTheBorder() throws Exception {
      for(int i = 0; i < COUNT; i++) {
         BufferedImage img = paint(pattern(i), 100, 20, 20, 60, 60);

         for(int d = 0; d < 2; d++) {
            for(int k = 0; k < 60; k++) {
               assertEquals(WHITE, img.getRGB(20 + d, 20 + k), "pattern " + i + " left " + d);
               assertEquals(WHITE, img.getRGB(79 - d, 20 + k), "pattern " + i + " right " + d);
               assertEquals(WHITE, img.getRGB(20 + k, 20 + d), "pattern " + i + " top " + d);
               assertEquals(WHITE, img.getRGB(20 + k, 79 - d), "pattern " + i + " bottom " + d);
            }
         }
      }
   }

   @Test
   void aRoundShapeKeepsTheMarginToo() throws Exception {
      BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      g.setColor(new Color(BLUE));
      pattern(5).paint(g, new java.awt.geom.Ellipse2D.Double(10, 10, 80, 80));
      g.dispose();

      // the bold lines would reach the edge of the circle at 3 o'clock; the last 2px, 88 and
      // 89 (the circle ends at x = 90), stay white
      for(int y = 48; y < 52; y++) {
         assertEquals(WHITE, img.getRGB(88, y), "y " + y);
      }

      int ink = 0;

      for(int x = 20; x < 80; x++) {
         if(((img.getRGB(x, 50) >> 16) & 0xFF) < 0xC0) {
            ink++;
         }
      }

      assertTrue(ink > 0, "the circle is still patterned inside");
   }

   @Test
   void aShapeBuiltFromManyPiecesIsNotStrokedAcrossItsMiddle() throws Exception {
      // an area chart fills one path of appended segments; stroking it would draw the edges
      // between the pieces as white lines across the area, so it gets no margin
      java.awt.geom.Path2D path = new java.awt.geom.Path2D.Double(java.awt.geom.Path2D.WIND_NON_ZERO);

      for(int i = 0; i < 20; i++) {
         path.moveTo(10 + i * 4, 90);
         path.lineTo(10 + i * 4, 40);
         path.lineTo(14 + i * 4, 40);
         path.lineTo(14 + i * 4, 90);
         path.closePath();
      }

      BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      g.setColor(new Color(BLUE));
      pattern(10).paint(g, path); // large dots, so every seam would show
      g.dispose();

      // the column x = 12 .. 13 crosses the seam between two pieces at x = 14; the dot rows
      // there are painted, not wiped by a stroke along the seam
      int ink = 0;

      for(int y = 42; y < 88; y++) {
         if(((img.getRGB(14, y) >> 16) & 0xFF) < 0xC0) {
            ink++;
         }
      }

      assertTrue(ink > 0, "the seam was wiped");
   }

   @Test
   void aSmallShapeStillShowsThePattern() throws Exception {
      // a legend swatch: the margin shrinks with the shape
      BufferedImage img = paint(pattern(3), 40, 10, 10, 14, 14);
      int ink = 0;

      for(int y = 10; y < 24; y++) {
         for(int x = 10; x < 24; x++) {
            if(((img.getRGB(x, y) >> 16) & 0xFF) < 0xC0) {
               ink++;
            }
         }
      }

      assertTrue(ink > 20, "ink " + ink);
   }

   @Test
   void lineTexturesAreStillDiagonalStripes() {
      // the tilt, orientation and grid frames build their own stripes
      GTexture t = new GTexture(3, Math.PI / 4, 1);
      BufferedImage img = paint(t, 80, 10, 10, 60, 60);
      int diagonal = 0;

      for(int y = 11; y < 69; y++) {
         for(int x = 11; x < 69; x++) {
            if(img.getRGB(x, y) != 0 && img.getRGB(x, y) != WHITE &&
               img.getRGB(x + 1, y + 1) == img.getRGB(x, y))
            {
               diagonal++;
            }
         }
      }

      assertTrue(diagonal > 100, "stripes should run along a diagonal");
      assertNotEquals(pattern0(), t);
   }

   private static GTexture pattern0() {
      return GTexture.PATTERN_0;
   }
}
