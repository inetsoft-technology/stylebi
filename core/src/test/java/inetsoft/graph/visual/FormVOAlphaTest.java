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
package inetsoft.graph.visual;

import inetsoft.graph.guide.form.RectForm;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for FormVO.paint(Graphics2D) honoring both the fill Color's own
 * alpha channel and the form's setAlpha() percentage, instead of discarding the
 * Color's alpha (bug 76853 / VSD-009).
 */
@Tag("core")
class FormVOAlphaTest {

   private int paintAndSampleAlpha(RectForm form) {
      form.setFill(true);
      RectFormVO vo = new RectFormVO(form, new Rectangle2D.Double(0, 0, 50, 50));

      BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = image.createGraphics();
      vo.paint(g2);
      g2.dispose();

      return new Color(image.getRGB(25, 25), true).getAlpha();
   }

   @Test
   void colorAlphaOnly_isHonored() {
      RectForm form = new RectForm(new Rectangle2D.Double(0, 0, 50, 50));
      form.setColor(new Color(255, 165, 0, 40));

      int alpha = paintAndSampleAlpha(form);

      assertEquals(40, alpha, 2);
   }

   @Test
   void setAlphaOnly_isBackwardCompatible() {
      RectForm form = new RectForm(new Rectangle2D.Double(0, 0, 50, 50));
      form.setColor(new Color(255, 165, 0));
      form.setAlpha(50);

      int alpha = paintAndSampleAlpha(form);

      assertEquals(Math.round(255 * 0.5f), alpha, 2);
   }

   @Test
   void colorAlphaAndSetAlpha_combineMultiplicatively() {
      RectForm form = new RectForm(new Rectangle2D.Double(0, 0, 50, 50));
      form.setColor(new Color(255, 165, 0, 128));
      form.setAlpha(50);

      int alpha = paintAndSampleAlpha(form);

      int expected = Math.round((128 / 255f) * 0.5f * 255f);
      assertEquals(expected, alpha, 2);
   }
}
