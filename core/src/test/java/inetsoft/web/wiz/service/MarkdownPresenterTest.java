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
package inetsoft.web.wiz.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class MarkdownPresenterTest {
   private final MarkdownPresenter presenter = new MarkdownPresenter();

   @Test
   void emptyContentHasZeroHeight() {
      assertEquals(0, presenter.getPreferredSize("", 400).height);
      assertEquals(0, presenter.getPreferredSize(null, 400).height);
   }

   @Test
   void narrowerWidthWrapsToGreaterHeight() {
      String md = "The premium band is about 69% of all revenue across every one of the ten categories.";
      int wide = presenter.getPreferredSize(md, 600).height;
      int narrow = presenter.getPreferredSize(md, 150).height;
      assertTrue(wide > 0);
      assertTrue(narrow > wide, "narrower width wraps to more lines: narrow=" + narrow + " wide=" + wide);
   }

   @Test
   void headersBulletsAndParagraphsStackVertically() {
      String md = "## Heading\n\nA paragraph of prose.\n- first bullet\n- second bullet";
      int h = presenter.getPreferredSize(md, 500).height;
      // heading + paragraph + 2 bullets => clearly multiple lines tall
      assertTrue(h > 40, "structured content stacks: " + h);
   }

   @Test
   void paintDrawsVisibleContent() {
      String md = "**Bold lead.** then *italic* and normal text.\n- a bullet point";
      int w = 400;
      int h = presenter.getPreferredSize(md, w).height + 10;
      BufferedImage img = new BufferedImage(w, Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, w, h);
      presenter.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

      assertDoesNotThrow(() -> presenter.paint(g, md, 0, 0, w, h));

      int nonWhite = 0;

      for(int px = 0; px < w; px++) {
         for(int py = 0; py < h; py++) {
            if((img.getRGB(px, py) & 0x00FFFFFF) != 0x00FFFFFF) {
               nonWhite++;
            }
         }
      }

      g.dispose();
      assertTrue(nonWhite > 50, "rendered glyphs leave visible pixels: " + nonWhite);
   }

   @Test
   void paintSliceClipsToVerticalWindow() {
      // A tall block; painting only the top slice should draw fewer pixels than the full height.
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < 20; i++) {
         sb.append("- bullet line number ").append(i).append("\n");
      }

      String md = sb.toString();
      int w = 400;
      int full = presenter.getPreferredSize(md, w).height;
      BufferedImage img = new BufferedImage(w, full, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, w, full);
      // paint only the top third
      presenter.paint(g, md, 0, 0, w, full / 3, 0, full / 3f);
      g.dispose();

      int nonWhiteBottom = 0;

      for(int py = full * 2 / 3; py < full; py++) {
         for(int px = 0; px < w; px++) {
            if((img.getRGB(px, py) & 0x00FFFFFF) != 0x00FFFFFF) {
               nonWhiteBottom++;
            }
         }
      }

      assertEquals(0, nonWhiteBottom, "bottom of the block must be clipped out of the top slice");
   }
}
