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
package inetsoft.graph.internal;

import inetsoft.graph.guide.VLabel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class GToolContrastInkTest {
   @Test
   void theHigherContrastInkWins() {
      Color bg = new Color(0xE5197E);

      assertEquals(Color.BLACK, GTool.getTextColor(bg, true));
      assertEquals(Color.WHITE, GTool.getTextColor(bg, false));
   }

   @Test
   void everyGreyReaches4_58() {
      for(int g = 0; g <= 255; g++) {
         Color bg = new Color(g, g, g);
         double l = GTool.getLuminance(bg);
         double inkL = GTool.getLuminance(GTool.getTextColor(bg, true));
         double ratio = (Math.max(l, inkL) + 0.05) / (Math.min(l, inkL) + 0.05);

         assertTrue(ratio >= 4.58, "grey " + g + " ratio " + ratio);
      }
   }

   @Test
   void aLabelUsesIt() {
      VLabel label = new VLabel("x");
      label.setAutoBackground(new Color(0xE5197E));

      label.setContrastInk(true);
      assertEquals(Color.BLACK, label.getColor());

      label.setContrastInk(false);
      assertEquals(Color.WHITE, label.getColor());
   }
}
