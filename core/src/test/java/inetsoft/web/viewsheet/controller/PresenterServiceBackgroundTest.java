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
package inetsoft.web.viewsheet.controller;

import inetsoft.report.Presenter;
import inetsoft.report.Size;
import inetsoft.report.StyleConstants;
import inetsoft.report.internal.Bounds;
import inetsoft.report.internal.Common;
import inetsoft.report.painter.*;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Common.paint fills the area it paints a presenter in with the background it is given. A modern
 * table's cell already paints its own background, so the image for a presenter that fills its cell
 * (a bar) must not carry that tint again: it showed as a box a little smaller than the cell.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PresenterServiceBackgroundTest {
   private static final Color TINT = new Color(170, 170, 170, 38);
   private static final int W = 88;
   private static final int H = 16;

   @Test
   void aModernTablePaintsOnAClearBackground() {
      Color bg = PresenterService.presenterPaintBackground(TINT, true);

      assertEquals(0, bg.getAlpha());
      assertEquals(0, PresenterService.presenterPaintBackground(null, true).getAlpha());
   }

   @Test
   void aClassicTableKeepsTheCellBackground() {
      assertEquals(TINT, PresenterService.presenterPaintBackground(TINT, false));
   }

   @Test
   void aBarImageForAModernTableHasNothingOutsideTheBar() {
      BufferedImage image = render(PresenterService.presenterPaintBackground(TINT, true));

      assertEquals(255, image.getRGB(10, 8) >>> 24, "the bar is drawn");
      assertEquals(0, image.getRGB(80, 8) >>> 24, "nothing beyond the bar");
      assertEquals(0, image.getRGB(10, 0) >>> 24, "nothing above the bar");
   }

   @Test
   void aBarImageForAClassicTableIsStillFilledWithTheCellBackground() {
      BufferedImage image = render(PresenterService.presenterPaintBackground(TINT, false));

      assertEquals(38, image.getRGB(80, 8) >>> 24);
   }

   @Test
   void noPresenterImageForAModernTableCarriesTheCellTint() {
      for(Map.Entry<String, Object[]> e : presenters().entrySet()) {
         assertEquals(0, tintedPixels(e.getValue(), PresenterService.presenterPaintBackground(TINT, true)),
                      e.getKey());
      }
   }

   @Test
   void theSameImagesDoCarryTheTintOnAClassicBackground() {
      // the control that shows the count above can find a tint: the presenters that draw
      // something of their own (a button, a shadow) are the only ones that leave none
      for(String name : new String[] { "Bar", "Bar +/- positive", "Bar +/- negative", "Stars",
                                       "Traffic Light", "Check mark on", "Check mark off",
                                       "Bullet Graph" })
      {
         Object[] c = presenters().get(name);

         assertTrue(tintedPixels(c, PresenterService.presenterPaintBackground(TINT, false)) > 0, name);
      }
   }

   private static Map<String, Object[]> presenters() {
      Map<String, Object[]> map = new LinkedHashMap<>();
      map.put("Bar", new Object[] { new BarPresenter(100, Color.BLUE), 50.0 });
      map.put("Bar +/- positive", new Object[] { new Bar2Presenter(), 30.0 });
      map.put("Bar +/- negative", new Object[] { new Bar2Presenter(), -30.0 });
      map.put("Stars", new Object[] { new IconCounterPresenter(), 3.0 });
      map.put("Traffic Light", new Object[] { new TrafficLightPresenter(), 50.0 });
      map.put("Check mark on", new Object[] { new BooleanPresenter(), 1.0 });
      map.put("Check mark off", new Object[] { new BooleanPresenter(), 0.0 });
      map.put("Bullet Graph", new Object[] { new BulletGraphPresenter(), 50.0 });
      map.put("Button", new Object[] { new ButtonPresenter(), "Go" });
      map.put("Shadow", new Object[] { new ShadowPresenter(), "Go" });
      return map;
   }

   // the number of pixels of the image that are the cell's tint, painted the way the service does:
   // a presenter that does not fill its cell is placed at its own size, the rest cover the image
   private static int tintedPixels(Object[] c, Color bg) {
      Presenter p = (Presenter) c[0];
      p.setBackground(TINT);
      BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      Bounds b = new Bounds(0, 0, W, H);
      Dimension psize = p.getPreferredSize(c[1]);

      if(!p.isFill() && psize.width > 0) {
         b = Common.alignPresenterCell(b, new Size(psize),
                                       StyleConstants.H_LEFT | StyleConstants.V_CENTER);
      }

      Common.paint(g, b.x, b.y, b.width, b.height, new PresenterPainter(c[1], p), 0f, 0f, b.width,
                   b.height, b.width, b.height, Color.BLACK, bg, -b.y, H);
      g.dispose();
      int count = 0;

      for(int x = 0; x < W; x++) {
         for(int y = 0; y < H; y++) {
            int argb = image.getRGB(x, y);

            // the tint as stored in a non-premultiplied image drifts by a few levels
            if((argb >>> 24) == 38 && near((argb >> 16) & 255) && near((argb >> 8) & 255) &&
               near(argb & 255))
            {
               count++;
            }
         }
      }

      return count;
   }

   private static boolean near(int level) {
      return level >= 164 && level <= 176;
   }

   // the way the service paints it: a presenter that fills its cell, over the whole image
   private static BufferedImage render(Color bg) {
      BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      PresenterPainter painter = new PresenterPainter(
         20.0, new BarPresenter(100, new Color(0, 0, 255)));

      Common.paint(g, 0, 0, W, H, painter, 0f, 0f, W, H, W, H, Color.BLACK, bg, 0, H);
      g.dispose();
      return image;
   }
}
