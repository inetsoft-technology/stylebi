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
package inetsoft.graph.visual;

import inetsoft.graph.guide.form.RectForm;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for how FormVO.paint composes the color's own alpha with the form alpha.
 */
@Tag("core")
class FormVOTest {
   @Test
   void translucentColorWithDefaultAlphaKeepsColorAlpha() {
      assertEquals(40, paintAlpha(new Color(255, 165, 0, 40), null), 1);
   }

   @Test
   void opaqueColorWithFormAlphaUsesFormAlpha() {
      assertEquals(102, paintAlpha(new Color(255, 165, 0, 255), 40), 1);
   }

   @Test
   void translucentColorAndFormAlphaAreComposed() {
      assertEquals(20, paintAlpha(new Color(255, 165, 0, 40), 50), 1);
   }

   @Test
   void opaqueColorWithDefaultAlphaIsOpaque() {
      assertEquals(255, paintAlpha(new Color(255, 165, 0, 255), null), 1);
   }

   private static int paintAlpha(Color color, Integer formAlpha) {
      RectForm form = new RectForm(new Rectangle2D.Double(0, 0, 10, 10));
      form.setFill(true);
      form.setColor(color);

      if(formAlpha != null) {
         form.setAlpha(formAlpha);
      }

      FormVO vo = new FormVO(form);
      vo.setShape(new Rectangle(0, 0, 10, 10));
      BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      vo.paint(g);
      g.dispose();
      return image.getRGB(5, 5) >>> 24;
   }
}
