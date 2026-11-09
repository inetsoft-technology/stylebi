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

import inetsoft.report.internal.ReportGenerator;
import inetsoft.report.internal.TextPaintable;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80713: the text that follows an anchored image in the same paragraph must be printed,
 * whichever side of the image is open for wrapping. With left-only wrapping the text runs to
 * the left of the image while beside it, then below it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AnchoredPainterWrappingTest {
   @ParameterizedTest(name = "wrapping {0}, anchor x {1} in")
   @CsvSource({
      "WRAP_LEFT, 3", "WRAP_LEFT, 0", "WRAP_LEFT, 1",
      "WRAP_RIGHT, 3", "WRAP_BOTH, 3"
   })
   void textAfterAnchoredImageIsPrinted(String wrapping, float anchorX) {
      TabularSheet report = new TabularSheet(Mockito.mock(LibManagerProvider.class),
                                             Mockito.mock(Cluster.class));
      report.setPageSize(new Size(8.5, 11));
      report.setMargin(new Margin(1, 1, 1, 1));

      String id = report.addImage(0, 0, new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB));
      PainterElement image = (PainterElement) report.getElement(id);
      image.setMargin(new Insets(0, 0, 0, 0));
      image.setAnchor(new Position(anchorX, 0));
      image.setWrapping(wrappingValue(wrapping));
      report.addText(0, 0, "word ".repeat(80));

      List<Rectangle> imageBounds = new ArrayList<>();
      List<TextPaintable> texts = new ArrayList<>();
      Enumeration<?> pages = ReportGenerator.generate(report);

      while(pages.hasMoreElements()) {
         StylePage pg = (StylePage) pages.nextElement();

         for(int i = 0; i < pg.getPaintableCount(); i++) {
            Paintable pt = pg.getPaintable(i);

            if(pt instanceof TextPaintable) {
               texts.add((TextPaintable) pt);
            }
            else if(pt.getElement() == image) {
               imageBounds.add(pt.getBounds());
            }
         }
      }

      assertEquals(1, imageBounds.size(), "the image is printed once");
      assertFalse(texts.isEmpty(), "the text after the anchored image is printed");

      long words = texts.stream()
         .map(TextPaintable::getText)
         .flatMap(s -> java.util.Arrays.stream(s.trim().split("\\s+")))
         .filter("word"::equals)
         .count();
      assertEquals(80, words, "every word is printed");

      Rectangle img = imageBounds.get(0);

      for(TextPaintable text : texts) {
         Rectangle b = text.getBounds();
         boolean besideImage = b.y < img.y + img.height && b.y + b.height > img.y;

         // no text line overlaps the image, and with left-only wrapping nothing is printed
         // on its right side
         if(besideImage) {
            assertFalse(b.intersects(img), "text " + b + " overlaps the image " + img);

            if("WRAP_LEFT".equals(wrapping)) {
               assertTrue(b.x + b.width <= img.x, "text " + b + " right of the image " + img);
            }
            else if("WRAP_RIGHT".equals(wrapping)) {
               assertTrue(b.x >= img.x + img.width, "text " + b + " left of the image " + img);
            }
         }
      }
   }

   private static int wrappingValue(String name) {
      switch(name) {
      case "WRAP_LEFT":
         return ReportSheet.WRAP_LEFT;
      case "WRAP_RIGHT":
         return ReportSheet.WRAP_RIGHT;
      default:
         return ReportSheet.WRAP_BOTH;
      }
   }
}
