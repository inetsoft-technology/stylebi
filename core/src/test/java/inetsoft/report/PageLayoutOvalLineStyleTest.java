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
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79414: a Print Layout Oval must draw its line style (width, dash, double),
 * not always a 1px solid outline.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PageLayoutOvalLineStyleTest {
   @Test
   void thinOvalIsUnchanged() {
      // what PageLayout.Oval drew before the fix: a plain drawOval with the default stroke
      BufferedImage expected = newImage();
      Graphics2D g = expected.createGraphics();

      try {
         g.setColor(Color.RED);
         g.drawOval(20, 20, 100, 60);
      }
      finally {
         g.dispose();
      }

      assertArrayEquals(pixels(expected), pixels(paint(oval(StyleConstants.THIN_LINE))));
   }

   @Test
   void thickOvalDrawsMoreThanThin() {
      int thin = opaquePixels(paint(oval(StyleConstants.THIN_LINE)));

      assertTrue(opaquePixels(paint(oval(StyleConstants.MEDIUM_LINE))) > thin);
      assertTrue(opaquePixels(paint(oval(StyleConstants.THICK_LINE))) > thin);
   }

   @Test
   void dashedAndDoubleOvalsDifferFromThin() {
      int[] thin = pixels(paint(oval(StyleConstants.THIN_LINE)));

      assertFalse(Arrays.equals(thin, pixels(paint(oval(StyleConstants.DASH_LINE)))));
      assertFalse(Arrays.equals(thin, pixels(paint(oval(StyleConstants.DOT_LINE)))));
      assertFalse(Arrays.equals(thin, pixels(paint(oval(StyleConstants.DOUBLE_LINE)))));
   }

   @Test
   void thickOvalStaysInsideItsBox() {
      BufferedImage img = paint(oval(StyleConstants.THICK_LINE));

      for(int y = 0; y < img.getHeight(); y++) {
         for(int x = 0; x < img.getWidth(); x++) {
            if(x < 20 || x > 120 || y < 20 || y > 80) {
               assertEquals(0, (img.getRGB(x, y) >> 24) & 0xff,
                            "pixel outside the box at " + x + "," + y);
            }
         }
      }
   }

   @Test
   void strokeDoesNotLeakToTheCallersGraphics() {
      BufferedImage img = newImage();
      Graphics2D g = img.createGraphics();

      try {
         Stroke before = g.getStroke();
         oval(StyleConstants.THICK_LINE).paint(g);
         assertEquals(before, g.getStroke());
      }
      finally {
         g.dispose();
      }
   }

   @Test
   void pdfThickOvalSetsLineWidth() {
      assertTrue(paintPdf(oval(StyleConstants.THICK_LINE)).contains("3 w"));
   }

   @Test
   void pdfDashedOvalSetsDashPattern() {
      assertTrue(paintPdf(oval(StyleConstants.DASH_LINE)).contains("[ 3.0 ] 0 d"));
   }

   private static PageLayout.Oval oval(int style) {
      PageLayout.Oval oval = new PageLayout.Oval(20, 20, 100, 60);
      oval.setColor(Color.RED);
      oval.setStyle(style);
      return oval;
   }

   private static BufferedImage newImage() {
      return new BufferedImage(160, 120, BufferedImage.TYPE_INT_ARGB);
   }

   private static BufferedImage paint(PageLayout.Shape shape) {
      BufferedImage img = newImage();
      Graphics2D g = img.createGraphics();

      try {
         shape.paint(g);
      }
      finally {
         g.dispose();
      }

      return img;
   }

   private static String paintPdf(PageLayout.Shape shape) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      PDFPrinter printer = new PDFPrinter(out);
      // uncompressed, so the page stream operators can be read
      printer.setCompressText(false);
      shape.paint(printer);
      printer.dispose();
      return out.toString(StandardCharsets.ISO_8859_1);
   }

   private static int[] pixels(BufferedImage img) {
      return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
   }

   private static int opaquePixels(BufferedImage img) {
      int count = 0;

      for(int pixel : pixels(img)) {
         if(((pixel >> 24) & 0xff) > 0) {
            count++;
         }
      }

      return count;
   }
}
