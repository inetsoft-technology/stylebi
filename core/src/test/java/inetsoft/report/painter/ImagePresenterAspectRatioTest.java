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

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #79671: with "Maintain aspect ratio" the image must be scaled to fit inside the
 * cell and keep its own aspect ratio. The height used to be computed from the cell
 * width, so any cell wider than the image aspect drew the image too tall and cut it
 * off at the cell bottom.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ImagePresenterAspectRatioTest {
   // 48x48 image, wide cell (cell aspect 5 > image aspect 1): expect 24x24
   @Test
   void wideCellKeepsImageInsideCell() throws Exception {
      assertFit(48, 48, 120, 24, 24, 24);
   }

   // 50x100 image in a square cell (cell aspect 1 > image aspect 0.5): expect 50x100
   @Test
   void tallImageInSquareCellKeepsRatio() throws Exception {
      assertFit(50, 100, 100, 100, 50, 100);
   }

   // tall cell (cell aspect < image aspect): width bound, expect 24x24
   @Test
   void tallCellFitsWidth() throws Exception {
      assertFit(48, 48, 24, 120, 24, 24);
   }

   // exact aspect: fills the cell
   @Test
   void exactAspectFillsCell() throws Exception {
      assertFit(96, 24, 120, 30, 120, 30);
   }

   /**
    * Paint the image (red, with a blue bottom-right quadrant) into a transparent cell
    * and check the opaque area is exactly the expected size and the blue quadrant,
    * the part that used to be cut off, is fully visible.
    */
   private void assertFit(int iw, int ih, int cw, int ch, int ew, int eh) throws Exception {
      File file = File.createTempFile("imgpresenter79671_", ".png");
      file.deleteOnExit();
      BufferedImage image = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
      Graphics2D ig = image.createGraphics();
      ig.setColor(Color.RED);
      ig.fillRect(0, 0, iw, ih);
      ig.setColor(Color.BLUE);
      ig.fillRect(iw / 2, ih / 2, iw - iw / 2, ih - ih / 2);
      ig.dispose();
      ImageIO.write(image, "png", file);

      ImagePresenter presenter = new ImagePresenter();
      presenter.setMaintainAspectRatio(true);
      BufferedImage cell = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = cell.createGraphics();
      presenter.paint(g, file.toURI().toURL().toString(), 0, 0, cw, ch);
      g.dispose();

      int maxX = -1;
      int maxY = -1;
      int blue = 0;

      for(int y = 0; y < ch; y++) {
         for(int x = 0; x < cw; x++) {
            int argb = cell.getRGB(x, y);

            if((argb >>> 24) != 0) {
               maxX = Math.max(maxX, x);
               maxY = Math.max(maxY, y);

               if((argb & 0xff) > 200 && ((argb >> 16) & 0xff) < 50) {
                  blue++;
               }
            }
         }
      }

      assertEquals(ew, maxX + 1, "drawn width");
      assertEquals(eh, maxY + 1, "drawn height, cell height is " + ch);
      // the blue quadrant is a quarter of the drawn area, allow scaling blur at edges
      assertTrue(blue >= ew * eh / 4 * 0.9, "bottom-right quadrant visible, blue=" + blue);
   }
}
